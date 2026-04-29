package dev.axp.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CliArgsTest {

    private val valueBearing = CliConstants.VALUE_BEARING_FLAGS

    @Test
    fun `--key=value 형식 파싱`() {
        val parsed = CliArgs.parse(arrayOf("--dist-dir=/opt/x"), valueBearing)

        assertEquals("/opt/x", parsed.valueOf("--dist-dir"))
        assertFalse(parsed.hasFlag("--dist-dir"))
    }

    @Test
    fun `비 value-bearing --flag 단독 은 flags set 에 등록`() {
        val parsed = CliArgs.parse(arrayOf("--stdio"), valueBearing)

        assertTrue(parsed.hasFlag("--stdio"))
        assertNull(parsed.valueOf("--stdio"))
    }

    @Test
    fun `--key= (빈 value) 는 IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            CliArgs.parse(arrayOf("--dist-dir="), valueBearing)
        }
        assertTrue(
            ex.message!!.contains("--dist-dir") && ex.message!!.contains("값 누락"),
            "에러 메시지: ${ex.message}",
        )
    }

    @Test
    fun `MF-B2 value-bearing --dist-dir bare (= 없이) 는 IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            CliArgs.parse(arrayOf("--dist-dir"), valueBearing)
        }
        assertTrue(
            ex.message!!.contains("--dist-dir") && ex.message!!.contains("값을 받는 옵션"),
            "에러 메시지: ${ex.message}",
        )
    }

    @Test
    fun `비-플래그 위치 arg (-- prefix 없음) 는 warn-only 로 무시`() {
        val parsed = CliArgs.parse(arrayOf("garbage", "--stdio"), valueBearing)

        assertTrue(parsed.hasFlag("--stdio"))
        assertFalse(parsed.hasFlag("garbage"))
        assertNull(parsed.valueOf("garbage"))
    }

    @Test
    fun `중복 key 는 last-wins`() {
        val parsed = CliArgs.parse(arrayOf("--dist-dir=/a", "--dist-dir=/b"), valueBearing)

        assertEquals("/b", parsed.valueOf("--dist-dir"))
    }

    @Test
    fun `MF-F2 edge cases — ---triple-dash 는 flags 에 그대로 저장, --=value 는 빈 key + value 저장`() {
        val parsed = CliArgs.parse(arrayOf("---triple-dash", "--=value"), valueBearing)

        assertTrue(parsed.hasFlag("---triple-dash"))
        assertEquals("value", parsed.valueOf("--"))
    }
}
