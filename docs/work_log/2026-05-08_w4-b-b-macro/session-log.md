# W4 candidate B-B — chip TextAppearance via macro indirection (session log)

날짜: 2026-05-08
선행 head: `c126512` (W4-B-A close: style item body trim)
phase: W4 — candidate B-B (chip TextAppearance NPE — root cause = `<macro>` element 미파싱)

---

## Outcome

LM-W4-B-B 의 root cause 가 handoff 의 path α (RJarSymbolSeeder dot-name canonicalization) / path β (chain walker reverse-map) / path γ (BridgeContext RES_AUTO ↔ ANDROID 전이) 모두 아니었다 — 실 cause 는 **`<macro>` element 미파싱**. Material 3 이 design-token indirection layer 로 추가한 AAPT2 resource type 이며, `Widget.Material3.Chip.Assist` 의 `android:textAppearance` 가 `@macro/m3_comp_assist_chip_label_text_type` 으로 정의 (다시 macro body 가 `?attr/textAppearanceLabelLarge`). 우리 `NamespaceAwareValueParser` 가 `<macro>` tag 를 unknown top-level 로 skipElement → bundle 에 macro 항목 없음 → chain walker 가 `@macro/...` ref 를 resolve 못 함 → StyleItem 이 literal `@macro/...` string 보존 → BridgeTypedArray.getResourceId 가 0 반환 → MaterialResources.getTextAppearance null → ChipDrawable.getTextSize NPE.

LM-W4-B-E (handoff hypothesis 도 hypothesis) 정확히 trigger — handoff 의 3가지 path 모두 reject. empirical probe (LayoutlibRenderResources.findItemInTheme + resolveResValue + MinimalLayoutlibCallback.getOrGenerateResourceId 의 textAppearance-narrowed log) 가 핵심: chip IT 의 stack 안 `name=textAppearance value=@macro/m3_comp_assist_chip_label_text_type type=item` 패턴 발견 → values.xml grep 으로 macro 정의 593 건 census → ResourceType.MACRO 존재 verify (layoutlib-api 31.13.2 의 ResourceType enum 안 32번째 entry).

**fix**: NamespaceAwareValueParser 의 parseInternal 분기에 `TAG_MACRO ("macro")` 추가 + `handleMacro` helper (style item body trim 정책 동일 적용 — macro body 도 reference token, display text 아님). chain walker 는 기존 `getUnresolvedResource(MACRO ref)` 경로로 macro body 가 substituted ResourceValue 반환 → 다음 chain hop 으로 자연 진행.

post-fix chip IT 의 새 fail surface = **W4-B-C** — `Resources$NotFoundException: Could not find drawable resource matching value 0x7F070076 (resolved name: abc_vector_test) in current configuration`. AppCompat 의 `R.drawable.abc_vector_test` (appcompat-resources-1.6.1.aar 안 res/drawable/abc_vector_test.xml, 1028 bytes) 를 layoutlib 가 자체 resolution path 로 찾지 못함 — `MinimalLayoutlibCallback.getParser` 의 DRAWABLE 분기가 호출 안 됨 (probe 로 verify). chip IT 는 W4-B-C 의 새 message 로 @Disabled — 다음 phase entry.

dual-source review (Claude reviewer subagent + Codex direct CLI sanity-check) 진행 — commit 직전 finalize.

---

## Files modified

### main src (1 file, +`<macro>` element parsing)
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParser.kt` — `TAG_MACRO = "macro"` constant + parseInternal 의 START_ELEMENT switch 안 `TAG_MACRO ->` 분기 + 신규 `handleMacro` helper. handleMacro 는 macro body 를 `readElementText(reader).trim()` 으로 capture 하고 ParsedNsEntry.SimpleValue(ResourceType.MACRO, name, body, namespace, sourcePackage) emit. 신규 helper KDoc 에 macro 의 design-token indirection rationale + chain walker contract + ChipDrawable 의 strict consumer 가 trigger 임을 structural-only 영문 으로 기술.

### test src (1 신규 unit case)
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParserTest.kt` — `macro element is parsed as ResourceType MACRO with trimmed body` case. Material 1.12.0 의 single-line + multi-line macro 두 form 동시 검증 + ResourceType.MACRO assert + body trim contract assert.

### test src (chip IT @Disabled 갱신 — W4-B-C-only)
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` — KDoc 재작성 (W4-B-A trim + W4-B-B macro 의 close + W4-B-C 의 정확한 fail surface — `abc_vector_test` 의 resource id 0x7F070076, AAR 위치, layoutlib path 가 callback 우회 empirical evidence) + `@Disabled` 메시지 W4-B-C-only 갱신.

### work_log 신규 folder
- `docs/work_log/2026-05-08_w4-b-b-macro/` (Pattern 3 — 새 milestone) — session-log.md (본 파일) + handoff.md + next-session-prompt.md.

총 3 main/test files + 1 work_log folder / +1 main behavior change (TAG_MACRO 분기) + +1 unit test + KDoc/annotation.

## Test results

| 지표 | post-W4-B-A | post-W4-B-B |
|---|---|---|
| 모듈 합산 unit | 255 PASS / 0 fail | **256 PASS / 0 fail** (+1: macro contract test) |
| layoutlib-worker IT | 26 PASS + 1 SKIP (chip @Disabled W4-B-A/B) | **26 PASS + 1 SKIP** (chip @Disabled W4-B-C-only) |
| `NamespaceAwareValueParserTest` | 14 cases | **15 cases** |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| T20 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| acceptance gate `activity_basic` | PASS | PASS (불변) |
| `activity_minimal` glyph dark-pixel | PASS | PASS (불변) |
| `MaterialFidelityIntegrationTest` | 4/4 PASS | 4/4 PASS (불변) |

empirical chip-IT progression evidence (post-fix, temp probe instrumentation 후 revert):

before macro fix:
- chip IT failure = `ERROR_INFLATION msg=Cannot invoke "TextAppearance.getTextSize()" because "textAppearance" is null exc=NullPointerException`
- chip IT 의 textAppearance lookup 안 `findItemInTheme: attr=textAppearance ns=android` 가 `@macro/m3_comp_assist_chip_label_text_type` 반환 — chain walker 가 macro 를 resolve 못 해 literal string 으로 보존

after macro fix:
- chip IT failure = `ERROR_INFLATION msg=Could not find drawable resource matching value 0x7F070076 (resolved name: abc_vector_test) in current configuration. exc=NotFoundException`
- chip IT 의 textAppearance path 정상 — `MaterialResources.getTextAppearance` 가 valid TextAppearance 인스턴스 반환 + `ChipDrawable.loadFromAttributes` 의 다음 step (drawable resolution) 으로 advance

## Landmines discovered + 해결

| LM | 내용 | 해결 |
|---|---|---|
| **LM-W4-B-B close — empirical investigation overrides handoff path α/β/γ** | handoff §2.4 의 path α (RJarSymbolSeeder dot-name) / path β (chain walker reverse-map) / path γ (BridgeContext RES_AUTO ↔ ANDROID) 모두 reject. 실 cause = `<macro>` element 미파싱 (Material 3 design-token indirection). LayoutlibRenderResources.findItemInTheme + resolveResValue + MinimalLayoutlibCallback.getOrGenerateResourceId 의 textAppearance-narrowed probe 가 `value=@macro/...` 패턴 노출 → values.xml grep 으로 593 macro definitions 발견 → ResourceType.MACRO existence verify (layoutlib-api). | empirical probe + bytecode evidence 로 hypothesis 양쪽 reject + 실 cause 식별. LM-W4-B-E 의 강한 vindication — handoff hypothesis 가 plan-writing 의 ground truth 아님. |
| **LM-W4-B-G (신규)** | `<macro>` 는 AAPT2 resource type (Material 3 introduce). values.xml 의 593 macro definitions 가 design-token aliases (e.g. `@macro/m3_comp_assist_chip_label_text_type → ?attr/textAppearanceLabelLarge`). NamespaceAwareValueParser 가 unknown top-level element 를 skipElement 처리 → silent feature gap. ResourceType.MACRO 가 layoutlib-api 31.13.2 안 enum 에 존재 (32번째 entry — `com.android.resources.ResourceType.MACRO`). chain walker 의 `getUnresolvedResource(MACRO ref)` 경로 자동 활용 가능 — Material 3 widget 의 strict consumer (ChipDrawable etc.) 가 trigger. | TAG_MACRO 분기 + handleMacro helper 추가. silent feature gap 패턴 — unknown XML resource type 이 sniffless skip 되면 downstream strict consumer 에서만 surface. 향후 Material 4+ 가 새 resource type 추가 시 동일 catch 가능성. |
| **LM-W4-B-H (신규, carry — 다음 phase)** | `R.drawable.abc_vector_test` (appcompat-resources-1.6.1.aar 안 res/drawable/abc_vector_test.xml, resource id 0x7F070076) lookup 이 layoutlib 의 `Resources_Delegate.getDrawable` path 에서 fail. probe (MinimalLayoutlibCallback.getParser DRAWABLE 분기) 가 호출 안 됨 — layoutlib 의 자체 resolution path 가 callback 우회. layoutlib `Resources_Delegate.getDrawable` 의 disasm + 우리 bundle 의 byType[DRAWABLE] entry 존재 여부 검증 필요. | 본 phase 미해결. chip IT @Disabled 으로 park (W4-B-C-only message). 다음 phase entry. |
| **LM-W4-B-I (신규)** | Material widget 의 strict-consumer pattern — `MaterialResources.getTextAppearance(typedArray, idx)` 가 `getResourceId(idx, 0) == 0` 시 null 반환 + 직후 NPE. button (MaterialButton extends AppCompatButton extends TextView) 은 lenient (TextView 의 setTextAppearance(0) silent), chip (ChipDrawable) 은 strict. 향후 Material widget extension 시 strict-consumer 가 새 layer 의 trigger 가 가능 — 본 LM 은 LM-W3D4-δ-G 의 자연 escalation (annotation message 의 future-option technical-claim 검증 의무 → strict-consumer 의 mandatory contract 검증 의무). | 패턴 인식 — 다음 widget 추가 시 strict-consumer chain 진단 budget 우선. |

## Canonical document changes

본 phase 는 `docs/superpowers/specs/` 또는 `docs/plan/` 변경 없음. 1 file + 1 unit test + KDoc 갱신 — 큰 design decision 아님 (W4-B-A 와 같은 minimal-fix 패턴). plan v6 작성 trigger 미충족.

W3D4 phase 의 plan v3.2 (`docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md`) 의 §11 out-of-scope 의 LM-W3D4-β-H entry-point comparison + LM-W3D4-δ-A (subagent summary verify) 의 자연 적용 — handoff document 자체를 hypothesis source 로 취급 (LM-W4-B-E 영역).

## What's blocking / carried forward

본 phase close 후 **active fail surface = chip IT @Disabled (1 SKIP, W4-B-C-only)**. 다음 candidate:

| candidate | 상태 | 권장 trigger |
|---|---|---|
| **W4-B-C** (chip drawable abc_vector_test lookup) | open, **다음 phase 권장** | layoutlib `Resources_Delegate.getDrawable(Resources, int)` disasm → 자체 resolution path 분석. callback 우회 메커니즘 식별 (probe 가 callback 호출 0 건 confirm). 우리 bundle 의 byType[DRAWABLE] entry 가 존재하는지 + ResourceValue 의 `value` 필드가 `@axp:drawable-xml` placeholder 인지 검증. 가능 fix path: (a) bundle 의 DRAWABLE ResourceValue 가 layoutlib 의 expected path-style value (실 file path 또는 알려진 placeholder) 으로 set 되어야 callback 활성화, (b) layoutlib 의 BridgeContext.getResource hook 으로 callback 우선 dispatch, (c) Resources_Delegate.getDrawable 의 NotFoundException catch 에서 callback fallback 추가. |
| C (namespace-aware mode) | hold | active fail 없음 — design 정합 priority 시. |
| E (R$styleable layer) | hold | future Material widget styled-attrs lookup fail 시 escalate. |
| 그 외 widget (TextInputLayout / MaterialSwitch / Snackbar / Badge) | hold | W4-B-C close 후 자연 진입. |

## Pair review verdicts

본 phase 는 implementation phase — Round 7 pair-review 미요구 (CLAUDE.md §Codex). 단 fix 가 parser-layer 의 새 element type 추가 (silent feature gap close) — dual-source single-shot review (Claude reviewer subagent + Codex direct CLI sanity-check) 진행. Q1-Q5 + Q6 open critique slot.

(dual-source review 결과는 commit 직전 finalize.)

## Commits + push

- (다음 commit) `feat(w4-b-b): macro element parsing — chip textAppearance via @macro/... indirection now resolves` — 본 session-log + 3 file diff 동시. CLAUDE.md task-unit completion 규약 준수 — push 즉시 진행.
