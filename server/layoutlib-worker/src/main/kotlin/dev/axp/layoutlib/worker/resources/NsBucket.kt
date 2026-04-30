package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType

/**
 * W3D4 §3.1 #3: 단일 namespace 의 byType/styles/attrs immutable container.
 * LayoutlibResourceBundle.byNs 의 value type.
 *
 * W3D4-β T12: colorStateLists (name → raw selector XML) 추가. Bridge
 * ResourceHelper.getColorStateList 가 callback.getParser 를 통해 input feed 받을
 * 대상. byType[COLOR] 에는 placeholder ResourceValue 가 같이 등록되어 BridgeContext
 * resolution 단계 통과를 보장.
 */
internal data class NsBucket(
    val byType: Map<ResourceType, Map<String, ResourceValue>>,
    val styles: Map<String, StyleResourceValue>,
    val attrs: Map<String, AttrResourceValue>,
    val colorStateLists: Map<String, String> = emptyMap(),
)
{
    companion object
    {
        val EMPTY: NsBucket = NsBucket(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}
