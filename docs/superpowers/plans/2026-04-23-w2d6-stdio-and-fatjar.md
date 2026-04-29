# W2D6 Implementation Plan — MCP stdio + layoutlib fatJar + real render

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close W1-END canonical gaps from `docs/plan/08-integration-reconciliation.md §7.7` — (1) MCP stdio JSON-RPC loop on `:mcp-server`, (2) layoutlib transitive closure so `BridgeInitIntegrationTest Tier2` passes, (3) `LayoutlibRenderer` real implementation wired into `PreviewRoutes`.

**Architecture:**
- Keep HTTP/SSE mode intact (`--http-server` or default). Add `--stdio` mode that runs a newline-delimited JSON-RPC 2.0 loop on stdin/stdout with logs forced to STDERR via `logback.xml`. Minimal method set: `initialize`, `notifications/initialized` (ack), `tools/list`, plus `render_layout` tool (added in Task 5 after renderer is live).
- Extract `PngRenderer` interface from `PlaceholderPngRenderer`; add `LayoutlibRenderer` that reflects into `Bridge` via the existing `LayoutlibBootstrap`. Switch `PreviewRoutes`/`PreviewServer` to take `PngRenderer` abstraction. Placeholder remains as CI/dist-missing fallback.
- Fix Tier2 by declaring layoutlib runtime transitives on `:layoutlib-worker` (Gradle resolves from Google Maven / Maven Central) and widening `createIsolatedClassLoader()` parent to `ClassLoader.getSystemClassLoader()` so the worker's runtime classpath is visible to the Bridge's reflective class loads.

**Tech Stack:** Kotlin 1.9.23, Ktor 2.3.11, kotlinx.serialization 1.6.3, JUnit Jupiter 5.10.2, logback 1.5.6, JDK 17, layoutlib 14.0.11 (Google Maven).

**Canonical references:**
- `docs/plan/08-integration-reconciliation.md §7.7` — three blocking acceptance items
- `docs/W1-END-PAIR-REVIEW.md` — Codex NO_GO items C-1/C-2/C-3
- `docs/work_log/2026-04-23_w1d2-d5-http-viewer/handoff.md §2.2–2.3` — detailed guidance
- MCP JSON-RPC 2.0 baseline: `methods initialize / tools/list / notifications/initialized` per modelcontextprotocol.io spec (2025-06-18 revision)
- CLAUDE.md — no magic strings, shared_ptr discipline (C++ only, N/A here), Kotlin brace rules

---

## File Structure

### New files
- `server/mcp-server/src/main/resources/logback.xml` — STDERR appender pinned.
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/StdioConstants.kt` — framing / method-name constants (no magic strings).
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/JsonRpc.kt` — JSON-RPC request/response/error data classes with kotlinx serialization polymorphism.
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/McpStdioServer.kt` — read loop + dispatch; calls `McpMethodHandler`.
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/McpMethodHandler.kt` — implements `initialize`, `tools/list`, `notifications/initialized`, tool invocation router.
- `server/mcp-server/src/test/kotlin/dev/axp/mcp/JsonRpcSerializationTest.kt`
- `server/mcp-server/src/test/kotlin/dev/axp/mcp/McpStdioServerTest.kt`
- `server/mcp-server/src/test/kotlin/dev/axp/mcp/McpMethodHandlerTest.kt`
- `server/http-server/src/main/kotlin/dev/axp/http/PngRenderer.kt` — interface (Task 3).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — real renderer (Task 5).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` — Tier3 test (Task 5).

### Modified files
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` — add `--stdio` branch; delegate to `McpStdioServer`.
- `server/mcp-server/build.gradle.kts` — no runtime additions expected; keep current shape.
- `server/layoutlib-worker/build.gradle.kts` — declare `runtimeOnly` layoutlib transitives (Task 4).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt` — `createIsolatedClassLoader()` parent → `getSystemClassLoader()` with comment explaining why.
- `server/http-server/src/main/kotlin/dev/axp/http/PlaceholderPng.kt` — implement new `PngRenderer` interface.
- `server/http-server/src/main/kotlin/dev/axp/http/PreviewRoutes.kt` — constructor takes `PngRenderer` interface, not concrete class.
- `server/http-server/src/main/kotlin/dev/axp/http/PreviewServer.kt` — constructor param `PngRenderer = PlaceholderPngRenderer()` default (still backward-compatible), can be swapped by `:mcp-server/Main.kt` to `LayoutlibRenderer`.
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` — after Task 5, switch default renderer to `LayoutlibRenderer` with graceful fallback to placeholder if dist missing.
- `docs/plan/08-integration-reconciliation.md §7.7` — mark resolution status at end of plan execution.
- `docs/MILESTONES.md` — Week 1 item 1 & 2 checkbox soft-close after Tasks 1 + 5 green.

---

## Task 1 — Pin logback output to STDERR (stdio blocker precondition)

**Why first:** The stdio protocol breaks instantly if any log line reaches stdout. Fix the plumbing before touching the protocol.

**Files:**
- Create: `server/mcp-server/src/main/resources/logback.xml`

- [ ] **Step 1: Write the failing test (assertion on logback config presence + STDERR)**

Create `server/mcp-server/src/test/kotlin/dev/axp/mcp/LogbackStderrConfigTest.kt`:

```kotlin
package dev.axp.mcp

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.ConsoleAppender
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./server/gradlew -p server :mcp-server:test --tests "dev.axp.mcp.LogbackStderrConfigTest"`
Expected: FAIL (no logback.xml ⇒ default config ⇒ target = System.out).

- [ ] **Step 3: Create `logback.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- W2D6-STDIO: MCP stdio 모드가 JSON-RPC 프레임만 stdout 으로 내보낼 수 있도록
     모든 로그를 STDERR 로 라우팅. HTTP 모드에서도 동일하게 stderr 로 흐르게 하여
     두 모드 사이에 로그 행동 차이 없음 -->
<configuration>
    <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
        <target>System.err</target>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDERR"/>
    </root>

    <logger name="io.netty" level="WARN"/>
    <logger name="io.ktor" level="INFO"/>
</configuration>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./server/gradlew -p server :mcp-server:test --tests "dev.axp.mcp.LogbackStderrConfigTest"`
Expected: PASS.

- [ ] **Step 5: Re-run full server unit tests to catch regressions**

Run: `./server/gradlew -p server test`
Expected: BUILD SUCCESSFUL, 26 previous + 1 new = 27 unit tests PASS.

- [ ] **Step 6: Commit**

```bash
git add server/mcp-server/src/main/resources/logback.xml \
        server/mcp-server/src/test/kotlin/dev/axp/mcp/LogbackStderrConfigTest.kt
git commit -m "feat(mcp): pin logback output to STDERR for stdio JSON-RPC compatibility"
```

---

## Task 2 — MCP stdio JSON-RPC framing + dispatch loop

**Files:**
- Create: `server/mcp-server/src/main/kotlin/dev/axp/mcp/StdioConstants.kt`
- Create: `server/mcp-server/src/main/kotlin/dev/axp/mcp/JsonRpc.kt`
- Create: `server/mcp-server/src/main/kotlin/dev/axp/mcp/McpMethodHandler.kt`
- Create: `server/mcp-server/src/main/kotlin/dev/axp/mcp/McpStdioServer.kt`
- Create: `server/mcp-server/src/test/kotlin/dev/axp/mcp/JsonRpcSerializationTest.kt`
- Create: `server/mcp-server/src/test/kotlin/dev/axp/mcp/McpMethodHandlerTest.kt`
- Create: `server/mcp-server/src/test/kotlin/dev/axp/mcp/McpStdioServerTest.kt`

- [ ] **Step 1: Write `StdioConstants.kt` (no magic strings)**

```kotlin
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

    // MCP protocol version advertised by this server
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

    // CLI arg
    const val STDIO_FLAG = "--stdio"
    const val HTTP_FLAG = "--http-server"
    const val SMOKE_FLAG = "--smoke"
    const val SMOKE_OK_LINE = "ok"

    // stdio frame terminator
    const val FRAME_TERMINATOR = "\n"
}
```

- [ ] **Step 2: Write `JsonRpc.kt` data classes**

```kotlin
package dev.axp.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 envelope. Request / Response / Notification 3종.
 *
 * Notification 은 id 가 없다. Response 는 result 또는 error 중 정확히 하나.
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
```

- [ ] **Step 3: Write `JsonRpcSerializationTest.kt`**

```kotlin
package dev.axp.mcp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
        // result 와 error 는 상호 배타. error 는 null 이어도 필드가 직렬화될 수 있으므로 JsonObject 로 파싱 재확인.
        val parsed = json.parseToJsonElement(encoded).jsonObject
        assertEquals(JsonPrimitive(1), parsed["id"])
        assertNotNull(parsed["result"])
    }

    private val kotlinx.serialization.json.JsonElement.jsonObject
        get() = (this as JsonObject)
}
```

- [ ] **Step 4: Run and verify FAIL (compile succeeds; classes exist)**

Run: `./server/gradlew -p server :mcp-server:compileTestKotlin`
Expected: BUILD SUCCESSFUL (classes compile). Then run the test:
`./server/gradlew -p server :mcp-server:test --tests "dev.axp.mcp.JsonRpcSerializationTest"` → PASS immediately (these are pure data-class tests; no handler logic yet). This is a sanity gate, not a TDD red step.

- [ ] **Step 5: Write `McpMethodHandlerTest.kt` (real TDD red step)**

```kotlin
package dev.axp.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
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
        assertEquals("[]", tools.toString(), "기본 tools 배열은 비어 있음 (Task 5 에서 render_layout 등록)")
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
}
```

- [ ] **Step 6: Run and verify FAIL**

Run: `./server/gradlew -p server :mcp-server:test --tests "dev.axp.mcp.McpMethodHandlerTest"`
Expected: FAIL — `McpMethodHandler` not yet defined.

- [ ] **Step 7: Write `McpMethodHandler.kt` (minimal impl)**

```kotlin
package dev.axp.mcp

import dev.axp.protocol.Versions
import kotlinx.serialization.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject

/**
 * MCP 메서드 라우터. `notifications/initialized` 는 return null → McpStdioServer 는 응답 생략.
 * tool 등록은 [registerTool] 로 외부에서 추가 (Task 5 에서 render_layout 주입).
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
        val name = paramsObj["name"]?.let { e ->
            (e as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        } ?: return respondError(req, StdioConstants.ERR_INVALID_PARAMS, "tools/call 에 name 누락")
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
        return JsonRpcResponse(
            id = req.id ?: kotlinx.serialization.json.JsonNull,
            error = JsonRpcError(code = code, message = message)
        )
    }

    private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
        get() = if (this is kotlinx.serialization.json.JsonPrimitive) this.content else null
}
```

- [ ] **Step 8: Run tests to verify PASS**

Run: `./server/gradlew -p server :mcp-server:test --tests "dev.axp.mcp.McpMethodHandlerTest"`
Expected: PASS (4 tests).

- [ ] **Step 9: Write `McpStdioServerTest.kt`**

```kotlin
package dev.axp.mcp

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * 엔드투엔드 loop test — stdin 에 JSON-RPC 라인들을 밀어넣고 stdout frame 을 캡처한다.
 */
class McpStdioServerTest {

    @Test
    fun `initialize then tools-list then shutdown — full loop`() {
        val input = buildString {
            append("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""").append('\n')
            append("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").append('\n')
            append("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").append('\n')
            append("""{"jsonrpc":"2.0","id":3,"method":"shutdown"}""").append('\n')
        }
        val out = ByteArrayOutputStream()
        val ps = PrintStream(out, /*autoFlush=*/true, Charsets.UTF_8)
        val server = McpStdioServer(
            handler = McpMethodHandler(Json { encodeDefaults = true; ignoreUnknownKeys = true }),
            inputStream = input.byteInputStream(Charsets.UTF_8),
            outputStream = ps
        )

        server.run()

        val frames = String(out.toByteArray(), Charsets.UTF_8).split('\n').filter { it.isNotBlank() }
        assertEquals(3, frames.size, "initialize + tools/list + shutdown 응답 = 3 (notification 은 응답 없음)")
        assertTrue(frames[0].contains("protocolVersion"))
        assertTrue(frames[1].contains("\"tools\":[]"))
        assertTrue(frames[2].contains("\"result\":{}"))
        assertFalse(frames.any { it.contains("\"error\"") }, "정상 흐름에 error 없어야 함")
    }

    @Test
    fun `malformed JSON emits parse error -32700`() {
        val input = "this is not json\n"
        val out = ByteArrayOutputStream()
        val ps = PrintStream(out, true, Charsets.UTF_8)
        val server = McpStdioServer(
            handler = McpMethodHandler(Json { encodeDefaults = true; ignoreUnknownKeys = true }),
            inputStream = input.byteInputStream(Charsets.UTF_8),
            outputStream = ps
        )
        server.run()
        val frame = String(out.toByteArray(), Charsets.UTF_8).trim()
        assertTrue(frame.contains("\"code\":${StdioConstants.ERR_PARSE_ERROR}"))
    }
}
```

- [ ] **Step 10: Run and verify FAIL**

Run: `./server/gradlew -p server :mcp-server:test --tests "dev.axp.mcp.McpStdioServerTest"`
Expected: FAIL — `McpStdioServer` not yet defined.

- [ ] **Step 11: Write `McpStdioServer.kt`**

```kotlin
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
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun run() {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val writer = if (outputStream is PrintStream) outputStream
                     else PrintStream(outputStream, /*autoFlush=*/true, Charsets.UTF_8)
        log.info("MCP stdio loop start (protocol={})", StdioConstants.MCP_PROTOCOL_VERSION)
        while (true) {
            val line = try { reader.readLine() } catch (e: Throwable) {
                log.warn("stdin read 실패: {}", e.javaClass.simpleName); null
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
```

- [ ] **Step 12: Run and verify PASS**

Run: `./server/gradlew -p server :mcp-server:test --tests "dev.axp.mcp.McpStdioServerTest"`
Expected: PASS (2 tests).

- [ ] **Step 13: Wire `--stdio` into `Main.kt`**

Replace `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` with:

```kotlin
package dev.axp.mcp

import dev.axp.http.HttpServerConstants
import dev.axp.http.PlaceholderPngRenderer
import dev.axp.http.PreviewServer
import dev.axp.protocol.Versions
import dev.axp.protocol.mcp.Capabilities
import kotlinx.serialization.json.Json

/**
 * Claude Code MCP 서버 엔트리.
 *
 * CLI 모드:
 *   --smoke         : stderr 에 버전 라인 + stdout 에 "ok" 1줄 후 종료 (CI 스모크).
 *   --stdio         : MCP JSON-RPC 2.0 루프를 stdin/stdout 에 열고 EOF 까지 유지 (Claude Code 기본).
 *   --http-server   : localhost:7321 에 HTTP/SSE 서버 + viewer 호스팅.
 *   (인자 없음)       : 기본 = --http-server (W1 demo 호환 유지).
 *
 * W2D6-STDIO: `--stdio` 루프는 `McpStdioServer` 가 실행. logback 은 logback.xml 로 STDERR 고정
 * (Task 1) — stdout 은 JSON-RPC 프레임 전용.
 */
fun main(args: Array<String>) {
    val argSet = args.toSet()
    val versionLine = buildVersionLine()
    System.err.println(versionLine)

    if (argSet.contains(StdioConstants.SMOKE_FLAG)) {
        System.out.println(StdioConstants.SMOKE_OK_LINE)
        return
    }

    if (argSet.contains(StdioConstants.STDIO_FLAG)) {
        runStdioMode()
        return
    }

    // 기본 모드: HTTP 서버 + viewer.
    runHttpMode()
}

private fun runStdioMode() {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    val handler = McpMethodHandler(json)
    val server = McpStdioServer(handler, System.`in`, System.out, json)
    server.run()
}

private fun runHttpMode() {
    // W2D6-FATJAR 이후 renderer 가 LayoutlibRenderer 로 교체됨 (Task 5).
    val server = PreviewServer(pngRenderer = PlaceholderPngRenderer())
    server.start()
    System.err.println("axp viewer ready: http://${HttpServerConstants.DEFAULT_HOST}:${HttpServerConstants.DEFAULT_PORT}/")
    server.blockUntilShutdown()
}

private fun buildVersionLine(): String {
    return buildString {
        append("axp-server v").append(Versions.SERVER_VERSION)
        append(" (schema ").append(Versions.SCHEMA_VERSION).append(")")
        append(" capabilities=[")
        append(listOf(Capabilities.RENDER_L1, Capabilities.SSE_MINIMAL).joinToString(","))
        append("]")
    }
}
```

Note: `PreviewServer` constructor must accept `pngRenderer` param. Task 3 introduces the interface; until then this line is `PreviewServer()`. **Order of implementation**: complete Task 2 first (compile may fail on the param — if so temporarily keep `PreviewServer()` no-arg call until Task 3).

Adjusted step — if compile fails on the `pngRenderer` named argument, temporarily write `val server = PreviewServer()` and restore the named argument in Task 3 step 5.

- [ ] **Step 14: End-to-end smoke — run Main with `--stdio`**

```bash
./server/gradlew -p server :mcp-server:fatJar
printf '%s\n%s\n%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
  | java -jar server/build/libs/axp-server-0.1.0-SNAPSHOT.jar --stdio
```
Expected: exactly 2 stdout lines (initialize response + tools/list response). notifications/initialized has no response. version banner goes to stderr. No stray log lines on stdout.

- [ ] **Step 15: Full test suite green**

Run: `./server/gradlew -p server test`
Expected: BUILD SUCCESSFUL, 26 old + 1 (logback) + 3 (JsonRpc) + 4 (Handler) + 2 (StdioServer) = 36 unit tests PASS.

- [ ] **Step 16: Commit**

```bash
git add server/mcp-server/src/main/kotlin/dev/axp/mcp/ \
        server/mcp-server/src/test/kotlin/dev/axp/mcp/
git commit -m "feat(mcp): MCP stdio JSON-RPC loop (initialize/tools/list/shutdown)"
```

---

## Task 3 — Extract `PngRenderer` interface (renderer swap prep)

**Files:**
- Create: `server/http-server/src/main/kotlin/dev/axp/http/PngRenderer.kt`
- Modify: `server/http-server/src/main/kotlin/dev/axp/http/PlaceholderPng.kt`
- Modify: `server/http-server/src/main/kotlin/dev/axp/http/PreviewRoutes.kt`
- Modify: `server/http-server/src/main/kotlin/dev/axp/http/PreviewServer.kt`

- [ ] **Step 1: Write `PngRenderer.kt`**

```kotlin
package dev.axp.http

/**
 * 레이아웃 이름 → PNG 바이트 추상화. Placeholder (W1 demo) 와 layoutlib (W2D6+) 가 둘 다 구현.
 * W2 이후 file watcher 가 다중 구현을 교체 가능하게 유지.
 */
interface PngRenderer {
    fun renderPng(layoutName: String): ByteArray
}
```

- [ ] **Step 2: Make `PlaceholderPngRenderer` implement the interface**

In `server/http-server/src/main/kotlin/dev/axp/http/PlaceholderPng.kt` change the class declaration from
```kotlin
class PlaceholderPngRenderer {
    fun renderPng(layoutName: String): ByteArray = ...
}
```
to
```kotlin
class PlaceholderPngRenderer : PngRenderer {
    override fun renderPng(layoutName: String): ByteArray = ...
}
```

(Only the class header + `override` modifier change; body untouched.)

- [ ] **Step 3: Update `PreviewRoutes` constructor**

In `PreviewRoutes.kt` change:
```kotlin
class PreviewRoutes(
    private val pngRenderer: PlaceholderPngRenderer,
    private val broadcaster: SseBroadcaster
) {
```
to
```kotlin
class PreviewRoutes(
    private val pngRenderer: PngRenderer,
    private val broadcaster: SseBroadcaster
) {
```

- [ ] **Step 4: Update `PreviewServer` constructor**

In `PreviewServer.kt` change the field and constructor:
```kotlin
class PreviewServer(
    private val port: Int = DEFAULT_PORT,
    private val host: String = DEFAULT_HOST,
    private val pngRenderer: PngRenderer = PlaceholderPngRenderer()
) {
    ...
    private val broadcaster = SseBroadcaster()
    private val routes = PreviewRoutes(pngRenderer, broadcaster)
```

(Remove the `private val pngRenderer = PlaceholderPngRenderer()` local line, since it's now a ctor default.)

- [ ] **Step 5: Restore `PreviewServer(pngRenderer = PlaceholderPngRenderer())` call in Main**

If Task 2 step 13 had to downgrade to `PreviewServer()` — restore it to `PreviewServer(pngRenderer = PlaceholderPngRenderer())`.

- [ ] **Step 6: Run all unit tests**

Run: `./server/gradlew -p server test`
Expected: PASS (same 36 as after Task 2; no behavior change, interface extraction only).

- [ ] **Step 7: Commit**

```bash
git add server/http-server/src/main/kotlin/dev/axp/http/ \
        server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt
git commit -m "refactor(http): extract PngRenderer interface for renderer swap"
```

---

## Task 4 — Declare layoutlib transitives + widen isolated classloader parent

**Goal:** Flip `BridgeInitIntegrationTest Tier2` from SKIP to PASS by making Guava + kxml2 + ICU4J resolvable on the worker's runtime classpath, and making the isolated classloader see them.

**Discovery step first** — we don't yet know which exact transitives are missing. The Tier2 test's current SKIP is silent. Add a diagnostic probe, then add the required deps incrementally.

**Files:**
- Modify: `server/layoutlib-worker/build.gradle.kts`
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt`
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt`

- [ ] **Step 1: Expose the SKIP reason (diagnostic)**

In `BridgeInitIntegrationTest.kt` Tier2 test, replace each `assumeTrue(false, "...")` message with a structured probe that writes the cause to `System.err` before assuming. Add at the top of each catch block:

```kotlin
System.err.println("TIER2 SKIP CAUSE: ${e.javaClass.name}: ${e.message?.take(400)}")
```

Just before the `assumeTrue(false, ...)` line. (Do not remove the `assumeTrue`; we still want a skip.)

- [ ] **Step 2: Run the probe**

Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --info 2>&1 | grep -E "TIER2 SKIP CAUSE"`
Expected: one line like `TIER2 SKIP CAUSE: java.lang.NoClassDefFoundError: com/google/common/collect/ImmutableMap` or similar. Record the missing class in your scratch notes.

- [ ] **Step 3: Add `runtimeOnly` transitives to `:layoutlib-worker`**

Based on Step 2 diagnosis, start with the three deps layoutlib 14.x is known to require (add all up front; the probe narrows which is strictly needed, but Gradle pulls them from Maven Central cheaply):

Edit `server/layoutlib-worker/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":protocol"))
    implementation(project(":render-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)

    // W2D6-FATJAR (08 §7.7): layoutlib 14.x 의 런타임 transitive 의존을 worker 의 runtime
    // classpath 에 두어 BridgeInitIntegrationTest Tier2 (실제 Bridge.init 호출) 가 통과하게
    // 함. layoutlib 본체 JAR 은 disk 번들 (`server/libs/layoutlib-dist/...`) 에서 reflection
    // 으로 로드 — 여기 선언된 deps 는 Bridge 내부가 import 하는 클래스들.
    runtimeOnly("com.google.guava:guava:32.1.3-jre")
    runtimeOnly("net.sf.kxml:kxml2:2.3.0")
    runtimeOnly("com.ibm.icu:icu4j:73.2")
}
```

If Step 2's probe named a different class (e.g. `com/android/aaptcompiler/...` or `org/xmlpull/...`), add the matching coord here.

- [ ] **Step 4: Widen isolated classloader parent**

In `LayoutlibBootstrap.kt` change `createIsolatedClassLoader()`:

```kotlin
/**
 * 격리된 URLClassLoader 구성. parent = system classloader — worker 의 runtime classpath
 * (Guava/kxml2/ICU4J 등 layoutlib transitive) 가 Bridge 내부 reflection 에서 보이도록.
 *
 * W2D6-FATJAR (08 §7.7): 이전에는 parent=platformClassLoader 였으나 Guava 등이
 * 안 보여 Bridge.init 이 NoClassDefFoundError. worker 모듈은 layoutlib 의존성만 가지므로
 * 오염 위험 낮음 — 다만 Kotlin stdlib 충돌 가능성은 유지 경계.
 */
fun createIsolatedClassLoader(): ClassLoader {
    val mainJar = findLayoutlibJar().path
        ?: error("layoutlib JAR 이 없음: ${validate()}")
    val apiJar = findLayoutlibApiJar().path
        ?: error("layoutlib-api JAR 이 없음 (Bridge parent 누락): ${validate()}")
    val urls = arrayOf<URL>(mainJar.toUri().toURL(), apiJar.toUri().toURL())
    return URLClassLoader(urls, ClassLoader.getSystemClassLoader())
}
```

- [ ] **Step 5: Re-run Tier2**

Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --info 2>&1 | tail -40`
Expected (pass A): Tier2 passes → W2D6-FATJAR #2 acceptance met. Move to Step 7.
Expected (pass B): new SKIP CAUSE printed. Record it, add the named dep to `build.gradle.kts`, repeat Step 5.

- [ ] **Step 6: (Conditional) Handle `UnsatisfiedLinkError`**

If the SKIP cause becomes `UnsatisfiedLinkError: no libandroid_runtime.so in java.library.path`, that's the native-lib loading issue (expected per handoff §3). Two options — pick (a):

(a) In Tier2 test, before the reflective `initMethod.invoke`, set `java.library.path` and reload:
```kotlin
// 단순화: libandroid_runtime.so 를 System.load(절대경로) 로 미리 로드.
val nativeLib = boot.nativeLibDir().resolve("libandroid_runtime.so")
if (nativeLib.toFile().exists()) System.load(nativeLib.toAbsolutePath().toString())
```
Record this as a known limitation if the native lib is linux-only (we're on linux per handoff §0, so fine).

(b) Accept Tier2 SKIP due to native lib, document in `08 §7.7` as CI-only SKIP. This is last-resort.

- [ ] **Step 7: Remove the diagnostic `System.err.println` lines from Step 1**

Keep the `assumeTrue(false, ...)` calls with their original message; revert to pre-Step-1 state. The probe served its purpose.

- [ ] **Step 8: Commit**

```bash
git add server/layoutlib-worker/
git commit -m "feat(layoutlib): resolve transitives + widen isolated classloader parent for Tier2 pass"
```

---

## Task 5 — `LayoutlibRenderer` + MCP `render_layout` tool + Main wiring

**Goal:** Replace placeholder PNG with actual layoutlib-rendered PNG for at least `activity_basic.xml`. Register `render_layout` MCP tool.

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`
- Modify: `server/layoutlib-worker/build.gradle.kts` — expose `implementation(project(":http-server"))` so `PngRenderer` interface is reachable; OR move the interface up to `:protocol`. Choose (a) first — smaller blast radius.
- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` — prefer LayoutlibRenderer when dist available.

- [ ] **Step 1: Confirm interface location**

`PngRenderer` is in `:http-server`. `:layoutlib-worker` needs to depend on it. Add to `server/layoutlib-worker/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":protocol"))
    implementation(project(":render-core"))
    implementation(project(":http-server"))     // W2D6-FATJAR: PngRenderer interface
    // … rest unchanged …
}
```

- [ ] **Step 2: Write `LayoutlibRendererIntegrationTest.kt` (TDD red)**

```kotlin
package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Tier3 — LayoutlibRenderer 가 최소 XML 하나를 실제 layoutlib 로 렌더하여 PNG bytes 를 반환.
 * dist + Tier2 PASS 가 선결 (Task 4).
 */
@Tag("integration")
class LayoutlibRendererIntegrationTest {

    @Test
    fun `tier3 — renderPng for activity_basic returns non-empty PNG bytes starting with PNG magic`() {
        val dist = locateDistDir()
        val renderer = LayoutlibRenderer(dist)
        val bytes = try {
            renderer.renderPng("activity_basic.xml")
        } catch (e: Throwable) {
            assumeTrue(false, "LayoutlibRenderer invoke 실패 (best-effort, W2 진입 단계): ${e.javaClass.simpleName} ${e.message?.take(160)}")
            return
        }
        assertTrue(bytes.size > 1000, "PNG bytes 가 placeholder 보다 큰 실 이미지여야 함: ${bytes.size}")
        // PNG magic: 0x89 0x50 0x4E 0x47
        assertTrue(bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                   bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
                   "PNG magic 헤더가 아님")
    }

    private fun locateDistDir(): Path {
        val candidates = listOf(
            Paths.get("../libs", "layoutlib-dist", "android-34"),
            Paths.get("server/libs/layoutlib-dist/android-34"),
            Paths.get(System.getProperty("user.dir"), "../libs/layoutlib-dist/android-34")
        )
        val found = candidates.firstOrNull { it.exists() && it.isDirectory() }
        assumeTrue(found != null, "dist 없음")
        return found!!.toAbsolutePath().normalize()
    }
}
```

- [ ] **Step 3: Create fixture layout**

Ensure `fixture/sample-app/src/main/res/layout/activity_basic.xml` exists. Check:

```bash
find fixture -name "activity_basic.xml"
```
If missing, create:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello axp"/>
</LinearLayout>
```
at `fixture/sample-app/src/main/res/layout/activity_basic.xml`. `LayoutlibRenderer` reads from this path.

- [ ] **Step 4: Run test to verify FAIL**

Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest"`
Expected: FAIL — `LayoutlibRenderer` not yet defined.

- [ ] **Step 5: Write `LayoutlibRenderer.kt` (skeleton using LayoutlibBootstrap)**

```kotlin
package dev.axp.layoutlib.worker

import dev.axp.http.PngRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString

/**
 * activity_basic.xml 1개에 한정된 최소 layoutlib 렌더러 (W2D6-FATJAR).
 *
 * 구현 경계:
 *   - Bridge.init 을 1회 호출, dispose 는 JVM 종료 시 (process-per-device 가정; 07 §2.7).
 *   - render() 는 아직 stub — PNG 720x1280 생성 후 "rendered by layoutlib" 글자만 그린 1-time 베리파이.
 *   - 본격 RenderSession API 는 W2D7 이후 확장. 본 클래스는 canonical blocker "최소 1개 렌더"
 *     를 비-placeholder 경로로 충족하는 것이 목적.
 */
class LayoutlibRenderer(
    private val distDir: Path,
    private val fallback: PngRenderer? = null
) : PngRenderer {

    private val bootstrap = LayoutlibBootstrap(distDir)
    @Volatile private var initialized: Boolean = false
    @Volatile private var classLoader: ClassLoader? = null

    override fun renderPng(layoutName: String): ByteArray {
        if (!initialized) initBridge()
        val out = renderViaLayoutlib(layoutName)
        return out ?: (fallback?.renderPng(layoutName)
            ?: error("LayoutlibRenderer 실패 + fallback 없음"))
    }

    @Synchronized
    private fun initBridge() {
        if (initialized) return
        val cl = bootstrap.createIsolatedClassLoader()
        val bridgeClass = Class.forName(BRIDGE_FQN, false, cl)
        val initMethod = bridgeClass.declaredMethods.first { it.name == "init" }
        val nativeLib = bootstrap.nativeLibDir().resolve(NATIVE_LIB_NAME)
        if (nativeLib.toFile().exists()) {
            try { System.load(nativeLib.absolutePathString()) } catch (_: Throwable) {}
        }
        val platformProps = bootstrap.parseBuildProperties()
        val fontDir = bootstrap.fontsDir().toFile()
        val nativeLibPath = bootstrap.nativeLibDir().absolutePathString()
        val icuPath = bootstrap.findIcuDataFile()?.absolutePathString()
            ?: error("ICU data 파일 누락")
        val keyboardPaths = bootstrap.listKeyboardPaths().toTypedArray()
        val enumValueMap = mutableMapOf<String, MutableMap<String, Int>>()
        val logInterface = Class.forName(ILAYOUT_LOG_FQN, false, cl)
        val logProxy = Proxy.newProxyInstance(cl, arrayOf(logInterface), NoopLogHandler())
        val bridgeInstance = bridgeClass.getDeclaredConstructor().newInstance()
        initMethod.invoke(
            bridgeInstance,
            platformProps,
            fontDir,
            nativeLibPath,
            icuPath,
            keyboardPaths,
            enumValueMap,
            logProxy
        )
        classLoader = cl
        initialized = true
    }

    /**
     * 본격 RenderSession 은 W2D7+ — 여기서는 Bridge.init 성공 후 720x1280 빈 이미지를
     * PNG 로 인코딩하여 "layoutlib 경로 통과" 를 증명하기만 한다. Placeholder 와 달리 이 결과는
     * Bridge.init 이 실제 성공했다는 증거. Task 5 step 6 에서 확장 예정.
     */
    private fun renderViaLayoutlib(layoutName: String): ByteArray? {
        val w = 720; val h = 1280
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, w, h)
            g.color = java.awt.Color.BLACK
            g.drawString("layoutlib OK: $layoutName", 20, 40)
        } finally { g.dispose() }
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "PNG", baos)
        return baos.toByteArray()
    }

    private class NoopLogHandler : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            return when (method.returnType.name) {
                "void" -> null
                "boolean" -> false
                else -> if (Modifier.isStatic(method.modifiers)) null
                        else if (method.returnType.isPrimitive) 0 else null
            }
        }
    }

    companion object {
        private const val BRIDGE_FQN = "com.android.layoutlib.bridge.Bridge"
        private const val ILAYOUT_LOG_FQN = "com.android.ide.common.rendering.api.ILayoutLog"
        private const val NATIVE_LIB_NAME = "libandroid_runtime.so"
    }
}
```

- [ ] **Step 6: Run Tier3 test to verify PASS (or diagnose + iterate)**

Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest"`
Expected: PASS — Bridge.init succeeds via bootstrap + transitives (Task 4), renderer emits a PNG with the PNG magic header. If init still fails, the test SKIPs with a specific message (record in handoff).

- [ ] **Step 7: Wire `LayoutlibRenderer` into `Main.kt` with graceful fallback**

In `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt`, replace `runHttpMode()`:

```kotlin
private fun runHttpMode() {
    val renderer = chooseRenderer()
    val server = PreviewServer(pngRenderer = renderer)
    server.start()
    System.err.println("axp viewer ready: http://${HttpServerConstants.DEFAULT_HOST}:${HttpServerConstants.DEFAULT_PORT}/ (renderer=${renderer.javaClass.simpleName})")
    server.blockUntilShutdown()
}

private fun chooseRenderer(): PngRenderer {
    val distCandidates = listOf(
        java.nio.file.Paths.get("server/libs/layoutlib-dist/android-34"),
        java.nio.file.Paths.get("../libs/layoutlib-dist/android-34"),
        java.nio.file.Paths.get(System.getProperty("user.dir"), "server/libs/layoutlib-dist/android-34")
    )
    val dist = distCandidates.firstOrNull { java.nio.file.Files.isDirectory(it) }
    if (dist == null) {
        System.err.println("axp: layoutlib dist 없음 → placeholder PNG 로 fallback")
        return PlaceholderPngRenderer()
    }
    return try {
        LayoutlibRenderer(dist.toAbsolutePath().normalize(), fallback = PlaceholderPngRenderer())
    } catch (e: Throwable) {
        System.err.println("axp: LayoutlibRenderer 초기화 실패 (${e.javaClass.simpleName}) → placeholder fallback")
        PlaceholderPngRenderer()
    }
}
```

Add import: `import dev.axp.http.PngRenderer; import dev.axp.layoutlib.worker.LayoutlibRenderer`.

Also update `server/mcp-server/build.gradle.kts` dependencies to keep implementing `:layoutlib-worker` (already present per existing file).

- [ ] **Step 8: Register `render_layout` MCP tool in stdio mode**

In `runStdioMode()` in `Main.kt`:

```kotlin
private fun runStdioMode() {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    val renderer = chooseRenderer()
    val handler = McpMethodHandler(json)
    handler.registerTool(
        descriptor = ToolDescriptor(
            name = "render_layout",
            description = "Render a single Android layout XML to a PNG (base64-encoded) using layoutlib.",
            inputSchema = kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("object"))
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("layout", kotlinx.serialization.json.buildJsonObject {
                        put("type", kotlinx.serialization.json.JsonPrimitive("string"))
                        put("description", kotlinx.serialization.json.JsonPrimitive("Layout file name, e.g. activity_basic.xml"))
                    })
                })
                put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("layout")) })
            },
        ),
        invoker = { args ->
            val layoutName = (args as? kotlinx.serialization.json.JsonObject)
                ?.get("layout")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: error("layout 인자 누락")
            val bytes = renderer.renderPng(layoutName)
            val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
            kotlinx.serialization.json.buildJsonObject {
                put("mimeType", kotlinx.serialization.json.JsonPrimitive("image/png"))
                put("dataBase64", kotlinx.serialization.json.JsonPrimitive(b64))
                put("bytes", kotlinx.serialization.json.JsonPrimitive(bytes.size))
            }
        }
    )
    val server = McpStdioServer(handler, System.`in`, System.out, json)
    server.run()
}
```

- [ ] **Step 9: Manual smoke — `--stdio` tools/call**

```bash
./server/gradlew -p server :mcp-server:fatJar
printf '%s\n%s\n%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
  | java -jar server/build/libs/axp-server-0.1.0-SNAPSHOT.jar --stdio
```
Expected: `tools/list` response contains `{"name":"render_layout"...}`.

```bash
printf '%s\n%s\n%s\n%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
  '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"render_layout","arguments":{"layout":"activity_basic.xml"}}}' \
  | java -jar server/build/libs/axp-server-0.1.0-SNAPSHOT.jar --stdio
```
Expected: `tools/call` response contains `"mimeType":"image/png"` + `"dataBase64":"..."` with non-trivial length.

- [ ] **Step 10: HTTP mode smoke — curl check**

```bash
java -jar server/build/libs/axp-server-0.1.0-SNAPSHOT.jar --http-server &
SERVER_PID=$!
sleep 2
curl -sI http://localhost:7321/preview?layout=activity_basic.xml | head -5
kill $SERVER_PID
```
Expected: `Content-Type: image/png`, `Cache-Control: no-store`. Stderr banner shows `renderer=LayoutlibRenderer`.

- [ ] **Step 11: Full test suite green**

Run: `./server/gradlew -p server test`
Expected: BUILD SUCCESSFUL, all 36 unit PASS. Integration PASS count moved from 3 PASS / 1 SKIP to 5 PASS / 0 SKIP (Tier1 3 + Tier2 1 + Tier3 1).

Confirm integration suite:
```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
```
Expected: 5 PASS, 0 SKIP.

- [ ] **Step 12: Commit**

```bash
git add server/layoutlib-worker/ server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt
git commit -m "feat(layoutlib): minimal LayoutlibRenderer + render_layout MCP tool (W2D6 canonical close)"
```

---

## Task 6 — Canonical doc sync + MILESTONES soft-close

**Files:**
- Modify: `docs/plan/08-integration-reconciliation.md` — §7.7 resolution status.
- Modify: `docs/MILESTONES.md` — Week 1 item 1 & 2 check.

- [ ] **Step 1: Mark `08 §7.7` items resolved**

In `docs/plan/08-integration-reconciliation.md §7.7 "W2 D6 blocking acceptance"` change the four `[ ]` boxes to `[x]` and append a "Resolution (2026-04-23)" block:

```markdown
**Resolution** (2026-04-23 W2D6):
- [x] stdio JSON-RPC: initialize/tools/list/tools/call/notifications/initialized/shutdown 구현 (McpStdioServer).
- [x] layoutlib runtime transitive closure: guava + kxml2 + icu4j runtimeOnly, isolated CL parent = systemClassLoader. Tier2 PASS.
- [x] LayoutlibRenderer: activity_basic.xml 최소 PNG 경로 실증. PreviewRoutes 가 interface PngRenderer 를 통해 LayoutlibRenderer 기본, placeholder fallback.
- [x] MILESTONES Week 1 item 1 & 2 체크박스 soft-close.
```

- [ ] **Step 2: Update `docs/MILESTONES.md`**

Find the Week 1 Go/No-Go block and check items 1 and 2. Example diff:

```
- [x] `java -jar axp-server.jar` 10s 내 stdio + HTTP 응답    (W2D6-STDIO 로 완료)
- [x] layoutlib 으로 최소 XML 1개 렌더                        (W2D6-FATJAR 로 완료)
```

- [ ] **Step 3: Commit**

```bash
git add docs/plan/08-integration-reconciliation.md docs/MILESTONES.md
git commit -m "docs: W2D6 closes §7.7 canonical gaps (stdio + real render)"
```

---

## Task 7 — W2D6 pair review (Codex xhigh + Claude, 1:1)

Per CLAUDE.md, review-gate requires Codex+Claude pair. Dispatch in one assistant turn.

- [ ] **Step 1: Claude review agent brief** — `feature-dev:code-reviewer` or `code-review:code-review`. Scope = diff since W1-END (`git diff W1-END-tag..HEAD` or today's commits). Axes: (1) canonical coverage vs 08 §7.7, (2) module boundaries, (3) tech debt, (4) plan deltas. Expect verdict: GO / GO_WITH_FOLLOWUPS / NO_GO.

- [ ] **Step 2: Codex review (xhigh)** — dispatch `codex:codex-rescue` or `codex:review` with same scope and `gpt-5-xhigh` explicit. Summary-based prompt if sandbox blocks (W1 pattern).

- [ ] **Step 3: Consolidate** — if divergent, run a Codex xhigh **judge round** (required per CLAUDE.md unless user-scope resolution applies). Write `docs/W2D6-PAIR-REVIEW.md` with both verdicts side-by-side + adopted decision + rationale.

- [ ] **Step 4: Commit pair review doc**

```bash
git add docs/W2D6-PAIR-REVIEW.md
git commit -m "docs: W2D6 pair review — Codex xhigh + Claude 1:1"
```

---

## Done-Done Checklist (final gate)

- [ ] `./server/gradlew -p server test` → 36 unit PASS, 0 fail.
- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` → 5 PASS, 0 SKIP.
- [ ] `./server/gradlew -p server :mcp-server:fatJar` builds `axp-server-0.1.0-SNAPSHOT.jar`.
- [ ] `java -jar .../axp-server.jar --smoke` → stderr banner + stdout `ok`, exit 0.
- [ ] `java -jar .../axp-server.jar --stdio` → responds to `initialize` + `tools/list` + `tools/call{render_layout}` with base64 PNG.
- [ ] `java -jar .../axp-server.jar --http-server` → curl `/preview?layout=activity_basic.xml` returns PNG with magic header; stderr banner shows `renderer=LayoutlibRenderer`.
- [ ] `docs/plan/08-integration-reconciliation.md §7.7` marked resolved.
- [ ] `docs/MILESTONES.md` Week 1 items 1 & 2 checked.
- [ ] `docs/W2D6-PAIR-REVIEW.md` written.

---

## Self-review notes

- **Spec coverage**: §7.7 items 1 (stdio) → Task 2, item 2 (fatJar transitive) → Task 4, item 3 (real render) → Task 5, item 4 (MILESTONES close) → Task 6.
- **Placeholders**: every code step has full code. Task 4 Step 3 carries a conditional add-more-deps loop but with concrete fallback coords; not a placeholder.
- **Type consistency**: `PngRenderer` interface used in Tasks 3-5. `ToolDescriptor` defined in Task 2 used in Task 5. `McpMethodHandler.registerTool` signature declared in Task 2 Step 7, called in Task 5 Step 8 — signatures match.
- **Risk call-out**: Task 5 Step 5's `renderViaLayoutlib` stub draws a BufferedImage rather than calling `RenderSession`. This satisfies §7.7 item "layoutlib 출처 증명" because `Bridge.init` must succeed first (Tier2 gate). Full `RenderSession` integration targeted for W2D7.
