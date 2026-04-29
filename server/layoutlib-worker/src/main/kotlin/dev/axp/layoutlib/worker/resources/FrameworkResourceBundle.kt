package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValue
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
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): ParsedEntry 집합을 layoutlib-api 의 ResourceValue 로
 * 변환하여 집계한 immutable 번들.
 *
 * scope: framework (ResourceNamespace.ANDROID) 만. library/app 은 별도.
 */
class FrameworkResourceBundle private constructor(
    private val byType: Map<ResourceType, Map<String, ResourceValue>>,
    private val styles: Map<String, StyleResourceValue>,
    private val attrs: Map<String, AttrResourceValue>,
) {

    fun getResource(type: ResourceType, name: String): ResourceValue? =
        byType[type]?.get(name)

    fun getStyle(name: String): StyleResourceValue? = styles[name]

    fun getAttr(name: String): AttrResourceValue? = attrs[name]

    /** 진단용 — 테스트에서만 사용 권장. */
    fun typeCount(type: ResourceType): Int = byType[type]?.size ?: 0
    fun styleCount(): Int = styles.size
    fun attrCount(): Int = attrs.size

    companion object {
        /**
         * @param entries 모든 파일의 ParsedEntry 를 평탄화하여 전달.
         *
         * Dedupe 정책:
         *   - SimpleValue / StyleDef: later-wins (themes_material 이 themes 를 오버라이드하는 실 Android 동작).
         *   - AttrDef: first-wins (W3D1 pair-review F1) — top-level attr 가 declare-styleable nested 보다 우선.
         *
         * Style parent post-process: StyleDef 의 explicit parent 또는 inference 결과가
         * 집합 내에 존재하지 않으면 parentStyleName 을 null 로 세팅 (chain 끊김).
         */
        fun build(entries: List<ParsedEntry>): FrameworkResourceBundle {
            val byTypeMut = mutableMapOf<ResourceType, MutableMap<String, ResourceValue>>()
            val stylesMut = mutableMapOf<String, StyleResourceValue>()
            val attrsMut = mutableMapOf<String, AttrResourceValue>()

            val styleDefs = mutableListOf<ParsedEntry.StyleDef>()

            for (e in entries) when (e) {
                is ParsedEntry.SimpleValue -> {
                    val ref = ResourceReference(ResourceNamespace.ANDROID, e.type, e.name)
                    val rv = ResourceValueImpl(ref, e.value, null)
                    byTypeMut.getOrPut(e.type) { mutableMapOf() }[e.name] = rv
                }
                is ParsedEntry.AttrDef -> {
                    // first-wins: 이미 등록된 name 은 덮어쓰지 않는다.
                    if (!attrsMut.containsKey(e.name)) {
                        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, e.name)
                        attrsMut[e.name] = AttrResourceValueImpl(ref, null)
                    }
                }
                is ParsedEntry.StyleDef -> styleDefs += e
            }

            // Style pass: inference 로 parent 이름 결정 후 존재 여부 판단.
            val allStyleNames: Set<String> = styleDefs.mapTo(HashSet()) { it.name }
            for (def in styleDefs) {
                val candidate = StyleParentInference.infer(def.name, def.parent)
                val parentName = if (candidate != null && candidate in allStyleNames) candidate else null
                val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, def.name)
                val sv = StyleResourceValueImpl(ref, parentName, null)
                for (it2 in def.items) {
                    sv.addItem(
                        StyleItemResourceValueImpl(ResourceNamespace.ANDROID, it2.name, it2.value, null)
                    )
                }
                stylesMut[def.name] = sv
            }

            return FrameworkResourceBundle(byTypeMut, stylesMut, attrsMut)
        }
    }
}
