# W4-NEXT phase 2 (AAR `res/layout` feed) close — handoff

Date: 2026-05-13. Next session entry: **AAR PNG / qualifier drawable feed (LM-W4-NEXT-PHASE3-SCOPE) — plan v6 + Round 7 Codex+Claude planning pair-review**.

Predecessor head: (next commit) `feat(w4-next-b): AAR res/layout feed — TextInputLayout's design_text_input_start_icon now resolves through callback`.

---

## §1 Where we ended — W4-NEXT phase 2 green state

W4-NEXT phase 2 (AAR `res/layout` feed) closed by mirroring the W4-A drawable + W4-NEXT phase 1 interpolator XML feed pattern for `ResourceType.LAYOUT`. AppCompat / core / Material 1.12.0 ship 106 internal layout XMLs (32 + 9 + 65) that widgets inflate via `LayoutInflater.from(ctx).inflate(R.layout.<name>, parent, attachToRoot)`; these now flow through:

- `AarResourceWalker.collectLayoutXmls` → `ParsedNsEntry.LayoutXml`
- `LayoutlibResourceBundle.buildBucket` `ParsedNsEntry.LayoutXml` branch → `byType[LAYOUT]` placeholder + `layouts` raw-XML map
- `MinimalLayoutlibCallback.getParser` `ResourceType.LAYOUT` branch → `SelectorXmlPullParser.fromString`
- `LayoutlibRenderer` passes `bundle::getLayoutXml` as the 7th `MinimalLayoutlibCallback` arg

The placeholder shape `axp/layout/<name>.xml` is preserved for parity with W4-B-C drawable + W4-NEXT phase 1 interpolator placeholders and as a defensive marker for the `BridgeInflater.inflate(int, ViewGroup)` 2-arg bypass path. The bypass is detected (but not closed) by a defensive log in `MinimalLayoutlibCallback.createXmlParserForFile` when the inbound `fileName` starts with `axp/layout/`.

The textInputLayout IT remains `@Disabled` with the IT KDoc updated to cite the next downstream gate (StateListDrawable → 9-patch PNG / qualifier drawable feed). The empirical sanity probe confirmed the LAYOUT feed wires correctly: `[axp.probe] getParser type=layout` resolves cleanly and the next ERROR layer surfaces at `axp/drawable/abc_item_background_holo_light.xml` StateListDrawable inflation. Codex Q5 KILL POINT (bypass active for this widget) did NOT materialize — the stack trace shows `StateListDrawable.inflateChildElements`, not `BridgeInflater.inflate(int, ViewGroup)`.

### §1.1 Test posture (next commit on main)

| Metric | post-W4-NEXT phase 1 (`8fc426d`) | post-W4-NEXT phase 2 |
|---|---|---|
| Cross-module unit | 272 PASS / 0 fail | **288 PASS / 0 fail** (+16: 7 bundle layout + 4 walker layout + 2 callback LAYOUT branch + 2 createXmlParserForFile bypass + 1 walker stats token assertion) |
| layoutlib-worker IT | 27 PASS + 1 SKIP (textInputLayout @Disabled) | **27 PASS + 1 SKIP** (textInputLayout @Disabled, KDoc updated) |
| `LayoutlibResourceBundleLayoutTest` | n/a | **7/7 PASS** |
| `AarResourceWalkerLayoutTest` | n/a | **4/4 PASS** |
| `MinimalLayoutlibCallbackColorParserTest` | 10 cases | **14 cases** (+ LAYOUT miss + LAYOUT hit + createXmlParserForFile non-placeholder + createXmlParserForFile bypass) |
| T17 / T20 / MaterialFidelity / chip / basic / minimal | all PASS | all PASS (unchanged) |
| Walker AAR stats | 132 drawable-xmls + 18 interpolator-xmls | + **106 layout-xmls** (32 appcompat + 9 core + 65 material) |

## §2 W4 phase status

- W3D4-δ-A~D: **CLOSED ✓**
- W4-A (drawable feed): **CLOSED ✓**
- W4-B-A/B/C (chip trilogy): **CLOSED ✓**
- W4-D (runtime-classpath.txt hardening): **CLOSED ✓**
- W4-NEXT phase 1 (interpolator XML feed): **CLOSED ✓** (`8fc426d`)
- **W4-NEXT phase 2 (AAR `res/layout` feed): CLOSED ✓** (next commit)
- W4-NEXT phase 3 (PNG / qualifier drawable feed for TextInputLayout's `abc_item_background_holo_light` StateListDrawable chain): **OPEN — next session entry, plan v6 + Round 7 territory**
- W4-C (namespace-aware mode): hold (no trigger)
- W4-E (R$styleable layer): hold (no trigger)

## §3 Next session entry — AAR PNG / qualifier drawable feed (LM-W4-NEXT-PHASE3-SCOPE)

### §3.1 fail surface

The textInputLayout IT (`@Disabled`) sanity probe captured the concrete trigger:

```
Failed to parse file axp/drawable/abc_item_background_holo_light.xml
XmlPullParserException: Binary XML file line #20: <item> tag requires a 'drawable' attribute or child tag defining a drawable
  at android.graphics.drawable.StateListDrawable.inflateChildElements(StateListDrawable.java:195)
  at android.graphics.drawable.StateListDrawable.inflate(StateListDrawable.java:126)
  at android.graphics.drawable.DrawableInflater.inflateFromXmlForDensity(DrawableInflater.java:141)
  at com.google.android.material.internal.CheckableImageButton.<init>(CheckableImageButton.java:56)
```

`?attr/actionBarItemBackground` resolves to `@drawable/abc_item_background_holo_light` (an AppCompat StateList drawable, 1909 bytes). Our W4-A drawable feed captures this XML body. But `StateListDrawable.inflateChildElements` parses each `<item>` and tries `getResources().getDrawable(R.drawable.abc_list_selector_disabled_holo_light)` — and that target is a 9-patch PNG in the qualifier directory `res/drawable-mdpi-v4/` (and xxhdpi-v4, etc.). Our walker only enumerates `res/drawable/<name>.xml` (default qualifier, XML only).

### §3.2 mechanism + scope

`abc_item_background_holo_light.xml` `<item>` chain references:
- `@drawable/abc_list_selector_disabled_holo_light` — 9-patch PNG, qualifier dirs (mdpi-v4, xxhdpi-v4).
- `@drawable/abc_list_selector_background_transition_holo_light` — XML, default dir (already in our bundle).
- `@drawable/abc_list_focused_holo` — 9-patch PNG, qualifier dirs.

Phase 3 scope:
1. **PNG / 9-patch enumeration** — walker extends to `res/drawable/<name>.png`, `res/drawable/<name>.9.png`, plus density qualifier dirs `res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}{-v4,-vNN}/<name>.{png,9.png}`. Binary content, NOT raw XML — fundamentally different feed shape from the 5 existing XML feeds.
2. **Density qualifier selection** — pick the right density variant for our target hardware config (XHIGH per SessionConstants). ResourceQualifier matching logic.
3. **9-patch decode** — `.9.png` files have 1px padding patches that android.graphics.NinePatch decodes via native code. Layoutlib has NinePatch_Delegate but the chrome strip parsing happens in native.
4. **Consumer bytecode disasm** — `Resources_Delegate.getDrawable` PNG path (likely AssetManager-based, not callback.getParser); also AppCompat-side `ResourceManagerInternal.loadDrawableFromDelegates` PNG path.

Plan v6 + Round 7 territory. Empirical probe budget ~120-180 minutes (substantially larger than phase 1/2's 60-75 min budgets).

### §3.3 trigger

The `@Disabled` textInputLayout IT is still the trigger. Removing the annotation surfaces the StateListDrawable error message (already captured in this session's sanity probe).

## §4 Carry-forward LMs (combined 39 — W3D4 phase 19 + W4-D 2 + W4-B 9 + W4-NEXT 9)

| LM | 1-line summary |
|---|---|
| LM-W3D4-β-D~H | KDoc backtick + assertNotNull ban + IT-tag + subagent unzip /tmp + acceptance fail surface classification |
| LM-W3D4-γ-A~C | Reviewer prompt + Long.decode + ThemeEnforcement multi-sentinel |
| LM-W3D4-δ-A~I | spec verify / NsBucket type-specific / runtime-classpath assertion / IT-tagged RED-on-main / entry-point comparison / Rule 2 OUT-OF-SCOPE / annotation message technical-claim verify / chain-walker IT render-path divergence / RenderResources.applyStyle clear() ban |
| LM (CLAUDE.md Three Hard Rules) | All new code KDoc / inline English-only, function/structure-only |
| LM-G | codex sandbox bypass via direct CLI (planning phase only) |
| LM-G2 (NEW from this session) | codex exec direct CLI requires `< /dev/null` to close stdin; otherwise hangs at "Reading additional input from stdin..." See `feedback_codex_exec_stdin_redirect.md` memory entry |
| LM-W4-D-A | reviewer agent catches new string literals + KDoc modification-history phrasing |
| LM-W4-D-B | Codex CLI explicit `--model` flag rejected by ChatGPT account; default model + reasoning effort only |
| LM-W4-B-A (close ✓ — `c126512`) | Chip stateListAnimator callback bypass — root cause = StyleItem body whitespace |
| LM-W4-B-B (close ✓ — `e78d969`) | Chip TextAppearance NPE — root cause = `<macro>` element parser miss |
| LM-W4-B-C (carry) | Subagent hypothesis is hypothesis. file:line read is ground truth |
| LM-W4-B-D (carry) | Kotlin helper default-arg + trailing-lambda call-pattern incompatibility |
| LM-W4-B-E (vindicated 5x, carry) | Handoff hypothesis is hypothesis. 60-120 min empirical probe (bytecode disasm + runtime probe) before plan-writing. NOW vindicated 5 times: W4-B-A α/β/γ rejection, W4-B-B path α/β/γ rejection, W4-B-C bytecode-evidence-first investigation, W4-NEXT phase 1 interpolator surface, W4-NEXT phase 2 layout surface |
| LM-W4-B-F (carry) | NamespaceAwareValueParser top-level `<item type=...>` trim not applied — extend on regression |
| LM-W4-B-G (carry) | AAPT2 silent feature gap pattern |
| LM-W4-B-H (close ✓ — `c4475c3`) | Chip drawable abc_vector_test — per-name `.xml`-suffixed placeholder |
| LM-W4-B-I (carry) | Material widget strict-consumer pattern — strict-consumer chain diagnosis budget |
| LM-W4-B-J (carry, fully verified) | Sibling-routing asymmetry — disasm `Resources_Delegate.get<Type>` + inner `ResourceHelper.get<Type>` for any new ResourceType-routed XML feed. Verified for getDrawable (W4-B-C), AppCompatResources.getDrawable (W4-NEXT phase 1), and getLayout (W4-NEXT phase 2 — no .xml gate, no LayoutCache, identical to getXml/getAnimation) |
| LM-W4-B-K (carry) | `sDrawableCache` JVM-static; cross-render staleness theoretical |
| LM-W4-B-L (vindicated 4x, carry) | Refactor-on-Sight self-audit BEFORE reviewer run. Now vindicated by W4-B-A NamespaceAwareValueParser, W4-B-C AppLibraryResourceConstants+IT, W4-NEXT phase 1 AarResourceWalker+LayoutlibResourceBundle, AND W4-NEXT phase 2 LayoutlibRenderer (52 Korean lines) + ParsedNsEntry (1 line in AnimatorXml KDoc) + SessionParamsFactoryTest (full-file). Discipline: every diff hunk in non-English file triggers full-file scan, not per-hunk |
| LM-W4-NEXT-A (carry, from W4-NEXT phase 1 pair-review Q5) | Widget extension wins can be lucky-value false positives inside RES_AUTO collapsed bucket. Bundle dup log assertion or documented rationale recommended for new widget IT acceptance gates |
| LM-W4-NEXT-B (carry) | `/tmp/w4*` artefact decay between sessions; handoff citations are pointers, regenerate before citing |
| LM-W4-NEXT-C (carry, fully closed for LAYOUT) | Pre-plan disasm of layoutlib + AppCompat sibling routing for LAYOUT verified. AppCompat sibling for LAYOUT not invoked (LayoutInflater is layoutlib-side only). Pattern remains for future ResourceType feeds |
| LM-W4-NEXT-MOTION (close ✓ — `8fc426d`) | MotionUtils.resolveThemeInterpolator TYPE_STRING gate — closed by W4-NEXT phase 1 |
| **LM-W4-NEXT-LAYOUT (close ✓ — next commit)** | TextInputLayout `design_text_input_start_icon` LAYOUT callback gap — closed by W4-NEXT phase 2 (this session) via δ1 mechanical W4-A-sibling mirror |
| LM-W4-NEXT-MOTIONUTILS-A (carry) | Strict-consumer pattern via Theme.resolveAttribute + TypedValue.type assertion is distinct from resourceId-only consumer pattern. Placeholder shapes must avoid `@`/`#`/`true`/`false`/parseInt/parseFloatAttribute prefixes for TYPE_STRING fall-through |
| LM-W4-NEXT-AAPT-XMLS (carry, from Round 7 Q1+Q2) | Mechanical 5th sibling. Refactor trigger = 2nd concrete future XML feed surfaces empirically. Phase 3 (PNG / 9-patch) is binary, NOT raw-XML, so does NOT generalize to a registry abstraction |
| LM-W4-NEXT-CODEX-Q4-VINDICATED | Codex's pair-review pre-emptively named the future fail surface 3 sessions in a row (W4-B-C drawable Q5 KILL POINT, W4-NEXT phase 1 layout Q4 + Q6 walker scope, W4-NEXT phase 2 bypass Q5 KILL POINT). Planning-phase pair-review discipline empirically load-bearing |
| **LM-W4-NEXT-LAYOUT-BYPASS (NEW, carry)** | `BridgeInflater.inflate(int, ViewGroup)` 2-arg overload bypasses callback. Defensive log in `createXmlParserForFile` detects bypass attempts. Closing requires thicker `createXmlParserForFile` override; defer until concrete trigger |
| **LM-W4-NEXT-LAYOUT-DUPS (NEW, carry, from Codex Q4)** | Duplicate layout names across AARs (RES_AUTO collapse risk). Bundle dup-log + first-wins-with-byType-preserved test contract covers it |
| **LM-W4-NEXT-PHASE3-SCOPE (NEW, carry)** | Phase 3 (PNG / qualifier drawable feed) — substantially more complex than phase 2. Plan v6 + Round 7 territory. Probe budget ~120-180 min. PNG enumeration + density qualifier selection + 9-patch decode + AppCompat consumer bytecode |

## §5 Starting environment sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
(next commit) feat(w4-next-b): AAR res/layout feed — TextInputLayout's design_text_input_start_icon now resolves through callback
8fc426d feat(w4-next-a): interpolator XML feed mirror — MotionUtils.resolveThemeInterpolator TYPE_STRING gate now satisfied
c663d24 docs(w4-b-c): paste-ready txt prompt for W4 next-candidate scope decision
c4475c3 feat(w4-b-c): drawable placeholder per-name xml suffix — chip drawable resolution now wires through callback
e78d969 feat(w4-b-b): macro element parsing — chip textAppearance via @macro/... indirection now resolves

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 288 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
... 27 IT PASS + 1 SKIP (textInputLayout @Disabled) ...
BUILD SUCCESSFUL — 27 IT PASS + 1 SKIP.
```

Regression guards: §1.1 table + new test classes + extended walker stats assertion + 2 createXmlParserForFile bypass-detection cases.

Debug toggles (optional):
- `-Daxp.debug.bundleShape=true` (LayoutlibResourceValueLoader bootstrap diagnostics)
- `-Daxp.debug.callback=true` (BridgeContext callback layer instrumentation)

`/tmp/w4-layout/*` artefacts (regenerable per LM-W4-NEXT-B):
- `Resources_Delegate-disasm.txt` (`getLayout` 829-872, `getXml` 1679-, `getAnimation` 877-)
- `ResourceHelper-disasm.txt` (`getXmlBlockParser` 415-461)
- `BridgeInflater-disasm.txt` (`inflate(int, ViewGroup)` bypass 509-590)
- `LayoutInflater-disasm.txt` (`inflate(int, ViewGroup, boolean)` 403-, parseInclude 1750)
- `ParserFactory-disasm.txt` (`create(String, boolean)` 26-54)
- `material-aar/res/layout/design_text_input_start_icon.xml` (the inflated layout body)
- `probe-findings.md` (empirical pre-plan probe summary)
- `codex-pair-prompt.md` + `codex-pair-response.md` (Round 7 planning pair-review)
- `claude-pair-answer.md` (Claude independent answer, sealed against Codex bleed-through)
- `round7-consolidation.md` (Round 7 verdict + amendments A1-A5)
- `codex-review-prompt.md` + `codex-review-response.md` (implementation-phase Codex sanity-check)

## §6 W4 phase summary

W4-NEXT phase 2 (AAR `res/layout` feed) is closed via Round 7-converged δ1 path. The pattern is now consistent across the W4-A-sibling family: each new ResourceType feed adds a placeholder constant, a `ParsedNsEntry` data class, an `NsBucket` field, a walker `collect*Xmls` method, a bundle accessor + `buildBucket` branch, a callback `getParser` `when` branch, a renderer call-site lookup, and 2 contract test classes. Total per-phase size has been steady at 14-16 files modified. The redundancy is real (LM-W4-NEXT-AAPT-XMLS-CARRY) but the registry refactor remains premature — phase 3 (PNG / 9-patch) breaks the raw-XML feed abstraction entirely.

W4-NEXT phase 3 (PNG / qualifier drawable feed) is the next gate. The textInputLayout IT remains `@Disabled` until phase 3 closes. Plan v6 + Round 7 Codex+Claude planning pair-review on the design before implementation.
