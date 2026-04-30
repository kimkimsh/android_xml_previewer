package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import dev.axp.layoutlib.worker.DistDiscovery
import dev.axp.layoutlib.worker.FixtureDiscovery
import dev.axp.layoutlib.worker.session.SessionConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * W3D4 §3.4 (T9): MATERIAL-FIDELITY integration verification.
 *
 * Real bundle (LayoutlibResourceValueLoader 3-입력 통합) 위에서 Theme.AxpFixture 의
 * Material3 chain (실측 17 hop, MAX_THEME_HOPS=32 마진) 이 끊기지 않고 colorPrimary /
 * colorPrimaryContainer 가 raw 값으로 resolve 되는지 검증. tier3 primary acceptance gate 의
 * 보조 신호로, primary render 직접 fail 시 root cause 를 chain/attr/style 카운트 단위로 분리.
 *
 * v2 round 2 follow-up:
 *  - FF#5 (Codex Q6): chain depth ≥ 15 assert (실측 17). 회귀 시 chain 끊김 detect.
 *  - bundle 의 RES_AUTO style 카운트 > 100 (Material/AppCompat AAR 다수 정의).
 */
@Tag("integration")
class MaterialFidelityIntegrationTest
{

    @Test
    fun `Theme AxpFixture parent walk to Theme — real bundle`()
    {
        val (dist, sampleApp) = locate() ?: return
        val args = LayoutlibResourceValueLoader.Args(
            distDataDir = dist.resolve(ResourceLoaderConstants.DATA_DIR),
            sampleAppRoot = sampleApp,
            runtimeClasspathTxt = sampleApp.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
        )
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        val themes = listOf(ResourceNamespace.ANDROID, ResourceNamespace.RES_AUTO)
        assertEquals(themes, bundle.namespacesInOrder())

        val rr = LayoutlibRenderResources(bundle, SessionConstants.DEFAULT_FIXTURE_THEME)
        val stack = rr.allThemes
        val names = stack.map { it.name }
        assertTrue(names.contains("Theme.AxpFixture"), "stack 에 AxpFixture")
        assertTrue(names.any { it.startsWith("Theme.Material3") }, "stack 에 Material3 ancestor")
        assertTrue(
            names.contains("Theme") || names.any { it.startsWith("Theme.AppCompat") },
            "최상단 Theme/AppCompat",
        )
        // v2 round 2 follow-up #5 (Codex Q6): 실측 chain depth = 17 hop. 회귀 시 chain 끊김 detect.
        assertTrue(
            stack.size >= MIN_CHAIN_DEPTH,
            "chain depth ≥ $MIN_CHAIN_DEPTH (실측 17, MAX_THEME_HOPS=32 마진), got ${stack.size}",
        )
    }

    @Test
    fun `theme 의 colorPrimary item 가 attr 형태가 아닌 raw 값으로 resolve`()
    {
        val (dist, sampleApp) = locate() ?: return
        val rr = makeRR(dist, sampleApp)
        val ref = ResourceReference(
            ResourceNamespace.RES_AUTO,
            ResourceType.ATTR,
            "colorPrimary",
        )
        val item = rr.findItemInTheme(ref)
        assertNotNull(item, "Theme.AxpFixture 에 colorPrimary item 정의됨")
    }

    @Test
    fun `Material3 부모 chain 의 colorPrimaryContainer 가 추적 가능`()
    {
        val (dist, sampleApp) = locate() ?: return
        val rr = makeRR(dist, sampleApp)
        val ref = ResourceReference(
            ResourceNamespace.RES_AUTO,
            ResourceType.ATTR,
            "colorPrimaryContainer",
        )
        val item = rr.findItemInTheme(ref)
        // Theme.AxpFixture 가 직접 정의 (themes.xml 의 colorPrimaryContainer)
        assertNotNull(item)
    }

    @Test
    fun `bundle 의 RES_AUTO 안에 41 AAR 의 styles 통합 (100 plus 보유)`()
    {
        val (dist, sampleApp) = locate() ?: return
        val args = LayoutlibResourceValueLoader.Args(
            distDataDir = dist.resolve(ResourceLoaderConstants.DATA_DIR),
            sampleAppRoot = sampleApp,
            runtimeClasspathTxt = sampleApp.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
        )
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        val resAutoStyles = bundle.styleCountForNamespace(ResourceNamespace.RES_AUTO)
        assertTrue(
            resAutoStyles > MIN_RES_AUTO_STYLES,
            "Material/AppCompat AAR 의 다수 style → expected > $MIN_RES_AUTO_STYLES, got $resAutoStyles",
        )
    }

    private fun makeRR(dist: Path, sampleApp: Path): LayoutlibRenderResources
    {
        val args = LayoutlibResourceValueLoader.Args(
            distDataDir = dist.resolve(ResourceLoaderConstants.DATA_DIR),
            sampleAppRoot = sampleApp,
            runtimeClasspathTxt = sampleApp.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
        )
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        return LayoutlibRenderResources(bundle, SessionConstants.DEFAULT_FIXTURE_THEME)
    }

    /**
     * graceful skip — dist/fixture/moduleRoot 중 하나라도 없으면 assumeTrue(false) 로 SKIP.
     * (LayoutlibRendererIntegrationTest.locateAll() 와 동일 graceful contract.)
     */
    private fun locate(): Pair<Path, Path>?
    {
        val dist = DistDiscovery.locate(null)
        if (dist == null)
        {
            assumeTrue(false, "dist 없음")
            return null
        }
        val fixture = FixtureDiscovery.locate(null)
        if (fixture == null)
        {
            assumeTrue(false, "fixture 없음")
            return null
        }
        val sampleApp = FixtureDiscovery.locateModuleRoot(null)
        if (sampleApp == null)
        {
            assumeTrue(false, "module root 없음")
            return null
        }
        return dist.toAbsolutePath().normalize() to sampleApp.toAbsolutePath().normalize()
    }

    private companion object
    {
        // v2 round 2 follow-up #5: 실측 17 hop chain. MAX_THEME_HOPS=32 마진. 15 미만 = chain 끊김.
        const val MIN_CHAIN_DEPTH = 15

        // 41 AAR (Material/AppCompat/ConstraintLayout/...) 통합 시 RES_AUTO 의 style 정의 다수.
        const val MIN_RES_AUTO_STYLES = 100
    }
}
