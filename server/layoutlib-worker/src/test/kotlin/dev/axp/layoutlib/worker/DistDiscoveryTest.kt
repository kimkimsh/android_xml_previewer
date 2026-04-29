package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DistDiscoveryTest {

    @Test
    fun `override 가 존재 디렉토리이면 해당 경로 반환`(@TempDir tempDir: Path) {
        val customDist = tempDir.resolve("custom-layoutlib").also { Files.createDirectory(it) }

        val found = DistDiscovery.locate(customDist)

        assertEquals(customDist, found)
    }

    @Test
    fun `override 경로가 비존재면 IllegalArgumentException`(@TempDir tempDir: Path) {
        val nonexistent = tempDir.resolve("does-not-exist")

        val ex = assertThrows<IllegalArgumentException> {
            DistDiscovery.locate(nonexistent)
        }
        assertTrue(
            ex.message!!.contains(nonexistent.toString()),
            "에러 메시지에 경로 포함 필요: ${ex.message}",
        )
    }

    @Test
    fun `override null + candidate match 시 해당 경로 반환`(@TempDir tempDir: Path) {
        val tSyntheticRoot = "fake-libs"  // arbitrary test root, any non-conflicting name works
        val target = tempDir.resolve(tSyntheticRoot).resolve(DistDiscovery.LAYOUTLIB_DIST_SUBDIR)
        Files.createDirectories(target)

        val found = DistDiscovery.locateInternal(
            override = null,
            userDir = tempDir.toString(),
            candidateRoots = listOf(tSyntheticRoot),
        )

        assertEquals(target, found)
    }

    @Test
    fun `override null + 모든 candidate 실패 시 null 반환`(@TempDir tempDir: Path) {
        val tMissingRoot1 = "no-such"
        val tMissingRoot2 = "not-here"
        val found = DistDiscovery.locateInternal(
            override = null,
            userDir = tempDir.toString(),
            candidateRoots = listOf(tMissingRoot1, tMissingRoot2),
        )

        assertNull(found)
    }
}
