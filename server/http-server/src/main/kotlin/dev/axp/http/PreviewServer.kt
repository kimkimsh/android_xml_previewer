package dev.axp.http

import dev.axp.protocol.render.PngRenderer
import dev.axp.http.HttpServerConstants.DEFAULT_HOST
import dev.axp.http.HttpServerConstants.DEFAULT_PORT
import dev.axp.http.HttpServerConstants.GRACE_PERIOD_MS
import dev.axp.http.HttpServerConstants.HARD_TIMEOUT_MS
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Ktor Netty 임베디드 서버 lifecycle.
 *
 * Week 1 D5: localhost:7321 에 PreviewRoutes 마운트. mcp-server Main 이 이 클래스를 호출.
 * Week 4 D18: flock 기반 포트 코디네이션 + AXP_TOKEN 헤더 (07 §4.6, §4.7).
 */
class PreviewServer(
    private val port: Int = DEFAULT_PORT,
    private val host: String = DEFAULT_HOST,
    private val pngRenderer: PngRenderer = PlaceholderPngRenderer()
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val broadcaster = SseBroadcaster()
    private val routes = PreviewRoutes(pngRenderer, broadcaster)

    // W1-END 페어 리뷰 C-1: shutdown hook thread 와 main thread 간 JMM data race 방지.
    // @Volatile 이 없으면 blockUntilShutdown() 루프가 stop() 의 engine=null 쓰기를 영원히 못 볼 live-lock 위험.
    @Volatile
    private var engine: NettyApplicationEngine? = null

    /** 서버 시작 (논블로킹). 초기 render_complete 이벤트도 1건 발행. */
    fun start() {
        check(engine == null) { "PreviewServer 가 이미 시작됨" }
        engine = embeddedServer(Netty, port = port, host = host) {
            routing {
                routes.install(this)
            }
        }.start(wait = false)
        log.info("axp HTTP server listening on http://{}:{}", host, port)
        log.info("viewer: http://{}:{}/", host, port)

        // 새 구독자가 즉시 PNG 갱신 신호를 받도록 초기 이벤트 1건 broadcaster 에 emit.
        // SharedFlow.replay=1 이라 이 시점 이후 연결되는 모든 EventSource 가 받음.
        runBlocking {
            broadcaster.emitRenderComplete(
                renderId = "init-0",
                layout = HttpServerConstants.DEFAULT_LAYOUT,
                widthPx = PlaceholderPngConstants.PHONE_NORMAL_WIDTH_PX,
                heightPx = PlaceholderPngConstants.PHONE_NORMAL_HEIGHT_PX,
                durationMs = 0L
            )
        }
    }

    /** Graceful shutdown (테스트/JVM hook 에서 호출). */
    fun stop() {
        engine?.stop(GRACE_PERIOD_MS, HARD_TIMEOUT_MS)
        engine = null
    }

    /**
     * JVM 종료 시까지 main thread 를 hold (foreground 실행용).
     * SIGTERM/SIGINT 시 shutdown hook 이 stop() 호출하여 engine 을 null 로 만들면 루프 종료.
     */
    fun blockUntilShutdown() {
        if (engine == null) return
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
        while (engine != null) {
            Thread.sleep(SHUTDOWN_POLL_INTERVAL_MS)
        }
    }

    private companion object {
        // shutdown poll 주기 — main thread 가 종료 조건 검사하는 sleep.
        private const val SHUTDOWN_POLL_INTERVAL_MS = 500L
    }
}
