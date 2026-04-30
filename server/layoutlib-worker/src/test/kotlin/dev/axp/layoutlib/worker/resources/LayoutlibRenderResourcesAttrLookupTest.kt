package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * W3D4-γ T14 (round 4 Codex Q5 acceptance-critical): RenderResources 의 양쪽 ATTR lookup
 * entry point — getResolvedResource (BridgeTypedArray.resolveEnumAttribute path) +
 * getUnresolvedResource (BridgeXmlPullAttributes.getAttributeIntValue project supplier path) —
 * 가 모두 AttrResourceValueImpl 인스턴스 반환 검증. instanceof AttrResourceValue cast 통과
 * = enum/flag 변환 가능 = "not a valid integer" warning 회피.
 */
internal class LayoutlibRenderResourcesAttrLookupTest
{
    @Test
    fun `getResolvedResource - RES_AUTO ATTR returns AttrResourceValue with parent enum`()
    {
        val rr = makeRenderResources(
            ResourceNamespace.RES_AUTO,
            "layout_constraintEnd_toEndOf",
            mapOf("parent" to 0),
        )
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "layout_constraintEnd_toEndOf")
        val resolved = rr.getResolvedResource(ref)
        assertNotNull(resolved, "BridgeTypedArray path: getResolvedResource(ATTR) must not return null")
        assertTrue(resolved is AttrResourceValue, "Q5 fix: must be AttrResourceValue for instanceof cast")
        val attr = resolved as AttrResourceValue
        assertEquals(mapOf("parent" to 0), attr.attributeValues)
    }

    @Test
    fun `getUnresolvedResource - RES_AUTO ATTR returns AttrResourceValue with parent enum`()
    {
        // BridgeXmlPullAttributes.getAttributeIntValue 의 project supplier 가 사용하는 sibling path.
        val rr = makeRenderResources(
            ResourceNamespace.RES_AUTO,
            "layout_constraintEnd_toEndOf",
            mapOf("parent" to 0),
        )
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "layout_constraintEnd_toEndOf")
        val unresolved = rr.getUnresolvedResource(ref)
        assertNotNull(unresolved, "BridgeXmlPullAttributes path: getUnresolvedResource(ATTR) must not return null")
        assertTrue(unresolved is AttrResourceValue)
        val attr = unresolved as AttrResourceValue
        assertEquals(mapOf("parent" to 0), attr.attributeValues)
    }

    @Test
    fun `getResolvedResource - ANDROID ATTR also exposed (framework path 통합 회귀 검사)`()
    {
        val rr = makeRenderResources(
            ResourceNamespace.ANDROID,
            "orientation",
            mapOf("horizontal" to 0, "vertical" to 1),
        )
        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, "orientation")
        val resolved = rr.getResolvedResource(ref)
        assertNotNull(resolved)
        val attr = resolved as AttrResourceValue
        assertEquals(mapOf("horizontal" to 0, "vertical" to 1), attr.attributeValues)
    }

    private fun makeRenderResources(
        ns: ResourceNamespace,
        attrName: String,
        enums: Map<String, Int>,
    ): LayoutlibRenderResources
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ns to listOf(
                    ParsedNsEntry.AttrDef(
                        attrName,
                        ns,
                        enums,
                        emptyMap(),
                        "test",
                    ),
                ),
            ),
        )
        // theme 이름은 lookup 실패 시 emptyTheme fallback — ATTR lookup path 와 무관.
        return LayoutlibRenderResources(bundle, "Theme.AxpFixture")
    }
}
