package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleResourceValueImpl
import com.android.resources.ResourceType

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2 3b-values): RenderResources subclass.
 * Bundle 에 있는 값을 framework (android) namespace 로 매핑하여 layoutlib 에 제공.
 *
 * 설계 결정:
 *  - `findResValue` 는 override 금지 (W2D7 L3 landmine — 기본 RenderResources 의 ref 해석을 가로챔).
 *  - default theme 은 생성자 주입. bundle 내 style 이 없어도 parent=null 빈 StyleResourceValue 반환
 *    (layoutlib 이 self-chain 만 수행하고 실패하지 않도록 minimal fallback).
 *  - project namespace (RES_AUTO) 요청은 전부 null — framework 전용 resolver.
 */
class FrameworkRenderResources(
    private val bundle: FrameworkResourceBundle,
    private val defaultThemeName: String,
) : RenderResources() {

    private val mDefaultTheme: StyleResourceValue = bundle.getStyle(defaultThemeName)
        ?: StyleResourceValueImpl(
            ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, defaultThemeName),
            /* parentStyle */ null,
            /* libraryName */ null,
        )

    override fun getDefaultTheme(): StyleResourceValue = mDefaultTheme

    override fun getStyle(ref: ResourceReference): StyleResourceValue? {
        if (ref.namespace != ResourceNamespace.ANDROID) return null
        return bundle.getStyle(ref.name)
    }

    override fun getUnresolvedResource(ref: ResourceReference): ResourceValue? {
        if (ref.namespace != ResourceNamespace.ANDROID) return null
        return bundle.getResource(ref.resourceType, ref.name)
    }

    override fun getResolvedResource(ref: ResourceReference): ResourceValue? {
        // framework scope 내에서는 unresolved==resolved (string literal, ref 해석 X — RenderResources base delegate).
        return getUnresolvedResource(ref)
    }
}
