package dev.axp.protocol.render

/**
 * Layout name → PNG bytes abstraction.
 *
 * W2D6-FATJAR: moved to :protocol so both :http-server (PlaceholderPngRenderer)
 * and :layoutlib-worker (LayoutlibRenderer) can implement without a circular
 * module dependency (08 §7.7 blocker #3).
 */
interface PngRenderer {
    fun renderPng(layoutName: String): ByteArray
}
