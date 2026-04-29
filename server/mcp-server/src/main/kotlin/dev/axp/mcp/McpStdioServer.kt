package dev.axp.mcp

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream

/**
 * stdin 의 각 라인을 JsonRpcRequest 로 파싱, [McpMethodHandler] 에 위임, 응답을 stdout 에
 * ndjson 으로 기록. Notification (id=null) 은 응답을 보내지 않는다.
 *
 * 스레드-단일: stdin 이 EOF 일 때까지 단일 루프. logback 출력은 stderr 로 고정 (Task 1).
 */
class McpStdioServer(
    private val handler: McpMethodHandler,
    private val inputStream: InputStream = System.`in`,
    private val outputStream: OutputStream = System.out,
    // W2D6 final pair review (Codex HIGH): JSON-RPC 2.0 §5 — result/error 상호 배타.
    // explicitNulls=false 로 null 필드 생략 → 성공 응답에 error:null 이, 에러 응답에 result:null 이
    // 직렬화되지 않도록 함.
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false }
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun run() {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val writer = if (outputStream is PrintStream) outputStream
                     else PrintStream(outputStream, true, Charsets.UTF_8)
        log.info("MCP stdio loop start (protocol={})", StdioConstants.MCP_PROTOCOL_VERSION)
        while (true) {
            val line = try {
                reader.readLine()
            } catch (e: Throwable) {
                log.warn("stdin read 실패: {}", e.javaClass.simpleName)
                null
            } ?: break
            if (line.isBlank()) continue
            processLine(line, writer)
        }
        log.info("MCP stdio loop exit (EOF)")
    }

    private fun processLine(line: String, writer: PrintStream) {
        val request = try {
            json.decodeFromString<JsonRpcRequest>(line)
        } catch (e: SerializationException) {
            emitParseError(writer, e.message ?: "parse error")
            return
        } catch (e: IllegalArgumentException) {
            emitParseError(writer, e.message ?: "parse error")
            return
        }
        val response = handler.handle(request) ?: return
        val encoded = json.encodeToString(JsonRpcResponse.serializer(), response)
        writer.print(encoded)
        writer.print(StdioConstants.FRAME_TERMINATOR)
        writer.flush()
    }

    private fun emitParseError(writer: PrintStream, detail: String) {
        val resp = JsonRpcResponse(
            id = JsonNull,
            error = JsonRpcError(
                code = StdioConstants.ERR_PARSE_ERROR,
                message = "Parse error: ${detail.take(160)}"
            )
        )
        writer.print(json.encodeToString(JsonRpcResponse.serializer(), resp))
        writer.print(StdioConstants.FRAME_TERMINATOR)
        writer.flush()
    }
}
