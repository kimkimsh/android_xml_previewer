package dev.axp.layoutlib.worker.session

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.lang.reflect.InvocationTargetException

class MinimalLayoutlibCallbackLoadViewTest {

    /**
     * 호출 추적용 ClassLoader — 어떤 클래스가 어느 provider 로부터 요청됐는지 검증.
     * StringBuilder 같은 bootstrap 클래스를 직접 쓰면 cls.classLoader == null 이라 비교가 깨짐.
     */
    private class TrackingClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        val requested = mutableListOf<String>()
        override fun loadClass(name: String): Class<*> {
            requested += name
            return super.loadClass(name)
        }
    }

    private fun newCallback(cl: ClassLoader): MinimalLayoutlibCallback =
        MinimalLayoutlibCallback({ cl }, { /* no-op */ })

    @Test
    fun `loadView — provider CL 로 위임 + 정상 instantiate`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        val v = cb.loadView("java.lang.StringBuilder", arrayOf(CharSequence::class.java), arrayOf<Any>("hi"))
        assertNotNull(v)
        assertEquals("hi", v.toString())
        assertTrue("java.lang.StringBuilder" in cl.requested, "provider CL 호출 기록: ${cl.requested}")
    }

    @Test
    fun `loadView — 미지 클래스 ClassNotFoundException pass-through`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        assertThrows<ClassNotFoundException> {
            cb.loadView("does.not.Exist", arrayOf(), arrayOf())
        }
    }

    @Test
    fun `loadView — InvocationTargetException 의 cause 가 unwrap 되어 throw`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        val ex = assertThrows<IllegalArgumentException> {
            cb.loadView("java.util.ArrayList", arrayOf(Int::class.javaPrimitiveType!!), arrayOf<Any>(-1))
        }
        assertTrue(ex !is InvocationTargetException)
    }

    @Test
    fun `findClass — provider CL 로 위임`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        val cls = cb.findClass("java.lang.StringBuilder")
        assertEquals(java.lang.StringBuilder::class.java, cls)
        assertTrue("java.lang.StringBuilder" in cl.requested)
    }

    @Test
    fun `hasAndroidXAppCompat — true`() {
        val cb = newCallback(ClassLoader.getSystemClassLoader())
        assertTrue(cb.hasAndroidXAppCompat())
    }
}
