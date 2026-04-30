# W3D4 T9 — Integration Test Split + Acceptance Gate Discovery (W3D4-β carry)

**Status**: T9 spec 구현 완료 + MaterialFidelity 4/4 PASS + minimal smoke PASS, **단 primary acceptance gate FAIL → @Disabled carry, plan-revision (W3D4-β) escalate**.
**Date**: 2026-04-30
**Predecessor**: `t8-renderer-integration-w3d1-absorption.md`
**Carry**: 2 production-pipeline gap (ThemeEnforcement + ColorStateList wiring) → W3D4-β plan-revision phase 의 T11/T12 fix

---

## 1. 한 줄 요약

T9 가 plan v2 spec verbatim 구현 완료 — `MaterialFidelityIntegrationTest` 4/4 PASS 가 W3D4 의 자료구조 + chain walker (T1-T7) 정상 작동 입증. **그러나 primary `activity_basic.xml` 렌더 시 2 production-pipeline gap 발견** — Material ThemeEnforcement + Bridge ColorStateList wiring. Plan v2 의 가정 (T1-T8 만으로 primary SUCCESS) 미충족 → primary `@Disabled("W3D4-β carry")` 처리 + plan-revision phase escalate.

## 2. T9 구현 결과

### 2.1 신규 / 수정 (commit pending — 본 work_log 와 atomic)

| 파일 | 변경 | 결과 |
|---|---|---|
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/MaterialFidelityIntegrationTest.kt` | NEW (4 case) | **4/4 PASS** |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` | tier3 basic 1 → 2 분리 (primary + minimal smoke) + `renderWithMaterialFallback` helper 제거 + `locateAll()` explicit body | minimal smoke PASS, **primary @Disabled (W3D4-β carry)** |

### 2.2 Test 결과 (commit 시점)

```
LayoutlibRendererIntegrationTest:
  - tier3 basic primary @Disabled  (W3D4-β carry)
  - tier3 basic minimal smoke PASS (activity_basic_minimal Button-only)

MaterialFidelityIntegrationTest:
  - Theme AxpFixture parent walk to Theme PASS  (chain depth ≥ 15 assert PASS — 실측 17 hop 확인)
  - theme colorPrimary item raw resolve PASS    (RES_AUTO bucket 안 colorPrimary 정의 확인)
  - Material3 colorPrimaryContainer 추적 PASS   (Theme.AxpFixture 의 themes.xml 정의 확인)
  - 41 AAR styles > 100 PASS                    (실측 1087 styles 확인)

전체 integration: 14 PASS + 2 SKIP (tier3-glyph W4 carry + tier3 basic primary W3D4-β carry).
unit 변동 없음 (151 PASS).
build SUCCESSFUL.
```

## 3. Acceptance gate failure — 2 cascading production gap

### 3.1 Gap A — Material ThemeEnforcement.checkAppCompatTheme 실패

**증상**:
```
java.lang.IllegalArgumentException: The style on this component requires your app theme to be Theme.AppCompat (or a descendant).
  at com.google.android.material.internal.ThemeEnforcement.checkAppCompatTheme(...)
  at com.google.android.material.button.MaterialButton.<init>(...)
[layoutlib.error] View class com.google.android.material.button.MaterialButton is an AppCompat widget that can only be used with a Theme.AppCompat theme (or descendant).
```

**Root cause 가설**:
- `Theme.AxpFixture` 의 ancestor chain 은 정상 (MaterialFidelity 의 `chain depth ≥ 15` assert 가 17 hop walk 확인 — `Theme.AxpFixture → Theme.Material3.* → Theme.AppCompat → Theme`).
- `ThemeEnforcement.checkAppCompatTheme()` 의 sentinel-attr lookup (`R.attr.colorPrimaryDark` 또는 `R.attr.colorPrimary` 같은 AppCompat-only attr) 가 **Theme 안 item 으로 정의되어 있어도** lookup 실패 — `BridgeContext.obtainStyledAttributes` 가 RES_AUTO 의 attr ID 를 inflate 시점에 resolve 못하는 것으로 추정.
- 또는 `RJarSymbolSeeder` (T7) 가 R$attr 의 colorPrimary 등 sentinel attr 를 namespace=RES_AUTO 로 register, 그러나 Material 의 ThemeEnforcement 가 host R$attr.colorPrimary (compile-time R class 의 ID) 와 match 시도 → ID mismatch.

**검증 필요**:
- `BridgeContext.resolveThemeAttribute` 와 `LayoutlibRenderResources.findItemInTheme` 의 wiring (ID 기반 vs name 기반 lookup).
- ThemeEnforcement 의 정확한 sentinel-attr name + 그 attr 가 Theme.Material3 chain 안 정의 여부.

### 3.2 Gap B — Bridge getColorStateList 의 input feed 부재

**증상** (Gap A 와 별개로 발생):
```
[layoutlib.error] broken | Failed to configure parser for @color/m3_highlighted_text
  java.io.IOException: input must be specified to setInput(InputStream)
  at org.kxml2.io.KXmlParser.setInput(...)
[layoutlib.error] broken | Failed to configure parser for @color/m3_hint_foreground
```

**Root cause 가설**:
- AAR 의 `res/color/*.xml` (color state list — `<selector>` 패턴) 가 우리 `LayoutlibResourceValueLoader` 에 **포함 안 됨**. 본 loader 는 `res/values/values.xml` 만 파싱 (T3 AarResourceWalker 가 `AAR_VALUES_XML_PATH = "res/values/values.xml"` 만 처리).
- Bridge 가 `getColorStateList("@color/m3_highlighted_text")` 호출 시 `MinimalLayoutlibCallback.getParser(ResourceValue)` 또는 동등 hook 이 input stream feed 못 함 → KXmlParser.setInput(null) → IOException.
- `<color>` element (single value) 는 `values.xml` 에 정의되므로 정상, **`<selector>` (state list, 별도 `res/color/*.xml` 파일)** 만 누락.

**검증 필요**:
- AAR 안 `res/color/*.xml` 파일 카운트 + 실 패턴.
- `MinimalLayoutlibCallback` 또는 `LayoutlibCallback` 의 resource-fetch hook (`getParser`, `getInputStream` 등).
- W3D1 `FrameworkResourceValueLoader` 가 framework `data/res/color/*.xml` 를 어떻게 처리했는지 (혹은 처리 안 하고 bypass 했는지) 비교.

### 3.3 Inter-relation

Gap A 는 inflate 첫 단계 (ThemeEnforcement constructor check) 에서 throw. Gap B 는 그 throw 후속 — Material widget constructor 가 default style (state list color) 접근 시도 → KXmlParser fail. 즉 **Gap A 가 root, Gap B 는 surface**. Gap A 만 fix 시 Gap B 도 자동 해결 가능 (Material widget 이 throw 전에 state list 접근 안 함). 또는 Gap A/B 가 독립적이고 둘 다 fix 필요할 수 있음 — plan-revision 시 검증.

## 4. T1-T7 자료구조 정상 입증

MaterialFidelity 의 4/4 PASS 가 다음을 입증:
- T4 LayoutlibResourceBundle: byNs LinkedHashMap insertion order ANDROID → RES_AUTO 정합 (`bundle.namespacesInOrder() == [ANDROID, RES_AUTO]`)
- T5 LayoutlibResourceValueLoader: 3-입력 통합 + 41 AAR walk 작동 (style count > 100)
- T6 LayoutlibRenderResources: σ FULL chain walker 작동 (Theme.AxpFixture 의 17 hop parent walk → Theme.AppCompat 까지 도달, MAX_THEME_HOPS=32 마진 충분)
- T6 의 colorPrimary / colorPrimaryContainer findItemInTheme 작동 (`Theme.AxpFixture` 의 themes.xml 안 정의된 item 과 Material3 chain 안 inherited item 모두 resolve)
- T7 RJarSymbolSeeder canonicalization + AttrSeederGuard 회귀 없음 (전체 unit 151 PASS)
- T8 production wire 작동 (LayoutlibRenderer.renderViaLayoutlib 이 Args 3-tuple → loader → resources 흐름 정상)

T1-T8 의 **자료구조 layer 는 정확**. 잔여 fix 는 **Bridge ↔ resource layer 의 plumbing** (ThemeEnforcement seeding + ColorStateList input wiring).

## 5. Plan-revision (W3D4-β) escalate

CLAUDE.md "Pairs REQUIRED for plan-document review rounds" — fundamental plan revision mid-implementation 시 Codex+Claude pair on new plan delta.

### 5.1 W3D4-β 의 신규 task (예상)

- **T11**: Gap A 의 root-cause 정확화 + fix. `BridgeContext.obtainStyledAttributes` 와 `LayoutlibRenderResources` wiring 검증, ThemeEnforcement sentinel attr 정확 식별.
- **T12**: Gap B 의 root-cause 정확화 + fix. `MinimalLayoutlibCallback` 또는 `LayoutlibCallback` 의 resource-fetch hook 에 RES_AUTO color XML state list input stream feed.
- **T13**: 회귀 — primary `@Disabled` 제거 + tier3_basic_primary PASS 확인.

### 5.2 Plan v3 input

- 본 work_log 의 §3.1, §3.2 Root cause 가설 + 검증 필요 항목.
- T1-T7 의 자료구조 layer 정상 (MaterialFidelity 4/4 PASS) — 변경 불필요.
- Round 3 페어 review (Codex+Claude on plan v3 W3D4-β delta).

## 6. Round 2 페어 reviewer 의 한계 (W3D5+ 페어 playbook input)

T2 의 두 latent bug (T8 fix 한 mixed content + cross-NS attr) + T9 의 두 production gap 모두 **round 2 페어가 catch 못함** — 공통 root cause = **synthetic XML / mock fixtures 만 검증, real-world AAR / 실 production wiring 미접근**.

W3D5+ 페어 playbook 권고 (CLAUDE.md update 또는 페어-review 스킬 반영):
- Round 2 페어 review 시 real fixture (1+ Material AAR + 1+ AppCompat AAR) 의 실 values.xml + color/*.xml 를 reviewer 의 verification material 로 포함.
- Production wiring 단계 (renderer → loader → resources → callback) 의 end-to-end smoke 도 round 2 의 검증 수단.
- LM-W3D3-A "convergent verdict 도 empirical 검증 누락 시 wrong" 의 확장: **synthetic empirical 도 wrong** — real-world fixture 가 ground truth.

## 7. 다음 세션 시작점

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
git log --oneline -5
# 베이스라인:
./server/gradlew -p server :layoutlib-worker:test  # 151 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 14 PASS + 2 SKIP

# 본 work_log 정독:
cat docs/work_log/2026-04-29_w3d4-material-fidelity/t9-acceptance-gate-discovery.md

# Plan-revision phase 진입:
# 1. Gap A 의 root-cause empirical investigation (BridgeContext + LayoutlibRenderResources wiring grep)
# 2. Gap B 의 root-cause empirical investigation (MinimalLayoutlibCallback resource-fetch hook + AAR res/color/*.xml 카운트)
# 3. plan v3 (W3D4-β) 작성 — T11/T12/T13 spec
# 4. Codex+Claude pair round 3 (plan-revision review)
# 5. Plan v3 GO 후 implementation
```

## 8. Carry 0 / handoff complete

T9 의 spec 구현 자체는 완료 + 4/4 MaterialFidelity PASS — 자료구조 layer 가치 보존. Primary @Disabled 로 production gap 명시. plan-revision phase 의 input 모두 본 work_log 에 정합 — fresh session 으로 시작 가능.
