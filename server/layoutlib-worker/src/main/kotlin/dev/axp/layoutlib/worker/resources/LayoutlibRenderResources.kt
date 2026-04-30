package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleResourceValueImpl
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl

/**
 * W3D4 §3.1 #8 / §5.1: namespace-aware RenderResources subclass (T6).
 *
 * Q3 σ FULL: layoutlib `RenderResources` base 의 9 method 가 null/empty stub →
 * ρ 위임 = resolution bypass. 본 subclass 가 9 method 를 모두 override 하여
 * bundle-backed resolution + chain walker + theme stack 을 제공.
 *
 * Q2 fallback uniform: getStyle / getParent 모두 ns-exact-then-name 동일 정책.
 *
 * v2 round 2 follow-up:
 *  - FF#3 (Codex Q1): parseReference 가 layoutlib-api 의 ResourceUrl.parse() 직접 활용
 *    (self-built regex 폐기). @null/@empty sentinel 즉시 raw 반환. private @*android:...
 *    override + bracket aapt namespace 자동 처리.
 *  - FF#5: MAX_THEME_HOPS=32 (실측 17 hop + ThemeOverlay buffer). hop overflow 진단 로그.
 *  - FF#6: walkParent 가 parentStyleName="android:Theme.Holo.Light" prefix 를 ANDROID ns 로
 *    normalize.
 */
internal class LayoutlibRenderResources(
    private val bundle: LayoutlibResourceBundle,
    private val defaultThemeName: String,
) : RenderResources()
{

    private val mDefaultTheme: StyleResourceValue =
        bundle.getStyleByName(defaultThemeName) ?: emptyTheme(defaultThemeName)

    private val mThemeStack: MutableList<StyleResourceValue> = computeInitialStack()

    private fun computeInitialStack(): MutableList<StyleResourceValue>
    {
        val stack = mutableListOf<StyleResourceValue>()
        stack += mDefaultTheme
        var cur: StyleResourceValue? = mDefaultTheme
        var hops = 0
        while (cur != null && hops < AppLibraryResourceConstants.MAX_THEME_HOPS)
        {
            val parent = walkParent(cur)
            if (parent == null)
            {
                break
            }
            if (stack.contains(parent))
            {
                // cycle 검출 — 추가 walk 중단 (graceful)
                break
            }
            stack += parent
            cur = parent
            hops++
        }
        if (hops >= AppLibraryResourceConstants.MAX_THEME_HOPS)
        {
            // v2 round 2 follow-up #5: hop overflow 진단 로그 (ThemeOverlay 폭발 등 비정상 chain).
            System.err.println(
                "[LayoutlibRenderResources] MAX_THEME_HOPS(${AppLibraryResourceConstants.MAX_THEME_HOPS}) 초과 — theme stack walk 중단"
            )
        }
        return stack
    }

    private fun walkParent(style: StyleResourceValue): StyleResourceValue?
    {
        val parentRef = style.parentStyle
        if (parentRef != null)
        {
            val exact = bundle.getStyleExact(parentRef)
            if (exact != null)
            {
                return exact
            }
            return bundle.getStyleByName(parentRef.name)
        }
        // v2 round 2 follow-up #6: parentStyleName "android:Theme.Holo.Light" → ANDROID ns + bare name.
        val rawName = style.parentStyleName ?: return null
        return resolveStyleNameWithNamespace(rawName)
    }

    /**
     * v2 round 2 follow-up #6 (Codex Q6): cross-ns parent normalization.
     * "android:Theme.Holo.Light" → namespace=ANDROID + name="Theme.Holo.Light" 로 ns-exact lookup.
     * "Theme.AxpFixture" 처럼 prefix 없으면 ns-agnostic getStyleByName fallback.
     */
    private fun resolveStyleNameWithNamespace(rawName: String): StyleResourceValue?
    {
        val sepIdx = rawName.indexOf(AppLibraryResourceConstants.NS_NAME_SEPARATOR)
        if (sepIdx < 0)
        {
            return bundle.getStyleByName(rawName)
        }
        val nsPrefix = rawName.substring(0, sepIdx)
        val bareName = rawName.substring(sepIdx + AppLibraryResourceConstants.NS_NAME_SEPARATOR.length)
        val ns = if (nsPrefix == AppLibraryResourceConstants.ANDROID_NS_PREFIX)
        {
            ResourceNamespace.ANDROID
        }
        else
        {
            ResourceNamespace.RES_AUTO
        }
        val exact = bundle.getStyleExact(ResourceReference(ns, ResourceType.STYLE, bareName))
        if (exact != null)
        {
            return exact
        }
        return bundle.getStyleByName(bareName)
    }

    override fun getDefaultTheme(): StyleResourceValue = mDefaultTheme

    override fun getAllThemes(): List<StyleResourceValue> = mThemeStack.toList()

    override fun getStyle(reference: ResourceReference): StyleResourceValue?
    {
        val exact = bundle.getStyleExact(reference)
        if (exact != null)
        {
            return exact
        }
        return bundle.getStyleByName(reference.name)
    }

    override fun getParent(style: StyleResourceValue): StyleResourceValue? = walkParent(style)

    override fun getUnresolvedResource(reference: ResourceReference): ResourceValue? =
        bundle.getResource(reference)

    override fun getResolvedResource(reference: ResourceReference): ResourceValue?
    {
        val unresolved = bundle.getResource(reference) ?: return null
        return resolveResValue(unresolved)
    }

    override fun resolveResValue(value: ResourceValue?): ResourceValue?
    {
        if (value == null)
        {
            return null
        }
        var current: ResourceValue = value
        val seen = HashSet<ResourceReference>()
        var hops = 0
        while (hops < AppLibraryResourceConstants.MAX_REF_HOPS)
        {
            val text = current.value ?: return current
            // v2 round 2 follow-up #3 (Codex Q1): @null / @empty 는 sentinel — ref 가 아님 → 즉시 raw 반환.
            if (text == AppLibraryResourceConstants.RES_VALUE_NULL_LITERAL ||
                text == AppLibraryResourceConstants.RES_VALUE_EMPTY_LITERAL)
            {
                return current
            }
            val refLike = parseReference(text) ?: return current
            if (!seen.add(refLike))
            {
                // circular detection — graceful fallback (마지막 hop value 반환).
                System.err.println(
                    "[LayoutlibRenderResources] circular ref: ${current.name} → $refLike"
                )
                return current
            }
            val next = if (refLike.resourceType == ResourceType.ATTR)
            {
                findItemInTheme(refLike) ?: return current
            }
            else
            {
                getUnresolvedResource(refLike) ?: return current
            }
            current = next
            hops++
        }
        // v2 round 2 follow-up #5: MAX_REF_HOPS 초과 시 진단 로그 + 현재 value 반환 (graceful).
        System.err.println(
            "[LayoutlibRenderResources] MAX_REF_HOPS(${AppLibraryResourceConstants.MAX_REF_HOPS}) 초과 — chain ended at ${current.name}=${current.value}"
        )
        return current
    }

    override fun dereference(value: ResourceValue?): ResourceValue? = resolveResValue(value)

    override fun findItemInStyle(
        style: StyleResourceValue,
        attr: ResourceReference,
    ): ResourceValue?
    {
        var cur: StyleResourceValue? = style
        var hops = 0
        while (cur != null && hops < AppLibraryResourceConstants.MAX_THEME_HOPS)
        {
            val item = cur.getItem(attr)
            if (item != null)
            {
                return item
            }
            cur = walkParent(cur)
            hops++
        }
        return null
    }

    override fun findItemInTheme(attr: ResourceReference): ResourceValue?
    {
        for (theme in mThemeStack)
        {
            val item = findItemInStyle(theme, attr)
            if (item != null)
            {
                return item
            }
        }
        return null
    }

    override fun applyStyle(style: StyleResourceValue, useAsPrimary: Boolean)
    {
        if (useAsPrimary)
        {
            mThemeStack.clear()
        }
        if (!mThemeStack.contains(style))
        {
            mThemeStack.add(0, style)
        }
    }

    override fun clearStyles()
    {
        mThemeStack.clear()
        mThemeStack += mDefaultTheme
    }

    override fun clearAllThemes()
    {
        mThemeStack.clear()
    }

    private fun emptyTheme(name: String): StyleResourceValue = StyleResourceValueImpl(
        ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, name),
        /* parentStyle */ null,
        /* libraryName */ null,
    )

    /**
     * v2 round 2 follow-up #3 (Codex Q1): self-built regex 대신 layoutlib-api 의
     * com.android.resources.ResourceUrl.parse() 직접 활용. @null/@empty 는 이미 sentinel
     * fast-path 가 잡으므로 도달하지 않음. private (@*android:...) override 와 aapt
     * namespace 가 자동 처리됨 (url.namespace == "android" → ANDROID).
     */
    private fun parseReference(text: String): ResourceReference?
    {
        val url = ResourceUrl.parse(text) ?: return null
        val type = url.type ?: return null
        val ns = if (url.namespace == AppLibraryResourceConstants.ANDROID_NS_PREFIX)
        {
            ResourceNamespace.ANDROID
        }
        else
        {
            ResourceNamespace.RES_AUTO
        }
        return ResourceReference(ns, type, url.name)
    }
}
