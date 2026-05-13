package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import dev.axp.layoutlib.worker.resources.LayoutlibRenderResources
import dev.axp.layoutlib.worker.resources.LayoutlibResourceBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader

/**
 * Verifies that SessionParamsFactory builds the SessionParams shape layoutlib
 * expects: hardware config, rendering mode, layout parser identity, timeout,
 * default theme, asset repository, project key + SDK levels, locale, decor /
 * RTL flags, font scale, and UI mode. The default theme must be non-null
 * because Bridge rejects an empty RenderResources without one.
 */
class SessionParamsFactoryTest {

    private val sampleXml = """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="match_parent"/>
    """.trimIndent()

    /**
     * Stub RenderResources backed by an empty LayoutlibResourceBundle plus the
     * default framework theme. Suitable for SessionParams field assertions
     * that do not depend on real resource values; integration tests cover the
     * real bundle path.
     */
    private fun emptyFrameworkRenderResources() =
        LayoutlibRenderResources(
            LayoutlibResourceBundle.build(emptyMap()),
            SessionConstants.DEFAULT_FRAMEWORK_THEME,
        )

    private fun buildParams(): SessionParams =
        SessionParamsFactory.build(
            layoutParser = LayoutPullParserAdapter.fromReader(StringReader(sampleXml)),
            callback = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null }),
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
            callback = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null }),
            resources = emptyFrameworkRenderResources(),
        )
        assertSame(parser, params.layoutDescription)
    }

    @Test
    fun `timeout is configured`() {
        assertEquals(SessionConstants.RENDER_TIMEOUT_MS, buildParams().timeout)
    }

    @Test
    fun `default theme is non-null with expected name`() {
        // Bridge rejects empty RenderResources without a default theme. The
        // stub bundle's fallback theme lives in the RES_AUTO namespace; only
        // the name is asserted here because an empty bundle makes the
        // namespace assertion vacuous. Real framework theme namespace
        // resolution is covered by the integration tests.
        val theme = buildParams().resources.defaultTheme
        assertNotNull(theme, "default theme required (Bridge rejects empty RenderResources without one)")
        assertEquals(SessionConstants.DEFAULT_FRAMEWORK_THEME, theme.name)
    }

    @Test
    fun `AssetRepository is attached and non-supported`() {
        val params = buildParams()
        val assets = params.assets
        assertNotNull(assets)
        assertTrue(!assets.isSupported, "NoopAssetRepository signals non-supported mode to Bridge")
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
        // Without setForceNoDecor the rendered output includes the status bar
        // and action bar, which would false-positive a targeted-rect pixel
        // check downstream.
        val field = java.lang.Class.forName("com.android.ide.common.rendering.api.RenderParams")
            .getDeclaredField("mForceNoDecor").apply { isAccessible = true }
        assertTrue(field.getBoolean(buildParams()), "setForceNoDecor() must be applied")
    }

    @Test
    fun `rtl support enabled`() {
        val field = java.lang.Class.forName("com.android.ide.common.rendering.api.RenderParams")
            .getDeclaredField("mSupportsRtl").apply { isAccessible = true }
        assertTrue(field.getBoolean(buildParams()), "setRtlSupport(true) must be applied")
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
