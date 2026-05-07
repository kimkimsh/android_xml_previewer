# Next session entry — W4-B-A (chip stateListAnimator callback bypass)

본 세션 첫 작업 = **W4-B-A 진입** (chip stateListAnimator path 의 callback bypass mechanism 추적 + plan v6 design 작성 + Round 7 Codex+Claude pair-review). OR user priority 에 따라 W4-B-B 단독 (fixture-side stateListAnimator override 로 우회) 또는 W4-C/E (hold).

직전 session commit 1 개:
- `feat(w4-b): drawable XML feed mirror — candidate A natural trigger close + chip IT parked` — drawable XML feed (W3D4-δ-D animator pattern 의 1:1 mirror) 구현. 6 main + 8 test + 1 fixture + 2 doc 변경. chip IT @Disabled park (LM-W4-B-A + LM-W4-B-B carry).

본 세션 첫 작업 = W4-B-A 진입 — chip stateListAnimator path 의 callback bypass mechanism 추적 + plan v6 design + Round 7 pair-review.

현 상태:
- 253 unit PASS / 0 fail
- 26 IT PASS + 1 SKIP (tier3 chip — @Disabled, LM-W4-B-A reference)
- T17 5/5 + T20 5/5 regression guards PASS
- acceptance gate `activity_basic` + `activity_minimal` PASS
- walker stats: `41 AARs (13 with res, 28 code-only, 191 color-state-lists, 32 animator-xmls, 132 drawable-xmls)`

W4-B-A 의 정확한 fail surface (handoff §2.1 상세):
- chip 의 inflation 이 `View.<init>:6033 → AnimatorInflater.loadStateListAnimator(context, id) → createStateListAnimatorFromXml(parser)` path 에서 fail
- `parser.next()` throw `XmlPullParserException: No Input specified (position:START_DOCUMENT null@0:0)`
- m3_chip_state_list_anim 이 bundle 에 등록되어 있음 (debugListAnimators probe 로 verify)
- 동일 path 의 m3_btn_state_list_anim 은 정상 working — basic IT PASS, getParser type=animator 정상 호출됨
- 차이점: chip animator XML 의 root 가 `<selector>` (직접 state list animator), button 은 `<set>` 안 `<selector>` (래핑)
- 가설: layoutlib `Resources_Delegate.getAnimation_Original` 가 root form 에 따라 callback hook bypass

Cold-read 6 docs (handoff.md §3.1 list):
1. docs/work_log/2026-05-07_w4-candidate-b-chip/handoff.md (본 prompt 의 source — W4-B 의 close summary + W4-B-A/B carry)
2. docs/work_log/2026-05-07_w4-candidate-b-chip/session-log.md (W4-B drawable feed 의 정확한 file diff + dual-source review 결과 + LM 산출)
3. docs/work_log/2026-05-07_w4-candidate-d-hardening/handoff-w4-d-close.md (W4-D 의 close + W4 candidate 카탈로그)
4. docs/work_log/2026-05-06_w4-entry/handoff-w4-entry.md (W4 entry candidate A/B/C/D/E spec)
5. docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md (W3D4 phase escalation chain — background)
6. docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md (Round 7 Codex+Claude pair-review 패턴 reference)

진행:

0. 베이스라인 verify
   - cd server && ./gradlew test --console=plain
     → BUILD SUCCESSFUL — 253 unit / 0 fail
   - ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
     → 26 IT PASS + 1 SKIP

1. W4-B-A vs W4-B-B vs hold — user priority 결정
   - W4-B-A (strong recommend, 정면 돌파, plan v6 + Round 7 pair-review)
   - W4-B-B 단독 (fixture-side override 로 우회, 단 LM carry)
   - hold (다음 candidate 결정)

2. W4-B-A 진입 (path 1 권장):
   - layoutlib jar dump: `javap -p -c $LAYOUTLIB_JAR ResourcesImpl_Delegate.class Resources_Delegate.class > /tmp/w4ba-resources-delegate.txt` (또는 비슷한 layoutlib 의 Resources delegate 클래스)
   - `getAnimation_Original` (또는 비슷한) 의 정확한 implementation 추적 — `<selector>` root 의 callback hook 우회 메커니즘 식별
   - 가능한 fix path:
     - (A) layoutlib 패치 — 비현실적
     - (B) Worker-side ResourceType.STATE_LIST_ANIMATOR 별도 hook (단 enum 미존재 — 다른 dispatch 필요)
     - (C) Pre-process `<selector>` root → `<set>` wrap (walker-side 또는 BridgeXmlBlockParser wrapper)
   - plan v6 design 작성 (`docs/superpowers/specs/2026-05-XX-w4-b-stateListAnimator.md`):
     - §1 분석 (file:line 인용 — LM-W3D4-δ-A 의무)
     - §2 fix path 비교 (A/B/C trade-off)
     - §3 12-step transition plan
     - §4 regression risk
   - Round 7 Codex+Claude planning pair-review (LM-G + LM-W4-D-B 적용 — codex 직접 CLI, model 명시 회피):
     ```bash
     codex exec --skip-git-repo-check --sandbox danger-full-access \
       -c model_reasoning_effort=high \
       'Codex+Claude Round 7 plan-review prompt 본문...'
     ```
   - deltas inline → plan v6.x → GO
   - Implementation: 결정된 fix path 적용
   - regression guards: T17 5/5 + T20 5/5 + W4-D require throw guard 7 cases + drawable feed unit tests 4+6 + acceptance gate `activity_basic` + `activity_minimal` 모두 PASS 보장
   - chip IT @Disabled 제거 + LM-W4-B-A close
   - W4-B-B 자연 surface advance 시 그 phase 진입 OR 별도 next-session 으로 carry

3. 본 phase commit + push (CLAUDE.md task-unit completion)

CLAUDE.md 규약 준수 (brace own-line, no default params, Zero Tolerance, comments 영문 only / function-structure only / chronology phrasing 회피).

진행 후 candidate close 시 W4 cluster 의 work_log append + 다음 candidate handoff/prompt.

LM-G + LM-W4-D-B (codex sandbox bypass 직접 CLI, model 명시 회피) — planning phase 진입 시에만 적용. 실 implementation 은 subagent-driven 가능 (LM-W3D4-β-G unzip --directory /tmp/w4-* 의무).

LM-W4-B-C (subagent hypothesis propagation 위험) — Round 7 pair-review 의 subagent / Codex 결론은 hypothesis, file:line read 가 ground truth. 모든 결론은 직접 verify 후 plan 반영.

LM-W4-B-D (Kotlin default-arg + trailing-lambda 호환성) — 신규 helper function 추가 시 default-arg 위치 검토 의무. 두 form 분리 권장.

에이전트팀 구동하고 각종 스킬들 사용해서 철저하게 분석 하면서 진행해줘. 반드시 원본 기획안 (`docs/plan/03-roadmap.md`, `docs/plan/04-open-questions-and-risks.md`, `docs/MILESTONES.md`, `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md`) 도 읽으면서 진행해야돼. mcp-builder, plugin-dev, mcp-server-dev 스킬은 본 phase 와 무관 — 사용 회피. plan v6 작성 phase 는 codex pair-review (LM-G) 가 핵심. 새 세션에서 이어하는게 낫다고 판단되기 전까지 쭈욱 진행해줘.
