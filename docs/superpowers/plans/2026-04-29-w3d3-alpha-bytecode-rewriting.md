# W3D3-α Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** branch (C) 의 두 blocker (Build CNFE + R.jar id 불일치) 를 ASM bytecode rewriting + R.jar id seeding 으로 해결, `LayoutlibRendererIntegrationTest` 의 T1 gate 를 SUCCESS 로 전환.

**Architecture:**
- AAR `classes.jar` 적재 시점에 ASM `ClassRemapper` 로 `android/os/Build*` → `android/os/_Original_Build*` rewrite (4 entries NAME_MAP).
- `MinimalLayoutlibCallback` 의 init 에서 R.jar 의 모든 R$<type> 클래스 (R$styleable 제외) 를 enumerate, static int 필드를 byRef/byId 에 사전-populate.
- `FIRST_ID` 를 `0x7F80_0000` 으로 시프트 — AAPT type-byte 와 disjoint.

**Tech Stack:** Kotlin 1.9 / JDK 17 / `org.ow2.asm:asm:9.7` + `asm-commons:9.7` 신규. JUnit Jupiter Assertions (NOT kotlin.test).

**Spec**: [`2026-04-29-w3d3-alpha-bytecode-rewriting-design.md`](../specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md). 모든 결정은 spec round 2 본문에 일치.

**Pair-review status**: spec round 1 페어 리뷰 close (Claude NO_GO 2 blockers → spec round 2 반영; Codex 명시 verdict 미생성). 본 plan v1 에 대한 별도 round 2 페어 리뷰 진입 후 구현 phase.

---

## Task α-1: `ClassLoaderConstants` 확장 + ASM Gradle 의존

**Files:**
- Modify: `server/layoutlib-worker/build.gradle.kts`
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt`
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt` (기존 3 + 신규 4)

- [ ] **Step 1: build.gradle.kts 에 ASM 의존 추가**

```kotlin
dependencies {
    // ... 기존 ...
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
}
```

- [ ] **Step 2: ClassLoaderConstants 에 신규 상수 추가**

기존 파일 그대로 보존하고 끝에 append:

```kotlin
/** Standard JVM .class file suffix used in classpath entry name checks. */
const val CLASS_FILE_SUFFIX = ".class"

/** Java inner class separator (e.g., `R$attr`). */
const val INNER_CLASS_SEPARATOR = '$'

/** R 클래스 이름 접미사 — `<package>/R$<type>` 형태에서 `<package>/R` 부분 검증용. */
const val R_CLASS_NAME_SUFFIX = "/R"

/** JVM internal name separator (`/`). */
const val INTERNAL_NAME_SEPARATOR = '/'

/** JVM external (Java) name separator (`.`). */
const val EXTERNAL_NAME_SEPARATOR = '.'

/**
 * AarExtractor 의 cache key 버전. NAME_MAP 또는 transformer 변경 시 bump 하여
 * stale cache 회피. round 2 의 신설.
 */
const val REWRITE_VERSION = "v1"
```

- [ ] **Step 3: ClassLoaderConstantsTest 에 4개 신규 테스트 (TDD red)**

기존 3 tests 에 append:

```kotlin
@Test
fun `class file suffix 가 dot 으로 시작`() {
    assertTrue(ClassLoaderConstants.CLASS_FILE_SUFFIX.startsWith("."))
}

@Test
fun `inner class separator 는 dollar`() {
    assertEquals('$', ClassLoaderConstants.INNER_CLASS_SEPARATOR)
}

@Test
fun `R class name suffix 는 slash R`() {
    assertEquals("/R", ClassLoaderConstants.R_CLASS_NAME_SUFFIX)
}

@Test
fun `internal name 과 external name separator`() {
    assertEquals('/', ClassLoaderConstants.INTERNAL_NAME_SEPARATOR)
    assertEquals('.', ClassLoaderConstants.EXTERNAL_NAME_SEPARATOR)
}
```

- [ ] **Step 4: 컴파일 + 테스트**

```bash
./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.ClassLoaderConstantsTest" 2>&1 | tail -10
```

Expected: 7 PASS (3 기존 + 4 신규).

- [ ] **Step 5: Commit + push**

```bash
git add server/layoutlib-worker/build.gradle.kts \
        server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt
git commit -m "feat(w3d3-α): ASM 9.7 의존 + ClassLoaderConstants 확장 ..."
git push origin main
```

---

## Task α-2: `RewriteRules` + `AndroidClassRewriter`

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RewriteRules.kt`
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AndroidClassRewriter.kt`
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RewriteRulesTest.kt`
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AndroidClassRewriterTest.kt`

- [ ] **Step 1: 5개 단위 테스트 (TDD red — 둘 다)**

```kotlin
// RewriteRulesTest.kt
package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class RewriteRulesTest {

    @Test
    fun `NAME_MAP 은 정확히 4 entries (Build family)`() {
        assertEquals(4, RewriteRules.NAME_MAP.size)
        assertTrue(RewriteRules.NAME_MAP.keys.all { it.startsWith("android/os/Build") })
    }

    @Test
    fun `NAME_MAP 의 모든 value 가 _Original_ prefix`() {
        assertTrue(RewriteRules.NAME_MAP.values.all { it.contains("_Original_Build") })
    }

    @Test
    fun `REMAPPER 가 매핑된 이름은 변환`() {
        assertEquals("android/os/_Original_Build", RewriteRules.REMAPPER.map("android/os/Build"))
        assertEquals("android/os/_Original_Build\$VERSION", RewriteRules.REMAPPER.map("android/os/Build\$VERSION"))
    }

    @Test
    fun `REMAPPER 가 매핑되지 않은 이름은 그대로`() {
        assertEquals("java/lang/String", RewriteRules.REMAPPER.map("java/lang/String"))
        assertEquals("android/view/SurfaceView", RewriteRules.REMAPPER.map("android/view/SurfaceView"))
    }

    @Test
    fun `REMAPPER 가 SurfaceView 등 21 의도된 dual-publish 클래스를 변경 안 함`() {
        // round 2 fix A1 — empirical NAME_MAP scope verification
        val nonRewriteCases = listOf(
            "android/view/SurfaceView",
            "android/webkit/WebView",
            "android/os/ServiceManager",
            "android/view/WindowManagerImpl",
            "android/view/textservice/TextServicesManager",
        )
        for (cls in nonRewriteCases)
        {
            assertEquals(cls, RewriteRules.REMAPPER.map(cls), "should NOT rewrite: $cls")
        }
    }
}
```

```kotlin
// AndroidClassRewriterTest.kt
package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

class AndroidClassRewriterTest {

    /**
     * Build.VERSION.SDK_INT 를 참조하는 합성 클래스 bytecode 생성.
     * 단순한 `class Foo { static int x = android.os.Build.VERSION.SDK_INT; }`.
     */
    private fun makeFooReferencingBuild(): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Foo", null, "java/lang/Object", null)
        val fv = cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "x", "I", null, null)
        fv.visitEnd()
        // <clinit>: x = android.os.Build$VERSION.SDK_INT
        val mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
        mv.visitCode()
        mv.visitFieldInsn(Opcodes.GETSTATIC, "android/os/Build\$VERSION", "SDK_INT", "I")
        mv.visitFieldInsn(Opcodes.PUTSTATIC, "Foo", "x", "I")
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    @Test
    fun `Build VERSION 참조가 _Original_Build VERSION 으로 rewrite`() {
        val original = makeFooReferencingBuild()
        val rewritten = AndroidClassRewriter.rewrite(original)
        assertNotEquals(original.toList(), rewritten.toList())
        // bytes 안에 `_Original_Build` 문자열이 등장하는지 확인 (constant pool)
        val asString = String(rewritten, Charsets.ISO_8859_1)
        assertTrue(asString.contains("_Original_Build\$VERSION"))
    }

    @Test
    fun `Build 무관 클래스는 변경 없음`() {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Bar", null, "java/lang/Object", null)
        cw.visitEnd()
        val original = cw.toByteArray()
        val rewritten = AndroidClassRewriter.rewrite(original)
        // type rename 만 하므로 무변경 (단, ClassWriter 가 frame 재계산하면 byte 차이 가능)
        // 실용적: rewritten 도 valid bytecode 이면 OK + Build 문자열 없음.
        val asString = String(rewritten, Charsets.ISO_8859_1)
        assertTrue(!asString.contains("_Original_"))
    }

    @Test
    fun `field type signature 도 rewrite`() {
        // 합성: class Baz { Build$VERSION instance; }
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Baz", null, "java/lang/Object", null)
        cw.visitField(Opcodes.ACC_PUBLIC, "instance", "Landroid/os/Build\$VERSION;", null, null).visitEnd()
        cw.visitEnd()
        val original = cw.toByteArray()
        val rewritten = AndroidClassRewriter.rewrite(original)
        val asString = String(rewritten, Charsets.ISO_8859_1)
        assertTrue(asString.contains("_Original_Build\$VERSION"))
    }
}
```

- [ ] **Step 2: 컴파일 FAIL 확인**

`./server/gradlew -p server :layoutlib-worker:test --tests "*RewriteRulesTest" --tests "*AndroidClassRewriterTest" 2>&1 | tail -10`

- [ ] **Step 3: production 작성** (spec §4.1, §4.2 코드 그대로)

`RewriteRules.kt` + `AndroidClassRewriter.kt` 작성.

- [ ] **Step 4: 테스트 PASS**

Expected: 5 + 3 = 8 PASS.

- [ ] **Step 5: Commit + push**

---

## Task α-3: `AarExtractor` 의 jar-rewrite 통합

**Files:**
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt`
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorRewriteTest.kt`

- [ ] **Step 1: 신규 단위 테스트 1개 (TDD red)**

```kotlin
package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class AarExtractorRewriteTest {

    /**
     * AAR (with classes.jar containing a Foo.class that references android.os.Build$VERSION)
     * → extract → cached classes.jar 안의 Foo.class 가 rewrite 되어 _Original_Build$VERSION 참조하는지 검증.
     */
    @Test
    fun `AAR classes_jar 의 .class 들이 rewrite 됨`(@TempDir root: Path) {
        // 합성 .class
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Foo", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
        mv.visitCode()
        mv.visitFieldInsn(Opcodes.GETSTATIC, "android/os/Build\$VERSION", "SDK_INT", "I")
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        val fooBytes = cw.toByteArray()

        // 합성 classes.jar 안에 Foo.class 1개
        val classesJar = root.resolve("classes-source.jar")
        ZipOutputStream(Files.newOutputStream(classesJar)).use { zos ->
            zos.putNextEntry(ZipEntry("Foo${ClassLoaderConstants.CLASS_FILE_SUFFIX}"))
            zos.write(fooBytes)
            zos.closeEntry()
        }
        val classesJarBytes = Files.readAllBytes(classesJar)

        // 합성 AAR (ZIP) — root entry 가 classes.jar
        val aar = root.resolve("test-lib${ClassLoaderConstants.AAR_EXTENSION}")
        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
            zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
            zos.write(classesJarBytes)
            zos.closeEntry()
        }

        val cacheRoot = root.resolve("cache")
        val extracted = AarExtractor.extract(aar, cacheRoot)
        assertNotNull(extracted)

        // cached classes.jar 안의 Foo.class 검사
        ZipFile(extracted!!.toFile()).use { zip ->
            val fooEntry = zip.getEntry("Foo${ClassLoaderConstants.CLASS_FILE_SUFFIX}")
            assertNotNull(fooEntry)
            val rewrittenBytes = zip.getInputStream(fooEntry).readBytes()
            val asString = String(rewrittenBytes, Charsets.ISO_8859_1)
            assertTrue(asString.contains("_Original_Build\$VERSION"), "rewrite 결과에 _Original_Build\$VERSION 포함 필요")
        }
    }
}
```

- [ ] **Step 2: AarExtractor 수정** — spec §4.3 의 `rewriteClassesJar` 추가

기존 `extract` 함수 안의 `zip.getInputStream(entry).use { input -> tmpJar.outputStream().use { ... copyTo } }` 부분을 `rewriteClassesJar(input, tmpJar)` 로 교체. 그리고 cacheRoot 에 `REWRITE_VERSION` 디렉토리 layer 추가:

```kotlin
val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR)
    .resolve(REWRITE_VERSION)        // ← round 2 D1: stale cache 회피
    .resolve(key)
```

`rewriteClassesJar` 메서드 추가 (spec §4.3 그대로).

- [ ] **Step 3: 기존 AarExtractorTest (5 tests) + 신규 1 test 모두 PASS 확인**

```bash
./server/gradlew -p server :layoutlib-worker:test --tests "*AarExtractor*" 2>&1 | tail -10
```

기존 테스트들의 expected fixture (e.g., `lib.jar` 가 빈 ZIP only) 가 rewrite 후에도 valid 한지 검증 — `rewriteClassesJar` 가 valid ZIP 입력에 대해 valid ZIP 을 출력하므로 PASS 예상.

- [ ] **Step 4: Commit + push**

---

## Task α-4: `RJarTypeMapping` + `RJarSymbolSeeder`

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarTypeMapping.kt`
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt`
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RJarTypeMappingTest.kt`
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeederTest.kt`

- [ ] **Step 1: 단위 테스트 (TDD red — 둘 다)**

```kotlin
// RJarTypeMappingTest.kt — 3 tests
@Test fun `attr style layout 매핑`() { ... }
@Test fun `styleable 도 매핑`() { ... }
@Test fun `알 수 없는 simpleName 은 null`() { ... }
```

```kotlin
// RJarSymbolSeederTest.kt — 5 tests
@Test fun `합성 R jar 에서 R$attr 의 모든 static int 필드 register`(...)
@Test fun `R$styleable 은 전체 skip — A2 fix`(...)
@Test fun `int[] 필드 (R$styleable) skip 도 보장`(...)
@Test fun `매핑 안 된 R$type 은 skip`(...)
@Test fun `parseRClassName 의 패키지 + type 추출 정확`(...)
```

테스트는 `org.junit.jupiter.api.io.TempDir` 로 합성 R.jar 생성 — `R.jar` 안에 합성 `com/example/R$attr.class`, `com/example/R$styleable.class` 등을 ASM 또는 javac 로 생성.

- [ ] **Step 2: production 작성** — spec §4.4, §4.5 그대로.

- [ ] **Step 3: 테스트 PASS**

Expected: 3 + 5 = 8 PASS.

- [ ] **Step 4: Commit + push**

---

## Task α-5: `MinimalLayoutlibCallback` 통합 + `LayoutlibRenderer` wire + 통합 테스트 enable

본 task 가 가장 큰 atomic 변경 — callback constructor 시그니처 변경 + 12 callsites + LayoutlibRenderer 의 seedRJarSymbols method + integration test enable + activity_basic_minimal.xml 작성. 단일 commit.

**Files:**
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`
- Modify: 12 callsites (8 + 2 + 1 + 1)
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` (`@Disabled` 제거)
- Create: `fixture/sample-app/app/src/main/res/layout/activity_basic_minimal.xml`

- [ ] **Step 1: callback 시그니처 변경**

`MinimalLayoutlibCallback(viewClassLoaderProvider, initializer)` — initializer 가 `(register: (ResourceReference, Int) -> Unit) -> Unit`. init 에서 try-catch wrapping.

`FIRST_ID = 0x7F80_0000` 으로 변경.

`advanceNextIdAbove(seeded: Int)` private method 추가 (CAS-based).

- [ ] **Step 2: 12 callsites 일괄 갱신**

```bash
grep -rn "MinimalLayoutlibCallback {" server/ --include="*.kt"
```

각 site 에서 `MinimalLayoutlibCallback({ cl })` → `MinimalLayoutlibCallback({ cl }, { /* no-op */ })`.

- [ ] **Step 3: LayoutlibRenderer 의 `seedRJarSymbols` method + ::method reference wire**

spec §4.7 그대로. `MinimalLayoutlibCallback({ ensureSampleAppClassLoader() }, ::seedRJarSymbols)`.

- [ ] **Step 4: activity_basic_minimal.xml 작성**

spec §4.8 의 markup 그대로 (Material 회피 layout).

- [ ] **Step 5: LayoutlibRendererIntegrationTest 수정**

@Disabled 제거. test body 에 1차/fallback 분기 — `activity_basic.xml` 시도 → Material* exception 시 `activity_basic_minimal.xml` retry. JUnit Assumptions / try-catch 패턴.

```kotlin
@Test
fun `tier3-basic — activity_basic renders SUCCESS with non-empty PNG`() {
    val renderer = ...
    val (layoutName, bytes) = renderWithMaterialFallback(renderer, "activity_basic.xml", "activity_basic_minimal.xml")
    assertEquals(Result.Status.SUCCESS, renderer.lastSessionResult?.status, "render status SUCCESS 여야 함 (layout=$layoutName)")
    assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES)
    // PNG magic check
}

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
        if (t.stackTraceToString().contains("Material") ||
            t.stackTraceToString().contains("ThemeEnforcement"))
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

- [ ] **Step 6: 전체 build + test**

```bash
./server/gradlew -p server build 2>&1 | tail -10
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 142 → ~165 unit (Tasks α-1 ~ α-4 의 신규 +18) PASS, integration 11 → 12 PASS + 1 SKIP (tier3-glyph).

- [ ] **Step 7: 결과 분기**

  - **(A) PASS — primary `activity_basic.xml`**: T1 gate 충족 + Material theme 가 실제로 throw 안 함 (Codex B2 hypothesis 반박). 본 task close.
  - **(B) PASS — fallback `activity_basic_minimal.xml` 로 retry**: T1 gate 충족 (contingency 동작). commit message 에 "Material theme enforcement contingency 발동" 명시.
  - **(C) FAIL — 둘 다**: HARD STOP. evidence 캡처 후 BLOCKED 보고. 신규 carry.

- [ ] **Step 8: 단일 commit (모든 변경 atomic)**

---

## Task α-6: work_log + handoff + plan/08 §7.7.5

**Files:**
- Create: `docs/work_log/2026-04-29_w3d3-l3-classloader/alpha-session-log.md`
- Modify: `docs/work_log/2026-04-29_w3d3-l3-classloader/handoff.md` — α 완료 또는 BLOCKED 상태로 갱신.
- Modify: `docs/plan/08-integration-reconciliation.md` — §7.7.5 W3D3-α 결과 추가.

- [ ] Step 1-3: 일반 work_log 작성 패턴.
- [ ] Step 4: Commit + push.

---

## Self-Review

**Spec coverage** (round 2):
- §0 round 1 → round 2 정정 → Tasks α-1 (CLASS_FILE_SUFFIX/INNER/external constants) + α-2 (NAME_MAP 4 entries) + α-4 (R$styleable skip) + α-5 (FIRST_ID 시프트, lambda → method).
- §1 acceptance gate → α-5 의 fallback 분기 포함.
- §2-§4 컴포넌트 → α-1 ~ α-4 1:1.
- §5 위험 → α-5 Step 7 의 결과 분기.
- §6 페어 리뷰 Q1-Q3 → spec round 2 본문 정정 + 본 plan tests 가 검증.
- §9.1 contingency → α-5 의 activity_basic_minimal.xml 선제 작성 + fallback retry.

**No placeholders**: 모든 step 에 exact code OR clear instruction.

**Type consistency**: `MinimalLayoutlibCallback(provider, initializer)` 시그니처 일관, `RJarSymbolSeeder.seed(rJarPath, loader, register)` 일관.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-29-w3d3-alpha-bytecode-rewriting.md`.**

**Pre-execution gating**: 본 plan 에 대한 round 2 페어 리뷰 (Codex+Claude 1:1, planning phase 마지막 게이트) 가 먼저 수행됨 — verdict GO 또는 GO_WITH_FOLLOWUPS 일 때만 구현 phase 진입.

**Execution choice**: Subagent-Driven (CLAUDE.md "Implementation phase = Claude-only").
