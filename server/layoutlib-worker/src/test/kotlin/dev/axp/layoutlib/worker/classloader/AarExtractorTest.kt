package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AarExtractorTest {

    private fun makeAar(parent: Path, name: String, withClassesJar: Boolean): Path {
        val aar = parent.resolve("$name${ClassLoaderConstants.AAR_EXTENSION}")
        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("<?xml version='1.0'?><manifest/>".toByteArray())
            zos.closeEntry()
            if (withClassesJar)
            {
                zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
                zos.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
                zos.closeEntry()
            }
        }
        return aar
    }

    @Test
    fun `정상 추출 — classes_jar 가 cache 에 atomic 으로 생성`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val aar = makeAar(root, "lib", withClassesJar = true)
        val extracted = AarExtractor.extract(aar, cacheRoot)
        assertNotNull(extracted)
        assertTrue(Files.isRegularFile(extracted))
        assertTrue(extracted!!.fileName.toString().endsWith(ClassLoaderConstants.EXTRACTED_JAR_SUFFIX))
        val tmpName = "lib${ClassLoaderConstants.TEMP_JAR_SUFFIX}"
        Files.list(extracted.parent).use { stream ->
            assertTrue(stream.noneMatch { it.fileName.toString() == tmpName })
        }
    }

    @Test
    fun `cache hit — 두 번째 호출이 동일 path, 재추출 없음`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val aar = makeAar(root, "lib", withClassesJar = true)
        val first = AarExtractor.extract(aar, cacheRoot)!!
        val firstMtime = Files.getLastModifiedTime(first).toMillis()
        Thread.sleep(15)
        val second = AarExtractor.extract(aar, cacheRoot)!!
        assertEquals(first, second)
        assertEquals(firstMtime, Files.getLastModifiedTime(second).toMillis())
    }

    @Test
    fun `classes_jar 없는 AAR — null 반환`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val aar = makeAar(root, "resonly", withClassesJar = false)
        assertNull(AarExtractor.extract(aar, cacheRoot))
    }

    @Test
    fun `존재하지 않는 AAR — IllegalArgumentException`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val missing = root.resolve("missing.aar")
        assertThrows<IllegalArgumentException> { AarExtractor.extract(missing, cacheRoot) }
    }

    @Test
    fun `손상된 ZIP — ZipException`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val corrupted = root.resolve("broken${ClassLoaderConstants.AAR_EXTENSION}")
        Files.write(corrupted, byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0))
        assertThrows<java.util.zip.ZipException> { AarExtractor.extract(corrupted, cacheRoot) }
    }
}
