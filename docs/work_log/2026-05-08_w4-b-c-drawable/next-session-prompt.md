# Next-session prompt — W4 next candidate scope decision (post-W4-B trilogy close)

Paste-ready prompt for the next session. The W4-B chip integration trilogy (drawable feed → trim → macro → drawable placeholder) is fully closed. No active fail surface; priority decision required.

---

W4 next candidate scope decision after W4-B-C close — drawable placeholder per-name `.xml` suffix fix landed.

Just-shipped commit (most recent) `feat(w4-b-c): drawable placeholder per-name xml suffix — chip drawable resolution now wires through callback`. Closes LM-W4-B-H. Bytecode-grounded fix for the `Resources_Delegate.getDrawable` callback bypass: `ResourceHelper.getDrawable` (`/tmp/w4bc/ResourceHelper-disasm.txt` offsets 132-158) routes through `getXmlBlockParser` → `LayoutlibCallback.getParser` only when `value.toLowerCase().endsWith(".xml")` OR `resourceType == AAPT`. Replacing the static `DRAWABLE_PLACEHOLDER_VALUE = "@axp:drawable-xml"` with a per-name helper `drawablePlaceholderValue(name) = "axp/drawable/" + name + ".xml"` simultaneously satisfies the `.xml` gate and the JVM-static `sDrawableCache` per-key uniqueness requirement (offsets 24-44 / 109-119 of `Resources_Delegate.getDrawable`).

Current state (commit (most recent) above):

- 258 unit PASS / 0 fail
- 27 IT PASS / 0 SKIP (chip un-disabled)
- T17 + T20 regression guards 5/5 + 5/5
- Acceptance gates `activity_basic` + `activity_basic_minimal` + `activity_minimal` glyph dark-pixel PASS
- `MaterialFidelityIntegrationTest` 4/4 PASS
- `LayoutlibResourceBundleDrawableTest` 8 cases (suffix invariant + uniqueness invariant locked in)

W4 phase status:

- W3D4-δ-A~D: CLOSED ✓
- tier3-glyph: CLOSED ✓
- W4-D (runtime-classpath.txt hardening): CLOSED ✓
- W4-A (drawable XML feed mirror): CLOSED ✓
- W4-B-drawable: CLOSED ✓
- W4-B-A (chip stateListAnimator): CLOSED ✓ (`c126512`)
- W4-B-B (chip TextAppearance via macro): CLOSED ✓ (`e78d969`)
- W4-B-C (chip drawable abc_vector_test placeholder): CLOSED ✓ (most recent commit)
- W4-C (namespace-aware mode): hold (no trigger)
- W4-E (R$styleable layer): hold (no trigger)

Cold-read 6 docs (this order):

1. `docs/work_log/2026-05-08_w4-b-c-drawable/handoff.md` (this prompt's source)
2. `docs/work_log/2026-05-08_w4-b-c-drawable/session-log.md` (W4-B-C exact diff + bytecode evidence + dual-source review)
3. `docs/work_log/2026-05-08_w4-b-b-macro/handoff.md` (W4-B-B close + W4-B-C path δ1/δ2/δ3)
4. `docs/work_log/2026-05-08_w4-b-a-trim/handoff.md` (W4-B-A close + W4-B-B path α/β/γ rejection precedent)
5. `docs/work_log/2026-05-07_w4-candidate-b-chip/handoff.md` (W4-B drawable feed + chip fixture entry)
6. `docs/work_log/2026-05-07_w4-candidate-d-hardening/handoff-w4-d-close.md` (W4 candidate catalog)

Original planning docs (mandatory cross-read):

- `docs/plan/03-roadmap.md` (master roadmap, historical/superseded by `06 §6` + `08 §1`)
- `docs/plan/04-open-questions-and-risks.md` (risk register R1-R9)
- `docs/MILESTONES.md` (W1-W6 gate checkpoints)
- `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md` (W3D4 phase design — chain walker reference)

Next session entry:

0. Baseline verify
   - cd server && ./gradlew test --console=plain
     → BUILD SUCCESSFUL — 258 unit / 0 fail
   - ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain
     → 27 IT PASS + 0 SKIP

1. User priority decision — three open paths:

   ### path 1 — Widget extension (visual fidelity priority, recommended)

   Add new fixture XML for one Material widget per session, surface its own escalation chain. The chip W4-B trilogy (W4-B-A trim + W4-B-B macro + W4-B-C placeholder) is the precedent for the multi-round depth pattern. Priority order of likely surface variety:

   - `activity_textinputlayout.xml` — TextInputLayout + TextInputEditText. Likely surfaces TextAppearance + drawable + animator state interactions similar to chip.
   - `activity_snackbar.xml` — Snackbar / BaseTransientBottomBar. Round 6 Codex Q4 census flagged `BaseTransientBottomBar.checkAppCompatTheme` as a separate path.
   - `activity_materialswitch.xml` — MaterialSwitch.
   - `activity_badge.xml` — BadgeDrawable. Standalone Drawable, distinct from view hierarchy.

   Each new widget = potential multi-round escalation. Plan v6 + Round 7 Codex+Claude planning pair-review available if a widget surfaces a deep design decision.

   ### path 2 — Namespace-aware mode (design integrity priority)

   `docs/superpowers/specs/2026-05-XX-w4-namespace-aware.md` plan v6 + Round 7 Codex+Claude planning pair-review (CLAUDE.md §Codex). RES_AUTO collapse → ns-aware split. RJarSymbolSeeder + AarResourceWalker + LayoutlibResourceBundle.byNs all touched. No active fail trigger; only worth pursuing if cross-AAR same-name conflicts surface as a real bug. LM-G + LM-W4-D-B apply (codex direct CLI, no `--model` flag, reasoning effort xhigh).

   ### path 3 — Hold (no active trigger)

   Both W4-C and W4-E remain valid hold. Worth pursuing only when a surface bug emerges.

2. After path selected:

   - **path 1 (widget extension)**: add new fixture XML + IT measurement → fail surface classification (LM-W3D4-β-H + δ-E entry-point comparison: stack-trace entry-point comparison identifies layer shift). Each fail → empirical probe (LM-W4-B-E: 60-min budget for bytecode disasm + runtime probe before plan-writing). Small fix → implementation-phase, dual-source single-shot review (Claude reviewer subagent + Codex direct CLI). Big design → plan v6 + Round 7 Codex+Claude planning pair-review (LM-G + LM-W4-D-B: codex direct CLI, no `--model` flag, reasoning effort xhigh).

   - **path 2 (namespace-aware)**: plan v6 design first. Round 7 Codex+Claude planning pair-review mandatory. Q6 open critique slot per pair-review template. After GO → implementation in 12-step transition. T17 + T20 regression guards must pass through every step.

3. Carry-forward LMs (handoff §4 — 30 LMs total, all internalized at session entry):

   - LM-W3D4-β-D~H, LM-W3D4-γ-A~C, LM-W3D4-δ-A~I, LM (CLAUDE.md Three Hard Rules), LM-G, LM-W4-D-A/B
   - LM-W4-B-A (close ✓ — `c126512`)
   - LM-W4-B-B (close ✓ — `e78d969`)
   - LM-W4-B-C (carry): subagent hypothesis is hypothesis — file:line read is ground truth
   - LM-W4-B-D (carry): Kotlin default-arg + trailing-lambda incompatibility
   - LM-W4-B-E (vindicated, carry): handoff-document hypothesis is hypothesis. 60-min empirical probe (bytecode disasm + runtime probe) before plan-writing. Triggered THREE times now: W4-B-A α/β/γ rejection, W4-B-B path α/β/γ rejection, W4-B-C bytecode-evidence-first investigation
   - LM-W4-B-F (carry): NamespaceAwareValueParser top-level `<item type=...>` trim not applied
   - LM-W4-B-G (carry): AAPT2 silent feature gap pattern — `<macro>` (Material 3) + future `<sample-data>` / `<overlayable>` / `<style-item>`
   - LM-W4-B-H (close ✓ — most recent commit): chip drawable abc_vector_test placeholder
   - LM-W4-B-I (carry): Material widget strict-consumer pattern — strict-consumer chain diagnosis budget for new widgets
   - **LM-W4-B-J (new)**: sibling-routing asymmetry — `Resources_Delegate.get<Type>` may have type-specific value-format gates. When adding new ResourceType-routed XML feeds, disasm both `Resources_Delegate.get<Type>` AND the inner `ResourceHelper.get<Type>` (if any) to confirm whether a value-format gate exists
   - **LM-W4-B-K (new)**: `sDrawableCache` is JVM-static; cross-render staleness theoretical. Per-name uniqueness mitigates aliasing within a render. Out-of-scope until a real cross-render fail surfaces
   - **LM-W4-B-L (new)**: Refactor-on-Sight obligation pattern — every multi-edit Kotlin source file must be self-audited for non-English text and phase identifiers BEFORE running the reviewer agent. Caught in W4-B-A (NamespaceAwareValueParser) AND W4-B-C (AppLibraryResourceConstants + LayoutlibRendererIntegrationTest). Standing pre-commit step

4. After candidate close → docs/work_log/ entry per CLAUDE.md task-unit completion + commit + push.

CLAUDE.md compliance (brace own-line, no default params, Zero Tolerance for Magic Numbers, comments English-only / function-structure-only per Three Hard Rules, Refactor-on-Sight per §4). LM-G + LM-W4-D-B applies for any planning phase (codex direct CLI, no `--model`, reasoning effort xhigh).

Use agent teams + skills as appropriate (CLAUDE.md §Agent Team Dispatch — subagent mode wins for single-turn parallel research/review; teammate mode for multi-turn supervised collaboration). mcp-builder, plugin-dev, mcp-server-dev skills are out-of-scope for this phase — avoid.
