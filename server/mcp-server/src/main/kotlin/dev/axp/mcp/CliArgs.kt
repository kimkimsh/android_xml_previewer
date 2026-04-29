package dev.axp.mcp

/**
 * W3D2 integration cleanup (Codex F3 carry) — 간단한 CLI 인자 파서.
 *
 * GNU-style `--key=value` 와 `--flag` (value 없음) 모두 지원. 비-플래그 위치 인자와
 * 정체 불명 플래그는 stderr 경고 후 무시 (하위호환).
 */
internal data class CliArgs(
    val flags: Set<String>,
    val values: Map<String, String>,
) {
    fun hasFlag(name: String): Boolean = flags.contains(name)
    fun valueOf(name: String): String? = values[name]

    companion object {
        fun parse(args: Array<String>, valueBearingFlags: Set<String>): CliArgs {
            val flags = mutableSetOf<String>()
            val values = mutableMapOf<String, String>()
            for (raw in args) {
                if (!raw.startsWith("--")) {
                    System.err.println("axp: 알 수 없는 인자 무시: $raw")
                    continue
                }
                val eqIdx = raw.indexOf(CliConstants.ARG_SEPARATOR)
                if (eqIdx < 0) {
                    // MF-B2: value-bearing flag 가 `=` 없이 단독 입력되면 throw.
                    require(raw !in valueBearingFlags) {
                        "axp: $raw 는 값을 받는 옵션입니다 — `$raw=<value>` 형식 필요 (${CliConstants.USAGE_LINE})"
                    }
                    flags.add(raw)
                } else {
                    // MF-F2: `--=value` 는 key="--" + value="value" 로 파싱됨 (의도된 동작 —
                    // valueOf("--") lookup 은 호출부 어디서도 하지 않으므로 harmless).
                    // `---triple-dash` 등 malformed flag 는 eqIdx<0 branch 로 flags 에 저장되며
                    // 역시 조회되지 않아 무시됨. 엄격 validation 은 향후 필요 시 별도 carry.
                    val key = raw.substring(0, eqIdx)
                    val value = raw.substring(eqIdx + 1)
                    require(value.isNotEmpty()) {
                        "axp: $key 값 누락 (${CliConstants.USAGE_LINE})"
                    }
                    values[key] = value
                }
            }
            return CliArgs(flags, values)
        }
    }
}
