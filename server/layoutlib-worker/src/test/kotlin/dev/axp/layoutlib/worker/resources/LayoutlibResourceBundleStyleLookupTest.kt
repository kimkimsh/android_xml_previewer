package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins LayoutlibResourceBundle.getResource STYLE-ref contract: the returned instance
 * must come from the type-specific styles map and preserve the StyleResourceValue
 * runtime type that BridgeContext / BridgeTypedArray downstream cast to after
 * defStyleAttr / defStyleRes resolution. Routing STYLE refs through the generic
 * byType bucket would silently return null and break that instanceof contract.
 */
internal class LayoutlibResourceBundleStyleLookupTest
{
    @Test
    fun `getResource - STYLE ref returns the style instance from RES_AUTO`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.StyleDef(
                        name = "Widget.Material3.Button",
                        parent = "Widget.MaterialComponents.Button",
                        items = listOf(
                            ParsedNsEntry.StyleDef.StyleItem(
                                name = "android:textAppearance",
                                value = "?attr/textAppearanceButton",
                            ),
                        ),
                        namespace = ResourceNamespace.RES_AUTO,
                        sourcePackage = "material",
                    ),
                ),
            ),
        )
        val ref = ResourceReference(
            ResourceNamespace.RES_AUTO,
            ResourceType.STYLE,
            "Widget.Material3.Button",
        )
        val resource = bundle.getResource(ref)
        assertNotNull(resource)
        assertTrue(
            resource is StyleResourceValue,
            "getResource(STYLE) must return StyleResourceValue (instanceof contract), got ${resource!!::class.simpleName}",
        )
        val style = resource as StyleResourceValue
        assertEquals("Widget.Material3.Button", style.name)
    }

    @Test
    fun `getResource - STYLE ref returns null when name absent`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.StyleDef(
                        name = "Widget.Material3.Button",
                        parent = null,
                        items = emptyList(),
                        namespace = ResourceNamespace.RES_AUTO,
                        sourcePackage = "material",
                    ),
                ),
            ),
        )
        val missingRef = ResourceReference(
            ResourceNamespace.RES_AUTO,
            ResourceType.STYLE,
            "Widget.NotDefined",
        )
        assertNull(bundle.getResource(missingRef))
    }

    @Test
    fun `getResource - STYLE ref returns null when namespace absent`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.StyleDef(
                        name = "Widget.Material3.Button",
                        parent = null,
                        items = emptyList(),
                        namespace = ResourceNamespace.RES_AUTO,
                        sourcePackage = "material",
                    ),
                ),
            ),
        )
        val androidRef = ResourceReference(
            ResourceNamespace.ANDROID,
            ResourceType.STYLE,
            "Widget.Material3.Button",
        )
        assertNull(bundle.getResource(androidRef))
    }

    @Test
    fun `getResource - non-STYLE non-ATTR types still use byType bucket`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.SimpleValue(
                        type = ResourceType.DIMEN,
                        name = "spacing",
                        value = "8dp",
                        namespace = ResourceNamespace.RES_AUTO,
                        sourcePackage = "test",
                    ),
                    ParsedNsEntry.SimpleValue(
                        type = ResourceType.COLOR,
                        name = "primary",
                        value = "#ff0000",
                        namespace = ResourceNamespace.RES_AUTO,
                        sourcePackage = "test",
                    ),
                ),
            ),
        )
        val dimenRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DIMEN, "spacing")
        val colorRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "primary")
        assertEquals("8dp", bundle.getResource(dimenRef)!!.value)
        assertEquals("#ff0000", bundle.getResource(colorRef)!!.value)
    }
}
