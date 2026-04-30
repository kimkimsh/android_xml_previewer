package dev.axp.layoutlib.worker.classloader

/**
 * W3D4 §7.1 (R-1): R$style 의 underscore name (e.g., `Theme_AxpFixture`) 와
 * XML 의 dot name (e.g., `Theme.AxpFixture`) 매핑. AAPT 가 style name 의 dot 을
 * R class field 에 underscore 로 변환하는 표준 동작.
 *
 * attr/dimen/color 등은 underscore 가 의미 있는 separator → 보존.
 */
internal object RNameCanonicalization
{
    /** R$style 필드명 → XML style name. underscore → dot 일괄 치환. */
    fun styleNameToXml(rFieldName: String): String
    {
        return rFieldName.replace('_', '.')
    }

    /** R$attr 필드명 → XML attr name. 변환 없음 (underscore 보존). */
    fun attrName(rFieldName: String): String
    {
        return rFieldName
    }

    /** R$dimen / R$color / R$bool / 등 — 변환 없음. */
    fun simpleName(rFieldName: String): String
    {
        return rFieldName
    }
}
