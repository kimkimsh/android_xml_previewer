# W4-NEXT phase 2 (AAR `res/layout` feed) — session log

Date: 2026-05-13
Predecessor head: `8fc426d` (W4-NEXT phase 1 close: interpolator XML feed)
Phase: W4-NEXT phase 2 of N (AAR `res/layout` feed for TextInputLayout's `design_text_input_start_icon`)

---

## Outcome

LM-W4-NEXT-LAYOUT closed by mirroring the W4-A drawable + W4-NEXT interpolator XML feed pattern for `ResourceType.LAYOUT`. AppCompat / core / Material AAR-internal layout XMLs (`design_text_input_start_icon`, `design_text_input_end_icon`, `design_navigation_item`, ...) now flow through:

- `AarResourceWalker.collectLayoutXmls` → `ParsedNsEntry.LayoutXml`
- `LayoutlibResourceBundle.buildBucket` `ParsedNsEntry.LayoutXml` branch → `byType[LAYOUT]` placeholder + `layouts` raw-XML map
- `MinimalLayoutlibCallback.getParser` `ResourceType.LAYOUT` branch → `SelectorXmlPullParser.fromString`
- `LayoutlibRenderer` passes `bundle::getLayoutXml` as the 7th `MinimalLayoutlibCallback` arg

The placeholder shape is `axp/layout/<name>.xml` — preserved for parity with W4-B-C drawable + W4-NEXT interpolator placeholders and as a defensive marker for the `BridgeInflater.inflate(int, ViewGroup)` 2-arg bypass path. The bypass path is detected (but not closed) by a defensive log in `MinimalLayoutlibCallback.createXmlParserForFile` — when the inbound `fileName` starts with the `axp/layout/` prefix, a `[MinimalLayoutlibCallback] createXmlParserForFile bypass detected for layout placeholder '...'` line surfaces in stderr so future widgets that hit the bypass have a grep-able diagnostic instead of a silent fallback.

The textInputLayout IT (`tier3 textInputLayout — activity_textinputlayout renders SUCCESS via primary path`) **remains @Disabled** with the IT KDoc updated to cite the new downstream gate (StateListDrawable → 9-patch PNG / qualifier drawable feed). The empirical sanity probe confirmed the LAYOUT feed wires correctly: the `[axp.probe] getParser type=layout` event now resolves and the next ERROR layer surfaces at `axp/drawable/abc_item_background_holo_light.xml` StateListDrawable inflation.

## Bytecode-grounded rationale (LM-W4-B-E + LM-W4-NEXT-C applied; LM-W4-B-J operationalized for LAYOUT)

Empirical probe budget: ~70 minutes (in line with W4-NEXT phase 1's 75-min budget). Pre-plan disasm covered:

- `Resources_Delegate.getLayout(Resources, int)` (`/tmp/w4-layout/Resources_Delegate-disasm.txt` offset 829-872): structurally identical to `getXml` (offset 1679) and `getAnimation` (offset 877). All three call `ResourceHelper.getXmlBlockParser` via the same constant pool index `#476`. NO `.xml` suffix gate (unlike `getDrawable` offset 463+), NO `sLayoutCache` (unlike `getDrawable`'s `sDrawableCache` LruCache), NO LayoutInflater_Delegate intercept inside `getLayout` itself.

- `ResourceHelper.getXmlBlockParser(BridgeContext, ResourceValue)` (`/tmp/w4-layout/ResourceHelper-disasm.txt` offset 415-461): NO type-specific gates for LAYOUT — same path as ANIMATOR / COLOR / INTERPOLATOR. callback null → `ParserFactory.create(value)` (blank parser) fallback at offset 60-71.

- `BridgeInflater.inflate(int, ViewGroup)` 2-arg overload (`/tmp/w4-layout/BridgeInflater-disasm.txt` offset 509-590): BYPASSES the callback entirely. Calls `ParserFactory.create(value, true)` at offset 78-83 directly, which delegates to `XmlParserFactory.createXmlParserForFile(value)`. This bypass is the latent risk LM-W4-NEXT-LAYOUT-BYPASS — currently no widget triggers it, but the defensive log in `createXmlParserForFile` surfaces the case if it arises.

- `LayoutInflater.parseInclude` `<include>` recursion (`/tmp/w4-layout/LayoutInflater-disasm.txt` line 1750, byte offset 276): also routes through `Resources.getLayout(I)` → `Resources_Delegate.getLayout` → callback. So the same δ1 path covers `<include>` recursion in any future widget layout.

- `material-1.12.0/res/layout/design_text_input_start_icon.xml` body inspection (`/tmp/w4-layout/material-aar/res/layout/design_text_input_start_icon.xml`, 1332 bytes): single root `<com.google.android.material.internal.CheckableImageButton>`, NO `<include>`, NO `<merge>`, NO recursive `@layout/`. Custom view class is loaded via `viewClassLoaderProvider().loadClass(name)`.

Layout enumeration scope: 41 AARs scanned, 3 contribute layouts (appcompat-1.6.1: 32, core-1.13.0: 9, material-1.12.0: 65). Total **106 layout XMLs** added to the bundle (~100-300KB raw text footprint, walker time ~25-40ms).

Temp probe (with revert) before plan-writing: wired a temp `ResourceType.LAYOUT` branch into `MinimalLayoutlibCallback.getParser` reading from `/tmp/w4-layout/probe-layout.xml`, un-disabled the IT, ran once, captured next ERROR layer, reverted. The next layer surfaced at `StateListDrawable.inflateChildElements` for `abc_item_background_holo_light` — exactly the prediction the spec relied on.

## Files modified

### main src
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstants.kt` — adds `AAR_LAYOUT_DIR_PREFIX = "res/layout/"`, `LAYOUT_PLACEHOLDER_PREFIX = "axp/layout/"`, `LAYOUT_PLACEHOLDER_SUFFIX = ".xml"`, helper `layoutPlaceholderValue(name): String`. KDoc cites `Resources_Delegate.getLayout` offset 829-872 + path identity + bypass-detection rationale (no phase/round refs per Rule 2).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedNsEntry.kt` — adds `LayoutXml` data class (sourcePackage NOT defaulted per CLAUDE.md no-default-params rule). Refactor-on-Sight: 19 Korean lines translated to English (file-level KDoc + SimpleValue / AttrDef / StyleDef / StyleItem / ColorStateList / AnimatorXml KDocs).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NsBucket.kt` — adds `layouts: Map<String, String>` field (NOT defaulted) + extends `EMPTY` with the 8th map. KDoc updated to cover the LAYOUT feed alongside the 4 existing siblings.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalker.kt` — adds `collectLayoutXmls(zip, pkg)` mirroring `collectInterpolatorXmls`, integrates into `walkOne`, extends `walkAll` stats with `totalLayoutXmls`, extends the all-empty skip guard, extends the diagnostic message.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt` — adds `getLayoutXml(ref)` + `layoutXmlCountForNamespace(ns)` accessors + `ParsedNsEntry.LayoutXml` branch in `buildBucket` (placeholder ResourceValue in `byType[LAYOUT]` + raw-XML map population + dup log) + `layouts` passed to `NsBucket(...)`.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` — adds 7th constructor parameter `layoutXmlLookup: (ResourceReference) -> String?` + `ResourceType.LAYOUT` branch in `getParser` + defensive log in `createXmlParserForFile` for `axp/layout/<name>.xml` placeholder bypass detection (per amendment A2). Refactor-on-Sight: 23 Korean lines translated to English (file-level KDoc + getOrGenerateResourceId comment + getParser KDoc + R.jar seeding error message + companion KDoc).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — wires `bundle::getLayoutXml` as the 7th `MinimalLayoutlibCallback` arg (method reference per CLAUDE.md lambda policy, also converted the 4 existing sibling lookups from lambdas to method references for consistency). Refactor-on-Sight: 52 Korean lines translated to English across class-level KDoc, lastCreateSessionResult / lastRenderResult / lastSessionResult KDocs, loaderArgs KDoc, initBridge inline comments + error messages, renderViaLayoutlib KDoc + inline comments + error message, ensureSampleAppClassLoader KDoc + error message, seedRJarSymbols KDoc, and BRIDGE_CREATE_SESSION_PARAM_COUNT KDoc. Modification-history phrasing (W2D7, W3D3, W3D4-γ T15, 3b-arch, round-2 페어, α: callback init) removed per Rule 2 OUT-OF-SCOPE.

### test src
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleLayoutTest.kt` (NEW, 7 cases) — sibling to LayoutlibResourceBundleInterpolatorTest. Asserts raw body roundtrip, null on miss, null on wrong namespace, byType placeholder shape, no-leading-`@` invariant (parser-bypass-resilience), per-name uniqueness, first-wins-with-byType-preserved on duplicates.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerLayoutTest.kt` (NEW, 4 cases) — sibling to AarResourceWalkerInterpolatorTest. Default qualifier emit, qualifier directory skip (layout-v21/, layout-night/), partial-use when only layout XML present, null on truly code-only AAR.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerTest.kt` — extends the `walkAll prints wall-clock measurement plus counts` smoke test with explicit per-aggregate token assertions (color-state-lists / animator-xmls / drawable-xmls / interpolator-xmls / layout-xmls) so a future feed addition cannot regress the diagnostic line silently.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackColorParserTest.kt` — adds `newCallbackWithLayout` helper + 2 LAYOUT cases (miss → null, hit → parser fed with raw body, first START_TAG matches the `com.google.android.material.internal.CheckableImageButton` root) + 2 createXmlParserForFile cases (non-placeholder file does not log; `axp/layout/` placeholder triggers bypass diagnostic). Updated file-level KDoc to remove the stale "LAYOUT/MENU/DRAWABLE prior null 동작 보존" claim — LAYOUT is now handled. Replaced the LAYOUT-prior-null test with a MENU-equivalent. Translated all Korean comments and assertion messages per Rule 1.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` — updates textInputLayout IT KDoc to cite the new downstream StateListDrawable gate (no phase/round/plan/probe-budget references per Rule 2). IT remains `@Disabled`.
- 4 callback test files (`MinimalLayoutlibCallbackTest.kt`, `MinimalLayoutlibCallbackInitializerTest.kt`, `MinimalLayoutlibCallbackLoadViewTest.kt`, `SessionParamsFactoryTest.kt`) — add `{ null }` as the 7th constructor arg. Refactor-on-Sight: Korean comments / assertion messages translated per Rule 1.

### docs
- `docs/superpowers/specs/2026-05-13-w4-next-aar-layout-feed-design.md` (NEW) — plan v6 spec with §1 motivation + bytecode evidence, §2 path comparison (δ1/δ2/δ3), §3 11-step transition (Round 7 amendment A1 collapsed steps 6-8), §4 regression guard contract, §5 close criteria, §6 Round 7 questions Q1-Q6, §7 decision posture, §8 Round 7 pair-review record (verdict GO with δ1, side-by-side, absorbed amendments A1-A5, carry LMs).

Total: 7 main src + 7 test (2 new + 5 modified) + 1 spec = 15 files.

## Test results

| Metric | post-W4-NEXT phase 1 (`8fc426d`) | post-W4-NEXT phase 2 |
|---|---|---|
| Cross-module unit | 272 PASS / 0 fail | **288 PASS / 0 fail** (+16: 7 bundle layout + 4 walker layout + 2 callback LAYOUT branch + 2 createXmlParserForFile bypass + 1 walker stats token assertion) |
| layoutlib-worker IT | 27 PASS + 1 SKIP (textInputLayout @Disabled) | **27 PASS + 1 SKIP** (textInputLayout @Disabled, KDoc updated to cite phase 3 gate) |
| `LayoutlibResourceBundleLayoutTest` | n/a | **7/7 PASS** |
| `AarResourceWalkerLayoutTest` | n/a | **4/4 PASS** |
| `MinimalLayoutlibCallbackColorParserTest` | 10 cases | **14 cases** (+ LAYOUT miss + LAYOUT hit + createXmlParserForFile non-placeholder + createXmlParserForFile bypass) |
| T17 / T20 / MaterialFidelity / chip / basic / minimal | all PASS | all PASS (unchanged) |
| Walker AAR stats | 132 drawable-xmls + 18 interpolator-xmls | + **106 layout-xmls** (32 appcompat + 9 core + 65 material) |

Empirical sanity-probe evidence (textInputLayout IT temporarily un-disabled, captured stderr, re-disabled):

- Before fix: `ERROR_INFLATION msg=No Input specified (position:START_DOCUMENT null@0:0) exc=XmlPullParserException` with `[axp.probe] getParser type=layout name=design_text_input_start_icon value=design_text_input_start_icon`.
- After fix: `[axp.probe] getParser type=layout` no longer fires (LAYOUT branch wires correctly). The next ERROR surfaces at `Failed to parse file axp/drawable/abc_item_background_holo_light.xml | XmlPullParserException: Binary XML file line #20: <item> tag requires a 'drawable' attribute or child tag defining a drawable | at android.graphics.drawable.StateListDrawable.inflateChildElements(StateListDrawable.java:195)`. Stack trace shows `StateListDrawable.inflateChildElements` (not `BridgeInflater.inflate(int, ViewGroup)`), confirming Codex Q5 KILL POINT (bypass active) did NOT materialize.

## Landmines discovered + resolution

| LM | Description | Resolution |
|---|---|---|
| **LM-W4-NEXT-LAYOUT close — bytecode-grounded fix** | TextInputLayout's `StartCompoundLayout.<init>` calls `LayoutInflater.from(ctx).inflate(R.layout.design_text_input_start_icon, this, false)` which routes through `Resources_Delegate.getLayout(Resources, int)` → `ResourceHelper.getXmlBlockParser` → `LayoutlibCallback.getParser`. Our callback returned null for `ResourceType.LAYOUT` because no LAYOUT branch existed; Bridge fell back to `ParserFactory.create(value)` which produced a blank parser → "No Input specified" XmlPullParserException. | Per-name path-style placeholder `axp/layout/<name>.xml` + walker enumeration of `res/layout/<name>.xml` + bundle `byType[LAYOUT]` entry + callback `ResourceType.LAYOUT` routing. Mirror of W4-B-C drawable + W4-NEXT interpolator pattern. Two contract tests lock in the no-leading-`@` invariant + per-name uniqueness. |
| **LM-W4-NEXT-LAYOUT-BYPASS (carry, from Codex Q5 KILL POINT)** | `BridgeInflater.inflate(int, ViewGroup)` 2-arg overload bypasses `LayoutlibCallback.getParser` and calls `ParserFactory.create(value, true)` directly → `XmlParserFactory.createXmlParserForFile(value)`. Currently no widget triggers it; if a future widget does, the placeholder `axp/layout/<name>.xml` arrives at `createXmlParserForFile` unrecognized and the blank KXmlParser produces "No Input specified". | Defensive log in `createXmlParserForFile` detects bypass attempts via `LAYOUT_PLACEHOLDER_PREFIX` match and surfaces a grep-able diagnostic line. Closing the bypass requires a thicker `createXmlParserForFile` override that recognizes the placeholder and reads from the bundle — defer until concrete trigger surfaces. |
| **LM-W4-NEXT-LAYOUT-DUPS (carry, from Codex Q4 trigger)** | Duplicate layout names across AARs would feed wrong raw body for a real requested layout (RES_AUTO collapse risk). Empirical first-pass scan with the new walker showed no duplicates across appcompat / core / material, but the policy is silent first-wins-with-log; a future AAR could surface a real collision. | Bundle dup-log already covers this. The contract test in `LayoutlibResourceBundleLayoutTest.duplicate layout XML — first-wins with diagnostic line and byType placeholder preserved` locks in the first-wins behavior. |
| **LM-W4-NEXT-CALLBACK-BUILDER (closed by I3 fix)** | `MinimalLayoutlibCallback` constructor now has 7 parameters; the lambda-per-lookup pattern `{ ref -> bundle.getXxxXml(ref) }` was getting visually noisy. | Switched all 6 lookup lambdas in `LayoutlibRenderer.kt` to method references (`bundle::getColorStateListXml`, `bundle::getAnimatorXml`, `bundle::getDrawableXml`, `bundle::getInterpolatorXml`, `bundle::getLayoutXml`) per CLAUDE.md lambda policy. Reviewer caught this for the new line; full file Refactor-on-Sight extended the conversion to all 4 prior siblings. |
| **LM-W4-NEXT-PHASE3-SCOPE (carry)** | Phase 3 (PNG / qualifier drawable feed) is meaningfully more complex than phase 2 — qualifier directory parsing + density variant selection + 9-patch decode + AppCompat consumer bytecode. The temp probe + sanity probe both confirmed phase 3 is the next active gate for the textInputLayout IT. | Carry as the next LM-W4-NEXT-LAYOUT successor. Plan v6 + Round 7 territory (~120-180 min probe budget). |
| **LM-W4-NEXT-AAPT-XMLS-CARRY (carry, from Q1+Q2)** | The W4-A drawable mirror has now been applied 4 times: animator (W3D4-δ-D), drawable (W4-B), interpolator (W4-NEXT phase 1), layout (W4-NEXT phase 2). The mechanical copy-mirror is showing redundancy. | Deferred. Refactor trigger = 2nd concrete future XML feed surfaces empirically. PNG / 9-patch (phase 3) is binary, not raw-XML, so it does NOT generalize to the registry abstraction. |
| **LM-W4-B-L (vindicated 4th time) — Refactor-on-Sight self-audit BEFORE reviewer run** | Pre-existing Korean text in long-lived files slips past the "translate as you touch" discipline because the touched lines themselves are clean. The new lines added in step 6 to `LayoutlibRenderer.kt` were English, but the surrounding 52 Korean lines went untouched and the reviewer caught it. | Discipline reaffirmed: every diff hunk in a non-English file triggers a full-file scan, not just per-hunk. Caught by Claude reviewer subagent (C1 finding) + applied in same change. Codex review separately caught `ParsedNsEntry.kt:83` (one Korean line missed in the AnimatorXml KDoc) + `SessionParamsFactoryTest.kt` (full-file Korean scope, touched for the 7th-arg update). |
| **LM-G — codex exec stdin redirect** (new feedback memory written) | Codex CLI in `codex exec` mode reads "additional input from stdin" until EOF when stdin is non-TTY. The first invocation in this session (planning pair-review) worked because the shell snapshot happened to seal stdin; the second invocation (implementation review) inherited an open-but-empty stdin and blocked at "Reading additional input from stdin..." after only 39 bytes of output. | Diagnosed by inspecting stdout content + process state. Killed the hung process via `pkill -9 -f 'codex exec.*<keyword>'` and re-dispatched with explicit `< /dev/null`. Saved as `feedback_codex_exec_stdin_redirect.md` memory entry; future codex direct CLI dispatches must include the stdin redirect. |

## Dual-source review outcomes

Per CLAUDE.md §Codex implementation phase: dual-source single-shot review (not Round 7 planning pair-review).

- **`feature-dev:code-reviewer` (Claude subagent)**: REVISE_REQUIRED → 2 CRITICAL + 1 IMPORTANT (all applied):
  - **C1** (Conf 95) Korean text in `LayoutlibRenderer.kt` not translated despite the file being touched. **APPLIED** — translated all 52 Korean lines + removed modification-history phrasing per Rule 2 OUT-OF-SCOPE.
  - **C2** (Conf 90) `LM-W4-NEXT-LAYOUT-BYPASS` ticket-style label embedded in the `createXmlParserForFile` KDoc (Rule 2 OUT-OF-SCOPE). **APPLIED** — sentence removed; substance preserved in surrounding paragraphs.
  - **I3** (Conf 82) `{ ref -> bundle.getLayoutXml(ref) }` lambda used where `bundle::getLayoutXml` method reference would suffice. **APPLIED** — converted all 6 sibling lookups (color / animator / drawable / interpolator / layout) to method references for consistency.

- **Codex direct CLI sanity-check** (codex-cli, default GPT model, reasoning effort xhigh; dispatched with `< /dev/null` after diagnosing the stdin-hang root cause): REVISE_REQUIRED → 0 CRITICAL + 3 IMPORTANT + 4 NIT (all applied):
  - **I1** Korean residue in `ParsedNsEntry.kt:83` (AnimatorXml KDoc) + `SessionParamsFactoryTest.kt` (full-file). **APPLIED** — both translated.
  - **I2** New default parameter values added on `LayoutXml.sourcePackage` and `NsBucket.layouts` despite CLAUDE.md no-default-params rule. **APPLIED** — both defaults removed; existing siblings retain their pre-existing defaults (broader cleanup deferred as Refactor-on-Sight applies to §3 violations only, and "no default params" is §1).
  - **I3** Task-history / future-plan metadata in KDocs (`LayoutlibRendererIntegrationTest.kt:131-142` cites "phase 3", "plan v6", "Round 7", probe budget; `AppLibraryResourceConstants.kt:145` references `W4-B-C` / `W4-NEXT`). **APPLIED** — both KDocs rewritten to keep only structural rationale per Rule 2 OUT-OF-SCOPE.
  - **NIT 1** Bytecode claim verification ✓ (no action needed).
  - **NIT 2** Defensive `createXmlParserForFile` under-tested. **APPLIED** — added 2 direct test cases (non-placeholder does NOT log; `axp/layout/` placeholder triggers bypass diagnostic).
  - **NIT 3** `walkAll prints wall-clock measurement plus counts` smoke test only checks `[AarResourceWalker]` prefix and `ms`, not per-aggregate tokens. **APPLIED** — added explicit assertions for color-state-lists / animator-xmls / drawable-xmls / interpolator-xmls / layout-xmls tokens.
  - **NIT 4** Step-6 constructor coherence ✓ (no action needed).

Per [[feedback_pair_review_codex_killpoint]]: Codex's bytecode claims (`getLayout` offset 829-875, constant pool index `#476` shared with `getXml` and `getAnimation`) were verified by direct Read of `/tmp/w4-layout/Resources_Delegate-disasm.txt` and matched the spec.

## What's blocking / carried forward

W4-NEXT phase 2 (AAR `res/layout` feed) is closed. Active fail surface: 1 — the textInputLayout IT remains `@Disabled` pending phase 3 (PNG / qualifier drawable feed).

Next session options:

| Candidate | Status | Recommended trigger |
|---|---|---|
| AAR PNG / qualifier drawable feed (LM-W4-NEXT-PHASE3-SCOPE) | open | Plan v6 + Round 7 Codex+Claude planning pair-review per LM-W4-NEXT-PHASE3-SCOPE. Implementation also potentially feeds 9-patch decode + density qualifier selection + AppCompat consumer bytecode. Probe budget ~120-180 min. |
| BridgeInflater 2-arg bypass close (LM-W4-NEXT-LAYOUT-BYPASS) | hold | No active trigger. Defensive log surfaces detection if a future widget hits the bypass. |
| Hold + namespace-aware mode | hold | LM-W4-NEXT-A from earlier round. No active trigger. |

## Pair review verdicts

Implementation phase — Round 7 Codex+Claude planning pair-review NOT required (CLAUDE.md §Codex). The Round 7 pair-review from the planning phase (path δ1 selection) already converged GO with full set + order convergence. The next-session phase 3 IS Round 7 territory and should run a fresh planning pair-review on the design before implementation.

## Commits + push

Single commit bundling: source change + new contract tests + new bundle / walker test classes + extended callback test + walker stats token assertion + createXmlParserForFile bypass-detection tests + fixture XML carry-forward + IT KDoc update + Refactor-on-Sight Korean cleanup across 7 files (ParsedNsEntry, LayoutlibRenderer, MinimalLayoutlibCallback, MinimalLayoutlibCallbackTest, MinimalLayoutlibCallbackInitializerTest, MinimalLayoutlibCallbackLoadViewTest, SessionParamsFactoryTest, MinimalLayoutlibCallbackColorParserTest) + plan v6 spec + work_log folder.
