package dev.axp.layoutlib.worker

import dev.axp.protocol.Versions
import dev.axp.protocol.worker.WorkerFrame

/**
 * L1 워커 프로세스 엔트리 (07 §2.7 프로세스 격리).
 *
 * 기동 인자:
 *   --device=<id>         디바이스 ID (phone_normal 등). 워커 인스턴스 식별.
 *   --socket=<path>       Unix socket 파일 경로 (부모 프로세스가 생성 후 전달).
 *   --layoutlib-cp=<jar>  layoutlib-dist 디렉토리. 런타임에 URLClassLoader 로 로드.
 *
 * Week 1 Day 1 은 idle 상태에서 Pong 만 응답하는 최소 스켈레톤. 실제 render 루프는 Week 2-3.
 */
fun main(args: Array<String>) {
    val device = args.firstOrNull { it.startsWith("--device=") }?.removePrefix("--device=")
    val socket = args.firstOrNull { it.startsWith("--socket=") }?.removePrefix("--socket=")

    System.err.println(
        "axp-layoutlib-worker v${Versions.WORKER_VERSION}" +
            " device=${device ?: "(unset)"}" +
            " socket=${socket ?: "(unset)"}" +
            " frameHeaderBytes=${WorkerFrame.HEADER_BYTES}" +
            " maxFrame=${WorkerFrame.MAX_FRAME_SIZE}"
    )

    // TODO(W2D7): Unix socket accept 루프 + frame decode + WorkerRequest 처리
    //             (Ping → Pong, Render → LayoutlibSessionPool.render, …)
}
