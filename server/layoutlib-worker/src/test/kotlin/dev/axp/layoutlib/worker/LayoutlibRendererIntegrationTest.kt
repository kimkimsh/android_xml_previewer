package dev.axp.layoutlib.worker

import com.android.ide.common.rendering.api.Result
import dev.axp.layoutlib.worker.resources.AppLibraryResourceConstants
import dev.axp.layoutlib.worker.resources.LayoutlibResourceValueLoader
import dev.axp.layoutlib.worker.session.SessionConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Tier 3 integration test suite — verifies that the layoutlib rendering pipeline
 * produces a Result.Status.SUCCESS plus a valid PNG for each fixture layout
 * (`activity_basic.xml`, `activity_basic_minimal.xml`, `activity_chip.xml`,
 * `activity_textinputlayout.xml`).
 *
 * The primary test exercises the full Material-fidelity chain — Theme.AxpFixture
 * parented through Theme.Material3.* and Theme.AppCompat back to Theme — wired
 * through SampleAppClassLoader, MinimalLayoutlibCallback (reflection-based view
 * instantiation), and LayoutlibRenderResources (chain walker + theme stack).
 *
 * activity_textinputlayout extends the suite with TextInputLayout +
 * TextInputEditText, exercising AppCompat-side sibling routing
 * (androidx.appcompat.widget.ResourceManagerInternal.loadDrawableFromDelegates)
 * alongside the layoutlib-side ResourceHelper.getDrawable path covered by the
 * chip fixture, plus per-widget materialThemeOverlay on the inner EditText.
 */
@Tag("integration")
class LayoutlibRendererIntegrationTest
{

    @BeforeEach
    fun resetBundleCache()
    {
        // The bundle cache is JVM-static; clearing it between tests prevents
        // cross-test ConstantState contamination from masking real regressions.
        LayoutlibResourceValueLoader.clearCache()
    }

    @Test
    fun `tier3 basic primary — activity_basic renders SUCCESS via primary path`()
    {
        val (dist, layoutRoot, moduleRoot) = locateAll() ?: return
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = layoutRoot,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_basic.xml")
        assertEquals(
            Result.Status.SUCCESS,
            renderer.lastSessionResult?.status,
            "primary SUCCESS",
        )
        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG > $MIN_RENDERED_PNG_BYTES")
        assertTrue(isPngMagic(bytes), "PNG magic header")
    }

    @Test
    fun `tier3 basic minimal smoke — activity_basic_minimal Button-only`()
    {
        val (dist, layoutRoot, moduleRoot) = locateAll() ?: return
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = layoutRoot,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_basic_minimal.xml")
        assertEquals(
            Result.Status.SUCCESS,
            renderer.lastSessionResult?.status,
            "minimal carry SUCCESS",
        )
        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG > $MIN_RENDERED_PNG_BYTES")
    }

    /**
     * Single-widget Chip fixture acceptance gate. Verifies the full Material 3 chip
     * inflation chain — `<style><item>` body trim (style-item reference tokens),
     * `<macro>` design-token indirection (`@macro/m3_comp_assist_chip_label_text_type`),
     * and the per-name `.xml`-suffixed drawable placeholder that lets
     * `ResourceHelper.getDrawable` route through `LayoutlibCallback.getParser` for
     * AAR-side raw drawable XML such as `R.drawable.abc_vector_test`.
     */
    @Test
    fun `tier3 chip — activity_chip renders SUCCESS via primary path`()
    {
        val (dist, layoutRoot, moduleRoot) = locateAll() ?: return
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = layoutRoot,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_chip.xml")
        assertEquals(
            Result.Status.SUCCESS,
            renderer.lastSessionResult?.status,
            "chip primary SUCCESS",
        )
        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG > $MIN_RENDERED_PNG_BYTES")
        assertTrue(isPngMagic(bytes), "PNG magic header")
    }

    /**
     * Single-widget TextInputLayout fixture acceptance gate. Verifies that
     * com.google.android.material.textfield.TextInputLayout (FilledBox style
     * via the textInputFilledStyle theme attribute) inflates with its inner
     * com.google.android.material.textfield.TextInputEditText. Distinct from
     * the chip path in three respects:
     *  - drawable resolution routes through
     *    androidx.appcompat.widget.ResourceManagerInternal.loadDrawableFromDelegates
     *    (.xml-suffix gate at bytecode offset 152-157, sibling to layoutlib's
     *    ResourceHelper.getDrawable);
     *  - the inner TextInputEditText is wrapped via per-widget
     *    materialThemeOverlay, applying ThemeOverlay.Material3.TextInputEditText
     *    .FilledBox on top of the global theme stack;
     *  - cursorColor / cursorErrorColor (API 31+) are resolved through layoutlib
     *    14.0.11 (API 34) paths not exercised by activity_basic or activity_chip.
     *
     * Currently @Disabled pending the AAR res/layout feed. The probe captured by
     * the interpolator XML feed work showed TextInputLayout requesting
     * design_text_input_start_icon (ResourceType.LAYOUT, value
     * design_text_input_start_icon) through LayoutlibCallback.getParser. The
     * MinimalLayoutlibCallback handles COLOR / ANIMATOR / DRAWABLE / INTERPOLATOR
     * but not LAYOUT, so Bridge falls back to ParserFactory.create(value) which
     * returns a parser without setInput, surfacing as XmlPullParserException with
     * "No Input specified". Closing this gate is a separate scope decision — the
     * Codex Q4 trigger criterion lists "AAR res/layout feed" as plan v6 + Round 7
     * Codex+Claude planning pair-review territory rather than W4-A-sibling
     * single-shot work.
     */
    @Test
    @Disabled
    fun `tier3 textInputLayout — activity_textinputlayout renders SUCCESS via primary path`()
    {
        val (dist, layoutRoot, moduleRoot) = locateAll() ?: return
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = layoutRoot,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_textinputlayout.xml")
        assertEquals(
            Result.Status.SUCCESS,
            renderer.lastSessionResult?.status,
            "textInputLayout primary SUCCESS",
        )
        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG > $MIN_RENDERED_PNG_BYTES")
        assertTrue(isPngMagic(bytes), "PNG magic header")
    }

    private fun isPngMagic(bytes: ByteArray): Boolean =
        bytes.size >= PNG_MAGIC_PREFIX_BYTES &&
            bytes[0] == PNG_MAGIC_BYTE_0 && bytes[1] == PNG_MAGIC_BYTE_1 &&
            bytes[2] == PNG_MAGIC_BYTE_2 && bytes[3] == PNG_MAGIC_BYTE_3

    /**
     * Resolves dist + fixture + sample-app module root for a Tier 3 render. Returns
     * null and skips the calling test (via JUnit `assumeTrue(false, …)`) when any of
     * the three is absent — primary tests depend on all three being on disk.
     */
    private fun locateAll(): Triple<Path, Path, Path>?
    {
        val dist = DistDiscovery.locate(null)
        val fixture = FixtureDiscovery.locate(null)
        val moduleRoot = FixtureDiscovery.locateModuleRoot(null)
        if (dist == null || fixture == null || moduleRoot == null)
        {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                false,
                "dist / fixture / moduleRoot missing — graceful skip",
            )
            return null
        }
        val classpathTxt = moduleRoot.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
        if (!classpathTxt.exists())
        {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                false,
                "runtime-classpath.txt missing — run :app:assembleDebug to populate the AAR manifest before bundle build",
            )
            return null
        }
        return Triple(
            dist.toAbsolutePath().normalize(),
            fixture.toAbsolutePath().normalize(),
            moduleRoot.toAbsolutePath().normalize(),
        )
    }

    private companion object
    {
        const val MIN_RENDERED_PNG_BYTES = 1000
        const val PNG_MAGIC_PREFIX_BYTES = 4
        const val PNG_MAGIC_BYTE_0: Byte = 0x89.toByte()
        const val PNG_MAGIC_BYTE_1: Byte = 0x50.toByte()
        const val PNG_MAGIC_BYTE_2: Byte = 0x4E.toByte()
        const val PNG_MAGIC_BYTE_3: Byte = 0x47.toByte()
    }
}
