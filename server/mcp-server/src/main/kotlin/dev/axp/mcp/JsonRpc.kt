package dev.axp.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 envelope. Request / Response / Error 3종.
 *
 * Notification 은 id 가 없다 (null). Response 는 result 또는 error 중 정확히 하나.
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = StdioConstants.JSONRPC_VERSION,
    /** null 이면 notification. 숫자 또는 문자열 허용 — JsonElement 로 그대로 보관. */
    val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = StdioConstants.JSONRPC_VERSION,
    val id: JsonElement,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/** initialize 응답의 serverInfo 블록. */
@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

/** initialize 응답 최상단 구조. */
@Serializable
data class InitializeResult(
    @SerialName("protocolVersion") val protocolVersion: String,
    val capabilities: JsonElement,
    @SerialName("serverInfo") val serverInfo: ServerInfo
)

/** tools/list 응답. 빈 배열 허용. */
@Serializable
data class ToolsListResult(
    val tools: List<ToolDescriptor>
)

/** 단일 tool 메타데이터. */
@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String,
    @SerialName("inputSchema") val inputSchema: JsonElement
)
