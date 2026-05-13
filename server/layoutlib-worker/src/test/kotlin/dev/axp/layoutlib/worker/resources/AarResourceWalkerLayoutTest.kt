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
 * Sibling to AarResourceWalkerInterpolatorTest covering AarResourceWalker
 * .collectLayoutXmls. Validates that res/layout/<name>.xml entries are emitted as
 * ParsedNsEntry.LayoutXml with the original raw body preserved, that qualifier
 * directories are skipped, that an AAR with only a layout XML present (no
 * values.xml) still gets partial-use, and that an AAR with neither values nor
 * any layout XML returns null as a true code-only AAR.
 */
class AarResourceWalkerLayoutTest
{

    private fun buildMockAar(
        root: Path,
        aarName: String,
        manifestPkg: String,
        valuesXml: String?,
        layoutEntries: List<Pair<String, String>>,
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
            for ((path, body) in layoutEntries)
            {
                zos.putNextEntry(ZipEntry(path))
                zos.write(body.toByteArray())
                zos.closeEntry()
            }
        }
        return aar
    }

    @Test
    fun `default qualifier res slash layout XML emits LayoutXml entries`(@TempDir root: Path)
    {
        val startIconBody =
            """<?xml version="1.0" encoding="utf-8"?>
            |<com.google.android.material.internal.CheckableImageButton
            |    xmlns:android="http://schemas.android.com/apk/res/android"
            |    android:id="@+id/text_input_start_icon"
            |    android:layout_width="wrap_content"
            |    android:layout_height="wrap_content"/>""".trimMargin()
        val aar = buildMockAar(
            root, "mock-layout.aar", "com.example.lib",
            valuesXml = """<resources><color name="primary">#ff0000</color></resources>""",
            layoutEntries = listOf(
                "res/layout/design_text_input_start_icon.xml" to startIconBody,
                "res/layout/design_text_input_end_icon.xml" to startIconBody,
            ),
        )
        val result = AarResourceWalker.walkOne(aar)!!
        val layoutEntries = result.entries.filterIsInstance<ParsedNsEntry.LayoutXml>()
        assertEquals(2, layoutEntries.size, "two res/layout XML entries emitted")
        val byName = layoutEntries.associateBy { it.name }
        assertNotNull(byName["design_text_input_start_icon"])
        assertEquals(startIconBody, byName["design_text_input_start_icon"]!!.rawXml)
        assertEquals("com.example.lib", byName["design_text_input_start_icon"]!!.sourcePackage)
    }

    @Test
    fun `qualifier directories res slash layout-v21 and night variants are skipped`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-qualifier.aar", "com.example.qual",
            valuesXml = "<resources/>",
            layoutEntries = listOf(
                "res/layout/base.xml" to "<View/>",
                "res/layout-v21/lollipop.xml" to "<View/>",
                "res/layout-night/dark.xml" to "<View/>",
            ),
        )
        val result = AarResourceWalker.walkOne(aar)!!
        val layoutEntries = result.entries.filterIsInstance<ParsedNsEntry.LayoutXml>()
        assertEquals(1, layoutEntries.size, "default qualifier only — qualifier dirs reserved for W4+")
        assertEquals("base", layoutEntries.single().name)
    }

    @Test
    fun `values xml absent plus layout XML present yields partial use rather than skip`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-layout-only.aar", "com.example.layoutonly",
            valuesXml = null,
            layoutEntries = listOf("res/layout/foo.xml" to "<View/>"),
        )
        val result = AarResourceWalker.walkOne(aar)
        assertNotNull(result, "layout-only AAR is partially used")
        assertEquals(1, result!!.entries.size)
        assertTrue(result.entries.first() is ParsedNsEntry.LayoutXml)
    }

    @Test
    fun `values xml plus layout both absent yields null code-only AAR`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-empty.aar", "com.example.empty",
            valuesXml = null,
            layoutEntries = emptyList(),
        )
        assertNull(AarResourceWalker.walkOne(aar), "code-only AAR with no res entries returns null")
    }
}
