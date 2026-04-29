# W3D3-alpha Implementation Plan v1 — Codex Pair Review

Plan reviewed: `docs/superpowers/plans/2026-04-29-w3d3-alpha-bytecode-rewriting.md`.
Settled spec reviewed: `docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md`.

## A. Plan-level blockers (NO_GO)

### A1. Task alpha-5 Step 5 fallback detection cannot catch the actual renderer failure shape

**Where**: plan lines 435-470; current renderer lines `LayoutlibRenderer.kt:85-91`, `191-196`, `213-226`.

**Failure mode**: the proposed `renderWithMaterialFallback` only inspects `t.stackTraceToString()` for `Material` or `ThemeEnforcement` (plan lines 458-463). But `LayoutlibRenderer.renderViaLayoutlib()` catches createSession/render exceptions, prints them to stderr, returns `null`, and `renderPng()` then throws only `IllegalStateException("LayoutlibRenderer 실패 + fallback 없음: $layoutName")`. The original cause is not attached to the thrown exception.

This is not hypothetical. The branch-C evidence shows JUnit saw only:

```text
java.lang.IllegalStateException: LayoutlibRenderer 실패 + fallback 없음: activity_basic.xml
```

while the useful diagnostics were only in renderer stderr / session result (`ERROR_INFLATION msg=android.os.Build$VERSION exc=ClassNotFoundException`) at `branch-c-evidence.md:167-176` and `:220-227`.

If Material/ThemeEnforcement fails through the same path, branch (B) will not retry the fallback layout.

**Fix**: before implementation, change alpha-5 to make fallback classification use renderer-retained diagnostics, not only the thrown wrapper. Acceptable fixes:

- Add a renderer diagnostic field, e.g. `lastFailureDiagnostic` or `lastRenderThrowable`, set it in both `createSession.invoke` catch and `session.render` failure paths, and include it in `renderWithMaterialFallback`.
- Or change `renderPng()` to throw an `IllegalStateException` whose message/cause includes `lastSessionResult.errorMessage`, `lastSessionResult.exception`, and any caught createSession throwable.
- Add a focused test/helper assertion for `isMaterialFailure(thrown, renderer)` so this does not regress.

### A2. Task alpha-4 Step 1 names a test for a private method

**Where**: plan lines 379-388; spec lines 365-383.

**Failure mode**: alpha-4 asks for `parseRClassName 의 패키지 + type 추출 정확`, but the settled spec defines `parseRClassName` as `private fun` inside `RJarSymbolSeeder` (spec line 369). A literal test cannot call it. A subagent may either write a compile-failing test or widen production visibility without an architectural reason.

**Fix**: keep `parseRClassName` private and test the behavior through `RJarSymbolSeeder.seed()` using synthetic ASM-generated classes such as `com/example/R$attr.class`; assert the registered `ResourceReference` namespace/type/name and id. If direct parser testing is required, make the parser an explicitly `internal` helper object in its own file and document why that public surface exists.

## B. Strongly recommended

1. **Split alpha-5 into two green commits**. Keep the compile-breaking signature migration atomic, but split the large task as:
   - `alpha-5a`: callback signature/init/FIRST_ID + all 12 callsites updated with no-op initializers + callback unit tests.
   - `alpha-5b`: renderer `seedRJarSymbols`, contingency layout, integration fallback enable, integration run.
   This preserves W3D2 LM-B1 atomicity without packing callback API migration and T1 enablement into one oversized task.

2. **List the 12 callback callsites explicitly in alpha-5 Step 2**. The plan has the count at line 407 but only gives a grep command at lines 421-425. Add the exact files/counts from spec lines 515-519:
   - `MinimalLayoutlibCallbackTest.kt`: 8
   - `SessionParamsFactoryTest.kt`: 2
   - `MinimalLayoutlibCallbackLoadViewTest.kt`: 1
   - `LayoutlibRenderer.kt`: 1

3. **Make the alpha-4 R.jar fixture exact**. Replace "ASM 또는 javac" (plan line 388) with an ASM-only helper:
   - `ClassWriter(0)`
   - `visit(V17, ACC_PUBLIC or ACC_FINAL, "com/example/R$attr", null, "java/lang/Object", null)`
   - `visitField(ACC_PUBLIC or ACC_STATIC or ACC_FINAL, "layout_constraintTop_toTopOf", "I", null, 0x7F030001).visitEnd()`
   - write to a `ZipEntry("com/example/R$attr.class")`
   Also add `R$styleable` with both `int[]` and index `int` fields to verify full type skip.

4. **Add alpha-5 callback unit tests**. The plan changes non-trivial callback behavior but currently counts no alpha-5 unit tests. Add tests for:
   - initializer registers a seeded `(ResourceReference, id)` and `resolveResourceId(id)` returns it.
   - `getOrGenerateResourceId()` starts at or above `0x7F800000` for new refs, and advances above a seeded high id.
   - initializer exceptions are wrapped as `IllegalStateException` with the original cause.

5. **Add a preflight build step for the real fixture R.jar**. `SampleAppClassLoader` hard-requires `ClassLoaderConstants.R_JAR_RELATIVE_PATH` (`SampleAppClassLoader.kt:43-47`). The R.jar exists in this workspace now, but alpha-5 should explicitly run:
   `cd fixture/sample-app && ./gradlew :app:assembleDebug`
   before the integration test, or state this as a hard precondition.

6. **Tighten branch (C) handoff instructions**. Plan line 486 says "신규 carry"; alpha-6 lines 492-500 are generic. Specify the sequence: capture primary/fallback layout names, `lastSessionResult.status/errorMessage/exception`, stderr key lines, and full stack trace; classify as Material vs Build/CNFE vs resource-value/style; update `alpha-session-log.md`, `handoff.md`, and `docs/plan/08-integration-reconciliation.md`; escalate to the user without improvising a new fix.

7. **Test `REWRITE_VERSION` and cache path behavior**. Alpha-1 adds `REWRITE_VERSION` (plan lines 57-61) but alpha-1 tests only four other constants (lines 64-88). Alpha-3 should assert the extracted AAR path includes the version layer, or alpha-1 should add a simple non-blank/version-format test.

## C. Specific concerns

### C1. Task alpha-5 atomicity

Keeping all of alpha-5 in one commit is technically coherent because the constructor signature change and callsite migration must land together. But it is larger than necessary.

I recommend splitting into alpha-5a/alpha-5b as described in B1. That still honors the LM-B1 lesson: no commit should leave the callback signature half-migrated. The split point should be after a green no-op-initializer migration, before renderer seeding and integration enablement.

### C2. Test cardinality math

The plan/spec count is off.

Current planned unit additions:

| Task | New unit tests |
|---|---:|
| alpha-1 | 4 |
| alpha-2 | 8 |
| alpha-3 | 1 |
| alpha-4 | 8 |
| alpha-5 | 0 specified |
| **Total** | **21** |

Plan line 480 says alpha-1 through alpha-4 add `+18`; actual is `+21`. Spec line 586 says `142 unit + 23 신규 unit = ~165`; the plan as written reaches about `163`, not `165`.

Fix either the math to `142 -> ~163`, or add two alpha-5 callback tests so the `+23` claim becomes true.

### C3. Task alpha-5 callsite enumeration

The grep is useful but not sufficient for subagent execution. The W3D2 LM-B1 lesson was about avoiding missed callsites during signature changes. Add the explicit 12-site list from spec lines 515-519 directly into the plan.

Also note that the current callsites use trailing-lambda syntax (`MinimalLayoutlibCallback { ... }`), while plan line 425 describes `MinimalLayoutlibCallback({ cl })`. The intended replacement should be shown for both production and tests.

### C4. Task alpha-3 existing AarExtractor tests with synthetic classes.jar bytes

This is acceptable as written, with one documentation tweak.

The current `AarExtractorTest.makeAar()` writes a valid empty ZIP EOCD as the `classes.jar` bytes (`AarExtractorTest.kt:25-27`). `rewriteClassesJar()` will open it with `ZipInputStream`, see no entries, and never call `ClassReader`. For non-class entries, the spec already gates on `entry.name.endsWith(CLASS_FILE_SUFFIX)` before rewriting (spec lines 235-243).

Recommended tweak: add a sentence to alpha-3 Step 3 that existing tests pass because the EOCD fixture is an empty valid jar with zero entries, and add a non-class entry case if desired.

### C5. Alpha-5 fallback retry semantics

As written, not safe. See A1.

The catch block catches the generic `IllegalStateException`, but the generic error does not preserve the Material/ThemeEnforcement cause. The plan must either propagate renderer diagnostics or classify from `lastSessionResult` plus a new retained caught throwable.

### C6. `MinimalLayoutlibCallback` callsite in `LayoutlibRenderer.kt`

The order is compile-safe only if alpha-5 remains atomic or is split at a green no-op migration point.

Runtime order is sound: `renderPng()` initializes the bridge first (`LayoutlibRenderer.kt:85-88`), then `renderViaLayoutlib()` constructs `MinimalLayoutlibCallback` while building `SessionParams` (`LayoutlibRenderer.kt:172-175`). The proposed `seedRJarSymbols()` will call `ensureSampleAppClassLoader()`, and by then `classLoader` has been set during `initBridge()`. Existing unit callsites must use no-op initializers to avoid touching R.jar.

### C7. `activity_basic_minimal.xml` as contingency

The contingency still exercises ConstraintLayout, so it still depends on alpha's R.jar id seeding for `layout_constraint*` attrs. That is expected, not a reason to remove ConstraintLayout. Local `javap` against the real R.jar shows ConstraintLayout attrs such as `layout_constraintTop_toTopOf = 2130903705`, and those should be registered through `R$attr`.

The original style failure includes Material button style evidence: `0x7f0f03fd` maps to `Widget_MaterialComponents_Button` in `com.google.android.material.R$style`. Replacing `MaterialButton` with framework `Button` should avoid that specific style lookup, while ConstraintLayout continues to validate classloader + R.attr seeding.

Plan fix: alpha-5 should run the fallback layout directly if primary fails, and record whether primary or fallback passed. Do not rely solely on the Material string classifier until A1 is fixed.

## D. Independent issues

1. **R.jar fixture for `RJarSymbolSeederTest`**: use ASM byte-level class generation, not javac. Specify helper code as in B3. This avoids source layout, compiler availability, and escaping problems around `$` inner-class names.

2. **Real fixture R.jar freshness**: the current workspace has `fixture/sample-app/.../R.jar`, but alpha-5 should still run `:app:assembleDebug` before T1. The predecessor handoff records that `assembleDebug` emits the manifest (`handoff.md:8`, `session-log.md:131-132`); the same build produces the R.jar that alpha-5 needs.

3. **Branch (C) carry detail**: alpha-6 should not say only "신규 carry". Required carry data: stack trace, renderer stderr key lines, primary/fallback layout attempted, `lastSessionResult`, classification, suspected next component, and explicit user escalation.

4. **Spec drift in risk text**: spec line 546 says duplicate-ref mitigation is `if byRef.contains(ref) ignore`, but the callback initializer code at spec lines 443-447 unconditionally assigns `byRef[ref] = id` and `byId[id] = ref`. If duplicate refs are considered a real risk, implement the stated policy or remove the mitigation claim.

5. **Integration evidence command**: alpha-5 Step 6 should run the targeted integration test as well as the tag suite:
   `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest" -PincludeTags=integration`
   This makes the T1 gate output smaller and easier to classify before running the broader integration suite.

## E. Verdict

**NO_GO** until A1 and A2 are fixed.

The architecture and task order are mostly sound, and the settled spec decisions are represented. The blocker is execution fidelity: the Material fallback path cannot work with the current renderer failure propagation, and alpha-4 asks for a test against a private method. Both are small plan fixes, not design rework. After those corrections, I would move this to **GO_WITH_FOLLOWUPS**, with the alpha-5 split and explicit ASM R.jar fixture as the main follow-ups.
