# W4 candidate B-B close — handoff (W4-B-C 다음 phase entry)

날짜: 2026-05-08 (생성), 다음 세션 entry: **W4-B-C 진입** (chip drawable abc_vector_test lookup — layoutlib `Resources_Delegate.getDrawable` 의 자체 resolution path 가 callback 우회).

선행 head: (다음 commit) `feat(w4-b-b): macro element parsing — chip textAppearance via @macro/... indirection now resolves`

---

## §1 어디까지 끝났나 — W4-B-B green state

### §1.1 W4 candidate B-B 완전 종결 — `<macro>` element parsing 1-method fix

handoff 의 path α / β / γ 모두 reject. empirical probe 로 root cause = NamespaceAwareValueParser 가 Material 3 의 `<macro>` AAPT2 resource type 을 unknown top-level element 로 skipElement 처리. Material 1.12.0 안 593 macro definitions (예: `<macro name="m3_comp_assist_chip_label_text_type">?attr/textAppearanceLabelLarge</macro>`) 가 design-token indirection layer 로 동작. Widget.Material3.Chip.Assist 의 `android:textAppearance` 가 `@macro/m3_comp_assist_chip_label_text_type` 으로 set → chain walker 의 `getUnresolvedResource(MACRO ref)` 가 null 반환 → StyleItem 이 literal `@macro/...` string 보존 → BridgeTypedArray.getResourceId 가 0 반환 → MaterialResources.getTextAppearance null → ChipDrawable.getTextSize NPE.

**fix**: NamespaceAwareValueParser 의 `TAG_MACRO ("macro")` 분기 + `handleMacro` helper (style item body trim 정책 동일 적용 — macro body 도 reference token). chain walker 는 기존 `getUnresolvedResource(MACRO ref)` 경로로 macro body substituted ResourceValue 자동 활용 → 다음 chain hop 으로 자연 진행.

post-fix chip IT 의 새 fail surface = **W4-B-C** (chip drawable abc_vector_test lookup, layoutlib `Resources_Delegate.getDrawable` 의 callback 우회 path).

### §1.2 본 phase 의 dual-source review 결과

(commit 직전 finalize)

본 phase 는 implementation phase (CLAUDE.md §Codex "Pairs NOT required for ... ALL implementation-phase work"). dual-source single-shot review 는 *parser-layer 의 silent feature gap* 차원, 강제 pairing 아님.

### §1.3 Test posture (현재 main + 다음 commit)

| 측정 | post-W4-B-A (`c126512`) | post-W4-B-B |
|---|---|---|
| 모듈 합산 unit | 255 PASS | **256 PASS** (+1: macro contract test) |
| layoutlib-worker IT | 26 PASS + 1 SKIP (chip @Disabled W4-B-A/B) | **26 PASS + 1 SKIP** (chip @Disabled W4-B-C-only) |
| `NamespaceAwareValueParserTest` | 14 cases | **15 cases** |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| T20 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| acceptance gate `activity_basic` | PASS | PASS (불변) |
| `activity_minimal` glyph dark-pixel | PASS | PASS (불변) |
| `MaterialFidelityIntegrationTest` | 4/4 PASS | 4/4 PASS (불변) |

empirical chip-IT progression evidence (post-fix, temp probe instrumentation 후 revert):
- before macro fix: chip IT failure = `ERROR_INFLATION msg=Cannot invoke "TextAppearance.getTextSize()" because "textAppearance" is null`
- after macro fix: chip IT failure = `ERROR_INFLATION msg=Could not find drawable resource matching value 0x7F070076 (resolved name: abc_vector_test)`
- chip IT 의 textAppearance path 정상 — ChipDrawable.loadFromAttributes 의 TextAppearance instance 정상 생성 + 다음 step (drawable resolution) 으로 advance

## §2 W4-B-C (chip drawable abc_vector_test) — 다음 phase entry

### §2.1 fail surface

post-W4-B-B macro fix 시점:
```
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION 
msg=Could not find drawable resource matching value 0x7F070076 (resolved name: abc_vector_test) in current configuration. 
exc=NotFoundException
```

### §2.2 mechanism

**resource id 0x7F070076 = `R.drawable.abc_vector_test`**:
- AAR 위치: `/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.appcompat/appcompat-resources/1.6.1/3fe43025e50b556f319b1ff82a730d5a376a31e/appcompat-resources-1.6.1.aar`
- Path 안 AAR: `res/drawable/abc_vector_test.xml` (1028 bytes, default qualifier — qualifier dir 아님)
- R.txt: `int drawable abc_vector_test 0x7f070076` (fixture 의 `app/build/intermediates/runtime_symbol_list/debug/processDebugResources/R.txt`)
- 사용 site: ChipDrawable inflation 의 어떤 step (defStyleAttr/Res 또는 그 chain 안 default drawable) 이 본 drawable 을 reference

### §2.3 가설

empirical probe (MinimalLayoutlibCallback.getParser 의 ResourceType.DRAWABLE 분기) 결과:
- chip IT 안 callback.getParser 가 abc_vector_test 로 호출 **0 건** — layoutlib 의 자체 resolution path 가 callback 우회
- W4-B-A 의 stateListAnimator 와 동일 pattern (ParserFactory.create fallback)? 아니면 별도 path?

가능 mechanism (empirical 로 좁힐 후보):
- (m1) layoutlib `Resources_Delegate.getDrawable(Resources, int)` 가 자체 resource id → ResourceValue map (BridgeContext.getResource) 을 우선 query, 우리 bundle 의 byType[DRAWABLE] entry 가 expected ResourceValue shape 으로 set 안 됨 → null 반환 → throw
- (m2) 우리 bundle 의 byType[DRAWABLE] entry 의 `value` 필드가 `"@axp:drawable-xml"` placeholder 이며, layoutlib 가 이 magic placeholder 를 path-style filename 으로 parse 시도 → fail
- (m3) layoutlib 의 configuration-aware lookup ("in current configuration" wording) — qualifier 매칭 layer 가 default `res/drawable/` 의 entry 를 reject — 단 abc_vector_test 는 default qualifier 에 위치하므로 unlikely

### §2.4 fix path 후보 (empirical 결과 후 좁힘)

- (path δ1) **bundle 의 DRAWABLE ResourceValue 의 `value` 필드 정확화** — magic placeholder `"@axp:drawable-xml"` 대신 layoutlib 가 expected path-style 또는 known-sentinel 로 set. 동일 bundle 의 ANIMATOR/COLOR entry 도 동일 placeholder 사용 — 단 ANIMATOR/COLOR 는 callback path 진입 (W4-B-A trim post-fix verify) → 즉 placeholder 자체는 문제 없을 가능성. drawable 만 callback 우회 path 이용 → 다른 mechanism.
- (path δ2) **Resources_Delegate.getDrawable 의 자체 path bytecode trace** — disasm + W3D4-β-H entry-point comparison. callback hook injection point 식별 (BridgeContext.getResource 등). 큰 design 가능성 — plan v6 + Round 7 Codex+Claude pair-review.
- (path δ3) **fixture-side override** (W4-B-A 의 path 2 동등) — chip 의 inflation 이 abc_vector_test 를 reference 안 하도록 fixture 또는 chip style override. 단기 close 가능 — 단 LM-W4-B-H 는 carry (다른 widget 의 동일 layer 진입 시 다시 surface).

### §2.5 trigger

chip IT @Disabled 가 자동 trigger. W4-B-B macro fix 적용 후 chip IT 자연 surface — 다음 phase 의 entry 그 자체.

### §2.6 cost

unknown depth — Resources_Delegate.getDrawable 의 자체 resolution path 가 callback 우회 → bytecode trace + bundle entry shape 검증 + 가능 fix path 식별 의 3-step 진행. 작은 fix (1-line bundle entry 변화) 또는 큰 design (BridgeContext-side hook) 가능.

empirical probe 의 budget = 60-min (LM-W4-B-E 적용 — handoff hypothesis 도 hypothesis, plan-writing 전 ground-truth verify 필수).

## §3 다음 세션 진입 단계

### §3.1 cold-read 권장 docs (이 순서)

1. **`docs/work_log/2026-05-08_w4-b-b-macro/handoff.md`** (본 파일)
2. `docs/work_log/2026-05-08_w4-b-b-macro/session-log.md` (W4-B-B 의 정확한 file diff + macro evidence + dual-source review)
3. `docs/work_log/2026-05-08_w4-b-a-trim/handoff.md` (W4-B-A close + W4-B-B path α/β/γ — 본 phase 의 hypothesis source, all rejected)
4. `docs/work_log/2026-05-08_w4-b-a-trim/session-log.md` (W4-B-A 의 trim fix + bytecode evidence — chain walker contract reference)
5. `docs/work_log/2026-05-07_w4-candidate-b-chip/handoff.md` (W4-B drawable feed + chip fixture entry)
6. `docs/work_log/2026-05-07_w4-candidate-d-hardening/handoff-w4-d-close.md` (W4 candidate 카탈로그)

원본 기획안 (필수 cross-read):
- `docs/plan/03-roadmap.md` (master roadmap, historical/superseded by `06 §6` + `08 §1`)
- `docs/plan/04-open-questions-and-risks.md` (risk register R1-R9)
- `docs/MILESTONES.md` (W1-W6 gate 체크포인트)
- `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md` (W3D4 phase design — chain walker reference)

### §3.2 본 세션 첫 작업 = W4-B-C 진입 (권장) OR hold

#### path 1 — W4-B-C (chip drawable abc_vector_test — 권장)

1. 임시 probe 추가 (LM-W4-B-E 적용):
   - LayoutlibResourceBundle.getResource 호출 로깅 — chip IT 안 DRAWABLE type ref query 시 결과 ResourceValue 형태 trace
   - MinimalLayoutlibCallback.getParser DRAWABLE 분기 callback miss/hit (이미 callback 우회 confirm — 추가 probe 는 BridgeContext.getResource 의 callback fallback 동작 검증)
2. layoutlib jar 의 Resources_Delegate.getDrawable disasm:
   ```
   mkdir -p /tmp/w4bc && cd /tmp/w4bc && \
     unzip -o /home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar \
       'android/content/res/Resources_Delegate.class' \
       'com/android/layoutlib/bridge/impl/ResourceHelper.class' && \
     javap -p -c Resources_Delegate.class > Resources_Delegate-disasm.txt
   ```
   (LM-W3D4-β-G unzip --directory /tmp/w4-* 의무 적용)
3. `Resources_Delegate.getDrawable` 의 정확한 lookup path 추적 — file:line 인용 의무 (LM-W4-B-C). callback hook 진입점 식별 + 우리 bundle 의 byType[DRAWABLE] entry shape 검증.
4. fix path 결정 (δ1/δ2/δ3) — empirical 결과로 좁힘
5. fix 적용 + 신규 unit test
6. chip IT 의 @Disabled 제거 + LM-W4-B-H close

empirical 결과에 따라 plan v6 design 작성 가능 (BridgeContext-side hook 변화면 Round 7 Codex+Claude pair-review 진입 — small fix 면 dual-source single-shot only).

#### path 2 — W4-B 전체 hold + W4 candidate C/E 진입

candidate C (namespace-aware mode) 또는 candidate E (R$styleable layer) 진입. 단 active fail surface 가 chip IT @Disabled 1건 있으므로 본 path 는 visual fidelity priority 의 일시 보류.

## §4 회피해야 할 LM (combined 28 — W3D4 phase 산출 19 + W4-D 신규 2 + W4-B 신규 7)

| LM | 1-line 핵심 |
|---|---|
| LM-W3D4-β-D~H | KDoc backtick + assertNotNull 금지 + IT-tag + subagent unzip /tmp + acceptance fail surface 분류 |
| LM-W3D4-γ-A~C | reviewer prompt + Long.decode + ThemeEnforcement multi-sentinel |
| LM-W3D4-δ-A~I | spec verify / NsBucket type-specific / runtime-classpath assertion / IT-tagged RED-on-main / entry-point comparison / Rule 2 OUT-OF-SCOPE / annotation message technical-claim verify / chain-walker IT 의 render-path divergence / RenderResources.applyStyle clear() 금지 |
| LM (CLAUDE.md Three Hard Rules) | 모든 신규 코드 KDoc/inline 영문 only, function/structure only |
| LM-G | codex sandbox bypass 직접 CLI (planning phase 에만) |
| LM-W4-D-A | reviewer agent 가 신규 string literal + KDoc 의 modification-history phrasing 을 catch |
| LM-W4-D-B | Codex CLI 의 explicit `--model` flag 회피 (default model + reasoning effort 만) |
| LM-W4-B-A (close ✓ — `c126512`) | chip stateListAnimator callback bypass — root cause = StyleItem body whitespace, NamespaceAwareValueParser.handleStyle 의 trim 으로 close |
| LM-W4-B-B (close ✓ — 다음 commit) | chip TextAppearance NPE — root cause = `<macro>` element 미파싱, NamespaceAwareValueParser.handleMacro 추가로 close. handoff 의 path α/β/γ 모두 reject |
| LM-W4-B-C (carry) | subagent (feature-dev:code-explorer 등) 의 가설은 hypothesis. file:line read 가 ground truth |
| LM-W4-B-D (carry) | Kotlin helper function 에 default-arg 추가 시 trailing-lambda 호출 패턴 깨짐 |
| LM-W4-B-E (carry, vindicated) | handoff document 자체도 hypothesis source. plan-writing 전 60-min empirical probe (bytecode disasm + runtime probe). 본 phase 에서 path α/β/γ reject 의 trigger — 강한 vindication |
| LM-W4-B-F (carry) | NamespaceAwareValueParser 의 top-level `<item type=...>` 미적용 trim — 향후 회귀 시 확장 |
| **LM-W4-B-G (신규)** | `<macro>` 는 AAPT2 resource type (Material 3 introduce, layoutlib-api ResourceType 32번째 entry). values.xml 안 593 macro definitions 가 design-token aliases. NamespaceAwareValueParser 의 unknown top-level skipElement 처리 → silent feature gap. Material 3 widget 의 strict-consumer 가 trigger. 향후 새 AAPT2 resource type 추가 시 동일 catch 패턴 — `<sample-data>` / `<overlayable>` / `<style-item>` 등의 미파싱 silent gap 가능성. |
| **LM-W4-B-H (carry, 다음 phase)** | `R.drawable.abc_vector_test` (appcompat-resources-1.6.1.aar 안 res/drawable/abc_vector_test.xml, resource id 0x7F070076) lookup 이 layoutlib `Resources_Delegate.getDrawable` 의 자체 resolution path 에서 fail. probe 가 callback 호출 0 건 confirm. fix path δ1/δ2/δ3 — empirical 결과로 좁힘. |
| **LM-W4-B-I (신규)** | Material widget 의 strict-consumer pattern — `MaterialResources.getTextAppearance(typedArray, idx)` 가 `getResourceId(idx, 0) == 0` 시 null + 직후 NPE. button (TextView 기반) lenient, chip (ChipDrawable) strict. LM-W3D4-δ-G 의 자연 escalation — annotation 의 future-option technical-claim 검증 의무 + strict-consumer 의 mandatory contract 검증 의무. 다음 widget 추가 시 strict-consumer chain 진단 budget 우선. |

## §5 출발 지점 환경 sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
(다음 commit) feat(w4-b-b): macro element parsing — chip textAppearance via @macro/... indirection now resolves
c126512 feat(w4-b-a): style item body trim — chip stateListAnimator callback now wires correctly
bf21dac feat(w4-b): drawable XML feed mirror — candidate A naturally triggered, parked at LM-W4-B-A/B for next phase
7c879fc docs(w4-d): handoff + next-session-prompt for W4-D close — next candidate B/C/A/E decision
8150212 feat(w4-d): runtime-classpath.txt hardening — require() guard mirroring loadFramework

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 256 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
... 26 IT PASS + 1 SKIP (chip @Disabled W4-B-C-only) ...
BUILD SUCCESSFUL — 26 IT PASS + 1 SKIP.
```

regression guards: handoff §5 (W4-D handoff) 그대로 + `NamespaceAwareValueParserTest` 15 cases (style item trim contract + string preservation contract + macro contract).

debug toggles (선택):
- `-Daxp.debug.bundleShape=true` (LayoutlibResourceValueLoader bootstrap 진단)
- `-Daxp.debug.callback=true` (BridgeContext callback layer instrumentation, plan v3.2 §5.3.1)

`/tmp/w4ba/*` + `/tmp/w4bb/*` + `/tmp/w4bb-api/*` artefacts (cleanup 정책 무관, 본 phase 의 bytecode evidence — W4-B-C 진입 시 reference):
- `/tmp/w4ba/Resources_Delegate-disasm.txt` + `AnimatorInflater-disasm.txt` + `ResourceHelper-disasm.txt` (W4-B-A bytecode evidence)
- `/tmp/w4bb/BridgeTypedArray-disasm.txt` + `BridgeContext-disasm.txt` (W4-B-B 의 chip TypedArray path 검증)
- `/tmp/w4bb-api/com/android/resources/ResourceType.class` (ResourceType.MACRO existence verify)
- `/tmp/w4ba/material/res/values/values.xml` (Material 1.12.0 values.xml — macro 593 definitions + style item entries 비교 source)

## §6 W4 phase status

- W3D4-δ-A~D: **CLOSED ✓**
- tier3-glyph: **CLOSED ✓**
- W4-D (runtime-classpath.txt hardening): **CLOSED ✓**
- W4-A (drawable XML feed mirror): **CLOSED ✓** (candidate B 자연 trigger)
- W4-B-drawable: **CLOSED ✓**
- W4-B-A (chip stateListAnimator — style item body trim): **CLOSED ✓** (`c126512`)
- **W4-B-B (chip TextAppearance via macro): CLOSED ✓** (다음 commit)
- W4-B-C (chip drawable abc_vector_test lookup): **OPEN — 다음 phase entry**
- W4-C (namespace-aware mode): hold
- W4-E (R$styleable layer): hold

다음 세션 = W4-B-C 진입 또는 W4 전체 hold (priority 결정).
