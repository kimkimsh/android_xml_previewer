package dev.axp.protocol.render

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 07 §4.2 RenderRequest.
 *
 * - `xml_path` : 프로젝트 루트 기준 상대 경로 (canonical)
 * - `request_id` : 클라이언트-생성 멱등키 (취소/재발 식별에 사용)
 * - `client_epoch` : 브라우저 세션 epoch. 서버 재시작 시 mismatch → RESYNC (07 §4.5)
 */
@Serializable
data class RenderRequest(
    @SerialName("\$schema") val schema: String = SCHEMA_VERSION,
    @SerialName("xml_path") val xmlPath: String,
    val device: String = "phone_normal",
    val theme: String = "light",
    @SerialName("night_mode") val nightMode: Boolean = false,
    @SerialName("font_scale") val fontScale: Float = 1.0f,
    val locale: String = "en-US",
    /** "L1" | "L3" | null(auto). dispatcher 가 해석. */
    @SerialName("force_layer") val forceLayer: String? = null,
    @SerialName("force_rerender") val forceRerender: Boolean = false,
    @SerialName("request_id") val requestId: String,
    @SerialName("client_epoch") val clientEpoch: Long = 0,
    /** 미래 호환 — additive field 용 opaque bag. */
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        const val SCHEMA_VERSION = "axp/render-request/1.0"
    }
}

/**
 * 07 §4.2 성공 응답. 실패는 ErrorEnvelope 로 McpEnvelope.error 에 담김.
 */
@Serializable
data class RenderResponseData(
    val status: Status,
    /** monotonic nano-derived base36. URL versioning 에 사용. */
    @SerialName("render_id") val renderId: String,
    /** "layoutlib" | "emulator". 실패시 null. */
    val layer: String? = null,
    /** L1 실패 후 L3 로 에스컬레이션 한 경우 사유. */
    @SerialName("fallback_reason") val fallbackReason: String? = null,
    @SerialName("elapsed_ms") val elapsedMs: Long,
    @SerialName("png_url") val pngUrl: String? = null,
    @SerialName("cache_hit") val cacheHit: Boolean = false,
    @SerialName("aapt2_warm") val aapt2Warm: Boolean = false,
    /** "ready" | "booting" | "absent" | "unavailable" */
    @SerialName("avd_state") val avdState: String = "absent",
    @SerialName("request_id") val requestId: String,
    @SerialName("server_epoch") val serverEpoch: Long
) {
    enum class Status { SUCCESS, FALLBACK, UNRENDERABLE }
}

/**
 * 07 §4.2 디바이스 enum.
 * 값들은 stable — 제거나 rename 은 MAJOR 버전 bump.
 */
object DevicePreset {
    const val PHONE_SMALL = "phone_small"         // 360x640
    const val PHONE_NORMAL = "phone_normal"       // 412x892
    const val PHONE_LARGE = "phone_large"         // 480x960
    const val TABLET_7 = "tablet_7"               // 600x1024
    const val TABLET_10 = "tablet_10"             // 800x1280
    const val FOLDABLE_INNER = "foldable_inner"   // 673x841
    const val FOLDABLE_OUTER = "foldable_outer"   // 360x816
    const val TV_1080P = "tv_1080p"               // 1920x1080
    const val WEARABLE_ROUND = "wearable_round"   // 402x402

    val ALL: List<String> = listOf(
        PHONE_SMALL, PHONE_NORMAL, PHONE_LARGE,
        TABLET_7, TABLET_10,
        FOLDABLE_INNER, FOLDABLE_OUTER,
        TV_1080P, WEARABLE_ROUND
    )
}
