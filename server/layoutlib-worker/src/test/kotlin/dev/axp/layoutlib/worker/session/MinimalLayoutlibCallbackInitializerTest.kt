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
    fun `initializer-registered ref and id are bidirectionally lookupable`() {
        val seededRef = ResourceReference(
            ResourceNamespace.fromPackageName("com.example"),
            ResourceType.ATTR,
            "myAttr",
        )
        val cb = MinimalLayoutlibCallback(
            { ClassLoader.getSystemClassLoader() },
            { register -> register(seededRef, 0x7F010001) },
            { null },
            { null },
            { null },
            { null },
            { null },
        )
        assertEquals(seededRef, cb.resolveResourceId(0x7F010001))
        assertEquals(0x7F010001, cb.getOrGenerateResourceId(seededRef))
    }

    @Test
    fun `getOrGenerateResourceId advances above the seeded high id`() {
        val cb = MinimalLayoutlibCallback(
            { ClassLoader.getSystemClassLoader() },
            { register ->
                register(
                    ResourceReference(ResourceNamespace.fromPackageName("p"), ResourceType.ID, "seedHigh"),
                    0x7F900000,
                )
            },
            { null },
            { null },
            { null },
            { null },
            { null },
        )
        val newRef = ResourceReference(ResourceNamespace.fromPackageName("p"), ResourceType.ID, "fresh")
        val newId = cb.getOrGenerateResourceId(newRef)
        assertTrue(newId > 0x7F900000, "fresh id ($newId) > seed (0x7F900000)")
    }

    @Test
    fun `initializer throw is wrapped as IllegalStateException`() {
        val ex = assertThrows<IllegalStateException> {
            MinimalLayoutlibCallback(
                { ClassLoader.getSystemClassLoader() },
                { _ -> error("simulated R jar I O failure") },
                { null },
                { null },
                { null },
                { null },
                { null },
            )
        }
        assertTrue(ex.message!!.contains("R.jar"), "message must mention R.jar: ${ex.message}")
    }
}
