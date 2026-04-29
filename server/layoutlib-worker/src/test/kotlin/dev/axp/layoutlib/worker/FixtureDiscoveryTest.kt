package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FixtureDiscoveryTest {

    @Test
    fun `override 가 존재 디렉토리이면 해당 경로 반환`(@TempDir tempDir: Path) {
        val customFixture = tempDir.resolve("custom-fixture").also { Files.createDirectory(it) }

        val found = FixtureDiscovery.locate(customFixture)

        assertEquals(customFixture, found)
    }

    @Test
    fun `override 경로가 비존재면 IllegalArgumentException`(@TempDir tempDir: Path) {
        val nonexistent = tempDir.resolve("missing-fixture")

        val ex = assertThrows<IllegalArgumentException> {
            FixtureDiscovery.locate(nonexistent)
        }
        assertTrue(
            ex.message!!.contains(nonexistent.toString()),
            "에러 메시지에 경로 포함 필요: ${ex.message}",
        )
    }

    @Test
    fun `override null + candidate match 시 해당 경로 반환`(@TempDir tempDir: Path) {
        val tSyntheticRoot = "myroot"
        val target = tempDir.resolve(tSyntheticRoot).resolve(FixtureDiscovery.FIXTURE_SUBPATH)
        Files.createDirectories(target)

        val found = FixtureDiscovery.locateInternal(
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
        val found = FixtureDiscovery.locateInternal(
            override = null,
            userDir = tempDir.toString(),
            candidateRoots = listOf(tMissingRoot1, tMissingRoot2),
        )

        assertNull(found)
    }

    @Test
    fun `empty root candidate (CWD) 가 userDir 기준으로 FIXTURE_SUBPATH 해석`(@TempDir tempDir: Path) {
        val target = tempDir.resolve(FixtureDiscovery.FIXTURE_SUBPATH)
        Files.createDirectories(target)

        val found = FixtureDiscovery.locateInternal(
            override = null,
            userDir = tempDir.toString(),
            candidateRoots = listOf(""),
        )

        assertEquals(target, found)
    }
}
