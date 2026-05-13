package dev.axp.layoutlib.worker.resources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Sibling to AarResourceWalkerDrawableTest covering AarResourceWalker
 * .collectInterpolatorXmls. Validates that res/interpolator/<name>.xml entries are
 * emitted as ParsedNsEntry.InterpolatorXml with the original raw body preserved,
 * that qualifier directories are skipped, that an AAR with only an interpolator
 * XML present (no values.xml) still gets partial-use, and that an AAR with
 * neither values nor any interpolator XML returns null as a true code-only AAR.
 */
class AarResourceWalkerInterpolatorTest
{

    private fun buildMockAar(
        root: Path,
        aarName: String,
        manifestPkg: String,
        valuesXml: String?,
        interpolatorEntries: List<Pair<String, String>>,
    ): Path
    {
        val aar = root.resolve(aarName)
        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(
                """<?xml version="1.0" encoding="utf-8"?>
                |<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$manifestPkg" />""".trimMargin().toByteArray(),
            )
            zos.closeEntry()
            if (valuesXml != null)
            {
                zos.putNextEntry(ZipEntry("res/values/values.xml"))
                zos.write(valuesXml.toByteArray())
                zos.closeEntry()
            }
            for ((path, body) in interpolatorEntries)
            {
                zos.putNextEntry(ZipEntry(path))
                zos.write(body.toByteArray())
                zos.closeEntry()
            }
        }
        return aar
    }

    @Test
    fun `default qualifier res slash interpolator XML emits InterpolatorXml entries`(@TempDir root: Path)
    {
        val emphasizedBody =
            """<?xml version="1.0" encoding="utf-8"?>
            |<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
            |    android:controlX1="0.2" android:controlY1="0"
            |    android:controlX2="0" android:controlY2="1"/>""".trimMargin()
        val aar = buildMockAar(
            root, "mock-interpolator.aar", "com.example.lib",
            valuesXml = """<resources><color name="primary">#ff0000</color></resources>""",
            interpolatorEntries = listOf(
                "res/interpolator/m3_sys_motion_easing_emphasized.xml" to emphasizedBody,
                "res/interpolator/m3_sys_motion_easing_linear.xml" to emphasizedBody,
            ),
        )
        val result = AarResourceWalker.walkOne(aar)!!
        val interpolatorEntries = result.entries.filterIsInstance<ParsedNsEntry.InterpolatorXml>()
        assertEquals(2, interpolatorEntries.size, "two res/interpolator XML entries emitted")
        val byName = interpolatorEntries.associateBy { it.name }
        assertNotNull(byName["m3_sys_motion_easing_emphasized"])
        assertEquals(emphasizedBody, byName["m3_sys_motion_easing_emphasized"]!!.rawXml)
        assertEquals("com.example.lib", byName["m3_sys_motion_easing_emphasized"]!!.sourcePackage)
    }

    @Test
    fun `qualifier directories res slash interpolator-v21 and density variants are skipped`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-qualifier.aar", "com.example.qual",
            valuesXml = "<resources/>",
            interpolatorEntries = listOf(
                "res/interpolator/base.xml" to "<pathInterpolator/>",
                "res/interpolator-v21/lollipop.xml" to "<pathInterpolator/>",
                "res/interpolator-night/dark.xml" to "<pathInterpolator/>",
            ),
        )
        val result = AarResourceWalker.walkOne(aar)!!
        val interpolatorEntries = result.entries.filterIsInstance<ParsedNsEntry.InterpolatorXml>()
        assertEquals(1, interpolatorEntries.size, "default qualifier only — qualifier dirs reserved for W4+")
        assertEquals("base", interpolatorEntries.single().name)
    }

    @Test
    fun `values xml absent plus interpolator XML present yields partial use rather than skip`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-interpolator-only.aar", "com.example.interponly",
            valuesXml = null,
            interpolatorEntries = listOf("res/interpolator/foo.xml" to "<pathInterpolator/>"),
        )
        val result = AarResourceWalker.walkOne(aar)
        assertNotNull(result, "interpolator-only AAR is partially used")
        assertEquals(1, result!!.entries.size)
        assertTrue(result.entries.first() is ParsedNsEntry.InterpolatorXml)
    }

    @Test
    fun `values xml plus interpolator both absent yields null code-only AAR`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-empty.aar", "com.example.empty",
            valuesXml = null,
            interpolatorEntries = emptyList(),
        )
        assertNull(AarResourceWalker.walkOne(aar), "code-only AAR with no res entries returns null")
    }
}
