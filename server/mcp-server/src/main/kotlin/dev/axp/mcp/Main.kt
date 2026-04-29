package dev.axp.mcp

import dev.axp.http.HttpServerConstants
import dev.axp.http.PlaceholderPngRenderer
import dev.axp.http.PreviewServer
import dev.axp.layoutlib.worker.DistDiscovery
import dev.axp.layoutlib.worker.FixtureDiscovery
import dev.axp.layoutlib.worker.LayoutlibRenderer
import dev.axp.protocol.Versions
import dev.axp.protocol.mcp.Capabilities
import dev.axp.protocol.render.PngRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

/**
 * Claude Code MCP 서버 엔트리.
 *
 * CLI 모드:
 *   --smoke           : stderr 에 버전 라인 + stdout 에 "ok" 1줄 후 종료 (CI 스모크).
 *   --stdio           : MCP JSON-RPC 2.0 루프 (stdin/stdout, EOF 까지 유지).
 *   --http-server     : localhost:7321 에 HTTP/SSE 서버 + viewer.
 *   (인자 없음)         : 기본 = --http-server (W1 demo 호환 유지).
 *
 * Path override (optional):
 *   --dist-dir=<path>     : layoutlib dist. 없으면 DistDiscovery.locate(null) 로 auto-detect.
 *   --fixture-root=<path> : XML fixture 루트. 없으면 FixtureDiscovery.locate(null).
 *   override 제공했으나 경로 비존재 → IllegalArgumentException (fail-fast).
 *   auto-detect 모두 실패 → PlaceholderPngRenderer fallback (기존 behavior 유지).
 */
fun main(args: Array<String>) {
    val parsed = CliArgs.parse(args, CliConstants.VALUE_BEARING_FLAGS)
    val versionLine = buildVersionLine()
    System.err.println(versionLine)

    if (parsed.hasFlag(StdioConstants.SMOKE_FLAG)) {
        System.out.println(StdioConstants.SMOKE_OK_LINE)
        return
    }

    if (parsed.hasFlag(StdioConstants.STDIO_FLAG)) {
        runStdioMode(parsed)
        return
    }

    runHttpMode(parsed)
}

private fun runStdioMode(parsed: CliArgs) {
    // W2D6 final pair review (Codex HIGH): JSON-RPC 2.0 §5 는 result 와 error 상호 배타.
    // kotlinx.serialization 은 encodeDefaults=true + null 필드를 기본 방출하므로 explicitNulls=false
    // 로 null 필드를 생략해 spec 준수.
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false }
    val renderer = chooseRenderer(parsed)
    val handler = McpMethodHandler(json)
    handler.registerTool(
        descriptor = ToolDescriptor(
            name = StdioConstants.TOOL_RENDER_LAYOUT,
            description = "Render a single Android layout XML to a PNG (base64-encoded) using layoutlib.",
            inputSchema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {
                    put(StdioConstants.TOOL_PARAM_LAYOUT, buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Layout file name, e.g. activity_basic.xml"))
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive(StdioConstants.TOOL_PARAM_LAYOUT))
                })
            }
        ),
        invoker = { args -> invokeRenderLayoutTool(renderer, args) }
    )
    val server = McpStdioServer(handler, System.`in`, System.out, json)
    server.run()
}

private fun runHttpMode(parsed: CliArgs) {
    val renderer = chooseRenderer(parsed)
    val server = PreviewServer(pngRenderer = renderer)
    server.start()
    System.err.println(
        "axp viewer ready: http://${HttpServerConstants.DEFAULT_HOST}:${HttpServerConstants.DEFAULT_PORT}/" +
            " (renderer=${renderer.javaClass.simpleName})"
    )
    server.blockUntilShutdown()
}

private fun invokeRenderLayoutTool(renderer: PngRenderer, args: Any?): kotlinx.serialization.json.JsonObject {
    val layoutName = (args as? kotlinx.serialization.json.JsonObject)
        ?.get(StdioConstants.TOOL_PARAM_LAYOUT)
        ?.let { (it as? JsonPrimitive)?.content }
        ?: error("layout 인자 누락")
    val bytes = renderer.renderPng(layoutName)
    val b64 = Base64.getEncoder().encodeToString(bytes)
    // F-1 (W2D6 pair review): MCP spec requires result.content[] typed content blocks.
    return buildJsonObject {
        put(StdioConstants.FIELD_CONTENT, buildJsonArray {
            add(buildJsonObject {
                put(StdioConstants.FIELD_TYPE, JsonPrimitive(StdioConstants.CONTENT_TYPE_IMAGE))
                put(StdioConstants.FIELD_DATA, JsonPrimitive(b64))
                put(StdioConstants.FIELD_MIME_TYPE, JsonPrimitive(StdioConstants.MIME_TYPE_PNG))
            })
        })
        put(StdioConstants.FIELD_IS_ERROR, JsonPrimitive(false))
    }
}

private fun chooseRenderer(parsed: CliArgs): PngRenderer {
    val distOverride: Path? = parsed.valueOf(CliConstants.DIST_DIR_FLAG)?.let { Paths.get(it) }
    val fixtureOverride: Path? = parsed.valueOf(CliConstants.FIXTURE_ROOT_FLAG)?.let { Paths.get(it) }
    val sampleAppOverride: Path? = parsed.valueOf(CliConstants.SAMPLE_APP_ROOT_FLAG)?.let { Paths.get(it) }

    val dist = DistDiscovery.locate(distOverride)
    val fixture = FixtureDiscovery.locate(fixtureOverride)

    if (dist == null || fixture == null) {
        System.err.println(
            "axp: layoutlib dist 또는 fixture 탐지 실패 (dist=$dist, fixture=$fixture) " +
                "→ placeholder PNG 로 fallback"
        )
        return PlaceholderPngRenderer()
    }
    val sampleAppModuleRoot: Path = FixtureDiscovery.locateModuleRoot(sampleAppOverride)
        ?: error("sample-app module root 탐지 실패 — --sample-app-root 명시 또는 fixture/sample-app 확인")
    return try {
        LayoutlibRenderer(
            distDir = dist.toAbsolutePath().normalize(),
            fallback = PlaceholderPngRenderer(),
            fixtureRoot = fixture.toAbsolutePath().normalize(),
            sampleAppModuleRoot = sampleAppModuleRoot.toAbsolutePath().normalize(),
        )
    } catch (e: Throwable) {
        System.err.println(
            "axp: LayoutlibRenderer 초기화 실패 (${e.javaClass.simpleName}) → placeholder fallback"
        )
        PlaceholderPngRenderer()
    }
}

private fun buildVersionLine(): String {
    return buildString {
        append("axp-server v").append(Versions.SERVER_VERSION)
        append(" (schema ").append(Versions.SCHEMA_VERSION).append(")")
        append(" capabilities=[")
        // W1-END 페어 리뷰 C-2: 실제 보유 capability 만 advertise.
        append(listOf(Capabilities.RENDER_L1, Capabilities.SSE_MINIMAL).joinToString(","))
        append("]")
    }
}
