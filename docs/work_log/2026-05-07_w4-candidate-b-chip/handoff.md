# W4 candidate B — chip first measurement (handoff)

날짜: 2026-05-07 (생성), 다음 세션 entry: **W4-B-A 진입** (chip stateListAnimator callback bypass — Resources_Delegate.getAnimation_Original path 추적 + plan v6 작성 + Round 7 Codex+Claude pair-review). OR **W4-B-B 단독 진입** 시 textAppearance NPE 별도 분석 (단 W4-B-A 가 stateListAnimator selector path 의 callback hook 보강 결정 후 W4-B-B 가 자연 진입 권장).

선행 head: (다음 commit) `feat(w4-b): drawable XML feed mirror — candidate A natural trigger close + chip IT parked for W4-B-A`

---

## §1 어디까지 끝났나 — W4-B-drawable green state

### §1.1 W4 candidate A 자연 trigger close

W4 entry handoff (`docs/work_log/2026-05-06_w4-entry/handoff-w4-entry.md`) 의 candidate A — DRAWABLE selector XML feed mirror 가 candidate B 의 첫 measurement (chip fixture 추가) 에서 **자연 trigger** 발생. 6 main file mirror of W3D4-δ-D animator pattern + 2 신규 unit test 로 close. 향후 widget extension 의 foundation. Material 1.12.0 의 132 drawable XML 이 RES_AUTO bucket 의 drawables map 에 등록.

### §1.2 W4 candidate B 첫 surface 노출 — 두 carry layer

chip widget inflation 이 drawable feed 후 advance 했으나 두 깊은 layer 노출:

**LM-W4-B-A** — chip 의 `android:stateListAnimator` 가 callback bypass 된 채 fail. m3_chip_state_list_anim 이 bundle 에 등록되어 있고 (debugListAnimators probe 로 확인), 동일 path 의 m3_btn_state_list_anim 은 정상 working (basic IT PASS — getParser 정상 호출). 차이점: chip animator 는 `<selector>` root, button animator 는 `<set>` 안 `<selector>` root. layoutlib `Resources_Delegate.getAnimation_Original` 이 root form 에 따라 callback 우회.

**LM-W4-B-B** — chip stateListAnimator @null override 시 inflation advance — color state list lookup 정상. 그러나 `com.google.android.material.resources.TextAppearance.getTextSize()` NPE — `?attr/textAppearanceLabelLarge` 가 ChipDrawable.loadFromAttributes 의 expected TextAppearance 로 resolve 안 됨.

### §1.3 Test posture (현재 main + 다음 commit)

| 측정 | W4-D (post W4-D close) | W4-B (post drawable feed) |
|---|---|---|
| 모듈 합산 unit | 242 PASS | **253 PASS** (+11) |
| layoutlib-worker IT | 26 PASS + 0 SKIP | **26 PASS + 1 SKIP** (chip @Disabled) |
| `AarResourceWalkerDrawableTest` | n/a | **4/4 PASS** |
| `LayoutlibResourceBundleDrawableTest` | n/a | **6/6 PASS** |
| `MinimalLayoutlibCallbackColorParserTest` | 7 cases | **8 cases** (rename + sibling) |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** |
| T20 5-probe regression guard | 5/5 PASS | **5/5 PASS** |
| acceptance gate `activity_basic` | PASS | PASS |
| `activity_minimal` glyph dark-pixel | PASS | PASS |

walker stats: `walked 41 AARs (13 with res, 28 code-only, 191 color-state-lists, 32 animator-xmls, 132 drawable-xmls) in 36ms`.

## §2 W4-B 의 carry layer — 다음 entry path

### §2.1 W4-B-A — chip stateListAnimator callback bypass (strong recommend)

**Mechanism**: chip 의 inflation 이 `View.<init>:6033 → AnimatorInflater.loadStateListAnimator(context, id) → createStateListAnimatorFromXml(parser)` path 를 타지만, parser 가 `BridgeXmlBlockParser` wrapper 안에 setInput 안 된 inner parser 를 갖고 있어 `parser.next()` 가 "No Input specified" throw. callback.getParser 는 한 번도 호출 안 됨 (debug-print 로 verify).

**가설**: layoutlib `Resources_Delegate.getAnimation_Original(resources, id)` 의 implementation 이 `<selector>` root form (chip) 에서 callback hook 우회 path 를 타고, `<set>` root form (button) 에서는 callback path 를 탐. layoutlib jar 의 javap 로 정확한 path 확인 필요.

**Trigger**: 본 chip IT 가 정확히 본 LM 의 trigger.

**Cost**: plan v6 design 작성 (Resources_Delegate path 추적 후 구체 fix surface 식별). Round 7 Codex+Claude planning pair-review 필수 (CLAUDE.md §Codex). 가능한 fix:
- (A) Bridge-side hook 보강 — Resources_Delegate.getAnimation_Original 호출 시 callback.getParser 를 모든 root form 에서 시도 (layoutlib jar 패치 — 비현실적)
- (B) Worker-side mirror — `<selector>` root animator 를 별도 callback path (e.g. ResourceType.STATE_LIST_ANIMATOR 또는 INTERPOLATOR-style 분리) 로 처리. 단 ResourceType enum 에 stateListAnimator 가 없으므로 다른 hook 필요.
- (C) Pre-process stateListAnimator XML — `<selector>` root 를 `<set>` 으로 wrap. fixture-side 변환 또는 walker-side 변환.

**Coverage**: chip + 향후 다수 Material widget (Snackbar/Chip/MaterialSwitch 등 selector-root stateListAnimator 사용 widget 모두) 의 inflation 통과.

**Risk**: 큰 design — Round 7 pair-review 필수. layoutlib internal path 추적 후 minimum invasive fix surface 식별 필요.

### §2.2 W4-B-B — chip TextAppearance NPE (after W4-B-A close)

**Mechanism**: stateListAnimator 우회 (W4-B-A close 가 자연 활성화) 후 chip inflation 이 `?attr/textAppearanceLabelLarge` resolve 단계에서 `com.google.android.material.resources.TextAppearance.getTextSize()` NPE — TextAppearance instance == null.

**가설**: `?attr/textAppearanceLabelLarge` 가 LayoutlibRenderResources 의 chain walker 를 타고 resolve 되지만, 결과 ResourceValue 가 ChipDrawable.loadFromAttributes 가 expected `TextAppearance.create(context, styleId)` 형태로 consume 안 됨. style 의 attribute resolution 결과가 null styleId 또는 잘못된 type.

**Trigger**: W4-B-A close 후 chip IT advance 시 자연 surface.

**Cost**: 본 LM 단독 분석 — `findItemInTheme(textAppearanceLabelLarge)` → resolveResValue 의 chain walk 결과 trace + ChipDrawable.loadFromAttributes 의 정확한 TextAppearance.create signature 확인. 작은 fix (gate 1-line) 또는 큰 design (TextAppearance handling) 가능성.

**Coverage**: chip 의 textAppearance path 정상화 + 향후 Material widget 의 textAppearance attribute consumption 정상화.

**Risk**: 낮음 (LM-W4-B-A close 가 prerequisite — 단독 분석은 stateListAnimator override 가 필요).

### §2.3 candidate C / E (hold)

W4 entry handoff 의 candidate C (namespace-aware mode) + E (R$styleable layer) 그대로 hold. W4-B-A + W4-B-B close 후 자연 진입.

## §3 다음 세션 진입 단계

### §3.1 cold-read 권장 docs (이 순서)

1. **`docs/work_log/2026-05-07_w4-candidate-b-chip/handoff.md`** (본 파일)
2. `docs/work_log/2026-05-07_w4-candidate-b-chip/session-log.md` (W4-B drawable feed implementation 의 정확한 file diff + LM-W4-B-A/B 산출)
3. `docs/work_log/2026-05-07_w4-candidate-d-hardening/handoff-w4-d-close.md` (W4-D 의 close + W4 candidate 카탈로그)
4. `docs/work_log/2026-05-06_w4-entry/handoff-w4-entry.md` (W4 entry candidate A/B/C/D/E 의 spec)
5. `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (W3D4 phase 전체 escalation chain — 본 phase 의 background)
6. `docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md` (Codex+Claude pair-review 패턴 reference, W4-B-A 진입 시 Round 7 의 reference)

### §3.2 본 세션 첫 작업 = W4-B-A 진입

**user priority 결정 후** (W4-B-A vs W4-B-B 단독 vs hold):

#### path 1 — W4-B-A (chip stateListAnimator callback bypass, strong recommend)
1. layoutlib jar 의 `Resources_Delegate.getAnimation_Original` javap dump (`javap -p -c $LAYOUTLIB_JAR Resources_Delegate.class > /tmp/w4ba-resources-delegate.txt`)
2. `<selector>` root form 의 callback bypass 메커니즘 정확 식별 (file:line 인용 — LM-W3D4-δ-A spec verify 의무)
3. plan v6 design 작성 — `docs/superpowers/specs/2026-05-XX-w4-b-stateListAnimator.md` — 12-step transition + 가능 fix path (A/B/C 비교)
4. Round 7 Codex+Claude planning pair-review (LM-G + LM-W4-D-B 적용: codex sandbox bypass 직접 CLI, model 명시 회피)
5. deltas inline → plan v6.x → GO
6. Implementation
7. T17 + T20 regression guard + W4-D require throw guard 보장 필수
8. chip IT 의 @Disabled 제거 + LM-W4-B-A close
9. 자연 surface (W4-B-B) advance 시 그 phase 진입

#### path 2 — W4-B-B 단독 (W4-B-A 회피 — fixture 의 stateListAnimator override 유지로 우회)
1. activity_chip.xml 의 chip 에 `android:stateListAnimator="@null"` 추가 (fixture-side override)
2. chip IT 의 @Disabled 제거, 단 LM-W4-B-A 는 carry — 다른 selector-root animator widget 진입 시 다시 surface
3. textAppearanceLabelLarge resolve path trace + TextAppearance.create signature 확인
4. fix (gate 1-line 또는 작은 design)
5. 1 commit close — Material widget extension 의 visual fidelity 부분 진전

본 path 2 는 **W4-B-A 의 carry 를 유지** — 단기 가치 (chip IT green) 와 장기 부채 (selector-root animator 의 일반 처리 미완성) 의 trade-off. 권장 path 1 (W4-B-A 정면 돌파) — 하지만 빠른 visual fidelity 우선 시 path 2 도 선택 가능.

## §4 회피해야 할 LM (combined 24 — W3D4 phase 산출 19 + W4-D 신규 2 + W4-B 신규 3)

| LM | 1-line 핵심 |
|---|---|
| LM-W3D4-β-D~H | KDoc backtick + assertNotNull 금지 + IT-tag + subagent unzip /tmp + acceptance fail surface 분류 |
| LM-W3D4-γ-A~C | reviewer prompt + Long.decode + ThemeEnforcement multi-sentinel |
| LM-W3D4-δ-A | spec 코드 sample 의 모든 식별자 grep/Read 사전 verify |
| LM-W3D4-δ-B | NsBucket type-specific map 분리 sibling KILL POINT |
| LM-W3D4-δ-C | runtime-classpath.txt 부재 silent empty AAR — **W4-D close ✓** |
| LM-W3D4-δ-D | IT-tagged RED-on-main 가능 (default unit suite 제외) |
| LM-W3D4-δ-E | acceptance fail surface 의 stack-trace entry-point 비교로 layer shift 식별 |
| LM-W3D4-δ-F | CLAUDE.md Rule 2 OUT-OF-SCOPE 확대 — phase/task identifier 회피 |
| LM-W3D4-δ-G | annotation message 안 future option 의 technical claim 의 file:line 사전 verify |
| LM-W3D4-δ-H | chain-walker IT (T17/T20-style probe) PASS 가 render-time path PASS 보장 안 함 |
| LM-W3D4-δ-I | RenderResources.applyStyle override 시 clear() 호출 금지 |
| LM (CLAUDE.md Three Hard Rules) | 모든 신규 코드 KDoc/inline 영문 only, function/structure only |
| LM-G | codex sandbox bypass 직접 CLI (planning phase 에만) |
| LM-W4-D-A | reviewer agent 가 신규 string literal + KDoc 의 modification-history phrasing 을 catch |
| LM-W4-D-B | Codex CLI 의 explicit `--model` flag 회피 (default model + reasoning effort 만) |
| **LM-W4-B-A (신규)** | chip stateListAnimator (@animator/m3_chip_state_list_anim) 가 callback.getParser 호출 없이 fail. button (@animator/m3_btn_state_list_anim) 은 정상. 차이는 root form (`<selector>` vs `<set>`). layoutlib `Resources_Delegate.getAnimation_Original` 의 root form 별 path 추적 + worker-side hook 보강 필요. plan v6 + Round 7 pair-review 영역. |
| **LM-W4-B-B (신규)** | chip 의 stateListAnimator @null 시 textAppearance path 노출 — `?attr/textAppearanceLabelLarge` resolve 결과가 ChipDrawable.loadFromAttributes 의 TextAppearance instance==null 야기. style attr resolution 의 type 정합 가능성. W4-B-A close 후 진입 권장. |
| **LM-W4-B-C (신규)** | subagent (feature-dev:code-explorer) 의 가설 propagation 위험 — "@integer/m3_chip_anim_duration 누락" 가설이 검증 없이 받아들여질 뻔. Material 1.12.0 values.xml 직접 grep 으로 verify 후 reject. LM-W3D4-δ-A 의 subagent summary 영역 확장 — subagent의 결론은 hypothesis, file:line read 가 ground truth. |
| **LM-W4-B-D (신규)** | Kotlin helper function 에 default-arg 추가 시 trailing-lambda 호출 패턴 깨짐. 새 default-param 이 마지막 position 이면 trailing lambda binding 이 그 자리로 redirect — 첫 param 의 lookup missing. 두 form 의 helper (1-arg + 2-arg) 분리 권장. |

## §5 출발 지점 환경 sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
(다음 commit) feat(w4-b): drawable XML feed mirror — candidate A natural trigger close + chip IT parked
7c879fc docs(w4-d): handoff + next-session-prompt for W4-D close — next candidate B/C/A/E decision
8150212 feat(w4-d): runtime-classpath.txt hardening — require() guard mirroring loadFramework
2afe300 docs(w4-entry): handoff + next-session-prompt for W4 phase scope decision
9ea2afd feat(w4-entry): tier3-glyph un-disable — TextView dark-pixel render already passes after δ-C/δ-D fixes

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 253 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
... 26 PASS + 1 SKIP ...
BUILD SUCCESSFUL — 26 IT PASS + 1 SKIP (tier3 chip — @Disabled).
```

regression guards: handoff §5 (W4-D handoff) 그대로.

debug toggles (선택):
- `-Daxp.debug.bundleShape=true` (LayoutlibResourceValueLoader bootstrap 진단)
- `-Daxp.debug.callback=true` (BridgeContext callback layer instrumentation, plan v3.2 §5.3.1)

## §6 W4 phase status

- W3D4-δ-A~D: **CLOSED ✓**
- tier3-glyph: **CLOSED ✓**
- W4-D (runtime-classpath.txt hardening): **CLOSED ✓**
- W4-A (drawable XML feed mirror): **CLOSED ✓** (candidate B 자연 trigger)
- W4-B-drawable: **CLOSED ✓** (foundation 제공)
- W4-B-A (chip stateListAnimator callback bypass): **OPEN — 다음 phase entry**
- W4-B-B (chip TextAppearance NPE): **OPEN — W4-B-A close 후**
- W4-C (namespace-aware mode): hold
- W4-E (R$styleable layer): hold

다음 세션 = W4-B-A 진입. plan v6 design 작성 + Round 7 Codex+Claude pair-review.
