# W2D7 Implementation Plan — 실 RenderSession 경로 (08 §7.7.1 item 3b)

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:executing-plans` (또는 `superpowers:subagent-driven-development` 사용 가능 시 우선). Steps 는 `- [ ]` 체크박스. 각 Task 는 TDD (테스트 먼저 실패 → 구현 → 재실행 PASS) 순서.

**Goal:** `docs/plan/08-integration-reconciliation.md §7.7.1 item 3b` 를 canonical 충족. W2D6 의 `LayoutlibRenderer` 는 `Bridge.init` 후 BufferedImage stub 만 반환. 본 플랜은 `Bridge.createSession(SessionParams)` → `RenderSession.render(timeout)` → `session.image` 실제 layoutlib 렌더 픽셀을 PNG 로 반환하도록 교체한다.

**Plan pair review (2026-04-23)**: `docs/W2D7-PLAN-PAIR-REVIEW.md` — FULL CONVERGENCE GO_WITH_FOLLOWUPS. 4개 follow-up (F1 layoutlib-api URL 제거, F2 Theme baseline 승격, F3 Tier3 targeted-rect, F4 테스트 gap) 이 본 플랜에 이미 반영되어 있음.

**Architecture:**

- `:layoutlib-worker` 모듈에 `compileOnly + runtimeOnly` 로 `com.android.tools.layoutlib:layoutlib-api:31.13.2` 추가. 소스 레벨 subclass (abstract `LayoutlibCallback`, `AssetRepository`, `ILayoutPullParser`) 가 가능해지면서도, 런타임엔 isolated URLClassLoader 의 parent(=system CL) 로 delegate 되어 Bridge 가 기대하는 **동일 Class 객체**로 결합된다 (parent-first 위임). layoutlib 본체 (`layoutlib-14.0.11.jar`) 만 isolated CL 에서 로드하면 클래스 정체성 충돌은 발생하지 않는다.
- 새 파일 **모두 `:layoutlib-worker`** 의 `dev.axp.layoutlib.worker.session` 하위패키지. :protocol / :http-server 침범 없음.
- 새 fixture `activity_minimal.xml` 추가 — 기본 프레임워크 위젯 (LinearLayout + TextView) 만. 기존 `activity_basic.xml` 은 ConstraintLayout/MaterialButton 커스텀 뷰를 써서 L3 target. W2D7 canonical target 는 minimal fixture 로 정명화.
- `LayoutlibRenderer.renderViaLayoutlib()` 내부를 실제 pipeline 으로 교체. `initBridge()` 및 ShutdownHook 패턴은 유지. 각 render per-call 마다 `RenderSession.dispose()` try/finally.

**Tech Stack:** Kotlin 1.9.23, layoutlib-api 31.13.2 (신규 dep), layoutlib 14.0.11 (dist), kxml2 2.3.0 (runtime, 기존). JDK 17.

**Canonical references:**
- `docs/plan/08-integration-reconciliation.md §7.7.1` — 3a closure + 3b carry 구조
- `docs/work_log/2026-04-23_w2d6-stdio-and-fatjar/handoff.md §2.2` — W2D7-RENDERSESSION 작업 순서
- `docs/W2D6-PAIR-REVIEW.md` — 이전 게이트 페어 컨센서스 (FULL CONVERGENCE)
- `server/libs/layoutlib-dist/android-34/layoutlib-api-31.13.2.jar` — API surface (javap 로 검증)
- Paparazzi (cashapp/paparazzi) — 동일 전략(`compileOnly layoutlib-api` + Bridge subclassing) 을 사용. 단 우리는 pure framework widget 한정으로 최소화.

---

## File Structure

### 신규 파일
- `fixture/sample-app/app/src/main/res/layout/activity_minimal.xml` — W2D7 canonical fixture (프레임워크 위젯만).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapter.kt` — kxml2 `KXmlParser` 위에 `ILayoutPullParser` 구현. `getViewCookie()` = null, `getLayoutNamespace()` = `ResourceNamespace.RES_AUTO`.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` — abstract `LayoutlibCallback` subclass. resource id auto-generator + AndroidX/AppCompat no-op flags + no-op getAdapterBinding + `getActionBarCallback()`=`ActionBarCallback()`.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/NoopAssetRepository.kt` — `AssetRepository` subclass. `isSupported()=false`, openAsset/openNonAsset throw `IOException`.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt` — 주어진 (layoutXmlPath, renderer context) → `SessionParams` 빌드. `HardwareConfig(720, 1280, Density.XHIGH, 320f, 320f, ScreenSize.NORMAL, ScreenOrientation.PORTRAIT, ScreenRound.NOTROUND, false)` 고정.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt` — 모든 상수 (defaultTimeoutMs=6_000, minSdk=34, targetSdk=34, dpi=320f, width/height, namespace, 등).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapterTest.kt` — 단위 테스트 (fixture minimal XML 파싱, getAttributeValue 확인).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt` — 단위 테스트 (resource id 1회 발급 → 동일값 재발급, getAdapterBinding null, getActionBarCallback non-null).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt` — 단위 테스트 (HardwareConfig 필드, SessionParams.getRenderingMode, getLayoutDescription not null).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt` — 신규 Tier3 (PNG size ≥ 10_000 bytes + 비흰-픽셀 검출).

### 수정 파일
- `server/layoutlib-worker/build.gradle.kts` — `implementation("com.android.tools.layoutlib:layoutlib-api:31.13.2")` 추가 (compile + runtime). 추가 이유 주석.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — `renderViaLayoutlib(layoutName)` 내부를 real RenderSession pipeline 으로 교체. `initBridge()` + ShutdownHook 패턴 유지. BufferedImage stub 경로 삭제. per-render session dispose try/finally.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererConstants.kt` — LABEL_X_PX / LABEL_Y_PX 상수는 placeholder 전용이었으므로 `@Deprecated` 혹은 제거. render dimension 상수는 `SessionConstants` 와 single source 유지.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` — 기존 Tier3 테스트는 PNG magic header 만 체크. **개선**: fixture 를 `activity_minimal.xml` 로 명시. 강화된 Tier3 는 별도 파일 (`LayoutlibRendererTier3MinimalTest.kt`) 로 신규 작성하여 기존 경로 회귀 방지.
- `docs/plan/08-integration-reconciliation.md §7.7` — item 3 체크박스 `[x]` 전환. §7.7.1 3b 를 CLOSED 로 명시.
- `docs/MILESTONES.md` — Week 1 Go/No-Go item 2 문구 "3a + 3b 완료" 로 정명화.
- `docs/work_log/2026-04-23_w2d7-rendersession/` — session-log.md + handoff.md + next-session-prompt.md.

---

## Task 1 — 의존성 및 fixture 추가 (preflight)

**Why first:** 소스 파일 작성 전에 컴파일 타임 API 와 테스트 타겟 fixture 가 존재해야 한다.

### Step 1 — layoutlib-api dep 추가

**Files:** `server/layoutlib-worker/build.gradle.kts`

- [ ] `dependencies {}` 블록에 `implementation("com.android.tools.layoutlib:layoutlib-api:31.13.2")` 추가.
- [ ] 추가 이유 주석:
  ```
  // W2D7-RENDERSESSION (08 §7.7.1 item 3b): LayoutlibCallback / AssetRepository /
  // ILayoutPullParser subclass 를 :layoutlib-worker 소스에 직접 작성하기 위함.
  // runtime 은 isolated URLClassLoader parent-first delegation 으로 system CL 의 이
  // API JAR 이 Bridge 내부와 동일 Class 객체로 결합된다.
  ```

### Step 1.5 — F1 (페어 리뷰): `layoutlib-api.jar` 를 isolated CL URL 에서 제거

**Files:** `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt`

- [ ] `createIsolatedClassLoader()` 에서 `apiJar` URL 을 제거 — URL 배열에는 `mainJar` (layoutlib-*.jar) 만 남김. `findLayoutlibApiJar()` 메서드 자체는 `validate()` 에서 계속 사용하므로 유지.
- [ ] 교체 주석:
  ```
  // W2D7-RENDERSESSION F1 (페어 리뷰 FULL CONVERGENCE): :layoutlib-worker 가
  // implementation 으로 layoutlib-api:31.13.2 를 가지므로 이 classloader 의 parent(system CL)
  // 에 이미 존재. 자식 URL 에 중복 선언하면 parent-first 로 shadow 되지만, 향후 loader 정책
  // 변경(child-first 등) 시 ClassCastException 트랩. single source of truth 로 제거.
  ```

### Step 2 — 컴파일 검증

- [ ] `./server/gradlew -p server :layoutlib-worker:compileKotlin` 실행. BUILD SUCCESSFUL 확인.
- [ ] `./server/gradlew -p server build` BUILD SUCCESSFUL 확인 (기존 테스트 40 unit + 5 integration 영향 없음).
- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` Tier1/Tier2 통과 확인 — F1 의 URL 제거가 Bridge 로딩을 깨지 않음을 확인.

### Step 3 — Minimal fixture XML 작성

**Files:** `fixture/sample-app/app/src/main/res/layout/activity_minimal.xml` (신규)

- [ ] 파일 내용:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!--
    08 §7.7.1 item 3b — W2D7 canonical render target.
    프레임워크 위젯만 (Material / AndroidX / ConstraintLayout 미사용).
    LayoutlibCallback.loadView 가 android.widget.* 기본 경로만 타면 되도록 최소화.
  -->
  <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      android:orientation="vertical"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      android:background="#FFFFFFFF"
      android:padding="32dp">

      <TextView
          android:id="@+id/title"
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:text="Hello layoutlib"
          android:textColor="#FF000000"
          android:textSize="28sp" />

      <View
          android:layout_width="match_parent"
          android:layout_height="1dp"
          android:layout_marginTop="16dp"
          android:background="#FF888888" />
  </LinearLayout>
  ```

### Step 4 — fixture 가 디스크에 존재하는지 확인

- [ ] `test -f fixture/sample-app/app/src/main/res/layout/activity_minimal.xml`

---

## Task 2 — SessionConstants + LayoutPullParserAdapter (TDD)

### Step 1 — SessionConstants 먼저 (magic number 정책)

**Files:** `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt`

- [ ] 파일 작성:
  ```kotlin
  package dev.axp.layoutlib.worker.session

  /**
   * W2D7-RENDERSESSION: SessionParams / HardwareConfig / render timeout 등의 상수.
   * CLAUDE.md: "Zero Tolerance for Magic Numbers/Strings" 정책.
   */
  object SessionConstants {
      const val RENDER_WIDTH_PX = 720
      const val RENDER_HEIGHT_PX = 1280
      const val DPI_XHIGH = 320f

      /** RenderSession.render(timeout) millis. 6s 는 Paparazzi 기본값과 정합. */
      const val RENDER_TIMEOUT_MS = 6_000L

      const val MIN_SDK = 34
      const val TARGET_SDK = 34

      const val PROJECT_KEY = "axp-w2d7-rendersession"

      /** FQN for reflective lookup. layoutlib-api 와 layoutlib 내부 클래스. */
      const val BRIDGE_FQN = "com.android.layoutlib.bridge.Bridge"
      const val CREATE_SESSION_METHOD = "createSession"

      /** layoutlib framework package name (android.*). */
      const val FRAMEWORK_PACKAGE = "android"

      /** Default locale when rendering. */
      const val RENDER_LOCALE = "en"
  }
  ```

### Step 2 — LayoutPullParserAdapter failing test

**Files:** `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapterTest.kt`

- [ ] 작성 (가이드: @Tag 미지정 → unit test, 기본 build 에서 실행):
  ```kotlin
  package dev.axp.layoutlib.worker.session

  import com.android.ide.common.rendering.api.ResourceNamespace
  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertNull
  import org.junit.jupiter.api.Test
  import org.xmlpull.v1.XmlPullParser
  import java.io.StringReader

  class LayoutPullParserAdapterTest {

      private val sampleXml = """
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:orientation="vertical">
              <TextView android:text="hi" />
          </LinearLayout>
      """.trimIndent()

      @Test
      fun `parses element name and attributes`() {
          val p = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
          while (p.eventType != XmlPullParser.START_TAG) p.next()
          assertEquals("LinearLayout", p.name)
          assertEquals("vertical", p.getAttributeValue(null, "orientation")
              ?: p.getAttributeValue("http://schemas.android.com/apk/res/android", "orientation"))
      }

      @Test
      fun `getViewCookie returns null`() {
          val p = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
          assertNull(p.viewCookie)
      }

      @Test
      fun `getLayoutNamespace returns RES_AUTO`() {
          val p = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
          assertEquals(ResourceNamespace.RES_AUTO, p.layoutNamespace)
      }
  }
  ```

- [ ] `./server/gradlew -p server :layoutlib-worker:test` → FAIL (클래스 없음).

### Step 3 — LayoutPullParserAdapter 구현

**Files:** `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapter.kt`

- [ ] 작성 — `ILayoutPullParser` 를 구현. 내부에 `KXmlParser` 를 위임. `getViewCookie()`=null, `getLayoutNamespace()`=`ResourceNamespace.RES_AUTO`.
- [ ] Static factory `fromReader(Reader)`, `fromFile(Path)` 추가.
- [ ] `XmlPullParser` 의 모든 추상 메서드는 delegate 로 수동 포워딩 (Kotlin `by` delegation 은 `XmlPullParser` 가 interface 인 점 이용 가능).
- [ ] 주석: "ILayoutPullParser 는 `getViewCookie` 만 추가한 얇은 확장. layoutlib 이 뷰 계층 ↔ XML 소스 mapping 에 사용."

### Step 4 — 테스트 재실행

- [ ] `./server/gradlew -p server :layoutlib-worker:test` → PASS.

---

## Task 3 — MinimalLayoutlibCallback (TDD)

### Step 1 — Failing test

**Files:** `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt`

- [ ] 작성:
  ```kotlin
  package dev.axp.layoutlib.worker.session

  import com.android.ide.common.rendering.api.ResourceNamespace
  import com.android.ide.common.rendering.api.ResourceReference
  import com.android.resources.ResourceType
  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertNotNull
  import org.junit.jupiter.api.Assertions.assertNull
  import org.junit.jupiter.api.Test

  class MinimalLayoutlibCallbackTest {

      @Test
      fun `resource id is stable across calls`() {
          val cb = MinimalLayoutlibCallback()
          val ref = ResourceReference(
              ResourceNamespace.RES_AUTO, ResourceType.ID, "title"
          )
          val first = cb.getOrGenerateResourceId(ref)
          val second = cb.getOrGenerateResourceId(ref)
          assertEquals(first, second)
      }

      @Test
      fun `resolveResourceId returns registered reference`() {
          val cb = MinimalLayoutlibCallback()
          val ref = ResourceReference(
              ResourceNamespace.RES_AUTO, ResourceType.ID, "title"
          )
          val id = cb.getOrGenerateResourceId(ref)
          assertEquals(ref, cb.resolveResourceId(id))
      }

      @Test
      fun `getAdapterBinding is null`() {
          val cb = MinimalLayoutlibCallback()
          assertNull(cb.getAdapterBinding(Any(), emptyMap()))
      }

      @Test
      fun `getActionBarCallback is non-null`() {
          val cb = MinimalLayoutlibCallback()
          assertNotNull(cb.getActionBarCallback())
      }

      @Test
      fun `getParser returns null for any value`() {
          val cb = MinimalLayoutlibCallback()
          assertNull(cb.getParser(null))
      }
  }
  ```

- [ ] 테스트 FAIL 확인.

### Step 2 — 구현

**Files:** `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`

- [ ] `LayoutlibCallback` abstract class 를 subclass. `java.util.concurrent.atomic.AtomicInteger` 로 id 카운터.
- [ ] `val byId: MutableMap<Int, ResourceReference>` + `val byRef: MutableMap<ResourceReference, Int>`.
- [ ] `getOrGenerateResourceId(ref)`: 이미 존재하면 기존 id, 아니면 카운터 증가 + 양방향 등록.
- [ ] `resolveResourceId(id)`: `byId[id]`.
- [ ] `loadView(name, classes, args)`: `throw UnsupportedOperationException("W2D7 minimal: custom view '$name' 은 L3 (W3+) 타겟")`. Framework widgets 는 Bridge 가 내부에서 처리하므로 호출되지 않음.
- [ ] `getParser(value)`: null (include 없음).
- [ ] `getAdapterBinding(cookie, attrs)`: null.
- [ ] `getActionBarCallback()`: `ActionBarCallback()` 기본.
- [ ] `createXmlParser()` / `createXmlParserForFile(path)` / `createXmlParserForPsiFile(path)`: 최소 — 본 W2D7 경로에서 호출되지 않지만, `XmlParserFactory` interface 계약 이행. 빈 `KXmlParser` 반환.
- [ ] `hasLegacyAppCompat()` / `hasAndroidXAppCompat()` / `isResourceNamespacingRequired()` / `shouldUseCustomInflater()`: false. (default impl 도 false 이지만 명시적으로 override 안 함 → default 사용 OK).
- [ ] `getApplicationId()`: `"axp.render"`.

### Step 3 — 테스트 재실행

- [ ] `./server/gradlew -p server :layoutlib-worker:test` → PASS.

---

## Task 4 — NoopAssetRepository + SessionParamsFactory (TDD)

### Step 1 — Failing test

**Files:** `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt`

- [ ] 작성:
  ```kotlin
  package dev.axp.layoutlib.worker.session

  import com.android.ide.common.rendering.api.SessionParams
  import com.android.resources.Density
  import com.android.resources.ScreenOrientation
  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertNotNull
  import org.junit.jupiter.api.Assertions.assertSame
  import org.junit.jupiter.api.Test
  import java.io.StringReader

  class SessionParamsFactoryTest {

      private val sampleXml = """
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent" android:layout_height="match_parent"/>
      """.trimIndent()

      @Test
      fun `HardwareConfig is phone xhigh portrait`() {
          val params = SessionParamsFactory.build(
              layoutParser = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
          )
          val hw = params.hardwareConfig
          assertEquals(SessionConstants.RENDER_WIDTH_PX, hw.screenWidth)
          assertEquals(SessionConstants.RENDER_HEIGHT_PX, hw.screenHeight)
          assertEquals(Density.XHIGH, hw.density)
          assertEquals(ScreenOrientation.PORTRAIT, hw.orientation)
      }

      @Test
      fun `rendering mode is NORMAL`() {
          val params = SessionParamsFactory.build(
              layoutParser = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
          )
          assertEquals(SessionParams.RenderingMode.NORMAL, params.renderingMode)
      }

      @Test
      fun `layout parser is forwarded to SessionParams`() {
          val parser = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
          val params = SessionParamsFactory.build(layoutParser = parser)
          assertSame(parser, params.layoutDescription)
      }

      @Test
      fun `timeout is configured`() {
          val params = SessionParamsFactory.build(
              layoutParser = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
          )
          assertEquals(SessionConstants.RENDER_TIMEOUT_MS, params.timeout)
      }
  }
  ```

### Step 2 — 구현 (F2: Theme baseline 승격 — 페어 리뷰 FULL CONVERGENCE)

**Files:**
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/NoopAssetRepository.kt`
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalFrameworkRenderResources.kt` (신규 — F2)
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt`

- [ ] `NoopAssetRepository`: `AssetRepository` subclass. `isSupported()=false`. `openAsset`/`openNonAsset` throw `IOException`. `isFileResource(String)=false`.

- [ ] `MinimalFrameworkRenderResources` (F2 신규): `RenderResources` 를 subclass. 최소한 `getDefaultTheme()` 이 non-null `StyleResourceValue` (Theme.Material.Light.NoActionBar 를 참조) 를 반환. `findItemInTheme(ref)` 는 TextView/LinearLayout inflate 가 일반적으로 요청하는 attrs (`textAppearance`, `textSize`, `textColor`, `background`) 에 대해 null return 허용 — 우리 fixture 는 이미 literal 만 사용하므로 리턴값 없이도 inflate 통과를 기대. 단, `getDefaultTheme()` 이 null 이 아니어야 layoutlib 의 `Resources.Theme` 구성 파이프라인이 NPE 없이 통과.
  - 구현 전략 **A (preferred)**: `ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme.Material.Light.NoActionBar")` 을 `StyleResourceValueImpl` (layoutlib-api 의 `StyleResourceValueImpl` 또는 `StyleResourceValue` 의 공용 impl) 로 감싸 반환. 만약 layoutlib-api 에 공용 impl 이 없으면 전략 B.
  - 구현 전략 **B (fallback)**: anonymous `StyleResourceValue` subclass (interface 가 아닌 abstract class 여부 확인 필요). 인터페이스면 익명 객체 OK, abstract class 면 subclass 파일 1개 추가.
  - **실행 중 발견 사항은 session-log 에 "F2 실제 API 경로" 로 기록** — 페어 리뷰에서 Codex 가 플랜 gap 으로 지목한 부분.

- [ ] `SessionParamsFactory.build(layoutParser, callback=MinimalLayoutlibCallback(), log=NoopLogHandler 프록시)`:
  - `HardwareConfig(720, 1280, Density.XHIGH, 320f, 320f, ScreenSize.NORMAL, ScreenOrientation.PORTRAIT, ScreenRound.NOTROUND, false)`
  - `MinimalFrameworkRenderResources()` — **F2: 빈 RenderResources 금지**.
  - `SessionParams(parser, RenderingMode.NORMAL, PROJECT_KEY, hw, rr, callback, MIN_SDK, TARGET_SDK, ilayoutlog)`.
  - `params.setAssetRepository(NoopAssetRepository())`.
  - `params.setForceNoDecor()` — **F2: decor 제거로 status/action bar 없는 순수 content 만**.
  - `params.timeout = RENDER_TIMEOUT_MS`.
  - `params.setLocale(RENDER_LOCALE)`.
  - return params.

- [ ] ILayoutLog 프록시: 기존 `LayoutlibRenderer` 의 `NoopLogHandler` 를 `NoopLogProxies.kt` 로 승격(단일 source). 또는 `SessionParamsFactory` 내부 private.

### Step 2.1 — F4 (페어 리뷰): 테스트 gap 메우기

- [ ] `LayoutPullParserAdapterTest` 에 추가: `fromFile loads sample layout` — `activity_minimal.xml` 을 `fromFile(Path)` 로 파싱, root tag 가 `LinearLayout` 인지 assert.
- [ ] `LayoutPullParserAdapterTest` 에 추가: `getAttributeValue by android namespace URI` — `p.getAttributeValue("http://schemas.android.com/apk/res/android", "orientation")` 이 `"vertical"` 을 반환.
- [ ] `SessionParamsFactoryTest` 에 추가: `AssetRepository is attached` — `params.getAssets()` 이 `NoopAssetRepository` 인스턴스.
- [ ] `SessionParamsFactoryTest` 에 추가: `default theme is non-null and framework-namespaced` — `params.resources.defaultTheme` 이 non-null 이고 `namespace == ResourceNamespace.ANDROID`.
- [ ] `SessionParamsFactoryTest` 에 추가: `forceNoDecor flag applied` — reflective check on `params` (or observed via `session.getResult().getStatus()` 통합에서 검증 가능).

### Step 3 — 테스트 재실행

- [ ] `./server/gradlew -p server :layoutlib-worker:test` → PASS.

---

## Task 5 — LayoutlibRenderer.renderViaLayoutlib() 실 경로 교체

### Step 1 — 기존 Tier3 통과 유지 확인

- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` → 기존 Tier3 (PNG magic only) 통과.

### Step 2 — 실 경로 구현

**Files:** `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`

- [ ] `renderViaLayoutlib(layoutName: String)` 시그니처는 유지. 새 구현:
  1. fixture path 해결 — `fixture/sample-app/app/src/main/res/layout/$layoutName` (파일 없으면 throw).
  2. `LayoutPullParserAdapter.fromFile(path)` 로 parser 생성.
  3. `SessionParamsFactory.build(parser)` 로 SessionParams.
  4. `Bridge.createSession(params)` reflection 호출 — return `RenderSession` 인스턴스.
  5. try { `session.render(RENDER_TIMEOUT_MS)` → `session.getResult()` → `isSuccess` 체크; 실패 시 null return (fallback). `session.getImage(): BufferedImage` → PNG encode } finally { `session.dispose()` }.
  6. PNG bytes 반환.
- [ ] BufferedImage stub 경로 (흰 바탕 + `drawString`) 는 삭제.
- [ ] `LABEL_X_PX` / `LABEL_Y_PX` 상수도 더이상 사용되지 않으므로 `@Deprecated("W2D7: placeholder label 제거")` 처리하거나 파일에서 삭제 (삭제 권장).

### Step 3 — 기존 Tier3 재확인

- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` → **여전히 PASS** (같은 fixture 로 real render 산출 PNG 여도 magic header 는 유지).
- [ ] 만약 FAIL: `assumeTrue(false, reason)` 로 gracefully skip 되도록 renderer 안의 try/catch 확인. 단, 이 단계에서 SKIP 이 허용되는 것은 layoutlib 내부 실패일 때 한정. fixture 미발견 같은 우리 코드 버그는 throw.

---

## Task 6 — Tier3 강화 테스트 작성

### Step 1 — Failing test

**Files:** `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt`

- [ ] 작성 (@Tag("integration")) — F3: targeted-rect dark-pixel 검사 + HardwareConfig 치수 검증:
  ```kotlin
  package dev.axp.layoutlib.worker

  import dev.axp.layoutlib.worker.session.SessionConstants
  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertTrue
  import org.junit.jupiter.api.Assumptions.assumeTrue
  import org.junit.jupiter.api.Tag
  import org.junit.jupiter.api.Test
  import java.io.ByteArrayInputStream
  import java.nio.file.Path
  import java.nio.file.Paths
  import javax.imageio.ImageIO
  import kotlin.io.path.exists
  import kotlin.io.path.isDirectory

  /**
   * W2D7-RENDERSESSION (08 §7.7.1 item 3b) Tier3 강화 (F3: 페어 리뷰 FULL CONVERGENCE):
   *  - PNG size >= 10_000 bytes (corruption guard, stub 제거 확인).
   *  - 렌더 결과 치수가 HardwareConfig 와 일치 (width 720 / height 1280).
   *  - TextView text 가 위치한 targeted rect 안에 진한 pixel 이 있음을 assert
   *    (글로벌 non-white 카운트는 anti-aliasing noise 로 false-positive 가능).
   */
  @Tag("integration")
  class LayoutlibRendererTier3MinimalTest {

      /** fixture TextView 예상 위치: padding 32dp × xhdpi = 64px + ascent. */
      private val textRectX = 64..600
      private val textRectY = 64..200

      @Test
      fun `tier3 — activity_minimal renders real pixels at TextView position`() {
          val renderer = LayoutlibRenderer(locateDistDir())
          val bytes = try {
              renderer.renderPng("activity_minimal.xml")
          } catch (e: Throwable) {
              assumeTrue(
                  false,
                  "LayoutlibRenderer invoke 실패 (best-effort, layoutlib 내부 실패): " +
                      "${e.javaClass.simpleName} ${e.message?.take(160)}"
              )
              return
          }

          // (1) corruption guard
          assertTrue(
              bytes.size >= 10_000,
              "RenderSession 경로는 stub 보다 훨씬 큰 PNG 를 생성해야 함. actual=${bytes.size}"
          )

          val img = ImageIO.read(ByteArrayInputStream(bytes))
              ?: error("PNG decode 실패 — bytes=${bytes.size}")

          // (2) dimension assertion — HardwareConfig 가 실제로 적용되었는지
          assertEquals(SessionConstants.RENDER_WIDTH_PX, img.width, "PNG width 가 HardwareConfig 와 일치")
          assertEquals(SessionConstants.RENDER_HEIGHT_PX, img.height, "PNG height 가 HardwareConfig 와 일치")

          // (3) targeted-rect dark-pixel assertion
          val darkPixelCount = countDarkPixels(img, textRectX, textRectY)
          assertTrue(
              darkPixelCount >= 20,
              "TextView 영역 (x=$textRectX, y=$textRectY) 에 진한 pixel (R+G+B<384) 이 " +
                  "최소 20 개 있어야 함 — 실제 text render 흔적. actual=$darkPixelCount"
          )
      }

      /**
       * 주어진 rect 범위 내 dark pixel 개수. R+G+B < 384 (각 채널 평균 128 미만) 이면 진한 색.
       * #FF000000 (text) 은 통과, #FFFFFFFF (bg) / #FF888888 (divider, rect 밖) 는 제외.
       */
      private fun countDarkPixels(
          img: java.awt.image.BufferedImage,
          xRange: IntRange,
          yRange: IntRange
      ): Int {
          var count = 0
          for (y in yRange step 2) {
              for (x in xRange step 2) {
                  if (x >= img.width || y >= img.height) continue
                  val rgb = img.getRGB(x, y)
                  val r = (rgb shr 16) and 0xFF
                  val g = (rgb shr 8) and 0xFF
                  val b = rgb and 0xFF
                  if (r + g + b < 384) count++
              }
          }
          return count
      }

      private fun locateDistDir(): Path {
          val candidates = listOf(
              Paths.get("../libs", "layoutlib-dist", "android-34"),
              Paths.get("server/libs/layoutlib-dist/android-34"),
              Paths.get(System.getProperty("user.dir"), "../libs/layoutlib-dist/android-34")
          )
          val found = candidates.firstOrNull { it.exists() && it.isDirectory() }
          assumeTrue(found != null, "dist 없음 — W1D3-R2 다운로드를 먼저 수행")
          return found!!.toAbsolutePath().normalize()
      }
  }
  ```

### Step 2 — 실행 → RED / GREEN

- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`
- [ ] 가능한 결과 분기:
  - **PASS**: 이상적. Task 7 로.
  - **FAIL (pixel < 1000)**: 렌더가 흰 배경만 만듦 → 테마 또는 View 인플레이션 문제. 조사:
    1. `session.getResult().getStatus()` / `getErrorMessage()` 를 renderer 에서 stderr 로 dump 하여 원인 확인 (임시 로그).
    2. fixture 에 `android:background="#FFFF0000"` (빨강) 같은 명시적 색을 두어도 흰색이면 View hierarchy 가 인플레이트되지 않은 것.
    3. 해결 방향 후보:
       a. `RenderResources` 에 default theme 설정 필요. `StyleResourceValue` 로 `@android:style/Theme.Material` 참조 생성 후 `resources.applyStyle(style, true)`.
       b. `setForceNoDecor()` 호출하여 status bar/action bar 제거.
       c. `SessionParams.setTransparentBackground()` 또는 명시적 fontScale.
    4. 1회 iteration 으로 해결 안 되면 **테마 wiring 을 별도 sub-task 로 분리**하여 plan 에 반영. Scope creep 방지.
  - **SKIP (assumeTrue false)**: renderer 가 예외를 던져 best-effort skip 된 상태. 이 경우 **FAIL 로 간주** (W2D7 canonical 충족 아님). Renderer 의 try/catch 는 `session.getResult().isSuccess=false` 일 때 null return 이지 throw 가 아니므로 assumeTrue 경로는 일반적으로 renderer 내부 예외 의미. 원인 조사 후 handle.

### Step 3 — 해결 후 재실행

- [ ] 모든 integration test (Tier1 3개 + Tier2 1개 + 기존 Tier3 1개 + 신규 Tier3 1개 = 6개) PASS 확인.

---

## Task 7 — canonical 문서 갱신

### Step 1 — 08 §7.7 item 3 체크박스 전환

**Files:** `docs/plan/08-integration-reconciliation.md`

- [ ] §7.7 의 item 3 (3b) 체크박스 `[ ]` → `[x]` + 완료 날짜 `(W2D7 완료)` 추가.
- [ ] §7.7.1 에 "3b CLOSED — 2026-04-23 W2D7. Real RenderSession 경로 도입, PNG size ≥ 10KB + 비-흰 픽셀 ≥ 1000 검증." 기록.

### Step 2 — MILESTONES.md 갱신

**Files:** `docs/MILESTONES.md`

- [ ] Week 1 Go/No-Go item 2 문구를 "item 3a + 3b 완료 (W2D6 + W2D7)" 로 갱신.

### Step 3 — work_log 3종 작성

**Files:**
- `docs/work_log/2026-04-23_w2d7-rendersession/session-log.md`
- `docs/work_log/2026-04-23_w2d7-rendersession/handoff.md`
- `docs/work_log/2026-04-23_w2d7-rendersession/next-session-prompt.md`

- [ ] session-log: 수정 파일/신규 파일 목록 + 각 파일당 1줄 이유. landmine 기록.
- [ ] handoff: W3 kickoff 로 넘길 항목 (POST-W2D6-POM-RESOLVE, W3-CLASSLOADER-AUDIT, L3 DexClassLoader pipeline 시작).
- [ ] next-session-prompt: 다음 세션 cold start 용 prompt.

---

## Task 8 — W2D7 최종 페어 리뷰 (Codex xhigh + Claude)

**CLAUDE.md 정책**: 구현/리뷰 라운드는 Codex xhigh + Claude sonnet 1:1 페어. 각 독립적으로 PR diff 를 분석, 결과를 side-by-side 로 consolidate. divergence 시 judge round.

### Step 1 — Codex xhigh 리뷰

- [ ] Codex CLI 호출 (skill: `codex:rescue` 또는 `codex:codex-cli-runtime`). 리뷰 입력:
  - `git diff` (또는 modified/new 파일 전체)
  - 플랜 본 문서
  - `docs/plan/08-integration-reconciliation.md §7.7.1`
- [ ] 리뷰 요청: "이 W2D7 구현이 §7.7.1 3b canonical 을 충족하는지. 아키텍처 concerns (classloader parent-first 가정, layoutlib-api 이중 등장), 테스트 커버리지 gap, CLAUDE.md 스타일 준수 (magic number, brace, shared_ptr N/A)."
- [ ] 결과 verdict 기록 (GO / NO_GO / GO_WITH_FOLLOWUPS).

### Step 2 — Claude 독립 리뷰

- [ ] Claude general-purpose subagent (읽기 전용 권장) 로 동일 입력 리뷰. verdict 비교.

### Step 3 — Consolidation

**Files:** `docs/W2D7-PAIR-REVIEW.md`

- [ ] FULL CONVERGENCE / SET-ONLY / DIVERGENT 판정. divergent 면 judge round.
- [ ] 최종 GO 문서화.

---

## Success Criteria

- [ ] `./server/gradlew -p server build` SUCCESS.
- [ ] `./server/gradlew -p server test` 40 unit + 신규 unit (Parser 3 + Callback 5 + Factory 4 = +12) = 52 PASS.
- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` 6 PASS (Tier1 3 + Tier2 1 + 기존 Tier3 1 + 신규 Tier3 1).
- [ ] `docs/W2D7-PAIR-REVIEW.md` 최종 GO 기록.
- [ ] 08 §7.7.1 3b 체크박스 closure 완료.

## Known landmines / fail-fast exits

1. **`RenderResources` empty 로 theme 을 요구하는 View 가 있으면 render 실패** — fixture 를 literal-only 로 유지. 실패 시 Task 6 Step 2 의 대응 경로.
2. **isolated CL 이 layoutlib-api 를 shadow** — layoutlib-api JAR 이 system CL 에도 등장 (implementation dep) 하므로 parent-first 로 shadow 됨. 만약 `ClassCastException: Lcom/android/ide/common/rendering/api/LayoutlibCallback` 이 발생하면 isolated CL 의 layoutlib-api URL 을 제거하여 ONE definition 만 남도록 조정 (`LayoutlibBootstrap.createIsolatedClassLoader()` 수정 필요 — W3-CLASSLOADER-AUDIT 로 이관 가능).
3. **Bridge.createSession 이 native 호출에 의존** — `libandroid_runtime.so` 로드가 선행되어야 함. `initBridge()` 에 이미 존재. 실패 시 `UnsatisfiedLinkError` → renderer 가 null 반환 → fallback (placeholder) 로 동작해 testing 도 assumeTrue 로 skip.
4. **kxml2 KXmlParser 가 `nextTag` 전 `START_DOCUMENT` 요구** — Parser adapter 에서 `setInput(Reader, null)` 후 바로 반환 (read-ahead 하지 않음). layoutlib 이 스스로 next() 를 호출하여 시작.
5. **layoutlib 14.x Java API ≥ 17 요구** — 현재 JDK 17 이므로 OK. 만약 bootstrap JAR 이 Java 21 만 지원하는 Kotlin-stdlib 을 재참조하면 link error. W2D6 에서 검증됨.

---

## Out of Scope (carry to W3+)

- 커스텀 뷰 (ConstraintLayout, MaterialButton) 렌더 — L3 DexClassLoader pipeline 에서 해소.
- 멀티 fixture 지원 (activity_basic.xml 의 custom view 경로) — L3.
- pom-resolved transitive (POST-W2D6-POM-RESOLVE).
- classloader parent-first 영향 audit (W3-CLASSLOADER-AUDIT).
- SSE FULL taxonomy / HEAD mapping — W4 스쿠프.
- Paparazzi-style fontScale/locale matrix — W4+.
