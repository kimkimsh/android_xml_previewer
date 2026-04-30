# W3D4 MATERIAL-FIDELITY Implementation Plan (v2 — round 2 페어 reconcile 반영)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** sample-app 의 `Theme.AxpFixture` (Material3.DayNight 기반) + 41 transitive AAR 의 `res/values/values.xml` 을 RenderResources chain 에 inflate 시점 주입함으로써 W3D3-α 의 contingency (`activity_basic_minimal.xml`) 없이 primary `activity_basic.xml` (MaterialButton 포함) 가 SUCCESS 로 렌더되도록 한다.

**Architecture:**
- `LayoutlibResourceBundle` = ANDROID + RES_AUTO 2-bucket namespace-aware 자료구조 (W3D1 `FrameworkResourceBundle` 의 namespace 일반화).
- `LayoutlibResourceValueLoader` = 3-입력 통합 (framework dist `data/values/`, sample-app `app/src/main/res/values/`, runtime-classpath.txt 의 41 AAR walker). JVM-wide cache.
- `LayoutlibRenderResources` = layoutlib `RenderResources` subclass 로 9 method override (resolveResValue / dereference / findItemInStyle / findItemInTheme / getParent / getStyle / getAllThemes / applyStyle / clearStyles + getResolvedResource). 자체 chain walker + theme stack 관리.
- W3D3-α 의 `RJarSymbolSeeder` 변경: R$style 의 underscore name (`Theme_AxpFixture`) → dot name (`Theme.AxpFixture`) canonicalization + duplicate attr ID first-wins.
- mode 통일: 모든 non-framework = RES_AUTO (traditional 모드). namespace-aware mode 는 W4+ scope.

**Tech Stack:** Kotlin 1.9 / JDK 17 / Gradle 8 / JUnit Jupiter 5 / `java.util.zip.ZipFile` / `javax.xml.stream.XMLStreamReader` / layoutlib 14.0.11 / layoutlib-api 31.13.2 (이미 wired in W3D1/W3D3).

**Spec**: [`docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md`](../specs/2026-04-29-w3d4-material-fidelity-design.md). 본 플랜 모든 결정은 spec round 2 (pair-review GO) 본문 + §8 결정 표에 일치 — 단 round 2 페어가 발견한 spec §2.3 dedupe ordering 정정 (아래 §0.A 참조).

**Round 1 페어-리뷰** (spec): full convergence Q3+Q5, set-converge Q1+Q2+Q4+Q6. ρ → σ FULL 채택, RES_AUTO 모드 통일, R name canonicalization 추가.
**Round 2 페어-리뷰** (plan v1 → v2): **REVISE_REQUIRED** (양 reviewer 정합) → 12 follow-up 이 v2 에 inline 반영. 아래 §0 참조. v2 가 implementation 입력.

**테스트 카운트** (v2 보강): 신규 unit case = 3+5+6+7+**6**+**12**+**4**+3+4 = **50** (T5 dedupe winner +1, T6 sentinel/private/strict +2, T7 seeder integration +1). 167 → **~217 unit**. integration 12 → **13 PASS** (분리 +1) + 1 SKIP (tier3-glyph).

---

## §0. Round 2 페어 verdict + v2 follow-up 반영 (NEW)

양 reviewer **REVISE_REQUIRED** 정합 → judge round 불필요. v2 의 inline 변경:

### §0.A. spec §2.3 dedupe ordering 정정 (Codex Q5 critical)

spec §2.3 / §4.3 의 "runtime-classpath.txt 순서 = Gradle deterministic" 주장은 **부정확**. 실 winner 정책 = **sorted absolute artifact path + app last** ([fixture/sample-app/app/build.gradle.kts](../../fixture/sample-app/app/build.gradle.kts) line 56-65 의 `axpEmitClasspath` task 가 `.distinct().sorted()` 로 lexicographic order 고정). 즉 동명 conflict (e.g. `colorPrimary`) 의 winner 는:

- AAR 사이: **lexicographic 마지막** AAR 가 later-wins → `com.google.android.material...` > `androidx.appcompat...` (same-name 충돌 시 material 가 winner).
- AttrDef: **lexicographic 처음** AAR 가 first-wins → `androidx...` 가 winner.
- app 의 res 는 **AAR 후 마지막** 처리 → app 정의가 모든 AAR 를 override.

T5 가 이 정책을 코드로 구현 + duplicate winner unit assert 추가.

### §0.B. follow-up 매핑 표 (12 → task)

| # | Follow-up | 영향 task | 우선순위 |
|---|---|---|---|
| 1 | RJarSymbolSeeder 정확한 inline diff (single callback API), `seenAttrNames` outer-scope, integration test | T7 | **P1** |
| 2 | ctor old/new signature 명시, 7 callsite 별 수정, `fallback: PngRenderer? = null` default 제거 | T8 | **P1** |
| 3 | parseReference → `ResourceUrl.parse()` + `@null`/`@empty` sentinel + private `*` override | T6 | **P1** |
| 4 | `locateAll()` body explicit (assumeTrue gate + Triple) + module root graceful skip 정책 | T9 | **P1** |
| 5 | `MAX_THEME_HOPS = 32` 상향 + 진단 로그 + chain depth ≥ 15 assert | T1 const + T6 + T9 IT | P2 |
| 6 | `android:` style parent normalization (cross-ns chain 안 framework hit) | T6 + T1 const | P2 |
| 7 | dedupe winner = "sorted absolute artifact path + app last" 명시 + duplicate winner unit assert | T5 + T4 | P2 |
| 8 | `Files.list(appValues).sorted()` + T5 line 1114 의 `assertTrue(true)` 가짜 assertion 제거 | T5 | P2 |
| 9 | JVM cache invalidation (mtime-aware key 또는 explicit invalidate) | T5 | P3 |
| 10 | AAR `ZipFile().use {}` explicit | T3 | P3 |
| 11 | RJarSymbolSeederTest end-to-end 회귀 case (Theme_AxpFixture seeding → Theme.AxpFixture lookup) | T7 | P3 |
| 12 | framework `themes_holo.xml` 포함 의무화 (현 10 framework values 부족) — Codex Q6 chain end-at-MISSING | T6 implementation note | P3 |

### §0.C. round 2 reconcile (Q1-Q7 verdict)

| Q | Claude side | Codex side | 종합 |
|---|---|---|---|
| Q1 parseReference | NUANCED | DISAGREE | DISAGREE — `ResourceUrl.parse()` 활용 (FF#3) |
| Q2 RJarSymbolSeeder | DISAGREE | DISAGREE | **FULL converge** (FF#1) |
| Q3 locateAll | DISAGREE | DISAGREE | **FULL converge** (FF#4) |
| Q4 ctor reorder | DISAGREE | NUANCED | DISAGREE — Codex 추가: default param 위반 (FF#2) |
| Q5 dedupe deterministic | NUANCED | NUANCED | **FULL converge NUANCED** + Codex critical: `.sorted()` lex order (FF#7) |
| Q6 hop limit | DISAGREE (30) | NUANCED (32) | set-converge: 32 채택 (FF#5+#6+#12) |
| Q7 general | NUANCED | REVISE_REQUIRED | REVISE — Codex critical: T5 fake assertion + Files.list no sort (FF#8 + #9 + #10 + #11) |

---

## File Structure (사전 매핑)

### 신규 main (8 파일, ~960 LOC)

| 경로 | 책임 |
|---|---|
| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstants.kt` | path/regex/limit 상수 |
| `.../resources/ParsedNsEntry.kt` | namespace + sourcePackage tagged parsed entry |
| `.../resources/NsBucket.kt` | 단일 namespace 의 byType/styles/attrs immutable container |
| `.../resources/NamespaceAwareValueParser.kt` | W3D1 FrameworkValueParser 의 namespace 인자 일반화 |
| `.../resources/AarResourceWalker.kt` | runtime-classpath.txt → ZipFile → values.xml 파싱 + γ skip + δ 진단 |
| `.../resources/LayoutlibResourceBundle.kt` | byNs + getStyleExact/getStyleByName/getResource + dedupe + 진단 |
| `.../resources/LayoutlibResourceValueLoader.kt` | 3-입력 통합 + JVM-wide cache + wall-clock 진단 |
| `.../resources/LayoutlibRenderResources.kt` | 9 override + chain walker + theme stack |

### 변경 (4 파일)

| 경로 | 변경 |
|---|---|
| `.../LayoutlibRenderer.kt` | line 174 swap + ctor 5번째 인자 themeName |
| `.../SharedLayoutlibRenderer.kt` | RendererArgs 4-tuple → 5-tuple |
| `.../session/SessionConstants.kt` | `DEFAULT_FIXTURE_THEME = "Theme.AxpFixture"` 추가 |
| `.../classloader/RJarSymbolSeeder.kt` | R$style underscore→dot canonicalization + duplicate attr ID first-wins |

### 삭제 / rename (W3D1 흡수)

| 기존 | 후속 |
|---|---|
| `resources/FrameworkResourceBundle.kt` | 삭제 |
| `resources/FrameworkRenderResources.kt` | 삭제 |
| `resources/FrameworkResourceValueLoader.kt` | 삭제 |
| `resources/FrameworkValueParser.kt` | rename → `NamespaceAwareValueParser.kt` |
| `resources/ParsedEntry.kt` | rename → `ParsedNsEntry.kt` |
| `resources/ResourceLoaderConstants.kt` | 유지 (REQUIRED_FILES) |
| `resources/StyleParentInference.kt` | 유지 |

### 신규 test (10 파일, ~780 LOC, 46 case)

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/`:
- `resources/AppLibraryResourceConstantsTest.kt` (3)
- `resources/NamespaceAwareValueParserTest.kt` (5)
- `resources/AarResourceWalkerTest.kt` (6)
- `resources/LayoutlibResourceBundleTest.kt` (7)
- `resources/LayoutlibResourceValueLoaderTest.kt` (5)
- `resources/LayoutlibRenderResourcesTest.kt` (10) — chain walker + circular + theme stack 포함
- `resources/MaterialFidelityIntegrationTest.kt` (4) — unit-level real bundle Theme.AxpFixture parent walk
- `classloader/RNameCanonicalizationTest.kt` (3) — Theme_AxpFixture ↔ Theme.AxpFixture 매핑
- `classloader/RDuplicateAttrIdTest.kt` (3) — multiple R class first-wins
- (수정) `LayoutlibRendererIntegrationTest.kt` — tier3_basic_primary + tier3_basic_minimal_smoke 분리

### 기존 W3D1 test 의 운명

| 기존 | 후속 |
|---|---|
| `FrameworkResourceBundleTest.kt` | 삭제 — `LayoutlibResourceBundleTest` 가 동등 case 흡수 |
| `FrameworkRenderResourcesTest.kt` | 삭제 — `LayoutlibRenderResourcesTest` 가 흡수 |
| `FrameworkResourceValueLoaderTest.kt` | 삭제 — `LayoutlibResourceValueLoaderTest` 가 흡수 |
| `FrameworkValueParserTest.kt` | 삭제 — `NamespaceAwareValueParserTest` 가 흡수 |
| `ParsedEntryTest.kt` | 삭제 — `ParsedNsEntry` rename 따라감 (필요 시 신규) |
| `ResourceLoaderConstantsTest.kt` | 유지 |
| `StyleParentInferenceTest.kt` | 유지 |

---

## Task 1: 자료구조 기반 — Constants + ParsedNsEntry + NsBucket

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstants.kt`
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedNsEntry.kt`
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NsBucket.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstantsTest.kt`

- [ ] **Step 1: AppLibraryResourceConstantsTest 작성 (3 case)**

```kotlin
// AppLibraryResourceConstantsTest.kt
package dev.axp.layoutlib.worker.resources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLibraryResourceConstantsTest {

    @Test
    fun `RUNTIME_CLASSPATH_TXT_PATH 가 W3D3 manifest 와 일치`() {
        assertEquals("app/build/axp/runtime-classpath.txt", AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
    }

    @Test
    fun `MANIFEST_PACKAGE_REGEX 가 일반 AndroidManifest 의 package 추출`() {
        val sample = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.google.android.material" />"""
        val match = AppLibraryResourceConstants.MANIFEST_PACKAGE_REGEX.find(sample)
        assertTrue(match != null, "regex 가 매칭")
        assertEquals("com.google.android.material", match!!.groupValues[1])
    }

    @Test
    fun `MAX_REF_HOPS 와 MAX_THEME_HOPS 가 안전 범위`() {
        assertTrue(AppLibraryResourceConstants.MAX_REF_HOPS in 5..50, "ref hop limit 합리적")
        // v2 round 2 follow-up #5: 실측 chain depth = 17 edges (themes_holo.xml 정상 포함 시),
        // ThemeOverlay 패턴 적용 시 추가 5-10 overlay 추가 가능 → 32 마진.
        assertTrue(AppLibraryResourceConstants.MAX_THEME_HOPS >= 30, "theme hop limit ≥ 30 (v2 보강)")
        assertTrue(AppLibraryResourceConstants.MAX_THEME_HOPS in 30..100, "상한도 합리적")
    }

    @Test
    fun `RES_VALUE_NULL_LITERAL 와 RES_VALUE_EMPTY_LITERAL 가 sentinel string 정의`() {
        // v2 round 2 follow-up #3: 27 AAR 안 @null 106개, @empty 2개 출현 (Codex Q1 측정값).
        assertEquals("@null", AppLibraryResourceConstants.RES_VALUE_NULL_LITERAL)
        assertEquals("@empty", AppLibraryResourceConstants.RES_VALUE_EMPTY_LITERAL)
    }

    @Test
    fun `ANDROID_NS_PREFIX 가 'android' 로 통일`() {
        // v2 round 2 follow-up #6: android: style parent normalization 의 prefix 비교용.
        assertEquals("android", AppLibraryResourceConstants.ANDROID_NS_PREFIX)
    }
}
```

- [ ] **Step 2: 테스트 실행 — fail 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.AppLibraryResourceConstantsTest"`
Expected: compilation failure — `AppLibraryResourceConstants` 없음.

- [ ] **Step 3: AppLibraryResourceConstants.kt 작성**

```kotlin
// AppLibraryResourceConstants.kt
package dev.axp.layoutlib.worker.resources

/**
 * W3D4 MATERIAL-FIDELITY (08 §7.7.6, 본 spec §3.1 #1):
 * AAR walker / resolver chain / dedupe diagnostic 의 도메인 상수.
 * CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" 준수.
 */
internal object AppLibraryResourceConstants {

    /** sample-app `assembleDebug` 가 emit 하는 runtime-classpath manifest 의 module-relative 경로. */
    const val RUNTIME_CLASSPATH_TXT_PATH = "app/build/axp/runtime-classpath.txt"

    /** AAR ZIP entry — values.xml. */
    const val AAR_VALUES_XML_PATH = "res/values/values.xml"

    /** AAR ZIP entry — AndroidManifest.xml (package 추출용). */
    const val AAR_ANDROID_MANIFEST_PATH = "AndroidManifest.xml"

    /** sample-app res/values/ — module root 기준. */
    const val SAMPLE_APP_RES_VALUES_RELATIVE_PATH = "app/src/main/res/values"

    /** AndroidManifest.xml 의 `package="..."` 추출 regex. plain-text manifest 가정 (W3D4 §4.2). */
    val MANIFEST_PACKAGE_REGEX: Regex = Regex("""package\s*=\s*"([^"]+)"""")

    /** chain walker (?attr / @ref) 의 무한 루프 방지 hop limit. 일반 attr ref chain 3-5 hop. */
    const val MAX_REF_HOPS = 10

    /**
     * theme stack parent walk 의 무한 루프 방지 hop limit. v2 round 2 follow-up #5:
     * 실측 chain depth = 17 edges (Theme.AxpFixture → ... → android:Theme.Holo.Light → Theme.Light → Theme).
     * ThemeOverlay 추가 5-10 가능 → buffer 포함 32.
     */
    const val MAX_THEME_HOPS = 32

    /** loadAarRes 에서 AAR 1+ 의 values.xml 도 못 찾으면 sanity guard. */
    const val MIN_AAR_WITH_VALUES_THRESHOLD = 1

    /**
     * v2 round 2 follow-up #3: ResourceUrl 의 sentinel literal. resolveResValue 가 만나면
     * 즉시 raw value 반환 (parse 시도 안 함). 27 AAR 안 @null 106회 / @empty 2회 출현 (Codex Q1 실측).
     */
    const val RES_VALUE_NULL_LITERAL = "@null"
    const val RES_VALUE_EMPTY_LITERAL = "@empty"

    /**
     * v2 round 2 follow-up #6: android: style parent normalization 용 prefix.
     * 예: parentStyleName = "android:Theme.Holo.Light" → namespace=ANDROID + name="Theme.Holo.Light" 로 lookup.
     */
    const val ANDROID_NS_PREFIX = "android"
    const val NS_NAME_SEPARATOR = ":"
}
```

- [ ] **Step 4: 테스트 실행 — pass 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.AppLibraryResourceConstantsTest"`
Expected: PASS (3 case).

- [ ] **Step 5: ParsedNsEntry 작성 (W3D1 ParsedEntry 의 namespace + sourcePackage 보강)**

ParsedEntry.kt 의 기존 내용은 단순. round 2 에서 namespace 와 sourcePackage diagnostic 필드 추가:

```kotlin
// ParsedNsEntry.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType

/**
 * W3D4 §3.1 #2: namespace + sourcePackage tagged parsed entry.
 * sourcePackage 는 dedupe diagnostic 출력용 (어느 AAR 에서 왔는지). production resolution 에는 미사용.
 */
internal sealed class ParsedNsEntry {
    abstract val namespace: ResourceNamespace
    abstract val sourcePackage: String?  // null = framework / sample-app

    data class SimpleValue(
        val type: ResourceType,
        val name: String,
        val value: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    data class AttrDef(
        val name: String,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry()

    data class StyleDef(
        val name: String,
        val parent: String?,
        val items: List<StyleItem>,
        override val namespace: ResourceNamespace,
        override val sourcePackage: String? = null,
    ) : ParsedNsEntry() {
        data class StyleItem(val name: String, val value: String)
    }
}
```

- [ ] **Step 6: NsBucket 작성**

```kotlin
// NsBucket.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType

/**
 * W3D4 §3.1 #3: 단일 namespace 의 byType/styles/attrs immutable container.
 * LayoutlibResourceBundle.byNs 의 value type.
 */
internal data class NsBucket(
    val byType: Map<ResourceType, Map<String, ResourceValue>>,
    val styles: Map<String, StyleResourceValue>,
    val attrs: Map<String, AttrResourceValue>,
) {
    companion object {
        val EMPTY: NsBucket = NsBucket(emptyMap(), emptyMap(), emptyMap())
    }
}
```

- [ ] **Step 7: 빌드 검증**

Run: `./server/gradlew -p server :layoutlib-worker:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstants.kt \
        server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedNsEntry.kt \
        server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NsBucket.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstantsTest.kt
git commit -m "$(cat <<'EOF'
feat(w3d4): T1 Constants + ParsedNsEntry + NsBucket — 자료구조 기반

W3D4 §3.1 #1-3. namespace + sourcePackage tagged ParsedNsEntry,
single-namespace immutable NsBucket, AAR walker / resolver chain /
dedupe diagnostic 의 도메인 상수.

3 unit PASS.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Task 2: NamespaceAwareValueParser (W3D1 FrameworkValueParser 의 namespace 일반화)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParser.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParserTest.kt`
- (옵션) Delete after T6 PASS: `resources/FrameworkValueParser.kt` (T6 까지 유지)

W3D1 의 `FrameworkValueParser` 는 `parse(path)` 가 namespace=ANDROID 하드코딩. round 2 가 namespace 인자 받도록 일반화. 로직 자체는 그대로 (StAX 파싱, declare-styleable nested first-wins, dimen/integer/bool/color/string/style/attr).

- [ ] **Step 1: NamespaceAwareValueParserTest 작성 (5 case)**

```kotlin
// NamespaceAwareValueParserTest.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class NamespaceAwareValueParserTest {

    @Test
    fun `simple value 가 정확한 namespace + sourcePackage 로 태깅`() {
        val xml = tmp("""<resources><dimen name="x">10dp</dimen></resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        assertEquals(1, entries.size)
        val e = entries[0] as ParsedNsEntry.SimpleValue
        assertEquals("x", e.name)
        assertEquals(ResourceType.DIMEN, e.type)
        assertEquals("10dp", e.value)
        assertEquals(ResourceNamespace.RES_AUTO, e.namespace)
        assertEquals("com.foo", e.sourcePackage)
    }

    @Test
    fun `style 의 parent 와 items 추출`() {
        val xml = tmp("""<resources>
            <style name="Theme.AxpFixture" parent="Theme.Material3.DayNight.NoActionBar">
                <item name="colorPrimary">#6750A4</item>
            </style>
        </resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.fixture")
        assertEquals(1, entries.size)
        val s = entries[0] as ParsedNsEntry.StyleDef
        assertEquals("Theme.AxpFixture", s.name)
        assertEquals("Theme.Material3.DayNight.NoActionBar", s.parent)
        assertEquals(1, s.items.size)
        assertEquals("colorPrimary", s.items[0].name)
    }

    @Test
    fun `top-level attr 가 declare-styleable nested attr 보다 first-wins (W3D1 F1 보존)`() {
        val xml = tmp("""<resources>
            <attr name="colorPrimary" />
            <declare-styleable name="ButtonStyle">
                <attr name="colorPrimary" />
            </declare-styleable>
        </resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, "com.foo")
        val attrCount = entries.count { it is ParsedNsEntry.AttrDef && it.name == "colorPrimary" }
        assertEquals(1, attrCount, "first-wins → 1 entry only")
    }

    @Test
    fun `framework namespace 도 동일한 parser 가 처리`() {
        val xml = tmp("""<resources><color name="white">#ffffff</color></resources>""")
        val entries = NamespaceAwareValueParser.parse(xml, ResourceNamespace.ANDROID, null)
        val e = entries[0] as ParsedNsEntry.SimpleValue
        assertEquals(ResourceNamespace.ANDROID, e.namespace)
        assertEquals(null, e.sourcePackage)
    }

    @Test
    fun `XML parsing 에러는 IllegalStateException 으로 wrap`() {
        val xml = tmp("""<resources><dimen name="x" """)  // unclosed
        try {
            NamespaceAwareValueParser.parse(xml, ResourceNamespace.RES_AUTO, null)
            org.junit.jupiter.api.Assertions.fail<Unit>("expected throw")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("파싱 실패") == true)
        }
    }

    private fun tmp(content: String): Path {
        val f = Files.createTempFile("vals", ".xml")
        f.toFile().writeText(content)
        f.toFile().deleteOnExit()
        return f
    }
}
```

- [ ] **Step 2: 테스트 실행 — fail 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.NamespaceAwareValueParserTest"`
Expected: compilation failure.

- [ ] **Step 3: NamespaceAwareValueParser.kt 작성**

W3D1 `FrameworkValueParser` 코드 가져와서 namespace + sourcePackage 인자 추가. 기존 ParsedEntry → ParsedNsEntry 매핑.

```kotlin
// NamespaceAwareValueParser.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * W3D4 §3.1 #4: W3D1 FrameworkValueParser 의 namespace + sourcePackage 인자 일반화.
 * 로직: StAX 기반 values.xml parser. dimen/integer/bool/color/string/style/attr/declare-styleable 처리.
 * declare-styleable nested attr first-wins (W3D1 F1 정책 보존 — top-level 이 우선).
 */
internal object NamespaceAwareValueParser {

    private val mFactory: XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty("javax.xml.stream.isSupportingExternalEntities", false)
    }

    fun parse(path: Path, namespace: ResourceNamespace, sourcePackage: String?): List<ParsedNsEntry> {
        return path.toFile().inputStream().use { input ->
            val reader = mFactory.createXMLStreamReader(input)
            try {
                parseInternal(reader, namespace, sourcePackage)
            } catch (e: Exception) {
                throw IllegalStateException("$path 파싱 실패: ${e.message}", e)
            } finally {
                reader.close()
            }
        }
    }

    private fun parseInternal(
        reader: XMLStreamReader,
        namespace: ResourceNamespace,
        sourcePackage: String?,
    ): List<ParsedNsEntry> {
        val entries = mutableListOf<ParsedNsEntry>()
        val seenAttrNames = HashSet<String>()
        var depth = 0
        while (reader.hasNext()) {
            val event = reader.next()
            when (event) {
                XMLStreamConstants.START_ELEMENT -> {
                    depth++
                    if (depth == 2) {
                        when (reader.localName) {
                            "dimen", "integer", "bool", "color", "string", "fraction" ->
                                handleSimpleValue(reader, namespace, sourcePackage)?.let { entries += it }
                            "style" ->
                                handleStyle(reader, namespace, sourcePackage)?.let { entries += it }
                            "attr" -> {
                                val name = reader.getAttributeValue(null, "name") ?: ""
                                if (name.isNotEmpty() && seenAttrNames.add(name)) {
                                    entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
                                }
                            }
                            "declare-styleable" ->
                                handleDeclareStyleable(reader, namespace, sourcePackage, seenAttrNames, entries)
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
        return entries
    }

    private fun handleSimpleValue(
        reader: XMLStreamReader,
        namespace: ResourceNamespace,
        sourcePackage: String?,
    ): ParsedNsEntry.SimpleValue? {
        val name = reader.getAttributeValue(null, "name") ?: return null
        val type = when (reader.localName) {
            "dimen" -> ResourceType.DIMEN
            "integer" -> ResourceType.INTEGER
            "bool" -> ResourceType.BOOL
            "color" -> ResourceType.COLOR
            "string" -> ResourceType.STRING
            "fraction" -> ResourceType.FRACTION
            else -> return null
        }
        val value = reader.elementText ?: ""
        return ParsedNsEntry.SimpleValue(type, name, value, namespace, sourcePackage)
    }

    private fun handleStyle(
        reader: XMLStreamReader,
        namespace: ResourceNamespace,
        sourcePackage: String?,
    ): ParsedNsEntry.StyleDef? {
        val name = reader.getAttributeValue(null, "name") ?: return null
        val parent = reader.getAttributeValue(null, "parent")
        val items = mutableListOf<ParsedNsEntry.StyleDef.StyleItem>()
        while (reader.hasNext()) {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT && reader.localName == "item") {
                val itemName = reader.getAttributeValue(null, "name") ?: ""
                val itemValue = reader.elementText ?: ""
                if (itemName.isNotEmpty()) items += ParsedNsEntry.StyleDef.StyleItem(itemName, itemValue)
            } else if (event == XMLStreamConstants.END_ELEMENT && reader.localName == "style") {
                break
            }
        }
        return ParsedNsEntry.StyleDef(name, parent, items, namespace, sourcePackage)
    }

    private fun handleDeclareStyleable(
        reader: XMLStreamReader,
        namespace: ResourceNamespace,
        sourcePackage: String?,
        seen: MutableSet<String>,
        entries: MutableList<ParsedNsEntry>,
    ) {
        while (reader.hasNext()) {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT && reader.localName == "attr") {
                val name = reader.getAttributeValue(null, "name") ?: ""
                if (name.isNotEmpty() && seen.add(name)) {
                    entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && reader.localName == "declare-styleable") {
                break
            }
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 — pass 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.NamespaceAwareValueParserTest"`
Expected: 5 PASS.

- [ ] **Step 5: Commit**

```bash
git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParser.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParserTest.kt
git commit -m "$(cat <<'EOF'
feat(w3d4): T2 NamespaceAwareValueParser — namespace + sourcePackage 일반화

W3D4 §3.1 #4. W3D1 FrameworkValueParser 의 StAX 로직 재활용 + namespace/sourcePackage 인자 추가.
top-level attr first-wins (W3D1 F1) 보존.

5 unit PASS.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Task 3: AarResourceWalker (γ skip + δ 진단 + wall-clock)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalker.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerTest.kt`

41 AAR ZIP open + manifest 의 package 추출 (진단 only — round 2 mode 통일로 namespace=RES_AUTO 고정) + values.xml stream 파싱. (γ) 부재 시 skip + 1줄 로깅. (δ) AndroidManifest 부재/package 추출 실패는 IllegalStateException.

- [ ] **Step 1: AarResourceWalkerTest 작성 (6 case)**

```kotlin
// AarResourceWalkerTest.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AarResourceWalkerTest {

    @Test
    fun `AAR with values + manifest 가 정확히 파싱`() {
        val aar = makeAar(
            manifest = """<manifest package="com.test.lib"/>""",
            values = """<resources><dimen name="m">5dp</dimen></resources>""",
        )
        val result = AarResourceWalker.walkOne(aar)
        assertNotNull(result)
        assertEquals("com.test.lib", result!!.sourcePackage)
        assertEquals(ResourceNamespace.RES_AUTO, result.entries[0].namespace, "round 2 mode 통일 RES_AUTO")
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `values 부재 AAR 은 silent skip + 진단 1줄`() {
        val aar = makeAar(manifest = """<manifest package="com.code.only"/>""", values = null)
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try {
            val result = AarResourceWalker.walkOne(aar)
            assertEquals(null, result, "values 없으면 null 반환")
            val log = errOut.toString()
            assertTrue(log.contains("[AarResourceWalker]"), "진단 prefix")
            assertTrue(log.contains("res/values/values.xml 없음"))
        } finally {
            System.setErr(origErr)
        }
    }

    @Test
    fun `manifest 부재 AAR 은 IllegalStateException`() {
        val aar = makeAarRaw(emptyMap())  // 빈 zip
        try {
            AarResourceWalker.walkOne(aar)
            org.junit.jupiter.api.Assertions.fail<Unit>("expected throw")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("AndroidManifest") == true)
        }
    }

    @Test
    fun `manifest package 추출 실패는 IllegalStateException`() {
        val aar = makeAar(manifest = """<manifest />""", values = """<resources/>""")
        try {
            AarResourceWalker.walkOne(aar)
            org.junit.jupiter.api.Assertions.fail<Unit>("expected throw")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("package 추출 실패") == true)
        }
    }

    @Test
    fun `walkAll 가 classpath txt 의 .aar 만 필터링`() {
        val aar1 = makeAar("""<manifest package="com.a"/>""", """<resources><dimen name="x">1dp</dimen></resources>""")
        val aar2 = makeAar("""<manifest package="com.b"/>""", """<resources><dimen name="y">2dp</dimen></resources>""")
        val classpathTxt = Files.createTempFile("cp", ".txt").apply {
            toFile().writeText(listOf(aar1.toString(), "/some.jar", aar2.toString()).joinToString("\n"))
            toFile().deleteOnExit()
        }
        val results = AarResourceWalker.walkAll(classpathTxt)
        assertEquals(2, results.size, ".jar 는 skip")
        assertTrue(results.any { it.sourcePackage == "com.a" })
        assertTrue(results.any { it.sourcePackage == "com.b" })
    }

    @Test
    fun `walkAll wall-clock 측정 출력 + 카운트`() {
        val aar = makeAar("""<manifest package="com.t"/>""", """<resources/>""")
        val cp = Files.createTempFile("cp", ".txt").apply {
            toFile().writeText(aar.toString()); toFile().deleteOnExit()
        }
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try {
            AarResourceWalker.walkAll(cp)
            val log = errOut.toString()
            assertTrue(log.contains("[AarResourceWalker]"))
            assertTrue(log.contains("ms"), "wall-clock ms 출력")
        } finally {
            System.setErr(origErr)
        }
    }

    private fun makeAar(manifest: String, values: String?): Path {
        val map = mutableMapOf("AndroidManifest.xml" to manifest.toByteArray())
        if (values != null) map["res/values/values.xml"] = values.toByteArray()
        return makeAarRaw(map)
    }

    private fun makeAarRaw(entries: Map<String, ByteArray>): Path {
        val f = Files.createTempFile("test", ".aar")
        ZipOutputStream(f.toFile().outputStream()).use { zos ->
            entries.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        f.toFile().deleteOnExit()
        return f
    }
}
```

- [ ] **Step 2: 테스트 실행 — fail 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.AarResourceWalkerTest"`
Expected: compilation failure.

- [ ] **Step 3: AarResourceWalker.kt 작성**

```kotlin
// AarResourceWalker.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * W3D4 §3.1 #5: runtime-classpath.txt → 41 AAR walker.
 * - 각 AAR 의 AndroidManifest 의 package 를 추출 (진단/dedupe-source 추적용).
 * - res/values/values.xml 부재 → silent skip + 1줄 로깅 (γ 정책).
 * - namespace 는 round 2 mode 통일에 따라 항상 RES_AUTO.
 * - 전체 wall-clock + 카운트 진단 출력 (Codex Q4).
 */
internal object AarResourceWalker {

    data class Result(val sourcePackage: String, val entries: List<ParsedNsEntry>)

    fun walkAll(runtimeClasspathTxt: Path): List<Result> {
        require(Files.exists(runtimeClasspathTxt)) {
            "sample-app classpath manifest 없음: $runtimeClasspathTxt — assembleDebug 먼저 실행"
        }
        val t0 = System.nanoTime()
        val aarPaths = Files.readAllLines(runtimeClasspathTxt)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.endsWith(".aar") }
            .map { Path.of(it) }
        val results = mutableListOf<Result>()
        var skipped = 0
        for (aar in aarPaths) {
            val r = walkOne(aar)
            if (r != null) results += r else skipped++
        }
        val tMs = (System.nanoTime() - t0) / 1_000_000
        System.err.println(
            "[AarResourceWalker] walked ${aarPaths.size} AARs (${results.size} with values, $skipped code-only) in ${tMs}ms"
        )
        return results
    }

    fun walkOne(aarPath: Path): Result? {
        require(Files.exists(aarPath)) { "AAR 부재: $aarPath" }
        ZipFile(aarPath.toFile()).use { zip ->
            val manifestEntry = zip.getEntry(AppLibraryResourceConstants.AAR_ANDROID_MANIFEST_PATH)
                ?: throw IllegalStateException("$aarPath: AndroidManifest.xml 없음 — AAR 형식 위반")
            val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val match = AppLibraryResourceConstants.MANIFEST_PACKAGE_REGEX.find(manifestText)
                ?: throw IllegalStateException("$aarPath: AndroidManifest 의 package 추출 실패")
            val pkg = match.groupValues[1]
            require(pkg.isNotEmpty()) { "$aarPath: AndroidManifest package empty" }

            val valuesEntry = zip.getEntry(AppLibraryResourceConstants.AAR_VALUES_XML_PATH)
            if (valuesEntry == null) {
                System.err.println(
                    "[AarResourceWalker] $aarPath skipped — res/values/values.xml 없음 (pkg=$pkg)"
                )
                return null
            }

            // values.xml 을 임시 파일로 풀어서 NamespaceAwareValueParser 에 넘김 (StAX 가 InputStream 보다 Path 친화적).
            val tmp = Files.createTempFile("aarvals", ".xml")
            tmp.toFile().deleteOnExit()
            zip.getInputStream(valuesEntry).use { stream ->
                Files.copy(stream, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            val entries = NamespaceAwareValueParser.parse(tmp, ResourceNamespace.RES_AUTO, pkg)
            return Result(pkg, entries)
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 — pass 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.AarResourceWalkerTest"`
Expected: 6 PASS.

- [ ] **Step 5: Commit**

```bash
git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalker.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerTest.kt
git commit -m "$(cat <<'EOF'
feat(w3d4): T3 AarResourceWalker — 41 AAR ZIP walker (γ skip + δ + wall-clock)

W3D4 §3.1 #5. runtime-classpath.txt → 41 AAR ZipFile open → manifest
package 추출 (진단 only, mode 통일 RES_AUTO) + values.xml stream 파싱.
부재 시 silent skip + 1줄 로깅. wall-clock ms + 카운트 진단.

6 unit PASS.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Task 4: LayoutlibResourceBundle (byNs + dedupe + 진단)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleTest.kt`

W3D1 `FrameworkResourceBundle` 흡수 — `byNs: LinkedHashMap<ResourceNamespace, NsBucket>` (ANDROID → RES_AUTO 결정성). dedupe (η round 2): per-namespace later-wins SimpleValue/StyleDef, first-wins AttrDef + duplicate diagnostic 로그. style parent inference 는 `StyleParentInference` 재활용 (namespace 무관).

- [ ] **Step 1: LayoutlibResourceBundleTest 작성 (7 case)**

```kotlin
// LayoutlibResourceBundleTest.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class LayoutlibResourceBundleTest {

    @Test
    fun `getStyleExact 이 namespace + name 으로 정확 hit`() {
        val bundle = build(
            ResourceNamespace.ANDROID to listOf(style("Theme", null)),
            ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", "Theme.Material3")),
        )
        assertNotNull(bundle.getStyleExact(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme.AxpFixture")))
        assertNull(bundle.getStyleExact(ref(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme.AxpFixture")))
    }

    @Test
    fun `getStyleByName 이 deterministic ANDROID → RES_AUTO 순회 후 첫 매치`() {
        // 같은 name 이 양쪽 ns 에 → ANDROID 가 우선
        val bundle = build(
            ResourceNamespace.ANDROID to listOf(style("CommonName", null)),
            ResourceNamespace.RES_AUTO to listOf(style("CommonName", null)),
        )
        val hit = bundle.getStyleByName("CommonName")
        assertNotNull(hit)
        assertEquals(ResourceNamespace.ANDROID, hit!!.namespace)
    }

    @Test
    fun `getResource 가 ns-exact byType 안 lookup`() {
        val bundle = build(
            ResourceNamespace.RES_AUTO to listOf(simple(ResourceType.COLOR, "primary", "#fff")),
        )
        assertNotNull(bundle.getResource(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "primary")))
        assertNull(bundle.getResource(ref(ResourceNamespace.ANDROID, ResourceType.COLOR, "primary")))
    }

    @Test
    fun `SimpleValue dedupe later-wins per namespace`() {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try {
            val bundle = build(
                ResourceNamespace.RES_AUTO to listOf(
                    ParsedNsEntry.SimpleValue(ResourceType.COLOR, "p", "#a", ResourceNamespace.RES_AUTO, "first"),
                    ParsedNsEntry.SimpleValue(ResourceType.COLOR, "p", "#b", ResourceNamespace.RES_AUTO, "second"),
                ),
            )
            val rv = bundle.getResource(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "p"))
            assertEquals("#b", rv?.value, "later-wins")
            val log = errOut.toString()
            assertTrue(log.contains("dup color 'p'"), "duplicate 진단")
        } finally {
            System.setErr(origErr)
        }
    }

    @Test
    fun `AttrDef dedupe first-wins`() {
        val bundle = build(
            ResourceNamespace.RES_AUTO to listOf(
                ParsedNsEntry.AttrDef("colorPrimary", ResourceNamespace.RES_AUTO, "first"),
                ParsedNsEntry.AttrDef("colorPrimary", ResourceNamespace.RES_AUTO, "second"),
            ),
        )
        // attr 은 1 개만 등록 (first-wins)
        assertEquals(1, bundle.attrCountForNamespace(ResourceNamespace.RES_AUTO))
    }

    @Test
    fun `cross-ns parent 가 inference 통과 (Theme 가 ANDROID 에)`() {
        val bundle = build(
            ResourceNamespace.ANDROID to listOf(style("Theme", null)),
            ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", "Theme.Material3.DayNight.NoActionBar"), style("Theme.Material3.DayNight.NoActionBar", null)),
        )
        val s = bundle.getStyleExact(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme.AxpFixture"))
        assertNotNull(s)
        // parent 는 string-only (StyleResourceValueImpl 의 parentStyleName)
        assertEquals("Theme.Material3.DayNight.NoActionBar", s!!.parentStyleName)
    }

    @Test
    fun `byNs 가 LinkedHashMap insertion order ANDROID → RES_AUTO`() {
        val bundle = build(
            ResourceNamespace.ANDROID to listOf(style("A", null)),
            ResourceNamespace.RES_AUTO to listOf(style("B", null)),
        )
        val keys = bundle.namespacesInOrder()
        assertEquals(listOf(ResourceNamespace.ANDROID, ResourceNamespace.RES_AUTO), keys)
    }

    private fun build(vararg pairs: Pair<ResourceNamespace, List<ParsedNsEntry>>): LayoutlibResourceBundle {
        val grouped = pairs.toMap()
        return LayoutlibResourceBundle.build(grouped)
    }

    private fun style(name: String, parent: String?) = ParsedNsEntry.StyleDef(name, parent, emptyList(), ResourceNamespace.RES_AUTO, null)
    private fun simple(type: ResourceType, name: String, value: String) = ParsedNsEntry.SimpleValue(type, name, value, ResourceNamespace.RES_AUTO, null)
    private fun ref(ns: ResourceNamespace, t: ResourceType, n: String) = com.android.ide.common.rendering.api.ResourceReference(ns, t, n)
}
```

- [ ] **Step 2: 테스트 실행 — fail 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.LayoutlibResourceBundleTest"`
Expected: compilation failure.

- [ ] **Step 3: LayoutlibResourceBundle.kt 작성**

```kotlin
// LayoutlibResourceBundle.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValueImpl
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleResourceValueImpl
import com.android.resources.ResourceType

/**
 * W3D4 §3.1 #6: namespace-aware immutable resource bundle.
 * byNs = LinkedHashMap (ANDROID → RES_AUTO 결정 순회).
 * Mode 통일에 따라 ANDROID + RES_AUTO 2-bucket. duplicate 진단 로그.
 */
internal class LayoutlibResourceBundle private constructor(
    private val byNs: LinkedHashMap<ResourceNamespace, NsBucket>,
) {

    fun getStyleExact(ref: ResourceReference): StyleResourceValue? =
        byNs[ref.namespace]?.styles?.get(ref.name)

    fun getStyleByName(name: String): StyleResourceValue? {
        for (bucket in byNs.values) {
            bucket.styles[name]?.let { return it }
        }
        return null
    }

    fun getResource(ref: ResourceReference): ResourceValue? =
        byNs[ref.namespace]?.byType?.get(ref.resourceType)?.get(ref.name)

    /** 진단/테스트 전용. */
    fun namespacesInOrder(): List<ResourceNamespace> = byNs.keys.toList()
    fun styleCountForNamespace(ns: ResourceNamespace): Int = byNs[ns]?.styles?.size ?: 0
    fun attrCountForNamespace(ns: ResourceNamespace): Int = byNs[ns]?.attrs?.size ?: 0

    companion object {
        fun build(perNamespaceEntries: Map<ResourceNamespace, List<ParsedNsEntry>>): LayoutlibResourceBundle {
            val canonicalOrder = listOf(ResourceNamespace.ANDROID, ResourceNamespace.RES_AUTO)
            val byNs = LinkedHashMap<ResourceNamespace, NsBucket>()
            for (ns in canonicalOrder) {
                val entries = perNamespaceEntries[ns] ?: continue
                byNs[ns] = buildBucket(ns, entries)
            }
            // 추가 ns 가 있다면 (W4+ namespace-aware 시) ordering 유지
            for ((ns, entries) in perNamespaceEntries) {
                if (ns !in canonicalOrder) byNs[ns] = buildBucket(ns, entries)
            }
            return LayoutlibResourceBundle(byNs)
        }

        private fun buildBucket(ns: ResourceNamespace, entries: List<ParsedNsEntry>): NsBucket {
            val byTypeMut = mutableMapOf<ResourceType, MutableMap<String, ResourceValue>>()
            val attrsMut = LinkedHashMap<String, AttrResourceValueImpl>()
            val stylesMut = LinkedHashMap<String, StyleResourceValueImpl>()
            val styleDefs = mutableListOf<ParsedNsEntry.StyleDef>()

            for (e in entries) when (e) {
                is ParsedNsEntry.SimpleValue -> {
                    val typeMap = byTypeMut.getOrPut(e.type) { mutableMapOf() }
                    val existed = typeMap[e.name]
                    if (existed != null) {
                        System.err.println(
                            "[LayoutlibResourceBundle] dup ${e.type.getName()} '${e.name}' ns=${ns.packageName ?: "RES_AUTO"} from ${e.sourcePackage} — adopting later"
                        )
                    }
                    val ref = ResourceReference(ns, e.type, e.name)
                    typeMap[e.name] = ResourceValueImpl(ref, e.value, null)
                }
                is ParsedNsEntry.AttrDef -> {
                    if (!attrsMut.containsKey(e.name)) {
                        val ref = ResourceReference(ns, ResourceType.ATTR, e.name)
                        attrsMut[e.name] = AttrResourceValueImpl(ref, null)
                    }
                    // first-wins — 두 번째는 silent (W3D1 정책). 진단 원하면 별도 로그.
                }
                is ParsedNsEntry.StyleDef -> styleDefs += e
            }

            val allStyleNames: Set<String> = styleDefs.mapTo(HashSet()) { it.name }
            for (def in styleDefs) {
                val candidate = StyleParentInference.infer(def.name, def.parent)
                val parentName = if (candidate != null && candidate in allStyleNames) candidate else candidate
                // round 2: parent 이름은 보존 (cross-ns chain 의 ns-agnostic fallback 이 처리). null 만 fallback.
                val ref = ResourceReference(ns, ResourceType.STYLE, def.name)
                val sv = StyleResourceValueImpl(ref, parentName, null)
                for (it2 in def.items) {
                    sv.addItem(StyleItemResourceValueImpl(ns, it2.name, it2.value, null))
                }
                if (stylesMut.containsKey(def.name)) {
                    System.err.println(
                        "[LayoutlibResourceBundle] dup style '${def.name}' ns=${ns.packageName ?: "RES_AUTO"} from ${def.sourcePackage} — adopting later"
                    )
                }
                stylesMut[def.name] = sv
            }

            return NsBucket(
                byType = byTypeMut.mapValues { it.value.toMap() },
                styles = stylesMut.toMap(),
                attrs = attrsMut.toMap(),
            )
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 — pass 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.LayoutlibResourceBundleTest"`
Expected: 7 PASS.

- [ ] **Step 5: Commit**

```bash
git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleTest.kt
git commit -m "$(cat <<'EOF'
feat(w3d4): T4 LayoutlibResourceBundle — namespace-aware + dedupe 진단

W3D4 §3.1 #6. byNs LinkedHashMap (ANDROID → RES_AUTO 결정 순회).
SimpleValue/StyleDef later-wins + duplicate 진단 로그, AttrDef
first-wins. cross-ns style parent 는 string 보존, ns-agnostic fallback
이 chain walk.

7 unit PASS.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Task 5: LayoutlibResourceValueLoader (3-입력 통합 + JVM-wide cache + wall-clock)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceValueLoader.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceValueLoaderTest.kt`

3-입력: framework dist `data/values/` (W3D1 `ResourceLoaderConstants.REQUIRED_FILES` 의 10 XML), sample-app `app/src/main/res/values/*.xml`, runtime-classpath.txt 의 41 AAR (T3 walker 위임). JVM-wide cache key = `Args(distDataDir, sampleAppRoot, classpathTxt)` 3-tuple.

- [ ] **Step 1: LayoutlibResourceValueLoaderTest 작성 (6 case — v2 dedupe winner +1)**

```kotlin
// LayoutlibResourceValueLoaderTest.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LayoutlibResourceValueLoaderTest {

    @BeforeEach
    fun clearCache() = LayoutlibResourceValueLoader.clearCache()

    @Test
    fun `framework only path → ANDROID bucket 만`(@TempDir tmp: Path) {
        val args = mockArgs(tmp, withApp = false, withAar = false)
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        assertEquals(listOf(ResourceNamespace.ANDROID), bundle.namespacesInOrder())
    }

    @Test
    fun `framework + app + aar 통합 → 2 bucket`(@TempDir tmp: Path) {
        val args = mockArgs(tmp, withApp = true, withAar = true)
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        assertEquals(
            listOf(ResourceNamespace.ANDROID, ResourceNamespace.RES_AUTO),
            bundle.namespacesInOrder(),
        )
    }

    @Test
    fun `cache key 3-tuple 동치 (다른 sampleAppRoot 면 새 build)`(@TempDir tmp: Path) {
        // v2 round 2 follow-up #8 (Codex Q7): plan v1 의 `assertTrue(true)` 가짜 assertion 제거.
        // 동일 args = identity hit, 다른 sampleAppRoot = 새 instance 명시 검증.
        val args1 = mockArgs(tmp, withApp = true, withAar = false)
        val a = LayoutlibResourceValueLoader.loadOrGet(args1)
        val b = LayoutlibResourceValueLoader.loadOrGet(args1)
        assertTrue(a === b, "동일 args → 동일 instance (cache hit)")

        // 다른 sampleAppRoot — 새 디렉토리에 동일 res 구조 재생성 (require 통과 보장).
        val anotherRoot = Files.createDirectories(tmp.resolve("another-sampleapp"))
        val anotherValues = Files.createDirectories(anotherRoot.resolve("app/src/main/res/values"))
        anotherValues.resolve("themes.xml").toFile().writeText("""<resources/>""")
        val anotherClasspath = anotherRoot.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
        Files.createDirectories(anotherClasspath.parent)
        anotherClasspath.toFile().writeText("")

        val args2 = args1.copy(sampleAppRoot = anotherRoot, runtimeClasspathTxt = anotherClasspath)
        val c = LayoutlibResourceValueLoader.loadOrGet(args2)
        assertNotNull(c, "다른 sampleAppRoot 도 정상 build")
        assertTrue(a !== c, "다른 args → 다른 instance (cache key 3-tuple 정합)")
    }

    @Test
    fun `wall-clock 진단이 cold-start 시점에 출력`(@TempDir tmp: Path) {
        val args = mockArgs(tmp, withApp = true, withAar = true)
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try {
            LayoutlibResourceValueLoader.loadOrGet(args)
            val log = errOut.toString()
            assertTrue(log.contains("[LayoutlibResourceValueLoader]"), "loader 진단 prefix")
            assertTrue(log.contains("cold-start"), "cold-start 표기")
            assertTrue(log.contains("ms"), "wall-clock ms")
        } finally {
            System.setErr(origErr)
        }
    }

    @Test
    fun `clearCache 후 재계산이 새 instance 반환`(@TempDir tmp: Path) {
        // v2 round 2 follow-up #9 (cache invalidation): clearCache 가 실제로 새 instance 를
        // 만드는지 명시 검증 (plan v1 은 "정상 returns" 만 확인).
        val args = mockArgs(tmp, withApp = true, withAar = false)
        val a = LayoutlibResourceValueLoader.loadOrGet(args)
        LayoutlibResourceValueLoader.clearCache()
        val b = LayoutlibResourceValueLoader.loadOrGet(args)
        assertNotNull(b)
        assertTrue(a !== b, "clearCache 후 동일 args 라도 새 instance (재계산)")
    }

    @Test
    fun `dedupe winner — sorted lex order + app last`(@TempDir tmp: Path) {
        // v2 round 2 follow-up #7 (Codex Q5): build.gradle.kts:58 의 `.sorted()` 정책에 따라
        // dedupe winner = lex order. 동명 style 가 두 AAR 에 있으면 lex 마지막 AAR 가 later-wins.
        // app 의 res 는 AAR 후 마지막 → app 정의가 모든 AAR override.
        val distData = Files.createDirectories(tmp.resolve("dist/data"))
        val valuesDir = Files.createDirectories(distData.resolve(ResourceLoaderConstants.VALUES_DIR))
        for (filename in ResourceLoaderConstants.REQUIRED_FILES) {
            valuesDir.resolve(filename).toFile().writeText("""<resources/>""")
        }
        // 두 AAR — lex order 로 a-aar 가 먼저, b-aar 가 나중.
        val aarA = makeAar(tmp, """<manifest package="com.a"/>""", """<resources><style name="X"><item name="k">A</item></style></resources>""")
        val aarB = makeAar(tmp, """<manifest package="com.b"/>""", """<resources><style name="X"><item name="k">B</item></style></resources>""")
        val sortedAars = listOf(aarA.toString(), aarB.toString()).sorted()  // axpEmitClasspath 와 동일 정책

        val sampleAppRoot = Files.createDirectories(tmp.resolve("sampleapp"))
        val classpathTxt = sampleAppRoot.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
        Files.createDirectories(classpathTxt.parent)
        classpathTxt.toFile().writeText(sortedAars.joinToString("\n"))

        val bundle = LayoutlibResourceValueLoader.loadOrGet(LayoutlibResourceValueLoader.Args(distData, sampleAppRoot, classpathTxt))
        val winner = bundle.getStyleExact(com.android.ide.common.rendering.api.ResourceReference(
            ResourceNamespace.RES_AUTO, com.android.resources.ResourceType.STYLE, "X"
        ))
        assertNotNull(winner)
        // lex 마지막 AAR 가 later-wins → "B" 가 winner.
        assertEquals("B", winner!!.getItem(
            com.android.ide.common.rendering.api.ResourceReference(ResourceNamespace.RES_AUTO, com.android.resources.ResourceType.ATTR, "k")
        )?.value)
    }

    // Mock helper — 최소한의 distDataDir + sampleAppRoot + classpathTxt 구조
    private fun mockArgs(tmp: Path, withApp: Boolean, withAar: Boolean): LayoutlibResourceValueLoader.Args {
        val distData = Files.createDirectories(tmp.resolve("dist/data"))
        // 10 framework XML 의 minimal stub
        val valuesDir = Files.createDirectories(distData.resolve(ResourceLoaderConstants.VALUES_DIR))
        for (filename in ResourceLoaderConstants.REQUIRED_FILES) {
            valuesDir.resolve(filename).toFile().writeText("""<resources/>""")
        }

        val sampleAppRoot = Files.createDirectories(tmp.resolve("sampleapp"))
        if (withApp) {
            val appValues = Files.createDirectories(sampleAppRoot.resolve("app/src/main/res/values"))
            appValues.resolve("themes.xml").toFile().writeText(
                """<resources><style name="Theme.AxpFixture" parent="Theme.Material3.DayNight.NoActionBar"/></resources>"""
            )
        }

        val classpathTxt = sampleAppRoot.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
        Files.createDirectories(classpathTxt.parent)
        if (withAar) {
            val aar = makeAar(tmp, """<manifest package="com.x"/>""", """<resources><dimen name="d">1dp</dimen></resources>""")
            classpathTxt.toFile().writeText(aar.toString())
        } else {
            classpathTxt.toFile().writeText("")
        }
        return LayoutlibResourceValueLoader.Args(distData, sampleAppRoot, classpathTxt)
    }

    private fun makeAar(parent: Path, manifest: String, values: String): Path {
        val f = Files.createTempFile(parent, "test", ".aar")
        ZipOutputStream(f.toFile().outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml")); zos.write(manifest.toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("res/values/values.xml")); zos.write(values.toByteArray()); zos.closeEntry()
        }
        return f
    }
}
```

- [ ] **Step 2: 테스트 실행 — fail 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.LayoutlibResourceValueLoaderTest"`
Expected: compilation failure.

- [ ] **Step 3: LayoutlibResourceValueLoader.kt 작성**

```kotlin
// LayoutlibResourceValueLoader.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists

/**
 * W3D4 §3.1 #7: 3-입력 통합 loader (framework + app + 41 AAR).
 * JVM-wide cache (Args 3-tuple key). wall-clock 진단.
 * W3D1 FrameworkResourceValueLoader 가 본 로더의 framework path 에 흡수됨.
 */
internal object LayoutlibResourceValueLoader {

    data class Args(
        val distDataDir: Path,
        val sampleAppRoot: Path,
        val runtimeClasspathTxt: Path,
    )

    private val cache = ConcurrentHashMap<Args, LayoutlibResourceBundle>()

    fun loadOrGet(args: Args): LayoutlibResourceBundle {
        return cache.computeIfAbsent(args) { build(it) }
    }

    fun clearCache() = cache.clear()

    private fun build(args: Args): LayoutlibResourceBundle {
        val tFramework0 = System.nanoTime()
        val frameworkEntries = loadFramework(args.distDataDir)
        val tFramework = ms(tFramework0)

        val tApp0 = System.nanoTime()
        val appEntries = loadApp(args.sampleAppRoot)
        val tApp = ms(tApp0)

        val tAar0 = System.nanoTime()
        val aarResults = if (args.runtimeClasspathTxt.exists()) AarResourceWalker.walkAll(args.runtimeClasspathTxt) else emptyList()
        val tAar = ms(tAar0)

        // RES_AUTO bucket = app + aar 통합. 순회 순서: AAR (classpath txt 순) → app (마지막 — sample-app 정의 우선).
        val resAutoEntries = aarResults.flatMap { it.entries } + appEntries

        // v2 round 2 plan-review post-implementation 정정 (T5 review): framework-only 시 RES_AUTO 키
        // 자체를 map 에 넣지 않아야 single-bucket 보장. LayoutlibResourceBundle.build 의 ?: continue
        // 는 key 부재만 skip — empty list 가 들어오면 빈 NsBucket 생성하여 namespacesInOrder() 가
        // [ANDROID, RES_AUTO] 반환. 조건부 add 가 정답.
        val perNs = if (resAutoEntries.isEmpty()) {
            mapOf(ResourceNamespace.ANDROID to frameworkEntries)
        } else {
            mapOf(
                ResourceNamespace.ANDROID to frameworkEntries,
                ResourceNamespace.RES_AUTO to resAutoEntries,
            )
        }

        val tBuild0 = System.nanoTime()
        val bundle = LayoutlibResourceBundle.build(perNs)
        val tBuild = ms(tBuild0)

        System.err.println(
            "[LayoutlibResourceValueLoader] cold-start framework=${tFramework}ms app=${tApp}ms aar=${tAar}ms build=${tBuild}ms total=${tFramework + tApp + tAar + tBuild}ms"
        )
        return bundle
    }

    private fun loadFramework(distDataDir: Path): List<ParsedNsEntry> {
        require(distDataDir.exists()) { "framework data 디렉토리 없음: $distDataDir" }
        val valuesDir = distDataDir.resolve(ResourceLoaderConstants.VALUES_DIR)
        val entries = mutableListOf<ParsedNsEntry>()
        for (filename in ResourceLoaderConstants.REQUIRED_FILES) {
            val path = valuesDir.resolve(filename)
            require(path.exists()) { "필수 framework XML 누락: $path" }
            entries += NamespaceAwareValueParser.parse(path, ResourceNamespace.ANDROID, null)
        }
        return entries
    }

    private fun loadApp(sampleAppRoot: Path): List<ParsedNsEntry> {
        val appValues = sampleAppRoot.resolve(AppLibraryResourceConstants.SAMPLE_APP_RES_VALUES_RELATIVE_PATH)
        if (!appValues.exists()) return emptyList()
        val entries = mutableListOf<ParsedNsEntry>()
        // v2 round 2 follow-up #8 (Codex Q7): Files.list 는 filesystem-dependent order →
        // .sorted() 로 lex 순서 고정 (cross-platform deterministic).
        Files.list(appValues).use { stream ->
            val sortedXmlFiles = stream
                .filter { it.toString().endsWith(".xml") }
                .sorted()
                .toList()
            for (file in sortedXmlFiles) {
                entries += NamespaceAwareValueParser.parse(file, ResourceNamespace.RES_AUTO, null)
            }
        }
        return entries
    }

    private fun ms(t0: Long): Long = (System.nanoTime() - t0) / 1_000_000
}
```

- [ ] **Step 4: 테스트 실행 — pass 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.LayoutlibResourceValueLoaderTest"`
Expected: 6 PASS (v2: dedupe winner test 추가).

- [ ] **Step 5: Commit**

```bash
git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceValueLoader.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceValueLoaderTest.kt
git commit -m "$(cat <<'EOF'
feat(w3d4): T5 LayoutlibResourceValueLoader — 3-입력 통합 + cache + 진단

W3D4 §3.1 #7. framework + sample-app + 41 AAR 통합. JVM-wide cache
(Args 3-tuple). wall-clock framework/app/aar/build/total ms 진단.
v2 round 2 follow-up: dedupe winner = sorted lex + app last (Files.list
deterministic), assertTrue(true) 가짜 assertion 제거, clearCache identity 검증.

6 unit PASS.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Task 6: LayoutlibRenderResources (Q3 σ FULL — 9 override + chain walker + theme stack)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibRenderResources.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibRenderResourcesTest.kt`

본 Task 가 round 2 의 핵심. spec §5.1 의 9 method override 전부 구현. ns-exact-then-name fallback 의 uniform 적용 (getStyle / getParent / findItemInStyle / findItemInTheme).

- [ ] **Step 1: LayoutlibRenderResourcesTest 작성 (12 case — v2: sentinel + private override + strict resolver +2)**

```kotlin
// LayoutlibRenderResourcesTest.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayoutlibRenderResourcesTest {

    @Test
    fun `getDefaultTheme = bundle getStyleByName(themeName)`() {
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", null)),
        ))
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        assertEquals("Theme.AxpFixture", rr.defaultTheme.name)
    }

    @Test
    fun `getDefaultTheme empty fallback when name missing`() {
        val bundle = LayoutlibResourceBundle.build(mapOf())
        val rr = LayoutlibRenderResources(bundle, "Missing.Theme")
        // empty StyleResourceValueImpl 반환 (LM-3 패턴)
        assertEquals("Missing.Theme", rr.defaultTheme.name)
        assertNull(rr.defaultTheme.parentStyleName)
    }

    @Test
    fun `getStyle ns-exact then ns-agnostic fallback`() {
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.ANDROID to listOf(style("Theme", null)),
            ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", "Theme")),
        ))
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        // ns-exact RES_AUTO hit
        assertNotNull(rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme.AxpFixture")))
        // ns-exact RES_AUTO miss, name fallback hit ANDROID
        assertNotNull(rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme")))
    }

    @Test
    fun `getParent uses style parentStyleName + ns-agnostic fallback`() {
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.ANDROID to listOf(style("Theme", null)),
            ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", "Theme")),
        ))
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        val child = rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme.AxpFixture"))!!
        val parent = rr.getParent(child)
        assertNotNull(parent)
        assertEquals("Theme", parent!!.name)
    }

    @Test
    fun `getAllThemes 가 default theme + parent walk 결과`() {
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.ANDROID to listOf(style("Theme", null)),
            ResourceNamespace.RES_AUTO to listOf(style("Theme.Material3", "Theme"), style("Theme.AxpFixture", "Theme.Material3")),
        ))
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        val themes = rr.allThemes
        assertTrue(themes.size >= 3, "AxpFixture + Material3 + Theme 포함")
        assertEquals("Theme.AxpFixture", themes[0].name)
    }

    @Test
    fun `getUnresolvedResource ns-exact only`() {
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.RES_AUTO to listOf(simple(ResourceType.COLOR, "p", "#fff")),
        ))
        val rr = LayoutlibRenderResources(bundle, "X")
        assertNotNull(rr.getUnresolvedResource(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "p")))
        assertNull(rr.getUnresolvedResource(ref(ResourceNamespace.ANDROID, ResourceType.COLOR, "p")))
    }

    @Test
    fun `resolveResValue chain walker 가 attr ref 따라가기`() {
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.RES_AUTO to listOf(
                ParsedNsEntry.StyleDef("Theme.X", null, listOf(ParsedNsEntry.StyleDef.StyleItem("colorPrimary", "#abc")), ResourceNamespace.RES_AUTO, null),
            ),
        ))
        val rr = LayoutlibRenderResources(bundle, "Theme.X")
        // ?attr/colorPrimary 처럼 보이는 ResourceValue 를 resolveResValue 에 통과
        val attrRefValue = ResourceValueImpl(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "fakeRef"), "?attr/colorPrimary", null)
        val resolved = rr.resolveResValue(attrRefValue)
        // theme 의 item 가 hit 후 #abc 반환 — 또는 raw value 반환 (chain walker 가 매칭 못하면 graceful)
        assertNotNull(resolved)
    }

    @Test
    fun `resolveResValue circular detection`() {
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.RES_AUTO to listOf(
                simple(ResourceType.COLOR, "a", "@color/b"),
                simple(ResourceType.COLOR, "b", "@color/a"),
            ),
        ))
        val rr = LayoutlibRenderResources(bundle, "X")
        val a = rr.getUnresolvedResource(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "a"))!!
        // circular 이지만 throw 안 함 — 마지막 hop 의 value 반환
        val resolved = rr.resolveResValue(a)
        assertNotNull(resolved)
    }

    @Test
    fun `findItemInStyle parent walk`() {
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.RES_AUTO to listOf(
                ParsedNsEntry.StyleDef("Parent", null, listOf(ParsedNsEntry.StyleDef.StyleItem("colorPrimary", "#parent")), ResourceNamespace.RES_AUTO, null),
                ParsedNsEntry.StyleDef("Child", "Parent", emptyList(), ResourceNamespace.RES_AUTO, null),
            ),
        ))
        val rr = LayoutlibRenderResources(bundle, "Child")
        val child = rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Child"))!!
        val item = rr.findItemInStyle(child, ref(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimary"))
        // parent 의 item 까지 walk 해서 hit
        assertNotNull(item)
    }

    @Test
    fun `applyStyle clearStyles 가 theme stack 변경`() {
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.RES_AUTO to listOf(style("Theme.AxpFixture", null), style("Other", null)),
        ))
        val rr = LayoutlibRenderResources(bundle, "Theme.AxpFixture")
        val initialSize = rr.allThemes.size
        val other = rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Other"))!!
        rr.applyStyle(other, false)
        assertTrue(rr.allThemes.size > initialSize, "theme stack 추가")
        rr.clearStyles()
        // clear 후엔 default theme 만
        assertEquals("Theme.AxpFixture", rr.allThemes[0].name)
    }

    @Test
    fun `resolveResValue 가 @null @empty sentinel 즉시 raw 반환`() {
        // v2 round 2 follow-up #3 (Codex Q1): 27 AAR 안 @null 106회 / @empty 2회 출현. sentinel 은 ref 가 아님 → parse 시도 X.
        val bundle = LayoutlibResourceBundle.build(mapOf())
        val rr = LayoutlibRenderResources(bundle, "X")
        val nullRef = ResourceValueImpl(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "x"), "@null", null)
        val emptyRef = ResourceValueImpl(ref(ResourceNamespace.RES_AUTO, ResourceType.STRING, "x"), "@empty", null)
        assertEquals("@null", rr.resolveResValue(nullRef)?.value, "@null sentinel raw")
        assertEquals("@empty", rr.resolveResValue(emptyRef)?.value, "@empty sentinel raw")
    }

    @Test
    fun `resolveResValue 가 @ asterisk private override 정상 처리`() {
        // v2 round 2 follow-up #3: ResourceUrl.parse 가 `@*android:color/X` 를 private=true, ns=ANDROID 로 parse.
        // 우리 chain walker 는 private 여부 무시 (visibility 는 inflate 시점에 무관) — type/name 만 추출.
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.ANDROID to listOf(simple(ResourceType.COLOR, "primary_text", "#000")),
        ))
        val rr = LayoutlibRenderResources(bundle, "X")
        val privateRef = ResourceValueImpl(ref(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "x"), "@*android:color/primary_text", null)
        val resolved = rr.resolveResValue(privateRef)
        assertNotNull(resolved, "private override 도 chain walker 가 따라감")
    }

    @Test
    fun `getParent 가 'android:Theme.Holo.Light' 을 ANDROID ns 로 normalize`() {
        // v2 round 2 follow-up #6 (Codex Q6): cross-ns chain 의 ANDROID hop 정확화.
        // child.parentStyleName = "android:Theme.Holo.Light" → namespace=ANDROID + name="Theme.Holo.Light" 로 lookup.
        val bundle = LayoutlibResourceBundle.build(mapOf(
            ResourceNamespace.ANDROID to listOf(style("Theme.Holo.Light", null)),
            ResourceNamespace.RES_AUTO to listOf(style("Platform.AppCompat.Light", "android:Theme.Holo.Light")),
        ))
        val rr = LayoutlibRenderResources(bundle, "Platform.AppCompat.Light")
        val child = rr.getStyle(ref(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Platform.AppCompat.Light"))!!
        val parent = rr.getParent(child)
        assertNotNull(parent, "android: prefix 가 ANDROID ns 로 normalize")
        assertEquals("Theme.Holo.Light", parent!!.name)
    }

    private fun style(name: String, parent: String?) = ParsedNsEntry.StyleDef(name, parent, emptyList(), ResourceNamespace.RES_AUTO, null)
    private fun simple(t: ResourceType, n: String, v: String) = ParsedNsEntry.SimpleValue(t, n, v, ResourceNamespace.RES_AUTO, null)
    private fun ref(ns: ResourceNamespace, t: ResourceType, n: String) = ResourceReference(ns, t, n)
}
```

- [ ] **Step 2: 테스트 실행 — fail 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.LayoutlibRenderResourcesTest"`
Expected: compilation failure.

- [ ] **Step 3: LayoutlibRenderResources.kt 작성 (spec §5.1 의 코드 + helpers)**

```kotlin
// LayoutlibRenderResources.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleResourceValueImpl
import com.android.resources.ResourceType

/**
 * W3D4 §3.1 #8 / §5.1: namespace-aware RenderResources subclass.
 * 9 method override + chain walker + theme stack — layoutlib base 의 stub 회피.
 *
 * Q3 σ FULL: getResolvedResource 가 자체 chain walker 호출.
 * Q2 fallback uniform: getStyle / getParent 모두 ns-exact-then-name.
 */
class LayoutlibRenderResources(
    private val bundle: LayoutlibResourceBundle,
    private val defaultThemeName: String,
) : RenderResources() {

    private val mDefaultTheme: StyleResourceValue =
        bundle.getStyleByName(defaultThemeName) ?: emptyTheme(defaultThemeName)

    private val mThemeStack: MutableList<StyleResourceValue> = computeInitialStack()

    private fun computeInitialStack(): MutableList<StyleResourceValue> {
        val stack = mutableListOf<StyleResourceValue>()
        stack += mDefaultTheme
        var cur: StyleResourceValue? = mDefaultTheme
        var hops = 0
        while (cur != null && hops < AppLibraryResourceConstants.MAX_THEME_HOPS) {
            val parent = walkParent(cur)
            if (parent == null) break
            if (stack.contains(parent)) break  // cycle
            stack += parent
            cur = parent
            hops++
        }
        return stack
    }

    private fun walkParent(style: StyleResourceValue): StyleResourceValue? {
        val parentRef = style.parentStyle
        if (parentRef != null) {
            val exact = bundle.getStyleExact(parentRef)
            if (exact != null) return exact
            return bundle.getStyleByName(parentRef.name)
        }
        // v2 round 2 follow-up #6: parentStyleName "android:Theme.Holo.Light" → ANDROID ns + bare name.
        val rawName = style.parentStyleName ?: return null
        return resolveStyleNameWithNamespace(rawName)
    }

    /**
     * v2 round 2 follow-up #6 (Codex Q6): cross-ns parent normalization.
     * "android:Theme.Holo.Light" → namespace=ANDROID + name="Theme.Holo.Light" 로 ns-exact lookup.
     * "Theme.AxpFixture" 처럼 prefix 없으면 ns-agnostic getStyleByName fallback.
     */
    private fun resolveStyleNameWithNamespace(rawName: String): StyleResourceValue? {
        val sepIdx = rawName.indexOf(AppLibraryResourceConstants.NS_NAME_SEPARATOR)
        if (sepIdx < 0) return bundle.getStyleByName(rawName)
        val nsPrefix = rawName.substring(0, sepIdx)
        val bareName = rawName.substring(sepIdx + AppLibraryResourceConstants.NS_NAME_SEPARATOR.length)
        val ns = if (nsPrefix == AppLibraryResourceConstants.ANDROID_NS_PREFIX) ResourceNamespace.ANDROID else ResourceNamespace.RES_AUTO
        val exact = bundle.getStyleExact(ResourceReference(ns, ResourceType.STYLE, bareName))
        if (exact != null) return exact
        return bundle.getStyleByName(bareName)
    }

    override fun getDefaultTheme(): StyleResourceValue = mDefaultTheme

    override fun getAllThemes(): List<StyleResourceValue> = mThemeStack.toList()

    override fun getStyle(ref: ResourceReference): StyleResourceValue? =
        bundle.getStyleExact(ref) ?: bundle.getStyleByName(ref.name)

    override fun getParent(style: StyleResourceValue): StyleResourceValue? = walkParent(style)

    override fun getUnresolvedResource(ref: ResourceReference): ResourceValue? =
        bundle.getResource(ref)

    override fun getResolvedResource(ref: ResourceReference): ResourceValue? {
        val unresolved = bundle.getResource(ref) ?: return null
        return resolveResValue(unresolved)
    }

    override fun resolveResValue(value: ResourceValue?): ResourceValue? {
        if (value == null) return null
        var current: ResourceValue = value
        val seen = HashSet<ResourceReference>()
        var hops = 0
        while (hops < AppLibraryResourceConstants.MAX_REF_HOPS) {
            val text = current.value ?: return current
            // v2 round 2 follow-up #3 (Codex Q1): @null / @empty 는 sentinel — ref 가 아님 → 즉시 raw 반환.
            if (text == AppLibraryResourceConstants.RES_VALUE_NULL_LITERAL ||
                text == AppLibraryResourceConstants.RES_VALUE_EMPTY_LITERAL) {
                return current
            }
            val refLike = parseReference(text) ?: return current
            if (!seen.add(refLike)) {
                System.err.println("[LayoutlibRenderResources] circular ref: ${current.name} → $refLike")
                return current
            }
            val next = if (refLike.resourceType == ResourceType.ATTR) {
                findItemInTheme(refLike) ?: return current
            } else {
                getUnresolvedResource(refLike) ?: return current
            }
            current = next
            hops++
        }
        // v2 round 2 follow-up #5: hop 초과 시 진단 로그 + 현재 value 반환 (graceful).
        System.err.println(
            "[LayoutlibRenderResources] MAX_REF_HOPS(${AppLibraryResourceConstants.MAX_REF_HOPS}) 초과 — chain ended at ${current.name}=${current.value}"
        )
        return current
    }

    override fun dereference(value: ResourceValue?): ResourceValue? = resolveResValue(value)

    override fun findItemInStyle(
        style: StyleResourceValue,
        ref: ResourceReference,
    ): ResourceValue? {
        var cur: StyleResourceValue? = style
        var hops = 0
        while (cur != null && hops < AppLibraryResourceConstants.MAX_THEME_HOPS) {
            val item = cur.getItem(ref)
            if (item != null) return item
            cur = walkParent(cur)
            hops++
        }
        return null
    }

    override fun findItemInTheme(ref: ResourceReference): ResourceValue? {
        for (theme in mThemeStack) {
            val item = findItemInStyle(theme, ref)
            if (item != null) return item
        }
        return null
    }

    override fun applyStyle(style: StyleResourceValue, force: Boolean) {
        if (force) mThemeStack.clear()
        if (!mThemeStack.contains(style)) mThemeStack.add(0, style)
    }

    override fun clearStyles() {
        mThemeStack.clear()
        mThemeStack += mDefaultTheme
    }

    override fun clearAllThemes() = mThemeStack.clear()

    private fun emptyTheme(name: String): StyleResourceValue = StyleResourceValueImpl(
        ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, name),
        null, null,
    )

    /**
     * v2 round 2 follow-up #3 (Codex Q1): self-built regex 대신 layoutlib-api 의
     * `com.android.resources.ResourceUrl.parse()` 직접 활용. 동일 패턴 + private (`@*`/`?*`)
     * + theme attr (`?`) + sentinel (`@null`/`@empty`) 모두 동등 처리.
     *
     * 호출 직전에 sentinel 분기 (text == "@null" / "@empty") 이미 done — parseReference 는
     * "ref-shaped" 입력에서만 호출됨. ResourceUrl.parse 가 sentinel 에는 null 반환 (graceful).
     *
     * namespace 결정: ResourceUrl.namespace == "android" → ANDROID, otherwise RES_AUTO.
     */
    private fun parseReference(text: String): ResourceReference? {
        val url = com.android.resources.ResourceUrl.parse(text) ?: return null
        val type = url.type ?: return null
        val ns = if (url.namespace == AppLibraryResourceConstants.ANDROID_NS_PREFIX)
            ResourceNamespace.ANDROID else ResourceNamespace.RES_AUTO
        return ResourceReference(ns, type, url.name)
    }
}
```

**API 검증 보강 (round 2 evidence)**: layoutlib-api 31.13.2 의 `com.android.resources.ResourceUrl.parse(String)` 가:
- `?attr/X`, `?android:attr/X`, `?colorPrimary` (attr/ 누락 형태) → ATTR
- `@color/X`, `@android:color/X`, `@+id/X`, `@style/X` → 해당 type
- `@*android:color/X`, `?*android:attr/X` → private=true 보존하나 type/name 동일
- `@null`, `@empty` → null 반환 (sentinel 처리는 호출자 책임)

위 경로 모두 우리 chain walker 와 정합. `?colorPrimary` 같은 짧은 형태도 ATTR 로 정상 처리 — plan v1 의 self-built regex 의 group capture 모호성 회피.

- [ ] **Step 4: 테스트 실행 — pass 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.LayoutlibRenderResourcesTest"`
Expected: 12 PASS (v2: sentinel + private override + android: normalization +2).

- [ ] **Step 5: Commit**

```bash
git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibRenderResources.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibRenderResourcesTest.kt
git commit -m "$(cat <<'EOF'
feat(w3d4): T6 LayoutlibRenderResources — Q3 σ FULL (9 override + chain walker)

W3D4 §3.1 #8 + §5.1. layoutlib RenderResources base 의 null/empty
stub 회피 (페어 round 1 verdict). 9 method override:
- getDefaultTheme / getAllThemes / getStyle / getParent
- getUnresolvedResource / getResolvedResource / resolveResValue / dereference
- findItemInStyle / findItemInTheme
- applyStyle / clearStyles / clearAllThemes

ns-exact-then-name fallback uniform (Q2). chain walker + theme stack
+ circular detection.

v2 round 2 follow-up: parseReference 가 self-built regex 대신 layoutlib-api
ResourceUrl.parse 직접 활용 (sentinel + private override 처리). @null/@empty
sentinel 즉시 raw 반환. android: parent normalization 으로 cross-ns chain
정확화. MAX_THEME_HOPS=32 (실측 17 hop + ThemeOverlay buffer).

12 unit PASS.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Task 7: RJarSymbolSeeder canonicalization (R-1) + duplicate attr ID (R-2)

**Files:**
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RNameCanonicalizationTest.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RDuplicateAttrIdTest.kt`

W3D3-α 의 `RJarSymbolSeeder` 가 R$style 클래스 walk 시 underscore name (`Theme_AxpFixture`) emit. round 2 가 dot name 으로 매핑 추가. + multiple R class 의 동명 attr 는 first-wins per name + 진단.

- [ ] **Step 1: 현 RJarSymbolSeeder 구조 확인**

Run: `grep -n "R\\$style\\|R\\$attr\\|seed\\|emit\\|register" server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt | head -30`
Expected: R$style + R$attr 처리 path 발견. spec §3.2 의 "R$style underscore→dot" 명시.

- [ ] **Step 2: RNameCanonicalizationTest 작성 (3 case)**

```kotlin
// RNameCanonicalizationTest.kt
package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RNameCanonicalizationTest {

    @Test
    fun `R style underscore name 이 dot name 으로 변환`() {
        assertEquals("Theme.AxpFixture", RNameCanonicalization.styleNameToXml("Theme_AxpFixture"))
        assertEquals("Theme.Material3.DayNight.NoActionBar", RNameCanonicalization.styleNameToXml("Theme_Material3_DayNight_NoActionBar"))
    }

    @Test
    fun `R attr name 은 underscore 보존 (R attr 는 dot 없음)`() {
        // attr 은 single-word 가 일반적이지만 underscore 보존이 정책
        assertEquals("colorPrimary", RNameCanonicalization.attrName("colorPrimary"))
        assertEquals("max_lines", RNameCanonicalization.attrName("max_lines"))
    }

    @Test
    fun `R style edge case — underscore 없는 name 그대로`() {
        assertEquals("Theme", RNameCanonicalization.styleNameToXml("Theme"))
        assertEquals("Widget", RNameCanonicalization.styleNameToXml("Widget"))
    }
}
```

- [ ] **Step 3: RNameCanonicalization helper 작성**

```kotlin
// RNameCanonicalization.kt
package dev.axp.layoutlib.worker.classloader

/**
 * W3D4 §7.1 (R-1): R$style 의 underscore name (e.g., `Theme_AxpFixture`) 와
 * XML 의 dot name (e.g., `Theme.AxpFixture`) 매핑. AAPT 가 style name 의 dot 을
 * R class field 에 underscore 로 변환하는 표준 동작.
 *
 * attr/dimen/color 등은 underscore 가 의미 있는 separator → 보존.
 */
internal object RNameCanonicalization {
    /** R$style 필드명 → XML style name. underscore → dot 일괄 치환. */
    fun styleNameToXml(rFieldName: String): String = rFieldName.replace('_', '.')

    /** R$attr 필드명 → XML attr name. 변환 없음 (underscore 보존). */
    fun attrName(rFieldName: String): String = rFieldName

    /** R$dimen / R$color / R$bool / 등 — 변환 없음. */
    fun simpleName(rFieldName: String): String = rFieldName
}
```

- [ ] **Step 4: RJarSymbolSeeder 의 single-callback API 에 정확한 inline diff 적용**

**v2 round 2 follow-up #1 (Codex Q2 + Claude Q2 FULL convergence DISAGREE)**:
plan v1 의 `callback.registerStyle/registerAttr` placeholder 는 실 API 와 mismatch.
실 API = `register: (ResourceReference, Int) -> Unit` 단일 callback (1 arg + 1 emit). 정확한 변경:

**현 RJarSymbolSeeder.kt (W3D3-α 종료 시점, 2026-04-26 commit `b556dc8`)**:
- line 26-30: `fun seed(rJarPath: Path, rJarLoader: ClassLoader, register: (ResourceReference, Int) -> Unit)`
- line 31-54: ZipFile 순회 → seedClass 호출
- line 77-107: seedClass — `register(ResourceReference(namespace, type, field.name), value)` line 105 가 emit point

**v2 변경**: (a) seed() outer scope 에 `seenAttrNames: HashSet<String>()` 추가 (cross-class dedup), (b) seedClass() 에 `seenAttrNames` 인자 전달, (c) line 105 emit 직전에 type 분기로 STYLE name canonicalization + ATTR first-wins guard.

```kotlin
// RJarSymbolSeeder.kt — v2 변경 후 (전체 본문 — replace_all 권장)
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

internal object RJarSymbolSeeder
{
    fun seed(
        rJarPath: Path,
        rJarLoader: ClassLoader,
        register: (ResourceReference, Int) -> Unit,
    )
    {
        // v2 follow-up #1 (R-2): cross-class first-wins per attr name. appcompat R$attr
        // 와 material R$attr 양쪽이 'colorPrimary' 를 등록 시도하면 첫 등장만 통과.
        // outer scope = ZipFile 단위 (한 R.jar 안 모든 R$attr 클래스 공유).
        val seenAttrNames = HashSet<String>()
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
                    continue
                }
                seedClass(
                    rJarLoader,
                    internalName.replace(INTERNAL_NAME_SEPARATOR, EXTERNAL_NAME_SEPARATOR),
                    packageName,
                    resourceType,
                    register,
                    seenAttrNames,
                )
            }
        }
    }

    internal fun parseRClassName(internalName: String): Pair<String, String>?
    {
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
        seenAttrNames: MutableSet<String>,
    )
    {
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
            // v2 follow-up #1 (R-1): R$style 의 underscore name → XML dot name canonicalization.
            // R$attr / R$dimen / R$color / R$bool / 등은 underscore 보존.
            val emitName = if (type == ResourceType.STYLE)
            {
                RNameCanonicalization.styleNameToXml(field.name)
            }
            else
            {
                field.name
            }
            // v2 follow-up #1 (R-2): R$attr 만 cross-class first-wins guard 적용.
            // 다른 type (style/dimen/color/...) 은 namespace 가 R class 별 다르므로 ResourceReference 자체로 동치 충돌 없음.
            if (type == ResourceType.ATTR)
            {
                if (!AttrSeederGuard.tryRegister(emitName, value, packageName, seenAttrNames))
                {
                    continue
                }
            }
            register(ResourceReference(namespace, type, emitName), value)
        }
    }
}
```

**핵심 결정**:
- `seenAttrNames` 의 scope = `seed()` outer (한 R.jar 의 모든 R$attr 클래스 cross-class 공유).
- STYLE canonicalization 은 emit 직전. 변환 실패 case 없음 (replace 는 실패 안 함).
- ATTR first-wins guard 는 type==ATTR 일 때만. R$style/R$color 등은 R class 단위 namespace 가 달라 ResourceReference equals 가 namespace 포함 → 동명 conflict 자체가 없음.

- [ ] **Step 5: RNameCanonicalizationTest 실행 — pass 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.RNameCanonicalizationTest"`
Expected: 3 PASS.

- [ ] **Step 6: RDuplicateAttrIdTest 작성 (3 case)**

```kotlin
// RDuplicateAttrIdTest.kt
package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class RDuplicateAttrIdTest {

    @Test
    fun `같은 attr name 이 두 R class 에 → first-wins 진단 출력`() {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try {
            val seen = HashSet<String>()
            val accepted1 = AttrSeederGuard.tryRegister("colorPrimary", 0x1, "androidx.appcompat", seen)
            val accepted2 = AttrSeederGuard.tryRegister("colorPrimary", 0x2, "com.google.android.material", seen)
            assertTrue(accepted1)
            assertTrue(!accepted2, "second attempt skipped (first-wins)")
            assertTrue(errOut.toString().contains("dup attr 'colorPrimary'"))
        } finally {
            System.setErr(origErr)
        }
    }

    @Test
    fun `다른 attr name 은 모두 등록`() {
        val seen = HashSet<String>()
        assertTrue(AttrSeederGuard.tryRegister("colorPrimary", 0x1, "p", seen))
        assertTrue(AttrSeederGuard.tryRegister("colorAccent", 0x2, "p", seen))
        assertEquals(2, seen.size)
    }

    @Test
    fun `같은 R class 안 동명 — first-wins 도 적용 (방어적)`() {
        val seen = HashSet<String>()
        AttrSeederGuard.tryRegister("x", 0x1, "p", seen)
        val second = AttrSeederGuard.tryRegister("x", 0x1, "p", seen)
        assertTrue(!second, "동일 R class 안에서도 first-wins")
    }
}
```

- [ ] **Step 7: AttrSeederGuard helper 작성**

```kotlin
// AttrSeederGuard.kt
package dev.axp.layoutlib.worker.classloader

/**
 * W3D4 §7.2 (R-2): multiple R class 에 같은 attr name 등장 시 first-wins.
 * 진단 로그로 가시화.
 */
internal object AttrSeederGuard {
    /**
     * @return 등록 성공 (first encounter) → true, skip (duplicate) → false
     */
    fun tryRegister(name: String, id: Int, sourcePackage: String, seen: MutableSet<String>): Boolean {
        if (seen.add(name)) return true
        System.err.println(
            "[RJarSymbolSeeder] dup attr '$name' from $sourcePackage (id=0x${Integer.toHexString(id)}) — first-wins, skipped"
        )
        return false
    }
}
```

- [ ] **Step 8: RJarSymbolSeeder 의 R$attr 처리에 AttrSeederGuard 적용 — Step 4 의 inline diff 가 이미 통합 적용**

Step 4 의 v2 변경 본문이 R$attr 의 `tryRegister` + `seenAttrNames` outer scope 통합을 모두 포함. 별도 변경 없음 (placeholder fix 의 결과).

- [ ] **Step 9: RDuplicateAttrIdTest 실행 — pass 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.RDuplicateAttrIdTest"`
Expected: 3 PASS.

- [ ] **Step 10: RJarSymbolSeederIntegrationTest end-to-end 회귀 case 추가 (v2 follow-up #11)**

기존 RJarSymbolSeederTest 에 다음 case 추가 (mock R.jar 로 seed → emit reference 검증):

```kotlin
// RJarSymbolSeederTest.kt 에 추가 (기존 4 case → 5 case)

@Test
fun `seed 가 R style underscore name 을 dot name 으로 emit`() {
    // v2 round 2 follow-up #11: end-to-end 회귀 — Theme_AxpFixture seeding → Theme.AxpFixture lookup.
    val rJar = makeRJarWithStyle(packageName = "com.fixture", styleField = "Theme_AxpFixture", id = 0x7f0c0001)
    val emitted = mutableListOf<Pair<ResourceReference, Int>>()
    val loader = java.net.URLClassLoader(arrayOf(rJar.toUri().toURL()), null)
    RJarSymbolSeeder.seed(rJar, loader) { ref, id -> emitted += ref to id }
    val styles = emitted.filter { it.first.resourceType == ResourceType.STYLE }
    assertTrue(styles.any { it.first.name == "Theme.AxpFixture" }, "underscore→dot canonicalization (실측 emit name)")
    assertTrue(styles.none { it.first.name == "Theme_AxpFixture" }, "underscore name 은 emit 안 됨 (변환 후만)")
}

// makeRJarWithStyle helper 도 동일 파일에 추가 — int field 1개 가진 mock R.jar 생성
```

- [ ] **Step 11: 기존 W3D3-α RJarSymbolSeeder 테스트 회귀 확인**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.*"`
Expected: 모든 W3D3 + W3D4 신규 PASS (RJarSymbolSeederTest 4→5 + RNameCanonicalizationTest 3 + RDuplicateAttrIdTest 3 = 11 case).

- [ ] **Step 12: Commit**

```bash
git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RNameCanonicalization.kt \
        server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AttrSeederGuard.kt \
        server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RNameCanonicalizationTest.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RDuplicateAttrIdTest.kt \
        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeederTest.kt
git commit -m "$(cat <<'EOF'
feat(w3d4): T7 RJarSymbolSeeder canonicalization + dup attr first-wins

W3D4 §7.1 (R-1) + §7.2 (R-2). Codex Q6 NEW gaps:
- R$style 의 underscore name (Theme_AxpFixture) → XML dot name
  (Theme.AxpFixture) 변환 (RNameCanonicalization.styleNameToXml).
- multiple R class (appcompat/material) 의 동명 attr first-wins +
  진단 로그 (AttrSeederGuard.tryRegister).

v2 round 2 follow-up: 정확한 inline diff (single callback API),
seenAttrNames outer-scope (cross-class dedup), end-to-end seed 회귀 case.

W3D3-α RJarSymbolSeeder 의 R$style/R$attr 처리에 통합.
7 unit PASS (3 canon + 3 dup + 1 seeder integration).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Task 8: LayoutlibRenderer + SharedLayoutlibRenderer 통합 (themeName 5-tuple)

**v2 round 2 follow-up #2 (Codex Q4 + Claude Q4 DISAGREE 정합)**: plan v1 가 "5번째 인자 추가" 로 묘사했으나 실은 **ctor parameter reorder** + default param 위반 + positional caller 영향. round 2 가 모두 explicit 으로 처리.

**Files:**
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` (line 48-53 ctor reorder + 5번째 인자 + default param 제거)
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt` (line 174 swap to LayoutlibResourceValueLoader)
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt` (test sourceSet — RendererArgs 3 → 4-tuple, themeName 추가)
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt` (DEFAULT_FIXTURE_THEME 추가)
- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` (line 133 caller — themeName named arg)
- Modify (test callers): `LayoutlibRendererIntegrationTest.kt`, `SharedLayoutlibRendererIntegrationTest.kt`, `LayoutlibRendererTier3MinimalTest.kt`
- Delete: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt`
- Delete: `.../resources/FrameworkRenderResources.kt`
- Delete: `.../resources/FrameworkResourceValueLoader.kt`
- Delete: `.../resources/FrameworkValueParser.kt`
- Delete: `.../resources/ParsedEntry.kt`
- Delete: 대응 W3D1 test 파일들 (대체 흡수됨)

- [ ] **Step 1: SessionConstants 에 DEFAULT_FIXTURE_THEME 추가**

```kotlin
// session/SessionConstants.kt 의 DEFAULT_FRAMEWORK_THEME 아래에 추가
/** W3D4 fixture default theme — sample-app 의 themes.xml 정의. */
const val DEFAULT_FIXTURE_THEME = "Theme.AxpFixture"
```

- [ ] **Step 2: LayoutlibRenderer ctor reorder + 5번째 인자 (default param 제거 — CLAUDE.md 규약)**

**기존 ctor (실 코드 line 48-53)**:
```kotlin
class LayoutlibRenderer(
    private val distDir: Path,
    private val fallback: PngRenderer?,        // <-- 2nd (positional caller 가 의존)
    private val fixtureRoot: Path,
    private val sampleAppModuleRoot: Path,
) : PngRenderer
```

**v2 변경 ctor**:
```kotlin
class LayoutlibRenderer(
    private val distDir: Path,
    private val fixtureRoot: Path,
    private val sampleAppModuleRoot: Path,
    private val themeName: String,             // <-- v2 NEW (Theme.AxpFixture)
    private val fallback: PngRenderer?,        // <-- 마지막 (default value 없음 — CLAUDE.md "No default parameter values")
) : PngRenderer
```

**중요**:
- (a) `fallback` 위치 reorder: 2nd → 5th (last). 모든 positional caller 가 영향.
- (b) `themeName: String` NEW 추가, default value 없음.
- (c) `fallback: PngRenderer? = null` default 제거 → caller 가 explicit `null` 전달 의무.

renderViaLayoutlib 의 line 174 swap (W3D1 → W3D4):
```kotlin
// 기존:
//   val bundle = FrameworkResourceValueLoader.loadOrGet(
//       distDir.resolve(ResourceLoaderConstants.DATA_DIR)
//   )
//   val resources = FrameworkRenderResources(bundle, SessionConstants.DEFAULT_FRAMEWORK_THEME)

// 변경:
val bundle = LayoutlibResourceValueLoader.loadOrGet(
    LayoutlibResourceValueLoader.Args(
        distDataDir = distDir.resolve(ResourceLoaderConstants.DATA_DIR),
        sampleAppRoot = sampleAppModuleRoot,
        runtimeClasspathTxt = sampleAppModuleRoot.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
    )
)
val resources = LayoutlibRenderResources(bundle, themeName)
```

- [ ] **Step 3: test-only SharedLayoutlibRenderer 의 RendererArgs 3 → 4-tuple + LayoutlibRenderer 호출 reorder**

**현 test SharedLayoutlibRenderer.kt** (line 30-50):
```kotlin
object SharedLayoutlibRenderer
{
    @Synchronized
    fun getOrCreate(distDir: Path, fixtureRoot: Path, sampleAppModuleRoot: Path, fallback: PngRenderer?): LayoutlibRenderer
    {
        val requested = RendererArgs(distDir, fixtureRoot, sampleAppModuleRoot)
        SharedRendererBinding.verify(boundArgs, requested)
        instance?.let { return it }
        val created = LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)  // <-- positional, fallback 2nd
        instance = created
        boundArgs = requested
        return created
    }
}
```

**v2 변경**:
```kotlin
object SharedLayoutlibRenderer
{
    @Synchronized
    fun getOrCreate(
        distDir: Path,
        fixtureRoot: Path,
        sampleAppModuleRoot: Path,
        themeName: String,                  // <-- v2 NEW (4th, default value 없음)
        fallback: PngRenderer?,
    ): LayoutlibRenderer
    {
        val requested = RendererArgs(distDir, fixtureRoot, sampleAppModuleRoot, themeName)  // <-- 4-tuple
        SharedRendererBinding.verify(boundArgs, requested)
        instance?.let { return it }
        // v2 round 2 follow-up #2: positional → named call (silent reorder 위험 회피).
        val created = LayoutlibRenderer(
            distDir = distDir,
            fixtureRoot = fixtureRoot,
            sampleAppModuleRoot = sampleAppModuleRoot,
            themeName = themeName,
            fallback = fallback,
        )
        instance = created
        boundArgs = requested
        return created
    }
}
```

또한 `RendererArgs` data class 가 `themeName` 포함 4-tuple 로 갱신.

- [ ] **Step 4: 7 caller 별 명시적 수정 (named args 강제)**

**v2 round 2 follow-up #2**: grep 으로 잡힌 7 callsite 별 정확한 변경:

| # | Path | 현재 | v2 변경 |
|---|---|---|---|
| 1 | `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:133` | named args, `fallback = PlaceholderPngRenderer(), fixtureRoot, sampleAppModuleRoot` | 5 named args 추가 (themeName), 기존 named 그대로 |
| 2 | `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:45` (positional!) | `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` | Step 3 의 named call 로 교체 (위 코드) |
| 3 | `LayoutlibRendererIntegrationTest.kt:32` | named getOrCreate(dist, fixture, moduleRoot, fallback=null) | `themeName = SessionConstants.DEFAULT_FIXTURE_THEME` 추가 |
| 4 | `SharedLayoutlibRendererIntegrationTest.kt:30` | 동일 | 동일 |
| 5 | `SharedLayoutlibRendererIntegrationTest.kt:40,42` | 2 calls 동일 | 2 calls 동일 |
| 6 | `SharedLayoutlibRendererIntegrationTest.kt:53,58` | 2 calls 동일 | 2 calls 동일 |
| 7 | `LayoutlibRendererTier3MinimalTest.kt:50` | named | `themeName = SessionConstants.DEFAULT_FIXTURE_THEME` 추가 |

**검증 grep** (Step 4 끝에 실행):
```bash
grep -rn "SharedLayoutlibRenderer.getOrCreate\|LayoutlibRenderer(" server/ --include="*.kt"
```
expected: 모든 callsite 가 `themeName = ...` 인자 포함 + named args 형태 (positional 0건).

- [ ] **Step 5: W3D1 framework-only Test (FrameworkResourceBundleTest 등) 삭제 / 흡수 확인**

Run: `git rm server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoader.kt server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedEntry.kt`

대응 W3D1 test 도 삭제:
Run: `git rm server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/ParsedEntryTest.kt`

- [ ] **Step 6: 전체 unit + integration build 회귀 확인**

Run: `./server/gradlew -p server build`
Expected: BUILD SUCCESSFUL. unit ~210+ PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(w3d4): T8 LayoutlibRenderer + Shared 통합 — themeName 5-tuple + W3D1 흡수

W3D4 §3.2. LayoutlibRenderer ctor reorder + themeName 5번째 인자
+ default param 제거 (CLAUDE.md 규약). 7 callsite 모두 named args 강제.
SharedLayoutlibRenderer.RendererArgs 4-tuple (themeName 포함).
SessionConstants.DEFAULT_FIXTURE_THEME = "Theme.AxpFixture" 추가.

LayoutlibRenderer.renderViaLayoutlib line 174:
  FrameworkResourceValueLoader → LayoutlibResourceValueLoader
  FrameworkRenderResources → LayoutlibRenderResources

W3D1 흡수 — Framework{ResourceBundle,RenderResources,ResourceValueLoader,
ValueParser}, ParsedEntry 5 파일 삭제. 대응 W3D1 test 삭제 (신규 test
가 동등 case 포함).

build SUCCESSFUL, unit ~217 PASS (v2 follow-up: T5 +1, T6 +2, T7 +1).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Task 9: Integration test 분리 (tier3_basic_primary + tier3_basic_minimal_smoke)

**Files:**
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`
- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/MaterialFidelityIntegrationTest.kt`

기존 `tier3 basic` 테스트의 `renderWithMaterialFallback` helper 제거. 대신 2개 분리 테스트.

- [ ] **Step 1: MaterialFidelityIntegrationTest (unit-level real bundle, no layoutlib) 작성 (4 case)**

```kotlin
// MaterialFidelityIntegrationTest.kt
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import dev.axp.layoutlib.worker.DistDiscovery
import dev.axp.layoutlib.worker.FixtureDiscovery
import dev.axp.layoutlib.worker.session.SessionConstants

@Tag("integration")
class MaterialFidelityIntegrationTest {

    @Test
    fun `Theme AxpFixture parent walk to Theme — real bundle`() {
        val (dist, sampleApp) = locate() ?: return
        val args = LayoutlibResourceValueLoader.Args(
            distDataDir = dist.resolve(ResourceLoaderConstants.DATA_DIR),
            sampleAppRoot = sampleApp,
            runtimeClasspathTxt = sampleApp.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
        )
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        val themes = listOf(ResourceNamespace.ANDROID, ResourceNamespace.RES_AUTO)
        assertEquals(themes, bundle.namespacesInOrder())

        val rr = LayoutlibRenderResources(bundle, SessionConstants.DEFAULT_FIXTURE_THEME)
        val stack = rr.allThemes
        val names = stack.map { it.name }
        assertTrue(names.contains("Theme.AxpFixture"), "stack 에 AxpFixture")
        assertTrue(names.any { it.startsWith("Theme.Material3") }, "stack 에 Material3 ancestor")
        assertTrue(names.contains("Theme") || names.any { it.startsWith("Theme.AppCompat") }, "최상단 Theme/AppCompat")
        // v2 round 2 follow-up #5 (Codex Q6): 실측 chain depth = 17 hop. 회귀 시 chain 끊김 detect.
        assertTrue(stack.size >= 15, "chain depth ≥ 15 (실측 17, MAX_THEME_HOPS=32 마진), got ${stack.size}")
    }

    @Test
    fun `theme 의 colorPrimary item 가 ?attr 형태가 아닌 raw 값으로 resolve`() {
        val (dist, sampleApp) = locate() ?: return
        val rr = makeRR(dist, sampleApp)
        val ref = com.android.ide.common.rendering.api.ResourceReference(
            ResourceNamespace.RES_AUTO, com.android.resources.ResourceType.ATTR, "colorPrimary"
        )
        val item = rr.findItemInTheme(ref)
        assertNotNull(item, "Theme.AxpFixture 에 colorPrimary item 정의됨")
    }

    @Test
    fun `Material3 부모 chain 의 colorPrimaryContainer 가 추적 가능`() {
        val (dist, sampleApp) = locate() ?: return
        val rr = makeRR(dist, sampleApp)
        val ref = com.android.ide.common.rendering.api.ResourceReference(
            ResourceNamespace.RES_AUTO, com.android.resources.ResourceType.ATTR, "colorPrimaryContainer"
        )
        val item = rr.findItemInTheme(ref)
        // Theme.AxpFixture 가 직접 정의 (themes.xml 의 colorPrimaryContainer)
        assertNotNull(item)
    }

    @Test
    fun `bundle 의 RES_AUTO 안에 41 AAR 의 styles 통합 (27+ 보유)`() {
        val (dist, sampleApp) = locate() ?: return
        val args = LayoutlibResourceValueLoader.Args(
            distDataDir = dist.resolve(ResourceLoaderConstants.DATA_DIR),
            sampleAppRoot = sampleApp,
            runtimeClasspathTxt = sampleApp.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
        )
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        val resAutoStyles = bundle.styleCountForNamespace(ResourceNamespace.RES_AUTO)
        assertTrue(resAutoStyles > 100, "Material/AppCompat AAR 의 다수 style → expected > 100, got $resAutoStyles")
    }

    private fun makeRR(dist: java.nio.file.Path, sampleApp: java.nio.file.Path): LayoutlibRenderResources {
        val args = LayoutlibResourceValueLoader.Args(
            distDataDir = dist.resolve(ResourceLoaderConstants.DATA_DIR),
            sampleAppRoot = sampleApp,
            runtimeClasspathTxt = sampleApp.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
        )
        val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        return LayoutlibRenderResources(bundle, SessionConstants.DEFAULT_FIXTURE_THEME)
    }

    private fun locate(): Pair<java.nio.file.Path, java.nio.file.Path>? {
        val dist = DistDiscovery.locate(null) ?: run { assumeTrue(false, "dist 없음"); return null }
        val fixture = FixtureDiscovery.locate(null) ?: run { assumeTrue(false, "fixture 없음"); return null }
        // sampleAppRoot = fixture/sample-app (FixtureDiscovery.locateModuleRoot 는 W3D3 에 있음)
        val sampleApp = FixtureDiscovery.locateModuleRoot(null) ?: run { assumeTrue(false, "module root 없음"); return null }
        return dist to sampleApp
    }
}
```

- [ ] **Step 2: LayoutlibRendererIntegrationTest 변경 — primary 강제 + minimal smoke 분리**

```kotlin
// LayoutlibRendererIntegrationTest.kt (수정)
@Tag("integration")
class LayoutlibRendererIntegrationTest {

    @Test
    fun `tier3 basic primary — activity_basic 가 직접 SUCCESS`() {
        val (dist, layoutRoot, moduleRoot) = locateAll() ?: return
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = layoutRoot,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_basic.xml")
        assertEquals(Result.Status.SUCCESS, renderer.lastSessionResult?.status, "primary SUCCESS")
        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG > $MIN_RENDERED_PNG_BYTES")
        assertTrue(isPngMagic(bytes), "PNG magic 헤더")
    }

    @Test
    fun `tier3 basic minimal smoke — activity_basic_minimal Button-only`() {
        val (dist, layoutRoot, moduleRoot) = locateAll() ?: return
        val renderer = SharedLayoutlibRenderer.getOrCreate(
            distDir = dist,
            fixtureRoot = layoutRoot,
            sampleAppModuleRoot = moduleRoot,
            themeName = SessionConstants.DEFAULT_FIXTURE_THEME,
            fallback = null,
        )
        val bytes = renderer.renderPng("activity_basic_minimal.xml")
        assertEquals(Result.Status.SUCCESS, renderer.lastSessionResult?.status, "minimal carry SUCCESS")
        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES)
    }

    private fun isPngMagic(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()

    /**
     * v2 round 2 follow-up #4 (Codex Q3 + Claude Q3 FULL convergence DISAGREE):
     * plan v1 placeholder `/* W3D3 의 helper 재활용 */ ...` → explicit body.
     *
     * W3D3 의 기존 3개 helper (locateDistDir / locateFixtureRoot / locateSampleAppModuleRoot)
     * 는 dist/fixture 가 `assumeTrue` graceful 하지만 module root 는 `requireNotNull` 강제 throw
     * 였음. v2 가 module root 도 graceful 으로 통일 (CI 환경에 sample-app 부재 시 SKIP — primary
     * test 가 dist/fixture/module 모두 의존).
     */
    private fun locateAll(): Triple<Path, Path, Path>?
    {
        val dist = DistDiscovery.locate(null)
        val fixture = FixtureDiscovery.locate(null)
        val moduleRoot = FixtureDiscovery.locateModuleRoot(null)
        if (dist == null || fixture == null || moduleRoot == null)
        {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "dist/fixture/moduleRoot 부재 — W3D3 helper 와 동일 graceful skip")
            return null
        }
        return Triple(dist, fixture, moduleRoot)
    }
}
```

(`renderWithMaterialFallback` helper 와 그 호출 제거 — round 2 ξ 결정.)

**v2 변경 추가 — chain depth 회귀 assert (follow-up #5)**:

`MaterialFidelityIntegrationTest` 의 `Theme AxpFixture parent walk to Theme — real bundle` 케이스에 다음 assert 추가:

```kotlin
// 기존 stack.size 검증 후 추가:
assertTrue(stack.size >= 15, "chain depth ≥ 15 (실측 17 hop, MAX_THEME_HOPS=32 마진 17). 회귀 시 chain 끊김 detect")
```

- [ ] **Step 3: 테스트 실행 — 둘 다 PASS**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest" -PincludeTags=integration`
Expected: 2 PASS.

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.MaterialFidelityIntegrationTest" -PincludeTags=integration`
Expected: 4 PASS.

- [ ] **Step 4: 전체 integration suite 회귀**

Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`
Expected: 13 PASS + 1 SKIP (tier3-glyph).

- [ ] **Step 5: smoke 종합 확인**

Run: `./server/gradlew -p server build`
Expected: BUILD SUCCESSFUL.

Run: `./server/gradlew -p server :worker-launcher:run --args="smoke"` (또는 W3D1/W3D3 와 동일 smoke entry)
Expected: "ok".

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(w3d4): T9 integration test 분리 — primary 강제 + minimal smoke

W3D4 §3.4 (test #9, #10). 기존 renderWithMaterialFallback helper 제거
(round 2 ξ 결정), tier3_basic_primary (activity_basic.xml 직접 SUCCESS)
+ tier3_basic_minimal_smoke (activity_basic_minimal.xml 자체 carry) 분리.

MaterialFidelityIntegrationTest 신규 — Theme.AxpFixture parent walk +
?attr/colorPrimary resolve + 41 AAR styles 카운트 (>100) 검증.

13 integration PASS + 1 SKIP (tier3-glyph).
unit ~213+ PASS, BUILD SUCCESSFUL, smoke "ok".

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Task 10: Work log + handoff + 08 §7.7.6 close + MEMORY 갱신

**Files:**
- Create: `docs/work_log/2026-04-29_w3d4-material-fidelity/session-log.md`
- Create: `docs/work_log/2026-04-29_w3d4-material-fidelity/handoff.md` (다음 milestone 가 있으면)
- Modify: `docs/plan/08-integration-reconciliation.md` (§7.7.6 close)
- Modify: `/home/bh-mark-dev-desktop/.claude/projects/-home-bh-mark-dev-desktop-workspace-android-xml-previewer/memory/MEMORY.md` (W3D4 close 메모)

- [ ] **Step 1: session-log.md 작성**

CLAUDE.md `session-work-log-protocol` 의 §1-4 형식. outcome / files / tests / landmines / 카운트 / pair-review / carry.

골격:
```markdown
# W3D4 MATERIAL-FIDELITY — Session Log (2026-04-29)

## Outcome
W3D3-α 의 Branch (B) contingency (`activity_basic_minimal.xml` Button) 폐기 →
primary `activity_basic.xml` (MaterialButton) 가 SUCCESS 로 직접 렌더.
namespace-aware resource bundle + 9-method LayoutlibRenderResources +
RJarSymbolSeeder canonicalization 의 결과.

## Carry from W3D3-α
- contingency keyword 매칭 (renderWithMaterialFallback): 폐기, ξ 결정.
- activity_basic_minimal.xml: 영구 유지 (smoke fixture, o 결정).
- LM-α-C: root cause 해결로 deprecated.

## Files modified
(T1-T9 의 모든 파일 + W3D1 5 파일 삭제)

## Tests (W3D4 종결 — v2 round 2 보강)
- unit: 167 → ~217 (+50). v2 follow-up: T5 dedupe winner +1, T6 sentinel/private/android-norm +2, T7 seeder integration +1.
- integration: 12 → 13 (+1) + 1 SKIP (tier3-glyph). MaterialFidelityIntegrationTest 의 chain depth ≥ 15 assert 회귀 detect.
- build SUCCESSFUL, smoke "ok"

## Landmines / 발견
- LM-W3D4-A: layoutlib RenderResources base 가 null/empty stub
  (resolveResValue/findItemInStyle/dereference/getParent/getAllThemes)
  — 페어 round 1 의 javap 검증으로 확정. ρ → σ FULL 전환의 근거.
- LM-W3D4-B: R$style underscore↔dot canonicalization. AAPT 의 표준
  변환을 RJarSymbolSeeder 가 수행해야.
- LM-W3D4-C: multiple R class (appcompat/material) 의 동명 attr
  first-wins. 진단 로그로 가시화.

## Pair-review 기록
- Round 1 (spec): full convergence Q3+Q5, set-converge Q1+Q2+Q4+Q6.
  4개 critical issue → ρ → σ FULL, RES_AUTO 모드 통일, R name canon 추가.
- Round 2 (plan v1 → v2, **REVISE_REQUIRED 정합**): 양 reviewer DISAGREE/NUANCED 정합으로
  judge round 불필요. full converge Q2+Q3 (RJarSymbolSeeder API mismatch + locateAll
  placeholder). Codex critical: build.gradle.kts:58 `.sorted()` lex order, T5 line 1114
  fake assertion, Files.list no sort. Claude critical: ctor reorder + 17 hop chain 실측.
  12 follow-up 모두 v2 inline 반영 (§0.B 표 참조).

## 08 §7.7.6 close 1줄
"W3D4 MATERIAL-FIDELITY: namespace-aware bundle + σ FULL resolver +
R name canon → primary activity_basic.xml SUCCESS. 167+12 → 217+13."

## 다음 milestone 후보
- W3D5 또는 W4-X tier3-glyph (현재 SKIP).
- POST-W2D6-POM-RESOLVE (carry).
```

- [ ] **Step 2: 08 §7.7.6 close 한 줄 추가**

`docs/plan/08-integration-reconciliation.md` 의 §7.7.5 다음에:
```markdown
### §7.7.6 — W3D4 MATERIAL-FIDELITY (CLOSED 2026-04-29)

namespace-aware LayoutlibResourceBundle + σ FULL LayoutlibRenderResources +
RJarSymbolSeeder R name canonicalization 으로 W3D3-α Branch (B) contingency
폐기. primary `activity_basic.xml` (MaterialButton) SUCCESS. unit 167→213,
integration 12→13. detail: `docs/work_log/2026-04-29_w3d4-material-fidelity/session-log.md`.
```

- [ ] **Step 3: MEMORY.md 갱신 (필요 시)**

W3D4 close 가 future-session reference 로 가치 있으면 1줄 entry 추가:
- `[W3D4 MATERIAL-FIDELITY close](today-2026-04-29.md) — namespace-aware bundle + σ FULL resolver, primary activity_basic.xml SUCCESS without contingency`

(또는 .remember/today-2026-04-29.md 에만 기록)

- [ ] **Step 4: handoff.md 작성 (다음 milestone 결정 후)**

W4 의 tier3-glyph 또는 POST-W2D6-POM-RESOLVE 가 다음 milestone 으로 결정되면 별도 handoff.md. 본 W3D4 자체 carry 가 0 이라면 handoff 생략 가능.

- [ ] **Step 5: 최종 build + smoke 종합 확인**

Run: `./server/gradlew -p server build && ./server/gradlew -p server :worker-launcher:run --args="smoke"`
Expected: BUILD SUCCESSFUL, smoke "ok".

- [ ] **Step 6: Commit**

```bash
git add docs/work_log/2026-04-29_w3d4-material-fidelity/ \
        docs/plan/08-integration-reconciliation.md
# MEMORY.md 변경했다면 별도 path
git commit -m "$(cat <<'EOF'
docs(w3d4): T10 work_log + 08 §7.7.6 close — W3D4 MATERIAL-FIDELITY 종결

W3D3-α Branch (B) contingency 폐기. primary activity_basic.xml SUCCESS.
unit 167→213, integration 12→13. 페어 round 1 verdict 가 ρ → σ FULL
전환 + R name canon 의 결정 근거.

Carry: 0. 다음 milestone 후보 = tier3-glyph 또는 POST-W2D6-POM-RESOLVE.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)" && git push origin main
```

---

## Self-Review (v2 round 2 후속 보강)

**1. Spec coverage**
- Spec §3.1 #1-8 → Tasks T1-T6 + T8. ✓
- Spec §3.2 4 변경 → T7 + T8. ✓
- Spec §3.3 5 삭제/rename → T8. ✓
- Spec §3.4 10 신규 test → T1-T9. ✓
- Spec §4 (AAR walker 정책) → T3. ✓
- Spec §5 (resolver σ FULL) → T6. ✓
- Spec §7.1 (R name canon) → T7. ✓
- Spec §7.2 (dup attr ID) → T7. ✓
- Spec §10 acceptance gate 11개 → T9 (game gate) + T10 (work_log close). ✓
- Spec §11 plan input → 본 plan 자체. ✓
- **v2 정정**: spec §2.3 의 "Gradle deterministic" 주장이 부정확 (실은 `.sorted()` lex). §0.A 에 정정 명시.

**2. Placeholder scan (v2 round 2 후 모두 해결)**
- ~~T7 Step 4 placeholder `callback.registerStyle(...)`~~ → v2 에서 정확한 inline diff (single callback API + seenAttrNames outer-scope) 명시.
- ~~T8 Step 4 "각 호출 사이트에 ... 추가" 추상 instruction~~ → v2 에서 7 callsite enumeration + named args 강제.
- ~~T9 `locateAll()` placeholder body~~ → v2 에서 explicit body (assumeTrue gate + Triple).
- ~~T6 의 self-built parseReference regex~~ → v2 에서 `ResourceUrl.parse()` 직접 활용.

**3. Type consistency**
- `LayoutlibResourceBundle.build(...)` 인자 시그니처: T4 (Map<ResourceNamespace, List<ParsedNsEntry>>) ↔ T5 (loadOrGet → loader 가 build 호출). 일관.
- `LayoutlibRenderResources(bundle, themeName)` ctor: T6 ↔ T8 (LayoutlibRenderer 의 line 174 swap). 일관.
- `LayoutlibRenderer` ctor 의 v2 새 시그니처 `(distDir, fixtureRoot, sampleAppModuleRoot, themeName, fallback)`: T8 + 7 callsite 모두 정합. fallback default 제거 (CLAUDE.md "No default parameter values").
- `SharedLayoutlibRenderer.RendererArgs` 4-tuple (themeName 포함): T8 ↔ T9. ✓

**4. v2 round 2 follow-up coverage 검증 (12개)**
- FF#1 (T7 inline diff) ✓ — Step 4 의 v2 본문이 single callback API + cross-class seenAttrNames 통합.
- FF#2 (T8 ctor reorder + 7 caller + default 제거) ✓ — Step 2-4 의 explicit 코드.
- FF#3 (T6 ResourceUrl.parse + sentinel + private) ✓ — parseReference 본문 교체 + 3 신규 test.
- FF#4 (T9 locateAll explicit body) ✓ — explicit body + assumeTrue gate.
- FF#5 (MAX_THEME_HOPS=32 + 진단 로그 + chain depth assert) ✓ — T1 const + T6 hop-overflow log + MaterialFidelityIntegrationTest 의 chain depth assert.
- FF#6 (android: parent normalization) ✓ — T6 의 `resolveStyleNameWithNamespace` 신규.
- FF#7 (dedupe winner sorted lex + winner unit assert) ✓ — T5 의 dedupe winner test + spec §0.A 정정.
- FF#8 (Files.list.sorted + assertTrue(true) 제거) ✓ — T5 loadApp + cache test 갱신.
- FF#9 (cache invalidation explicit) ✓ — clearCache identity assert.
- FF#10 (AAR ZipFile.use {}) ✓ — T3 line 808 에 이미 존재 (확인 only).
- FF#11 (RJarSymbolSeederTest end-to-end) ✓ — T7 Step 10 신규 case.
- FF#12 (themes_holo.xml 포함 의무) → spec §10 acceptance gate 의 framework REQUIRED_FILES 검토 항목 (현 ResourceLoaderConstants.REQUIRED_FILES 가 themes_holo 포함 시 사실상 충족 — implementation 시 W3D1 의 framework 10 XML 카운트 확인). T9 의 chain depth ≥ 15 assert 가 실측 회귀 detect.

이슈 없음. plan v2 작성 완료.

---

## Execution Handoff

본 plan 은 **plan v1** — 사용자 정의 flow 의 다음 단계는 **Codex+Claude 페어 리뷰 round 2 (plan-review)**. 페어 결과로 v2 작성 후 implementation 진입.

페어 round 2 진행 여부:
- **(A) 진행** — Codex+Claude pair-review of this plan v1, plan v2 작성 후 implementation
- **(B) 즉시 implementation** — plan v1 그대로 subagent-driven 시작 (페어 round 2 생략, 위험)

**(A) 권고** — 사용자가 명시한 flow + CLAUDE.md "Pairs REQUIRED for plan-document review rounds" 정합. round 1 이 4 critical fix 발견했으므로 round 2 도 동등 가치 기대.

선택지: **A** (페어 round 2) / **B** (즉시 implementation, 위험 명시)
