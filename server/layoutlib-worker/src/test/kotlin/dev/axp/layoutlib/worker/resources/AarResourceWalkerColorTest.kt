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
 * W3D4-β T12: AarResourceWalker 의 res/color/{name}.xml enumeration 검증.
 * mock AAR 을 ZipOutputStream 으로 합성 — Material AAR 의존성 없이 walker 동작 단독 검증.
 */
class AarResourceWalkerColorTest
{

    private fun buildMockAar(
        root: Path,
        aarName: String,
        manifestPkg: String,
        valuesXml: String?,
        colorEntries: List<Pair<String, String>>,
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
            for ((path, body) in colorEntries)
            {
                zos.putNextEntry(ZipEntry(path))
                zos.write(body.toByteArray())
                zos.closeEntry()
            }
        }
        return aar
    }

    @Test
    fun `res slash color slash {name}_xml 만 ColorStateList 로 emit`(@TempDir root: Path)
    {
        val selectorBody =
            """<?xml version="1.0" encoding="utf-8"?>
            |<selector xmlns:android="http://schemas.android.com/apk/res/android">
            |    <item android:color="@color/m3_sys_color_light_primary" />
            |</selector>""".trimMargin()
        val aar = buildMockAar(
            root, "mock-color.aar", "com.example.lib",
            valuesXml = """<resources><color name="primary">#ff0000</color></resources>""",
            colorEntries = listOf(
                "res/color/m3_highlighted_text.xml" to selectorBody,
                "res/color/another_selector.xml" to selectorBody,
            ),
        )
        val result = AarResourceWalker.walkOne(aar)!!
        val colorEntries = result.entries.filterIsInstance<ParsedNsEntry.ColorStateList>()
        assertEquals(2, colorEntries.size, "두 res/color XML emit")
        val byName = colorEntries.associateBy { it.name }
        assertNotNull(byName["m3_highlighted_text"])
        assertEquals(selectorBody, byName["m3_highlighted_text"]!!.rawXml)
        assertEquals("com.example.lib", byName["m3_highlighted_text"]!!.sourcePackage)
    }

    @Test
    fun `qualifier dir res slash color-night 은 skip`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-qualifier.aar", "com.example.qual",
            valuesXml = "<resources/>",
            colorEntries = listOf(
                "res/color/base.xml" to "<selector/>",
                "res/color-night-v8/material_timepicker.xml" to "<selector/>",
                "res/color-v31/m3_dynamic.xml" to "<selector/>",
                "res/color-v23/abc_btn.xml" to "<selector/>",
            ),
        )
        val result = AarResourceWalker.walkOne(aar)!!
        val colorEntries = result.entries.filterIsInstance<ParsedNsEntry.ColorStateList>()
        assertEquals(1, colorEntries.size, "default 만 emit (qualifier 는 W4+ scope)")
        assertEquals("base", colorEntries.single().name)
    }

    @Test
    fun `values_xml 부재 + color XML 존재 시 부분 이용 — null 반환 안 함`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-color-only.aar", "com.example.coloronly",
            valuesXml = null,
            colorEntries = listOf("res/color/foo.xml" to "<selector/>"),
        )
        val result = AarResourceWalker.walkOne(aar)
        assertNotNull(result, "color 만 있어도 부분 이용")
        assertEquals(1, result!!.entries.size)
        assertTrue(result.entries.first() is ParsedNsEntry.ColorStateList)
    }

    @Test
    fun `values_xml + color 모두 부재 → null (code-only AAR)`(@TempDir root: Path)
    {
        val aar = buildMockAar(
            root, "mock-empty.aar", "com.example.empty",
            valuesXml = null,
            colorEntries = emptyList(),
        )
        assertNull(AarResourceWalker.walkOne(aar), "둘 다 없으면 code-only skip")
    }
}
