# Next-session prompt — W4-B-B (chip TextAppearance NPE) entry

다음 세션의 첫 작업 prompt — paste-ready.

---

W4-B-B 진입 — chip TextAppearance NPE 정면 돌파.

직전 commit 1개 (W4-B-A close: NamespaceAwareValueParser 의 style item body trim — chip stateListAnimator callback bypass 의 root cause = StyleItem body whitespace, layoutlib root form 가설은 reject). 본 세션 첫 작업 = **W4-B-B 진입** (chip 의 `?attr/textAppearanceLabelLarge` chain 이 `MaterialResources.getTextAppearance` 의 `typedArray.getResourceId` 0 반환 → null TextAppearance → NPE on getTextSize).

현 시점 상태 (commit (가장 최근) `feat(w4-b-a): style item body trim — chip stateListAnimator callback now wires correctly`):
- 255 unit PASS / 0 fail (+2: trim contract + string preservation contract)
- 26 IT PASS + 1 SKIP (chip @Disabled, W4-B-B-only message)
- T17 5/5 + T20 5/5 regression guards PASS
- acceptance gate `activity_basic` + `activity_minimal` PASS
- `MaterialFidelityIntegrationTest` 4/4 PASS
- walker stats: `41 AARs (13 with res, 28 code-only, 191 color-state-lists, 32 animator-xmls, 132 drawable-xmls)`

W4-B-B 의 정확한 fail surface (post W4-B-A trim fix):
- `[LayoutlibRenderer] createSession result: status=ERROR_INFLATION msg=Cannot invoke "com.google.android.material.resources.TextAppearance.getTextSize()" because "textAppearance" is null exc=NullPointerException`
- ChipDrawable.loadFromAttributes (offset 165 of bytecode disasm) calls **MaterialResources.getTextAppearance(context, typedArray, index)** → returns null
- MaterialResources.getTextAppearance returns null when `typedArray.hasValue(index)` is false OR `typedArray.getResourceId(index, 0)` returns 0

Cold-read 6 docs (handoff §3.1 list, 이 순서):
1. `docs/work_log/2026-05-08_w4-b-a-trim/handoff.md` (본 prompt 의 source — W4-B-A close + W4-B-B path α/β/γ)
2. `docs/work_log/2026-05-08_w4-b-a-trim/session-log.md` (W4-B-A 의 정확한 file diff + bytecode evidence + dual-source review 결과)
3. `docs/work_log/2026-05-07_w4-candidate-b-chip/handoff.md` (W4-B drawable feed close — 본 phase 의 hypothesis source)
4. `docs/work_log/2026-05-07_w4-candidate-d-hardening/handoff-w4-d-close.md` (W4 candidate 카탈로그)
5. `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (W3D4 phase escalation chain)
6. `docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md` (Codex+Claude pair-review 패턴 reference)

원본 기획안 (필수 cross-read):
- `docs/plan/03-roadmap.md` (master roadmap, historical/superseded by `06 §6` + `08 §1`)
- `docs/plan/04-open-questions-and-risks.md` (risk register R1-R9)
- `docs/MILESTONES.md` (W1-W6 gate 체크포인트)
- `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md` (W3D4 phase design — chain walker + RJarSymbolSeeder reference)

진행:

0. 베이스라인 verify
   - cd server && ./gradlew test --console=plain
     → BUILD SUCCESSFUL — 255 unit / 0 fail
   - ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
     → 26 IT PASS + 1 SKIP (chip @Disabled W4-B-B-only)

1. W4-B-B vs hold — 사용자 priority 결정
   - W4-B-B (strong recommend, chip IT 자동 trigger)
   - hold (W4 candidate C/E 진입)

2. W4-B-B 진입:
   - 임시 probe 추가 (LM-W4-B-E 적용: bytecode + runtime probe 가 plan-writing 의 prerequisite):
     - MinimalLayoutlibCallback.getParser 에 println — basic IT 와 chip IT 의 textAppearance-related 호출 비교 (W3D4-β-H entry-point comparison 적용)
     - `BridgeContext.obtainStyledAttributes` 의 호출 내부 trace 가능 여부 확인 (`-Daxp.debug.callback=true` toggle 활용)
   - layoutlib jar 의 BridgeTypedArray.getResourceId disasm:
     ```
     mkdir -p /tmp/w4bb && cd /tmp/w4bb && \
       unzip -o /home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar \
         'com/android/layoutlib/bridge/android/BridgeTypedArray.class' && \
       javap -p -c BridgeTypedArray.class > BridgeTypedArray-disasm.txt
     ```
     (LM-W3D4-β-G unzip --directory /tmp/w4-* 의무 적용)
   - `R$style.TextAppearance_Material3_LabelLarge` 의 RJarSymbolSeeder seed 결과 검증 — `RNameCanonicalization.styleNameToXml("TextAppearance_Material3_LabelLarge")` → expected `"TextAppearance.Material3.LabelLarge"` (dot-3) — empirical assertion
   - 가능한 fix path (handoff §2.4):
     - (path α) RJarSymbolSeeder dot-name canonicalization 회귀 — `RNameCanonicalization.styleNameToXml` 의 edge case
     - (path β) chain walker 의 STYLE 결과 반환 형태 — `bundle.getStyleByName` → ResourceReference → `callback.byRef` 역방향 lookup
     - (path γ) BridgeContext 의 resolveAttribute 의 RES_AUTO ↔ ANDROID 전이 — Theme.AxpFixture chain 안 RES_AUTO attr `textAppearanceLabelLarge` lookup
   - empirical 결과로 fix path 결정:
     - 작은 fix (1-line) → bytecode evidence + reviewer subagent + commit close
     - 큰 design (TypedArray.getResourceId 의 layoutlib internal contract 변화) → plan v6 design + Round 7 Codex+Claude pair-review (LM-G + LM-W4-D-B 적용 — codex 직접 CLI, model 명시 회피)
   - regression guards 보장: T17 5/5 + T20 5/5 + W4-D require throw guard 7 + drawable feed unit 4+6 + acceptance gate `activity_basic` + `activity_minimal` 모두 PASS
   - chip IT 의 @Disabled 제거 + LM-W4-B-B close

3. 본 phase commit + push (CLAUDE.md task-unit completion)

CLAUDE.md 규약 준수 (brace own-line, no default params, Zero Tolerance for Magic Numbers, comments 영문 only / function-structure only / chronology phrasing 회피 per Three Hard Rules + reviewer Issue 1+2+3 의 적용 사례 참조). carry-forward LM (handoff §4 의 25 항목) 모두 다음 세션 진입 시 내재화:

- LM-W3D4-β-D~H, LM-W3D4-γ-A~C, LM-W3D4-δ-A~I, LM (CLAUDE.md Three Hard Rules), LM-G, LM-W4-D-A/B
- LM-W4-B-A (close ✓ — 본 commit)
- LM-W4-B-B (carry — 다음 phase): chip TextAppearance NPE — `MaterialResources.getTextAppearance` 의 `typedArray.getResourceId` 0 반환. path α/β/γ — empirical 결과로 좁힘
- LM-W4-B-C (carry): subagent 가설 propagation 위험 — file:line read 가 ground truth
- LM-W4-B-D (carry): Kotlin default-arg + trailing-lambda 회귀
- LM-W4-B-E (carry): handoff document 자체도 hypothesis source. plan-writing 전 60-min empirical probe (bytecode disasm + runtime probe). LM-W3D4-δ-A + LM-W4-B-C 의 자연 escalation
- LM-W4-B-F (carry): NamespaceAwareValueParser 의 top-level `<item type=...>` 미적용 trim — 향후 회귀 시 확장

본 phase 의 pair-review 적용:
- 큰 design (path α/β/γ 결정 결과 implementation phase 가 큰 변화) → plan v6 작성 → Round 7 Codex+Claude planning pair-review 필수 (LM-G + LM-W4-D-B — codex 직접 CLI, model 명시 회피, reasoning effort=high). pair-review prompt 에 Q6 open critique slot 명시 + feedback_pair_review_codex_killpoint.md 의 KILL POINT 패턴 적용
- 작은 fix → implementation phase, dual-source single-shot review (Claude reviewer subagent + Codex sanity-check) 가능. 강제 pairing 아님 (CLAUDE.md §Codex)

LM-G + LM-W4-D-B (codex sandbox bypass 직접 CLI, model 명시 회피) — planning phase 진입 시에만 적용. 실 implementation 은 subagent-driven 가능 (LM-W3D4-β-G unzip --directory /tmp/w4-* 의무).

진행 후 candidate close 시 W4 cluster 의 work_log 추가 + 다음 candidate 또는 새 phase 의 handoff/prompt 작성.

에이전트팀 구동하고 각종 스킬들 사용해서 철저하게 분석 하면서 진행해줘.
반드시 원본 기획안 (`docs/plan/03-roadmap.md`,
`docs/plan/04-open-questions-and-risks.md`, `docs/MILESTONES.md`,
`docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md`) 도
읽으면서 진행해야돼. mcp-builder, plugin-dev, mcp-server-dev 스킬은 본
phase 와 무관 — 사용 회피. plan v6 작성 phase 는 codex pair-review (LM-G +
LM-W4-D-B) 가 핵심. 새 세션에서 이어하는게 낫다고 판단되기 전까지 쭈욱
진행해줘. 에이전트팀 구동하고 철저하게 분석 검토하면서 각종 스킬 사용하면서 진행해줘. mcp-builder, plugin-dev 스킬 사용하면 도움 될 수 있어 필요하면 사용해줘
