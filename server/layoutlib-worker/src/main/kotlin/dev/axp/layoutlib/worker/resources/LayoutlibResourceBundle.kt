package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValueImpl
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleResourceValueImpl
import com.android.resources.ResourceType

/**
 * Namespace-aware immutable resource bundle. byNs is a LinkedHashMap with a
 * deterministic ANDROID → RES_AUTO iteration order; the unified two-bucket layout
 * collapses the original multi-namespace shape and logs a single diagnostic line
 * on every duplicate.
 *
 * Dedupe policy:
 *  - SimpleValue / StyleDef: later-wins per namespace + one diagnostic line.
 *  - AttrDef: first-wins (silent).
 *
 * Cross-namespace parent inference reuses StyleParentInference and stays
 * namespace-agnostic. Parent names are preserved verbatim; only null falls
 * back. Namespace-agnostic chain walking is the next phase's responsibility.
 */
internal class LayoutlibResourceBundle private constructor(
    private val byNs: LinkedHashMap<ResourceNamespace, NsBucket>,
)
{

    fun getStyleExact(ref: ResourceReference): StyleResourceValue? =
        byNs[ref.namespace]?.styles?.get(ref.name)

    fun getStyleByName(name: String): StyleResourceValue?
    {
        for (bucket in byNs.values)
        {
            bucket.styles[name]?.let { return it }
        }
        return null
    }

    /**
     * Looks up a resource by namespace + type. ATTR and STYLE refs read from their
     * type-specific maps (`attrs` / `styles`) so the returned instance preserves the
     * AttrResourceValue / StyleResourceValue runtime type that bridge code casts to
     * after defStyleAttr / defStyleRes resolution; every other type falls through to
     * the generic byType bucket. Splitting attrs and styles out of byType is what
     * keeps NsBucket.attrs and NsBucket.styles each aligned with the layoutlib API
     * the bridge expects.
     */
    fun getResource(ref: ResourceReference): ResourceValue?
    {
        val bucket = byNs[ref.namespace] ?: return null
        if (ref.resourceType == ResourceType.ATTR)
        {
            return bucket.attrs[ref.name]
        }
        if (ref.resourceType == ResourceType.STYLE)
        {
            return bucket.styles[ref.name]
        }
        return bucket.byType[ref.resourceType]?.get(ref.name)
    }

    /**
     * Raw body lookup for `<selector>` color state-list XML. Bridge
     * ResourceHelper.getColorStateList feeds the body through
     * MinimalLayoutlibCallback.getParser at the input-feed step.
     */
    fun getColorStateListXml(ref: ResourceReference): String? =
        byNs[ref.namespace]?.colorStateLists?.get(ref.name)

    /**
     * Sibling to getColorStateListXml — animator/motion-spec XML body lookup.
     * MinimalLayoutlibCallback.getParser routes ResourceType.ANIMATOR through the same
     * raw-XML feed mechanism, so Bridge AnimatorInflater receives an XmlResourceParser
     * with input pointing at this body.
     */
    fun getAnimatorXml(ref: ResourceReference): String? =
        byNs[ref.namespace]?.animators?.get(ref.name)

    /**
     * Sibling to getColorStateListXml / getAnimatorXml — drawable XML body lookup.
     * MinimalLayoutlibCallback.getParser routes ResourceType.DRAWABLE through the same
     * raw-XML feed mechanism, so BridgeContext's DrawableInflater receives an
     * XmlResourceParser with input pointing at this body. Covers vector / animated-vector
     * / shape / selector / layer-list / inset / ripple / level-list root elements.
     */
    fun getDrawableXml(ref: ResourceReference): String? =
        byNs[ref.namespace]?.drawables?.get(ref.name)

    /**
     * Sibling to getDrawableXml — interpolator XML body lookup. MinimalLayoutlibCallback
     * .getParser routes ResourceType.INTERPOLATOR through the same raw-XML feed
     * mechanism; layoutlib's `Resources_Delegate.getXml` delegates to
     * callback.getParser when AnimationUtils.loadInterpolator opens the XML by
     * resourceId. Covers the Material 3 motion-easing interpolators that MotionUtils
     * .resolveThemeInterpolator consumes for Fade transitions and ValueAnimator.
     */
    fun getInterpolatorXml(ref: ResourceReference): String? =
        byNs[ref.namespace]?.interpolators?.get(ref.name)

    /** Diagnostic / test accessors. */
    fun namespacesInOrder(): List<ResourceNamespace> = byNs.keys.toList()
    fun styleCountForNamespace(ns: ResourceNamespace): Int = byNs[ns]?.styles?.size ?: 0
    fun attrCountForNamespace(ns: ResourceNamespace): Int = byNs[ns]?.attrs?.size ?: 0
    fun colorStateListCountForNamespace(ns: ResourceNamespace): Int =
        byNs[ns]?.colorStateLists?.size ?: 0
    fun animatorCountForNamespace(ns: ResourceNamespace): Int =
        byNs[ns]?.animators?.size ?: 0
    fun drawableXmlCountForNamespace(ns: ResourceNamespace): Int =
        byNs[ns]?.drawables?.size ?: 0
    fun interpolatorXmlCountForNamespace(ns: ResourceNamespace): Int =
        byNs[ns]?.interpolators?.size ?: 0

    /**
     * Exports framework (ANDROID-bucket) enum and flag tables for Bridge.init's
     * enumValueMap argument (the sixth parameter, which becomes Bridge.sEnumValueMap).
     * Aggregates the AttrResourceValueImpl enum / flag children of every ANDROID
     * AttrDef into a single Map<String, Map<String, Int>>; an attr with no enum or
     * flag children is omitted. RES_AUTO attrs are exported via the separate
     * getResource ATTR special-case backed by AttrResourceValueImpl
     * .getAttributeValues, so this helper stays framework-only.
     */
    fun frameworkEnumValueMap(): Map<String, Map<String, Int>>
    {
        val src = byNs[ResourceNamespace.ANDROID]?.attrs ?: return emptyMap()
        val out = LinkedHashMap<String, Map<String, Int>>()
        for ((name, attr) in src)
        {
            val raw = attr.attributeValues
            if (raw.isNullOrEmpty()) continue
            val converted = LinkedHashMap<String, Int>(raw.size)
            for ((k, v) in raw)
            {
                if (v != null) converted[k] = v.toInt()
            }
            if (converted.isNotEmpty()) out[name] = converted
        }
        return out
    }

    companion object
    {
        fun build(perNamespaceEntries: Map<ResourceNamespace, List<ParsedNsEntry>>): LayoutlibResourceBundle
        {
            val canonicalOrder = listOf(ResourceNamespace.ANDROID, ResourceNamespace.RES_AUTO)
            val byNs = LinkedHashMap<ResourceNamespace, NsBucket>()
            for (ns in canonicalOrder)
            {
                val entries = perNamespaceEntries[ns] ?: continue
                byNs[ns] = buildBucket(ns, entries)
            }
            // Preserve insertion order for any extra namespaces (forward-compatible
            // hook for future namespace-aware bucket splits).
            for ((ns, entries) in perNamespaceEntries)
            {
                if (ns !in canonicalOrder)
                {
                    byNs[ns] = buildBucket(ns, entries)
                }
            }
            return LayoutlibResourceBundle(byNs)
        }

        private fun buildBucket(ns: ResourceNamespace, entries: List<ParsedNsEntry>): NsBucket
        {
            val byTypeMut = mutableMapOf<ResourceType, MutableMap<String, ResourceValue>>()
            val attrsMut = LinkedHashMap<String, AttrResourceValueImpl>()
            val stylesMut = LinkedHashMap<String, StyleResourceValueImpl>()
            val styleDefs = mutableListOf<ParsedNsEntry.StyleDef>()
            val colorStateListsMut = LinkedHashMap<String, String>()
            val animatorsMut = LinkedHashMap<String, String>()
            val drawablesMut = LinkedHashMap<String, String>()
            val interpolatorsMut = LinkedHashMap<String, String>()

            for (e in entries) when (e)
            {
                is ParsedNsEntry.SimpleValue ->
                {
                    val typeMap = byTypeMut.getOrPut(e.type) { mutableMapOf() }
                    val existed = typeMap[e.name]
                    if (existed != null)
                    {
                        System.err.println(
                            "[LayoutlibResourceBundle] dup ${e.type.getName()} '${e.name}' ns=${ns.packageName ?: "RES_AUTO"} from ${e.sourcePackage} — adopting later"
                        )
                    }
                    val ref = ResourceReference(ns, e.type, e.name)
                    typeMap[e.name] = ResourceValueImpl(ref, e.value, null)
                }
                is ParsedNsEntry.AttrDef ->
                {
                    if (!attrsMut.containsKey(e.name))
                    {
                        val ref = ResourceReference(ns, ResourceType.ATTR, e.name)
                        val attr = AttrResourceValueImpl(ref, null)
                        // Inject <enum> / <flag> child value tables into
                        // AttrResourceValueImpl so BridgeTypedArray.resolveEnumAttribute
                        // can look them up via getAttributeValues().get("vertical")
                        // when converting a RES_AUTO attr. Values are boxed Integer
                        // (Kotlin Int auto-box).
                        for ((enumName, enumValue) in e.enumValues)
                        {
                            attr.addValue(enumName, enumValue, null)
                        }
                        for ((flagName, flagValue) in e.flagValues)
                        {
                            attr.addValue(flagName, flagValue, null)
                        }
                        attrsMut[e.name] = attr
                    }
                    // first-wins — a second registration is silent. Empirical scan
                    // of the 41-AAR fixture found zero nonempty-vs-nonempty conflicts
                    // so the first nonempty entry is always preserved.
                }
                is ParsedNsEntry.StyleDef -> styleDefs += e
                is ParsedNsEntry.ColorStateList ->
                {
                    // Register a placeholder ResourceValue in byType[COLOR] so
                    // BridgeContext.getResource always finds a non-null value. The
                    // magic placeholder string is intercepted by callback.getParser,
                    // so Bridge never falls back to ParserFactory.create.
                    val typeMap = byTypeMut.getOrPut(ResourceType.COLOR) { mutableMapOf() }
                    if (!typeMap.containsKey(e.name))
                    {
                        val ref = ResourceReference(ns, ResourceType.COLOR, e.name)
                        typeMap[e.name] = ResourceValueImpl(
                            ref,
                            AppLibraryResourceConstants.COLOR_STATE_LIST_PLACEHOLDER_VALUE,
                            null,
                        )
                    }
                    if (colorStateListsMut.containsKey(e.name))
                    {
                        System.err.println(
                            "[LayoutlibResourceBundle] dup color-state-list '${e.name}' ns=${ns.packageName ?: "RES_AUTO"} from ${e.sourcePackage} — first-wins",
                        )
                    }
                    else
                    {
                        colorStateListsMut[e.name] = e.rawXml
                    }
                }
                is ParsedNsEntry.AnimatorXml ->
                {
                    val typeMap = byTypeMut.getOrPut(ResourceType.ANIMATOR) { mutableMapOf() }
                    if (!typeMap.containsKey(e.name))
                    {
                        val ref = ResourceReference(ns, ResourceType.ANIMATOR, e.name)
                        typeMap[e.name] = ResourceValueImpl(
                            ref,
                            AppLibraryResourceConstants.ANIMATOR_PLACEHOLDER_VALUE,
                            null,
                        )
                    }
                    if (animatorsMut.containsKey(e.name))
                    {
                        System.err.println(
                            "[LayoutlibResourceBundle] dup animator-xml '${e.name}' ns=${ns.packageName ?: "RES_AUTO"} from ${e.sourcePackage} — first-wins",
                        )
                    }
                    else
                    {
                        animatorsMut[e.name] = e.rawXml
                    }
                }
                is ParsedNsEntry.DrawableXml ->
                {
                    val typeMap = byTypeMut.getOrPut(ResourceType.DRAWABLE) { mutableMapOf() }
                    if (!typeMap.containsKey(e.name))
                    {
                        val ref = ResourceReference(ns, ResourceType.DRAWABLE, e.name)
                        typeMap[e.name] = ResourceValueImpl(
                            ref,
                            AppLibraryResourceConstants.drawablePlaceholderValue(e.name),
                            null,
                        )
                    }
                    if (drawablesMut.containsKey(e.name))
                    {
                        System.err.println(
                            "[LayoutlibResourceBundle] dup drawable-xml '${e.name}' ns=${ns.packageName ?: "RES_AUTO"} from ${e.sourcePackage} — first-wins",
                        )
                    }
                    else
                    {
                        drawablesMut[e.name] = e.rawXml
                    }
                }
                is ParsedNsEntry.InterpolatorXml ->
                {
                    val typeMap = byTypeMut.getOrPut(ResourceType.INTERPOLATOR) { mutableMapOf() }
                    if (!typeMap.containsKey(e.name))
                    {
                        val ref = ResourceReference(ns, ResourceType.INTERPOLATOR, e.name)
                        typeMap[e.name] = ResourceValueImpl(
                            ref,
                            AppLibraryResourceConstants.interpolatorPlaceholderValue(e.name),
                            null,
                        )
                    }
                    if (interpolatorsMut.containsKey(e.name))
                    {
                        System.err.println(
                            "[LayoutlibResourceBundle] dup interpolator-xml '${e.name}' ns=${ns.packageName ?: "RES_AUTO"} from ${e.sourcePackage} — first-wins",
                        )
                    }
                    else
                    {
                        interpolatorsMut[e.name] = e.rawXml
                    }
                }
            }

            val allStyleNames: Set<String> = styleDefs.mapTo(HashSet()) { it.name }
            for (def in styleDefs)
            {
                val candidate = StyleParentInference.infer(def.name, def.parent)
                // Preserve the parent name verbatim — the cross-namespace
                // chain walk handles the namespace-agnostic fallback. The candidate
                // is kept whether or not it appears in this bundle's style set;
                // only a null candidate stays null.
                @Suppress("UNUSED_VARIABLE")
                val inSet = candidate != null && candidate in allStyleNames
                val parentName = candidate
                val ref = ResourceReference(ns, ResourceType.STYLE, def.name)
                val sv = StyleResourceValueImpl(ref, parentName, null)
                for (it2 in def.items)
                {
                    sv.addItem(StyleItemResourceValueImpl(ns, it2.name, it2.value, null))
                }
                if (stylesMut.containsKey(def.name))
                {
                    System.err.println(
                        "[LayoutlibResourceBundle] dup style '${def.name}' ns=${ns.packageName ?: "RES_AUTO"} from ${def.sourcePackage} — adopting later"
                    )
                }
                stylesMut[def.name] = sv
            }

            return NsBucket(
                byType = byTypeMut.mapValues { it.value.toMap() },
                styles = stylesMut.toMap(),
                attrs = attrsMut.toMap(),
                colorStateLists = colorStateListsMut.toMap(),
                animators = animatorsMut.toMap(),
                drawables = drawablesMut.toMap(),
                interpolators = interpolatorsMut.toMap(),
            )
        }
    }
}
