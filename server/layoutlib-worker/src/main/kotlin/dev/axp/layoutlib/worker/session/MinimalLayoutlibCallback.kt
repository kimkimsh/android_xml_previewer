package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.atomic.AtomicInteger

/**
 * W2D7-RENDERSESSION — LayoutlibCallback 의 최소 구현체.
 *
 * 구현 범위 (activity_minimal.xml 기준):
 *  - resource id 양방향 map (getOrGenerateResourceId ↔ resolveResourceId). 스레드-세이프.
 *  - loadView: 커스텀 뷰 요구 시 즉시 UnsupportedOperationException — 프레임워크 위젯은
 *    Bridge 내부에서 처리되므로 호출되지 않음이 불변식 (custom view = L3 W3+).
 *  - getAdapterBinding: ListView/Spinner 데이터 바인딩 없음 → null.
 *  - getActionBarCallback: 기본 ActionBarCallback() — setForceNoDecor 로 어차피 action bar 미표시.
 *  - XmlParserFactory 메서드: 본 W2D7 fixture 에서 호출될 일 없으나 interface 계약상 KXmlParser 반환.
 */
class MinimalLayoutlibCallback : LayoutlibCallback() {

    private val nextId = AtomicInteger(FIRST_ID)
    private val byRef = mutableMapOf<ResourceReference, Int>()
    private val byId = mutableMapOf<Int, ResourceReference>()

    @Synchronized
    override fun getOrGenerateResourceId(ref: ResourceReference): Int {
        byRef[ref]?.let { return it }
        val id = nextId.getAndIncrement()
        byRef[ref] = id
        byId[id] = ref
        return id
    }

    @Synchronized
    override fun resolveResourceId(id: Int): ResourceReference? = byId[id]

    override fun loadView(name: String, constructorSignature: Array<out Class<*>>?, constructorArgs: Array<out Any>?): Any {
        throw UnsupportedOperationException(
            "W2D7 minimal: custom view '$name' 은 L3 (W3+) 타겟. activity_minimal.xml 은 framework 위젯만 허용."
        )
    }

    override fun getParser(layoutResource: ResourceValue?): ILayoutPullParser? = null

    override fun getAdapterBinding(cookie: Any?, attributes: Map<String, String>): AdapterBinding? = null

    override fun getActionBarCallback(): ActionBarCallback = ActionBarCallback()

    override fun createXmlParser(): XmlPullParser = buildKxml()

    override fun createXmlParserForFile(fileName: String): XmlPullParser = buildKxml()

    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser = buildKxml()

    override fun getApplicationId(): String = APPLICATION_ID

    private fun buildKxml(): XmlPullParser = KXmlParser().also {
        it.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    }

    companion object {
        /** 생성 id 기저. 0x7F 패밀리 (android studio 관례) 의 하위. */
        private const val FIRST_ID = 0x7F0A_0000

        /** Bridge.mProjectKey lookup 등 내부 진단에 쓰일 수 있는 안정적 app id. */
        private const val APPLICATION_ID = "axp.render"
    }
}
