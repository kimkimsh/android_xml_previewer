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
 * Sibling to LayoutlibResourceBundleDrawableTest covering the interpolator XML feed.
 * Validates that getInterpolatorXml returns the raw body, that byType[INTERPOLATOR]
 * receives a per-name path-style placeholder ResourceValue alongside the body, that
 * duplicates are first-wins with a diagnostic line, and that the count helper reports
 * the expected per-namespace tallies.
 *
 * The contract tests on placeholder shape lock in the two BridgeContext invariants
 * required for MotionUtils.resolveThemeInterpolator: the placeholder must NOT start
 * with `@` (so TypedValue.type resolves to TYPE_STRING (3) instead of TYPE_REFERENCE
 * (1)) and it must be unique per interpolator name (so any downstream cache keyed
 * by the value string addresses each interpolator independently).
 */
class LayoutlibResourceBundleInterpolatorTest
{

    private fun bundleWith(entries: List<ParsedNsEntry>): LayoutlibResourceBundle =
        LayoutlibResourceBundle.build(mapOf(ResourceNamespace.RES_AUTO to entries))

    @Test
    fun `getInterpolatorXml returns raw body for registered name`()
    {
        val rawXml = "<pathInterpolator android:controlX1=\"0.2\" android:controlY1=\"0\" android:controlX2=\"0\" android:controlY2=\"1\"/>"
        val bundle = bundleWith(listOf(
            ParsedNsEntry.InterpolatorXml("m3_sys_motion_easing_emphasized", rawXml, ResourceNamespace.RES_AUTO, "material"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.INTERPOLATOR, "m3_sys_motion_easing_emphasized")
        assertEquals(rawXml, bundle.getInterpolatorXml(ref))
    }

    @Test
    fun `getInterpolatorXml returns null for unknown name`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.InterpolatorXml("foo", "<pathInterpolator/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.INTERPOLATOR, "missing")
        assertNull(bundle.getInterpolatorXml(ref))
    }

    @Test
    fun `getInterpolatorXml returns null for wrong namespace`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.InterpolatorXml("foo", "<pathInterpolator/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.INTERPOLATOR, "foo")
        assertNull(bundle.getInterpolatorXml(ref))
    }

    @Test
    fun `byType INTERPOLATOR receives per-name path-style placeholder ResourceValue alongside raw XML`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.InterpolatorXml("foo", "<pathInterpolator/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.INTERPOLATOR, "foo")
        val value = bundle.getResource(ref)
        assertNotNull(value)
        assertEquals(AppLibraryResourceConstants.interpolatorPlaceholderValue("foo"), value!!.value)
    }

    @Test
    fun `interpolator placeholder does not start with the at sign so MotionUtils sees TypedValue type TYPE_STRING`()
    {
        // Contract: BridgeContext.resolveThemeAttribute inspects the resolved
        // ResourceValue's value string. A leading `@` (offset 204-220) forces
        // TypedValue.type = TYPE_REFERENCE (1). MotionUtils.resolveThemeInterpolator
        // requires TYPE_STRING (3) and throws otherwise. Therefore the placeholder
        // must never start with `@`.
        val bundle = bundleWith(listOf(
            ParsedNsEntry.InterpolatorXml("m3_sys_motion_easing_emphasized", "<pathInterpolator/>", ResourceNamespace.RES_AUTO, "material"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.INTERPOLATOR, "m3_sys_motion_easing_emphasized")
        val value = bundle.getResource(ref)
        assertNotNull(value)
        assertFalse(
            value!!.value!!.startsWith("@"),
            "interpolator placeholder must not start with @ so BridgeContext sets TypedValue.type to TYPE_STRING (3), not TYPE_REFERENCE (1)",
        )
    }

    @Test
    fun `interpolator placeholder is unique per name to avoid cache aliasing`()
    {
        // Contract: per-name uniqueness keeps any downstream value-keyed cache
        // (sibling to Resources_Delegate.sDrawableCache for drawables) keyed
        // correctly across distinct interpolator names within one JVM session.
        val bundle = bundleWith(listOf(
            ParsedNsEntry.InterpolatorXml("a", "<pathInterpolator/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.InterpolatorXml("b", "<pathInterpolator/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val refA = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.INTERPOLATOR, "a")
        val refB = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.INTERPOLATOR, "b")
        val valueA = bundle.getResource(refA)!!.value
        val valueB = bundle.getResource(refB)!!.value
        assertNotNull(valueA)
        assertNotNull(valueB)
        assertTrue(valueA != valueB, "per-interpolator placeholder must differ across names")
    }

    @Test
    fun `duplicate interpolator XML — first-wins with diagnostic line and byType placeholder preserved`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val firstXml = "<pathInterpolator>FIRST</pathInterpolator>"
            val secondXml = "<pathInterpolator>SECOND</pathInterpolator>"
            val bundle = bundleWith(listOf(
                ParsedNsEntry.InterpolatorXml("foo", firstXml, ResourceNamespace.RES_AUTO, "appcompat"),
                ParsedNsEntry.InterpolatorXml("foo", secondXml, ResourceNamespace.RES_AUTO, "material"),
            ))
            val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.INTERPOLATOR, "foo")
            assertEquals(firstXml, bundle.getInterpolatorXml(ref), "first-wins on raw body")
            val resource = bundle.getResource(ref)
            assertNotNull(resource, "byType[INTERPOLATOR] placeholder must survive duplicate-handling pass")
            assertEquals(
                AppLibraryResourceConstants.interpolatorPlaceholderValue("foo"),
                resource!!.value,
                "byType placeholder must remain the first-registered path-style value",
            )
            assertTrue(errOut.toString().contains("dup interpolator-xml 'foo'"))
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `count helper reports per-namespace interpolator XML tallies`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.InterpolatorXml("a", "<pathInterpolator/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.InterpolatorXml("b", "<pathInterpolator/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.InterpolatorXml("c", "<pathInterpolator/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        assertEquals(3, bundle.interpolatorXmlCountForNamespace(ResourceNamespace.RES_AUTO))
        assertEquals(0, bundle.interpolatorXmlCountForNamespace(ResourceNamespace.ANDROID))
    }
}
