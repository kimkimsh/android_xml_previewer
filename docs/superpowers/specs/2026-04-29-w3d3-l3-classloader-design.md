# W3D3 — L3 ClassLoader (in-JVM custom view) 설계 스펙

> **Date**: 2026-04-29
> **Scope**: Option A — sample-app `activity_basic.xml` (ConstraintLayout / MaterialButton) 의 layoutlib 렌더 unblock.
> **Canonical refs**:
> - `docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md` §"Option A".
> - `docs/plan/06-revisions-and-decisions.md §1.1, §2.3` (canonical L3 = AVD/harness-APK + DexClassLoader; **본 W3D3 는 그 L3 가 아님** — §0.3 참조).
> - `docs/plan/08-integration-reconciliation.md §7.7.3` (W3D1 carry: tier3-glyph + W3-CLASSLOADER-AUDIT F-4).

---

## 0. 용어 정리 (CRITICAL — 페어 리뷰 전 명확화)

### 0.1 "L3" 의 두 가지 의미

이 프로젝트에서 "L3" 는 두 다른 메커니즘을 지칭한다:

| 명명 | 메커니즘 | 위치 | 상태 |
|------|---------|------|------|
| **canonical L3** (`plan/06 §2.1`) | AVD 에뮬레이터 + harness APK + Dalvik DexClassLoader | 별도 Android 런타임 프로세스 | v1.0 W3 후반/W4 carry |
| **본 W3D3 L3** (=in-JVM custom view) | layoutlib host JVM 안에서 AAR `classes.jar` 를 URLClassLoader 로 적재 | layoutlib-worker JVM | 본 스펙 타겟 |

핸드오프 `Option A` 와 사용자 prompt 의 "DexClassLoader 기반 L3 class loading" 표현은 **후자** 를 의미한다 (사용자 prompt 의 dex/jar 양쪽 허용 표현이 근거). 본 스펙 안에서는 혼동 회피를 위해:
- **canonical L3** = AVD-L3.
- **본 스펙 타겟** = "in-JVM custom view loading" 또는 "L3-classloader" (개행 시 줄임 — 동일 의미).

### 0.2 DexClassLoader 가 본 스펙에서 "쓰이지 않는" 이유

`dalvik.system.DexClassLoader` 는 Android 런타임(ART/Dalvik) 전용 API 이며, host JVM 에는 존재하지 않는다. layoutlib 은 host JVM 위에서 `libandroid_runtime.so` 만 native 로 로드하여 Android 프레임워크 클래스를 JVM 바이트코드 형태로 포함한 layoutlib JAR 을 사용한다. AAR 의 `classes.jar` 도 D8 변환 전의 **JVM 바이트코드** (= classfile, magic `0xCAFEBABE`) — DEX(매직 `dex.035`) 가 아니다.

따라서 본 스펙은 `java.net.URLClassLoader` 를 사용한다. 사용자 prompt 의 "dex/jar classloader" 표현은 jar branch 를 채택.

### 0.3 ChildFirst 정책 검토

`plan/06 §2.3` 의 ChildFirstDexClassLoader 정책은 AVD-L3 한정 — harness APK 의 androidx 가 사용자 앱 androidx 보다 우선되지 않도록 child-first delegation 을 강제하기 위함. 본 스펙은 다음 구조이므로 ChildFirst 가 **불필요**:

```
system CL          ← Kotlin stdlib, layoutlib-api, kxml2
   ↑ parent
isolated CL        ← layoutlib JAR (android.view.*, android.widget.*, android.content.*)
   ↑ parent
AAR URL CL         ← constraintlayout/material/appcompat/... classes.jar + sample-app R.jar
```

각 계층의 클래스가 disjoint:
- `android.*` → isolated CL only.
- `androidx.*` / `com.google.android.material.*` / `com.fixture.*` → AAR CL only.
- `kotlin.*` / `kxml2.*` / `org.xmlpull.*` → system CL.

shadowing 가능 클래스 없음 → parent-first 안전. ChildFirst 도입은 YAGNI.

---

## 1. 요구사항 (W3D3 deliverable)

### 1.1 In-scope (T1 gate 통과 목표)

1. AAR 의 `classes.jar` 를 host JVM 클래스로더로 적재.
2. `MinimalLayoutlibCallback.loadView(name, sig, args)` 가 위 클래스로더로부터 custom view 를 reflection-instantiate.
3. sample-app 의 `R.jar` (이미 빌드되어 존재) 를 클래스로더 URL 에 포함 — `androidx.constraintlayout.widget.R$styleable` 등이 정상 resolve 되어야 함.
4. `LayoutlibRendererIntegrationTest` 의 `@Disabled` 제거 + tier3-values 패턴 (T1 gate: `Result.Status == SUCCESS && PNG > 1000 bytes`) 통과.

### 1.2 Out-of-scope (W3D4+ carry)

다음은 본 W3D3 에서 **다루지 않는다**. 페어 리뷰가 in-scope 이동을 요구하면 별도 합의:

- **App/library resource value loading**: AAR 의 `res/values/values.xml` 파싱 + RenderResources 통합. 영향: ConstraintLayout 의 `app:layout_constraintTop_toTopOf` 등 custom attr 가 default(0/null) 로 fallback. layout 위치가 잘못되어도 `Result.Status == SUCCESS` + PNG > 1000 bytes 는 통과 가능.
- **Visual fidelity assertion** (pixel level). T1 gate 는 status + 크기만 검사.
- **AVD-L3** (canonical L3 — `plan/06 §2`).
- **tier3-glyph** (W4+ carry).

### 1.3 Acceptance Gate (T1)

```kotlin
// LayoutlibRendererIntegrationTest 가 expect:
val bytes = renderer.renderPng("activity_basic.xml")
assertEquals(SUCCESS, renderer.lastSessionResult.status)   // 페어 리뷰가 강하게 권장하면 추가
assertTrue(bytes.size > 1000)                              // 기존 패턴
assertTrue(bytes[0..3] == PNG_MAGIC)                       // 기존 패턴
```

---

## 2. 컴포넌트 분해

| # | 파일 | 책임 | LOC 추정 |
|---|------|------|----------|
| C1 | `fixture/sample-app/app/build.gradle.kts` (수정) | `axpEmitClasspath` Gradle task — 해석된 debugRuntimeClasspath 의 AAR + JAR 절대 경로를 manifest 로 emit. `assembleDebug` 에 finalizedBy. | +25 |
| C2 | `server/layoutlib-worker/.../classloader/SampleAppClasspathManifest.kt` | manifest 파일 (텍스트, 한 줄에 한 절대 경로) 파싱. 누락/오래됨/형식 오류 → 명시 예외. | 60 |
| C3 | `server/layoutlib-worker/.../classloader/AarExtractor.kt` | `.aar` ZIP 에서 `classes.jar` 를 stable cache (`<sample-app>/build/axp/aar-classes/<sha1>/<artifact>.jar`) 로 추출. mtime 기반 idempotent. | 70 |
| C4 | `server/layoutlib-worker/.../classloader/SampleAppClassLoader.kt` | 위 manifest+extractor 결과 + R.jar 위치 → `URLClassLoader(parent = isolatedCL)`. 빌드된 CL 인스턴스 + 진단용 entry list. | 60 |
| C5 | `server/layoutlib-worker/.../classloader/ClassLoaderConstants.kt` | 패키지 상수 (manifest 파일명, R.jar 상대 경로, AAR cache 디렉토리명). | 30 |
| C6 | `MinimalLayoutlibCallback.kt` (수정) | 생성자에 `viewClassLoader: ClassLoader` 추가. `loadView` 가 reflection 으로 instantiate. UnsupportedOperationException 분기 제거. | +25 |
| C7 | `LayoutlibRenderer.kt` (수정) | `renderViaLayoutlib` 가 SampleAppClassLoader 를 빌드 후 callback 에 주입. lazy + per-renderer 캐시. | +20 |
| C8 | `LayoutlibRendererIntegrationTest.kt` (수정) | `@Disabled` 제거. T1 gate assertion 추가. SharedLayoutlibRenderer 호출은 유지. | +10 |
| **Tests** | | | |
| TC1 | `SampleAppClasspathManifestTest` (단위 5) | manifest 파일 누락 / 빈 / 잘못된 라인 / 정상 / 절대경로 강제. | 80 |
| TC2 | `AarExtractorTest` (단위 4) | 정상 추출 / 캐시 hit / 손상 AAR / classes.jar 없는 AAR. | 100 |
| TC3 | `SampleAppClassLoaderTest` (단위 3) | 클래스로더 합성 / parent chain / R.jar 포함 검증. tmp dir + minimal jar fixture. | 80 |
| TC4 | `MinimalLayoutlibCallbackLoadViewTest` (단위 3) | 새 생성자 / reflection instantiate 성공 / 클래스 없음 → ClassNotFoundException. | 70 |

추정 합계: production ~290 LOC, test ~330 LOC, +25 Gradle.

---

## 3. 데이터 흐름

```
[Build time, fixture/sample-app]
  ./gradlew :app:assembleDebug
    └─ tasks: ... → axpEmitClasspath (finalizedBy assembleDebug)
        └─ writes: app/build/axp/runtime-classpath.txt
                   각 라인: <abs path to .aar or .jar>

[Test time, server/layoutlib-worker]
  LayoutlibRenderer.renderPng("activity_basic.xml")
    └─ initBridge() (기존)
    └─ buildSampleAppClassLoader(fixtureRoot) [lazy, per-renderer]
        ├─ SampleAppClasspathManifest.read(fixtureRoot.../runtime-classpath.txt)
        │     → List<Path> entries
        ├─ entries.map { path ->
        │     when {
        │       path ends ".aar" → AarExtractor.extract(path) // → classes.jar path
        │       path ends ".jar" → path
        │       else → IllegalStateException
        │     }
        │   }
        ├─ + sample-app R.jar (fixtureRoot.../intermediates/.../R.jar)
        └─ URLClassLoader(urls, parent = isolatedClassLoader)
    └─ renderViaLayoutlib(layoutName, viewClassLoader)
        ├─ MinimalLayoutlibCallback(viewClassLoader)
        ├─ SessionParamsFactory.build(...)
        └─ Bridge.createSession(params).render()
            ├─ inflater 가 LayoutlibCallback.loadView 호출
            └─ callback.loadView("androidx.constraintlayout.widget.ConstraintLayout", [Context, AttributeSet], [...])
                └─ viewClassLoader.loadClass(name).getConstructor(...).newInstance(...)
```

---

## 4. 컴포넌트 스펙

### 4.1 C1 — Gradle manifest task

`fixture/sample-app/app/build.gradle.kts` 의 끝에 추가:

```kotlin
// 본 task 는 W3D3 in-JVM custom view 로딩 지원 — server/layoutlib-worker 가
// debugRuntimeClasspath (resolved AAR + transitive JAR) 의 절대경로 manifest 를 읽어
// 자체 URLClassLoader 를 구성한다. Maven cache 의 transforms-* hash dir 의 불안정성을
// 우회하기 위해 본 manifest 가 single source of truth 가 된다.
val axpClasspathManifest = layout.buildDirectory.file("axp/runtime-classpath.txt")
val axpEmitClasspath = tasks.register("axpEmitClasspath") {
    val cpProvider = configurations.named("debugRuntimeClasspath")
    inputs.files(cpProvider)
    outputs.file(axpClasspathManifest)
    doLast {
        val cp = cpProvider.get()
        val artifacts = cp.resolvedConfiguration.resolvedArtifacts
            .map { it.file.absolutePath }
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

**계약**:
- 출력 경로: `fixture/sample-app/app/build/axp/runtime-classpath.txt`.
- 형식: 라인 separator `\n`, 각 라인 = AAR 또는 JAR 의 절대경로. trailing newline 없음 (joinToString 결과).
- 정렬: lexicographic — reproducibility 를 위함.
- 경로는 `.aar` 또는 `.jar` 로 끝나야 함. 그 외 형식 발견 시 worker 가 거부 (TC1 가 검증).

**왜 emit 시점 = `finalizedBy(assembleDebug)`?**: 사용자 흐름 `./gradlew :app:assembleDebug` 가 보통 W3 dev 사이클의 entry point. 별도 task 명령 강제 시 forget 위험.

**페어 리뷰 Q1**: `applicationVariants.all` 또는 androidComponents API 를 써야 할까? — `debugRuntimeClasspath` 은 표준 configuration 이름 (AGP 8.x 안정), 직접 참조 OK. 그러나 향후 flavor 추가 시 break — 본 fixture 는 flavor 없음, defer.

### 4.2 C2 — SampleAppClasspathManifest

```kotlin
package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
import java.nio.file.Files
import java.nio.file.Path

/**
 * fixture/sample-app/app/build/axp/runtime-classpath.txt 파일을 읽어
 * resolved runtime classpath 의 AAR/JAR 절대경로 리스트를 제공한다.
 *
 * 파일 형식 (axpEmitClasspath Gradle task 가 emit):
 *  - 라인 separator '\n', trailing newline 없음.
 *  - 각 라인 = AAR 또는 JAR 의 절대 경로.
 *  - 정렬: lexicographic.
 *
 * 누락 / 빈 라인 (whitespace 만) / 비-절대경로 / 비-aar/jar 확장자는 모두 throw.
 * "fail loud at fixture-prep boundary" — 부분 적재로 silent 한 ClassNotFoundException 보다
 * 명시적 에러가 디버깅 비용 낮음.
 */
object SampleAppClasspathManifest {

    fun read(fixtureRoot: Path): List<Path> {
        val mf = fixtureRoot.resolve(MANIFEST_RELATIVE_PATH)
        require(Files.isRegularFile(mf)) {
            "axp classpath manifest 누락: $mf — `./gradlew :app:assembleDebug` 를 먼저 실행하세요"
        }
        val raw = Files.readString(mf)
        if (raw.isBlank()) {
            error("axp classpath manifest 가 비어있음: $mf")
        }
        return raw.split('\n').mapIndexed { idx, line ->
            require(line.isNotBlank()) { "manifest line ${idx + 1} 이 공백" }
            val p = Path.of(line)
            require(p.isAbsolute) { "manifest line ${idx + 1} 이 비-절대경로: '$line'" }
            require(line.endsWith(".aar") || line.endsWith(".jar")) {
                "manifest line ${idx + 1} 의 확장자가 .aar/.jar 가 아님: '$line'"
            }
            require(Files.isRegularFile(p)) { "manifest line ${idx + 1} 의 파일이 없음: $p" }
            p
        }
    }
}
```

### 4.3 C3 — AarExtractor

```kotlin
package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

/**
 * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출.
 *
 * Cache 위치: `<fixtureRoot>/app/build/axp/aar-classes/<sha1(absPath)>/<artifactName>.jar`.
 * Idempotent: 캐시된 파일의 mtime >= AAR 의 mtime 이면 재사용.
 *
 * AAR 안에 classes.jar 가 없는 경우 (e.g. 순수 resource-only AAR) → 경고 + null 반환 (호출자가 skip).
 * 손상된 ZIP → IOException pass-through.
 */
object AarExtractor {

    /**
     * @return 추출된 classes.jar 의 Path, classes.jar 가 AAR 에 없으면 null.
     */
    fun extract(aarPath: Path, cacheRoot: Path): Path? {
        require(aarPath.isRegularFile()) { "AAR 누락: $aarPath" }
        val key = sha1(aarPath.toAbsolutePath().toString())
        val artifactName = aarPath.fileName.toString().removeSuffix(".aar")
        val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR).resolve(key)
        Files.createDirectories(outDir)
        val outJar = outDir.resolve("$artifactName.jar")

        val aarMtime = Files.getLastModifiedTime(aarPath).toMillis()
        if (outJar.isRegularFile() &&
            Files.getLastModifiedTime(outJar).toMillis() >= aarMtime) {
            return outJar
        }

        ZipFile(aarPath.toFile()).use { zip ->
            val entry = zip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
            zip.getInputStream(entry).use { input ->
                outJar.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outJar
    }

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
```

**페어 리뷰 Q2**: 캐시 invalidation 을 mtime 기반으로 하면 AAR 이 동일 경로로 재배포되면서 mtime 만 갱신되는 시나리오가 깨지나? — Gradle cache 의 AAR 은 hash 디렉토리에 위치하므로 동일 path 의 contents 변경은 사실상 발생하지 않음 (Maven 좌표+버전이 같으면 contents 동일). hash mismatch 시 path 자체가 달라짐 → cacheRoot 안의 sha1 키도 다름. 안전.

### 4.4 C4 — SampleAppClassLoader

```kotlin
package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * sample-app 의 runtime classpath (resolved AAR + transitive JAR + R.jar) 로부터
 * URLClassLoader 를 구성. parent = layoutlib isolatedClassLoader (android.view.* 보유).
 *
 * AAR 은 AarExtractor 로 classes.jar 를 추출 후 그 경로를 URL 로 사용.
 * Resource-only AAR (classes.jar 없음) 은 silently skip.
 */
class SampleAppClassLoader private constructor(
    val classLoader: ClassLoader,
    val urls: List<URL>,
) {
    companion object {

        /**
         * @param fixtureRoot fixture/sample-app 절대경로.
         * @param parent 본 CL 의 parent — 보통 LayoutlibBootstrap.createIsolatedClassLoader() 결과.
         */
        fun build(fixtureRoot: Path, parent: ClassLoader): SampleAppClassLoader {
            val manifest = SampleAppClasspathManifest.read(fixtureRoot)
            val cacheRoot = fixtureRoot.resolve("app").resolve("build")
            val urls = mutableListOf<URL>()

            for (entry in manifest) {
                val asString = entry.toString()
                val jarPath = if (asString.endsWith(".aar")) {
                    AarExtractor.extract(entry, cacheRoot) ?: continue // resource-only AAR skip
                } else {
                    entry
                }
                urls += jarPath.toUri().toURL()
            }

            // sample-app 의 R.jar — 모든 R.<package>.* 클래스 포함. 누락 시 NoClassDefFoundError
            // for androidx.constraintlayout.widget.R$styleable etc.
            val rJar = fixtureRoot.resolve(R_JAR_RELATIVE_PATH)
            require(Files.isRegularFile(rJar)) {
                "sample-app R.jar 누락: $rJar — `./gradlew :app:assembleDebug` 가 필요"
            }
            urls += rJar.toUri().toURL()

            val cl = URLClassLoader(urls.toTypedArray(), parent)
            return SampleAppClassLoader(cl, urls.toList())
        }
    }
}
```

### 4.5 C5 — ClassLoaderConstants

```kotlin
package dev.axp.layoutlib.worker.classloader

internal object ClassLoaderConstants {
    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 fixture-relative 경로. */
    const val MANIFEST_RELATIVE_PATH = "app/build/axp/runtime-classpath.txt"

    /** AarExtractor 의 stable 캐시 디렉토리 (cacheRoot 기준 상대). */
    const val AAR_CACHE_RELATIVE_DIR = "axp/aar-classes"

    /** AAR ZIP 안에서 JVM 바이트코드 JAR 의 표준 entry 이름. */
    const val AAR_CLASSES_JAR_ENTRY = "classes.jar"

    /**
     * AGP 8.x 가 emit 하는 통합 R.jar 경로 (compile_and_runtime_not_namespaced_r_class_jar variant).
     * 본 경로는 AGP minor 버전 변경 시 깨질 수 있으나 8.x 안정 — 변경 시 본 상수만 갱신.
     */
    const val R_JAR_RELATIVE_PATH =
        "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
}
```

### 4.6 C6 — MinimalLayoutlibCallback 변경

```kotlin
class MinimalLayoutlibCallback(
    private val viewClassLoader: ClassLoader,
) : LayoutlibCallback() {

    // ... 기존 nextId / byRef / byId / 동기화된 getOrGenerateResourceId / resolveResourceId 유지 ...

    override fun loadView(
        name: String,
        constructorSignature: Array<out Class<*>>?,
        constructorArgs: Array<out Any>?,
    ): Any {
        val cls = viewClassLoader.loadClass(name)
        val sig = constructorSignature ?: emptyArray()
        val args = constructorArgs ?: emptyArray()
        val ctor = cls.getDeclaredConstructor(*sig)
        ctor.isAccessible = true
        return ctor.newInstance(*args)
    }

    // ... 나머지 unchanged ...
}
```

**페어 리뷰 Q3**: `loadView` 가 throw 하는 정확한 타입은 무엇이어야 하나? — LayoutlibCallback 인터페이스는 unchecked. 우리는 ClassNotFoundException / NoSuchMethodException / InvocationTargetException 를 그대로 propagate (try-catch 없음 — 실제 layoutlib 의 inflater 가 catch). 명시적 wrap 불요 — 페어 리뷰가 별도 처리를 권하면 별도 PR.

### 4.7 C7 — LayoutlibRenderer 변경

`renderViaLayoutlib` 가 callback 인스턴스 생성 직전에 SampleAppClassLoader 를 빌드:

```kotlin
@Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null

private fun resolveViewClassLoader(): ClassLoader {
    sampleAppClassLoader?.let { return it.classLoader }
    val isolated = classLoader ?: error("Bridge 가 init 안 됨")
    val built = SampleAppClassLoader.build(fixtureRoot, isolated)
    sampleAppClassLoader = built
    return built.classLoader
}

private fun renderViaLayoutlib(layoutName: String): ByteArray? {
    // ... 기존 코드 ...
    val viewCL = resolveViewClassLoader()
    val params: SessionParams = SessionParamsFactory.build(
        layoutParser = parser,
        callback = MinimalLayoutlibCallback(viewCL),  // ← 변경
        resources = resources,
    )
    // ... 기존 코드 ...
}
```

**페어 리뷰 Q4**: `fixtureRoot` 이 sample-app 이 아닌 (예: 다른 fixture) 이면? — 현재 fixture-discovery 가 sample-app 만 지원. activity_minimal 이 framework-only 이므로 SampleAppClassLoader.build 호출이 manifest 누락 → throw 하면 W3D1 시나리오가 깨진다. 따라서:
- 옵션 A: lazy build — `loadView` 가 호출될 때만 build 시도. activity_minimal 은 framework only 라 호출 0 → side-effect 없음.
- 옵션 B: build 가 manifest 없으면 empty CL 반환.

옵션 A 채택 (스펙 §4.6 의 viewClassLoader 가 lazy proxy 가 됨). 페어 리뷰가 단순화 권하면 옵션 B 검토.

**구현 변경**: `MinimalLayoutlibCallback` 에 `viewClassLoader: () -> ClassLoader` (lambda) 또는 `Lazy<ClassLoader>` 주입. activity_minimal 은 절대 호출 안 됨 → manifest 누락이 무해.

### 4.8 C8 — LayoutlibRendererIntegrationTest 변경

```kotlin
@Tag("integration")
class LayoutlibRendererIntegrationTest {

    @Test
    fun `tier3-values — activity_basic renders SUCCESS with non-empty PNG`() {
        val dist = locateDistDir()
        val fixture = locateFixtureRoot()
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = fixture,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_basic.xml")

        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG bytes 가 placeholder 보다 큼: ${bytes.size}")
        assertTrue(
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
            "PNG magic 헤더가 아님",
        )
        // 페어 리뷰가 SUCCESS assertion 추가 권하면 본 줄 활성:
        // assertEquals(Result.Status.SUCCESS, renderer.lastSessionResult?.status)
    }

    // ... locateDistDir / locateFixtureRoot 유지 ...

    private companion object {
        const val MIN_RENDERED_PNG_BYTES = 1000
    }
}
```

---

## 5. 테스트 전략 (TDD)

각 컴포넌트별 단위 테스트 → integration 테스트 마지막.

### 5.1 단위

| Test | 케이스 | 검증 |
|------|-------|------|
| TC1-1 | manifest 파일 누락 | IllegalArgumentException with hint |
| TC1-2 | manifest 빈 파일 | IllegalStateException |
| TC1-3 | 라인이 공백 | IllegalArgumentException with line index |
| TC1-4 | 비-절대경로 | IllegalArgumentException |
| TC1-5 | 정상 (.aar + .jar 혼합) | List<Path> 반환, 순서 유지 |
| TC2-1 | 정상 추출 | classes.jar 가 cacheRoot 에 생성, content 일치 |
| TC2-2 | 캐시 hit | 두 번째 호출이 동일 Path, mtime 변경 없음 |
| TC2-3 | classes.jar 없는 AAR | null 반환 |
| TC2-4 | 손상 ZIP | IOException |
| TC3-1 | URLClassLoader 합성 + parent chain | parent === isolatedCL |
| TC3-2 | manifest 의 .jar 가 그대로 URL 화 | URL list 에 포함 |
| TC3-3 | R.jar 누락 시 require fail | 명확한 메시지 |
| TC4-1 | viewClassLoader 가 known class 를 load | Class 인스턴스 반환 |
| TC4-2 | unknown class 요청 | ClassNotFoundException pass-through |
| TC4-3 | constructor signature mismatch | NoSuchMethodException pass-through |

### 5.2 Integration

| Test | 입력 | 검증 |
|------|-----|------|
| `tier3-basic` (renamed from `tier3 - renderPng...`) | activity_basic.xml | T1 gate (PNG > 1000 + magic). 페어 리뷰가 권하면 SUCCESS status 추가. |

기존 `LayoutlibRendererTier3MinimalTest` (activity_minimal) 영향 없음 — 본 변경 후에도 PASS 보장. lazy CL 덕분에 manifest 누락 영향 없음.

### 5.3 회귀 가능성

- Bridge.init / RenderSession 경로 — 변경 없음.
- FrameworkResourceValueLoader / FrameworkRenderResources — 변경 없음.
- SharedLayoutlibRenderer — 변경 없음.
- 99 → 118 unit tests + 11 integration → 본 변경 후: 118 + ~15 unit (TC1-4) + 11 → 12 integration (LayoutlibRendererIntegrationTest enable) PASS. tier3-glyph 만 SKIPPED 유지.

---

## 6. 위험 / 알려진 한계

### 6.1 위험

| ID | 위험 | 완화 |
|----|------|------|
| R1 | sample-app 미빌드 시 manifest/R.jar 누락 → 모든 integration test 가 FAIL | C2/C4 의 require 메시지가 정확한 명령(`./gradlew :app:assembleDebug`) 안내. 페어 리뷰가 더 강한 요구 시 worker 가 자동으로 invoke 하는 방안 검토. |
| R2 | layoutlib 의 LayoutlibCallback 이 loadView 외 다른 callback (e.g. `getProject` / `findClass`) 도 사용? | 페어 리뷰에서 layoutlib 14.x 의 LayoutlibCallback 추상메서드 audit. 본 스펙은 loadView 만 변경. |
| R3 | ConstraintLayout 의 `app:layout_constraintXxx` custom attr 가 default 로 fallback → render 결과가 broken positioning. T1 gate 통과는 하지만 visual fidelity 미달. | W3D4 carry: app/library resource value loader. 본 스펙 out-of-scope §1.2 명시. |
| R4 | AGP 8.x 의 `compile_and_runtime_not_namespaced_r_class_jar` 경로가 AGP 9.x 에서 변경 가능 | ClassLoaderConstants 에 상수 1개 — 발견 시 단일 변경. |
| R5 | sample-app 의 `debugRuntimeClasspath` 에 layoutlib JAR 이 포함되면 isolatedCL 과 충돌? | sample-app 은 layoutlib 에 의존하지 않음. 가능성 없음. |
| R6 | `forkEvery(1L)` 에서 두 integration test 가 같은 sample-app cache dir 을 동시에 쓰지 않나 | Gradle test fork 는 process 격리, 같은 디스크지만 mtime 기반 idempotent → race condition 시 둘이 동일 컨텐츠로 덮어씀. 안전. |

### 6.2 알려진 한계

- **Visual fidelity**: §1.2 R3 — out-of-scope, W3D4+ carry.
- **Multi-fixture**: 본 스펙은 sample-app 만 가정. 다중 fixture 지원은 W3D4+.
- **Hot reload**: 빌드 manifest 가 매 build 마다 갱신 → 워커 재시작 필요. 현재 worker 는 매 test class 별 fork 라 실용 영향 없음.

---

## 7. 페어 리뷰용 질문 (Q1-Q5)

페어 리뷰 (Codex+Claude) 에서 명시적으로 검증 요청:

- **Q1** (Gradle): `applicationVariants.all` API 가 `axpEmitClasspath` 를 더 안정적으로 wire 할 수 있나? AGP 8.x 의 best practice 는?
- **Q2** (Cache): `AarExtractor` 의 mtime 기반 invalidation 이 충분한가? hash 기반 (sha256) 으로 변경해야 하는 시나리오가 있나?
- **Q3** (loadView): InvocationTargetException 의 cause 를 unwrap 해서 re-throw 해야 하나? layoutlib 14.x 의 inflater 가 unwrap 을 기대하나?
- **Q4** (lazy build): SampleAppClassLoader 빌드를 lazy 로 미루면 (옵션 A) activity_minimal 호환성 유지. 더 단순한 옵션 (B: empty CL) 이 권장되는가?
- **Q5** (assertion): T1 gate 에 `Result.Status == SUCCESS` 를 추가해야 하나? 현재 W2D7 패턴은 PNG > 1000 만 검사 — strictness 의 trade-off?

페어 리뷰 verdict 형식: GO / GO_WITH_FOLLOWUPS / NO_GO + 각 Q 별 stance.

---

## 8. 변경 로그

- 2026-04-29: 초안 작성. W3D2 carry 의 sample-app unblock 옵션 A 의 in-JVM 변형 (canonical AVD-L3 와 구분).
