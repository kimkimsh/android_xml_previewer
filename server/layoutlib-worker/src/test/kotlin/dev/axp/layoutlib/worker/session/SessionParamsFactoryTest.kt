package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import dev.axp.layoutlib.worker.resources.FrameworkRenderResources
import dev.axp.layoutlib.worker.resources.FrameworkResourceBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader

/**
 * W2D7-RENDERSESSION — SessionParamsFactory 가 layoutlib 이 기대하는 SessionParams 를
 * 올바른 필드값으로 생성하는지 검증.
 *
 * CLAUDE.md F2 (페어 리뷰): empty RenderResources 금지 → default theme 필수.
 */
class SessionParamsFactoryTest {

    private val sampleXml = """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="match_parent"/>
    """.trimIndent()

    /**
     * W3D1 3b-values: resources 파라미터 default 제거 → 테스트용 empty bundle + DEFAULT_FRAMEWORK_THEME.
     * 실 값 로딩은 integration (tier3-values) 에서 검증.
     */
    private fun emptyFrameworkRenderResources() =
        FrameworkRenderResources(
            FrameworkResourceBundle.build(emptyList()),
            SessionConstants.DEFAULT_FRAMEWORK_THEME,
        )

    private fun buildParams(): SessionParams =
        SessionParamsFactory.build(
            layoutParser = LayoutPullParserAdapter.fromReader(StringReader(sampleXml)),
            callback = MinimalLayoutlibCallback(),
            resources = emptyFrameworkRenderResources(),
        )

    @Test
    fun `HardwareConfig is phone xhigh portrait`() {
        val hw = buildParams().hardwareConfig
        assertEquals(SessionConstants.RENDER_WIDTH_PX, hw.screenWidth)
        assertEquals(SessionConstants.RENDER_HEIGHT_PX, hw.screenHeight)
        assertEquals(Density.XHIGH, hw.density)
        assertEquals(ScreenOrientation.PORTRAIT, hw.orientation)
    }

    @Test
    fun `rendering mode is NORMAL`() {
        assertEquals(SessionParams.RenderingMode.NORMAL, buildParams().renderingMode)
    }

    @Test
    fun `layout parser is forwarded to SessionParams`() {
        val parser = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
        val params = SessionParamsFactory.build(
            layoutParser = parser,
            callback = MinimalLayoutlibCallback(),
            resources = emptyFrameworkRenderResources(),
        )
        assertSame(parser, params.layoutDescription)
    }

    @Test
    fun `timeout is configured`() {
        assertEquals(SessionConstants.RENDER_TIMEOUT_MS, buildParams().timeout)
    }

    @Test
    fun `default theme is non-null and framework-namespaced`() {
        val theme = buildParams().resources.defaultTheme
        assertNotNull(theme, "F2: empty RenderResources 금지 — default theme 필수")
        assertEquals(ResourceNamespace.ANDROID, theme.namespace)
        assertEquals(SessionConstants.DEFAULT_FRAMEWORK_THEME, theme.name)
    }

    @Test
    fun `AssetRepository is attached and non-supported`() {
        val params = buildParams()
        val assets = params.assets
        assertNotNull(assets)
        assertTrue(!assets.isSupported, "NoopAssetRepository 는 비지원 모드로 Bridge 에 신호")
    }

    @Test
    fun `project key and sdk levels are set`() {
        val params = buildParams()
        assertEquals(SessionConstants.PROJECT_KEY, params.projectKey)
        assertEquals(SessionConstants.MIN_SDK, params.minSdkVersion)
        assertEquals(SessionConstants.TARGET_SDK, params.targetSdkVersion)
    }

    @Test
    fun `locale is configured`() {
        assertEquals(SessionConstants.RENDER_LOCALE, buildParams().locale)
    }

    @Test
    fun `forceNoDecor flag applied`() {
        // 페어 리뷰 (Claude): setForceNoDecor 가 적용되지 않으면 status bar/action bar 가
        // 포함되어 targeted-rect pixel 체크가 false-positive 될 수 있음.
        val field = java.lang.Class.forName("com.android.ide.common.rendering.api.RenderParams")
            .getDeclaredField("mForceNoDecor").apply { isAccessible = true }
        assertTrue(field.getBoolean(buildParams()), "setForceNoDecor() 가 적용되어야 함")
    }

    @Test
    fun `rtl support enabled`() {
        val field = java.lang.Class.forName("com.android.ide.common.rendering.api.RenderParams")
            .getDeclaredField("mSupportsRtl").apply { isAccessible = true }
        assertTrue(field.getBoolean(buildParams()), "setRtlSupport(true) 가 적용되어야 함")
    }

    @Test
    fun `font scale is unity`() {
        assertEquals(1.0f, buildParams().fontScale, 0.001f)
    }

    @Test
    fun `uiMode set to normal day`() {
        // UI_MODE_TYPE_NORMAL (0x01) | UI_MODE_NIGHT_NO (0x10) = 0x11 (17).
        assertEquals(0x11, buildParams().uiMode)
    }
}
