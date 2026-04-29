# W3D4 MATERIAL-FIDELITY — Design Spec (round 2)

**Status**: round 2 (post-pair-review, GO accepted by user)
**Date**: 2026-04-29
**Carry**: W3D3-α 의 Branch (B) contingency (`activity_basic_minimal.xml` Button) → primary (`activity_basic.xml` MaterialButton) 환원
**Predecessor**: `docs/work_log/2026-04-29_w3d3-l3-classloader/next-session-handoff.md`
**Prior art**: `docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md` (W3D1 framework loader)
**Round 1**: git history (commit `0ccb8c7`) — Codex+Claude 페어 리뷰가 4개 critical issue 발견 → round 2 가 σ FULL 채택, RES_AUTO mode 통일, R name canonicalization 추가, 비용 추정 보정.
**Pair-review verdict (round 1)**: full convergence (Q3 DISAGREE, Q5 AGREE) + set-converge (Q1 NUANCED, Q2 NUANCED, Q4 NUANCED, Q6 NUANCED). judge round 불필요.

---

## 0. Round 2 변경 요약 (round 1 → round 2 delta)

| 결정 | round 1 | round 2 (post-pair) |
|---|---|---|
| **Q1 namespace 모드** | hybrid (app=RES_AUTO + AAR=named per-package) | **traditional 통일** — 모든 non-framework = RES_AUTO. AAR 의 package 는 진단/dedupe 추적용으로만 추출 |
| **Q3 resolver 위임** | ρ (`getResolvedResource = getUnresolvedResource`, base 위임) | **σ FULL** — 9 method override (resolveResValue, dereference, findItemInStyle, findItemInTheme, getParent, getAllThemes, applyStyle, clearStyles, getResolvedResource) |
| **Q4 AAR 비용 추정** | 2-5초 cold-start | **sub-second 예상** (27/41 보유, 855KB 합산, I/O ~0.12초 실측). wall-clock 진단 의무 |
| **Q6 NEW** — R name canonicalization | 미언급 | **W3D3-α RJarSymbolSeeder 의 R$style underscore name (Theme_AxpFixture) ↔ XML dot name (Theme.AxpFixture) 매핑** 명시. Red test 우선 |
| **Q6 NEW** — duplicate attr ID 정책 | 미언급 | 같은 attr 이름이 여러 R class (appcompat/material/...) 에 등록. seeder 의 first-wins/dedup 정책 명시 |
| 자료구조 byNs | `Map<ResourceNamespace, NsBucket>` (3+ ns 가정) | 사실상 2-bucket (ANDROID + RES_AUTO). 자료구조 자체는 일반화 유지 |
| LayoutlibRenderResources LOC | ~80 | **~250** (9 override + chain walker) |
| 신규 unit test | ~25-30 | **~40-50** (resolver chain edge case + canonicalization 추가) |
| Q2 fallback 적용 surface | getStyle 만 | getStyle + getParent + findItemInStyle + findItemInTheme 일관 적용. byNs 순회 deterministic + duplicate 진단 |

---

## 1. 요구사항

### 1.1 T2 게이트 (T1 강화)

`LayoutlibRendererIntegrationTest.tier3_basic_primary` 가 contingency 발화 없이 primary 로 PASS:

```kotlin
@Tag("integration")
@Test
fun `tier3 basic primary — activity_basic renders SUCCESS with non-empty PNG`() {
    val (dist, layoutRoot, moduleRoot) = locateW3D3Triplet()
    val renderer = SharedLayoutlibRenderer.getOrCreate(
        distDir = dist,
        fixtureRoot = layoutRoot,
        sampleAppModuleRoot = moduleRoot,
        themeName = SessionConstants.DEFAULT_FIXTURE_THEME, // Theme.AxpFixture
        fallback = null,
    )
    val bytes = renderer.renderPng("activity_basic.xml")
    assertEquals(Result.Status.SUCCESS, renderer.lastSessionResult?.status)
    assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES)
    assertTrue(isPngMagic(bytes))
}
```

contingency helper 제거. `activity_basic_minimal.xml` 은 별도 `tier3_basic_minimal_smoke` 테스트로 carry 보존 (Q4 ξ + o 결정).

### 1.2 카운트 베이스라인 → 목표 (round 2 보정)

| 메트릭 | 베이스라인 | round 1 추정 | round 2 추정 |
|---|---|---|---|
| unit PASS | 167 | ~190+ | **~210+ (+40-50)** |
| integration PASS | 12 | 13 | 13 |
| integration SKIP | 1 (tier3-glyph) | 1 | 1 |
| 신규 main LOC | — | ~790 | **~960** (resolver +170) |
| 신규 test LOC | — | ~600 | **~780** (resolver chain +180) |

### 1.3 out-of-scope (carry 보존)

- tier3-glyph (W4+).
- POST-W2D6-POM-RESOLVE.
- CLI `--theme` 플래그, MCP renderRequest 의 themeName 필드.
- sample-app 외 다른 fixture 의 multi-fixture 통합.
- **full namespace-aware 모드** — round 2 가 traditional RES_AUTO 채택. namespace-aware 는 W4+ 후보 (callback/parser/seeder 동시 전환 필요, scope 큼).

---

## 2. 아키텍처 (round 2)

### 2.1 자료구조 (Q1 mode 통일 — traditional RES_AUTO)

```
LayoutlibResourceBundle (immutable, JVM-wide cache)
└── byNs: Map<ResourceNamespace, NsBucket>
    ├── [ANDROID]   → NsBucket: framework values (10 XML)
    └── [RES_AUTO]  → NsBucket: app + 41 AAR values (collapsed)
```

- 자료구조 일반화 유지 (`Map<Namespace, ...>`) — W4+ namespace-aware 전환 시 재구성 비용 0.
- 현 round 2 에서는 사실상 ANDROID + RES_AUTO 2 bucket 만 사용.
- AAR 별 `package` 는 dedupe 진단 용으로만 별도 보존 (resource 자체는 RES_AUTO bucket 통합).

### 2.2 Resolver (Q3 σ FULL — 9 override)

```
LayoutlibRenderResources(bundle, themeName)
  ├── getDefaultTheme()              → mDefaultTheme
  ├── getAllThemes()                 → mThemeStack (default theme + ancestor walk, ordered)
  ├── getStyle(ref)                  → bundle.getStyleExact(ref)
  │                                    ?: bundle.getStyleByName(ref.name)             // ns-exact-then-name
  ├── getParent(style)               → bundle.getStyleExact(parentRef)
  │                                    ?: bundle.getStyleByName(style.parentStyleName) // ns-exact-then-name
  ├── findItemInStyle(style, ref)    → style.getItem(ref) ?: walk(style.parent.getItem(ref) ...)
  ├── findItemInTheme(ref)           → for theme in mThemeStack: findItemInStyle(theme, ref)
  ├── resolveResValue(value)         → if (value is ref) chainWalk(value) else value
  ├── dereference(value)             → resolveResValue(value)  // alias
  ├── getUnresolvedResource(ref)     → bundle.getResource(ref)
  └── getResolvedResource(ref)       → resolveResValue(getUnresolvedResource(ref))

  + applyStyle(style, force)  → mThemeStack 갱신 (Bridge 가 호출)
  + clearStyles() / clearAllThemes() → mThemeStack reset
```

ns-exact-then-name fallback 은 `getStyle / getParent` 모두 일관 적용. `findItemInStyle` 은 style.getItem(ref) → parent walk (ns-agnostic getParent 활용 → cross-ns chain 따라감). `findItemInTheme` 은 mThemeStack 순회.

**chain walker 알고리즘** (resolveResValue):
```
function resolveResValue(value: ResourceValue): ResourceValue?
    seen = HashSet()
    current = value
    loop (max 10 hops, circular detection)
        if current.value 가 "@type/name" or "?attr/name" pattern 이면
            ref = parseReference(current.value)
            if ref in seen → return current  // circular abort
            seen.add(ref)
            current =
                if ref.type == ATTR → findItemInTheme(ref)
                else → getUnresolvedResource(ref)
            if current == null → return null
        else
            return current
```

### 2.3 Loader 계층

```
LayoutlibResourceValueLoader.loadOrGet(Args(distDataDir, sampleAppRoot, runtimeClasspathTxt))
  ├── loadFramework(distDataDir/values)        → 10 framework XML → NsBucket(ANDROID)
  ├── loadAppRes(sampleAppRoot/app/src/main/res/values)  → ~3 XML → NsBucket(RES_AUTO) accumulator
  ├── loadAarRes(runtimeClasspathTxt)          → 41 AAR walker
  │   for each .aar:
  │     - AndroidManifest.xml package 추출 (진단/dedupe-source 추적용)
  │     - res/values/values.xml 부재 → System.err 1줄 + skip (γ)
  │     - parse with namespace=RES_AUTO (mode 통일)
  │     - entries 누적 to RES_AUTO accumulator + diagnostic 매핑 (entry → AAR package)
  └── LayoutlibResourceBundle.build({ANDROID: framework, RES_AUTO: app+aar})
```

JVM-wide cache key = `Args(...)` 3-tuple. 동일 (dist, sampleAppRoot, classpathTxt) 면 동일 bundle.

**dedupe within RES_AUTO** (η round 2 보강):
- SimpleValue / StyleDef: later-wins. AAR 순회 순서 = `runtime-classpath.txt` 순서 (Gradle deterministic). app 의 res 는 마지막에 처리되어 sample-app 정의가 AAR 정의를 override.
- AttrDef: first-wins (W3D1 정책 보존).
- duplicate detection: 같은 (type, name) 의 conflict 발견 시 `System.err` 1줄 — `[LayoutlibResourceBundle] dup style 'colorPrimary' from material/appcompat — adopting later`.

### 2.4 Theme cross-ns parent chain (round 2)

`Theme.AxpFixture` (RES_AUTO) → `Theme.Material3.DayNight.NoActionBar` (RES_AUTO, material AAR 출처) → `Theme.Material3.Light.NoActionBar` (RES_AUTO, material AAR) → ... → `Theme.AppCompat.Light` (RES_AUTO, appcompat AAR) → `Theme.AppCompat` (RES_AUTO) → ... → `Theme` (ANDROID).

모든 non-framework parent 는 RES_AUTO bucket 의 single map 안에서 직접 lookup → ns-exact 가 그대로 hit. ANDROID 진입 시점 (Theme) 은 parent inference 가 framework name 임을 식별 (style name pattern + framework table 화이트리스트) 또는 Bridge 가 ANDROID ref 로 던질 때 ns-exact hit.

## 3. 컴포넌트 분해 (round 2)

### 3.1 신규 main (8 파일, ~960 LOC)

| # | 파일 | 책임 | LOC est (round 2) |
|---|---|---|---|
| 1 | `resources/AppLibraryResourceConstants.kt` | RUNTIME_CLASSPATH_TXT_PATH, AAR_VALUES_XML_PATH, AAR_ANDROID_MANIFEST_PATH, MANIFEST_PACKAGE_REGEX, MAX_REF_HOPS=10 | ~40 |
| 2 | `resources/ParsedNsEntry.kt` | namespace-tagged variant (round 2 에서는 RES_AUTO/ANDROID 만 사용) + sourcePackage diagnostic 필드 | ~70 |
| 3 | `resources/NsBucket.kt` | byType/styles/attrs immutable container | ~50 |
| 4 | `resources/NamespaceAwareValueParser.kt` | W3D1 parser 일반화 (namespace 인자) | ~120 |
| 5 | `resources/AarResourceWalker.kt` | runtime-classpath.txt → ZipFile → values.xml 파싱. namespace=RES_AUTO 고정. AAR package 는 진단 출력 only | ~140 |
| 6 | `resources/LayoutlibResourceBundle.kt` | byNs + getStyleExact/getStyleByName/getResource. dedupe 진단 + ANDROID/RES_AUTO 2-bucket build | ~200 |
| 7 | `resources/LayoutlibResourceValueLoader.kt` | 3-입력 통합. JVM-wide cache. wall-clock 진단 출력 | ~140 |
| 8 | `resources/LayoutlibRenderResources.kt` | **9 override + chain walker + theme stack 관리** (Q3 σ FULL) | ~250 |

### 3.2 변경 (4 파일)

| 파일 | 변경 |
|---|---|
| `LayoutlibRenderer.kt` | line 174 `FrameworkRenderResources` → `LayoutlibRenderResources(bundle, themeName)`. 생성자 5번째 인자 themeName |
| `SharedLayoutlibRenderer.kt` | RendererArgs 4-tuple → 5-tuple |
| `session/SessionConstants.kt` | `DEFAULT_FIXTURE_THEME = "Theme.AxpFixture"` 추가 |
| `classloader/RJarSymbolSeeder.kt` (round 2 NEW) | **R$style 의 underscore name (`Theme_AxpFixture`) → dot name (`Theme.AxpFixture`) canonicalization 추가**. duplicate attr ID dedupe 정책 명시 (first-wins per name) |

### 3.3 삭제 / rename (W3D1 흡수, round 1 동일)

| 기존 | 후속 |
|---|---|
| `FrameworkResourceBundle.kt` | 삭제 (LayoutlibResourceBundle 흡수) |
| `FrameworkRenderResources.kt` | 삭제 (LayoutlibRenderResources 대체) |
| `FrameworkResourceValueLoader.kt` | 삭제 (LayoutlibResourceValueLoader.loadFramework() 흡수) |
| `FrameworkValueParser.kt` | rename → `NamespaceAwareValueParser.kt` |
| `ParsedEntry.kt` | rename → `ParsedNsEntry.kt` |
| `ResourceLoaderConstants.kt` | 유지 |
| `StyleParentInference.kt` | 유지 |

### 3.4 신규 test (10 파일, ~780 LOC, ~40-50 case)

| # | 파일 | 케이스 수 | round 2 추가 항목 |
|---|---|---|---|
| 1 | `AppLibraryResourceConstantsTest` | ~3 | path/regex 무결성 |
| 2 | `NamespaceAwareValueParserTest` | ~5 | W3D1 동등 + namespace 정확 태깅 |
| 3 | `AarResourceWalkerTest` | ~6 | happy + γ skip + δ regex + 27/41 카운트 + **wall-clock 진단** + sourcePackage diagnostic |
| 4 | `LayoutlibResourceBundleTest` | ~7 | ns-agnostic/exact + η dedupe + duplicate diagnostic 로그 + cross-ns parent + RES_AUTO collapse 정합 |
| 5 | `LayoutlibResourceValueLoaderTest` | ~5 | 3-입력 통합 + cache 3-tuple + wall-clock 측정 |
| 6 | `LayoutlibRenderResourcesTest` (round 2 확장) | ~10 | defaultTheme + ns-exact/agnostic + **chain walker (`?attr` + `@color`)** + **circular detection** + **theme stack walk** + dereference + applyStyle/clearStyles |
| 7 | **`RNameCanonicalizationTest` (round 2 NEW)** | ~3 | R$style underscore↔dot 변환, RJarSymbolSeeder 와 bundle 의 name 정합 |
| 8 | **`RDuplicateAttrIdTest` (round 2 NEW)** | ~3 | 같은 attr 이름이 여러 R class 에 있을 때 first-wins per name + diagnostic |
| 9 | `MaterialFidelityIntegrationTest` (unit-level) | ~4 | real bundle Theme.AxpFixture parent walk to Theme + `?attr/colorPrimary` chain expansion |
| 10 | (수정) `LayoutlibRendererIntegrationTest` | tier3_basic_primary + tier3_basic_minimal_smoke | (ξ) 분리 |

**Red-test-first 의무화 (Codex Q6)**: 구현 전 #6 (chain walker), #7 (canonicalization), #8 (duplicate attr) 테스트가 fail 하는 상태에서 시작. green 으로 가는 과정이 implementation 의 정합성 증거.

---

## 4. AAR walker 정책 (round 2)

### 4.1 (γ) values.xml 부재 AAR — lenient + 명시 로깅 (round 1 동일)

```kotlin
val valuesEntry = zip.getEntry("res/values/values.xml")
if (valuesEntry == null) {
    System.err.println("[AarResourceWalker] $aarPath skipped — res/values/values.xml 없음")
    return null
}
```

27/41 보유 (Codex 실측). 1+ AAR 도 PASS 못 하면 sanity guard 로 IllegalStateException.

### 4.2 (δ) AndroidManifest.xml 의 package 추출 — **진단/dedupe-source 추적 only**

round 1 에서는 namespace 결정 입력. round 2 에서는 **diagnostic only** (mode 통일로 모든 AAR = RES_AUTO):

```kotlin
val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
val pkg = MANIFEST_PACKAGE_REGEX.find(manifestText)?.groupValues?.get(1)
    ?: throw IllegalStateException("$aarPath: AndroidManifest 의 package 추출 실패")
require(pkg.isNotEmpty()) { "$aarPath: package empty" }
// pkg 는 ParsedNsEntry.sourcePackage 로 보존 — duplicate diagnostic 에서 활용
```

### 4.3 (η) per-namespace dedupe (round 2 보강)

namespace 통일로 RES_AUTO 안 모든 entry collapse. dedupe 정책:
- SimpleValue / StyleDef: later-wins. 순회 순서 = `runtime-classpath.txt` 순서 + 마지막 sample-app res. Gradle 결정성 의존.
- AttrDef: first-wins.
- conflict 시 진단 로그: `[LayoutlibResourceBundle] dup <type> '<name>' from <pkgA>/<pkgB> — adopting <winner>` (Codex Q2 권고).

deterministic 보장: `byNs.values` 순회 시 ANDROID → RES_AUTO 고정 (LinkedHashMap insertion order).

### 4.4 wall-clock 진단 (round 2 NEW, Codex Q4)

```kotlin
val t0 = System.nanoTime()
val frameworkBucket = loadFramework(...)
val tFw = (System.nanoTime() - t0) / 1_000_000
val tApp = ... // 비슷
val tAar = ...
System.err.println(
    "[LayoutlibResourceValueLoader] cold-start framework=${tFw}ms app=${tApp}ms aar=${tAar}ms total=${tFw+tApp+tAar}ms"
)
```

Codex 실측: I/O ~0.12초. parser 포함 sub-second 예상. 측정값으로 spec 의 "수 초" 추정 폐기.

---

## 5. Resolver 시맨틱 (Q3 σ FULL — round 2 핵심 변경)

### 5.1 9 override 의 자세한 구현

```kotlin
class LayoutlibRenderResources(
    private val bundle: LayoutlibResourceBundle,
    private val themeName: String,
) : RenderResources() {

    private val mDefaultTheme: StyleResourceValue = bundle.getStyleByName(themeName)
        ?: StyleResourceValueImpl(
            ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, themeName),
            null, null,
        )

    // theme stack — applyStyle / clearStyles 가 갱신. 초기엔 default theme + parent walk.
    private val mThemeStack: MutableList<StyleResourceValue> = mutableListOf<StyleResourceValue>().apply {
        add(mDefaultTheme)
        var cur: StyleResourceValue? = mDefaultTheme
        var hops = 0
        while (cur != null && hops < ResourceLoaderConstants.MAX_THEME_HOPS) {
            val parent = getParent(cur)
            if (parent == null) break
            add(parent)
            cur = parent
            hops++
        }
    }

    override fun getDefaultTheme(): StyleResourceValue = mDefaultTheme

    override fun getAllThemes(): List<StyleResourceValue> = mThemeStack.toList()

    override fun getStyle(ref: ResourceReference): StyleResourceValue? =
        bundle.getStyleExact(ref) ?: bundle.getStyleByName(ref.name)

    override fun getParent(style: StyleResourceValue): StyleResourceValue? {
        val parentRef = style.parentStyle ?: return resolveByName(style.parentStyleName)
        return bundle.getStyleExact(parentRef) ?: bundle.getStyleByName(parentRef.name)
    }

    private fun resolveByName(name: String?): StyleResourceValue? =
        name?.let { bundle.getStyleByName(it) }

    override fun getUnresolvedResource(ref: ResourceReference): ResourceValue? =
        bundle.getResource(ref)

    override fun getResolvedResource(ref: ResourceReference): ResourceValue? {
        val unresolved = getUnresolvedResource(ref) ?: return null
        return resolveResValue(unresolved)
    }

    override fun resolveResValue(value: ResourceValue?): ResourceValue? {
        if (value == null) return null
        var current: ResourceValue = value
        val seen = HashSet<ResourceReference>()
        var hops = 0
        while (hops < AppLibraryResourceConstants.MAX_REF_HOPS) {
            val text = current.value ?: return current
            val refLike = parseRef(text) ?: return current
            if (seen.contains(refLike)) {
                System.err.println("[LayoutlibRenderResources] circular ref detected: ${current.name} → $refLike")
                return current
            }
            seen.add(refLike)
            val next = if (refLike.resourceType == ResourceType.ATTR) {
                findItemInTheme(refLike) ?: return current
            } else {
                getUnresolvedResource(refLike) ?: return current
            }
            current = next
            hops++
        }
        return current
    }

    override fun dereference(value: ResourceValue?): ResourceValue? = resolveResValue(value)

    override fun findItemInStyle(
        style: StyleResourceValue,
        ref: ResourceReference,
    ): ResourceValue? {
        var current: StyleResourceValue? = style
        var hops = 0
        while (current != null && hops < AppLibraryResourceConstants.MAX_THEME_HOPS) {
            val item = current.getItem(ref)
            if (item != null) return item
            current = getParent(current)
            hops++
        }
        return null
    }

    override fun findItemInTheme(ref: ResourceReference): ResourceValue? {
        for (theme in mThemeStack) {
            val item = findItemInStyle(theme, ref)
            if (item != null) return item
        }
        return null
    }

    override fun applyStyle(style: StyleResourceValue, force: Boolean) {
        if (force) mThemeStack.clear()
        if (!mThemeStack.contains(style)) mThemeStack.add(0, style)  // top of stack
    }

    override fun clearStyles() { mThemeStack.clear(); mThemeStack.add(mDefaultTheme) }

    override fun clearAllThemes() { mThemeStack.clear() }
}
```

### 5.2 LayoutlibResourceBundle 의 lookup API

```kotlin
class LayoutlibResourceBundle(...) {
    fun getStyleExact(ref: ResourceReference): StyleResourceValue? =
        byNs[ref.namespace]?.styles?.get(ref.name)

    fun getStyleByName(name: String): StyleResourceValue? {
        // deterministic ANDROID → RES_AUTO 순회, 첫 매치
        for (bucket in byNs.values) {
            bucket.styles[name]?.let { return it }
        }
        return null
    }

    fun getResource(ref: ResourceReference): ResourceValue? =
        byNs[ref.namespace]?.byType?.get(ref.resourceType)?.get(ref.name)
}
```

`byNs` 는 LinkedHashMap (insertion order ANDROID → RES_AUTO). 순회 결정성 보장.

---

## 6. 호출 흐름

### 6.1 Cold-start

```
SharedLayoutlibRenderer.getOrCreate(dist, fixture, sampleAppRoot, "Theme.AxpFixture", null)
  └→ RendererArgs miss → new LayoutlibRenderer(...)

LayoutlibRenderer.renderViaLayoutlib("activity_basic.xml")
  ├→ LayoutlibResourceValueLoader.loadOrGet(Args(distDataDir, sampleAppRes, classpathTxt))
  │     ├→ loadFramework         → 10 XML → NsBucket(ANDROID)         [t=N1ms]
  │     ├→ loadAppRes             → ~3 XML → NsBucket(RES_AUTO) acc    [t=N2ms]
  │     ├→ loadAarRes             → 41 AAR walker → 27 with values
  │     │     for each AAR:
  │     │       ZipFile → manifest package (진단)
  │     │       values.xml 부재 → skip + 1줄 로깅
  │     │       parse with namespace=RES_AUTO → entries 누적          [t=N3ms]
  │     │       sourcePackage 매핑 보존
  │     └→ LayoutlibResourceBundle.build({ANDROID: framework, RES_AUTO: app+aar})
  │           dedupe + duplicate 진단 로그
  │           cross-ns style parent inference (StyleParentInference)
  │           ConcurrentHashMap put → 진단 출력 (cold-start ${total}ms)
  └→ LayoutlibRenderResources(bundle, "Theme.AxpFixture")
       ctor 에서 mThemeStack 초기화 (default theme + parent walk via getParent ns-exact-then-name)
       → SessionParams.resources
```

### 6.2 Bridge 의 reverse callback (round 2)

```
Bridge.createSession(params) → inflate
  ├→ getDefaultTheme()         → mDefaultTheme (Theme.AxpFixture)
  ├→ applyStyle(...) / clearStyles() → mThemeStack 갱신
  ├→ findItemInTheme(attr_ref)   → for theme in stack: findItemInStyle(theme, attr_ref)
  │     findItemInStyle 가 style 의 items 검사 → parent walk
  ├→ resolveResValue(value)    → chain walker (?attr → findItemInTheme, @ref → getUnresolvedResource)
  └→ getResolvedResource(ref) → unresolved → resolveResValue
```

모든 Bridge 호출 path 가 구체적 override 로 dispatch. base = null 의 chain 끊김 위험 차단.

---

## 7. 위험 / contingency (round 2 보강)

### 7.1 R name canonicalization (Codex Q6 NEW)

W3D3-α 의 `RJarSymbolSeeder` 가 R$style 클래스의 field 를 walk → `Theme_AxpFixture` (underscore) name 으로 emit. 그러나 우리 bundle 은 XML 파싱으로 `Theme.AxpFixture` (dot) name 등록. ID 역참조 시 mismatch.

**해결**: `RJarSymbolSeeder` 의 R$style 처리 단계에서 underscore → dot 매핑 추가 — `Theme_AxpFixture` 를 보면 `Theme.AxpFixture` 로 등록. R$attr/R$dimen/R$color 등 다른 type 은 단순 (underscore 가 의미 있는 separator 아님 — name 그대로).

**red test 우선** (Codex 권고): `RNameCanonicalizationTest` 가 mock R class 에 `Theme_AxpFixture` 필드 + bundle 에 `Theme.AxpFixture` style 를 두고 ID 역참조 → bundle hit 검증.

### 7.2 Duplicate attr ID across R classes (Codex Q6 NEW)

같은 attr 이름 (`colorPrimary`) 가 multiple R class (appcompat R$attr / material R$attr / 기타) 에 register. 다른 ID 값. seeder 가 first-wins 으로 dedup 안 하면 layoutlib 이 잘못된 ID 받아 lookup 실패.

**해결**: `RJarSymbolSeeder` 에 `seenAttrNames: HashSet<String>` 추가. 첫 등록 후 같은 name 의 다른 R class 등장 시 skip + 진단 로그 (`[RJarSymbolSeeder] dup attr 'colorPrimary' from <pkg> — first-wins, kept ${earlierId}`).

**red test 우선**: `RDuplicateAttrIdTest` 가 2개 mock R$attr 클래스 (다른 package, 동명 attr) 시드 → seeder 의 first-wins 검증.

### 7.3 Material3 vs MaterialComponents 핸드오프 오기 (round 1 그대로 해결)

핸드오프의 MaterialComponents 가 실 Material3. spec 은 actual themes.xml 에 정합.

### 7.4 Resolver 의 chain walker MAX_REF_HOPS (round 2)

cycle 또는 deep chain 의 무한 루프 방지. `MAX_REF_HOPS = 10`, `MAX_THEME_HOPS = 20`. 초과 시 진단 로그 + 현재 value 반환 (graceful — Bridge 가 처리).

**근거**: 일반 Material theme 의 attr ref chain depth 는 보통 3-5 hop. theme 의 parent depth 는 ~10 (Theme.Material3.* → Theme.AppCompat.* → Theme). 양쪽 다 2-3 배 buffer.

### 7.5 LM-α-C 재발 가능성 (round 1 보존)

α 의 contingency keyword matching 이 새 fixture 에서 surface 가능. round 2 가 root cause (theme resolution 정확화 + canonicalization) 해결 → α contingency deprecated → `tier3_basic_primary` 강제 PASS.

### 7.6 LM-W3D3-A 회피 (round 1 보존, round 2 강화)

페어 round 1 자체가 LM-W3D3-A 의 적용 — convergent 하지만 empirical 검증 (Codex 의 27/41 + 855KB + 0.12s 실측, Claude 의 javap RR base null 검증) 가 verdict 의 신뢰성. round 2 implementation 에서도 wall-clock 진단 + canonicalization 검증으로 동일 규율.

### 7.7 LM-G (Codex CLI bypass — round 1 동일)

`codex exec --skip-git-repo-check --sandbox danger-full-access` 직접 CLI. codex-rescue subagent 사용 안 함.

---

## 8. 결정 기록 (round 2 최종)

| Q | round 1 | round 2 | 근거 (페어 reviewer) |
|---|---|---|---|
| Q1 (bundle 모드) | A namespace-aware 단일 (hybrid app=RES_AUTO + AAR=named) | **traditional 통일** — 모든 non-framework = RES_AUTO | Codex: hybrid 는 not pure symmetric model. callback `isResourceNamespacingRequired()=false` + parser RES_AUTO 와 정합. namespace-aware 로 가려면 callback/parser/seeder 동시 전환 필요 (scope 큼) |
| Q2 (themeName 도달) | 2 RendererArgs 5-tuple cache key | **2 그대로** | 페어 무영향 |
| Q3a (γ values.xml lenient skip) | γ + 1줄 로깅 | **γ 그대로** | 페어 무영향 |
| Q3b (δ AndroidManifest pkg) | namespace 결정 입력 | **진단/dedupe-source 추적용** (mode 통일로) | Codex Q1 권고 |
| Q3c (η dedupe) | per-ns later-wins / first-wins | **RES_AUTO collapse 후 동일 정책 + duplicate 진단 로그** | Codex Q2 권고 (deterministic 순회, duplicate 가시화) |
| Q4a (ξ test 분리) | helper 제거 + 2 테스트 분리 | **ξ 그대로** | 페어 무영향 |
| Q4b (o minimal carry) | 영구 유지 | **o 그대로** | 페어 무영향 |
| Q4c (resolver 위임) | ρ resolved=unresolved (base 위임) | **σ FULL** — 9 override (resolveResValue, dereference, findItemInStyle, findItemInTheme, getParent, getAllThemes, applyStyle, clearStyles, getResolvedResource) | Claude+Codex full convergent DISAGREE — base methods stub, ρ 는 resolution bypass. layoutlib-api 의 ResourceResolver 는 sdk-common 별도 |
| **R-1 (NEW)** R name canonicalization | 미언급 | **Theme_AxpFixture (R underscore) → Theme.AxpFixture (XML dot) 매핑** + RJarSymbolSeeder 변경 | Codex Q6 권고 |
| **R-2 (NEW)** Duplicate attr ID | 미언급 | **first-wins per name + 진단 로그** | Codex Q6 권고 |
| **R-3 (NEW)** Wall-clock 진단 | 미언급 | **cold-start ms 측정 + 27/41 카운트 출력** | Codex Q4 권고 (실측 ~0.12초) |
| **R-4 (NEW)** Red-test-first | 부분적 | **chain walker / canonicalization / duplicate attr 모두 red test 부터** | Codex Q6 권고 |

---

## 9. 페어 리뷰 round 1 결과 (보존, round 2 plan 의 input)

### 9.1 양 reviewer convergence

| Q | Claude verdict | Codex verdict | 종합 |
|---|---|---|---|
| Q1 | NUANCED (equals 시맨틱 명시) | NUANCED (mode 통일 필요) | set-converge → mode 통일 채택 |
| Q2 | AGREE (강한 javap 증거) | NUANCED (fallback OK 단 전체 surface) | set-converge → uniform fallback |
| Q3 | DISAGREE (ρ → σ) | DISAGREE (resolution bypass) | **FULL convergence** → σ FULL |
| Q4 | NUANCED (timing diagnostics) | NUANCED (sub-second 예상, empirical) | set-converge → empirical 보정 |
| Q5 | AGREE | AGREE | **FULL convergence** |
| Q6 | REVISE_REQUIRED (4 weak points) | NUANCED (R canonicalization + dup attr ID) | set-converge → both 적용 |

### 9.2 pair-review 의 cross-family 가치

- Codex 실측: 27/41 AAR 가 values.xml 보유, 855KB 합산, I/O ~0.12초 → spec 의 "2-5초" 추정 폐기.
- Claude 의 javap 검증: RenderResources base 의 `resolveResValue/findItemInStyle/dereference/getParent/getAllThemes` 모두 null/empty stub 확정 → ρ 무너짐.
- Codex NEW: R underscore↔dot 의 canonicalization 위험 + duplicate attr ID 정책 → W3D3-α 의 RJarSymbolSeeder 회귀 위험 차단.

→ judge round 불필요. 양 reviewer 의 evidence-based critique 가 round 2 의 6개 결정 변경의 충분한 근거.

---

## 10. acceptance gate (round 2)

W3D4 종결 판정:

1. ✅ `tier3_basic_primary` PASS — `assertEquals("activity_basic.xml", ...)` 강제 + SUCCESS + PNG > 1000.
2. ✅ `tier3_basic_minimal_smoke` PASS — minimal carry 보존.
3. ✅ unit ~210+ PASS, 모든 신규 10 test 파일 PASS (resolver chain 7 + canonicalization 8 + dup attr id 9 추가).
4. ✅ integration 12 + 1 (분리 추가) = 13 PASS, 1 SKIP (tier3-glyph).
5. ✅ BUILD SUCCESSFUL, smoke "ok".
6. ✅ wall-clock 진단 — cold-start total < 5초 (안전 buffer, 실 측 sub-second 기대).
7. ✅ R name canonicalization 검증 — `RNameCanonicalizationTest` PASS.
8. ✅ Duplicate attr ID dedup — `RDuplicateAttrIdTest` PASS.
9. ✅ `docs/work_log/2026-04-29_w3d4-material-fidelity/` 생성 + handoff (carry 0 또는 다음 milestone).
10. ✅ `docs/plan/08-integration-reconciliation.md` §7.7.6 에 close 1줄.
11. ✅ MEMORY.md 갱신 (필요 시).

---

## 11. 다음 단계 (writing-plans 입력)

이 spec 이 **GO 승인되면** writing-plans 스킬 invoke. plan v1 골격:

- T1: AppLibraryResourceConstants + ParsedNsEntry + NsBucket (자료구조 + 상수)
- T2: NamespaceAwareValueParser (W3D1 일반화)
- T3: AarResourceWalker (γ + δ 진단 + wall-clock)
- T4: LayoutlibResourceBundle (build + dedupe + 진단 로그)
- T5: LayoutlibResourceValueLoader (3-입력 통합 + cache)
- T6: LayoutlibRenderResources (9 override + chain walker + theme stack)
- T7: RJarSymbolSeeder canonicalization (R-1) + duplicate attr ID (R-2)
- T8: LayoutlibRenderer + SharedLayoutlibRenderer 통합 (themeName 5-tuple)
- T9: integration test 분리 (tier3_basic_primary + tier3_basic_minimal_smoke)
- T10: work_log + handoff + 08 §7.7.6 close

각 task 는 TDD red-green + atomic commit. plan v1 작성 후 Codex+Claude 페어 round 2 (plan-review) 진행.
