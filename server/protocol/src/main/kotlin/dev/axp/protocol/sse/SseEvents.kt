package dev.axp.protocol.sse

import dev.axp.protocol.mcp.ErrorEnvelope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 07 §4.3 SSE 10-event taxonomy.
 *
 * canonical 배정 (08 §1.2):
 *   - Week 2 Day 10 : minimal SSE (render_complete 만)
 *   - Week 4 D16-17 : full taxonomy + state machine + Last-Event-ID replay
 *
 * 공통 헤더 (Ktor pipeline 에서 부여):
 *   id: <monotonic u64>
 *   retry: 2000
 *
 * 순서 보장: render_id 별로 queued → started → progress* → (complete | failed | cancelled).
 * 터미널 이벤트는 정확히 1회 방출.
 */

@Serializable
sealed class SseEvent {
    /** 모든 이벤트 공통 — 서버 monotonic 이벤트 id. SSE `id:` 로 전송. */
    abstract val eventId: Long

    @Serializable
    @SerialName("server_ready")
    data class ServerReady(
        override val eventId: Long,
        val aapt2Warm: Boolean,
        val avdState: String,
        val bundledApiLevel: Int,
        val projects: List<String>,
        /** 서버 부팅마다 증가. 불일치 시 클라이언트가 RESYNCING 으로 진입 (07 §4.5). */
        val serverEpoch: Long,
        /** 07 §4.7 CSRF — double-submit 토큰. */
        val csrfToken: String
    ) : SseEvent()

    @Serializable
    @SerialName("render_queued")
    data class RenderQueued(
        override val eventId: Long,
        val renderId: String,
        val layout: String,
        val device: String,
        val theme: String,
        val debounceMsRemaining: Long
    ) : SseEvent()

    @Serializable
    @SerialName("render_started")
    data class RenderStarted(
        override val eventId: Long,
        val renderId: String,
        /** "L1" | "L3" */
        val layerAttempt: String
    ) : SseEvent()

    @Serializable
    @SerialName("render_progress")
    data class RenderProgress(
        override val eventId: Long,
        val renderId: String,
        /** "resource_compile" | "layoutlib_session" | "avd_boot" | "adb_push" ... */
        val stage: String,
        /** 0-100. 모를 경우 null. */
        val pct: Int? = null
    ) : SseEvent()

    @Serializable
    @SerialName("render_complete")
    data class RenderComplete(
        override val eventId: Long,
        val renderId: String,
        val layer: String,
        val elapsedMs: Long,
        val pngUrl: String,
        val cacheHit: Boolean,
        val fallbackReason: String? = null
    ) : SseEvent()

    @Serializable
    @SerialName("render_failed")
    data class RenderFailed(
        override val eventId: Long,
        val renderId: String,
        val error: ErrorEnvelope
    ) : SseEvent()

    @Serializable
    @SerialName("render_cancelled")
    data class RenderCancelled(
        override val eventId: Long,
        val renderId: String,
        val supersededBy: String? = null,
        val reason: String
    ) : SseEvent()

    @Serializable
    @SerialName("project_changed")
    data class ProjectChanged(
        override val eventId: Long,
        val oldRoot: String? = null,
        val newRoot: String,
        val layoutsCount: Int
    ) : SseEvent()

    @Serializable
    @SerialName("resource_invalidated")
    data class ResourceInvalidated(
        override val eventId: Long,
        /** "values" | "drawable" | "merged" | "manifest" 등. */
        val scope: String,
        val affectedLayouts: List<String>? = null,
        val renderIdsCancelled: List<String> = emptyList()
    ) : SseEvent()

    @Serializable
    @SerialName("server_shutdown")
    data class ServerShutdown(
        override val eventId: Long,
        val reason: String,
        val graceMs: Long
    ) : SseEvent()
}

/**
 * 07 §4.5 브라우저 상태머신에서 참조되는 상수.
 */
object SseConstants {
    /** 재연결 replay 용 ring buffer 크기. */
    const val REPLAY_BUFFER_SIZE: Int = 256

    /** heartbeat `event: ping` 주기 (ms). */
    const val HEARTBEAT_INTERVAL_MS: Long = 15_000

    /** keep-alive `:comment` 주기 (ms). */
    const val KEEPALIVE_COMMENT_MS: Long = 5_000

    /** 초기 재연결 간격 (ms). exp-backoff 의 시작. */
    const val RECONNECT_INITIAL_MS: Long = 1_000

    /** 재연결 간격 상한 (ms). */
    const val RECONNECT_MAX_MS: Long = 30_000
}
