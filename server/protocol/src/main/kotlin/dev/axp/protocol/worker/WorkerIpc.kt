package dev.axp.protocol.worker

import dev.axp.protocol.error.UnrenderableResult
import dev.axp.protocol.render.RenderRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 08 §3.1 canonical — Unix domain socket + JSON line-framed IPC.
 *
 * 프레임 포맷:
 *   [u32 LE length][UTF-8 JSON payload]
 *
 * 선택 근거 (08 §3.1):
 *   - stdin/stdout: framing/backpressure 불편
 *   - gRPC: 과잉 의존성, protobuf 스키마 추가 부담
 *   - Unix socket + JSON line-framed: 단순 + backpressure 자연 + 디버깅 쉬움
 *
 * 소켓 경로: `.axp-cache/workers/worker-<device>.sock`
 *
 * 모든 요청/응답은 kotlinx.serialization 의 polymorphism 으로 직렬화.
 * JSON content: `{"type":"Render","req":{...}}` 형태 (class discriminator = "type").
 */

@Serializable
sealed class WorkerRequest {
    /** 단일 레이아웃 렌더 요청. */
    @Serializable
    @SerialName("Render")
    data class Render(val req: RenderRequest) : WorkerRequest()

    /**
     * 캐시 무효화. resource 변경 또는 프로젝트 전환 시 호출.
     * `projectKey` 는 워커가 관리하는 프로젝트 단위 식별자(07 §2.3).
     */
    @Serializable
    @SerialName("Invalidate")
    data class Invalidate(val projectKey: String) : WorkerRequest()

    /** Graceful shutdown. 워커는 진행 중 작업 취소 후 종료. */
    @Serializable
    @SerialName("Shutdown")
    data object Shutdown : WorkerRequest()

    /** 워커 상태 프로빙 (health check). */
    @Serializable
    @SerialName("Ping")
    data object Ping : WorkerRequest()
}

@Serializable
sealed class WorkerResponse {
    @Serializable
    @SerialName("RenderOk")
    data class RenderOk(
        /** PNG 가 쓰인 경로 (호스트 파일시스템). */
        val pngPath: String,
        val elapsedMs: Long,
        /** 렌더 메트릭 — 세션 재사용, 폰트 워커 ID 등 관측용. */
        val metrics: Map<String, String> = emptyMap()
    ) : WorkerResponse()

    @Serializable
    @SerialName("RenderErr")
    data class RenderErr(val error: WorkerErrorPayload) : WorkerResponse()

    /** Invalidate/Shutdown 에 대한 ACK. */
    @Serializable
    @SerialName("Ack")
    data object Ack : WorkerResponse()

    /** Ping 응답 — 워커 PID + 버전. */
    @Serializable
    @SerialName("Pong")
    data class Pong(val pid: Long, val workerVersion: String) : WorkerResponse()
}

/**
 * 프로토콜 경계에서 UnrenderableResult 는 IPC-safe 하게 풀어서 전달.
 * 07 §4.1 ErrorEnvelope 과는 유사하지만 IPC 계층 전용 형태.
 */
@Serializable
data class WorkerErrorPayload(
    val code: String,
    val category: String,
    val message: String,
    val detail: String? = null,
    val stackTrace: String? = null
) {
    companion object {
        fun from(result: UnrenderableResult): WorkerErrorPayload = WorkerErrorPayload(
            code = result.reason.code,
            category = result.reason.category.name,
            message = result.detail,
            detail = null,
            stackTrace = result.stackTrace
        )
    }
}

/**
 * Frame 헤더 상수 — 양단 합의된 포맷을 한 곳에서 관리.
 */
object WorkerFrame {
    /** length prefix 는 u32 little-endian. */
    const val HEADER_BYTES: Int = 4

    /** 단일 프레임 최대 크기 (2 MiB). PNG 경로 + 메타 정도만 흐르므로 넉넉. */
    const val MAX_FRAME_SIZE: Int = 2 * 1024 * 1024

    /** .axp-cache/workers/ 하위의 소켓 파일명 패턴. */
    fun socketFileName(deviceId: String): String = "worker-$deviceId.sock"
}
