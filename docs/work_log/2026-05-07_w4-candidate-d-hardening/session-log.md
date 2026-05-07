# W4 candidate D — runtime-classpath.txt hardening (session log)

날짜: 2026-05-07
선행 head: `2afe300` (main, W4 entry handoff + scope decision pending)
phase: W4 — production hardening (LM-W3D4-δ-C carry close)

---

## Outcome

`LayoutlibResourceValueLoader.loadOrGet` 의 AAR-side 분기에서 `runtime-classpath.txt` 부재 시 silent `else emptyList()` fallback 을 명시 `require(...)` loud throw 로 전환. `loadFramework` 의 기존 `require()` 가드 (lines 85-91) 와 동일 contract shape 으로 정합. silent fallback 이 production 에서 sample-app 의 `:app:assembleDebug` 미실행 상태를 *조용히 통과* 시켜 framework + app-only 빈 AAR 통합으로 false-PASS 가능하던 risk 가 IllegalArgumentException + remediation hint 로 변환됨. IT graceful-skip 패턴 (assumeTrue) 은 5개 helper 의 추가 precondition 1 행으로 보존.

W3D4 phase 의 LM-W3D4-δ-C (round 5 escalation, "W4+ hardening pass 권장") 정확히 close.

## Files modified

### main src
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceValueLoader.kt:42-46` — AAR enumeration 분기를 `if (...exists()) walkAll(...) else emptyList()` 에서 `require(args.runtimeClasspathTxt.exists()) { ... }` + 무조건 `walkAll(...)` 로 교체. require 메시지에 `${args.runtimeClasspathTxt}` 인터폴레이션 (full path 노출) + `:app:assembleDebug` 명시 (remediation hint).

### test src
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceValueLoaderTest.kt` — 신규 unit test `runtime-classpath txt 부재 시 명시 require throw` (assertThrows<IllegalArgumentException>, message substring 2개 assert: "runtime-classpath.txt" + "assembleDebug"). KDoc 은 contract 만 기술 (modification-history phrasing 회피, CLAUDE.md Rule 2 OUT-OF-SCOPE 준수).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/W3D4DeltaThemeChainDiagnosticTest.kt:locate()` — classpathTxt assumeTrue precondition 추가 + `kotlin.io.path.exists` import.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/W3D4DeltaBGateChainProbeTest.kt:locate()` — 동일 패턴.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/MaterialFidelityIntegrationTest.kt:locate()` — 동일 패턴.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:locateAll()` — renderer 경유 caller (renderPng → loadOrGet) graceful-skip 보존을 위해 동일 precondition 추가.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:renderer()` — 동일 패턴 (단 dist 만 assumeTrue, 그 외는 error() — 기존 asymmetric 패턴 보존).

총 7 files / 82+ insertions / 1 deletion.

## Test results

| 지표 | baseline (W4 entry) | post-hardening |
|---|---|---|
| 모듈 합산 unit | 241 PASS / 0 fail | **242 PASS / 0 fail** (+1 new) |
| layoutlib-worker IT | 26 PASS / 0 SKIP | **26 PASS / 0 SKIP** (불변) |
| `LayoutlibResourceValueLoaderTest` | 6 cases | **7 cases** (require-throw 추가) |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| T20 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| acceptance gate `activity_basic` | PASS | PASS (불변) |
| `MaterialFidelityIntegrationTest` | 4/4 PASS | 4/4 PASS (불변) |

## Landmines discovered + 해결

| LM | 내용 | 해결 |
|---|---|---|
| LM-W3D4-δ-C (carry close) | runtime-classpath.txt 부재 silent empty AAR — false-PASS 위험 (W3D4 round 5 round-end escalation, "W4+ hardening 권장") | 본 phase 의 핵심 작업으로 close. require() loud throw + IT precondition 5개 mirror 로 graceful-skip 보존. |
| LM-W4-D-A (신규) | reviewer agent (feature-dev:code-reviewer) 가 신규 assumeTrue 메시지 5건 + KDoc 의 modification-history phrasing 을 single-shot HIGH-confidence catch. CLAUDE.md Rule 1 (영문-only) + Rule 2 OUT-OF-SCOPE (chronology phrasing 회피) 의 cross-cutting check 를 self-audit checklist 가 놓침. | 신규 string 5개 + KDoc 1건 정정. self-audit protocol 에 *영문 + chronology phrasing* check item 추가 권고 (carry — CLAUDE.md update 후보). |
| LM-W4-D-B (신규) | Codex CLI 의 `--model gpt-5.1-codex` 명시 시 ChatGPT 계정 권한 reject ("model not supported when using Codex with a ChatGPT account"). 모델 제거 + `-c model_reasoning_effort=high` 만 명시 시 default 모델로 정상 작동. | 향후 Codex 호출 시 explicit model 명시 회피, reasoning effort 만 명시. CLAUDE.md §Codex 의 "highest available model" wording 과 일치 (CLI 가 자체 resolve). |

## Canonical document changes

본 phase 는 docs/superpowers/specs/ 또는 plans/ 변경 없음. design 이 기존 `loadFramework` 패턴 mirror 의 1-line replacement (큰 design decision 아님) — plan 작성 trigger 미충족.

W3D4 phase 의 plan v3.2 (`docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md`) §11 out-of-scope 의 LM-W3D4-δ-C entry 가 본 commit 으로 close — 단 plan 자체는 historical artifact 로 보존.

## What's blocking / carried forward

본 candidate D close 후 W4 phase 의 active fail surface 0. 다음 candidate 결정 시점:

| candidate | 상태 | 권장 trigger |
|---|---|---|
| **A** — DRAWABLE selector XML feed (mirror, speculative) | hold | future widget fixture 가 drawable selector 사용 시 trigger |
| **B** — widget fixture 확장 (Snackbar/Chip/TextInputLayout/Badge) | open | visual fidelity priority 시 entry. depth unknown — multi-round escalation 가능 |
| **C** — namespace-aware mode (RES_AUTO collapse → ns 분리) | hold | active fail 없음. 큰 refactor — Round 7 Codex+Claude pair-review 필수 |
| **D** — runtime-classpath.txt hardening | **CLOSED ✓** | LM-W3D4-δ-C close |
| **E** — W3D4-ε R$styleable layer | hold | future Material widget styled-attrs lookup fail 시 escalate |

## Pair review verdicts

- **feature-dev:code-reviewer (Claude subagent)**: REVISE (1 IMPORTANT, confidence 88) — 신규 assumeTrue 메시지 5건 + KDoc 의 modification-history phrasing 을 catch. fix 적용 후 본 review 의 모든 angle clear.
- **Codex independent sanity-check (codex-cli 0.128.0, default high model + reasoning effort high)**: APPROVE — 3 angles (require message wording, JVM cache pollution, IT helper symmetry) 모두 no blocking finding. 1 nit (unit test 가 `AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH` 자체를 assert 하면 stronger lock-in — 단 message 의 path 인터폴레이션이 이미 cover, 본 commit 에서 미적용).

본 phase 는 implementation phase (CLAUDE.md §Codex "Pairs NOT required for ... ALL implementation-phase work"). 본 dual-source review 는 *bound semantic shift (silent → loud)* 의 single-source 회피 차원, 강제 pairing 아님.

## Commits + push

- (다음 commit) `feat(w4-d): runtime-classpath.txt hardening — require() guard mirroring loadFramework + IT precondition graceful-skip` — 본 session-log + 7 file diff 동시.

CLAUDE.md task-unit completion 규약 준수 — push 즉시 진행.
