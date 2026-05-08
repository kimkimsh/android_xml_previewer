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
 * Tier3 integration test — primary `activity_basic.xml` (ConstraintLayout / MaterialButton 포함)
 * 가 직접 SUCCESS 로 렌더되는지 + minimal carry (`activity_basic_minimal.xml`) 별도 smoke 확인.
 *
 * **W3D4 acceptance gate** (T9): W3D4 Material-fidelity 의 의도 (Theme.AxpFixture +
 * Material3 chain + colorPrimary resolve → MaterialButton 정상 inflate) 가 작동해야
 * primary test 가 PASS. round 2 ξ 결정으로 W3D3-α 의 `renderWithMaterialFallback` helper
 * (Material/ThemeEnforcement fail 시 minimal 으로 retry) 는 폐기 — primary 는 직접 SUCCESS 강제.
 *
 *  - SampleAppClassLoader 가 sample-app 의 AAR + R.jar 를 host JVM 에 적재.
 *  - MinimalLayoutlibCallback.loadView 가 reflection-instantiate.
 *  - LayoutlibResourceValueLoader (3-입력 통합) + LayoutlibRenderResources (Q3 σ FULL chain walker)
 *    가 wire 되어 Theme.AxpFixture → Theme.Material3.* → Theme.AppCompat → Theme parent walk 작동.
 */
@Tag("integration")
class LayoutlibRendererIntegrationTest
{

    @BeforeEach
    fun resetBundleCache()
    {
        // W3D4-β T13 (round 3 reconcile, Claude Q6.3): JVM-wide bundle cache 가 stale 면
        // T11/T12 효과 측정 무효화 가능. 각 test 시작 시 명시 clear.
        LayoutlibResourceValueLoader.clearCache()
    }

    @Test
    fun `tier3 basic primary — activity_basic 가 직접 SUCCESS`()
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
        assertTrue(isPngMagic(bytes), "PNG magic 헤더")
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
     * Single-widget Chip fixture acceptance gate. Disabled until the remaining
     * TextAppearance layer is wired:
     *
     *   ChipDrawable.loadFromAttributes calls
     *   MaterialResources.getTextAppearance(context, typedArray, index), which
     *   returns null when typedArray.hasValue(index) is false or
     *   typedArray.getResourceId(index, 0) yields 0. The ?attr/textAppearanceLabelLarge
     *   chain must therefore land on an int resource id that maps back to the
     *   @style/TextAppearance.Material3.LabelLarge style; today the path
     *   surfaces NullPointerException at TextAppearance.getTextSize() because
     *   the resolved styleId is unmapped.
     *
     * The earlier <selector>-root callback bypass on android:stateListAnimator
     * is no longer the blocker — that surface was caused by multi-line whitespace
     * around @animator/m3_chip_state_list_anim in the chip style item, fixed in
     * NamespaceAwareValueParser.handleStyle by trimming style item bodies.
     */
    @Disabled("TextAppearance styleId mapping for ?attr/textAppearanceLabelLarge unresolved — see KDoc")
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
     * v2 round 2 follow-up #4 (Codex Q3 + Claude Q3 FULL convergence DISAGREE):
     * plan v1 placeholder `/* W3D3 의 helper 재활용 */ ...` → explicit body.
     *
     * W3D3 의 기존 3개 helper (locateDistDir / locateFixtureRoot / locateSampleAppModuleRoot)
     * 는 dist/fixture 가 `assumeTrue` graceful 하지만 module root 는 `requireNotNull` 강제 throw
     * 였음. v2 가 module root 도 graceful 으로 통일 (CI 환경에 sample-app 부재 시 SKIP — primary
     * test 가 dist/fixture/module 모두 의존).
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
                "dist/fixture/moduleRoot 부재 — W3D3 helper 와 동일 graceful skip",
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
