package dev.axp.layoutlib.worker.classloader

import com.android.resources.ResourceType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class RJarTypeMappingTest {

    @Test
    fun `attr style layout 매핑`() {
        assertEquals(ResourceType.ATTR, RJarTypeMapping.fromSimpleName("attr"))
        assertEquals(ResourceType.STYLE, RJarTypeMapping.fromSimpleName("style"))
        assertEquals(ResourceType.LAYOUT, RJarTypeMapping.fromSimpleName("layout"))
    }

    @Test
    fun `styleable 매핑 존재`() {
        assertEquals(ResourceType.STYLEABLE, RJarTypeMapping.fromSimpleName("styleable"))
    }

    @Test
    fun `알 수 없는 simpleName 은 null`() {
        assertNull(RJarTypeMapping.fromSimpleName("unknown_type"))
        assertNull(RJarTypeMapping.fromSimpleName(""))
    }
}
