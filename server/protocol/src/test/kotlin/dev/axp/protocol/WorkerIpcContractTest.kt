package dev.axp.protocol

import dev.axp.protocol.render.RenderRequest
import dev.axp.protocol.worker.WorkerErrorPayload
import dev.axp.protocol.worker.WorkerFrame
import dev.axp.protocol.worker.WorkerRequest
import dev.axp.protocol.worker.WorkerResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 08 §3.1 Worker IPC contract.
 *
 * 이 테스트가 검증하는 contract:
 *   1. WorkerRequest / WorkerResponse sealed class 가 polymorphic JSON 으로 round-trip
 *   2. frame header 가 4바이트 little-endian u32 로 남음 (양단 합의)
 *   3. MAX_FRAME_SIZE 상한이 WorkerFrame 에 상수로 존재
 *   4. socket 파일명 패턴이 device 식별자를 정확히 포함
 */
class WorkerIpcContractTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `WorkerRequest Render round-trips via polymorphic JSON`() {
        val req = WorkerRequest.Render(
            RenderRequest(
                xmlPath = "app/src/main/res/layout/activity_main.xml",
                requestId = "req_01HXYZ",
                clientEpoch = 42
            )
        )
        val encoded = json.encodeToString(WorkerRequest.serializer(), req)
        assertTrue(encoded.contains("\"type\":\"Render\""), "class discriminator 가 type 필드로 표기되어야 함: $encoded")

        val decoded = json.decodeFromString(WorkerRequest.serializer(), encoded) as WorkerRequest.Render
        assertEquals(req.req.xmlPath, decoded.req.xmlPath)
        assertEquals(req.req.requestId, decoded.req.requestId)
    }

    @Test
    fun `WorkerRequest singleton objects (Shutdown, Ping) round-trip`() {
        val ping = WorkerRequest.Ping
        val shutdown = WorkerRequest.Shutdown

        val pingJson = json.encodeToString(WorkerRequest.serializer(), ping)
        val shutdownJson = json.encodeToString(WorkerRequest.serializer(), shutdown)

        assertEquals(ping, json.decodeFromString(WorkerRequest.serializer(), pingJson))
        assertEquals(shutdown, json.decodeFromString(WorkerRequest.serializer(), shutdownJson))
    }

    @Test
    fun `WorkerResponse RenderOk with metrics round-trips`() {
        val resp = WorkerResponse.RenderOk(
            pngPath = "/tmp/axp/renders/abc.png",
            elapsedMs = 830,
            metrics = mapOf("session_reuse" to "true", "font_worker" to "intl")
        )
        val encoded = json.encodeToString(WorkerResponse.serializer(), resp)
        val decoded = json.decodeFromString(WorkerResponse.serializer(), encoded) as WorkerResponse.RenderOk
        assertEquals(resp.pngPath, decoded.pngPath)
        assertEquals(resp.elapsedMs, decoded.elapsedMs)
        assertEquals(resp.metrics["font_worker"], decoded.metrics["font_worker"])
    }

    @Test
    fun `WorkerResponse RenderErr carries WorkerErrorPayload`() {
        val payload = WorkerErrorPayload(
            code = "AXP-L1-002",
            category = "CUSTOM_VIEW",
            message = "com.acme.FancyChart 클래스를 찾을 수 없음"
        )
        val resp = WorkerResponse.RenderErr(payload)

        val encoded = json.encodeToString(WorkerResponse.serializer(), resp)
        val decoded = json.decodeFromString(WorkerResponse.serializer(), encoded) as WorkerResponse.RenderErr

        assertEquals("AXP-L1-002", decoded.error.code)
        assertEquals("CUSTOM_VIEW", decoded.error.category)
    }

    @Test
    fun `frame header is exactly 4 bytes`() {
        // 양단 합의 — u32 LE. 이 상수가 변하면 모든 워커/부모 바이너리가 incompatible.
        assertEquals(4, WorkerFrame.HEADER_BYTES)
    }

    @Test
    fun `max frame size is 2 MiB`() {
        // PNG 경로 + 메타만 흐르므로 충분. 이 한계가 변하면 DoS 방지 정책 재검토 필요.
        assertEquals(2 * 1024 * 1024, WorkerFrame.MAX_FRAME_SIZE)
    }

    @Test
    fun `socket file name encodes device id`() {
        assertEquals("worker-phone_normal.sock", WorkerFrame.socketFileName("phone_normal"))
        assertEquals("worker-tablet_10.sock", WorkerFrame.socketFileName("tablet_10"))
    }
}
