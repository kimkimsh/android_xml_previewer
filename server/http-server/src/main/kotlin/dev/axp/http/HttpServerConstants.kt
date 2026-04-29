package dev.axp.http

/**
 * HTTP/SSE 서버 도메인 상수.
 *
 * 본 프로젝트는 CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" 정책에 따라
 * 도메인 의미가 있는 모든 리터럴을 상수로 분리. 도메인별로 하나의 object 에 집약 (NetworkConstants 분류).
 */
object HttpServerConstants {
    /** 08 §5 / handoff §2.5 canonical 포트. plugin.json 의 viewer URL 과 동기화. */
    const val DEFAULT_PORT = 7321

    /** Linux/Mac/Windows 모두 loopback. 외부 노출은 W4 axprev serve --host 로. */
    const val DEFAULT_HOST = "127.0.0.1"

    /** 서버 graceful shutdown 대기 시간 (ms). Netty engineStop 인자. */
    const val GRACE_PERIOD_MS = 1_000L

    /** 서버 hard timeout (ms). 위 grace 가 지나면 강제 종료. */
    const val HARD_TIMEOUT_MS = 5_000L

    /** /preview 의 default 레이아웃. fixture/sample-app 의 활성 레이아웃. */
    const val DEFAULT_LAYOUT = "activity_basic.xml"

    /** PNG 미디어 타입 헤더 값. */
    const val MEDIA_TYPE_PNG = "image/png"

    /** /preview 응답에 적용 — 항상 fresh 가져오게 함 (cache buster 와 병행). */
    const val CACHE_CONTROL_NO_STORE = "no-store"

    /** 정적 viewer 가 들어있는 classpath resource base 경로. */
    const val VIEWER_RESOURCE_BASE = "viewer"

    /** viewer 진입 파일 (Ktor staticResources 의 index). */
    const val VIEWER_INDEX_FILE = "index.html"
}
