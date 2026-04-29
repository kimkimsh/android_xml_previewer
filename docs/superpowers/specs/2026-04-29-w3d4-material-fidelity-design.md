# W3D4 MATERIAL-FIDELITY — Design Spec (round 1)

**Status**: round 1 (Codex 페어 리뷰 entry point)
**Date**: 2026-04-29
**Carry**: W3D3-α 의 Branch (B) contingency (`activity_basic_minimal.xml` Button) → primary (`activity_basic.xml` MaterialButton) 환원
**Predecessor**: `docs/work_log/2026-04-29_w3d3-l3-classloader/next-session-handoff.md`
**Prior art**: `docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md` (W3D1 framework loader)
**Spec author**: Claude (Q1-Q4 사용자 결정 반영)

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

contingency helper (`renderWithMaterialFallback`) 는 본 테스트에서 제거. `activity_basic_minimal.xml` 은 별도 `tier3_basic_minimal_smoke` 테스트로 carry 보존 (Q4 ξ + o 결정).

### 1.2 카운트 베이스라인 → 목표

| 메트릭 | 베이스라인 (W3D3-α 종결) | W3D4 목표 |
|---|---|---|
| unit PASS | 167 | ~190+ (+20-30) |
| integration PASS | 12 | 13 (1 분리 +1) |
| integration SKIP | 1 (tier3-glyph) | 1 |
| 신규 main LOC | — | ~790 |
| 신규 test LOC | — | ~600 |

### 1.3 out-of-scope (carry 보존)

- tier3-glyph (W4+).
- POST-W2D6-POM-RESOLVE.
- CLI `--theme` 플래그, MCP renderRequest 의 themeName 필드.
- sample-app 외 다른 fixture 의 multi-fixture 통합.

---

## 2. 아키텍처

### 2.1 자료구조 (Q1 — A: namespace-aware 단일 bundle)

```
LayoutlibResourceBundle (immutable, JVM-wide cache)
└── byNs: Map<ResourceNamespace, NsBucket>
    └── NsBucket
        ├── byType:  Map<ResourceType, Map<String, ResourceValue>>
        ├── styles:  Map<String, StyleResourceValue>
        └── attrs:   Map<String, AttrResourceValue>
```

W3D1 `FrameworkResourceBundle` 의 자료구조 위에 namespace 한 층 추가. NsBucket 안의 byType/styles/attrs 는 W3D1 그대로.

### 2.2 Resolver

```
LayoutlibRenderResources(bundle: LayoutlibResourceBundle, themeName: String)
  ├── getDefaultTheme()              → bundle.getStyleByName(themeName)              // ns-agnostic
  ├── getStyle(ref)                  → bundle.getStyleExact(ref)
  │                                    ?: bundle.getStyleByName(ref.name)            // ns-exact → ns-agnostic
  ├── getUnresolvedResource(ref)     → bundle.getResource(ref)                       // ns-exact only
  └── getResolvedResource(ref)       → getUnresolvedResource(ref)                    // (Q4 ρ) base 위임
```

3 entry-point — `getStyle` 만 ns-agnostic fallback (parent chain 보존). resource value 는 ns-exact 만.

### 2.3 Loader 계층

```
LayoutlibResourceValueLoader.loadOrGet(Args(distDataDir, sampleAppRoot, runtimeClasspathTxt))
  ├── loadFramework(distDataDir/values)        → 10 framework XML → NsBucket(ANDROID)
  ├── loadAppRes(sampleAppRoot/app/src/main/res/values)  → ~3 XML → NsBucket(RES_AUTO, app)
  ├── loadAarRes(runtimeClasspathTxt)          → 41 AAR walker → N × NsBucket(<aar-ns>)
  └── LayoutlibResourceBundle.build(allNsBuckets)
```

JVM-wide cache key = `Args(...)` 3-tuple. 동일 (dist, sampleAppRoot, classpathTxt) 면 동일 bundle 재사용.

### 2.4 Theme 의 cross-namespace parent chain

`Theme.AxpFixture` (RES_AUTO, sample-app 정의) → `Theme.Material3.DayNight.NoActionBar` (com.google.android.material AAR) → `Theme.Material3.Light.NoActionBar` (동) → ... → `Theme.AppCompat.Light` (androidx.appcompat AAR) → `Theme.AppCompat` (동) → ... → `Theme` (ANDROID).

분산된 3+ namespace 의 style 들이 단일 bundle 안에 namespace 별로 저장. layoutlib base `RenderResources.resolveValue` 가 parent walk 시 name-only chain 따라가도, 우리의 `bundle.getStyleByName(name)` namespace-agnostic helper 가 모든 NsBucket 합집합 walk 로 응답.

---

## 3. 컴포넌트 분해

### 3.1 신규 main (8 파일, ~790 LOC)

| # | 파일 | 책임 | LOC est |
|---|---|---|---|
| 1 | `resources/AppLibraryResourceConstants.kt` | RUNTIME_CLASSPATH_TXT_PATH, AAR_VALUES_XML_PATH, AAR_ANDROID_MANIFEST_PATH, MANIFEST_PACKAGE_REGEX | ~30 |
| 2 | `resources/ParsedNsEntry.kt` | W3D1 ParsedEntry 의 namespace-tagged 변형 (SimpleValue/AttrDef/StyleDef + namespace 필드) | ~60 |
| 3 | `resources/NsBucket.kt` | 단일 namespace 의 byType/styles/attrs immutable container | ~50 |
| 4 | `resources/NamespaceAwareValueParser.kt` | W3D1 FrameworkValueParser 의 일반화 (namespace 인자) | ~120 |
| 5 | `resources/AarResourceWalker.kt` | runtime-classpath.txt → ZipFile → AndroidManifest package 추출 + values.xml 파싱 + (γ) skip + (δ) regex | ~150 |
| 6 | `resources/LayoutlibResourceBundle.kt` | namespace-aware 통합 bundle. ns-agnostic + ns-exact getStyle. cross-ns parent inference | ~180 |
| 7 | `resources/LayoutlibResourceValueLoader.kt` | 3-입력 통합 loader. JVM-wide cache (3-tuple key) | ~120 |
| 8 | `resources/LayoutlibRenderResources.kt` | namespace-aware RenderResources subclass | ~80 |

### 3.2 변경 (4 파일)

| 파일 | 변경 |
|---|---|
| `LayoutlibRenderer.kt` | line 174 `FrameworkRenderResources` → `LayoutlibRenderResources(bundle, themeName)`. 생성자 5번째 인자 themeName |
| `SharedLayoutlibRenderer.kt` | RendererArgs 4-tuple → 5-tuple (themeName 추가). getOrCreate 시그니처 |
| `session/SessionConstants.kt` | DEFAULT_FRAMEWORK_THEME 보존 + `DEFAULT_FIXTURE_THEME = "Theme.AxpFixture"` 신규 추가 |
| `session/SessionParamsFactory.kt` | (없음 — Q4 ρ 결정에 따라 setTheme 호출 추가 안 함) |

### 3.3 삭제 / rename (W3D1 흡수)

| 기존 | 후속 | 이유 |
|---|---|---|
| `resources/FrameworkResourceBundle.kt` | **삭제** | LayoutlibResourceBundle 흡수 |
| `resources/FrameworkRenderResources.kt` | **삭제** | LayoutlibRenderResources 대체 |
| `resources/FrameworkResourceValueLoader.kt` | **삭제** | LayoutlibResourceValueLoader.loadFramework() 흡수 |
| `resources/FrameworkValueParser.kt` | rename → `NamespaceAwareValueParser.kt` | 로직 보존 + namespace 인자 |
| `resources/ParsedEntry.kt` | rename → `ParsedNsEntry.kt` | namespace 필드 추가 |
| `resources/ResourceLoaderConstants.kt` | **유지** | REQUIRED_FILES (10 framework XML) |
| `resources/StyleParentInference.kt` | **유지** | namespace 무관 알고리즘 |

### 3.4 신규 test (8 파일, ~600 LOC)

| # | 파일 | 케이스 수 |
|---|---|---|
| 1 | `AppLibraryResourceConstantsTest.kt` | ~3 (path/regex 무결성) |
| 2 | `NamespaceAwareValueParserTest.kt` | ~5 (W3D1 동등 + namespace 정확 태깅) |
| 3 | `AarResourceWalkerTest.kt` | ~5 (happy + γ skip + δ regex + 41 카운트) |
| 4 | `LayoutlibResourceBundleTest.kt` | ~6 (ns-agnostic/exact + η dedupe + cross-ns parent) |
| 5 | `LayoutlibResourceValueLoaderTest.kt` | ~4 (3-입력 통합 + cache 3-tuple) |
| 6 | `LayoutlibRenderResourcesTest.kt` | ~5 (defaultTheme + ns-exact 분기 + ρ resolved=unresolved) |
| 7 | `MaterialFidelityIntegrationTest.kt` (unit-level) | ~3 (real bundle Theme.AxpFixture parent walk to Theme) |
| 8 | (수정) `LayoutlibRendererIntegrationTest.kt` | tier3_basic_primary + tier3_basic_minimal_smoke 분리 |

신규 unit ~25-30 case (167 → 192-197). integration 12 → 13.

---

## 4. AAR walker 정책 (Q3 결정)

### 4.1 (γ) values.xml 부재 AAR — lenient + 명시 로깅

```kotlin
val valuesEntry = zip.getEntry("res/values/values.xml")
if (valuesEntry == null) {
    System.err.println("[AarResourceWalker] $aarPath skipped — res/values/values.xml 없음")
    return null  // 호출자 caller 가 null 결과 누적 안 함
}
```

41 AAR 중 1 개 이상 PASS 못 하면 `LayoutlibResourceValueLoader.build` 가 sanity guard 로 IllegalStateException ("AAR 0개").

### 4.2 (δ) AndroidManifest.xml 의 package 추출

```kotlin
val manifestEntry = zip.getEntry("AndroidManifest.xml")
    ?: throw IllegalStateException("$aarPath: AndroidManifest.xml 없음 — AAR 형식 위반")
val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
val match = MANIFEST_PACKAGE_REGEX.find(manifestText)
    ?: throw IllegalStateException("$aarPath: AndroidManifest 의 package 추출 실패")
val pkg = match.groupValues[1]
val namespace = ResourceNamespace.fromPackageName(pkg)
```

`MANIFEST_PACKAGE_REGEX = Regex("""package\s*=\s*"([^"]+)"""")`. AAR 의 manifest 는 plain text 표준 (binary XML 아님).

### 4.3 (η) per-namespace dedupe

namespace 가 같은 entry 만 dedupe 대상. W3D1 정책 그대로:

- SimpleValue / StyleDef: later-wins (runtime-classpath.txt 순서가 결정)
- AttrDef: first-wins (W3D1 pair-review F1 보존)

namespace 가 다르면 (e.g., `colorPrimary` 가 ANDROID 와 RES_AUTO 양쪽) 별도 NsBucket 에 독립 보존.

---

## 5. RenderResources 시맨틱 (Q4 ρ)

### 5.1 위임 모델

layoutlib base `RenderResources.resolveValue(ResourceValue)` 는 production-grade — `?attr/X` 의 theme stack walk, `@android:color/Y` 의 namespace dispatch, circular detection 까지 포함. 우리 subclass 는 entry-point 만 정확 구현:

```kotlin
override fun getDefaultTheme(): StyleResourceValue = mDefaultTheme
override fun getStyle(ref: ResourceReference): StyleResourceValue? {
    // ns-exact 우선 → 미스 시 ns-agnostic name-only fallback.
    // values.xml 파싱 시점에 parent="Theme.AppCompat" 의 namespace 는 미지 (AAPT 미통과 plain XML).
    // Bridge 의 parent walk 가 wrong-ns ref 던질 때 chain 끊어짐 방지.
    return bundle.getStyleExact(ref) ?: bundle.getStyleByName(ref.name)
}
override fun getUnresolvedResource(ref: ResourceReference): ResourceValue? =
    bundle.getResource(ref) // ns-exact 만 — value 는 의미적으로 정확 ns 매칭 필수 (동명 다른 의미)
override fun getResolvedResource(ref: ResourceReference): ResourceValue? =
    getUnresolvedResource(ref)
```

### 5.2 LayoutlibResourceBundle 의 lookup API

```kotlin
class LayoutlibResourceBundle(...) {
    /** ns-exact style lookup. 정상 경로. */
    fun getStyleExact(ref: ResourceReference): StyleResourceValue? =
        byNs[ref.namespace]?.styles?.get(ref.name)

    /** ns-agnostic style lookup. parent chain 의 plain-text ref fallback 용.
     *  순회 순서: ANDROID → RES_AUTO → 각 AAR ns. 첫 매치 반환. */
    fun getStyleByName(name: String): StyleResourceValue? =
        byNs.values.firstNotNullOfOrNull { it.styles[name] }

    /** ns-exact resource lookup. value 는 항상 ns-exact (동명 다른 의미 보존). */
    fun getResource(ref: ResourceReference): ResourceValue? =
        byNs[ref.namespace]?.byType?.get(ref.resourceType)?.get(ref.name)
}
```

`getStyleByName` 만 ns-agnostic — parent walk 의 plain-text fallback. 자주 호출되지 않으므로 최적화 불필요.

### 5.3 default theme fallback (W3D1 LM-3 보존)

```kotlin
private val mDefaultTheme: StyleResourceValue = bundle.getStyleByName(themeName)
    ?: StyleResourceValueImpl(
        ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, themeName),
        /* parentStyle */ null,
        /* libraryName */ null,
    )
```

bundle 내 themeName style 이 없어도 empty StyleResourceValueImpl. layoutlib base 가 self-chain 만 수행하고 NPE 안 남.

---

## 6. 호출 흐름

### 6.1 Cold-start

```
SharedLayoutlibRenderer.getOrCreate(dist, fixture, sampleAppRoot, themeName="Theme.AxpFixture", fallback=null)
  └→ RendererArgs cache miss → new LayoutlibRenderer(...)

LayoutlibRenderer.renderViaLayoutlib("activity_basic.xml")
  ├→ LayoutlibResourceValueLoader.loadOrGet(Args(distDataDir, sampleAppRes, classpathTxt))
  │     ├→ loadFramework      → 10 XML → NsBucket(ANDROID)
  │     ├→ loadAppRes          → ~3 XML → NsBucket(RES_AUTO sample-app)
  │     ├→ loadAarRes          → 41 AAR walker → N NsBucket
  │     │     for each .aar in classpath:
  │     │       ZipFile → AndroidManifest.xml package 추출 (δ)
  │     │       res/values/values.xml 부재 → skip + 1줄 로깅 (γ)
  │     │       NamespaceAwareValueParser.parse(stream, namespace)
  │     └→ LayoutlibResourceBundle.build(allNsBuckets)
  │           per-ns dedupe (η)
  │           cross-ns parent inference (StyleParentInference name-only)
  │           ConcurrentHashMap put → return
  └→ LayoutlibRenderResources(bundle, "Theme.AxpFixture") → SessionParams.resources
```

### 6.2 Bridge 의 reverse callback

```
Bridge.createSession(params) → inflate
  └→ RenderResources base 의 resolveValue / resolveResource 호출
       getStyle(ResourceReference("Theme.AxpFixture", RES_AUTO))
         → bundle.getStyleExact(ref) HIT (RES_AUTO NsBucket.styles["Theme.AxpFixture"])
         → parent="Theme.Material3.DayNight.NoActionBar" (ns 정보 없음 — plain-text values.xml)
       getStyle(ResourceReference("Theme.Material3.DayNight.NoActionBar", ?))
         → bundle.getStyleExact(ref) MISS (Bridge 가 RES_AUTO 던지면 RES_AUTO 에 없음)
         → bundle.getStyleByName(ref.name) HIT (material AAR ns NsBucket)
       ... (재귀 walk: 동일 ns-exact → ns-agnostic 패턴 으로 Theme.AppCompat → Theme 까지)
```

---

## 7. 위험 / contingency

### 7.1 Material3 vs MaterialComponents 핸드오프 오기 (해결됨)

핸드오프 §1 가 `Theme.MaterialComponents.DayNight.NoActionBar` 를 명시했으나 실 themes.xml 은 `Theme.Material3.DayNight.NoActionBar`. parent chain 자체 다름 (`Theme.Material3.* → Theme.MaterialComponents.Light.* → Theme.AppCompat.*`). 본 spec 은 actual themes.xml 에 정합.

### 7.2 layoutlib 의 Bridge 가 던지는 ResourceReference namespace 일관성

가설: Bridge 가 `getStyle(ref)` 에 항상 ns-exact ref 를 던질지, 일부 케이스에 RES_AUTO 던질지 불확실. **mitigation**: `LayoutlibRenderResources.getStyle` override 가 `bundle.getStyleExact(ref) ?: bundle.getStyleByName(ref.name)` 2 단계. ns-exact 정확 ref 면 첫 단계 hit, plain-text values.xml 의 parent chain 이 wrong-ns ref 로 쳐도 ns-agnostic fallback 이 chain 보존.

empirical 검증: `MaterialFidelityIntegrationTest` 의 unit-level walker 가 layoutlib 없이 `Theme.AxpFixture` 부터 `Theme` 까지 직접 walk → 모든 단계 매칭 검증.

### 7.3 41 AAR 의 values.xml 보유 비율

추정: ~30 개가 보유 (resource-bearing AAR). ~11 개는 code-only (e.g., `concurrent-futures`, `versionedparcelable`). (γ) lenient skip 으로 robust.

empirical: `AarResourceWalkerTest` 의 진단 출력으로 실 카운트 보고.

### 7.4 LM-α-C 재발 가능성

α 의 contingency keyword 매칭이 W4+ 다른 fixture 에서 새 keyword (e.g., `MDC` family) 로 또 surface 가능. 본 W3D4 가 root cause (theme resolution 정확화) 해결 → α contingency 는 deprecated → `tier3_basic_primary` 로 강제 PASS.

### 7.5 LM-W3D3-A 회피 (페어 convergent → empirical 누락 위험)

페어 의견만 신뢰하지 않고 모든 empirical-verifiable claim 직접 측정 (5.4 의 진단 출력 + 7.3 카운트). LM-W3D3-A / LM-α-A 에서 학습한 규율.

---

## 8. 결정 기록 (Q1-Q4)

| Q | 결정 | 근거 |
|---|---|---|
| Q1 (bundle 아키텍처) | **A** namespace-aware 단일 | cross-ns parent walk 보존, W3D1 자료구조 흡수 |
| Q2 (themeName 도달) | **2** RendererArgs 5-tuple cache key | 동일 process multi-theme 충돌 방지, free 한 줄 |
| Q3a (γ) | values.xml 부재 lenient + 로깅 | strict false-fail 회피, 디버깅 신호 |
| Q3b (δ) | AndroidManifest.xml package 추출 | AAR 자체 정보, cache layout 가정 X |
| Q3c (η) | per-namespace dedupe (W3D1 정책) | layoutlib RenderResources API 정합 |
| Q4a (ξ) | helper 제거 + 2 테스트 분리 | dead code 제거 + minimal carry 보존 |
| Q4b (o) | activity_basic_minimal.xml 영구 | cost-near-zero, future regression detector |
| Q4c (ρ) | resolved=unresolved (base 위임) | layoutlib base resolveValue 신뢰, redundant 회피 |

---

## 9. 페어 리뷰 질문 (Codex 라운드 1 entry point)

### Q1 — bundle 자료구조의 namespace 대칭성

`byNs: Map<ResourceNamespace, NsBucket>` 의 key 동등성. `ResourceNamespace.RES_AUTO` 와 `ResourceNamespace.fromPackageName("com.fixture")` 가 distinct? layoutlib API 의 sample-app res 는 RES_AUTO 가 정합인가, packaged ns 가 정합인가?

(Codex 의견 요청: 사용자 설계 결정에 따라 우리는 sample-app 의 res 를 RES_AUTO 로, AAR 의 res 를 named-package ns 로 분리 저장 가정. 정합?)

### Q2 — Theme.AxpFixture 의 namespace 정확성

`themes.xml` 의 `<style name="Theme.AxpFixture">` 은 sample-app 의 res. R class 는 com.fixture.* 로 컴파일되지만 layoutlib 의 RenderResources 입장에서 RES_AUTO 가 정합인가?

(Codex 의견 요청: 우리는 RES_AUTO 가정. layoutlib base 가 ns-agnostic name-walk 하므로 정확한 ns 매칭은 부수. 본 가정에 위반 사례 알면 지적.)

### Q3 — 41 AAR 의 walker 비용 (cold-start)

41 AAR ZipFile open + manifest regex + values.xml stream 파싱이 첫 render 의 cold-start 비용. JVM-wide cache 로 1회만 발생하지만 실 측정 필요. 추정 ~2-5 초 (W3D1 framework 10 XML ~200ms 기준 비율). 페어 의견: 추정 합당? 더 빠른 path 있나?

### Q4 — getResolvedResource = getUnresolvedResource 의 layoutlib base 위임 가정

W3D1 가 framework-only 환경에서 검증된 패턴. multi-namespace 에서도 base resolveValue 가 정확 chain walk? (특히 `?attr/colorPrimary` 가 Material3 theme item → AppCompat attr 까지 chain)

(Codex 의견 요청: layoutlib RenderResources base 의 resolveValue 동작이 multi-ns 환경에서 chain 끊는 known issue 있나? 있다면 ρ 가 σ 로 전환되어야.)

---

## 10. acceptance gate

W3D4 종결 판정:

1. ✅ `tier3_basic_primary` PASS — `assertEquals("activity_basic.xml", ...)` 강제 + SUCCESS + PNG > 1000.
2. ✅ `tier3_basic_minimal_smoke` PASS — minimal carry 보존.
3. ✅ unit ~190+ PASS, 모든 신규 8 test 파일 PASS.
4. ✅ integration 12 + 1 (분리 추가) = 13 PASS, 1 SKIP (tier3-glyph).
5. ✅ BUILD SUCCESSFUL, smoke "ok".
6. ✅ `docs/work_log/2026-04-29_w3d4-material-fidelity/` 생성 + handoff (carry 0 또는 다음 milestone).
7. ✅ `docs/plan/08-integration-reconciliation.md` §7.7.6 에 close 1줄.
8. ✅ MEMORY.md 갱신 (필요 시).
