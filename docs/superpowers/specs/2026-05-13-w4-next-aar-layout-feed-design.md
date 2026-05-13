# W4-NEXT phase 2 — AAR `res/layout` feed design

Date: 2026-05-13. Status: **GO with δ1** (Round 7 Codex+Claude planning pair-review CONVERGED full set + order; amendments A1-A5 absorbed, see §8 below).

Predecessor: `8fc426d` `feat(w4-next-a): interpolator XML feed mirror — MotionUtils.resolveThemeInterpolator TYPE_STRING gate now satisfied`.

Trigger: textInputLayout IT (`@Disabled`) — `[axp.probe] getParser type=layout name=design_text_input_start_icon` surfaces "No Input specified" XmlPullParserException because the AAR walker does not enumerate `res/layout/<name>.xml` and the callback does not route ResourceType.LAYOUT.

---

## §1 Motivation + bytecode-grounded evidence

### §1.1 Empirical fail surface (carried from W4-NEXT phase 1)

W4-NEXT phase 1 (interpolator XML feed) closed `MotionUtils.resolveThemeInterpolator`'s `TypedValue.type == TYPE_STRING` assertion. The next escalation surfaced immediately in the textInputLayout IT probe:

```
[axp.probe] getParser type=layout ns=RES_AUTO name=design_text_input_start_icon value=design_text_input_start_icon
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION
  msg=No Input specified (position:START_DOCUMENT null@0:0)
  exc=XmlPullParserException
```

`com.google.android.material.textfield.StartCompoundLayout.<init>` (TextInputLayout's leading-icon container) calls `LayoutInflater.from(ctx).inflate(R.layout.design_text_input_start_icon, this, false)`. The 3-arg `inflate(int, ViewGroup, boolean)` overload routes through `Resources_Delegate.getLayout(Resources, int)` → `ResourceHelper.getXmlBlockParser(BridgeContext, ResourceValue)` → `LayoutlibCallback.getParser(value)` → null (we have no LAYOUT branch) → `ParserFactory.create(value)` (blank parser, no input) → "No Input specified".

### §1.2 Bytecode evidence (`/tmp/w4-layout/`, regenerable per LM-W4-NEXT-B)

#### Resources_Delegate.getLayout (offset 829-872)

Structurally identical to `getXml` (offset 1679) and `getAnimation` (offset 877). All three call `ResourceHelper.getXmlBlockParser` via the same constant pool index `#476`. Differences from `getDrawable` (offset 463+):
- NO `.xml` suffix gate (drawable's `endsWith(".xml") || resourceType == AAPT` gate is absent).
- NO `sLayoutCache` (drawable's JVM-static `sDrawableCache` LruCache is absent).
- NO LayoutInflater_Delegate intercept inside `getLayout` itself.

**Implication**: the W4-A-sibling pattern (callback `getParser` routing) works for the 3-arg `LayoutInflater.inflate(int, ViewGroup, boolean)` path. Placeholder shape requires NO `.xml` suffix for routing, but `axp/layout/<name>.xml` is preferred for parity with W4-B-C drawable + W4-NEXT interpolator placeholders (and resilience against any future hypothetical gate).

#### ResourceHelper.getXmlBlockParser (offset 415-461)

Same bytecode chain as documented in W4-NEXT phase 1 session-log. NO type-specific gates for LAYOUT — same path as ANIMATOR / COLOR / INTERPOLATOR. Specifically:
- offset 7-17: value.equals("@null") → return null.
- offset 26-32: if !value.isFramework() → callback.getParser(value) at offset 35-41.
- offset 60-71: if parser is null → ParserFactory.create(value) fallback (blank parser).
- offset 81-92: wrap in BridgeXmlBlockParser.

#### BridgeInflater.inflate(int, ViewGroup) — secondary path (offset 509-590)

This is the 2-arg `inflate(int, ViewGroup)` overload that BYPASSES the callback entirely:
- offset 26-30: Bridge.resolveResourceId(I).
- offset 52-62: BridgeContext.getRenderResources().getResolvedResource(ref) → ResourceValue.
- offset 78-83: ParserFactory.create(value, true) — direct call, callback BYPASSED.

ParserFactory.create(String, boolean) (offset 26-54) delegates to `XmlParserFactory.createXmlParserForFile(value)`. Our `MinimalLayoutlibCallback.createXmlParserForFile` returns a blank `KXmlParser` → "No Input specified" if hit.

**Latent risk**: textInputLayout uses the 3-arg overload (probe evidence). Future widgets that use the 2-arg overload would NOT be helped by `getParser` alone — they'd also need `createXmlParserForFile` to recognize the `axp/layout/<name>.xml` placeholder. Phase 2 scope: 3-arg path only. Carry the bypass as LM-W4-NEXT-LAYOUT-BYPASS for future closure when a real fail trigger surfaces.

### §1.3 Layout XML scope (across the 41-AAR fixture set)

| AAR | res/layout/*.xml count |
|---|---|
| appcompat-1.6.1.aar | 32 |
| core-1.13.0.aar | 9 |
| material-1.12.0.aar | 65 |
| **Total** | **106** |

Only 3 of 41 AARs ship `res/layout/`. Walker scope is moderate (~100-300 KB raw text bundle footprint).

### §1.4 design_text_input_start_icon body inspection

Material 1.12.0 `res/layout/design_text_input_start_icon.xml` (1332 bytes):
- Single root: `<com.google.android.material.internal.CheckableImageButton>`.
- NO `<include>`, NO `<merge>`, NO `<ViewStub>`, NO `<view class=>`, NO recursive `@layout/` reference.
- Attribute deps: `?attr/actionBarItemBackground`, `@dimen/mtrl_textinput_start_icon_margin_end`, `@dimen/mtrl_min_touch_target_size`. All simple chain/values lookups already supported by W3D4 + W4-A.
- Custom view class `com.google.android.material.internal.CheckableImageButton` is in material-1.12.0.aar — already on runtime classpath, loadable via `viewClassLoaderProvider`.

`SelectorXmlPullParser.fromString` consumes the body correctly.

### §1.5 Temp probe — next escalation surfaces past LAYOUT feed

Wired temp ResourceType.LAYOUT branch into `MinimalLayoutlibCallback.getParser` (read `/tmp/w4-layout/probe-layout.xml` for the magic name `design_text_input_start_icon`). Un-disabled IT, ran, captured next error layer, reverted.

Result: layout feed succeeds (CheckableImageButton constructor invoked at line 56). The NEXT error surfaces:

```
Failed to parse file axp/drawable/abc_item_background_holo_light.xml
XmlPullParserException: Binary XML file line #20: <item> tag requires a 'drawable' attribute or child tag defining a drawable
  at android.graphics.drawable.StateListDrawable.inflateChildElements(StateListDrawable.java:195)
  at com.google.android.material.internal.CheckableImageButton.<init>(CheckableImageButton.java:56)
```

Root cause: `?attr/actionBarItemBackground` resolves to `@drawable/abc_item_background_holo_light` (an AppCompat StateList drawable). Our W4-A drawable feed captures this XML body. But `StateListDrawable.inflateChildElements` parses each `<item>` and tries `getResources().getDrawable(R.drawable.abc_list_selector_disabled_holo_light)` — and that target is a 9-patch PNG in the qualifier directory `res/drawable-mdpi-v4/` (and xxhdpi-v4, etc.). Our walker only enumerates `res/drawable/<name>.xml` (default qualifier, XML only). The chained PNG is NOT in our bundle → `getDrawable` returns null → StateListDrawable interprets as "no drawable" → throws.

**Implication**: closing phase 2 (LAYOUT feed) is necessary but NOT sufficient for the textInputLayout IT. The downstream chain StateListDrawable → PNG/qualifier drawable lookup remains broken. Phase 3+ (PNG / qualifier drawable feed) is the next gate.

This is consistent with the W4-NEXT phase 1 pattern: each phase closes one gate, surfaces the next, the IT remains @Disabled until all gates close.

---

## §2 Three path candidates

### §2.1 path δ1 — mechanical W4-A-sibling mirror (RECOMMENDED for v1)

Add the fifth raw-XML feed sibling, mirroring the existing color / animator / drawable / interpolator pattern.

Files touched:
1. `AppLibraryResourceConstants.kt` — add `AAR_LAYOUT_DIR_PREFIX = "res/layout/"`, `LAYOUT_PLACEHOLDER_PREFIX = "axp/layout/"`, `LAYOUT_PLACEHOLDER_SUFFIX = ".xml"`, helper `layoutPlaceholderValue(name): String`.
2. `ParsedNsEntry.kt` — add `LayoutXml` data class.
3. `NsBucket.kt` — add `layouts: Map<String, String>` field + extend `EMPTY`.
4. `AarResourceWalker.kt` — add `collectLayoutXmls(zip, pkg)` + integrate into `walkOne` + new `totalLayoutXmls` stats.
5. `LayoutlibResourceBundle.kt` — add `getLayoutXml(ref)` + `layoutXmlCountForNamespace(ns)` + `ParsedNsEntry.LayoutXml` branch in `buildBucket` + `layouts` passed to `NsBucket(...)`.
6. `MinimalLayoutlibCallback.kt` — add `layoutXmlLookup: (ResourceReference) -> String?` constructor param + `ResourceType.LAYOUT` branch in `getParser`.
7. `LayoutlibRenderer.kt` — wire `{ ref -> bundle.getLayoutXml(ref) }` as the 7th `MinimalLayoutlibCallback` arg.
8. 4 callback test files — add `{ null }` as the 7th constructor arg.
9. `LayoutlibResourceBundleLayoutTest.kt` (NEW, 7 cases) — sibling to interpolator test.
10. `AarResourceWalkerLayoutTest.kt` (NEW, 4 cases) — sibling to interpolator walker test.
11. `MinimalLayoutlibCallbackColorParserTest.kt` — extend with 2 new cases (LAYOUT miss + hit).
12. `LayoutlibRendererIntegrationTest.kt` — update textInputLayout IT KDoc to cite the new downstream gate (StateListDrawable → PNG drawable feed) as the carry blocking close. IT remains @Disabled.

Pros:
- Smallest delta, copies an established 4× pattern.
- Implementation cost ~ same as W4-NEXT phase 1 (~16 files).
- Regression surface narrow — same NsBucket extension, callback branch addition.

Cons:
- Fifth nearly-identical sibling reinforces LM-W4-NEXT-AAPT-XMLS (mechanical copy redundancy).
- Each future XML resource type adds yet another sibling (if any surface).

### §2.2 path δ2 — data-driven feed registry (HOLD for now)

Refactor four existing sibling helpers (color / animator / drawable / interpolator) plus the new layout helper into a single data-driven registry.

Possible shape:

```kotlin
internal enum class AarRawXmlFeed(
    val dirPrefix: String,
    val resType: ResourceType,
    val placeholderPrefix: String,
)
{
    COLOR_STATE_LIST("res/color/", ResourceType.COLOR, "axp/color/"),
    ANIMATOR("res/animator/", ResourceType.ANIMATOR, "axp/animator/"),
    DRAWABLE("res/drawable/", ResourceType.DRAWABLE, "axp/drawable/"),
    INTERPOLATOR("res/interpolator/", ResourceType.INTERPOLATOR, "axp/interpolator/"),
    LAYOUT("res/layout/", ResourceType.LAYOUT, "axp/layout/"),
}
```

NsBucket consolidates 4 (now 5) `Map<String, String>` fields into a single `Map<AarRawXmlFeed, Map<String, String>>`. Walker iterates the enum once. Callback's `getParser` `when` branch becomes a single dispatch.

Pros:
- Eliminates LM-W4-NEXT-AAPT-XMLS redundancy.
- Adding a future XML resource type (font / xml / transition) becomes a single enum entry.
- Cleaner semantics — all "raw XML feed" resources are uniformly treated.

Cons:
- Refactor surface is wide — touches 5+ source files + all existing tests for color / animator / drawable / interpolator.
- Higher initial cost. Risk: regression in any of T17 / T20 / MaterialFidelity / chip / basic / minimal.
- Drawable's per-name `.xml` placeholder shape diverges from animator/interpolator/layout (drawable has a real value-format gate; others use the same shape only for parity). Encoding this divergence in the registry adds complexity that partially offsets the cleanup.
- PNG / 9-patch drawable feed (phase 3) is a fundamentally different feed shape (binary asset, not raw XML). The registry would NOT generalize to PNG, so the "future-proofing" benefit is bounded to XML-only resource types.
- DRAWABLE has the per-name path-style placeholder uniqueness requirement (sDrawableCache). Other XML feeds (color / animator / interpolator / layout) don't strictly require it. Registry must encode this asymmetry.

### §2.3 path δ3 — selective layout pass (REJECTED)

Only enumerate layout XMLs that are actually requested by integration tests during a probe run.

Cons:
- Adds runtime feedback loop the walker doesn't currently have (walker is startup-time; probe results are render-time).
- 106 layout XMLs × ~2KB avg = ~200KB bundle footprint. Walker enumeration time is a fraction of a millisecond. No measurable runtime cost.
- Test reproducibility hurt (which layout XMLs are loaded depends on which tests run).

Reject.

### §2.4 Decision rationale

**Recommend δ1** for phase 2:

1. **Empirical urgency**: textInputLayout IT remains @Disabled. δ1 unblocks phase 2 in ~16 files. δ2 doubles or triples that.
2. **Forward-compatibility bound**: PNG / 9-patch drawable feed (phase 3) breaks the "raw XML feed" registry abstraction anyway. δ2's payback is bounded to XML resource types — the natural inflection point is when phase 3 surfaces (or any non-XML feed surfaces).
3. **Risk asymmetry**: δ2 touches 4 working sibling implementations. Each touched implementation must remain green for T17 / T20 / MaterialFidelity / chip / basic / minimal. Wider regression surface.
4. **Iteration discipline**: per LM-W4-B-E (vindicated 4×), incremental closure with empirical evidence beats speculative refactor. δ2 has no current empirical trigger — it's a "feels redundant" heuristic, not a "concrete fail or smell" trigger.

**Defer δ2** to a future session when EITHER:
- A 6th XML feed surfaces (font / xml / transition / etc.), making the redundancy-vs-refactor calculus tilt toward δ2.
- A specific bug surfaces in the per-sibling code that a registry would have prevented.

Carry as LM-W4-NEXT-AAPT-XMLS-CARRY.

---

## §3 Phase 2 (path δ1) implementation plan — 11 steps (Round 7 amendment A1)

Per Round 7 Codex Q6 #1: original steps 6-8 collapsed into one atomic compile-safe step (step 6 below). Adding `layoutXmlLookup` as a required constructor parameter (no defaults per CLAUDE.md style) BREAKS compilation between data layer and call sites — must be one commit.

Each step ends with `./gradlew :layoutlib-worker:test --tests <new test class>` PASS + acceptance regression `T17 + T20 + MaterialFidelity + chip + basic + minimal` PASS.

1. **AppLibraryResourceConstants.kt** — add `AAR_LAYOUT_DIR_PREFIX`, `LAYOUT_PLACEHOLDER_PREFIX`, `LAYOUT_PLACEHOLDER_SUFFIX`, `layoutPlaceholderValue(name)`. KDoc cites bytecode evidence (`Resources_Delegate.getLayout` offset 829-872 path identity with `getXml` / `getAnimation`; no `.xml` gate; no LruCache). Refactor-on-Sight self-audit: file is English-only post W4-NEXT phase 1; no translation needed.
2. **ParsedNsEntry.kt** — add `LayoutXml` data class mirroring `InterpolatorXml`. KDoc cites layout consumer pattern (LayoutInflater.inflate path through `Resources_Delegate.getLayout` callback). Refactor-on-Sight: file-level KDoc + AttrDef KDoc + StyleDef KDoc still in Korean (19 Korean lines verified) — translate per Rule 1.
3. **NsBucket.kt** — add `layouts: Map<String, String> = emptyMap()` field + extend `EMPTY` with the 8th map. KDoc updated to mention the LAYOUT feed alongside the 4 existing siblings.
4. **AarResourceWalker.kt** — add `collectLayoutXmls(zip, pkg)` mirroring `collectInterpolatorXmls`. Integrate into `walkOne`, extend `walkAll` stats with `totalLayoutXmls`, extend the "all-empty skip" guard, extend the diagnostic message. Refactor-on-Sight: file already English-only post W4-NEXT phase 1.
5. **LayoutlibResourceBundle.kt** — add `getLayoutXml(ref)` + `layoutXmlCountForNamespace(ns)` accessors. Add `ParsedNsEntry.LayoutXml` branch in `buildBucket` (placeholder ResourceValue in `byType[LAYOUT]` + raw-XML map population + dup log). Pass `layouts` to `NsBucket(...)`. Refactor-on-Sight: file English-only post W4-NEXT phase 1.
6. **ATOMIC compile-safe callback wiring** (combined per amendment A1) — single commit touching:
   - `MinimalLayoutlibCallback.kt`: add `layoutXmlLookup: (ResourceReference) -> String?` constructor parameter (7th). Add `ResourceType.LAYOUT` branch in `getParser`. Add defensive log in `createXmlParserForFile` when `fileName` starts with `LAYOUT_PLACEHOLDER_PREFIX` (per amendment A2 — flags 2-arg bypass attempts for future debugging). Refactor-on-Sight: file-level KDoc + getOrGenerateResourceId comment + getParser KDoc + R.jar 시드 message + companion KDoc are all in Korean (23 Korean lines verified) — translate per Rule 1.
   - `LayoutlibRenderer.kt`: pass `{ ref -> bundle.getLayoutXml(ref) }` as the 7th `MinimalLayoutlibCallback` arg (LayoutlibRenderer.kt:198-205 production call site).
   - 4 callback test files: `MinimalLayoutlibCallbackTest.kt`, `MinimalLayoutlibCallbackInitializerTest.kt`, `MinimalLayoutlibCallbackLoadViewTest.kt`, `SessionParamsFactoryTest.kt` — add `{ null }` as the 7th constructor arg.
   - `MinimalLayoutlibCallbackColorParserTest.kt`: add `{ null }` as the 7th constructor arg in existing helpers (full LAYOUT case extension is step 9 below).
   This step compiles + passes existing tests as one commit; subsequent steps add new test coverage.
7. **LayoutlibResourceBundleLayoutTest.kt** (NEW, 7 cases) — sibling to LayoutlibResourceBundleInterpolatorTest. Cases: raw body roundtrip, null on miss, null on wrong namespace, placeholder shape, no-leading-`@` invariant (parser-bypass-resilience), per-name uniqueness, first-wins-with-byType-preserved on duplicates.
8. **AarResourceWalkerLayoutTest.kt** (NEW, 4 cases) — sibling to AarResourceWalkerInterpolatorTest. Cases: default qualifier emit, qualifier-dir skip (layout-v21/, layout-night/), partial-use when only layout XML present, null on truly code-only AAR.
9. **MinimalLayoutlibCallbackColorParserTest.kt extension** — add 2 new cases (LAYOUT miss → null, LAYOUT hit → parser fed with raw body, first START_TAG matches the root). Per amendment A3: also update file-level KDoc to remove "LAYOUT/MENU/DRAWABLE 등 prior null 동작 보존" stale claim — LAYOUT is now handled. Translate any remaining Korean comments per Rule 1.
10. **LayoutlibRendererIntegrationTest.kt** — update textInputLayout IT KDoc to cite the new downstream StateListDrawable gate as the carry. IT remains @Disabled. Note phase 2 closes the LAYOUT-feed gate; phase 3 (PNG / qualifier drawable feed) is the next escalation. Per amendment A4: KDoc cites the empirical acceptance gate (next error message moves past `design_text_input_start_icon`).
11. **IT @Disabled sanity re-run + capture stderr** (NEW per amendment A4 + Claude Q6 #3) — temporarily un-disable textInputLayout IT, run once, verify ERROR_INFLATION moves from "No Input specified" to the `axp/drawable/abc_item_background_holo_light.xml` StateListDrawable error. Capture stderr in work_log. Re-disable. If error stays at "No Input specified" → STOP + plan v6.1 mini-round (Codex Q5 KILL POINT: bypass path is active).

After step 11: full regression run (`./gradlew test` + `./gradlew :layoutlib-worker:test -PincludeTags=integration`). Expected delta:
- Cross-module unit: 272 → ~286 (+14: 7 bundle layout + 4 walker layout + 2 callback LAYOUT branch + 1 bundle dup test extension).
- IT: 27 PASS + 1 SKIP (textInputLayout @Disabled, KDoc updated).
- Walker stats: now reports `... 132 drawable-xmls, 18 interpolator-xmls, 106 layout-xmls`.

---

## §4 Regression guard contract

Every step in §3 MUST pass:
- T17 5-probe: `--tests "dev.axp.layoutlib.worker.resources.W3D4DeltaThemeChainDiagnosticTest"`.
- T20 5-probe: `--tests "dev.axp.layoutlib.worker.resources.W3D4DeltaBGateChainProbeTest"`.
- MaterialFidelity 4-case: `--tests "dev.axp.layoutlib.worker.resources.MaterialFidelityIntegrationTest"`.
- LayoutlibRendererIntegrationTest 4-case: activity_basic, activity_basic_minimal, activity_chip + (textInputLayout SKIP).
- LayoutlibRendererTier3MinimalTest 3-case.
- BridgeInitIntegrationTest 4-case + SharedLayoutlibRendererIntegrationTest 3-case.

Phase 2 acceptance gate (empirical, not via assertion — Round 7 amendment A4):
- Re-running the textInputLayout IT (manually un-disabled for sanity check, then re-disabled) MUST surface the StateListDrawable error message instead of "No Input specified". Demonstrates LAYOUT feed wired correctly. The IT's `Result.Status` remains ERROR_INFLATION (downstream gate is open) but the error LOCATION moves past `design_text_input_start_icon`.
- **KILL POINT (Codex Q5)**: if the error stays at "No Input specified" with the stack trace pointing through `BridgeInflater.inflate(int, ViewGroup)` / `ParserFactory.create(value, true)` instead of `LayoutlibCallback.getParser`, then the 2-arg bypass is the active path and δ1 callback-only design is insufficient. Recovery: STOP + plan v6.1 mini-round on `createXmlParserForFile` bridge (do NOT in-flight fix the bypass silently).

---

## §5 Close criteria

- All 12 steps committed.
- Cross-module unit + IT regression PASS (~286 unit / 27 IT + 1 SKIP).
- New `LayoutlibResourceBundleLayoutTest` 7/7 + `AarResourceWalkerLayoutTest` 4/4 + extended `MinimalLayoutlibCallbackColorParserTest` 12 cases all PASS.
- Walker stats line reports `106 layout-xmls`.
- textInputLayout IT KDoc cites the StateListDrawable downstream gate as the next phase 3 trigger; IT remains @Disabled.
- Dual-source single-shot review (Claude code-reviewer subagent + Codex direct CLI sanity-check) APPROVE.
- work_log/2026-05-XX_w4-next-b-aar-layout/ Pattern 3 folder created with session-log.md + handoff.md + next-session-prompt.txt per CLAUDE.md.
- Commit + push to origin/main per CLAUDE.md task-unit completion authorization.

NOT a close criterion: textInputLayout IT @Disabled removal. That waits for phase 3 (PNG / qualifier drawable feed).

---

## §6 Round 7 Codex+Claude planning pair-review questions

Per CLAUDE.md §Codex, planning-phase pair-review is mandatory. Each side answers independently before consolidation.

### Q1 — Path ranking

Rank δ1 / δ2 / δ3 by aggregate (implementation cost + regression risk + forward-compatibility). State the primary deciding signal — the one that would flip your ranking if it went the other way. Specifically address whether the empirical urgency (textInputLayout @Disabled with PNG drawable feed downstream) favors the smaller δ1 delta, or whether the recurring-pattern smell (LM-W4-NEXT-AAPT-XMLS, 5th sibling) favors the broader δ2 refactor.

### Q2 — Forward-compatibility ceiling

Assume 1-3 more XML resource types will surface (font, transition, xml, mipmap, raw). Does δ2's data-driven registry payback materialize across these, given that:
- DRAWABLE has a per-name placeholder uniqueness requirement (sDrawableCache) the others don't.
- PNG / 9-patch drawables (phase 3) are binary, not raw XML — δ2 does NOT generalize to them.
- font / raw / xml / transition have unknown consumer contracts (no bytecode evidence yet).

Is the registry abstraction load-bearing across these axes, or does each new resource type's contract differences erode the cleanup benefit?

### Q3 — Empirical probe budget + remaining unknowns

The 70-min probe surfaced:
- LAYOUT routing parity with getXml/getAnimation (no .xml gate).
- BridgeInflater.inflate(int, ViewGroup) bypass (latent risk, not triggered).
- Downstream StateListDrawable → PNG/qualifier drawable gap.

What probe coverage gaps remain that could re-surface as plan-revision triggers mid-implementation? Specifically: are there any other LAYOUT consumer paths beyond the 3-arg `inflate(int, ViewGroup, boolean)` that could surface during the textInputLayout sanity check? Should the bypass path (LM-W4-NEXT-LAYOUT-BYPASS) be partially closed in this phase, or is the carry the right call?

### Q4 — plan v6.x trigger criterion

If chosen path is δ1: would any sub-decision within δ1 trigger a plan v6.x mini-round (e.g., placeholder shape variants, dup behavior, XML-vs-binary feed asymmetry handling)? If chosen path is δ2: which of its sub-decisions (registry shape, asymmetry encoding, callback dispatch refactor) would warrant mid-implementation pair-review?

### Q5 — KILL POINT (mandatory per LM-W4-NEXT-A pattern)

Identify the single most likely empirical surprise that would invalidate the chosen path's design within the first 4 steps of §3. State concretely: what specifically would surface in T17 / T20 / MaterialFidelity / chip / basic / minimal regression, or in the textInputLayout @Disabled re-run, that would force a plan revision? If this surprise materializes, what is the recovery posture (revert + plan v6.1 round, or in-flight fix)?

### Q6 — Open critique slot (per [[feedback_pair_review_q6_slot]])

Free critique. Anything in §1-5 that's wrong, missing, or load-bearing in a way the explicit Q1-Q5 don't cover. Examples from past rounds:
- W3D4-β SelectorXmlPullParser flaw (Round 6 catch).
- W4-B-C placeholder uniqueness (Round 6 catch).
- W4-NEXT phase 1 res/layout walker scope critique (Round 6 catch — vindicated by THIS phase entry).

Specifically prompt: is there a downstream gate AFTER the StateListDrawable error that this plan misses? Is there a non-empirical structural concern (refactor-on-sight scope, KDoc accuracy, regression surface coverage) that could surface as a reviewer finding post-implementation? Is the textInputLayout IT acceptance gate (empirical "next error message" vs assertion-based) the right framing?

---

## §7 Decision posture for consolidation

Convergence (full + ranking) → GO with δ1 + 12-step plan.

Set converges, ranking diverges → tie-break by **primary objective = unblock textInputLayout IT incrementally** (favors δ1).

Set diverges → Codex judge round (top GPT model + xhigh + both positions + bytecode evidence) before commit.

Per [[feedback_pair_review_codex_killpoint]]: if Codex DISAGREES with file:line evidence, direct verification via Read/Bash before adopting Claude's position.

---

Generated 2026-05-13 by Claude as planning input. Round 7 Codex+Claude planning pair-review CONVERGED — see §8.

---

## §8 Round 7 pair-review record

Verdict: **GO with δ1**. Both halves converged on path δ1 + ranking. KILL POINTS divergent (Codex's empirically grounded; adopted). Q6 critiques complementary — all absorbed as amendments A1-A5.

### Side-by-side headlines

| Q | Claude | Codex |
|---|---|---|
| Q1 | δ1 > δ2 > δ3. Signal: empirical urgency × phase 3 abstraction-break. | δ1 > δ2 > δ3. Signal: callback-routed-vs-bypass for current trigger. |
| Q2 | Bounded payback (~3/5). Defer to 2nd concrete future XML feed. | Not yet load-bearing. Threshold = 2 more empirically verified raw XML types. |
| Q3 | Bypass = carry. 4 gaps named. | Bypass = carry. 4-5 gaps including include recursion (offset 276 verified). |
| Q4 | 1 trigger. | 6 triggers; same posture for normal flow. |
| Q5 KILL POINT | SelectorXmlPullParser fragility (de-rated post-probe). | Bypass path active for textInputLayout (next error msg stays at "No Input specified"). **ADOPTED.** |
| Q6 | Test count, defensive log, step 13, RoS scope, phase 3, builder. | Compile-safe step granularity (CRITICAL), reviewer test KDoc gap. |

### Absorbed amendments

- **A1**: §3 steps 6-8 collapsed into one atomic step (Codex Q6 #1). No-default-args style + required ctor param ⇒ atomic compile-safe step.
- **A2**: defensive log in `createXmlParserForFile` for `axp/layout/` placeholder bypass detection (Claude Q6 #2). ~3 lines.
- **A3**: `MinimalLayoutlibCallbackColorParserTest` KDoc cleanup — remove "LAYOUT/MENU/DRAWABLE prior null 동작 보존" stale claim (Codex Q6 #4).
- **A4**: §4 + §5 acceptance gate framing per Codex Q5 KILL POINT — explicit "next error message moves" criterion + STOP+v6.1 if it doesn't.
- **A5**: phase 3 scope flagged as 120-180 min plan v6 territory (Codex Q6 + Claude Q6 #5).

### Carry LMs (added to combined table)

- LM-W4-NEXT-LAYOUT-BYPASS — `BridgeInflater.inflate(int, ViewGroup)` 2-arg bypass. Defer until concrete trigger.
- LM-W4-NEXT-LAYOUT-DUPS — duplicate layout names across AARs (RES_AUTO collapse risk per Codex Q4).
- LM-W4-NEXT-CALLBACK-BUILDER — extract test setup builder (deferred cleanup).
- LM-W4-NEXT-PHASE3-SCOPE — phase 3 PNG / qualifier feed = plan v6 + Round 7 territory (120-180 min probe).
- LM-W4-NEXT-AAPT-XMLS-CARRY — refactor trigger = 2nd concrete future XML feed surfaces empirically.

### Pair-review verification per [[feedback_pair_review_codex_killpoint]]

Codex's Q3 claim "include recursion in LayoutInflater around offsets 270-276" verified by direct grep of `/tmp/w4-layout/LayoutInflater-disasm.txt:1750`: `invokevirtual #307 // Method android/content/res/Resources.getLayout` at byte offset 276 within `parseInclude` method. Accurate. No pushback needed.

### Pair artefacts (regenerable per LM-W4-NEXT-B)

- `/tmp/w4-layout/codex-pair-prompt.md` (Round 7 prompt).
- `/tmp/w4-layout/codex-pair-response.md` (Codex sealed answer, 9535 bytes).
- `/tmp/w4-layout/claude-pair-answer.md` (Claude sealed answer).
- `/tmp/w4-layout/round7-consolidation.md` (consolidation framework + verdict).
- `/tmp/w4-layout/probe-findings.md` (empirical pre-plan probe summary).

Implementation begins per §3 11-step plan (post-A1).
