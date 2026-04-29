# W3D3 L3 ClassLoader Implementation Plan — Round 2 Pair Review (Claude side)

**Reviewer**: Claude Opus 4.7 (1M context). Plan: `docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md`. Spec: `docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md`. Reviewed without seeing the Codex half.

---

## A. Plan-level blockers (NO_GO conditions)

### A1. Task 9 Gradle DSL — `tasks.named("assembleDebug")` at top-level config time fails in AGP 8.x. (HARD BLOCKER)

**Where**: Task 9 Step 1 + spec §4.1, both lines:

```
tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
```

**Failure mode** (verified empirically): I appended exactly the spec/plan §4.1 snippet to `fixture/sample-app/app/build.gradle.kts` and ran `./gradlew :app:tasks --all`:

```
* Where: Build file '/.../fixture/sample-app/app/build.gradle.kts' line: 63
* What went wrong: Task with name 'assembleDebug' not found in project ':app'.
```

AGP 8.x creates `assembleDebug` via the variant API (lazy task registration) which only resolves after the Android plugin has finalized variants. At the moment Gradle evaluates the top-level Kotlin DSL `tasks.named("assembleDebug")` call, AGP has not yet registered that task → `UnknownTaskException`.

I then re-ran the same code wrapped in `afterEvaluate { ... }`:

```
./gradlew :app:tasks --all 2>&1 | grep axpEmitClasspath
axpEmitClasspath
```

Works. So **the Q1 round-1 convergence ("Q1: `afterEvaluate` 제거")** that landed in spec §0 + spec §4.1 + plan Task 9 is **empirically wrong** for this AGP version. The Codex+Claude round-1 reasoning ("`tasks.named` is already lazy, no `afterEvaluate` needed") is correct *for tasks the calling plugin itself registers*, but `assembleDebug` is registered by the Android plugin via its variant API, and `tasks.named` only resolves an already-registered task — it does not wait for the Android plugin to register one.

**Fix** (apply to both the spec §4.1 and plan Task 9 Step 1):

```kotlin
afterEvaluate {
    tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
}
```

Or, the canonical AGP 8.x pattern:

```kotlin
androidComponents {
    onVariants(selector().withName("debug")) {
        afterEvaluate {
            tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
        }
    }
}
```

The first form is sufficient and minimal. Either way, `afterEvaluate` (or the equivalent variant-API hook) is mandatory and the spec round-2 convergence note must be reverted.

### A2. `SharedRendererBinding`/`SharedLayoutlibRenderer` `Pair<Path,Path>` does not extend to 3 paths. (BLOCKER unless Task 8 explicitly addresses)

**Where**:
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:32` — `boundArgs: Pair<Path, Path>`.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt:13` — `verify(bound: Pair<Path, Path>?, requested: Pair<Path, Path>)`.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt` — 3 tests against `Pair`.

**Failure mode**: Plan Task 8 Step 3 says "W3D2 의 시그니처 그대로 확장. binding 은 `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 호출." But the binding is currently keyed on `Pair<Path,Path>` — adding a third path means either changing to `Triple<Path,Path,Path>` (ugly) or introducing a small data class `RendererArgs(distDir, fixtureRoot, sampleAppModuleRoot)`. Plan does not specify which, and `SharedRendererBindingTest` (3 tests) will need to change accordingly.

**Fix**: Plan Task 8 must add an explicit step: introduce `data class RendererArgs(val distDir: Path, val fixtureRoot: Path, val sampleAppModuleRoot: Path)`, change `boundArgs` and `verify` to take it, and update `SharedRendererBindingTest` (the 3 existing test cases) accordingly. The 3 existing tests in `SharedLayoutlibRendererIntegrationTest.kt` (1 of which constructs a `differentFixture` to exercise mismatch) also need updating — currently they pass `Pair`-equivalent positional args to `getOrCreate`. The plan Task 8 Step 4 grep covers `SharedLayoutlibRenderer.getOrCreate`, but does not flag the binding-key type change as a deliverable.

### A3. Task 5 `locateInternalGeneric` refactor — plan code does NOT preserve the existing 3-arg `locateInternal` signature that 3 existing tests bind to.

**Where**:
- Plan Task 5 Step 2 shows only `locateInternalGeneric` (4-arg) and `locateInternalModuleRoot` (3-arg).
- `FixtureDiscoveryTest.kt:42, :55, :69` all call `FixtureDiscovery.locateInternal(override, userDir, candidateRoots)` — the existing 3-arg fixture-subpath form.

The plan footnote says "기존 `locateInternal` 은 `locateInternalGeneric(... , FIXTURE_SUBPATH)` 로 위임 (동작 동일)" but that bridging method is not in the code block. A naive subagent reading only the code block will delete `locateInternal`, breaking 3 of the existing 5 tests at compile time.

**Severity**: blocker for "existing tests still pass after refactor" but easily fixed if the plan code block is amended.

**Fix**: Include the bridging definition explicitly in Task 5 Step 2:

```kotlin
internal fun locateInternal(
    override: Path?,
    userDir: String,
    candidateRoots: List<String>,
): Path? = locateInternalGeneric(override, userDir, candidateRoots, FIXTURE_SUBPATH)
```

---

## B. Strongly recommended (GO_WITH_FOLLOWUPS)

### B1. Task 8 Step 1 — `--sample-app-root` CLI flag also belongs in `CliConstants.USAGE_LINE` and must be added to `VALUE_BEARING_FLAGS` (plan says "Cli 추가" but mentions only the set).

`server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt:13-14` defines `USAGE_LINE`. Adding the flag to `VALUE_BEARING_FLAGS` without updating `USAGE_LINE` leaves the user-facing usage string stale. Already covered by W3D2 LM-B2 prior art. Add an explicit Step 1 sub-bullet: "USAGE_LINE 에 `[--sample-app-root=<path>]` 추가".

### B2. `assumeTrue(found != null, ...)` for `sampleAppModuleRoot` in the integration test (plan §4.8 / spec §4.8).

The plan inherits the existing `assumeTrue` pattern from `LayoutlibRendererTier3MinimalTest`. Once the W3D3 acceptance gate is "T1 gate (SUCCESS) PASS", a missing `sample-app` module root means the test silently SKIPS instead of FAILing — which masks regression. The current Tier3 test is already wrong on this count for `dist`/`fixture`, but at least there it pre-dates the gate. For `sampleAppModuleRoot`, since the manifest is the *whole point* of W3D3, missing it should be a hard FAIL (`require` with the `./gradlew :app:assembleDebug` hint), not `assumeTrue`. Otherwise CI may go green silently when the fixture build was never run.

**Recommend**: change to `requireNotNull(FixtureDiscovery.locateModuleRoot(null)) { "sample-app module root 없음 — fixture/sample-app 확인 + ./gradlew :app:assembleDebug" }` for the W3D3 integration test. Leave `dist`/`fixture` `assumeTrue` as-is (pre-existing convention).

### B3. Plan claims "118 → 130+, +15 신규" — math is off.

Counted from plan Tasks 1-6:

| Task | New tests |
|---|---|
| T1 (ClassLoaderConstants) | 3 |
| T2 (ClasspathManifest) | 5 |
| T3 (AarExtractor) | 4 |
| T4 (SampleAppClassLoader) | 3 |
| T5 (FixtureDiscovery extension) | 2 |
| T6 (MinimalLayoutlibCallback `loadView`/`findClass`/`hasAndroidXAppCompat`) | 4 |
| **Total** | **21** |

Plus modifications to existing `MinimalLayoutlibCallbackTest` (8 tests, all updated to lambda CL provider — count unchanged). 118 + 21 = **139**, not 130. Plan's "+15" is significantly under-counted. Update plan numerics for accountability.

### B4. Integration test count "11 → 12 PASS" — also off.

Current state (verified):
- `LayoutlibRendererIntegrationTest` (1 test, `@Disabled`)
- `BridgeInitIntegrationTest` (4 tests, all PASS)
- `LayoutlibRendererTier3MinimalTest` (3 tests, 1 `@Disabled` for tier3-glyph) → 2 PASS
- `SharedLayoutlibRendererIntegrationTest` (4 tests, all PASS)

Total 12 tests; 10 PASS + 2 SKIP. After plan Task 10 enables LayoutlibRendererIntegrationTest → 11 PASS + 1 SKIP. Plan says "11 → 12 PASS" which would require 11 PASS pre-W3D3 (off by 1). The W3D2 session-log §3 baseline of "11" was already off and should be corrected as part of plan Task 11 work_log.

### B5. Empty-manifest `error("...")` → `IllegalStateException`. Plan Task 4 Step 1 test 2 ("R_jar 누락") asserts `IllegalStateException`. Match — but the test comment ("둘 중 하나면 된다 — IllegalStateException 또는 IllegalArgumentException") contradicts the actual `assertThrows<IllegalStateException>` — `IllegalArgumentException` would FAIL the test. Either widen to `assertThrows<RuntimeException>` or correct the comment.

`error("...")` calls `kotlin.error(message)` which always throws `IllegalStateException(message)`. Confirmed by `kotlin.PreconditionsKt.error`. So the empty-manifest path firing `error()` first → `IllegalStateException`, and the `R.jar` `require()` would fire `IllegalArgumentException` only if the manifest had any non-empty content. As written (empty manifest), only `IllegalStateException` can fire. Comment is misleading; either remove it or rewrite the test to inject a non-empty (but valid) manifest and force the R.jar require path.

### B6. Task 6 plan code drops the existing comment block (W2D7 KDoc) on `MinimalLayoutlibCallback`.

The new code block at plan lines 681-766 fully replaces the class. The original W2D7 KDoc (lines 13-23 of the existing file) describes the resource id contract and per-method intent. CLAUDE.md "Never delete existing comments" applies. The plan should explicitly call out: "preserve the existing class-level KDoc, modifying only the parts about `loadView` UnsupportedOperationException → reflection-instantiate". The plan-pasted code shows new KDoc but doesn't note preservation, so a literal subagent will overwrite.

---

## C. Specific concern resolutions

### C1. Task 7+8 atomicity — splitting "add new constructor param" (Task 7, no commit, build broken) from "fix all callers + commit" (Task 8) — **acceptable**.

The W3D2 LM-B1 prior art at `docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:48-50` already established the pattern: when a constructor param removal/addition forces a build break, the same task unit must include all caller updates before the commit. Plan Task 7 explicitly says "**여기서 commit 하지 않고 Task 8 까지 묶어서 처리 — 컴파일이 깨진 상태는 task unit 미완으로 간주.**" (line 815). Task 8 Step 7 commits both. This matches the LM-B1 lesson.

**However**: subagent-driven-development executes tasks one at a time with a verification gate between them. The current plan structure means Task 7 executes, fails the verification gate (compileKotlin breaks), and the task is marked unfinished — Task 8 then resumes. Two cleaner alternatives:

1. **Merge Task 7 + Task 8 into one task** with two phases (modify renderer; fix all callers; commit). Strongly recommended — the W3D2 LM-B1 lesson suggests this exact split was the bug, not the fix.
2. **Keep split** but explicitly mark Task 7 as "no verification expected; do NOT commit; mark Task 7 done only when Task 8 also passes". Plan currently does this ("commit 하지 않고") but also says `compileKotlin` "Expected: FAIL" as the Step 3 expected output, which the subagent-driven runner will interpret as a successful step. Confusing.

**Recommend**: merge into a single Task. The atomicity discipline justifies a 50-line task; splitting just to cap task LOC has a worse cost.

### C2. Task 5 `locateInternalGeneric` refactor — existing 5 tests remain passing.

Verified above (A3). The 5 existing tests bind to `FixtureDiscovery.locateInternal(override, userDir, candidateRoots)` (3-arg). If the plan adds the 1-line bridging wrapper (delegating to `locateInternalGeneric(..., FIXTURE_SUBPATH)`) per the footnote, all 5 existing tests pass without modification. **Mechanical**, but the plan code block must include the wrapper definition.

### C3. Task 6 `loadView` test using `StringBuilder(CharSequence)` constructor — verified accessible via `getDeclaredConstructor(CharSequence::class.java)`.

Empirically verified:

```
$ javac /tmp/check_sb.java && java check_sb
OK: public java.lang.StringBuilder(java.lang.CharSequence)
Built: hi
```

`StringBuilder` exposes a public `(CharSequence)` constructor since Java 1.5. `Class.getDeclaredConstructor(CharSequence::class.java)` resolves it. Test works as written.

Caveat: The Task 6 test imports `cb.loadView("java.lang.StringBuilder", arrayOf(CharSequence::class.java), arrayOf<Any>("hi"))` — the second arg is an `Array<out Class<*>>` (vararg-friendly) and third is `Array<out Any>`. The plan signature for `loadView` matches `LayoutlibCallback.loadView(name, sig, args)`. Compilation OK.

### C4. Task 9 Gradle wiring — see A1. **NO_GO until afterEvaluate is added.**

### C5. Task 10 branch (B)/(C) — sufficient with caveat.

(A) PASS path — clean, advances. ✓
(B) FAIL with Material*-related stack trace — falls through to spec §9.1 (`activity_basic_minimal.xml` with standard `Button` instead of `MaterialButton`). Spec §9.1 markup looks valid. ✓
(C) "FAIL with 다른 원인" — plan says "stack trace 분석 → 본 플랜의 누락된 컴포넌트 식별 → 그 컴포넌트의 task 재진입 / 신규 task 추가." This is too open-ended. A subagent following subagent-driven-development discipline cannot "add a new task" — it executes the existing plan. Branch (C) effectively means "stop and escalate to human", which should be stated explicitly: "branch (C) → `STOP — escalate to human pair, do not improvise. Capture stack trace + first 50 lines into work_log/2026-04-29_w3d3-l3-classloader/branch-c-evidence.md and end the session.`" Otherwise an over-eager subagent might commit a half-baked fix.

**Recommend**: tighten branch (C) wording to a hard stop + evidence capture.

### C6. Test cardinality math — see B3. Plan claims "+15", actual = 21.

### C7. `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` — Task 8 caller enumeration.

Verified all `LayoutlibRenderer(` callers via `grep -rn "LayoutlibRenderer(" server/ --include="*.kt"`:

1. `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:130` — production CLI. Plan Task 8 Step 2 covers.
2. `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:44` — test singleton. Plan Task 8 Step 3 covers.
3. `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:44` — class declaration itself.

Plan Task 8 covers all production callers. Indirectly via `SharedLayoutlibRenderer`, all 3 integration test files (`LayoutlibRendererTier3MinimalTest`, `LayoutlibRendererIntegrationTest`, `SharedLayoutlibRendererIntegrationTest`) reach `LayoutlibRenderer`. Plan Step 4 grep covers `SharedLayoutlibRenderer.getOrCreate`. The plan must also cover **`SharedLayoutlibRendererIntegrationTest` (4 call sites)** — currently Step 5 only mentions `LayoutlibRendererTier3MinimalTest`. Add explicit step for `SharedLayoutlibRendererIntegrationTest` as well.

---

## D. Independent issues

### D1. Magic strings in plan code blocks — only one slipped, in Task 9 Step 1. Plan §4.1 / Task 9 hardcodes `"axpEmitClasspath"` and `"assembleDebug"` (the latter is a Gradle string convention; the former is the new task name).

**Verdict**: acceptable. Gradle DSL conventions allow string task names, and adding constants for them in `ClassLoaderConstants` would not improve safety. CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" is intent-scoped and these are de-facto Gradle DSL identifiers.

### D2. Empty-manifest exception type match — see B5. As written, plan tests fire `IllegalStateException` correctly; comment is misleading but tests pass.

### D3. `assumeTrue` for missing fixture build vs. `require` — see B2. Required as a fix.

### D4. `LayoutlibRendererTier3MinimalTest`/`SharedRendererBinding` impact — covered in A2 + B4 + C7.

### D5. Spec round-1 `0.3 ChildFirst 정책 검토` argument is sound, but `application id` shadowing (`com.fixture` vs `axp.render`) — minor.

`MinimalLayoutlibCallback.applicationId = "axp.render"` (line 71 of existing code, unchanged in plan). sample-app's actual `applicationId = "com.fixture"` (build.gradle.kts:11). These differ; layoutlib uses the callback's applicationId for `Bridge.mProjectKey` lookup. If layoutlib resolves resources by appId match, the wrong appId could mask R.jar resolution. Probably safe (R.jar is loaded by classloader URL, not by appId) — but worth a one-line note in the plan that this is intentionally divergent from sample-app's app id and a worth-watching landmine for branch (C).

### D6. CLAUDE.md "no default parameter values" compliance — plan Task 7 declares `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` with no defaults. ✓.

### D7. `Files.move` ATOMIC_MOVE in `AarExtractor` — works on POSIX same-filesystem. Test fixtures use `@TempDir` which is on the same filesystem as the cache (cache is also under TempDir). ✓.

### D8. Companion sharedRenderer in `LayoutlibRendererTier3MinimalTest` captures dist + fixture. After plan, the companion must also capture sampleAppModuleRoot. Plan Task 8 Step 5 covers explicitly. ✓.

### D9. The plan's `setupModuleRoot` test helper writes a copy of a "tinyJar" into the AGP R.jar location. That's fine for the URL-existence check but if the test ever asserts "R.jar contents are real R class files", it will fail. Plan tests don't make that assertion, so OK. ✓.

---

## E. Verdict

**GO_WITH_FOLLOWUPS** — with the following hard-required fixes before implementation kickoff:

1. **A1 (HARD BLOCKER)**: revert the round-1 Q1 convergence on `afterEvaluate 제거`. Re-introduce `afterEvaluate { ... }` around `tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }` in BOTH spec §4.1 AND plan Task 9 Step 1. Empirically verified that without it `./gradlew :app:tasks` fails with `UnknownTaskException: assembleDebug`. This is a NO_GO if uncorrected — the entire W3D3 deliverable depends on the manifest being emitted, and the emit task wiring fails at config time without `afterEvaluate`.

2. **A2 (HARD BLOCKER)**: introduce `data class RendererArgs` (or equivalent) for `SharedRendererBinding`/`SharedLayoutlibRenderer`. The current `Pair<Path,Path>` does not extend to 3 paths, and `SharedRendererBindingTest` (3 tests) needs corresponding update. Plan Task 8 must add an explicit step for this.

3. **A3 (BLOCKER)**: Task 5 plan code must show the 1-line bridging wrapper for `locateInternal` (3-arg) → `locateInternalGeneric(..., FIXTURE_SUBPATH)`. Currently only mentioned in a footnote.

4. **B-class fixes**: USAGE_LINE update (B1), `requireNotNull` for sampleAppModuleRoot in T1 gate (B2), test cardinality numbers (B3, B4), test comment cleanup (B5), preservation of existing KDoc (B6).

5. **C5 fix**: tighten branch (C) wording to a hard stop + evidence capture, not "신규 task 추가."

6. **C1 reconsider**: prefer merging Task 7 + Task 8 into one atomic task instead of split; reduces verification gate confusion.

If A1 is not fixed, `axpEmitClasspath` will never run, the manifest will never be written, and every downstream task (T2 read, T4 build classloader, T6 loadView via lazy provider, T10 integration test) will hit the require("manifest 누락") path. The blocker is small to fix (one keyword: `afterEvaluate`) but uncorrectable without re-opening spec round 2 → re-running the round-2 pair gate.

**Net rationale**: the architecture, decomposition, contingency plan, and overall TDD shape of the plan are sound, with the round-1 spec-pair fixes well integrated. The only HARD blocker is the empirically wrong AGP DSL pattern, which round-1 reasoned about but did not test against the actual Android plugin's variant lifecycle. A2/A3 are bookkeeping omissions but would each surface as a compile failure in implementation. Once A1/A2/A3 land, this is a clean GO.

---

## Empirical evidence captured

1. AGP 8.x `tasks.named("assembleDebug")` at top-level fails:
   ```
   * Where: Build file '.../app/build.gradle.kts' line: 63
   * What went wrong: Task with name 'assembleDebug' not found in project ':app'.
   ```
   Wrapping in `afterEvaluate { ... }` resolves: `./gradlew :app:tasks --all | grep axpEmitClasspath` → `axpEmitClasspath`.

2. `StringBuilder(CharSequence)` constructor reflection: `getDeclaredConstructor(CharSequence::class.java)` returns it; `newInstance("hi")` works.

3. Current test counts: 118 unit tests (`./server/gradlew -p server test` BUILD SUCCESSFUL), 12 integration tests of which 2 are `@Disabled` (`LayoutlibRendererIntegrationTest` + `tier3-glyph`).

4. Caller enumeration of `LayoutlibRenderer(`: 1 production (`Main.kt:130`), 1 test singleton (`SharedLayoutlibRenderer.kt:44`), 1 self (`LayoutlibRenderer.kt:44`).

5. `MinimalLayoutlibCallback(` callers: `MinimalLayoutlibCallbackTest` (8 sites), `SessionParamsFactoryTest` (2 sites), `LayoutlibRenderer.kt:171` (1 site). Plan Task 6 Step 2 says "기존 MinimalLayoutlibCallbackTest" only — `SessionParamsFactoryTest` (2 sites at lines 42 + 65) is also a caller and must be updated. **Add B7 follow-up**: Plan Task 6 Step 2 must also update `SessionParamsFactoryTest.kt` 2 call sites.
