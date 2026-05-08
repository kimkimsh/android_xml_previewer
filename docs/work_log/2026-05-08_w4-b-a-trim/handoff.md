# W4 candidate B-A close — handoff (W4-B-B 다음 phase entry)

날짜: 2026-05-08 (생성), 다음 세션 entry: **W4-B-B 진입** (chip TextAppearance NPE — `MaterialResources.getTextAppearance` 의 `typedArray.getResourceId` 0 반환 → null TextAppearance → NPE on getTextSize). OR W4 candidate C/E hold.

선행 head: (다음 commit) `feat(w4-b-a): style item body trim — chip stateListAnimator callback now wires correctly`

---

## §1 어디까지 끝났나 — W4-B-A green state

### §1.1 W4 candidate B-A 완전 종결 — empirical investigation 의 hypothesis reject + 1-line fix

handoff 의 hypothesis (`<selector>` vs `<set>` root form 으로 인한 layoutlib 의 `Resources_Delegate.getAnimation_Original` 의 분기 — plan v6 design + Round 7 pair-review 영역) 가 layoutlib bytecode 직접 dump (`/tmp/w4ba/Resources_Delegate-disasm.txt`, `/tmp/w4ba/AnimatorInflater-disasm.txt`, `/tmp/w4ba/ResourceHelper-disasm.txt`) + runtime probe (MinimalLayoutlibCallback.getParser 에 임시 println 후 chip IT 실행) 로 reject. 실 cause = NamespaceAwareValueParser 의 `<style><item>` body whitespace preservation. Material 1.12.0 의 `Base.Widget.Material3.Chip` style 의 `<item name="android:stateListAnimator">` 가 multi-line 형식 → StyleItem.value 에 `"\n      @animator/m3_chip_state_list_anim\n    "` 형태로 보존 → layoutlib 의 @ref token resolve fail → `BridgeContext.getXmlBlockParser` 가 `ParserFactory.create(value.getValue())` fallback path → 우리 `MinimalLayoutlibCallback.createXmlParserForFile` 의 blank `KXmlParser` 반환 → `parser.next()` "No Input specified".

**1-line fix**: `NamespaceAwareValueParser.handleStyle:184` — `readElementText(reader)` → `readElementText(reader).trim()`. style item only — `<string>` / `<dimen>` 등은 영향 없음 (i18n placeholder + DIMEN literal 의 정합 유지).

### §1.2 본 phase 의 dual-source review 결과

- **feature-dev:code-reviewer (Claude subagent)**: REVISE → IMPORTANT 3건. (1) NamespaceAwareValueParser.kt 의 pre-existing Korean comments 가 Rule 1 영문 의무 위반 — touched 한 file 의 동시 정리 (Rule 1 의 "file you are already editing" clause). (2) @Disabled annotation message "W4-B-B:" prefix 가 Rule 2 OUT-OF-SCOPE phase identifier. (3) Issue 1 + 2 의 intersection — 신규 영문 comment block 인근 line 182-183 의 phase identifier ("T8 fix", "W3D1") 동시 정리. 모두 APPLIED — NamespaceAwareValueParser.kt 전체 영문화 + 모든 phase identifier 제거 + @Disabled message 의 W4-B-B prefix drop.
- **Codex independent sanity-check**: (commit 직전 finalize)

본 phase 는 implementation phase (CLAUDE.md §Codex "Pairs NOT required for ... ALL implementation-phase work"). dual-source single-shot review 는 *parser-layer change 의 ripple* 차원 — 강제 pairing 아님.

### §1.3 Test posture (현재 main + 다음 commit)

| 측정 | W4-B (post drawable) | W4-B-A (post-trim) |
|---|---|---|
| 모듈 합산 unit | 253 PASS | **255 PASS** (+2: trim contract + string preservation contract) |
| layoutlib-worker IT | 26 PASS + 1 SKIP (chip @Disabled W4-B-A/B) | **26 PASS + 1 SKIP** (chip @Disabled W4-B-B-only) |
| `NamespaceAwareValueParserTest` | 12 cases | **14 cases** |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| T20 5-probe regression guard | 5/5 PASS | **5/5 PASS** (불변) |
| acceptance gate `activity_basic` | PASS | PASS (불변) |
| `activity_minimal` glyph dark-pixel | PASS | PASS (불변) |
| `MaterialFidelityIntegrationTest` | 4/4 PASS | 4/4 PASS (불변) |

empirical chip-IT progression evidence (post-fix, temp probe instrumentation 후 revert):
- before fix: 0 ANIMATOR callback hits for `m3_chip_state_list_anim` (32 calls total, 1 ANIMATOR for `m3_btn_state_list_anim` only)
- after fix: 1 ANIMATOR callback hit for `m3_chip_state_list_anim` (xml.size=2173) + 4+ chip COLOR callback hits (m3_chip_assist_text_color, m3_chip_background_color, m3_chip_stroke_color, m3_chip_ripple_color)
- chip render advances past stateListAnimator inflation, surfaces W4-B-B (TextAppearance NPE) 자연스럽게.

## §2 W4-B-B (chip TextAppearance NPE) — 다음 phase entry

### §2.1 fail surface

post-W4-B-A trim fix 시점:
```
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION 
msg=Cannot invoke "com.google.android.material.resources.TextAppearance.getTextSize()" because "textAppearance" is null 
exc=NullPointerException
```

### §2.2 mechanism

**ChipDrawable.loadFromAttributes** (offset 165 of bytecode disasm) calls **MaterialResources.getTextAppearance(context, typedArray, index)** → returns null when `typedArray.hasValue(index)` is false OR `typedArray.getResourceId(index, 0)` returns 0. 다음 ChipDrawable 의 `TextAppearance.getTextSize()` 가 null instance 위에 호출 → NPE.

**MaterialResources.getTextAppearance** (bytecode):
```java
if (typedArray.hasValue(index)) {            // offset 5
  int styleId = typedArray.getResourceId(index, 0);  // offset 11
  if (styleId != 0) {                        // offset 16
    return new TextAppearance(context, styleId);
  }
}
return null;                                 // offset 29
```

### §2.3 가설

chip's `android:textAppearance` 가 `?attr/textAppearanceLabelLarge` 로 set (single-line, no whitespace issue). Theme.Material3 → `<item name="textAppearanceLabelLarge">@style/TextAppearance.Material3.LabelLarge</item>` (also single-line). chain walker 가 이 ref 를 resolve 하면 ResourceValue with value=`@style/TextAppearance.Material3.LabelLarge` 를 반환.

`typedArray.getResourceId(index, 0)` 가 이 style-ref ResourceValue 를 int resource ID 로 reverse-map 해야 함. 가능 mechanism:
- (A) RJarSymbolSeeder 의 `R$style.TextAppearance_Material3_LabelLarge` field → `TextAppearance.Material3.LabelLarge` (canonical dot-name, RNameCanonicalization 적용) → callback.byRef[ref] = int. 정상이면 reverse lookup 가능.
- (B) layoutlib BridgeContext.getOrGenerateResourceId 의 path — fresh int 가 생성되지만 resolved ResourceValue 의 namespace/type 이 정확해야 함.

active fail 은 (A) 또는 (B) 중 어느 layer 에서 0 반환되는지 empirical probe 필요. `BridgeTypedArray.getResourceId` 의 path trace + `RNameCanonicalization.styleNameToXml("TextAppearance_Material3_LabelLarge")` empirical 검증.

### §2.4 fix path 후보 (empirical 결과 후 좁힘)

- (path α) **RJarSymbolSeeder dot-name canonicalization 회귀 진단** — 현재 `RNameCanonicalization.styleNameToXml` 가 모든 R$style underscore name 을 dot 으로 변환. 실 R class name `TextAppearance_Material3_LabelLarge` → expected output `TextAppearance.Material3.LabelLarge` (dot-3). 검증 필요.
- (path β) **chain walker 의 STYLE 결과 반환 형태** — chain walker 가 ?attr/... 를 resolve 시 StyleResourceValue 반환. layoutlib 의 TypedArray.getResourceId 가 StyleResourceValue 의 int 변환을 어떻게 하는지 (`bundle.getStyleByName` → ResourceReference → `callback.byRef` 역방향 lookup). 동일 path 의 button widget (MaterialButton) 도 `?attr/textAppearanceTitleMedium` 또는 비슷한 attr 사용 — basic IT 가 PASS 하므로 이 path 는 정상 work. chip 만의 실패 trigger 식별 필요.
- (path γ) **BridgeContext 의 resolveAttribute 의 RES_AUTO ↔ ANDROID 전이** — `?attr/textAppearanceLabelLarge` 가 RES_AUTO attr 이지만 Theme.Material3 chain 안에서 정의. Theme.AxpFixture → Theme.Material3.* → ... 의 chain 에서 RES_AUTO bucket 의 이 attr 가 정상 lookup 되는지 검증.

### §2.5 trigger

chip IT @Disabled 가 자동 trigger. W4-B-A trim fix 적용 후 chip IT 자연 surface — 다음 phase 의 entry 그 자체.

### §2.6 cost

unknown depth. 가능 path:
- 작은 fix (1-line gate 또는 RNameCanonicalization edge case) — basic IT 와 chip IT 의 textAppearance 관련 path 차이 식별
- 큰 design (TypedArray.getResourceId 의 layoutlib internal vs. our chain walker 의 reverse-map contract)

empirical probe 진입 후 결정 (W3D4-β-H entry-point comparison 패턴 적용 + LM-W4-B-E 신규: handoff hypothesis 도 hypothesis — bytecode + runtime probe 로 verify 후 plan-writing).

## §3 다음 세션 진입 단계

### §3.1 cold-read 권장 docs (이 순서)

1. **`docs/work_log/2026-05-08_w4-b-a-trim/handoff.md`** (본 파일)
2. `docs/work_log/2026-05-08_w4-b-a-trim/session-log.md` (W4-B-A 의 정확한 file diff + bytecode evidence + dual-source review)
3. `docs/work_log/2026-05-07_w4-candidate-b-chip/handoff.md` (W4-B drawable feed close + W4-B-A/B carry hypothesis — 본 phase 의 hypothesis reject 의 출발점)
4. `docs/work_log/2026-05-07_w4-candidate-d-hardening/handoff-w4-d-close.md` (W4 candidate 카탈로그)
5. `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (W3D4 phase escalation chain — chain walker 의 reference)
6. `docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md` (Codex+Claude pair-review 패턴 reference, 본 phase 의 dual-source review 의 reference)

원본 기획안 (필수 cross-read):
- `docs/plan/03-roadmap.md` (master roadmap, historical/superseded by `06 §6` + `08 §1`)
- `docs/plan/04-open-questions-and-risks.md` (risk register R1-R9)
- `docs/MILESTONES.md` (W1-W6 gate 체크포인트)
- `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md` (W3D4 phase design — chain walker + RJarSymbolSeeder 의 reference)

### §3.2 본 세션 첫 작업 = W4-B-B 진입 (권장) OR hold

#### path 1 — W4-B-B (chip TextAppearance NPE — 권장)

1. RJarSymbolSeeder 의 `R$style.TextAppearance_Material3_LabelLarge` seed 결과 검증 — `RNameCanonicalizationTest` 의 expected dot-name 추가
2. MinimalLayoutlibCallback.getParser 에 임시 println probe 추가 (W3D4-δ-H 패턴) — chip IT 의 textAppearance 관련 callback 호출 추적. basic IT 의 동일 path 와 비교 (W3D4-β-H entry-point comparison 적용)
3. layoutlib BridgeTypedArray.getResourceId 의 bytecode dump + 실 runtime path trace
4. fix path α/β/γ 결정
5. fix 적용 + 신규 unit test
6. chip IT 의 @Disabled 제거 + W4-B 전체 close

empirical 결과에 따라 plan v6 design 작성 가능 (BridgeTypedArray.getResourceId 의 layoutlib internal contract 가 큰 변화면 Round 7 Codex+Claude pair-review 진입 — small fix 면 dual-source single-shot only).

#### path 2 — W4-B 전체 hold + W4 candidate C/E 진입

candidate C (namespace-aware mode) 또는 candidate E (R$styleable layer) 진입. 단 active fail surface 가 chip IT @Disabled 1건 있으므로 본 path 는 visual fidelity priority 의 일시 보류.

## §4 회피해야 할 LM (combined 25 — W3D4 phase 산출 19 + W4-D 신규 2 + W4-B 신규 4)

| LM | 1-line 핵심 |
|---|---|
| LM-W3D4-β-D~H | KDoc backtick + assertNotNull 금지 + IT-tag + subagent unzip /tmp + acceptance fail surface 분류 |
| LM-W3D4-γ-A~C | reviewer prompt + Long.decode + ThemeEnforcement multi-sentinel |
| LM-W3D4-δ-A | spec 코드 sample 의 모든 식별자 grep/Read 사전 verify |
| LM-W3D4-δ-B | NsBucket type-specific map 분리 sibling KILL POINT |
| LM-W3D4-δ-C | runtime-classpath.txt 부재 silent empty AAR — W4-D close ✓ |
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
| LM-W4-B-A (close ✓) | chip stateListAnimator callback bypass — 실 cause = StyleItem body whitespace, NamespaceAwareValueParser.handleStyle 의 trim 으로 close |
| LM-W4-B-B (carry — 다음 phase) | chip TextAppearance NPE — `MaterialResources.getTextAppearance` 의 `typedArray.getResourceId` 0 반환. 가설 path α/β/γ — empirical 결과 후 좁힘 |
| LM-W4-B-C (carry) | subagent (feature-dev:code-explorer 등) 의 가설은 hypothesis. 검증 없이 propagate 위험. file:line read 가 ground truth |
| LM-W4-B-D (carry) | Kotlin helper function 에 default-arg 추가 시 trailing-lambda 호출 패턴 깨짐 |
| **LM-W4-B-E (신규)** | handoff document 자체도 hypothesis source. plan-writing 전 60-min empirical probe (bytecode disasm + runtime probe) 가 plan 의 fact-claim 정확도 보장. LM-W3D4-δ-A (spec verify 의무) + LM-W4-B-C (subagent 가설) 의 자연 escalation. 본 phase 의 W4-B-A hypothesis (root form 분기) reject 가 trigger. |
| **LM-W4-B-F (신규)** | NamespaceAwareValueParser.parseInternal 의 line 145 (top-level `<item type="X" name="Y">Z</item>`) 가 trim 미적용. style item body 만 trim — top-level `<item type=...>` 은 carry. 향후 multi-line top-level item 의 회귀 발견 시 동일 trim 정책 확장. |

## §5 출발 지점 환경 sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
(다음 commit) feat(w4-b-a): style item body trim — chip stateListAnimator callback now wires correctly
bf21dac feat(w4-b): drawable XML feed mirror — candidate A naturally triggered, parked at LM-W4-B-A/B for next phase
7c879fc docs(w4-d): handoff + next-session-prompt for W4-D close — next candidate B/C/A/E decision
8150212 feat(w4-d): runtime-classpath.txt hardening — require() guard mirroring loadFramework
2afe300 docs(w4-entry): handoff + next-session-prompt for W4 phase scope decision

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 255 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
... 26 IT PASS + 1 SKIP (chip @Disabled W4-B-B-only) ...
BUILD SUCCESSFUL — 26 IT PASS + 1 SKIP.
```

regression guards: handoff §5 (W4-D handoff) 그대로 + `NamespaceAwareValueParserTest` 14 cases (style item trim contract + string preservation contract).

debug toggles (선택):
- `-Daxp.debug.bundleShape=true` (LayoutlibResourceValueLoader bootstrap 진단)
- `-Daxp.debug.callback=true` (BridgeContext callback layer instrumentation, plan v3.2 §5.3.1)

`/tmp/w4ba/*` artefacts (cleanup 정책 무관, 본 phase 의 bytecode evidence — W4-B-B 진입 시 reference):
- `/tmp/w4ba/Resources_Delegate-disasm.txt` (Resources_Delegate.getAnimation lines 877-892)
- `/tmp/w4ba/AnimatorInflater-disasm.txt` (loadStateListAnimator lines 167-290 + createStateListAnimatorFromXml line 299)
- `/tmp/w4ba/ResourceHelper-disasm.txt` (getXmlBlockParser lines 415-440)
- `/tmp/w4ba/material/res/values/values.xml` (Material 1.12.0 values.xml — chip vs button stateListAnimator entries 비교 source)
- `/tmp/w4ba/material/res/animator/m3_chip_state_list_anim.xml` + `m3_btn_state_list_anim.xml` (root form `<selector>` vs `<set>` — red herring)
- `/tmp/w4ba/com/google/android/material/chip/ChipDrawable.class` + `MaterialResources.class` + `TextAppearance.class` + `MaterialAttributes.class` (W4-B-B disasm reference)

## §6 W4 phase status

- W3D4-δ-A~D: **CLOSED ✓**
- tier3-glyph: **CLOSED ✓**
- W4-D (runtime-classpath.txt hardening): **CLOSED ✓**
- W4-A (drawable XML feed mirror): **CLOSED ✓** (candidate B 자연 trigger)
- W4-B-drawable: **CLOSED ✓**
- **W4-B-A (chip stateListAnimator — style item body trim): CLOSED ✓**
- W4-B-B (chip TextAppearance NPE): **OPEN — 다음 phase entry**
- W4-C (namespace-aware mode): hold
- W4-E (R$styleable layer): hold

다음 세션 = W4-B-B 진입 또는 W4 전체 hold (priority 결정).
