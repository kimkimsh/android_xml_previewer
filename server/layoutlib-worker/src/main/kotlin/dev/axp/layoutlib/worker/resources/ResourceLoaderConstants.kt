package dev.axp.layoutlib.worker.resources

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2 3b-values): data/res/values 경로 및 파일명 상수.
 *
 * CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" 정책 준수 — 10 XML 파일명 전부 명시.
 */
object ResourceLoaderConstants {
    /**
     * layoutlib dist 의 data 디렉토리 이름 (W3D1 impl-pair-review MF3 / Codex N2).
     * 실 구조: `android-34/data/`.
     */
    const val DATA_DIR = "data"

    /**
     * `data/` 디렉토리 하위 values 폴더 상대경로 (qualifier 없는 base).
     * 실 layoutlib-dist 구조: `android-34/data/res/values/` 내의 XML 파일들.
     */
    const val VALUES_DIR = "res/values"

    // 파싱 대상 10 XML. 순서는 parent chain safety 를 위해 base → material 순.
    const val FILE_CONFIG = "config.xml"
    const val FILE_COLORS = "colors.xml"
    const val FILE_DIMENS = "dimens.xml"
    const val FILE_THEMES = "themes.xml"
    const val FILE_STYLES = "styles.xml"
    const val FILE_ATTRS = "attrs.xml"
    const val FILE_COLORS_MATERIAL = "colors_material.xml"
    const val FILE_DIMENS_MATERIAL = "dimens_material.xml"
    const val FILE_THEMES_MATERIAL = "themes_material.xml"
    const val FILE_STYLES_MATERIAL = "styles_material.xml"

    /** loader 가 반드시 로드할 파일의 목록 — Loader 내부 iteration 순서. */
    val REQUIRED_FILES: List<String> = listOf(
        FILE_CONFIG, FILE_COLORS, FILE_DIMENS,
        FILE_THEMES, FILE_STYLES, FILE_ATTRS,
        FILE_COLORS_MATERIAL, FILE_DIMENS_MATERIAL,
        FILE_THEMES_MATERIAL, FILE_STYLES_MATERIAL,
    )

    /** required files 개수 (테스트 assertion 에서 1 표준). */
    const val REQUIRED_FILE_COUNT = 10
}
