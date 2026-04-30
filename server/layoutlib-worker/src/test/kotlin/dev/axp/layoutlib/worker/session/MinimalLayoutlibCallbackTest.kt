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
 * W2D7-RENDERSESSION — MinimalLayoutlibCallback 의 resource id 양방향 맵 + 기본값 계약 검증.
 *
 * LayoutlibCallback 은 abstract class. Bridge 내부가 callback.getOrGenerateResourceId 로 int
 * 를 받고 나중에 callback.resolveResourceId 로 역참조. 양방향 stable mapping 이 필수.
 */
class MinimalLayoutlibCallbackTest {

    @Test
    fun `getOrGenerateResourceId returns stable id across calls`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null })
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
        val first = cb.getOrGenerateResourceId(ref)
        val second = cb.getOrGenerateResourceId(ref)
        assertEquals(first, second)
    }

    @Test
    fun `different references get different ids`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null })
        val title = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
        val body = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "body")
        assertNotEquals(cb.getOrGenerateResourceId(title), cb.getOrGenerateResourceId(body))
    }

    @Test
    fun `resolveResourceId returns registered reference`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null })
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
        val id = cb.getOrGenerateResourceId(ref)
        assertEquals(ref, cb.resolveResourceId(id))
    }

    @Test
    fun `resolveResourceId returns null for unknown id`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null })
        assertNull(cb.resolveResourceId(0x7F999999))
    }

    @Test
    fun `getAdapterBinding is null`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null })
        assertNull(cb.getAdapterBinding(Any(), emptyMap()))
    }

    @Test
    fun `getActionBarCallback is non-null`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null })
        assertNotNull(cb.getActionBarCallback())
    }

    @Test
    fun `getParser returns null for any resource value`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null })
        assertNull(cb.getParser(null))
    }

    @Test
    fun `applicationId is stable axp token`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, { null })
        assertEquals("axp.render", cb.applicationId)
    }
}
