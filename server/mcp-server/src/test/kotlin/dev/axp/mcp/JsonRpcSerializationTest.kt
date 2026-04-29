package dev.axp.mcp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonRpcSerializationTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `request roundtrips with numeric id`() {
        val raw = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}"""
        val req = json.decodeFromString<JsonRpcRequest>(raw)
        assertEquals("initialize", req.method)
        assertEquals(JsonPrimitive(1), req.id)
        assertNotNull(req.params)
    }

    @Test
    fun `notification has null id`() {
        val raw = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
        val req = json.decodeFromString<JsonRpcRequest>(raw)
        assertNull(req.id, "notification 의 id 는 null")
    }

    @Test
    fun `response with result encodes minimal JSON`() {
        val resp = JsonRpcResponse(
            id = JsonPrimitive(1),
            result = buildJsonObject { put("ok", JsonPrimitive(true)) }
        )
        val encoded = json.encodeToString(resp)
        val parsed = json.parseToJsonElement(encoded) as JsonObject
        assertEquals(JsonPrimitive(1), parsed["id"])
        assertNotNull(parsed["result"])
    }
}
