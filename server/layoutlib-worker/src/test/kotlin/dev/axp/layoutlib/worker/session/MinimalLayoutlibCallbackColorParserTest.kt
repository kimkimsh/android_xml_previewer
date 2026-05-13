package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.resources.ResourceType
import dev.axp.layoutlib.worker.resources.AppLibraryResourceConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * MinimalLayoutlibCallback.getParser contract verification for the raw-XML feed
 * path covering ResourceType COLOR / ANIMATOR / DRAWABLE / INTERPOLATOR / LAYOUT.
 *
 *  - null layout resource yields null.
 *  - MENU and other unhandled types yield null without consulting any lookup.
 *  - COLOR / ANIMATOR / DRAWABLE / INTERPOLATOR / LAYOUT on lookup miss yield null.
 *  - COLOR / ANIMATOR / DRAWABLE / INTERPOLATOR / LAYOUT on lookup hit yield an
 *    ILayoutPullParser whose layoutNamespace is RES_AUTO and whose first
 *    START_TAG matches the raw body's root element.
 */
class MinimalLayoutlibCallbackColorParserTest
{

    private fun newCallback(lookup: (ResourceReference) -> String?): MinimalLayoutlibCallback =
        MinimalLayoutlibCallback({ ClassLoader.getSystemClassLoader() }, { /* no-op */ }, lookup, { null }, { null }, { null }, { null })

    private fun newCallbackWithDrawable(
        colorLookup: (ResourceReference) -> String?,
        drawableLookup: (ResourceReference) -> String?,
    ): MinimalLayoutlibCallback =
        MinimalLayoutlibCallback(
            { ClassLoader.getSystemClassLoader() },
            { /* no-op */ },
            colorLookup,
            { null },
            drawableLookup,
            { null },
            { null },
        )

    private fun newCallbackWithInterpolator(
        interpolatorLookup: (ResourceReference) -> String?,
    ): MinimalLayoutlibCallback =
        MinimalLayoutlibCallback(
            { ClassLoader.getSystemClassLoader() },
            { /* no-op */ },
            { null },
            { null },
            { null },
            interpolatorLookup,
            { null },
        )

    private fun newCallbackWithLayout(
        layoutLookup: (ResourceReference) -> String?,
    ): MinimalLayoutlibCallback =
        MinimalLayoutlibCallback(
            { ClassLoader.getSystemClassLoader() },
            { /* no-op */ },
            { null },
            { null },
            { null },
            { null },
            layoutLookup,
        )

    private fun colorRv(name: String): ResourceValueImpl =
        ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, name),
            "@axp:color-state-list",
            null,
        )

    @Test
    fun `getParser - null layout resource returns null`()
    {
        val cb = newCallback { _ -> error("lookup must not be called") }
        assertNull(cb.getParser(null))
    }

    @Test
    fun `getParser - MENU type returns null without consulting any lookup`()
    {
        val cb = newCallback { _ -> error("lookup must not be called for menu") }
        val rv = ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.MENU, "main_menu"),
            "/path/to/main_menu.xml",
            null,
        )
        assertNull(cb.getParser(rv))
    }

    @Test
    fun `getParser - DRAWABLE type + drawable lookup miss returns null`()
    {
        val cb = newCallbackWithDrawable(
            colorLookup = { _ -> error("color lookup must not be called for drawable") },
            drawableLookup = { _ -> null },
        )
        val rv = ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "ic_foo"),
            "/path/to/ic_foo.xml",
            null,
        )
        assertNull(cb.getParser(rv))
    }

    @Test
    fun `getParser - DRAWABLE type + drawable lookup hit feeds raw XML`()
    {
        val rawXml = """<?xml version="1.0" encoding="utf-8"?>
            |<vector xmlns:android="http://schemas.android.com/apk/res/android"
            |    android:width="24dp" android:height="24dp"
            |    android:viewportWidth="24" android:viewportHeight="24">
            |    <path android:fillColor="#000000" android:pathData="M12 2 L22 22 H2 Z"/>
            |</vector>""".trimMargin()
        val cb = newCallbackWithDrawable(
            colorLookup = { _ -> error("color lookup must not be called for drawable") },
            drawableLookup = { ref ->
                if (ref.name == "ic_m3_chip_close") rawXml else null
            },
        )
        val rv = ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "ic_m3_chip_close"),
            "@axp:drawable-xml",
            null,
        )
        val parser = cb.getParser(rv)
        assertNotNull(parser)
        val p = parser!!
        var event = p.next()
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT)
        {
            event = p.next()
        }
        assertEquals(XmlPullParser.START_TAG, event)
        assertEquals("vector", p.name)
        assertEquals(ResourceNamespace.RES_AUTO, p.layoutNamespace)
    }

    @Test
    fun `getParser - INTERPOLATOR type + interpolator lookup miss returns null`()
    {
        val cb = newCallbackWithInterpolator { _ -> null }
        val rv = ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.INTERPOLATOR, "m3_sys_motion_easing_emphasized"),
            "axp/interpolator/m3_sys_motion_easing_emphasized.xml",
            null,
        )
        assertNull(cb.getParser(rv))
    }

    @Test
    fun `getParser - INTERPOLATOR type + interpolator lookup hit feeds raw XML`()
    {
        val rawXml = """<?xml version="1.0" encoding="utf-8"?>
            |<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
            |    android:controlX1="0.2" android:controlY1="0"
            |    android:controlX2="0" android:controlY2="1"/>""".trimMargin()
        val cb = newCallbackWithInterpolator { ref ->
            if (ref.name == "m3_sys_motion_easing_emphasized") rawXml else null
        }
        val rv = ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.INTERPOLATOR, "m3_sys_motion_easing_emphasized"),
            "axp/interpolator/m3_sys_motion_easing_emphasized.xml",
            null,
        )
        val parser = cb.getParser(rv)
        assertNotNull(parser)
        val p = parser!!
        var event = p.next()
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT)
        {
            event = p.next()
        }
        assertEquals(XmlPullParser.START_TAG, event)
        assertEquals("pathInterpolator", p.name)
        assertEquals(ResourceNamespace.RES_AUTO, p.layoutNamespace)
    }

    @Test
    fun `getParser - LAYOUT type + layout lookup miss returns null`()
    {
        val cb = newCallbackWithLayout { _ -> null }
        val rv = ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "design_text_input_start_icon"),
            "axp/layout/design_text_input_start_icon.xml",
            null,
        )
        assertNull(cb.getParser(rv))
    }

    @Test
    fun `getParser - LAYOUT type + layout lookup hit feeds raw XML`()
    {
        val rawXml = """<?xml version="1.0" encoding="utf-8"?>
            |<com.google.android.material.internal.CheckableImageButton
            |    xmlns:android="http://schemas.android.com/apk/res/android"
            |    android:id="@+id/text_input_start_icon"
            |    android:visibility="gone"
            |    android:layout_width="wrap_content"
            |    android:layout_height="wrap_content"/>""".trimMargin()
        val cb = newCallbackWithLayout { ref ->
            if (ref.name == "design_text_input_start_icon") rawXml else null
        }
        val rv = ResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "design_text_input_start_icon"),
            "axp/layout/design_text_input_start_icon.xml",
            null,
        )
        val parser = cb.getParser(rv)
        assertNotNull(parser)
        val p = parser!!
        var event = p.next()
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT)
        {
            event = p.next()
        }
        assertEquals(XmlPullParser.START_TAG, event)
        assertEquals("com.google.android.material.internal.CheckableImageButton", p.name)
        assertEquals(ResourceNamespace.RES_AUTO, p.layoutNamespace)
    }

    @Test
    fun `getParser - COLOR + lookup miss returns null`()
    {
        val cb = newCallback { _ -> null }
        assertNull(cb.getParser(colorRv("missing")))
    }

    @Test
    fun `getParser - COLOR + lookup hit returns ILayoutPullParser fed selector XML`()
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

        // selector should surface as the first START_TAG.
        var event = p.next()
        // skip optional StartDocument-equivalent events
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT)
        {
            event = p.next()
        }
        assertEquals(XmlPullParser.START_TAG, event)
        assertEquals("selector", p.name)

        // ILayoutPullParser contract — getLayoutNamespace returns RES_AUTO.
        assertEquals(ResourceNamespace.RES_AUTO, p.layoutNamespace)
        assertNull(p.viewCookie)
    }

    @Test
    fun `getParser - lookup hit also parses child item elements`()
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
        assertEquals(2, itemCount, "both <item> children parsed")
    }

    @Test
    fun `getParser - lookup miss does not fetch selector XML twice`()
    {
        var lookupCalls = 0
        val cb = newCallback { _ ->
            lookupCalls++
            null
        }
        cb.getParser(colorRv("foo"))
        assertEquals(1, lookupCalls, "ColorStateList lookup invoked exactly once")
        assertTrue(true)  // sanity
    }

    @Test
    fun `createXmlParserForFile - non-placeholder file name does not log bypass`()
    {
        val cb = newCallback { _ -> null }
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            cb.createXmlParserForFile("/tmp/some-other-file.xml")
            assertFalse(
                errOut.toString().contains("createXmlParserForFile bypass"),
                "non-placeholder file name must NOT trigger the layout bypass diagnostic",
            )
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `createXmlParserForFile - axp slash layout placeholder logs bypass diagnostic`()
    {
        val cb = newCallback { _ -> null }
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val placeholder = AppLibraryResourceConstants.layoutPlaceholderValue("design_text_input_start_icon")
            cb.createXmlParserForFile(placeholder)
            assertTrue(
                errOut.toString().contains("createXmlParserForFile bypass"),
                "axp/layout/<name>.xml placeholder must trigger the bypass diagnostic for BridgeInflater 2-arg path",
            )
        }
        finally
        {
            System.setErr(origErr)
        }
    }
}
