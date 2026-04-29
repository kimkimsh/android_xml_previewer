# W3D3 L3 ClassLoader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** sample-app 의 `activity_basic.xml` (ConstraintLayout / MaterialButton 등 custom view) 가 layoutlib host JVM 안에서 SUCCESS 로 렌더되도록, AAR `classes.jar` 를 `URLClassLoader` 로 적재해 `MinimalLayoutlibCallback.loadView` 를 통해 reflection-instantiate 한다.

**Architecture:**
- AAR/JAR resolution = sample-app `assembleDebug` 가 emit 한 manifest 파일 (`<sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt`).
- Worker 가 manifest 의 `.aar` 를 풀어 (`<sampleAppModuleRoot>/app/build/axp/aar-classes/<sha1>/<artifact>.jar`) JAR + sample-app `R.jar` 와 합쳐 `URLClassLoader(parent = layoutlib isolatedCL)` 구성.
- `MinimalLayoutlibCallback` 의 `loadView` / `findClass` 가 lazy provider 로 위 CL 사용. `hasAndroidXAppCompat = true` 로 BridgeInflater 의 AppCompat 자동 치환 활성. `InvocationTargetException` 은 cause 로 unwrap.

**Tech Stack:** Kotlin 1.9 / JDK 17 / Gradle 8 / JUnit Jupiter 5 / `java.net.URLClassLoader` / `java.util.zip.ZipFile` / `java.security.MessageDigest`. layoutlib 14.0.11 (이미 wired). AGP 8.x in `fixture/sample-app`.

**Spec**: [`docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md`](../specs/2026-04-29-w3d3-l3-classloader-design.md). 본 플랜 모든 결정은 spec 의 round 2 본문 + §9 contingency 에 일치.

**Round 1 페어-리뷰** (spec): `docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-{codex,claude}.md`. **Round 2 페어-리뷰** (plan v1): `plan-pair-review-round2-{codex,claude}.md`. 본 v2 는 round 2 의 모든 컨버전트 fix 를 반영:

- **B1** (양측 수렴): Task 7+8 merge → 단일 atomic Task 7 (constructor + 모든 caller + 1 commit).
- **B2** (Codex valid): Task 6 `findClass` 테스트의 `StringBuilder.classLoader == parent` 가정 잘못 (StringBuilder bootstrap-loaded → null). 사용자 정의 추적용 ClassLoader 로 교체.
- **B3** (Codex valid): Task 4 의 "R.jar 누락" 테스트가 사실은 empty manifest 를 트리거 — 정정해 valid manifest + R.jar 만 누락하도록.
- **B4** (Codex valid): `RendererArgs` data class 도입해 SharedRendererBinding 의 Pair<Path,Path> 를 3-path 로 확장.
- **B5** (Claude empirical): AGP 8.x `tasks.named("assembleDebug")` 가 top-level 에서 `UnknownTaskException` — `afterEvaluate` 필수 (round 1 Q1 convergence 정정).
- **B6** (Codex valid): Task 6 가 `MinimalLayoutlibCallback()` 의 모든 caller (특히 `SessionParamsFactoryTest`) 를 enumerate.
- **B7** (양측 수렴): Task 10 branch (C) 를 hard-stop + evidence capture 로 명시.
- **B8** (Codex valid): Task 3 에 corrupted AAR (IOException) 테스트 추가.
- **B9** (Codex valid): Task 6 의 `MinimalLayoutlibCallback` 신규 KDoc 가 기존 W2D7 KDoc 을 보존하도록 명시.
- **B10** (Codex valid): Task 8 의 CLI 변경에 `--sample-app-root` 단위 테스트 + USAGE_LINE 갱신 추가.
- **B11** (양측 수렴): T1 gate 의 `assumeTrue(found != null, ...)` → `requireNotNull(...)` 로 — fixture 누락은 정직한 실패가 옳음.
- **테스트 카운트**: 신규 unit = 3+5+5+3+2+5 = **23** (Codex 발견 +1, T6 와 T3 추가 케이스 포함). 118 → **141 unit**. 기존 integration 11+1 SKIP → **12 PASS + 1 SKIP** (tier3-glyph 만 SKIP).

---

## Task 1: `ClassLoaderConstants` (B4 magic strings 제거 기반 작업)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt`

- [ ] **Step 1: 새 디렉토리 + Constants 파일 작성**

```kotlin
// ClassLoaderConstants.kt
package dev.axp.layoutlib.worker.classloader

internal object ClassLoaderConstants {
    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 sampleAppModuleRoot-relative 경로. */
    const val MANIFEST_RELATIVE_PATH = "app/build/axp/runtime-classpath.txt"

    /** AarExtractor cacheRoot (= sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)) 의 base. */
    const val AAR_CACHE_BASE_RELATIVE_PATH = "app/build/axp"

    /** AarExtractor 의 stable 캐시 서브디렉토리 (cacheRoot 기준 상대). */
    const val AAR_CACHE_RELATIVE_DIR = "aar-classes"

    /** AAR ZIP 안에서 JVM 바이트코드 JAR 의 표준 entry 이름. */
    const val AAR_CLASSES_JAR_ENTRY = "classes.jar"

    /** AAR file 확장자 (manifest 검증 + 추출 분기). */
    const val AAR_EXTENSION = ".aar"

    /** JAR file 확장자 (manifest 검증). */
    const val JAR_EXTENSION = ".jar"

    /** AarExtractor 의 추출 결과 파일 suffix (artifactName 뒤에 붙음). */
    const val EXTRACTED_JAR_SUFFIX = ".jar"

    /** AarExtractor 의 atomic write 용 임시 파일 suffix. */
    const val TEMP_JAR_SUFFIX = ".jar.tmp"

    /** AarExtractor 의 캐시 키용 path digest 알고리즘. */
    const val SHA1_DIGEST_NAME = "SHA-1"

    /**
     * AGP 8.x 가 emit 하는 통합 R.jar 경로 (compile_and_runtime_not_namespaced_r_class_jar variant).
     * 본 경로는 AGP minor 버전 변경 시 깨질 수 있으나 8.x 안정 — 변경 시 본 상수만 갱신.
     */
    const val R_JAR_RELATIVE_PATH =
        "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
}
```

- [ ] **Step 2: 단일 sanity test 작성 — 상수 무결성**

```kotlin
// ClassLoaderConstantsTest.kt
package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassLoaderConstantsTest {

    @Test
    fun `manifest path 는 module-relative 이고 trailing 없음`() {
        assertEquals("app/build/axp/runtime-classpath.txt", ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        assertTrue(!ClassLoaderConstants.MANIFEST_RELATIVE_PATH.startsWith("/"))
    }

    @Test
    fun `aar 와 jar 확장자가 dot 으로 시작`() {
        assertTrue(ClassLoaderConstants.AAR_EXTENSION.startsWith("."))
        assertTrue(ClassLoaderConstants.JAR_EXTENSION.startsWith("."))
        assertTrue(ClassLoaderConstants.TEMP_JAR_SUFFIX.startsWith("."))
    }

    @Test
    fun `R_JAR 경로가 module-relative + 정확한 AGP 8 layout`() {
        val expected = "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
        assertEquals(expected, ClassLoaderConstants.R_JAR_RELATIVE_PATH)
    }
}
```

- [ ] **Step 3: 컴파일 + 테스트 실행**

Run: `./server/gradlew -p server :layoutlib-worker:compileKotlin :layoutlib-worker:compileTestKotlin :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.ClassLoaderConstantsTest"`
Expected: BUILD SUCCESSFUL, 3 tests PASS.

- [ ] **Step 4: Commit (per CLAUDE.md commit-and-push standing rule)**

```bash
git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt
git commit -m "feat(w3d3): ClassLoaderConstants — manifest/AAR cache/R.jar 경로 + 확장자 상수화 (...heredoc with Co-Authored-By...)"
git push origin main
```

---

## Task 2: `SampleAppClasspathManifest`

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifest.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifestTest.kt`

- [ ] **Step 1: 5개 단위 테스트 작성 (TDD red)**

```kotlin
package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class SampleAppClasspathManifestTest {

    @Test
    fun `누락 파일 — IllegalArgumentException 명확 메시지`(@TempDir root: Path) {
        val ex = assertThrows<IllegalArgumentException> {
            SampleAppClasspathManifest.read(root)
        }
        assertEquals(true, ex.message!!.contains("manifest 누락"))
        assertEquals(true, ex.message!!.contains("./gradlew :app:assembleDebug"))
    }

    @Test
    fun `빈 파일 — IllegalStateException`(@TempDir root: Path) {
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.createDirectories(mf.parent)
        Files.writeString(mf, "")
        assertThrows<IllegalStateException> { SampleAppClasspathManifest.read(root) }
    }

    @Test
    fun `공백 라인 포함 — IllegalArgumentException with line index`(@TempDir root: Path) {
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.createDirectories(mf.parent)
        val realJar = Files.createFile(root.resolve("real.jar"))
        Files.writeString(mf, "${realJar.toAbsolutePath()}\n   \n")
        val ex = assertThrows<IllegalArgumentException> { SampleAppClasspathManifest.read(root) }
        assertEquals(true, ex.message!!.contains("line 2"))
    }

    @Test
    fun `비-절대경로 — IllegalArgumentException`(@TempDir root: Path) {
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.createDirectories(mf.parent)
        Files.writeString(mf, "relative/path.aar")
        val ex = assertThrows<IllegalArgumentException> { SampleAppClasspathManifest.read(root) }
        assertEquals(true, ex.message!!.contains("비-절대경로"))
    }

    @Test
    fun `정상 — aar 와 jar 혼합, 순서 유지`(@TempDir root: Path) {
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.createDirectories(mf.parent)
        val a = Files.createFile(root.resolve("a.aar"))
        val b = Files.createFile(root.resolve("b.jar"))
        Files.writeString(mf, "${a.toAbsolutePath()}\n${b.toAbsolutePath()}")
        val result = SampleAppClasspathManifest.read(root)
        assertEquals(listOf(a.toAbsolutePath(), b.toAbsolutePath()), result.map { it.toAbsolutePath() })
    }
}
```

- [ ] **Step 2: 테스트 실행 → FAIL (object 미존재)**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.SampleAppClasspathManifestTest"`
Expected: 5 FAIL — `SampleAppClasspathManifest` unresolved.

- [ ] **Step 3: 구현 작성**

```kotlin
package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.JAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
import java.nio.file.Files
import java.nio.file.Path

/**
 * <sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt 파일을 읽어
 * resolved runtime classpath 의 AAR/JAR 절대경로 리스트를 반환.
 *
 * 형식: 라인당 하나의 절대 경로, '\n' 구분, trailing newline 없음, distinct + sorted.
 * 누락/비/공백 라인/비-절대/비-aar-jar 확장자/존재안하는 파일 → 모두 즉시 throw.
 */
object SampleAppClasspathManifest {

    fun read(sampleAppModuleRoot: Path): List<Path> {
        val mf = sampleAppModuleRoot.resolve(MANIFEST_RELATIVE_PATH)
        require(Files.isRegularFile(mf)) {
            "axp classpath manifest 누락: $mf — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 를 먼저 실행하세요"
        }
        val raw = Files.readString(mf)
        if (raw.isBlank()) {
            error("axp classpath manifest 가 비어있음: $mf")
        }
        return raw.split('\n').mapIndexed { idx, line ->
            require(line.isNotBlank()) { "manifest line ${idx + 1} 이 공백" }
            val p = Path.of(line)
            require(p.isAbsolute) { "manifest line ${idx + 1} 이 비-절대경로: '$line'" }
            require(line.endsWith(AAR_EXTENSION) || line.endsWith(JAR_EXTENSION)) {
                "manifest line ${idx + 1} 의 확장자가 ${AAR_EXTENSION}/${JAR_EXTENSION} 가 아님: '$line'"
            }
            require(Files.isRegularFile(p)) { "manifest line ${idx + 1} 의 파일이 없음: $p" }
            p
        }
    }
}
```

- [ ] **Step 4: 테스트 PASS 확인**

Run: 동일 명령. Expected: 5 PASS.

- [ ] **Step 5: Commit**

`git add` Manifest 두 파일 + commit.

---

## Task 3: `AarExtractor`

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt`

- [ ] **Step 1: 단위 테스트 4개 작성 (TDD red)**

```kotlin
package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AarExtractorTest {

    private fun makeAar(parent: Path, name: String, withClassesJar: Boolean): Path {
        val aar = parent.resolve("$name${ClassLoaderConstants.AAR_EXTENSION}")
        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("<?xml version='1.0'?><manifest/>".toByteArray())
            zos.closeEntry()
            if (withClassesJar) {
                zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
                // minimal valid empty zip bytes (PK\05\06 EOCD only)
                zos.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
                zos.closeEntry()
            }
        }
        return aar
    }

    @Test
    fun `정상 추출 — classes_jar 가 cache 에 atomic 으로 생성`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val aar = makeAar(root, "lib", withClassesJar = true)
        val extracted = AarExtractor.extract(aar, cacheRoot)
        assertNotNull(extracted)
        assertTrue(Files.isRegularFile(extracted))
        assertTrue(extracted.fileName.toString().endsWith(ClassLoaderConstants.EXTRACTED_JAR_SUFFIX))
        // tmp 파일 청소 확인
        val tmpName = "lib${ClassLoaderConstants.TEMP_JAR_SUFFIX}"
        assertTrue(Files.list(extracted.parent).noneMatch { it.fileName.toString() == tmpName })
    }

    @Test
    fun `cache hit — 두 번째 호출이 동일 path, 재추출 없음`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val aar = makeAar(root, "lib", withClassesJar = true)
        val first = AarExtractor.extract(aar, cacheRoot)!!
        val firstMtime = Files.getLastModifiedTime(first).toMillis()
        Thread.sleep(15) // ensure mtime tick
        val second = AarExtractor.extract(aar, cacheRoot)!!
        assertEquals(first, second)
        assertEquals(firstMtime, Files.getLastModifiedTime(second).toMillis())
    }

    @Test
    fun `classes_jar 없는 AAR — null 반환`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val aar = makeAar(root, "resonly", withClassesJar = false)
        assertNull(AarExtractor.extract(aar, cacheRoot))
    }

    @Test
    fun `존재하지 않는 AAR — IllegalArgumentException`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val missing = root.resolve("missing.aar")
        assertThrows<IllegalArgumentException> { AarExtractor.extract(missing, cacheRoot) }
    }

    @Test
    fun `손상된 ZIP — IOException`(@TempDir root: Path) {
        val cacheRoot = root.resolve("cache")
        val corrupted = root.resolve("broken${ClassLoaderConstants.AAR_EXTENSION}")
        // valid ZIP magic 으로 시작하지만 EOCD 가 없는 truncated 파일
        Files.write(corrupted, byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0))
        assertThrows<java.util.zip.ZipException> { AarExtractor.extract(corrupted, cacheRoot) }
    }
}
```

- [ ] **Step 2: 테스트 실행 → FAIL**

- [ ] **Step 3: 구현**

```kotlin
package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTRACTED_JAR_SUFFIX
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.SHA1_DIGEST_NAME
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.TEMP_JAR_SUFFIX
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

/**
 * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출. mtime 기반 idempotent + atomic write.
 *
 * Cache 위치: `<cacheRoot>/aar-classes/<sha1(absPath)>/<artifactName>.jar`.
 * AAR 안에 classes.jar 없는 경우 (resource-only) → null.
 */
object AarExtractor {

    fun extract(aarPath: Path, cacheRoot: Path): Path? {
        require(aarPath.isRegularFile()) { "AAR 누락: $aarPath" }
        val key = sha1(aarPath.toAbsolutePath().toString())
        val artifactName = aarPath.fileName.toString().removeSuffix(AAR_EXTENSION)
        val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR).resolve(key)
        Files.createDirectories(outDir)
        val outJar = outDir.resolve(artifactName + EXTRACTED_JAR_SUFFIX)

        val aarMtime = Files.getLastModifiedTime(aarPath).toMillis()
        if (outJar.isRegularFile() &&
            Files.getLastModifiedTime(outJar).toMillis() >= aarMtime) {
            return outJar
        }

        val tmpJar = outDir.resolve(artifactName + TEMP_JAR_SUFFIX)
        ZipFile(aarPath.toFile()).use { zip ->
            val entry = zip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
            zip.getInputStream(entry).use { input ->
                tmpJar.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Files.move(tmpJar, outJar, ATOMIC_MOVE, REPLACE_EXISTING)
        return outJar
    }

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance(SHA1_DIGEST_NAME)
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 4: 테스트 PASS**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.AarExtractorTest"`
Expected: 4 PASS.

- [ ] **Step 5: Commit**

---

## Task 4: `SampleAppClassLoader`

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt`

- [ ] **Step 1: 단위 테스트 3개 (TempDir + minimal AAR/jar/R.jar fixture)**

```kotlin
package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SampleAppClassLoaderTest {

    private fun makeTinyAar(parent: Path, name: String): Path {
        val aar = parent.resolve("$name${ClassLoaderConstants.AAR_EXTENSION}")
        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
            zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
            zos.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            zos.closeEntry()
        }
        return aar
    }

    private fun makeTinyJar(parent: Path, name: String): Path {
        val jar = parent.resolve("$name${ClassLoaderConstants.JAR_EXTENSION}")
        ZipOutputStream(Files.newOutputStream(jar)).use { zos ->
            zos.putNextEntry(ZipEntry("dummy.txt"))
            zos.write("ok".toByteArray())
            zos.closeEntry()
        }
        return jar
    }

    private fun setupModuleRoot(@TempDir root: Path): Path {
        val mfDir = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH).parent
        Files.createDirectories(mfDir)
        val rJarDir = root.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH).parent
        Files.createDirectories(rJarDir)
        val rJar = root.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH)
        Files.copy(makeTinyJar(root, "rjar-source"), rJar)
        return root
    }

    @Test
    fun `정상 빌드 — parent chain + AAR classes jar + R_jar 모두 URL 에 포함`(@TempDir root: Path) {
        setupModuleRoot(root)
        val aar = makeTinyAar(root, "lib")
        val jar = makeTinyJar(root, "extra")
        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
        Files.writeString(mf, "${aar.toAbsolutePath()}\n${jar.toAbsolutePath()}")

        val parent = ClassLoader.getSystemClassLoader()
        val cl = SampleAppClassLoader.build(root, parent)
        assertSame(parent, cl.classLoader.parent)
        assertEquals(3, cl.urls.size)  // aar-extracted + jar + R.jar
    }

    @Test
    fun `R_jar 누락 — IllegalArgumentException 메시지에 R_jar 포함`(@TempDir root: Path) {
        // valid manifest 작성 (jar 1 개) — manifest 빈-체크를 통과하도록.
        val mfDir = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH).parent
        Files.createDirectories(mfDir)
        val realJar = makeTinyJar(root, "stub")
        Files.writeString(
            root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH),
            realJar.toAbsolutePath().toString(),
        )
        // R.jar 디렉토리는 만들지 않음 (require Files.isRegularFile 이 fire) — IllegalArgumentException.
        val parent = ClassLoader.getSystemClassLoader()
        val ex = assertThrows<IllegalArgumentException> { SampleAppClassLoader.build(root, parent) }
        kotlin.test.assertTrue(ex.message!!.contains("R.jar"), "메시지에 R.jar 포함 필요: ${ex.message}")
    }

    @Test
    fun `manifest 의 jar 항목은 그대로 URL 화 됨`(@TempDir root: Path) {
        setupModuleRoot(root)
        val jar = makeTinyJar(root, "passthru")
        Files.writeString(root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH), jar.toAbsolutePath().toString())
        val cl = SampleAppClassLoader.build(root, ClassLoader.getSystemClassLoader())
        assertTrue(cl.urls.any { it.toString().endsWith("passthru${ClassLoaderConstants.JAR_EXTENSION}") })
    }
}
```

- [ ] **Step 2: 테스트 실행 → FAIL**

- [ ] **Step 3: 구현 — spec §4.4 그대로**

```kotlin
package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_BASE_RELATIVE_PATH
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class SampleAppClassLoader private constructor(
    val classLoader: ClassLoader,
    val urls: List<URL>,
) {
    companion object {
        fun build(sampleAppModuleRoot: Path, parent: ClassLoader): SampleAppClassLoader {
            val manifest = SampleAppClasspathManifest.read(sampleAppModuleRoot)
            val cacheRoot = sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)
            val urls = mutableListOf<URL>()

            for (entry in manifest) {
                val asString = entry.toString()
                val jarPath = if (asString.endsWith(AAR_EXTENSION)) {
                    AarExtractor.extract(entry, cacheRoot) ?: continue
                } else {
                    entry
                }
                urls += jarPath.toUri().toURL()
            }

            val rJar = sampleAppModuleRoot.resolve(R_JAR_RELATIVE_PATH)
            require(Files.isRegularFile(rJar)) {
                "sample-app R.jar 누락: $rJar — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 필요"
            }
            urls += rJar.toUri().toURL()

            val cl = URLClassLoader(urls.toTypedArray(), parent)
            return SampleAppClassLoader(cl, urls.toList())
        }
    }
}
```

- [ ] **Step 4: 테스트 PASS**

- [ ] **Step 5: Commit**

---

## Task 5: `FixtureDiscovery.locateModuleRoot` 추가

**Files:**
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt` (기존 + 확장)

- [ ] **Step 1: 새 케이스 2개 추가 (locateModuleRoot)**

```kotlin
@Test
fun `locateModuleRoot — override 디렉토리 그대로 반환`(@TempDir root: Path) {
    val moduleDir = Files.createDirectory(root.resolve("custom-sample-app"))
    val result = FixtureDiscovery.locateModuleRoot(moduleDir)
    assertEquals(moduleDir, result)
}

@Test
fun `locateModuleRoot — candidate root 에서 fixture sample-app 디렉토리 발견`(@TempDir root: Path) {
    val moduleDir = root.resolve("fixture").resolve("sample-app")
    Files.createDirectories(moduleDir)
    val result = FixtureDiscovery.locateInternalModuleRoot(
        override = null,
        userDir = root.toString(),
        candidateRoots = FixtureDiscovery.CANDIDATE_ROOTS,
    )
    assertEquals(moduleDir, result)
}
```

- [ ] **Step 2: FixtureDiscovery 수정 — `locateModuleRoot` + `locateInternalModuleRoot` + `FIXTURE_MODULE_SUBPATH` 상수 추가** (B6 round 2 페어 컨버전스: 기존 `locateInternal` (3-arg) 을 같이 보존하는 명시적 bridge 정의 — 기존 `FixtureDiscoveryTest` 5 tests 가 그대로 PASS):

```kotlin
const val FIXTURE_MODULE_SUBPATH = "fixture/sample-app"

fun locateModuleRoot(override: Path?): Path? = locateInternalModuleRoot(
    override = override,
    userDir = System.getProperty("user.dir"),
    candidateRoots = CANDIDATE_ROOTS,
)

internal fun locateInternalModuleRoot(
    override: Path?,
    userDir: String,
    candidateRoots: List<String>,
): Path? = locateInternalGeneric(override, userDir, candidateRoots, FIXTURE_MODULE_SUBPATH)

/**
 * 기존 3-arg locateInternal 을 보존 — FixtureDiscoveryTest 5 tests 의 호출부 유지.
 * subpath = FIXTURE_SUBPATH (= "fixture/sample-app/app/src/main/res/layout").
 */
internal fun locateInternal(
    override: Path?,
    userDir: String,
    candidateRoots: List<String>,
): Path? = locateInternalGeneric(override, userDir, candidateRoots, FIXTURE_SUBPATH)

internal fun locateInternalGeneric(
    override: Path?,
    userDir: String,
    candidateRoots: List<String>,
    subpath: String,
): Path? {
    if (override != null) {
        require(Files.isDirectory(override))
        {
            "fixture root 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
        }
        return override
    }
    val candidates: List<Path> = candidateRoots.flatMap { root ->
        if (root.isEmpty())
        {
            listOf(Paths.get(subpath), Paths.get(userDir, subpath))
        }
        else
        {
            listOf(Paths.get(root, subpath), Paths.get(userDir, root, subpath))
        }
    }
    return candidates.firstOrNull { Files.isDirectory(it) }
}
```

(CLAUDE.md "Brace Style — 단일 라인도 `{}` + opening brace own newline" 준수 — Codex round 2 valid 발견.)

- [ ] **Step 3: 전체 테스트 실행 — 기존 5 + 신규 2 모두 PASS**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.FixtureDiscoveryTest"`
Expected: 7 PASS.

- [ ] **Step 4: Commit**

---

## Task 6: `MinimalLayoutlibCallback` 변경 (loadView + findClass + hasAndroidXAppCompat)

**Files:**
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`
- Modify (small): `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt` — 기존 테스트 새 시그니처에 맞게 업데이트.
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackLoadViewTest.kt` (3 tests)

- [ ] **Step 1: 신규 테스트 5개 작성** (B2 fix: StringBuilder.classLoader is null bootstrap → tracking ClassLoader 로 교체. Codex valid: ITE unwrap + ctor mismatch 케이스 추가).

```kotlin
package dev.axp.layoutlib.worker.session

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MinimalLayoutlibCallbackLoadViewTest {

    /**
     * 호출 추적용 ClassLoader — 어떤 클래스가 어느 provider 로부터 요청됐는지 검증.
     * StringBuilder 같은 bootstrap 클래스를 직접 쓰면 cls.classLoader == null 이라 비교가 깨짐.
     */
    private class TrackingClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        val requested = mutableListOf<String>()
        override fun loadClass(name: String): Class<*> {
            requested += name
            return super.loadClass(name)
        }
    }

    private fun newCallback(cl: ClassLoader): MinimalLayoutlibCallback =
        MinimalLayoutlibCallback { cl }

    @Test
    fun `loadView — provider CL 로 위임 + 정상 instantiate`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        val v = cb.loadView("java.lang.StringBuilder", arrayOf(CharSequence::class.java), arrayOf<Any>("hi"))
        assertNotNull(v)
        assertEquals("hi", v.toString())
        assertTrue("java.lang.StringBuilder" in cl.requested, "provider CL 호출 기록: ${cl.requested}")
    }

    @Test
    fun `loadView — 미지 클래스 ClassNotFoundException pass-through`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        assertThrows<ClassNotFoundException> {
            cb.loadView("does.not.Exist", arrayOf(), arrayOf())
        }
    }

    @Test
    fun `loadView — InvocationTargetException 의 cause 가 unwrap 되어 throw`() {
        // 생성자에서 throw 하는 클래스를 일부러 호출 (ArrayList(int) 가 negative 면 IllegalArgumentException).
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        val ex = assertThrows<IllegalArgumentException> {
            cb.loadView("java.util.ArrayList", arrayOf(Int::class.javaPrimitiveType!!), arrayOf<Any>(-1))
        }
        // unwrap 됐으므로 InvocationTargetException 가 아닌 IllegalArgumentException 이 잡힘.
        assertTrue(ex !is InvocationTargetException)
    }

    @Test
    fun `findClass — provider CL 로 위임`() {
        val cl = TrackingClassLoader(ClassLoader.getSystemClassLoader())
        val cb = newCallback(cl)
        val cls = cb.findClass("java.lang.StringBuilder")
        assertEquals(java.lang.StringBuilder::class.java, cls)
        assertTrue("java.lang.StringBuilder" in cl.requested)
    }

    @Test
    fun `hasAndroidXAppCompat — true`() {
        val cb = newCallback(ClassLoader.getSystemClassLoader())
        assertTrue(cb.hasAndroidXAppCompat())
    }
}
```

- [ ] **Step 2: 기존 `MinimalLayoutlibCallback()` no-arg 모든 caller 업데이트** (Codex B6/A2):

```bash
grep -rn "MinimalLayoutlibCallback()" server/ --include="*.kt"
# expected sites:
#   server/layoutlib-worker/src/test/kotlin/.../MinimalLayoutlibCallbackTest.kt (8 sites)
#   server/layoutlib-worker/src/test/kotlin/.../session/SessionParamsFactoryTest.kt (lines 42, 65)
#   server/layoutlib-worker/src/main/kotlin/.../LayoutlibRenderer.kt (renderViaLayoutlib 안)
```

각 호출부를 `MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }` (테스트) 또는 `MinimalLayoutlibCallback { ensureSampleAppClassLoader() }` (production) 로 변경.

- [ ] **Step 3: MinimalLayoutlibCallback 수정** (CLAUDE.md "Never delete existing comments" — 기존 W2D7 class-level KDoc 보존, **추가 변경 부분에만 새 KDoc 덧붙임**. `loadView` 의 UnsupportedOperationException 분기만 reflection 으로 교체)

```kotlin
package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger

/**
 * W3D3-L3-CLASSLOADER (round 2 페어 리뷰 반영).
 *
 * 변경:
 *  - loadView: viewClassLoaderProvider 로부터 lazy 로 CL 받아 reflection-instantiate.
 *    InvocationTargetException 의 cause 를 unwrap (Q3, Codex 입장).
 *  - findClass: BridgeInflater.findCustomInflater 의 AppCompat 자동 치환 활성화 (F1).
 *  - hasAndroidXAppCompat: true (F1) — sample-app 의존 그래프 보유.
 *
 * lazy provider (Q4): activity_minimal 처럼 custom view 없는 fixture 는 호출 0 회 →
 * sample-app manifest 누락이 silent (W3D1 의 `tier3-values` 회귀 없음).
 */
class MinimalLayoutlibCallback(
    private val viewClassLoaderProvider: () -> ClassLoader,
) : LayoutlibCallback() {

    private val nextId = AtomicInteger(FIRST_ID)
    private val byRef = mutableMapOf<ResourceReference, Int>()
    private val byId = mutableMapOf<Int, ResourceReference>()

    @Synchronized
    override fun getOrGenerateResourceId(ref: ResourceReference): Int {
        byRef[ref]?.let { return it }
        val id = nextId.getAndIncrement()
        byRef[ref] = id
        byId[id] = ref
        return id
    }

    @Synchronized
    override fun resolveResourceId(id: Int): ResourceReference? = byId[id]

    override fun loadView(
        name: String,
        constructorSignature: Array<out Class<*>>?,
        constructorArgs: Array<out Any>?,
    ): Any {
        val cls = viewClassLoaderProvider().loadClass(name)
        val sig = constructorSignature ?: emptyArray()
        val args = constructorArgs ?: emptyArray()
        val ctor = cls.getDeclaredConstructor(*sig)
        ctor.isAccessible = true
        try {
            return ctor.newInstance(*args)
        } catch (ite: InvocationTargetException) {
            // Q3: cause unwrap — layoutlib 의 BridgeInflater 가 InflateException 으로 wrap 함.
            throw ite.cause ?: ite
        }
    }

    override fun findClass(name: String): Class<*> {
        return viewClassLoaderProvider().loadClass(name)
    }

    override fun hasAndroidXAppCompat(): Boolean = true

    override fun getParser(layoutResource: ResourceValue?): ILayoutPullParser? = null
    override fun getAdapterBinding(cookie: Any?, attributes: Map<String, String>): AdapterBinding? = null
    override fun getActionBarCallback(): ActionBarCallback = ActionBarCallback()
    override fun createXmlParser(): XmlPullParser = buildKxml()
    override fun createXmlParserForFile(fileName: String): XmlPullParser = buildKxml()
    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser = buildKxml()
    override fun getApplicationId(): String = APPLICATION_ID

    private fun buildKxml(): XmlPullParser = KXmlParser().also {
        it.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    }

    companion object {
        private const val FIRST_ID = 0x7F0A_0000
        private const val APPLICATION_ID = "axp.render"
    }
}
```

- [ ] **Step 4: 전체 callback 단위 테스트 PASS 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.session.MinimalLayoutlibCallback*"`
Expected: 기존 + 신규 모두 PASS.

- [ ] **Step 5: Commit**

---

## Task 7: LayoutlibRenderer + 모든 caller — atomic 변경 + 1 commit

(B1 페어-리뷰 컨버전스: round 2 plan-review 가 W3D2 LM-B1 prior art 를 인용하며 Task 7+8 분리는 LM-B1 의 안티패턴이라고 명시. 본 v2 에서 단일 atomic task 로 merge.)

**Files (모든 변경 한 commit):**
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` (생성자 + lazy provider)
- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` (`chooseRenderer` 의 새 인자 주입)
- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt` (helper)
- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt` (`SAMPLE_APP_ROOT_FLAG`, `VALUE_BEARING_FLAGS`, `USAGE_LINE`)
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt`
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt`
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt` (3 tests adjusted)
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt` (4 sites)
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt` (companion sharedRenderer)
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/RendererArgs.kt` (B4: data class)
- Modify: `server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt` (B10: `--sample-app-root` 단위 테스트 1 추가)

**Step 0: 변경 요약 (atomic compile + atomic commit)**:
1. RendererArgs data class 새로 만든다 (B4).
2. SharedRendererBinding 의 Pair<Path,Path> → RendererArgs 로 교체.
3. SharedRendererBindingTest 3 tests 의 Pair 사용 → RendererArgs 로 교체.
4. LayoutlibRenderer 생성자에 `sampleAppModuleRoot` 인자 추가.
5. SharedLayoutlibRenderer.getOrCreate / boundArgs 시그니처 확장.
6. Main.kt + CliArgs + CliConstants 에 `--sample-app-root` 추가 (USAGE_LINE 갱신 포함).
7. CliArgsTest 에 신규 test 1 추가.
8. SharedLayoutlibRendererIntegrationTest / LayoutlibRendererTier3MinimalTest 모든 호출부 갱신.
9. LayoutlibRenderer.renderViaLayoutlib 의 callback 생성을 lazy provider 로.
10. **반드시 한 commit** — `./server/gradlew -p server build` 가 최종 PASS 일 때만 commit.

**Step 1: RendererArgs data class 생성**

```kotlin
// server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/RendererArgs.kt
package dev.axp.layoutlib.worker

import java.nio.file.Path

/**
 * SharedLayoutlibRenderer / SharedRendererBinding 의 cache 키 + 인자 묶음.
 * W3D3-B4 (round 2 페어 리뷰): 기존 Pair<Path,Path> 가 3-path 로 확장 안 되어 도입.
 */
internal data class RendererArgs(
    val distDir: Path,
    val fixtureRoot: Path,
    val sampleAppModuleRoot: Path,
)
```

**Step 2: SharedRendererBinding 변경** (`Pair<Path,Path>` → `RendererArgs`)

기존 `verify(bound: Pair<Path,Path>?, requested: Pair<Path,Path>): Pair<Path,Path>` 형태였다면, 이제:

```kotlin
internal fun verify(bound: RendererArgs?, requested: RendererArgs): RendererArgs {
    if (bound != null) {
        require(bound == requested) { "SharedLayoutlibRenderer 가 다른 인자로 재바인딩됨: bound=$bound, requested=$requested" }
        return bound
    }
    return requested
}
```

**Step 3: SharedRendererBinding 의 3 tests 갱신**

`Pair(d1, f1)` → `RendererArgs(d1, f1, m1)` 로 교체. `differentFixture` mismatch 케이스도 sampleAppModuleRoot 까지 비교하도록 확장.

**Step 4: SharedLayoutlibRenderer 변경**

```kotlin
class SharedLayoutlibRenderer private constructor(...) {
    @Volatile private var boundArgs: RendererArgs? = null
    // ... existing impl ...

    companion object {
        fun getOrCreate(
            distDir: Path,
            fixtureRoot: Path,
            sampleAppModuleRoot: Path,
            fallback: PngRenderer?,
        ): SharedLayoutlibRenderer {
            val requested = RendererArgs(distDir, fixtureRoot, sampleAppModuleRoot)
            // existing instance 가 있으면 verify(bound, requested) 후 그대로 반환.
            // 없으면 LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot) 새 인스턴스 wrap.
            ...
        }
    }
}
```

**Step 5: SharedLayoutlibRendererIntegrationTest** — 4 호출부 모두 `sampleAppModuleRoot = FixtureDiscovery.locateModuleRoot(null)!!.toAbsolutePath().normalize()` 추가.

**Step 6: LayoutlibRenderer 변경**

```kotlin
class LayoutlibRenderer(
    private val distDir: Path,
    private val fallback: PngRenderer?,
    private val fixtureRoot: Path,
    private val sampleAppModuleRoot: Path,
) : PngRenderer {
    // ... 기존 모든 필드/메서드 그대로 — KDoc 보존 ...

    @Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null

    @Synchronized
    private fun ensureSampleAppClassLoader(): ClassLoader {
        sampleAppClassLoader?.let { return it.classLoader }
        val isolated = classLoader ?: error("Bridge 가 init 안 됨 (initBridge 가 먼저 실행되어야 함)")
        val built = SampleAppClassLoader.build(sampleAppModuleRoot, isolated)
        sampleAppClassLoader = built
        return built.classLoader
    }
    // ... renderViaLayoutlib 내부의 MinimalLayoutlibCallback() 라인을
    //     MinimalLayoutlibCallback { ensureSampleAppClassLoader() } 로 변경 ...
}
```

**Step 7: CLI**

`CliConstants.kt`:
```kotlin
const val SAMPLE_APP_ROOT_FLAG = "--sample-app-root"
val VALUE_BEARING_FLAGS: Set<String> = setOf(DIST_DIR_FLAG, FIXTURE_ROOT_FLAG, SAMPLE_APP_ROOT_FLAG)
const val USAGE_LINE = "axp [--stdio] [--dist-dir=<path>] [--fixture-root=<layout-dir>] [--sample-app-root=<sample-app-module-dir>]"
```

`Main.kt` `chooseRenderer`:
```kotlin
val sampleAppOverride: Path? = parsed.valueOf(CliConstants.SAMPLE_APP_ROOT_FLAG)?.let { Paths.get(it) }
val sampleAppModuleRoot: Path = FixtureDiscovery.locateModuleRoot(sampleAppOverride)
    ?: error("sample-app module root 탐지 실패 — --sample-app-root 명시 또는 fixture/sample-app 확인")
// ... LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot) ...
```

**Step 8: CliArgsTest 에 신규 test 1 추가**:
```kotlin
@Test
fun `--sample-app-root=value 파싱`() {
    val parsed = CliArgs.parse(arrayOf("--sample-app-root=/abs/sample-app"), valueBearing)
    assertEquals("/abs/sample-app", parsed.valueOf("--sample-app-root"))
}
```

**Step 9: LayoutlibRendererTier3MinimalTest companion sharedRenderer** — `getOrCreate(dist, fixture, FixtureDiscovery.locateModuleRoot(null)!!, fallback=null)` 형태로.

**Step 10: 전체 build + test**

Run:
```bash
./server/gradlew -p server build
./server/gradlew -p server test                                                # all unit
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration    # all integration
```

Expected: BUILD SUCCESSFUL. integration test 카운트 변화 없음 (`LayoutlibRendererIntegrationTest` 아직 `@Disabled`). unit count 는 +1 (`--sample-app-root` 테스트).

**Step 11: 단일 commit**

```bash
git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/RendererArgs.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt \
        server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt \
        server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt \
        server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt \
        server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt
git commit -m "feat(w3d3): LayoutlibRenderer.sampleAppModuleRoot + RendererArgs + CLI ..."
git push origin main
```

---

## Task 8 (was 9): Gradle `axpEmitClasspath` task + 첫 manifest 생성

(이전 Task 7 가 합쳐진 결과 번호가 시프트. 본 v2 의 Task 8 는 round 1 v1 의 Task 9.)

## Task 8 (was 9): Gradle `axpEmitClasspath` task + 첫 manifest 생성

**Files:**
- Modify: `fixture/sample-app/app/build.gradle.kts`

- [ ] **Step 1: Gradle 신규 task 추가** (B5: AGP 8.x `tasks.named("assembleDebug")` 가 top-level 에서 미존재 → `afterEvaluate` 필수. round 1 Q1 convergence 정정.)

`build.gradle.kts` 의 끝에 다음 추가:

```kotlin
val axpClasspathManifest = layout.buildDirectory.file("axp/runtime-classpath.txt")
val axpEmitClasspath = tasks.register("axpEmitClasspath") {
    val cpProvider = configurations.named("debugRuntimeClasspath")
    inputs.files(cpProvider)
    outputs.file(axpClasspathManifest)
    doLast {
        val cp = cpProvider.get()
        val artifacts = cp.resolvedConfiguration.resolvedArtifacts
            .map { it.file.absolutePath }
            .distinct()
            .sorted()
        val outFile = axpClasspathManifest.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(artifacts.joinToString("\n"))
    }
}
afterEvaluate {
    tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
}
```

- [ ] **Step 2: 한 번 수동 실행 — manifest 가 expected 위치에 생성되는지 확인**

```bash
cd fixture/sample-app && ./gradlew :app:assembleDebug 2>&1 | tail -10
ls -la app/build/axp/runtime-classpath.txt
head -5 app/build/axp/runtime-classpath.txt
wc -l app/build/axp/runtime-classpath.txt
```

Expected: 파일 존재, 첫 5 라인이 절대경로, 라인수 ≥ 30 (transitive deps).

- [ ] **Step 3: Commit**

(`app/build/` 는 .gitignore — 코드만 commit.)

---

## Task 9 (was 10): `LayoutlibRendererIntegrationTest` enable + 첫 실측 + B2 contingency 결정

**Files:**
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`

- [ ] **Step 1: `@Disabled` 제거 + spec §4.8 의 코드 적용** (B11: `assumeTrue` → `requireNotNull` for sampleAppModuleRoot — fixture 누락은 silent SKIP 이 아닌 정직한 FAIL)

`locateSampleAppModuleRoot()` 메서드:

```kotlin
private fun locateSampleAppModuleRoot(): Path {
    val found = FixtureDiscovery.locateModuleRoot(null)
    return requireNotNull(found) {
        "sample-app module root 없음 — fixture/sample-app 확인 + `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 실행"
    }.toAbsolutePath().normalize()
}
```

(`locateDistDir` / 기존 `locateFixtureRoot` 의 `assumeTrue` 패턴은 backward compat 위해 유지 — W3D2 시점부터 의도적 SKIP 패턴.)

- [ ] **Step 2: 통합 테스트 실행**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest" -PincludeTags=integration`
Expected: 1 test PASS or FAIL — 결과에 따라 분기.

- [ ] **Step 3: 결과 분기**

  **(A) PASS — B2 가설 무효, T1 gate 통과**:
  - 다음 Task 로 진행 (전체 게이트 + work_log).

  **(B) FAIL with `MaterialThemeOverlay`/`ThemeEnforcement`/`Material*` 관련 stack trace**:
  - B2 가설 confirmed → §9.1 contingency 발동.
  - `fixture/sample-app/app/src/main/res/layout/activity_basic_minimal.xml` 작성 (spec §9.1 의 마크업).
  - `LayoutlibRendererIntegrationTest` 의 layoutName 을 `"activity_basic_minimal.xml"` 로 변경.
  - 다시 실행 → PASS 기대.

  **(C) FAIL with 다른 원인** (e.g., R class missing, ClassNotFoundException 의 다른 cause):
  - **HARD STOP — 자율 분기 금지** (B7 round 2 페어 컨버전스).
  - 다음 commands 로 evidence 만 수집:
    ```bash
    ./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest" -PincludeTags=integration --info 2>&1 | tail -200 \
      > docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-evidence.md
    ```
  - branch-c-evidence.md 에 stack trace + first 50 라인 + git status 첨부.
  - **본 task 를 PASS 로 종결하지 않음**. handoff.md 에 "branch (C) hit — 신규 plan 필요" 기록 후 사용자에게 escalation. 신규 task 추가 / improvise 금지.

- [ ] **Step 4: 전체 게이트 실행**

```bash
./server/gradlew -p server test                                          # 전체 unit
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 전체 integration
./server/gradlew -p server build                                         # BUILD SUCCESSFUL
./server/mcp-server/build/install/mcp-server/bin/mcp-server --smoke      # "ok"
```

Expected: 모든 게이트 통과. Unit 118+ → 130+ (15 신규), integration 11 → 12 PASS, tier3-glyph 만 SKIPPED.

- [ ] **Step 5: Commit (Task 10 unit)**

---

## Task 10 (was 11): work_log + handoff 업데이트

**Files:**
- Create: `docs/work_log/2026-04-29_w3d3-l3-classloader/session-log.md`
- Create: `docs/work_log/2026-04-29_w3d3-l3-classloader/handoff.md`
- Modify: `docs/plan/08-integration-reconciliation.md` (W3D3 close section 추가)

- [ ] **Step 1: session-log 작성** — 변경 파일 목록, 테스트 카운트 변화, 발견된 landmine, B2 contingency 발동 여부, pair-review verdicts.

- [ ] **Step 2: handoff 작성** — 다음 세션의 W3D4 후보 (app/library resource value loading, 또는 tier3-glyph).

- [ ] **Step 3: `docs/plan/08-integration-reconciliation.md` §7.7.4 추가 (W3D3 close 기록)**.

- [ ] **Step 4: Commit**

---

## Self-Review (writing-plans 종결 체크리스트)

**Spec coverage** (v2 task 번호 기준):
- §0 페어 리뷰 결과 (round 1 + round 2) → Task 1-9 구현으로 모두 반영.
- §1.1 deliverables (manifest, AAR loader, callback override, integration enable) → Task 1-4, 6, 8, 9.
- §1.2 out-of-scope → 본 플랜 미터치 (W3D4 carry).
- §1.3 acceptance gate (T1, SUCCESS + PNG > 1000) → Task 9 Step 2.
- §2 컴포넌트 분해 → Task 별 1:1 매핑 (C0/C1/C2/C3/C4/C5/C6/C7/C7b/C8 → Task 5 / 8 / 2 / 3 / 4 / 1 / 6 / 7 / 7 / 9).
- §3 데이터 흐름 → Task 7 의 LayoutlibRenderer 수정으로 wire.
- §4 컴포넌트 스펙 → Task 1-7 의 코드 블록.
- §5 테스트 전략 → Task 1-4, 6 의 단위 (23 신규) + Task 9 의 통합 1.
- §6 위험 → Task 9 Step 3 의 분기 (R1/R3/R4).
- §7 페어 리뷰 Q1-Q5 (round 1) → spec round 2 + plan v2 에서 모두 처리, 코드 반영. **Q1 정정** (Q1 round 1 convergence ↔ B5 round 2 plan empirical 정정).
- §8 변경 로그 → Task 10 step 3 가 plan/08 에 추가.
- §9 contingency → Task 9 Step 3 (B).
- **Round 2 plan-review B1-B11**: 본 v2 의 §0 헤더에 1:1 매핑 — 모두 반영 완료.

**No placeholders**: 모든 Task 가 exact code + exact commands + exact expected output. "TODO" 없음.

**Type consistency**: `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 시그니처가 모든 task 에서 일관. `MinimalLayoutlibCallback { provider }` lambda 도.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md`. Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, two-stage review.
2. **Inline Execution** — batch with checkpoints.

**Pre-execution gating**: round 2 페어 리뷰 (Codex NO_GO + Claude GO_WITH_FOLLOWUPS) 의 모든 컨버전트 fix (B1-B11) 가 본 v2 에 반영. divergence on severity (NO_GO vs GO_WITH_FOLLOWUPS) — direction 컨버전. 본 v2 가 양측 모든 issue 를 메우므로 추가 round 3 페어 리뷰 없이 구현 phase 진입.

**Execution choice**: Subagent-Driven (CLAUDE.md "Implementation phase = Claude-only, no Codex" 정책 + W3D2 에서 검증된 패턴).
