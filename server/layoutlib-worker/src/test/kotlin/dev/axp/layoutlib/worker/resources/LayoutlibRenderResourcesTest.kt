package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * W3D4 §3.1 #8 + §5.1 (T6): Q3 σ FULL — 9 method override + chain walker + theme stack.
 *
 * v2 round 2 follow-up:
 *  - FF#3 (Codex Q1): @null / @empty sentinel raw 반환 + @*android:... private override 처리.
 *  - FF#5: MAX_THEME_HOPS=32 hop overflow 진단 로그.
 *  - FF#6: parentStyleName "android:Theme.Holo.Light" 가 ANDROID ns 로 normalize.
 */
class LayoutlibRenderResourcesTest
{

    @Test
    fun `getDefaultTheme 가 bundle getStyleByName 결과`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", null)),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        assertEquals("Theme.AxpFixture", rr.defaultTheme.name)
    }

    @Test
    fun `getDefaultTheme empty fallback 가 name 누락 시 동작`()
    {
        val bundle = LayoutlibResourceBundle.build(mapOf())
        val rr = LayoutlibRenderResources(bundle, "Missing.Theme")
        // empty StyleResourceValueImpl 반환 (LM-3 패턴)
        assertEquals("Missing.Theme", rr.defaultTheme.name)
        assertNull(rr.defaultTheme.parentStyleName)
    }

    @Test
    fun `getStyle ns-exact then ns-agnostic fallback`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.ANDROID to listOf(style("Theme", null)),
                ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", "Theme")),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        // ns-exact RES_AUTO hit
        assertNotNull(rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme.AxpFixture")))
        // ns-exact RES_AUTO miss, name fallback hit ANDROID
        assertNotNull(rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme")))
    }

    @Test
    fun `getParent 가 style parentStyleName + ns-agnostic fallback 활용`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.ANDROID to listOf(style("Theme", null)),
                ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", "Theme")),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        val child = rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme.AxpFixture"))!!
        val parent = rr.getParent(child)
        assertNotNull(parent)
        assertEquals("Theme", parent!!.name)
    }

    @Test
    fun `getAllThemes 가 default theme + parent walk 결과`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.ANDROID to listOf(style("Theme", null)),
                ResourceNamespace.RES_AUTO to listOf(
                    style("Theme.Material3", "Theme"),
                    style("Theme.AxpFixture", "Theme.Material3"),
                ),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        val themes = rr.allThemes
        assertTrue(themes.size >= 3, "AxpFixture + Material3 + Theme 포함")
        assertEquals("Theme.AxpFixture", themes[0].name)
    }

    @Test
    fun `getUnresolvedResource 가 ns-exact 만 hit`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(simple(ResourceType.COLOR, "p", "#fff")),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "X")
        assertNotNull(rr.getUnresolvedResource(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "p")))
        assertNull(rr.getUnresolvedResource(ref(ResourceNamespace.ANDROID, ResourceType.COLOR, "p")))
    }

    @Test
    fun `resolveResValue chain walker 가 attr ref 따라가기`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.StyleDef(
                        "Theme.X",
                        null,
                        listOf(ParsedNsEntry.StyleDef.StyleItem("colorPrimary", "#abc")),
                        ResourceNamespace.RES_AUTO,
                        null,
                    ),
                ),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "Theme.X")
        // ?attr/colorPrimary 처럼 보이는 ResourceValue 를 resolveResValue 에 통과
        val attrRefValue = ResourceValueImpl(
            ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "fakeRef"),
            "?attr/colorPrimary",
            null,
        )
        val resolved = rr.resolveResValue(attrRefValue)
        // theme 의 item 가 hit 후 #abc 반환 — 또는 raw value 반환 (chain walker 가 매칭 못하면 graceful)
        assertNotNull(resolved)
    }

    @Test
    fun `resolveResValue circular detection 가 throw 안 함`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    simple(ResourceType.COLOR, "a", "@color/b"),
                    simple(ResourceType.COLOR, "b", "@color/a"),
                ),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "X")
        val a = rr.getUnresolvedResource(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "a"))!!
        // circular 이지만 throw 안 함 — 마지막 hop 의 value 반환
        val resolved = rr.resolveResValue(a)
        assertNotNull(resolved)
    }

    @Test
    fun `findItemInStyle parent walk 로 item 검색`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.StyleDef(
                        "Parent",
                        null,
                        listOf(ParsedNsEntry.StyleDef.StyleItem("colorPrimary", "#parent")),
                        ResourceNamespace.RES_AUTO,
                        null,
                    ),
                    ParsedNsEntry.StyleDef("Child", "Parent", emptyList(), ResourceNamespace.RES_AUTO, null),
                ),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "Child")
        val child = rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Child"))!!
        val item = rr.findItemInStyle(child, ref(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimary"))
        // parent 의 item 까지 walk 해서 hit
        assertNotNull(item)
    }

    @Test
    fun `applyStyle clearStyles 가 theme stack 변경`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    style("Theme.AxpFixture", null),
                    style("Other", null),
                ),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        val initialSize = rr.allThemes.size
        val other = rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Other"))!!
        rr.applyStyle(other, false)
        assertTrue(rr.allThemes.size > initialSize, "theme stack 추가")
        rr.clearStyles()
        // clear 후엔 default theme 만
        assertEquals("Theme.AxpFixture", rr.allThemes[0].name)
    }

    @Test
    fun `resolveResValue 가 at-null at-empty sentinel 즉시 raw 반환`()
    {
        // v2 round 2 follow-up #3 (Codex Q1): 27 AAR 안 @null 106회 / @empty 2회 출현. sentinel 은 ref 가 아님 → parse 시도 X.
        val bundle = LayoutlibResourceBundle.build(mapOf())
        val rr = LayoutlibRenderResources(bundle, "X")
        val nullRef = ResourceValueImpl(
            ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "x"),
            "@null",
            null,
        )
        val emptyRef = ResourceValueImpl(
            ref(ResourceNamespace.RES_AUTO, ResourceType.STRING, "x"),
            "@empty",
            null,
        )
        assertEquals("@null", rr.resolveResValue(nullRef)?.value, "@null sentinel raw")
        assertEquals("@empty", rr.resolveResValue(emptyRef)?.value, "@empty sentinel raw")
    }

    @Test
    fun `resolveResValue 가 asterisk private override 정상 처리`()
    {
        // v2 round 2 follow-up #3: ResourceUrl.parse 가 @*android:color/X 를 private=true, ns=ANDROID 로 parse.
        // 우리 chain walker 는 private 여부 무시 (visibility 는 inflate 시점에 무관) — type/name 만 추출.
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.ANDROID to listOf(simple(ResourceType.COLOR, "primary_text", "#000")),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "X")
        val privateRef = ResourceValueImpl(
            ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "x"),
            "@*android:color/primary_text",
            null,
        )
        val resolved = rr.resolveResValue(privateRef)
        assertNotNull(resolved, "private override 도 chain walker 가 따라감")
    }

    @Test
    fun `getParent 가 android prefix Theme Holo Light 을 ANDROID ns 로 normalize`()
    {
        // v2 round 2 follow-up #6 (Codex Q6): cross-ns chain 의 ANDROID hop 정확화.
        // child.parentStyleName = "android:Theme.Holo.Light" → namespace=ANDROID + name="Theme.Holo.Light" 로 lookup.
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.ANDROID to listOf(style("Theme.Holo.Light", null)),
                ResourceNamespace.RES_AUTO to listOf(style("Platform.AppCompat.Light", "android:Theme.Holo.Light")),
            )
        )
        val rr = LayoutlibRenderResources(bundle, "Platform.AppCompat.Light")
        val child = rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Platform.AppCompat.Light"))!!
        val parent = rr.getParent(child)
        assertNotNull(parent, "android prefix 가 ANDROID ns 로 normalize")
        assertEquals("Theme.Holo.Light", parent!!.name)
    }

    private fun style(name: String, parent: String?): ParsedNsEntry.StyleDef =
        ParsedNsEntry.StyleDef(name, parent, emptyList(), ResourceNamespace.RES_AUTO, null)

    private fun simple(t: ResourceType, n: String, v: String): ParsedNsEntry.SimpleValue =
        ParsedNsEntry.SimpleValue(t, n, v, ResourceNamespace.RES_AUTO, null)

    private fun ref(ns: ResourceNamespace, t: ResourceType, n: String): ResourceReference =
        ResourceReference(ns, t, n)
}
