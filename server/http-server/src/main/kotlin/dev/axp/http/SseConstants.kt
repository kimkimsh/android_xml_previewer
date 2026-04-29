package dev.axp.http

/**
 * SSE 프레임/이벤트 도메인 상수.
 *
 * 08 §1.2 minimal SSE: Week 2 D10 까지는 `render_complete` 단일 이벤트만 emit.
 * Week 4 D16-17 에서 07 §4.3 의 10-event taxonomy 로 확장.
 *
 * Ktor 2.3.11 에는 `ktor-server-sse` 가 없어 `respondBytesWriter` 로 직접 구현 (handoff §3.2).
 */
object SseConstants {
    /** Week 2 까지 emit 되는 유일한 이벤트. */
    const val EVENT_RENDER_COMPLETE = "render_complete"

    /** SSE 응답 Content-Type. */
    const val CONTENT_TYPE_EVENT_STREAM = "text/event-stream"

    /** SSE 캐싱 금지 (브라우저가 재전송 받지 못하게). */
    const val CACHE_CONTROL_NO_CACHE = "no-cache"

    /** 연결 유지 헤더. */
    const val CONNECTION_KEEP_ALIVE = "keep-alive"

    /** Nginx 등 reverse proxy 가 SSE 응답을 buffering 하지 않도록 hint. */
    const val PROXY_BUFFER_OFF_HEADER = "X-Accel-Buffering"
    const val PROXY_BUFFER_OFF_VALUE = "no"

    /** SSE event field separator (id/data/event 각 라인). */
    const val FIELD_LINE_SEPARATOR = "\n"

    /** SSE event terminator (frame 끝 — RFC 6.2 빈 라인). */
    const val EVENT_FRAME_TERMINATOR = "\n\n"

    /** SSE event field 이름들. */
    const val FIELD_ID = "id"
    const val FIELD_EVENT = "event"
    const val FIELD_DATA = "data"

    /** SSE keep-alive 코멘트(line starting with `:`) — 일부 proxy 가 idle close 회피. */
    const val KEEPALIVE_COMMENT_LINE = ":keepalive\n\n"

    /** keep-alive 송출 주기 (ms). */
    const val KEEPALIVE_INTERVAL_MS = 15_000L
}
