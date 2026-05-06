# T22 acceptance gate followup — δ-A + δ-B (option B) closed at chain-walker layer, render-time gate persists at setupResources mutation layer

날짜: 2026-05-06
선행 head: `2175eb1` (main, T20 + T21 applied)
plan: `docs/superpowers/specs/2026-05-06-w3d4-delta-b-gate-chain-design.md` v4.1

---

## §1 outcome

- W3D4-δ-A (TextAppearance sentinel) **CLOSED** ✓ — T18 의 STYLE special-case fix.
- W3D4-δ-B option B (colorPrimaryVariant chain) **CLOSED at chain-walker layer** ✓ — T20 5/5 PASS + T17 5/5 PASS regression guard.
- Acceptance gate (`activity_basic` SUCCESS) **여전히 fail** — fail surface 가 chain-walker layer 가 아닌 *render-time setupResources mutation layer* (round 6 Codex Q1 caveat 정확 적중).
- W3D4-δ phase 종결 미달성 — W3D4-δ-C (또는 W3D4-ε) 별도 phase escalation 필요.

## §2 측정 (T20 + T21 적용 후)

### §2.1 chain-walker layer (PASS)

```
W3D4DeltaBGateChainProbeTest > P1 — colorPrimaryVariant attr is defined in the bundle as RES_AUTO() PASSED
W3D4DeltaBGateChainProbeTest > P2 — findItemInTheme colorPrimaryVariant returns the chain item() PASSED
W3D4DeltaBGateChainProbeTest > P3 — resolveResValue of the chain item terminates on a concrete color value() PASSED
W3D4DeltaBGateChainProbeTest > P4 — T18 regression guard — getResource STYLE Widget_Material3_Button still returns the style() PASSED
W3D4DeltaBGateChainProbeTest > P5 — colorPrimary chain anchor is concrete in fixture (anchor of P3 chain) PASSED

W3D4DeltaThemeChainDiagnosticTest > H1 ... PASSED
W3D4DeltaThemeChainDiagnosticTest > H2 ... PASSED
W3D4DeltaThemeChainDiagnosticTest > H2-bridgeTypedArray gate ... PASSED
W3D4DeltaThemeChainDiagnosticTest > H3a ... PASSED
W3D4DeltaThemeChainDiagnosticTest > H3b ... PASSED
```

### §2.2 acceptance gate (fail)

```
[layoutlib.logAndroidFramework] 4 | ThemeUtils | View class com.google.android.material.button.MaterialButton is an AppCompat widget that can only be used with a Theme.AppCompat theme (or descendant).
[layoutlib.warning] resources.resolve.theme | Failed to find '@attr/textAppearanceButton' in current theme. | null | @attr/textAppearanceButton
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION
  msg=The style on this component requires your app theme to be Theme.MaterialComponents (or a descendant).
  exc=IllegalArgumentException
[LayoutlibRenderer] RenderSession.render failed: status=ERROR_NOT_INFLATED msg=null exc=null
→ IllegalStateException: LayoutlibRenderer 실패 + fallback 없음: activity_basic.xml

Caused by: java.lang.IllegalArgumentException: The style on this component requires
        your app theme to be Theme.MaterialComponents (or a descendant).
    at com.google.android.material.internal.ThemeEnforcement.checkTheme(ThemeEnforcement.java:247)
    at com.google.android.material.internal.ThemeEnforcement.checkMaterialTheme(ThemeEnforcement.java:216)
    at com.google.android.material.internal.ThemeEnforcement.checkCompatibleTheme(ThemeEnforcement.java:144)
    at com.google.android.material.internal.ThemeEnforcement.obtainStyledAttributes(ThemeEnforcement.java:76)
    at com.google.android.material.button.MaterialButton.<init>(MaterialButton.java:239)
```

T19 측정 (option B 적용 *전*) 과 throw 메시지 + 진입 entry 정확히 동일 — *layer shift 발생 안 함*. 단 system-err 의 새 warning `[layoutlib.warning] resources.resolve.theme | Failed to find '@attr/textAppearanceButton' in current theme.` 가 결정적 differential evidence.

## §3 surface 분류 (LM-W3D4-β-H + LM-W3D4-δ-E 적용)

| 측면 | δ-B (T19 측정) | δ-B (T22 측정, option B 적용 후) |
|---|---|---|
| 메시지 | "...requires your app theme to be Theme.MaterialComponents..." | 동일 (literal) |
| Throw entry | `checkTheme:247` via `checkMaterialTheme:216` | 동일 |
| chain walker layer | n/a (T17/T20 미실행) | **PASS** (T17 5/5 + T20 5/5) |
| render-time `findItemInTheme(textAppearanceButton)` | unknown | **FAIL** (`Failed to find '@attr/textAppearanceButton' in current theme` warning) |
| 결정적 differential | T19 후 system-err 분석 미완 | **chain walker = PASS but render-time = FAIL → 두 layer 가 서로 다른 mThemeStack instance** |

## §4 root cause 가설 (round 6 Codex Q1 caveat 정확 적중)

`Resources_Theme_Delegate.setupResources` (`/tmp/w3d4db-codex-Resources_Theme_Delegate-javap.txt:165-205`) bytecode trace:

```
private static boolean setupResources(android.content.res.Resources$Theme theme):
  165:  Theme.getKey() → Resources$ThemeKey
  171:  ThemeKey.mResId → int[]                 // numeric style IDs applied via Theme.applyStyle(int)
  174:  ThemeKey.mForce → boolean[]
  181:  ThemeKey.mCount → int                   // length of mResId
  185-202: for (i = 0; i < mCount; i++):
    188:    int styleId = mResId[i]
    189:    StyleResourceValue style = resolveStyle(styleId)    // BridgeContext-internal lookup by numeric ID
    192:    if (style == null) continue
    193-199: getCurrentContext().getRenderResources().applyStyle(style, mForce[i])
                                                        // ← MUTATES our LayoutlibRenderResources.mThemeStack
```

`obtainStyledAttributes(Resources, Theme, int[])` (`/tmp/w3d4db-codex-Resources_Theme_Delegate-javap.txt:23-31` 영역) 흐름:
```
0: setupResources(theme)              // mutate stack
5: BridgeContext.internalObtainStyledAttributes(0, attrs)    // uses MUTATED stack
21: restoreResources(...)              // restore
```

따라서 layoutlib 의 `Resources.Theme.obtainStyledAttributes(int[])` (ThemeEnforcement.isTheme 가 사용) 는:
1. `setupResources` 가 `Theme.getKey().mResId` 의 numeric style IDs 로 `applyStyle` 호출 → `LayoutlibRenderResources.mThemeStack` mutate.
2. `applyStyle(style, force=true)` 시 `mThemeStack.clear()` (LayoutlibRenderResources.kt:228) — *fixture chain 전체 wipe*.
3. mutated stack 상에서 `BridgeContext.createStyleBasedTypedArray(null, attrs)` → `mRenderResources.findItemInTheme` 호출 — fixture 의 직접 attr 정의 invisible.

**즉 옵션 A 도 옵션 B 도 chain-walker layer 의 변경이므로, setupResources 가 mutate 한 후의 stack 에서는 fixture-direct definition 이 *동일하게* invisible**. fixture 정의가 살아남으려면 setupResources mutation path 자체를 우회하거나 mForce 가 false 가 되도록 layoutlib runtime 에 상태 통보해야 함.

## §5 escalation 정책

### §5.1 W3D4-δ-C (next phase, setupResources mutation layer)

- **option C-1**: `LayoutlibRenderResources.applyStyle(style, useAsPrimary)` 의 `useAsPrimary=true` 시 `mThemeStack.clear()` 동작을 *fixture chain 보존* 으로 변경. setupResources 가 force=true 로 push 시도해도 fixture 의 default theme stack 이 보존되도록.
- **option C-2**: `BridgeContext.resolveStyle(int)` 의 callback layer (MinimalLayoutlibCallback.byId) 가 numeric style ID → StyleResourceValue 매핑을 우리 bundle 의 styles map 으로 위임. setupResources 가 *우리* StyleResourceValue instance 를 받아 applyStyle → mThemeStack 의 동일 instance 가 push 됨.
- **option C-3**: setupResources 미경유 path 발견. `Resources.Theme.obtainStyledAttributes(int[])` 외 다른 obtainStyledAttributes 변형이 setupResources 미호출.

### §5.2 W3D4-ε (R$styleable layer, plan v3.2 §5.3.1 carry)

- BridgeContext callback layer (`getOrGenerateResourceId` / `resolveResourceId`) instrumentation. `axp.debug.callback=true` toggle 로 enable. R$styleable 의 numeric ID → fixture chain 의 StyleResourceValue 매핑 단절 측정.

### §5.3 carry-forward

- T17 5-probe + T20 5-probe 둘 다 regression guard — δ-C / ε fix 후도 PASS 보장 필수.
- LM-W3D4-δ-G — `@Disabled` message 의 새 reason (setupResources mutation) 작성 시 file:line evidence 인용 (Resources_Theme_Delegate-javap.txt:165-205) — 본 followup md 가 evidence 자체.

## §6 본 commit 의 구성

- `LayoutlibRendererIntegrationTest.tier3 basic primary` 의 `@Disabled` reason 갱신 (δ-B option B 적용 후 setupResources mutation layer 로 escalation 명시, 영문 + structural-only).
- `t22-acceptance-gate-followup.md` (본 파일).
- IT runner: `tier3 basic primary` SKIP 로 분류 — IT-runner GREEN 유지 (default unit suite 도 GREEN).

## §7 다음 세션 entry 신호

### W3D4-δ-C planning entry (다음 세션)
- 본 followup §5.1 의 3 option (C-1 / C-2 / C-3) cost-benefit + minimal change 결정.
- subagent investigation: setupResources call site frequency + applyStyle force semantics + callback resolveStyle signature.
- round 7 Codex+Claude pair-review (planning ONLY, CLAUDE.md §Codex).

### carry-forward LM
- LM-W3D4-δ-A~G (모두 carry).
- T22 의 setupResources mutation layer 분석 → LM-W3D4-δ-H 신규 가능성 (chain-walker 검증과 render-time 검증의 분리 — IT 에서는 chain walker 만 측정, render path 는 별도 instrumentation 필요).

---

## §8 follow-up (2026-05-06, applyStyle preserve fix 적용 후)

### §8.1 Outcome — δ-A + δ-B 양쪽 closed, 새 surface 는 animator XML feed layer

`LayoutlibRenderResources.applyStyle` 의 `useAsPrimary` clear() 제거 → fixture parent chain 보존.

T22 재측정 결과:
- 이전 fail: `IllegalArgumentException: The style on this component requires your app theme to be Theme.MaterialComponents (or a descendant)` (ThemeEnforcement.checkMaterialTheme)
- 새 fail: `XmlPullParserException: No Input specified (position:START_DOCUMENT null@0:0)` from `AnimatorInflater.loadStateListAnimator(AnimatorInflater.java:189)` ← `View.<init>(View.java:6033)` ← `MaterialButton.<init>`

**δ-A (TextAppearance) + δ-B (gate-chain) 양쪽 closed** ✓ — `Failed to find '@attr/textAppearanceButton' in current theme` warning 사라짐, ThemeEnforcement throw 제거.

### §8.2 새 surface — animator XML feed mechanism 부재

`Widget.Material3.Button:5250` 의 `<item name="android:stateListAnimator" ns1:ignore="NewApi">@animator/m3_btn_state_list_anim</item>` 가 trigger. Material AAR `res/animator/m3_btn_state_list_anim.xml` 의 raw XML body 가:
- `LayoutlibResourceValueLoader` / `AarResourceWalker` 의 처리 surface 외 (현 walker 가 `res/values/values.xml` + `res/color/*.xml` 만 enumerate, T12 이전 phase 의 surface).
- `MinimalLayoutlibCallback.getParser` (line 116-131) 의 `ResourceType.COLOR` 만 accept, `ResourceType.ANIMATOR` fall-through null 반환.
- 결과: layoutlib `AnimatorInflater.loadStateListAnimator` 의 `XmlResourceParser parser = res.getAnimator(id)` path 에서 input 없이 parse → throw.

본 surface 는 **W3D4-δ-D (animator XML feed)** 또는 **W3D4 tier3-glyph 와 동시 처리** (W4 carry):
- 옵션 D-1: AarResourceWalker 에 `res/animator/` enumeration 추가 (T12 의 `res/color/` 패턴 mirror) + NsBucket 에 animator map 신규 + callback.getParser 의 ResourceType.ANIMATOR routing.
- 옵션 D-2: 빈 animator XML stub 으로 우회 (ProgressDialog/Dialog-style fallback) — visual fidelity 손실 가능.

### §8.3 closed milestone

본 commit (`applyStyle` preserve fixture chain) 이 W3D4-δ 시리즈의 Material gate-chain 의 close — γ enum/flag → δ-A TextAppearance → δ-B gate-chain 의 progressive escalation chain 종착. ThemeEnforcement 의 sentinel attr 검사 모두 우회 또는 통과.

Test posture:
- 모듈 합산 unit: 241 PASS (변동 없음)
- IT: 24 PASS + 2 SKIP (T17 5/5 + T20 5/5 + tier3-basic-primary `@Disabled` (animator surface) + tier3-glyph W4 carry)
- δ-A closed, δ-B closed, animator surface escalation 명시.

LM-W3D4-δ-I (신규) — `applyStyle(useAsPrimary)` 의 layoutlib runtime semantic: `Resources_Theme_Delegate.setupResources` 가 force=true 로 호출하지만 *기존 stack wipe 의도가 아닌 head-priority 갱신*. RenderResources subclass 의 `applyStyle` 구현 시 `clear()` 호출하면 fixture/computeInitialStack 결과 wipe — chain walker 와 render-time stack divergence 의 root cause. future RenderResources subclass 작성 시 동일 LM 적용.
