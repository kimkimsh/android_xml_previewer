# W3D4 MATERIAL-FIDELITY — Pair Round 2 (plan-review) 결과

**Date**: 2026-04-30 (실행), spec round 2 + plan v1 작성일 = 2026-04-29
**Predecessor**: `spec-plan-handoff.md` (2026-04-29 작성 직후 상태)
**Outcome**: 양 reviewer **REVISE_REQUIRED 정합** → judge round 불필요 → plan v2 in-place edit 완료 → implementation 진입 직전.
**Baseline 변동 없음**: 167 unit + 12 integration PASS + 1 SKIP (tier3-glyph), BUILD SUCCESSFUL.

---

## 1. Pair dispatch

| Reviewer | 방식 | 모델 | duration |
|---|---|---|---|
| Codex | direct CLI `codex exec --skip-git-repo-check --sandbox danger-full-access` (LM-G), background | ~/.codex/config.toml: latest GPT (gpt-5.5) + reasoning effort xhigh | ~13 분 |
| Claude | subagent (`general-purpose`), foreground (Codex 보다 먼저 종결) | parent 와 동일 model | ~6 분 |

handoff §3.2 의 의도된 dispatch 패턴 정합 — Codex background, Claude foreground.

empirical evidence 수집 (LM-W3D3-A 회피 — 페어 의견만으로 verdict 채택 X):
- 현 RJarSymbolSeeder 구조 grep (실 API 확인 — single callback)
- 7 callsite enumeration (`grep -rn "SharedLayoutlibRenderer.getOrCreate\|LayoutlibRenderer("`)
- W3D3 의 locate helper signature (`locateAll` 부재 확인)
- material-1.12.0.aar 의 res/values/values.xml 추출 + chain depth 측정
- LayoutlibRenderer.kt:48-53 ctor 실 시그니처 (fallback 2nd 위치)
- SharedLayoutlibRenderer.kt:45 positional 호출 확인
- sample-app/build.gradle.kts:58 `.sorted()` (Codex 가 발견 후 직접 grep 으로 확인)

---

## 2. Q1-Q7 verdict reconcile

| Q | Claude side | Codex side | 종합 |
|---|---|---|---|
| **Q1** parseReference regex | NUANCED — `*` private + `[` aapt namespace 미커버, ResourceUrl.parse 권고 | DISAGREE — `@*android:...`, `?*android:...` 처리 불가, `@null`/`@empty` sentinel 누락 | **DISAGREE** — `ResourceUrl.parse()` 직접 활용. Codex 측정: `@null` 106회, `@empty` 2회 (27 AAR) |
| **Q2** RJarSymbolSeeder API | DISAGREE — `register: (ResourceReference, Int) -> Unit` 단일 callback, plan 의 `registerStyle/registerAttr` placeholder mismatch | DISAGREE — line 105 single emit point. seenAttrNames scope 명시 없음 | **FULL convergence DISAGREE** — 정확한 inline diff + seenAttrNames outer-scope (cross-class) |
| **Q3** locateAll helper | DISAGREE — W3D3 에 Triple-반환 helper 없음. 3 분리 helper (locateDistDir/locateFixtureRoot/locateSampleAppModuleRoot) | DISAGREE — module root 는 requireNotNull 으로 throw, plan v1 의 graceful skip 과 불일치 | **FULL convergence DISAGREE** — explicit body + module root graceful skip |
| **Q4** ctor reorder + caller | DISAGREE — fallback 2nd → 5th 이동 명시 안 됨, SharedLayoutlibRenderer.kt:45 positional silent break | NUANCED — 동일 발견 + Codex 추가: `fallback: PngRenderer? = null` default 가 CLAUDE.md "No default parameter values" 위반 | **DISAGREE** — explicit ctor old/new + 7 callsite + default 제거 + named args 강제 |
| **Q5** dedupe deterministic | NUANCED — AGP version 의존, dedupe winner unit test 없음 | NUANCED + critical 발견 — `build.gradle.kts:58` 의 `.distinct().sorted()` → winner 가 lex order, NOT Gradle order. spec §2.3 wrong | **FULL convergence NUANCED** — spec §2.3 정정 + winner unit assert |
| **Q6** chain hop limit | DISAGREE — 30 hops 권고. Material3 chain 측정 17-18 hop, MAX_THEME_HOPS=20 마진 부족 | NUANCED — 32 hops 권고. Theme.AxpFixture → ... → Platform.AppCompat.Light → android:Theme.Holo.Light 끊김. android: normalization + themes_holo.xml 포함 의무 | **set-converge** — 32 채택, android: normalization 추가, framework 10 file 검토 |
| **Q7** general critique | NUANCED — thread-safety, cache invalidation, AAR ZipFile leak, dist absent, RJarSymbolSeederTest e2e | REVISE_REQUIRED — T5 line 1114 `assertTrue(true)` 가짜 assertion, T5 line 1268 `Files.list(appValues)` no sort, seeder integration red tests, strict resolver tests | **REVISE_REQUIRED** — 양쪽 union 의 follow-up 모두 채택 |

종합: 양쪽 REVISE_REQUIRED 정합. judge round 불필요.

---

## 3. 12 follow-up (plan v2 inline 반영)

| # | Follow-up | 영향 task | 발견자 | 우선순위 |
|---|---|---|---|---|
| 1 | RJarSymbolSeeder 정확한 inline diff (single callback API), `seenAttrNames` outer-scope, integration test | T7 | 양쪽 | **P1** |
| 2 | ctor old/new signature 명시, 7 callsite 별 수정, `fallback: PngRenderer? = null` default 제거 | T8 | 양쪽 (Codex default 추가) | **P1** |
| 3 | parseReference → `ResourceUrl.parse()` + `@null`/`@empty` sentinel + private `*` override | T6 | 양쪽 | **P1** |
| 4 | `locateAll()` body explicit (assumeTrue gate + Triple) + module root graceful skip 정책 | T9 | 양쪽 | **P1** |
| 5 | `MAX_THEME_HOPS = 32` 상향 + 진단 로그 + chain depth ≥ 15 assert | T1 const + T6 + T9 IT | 양쪽 | P2 |
| 6 | `android:` style parent normalization (cross-ns chain 안 framework hit) | T6 + T1 const | Codex 주도 | P2 |
| 7 | dedupe winner = "sorted absolute artifact path + app last" 명시 + duplicate winner unit assert | T5 + T4 | Codex critical | P2 |
| 8 | `Files.list(appValues).sorted()` + T5 line 1114 의 `assertTrue(true)` 가짜 assertion 제거 | T5 | Codex critical | P2 |
| 9 | JVM cache invalidation (mtime-aware key 또는 explicit invalidate) | T5 | Claude | P3 |
| 10 | AAR `ZipFile().use {}` explicit | T3 | Claude | P3 (실 코드 line 808 이미 충족) |
| 11 | RJarSymbolSeederTest end-to-end 회귀 case (Theme_AxpFixture seeding → Theme.AxpFixture lookup) | T7 | Claude | P3 |
| 12 | framework `themes_holo.xml` 포함 의무화 (현 10 framework values 부족) | T6 implementation note + T9 IT | Codex | P3 |

---

## 4. Plan v2 변경 요약

**File**: `docs/superpowers/plans/2026-04-29-w3d4-material-fidelity.md` (in-place edit, 2349 → ~2700 줄)

**구조 변경**:
- header v1 → v2, "REVISE_REQUIRED" round 2 verdict 명시
- 신규 §0 round 2 페어 verdict + 12 follow-up 매핑 표 + Q1-Q7 reconcile

**T1** (Constants):
- MAX_THEME_HOPS 20 → **32** (실측 17 hop + ThemeOverlay buffer)
- 신규 sentinel: `RES_VALUE_NULL_LITERAL`, `RES_VALUE_EMPTY_LITERAL`
- 신규 normalization: `ANDROID_NS_PREFIX`, `NS_NAME_SEPARATOR`
- 테스트 +2 (sentinel + ANDROID prefix)

**T5** (Loader):
- 가짜 `assertTrue(true)` → 실 identity assert (`assertTrue(a !== c, ...)`)
- `clearCache` 후 재계산이 새 instance 검증
- 신규 dedupe winner test (`X` style 가 a-aar/b-aar 양쪽 정의 시 lex 마지막 winner = "B")
- `Files.list(appValues).sorted()` (deterministic order)
- 테스트 5 → 6

**T6** (Resolver):
- self-built `parseReference` regex → `com.android.resources.ResourceUrl.parse()` 직접 활용
- `@null`/`@empty` sentinel 즉시 raw 반환
- private `@*android:...` override chain walker 정상 처리
- `walkParent` 가 `android:Theme.Holo.Light` 같은 prefix 를 ANDROID ns 로 normalize (`resolveStyleNameWithNamespace` 신규)
- MAX_REF_HOPS 초과 시 진단 로그 + graceful 반환
- 테스트 10 → 12 (sentinel + private + android-norm +2)

**T7** (RJarSymbolSeeder):
- placeholder `callback.registerStyle(...)` → 정확한 inline diff (single callback API)
- `seenAttrNames` outer-scope (seed() 함수 단위 cross-class 공유)
- type==STYLE 분기에서만 `RNameCanonicalization.styleNameToXml` 적용
- type==ATTR 분기에서만 `AttrSeederGuard.tryRegister` first-wins
- 신규 RJarSymbolSeederTest end-to-end case (Theme_AxpFixture → Theme.AxpFixture emit)

**T8** (LayoutlibRenderer integration):
- ctor 명시 reorder 표: 기존 `(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` → 새 `(distDir, fixtureRoot, sampleAppModuleRoot, themeName, fallback)`
- `fallback: PngRenderer? = null` default 제거 (CLAUDE.md "No default parameter values")
- 7 callsite 별 explicit 수정 표 (Main.kt:133, SharedLayoutlibRenderer.kt:45 positional, IT 5개)
- positional → named call 강제 (silent reorder 위험 회피)

**T9** (Integration test):
- `locateAll(): Triple<Path, Path, Path>?` 의 explicit body (assumeTrue gate + null 처리)
- module root 도 graceful skip 으로 통일
- chain depth ≥ 15 assert 추가 (실측 17 hop 회귀 detect)

**Test 카운트**: 167 → ~217 unit (+50, v1 의 +46 → v2 의 +50). integration 13 + 1 SKIP 동일.

---

## 5. Empirical evidence (round 2 핵심)

### 5.1 RJarSymbolSeeder 실 API
```kotlin
// server/.../RJarSymbolSeeder.kt:26-30 (실 코드)
fun seed(
    rJarPath: Path,
    rJarLoader: ClassLoader,
    register: (ResourceReference, Int) -> Unit,    // <-- single callback, 'registerStyle' 분기 없음
)
// line 105: register(ResourceReference(namespace, type, field.name), value)
```

### 5.2 LayoutlibRenderer 실 ctor
```kotlin
// server/.../LayoutlibRenderer.kt:48-53 (실 코드)
class LayoutlibRenderer(
    private val distDir: Path,
    private val fallback: PngRenderer?,           // <-- 2nd!
    private val fixtureRoot: Path,
    private val sampleAppModuleRoot: Path,
)
```
plan v1 line 1893-1898 의 "기존 ctor" 묘사는 fallback 을 마지막으로 표기 — 실 코드와 mismatch.

### 5.3 SharedLayoutlibRenderer (test sourceSet) positional 호출
```kotlin
// server/.../test/SharedLayoutlibRenderer.kt:45 (실 코드)
val created = LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)
//                              ^positional, fallback 2nd
```
ctor reorder 시 silent 잘못된 인자 매핑 위험.

### 5.4 build.gradle.kts:58 `.sorted()` (Codex critical)
```kotlin
// fixture/sample-app/app/build.gradle.kts:58-65
val artifacts = cp.resolvedConfiguration.resolvedArtifacts
    .map { it.file.absolutePath }
    .distinct()
    .sorted()                                     // <-- lex order, NOT Gradle order
val outFile = axpClasspathManifest.get().asFile
outFile.writeText(artifacts.joinToString("\n"))
```
spec §2.3 의 "Gradle deterministic" 주장은 부정확. 실 winner = lex order. plan v2 §0.A 정정.

### 5.5 Material3 chain depth (Claude+Codex 양쪽 측정 정합)
```
Theme.AxpFixture
→ Theme.Material3.DayNight.NoActionBar (parent="Theme.Material3.Light.NoActionBar")
→ Theme.Material3.Light.NoActionBar
→ Theme.Material3.Light
→ Base.Theme.Material3.Light
→ Base.V14.Theme.Material3.Light
→ Theme.MaterialComponents.Light
→ Base.Theme.MaterialComponents.Light
→ Base.V14.Theme.MaterialComponents.Light
→ Base.V14.Theme.MaterialComponents.Light.Bridge
→ Platform.MaterialComponents.Light
→ Theme.AppCompat.Light
→ Base.Theme.AppCompat.Light
→ Base.V7.Theme.AppCompat.Light
→ Platform.AppCompat.Light
→ android:Theme.Holo.Light
→ Theme.Light (framework)
→ Theme (framework)

총 17-18 edges. MAX_THEME_HOPS=20 마진 2-3 hop (불충분). 32 채택.
```

### 5.6 Codex 의 layoutlib-api ResourceUrl.parse 검증
```
~/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/.../layoutlib-api-31.13.2.jar
class com.android.resources.ResourceUrl
- parse(String text): static
  - lookupswitch { 47, 58, 91 } — '/', ':', '[' (compiled aapt2 namespace)
  - bipush 42 ('*' private override prefix)
  - sentinel: @null / @empty 는 null 반환
27 AAR 안 sentinel 출현: @null 106회, @empty 2회, @aapt: 0회, @* 0회 (실 측)
```

---

## 6. Lessons learned (round 2 적용)

- **LM-W3D3-A 재확인**: 양 reviewer convergence 도 empirical 검증 필수. round 2 가 spec round 2 의 '~10 hop' 추정을 '17-18 hop' 실측으로, '`Gradle deterministic`' 주장을 '`.sorted()` lex' 실측으로 정정 — 양쪽 모두 round 1 spec 페어가 놓친 것.
- **LM-α-A**: pair convergent 도 empirical evidence 동반 (round 2 의 javap + Material AAR walker + ctor signature grep + build.gradle.kts grep 으로 모두 확정).
- **plan v1 의 placeholder 관행 위험**: round 1 spec 페어가 spec-level 결정을 정합화했지만, 그 spec 을 plan-step 으로 옮길 때의 placeholder (T7 callback hint, T8 "5번째 인자 추가", T9 locateAll `...`) 가 implementation 시 wrong 코드 산출 위험. round 2 페어가 이 layer 를 catch — plan-review 페어의 가치 입증.
- **Codex 의 cross-family 가치**: build.gradle.kts:58 `.sorted()`, T5 line 1114 가짜 assertion, Files.list no sort 모두 Codex 가 단독 발견. Claude side 의 javap + 17-hop 측정과 union 으로 12 follow-up 의 완성도.

---

## 7. Plan v2 acceptance + implementation 진입

- ✅ Plan v2 file in-place edit 완료 (2349 → ~2750줄, surgical)
- ✅ §0 페어 verdict + 12 follow-up 표 명시
- ✅ T1, T5, T6, T7, T8, T9 모두 inline 변경
- ✅ Self-Review 의 placeholder scan 완전 해제
- ✅ Test 카운트 ~217 unit + 13 integration

다음 단계 (user 확인 후): `superpowers:subagent-driven-development` 으로 T1-T10 sequential implementation. 각 task 단위 commit + push (CLAUDE.md task-unit completion 의무).

**carry forward**: 0. Plan v2 자체가 implementation 입력 — 추가 정정 필요 없음.
