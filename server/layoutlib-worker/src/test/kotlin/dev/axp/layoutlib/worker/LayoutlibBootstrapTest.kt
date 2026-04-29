package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * W1D1 L1 spike scaffolding 검증.
 * 실제 Bridge.init 호출은 JAR 과 native lib 를 필요로 하므로 이 테스트 범위 밖 (W1D4 에서).
 */
class LayoutlibBootstrapTest {

    @Test
    fun `constructor rejects non-existent dir`() {
        assertThrows<IllegalArgumentException> {
            LayoutlibBootstrap(Path.of("/tmp/this-path-should-not-exist-axp"))
        }
    }

    @Test
    fun `validate reports missing components on empty dist dir`(@TempDir tmp: Path) {
        val distDir = tmp.resolve("layoutlib-dist/android-34").apply { createDirectories() }
        val boot = LayoutlibBootstrap(distDir)
        val result = boot.validate()
        assertInstanceOf(LayoutlibBootstrap.ValidationResult.MissingComponents::class.java, result)
        val missing = (result as LayoutlibBootstrap.ValidationResult.MissingComponents).components
        assertTrue(missing.any { it.startsWith("layoutlib-*.jar") },
            "layoutlib-*.jar 누락이 missing 에 포함되어야 함 (08 §7.5 정명화): $missing")
        assertTrue(missing.any { it.contains("data/res") })
        assertTrue(missing.any { it.contains("data/fonts") })
    }

    @Test
    fun `validate reports ok when all required components present`(@TempDir tmp: Path) {
        val distDir = tmp.resolve("dist").apply { createDirectories() }
        // fake JAR + required data dirs
        Files.createFile(distDir.resolve("layoutlib-14.0.11.jar"))
        Files.createFile(distDir.resolve("layoutlib-api-31.13.2.jar"))
        distDir.resolve("data/res").createDirectories()
        distDir.resolve("data/fonts").createDirectories()
        distDir.resolve("data/icu").createDirectories()

        val boot = LayoutlibBootstrap(distDir)
        assertEquals(LayoutlibBootstrap.ValidationResult.Ok, boot.validate())
    }

    @Test
    fun `findLayoutlibJar picks highest version by lexicographic sort`(@TempDir tmp: Path) {
        val distDir = tmp.resolve("dist").apply { createDirectories() }
        Files.createFile(distDir.resolve("layoutlib-13.0.0.jar"))
        Files.createFile(distDir.resolve("layoutlib-14.0.11.jar"))
        // sibling Maven artifacts that must NOT be picked (08 §7.5)
        Files.createFile(distDir.resolve("layoutlib-api-31.13.2.jar"))
        Files.createFile(distDir.resolve("layoutlib-runtime-14.0.11-linux.jar"))
        Files.createFile(distDir.resolve("layoutlib-resources-14.0.11.jar"))
        Files.createFile(distDir.resolve("other-unrelated.jar"))

        val boot = LayoutlibBootstrap(distDir)
        val lookup = boot.findLayoutlibJar()
        assertTrue(lookup.exists)
        assertTrue(lookup.path?.fileName?.toString() == "layoutlib-14.0.11.jar",
            "최신 버전 JAR 이 선택되어야 함 (sibling artifacts 제외): ${lookup.path}")
    }

    @Test
    fun `findLayoutlibJar returns null exists false when no jar`(@TempDir tmp: Path) {
        val distDir = tmp.resolve("dist").apply { createDirectories() }
        val boot = LayoutlibBootstrap(distDir)
        val lookup = boot.findLayoutlibJar()
        assertFalse(lookup.exists)
    }
}
