package dev.axp.http

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class PlaceholderPngRendererTest {

    private val renderer = PlaceholderPngRenderer()

    @Test
    fun `output bytes start with PNG magic`() {
        val bytes = renderer.renderPng("activity_basic.xml")
        // PNG signature RFC: 137 80 78 71 13 10 26 10
        val expected = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        assertArrayEquals(expected, bytes.copyOfRange(0, expected.size),
            "PNG 파일 시그니처 확인 실패")
    }

    @Test
    fun `output decodes to expected dimensions`() {
        val bytes = renderer.renderPng("activity_custom_view.xml")
        val img = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(PlaceholderPngConstants.PHONE_NORMAL_WIDTH_PX, img.width)
        assertEquals(PlaceholderPngConstants.PHONE_NORMAL_HEIGHT_PX, img.height)
    }

    @Test
    fun `output is non-trivially sized`() {
        val bytes = renderer.renderPng("activity_basic.xml")
        // 720x1280 RGBA + PNG 압축이라 최소 5KB 이상 (배지 + 본문 그려짐).
        assertTrue(bytes.size > 5_000, "PNG 출력 너무 작음 (배경만 그려진 것 의심): ${bytes.size}B")
    }
}
