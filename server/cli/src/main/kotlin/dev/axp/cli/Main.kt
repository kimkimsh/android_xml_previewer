package dev.axp.cli

import dev.axp.protocol.Versions
import kotlin.system.exitProcess

/**
 * `axprev` CLI 엔트리 (F7 — 브랜드명 변경).
 *
 * 서브커맨드 (계획):
 *   axprev render <xml> --device=phone_normal --out foo.png
 *   axprev serve  --no-open-browser --host=0.0.0.0 --port=7321
 *   axprev setup-avd  (AVD 자동 다운로드 안내 — 08 §3.2)
 *   axprev clean-cache
 *
 * Week 1 Day 1 는 버전 출력만. 실제 파서는 Week 5 D22 (F5 — CLI v1 승격).
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null, "--help", "-h" -> {
            System.out.println(HELP_TEXT)
        }
        "--version", "-V" -> {
            System.out.println("axprev ${Versions.SERVER_VERSION}")
        }
        else -> {
            System.err.println("axprev: unimplemented subcommand '${args[0]}' (W1D1 scaffold)")
            exitProcess(64)
        }
    }
}

private val HELP_TEXT = """
    axprev — Android XML Previewer (v${Versions.SERVER_VERSION})

    USAGE:
      axprev <command> [options]

    COMMANDS:
      render <xml>          (TBD W5)  단일 레이아웃 → PNG 파일로 렌더
      serve                 (TBD W2)  HTTP+SSE 서버만 실행
      setup-avd             (TBD W3)  AVD 시스템 이미지 다운로드 안내
      clean-cache           (TBD W5)  .axp-cache 정리

    STATUS:
      이 바이너리는 Week 1 Day 1 스캐폴드입니다. 서브커맨드는 순차적으로 구현됩니다.
""".trimIndent()
