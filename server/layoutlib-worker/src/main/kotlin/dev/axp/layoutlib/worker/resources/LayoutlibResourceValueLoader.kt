package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists

/**
 * W3D4 §3.1 #7: 3-입력 통합 loader (framework + app + 41 AAR).
 * JVM-wide cache (Args 3-tuple key). wall-clock 진단.
 * W3D1 FrameworkResourceValueLoader 가 본 로더의 framework path 에 흡수됨.
 */
internal object LayoutlibResourceValueLoader
{

    data class Args(
        val distDataDir: Path,
        val sampleAppRoot: Path,
        val runtimeClasspathTxt: Path,
    )

    private val cache = ConcurrentHashMap<Args, LayoutlibResourceBundle>()

    fun loadOrGet(args: Args): LayoutlibResourceBundle
    {
        return cache.computeIfAbsent(args) { build(it) }
    }

    fun clearCache() = cache.clear()

    private fun build(args: Args): LayoutlibResourceBundle
    {
        val tFramework0 = System.nanoTime()
        val frameworkEntries = loadFramework(args.distDataDir)
        val tFramework = ms(tFramework0)

        val tApp0 = System.nanoTime()
        val appEntries = loadApp(args.sampleAppRoot)
        val tApp = ms(tApp0)

        val tAar0 = System.nanoTime()
        val aarResults = if (args.runtimeClasspathTxt.exists()) AarResourceWalker.walkAll(args.runtimeClasspathTxt) else emptyList()
        val tAar = ms(tAar0)

        // RES_AUTO bucket = app + aar 통합. 순회 순서: AAR (classpath txt 순) → app (마지막 — sample-app 정의 우선).
        val resAutoEntries = aarResults.flatMap { it.entries } + appEntries

        // framework-only (app/aar 모두 없음) 시 RES_AUTO bucket 자체를 생성 안 함 (single-bucket 보장).
        val perNs = if (resAutoEntries.isEmpty())
        {
            mapOf(ResourceNamespace.ANDROID to frameworkEntries)
        }
        else
        {
            mapOf(
                ResourceNamespace.ANDROID to frameworkEntries,
                ResourceNamespace.RES_AUTO to resAutoEntries,
            )
        }

        val tBuild0 = System.nanoTime()
        val bundle = LayoutlibResourceBundle.build(perNs)
        val tBuild = ms(tBuild0)

        System.err.println(
            "[LayoutlibResourceValueLoader] cold-start framework=${tFramework}ms app=${tApp}ms aar=${tAar}ms build=${tBuild}ms total=${tFramework + tApp + tAar + tBuild}ms"
        )
        return bundle
    }

    private fun loadFramework(distDataDir: Path): List<ParsedNsEntry>
    {
        require(distDataDir.exists()) { "framework data 디렉토리 없음: $distDataDir" }
        val valuesDir = distDataDir.resolve(ResourceLoaderConstants.VALUES_DIR)
        val entries = mutableListOf<ParsedNsEntry>()
        for (filename in ResourceLoaderConstants.REQUIRED_FILES)
        {
            val path = valuesDir.resolve(filename)
            require(path.exists()) { "필수 framework XML 누락: $path" }
            entries += NamespaceAwareValueParser.parse(path, ResourceNamespace.ANDROID, null)
        }
        return entries
    }

    private fun loadApp(sampleAppRoot: Path): List<ParsedNsEntry>
    {
        val appValues = sampleAppRoot.resolve(AppLibraryResourceConstants.SAMPLE_APP_RES_VALUES_RELATIVE_PATH)
        if (!appValues.exists()) return emptyList()
        val entries = mutableListOf<ParsedNsEntry>()
        // v2 round 2 follow-up #8 (Codex Q7): Files.list 는 filesystem-dependent order →
        // .sorted() 로 lex 순서 고정 (cross-platform deterministic).
        Files.list(appValues).use { stream ->
            val sortedXmlFiles = stream
                .filter { it.toString().endsWith(".xml") }
                .sorted()
                .toList()
            for (file in sortedXmlFiles)
            {
                entries += NamespaceAwareValueParser.parse(file, ResourceNamespace.RES_AUTO, null)
            }
        }
        return entries
    }

    private fun ms(t0: Long): Long = (System.nanoTime() - t0) / 1_000_000
}
