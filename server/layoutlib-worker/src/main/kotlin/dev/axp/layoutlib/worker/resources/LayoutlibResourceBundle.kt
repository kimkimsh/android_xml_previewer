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
 * W3D4 §3.1 #6: namespace-aware immutable resource bundle.
 * byNs = LinkedHashMap (ANDROID → RES_AUTO 결정 순회).
 * Mode 통일에 따라 ANDROID + RES_AUTO 2-bucket. duplicate 진단 로그.
 *
 * dedupe 정책 (η round 2):
 *  - SimpleValue / StyleDef: later-wins per namespace + duplicate 진단 1줄.
 *  - AttrDef: first-wins (W3D1 정책 보존, silent).
 *
 * cross-ns parent inference: StyleParentInference (W3D1) 그대로 활용 (namespace 무관).
 * round 2: parent 이름은 보존 — null 만 fallback. ns-agnostic chain walk 은 후속 phase 가 처리.
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
     * W3D4-γ T14 (round 4 Codex Q5 KILL POINT fix): RES_AUTO/ANDROID 의 ATTR ref 는
     * `byType[ATTR]` 가 아닌 `attrs` map 에서 lookup (AttrDef 가 attrs 에만 등록됨 — NsBucket
     * 의 byType ↔ attrs 분리 정합). BridgeTypedArray.resolveEnumAttribute 의 path B 와
     * BridgeXmlPullAttributes.getAttributeIntValue 의 project supplier 가 모두 RenderResources
     * 의 (un)resolvedResource 를 거쳐 본 메서드에 도달 — instanceof AttrResourceValue cast 를
     * 위해 AttrResourceValueImpl 인스턴스 자체가 반환되어야 함.
     */
    fun getResource(ref: ResourceReference): ResourceValue?
    {
        val bucket = byNs[ref.namespace] ?: return null
        if (ref.resourceType == ResourceType.ATTR)
        {
            return bucket.attrs[ref.name]
        }
        return bucket.byType[ref.resourceType]?.get(ref.name)
    }

    /**
     * W3D4-β T12: color state list (`<selector>` XML) 의 raw body lookup.
     * MinimalLayoutlibCallback.getParser 가 Bridge ResourceHelper.getColorStateList 의
     * input feed 단계에서 호출.
     */
    fun getColorStateListXml(ref: ResourceReference): String? =
        byNs[ref.namespace]?.colorStateLists?.get(ref.name)

    /** 진단/테스트 전용. */
    fun namespacesInOrder(): List<ResourceNamespace> = byNs.keys.toList()
    fun styleCountForNamespace(ns: ResourceNamespace): Int = byNs[ns]?.styles?.size ?: 0
    fun attrCountForNamespace(ns: ResourceNamespace): Int = byNs[ns]?.attrs?.size ?: 0
    fun colorStateListCountForNamespace(ns: ResourceNamespace): Int =
        byNs[ns]?.colorStateLists?.size ?: 0

    /**
     * W3D4-γ T15: Bridge.init() 의 enumValueMap 인자용 export — framework (ANDROID) bucket 의
     * 모든 AttrResourceValueImpl 에서 enum/flag 테이블을 추출하여 단일 Map<String, Map<String, Int>>
     * 반환. layoutlib BridgeTypedArray.resolveEnumAttribute 가 ANDROID namespace attr 변환 시
     * Bridge.sEnumValueMap.get(attrName).get("vertical") 형태로 조회 — Bridge.init 의 6번째
     * 인자가 곧 sEnumValueMap.
     *
     * 비어있는 attr (enum/flag 자식 없음) 은 결과 map 에서 제외. RES_AUTO bucket 은 별도 경로
     * (T14 의 getResource ATTR special-case + AttrResourceValueImpl.getAttributeValues) — 본
     * helper 미관여.
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
            // 추가 ns 가 있다면 (W4+ namespace-aware 시) ordering 유지
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
                        // W3D4-γ T14: <enum>/<flag> 자식 값 테이블을 AttrResourceValueImpl 에 주입.
                        // BridgeTypedArray.resolveEnumAttribute 가 RES_AUTO attr 변환 시
                        // getAttributeValues().get("vertical") 등으로 조회. value 는 boxed Integer
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
                    // first-wins — 두 번째 등록은 silent (W3D1 정책 + round 4 empirical: 41 AAR
                    // 안 nonempty-vs-nonempty conflict 0건, 첫 nonempty 가 항상 보존됨).
                }
                is ParsedNsEntry.StyleDef -> styleDefs += e
                is ParsedNsEntry.ColorStateList ->
                {
                    // W3D4-β T12: byType[COLOR] 에 placeholder ResourceValue 등록 →
                    // BridgeContext 의 getResource 단계 통과 보장. value 는 magic placeholder
                    // (callback.getParser 가 가로챔 — Bridge fallback ParserFactory.create 미도달).
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
            }

            val allStyleNames: Set<String> = styleDefs.mapTo(HashSet()) { it.name }
            for (def in styleDefs)
            {
                val candidate = StyleParentInference.infer(def.name, def.parent)
                // round 2: parent 이름은 보존 (cross-ns chain 의 ns-agnostic fallback 이 후속 chain walk 에서 처리).
                // candidate 가 set 안에 있으면 그대로, 없어도 그대로 — null 만 그대로 null.
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
            )
        }
    }
}
