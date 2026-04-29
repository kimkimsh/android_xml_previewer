# W3-RESOURCE-VALUE-LOADER — Design Spec

**Date**: 2026-04-23 (W2 Day 8 / W3 Day 1)
**Scope reference**: `docs/plan/08-integration-reconciliation.md §7.7.2` 3b-values
**Session ancestry**: `docs/work_log/2026-04-23_w2d7-rendersession/handoff.md §2.2.A`
**Brainstorming scope choice**: B (Material-theme chain, 8 XML) + α (pre-populate parent) + T1 (SUCCESS gate)

---

## 1. Problem

W2D7 3b-arch 완료로 `Bridge.createSession(SessionParams) → RenderSession.render(timeout)` 경로가
실제 layoutlib inflate 단계까지 도달함을 증명했다. 그러나 tier3 fixture `activity_minimal.xml` 의
렌더는 `ERROR_INFLATION` 에서 중단된다. 원인은 `android.content.res.Resources_Delegate.getDimensionPixelSize()`
가 프레임워크 dimen `config_scrollbarSize` (0x10500DD) 의 실제 VALUE 를 `RenderResources` 에서
찾지 못하는 것이다.

`Bridge.init` 은 R.class reflection 으로 **프레임워크 리소스 ID ↔ name 매핑만** 로드하고,
**실제 값 (dimen 크기, color 값, style item) 은 로드하지 않는다**. 이 값들은 layoutlib 이
외부 `RenderResources` 구현을 통해 받아야 한다. 본 spec 은 해당 loader 를 설계한다.

---

## 2. Scope (Option B — Material-theme chain)

**파싱 대상** (`server/libs/layoutlib-dist/android-34/data/res/values/`):
1. `config.xml` — `config_scrollbarSize` 등 config dimens.
2. `colors.xml` — 프레임워크 기본 색상 팔레트.
3. `colors_material.xml` — Material 팔레트.
4. `dimens.xml` — 프레임워크 기본 치수.
5. `dimens_material.xml` — Material 치수.
6. `themes.xml` — 루트 `Theme` 및 기본 Theme 체인. **체인 완결성에 필수** (Theme.Material → Theme).
7. `styles.xml` — 루트 `TextAppearance`, `Widget` 등 프레임워크 기본 스타일. **TextView 체인 완결성에 필수**.
8. `themes_material.xml` — Theme.Material.Light.NoActionBar 포함 체인.
9. `styles_material.xml` — TextAppearance.Material 등 Material 스타일.
10. `attrs.xml` — attr 카탈로그 (format 정보). ~10K 줄이나 JVM-lifetime 캐시이므로 1회 비용.
    **중요** (W3D1 pair-review F1 확인): 실 `attrs.xml` 은 top-level `<attr>` 이 0 개이고 모든
    attr 이 `<declare-styleable>` 내부에 nested 로 존재. parser 는 양쪽 경로 모두에서 AttrDef 를
    수집해야 하며, 동일 name 중복 시 first-wins 로 dedupe. nested attr 을 skip 하면 `?attr/...`
    lookup 이 모두 null 반환 → 런타임 `ERROR_INFLATION`.

**스코프 확장 근거**: W2D7 브레인스토밍 시 8 파일로 예상했으나 실측 시 `themes.xml`/`styles.xml`
가 `themes_material.xml`/`styles_material.xml` 과 분리되어 있고 루트 `Theme`/`TextAppearance` 를
담고 있음 (`grep <style name="Theme"> values/themes.xml` 확인). 생략 시 `Theme.Material`
의 parent chain 이 bundle 내에서 끊어짐 → layoutlib 의 ?attr lookup 이 null 반환 → 새로운
ERROR_INFLATION 발생 가능. 체인 완결성 보장을 위해 10 파일 포함.

**의도적으로 제외**:
- `values-*/` qualifier 폴더 (ldpi/hdpi/xhdpi/night/sw720dp 등). activity_minimal 의 기본
  Configuration (en/xhdpi/portrait/phone_normal/NORMAL_DAY) 이 `values/` base 매칭이므로 충분.
- `arrays.xml`, `strings.xml` 등 Material 체인 밖 파일. YAGNI — 필요 시 carry 로 분할.
- `public` tag — Tier3 범위 밖.
- `declare-styleable` — element 자체는 resource 로 집계 안 함. 단, **내부 `<attr>` 은 수집** (F1).

**제공되는 RenderResources API 표면**:
- `getStyle(ResourceReference): StyleResourceValue`
- `getUnresolvedResource(ResourceReference): ResourceValue`
- `getResolvedResource(ResourceReference): ResourceValue`
- `getDefaultTheme(): StyleResourceValue`

---

## 3. Architecture

### 3.1 모듈 배치

```
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/
  resources/                             [NEW subpackage]
    ResourceLoaderConstants.kt           // VALUES_DIR="values", 파일명 8종 상수
    FrameworkValueParser.kt              // single XML → ParsedEntry list (kxml2)
    FrameworkResourceBundle.kt           // immutable aggregate (type→name→value)
    FrameworkResourceValueLoader.kt      // 8 XML 로드 + bundle 빌드 + JVM cache
    FrameworkRenderResources.kt          // RenderResources subclass (bundle delegate)
    StyleParentInference.kt              // dotted-prefix convention

  session/
    MinimalFrameworkRenderResources.kt   // [REMOVED] — FrameworkRenderResources 로 교체
    SessionParamsFactory.kt              // resources = FrameworkRenderResources.create(...)
```

### 3.2 핵심 클래스 책임

| 클래스 | 책임 | 외부 의존 |
|---|---|---|
| `ResourceLoaderConstants` | `VALUES_DIR`, `FILE_CONFIG`, `FILE_COLORS` 등 path 상수. CLAUDE.md "Zero Tolerance" 준수. | — |
| `FrameworkValueParser` | 단일 XML → `List<ParsedEntry>` 변환. parsing-only (refs 해석 X). `<dimen>`, `<color>`, `<bool>`, `<integer>`, `<string>`, `<item>` (in style), `<style>`, `<attr>` 인식. | `kxml2` |
| `StyleParentInference` | pure function `(styleName, explicitParent) → String?`. explicit 존재 시 그대로(ref form 정규화 후). explicit 이 null/빈 문자열 → dotted-prefix (Theme.Material.Light.NoActionBar → Theme.Material.Light). style 이름에 점 없음 → null (루트). **bundle 존재 여부는 확인하지 않음** — 이는 Loader 의 post-process 단계 책임. | — |
| `FrameworkResourceBundle` | immutable value object. `byType: Map<ResourceType, Map<String, ResourceValue>>`, `styles: Map<String, StyleResourceValue>`, `attrs: Map<String, AttrResourceValue>`. lookup API. | layoutlib-api types |
| `FrameworkResourceValueLoader` | static `loadOrGet(dataDir: Path): FrameworkResourceBundle`. `ConcurrentHashMap<Path, Bundle>` JVM-wide cache. `@VisibleForTesting clearCache()`. | parser, inference |
| `FrameworkRenderResources` | `RenderResources()` subclass. 4 override: `getStyle`, `getUnresolvedResource`, `getResolvedResource`, `getDefaultTheme`. `findResValue`는 W2D7 L3 landmine 에 따라 override 금지. | bundle, `SessionConstants.DEFAULT_FRAMEWORK_THEME` |

### 3.3 인스턴스 수명

- `FrameworkResourceBundle` — JVM 생애 (layoutlib dist 불변).
- `FrameworkRenderResources` — per-session (SessionParams 에 전달). 경량 wrapper (bundle 참조만).
- `FrameworkValueParser` — stateless. object 또는 instance-per-call 자유.

---

## 4. 데이터 흐름

```
LayoutlibRenderer.renderViaLayoutlib(layoutName):
  1. parser = LayoutPullParserAdapter.fromFile(fixtureRoot / layoutName)
  2. bundle = FrameworkResourceValueLoader.loadOrGet(distDir / "data")  [NEW]
  3. resources = FrameworkRenderResources(bundle, DEFAULT_FRAMEWORK_THEME)  [NEW]
  4. params = SessionParamsFactory.build(parser, resources=resources)
  5. session = bridge.createSession(params)  (reflection, 기존)
  6. result = session.render(TIMEOUT_MS)
  7. session.image → PNG bytes
```

**Bundle 구성 흐름** (loader 내부):
```
loadOrGet(dataDir):
  cache.computeIfAbsent(dataDir) {
    valuesDir = dataDir / "values"
    files = [config.xml, colors.xml, colors_material.xml,
             dimens.xml, dimens_material.xml,
             themes.xml, styles.xml,
             themes_material.xml, styles_material.xml,
             attrs.xml]                                 // 10 XML
    entries = files.flatMap { FrameworkValueParser.parse(it) }
    FrameworkResourceBundle.build(entries, styleParentInference)
      // post-process: 각 style 의 parentName 이 bundle 내 존재 여부 확인.
      //   존재 X → warning + parentName=null (chain 끊김 but layoutlib 은 다른 대안 시도)
  }
```

---

## 5. 에러 처리

- **파일 누락** → `IllegalStateException("필수 프레임워크 리소스 XML 누락: <path>")`. fail-fast. fallback 금지 (W2D7 L3 landmine).
- **XML 파싱 에러** → `IllegalStateException` with 파일명 + 라인 + 원인. stderr 에도 dump.
- **인식 못 하는 tag** → stderr warning, skip (forward-compat). `<public>`, `<declare-styleable>`, `<eat-comment>` 등이 여기 해당.
- **style parent 자체가 없는 경우** (e.g. parent="NonExistent") → Bundle 빌드 시 warning, style 은 parent=null 로 기록 (layoutlib 이 chain 중단).
- **`FrameworkRenderResources.override` 금지 목록**:
  - `findResValue()` — W2D7 시도1 에서 프레임워크 lookup 을 가로채 inflate 실패 유발. 영구 금지.

---

## 6. 테스트 전략

### 6.1 신규 unit tests

서브디렉토리: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/`

| Suite | 테스트 개수 | 범위 |
|---|---|---|
| `StyleParentInferenceTest` | ~5 | explicit / dotted-prefix / root (점 없음) / 빈 string / 비정상 이름 |
| `FrameworkValueParserTest` | ~10 | dimen/color/integer/bool/string/item/style/attr 각각 + 에러 케이스 + unknown tag skip |
| `FrameworkResourceValueLoaderTest` | ~6 | 10 XML 통합 로드 / cache 재사용 / clearCache / 파일 누락 실패 / bundle post-process (존재 X parent 처리) |
| `FrameworkRenderResourcesTest` | ~6 | getStyle / getResolvedResource / getUnresolvedResource / getDefaultTheme / findResValue override 부재 확인 |

**예상 신규 unit 수**: ~27 (기존 65 + 27 = **92 unit**).

### 6.2 Tier3 integration

- **`tier3-arch`** — 전환 필요 (canonical flip):
  - expected: `{SUCCESS}` (was `{ERROR_INFLATION, ERROR_UNKNOWN, ERROR_RENDER}`)
  - rejected: `{ERROR_NOT_INFLATED, ERROR_INFLATION}` (was `{SUCCESS, ERROR_NOT_INFLATED}`)
  - 의미: 3b-values 가 성공했으면 3b-arch 도 여전히 유효 (stronger assertion).
- **`tier3-values`** — @Disabled 제거. assertion 세트 (T1):
  - `result.isSuccess == true`
  - `img.width == SessionConstants.RENDER_WIDTH_PX && img.height == HEIGHT`
  - `PNG bytes > 1000`
- **`tier3-glyph`** (신규, `@Disabled`, W4+ carry):
  - handoff 원문 기준: PNG ≥ 10_000 + TextView 영역 dark pixel ≥ 20.
  - Font wiring 필요 여부를 독립 gate 로 분리 — T1 실패 시 즉시 블록킹, T1 성공 후 별 carry.

### 6.3 TDD 순서 (red → green 단위)

```
R1. StyleParentInferenceTest       (pure function, 외부 의존 0)
R2. FrameworkValueParserTest        (XML 입력만)
R3. FrameworkResourceValueLoaderTest (cache + file IO)
R4. FrameworkRenderResourcesTest    (bundle delegate)
R5. tier3-arch 업데이트 + tier3-values unblock (integration)
```

각 R 은 red 작성 → green 구현 → build 통과 확인 (unit → integration 순).

---

## 7. 리스크와 carry

### 7.1 resolved in this session (W2D7 페어 carry)

- **C2 (null-parent theme)** — 해소. `FrameworkRenderResources(bundle, DEFAULT_FRAMEWORK_THEME)` + bundle 에 실제 parent chain 존재.

### 7.2 deferred (W3+ carry 유지)

- **N2 (@Volatile public var Result leak)** — `W3-CLASSLOADER-AUDIT` (§7.7.1 F-4) 에서 DTO 경계 합류.
- **N3 (중복 NoopLogHandler)** — low-priority refactor.
- **N4 (forkEvery=1 integration 만)** — 현재 유지.
- **N5 (Callback thread-safety test)** — defer (worker 는 single-thread).

### 7.3 새 리스크

- **R-α: kxml2 의존**. layoutlib-api 31.13.2 transitive 로 들어와 있는지 build 로 확인. 없으면 `runtimeOnly("net.sf.kxml:kxml2:2.3.0")` 추가. Paparazzi 동일 버전.
- **R-β: style parent ref form 정규화**. XML 에서 `parent="@android:style/Theme.Material"`, `parent="Theme.Material"`, `parent="@style/Theme.Material"` 세 가지 표기 공존. parser 가 모두 name-only 로 정규화 필요.
- **R-γ: attrs.xml 사이즈**. ~수천 attr → Bundle 메모리 footprint 측정. JVM 생애 보유이므로 MB 단위이면 재검토.

### 7.4 scope 밖

- `values-*/` qualifier 매칭 — 필요 시 W4+ `W4-QUALIFIER-RESOLVER` carry.
- font wiring (tier3-glyph) — 위 §6.2 참조.
- project (앱) 리소스 로더 — 본 spec 은 **framework only**. 앱 res 는 W3-L3-DEXCLASSLOADER 와 별도.

---

## 8. 예상 LOC

| 영역 | LOC |
|---|---|
| `resources/` production code (6 classes) | 600 ~ 800 |
| resources/ + tier3 테스트 | 300 ~ 400 |
| SessionParamsFactory / LayoutlibRenderer 수정 | 20 ~ 50 |
| **총계** | **920 ~ 1250** |

handoff 예상 500-1000 LOC 에 상단 일치.

---

## 9. 완료 기준 (Definition of Done)

- [ ] 92 unit 전량 PASS, 5+1 integration PASS (tier3-arch SUCCESS + tier3-values SUCCESS).
- [ ] `server/gradlew -p server build` green.
- [ ] `axp-server-*.jar --smoke` OK.
- [ ] `docs/plan/08-integration-reconciliation.md` 에 `§7.7.3` 추가 — 3b-values CLOSED + tier3-glyph 신규 carry 기록.
- [ ] `docs/work_log/2026-04-23_w3d1-resource-value-loader/` (신규 폴더) 에 session-log + handoff + next-session-prompt.
- [ ] 1:1 Claude+Codex 최종 페어 리뷰 GO_WITH_FOLLOWUPS 이상.

---

## 10. 비전향 원칙 (no-change)

- `@Volatile` public var Result API 는 수정하지 않음 (N2 carry 유지).
- `LayoutlibBootstrap.createIsolatedClassLoader()` 는 수정하지 않음 (W3-CLASSLOADER-AUDIT 범위).
- `activity_minimal.xml` fixture 는 수정하지 않음 (스토리 stable).

---

## Appendix A — 샘플 config.xml `<dimen>` 파싱 흐름

```xml
<resources>
  <dimen name="config_scrollbarSize">4dp</dimen>
</resources>
```

→ `ParsedEntry(type=DIMEN, name="config_scrollbarSize", value="4dp")`
→ `ResourceValueImpl(namespace=ANDROID, type=DIMEN, name="config_scrollbarSize", value="4dp")`
→ `bundle.byType[DIMEN]["config_scrollbarSize"] = <above>`
→ layoutlib 에서 `Resources_Delegate.getDimensionPixelSize(0x10500DD)`:
   - Bridge 내부: sRMap[0x10500DD] → "config_scrollbarSize" (이미 있음).
   - RenderResources.getUnresolvedResource(REF(ANDROID, DIMEN, "config_scrollbarSize")) → "4dp".
   - 1dp × 320 dpi / 160 = 2px, ×4 = **8px**. 반환.

---

## Appendix B — Theme.Material 체인 예시

```xml
<style name="Theme.Material.Light.NoActionBar" parent="Theme.Material.Light">
  <item name="windowActionBar">false</item>
  <item name="windowNoTitle">true</item>
</style>
<style name="Theme.Material.Light" parent="Theme.Material">
  <item name="colorPrimary">@color/material_blue_grey_800</item>
  ...
</style>
<style name="Theme.Material">   <!-- no explicit parent -->
  <item name="textColorPrimary">?attr/textColorPrimary</item>
  ...
</style>
```

→ Inference 는 순수 함수: `parentInference("Theme.Material", explicit=null)` = `"Theme"`
   (dotted-prefix 규칙, bundle 조회 없이).
→ Loader post-process 가 bundle 내 `"Theme"` 존재 여부 확인. `themes.xml` 이 파싱 대상에 포함
   (§2 스코프) 되므로 루트 `<style name="Theme">` 이 bundle 에 존재 → chain 유지.
→ 만약 bundle 에 없다면 warning 출력 + parentName=null (chain 끊김, layoutlib 이 루트로 간주).

Paparazzi 의 framework style 은 대부분 explicit parent 를 가지므로 inference 는 safety net.
Inference 가 순수 함수 (bundle 의존 X) 라는 것이 중요 — 테스트 가능성 + 단일 책임.

---

END of spec.
