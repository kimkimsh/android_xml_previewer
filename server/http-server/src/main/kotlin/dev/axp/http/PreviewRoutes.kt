package dev.axp.http

import dev.axp.http.HttpServerConstants.CACHE_CONTROL_NO_STORE
import dev.axp.http.HttpServerConstants.DEFAULT_LAYOUT
import dev.axp.http.HttpServerConstants.MEDIA_TYPE_PNG
import dev.axp.http.HttpServerConstants.VIEWER_INDEX_FILE
import dev.axp.http.HttpServerConstants.VIEWER_RESOURCE_BASE
import dev.axp.protocol.render.PngRenderer
import dev.axp.http.SseConstants.CACHE_CONTROL_NO_CACHE
import dev.axp.http.SseConstants.CONNECTION_KEEP_ALIVE
import dev.axp.http.SseConstants.CONTENT_TYPE_EVENT_STREAM
import dev.axp.http.SseConstants.EVENT_FRAME_TERMINATOR
import dev.axp.http.SseConstants.EVENT_RENDER_COMPLETE
import dev.axp.http.SseConstants.FIELD_DATA
import dev.axp.http.SseConstants.FIELD_EVENT
import dev.axp.http.SseConstants.FIELD_ID
import dev.axp.http.SseConstants.FIELD_LINE_SEPARATOR
import dev.axp.http.SseConstants.PROXY_BUFFER_OFF_HEADER
import dev.axp.http.SseConstants.PROXY_BUFFER_OFF_VALUE
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * /preview, /api/events, 정적 viewer routes.
 *
 * Week 1 D5 의 minimal SSE 는 `respondBytesWriter` 직접 구현 (Ktor 2.3 엔 ktor-server-sse 없음).
 * Week 4 D16-17 에서 10-event taxonomy + Last-Event-ID replay 로 확장.
 */
class PreviewRoutes(
    private val pngRenderer: PngRenderer,
    private val broadcaster: SseBroadcaster
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    fun install(route: Route) {
        installPreview(route)
        installSse(route)
        installManualTrigger(route)
        installViewerStatic(route)
    }

    /** GET /preview?layout=xxx — placeholder PNG (W2 부터 layoutlib 실제 렌더). */
    private fun installPreview(route: Route) {
        route.get("/preview") {
            val layout = call.request.queryParameters["layout"] ?: DEFAULT_LAYOUT
            val pngBytes = pngRenderer.renderPng(layout)
            call.response.header(HttpHeaders.CacheControl, CACHE_CONTROL_NO_STORE)
            call.respondBytes(pngBytes, ContentType.parse(MEDIA_TYPE_PNG))
        }
    }

    /**
     * GET /api/events — SSE 스트림. 새 구독자는 SharedFlow.replay=1 덕분에 가장 최근
     * `render_complete` 이벤트를 즉시 받음 (브라우저 페이지 로드 직후 PNG refresh 보장).
     */
    private fun installSse(route: Route) {
        route.get("/api/events") {
            call.response.header(HttpHeaders.CacheControl, CACHE_CONTROL_NO_CACHE)
            call.response.header(HttpHeaders.Connection, CONNECTION_KEEP_ALIVE)
            call.response.header(PROXY_BUFFER_OFF_HEADER, PROXY_BUFFER_OFF_VALUE)
            call.respondBytesWriter(contentType = ContentType.parse(CONTENT_TYPE_EVENT_STREAM)) {
                try {
                    // 초기 코멘트 — 일부 클라이언트는 첫 byte 받기 전엔 onopen 호출 안 함.
                    writeStringUtf8(":connected${EVENT_FRAME_TERMINATOR}")
                    flush()
                    broadcaster.events.collect { event ->
                        val payloadJson = json.encodeToString(event)
                        val frame = buildString {
                            append(FIELD_ID).append(": ").append(event.id).append(FIELD_LINE_SEPARATOR)
                            append(FIELD_EVENT).append(": ").append(EVENT_RENDER_COMPLETE).append(FIELD_LINE_SEPARATOR)
                            append(FIELD_DATA).append(": ").append(payloadJson).append(EVENT_FRAME_TERMINATOR)
                        }
                        writeStringUtf8(frame)
                        flush()
                    }
                }
                catch (e: Throwable) {
                    // 클라이언트 disconnect / 네트워크 에러 시 정상 종료. W4 에서 Last-Event-ID 처리.
                    log.debug("SSE subscriber disconnected: {}", e.javaClass.simpleName)
                }
            }
        }
    }

    /**
     * POST /api/render-now — W1 데모용 수동 트리거. 한 번 호출하면 broadcaster 가 render_complete 발행.
     * Week 2 의 file watcher 가 들어오면 자연 deprecated — 본 endpoint 는 W2 D6 에 제거 예정.
     */
    private fun installManualTrigger(route: Route) {
        route.post("/api/render-now") {
            val layout = call.request.queryParameters["layout"] ?: DEFAULT_LAYOUT
            val started = System.currentTimeMillis()
            // placeholder 만 만들어보고 (실제 렌더 W2) — bytes 자체는 버림 (트리거 의도).
            pngRenderer.renderPng(layout)
            val elapsed = System.currentTimeMillis() - started
            broadcaster.emitRenderComplete(
                renderId = "manual-${started}",
                layout = layout,
                widthPx = PlaceholderPngConstants.PHONE_NORMAL_WIDTH_PX,
                heightPx = PlaceholderPngConstants.PHONE_NORMAL_HEIGHT_PX,
                durationMs = elapsed
            )
            call.respondBytes("ok\n".toByteArray(), ContentType.Text.Plain, HttpStatusCode.OK)
        }
    }

    /** classpath resource viewer/ 디렉토리 아래 정적 파일을 루트 경로(`/`)에서 서빙. index 는 index.html. */
    private fun installViewerStatic(route: Route) {
        route.staticResources("/", VIEWER_RESOURCE_BASE, VIEWER_INDEX_FILE)
    }

    @Suppress("unused") // 향후 W4 의 CacheControl 빌더에 사용 — 컴파일러 워닝 회피
    private fun ktorCacheControlNoStoreHelper(): CacheControl = CacheControl.NoStore(visibility = null)
}
