package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LayoutlibResourceValueLoaderTest
{

    @BeforeEach
    fun clearCache() = LayoutlibResourceValueLoader.clearCache()

    @Test
    fun `framework only path 로 로드 시 ANDROID bucket 만`(@TempDir tmp: Path)
    {
        val args = mockArgs(tmp, withApp = false, withAar = false)
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        assertEquals(listOf(ResourceNamespace.ANDROID), bundle.namespacesInOrder())
    }

    @Test
    fun `framework + app + aar 통합 시 2 bucket`(@TempDir tmp: Path)
    {
        val args = mockArgs(tmp, withApp = true, withAar = true)
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        assertEquals(
            listOf(ResourceNamespace.ANDROID, ResourceNamespace.RES_AUTO),
            bundle.namespacesInOrder(),
        )
    }

    @Test
    fun `cache key 3-tuple 동치 (다른 sampleAppRoot 면 새 build)`(@TempDir tmp: Path)
    {
        // v2 round 2 follow-up #8 (Codex Q7): plan v1 의 assertTrue(true) 가짜 assertion 제거.
        // 동일 args = identity hit, 다른 sampleAppRoot = 새 instance 명시 검증.
        val args1 = mockArgs(tmp, withApp = true, withAar = false)
        val a = LayoutlibResourceValueLoader.loadOrGet(args1)
        val b = LayoutlibResourceValueLoader.loadOrGet(args1)
        assertTrue(a === b, "동일 args 는 동일 instance (cache hit)")

        // 다른 sampleAppRoot — 새 디렉토리에 동일 res 구조 재생성 (require 통과 보장).
        val anotherRoot = Files.createDirectories(tmp.resolve("another-sampleapp"))
        val anotherValues = Files.createDirectories(anotherRoot.resolve("app/src/main/res/values"))
        anotherValues.resolve("themes.xml").toFile().writeText("""<resources/>""")
        val anotherClasspath = anotherRoot.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
        Files.createDirectories(anotherClasspath.parent)
        anotherClasspath.toFile().writeText("")

        val args2 = args1.copy(sampleAppRoot = anotherRoot, runtimeClasspathTxt = anotherClasspath)
        val c = LayoutlibResourceValueLoader.loadOrGet(args2)
        assertNotNull(c, "다른 sampleAppRoot 도 정상 build")
        assertTrue(a !== c, "다른 args 는 다른 instance (cache key 3-tuple 정합)")
    }

    @Test
    fun `wall-clock 진단이 cold-start 시점에 출력`(@TempDir tmp: Path)
    {
        val args = mockArgs(tmp, withApp = true, withAar = true)
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try
        {
            LayoutlibResourceValueLoader.loadOrGet(args)
            val log = errOut.toString()
            assertTrue(log.contains("[LayoutlibResourceValueLoader]"), "loader 진단 prefix")
            assertTrue(log.contains("cold-start"), "cold-start 표기")
            assertTrue(log.contains("ms"), "wall-clock ms")
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `clearCache 후 재계산이 새 instance 반환`(@TempDir tmp: Path)
    {
        // v2 round 2 follow-up #9 (cache invalidation): clearCache 가 실제로 새 instance 를
        // 만드는지 명시 검증 (plan v1 은 "정상 returns" 만 확인).
        val args = mockArgs(tmp, withApp = true, withAar = false)
        val a = LayoutlibResourceValueLoader.loadOrGet(args)
        LayoutlibResourceValueLoader.clearCache()
        val b = LayoutlibResourceValueLoader.loadOrGet(args)
        assertNotNull(b)
        assertTrue(a !== b, "clearCache 후 동일 args 라도 새 instance (재계산)")
    }

    @Test
    fun `dedupe winner — sorted lex order + app last`(@TempDir tmp: Path)
    {
        // v2 round 2 follow-up #7 (Codex Q5): build.gradle.kts:58 의 .sorted() 정책에 따라
        // dedupe winner = lex order. 동명 style 가 두 AAR 에 있으면 lex 마지막 AAR 가 later-wins.
        // app 의 res 는 AAR 후 마지막 → app 정의가 모든 AAR override.
        val distData = Files.createDirectories(tmp.resolve("dist/data"))
        val valuesDir = Files.createDirectories(distData.resolve(ResourceLoaderConstants.VALUES_DIR))
        for (filename in ResourceLoaderConstants.REQUIRED_FILES)
        {
            valuesDir.resolve(filename).toFile().writeText("""<resources/>""")
        }
        // 두 AAR — lex order 로 a-aar 가 먼저, b-aar 가 나중. 결정론적 이름 (createTempFile 의 random
        // suffix 가 ordering 을 흔드는 것을 방지).
        val aarA = makeAarAt(tmp.resolve("a.aar"), """<manifest package="com.a"/>""", """<resources><style name="X"><item name="k">A</item></style></resources>""")
        val aarB = makeAarAt(tmp.resolve("b.aar"), """<manifest package="com.b"/>""", """<resources><style name="X"><item name="k">B</item></style></resources>""")
        val sortedAars = listOf(aarA.toString(), aarB.toString()).sorted()  // axpEmitClasspath 와 동일 정책

        val sampleAppRoot = Files.createDirectories(tmp.resolve("sampleapp"))
        val classpathTxt = sampleAppRoot.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
        Files.createDirectories(classpathTxt.parent)
        classpathTxt.toFile().writeText(sortedAars.joinToString("\n"))

        val bundle = LayoutlibResourceValueLoader.loadOrGet(LayoutlibResourceValueLoader.Args(distData, sampleAppRoot, classpathTxt))
        val winner = bundle.getStyleExact(com.android.ide.common.rendering.api.ResourceReference(
            ResourceNamespace.RES_AUTO, com.android.resources.ResourceType.STYLE, "X"
        ))
        assertNotNull(winner)
        // lex 마지막 AAR 가 later-wins: "B" 가 winner.
        assertEquals("B", winner!!.getItem(
            com.android.ide.common.rendering.api.ResourceReference(ResourceNamespace.RES_AUTO, com.android.resources.ResourceType.ATTR, "k")
        )?.value)
    }

    // Mock helper — 최소한의 distDataDir + sampleAppRoot + classpathTxt 구조
    private fun mockArgs(tmp: Path, withApp: Boolean, withAar: Boolean): LayoutlibResourceValueLoader.Args
    {
        val distData = Files.createDirectories(tmp.resolve("dist/data"))
        // 10 framework XML 의 minimal stub
        val valuesDir = Files.createDirectories(distData.resolve(ResourceLoaderConstants.VALUES_DIR))
        for (filename in ResourceLoaderConstants.REQUIRED_FILES)
        {
            valuesDir.resolve(filename).toFile().writeText("""<resources/>""")
        }

        val sampleAppRoot = Files.createDirectories(tmp.resolve("sampleapp"))
        if (withApp)
        {
            val appValues = Files.createDirectories(sampleAppRoot.resolve("app/src/main/res/values"))
            appValues.resolve("themes.xml").toFile().writeText(
                """<resources><style name="Theme.AxpFixture" parent="Theme.Material3.DayNight.NoActionBar"/></resources>"""
            )
        }

        val classpathTxt = sampleAppRoot.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
        Files.createDirectories(classpathTxt.parent)
        if (withAar)
        {
            val aar = makeAar(tmp, """<manifest package="com.x"/>""", """<resources><dimen name="d">1dp</dimen></resources>""")
            classpathTxt.toFile().writeText(aar.toString())
        }
        else
        {
            classpathTxt.toFile().writeText("")
        }
        return LayoutlibResourceValueLoader.Args(distData, sampleAppRoot, classpathTxt)
    }

    private fun makeAar(parent: Path, manifest: String, values: String): Path
    {
        val f = Files.createTempFile(parent, "test", ".aar")
        return writeAar(f, manifest, values)
    }

    // 결정론적 경로의 AAR — dedupe winner test 처럼 lex-ordering 검증이 필요한 경우 사용.
    private fun makeAarAt(target: Path, manifest: String, values: String): Path
    {
        return writeAar(target, manifest, values)
    }

    private fun writeAar(target: Path, manifest: String, values: String): Path
    {
        ZipOutputStream(target.toFile().outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml")); zos.write(manifest.toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("res/values/values.xml")); zos.write(values.toByteArray()); zos.closeEntry()
        }
        return target
    }
}
