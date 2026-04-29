# CLAUDE.md — Android XML Previewer

Project-local guidance for Claude Code. Global user instructions in `~/.claude/CLAUDE.md` still apply.

---

## Session work log discipline

After completing a unit of work (session, feature, fix, milestone, pair review, or anything future sessions will need to reconstruct), summarize the progress and persist it under `docs/work_log/`. Pick ONE of the three patterns below — whichever best fits the work. Don't bury substantial work in someone else's session; don't spin up a new folder for a one-line follow-up.

### Pattern 1 — Update an existing file in an existing folder

Use when the work continues a session already recorded (same-day follow-up, minor amendment, correction of a prior entry) and fits naturally into an existing `session-log.md` / `handoff.md` / sibling file. Edit in place; never overwrite historical entries — append or add a dated subsection.

### Pattern 2 — Create a new file in an existing folder

Use when the work is a distinct artifact within an existing session's scope (e.g., adding a `pair-review.md`, `postmortem.md`, or `rerun-notes.md` alongside the session's `session-log.md`). Match the existing naming style of that folder.

### Pattern 3 — Create a new folder + new file

Use when the work is a new session or milestone worth isolating. Follow the existing convention:

```
docs/work_log/YYYY-MM-DD_<slug>/
  session-log.md        # what changed, files, tests, landmines
  handoff.md            # (if next session needs a cold-start onramp)
  next-session-prompt.md # (if handoff is meaningful)
```

Use absolute dates (`2026-04-23`), never relative ("today", "yesterday"). Slug should be short and descriptive (`w2d7-rendersession`, not `work`).

---

## What to include

Every entry should let a cold-start future session continue without reconstructing context:

- **Outcome** — what changed and why (1-2 sentences, not a diff dump).
- **Files created/modified** — one line per file explaining its reason-to-exist.
- **Test results** after the change (counts, pass/fail/skip, key names).
- **Landmines discovered** and how they were resolved — these compound over sessions.
- **Canonical document changes** (e.g., `docs/plan/...` edits) with section numbers.
- **What's blocking / carried forward** to the next session, if anything.
- **Pair review verdicts** (if applicable), following the pairing policy in `~/.claude/CLAUDE.md`.

## When NOT to create a work_log entry

Skip work_log updates for trivial reads, exploratory questions, one-line fixes, or status checks — unless the user explicitly asks. Work_log is for work that future sessions need to understand in order to continue without context loss. Noise pollutes signal.

## Picking the lightest pattern

Prefer Pattern 1 over Pattern 2, and Pattern 2 over Pattern 3, when the work genuinely fits. Creating a new folder signals "this is a distinct session/milestone worth indexing later"; use it only when that framing is accurate.
