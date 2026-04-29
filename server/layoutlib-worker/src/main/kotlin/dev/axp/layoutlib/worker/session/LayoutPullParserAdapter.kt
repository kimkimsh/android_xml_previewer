package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.ResourceNamespace
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * W2D7-RENDERSESSION — kxml2 `KXmlParser` 위에 `ILayoutPullParser` 인터페이스를 얇게 얹는 adapter.
 *
 * ILayoutPullParser 는 XmlPullParser 에 두 개의 추가 메서드만 얹는다:
 *  - getViewCookie(): inflate 된 View ↔ XML source 간의 mapping 훅 (IDE 용). 본 경로에서 null.
 *  - getLayoutNamespace(): 레이아웃이 속한 리소스 네임스페이스. 우리 fixture 는 app-local 이므로 RES_AUTO.
 *
 * layoutlib 은 `SessionParams.layoutDescription` 으로 본 어댑터를 소비하여 `next()` 를 반복 호출,
 * `START_TAG` 에서 element name + attribute 들을 읽어 View 를 inflate 한다.
 */
class LayoutPullParserAdapter private constructor(
    private val delegate: KXmlParser
) : ILayoutPullParser, XmlPullParser by delegate {

    override fun getViewCookie(): Any? = null

    override fun getLayoutNamespace(): ResourceNamespace = ResourceNamespace.RES_AUTO

    companion object {

        /**
         * Reader 로부터 파서 생성. 내부적으로 FEATURE_PROCESS_NAMESPACES=true 설정.
         * 파서는 input 직후 START_DOCUMENT 위치에 있으므로 layoutlib 이 next() 로 진행.
         */
        @Throws(XmlPullParserException::class)
        fun fromReader(reader: Reader): LayoutPullParserAdapter {
            val parser = KXmlParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(reader)
            return LayoutPullParserAdapter(parser)
        }

        /** 파일 경로로부터 파서 생성 — UTF-8 로 읽어 fromReader 에 위임. */
        @Throws(XmlPullParserException::class)
        fun fromFile(path: Path): LayoutPullParserAdapter {
            val reader = path.toFile().inputStream().reader(StandardCharsets.UTF_8).buffered()
            return fromReader(reader)
        }
    }
}
