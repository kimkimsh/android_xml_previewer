package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.ResourceNamespace
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * W3D4-β T12 (round 3 reconcile): color state list (`<selector>` XML) 의 raw body 를
 * KXmlParser 에 StringReader 로 feed. layoutlib 의 ILayoutPullParser 계약 = XmlPullParser
 * delegate + getViewCookie() + getLayoutNamespace() 세 가지 모두 구현.
 *
 * round 3 critique 양쪽 (Codex / Claude) 이 독립으로 catch — `getLayoutNamespace()`
 * override 누락 시 Bridge ResourceHelper.getXmlBlockParser 호출 직후 NPE /
 * UnsupportedOperationException. selector 색상은 RES_AUTO ns 의 `res/color/<name>.xml` 에서
 * 왔으므로 RES_AUTO 반환.
 */
internal class SelectorXmlPullParser private constructor(
    private val mDelegate: KXmlParser,
) : ILayoutPullParser, XmlPullParser by mDelegate
{

    override fun getViewCookie(): Any? = null

    override fun getLayoutNamespace(): ResourceNamespace = ResourceNamespace.RES_AUTO

    companion object
    {

        fun fromString(rawXml: String): ILayoutPullParser
        {
            val parser = KXmlParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(rawXml))
            return SelectorXmlPullParser(parser)
        }
    }
}
