package dev.axp.layoutlib.worker.resources

/** Domain constants for the AAR walker, resolver chain, and dedupe diagnostics. */
internal object AppLibraryResourceConstants
{

    /** Module-relative path of the runtime-classpath manifest emitted by sample-app `assembleDebug`. */
    const val RUNTIME_CLASSPATH_TXT_PATH = "app/build/axp/runtime-classpath.txt"

    /** AAR ZIP entry — values.xml. */
    const val AAR_VALUES_XML_PATH = "res/values/values.xml"

    /**
     * AAR ZIP entry prefix — color state-list XML directory (default qualifier only).
     * Qualifier directories (color-v31/, color-night-v8/, color-v23/, …) are out of
     * scope until density / locale / night-mode support lands.
     */
    const val AAR_COLOR_DIR_PREFIX = "res/color/"

    /** AAR ZIP entry prefix — animator / motion-spec XML directory (sibling to AAR_COLOR_DIR_PREFIX). */
    const val AAR_ANIMATOR_DIR_PREFIX = "res/animator/"

    /**
     * AAR ZIP entry prefix — drawable XML directory (sibling to AAR_COLOR_DIR_PREFIX +
     * AAR_ANIMATOR_DIR_PREFIX). Material 1.12.0 ships ~73 drawable XMLs (35 vector,
     * 18 animated-vector, 10 shape, 3 selector, 1 inset, 1 layer-list, etc.). Style
     * items such as Base.Widget.Material3.Chip.checkedIcon and Chip.closeIcon point
     * directly at @drawable/ic_m3_chip_*, so the bundle must feed the raw XML through
     * MinimalLayoutlibCallback.getParser the same way ColorStateList and AnimatorXml do;
     * BridgeContext.DrawableInflater consumes the body via XmlResourceParser. Qualifier
     * directories (drawable-night/, drawable-v24/, density variants) are out of scope
     * until W4+ density/locale/night-mode support.
     */
    const val AAR_DRAWABLE_DIR_PREFIX = "res/drawable/"

    /** File extension shared by color / animator / drawable XML resources. */
    const val COLOR_XML_SUFFIX = ".xml"

    /**
     * Color-state-list ResourceValue placeholder. The `LayoutlibCallback.getParser`
     * implementation returns an `ILayoutPullParser` for COLOR refs from the raw-XML
     * map, so Bridge never falls back to `ParserFactory.create(value)` and the value
     * string only needs to be a non-null sentinel. The `@axp:` prefix keeps the
     * sentinel identifiable in diagnostic logs.
     */
    const val COLOR_STATE_LIST_PLACEHOLDER_VALUE = "@axp:color-state-list"

    /** Animator-XML ResourceValue placeholder — sibling to COLOR_STATE_LIST_PLACEHOLDER_VALUE. */
    const val ANIMATOR_PLACEHOLDER_VALUE = "@axp:animator-xml"

    /**
     * Drawable-XML ResourceValue placeholder generator.
     *
     * Two non-obvious layoutlib contracts force the value shape to be (a) per-drawable
     * unique and (b) `.xml`-suffixed:
     *
     *  - `com.android.layoutlib.bridge.impl.ResourceHelper.getDrawable(ResourceValue,
     *    BridgeContext, Theme)` routes through `getXmlBlockParser` (which calls
     *    `LayoutlibCallback.getParser`) only when `value.toLowerCase().endsWith(".xml")`
     *    OR `resourceType == AAPT`. A DRAWABLE-typed bundle entry whose value does not
     *    end with `.xml` falls through to the asset / file resource path, which fails
     *    `AssetRepository.isFileResource` and returns null, causing
     *    `Resources_Delegate.throwException` to raise `Resources$NotFoundException` with
     *    "Could not find drawable resource matching value …".
     *
     *  - `android.content.res.Resources_Delegate.getDrawable` consults a JVM-static
     *    `sDrawableCache` (LruCache) keyed by the value string before delegating to
     *    `ResourceHelper.getDrawable`. A single shared placeholder for all drawables
     *    would alias every subsequent lookup to the first drawable's `ConstantState`,
     *    so the placeholder must encode the drawable name to give each entry a unique
     *    cache key.
     *
     * `Resources_Delegate.getAnimation` and the ColorStateList path do not pass through
     * the same `.xml` gate, so `ANIMATOR_PLACEHOLDER_VALUE` and
     * `COLOR_STATE_LIST_PLACEHOLDER_VALUE` remain static sentinels — only DRAWABLE needs
     * the per-name path-shaped value.
     */
    const val DRAWABLE_PLACEHOLDER_PREFIX = "axp/drawable/"
    const val DRAWABLE_PLACEHOLDER_SUFFIX = ".xml"

    fun drawablePlaceholderValue(name: String): String =
        DRAWABLE_PLACEHOLDER_PREFIX + name + DRAWABLE_PLACEHOLDER_SUFFIX

    /** AAR ZIP entry — AndroidManifest.xml (used to extract the package attribute). */
    const val AAR_ANDROID_MANIFEST_PATH = "AndroidManifest.xml"

    /** sample-app `res/values/`, relative to the sample-app module root. */
    const val SAMPLE_APP_RES_VALUES_RELATIVE_PATH = "app/src/main/res/values"

    /** Extracts the `package="..."` attribute from an AAR's plain-text AndroidManifest.xml. */
    val MANIFEST_PACKAGE_REGEX: Regex = Regex("""package\s*=\s*"([^"]+)"""")

    /** Hop limit for the chain walker (?attr / @ref) to bound resolution depth. */
    const val MAX_REF_HOPS = 10

    /**
     * Hop limit for the theme stack parent walk. The Theme.AxpFixture → … →
     * android:Theme chain measures at 17 edges in the current fixture; ThemeOverlay
     * additions can lengthen it by 5-10, so 32 leaves buffer headroom.
     */
    const val MAX_THEME_HOPS = 32

    /** Sanity guard — at least one AAR must contribute a `values.xml`, or `loadAarRes` fails loudly. */
    const val MIN_AAR_WITH_VALUES_THRESHOLD = 1

    /**
     * Sentinel literals consumed by `resolveResValue`: when encountered, the chain
     * walker returns the raw value rather than attempting to parse it as a reference.
     */
    const val RES_VALUE_NULL_LITERAL = "@null"
    const val RES_VALUE_EMPTY_LITERAL = "@empty"

    /**
     * Prefix used to normalize `android:`-qualified style parent names — e.g.
     * `parentStyleName = "android:Theme.Holo.Light"` is rewritten to namespace=ANDROID
     * + name="Theme.Holo.Light" before bundle lookup.
     */
    const val ANDROID_NS_PREFIX = "android"
    const val NS_NAME_SEPARATOR = ":"
}
