package dev.axp.mcp

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.ConsoleAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * W2D6-STDIO precondition: MCP stdio 모드는 JSON-RPC 프레임만 stdout 으로 내보내야 한다.
 * logback 이 기본으로 stdout 을 쓰면 프레임 파싱이 깨지므로, ConsoleAppender 의 target
 * 이 반드시 "System.err" 로 지정되어 있어야 한다.
 */
class LogbackStderrConfigTest {

    @Test
    fun `root logger uses ConsoleAppender pinned to STDERR`() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        val root = ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        val appenders = root.iteratorForAppenders().asSequence().toList()
        assertTrue(appenders.isNotEmpty(), "root logger 에 appender 가 하나 이상 있어야 함")
        val consoles = appenders.filterIsInstance<ConsoleAppender<*>>()
        assertTrue(consoles.isNotEmpty(), "ConsoleAppender 가 설정되어 있어야 함")
        consoles.forEach {
            assertEquals(
                "System.err", it.target,
                "ConsoleAppender target 이 System.err 가 아니면 stdio JSON-RPC 프레임이 오염됨"
            )
        }
    }
}
