package dev.axp.layoutlib.worker.resources

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): style 의 parent name 을 결정.
 *
 * pure function — bundle 존재 여부는 확인하지 않는다 (Loader post-process 가 담당).
 *
 * 규칙:
 *  1. explicitParent 가 non-null, non-empty → ref form 정규화 (`@style/` / `@android:style/` 제거) 후 반환.
 *  2. explicitParent 가 null 또는 빈 문자열 → styleName 의 마지막 dot 앞 부분 반환 (dotted-prefix 상속).
 *  3. styleName 에 dot 이 없음 (루트) → null.
 */
object StyleParentInference {

    fun infer(styleName: String, explicitParent: String?): String? {
        if (!explicitParent.isNullOrEmpty()) {
            return normalizeRefForm(explicitParent)
        }
        val lastDot = styleName.lastIndexOf('.')
        return if (lastDot > 0) styleName.substring(0, lastDot) else null
    }

    /**
     * `@android:style/Theme.Material`  → `Theme.Material`
     * `@style/Widget.Material`          → `Widget.Material`
     * `Theme.Material`                  → `Theme.Material`  (이미 이름 형태)
     */
    private fun normalizeRefForm(raw: String): String {
        var s = raw
        if (s.startsWith(PREFIX_AT_ANDROID_STYLE)) s = s.removePrefix(PREFIX_AT_ANDROID_STYLE)
        else if (s.startsWith(PREFIX_AT_STYLE)) s = s.removePrefix(PREFIX_AT_STYLE)
        return s
    }

    private const val PREFIX_AT_ANDROID_STYLE = "@android:style/"
    private const val PREFIX_AT_STYLE = "@style/"
}
