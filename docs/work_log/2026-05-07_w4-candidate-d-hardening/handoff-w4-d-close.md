# W4 candidate D close — handoff (next candidate decision required)

날짜: 2026-05-07 (생성), 다음 세션 entry: **W4 phase next candidate scope decision** (B / C / A / E 중 선택) + 그 phase 의 planning/implementation
선행 head: `8150212` (main, W4-D close — runtime-classpath.txt hardening)

---

## §1 어디까지 끝났나 — W4-D green state

### §1.1 W4 candidate D 완전 종결

`LayoutlibResourceValueLoader.loadOrGet` AAR-side 분기의 silent `else emptyList()` fallback 을 `loadFramework` 의 기존 `require()` 가드 (lines 85-91) 와 동일 contract 로 정합. silent → loud 전환 1-line 변경이 7-file diff (1 main + 5 IT helpers + 1 unit test 신규) 로 fanning. dual-source review (Claude reviewer subagent + Codex independent sanity-check) 모두 GO.

| 측정 | W4 entry | post-W4-D |
|---|---|---|
| 모듈 합산 unit | 241 PASS | **242 PASS** (+1: require throw 검증) |
| layoutlib-worker IT (PASS + SKIP) | 26 + 0 | **26 + 0** (불변) |
| `LayoutlibResourceValueLoaderTest` | 6 cases | **7 cases** |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| T20 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| acceptance gate `activity_basic` | PASS | PASS (불변) |
| `activity_minimal` glyph dark-pixel | PASS | PASS (불변) |

**LM-W3D4-δ-C** (W3D4 round 5 escalation, *"runtime-classpath.txt 부재 silent empty AAR — false-PASS 위험 → W4+ hardening pass 권장"*) 정확히 close.

### §1.2 본 phase 의 dual-source review 결과

- **feature-dev:code-reviewer (Claude subagent)**: REVISE → 신규 assumeTrue 메시지 5건 + KDoc 의 modification-history phrasing 을 single-shot HIGH-confidence (88) catch — CLAUDE.md Rule 1 (영문-only) + Rule 2 OUT-OF-SCOPE (chronology phrasing 회피). fix 적용 후 본 review 의 모든 angle clear.
- **Codex independent sanity-check (codex-cli 0.128.0, default high model, reasoning effort high)**: APPROVE — 3 angles (require message wording, JVM cache pollution, IT helper symmetry) 모두 no blocking finding. 1 nit: unit test 가 `AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH` 자체를 assert 하면 stronger lock-in (단 message 의 path 인터폴레이션이 이미 cover, 본 commit 에서 미적용).

본 phase 는 implementation phase (CLAUDE.md §Codex "Pairs NOT required for ... ALL implementation-phase work"). 본 dual-source review 는 *bound semantic shift (silent → loud)* 의 single-source 회피 차원, 강제 pairing 아님.

## §2 W4 phase remaining candidates — 다음 scope 결정 필요

W4-D 가 close 되어 active fail surface 0. 다음 candidate 는 4 options:

### §2.1 candidate A — DRAWABLE selector XML feed (mirror, speculative)

- **상태**: hold (no trigger).
- **Mechanism**: T12 ColorStateList + δ-D animator pattern 의 sibling — `res/drawable/*.xml` enumeration + NsBucket.drawables map + getParser ResourceType.DRAWABLE routing.
- **Trigger**: 현 fixture (activity_basic + activity_minimal) PASS — drawable selector 사용 site 없음. 새 fixture XML 추가 + IT 추가가 *trigger 생성*. 즉 speculative — fail 가 없는 상태에서 mirror 추가.
- **권장**: hold until trigger surface 발견 (future widget fixture 추가 시 자연 발생).

### §2.2 candidate B — widget fixture 확장 (Snackbar / Chip / Badge / TextInputLayout)

- **상태**: open, **visual fidelity priority 시 strong recommend**.
- **Mechanism**: 새 fixture XML 추가 — Snackbar / Chip / TextInputLayout / BadgeDrawable 등 Material widget 종류 다양화. 각각 새 IT 추가.
- **Trigger**: 자연 발생 — 새 widget 의 inflate/render path 가 현 fixture 의 cover 외 surface 노출. round 6 Codex Q4 census: BadgeDrawable 가 `checkMaterialTheme` 직접 호출 (별도 path), Snackbar 가 `checkAppCompatTheme` 직접 호출.
- **Cost**: 1 widget 당 fixture XML + IT + 발견 escalation. unknown depth — 이전 phase 들 처럼 multi-round.
- **Coverage**: visual surface 확장 + 새 layer 발견의 강력한 enabler.
- **Risk**: depth unknown — 한 widget 의 새 surface 가 또 다른 multi-phase escalation chain 시작 가능 (W3D4 가 그 예). 새 escalation chain 진입 시 plan v6 + Round 7 Codex+Claude pair-review (planning ONLY) 가능.
- **권장**: 새 세션에서 fresh context 로 entry. visual fidelity 가 main goal 이면 here.

### §2.3 candidate C — namespace-aware mode (cross-AAR 동명 attr/style 분리)

- **상태**: hold (active fail 없음, design 정합 priority 시 entry).
- **Mechanism**: 현재 RES_AUTO bucket 단일 collapse 정책. namespace 분리 시 cross-AAR 동명 ID 의 정확한 disambiguation. layoutlib 의 ResourceNamespace.fromPackageName 활용.
- **Trigger**: 현재 active fail 없음 — `RJarSymbolSeeder.kt:30-33` 의 round 3 reconcile 이 RES_AUTO 통일로 fail 우회. 단 future scenario (e.g. AAR1 의 `myAttr` 와 AAR2 의 `myAttr` 다른 정의) 에서 silent first-wins 위험.
- **Cost**: 큰 design — Round 7 Codex+Claude planning pair-review 필수 (CLAUDE.md §Codex). RJarSymbolSeeder + AarResourceWalker + LayoutlibResourceBundle.byNs 모두 ns-aware refactor.
- **Coverage**: 정합 — 현재 silent first-wins 의 잠재 회귀 면역.
- **Risk**: 큰 refactor, 회귀 가능성. trigger 없는 상태에서 진입 비합리.
- **권장**: design 정합 priority 가 절대적일 때만 entry — 그 외에는 hold.

### §2.4 candidate D — runtime-classpath.txt 부재 hardening

- **상태**: **CLOSED ✓** (본 phase). LM-W3D4-δ-C close.

### §2.5 candidate E — W3D4-ε R$styleable layer

- **상태**: hold (no trigger).
- **Mechanism**: `RJarSymbolSeeder.kt:64-66` 의 R$styleable skip — round 2 A2 결정 (Bridge.parseStyleable 위임). 현재 active fail 없음.
- **Trigger**: hold. 단 future Material widget 의 styled-attrs lookup 단계에서 fail 시 escalation 후보 (plan v3.2 §5.3.1 의 BridgeContext callback instrumentation 으로 narrow).
- **권장**: hold (no trigger) — future surface 시 자연 escalation.

## §3 다음 세션 진입 단계

### §3.1 cold-read 권장 docs (이 순서)

1. **`docs/work_log/2026-05-07_w4-candidate-d-hardening/handoff-w4-d-close.md`** (본 파일)
2. `docs/work_log/2026-05-07_w4-candidate-d-hardening/session-log.md` (W4-D 의 정확한 implementation log + dual-source review 결과)
3. `docs/work_log/2026-05-06_w4-entry/handoff-w4-entry.md` (W4 phase 전체 candidate 카탈로그 — A/B/C/E 의 상세 spec 가 본 핸드오프의 §2 보다 상세)
4. `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (W3D4 phase 전체 progressive escalation 카탈로그 — background, candidate B 진입 시 escalation 패턴 reference)
5. `docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md` (Codex+Claude round 6 — pair-review 패턴 reference, candidate B/C 진입 시)

### §3.2 본 세션 첫 작업 = W4 next candidate scope decision

**사용자 priority 결정 필요** — §2 candidates 중 1 개 선택:

| priority focus | 권장 candidate |
|---|---|
| **visual fidelity 확장** (Material widget coverage) | candidate B (widget fixture 확장) — Snackbar / Chip / TextInputLayout 우선 |
| **design 정합** (namespace separation) | candidate C — 단 Round 7 Codex+Claude pair-review 필수 (planning ONLY) |
| **speculative future-proof** | candidate A (DRAWABLE mirror) — trigger 없으므로 hold 권장 |
| (없음 — wait for trigger) | candidate E (R$styleable) — hold |

### §3.3 결정 후 진행 path

#### path 1 — candidate B (widget fixture 확장, **visual fidelity priority 시 권장**)
1. fixture XML 신규 (e.g. `activity_snackbar.xml` with Snackbar 또는 `activity_extended.xml` with Snackbar/Chip/TextInputLayout 통합) + LayoutlibRendererIntegrationTest 신규 IT
2. IT 측정 → fail surface 분류 (LM-W3D4-β-H + δ-E entry-point comparison 적용 — stack-trace 의 entry-point 비교로 layer shift 식별)
3. 새 escalation chain 진입 가능성 — depth unknown, plan v6 + Round 7 Codex+Claude pair-review 가능성

#### path 2 — candidate C (namespace-aware mode)
1. plan v6 design 작성 — `docs/superpowers/specs/2026-05-XX-w4-namespace-aware.md` — RES_AUTO collapse → ns-aware 분리의 정확한 mechanism + 12-step transition plan
2. Round 7 Codex+Claude pair-review (planning ONLY, LM-G direct CLI: `codex exec --skip-git-repo-check --sandbox danger-full-access` — single LM-W4-D-B 적용으로 explicit model 명시 회피, reasoning effort 만 명시)
3. deltas inline → plan v6.x → GO
4. Implementation: RJarSymbolSeeder + AarResourceWalker + LayoutlibResourceBundle.byNs ns-aware refactor
5. T17 + T20 regression guard 5/5 + 5/5 보장 필수

## §4 회피해야 할 LM (combined 21 — W3D4 phase 산출 19 + W4-D 신규 2)

| LM | 1-line 핵심 |
|---|---|
| LM-W3D4-β-D | KDoc path 표기 시 `/*` 회피 (backtick 인용) |
| LM-W3D4-β-E | `assertNotNull(...)` 반환 chain 금지 |
| LM-W3D4-β-F | IT 실행은 `-PincludeTags=integration` 명시 |
| LM-W3D4-β-G | subagent unzip `--directory /tmp/...` (cwd 회피) |
| LM-W3D4-β-H | acceptance fail 시 stack trace 우선 → fail surface 분류 |
| LM-W3D4-γ-A | reviewer prompt 에 "bundle 의 lookup path trace" 명시 |
| LM-W3D4-γ-B | `Long.decode(...).toInt()` 로 32-bit unsigned hex literal 처리 |
| LM-W3D4-γ-C | ThemeEnforcement multi-sentinel census |
| LM-W3D4-δ-A | spec 코드 sample 의 모든 식별자 grep/Read 검증 의무 |
| LM-W3D4-δ-B | NsBucket type-specific map 분리 sibling KILL POINT |
| LM-W3D4-δ-C | runtime-classpath.txt 부재 silent empty AAR — **W4-D 에서 close ✓** (참고용 carry) |
| LM-W3D4-δ-D | IT-tagged RED-on-main 가능 (default unit suite 제외) |
| LM-W3D4-δ-E | acceptance fail surface 의 stack-trace entry-point 비교로 layer shift 식별 |
| LM-W3D4-δ-F | CLAUDE.md Rule 2 OUT-OF-SCOPE 확대 — phase/task identifier 회피 |
| LM-W3D4-δ-G | annotation message 안 future option 의 technical claim 의 file:line 사전 verify |
| LM-W3D4-δ-H | chain-walker IT (T17/T20-style probe) PASS 가 render-time path PASS 보장 안 함 — setupResources mutation layer 별도 검증 필요 |
| LM-W3D4-δ-I | `RenderResources.applyStyle(useAsPrimary)` override 시 `clear()` 호출 금지 — head-push + dedupe만 |
| LM (CLAUDE.md Three Hard Rules) | 모든 신규 코드 KDoc/inline 영문 only, function/structure only |
| LM-G | codex sandbox bypass 직접 CLI (planning phase 에만) |
| **LM-W4-D-A (신규)** | reviewer agent 가 신규 string literal (assumeTrue 메시지) 의 영문-only 위반 + KDoc 의 modification-history phrasing 을 single-shot HIGH-confidence catch. self-audit 6-item checklist 가 *Rule 1 영문 + Rule 2 chronology phrasing* cross-cutting check 를 sub-audit 으로 빠뜨리는 패턴. → 신규 코드 작성 시 사전 *non-English string literals* 와 *"now matches" / "replacing the earlier" / "previously had"* phrasing 의 인지 필수 |
| **LM-W4-D-B (신규)** | Codex CLI 의 explicit `--model gpt-5.x-codex` flag 가 ChatGPT 계정에서 reject ("model not supported when using Codex with a ChatGPT account"). 모델 제거 + `-c model_reasoning_effort=high` 만 명시 시 default 모델로 정상 작동. CLAUDE.md §Codex 의 *"highest available GPT model"* wording 과 일치 (CLI 자체 resolve). → 향후 Codex 호출 시 explicit model 명시 회피 |

## §5 출발 지점 환경 sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
8150212 feat(w4-d): runtime-classpath.txt hardening — require() guard mirroring loadFramework
2afe300 docs(w4-entry): handoff + next-session-prompt for W4 phase scope decision
9ea2afd feat(w4-entry): tier3-glyph un-disable — TextView dark-pixel render already passes after δ-C/δ-D fixes
f315192 docs(w3d4-close): session-log append for W3D4 phase 종결 (γ → δ-A → δ-B → δ-C → δ-D escalation chain closed)
79d7dc1 feat(w3d4-delta-d): animator XML feed mirror — W3D4 phase closed (activity_basic SUCCESS)

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 242 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
... 26 tests completed (PASS) ...
BUILD SUCCESSFUL — 26 IT PASS + 0 SKIP.
```

regression guards:
```bash
# T17 5-probe (W3D4-δ-A regression guard)
./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.resources.W3D4DeltaThemeChainDiagnosticTest"

# T20 5-probe (W3D4-δ-B regression guard)
./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.resources.W3D4DeltaBGateChainProbeTest"

# acceptance gate
./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest"
./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.LayoutlibRendererTier3MinimalTest"

# require throw guard (W4-D regression guard)
./gradlew :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.LayoutlibResourceValueLoaderTest"
```

debug toggles (선택):
- `-Daxp.debug.bundleShape=true` (LayoutlibResourceValueLoader bootstrap 진단)
- `-Daxp.debug.callback=true` (BridgeContext callback layer instrumentation, plan v3.2 §5.3.1)

`/tmp/w3d4d-*` artefacts 잔존 (cleanup 정책 무관, candidate B/C 진입 시 reference):
- `/tmp/w3d4d-themeenforcement/te_disasm.txt`
- `/tmp/w3d4d-themechain/material/res/values/values.xml`
- `/tmp/w3d4db-codex-*` (round 6 산출 — BadgeDrawable / BaseTransientBottomBar / Resources_Theme_Delegate javap)
- `/tmp/w4-d-hardening-final.diff` (본 phase 의 final review-target diff, codex sanity-check 입력)

## §6 W4 phase status

- W3D4-δ-A~D: **CLOSED ✓**
- tier3-glyph: **CLOSED ✓**
- W4-D (runtime-classpath.txt hardening): **CLOSED ✓**
- 모든 active surface closed — 새 candidate scope 결정 시점.
- 다음 priority = candidate B / C / A / E 중 사용자 결정.

다음 세션 = W4 phase next candidate decision. 결정 후 그 path 진입 (planning OR implementation, candidate 별로 다름).
