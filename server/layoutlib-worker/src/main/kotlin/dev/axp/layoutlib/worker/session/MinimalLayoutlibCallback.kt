package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.lang.reflect.InvocationTargetException
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
 *
 * W3D3-L3-CLASSLOADER (round 2 페어 리뷰 반영) — 변경 사항:
 *  - 생성자에 viewClassLoaderProvider lazy lambda 추가 (Q4 lazy build).
 *  - loadView: viewClassLoaderProvider 로부터 lazy 로 CL 받아 reflection-instantiate.
 *    InvocationTargetException 의 cause 를 unwrap (Q3, Codex 입장).
 *  - findClass: BridgeInflater.findCustomInflater 의 AppCompat 자동 치환 활성화 (F1).
 *  - hasAndroidXAppCompat: true (F1) — sample-app 의존 그래프 보유.
 */
class MinimalLayoutlibCallback(
    private val viewClassLoaderProvider: () -> ClassLoader,
    private val initializer: ((ResourceReference, Int) -> Unit) -> Unit,
    private val colorStateListLookup: (ResourceReference) -> String?,
) : LayoutlibCallback() {

    private val nextId = AtomicInteger(FIRST_ID)
    private val byRef = mutableMapOf<ResourceReference, Int>()
    private val byId = mutableMapOf<Int, ResourceReference>()

    init {
        try
        {
            initializer { ref, id ->
                byRef[ref] = id
                byId[id] = ref
                advanceNextIdAbove(id)
            }
        }
        catch (t: Throwable)
        {
            throw IllegalStateException("R.jar 시드 중 실패: ${t.message}", t)
        }
    }

    private fun advanceNextIdAbove(seeded: Int)
    {
        while (true)
        {
            val current = nextId.get()
            if (current > seeded)
            {
                return
            }
            if (nextId.compareAndSet(current, seeded + 1))
            {
                return
            }
        }
    }

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
        val cls = viewClassLoaderProvider().loadClass(name)
        val sig = constructorSignature ?: emptyArray()
        val args = constructorArgs ?: emptyArray()
        val ctor = cls.getDeclaredConstructor(*sig)
        ctor.isAccessible = true
        try
        {
            return ctor.newInstance(*args)
        }
        catch (ite: InvocationTargetException)
        {
            // Q3: cause unwrap — layoutlib 의 BridgeInflater 가 InflateException 으로 wrap 함.
            throw ite.cause ?: ite
        }
    }

    override fun findClass(name: String): Class<*> {
        return viewClassLoaderProvider().loadClass(name)
    }

    override fun hasAndroidXAppCompat(): Boolean = true

    /**
     * W3D4-β T12: Bridge ResourceHelper.getColorStateList → getXmlBlockParser 가 호출.
     * RES_AUTO color state list (`res/color/<name>.xml`) 이면 raw selector XML 을
     * StringReader 로 wrap 하여 ILayoutPullParser 반환. 그 외 (LAYOUT/MENU/DRAWABLE 등)
     * 은 prior null 동작 보존 — round 3 양쪽 reviewer 가 회귀 없음을 확인.
     */
    override fun getParser(layoutResource: ResourceValue?): ILayoutPullParser?
    {
        if (layoutResource == null)
        {
            return null
        }
        if (layoutResource.resourceType != ResourceType.COLOR)
        {
            return null
        }
        val ns = layoutResource.namespace ?: return null
        val name = layoutResource.name ?: return null
        val ref = ResourceReference(ns, ResourceType.COLOR, name)
        val rawXml = colorStateListLookup(ref) ?: return null
        return SelectorXmlPullParser.fromString(rawXml)
    }

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
        /**
         * 생성 id 기저. 0x7F 패밀리 (android studio 관례) 의 하위.
         * round 2 Q2 정정 — AAPT type-byte 와 disjoint. 0x7F0A_0000 → 0x7F80_0000.
         */
        private const val FIRST_ID = 0x7F80_0000

        /** Bridge.mProjectKey lookup 등 내부 진단에 쓰일 수 있는 안정적 app id. */
        private const val APPLICATION_ID = "axp.render"
    }
}
