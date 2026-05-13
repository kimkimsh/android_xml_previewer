package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType

/**
 * Single-namespace immutable container holding the resource shape that
 * LayoutlibResourceBundle.byNs maps each ResourceNamespace to. Splits resources
 * across four maps:
 *  - byType: generic ResourceValue keyed by ResourceType + name (covers SimpleValue
 *    plus the placeholder ResourceValue for color-state-list / animator / drawable
 *    entries that gives BridgeContext's getResource a non-null value to return).
 *  - styles / attrs: type-specific maps preserving StyleResourceValue and
 *    AttrResourceValue runtime types that the bridge casts to after defStyleAttr /
 *    defStyleRes resolution.
 *  - colorStateLists / animators / drawables / interpolators: name → raw XML body,
 *    fed back into layoutlib through MinimalLayoutlibCallback.getParser when Bridge
 *    asks for an XmlResourceParser via ResourceHelper.getColorStateList,
 *    AnimatorInflater, DrawableInflater, or AnimationUtils.loadInterpolator
 *    respectively.
 */
internal data class NsBucket(
    val byType: Map<ResourceType, Map<String, ResourceValue>>,
    val styles: Map<String, StyleResourceValue>,
    val attrs: Map<String, AttrResourceValue>,
    val colorStateLists: Map<String, String> = emptyMap(),
    val animators: Map<String, String> = emptyMap(),
    val drawables: Map<String, String> = emptyMap(),
    val interpolators: Map<String, String> = emptyMap(),
)
{
    companion object
    {
        val EMPTY: NsBucket = NsBucket(
            emptyMap(), emptyMap(), emptyMap(),
            emptyMap(), emptyMap(), emptyMap(), emptyMap(),
        )
    }
}
