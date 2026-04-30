package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * W3D4 §3.1 #5: runtime-classpath.txt → 41 AAR walker.
 * - 각 AAR 의 AndroidManifest 의 package 를 추출 (진단/dedupe-source 추적용).
 * - res/values/values.xml 부재 → silent skip + 1줄 로깅 (γ 정책).
 * - namespace 는 round 2 mode 통일에 따라 항상 RES_AUTO.
 * - 전체 wall-clock + 카운트 진단 출력 (Codex Q4).
 *
 * W3D4-β T12: `res slash color slash {name}.xml` (default qualifier 만 — color-v31/ color-night-v8/ 등은
 * W4+ scope) 도 enumerate 하여 ParsedNsEntry.ColorStateList 로 emit. <selector> 파싱은
 * layoutlib Bridge 에 위임 — 본 walker 는 raw XML 문자열만 책임.
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
        for (aar in aarPaths)
        {
            val r = walkOne(aar)
            if (r != null)
            {
                results += r
                totalColorXmls += r.entries.count { it is ParsedNsEntry.ColorStateList }
            }
            else
            {
                skipped++
            }
        }
        val tMs = (System.nanoTime() - t0) / 1_000_000
        System.err.println(
            "[AarResourceWalker] walked ${aarPaths.size} AARs (${results.size} with res, $skipped code-only, $totalColorXmls color-state-lists) in ${tMs}ms",
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

            // W3D4-β T12: values.xml 또는 color/*.xml 둘 중 하나라도 있으면 부분 이용.
            // 둘 다 없으면 진짜 code-only (logging unchanged from γ 정책).
            if (valuesEntries.isEmpty() && colorEntries.isEmpty())
            {
                if (valuesEntry == null)
                {
                    System.err.println(
                        "[AarResourceWalker] $aarPath skipped — res/values/values.xml + res slash color slash {name}.xml 모두 없음 (pkg=$pkg)",
                    )
                }
                return null
            }
            return Result(pkg, valuesEntries + colorEntries)
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
}
