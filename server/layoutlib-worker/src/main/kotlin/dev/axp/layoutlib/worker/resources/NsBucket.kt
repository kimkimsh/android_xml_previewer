package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType

/**
 * W3D4 §3.1 #3: 단일 namespace 의 byType/styles/attrs immutable container.
 * LayoutlibResourceBundle.byNs 의 value type.
 */
internal data class NsBucket(
    val byType: Map<ResourceType, Map<String, ResourceValue>>,
    val styles: Map<String, StyleResourceValue>,
    val attrs: Map<String, AttrResourceValue>,
)
{
    companion object
    {
        val EMPTY: NsBucket = NsBucket(emptyMap(), emptyMap(), emptyMap())
    }
}
