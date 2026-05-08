# Next-session prompt — W4-B-C (chip drawable abc_vector_test lookup) entry

다음 세션의 첫 작업 prompt — paste-ready.

---

W4-B-C 진입 — chip drawable abc_vector_test lookup callback 우회 정면 돌파.

직전 commit 2개 (W4-B-A close: NamespaceAwareValueParser 의 style item body trim — chip stateListAnimator callback bypass 의 root cause = StyleItem body whitespace; W4-B-B close: NamespaceAwareValueParser 의 `<macro>` element 파싱 추가 — chip TextAppearance NPE 의 root cause = `<macro>` element 미파싱, handoff 의 path α/β/γ 모두 reject). 본 세션 첫 작업 = **W4-B-C 진입** (chip 의 `R.drawable.abc_vector_test` (resource id 0x7F070076, appcompat-resources-1.6.1.aar 안 res/drawable/abc_vector_test.xml) lookup 이 layoutlib `Resources_Delegate.getDrawable` 의 자체 resolution path 에서 callback 우회 + NotFoundException).

현 시점 상태 (commit (가장 최근) `feat(w4-b-b): macro element parsing — chip textAppearance via @macro/... indirection now resolves`):
- 256 unit PASS / 0 fail (+1: macro contract test)
- 26 IT PASS + 1 SKIP (chip @Disabled, W4-B-C-only message)
- T17 5/5 + T20 5/5 regression guards PASS
- acceptance gate `activity_basic` + `activity_minimal` PASS
- `MaterialFidelityIntegrationTest` 4/4 PASS
- `NamespaceAwareValueParserTest` 15 cases (style item trim + string preservation + macro contract)

W4-B-C 의 정확한 fail surface (post W4-B-B macro fix):
- `[LayoutlibRenderer] createSession result: status=ERROR_INFLATION msg=Could not find drawable resource matching value 0x7F070076 (resolved name: abc_vector_test) in current configuration. exc=NotFoundException`
- empirical probe (MinimalLayoutlibCallback.getParser DRAWABLE 분기) 가 abc_vector_test 호출 0 건 confirm — layoutlib 의 자체 resolution path 가 callback 우회

abc_vector_test 의 위치 + ID:
- AAR: `appcompat-resources-1.6.1.aar`
- Path 안 AAR: `res/drawable/abc_vector_test.xml` (1028 bytes, default qualifier)
- R.txt: `int drawable abc_vector_test 0x7f070076`
- 사용 site: ChipDrawable inflation 의 어떤 step (defStyleAttr/Res 또는 그 chain 안 default drawable) 이 본 drawable 을 reference

Cold-read 6 docs (handoff §3.1 list, 이 순서):
1. `docs/work_log/2026-05-08_w4-b-b-macro/handoff.md` (본 prompt 의 source — W4-B-B close + W4-B-C path δ1/δ2/δ3)
2. `docs/work_log/2026-05-08_w4-b-b-macro/session-log.md` (W4-B-B 의 정확한 file diff + macro evidence + dual-source review)
3. `docs/work_log/2026-05-08_w4-b-a-trim/handoff.md` (W4-B-A close + 처음 hypothesis path α/β/γ — 모두 rejected by W4-B-B empirical investigation)
4. `docs/work_log/2026-05-08_w4-b-a-trim/session-log.md` (W4-B-A trim fix + bytecode evidence — chain walker contract reference)
5. `docs/work_log/2026-05-07_w4-candidate-b-chip/handoff.md` (W4-B drawable feed + chip fixture entry)
6. `docs/work_log/2026-05-07_w4-candidate-d-hardening/handoff-w4-d-close.md` (W4 candidate 카탈로그)

원본 기획안 (필수 cross-read):
- `docs/plan/03-roadmap.md` (master roadmap, historical/superseded by `06 §6` + `08 §1`)
- `docs/plan/04-open-questions-and-risks.md` (risk register R1-R9)
- `docs/MILESTONES.md` (W1-W6 gate 체크포인트)
- `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md` (W3D4 phase design — chain walker reference)

진행:

0. 베이스라인 verify
   - cd server && ./gradlew test --console=plain
     → BUILD SUCCESSFUL — 256 unit / 0 fail
   - ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
     → 26 IT PASS + 1 SKIP (chip @Disabled W4-B-C-only)

1. W4-B-C vs hold — 사용자 priority 결정
   - W4-B-C (strong recommend, chip IT 자동 trigger)
   - hold (W4 candidate C/E 진입)

2. W4-B-C 진입:
   - 임시 probe 추가 (LM-W4-B-E 적용 — handoff hypothesis 도 hypothesis):
     - LayoutlibResourceBundle.getResource 호출 로깅 — chip IT 안 DRAWABLE type ref query 시 결과 ResourceValue 형태 trace
     - BridgeContext.getResource hook 의 callback fallback 동작 검증 가능 여부 확인 (`-Daxp.debug.callback=true` toggle 활용)
   - layoutlib jar 의 Resources_Delegate.getDrawable disasm:
     ```
     mkdir -p /tmp/w4bc && cd /tmp/w4bc && \
       unzip -o /home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar \
         'android/content/res/Resources_Delegate.class' \
         'com/android/layoutlib/bridge/impl/ResourceHelper.class' && \
       javap -p -c Resources_Delegate.class > Resources_Delegate-disasm.txt
     ```
     (LM-W3D4-β-G unzip --directory /tmp/w4-* 의무 적용)
   - `Resources_Delegate.getDrawable` 의 정확한 lookup path 추적 — file:line 인용 의무 (LM-W4-B-C). callback hook 진입점 식별 + 우리 bundle 의 byType[DRAWABLE] entry shape 검증
   - 가능한 fix path (handoff §2.4):
     - (path δ1) bundle 의 DRAWABLE ResourceValue 의 `value` 필드 정확화 — magic placeholder `"@axp:drawable-xml"` 대신 layoutlib expected format
     - (path δ2) Resources_Delegate.getDrawable 의 자체 path bytecode trace + callback hook injection point (BridgeContext.getResource etc.) — 큰 design 가능성
     - (path δ3) fixture-side override (단기 close, LM-W4-B-H carry)
   - empirical 결과로 fix path 결정:
     - 작은 fix (1-line) → bytecode evidence + reviewer subagent + commit close
     - 큰 design (Resources_Delegate hook) → plan v6 design + Round 7 Codex+Claude pair-review (LM-G + LM-W4-D-B 적용 — codex 직접 CLI, model 명시 회피)
   - regression guards 보장: T17 5/5 + T20 5/5 + W4-D require throw guard 7 + drawable feed unit 4+6 + acceptance gate `activity_basic` + `activity_minimal` + 본 phase 의 macro contract test 모두 PASS
   - chip IT 의 @Disabled 제거 + LM-W4-B-H close

3. 본 phase commit + push (CLAUDE.md task-unit completion)

CLAUDE.md 규약 준수 (brace own-line, no default params, Zero Tolerance for Magic Numbers, comments 영문 only / function-structure only / chronology phrasing 회피 per Three Hard Rules + reviewer Issue 1+2+3 의 적용 사례 참조). carry-forward LM (handoff §4 의 28 항목) 모두 다음 세션 진입 시 내재화:

- LM-W3D4-β-D~H, LM-W3D4-γ-A~C, LM-W3D4-δ-A~I, LM (CLAUDE.md Three Hard Rules), LM-G, LM-W4-D-A/B
- LM-W4-B-A (close ✓ — `c126512`)
- LM-W4-B-B (close ✓ — 다음 commit)
- LM-W4-B-C (carry): subagent 가설 propagation 위험 — file:line read 가 ground truth
- LM-W4-B-D (carry): Kotlin default-arg + trailing-lambda 회귀
- LM-W4-B-E (carry, **vindicated** — W4-B-B 에서 path α/β/γ reject 의 trigger): handoff hypothesis 도 hypothesis. plan-writing 전 60-min empirical probe (bytecode disasm + runtime probe)
- LM-W4-B-F (carry): NamespaceAwareValueParser 의 top-level `<item type=...>` 미적용 trim — 향후 회귀 시 확장
- LM-W4-B-G (carry, 신규): AAPT2 resource type silent feature gap — `<macro>` 의 unknown top-level skipElement 가 trigger. 향후 `<sample-data>` / `<overlayable>` / `<style-item>` 등의 미파싱 silent gap 가능성
- LM-W4-B-H (carry, 다음 phase): chip drawable abc_vector_test lookup callback 우회 — Resources_Delegate.getDrawable 의 자체 resolution path
- LM-W4-B-I (carry, 신규): Material widget strict-consumer pattern. button lenient (TextView 기반), chip strict (ChipDrawable). 다음 widget 추가 시 strict-consumer chain 진단 budget 우선

본 phase 의 pair-review 적용:
- 큰 design (path δ2 결정 결과 implementation phase 가 큰 변화) → plan v6 작성 → Round 7 Codex+Claude planning pair-review 필수 (LM-G + LM-W4-D-B — codex 직접 CLI, model 명시 회피, reasoning effort=high). pair-review prompt 에 Q6 open critique slot 명시 + feedback_pair_review_codex_killpoint.md 의 KILL POINT 패턴 적용
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
