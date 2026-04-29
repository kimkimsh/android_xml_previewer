package dev.axp.http

import dev.axp.protocol.render.PngRenderer
import dev.axp.http.PlaceholderPngConstants.ACCENT_RGB
import dev.axp.http.PlaceholderPngConstants.BADGE_HEIGHT_PX
import dev.axp.http.PlaceholderPngConstants.BADGE_PADDING_PX
import dev.axp.http.PlaceholderPngConstants.BEZEL_PX
import dev.axp.http.PlaceholderPngConstants.BEZEL_RGB
import dev.axp.http.PlaceholderPngConstants.BG_RGB
import dev.axp.http.PlaceholderPngConstants.BODY_FONT_PX
import dev.axp.http.PlaceholderPngConstants.FONT_FAMILY_SANS
import dev.axp.http.PlaceholderPngConstants.GAP_AFTER_BADGE_PX
import dev.axp.http.PlaceholderPngConstants.HEADLESS_PROPERTY_KEY
import dev.axp.http.PlaceholderPngConstants.HEADLESS_PROPERTY_VALUE
import dev.axp.http.PlaceholderPngConstants.IMAGE_FORMAT_PNG
import dev.axp.http.PlaceholderPngConstants.LINE_HEIGHT_PX
import dev.axp.http.PlaceholderPngConstants.META_FONT_PX
import dev.axp.http.PlaceholderPngConstants.MUTED_RGB
import dev.axp.http.PlaceholderPngConstants.PHONE_NORMAL_HEIGHT_PX
import dev.axp.http.PlaceholderPngConstants.PHONE_NORMAL_WIDTH_PX
import dev.axp.http.PlaceholderPngConstants.TEXT_RGB
import dev.axp.http.PlaceholderPngConstants.TITLE_FONT_PX
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Week 1 placeholder PNG generator.
 *
 * 08 §7.6 (W1D5-R4 escape hatch): layoutlib 실제 렌더가 W2 fatJar 작업 전까지 막혀있어 (transitive Guava
 * 누락) /preview 응답으로 placeholder PNG 를 반환. infrastructure path (HTTP + SSE + viewer) 는 W1 에
 * 검증되고 실제 렌더는 W2 D6 부터 자연 활성화.
 *
 * 레이아웃: 베젤 + 상단 강조 배지(layout 명) + 본문 다중 라인 (상태 설명).
 * 디바이스 프리셋: phone_normal 720x1280 (xhdpi). v1 단일.
 */
class PlaceholderPngRenderer : PngRenderer {

    private val width: Int = PHONE_NORMAL_WIDTH_PX
    private val height: Int = PHONE_NORMAL_HEIGHT_PX

    init {
        // headless 환경(서버, CI)에서 AWT 가 X 디스플레이를 시도하지 않도록 강제.
        System.setProperty(HEADLESS_PROPERTY_KEY, HEADLESS_PROPERTY_VALUE)
    }

    /**
     * `layoutName` 을 강조 배지에 표시하는 placeholder PNG 의 byte array 를 반환.
     * 멀티 라인 본문은 W1 의 escape-hatch 사실을 사용자에게 명시적으로 알림.
     */
    override fun renderPng(layoutName: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            paintBackground(g)
            paintBezel(g)
            paintBadge(g, layoutName)
            paintBody(g, layoutName)
        }
        finally {
            g.dispose()
        }
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, IMAGE_FORMAT_PNG, baos)
        return baos.toByteArray()
    }

    private fun paintBackground(g: java.awt.Graphics2D) {
        g.color = Color(BG_RGB, true)
        g.fillRect(0, 0, width, height)
    }

    private fun paintBezel(g: java.awt.Graphics2D) {
        g.color = Color(BEZEL_RGB, true)
        // 상하좌우 두께 BEZEL_PX 의 사각 외곽선.
        g.fillRect(0, 0, width, BEZEL_PX)
        g.fillRect(0, height - BEZEL_PX, width, BEZEL_PX)
        g.fillRect(0, 0, BEZEL_PX, height)
        g.fillRect(width - BEZEL_PX, 0, BEZEL_PX, height)
    }

    private fun paintBadge(g: java.awt.Graphics2D, layoutName: String) {
        val badgeTop = BEZEL_PX
        g.color = Color(ACCENT_RGB, true)
        g.fillRect(BEZEL_PX, badgeTop, width - BEZEL_PX * 2, BADGE_HEIGHT_PX)

        g.color = Color.WHITE
        g.font = Font(FONT_FAMILY_SANS, Font.BOLD, TITLE_FONT_PX)
        val fmTitle = g.fontMetrics
        val titleY = badgeTop + (BADGE_HEIGHT_PX + fmTitle.ascent - fmTitle.descent) / 2
        g.drawString(layoutName, BADGE_PADDING_PX + BEZEL_PX, titleY)
    }

    private fun paintBody(g: java.awt.Graphics2D, layoutName: String) {
        val startX = BEZEL_PX + BADGE_PADDING_PX
        var y = BEZEL_PX + BADGE_HEIGHT_PX + GAP_AFTER_BADGE_PX

        // 본문 첫 라인 — 상태 메시지.
        g.color = Color(TEXT_RGB, true)
        g.font = Font(FONT_FAMILY_SANS, Font.BOLD, BODY_FONT_PX)
        g.drawString("L1 layoutlib render — pending", startX, y)
        y += LINE_HEIGHT_PX

        // 보조 설명.
        g.color = Color(MUTED_RGB, true)
        g.font = Font(FONT_FAMILY_SANS, Font.PLAIN, META_FONT_PX)
        for (line in PLACEHOLDER_BODY_LINES) {
            g.drawString(line, startX, y)
            y += LINE_HEIGHT_PX
        }

        // 메타 정보 — layout 경로 + 디바이스.
        y += LINE_HEIGHT_PX
        g.color = Color(TEXT_RGB, true)
        g.font = Font(FONT_FAMILY_SANS, Font.PLAIN, META_FONT_PX)
        g.drawString("layout: $layoutName", startX, y); y += LINE_HEIGHT_PX
        g.drawString("device: phone_normal (720x1280, xhdpi)", startX, y); y += LINE_HEIGHT_PX
        g.drawString("mode: L1 placeholder (Week 1 exit infra check)", startX, y)
    }

    private companion object {
        // 본문 안내문. CLAUDE.md 의 magic-string 정책에 따라 객체 멤버로 분리.
        private val PLACEHOLDER_BODY_LINES = listOf(
            "Week 1 Day 5 — HTTP + SSE + viewer infra OK.",
            "Bridge.init blocked on transitive Guava (W1D4-R3 SKIP).",
            "Resolution: W2 fatJar bundles all transitive deps.",
            "Then this PNG becomes the real layoutlib render.",
            "See docs/plan/08 §7.6 for escape-hatch rationale."
        )
    }
}
