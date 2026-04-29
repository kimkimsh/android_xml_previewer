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

    @Test
    fun `class file suffix 가 dot 으로 시작`() {
        assertTrue(ClassLoaderConstants.CLASS_FILE_SUFFIX.startsWith("."))
    }

    @Test
    fun `inner class separator 는 dollar`() {
        assertEquals('$', ClassLoaderConstants.INNER_CLASS_SEPARATOR)
    }

    @Test
    fun `R class name suffix 는 slash R`() {
        assertEquals("/R", ClassLoaderConstants.R_CLASS_NAME_SUFFIX)
    }

    @Test
    fun `internal name 과 external name separator + REWRITE_VERSION 형식`() {
        assertEquals('/', ClassLoaderConstants.INTERNAL_NAME_SEPARATOR)
        assertEquals('.', ClassLoaderConstants.EXTERNAL_NAME_SEPARATOR)
        assertTrue(ClassLoaderConstants.REWRITE_VERSION.matches(Regex("v[0-9]+")))
    }
}
