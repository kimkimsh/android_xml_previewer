package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * W3D4-γ T15: LayoutlibResourceBundle.frameworkEnumValueMap() 의 ANDROID bucket 추출 검증.
 *
 * Bridge.init 의 6번째 인자 (Map<String, Map<String, Integer>>) 형식으로 export. 빈 attr 은
 * 결과 map 에서 제외 (BridgeTypedArray 가 outer map miss 를 정상 처리하므로 noise 회피). RES_AUTO
 * bucket 은 별도 경로 (T14 의 getResource ATTR special-case) — 본 helper 의 결과에 미포함.
 */
internal class LayoutlibResourceBundleFrameworkEnumExportTest
{
    @Test
    fun `frameworkEnumValueMap - returns enum entries from ANDROID bucket`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.ANDROID to listOf(
                    ParsedNsEntry.AttrDef(
                        "orientation",
                        ResourceNamespace.ANDROID,
                        mapOf("horizontal" to 0, "vertical" to 1),
                        emptyMap(),
                        null,
                    ),
                ),
            ),
        )
        val out = bundle.frameworkEnumValueMap()
        assertEquals(setOf("orientation"), out.keys)
        assertEquals(mapOf("horizontal" to 0, "vertical" to 1), out["orientation"])
    }

    @Test
    fun `frameworkEnumValueMap - flag entries merged with enum (single layoutlib map per attr)`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.ANDROID to listOf(
                    ParsedNsEntry.AttrDef(
                        "gravity",
                        ResourceNamespace.ANDROID,
                        emptyMap(),
                        mapOf("top" to 0x30, "center_horizontal" to 0x01),
                        null,
                    ),
                ),
            ),
        )
        val out = bundle.frameworkEnumValueMap()
        // layoutlib API 는 enum/flag 둘 다 동일 attributeValues — frameworkEnumValueMap 도 합쳐서 emit.
        assertEquals(mapOf("top" to 0x30, "center_horizontal" to 0x01), out["gravity"])
    }

    @Test
    fun `frameworkEnumValueMap - excludes attrs with empty maps`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.ANDROID to listOf(
                    ParsedNsEntry.AttrDef("ref", ResourceNamespace.ANDROID, emptyMap(), emptyMap(), null),
                    ParsedNsEntry.AttrDef(
                        "orientation",
                        ResourceNamespace.ANDROID,
                        mapOf("vertical" to 1),
                        emptyMap(),
                        null,
                    ),
                ),
            ),
        )
        val out = bundle.frameworkEnumValueMap()
        // empty 인 "ref" 는 결과에서 제외; "orientation" 만 emit.
        assertEquals(setOf("orientation"), out.keys)
        assertNull(out["ref"])
    }

    @Test
    fun `frameworkEnumValueMap - returns emptyMap when ANDROID bucket absent`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.AttrDef(
                        "local",
                        ResourceNamespace.RES_AUTO,
                        mapOf("a" to 1),
                        emptyMap(),
                        null,
                    ),
                ),
            ),
        )
        val out = bundle.frameworkEnumValueMap()
        assertTrue(out.isEmpty(), "ANDROID bucket 부재 시 결과 비어야 함")
    }

    @Test
    fun `frameworkEnumValueMap - RES_AUTO bucket attrs not included (bucket separation)`()
    {
        val bundle = LayoutlibResourceBundle.build(
            mapOf(
                ResourceNamespace.ANDROID to listOf(
                    ParsedNsEntry.AttrDef(
                        "orientation",
                        ResourceNamespace.ANDROID,
                        mapOf("vertical" to 1),
                        emptyMap(),
                        null,
                    ),
                ),
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.AttrDef(
                        "layout_constraintEnd_toEndOf",
                        ResourceNamespace.RES_AUTO,
                        mapOf("parent" to 0),
                        emptyMap(),
                        null,
                    ),
                ),
            ),
        )
        val out = bundle.frameworkEnumValueMap()
        // ANDROID bucket 만 — RES_AUTO 의 layout_constraintEnd_toEndOf 는 별도 path (T14).
        assertEquals(setOf("orientation"), out.keys)
        assertNull(out["layout_constraintEnd_toEndOf"])
    }
}
