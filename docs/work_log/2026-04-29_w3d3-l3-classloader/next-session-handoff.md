# W3D4 Next-Session Handoff — MATERIAL-FIDELITY

**Carry**: W3D3-α 의 Branch (B) contingency 를 primary 로 환원.
**Date**: 2026-04-29 (W3D3-α 종결 직후 작성).
**Suggested next-session date**: TBD (사용자 결정).
**Predecessor close**: `docs/work_log/2026-04-29_w3d3-l3-classloader/alpha-session-log.md` §6, `docs/plan/08-integration-reconciliation.md §7.7.5`.

---

## 1. 한 줄 요약

W3D3-α 의 통합 테스트가 `activity_basic.xml` (MaterialButton 포함) 1차 시도에서 `Theme.AppCompat` enforcement throw 로 fail → contingency `activity_basic_minimal.xml` (Button 으로 교체) 으로 PASS. **본 W3D4 는 sample-app 의 `Theme.AxpFixture` (Material3.DayNight) 와 transitive AAR 의 `res/values/values.xml` 을 RenderResources 에 inflate 시점 주입함으로써 primary `activity_basic.xml` 가 contingency 없이 PASS 하도록 만든다.**

## 2. 현재 상태 (origin/main, commit `2f3589c`)

### 2.1 테스트
- **167 unit PASS** (W3D3 base 142 + α 신규 25).
- **12 integration PASS + 1 SKIPPED** (`tier3-glyph` 만; `LayoutlibRendererIntegrationTest` 가 fallback 으로 PASS).
- BUILD SUCCESSFUL, smoke "ok".

### 2.2 작동 중인 인프라 (재활용)
- `dev.axp.layoutlib.worker.classloader.*` 패키지 (8 main + 8 test = 16 파일):
  - `ClassLoaderConstants` — paths/extensions/digest 상수.
  - `SampleAppClasspathManifest` — runtime-classpath.txt 파서.
  - `AarExtractor` — AAR 의 classes.jar 를 stream-rewrite (ASM ClassRemapper) 하여 stable cache 에 저장.
  - `SampleAppClassLoader` — URLClassLoader(parent=isolatedCL) builder.
  - `RewriteRules` — NAME_MAP 4 entries (Build family 만).
  - `AndroidClassRewriter` — single-class ASM rewriter.
  - `RJarTypeMapping` — R$type → ResourceType 매핑.
  - `RJarSymbolSeeder` — R.jar 의 R$<type> 클래스 walker (R$styleable 전체 skip).
- `MinimalLayoutlibCallback` — `viewClassLoaderProvider` lazy lambda + `initializer` (init 시점 R.jar seed). FIRST_ID 0x7F80_0000.
- `LayoutlibRenderer` — `sampleAppModuleRoot` 4번째 인자, `ensureSampleAppClassLoader()` lazy provider, `seedRJarSymbols()` named method.
- `FixtureDiscovery.locateModuleRoot()` — sample-app 모듈 루트 탐지.
- `RendererArgs` data class — SharedLayoutlibRenderer 의 3-path cache key.

### 2.3 W3D1 의 framework resource loader (직접적 prior art)
- `dev.axp.layoutlib.worker.resources.*` (7 main + 5 test = 12 파일, ~950 LoC):
  - `FrameworkResourceValueLoader` — `data/res/values/` 의 10 XML 파싱 → `FrameworkResourceBundle`.
  - `FrameworkValueParser` — `<dimen>`/`<integer>`/`<bool>`/`<color>`/`<style>`/`<attr>`/`<declare-styleable>` 파서.
  - `FrameworkRenderResources` — bundle delegate 로 layoutlib 에 제공. **본 W3D4 의 직접 모델**.
  - `StyleParentInference` — style 부모 추론 (`Theme.X.Y` → `Theme.X`).
  - `ParsedEntry`, `ResourceLoaderConstants`.
- 본 인프라가 `data/res/values/values_material*.xml` 등 framework resource 를 `Theme.Material.Light.NoActionBar` 까지 정상 resolve.

## 3. 본 W3D4 의 deliverable

### 3.1 핵심 작업
W3D1 의 `FrameworkResourceValueLoader` 와 동일 패턴으로 **app/library resource value loader** 추가:
- 입력 1: sample-app 의 `app/src/main/res/values/themes.xml` + `colors.xml` + `strings.xml` 등.
- 입력 2: 각 transitive AAR 의 `res/values/values.xml` (e.g., `material-1.12.0.aar` 안의 values.xml — `Theme.MaterialComponents.*` 정의 포함).
- 출력: `AppLibraryResourceBundle` (W3D1 의 `FrameworkResourceBundle` 과 평행).
- 통합: `RenderResources` chain — framework + app + library bundles 를 callback/SessionParams 에 주입.
- 부수: `SessionConstants.DEFAULT_FRAMEWORK_THEME` 을 `Theme.AxpFixture` (or `Theme.MaterialComponents.DayNight.NoActionBar`) 로 override 가능.

### 3.2 acceptance gate (T2 — 기존 T1 의 강화)
`LayoutlibRendererIntegrationTest` 가 contingency 발동 없이 primary 로 PASS:
```kotlin
val (layoutName, bytes) = renderWithMaterialFallback(
    renderer,
    primary = "activity_basic.xml",  // ← contingency 가 발화하지 않아야 함
    fallback = "activity_basic_minimal.xml",
)
assertEquals("activity_basic.xml", layoutName, "primary 로 SUCCESS 해야 함")
assertEquals(SUCCESS, renderer.lastSessionResult?.status)
assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES)
```

테스트 자체는 기존 helper 그대로 — contingency 가 발화하지 않으면 primary 가 그대로 결과로 반환됨을 새 assertion 으로 추가.

### 3.3 out-of-scope (carry 보존)
- **tier3-glyph** (W4+).
- **POST-W2D6-POM-RESOLVE**.
- **CLI/MCP 통합 — sample-app 외 다른 fixture 지원**.

## 4. 페어 리뷰 요건 (CLAUDE.md 정책)

본 W3D4 는 architectural design tradeoff (resource value loader 의 저장 자료구조, namespace 매핑 정책, AAR vs sample-app vs framework resolution 우선순위) 를 다루므로 **Codex+Claude 1:1 페어 필수** (planning + plan-review phases).

지난 W3D3-α 의 Codex 페어가 stalled-final 패턴을 보임 (tool-trace 후 verdict 늦음). mitigation:
- spec/plan 작성 후 Codex 호출 명령에 `codex exec --skip-git-repo-check --sandbox danger-full-access` direct CLI 사용 (codex-rescue subagent bypass — LM-G).
- Codex output 이 stalled 되면 single-source verdict (Claude empirical) 로 진행하되 명시적 flagging.

## 5. 핵심 위험 / 알려진 함정

### 5.1 Resource resolution chain 의 우선순위
W3D1 framework only 일 때는 namespace=`android` 한 path 만 존재. W3D4 추가 후:
- `android` namespace → framework bundle.
- `<sampleAppPackage>` namespace (e.g., `com.fixture`) → app bundle.
- `<aarPackage>` namespace (e.g., `androidx.constraintlayout.widget`, `com.google.android.material`) → library bundle.

각 bundle 의 우선순위 (later-wins vs first-wins) 는 W3D1 의 framework loader 정책 (later-wins for `SimpleValue`/`StyleDef`, first-wins for `AttrDef`) 을 그대로 적용 권장. spec round 1 페어 리뷰가 검증.

### 5.2 Theme.AxpFixture 의 부모 chain
`themes.xml` 의 `<style name="Theme.AxpFixture" parent="Theme.MaterialComponents.DayNight.NoActionBar">`. 부모 chain: Theme.AxpFixture → Theme.MaterialComponents.DayNight.NoActionBar → Theme.MaterialComponents.Light.NoActionBar → Theme.AppCompat.Light.NoActionBar → Theme.AppCompat.Light → Theme.AppCompat → ... → Theme.

각 부모가 framework / appcompat / material AAR 분산. resolution 이 정확하려면 *모든 transitive AAR* 의 `res/values/values.xml` 가 파싱 + 결합되어야.

### 5.3 LM-α-C 재발 가능성
α 가 contingency 의 keyword 매칭에 `Theme.AppCompat` 추가했음을 W4+ 다른 fixture 에서 또 다른 keyword (e.g., `MDC` family) 가 surface 가능. 본 W3D4 가 root cause (theme resolution) 를 해결하므로 α 의 contingency 는 *deprecated* 가능. 단, contingency 는 `activity_basic_minimal.xml` 자체 보존 — `LayoutlibRendererTier3MinimalTest` 와 평행한 simpler-layout 테스트 자산.

### 5.4 sample-app 의 빌드 의존
sample-app `assembleDebug` 가 `app/build/intermediates/incremental/debug/mergeDebugResources/merged.dir/values/values.xml` 같은 merged resource 도 emit. 본 W3D4 가 그 merged 파일을 입력으로 쓸 수도 있으나, AAR 별로 개별 파싱하는 path 가 namespace 분리에 정직함 — spec round 1 페어가 결정.

## 6. 추천 spec → plan → implementation 흐름

### 6.1 Spec round 1 (페어 리뷰 entry point)
- 경로: `docs/superpowers/specs/2026-04-30-w3d4-material-fidelity-design.md` (or 적절한 날짜).
- 골격:
  1. 요구사항 (T2 gate 정의).
  2. 아키텍처 — bundle 계층 + resolution chain.
  3. 컴포넌트 분해 (~5-7 components).
  4. AAR `res/values/values.xml` 파싱 정책 (W3D1 framework loader 와 동일 + namespace 추가).
  5. `setTheme` 변경 — `SessionConstants.DEFAULT_FRAMEWORK_THEME` → `Theme.AxpFixture` 또는 사용자 fixture 의 themed Activity 선택.
  6. `RenderResources` 통합 — framework + app + library 의 chain.
  7. 위험 / contingency.
  8. 페어 리뷰 질문 Q1-Q3.

### 6.2 Plan v1
~6-8 tasks (W3D1 의 7 main + 5 test = 12 파일 규모 예상):
- T1: AppLibraryResourceConstants + 신규 도메인 상수.
- T2: AppLibraryValueParser (W3D1 FrameworkValueParser 와 namespace 차이만 있는 변형).
- T3: AppLibraryResourceBundle.
- T4: AppLibraryResourceValueLoader — sample-app res 파싱 + 모든 AAR `res/values/values.xml` walker.
- T5: AppLibraryRenderResources 또는 통합 ChainedRenderResources.
- T6: SessionConstants/SessionParamsFactory 변경 — themeName parameter 도입.
- T7: LayoutlibRenderer 통합 + integration test 의 primary assertion 강화.
- T8: work_log + handoff.

### 6.3 Implementation
Subagent-driven, Claude-only (CLAUDE.md). 각 task 는 TDD red-green + atomic commit.

## 7. Quick start commands (다음 세션 시작 직후)

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer

# 현 상태 검증
./server/gradlew -p server test                                          # 167 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 12 + 1 SKIP
./server/gradlew -p server build                                         # SUCCESSFUL

# Branch (B) 재현 — primary fail → fallback succeed (현재 상태)
./server/gradlew -p server :layoutlib-worker:test \
    --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest" \
    -PincludeTags=integration --info 2>&1 | grep -E "Theme.AppCompat|status="

# W3D1 prior art 직접 검토 (resource loader 패턴)
ls server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/

# AAR values.xml 구조 확인 (Material 의 themes 정의 위치)
unzip -l ~/.gradle/caches/modules-2/files-2.1/com.google.android.material/material/1.12.0/*/material-1.12.0.aar | grep values
unzip -p ~/.gradle/caches/modules-2/files-2.1/com.google.android.material/material/1.12.0/*/material-1.12.0.aar res/values/values.xml | head -100

# sample-app themes 확인
cat fixture/sample-app/app/src/main/res/values/themes.xml
```

## 8. 주의할 LM (W3D3-α 시리즈 누적)

- **LM-W3D3-A** / **LM-α-A** — 페어 convergent decision 도 empirical 검증 누락 시 잘못 가능 (Q1 afterEvaluate 사례 + α 의 NAME_MAP 25 사례). **해결**: empirical-verifiable claim 은 직접 실측.
- **LM-W3D3-B** — `:layoutlib-worker` test classpath 에 `kotlin.test` 없음. JUnit Jupiter Assertions 만.
- **LM-α-B** — Codex stalled-final. mitigation: Claude single-source verdict + flagging.
- **LM-α-D** — Kotlin backtick 함수명 안에 마침표 (.) 사용 금지.
- **LM-G** — codex exec 항상 `--skip-git-repo-check --sandbox danger-full-access` 직접 CLI (codex-rescue subagent bypass).

## 9. 참고할 핵심 문서 (다음 세션 1턴에 읽을 것)

1. **본 핸드오프** (이 파일).
2. `docs/work_log/2026-04-29_w3d3-l3-classloader/alpha-session-log.md` — α 종결 상세.
3. `docs/work_log/2026-04-29_w3d3-l3-classloader/handoff.md` — α 직전 상태.
4. `docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md` — W3D1 framework loader spec (직접 모델).
5. `docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md` — W3D1 plan.
6. `docs/plan/08-integration-reconciliation.md` §7.7.3 (W3D1 close) + §7.7.5 (W3D3-α close).
7. `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/*.kt` — 직접 prior-art 코드.
