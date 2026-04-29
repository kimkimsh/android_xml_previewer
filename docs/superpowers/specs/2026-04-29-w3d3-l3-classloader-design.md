# W3D3 — L3 ClassLoader (in-JVM custom view) 설계 스펙

> **Date**: 2026-04-29
> **Scope**: Option A — sample-app `activity_basic.xml` (ConstraintLayout / MaterialButton) 의 layoutlib 렌더 unblock.
> **Canonical refs**:
> - `docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md` §"Option A".
> - `docs/plan/06-revisions-and-decisions.md §1.1, §2.3` (canonical L3 = AVD/harness-APK + DexClassLoader; **본 W3D3 는 그 L3 가 아님** — §0.3 참조).
> - `docs/plan/08-integration-reconciliation.md §7.7.3` (W3D1 carry: tier3-glyph + W3-CLASSLOADER-AUDIT F-4).

---

## 0. 페어 리뷰 결과 (round 1, 2026-04-29)

본 스펙 round 1 페어 리뷰 (Codex GPT-5.5 xhigh + Claude Opus 4.7) 결과:
- **Codex**: NO_GO (4 blockers + 4 follow-ups). `/tmp/w3d3-codex-spec-review.md`.
- **Claude**: GO_WITH_FOLLOWUPS (1 blocker + 6 follow-ups). `/tmp/w3d3-claude-spec-review.md`.

**컨버전스 항목 (양측 일치, round 2 본문에 반영)**:
- **B1**: `fixtureRoot` 의미 불일치 — 별도 `sampleAppModuleRoot` 인자 도입 (§4.4, §4.7).
- **F1**: `MinimalLayoutlibCallback.findClass` + `hasAndroidXAppCompat` override 추가 (§4.6 D3).
- **B4 (Codex-only, valid)**: magic strings 를 `ClassLoaderConstants` 로 (§4.5).
- **Manifest dedup** (Claude-F4): `.distinct()` 추가 (§4.1).
- **Atomic AAR write** (Codex-B2-followup): temp + ATOMIC_MOVE (§4.3).
- **Q1**: `afterEvaluate` 제거, 직접 `tasks.named("assembleDebug").configure { finalizedBy(...) }` (§4.1).
- **Q2**: mtime 유지 (Gradle modules-2 cache 의 content-hash dir 가정).
- **Q3**: Codex 입장 채택 — `InvocationTargetException` cause unwrap (§4.6).
- **Q4**: lazy build (option A) 유지 (§4.7).
- **Q5**: T1 gate 에 `Result.Status == SUCCESS` assertion 활성 (§4.8).

**디버전트 항목 (round 2 에서 처리)**:
- **B2** (Codex): activity_basic.xml 의 `MaterialButton` 이 Material theme enforcement 로 SUCCESS 불가 주장 — Claude 는 약하게 동의 (D7 fidelity 손상). **empirical 검증 필요** — 구현 후 실측. Contingency: T1 gate 가 fail 시 § 9.1 의 fallback layout (activity_basic_minimal.xml — MaterialButton → 표준 Button) 으로 자동 다운그레이드.
- **B3** (Codex): R.jar real id (e.g., `2130903705`) ↔ callback generated id (`0x7F0A_xxxx`) 불일치. **이미 §1.2 R3 가 인정** — broken positioning. SUCCESS 자체는 막지 않음 (`obtainStyledAttributes` 가 unknown id 에 대해 default 반환, throw 없음). round 2 에서 §1.2 R3 표현 강화.

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

- **App/library resource value loading**: AAR 의 `res/values/values.xml` 파싱 + RenderResources 통합. 영향:
  - ConstraintLayout 의 `app:layout_constraintTop_toTopOf` 등 custom attr 가 default(0/null) 로 fallback → 모든 child 가 (0,0) 에 적층. layout 위치가 잘못되어도 `Result.Status == SUCCESS` + PNG > 1000 bytes 는 통과 가능.
  - sample-app 의 `Theme.AxpFixture` (Material3.DayNight 파생) 가 적용 안 됨 — framework `Theme.Material.Light.NoActionBar` 로 fallback. AppCompat/Material 의 `?attr/textAppearanceHeadlineMedium`, `?attr/colorOnSurface` 등 unresolved → 모든 TextView default text appearance.
  - **R.jar real id (e.g., `2130903705`) ↔ callback generated id (`0x7F0A_xxxx`) 불일치**: ConstraintLayout/MaterialButton 등이 `obtainStyledAttributes(attrs, R.styleable.X)` 를 호출하면 `R.styleable.X` 의 int 들은 layoutlib 의 resource id space 와 disjoint → `TypedArray` lookup miss, default 반환 (throw 없음). 이는 SUCCESS 자체는 막지 않음.
  - **Material theme enforcement 위험**: `MaterialThemeOverlay.wrap` / `ThemeEnforcement.checkCompatibleTheme` 가 framework theme 에 specific Material attr 부재 시 throw 가능 — empirical 검증 필요 (구현 후 §9.1 contingency 발동 여부 판단).
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
| C0 | `server/layoutlib-worker/.../FixtureDiscovery.kt` (수정) | `locateModuleRoot(override): Path?` 메서드 추가 — `fixture/sample-app` (= layout 디렉토리의 5 ancestors up). FIXTURE_MODULE_SUBPATH 상수. | +30 |
| C2 | `server/layoutlib-worker/.../classloader/SampleAppClasspathManifest.kt` | manifest 파일 (텍스트, 한 줄에 한 절대 경로) 파싱. 누락/오래됨/형식 오류 → 명시 예외. **input 은 sampleAppModuleRoot** (B1 수정). | 60 |
| C3 | `server/layoutlib-worker/.../classloader/AarExtractor.kt` | `.aar` ZIP 에서 `classes.jar` 를 stable cache (`<sample-app>/app/build/axp/aar-classes/<sha1>/<artifact>.jar`) 로 추출. mtime 기반 idempotent. **atomic write** (temp + ATOMIC_MOVE). | 80 |
| C4 | `server/layoutlib-worker/.../classloader/SampleAppClassLoader.kt` | 위 manifest+extractor 결과 + R.jar 위치 → `URLClassLoader(parent = isolatedCL)`. 빌드된 CL 인스턴스 + 진단용 entry list. **input 은 sampleAppModuleRoot** (B1 수정). | 70 |
| C5 | `server/layoutlib-worker/.../classloader/ClassLoaderConstants.kt` | 모든 magic strings/번호 상수화 (B4): manifest 파일명, R.jar 상대 경로, AAR cache 디렉토리명, `"classes.jar"` entry, `"SHA-1"` digest, `".aar"`/`".jar"` 확장자, `"app"`/`"build"` path 세그먼트. | 50 |
| C6 | `MinimalLayoutlibCallback.kt` (수정) | 생성자에 `viewClassLoader: () -> ClassLoader` 추가 (lazy provider, F1/Q4). `loadView`/`findClass` reflection. `hasAndroidXAppCompat` true. UnsupportedOperationException 분기 제거. **InvocationTargetException unwrap** (Q3). | +40 |
| C7 | `LayoutlibRenderer.kt` (수정) | 생성자에 `sampleAppModuleRoot: Path` 인자 추가 (B1, no default). `renderViaLayoutlib` 가 SampleAppClassLoader 를 lazy 빌드 후 callback 에 주입. | +25 |
| C7b | `Main.kt` / `SharedLayoutlibRenderer.kt` / `LayoutlibRendererTier3MinimalTest.kt` 등 모든 호출부 (수정) | 새 인자 `sampleAppModuleRoot` 명시 주입 — CLAUDE.md "no default parameter values" 정책. CLI override `--sample-app-root`. | +15 |
| C8 | `LayoutlibRendererIntegrationTest.kt` (수정) | `@Disabled` 제거. T1 gate assertion 추가 — `assertEquals(Result.Status.SUCCESS, ...)` (Q5). SharedLayoutlibRenderer 호출은 유지. | +15 |
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

### 4.0 C0 — FixtureDiscovery 확장 (B1 fix)

`server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt`:

```kotlin
object FixtureDiscovery {
    const val FIXTURE_SUBPATH = "fixture/sample-app/app/src/main/res/layout"
    const val FIXTURE_MODULE_SUBPATH = "fixture/sample-app"   // ← 신규

    // ... 기존 CANDIDATE_ROOTS / locate(...) 그대로 ...

    /** sample-app 모듈 루트 (= app/src/main/res/layout 의 5 ancestors up) 를 탐지. */
    fun locateModuleRoot(override: Path?): Path? = locateInternal(
        override = override,
        userDir = System.getProperty("user.dir"),
        candidateRoots = CANDIDATE_ROOTS,
        subpath = FIXTURE_MODULE_SUBPATH,
    )

    // 기존 locateInternal 시그니처에 subpath 파라미터 추가 (default 없음 — overload 두 개로 분리하거나
    // CLAUDE.md 정책에 따라 호출자가 양쪽 명시 인자 주입). 권장: 기존 locateInternal 은 그대로 유지하고
    // 신규 internal helper 함수 `locateInternalWithSubpath` 분리.
}
```

CLI override (`Main.kt`) 도 확장: 기존 `--fixture-root=<layout dir>` 외에 `--sample-app-root=<module dir>` 추가. CliConstants/CliArgs 에 신규 flag.

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
            .distinct()                // ← Claude-F4: dup 라인 제거 (Gradle resolve 가 직접+transitive 양쪽 path 노출)
            .sorted()
        val outFile = axpClasspathManifest.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(artifacts.joinToString("\n"))
    }
}
// Q1 (Codex+Claude convergent): afterEvaluate 제거. tasks.named 가 이미 lazy.
tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
```

**계약**:
- 출력 경로: `fixture/sample-app/app/build/axp/runtime-classpath.txt`.
- 형식: 라인 separator `\n`, 각 라인 = AAR 또는 JAR 의 절대경로. trailing newline 없음 (joinToString 결과).
- 정렬: lexicographic — reproducibility 를 위함.
- 경로는 `.aar` 또는 `.jar` 로 끝나야 함. 그 외 형식 발견 시 worker 가 거부 (TC1 가 검증).

**왜 emit 시점 = `finalizedBy(assembleDebug)`?**: 사용자 흐름 `./gradlew :app:assembleDebug` 가 보통 W3 dev 사이클의 entry point. 별도 task 명령 강제 시 forget 위험.

**페어 리뷰 Q1**: `applicationVariants.all` 또는 androidComponents API 를 써야 할까? — `debugRuntimeClasspath` 은 표준 configuration 이름 (AGP 8.x 안정), 직접 참조 OK. 그러나 향후 flavor 추가 시 break — 본 fixture 는 flavor 없음, defer.

### 4.2 C2 — SampleAppClasspathManifest

`sampleAppModuleRoot` 가 `fixture/sample-app` 모듈 루트 (B1 fix). MANIFEST_RELATIVE_PATH 도 module-root 기준으로 재정의 (§4.5).

```kotlin
package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.JAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
import java.nio.file.Files
import java.nio.file.Path

/**
 * <sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt 파일을 읽어
 * resolved runtime classpath 의 AAR/JAR 절대경로 리스트를 제공한다.
 *
 * 파일 형식 (axpEmitClasspath Gradle task 가 emit):
 *  - 라인 separator '\n', trailing newline 없음.
 *  - 각 라인 = AAR 또는 JAR 의 절대 경로.
 *  - 정렬: lexicographic, distinct.
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

### 4.3 C3 — AarExtractor

Atomic write 적용 — temp file + ATOMIC_MOVE (Codex follow-up).

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
 * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출.
 *
 * Cache 위치: `<cacheRoot>/aar-classes/<sha1(absPath)>/<artifactName>.jar`.
 * Idempotent: 캐시된 파일의 mtime >= AAR 의 mtime 이면 재사용.
 * Atomic: temp file 에 먼저 쓴 뒤 ATOMIC_MOVE — 동시 forks 에서도 race-free.
 *
 * AAR 안에 classes.jar 가 없는 경우 (e.g. 순수 resource-only AAR) → null 반환.
 * 손상된 ZIP → IOException pass-through.
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

**페어 리뷰 Q2**: 캐시 invalidation 을 mtime 기반으로 하면 AAR 이 동일 경로로 재배포되면서 mtime 만 갱신되는 시나리오가 깨지나? — Gradle cache 의 AAR 은 hash 디렉토리에 위치하므로 동일 path 의 contents 변경은 사실상 발생하지 않음 (Maven 좌표+버전이 같으면 contents 동일). hash mismatch 시 path 자체가 달라짐 → cacheRoot 안의 sha1 키도 다름. 안전.

### 4.4 C4 — SampleAppClassLoader

`sampleAppModuleRoot` (= `fixture/sample-app`) 가 모든 path 의 base. layoutlib 의 layout-dir `fixtureRoot` 와 분리 (B1 fix).

```kotlin
package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_BASE_RELATIVE_PATH
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
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
         * @param sampleAppModuleRoot `fixture/sample-app` 모듈 루트 (= FixtureDiscovery.locateModuleRoot 결과).
         * @param parent 본 CL 의 parent — 보통 LayoutlibBootstrap.createIsolatedClassLoader() 결과.
         */
        fun build(sampleAppModuleRoot: Path, parent: ClassLoader): SampleAppClassLoader {
            val manifest = SampleAppClasspathManifest.read(sampleAppModuleRoot)
            val cacheRoot = sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)
            val urls = mutableListOf<URL>()

            for (entry in manifest) {
                val asString = entry.toString()
                val jarPath = if (asString.endsWith(AAR_EXTENSION)) {
                    AarExtractor.extract(entry, cacheRoot) ?: continue // resource-only AAR skip
                } else {
                    entry
                }
                urls += jarPath.toUri().toURL()
            }

            // sample-app 의 R.jar — 모든 R.<package>.* 클래스 포함. 누락 시 NoClassDefFoundError
            // for androidx.constraintlayout.widget.R$styleable etc.
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

### 4.5 C5 — ClassLoaderConstants

CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" 정책에 따라 **모든** literal 을 상수화 (B4).

```kotlin
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

### 4.6 C6 — MinimalLayoutlibCallback 변경 (F1 + Q3 적용)

페어 리뷰 컨버전스:
- **F1**: `findClass` + `hasAndroidXAppCompat` override 추가 — `BridgeInflater` 의 `findCustomInflater` 가 AppCompat 자동 치환을 활성화하기 위해.
- **Q3 (Codex 입장 채택)**: `InvocationTargetException` 의 cause 를 unwrap 해 layoutlib 로 던짐 — log/diagnostic 에서 raw cause 가 보이도록.
- **Q4 (lazy)**: `viewClassLoader: () -> ClassLoader` lambda — activity_minimal 처럼 custom view 가 없으면 호출되지 않아 manifest 누락이 silent.

```kotlin
import java.lang.reflect.InvocationTargetException

class MinimalLayoutlibCallback(
    private val viewClassLoaderProvider: () -> ClassLoader,
) : LayoutlibCallback() {

    // ... 기존 nextId / byRef / byId / 동기화된 getOrGenerateResourceId / resolveResourceId 유지 ...

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
            // Q3: cause 를 그대로 throw — layoutlib BridgeInflater.createViewFromTag 가 InflateException 으로 wrap.
            throw ite.cause ?: ite
        }
    }

    /**
     * F1: BridgeInflater.findCustomInflater 가 isAppCompatTheme=true 일 때
     * `findClass("androidx.appcompat.app.AppCompatViewInflater")` 를 호출.
     * default impl 은 즉시 ClassNotFoundException → null 분기 → AppCompatViewInflater 비활성.
     * 우리 viewClassLoader 가 androidx.appcompat 을 보유하므로 정상 resolve 가능.
     */
    override fun findClass(name: String): Class<*> {
        return viewClassLoaderProvider().loadClass(name)
    }

    /**
     * F1: sample-app 의존 그래프에 androidx.appcompat 존재 → true. default false.
     * BridgeInflater.findCustomInflater 의 분기 활성화에 필수.
     */
    override fun hasAndroidXAppCompat(): Boolean = true

    // ... 나머지 (getParser / getAdapterBinding / getActionBarCallback / createXmlParser*) unchanged ...
}
```

### 4.7 C7 — LayoutlibRenderer 변경 (B1 + Q4 적용)

생성자에 `sampleAppModuleRoot: Path` 인자 추가 (B1, no default — CLAUDE.md 정책). lazy provider 로 callback 에 주입.

```kotlin
class LayoutlibRenderer(
    private val distDir: Path,
    private val fallback: PngRenderer?,
    private val fixtureRoot: Path,
    private val sampleAppModuleRoot: Path,  // ← 신규 (B1)
) : PngRenderer {

    // ... 기존 bootstrap / initialized / classLoader / bridgeInstance 그대로 ...

    @Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null

    @Synchronized
    private fun ensureSampleAppClassLoader(): ClassLoader {
        sampleAppClassLoader?.let { return it.classLoader }
        val isolated = classLoader ?: error("Bridge 가 init 안 됨 (initBridge 가 먼저 실행되어야 함)")
        val built = SampleAppClassLoader.build(sampleAppModuleRoot, isolated)
        sampleAppClassLoader = built
        return built.classLoader
    }

    private fun renderViaLayoutlib(layoutName: String): ByteArray? {
        // ... 기존 코드 (parser / bundle / resources) ...
        val params: SessionParams = SessionParamsFactory.build(
            layoutParser = parser,
            callback = MinimalLayoutlibCallback { ensureSampleAppClassLoader() },  // ← lazy provider
            resources = resources,
        )
        // ... 기존 코드 (bridge.createSession / render / image / dispose) ...
    }
}
```

**Q4 (lazy)**: `MinimalLayoutlibCallback` 의 `viewClassLoaderProvider: () -> ClassLoader` lambda 가 activity_minimal 처럼 custom view 가 없는 경우 호출 0 → manifest 누락 영향 없음 → W3D1 의 `tier3-values` 회귀 없음.

**모든 호출부 (C7b)**: `Main.kt`, `SharedLayoutlibRenderer.getOrCreate`, 그리고 production `LayoutlibRenderer(...)` 호출하는 모든 테스트에서 `sampleAppModuleRoot` 인자 명시. CLI override `--sample-app-root=<path>` 추가 (CliConstants/CliArgs).

### 4.8 C8 — LayoutlibRendererIntegrationTest 변경 (Q5 적용)

Q5 컨버전스: `Result.Status == SUCCESS` assertion 활성. `LayoutlibRendererTier3MinimalTest` 의 W3D1 패턴과 일관성 유지.

```kotlin
@Tag("integration")
class LayoutlibRendererIntegrationTest {

    @Test
    fun `tier3-basic — activity_basic renders SUCCESS with non-empty PNG`() {
        val dist = locateDistDir()
        val layoutRoot = locateFixtureRoot()
        val moduleRoot = locateSampleAppModuleRoot()
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = layoutRoot,
            sampleAppModuleRoot = moduleRoot,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_basic.xml")

        assertEquals(Result.Status.SUCCESS, renderer.lastSessionResult?.status, "render status SUCCESS 여야 함")
        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG bytes 가 placeholder 보다 큼: ${bytes.size}")
        assertTrue(
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
            "PNG magic 헤더가 아님",
        )
    }

    private fun locateSampleAppModuleRoot(): Path {
        val found = FixtureDiscovery.locateModuleRoot(null)
        assumeTrue(found != null, "sample-app module root 없음 — fixture/sample-app 확인")
        return found!!.toAbsolutePath().normalize()
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

## 9. Contingency — B2 empirical fallback

**B2 가설** (Codex round 1): `MaterialButton` 의 Material theme enforcement (`MaterialThemeOverlay.wrap`, `ThemeEnforcement.checkCompatibleTheme`) 가 framework theme + missing app/library resource values 환경에서 inflate 시 throw → `Result.Status != SUCCESS`.

**검증 단계** (구현 phase 의 첫 통합 테스트 실행 직후):
1. T1 gate (SUCCESS + PNG > 1000) PASS → B2 가설 무효, 본 스펙 그대로 close.
2. T1 gate FAIL with `ThemeEnforcement` 또는 `MaterialThemeOverlay` 관련 exception → §9.1 발동.

### 9.1 Contingency layout — `activity_basic_minimal.xml`

만약 §1 의 acceptance gate 가 MaterialButton 때문에 깨지면, 동일 fixture 디렉토리에 추가 layout 파일을 작성하고 `LayoutlibRendererIntegrationTest` 의 타겟을 그것으로 변경:

```xml
<!-- fixture/sample-app/app/src/main/res/layout/activity_basic_minimal.xml -->
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/fixture_basic_title"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <Button
        android:id="@+id/primary_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/fixture_basic_button"
        app:layout_constraintTop_toBottomOf="@id/title"
        app:layout_constraintStart_toStartOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

**왜 contingency 가 W3D3 의 의도를 지키나**:
- 핵심 deliverable = "in-JVM custom view classloading 증명". `androidx.constraintlayout.widget.ConstraintLayout` 도 layoutlib framework 외부 클래스이므로 classloader 경로의 모든 코드 (loadView / findClass / AAR 추출 / R.jar / parent chain) 가 동일하게 exercised.
- MaterialButton 만 표준 `android.widget.Button` (framework, isolated CL) 으로 교체 — 새 코드 경로 영향 없음.
- 원래 `activity_basic.xml` 은 보존 — W3D4 (app/library resource value loader) 완료 후 다시 타겟으로 환원.

**spec close 전 결정**: 구현 phase 에서 empirical 결과에 따라 §1 의 layout 이름을 "activity_basic.xml" 또는 "activity_basic_minimal.xml" 중 어느 것으로 fix 할지 work_log 에 기록.

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
- 2026-04-29 (round 1 페어 리뷰 반영): B1 (fixtureRoot 분리), F1 (findClass + hasAndroidXAppCompat), B4 (magic strings → ClassLoaderConstants), Q1 (afterEvaluate 제거), Q3 (ITE unwrap), Q5 (SUCCESS assertion 활성), manifest distinct, atomic AAR write. §9 contingency 신설 (B2 empirical risk).
