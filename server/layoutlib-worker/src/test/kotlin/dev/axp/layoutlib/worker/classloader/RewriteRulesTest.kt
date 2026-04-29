package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class RewriteRulesTest {

    @Test
    fun `NAME_MAP 은 정확히 4 entries (Build family)`() {
        assertEquals(4, RewriteRules.NAME_MAP.size)
        assertTrue(RewriteRules.NAME_MAP.keys.all { it.startsWith("android/os/Build") })
    }

    @Test
    fun `NAME_MAP 의 모든 value 가 _Original_Build prefix`() {
        assertTrue(RewriteRules.NAME_MAP.values.all { it.contains("_Original_Build") })
    }

    @Test
    fun `REMAPPER 가 매핑된 이름은 변환`() {
        assertEquals("android/os/_Original_Build", RewriteRules.REMAPPER.map("android/os/Build"))
        assertEquals(
            "android/os/_Original_Build\$VERSION",
            RewriteRules.REMAPPER.map("android/os/Build\$VERSION"),
        )
    }

    @Test
    fun `REMAPPER 가 매핑되지 않은 이름은 그대로`() {
        assertEquals("java/lang/String", RewriteRules.REMAPPER.map("java/lang/String"))
        assertEquals("android/view/SurfaceView", RewriteRules.REMAPPER.map("android/view/SurfaceView"))
    }

    @Test
    fun `REMAPPER 가 21개 dual-publish 클래스 변경 안 함 (round 2 A1 fix)`() {
        val nonRewriteCases = listOf(
            "android/view/SurfaceView",
            "android/webkit/WebView",
            "android/os/ServiceManager",
            "android/view/WindowManagerImpl",
            "android/view/textservice/TextServicesManager",
        )
        for (cls in nonRewriteCases)
        {
            assertEquals(cls, RewriteRules.REMAPPER.map(cls), "should NOT rewrite: $cls")
        }
    }
}
