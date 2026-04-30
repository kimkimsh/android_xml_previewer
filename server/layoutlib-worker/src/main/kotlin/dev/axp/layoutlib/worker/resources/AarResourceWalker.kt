package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * W3D4 §3.1 #5: runtime-classpath.txt → 41 AAR walker.
 * - 각 AAR 의 AndroidManifest 의 package 를 추출 (진단/dedupe-source 추적용).
 * - res/values/values.xml 부재 → silent skip + 1줄 로깅 (γ 정책).
 * - namespace 는 round 2 mode 통일에 따라 항상 RES_AUTO.
 * - 전체 wall-clock + 카운트 진단 출력 (Codex Q4).
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
        for (aar in aarPaths)
        {
            val r = walkOne(aar)
            if (r != null) results += r else skipped++
        }
        val tMs = (System.nanoTime() - t0) / 1_000_000
        System.err.println(
            "[AarResourceWalker] walked ${aarPaths.size} AARs (${results.size} with values, $skipped code-only) in ${tMs}ms"
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
            if (valuesEntry == null)
            {
                System.err.println(
                    "[AarResourceWalker] $aarPath skipped — res/values/values.xml 없음 (pkg=$pkg)"
                )
                return null
            }

            // values.xml 을 임시 파일로 풀어서 NamespaceAwareValueParser 에 넘김 (StAX 가 InputStream 보다 Path 친화적).
            val tmp = Files.createTempFile("aarvals", ".xml")
            tmp.toFile().deleteOnExit()
            zip.getInputStream(valuesEntry).use { stream ->
                Files.copy(stream, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            val entries = NamespaceAwareValueParser.parse(tmp, ResourceNamespace.RES_AUTO, pkg)
            return Result(pkg, entries)
        }
    }
}
