package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Sibling to LayoutlibResourceBundleInterpolatorTest covering the layout XML feed.
 * Validates that getLayoutXml returns the raw body, that byType[LAYOUT] receives a
 * per-name path-style placeholder ResourceValue alongside the body, that duplicates
 * are first-wins with a diagnostic line, and that the count helper reports the
 * expected per-namespace tallies.
 *
 * The contract tests on placeholder shape lock in two BridgeContext invariants:
 * the placeholder must NOT start with `@` (so any future TypedValue.type-sensitive
 * consumer along the LAYOUT path resolves to TYPE_STRING (3) instead of
 * TYPE_REFERENCE (1)) and it must be unique per layout name (so the
 * BridgeInflater.inflate(int, ViewGroup) 2-arg bypass — which calls
 * ParserFactory.create(value, true) directly — receives a per-layout-keyed string
 * that can be recognized by the defensive log in createXmlParserForFile).
 */
class LayoutlibResourceBundleLayoutTest
{

    private fun bundleWith(entries: List<ParsedNsEntry>): LayoutlibResourceBundle =
        LayoutlibResourceBundle.build(mapOf(ResourceNamespace.RES_AUTO to entries))

    @Test
    fun `getLayoutXml returns raw body for registered name`()
    {
        val rawXml = "<com.google.android.material.internal.CheckableImageButton android:id=\"@+id/text_input_start_icon\"/>"
        val bundle = bundleWith(listOf(
            ParsedNsEntry.LayoutXml("design_text_input_start_icon", rawXml, ResourceNamespace.RES_AUTO, "material"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "design_text_input_start_icon")
        assertEquals(rawXml, bundle.getLayoutXml(ref))
    }

    @Test
    fun `getLayoutXml returns null for unknown name`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.LayoutXml("foo", "<View/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "missing")
        assertNull(bundle.getLayoutXml(ref))
    }

    @Test
    fun `getLayoutXml returns null for wrong namespace`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.LayoutXml("foo", "<View/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.LAYOUT, "foo")
        assertNull(bundle.getLayoutXml(ref))
    }

    @Test
    fun `byType LAYOUT receives per-name path-style placeholder ResourceValue alongside raw XML`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.LayoutXml("foo", "<View/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "foo")
        val value = bundle.getResource(ref)
        assertNotNull(value)
        assertEquals(AppLibraryResourceConstants.layoutPlaceholderValue("foo"), value!!.value)
    }

    @Test
    fun `layout placeholder does not start with the at sign so consumers see TypedValue type TYPE_STRING`()
    {
        // Contract: BridgeContext.resolveThemeAttribute (offsets 95-316) sets
        // TypedValue.type based on the resolved value's leading character. A `@`
        // prefix forces TYPE_REFERENCE (1); the path-style `axp/layout/<name>.xml`
        // shape falls through to TYPE_STRING (3). Layout consumers do not
        // currently assert TypedValue.type, but the no-leading-@ invariant
        // matches the W4-NEXT interpolator placeholder for parity with any
        // hypothetical future strict-consumer along the LAYOUT path.
        val bundle = bundleWith(listOf(
            ParsedNsEntry.LayoutXml("design_text_input_start_icon", "<View/>", ResourceNamespace.RES_AUTO, "material"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "design_text_input_start_icon")
        val value = bundle.getResource(ref)
        assertNotNull(value)
        assertFalse(
            value!!.value!!.startsWith("@"),
            "layout placeholder must not start with @ so any TypedValue.type-sensitive consumer falls through to TYPE_STRING (3)",
        )
    }

    @Test
    fun `layout placeholder is unique per name to avoid bypass-path aliasing`()
    {
        // Contract: per-name uniqueness keeps the BridgeInflater.inflate(int,
        // ViewGroup) 2-arg bypass — which calls
        // ParserFactory.create(value, true) directly — receiving a per-layout-
        // keyed string. The defensive log in
        // MinimalLayoutlibCallback.createXmlParserForFile uses the
        // axp/layout/<name>.xml shape to detect bypass attempts; collisions
        // would mask which layout name triggered the bypass.
        val bundle = bundleWith(listOf(
            ParsedNsEntry.LayoutXml("a", "<View/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.LayoutXml("b", "<View/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val refA = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "a")
        val refB = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "b")
        val valueA = bundle.getResource(refA)!!.value
        val valueB = bundle.getResource(refB)!!.value
        assertNotNull(valueA)
        assertNotNull(valueB)
        assertTrue(valueA != valueB, "per-layout placeholder must differ across names")
    }

    @Test
    fun `duplicate layout XML — first-wins with diagnostic line and byType placeholder preserved`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val firstXml = "<View>FIRST</View>"
            val secondXml = "<View>SECOND</View>"
            val bundle = bundleWith(listOf(
                ParsedNsEntry.LayoutXml("foo", firstXml, ResourceNamespace.RES_AUTO, "appcompat"),
                ParsedNsEntry.LayoutXml("foo", secondXml, ResourceNamespace.RES_AUTO, "material"),
            ))
            val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "foo")
            assertEquals(firstXml, bundle.getLayoutXml(ref), "first-wins on raw body")
            val resource = bundle.getResource(ref)
            assertNotNull(resource, "byType[LAYOUT] placeholder must survive duplicate-handling pass")
            assertEquals(
                AppLibraryResourceConstants.layoutPlaceholderValue("foo"),
                resource!!.value,
                "byType placeholder must remain the first-registered path-style value",
            )
            assertTrue(errOut.toString().contains("dup layout-xml 'foo'"))
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `count helper reports per-namespace layout XML tallies`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.LayoutXml("a", "<View/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.LayoutXml("b", "<View/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.LayoutXml("c", "<View/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        assertEquals(3, bundle.layoutXmlCountForNamespace(ResourceNamespace.RES_AUTO))
        assertEquals(0, bundle.layoutXmlCountForNamespace(ResourceNamespace.ANDROID))
    }
}
