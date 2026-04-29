package dev.axp.layoutlib.worker.classloader

import com.android.resources.ResourceType

/**
 * R$<simpleName> 의 simpleName → ResourceType 매핑.
 * 매핑 누락 시 null → 호출자가 그 R$* 클래스 전체 skip.
 */
internal object RJarTypeMapping {

    private val MAPPING: Map<String, ResourceType> = mapOf(
        "anim" to ResourceType.ANIM,
        "animator" to ResourceType.ANIMATOR,
        "array" to ResourceType.ARRAY,
        "attr" to ResourceType.ATTR,
        "bool" to ResourceType.BOOL,
        "color" to ResourceType.COLOR,
        "dimen" to ResourceType.DIMEN,
        "drawable" to ResourceType.DRAWABLE,
        "font" to ResourceType.FONT,
        "fraction" to ResourceType.FRACTION,
        "id" to ResourceType.ID,
        "integer" to ResourceType.INTEGER,
        "interpolator" to ResourceType.INTERPOLATOR,
        "layout" to ResourceType.LAYOUT,
        "menu" to ResourceType.MENU,
        "mipmap" to ResourceType.MIPMAP,
        "navigation" to ResourceType.NAVIGATION,
        "plurals" to ResourceType.PLURALS,
        "raw" to ResourceType.RAW,
        "string" to ResourceType.STRING,
        "style" to ResourceType.STYLE,
        "styleable" to ResourceType.STYLEABLE,
        "transition" to ResourceType.TRANSITION,
        "xml" to ResourceType.XML,
    )

    fun fromSimpleName(simpleName: String): ResourceType? = MAPPING[simpleName]
}
