package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * W3D4 §7.1 (R-1): R$style 의 underscore name → XML dot name 변환 verify.
 * R$attr 등은 underscore 보존 (정책 회귀 방지).
 */
class RNameCanonicalizationTest
{

    @Test
    fun `R style underscore name 이 dot name 으로 변환`()
    {
        assertEquals("Theme.AxpFixture", RNameCanonicalization.styleNameToXml("Theme_AxpFixture"))
        assertEquals(
            "Theme.Material3.DayNight.NoActionBar",
            RNameCanonicalization.styleNameToXml("Theme_Material3_DayNight_NoActionBar"),
        )
    }

    @Test
    fun `R attr name 은 underscore 보존 (R attr 는 dot 없음)`()
    {
        // attr 은 single-word 가 일반적이지만 underscore 보존이 정책
        assertEquals("colorPrimary", RNameCanonicalization.attrName("colorPrimary"))
        assertEquals("max_lines", RNameCanonicalization.attrName("max_lines"))
    }

    @Test
    fun `R style edge case — underscore 없는 name 그대로`()
    {
        assertEquals("Theme", RNameCanonicalization.styleNameToXml("Theme"))
        assertEquals("Widget", RNameCanonicalization.styleNameToXml("Widget"))
    }
}
