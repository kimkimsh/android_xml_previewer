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
 *
 * Qualifier directories (color-v31/, animator-v21/, drawable-night/, etc.) stay
 * out of scope until density / locale / night-mode support is wired. The walker
 * captures raw XML strings only — selector / vector / animator parsing is
 * delegated to layoutlib's Bridge via MinimalLayoutlibCallback.getParser. AARs
 * lacking all four resource sources are skipped with a single diagnostic line.
 */
internal object AarResourceWalker
{

    data class Result(val sourcePackage: String, val entries: List<ParsedNsEntry>)

    fun walkAll(runtimeClasspathTxt: Path): List<Result>
    {
        require(Files.exists(runtimeClasspathTxt)) {
            "sample-app classpath manifest 없음: $runtimeClasspathTxt — assembleDebug 먼저 실행"
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
        for (aar in aarPaths)
        {
            val r = walkOne(aar)
            if (r != null)
            {
                results += r
                totalColorXmls += r.entries.count { it is ParsedNsEntry.ColorStateList }
                totalAnimatorXmls += r.entries.count { it is ParsedNsEntry.AnimatorXml }
                totalDrawableXmls += r.entries.count { it is ParsedNsEntry.DrawableXml }
            }
            else
            {
                skipped++
            }
        }
        val tMs = (System.nanoTime() - t0) / 1_000_000
        System.err.println(
            "[AarResourceWalker] walked ${aarPaths.size} AARs (${results.size} with res, $skipped code-only, $totalColorXmls color-state-lists, $totalAnimatorXmls animator-xmls, $totalDrawableXmls drawable-xmls) in ${tMs}ms",
        )
        return results
    }

    fun walkOne(aarPath: Path): Result?
    {
        require(Files.exists(aarPath)) { "AAR 부재: $aarPath" }
        ZipFile(aarPath.toFile()).use { zip ->
            val manifestEntry = zip.getEntry(AppLibraryResourceConstants.AAR_ANDROID_MANIFEST_PATH)
                ?: throw IllegalStateException("$aarPath: AndroidManifest.xml 없음 — AAR 형식 위반")
            val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val match = AppLibraryResourceConstants.MANIFEST_PACKAGE_REGEX.find(manifestText)
                ?: throw IllegalStateException("$aarPath: AndroidManifest 의 package 추출 실패")
            val pkg = match.groupValues[1]
            require(pkg.isNotEmpty()) { "$aarPath: AndroidManifest package empty" }

            val valuesEntry = zip.getEntry(AppLibraryResourceConstants.AAR_VALUES_XML_PATH)
            val valuesEntries: List<ParsedNsEntry> =
                if (valuesEntry == null) emptyList() else parseValuesXml(zip, valuesEntry, pkg)
            val colorEntries: List<ParsedNsEntry> = collectColorStateLists(zip, pkg)
            val animatorEntries: List<ParsedNsEntry> = collectAnimatorXmls(zip, pkg)
            val drawableEntries: List<ParsedNsEntry> = collectDrawableXmls(zip, pkg)

            // values.xml / color/*.xml / animator/*.xml / drawable/*.xml 중 하나라도 있으면 부분 이용.
            // 모두 없으면 진짜 code-only.
            if (valuesEntries.isEmpty() && colorEntries.isEmpty() && animatorEntries.isEmpty() && drawableEntries.isEmpty())
            {
                if (valuesEntry == null)
                {
                    System.err.println(
                        "[AarResourceWalker] $aarPath skipped — res/values/values.xml + res/color/{name}.xml + res/animator/{name}.xml + res/drawable/{name}.xml all absent (pkg=$pkg)",
                    )
                }
                return null
            }
            return Result(pkg, valuesEntries + colorEntries + animatorEntries + drawableEntries)
        }
    }

    private fun parseValuesXml(zip: ZipFile, valuesEntry: ZipEntry, pkg: String): List<ParsedNsEntry>
    {
        // values.xml 을 임시 파일로 풀어서 NamespaceAwareValueParser 에 넘김
        // (StAX 가 InputStream 보다 Path 친화적).
        val tmp = Files.createTempFile("aarvals", ".xml")
        tmp.toFile().deleteOnExit()
        zip.getInputStream(valuesEntry).use { stream ->
            Files.copy(stream, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return NamespaceAwareValueParser.parse(tmp, ResourceNamespace.RES_AUTO, pkg)
    }

    /**
     * W3D4-β T12: AAR ZIP 내 default `res slash color slash {name}.xml` enumerate, 각 파일을 raw XML 문자열로
     * 읽어 ParsedNsEntry.ColorStateList 로 emit. <selector> 파싱은 layoutlib Bridge 에
     * 위임 — 본 walker 는 InputStream feed 만 책임 (callback.getParser 가 사용).
     *
     * qualifier 디렉토리 (color-v31/, color-night-v8/, color-v23/) 는 본 plan 범위 외 —
     * W4+ density/locale/night-mode 지원 시 추가. 본 매치는 정확히 `res/color/<name>.xml`
     * (path separator '/' 가 baseName 안에 등장하면 qualifier 경로 → skip).
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
            // qualifier dir (color-night/foo.xml 의 경우 prefix mismatch 라 도달 안 하지만,
            // 안전을 위해 nested path 도 차단).
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
}
