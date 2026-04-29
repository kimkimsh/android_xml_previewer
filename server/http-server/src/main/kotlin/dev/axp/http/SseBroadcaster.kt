package dev.axp.http

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * 메모리 내 SSE broadcaster.
 *
 * 다중 구독자(여러 브라우저 탭)가 같은 이벤트 스트림을 받을 수 있도록 SharedFlow 기반.
 * Week 1 의 단일 이벤트(`render_complete`) 뿐 아니라 후속 이벤트 추가에도 호환.
 *
 * 정책:
 *  - replay = 1 — 새 구독자가 가장 최근 이벤트 1개를 즉시 받음 (브라우저 로딩 직후 PNG 새로고침 보장).
 *  - extraBufferCapacity = 64 — 빠른 다중 emit 시 backpressure 회피.
 *  - onBufferOverflow = DROP_OLDEST — 느린 구독자 때문에 producer 가 막히지 않음.
 *
 * 이벤트 ID 는 단조 증가 long (Last-Event-ID replay 의 기반, W4 에서 본격 사용).
 */
class SseBroadcaster {

    private val mutableEvents = MutableSharedFlow<RenderCompleteEvent>(
        replay = REPLAY_BUFFER,
        extraBufferCapacity = EXTRA_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<RenderCompleteEvent> = mutableEvents.asSharedFlow()

    private val nextId = AtomicLong(0)

    /** ID 를 자동 할당하면서 새 이벤트 발행. 호출자는 id 신경 안 써도 됨. */
    suspend fun emitRenderComplete(
        renderId: String,
        layout: String,
        widthPx: Int,
        heightPx: Int,
        durationMs: Long
    ) {
        mutableEvents.emit(
            RenderCompleteEvent(
                id = nextId.incrementAndGet(),
                renderId = renderId,
                layout = layout,
                widthPx = widthPx,
                heightPx = heightPx,
                durationMs = durationMs
            )
        )
    }

    private companion object {
        // 새 구독자가 받는 replay 깊이 — 1 이면 최신 1개만.
        private const val REPLAY_BUFFER = 1
        // 추가 버퍼 — 빠른 emit 처리.
        private const val EXTRA_BUFFER_CAPACITY = 64
    }
}
