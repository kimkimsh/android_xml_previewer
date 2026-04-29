package dev.axp.mcp

/**
 * W3D2 integration cleanup (Codex F3 carry) — CLI flag 이름 / 구분자 / value-bearing flag 집합.
 *
 * StdioConstants (mode flags: --smoke, --stdio, --http-server) 와 분리되어 있다.
 * 이 object 는 "값을 받는 옵션" (--dist-dir=/x, --fixture-root=/y) 전용.
 */
internal object CliConstants {
    const val DIST_DIR_FLAG = "--dist-dir"
    const val FIXTURE_ROOT_FLAG = "--fixture-root"
    const val SAMPLE_APP_ROOT_FLAG = "--sample-app-root"
    const val ARG_SEPARATOR = "="
    const val USAGE_LINE =
        "Usage: axp-server [--dist-dir=<path>] [--fixture-root=<path>] " +
            "[--sample-app-root=<sample-app-module-dir>] [--smoke|--stdio|--http-server]"

    /**
     * MF-B2 — 값을 반드시 `=<value>` 로 받아야 하는 flag 집합. `CliArgs.parse` 가
     * 이 집합의 flag 가 `=` 없이 단독 입력되면 `IllegalArgumentException` 을 던진다.
     */
    val VALUE_BEARING_FLAGS: Set<String> = setOf(DIST_DIR_FLAG, FIXTURE_ROOT_FLAG, SAMPLE_APP_ROOT_FLAG)
}
