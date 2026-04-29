# W3D2-INTEGRATION-CLEANUP — Design Spec

**Date**: 2026-04-24 (W3 Day 2)
**Scope reference**: `docs/W3D1-PAIR-REVIEW.md §3 Deferred carry` (Codex F3 + F4 + CLAUDE.md "No default parameter values" 잔재)
**Session ancestry**: `docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md §"다음 세션 작업 옵션 — Option A"`
**Brainstorming scope choice**: Q1=B (optional CLI override + auto-detect fallback), Q2=C (top-level singleton object), 실행 전략=A (phase-bundled, C1+C2 / C3)

---

## 1. Problem

W3D1 pair-review 에서 **3개의 deferred carry** 가 식별되었으나 동일 세션에서 처리하지 않고 이월했다.

1. **C1 — LayoutlibRenderer default parameter 잔재**: `LayoutlibRenderer.kt:44-47` 에서 `fallback: PngRenderer? = null, fixtureRoot: Path = defaultFixtureRoot()` 2개의 default 가 CLAUDE.md "No default parameter values" 규약을 위반한다. W3D1 MF2 로 `SessionParamsFactory.callback` default 만 제거했고, `LayoutlibRenderer` 쪽은 production 사용처 (AxpServer, MCP `Main.kt`) 리팩토링 비용 때문에 carry 로 남겼다.
2. **C2 — CLI-DIST-PATH (Codex F3)**: `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:111-116` 의 `Paths.get("server/libs/layoutlib-dist/android-34")` 등 3개 후보가 하드코딩이다. fixture root 도 `LayoutlibRenderer.defaultFixtureRoot()` 안에 4개 후보가 하드코딩되어 있다. 배포 환경 (fatJar standalone, CI, 로컬) 마다 다른 경로를 지원하려면 CLI 인자 override 가 필수다.
3. **C3 — TEST-INFRA-SHARED-RENDERER (Codex F4)**: W2D7 L4 landmine (native lib 프로세스 당 1회 로드) 은 *같은 test class 내 여러 @Test 메서드* 가 동일 JVM 에서 실행될 때 문제가 된다. `LayoutlibRendererTier3MinimalTest.companion object` 에 `sharedRenderer` 패턴으로 이를 막아뒀다. 다른 integration test class (`LayoutlibRendererIntegrationTest.kt:26`) 는 Gradle `forkEvery(1L)` (`server/layoutlib-worker/build.gradle.kts:60-65`) 덕에 **독립 JVM fork** 에서 실행되므로 cross-class 의 native lib 충돌은 없다. 그러나 동일 클래스 내 `LayoutlibRenderer(dist)` 를 직접 호출하는 일관성 부재 + `assumeTrue(false)` 의 throwable catch 가 실패를 모두 skip 로 전환해 커버리지 손실을 가리는 두 문제는 남아 있다. 이 carry 는 **각 JVM fork 내부** 에서 일관된 singleton 패턴 + 명시적 `@Disabled` 로 두 문제를 정돈한다.

본 spec 은 위 3 carry 를 2 phase 로 묶어 처리하는 설계다. `sample-app` unblock (ConstraintLayout / MaterialButton / DexClassLoader) 같은 기능 확장은 **본 스코프에 포함하지 않는다** — 독립 carry 로 후속 세션 처리.

---

## 2. Scope

### 2.1 In scope

- LayoutlibRenderer 생성자에서 default 2개 제거 (`fallback`, `fixtureRoot`).
- Main.kt 에 `--dist-dir=<path>`, `--fixture-root=<path>` CLI 인자 추가 (optional, override + auto-detect fallback).
- 하드코딩된 path literal 의 constant / discovery object 분리:
  - `DistDiscovery` (신규 object, `layoutlib-worker` 모듈): `LAYOUTLIB_DIST_SUBDIR`, `CANDIDATE_ROOTS`, `locate(override: Path?): Path?`.
  - `FixtureDiscovery` (신규 object, `layoutlib-worker` 모듈): `FIXTURE_SUBPATH`, `CANDIDATE_ROOTS`, `locate(override: Path?): Path?`.
  - `CliConstants` (신규 object, `mcp-server` 모듈): `DIST_DIR_FLAG`, `FIXTURE_ROOT_FLAG`, `ARG_SEPARATOR`, `VALUE_BEARING_FLAGS`.
  - `CliArgs` (신규 `internal` data class, `mcp-server` 모듈): `parse(args, valueBearingFlags)` companion + `flags`/`values` 접근자. value-bearing flag 이 값 없이 (`--dist-dir` 단독) 입력되면 throw.
- `SharedLayoutlibRenderer` 신규 test-전용 singleton (`layoutlib-worker` test sourceset) — **각 JVM fork 내부** 에서 native lib 를 1회 로드로 보장 (Gradle `forkEvery(1L)` 덕에 integration test class 마다 별도 fork, fork 간 격리).
- `SharedRendererBinding` pure helper object — args 일관성 검증 (unit-test 가능).
- `LayoutlibRendererIntegrationTest` 의 `assumeTrue(false)` masking 을 `@Disabled("W3 sample-app unblock — L3 DexClassLoader carry")` 로 전환.

### 2.2 Out of scope (carry)

- `sample-app` unblock (ConstraintLayout, MaterialButton, DexClassLoader L3).
- 환경변수 기반 path override (`AXP_DIST_DIR` 등) — 배포 단계 carry.
- tier3-glyph (font wiring + StaticLayout + Canvas.drawText JNI) — W4 carry.
- JUnit 5 Extension 기반 test-infra 일반화 — 현재 singleton 로 충분.

---

## 3. Architecture

### 3.1 모듈 간 의존성 (변경 없음)

```
protocol (순수 schema)
   ↑
render-core (PngRenderer interface + Placeholder)
   ↑
layoutlib-worker (LayoutlibRenderer + 신규 Dist/FixtureDiscovery + test-only SharedLayoutlibRenderer)
   ↑
http (HTTP server)
   ↑
mcp-server (Main.kt + 신규 CliConstants)
```

### 3.2 신규 프로덕션 + 테스트 support 파일 (6개)

| 파일 | Phase | 역할 |
|---|---|---|
| `layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/DistDiscovery.kt` | 1 | `object DistDiscovery`: `LAYOUTLIB_DIST_SUBDIR`, `CANDIDATE_ROOTS`, `locate(override): Path?`. override 제공 시 존재 검증 + 명시 경로 반환 (비존재 시 throw), 없으면 candidates 순차 탐색. |
| `layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt` | 1 | `object FixtureDiscovery`: 동일 패턴. `FIXTURE_SUBPATH` + candidates. |
| `mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt` | 1 | `object CliConstants`: flag 이름 + 구분자 상수. `StdioConstants` 와 분리 (concerns). |
| `mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt` | 1 | `internal data class CliArgs` + `companion object parse(args): CliArgs`. Main.kt 에서 use, test 에서 `internal` 가시성으로 접근. |
| `layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt` | 2 | `object SharedLayoutlibRenderer`: `@Volatile instance`, `getOrCreate(distDir, fixtureRoot, fallback)` synchronized singleton. |
| `layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt` | 2 | `internal object SharedRendererBinding`: `verify(bound, requested)` pure helper — unit test 용이성 목적. |

### 3.3 수정 파일 (5개)

| 파일 | Phase | 변경 |
|---|---|---|
| `layoutlib-worker/.../LayoutlibRenderer.kt` | 1 | 생성자 default 2개 (`fallback`, `fixtureRoot`) 제거. `companion.defaultFixtureRoot()` helper 삭제 (책임을 `FixtureDiscovery` 로 이관). |
| `mcp-server/.../Main.kt` | 1 | `main(args)` 에서 `CliArgs.parse(args)` → `chooseRenderer(parsed)` 재배선. `Paths.get("server/libs/layoutlib-dist/android-34")` 등 하드코딩 제거. `runStdioMode` / `runHttpMode` 가 `CliArgs` 를 파라미터로 받도록 시그니처 확장. |
| `layoutlib-worker/src/test/.../LayoutlibRendererTier3MinimalTest.kt` | 1 → 2 | Phase 1: `LayoutlibRenderer(dist)` → `LayoutlibRenderer(dist, null, FixtureDiscovery.locate(null)!!)` 임시. Phase 2: → `SharedLayoutlibRenderer.getOrCreate(...)`. `companion.sharedRenderer` helper 제거. |
| `layoutlib-worker/src/test/.../LayoutlibRendererIntegrationTest.kt` | 2 | `assumeTrue(false)` + try/catch 제거, `@Disabled(reason)` 으로 전환. `SharedLayoutlibRenderer.getOrCreate` 사용. |
| `mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt` (신규) | 1 | `CliArgs.parse` 단위 테스트. |

**신규 테스트 파일 (4개)**:

| 파일 | Phase |
|---|---|
| `layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/DistDiscoveryTest.kt` | 1 |
| `layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt` | 1 |
| `layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt` | 2 |
| `layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt` | 2 |

`CliArgsTest.kt` 는 위 수정 파일 목록에 포함.

---

## 4. Component design — Phase 1

### 4.1 `DistDiscovery`

```kotlin
package dev.axp.layoutlib.worker

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DistDiscovery {
    const val LAYOUTLIB_DIST_SUBDIR = "layoutlib-dist/android-34"

    // cwd 기준 후보 접두. 빈 문자열 = cwd, "../" 등은 모듈 sub-cwd 에서 실행 시.
    val CANDIDATE_ROOTS: List<String> = listOf(
        "server/libs",
        "../libs",
    )

    /**
     * override 제공 시 해당 경로가 존재하고 디렉토리인지 검증 후 반환.
     * override 미제공 시 CANDIDATE_ROOTS 순차 탐색, 첫 매칭 반환. 모두 실패 시 null.
     * override 가 있으나 존재하지 않으면 IllegalArgumentException (fail-fast).
     */
    fun locate(override: Path?): Path? {
        if (override != null) {
            require(Files.isDirectory(override)) {
                "dist 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
            }
            return override
        }
        val userDir = System.getProperty("user.dir")
        val candidates: List<Path> = CANDIDATE_ROOTS.flatMap { root ->
            listOf(
                Paths.get(root, LAYOUTLIB_DIST_SUBDIR),
                Paths.get(userDir, root, LAYOUTLIB_DIST_SUBDIR),
            )
        }
        return candidates.firstOrNull { Files.isDirectory(it) }
    }
}
```

**모듈 의존성 주의**: `DistDiscovery` 는 `layoutlib-worker` 모듈에 위치하므로 `mcp-server` 의 `CliConstants` 를 참조할 수 없다 (역방향 의존). 에러 메시지에서 flag 이름 (`--dist-dir`) 을 언급하지 않고 "dist 경로" 같은 중립적 표현 사용. CLI 관점의 에러 메시지는 `Main.kt` 의 상위 `try/catch` 에서 flag 이름과 함께 재포장 가능 (필요 시).

### 4.2 `FixtureDiscovery`

```kotlin
package dev.axp.layoutlib.worker

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object FixtureDiscovery {
    const val FIXTURE_SUBPATH = "fixture/sample-app/app/src/main/res/layout"

    val CANDIDATE_ROOTS: List<String> = listOf(
        "",           // cwd
        "..",         // server/layoutlib-worker 에서 실행 시 → server/
        "../..",      // server/ 에서 실행 시 → repo root
    )

    fun locate(override: Path?): Path? {
        if (override != null) {
            require(Files.isDirectory(override)) {
                "fixture root 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
            }
            return override
        }
        val userDir = System.getProperty("user.dir")
        val candidates: List<Path> = CANDIDATE_ROOTS.flatMap { root ->
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

### 4.3 `CliConstants`

```kotlin
package dev.axp.mcp

object CliConstants {
    const val DIST_DIR_FLAG = "--dist-dir"
    const val FIXTURE_ROOT_FLAG = "--fixture-root"
    const val ARG_SEPARATOR = "="
    const val USAGE_LINE =
        "Usage: axp-server [--dist-dir=<path>] [--fixture-root=<path>] [--smoke|--stdio|--http-server]"

    /**
     * 값을 반드시 `=<value>` 로 받아야 하는 flag 목록. parse 시 value 없이 들어오면 throw.
     * 예: `--dist-dir` (단독) → IllegalArgumentException.
     */
    val VALUE_BEARING_FLAGS: Set<String> = setOf(DIST_DIR_FLAG, FIXTURE_ROOT_FLAG)
}
```

### 4.4 `CliArgs` 파싱 헬퍼 (별도 파일, `internal` 가시성)

`mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt`:

```kotlin
package dev.axp.mcp

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
                    // value-bearing flag 인데 `=` 없이 온 경우 → throw (MF-B2).
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

`internal` 가시성은 같은 Gradle 모듈 (`mcp-server`) 내에서 접근 가능. `mcp-server/src/test/.../CliArgsTest.kt` 가 동일 모듈이므로 자유롭게 테스트 가능. Caller (`Main.kt`) 는 `CliConstants.VALUE_BEARING_FLAGS` 를 전달.

### 4.5 `LayoutlibRenderer` 수정

```kotlin
class LayoutlibRenderer(
    private val distDir: Path,
    private val fallback: PngRenderer?,    // default 제거 (nullable 유지)
    private val fixtureRoot: Path,          // default 제거
) : PngRenderer {
    // companion.defaultFixtureRoot() → 제거 (FixtureDiscovery.locate 로 이관)
}
```

`fallback: PngRenderer?` 의 nullable 유지 — "fallback 없음" 은 의도가 명확한 상태 (render 실패 시 throw). non-null 로 강제하면 caller 가 항상 NullPngRenderer 류 sentinel 을 만들어야 해서 오히려 boilerplate 증가.

### 4.6 `Main.kt` 리팩토링

```kotlin
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

private fun chooseRenderer(parsed: CliArgs): PngRenderer {
    val distOverride = parsed.valueOf(CliConstants.DIST_DIR_FLAG)?.let { Paths.get(it) }
    val fixtureOverride = parsed.valueOf(CliConstants.FIXTURE_ROOT_FLAG)?.let { Paths.get(it) }

    val dist = DistDiscovery.locate(distOverride)
    val fixture = FixtureDiscovery.locate(fixtureOverride)

    if (dist == null || fixture == null) {
        System.err.println("axp: layoutlib dist 또는 fixture 탐지 실패 → placeholder PNG 로 fallback")
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
```

---

## 5. Component design — Phase 2

### 5.1 `SharedRendererBinding` (pure helper)

```kotlin
package dev.axp.layoutlib.worker

import java.nio.file.Path

internal object SharedRendererBinding {
    /**
     * 첫 바인드 (bound=null) 이면 통과. 두 번째 이후는 bound 와 requested 가 동일해야 함.
     * 불일치 시 IllegalStateException — L4 native lib 일관성 보존.
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

### 5.2 `SharedLayoutlibRenderer` (singleton)

```kotlin
package dev.axp.layoutlib.worker

import dev.axp.protocol.render.PngRenderer
import java.nio.file.Path

/**
 * Test-only singleton. **각 JVM fork 내부** 에서 유일 인스턴스. Gradle `forkEvery(1L)`
 * (`server/layoutlib-worker/build.gradle.kts:60-65`) 덕에 integration test class 마다
 * 별도 fork 가 생성되므로, 본 object 의 static 상태도 fork 경계에서 자연히 리셋된다.
 * 따라서 cross-fork 재사용은 설계상 불가능하며 그럴 필요도 없다.
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

**`resetForTestOnly()` 미포함 근거**: 초기 설계에서 고려했으나 native lib 는 `System.load` 이후 JVM 종료까지 언로드 불가하므로 instance 필드를 null 로 초기화해도 실질적 isolation 효과가 없다. dead API 방지 (YAGNI) 를 위해 제외. cross-class isolation 은 Gradle `forkEvery(1L)` 로 이미 보장됨.

### 5.3 기존 test 호출부 전환

- `LayoutlibRendererTier3MinimalTest.companion.sharedRenderer` / `renderer()` factory 제거.
- 각 `@Test` 는 `SharedLayoutlibRenderer.getOrCreate(locateDistDir(), FixtureDiscovery.locate(null)!!, null)` 로 얻은 인스턴스 사용.
- `LayoutlibRendererIntegrationTest` 는 `@Disabled("W3 sample-app unblock 필요 — L3 DexClassLoader carry")` annotation + `SharedLayoutlibRenderer.getOrCreate` 호출부로 전환 (SKIPPED 로 기록되나 코드 경로는 일관).

---

## 6. Data flow

### 6.1 Production 시작 (stdio mode, override 제공)

```
axp-server.jar --dist-dir=/opt/axp/layoutlib --fixture-root=/opt/axp/fixtures --stdio
  ↓ main(args)
CliArgs.parse(args)
  → flags={"--stdio"}
  → values={"--dist-dir":"/opt/axp/layoutlib","--fixture-root":"/opt/axp/fixtures"}
  ↓
runStdioMode(parsed)
  ↓ chooseRenderer(parsed)
DistDiscovery.locate(Paths.get("/opt/axp/layoutlib"))
  → require(isDirectory) → 반환 (또는 IllegalArgumentException)
FixtureDiscovery.locate(Paths.get("/opt/axp/fixtures"))
  → require(isDirectory) → 반환
  ↓
LayoutlibRenderer(dist=..., fallback=PlaceholderPngRenderer(), fixtureRoot=...)
  ↓
McpStdioServer(handler, stdin, stdout, json).run()
```

### 6.2 Production 시작 (override 미제공)

```
axp-server.jar --stdio
  ↓ CliArgs.parse → values={}
  ↓ chooseRenderer
DistDiscovery.locate(null)
  → CANDIDATE_ROOTS × LAYOUTLIB_DIST_SUBDIR 순차 탐색
  → 첫 isDirectory match 반환 (또는 null)
FixtureDiscovery.locate(null)
  → 동일 패턴
  ↓
(둘 다 성공 시) LayoutlibRenderer 인스턴스화
(하나라도 null) PlaceholderPngRenderer fallback
```

### 6.3 Integration test 경로 (Phase 2 후)

```
@Test tier3-values
  ↓ SharedLayoutlibRenderer.getOrCreate(dist, fixture, null)
  ↓ SharedRendererBinding.verify(boundArgs, requested)
  ↓ instance == null → LayoutlibRenderer 생성 (native lib 1회 로드)
  ↓ boundArgs = (dist, fixture)
  ↓ return instance

@Test tier3-arch (이어서)
  ↓ SharedLayoutlibRenderer.getOrCreate(dist, fixture, null)
  ↓ verify: bound == requested → 통과
  ↓ instance 이미 존재 → 재사용
  ↓ return (같은 인스턴스, native lib 재로드 없음)
```

---

## 7. Error handling

### 7.1 CLI 파싱

| 상황 | 동작 |
|---|---|
| `--dist-dir=/x/y` (존재하지 않음) | `DistDiscovery.locate` 가 `IllegalArgumentException` (fail-fast). |
| `--fixture-root=/a/b` (존재하지 않음) | `FixtureDiscovery.locate` 가 `IllegalArgumentException`. |
| `--dist-dir=` (빈 value) | `CliArgs.parse` 의 `require(value.isNotEmpty())` 가 `IllegalArgumentException` with USAGE_LINE. |
| `--dist-dir` (value-bearing flag 인데 `=` 없이 단독) | `CliArgs.parse` 의 `require(raw !in valueBearingFlags)` 가 `IllegalArgumentException` — "`--dist-dir=<value>` 형식 필요" 메시지. |
| 알 수 없는 플래그 (`--foo`, `--bar=xx`) — `VALUE_BEARING_FLAGS` 밖 | stderr 경고 없이 flags/values 에 저장만 됨, Main.kt 가 lookup 하지 않으면 무시. |
| 비-플래그 위치 인자 | stderr "알 수 없는 인자 무시" 후 진행. |

### 7.2 Renderer 생성

| 상황 | 동작 |
|---|---|
| dist 또는 fixture 둘 중 하나라도 null (auto-detect 실패) | `PlaceholderPngRenderer` fallback. |
| `LayoutlibRenderer` 생성자 내부 throw | 기존 `try/catch (Throwable)` → placeholder fallback. |

### 7.3 Shared singleton

| 상황 | 동작 |
|---|---|
| 같은 args 로 반복 호출 | 같은 인스턴스 반환 (synchronized). |
| 다른 args 로 호출 | `SharedRendererBinding.verify` 가 `IllegalStateException` — 진단 메시지에 bound vs requested 포함. |
| 첫 호출에서 LayoutlibRenderer 생성자 throw | singleton 미할당 유지, 예외 전파. 다음 호출은 재시도 가능하나 native lib 는 이미 부분 로드 상태일 수 있음 (caller 가 skip 처리). |

---

## 8. Testing strategy

### 8.1 신규 unit tests (Phase 1)

| Test class | 케이스 수 | 검증 |
|---|---|---|
| `DistDiscoveryTest` | 4 | (a) override 존재 → 반환, (b) override 비존재 → throw, (c) override null + candidate match → 반환, (d) override null + 모두 실패 → null. |
| `FixtureDiscoveryTest` | 4 | 동일 패턴. |
| `CliArgsTest` | 7 | (a) `--k=v` 파싱, (b) 비-value-bearing `--flag` 단독, (c) `--k=` (빈 value) → throw, (d) value-bearing `--dist-dir` bare → throw (MF-B2), (e) 비-플래그 위치 arg → warn-only, (f) 중복 key → last-wins, (g) edge case: `--=value` (빈 key) + `---triple-dash`. |
| `SharedRendererBindingTest` | 3 | (a) bound=null + req → 통과, (b) bound=req → 통과, (c) bound≠req → `IllegalStateException`. |

**합계 Phase 1 신규 unit**: 18 (DistDiscovery 4 + FixtureDiscovery 4 + CliArgs 7 + SharedRendererBinding 3).

### 8.2 신규 integration tests (Phase 2)

| Test class | 케이스 수 | 검증 |
|---|---|---|
| `SharedLayoutlibRendererIntegrationTest` | 3 | (a) 첫 `getOrCreate` → LayoutlibRenderer 반환, (b) 같은 args 재호출 → 동일 인스턴스 (referential equality), (c) 다른 args 호출 → `IllegalStateException`. |

**합계 Phase 2 신규 integration**: 3.

### 8.3 기존 test 마이그레이션

- `LayoutlibRendererTier3MinimalTest`: Phase 1 에서 factory 명시 주입, Phase 2 에서 `SharedLayoutlibRenderer.getOrCreate` 로 전환.
- `LayoutlibRendererIntegrationTest`: Phase 2 에서 `@Disabled` + `SharedLayoutlibRenderer.getOrCreate` 로 전환 (SKIPPED 로 기록 유지).
- 기타 (`SessionParamsFactoryTest`, `FrameworkResource*Test`, `StyleParentInferenceTest` 등): 변경 없음.

### 8.4 최종 green 목표

| 카테고리 | 전 (W3D1) | 후 (W3D2) | Δ |
|---|---|---|---|
| Unit | 99 | **117** | +18 (DistDiscovery 4 + FixtureDiscovery 4 + CliArgs 7 + SharedRendererBinding 3) |
| Integration PASS | 8 | **11** | +3 (SharedLayoutlibRendererIntegrationTest) |
| Integration SKIPPED | 2 | **2** | tier3-glyph + LayoutlibRendererIntegrationTest@Disabled (activity_basic → ConstraintLayout/MaterialButton) |

### 8.5 TDD step 순서

**Phase 1**:
1. `DistDiscoveryTest` (RED) → `DistDiscovery.kt` (GREEN).
2. `FixtureDiscoveryTest` (RED) → `FixtureDiscovery.kt` (GREEN).
3. `CliArgsTest` (RED) → `CliArgs` inner class + `CliConstants.kt` (GREEN).
4. `LayoutlibRenderer` 생성자 default 제거 → 컴파일 RED → `LayoutlibRendererTier3MinimalTest.companion.renderer()` factory 수정 → GREEN.
5. `Main.kt` 리팩토링 (`chooseRenderer(parsed)` 재배선).
6. Full gate: `./server/gradlew -p server test`, `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`, `./server/gradlew -p server build`.

**Phase 2**:
7. `SharedRendererBindingTest` (RED) → `SharedRendererBinding.kt` (GREEN).
8. `SharedLayoutlibRendererIntegrationTest` (RED) → `SharedLayoutlibRenderer.kt` (GREEN).
9. `LayoutlibRendererTier3MinimalTest` → `SharedLayoutlibRenderer.getOrCreate` 전환, `companion.sharedRenderer` 제거.
10. `LayoutlibRendererIntegrationTest` → `@Disabled` + `SharedLayoutlibRenderer.getOrCreate` 전환.
11. Full gate (최종).

### 8.6 Regression safeguard

- 각 step 이후 해당 모듈만 빠르게 테스트 (`./server/gradlew -p server :layoutlib-worker:test` 등).
- Phase 경계 (step 6, step 11) 에서만 full suite + `build`.
- Phase 1 완료 후 Phase 2 시작 전에 work_log interim update (optional — 본 세션 내 commit 전략에 따름).

---

## 9. CLAUDE.md 규약 준수 체크리스트

- [x] **Naming (Java-style)**: 로컬 `tValue` / 멤버 `mValue` / 함수 `camelCase` / 클래스 `PascalCase` — 기존 Kotlin 관행 유지 (Kotlin 에서 `m`/`t` 접두 지양이 일반적이나 본 프로젝트 한정 규약 — 기존 파일에서는 일부 적용, 일부 미적용; 본 작업은 **새 Kotlin 파일 작성 시 기존 코드베이스 스타일 (non-prefixed) 준수**).
- [x] **One class per file**: 5 신규 파일 모두 단일 object.
- [x] **Brace style**: opening brace own-line — Kotlin 관례 (trailing brace) 로 통일 유지. 본 프로젝트 Kotlin 코드 전반 관례를 따름 (CLAUDE.md 의 C++ 규약 그대로 적용하지 않음).
- [x] **No default parameter values**: `LayoutlibRenderer` 의 default 2개 제거. 신규 파일의 parameter default 도 0개.
- [x] **Constants — Zero Tolerance**: 하드코딩 path literal 제거, 상수화. 각 상수는 도메인별 object 안에.
- [x] **Comments**: KDoc 유지, L5 (Kotlin nested block comment) 회피 — `/*` 표현 금지.
- [x] **Change Policy**: carry 로 명시된 범위 밖 수정 없음. `SessionParamsFactory` 이미 W3D1 MF2 에서 처리됨 — 건드리지 않음.

---

## 10. 예상 landmine + 회피

| ID | 예상 landmine | 회피 |
|---|---|---|
| LM-A | `DistDiscovery.locate` 에서 `user.dir` 기준 경로와 상대경로 중복 후보 → 동일 Path 두 번 탐색 (성능 무해하나 로그 혼란) | 후보 리스트 생성 후 `distinct()` 필요 시 적용. 일단 미적용 (4 개 후보라 저비용). |
| LM-B | `CliArgs.parse` 가 `--key=value=extra` 같은 입력에서 잘못된 value 추출 | `indexOf("=")` 로 **첫** 등호만 기준 → value 에 `=` 허용 (실용적). |
| LM-C | `SharedLayoutlibRenderer.getOrCreate` 첫 호출에서 `LayoutlibRenderer` 생성자가 Throwable 을 던질 때 singleton 미할당 상태 유지됨 → 다음 호출 시 재시도하나 native lib 가 "이미 로드" 상태라 두 번째도 실패 — 실패가 전파되지 않으면 조용한 오류 | Native lib 로드 실패 시 `initBridge` 가 throwable 을 swallow 하는 기존 동작이 있어, 재시도 가능성이 낮음. 해결: 재시도 시 bound=null 유지하되 에러 메시지에 "native lib 재로드 실패 가능" 를 포함. 본 session 에서는 실현된 시나리오가 없어 observability 만 추가. |
| LM-D | `LayoutlibRendererIntegrationTest` 를 `@Disabled` 로 바꾸면 `@Tag("integration")` 태그 gating 이 영향받지 않는지 | JUnit 5 는 `@Disabled` 가 tag 와 독립 — SKIPPED 로 기록되나 `-PincludeTags=integration` 결과 카운트에 반영됨. 기존 tier3-glyph 와 동일 패턴이라 안전. |
| LM-E | `FixtureDiscovery.CANDIDATE_ROOTS` 가 `""` 포함 시 `Paths.get("", FIXTURE_SUBPATH)` 는 Linux 에서 `"/fixture/..."` (absolute) 가 아닌 `"fixture/..."` (cwd-relative) 가 되어 의도와 일치 | Java `Paths.get("", "x")` 는 `Paths.get("x")` 와 동일 (cwd-relative). 의도 일치. |

---

## 11. Success criteria

1. `./server/gradlew -p server test` 이후: **117 unit PASS** (99 + 18 신규).
2. `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` 이후: **11 integration PASS + 2 SKIPPED** (tier3-glyph + LayoutlibRendererIntegrationTest@Disabled).
3. `./server/gradlew -p server build` 이후: `BUILD SUCCESSFUL`.
4. `axp-server.jar --smoke` → stderr 에 version line + stdout "ok" (기존 동작 유지).
5. `axp-server.jar --dist-dir=/path/to/layoutlib --fixture-root=/path/to/fixtures --http-server` → 정상 부팅 + viewer 응답.
6. `axp-server.jar --dist-dir=/nonexistent --http-server` → `IllegalArgumentException` 로 즉시 종료 (fail-fast).
7. `grep -rn "layoutlib-dist/android-34" server/mcp-server/src/main/` → 결과 없음 (상수 추출 완료).
8. `grep -rn "defaultFixtureRoot" server/layoutlib-worker/src/main/` → 결과 없음 (helper 삭제 완료).
9. `grep -En '^\s*private val (fallback|fixtureRoot).* = ' server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` → 결과 없음 (default parameter 제거 확인).

---

END.
