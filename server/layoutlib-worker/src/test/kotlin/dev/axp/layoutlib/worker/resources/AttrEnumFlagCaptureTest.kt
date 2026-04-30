package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * W3D4-γ T14: NamespaceAwareValueParser 의 `<attr>` 자식 `<enum>/<flag>` 캡처 단위 테스트.
 *
 * round 4 reconcile (Codex Q2): Long.decode 가 32-bit unsigned hex (0x80000000, 0xffffffff)
 * 와 음수 십진까지 cover — Integer.decode 는 0x80000000 등에서 NumberFormatException 발화.
 */
internal class AttrEnumFlagCaptureTest
{
    @Test
    fun `top-level attr with enum children — values map populated`()
    {
        val xml = """
            <resources>
              <attr name="orientation">
                <enum name="horizontal" value="0"/>
                <enum name="vertical" value="1"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals("orientation", attr.name)
        assertEquals(mapOf("horizontal" to 0, "vertical" to 1), attr.enumValues)
        assertEquals(emptyMap<String, Int>(), attr.flagValues)
    }

    @Test
    fun `top-level attr with flag children — hex values parsed`()
    {
        val xml = """
            <resources>
              <attr name="gravity">
                <flag name="top" value="0x30"/>
                <flag name="center_horizontal" value="0x01"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(mapOf("top" to 0x30, "center_horizontal" to 0x01), attr.flagValues)
        assertEquals(emptyMap<String, Int>(), attr.enumValues)
    }

    @Test
    fun `attr inside declare-styleable — children captured`()
    {
        val xml = """
            <resources>
              <declare-styleable name="MyView">
                <attr name="layoutDirection">
                  <enum name="ltr" value="0"/>
                  <enum name="rtl" value="1"/>
                </attr>
              </declare-styleable>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals("layoutDirection", attr.name)
        assertEquals(mapOf("ltr" to 0, "rtl" to 1), attr.enumValues)
    }

    @Test
    fun `attr without children — both maps empty`()
    {
        val xml = """<resources><attr name="customRef" format="reference"/></resources>"""
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(emptyMap<String, Int>(), attr.enumValues)
        assertEquals(emptyMap<String, Int>(), attr.flagValues)
    }

    @Test
    fun `unparseable value — silently skipped`()
    {
        val xml = """
            <resources>
              <attr name="weird">
                <enum name="ok" value="5"/>
                <enum name="broken" value="not_a_number"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(mapOf("ok" to 5), attr.enumValues)
    }

    @Test
    fun `cross-NS attr name — still skipped (T8 fix preserved)`()
    {
        val xml = """
            <resources>
              <attr name="android:visible"><enum name="x" value="1"/></attr>
              <attr name="local"><enum name="y" value="2"/></attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val names = entries.filterIsInstance<ParsedNsEntry.AttrDef>().map { it.name }
        assertEquals(listOf("local"), names)
    }

    @Test
    fun `enum value 0 — captured (ConstraintLayout parent literal)`()
    {
        val xml = """
            <resources>
              <attr name="layout_constraintEnd_toEndOf">
                <enum name="parent" value="0"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(mapOf("parent" to 0), attr.enumValues)
    }

    @Test
    fun `flag value 0x80000000 — Long-decode handles unsigned 32-bit hex`()
    {
        val xml = """
            <resources>
              <attr name="inputMethodFlags">
                <flag name="forceAscii" value="0x80000000"/>
                <flag name="all" value="0xffffffff"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        // Long.decode("0x80000000").toInt() == Int.MIN_VALUE; Long.decode("0xffffffff").toInt() == -1
        assertEquals(mapOf("forceAscii" to Int.MIN_VALUE, "all" to -1), attr.flagValues)
    }

    @Test
    fun `negative decimal value — Long-decode handles negative literals`()
    {
        val xml = """
            <resources>
              <attr name="layout_constraintHeight">
                <enum name="match_constraint" value="-1"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(mapOf("match_constraint" to -1), attr.enumValues)
    }

    private fun parseXml(content: String): List<ParsedNsEntry>
    {
        val tmp = Files.createTempFile("attr-enum-flag", ".xml")
        Files.writeString(tmp, content)
        return NamespaceAwareValueParser.parse(tmp, ResourceNamespace.RES_AUTO, "test")
    }
}
