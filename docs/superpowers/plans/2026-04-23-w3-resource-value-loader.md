# W3-RESOURCE-VALUE-LOADER Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 프레임워크 resource VALUE loader 구현으로 `activity_minimal.xml` 의 tier3 렌더를 `SUCCESS` 까지 도달시키고 tier3-values gate 통과.

**Architecture:** 10 XML 파일을 `data/res/values/` 에서 kxml2 로 파싱, immutable `FrameworkResourceBundle` 로 집계, JVM-wide 캐시 1회 로드. `RenderResources` subclass `FrameworkRenderResources` 가 bundle delegate 로 layoutlib 에 값 제공. Style parent 는 Loader 에서 pre-populate (pure inference + bundle post-process).

**Tech Stack:** Kotlin 1.9 (targets `CMAKE_CXX_STANDARD` not applicable — 이건 JVM 모듈), kxml2 2.3.0 (이미 pinned), layoutlib-api 31.13.2, JUnit 5 Jupiter, Gradle 8.7.

**Non-git project note:** 본 저장소는 git init 이 되어 있지 않다 (`fatal: not a git repository` 확인됨). 따라서 각 Task 의 commit step 은 **생략**하고, 대신 각 Task 완료 후 `./server/gradlew -p server test` 가 green 임을 확인하고, 마지막에 session work_log 로 배치 journaling 한다. 프로젝트 CLAUDE.md `session-work-log-protocol` 준수.

---

## 사전 환경 검증 (본 플랜 실행 전 필수)

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
./server/gradlew -p server build                     # Expected: BUILD SUCCESSFUL
./server/gradlew -p server test                       # Expected: 65 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
                                                     # Expected: 5 PASS + 2 SKIPPED
```

모두 green 이어야 시작. 실패 시 W2D7 handoff §5 긴급 회복 참조.

---

## File Structure

**신규 파일** (모두 `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/`):

| 파일 | 책임 | 예상 LOC |
|---|---|---|
| `ResourceLoaderConstants.kt` | VALUES_DIR, FILE_CONFIG, FILE_COLORS 등 path/이름 상수 10종 | ~40 |
| `StyleParentInference.kt` | pure function: (styleName, explicitParent) → String? | ~60 |
| `ParsedEntry.kt` | sealed class: SimpleValue / StyleDef / AttrDef + StyleItem data class | ~40 |
| `FrameworkValueParser.kt` | KXmlParser 기반 XML → List<ParsedEntry> | ~250 |
| `FrameworkResourceBundle.kt` | immutable aggregate (byType/styles/attrs maps) + build 팩토리 | ~120 |
| `FrameworkResourceValueLoader.kt` | 10 XML 로드 + bundle 빌드 + ConcurrentHashMap 캐시 | ~100 |
| `FrameworkRenderResources.kt` | RenderResources subclass, bundle delegate | ~100 |

**신규 테스트** (`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/`):

| 파일 | 테스트 개수 | 예상 LOC |
|---|---|---|
| `StyleParentInferenceTest.kt` | 5 | ~80 |
| `FrameworkValueParserTest.kt` | 10 | ~250 |
| `FrameworkResourceBundleTest.kt` | 4 | ~100 |
| `FrameworkResourceValueLoaderTest.kt` | 6 | ~150 |
| `FrameworkRenderResourcesTest.kt` | 6 | ~160 |

**수정** (3 파일):
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt` — `resources` param default 를 `FrameworkRenderResources` 로 교체.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — renderViaLayoutlib 에서 `FrameworkResourceValueLoader.loadOrGet(distDir/"data")` 호출 후 SessionParamsFactory.build 에 주입.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt` — tier3-arch expected/rejected set flip + tier3-values @Disabled 제거 + tier3-glyph @Disabled 신설.

**삭제** (1 파일):
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalFrameworkRenderResources.kt` — `FrameworkRenderResources` 로 대체.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalFrameworkRenderResourcesTest.kt` — 존재하면 삭제 (현재 없음).

---

## Task 1: ResourceLoaderConstants + 서브패키지 부트스트랩

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ResourceLoaderConstants.kt`

- [ ] **Step 1: Create constants file**

```kotlin
package dev.axp.layoutlib.worker.resources

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2 3b-values): data/res/values 경로 및 파일명 상수.
 *
 * CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" 정책 준수 — 10 XML 파일명 전부 명시.
 */
object ResourceLoaderConstants {
    /** data 디렉토리 하위 values 폴더 이름 (qualifier 없는 base). */
    const val VALUES_DIR = "values"

    // 파싱 대상 10 XML. 순서는 parent chain safety 를 위해 base → material 순.
    const val FILE_CONFIG = "config.xml"
    const val FILE_COLORS = "colors.xml"
    const val FILE_DIMENS = "dimens.xml"
    const val FILE_THEMES = "themes.xml"
    const val FILE_STYLES = "styles.xml"
    const val FILE_ATTRS = "attrs.xml"
    const val FILE_COLORS_MATERIAL = "colors_material.xml"
    const val FILE_DIMENS_MATERIAL = "dimens_material.xml"
    const val FILE_THEMES_MATERIAL = "themes_material.xml"
    const val FILE_STYLES_MATERIAL = "styles_material.xml"

    /** loader 가 반드시 로드할 파일의 목록 — Loader 내부 iteration 순서. */
    val REQUIRED_FILES: List<String> = listOf(
        FILE_CONFIG, FILE_COLORS, FILE_DIMENS,
        FILE_THEMES, FILE_STYLES, FILE_ATTRS,
        FILE_COLORS_MATERIAL, FILE_DIMENS_MATERIAL,
        FILE_THEMES_MATERIAL, FILE_STYLES_MATERIAL,
    )

    /** required files 개수 (테스트 assertion 에서 1 표준). */
    const val REQUIRED_FILE_COUNT = 10
}
```

- [ ] **Step 2: Verify compile**

Run: `./server/gradlew -p server :layoutlib-worker:compileKotlin`
Expected: BUILD SUCCESSFUL (새 object 는 어디서도 참조되지 않아 unused warning 가능 — 허용).

---

## Task 2: StyleParentInference (TDD R1)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInference.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package dev.axp.layoutlib.worker.resources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StyleParentInferenceTest {

    @Test
    fun `explicit parent 이름 그대로 반환`() {
        assertEquals(
            "Theme.Material.Light",
            StyleParentInference.infer("Theme.Material.Light.NoActionBar", explicitParent = "Theme.Material.Light")
        )
    }

    @Test
    fun `explicit parent ref 형식 정규화 - at android style prefix 제거`() {
        assertEquals(
            "Theme.Material",
            StyleParentInference.infer("Theme.Material.Light", explicitParent = "@android:style/Theme.Material")
        )
    }

    @Test
    fun `explicit parent ref 형식 정규화 - at style prefix 제거`() {
        assertEquals(
            "Widget.Material",
            StyleParentInference.infer("Widget.Material.Button", explicitParent = "@style/Widget.Material")
        )
    }

    @Test
    fun `explicit null 또는 empty 면 dotted-prefix 상속`() {
        assertEquals("Theme.Material.Light", StyleParentInference.infer("Theme.Material.Light.NoActionBar", null))
        assertEquals("Theme.Material", StyleParentInference.infer("Theme.Material.Light", ""))
    }

    @Test
    fun `점 없는 이름은 루트 - null 반환`() {
        assertNull(StyleParentInference.infer("Theme", explicitParent = null))
        assertNull(StyleParentInference.infer("Theme", explicitParent = ""))
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.StyleParentInferenceTest"`
Expected: COMPILATION FAIL (StyleParentInference 없음).

- [ ] **Step 3: Create minimal implementation**

```kotlin
package dev.axp.layoutlib.worker.resources

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): style 의 parent name 을 결정.
 *
 * pure function — bundle 존재 여부는 확인하지 않는다 (Loader post-process 가 담당).
 *
 * 규칙:
 *  1. explicitParent 가 non-null, non-empty → ref form 정규화 (`@style/` / `@android:style/` 제거) 후 반환.
 *  2. explicitParent 가 null 또는 빈 문자열 → styleName 의 마지막 dot 앞 부분 반환 (dotted-prefix 상속).
 *  3. styleName 에 dot 이 없음 (루트) → null.
 */
object StyleParentInference {

    fun infer(styleName: String, explicitParent: String?): String? {
        if (!explicitParent.isNullOrEmpty()) {
            return normalizeRefForm(explicitParent)
        }
        val lastDot = styleName.lastIndexOf('.')
        return if (lastDot > 0) styleName.substring(0, lastDot) else null
    }

    /**
     * `@android:style/Theme.Material`  → `Theme.Material`
     * `@style/Widget.Material`          → `Widget.Material`
     * `Theme.Material`                  → `Theme.Material`  (이미 이름 형태)
     */
    private fun normalizeRefForm(raw: String): String {
        var s = raw
        if (s.startsWith(PREFIX_AT_ANDROID_STYLE)) s = s.removePrefix(PREFIX_AT_ANDROID_STYLE)
        else if (s.startsWith(PREFIX_AT_STYLE)) s = s.removePrefix(PREFIX_AT_STYLE)
        return s
    }

    private const val PREFIX_AT_ANDROID_STYLE = "@android:style/"
    private const val PREFIX_AT_STYLE = "@style/"
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.StyleParentInferenceTest"`
Expected: 5 PASS.

- [ ] **Step 5: Verify total suite regression**

Run: `./server/gradlew -p server test`
Expected: 70 unit PASS (65 기존 + 5 신규).

---

## Task 3: ParsedEntry sealed class

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedEntry.kt`

이 파일에는 테스트가 없다 — 단순 data class 집합. 다음 Task (parser) 가 즉시 사용하고 검증한다.

- [ ] **Step 1: Create file**

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.resources.ResourceType

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): 단일 XML 파싱 시 얻어지는 엔트리.
 * sealed class — 각 resource 유형별 최소 필드.
 *
 * parser 는 refs 를 해석하지 않는다 (value 는 문자열 그대로). ref 해석은 layoutlib 의 책임.
 */
sealed class ParsedEntry {

    abstract val name: String

    /**
     * `<dimen name="X">4dp</dimen>`, `<color name="X">#fff</color>`, `<integer>`, `<bool>`,
     * `<string>`, `<item type="X" name="Y">Z</item>` 단일-값 엔트리.
     */
    data class SimpleValue(
        val type: ResourceType,
        override val name: String,
        val value: String,
    ) : ParsedEntry()

    /**
     * `<style name="X" parent="Y"> <item name="A">B</item> ... </style>`
     *
     * parent 는 파서가 가공하지 않은 원본 문자열 (null 또는 empty 가능). items 는 선언 순서 유지.
     */
    data class StyleDef(
        override val name: String,
        val parent: String?,
        val items: List<StyleItem>,
    ) : ParsedEntry()

    /**
     * `<attr name="X" format="Y" />` 선언. top-level 또는 `<declare-styleable>` 자식 모두 수집.
     *
     * **중요** (W3D1 pair-review F1): 실 `data/res/values/attrs.xml` 에는 top-level `<attr>` 이
     * 하나도 없고 모두 `<declare-styleable>` 자식으로 존재. 따라서 parser 는 양쪽 경로 모두에서
     * AttrDef 를 생성해야 한다. 동일 name 이 여러 번 등장하면 **first-wins** (첫 선언 유지).
     * format 이 없는 ref-only 선언 (`<attr name="id" />`) 은 format=null 로 수집.
     */
    data class AttrDef(
        override val name: String,
        val format: String?,
    ) : ParsedEntry()
}

/** `<style>` 내부의 단일 `<item name="...">value</item>`. */
data class StyleItem(
    val name: String,
    val value: String,
)
```

- [ ] **Step 2: Verify compile**

Run: `./server/gradlew -p server :layoutlib-worker:compileKotlin`
Expected: BUILD SUCCESSFUL.

---

## Task 4: FrameworkValueParser (TDD R2)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameworkValueParserTest {

    private fun parse(xml: String): List<ParsedEntry> =
        FrameworkValueParser.parse("inline-test.xml", xml.byteInputStream(Charsets.UTF_8))

    @Test
    fun `dimen - name 과 value 추출`() {
        val xml = """
            <resources>
              <dimen name="config_scrollbarSize">4dp</dimen>
            </resources>
        """.trimIndent()
        val entries = parse(xml)
        val d = entries.single() as ParsedEntry.SimpleValue
        assertEquals(ResourceType.DIMEN, d.type)
        assertEquals("config_scrollbarSize", d.name)
        assertEquals("4dp", d.value)
    }

    @Test
    fun `color 엔트리`() {
        val xml = """
            <resources>
              <color name="material_blue_grey_800">#ff37474f</color>
            </resources>
        """.trimIndent()
        val c = parse(xml).single() as ParsedEntry.SimpleValue
        assertEquals(ResourceType.COLOR, c.type)
        assertEquals("#ff37474f", c.value)
    }

    @Test
    fun `integer bool string 엔트리`() {
        val xml = """
            <resources>
              <integer name="config_maxRetries">3</integer>
              <bool name="config_allowTheming">true</bool>
              <string name="emptyString"></string>
            </resources>
        """.trimIndent()
        val list = parse(xml).filterIsInstance<ParsedEntry.SimpleValue>()
        assertEquals(3, list.size)
        assertEquals(ResourceType.INTEGER, list[0].type)
        assertEquals(ResourceType.BOOL, list[1].type)
        assertEquals(ResourceType.STRING, list[2].type)
        assertEquals("", list[2].value)
    }

    @Test
    fun `item with type attribute - explicit type 사용`() {
        val xml = """
            <resources>
              <item type="dimen" name="fraction_half" format="float">0.5</item>
            </resources>
        """.trimIndent()
        val e = parse(xml).single() as ParsedEntry.SimpleValue
        assertEquals(ResourceType.DIMEN, e.type)
        assertEquals("fraction_half", e.name)
        assertEquals("0.5", e.value)
    }

    @Test
    fun `style - parent explicit + items 순서 유지`() {
        val xml = """
            <resources>
              <style name="Theme.Material.Light.NoActionBar" parent="Theme.Material.Light">
                <item name="windowActionBar">false</item>
                <item name="windowNoTitle">true</item>
              </style>
            </resources>
        """.trimIndent()
        val s = parse(xml).single() as ParsedEntry.StyleDef
        assertEquals("Theme.Material.Light.NoActionBar", s.name)
        assertEquals("Theme.Material.Light", s.parent)
        assertEquals(2, s.items.size)
        assertEquals("windowActionBar", s.items[0].name)
        assertEquals("false", s.items[0].value)
        assertEquals("windowNoTitle", s.items[1].name)
    }

    @Test
    fun `style - parent 누락 허용 null 보존`() {
        val xml = """
            <resources>
              <style name="Theme">
                <item name="colorPrimary">#fff</item>
              </style>
            </resources>
        """.trimIndent()
        val s = parse(xml).single() as ParsedEntry.StyleDef
        assertNull(s.parent)
    }

    @Test
    fun `attr - top level 수집`() {
        val xml = """
            <resources>
              <attr name="isLightTheme" format="boolean" />
              <attr name="colorForeground" format="color" />
            </resources>
        """.trimIndent()
        val attrs = parse(xml).filterIsInstance<ParsedEntry.AttrDef>()
        assertEquals(2, attrs.size)
        assertEquals("isLightTheme", attrs[0].name)
        assertEquals("boolean", attrs[0].format)
    }

    @Test
    fun `declare-styleable 내부 attr 도 AttrDef 로 수집 - first-wins dedupe`() {
        // 실 attrs.xml 은 top-level <attr> 이 0 개. 모두 declare-styleable 자식.
        // 동일 name 이 top-level + nested 로 중복 선언되면 먼저 등장한 쪽 유지.
        val xml = """
            <resources>
              <attr name="colorPrimary" format="color" />
              <declare-styleable name="View">
                <attr name="colorPrimary" />
                <attr name="id" format="reference" />
                <attr name="background" />
              </declare-styleable>
            </resources>
        """.trimIndent()
        val attrs = parse(xml).filterIsInstance<ParsedEntry.AttrDef>()
        val byName = attrs.associateBy { it.name }
        assertEquals(3, byName.size, "colorPrimary(top) + id + background (nested) = 3 고유 attr")
        assertEquals("color", byName.getValue("colorPrimary").format, "first-wins: top-level 의 format=color 유지")
        assertEquals("reference", byName.getValue("id").format)
        assertNull(byName.getValue("background").format, "format 없는 nested <attr> 는 format=null")
    }

    @Test
    fun `attrs xml 실 케이스 - top-level attr 0 개 + declare-styleable nested 만 있을 때 모두 수집`() {
        // 실 프레임워크 attrs.xml 재현: top-level <attr> 없음.
        val xml = """
            <resources>
              <declare-styleable name="Theme">
                <attr name="colorPrimary" format="color" />
                <attr name="isLightTheme" format="boolean" />
              </declare-styleable>
              <declare-styleable name="View">
                <attr name="id" format="reference" />
                <attr name="colorPrimary" />
              </declare-styleable>
            </resources>
        """.trimIndent()
        val attrs = parse(xml).filterIsInstance<ParsedEntry.AttrDef>()
        val byName = attrs.associateBy { it.name }
        assertEquals(3, byName.size)
        assertEquals("color", byName.getValue("colorPrimary").format, "Theme 의 colorPrimary 먼저 — first-wins")
        assertEquals("boolean", byName.getValue("isLightTheme").format)
        assertEquals("reference", byName.getValue("id").format)
    }

    @Test
    fun `unknown tag 는 skip 하고 warning`() {
        val xml = """
            <resources>
              <public type="dimen" name="config_scrollbarSize" id="0x10500dd" />
              <dimen name="config_scrollbarSize">4dp</dimen>
            </resources>
        """.trimIndent()
        val entries = parse(xml)
        assertEquals(1, entries.size)
        assertTrue(entries.single() is ParsedEntry.SimpleValue)
    }

    @Test
    fun `malformed XML 은 IllegalStateException 에 파일명 포함`() {
        val xml = "<resources><dimen name=broken></resources>"
        val ex = assertThrows(IllegalStateException::class.java) { parse(xml) }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("inline-test.xml"), "파일명이 에러 메시지에 포함: ${ex.message}")
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.FrameworkValueParserTest"`
Expected: COMPILATION FAIL.

- [ ] **Step 3: Create parser implementation**

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.resources.ResourceType
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): 단일 `values/*.xml` → `List<ParsedEntry>`.
 *
 * kxml2 기반 (Bridge 내부와 동일 parser) — 스타일 텍스트 정규화가 일관.
 * parser 는 refs (`@android:...`, `?attr:...`) 를 해석하지 않는다 — string 그대로 보관.
 *
 * 지원 tag: dimen / color / integer / bool / string / item / style / attr / declare-styleable.
 * skip (forward-compat): public, eat-comment, 기타 unknown.
 *
 * **attr 수집 규칙** (W3D1 pair-review F1): top-level `<attr>` 와 `<declare-styleable>` 내부의
 * `<attr>` 모두 `ParsedEntry.AttrDef` 로 수집. 실 `attrs.xml` 은 top-level 이 0 개이고 모두
 * declare-styleable 자식이므로 이 로직이 필수. 동일 name 이 복수 등장하면 **first-wins**
 * — 파서가 dedupe 하지 않고 모두 emit, Loader 단계에서 순서 기반 dedupe.
 */
object FrameworkValueParser {

    fun parse(path: Path): List<ParsedEntry> =
        Files.newInputStream(path).use { parse(path.fileName.toString(), it) }

    fun parse(fileLabel: String, input: InputStream): List<ParsedEntry> {
        val parser = KXmlParser()
        try {
            parser.setInput(input, null)
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)

            val out = mutableListOf<ParsedEntry>()

            // <resources> 까지 skip.
            var event = parser.next()
            while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
                event = parser.next()
            }
            if (event == XmlPullParser.END_DOCUMENT) return out
            if (parser.name != TAG_RESOURCES) {
                error("root element 는 <$TAG_RESOURCES> 여야 함: ${parser.name}")
            }

            // <resources> 의 direct children 순회.
            while (true) {
                event = parser.next()
                when (event) {
                    XmlPullParser.END_DOCUMENT -> break
                    XmlPullParser.END_TAG -> if (parser.name == TAG_RESOURCES) break
                    XmlPullParser.START_TAG -> handleTopLevelTag(parser, out)
                    else -> { /* text / CDATA 등 무시 */ }
                }
            }
            return out
        } catch (e: XmlPullParserException) {
            throw IllegalStateException("XML 파싱 실패 [$fileLabel]: ${e.message}", e)
        } catch (e: Throwable) {
            if (e is IllegalStateException && e.message?.contains(fileLabel) == true) throw e
            throw IllegalStateException("XML 파싱 실패 [$fileLabel]: ${e.message}", e)
        }
    }

    private fun handleTopLevelTag(p: KXmlParser, out: MutableList<ParsedEntry>) {
        when (val tag = p.name) {
            TAG_DIMEN -> out += simpleValue(p, ResourceType.DIMEN)
            TAG_COLOR -> out += simpleValue(p, ResourceType.COLOR)
            TAG_INTEGER -> out += simpleValue(p, ResourceType.INTEGER)
            TAG_BOOL -> out += simpleValue(p, ResourceType.BOOL)
            TAG_STRING -> out += simpleValue(p, ResourceType.STRING)
            TAG_ITEM -> {
                // top-level <item type="X" name="Y"> — type attr 사용.
                val typeAttr = p.getAttributeValue(null, ATTR_TYPE)
                val resType = typeAttr?.let { ResourceType.fromXmlValue(it) }
                val name = p.getAttributeValue(null, ATTR_NAME) ?: ""
                val value = readText(p)
                if (resType != null && name.isNotEmpty()) {
                    out += ParsedEntry.SimpleValue(resType, name, value)
                }
            }
            TAG_STYLE -> out += parseStyle(p)
            TAG_ATTR -> collectAttr(p, out)
            TAG_DECLARE_STYLEABLE -> parseDeclareStyleable(p, out)
            TAG_PUBLIC, TAG_EAT_COMMENT -> {
                skipTag(p)
            }
            else -> {
                System.err.println("[FrameworkValueParser] unknown top-level tag skipped: <$tag>")
                skipTag(p)
            }
        }
    }

    /**
     * 현재 `<attr>` START_TAG 에서 name/format 추출 후 AttrDef 를 out 에 추가. 그다음 tag 끝까지 소비.
     * top-level 과 nested (declare-styleable 자식) 모두에서 공유.
     */
    private fun collectAttr(p: KXmlParser, out: MutableList<ParsedEntry>) {
        val name = p.getAttributeValue(null, ATTR_NAME)
        val format = p.getAttributeValue(null, ATTR_FORMAT)
        if (!name.isNullOrEmpty()) {
            out += ParsedEntry.AttrDef(name, format)
        }
        skipTag(p)
    }

    /**
     * `<declare-styleable>` 자식 `<attr>` 를 모두 수집. styleable 자체는 resource 로 취급하지 않음.
     * W3D1 pair-review F1: 실 attrs.xml 은 top-level <attr> 이 0 개이고 모든 attr 이 여기서 수집됨.
     */
    private fun parseDeclareStyleable(p: KXmlParser, out: MutableList<ParsedEntry>) {
        while (true) {
            val event = p.next()
            when (event) {
                XmlPullParser.END_DOCUMENT -> return
                XmlPullParser.END_TAG -> if (p.name == TAG_DECLARE_STYLEABLE) return
                XmlPullParser.START_TAG -> if (p.name == TAG_ATTR) {
                    collectAttr(p, out)
                } else {
                    skipTag(p)
                }
                else -> { /* text / comment */ }
            }
        }
    }

    private fun simpleValue(p: KXmlParser, type: ResourceType): ParsedEntry.SimpleValue {
        val name = p.getAttributeValue(null, ATTR_NAME) ?: ""
        val value = readText(p)
        return ParsedEntry.SimpleValue(type, name, value)
    }

    private fun parseStyle(p: KXmlParser): ParsedEntry.StyleDef {
        val name = p.getAttributeValue(null, ATTR_NAME) ?: ""
        val parentAttr = p.getAttributeValue(null, ATTR_PARENT)
        val items = mutableListOf<StyleItem>()

        while (true) {
            val event = p.next()
            when (event) {
                XmlPullParser.END_DOCUMENT -> return ParsedEntry.StyleDef(name, parentAttr, items.toList())
                XmlPullParser.END_TAG -> if (p.name == TAG_STYLE) {
                    return ParsedEntry.StyleDef(name, parentAttr, items.toList())
                }
                XmlPullParser.START_TAG -> if (p.name == TAG_ITEM) {
                    val itemName = p.getAttributeValue(null, ATTR_NAME) ?: ""
                    val itemValue = readText(p)
                    if (itemName.isNotEmpty()) items += StyleItem(itemName, itemValue)
                } else {
                    skipTag(p)
                }
                else -> { /* text / comment */ }
            }
        }
    }

    /**
     * 현재 START_TAG → 매칭되는 END_TAG 까지 이벤트 skip. nested tag 포함.
     */
    private fun skipTag(p: KXmlParser) {
        if (p.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /**
     * 현재 START_TAG 의 text 내용을 읽고 END_TAG 까지 진행.
     * 중첩 tag 있으면 (e.g. `<xliff:g>`) text 누적, 자식 tag 는 text 로만 취급.
     */
    private fun readText(p: KXmlParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (val event = p.next()) {
                XmlPullParser.TEXT -> sb.append(p.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
                XmlPullParser.CDSECT -> sb.append(p.text)
                else -> { /* ignore */ }
            }
        }
        return sb.toString()
    }

    // 태그/속성 이름 상수 — CLAUDE.md zero-magic-strings.
    private const val TAG_RESOURCES = "resources"
    private const val TAG_DIMEN = "dimen"
    private const val TAG_COLOR = "color"
    private const val TAG_INTEGER = "integer"
    private const val TAG_BOOL = "bool"
    private const val TAG_STRING = "string"
    private const val TAG_ITEM = "item"
    private const val TAG_STYLE = "style"
    private const val TAG_ATTR = "attr"
    private const val TAG_DECLARE_STYLEABLE = "declare-styleable"
    private const val TAG_PUBLIC = "public"
    private const val TAG_EAT_COMMENT = "eat-comment"

    private const val ATTR_NAME = "name"
    private const val ATTR_PARENT = "parent"
    private const val ATTR_TYPE = "type"
    private const val ATTR_FORMAT = "format"
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.FrameworkValueParserTest"`
Expected: 10 PASS. 만약 `unknown top-level tag skipped: <string-array>` 등 warning 이 stdout 에 뜨면 정상.

- [ ] **Step 5: Run parser against real config.xml smoke**

Run:
```bash
./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.FrameworkValueParserTest" --info 2>&1 | grep -E 'unknown top-level|PASS|FAIL' | head -20
```
Expected: 신규 10 test PASS. (실 XML 검증은 Task 6 에서 수행.)

- [ ] **Step 6: Verify regression**

Run: `./server/gradlew -p server test`
Expected: 80 unit PASS (65 + 5 + 10).

---

## Task 5: FrameworkResourceBundle (TDD R3a)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FrameworkResourceBundleTest {

    @Test
    fun `build - SimpleValue entries 가 type 별 map 으로 집계`() {
        val entries = listOf(
            ParsedEntry.SimpleValue(ResourceType.DIMEN, "config_scrollbarSize", "4dp"),
            ParsedEntry.SimpleValue(ResourceType.COLOR, "material_blue_grey_800", "#ff37474f"),
            ParsedEntry.SimpleValue(ResourceType.DIMEN, "action_bar_size", "56dp"),
        )
        val bundle = FrameworkResourceBundle.build(entries)

        val d = bundle.getResource(ResourceType.DIMEN, "config_scrollbarSize")
        assertNotNull(d)
        assertEquals("4dp", d!!.value)

        val c = bundle.getResource(ResourceType.COLOR, "material_blue_grey_800")
        assertEquals("#ff37474f", c!!.value)

        assertNull(bundle.getResource(ResourceType.DIMEN, "does_not_exist"))
    }

    @Test
    fun `build - StyleDef 의 parent 가 존재하는 이름이면 유지 존재 안 하면 null`() {
        val entries = listOf(
            ParsedEntry.StyleDef("Theme", parent = null, items = emptyList()),
            ParsedEntry.StyleDef("Theme.Material.Light.NoActionBar", parent = "Theme.Material.Light",
                items = listOf(StyleItem("windowActionBar", "false"))),
            ParsedEntry.StyleDef("Theme.Material.Light", parent = null,
                items = emptyList()),  // 점 포함하지만 explicit parent 없음 → inference 로 Theme.Material 추정되나 존재 X
        )
        val bundle = FrameworkResourceBundle.build(entries)

        val root = bundle.getStyle("Theme")
        assertNotNull(root); assertNull(root!!.parentStyleName)

        val noActionBar = bundle.getStyle("Theme.Material.Light.NoActionBar")
        assertEquals("Theme.Material.Light", noActionBar!!.parentStyleName)

        val light = bundle.getStyle("Theme.Material.Light")
        // inference → "Theme.Material" → bundle 내 X → null (post-process).
        assertNull(light!!.parentStyleName)
    }

    @Test
    fun `build - AttrDef 는 attrs map 에 집계`() {
        val entries = listOf(
            ParsedEntry.AttrDef("isLightTheme", "boolean"),
            ParsedEntry.AttrDef("colorForeground", "color"),
        )
        val bundle = FrameworkResourceBundle.build(entries)
        val a = bundle.getAttr("isLightTheme")
        assertNotNull(a)
        assertEquals("isLightTheme", a!!.name)
    }

    @Test
    fun `build - SimpleValue 중복 이름은 later-wins`() {
        val entries = listOf(
            ParsedEntry.SimpleValue(ResourceType.DIMEN, "x", "1dp"),
            ParsedEntry.SimpleValue(ResourceType.DIMEN, "x", "2dp"),
        )
        val bundle = FrameworkResourceBundle.build(entries)
        assertEquals("2dp", bundle.getResource(ResourceType.DIMEN, "x")!!.value)
    }

    @Test
    fun `build - AttrDef 중복 이름은 first-wins (F1)`() {
        // 실 attrs.xml 시나리오: top-level attr 이 0 개, declare-styleable 에서 같은 name 이
        // 여러 styleable 에 재등장. first-wins 로 처음 format 이 유지되어야 한다.
        val entries = listOf(
            ParsedEntry.AttrDef("colorPrimary", "color"),
            ParsedEntry.AttrDef("colorPrimary", null),      // 무시되어야 함
            ParsedEntry.AttrDef("colorPrimary", "reference"), // 무시되어야 함
        )
        val bundle = FrameworkResourceBundle.build(entries)
        assertEquals(1, bundle.attrCount(), "동일 name 은 1 개만 집계")
        assertNotNull(bundle.getAttr("colorPrimary"))
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.FrameworkResourceBundleTest"`
Expected: COMPILATION FAIL.

- [ ] **Step 3: Create bundle**

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.AttrResourceValue
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
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): ParsedEntry 집합을 layoutlib-api 의 ResourceValue 로
 * 변환하여 집계한 immutable 번들.
 *
 * scope: framework (ResourceNamespace.ANDROID) 만. library/app 은 별도.
 */
class FrameworkResourceBundle private constructor(
    private val byType: Map<ResourceType, Map<String, ResourceValue>>,
    private val styles: Map<String, StyleResourceValue>,
    private val attrs: Map<String, AttrResourceValue>,
) {

    fun getResource(type: ResourceType, name: String): ResourceValue? =
        byType[type]?.get(name)

    fun getStyle(name: String): StyleResourceValue? = styles[name]

    fun getAttr(name: String): AttrResourceValue? = attrs[name]

    /** 진단용 — 테스트에서만 사용 권장. */
    fun typeCount(type: ResourceType): Int = byType[type]?.size ?: 0
    fun styleCount(): Int = styles.size
    fun attrCount(): Int = attrs.size

    companion object {
        /**
         * @param entries 모든 파일의 ParsedEntry 를 평탄화하여 전달.
         *
         * Dedupe 정책:
         *   - SimpleValue / StyleDef: **later-wins** — 같은 이름 중복 시 마지막 entry 가 승리
         *     (themes_material 이 themes 를 오버라이드하는 실 Android 동작).
         *   - AttrDef: **first-wins** (W3D1 pair-review F1) — top-level `<attr>` 가 declare-styleable
         *     내부 nested 선언보다 우선하도록. 실 attrs.xml 은 top-level 이 0 개이므로 실질적으로는
         *     nested 등장 순서대로 고정됨.
         *
         * Style parent post-process: StyleDef 의 explicit parent 또는 inference 결과가
         * 집합 내에 존재하지 않으면 `parentStyleName` 을 null 로 세팅 (chain 끊김).
         */
        fun build(entries: List<ParsedEntry>): FrameworkResourceBundle {
            val byTypeMut = mutableMapOf<ResourceType, MutableMap<String, ResourceValue>>()
            val stylesMut = mutableMapOf<String, StyleResourceValue>()
            val attrsMut = mutableMapOf<String, AttrResourceValue>()

            val styleDefs = mutableListOf<ParsedEntry.StyleDef>()

            for (e in entries) when (e) {
                is ParsedEntry.SimpleValue -> {
                    val ref = ResourceReference(ResourceNamespace.ANDROID, e.type, e.name)
                    val rv = ResourceValueImpl(ref, e.value, null)
                    byTypeMut.getOrPut(e.type) { mutableMapOf() }[e.name] = rv
                }
                is ParsedEntry.AttrDef -> {
                    // first-wins: 이미 등록된 name 은 덮어쓰지 않는다.
                    if (!attrsMut.containsKey(e.name)) {
                        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, e.name)
                        attrsMut[e.name] = AttrResourceValueImpl(ref, null)
                    }
                }
                is ParsedEntry.StyleDef -> styleDefs += e
            }

            // Style pass: inference 로 parent 이름 결정 → 존재 여부 판단.
            val allStyleNames: Set<String> = styleDefs.mapTo(HashSet()) { it.name }
            for (def in styleDefs) {
                val candidate = StyleParentInference.infer(def.name, def.parent)
                val parentName = if (candidate != null && candidate in allStyleNames) candidate
                                 else null
                val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, def.name)
                val sv = StyleResourceValueImpl(ref, parentName, null)
                for (it2 in def.items) {
                    val itemRef = ResourceReference(
                        ResourceNamespace.ANDROID, ResourceType.ATTR, it2.name
                    )
                    sv.addItem(StyleItemResourceValueImpl(ResourceNamespace.ANDROID, it2.name, it2.value, null))
                }
                stylesMut[def.name] = sv
            }

            return FrameworkResourceBundle(byTypeMut, stylesMut, attrsMut)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.FrameworkResourceBundleTest"`
Expected: 4 PASS.

- [ ] **Step 5: Regression**

Run: `./server/gradlew -p server test`
Expected: 84 unit PASS (80 + 4).

---

## Task 6: FrameworkResourceValueLoader (TDD R3b)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoader.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.resources.ResourceType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class FrameworkResourceValueLoaderTest {

    @BeforeEach
    fun setUp() {
        FrameworkResourceValueLoader.clearCache()
    }

    @AfterEach
    fun tearDown() {
        FrameworkResourceValueLoader.clearCache()
    }

    @Test
    fun `loadOrGet - 10 파일 모두 존재하면 bundle 생성`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val bundle = FrameworkResourceValueLoader.loadOrGet(data)

        // config.xml 에 config_scrollbarSize 넣었음.
        val d = bundle.getResource(ResourceType.DIMEN, "config_scrollbarSize")
        assertNotNull(d)
        assertEquals("4dp", d!!.value)
    }

    @Test
    fun `loadOrGet - 파일 하나라도 누락 시 IllegalStateException`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        // 하나 지우기.
        Files.delete(data.resolve(ResourceLoaderConstants.VALUES_DIR).resolve(ResourceLoaderConstants.FILE_ATTRS))
        val ex = assertThrows(IllegalStateException::class.java) {
            FrameworkResourceValueLoader.loadOrGet(data)
        }
        assertTrue(ex.message?.contains(ResourceLoaderConstants.FILE_ATTRS) == true)
    }

    @Test
    fun `loadOrGet - 동일 dataDir 재호출 시 cache 반환 동일 인스턴스`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val a = FrameworkResourceValueLoader.loadOrGet(data)
        val b = FrameworkResourceValueLoader.loadOrGet(data)
        assertSame(a, b)
    }

    @Test
    fun `clearCache 후 재로드는 새 instance`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val a = FrameworkResourceValueLoader.loadOrGet(data)
        FrameworkResourceValueLoader.clearCache()
        val b = FrameworkResourceValueLoader.loadOrGet(data)
        assertTrue(a !== b, "clearCache 후에는 재로드 인스턴스가 새 객체")
    }

    @Test
    fun `loadOrGet - REQUIRED_FILES 개수 = 10`() {
        assertEquals(ResourceLoaderConstants.REQUIRED_FILE_COUNT, ResourceLoaderConstants.REQUIRED_FILES.size)
    }

    @Test
    fun `loadOrGet - 스타일 parent chain 이 bundle 에 존재`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val bundle = FrameworkResourceValueLoader.loadOrGet(data)

        val noActionBar = bundle.getStyle("Theme.Material.Light.NoActionBar")
        assertNotNull(noActionBar)
        assertEquals("Theme.Material.Light", noActionBar!!.parentStyleName)

        // F2: themes_material 의 Theme.Material.Light 가 themes.xml 의 Theme.Light 를 parent 로 resolve.
        val light = bundle.getStyle("Theme.Material.Light")
        assertNotNull(light)
        assertEquals("Theme.Light", light!!.parentStyleName,
            "F2 regression guard: 실 frameworks 는 Theme.Material.Light parent=Theme.Light")

        // Theme.Light 체인 정점은 Theme (inference).
        val themeLight = bundle.getStyle("Theme.Light")
        assertNotNull(themeLight)
        assertEquals("Theme", themeLight!!.parentStyleName)
    }

    @Test
    fun `loadOrGet - attrs xml declare-styleable 내부 attr 이 bundle 에 수집됨 (F1)`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val bundle = FrameworkResourceValueLoader.loadOrGet(data)

        // Theme 의 colorPrimary, isLightTheme + View 의 id → first-wins 로 3 개.
        assertNotNull(bundle.getAttr("colorPrimary"),
            "F1 regression guard: declare-styleable 내부 attr 이 bundle 에 수집되어야 함")
        assertNotNull(bundle.getAttr("isLightTheme"))
        assertNotNull(bundle.getAttr("id"))
    }

    // ----- helpers -----

    /**
     * 실 data/res/values 대체로 minimal 한 10 파일을 tmp 에 생성.
     *
     * **W3D1 pair-review F2 반영**: themes/themes_material 의 parent chain 은 실 frameworks 를
     * 반영 — `Theme.Material.Light parent="Theme.Light"` 이고 `Theme.Light` 는 `themes.xml` 에
     * 존재. fake 데이터가 실 구조를 흉내내지 않으면 "unit green / integration red" 함정이 생김.
     *
     * **F1 반영**: attrs.xml 은 top-level `<attr>` 이 0 개이고 declare-styleable 내부에서만
     * 등장하는 실 구조를 흉내. parser 가 nested attr 을 수집하지 못하면 이 fake 에서도 이미 실패.
     */
    private fun createFakeDataDir(tmp: Path, allFiles: Boolean): Path {
        val valuesDir = tmp.resolve("data").resolve(ResourceLoaderConstants.VALUES_DIR)
        Files.createDirectories(valuesDir)

        valuesDir.resolve(ResourceLoaderConstants.FILE_CONFIG).writeText("""
            <resources>
              <dimen name="config_scrollbarSize">4dp</dimen>
            </resources>
        """.trimIndent())

        valuesDir.resolve(ResourceLoaderConstants.FILE_COLORS).writeText("<resources/>")
        valuesDir.resolve(ResourceLoaderConstants.FILE_DIMENS).writeText("<resources/>")

        // 실 themes.xml 구조: Theme (root) + Theme.Light (parent implicit "Theme").
        valuesDir.resolve(ResourceLoaderConstants.FILE_THEMES).writeText("""
            <resources>
              <style name="Theme"><item name="colorPrimary">#fff</item></style>
              <style name="Theme.Light"><item name="colorPrimary">#eee</item></style>
            </resources>
        """.trimIndent())

        valuesDir.resolve(ResourceLoaderConstants.FILE_STYLES).writeText("<resources/>")

        // F1: top-level <attr> 없음 + declare-styleable 내부에서만 선언.
        valuesDir.resolve(ResourceLoaderConstants.FILE_ATTRS).writeText("""
            <resources>
              <declare-styleable name="Theme">
                <attr name="colorPrimary" format="color" />
                <attr name="isLightTheme" format="boolean" />
              </declare-styleable>
              <declare-styleable name="View">
                <attr name="id" format="reference" />
                <attr name="colorPrimary" />
              </declare-styleable>
            </resources>
        """.trimIndent())

        valuesDir.resolve(ResourceLoaderConstants.FILE_COLORS_MATERIAL).writeText("<resources/>")
        valuesDir.resolve(ResourceLoaderConstants.FILE_DIMENS_MATERIAL).writeText("<resources/>")

        // 실 themes_material.xml: parent 가 "Theme.Light" (themes.xml 소재).
        valuesDir.resolve(ResourceLoaderConstants.FILE_THEMES_MATERIAL).writeText("""
            <resources>
              <style name="Theme.Material.Light" parent="Theme.Light"/>
              <style name="Theme.Material.Light.NoActionBar" parent="Theme.Material.Light"/>
            </resources>
        """.trimIndent())

        valuesDir.resolve(ResourceLoaderConstants.FILE_STYLES_MATERIAL).writeText("<resources/>")
        return tmp.resolve("data")
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.FrameworkResourceValueLoaderTest"`
Expected: COMPILATION FAIL.

- [ ] **Step 3: Create loader**

```kotlin
package dev.axp.layoutlib.worker.resources

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): `data/` 디렉토리의 10 XML 을 로드하여
 * FrameworkResourceBundle 로 집계.
 *
 * JVM-wide lazy cache — layoutlib dist 는 process 생애 불변.
 * `clearCache()` 는 테스트 격리용.
 */
object FrameworkResourceValueLoader {

    private val cache = ConcurrentHashMap<Path, FrameworkResourceBundle>()

    /**
     * @param dataDir layoutlib dist 의 `data` 디렉토리 (e.g. `server/libs/layoutlib-dist/android-34/data`).
     * @throws IllegalStateException 10 XML 중 하나라도 없거나 parsing 실패.
     */
    fun loadOrGet(dataDir: Path): FrameworkResourceBundle {
        val key = dataDir.toAbsolutePath().normalize()
        cache[key]?.let { return it }
        return cache.computeIfAbsent(key) { build(it) }
    }

    /** 테스트 격리용 — production 경로에서는 호출 금지. */
    fun clearCache() {
        cache.clear()
    }

    private fun build(dataDir: Path): FrameworkResourceBundle {
        val valuesDir = dataDir.resolve(ResourceLoaderConstants.VALUES_DIR)
        val entries = mutableListOf<ParsedEntry>()
        for (filename in ResourceLoaderConstants.REQUIRED_FILES) {
            val path = valuesDir.resolve(filename)
            if (!path.exists() || !path.isRegularFile()) {
                throw IllegalStateException(
                    "필수 프레임워크 리소스 XML 누락: $path (파일명: $filename)"
                )
            }
            entries += FrameworkValueParser.parse(path)
        }
        return FrameworkResourceBundle.build(entries)
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.FrameworkResourceValueLoaderTest"`
Expected: 6 PASS.

- [ ] **Step 5: Integration smoke — 실 dist 디렉토리 로드**

다음 inline 테스트를 **추가**로 붙이지 않고, 대신 수동 smoke 명령으로 진행:

```bash
./server/gradlew -p server :layoutlib-worker:compileTestKotlin
```

- [ ] **Step 6: Regression**

Run: `./server/gradlew -p server test`
Expected: 90 unit PASS (84 + 6).

---

## Task 7: FrameworkRenderResources (TDD R4)

**Files:**
- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt`
- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameworkRenderResourcesTest {

    private fun bundle(): FrameworkResourceBundle = FrameworkResourceBundle.build(listOf(
        ParsedEntry.SimpleValue(ResourceType.DIMEN, "config_scrollbarSize", "4dp"),
        ParsedEntry.StyleDef("Theme", null, emptyList()),
        ParsedEntry.StyleDef("Theme.Material.Light.NoActionBar", "Theme", listOf(StyleItem("windowActionBar", "false"))),
    ))

    @Test
    fun `getDefaultTheme - 생성자에 전달된 이름으로 style 반환`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Theme.Material.Light.NoActionBar")
        val theme = rr.defaultTheme
        assertNotNull(theme)
        assertEquals("Theme.Material.Light.NoActionBar", theme.name)
    }

    @Test
    fun `getDefaultTheme - bundle 에 해당 style 이 없으면 parent null 빈 style 반환`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Does.Not.Exist")
        val theme = rr.defaultTheme
        assertNotNull(theme)
        assertEquals("Does.Not.Exist", theme.name)
    }

    @Test
    fun `getStyle - android namespace 에서 lookup`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Theme")
        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme")
        val s = rr.getStyle(ref)
        assertNotNull(s)
        assertEquals("Theme", s!!.name)
    }

    @Test
    fun `getStyle - project namespace 는 null`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Theme")
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme")
        assertNull(rr.getStyle(ref))
    }

    @Test
    fun `getResolvedResource 와 getUnresolvedResource - framework 에서 동일 반환`() {
        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Theme")
        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.DIMEN, "config_scrollbarSize")
        val u = rr.getUnresolvedResource(ref)
        val r = rr.getResolvedResource(ref)
        assertNotNull(u); assertNotNull(r)
        assertEquals("4dp", u!!.value)
        assertEquals("4dp", r!!.value)
    }

    @Test
    fun `findResValue override 가 존재하지 않아야 - class 에 override 메서드 없음`() {
        // 리플렉션으로 override 선언 여부 검증. W2D7 L3 landmine: findResValue override 는 프레임워크
        // resource lookup 을 가로채므로 영구 금지.
        val methods = FrameworkRenderResources::class.java.declaredMethods
        val findResValue = methods.firstOrNull { it.name == "findResValue" }
        assertTrue(findResValue == null, "findResValue 는 override 하지 말 것 — W2D7 landmine L3")
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.FrameworkRenderResourcesTest"`
Expected: COMPILATION FAIL.

- [ ] **Step 3: Create RenderResources impl**

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleResourceValueImpl

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2 3b-values): RenderResources subclass.
 * Bundle 에 있는 값을 framework (android) namespace 로 매핑하여 layoutlib 에 제공.
 *
 * 설계 결정:
 *  - `findResValue` 는 override 금지 (W2D7 L3 landmine).
 *  - default theme 은 생성자 주입. bundle 내 style 이 없어도 `parent=null` 빈 StyleResourceValue 반환
 *    — layoutlib 이 self-chain 만 수행하고 실패하지 않도록 minimal fallback.
 *  - project namespace (RES_AUTO) 요청은 전부 null — framework 전용 resolver.
 */
class FrameworkRenderResources(
    private val bundle: FrameworkResourceBundle,
    private val defaultThemeName: String,
) : RenderResources() {

    private val defaultTheme: StyleResourceValue = run {
        bundle.getStyle(defaultThemeName) ?: StyleResourceValueImpl(
            ResourceReference(ResourceNamespace.ANDROID, com.android.resources.ResourceType.STYLE, defaultThemeName),
            /* parentStyle */ null,
            /* libraryName */ null,
        )
    }

    override fun getDefaultTheme(): StyleResourceValue = defaultTheme

    override fun getStyle(ref: ResourceReference): StyleResourceValue? {
        if (ref.namespace != ResourceNamespace.ANDROID) return null
        return bundle.getStyle(ref.name)
    }

    override fun getUnresolvedResource(ref: ResourceReference): ResourceValue? {
        if (ref.namespace != ResourceNamespace.ANDROID) return null
        return bundle.getResource(ref.resourceType, ref.name)
    }

    override fun getResolvedResource(ref: ResourceReference): ResourceValue? {
        // framework scope 내에서는 unresolved==resolved (string literal, ref 해석 X).
        return getUnresolvedResource(ref)
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.resources.FrameworkRenderResourcesTest"`
Expected: 6 PASS.

- [ ] **Step 5: Regression**

Run: `./server/gradlew -p server test`
Expected: 96 unit PASS (90 + 6).

---

## Task 8: 기존 MinimalFrameworkRenderResources 제거 + SessionParamsFactory 배선

**Files:**
- Rename (reversible backup — F5): `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalFrameworkRenderResources.kt` → `.kt.w3-backup`
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt`
- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`

- [ ] **Step 1: Rename legacy class to backup (non-git reversibility)**

비-git 프로젝트이므로 즉시 `rm` 하면 롤백 불가. 백업 suffix 로 이동시켜 회복 가능 상태 유지.
(Task 10 또는 Task 11 에서 green 확인 후 `rm *.w3-backup` 으로 cleanup.)

Run:
```bash
mv server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalFrameworkRenderResources.kt \
   server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalFrameworkRenderResources.kt.w3-backup
```

Kotlin 컴파일러는 `.kt` 만 소스로 인식하므로 `.kt.w3-backup` 는 빌드 대상에서 자동 제외.

- [ ] **Step 2: Modify SessionParamsFactory.kt default RenderResources**

파일 `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt`
를 읽고, 아래 두 부분을 수정:

**A. import 변경:**

제거: `import dev.axp.layoutlib.worker.session.MinimalFrameworkRenderResources` (존재 시)
유지: `import com.android.ide.common.rendering.api.RenderResources`
추가: (없음 — FrameworkRenderResources 는 호출자가 주입)

**B. `build` 함수의 `resources` 파라미터 default 제거**:

```kotlin
fun build(
    layoutParser: ILayoutPullParser,
    callback: LayoutlibCallback = MinimalLayoutlibCallback(),
    resources: RenderResources  // default 제거 — 호출자가 반드시 주입
): SessionParams {
```

CLAUDE.md "No default parameter values" 정책 준수. 기존 `resources = MinimalFrameworkRenderResources()` default 를 제거.

**C. SessionParamsFactoryTest 영향 점검**:

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt`
가 default 에 의존하여 `resources` 를 생략하고 호출하면 컴파일 실패. 호출부에 명시적으로
`resources = FrameworkRenderResources(FrameworkResourceBundle.build(emptyList()), "Theme")` 등
empty bundle 을 주입하도록 테스트 헬퍼 추가 필요. 구체 수정은 다음 step 에서.

- [ ] **Step 3: SessionParamsFactoryTest 호출부 수정**

파일 `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt`
상단에 헬퍼 추가:

```kotlin
import dev.axp.layoutlib.worker.resources.FrameworkRenderResources
import dev.axp.layoutlib.worker.resources.FrameworkResourceBundle
// ...
private fun emptyFrameworkRenderResources() =
    FrameworkRenderResources(FrameworkResourceBundle.build(emptyList()), "Theme")
```

모든 `SessionParamsFactory.build(layoutParser = parser)` / `SessionParamsFactory.build(parser)`
호출을 `SessionParamsFactory.build(parser, resources = emptyFrameworkRenderResources())` 로 변경
(callback 도 default 허용).

- [ ] **Step 4: Modify LayoutlibRenderer.kt renderViaLayoutlib**

파일 `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`:

**A. import 추가:**

```kotlin
import dev.axp.layoutlib.worker.resources.FrameworkRenderResources
import dev.axp.layoutlib.worker.resources.FrameworkResourceValueLoader
```

**B. `renderViaLayoutlib(layoutName: String)` 의 SessionParamsFactory.build 호출 앞에 추가:**

기존 (약 Line 158-159):
```kotlin
val parser = LayoutPullParserAdapter.fromFile(layoutPath)
val params: SessionParams = SessionParamsFactory.build(layoutParser = parser)
```

변경:
```kotlin
val parser = LayoutPullParserAdapter.fromFile(layoutPath)
val bundle = FrameworkResourceValueLoader.loadOrGet(distDir.resolve("data"))
val resources = FrameworkRenderResources(bundle, SessionConstants.DEFAULT_FRAMEWORK_THEME)
val params: SessionParams = SessionParamsFactory.build(
    layoutParser = parser,
    resources = resources,
)
```

- [ ] **Step 5: Build + unit test regression**

Run: `./server/gradlew -p server test`
Expected: 96 unit PASS (변경 없음 — SessionParamsFactoryTest 호출부 수정으로 기존 카운트 유지).

- [ ] **Step 6: Build fat jar + smoke**

Run: `./server/gradlew -p server build`
Expected: BUILD SUCCESSFUL. (smoke 는 Task 9 에서.)

---

## Task 9: Tier3 integration 업데이트 (R5)

**Files:**
- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt`

- [ ] **Step 1: Read current file**

Run: `wc -l server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt`
Expected: 138 lines.

- [ ] **Step 2a (F6 분할 전반): expected/rejected 세트 + assertion 메시지 교체**

파일의 `tier3-arch` 테스트 함수 (대략 line 39-89) 내부의 set 정의를 아래로 교체:

**기존** (3b-arch 기간):
```kotlin
val expectedArchStatuses = setOf(
    Result.Status.ERROR_INFLATION,
    Result.Status.ERROR_UNKNOWN,
    Result.Status.ERROR_RENDER,
)
val rejectedStatuses = setOf(
    Result.Status.SUCCESS,
    Result.Status.ERROR_NOT_INFLATED,
)
```

**신규** (3b-values 완료 후 — stronger assertion, F4 반영):
```kotlin
// 3b-values 완료 후: SUCCESS 가 architecture+values 양쪽 모두 PASS 증거.
val expectedArchStatuses = setOf(Result.Status.SUCCESS)
// F4: draw-phase regression (ERROR_RENDER) 과 JNI/native 단계 실패 (ERROR_UNKNOWN) 도
// 명시적으로 거부하여 "silent pass" 가능성을 차단.
val rejectedStatuses = setOf(
    Result.Status.ERROR_NOT_INFLATED,  // createSession 자체 실패 — SessionParams 결함.
    Result.Status.ERROR_INFLATION,     // 3b-values 미해결 regression.
    Result.Status.ERROR_RENDER,        // draw phase 실패 (Canvas/graphics 단계).
    Result.Status.ERROR_UNKNOWN,       // native lib / JNI 단계 — W4 carry 실패 검출.
)
```

assertion fail 메시지 업데이트:
```kotlin
"3b-values 완료 canonical: SUCCESS 만 허용.\n" +
    "  actual=$status msg=$msg exc=$exc\n" +
    "  ERROR_INFLATION → 3b-values regression (framework VALUE loader 확인).\n" +
    "  ERROR_RENDER    → draw-phase regression (W4 carry 범위 침범).\n" +
    "  ERROR_UNKNOWN   → native/JNI 단계 실패 (native lib wiring 확인).\n" +
    "  ERROR_NOT_INFLATED → SessionParams 빌드 결함 (W2D7 regression).\n"
```

이 서브스텝에서 `tier3-arch` 함수 **말미의 assertion 블록만** 교체 — renderPng 호출을 둘러싼
try/catch 구조는 9b 에서 분리해서 flip (bisect surface 축소 목적).

- [ ] **Step 2b (F6 분할 후반): renderPng try/catch 구조 flip**

**기존**:
```kotlin
try {
    renderer.renderPng("activity_minimal.xml")
    fail<Unit>("3b-arch: layoutlib 이 성공했다면 3b-values (W3 carry) 가 이미 구현되었다는 뜻 — 테스트 업데이트 필요")
} catch (expected: IllegalStateException) {
    ...
}
```

**신규**:
```kotlin
// 3b-values 완료: renderPng 은 성공적으로 PNG 반환. 예외는 곧 regression.
val bytes = renderer.renderPng("activity_minimal.xml")
assertTrue(bytes.isNotEmpty(), "3b-values: PNG bytes 는 non-empty")
```

**분할 이유** (F6): 9a 는 assertion-surface 변화, 9b 는 call-shape 변화. 9a 만 적용 후 테스트를
돌렸을 때 컴파일은 green 이지만 tier3-arch 가 ERROR_INFLATION 으로 fail — 이는 정상이며 9b 적용
후 SUCCESS 로 전환됨을 기대. 둘을 한 번에 바꾸면 어느 쪽에서 regression 이 왔는지 bisect 어려움.

- [ ] **Step 3: tier3-values @Disabled 제거 + T1 gate**

**기존**:
```kotlin
@Test
@Disabled("3b-values W3 carry — framework resource VALUE loader 필요 (Paparazzi 급 infra)")
fun `tier3-values — activity_minimal renders real pixels at TextView position`() {
    ...
    assertTrue(bytes.size >= 10_000, "PNG size >= 10_000: actual=${bytes.size}")
    ...
    assertTrue(dark >= 20, "TextView 영역 dark pixels >= 20: actual=$dark")
}
```

**신규 (T1 gate — SUCCESS + size, glyph 제외)**:
```kotlin
@Test
fun `tier3-values — activity_minimal 이 SUCCESS + valid PNG 반환`() {
    val renderer = LayoutlibRenderer(locateDistDir())
    val bytes = renderer.renderPng("activity_minimal.xml")

    assertTrue(bytes.size > 1000, "PNG size > 1000 bytes: actual=${bytes.size}")
    val img = ImageIO.read(ByteArrayInputStream(bytes))
    assertNotNull(img)
    assertEquals(SessionConstants.RENDER_WIDTH_PX, img!!.width)
    assertEquals(SessionConstants.RENDER_HEIGHT_PX, img.height)

    val result = renderer.lastSessionResult
    assertNotNull(result)
    assertEquals(Result.Status.SUCCESS, result!!.status,
        "tier3-values T1 gate: SUCCESS 필요. actual=${result.status} msg=${result.errorMessage}")
}
```

- [ ] **Step 4: tier3-glyph 신설 (@Disabled W4 carry)**

기존 `tier3-values` 함수 뒤에 **추가**:

```kotlin
/**
 * tier3-glyph (W4+ carry) — 실 글리프 렌더 증명.
 * Font wiring + StaticLayout + Canvas.drawText JNI 전 영역 검증.
 * T1 gate 와 분리 (3b-values 완료는 이 테스트 unblock 의 전제).
 */
@Test
@Disabled("tier3-glyph W4 carry — Font wiring + glyph 렌더링 검증 (T2 gate)")
fun `tier3-glyph — activity_minimal 의 TextView 영역에 실 dark pixel`() {
    val renderer = LayoutlibRenderer(locateDistDir())
    val bytes = renderer.renderPng("activity_minimal.xml")

    assertTrue(bytes.size >= 10_000, "PNG size >= 10_000: actual=${bytes.size}")
    val img = ImageIO.read(ByteArrayInputStream(bytes))!!
    assertEquals(SessionConstants.RENDER_WIDTH_PX, img.width)
    assertEquals(SessionConstants.RENDER_HEIGHT_PX, img.height)

    val textRectX = 64..600
    val textRectY = 64..200
    var dark = 0
    for (y in textRectY step 2) for (x in textRectX step 2) {
        if (x >= img.width || y >= img.height) continue
        val rgb = img.getRGB(x, y)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        if (r + g + b < 384) dark++
    }
    assertTrue(dark >= 20, "TextView 영역 dark pixels >= 20: actual=$dark")
}
```

- [ ] **Step 5: Run integration tests**

Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`
Expected (F3 off-by-one 교정):
  - 기존 PASS 5: Bootstrap + BridgeInit + LayoutlibRendererIntegration + MinimalLayoutlibCallbackIntegration + **tier3-arch** (W2D7 에서 이미 PASS — 본 세션 canonical flip 후에도 PASS)
  - `tier3-values` PASS (T1 gate — @Disabled 에서 flip, **+1 PASS**)
  - `tier3-glyph` SKIPPED (신규 @Disabled, **+1 SKIPPED**)
  - `activity_basic` SKIPPED (기존 @Disabled 유지)
  - 총 **6 integration PASS + 2 SKIPPED** (이전 5 PASS + 2 SKIPPED 에서 tier3-values flip + tier3-glyph 신설).

만약 tier3-arch 가 여전히 ERROR_INFLATION 을 반환하면 실 XML 과 `FrameworkResourceBundle` 간의
불일치 — stderr 의 `[LayoutlibRenderer] createSession result` 출력 확인. 가장 흔한 원인:
parent chain 이 inference 로는 resolve 되지 않거나, `android:` 네임스페이스 값이 ref 형식 유지됨.

- [ ] **Step 6: Fat jar smoke**

Run: `java -jar server/mcp-server/build/libs/axp-server-0.1.0-SNAPSHOT.jar --smoke`
Expected: `ok` 또는 동등 smoke 통과 메시지 (기존 W2D6 동작 유지).

---

## Task 10: 문서 업데이트 (canonical + work_log)

**Files:**
- Modify: `docs/plan/08-integration-reconciliation.md` (§7.7.3 추가)
- Create: `docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md`
- Create: `docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md`
- Create: `docs/work_log/2026-04-23_w3d1-resource-value-loader/next-session-prompt.md`
- Modify: `docs/MILESTONES.md` (Week 2 close 체크박스 반영 — W3 진입)

- [ ] **Step 1: Append §7.7.3 to 08**

파일 `docs/plan/08-integration-reconciliation.md` 끝에 추가:

```markdown
### 7.7.3 W3D1 실행 결과 — 3b-values CLOSED, tier3-glyph 신규 carry (2026-04-23)

**완료**:
- `dev.axp.layoutlib.worker.resources` 서브패키지 신설 (7 main / 5 test = 12 파일, ~1000 LOC).
- `FrameworkResourceValueLoader.loadOrGet(dataDir)` 가 10 XML 을 kxml2 로 파싱 → 집계.
- `FrameworkRenderResources` 가 bundle delegate 로 layoutlib 에 값 제공.
- Tier3 `tier3-arch` 의 canonical 플립: expected=SUCCESS, rejected={ERROR_NOT_INFLATED, ERROR_INFLATION, ERROR_RENDER, ERROR_UNKNOWN} (F4).
- `tier3-values` @Disabled 제거, T1 gate (SUCCESS + PNG > 1000 bytes) 통과.
- 테스트: **92 unit + 6 integration PASS + 2 SKIPPED (activity_basic + tier3-glyph)** (F3).

**canonical split**:
- §7.7.1 item 3b (W2 carry) → **CLOSED**.
- §7.7.2 3b-values (W3 carry) → **CLOSED**.
- 신규 carry: `tier3-glyph` — Font wiring + StaticLayout + Canvas.drawText JNI 증명. T2 gate (PNG ≥ 10KB + dark pixel ≥ 20). W4+ 타겟.

**결정**:
- 스코프 Option B (Material-theme chain, 10 XML) 채택. spec: `docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md`.
- Style parent 는 pre-populate + bundle post-process.
- 캐시 단일 JVM-wide (process 생애 동안 layoutlib dist 불변).

**새 carry list** (W3 Day 2+):
- `tier3-glyph` (본 §7.7.3)
- 기존: `POST-W2D6-POM-RESOLVE` (F-6), `W3-CLASSLOADER-AUDIT` (F-4).
```

- [ ] **Step 2: Create session-log + handoff + next-session-prompt**

work_log 3 파일 작성 — 본 Task 의 실제 구현 결과를 기준으로 템플릿 채움:

`docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md` — 변경 파일 목록, 테스트 결과,
landmine, pair review 결과.

`docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md` — W3D2 cold-start 온보딩 (§5
긴급 회복 포함).

`docs/work_log/2026-04-23_w3d1-resource-value-loader/next-session-prompt.md` — 다음 세션 시작 prompt
템플릿.

상세 내용은 실제 구현 후 본 세션 종료 시점에 작성 (구현 과정에서 새로 발견된 landmine 은
§7.7.3 에도 추가 반영).

- [ ] **Step 3: MILESTONES.md 갱신 (optional — 시간 여유 시)**

Week 2 의 3b-values 체크박스 soft-close, Week 3 kickoff 헤더 추가.

---

## Task 11: 페어 리뷰 + 세션 종료

- [ ] **Step 1: Claude teammate + Codex xhigh 1:1 페어 리뷰**

CLAUDE.md 정책 — 구현 완료 후 plan/impl 페어 리뷰 필수.

Claude 측: Team `w3d1-review` 생성, `code-auditor` teammate spawn (subagent_type=general-purpose),
리뷰 범위를 다음으로 지정:
- `resources/` 서브패키지 7 production file 전수 검토.
- Tier3 테스트 세트 변경의 canonical 정합성.
- 기존 `session/` 패키지와의 경계 (중복 책임 없음).

Codex 측: `codex:rescue` 스킬로 xhigh 발동. 동일 범위, file-read 없이 inline text 기반 리뷰.

산출물: `docs/W3D1-PAIR-REVIEW.md` — convergence / divergence / judge-round 기록.

- [ ] **Step 2: Follow-up 반영**

페어가 GO_WITH_FOLLOWUPS 이면, (B1/C1/F1/F2 식 naming) 각 follow-up 을 inline 수정. 재빌드 + 재테스트.

- [ ] **Step 3: 최종 테스트 세트 확인**

Run: `./server/gradlew -p server build && ./server/gradlew -p server test && ./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`
Expected: BUILD SUCCESSFUL + 92 unit + **6 integration PASS + 2 SKIPPED** (F3 교정).

- [ ] **Step 4: `.w3-backup` cleanup (F5 cleanup)**

green 확인 후 Task 8 Step 1 에서 생성한 backup 파일 제거.
```bash
find server/layoutlib-worker/src -name "*.w3-backup" -print -delete
```
이 단계 이후로는 MinimalFrameworkRenderResources 복원 불가 — 따라서 Task 11 Step 3 의 green 확인 이후에만 실행.

- [ ] **Step 5: Task 10 work_log 3 파일을 실 내용으로 완성**

- [ ] **Step 6: Task tools 정리**

TaskList 확인, #1-#5 완료 전환.

---

## Rollback 전략 (본 플랜이 실패할 경우)

1. **Task 8 이후 tier3-arch 가 regression (ERROR_INFLATION 재발)**:
   - stderr 의 `[LayoutlibRenderer] createSession result` 메시지에서 missing resource 확인.
   - `data/res/values/` 외부 파일 (e.g. `themes_device_defaults.xml`) 의존 가능성 → carry 로 기록.
   - 임시 롤백: `LayoutlibRenderer.renderViaLayoutlib` 의 resources 주입을 MinimalFrameworkRenderResources
     로 되돌림 (git 없으므로 파일 복원은 수동).

2. **kxml2 런타임 NoClassDefFoundError**:
   - `server/layoutlib-worker/build.gradle.kts` 의 `implementation("net.sf.kxml:kxml2:2.3.0")`
     확인. Task 4 step 3 의 parser 가 compile 시점에 인식 가능해야.

3. **JVM-wide cache 관련 테스트 간 오염**:
   - `FrameworkResourceValueLoaderTest` 의 `@BeforeEach/@AfterEach clearCache()` 가 충분.
   - Integration tests 의 `forkEvery=1L` 는 이미 W2D7 에서 설정됨.

4. **Spec 과 실측의 불일치 (예: `<style name="Theme">` 위치 다름)**:
   - §2 스코프의 10 파일 list 에서 해당 파일이 정말 필요한지 재검토.
   - `values-*/` qualifier 폴더에만 존재할 가능성 — 그 경우 `W4-QUALIFIER-RESOLVER` 로 분리.

---

END of plan.
