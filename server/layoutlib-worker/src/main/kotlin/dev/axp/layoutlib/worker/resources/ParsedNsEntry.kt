package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType

/**
 * W3D4 §3.1 #2: namespace + sourcePackage tagged parsed entry.
 * sourcePackage 는 dedupe diagnostic 출력용 (어느 AAR 에서 왔는지). production resolution 에는 미사용.
 *
 * W3D1 ParsedEntry 와 별개 sealed class — namespace/sourcePackage 가 추가된 W3D4 전용 형태.
 * (W3D1 ParsedEntry: sample-app/framework single-namespace 가정. W3D4 ParsedNsEntry: multi-AAR
 * + sample-app + framework 가 동일 chain 위에서 공존.)
 */
internal sealed class ParsedNsEntry
{
    abstract val namespace: ResourceNamespace
    abstract val sourcePackage: String?  // null = framework / sample-app

    /**
     * `<dimen name="X">4dp</dimen>`, `<color name="X">#fff</color>`, `<integer>`, `<bool>`,
     * `<string>`, `<item type="X" name="Y">Z</item>` 단일-값 엔트리 (namespace tagged).
     */
    data class SimpleValue(
        val type: ResourceType,
        val name: String,
        val value: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    /**
     * `<attr name="X" format="Y" />` 선언 (namespace tagged).
     * (W3D1 AttrDef 와 동일하게 top-level / declare-styleable 자식 모두 수집.)
     */
    data class AttrDef(
        val name: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    /**
     * `<style name="X" parent="Y"> <item name="A">B</item> ... </style>` (namespace tagged).
     *
     * parent 는 파서가 가공하지 않은 원본 문자열 (null 또는 empty 가능). items 는 선언 순서 유지.
     */
    data class StyleDef(
        val name: String,
        val parent: String?,
        val items: List<StyleItem>,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()
    {
        /** `<style>` 내부의 단일 `<item name="...">value</item>`. */
        data class StyleItem(val name: String, val value: String)
    }

    /**
     * W3D4-β T12: `res/color/<name>.xml` 의 색 state list (`<selector>` root).
     * rawXml 는 selector body 전체. MinimalLayoutlibCallback.getParser 가 StringReader
     * 로 wrap 하여 Bridge ResourceHelper.getColorStateList 에 feed.
     */
    data class ColorStateList(
        val name: String,
        val rawXml: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()
}
