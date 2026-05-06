# Round 6 pair-review verdict — W3D4-δ-B plan v4 (planning phase)

날짜: 2026-05-06
선행 head: `db17bfe` (main, plan v4 draft 직전)
plan v4 draft: `docs/superpowers/specs/2026-05-06-w3d4-delta-b-gate-chain-design.md`
review pair: Codex (codex-cli 0.128.0, latest GPT, xhigh) + Claude planning subagent

---

## §1 Verdict

| 채널 | verdict | confidence | headline |
|---|---|---|---|
| **Codex** | **REVISE** | 0.84 | Option B is plausible, but plan v4 still contains bytecode/resource contradictions and T20 does not directly prove the BridgeContext `hasValue` gate. |
| **Claude** | **GO_WITH_FIXES** | 0.86 | Option B 는 empirically 정합 (BridgeContext.createStyleBasedTypedArray 가 동일 findItemInTheme 경로 사용) — 단 §4.1 P3/P5 null-safety + §6.3 theme overlay redundancy + §2.2 옵션 A side-effect 과장 정정 필요. |

**Resolution**: 양쪽 reviewer 가 옵션 B dominant 에 수렴. Codex 의 REVISE 는 plan 의 일부 진술 contradictions (Q6 KILLPOINTS) — 직접 verify 후 inline 정정으로 closeable. Judge round 불필요 (memory feedback_pair_review_codex_killpoint.md 패턴: Codex DISAGREE + file:line evidence 시 직접 verify 로 reconcile). 12 deltas 적용 후 plan v4.1 → **GO**.

---

## §2 Convergence map

| Q | Codex | Claude | reconcile |
|---|---|---|---|
| Q1 (BridgeContext path) | createStyleBasedTypedArray (offset 2494-2499) → mRenderResources.findItemInTheme. setupResources (Resources_Theme_Delegate-javap.txt:165-199) caveat. | createStyleBasedTypedArray 동일 path. 별도 layer 아님. | **CONVERGE** — Codex 의 setupResources caveat inline 추가 |
| Q2 (option B → hasValue=true) | Yes — fixture style entry → findItemInStyle → bridgeSetValue → hasValue=true. ResourceData mark via offset 1567-1584. | Yes — direct mechanism trace. | **CONVERGE** ✓ |
| **Q3 (T20 coverage)** | P1-P5 cover bundle/chain layer 만, BridgeContext.internalObtainStyledAttributes hasValue gate 직접 미cover. P6/P7 추가 권고. | P2+P3 = BridgeContext.hasValue=true 동치 (chain walker delegation 검증). | **DIVERGE** — Codex 더 strict, Claude minimal. **resolution**: §4.2 에 P2+P3 ≡ BridgeContext.hasValue path equivalence note + §6.3 의 residual risk 명시 (setupResources / R-id mapping). P6/P7 standalone IT 는 BridgeContext 부트스트랩 비용 > benefit — 추가하지 않음. T22 가 end-to-end probe 역할. |
| Q4 (option A side-effect) | colorPrimaryVariant 직접 reader = ThemeEnforcement.class **유일** (material classes 안 grep). BadgeDrawable 직접 checkMaterialTheme 호출. BaseTransientBottomBar 는 checkAppCompatTheme 직접. | 동일 — Material AAR 안 사용 site 0. plan §2.2 의 "high" side-effect 과장. | **CONVERGE** ✓ — plan §2.2 의 side-effect 평가 정정. |
| Q5 (shadowing) | safe (findItemInTheme first-match, LayoutlibRenderResources.kt:211-219). M2 concrete fallback (3023) 만 bypass — 의미적 동치. | safe — Q4 결과로 정합. | **CONVERGE** ✓ |
| **Q6 (defects)** | **3 KILLPOINTS** (contradictory file:line) + 5 deltas | 5 minor defects + 8 deltas | **PARTIAL CONVERGE** — Codex 3 KILLPOINTS 모두 직접 verify 로 confirmed. union 적용. |

### §2.1 Codex KILLPOINTS — 직접 verify 결과

| KILLPOINT | Codex 인용 | 직접 verify | resolution |
|---|---|---|---|
| Theme.AxpFixture chain 의 isMaterialTheme=true 미존재 진술 vs. Lvl 9 inheritance | values.xml:3020-3023 | ✓ Lvl 9 (Base.V14.Theme.MaterialComponents.Light.Bridge) 의 isMaterialTheme=true 정의 정확. plan 의 §2.1 step 8 진술이 *fixture-direct* miss 와 *chain-walker reachability* 미구분. | §2.1 step 8 정정: "fixture 자체에 직접 정의 없음. chain walker 의 inheritance 측 reachability 는 별도 측정 필요 (T17 가 H1 reachable 만 측정, isMaterialTheme attr 미측정). 실 fail 의 기제는 BridgeContext layer 에서의 path 이며 P2/P6 측정 대상." |
| Snackbar/BadgeDrawable/ChipGroup 직접 colorPrimaryVariant 사용 진술 vs. 실 grep 결과 | classes-strings.txt:2982 (colorPrimaryVariant 만 ThemeEnforcement.class) | ✓ 직접 사용 site 0. BadgeDrawable 가 *checkMaterialTheme 직접* 호출 (다른 path). | §2.2 옵션 A side-effect 정정: "high (silent unresolved Material widgets)" → "medium-low (gate skip semantic — ThemeEnforcement.class 외 colorPrimaryVariant 직접 reader 부재). 단, 옵션 A 가 BadgeDrawable 의 checkMaterialTheme 직접 호출도 동일하게 우회 — option B 의 'gate PASS vs SKIP semantic' rationale 유지." |
| M3 path 가 enforceMaterialTheme=false 로 SKIP 진술 vs. Widget.Material3.Button 의 inheritance | values.xml:5238-5239 + 6348-6349 | ✓ Widget.Material3.Button 본체 (5238-5257) 안 enforceMaterialTheme override 부재 — 부모 Widget.MaterialComponents.Button:6349 의 true 상속. | 본 stale 진술은 plan 자체에 없고, 현 main 의 LayoutlibRendererIntegrationTest.kt 의 @Disabled message 안에 잘못 작성됨. plan v4 §6.1 의 T22 가 본 @Disabled 제거하므로 자동 close. (단 본 @Disabled 의 wording 자체가 잘못이었음을 LM-W3D4-δ-G 로 catalog.) |

### §2.2 가장 강한 convergence

Q4 — colorPrimaryVariant 의 직접 reader = ThemeEnforcement only — 양쪽 grep 결과 동일. 옵션 A 의 side-effect cost 가 plan 작성 시 과장됨 (이전 phase 의 carry-over wording). 옵션 B 의 dominance 는 *gate semantic strictness* 로 재정렬 (gate skip vs gate PASS).

### §2.3 가장 강한 divergence

Q3 — T20 의 BridgeContext-side coverage. Codex 의 strict view (P6/P7 추가) vs. Claude 의 equivalence view (P2+P3 ≡ hasValue path). Codex 의 file:line trace 가 더 자세 — `Resources_Theme_Delegate.setupResources` (offset 165-199) 가 활성 RenderResources stack 을 mutate 가능. 단 P6/P7 standalone IT 가 BridgeContext 부트스트랩 비용 > benefit — T22 가 end-to-end probe 역할 + §6.3 의 residual risk 명시로 reconcile.

---

## §3 Adopted plan deltas (12 — 모두 inline 적용)

1. **§2.1 step 8** — fixture-direct miss vs. inheritance reachability 구분. T17 의 isMaterialTheme attr 미측정 명시.
2. **§4.2 P2/P3 footnote** — Lvl 5:2214 의 ?attr/colorPrimary inheritance 로 chain walker 가 P2 PASS 가능성. T20 측정이 결정적. **P2+P3 ≡ BridgeContext.hasValue path equivalence note** 추가 (Q1 의 createStyleBasedTypedArray:2494-2499 trace 인용). residual risk = setupResources / R-id mapping (T22 + §6.3 escalation).
3. **§2.2 옵션 A side-effect** — "high" → "medium-low". colorPrimaryVariant 직접 reader = ThemeEnforcement only (Codex Q4 + Claude Q4 grep evidence). 옵션 B dominance rationale = 'gate PASS vs SKIP semantic' 로 재정렬.
4. **§4.1 P5 null-safety** — `assertNotNull(raw)` 한 줄 추가 (Claude Q6 catch).
5. **§6.3 theme overlay layer** — §11 out-of-scope 와 중복 → 제거 (Claude Q6 catch).
6. **§1.3 inheritance 명시** — Lvl 5:2141-2142 의 isMaterial3Theme=true (≠ isMaterialTheme), Lvl 9:3021 의 isMaterialTheme=true 양쪽 명시 census table 에 추가.
7. **§2.4 옵션 C cost-benefit** — Widget.Material3.Button 가 enforceMaterialTheme=true *상속* (override 부재) — Codex 인용한 values.xml:5238-5239 + 6348-6349 file:line 추가.
8. **§6.1 stale text** — 본 plan diff 의 @Disabled 제거 자체가 stale wording 정리. **LM-W3D4-δ-G** 신규 — annotation message 작성 시 future option 의 *technical accuracy* 검증 의무.
9. **§9 test impact** — P6/P7 미추가 (Q3 reconcile) → IT count 변동 없음 (5-probe). T22 가 BridgeContext-side acceptance probe.
10. **§8 LM-W3D4-δ-A self-check** — "✓" 의 evidence citation 추가 (Codex 권고): `LayoutlibResourceValueLoader.Args` (loader.kt:17-21), `clearCache` (loader.kt:30), `loadOrGet` (loader.kt:25), `bundle.getResource` (bundle.kt:50-58 post-T18), `findItemInTheme` (renderResources.kt:211), `resolveResValue` (renderResources.kt:144).
11. **§7 Q3** — round 6 reconcile inline. BridgeContext-side equivalence note + Codex 의 setupResources caveat 추가.
12. **§11 out-of-scope** — direct-sentinel widget surface (BadgeDrawable 직접 checkMaterialTheme 호출 evidence with file:line `/tmp/w3d4db-codex-BadgeDrawable-javap-v.txt:1418-1423`) — Codex Q4 가 found.

---

## §4 LM 준수 검증

- **LM-G**: codex 직접 CLI `codex exec --skip-git-repo-check --sandbox danger-full-access` (codex-rescue subagent 미사용) ✓
- **LM-α-A**: Codex 모든 claim file:line 인용 (50+ citations including BridgeContext-javap, te_disasm, values.xml, repo source). Claude 도 file:line 인용 (40+ citations). ✓
- **LM-α-B**: dual-channel verdict (Codex REVISE + Claude GO_WITH_FIXES), single-source 회피 ✓
- **LM-W3D4-D**: 모든 fix explicit diff (12 deltas inline). fabricated `TODO("...")` 0건 ✓
- **LM-W3D4-β-G**: subagent + codex 모두 `unzip -d /tmp/w3d4db-...` 명시. 검증: `/tmp/w3d4db-codex-*.txt` 산출 (BadgeDrawable, BaseTransientBottomBar, ThemeEnforcement, Resources_Theme_Delegate javap) — cwd 미오염 ✓
- **LM-W3D4-γ-A**: reviewer prompt 에 "BridgeContext.obtainStyledAttributes(int[]) 의 hasValue path trace" 명시 — 양쪽 reviewer 가 createStyleBasedTypedArray:2494-2499 동일 path 도달 (convergence Q1) ✓
- **LM-W3D4-δ-A**: 모든 §4.1 식별자 verify ✓ (양쪽 reviewer 의 self-check 동일 결론)
- **LM-W3D4-δ-B**: NsBucket type-specific 분리 — T20 P4 이 T18 의 STYLE special-case 회귀 가드 ✓
- **LM-W3D4-δ-E**: stack-trace entry-point 비교 — Codex Q1 의 trace 가 본 LM 적용한 review (BridgeContext.obtainStyledAttributes 의 entry-point 정확 식별) ✓
- **LM-W3D4-δ-F**: CLAUDE.md Rule 2 OUT-OF-SCOPE 확대 — plan v4 의 신규 코드 sample KDoc/annotation structural-only 영문 ✓ (양쪽 reviewer self-check)
- **LM-W3D4-δ-G (신규)**: annotation message (e.g. `@Disabled` reason) 안 future option 의 technical claim 작성 시 file:line evidence 사전 verify 의무. round 6 의 stale text catch (Widget.Material3.Button 의 enforceMaterialTheme=false 잘못 진술 — 실제로는 true 상속) 가 trigger.

---

## §5 최종 verdict

REVISE → APPLIED (12/12 deltas inline 적용 완료). plan v4 → **plan v4.1** → **GO** (post-revision). T20/T21/T22 구현 진입 승인.

다음 phase: T20 (W3D4DeltaBGateChainProbeTest 신규) → T21 (fixture themes.xml 1-line) → T22 (acceptance gate close). 각 task 단위 commit + push (CLAUDE.md task-unit completion).
