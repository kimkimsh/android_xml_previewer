# W4 candidate B — chip fixture entry + drawable XML feed mirror (session log)

날짜: 2026-05-07
선행 head: `7c879fc` (main, W4-D close handoff + next-session-prompt)
phase: W4 — visual fidelity 확장 (candidate B 진입, candidate A 자연 trigger close)

---

## Outcome

candidate B (widget fixture 확장) 진입의 첫 단계로 single-Chip fixture (`activity_chip.xml`) + Tier3 IT 추가. 첫 측정에서 `XmlPullParserException: No Input specified (position:START_DOCUMENT null@0:0)` 의 inflation 실패가 자연스럽게 candidate A (DRAWABLE selector XML feed mirror) 의 trigger 로 동작 — Base.Widget.Material3.Chip style 의 `<item name="checkedIcon">@drawable/ic_m3_chip_checked_circle</item>` + `<item name="closeIcon">@drawable/ic_m3_chip_close</item>` 가 inflation 시점에 즉시 lookup 되는데 bundle 의 AarResourceWalker 가 `res/drawable/*.xml` 을 enumerate 하지 않아 발생. W3D4-δ-D animator pattern 의 1:1 mirror 로 6 main 파일 구현 + 2 신규 unit test 추가. 7 file 의 callback 생성 site 에 5번째 `drawableXmlLookup` 인자 추가.

drawable feed 적용 후 chip 의 inflation 단계가 advance 했으나 두 깊은 layer 가 새로 surface — (1) chip 의 `android:stateListAnimator="@animator/m3_chip_state_list_anim"` 가 callback.getParser 를 한 번도 호출하지 않은 채 `BridgeXmlBlockParser.next` 에서 No Input specified 로 fail (bundle 에 m3_chip_state_list_anim 등록 확인됨, 그러나 layoutlib 의 Resources_Delegate.getAnimation_Original path 가 `<selector>` root form 에서 callback 을 우회하는 것으로 보임. MaterialButton 의 `<set>` root m3_btn_state_list_anim 은 정상 working — basic IT PASS). (2) stateListAnimator 를 `@null` override 시 진단 path 가 advance 하나 `com.google.android.material.resources.TextAppearance.getTextSize()` NPE — `?attr/textAppearanceLabelLarge` 가 ChipDrawable.loadFromAttributes 가 consume 하지 못하는 형태로 resolve.

본 두 layer 는 다음 phase (W4-B-A) 의 design plan + Round 7 Codex+Claude pair-review 영역. chip IT 는 `@Disabled` + LM-W4-B-A reference 로 park, fixture XML 은 다음 phase 의 entry point 로 in-tree 보존. drawable feed mirror 는 **본 phase 의 closeable + valuable**: candidate A (LM-W3D4-β-H 시점의 hold) 자연 trigger close + 향후 chip-style widget extension 의 foundation.

LM-W3D4-δ-A (spec verify 의무) 적용: subagent 의 "@integer/m3_chip_anim_duration 누락" 가설은 Material 1.12.0 values.xml 직접 grep 으로 검증 — `<integer name="m3_chip_anim_duration">100</integer>` 존재 확인 → 가설 reject. 실 cause 는 callback bypass 가설 (검증 path) 로 전환.

---

## Files modified

### main src (drawable XML feed — 6 file mirror of W3D4-δ-D animator pattern)
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstants.kt` — `AAR_DRAWABLE_DIR_PREFIX = "res/drawable/"` + `DRAWABLE_PLACEHOLDER_VALUE = "@axp:drawable-xml"` 신규 상수.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedNsEntry.kt` — `data class DrawableXml(name, rawXml, namespace, sourcePackage)` 신규 (sibling to AnimatorXml).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalker.kt` — `collectDrawableXmls(zip, pkg)` 신규 + walkOne 의 부분-이용 판정에 drawableEntries 포함 + walkAll 진단 카운트에 `132 drawable-xmls` 추가. skip 진단 메시지의 한글 phrase ("모두 없음") 영문화 ("all absent") — Rule 1 refactor-on-touch.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NsBucket.kt` — `val drawables: Map<String, String> = emptyMap()` field 추가 + `EMPTY` 인스턴스 6번째 인자 갱신.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt` — buildBucket 의 DrawableXml 분기 (animator pattern 1:1 mirror) + `getDrawableXml(ref)` lookup + `drawableXmlCountForNamespace(ns)` count helper + NsBucket 생성 호출에 drawables 인자.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` — 5번째 ctor 인자 `drawableXmlLookup: (ResourceReference) -> String?` 추가 + getParser 의 ResourceType.DRAWABLE 분기 추가.

### main src (callback wiring)
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:198-203` — MinimalLayoutlibCallback 생성 시 5번째 인자 `{ ref -> bundle.getDrawableXml(ref) }` wiring.

### test src (drawable feed unit tests — 2 신규 + callback site update)
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerDrawableTest.kt` — 신규 (AarResourceWalkerColorTest mirror): 4 cases (default qualifier emit, qualifier dir skip, drawable-only AAR partial use, code-only AAR null).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleDrawableTest.kt` — 신규 (LayoutlibResourceBundleColorStateListTest mirror): 6 cases (lookup hit, unknown name, wrong ns, byType placeholder, dup first-wins + log, count helper).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt` — 8 callback 생성 site 에 5번째 `{ null }` 인자 추가.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt` — 2 callback 생성 site 에 5번째 `{ null }` 인자.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackLoadViewTest.kt` — newCallback helper 의 5번째 인자.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackInitializerTest.kt` — 3 callback 생성 site 에 5번째 인자.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackColorParserTest.kt` — newCallback helper + 신규 newCallbackWithDrawable helper, 기존 "DRAWABLE → null (T12_5 escalation 대상)" 테스트를 새 의미로 rename ("DRAWABLE type + drawable lookup miss returns null") + sibling 신규 ("DRAWABLE type + drawable lookup hit feeds raw XML") 추가.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerTest.kt:43-45` — skip 진단 메시지 assert 의 "모두 없음" → "all absent" 갱신 (Rule 1 refactor-on-touch).

### fixture (chip widget entry)
- `fixture/sample-app/app/src/main/res/layout/activity_chip.xml` — 신규 (single Chip widget, ConstraintLayout root).
- `fixture/sample-app/app/src/main/res/values/strings.xml` — `fixture_chip_label = "Assist"` 신규 string.

### test src (chip IT — Disabled park)
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` — `tier3 chip — activity_chip renders SUCCESS via primary path` 신규 IT + `@Disabled("W4-B-A: ...")` annotation. KDoc 에 두 carry layer (stateListAnimator callback bypass + TextAppearance NPE) 명시.

총 17 files (8 main + 8 test + 1 doc) / ~280 insertions / ~5 deletions.

## Test results

| 지표 | baseline (post W4-D) | post-phase |
|---|---|---|
| 모듈 합산 unit | 242 PASS / 0 fail | **253 PASS / 0 fail** (+11) |
| layoutlib-worker IT | 26 PASS + 0 SKIP | **26 PASS + 1 SKIP** (chip @Disabled) |
| `AarResourceWalkerDrawableTest` | n/a | **4 cases / 4 PASS** |
| `LayoutlibResourceBundleDrawableTest` | n/a | **6 cases / 6 PASS** |
| `MinimalLayoutlibCallbackColorParserTest` | 7 cases | **8 cases** (rename + sibling) |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| T20 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| acceptance gate `activity_basic` | PASS | PASS (불변) |
| `activity_minimal` glyph dark-pixel | PASS | PASS (불변) |
| `MaterialFidelityIntegrationTest` | 4/4 PASS | 4/4 PASS (불변) |

walker stats (post-phase): `walked 41 AARs (13 with res, 28 code-only, 191 color-state-lists, 32 animator-xmls, 132 drawable-xmls) in 36ms`.

## Landmines discovered + 해결

| LM | 내용 | 해결 |
|---|---|---|
| (carry close) candidate A trigger | candidate B (chip fixture) 의 첫 measurement 가 candidate A (drawable XML feed) 의 자연 trigger 로 동작. handoff §2.1 의 "trigger 없으므로 hold 권장" 가 단 fixture 추가 1단계 만에 자연 발생. | drawable feed mirror 적용으로 close. 향후 widget extension 의 foundation. |
| LM-W4-B-A (신규) | chip 의 `android:stateListAnimator="@animator/m3_chip_state_list_anim"` 이 callback.getParser 를 호출하지 않은 채 fail (No Input specified). bundle 의 animators map 에 m3_chip_state_list_anim 등록 확인 (debugListAnimators probe). 동일 path 의 MaterialButton m3_btn_state_list_anim 은 정상 working (basic IT PASS — getParser 정상 호출됨). 차이점: chip animator XML 의 root 가 `<selector>` (직접 state list animator), button 은 `<set>` 안 `<selector>` (래핑 형태). layoutlib `Resources_Delegate.getAnimation_Original` 가 root form 에 따라 callback 우회 path 를 타는 것으로 추정. | 본 phase 미해결. chip IT @Disabled 으로 park. 다음 phase entry — Resources_Delegate.getAnimation_Original 의 정확한 implementation trace + state list animator 의 callback hook path 보강 (또는 별도 selector-root animator 처리 mirror). |
| LM-W4-B-B (신규) | chip 의 stateListAnimator 를 `@null` override 시 inflation path 가 advance — 다양한 color state list lookup (m3_chip_assist_text_color, m3_ref_palette_*) 가 정상 callback.getParser → SelectorXmlPullParser feed. 그러나 `com.google.android.material.resources.TextAppearance.getTextSize()` NPE 로 fail — `?attr/textAppearanceLabelLarge` 의 resolve 결과가 ChipDrawable.loadFromAttributes 가 expected TextAppearance 형태로 consume 하지 못함. textAppearance instance == null. | 본 phase 미해결. LM-W4-B-A close 후 진입 가능. textAppearance attr → style chain 의 정확한 resolve path 추적 + ChipDrawable's TextAppearance.create(resolved style) 이 null 반환하는 trigger 조건 식별. |
| LM-W4-B-C (신규) | subagent 의 "@integer/m3_chip_anim_duration 누락" 가설이 검증 없이 propagate 위험. Material 1.12.0 values.xml 직접 grep 으로 verify → 정의 존재 확인 → reject. LM-W3D4-δ-A (spec verify 의무) 의 subagent summary 영역 적용. | 본 session 에서 자체 catch — 가설 검증 우선 + 직접 file:line read 의무 재확인. |
| LM-W4-B-D (신규) | `MinimalLayoutlibCallbackColorParserTest` 의 helper `newCallback(lookup) { ... }` trailing-lambda 패턴이 default-arg 추가 시 깨짐 — 새 default param 이 *마지막* position 이면 trailing lambda 가 그 자리로 binding 되어 첫 param 의 lookup 가 missing 됨. | helper 를 두 form 으로 분리: 단일-arg 버전 (lookup 만, drawable={null}) + 두-arg 버전 (colorLookup, drawableLookup). default-arg + trailing-lambda 의 cross-cutting 회귀 패턴 확인. |

## Canonical document changes

본 phase 는 `docs/superpowers/specs/` 또는 `docs/plan/` 변경 없음. drawable feed 가 W3D4-δ-D animator pattern 의 mirror — 큰 design decision 아님 (1:1 mirror). plan 작성 trigger 미충족. 향후 LM-W4-B-A 진입 phase 는 plan v6 작성 가능 (Resources_Delegate 의 정확한 `<selector>` root 처리 path 추적 후 design 결정).

W3D4 phase 의 plan v3.2 (`docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md`) 의 §11 out-of-scope 의 LM-W3D4-β-H entry-point comparison 적용 — 본 chip 의 fail surface 분류에 stack-trace entry-point (`AnimatorInflater.createStateListAnimatorFromXml:215` vs basic 의 `MaterialButton.<init>` callback hit) 비교로 layer shift 정확 식별.

## What's blocking / carried forward

본 phase close 후 **active fail surface = chip IT @Disabled (1 SKIP)**. 다음 candidate 결정 시점:

| candidate | 상태 | 권장 trigger |
|---|---|---|
| **W4-B-A** (chip stateListAnimator callback bypass) | open, **strong recommend** | LM-W4-B-A 의 root cause 분석 — Resources_Delegate.getAnimation_Original 의 정확 path 추적. layoutlib jar 의 javap 로 dump 후 `<selector>` root form 에서의 callback bypass 메커니즘 식별. plan v6 design + Round 7 Codex+Claude pair-review (planning ONLY). |
| **W4-B-B** (chip TextAppearance NPE) | open, **W4-B-A 후 진입** | textAppearanceLabelLarge → ChipDrawable.loadFromAttributes path 의 TextAppearance.create() 가 null 반환하는 trigger 조건. attr resolve 의 type 정합 (style vs item) 가능성. |
| C (namespace-aware mode) | hold | active fail 없음 — design 정합 priority 시. 큰 refactor — Round 7 pair-review 필수. |
| E (R$styleable layer) | hold | future Material widget styled-attrs lookup fail 시 escalate. |
| 그 외 widget (TextInputLayout / MaterialSwitch / Snackbar / Badge) | hold | W4-B-A + W4-B-B close 후 자연 진입. 각 widget 의 자체 LM 발견 가능성. |

## Pair review verdicts

본 phase 는 implementation phase — drawable feed mirror 는 W3D4-δ-D animator pattern 의 1:1 mirror 로 큰 design decision 아니므로 강제 pair-review 미요구 (CLAUDE.md §Codex). 단 dual-source review 는 reviewer subagent + commit 후 진행 가능 — 본 session-log 작성 후 commit 단계에서 추가 평가.

## Commits + push

- (다음 commit) `feat(w4-b): drawable XML feed mirror — candidate A natural trigger close + chip IT parked for W4-B-A` — 본 session-log + 17 file diff 동시.

CLAUDE.md task-unit completion 규약 준수 — push 즉시 진행.
