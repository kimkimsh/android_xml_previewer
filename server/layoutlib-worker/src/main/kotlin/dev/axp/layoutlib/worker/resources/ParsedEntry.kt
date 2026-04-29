package dev.axp.layoutlib.worker.resources

import com.android.resources.ResourceType

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): 단일 XML 파싱 시 얻어지는 엔트리.
 * sealed class — 각 resource 유형별 최소 필드.
 *
 * parser 는 refs 를 해석하지 않는다 (value 는 문자열 그대로). ref 해석은 layoutlib 의 책임.
 */
sealed class ParsedEntry {

    abstract val name: String

    /**
     * `<dimen name="X">4dp</dimen>`, `<color name="X">#fff</color>`, `<integer>`, `<bool>`,
     * `<string>`, `<item type="X" name="Y">Z</item>` 단일-값 엔트리.
     */
    data class SimpleValue(
        val type: ResourceType,
        override val name: String,
        val value: String,
    ) : ParsedEntry()

    /**
     * `<style name="X" parent="Y"> <item name="A">B</item> ... </style>`
     *
     * parent 는 파서가 가공하지 않은 원본 문자열 (null 또는 empty 가능). items 는 선언 순서 유지.
     */
    data class StyleDef(
        override val name: String,
        val parent: String?,
        val items: List<StyleItem>,
    ) : ParsedEntry()

    /**
     * `<attr name="X" format="Y" />` 선언. top-level 또는 `<declare-styleable>` 자식 모두 수집.
     *
     * **중요** (W3D1 pair-review F1): 실 `data/res/values/attrs.xml` 에는 top-level `<attr>` 이
     * 하나도 없고 모두 `<declare-styleable>` 자식으로 존재. 따라서 parser 는 양쪽 경로 모두에서
     * AttrDef 를 생성해야 한다. 동일 name 이 여러 번 등장하면 **first-wins** (첫 선언 유지).
     * format 이 없는 ref-only 선언 (`<attr name="id" />`) 은 format=null 로 수집.
     */
    data class AttrDef(
        override val name: String,
        val format: String?,
    ) : ParsedEntry()
}

/** `<style>` 내부의 단일 `<item name="...">value</item>`. */
data class StyleItem(
    val name: String,
    val value: String,
)
