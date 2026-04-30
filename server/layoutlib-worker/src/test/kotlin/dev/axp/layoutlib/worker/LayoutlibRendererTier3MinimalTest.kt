package dev.axp.layoutlib.worker

import com.android.ide.common.rendering.api.Result
import dev.axp.layoutlib.worker.session.SessionConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * W2D7-RENDERSESSION — Tier3 "아키텍처 positive evidence" + W3 carry placeholder.
 *
 * **§7.7.1 item 3b 의 canonical split**:
 *   - **3b-arch (W2D7 closure)**: `Bridge.createSession(SessionParams)` 가 실제로 실행되어
 *     layoutlib 의 inflate 단계 (`Layout.<init>` → `FrameLayout.<init>` → `ViewConfiguration.<init>`)
 *     까지 도달한다. 이 경로가 연결되었다는 사실은 SessionParams / HardwareConfig / LayoutPullParser
 *     / LayoutlibCallback 인프라가 올바르게 조립되었다는 positive evidence.
 *   - **3b-values (W3 carry)**: 프레임워크 리소스 VALUE (`config_scrollbarSize` 등 data/res/values
 *     내 XML 의 실제 값) 를 `RenderResources` 에 제공하는 풀 resource parsing (Paparazzi 급 infra ~1000 LOC).
 *     이 작업이 완료되어야 실 pixel 렌더가 가능.
 *
 * 따라서 본 세션의 Tier3 는 **architecture evidence 만** assert. 실 pixel 테스트는 `@Disabled` 로
 * 남겨두고 W3 에서 unblock.
 */
@Tag("integration")
class LayoutlibRendererTier3MinimalTest {

    companion object {
        /**
         * W3D2 cleanup: native lib JVM-wide single-load 이슈 (L4) 는 SharedLayoutlibRenderer
         * 로 해결. 기존 `sharedRenderer` 필드 + synchronized factory 는 그 object 로 이동.
         */
        private fun renderer(): LayoutlibRenderer {
            val dist = DistDiscovery.locate(null)
                ?: run {
                    // CI 환경 변수에 dist 없을 수 있음 — assumeTrue 로 skip.
                    org.junit.jupiter.api.Assumptions.assumeTrue(
                        false, "dist 없음 — W1D3-R2 다운로드를 먼저 수행",
                    )
                    error("unreachable")
                }
            val fixture = FixtureDiscovery.locate(null)
                ?: error("fixture 없음 — fixture/sample-app 확인")
            val moduleRoot = FixtureDiscovery.locateModuleRoot(null)
                ?: error("sample-app module root 없음 — fixture/sample-app 확인")
            return SharedLayoutlibRenderer.getOrCreate(
                distDir = dist.toAbsolutePath().normalize(),
                fixtureRoot = fixture.toAbsolutePath().normalize(),
                sampleAppModuleRoot = moduleRoot.toAbsolutePath().normalize(),
                themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
                fallback = null,
            )
        }
    }

    @Test
    fun `tier3-arch — createSession reaches inflate phase on activity_minimal`() {
        val renderer = renderer()

        // 3b-values 완료: renderPng 은 성공적으로 PNG 반환. 예외는 곧 regression.
        val bytes = renderer.renderPng("activity_minimal.xml")
        assertTrue(bytes.isNotEmpty(), "3b-values: PNG bytes 는 non-empty")

        // ===== architecture positive evidence =====
        val result = renderer.lastSessionResult
        assertNotNull(
            result,
            "createSession 이 호출되어 result 가 채워져야 함 — null 이면 SessionParams 빌드 전 실패."
        )

        // W3D1 impl-pair-review MF1 (Codex F2 + Claude F1 converged):
        // rejectedStatuses 는 SUCCESS-only 와 중복이라 제거. Result.Status 의 8+ 개 non-success enum
        // (NOT_IMPLEMENTED / ERROR_TIMEOUT / ERROR_LOCK_INTERRUPTED / ERROR_VIEWGROUP_NO_CHILDREN /
        //  ERROR_ANIM_NOT_FOUND / ERROR_NOT_A_DRAWABLE / ERROR_REFLECTION / ERROR_RENDER_TASK 등)
        // 을 일일이 나열하기보다 "SUCCESS 만 허용" 한 assertion 으로 모두 거부.
        val status = result!!.status
        val msg = result.errorMessage.orEmpty()
        val exc = result.exception?.javaClass?.simpleName
        assertEquals(
            Result.Status.SUCCESS, status,
            "3b-values 완료 canonical: SUCCESS 만 허용.\n" +
                "  actual=$status msg=$msg exc=$exc\n" +
                "  ERROR_INFLATION    → 3b-values regression (framework VALUE loader 확인).\n" +
                "  ERROR_RENDER       → draw-phase regression (W4 carry 범위 침범).\n" +
                "  ERROR_UNKNOWN      → native/JNI 단계 실패 (native lib wiring 확인).\n" +
                "  ERROR_NOT_INFLATED → SessionParams 빌드 결함 (W2D7 regression).\n"
        )
    }

    /**
     * W3D1 3b-values — T1 gate: SUCCESS + PNG > 1000 bytes. glyph 렌더는 tier3-glyph 로 분리.
     */
    @Test
    fun `tier3-values — activity_minimal 이 SUCCESS + valid PNG 반환`() {
        val renderer = renderer()
        val bytes = renderer.renderPng("activity_minimal.xml")

        assertTrue(bytes.size > 1000, "PNG size > 1000 bytes: actual=${bytes.size}")
        val img = ImageIO.read(ByteArrayInputStream(bytes))
        assertNotNull(img)
        assertEquals(SessionConstants.RENDER_WIDTH_PX, img!!.width)
        assertEquals(SessionConstants.RENDER_HEIGHT_PX, img.height)

        val result = renderer.lastSessionResult
        assertNotNull(result)
        assertEquals(
            Result.Status.SUCCESS, result!!.status,
            "tier3-values T1 gate: SUCCESS 필요. actual=${result.status} msg=${result.errorMessage}"
        )
    }

    /**
     * tier3-glyph (W4+ carry) — 실 글리프 렌더 증명.
     * Font wiring + StaticLayout + Canvas.drawText JNI 전 영역 검증.
     * T1 gate 와 분리 (3b-values 완료는 이 테스트 unblock 의 전제).
     */
    @Test
    @Disabled("tier3-glyph W4 carry — Font wiring + glyph 렌더링 검증 (T2 gate)")
    fun `tier3-glyph — activity_minimal 의 TextView 영역에 실 dark pixel`() {
        val renderer = renderer()
        val bytes = renderer.renderPng("activity_minimal.xml")

        assertTrue(bytes.size >= 10_000, "PNG size >= 10_000: actual=${bytes.size}")
        val img = ImageIO.read(ByteArrayInputStream(bytes))!!
        assertEquals(SessionConstants.RENDER_WIDTH_PX, img.width)
        assertEquals(SessionConstants.RENDER_HEIGHT_PX, img.height)

        val textRectX = 64..600
        val textRectY = 64..200
        var dark = 0
        for (y in textRectY step 2) for (x in textRectX step 2) {
            if (x >= img.width || y >= img.height) continue
            val rgb = img.getRGB(x, y)
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            if (r + g + b < 384) dark++
        }
        assertTrue(dark >= 20, "TextView 영역 dark pixels >= 20: actual=$dark")
    }

}
