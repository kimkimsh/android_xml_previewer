# W3D3-α — Bytecode Rewriting + R.jar id Seeding 설계 스펙

> **Date**: 2026-04-29
> **Scope**: W3D3 의 branch (C) blocker 두 건을 ASM bytecode rewriting + R.jar id seeding 으로 해결.
> **Carried-from**: `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md` (옵션 α 선택).
> **Predecessor specs**: `2026-04-29-w3d3-l3-classloader-design.md` (round 2 close 상태).

## 0. 페어 리뷰 round 1 결과 + round 2 정정 (2026-04-29)

Round 1 페어 (Codex GPT-5.5 xhigh + Claude Opus 4.7 1:1):
- **Claude verdict**: NO_GO — 2 hard blockers (A1, A2) + 6 follow-ups.
  `docs/work_log/2026-04-29_w3d3-l3-classloader/alpha-spec-pair-review-round1-claude.md`
- **Codex verdict**: 명시적 verdict 미생성 — tool 측면 (benchmark, NAME_MAP code-gen) 작업 위주. 출력 그대로 보존하나 critical 분석은 Claude 측이 결정적. (`alpha-spec-pair-review-round1-codex.md`)

### round 2 본문 반영 (Claude empirical-verified findings)

- **A1** (HARD BLOCKER, 정정): NAME_MAP 25 → 4 entries 로 축소. layoutlib 14.0.11 JAR 검사 결과 `_Original_*` 외에 *non-prefixed 도 존재* 하는 클래스가 21개 — 이들은 layoutlib 의 의도된 shim. rewrite 시 working code 가 silently break. **본 round 2 가 NAME_MAP 을 Build family 4 entries 로 한정**:
  - `android/os/Build` (non-prefixed 부재 — rewrite 필수)
  - `android/os/Build$Partition`
  - `android/os/Build$VERSION`
  - `android/os/Build$VERSION_CODES`
- **A2** (HARD BLOCKER, 정정): seeder 가 `int` vs `int[]` filter 로 R$styleable 의 `int[]` array 만 skip 하면 1265개의 index int 필드 (`ActionBar_background = 0` 등) 가 byRef/byId 에 등록되어 byId[0..79] 가 last-writer-wins 로 corrupt. **본 round 2 가 R$styleable type 전체 skip** — `if (type == ResourceType.STYLEABLE) continue`. layoutlib 자체 `Bridge.parseStyleable` 가 styleable 를 별도 처리.
- **Q2 정정**: `FIRST_ID = 0x7F0A_0000` 이 R$interpolator 의 type-byte 영역과 직접 overlap. **본 round 2 에서 `FIRST_ID = 0x7F800000` 으로 시프트** — AAPT 가 사용하는 어떤 type byte (0x01-0x10) 와도 충돌하지 않는 high bit 영역. `advanceNextIdAbove` mitigation 은 보존 (다른 fixture 가 더 높은 id 사용 시 대비).
- **B1**: `CLASS_FILE_SUFFIX = ".class"` 를 `ClassLoaderConstants` 에 추가. 본 round 2 에 명시.
- **B2**: callsite count 정정 — "12 sites total" (`MinimalLayoutlibCallbackTest` 8 + `SessionParamsFactoryTest` 2 + `MinimalLayoutlibCallbackLoadViewTest` 1 + `LayoutlibRenderer` 1).
- **B3 (Material theme contingency)**: `activity_basic_minimal.xml` (§9.1 의 contingency layout) 를 본 W3D3-α 의 deliverable 에 **선제적 포함** — α 적용 후 Material theme enforcement throw 시 즉시 활용. 별도 round 진입 회피.
- **B4 (lambda → method)**: `LayoutlibRenderer` 의 seeder 합성 lambda 를 named private method 로 추출. CLAUDE.md "Lambdas — Avoid" 준수.
- **B5 (init race + 예외 wrapping)**: callback init 의 try-catch 로 `IllegalStateException("R.jar seeding failed", cause)` wrapping. layoutlib 의 single-thread invariant (`Bridge.getLock()` 보호) 명시 KDoc 추가.
- **D1 (cache key version)**: `AarExtractor` cacheRoot 에 `v$REWRITE_VERSION` 디렉토리 추가. NAME_MAP 변경 시 stale cache 회피.
- **D2 (magic chars)**: `INNER_CLASS_SEPARATOR = '$'`, `R_CLASS_NAME_SUFFIX = "/R"`, `INTERNAL_NAME_SEPARATOR = '/'`, `EXTERNAL_NAME_SEPARATOR = '.'` 모두 ClassLoaderConstants 에.

### Acceptance gate 변경 (B3)

T1 gate 의 fixture XML:
- **1차 시도**: `activity_basic.xml` (Material 그대로).
- **fallback (자동)**: T1 가 Material* stack trace 로 fail 시, 미리 작성된 `activity_basic_minimal.xml` (Button 으로 교체) 로 retry. round 1 의 §9.1 contingency 가 이미 본 round 2 deliverable 에 포함되어 즉시 활용 가능.

---

## 1. 요구사항

`activity_basic.xml` 의 `LayoutlibRendererIntegrationTest` (현재 `@Disabled`) 가 T1 gate 를 통과해야 함:
- `Result.Status.SUCCESS`
- PNG bytes > 1000
- PNG magic 헤더

본 W3D3-α 에서 다음 두 blocker 를 해결:
- **Blocker #1**: AAR 의 `android.os.Build.VERSION.SDK_INT` 참조가 `_Original_Build` 부재로 ClassNotFoundException → ASM rewriting 으로 해결.
- **Blocker #2**: R.jar 의 real id ↔ callback generated id 불일치 → R.jar id seeding 으로 해결.

In-scope: 새 컴포넌트 2개 (`AndroidClassRewriter` + `RJarSymbolSeeder`) + 통합 + 통합 테스트 enable.
Out-of-scope: AVD-L3, tier3-glyph, 다중 fixture, fidelity (visual pixel) 검증.

---

## 2. 아키텍처

### 2.1 데이터 흐름 변경

```
[기존 W3D3 round 2 흐름 — Tasks 1-8]
SampleAppClassLoader.build(sampleAppModuleRoot, parent=isolatedCL)
  ├─ manifest 파싱
  ├─ AarExtractor.extract(aar)  ← classes.jar 그대로 추출
  └─ URLClassLoader (rewritten 없음 + R.jar URL 만 추가)

[α 추가 흐름]
AarExtractor.extract(aar)
  └─ ZipFile 의 classes.jar entry 를 stream-read
     └─ stream 안의 각 .class 를 AndroidClassRewriter.rewrite(bytes)  ← 신규
     └─ rewritten classes.jar 를 cache 에 atomic 저장

MinimalLayoutlibCallback.<init>
  └─ rJarLoader.let { RJarSymbolSeeder.seed(it, this::registerSymbol) }  ← 신규
     └─ 본 callback 의 byRef/byId 가 R.jar 의 실 id 와 일치되도록 사전-populate
```

### 2.2 클래스로더 계층은 W3D3 round 2 그대로

```
system CL          ← Kotlin stdlib, layoutlib-api, kxml2
   ↑ parent
isolated CL        ← layoutlib JAR (android.view.* / _Original_Build*)
   ↑ parent
SampleAppClassLoader  ← rewritten AAR classes.jar + R.jar
```

α 의 변화는 **classes.jar 내용물의 transform** 만이며, 계층 구조는 무변경.

### 2.3 NAME_MAP — empirical 도출 (Round 2: 4 entries 만)

**Round 1 의 25 entries 가설은 잘못됐다** (Claude pair-review A1 발견).

layoutlib 14.0.11 JAR 검사 결과:
- 25개의 `_Original_*` prefix 클래스 중 **21개는 non-prefixed 버전도 함께 존재** — 즉 SurfaceView, WebView, ServiceManager, WindowManagerImpl, TextServicesManager 등은 layoutlib 가 의도적으로 dual-publish 한 것 (non-prefixed = 외부 AAR 가 사용할 SHIM, `_Original_` = 내부 구현). AAR 의 `new android.view.SurfaceView(ctx)` 같은 reference 는 *이미 정상 resolve*. rewrite 시 working code 를 silently break.
- 오직 **`android/os/Build` 가족 4개만 non-prefixed 부재** — 이들이 진짜 rewrite 대상.

empirical 검증:
```bash
$ unzip -l layoutlib-14.0.11.jar | grep -c "android/os/Build\.class"
0   ← rewrite 필수
$ unzip -l layoutlib-14.0.11.jar | grep -c "android/view/SurfaceView\.class"
1   ← non-prefixed 존재 → rewrite 금지
$ unzip -l layoutlib-14.0.11.jar | grep -c "android/webkit/WebView\.class"
1   ← non-prefixed 존재 → rewrite 금지
```

**최종 NAME_MAP** (4 entries):

| 원래 이름 | rewrite 대상 |
|-----------|--------------|
| `android/os/Build` | `android/os/_Original_Build` |
| `android/os/Build$Partition` | `android/os/_Original_Build$Partition` |
| `android/os/Build$VERSION` | `android/os/_Original_Build$VERSION` |
| `android/os/Build$VERSION_CODES` | `android/os/_Original_Build$VERSION_CODES` |

α 적용 후 다른 framework class CNFE 가 발화하면 *별도 root cause* 가능성 — investigate first 후 신규 매핑 추가 결정. branch (C) stack trace 는 정확히 `android.os.Build$VERSION` 만 명시했으므로 본 4 entries 로 sufficient 추정.

---

## 3. 컴포넌트 분해

| # | 파일 | 책임 | LOC |
|---|------|------|-----|
| α1 | `server/layoutlib-worker/build.gradle.kts` (수정) | `org.ow2.asm:asm:9.7` + `asm-commons:9.7` 의존 추가 | +2 |
| α2 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RewriteRules.kt` (신규) | `NAME_MAP` 25 entries + `Remapper` 인스턴스 | 60 |
| α3 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AndroidClassRewriter.kt` (신규) | `rewrite(classBytes: ByteArray): ByteArray` — `ClassReader`/`ClassRemapper`/`ClassWriter` | 30 |
| α4 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt` (수정) | classes.jar entries 를 stream 으로 읽고 각 .class 를 `AndroidClassRewriter.rewrite` 후 새 jar 에 write | +50 |
| α5 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt` (신규) | `seed(rJarPath, registerSymbol: (ResourceReference, Int) -> Unit)` — R.jar 의 모든 R$* 클래스를 walk + 각 정적 int 필드를 enumerate | 90 |
| α6 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarTypeMapping.kt` (신규) | R$type 의 simple name (e.g., "attr", "style", "styleable") → `com.android.resources.ResourceType` enum | 50 |
| α7 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` (수정) | 생성자에 `rJarSeeder: (MinimalLayoutlibCallback) -> Unit` 추가, init 에서 invoke. byRef/byId 에 사전 populate 가능하도록 `registerSymbol` private method 노출 (internal) | +25 |
| α8 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` (수정) | `ensureSampleAppClassLoader` 가 빌드한 후 그 classloader 의 R.jar URL 로부터 seeder 함수 합성 → callback 에 주입 | +15 |
| α9 | `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` (수정) | `@Disabled` 제거 (round 2 의 SUCCESS assertion 등은 그대로) | -5 |
| **Tests** | | | |
| TC-α1 | `RewriteRulesTest` | NAME_MAP 25 entries 무결성 + remapper 가 unknown 이름 그대로 반환 | 60 |
| TC-α2 | `AndroidClassRewriterTest` | (a) Build 참조하는 합성 .class → _Original_Build 로 rewritten / (b) Build 무관 .class → no change / (c) field type signature 도 rewritten | 130 |
| TC-α3 | `AarExtractorRewriteTest` | extract 결과 classes.jar 안의 각 .class 가 NAME_MAP 적용됐는지 Round-trip ZipFile 로 확인 | 100 |
| TC-α4 | `RJarSymbolSeederTest` | (a) 합성 R.jar 의 모든 R$* 클래스 walk / (b) static int 필드 register 됨 / (c) int[] 필드 (R$styleable) skip / (d) namespace + type 정확 추출 | 130 |
| TC-α5 | `RJarTypeMappingTest` | "attr"/"style"/"layout"/... 모두 매핑 + 알 수 없는 type → null | 60 |

production ~270 LOC + tests ~480 LOC.

---

## 4. 상세 스펙

### 4.1 RewriteRules.kt (round 2 — 4 entries)

```kotlin
package dev.axp.layoutlib.worker.classloader

import org.objectweb.asm.commons.Remapper

/**
 * layoutlib 14.0.11 의 자체 build pipeline 이 의도적으로 _Original_ prefix 만 publish 하고
 * 외부용 SHIM 을 미포함시킨 클래스들의 매핑. host-JVM 환경에서 AAR bytecode 의
 * `android/os/Build` reference 를 layoutlib 의 실재 `android/os/_Original_Build` 로 rewrite.
 *
 * Round 1 페어 리뷰 (Claude empirical) 의 critical finding: layoutlib JAR 의 25개
 * `_Original_*` prefix 클래스 중 21개 (SurfaceView/WebView/ServiceManager/WindowManagerImpl/
 * TextServicesManager) 는 *non-prefixed 버전도 함께 존재* — layoutlib 의 의도된 dual-publish.
 * 본 NAME_MAP 은 non-prefixed 가 *부재한* Build family 4 entries 만 포함.
 *
 * 향후 layoutlib 버전 변경 시 empirical 재검증:
 *   for c in $(unzip -l layoutlib-X.X.X.jar | grep "_Original_" | awk '{print $NF}'); do
 *     non=$(echo $c | sed 's|_Original_||')
 *     if ! unzip -l layoutlib-X.X.X.jar | grep -q "$non"; then echo "REWRITE: $c"; fi
 *   done
 */
internal object RewriteRules {

    val NAME_MAP: Map<String, String> = mapOf(
        "android/os/Build" to "android/os/_Original_Build",
        "android/os/Build\$Partition" to "android/os/_Original_Build\$Partition",
        "android/os/Build\$VERSION" to "android/os/_Original_Build\$VERSION",
        "android/os/Build\$VERSION_CODES" to "android/os/_Original_Build\$VERSION_CODES",
    )

    val REMAPPER: Remapper = object : Remapper() {
        override fun map(internalName: String): String =
            NAME_MAP[internalName] ?: internalName
    }
}
```

### 4.2 AndroidClassRewriter.kt

```kotlin
package dev.axp.layoutlib.worker.classloader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper

/**
 * 단일 .class bytecode 를 받아 RewriteRules.REMAPPER 로 type reference 를 rewrite.
 * ClassWriter 의 COMPUTE_MAXS 만 사용 (frame 재계산 불요 — type rename 만 수행).
 */
internal object AndroidClassRewriter {
    fun rewrite(classBytes: ByteArray): ByteArray {
        val reader = ClassReader(classBytes)
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        reader.accept(ClassRemapper(writer, RewriteRules.REMAPPER), 0)
        return writer.toByteArray()
    }
}
```

### 4.3 AarExtractor 수정

기존 `extract(aarPath, cacheRoot)` 가 AAR 안의 `classes.jar` 를 그대로 추출. α 에서는 추출 *직후* 본 jar 를 rewrite:

```kotlin
fun extract(aarPath: Path, cacheRoot: Path): Path? {
    // ... 기존 require / sha1 / outDir / mtime check ...

    val tmpJar = outDir.resolve(artifactName + TEMP_JAR_SUFFIX)
    ZipFile(aarPath.toFile()).use { aarZip ->
        val entry = aarZip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
        aarZip.getInputStream(entry).use { input ->
            // α: classes.jar 의 entries 각각을 rewrite
            rewriteClassesJar(input, tmpJar)
        }
    }
    Files.move(tmpJar, outJar, ATOMIC_MOVE, REPLACE_EXISTING)
    return outJar
}

private fun rewriteClassesJar(input: InputStream, outPath: Path) {
    ZipInputStream(input).use { zin ->
        ZipOutputStream(Files.newOutputStream(outPath)).use { zout ->
            var entry = zin.nextEntry
            while (entry != null)
            {
                val bytes = zin.readBytes()
                val rewritten = if (entry.name.endsWith(CLASS_FILE_SUFFIX))
                {
                    AndroidClassRewriter.rewrite(bytes)
                }
                else
                {
                    bytes
                }
                zout.putNextEntry(java.util.zip.ZipEntry(entry.name))
                zout.write(rewritten)
                zout.closeEntry()
                entry = zin.nextEntry
            }
        }
    }
}
```

`CLASS_FILE_SUFFIX = ".class"` 추가 — `ClassLoaderConstants` 에.

### 4.4 RJarTypeMapping.kt

```kotlin
package dev.axp.layoutlib.worker.classloader

import com.android.resources.ResourceType

/**
 * R$<simpleName> 의 simpleName → ResourceType 매핑.
 * R.jar 안의 모든 inner class 이름을 ResourceType enum 으로 변환.
 *
 * 매핑 누락 시 null 반환 → 호출자가 그 R$* 클래스 전체 skip.
 */
internal object RJarTypeMapping {

    private val MAPPING: Map<String, ResourceType> = mapOf(
        "anim" to ResourceType.ANIM,
        "animator" to ResourceType.ANIMATOR,
        "array" to ResourceType.ARRAY,
        "attr" to ResourceType.ATTR,
        "bool" to ResourceType.BOOL,
        "color" to ResourceType.COLOR,
        "dimen" to ResourceType.DIMEN,
        "drawable" to ResourceType.DRAWABLE,
        "font" to ResourceType.FONT,
        "fraction" to ResourceType.FRACTION,
        "id" to ResourceType.ID,
        "integer" to ResourceType.INTEGER,
        "interpolator" to ResourceType.INTERPOLATOR,
        "layout" to ResourceType.LAYOUT,
        "menu" to ResourceType.MENU,
        "mipmap" to ResourceType.MIPMAP,
        "navigation" to ResourceType.NAVIGATION,
        "plurals" to ResourceType.PLURALS,
        "raw" to ResourceType.RAW,
        "string" to ResourceType.STRING,
        "style" to ResourceType.STYLE,
        "styleable" to ResourceType.STYLEABLE,
        "transition" to ResourceType.TRANSITION,
        "xml" to ResourceType.XML,
    )

    fun fromSimpleName(simpleName: String): ResourceType? = MAPPING[simpleName]
}
```

### 4.5 RJarSymbolSeeder.kt (round 2 — R$styleable 전체 skip)

**round 1 의 critical fix (Claude pair-review A2)**: 기존 `field.type != Int::class.javaPrimitiveType` filter 는 `int[]` 만 skip 하고 `int` 인덱스 필드 (`ActionBar_background = 0` 등 1265개/styleable class) 는 그대로 register → byId[0..79] 가 styleable 들 사이에서 last-writer-wins 로 corrupt. 본 round 2 는 **R$styleable type 전체 skip** — layoutlib `Bridge.parseStyleable` 가 styleable 자체를 별도 처리하므로 R.jar reflection 에서 styleable 을 다룰 필요 자체가 없음.

```kotlin
package dev.axp.layoutlib.worker.classloader

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.CLASS_FILE_SUFFIX
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.INNER_CLASS_SEPARATOR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.INTERNAL_NAME_SEPARATOR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTERNAL_NAME_SEPARATOR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_CLASS_NAME_SUFFIX
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * sample-app `R.jar` 의 모든 R$<type> 클래스를 enumerate 하여, 각 정적 int 필드를
 * (ResourceReference, id) 로 callback 에 등록.
 *
 * 처리:
 *  - R$styleable 전체 skip — round 2 fix. layoutlib Bridge.parseStyleable 이 styleable 을 별도 처리.
 *  - 알 수 없는 type (RJarTypeMapping 미매핑) 인 R$* 는 skip.
 *  - int[] 필드는 부수적으로 skip (정상적이지만 sanity).
 */
internal object RJarSymbolSeeder {

    fun seed(
        rJarPath: Path,
        rJarLoader: ClassLoader,
        register: (ResourceReference, Int) -> Unit,
    ) {
        ZipFile(rJarPath.toFile()).use { zip ->
            for (entry in zip.entries())
            {
                if (!entry.name.endsWith(CLASS_FILE_SUFFIX))
                {
                    continue
                }
                val internalName = entry.name.removeSuffix(CLASS_FILE_SUFFIX)
                val parts = parseRClassName(internalName) ?: continue
                val (packageName, typeSimpleName) = parts
                val resourceType = RJarTypeMapping.fromSimpleName(typeSimpleName) ?: continue
                if (resourceType == ResourceType.STYLEABLE)
                {
                    // round 2 fix: styleable 의 index int 필드 (ActionBar_background = 0 등) 가
                    // byId 를 corrupt 시킴. layoutlib 자체로 styleable 처리하므로 skip.
                    continue
                }
                seedClass(
                    rJarLoader,
                    internalName.replace(INTERNAL_NAME_SEPARATOR, EXTERNAL_NAME_SEPARATOR),
                    packageName,
                    resourceType,
                    register,
                )
            }
        }
    }

    /**
     * @return Pair(packageName, typeSimpleName) — 예: ("androidx.constraintlayout.widget", "attr").
     * R 클래스 이름 패턴 안 맞으면 null. 패키지 없는 bare `R$attr` 도 null (`/R` 접미사 부재).
     */
    private fun parseRClassName(internalName: String): Pair<String, String>? {
        val dollarIdx = internalName.lastIndexOf(INNER_CLASS_SEPARATOR)
        if (dollarIdx < 0)
        {
            return null
        }
        val before = internalName.substring(0, dollarIdx)
        val after = internalName.substring(dollarIdx + 1)
        if (!before.endsWith(R_CLASS_NAME_SUFFIX))
        {
            return null
        }
        val packageInternal = before.removeSuffix(R_CLASS_NAME_SUFFIX)
        return packageInternal.replace(INTERNAL_NAME_SEPARATOR, EXTERNAL_NAME_SEPARATOR) to after
    }

    private fun seedClass(
        loader: ClassLoader,
        fqcn: String,
        packageName: String,
        type: ResourceType,
        register: (ResourceReference, Int) -> Unit,
    ) {
        val cls = try
        {
            loader.loadClass(fqcn)
        }
        catch (t: Throwable)
        {
            return
        }
        val namespace = ResourceNamespace.fromPackageName(packageName)
        for (field in cls.declaredFields)
        {
            if (!Modifier.isStatic(field.modifiers))
            {
                continue
            }
            if (field.type != Int::class.javaPrimitiveType)
            {
                continue
            }
            field.isAccessible = true
            val value = field.getInt(null)
            register(ResourceReference(namespace, type, field.name), value)
        }
    }
}
```

### 4.6 MinimalLayoutlibCallback 수정

```kotlin
class MinimalLayoutlibCallback(
    private val viewClassLoaderProvider: () -> ClassLoader,
    /**
     * α: callback 생성 직후 호출되는 seeder. R.jar id seeding 등 사전-populate 작업.
     * lambda 기본값 없음 (CLAUDE.md "no default parameter values").
     */
    private val initializer: (registerSymbol: (ResourceReference, Int) -> Unit) -> Unit,
) : LayoutlibCallback() {

    private val nextId = AtomicInteger(FIRST_ID)
    private val byRef = mutableMapOf<ResourceReference, Int>()
    private val byId = mutableMapOf<Int, ResourceReference>()

    init {
        // round 2 fix B5: initializer 가 R.jar I/O 실패 등으로 throw 시 layoutlib 진단 로그에서
        // 원인이 잘 보이도록 IllegalStateException 으로 wrap.
        // layoutlib 의 single-thread invariant (Bridge.getLock() 보호) 덕분에 init 의 byRef/byId
        // 쓰기와 이후 render 의 read 사이에 happens-before 보장. callback instance 가 SessionParams
        // 로 publish 되기 전에 init 완료.
        try
        {
            initializer { ref, id ->
                byRef[ref] = id
                byId[id] = ref
                advanceNextIdAbove(id)
            }
        }
        catch (t: Throwable)
        {
            throw IllegalStateException("R.jar 시드 중 실패: ${t.message}", t)
        }
    }

    private fun advanceNextIdAbove(seeded: Int) {
        // CAS 로 nextId >= seeded + 1 보장. R.jar 의 id (`0x7F010001` 등) 가 FIRST_ID
        // (round 2 에서 `0x7F800000`) 보다 작으면 no-op. 큰 경우 monotonic 증가.
        while (true)
        {
            val current = nextId.get()
            if (current > seeded)
            {
                return
            }
            if (nextId.compareAndSet(current, seeded + 1))
            {
                return
            }
        }
    }

    @Synchronized
    override fun getOrGenerateResourceId(ref: ResourceReference): Int {
        byRef[ref]?.let { return it }
        val id = nextId.getAndIncrement()
        byRef[ref] = id
        byId[id] = ref
        return id
    }

    // ... resolveResourceId / loadView / findClass / hasAndroidXAppCompat 등 그대로 ...
}
```

### 4.7 LayoutlibRenderer 수정 (round 2 — lambda → named method, B4)

CLAUDE.md "Lambdas — Avoid" 준수. seeder 합성을 named private method 로 추출:

```kotlin
private fun renderViaLayoutlib(layoutName: String): ByteArray? {
    // ... 기존 코드 ...

    val params: SessionParams = SessionParamsFactory.build(
        layoutParser = parser,
        callback = MinimalLayoutlibCallback(
            viewClassLoaderProvider = { ensureSampleAppClassLoader() },
            initializer = ::seedRJarSymbols,  // ← method reference (lambda 회피)
        ),
        resources = resources,
    )
    // ... 기존 코드 ...
}

/**
 * α: callback init 에서 호출. R.jar 의 모든 R$<type> 클래스를 enumerate 하여 등록.
 * SampleAppClassLoader 가 R.jar URL 을 갖고 있으므로 그것으로 reflection.
 */
private fun seedRJarSymbols(register: (ResourceReference, Int) -> Unit) {
    val sampleAppCL = ensureSampleAppClassLoader()
    val rJarPath = sampleAppModuleRoot.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH)
    RJarSymbolSeeder.seed(rJarPath, sampleAppCL, register)
}
```

호출부 (round 2 정정 — Claude pair-review B2): **12 sites total** —
- `MinimalLayoutlibCallbackTest.kt`: 8 sites
- `SessionParamsFactoryTest.kt`: 2 sites
- `MinimalLayoutlibCallbackLoadViewTest.kt`: 1 site
- `LayoutlibRenderer.kt`: 1 site (production)

각 test 호출부: `MinimalLayoutlibCallback({ cl }, { /* no-op initializer */ })`. initializer 가 register 를 호출하지 않아 기존 byRef/byId 가 빈 상태로 시작 (W3D3 round 2 동작 그대로).

또한 `MinimalLayoutlibCallback.companion` 의 `FIRST_ID = 0x7F0A_0000` 을 `0x7F80_0000` 로 변경 (round 2 Q2 정정). 본 변경은 W3D3 round 2 spec 의 callback 정의 수정에 해당.

### 4.8 LayoutlibRendererIntegrationTest + activity_basic_minimal 활성화

W3D3 round 2 에서 이미 SUCCESS assertion + `requireNotNull` 등이 작성됨. α 의 추가 변경:
1. `@Disabled` annotation 제거.
2. **Material theme contingency 자동화** (round 1 §9.1 → 본 round 2 deliverable 통합):
   - `fixture/sample-app/app/src/main/res/layout/activity_basic_minimal.xml` 신규 작성 (Button → 표준 Button, MaterialButton 회피).
   - 통합 테스트가 `activity_basic.xml` 1차 시도 → Material* stack trace 로 fail 시 `activity_basic_minimal.xml` 로 자동 retry.
   - 두 layout 모두 fail 시 BLOCKED (예상 외 root cause).

---

## 5. 위험 / 알려진 한계

### 5.1 위험

| ID | 위험 | 완화 |
|----|------|------|
| Rα-1 | NAME_MAP 가 필요 매핑을 누락 — 본 25 외에 layoutlib 가 다른 framework class 도 rewrite 했을 수 있음 | empirical 검증: 모든 `_Original_*` 가 NAME_MAP 의 reverse 방향에 정확 매핑됨을 확인. 추가 발견 시 한 줄 추가. |
| Rα-2 | classes.jar rewrite 가 stack frame 재계산을 트리거 (ClassWriter.COMPUTE_FRAMES 미사용) → bytecode 무결성 issue | type rename 만 수행하고 stack 변경은 없으므로 COMPUTE_MAXS 로 충분. ASM 표준 패턴. |
| Rα-3 | R.jar 의 일부 R$* 클래스가 reflection 시 NoClassDefFoundError | seeder 가 try-catch 로 skip — robust |
| Rα-4 | id seeding 후 0x7F0A_0000 이상의 id 가 R.jar 에 존재해 nextId 와 collision | `advanceNextIdAbove` 가 seed 후 nextId 를 monotonically 증가. CAS 로 thread-safe. |
| Rα-5 | sample-app 의 com.fixture.R 같은 application R 의 id 가 0x7F0A_xxxx 와 겹침 | 동일 ResourceReference (namespace + type + name) 라면 register 가 byRef 에 같은 entry 한 번. 다른 ref 라면 두 id 가 같은 byRef 매핑이 안 됨 — 후순위 등록이 byRef 에서 conflict 가능. **mitigation**: register 가 `if byRef.contains(ref) ignore` 정책. (단, sample-app 환경에서 발생 불가능 — 0x7F0A_0000 아래 R.jar id 만 존재.) |
| Rα-6 | Material/AppCompat 의 ThemeEnforcement 가 별도 throw (round 1 Codex B2 의 가설) | branch (C) 진단에서 stack trace 가 ThemeEnforcement 가 *아닌* Build CNFE 였음 — α 가 Build 를 해결한 후 ThemeEnforcement 가 재발화하면 §5.2 발동. |

### 5.2 contingency — α 적용 후 추가 blocker 발견 시

α 적용 후 통합 테스트가 다시 FAIL 할 가능성:
- (A) Build 와 ServiceManager 가 해결되었으나 layoutlib 의 다른 internal API 가 누락 → empirical NAME_MAP 확장.
- (B) R.jar id seeding 만으로 부족하고 추가로 *resource value* 도 RenderResources 에 주입해야 함 → app/library resource value loader (W3D4 carry, 별도 work).
- (C) ThemeEnforcement throw — `SessionConstants.DEFAULT_FRAMEWORK_THEME` (`Theme.Material.Light.NoActionBar`) 는 frame work theme. AppCompat 자동 치환이 활성화되었으므로 inflation 은 `AppCompatTextView` 사용 → AppCompat 이 framework theme 와 호환 점검 가능 (?attr/colorAccent 등). throw 면 `setTheme` 를 sample-app 의 `Theme.AxpFixture` 로 교체 필요 — 이는 W3D4 의 resource value loader 와 함께만 가능.

α 의 contingency: 발화 시 hard-stop 후 진단 (W3D3-α 자체의 branch (C)).

---

## 6. 페어 리뷰 질문 (Q1-Q3)

### Q1 — NAME_MAP 25 entries 가 layoutlib 14.0.11 의 전수인가?
empirical 검증: `unzip -l layoutlib-14.0.11.jar | grep "_Original_" | wc -l` = 25. 매핑 25 entries 는 그 전수 1:1. 단, layoutlib 이 *간접적으로* rewrite 한 다른 reference (예: import statement 의 type signature) 가 있을 가능성 — Paparazzi 의 transformer 와 비교 필요.

### Q2 — R.jar id 와 callback generated id collision 처리
seeder 가 등록한 id 가 0x7F0A_0000 ~ 0x7F0A_FFFF 범위와 겹치는지? sample-app 의 R.jar id 분포: AGP 가 0x7F010001 (attr), 0x7F060001 (color), 0x7F0F0001 (style) 등 type 별 sub-range 사용. 0x7F0A 는 `R.id` (id 0x0A) 이지만 sample-app 에서는 미사용 (com.fixture 가 layout id 를 layout XML 에 정의 안 함). 그러나 transitive AAR (material 의 R.id 등) 는 0x7F0A 범위 사용. **Codex/Claude 어느 입장이 맞는지 검증 필요.**

### Q3 — `R$styleable` int[] 필드 skip 정책
seeder 가 `R$styleable.MaterialButton` (= `int[]`) 을 skip. layoutlib 의 RenderResources 가 styleable 을 어떻게 lookup 하나? styleable 은 attr id 의 array 이므로 attr 자체가 등록되어 있으면 충분 — array 자체를 register 하지 않아도 layoutlib 의 `obtainStyledAttributes(set, attrs[])` 가 array 의 각 attr id 로 lookup. 검증: layoutlib 14.x 의 `BridgeContext.obtainStyledAttributes` 가 attr id array 를 caller 가 준 것으로 사용하므로 array 자체 등록 불요. **Codex 검증 권장.**

---

## 7. Acceptance Gate (T1)

본 W3D3-α 의 close 조건:

```kotlin
// LayoutlibRendererIntegrationTest 가 expect:
val bytes = renderer.renderPng("activity_basic.xml")
assertEquals(SUCCESS, renderer.lastSessionResult?.status)
assertTrue(bytes.size > 1000)
assertTrue(bytes[0..3] == PNG_MAGIC)
```

전체 게이트:
- 142 unit + 23 신규 unit = ~165 unit PASS
- 11 → 12 integration PASS, 1 SKIP (tier3-glyph)
- BUILD SUCCESSFUL

---

## 8. 변경 로그

- 2026-04-29: 초안 작성. W3D3 branch (C) 의 옵션 α 진입.
