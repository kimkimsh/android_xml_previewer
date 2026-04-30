package dev.axp.layoutlib.worker

import dev.axp.layoutlib.worker.session.SessionConstants
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

/**
 * W3D2 integration cleanup — SharedLayoutlibRenderer 의 per-JVM-fork 싱글톤 동작 검증.
 *
 * 본 test 는 native lib 를 실제 로드한다 (LayoutlibRenderer 생성). 따라서 @Tag("integration").
 * Gradle `forkEvery(1L)` (`server/layoutlib-worker/build.gradle.kts:60-65`) 덕에 이 test
 * class 는 독립 JVM fork 에서 실행되고, 그 fork 안의 3 @Test 는 순차 실행되며 singleton
 * 상태를 공유한다. 다른 integration test class (Tier3MinimalTest 등) 는 별도 JVM fork
 * 에서 실행되므로 cross-class state leakage 없음.
 */
@Tag("integration")
class SharedLayoutlibRendererIntegrationTest {

    @Test
    fun `첫 getOrCreate 시 LayoutlibRenderer 반환`() {
        val dist = locateDistDir()
        val fixture = locateFixtureRoot()
        val moduleRoot = locateSampleAppModuleRoot()

        val r = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = fixture,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )

        assertNotNull(r)
    }

    @Test
    fun `같은 args 로 재호출 시 동일 인스턴스 반환`() {
        val dist = locateDistDir()
        val fixture = locateFixtureRoot()
        val moduleRoot = locateSampleAppModuleRoot()
        val r1 = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = fixture,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )

        val r2 = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = fixture,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )

        assertSame(r1, r2, "같은 args 는 동일 인스턴스여야 함 (referential equality)")
    }

    @Test
    fun `다른 args 로 호출 시 IllegalStateException`() {
        val dist = locateDistDir()
        val fixture = locateFixtureRoot()
        val moduleRoot = locateSampleAppModuleRoot()
        // bound 상태 확보 — 첫 getOrCreate.
        SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = fixture,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )

        val differentFixture = fixture.resolveSibling("different")

        val ex = assertThrows<IllegalStateException> {
            SharedLayoutlibRenderer.getOrCreate(
                distDir = dist,
                fixtureRoot = differentFixture,
                sampleAppModuleRoot = moduleRoot,
                themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
                fallback = null,
            )
        }
        assertTrue(
            ex.message!!.contains("불일치"),
            "메시지: ${ex.message}",
        )
    }

    private fun locateDistDir(): Path {
        val found = DistDiscovery.locate(null)
        assumeTrue(found != null, "dist 없음 — W1D3-R2 다운로드를 먼저 수행")
        return found!!.toAbsolutePath().normalize()
    }

    private fun locateFixtureRoot(): Path {
        val found = FixtureDiscovery.locate(null)
        assumeTrue(found != null, "fixture 없음 — fixture/sample-app 확인")
        return found!!.toAbsolutePath().normalize()
    }

    private fun locateSampleAppModuleRoot(): Path {
        val found = FixtureDiscovery.locateModuleRoot(null)
        assumeTrue(found != null, "sample-app module root 없음 — fixture/sample-app 확인")
        return found!!.toAbsolutePath().normalize()
    }
}
