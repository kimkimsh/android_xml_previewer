package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.nio.file.Path

/**
 * W2D7-RENDERSESSION — LayoutPullParserAdapter 가 ILayoutPullParser 계약을 만족하는지 검증.
 *
 * ILayoutPullParser = XmlPullParser + getViewCookie() + getLayoutNamespace().
 * layoutlib 이 SessionParams.layoutDescription 으로 본 어댑터를 소비하여 View 계층을 inflate.
 */
class LayoutPullParserAdapterTest {

    private val sampleXml = """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:orientation="vertical">
            <TextView android:text="hi" />
        </LinearLayout>
    """.trimIndent()

    private val androidNs = "http://schemas.android.com/apk/res/android"

    @Test
    fun `parses element name on start tag`() {
        val p = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
        advanceToFirstStartTag(p)
        assertEquals("LinearLayout", p.name)
    }

    @Test
    fun `getAttributeValue by android namespace URI returns orientation`() {
        val p = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
        advanceToFirstStartTag(p)
        assertEquals("vertical", p.getAttributeValue(androidNs, "orientation"))
    }

    @Test
    fun `getViewCookie returns null`() {
        val p = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
        assertNull(p.viewCookie)
    }

    @Test
    fun `getLayoutNamespace returns RES_AUTO`() {
        val p = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
        assertEquals(ResourceNamespace.RES_AUTO, p.layoutNamespace)
    }

    @Test
    fun `fromFile loads sample layout from fixture`() {
        val path = locateMinimalFixture()
        val p = LayoutPullParserAdapter.fromFile(path)
        advanceToFirstStartTag(p)
        assertEquals("LinearLayout", p.name)
        assertEquals("vertical", p.getAttributeValue(androidNs, "orientation"))
    }

    private fun advanceToFirstStartTag(p: XmlPullParser) {
        while (p.eventType != XmlPullParser.START_TAG) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) return
            p.next()
        }
    }

    private fun locateMinimalFixture(): Path {
        // gradle test cwd = server/layoutlib-worker/ → fixture/ 는 두 단계 상위.
        val candidates = listOf(
            Path.of("../../fixture/sample-app/app/src/main/res/layout/activity_minimal.xml"),
            Path.of("../fixture/sample-app/app/src/main/res/layout/activity_minimal.xml"),
            Path.of("fixture/sample-app/app/src/main/res/layout/activity_minimal.xml"),
            Path.of(System.getProperty("user.dir"), "../../fixture/sample-app/app/src/main/res/layout/activity_minimal.xml")
        )
        val found = candidates.firstOrNull { it.toFile().isFile }
            ?: error("activity_minimal.xml 찾을 수 없음 — cwd=${System.getProperty("user.dir")}")
        assertTrue(found.toFile().length() > 0)
        return found.toAbsolutePath().normalize()
    }
}
