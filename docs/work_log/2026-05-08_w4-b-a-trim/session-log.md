# W4 candidate B-A — chip stateListAnimator trim fix (session log)

날짜: 2026-05-08
선행 head: `bf21dac` (W4-B drawable feed mirror — candidate A natural trigger close + chip IT parked for W4-B-A)
phase: W4 — candidate B-A (chip stateListAnimator callback bypass)

---

## Outcome

LM-W4-B-A 의 root cause 가 handoff 의 hypothesis (`<selector>` vs `<set>` root form 으로 인한 layoutlib `Resources_Delegate.getAnimation_Original` 의 분기) 가 아닌 **NamespaceAwareValueParser 의 style item body whitespace preservation** 으로 empirically 식별. 1-line `.trim()` fix 로 close. Round 7 Codex+Claude planning pair-review + plan v6 design 작성 budget 가 empirical probing 으로 단축됨.

bytecode-level analysis (layoutlib-14.0.11.jar disasm) + runtime-level probe 가 hypothesis 검증의 core. Material 1.12.0 의 `Base.Widget.Material3.Chip` style 안 `<item name="android:stateListAnimator">` 가 multi-line whitespace 형식 (`<item ...>\n      @animator/m3_chip_state_list_anim\n    </item>`), button 의 `Widget.Material3.Button` style 안 `<item name="android:stateListAnimator">@animator/m3_btn_state_list_anim</item>` 는 single-line. 우리 `NamespaceAwareValueParser.handleStyle:184` 의 `readElementText(reader)` 가 trim 없이 raw whitespace 보존 → StyleItem.value 가 `"\n      @animator/m3_chip_state_list_anim\n    "` 형태로 layoutlib 에 전달 → `BridgeContext.getXmlBlockParser` 의 @ref 토큰 매칭 fail → `value.isFramework()` 또는 callback null 분기에서 `ParserFactory.create(value.getValue())` fallback → 우리 `MinimalLayoutlibCallback.createXmlParserForFile` 의 blank `KXmlParser` 반환 (no `setInput`) → `parser.next()` 가 "No Input specified" throw.

**chip IT 의 @Disabled 는 유지** — 직전 fail layer (W4-B-A) 가 close 되자 자연스럽게 다음 layer (W4-B-B: ChipDrawable.loadFromAttributes 의 TextAppearance NPE) 가 surface. handoff 의 예측대로. @Disabled message 와 KDoc 을 W4-B-B-only 로 갱신 + 직전 stateListAnimator 분석은 KDoc 의 closing paragraph 에 historical reference 로 보존 (CLAUDE.md Rule 2 OUT-OF-SCOPE 영향 — phase/task identifier 회피하면서 structural transition 의 "what is" 명시).

dual-source review (feature-dev:code-reviewer + Codex direct CLI Q1-Q5+Q6 open critique) 의 결과는 round 별 sub-section 에 기록.

---

## Files modified

### main src (1 file, 1-line behavior change)
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParser.kt:184` — `readElementText(reader)` → `readElementText(reader).trim()` (`handleStyle` 의 `<style><item>` body 만, top-level `<item>` 과 `handleSimpleValue` 는 영향 없음). 신규 inline KDoc 가 contract 를 명시: style item bodies 는 reference / literal payloads (display text 아님) → AAPT2 normalised form 에 정합.

### test src (2 신규 unit case)
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParserTest.kt` — 2 신규 case:
  - `style item with multi-line whitespace content is trimmed to a clean reference token` — Material1.12.0 의 chip pattern 직접 simulation. `@animator/m3_chip_state_list_anim` 와 `?attr/textAppearanceLabelLarge` (single-line baseline 동시 검증) 양쪽 trim post-state assert.
  - `string resource preserves intentional leading and trailing whitespace` — `<string>` content 의 whitespace preservation 강화 (i18n placeholder 정합). trim contract 가 style item only 임을 lock-in.

### test src (chip IT @Disabled 유지 + KDoc 갱신)
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:84-104` — KDoc 재작성 (W4-B-A historical 부분 + W4-B-B 의 정확한 fail surface — `MaterialResources.getTextAppearance` 의 `typedArray.getResourceId` 0 반환 시 null TextAppearance) + `@Disabled` 메시지 W4-B-B-only 갱신.

총 3 files / 5 insertions (main) + 53 insertions (test) — 1-line behavior change + 2 unit test + KDoc/annotation.

## Test results

| 지표 | baseline (post W4-B drawable) | post-phase |
|---|---|---|
| 모듈 합산 unit | 253 PASS / 0 fail | **255 PASS / 0 fail** (+2: trim contract + string preservation contract) |
| layoutlib-worker IT | 26 PASS + 1 SKIP (chip @Disabled) | **26 PASS + 1 SKIP** (chip @Disabled W4-B-B-only) |
| `NamespaceAwareValueParserTest` | 12 cases | **14 cases** |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| T20 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| acceptance gate `activity_basic` | PASS | PASS (불변) |
| `activity_minimal` glyph dark-pixel | PASS | PASS (불변) |
| `MaterialFidelityIntegrationTest` | 4/4 PASS | 4/4 PASS (불변) |

empirical chip-IT progression evidence (post-fix probe run with temp instrumentation, 후 revert):
- before fix: 0 ANIMATOR callback hits for `m3_chip_state_list_anim` (32 callback calls total, only 1 ANIMATOR for `m3_btn_state_list_anim`)
- after fix: 1 ANIMATOR callback hit for `m3_chip_state_list_anim` (xml.size=2173) + 4+ chip-specific COLOR callback hits (`m3_chip_assist_text_color`, `m3_chip_background_color`, `m3_chip_stroke_color`, `m3_chip_ripple_color`)
- chip render advances past stateListAnimator inflation, surfaces W4-B-B (TextAppearance NPE) — predicted

## Landmines discovered + 해결

| LM | 내용 | 해결 |
|---|---|---|
| **LM-W4-B-A close — empirical investigation overrides handoff hypothesis** | handoff §2.1 의 "Resources_Delegate.getAnimation_Original 의 root form 별 path 추적" 가설은 layoutlib bytecode 직접 dump 로 reject. `Resources_Delegate.getAnimation` 은 root form 무관하게 단일 path (`getResourceValue` → `ResourceHelper.getXmlBlockParser`) 사용. `getXmlBlockParser` 는 non-framework value 에 대해 항상 `LayoutlibCallback.getParser` 호출. 실 cause 는 우리 parser 의 whitespace handling. | bytecode disasm + runtime probe 의 dual evidence 로 hypothesis 정확 reject + 실 cause 식별. plan v6 design + Round 7 pair-review budget 가 empirical probing 으로 단축. |
| **LM-W4-B-E (신규)** | handoff hypothesis 는 hypothesis. file:line + bytecode + runtime probe 의 ground-truth verify 가 plan-writing 의 prerequisite. 본 case 는 LM-W3D4-δ-A (spec verify 의무) + LM-W4-B-C (subagent 결론 hypothesis) 의 자연스런 escalation — handoff document 자체도 hypothesis source. plan-writing 전 empirical probe 가 plan 의 fact-claim 정확도 보장. | LM-W4-B-C 의 영역을 handoff document 로 확대. 향후 multi-day plan v6 design 진입 전 60-min empirical probe budget 고려. |
| **LM-W4-B-F (신규)** | NamespaceAwareValueParser.parseInternal 의 line 129 (top-level `<item type="X" name="Y">Z</item>`) 가 `readElementText(reader)` 를 trim 없이 사용. 본 phase 는 style item body 만 trim — top-level `<item type=...>` 의 trim 은 미적용. Material AAR 안 multi-line top-level `<item type="...">` 사용 site 가 활성화되면 동일 회귀 가능. 현재 active fail 없음 — 본 carry. | 향후 top-level `<item type=...>` 의 multi-line value site 발견 시 동일 trim 정책 적용. dual-source review 의 deltas 로 carry 가능. |

## Canonical document changes

본 phase 는 `docs/superpowers/specs/` 또는 `docs/plan/` 변경 없음. 1-line fix + 2 unit test + KDoc 갱신 — 큰 design decision 아님. plan v6 작성 trigger 미충족 — empirical probing 결과 hypothesis reject 가 design 비용 단축.

W3D4 phase 의 plan v3.2 (`docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md`) 의 §11 out-of-scope 의 LM-W3D4-β-H entry-point comparison + LM-W3D4-δ-A (subagent summary verify) 의 자연 적용 — handoff document 자체를 hypothesis source 로 취급.

## What's blocking / carried forward

본 phase close 후 **active fail surface = chip IT @Disabled (1 SKIP, W4-B-B-only)**. 다음 candidate:

| candidate | 상태 | 권장 trigger |
|---|---|---|
| **W4-B-B** (chip TextAppearance NPE) | open, **다음 phase 권장** | `MaterialResources.getTextAppearance(context, typedArray, index)` 가 null 반환 — `typedArray.hasValue(index)` 또는 `typedArray.getResourceId(index, 0)` 의 0 반환. `?attr/textAppearanceLabelLarge` chain 이 `@style/TextAppearance.Material3.LabelLarge` 의 int resource ID 로 mapping 안 됨. RJarSymbolSeeder 가 Material AAR 의 `R$style.TextAppearance_Material3_LabelLarge` 를 `TextAppearance.Material3.LabelLarge` 로 register 하는지 + LayoutlibRenderResources 의 chain walker 가 STYLE ResourceValue 를 int ID 로 reverse lookup 하는지 검증 필요. |
| C (namespace-aware mode) | hold | active fail 없음 — design 정합 priority 시. |
| E (R$styleable layer) | hold | future Material widget styled-attrs lookup fail 시 escalate. |
| 그 외 widget (TextInputLayout / MaterialSwitch / Snackbar / Badge) | hold | W4-B-B close 후 자연 진입. |

## Pair review verdicts

본 phase 는 implementation phase — Round 7 pair-review 미요구 (CLAUDE.md §Codex). 단 fix 가 parser-layer change 로 ripple 가능 (모든 style item body trim) — dual-source single-shot review (Claude reviewer subagent + Codex direct CLI sanity-check) 진행. Q1-Q5 + Q6 open critique slot.

(dual-source review 결과는 commit 직전 finalize.)

## Commits + push

- (다음 commit) `feat(w4-b-a): style item body trim — chip stateListAnimator callback now wires correctly` — 본 session-log + 3 file diff 동시. CLAUDE.md task-unit completion 규약 준수 — push 즉시 진행.
