package dev.axp.layoutlib.worker

import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.SessionParams
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants
import dev.axp.layoutlib.worker.classloader.RJarSymbolSeeder
import dev.axp.layoutlib.worker.classloader.SampleAppClassLoader
import dev.axp.layoutlib.worker.resources.AppLibraryResourceConstants
import dev.axp.layoutlib.worker.resources.LayoutlibRenderResources
import dev.axp.layoutlib.worker.resources.LayoutlibResourceValueLoader
import dev.axp.layoutlib.worker.resources.ResourceLoaderConstants
import dev.axp.layoutlib.worker.session.LayoutPullParserAdapter
import dev.axp.layoutlib.worker.session.MinimalLayoutlibCallback
import dev.axp.layoutlib.worker.session.SessionConstants
import dev.axp.layoutlib.worker.session.SessionParamsFactory
import dev.axp.protocol.render.PngRenderer
import dev.axp.layoutlib.worker.LayoutlibRendererConstants.BRIDGE_DISPOSE_METHOD
import dev.axp.layoutlib.worker.LayoutlibRendererConstants.BRIDGE_FQN
import dev.axp.layoutlib.worker.LayoutlibRendererConstants.BRIDGE_INIT_METHOD
import dev.axp.layoutlib.worker.LayoutlibRendererConstants.HEADLESS_PROPERTY_KEY
import dev.axp.layoutlib.worker.LayoutlibRendererConstants.HEADLESS_PROPERTY_VALUE
import dev.axp.layoutlib.worker.LayoutlibRendererConstants.ILAYOUT_LOG_FQN
import dev.axp.layoutlib.worker.LayoutlibRendererConstants.IMAGE_FORMAT_PNG
import dev.axp.layoutlib.worker.LayoutlibRendererConstants.NATIVE_LIB_NAME
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString

/**
 * Layoutlib-backed PngRenderer. `renderViaLayoutlib` drives the real
 * `Bridge.createSession(SessionParams)` → `RenderSession.render(timeout)` →
 * `session.image: BufferedImage` path. `Bridge.init` runs once per JVM;
 * RenderSession is created and disposed per render.
 *
 * Boundaries:
 *  - Custom views (ConstraintLayout, MaterialButton, ...) inflate via
 *    `MinimalLayoutlibCallback.loadView` against the sample-app classloader.
 *  - A missing fixture layout throws immediately (treated as a bug);
 *    RenderSession-internal failures delegate to the fallback PngRenderer.
 */
class LayoutlibRenderer(
    private val distDir: Path,
    private val fixtureRoot: Path,
    private val sampleAppModuleRoot: Path,
    private val themeName: String,
    private val fallback: PngRenderer?,
) : PngRenderer
{

    private val bootstrap = LayoutlibBootstrap(distDir)

    @Volatile private var initialized = false
    @Volatile private var classLoader: ClassLoader? = null
    @Volatile private var bridgeInstance: Any? = null
    @Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null

    /**
     * Diagnostic hook holding the Result returned by the most recent
     * `createSession` call. Captures the immediate post-inflate state so a
     * subsequent ERROR_NOT_INFLATED from `render()` does not overwrite the
     * upstream inflate failure cause.
     */
    @Volatile var lastCreateSessionResult: Result? = null
        private set

    /** Result of the most recent `render()` call (meaningful only when createSession succeeded). */
    @Volatile var lastRenderResult: Result? = null
        private set

    /**
     * Convenience accessor: when createSession itself failed, surfaces that
     * result; otherwise surfaces the render result. Integration tests assert
     * on this to distinguish inflate failures from render failures.
     */
    val lastSessionResult: Result?
        get() = lastCreateSessionResult?.takeIf { !it.isSuccess } ?: lastRenderResult

    init {
        System.setProperty(HEADLESS_PROPERTY_KEY, HEADLESS_PROPERTY_VALUE)
    }

    /**
     * Both initBridge and renderViaLayoutlib share the same LoaderArgs so the
     * JVM-wide bundle cache hits identically. The cache key is the 3-tuple
     * (distDataDir, sampleAppRoot, runtimeClasspathTxt); the constructor
     * arguments are immutable across this instance's lifetime, so rebuilding
     * the Args object per call produces the same hash.
     */
    private fun loaderArgs(): LayoutlibResourceValueLoader.Args =
        LayoutlibResourceValueLoader.Args(
            distDataDir = distDir.resolve(ResourceLoaderConstants.DATA_DIR),
            sampleAppRoot = sampleAppModuleRoot,
            runtimeClasspathTxt = sampleAppModuleRoot.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
        )

    override fun renderPng(layoutName: String): ByteArray {
        if (!initialized) {
            initBridge()
        }
        return renderViaLayoutlib(layoutName)
            ?: (fallback?.renderPng(layoutName)
                ?: error("LayoutlibRenderer failed and no fallback configured: $layoutName"))
    }

    @Synchronized
    private fun initBridge() {
        if (initialized) return
        val cl = bootstrap.createIsolatedClassLoader()
        val bridgeClass = Class.forName(BRIDGE_FQN, false, cl)
        val initMethod = bridgeClass.declaredMethods.first { it.name == BRIDGE_INIT_METHOD }

        val nativeLib = bootstrap.nativeLibDir().resolve(NATIVE_LIB_NAME)
        if (nativeLib.toFile().exists()) {
            try {
                System.load(nativeLib.absolutePathString())
            } catch (_: Throwable) {
                // Native lib load failures do not abort init — Bridge.init still
                // succeeds without the optional native components.
            }
        }

        val platformProps = bootstrap.parseBuildProperties()
        val fontDir = bootstrap.fontsDir().toFile()
        val nativeLibPath = bootstrap.nativeLibDir().absolutePathString()
        val icuPath = bootstrap.findIcuDataFile()?.absolutePathString()
            ?: error("ICU data file missing (data/icu/icudt*.dat)")
        val keyboardPaths = bootstrap.listKeyboardPaths().toTypedArray()
        // Inject the framework attrs.xml enum/flag tables into Bridge.sEnumValueMap.
        // BridgeTypedArray.resolveEnumAttribute consults the static sEnumValueMap on
        // the ANDROID namespace path; renderViaLayoutlib hits the same JVM-wide
        // bundle cache by Args identity so this is a single parse.
        val bundle = LayoutlibResourceValueLoader.loadOrGet(loaderArgs())
        val enumValueMap = bundle.frameworkEnumValueMap()

        val logInterface = Class.forName(ILAYOUT_LOG_FQN, false, cl)
        val logProxy = Proxy.newProxyInstance(cl, arrayOf(logInterface), NoopLogHandler())

        val instance = bridgeClass.getDeclaredConstructor().newInstance()
        initMethod.invoke(
            instance,
            platformProps,
            fontDir,
            nativeLibPath,
            icuPath,
            keyboardPaths,
            enumValueMap,
            logProxy
        )

        classLoader = cl
        bridgeInstance = instance
        initialized = true

        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                bridgeClass.getDeclaredMethod(BRIDGE_DISPOSE_METHOD).invoke(instance)
            } catch (_: Throwable) {}
        })
    }

    /**
     * Real Bridge.createSession → RenderSession.render → BufferedImage → PNG
     * pipeline.
     *
     * Flow:
     *  1. Load fixture XML → LayoutPullParserAdapter.
     *  2. SessionParamsFactory.build(parser) → SessionParams.
     *  3. Reflection-invoke bridge.createSession(params). Bridge is loaded from
     *     the isolated classloader; SessionParams is the system-classloader
     *     type — parent-first delegation guarantees Class identity.
     *  4. session.render(timeout) and check result.isSuccess.
     *  5. session.image → ImageIO.write PNG → bytes.
     *  6. session.dispose() unconditionally in finally.
     *
     * On any failure other than a missing fixture, returns null so the caller
     * can fall back to the configured PngRenderer.
     */
    private fun renderViaLayoutlib(layoutName: String): ByteArray? {
        val layoutPath = fixtureRoot.resolve(layoutName)
        require(layoutPath.toFile().isFile) {
            "fixture layout not found: $layoutPath"
        }

        val parser = LayoutPullParserAdapter.fromFile(layoutPath)
        // Build the unified resource bundle covering framework + sample-app + AAR
        // values XML across the ANDROID and RES_AUTO buckets. Cache is keyed by
        // the 3-tuple Args identity, so only the first call pays parsing cost.
        val bundle = LayoutlibResourceValueLoader.loadOrGet(loaderArgs())
        val resources = LayoutlibRenderResources(bundle, themeName)
        val params: SessionParams = SessionParamsFactory.build(
            layoutParser = parser,
            // Wire each raw-XML feed lookup into the callback so Bridge's
            // ResourceHelper.getXmlBlockParser path returns a parser fed with
            // the bundle's stored body for COLOR / ANIMATOR / DRAWABLE /
            // INTERPOLATOR / LAYOUT references.
            callback = MinimalLayoutlibCallback(
                { ensureSampleAppClassLoader() },
                ::seedRJarSymbols,
                bundle::getColorStateListXml,
                bundle::getAnimatorXml,
                bundle::getDrawableXml,
                bundle::getInterpolatorXml,
                bundle::getLayoutXml,
            ),
            resources = resources,
        )

        val bridge = bridgeInstance ?: return null

        // createSession is declared on the bridge class. Parent-first delegation
        // makes the argument and return types share Class identity with the
        // system-classloader SessionParams / RenderSession. Matching on
        // parameterCount alone would risk picking up a future 1-arg overload,
        // so the argument type is verified explicitly as SessionParams.
        val createSession = bridge.javaClass.methods.firstOrNull {
            it.name == BRIDGE_CREATE_SESSION &&
                it.parameterCount == BRIDGE_CREATE_SESSION_PARAM_COUNT &&
                it.parameterTypes.singleOrNull()?.name == SessionParams::class.java.name
        } ?: return null

        val session = try {
            createSession.invoke(bridge, params) as? RenderSession ?: return null
        } catch (t: Throwable) {
            // Bridge-internal exception — defer to the fallback renderer.
            t.printStackTrace(System.err)
            return null
        }

        // createSession attempts inflate before returning. On failure the
        // session.result carries the cause; the diagnostic hook captures it so
        // integration tests can assert on the upstream status independently of
        // any later render() failure.
        val initialResult = session.result
        lastCreateSessionResult = initialResult
        initialResult?.let {
            System.err.println(
                "[LayoutlibRenderer] createSession result: status=${it.status} " +
                    "msg=${it.errorMessage} exc=${it.exception?.javaClass?.simpleName}"
            )
        }

        try {
            val result = session.render(SessionConstants.RENDER_TIMEOUT_MS)
            lastRenderResult = result
            if (!result.isSuccess) {
                System.err.println(
                    "[LayoutlibRenderer] RenderSession.render failed: status=${result.status} " +
                        "msg=${result.errorMessage} exc=${result.exception?.javaClass?.simpleName}"
                )
                return null
            }
            val image: BufferedImage = session.image ?: return null
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, IMAGE_FORMAT_PNG, baos)
            return baos.toByteArray()
        } catch (t: Throwable) {
            t.printStackTrace(System.err)
            return null
        } finally {
            try {
                session.dispose()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Lazy build of the sample-app dex/aar classloader. The isolated Bridge
     * classloader must exist first (Bridge.init seeds it); this method is
     * called the first time MinimalLayoutlibCallback.loadView resolves a view.
     */
    @Synchronized
    private fun ensureSampleAppClassLoader(): ClassLoader
    {
        sampleAppClassLoader?.let { return it.classLoader }
        val isolated = classLoader ?: error("Bridge not initialized (initBridge must run first)")
        val built = SampleAppClassLoader.build(sampleAppModuleRoot, isolated)
        sampleAppClassLoader = built
        return built.classLoader
    }

    /**
     * Invoked by MinimalLayoutlibCallback's initializer. Enumerates every
     * `R$<type>` inner class in the sample-app R.jar and registers each int
     * symbol against its ResourceReference via the supplied callback.
     */
    private fun seedRJarSymbols(register: (ResourceReference, Int) -> Unit)
    {
        val sampleAppCL = ensureSampleAppClassLoader()
        val rJarPath = sampleAppModuleRoot.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH)
        RJarSymbolSeeder.seed(rJarPath, sampleAppCL, register)
    }

    private class NoopLogHandler : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            return when (method.returnType.name) {
                "void" -> null
                "boolean" -> false
                else -> if (Modifier.isStatic(method.modifiers)) null
                        else if (method.returnType.isPrimitive) 0
                        else null
            }
        }
    }

    companion object {
        private const val BRIDGE_CREATE_SESSION = "createSession"

        /** Strict reflection match — `createSession(SessionParams)` parameter count. */
        private const val BRIDGE_CREATE_SESSION_PARAM_COUNT = 1
    }
}
