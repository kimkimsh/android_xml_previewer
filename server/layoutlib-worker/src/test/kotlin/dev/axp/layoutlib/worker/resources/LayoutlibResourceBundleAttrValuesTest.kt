package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * W3D4-γ T14: LayoutlibResourceBundle.buildBucket 의 AttrDef → AttrResourceValueImpl.addValue
 * + getResource ATTR special-case (round 4 Codex Q5 KILL POINT fix) 단위 테스트.
 *
 * BridgeTypedArray.resolveEnumAttribute 와 BridgeXmlPullAttributes.getAttributeIntValue 가
 * 동일 surface — getResource(ATTR ref) 가 AttrResourceValueImpl 인스턴스 직접 반환해야
 * instanceof AttrResourceValue cast 가 통과.
 */
internal class LayoutlibResourceBundleAttrValuesTest
{
    @Test
    fun `buildBucket - AttrDef with enumValues populates AttrResourceValueImpl getAttributeValues`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.AttrDef(
                        "orientation",
                        ResourceNamespace.RES_AUTO,
                        mapOf("horizontal" to 0, "vertical" to 1),
                        emptyMap(),
                        "test",
                    ),
                ),
            ),
        )
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "orientation")
        val resource = bundle.getResource(ref)
        assertNotNull(resource)
        assertTrue(resource is AttrResourceValue, "getResource(ATTR) must return AttrResourceValue")
        val attr = resource as AttrResourceValue
        assertEquals(mapOf("horizontal" to 0, "vertical" to 1), attr.attributeValues)
    }

    @Test
    fun `buildBucket - AttrDef with flagValues populates same map (single Map per attr in layoutlib API)`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.AttrDef(
                        "gravity",
                        ResourceNamespace.RES_AUTO,
                        emptyMap(),
                        mapOf("top" to 0x30, "center_horizontal" to 0x01),
                        "test",
                    ),
                ),
            ),
        )
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "gravity")
        val attr = bundle.getResource(ref) as AttrResourceValue
        // layoutlib API 는 enum/flag 둘 다 동일 attributeValues map (서로 다른 accessor 부재).
        assertEquals(mapOf("top" to 0x30, "center_horizontal" to 0x01), attr.attributeValues)
    }

    @Test
    fun `buildBucket - AttrDef with empty enum and flag — getAttributeValues returns emptyMap`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.AttrDef(
                        "customRef",
                        ResourceNamespace.RES_AUTO,
                        emptyMap(),
                        emptyMap(),
                        "test",
                    ),
                ),
            ),
        )
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "customRef")
        val attr = bundle.getResource(ref) as AttrResourceValue
        assertTrue(attr.attributeValues.isEmpty(), "empty enum/flag → empty getAttributeValues")
    }

    @Test
    fun `getResource - ATTR ref returns AttrResourceValueImpl with populated values (Q5 fix regression guard)`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.AttrDef(
                        "layout_constraintEnd_toEndOf",
                        ResourceNamespace.RES_AUTO,
                        mapOf("parent" to 0),
                        emptyMap(),
                        "constraintlayout",
                    ),
                ),
            ),
        )
        // Q5 KILL POINT: 이 lookup 이 null 반환하면 BridgeTypedArray.resolveEnumAttribute 의
        // RES_AUTO branch 가 instanceof cast fail → "is not a valid integer" warning.
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "layout_constraintEnd_toEndOf")
        val resource = bundle.getResource(ref)
        assertNotNull(resource, "Q5 fix: bundle.getResource(ATTR) must not return null")
        val attr = resource as AttrResourceValue
        assertEquals(mapOf("parent" to 0), attr.attributeValues)
    }

    @Test
    fun `getResource - non-ATTR ref still uses byType bucket (special-case 가 다른 type 영향 없음)`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.SimpleValue(
                        ResourceType.DIMEN,
                        "spacing",
                        "8dp",
                        ResourceNamespace.RES_AUTO,
                        "test",
                    ),
                ),
            ),
        )
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DIMEN, "spacing")
        val resource = bundle.getResource(ref)
        assertNotNull(resource)
        assertEquals("8dp", resource!!.value)
        // unknown name 은 여전히 null.
        val missingRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DIMEN, "missing")
        assertNull(bundle.getResource(missingRef))
    }
}
