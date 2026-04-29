# W3D3-α Plan v1 — Claude Plan Review (1:1 pair half)

**Reviewer**: Claude Opus 4.7 (1M context).
**Plan**: `docs/superpowers/plans/2026-04-29-w3d3-alpha-bytecode-rewriting.md` (v1, 2026-04-29).
**Spec**: `docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md` (round 2 settled).
**Round 1 reviews**: `docs/work_log/2026-04-29_w3d3-l3-classloader/alpha-spec-pair-review-round1-{claude,codex}.md`.
**Verdict**: **NO_GO** (1 hard blocker — α-5 Step 5 fallback discriminator never matches in the plan's exception flow).
**Date**: 2026-04-29.

---

## A. Blockers (NO_GO)

### A1. Task α-5 Step 5 — `t.stackTraceToString().contains("Material")` will NEVER match in the plan's catch-block; Material/Theme exceptions are swallowed inside `renderViaLayoutlib`, never reach the test's catch

**Citation**: plan `docs/superpowers/plans/2026-04-29-w3d3-alpha-bytecode-rewriting.md:454-470` (renderWithMaterialFallback) + `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:85-92,159-232`.

**Failure mode** (verified by reading the production code):

`LayoutlibRenderer.renderPng` (L85-92):
```kotlin
override fun renderPng(layoutName: String): ByteArray {
    if (!initialized) initBridge()
    return renderViaLayoutlib(layoutName)
        ?: (fallback?.renderPng(layoutName)
            ?: error("LayoutlibRenderer 실패 + fallback 없음: $layoutName"))
}
```

Inside `renderViaLayoutlib`, **every** layoutlib-internal throw is caught and converted to `null`:

- L191-197 (createSession `try { ... } catch (t: Throwable) { t.printStackTrace(System.err); return null }`)
- L210-227 (render `try { ... } catch (t: Throwable) { t.printStackTrace(System.err); return null } finally { session.dispose() }`)

The `MaterialThemeOverlay.wrap` / `ThemeEnforcement` `IllegalArgumentException` raised during `MaterialButton` inflate is consumed inside `createSession`, written to stderr, and swallowed. `renderViaLayoutlib` then returns `null`. `renderPng` then sees a null `fallback` (integration test passes `fallback = null`, see test `LayoutlibRendererIntegrationTest:46`) and throws `error("LayoutlibRenderer 실패 + fallback 없음: $layoutName")` — an `IllegalStateException` whose:

- Message = `"LayoutlibRenderer 실패 + fallback 없음: activity_basic.xml"`
- Cause = `null` (not chained — `error()` creates IllegalStateException with no cause)
- Stack trace = LayoutlibRenderer.renderPng → JUnit framework, ZERO frames mentioning `Material*` or `ThemeEnforcement` because those frames lived inside the swallowed throwable in renderViaLayoutlib

So `t.stackTraceToString().contains("Material")` will **always** be false. The fallback retry in plan Step 5 will never fire on the Material path; the test will fail with the bare IllegalStateException, fail-loud, and α-5 outcome (B) "PASS — fallback `activity_basic_minimal.xml` 로 retry" is unreachable.

The same swallowing also kills the `"ThemeEnforcement"` discriminator. And — separately — the spec/plan's "Material* stack trace 로 fail" assumption (spec §0 B3, §1 acceptance, plan Step 7 outcome B) is built on the same false premise.

**Fix** (pick ONE, not both):

**Fix-1 (cheapest)**: Use `renderer.lastSessionResult` AFTER the (failed) primary render, before re-throwing. The renderer already records `lastCreateSessionResult` (LayoutlibRenderer:67) and `lastRenderResult` (L71); the test can inspect `result.errorMessage` / `result.exception?.javaClass?.name` (L206-207, L216-217 already log these). Replace the catch-block discriminator with:

```kotlin
private fun renderWithMaterialFallback(
    renderer: LayoutlibRenderer,
    primary: String,
    fallback: String,
): Pair<String, ByteArray> {
    return try
    {
        primary to renderer.renderPng(primary)
    }
    catch (t: Throwable)
    {
        val sessionExc = renderer.lastSessionResult?.exception?.javaClass?.name.orEmpty()
        val sessionMsg = renderer.lastSessionResult?.errorMessage.orEmpty()
        if (sessionExc.contains("Material") ||
            sessionMsg.contains("Material") ||
            sessionMsg.contains("ThemeEnforcement"))
        {
            fallback to renderer.renderPng(fallback)
        }
        else
        {
            throw t
        }
    }
}
```

**Fix-2 (more invasive)**: Modify `LayoutlibRenderer.renderViaLayoutlib`'s two catch blocks to chain via `IllegalStateException("...", cause = t)` instead of returning null when the cause is a layoutlib-internal throw (or, simpler, store `lastSessionThrowable` field). The integration test then unwraps `t.cause`. This couples the renderer to test contingency logic — not recommended.

**Recommend Fix-1**: zero production change, the fields are already exposed.

**Required plan additions**:
- Replace lines 449-470 of plan `α-5 Step 5` with Fix-1 code above.
- Add a sentence to plan `α-5 Step 5` explicitly noting the discriminator inspects `renderer.lastSessionResult?.exception/errorMessage`, NOT the thrown `IllegalStateException`'s stack.
- α-6 step 1 description ("신규 carry") — add: "if both layouts fail, capture `renderer.lastSessionResult?.errorMessage` and `exception?.javaClass?.name` and emit them in the BLOCKED message" (resolves D in Section D below).

---

## B. Strongly recommended (do before merge, not strict GO blockers)

### B1. Spec §0 / B5 says "init exception wrapping". Plan α-5 Step 1 says "init 에서 try-catch wrapping" but provides no code. Subagent will guess.

Plan α-5 Step 1 (line 414): *"`MinimalLayoutlibCallback(viewClassLoaderProvider, initializer)` — initializer 가 `(register: (ResourceReference, Int) -> Unit) -> Unit`. init 에서 try-catch wrapping."*

The spec §4.6 lines 442-453 has the actual try-catch code. Plan should either inline it or explicitly cite spec §4.6. Given the plan's pattern of inlining code for other Tasks (α-1 Step 2-3, α-2 Step 1, α-3 Step 1), inline it here as well. Otherwise a subagent may forget the wrapping and we lose the round 2 B5 fix.

### B2. Plan α-5 Step 1 says `advanceNextIdAbove(seeded: Int)` is private; spec §4.6 line 455 also private. But the lambda inside `init {}` (spec §4.6 line 443-447) calls it via direct method ref `advanceNextIdAbove(id)` — fine if same class. Just document that it's instance-private (not `companion`).

### B3. ASM dependency conflict check missing from α-1 Step 4

Round 1 review B6 noted layoutlib-api-31.13.2 may pull ASM transitively. Plan α-1 Step 4 only runs `ClassLoaderConstantsTest`. Add:

```bash
./server/gradlew -p server :layoutlib-worker:dependencies --configuration runtimeClasspath | grep -i asm
```

If `org.ow2.asm:asm:` reports a different version, force-resolution to 9.7 in build.gradle.kts.

### B4. Plan α-5 doesn't verify R.jar exists at runtime — depends on prior `assembleDebug`

Plan α-5 Step 6 calls `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` — but the integration test path uses `RJarSymbolSeeder.seed(rJarPath = sampleAppModuleRoot.resolve(R_JAR_RELATIVE_PATH), …)` per spec §4.7 line 510. If `R.jar` was never built, the test fails with `FileNotFoundException` from `ZipFile(rJarPath.toFile())`. Verify before the integration step:

```bash
ls -la fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar
```

(I verified at session start: file exists, 839052 bytes.) Add the `ls` to plan α-5 Step 6 as a precondition. If missing, instruct the subagent to run `(cd fixture/sample-app && ./gradlew :app:assembleDebug)`.

### B5. R$styleable skip — production tests should also assert byRef/byId have NO styleable entries

Plan α-4 Step 1 lists `R$styleable 은 전체 skip — A2 fix` test, but as a per-`int[]` filter. Round 1 A2 fix is *type-level* skip (R$styleable class entirely). The test should stress this end-to-end: build a synthetic R$styleable with both `int[]` and `int` index fields, run seeder, assert that `register` was invoked **zero** times for ResourceType.STYLEABLE. The plan's wording "R$styleable 은 전체 skip — A2 fix" suggests this is intended; ensure the subagent's test assertion matches (`register` mock never called with `STYLEABLE`).

---

## C. Specific concerns (verified, with file:line evidence)

### C1. Task α-5 atomicity — 5 phases in one commit (callback sig + 12 callsites + renderer wire + contingency layout + test enable). Single atomic? Or split α-5a/α-5b?

**Verdict: KEEP atomic, BUT add an explicit "callsite enumeration" preflight step**.

Why atomic: the callback constructor sig change (`MinimalLayoutlibCallback(provider, initializer)`) breaks all 12 callsites at compile time. Splitting would require either (a) a 2-arg overload + back-compat default = forbidden by CLAUDE.md "no default parameter values"; (b) leaving the codebase un-buildable across two commits = bad. So atomic is correct per W3D2 LM-B1's lesson "atomic across breaking-sig changes".

But α-5 atomicity implies the subagent must touch 6 files in one commit. Plan Step 2 (line 421-425) just says `grep -rn "MinimalLayoutlibCallback {" server/ --include="*.kt"` — this is exactly the W3D2 LM-B1 anti-pattern (relying on grep at execution-time rather than enumerated at plan-time). See C3 below.

### C2. Test count math — Plan claims +23 (142→165). Recount: α-1 (+4), α-2 (+8), α-3 (+1), α-4 (+8), α-5 (+0). Sum = +21. Plan is off by 2.

Verified by reading the plan:
- α-1 Step 3 (line 64-89): 4 new tests added to `ClassLoaderConstantsTest.kt` (existing 3 → 7).
- α-2 Step 1 (line 119-241): 5 in `RewriteRulesTest.kt` + 3 in `AndroidClassRewriterTest.kt` = 8.
- α-3 Step 1 (line 266-336): 1 in `AarExtractorRewriteTest.kt`.
- α-4 Step 1 (line 372-394): 3 in `RJarTypeMappingTest.kt` + 5 in `RJarSymbolSeederTest.kt` = 8.
- α-5: enables existing `LayoutlibRendererIntegrationTest` (already counted in 11 integration); adds 0 unit.

Total: **+21 unit + 1 integration enabled (was @Disabled)**. Plan summary "142 → ~165" should read "142 → 163 unit + 11 → 12 integration". Minor — fix the numbers in α-5 Step 6 (line 480) and §7 acceptance (spec line 586).

### C3. Task α-5 callsite enumeration — Plan Step 2 uses `grep`. Need explicit list per W3D2 LM-B1 lesson.

**Yes, plan should embed the enumerated list**, NOT a grep. I ran the grep this session:

```
$ grep -rn "MinimalLayoutlibCallback {" server/layoutlib-worker/src/ --include="*.kt"
src/test/.../classloader/MinimalLayoutlibCallbackLoadViewTest.kt:25
src/test/.../session/MinimalLayoutlibCallbackTest.kt:22
src/test/.../session/MinimalLayoutlibCallbackTest.kt:31
src/test/.../session/MinimalLayoutlibCallbackTest.kt:39
src/test/.../session/MinimalLayoutlibCallbackTest.kt:47
src/test/.../session/MinimalLayoutlibCallbackTest.kt:53
src/test/.../session/MinimalLayoutlibCallbackTest.kt:59
src/test/.../session/MinimalLayoutlibCallbackTest.kt:65
src/test/.../session/MinimalLayoutlibCallbackTest.kt:71
src/test/.../session/SessionParamsFactoryTest.kt:42
src/test/.../session/SessionParamsFactoryTest.kt:65
src/main/.../LayoutlibRenderer.kt:174
```

Embedding the list in plan α-5 Step 2 makes the change *deterministic* (a subagent doesn't need to re-run grep). Replace plan lines 419-425 with the table above + per-line replacement instruction.

Also note: only `LayoutlibRenderer.kt:174` uses the *real* seeder (`::seedRJarSymbols`); the other 11 are tests that should pass `{ /* no-op */ }` (already specified in plan line 425 and spec §4.7 line 521).

### C4. Task α-3 existing AarExtractorTest — 5 existing tests use synthetic AARs with `byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, ...)` (EOCD-only ZIPs). After rewriteClassesJar integration, does ClassReader crash on these?

**Verified — safe.** I traced the path:

1. `AarExtractorTest.kt:24-28` writes a 22-byte EOCD-only ZIP as `classes.jar` content (it IS a valid empty ZIP — central-directory-end with zero entries).
2. The new `rewriteClassesJar(input, tmpJar)` opens this with `ZipInputStream(input)`. I verified empirically (via Python `zipfile.ZipFile`): EOCD-only ZIP yields `entries = []`. `zin.nextEntry` returns null on first call.
3. The while-loop body is never entered. `zout` writes a valid empty ZIP.
4. `assertTrue(extracted!!.fileName.toString().endsWith(...))` still passes; `cache hit` (mtime check) still passes; `null 반환` test isn't affected (no classes.jar entry); `손상된 ZIP` triggers `ZipException` BEFORE rewriteClassesJar even runs (at the `ZipFile(aarPath.toFile())` outer call).

**No fix needed**. But plan α-3 Step 3 (line 350-356) currently *speculates* about this with prose ("기존 테스트들의 expected fixture (e.g., `lib.jar` 가 빈 ZIP only) 가 rewrite 후에도 valid 한지 검증"). Tighten to: "Verified empirically — EOCD-only ZIP → 0 entries → rewrite no-op → valid empty ZIP output. All 5 pre-existing AarExtractorTest tests pass without fixture upgrade."

### C5. α-5 fallback retry semantics — `renderer.renderPng()` throws `IllegalStateException`. Material exception in cause chain or stderr log only.

**See A1**. The Material exception is in stderr only — never in cause chain, never in the IllegalStateException's stack trace. `t.stackTraceToString().contains("Material")` cannot work as written. NO_GO blocker.

### C6. `activity_basic_minimal.xml` — uses ConstraintLayout + standard Button. ConstraintLayout's `layout_constraintXxx` attrs require R.jar id seeding (Codex B3). Will fallback layout actually render with default-fallback constraint values, or will style 2130903769 lookup recur?

This is a substantive concern. Two questions:

**(i) Will ConstraintLayout itself inflate?** Yes — bytecode rewriting fixes Build CNFE that breaks ConstraintLayout's `<clinit>`. Once Build is `_Original_Build`-rewritten, ConstraintLayout class loads. R.jar id seeding then provides the `layout_constraintTop_toTopOf` attr id mapping. Both blockers are addressed by α before fallback layout runs.

**(ii) Will style 2130903769 (= 0x7f0f03fd) recur?** Plan/spec is correct that the recurrence is style-related (Material theme styles). The fallback layout (per spec §0 B3) **avoids Material widgets** — so MaterialButton's `MaterialThemeOverlay.wrap` doesn't run. But the fallback still uses the **session-level theme** `Theme.Material.Light.NoActionBar` (SessionConstants:42, set at session-init time before any widget). The id 0x7f0f03fd is from app/AAR R.jar (`R$style.*` resolved against the seeded callback). **After R.jar id seeding, `0x7f0f03fd` IS in `byId` mapping to a real ResourceReference.** layoutlib's `RenderResources.findStyle(ResourceReference)` then walks `FrameworkRenderResources` (which only has framework styles loaded). If that style is *AppCompat/Material* (not framework), `findStyle` returns null → "Failed to find style 2130903769" recurs — even on fallback.

**Risk for α-5 outcome (B)**: contingency layout MIGHT NOT actually fix the style failure. Plan α-5 Step 7 outcome (B) "PASS — fallback `activity_basic_minimal.xml` 로 retry" assumes the retry resolves the issue, but if the un-found style is the session theme (not the widget), changing the widget doesn't help.

**Recommend**: in plan α-5 Step 4 (line 431-432), add to `activity_basic_minimal.xml` an explicit `android:theme="@style/Theme.AppCompat.Light.NoActionBar"` override on the root → does NOT solve it either (AppCompat is also library R, not framework). The realistic minimal fallback is: switch session theme override to a *framework* `@android:style/Theme.Material.Light.NoActionBar` and use ConstraintLayout + `<Button>` (no Material). The plan's contingency layout assumes Material removal alone is sufficient — empirically uncertain.

Document this as a known risk in plan α-5 Step 7 outcome (C): "if both layouts FAIL with style 0x7f0f03fd recurrence, the issue is *session theme*, not widget — α delivers id-mapping but framework theme cannot host AppCompat-defined styles. carry to W3D4 (resource value loader for app/library)."

### C7. callback init sequence — α-5 Step 1 changes callback init (wraps exception). Step 1 vs Step 3 ordering OK?

Step 1 changes the **constructor signature** (adds `initializer` param) AND the **init {} body** (wraps initializer in try-catch). Step 3 wires `LayoutlibRenderer` to pass `::seedRJarSymbols` as the initializer. The 11 test callsites pass `{ /* no-op */ }` (Step 2).

**Ordering is fine.** Step 1 changes the class definition. Step 2-4 update callsites. Step 5 enables integration test. Compilation will fail cleanly between Step 1 and Step 2 (signature change without callsite update), but that's expected within an atomic commit — the unit of compilation is the final commit, not intermediate steps. Subagent should run `./gradlew compileKotlin` only after Step 4.

But: callback CONSTRUCTION (`MinimalLayoutlibCallback(...)`) happens AFTER its sig is changed. Step 1 (sig change) → Step 2 (callsites updated to new sig) → Step 3 (LayoutlibRenderer constructs callback w/ method ref) → Step 4 (fixture) → Step 5 (test enabled). Linear. Good.

One subtle hazard: `seedRJarSymbols` (Step 3) calls `ensureSampleAppClassLoader()` which calls `SampleAppClassLoader.build(...)`. If this call happens **before** `Bridge.init` finishes (i.e., during callback construction inside `SessionParamsFactory.build`), the L243 `error("Bridge 가 init 안 됨...")` could fire. But the call chain in `LayoutlibRenderer.renderViaLayoutlib` (L160-176) is: `initBridge()` → load fixture → build SessionParams (callback constructed, initializer fires `seedRJarSymbols` → `ensureSampleAppClassLoader()` succeeds because Bridge is already initialized). Safe.

---

## D. Independent issues

### D1. Synthetic R.jar in RJarSymbolSeederTest — plan says "ASM 또는 javac 로 생성". Specify ASM-based.

Plan α-4 Step 1 (line 388): *"테스트는 `org.junit.jupiter.api.io.TempDir` 로 합성 R.jar 생성 — `R.jar` 안에 합성 `com/example/R$attr.class`, `com/example/R$styleable.class` 등을 ASM 또는 javac 로 생성."*

`javac` from a Kotlin test is awkward (needs ToolProvider.getSystemJavaCompiler). ASM is already on classpath (after α-1) and the existing α-2 tests (plan line 187-241) use ASM. Specify:

> Use `ClassWriter` (ASM) — for each synthetic R$<type> class:
> ```kotlin
> val cw = ClassWriter(0)
> cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/R\$attr", null, "java/lang/Object", null)
> // each static int field:
> cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "windowBackground", "I", null, 0x7F010001).visitEnd()
> // ... (constant value via FieldVisitor.value, last arg)
> cw.visitEnd()
> // package each into a ZipOutputStream(R.jar) under "com/example/R$attr.class"
> ```

Note: `FieldVisitor` constant `value` arg seeds the field's initial value AT class-load time (without `<clinit>`) — this matches AGP's R.jar behavior.

### D2. Real R.jar dependency for α-5 integration test — verified at session start.

```
$ ls -la fixture/sample-app/app/build/intermediates/.../R.jar
-rw-rw-r-- 839052 Apr 29 15:01 .../R.jar
```

Exists (839KB). Plan α-5 Step 6 should reference it via `ls` precondition (B4 above).

### D3. Task α-6 work_log handoff — "신규 carry" is too vague.

Plan α-6 Step 1-3 (line 499) says "일반 work_log 작성 패턴". Plan α-5 Step 7 outcome (C) says "evidence 캡처 후 BLOCKED 보고. 신규 carry."

Specify in α-5 Step 7 (C):
- capture `renderer.lastSessionResult?.exception?.javaClass?.name` and `errorMessage` to `docs/work_log/2026-04-29_w3d3-l3-classloader/alpha-session-log.md`
- classify root cause: "Material*" → resource value loader carry; otherwise → fresh diagnosis
- escalate to user (per orchestrator policy) — do NOT auto-pivot

### D4. Plan claims `BUILD SUCCESSFUL` after α-3 — but if rewriteClassesJar throws on test fixtures, BUILD FAILS.

Resolved by C4: verified the EOCD-only fixtures are safe (rewriteClassesJar produces valid empty ZIP). No defensive guard needed; no fixture upgrade needed. Plan α-3 Step 3's prose (line 356) is correct — but should be tightened to "verified" not "expected" (see C4 above).

### D5. `com.android.resources.ResourceType.STYLEABLE` exists — verified.

```
$ javap -p -cp server/libs/layoutlib-dist/android-34/layoutlib-api-31.13.2.jar com.android.resources.ResourceType | grep STYLEABLE
  public static final com.android.resources.ResourceType STYLEABLE;
```

Plan α-4's `if (resourceType == ResourceType.STYLEABLE) continue` reference is valid. (Also confirms round 1 review D4's verification.)

### D6. Plan α-4 Step 1 — `RJarTypeMappingTest` enumerates 3 tests. Spec §3 TC-α5 says 3 tests but doesn't list 'styleable 도 매핑'. Confirm consistency.

Plan α-4 Step 1 (line 376): `@Test fun \`styleable 도 매핑\``. This is correct — `RJarTypeMapping.fromSimpleName("styleable")` should return `ResourceType.STYLEABLE` (mapping table line 293). The seeder still skips STYLEABLE downstream (per A2 fix), but the mapping itself is total.

### D7. Plan α-5 Step 1 uses `0x7F80_0000` correctly — matches spec round 2 Q2 (line 23).

`FIRST_ID = 0x7F80_0000` — above all AAPT type bytes (0x01..0x10). Plan α-5 Step 1 (line 416). Spec round 2 §0 Q2 (line 23). Consistent.

### D8. Plan α-1 commit message line 105 has trailing `...` — placeholder?

`git commit -m "feat(w3d3-α): ASM 9.7 의존 + ClassLoaderConstants 확장 ..."` — looks like the plan author left placeholder ellipsis. Subagent should write a complete message. Per CLAUDE.md "No placeholders" (plan self-review section claim), tighten:

```
git commit -m "feat(w3d3-α): ASM 9.7 의존 + ClassLoaderConstants 확장 (CLASS_FILE_SUFFIX, INNER_CLASS_SEPARATOR, R_CLASS_NAME_SUFFIX, INTERNAL_NAME_SEPARATOR, EXTERNAL_NAME_SEPARATOR, REWRITE_VERSION)"
```

(Identical issue at α-2 / α-3 commit steps — plan only writes "Commit + push" with no message specified. Subagents will improvise. Acceptable.)

---

## E. Verdict — **NO_GO**

**One hard blocker (A1)**: the test-side fallback discriminator `t.stackTraceToString().contains("Material")` will never match because `LayoutlibRenderer.renderViaLayoutlib` swallows layoutlib-internal exceptions (lines 191-197, 210-227), so the fallback retry never fires. Plan α-5 Step 7 outcome (B) is unreachable as written. Fix-1 (use `renderer.lastSessionResult?.exception` and `errorMessage` instead of the `IllegalStateException`'s stack trace) is a one-method change inside the integration test — no production code change needed.

**Strongly recommended (B1-B5)**: inline the `init {}` try-catch code (B1), add ASM-version conflict check to α-1 (B3), embed the 12-callsite enumerated list in α-5 Step 2 (C3), tighten R$styleable test assertion (B5), add `R.jar` precondition `ls` to α-5 Step 6 (B4).

**Specific concerns (C1-C7)**: most are tractable. C6 (style 0x7f0f03fd recurrence on fallback) is a substantive risk that should be acknowledged in plan α-5 Step 7 outcome (C) — fallback may not actually fix the style failure if the failing style is from session theme (not Material widget).

**Independent (D1-D8)**: cosmetic / clarity. Test count off by 2 (C2). Synthetic R.jar tooling needs explicit ASM specification (D1). Commit messages have placeholder `...` (D8).

**Rationale paragraph**: The plan correctly translates spec round 2's settled decisions into 6 atomic tasks with TDD red→green discipline. The component decomposition is faithful; the LOC budget is reasonable; α-3's caching with REWRITE_VERSION layer correctly addresses round 1 D1; the lambda → method ref refactor (B4) is in. Two structural issues, however, prevent immediate GO. (i) Hard blocker A1: the renderer's existing exception flow (verified by reading LayoutlibRenderer.kt:191-197,210-227) swallows the very Material exception that the plan's fallback retry tries to discriminate against, so the contingency mechanism is wired to a signal that never arrives. The fix is small (one private method in the integration test, querying `renderer.lastSessionResult?.exception` instead of the rethrown IllegalStateException's stack), but the plan as written would lead the implementing subagent to author a fallback path that compiles but never executes — silently regressing α's contingency commitment. (ii) Plan-level enumeration gap (C3, B1, D1): three places where the plan says "grep this", "add try-catch wrapping", "via ASM or javac" without inlining the deterministic artifact, repeating the W3D2 LM-B1 pattern of relying on execution-time discovery for plan-time decisions. After A1 is corrected and B1/C3/D1 inlined, GO_WITH_FOLLOWUPS at most. As written, NO_GO.

---

## Appendix — empirical commands run this session

```bash
# C2 — test count audit (read plan + counted)
# +4 (α-1) + 5+3 (α-2) + 1 (α-3) + 3+5 (α-4) + 0 (α-5) = +21, NOT +23

# C3 — callsite enumeration
grep -rn "MinimalLayoutlibCallback {" server/layoutlib-worker/src/ --include="*.kt"
# → 12 hits, listed in C3

# C4 — EOCD-only ZIP behavior
python3 -c "import zipfile, io; z=zipfile.ZipFile(io.BytesIO(bytes([0x50,0x4B,0x05,0x06]+[0]*18)),'r'); print(z.namelist())"
# → []  (zero entries; rewriteClassesJar's while loop never enters)

# A1 — verify exception swallowing
grep -n "catch (t: Throwable)\|return null\|error(" server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt
# → L91 (error after fallback ?: chain), L193 (catch+return null), L224 (catch+return null)

# D2 — real R.jar exists
ls -la fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar
# → 839052 bytes, mtime 2026-04-29 15:01

# D5 — STYLEABLE in ResourceType enum
javap -p -cp server/libs/layoutlib-dist/android-34/layoutlib-api-31.13.2.jar com.android.resources.ResourceType | grep STYLEABLE
# → public static final com.android.resources.ResourceType STYLEABLE;
```
