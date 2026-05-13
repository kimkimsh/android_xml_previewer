package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import dev.axp.layoutlib.worker.resources.AppLibraryResourceConstants
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal LayoutlibCallback implementation feeding bridge inflation.
 *
 * Implementation scope:
 *  - Resource id bidirectional map (getOrGenerateResourceId ↔ resolveResourceId),
 *    thread-safe.
 *  - loadView: reflection-instantiates custom view classes from the sample-app
 *    classloader. AppCompat auto-substitution remains enabled via findClass.
 *  - getAdapterBinding: no ListView/Spinner data binding → null.
 *  - getActionBarCallback: default ActionBarCallback() — setForceNoDecor masks
 *    the action bar regardless.
 *  - XmlParserFactory methods return KXmlParser by interface contract.
 *
 * Lazy classloader provider: the constructor takes a viewClassLoaderProvider
 * lambda so the sample-app classloader is built on-demand. loadView uses the
 * provider to reflection-instantiate the view, unwrapping
 * InvocationTargetException so the bridge inflater sees the original cause.
 *
 * Raw-XML lookup wiring: COLOR / ANIMATOR / DRAWABLE / INTERPOLATOR / LAYOUT
 * resource refs are routed through their respective lookup callbacks in
 * getParser, so Bridge's ResourceHelper.getXmlBlockParser receives the bundle's
 * stored raw body via SelectorXmlPullParser.
 */
class MinimalLayoutlibCallback(
    private val viewClassLoaderProvider: () -> ClassLoader,
    private val initializer: ((ResourceReference, Int) -> Unit) -> Unit,
    private val colorStateListLookup: (ResourceReference) -> String?,
    private val animatorXmlLookup: (ResourceReference) -> String?,
    private val drawableXmlLookup: (ResourceReference) -> String?,
    private val interpolatorXmlLookup: (ResourceReference) -> String?,
    private val layoutXmlLookup: (ResourceReference) -> String?,
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
            throw IllegalStateException("R.jar seeding failed: ${t.message}", t)
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
            // Unwrap so layoutlib's BridgeInflater surfaces the original cause
            // rather than an InflateException wrapper.
            throw ite.cause ?: ite
        }
    }

    override fun findClass(name: String): Class<*> {
        return viewClassLoaderProvider().loadClass(name)
    }

    override fun hasAndroidXAppCompat(): Boolean = true

    /**
     * Bridge ResourceHelper.getXmlBlockParser routes through this method for
     * non-framework values. Each raw-XML feed type has its own lookup that
     * returns the bundle's stored body, which SelectorXmlPullParser wraps in a
     * KXmlParser with namespace processing enabled. A null return drops Bridge
     * back to ParserFactory.create(value), which calls createXmlParserForFile
     * and produces a blank parser ("No Input specified") — that fallback is the
     * canonical signal for an unwired ResourceType branch.
     */
    override fun getParser(layoutResource: ResourceValue?): ILayoutPullParser?
    {
        if (layoutResource == null)
        {
            return null
        }
        val ns = layoutResource.namespace ?: return null
        val name = layoutResource.name ?: return null
        return when (layoutResource.resourceType)
        {
            ResourceType.COLOR ->
            {
                val ref = ResourceReference(ns, ResourceType.COLOR, name)
                val rawXml = colorStateListLookup(ref) ?: return null
                SelectorXmlPullParser.fromString(rawXml)
            }
            ResourceType.ANIMATOR ->
            {
                val ref = ResourceReference(ns, ResourceType.ANIMATOR, name)
                val rawXml = animatorXmlLookup(ref) ?: return null
                SelectorXmlPullParser.fromString(rawXml)
            }
            ResourceType.DRAWABLE ->
            {
                val ref = ResourceReference(ns, ResourceType.DRAWABLE, name)
                val rawXml = drawableXmlLookup(ref) ?: return null
                SelectorXmlPullParser.fromString(rawXml)
            }
            ResourceType.INTERPOLATOR ->
            {
                val ref = ResourceReference(ns, ResourceType.INTERPOLATOR, name)
                val rawXml = interpolatorXmlLookup(ref) ?: return null
                SelectorXmlPullParser.fromString(rawXml)
            }
            ResourceType.LAYOUT ->
            {
                val ref = ResourceReference(ns, ResourceType.LAYOUT, name)
                val rawXml = layoutXmlLookup(ref) ?: return null
                SelectorXmlPullParser.fromString(rawXml)
            }
            else -> null
        }
    }

    override fun getAdapterBinding(cookie: Any?, attributes: Map<String, String>): AdapterBinding? = null

    override fun getActionBarCallback(): ActionBarCallback = ActionBarCallback()

    override fun createXmlParser(): XmlPullParser = buildKxml()

    /**
     * BridgeInflater.inflate(int, ViewGroup) — the 2-arg LayoutInflater
     * overload — bypasses LayoutlibCallback.getParser and calls
     * ParserFactory.create(value, true) directly, which delegates to this
     * method with the ResourceValue.value string as fileName. The LAYOUT
     * placeholder shape `axp/layout/<name>.xml` would arrive here
     * unrecognized; the blank KXmlParser then produces "No Input specified".
     * The defensive log surfaces the bypass case so future widgets that hit
     * it have a grep-able diagnostic instead of a silent fallback.
     */
    override fun createXmlParserForFile(fileName: String): XmlPullParser
    {
        if (fileName.startsWith(AppLibraryResourceConstants.LAYOUT_PLACEHOLDER_PREFIX))
        {
            System.err.println(
                "[MinimalLayoutlibCallback] createXmlParserForFile bypass detected for layout placeholder '$fileName' — BridgeInflater 2-arg overload skipped getParser",
            )
        }
        return buildKxml()
    }

    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser = buildKxml()

    override fun getApplicationId(): String = APPLICATION_ID

    private fun buildKxml(): XmlPullParser = KXmlParser().also {
        it.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    }

    companion object {
        /**
         * Generated id base — kept inside the 0x7F family per Android Studio
         * convention but disjoint from the AAPT type-byte range; 0x7F80_0000
         * leaves the per-type allocations 0x7F0A_xxxx through 0x7F7F_xxxx free
         * for seeded R.jar entries.
         */
        private const val FIRST_ID = 0x7F80_0000

        /** Stable application id used by Bridge.mProjectKey lookups. */
        private const val APPLICATION_ID = "axp.render"
    }
}
