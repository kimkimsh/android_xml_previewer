# W4 candidate B-C close — handoff (W4-B trilogy fully closed)

Date: 2026-05-08 (created). Next session entry: **W4 next candidate scope decision** — no active fail surface, priority decision required.

Predecessor head: (next commit) `feat(w4-b-c): drawable placeholder per-name xml suffix — chip drawable resolution now wires through callback`

---

## §1 Where we ended — W4-B-C green state

### §1.1 W4 candidate B-C closed — per-name `.xml` placeholder fix

The chip drawable `abc_vector_test` lookup that bypassed `LayoutlibCallback.getParser` is now resolved by replacing the static `DRAWABLE_PLACEHOLDER_VALUE = "@axp:drawable-xml"` constant with a per-name path-shaped helper `drawablePlaceholderValue(name) = "axp/drawable/" + name + ".xml"`. The new shape simultaneously satisfies two non-obvious layoutlib contracts uncovered by bytecode disassembly of `layoutlib-14.0.11.jar`:

- `ResourceHelper.getDrawable` (`/tmp/w4bc/ResourceHelper-disasm.txt` offsets 132-158) routes through `getXmlBlockParser` — and therefore through `LayoutlibCallback.getParser` — only when `value.toLowerCase().endsWith(".xml")` OR `resourceType == AAPT`. A DRAWABLE entry whose value lacks the `.xml` suffix falls through to the asset / file resource path (offset 339+), which fails `AssetRepository.isFileResource` and returns null. `Resources_Delegate.getDrawable` (offset 80-91) then raises `Resources$NotFoundException` with the message `"Could not find … resource matching value 0x… (resolved name: …) in current configuration."` (constant `#878`).

- `Resources_Delegate.getDrawable` (`/tmp/w4bc/Resources_Delegate-disasm.txt` offsets 24-44 and 109-119) consults a JVM-static `sDrawableCache` (`LruCache`) keyed by the value string, both before delegating to `ResourceHelper.getDrawable` and after a successful resolution. A single shared placeholder for all drawables would alias every subsequent lookup to the first drawable's `ConstantState`.

`Resources_Delegate.getAnimation` (offset 23) calls `getXmlBlockParser` with no `.xml` gate, which is why the existing `ANIMATOR_PLACEHOLDER_VALUE = "@axp:animator-xml"` is unaffected. The ColorStateList path goes through `Resources_Delegate.getColorStateList` → `ResourceHelper.getColorStateList`, also no `.xml` gate. Only DRAWABLE needs the per-name path-shaped value.

### §1.2 Dual-source review

- **`feature-dev:code-reviewer` (Claude subagent)**: APPROVE on bytecode hypothesis + cache uniqueness + new test contract strength + new KDoc compliance. REVISE_REQUIRED on 5 IMPORTANT Refactor-on-Sight findings — pre-existing Korean / phase-identifier comments in the two source files we touched. All 5 applied in the same change per CLAUDE.md §4.
- **Codex direct CLI sanity-check** (codex-cli, default GPT model, reasoning effort xhigh): launched in parallel with the Claude reviewer; did not return within the session window (>30 min for the W4-B-C diff). Per CLAUDE.md §Codex implementation-phase exclusion, dual-source Codex sanity-check is optional. If the eventual Codex output surfaces a finding worth acting on, it lands in a follow-up commit.

This phase is implementation-only — Round 7 Codex+Claude planning pair-review NOT required (CLAUDE.md §Codex). Dual-source single-shot review is the W4-B-A/B precedent for parser-/constants-layer changes.

### §1.3 Test posture (next commit on main)

| Metric | post-W4-B-B (`e78d969`) | post-W4-B-C |
|---|---|---|
| Cross-module unit | 256 PASS / 0 fail | **258 PASS / 0 fail** (+2: drawable suffix + drawable uniqueness contract) |
| layoutlib-worker IT | 26 PASS + 1 SKIP (chip @Disabled) | **27 PASS + 0 SKIP** (chip un-disabled) |
| `LayoutlibResourceBundleDrawableTest` | 6 cases | **8 cases** |
| T17 5-probe regression guard | 5/5 PASS | **5/5 PASS** (unchanged) |
| T20 5-probe regression guard | 5/5 PASS | **5/5 PASS** (unchanged) |
| Acceptance gate `activity_basic` + `activity_basic_minimal` + `activity_minimal` glyph dark-pixel | PASS | PASS (unchanged) |
| `MaterialFidelityIntegrationTest` | 4/4 PASS | 4/4 PASS (unchanged) |

## §2 W4 phase status — fully closed

Active fail surface: 0. All 27 IT pass, 0 SKIP. The W4-B trilogy (drawable feed → trim → macro → drawable placeholder) closes the chip integration test as the first single-Material-widget acceptance gate beyond `activity_basic` (mixed widgets) and `activity_minimal` (TextView only).

```
W3D4-δ-A~D: CLOSED ✓
tier3-glyph: CLOSED ✓
W4-D (runtime-classpath.txt hardening): CLOSED ✓
W4-A (drawable XML feed mirror): CLOSED ✓
W4-B-drawable: CLOSED ✓
W4-B-A (chip stateListAnimator — style item body trim): CLOSED ✓ (c126512)
W4-B-B (chip TextAppearance via macro): CLOSED ✓ (e78d969)
W4-B-C (chip drawable abc_vector_test placeholder): CLOSED ✓ (next commit)
W4-C (namespace-aware mode): hold (no trigger)
W4-E (R$styleable layer): hold (no trigger)
```

## §3 Next session entry — W4 next candidate scope decision

User priority decision required. Three open options:

### path 1 — Widget extension (visual fidelity priority, recommended)

Add new fixture XML for one Material widget per session, surface its own escalation chain. Strong recommend if visual fidelity is the priority axis — the chip W4-B trilogy is the precedent for the depth pattern. Candidates in order of likely surface variety:

- `activity_textinputlayout.xml` — TextInputLayout + TextInputEditText. Likely surfaces TextAppearance + drawable + animator state interactions similar to chip.
- `activity_snackbar.xml` — Snackbar / BaseTransientBottomBar. Round 6 Codex Q4 census flagged `BaseTransientBottomBar.checkAppCompatTheme` as a separate path. Possibly distinct fail surface.
- `activity_materialswitch.xml` — MaterialSwitch. Compact widget, likely simpler.
- `activity_badge.xml` — BadgeDrawable. Standalone Drawable, distinct from view hierarchy.

Each new widget = potential multi-round escalation. Plan v6 + Round 7 Codex+Claude planning pair-review available if a widget surfaces a deep design decision (e.g. namespace-aware mode trigger, density qualifier support, or AAPT2-resource type beyond `<macro>`).

### path 2 — Namespace-aware mode (design integrity priority)

`docs/superpowers/specs/2026-05-XX-w4-namespace-aware.md` plan v6 + Round 7 Codex+Claude planning pair-review (CLAUDE.md §Codex). RES_AUTO collapse → ns-aware split. RJarSymbolSeeder + AarResourceWalker + LayoutlibResourceBundle.byNs all touched. No active fail trigger; only worth pursuing if cross-AAR same-name conflicts surface as a real bug, or if a future widget extension reveals one. LM-G + LM-W4-D-B apply (codex direct CLI, no `--model` flag, reasoning effort xhigh).

### path 3 — R$styleable layer (defer)

Hold — no active trigger. Future Material widget styled-attrs lookup failure would escalate.

## §4 Avoidable LMs (combined 30 — W3D4 phase 19 + W4-D 2 + W4-B 9)

| LM | 1-line summary |
|---|---|
| LM-W3D4-β-D~H | KDoc backtick + assertNotNull ban + IT-tag + subagent unzip /tmp + acceptance fail surface classification |
| LM-W3D4-γ-A~C | Reviewer prompt + Long.decode + ThemeEnforcement multi-sentinel |
| LM-W3D4-δ-A~I | spec verify / NsBucket type-specific / runtime-classpath assertion / IT-tagged RED-on-main / entry-point comparison / Rule 2 OUT-OF-SCOPE / annotation message technical-claim verify / chain-walker IT render-path divergence / RenderResources.applyStyle clear() ban |
| LM (CLAUDE.md Three Hard Rules) | All new code KDoc / inline English-only, function/structure-only |
| LM-G | codex sandbox bypass via direct CLI (planning phase only) |
| LM-W4-D-A | reviewer agent catches new string literals + KDoc modification-history phrasing |
| LM-W4-D-B | Codex CLI explicit `--model` flag rejected by ChatGPT account; use default model + reasoning effort only |
| LM-W4-B-A (close ✓ — `c126512`) | Chip stateListAnimator callback bypass — root cause = StyleItem body whitespace; NamespaceAwareValueParser.handleStyle trim |
| LM-W4-B-B (close ✓ — `e78d969`) | Chip TextAppearance NPE — root cause = `<macro>` element parser miss; NamespaceAwareValueParser.handleMacro |
| LM-W4-B-C (carry) | Subagent hypothesis is hypothesis. file:line read is ground truth |
| LM-W4-B-D (carry) | Kotlin helper default-arg + trailing-lambda call-pattern incompatibility |
| LM-W4-B-E (vindicated, carry) | Handoff-document hypothesis is hypothesis. 60-min empirical probe (bytecode disasm + runtime probe) before plan-writing. Triggered by W4-B-B path α/β/γ rejection AND W4-B-C bytecode-evidence-first investigation |
| LM-W4-B-F (carry) | NamespaceAwareValueParser top-level `<item type=...>` trim not applied — extend on regression |
| LM-W4-B-G (carry) | AAPT2 silent feature gap pattern — `<macro>` (Material 3) + future `<sample-data>` / `<overlayable>` / `<style-item>` |
| **LM-W4-B-H (close ✓ — next commit)** | Chip drawable abc_vector_test lookup callback bypass — root cause = `ResourceHelper.getDrawable` `.xml` gate + `Resources_Delegate.sDrawableCache` aliasing; per-name `.xml`-suffixed placeholder satisfies both contracts |
| LM-W4-B-I (carry) | Material widget strict-consumer pattern — strict-consumer chain diagnosis budget for new widgets |
| **LM-W4-B-J (new)** | Sibling-routing asymmetry — `Resources_Delegate.get<Type>` may have type-specific value-format gates. When adding new ResourceType-routed XML feeds, disasm both `Resources_Delegate.get<Type>` AND the inner `ResourceHelper.get<Type>` (if any) to confirm whether a value-format gate exists |
| **LM-W4-B-K (new)** | `sDrawableCache` is JVM-static; cross-render staleness is theoretical. Per-name uniqueness mitigates aliasing within a render. Out of scope for W4-B-C — fixture XML edit during a JVM run would re-emit the same placeholder |
| **LM-W4-B-L (new)** | Refactor-on-Sight obligation pattern — every multi-edit Kotlin source file must be self-audited for non-English text and phase identifiers BEFORE running the reviewer agent. Caught in W4-B-A (NamespaceAwareValueParser) AND W4-B-C (AppLibraryResourceConstants + LayoutlibRendererIntegrationTest). Standing pre-commit step now |

## §5 Starting environment sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
(next commit) feat(w4-b-c): drawable placeholder per-name xml suffix — chip drawable resolution now wires through callback
e78d969 feat(w4-b-b): macro element parsing — chip textAppearance via @macro/... indirection now resolves
c126512 feat(w4-b-a): style item body trim — chip stateListAnimator callback now wires correctly
080cedf docs(w4-b): paste-ready txt prompt for W4-B-A next-session entry
bf21dac feat(w4-b): drawable XML feed mirror + chip fixture entry — candidate A naturally triggered, parked at LM-W4-B-A/B for next phase

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 258 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
... 27 IT PASS + 0 SKIP ...
BUILD SUCCESSFUL — 27 IT PASS + 0 SKIP.
```

Regression guards: §1.3 table + `LayoutlibResourceBundleDrawableTest` 8 cases (suffix invariant + uniqueness invariant locked in).

Debug toggles (optional):
- `-Daxp.debug.bundleShape=true` (LayoutlibResourceValueLoader bootstrap diagnostics)
- `-Daxp.debug.callback=true` (BridgeContext callback layer instrumentation)

`/tmp/w4bc/*` artefacts (cleanup-policy independent — bytecode evidence for future similar investigations):
- `/tmp/w4bc/Resources_Delegate-disasm.txt` (`getDrawable` lines 221-292, `getAnimation` lines 877-918, `throwException` lines 2030-2056)
- `/tmp/w4bc/ResourceHelper-disasm.txt` (`getDrawable` lines 463-820, `getXmlBlockParser` lines 415-461)
- `/tmp/w4bc/full/strings.properties` (layoutlib resource string templates)
- `/tmp/w4bc/appcompat-aar/res/drawable/abc_vector_test.xml` (the actual back-arrow vector that triggered the W4-B-C surface)

## §6 W4 phase summary

The W4-B trilogy (chip widget integration) is fully closed. The pattern observed across W4-B-A → W4-B-B → W4-B-C is consistent: each Material 3 strict-consumer (chip's `ChipDrawable.loadFromAttributes`) surfaces a SILENT FEATURE GAP in our parser / bundle / placeholder layer that lenient consumers (basic IT's MaterialButton / TextView) tolerate. Bytecode-grounded empirical investigation (LM-W4-B-E vindicated by all three sub-phases) converted each gap into a small, localized fix backed by a contract test.

W4 phase is now in "next candidate" mode — choose a widget extension (visual fidelity priority) or a design-integrity initiative (namespace-aware mode), or hold for a real fail trigger.
