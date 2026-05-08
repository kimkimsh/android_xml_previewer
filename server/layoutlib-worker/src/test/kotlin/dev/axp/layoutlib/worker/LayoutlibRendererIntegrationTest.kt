package dev.axp.layoutlib.worker

import com.android.ide.common.rendering.api.Result
import dev.axp.layoutlib.worker.resources.AppLibraryResourceConstants
import dev.axp.layoutlib.worker.resources.LayoutlibResourceValueLoader
import dev.axp.layoutlib.worker.session.SessionConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Tier 3 integration test suite — verifies that the layoutlib rendering pipeline
 * produces a Result.Status.SUCCESS plus a valid PNG for each fixture layout
 * (`activity_basic.xml`, `activity_basic_minimal.xml`, `activity_chip.xml`).
 *
 * The primary test exercises the full Material-fidelity chain — Theme.AxpFixture
 * parented through Theme.Material3.* and Theme.AppCompat back to Theme — wired
 * through SampleAppClassLoader, MinimalLayoutlibCallback (reflection-based view
 * instantiation), and LayoutlibRenderResources (chain walker + theme stack).
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
