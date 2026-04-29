package dev.axp.http

import kotlinx.serialization.Serializable

/**
 * SSE 로 emit 되는 단일 이벤트.
 *
 * Week 2 D10 까지는 `render_complete` 한 종류만 사용 (08 §1.2 minimal SSE).
 * Week 4 D16-17 에서 sealed class + 10-event taxonomy 로 확장 (07 §4.3).
 *
 * `id` 는 SSE Last-Event-ID 재전송 (W4) 의 시드. 단조증가.
 * `renderId` 는 cache buster — 클라이언트가 `<img src=/preview?...&v=$renderId>` 로 강제 새로고침.
 */
@Serializable
data class RenderCompleteEvent(
    val id: Long,
    val renderId: String,
    val layout: String,
    val widthPx: Int,
    val heightPx: Int,
    val durationMs: Long
)
