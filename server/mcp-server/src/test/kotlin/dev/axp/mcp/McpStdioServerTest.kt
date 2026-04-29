package dev.axp.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * End-to-end loop test — stdin 에 JSON-RPC 라인들을 밀어넣고 stdout frame 을 캡처한다.
 */
class McpStdioServerTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `initialize then tools-list then shutdown — full loop`() {
        val input = buildString {
            append("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""").append('\n')
            append("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").append('\n')
            append("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").append('\n')
            append("""{"jsonrpc":"2.0","id":3,"method":"shutdown"}""").append('\n')
        }
        val out = ByteArrayOutputStream()
        val ps = PrintStream(out, true, Charsets.UTF_8)
        val server = McpStdioServer(
            handler = McpMethodHandler(json),
            inputStream = input.byteInputStream(Charsets.UTF_8),
            outputStream = ps
        )

        server.run()

        val frames = String(out.toByteArray(), Charsets.UTF_8).split('\n').filter { it.isNotBlank() }
        assertEquals(3, frames.size, "initialize + tools/list + shutdown 응답 = 3 (notification 은 응답 없음)")
        assertTrue(frames[0].contains("protocolVersion"))
        assertTrue(frames[1].contains("\"tools\":[]"))
        assertTrue(frames[2].contains("\"result\":{}"))
        assertFalse(frames.any { frame ->
            val parsed = runCatching { json.parseToJsonElement(frame).jsonObject }.getOrNull()
            parsed?.get("error") != null && parsed["error"].toString() != "null"
        }, "정상 흐름에 error 객체 없어야 함")
    }

    @Test
    fun `malformed JSON emits parse error -32700`() {
        val input = "this is not json\n"
        val out = ByteArrayOutputStream()
        val ps = PrintStream(out, true, Charsets.UTF_8)
        val server = McpStdioServer(
            handler = McpMethodHandler(json),
            inputStream = input.byteInputStream(Charsets.UTF_8),
            outputStream = ps
        )
        server.run()
        val frame = String(out.toByteArray(), Charsets.UTF_8).trim()
        assertTrue(frame.contains("\"code\":${StdioConstants.ERR_PARSE_ERROR}"))
    }

    @Test
    fun `tools-call with registered tool returns content array with image block`() {
        val handler = McpMethodHandler(json)
        handler.registerTool(
            descriptor = ToolDescriptor(
                name = StdioConstants.TOOL_RENDER_LAYOUT,
                description = "test",
                inputSchema = kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("object"))
                }
            ),
            invoker = { _ ->
                // Minimal response matching F-1 spec shape: content[] array with image block
                kotlinx.serialization.json.buildJsonObject {
                    put(StdioConstants.FIELD_CONTENT, kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.buildJsonObject {
                            put(StdioConstants.FIELD_TYPE, kotlinx.serialization.json.JsonPrimitive(StdioConstants.CONTENT_TYPE_IMAGE))
                            put(StdioConstants.FIELD_DATA, kotlinx.serialization.json.JsonPrimitive("dGVzdA=="))
                            put(StdioConstants.FIELD_MIME_TYPE, kotlinx.serialization.json.JsonPrimitive(StdioConstants.MIME_TYPE_PNG))
                        })
                    })
                    put(StdioConstants.FIELD_IS_ERROR, kotlinx.serialization.json.JsonPrimitive(false))
                }
            }
        )
        val input = buildString {
            append("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""").append('\n')
            append("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").append('\n')
            append("""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"render_layout","arguments":{"layout":"activity_basic.xml"}}}""").append('\n')
        }
        val out = ByteArrayOutputStream()
        val ps = PrintStream(out, true, Charsets.UTF_8)
        val server = McpStdioServer(handler = handler, inputStream = input.byteInputStream(Charsets.UTF_8), outputStream = ps)
        server.run()

        val frames = String(out.toByteArray(), Charsets.UTF_8).split('\n').filter { it.isNotBlank() }
        assertEquals(2, frames.size, "initialize + tools/call 응답 = 2")

        val callFrame = json.parseToJsonElement(frames[1]).jsonObject
        val result = callFrame["result"]?.jsonObject ?: error("result 없음")
        val content = result[StdioConstants.FIELD_CONTENT]?.jsonArray ?: error("content 배열 없음")
        assertEquals(1, content.size)
        val block = content[0].jsonObject
        assertEquals(StdioConstants.CONTENT_TYPE_IMAGE, block[StdioConstants.FIELD_TYPE]?.jsonPrimitive?.content)
        assertEquals(StdioConstants.MIME_TYPE_PNG, block[StdioConstants.FIELD_MIME_TYPE]?.jsonPrimitive?.content)
        assertNotNull(block[StdioConstants.FIELD_DATA])
    }
}
