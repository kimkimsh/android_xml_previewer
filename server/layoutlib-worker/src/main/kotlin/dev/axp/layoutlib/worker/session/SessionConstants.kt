package dev.axp.layoutlib.worker.session

/**
 * W2D7-RENDERSESSION (08 §7.7.1 item 3b): SessionParams / HardwareConfig / render timeout
 * 등 layoutlib render 경로 전역 상수.
 *
 * CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" 정책 준수 — 모든 리터럴은 본 object
 * 를 참조하여 single source of truth.
 */
object SessionConstants {
    /** phone_normal 폭 (px). 360dp × xhdpi. HardwareConfig.screenWidth 와 일치. */
    const val RENDER_WIDTH_PX = 720

    /** phone_normal 높이 (px). 640dp × xhdpi. */
    const val RENDER_HEIGHT_PX = 1280

    /** xhdpi 의 논리 dpi. HardwareConfig.xdpi / ydpi 양쪽에 사용. */
    const val DPI_XHIGH = 320f

    /**
     * RenderSession.render(timeout) 밀리초. 6_000ms 는 Paparazzi 기본값과 정합.
     * RenderParams.DEFAULT_TIMEOUT 이 10_000ms 이지만, fixture 가 trivial 하므로 더 짧게 설정.
     */
    const val RENDER_TIMEOUT_MS = 6_000L

    /** minSdk — fixture 는 android-34 dist 에서 렌더. */
    const val MIN_SDK = 34

    /** targetSdk — android-34 번들과 맞춤. */
    const val TARGET_SDK = 34

    /** SessionParams.projectKey — render cache / callback context 식별용 상수 토큰. */
    const val PROJECT_KEY = "axp-w2d7-rendersession"

    /** RenderSession.setLocale(locale). 기본 en. */
    const val RENDER_LOCALE = "en"

    /** layoutlib framework package name (resource namespace "android"). */
    const val FRAMEWORK_PACKAGE = "android"

    /** F2 default theme — Theme.Material.Light.NoActionBar (decor 제거됨 + decent TextAppearance 체인). */
    const val DEFAULT_FRAMEWORK_THEME = "Theme.Material.Light.NoActionBar"

    /**
     * W3D4 §3.1 (T8): fixture-app 의 default theme — `Theme.AxpFixture`.
     * sample-app 의 themes.xml 에 정의된 root style. Material3 chain 의 시작점
     * (Theme.AxpFixture → Material3.DayNight.NoActionBar → ... → android:Theme).
     * LayoutlibRenderer ctor 의 themeName 인자로 caller 가 명시 전달.
     */
    const val DEFAULT_FIXTURE_THEME = "Theme.AxpFixture"

    /**
     * UI_MODE_TYPE_NORMAL (0x01) | UI_MODE_NIGHT_NO (0x10) = 0x11 (17).
     * android.content.res.Configuration 의 uiMode 필드 값.
     * FolderConfiguration 매칭 시 base `values/` (qualifier 없음) 리소스 선택 보장.
     */
    const val UI_MODE_NORMAL_DAY = 0x11
}
