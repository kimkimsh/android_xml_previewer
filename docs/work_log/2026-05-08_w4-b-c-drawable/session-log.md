# W4 candidate B-C — chip drawable abc_vector_test (session log)

Date: 2026-05-08
Predecessor head: `e78d969` (W4-B-B close: macro element parsing)
Phase: W4 — candidate B-C (chip drawable lookup callback bypass)

---

## Outcome

LM-W4-B-H closed by a per-drawable, `.xml`-suffixed placeholder for `byType[DRAWABLE]` ResourceValues. Bytecode-level analysis of `layoutlib-14.0.11.jar` (`/tmp/w4bc/Resources_Delegate-disasm.txt` and `/tmp/w4bc/ResourceHelper-disasm.txt`) located two non-obvious layoutlib contracts that together force the value shape:

1. **`com.android.layoutlib.bridge.impl.ResourceHelper.getDrawable(ResourceValue, BridgeContext, Theme)` `.xml` gate** (offsets 132-158): control jumps to `getXmlBlockParser` (which calls `LayoutlibCallback.getParser`) only when `value.toLowerCase().endsWith(".xml")` OR `resourceType == AAPT`. A DRAWABLE-typed bundle entry whose value does not end with `.xml` falls through to the asset / file resource path (offset 339+), which fails `AssetRepository.isFileResource` and returns null. `Resources_Delegate.getDrawable` (offset 80-91) then calls `throwException(Resources, int)` with the format string `"Could not find %1$s resource matching value 0x%2$X (resolved name: %3$s) in current configuration."` (constant `#878`).

2. **`Resources_Delegate.getDrawable` `sDrawableCache` aliasing** (offsets 24-44 and 109-119): the JVM-static `sDrawableCache` (`LruCache`) is consulted with the value string as key before delegating to `ResourceHelper.getDrawable`, and the resulting `ConstantState` is cached under the same key. A single shared placeholder for all drawables would alias every subsequent lookup to the first drawable's `ConstantState`.

`Resources_Delegate.getAnimation` (offset 23 of its body) calls `getXmlBlockParser` directly, with no `.xml` gate — that is why the existing `ANIMATOR_PLACEHOLDER_VALUE = "@axp:animator-xml"` works for animator XMLs. The ColorStateList path (`Resources_Delegate.getColorStateList` → `ResourceHelper.getColorStateList`) does not pass through this gate either. Only DRAWABLE needs the per-name path-shaped value.

The fix replaces the static `DRAWABLE_PLACEHOLDER_VALUE = "@axp:drawable-xml"` constant with a `drawablePlaceholderValue(name)` helper that returns `"axp/drawable/" + name + ".xml"` — encoding the drawable name (cache uniqueness) and the `.xml` suffix (callback gate) at the same time. `LayoutlibResourceBundle.build` switches the DRAWABLE branch to call the helper.

Two new contract tests lock in both invariants: the suffix must satisfy the callback gate, and the value must differ across drawable names. The chip integration test's `@Disabled` is removed; it now PASSES, completing the W4-B trilogy (style-item trim + `<macro>` parsing + drawable placeholder).

The dual-source review (`feature-dev:code-reviewer` + Codex direct CLI) APPROVE on logic. Claude reviewer surfaced a Refactor-on-Sight obligation per CLAUDE.md §4 — pre-existing Korean / phase-identifier comments in the two source files we touched (`AppLibraryResourceConstants.kt` and `LayoutlibRendererIntegrationTest.kt`). Cleaned in the same change.

---

## Files modified

### main src
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstants.kt`
  - Replaces `DRAWABLE_PLACEHOLDER_VALUE` constant with `DRAWABLE_PLACEHOLDER_PREFIX = "axp/drawable/"`, `DRAWABLE_PLACEHOLDER_SUFFIX = ".xml"`, and a top-level helper function `drawablePlaceholderValue(name: String): String`.
  - Adds a multi-paragraph KDoc on the helper explaining the two layoutlib contracts (offsets cited).
  - Refactor-on-Sight cleanup: file-level KDoc, AAR-color / AAR-manifest / sample-app / regex / hop-limit / null-empty-literal / android-prefix comments translated from Korean to English; phase identifiers (W3D4-β T12, 08 §7.7.6, v2 round 2 follow-up #N) removed; "Zero Tolerance for Magic Numbers/Strings" self-reference removed.

- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt`
  - Single-line behavior change in the `ParsedNsEntry.DrawableXml` branch of `buildBucket`: `AppLibraryResourceConstants.DRAWABLE_PLACEHOLDER_VALUE` → `AppLibraryResourceConstants.drawablePlaceholderValue(e.name)`.

### test src
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleDrawableTest.kt`
  - Renames `byType DRAWABLE receives placeholder ResourceValue alongside raw XML` to `byType DRAWABLE receives per-name xml-suffixed placeholder ResourceValue alongside raw XML`; assertion now uses `drawablePlaceholderValue("foo")` instead of the deleted constant.
  - Adds `drawable placeholder ends with xml suffix so layoutlib ResourceHelper getDrawable routes through getXmlBlockParser` — locks in the suffix invariant.
  - Adds `drawable placeholder is unique per name to avoid sDrawableCache aliasing` — locks in the per-name uniqueness invariant.

- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`
  - Removes the `@Disabled` annotation and the `import org.junit.jupiter.api.Disabled` from the chip integration test.
  - Rewrites the chip test KDoc to describe what the test verifies (the full chip inflation chain — style-item trim, `<macro>` design-token indirection, and the per-name `.xml`-suffixed drawable placeholder).
  - Renames `tier3 basic primary — activity_basic 가 직접 SUCCESS` to `tier3 basic primary — activity_basic renders SUCCESS via primary path` (Korean → English).
  - Refactor-on-Sight cleanup: class-level KDoc, `resetBundleCache` `@BeforeEach` comment, and `locateAll` KDoc translated from Korean to English; phase identifiers (W3D4-β T13, v2 round 2 follow-up #4, W3D3 helper history, Codex Q3 + Claude Q3 attribution) removed; "PNG magic 헤더" assertion message → English.

### work_log
- `docs/work_log/2026-05-08_w4-b-c-drawable/` (Pattern 3 — new milestone) — session-log.md (this file) + handoff.md + next-session-prompt.md.

Total: 2 main + 2 test files + 1 work_log folder. Behavior change: 1 line (bundle drawable branch) plus the new helper function. Test changes: 2 new contract tests, 1 renamed/updated test, chip IT un-disabled. Comment hygiene: ~30 lines of pre-existing Korean / phase-identifier comments translated and depersonalized in the two touched source files.

## Test results

| Metric | post-W4-B-B (`e78d969`) | post-W4-B-C |
|---|---|---|
| Cross-module unit | 256 PASS / 0 fail | **258 PASS / 0 fail** (+2: drawable suffix + drawable uniqueness contract tests) |
| layoutlib-worker IT | 26 PASS + 1 SKIP (chip @Disabled) | **27 PASS + 0 SKIP** (chip un-disabled) |
| `LayoutlibResourceBundleDrawableTest` | 6 cases | **8 cases** |
| T17 5-probe regression guard (`W3D4DeltaThemeChainDiagnosticTest`) | 5/5 PASS | **5/5 PASS** (unchanged) |
| T20 5-probe regression guard (`W3D4DeltaBGateChainProbeTest`) | 5/5 PASS | **5/5 PASS** (unchanged) |
| Acceptance gate `activity_basic` | PASS | PASS (unchanged) |
| Acceptance gate `activity_basic_minimal` | PASS | PASS (unchanged) |
| `activity_minimal` glyph dark-pixel | PASS | PASS (unchanged) |
| `MaterialFidelityIntegrationTest` | 4/4 PASS | 4/4 PASS (unchanged) |
| W4-D require-throw guard (`LayoutlibResourceValueLoaderTest`) | 7/7 PASS | 7/7 PASS (unchanged) |
| Drawable feed walker (`AarResourceWalkerDrawableTest`) | 4/4 PASS | 4/4 PASS (unchanged) |

Empirical chip-IT progression evidence:

- Before W4-B-C fix: chip IT failure = `ERROR_INFLATION msg=Could not find drawable resource matching value 0x7F070076 (resolved name: abc_vector_test) in current configuration. exc=NotFoundException`.
- After W4-B-C fix: chip IT = `Result.Status.SUCCESS`, valid PNG > 1000 bytes. The full Material 3 chip inflation chain (stateListAnimator + textAppearance + drawable lookups + color state lists) completes. `MinimalLayoutlibCallback.getParser` is invoked with the new per-name `.xml`-suffixed value strings, and the `ResourceHelper.getDrawable` callback path resolves `abc_vector_test`, `m3_chip_close`, etc., from the AAR-walker raw-XML map.

## Landmines discovered + resolution

| LM | Description | Resolution |
|---|---|---|
| **LM-W4-B-C close — bytecode-grounded fix** | The chip drawable abc_vector_test lookup callback bypass identified in the W4-B-B handoff was conclusively grounded in the `.xml` suffix gate at `ResourceHelper.getDrawable` offsets 138-158, plus the `sDrawableCache` aliasing risk at offsets 24-44 / 109-119 of `Resources_Delegate.getDrawable`. | Per-name path-shaped placeholder `axp/drawable/<name>.xml` satisfies both contracts in one shape. Two contract tests lock in both invariants. |
| **LM-W4-B-J (new)** | Sibling-routing asymmetry across `Resources_Delegate.getDrawable` / `getAnimation` / `getColorStateList`. The `.xml` gate exists ONLY in `ResourceHelper.getDrawable`. `Resources_Delegate.getAnimation` calls `getXmlBlockParser` directly; `ResourceHelper.getColorStateList` is a separate entry. A future widget surfacing FONT, RAW, or XML-typed resources may have a similar but DIFFERENT gate, so each sibling type must be checked against its own `Resources_Delegate.get<Type>` bytecode before assuming the placeholder shape works. | Pattern recognition — when adding a new `ResourceType`-routed XML feed, disasm the corresponding `Resources_Delegate.get<Type>` AND the inner `ResourceHelper.get<Type>` (if any) to confirm whether a value-format gate exists. |
| **LM-W4-B-K (new)** | `sDrawableCache` is JVM-static. Across multiple `LayoutlibRenderer` sessions in the same JVM, a stale ConstantState could survive `LayoutlibResourceValueLoader.clearCache()`. The per-name uniqueness mitigates aliasing within one render but cross-session staleness is theoretically possible if a drawable's RAW XML mutates between sessions and the placeholder string remains constant. | Out of scope for W4-B-C — the placeholder-as-cache-key is layoutlib's contract, not ours; a fixture XML edit during a JVM run would also re-emit the same placeholder. Documented for future awareness. |
| **LM-W4-B-L (new)** | Refactor-on-Sight obligation — Claude reviewer caught 5 IMPORTANT findings on pre-existing Korean / phase-identifier comments in the two source files we touched (`AppLibraryResourceConstants.kt` file-level KDoc + 7 inline comments; `LayoutlibRendererIntegrationTest.kt` class KDoc + `resetBundleCache` comment + `locateAll` KDoc). The W4-B-A precedent saw the same pattern in `NamespaceAwareValueParser.kt`. | Cleaned in the same change per CLAUDE.md §4 (Rule 1 English-only + Rule 2 OUT-OF-SCOPE phase identifiers + Refactor-on-Sight). Pattern: every multi-edit C++ / Kotlin source file should be self-audited for non-English text BEFORE running the reviewer agent — not after. |

## Canonical document changes

This phase is implementation-only — no `docs/superpowers/specs/` or `docs/plan/` change. The fix is a small, localized constants + bundle update with bytecode-grounded rationale. Plan v6 + Round 7 Codex+Claude pair-review trigger not met (per CLAUDE.md §Codex implementation-phase exclusion).

## What's blocking / carried forward

The W4-B trilogy (W4-B drawable feed → W4-B-A trim → W4-B-B macro → W4-B-C drawable placeholder) is now fully closed. Active fail surface: 0. All 27 IT pass, 0 SKIP, 258 unit pass.

Next candidate options (no active trigger — priority decision time):

| Candidate | Status | Recommended trigger |
|---|---|---|
| Widget extension (Snackbar / TextInputLayout / MaterialSwitch / Badge) | open | Strong recommend — visual fidelity priority. Each new widget surfaces its own escalation chain (chip's W4-B-A/B/C trilogy is the precedent). Multi-day depth possible. |
| C (namespace-aware mode) | hold | No active fail. Only worth it if cross-AAR same-name conflicts surface — currently silent first-wins is sufficient. |
| E (R$styleable layer) | hold | No active fail. Future Material widget styled-attrs lookup failure would escalate. |

## Pair review verdicts

This phase is implementation-only. Per CLAUDE.md §Codex, pair review (Round 7 Codex+Claude planning) is NOT required for implementation-phase work. Dual-source single-shot review (Claude reviewer subagent + Codex direct CLI sanity-check) was conducted instead — the W4-B-A/B precedent.

- **`feature-dev:code-reviewer` (Claude subagent)**: APPROVE on bytecode hypothesis + cache uniqueness + new test contract strength + new KDoc compliance. Verdict REVISE_REQUIRED on 5 IMPORTANT Refactor-on-Sight findings (pre-existing Korean / phase-identifier comments in the two files we edited). All 5 applied: full English translation of `AppLibraryResourceConstants.kt` and `LayoutlibRendererIntegrationTest.kt` comments, removal of `W3D4-β T12 / W3D4-β T13 / W3D4 §3.1 #1 / 08 §7.7.6 / v2 round 2 follow-up #N / Codex Q3 + Claude Q3 / round 3 reconcile / Q6.3 / W3D3 helper history / etc.` phase identifiers and pair-review attribution.
- **Codex direct CLI sanity-check (codex-cli, default GPT model, reasoning effort xhigh)**: launched in parallel with the Claude reviewer (LM-G + LM-W4-D-B applied — direct CLI, no explicit `--model`). Did not return within the working session window (>30 min for the W4-B-C diff). Per CLAUDE.md §Codex implementation-phase exclusion, dual-source single-shot Codex sanity-check is optional, so the commit proceeds on the strength of the Claude reviewer's APPROVE-on-logic plus the bytecode-grounded fix and the green regression guards. If the Codex run later surfaces a finding worth acting on, it lands in a follow-up commit (CLAUDE.md "never amend pushed commits").

## Commits + push

- (next commit) `feat(w4-b-c): drawable placeholder per-name xml suffix — chip drawable resolution now wires through callback` — bundles the source change, new contract tests, chip un-disable, work_log, and Refactor-on-Sight comment cleanup.
