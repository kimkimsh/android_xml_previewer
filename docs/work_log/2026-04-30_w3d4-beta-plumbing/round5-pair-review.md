# W3D4-δ Round 5 Pair Review (Codex + Claude)

날짜: 2026-05-04 (생성), 2026-05-04 (Codex 완료 — xhigh effort)
대상: `docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md` (plan v3.2 draft)
phase: planning / plan-revision (Codex pairing 정책 trigger 정확히 적용 — pre-implementation, ThemeEnforcement multi-sentinel surface + bundle.getResource(STYLE) byType-only KILL POINT 후보)
LM-G 회피: `codex exec --skip-git-repo-check --sandbox danger-full-access` (MEMORY.md feedback 준수, codex-rescue subagent 미사용)

---

## §1 디스패치 채널

| 채널 | 모델 / 모드 | 산출물 |
|---|---|---|
| Codex CLI (직접) | latest GPT @ xhigh effort, sandbox bypass | `/tmp/codex-round5-review.txt` (~46 lines structured) + `/tmp/codex-round5-stdout.txt` (~8390 lines, javap evidence dump 포함) |
| Claude Plan agent | claude internal | inline 결과 (~2300 words, structured Q1-Q6) |

각 채널은 서로의 산출물을 보지 못한 상태에서 동일 Q1-Q5 + Q6 open critique 에 답함 — convergence 자체가 신뢰 신호.

## §2 Verdict

| 채널 | 판정 | confidence |
|---|---|---|
| Codex | GO_WITH_FIXES | 0.91 |
| Claude (Plan agent) | GO_WITH_FIXES | 0.88 |

**최종 reconciled verdict: GO** — 12 plan delta inline 적용 완료. 양 reviewer 의 verdict 가 동일 (GO_WITH_FIXES) + Q4 양쪽 DISAGREE 의 file:line evidence (memory `feedback_pair_review_codex_killpoint.md` 패턴) 가 fabricated API references 를 정확히 catch — judge round 불요, 직접 코드 read 로 verify 후 fix 적용.

## §3 Convergence map

| Q | Codex | Claude | 종합 |
|---|---|---|---|
| Q1 (H2 KILL POINT 정확성) | CORRECT — H2 real, BridgeContext defStyleAttr path uses findItemInTheme + resolveResValue (NOT direct getStyle). LayoutlibResourceBundle.kt:50/58 byType-only fallback + buildBucket:201/223 stylesMut-only writes 검증 + BridgeContext-javap.txt:1879/1926/1959 instance check 검증. | NUANCED — 동일 결론, 단 §1.3 의 "발견 못 하거나 nullify" disjunctive 표현이 fail mode 두 개를 conflate. | **CORRECT** (Codex empirical 우선, Claude 의 §1.3 정확화 delta 도 inline) |
| Q2 (Lvl 5 reachability) | NUANCED — Lvl 2→Lvl 3 의 dot-trim edge 정확. 그러나 spec §1.2 의 "all alias-only" 표현이 over-claim — `StyleParentInference` 가 nonexistent parent 도 blind 으로 infer (buildBucket inSet 미강제). Material3 chain 한정 narrow 권장. | CORRECT — Lvl 2 만 dot-trim 의존, 나머지 explicit; MaterialFidelityIntegrationTest:56-59 의 chain depth ≥15 가 green 으로 검증. | **NUANCED** (Codex 의 narrow framing inline + Claude 의 chain-depth IT 검증 결합) |
| Q3 (T17 4-question battery 충분성) | NUANCED — T17 가 resource-layer (bundle / chain walker) narrowing 에 충분. 단 layoutlib BridgeContext callback (resolveResourceId / getOrGenerateResourceId / R$styleable) layer 미커버. all-green 후 fail 시 instrumentation 필요. | NUANCED — 동일 결론. 5th probe (`resolveResValue(StyleItemResourceValueImpl)` returns is StyleResourceValue) 추가 권장 — BridgeTypedArray.getResourceId offset 28 instanceof gate 사전 검증. | **NUANCED — 양 측 권고 결합** (Codex 의 §5.3.1 BridgeContext post-trigger instrumentation + Claude 의 §4.2 5th probe 둘 다 inline) |
| **Q4 (T17 production-input 의존성)** | **DISAGREE** — `Args.sampleAppRoot` 가 정답 (LayoutlibResourceValueLoader.kt:17/21), spec 의 `sampleAppModuleRoot` 는 fabricated. fixture runtime classpath 가 `:app:assembleDebug` finalizer 의존 — 미build 시 silent empty AAR list (false diagnostic 위험). `@Tag("integration")` 자체 contradiction. | **DISAGREE** — 동일 catch: `PathLocator.locateW3D4Triplet()` 도 fabricated, `MaterialFidelityIntegrationTest.locate()` 가 실 helper 이고 private/`Pair<Path, Path>?`. KDoc "NOT integration-tagged" 와 `@Tag("integration")` 충돌. compile fail 확정. | **DISAGREE — 양 reviewer 직접 verify 의 KILL POINT** (memory feedback_pair_review_codex_killpoint.md 패턴). 즉시 §4.1/§4.3 fix — `MaterialFidelityIntegrationTest.locate()` 패턴 mirror, 필드명 `sampleAppRoot`, IT-tag + KDoc 정합, PathLocator 추출 폐기. round 5 의 dominant single-largest delta. |
| Q5 (H2 fix coverage) | NUANCED — H2 가 M3 path (BridgeContext findItemInTheme + resolveResValue + instanceof gate) AND M2 path (BridgeTypedArray.getResourceId offset 28-46 + getDynamicIdByStyle) 양쪽 enable, 단 두 path 모두 item 발견 후의 STYLE-ref hop resolution 단계만 cover (item discovery 자체 fail 시 H2 fix 무관). | CORRECT — 동일 결론, BridgeContext offset 312-325 (M3 instanceof gate) + BridgeTypedArray offset 28-46 (M2 instanceof gate) 양쪽 cite. | **NUANCED — Codex 의 conditional framing 이 더 정확** (Claude 의 양 path coverage + Codex 의 "item discovery 후의 STYLE hop" 한정). §5.1 의 코멘트 갱신 inline. |
| Q6 (open critique) | NUANCED — multi-defect: KDoc/Tag self-contradiction, `sampleAppModuleRoot` 오기, `PathLocator` TODO, `DEBUG_STYLE_MISS` 미정의, §6.3 `enforceMaterialTheme` 누락. | NUANCED — 동일 defect 리스트 + 추가: §5.6 H1+H3 partial-FAIL row 누락, T17 red-on-main vs entry criterion 충돌, KDoc 일부 Korean prose. | **NUANCED — 양 측 deficit list 의 union 적용** (12 deltas inline). |

**가장 강한 convergence**: Q4 (양쪽 DISAGREE 의 동일 evidence — `Args.sampleAppRoot` + `PathLocator` + `@Tag` 모순) + Q6 (defect list 의 8개 항목 중 6개 동일).
**가장 강한 divergence**: 부재 (Q1 의 CORRECT vs NUANCED 도 결론 동일, framing 만 차이).

→ judge round 불요 (memory feedback_pair_review_codex_killpoint.md 패턴 정확 적용 — Codex DISAGREE + file:line evidence + Claude 동일 DISAGREE + file:line evidence = direct verify, judge skip).

## §4 Adopted plan deltas (12)

모두 plan v3.2 inline 적용 완료 (§12.2 참조).

1. **§1.3 fail mode 정확화** (Q1 NUANCED Claude, Codex Q1 delta 보강): "발견 못 하거나 nullify" disjunction 을 "Lvl 5:2273 의 item 발견 후 후속 chain 의 `resolveResValue("@style/Widget.Material3.Button")` 가 unresolved StyleItem 반환 (LayoutlibResourceBundle.kt:57 의 byType-only 경로) → BridgeContext offset 312-325 instanceof gate fail → defStyleRes M2 fallback" 로 명시.

2. **§1.2 / §2.1 dot-parent claim 좁히기** (Codex Q2 NUANCED): "all alias-only style 의 dot-trim 검증" 일반화를 `Theme.Material3.Light.NoActionBar → Theme.Material3.Light` 단일 edge 의 검증으로 narrow. spec 본문 phrasing 정정.

3. **§4.1 fabricated API 제거 + 실 helper 패턴 mirror** (Q4 DISAGREE 양쪽, KILL POINT): `PathLocator.locateW3D4Triplet()` 호출 폐기, `MaterialFidelityIntegrationTest.locate()` 의 `Pair<Path, Path>?` 패턴 직접 mirror (test-internal helper). `Args.sampleAppRoot` 필드명 정정 (NOT `sampleAppModuleRoot`). `dist.resolve(ResourceLoaderConstants.DATA_DIR)` 패턴 적용.

4. **§4.1 KDoc + `@Tag` 정합** (Q4/Q6 양쪽): "NOT integration-tagged" KDoc 제거. `@Tag("integration")` 유지 (real fixture 필요 — H1/H3a/H3b probe 가 production AAR data 의존). §9 의 unit/IT count 도 정정 — T17 은 IT suite 안에서만 실행되어 unit gate 영향 없음.

5. **§4.2 5th probe 추가** (Claude Q3 delta): `resolveResValue(StyleItemResourceValueImpl(..., value="@style/Widget.Material3.Button"))` returns `is StyleResourceValue == true` — BridgeTypedArray.getResourceId offset 28 instanceof gate 의 post-fix 사전 검증. T17 의 4-question 을 5-question 으로 확장.

6. **§4.3 PathLocator 추출 폐기** (Q4 양쪽): 본 절 전체 삭제. T17 가 `MaterialFidelityIntegrationTest.locate()` 와 동일 패턴을 자체 private 으로 보유 — extraction 의 indirection cost > saving.

7. **§5.1 BridgeContext split + bytecode-offset 코멘트** (Q1 Codex delta + Q5 Claude delta): H2 fix 의 KDoc 에 명시 — "BridgeContext defStyleAttr path: findItemInTheme + resolveResValue (offset 1879-1959, instanceof gate at offset 312-325) — H2 enable. defStyleRes fallback uses getStyle (offset 396+) — already works via bundle.getStyleExact, H2 unrelated. M2 path: BridgeTypedArray.getResourceId offset 28-46 instanceof + getDynamicIdByStyle — H2 enable."

8. **§5.1/§5.6 conditional framing 보강** (Q5 Codex NUANCED): H2 fix 의 coverage 가 "M3 materialButtonStyle + M2 textAppearanceButton 의 STYLE-ref hop resolution 단계 *conditional on item discovery success*" 임을 명시. item discovery (findItemInTheme) 자체 fail 시 H2 fix 무관 — §5.6 의 결정 매트릭스에 H1/H3a/H3b FAIL 시 §5.2 / §5.3 우선 적용 row 추가.

9. **§5.3.1 신규 — post-T17-all-green BridgeContext instrumentation** (Codex Q3 delta): T17 4-question 모두 PASS + T19 fail 시 적용할 instrumentation. `MinimalLayoutlibCallback.getOrGenerateResourceId/resolveResourceId` 에 system property gated logger 추가, materialButtonStyle / Widget.Material3.Button / textAppearanceButton / TextAppearance.MaterialComponents.Button 4개 ref 의 callback layer 도달 측정. R$styleable seeder gap (RJarSymbolSeeder.kt:64-66 skip) 의 후속 진단 도구.

10. **§5.5 DEBUG_STYLE_MISS branch 제거** (Q6 양쪽): 미정의 system property gate 폐기. 대신 LayoutlibResourceValueLoader bootstrap 의 one-shot summary log (cold-start 진단 print 옆에 `styles=N attrs=M byType[STYLE]=K` count) 로 진단 가시성 확보 — per-call hot-path log 회피 (Claude Q6 #3 권고).

11. **§5.6 H1/H3 partial-FAIL row 추가** (Claude Q6 #4): "H1 PASS, H2 PASS, H3a OR H3b FAIL" → §5.2 walkParent fallback 적용. 결정 매트릭스를 conjunctive 가 아닌 row-별 disjunctive 로 명시.

12. **§6.3 escalation 확장** (Q6 양쪽): `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant` gate chain 명시 (Widget.MaterialComponents.Button:6349 의 enforceMaterialTheme=true). 추가로 `BadgeDrawable` / `BaseTransientBottomBar` 의 직접 sentinel 호출 (§1.1 line 72) 도 future fixture 진입 시 surface candidate 로 명시.

## §5 Escalation notes (3)

본 round 의 12 deltas 모두 plan v3.2 inline 처리. 단 다음 3 항목은 round 6 또는 future phase 에 carry:

1. **R$styleable layer gap (W3D4-ε)** — RJarSymbolSeeder.kt:64-66 의 R$styleable skip 정책 (W3D4 round 2 A2 결정). H2 fix 후도 tier3-basic-primary fail 시 styled-attrs ID resolution 의 layoutlib-side path 가 후속 surface — `checkAppCompatTheme` / `checkMaterialTheme` 가 현 fix scope 외이지만 *동일 BridgeTypedArray.getResourceId* 경로.

2. **MaterialFidelity fixture-manifest softness** (Codex 추가): `LayoutlibResourceValueLoader` 가 `runtime-classpath.txt` 부재 시 silent empty AAR list 사용 (LayoutlibResourceValueLoader.kt:42-44). 미상태에서 진단 false-pass 위험 — T17 의 hard-require 가 본 plan 에 포함되지만, 본 layer 자체는 W4+ scope 의 hardening pass 권장.

3. **Macro reference probe** (Claude Q6 #c): Widget.Material3.Button:5247 의 `@macro/m3_comp_filled_button_label_text_type`. T17 에 `parseReference("@macro/...")` 의 graceful null 검증 1줄 추가 가능 — round 5 plan inline 으로 채택 (§4.2 의 5번째가 6번째가 됨, 본 escalation 에서 5+1 추가 검토).

## §6 Divergence + reconcile rationale

가장 큰 divergence = Q5 (Codex NUANCED, Claude CORRECT) 와 Q1 (Codex CORRECT, Claude NUANCED) — 둘 다 결론 동일 (H2 진짜, 양 path enable). Codex 의 conditional framing ("item discovery 후의 STYLE hop 만") 이 Claude 의 categorical framing ("양 path coverage") 보다 더 정확 — Codex 가 BridgeContext bytecode 직접 trace 한 결과의 정밀도. 양쪽 verdict 가 GO_WITH_FIXES 로 align.

Q4 (양쪽 DISAGREE) 는 spec 작성자 (Claude main agent) 의 fabrication — `PathLocator` 와 `sampleAppModuleRoot` 둘 다 실 코드에 부재. Codex + Claude Plan 양쪽이 독립적으로 catch + 동일 file:line evidence 제시 — memory `feedback_pair_review_codex_killpoint.md` 의 KILL POINT 패턴 정확 실현. judge round 불요 (양쪽 동일 DISAGREE = single-source 가 아닌 dual-channel 검증 = 직접 fix 의 신뢰성 충분).

Q6 (양쪽 NUANCED) 의 defect list 가 6개 동일 + 각자 2-3개 추가. union 으로 inline 적용.

→ Codex + Claude 의 evidence-based critique 가 round 5 의 12 deltas 모두 충분한 근거. plan v3.2 의 round 6 escalation 부재 (3 escalation note 는 implementation phase 또는 W4+ scope).

## §7 LM 적용

- LM-G: `codex exec --skip-git-repo-check --sandbox danger-full-access` 직접 CLI ✓
- LM-α-A: Codex 모든 claim file:line 인용 (40+ citations 본 round 5 stdout), Claude 도 file:line 사용 (35+ citations) ✓
- LM-α-B: dual-channel verdict, single-source 회피 (Claude Plan + Codex CLI 둘 다 분석) ✓
- LM-W3D4-D: plan 의 모든 fix 가 explicit diff (round 5 의 모든 12 deltas 도 동일) ✓
- LM-W3D4-β-D~H: round 5 reviewer 가 명시 검증 (Q6 inline)
- LM-W3D4-γ-A~C: round 5 reviewer prompt 가 "bundle 의 byType ↔ styles ↔ attrs 분리 trace" 명시 — Claude Plan 의 Q1 분석에서 Codex 와 동일 detection 도달 ✓
- **LM (CLAUDE.md 신규 Three Hard Rules)**: spec 본문은 Korean prose (markdown design doc). 단 신규 코드 sample 의 KDoc / inline comment 는 영문 only, function/structure 만 — Q6 Claude #7 의 borderline case 도 round 5 reconcile 시 정정 ✓

## §8 다음 단계

T17 → T18 → T19 순으로 subagent-driven implementation 또는 직접 implementation. 각 task 단위 commit + push (CLAUDE.md task-unit completion). 각 단계 완료 후 모듈 합산 test 재측정.

특히 T17 commit 은 round 5 의 critical Q4 fix (`Args.sampleAppRoot` + `MaterialFidelityIntegrationTest.locate()` mirror) 를 첫 실 테스트 — 회귀 가드는 W3D4DeltaThemeChainDiagnosticTest 자체가 PASS/FAIL 로 어느 hypothesis 가 confirmed 인지 즉시 narrow.

T18 의 dominant 적용 (H2 §5.1 STYLE special-case) 은 W3D4-γ T14 의 round 4 Q5 KILL POINT fix (ATTR special-case) 와 1:1 sibling — 동일 자료구조 분리에서 sibling type 의 동일 fix.

## §9 산출물

- `/tmp/codex-round5-review.txt` (~46 lines, structured Q1-Q6 + 9 deltas + escalation)
- `/tmp/codex-round5-stdout.txt` (~8390 lines, javap/zipgrep raw evidence — 보존 안 함, 본 review 의 §3 convergence map 으로 요약. file paths 인용은 본 review 가 정합)
- 본 doc + spec (plan v3.2) 의 §12 (round 5 reconcile 요약 — plan 본문 inline)
- `/tmp/w3d4d-{themeenforcement,themechain,widgets}/` (LM-W3D4-β-G 회피 — cwd 외 unzip)
