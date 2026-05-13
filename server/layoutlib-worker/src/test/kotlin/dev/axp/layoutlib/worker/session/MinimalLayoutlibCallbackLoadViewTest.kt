package dev.axp.layoutlib.worker.session

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.lang.reflect.InvocationTargetException

class MinimalLayoutlibCallbackLoadViewTest {

    /**
     * Tracking ClassLoader for verifying which class loads were dispatched
     * through which provider. Bootstrap-loaded classes such as StringBuilder
     * report a null classLoader, so a tracking subclass intercepts loadClass
     * to record requests without breaking comparison.
     */
    private class TrackingClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        val requested = mutableListOf<String>()
        override fun loadClass(name: String): Class<*> {
            requested += name
            return super.loadClass(name)
        }
    }

    private fun newCallback(cl: ClassLoader): MinimalLayoutlibCallback =
        MinimalLayoutlibCallback({ cl }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null })

    @Test
    fun `loadView delegates to provider classloader and instantiates`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        val v = cb.loadView("java.lang.StringBuilder", arrayOf(CharSequence::class.java), arrayOf<Any>("hi"))
        assertNotNull(v)
        assertEquals("hi", v.toString())
        assertTrue("java.lang.StringBuilder" in cl.requested, "provider CL load record: ${cl.requested}")
    }

    @Test
    fun `loadView surfaces ClassNotFoundException for unknown class`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        assertThrows<ClassNotFoundException> {
            cb.loadView("does.not.Exist", arrayOf(), arrayOf())
        }
    }

    @Test
    fun `loadView unwraps InvocationTargetException to its cause`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        val ex = assertThrows<IllegalArgumentException> {
            cb.loadView("java.util.ArrayList", arrayOf(Int::class.javaPrimitiveType!!), arrayOf<Any>(-1))
        }
        assertTrue(ex !is InvocationTargetException)
    }

    @Test
    fun `findClass delegates to provider classloader`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        val cls = cb.findClass("java.lang.StringBuilder")
        assertEquals(java.lang.StringBuilder::class.java, cls)
        assertTrue("java.lang.StringBuilder" in cl.requested)
    }

    @Test
    fun `hasAndroidXAppCompat is true`() {
        val cb = newCallback(ClassLoader.getSystemClassLoader())
        assertTrue(cb.hasAndroidXAppCompat())
    }
}
