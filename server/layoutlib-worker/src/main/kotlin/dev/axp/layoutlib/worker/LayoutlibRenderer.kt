package dev.axp.layoutlib.worker

import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.SessionParams
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants
import dev.axp.layoutlib.worker.classloader.RJarSymbolSeeder
import dev.axp.layoutlib.worker.classloader.SampleAppClassLoader
import dev.axp.layoutlib.worker.resources.FrameworkRenderResources
import dev.axp.layoutlib.worker.resources.FrameworkResourceValueLoader
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
 * W2D6-FATJAR (08 §7.7 blocker #3) → W2D7-RENDERSESSION (08 §7.7.1 item 3b).
 *
 * W2D7 에서 `renderViaLayoutlib` 는 실제 `Bridge.createSession(SessionParams)` →
 * `RenderSession.render(timeout)` → `session.image: BufferedImage` 경로로 교체되었다.
 * Bridge.init 은 한 번만 수행 (JVM 생애). RenderSession 은 per-render 생성-dispose.
 *
 * 경계:
 *  - 커스텀 뷰 (ConstraintLayout, MaterialButton 등) 는 `MinimalLayoutlibCallback.loadView`
 *    가 UnsupportedOperationException 으로 거부 — activity_basic.xml 은 W3+ L3 타겟.
 *  - fixture XML 을 찾지 못하면 즉시 throw (버그). RenderSession 내부 실패는 fallback 으로 위임.
 */
class LayoutlibRenderer(
    private val distDir: Path,
    private val fallback: PngRenderer?,
    private val fixtureRoot: Path,
    private val sampleAppModuleRoot: Path,
) : PngRenderer {

    private val bootstrap = LayoutlibBootstrap(distDir)

    @Volatile private var initialized = false
    @Volatile private var classLoader: ClassLoader? = null
    @Volatile private var bridgeInstance: Any? = null
    @Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null

    /**
     * W2D7 3b-arch 진단 hook — 마지막 `createSession` 호출의 Result. createSession 이 inflate 를
     * 시도한 뒤의 즉시 상태를 담는다 (render() 가 이후 ERROR_NOT_INFLATED 를 추가로 반환해도
     * 덮어쓰지 않음 — 원인은 createSession 에 있음).
     *
     * 현재 3b-values (framework resource VALUE 파싱) 이 W3 carry 로 split 되어 있어 실 inflate 는
     * `ERROR_INFLATION (config_scrollbarSize not found)` 에서 중단된다.
     */
    @Volatile var lastCreateSessionResult: Result? = null
        private set

    /** `render()` 호출의 Result (createSession 이 성공했을 때만 의미 있음). */
    @Volatile var lastRenderResult: Result? = null
        private set

    /**
     * 진단용 편의: createSession 이 실패했다면 그 result, 아니면 render 의 result.
     * 3b-arch tests 는 이 값을 assert.
     */
    val lastSessionResult: Result?
        get() = lastCreateSessionResult?.takeIf { !it.isSuccess } ?: lastRenderResult

    init {
        System.setProperty(HEADLESS_PROPERTY_KEY, HEADLESS_PROPERTY_VALUE)
    }

    override fun renderPng(layoutName: String): ByteArray {
        if (!initialized) {
            initBridge()
        }
        return renderViaLayoutlib(layoutName)
            ?: (fallback?.renderPng(layoutName)
                ?: error("LayoutlibRenderer 실패 + fallback 없음: $layoutName"))
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
                // native lib 로딩 실패는 렌더 실패로 처리하지 않음 — init 시도는 계속.
            }
        }

        val platformProps = bootstrap.parseBuildProperties()
        val fontDir = bootstrap.fontsDir().toFile()
        val nativeLibPath = bootstrap.nativeLibDir().absolutePathString()
        val icuPath = bootstrap.findIcuDataFile()?.absolutePathString()
            ?: error("ICU data 파일 누락 (data/icu/icudt*.dat)")
        val keyboardPaths = bootstrap.listKeyboardPaths().toTypedArray()
        val enumValueMap = mutableMapOf<String, MutableMap<String, Int>>()

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
     * W2D7: 실 Bridge.createSession → RenderSession.render → BufferedImage → PNG 경로.
     *
     * 흐름:
     *  1. fixture 경로에서 XML 로드 → LayoutPullParserAdapter.
     *  2. SessionParamsFactory.build(parser) → SessionParams.
     *  3. bridge.createSession(params) 를 reflection 으로 호출 (bridge 는 isolated CL 에서 로드된
     *     인스턴스; SessionParams 는 system CL 의 동일 클래스 타입 — parent-first delegation 이
     *     동일 Class identity 를 보장).
     *  4. session.render(timeout) → result.isSuccess 체크.
     *  5. session.image → ImageIO.write PNG → bytes.
     *  6. 무조건 session.dispose().
     *
     * 실패 시 (result non-success, exception, fixture 없음 이외) → null 반환 → fallback.
     */
    private fun renderViaLayoutlib(layoutName: String): ByteArray? {
        val layoutPath = fixtureRoot.resolve(layoutName)
        require(layoutPath.toFile().isFile) {
            "fixture 레이아웃을 찾을 수 없음: $layoutPath"
        }

        val parser = LayoutPullParserAdapter.fromFile(layoutPath)
        // W3D1 3b-values: framework resource VALUE loader 가 10 XML 을 파싱하여 RenderResources 에 주입.
        // JVM-wide cache 라 첫 호출만 파싱 비용 발생.
        val bundle = FrameworkResourceValueLoader.loadOrGet(
            distDir.resolve(ResourceLoaderConstants.DATA_DIR)
        )
        val resources = FrameworkRenderResources(bundle, SessionConstants.DEFAULT_FRAMEWORK_THEME)
        val params: SessionParams = SessionParamsFactory.build(
            layoutParser = parser,
            callback = MinimalLayoutlibCallback({ ensureSampleAppClassLoader() }, ::seedRJarSymbols),
            resources = resources,
        )

        val bridge = bridgeInstance ?: return null

        // createSession 은 bridge 클래스에서 선언. parent-first 덕분에 인자/반환 타입이 system CL 의
        // SessionParams / RenderSession 과 동일.
        // 페어 리뷰 (Codex + Claude convergent): parameterCount==1 만 매칭하면 향후 layoutlib 이
        // 다른 1-arg createSession 오버로드를 추가할 때 잘못된 메서드를 선택할 수 있으므로, 인자
        // 타입을 SessionParams 로 명시 검증.
        val createSession = bridge.javaClass.methods.firstOrNull {
            it.name == BRIDGE_CREATE_SESSION &&
                it.parameterCount == BRIDGE_CREATE_SESSION_PARAM_COUNT &&
                it.parameterTypes.singleOrNull()?.name == SessionParams::class.java.name
        } ?: return null

        val session = try {
            createSession.invoke(bridge, params) as? RenderSession ?: return null
        } catch (t: Throwable) {
            // Bridge 내부 예외 — fallback 으로 위임.
            t.printStackTrace(System.err)
            return null
        }

        // createSession 이 inflate 까지 시도. 실패 시 session.result 에 상태가 담긴다.
        // 3b-arch hook: tests 가 architecture-positive evidence 로 사용.
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
     * W3D3 (round-2 페어 B1): sample-app 의 dex/aar 클래스로더는 lazy 하게 build.
     * Bridge.init 이 끝난 후 (= isolated CL 가 준비된 후) MinimalLayoutlibCallback 의
     * loadView 가 처음 호출되는 시점에 한 번 빌드된다.
     */
    @Synchronized
    private fun ensureSampleAppClassLoader(): ClassLoader
    {
        sampleAppClassLoader?.let { return it.classLoader }
        val isolated = classLoader ?: error("Bridge 가 init 안 됨 (initBridge 가 먼저 실행되어야 함)")
        val built = SampleAppClassLoader.build(sampleAppModuleRoot, isolated)
        sampleAppClassLoader = built
        return built.classLoader
    }

    /**
     * α: callback init 에서 호출. R.jar 의 모든 R$<type> 클래스를 enumerate 하여 등록.
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

        /** `createSession(SessionParams)` 의 parameter count — strict 리플렉션 매칭용. */
        private const val BRIDGE_CREATE_SESSION_PARAM_COUNT = 1
    }
}
