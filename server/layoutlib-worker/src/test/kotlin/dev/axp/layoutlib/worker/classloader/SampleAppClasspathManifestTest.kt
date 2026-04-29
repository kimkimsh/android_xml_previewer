package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class SampleAppClasspathManifestTest {

    @Test
    fun `누락 파일 — IllegalArgumentException 명확 메시지`(@TempDir root: Path)
    {
        val ex = assertThrows<IllegalArgumentException> {
            SampleAppClasspathManifest.read(root)
        }
        assertTrue(ex.message!!.contains("manifest 누락"))
        assertTrue(ex.message!!.contains("./gradlew :app:assembleDebug"))
    }

    @Test
    fun `빈 파일 — IllegalStateException`(@TempDir root: Path)
    {
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.createDirectories(mf.parent)
        Files.writeString(mf, "")
        assertThrows<IllegalStateException> { SampleAppClasspathManifest.read(root) }
    }

    @Test
    fun `공백 라인 포함 — IllegalArgumentException with line index`(@TempDir root: Path)
    {
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.createDirectories(mf.parent)
        val realJar = Files.createFile(root.resolve("real.jar"))
        Files.writeString(mf, "${realJar.toAbsolutePath()}\n   \n")
        val ex = assertThrows<IllegalArgumentException> { SampleAppClasspathManifest.read(root) }
        assertTrue(ex.message!!.contains("line 2"))
    }

    @Test
    fun `비-절대경로 — IllegalArgumentException`(@TempDir root: Path)
    {
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.createDirectories(mf.parent)
        Files.writeString(mf, "relative/path.aar")
        val ex = assertThrows<IllegalArgumentException> { SampleAppClasspathManifest.read(root) }
        assertTrue(ex.message!!.contains("비-절대경로"))
    }

    @Test
    fun `정상 — aar 와 jar 혼합, 순서 유지`(@TempDir root: Path)
    {
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.createDirectories(mf.parent)
        val a = Files.createFile(root.resolve("a.aar"))
        val b = Files.createFile(root.resolve("b.jar"))
        Files.writeString(mf, "${a.toAbsolutePath()}\n${b.toAbsolutePath()}")
        val result = SampleAppClasspathManifest.read(root)
        assertEquals(listOf(a.toAbsolutePath(), b.toAbsolutePath()), result.map { it.toAbsolutePath() })
    }
}
