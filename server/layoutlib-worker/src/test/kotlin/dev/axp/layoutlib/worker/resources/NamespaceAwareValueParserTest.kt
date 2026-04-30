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

    @Test
    fun `declare-styleable 의 attr 자식 (enum) 정상 skip`()
    {
        // W3D1 패턴 inherit: declare-styleable 안의 <attr> 가 <enum>/<flag> 자식을 가질 때
        // depth-aware skip 으로 정상 종료. 두 attr 모두 수집되어야 함.
        val xml = tmp("""<resources>
            <declare-styleable name="View">
                <attr name="visibility">
                    <enum name="visible" value="0"/>
                    <enum name="gone" value="2"/>
                </attr>
                <attr name="other"/>
            </declare-styleable>
        </resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        val attrs = entries.filterIsInstance<ParsedNsEntry.AttrDef>().map { it.name }.toSet()
        assertEquals(setOf("visibility", "other"), attrs)
    }

    @Test
    fun `top-level item 가 type 인자로 SimpleValue 생성`()
    {
        // W3D1 패턴 inherit: Material3 AAR 흔한 <item type="dimen" name="...">value</item> 패턴.
        // T3/T4 가 real Material3 AAR 만나면 회귀 방지용 — 본 case 가 silently drop 되면 안 됨.
        val xml = tmp("""<resources>
            <item type="dimen" name="design_appbar_elevation">4dp</item>
        </resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.material")
        assertEquals(1, entries.size)
        val e = entries[0] as ParsedNsEntry.SimpleValue
        assertEquals(ResourceType.DIMEN, e.type)
        assertEquals("design_appbar_elevation", e.name)
        assertEquals("4dp", e.value)
    }

    private fun tmp(content: String): Path
    {
        val f = Files.createTempFile("vals", ".xml")
        f.toFile().writeText(content)
        f.toFile().deleteOnExit()
        return f
    }
}
