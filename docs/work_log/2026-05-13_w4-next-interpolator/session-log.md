# W4-NEXT (interpolator XML feed) — session log

Date: 2026-05-13
Predecessor head: `c4475c3` (W4-B-C close: drawable placeholder per-name xml suffix)
Phase: W4 next candidate — TextInputLayout extension, phase 1 of N (interpolator XML feed)

---

## Outcome

LM-W4-NEXT-MOTION (new) closed by mirroring the W4-A drawable XML feed pattern for `ResourceType.INTERPOLATOR`. The chain `?attr/motionEasingEmphasizedInterpolator` → `@interpolator/m3_sys_motion_easing_emphasized` now resolves to a `TypedValue` with `type = TYPE_STRING (3)` instead of `TYPE_REFERENCE (1)`, which is what `com.google.android.material.motion.MotionUtils.resolveThemeInterpolator` asserts before calling `AnimationUtils.loadInterpolator(ctx, value.resourceId)`.

Path 1 / TextInputLayout from the Claude+Codex planning pair-review was the source. Path 1 surfaced two escalation layers in one probe:
- **Phase 1 — MotionUtils interpolator gate** (this commit). W4-A-sibling implementation: walker enumerates `res/interpolator/*.xml`, bundle stores per-name path-style placeholders, callback routes `ResourceType.INTERPOLATOR` through the new lookup. 18 interpolator XMLs picked up from Material 1.12.0 in the fixture's AAR set.
- **Phase 2 — AAR `res/layout` feed** (carry, plan v6 territory per Codex Q4 trigger criterion). The empirical probe also captured `[axp.probe] getParser type=layout name=design_text_input_start_icon` — TextInputLayout requests an AAR-internal layout XML that our walker does not yet enumerate. Documented in the `activity_textinputlayout.xml` IT's `@Disabled` KDoc.

The TextInputLayout IT (`tier3 textInputLayout — activity_textinputlayout renders SUCCESS via primary path`) is `@Disabled` until phase 2 lands. Path 1's IT-level acceptance gate moves forward to 27 PASS + 1 SKIP, matching the W4-B-A precedent during the chip trilogy.

## Bytecode-grounded rationale (LM-W4-B-E + LM-W4-NEXT-C applied)

Empirical probe budget: ~75 minutes (Codex Q3 budget; matched). Pre-plan disasm covered AppCompatResources sibling routing per LM-W4-NEXT-C — confirmed `ResourceManagerInternal.loadDrawableFromDelegates` has its own `.xml`-suffix gate at offset 152-157 structurally identical to layoutlib's `ResourceHelper.getDrawable` gate, so the W4-B-C placeholder shape satisfies both. The remaining surface was the MotionUtils gate (separate widget, different sibling).

Bytecode evidence (`/tmp/w4-next/` + `/tmp/w4-tinput/` artefacts):

- `com.google.android.material.motion.MotionUtils.resolveThemeInterpolator` offset 24-40 (`MotionUtils-disasm.txt`): reads `TypedValue.type`, asserts `iconst_3` (TYPE_STRING), throws `IllegalArgumentException("Motion easing theme attribute must be an @interpolator resource for ?attr/motionEasing*Interpolator attributes or a string for ?attr/motionEasing* attributes.")` otherwise.

- `com.android.layoutlib.bridge.android.BridgeContext.resolveThemeAttribute` offset 95-316 (`BridgeContext-disasm.txt`): inspects the resolved ResourceValue's value string and assigns `TypedValue.type` based on the leading character. `@` (0x40) → TYPE_REFERENCE (1) at offset 215-220; `#` → color types (28-31); `true`/`false` → TYPE_INT_BOOLEAN (18); Integer.parseInt branch → TYPE_INT_DEC (16); parseFloatAttribute branch → various float types. The fall-through path at offset 305-313 sets `TypedValue.type = TYPE_STRING (3)` and copies the value into `TypedValue.string`. All branches converge at offset 316 (common tail) which sets `TypedValue.resourceId` from `ResourceValue.asReference()` → `getResourceId(ref, 0)`. The placeholder shape `axp/interpolator/<name>.xml` falls through to offset 305-313 — does not match any prefix, fails parseInt and parseFloatAttribute — and lands at TYPE_STRING, satisfying MotionUtils' assertion.

- `android.content.res.Resources_Delegate.getAnimation(Resources, int)` offset 23 (`Resources_Delegate-full-disasm.txt`): routes through `ResourceHelper.getXmlBlockParser(BridgeContext, ResourceValue)` for the eventual `AnimationUtils.loadInterpolator` body fetch.

- `com.android.layoutlib.bridge.impl.ResourceHelper.getXmlBlockParser` offset 26-41 (`ResourceHelper-full.txt`): when `value.isFramework() == false`, calls `LayoutlibCallback.getParser(value)`; if non-null, wraps in `BridgeXmlBlockParser`. Otherwise falls back to `ParserFactory.create(value)` which returns a blank `KXmlParser` with no input — the failure mode that produced "No Input specified" prior to this fix for interpolators (and now produces it for the as-yet-unfed AAR `res/layout` entries).

The placeholder shape `axp/interpolator/<name>.xml` satisfies all three contracts together:
1. NOT `@`-prefixed → TYPE_STRING (3) in BridgeContext.resolveThemeAttribute (MotionUtils gate satisfied).
2. Per-name uniqueness → any downstream value-keyed cache addresses each interpolator independently.
3. `.xml` suffix → consistent with the W4-B-C drawable placeholder for any future gate that checks suffix (none surfaced for INTERPOLATOR yet but kept for parity with W4-B-C and resilience against a hypothetical future gate).

## Files modified

### main src
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstants.kt`
  - Adds `AAR_INTERPOLATOR_DIR_PREFIX = "res/interpolator/"`, `INTERPOLATOR_PLACEHOLDER_PREFIX = "axp/interpolator/"`, `INTERPOLATOR_PLACEHOLDER_SUFFIX = ".xml"`, and helper `interpolatorPlaceholderValue(name: String): String`.
  - Refactor-on-Sight: no preexisting Korean comments needed translation (file was English-only after W4-B-C cleanup).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedNsEntry.kt` — adds `InterpolatorXml` data class mirroring `DrawableXml`.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NsBucket.kt` — adds `interpolators: Map<String, String>` field + extends `EMPTY` constant.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalker.kt`
  - Adds `collectInterpolatorXmls(zip, pkg)` + integrates into `walkOne` + new `totalInterpolatorXmls` stats counter.
  - Refactor-on-Sight: all preexisting Korean comments (require-message wording, parseValuesXml + collectColorStateLists KDoc) translated to English per Rule 1.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt`
  - Adds `getInterpolatorXml(ref)` + `interpolatorXmlCountForNamespace(ns)` + `ParsedNsEntry.InterpolatorXml` branch in `buildBucket` + `interpolators` passed to `NsBucket(...)`.
  - Refactor-on-Sight: file-level KDoc, getColorStateListXml KDoc, frameworkEnumValueMap KDoc, and 5 inline comments translated from Korean to English per Rule 1.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`
  - Adds `interpolatorXmlLookup: (ResourceReference) -> String?` constructor parameter + `ResourceType.INTERPOLATOR` branch in `getParser`.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`
  - Wires `{ ref -> bundle.getInterpolatorXml(ref) }` into the `MinimalLayoutlibCallback` constructor as the 6th argument.

### main fixture
- `fixture/sample-app/app/src/main/res/layout/activity_textinputlayout.xml` (new) — single-widget TextInputLayout (FilledBox via theme attr) + inner TextInputEditText. KDoc cites the three distinct gate paths from chip/MaterialButton.
- `fixture/sample-app/app/src/main/res/values/strings.xml` — adds `fixture_text_input_hint = "Email"`.

### test src
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleInterpolatorTest.kt` (new, 7 cases) — sibling to LayoutlibResourceBundleDrawableTest. Asserts raw body roundtrip, null on miss, null on wrong namespace, placeholder shape, no-leading-@ invariant (MotionUtils gate contract), per-name uniqueness, and first-wins-with-byType-preserved on duplicates.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerInterpolatorTest.kt` (new, 4 cases) — sibling to AarResourceWalkerDrawableTest. Default qualifier emit, qualifier directory skip (interpolator-v21/, interpolator-night/), partial-use when only interpolator XML present, null on truly code-only AAR.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` — adds `@Disabled` IT for activity_textinputlayout with KDoc citing the AAR `res/layout` feed as the next plan v6 phase. File-level KDoc updated.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackColorParserTest.kt` — adds `newCallbackWithInterpolator` helper + 2 new cases for the `ResourceType.INTERPOLATOR` branch of `getParser` (miss → null, hit → parser fed with raw body, first START_TAG matches `pathInterpolator`).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerTest.kt` — Refactor-on-Sight: 6 Korean test function names + 2 Korean assertion messages translated to English. The "manifest package extraction failure" assertion's substring updated from "package 추출 실패" to "failed to extract package" to match the new English production error message.
- 4 callback test files updated to pass `{ null }` as the new 6th constructor argument: `MinimalLayoutlibCallbackTest.kt`, `MinimalLayoutlibCallbackInitializerTest.kt`, `MinimalLayoutlibCallbackLoadViewTest.kt`, `SessionParamsFactoryTest.kt`.

Total: 6 main src + 1 fixture XML + 1 string + 8 test files (2 new + 6 modified) = 16 files. Bytecode evidence + W4-B-C-pattern reuse keeps the conceptual surface small despite the file count.

## Test results

| Metric | post-W4-B-C (`c4475c3`) | post-W4-NEXT |
|---|---|---|
| Cross-module unit | 258 PASS / 0 fail | **272 PASS / 0 fail** (+14: 7 bundle interpolator + 4 walker interpolator + 2 callback INTERPOLATOR branch + 1 bundle dup test extension) |
| layoutlib-worker IT | 27 PASS + 0 SKIP | 27 PASS + 1 SKIP (textInputLayout @Disabled pending AAR `res/layout` feed) |
| `LayoutlibResourceBundleInterpolatorTest` | n/a | 7 cases PASS |
| `AarResourceWalkerInterpolatorTest` | n/a | 4 cases PASS |
| `MinimalLayoutlibCallbackColorParserTest` | 8 cases | 10 cases (+ INTERPOLATOR miss/hit) |
| T17 5-probe regression guard | 5/5 PASS | 5/5 PASS (unchanged) |
| T20 5-probe regression guard | 5/5 PASS | 5/5 PASS (unchanged) |
| Acceptance gate `activity_basic` + `activity_basic_minimal` + `activity_minimal` + `activity_chip` | all PASS | all PASS (unchanged) |
| `MaterialFidelityIntegrationTest` | 4/4 PASS | 4/4 PASS (unchanged) |
| Walker AAR stats | 132 drawable-xmls | 132 drawable-xmls + **18 interpolator-xmls** |

Empirical chip-IT progression evidence (from the @Disabled IT's stderr captured by the test harness):

- Before fix: `ERROR_INFLATION msg=Motion easing theme attribute must be an @interpolator resource for ?attr/motionEasing*Interpolator attributes or a string for ?attr/motionEasing* attributes. exc=IllegalArgumentException`.
- After interpolator feed fix: `ERROR_INFLATION msg=No Input specified (position:START_DOCUMENT null@0:0) exc=XmlPullParserException` — MotionUtils error is GONE; the new error is the AAR `res/layout` feed gap for `design_text_input_start_icon`, surfaced as the next escalation per Codex Q4 trigger criterion.

The probe also confirmed the callback IS invoked with the correct ResourceType.INTERPOLATOR for `m3_sys_motion_easing_emphasized_decelerate` and `m3_sys_motion_easing_linear` — both lookup HIT and return raw XML through `SelectorXmlPullParser.fromString`.

## Landmines discovered + resolution

| LM | Description | Resolution |
|---|---|---|
| **LM-W4-NEXT-MOTION close — bytecode-grounded fix** | `MotionUtils.resolveThemeInterpolator` asserts `TypedValue.type == TYPE_STRING (3)` after Theme.resolveAttribute, throwing IllegalArgumentException when the value is `@`-prefixed. Our bundle previously had no INTERPOLATOR entries, so the chain walker left the value unresolved and `BridgeContext.resolveThemeAttribute` set type=TYPE_REFERENCE (1) → assertion fires. | Per-name path-style placeholder `axp/interpolator/<name>.xml` + walker enumeration + bundle byType[INTERPOLATOR] entry + callback ResourceType.INTERPOLATOR routing. Mirror of W4-B-C drawable pattern. Two contract tests lock in the no-leading-@ + per-name uniqueness invariants. |
| **LM-W4-NEXT-LAYOUT (new, carry)** | TextInputLayout inflate path requests `design_text_input_start_icon` (ResourceType.LAYOUT) — an AAR-internal layout XML. Walker does not enumerate `res/layout/*.xml` in any AAR. Bridge falls back to `ParserFactory.create(value)` which returns a parser with no input → "No Input specified" XmlPullParserException. | Out of scope for this commit per Codex pair-review Q4 trigger criterion (AAR `res/layout` feed = plan v6 + Round 7 territory). Carry-forward as the next-session phase 2 for the textInputLayout IT. The textInputLayout IT is `@Disabled` with this rationale documented inline. |
| **LM-W4-NEXT-MOTIONUTILS-A (new, carry)** | `MotionUtils.resolveThemeInterpolator` is a strict-consumer pattern (TypedValue.type assertion after Theme.resolveAttribute) distinct from the resourceId-only consumer pattern (e.g. AnimatorInflater.loadStateListAnimator for chip's W4-B-A path). When new Material widgets surface, the strict-consumer dispatch into Theme.resolveAttribute → TypedValue.type assertions is a separate failure surface from the resourceId-only callback bypass. Pattern: any consumer reading `TypedValue.type` is sensitive to BridgeContext.resolveThemeAttribute's leading-character branch, so placeholder shapes must avoid `@`, `#`, `true`/`false`, integer-parseable, and parseFloatAttribute-parseable prefixes for the TYPE_STRING fall-through. | Carry for future widget extensions. Captured in the AppLibraryResourceConstants.interpolatorPlaceholderValue KDoc as the bytecode-grounded contract. |
| **LM-W4-NEXT-AAPT-XMLS (new, carry)** | The W4-A drawable mirror has now been applied three times: animator (W3D4-δ-D), drawable (W4-B), interpolator (W4-NEXT). A fourth mirror (LAYOUT for AAR layout feed) and possibly more (anim, font, raw, transition, mipmap, xml) are likely next. The mechanical copy-mirror is getting redundant; the next phase should evaluate whether to refactor into a data-driven feed registry instead of yet another sibling helper. | Carry as a design discussion for the next plan v6 entry alongside the AAR `res/layout` feed (LM-W4-NEXT-LAYOUT). |
| **LM-W4-NEXT-CODEX-Q4-VINDICATED (new)** | Codex's planning-phase Q4 trigger criterion ("AAR `res/layout` feed = plan v6 territory") and Q6 critique ("walker only advertises values/color/animator/drawable, not res/layout") were both empirically vindicated by the TextInputLayout probe. The pair-review's pre-emptive call on phase 2 saved a round of rework. | This is the second project case (after the W4-B-C session-log's "first Claude miss / Codex catch" Q5 vindication) where Codex's pair-review specifically caught the future fail surface ahead of empirical observation. Reinforces the planning-phase pair-review discipline. |

## Dual-source review outcomes

Per CLAUDE.md §Codex: implementation phase = dual-source single-shot review (not Round 7 planning pair-review).

- **feature-dev:code-reviewer (Claude subagent)**: REVISE_REQUIRED → 4 IMPORTANT findings (3 applied, 1 pushback after bytecode verification):
  - (Conf 95) Non-English comments in AarResourceWalker.kt + LayoutlibResourceBundle.kt. **APPLIED** — translated all preexisting Korean comments in the two touched main src files per Rule 1 + Refactor-on-Sight.
  - (Conf 90) `newCallbackWithInterpolator` helper was added without coverage. **APPLIED** — added 2 new test cases (miss + hit) to MinimalLayoutlibCallbackColorParserTest.
  - (Conf 80) `byType[INTERPOLATOR]` placeholder preservation on duplicate untested. **APPLIED** — extended the dup test to assert the byType placeholder remains the first-registered path-style value after the duplicate is processed.
  - (Conf 80) KDoc claim that `TypedValue.resourceId` is set in BridgeContext offsets 316-338 was flagged as inaccurate. **PUSHED BACK** — bytecode evidence shows every type-branch path (offsets 174, 192, 201, 220, 271, 289, 302, 305-313) converges at offset 316 via `goto 316`, making 316-338 the common tail rather than the `@`-only path. Verified by reading the disasm lines 539-602 of BridgeContext-disasm.txt. KDoc is accurate as written. Per [[feedback_pair_review_codex_killpoint]] and [[feedback_spec_code_sample_verify]], file:line evidence is ground truth — Claude reviewer's reading was incorrect on this point.
- **Codex direct CLI sanity-check (codex-cli, default GPT model, reasoning effort xhigh)**: APPROVE. No blocking findings. Independently verified the bytecode chain — `BridgeContext.resolveThemeAttribute` offsets 274-313 (TYPE_STRING fall-through) + offsets 316-338 (common-tail resourceId from asReference). Cited additional evidence at `ResourceHelper.parseFloatAttribute` offsets 75-121 confirming the placeholder's first character (`a`) fails the parseFloat gate. Codex's verdict also **independently corroborated my pushback on Claude reviewer Finding 4** — offsets 316-338 are the common tail across all type branches, not the `@`-only path. 2 non-blocking nits applied: AarResourceWalker.kt:23 "all four" → "all five" (interpolator added); AarResourceWalkerInterpolatorTest.kt:14-20 KDoc rewritten to match the two distinct "AAR with only interpolator XML" + "AAR with neither values nor interpolator" test cases. Codex's verification commands matched mine: `:layoutlib-worker:test --rerun-tasks --tests ...Interpolator...` + `:layoutlib-worker:test -PincludeTags=integration --tests ...LayoutlibRendererIntegrationTest --stacktrace` both passed with 3 rendered IT + TextInputLayout SKIP.

This phase is implementation-only — no `docs/superpowers/specs/` or `docs/plan/` change. The fix is a mechanical W4-A-sibling mirror with bytecode-grounded rationale; plan v6 + Round 7 Codex+Claude pair-review trigger not met for the interpolator feed itself (the AAR `res/layout` feed phase 2 IS plan v6 territory per Codex Q4, but that is the next session).

## What's blocking / carried forward

The interpolator feed phase 1 of the TextInputLayout extension is closed. Active fail surface: 1 — the textInputLayout IT remains `@Disabled` pending phase 2 (AAR `res/layout` feed).

Next session options:

| Candidate | Status | Recommended trigger |
|---|---|---|
| AAR `res/layout` feed (LM-W4-NEXT-LAYOUT) | open | Plan v6 + Round 7 Codex+Claude pair-review per Codex Q4 criterion. Implementation also potentially feeds 6+ other AAR layout XMLs that future widgets will need. The mechanical W4-A-sibling mirror may transition to a data-driven feed registry per LM-W4-NEXT-AAPT-XMLS. |
| Hold + namespace-aware | hold | Codex's Q5 KILL POINT (RES_AUTO collapse later-wins masking) remains carry-forward as LM-W4-NEXT-A from the planning round. No active trigger yet. |
| Hold + another widget | hold | Codex's Q6 widget-yield ranking (Snackbar > Badge > MaterialSwitch) names Snackbar / Badge as non-XML-inflatable and MaterialSwitch as low-yield. Adding a separate widget without closing the textInputLayout IT would dilute the current investigation. |

## Pair review verdicts

Implementation phase — Round 7 Codex+Claude planning pair-review NOT required (CLAUDE.md §Codex). The Round 7 pair-review from the planning phase (path selection) already converged on Path 1 + TextInputLayout. The next-session AAR `res/layout` feed IS Round 7 territory and should run a fresh planning pair-review on the design before implementation.

## Commits + push

`feat(w4-next-a): interpolator XML feed mirror — MotionUtils.resolveThemeInterpolator TYPE_STRING gate now satisfied` — bundles the source change, new contract tests, INTERPOLATOR callback parser coverage, dup-test extension, fixture XML + string + @Disabled IT entry, Refactor-on-Sight Korean cleanup, and the work_log folder.
