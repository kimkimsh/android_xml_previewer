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

    fun getResource(ref: ResourceReference): ResourceValue? =
        byNs[ref.namespace]?.byType?.get(ref.resourceType)?.get(ref.name)

    /** 진단/테스트 전용. */
    fun namespacesInOrder(): List<ResourceNamespace> = byNs.keys.toList()
    fun styleCountForNamespace(ns: ResourceNamespace): Int = byNs[ns]?.styles?.size ?: 0
    fun attrCountForNamespace(ns: ResourceNamespace): Int = byNs[ns]?.attrs?.size ?: 0

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
                        attrsMut[e.name] = AttrResourceValueImpl(ref, null)
                    }
                    // first-wins — 두 번째는 silent (W3D1 정책). 진단 원하면 별도 로그.
                }
                is ParsedNsEntry.StyleDef -> styleDefs += e
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
            )
        }
    }
}
