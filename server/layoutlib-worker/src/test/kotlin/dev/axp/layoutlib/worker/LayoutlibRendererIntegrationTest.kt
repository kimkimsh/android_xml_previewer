package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Tier3 pre-canonical integration test — activity_basic.xml (ConstraintLayout / MaterialButton 포함)
 * 의 full render 를 기대.
 *
 * W2D6 시점에는 `assumeTrue(false)` 로 L4 masking + L3 (custom view 미지원) 을 모두 skip
 * 으로 처리했었다. W3D2 cleanup 에서 L4 는 SharedLayoutlibRenderer 로 해결됐으나 L3
 * (MinimalLayoutlibCallback.loadView 가 ConstraintLayout/MaterialButton 을 reject) 는
 * 여전히 남아있어 render 실패가 예측된다. 따라서 `@Disabled` annotation 으로 명시적
 * skip — W3 sample-app unblock (DexClassLoader) 이후 enable 예정.
 *
 * 호출부는 **SharedLayoutlibRenderer** 를 사용하도록 유지해, @Disabled 가 풀렸을 때
 * L4 regression 재발하지 않도록 설계.
 */
@Tag("integration")
@Disabled("W3 sample-app unblock 필요 — L3 DexClassLoader carry (ConstraintLayout / MaterialButton)")
class LayoutlibRendererIntegrationTest {

    @Test
    fun `tier3 — renderPng returns non-empty PNG bytes with PNG magic header`() {
        val dist = locateDistDir()
        val fixture = locateFixtureRoot()
        val moduleRoot = locateSampleAppModuleRoot()
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = fixture,
            sampleAppModuleRoot = moduleRoot,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_basic.xml")

        assertTrue(bytes.size > 1000, "PNG bytes 가 placeholder 보다 큰 실 이미지여야 함: ${bytes.size}")
        // PNG magic: 0x89 0x50 0x4E 0x47
        assertTrue(
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
            "PNG magic 헤더가 아님",
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
