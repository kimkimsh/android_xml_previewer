package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType

/**
 * Namespace + sourcePackage tagged parsed entry. sourcePackage is for dedupe
 * diagnostic output only (which AAR the entry came from); production resolution
 * does not consult it. This sealed class is distinct from the older single-
 * namespace ParsedEntry — it carries the namespace explicitly so multi-AAR +
 * sample-app + framework can coexist on the same chain.
 */
internal sealed class ParsedNsEntry
{
    abstract val namespace: ResourceNamespace
    abstract val sourcePackage: String?  // null = framework / sample-app

    /**
     * Single-value entry — `<dimen name="X">4dp</dimen>`, `<color name="X">#fff</color>`,
     * `<integer>`, `<bool>`, `<string>`, `<item type="X" name="Y">Z</item>`.
     * Namespace tagged.
     */
    data class SimpleValue(
        val type: ResourceType,
        val name: String,
        val value: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    /**
     * `<attr name="X" format="Y" />` declaration (namespace tagged). Collected from
     * both top-level and `<declare-styleable>` children.
     *
     * `<enum>` / `<flag>` child value tables (name → integer) are also preserved.
     * BridgeTypedArray.resolveEnumAttribute reads them from the RES_AUTO path via
     * AttrResourceValueImpl.getAttributeValues(); the framework path goes through
     * the separate Bridge.sEnumValueMap (same data source).
     *
     * enumValues and flagValues are both Map<String, Int> with insertion order
     * preserved. An attr holding both enum and flag children was 0 cases in the
     * framework attrs.xml census — when one side is empty the field is explicitly
     * emptyMap().
     */
    data class AttrDef(
        val name: String,
        override val namespace: ResourceNamespace,
        val enumValues: Map<String, Int>,
        val flagValues: Map<String, Int>,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    /**
     * `<style name="X" parent="Y"> <item name="A">B</item> ... </style>` (namespace
     * tagged). parent is the raw unprocessed string (null or empty allowed). items
     * preserve declaration order.
     */
    data class StyleDef(
        val name: String,
        val parent: String?,
        val items: List<StyleItem>,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()
    {
        /** A single `<item name="...">value</item>` inside a `<style>`. */
        data class StyleItem(val name: String, val value: String)
    }

    /**
     * `res/color/<name>.xml` color state list (`<selector>` root). rawXml is the
     * full selector body. MinimalLayoutlibCallback.getParser wraps it in a
     * StringReader and feeds it to Bridge ResourceHelper.getColorStateList.
     */
    data class ColorStateList(
        val name: String,
        val rawXml: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    /**
     * `res/animator/<name>.xml` raw body — Material AAR stateListAnimator and
     * motion-spec XML files. callback.getParser feeds the body through
     * SelectorXmlPullParser the same way ColorStateList does;
     * AnimatorInflater consumes the result via XmlResourceParser.
     */
    data class AnimatorXml(
        val name: String,
        val rawXml: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    /**
     * `res/drawable/<name>.xml` raw body — vector / animated-vector / shape / selector /
     * layer-list / inset / ripple / level-list root elements. Style items in Material AAR
     * (e.g. Base.Widget.Material3.Chip.checkedIcon → @drawable/ic_m3_chip_checked_circle)
     * resolve to these files at inflate time, regardless of widget visibility. callback
     * .getParser feeds the rawXml through SelectorXmlPullParser; BridgeContext's
     * DrawableInflater consumes via XmlResourceParser.
     */
    data class DrawableXml(
        val name: String,
        val rawXml: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    /**
     * `res/interpolator/<name>.xml` raw body — Material 3 motion-easing interpolators
     * (linear, standard, emphasized, accelerate / decelerate variants). MotionUtils
     * .resolveThemeInterpolator routes via Theme.resolveAttribute on
     * ?attr/motionEasing*Interpolator and consumes the resolved TypedValue.resourceId
     * via AnimationUtils.loadInterpolator → Resources.getXml → callback.getParser.
     * The bundle's per-name path-style placeholder lets the TypedValue.type pass the
     * MotionUtils TYPE_STRING (3) assertion at the same time as the resourceId routes
     * through the standard callback feed.
     */
    data class InterpolatorXml(
        val name: String,
        val rawXml: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    /**
     * `res/layout/<name>.xml` raw body — AppCompat / core / Material AAR-internal
     * layout files (e.g. `design_text_input_start_icon` for TextInputLayout's
     * leading icon container). LayoutInflater.inflate(int, ViewGroup, boolean)
     * routes via Resources_Delegate.getLayout → ResourceHelper.getXmlBlockParser
     * → callback.getParser at the same callback path as ColorStateList /
     * AnimatorXml / DrawableXml / InterpolatorXml. Default qualifier directory
     * only — layout-v21/, layout-night/, etc. are out of scope until W4+
     * qualifier support.
     */
    data class LayoutXml(
        val name: String,
        val rawXml: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String?,
    ) : ParsedNsEntry()
}
