# W3D2-INTEGRATION-CLEANUP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** LayoutlibRenderer 생성자 default parameter 2개 제거 + CLI override 인자 추가 + integration test 의 `assumeTrue(false)` masking 제거 + singleton 패턴으로 in-class L4 회피. 99→**117** unit PASS, 8→**11** integration PASS, 2 SKIPPED 유지.

**Architecture:** 2 phase.
- **Phase 1 (C1+C2)** — `LayoutlibRenderer.fallback` / `fixtureRoot` default 제거, `DistDiscovery` / `FixtureDiscovery` / `CliConstants` / `CliArgs` 신규 object 로 path / CLI 상수화, `Main.kt` 가 `CliArgs.parse(args, CliConstants.VALUE_BEARING_FLAGS)` → `chooseRenderer(parsed)` 로 재배선. Task 4 는 **default 제거와 동시에 두 test 파일** (Tier3MinimalTest, LayoutlibRendererIntegrationTest) 의 constructor 호출을 3-arg 로 동반 수정 (compile 유지).
- **Phase 2 (C3)** — `SharedRendererBinding` pure helper + `SharedLayoutlibRenderer` test-only singleton 으로 **각 JVM fork 내부** native lib 일관성 보장 (Gradle `forkEvery(1L)` 가 integration test class 단위 JVM 격리를 이미 보장하므로 cross-class 문제는 없음; 본 singleton 은 in-class 패턴 통일용). Tier3MinimalTest 와 LayoutlibRendererIntegrationTest 양쪽이 동일 `getOrCreate` API 사용.

**Tech Stack:** Kotlin 1.9 (JVM target), JUnit 5 Jupiter (`@TempDir`, `@Tag`, `@Disabled`), Gradle 8.7, kxml2 2.3.0 (기존).

**Non-git project note:** 본 저장소는 git init 되어 있지 않다 (`fatal: not a git repository` 확인, W3D1 L6 landmine). 각 Task 의 commit step 은 생략하고, Phase 경계에서 전체 gradle gate + 마지막에 session work_log 에 journaling. Codex 호출 시 `--skip-git-repo-check` 필수.

---

## 사전 환경 검증 (필수)

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
./server/gradlew -p server test
# Expected: BUILD SUCCESSFUL, 99 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
# Expected: BUILD SUCCESSFUL, 8 PASS + 2 SKIPPED (tier3-glyph + LayoutlibRendererIntegrationTest)
./server/gradlew -p server build
# Expected: BUILD SUCCESSFUL (fatJar 포함)
```

모두 green 이어야 시작. 실패 시 W3D1 handoff `docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md` §"긴급 회복" 참조.

---

## File Structure

### Phase 1 신규 파일 (7개 — production 4 + test 3)

| 파일 | 책임 | 예상 LOC |
|---|---|---|
| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/DistDiscovery.kt` | `object DistDiscovery`: dist override/auto-detect + `LAYOUTLIB_DIST_SUBDIR`, `CANDIDATE_ROOTS`. Testable via `locateInternal(override, userDir, candidateRoots)`. | ~55 |
| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt` | `object FixtureDiscovery`: 동일 패턴, `FIXTURE_SUBPATH`, `CANDIDATE_ROOTS`. | ~55 |
| `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt` | `object CliConstants`: flag 이름 + `ARG_SEPARATOR` + `USAGE_LINE`. | ~20 |
| `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt` | `internal data class CliArgs` + companion `parse(args, valueBearingFlags)`. | ~60 |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/DistDiscoveryTest.kt` | 4 unit tests | ~70 |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt` | 4 unit tests | ~70 |
| `server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt` | 7 unit tests | ~120 |

### Phase 2 신규 파일 (4개 — test 4)

| 파일 | 책임 | 예상 LOC |
|---|---|---|
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt` | `internal object` pure helper `verify(bound, requested)`. test sourceset 이지만 `internal` 이라 테스트 클래스에서 접근. | ~25 |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt` | `object` JVM-wide singleton `getOrCreate(distDir, fixtureRoot, fallback)`. | ~55 |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt` | 3 unit tests | ~50 |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt` | 3 integration tests (`@Tag("integration")`) | ~80 |

### 수정 파일 (4개)

| 파일 | Phase | 변경 요약 |
|---|---|---|
| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` | 1 | 생성자 default 2개 (`fallback`, `fixtureRoot`) 제거. `companion.defaultFixtureRoot()` 삭제. |
| `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` | 1 | `main(args)` → `CliArgs.parse(args)` 로 파싱. `chooseRenderer(parsed)` 가 `DistDiscovery.locate` + `FixtureDiscovery.locate` 호출 후 LayoutlibRenderer 명시 주입. `runStdioMode` / `runHttpMode` 가 `CliArgs` 받음. |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt` | 1 → 2 | Phase 1: `companion.renderer()` 가 `LayoutlibRenderer(dist, null, FixtureDiscovery.locate(null)!!)` 명시 주입. Phase 2: `SharedLayoutlibRenderer.getOrCreate(...)` 호출로 전환, `sharedRenderer`/`renderer()`/`locateDistDirStatic()` 제거. |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` | 2 | `@Disabled("W3 sample-app unblock 필요 — L3 DexClassLoader carry")` + `SharedLayoutlibRenderer.getOrCreate`. `assumeTrue(false)` + try/catch 제거. |

---

# PHASE 1 — Default 제거 + CLI 인자 (C1+C2)

---

## Task 1: DistDiscovery

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/DistDiscovery.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/DistDiscoveryTest.kt`

- [ ] **Step 1.1: Write failing tests (4 cases)**

Create `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/DistDiscoveryTest.kt`:

```kotlin
package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DistDiscoveryTest {

    @Test
    fun `override 가 존재 디렉토리이면 해당 경로 반환`(@TempDir tempDir: Path) {
        val customDist = tempDir.resolve("custom-layoutlib").also { Files.createDirectory(it) }

        val found = DistDiscovery.locate(customDist)

        assertEquals(customDist, found)
    }

    @Test
    fun `override 경로가 비존재면 IllegalArgumentException`(@TempDir tempDir: Path) {
        val nonexistent = tempDir.resolve("does-not-exist")

        val ex = assertThrows<IllegalArgumentException> {
            DistDiscovery.locate(nonexistent)
        }
        assertTrue(
            ex.message!!.contains(nonexistent.toString()),
            "에러 메시지에 경로 포함 필요: ${ex.message}",
        )
    }

    @Test
    fun `override null + candidate match 시 해당 경로 반환`(@TempDir tempDir: Path) {
        val root = "fake-libs"
        val target = tempDir.resolve(root).resolve(DistDiscovery.LAYOUTLIB_DIST_SUBDIR)
        Files.createDirectories(target)

        val found = DistDiscovery.locateInternal(
            override = null,
            userDir = tempDir.toString(),
            candidateRoots = listOf(root),
        )

        assertEquals(target, found)
    }

    @Test
    fun `override null + 모든 candidate 실패 시 null 반환`(@TempDir tempDir: Path) {
        val found = DistDiscovery.locateInternal(
            override = null,
            userDir = tempDir.toString(),
            candidateRoots = listOf("no-such", "not-here"),
        )

        assertNull(found)
    }
}
```

- [ ] **Step 1.2: Run tests — verify RED (컴파일 실패)**

```bash
./server/gradlew -p server :layoutlib-worker:test --tests "*DistDiscoveryTest"
```
Expected: `unresolved reference: DistDiscovery` — `DistDiscovery` 가 아직 존재하지 않음.

- [ ] **Step 1.3: Create DistDiscovery.kt**

Create `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/DistDiscovery.kt`:

```kotlin
package dev.axp.layoutlib.worker

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * W3D2 integration cleanup (Codex F3 carry).
 *
 * layoutlib-dist 디렉토리를 탐지. override 가 있으면 명시 경로 검증 후 사용,
 * 없으면 CANDIDATE_ROOTS × LAYOUTLIB_DIST_SUBDIR 순차 탐색. 모두 실패 시 null.
 *
 * Main.kt (CLI) 와 LayoutlibRendererTier3MinimalTest (테스트) 양쪽이 사용하는
 * 공용 entry point. CLI 관점의 에러 메시지는 Main.kt 가 flag 이름을 포함해
 * 재포장한다 — 본 object 는 CliConstants 에 의존하지 않는다 (모듈 역방향 회피).
 */
object DistDiscovery {
    const val LAYOUTLIB_DIST_SUBDIR = "layoutlib-dist/android-34"

    val CANDIDATE_ROOTS: List<String> = listOf(
        "server/libs",
        "../libs",
    )

    fun locate(override: Path?): Path? = locateInternal(
        override = override,
        userDir = System.getProperty("user.dir"),
        candidateRoots = CANDIDATE_ROOTS,
    )

    internal fun locateInternal(
        override: Path?,
        userDir: String,
        candidateRoots: List<String>,
    ): Path? {
        if (override != null) {
            require(Files.isDirectory(override)) {
                "dist 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
            }
            return override
        }
        val candidates: List<Path> = candidateRoots.flatMap { root ->
            listOf(
                Paths.get(root, LAYOUTLIB_DIST_SUBDIR),
                Paths.get(userDir, root, LAYOUTLIB_DIST_SUBDIR),
            )
        }
        return candidates.firstOrNull { Files.isDirectory(it) }
    }
}
```

- [ ] **Step 1.4: Run tests — verify GREEN**

```bash
./server/gradlew -p server :layoutlib-worker:test --tests "*DistDiscoveryTest"
```
Expected: `BUILD SUCCESSFUL`, 4 tests PASS.

- [ ] **Step 1.5: Run full unit suite — regression check**

```bash
./server/gradlew -p server :layoutlib-worker:test
```
Expected: 기존 tests 영향 없음, DistDiscoveryTest 포함해 추가로 4개 PASS (+4 = 79 in layoutlib-worker + 20 other unit = 103 total; 시기별 숫자는 gradle 요약 확인).

---

## Task 2: FixtureDiscovery

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt`

- [ ] **Step 2.1: Write failing tests (4 cases)**

Create `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt`:

```kotlin
package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FixtureDiscoveryTest {

    @Test
    fun `override 가 존재 디렉토리이면 해당 경로 반환`(@TempDir tempDir: Path) {
        val customFixture = tempDir.resolve("custom-fixture").also { Files.createDirectory(it) }

        val found = FixtureDiscovery.locate(customFixture)

        assertEquals(customFixture, found)
    }

    @Test
    fun `override 경로가 비존재면 IllegalArgumentException`(@TempDir tempDir: Path) {
        val nonexistent = tempDir.resolve("missing-fixture")

        val ex = assertThrows<IllegalArgumentException> {
            FixtureDiscovery.locate(nonexistent)
        }
        assertTrue(
            ex.message!!.contains(nonexistent.toString()),
            "에러 메시지에 경로 포함 필요: ${ex.message}",
        )
    }

    @Test
    fun `override null + candidate match 시 해당 경로 반환`(@TempDir tempDir: Path) {
        val root = "myroot"
        val target = tempDir.resolve(root).resolve(FixtureDiscovery.FIXTURE_SUBPATH)
        Files.createDirectories(target)

        val found = FixtureDiscovery.locateInternal(
            override = null,
            userDir = tempDir.toString(),
            candidateRoots = listOf(root),
        )

        assertEquals(target, found)
    }

    @Test
    fun `override null + 모든 candidate 실패 시 null 반환`(@TempDir tempDir: Path) {
        val found = FixtureDiscovery.locateInternal(
            override = null,
            userDir = tempDir.toString(),
            candidateRoots = listOf("no-such", "not-here"),
        )

        assertNull(found)
    }
}
```

- [ ] **Step 2.2: Run tests — verify RED**

```bash
./server/gradlew -p server :layoutlib-worker:test --tests "*FixtureDiscoveryTest"
```
Expected: `unresolved reference: FixtureDiscovery`.

- [ ] **Step 2.3: Create FixtureDiscovery.kt**

Create `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt`:

```kotlin
package dev.axp.layoutlib.worker

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * W3D2 integration cleanup (Codex F3 carry) — fixture root (XML 레이아웃 루트) 탐지.
 *
 * override 제공 시 명시 경로 검증 후 사용, 없으면 CANDIDATE_ROOTS × FIXTURE_SUBPATH
 * 순차 탐색. 모두 실패 시 null.
 *
 * 이전에는 LayoutlibRenderer.companion.defaultFixtureRoot() 에 있었으나, CLI 와
 * 테스트 양쪽에서 사용하도록 독립 object 로 추출.
 */
object FixtureDiscovery {
    const val FIXTURE_SUBPATH = "fixture/sample-app/app/src/main/res/layout"

    val CANDIDATE_ROOTS: List<String> = listOf(
        "",         // cwd
        "..",       // server/layoutlib-worker 등 sub-cwd
        "../..",    // server/ cwd
    )

    fun locate(override: Path?): Path? = locateInternal(
        override = override,
        userDir = System.getProperty("user.dir"),
        candidateRoots = CANDIDATE_ROOTS,
    )

    internal fun locateInternal(
        override: Path?,
        userDir: String,
        candidateRoots: List<String>,
    ): Path? {
        if (override != null) {
            require(Files.isDirectory(override)) {
                "fixture root 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
            }
            return override
        }
        val candidates: List<Path> = candidateRoots.flatMap { root ->
            if (root.isEmpty()) listOf(
                Paths.get(FIXTURE_SUBPATH),
                Paths.get(userDir, FIXTURE_SUBPATH),
            ) else listOf(
                Paths.get(root, FIXTURE_SUBPATH),
                Paths.get(userDir, root, FIXTURE_SUBPATH),
            )
        }
        return candidates.firstOrNull { Files.isDirectory(it) }
    }
}
```

- [ ] **Step 2.4: Run tests — verify GREEN**

```bash
./server/gradlew -p server :layoutlib-worker:test --tests "*FixtureDiscoveryTest"
```
Expected: 4 tests PASS.

- [ ] **Step 2.5: Run full layoutlib-worker unit suite**

```bash
./server/gradlew -p server :layoutlib-worker:test
```
Expected: DistDiscovery 4 + FixtureDiscovery 4 = +8 new tests PASS, 기타 regression 없음.

---

## Task 3: CliConstants + CliArgs

**Files:**
- Create: `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt`
- Create: `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt`
- Test: `server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt`

- [ ] **Step 3.1: Write failing tests (7 cases — MF-B2 + MF-F2 반영)**

Create `server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt`:

```kotlin
package dev.axp.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CliArgsTest {

    private val valueBearing = CliConstants.VALUE_BEARING_FLAGS

    @Test
    fun `--key=value 형식 파싱`() {
        val parsed = CliArgs.parse(arrayOf("--dist-dir=/opt/x"), valueBearing)

        assertEquals("/opt/x", parsed.valueOf("--dist-dir"))
        assertFalse(parsed.hasFlag("--dist-dir"))
    }

    @Test
    fun `비 value-bearing --flag 단독 은 flags set 에 등록`() {
        val parsed = CliArgs.parse(arrayOf("--stdio"), valueBearing)

        assertTrue(parsed.hasFlag("--stdio"))
        assertNull(parsed.valueOf("--stdio"))
    }

    @Test
    fun `--key= (빈 value) 는 IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            CliArgs.parse(arrayOf("--dist-dir="), valueBearing)
        }
        assertTrue(
            ex.message!!.contains("--dist-dir") && ex.message!!.contains("값 누락"),
            "에러 메시지: ${ex.message}",
        )
    }

    @Test
    fun `MF-B2 value-bearing --dist-dir bare (= 없이) 는 IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            CliArgs.parse(arrayOf("--dist-dir"), valueBearing)
        }
        assertTrue(
            ex.message!!.contains("--dist-dir") && ex.message!!.contains("값을 받는 옵션"),
            "에러 메시지: ${ex.message}",
        )
    }

    @Test
    fun `비-플래그 위치 arg (-- prefix 없음) 는 warn-only 로 무시`() {
        val parsed = CliArgs.parse(arrayOf("garbage", "--stdio"), valueBearing)

        assertTrue(parsed.hasFlag("--stdio"))
        assertFalse(parsed.hasFlag("garbage"))
        assertNull(parsed.valueOf("garbage"))
    }

    @Test
    fun `중복 key 는 last-wins`() {
        val parsed = CliArgs.parse(arrayOf("--dist-dir=/a", "--dist-dir=/b"), valueBearing)

        assertEquals("/b", parsed.valueOf("--dist-dir"))
    }

    @Test
    fun `MF-F2 edge cases — ---triple-dash 는 flags 에 그대로 저장, --=value 는 빈 key + value 저장`() {
        // ---triple-dash 는 value-bearing 밖이라 flags 에 저장만 됨 (Main.kt 가 lookup 하지 않으면 무시).
        // --=value 는 key="--", value="value" 로 파싱 — 역시 Main.kt 가 조회하지 않아 무영향.
        val parsed = CliArgs.parse(arrayOf("---triple-dash", "--=value"), valueBearing)

        assertTrue(parsed.hasFlag("---triple-dash"))
        assertEquals("value", parsed.valueOf("--"))
    }
}
```

- [ ] **Step 3.2: Run tests — verify RED**

```bash
./server/gradlew -p server :mcp-server:test --tests "*CliArgsTest"
```
Expected: `unresolved reference: CliArgs`.

- [ ] **Step 3.3: Create CliConstants.kt**

Create `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt`:

```kotlin
package dev.axp.mcp

/**
 * W3D2 integration cleanup (Codex F3 carry) — CLI flag 이름 / 구분자 / value-bearing flag 집합.
 *
 * StdioConstants (mode flags: --smoke, --stdio, --http-server) 와 분리되어 있다.
 * 이 object 는 "값을 받는 옵션" (--dist-dir=/x, --fixture-root=/y) 전용.
 */
object CliConstants {
    const val DIST_DIR_FLAG = "--dist-dir"
    const val FIXTURE_ROOT_FLAG = "--fixture-root"
    const val ARG_SEPARATOR = "="
    const val USAGE_LINE =
        "Usage: axp-server [--dist-dir=<path>] [--fixture-root=<path>] [--smoke|--stdio|--http-server]"

    /**
     * MF-B2 — 값을 반드시 `=<value>` 로 받아야 하는 flag 집합. `CliArgs.parse` 가
     * 이 집합의 flag 가 `=` 없이 단독 입력되면 `IllegalArgumentException` 을 던진다.
     */
    val VALUE_BEARING_FLAGS: Set<String> = setOf(DIST_DIR_FLAG, FIXTURE_ROOT_FLAG)
}
```

- [ ] **Step 3.4: Create CliArgs.kt**

Create `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt`:

```kotlin
package dev.axp.mcp

/**
 * W3D2 integration cleanup (Codex F3 carry) — 간단한 CLI 인자 파서.
 *
 * GNU-style `--key=value` 와 `--flag` (value 없음) 모두 지원. 비-플래그 위치 인자와
 * 정체 불명 플래그는 stderr 경고 후 무시 (하위호환).
 */
internal data class CliArgs(
    val flags: Set<String>,
    val values: Map<String, String>,
) {
    fun hasFlag(name: String): Boolean = flags.contains(name)
    fun valueOf(name: String): String? = values[name]

    companion object {
        fun parse(args: Array<String>, valueBearingFlags: Set<String>): CliArgs {
            val flags = mutableSetOf<String>()
            val values = mutableMapOf<String, String>()
            for (raw in args) {
                if (!raw.startsWith("--")) {
                    System.err.println("axp: 알 수 없는 인자 무시: $raw")
                    continue
                }
                val eqIdx = raw.indexOf(CliConstants.ARG_SEPARATOR)
                if (eqIdx < 0) {
                    // MF-B2: value-bearing flag 가 `=` 없이 단독 입력되면 throw.
                    require(raw !in valueBearingFlags) {
                        "axp: $raw 는 값을 받는 옵션입니다 — `$raw=<value>` 형식 필요 (${CliConstants.USAGE_LINE})"
                    }
                    flags.add(raw)
                } else {
                    val key = raw.substring(0, eqIdx)
                    val value = raw.substring(eqIdx + 1)
                    require(value.isNotEmpty()) {
                        "axp: $key 값 누락 (${CliConstants.USAGE_LINE})"
                    }
                    values[key] = value
                }
            }
            return CliArgs(flags, values)
        }
    }
}
```

- [ ] **Step 3.5: Run tests — verify GREEN**

```bash
./server/gradlew -p server :mcp-server:test --tests "*CliArgsTest"
```
Expected: 7 tests PASS (MF-B2 + MF-F2 반영분 포함).

- [ ] **Step 3.6: Run full mcp-server unit suite**

```bash
./server/gradlew -p server :mcp-server:test
```
Expected: 기존 테스트 (JsonRpcSerializationTest, McpMethodHandlerTest, McpStdioServerTest, LogbackStderrConfigTest) 모두 PASS + CliArgsTest 7개 PASS.

---

## Task 4: LayoutlibRenderer default 제거 + Tier3 factory 명시 주입 (임시)

**Files:**
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt`

목적: `LayoutlibRenderer` 생성자에서 `fallback`, `fixtureRoot` default 를 제거. production 호출부 (Main.kt) 는 Task 5 에서 수정. 본 Task 에서는 test-side factory 를 임시로 명시 주입으로 바꿔 컴파일 유지.

- [ ] **Step 4.1: 현재 `LayoutlibRenderer.kt:44-48` 생성자 + `companion.defaultFixtureRoot` 확인**

```bash
sed -n '44,48p' server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt
sed -n '252,267p' server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt
```

- [ ] **Step 4.2: LayoutlibRenderer 수정 — default 제거 + defaultFixtureRoot 삭제**

Apply edits to `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`:

(a) 생성자 라인 44-48 을 아래로 교체:

```kotlin
class LayoutlibRenderer(
    private val distDir: Path,
    private val fallback: PngRenderer?,
    private val fixtureRoot: Path,
) : PngRenderer {
```

(b) `companion object` 내부 `defaultFixtureRoot()` 메서드 (라인 252-265 추정) **전체 삭제**. `BRIDGE_CREATE_SESSION` 상수는 유지. 삭제 후 companion 은:

```kotlin
    companion object {
        private const val BRIDGE_CREATE_SESSION = "createSession"

        /** `createSession(SessionParams)` 의 parameter count — strict 리플렉션 매칭용. */
        private const val BRIDGE_CREATE_SESSION_PARAM_COUNT = 1
    }
```

- [ ] **Step 4.3: Tier3 test factory 업데이트 (임시 — Phase 2 에서 다시 수정됨)**

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt` 의 `companion.renderer()` (라인 45-53) 를 아래로 교체:

```kotlin
        private fun renderer(): LayoutlibRenderer {
            sharedRenderer?.let { return it }
            synchronized(this) {
                sharedRenderer?.let { return it }
                val dist = locateDistDirStatic()
                val fixture = FixtureDiscovery.locate(null)
                    ?: error("fixture root 탐지 실패 — Tier3 test 실행 환경 확인")
                val r = LayoutlibRenderer(
                    distDir = dist,
                    fallback = null,
                    fixtureRoot = fixture,
                )
                sharedRenderer = r
                return r
            }
        }
```

- [ ] **Step 4.4: LayoutlibRendererIntegrationTest 호출부 임시 update (MF-B1 — 컴파일 유지)**

현재 `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:26` 은 `LayoutlibRenderer(dist)` (1-arg) 를 호출한다. Step 4.2 의 default 제거로 이 호출은 컴파일 FAIL → `:layoutlib-worker:test` 가 컴파일 단계에서 깨진다 (integration 태그 여부와 무관하게 모든 test source 가 `compileTestKotlin` 에서 컴파일됨).

해결: 3-arg 로 임시 명시 주입. Task 10 에서 `SharedLayoutlibRenderer.getOrCreate` + `@Disabled` 로 다시 교체 예정.

`LayoutlibRendererIntegrationTest.kt:25-26` 의 2줄을 아래로 교체:

```kotlin
        val dist = locateDistDir()
        val fixture = FixtureDiscovery.locate(null)
            ?: org.junit.jupiter.api.Assumptions.assumeTrue(false, "fixture 없음").let { return }
        val renderer = LayoutlibRenderer(
            distDir = dist,
            fallback = null,
            fixtureRoot = fixture,
        )
```

`assumeTrue(false)` 호출로 skip, 다음 문의 `return` 은 실제로는 도달 불가 (assumeTrue 가 TestAbortedException throw) — 컴파일을 통과시키는 구문적 trick. Task 10 에서 전체 파일이 `@Disabled` 로 교체되며 이 trick 도 제거.

- [ ] **Step 4.5: 컴파일 + 테스트 실행 — verify GREEN**

```bash
./server/gradlew -p server :layoutlib-worker:test
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
```
Expected:
- Unit: 기존 + DistDiscovery 4 + FixtureDiscovery 4 모두 PASS.
- Integration: tier3-arch, tier3-values PASS (Tier3 test 가 새 factory 로 정상 동작). tier3-glyph 여전히 SKIPPED. LayoutlibRendererIntegrationTest 는 `assumeTrue(false)` masking (기존) 으로 여전히 SKIPPED — Task 10 에서 `@Disabled` 로 깔끔히 전환.

**주의**: mcp-server 는 아직 컴파일 에러일 가능성 — 기존 `LayoutlibRenderer(dist.toAbsolutePath().normalize(), fallback = PlaceholderPngRenderer())` 호출에 `fixtureRoot` 인자가 없음. **다음 Task 5 에서 즉시 수정**.

- [ ] **Step 4.6: 빌드 확인 (mcp-server 컴파일 에러 예상 — Task 4+5 atomicity scope)**

```bash
./server/gradlew -p server :mcp-server:compileKotlin
```
Expected: `Main.kt` 에서 `LayoutlibRenderer` 호출이 `fixtureRoot` 인자 부족으로 컴파일 FAIL — 이는 예상된 상태. 다음 Task 에서 해결.

**Atomicity note (MF-B1 extension)**: Task 4+5 는 atomic unit. 중간 단계에서 **full-project gate (`./server/gradlew -p server test` 또는 `./server/gradlew -p server build`) 를 절대 돌리지 말 것** — mcp-server 컴파일 FAIL 이 의도된 intermediate state. Full-project gate 는 Task 6 에서만 실행.

---

## Task 5: Main.kt 리팩토링 (CLI parse + chooseRenderer 재배선)

**Files:**
- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt`

- [ ] **Step 5.1: 현재 Main.kt 구조 확인**

```bash
cat server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt
```
기존: `fun main(args)` → `argSet = args.toSet()` → flag 분기 → `runStdioMode()` / `runHttpMode()`. `chooseRenderer()` 가 내부적으로 Path 후보 탐색.

- [ ] **Step 5.2: Main.kt 재작성**

Replace the ENTIRE contents of `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` with:

```kotlin
package dev.axp.mcp

import dev.axp.http.HttpServerConstants
import dev.axp.http.PlaceholderPngRenderer
import dev.axp.http.PreviewServer
import dev.axp.layoutlib.worker.DistDiscovery
import dev.axp.layoutlib.worker.FixtureDiscovery
import dev.axp.layoutlib.worker.LayoutlibRenderer
import dev.axp.protocol.Versions
import dev.axp.protocol.mcp.Capabilities
import dev.axp.protocol.render.PngRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

/**
 * Claude Code MCP 서버 엔트리.
 *
 * CLI 모드:
 *   --smoke           : stderr 에 버전 라인 + stdout 에 "ok" 1줄 후 종료 (CI 스모크).
 *   --stdio           : MCP JSON-RPC 2.0 루프 (stdin/stdout, EOF 까지 유지).
 *   --http-server     : localhost:7321 에 HTTP/SSE 서버 + viewer.
 *   (인자 없음)         : 기본 = --http-server (W1 demo 호환 유지).
 *
 * Path override (optional):
 *   --dist-dir=<path>     : layoutlib dist. 없으면 DistDiscovery.locate(null) 로 auto-detect.
 *   --fixture-root=<path> : XML fixture 루트. 없으면 FixtureDiscovery.locate(null).
 *   override 제공했으나 경로 비존재 → IllegalArgumentException (fail-fast).
 *   auto-detect 모두 실패 → PlaceholderPngRenderer fallback (기존 behavior 유지).
 */
fun main(args: Array<String>) {
    val parsed = CliArgs.parse(args, CliConstants.VALUE_BEARING_FLAGS)
    val versionLine = buildVersionLine()
    System.err.println(versionLine)

    if (parsed.hasFlag(StdioConstants.SMOKE_FLAG)) {
        System.out.println(StdioConstants.SMOKE_OK_LINE)
        return
    }

    if (parsed.hasFlag(StdioConstants.STDIO_FLAG)) {
        runStdioMode(parsed)
        return
    }

    runHttpMode(parsed)
}

private fun runStdioMode(parsed: CliArgs) {
    // W2D6 final pair review (Codex HIGH): JSON-RPC 2.0 §5 는 result 와 error 상호 배타.
    // kotlinx.serialization 은 encodeDefaults=true + null 필드를 기본 방출하므로 explicitNulls=false
    // 로 null 필드를 생략해 spec 준수.
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false }
    val renderer = chooseRenderer(parsed)
    val handler = McpMethodHandler(json)
    handler.registerTool(
        descriptor = ToolDescriptor(
            name = StdioConstants.TOOL_RENDER_LAYOUT,
            description = "Render a single Android layout XML to a PNG (base64-encoded) using layoutlib.",
            inputSchema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {
                    put(StdioConstants.TOOL_PARAM_LAYOUT, buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Layout file name, e.g. activity_basic.xml"))
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive(StdioConstants.TOOL_PARAM_LAYOUT))
                })
            }
        ),
        invoker = { args ->
            val layoutName = (args as? kotlinx.serialization.json.JsonObject)
                ?.get(StdioConstants.TOOL_PARAM_LAYOUT)
                ?.let { (it as? JsonPrimitive)?.content }
                ?: error("layout 인자 누락")
            val bytes = renderer.renderPng(layoutName)
            val b64 = Base64.getEncoder().encodeToString(bytes)
            // F-1 (W2D6 pair review): MCP spec requires result.content[] typed content blocks.
            buildJsonObject {
                put(StdioConstants.FIELD_CONTENT, buildJsonArray {
                    add(buildJsonObject {
                        put(StdioConstants.FIELD_TYPE, JsonPrimitive(StdioConstants.CONTENT_TYPE_IMAGE))
                        put(StdioConstants.FIELD_DATA, JsonPrimitive(b64))
                        put(StdioConstants.FIELD_MIME_TYPE, JsonPrimitive(StdioConstants.MIME_TYPE_PNG))
                    })
                })
                put(StdioConstants.FIELD_IS_ERROR, JsonPrimitive(false))
            }
        }
    )
    val server = McpStdioServer(handler, System.`in`, System.out, json)
    server.run()
}

private fun runHttpMode(parsed: CliArgs) {
    val renderer = chooseRenderer(parsed)
    val server = PreviewServer(pngRenderer = renderer)
    server.start()
    System.err.println(
        "axp viewer ready: http://${HttpServerConstants.DEFAULT_HOST}:${HttpServerConstants.DEFAULT_PORT}/" +
            " (renderer=${renderer.javaClass.simpleName})"
    )
    server.blockUntilShutdown()
}

private fun chooseRenderer(parsed: CliArgs): PngRenderer {
    val distOverride: Path? = parsed.valueOf(CliConstants.DIST_DIR_FLAG)?.let { Paths.get(it) }
    val fixtureOverride: Path? = parsed.valueOf(CliConstants.FIXTURE_ROOT_FLAG)?.let { Paths.get(it) }

    val dist = DistDiscovery.locate(distOverride)
    val fixture = FixtureDiscovery.locate(fixtureOverride)

    if (dist == null || fixture == null) {
        System.err.println(
            "axp: layoutlib dist 또는 fixture 탐지 실패 (dist=$dist, fixture=$fixture) " +
                "→ placeholder PNG 로 fallback"
        )
        return PlaceholderPngRenderer()
    }
    return try {
        LayoutlibRenderer(
            distDir = dist.toAbsolutePath().normalize(),
            fallback = PlaceholderPngRenderer(),
            fixtureRoot = fixture.toAbsolutePath().normalize(),
        )
    } catch (e: Throwable) {
        System.err.println(
            "axp: LayoutlibRenderer 초기화 실패 (${e.javaClass.simpleName}) → placeholder fallback"
        )
        PlaceholderPngRenderer()
    }
}

private fun buildVersionLine(): String {
    return buildString {
        append("axp-server v").append(Versions.SERVER_VERSION)
        append(" (schema ").append(Versions.SCHEMA_VERSION).append(")")
        append(" capabilities=[")
        // W1-END 페어 리뷰 C-2: 실제 보유 capability 만 advertise.
        append(listOf(Capabilities.RENDER_L1, Capabilities.SSE_MINIMAL).joinToString(","))
        append("]")
    }
}
```

- [ ] **Step 5.3: 컴파일 검증**

```bash
./server/gradlew -p server :mcp-server:compileKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5.4: mcp-server 전체 테스트**

```bash
./server/gradlew -p server :mcp-server:test
```
Expected: 기존 테스트 (JsonRpcSerializationTest, McpMethodHandlerTest, McpStdioServerTest, LogbackStderrConfigTest) + CliArgsTest 5개 모두 PASS.

- [ ] **Step 5.5: smoke test — CLI 실제 실행 sanity**

```bash
./server/gradlew -p server :mcp-server:installDist
./server/mcp-server/build/install/mcp-server/bin/mcp-server --smoke
```
Expected: stderr 에 "axp-server vX.Y.Z..." line, stdout 에 "ok", exit 0.

```bash
./server/mcp-server/build/install/mcp-server/bin/mcp-server --dist-dir=/nonexistent --smoke
```
Expected: `--smoke` 는 renderer 를 만들지 않으므로 이 명령은 정상 종료 ("ok") — dist 검증은 renderer 생성 시점에서만 발생. (또한 `--smoke` 단계에 도달하기 전 `CliArgs.parse` 는 `--dist-dir=/nonexistent` 를 values 에 담기만 하고 검증하지 않음.)

---

## Task 6: Phase 1 full gate

**Files:** 없음 (검증만)

- [ ] **Step 6.1: 전체 unit test**

```bash
./server/gradlew -p server test
```
Expected: **114 unit PASS** — 99 (W3D1 baseline) + DistDiscovery 4 + FixtureDiscovery 4 + CliArgs 7 = 114. SharedRendererBinding 3 은 Phase 2 추가분이라 아직 없음. 최종 117 은 Phase 2 gate 에서.

- [ ] **Step 6.2: 전체 integration test**

```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
```
Expected: 8 PASS + 2 SKIPPED (tier3-arch, tier3-values, BridgeInitIntegrationTest 관련 6, FrameworkResourceLoaderRealDistTest 2 = 8 / tier3-glyph, LayoutlibRendererIntegrationTest = 2 SKIPPED). Phase 1 은 integration 수 불변.

- [ ] **Step 6.3: 전체 빌드 (fatJar 포함)**

```bash
./server/gradlew -p server build
```
Expected: `BUILD SUCCESSFUL`.

Phase 1 완료. Phase 2 진입.

---

# PHASE 2 — SharedLayoutlibRenderer (C3)

---

## Task 7: SharedRendererBinding (pure helper)

**Files:**
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt`

- [ ] **Step 7.1: Write failing tests (3 cases)**

Create `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt`:

```kotlin
package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths

class SharedRendererBindingTest {

    private val distA = Paths.get("/dist/a")
    private val distB = Paths.get("/dist/b")
    private val fixA = Paths.get("/fix/a")
    private val fixB = Paths.get("/fix/b")

    @Test
    fun `bound 가 null 이면 항상 통과 (첫 바인드)`() {
        assertDoesNotThrow {
            SharedRendererBinding.verify(bound = null, requested = distA to fixA)
        }
    }

    @Test
    fun `bound 와 requested 가 동일하면 통과`() {
        val same = distA to fixA

        assertDoesNotThrow {
            SharedRendererBinding.verify(bound = same, requested = same)
        }
    }

    @Test
    fun `bound 와 requested 가 다르면 IllegalStateException`() {
        val bound = distA to fixA
        val requested = distB to fixB

        val ex = assertThrows<IllegalStateException> {
            SharedRendererBinding.verify(bound = bound, requested = requested)
        }
        assertTrue(
            ex.message!!.contains("불일치") && ex.message!!.contains("bound"),
            "메시지: ${ex.message}",
        )
    }
}
```

- [ ] **Step 7.2: Run tests — verify RED**

```bash
./server/gradlew -p server :layoutlib-worker:test --tests "*SharedRendererBindingTest"
```
Expected: `unresolved reference: SharedRendererBinding`.

- [ ] **Step 7.3: Create SharedRendererBinding.kt**

Create `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt`:

```kotlin
package dev.axp.layoutlib.worker

import java.nio.file.Path

/**
 * W3D2 integration cleanup (Codex F4 carry) — SharedLayoutlibRenderer 의 args 일관성
 * 검증용 pure helper. LayoutlibRenderer 생성 없이 단위 테스트 가능하도록 분리.
 *
 * W2D7 L4: native lib 는 프로세스당 1회만 로드되고 첫 dist 에 바인드됨. 동일
 * JVM 에서 다른 dist 로 singleton 을 재사용하면 진단 어려운 실패 발생 →
 * 두 번째 호출의 args 가 첫 호출과 동일해야 함.
 */
internal object SharedRendererBinding {
    /**
     * 첫 바인드 (`bound == null`) 이면 silently 통과. 이후는 `bound == requested`
     * 여야 하며 불일치 시 [IllegalStateException].
     */
    fun verify(bound: Pair<Path, Path>?, requested: Pair<Path, Path>) {
        if (bound == null) return
        check(bound == requested) {
            "SharedLayoutlibRenderer args 불일치 — native lib 는 첫 바인드 args 에 고정. " +
                "bound=$bound requested=$requested"
        }
    }
}
```

- [ ] **Step 7.4: Run tests — verify GREEN**

```bash
./server/gradlew -p server :layoutlib-worker:test --tests "*SharedRendererBindingTest"
```
Expected: 3 tests PASS.

- [ ] **Step 7.5: Full layoutlib-worker unit suite regression**

```bash
./server/gradlew -p server :layoutlib-worker:test
```
Expected: Phase 1 의 +8 + Phase 2 의 +3 = +11 new tests. Regression 없음.

---

## Task 8: SharedLayoutlibRenderer (singleton) + integration test

**Files:**
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt`

- [ ] **Step 8.1: Write failing integration tests (3 cases)**

Create `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt`:

```kotlin
package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

/**
 * W3D2 integration cleanup — SharedLayoutlibRenderer 의 per-JVM-fork 싱글톤 동작 검증.
 *
 * 본 test 는 native lib 를 실제 로드한다 (LayoutlibRenderer 생성). 따라서 @Tag("integration").
 * Gradle `forkEvery(1L)` (`server/layoutlib-worker/build.gradle.kts:60-65`) 덕에 이 test
 * class 는 독립 JVM fork 에서 실행되고, 그 fork 안의 3 @Test 는 순차 실행되며 singleton
 * 상태를 공유한다. 다른 integration test class (Tier3MinimalTest 등) 는 별도 JVM fork
 * 에서 실행되므로 cross-class state leakage 없음.
 */
@Tag("integration")
class SharedLayoutlibRendererIntegrationTest {

    @Test
    fun `첫 getOrCreate 시 LayoutlibRenderer 반환`() {
        val dist = locateDistDir()
        val fixture = locateFixtureRoot()

        val r = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)

        assertNotNull(r)
    }

    @Test
    fun `같은 args 로 재호출 시 동일 인스턴스 반환`() {
        val dist = locateDistDir()
        val fixture = locateFixtureRoot()
        val r1 = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)

        val r2 = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)

        assertSame(r1, r2, "같은 args 는 동일 인스턴스여야 함 (referential equality)")
    }

    @Test
    fun `다른 args 로 호출 시 IllegalStateException`() {
        val dist = locateDistDir()
        val fixture = locateFixtureRoot()
        // bound 상태 확보 — 첫 getOrCreate.
        SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)

        val differentFixture = fixture.resolveSibling("different")

        val ex = assertThrows<IllegalStateException> {
            SharedLayoutlibRenderer.getOrCreate(dist, differentFixture, fallback = null)
        }
        assertTrue(
            ex.message!!.contains("불일치"),
            "메시지: ${ex.message}",
        )
    }

    private fun locateDistDir(): Path {
        val found = DistDiscovery.locate(null)
        assumeTrue(found != null, "dist 없음 — W1D3-R2 다운로드를 먼저 수행")
        return found!!.toAbsolutePath().normalize()
    }

    private fun locateFixtureRoot(): Path {
        val found = FixtureDiscovery.locate(null)
        assumeTrue(found != null, "fixture 없음 — fixture/sample-app 확인")
        return found!!.toAbsolutePath().normalize()
    }
}
```

- [ ] **Step 8.2: Run tests — verify RED**

```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "*SharedLayoutlibRendererIntegrationTest"
```
Expected: `unresolved reference: SharedLayoutlibRenderer`.

- [ ] **Step 8.3: Create SharedLayoutlibRenderer.kt**

Create `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt`:

```kotlin
package dev.axp.layoutlib.worker

import dev.axp.protocol.render.PngRenderer
import java.nio.file.Path

/**
 * W3D2 integration cleanup (Codex F4 carry) — test-only per-JVM-fork LayoutlibRenderer singleton.
 *
 * **왜 필요한가 (W2D7 L4, in-class scope)**:
 *   `System.load(libandroid_runtime.so)` 는 프로세스당 1회만 허용. 동일 test class 의
 *   여러 @Test 메서드 가 각자 별도 `LayoutlibRenderer` 를 만들면 두 번째부터
 *   `UnsatisfiedLinkError` 가 발생 → LayoutlibBootstrap 이 catch 해서 조용히 삼킴 →
 *   Bridge 의 native 바인딩이 깨진 상태로 render 시도 → 진단 어려운 null 반환.
 *   Gradle `forkEvery(1L)` 가 test class 단위 JVM 격리를 보장하므로 cross-class 는 문제
 *   아니나, in-class 는 여전히 이 singleton 으로 방어 필요.
 *
 * **해결**:
 *   - JVM fork 내에서 유일한 instance.
 *   - bound 된 dist/fixture 와 다른 args 로 호출 시 `IllegalStateException`.
 *
 * **Production 과의 관계**:
 *   Production (`Main.kt.chooseRenderer`) 은 mode 당 1회 `LayoutlibRenderer` 를 생성
 *   하므로 이 singleton 을 사용하지 않는다. 이 object 는 test sourceset 에만 존재.
 *
 * **resetForTestOnly() 미포함 (MF-F1)**: 초기 설계에 있었으나 native lib 는 `System.load`
 * 이후 JVM 종료까지 unload 불가이므로 instance 를 null 로 만들어도 실질적 isolation 효과
 * 없음 → dead API. YAGNI 로 제거. cross-class 격리는 Gradle forkEvery(1L) 이 담당.
 */
object SharedLayoutlibRenderer {
    @Volatile private var instance: LayoutlibRenderer? = null
    @Volatile private var boundArgs: Pair<Path, Path>? = null

    @Synchronized
    fun getOrCreate(
        distDir: Path,
        fixtureRoot: Path,
        fallback: PngRenderer?,
    ): LayoutlibRenderer {
        val requested = distDir to fixtureRoot
        SharedRendererBinding.verify(boundArgs, requested)
        instance?.let { return it }
        val created = LayoutlibRenderer(distDir, fallback, fixtureRoot)
        instance = created
        boundArgs = requested
        return created
    }
}
```

- [ ] **Step 8.4: Run tests — verify GREEN**

```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "*SharedLayoutlibRendererIntegrationTest"
```
Expected: 3 tests PASS.

- [ ] **Step 8.5: Full integration suite regression**

```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
```
Expected: 8 PASS (기존) + 3 SharedLayoutlibRendererIntegrationTest = **11 PASS** + 2 SKIPPED (tier3-glyph, LayoutlibRendererIntegrationTest). tier3-arch / tier3-values 는 still OK (아직 Tier3 가 SharedLayoutlibRenderer 로 전환 전이나, 같은 JVM 에서 돌더라도 same-args 이라 conflict 없음).

---

## Task 9: Tier3MinimalTest → SharedLayoutlibRenderer 전환

**Files:**
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt`

- [ ] **Step 9.1: Tier3MinimalTest companion 제거 + renderer() 는 SharedLayoutlibRenderer 직접 호출**

Replace the `companion object` (lines 37-65) of `LayoutlibRendererTier3MinimalTest.kt` with:

```kotlin
    companion object {
        /**
         * W3D2 cleanup: native lib JVM-wide single-load 이슈 (L4) 는 SharedLayoutlibRenderer
         * 로 해결. 기존 `sharedRenderer` 필드 + synchronized factory 는 그 object 로 이동.
         */
        private fun renderer(): LayoutlibRenderer {
            val dist = DistDiscovery.locate(null)
                ?: run {
                    // CI 환경 변수에 dist 없을 수 있음 — assumeTrue 로 skip.
                    org.junit.jupiter.api.Assumptions.assumeTrue(
                        false, "dist 없음 — W1D3-R2 다운로드를 먼저 수행",
                    )
                    error("unreachable")
                }
            val fixture = FixtureDiscovery.locate(null)
                ?: error("fixture 없음 — fixture/sample-app 확인")
            return SharedLayoutlibRenderer.getOrCreate(
                distDir = dist.toAbsolutePath().normalize(),
                fixtureRoot = fixture.toAbsolutePath().normalize(),
                fallback = null,
            )
        }
    }
```

또한 파일 상단 import 에서 제거:
```kotlin
import org.junit.jupiter.api.Assumptions.assumeTrue   // 제거 (필요 없음 — 위 내부에서 FQN 사용)
import java.nio.file.Paths                              // 제거
import kotlin.io.path.exists                            // 제거
import kotlin.io.path.isDirectory                       // 제거
```
그리고 `java.nio.file.Path` import 도 더 이상 직접 쓰이지 않으면 제거. IDE/컴파일러 경고 확인.

- [ ] **Step 9.2: 컴파일 + Tier3 integration test 실행**

```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "*LayoutlibRendererTier3MinimalTest"
```
Expected: tier3-arch PASS, tier3-values PASS, tier3-glyph SKIPPED (여전히 `@Disabled`).

- [ ] **Step 9.3: Full integration suite — 병렬 실행 회귀 검증**

```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
```
Expected: 11 PASS + 2 SKIPPED. **주의 (MF-B3 반영)**: Gradle `setForkEvery(1L)` (`server/layoutlib-worker/build.gradle.kts:60-65`) 로 `SharedLayoutlibRendererIntegrationTest` 와 `Tier3MinimalTest` 는 **별도 JVM fork** 에서 실행된다. 따라서 두 클래스가 singleton 을 공유하지 않으며, cross-class 간 state leakage / conflict 는 구조적으로 발생할 수 없다. singleton 은 각 클래스의 JVM fork 내부 @Test 메서드 간 공유될 뿐이다.

---

## Task 10: LayoutlibRendererIntegrationTest → @Disabled + SharedLayoutlibRenderer

**Files:**
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`

- [ ] **Step 10.1: 파일 전체 재작성 — @Disabled + SharedLayoutlibRenderer 호출부 유지 (로직은 never run)**

Replace the ENTIRE contents of `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` with:

```kotlin
package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Tier3 pre-canonical integration test — activity_basic.xml (ConstraintLayout / MaterialButton 포함)
 * 의 full render 를 기대.
 *
 * W2D6 시점에는 `assumeTrue(false)` 로 L4 masking + L3 (custom view 미지원) 을 모두 skip
 * 으로 처리했었다. W3D2 cleanup 에서 L4 는 SharedLayoutlibRenderer 로 해결됐으나 L3
 * (MinimalLayoutlibCallback.loadView 가 ConstraintLayout/MaterialButton 을 reject) 는
 * 여전히 남아있어 render 실패가 예측된다. 따라서 `@Disabled` annotation 으로 명시적
 * skip — W3 sample-app unblock (DexClassLoader) 이후 enable 예정.
 *
 * 호출부는 **SharedLayoutlibRenderer** 를 사용하도록 유지해, @Disabled 가 풀렸을 때
 * L4 regression 재발하지 않도록 설계.
 */
@Tag("integration")
@Disabled("W3 sample-app unblock 필요 — L3 DexClassLoader carry (ConstraintLayout / MaterialButton)")
class LayoutlibRendererIntegrationTest {

    @Test
    fun `tier3 — renderPng returns non-empty PNG bytes with PNG magic header`() {
        val dist = locateDistDir()
        val fixture = locateFixtureRoot()
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = fixture,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_basic.xml")

        assertTrue(bytes.size > 1000, "PNG bytes 가 placeholder 보다 큰 실 이미지여야 함: ${bytes.size}")
        // PNG magic: 0x89 0x50 0x4E 0x47
        assertTrue(
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
            "PNG magic 헤더가 아님",
        )
    }

    private fun locateDistDir(): Path {
        val found = DistDiscovery.locate(null)
        assumeTrue(found != null, "dist 없음 — W1D3-R2 다운로드를 먼저 수행")
        return found!!.toAbsolutePath().normalize()
    }

    private fun locateFixtureRoot(): Path {
        val found = FixtureDiscovery.locate(null)
        assumeTrue(found != null, "fixture 없음 — fixture/sample-app 확인")
        return found!!.toAbsolutePath().normalize()
    }
}
```

- [ ] **Step 10.2: 컴파일 확인**

```bash
./server/gradlew -p server :layoutlib-worker:compileTestKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10.3: Integration test — @Disabled 로 SKIPPED 기록 확인**

```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "*LayoutlibRendererIntegrationTest"
```
Expected: 1 SKIPPED ("W3 sample-app unblock 필요 — ...").

---

## Task 11: Phase 2 full gate + work_log journaling

**Files:**
- Create: `docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md`
- Create: `docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md`
- Create: `docs/work_log/2026-04-24_w3d2-integration-cleanup/next-session-prompt.md`

- [ ] **Step 11.1: 전체 unit test gate**

```bash
./server/gradlew -p server test
```
Expected: **117 unit PASS** (99 + DistDiscovery 4 + FixtureDiscovery 4 + CliArgs 7 + SharedRendererBinding 3).

- [ ] **Step 11.2: 전체 integration test gate**

```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
```
Expected: **11 PASS + 2 SKIPPED** (기존 8 + SharedLayoutlibRendererIntegrationTest 3 = 11 PASS, tier3-glyph + LayoutlibRendererIntegrationTest = 2 SKIPPED).

- [ ] **Step 11.3: 전체 빌드 (fatJar 포함)**

```bash
./server/gradlew -p server build
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11.4: Production CLI sanity — override 동작 확인 (MF-B4: fail-fast)**

```bash
# Smoke (기존)
./server/mcp-server/build/install/mcp-server/bin/mcp-server --smoke
# Expected: stdout "ok", exit 0.

# --dist-dir= (빈 value) → CliArgs.parse 가 IllegalArgumentException (fail-fast)
./server/mcp-server/build/install/mcp-server/bin/mcp-server --dist-dir= --smoke || echo "exited non-zero (expected)"
# Expected: IllegalArgumentException stacktrace to stderr + non-zero exit.

# --dist-dir (= 없이) → CliArgs.parse 가 IllegalArgumentException (MF-B2)
./server/mcp-server/build/install/mcp-server/bin/mcp-server --dist-dir --smoke || echo "exited non-zero (expected)"
# Expected: IllegalArgumentException ("값을 받는 옵션") + non-zero exit.

# --dist-dir=/nonexistent → DistDiscovery.locate 가 IllegalArgumentException (spec §11.6 fail-fast)
./server/mcp-server/build/install/mcp-server/bin/mcp-server --dist-dir=/nonexistent --http-server || echo "exited non-zero (expected)"
# Expected: IllegalArgumentException stacktrace ("dist 경로가 디렉토리가 아님...") + non-zero exit.
# chooseRenderer 의 try/catch 는 LayoutlibRenderer 생성자만 감쌀 뿐 DistDiscovery.locate 의
# require() 는 catch 하지 않아 main() 으로 propagate → JVM exit (fail-fast).

# Valid override — fatJar 환경에서 실제 경로 제공 시 정상 부팅
timeout 3 ./server/mcp-server/build/install/mcp-server/bin/mcp-server \
    --dist-dir="$PWD/server/libs/layoutlib-dist/android-34" \
    --fixture-root="$PWD/fixture/sample-app/app/src/main/res/layout" \
    --http-server || true
# Expected: stderr 에 "axp viewer ready: http://localhost:7321/ (renderer=LayoutlibRenderer)"
# (timeout 으로 3초 후 kill — HTTP 서버 blockUntilShutdown).
```

- [ ] **Step 11.5: grep 기반 success criteria 확인**

```bash
# (1) Main.kt 에 하드코딩된 layoutlib-dist/android-34 없음
grep -rn "layoutlib-dist/android-34" server/mcp-server/src/main/
# Expected: 결과 없음

# (2) LayoutlibRenderer 에 defaultFixtureRoot 없음 (MF-F3: directory 타겟이라 -r)
grep -rn "defaultFixtureRoot" server/layoutlib-worker/src/main/
# Expected: 결과 없음

# (3) LayoutlibRenderer 생성자 default 2개 제거됨
grep -En '^\s*private val (fallback|fixtureRoot).* = ' server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt
# Expected: 결과 없음
```

- [ ] **Step 11.6: work_log/session-log.md 작성**

Create `docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md`:

```markdown
# W3D2 Session Log — INTEGRATION-CLEANUP (C1+C2+C3 carry CLOSED)

**Date**: 2026-04-24 (W3 Day 2)
**Canonical ref**: `docs/W3D1-PAIR-REVIEW.md §3 Deferred carry`.
**Outcome**: LayoutlibRenderer 생성자 default 2개 제거 + CLI override 인자 추가 + `assumeTrue(false)` masking 제거 + in-class L4 회피 singleton 도입. 99→117 unit + 8→11 integration PASS.

---

## 1. 작업 범위

W3D1 pair-review deferred carry 3 개를 일괄 처리.

## 2. 변경 파일

### Phase 1 (production 4 + test 3)
- `server/layoutlib-worker/.../DistDiscovery.kt` (신규)
- `server/layoutlib-worker/.../FixtureDiscovery.kt` (신규)
- `server/mcp-server/.../CliConstants.kt` (신규)
- `server/mcp-server/.../CliArgs.kt` (신규)
- `server/layoutlib-worker/src/test/.../DistDiscoveryTest.kt` (신규, 4 tests)
- `server/layoutlib-worker/src/test/.../FixtureDiscoveryTest.kt` (신규, 4 tests)
- `server/mcp-server/src/test/.../CliArgsTest.kt` (신규, 5 tests)
- `server/layoutlib-worker/.../LayoutlibRenderer.kt` (수정 — default 2개 제거, defaultFixtureRoot() 삭제)
- `server/mcp-server/.../Main.kt` (수정 — CliArgs 파싱 + chooseRenderer 재배선)

### Phase 2 (test 4)
- `server/layoutlib-worker/src/test/.../SharedRendererBinding.kt` (신규)
- `server/layoutlib-worker/src/test/.../SharedLayoutlibRenderer.kt` (신규)
- `server/layoutlib-worker/src/test/.../SharedRendererBindingTest.kt` (신규, 3 tests)
- `server/layoutlib-worker/src/test/.../SharedLayoutlibRendererIntegrationTest.kt` (신규, 3 integ tests)
- `server/layoutlib-worker/src/test/.../LayoutlibRendererTier3MinimalTest.kt` (수정 — companion 재작성, SharedLayoutlibRenderer 호출)
- `server/layoutlib-worker/src/test/.../LayoutlibRendererIntegrationTest.kt` (수정 — @Disabled + SharedLayoutlibRenderer 호출)

## 3. 테스트 결과

| 카테고리 | 전 (W3D1) | 후 (W3D2) | 변화 |
|---|---|---|---|
| Unit | 99 PASS | **117 PASS** | +18 (DistDiscovery 4 + FixtureDiscovery 4 + CliArgs 7 + SharedRendererBinding 3) |
| Integration PASS | 8 | **11** | +3 (SharedLayoutlibRendererIntegrationTest) |
| Integration SKIPPED | 2 | **2** | tier3-glyph + LayoutlibRendererIntegrationTest@Disabled |
| BUILD | SUCCESSFUL | SUCCESSFUL | — |

## 4. 발견된 landmine

(실제 실행 후 기입. 예상 LM-A ~ LM-E 는 spec §10 참조.)

## 5. 페어 리뷰 결과

- Plan pair-review (Claude+Codex, 1:1, pre-execution): 결과 기입.
- Implementation 은 Claude-only (CLAUDE.md policy).

## 6. 다음 세션 carry

- **sample-app unblock** (ConstraintLayout / MaterialButton + DexClassLoader L3) — W3 Day 3+ 스코프.
- **tier3-glyph** (Font wiring + StaticLayout + Canvas.drawText) — W4 carry.
- **POST-W2D6-POM-RESOLVE** (F-6) — 기존.
- **W3-CLASSLOADER-AUDIT** (F-4) — 기존.
```

- [ ] **Step 11.7: handoff.md 작성**

Create `docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md`:

```markdown
# W3D2 → W3D3+ Handoff

## 상태 요약 (2026-04-24 종료 시점)

- W3D1 pair-review deferred carry 3 개 (C1 default 제거, C2 CLI-DIST-PATH, C3 TEST-INFRA-SHARED-RENDERER) 모두 CLOSED.
- Unit 117 + Integration 11 PASS + 2 SKIPPED (tier3-glyph, LayoutlibRendererIntegrationTest@Disabled).
- LayoutlibRenderer 생성자 default parameter 0 — CLAUDE.md "No default parameter values" 완전 준수.
- Main.kt 가 `--dist-dir=<path>` / `--fixture-root=<path>` CLI override 지원 (optional, auto-detect fallback).
- SharedLayoutlibRenderer 싱글톤이 L4 (native lib JVM-wide single-load) 를 원천 차단.

## 1턴에 읽어야 할 문서

1. `docs/plan/08-integration-reconciliation.md` — 전체 로드맵 (W3D2 는 7.7.3 이후 추가 섹션 고려).
2. `docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md` — 본 세션 상세.
3. `docs/superpowers/specs/2026-04-24-w3d2-integration-cleanup-design.md` — W3D2 설계.
4. `docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md` — 본 플랜 (모든 Task 체크박스 닫힘 확인).

## 다음 세션 작업 옵션

### Option A (권장) — sample-app unblock (W3 본론)

- `fixture/sample-app/app/src/main/res/layout/activity_basic.xml` 의 ConstraintLayout / MaterialButton 등 커스텀 뷰 지원.
- 핵심 구현: DexClassLoader 로 Android support/material artifact (AAR) 로부터 클래스 로드.
- `MinimalLayoutlibCallback.loadView` 를 확장해 위임.
- LayoutlibRendererIntegrationTest 를 `@Disabled` 해제 후 tier3-values 패턴으로 검증.

### Option B — tier3-glyph (W4 target)

- Font wiring + StaticLayout + Canvas.drawText JNI 경로 증명.
- T2 gate (activity_minimal TextView 영역 dark pixel >= 20) unblock.

### Option C — POST-W2D6-POM-RESOLVE / W3-CLASSLOADER-AUDIT (기존 carry)

## 긴급 회복 (빌드가 깨졌을 때)

1. `git` 관리가 없으므로 수동 rollback 필요. 본 세션의 신규 파일을 rm + 수정 파일을 W3D1 상태 (`session-log §2`) 로 복구.
2. `./server/gradlew -p server test` 로 W3D1 state (99 unit + 8 integration PASS + 2 SKIPPED) 복귀 확인.

## 주의 landmine 재발 방지

- **L6** Codex CLI trust-check hang — non-git 프로젝트에서 항상 `--skip-git-repo-check` 플래그.
- LM-F (신규 handoff carry) — `SharedLayoutlibRenderer` 에 `resetForTestOnly()` 를 추가하지 말 것. 이전 설계에서 고려됐으나 native lib unload 불가 로 의미 없는 dead API — MF-F1 로 제외됨.
- LM-D (spec §10) — `@Disabled` 는 `@Tag("integration")` 과 독립이라 tag gating 결과 카운트에 SKIPPED 로 반영.
```

- [ ] **Step 11.8: next-session-prompt.md 작성**

Create `docs/work_log/2026-04-24_w3d2-integration-cleanup/next-session-prompt.md`:

```markdown
# W3D3 Next-Session Prompt

다음 세션 시작 시 아래 프롬프트 중 하나 복사.

---

## Option A (권장) — sample-app unblock

```
docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md 읽고 Option A 경로로 진행.

sample-app (activity_basic.xml) 의 ConstraintLayout / MaterialButton 등 커스텀 뷰
지원을 위한 DexClassLoader 기반 L3 class loading 을 구현한다. 계약 파일:
- MinimalLayoutlibCallback.loadView 확장.
- AAR 아티팩트 (material / constraintlayout) 파싱 → dex / jar classloader.
- LayoutlibRendererIntegrationTest @Disabled 해제 + tier3-values 패턴 assertion.

에이전트 팀과 스킬 적극 활용. CLAUDE.md 규약 준수. Codex 호출 시 --skip-git-repo-check.
```

## Option B — tier3-glyph

```
docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md Option B 경로.

tier3-glyph unblock: Font wiring + StaticLayout + Canvas.drawText JNI 증명.
T2 gate (dark pixel >= 20) 통과가 목표.
```

---

## 공통 시작 체크리스트

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
./server/gradlew -p server test                                    # 117 unit PASS 확인
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration   # 11 + 2 SKIPPED 확인
./server/gradlew -p server build                                   # BUILD SUCCESSFUL 확인
```

모두 green 이어야 시작. 실패 시 handoff.md §"긴급 회복" 참조.
```

- [ ] **Step 11.9: 최종 sanity**

```bash
./server/gradlew -p server test && \
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration && \
./server/gradlew -p server build
```
Expected: 3 gates 모두 `BUILD SUCCESSFUL`. W3D2 완료.

---

## Self-review 완료 체크리스트

- [ ] 모든 신규 파일이 위 File Structure 에 명시된 LOC 예상 범위 (±30%) 내에 있음.
- [ ] 모든 `assertEquals` / `assertThrows` 인자 순서가 JUnit 5 convention (expected, actual) 을 따름.
- [ ] `internal` visibility 가 같은 Gradle 모듈 내에서만 접근 가능함을 확인 (모듈 경계를 넘는 호출 없음).
- [ ] L6 (non-git) 준수 — commit step 없음.
- [ ] 각 Task 마지막에 gradle gate 포함.
- [ ] Phase 경계 (Task 6, Task 11) 에서 full suite + build + grep 확인.

---

END.
