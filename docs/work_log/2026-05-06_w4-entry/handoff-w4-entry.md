# W4 phase entry — handoff (scope decision required)

날짜: 2026-05-06 (생성), 다음 세션 entry: **W4 phase scope decision** (which carry to prioritize) + 그 phase 의 planning/implementation
선행 head: `9ea2afd` (main, W3D4 phase 완전 close + W4 entry tier3-glyph close)

---

## §1 어디까지 끝났나 — W3D4 + W4 entry green state

### §1.1 W3D4 phase 완전 종결

5-phase escalation chain (γ → δ-A → δ-B → δ-C → δ-D) 모두 close:

| phase | layer | fix | commit |
|---|---|---|---|
| γ | enum/flag attr capture | T14 RES_AUTO ATTR special-case + T15 framework Bridge.init | `3ca8bb5` `1541958` |
| δ-A | TextAppearance sentinel (`bundle.getResource STYLE` 누락) | T18 STYLE special-case sibling to ATTR | `22f7077` |
| δ-B | gate chain `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant.hasValue` | T20 5-probe diagnostic + T21 fixture `colorPrimaryVariant=?attr/colorPrimary` | `f7fb4d5` `2175eb1` |
| δ-C | setupResources mutation (`applyStyle clear() wipe fixture chain`) | head-push + dedupe (no clear) | `654a70e` |
| δ-D | animator XML feed (Material AAR `res/animator/` 미enumerate) | T12 ColorStateList pattern mirror to ANIMATOR | `79d7dc1` |

### §1.2 W4 entry — tier3-glyph close

직전 SKIP 1건 (`tier3-glyph — activity_minimal 의 TextView 영역에 실 dark pixel`) 도 직접 측정으로 close (`9ea2afd`). δ-C/δ-D fix 의 부산물로 layoutlib 의 native font + StaticLayout + Canvas.drawText path 가 정상 동작 — 추가 wiring 불필요.

### §1.3 Test posture (현재 main)

| 측정 | β baseline | W3D4 종료 | W4 entry |
|---|---|---|---|
| 모듈 합산 unit | 215 PASS | 241 PASS | **241 PASS** |
| layoutlib-worker IT | 12 PASS + 2 SKIP | 25 PASS + 1 SKIP | **26 PASS + 0 SKIP** ✓ |
| acceptance gate `activity_basic` | δ-A `@Disabled` | PASS | PASS ✓ |
| `activity_minimal` glyph dark-pixel | n/a | n/a | PASS ✓ |
| T17 5-probe + T20 5-probe regression guards | n/a | 5/5 + 5/5 | **5/5 + 5/5** ✓ |

**모든 active surface closed — outstanding warning 모두 known-benign framework attr lookup** (textSelectHandle / textCursor / hyphenationFrequency / textAppearanceSmall — non-fatal, layoutlib data 자체 issue).

## §2 W4 phase candidates — scope 결정 필요

다음 phase 의 scope 결정이 본 handoff 의 핵심. 5 candidates:

### §2.1 candidate A — DRAWABLE selector XML feed (mirror, speculative)

- **Mechanism**: T12 ColorStateList + δ-D animator pattern 의 sibling — `res/drawable/*.xml` enumeration + NsBucket.drawables map + getParser ResourceType.DRAWABLE routing.
- **Trigger**: 현 fixture (activity_basic + activity_minimal) PASS — drawable selector 사용 site 없음. 새 fixture XML 추가 + IT 추가가 *trigger 생성*. 즉 speculative — fail 가 없는 상태에서 mirror 추가.
- **Cost**: ~6 file edits (constants + variant + walker + bucket + callback + renderer wire), 약 1 commit 또는 fixture 확장 포함 시 2 commit.
- **Coverage**: future widget 가 drawable selector 사용 시 surface 회피 — visual fidelity 확장.
- **Risk**: trigger 없는 fix 의 over-engineering 위험. 지금 적용하면 IT 가 cover 못 하고 나중에 surface 발견 시 회귀 위험.
- **권장**: hold until trigger surface 발견 (future widget fixture 추가 시 자연 발생).

### §2.2 candidate B — widget fixture 확장 (Snackbar / Chip / Badge / TextInputLayout)

- **Mechanism**: 새 fixture XML 추가 — Snackbar / Chip / TextInputLayout / BadgeDrawable 등 Material widget 종류 다양화. 각각 새 IT 추가.
- **Trigger**: 자연 발생 — 새 widget 의 inflate/render path 가 현 fixture 의 cover 외 surface 노출. round 6 Codex Q4 census: BadgeDrawable 가 `checkMaterialTheme` 직접 호출 (별도 path), Snackbar 가 `checkAppCompatTheme` 직접 호출.
- **Cost**: 1 widget 당 fixture XML + IT + 발견 escalation. unknown depth — 이전 phase 들 처럼 multi-round.
- **Coverage**: visual surface 확장 + 새 layer 발견의 강력한 enabler.
- **Risk**: depth unknown — 한 widget 의 새 surface 가 또 다른 multi-phase escalation chain 시작 가능 (W3D4 가 그 예).
- **권장**: priority 결정에 따라 — visual fidelity 가 main goal 이면 here.

### §2.3 candidate C — namespace-aware mode (cross-AAR 동명 attr/style 분리)

- **Mechanism**: 현재 RES_AUTO bucket 단일 collapse 정책. namespace 분리 시 cross-AAR 동명 ID 의 정확한 disambiguation. layoutlib 의 ResourceNamespace.fromPackageName 활용.
- **Trigger**: 현재 active fail 없음 — RJarSymbolSeeder.kt:30-33 의 round 3 reconcile 이 RES_AUTO 통일로 fail 우회. 단 future scenario (e.g. AAR1 의 `myAttr` 와 AAR2 의 `myAttr` 다른 정의) 에서 silent first-wins 위험.
- **Cost**: 큰 design — Round 7 Codex+Claude planning pair-review 필수 (CLAUDE.md §Codex). RJarSymbolSeeder + AarResourceWalker + LayoutlibResourceBundle.byNs 모두 ns-aware refactor.
- **Coverage**: 정합 — 현재 silent first-wins 의 잠재 회귀 면역.
- **Risk**: 큰 refactor, 회귀 가능성. trigger 없는 상태에서 진입 비합리.
- **권장**: hold — design fix 의 의도이지만 active fail 없으므로 시급 안함.

### §2.4 candidate D — runtime-classpath.txt 부재 hardening (LM-W3D4-δ-C)

- **Mechanism**: LayoutlibResourceValueLoader.loadOrGet 가 runtime-classpath.txt 부재 시 silent empty AAR list (loader.kt:42-44). false-PASS 위험 — bundle build 가 framework + sample-app 만으로 진행, AAR 통합 미작동.
- **Trigger**: graceful skip 패턴 (T17/T20 의 assumeTrue) 으로 본 phase 회피. 단 production runtime 에서 manifest 부재 시 silent fallback — 진단 어려움.
- **Cost**: 작은 작업 — assertion 또는 명시적 `IllegalStateException("manifest 부재 — assembleDebug 먼저 실행")` 추가. 1 commit.
- **Coverage**: production 안전성 향상 — silent failure → loud failure.
- **Risk**: 낮음 — 단 graceful skip 의 IT 패턴 영향 검증 필요.
- **권장**: low-cost hardening 으로 즉시 처리 가능.

### §2.5 candidate E — W3D4-ε R$styleable layer (hold — no active trigger)

- **Mechanism**: `RJarSymbolSeeder.kt:64-66` 의 R$styleable skip — round 2 A2 결정 (Bridge.parseStyleable 위임). 현재 active fail 없음.
- **Trigger**: hold. 단 future Material widget 의 styled-attrs lookup 단계에서 fail 시 escalation 후보 (plan v3.2 §5.3.1 의 BridgeContext callback instrumentation 으로 narrow).
- **권장**: hold (no trigger) — future surface 시 자연 escalation.

## §3 다음 세션 진입 단계

### §3.1 cold-read 권장 docs (이 순서)

1. **`docs/work_log/2026-05-06_w4-entry/handoff-w4-entry.md`** (본 파일)
2. `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (W3D4 phase 전체 progressive escalation 카탈로그 — 본 phase 의 background)
3. `docs/superpowers/specs/2026-05-06-w3d4-delta-b-gate-chain-design.md` (plan v4.1 — δ-D mirror pattern 의 reference)
4. `docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md` (Codex+Claude round 6 — δ-C root cause 정확 예측한 caveat 패턴)

### §3.2 본 세션 첫 작업 = W4 scope decision

**사용자 priority 결정 필요** — 본 handoff 의 §2 candidates 중 1-2 개 선택:

| priority focus | 권장 candidate |
|---|---|
| **visual fidelity 확장** (Material widget coverage) | candidate B (widget fixture 확장) — Snackbar / Chip / TextInputLayout 우선 |
| **production hardening** (silent failure 제거) | candidate D (runtime-classpath.txt assertion) — low cost |
| **design 정합** (namespace separation) | candidate C — 단 Round 7 Codex+Claude pair-review 필수 (planning ONLY) |
| **speculative future-proof** | candidate A (DRAWABLE mirror) — trigger 없으므로 hold 권장 |

### §3.3 결정 후 진행 path

#### path 1 — candidate B (widget fixture 확장)
1. fixture XML 신규 (e.g. `activity_snackbar.xml` with Snackbar) + LayoutlibRendererIntegrationTest 신규 IT
2. IT 측정 → fail surface 분류 (LM-W3D4-β-H + δ-E entry-point comparison 적용)
3. 새 escalation chain 진입 — depth unknown, plan v6 + Round 7 pair-review 가능성

#### path 2 — candidate D (runtime-classpath.txt hardening)
1. `LayoutlibResourceValueLoader.kt:42-44` — manifest 부재 시 명시 throw 또는 명시 warn (sample-app 의 assembleDebug 미실행 진단)
2. T17/T20 의 graceful skip 패턴 영향 검증 — assumeTrue path 보존 필요
3. 1 commit close

#### path 3 — candidate C (namespace-aware mode)
1. plan v6 design 작성 — RES_AUTO collapse → ns-aware 분리의 정확한 mechanism
2. Round 7 Codex+Claude pair-review (planning ONLY, LM-G direct CLI)
3. 12 deltas inline → plan v6.x → GO
4. Implementation — RJarSymbolSeeder + AarResourceWalker + LayoutlibResourceBundle.byNs ns-aware refactor
5. T17 + T20 regression guard 5/5 + 5/5 보장 필수

## §4 회피해야 할 LM (combined 19 — W3D4 phase 산출 모두 carry)

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
| LM-W3D4-δ-C | runtime-classpath.txt 부재 silent empty AAR (W4 candidate D 가 정확히 본 LM hardening) |
| LM-W3D4-δ-D | IT-tagged RED-on-main 가능 (default unit suite 제외) |
| LM-W3D4-δ-E | acceptance fail surface 의 stack-trace entry-point 비교로 layer shift 식별 |
| LM-W3D4-δ-F | CLAUDE.md Rule 2 OUT-OF-SCOPE 확대 — phase/task identifier 회피 |
| LM-W3D4-δ-G | annotation message 안 future option 의 technical claim 의 file:line 사전 verify |
| LM-W3D4-δ-H | chain-walker IT (T17/T20-style probe) PASS 가 render-time path PASS 보장 안 함 — setupResources mutation layer 별도 검증 필요 |
| **LM-W3D4-δ-I** | `RenderResources.applyStyle(useAsPrimary)` override 시 `clear()` 호출 금지 — `Resources_Theme_Delegate.setupResources` 의 force=true 가 *기존 stack wipe 의도가 아닌 head-priority 갱신*. RenderResources subclass 작성 시 dedupe + head-push 만 |
| LM (CLAUDE.md Three Hard Rules) | 모든 신규 코드 KDoc/inline 영문 only, function/structure only |
| LM-G | codex sandbox bypass 직접 CLI (planning phase 에만) |

## §5 출발 지점 환경 sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
9ea2afd feat(w4-entry): tier3-glyph un-disable — TextView dark-pixel render already passes after δ-C/δ-D fixes
f315192 docs(w3d4-close): session-log append for W3D4 phase 종결 (γ → δ-A → δ-B → δ-C → δ-D escalation chain closed)
79d7dc1 feat(w3d4-delta-d): animator XML feed mirror — W3D4 phase closed (activity_basic SUCCESS)
654a70e feat(w3d4-delta-c): applyStyle preserve fixture chain — Material gate-chain (δ-A + δ-B) closed, surface shifts to animator XML feed
ffca7cd docs(w3d4-delta-b): session-log append for T20/T21/T22 implementation phase

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 241 unit total / 0 fail.

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
./gradlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.LayoutlibRendererTier3MinimalTest"
```

debug toggles (선택):
- `-Daxp.debug.bundleShape=true` (LayoutlibResourceValueLoader bootstrap 진단)
- `-Daxp.debug.callback=true` (BridgeContext callback layer instrumentation, plan v3.2 §5.3.1)

`/tmp/w3d4d-*` artefacts 잔존 (cleanup 정책 무관):
- `/tmp/w3d4d-themeenforcement/te_disasm.txt`
- `/tmp/w3d4d-themechain/material/res/values/values.xml`
- `/tmp/w3d4d-BridgeContext-javap.txt` / `BridgeTypedArray-javap.txt` / `materialbutton-javap.txt`
- `/tmp/w3d4db-codex-*` (round 6 산출 — BadgeDrawable / BaseTransientBottomBar / Resources_Theme_Delegate javap)

## §6 W4 phase status

- W3D4-δ-A~D: **CLOSED** ✓
- tier3-glyph: **CLOSED** ✓
- 모든 active surface closed — 새 phase scope 결정 시점.
- 다음 priority = candidate A/B/C/D/E 중 사용자 결정.

다음 세션 = W4 phase scope decision. 결정 후 그 path 진입 (planning OR implementation, candidate 별로 다름).
