package dev.axp.layoutlib.worker.resources

import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameworkValueParserTest {

    private fun parse(xml: String): List<ParsedEntry> =
        FrameworkValueParser.parse("inline-test.xml", xml.byteInputStream(Charsets.UTF_8))

    @Test
    fun `dimen - name 과 value 추출`() {
        val xml = """
            <resources>
              <dimen name="config_scrollbarSize">4dp</dimen>
            </resources>
        """.trimIndent()
        val entries = parse(xml)
        val d = entries.single() as ParsedEntry.SimpleValue
        assertEquals(ResourceType.DIMEN, d.type)
        assertEquals("config_scrollbarSize", d.name)
        assertEquals("4dp", d.value)
    }

    @Test
    fun `color 엔트리`() {
        val xml = """
            <resources>
              <color name="material_blue_grey_800">#ff37474f</color>
            </resources>
        """.trimIndent()
        val c = parse(xml).single() as ParsedEntry.SimpleValue
        assertEquals(ResourceType.COLOR, c.type)
        assertEquals("#ff37474f", c.value)
    }

    @Test
    fun `integer bool string 엔트리`() {
        val xml = """
            <resources>
              <integer name="config_maxRetries">3</integer>
              <bool name="config_allowTheming">true</bool>
              <string name="emptyString"></string>
            </resources>
        """.trimIndent()
        val list = parse(xml).filterIsInstance<ParsedEntry.SimpleValue>()
        assertEquals(3, list.size)
        assertEquals(ResourceType.INTEGER, list[0].type)
        assertEquals(ResourceType.BOOL, list[1].type)
        assertEquals(ResourceType.STRING, list[2].type)
        assertEquals("", list[2].value)
    }

    @Test
    fun `item with type attribute - explicit type 사용`() {
        val xml = """
            <resources>
              <item type="dimen" name="fraction_half" format="float">0.5</item>
            </resources>
        """.trimIndent()
        val e = parse(xml).single() as ParsedEntry.SimpleValue
        assertEquals(ResourceType.DIMEN, e.type)
        assertEquals("fraction_half", e.name)
        assertEquals("0.5", e.value)
    }

    @Test
    fun `style - parent explicit + items 순서 유지`() {
        val xml = """
            <resources>
              <style name="Theme.Material.Light.NoActionBar" parent="Theme.Material.Light">
                <item name="windowActionBar">false</item>
                <item name="windowNoTitle">true</item>
              </style>
            </resources>
        """.trimIndent()
        val s = parse(xml).single() as ParsedEntry.StyleDef
        assertEquals("Theme.Material.Light.NoActionBar", s.name)
        assertEquals("Theme.Material.Light", s.parent)
        assertEquals(2, s.items.size)
        assertEquals("windowActionBar", s.items[0].name)
        assertEquals("false", s.items[0].value)
        assertEquals("windowNoTitle", s.items[1].name)
    }

    @Test
    fun `style - parent 누락 허용 null 보존`() {
        val xml = """
            <resources>
              <style name="Theme">
                <item name="colorPrimary">#fff</item>
              </style>
            </resources>
        """.trimIndent()
        val s = parse(xml).single() as ParsedEntry.StyleDef
        assertNull(s.parent)
    }

    @Test
    fun `attr - top level 수집`() {
        val xml = """
            <resources>
              <attr name="isLightTheme" format="boolean" />
              <attr name="colorForeground" format="color" />
            </resources>
        """.trimIndent()
        val attrs = parse(xml).filterIsInstance<ParsedEntry.AttrDef>()
        assertEquals(2, attrs.size)
        assertEquals("isLightTheme", attrs[0].name)
        assertEquals("boolean", attrs[0].format)
    }

    @Test
    fun `declare-styleable 내부 attr 도 AttrDef 로 수집 - first-wins dedupe`() {
        val xml = """
            <resources>
              <attr name="colorPrimary" format="color" />
              <declare-styleable name="View">
                <attr name="colorPrimary" />
                <attr name="id" format="reference" />
                <attr name="background" />
              </declare-styleable>
            </resources>
        """.trimIndent()
        // 파서는 중복 emit. dedupe 는 Loader/Bundle 단계. 이 테스트는 파서가 모두 emit 하는지 확인.
        val attrs = parse(xml).filterIsInstance<ParsedEntry.AttrDef>()
        assertEquals(4, attrs.size, "emit all: top-level colorPrimary + nested 3")
        assertEquals("colorPrimary", attrs[0].name)
        assertEquals("color", attrs[0].format)
        assertEquals("colorPrimary", attrs[1].name, "nested 중복 name")
        assertNull(attrs[1].format)
        assertEquals("id", attrs[2].name)
        assertEquals("reference", attrs[2].format)
        assertEquals("background", attrs[3].name)
        assertNull(attrs[3].format)
    }

    @Test
    fun `attrs xml 실 케이스 - top-level attr 0 개 + declare-styleable nested 만 있을 때 모두 수집`() {
        val xml = """
            <resources>
              <declare-styleable name="Theme">
                <attr name="colorPrimary" format="color" />
                <attr name="isLightTheme" format="boolean" />
              </declare-styleable>
              <declare-styleable name="View">
                <attr name="id" format="reference" />
                <attr name="colorPrimary" />
              </declare-styleable>
            </resources>
        """.trimIndent()
        val attrs = parse(xml).filterIsInstance<ParsedEntry.AttrDef>()
        assertEquals(4, attrs.size, "파서는 duplicate 포함 emit. Bundle 이 first-wins dedupe.")
        val names = attrs.map { it.name }
        assertEquals(listOf("colorPrimary", "isLightTheme", "id", "colorPrimary"), names)
    }

    @Test
    fun `unknown tag 는 skip 하고 warning`() {
        val xml = """
            <resources>
              <public type="dimen" name="config_scrollbarSize" id="0x10500dd" />
              <dimen name="config_scrollbarSize">4dp</dimen>
            </resources>
        """.trimIndent()
        val entries = parse(xml)
        assertEquals(1, entries.size)
        assertTrue(entries.single() is ParsedEntry.SimpleValue)
    }

    @Test
    fun `malformed XML 은 IllegalStateException 에 파일명 포함`() {
        val xml = "<resources><dimen name=broken></resources>"
        val ex = assertThrows(IllegalStateException::class.java) { parse(xml) }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("inline-test.xml"), "파일명이 에러 메시지에 포함: ${ex.message}")
    }
}
