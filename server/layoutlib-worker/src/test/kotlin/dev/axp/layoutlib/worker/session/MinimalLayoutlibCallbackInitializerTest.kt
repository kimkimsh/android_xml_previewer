package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class MinimalLayoutlibCallbackInitializerTest {

    @Test
    fun `initializer 가 등록한 ref 와 id 가 양방향 lookup 가능`() {
        val seededRef = ResourceReference(
            ResourceNamespace.fromPackageName("com.example"),
            ResourceType.ATTR,
            "myAttr",
        )
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }) { register ->
            register(seededRef, 0x7F010001)
        }
        assertEquals(seededRef, cb.resolveResourceId(0x7F010001))
        assertEquals(0x7F010001, cb.getOrGenerateResourceId(seededRef))
    }

    @Test
    fun `getOrGenerateResourceId 가 seed 된 high id 위로 증가`() {
        val cb = MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }) { register ->
            register(
                ResourceReference(ResourceNamespace.fromPackageName("p"), ResourceType.ID, "seedHigh"),
                0x7F900000,
            )
        }
        val newRef = ResourceReference(ResourceNamespace.fromPackageName("p"), ResourceType.ID, "fresh")
        val newId = cb.getOrGenerateResourceId(newRef)
        assertTrue(newId > 0x7F900000, "fresh id ($newId) > seed (0x7F900000)")
    }

    @Test
    fun `initializer 가 throw 하면 IllegalStateException 으로 wrap`() {
        val ex = assertThrows<IllegalStateException> {
            MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }) { _ ->
                error("simulated R jar I O failure")
            }
        }
        assertTrue(ex.message!!.contains("R.jar"), "메시지에 R.jar 포함: ${ex.message}")
    }
}
