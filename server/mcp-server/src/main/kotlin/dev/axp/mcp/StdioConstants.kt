package dev.axp.mcp

/**
 * MCP stdio JSON-RPC 2.0 프로토콜 상수 (spec 2025-06-18).
 *
 * MCP 는 newline-delimited JSON 프레임 (ndjson) 을 stdin/stdout 에 쓴다.
 * Content-Length 헤더는 쓰지 않는다 (LSP 와 다름).
 *
 * CLAUDE.md "no magic strings" — 모든 프로토콜 문자열은 여기서만 선언.
 */
object StdioConstants {
    const val JSONRPC_VERSION = "2.0"

    // method names
    const val METHOD_INITIALIZE = "initialize"
    const val METHOD_NOTIFICATIONS_INITIALIZED = "notifications/initialized"
    const val METHOD_TOOLS_LIST = "tools/list"
    const val METHOD_TOOLS_CALL = "tools/call"
    const val METHOD_SHUTDOWN = "shutdown"

    // MCP protocol version advertised by this server (spec 2025-06-18 revision)
    const val MCP_PROTOCOL_VERSION = "2025-06-18"

    // server info
    const val SERVER_NAME = "axp-server"

    // JSON-RPC error codes (spec)
    const val ERR_PARSE_ERROR = -32700
    const val ERR_INVALID_REQUEST = -32600
    const val ERR_METHOD_NOT_FOUND = -32601
    const val ERR_INVALID_PARAMS = -32602
    const val ERR_INTERNAL_ERROR = -32603

    // capability flags surfaced in initialize response
    const val CAP_TOOLS = "tools"
    const val CAP_LOGGING = "logging"

    // tools/call content block type
    const val CONTENT_TYPE_IMAGE = "image"

    // MIME types
    const val MIME_TYPE_PNG = "image/png"

    // tools/call result fields
    const val FIELD_CONTENT = "content"
    const val FIELD_IS_ERROR = "isError"
    const val FIELD_TYPE = "type"
    const val FIELD_DATA = "data"
    const val FIELD_MIME_TYPE = "mimeType"

    // tool names
    const val TOOL_RENDER_LAYOUT = "render_layout"
    const val TOOL_PARAM_LAYOUT = "layout"

    // CLI args
    const val STDIO_FLAG = "--stdio"
    const val HTTP_FLAG = "--http-server"
    const val SMOKE_FLAG = "--smoke"
    const val SMOKE_OK_LINE = "ok"

    // stdio frame terminator
    const val FRAME_TERMINATOR = "\n"
}
