package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xmlpull.v1.XmlPullParser

/**
 * W3D4-β T12: MinimalLayoutlibCallback.getParser 의 color state list path 검증.
 *
 * - rv == null → null (방어).
 * - rv.resourceType != COLOR → null (LAYOUT/MENU/DRAWABLE 등 prior 동작 보존).
 * - rv 가 COLOR + lookup miss → null.
 * - rv 가 COLOR + lookup hit → ILayoutPullParser, START_TAG=selector + getLayoutNamespace=RES_AUTO.
 */
class MinimalLayoutlibCallbackColorParserTest
{

    private fun newCallback(lookup: (ResourceReference) -> String?): MinimalLayoutlibCallback =
        MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, lookup)

    private fun colorRv(name: String): ResourceValueImpl =
        ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, name),
            "@axp:color-state-list",
            null,
        )

    @Test
    fun `getParser - null layout resource → null`()
    {
        val cb = newCallback { _ -> error("lookup must not be called") }
        assertNull(cb.getParser(null))
    }

    @Test
    fun `getParser - LAYOUT type → null (prior 동작 보존)`()
    {
        val cb = newCallback { _ -> error("lookup must not be called for layout") }
        val rv = ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main"),
            "/path/to/activity_main.xml",
            null,
        )
        assertNull(cb.getParser(rv))
    }

    @Test
    fun `getParser - DRAWABLE type → null (prior 동작 보존, drawable selector 는 T12_5 escalation 대상)`()
    {
        val cb = newCallback { _ -> error("lookup must not be called for drawable") }
        val rv = ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "ic_foo"),
            "/path/to/ic_foo.xml",
            null,
        )
        assertNull(cb.getParser(rv))
    }

    @Test
    fun `getParser - COLOR + lookup miss → null`()
    {
        val cb = newCallback { _ -> null }
        assertNull(cb.getParser(colorRv("missing")))
    }

    @Test
    fun `getParser - COLOR + lookup hit → ILayoutPullParser fed selector XML`()
    {
        val rawXml = """<?xml version="1.0" encoding="utf-8"?>
            |<selector xmlns:android="http://schemas.android.com/apk/res/android">
            |    <item android:color="#ff0000" />
            |</selector>""".trimMargin()
        val cb = newCallback { ref ->
            if (ref.name == "m3_highlighted_text") rawXml else null
        }
        val parser = cb.getParser(colorRv("m3_highlighted_text"))
        assertNotNull(parser)
        val p = parser!!

        // selector 가 START_TAG 로 잡혀야 함.
        var event = p.next()
        // skip optional StartDocument-equivalent events
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT)
        {
            event = p.next()
        }
        assertEquals(XmlPullParser.START_TAG, event)
        assertEquals("selector", p.name)

        // ILayoutPullParser contract — getLayoutNamespace 가 RES_AUTO.
        assertEquals(ResourceNamespace.RES_AUTO, p.layoutNamespace)
        assertNull(p.viewCookie)
    }

    @Test
    fun `getParser - lookup hit 의 자식 item element 도 정상 파싱`()
    {
        val rawXml = """<?xml version="1.0" encoding="utf-8"?>
            |<selector xmlns:android="http://schemas.android.com/apk/res/android">
            |    <item android:color="#ff0000" android:state_pressed="true"/>
            |    <item android:color="#00ff00"/>
            |</selector>""".trimMargin()
        val cb = newCallback { _ -> rawXml }
        val parser = cb.getParser(colorRv("dummy"))
        assertNotNull(parser)
        val p = parser!!

        var itemCount = 0
        var event = p.next()
        while (event != XmlPullParser.END_DOCUMENT)
        {
            if (event == XmlPullParser.START_TAG && p.name == "item")
            {
                itemCount++
            }
            event = p.next()
        }
        assertEquals(2, itemCount, "두 <item> 자식 모두 파싱")
    }

    @Test
    fun `getParser - lookup miss 면 selector XML 안 fetch`()
    {
        var lookupCalls = 0
        val cb = newCallback { _ ->
            lookupCalls++
            null
        }
        cb.getParser(colorRv("foo"))
        assertEquals(1, lookupCalls, "ColorStateList lookup 한 번만 호출")
        assertTrue(true)  // sanity
    }
}
