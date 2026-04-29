package dev.axp.protocol.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 07 §4.1 MCP envelope 표준.
 *
 * 모든 MCP 응답이 이 구조로 감싸짐. 클라이언트는 `schemaVersion` MAJOR 불일치 시 reject,
 * MINOR 는 additive 이므로 호환. 새 기능은 새 tool 로 제공하고 `capabilities` 로 advertise.
 */
@Serializable
data class McpEnvelope<T>(
    /** 프로토콜 컨트랙트 MAJOR.MINOR (e.g. "1.0"). 증가 규칙은 07 §4.1 참조. */
    @SerialName("schema_version") val schemaVersion: String,
    /** axp-server 빌드 버전 (e.g. "1.0.0"). */
    @SerialName("server_version") val serverVersion: String,
    /** 이 서버가 지원하는 기능 집합. e.g. ["render.l1","render.l3","render.batch"]. */
    val capabilities: Set<String>,
    /** 성공 페이로드. `error` 와 상호 배타 (둘 다 null 허용 — 빈 ACK). */
    val data: T? = null,
    val error: ErrorEnvelope? = null
)

/**
 * 07 §4.1 ErrorEnvelope. UnrenderableReason.code 를 machine-readable `code` 로 노출.
 */
@Serializable
data class ErrorEnvelope(
    /** "AXP-L3-001" 등. UnrenderableReason.code 와 매핑. */
    val code: String,
    /** UnrenderableReason.Category.name 문자열. */
    val category: String,
    /** 사용자-facing 한줄 메시지. */
    val message: String,
    /** 디버그 상세 (JVM 예외 요약 등). 사용자에게 기본 노출되지는 않음. */
    val detail: String? = null,
    /** 해결 방법 한줄 (e.g. "./gradlew :app:assembleDebug"). */
    val remediation: String? = null,
    /** docs/TROUBLESHOOTING.md anchor 의 완전 URL. */
    @SerialName("remediation_url") val remediationUrl: String? = null,
    val retriable: Boolean = false
)

/**
 * MCP 서버가 응답하는 capability 식별자 상수.
 * 새 capability 추가 시 이 객체에만 변경 — 오타 방지.
 */
object Capabilities {
    const val RENDER_L1 = "render.l1"
    const val RENDER_L3 = "render.l3"
    const val RENDER_BATCH = "render.batch"
    const val AAPT2_CACHE_WARM = "aapt2.cache.warm"
    const val SSE_MINIMAL = "sse.minimal"            // W1D5-R4: render_complete 단일 이벤트 (08 §1.2)
    const val SSE_FULL_TAXONOMY = "sse.taxonomy.v1"  // W4 D16-17: 10-event taxonomy 완전 구현 시 advertise
    const val PROJECT_MULTI = "project.multi"
}
