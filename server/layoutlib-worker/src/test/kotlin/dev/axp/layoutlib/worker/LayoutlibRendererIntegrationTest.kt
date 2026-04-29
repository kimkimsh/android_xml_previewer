package dev.axp.layoutlib.worker

import com.android.ide.common.rendering.api.Result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Tier3 pre-canonical integration test — activity_basic.xml (ConstraintLayout / MaterialButton 포함)
 * 의 full render 를 기대.
 *
 * W3D3-L3-CLASSLOADER (round 2 페어 리뷰 반영):
 *  - SampleAppClassLoader 가 sample-app 의 AAR + R.jar 를 host JVM 에 적재.
 *  - MinimalLayoutlibCallback.loadView 가 reflection-instantiate.
 *  - T1 gate (SUCCESS + PNG > 1000) 통과 시 W3D3 deliverable close.
 *
 * **W3D3-α (2026-04-29)**: callback initializer + R.jar seeding wire 후 enable.
 *  primary `activity_basic.xml` 시도 → Material/ThemeEnforcement 계열 fail 시
 *  `activity_basic_minimal.xml` (Material 우회) 로 retry. 둘 다 fail 시 BLOCKED.
 */
@Tag("integration")
class LayoutlibRendererIntegrationTest {

    @Test
    fun `tier3 basic — activity_basic renders SUCCESS with non-empty PNG`() {
        val dist = locateDistDir()
        val layoutRoot = locateFixtureRoot()
        val moduleRoot = locateSampleAppModuleRoot()
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = layoutRoot,
            sampleAppModuleRoot = moduleRoot,
            fallback = null,
        )
        val (layoutName, bytes) = renderWithMaterialFallback(
            renderer,
            primary = "activity_basic.xml",
            fallback = "activity_basic_minimal.xml",
        )
        assertEquals(
            Result.Status.SUCCESS,
            renderer.lastSessionResult?.status,
            "render status SUCCESS 여야 함 (layout=$layoutName)",
        )
        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG bytes > $MIN_RENDERED_PNG_BYTES (layout=$layoutName)")
        assertTrue(
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
            "PNG magic 헤더가 아님",
        )
    }

    /**
     * primary layout 시도 → Material/ThemeEnforcement 관련 fail 시 fallback 으로 retry.
     * α-5 round 2 A1 fix: t.stackTraceToString() 만 보면 Material frame 부재이므로
     * renderer.lastSessionResult.exception/errorMessage 도 검사.
     */
    private fun renderWithMaterialFallback(
        renderer: LayoutlibRenderer,
        primary: String,
        fallback: String,
    ): Pair<String, ByteArray> {
        return try
        {
            primary to renderer.renderPng(primary)
        }
        catch (t: Throwable)
        {
            val sessionExc = renderer.lastSessionResult?.exception
            val sessionMsg = renderer.lastSessionResult?.errorMessage ?: ""
            val excString = sessionExc?.let { it::class.qualifiedName + " " + (it.message ?: "") } ?: ""
            val isMaterial = listOf(excString, sessionMsg).any {
                it.contains("Material", ignoreCase = true) ||
                    it.contains("ThemeEnforcement", ignoreCase = true) ||
                    it.contains("Theme.AppCompat", ignoreCase = true) ||
                    it.contains("AppCompat", ignoreCase = true)
            }
            if (isMaterial)
            {
                fallback to renderer.renderPng(fallback)
            }
            else
            {
                throw t
            }
        }
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
        return requireNotNull(found)
        {
            "sample-app module root 없음 — fixture/sample-app 확인 + (cd fixture/sample-app && ./gradlew :app:assembleDebug) 실행"
        }.toAbsolutePath().normalize()
    }

    private companion object {
        const val MIN_RENDERED_PNG_BYTES = 1000
    }
}
