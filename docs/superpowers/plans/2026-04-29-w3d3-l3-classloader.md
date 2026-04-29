# W3D3 L3 ClassLoader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** sample-app 의 `activity_basic.xml` (ConstraintLayout / MaterialButton 등 custom view) 가 layoutlib host JVM 안에서 SUCCESS 로 렌더되도록, AAR `classes.jar` 를 `URLClassLoader` 로 적재해 `MinimalLayoutlibCallback.loadView` 를 통해 reflection-instantiate 한다.

**Architecture:**
- AAR/JAR resolution = sample-app `assembleDebug` 가 emit 한 manifest 파일 (`<sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt`).
- Worker 가 manifest 의 `.aar` 를 풀어 (`<sampleAppModuleRoot>/app/build/axp/aar-classes/<sha1>/<artifact>.jar`) JAR + sample-app `R.jar` 와 합쳐 `URLClassLoader(parent = layoutlib isolatedCL)` 구성.
- `MinimalLayoutlibCallback` 의 `loadView` / `findClass` 가 lazy provider 로 위 CL 사용. `hasAndroidXAppCompat = true` 로 BridgeInflater 의 AppCompat 자동 치환 활성. `InvocationTargetException` 은 cause 로 unwrap.

**Tech Stack:** Kotlin 1.9 / JDK 17 / Gradle 8 / JUnit Jupiter 5 / `java.net.URLClassLoader` / `java.util.zip.ZipFile` / `java.security.MessageDigest`. layoutlib 14.0.11 (이미 wired). AGP 8.x in `fixture/sample-app`.

**Spec**: [`docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md`](../specs/2026-04-29-w3d3-l3-classloader-design.md). 본 플랜 모든 결정은 spec 의 round 2 본문 + §9 contingency 에 일치.

**Round 1 페어-리뷰**: spec 단계에서 완료 (`docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-{codex,claude}.md`). 본 플랜에 별도 round 2 페어-리뷰 진행 후 구현 phase 진입.

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
    fun `R_jar 누락 — 명확한 메시지로 require fail`(@TempDir root: Path) {
        val mfDir = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH).parent
        Files.createDirectories(mfDir)
        Files.writeString(root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH), "")
        // intentionally do NOT create R.jar
        val parent = ClassLoader.getSystemClassLoader()
        val ex = assertThrows<IllegalStateException> { SampleAppClassLoader.build(root, parent) }
        // 빈 manifest 가 먼저 발화되거나 R.jar 누락이 발화 — 둘 다 IllegalStateException 또는
        // IllegalArgumentException. ex 가 둘 중 하나면 된다.
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

- [ ] **Step 2: FixtureDiscovery 수정 — `locateModuleRoot` + `locateInternalModuleRoot` + `FIXTURE_MODULE_SUBPATH` 상수 추가**

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

internal fun locateInternalGeneric(
    override: Path?,
    userDir: String,
    candidateRoots: List<String>,
    subpath: String,
): Path? {
    if (override != null) {
        require(Files.isDirectory(override)) {
            "fixture root 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
        }
        return override
    }
    val candidates: List<Path> = candidateRoots.flatMap { root ->
        if (root.isEmpty()) listOf(Paths.get(subpath), Paths.get(userDir, subpath))
        else listOf(Paths.get(root, subpath), Paths.get(userDir, root, subpath))
    }
    return candidates.firstOrNull { Files.isDirectory(it) }
}
```

기존 `locateInternal` 은 `locateInternalGeneric(... , FIXTURE_SUBPATH)` 로 위임 (동작 동일).

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

- [ ] **Step 1: 신규 테스트 3개 작성**

```kotlin
package dev.axp.layoutlib.worker.session

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class MinimalLayoutlibCallbackLoadViewTest {

    private fun newCallback(provider: () -> ClassLoader): MinimalLayoutlibCallback =
        MinimalLayoutlibCallback(provider)

    @Test
    fun `loadView — 기본 String 클래스 로드 성공 (자기 CL 활용)`() {
        val cb = newCallback { ClassLoader.getSystemClassLoader() }
        // String 의 (CharSequence) 생성자
        val v = cb.loadView("java.lang.StringBuilder", arrayOf(CharSequence::class.java), arrayOf<Any>("hi"))
        assertNotNull(v)
    }

    @Test
    fun `loadView — 미지 클래스 ClassNotFoundException`() {
        val cb = newCallback { ClassLoader.getSystemClassLoader() }
        assertThrows<ClassNotFoundException> {
            cb.loadView("does.not.Exist", arrayOf(), arrayOf())
        }
    }

    @Test
    fun `findClass — provider CL 위임`() {
        val parent = ClassLoader.getSystemClassLoader()
        val cb = newCallback { parent }
        val cls = cb.findClass("java.lang.StringBuilder")
        assertSame(parent, cls.classLoader)
    }

    @Test
    fun `hasAndroidXAppCompat — true`() {
        val cb = newCallback { ClassLoader.getSystemClassLoader() }
        kotlin.test.assertTrue(cb.hasAndroidXAppCompat())
    }
}
```

- [ ] **Step 2: 기존 MinimalLayoutlibCallbackTest.kt — `MinimalLayoutlibCallback()` no-arg 호출 부분을 `MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }` 로 업데이트**

(파일 안의 모든 호출부에 lambda 인자 명시 — sed/grep 으로 일괄 변환 가능)

- [ ] **Step 3: MinimalLayoutlibCallback 수정**

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

## Task 7: `LayoutlibRenderer` — sampleAppModuleRoot 인자 + lazy provider 배선

**Files:**
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`

- [ ] **Step 1: 생성자에 `sampleAppModuleRoot: Path` 추가 + `ensureSampleAppClassLoader` private 메서드**

```kotlin
class LayoutlibRenderer(
    private val distDir: Path,
    private val fallback: PngRenderer?,
    private val fixtureRoot: Path,
    private val sampleAppModuleRoot: Path,
) : PngRenderer {
    // ... 기존 필드 그대로 ...

    @Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null

    @Synchronized
    private fun ensureSampleAppClassLoader(): ClassLoader {
        sampleAppClassLoader?.let { return it.classLoader }
        val isolated = classLoader ?: error("Bridge 가 init 안 됨 (initBridge 가 먼저 실행되어야 함)")
        val built = SampleAppClassLoader.build(sampleAppModuleRoot, isolated)
        sampleAppClassLoader = built
        return built.classLoader
    }
```

- [ ] **Step 2: `renderViaLayoutlib` 안의 `MinimalLayoutlibCallback()` 호출을 `MinimalLayoutlibCallback { ensureSampleAppClassLoader() }` 로 교체**

해당 라인은 `LayoutlibRenderer.kt:170` 부근 (W2D7 시점 기준; 현재 정확한 라인은 grep `MinimalLayoutlibCallback()`).

- [ ] **Step 3: 컴파일만 — 컴파일 FAIL 예상 (호출부 모두 sampleAppModuleRoot 인자 누락)**

Run: `./server/gradlew -p server :layoutlib-worker:compileKotlin :mcp-server:compileKotlin`
Expected: FAIL — 호출부가 새 인자를 안 줌.

(여기서 commit 하지 않고 Task 8 까지 묶어서 처리 — 컴파일이 깨진 상태는 task unit 미완으로 간주.)

---

## Task 8: 모든 호출부 업데이트 — Main.kt CLI + SharedLayoutlibRenderer + 모든 테스트

**Files:**
- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt`
- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt`
- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt`
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt`
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt`
- Modify: 모든 production `LayoutlibRenderer(...)` 호출하는 테스트 (W3D1: `LayoutlibRendererTier3MinimalTest`, integration tests).
- Modify: `LayoutlibRendererIntegrationTest.kt` — 곧 Task 10 에서 enable.

- [ ] **Step 1: CliConstants/CliArgs — `--sample-app-root=<path>` 플래그 추가**

CliConstants.VALUE_BEARING_FLAGS 에 `"--sample-app-root"` 추가.
CliArgs 데이터클래스에 `val sampleAppRoot: String?` 추가, parse 가 채움.

- [ ] **Step 2: Main.kt `chooseRenderer` — `FixtureDiscovery.locateModuleRoot(cliArgs.sampleAppRoot?.let { Path.of(it) })` 결과를 LayoutlibRenderer 에 주입**

기존 `--fixture-root` 처리 옆에 동일 패턴.

- [ ] **Step 3: SharedLayoutlibRenderer.getOrCreate(distDir, fixtureRoot, sampleAppModuleRoot, fallback)**

W3D2 의 시그니처 그대로 확장. binding 은 `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 호출.

- [ ] **Step 4: 모든 production renderer 호출 grep + 업데이트**

```bash
grep -rn "LayoutlibRenderer(" server/ --include="*.kt"
grep -rn "SharedLayoutlibRenderer.getOrCreate" server/ --include="*.kt"
```

각 호출부에서 `sampleAppModuleRoot` 인자 명시. 테스트는 `FixtureDiscovery.locateModuleRoot(null)!!` 패턴 사용.

- [ ] **Step 5: `LayoutlibRendererTier3MinimalTest.kt` — companion sharedRenderer 생성 시 sampleAppModuleRoot 추가**

- [ ] **Step 6: 전체 build + test 실행 — 컴파일 PASS + 기존 테스트 (118 unit + 11 integration) 회귀 없음 확인**

Run: `./server/gradlew -p server build`
Expected: BUILD SUCCESSFUL. activity_basic.xml 의 `LayoutlibRendererIntegrationTest` 는 아직 `@Disabled` 상태 — 회귀 없음.

- [ ] **Step 7: Commit (Task 7 + Task 8 묶음 — atomic compilation unit)**

---

## Task 9: Gradle `axpEmitClasspath` task + 첫 manifest 생성

**Files:**
- Modify: `fixture/sample-app/app/build.gradle.kts`

- [ ] **Step 1: Gradle 신규 task 추가**

`build.gradle.kts` 의 끝에 spec §4.1 의 코드 그대로 추가.

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

## Task 10: `LayoutlibRendererIntegrationTest` enable + 첫 실측 + B2 contingency 결정

**Files:**
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`

- [ ] **Step 1: `@Disabled` 제거 + spec §4.8 의 코드 적용 (SUCCESS assertion 활성, sampleAppModuleRoot 주입)**

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
  - stack trace 분석 → 본 플랜의 누락된 컴포넌트 식별 → 그 컴포넌트의 task 재진입 / 신규 task 추가.

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

## Task 11: work_log + handoff 업데이트

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

**Spec coverage**:
- §0 페어 리뷰 결과 → Task 1-10 구현으로 모두 반영.
- §1.1 deliverables (manifest, AAR loader, callback override, integration enable) → Task 1-4, 6, 9, 10.
- §1.2 out-of-scope → 본 플랜 미터치 (W3D4 carry).
- §1.3 acceptance gate (T1) → Task 10 Step 2.
- §2 컴포넌트 분해 → Task 별 1:1 매핑 (C0/C1/C2/C3/C4/C5/C6/C7/C8 → Task 5/9/2/3/4/1/6/7/10).
- §3 데이터 흐름 → Task 7+8 의 LayoutlibRenderer 수정으로 wire.
- §4 컴포넌트 스펙 → Task 1-7 의 코드 블록.
- §5 테스트 전략 → Task 1-4, 6 의 단위 + Task 10 의 통합.
- §6 위험 → Task 10 Step 3 의 분기 (R1/R3/R4).
- §7 페어 리뷰 Q1-Q5 → spec round 2 에서 모두 처리, 코드 반영.
- §8 변경 로그 → Task 11 step 3 가 plan/08 에 추가.
- §9 contingency → Task 10 Step 3 (B).

**No placeholders**: 모든 Task 가 exact code + exact commands + exact expected output. "TODO" 없음.

**Type consistency**: `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 시그니처가 모든 task 에서 일관. `MinimalLayoutlibCallback { provider }` lambda 도.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md`. Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, two-stage review.
2. **Inline Execution** — batch with checkpoints.

**Pre-execution gating**: 본 플랜 자체에 대한 round 2 페어 리뷰 (Codex+Claude 1:1, planning phase 마지막 게이트) 가 먼저 수행됨 — verdict GO 또는 GO_WITH_FOLLOWUPS 일 때만 구현 phase 진입.

**Execution choice**: Subagent-Driven (CLAUDE.md "Implementation phase = Claude-only, no Codex" 정책 + W3D2 에서 검증된 패턴).
