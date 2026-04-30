# W3D4-β — Next-Session Handoff (Plan-Revision Phase)

**Status**: T1-T9 spec 구현 완료, T9 의 acceptance gate (primary `activity_basic.xml` SUCCESS) 가 2 production-pipeline gap 으로 fail → @Disabled carry. **다음 세션 = W3D4-β plan-revision phase**.
**Date**: 2026-04-30 (작성), suggested next-session date TBD
**Last commit**: `f2bc922` (T9 IT split + MaterialFidelity 4/4 PASS + primary @Disabled W3D4-β carry)
**Predecessor handoff**: `2026-04-29_w3d4-material-fidelity/spec-plan-handoff.md` (T1-T10 implementation 진입 직전)

---

## 1. 한 줄 요약

W3D4 plan v2 의 T1-T9 implementation 완료. **자료구조 + chain walker (T1-T8) 정상** (MaterialFidelity 4/4 PASS 입증). **단 production-pipeline 의 Bridge ↔ resource layer wiring 의 2 gap 이 acceptance gate 막음** → T9 primary `@Disabled("W3D4-β carry")` + plan-revision phase escalate. 다음 세션 = Codex+Claude pair round 3 (plan v3 W3D4-β delta) 부터 시작.

## 2. 베이스라인 (origin/main, commit `f2bc922`)

```
unit:        151 PASS / 0 fail (T1-T8 의 자료구조 + chain walker + RJarSymbolSeeder 모두 정상)
integration: 14 PASS + 2 SKIP (tier3-glyph W4 carry + tier3 basic primary W3D4-β carry)
build:       SUCCESSFUL
```

W3D4 implementation 의 commit history (`f2bc922` 부터 거슬러 올라가):

| Commit | Task | 한 줄 |
|---|---|---|
| `f2bc922` | T9 carry | IT split + MaterialFidelity 4/4 PASS, primary @Disabled W3D4-β |
| `1d5729a` | T8 | LayoutlibRenderer ctor reorder + W3D1 흡수 + 2 latent T2 fix (mixed content + cross-NS attr) |
| `a9b328c` | T7 | RJarSymbolSeeder canon + AttrSeederGuard first-wins |
| `b29f3b7` | T6 | LayoutlibRenderResources σ FULL 9 override + chain walker |
| `7633198` | T5 patch | RES_AUTO conditional add (post-impl 정정) |
| `b6dff1e` | T5 | LayoutlibResourceValueLoader 3-입력 + cache + dedupe winner |
| `ec2db0c` | T4 | LayoutlibResourceBundle byNs + dedupe 진단 |
| `1033c0d` | T3 | AarResourceWalker γ skip + δ + wall-clock |
| `e171ef0` + `627ff83` | T2 fix | skipTag + top-level item + multi-element depth |
| `a58be2a` | T2 | NamespaceAwareValueParser |
| `2b1e6ac` + `24f7653` | T1 | Constants + ParsedNsEntry + NsBucket |
| `4b005a8` | plan v2 | round 2 페어 reconcile (REVISE_REQUIRED) + 12 follow-up |

## 3. 핵심 발견 — W3D4 의 자료구조 layer 정상 + plumbing layer gap

### 3.1 입증된 정상 작동 (MaterialFidelityIntegrationTest 4/4 PASS)

- byNs LinkedHashMap insertion order ANDROID → RES_AUTO 정합
- 3-입력 통합 + 41 AAR walk (1087 styles 실측, plan v2 의 ">100" 만족)
- σ FULL chain walker — Theme.AxpFixture → ... → Theme.AppCompat → Theme (17 hop, MAX_THEME_HOPS=32 마진)
- colorPrimary / colorPrimaryContainer findItemInTheme — RES_AUTO bucket 안 정의 + Material3 chain inheritance 모두 작동
- RJarSymbolSeeder canonicalization (Theme_AxpFixture → Theme.AxpFixture) + cross-class attr first-wins

### 3.2 Acceptance gate 막은 2 gap (W3D4-β 의 fix 대상)

#### Gap A — Material ThemeEnforcement.checkAppCompatTheme 실패

```
java.lang.IllegalArgumentException: The style on this component requires your app theme to be Theme.AppCompat (or a descendant).
  at com.google.android.material.internal.ThemeEnforcement.checkAppCompatTheme(...)
  at com.google.android.material.button.MaterialButton.<init>(...)
```

가설: `BridgeContext.obtainStyledAttributes` ↔ `LayoutlibRenderResources` wiring 의 ID/name lookup mismatch. ThemeEnforcement 가 sentinel attr (R.attr.colorPrimaryDark 등) 를 R class ID 로 query 하나, RJarSymbolSeeder 의 namespace=RES_AUTO 등록과 BridgeContext 의 ID resolve 사이 path 가 끊김.

#### Gap B — Bridge getColorStateList 의 input feed 부재

```
[layoutlib.error] Failed to configure parser for @color/m3_highlighted_text
  java.io.IOException: input must be specified to setInput(InputStream)
```

가설: T3 AarResourceWalker 가 `res/values/values.xml` 만 처리, AAR 안 `res/color/*.xml` (state list `<selector>` 패턴) 미포함. MinimalLayoutlibCallback 의 resource-fetch hook (`getParser` / `getInputStream`) wiring 누락.

(자세한 분석 + 검증 필요 항목: `t9-acceptance-gate-discovery.md` §3 참조.)

## 4. 다음 세션의 첫 작업 — W3D4-β plan-revision phase

### 4.1 Round 3 페어 review 의 input

CLAUDE.md "Pairs REQUIRED for plan-document review rounds" — fundamental plan revision 가 필수. 본 round 의 input:

1. 본 핸드오프 + `t9-acceptance-gate-discovery.md` (gap 분석)
2. 베이스라인 코드 (`f2bc922`) 의 production stack — `LayoutlibRenderer.renderViaLayoutlib`, `BridgeContext`, `MinimalLayoutlibCallback`, `LayoutlibCallback` (layoutlib-api), `Bridge` (layoutlib core).
3. Material 의 `ThemeEnforcement.checkAppCompatTheme` 실 코드 (decompile from material-1.12.0.aar — 이미 W3D3 에서 했던 것 가능).
4. AAR 의 `res/color/*.xml` 카운트 + 패턴 (실측 — material/appcompat AAR 안 selector files).

### 4.2 W3D4-β 의 신규 task (예상)

- **T11**: Gap A fix. BridgeContext / LayoutlibRenderResources 의 ID-based attr lookup wiring. R$attr.colorPrimaryDark (또는 ThemeEnforcement 실 sentinel) 검증 + seeding 보강.
- **T12**: Gap B fix. T3 AarResourceWalker 확장 (res/color/*.xml 도 walk) + LayoutlibResourceValueLoader 가 color XML 별도 processing path. MinimalLayoutlibCallback 의 resource-fetch hook 활성화.
- **T13**: 회귀 — primary `@Disabled` 제거 + tier3_basic_primary PASS 확인 + W3D4-β acceptance gate 닫힘.

### 4.3 Plan v3 작성

`docs/superpowers/plans/<date>-w3d4-beta-plumbing.md` 또는 plan v2 in-place revise (W3D4 vs W3D4-β 명시). plan-revision 권장 패턴 = 새 plan 파일 (W3D4 의 close 는 보류, W3D4-β 가 자체 milestone).

### 4.4 Round 3 페어 dispatch

```
Codex: codex exec --skip-git-repo-check --sandbox danger-full-access (LM-G)
Claude: subagent (general-purpose) 또는 feature-dev:code-reviewer
검토 query (예시):
  Q1. Gap A 의 정확한 root-cause — ThemeEnforcement sentinel attr 식별 + BridgeContext wiring 검증.
  Q2. Gap B 의 정확한 root-cause — color XML state list 처리 위치 (parser? loader? callback?).
  Q3. T11/T12 의 fix 가 T1-T8 의 자료구조 변경 없이 plumbing-only 인가?
  Q4. 회귀 위험 — 기존 14 PASS + 2 SKIP integration suite 의 영향?
  Q5. tier3-glyph (W4 carry) 도 본 fix 로 영향 받는지 (font asset 처리 path 동일?)?
```

## 5. 본 세션의 누적 LM (W3D4 시리즈)

### 5.1 W3D3 시리즈 누적 (보존)

- LM-W3D3-A / LM-α-A / LM-W3D3-B / LM-α-B / LM-α-D / LM-G (전부 본 W3D4 에 적용 + 새 LM 추가).

### 5.2 W3D4 신규 LM

- **LM-W3D4-A** (round 1 spec): RenderResources base null/empty stub — σ FULL 강제. T6 가 fix.
- **LM-W3D4-B** (round 1 spec): R$style underscore↔dot canonicalization. T7 가 fix.
- **LM-W3D4-C** (round 1 spec): multiple R class 의 동명 attr first-wins. T7 가 fix.
- **LM-W3D4-D** (round 2 plan): plan v1 의 placeholder 관행 위험 (T7 callback API mismatch, T8 ctor reorder, T9 locateAll). T8/T9 implementer 가 explicit fix.
- **LM-W3D4-E** (T8 implementation): NamespaceAwareValueParser 의 mixed content (HTML markup, xliff:g) `elementText` throw — W3D1 readText 패턴 회복으로 fix.
- **LM-W3D4-F** (T8 implementation): NamespaceAwareValueParser 의 cross-NS attr ref (`android:visible` 같은 declare-styleable child) `:` in name 으로 ResourceReference ctor throw — parser-level skip 으로 fix.
- **LM-W3D4-G** (T9 acceptance, NEW): Material ThemeEnforcement 가 chain walker 가 정상이어도 sentinel attr lookup 실패 — Bridge ↔ resource layer plumbing gap (W3D4-β 에서 fix).
- **LM-W3D4-H** (T9 acceptance, NEW): Bridge getColorStateList 의 RES_AUTO color XML input feed 부재 — AAR `res/color/*.xml` walker 부재 + callback hook 미wiring (W3D4-β 에서 fix).

### 5.3 페어 review 의 한계 (W3D5+ playbook)

T2 의 두 latent bug (mixed content + cross-NS attr) + T9 의 두 production gap 모두 round 2 페어가 catch 못함. **공통 root cause = synthetic XML / mock fixtures 만 검증, real-world AAR / 실 production wiring 미접근**.

W3D5+ 페어 playbook 권고: real fixture (Material AAR + AppCompat AAR) 의 실 values.xml + color/*.xml 를 reviewer 의 verification material 에 포함. Production wiring end-to-end smoke 도 round 2 의 검증 수단으로.

## 6. 다음 세션 시작 시 참고 문서

1. **본 핸드오프** (이 파일) — `docs/work_log/2026-04-29_w3d4-material-fidelity/next-session-handoff.md`
2. **T9 acceptance gate discovery** — `docs/work_log/2026-04-29_w3d4-material-fidelity/t9-acceptance-gate-discovery.md` (자세한 gap 분석)
3. **T8 work_log** — `docs/work_log/2026-04-29_w3d4-material-fidelity/t8-renderer-integration-w3d1-absorption.md` (5 LM-T8-* + W3D5+ playbook)
4. **plan v2** — `docs/superpowers/plans/2026-04-29-w3d4-material-fidelity.md` (T1-T10 spec — T9 까지 implementation 완료)
5. **round 2 페어 review** — `docs/work_log/2026-04-29_w3d4-material-fidelity/round2-pair-review.md`
6. **spec round 2** — `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md`

## 7. Quick start commands

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer

# 베이스라인 verify
./server/gradlew -p server :layoutlib-worker:test                          # 151 PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration # 14 PASS + 2 SKIP

# Gap A empirical investigation (production stack)
grep -rn "ThemeEnforcement\|checkAppCompatTheme" server/ --include="*.kt"
grep -rn "obtainStyledAttributes\|resolveThemeAttribute" server/ --include="*.kt"
# Material AAR 의 ThemeEnforcement decompile (W3D3 에서 한 패턴)
javap -p -c ~/.gradle/caches/.../material-1.12.0.aar/!classes.jar:com/google/android/material/internal/ThemeEnforcement

# Gap B empirical investigation
grep -rn "getColorStateList\|getParser\|MinimalLayoutlibCallback" server/ --include="*.kt"
# AAR 안 res/color/*.xml 카운트 (Codex 실측 패턴):
unzip -l ~/.gradle/caches/.../material-1.12.0.aar | grep "res/color/" | wc -l

# Plan v3 작성 후 round 3 pair dispatch (codex exec direct CLI)
```

## 8. carry 0 / handoff complete

본 세션의 모든 implementation 결과 (T1-T9) origin/main 에 push. 자료구조 + chain walker layer 의 가치 보존 (MaterialFidelity 4/4 PASS). plan-revision 의 input 모두 work_log 에 capture — fresh session 에서 cold-start OK.
