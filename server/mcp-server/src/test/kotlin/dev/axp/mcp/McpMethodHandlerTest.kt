package dev.axp.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpMethodHandlerTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val handler = McpMethodHandler(json)

    @Test
    fun `initialize returns protocolVersion + serverInfo + tools capability`() {
        val req = JsonRpcRequest(
            id = JsonPrimitive(1),
            method = StdioConstants.METHOD_INITIALIZE,
            params = buildJsonObject { }
        )
        val resp = handler.handle(req)
        assertNotNull(resp)
        val result = resp!!.result?.jsonObject ?: error("result 없음")
        assertEquals(StdioConstants.MCP_PROTOCOL_VERSION, result["protocolVersion"]?.jsonPrimitive?.content)
        val serverInfo = result["serverInfo"]?.jsonObject ?: error("serverInfo 없음")
        assertEquals(StdioConstants.SERVER_NAME, serverInfo["name"]?.jsonPrimitive?.content)
        val caps = result["capabilities"]?.jsonObject ?: error("capabilities 없음")
        assertTrue(caps.containsKey(StdioConstants.CAP_TOOLS), "tools capability 필수")
    }

    @Test
    fun `notifications initialized returns null response`() {
        val req = JsonRpcRequest(
            id = null,
            method = StdioConstants.METHOD_NOTIFICATIONS_INITIALIZED
        )
        val resp = handler.handle(req)
        assertNull(resp, "notification 은 response 없음")
    }

    @Test
    fun `tools list returns empty array by default`() {
        val req = JsonRpcRequest(
            id = JsonPrimitive(2),
            method = StdioConstants.METHOD_TOOLS_LIST
        )
        val resp = handler.handle(req)
        assertNotNull(resp)
        val result = resp!!.result?.jsonObject ?: error("result 없음")
        val tools = result["tools"] ?: error("tools key 없음")
        assertEquals("[]", tools.toString(), "기본 tools 배열은 비어 있음")
    }

    @Test
    fun `unknown method yields JSON-RPC -32601`() {
        val req = JsonRpcRequest(
            id = JsonPrimitive(3),
            method = "does/not/exist"
        )
        val resp = handler.handle(req)
        assertNotNull(resp)
        assertNull(resp!!.result)
        assertEquals(StdioConstants.ERR_METHOD_NOT_FOUND, resp.error?.code)
    }

    @Test
    fun `id with JsonArray returns -32600 Invalid Request`() {
        val req = JsonRpcRequest(
            id = JsonArray(listOf(JsonPrimitive(1))),
            method = StdioConstants.METHOD_TOOLS_LIST
        )
        val resp = handler.handle(req)
        assertNotNull(resp)
        assertEquals(StdioConstants.ERR_INVALID_REQUEST, resp!!.error?.code)
    }

    @Test
    fun `id with JsonObject returns -32600 Invalid Request`() {
        val req = JsonRpcRequest(
            id = buildJsonObject { },
            method = StdioConstants.METHOD_TOOLS_LIST
        )
        val resp = handler.handle(req)
        assertNotNull(resp)
        assertEquals(StdioConstants.ERR_INVALID_REQUEST, resp!!.error?.code)
    }

    @Test
    fun `JsonNull id routes normally — treated as non-notification primitive`() {
        // JsonNull is a JsonPrimitive subtype in kotlinx.serialization; the JSON literal null is
        // not a structural type error. The request is treated as a regular (non-notification) call.
        val req = JsonRpcRequest(
            id = JsonNull,
            method = StdioConstants.METHOD_TOOLS_LIST
        )
        val resp = handler.handle(req)
        assertNotNull(resp)
        // Should get a valid tools/list result, not an error.
        assertNotNull(resp!!.result)
        assertNull(resp.error)
    }
}
