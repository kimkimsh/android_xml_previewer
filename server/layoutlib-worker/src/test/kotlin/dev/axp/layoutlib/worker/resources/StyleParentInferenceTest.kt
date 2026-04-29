package dev.axp.layoutlib.worker.resources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StyleParentInferenceTest {

    @Test
    fun `explicit parent 이름 그대로 반환`() {
        assertEquals(
            "Theme.Material.Light",
            StyleParentInference.infer("Theme.Material.Light.NoActionBar", explicitParent = "Theme.Material.Light")
        )
    }

    @Test
    fun `explicit parent ref 형식 정규화 - at android style prefix 제거`() {
        assertEquals(
            "Theme.Material",
            StyleParentInference.infer("Theme.Material.Light", explicitParent = "@android:style/Theme.Material")
        )
    }

    @Test
    fun `explicit parent ref 형식 정규화 - at style prefix 제거`() {
        assertEquals(
            "Widget.Material",
            StyleParentInference.infer("Widget.Material.Button", explicitParent = "@style/Widget.Material")
        )
    }

    @Test
    fun `explicit null 또는 empty 면 dotted-prefix 상속`() {
        assertEquals("Theme.Material.Light", StyleParentInference.infer("Theme.Material.Light.NoActionBar", null))
        assertEquals("Theme.Material", StyleParentInference.infer("Theme.Material.Light", ""))
    }

    @Test
    fun `점 없는 이름은 루트 - null 반환`() {
        assertNull(StyleParentInference.infer("Theme", explicitParent = null))
        assertNull(StyleParentInference.infer("Theme", explicitParent = ""))
    }
}
