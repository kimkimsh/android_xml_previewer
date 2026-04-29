package dev.axp.protocol

import dev.axp.protocol.error.UnrenderableReason
import dev.axp.protocol.mcp.Capabilities
import dev.axp.protocol.mcp.ErrorEnvelope
import dev.axp.protocol.mcp.McpEnvelope
import dev.axp.protocol.render.RenderResponseData
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 08 §5 item 8 + 07 §4.1 — MCP envelope 직렬화/역직렬화 contract test.
 *
 * 목적:
 *   - envelope `schema_version` / `server_version` / `capabilities` 필드명이 canonical JSON 그대로 유지
 *   - 에러 응답은 `data=null, error!=null` 유효, 성공 응답은 그 반대
 *   - ErrorEnvelope.code 와 UnrenderableReason.code round-trip
 */
class McpEnvelopeSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun `success envelope round-trips with snake_case keys`() {
        val original = McpEnvelope(
            schemaVersion = Versions.SCHEMA_VERSION,
            serverVersion = Versions.SERVER_VERSION,
            capabilities = setOf(Capabilities.RENDER_L1, Capabilities.SSE_FULL_TAXONOMY),
            data = RenderResponseData(
                status = RenderResponseData.Status.SUCCESS,
                renderId = "af42b8c1",
                layer = "layoutlib",
                elapsedMs = 830,
                pngUrl = "/preview?layout=activity_main.xml&v=af42b8c1",
                cacheHit = false,
                aapt2Warm = true,
                avdState = "ready",
                requestId = "req_01HXYZ",
                serverEpoch = 42
            )
        )

        val encoded = json.encodeToString(McpEnvelope.serializer(RenderResponseData.serializer()), original)

        // canonical JSON 필드명 검증 — 07 §4.1 스펙 준수
        assertTrue(encoded.contains("\"schema_version\""),
            "schema_version 필드가 snake_case 로 인코딩되지 않음: $encoded")
        assertTrue(encoded.contains("\"server_version\""),
            "server_version 필드가 snake_case 로 인코딩되지 않음: $encoded")
        assertTrue(encoded.contains("\"png_url\""),
            "png_url 필드가 snake_case 로 인코딩되지 않음: $encoded")
        assertTrue(encoded.contains("\"cache_hit\""),
            "cache_hit 필드가 snake_case 로 인코딩되지 않음: $encoded")

        val decoded = json.decodeFromString(McpEnvelope.serializer(RenderResponseData.serializer()), encoded)
        assertEquals(original.schemaVersion, decoded.schemaVersion)
        assertEquals(original.serverVersion, decoded.serverVersion)
        assertEquals(original.capabilities, decoded.capabilities)
        assertEquals(original.data?.renderId, decoded.data?.renderId)
        assertEquals(original.data?.pngUrl, decoded.data?.pngUrl)
        assertNull(decoded.error)
    }

    @Test
    fun `error envelope preserves UnrenderableReason code`() {
        val reason = UnrenderableReason.L3_NO_APP_APK_BUILT
        val error = ErrorEnvelope(
            code = reason.code,
            category = reason.category.name,
            message = "app APK 가 빌드되지 않음",
            detail = "./app/build/outputs/apk/debug/ 디렉토리 없음",
            remediation = "./gradlew :app:assembleDebug",
            remediationUrl = "docs/TROUBLESHOOTING.md${reason.docsAnchor}",
            retriable = false
        )
        val envelope = McpEnvelope<RenderResponseData>(
            schemaVersion = Versions.SCHEMA_VERSION,
            serverVersion = Versions.SERVER_VERSION,
            capabilities = setOf(Capabilities.RENDER_L3),
            data = null,
            error = error
        )

        val encoded = json.encodeToString(McpEnvelope.serializer(RenderResponseData.serializer()), envelope)
        val decoded = json.decodeFromString(McpEnvelope.serializer(RenderResponseData.serializer()), encoded)

        assertNull(decoded.data, "에러 envelope 은 data=null 이어야 함")
        assertNotNull(decoded.error, "에러 envelope 은 error 가 채워져야 함")
        assertEquals("AXP-L3-001", decoded.error?.code)
        // code → enum reverse lookup
        assertEquals(reason, UnrenderableReason.fromCode(decoded.error?.code ?: ""))
    }
}
