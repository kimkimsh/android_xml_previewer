package dev.axp.layoutlib.worker

import com.android.ide.common.rendering.api.Result
import org.junit.jupiter.api.Assertions.assertEquals
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
 * W3D3-L3-CLASSLOADER (round 2 페어 리뷰 반영):
 *  - SampleAppClassLoader 가 sample-app 의 AAR + R.jar 를 host JVM 에 적재.
 *  - MinimalLayoutlibCallback.loadView 가 reflection-instantiate.
 *  - T1 gate (SUCCESS + PNG > 1000) 통과 시 W3D3 deliverable close.
 *
 * **W3D3 status: BLOCKED (branch (C) — `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md`)**
 *  - layoutlib 14.0.11 JAR 이 `android.os.Build` 자체를 포함하지 않음 (`_Original_Build*` prefix 만 존재).
 *    Studio 외부 환경에서 SHIM 부재 → AAR 의 Build.VERSION.SDK_INT 참조가 ClassNotFoundException.
 *  - 추가로 R.jar 의 real id (e.g., 2130903769) ↔ callback generated id (0x7F0A_xxxx) 불일치
 *    → style resolve 실패 (Codex round 1 B3 confirmed).
 *  - 본 테스트의 acceptance gate, requireNotNull, SUCCESS assertion 은 향후 옵션 α (bytecode rewriting)
 *    또는 옵션 β (Build shim JAR + R.jar id 시드) 가 land 된 후 enable 될 수 있도록 보존.
 */
@Tag("integration")
@Disabled(
    "W3D3 branch (C) — layoutlib 의 android.os.Build 부재 + R.jar id 불일치. " +
        "carry: docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md 참조.",
)
class LayoutlibRendererIntegrationTest {

    @Test
    fun `tier3-basic — activity_basic renders SUCCESS with non-empty PNG`() {
        val dist = locateDistDir()
        val layoutRoot = locateFixtureRoot()
        val moduleRoot = locateSampleAppModuleRoot()
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = layoutRoot,
            sampleAppModuleRoot = moduleRoot,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_basic.xml")

        assertEquals(
            Result.Status.SUCCESS,
            renderer.lastSessionResult?.status,
            "render status SUCCESS 여야 함",
        )
        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG bytes 가 placeholder 보다 큼: ${bytes.size}")
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
        return requireNotNull(found)
        {
            "sample-app module root 없음 — fixture/sample-app 확인 + (cd fixture/sample-app && ./gradlew :app:assembleDebug) 실행"
        }.toAbsolutePath().normalize()
    }

    private companion object {
        const val MIN_RENDERED_PNG_BYTES = 1000
    }
}
