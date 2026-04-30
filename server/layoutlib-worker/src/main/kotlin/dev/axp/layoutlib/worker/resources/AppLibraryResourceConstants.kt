package dev.axp.layoutlib.worker.resources

/**
 * W3D4 MATERIAL-FIDELITY (08 §7.7.6, 본 spec §3.1 #1):
 * AAR walker / resolver chain / dedupe diagnostic 의 도메인 상수.
 * CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" 준수.
 */
internal object AppLibraryResourceConstants
{

    /** sample-app `assembleDebug` 가 emit 하는 runtime-classpath manifest 의 module-relative 경로. */
    const val RUNTIME_CLASSPATH_TXT_PATH = "app/build/axp/runtime-classpath.txt"

    /** AAR ZIP entry — values.xml. */
    const val AAR_VALUES_XML_PATH = "res/values/values.xml"

    /**
     * W3D4-β T12: AAR ZIP entry prefix — color state-list XML 디렉토리.
     * 예: material-1.12.0 의 res/color/m3_highlighted_text.xml. 실측 default `res/color/`
     * 합계 192 files (material 171 + appcompat 21). qualifier 디렉토리 (color-v31/,
     * color-night-v8/, color-v23/) 는 W4+ density/locale/night-mode 지원 시 추가.
     */
    const val AAR_COLOR_DIR_PREFIX = "res/color/"

    /** color XML 파일 확장자. */
    const val COLOR_XML_SUFFIX = ".xml"

    /**
     * W3D4-β T12: color state list ResourceValue 의 placeholder value. callback.getParser
     * 가 ILayoutPullParser 를 반환하므로 Bridge fallback (ParserFactory.create(value)) 에
     * 도달하지 않음 — 단지 non-null marker. 진단 시 식별 용이성을 위해 의도된 magic prefix.
     */
    const val COLOR_STATE_LIST_PLACEHOLDER_VALUE = "@axp:color-state-list"

    /** AAR ZIP entry — AndroidManifest.xml (package 추출용). */
    const val AAR_ANDROID_MANIFEST_PATH = "AndroidManifest.xml"

    /** sample-app res/values/ — module root 기준. */
    const val SAMPLE_APP_RES_VALUES_RELATIVE_PATH = "app/src/main/res/values"

    /** AndroidManifest.xml 의 `package="..."` 추출 regex. plain-text manifest 가정 (W3D4 §4.2). */
    val MANIFEST_PACKAGE_REGEX: Regex = Regex("""package\s*=\s*"([^"]+)"""")

    /** chain walker (?attr / @ref) 의 무한 루프 방지 hop limit. 일반 attr ref chain 3-5 hop. */
    const val MAX_REF_HOPS = 10

    /**
     * theme stack parent walk 의 무한 루프 방지 hop limit. v2 round 2 follow-up #5:
     * 실측 chain depth = 17 edges (Theme.AxpFixture → ... → android:Theme.Holo.Light → Theme.Light → Theme).
     * ThemeOverlay 추가 5-10 가능 → buffer 포함 32.
     */
    const val MAX_THEME_HOPS = 32

    /** loadAarRes 에서 AAR 1+ 의 values.xml 도 못 찾으면 sanity guard. */
    const val MIN_AAR_WITH_VALUES_THRESHOLD = 1

    /**
     * v2 round 2 follow-up #3: ResourceUrl 의 sentinel literal. resolveResValue 가 만나면
     * 즉시 raw value 반환 (parse 시도 안 함). 27 AAR 안 @null 106회 / @empty 2회 출현 (Codex Q1 실측).
     */
    const val RES_VALUE_NULL_LITERAL = "@null"
    const val RES_VALUE_EMPTY_LITERAL = "@empty"

    /**
     * v2 round 2 follow-up #6: android: style parent normalization 용 prefix.
     * 예: parentStyleName = "android:Theme.Holo.Light" → namespace=ANDROID + name="Theme.Holo.Light" 로 lookup.
     */
    const val ANDROID_NS_PREFIX = "android"
    const val NS_NAME_SEPARATOR = ":"
}
