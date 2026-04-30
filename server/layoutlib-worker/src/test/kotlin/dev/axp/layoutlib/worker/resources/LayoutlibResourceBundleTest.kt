package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class LayoutlibResourceBundleTest
{

    @Test
    fun `getStyleExact 이 namespace + name 으로 정확 hit`()
    {
        val bundle = build(
            ResourceNamespace.ANDROID to listOf(style("Theme", null)),
            ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", "Theme.Material3")),
        )
        assertNotNull(bundle.getStyleExact(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme.AxpFixture")))
        assertNull(bundle.getStyleExact(ref(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme.AxpFixture")))
    }

    @Test
    fun `getStyleByName 이 deterministic ANDROID 우선 RES_AUTO 후 첫 매치`()
    {
        // 같은 name 이 양쪽 ns 에 → ANDROID 가 우선
        val bundle = build(
            ResourceNamespace.ANDROID to listOf(style("CommonName", null)),
            ResourceNamespace.RES_AUTO to listOf(style("CommonName", null)),
        )
        val hit = bundle.getStyleByName("CommonName")
        assertNotNull(hit)
        assertEquals(ResourceNamespace.ANDROID, hit!!.namespace)
    }

    @Test
    fun `getResource 가 ns-exact byType 안 lookup`()
    {
        val bundle = build(
            ResourceNamespace.RES_AUTO to listOf(simple(ResourceType.COLOR, "primary", "#fff")),
        )
        assertNotNull(bundle.getResource(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "primary")))
        assertNull(bundle.getResource(ref(ResourceNamespace.ANDROID, ResourceType.COLOR, "primary")))
    }

    @Test
    fun `SimpleValue dedupe later-wins per namespace`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try
        {
            val bundle = build(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.SimpleValue(ResourceType.COLOR, "p", "#a", ResourceNamespace.RES_AUTO, "first"),
                    ParsedNsEntry.SimpleValue(ResourceType.COLOR, "p", "#b", ResourceNamespace.RES_AUTO, "second"),
                ),
            )
            val rv = bundle.getResource(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "p"))
            assertEquals("#b", rv?.value, "later-wins")
            val log = errOut.toString()
            assertTrue(log.contains("dup color 'p'"), "duplicate 진단")
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `AttrDef dedupe first-wins`()
    {
        val bundle = build(
            ResourceNamespace.RES_AUTO to listOf(
                ParsedNsEntry.AttrDef("colorPrimary", ResourceNamespace.RES_AUTO, emptyMap(), emptyMap(), "first"),
                ParsedNsEntry.AttrDef("colorPrimary", ResourceNamespace.RES_AUTO, emptyMap(), emptyMap(), "second"),
            ),
        )
        // attr 은 1 개만 등록 (first-wins)
        assertEquals(1, bundle.attrCountForNamespace(ResourceNamespace.RES_AUTO))
    }

    @Test
    fun `cross-ns parent 가 inference 통과 (Theme 가 ANDROID 에)`()
    {
        val bundle = build(
            ResourceNamespace.ANDROID to listOf(style("Theme", null)),
            ResourceNamespace.RES_AUTO to listOf(
                style("Theme.AxpFixture", "Theme.Material3.DayNight.NoActionBar"),
                style("Theme.Material3.DayNight.NoActionBar", null),
            ),
        )
        val s = bundle.getStyleExact(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme.AxpFixture"))
        assertNotNull(s)
        // parent 는 string-only (StyleResourceValueImpl 의 parentStyleName)
        assertEquals("Theme.Material3.DayNight.NoActionBar", s!!.parentStyleName)
    }

    @Test
    fun `byNs 가 LinkedHashMap insertion order ANDROID 그다음 RES_AUTO`()
    {
        val bundle = build(
            ResourceNamespace.ANDROID to listOf(style("A", null)),
            ResourceNamespace.RES_AUTO to listOf(style("B", null)),
        )
        val keys = bundle.namespacesInOrder()
        assertEquals(listOf(ResourceNamespace.ANDROID, ResourceNamespace.RES_AUTO), keys)
    }

    private fun build(vararg pairs: Pair<ResourceNamespace, List<ParsedNsEntry>>): LayoutlibResourceBundle
    {
        val grouped = pairs.toMap()
        return LayoutlibResourceBundle.build(grouped)
    }

    private fun style(name: String, parent: String?) =
        ParsedNsEntry.StyleDef(name, parent, emptyList(), ResourceNamespace.RES_AUTO, null)

    private fun simple(type: ResourceType, name: String, value: String) =
        ParsedNsEntry.SimpleValue(type, name, value, ResourceNamespace.RES_AUTO, null)

    private fun ref(ns: ResourceNamespace, t: ResourceType, n: String) =
        com.android.ide.common.rendering.api.ResourceReference(ns, t, n)
}
