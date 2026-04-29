# W3D4 MATERIAL-FIDELITY — Next-Session Handoff (spec + plan v1 prep done)

**Status**: spec round 2 + plan v1 작성 완료, **페어 round 2 (plan-review) 직전 상태**
**Date**: 2026-04-29 (작성일)
**Suggested next-session date**: TBD (사용자 결정)
**Predecessor**: `docs/work_log/2026-04-29_w3d3-l3-classloader/next-session-handoff.md`

---

## 1. 한 줄 요약

W3D4 MATERIAL-FIDELITY 의 spec round 1 → 페어 round 1 → spec round 2 (GO) → plan v1 까지 완료. **다음 세션 첫 작업 = plan v1 에 대한 Codex+Claude 페어 round 2 (plan-review)**, 그 결과로 plan v2 작성 후 subagent-driven implementation 진입.

## 2. 현재 상태 (origin/main, commit `b584f31`)

### 2.1 작성된 artifact (commit 순)

| Commit | 파일 | 줄수 | 내용 |
|---|---|---|---|
| `0ccb8c7` | `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md` (round 1) | 374 | spec round 1 — Q1-Q4 사용자 결정 반영 |
| `3e0669b` | 동일 파일 (round 2 in-place revise) | 564 | spec round 2 — 페어 round 1 GO 반영 (σ FULL, RES_AUTO 통일, R name canon) |
| `b584f31` | `docs/superpowers/plans/2026-04-29-w3d4-material-fidelity.md` | 2349 | plan v1 — T1-T10 TDD steps |

### 2.2 베이스라인 (W3D3-α 종결, 변동 없음)

- **167 unit PASS** (W3D3 142 + α 25).
- **12 integration PASS + 1 SKIPPED** (`tier3-glyph` 만; `LayoutlibRendererIntegrationTest` fallback 으로 PASS).
- BUILD SUCCESSFUL, smoke "ok".

### 2.3 페어 round 1 결과 (spec round 1 → round 2 변경 근거)

| Q | 양 reviewer verdict | 변경 |
|---|---|---|
| Q1 (ns 모드) | NUANCED — hybrid 회피, mode 통일 권고 | hybrid (app=RES_AUTO + AAR=named) → **traditional 통일** (모든 non-framework = RES_AUTO) |
| Q2 (getStyle fallback) | AGREE/NUANCED — fallback 필요, 단 surface 확장 | **uniform fallback** (getStyle + getParent + findItemInStyle + findItemInTheme 모두 ns-exact-then-name) |
| Q3 (resolved=unresolved) | **FULL convergence DISAGREE** — base methods stub | **ρ → σ FULL** (9 method override, ~250 LOC, chain walker + theme stack) |
| Q4 (41 AAR cost) | NUANCED — 2-5초 너무 보수적, sub-second 예상 | **wall-clock 진단 의무** + 27/41 카운트 명시 |
| Q5 (fromPackageName) | **FULL convergence AGREE** | 변경 없음 |
| Q6 (general) | NUANCED — R name canon (underscore↔dot) + dup attr ID + red tests | **R-1 + R-2 NEW 추가** (RJarSymbolSeeder 변경) |

empirical 증거: Claude javap (RR base 5 method = null/empty stub), Codex 실측 (27/41 보유, 855KB 합산, I/O ~0.12s).

## 3. 다음 세션의 첫 작업 — 페어 round 2 (plan-review)

### 3.1 Pair-review 의 목적

CLAUDE.md "Pairs REQUIRED for plan-document review rounds". round 1 의 4 critical fix 가 implementation 회귀 1-2 day 방지 → round 2 도 동등 가치 기대. plan v1 의 잠재 weak point:

1. **T6 의 `parseReference` regex** (`?<ns>:attr/...`, `@<ns>:<type>/<name>` 매칭) 가 layoutlib Bridge 의 실제 던지는 ref 패턴과 100% 일치 검증 안 됨. round 2 가 검증.
2. **T7 의 RJarSymbolSeeder 변경 위치** — sub-step 가 grep 후 수정 instruction. 정확한 emit 코드 위치를 plan 이 명시 안 함. round 2 가 explicit 코드 보강 권고 가능.
3. **T9 의 `locateAll()` helper 시그니처** — W3D3 helper 재활용 가정 (`...` 표기). round 2 가 Triple<Path, Path, Path> 반환 검증.
4. **T8 의 caller enumeration** — `grep -rn "SharedLayoutlibRenderer.getOrCreate"` 후 모든 site 갱신 instruction. CLI / MCP / 기존 test 등 누락 위험.
5. **dedupe 정책의 deterministic 보장** — runtime-classpath.txt 순서가 Gradle 결정성에 의존. plan v1 가 명시하지만 edge case (동일 우선순위 AAR 충돌) 미검증.
6. **chain walker hop limit** — `MAX_REF_HOPS=10`, `MAX_THEME_HOPS=20` 추정. 실 Material3 chain depth 와 비교 검증 필요.

### 3.2 페어 round 2 dispatch (다음 세션 1턴)

- **Codex**: direct CLI `codex exec --skip-git-repo-check --sandbox danger-full-access` (LM-G), config 의 `model = gpt-5.5`, `model_reasoning_effort = xhigh`. prompt 는 plan v1 path + 위 6개 query.
- **Claude**: subagent (`general-purpose`) 또는 `feature-dev:code-reviewer`. 동일 plan + query + 독립 verdict.
- 두 결과 reconcile (full convergence / set-converge / divergent → judge round).

### 3.3 round 2 verdict 후 plan v2

- **GO** (양쪽 NUANCED 이하) → plan v2 작성 (in-place edit) → implementation
- **REVISE** (양쪽 DISAGREE 또는 critical 발견) → plan v1 의 affected task 만 수정 → 재dispatch 또는 user 결정

## 4. Implementation 후 단계 (plan v2 GO 이후)

### 4.1 subagent-driven 진행

- `superpowers:subagent-driven-development` 스킬 invoke.
- 각 task (T1-T10) 별 fresh subagent dispatch. parent (Claude) 가 두 단계 review:
  1. plan-step compliance review (subagent 결과가 plan task spec 와 정합?)
  2. domain review (실 동작 정합?)
- task 단위 commit + push (CLAUDE.md "task-unit completion" 의무).

### 4.2 acceptance gate (spec §10)

종결 기준:
1. `tier3_basic_primary` PASS — `assertEquals("activity_basic.xml", ...)` 강제 + SUCCESS + PNG > 1000.
2. `tier3_basic_minimal_smoke` PASS — minimal carry 보존.
3. unit ~213+ PASS.
4. integration 13 PASS + 1 SKIP.
5. BUILD SUCCESSFUL, smoke "ok".
6. wall-clock cold-start < 5초 (실측 sub-second 기대).
7. RNameCanonicalizationTest + RDuplicateAttrIdTest PASS.
8. work_log + 08 §7.7.6 close + MEMORY 갱신.

## 5. 핵심 위험 / LM (W3D3-α 누적 + W3D4 신규)

### 5.1 W3D3 시리즈 누적 (보존)

- **LM-W3D3-A** / **LM-α-A** — 페어 convergent 도 empirical 검증 누락 시 wrong. **본 W3D4 도 동일 규율** — round 2 페어가 javap/실측 evidence 동반 권고.
- **LM-W3D3-B** — `:layoutlib-worker` test classpath 에 `kotlin.test` 없음. JUnit Jupiter Assertions 만 (plan v1 의 모든 test 에 적용).
- **LM-α-B** — Codex stalled-final 패턴. mitigation: Claude single-source verdict + flagging.
- **LM-α-D** — Kotlin backtick 함수명 안 마침표 금지.
- **LM-G** — `codex exec --skip-git-repo-check --sandbox danger-full-access` 직접 CLI (codex-rescue subagent bypass).

### 5.2 W3D4 신규 (round 1 페어가 발견)

- **LM-W3D4-A**: layoutlib `RenderResources` base 가 null/empty stub (`resolveResValue / findItemInStyle / dereference / getParent / getAllThemes`). javap 으로 확정. ρ 위임 = resolution bypass — σ FULL 강제.
- **LM-W3D4-B**: R$style 의 underscore name (`Theme_AxpFixture`) ↔ XML dot name (`Theme.AxpFixture`) AAPT 변환. RJarSymbolSeeder 가 styleNameToXml 호출 필수.
- **LM-W3D4-C**: 다중 R class (appcompat / material) 의 동명 attr → first-wins per name + 진단 로그. seeder 의 `seenAttrNames` HashSet 추가.

## 6. 다음 세션 시작 시 참고 문서 (1턴에 읽을 것)

1. **본 핸드오프** (이 파일) — `docs/work_log/2026-04-29_w3d4-material-fidelity/spec-plan-handoff.md`
2. **spec round 2** — `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md` (564줄, §0 round 2 변경 요약 부터 읽으면 빠름)
3. **plan v1** — `docs/superpowers/plans/2026-04-29-w3d4-material-fidelity.md` (2349줄, T1-T10)
4. **W3D3-α 종결** — `docs/work_log/2026-04-29_w3d3-l3-classloader/alpha-session-log.md`
5. **W3D1 prior art** — `docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md`, `docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md`

## 7. Quick start commands (다음 세션 시작 직후)

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer

# 베이스라인 검증 (변동 없는지)
./server/gradlew -p server test                                          # 167 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 12 + 1 SKIP
./server/gradlew -p server build                                         # SUCCESSFUL

# spec round 2 + plan v1 확인
ls -la docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md
ls -la docs/superpowers/plans/2026-04-29-w3d4-material-fidelity.md
git log --oneline -5  # 0ccb8c7 → 3e0669b → b584f31 확인

# Codex CLI 사용 가능 여부 확인 (페어 round 2 dispatch 전)
codex --version  # 0.125.0 이상 + ~/.codex/config.toml 의 model/effort/sandbox 확인
```

## 8. 페어 round 2 prompt (initial template)

다음 세션이 페어 round 2 dispatch 시 사용할 Codex/Claude prompt 골격:

```
W3D4 MATERIAL-FIDELITY plan v1 페어 리뷰. round 1 (spec) 이 4 critical fix
도출 → spec round 2 GO + plan v1 작성. 본 round 가 plan v1 의 잠재 weak
point 검증.

Repo: /home/bh-mark-dev-desktop/workspace/android_xml_previewer
Plan: docs/superpowers/plans/2026-04-29-w3d4-material-fidelity.md (2349줄, T1-T10)
Spec: docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md (564줄)
Predecessor: docs/work_log/2026-04-29_w3d3-l3-classloader/next-session-handoff.md

검토 axis (양 reviewer 공통):
Q1. T6 parseReference regex (?<ns>:attr/X, @<ns>:<type>/<name>) 가 layoutlib
    Bridge 의 실제 ref 패턴과 일치? known issue?
Q2. T7 의 RJarSymbolSeeder 변경 위치 명시 충분? grep 후 sub-step 의
    explicit code 가 필요?
Q3. T9 locateAll() helper 시그니처 — W3D3 helper 의 정확한 반환 type?
Q4. T8 caller enumeration — SharedLayoutlibRenderer.getOrCreate 의
    호출 site 누락 가능성? CLI / MCP / 기존 test?
Q5. dedupe deterministic — runtime-classpath.txt 순서 외 Gradle 결정성
    위반 가능 케이스?
Q6. chain walker hop limit (MAX_REF_HOPS=10, MAX_THEME_HOPS=20) 가
    실 Material3 chain depth 보다 안전 마진?
Q7. 일반 critique — TDD 순서 / atomic commit boundary / 누락된 step.

출력: Q1-Q7 verdict (AGREE/DISAGREE/NUANCED/INSUFFICIENT-INFO) +
Reasoning + Recommended action + Confidence + Evidence.
종합: GO / GO_WITH_FOLLOWUPS / REVISE_REQUIRED.

Codex 호출: `codex exec --skip-git-repo-check --sandbox danger-full-access`,
config 의 latest GPT model + xhigh effort. 한국어 답변.
```

(본 prompt 는 `next-session-prompt.txt` 에도 저장됨.)

## 9. 본 세션의 주요 발견 (다음 세션이 잊으면 안 되는 것)

1. **layoutlib RenderResources base 의 null/empty stub** (LM-W3D4-A) — empirical javap 검증으로 확정. plan v1 의 T6 σ FULL 의 직접 근거.
2. **AGP 의 merged.dir 가 비어있음** (assembleDebug 후 빈 디렉토리만 marker). per-AAR walker 강제 — `packaged_res/values/values.xml` 도 sample-app 의 14줄만 (Theme.AxpFixture 단일).
3. **`Theme.AxpFixture` 의 actual parent = `Theme.Material3.DayNight.NoActionBar`** (W3D3 핸드오프의 `Theme.MaterialComponents.*` 는 오기. spec round 2 §7.3 에 정정).
4. **`runtime-classpath.txt` = 55 entries / 41 AAR / 27 with values.xml** (Codex 실측). loader 의 대상 카운트.
5. **material-1.12.0.aar 안 `res/values/values.xml` = 604KB / 83 Theme.* style** — 핵심 inflation 자산.

## 10. carry 0 (본 핸드오프 자체)

이 핸드오프 작성으로 W3D4 spec/plan prep 단계 완전 종결. 다음 세션은 fresh context 로 §3.1-3.2 의 페어 round 2 부터 즉시 시작 가능. carry forward 항목 없음.
