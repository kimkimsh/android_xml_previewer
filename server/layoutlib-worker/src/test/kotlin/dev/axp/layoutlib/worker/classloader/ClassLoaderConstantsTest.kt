package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClassLoaderConstantsTest {

    @Test
    fun `manifest path 는 module-relative 이고 trailing 없음`() {
        assertEquals("app/build/axp/runtime-classpath.txt", ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        assertTrue(!ClassLoaderConstants.MANIFEST_RELATIVE_PATH.startsWith("/"))
    }

    @Test
    fun `aar 와 jar 확장자가 dot 으로 시작`() {
        assertTrue(ClassLoaderConstants.AAR_EXTENSION.startsWith("."))
        assertTrue(ClassLoaderConstants.JAR_EXTENSION.startsWith("."))
        assertTrue(ClassLoaderConstants.TEMP_JAR_SUFFIX.startsWith("."))
    }

    @Test
    fun `R_JAR 경로가 module-relative + 정확한 AGP 8 layout`() {
        val expected = "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
        assertEquals(expected, ClassLoaderConstants.R_JAR_RELATIVE_PATH)
    }
}
