# T16 acceptance-gate followup — γ-A 닫힘 + 새 surface (W3D4-δ escalation)

날짜: 2026-04-30 → 2026-05-01 (T14/T15 commit 후 즉시 측정)
선행 commit: T14 (`3ca8bb5`), T15 (`1541958`)
현재 head: T16 partial 직전

---

## §1 측정된 사실

### §1.1 Plan v3.1 의 3 target warning 제거 검증

`tier3 basic primary` `@Disabled` 제거 후 1회 실행. 이전 W3D4-β 종료 시 발화하던 enum/flag 변환 fail 3건 (`docs/work_log/2026-04-30_w3d4-beta-plumbing/t13-acceptance-gate-followup.md §1.3` 참조):

```
[layoutlib.warning] resources.format | "vertical" in attribute "orientation" is not a valid integer
[layoutlib.warning] resources.format | "center_horizontal" in attribute "gravity" is not a valid integer
[layoutlib.warning] resources.format | "parent" in attribute "layout_constraintEnd_toEndOf" is not a valid integer
```

→ **모두 부재** (test result XML system-err 캡처 — 아래 §1.3 참조). γ-A.1 (framework `Bridge.sEnumValueMap` 채우기) + γ-A.2 (RES_AUTO `AttrResourceValueImpl.addValue` + `LayoutlibResourceBundle.getResource` ATTR special-case) 가 의도대로 작동.

### §1.2 새 fail surface — W3D4-δ-A escalation

`tier3 basic primary` 의 새 fail:

```
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION
  msg=This component requires that you specify a valid TextAppearance attribute.
      Update your app theme to inherit from Theme.MaterialComponents (or a descendant).
  exc=IllegalArgumentException
[LayoutlibRenderer] RenderSession.render failed: status=ERROR_NOT_INFLATED msg=null exc=null
→ IllegalStateException: LayoutlibRenderer 실패 + fallback 없음: activity_basic.xml
```

→ Material ThemeEnforcement 가 colorPrimary (W3D4-β Gap A 의 닫힘 surface) 외 **추가 sentinel attr — TextAppearance 계열** 도 검사. Theme.AxpFixture 의 parent chain 또는 Material AAR 의 styleable 정의에 또 한 layer 의 inheritance check 가 있음 (γ-D hypothesis 와 다름 — cross-NS chain 미스가 아니라 sentinel attr 의 multi-check).

### §1.3 system-err 발췌 (실측, test xml)

```
[AarResourceWalker] walked 41 AARs (12 with res, 29 code-only, 191 color-state-lists) in 30ms
[LayoutlibResourceValueLoader] cold-start framework=37ms app=1ms aar=34ms build=13ms total=85ms
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION msg=This component requires that you specify a valid TextAppearance attribute. Update your app theme to inherit from Theme.MaterialComponents (or a descendant). exc=IllegalArgumentException
[LayoutlibRenderer] RenderSession.render failed: status=ERROR_NOT_INFLATED msg=null exc=null
```

이전 발화하던 enum/flag warning 3건 부재 — γ-A 의 직접 효과 확정.

## §2 Root cause hypothesis — W3D4-δ-A

### §2.1 ThemeEnforcement 의 multi-sentinel check

W3D4-β 의 Gap A 가 닫힌 후 (RJarSymbolSeeder RES_AUTO 통일 + bundle 의 colorPrimary lookup 정합), Material AAR 안의 `com.google.android.material.internal.ThemeEnforcement` 가 *추가* sentinel attr 을 검사:

가설:
1. Material 의 `MaterialButton` 또는 sibling widget 이 `obtainStyledAttributes` 로 `R$styleable.MaterialButton` (또는 parent class 의 styleable) 를 query.
2. 그 styleable 안에 `textAppearanceLargeButton` / `textAppearanceBodyMedium` / `textAppearanceLabelLarge` 등의 attr 를 expected.
3. `Theme.AxpFixture` 의 parent chain 이 그 attr 을 정의하지 않거나, 정의된 attr 의 ResourceReference 가 우리 bundle 의 RES_AUTO 좌표계와 어긋남 (colorPrimary 와 동일 W3D4-β Gap A 의 sibling case).

### §2.2 후속 검증 단계 (W3D4-δ 진입 시)

1. layoutlib `ThemeEnforcement` bytecode (`com.google.android.material.internal.ThemeEnforcement`) 의 `checkAppCompatTheme` 외 다른 메서드 (예: `checkMaterialTheme`, `checkTextAppearance`) 식별 → 검사 sentinel attr 목록.
2. `fixture/sample-app/app/src/main/res/values/themes.xml` 의 `Theme.AxpFixture` 정의 + Material parent inheritance 확인.
3. `tier3-basic-primary` 의 stack trace 에서 IllegalArgumentException 이 어느 widget 의 어느 attr 검사 시 발화하는지 (현재 메시지에 이름 없음 — Material verbose log 활성 또는 reflection 으로 검사 path trace 필요).
4. plan v3.2 작성 → round 5 pair-review → T17 (또는 T-δ 시리즈) 구현.

## §3 본 세션 결과 요약

| Task | 산출 | 상태 |
|---|---|---|
| T14 (γ-A.2 RES_AUTO) | ParsedNsEntry.AttrDef + parser child loop + Bundle addValue + getResource ATTR (commit `3ca8bb5`) | **DONE** — 189 unit / 0 fail (+17), IT 14+2 SKIP |
| T15 (γ-A.1 framework wiring) | LayoutlibResourceBundle.frameworkEnumValueMap + LayoutlibRenderer.initBridge enumValueMap inject (commit `1541958`) | **DONE** — 194 unit / 0 fail (+5), IT 14+2 SKIP, cold-start 85ms |
| T16 (acceptance gate) | tier3-basic-primary 1회 실행 후 fail surface 분류 + 재 `@Disabled` (W3D4-δ-A reason) | **PARTIAL** — gap shifted, W3D4-δ escalate (LM-W3D4-β-H 정확히 실현) |

3 target warning 제거 = γ-A 의 production-pipeline path closure 증명. 새 surface (TextAppearance sentinel) 는 본 plan 범위 밖 — W3D4-δ.

## §4 모듈 합산 test 카운트 (T16 partial 후)

| 모듈 | unit |
|---|---|
| protocol | 16 |
| http-server | 5 |
| mcp-server | 22 |
| layoutlib-worker | 194 |
| **합계** | **237** (baseline 215 → +22) |

IT (layoutlib-worker only): 14 PASS + 2 SKIP (`tier3-glyph` W4 carry, `tier3-basic-primary` W3D4-δ-A carry).

## §5 다음 단계

1. T16 partial commit + push (re-`@Disabled` with W3D4-δ-A reason + 본 work_log).
2. session-log.md 갱신 (T14/T15/T16 산출 + 새 LM 후보).
3. (다음 세션) `docs/superpowers/specs/2026-05-XX-w3d4-delta-...` (W3D4-δ-A spec, TextAppearance sentinel 분석 시작점).
4. round 4 패턴 inheritance — γ 가 plan v3 (β 의 후속) 의 후속이듯 δ 도 v3.2 patterning.

## §6 반영된 LM (W3D4-γ session)

- LM-W3D4-β-D~H 모두 carry over → 본 session 에서 회피 검증:
  - LM-D (KDoc `/*`): plan + 신규 코드 KDoc 모두 backtick 인용 ✓
  - LM-E (assertNotNull chain): `val v = ...; assertNotNull(v); ...` 패턴 ✓
  - LM-F (-PincludeTags=integration): IT 측정 시 명시 ✓
  - LM-G (subagent unzip --directory): round 4 Codex CLI 사용 일관 ✓
  - **LM-H (acceptance fail surface 분류)**: 본 followup 의 §1-§2 가 정확히 이 패턴 수행 — 새 fail (TextAppearance sentinel) 을 plan §5.3 의 hypothesis 와 다른 새 surface 로 분류, escalation 명확화 ✓

신규 LM (W3D4-γ session 산출):
- **LM-W3D4-γ-A**: ThemeEnforcement 의 multi-sentinel check — colorPrimary 닫힘 후 TextAppearance 가 다음 layer. Material AAR 안 ThemeEnforcement 의 sentinel surface 가 stepwise 발견됨. 향후 phase 진입 시 ThemeEnforcement bytecode 의 모든 sentinel 검사 메서드 (`checkAppCompatTheme`, `checkMaterialTheme`, `checkTextAppearance` 등) 를 한 번에 census 권장.
