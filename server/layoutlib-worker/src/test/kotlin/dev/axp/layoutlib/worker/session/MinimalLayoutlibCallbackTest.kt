package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Verifies MinimalLayoutlibCallback's resource id bidirectional map plus the
 * default contracts on the abstract LayoutlibCallback overrides. Bridge calls
 * callback.getOrGenerateResourceId to mint an int, then callback.resolveResourceId
 * to dereference it later — stable bidirectional mapping is the load-bearing
 * invariant.
 */
class MinimalLayoutlibCallbackTest {

    @Test
    fun `getOrGenerateResourceId returns stable id across calls`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null })
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
        val first = cb.getOrGenerateResourceId(ref)
        val second = cb.getOrGenerateResourceId(ref)
        assertEquals(first, second)
    }

    @Test
    fun `different references get different ids`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null })
        val title = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
        val body = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "body")
        assertNotEquals(cb.getOrGenerateResourceId(title), cb.getOrGenerateResourceId(body))
    }

    @Test
    fun `resolveResourceId returns registered reference`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null })
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
        val id = cb.getOrGenerateResourceId(ref)
        assertEquals(ref, cb.resolveResourceId(id))
    }

    @Test
    fun `resolveResourceId returns null for unknown id`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null })
        assertNull(cb.resolveResourceId(0x7F999999))
    }

    @Test
    fun `getAdapterBinding is null`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null })
        assertNull(cb.getAdapterBinding(Any(), emptyMap()))
    }

    @Test
    fun `getActionBarCallback is non-null`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null })
        assertNotNull(cb.getActionBarCallback())
    }

    @Test
    fun `getParser returns null for any resource value`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null })
        assertNull(cb.getParser(null))
    }

    @Test
    fun `applicationId is stable axp token`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null }, { null }, { null }, { null }, { null })
        assertEquals("axp.render", cb.applicationId)
    }
}
