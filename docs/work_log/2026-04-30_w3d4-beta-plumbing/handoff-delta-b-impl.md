# W3D4-δ-B implementation phase entry — handoff

날짜: 2026-05-06 (생성), 다음 세션 entry: W3D4-δ-B **implementation** (T20 → T21 → T22)
선행 head: `c8f835f` (main, plan v4.1 + round 6 pair-review APPLIED → GO)

---

## §1 어디까지 끝났나

W3D4-δ-B planning phase 완료. plan v4 draft → round 6 Codex+Claude pair-review (Codex REVISE 0.84, Claude GO_WITH_FIXES 0.86) → 12 deltas inline → plan v4.1 → **GO**. 본 session commit 2 개:

| commit | 내용 |
|---|---|
| `db17bfe` | handoff-delta-b + next-session-prompt-delta-b (planning entry cold-start) |
| `c8f835f` | plan v4.1 + round 6 pair-review + session-log append (REVISE + GO_WITH_FIXES → APPLIED → GO) |

**dominant 결정**: 옵션 B — fixture `Theme.AxpFixture` 안 `<item name="colorPrimaryVariant">?attr/colorPrimary</item>` 1-line 추가. gate PASS (actual semantic) — 옵션 A 의 gate skip (semantic loose) 보다 strict + future-proof. 옵션 C 는 closed (Widget.Material3.Button 도 부모 Widget.MaterialComponents.Button:6349 의 enforceMaterialTheme=true 상속).

**옵션 B 의 enable mechanism** (Codex+Claude convergent BridgeContext bytecode trace, plan v4.1 §4.2 footnote): `Resources.Theme.obtainStyledAttributes(int[])` → `Resources_Theme_Delegate.internalObtainStyledAttributes` → `BridgeContext.createStyleBasedTypedArray(style=null)` → **`mRenderResources.findItemInTheme`** (LayoutlibRenderResources.kt:211-221) → `bridgeSetValue` → `BridgeTypedArray.hasValue(slot)` returns `mResourceData[slot] != null`. fixture-side 직접 정의 → chain walker first-match → BridgeContext.hasValue=true.

**Test posture (현재 main)**:

| 측정 | T19 partial 후 | 본 phase 종료 (T22 PASS 시 expected) |
|---|---|---|
| 모듈 합산 unit | 241 PASS | **241 PASS** (T20 IT-tagged + T21 fixture XML + T22 annotation removal — unit gate 무영향) |
| layoutlib-worker IT | 19 PASS + 2 SKIP | **25 PASS + 1 SKIP** (T20 5-probe + tier3-basic-primary 닫힘, tier3-glyph W4 carry only) |
| acceptance gate `activity_basic` | δ-A closed, δ-B `@Disabled` carry | **δ-A + δ-B 양쪽 closed**, W3D4-δ phase 종결 |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** (필수) |
| T20 5-probe (신규) | n/a | **5/5 PASS** (post-T21) |

## §2 plan v4.1 의 정확한 task split (`docs/superpowers/specs/2026-05-06-w3d4-delta-b-gate-chain-design.md`)

### §2.1 T20 — Diagnostic 5-probe battery (commit 1, IT-tagged)

신규 파일 `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/W3D4DeltaBGateChainProbeTest.kt`. plan v4.1 §4.1 의 explicit 코드 (round 6 Q1 equivalence note + LM-W3D4-δ-A self-check + P5 null-safety inline 적용 후 compile 가능 상태). 5 probes:

- **P1**: `bundle.getResource(ATTR colorPrimaryVariant)` — RES_AUTO bucket 의 attr 정의 검증.
- **P2**: `resources.findItemInTheme(ATTR colorPrimaryVariant)` — chain walker (mThemeStack walk) 가 fixture chain 안 colorPrimaryVariant 발견 (current state 측정 — Lvl 5:2214 의 `?attr/colorPrimary` reachability).
- **P3**: `resources.resolveResValue(item).value` — chain hop 의 종착 (concrete color or `@color/...` ref).
- **P4**: `bundle.getResource(STYLE Widget.Material3.Button)` — T18 의 STYLE special-case 회귀 가드.
- **P5**: `resources.findItemInTheme(ATTR colorPrimary)` — chain anchor 검증 (fixture #6750A4 직접 정의). round 6 Claude Q6 catch 의 null-safety fix 적용 (`assertNotNull(raw)` 한 줄 추가).

`@Tag("integration")` — default unit suite 제외 (axp.kotlin-common.gradle.kts:43-50). RED-on-main 가능 (LM-W3D4-δ-D carry).

```bash
cd server && ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.resources.W3D4DeltaBGateChainProbeTest"
```

P2/P3 의 current state 측정이 결정적 — 이미 PASS 라면 inheritance chain (Lvl 5:2214 `?attr/colorPrimary`) 으로 도달, 옵션 B 의 fix 가 chain shortening 효과만 (round 6 Q5 shadowing safe).

### §2.2 T21 — fixture themes.xml 1-line add (옵션 B, commit 2)

`fixture/sample-app/app/src/main/res/values/themes.xml`:

```diff
 <style name="Theme.AxpFixture" parent="Theme.Material3.DayNight.NoActionBar">
     <item name="colorPrimary">#6750A4</item>
     <item name="colorOnPrimary">#FFFFFF</item>
     <item name="colorPrimaryContainer">#EADDFF</item>
+    <item name="colorPrimaryVariant">?attr/colorPrimary</item>
     <item name="colorOnSurface">#1C1B1F</item>
     <item name="colorOnSurfaceVariant">#49454F</item>
 </style>
```

T20 재측정 → 5/5 PASS 검증. T17 5-probe 도 동시 재측정 (LM-W3D4-δ-B carry — STYLE special-case 회귀 없음 검증).

### §2.3 T22 — Acceptance gate close (commit 3)

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` 의 `tier3 basic primary` `@Disabled` 제거. **본 @Disabled 의 message 자체가 LM-W3D4-δ-G trigger** — T19 작성 시 "M3 path 가 enforceMaterialTheme=false 로 SKIP" 잘못 진술 (실제 Widget.Material3.Button 본체 override 부재, true 상속). 제거 자동으로 stale text 정리.

PASS 조건:
- `Result.Status.SUCCESS == renderer.lastSessionResult?.status`
- `bytes.size > MIN_RENDERED_PNG_BYTES` + `isPngMagic(bytes) == true`
- IT 수: 19 PASS + 2 SKIP → **25 PASS + 1 SKIP** (tier3-glyph W4 carry only)
- system-err 에 `[layoutlib.warning]` 또는 `IllegalArgumentException` 없음

fail 시 escalation (plan v4.1 §6.3): W3D4-ε (R$styleable layer) / direct-sentinel widget surface / Resources_Theme_Delegate.setupResources mutation (Codex Q1 caveat) — `t22-acceptance-gate-followup.md` 작성.

## §3 다음 세션 진입 단계

### §3.1 cold-read 권장 docs (이 순서)

1. **`docs/work_log/2026-04-30_w3d4-beta-plumbing/handoff-delta-b-impl.md`** (본 파일)
2. `docs/superpowers/specs/2026-05-06-w3d4-delta-b-gate-chain-design.md` (plan v4.1 — T20/T21/T22 explicit code/diff, round 6 12 deltas inline)
3. `docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md` (round 6 verdict + KILLPOINTS resolution)
4. `docs/work_log/2026-04-30_w3d4-beta-plumbing/t19-acceptance-gate-followup.md` (δ-A close + δ-B surface entry — 본 phase 의 측정 baseline)
5. `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (β/γ/δ/δ-B 전체 progressive escalation 카탈로그)
6. `docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md` (plan v3.2 — §6.3 escalation 정책 origin)

### §3.2 본 세션 첫 작업 = T20 → T21 → T22 implementation

#### 단계 0 — 베이스라인 verify

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
c8f835f docs(w3d4-delta-b): plan v4.1 + round 6 pair-review (REVISE + GO_WITH_FIXES → APPLIED → GO)
db17bfe docs(w3d4-delta-b): handoff-delta-b + next-session-prompt-delta-b for cold-start planning entry
7462016 docs(w3d4-delta): session-log append for T17/T18/T19 implementation phase
473f55a feat(w3d4-delta): T19 partial — TextAppearance sentinel layer closed via T18, gate-chain surface escalates
22f7077 feat(w3d4-delta): T18 LayoutlibResourceBundle.getResource STYLE special-case (H2 KILL POINT fix)

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 241 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain | tail -3
BUILD SUCCESSFUL — 19 IT PASS + 2 SKIP (tier3-basic-primary δ-B `@Disabled` carry, tier3-glyph W4 carry).
```

#### 단계 1 — T20 신규 (commit 1)

plan v4.1 §4.1 의 explicit 코드 그대로 사용. compile + IT 1회 실행 → P1~P5 결과 캡처. 패턴 매핑 (plan §4.2):
- P1 PASS, P2/P3 PASS but T22 FAIL → option B chain shortening 만 효과, 다른 layer escalation 검토 (setupResources caveat per Codex Q1)
- P1 PASS, P2/P3 FAIL → option B fix 가 P2/P3 PASS 로 전환 (정상 close path)
- P1 FAIL → bundle build / parser 회귀 (LM-W3D4-δ-B 회귀 — 매우 적음)

T20 IT-tagged 이므로 default unit suite 무영향. T17 RED-on-main 패턴 동일.

commit 1: `feat(w3d4-delta-b): T20 gate-chain probe IT (5-probe colorPrimaryVariant chain diagnostic)`.

#### 단계 2 — T21 fixture 1-line (commit 2)

plan v4.1 §5.1 의 정확한 diff 적용. T20 재측정 → 5/5 PASS + T17 5-probe regression guard 5/5 PASS 검증.

commit 2: `feat(w3d4-delta-b): T21 fixture colorPrimaryVariant 1-line — checkMaterialTheme.hasValue gate close`.

#### 단계 3 — T22 acceptance close (commit 3)

plan v4.1 §6.1 의 `@Disabled` 제거. IT 1회 실행. PASS = W3D4-δ phase 종결 (δ-A + δ-B 양쪽 closed). FAIL → §6.3 escalation policy 적용 + `t22-acceptance-gate-followup.md` 작성.

commit 3: `feat(w3d4-delta-b): T22 tier3-basic-primary 닫힘 — δ-A + δ-B 양쪽 sentinel layer closed`.

#### 단계 4 (T22 PASS 시) — W3D4 phase 종결 + W4 entry handoff

- `session-log.md` append (W3D4-δ-B implementation summary).
- `handoff-w4-entry.md` 신규 — W4 entry plan (tier3-glyph carry + namespace-aware mode 검토).
- `next-session-prompt-w4.txt` 신규 — paste-able prompt.

## §4 회피해야 할 LM (combined — β/γ/δ-planning/δ-implementation/δ-B-planning)

| LM | 회피 방법 |
|---|---|
| LM-W3D4-β-D | KDoc path 표기 시 `/*` 회피 (backtick 인용) |
| LM-W3D4-β-E | `assertNotNull(...)` 반환 chain 금지 → `val v = ...; assertNotNull(v); v!!.method()` 패턴 |
| LM-W3D4-β-F | IT 실행은 `-PincludeTags=integration` 명시 |
| LM-W3D4-β-G | subagent unzip `--directory /tmp/...` (cwd 회피) |
| LM-W3D4-β-H | acceptance fail 시 stack trace 우선 → fail surface 가 spec layer 인지 새 layer 인지 분류 |
| LM-W3D4-γ-A | reviewer prompt 에 "bundle 의 lookup path trace" 명시 |
| LM-W3D4-γ-B | `Long.decode(...).toInt()` 로 32-bit unsigned hex literal 처리 |
| LM-W3D4-γ-C | ThemeEnforcement multi-sentinel census — δ-A + δ-B planning 모두 적용 완료 |
| LM-W3D4-δ-A | spec 코드 sample 의 모든 식별자 grep/Read 검증 의무 — plan v4.1 §4.1 verify 완료 |
| LM-W3D4-δ-B | NsBucket type-specific map 분리 = type 별 sibling KILL POINT (ATTR γ T14 + STYLE δ T18 두 차례 catch) — T20 P4 가 회귀 가드 |
| LM-W3D4-δ-C | runtime-classpath.txt 부재 silent empty AAR (W4+ hardening) |
| LM-W3D4-δ-D | IT-tagged RED-on-main 가능 (default unit suite 제외) — T20 도 동일 |
| LM-W3D4-δ-E | acceptance fail surface 의 stack-trace entry-point 비교로 layer shift 식별 — T22 fail 시 적용 |
| LM-W3D4-δ-F | CLAUDE.md Rule 2 OUT-OF-SCOPE 확대 — phase/task identifier 회피 (KDoc/annotation structural-only 영문) |
| **LM-W3D4-δ-G** | annotation message 안 future option 의 *technical claim* 작성 시 file:line evidence 사전 verify 의무 — round 6 catch (T19 `@Disabled` stale text). T22 의 `@Disabled` 제거 자체가 자동 close, 단 future annotation 작성 시 동일 LM 적용 |
| LM (CLAUDE.md Three Hard Rules) | 모든 신규 코드 KDoc/inline 영문 only, function/structure only, ticket reference 최소화 |
| LM-G | codex sandbox bypass 직접 CLI (planning phase 에만 적용 — 본 implementation phase 무관) |

## §5 출발 지점 환경 sanity

```bash
# baseline
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
git status                                                  # clean
git log --oneline -1                                        # c8f835f
cd server && ./gradlew test --console=plain | tail -3      # 241 unit / 0 fail
./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain | tail -3  # 19+2

# T17 regression guard (필수 — δ-B fix 후도 5/5 PASS 보장)
./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.resources.W3D4DeltaThemeChainDiagnosticTest" | tail -10

# debug toggles (선택)
./gradlew ... -Daxp.debug.bundleShape=true ...   # bundle shape 진단 (plan v3.2 §5.5)
./gradlew ... -Daxp.debug.callback=true ...      # BridgeContext callback (plan v3.2 §5.3.1, T22 fail 시)
```

`/tmp/w3d4d-*` artefacts 잔존 여부 (cleanup 정책 무관) — 필요 시 재unzip:
- `/tmp/w3d4d-themeenforcement/te_disasm.txt`
- `/tmp/w3d4d-themechain/material/res/values/values.xml`
- `/tmp/w3d4d-BridgeContext-javap.txt` / `BridgeTypedArray-javap.txt` / `materialbutton-javap.txt`
- `/tmp/w3d4db-codex-*` (round 6 산출 — BadgeDrawable / BaseTransientBottomBar / Resources_Theme_Delegate javap)

## §6 W3D4 phase 전체 status

- δ-A (TextAppearance sentinel): **CLOSED** (T18 H2 KILL POINT fix).
- δ-B (gate chain `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant`): **OPEN — 본 phase implementation entry**.
- ε (R$styleable layer): **carry** — T22 fail 시 §5.3.1 callback instrumentation 으로 narrow.
- direct-sentinel widget surface (BadgeDrawable / BaseTransientBottomBar): **carry** (현 fixture 부재).
- tier3-glyph (Font wiring): **W4 carry**.
- DRAWABLE selector XML feed: **별도 phase**.

다음 세션 = W3D4-δ-B implementation entry. T20 → T21 → T22 → W3D4 phase 종결 (T22 PASS) 또는 W3D4-ε escalation (T22 fail). T22 PASS 시 W4 entry plan 신규 작성.
