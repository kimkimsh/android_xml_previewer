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

    @Test
    fun `multi-element top-level (real Material3 패턴) 모두 파싱`()
    {
        // W3D4 T2 second-fix: depth-counter bug regression detect.
        // handleSimpleValue/handleStyle/handleDeclareStyleable 가 자체 END_ELEMENT 까지 consume 후
        // 종료 → outer loop 의 depth-- 가 fire 안 됨 → 두 번째 sibling 부터 silently dropped.
        // T3/T4 의 real Material3 values.xml (수백 sibling) 회귀 방지용.
        val xml = tmp("""<resources>
            <dimen name="d1">1dp</dimen>
            <dimen name="d2">2dp</dimen>
            <color name="c1">#fff</color>
            <style name="S1" parent="P1"><item name="i1">v1</item></style>
            <style name="S2" parent="P2"><item name="i2">v2</item></style>
            <attr name="a1"/>
            <declare-styleable name="X"><attr name="a2"/></declare-styleable>
            <item type="dimen" name="d3">3dp</item>
        </resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        val simples = entries.filterIsInstance<ParsedNsEntry.SimpleValue>().associateBy { it.name }
        val styles = entries.filterIsInstance<ParsedNsEntry.StyleDef>().associateBy { it.name }
        val attrs = entries.filterIsInstance<ParsedNsEntry.AttrDef>().map { it.name }.toSet()

        assertEquals(setOf("d1", "d2", "c1", "d3"), simples.keys)
        assertEquals(setOf("S1", "S2"), styles.keys)
        assertEquals(setOf("a1", "a2"), attrs)
        assertEquals(8, entries.size, "총 4 simple + 2 style + 2 attr = 8")
    }

    @Test
    fun `string with nested HTML markup 가 text 만 추출 (mixed content)`()
    {
        // T8 fix (W3D1 readText 패턴 회복): StAX elementText 가 mixed content 시 throw 하던 case.
        // 실 Android AAR values.xml 에 흔함 (e.g., 'Click <b>here</b>'). 본 fix 후 markup 은
        // strip 되고 그 안의 text 도 누적된다.
        val xml = tmp(
            """<resources>
                <string name="welcome">Hello <b>world</b> from <i>Material</i></string>
            </resources>""",
        )
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        assertEquals(1, entries.size)
        val e = entries[0] as ParsedNsEntry.SimpleValue
        assertEquals("welcome", e.name)
        assertEquals(ResourceType.STRING, e.type)
        // mixed content text 만 (markup 자체는 strip).
        assertEquals("Hello world from Material", e.value)
    }

    @Test
    fun `xliff g placeholder 가 inline text 로 통합`()
    {
        // 실 Android value 에 흔한 패턴: <string>Hello <xliff:g id="user">%1$s</xliff:g></string>.
        // xliff:g 는 mixed content 의 또 다른 형태 — START_ELEMENT 안 text 도 outer 누적에 합류.
        // Kotlin string interpolation 회피: '$' 를 escape 하기보다 concat 으로 안전 작성.
        val placeholder = "%1" + "\$" + "s"
        val xml = tmp(
            """<resources>
                <string name="greeting">Hello <xliff:g id="user" xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">$placeholder</xliff:g></string>
            </resources>""",
        )
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        assertEquals(1, entries.size)
        val e = entries[0] as ParsedNsEntry.SimpleValue
        assertEquals("greeting", e.name)
        // outer text "Hello " + xliff:g 안 text "%1${'$'}s" 누적.
        assertEquals("Hello $placeholder", e.value)
    }

    @Test
    fun `declare-styleable 안 cross-NS attr ref (android prefix) 는 skip — local def 만 emit`()
    {
        // T8 fix: real Material/AppCompat AAR 의 declare-styleable 에 흔한 패턴.
        // 'android:visible' 같은 framework attr ref 는 local def 아님 → AttrDef emit 안 함.
        // ResourceReference name 이 ':' 를 disallow 하므로 emit 시 AssertionError.
        val xml = tmp(
            """<resources>
                <declare-styleable name="MyView">
                    <attr name="android:visible" />
                    <attr name="myCustomAttr" format="dimension" />
                    <attr name="android:textSize" />
                </declare-styleable>
            </resources>""",
        )
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        val attrs = entries.filterIsInstance<ParsedNsEntry.AttrDef>().map { it.name }.toSet()
        // local def 만 (cross-NS ref 둘 다 skip)
        assertEquals(setOf("myCustomAttr"), attrs)
    }

    @Test
    fun `top-level cross-NS attr ref 도 skip`()
    {
        // 드물지만 가능한 경우 — top-level 에 namespace-prefixed attr.
        // 'app:' 등 ANDROID 외 prefix 도 동일 정책 (모든 ':' 포함 name 은 ref 로 간주).
        val xml = tmp(
            """<resources>
                <attr name="android:gravity" />
                <attr name="myAttr" />
                <attr name="app:fooBar" />
            </resources>""",
        )
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        val attrs = entries.filterIsInstance<ParsedNsEntry.AttrDef>().map { it.name }.toSet()
        assertEquals(setOf("myAttr"), attrs)
    }

    private fun tmp(content: String): Path
    {
        val f = Files.createTempFile("vals", ".xml")
        f.toFile().writeText(content)
        f.toFile().deleteOnExit()
        return f
    }
}
