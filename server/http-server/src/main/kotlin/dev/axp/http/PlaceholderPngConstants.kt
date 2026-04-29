package dev.axp.http

/**
 * Placeholder PNG 렌더링 도메인 상수.
 *
 * Week 1 의 /preview 응답은 layoutlib 실제 렌더 대신 placeholder PNG 를 반환 (W1D5-R4 / 08 §7.6).
 * 실제 렌더는 W2 fatJar (transitive 의존 포함) 후 활성화.
 *
 * 디바이스 프리셋: Pixel-class 360x640 dp ≈ 720x1280 px (xhdpi). v1 에서는 단일 프리셋만.
 */
object PlaceholderPngConstants {
    /** "phone_normal" preset 폭 (px). 360dp x 2 (xhdpi) = 720. v1 단일 프리셋. */
    const val PHONE_NORMAL_WIDTH_PX = 720

    /** "phone_normal" preset 높이 (px). 640dp x 2 = 1280. */
    const val PHONE_NORMAL_HEIGHT_PX = 1280

    /** 배경색 (Material Surface light) — RGB int. */
    const val BG_RGB = 0xFFFAFAFA.toInt()

    /** 테두리 (디바이스 베젤 흉내) — Material Outline. */
    const val BEZEL_RGB = 0xFF424242.toInt()

    /** 본문 텍스트 — Material OnSurface. */
    const val TEXT_RGB = 0xFF1F1F1F.toInt()

    /** 강조색 — Material primary (M3 default purple). */
    const val ACCENT_RGB = 0xFF6750A4.toInt()

    /** 보조 텍스트 — onSurfaceVariant. */
    const val MUTED_RGB = 0xFF6F6F6F.toInt()

    /** 베젤 두께 (px). */
    const val BEZEL_PX = 8

    /** 배지(badge) 영역 높이 (px) — 상단 "L1 render pending" 표시. */
    const val BADGE_HEIGHT_PX = 96

    /** 배지 안쪽 가로 패딩 (px). */
    const val BADGE_PADDING_PX = 24

    /** 타이틀 폰트 크기 (px). */
    const val TITLE_FONT_PX = 36

    /** 본문 폰트 크기 (px). */
    const val BODY_FONT_PX = 24

    /** 메타 정보(파일명, 디바이스) 폰트 크기 (px). */
    const val META_FONT_PX = 20

    /** 배지 → 본문 사이 간격 (px). */
    const val GAP_AFTER_BADGE_PX = 80

    /** 본문 줄간격 (px). */
    const val LINE_HEIGHT_PX = 40

    /** 시스템 폰트 패밀리 (AWT 기본). */
    const val FONT_FAMILY_SANS = "SansSerif"

    /** PNG 출력 포맷 식별자 (ImageIO writer name). */
    const val IMAGE_FORMAT_PNG = "PNG"

    /** AWT headless 시스템 프로퍼티. 서버 환경(no-display)에서 필수. */
    const val HEADLESS_PROPERTY_KEY = "java.awt.headless"
    const val HEADLESS_PROPERTY_VALUE = "true"
}
