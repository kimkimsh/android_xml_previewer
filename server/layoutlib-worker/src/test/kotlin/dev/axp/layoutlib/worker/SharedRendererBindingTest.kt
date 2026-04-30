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

    // W3D4 T8: RendererArgs 가 themeName 까지 포함하는 4-tuple. 본 unit test 는 verify() 의
    // equality 동작만 검사하므로 임의 fixture theme 문자열 사용.
    private val themeA = "Theme.AxpFixture"
    private val themeB = "Theme.Other"

    @Test
    fun `bound 가 null 이면 항상 통과 (첫 바인드)`() {
        assertDoesNotThrow {
            SharedRendererBinding.verify(
                bound = null,
                requested = RendererArgs(distA, fixA, moduleA, themeA),
            )
        }
    }

    @Test
    fun `bound 와 requested 가 동일하면 통과`() {
        val same = RendererArgs(distA, fixA, moduleA, themeA)

        assertDoesNotThrow {
            SharedRendererBinding.verify(bound = same, requested = same)
        }
    }

    @Test
    fun `bound 와 requested 가 다르면 IllegalStateException`() {
        val bound = RendererArgs(distA, fixA, moduleA, themeA)
        val requested = RendererArgs(distB, fixB, moduleB, themeB)

        val ex = assertThrows<IllegalStateException> {
            SharedRendererBinding.verify(bound = bound, requested = requested)
        }
        assertTrue(
            ex.message!!.contains("불일치") && ex.message!!.contains("bound"),
            "메시지: ${ex.message}",
        )
    }
}
