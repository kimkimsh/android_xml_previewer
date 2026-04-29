package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SampleAppClassLoaderTest {

    private fun makeTinyAar(parent: Path, name: String): Path {
        val aar = parent.resolve("$name${ClassLoaderConstants.AAR_EXTENSION}")
        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
            zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
            zos.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            zos.closeEntry()
        }
        return aar
    }

    private fun makeTinyJar(parent: Path, name: String): Path {
        val jar = parent.resolve("$name${ClassLoaderConstants.JAR_EXTENSION}")
        ZipOutputStream(Files.newOutputStream(jar)).use { zos ->
            zos.putNextEntry(ZipEntry("dummy.txt"))
            zos.write("ok".toByteArray())
            zos.closeEntry()
        }
        return jar
    }

    /**
     * sample-app module 디렉토리 구조 시뮬레이션.
     * - manifest 디렉토리 생성.
     * - R.jar 파일 생성 (실제 R class 는 없지만 Files.isRegularFile 만 통과).
     */
    private fun setupModuleRoot(root: Path): Path {
        val mfDir = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH).parent
        Files.createDirectories(mfDir)
        val rJarDir = root.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH).parent
        Files.createDirectories(rJarDir)
        val rJar = root.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH)
        Files.copy(makeTinyJar(root, "rjar-source"), rJar)
        return root
    }

    @Test
    fun `정상 빌드 — parent chain + AAR classes jar + R_jar 모두 URL 에 포함`(@TempDir root: Path) {
        setupModuleRoot(root)
        val aar = makeTinyAar(root, "lib")
        val jar = makeTinyJar(root, "extra")
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.writeString(mf, "${aar.toAbsolutePath()}\n${jar.toAbsolutePath()}")

        val parent = ClassLoader.getSystemClassLoader()
        val cl = SampleAppClassLoader.build(root, parent)
        assertSame(parent, cl.classLoader.parent)
        assertEquals(3, cl.urls.size)
    }

    @Test
    fun `R_jar 누락 — IllegalArgumentException 메시지에 R_jar 포함`(@TempDir root: Path) {
        val mfDir = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH).parent
        Files.createDirectories(mfDir)
        val realJar = makeTinyJar(root, "stub")
        Files.writeString(
            root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH),
            realJar.toAbsolutePath().toString(),
        )
        val parent = ClassLoader.getSystemClassLoader()
        val ex = assertThrows<IllegalArgumentException> { SampleAppClassLoader.build(root, parent) }
        assertTrue(ex.message!!.contains("R.jar"), "메시지에 R.jar 포함 필요: ${ex.message}")
    }

    @Test
    fun `manifest 의 jar 항목은 그대로 URL 화 됨`(@TempDir root: Path) {
        setupModuleRoot(root)
        val jar = makeTinyJar(root, "passthru")
        Files.writeString(root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH), jar.toAbsolutePath().toString())
        val cl = SampleAppClassLoader.build(root, ClassLoader.getSystemClassLoader())
        assertTrue(cl.urls.any { it.toString().endsWith("passthru${ClassLoaderConstants.JAR_EXTENSION}") })
    }
}
