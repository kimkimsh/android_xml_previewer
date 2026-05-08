package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Sibling to LayoutlibResourceBundleColorStateListTest covering the drawable XML feed.
 * Validates that getDrawableXml returns the raw body, that byType[DRAWABLE] receives the
 * placeholder ResourceValue alongside the body, that duplicates are first-wins with a
 * diagnostic line, and that count helper reports the expected per-namespace tallies.
 */
class LayoutlibResourceBundleDrawableTest
{

    private fun bundleWith(entries: List<ParsedNsEntry>): LayoutlibResourceBundle =
        LayoutlibResourceBundle.build(mapOf(ResourceNamespace.RES_AUTO to entries))

    @Test
    fun `getDrawableXml returns raw body for registered name`()
    {
        val rawXml = "<vector><path android:fillColor=\"#000000\" android:pathData=\"M0 0 L10 10\"/></vector>"
        val bundle = bundleWith(listOf(
            ParsedNsEntry.DrawableXml("ic_m3_chip_close", rawXml, ResourceNamespace.RES_AUTO, "material"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "ic_m3_chip_close")
        assertEquals(rawXml, bundle.getDrawableXml(ref))
    }

    @Test
    fun `getDrawableXml returns null for unknown name`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.DrawableXml("foo", "<vector/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "missing")
        assertNull(bundle.getDrawableXml(ref))
    }

    @Test
    fun `getDrawableXml returns null for wrong namespace`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.DrawableXml("foo", "<vector/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.DRAWABLE, "foo")
        assertNull(bundle.getDrawableXml(ref))
    }

    @Test
    fun `byType DRAWABLE receives per-name xml-suffixed placeholder ResourceValue alongside raw XML`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.DrawableXml("foo", "<vector/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "foo")
        val value = bundle.getResource(ref)
        assertNotNull(value)
        assertEquals(AppLibraryResourceConstants.drawablePlaceholderValue("foo"), value!!.value)
    }

    @Test
    fun `drawable placeholder ends with xml suffix so layoutlib ResourceHelper getDrawable routes through getXmlBlockParser`()
    {
        // Contract: ResourceHelper.getDrawable gates the LayoutlibCallback.getParser
        // path on `value.toLowerCase().endsWith(".xml") || resourceType == AAPT`. A
        // drawable whose placeholder fails this gate falls through to the asset /
        // file resource path and throws Resources$NotFoundException.
        val bundle = bundleWith(listOf(
            ParsedNsEntry.DrawableXml("abc_vector_test", "<vector/>", ResourceNamespace.RES_AUTO, "appcompat"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "abc_vector_test")
        val value = bundle.getResource(ref)
        assertNotNull(value)
        assertTrue(
            value!!.value!!.lowercase().endsWith(".xml"),
            "drawable placeholder must end with .xml so ResourceHelper.getDrawable invokes getXmlBlockParser",
        )
    }

    @Test
    fun `drawable placeholder is unique per name to avoid sDrawableCache aliasing`()
    {
        // Contract: Resources_Delegate.getDrawable consults a JVM-static sDrawableCache
        // (LruCache) keyed by the value string before delegating to
        // ResourceHelper.getDrawable. A shared placeholder would alias every
        // subsequent drawable lookup to the first drawable's ConstantState.
        val bundle = bundleWith(listOf(
            ParsedNsEntry.DrawableXml("a", "<vector/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.DrawableXml("b", "<vector/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val refA = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "a")
        val refB = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "b")
        val valueA = bundle.getResource(refA)!!.value
        val valueB = bundle.getResource(refB)!!.value
        assertNotNull(valueA)
        assertNotNull(valueB)
        assertTrue(valueA != valueB, "per-drawable placeholder must differ across names")
    }

    @Test
    fun `duplicate drawable XML — first-wins with diagnostic line`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val firstXml = "<vector>FIRST</vector>"
            val secondXml = "<vector>SECOND</vector>"
            val bundle = bundleWith(listOf(
                ParsedNsEntry.DrawableXml("foo", firstXml, ResourceNamespace.RES_AUTO, "appcompat"),
                ParsedNsEntry.DrawableXml("foo", secondXml, ResourceNamespace.RES_AUTO, "material"),
            ))
            val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "foo")
            assertEquals(firstXml, bundle.getDrawableXml(ref), "first-wins")
            assertTrue(errOut.toString().contains("dup drawable-xml 'foo'"))
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `count helper reports per-namespace drawable XML tallies`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.DrawableXml("a", "<vector/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.DrawableXml("b", "<vector/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.DrawableXml("c", "<vector/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        assertEquals(3, bundle.drawableXmlCountForNamespace(ResourceNamespace.RES_AUTO))
        assertEquals(0, bundle.drawableXmlCountForNamespace(ResourceNamespace.ANDROID))
    }
}
