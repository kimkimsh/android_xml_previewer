package dev.axp.mcp

import dev.axp.protocol.Versions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * MCP 메서드 라우터.
 *
 * `notifications/initialized` 는 null 반환 → McpStdioServer 는 응답 생략.
 * tool 등록은 [registerTool] 로 외부에서 추가 (Task 5 에서 render_layout 주입).
 *
 * F-5 (W2D6 pair review): id 가 JsonPrimitive(String|Number) 도 아니고 Kotlin null 도 아니면
 * -32600 Invalid Request 반환. spec: id MUST be String, Number, or null.
 */
class McpMethodHandler(
    private val json: Json
) {

    private val tools: MutableMap<String, ToolDescriptor> = mutableMapOf()
    private val toolInvokers: MutableMap<String, (JsonElement?) -> JsonElement> = mutableMapOf()

    fun registerTool(descriptor: ToolDescriptor, invoker: (JsonElement?) -> JsonElement) {
        tools[descriptor.name] = descriptor
        toolInvokers[descriptor.name] = invoker
    }

    fun handle(req: JsonRpcRequest): JsonRpcResponse? {
        val isNotification = req.id == null
        if (!isNotification && req.id !is JsonPrimitive) {
            // F-5: JsonNull, JsonObject, JsonArray as id are all invalid per spec.
            return respondError(req, StdioConstants.ERR_INVALID_REQUEST, "id 는 number 또는 string 만 허용")
        }
        return when (req.method) {
            StdioConstants.METHOD_INITIALIZE -> respondInitialize(req)
            StdioConstants.METHOD_NOTIFICATIONS_INITIALIZED -> null
            StdioConstants.METHOD_TOOLS_LIST -> respondToolsList(req)
            StdioConstants.METHOD_TOOLS_CALL -> respondToolsCall(req)
            StdioConstants.METHOD_SHUTDOWN -> respondShutdown(req)
            else -> respondError(req, StdioConstants.ERR_METHOD_NOT_FOUND, "Unknown method: ${req.method}")
        }
    }

    private fun respondInitialize(req: JsonRpcRequest): JsonRpcResponse {
        val capabilities = buildJsonObject {
            put(StdioConstants.CAP_TOOLS, buildJsonObject { })
            put(StdioConstants.CAP_LOGGING, buildJsonObject { })
        }
        val serverInfo = ServerInfo(
            name = StdioConstants.SERVER_NAME,
            version = Versions.SERVER_VERSION
        )
        val result = InitializeResult(
            protocolVersion = StdioConstants.MCP_PROTOCOL_VERSION,
            capabilities = capabilities,
            serverInfo = serverInfo
        )
        return JsonRpcResponse(
            id = req.id ?: error("initialize request 는 id 필수"),
            result = json.encodeToJsonElement(InitializeResult.serializer(), result)
        )
    }

    private fun respondToolsList(req: JsonRpcRequest): JsonRpcResponse {
        val result = ToolsListResult(tools.values.toList())
        return JsonRpcResponse(
            id = req.id ?: error("tools/list request 는 id 필수"),
            result = json.encodeToJsonElement(ToolsListResult.serializer(), result)
        )
    }

    private fun respondToolsCall(req: JsonRpcRequest): JsonRpcResponse {
        val paramsObj = req.params as? JsonObject
            ?: return respondError(req, StdioConstants.ERR_INVALID_PARAMS, "tools/call params 는 JSON object")
        val name = (paramsObj["name"] as? JsonPrimitive)?.content
            ?: return respondError(req, StdioConstants.ERR_INVALID_PARAMS, "tools/call 에 name 누락")
        val invoker = toolInvokers[name]
            ?: return respondError(req, StdioConstants.ERR_METHOD_NOT_FOUND, "Unknown tool: $name")
        val toolArgs = paramsObj["arguments"]
        val toolResult = runCatching { invoker(toolArgs) }
        return toolResult.fold(
            onSuccess = { JsonRpcResponse(id = req.id ?: error("id 필수"), result = it) },
            onFailure = { respondError(req, StdioConstants.ERR_INTERNAL_ERROR, "tool 실행 실패: ${it.message}") }
        )
    }

    private fun respondShutdown(req: JsonRpcRequest): JsonRpcResponse {
        return JsonRpcResponse(
            id = req.id ?: error("shutdown 은 id 필수"),
            result = buildJsonObject { }
        )
    }

    private fun respondError(req: JsonRpcRequest, code: Int, message: String): JsonRpcResponse {
        // Use JsonNull as id when req.id is non-null but invalid (e.g. JsonArray) — spec says echo id if possible.
        val responseId = when (val rid = req.id) {
            null -> JsonNull
            is JsonPrimitive -> rid
            else -> JsonNull
        }
        return JsonRpcResponse(
            id = responseId,
            error = JsonRpcError(code = code, message = message)
        )
    }
}
