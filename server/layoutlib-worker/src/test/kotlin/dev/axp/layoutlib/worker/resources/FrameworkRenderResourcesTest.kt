package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameworkRenderResourcesTest {

    private fun bundle(): FrameworkResourceBundle = FrameworkResourceBundle.build(listOf(
        ParsedEntry.SimpleValue(ResourceType.DIMEN, "config_scrollbarSize", "4dp"),
        ParsedEntry.StyleDef("Theme", null, emptyList()),
        ParsedEntry.StyleDef(
            "Theme.Material.Light.NoActionBar",
            "Theme",
            listOf(StyleItem("windowActionBar", "false")),
        ),
    ))

    @Test
    fun `getDefaultTheme - 생성자에 전달된 이름으로 style 반환`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Theme.Material.Light.NoActionBar")
        val theme = rr.defaultTheme
        assertNotNull(theme)
        assertEquals("Theme.Material.Light.NoActionBar", theme.name)
    }

    @Test
    fun `getDefaultTheme - bundle 에 해당 style 이 없으면 parent null 빈 style 반환`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Does.Not.Exist")
        val theme = rr.defaultTheme
        assertNotNull(theme)
        assertEquals("Does.Not.Exist", theme.name)
    }

    @Test
    fun `getStyle - android namespace 에서 lookup`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Theme")
        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme")
        val s = rr.getStyle(ref)
        assertNotNull(s)
        assertEquals("Theme", s!!.name)
    }

    @Test
    fun `getStyle - project namespace 는 null`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Theme")
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme")
        assertNull(rr.getStyle(ref))
    }

    @Test
    fun `getResolvedResource 와 getUnresolvedResource - framework 에서 동일 반환`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Theme")
        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.DIMEN, "config_scrollbarSize")
        val u = rr.getUnresolvedResource(ref)
        val r = rr.getResolvedResource(ref)
        assertNotNull(u); assertNotNull(r)
        assertEquals("4dp", u!!.value)
        assertEquals("4dp", r!!.value)
    }

    @Test
    fun `findResValue override 가 존재하지 않아야 - W2D7 L3 landmine 재발 방지`() {
        val methods = FrameworkRenderResources::class.java.declaredMethods
        val findResValue = methods.firstOrNull { it.name == "findResValue" }
        assertTrue(findResValue == null, "findResValue 는 override 하지 말 것 — W2D7 landmine L3")
    }
}
