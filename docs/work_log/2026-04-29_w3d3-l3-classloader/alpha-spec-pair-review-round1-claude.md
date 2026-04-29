# W3D3-α Spec Review — Claude (1:1 pair half)

**Reviewer**: Claude Opus 4.7 (1M context).
**Spec**: `docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md`.
**Verdict**: **NO_GO** (1 hard blocker + 1 strongly likely blocker; multiple smaller bugs).
**Date**: 2026-04-29.

---

## A. Blockers (NO_GO)

### A1. NAME_MAP overrewrites classes that exist BOTH prefixed and non-prefixed in layoutlib — would break references that work today

**Citation**: spec §2.3 (NAME_MAP, all 25 entries) + §4.1 (`RewriteRules.NAME_MAP`).

**Evidence (empirical, this session)**:

```
$ unzip -l server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | grep -c "android/os/Build\.class"
0
$ unzip -l server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | grep -c "android/view/SurfaceView\.class"
1   ← non-prefixed EXISTS
$ unzip -l server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | grep -c "android/view/_Original_SurfaceView\.class"
1   ← _Original_ ALSO exists

# Per-class summary (non-prefixed exists?, _Original_ exists?):
android/os/Build:                       non=0, _Original_=1   ← rewrite REQUIRED
android/os/ServiceManager:              non=1, _Original_=1   ← rewrite WRONG
android/view/SurfaceView:               non=1, _Original_=1   ← rewrite WRONG
android/view/WindowManagerImpl:         non=1, _Original_=1   ← rewrite WRONG
android/view/textservice/TextServicesManager: non=1, _Original_=1 ← rewrite WRONG
android/webkit/WebView:                 non=1, _Original_=1   ← rewrite WRONG
```

**Why this is a blocker**: layoutlib intentionally publishes BOTH versions for the non-Build classes:
- The non-prefixed (e.g., `SurfaceView`) is a shim/public surface intended for external bytecode (AAR) to reference.
- The `_Original_` version is layoutlib's internal implementation, called BY the shim.

The dual structure is deliberate. AAR bytecode that says `new android.view.SurfaceView(ctx)` or extends `WebView` already resolves correctly today against the non-prefixed class. Rewriting that reference to `_Original_SurfaceView` **bypasses the layoutlib shim**, lands on the internal class which expects internal invariants, and silently breaks behavior (or fails verifier — `_Original_` is often package-private API surface, super-call shapes differ).

**Only the Build family (4 entries: `Build`, `Build$Partition`, `Build$VERSION`, `Build$VERSION_CODES`) is genuinely missing the non-prefixed class** and is therefore the only legitimate rewrite target. The branch (C) diagnosis stack trace itself names exactly `android.os.Build$VERSION` — not SurfaceView/WebView/ServiceManager.

**Fix**: Reduce NAME_MAP to 4 entries (the Build family only). Drop the other 21. Update spec §2.3, §3 TC-α1 LOC, and §6 Q1.

If a follow-up CNFE surfaces in WebView/SurfaceView/ServiceManager during empirical run, **investigate first** (likely a separate root cause — maybe a transitive `import` of an inner class that isn't in the public shim) — do NOT add the rewrite back without verifying the AAR does NOT already resolve it.

### A2. R$styleable index-fields are seeded as bogus resource ids — `byRef` and `byId` get polluted, `byId[0]` collisions are catastrophic

**Citation**: spec §4.5 RJarSymbolSeeder.kt lines 380-389:

```kotlin
// styleable 의 int[] 같은 array 필드는 skip
if (field.type != Int::class.javaPrimitiveType)
{
    continue
}
field.isAccessible = true
val value = field.getInt(null)
register(ResourceReference(namespace, type, field.name), value)
```

This skips `int[]` (the styleable definition arrays — correct). But **R$styleable also contains `int` index fields** like:

```
public static int ActionBar_background = 0;
public static int ActionBar_backgroundSplit = 1;
public static int ActionBar_backgroundStacked = 2;
…
```

These are array indices into the styleable's int[] — not resource IDs. They are 0, 1, 2, … 79 (per styleable). The seeder's `field.type != Int::class.javaPrimitiveType` check **does not skip them** (they ARE primitive int).

**Empirical magnitude** (this fixture):

```
$ javap -p androidx/constraintlayout/widget/R$styleable.class | grep -c "public static int "
1265   ← int (index) fields
$ javap -p ... | grep -c "public static int\[\]"
79     ← int[] (the actual styleable definitions, skipped correctly)
```

**Consequences**:
1. `byRef[ResourceReference(STYLEABLE, "ActionBar_background")] = 0` — bogus, plus
2. `byId[0] = ResourceReference(STYLEABLE, "ActionBar_background")` then immediately overwritten by the next R$styleable class with its own `_background = 0` field (constraintlayout, material, appcompat all overlap). `byId[0]` is fundamentally undefined.
3. `byId[N]` for small N becomes a chaotic mess of styleable index labels.
4. **Critical**: layoutlib's BridgeContext.resolveResourceId may legitimately call `callback.resolveResourceId(0)` somewhere; getting back `ResourceReference(STYLEABLE, "ActionBar_background")` is a wrong answer that may downstream into a NullPointerException or wrong-style lookup.
5. `nextId` advances correctly above 0x7F0A_0000 regardless — that's not the issue. The issue is byRef/byId pollution.

**Fix**: Skip `R$styleable` entirely (the styleable type's index fields aren't resource ids; the int[] arrays are styleable definitions handled by the layoutlib parseStyleable() machinery — see `Bridge.parseStyleable` private method). Two ways:

```kotlin
// Option 1 — skip whole class on type:
if (resourceType == ResourceType.STYLEABLE) continue   // in seed()

// Option 2 — keep STYLEABLE filter to int[] only:
if (resourceType == ResourceType.STYLEABLE && field.type != IntArray::class.java) continue
// then handle int[] (currently skipped) — but layoutlib already loads styleable arrays via Bridge.parseStyleable
```

Recommend Option 1 (cleanest). The R$styleable class is layoutlib's `Bridge.parseStyleable()` territory — the seeder shouldn't touch it.

Update spec §4.5 + §3 TC-α4 (which currently asserts "int[] 필드 (R$styleable) skip" — the test should ALSO assert that no field is seeded from R$styleable at all).

---

## B. Strongly recommended (do before merge, not strict GO blockers)

### B1. CLASS_FILE_SUFFIX is referenced but never added — spec §4.3 / §4.5 import that constant

`AarExtractor` uses `entry.name.endsWith(CLASS_FILE_SUFFIX)` and `RJarSymbolSeeder.seed` imports `ClassLoaderConstants.CLASS_FILE_SUFFIX` — but spec §4.5 **lists the existing ClassLoaderConstants and does NOT show `CLASS_FILE_SUFFIX` being added**. Verified against the actual file (`server/.../classloader/ClassLoaderConstants.kt`): no `CLASS_FILE_SUFFIX` constant. Reference compile-fails.

Add to ClassLoaderConstants.kt:
```kotlin
/** Standard JVM .class file suffix used in classpath entry name checks. */
const val CLASS_FILE_SUFFIX = ".class"
```

Mention this addition explicitly in spec §3 row α1 (or new α-table row), not buried in §4.3 prose.

### B2. Callsite count undercounted — actually 12 sites need updating, not "8+2"

Spec §4.7 says *"호출 부 (`MinimalLayoutlibCallbackTest` / `SessionParamsFactoryTest` 등 기존 8+2 sites)"*. Empirical count:

```
$ grep -rn "MinimalLayoutlibCallback {" server/layoutlib-worker/src/ | wc -l
12
```

Breakdown:
- `MinimalLayoutlibCallbackTest.kt`: 8 sites
- `SessionParamsFactoryTest.kt`: 2 sites
- `MinimalLayoutlibCallbackLoadViewTest.kt`: 1 site
- `LayoutlibRenderer.kt`: 1 site

Update spec §4.7 to read "12 sites total" and ensure α9 LOC delta accommodates `initializer = { /* no-op */ }` arg added everywhere.

### B3. Material theme enforcement risk (5.2.C contingency) is the most likely real blocker once Build CNFE is fixed

Spec §5.2 (C) acknowledges this. The branch (C) trace conveniently surfaced Build CNFE first because it fails earlier in inflate. After α fixes Build, `MaterialThemeOverlay.wrap` will run during MaterialButton inflate against `Theme.Material.Light.NoActionBar` (which lacks `?attr/colorPrimaryVariant`, `?attr/colorOnSurface`, etc.) — known to throw `IllegalArgumentException: This component requires that you specify a valid …Theme.MaterialComponents…`.

**Recommend**: add a pre-flight unit test inside α that exercises a **synthetic** MaterialButton inflate against the same SessionParams to detect the throw before T1 gate. Then α can either:
- swap acceptance gate to `activity_basic_minimal.xml` (§9.1) up-front; or
- continue and accept T1 fail → §9.1 fallback per spec.

Either way, the contingency layout (`activity_basic_minimal.xml`) needs to be **created in this α** (it's referenced in §9.1 as a new file but is not listed in the §3 component decomposition).

### B4. "Lambda Avoid" CLAUDE.md violation — `initializer` is a lambda by design

Spec §4.6 introduces `private val initializer: (registerSymbol: (ResourceReference, Int) -> Unit) -> Unit`. This is a non-trivial lambda used for multi-line behavior (R.jar symbol enumeration + advanceNextIdAbove). CLAUDE.md "Lambdas — Avoid" says **"Never lambdas for multi-line complex logic — extract a named method"**.

Spec already partially complies because the actual seeding logic lives in named `RJarSymbolSeeder.seed`. But the **callsite glue lambda in §4.7** wraps two non-trivial steps inline:

```kotlin
val seeder: (((ResourceReference, Int) -> Unit) -> Unit) = { register ->
    val sampleAppCL = ensureSampleAppClassLoader()
    val rJarPath = sampleAppModuleRoot.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH)
    RJarSymbolSeeder.seed(rJarPath, sampleAppCL, register)
}
```

**Fix**: extract to a private method `LayoutlibRenderer.seedRJarSymbols(register: (ResourceReference, Int) -> Unit)` and pass a method reference (`this::seedRJarSymbols`). Keeps the lambda violation small — function-reference is implicitly a fn type.

### B5. Reflection-based seeding initialization race — class init-time side effects can block render

`RJarSymbolSeeder.seedClass` calls `loader.loadClass(fqcn)` → triggers `<clinit>` of every R$* class in R.jar (159 classes per `unzip -l`). Each `<clinit>` is a constant-pool int load — fast, but it forces R.jar JAR read I/O upfront. On a worker fork doing exactly one render, this adds ~100ms-1s of warmup before render begins. Acceptable, but **document in spec §5.1 (위험)** and pre-disclose to user.

Smaller race concern: `MinimalLayoutlibCallback.<init>` calls `initializer` synchronously inside `init {}` block. If `getOrGenerateResourceId` is called from any callback before constructor completes (impossible in this code path — Kotlin completes init before `this` escapes the constructor — but worth a comment).

### B6. ASM 9.7 dependency — verify against existing classpath conflicts

Spec §3 row α1 adds `org.ow2.asm:asm:9.7` and `asm-commons:9.7`. Layoutlib-api 31.13.2 may already pull in ASM transitively. Check:

```bash
./gradlew :server:layoutlib-worker:dependencies --configuration runtimeClasspath | grep -i asm
```

If a version mismatch exists, force-resolution to 9.7 in build.gradle.kts.

---

## C. Q1-Q3 stances

### Q1 — NAME_MAP completeness

**DISAGREE WITH SPEC**. Spec claims "25 entries are sufficient" — empirical evidence shows **only 4 of those 25 should be rewritten** (Build family). The other 21 entries (SurfaceView*, WindowManagerImpl*, TextServicesManager, WebView*, ServiceManager*) all have non-prefixed counterparts in layoutlib JAR — rewriting them would break references that already resolve correctly. See A1.

Reduce NAME_MAP to:
```
android/os/Build → android/os/_Original_Build
android/os/Build$Partition → android/os/_Original_Build$Partition
android/os/Build$VERSION → android/os/_Original_Build$VERSION
android/os/Build$VERSION_CODES → android/os/_Original_Build$VERSION_CODES
```

Empirical verification command:
```bash
unzip -l layoutlib-14.0.11.jar | grep -E "android/(os/Build|view/SurfaceView|view/WindowManagerImpl|view/textservice/TextServicesManager|webkit/WebView|os/ServiceManager)"
```

### Q2 — R.jar id ↔ callback nextId collision

**SPEC ANSWER PARTIALLY CORRECT, MITIGATION INCOMPLETE**.

Empirical R.jar id ranges (this fixture, all 22 R$id classes + other R$type classes):

```
R$attr        0x7F010000-0x7F0304DF
R$animator    0x7F020000-0x7F020021
R$attr        0x7F030000-0x7F0304DF   (across all packages)
R$bool        0x7F040000-0x7F040002
R$color       0x7F050000-0x7F050322
R$dimen       0x7F060000-0x7F060322
R$drawable    0x7F070028-0x7F0700E4
R$id          0x7F080001-0x7F0801F7
R$integer     0x7F090000-0x7F090043
R$interpolator 0x7F0A0000-0x7F0A0011  ← OVERLAPS spec nextId start 0x7F0A_0000!
R$layout      0x7F0B0000-0x7F0B006D
R$plurals     0x7F0D0000
R$string      0x7F0E0000-0x7F0E00AA
R$style       0x7F0F0000-0x7F0F0466
R$styleable   0x0-0x7E (index fields, not real ids)
```

**Spec's `FIRST_ID = 0x7F0A_0000` is the start of the R$interpolator type-byte range.** After seeding, `advanceNextIdAbove` will move nextId to `max(R$interpolator id) + 1 = 0x7F0A0012` (or higher if material defines more interpolators). nextId monotonically goes up from there.

**Risk**: Once nextId climbs past 0x7F0AFFFF, layoutlib may inspect the type byte (`(id >> 16) & 0xFF`) of the dynamic id. From layoutlib internals (Bridge.sRMap / DynamicIdMap), layoutlib's own dynamic ids start from a different seed (`DYNAMIC_ID_SEED_START`, separate constant) and don't share this space. But callback-generated ids enter `byId` with a TYPE-BYTE that doesn't correspond to the resource type. If layoutlib (or a render-resource resolver) ever round-trips id → ResourceType via the type byte, mismatches will silently misroute.

**Better mitigation**: **change `FIRST_ID` to 0x7F100000 or higher** — above all known AAPT type-bytes (the highest currently used is 0x0F = STYLE; 0x10+ is unused space). Specifically `0x7F800000` would be safest (very high bit avoids any AGP type-byte clash). The spec's `advanceNextIdAbove` works but starts in a polluted region.

Also: **nextId increments per ResourceReference, not per type**. Two different generated refs (e.g., `(ATTR, foo)` and `(STRING, bar)`) get sequential ids. If layoutlib derives `getResourceType(id)` from id-byte, both end up with same wrong byte. Spec doesn't address this. Acceptable for v1 but document in §1.2 / §5.1 as a known limitation.

### Q3 — R$styleable int[] skip

**DISAGREE WITH SPEC INTENT, AGREE WITH SPEC CODE OUTCOME — but for the WRONG reason, AND the index-int fields are NOT skipped**.

The spec's analysis is correct that int[] arrays don't need to be registered (layoutlib's `Bridge.parseStyleable()` handles them). But the spec's CODE only filters `field.type != Int::class.javaPrimitiveType` which skips arrays but **keeps the 1265 int index fields per R$styleable class** — these are 0-79 array indices, not resource ids. See A2 above.

**Recommended stance**: skip the entire `R$styleable` ResourceType in the seeder loop. layoutlib has its own machinery (`Bridge.parseStyleable`) that loads styleables from a parsed XML/binary path, not from R.jar reflection.

Confirm by inspection of `com.android.layoutlib.bridge.Bridge`:
```bash
javap -p -cp layoutlib-14.0.11.jar com.android.layoutlib.bridge.Bridge | grep -i styleable
# → private static void parseStyleable() throws java.lang.Exception;
```

`Bridge.parseStyleable` runs at `Bridge.init()` time and populates layoutlib's internal styleable map from framework data — separate from project R.jar.

---

## D. Independent issues

### D1. ASM rewrite performance estimate

```
Empirical: 41 AAR entries, total classes.jar payload = 6,460,551 bytes (~6.5 MB).
ASM ClassRemapper (COMPUTE_MAXS only, no frame compute) ≈ 5-10 MB/sec single-threaded.
```

First-run cost: **~1-2 seconds**. Subsequent runs cached (mtime-idempotent in AarExtractor). Acceptable.

Concern: cache key in `AarExtractor` uses sha1(absolute path of AAR), not a content hash including the rewrite version. **If NAME_MAP changes (likely to happen — see A1), existing caches will be served stale**. Add a NAME_MAP version constant to the cache directory key:

```kotlin
const val REWRITE_VERSION = 1   // bump on NAME_MAP changes
val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR)
                     .resolve("v$REWRITE_VERSION")
                     .resolve(key)
```

### D2. CLAUDE.md compliance — magic strings handling

Spec §4.3 introduces `entry.name.endsWith(CLASS_FILE_SUFFIX)` — good (constant addition required, see B1). But:

- Spec §4.5 RJarSymbolSeeder.kt line 343 uses `internalName.lastIndexOf('$')` — **magic char `'$'`**. Per CLAUDE.md "Zero Tolerance", this is a domain-meaning literal (Java inner-class separator). Constantize:
  ```kotlin
  const val INNER_CLASS_SEPARATOR = '$'
  ```
- Spec §4.5 line 349 uses `before.endsWith("/R")` — magic substring `"/R"`. Constantize as `R_CLASS_NAME_SUFFIX = "/R"`.
- Spec §4.5 line 332 uses `internalName.replace('/', '.')` — magic chars `/` and `.`. These are well-known JVM internal/external name separators; CLAUDE.md tolerance varies, but for consistency with the file's other constants, define `INTERNAL_NAME_SEPARATOR = '/'` and `EXTERNAL_NAME_SEPARATOR = '.'` once.

### D3. Race in MinimalLayoutlibCallback init

Spec §4.6:
```kotlin
init {
    initializer { ref, id ->
        byRef[ref] = id
        byId[id] = ref
        advanceNextIdAbove(id)
    }
}
```

Two concerns:
1. **No happens-before for `byRef`/`byId`**: layoutlib invokes callback methods from the rendering thread (single-threaded under `Bridge.getLock()`). Init completes on the constructing thread before the callback is published to layoutlib via `SessionParams`. Java Memory Model: writes by the constructing thread to `mutableMapOf` instances are visible to the same thread when `this` escapes. **But** if `LayoutlibCallback` is read by another thread, the maps are NOT volatile-published and writes may not be visible.

   Verify via empirical check — `Bridge.createSession` and `RenderSessionImpl.render` execute on caller thread inside the `Bridge.getLock()` reentrant lock. Same thread → safe. **Document this invariant** in MinimalLayoutlibCallback Kdoc + spec §4.6: "callback is single-threaded under Bridge lock; init writes are observed by render".

2. **`@Synchronized` on getOrGenerateResourceId/resolveResourceId is now overkill** if we trust the single-thread invariant — but it's also harmless.

3. **`initializer` may throw** (R.jar I/O failure, ZipException, ClassNotFoundException). If it does, `MinimalLayoutlibCallback.<init>` throws and the renderer fails fast. That's correct, but the rethrown exception loses context. Wrap:
   ```kotlin
   init {
       try { initializer(::registerSymbol) }
       catch (t: Throwable) { throw IllegalStateException("R.jar seeding failed", t) }
   }
   ```

### D4. Verified ResourceType + ResourceNamespace availability

```
$ javap -p -cp server/libs/layoutlib-dist/android-34/layoutlib-api-31.13.2.jar com.android.resources.ResourceType
public final class com.android.resources.ResourceType extends java.lang.Enum<...> {
  ANIM, ANIMATOR, ARRAY, ATTR, BOOL, COLOR, DIMEN, DRAWABLE, FONT, FRACTION,
  ID, INTEGER, INTERPOLATOR, LAYOUT, MENU, MIPMAP, NAVIGATION, PLURALS, RAW,
  STRING, STYLE, STYLEABLE, TRANSITION, XML, PUBLIC, AAPT, OVERLAYABLE, STYLE_ITEM, ...
}

$ javap -p -cp ... com.android.ide.common.rendering.api.ResourceNamespace
public class ResourceNamespace ... {
  public static ResourceNamespace fromPackageName(java.lang.String);
  ...
}
```

**Both available** on layoutlib-api-31.13.2.jar — already on worker classpath. Spec §4.4 / §4.5 imports are valid.

### D5. LayoutlibCallback contract — threading

Inspected `com.android.layoutlib.bridge.impl.RenderSessionImpl`:
```
public Result init(long);
public Result inflate();
public Result render(boolean);
```

All entry points are caller-thread methods. `Bridge.getLock()` is a `ReentrantLock` — taken by `RenderAction.acquire()` per render. layoutlib does NOT spin up worker threads for inflate/render. Callback invocations therefore happen on the same thread that called `Bridge.createSession` / `session.render()`.

**Conclusion**: spec's init-time seed is safe under the single-thread invariant. Add a prominent comment to MinimalLayoutlibCallback.kt asserting this invariant and pinning it via a single-threaded test.

### D6. Spec §3 LOC table claims 270 prod + 480 test — **doesn't include callsite update LOC**

12 callsites × ~1-2 lines each = +20 LOC test churn. Spec §3 row α7 says "+25" which absorbs renderer wiring but not the test-side `initializer = {}` arg. Bump α9 LOC or add a separate callsite-update row.

### D7. `parseRClassName` rejects "R$" classes that don't have a package — accidentally OK but undocumented

```kotlin
val before = internalName.substring(0, dollarIdx)
if (!before.endsWith("/R")) { return null }
```

Test case `R$attr` (no package, internalName = `R$attr`) → `before = "R"` → doesn't end with `"/R"` → null → skipped. This is fine for AGP-built R.jars (always package-prefixed), but document the assumption + add a TC-α4 test asserting bare `R$attr` is silently skipped.

Also `parseRClassName` doesn't reject classes named `Foo$attr` (i.e., not actually a R-class). Edge case unlikely but a one-line `before = "..."` log would help diagnostics.

---

## E. Verdict — **NO_GO**

**Two hard blockers** (A1, A2) require spec changes before implementation begins:

1. **A1 (NAME_MAP overrewrite)**: Rewriting all 25 entries breaks classes that resolve fine today. NAME_MAP should be 4 entries (Build family only) until empirical evidence shows another class is genuinely missing. This is a correctness blocker — not "we should test this", but "the spec as written will regress working code paths and the test suite would catch the regression as a fresh failure mode after Build is fixed".

2. **A2 (R$styleable index-field pollution)**: 1265 bogus entries per styleable class class get registered with array-index values as resource IDs, polluting `byId` (collisions on small ints) and `byRef` (wrong type tagging). The spec text says "skip int[]" but the code only skips arrays — it lets through 16× more bogus int fields than it filters. Skip the entire `R$styleable` type.

Plus several smaller defects (B1-B6, D1-D7) that are tractable but should be fixed before merge.

**Rationale paragraph**: The α spec is structurally sound — bytecode rewriting plus R.jar id seeding is the right pair of mechanisms for the two branch-(C) blockers. But two of its core asserts are empirically wrong: (1) the layoutlib JAR contains both prefixed and non-prefixed versions of all the rewriting targets except Build, so the spec's NAME_MAP is 5× too aggressive and would silently break working code; (2) the seeder code's "skip int[]" filter does not match the spec's stated intent of "skip R$styleable" and lets 1265 array-index ints per styleable class poison the callback's resource maps. Both are caught by reading the layoutlib JAR contents and a single styleable class's javap output — neither requires running the implementation. After fixing A1 and A2, the contingency in §5.2(C) (Material theme enforcement) is the most likely next blocker, but is correctly scoped to a fallback layout. With A1 + A2 fixed, the rest of α is GO_WITH_FOLLOWUPS at most. As written, NO_GO.

---

## Appendix — empirical commands run this session

```bash
# A1 — verify dual structure in layoutlib
unzip -l server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | grep -E "android/(os/Build|view/SurfaceView|view/WindowManagerImpl|view/textservice/TextServicesManager|webkit/WebView|os/ServiceManager)"

# A2 — quantify R$styleable field types
unzip -p .../R.jar androidx/constraintlayout/widget/R\$styleable.class > /tmp/r_st.class
javap -p /tmp/r_st.class | grep -c "public static int "        # → 1345 total fields
javap -p /tmp/r_st.class | grep -c "public static int\[\]"     # → 79 int[] (skipped)
javap -p /tmp/r_st.class | grep -cE "public static int [a-zA-Z]" # → 1265 int (NOT skipped — bug)

# Q2 — R.jar id range survey
for cls in $(unzip -l .../R.jar | awk '/R\$[a-z]+\.class/ {print $NF}'); do
  unzip -p .../R.jar "$cls" > /tmp/x.class
  vals=$(javap -p -constants /tmp/x.class | grep -E "public static int [a-zA-Z_]" | grep -oE "= [0-9]+" | awk '{print $2+0}' | sort -un)
  printf "%-60s 0x%X-0x%X\n" "$cls" $(echo "$vals" | head -1) $(echo "$vals" | tail -1)
done

# D4 — verify ResourceType / ResourceNamespace
javap -p -cp server/libs/layoutlib-dist/android-34/layoutlib-api-31.13.2.jar com.android.resources.ResourceType
javap -p -cp server/libs/layoutlib-dist/android-34/layoutlib-api-31.13.2.jar com.android.ide.common.rendering.api.ResourceNamespace

# D1 — perf estimate
mf=fixture/sample-app/app/build/axp/runtime-classpath.txt
total=0; for e in $(cat $mf); do
  [ "${e##*.}" = "aar" ] && total=$((total + $(unzip -l "$e" classes.jar | awk '/classes\.jar$/ {print $1}')))
done; echo $total   # → 6,460,551 bytes

# B2 — callsite count
grep -rn "MinimalLayoutlibCallback {" server/layoutlib-worker/src/ | wc -l   # → 12
```
