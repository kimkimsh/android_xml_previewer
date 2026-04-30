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
 * W3D4-β T12: LayoutlibResourceBundle 의 colorStateLists 처리 검증.
 *
 * - getColorStateListXml 은 byNs[ns].colorStateLists[name] 로 raw XML 반환.
 * - byType[COLOR][name] 에 placeholder ResourceValue 동반 등록 (BridgeContext resolution 단계 통과).
 * - 동명 dup 진단 로그 (first-wins).
 */
class LayoutlibResourceBundleColorStateListTest
{

    private fun bundleWith(entries: List<ParsedNsEntry>): LayoutlibResourceBundle =
        LayoutlibResourceBundle.build(mapOf(ResourceNamespace.RES_AUTO to entries))

    @Test
    fun `getColorStateListXml 은 등록된 name 의 raw XML 반환`()
    {
        val rawXml = "<selector><item android:color=\"#ff0000\"/></selector>"
        val bundle = bundleWith(listOf(
            ParsedNsEntry.ColorStateList("m3_highlighted_text", rawXml, ResourceNamespace.RES_AUTO, "material"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "m3_highlighted_text")
        assertEquals(rawXml, bundle.getColorStateListXml(ref))
    }

    @Test
    fun `getColorStateListXml — unknown name 은 null`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.ColorStateList("foo", "<selector/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "missing")
        assertNull(bundle.getColorStateListXml(ref))
    }

    @Test
    fun `getColorStateListXml — wrong namespace 는 null`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.ColorStateList("foo", "<selector/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.COLOR, "foo")
        assertNull(bundle.getColorStateListXml(ref))
    }

    @Test
    fun `byType COLOR 에 placeholder ResourceValue 동반 등록`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.ColorStateList("foo", "<selector/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "foo")
        val value = bundle.getResource(ref)
        assertNotNull(value)
        assertEquals(AppLibraryResourceConstants.COLOR_STATE_LIST_PLACEHOLDER_VALUE, value!!.value)
    }

    @Test
    fun `dup color state list — first-wins + 진단 로그`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val firstXml = "<selector>FIRST</selector>"
            val secondXml = "<selector>SECOND</selector>"
            val bundle = bundleWith(listOf(
                ParsedNsEntry.ColorStateList("foo", firstXml, ResourceNamespace.RES_AUTO, "appcompat"),
                ParsedNsEntry.ColorStateList("foo", secondXml, ResourceNamespace.RES_AUTO, "material"),
            ))
            val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "foo")
            assertEquals(firstXml, bundle.getColorStateListXml(ref), "first-wins")
            assertTrue(errOut.toString().contains("dup color-state-list 'foo'"))
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `count helper — namespace 별 colorStateList 개수`()
    {
        val bundle = bundleWith(listOf(
            ParsedNsEntry.ColorStateList("a", "<selector/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.ColorStateList("b", "<selector/>", ResourceNamespace.RES_AUTO, "p"),
            ParsedNsEntry.ColorStateList("c", "<selector/>", ResourceNamespace.RES_AUTO, "p"),
        ))
        assertEquals(3, bundle.colorStateListCountForNamespace(ResourceNamespace.RES_AUTO))
        assertEquals(0, bundle.colorStateListCountForNamespace(ResourceNamespace.ANDROID))
    }
}
