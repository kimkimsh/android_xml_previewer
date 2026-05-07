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
 * Sibling to AarResourceWalkerColorTest covering AarResourceWalker.collectDrawableXmls.
 * Validates that res/drawable/<name>.xml entries are emitted as ParsedNsEntry.DrawableXml
 * with the original raw body preserved, that qualifier directories are skipped, and that
 * a code-only AAR returns null even when a drawable directory entry is the lone resource.
 */
class AarResourceWalkerDrawableTest
{

    private fun buildMockAar(
        root: Path,
        aarName: String,
        manifestPkg: String,
        valuesXml: String?,
        drawableEntries: List<Pair<String, String>>,
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
            for ((path, body) in drawableEntries)
            {
                zos.putNextEntry(ZipEntry(path))
                zos.write(body.toByteArray())
                zos.closeEntry()
            }
        }
        return aar
    }

    @Test
    fun `default qualifier res slash drawable XML emits DrawableXml entries`(@TempDir root: Path)
    {
        val vectorBody =
            """<?xml version="1.0" encoding="utf-8"?>
            |<vector xmlns:android="http://schemas.android.com/apk/res/android"
            |    android:width="24dp" android:height="24dp"
            |    android:viewportWidth="24" android:viewportHeight="24">
            |    <path android:fillColor="#000000" android:pathData="M12 2 L22 22 H2 Z"/>
            |</vector>""".trimMargin()
        val aar = buildMockAar(
            root, "mock-drawable.aar", "com.example.lib",
            valuesXml = """<resources><color name="primary">#ff0000</color></resources>""",
            drawableEntries = listOf(
                "res/drawable/ic_m3_chip_close.xml" to vectorBody,
                "res/drawable/ic_m3_chip_check.xml" to vectorBody,
            ),
        )
        val result = AarResourceWalker.walkOne(aar)!!
        val drawableEntries = result.entries.filterIsInstance<ParsedNsEntry.DrawableXml>()
        assertEquals(2, drawableEntries.size, "two res/drawable XML entries emitted")
        val byName = drawableEntries.associateBy { it.name }
        assertNotNull(byName["ic_m3_chip_close"])
        assertEquals(vectorBody, byName["ic_m3_chip_close"]!!.rawXml)
        assertEquals("com.example.lib", byName["ic_m3_chip_close"]!!.sourcePackage)
    }

    @Test
    fun `qualifier directories res slash drawable-night and density variants are skipped`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-qualifier.aar", "com.example.qual",
            valuesXml = "<resources/>",
            drawableEntries = listOf(
                "res/drawable/base.xml" to "<vector/>",
                "res/drawable-night/dark.xml" to "<vector/>",
                "res/drawable-v21/lollipop.xml" to "<vector/>",
                "res/drawable-hdpi/density.xml" to "<vector/>",
            ),
        )
        val result = AarResourceWalker.walkOne(aar)!!
        val drawableEntries = result.entries.filterIsInstance<ParsedNsEntry.DrawableXml>()
        assertEquals(1, drawableEntries.size, "default qualifier only — qualifier dirs reserved for W4+")
        assertEquals("base", drawableEntries.single().name)
    }

    @Test
    fun `values xml absent plus drawable XML present yields partial use rather than skip`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-drawable-only.aar", "com.example.drawableonly",
            valuesXml = null,
            drawableEntries = listOf("res/drawable/foo.xml" to "<vector/>"),
        )
        val result = AarResourceWalker.walkOne(aar)
        assertNotNull(result, "drawable-only AAR is partially used")
        assertEquals(1, result!!.entries.size)
        assertTrue(result.entries.first() is ParsedNsEntry.DrawableXml)
    }

    @Test
    fun `values xml plus drawable both absent yields null code-only AAR`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-empty.aar", "com.example.empty",
            valuesXml = null,
            drawableEntries = emptyList(),
        )
        assertNull(AarResourceWalker.walkOne(aar), "code-only AAR with no res entries returns null")
    }
}
