package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths

class SharedRendererBindingTest {

    private val distA = Paths.get("/dist/a")
    private val distB = Paths.get("/dist/b")
    private val fixA = Paths.get("/fix/a")
    private val fixB = Paths.get("/fix/b")
    private val moduleA = Paths.get("/module/a")
    private val moduleB = Paths.get("/module/b")

    @Test
    fun `bound 가 null 이면 항상 통과 (첫 바인드)`() {
        assertDoesNotThrow {
            SharedRendererBinding.verify(
                bound = null,
                requested = RendererArgs(distA, fixA, moduleA),
            )
        }
    }

    @Test
    fun `bound 와 requested 가 동일하면 통과`() {
        val same = RendererArgs(distA, fixA, moduleA)

        assertDoesNotThrow {
            SharedRendererBinding.verify(bound = same, requested = same)
        }
    }

    @Test
    fun `bound 와 requested 가 다르면 IllegalStateException`() {
        val bound = RendererArgs(distA, fixA, moduleA)
        val requested = RendererArgs(distB, fixB, moduleB)

        val ex = assertThrows<IllegalStateException> {
            SharedRendererBinding.verify(bound = bound, requested = requested)
        }
        assertTrue(
            ex.message!!.contains("불일치") && ex.message!!.contains("bound"),
            "메시지: ${ex.message}",
        )
    }
}
