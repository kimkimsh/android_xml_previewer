package dev.axp.layoutlib.worker.resources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLibraryResourceConstantsTest
{

    @Test
    fun `RUNTIME_CLASSPATH_TXT_PATH 가 W3D3 manifest 와 일치`()
    {
        assertEquals("app/build/axp/runtime-classpath.txt", AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
    }

    @Test
    fun `MANIFEST_PACKAGE_REGEX 가 일반 AndroidManifest 의 package 추출`()
    {
        val sample = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.google.android.material" />"""
        val match = AppLibraryResourceConstants.MANIFEST_PACKAGE_REGEX.find(sample)
        assertTrue(match != null, "regex 가 매칭")
        assertEquals("com.google.android.material", match!!.groupValues[1])
    }

    @Test
    fun `MAX_REF_HOPS 와 MAX_THEME_HOPS 가 안전 범위`()
    {
        assertTrue(AppLibraryResourceConstants.MAX_REF_HOPS in 5..50, "ref hop limit 합리적")
        // v2 round 2 follow-up #5: 실측 chain depth = 17 edges (themes_holo.xml 정상 포함 시),
        // ThemeOverlay 패턴 적용 시 추가 5-10 overlay 추가 가능 → 32 마진.
        assertTrue(AppLibraryResourceConstants.MAX_THEME_HOPS >= 30, "theme hop limit ≥ 30 (v2 보강)")
        assertTrue(AppLibraryResourceConstants.MAX_THEME_HOPS in 30..100, "상한도 합리적")
    }

    @Test
    fun `RES_VALUE_NULL_LITERAL 와 RES_VALUE_EMPTY_LITERAL 가 sentinel string 정의`()
    {
        // v2 round 2 follow-up #3: 27 AAR 안 @null 106개, @empty 2개 출현 (Codex Q1 측정값).
        assertEquals("@null", AppLibraryResourceConstants.RES_VALUE_NULL_LITERAL)
        assertEquals("@empty", AppLibraryResourceConstants.RES_VALUE_EMPTY_LITERAL)
    }

    @Test
    fun `ANDROID_NS_PREFIX 가 'android' 로 통일 + NS_NAME_SEPARATOR 가 콜론`()
    {
        // v2 round 2 follow-up #6: android: style parent normalization 의 prefix + separator 비교용.
        assertEquals("android", AppLibraryResourceConstants.ANDROID_NS_PREFIX)
        assertEquals(":", AppLibraryResourceConstants.NS_NAME_SEPARATOR)
    }
}
