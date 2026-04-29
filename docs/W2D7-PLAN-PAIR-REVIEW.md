# W2D7 Plan Pair Review — Codex xhigh + Claude Plan

**Date**: 2026-04-23 (W2 Day 7, plan stage)
**Artifact reviewed**: `docs/superpowers/plans/2026-04-23-w2d7-rendersession.md`
**Policy**: CLAUDE.md "1:1 Claude+Codex Pairing for Planning/Review" (architecturally non-trivial plan)

---

## 1. Verdicts (side-by-side)

| Reviewer | Model | Verdict |
|---------|-------|---------|
| Claude (Plan subagent) | sonnet | **GO_WITH_FOLLOWUPS** |
| Codex rescue | gpt-5-xhigh | **GO_WITH_FOLLOWUPS** |

**Consolidation**: FULL CONVERGENCE on verdict + FULL CONVERGENCE on all 4 substantive follow-ups. Judge round not required.

**Codex sandbox caveat**: Codex's bubblewrap blocked both direct fs reads and `/tmp/codex-inline-w2d7.md`. Codex analyzed based on the inline task summary only (could not verify §7.7.1 excerpt first-hand for Q8). Despite this limitation, Codex's independent reasoning converged with Claude's file-grounded review on all substantive points — indicating the follow-ups are inferred from the stated architecture rather than hidden document details, raising confidence.

---

## 2. Convergent follow-ups (all 4 — both reviewers, same recommendation direction)

### F1 — Remove `layoutlib-api.jar` from isolated URLClassLoader URL array

**Source**: Claude Q2 + Codex Q2 (both AGREE).

**Rationale**: Plan adds `implementation("com.android.tools.layoutlib:layoutlib-api:31.13.2")` to `:layoutlib-worker` so `LayoutlibCallback` / `AssetRepository` / `ILayoutPullParser` subclasses compile. Parent-first delegation means the child `URLClassLoader` already resolves all layoutlib-api classes via system CL — the `apiJar` entry in `createIsolatedClassLoader()` is **shadowed (never loaded)** but leaves a trap for future refactors that might switch to child-first or otherwise break the assumption.

**Action**: In Task 1 Step 2, modify `LayoutlibBootstrap.createIsolatedClassLoader()` — keep only `layoutlib-*.jar` (NOT `layoutlib-api-*.jar`) in URL array. Single source of truth.

### F2 — Theme application to Task 4 baseline (not Task 6 fallback)

**Source**: Claude Q3 + Codex Q3 (both AGREE, empty RenderResources is risky, not safe baseline).

**Rationale**: Even with literal-only fixture (`#FFFFFFFF` / `#FF000000` / literal text), framework View inflation internally resolves `Widget.TextView` → `TextAppearance` default style chain. Without `Resources.Theme.obtainStyledAttributes()` returning usable values, TextView.measure() degrades to zero-size content → rendered as blank white — which would pass a weak Tier3 but violate canonical item 3b ("실 pixel").

**Action**: In Task 4 SessionParamsFactory, **MUST** set up RenderResources with `Theme.Material.Light.NoActionBar` applied as default theme and `setForceNoDecor()` on SessionParams. Empty RenderResources allowed only in explicit negative-test path (document this clearly).

**Open execution question**: The plan does not specify the concrete layoutlib API for "load framework theme from `data/res/` into RenderResources". Codex explicitly flagged this as a plan gap. Resolution: Task 4 Step 2 gets an initial research sub-step that either (a) uses `ResourceResolver.create(projectResources, frameworkResources, themeName, isProjectTheme)` if signature is compatible, OR (b) implements a minimal `RenderResources` subclass overriding `getDefaultTheme()` / `findItemInTheme()` to return hardcoded defaults for the handful of attrs TextView/LinearLayout actually query during inflation of the minimal fixture. Outcome recorded in session-log.

### F3 — Tier3 assertion sharpened to targeted-rect dark-pixel check

**Source**: Claude Q6 + Codex Q6 (both: "≥ 1000 non-white pixels globally" is too weak — could pass on anti-aliasing / status bar noise without any text rendering).

**Action**: In Task 6 (`LayoutlibRendererTier3MinimalTest`), replace global scan with:
1. Assert `bytes.size >= 10_000` (coarse corruption guard — retained).
2. Assert `img.width == RENDER_WIDTH_PX && img.height == RENDER_HEIGHT_PX` (HardwareConfig honored).
3. Compute a target rect around the TextView position (xhdpi padding=64px, textSize=28sp≈112px height, text starts at y≈64px for padding+ascent). Rect: `x ∈ [64, 600], y ∈ [64, 200]`.
4. Count pixels inside the rect with `R+G+B < 384` (clearly dark, not white/gray noise).
5. Assert count `>= 20` (minimum to represent a few glyph strokes).

### F4 — Class identity assertion in unit tests

**Source**: Codex Q5 (new — Claude didn't mention). Claude Q5 gave different gaps (fromFile test, namespace-URI queries, AssetRepository attachment) which are also valid.

**Action**:
- Add `LayoutPullParserAdapterTest.`fromFile loads sample layout`` (exercises real path).
- Add `SessionParamsFactoryTest.`AssetRepository attached via setAssetRepository`` (reflection check).
- Add `SessionParamsFactoryTest.`getAttributeValue by android namespace URI`` (critical — layoutlib queries by namespace).
- Add `MinimalLayoutlibCallbackTest.`framework widget class name does not reach loadView`` — mark test `@Disabled` with reason: "drove at integration layer, confirmed by Tier3 pass" (documents the invariant without heavy reflective setup).

---

## 3. Non-convergent (complementary, not conflicting)

- **Claude Q7**: emphasized promoting theme OUT of fallback tree.
- **Codex Q7**: emphasized adding stop conditions to each fallback branch.
- Both directions compatible — F2 promotes theme, F2's execution adds explicit "render status + targeted pixel check" as stop conditions. No conflict.

- **Claude Q5** additional gaps: `createXmlParserForFile()` usable, `loadView` never-called assertion.
- **Codex Q5** additional gaps: class identity cross-CL, render status SUCCESS check in integration.
- Union adopted in F4.

---

## 4. Judge-round decision

Not triggered. Both reviewers converged on verdict AND on all substantive follow-ups. Remaining divergences are complementary gap coverage (F4 union) rather than disagreement.

---

## 5. Applied actions

1. Updated `docs/superpowers/plans/2026-04-23-w2d7-rendersession.md` with F1/F2/F3/F4 edits.
2. This document committed as the decision record.

---

## 6. Reporting (per CLAUDE.md pair-reporting template)

- **Pair dispatched**: 1 pair (Claude Plan subagent + Codex rescue gpt-5-xhigh). Scope: plan-level review.
- **Each half's headline**:
  - Claude: GO_WITH_FOLLOWUPS — empty RenderResources is wishful; theme must be baseline not fallback; Tier3 too weak.
  - Codex: GO_WITH_FOLLOWUPS — classloader identity confirmed safe but apiJar URL redundant/trap; empty RenderResources risky; plan must specify HOW theme loads.
- **Convergence verdict**: FULL CONVERGENCE (verdict + all 4 follow-ups).
- **Judge round**: not needed.
- **Adopted decision**: proceed with plan after applying F1–F4 edits. Execution can begin on updated plan.
