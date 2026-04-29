package dev.axp.http

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SseBroadcasterTest {

    @Test
    fun `replay=1 delivers most recent event to new subscriber`() = runTest {
        val broadcaster = SseBroadcaster()
        broadcaster.emitRenderComplete(
            renderId = "test-1",
            layout = "activity_basic.xml",
            widthPx = 720,
            heightPx = 1280,
            durationMs = 12L
        )
        // 새 collector — replay=1 덕분에 즉시 "test-1" 받아야 함.
        val first = broadcaster.events.first()
        assertEquals("test-1", first.renderId)
        assertEquals("activity_basic.xml", first.layout)
        assertEquals(12L, first.durationMs)
    }

    @Test
    fun `id auto-increments per emit`() = runTest {
        val broadcaster = SseBroadcaster()
        broadcaster.emitRenderComplete("a", "x.xml", 1, 1, 0)
        broadcaster.emitRenderComplete("b", "y.xml", 1, 1, 0)
        // 두번째 이벤트가 latest — replay=1.
        val latest = broadcaster.events.first()
        assertEquals("b", latest.renderId)
        assertEquals(2L, latest.id)
    }
}
