package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class NamespaceAwareValueParserTest
{

    @Test
    fun `simple value 가 정확한 namespace + sourcePackage 로 태깅`()
    {
        val xml = tmp("""<resources><dimen name="x">10dp</dimen></resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        assertEquals(1, entries.size)
        val e = entries[0] as ParsedNsEntry.SimpleValue
        assertEquals("x", e.name)
        assertEquals(ResourceType.DIMEN, e.type)
        assertEquals("10dp", e.value)
        assertEquals(ResourceNamespace.RES_AUTO, e.namespace)
        assertEquals("com.foo", e.sourcePackage)
    }

    @Test
    fun `style 의 parent 와 items 추출`()
    {
        val xml = tmp("""<resources>
            <style name="Theme.AxpFixture" parent="Theme.Material3.DayNight.NoActionBar">
                <item name="colorPrimary">#6750A4</item>
            </style>
        </resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.fixture")
        assertEquals(1, entries.size)
        val s = entries[0] as ParsedNsEntry.StyleDef
        assertEquals("Theme.AxpFixture", s.name)
        assertEquals("Theme.Material3.DayNight.NoActionBar", s.parent)
        assertEquals(1, s.items.size)
        assertEquals("colorPrimary", s.items[0].name)
    }

    @Test
    fun `top-level attr 가 declare-styleable nested attr 보다 first-wins (W3D1 F1 보존)`()
    {
        val xml = tmp("""<resources>
            <attr name="colorPrimary" />
            <declare-styleable name="ButtonStyle">
                <attr name="colorPrimary" />
            </declare-styleable>
        </resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        val attrCount = entries.count { it is ParsedNsEntry.AttrDef && it.name == "colorPrimary" }
        assertEquals(1, attrCount, "first-wins → 1 entry only")
    }

    @Test
    fun `framework namespace 도 동일한 parser 가 처리`()
    {
        val xml = tmp("""<resources><color name="white">#ffffff</color></resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.ANDROID, null)
        val e = entries[0] as ParsedNsEntry.SimpleValue
        assertEquals(ResourceNamespace.ANDROID, e.namespace)
        assertEquals(null, e.sourcePackage)
    }

    @Test
    fun `XML parsing 에러는 IllegalStateException 으로 wrap`()
    {
        val xml = tmp("""<resources><dimen name="x" """)  // unclosed
        try
        {
            NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, null)
            org.junit.jupiter.api.Assertions.fail<Unit>("expected throw")
        }
        catch (e: IllegalStateException)
        {
            assertTrue(e.message?.contains("파싱 실패") == true)
        }
    }

    private fun tmp(content: String): Path
    {
        val f = Files.createTempFile("vals", ".xml")
        f.toFile().writeText(content)
        f.toFile().deleteOnExit()
        return f
    }
}
