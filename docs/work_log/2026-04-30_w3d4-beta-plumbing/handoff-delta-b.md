# W3D4-δ-B planning entry — handoff

날짜: 2026-05-06 (생성), 다음 세션 entry: W3D4-δ-B **planning** (plan-revision phase, Claude+Codex 1:1 pair-review)
선행 head: `7462016` (main, W3D4-δ T17/T18/T19 + session-log append 완료)

---

## §1 어디까지 끝났나 (W3D4-δ-A close + δ-B 노출)

W3D4-δ implementation phase (plan v3.2 의 T17 → T18 → T19) 완료. 4 commit pushed:

| commit | 내용 |
|---|---|
| `2ce640a` T17 | `W3D4DeltaThemeChainDiagnosticTest.kt` 신규 — 5-probe diagnostic IT (intentional RED-on-main, IT-tagged) |
| `22f7077` T18 | `LayoutlibResourceBundle.getResource` STYLE special-case (3-line) + `axp.debug.bundleShape` bootstrap log + 4-case regression test. T17 5/5 PASS 전환 |
| `473f55a` T19 partial | `tier3 basic primary` `@Disabled` reason 갱신 (δ-A → δ-B structural reason) + `t19-acceptance-gate-followup.md` |
| `7462016` session-log | δ-phase implementation summary append + LM combined index |

**W3D4-δ-A close 검증** (TextAppearance sentinel layer):
- T18 적용 후 T17 5-probe diagnostic 5/5 PASS — H2 (getResource STYLE ref) + bridgeTypedArray instanceof gate 둘 다 PASS 로 전환.
- acceptance gate fail message shift — `checkTextAppearance` (δ-A) entry 가 더 이상 throw 안 함; `checkCompatibleTheme → checkMaterialTheme` (δ-B) entry 에서 throw.
- 즉 plan v3.2 §5.1 의 H2 KILL POINT fix 가 BridgeContext + BridgeTypedArray 양쪽 instanceof gate 동시 enable — round 5 Q5 conditional framing 의 양 path coverage 검증 ✓.

**Test posture (현재 main)**:

| 측정 | baseline (T16 partial) | T19 commit 후 |
|---|---|---|
| 모듈 합산 unit | 237 PASS | **241 PASS** (+4) |
| layoutlib-worker IT (`-PincludeTags=integration`) | 14 PASS + 2 SKIP | **19 PASS + 2 SKIP** (+5 T17 probes; δ-B + tier3-glyph SKIP) |
| acceptance gate (`activity_basic` SUCCESS) | δ-A `@Disabled` carry | **δ-A closed**, δ-B `@Disabled` carry |
| T17 diagnostic 5-probe (regression guard) | n/a | **5/5 PASS** |

## §2 새 fail surface — W3D4-δ-B

### §2.1 증상 (T19 측정)

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

이전 (T18 전, δ-A) 메시지: `"...specify a valid TextAppearance attribute. Update your app theme to inherit from Theme.MaterialComponents..."`. 비교 → **메시지 자체로 layer shift 식별** (LM-W3D4-δ-E 패턴).

### §2.2 plan v3.2 §6.3 의 정확한 예측

> **W3D4-δ-B (gate chain — 가장 가까운 escalation)**: `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant` 의 3-step gate. `Widget.MaterialComponents.Button:6349` 가 `enforceMaterialTheme=true` 명시 → M2 path 진입 시 `checkCompatibleTheme` (te_disasm.txt:90-156) 가 `Theme.resolveAttribute(R.attr.isMaterialTheme)` 호출 후 false 면 `checkMaterialTheme(ctx)` (te_disasm.txt:315-327) 호출 → `int[]{R.attr.colorPrimaryVariant} hasValue` 검사 → false 면 throw `"… requires your app theme to be Theme.MaterialComponents (or a descendant)."`.

실측 throw 메시지 정확히 일치 — δ-B 가 next surface confirmed.

### §2.3 3-option closure ladder (`t19-acceptance-gate-followup.md` §5)

| 옵션 | 내용 | 비고 |
|---|---|---|
| **A** | `isMaterialTheme=true` 를 fixture theme chain 에서 expose → `checkCompatibleTheme` first-gate (te_disasm:118-130) 즉시 return → `checkMaterialTheme` 미진입 | minimal change 가능성 — Theme.AxpFixture chain 의 어느 Lvl 에서 isMaterialTheme=true 가 정합 resolve 되는지 측정 |
| **B** | `colorPrimaryVariant` chain resolution 정합 (Lvl 5:2214 `colorPrimaryVariant → ?attr/colorPrimary` 또는 Lvl 9:3023 의 concrete color) → `checkMaterialTheme` 의 `int[]{R.attr.colorPrimaryVariant} hasValue` PASS | chain 의 attr-ref hop 검사 — H2 fix 와 동일 layer 의 ATTR side, 추가 fix surface 가능성 |
| **C** | M3 path dispatch (`Widget.Material3.Button` 사용) — `enforceMaterialTheme=false` 이므로 본 gate skip | T17 H3a (`materialButtonStyle → @style/Widget.Material3.Button` PASS) 가 partial 증거. `materialButtonStyle` chain 이 actually M3 path 를 dispatch 하는지 BridgeContext 측 확인 필요 |

planning 단계의 핵심 질문: **세 옵션 중 어느 것이 minimal change & maximum coverage 인가?** Codex+Claude 1:1 pair-review (CLAUDE.md §Codex: planning ONLY) 로 결정.

## §3 다음 세션 진입 단계 — planning phase

### §3.1 cold-read 권장 docs (이 순서)

1. **`docs/work_log/2026-04-30_w3d4-beta-plumbing/handoff-delta-b.md`** (본 파일)
2. `docs/work_log/2026-04-30_w3d4-beta-plumbing/t19-acceptance-gate-followup.md` (δ-A close + δ-B surface 분류 + 3-option ladder)
3. `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (β/γ/δ 전체 progressive escalation 카탈로그, δ-implementation append 포함)
4. `docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md` (plan v3.2 — §6.3 escalation 정책 + §11 out-of-scope 명시 + 7-attr sentinel census §1.1)
5. `docs/superpowers/plans/2026-04-30-w3d4-gamma-attr-enum-flag.md` (γ plan v3.1 — NsBucket type-specific 분리 패턴 background)

### §3.2 본 세션 첫 작업 = plan-revision (planning phase)

#### 단계 1 — investigation (subagent burst, Material AAR + theme chain)

3-stream parallel investigation 권장 (subagent A/B/C, 각각 IT 측정 가능):

- **Subagent A — `te_disasm.txt:90-156` checkCompatibleTheme bytecode 정밀 분석**:
  - first-gate (lines 118-130): `Theme.resolveAttribute(R.attr.isMaterialTheme, value, true)` 의 `value.data != 0` 검사. true 면 즉시 return — `checkMaterialTheme` 미진입.
  - second-gate: `Theme.resolveAttribute(R.attr.theme, value, false)` 검사 후 `checkMaterialTheme(ctx)` 호출.
  - `int[]{R.attr.colorPrimaryVariant}` hasValue probe 의 정확한 indirection (Theme.obtainStyledAttributes? TypedArray.hasValue?).
  - 출력: 옵션 A vs C 의 separately-actionable 검증 (어느 Lvl 의 isMaterialTheme 가 첫 gate 통과시키는지).

- **Subagent B — Theme.AxpFixture chain 안 isMaterialTheme + colorPrimaryVariant resolve 경로 측정**:
  - 15-level chain (plan v3.2 §1.2) 안 어느 Lvl 에서 `<item name="isMaterialTheme">true</item>` 가 정의되는지 grep (Material AAR `themes_material.xml` 또는 fixture themes).
  - Lvl 5:2214 의 `colorPrimaryVariant → ?attr/colorPrimary` chain 이 chain walker 단계에서 valid 하게 resolve 되는지 — T17-style probe 가능 (`resources.findItemInTheme(ATTR colorPrimaryVariant)` PASS 여부).
  - 출력: 옵션 A 와 옵션 B 의 chain validity 분류.

- **Subagent C — BridgeContext defStyleAttr → defStyleRes 의 M2/M3 dispatch 검증**:
  - T17 H3a `materialButtonStyle → @style/Widget.Material3.Button` 는 PASS — chain walker 가 M3 style 을 발견. 그러나 BridgeContext 가 *실제로 M3 dispatch 했는지* 는 별도 검증 필요 (M3 path 는 `enforceMaterialTheme` 미상속 — Widget.Material3.Button:??? 에 enforceMaterialTheme 정의 부재 확인).
  - `BridgeContext.obtainStyledAttributes` 의 defStyleAttr 처리 path (BridgeContext-javap.txt:1879/1926/1959) 가 chain walker 결과 styles map 의 실제 instance 를 사용하는지 + `enforceMaterialTheme=true` resolution 방향 (M2 vs M3) 확인.
  - 출력: 옵션 C 의 enable 여부 (M3 path 가 이미 dispatch 되고 있으면 옵션 C 는 close — 단 다른 widget 에서 M2 fallback 가능성).

각 subagent prompt 에 LM-W3D4-β-G (`unzip --directory /tmp/w3d4db-*`) 명시.

#### 단계 2 — plan v4 작성 (W3D4-δ-B 의 dominant fix 옵션 결정)

subagent 결과를 입력으로 plan-revision (`docs/superpowers/specs/2026-05-XX-w3d4-delta-b-gate-chain-design.md`). 구조 mirror plan v3.2:
- §0 Entry context
- §1 Empirical findings (subagent 출력)
- §2 Root cause + 옵션 A/B/C 의 cost-benefit
- §3 Scope & task split (T20 diagnostic, T21 fix, T22 acceptance close)
- §4-§5 task spec
- §6 Acceptance gate
- §7 round 6 pair-review query (Q1-Q6)

#### 단계 3 — round 6 Codex+Claude 1:1 pair-review (CLAUDE.md §Codex: planning ONLY)

- LM-G 적용: codex 직접 CLI `codex exec --skip-git-repo-check --sandbox danger-full-access` (codex-rescue subagent 미사용; MEMORY.md feedback_codex_sandbox_bypass.md 패턴).
- 양쪽 결과 합산 — full convergence / set divergence / order divergence 별 처리.
- LM-W3D4-δ-A 적용: plan v4 의 모든 코드 sample 식별자 grep/Read 사전 verify (round 5 의 PathLocator/sampleAppModuleRoot fabrication catch 가 trigger).
- 12 deltas 형식 적용 (round 4/5 패턴 동일).
- verdict + reconcile after deltas applied → plan v4.x → GO.

#### 단계 4 — implementation (T20/T21/T22, 각 task 단위 commit + push)

T20 (diagnostic) → T21 (fix) → T22 (acceptance close). T17 의 5-probe regression guard 가 5/5 PASS 보존 필수 — δ-B fix 가 STYLE special-case 회귀를 일으키지 않도록.

T22 acceptance gate close 시:
- δ-A `@Disabled` 갱신 → 제거 (gate close = SUCCESS)
- IT total: 19 PASS + 2 SKIP → **20 PASS + 1 SKIP** (tier3-glyph W4 carry only)
- 모듈 합산 unit: 241 → 245 예상 (+4 W3D4-δ-B regression test 가정)

## §4 회피해야 할 LM (combined — β/γ/δ-planning/δ-implementation)

| LM | 회피 방법 |
|---|---|
| LM-W3D4-β-D | KDoc path 표기 시 `/*` 회피 (backtick 인용) |
| LM-W3D4-β-E | `assertNotNull(...)` 반환 chain 금지 → `val v = ...; assertNotNull(v); v!!.method()` 패턴 |
| LM-W3D4-β-F | IT 실행은 `-PincludeTags=integration` 명시 |
| LM-W3D4-β-G | subagent unzip `--directory /tmp/...` (cwd 회피) |
| LM-W3D4-β-H | acceptance fail 시 stack trace 우선 → fail surface 가 spec layer 인지 새 layer 인지 분류 |
| LM-W3D4-γ-A | reviewer prompt 에 "bundle 의 lookup path trace" 명시 |
| LM-W3D4-γ-B | `Long.decode(...).toInt()` 로 32-bit unsigned hex literal 처리 |
| LM-W3D4-γ-C | ThemeEnforcement multi-sentinel census — δ-A 적용 완료 (7 attr) |
| LM-W3D4-δ-A | spec 코드 sample 의 모든 식별자 grep/Read 검증 의무 |
| LM-W3D4-δ-B | NsBucket type-specific map 분리 = type 별 sibling KILL POINT (ATTR γ T14 + STYLE δ T18) |
| LM-W3D4-δ-C | LayoutlibResourceValueLoader runtime-classpath.txt 부재 silent empty AAR — W4+ hardening |
| LM-W3D4-δ-D | T17 IT-tagged RED-on-main 가능 (default unit suite 제외) |
| LM-W3D4-δ-E | acceptance fail surface 의 stack-trace entry-point 비교로 layer shift 식별 |
| LM-W3D4-δ-F | CLAUDE.md Rule 2 OUT-OF-SCOPE 확대 (phase/task identifier 도 forbidden) — KDoc/annotation 모두 structural-only 영문 |
| LM (CLAUDE.md Three Hard Rules) | 모든 신규 코드 KDoc/inline 영문 only, function/structure only, ticket reference 최소화 |
| LM-G | codex exec sandbox bypass — `--skip-git-repo-check --sandbox danger-full-access` 직접 CLI |

## §5 출발 지점 환경 sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
7462016 docs(w3d4-delta): session-log append for T17/T18/T19 implementation phase
473f55a feat(w3d4-delta): T19 partial — TextAppearance sentinel layer closed via T18, gate-chain surface escalates
22f7077 feat(w3d4-delta): T18 LayoutlibResourceBundle.getResource STYLE special-case (H2 KILL POINT fix)
2ce640a feat(w3d4-delta): T17 theme chain diagnostic battery (5-probe IT)
61c761d docs(w3d4-delta): handoff-delta + next-session-prompt-delta for T17/T18/T19 cold-start

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 241 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain | tail -3
BUILD SUCCESSFUL — 19 IT PASS + 2 SKIP (tier3-basic-primary δ-B carry, tier3-glyph W4 carry).
```

T17 5-probe regression guard:
```bash
$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.resources.W3D4DeltaThemeChainDiagnosticTest" | tail -10
W3D4DeltaThemeChainDiagnosticTest > H1 ... PASSED
W3D4DeltaThemeChainDiagnosticTest > H2 ... PASSED
W3D4DeltaThemeChainDiagnosticTest > H3a ... PASSED
W3D4DeltaThemeChainDiagnosticTest > H3b ... PASSED
W3D4DeltaThemeChainDiagnosticTest > H2-bridgeTypedArray gate ... PASSED
```

bundle shape 진단 (선택, plan v3.2 §5.5):
```bash
$ ./gradlew ... -Daxp.debug.bundleShape=true ...
[LayoutlibResourceValueLoader] bundle shape RES_AUTO styles=$N attrs=$M (byType[STYLE] intentionally empty — STYLE refs route via styles map)
```

## §6 W3D4-δ phase 전체 status

- δ-A (TextAppearance sentinel): **CLOSED** (T18 H2 KILL POINT fix).
- δ-B (gate chain `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant`): **OPEN** — 다음 phase entry.
- ε (R$styleable layer): **carry** — δ-B close 후도 fail 시 §5.3.1 callback instrumentation 으로 narrow.
- tier3-glyph (Font wiring): **W4 carry**.
- DRAWABLE selector XML feed: **별도 phase** (W3D4-β plan v3 §5.4 T12.5).

다음 세션 = W3D4-δ-B planning entry. plan-revision phase → Codex+Claude 1:1 pair-review (CLAUDE.md §Codex: planning ONLY) → plan v4.x GO → T20/T21/T22 implementation.
