package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.HardwareConfig
import com.android.ide.common.rendering.api.ILayoutLog
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * W2D7-RENDERSESSION (08 §7.7.1 item 3b) — `Bridge.createSession(params)` 에 전달할 SessionParams
 * 를 일관된 설정으로 생성하는 팩토리.
 *
 * W3D1 3b-values: resources 파라미터 default 제거 — 호출자가 FrameworkRenderResources 를 주입해야 함
 * (CLAUDE.md "No default parameter values" 정책 + framework VALUE loader 배선).
 */
object SessionParamsFactory {

    /**
     * @param layoutParser fixture XML 을 감싼 ILayoutPullParser.
     * @param callback LayoutlibCallback — 호출자 주입 필수 (production 은 `MinimalLayoutlibCallback()`).
     * @param resources RenderResources — 호출자 주입 필수. production 경로는 FrameworkRenderResources.
     *
     * CLAUDE.md "No default parameter values" 준수 (W3D1 impl-pair-review MF2):
     * Codex reviewer 가 Q2 에서 `callback` default 가 아직 존재함을 지적 → 제거.
     */
    fun build(
        layoutParser: ILayoutPullParser,
        callback: LayoutlibCallback,
        resources: RenderResources,
    ): SessionParams {
        val hardware = HardwareConfig(
            SessionConstants.RENDER_WIDTH_PX,
            SessionConstants.RENDER_HEIGHT_PX,
            Density.XHIGH,
            SessionConstants.DPI_XHIGH,
            SessionConstants.DPI_XHIGH,
            ScreenSize.NORMAL,
            ScreenOrientation.PORTRAIT,
            ScreenRound.NOTROUND,
            /* softwareButtons */ false
        )

        val logProxy = Proxy.newProxyInstance(
            ILayoutLog::class.java.classLoader,
            arrayOf(ILayoutLog::class.java),
            NoopLogHandler
        ) as ILayoutLog

        val params = SessionParams(
            layoutParser,
            SessionParams.RenderingMode.NORMAL,
            SessionConstants.PROJECT_KEY,
            hardware,
            resources,
            callback,
            SessionConstants.MIN_SDK,
            SessionConstants.TARGET_SDK,
            logProxy
        )

        // F2: AssetRepository 미지원 + decor 제거 → status bar/action bar 없는 content-only.
        params.setAssetRepository(NoopAssetRepository())
        params.setForceNoDecor()
        params.setRtlSupport(true)
        params.setLocale(SessionConstants.RENDER_LOCALE)
        params.setFontScale(1.0f)
        // W2D7 시도 2: FolderConfiguration 매칭이 "current configuration" 에서 base 리소스
        // (values/config.xml) 를 못 찾는 문제가 uiMode/night mode 미지정 때문일 수 있어 명시적으로 설정.
        params.setUiMode(SessionConstants.UI_MODE_NORMAL_DAY)
        params.timeout = SessionConstants.RENDER_TIMEOUT_MS

        return params
    }

    /**
     * ILayoutLog 프록시 — 진단을 위해 error/warning/fidelityWarning 호출을 stderr 로 dump.
     * 정상 경로에서는 무작업. TDD / layoutlib 내부 예외 추적 용도.
     * Bridge.createSession 에 전달되어 layoutlib 이 inflate/render 중 발견한 문제를 통보.
     */
    private object NoopLogHandler : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            val name = method.name
            if (name in LOG_METHODS_TO_DUMP) {
                System.err.println(
                    "[layoutlib.${name}] " + (args?.joinToString(" | ") { safeStr(it) } ?: "")
                )
                val throwable = args?.firstOrNull { it is Throwable } as? Throwable
                throwable?.printStackTrace(System.err)
            }
            return when (method.returnType.name) {
                "void" -> null
                "boolean" -> false
                else -> if (Modifier.isStatic(method.modifiers)) null
                        else if (method.returnType.isPrimitive) 0
                        else null
            }
        }

        private fun safeStr(v: Any?): String = when (v) {
            null -> "null"
            is Throwable -> "${v.javaClass.simpleName}: ${v.message}"
            is Array<*> -> v.joinToString(",") { safeStr(it) }
            else -> v.toString().take(300)
        }

        private val LOG_METHODS_TO_DUMP = setOf(
            "error", "warning", "fidelityWarning", "logAndroidFramework"
        )
    }
}
