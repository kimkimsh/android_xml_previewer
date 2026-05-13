package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * AAR walker that enumerates the runtime-classpath manifest's AAR list and emits
 * ParsedNsEntry per file. Each AAR contributes:
 *  - AndroidManifest's package name (for diagnostic / dedupe-source tracking).
 *  - res/values/values.xml — single-value entries, attrs, styles (RES_AUTO).
 *  - res/color/<name>.xml — ColorStateList raw bodies (default qualifier only).
 *  - res/animator/<name>.xml — AnimatorXml raw bodies (default qualifier only).
 *  - res/drawable/<name>.xml — DrawableXml raw bodies (default qualifier only).
 *  - res/interpolator/<name>.xml — InterpolatorXml raw bodies (default qualifier only).
 *  - res/layout/<name>.xml — LayoutXml raw bodies (default qualifier only).
 *
 * Qualifier directories (color-v31/, animator-v21/, drawable-night/, layout-v21/,
 * etc.) stay out of scope until density / locale / night-mode support is wired.
 * The walker captures raw XML strings only — selector / vector / animator / layout
 * parsing is delegated to layoutlib's Bridge via MinimalLayoutlibCallback.getParser.
 * AARs lacking all six resource sources are skipped with a single diagnostic line.
 */
internal object AarResourceWalker
{

    data class Result(val sourcePackage: String, val entries: List<ParsedNsEntry>)

    fun walkAll(runtimeClasspathTxt: Path): List<Result>
    {
        require(Files.exists(runtimeClasspathTxt)) {
            "sample-app classpath manifest missing: $runtimeClasspathTxt — run assembleDebug first"
        }
        val t0 = System.nanoTime()
        val aarPaths = Files.readAllLines(runtimeClasspathTxt)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.endsWith(".aar") }
            .map { Path.of(it) }
        val results = mutableListOf<Result>()
        var skipped = 0
        var totalColorXmls = 0
        var totalAnimatorXmls = 0
        var totalDrawableXmls = 0
        var totalInterpolatorXmls = 0
        var totalLayoutXmls = 0
        for (aar in aarPaths)
        {
            val r = walkOne(aar)
            if (r != null)
            {
                results += r
                totalColorXmls += r.entries.count { it is ParsedNsEntry.ColorStateList }
                totalAnimatorXmls += r.entries.count { it is ParsedNsEntry.AnimatorXml }
                totalDrawableXmls += r.entries.count { it is ParsedNsEntry.DrawableXml }
                totalInterpolatorXmls += r.entries.count { it is ParsedNsEntry.InterpolatorXml }
                totalLayoutXmls += r.entries.count { it is ParsedNsEntry.LayoutXml }
            }
            else
            {
                skipped++
            }
        }
        val tMs = (System.nanoTime() - t0) / 1_000_000
        System.err.println(
            "[AarResourceWalker] walked ${aarPaths.size} AARs (${results.size} with res, $skipped code-only, $totalColorXmls color-state-lists, $totalAnimatorXmls animator-xmls, $totalDrawableXmls drawable-xmls, $totalInterpolatorXmls interpolator-xmls, $totalLayoutXmls layout-xmls) in ${tMs}ms",
        )
        return results
    }

    fun walkOne(aarPath: Path): Result?
    {
        require(Files.exists(aarPath)) { "AAR missing: $aarPath" }
        ZipFile(aarPath.toFile()).use { zip ->
            val manifestEntry = zip.getEntry(AppLibraryResourceConstants.AAR_ANDROID_MANIFEST_PATH)
                ?: throw IllegalStateException("$aarPath: AndroidManifest.xml missing — invalid AAR layout")
            val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val match = AppLibraryResourceConstants.MANIFEST_PACKAGE_REGEX.find(manifestText)
                ?: throw IllegalStateException("$aarPath: failed to extract package attribute from AndroidManifest")
            val pkg = match.groupValues[1]
            require(pkg.isNotEmpty()) { "$aarPath: AndroidManifest package empty" }

            val valuesEntry = zip.getEntry(AppLibraryResourceConstants.AAR_VALUES_XML_PATH)
            val valuesEntries: List<ParsedNsEntry> =
                if (valuesEntry == null) emptyList() else parseValuesXml(zip, valuesEntry, pkg)
            val colorEntries: List<ParsedNsEntry> = collectColorStateLists(zip, pkg)
            val animatorEntries: List<ParsedNsEntry> = collectAnimatorXmls(zip, pkg)
            val drawableEntries: List<ParsedNsEntry> = collectDrawableXmls(zip, pkg)
            val interpolatorEntries: List<ParsedNsEntry> = collectInterpolatorXmls(zip, pkg)
            val layoutEntries: List<ParsedNsEntry> = collectLayoutXmls(zip, pkg)

            // If any of values / color / animator / drawable / interpolator / layout
            // are present the AAR contributes resources. All absent means truly
            // code-only.
            if (valuesEntries.isEmpty() && colorEntries.isEmpty() &&
                animatorEntries.isEmpty() && drawableEntries.isEmpty() &&
                interpolatorEntries.isEmpty() && layoutEntries.isEmpty())
            {
                if (valuesEntry == null)
                {
                    System.err.println(
                        "[AarResourceWalker] $aarPath skipped — res/values/values.xml + res/color/{name}.xml + res/animator/{name}.xml + res/drawable/{name}.xml + res/interpolator/{name}.xml + res/layout/{name}.xml all absent (pkg=$pkg)",
                    )
                }
                return null
            }
            return Result(
                pkg,
                valuesEntries + colorEntries + animatorEntries + drawableEntries + interpolatorEntries + layoutEntries,
            )
        }
    }

    private fun parseValuesXml(zip: ZipFile, valuesEntry: ZipEntry, pkg: String): List<ParsedNsEntry>
    {
        // Materialize values.xml to a temp file before handing it to
        // NamespaceAwareValueParser — StAX is friendlier with Path than InputStream.
        val tmp = Files.createTempFile("aarvals", ".xml")
        tmp.toFile().deleteOnExit()
        zip.getInputStream(valuesEntry).use { stream ->
            Files.copy(stream, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return NamespaceAwareValueParser.parse(tmp, ResourceNamespace.RES_AUTO, pkg)
    }

    /**
     * Enumerates default `res/color/<name>.xml` entries inside an AAR ZIP and emits
     * each raw XML body as ParsedNsEntry.ColorStateList. `<selector>` parsing is
     * delegated to layoutlib Bridge — this walker is responsible only for the
     * InputStream feed that MinimalLayoutlibCallback.getParser hands back.
     *
     * Qualifier directories (color-v31/, color-night-v8/, color-v23/, ...) are out
     * of scope until density / locale / night-mode support lands. The match is
     * strictly `res/color/<name>.xml` — when '/' appears inside the residual base
     * name the entry belongs to a qualifier directory and is skipped.
     */
    private fun collectColorStateLists(zip: ZipFile, pkg: String): List<ParsedNsEntry>
    {
        val out = mutableListOf<ParsedNsEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements())
        {
            val e = entries.nextElement()
            if (e.isDirectory) continue
            val n = e.name
            if (!n.startsWith(AppLibraryResourceConstants.AAR_COLOR_DIR_PREFIX)) continue
            if (!n.endsWith(AppLibraryResourceConstants.COLOR_XML_SUFFIX)) continue
            val rel = n.substring(AppLibraryResourceConstants.AAR_COLOR_DIR_PREFIX.length)
            // Belt-and-suspenders block on nested paths inside the residual base
            // name. Qualifier dirs (color-night/foo.xml) miss the prefix anyway.
            if (rel.contains('/')) continue
            val baseName = rel.removeSuffix(AppLibraryResourceConstants.COLOR_XML_SUFFIX)
            if (baseName.isEmpty()) continue
            val rawXml = zip.getInputStream(e).bufferedReader().use { it.readText() }
            out += ParsedNsEntry.ColorStateList(baseName, rawXml, ResourceNamespace.RES_AUTO, pkg)
        }
        return out
    }

    /**
     * Sibling to collectColorStateLists for `res/animator/<name>.xml`. AnimatorInflater
     * consumes the body via XmlResourceParser, so the walker just captures the raw XML
     * and lets MinimalLayoutlibCallback.getParser feed it through SelectorXmlPullParser.
     * Qualifier directories (animator-v21/, etc.) are out of scope until W4+.
     */
    private fun collectAnimatorXmls(zip: ZipFile, pkg: String): List<ParsedNsEntry>
    {
        val out = mutableListOf<ParsedNsEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements())
        {
            val e = entries.nextElement()
            if (e.isDirectory) continue
            val n = e.name
            if (!n.startsWith(AppLibraryResourceConstants.AAR_ANIMATOR_DIR_PREFIX)) continue
            if (!n.endsWith(AppLibraryResourceConstants.COLOR_XML_SUFFIX)) continue
            val rel = n.substring(AppLibraryResourceConstants.AAR_ANIMATOR_DIR_PREFIX.length)
            if (rel.contains('/')) continue
            val baseName = rel.removeSuffix(AppLibraryResourceConstants.COLOR_XML_SUFFIX)
            if (baseName.isEmpty()) continue
            val rawXml = zip.getInputStream(e).bufferedReader().use { it.readText() }
            out += ParsedNsEntry.AnimatorXml(baseName, rawXml, ResourceNamespace.RES_AUTO, pkg)
        }
        return out
    }

    /**
     * Sibling to collectAnimatorXmls / collectColorStateLists for `res/drawable/<name>.xml`.
     * Captures the raw body so MinimalLayoutlibCallback.getParser can hand it to
     * BridgeContext's DrawableInflater. Default qualifier directory only — drawable-night/
     * and density variants stay out of scope until W4+ qualifier support.
     */
    private fun collectDrawableXmls(zip: ZipFile, pkg: String): List<ParsedNsEntry>
    {
        val out = mutableListOf<ParsedNsEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements())
        {
            val e = entries.nextElement()
            if (e.isDirectory) continue
            val n = e.name
            if (!n.startsWith(AppLibraryResourceConstants.AAR_DRAWABLE_DIR_PREFIX)) continue
            if (!n.endsWith(AppLibraryResourceConstants.COLOR_XML_SUFFIX)) continue
            val rel = n.substring(AppLibraryResourceConstants.AAR_DRAWABLE_DIR_PREFIX.length)
            if (rel.contains('/')) continue
            val baseName = rel.removeSuffix(AppLibraryResourceConstants.COLOR_XML_SUFFIX)
            if (baseName.isEmpty()) continue
            val rawXml = zip.getInputStream(e).bufferedReader().use { it.readText() }
            out += ParsedNsEntry.DrawableXml(baseName, rawXml, ResourceNamespace.RES_AUTO, pkg)
        }
        return out
    }

    /**
     * Sibling to collectAnimatorXmls / collectDrawableXmls for
     * `res/interpolator/<name>.xml`. Material 1.12.0 ships the Material 3 motion-easing
     * interpolator XMLs that MotionUtils.resolveThemeInterpolator consumes via
     * Theme.resolveAttribute + AnimationUtils.loadInterpolator. Default qualifier
     * directory only — interpolator-v21/ and similar qualifier variants stay out of
     * scope until W4+ qualifier support.
     */
    private fun collectInterpolatorXmls(zip: ZipFile, pkg: String): List<ParsedNsEntry>
    {
        val out = mutableListOf<ParsedNsEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements())
        {
            val e = entries.nextElement()
            if (e.isDirectory) continue
            val n = e.name
            if (!n.startsWith(AppLibraryResourceConstants.AAR_INTERPOLATOR_DIR_PREFIX)) continue
            if (!n.endsWith(AppLibraryResourceConstants.COLOR_XML_SUFFIX)) continue
            val rel = n.substring(AppLibraryResourceConstants.AAR_INTERPOLATOR_DIR_PREFIX.length)
            if (rel.contains('/')) continue
            val baseName = rel.removeSuffix(AppLibraryResourceConstants.COLOR_XML_SUFFIX)
            if (baseName.isEmpty()) continue
            val rawXml = zip.getInputStream(e).bufferedReader().use { it.readText() }
            out += ParsedNsEntry.InterpolatorXml(baseName, rawXml, ResourceNamespace.RES_AUTO, pkg)
        }
        return out
    }

    /**
     * Sibling to collectInterpolatorXmls / collectDrawableXmls for
     * `res/layout/<name>.xml`. AppCompat / core / Material AARs ship internal
     * layout files (e.g. design_text_input_start_icon for TextInputLayout's
     * leading icon container, design_navigation_item for NavigationView). Widget
     * code inflates them via `LayoutInflater.from(ctx).inflate(R.layout.<name>,
     * parent, attachToRoot)`, which routes through Resources_Delegate.getLayout
     * → ResourceHelper.getXmlBlockParser → callback.getParser. Default qualifier
     * directory only — layout-v21/, layout-night/, etc. stay out of scope until
     * W4+ qualifier support.
     */
    private fun collectLayoutXmls(zip: ZipFile, pkg: String): List<ParsedNsEntry>
    {
        val out = mutableListOf<ParsedNsEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements())
        {
            val e = entries.nextElement()
            if (e.isDirectory) continue
            val n = e.name
            if (!n.startsWith(AppLibraryResourceConstants.AAR_LAYOUT_DIR_PREFIX)) continue
            if (!n.endsWith(AppLibraryResourceConstants.COLOR_XML_SUFFIX)) continue
            val rel = n.substring(AppLibraryResourceConstants.AAR_LAYOUT_DIR_PREFIX.length)
            if (rel.contains('/')) continue
            val baseName = rel.removeSuffix(AppLibraryResourceConstants.COLOR_XML_SUFFIX)
            if (baseName.isEmpty()) continue
            val rawXml = zip.getInputStream(e).bufferedReader().use { it.readText() }
            out += ParsedNsEntry.LayoutXml(baseName, rawXml, ResourceNamespace.RES_AUTO, pkg)
        }
        return out
    }
}
