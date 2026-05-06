# T19 acceptance gate followup — δ-A closed, δ-B escalation

날짜: 2026-05-06
선행 head: `22f7077` (main, T18 STYLE special-case applied)
plan: `docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md` v3.2

---

## §1 outcome

- W3D4-δ-A (TextAppearance sentinel) 닫힘 ✓ — T18 의 `LayoutlibResourceBundle.getResource` STYLE special-case 가 BridgeContext + BridgeTypedArray 양쪽 instanceof gate 동시 enable.
- W3D4-δ-B (gate chain `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant`) 가 다음 fail surface 로 노출 — plan v3.2 §6.3 가 정확히 예측한 escalation chain.
- `tier3 basic primary — activity_basic 가 직접 SUCCESS` 의 `@Disabled` 는 reason 만 갱신 (δ-A → δ-B). plan §11 에 따라 δ-B 는 별도 plan/phase 로 escalate.

## §2 측정 (T18 적용 후)

```
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

이전 (T18 전) δ-A surface:
```
msg=This component requires that you specify a valid TextAppearance attribute.
    Update your app theme to inherit from Theme.MaterialComponents (or a descendant).
```

## §3 surface 분류 (LM-W3D4-β-H 적용)

| 측면 | δ-A (closed) | δ-B (open) |
|---|---|---|
| 메시지 | "specify a valid **TextAppearance**" | "**The style on this component** requires your app theme to be Theme.MaterialComponents" |
| Throw entry | `checkTextAppearance` (te_disasm:158-239) | `checkTheme:247` via `checkMaterialTheme:216` |
| 진입 경로 | M2 path `?attr/textAppearanceButton` resolution → BridgeTypedArray.getResourceId instanceof gate | M2 path `enforceMaterialTheme=true` (Widget.MaterialComponents.Button:6349) → checkCompatibleTheme → checkMaterialTheme |
| Sentinel attr | `android:textAppearance` (slot 0 of ThemeEnforcement_styleable) | `colorPrimaryVariant` (W3D4-δ planning §1.1 7-attr census #7 — open) |
| 닫는 fix | bundle.getResource(STYLE) 의 styles map 위임 (T18) | gate chain 의 colorPrimaryVariant chain resolution 또는 enforceMaterialTheme=false 로 우회 |

stack trace 의 `MaterialButton.<init>` offset 이 T18 전 53 (`obtainStyledAttributes`) 에서 동일하지만 — T18 전은 `obtainStyledAttributes` 내부의 `checkTextAppearance` 가 throw, T18 후는 동일 entry 의 `checkCompatibleTheme → checkMaterialTheme` 가 throw. 즉 ThemeEnforcement 가 multi-stage 검사 — δ-A 통과 후 δ-B 가 다음 stage 에서 검사.

## §4 plan v3.2 의 예측

§6.3 발췌:
> **W3D4-δ-B (gate chain — 가장 가까운 escalation)**: `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant` 의 3-step gate. `Widget.MaterialComponents.Button:6349` 가 `enforceMaterialTheme=true` 명시 → M2 path 진입 시 `checkCompatibleTheme` (te_disasm.txt:90-156) 가 `Theme.resolveAttribute(R.attr.isMaterialTheme)` 호출 후 false 면 `checkMaterialTheme(ctx)` (te_disasm.txt:315-327) 호출 → `int[]{R.attr.colorPrimaryVariant} hasValue` 검사 → false 면 throw `"… requires your app theme to be Theme.MaterialComponents (or a descendant)."`.

실측 throw 메시지와 §6.3 의 예측 메시지 정합 — δ-B 가 정확한 next surface.

## §5 escalation 정책

W3D4-δ phase 의 plan v3.2 scope 안에서는 δ-B 가 §11 명시 out-of-scope. δ-B 는 별도 phase:

- **W3D4-δ-B plan 작성 (다음 세션)**: ThemeEnforcement.checkMaterialTheme 의 `colorPrimaryVariant hasValue` 검증을 어떻게 통과시키는가:
  - 옵션 A — `isMaterialTheme=true` 를 fixture theme chain 안에서 expose 하여 `checkCompatibleTheme` 의 first-gate (te_disasm:118-130) 가 즉시 return → `checkMaterialTheme` 미진입.
  - 옵션 B — `colorPrimaryVariant` 를 fixture chain 안에서 정합 resolve 시키기 (Lvl 5:2214 chain 검사).
  - 옵션 C — M3 path dispatch (`Widget.Material3.Button` 사용 시 `enforceMaterialTheme` 미상속) — H2 fix 가 이미 enable, 단 `materialButtonStyle` chain 이 M3 path 로 resolve 되는지 추가 검증.

- **subagent investigation (다음 세션 entry)**: te_disasm.txt 의 checkCompatibleTheme/checkMaterialTheme bytecode + Theme.AxpFixture chain 의 isMaterialTheme/colorPrimaryVariant resolve 측정. plan §6.3 의 H2 fix 가 M3 path enable 했는지 확인 (T17 의 H3a `materialButtonStyle → @style/Widget.Material3.Button` PASS 가 partial 증거).

- **carry**: T17 의 5-probe diagnostic IT 는 본 phase 의 회귀 가드로 보존 (δ-B fix 후도 5/5 PASS 보장).

## §6 본 commit 의 구성

- `LayoutlibRendererIntegrationTest.tier3 basic primary` 의 `@Disabled` annotation 갱신:
  - 이전 reason: δ-A (TextAppearance sentinel)
  - 갱신 reason: δ-B (gate chain `enforceMaterialTheme → checkMaterialTheme`) 영문 + structural-only.
- `t19-acceptance-gate-followup.md` (본 파일).
- IT runner: `tier3 basic primary` 다시 SKIP 으로 분류 — IT-runner GREEN 유지 (default unit suite 도 GREEN, T17 IT-tagged + 5/5 PASS 보존).

## §7 다음 세션 entry 신호

- carry-forward LM:
  - LM-W3D4-δ-A (spec sample API verify 의무) — 본 phase 적용 후 새로 추가된 plan/spec 작성 시 동일 적용.
  - LM-W3D4-δ-B (NsBucket type-specific map 분리 KILL POINT) — 새 type-specific map 추가 시 회귀 가드 (γ T14 ATTR + δ T18 STYLE 두 차례 catch).
- 다음 phase 진입 = W3D4-δ-B 또는 W3D4-ε (R$styleable layer) — 어느 path 가 더 effective 한지 plan-revision 단계 측정.
