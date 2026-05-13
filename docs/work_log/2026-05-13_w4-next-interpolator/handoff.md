# W4-NEXT (interpolator XML feed) close — handoff

Date: 2026-05-13. Next session entry: **AAR `res/layout` feed (LM-W4-NEXT-LAYOUT) — plan v6 + Round 7 Codex+Claude planning pair-review**.

Predecessor head: (next commit) `feat(w4-next-a): interpolator XML feed mirror — MotionUtils.resolveThemeInterpolator TYPE_STRING gate now satisfied`.

---

## §1 Where we ended — W4-NEXT green state

W4-NEXT phase 1 (interpolator XML feed) closed by mirroring the W4-A drawable XML feed pattern for `ResourceType.INTERPOLATOR`. Material 1.12.0's 18 `res/interpolator/m3_sys_motion_easing_*.xml` files now flow through:

- `AarResourceWalker.collectInterpolatorXmls` → `ParsedNsEntry.InterpolatorXml`
- `LayoutlibResourceBundle.buildBucket` ParsedNsEntry.InterpolatorXml branch → `byType[INTERPOLATOR]` placeholder + `interpolators` raw-XML map
- `MinimalLayoutlibCallback.getParser` ResourceType.INTERPOLATOR branch → `SelectorXmlPullParser.fromString`
- `LayoutlibRenderer` passes `bundle.getInterpolatorXml(ref)` as the 6th MinimalLayoutlibCallback arg

The placeholder shape `axp/interpolator/<name>.xml` is the W4-B-C-pattern path-style value. It satisfies the `MotionUtils.resolveThemeInterpolator` `TypedValue.type == TYPE_STRING (3)` assertion because `BridgeContext.resolveThemeAttribute` (offsets 305-313 of layoutlib-14.0.11.jar) falls through to TYPE_STRING when the value does not start with `#`/`@`/`true`/`false`, fails `Integer.parseInt`, and fails `ResourceHelper.parseFloatAttribute`. Dual-source review (Claude code-reviewer subagent + Codex direct CLI) both APPROVE on the bytecode-grounded logic. Codex independently corroborated the offset 316-338 common-tail behavior, vindicating the Claude reviewer Finding 4 pushback.

### §1.1 Test posture (next commit on main)

| Metric | post-W4-B-C (`c4475c3`) | post-W4-NEXT |
|---|---|---|
| Cross-module unit | 258 PASS / 0 fail | **272 PASS / 0 fail** (+14) |
| layoutlib-worker IT | 27 PASS + 0 SKIP | **27 PASS + 1 SKIP** (textInputLayout @Disabled pending AAR `res/layout` feed) |
| `LayoutlibResourceBundleInterpolatorTest` | n/a | **7/7 PASS** |
| `AarResourceWalkerInterpolatorTest` | n/a | **4/4 PASS** |
| `MinimalLayoutlibCallbackColorParserTest` | 8 cases | **10 cases** (+ INTERPOLATOR miss + hit) |
| T17 / T20 / MaterialFidelity / chip / basic / minimal | all PASS | all PASS (unchanged) |
| Walker AAR stats | 132 drawable-xmls | + **18 interpolator-xmls** |

## §2 W4 phase status

- W3D4-δ-A~D: **CLOSED ✓**
- W4-A (drawable feed): **CLOSED ✓**
- W4-B-A/B/C (chip trilogy): **CLOSED ✓**
- W4-D (runtime-classpath.txt hardening): **CLOSED ✓**
- **W4-NEXT phase 1 (interpolator XML feed for MotionUtils gate): CLOSED ✓** (next commit)
- W4-NEXT phase 2 (AAR `res/layout` feed for TextInputLayout's `design_text_input_start_icon`): **OPEN — next session entry, plan v6 territory**
- W4-C (namespace-aware mode): hold (no active trigger)
- W4-E (R$styleable layer): hold (no active trigger)

## §3 Next session entry — AAR `res/layout` feed (LM-W4-NEXT-LAYOUT)

### §3.1 fail surface

The textInputLayout IT (`@Disabled`) captures the concrete trigger:

```
[axp.probe] getParser type=interpolator ns=RES_AUTO name=m3_sys_motion_easing_emphasized_decelerate value=axp/interpolator/m3_sys_motion_easing_emphasized_decelerate.xml
[axp.probe] getParser type=interpolator ns=RES_AUTO name=m3_sys_motion_easing_linear value=axp/interpolator/m3_sys_motion_easing_linear.xml
[axp.probe] getParser type=layout ns=RES_AUTO name=design_text_input_start_icon value=design_text_input_start_icon
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION msg=No Input specified (position:START_DOCUMENT null@0:0) exc=XmlPullParserException
```

The interpolator path resolves correctly (callback HIT for 3 interpolator entries). The new fail = TextInputLayout requests `design_text_input_start_icon` (ResourceType.LAYOUT) — an AAR-internal layout XML at `res/layout/design_text_input_start_icon.xml` inside `material-1.12.0.aar`. Our `AarResourceWalker` does not yet enumerate `res/layout/*.xml`, the bundle has no LAYOUT entries, and `MinimalLayoutlibCallback.getParser` does not route ResourceType.LAYOUT. Bridge falls back to `ParserFactory.create(value)` which returns a parser with no input → "No Input specified".

### §3.2 mechanism

`Resources_Delegate.getXml(int)` route via `ResourceHelper.getXmlBlockParser(BridgeContext, ResourceValue)`:
- offset 26-41: if `value.isFramework() == false`, calls `LayoutlibCallback.getParser(value)`
- If callback returns null → falls back to `ParserFactory.create(value)` (blank parser, no input)

Our callback returns null for ResourceType.LAYOUT because we never added that branch. The walker's value fallback (Resources_Delegate.getResourceValue at offset 187-217) uses `name` as `value` field when our bundle has no entry, so the value the callback sees is `design_text_input_start_icon` (a bare name, not even a path).

### §3.3 fix candidates — for the plan v6 round to evaluate

Codex's planning-phase Q4 trigger named this specifically as plan v6 territory rather than W4-A-sibling work. Possible design directions:

- **(δ1) Direct W4-A-sibling mirror** — add `collectLayoutXmls` + `ParsedNsEntry.LayoutXml` + bundle byType[LAYOUT] entries + callback ResourceType.LAYOUT routing. Smallest delta from existing pattern but introduces the fifth W4-A-sibling helper without addressing the redundancy of the copy-mirror approach (LM-W4-NEXT-AAPT-XMLS).
- **(δ2) Data-driven feed registry** — refactor the four existing sibling helpers (color / animator / drawable / interpolator) and the new layout helper into a single data-driven registry. Higher initial cost but pays back on the next AAR resource type (font / raw / transition / mipmap / xml). Wider regression surface — T17 + T20 + MaterialFidelity + chip + basic + minimal must remain green at every step.
- **(δ3) Selective layout pass** — only enumerate layout XMLs that are actually requested by integration tests during a probe run, rather than all `res/layout/*.xml` in all 41 AARs. Smaller bundle footprint but adds a runtime feedback loop the walker doesn't have today.

The plan v6 round should also evaluate:
- Whether the W4-B-C drawable placeholder shape (`axp/drawable/<name>.xml`) and W4-NEXT interpolator placeholder shape (`axp/interpolator/<name>.xml`) generalize to `axp/<resource-type>/<name>.xml` as a single helper.
- Whether `Resources_Delegate.getLayout(Resources, int)` has its own value-format gate (LM-W4-B-J operationalization for the LAYOUT sibling). Disasm `Resources_Delegate-full-disasm.txt` for `static *.getLayout(Resources, int)`.
- Whether `BridgeInflater.inflate(XmlPullParser, ViewGroup, boolean)` reads the parser in a way that's compatible with `SelectorXmlPullParser.fromString` — `LayoutInflater` is a different consumer from `DrawableInflater` / `AnimatorInflater`.

### §3.4 trigger

The `@Disabled` textInputLayout IT is the trigger. Removing the annotation surfaces the layout feed fail surface immediately.

### §3.5 cost

Plan v6 + Round 7 Codex+Claude planning pair-review. Empirical probe budget ~120 minutes (LayoutInflater consumer pattern likely requires more disasm + runtime probes than the prior 75-min interpolator probe). Implementation depth depends on δ1/δ2/δ3 choice.

## §4 Carry-forward LMs (combined 35 — W3D4 phase 19 + W4-D 2 + W4-B 9 + W4-NEXT 5)

| LM | 1-line summary |
|---|---|
| LM-W3D4-β-D~H | KDoc backtick + assertNotNull ban + IT-tag + subagent unzip /tmp + acceptance fail surface classification |
| LM-W3D4-γ-A~C | Reviewer prompt + Long.decode + ThemeEnforcement multi-sentinel |
| LM-W3D4-δ-A~I | spec verify / NsBucket type-specific / runtime-classpath assertion / IT-tagged RED-on-main / entry-point comparison / Rule 2 OUT-OF-SCOPE / annotation message technical-claim verify / chain-walker IT render-path divergence / RenderResources.applyStyle clear() ban |
| LM (CLAUDE.md Three Hard Rules) | All new code KDoc / inline English-only, function/structure-only |
| LM-G | codex sandbox bypass via direct CLI (planning phase only) |
| LM-W4-D-A | reviewer agent catches new string literals + KDoc modification-history phrasing |
| LM-W4-D-B | Codex CLI explicit `--model` flag rejected by ChatGPT account; use default model + reasoning effort only |
| LM-W4-B-A (close ✓ — `c126512`) | Chip stateListAnimator callback bypass — root cause = StyleItem body whitespace |
| LM-W4-B-B (close ✓ — `e78d969`) | Chip TextAppearance NPE — root cause = `<macro>` element parser miss |
| LM-W4-B-C (carry) | Subagent hypothesis is hypothesis. file:line read is ground truth |
| LM-W4-B-D (carry) | Kotlin helper default-arg + trailing-lambda call-pattern incompatibility |
| LM-W4-B-E (vindicated 4x, carry) | Handoff hypothesis is hypothesis. 60-90 min empirical probe (bytecode disasm + runtime probe) before plan-writing. Now vindicated by W4-B-A α/β/γ rejection, W4-B-B path α/β/γ rejection, W4-B-C bytecode-evidence-first investigation, AND W4-NEXT interpolator surface |
| LM-W4-B-F (carry) | NamespaceAwareValueParser top-level `<item type=...>` trim not applied — extend on regression |
| LM-W4-B-G (carry) | AAPT2 silent feature gap pattern — `<macro>` (Material 3) + future `<sample-data>` / `<overlayable>` / `<style-item>` |
| LM-W4-B-H (close ✓ — `c4475c3`) | Chip drawable abc_vector_test lookup callback bypass — per-name `.xml`-suffixed placeholder |
| LM-W4-B-I (carry) | Material widget strict-consumer pattern — strict-consumer chain diagnosis budget for new widgets |
| LM-W4-B-J (carry, partially verified) | Sibling-routing asymmetry — disasm `Resources_Delegate.get<Type>` + inner `ResourceHelper.get<Type>` for any new ResourceType-routed XML feed. Verified for AppCompatResources.getDrawable in W4-NEXT pre-plan disasm (.xml gate at ResourceManagerInternal.loadDrawableFromDelegates offset 152-157, satisfied by W4-B-C placeholder) |
| LM-W4-B-K (carry) | `sDrawableCache` JVM-static; cross-render staleness theoretical, out-of-scope until real fail |
| LM-W4-B-L (carry, vindicated again) | Refactor-on-Sight self-audit BEFORE reviewer run. Caught in W4-NEXT for AarResourceWalker + LayoutlibResourceBundle Korean comments |
| LM-W4-NEXT-A (carry, from pair-review Q5) | Widget extension wins can be lucky-value false positives inside RES_AUTO collapsed bucket. Bundle dup log assertion or documented rationale recommended for new widget IT acceptance gates |
| LM-W4-NEXT-B (carry, from pair-review Q6) | Bytecode artefacts under `/tmp/w4*` decay between sessions; handoff citations are pointers, not artefacts — regenerate before citing |
| LM-W4-NEXT-C (carry, partially closed) | Pre-plan disasm of AppCompat sibling routing operationalized for W4-NEXT. AppCompatResources.getDrawable confirmed gate-compatible. Pattern: every new ResourceType feed should disasm both layoutlib and AppCompat sibling entry points before plan-writing |
| **LM-W4-NEXT-MOTION (close ✓ — next commit)** | MotionUtils.resolveThemeInterpolator TYPE_STRING gate — root cause = chain walker resolved `?attr/motionEasing*Interpolator` to `@interpolator/...` string, BridgeContext set TypedValue.type=TYPE_REFERENCE (1), MotionUtils asserts TYPE_STRING (3). Per-name path-style placeholder `axp/interpolator/<name>.xml` falls through to TYPE_STRING via parseFloatAttribute-fail branch |
| **LM-W4-NEXT-LAYOUT (new, carry — next session)** | AAR `res/layout` feed empirically triggered by TextInputLayout via `design_text_input_start_icon`. Treat as plan v6 / Round 7 scope, not a W4-A raw-XML sibling quick fix. δ1 / δ2 / δ3 candidates documented in §3.3 |
| **LM-W4-NEXT-MOTIONUTILS-A (new, carry)** | Strict-consumer pattern via Theme.resolveAttribute + TypedValue.type assertion is distinct from the resourceId-only consumer pattern (chip stateListAnimator). Any new placeholder shape must avoid `@`, `#`, `true`/`false`, parseInt-able, and parseFloatAttribute-able prefixes for the TYPE_STRING fall-through |
| **LM-W4-NEXT-AAPT-XMLS (new, carry — design)** | The W4-A drawable mirror has now been applied 3 times (animator W3D4-δ-D, drawable W4-B, interpolator W4-NEXT). A 4th (LAYOUT) and possibly 5th+ are next. The mechanical copy-mirror is showing redundancy — plan v6 design discussion on whether to refactor into a data-driven feed registry |
| **LM-W4-NEXT-CODEX-Q4-VINDICATED (new)** | Codex's planning-phase Q4 trigger criterion ("AAR `res/layout` feed = plan v6 territory") and Q6 critique ("walker only advertises values/color/animator/drawable, not res/layout") both empirically vindicated by the TextInputLayout probe. Second consecutive case (after the W4-B-C session-log's Q5 vindication) where Codex's pair-review pre-emptively caught the future fail surface |

## §5 Starting environment sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
(next commit) feat(w4-next-a): interpolator XML feed mirror — MotionUtils.resolveThemeInterpolator TYPE_STRING gate now satisfied
c663d24 docs(w4-b-c): paste-ready txt prompt for W4 next-candidate scope decision
c4475c3 feat(w4-b-c): drawable placeholder per-name xml suffix — chip drawable resolution now wires through callback
e78d969 feat(w4-b-b): macro element parsing — chip textAppearance via @macro/... indirection now resolves
c126512 feat(w4-b-a): style item body trim — chip stateListAnimator callback now wires correctly

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 272 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
... 27 IT PASS + 1 SKIP (textInputLayout @Disabled) ...
BUILD SUCCESSFUL — 27 IT PASS + 1 SKIP.
```

Regression guards: handoff §1.1 table + `LayoutlibResourceBundleInterpolatorTest` 7 cases + `AarResourceWalkerInterpolatorTest` 4 cases + `MinimalLayoutlibCallbackColorParserTest` 10 cases (added INTERPOLATOR miss/hit).

Debug toggles (optional):
- `-Daxp.debug.bundleShape=true` (LayoutlibResourceValueLoader bootstrap diagnostics)
- `-Daxp.debug.callback=true` (BridgeContext callback layer instrumentation)

`/tmp/w4-next/*` and `/tmp/w4-tinput/*` artefacts (cleanup-policy independent — bytecode evidence for future similar investigations):
- `/tmp/w4-next/MotionUtils-disasm.txt` (`resolveThemeInterpolator` offset 0-72)
- `/tmp/w4-tinput/BridgeContext-disasm.txt` (`resolveThemeAttribute` offset 95-350)
- `/tmp/w4-tinput/Resources_Delegate-full-disasm.txt` (`getXml` 1679-1716, `getAnimation` 877-918, `getResourceValue` 183-219)
- `/tmp/w4-tinput/ResourceHelper-full.txt` (`getXmlBlockParser` 415-461, `parseFloatAttribute` referenced offset 75-121)
- `/tmp/w4-tinput/ResourceManagerInternal-disasm.txt` (`loadDrawableFromDelegates` .xml gate offset 152-157)
- `/tmp/w4-tinput/AppCompatResources-disasm.txt` (thin wrapper to ResourceManagerInternal.getDrawable)
- `/tmp/w4-next/codex-pair-prompt.md` + `/tmp/w4-next/codex-pair-response.md` (planning-phase pair-review record)
- `/tmp/w4-next/claude-pair-answer.md` (Claude independent pair-review answer, sealed against Codex bleed-through)
- `/tmp/w4-next/codex-review-response.md` (implementation-phase Codex sanity-check, APPROVE)

## §6 W4 phase summary

W4-NEXT phase 1 (interpolator XML feed) is closed. The pattern observed: TextInputLayout opened **two** escalation surfaces simultaneously — MotionUtils interpolator gate (closed by W4-A-sibling) and AAR `res/layout` feed (carry to plan v6). Codex's planning-phase pair-review pre-emptively named both surfaces (Q4 trigger criterion + Q6 walker scope critique), vindicating the discipline of running Round 7 pair-review on path selection before implementation.

W4 phase is now in **"phase 2 entry"** mode — the textInputLayout IT @Disabled is the carry, and the next session should run a plan v6 + Round 7 Codex+Claude planning pair-review on the AAR `res/layout` feed design (δ1 mechanical mirror vs δ2 data-driven registry vs δ3 selective).
