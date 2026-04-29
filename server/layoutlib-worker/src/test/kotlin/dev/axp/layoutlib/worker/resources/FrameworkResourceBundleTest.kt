package dev.axp.layoutlib.worker.resources

import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FrameworkResourceBundleTest {

    @Test
    fun `build - SimpleValue entries 가 type 별 map 으로 집계`() {
        val entries = listOf(
            ParsedEntry.SimpleValue(ResourceType.DIMEN, "config_scrollbarSize", "4dp"),
            ParsedEntry.SimpleValue(ResourceType.COLOR, "material_blue_grey_800", "#ff37474f"),
            ParsedEntry.SimpleValue(ResourceType.DIMEN, "action_bar_size", "56dp"),
        )
        val bundle = FrameworkResourceBundle.build(entries)

        val d = bundle.getResource(ResourceType.DIMEN, "config_scrollbarSize")
        assertNotNull(d)
        assertEquals("4dp", d!!.value)

        val c = bundle.getResource(ResourceType.COLOR, "material_blue_grey_800")
        assertEquals("#ff37474f", c!!.value)

        assertNull(bundle.getResource(ResourceType.DIMEN, "does_not_exist"))
    }

    @Test
    fun `build - StyleDef 의 parent 가 존재하는 이름이면 유지 존재 안 하면 null`() {
        val entries = listOf(
            ParsedEntry.StyleDef("Theme", parent = null, items = emptyList()),
            ParsedEntry.StyleDef("Theme.Material.Light.NoActionBar", parent = "Theme.Material.Light",
                items = listOf(StyleItem("windowActionBar", "false"))),
            // 점 포함하지만 explicit parent 없음 → inference 로 Theme.Material 추정되나 존재 X
            ParsedEntry.StyleDef("Theme.Material.Light", parent = null, items = emptyList()),
        )
        val bundle = FrameworkResourceBundle.build(entries)

        val root = bundle.getStyle("Theme")
        assertNotNull(root); assertNull(root!!.parentStyleName)

        val noActionBar = bundle.getStyle("Theme.Material.Light.NoActionBar")
        assertEquals("Theme.Material.Light", noActionBar!!.parentStyleName)

        val light = bundle.getStyle("Theme.Material.Light")
        // inference Theme.Material bundle null post-process.
        assertNull(light!!.parentStyleName)
    }

    @Test
    fun `build - AttrDef 는 attrs map 에 집계`() {
        val entries = listOf(
            ParsedEntry.AttrDef("isLightTheme", "boolean"),
            ParsedEntry.AttrDef("colorForeground", "color"),
        )
        val bundle = FrameworkResourceBundle.build(entries)
        val a = bundle.getAttr("isLightTheme")
        assertNotNull(a)
        assertEquals("isLightTheme", a!!.name)
    }

    @Test
    fun `build - SimpleValue 중복 이름은 later-wins`() {
        val entries = listOf(
            ParsedEntry.SimpleValue(ResourceType.DIMEN, "x", "1dp"),
            ParsedEntry.SimpleValue(ResourceType.DIMEN, "x", "2dp"),
        )
        val bundle = FrameworkResourceBundle.build(entries)
        assertEquals("2dp", bundle.getResource(ResourceType.DIMEN, "x")!!.value)
    }

    @Test
    fun `build - AttrDef 중복 이름은 first-wins (F1)`() {
        val entries = listOf(
            ParsedEntry.AttrDef("colorPrimary", "color"),
            ParsedEntry.AttrDef("colorPrimary", null),
            ParsedEntry.AttrDef("colorPrimary", "reference"),
        )
        val bundle = FrameworkResourceBundle.build(entries)
        assertEquals(1, bundle.attrCount(), "동일 name 은 1 개만 집계")
        assertNotNull(bundle.getAttr("colorPrimary"))
    }
}
