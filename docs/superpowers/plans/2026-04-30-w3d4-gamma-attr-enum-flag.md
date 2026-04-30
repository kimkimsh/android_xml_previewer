# W3D4-γ plumbing plan v3.1 — AttrDef enum/flag value capture

생성: 2026-04-30 (W3D4-β `0ea4f8c` 직후)
선행 head: `0ea4f8c` (main, W3D4-β session-log + γ handoff)
phase: **W3D4-γ plumbing** (AttrDef enum/flag capture, framework + RES_AUTO 2-surface)
범위: T14 (γ-A.2 RES_AUTO path) · T15 (γ-A.1 framework Bridge.init wiring) · T16 (acceptance gate close) — plan v3 의 T11/T12/T13 자료구조는 변경 없음, 본 plan 은 그 위에 attr child capture layer 만 추가.
선행 plan: `docs/superpowers/plans/2026-04-30-w3d4-beta-plumbing.md` (plan v3, T11/T12/T13)

---

## §0 Entry context (cold-start safe)

### §0.1 작업 흐름의 위치

- W3D4-β plan v3 (T11/T12/T13) 적용 완료 — Gap A (Material ThemeEnforcement sentinel attr namespace mismatch) + Gap B (color XML state list feed) 닫힘.
- W3D4-β acceptance gate (`tier3-basic-primary` SUCCESS) 는 plan v3 §5.4 의 escalation 정책 정확히 적중 — fail surface 가 새 layer (`<attr>` 의 `<enum>/<flag>` 자식 미캡처) 로 shift. T13 partial commit 으로 cache invalidation 만 적용 + primary IT 재 `@Disabled` (W3D4-γ-A 진단).
- 본 plan v3.1 은 그 새 fail surface 를 닫음. round 3 의 explicit-diff 정책 (LM-W3D4-D 회피) 일관 유지.

### §0.2 본 plan v3.1 가 변경하지 않는 것 (불변식)

- `LayoutlibResourceBundle` 의 byNs 2-bucket (ANDROID + RES_AUTO) 구조.
- `LayoutlibRenderResources` 의 chain walker (`MAX_REF_HOPS=10`, `MAX_THEME_HOPS=32`).
- `RJarSymbolSeeder` 의 RES_AUTO 통일 정책 (T11).
- `AarResourceWalker` 의 color state list 수집 + `MinimalLayoutlibCallback.getParser` wiring (T12).
- `LayoutlibResourceValueLoader` 의 3-입력 통합 + `clearCache()` BeforeEach (T13).
- `MaterialFidelityIntegrationTest` 4/4 PASS 의 entry criterion.

### §0.3 baseline (2026-04-30 측정, 본 plan 작성 시점)

- `cd server && ./gradlew test --console=plain` → BUILD SUCCESSFUL, **215 unit total** (172 layoutlib-worker + 16 protocol + 5 http-server + 22 mcp-server) / 0 fail.
- `cd server && ./gradlew :layoutlib-worker:test -PincludeTags=integration` → **14 IT PASS + 2 SKIP** (`tier3-basic-primary` W3D4-γ carry, `tier3-glyph` W4 carry).
- 현재 branch: `main`, head `0ea4f8c`.

---

## §1 Empirical findings (round 4 의 baseline 입력)

### §1.1 layoutlib `BridgeTypedArray.resolveEnumAttribute` 의 2-path 분기

`com.android.layoutlib.bridge.android.BridgeContext.internalObtainStyledAttributes` → `BridgeTypedArray.resolveEnumAttribute(int)` 가 enum/flag 문자열을 정수로 변환. 변환 실패 시 `ILayoutLog.warning("\"%1$s\" in attribute \"%2$s\" is not a valid integer", ...)` 발화 (layoutlib-14.0.11.jar `BridgeTypedArray.class` bytecode offset 32-56 에서 확인).

해당 메서드의 path 분기 (bytecode 검증):

| 분기 | namespace | 데이터 소스 |
|---|---|---|
| **A — framework** | `ResourceNamespace.ANDROID` | `Bridge.sEnumValueMap` (static `Map<String, Map<String, Integer>>`, `Bridge.init()` 의 6번째 인자로 주입) |
| **B — non-framework** | RES_AUTO (또는 그 외) | `BridgeContext.getRenderResources().getResolvedResource(ref)` → `instanceof AttrResourceValue` cast → `getAttributeValues(): Map<String, Integer>` |

→ 두 path 가 **완전히 분리**. A 는 정적 맵, B 는 런타임 RenderResources 위임. 한 쪽만 채워서는 `tier3-basic-primary` 의 3 warning 모두 닫지 못함.

**round 4 reconcile (Codex Q1 NUANCED)**: `android.util.BridgeXmlPullAttributes.getAttributeIntValue(namespace, name, default)` 도 sibling enum consumer — bytecode offsets 75-85 (framework supplier `Bridge.getEnumValues`), offsets 104-116 + 515-529 (project supplier `RenderResources.getUnresolvedResource(ref) → instanceof AttrResourceValue → getAttributeValues()`). 즉 RES_AUTO path 에서 **`getResolvedResource` 와 `getUnresolvedResource` 둘 다** AttrResourceValue 를 반환해야 — Q5 fix 가 양쪽 entry point 를 모두 cover 해야 한다. `Resources_Delegate.getColor` / `ResourceHelper.getColor` 는 enum 미경유 (단지 `#...` 색 파싱), 이 plan 영향권 밖.

### §1.2 현재 코드의 fail point 2 곳

**Path A — framework path 의 fail (orientation/gravity)**:
- `LayoutlibRenderer.kt:122` — `val enumValueMap = mutableMapOf<String, MutableMap<String, Int>>()` (빈 map)
- 이 빈 map 이 `initMethod.invoke(..., enumValueMap, ...)` (line 128-137) 로 Bridge 에 전달 → `Bridge.sEnumValueMap` 도 비게 됨
- `<orientation>` (framework) 의 `vertical` 변환 시 sEnumValueMap 조회 → null → warning + fail.

**Path B — RES_AUTO path 의 fail (`layout_constraintEnd_toEndOf="parent"`)**:
- `ParsedNsEntry.AttrDef` (line 35-39) 가 `name/namespace/sourcePackage` 만 보존 — `<enum>/<flag>` 자식 정보 drop.
- `NamespaceAwareValueParser.kt:95-110` (top-level `<attr>`) + line 205-219 (declare-styleable nested `<attr>`) 모두 `skipElement(reader)` 로 자식 element 무시.
- `LayoutlibResourceBundle.kt:105-112` (buildBucket 의 AttrDef branch) 가 `AttrResourceValueImpl(ref, null)` 만 생성 — `addValue(name, value, description)` 호출 부재.
- `<layout_constraintEnd_toEndOf>` (RES_AUTO, ConstraintLayout AAR 정의) 의 `parent` 변환 시 `getAttributeValues()` 조회 → empty map → warning + fail.

### §1.3 `<attr>` 자식 census (실측)

framework `attrs.xml` (`/server/libs/layoutlib-dist/android-34/data/res/values/attrs.xml`):
- 총 `<attr name=` 발생: 2,196 (top-level 32 + declare-styleable 안 2,164 = 1,650 unique attr name)
- `<enum>` 자식 보유 attr: **104 unique**, 총 `<enum>` element **710** — 가장 큰 것 `keycode` (305), `pointerIcon` (23), `keyboardLayoutType` (9)
- `<flag>` 자식 보유 attr: **40 unique**, 총 `<flag>` element **276** — 가장 큰 것 `inputType` (33), `accessibilityEventTypes` (26), `imeOptions` (17), `gravity` (14)
- 동시에 enum + flag 보유 attr: **0** (XOR 보장 — parser 가 같은 map 에 섞을 위험 없음)
- value literal: 십진 (`value="1"`) + 십육진 (`value="0x30"`) 혼재 → `Integer.decode` 로 파싱 (둘 다 처리)

`tier3-basic-primary` 의 3 failing attr 분포:
- `orientation` → framework, enum (`horizontal=0`, `vertical=1`)
- `gravity` → framework, flag (14 children: `top=0x30`, `bottom=0x50`, `center_horizontal=0x01`, ...)
- `layout_constraintEnd_toEndOf` → **RES_AUTO** (ConstraintLayout-2.1.4.aar `res/values/values.xml` 의 `<attr name="layout_constraintEnd_toEndOf"><enum name="parent" value="0"/></attr>`)

→ A path 2건 + B path 1건 — 양쪽 fix 필수 확정.

### §1.4 W3D4-γ-B 무력화 — γ-A 가 subsume

이전 handoff §2.4 가 hypothesize 한 "ConstraintLayout 의 `parent` ID = `R$id.parent == 0`" 가설은 empirical 로 **틀림**:
- `fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar` 안 `androidx.constraintlayout.widget.R$id.parent` 의 실제 값 = **0x7f080158** (정상 ID, 0 아님).
- `RJarSymbolSeeder.kt:121-150` 도 value=0 skip 안 함 (`field.getInt(null)` 무조건 등록, R$styleable 만 skip).
- "parent" 의 정체는 ConstraintLayout AAR 의 `<attr name="layout_constraintEnd_toEndOf">` 가 **자체 enum 자식** `<enum name="parent" value="0"/>` 를 정의하는 것 — R$id 와 무관한 attr-local enum 값.

→ γ-A.2 (RES_AUTO path enum 캡처) 가 자동으로 "parent" 변환을 닫음. γ-B 는 **plan v3.1 scope 에서 제거**.

### §1.5 layoutlib API surface (bytecode 검증)

`com.android.ide.common.rendering.api.AttrResourceValueImpl` (layoutlib-api-31.13.2.jar):
- `public void addValue(java.lang.String name, java.lang.Integer value, java.lang.String description)` — value 가 boxed `java.lang.Integer` (kotlin `Int?` 또는 `Integer` 명시 boxing 필요)
- `public java.util.Map<java.lang.String, java.lang.Integer> getAttributeValues()` — null 반환 안 함, internal `valueMap` 이 null 이면 `Collections.emptyMap()` 반환
- 부모 인터페이스 `AttrResourceValue.getAttributeValues()` 동일 시그니처 — `instanceof AttrResourceValue` cast 로도 hit

`com.android.layoutlib.bridge.Bridge` (layoutlib-14.0.11.jar):
- `init(Map<String, String>, File, String, String, String[], Map<String, Map<String, Integer>>, ILayoutLog)` 의 6번째 인자 → `private static Map<String, Map<String, Integer>> sEnumValueMap` 에 직접 putstatic
- `Bridge.getEnumValues(String attrName)` 는 sEnumValueMap 의 외층 lookup → 내층 `Map<String, Integer>` 반환

---

## §2 Scope & task split (round 4 의 verdict 입력)

| Task | scope | path | 이전 plan 와의 관계 |
|---|---|---|---|
| **T14** | γ-A.2 RES_AUTO path | ParsedNsEntry.AttrDef + NamespaceAwareValueParser child loop + LayoutlibResourceBundle.buildBucket addValue | plan v3 의 자료구조 (ParsedNsEntry / NsBucket / Bundle) 위에 추가 |
| **T15** | γ-A.1 framework Bridge.init wiring | LayoutlibResourceBundle 신규 helper `frameworkEnumValueMap()` + LayoutlibRenderer.initBridge enumValueMap 주입 | T14 의 데이터 (bundle.byNs[ANDROID].attrs 의 enum/flag) 를 sEnumValueMap 으로 변환 |
| **T16** | acceptance gate 닫기 | tier3-basic-primary `@Disabled` 제거 시도 + (필요 시) escalation 분류 | plan v3 의 T13 와 동일 패턴 |

T14 → T15 dependency: T14 이 ParsedNsEntry.AttrDef 에 enumValues/flagValues 를 채우고 buildBucket 이 AttrResourceValueImpl.addValue 를 호출해야 T15 가 bundle 에서 그 값을 추출할 수 있음. 단, 두 task 의 commit 은 분리 (CLAUDE.md task-unit completion).

**γ-B 는 본 plan 미포함** (§1.4 empirical 무력화).

---

## §3 T14 — RES_AUTO path fix (AttrDef enum/flag capture)

### §3.1 변경 파일

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedNsEntry.kt`

**변경 — AttrDef 에 enumValues/flagValues 필드 추가 + 필드 순서 재배치**

```diff
     /**
      * `<attr name="X" format="Y" />` 선언 (namespace tagged).
-     * (W3D1 AttrDef 와 동일하게 top-level / declare-styleable 자식 모두 수집.)
+     * W3D4-γ T14: `<enum>/<flag>` 자식 값 테이블 (name → integer) 도 함께 보존.
+     * layoutlib BridgeTypedArray.resolveEnumAttribute 가 RES_AUTO 의 attr 값 변환 시
+     * AttrResourceValueImpl.getAttributeValues() 로 이 테이블을 조회 (framework 는 별도
+     * Bridge.sEnumValueMap 경로 — T15 가 처리).
+     *
+     * enumValues / flagValues 둘 다 `Map<String, Int>` (insertion order 보존). framework
+     * attrs.xml census 결과 한 attr 가 enum/flag 동시 보유는 0 — 한 쪽이 비면 명시 emptyMap().
      */
     data class AttrDef(
         val name: String,
         override val namespace: ResourceNamespace,
+        val enumValues: Map<String, Int>,
+        val flagValues: Map<String, Int>,
         override val sourcePackage: String? = null,
     ) : ParsedNsEntry()
```

**필드 순서 결정 근거**: CLAUDE.md "No default parameter values" 정책에 따라 신규 두 필드는 **default 없음** (모든 호출처가 `emptyMap()` 명시 제공). 기존 `sourcePackage: String? = null` 의 default 는 grandfathered (변경 안 함). 신규 필드를 sourcePackage 앞에 배치 → 명명 인자 없이도 위치 인자만으로 호출 가능 + 기존 5-arg 호출처가 reorder 영향 받음 (의도된 — 모든 site 가 신규 의미를 의식하도록 강제).

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParser.kt`

**변경 1 — 태그/속성 상수 추가** (Zero Tolerance for Magic Strings, CLAUDE.md):

```diff
     private const val TAG_DECLARE_STYLEABLE = "declare-styleable"
+    private const val TAG_ENUM = "enum"
+    private const val TAG_FLAG = "flag"

     private const val ATTR_NAME = "name"
     private const val ATTR_PARENT = "parent"
     private const val ATTR_TYPE = "type"
+    private const val ATTR_VALUE = "value"
```

**변경 2 — 신규 helper `parseAttrChildren`**:

`<attr>` element 의 START_ELEMENT 직후 호출 시점 (자식 enum/flag 만 수집, END_ELEMENT 까지 소비). depth-aware — `skipElement` 의 변종이지만 직속 자식 (depth==2 in 본 helper 의 local frame) 만 enum/flag 검사.

```diff
+    /**
+     * W3D4-γ T14: `<attr>` 자식 `<enum>/<flag>` 값 테이블 수집 (depth-aware).
+     * 호출 시점: reader 가 `<attr>` 의 START_ELEMENT 위치. helper 는 매칭 END_ELEMENT 까지
+     * 소비 — skipElement 와 동일 종료 의미론 + 직속 enum/flag 만 추출.
+     *
+     * value 는 십진 또는 십육진 literal (framework attrs.xml 기준 — 십육진 약 60% 비중).
+     * Integer.decode 로 통일 파싱 — `0` (decimal), `0x30` (hex), `030` (octal — 사용 사례 없으나
+     * Integer.decode 가 자동 처리). parse 실패 시 silently skip (broken AAR 의 회복력).
+     *
+     * 동시 보유 (enum + flag) 는 framework census 0 건 — 보수적으로 두 map 분리, mutual exclusion
+     * 강제는 안 함 (결과 map 둘 다 emit, 호출자가 의미 결정).
+     */
+    private fun parseAttrChildren(reader: XMLStreamReader): Pair<Map<String, Int>, Map<String, Int>>
+    {
+        val enums = LinkedHashMap<String, Int>()
+        val flags = LinkedHashMap<String, Int>()
+        var depth = 1
+        while (reader.hasNext() && depth > 0)
+        {
+            when (reader.next())
+            {
+                XMLStreamConstants.START_ELEMENT ->
+                {
+                    depth++
+                    if (depth == 2)
+                    {
+                        val tag = reader.localName
+                        if (tag == TAG_ENUM || tag == TAG_FLAG)
+                        {
+                            val childName = reader.getAttributeValue(null, ATTR_NAME)
+                            val rawValue = reader.getAttributeValue(null, ATTR_VALUE)
+                            if (childName != null && rawValue != null)
+                            {
+                                val parsed = parseAttrValueLiteral(rawValue)
+                                if (parsed != null)
+                                {
+                                    if (tag == TAG_ENUM) enums[childName] = parsed else flags[childName] = parsed
+                                }
+                            }
+                        }
+                    }
+                }
+                XMLStreamConstants.END_ELEMENT -> depth--
+            }
+        }
+        return enums to flags
+    }
+
+    /**
+     * W3D4-γ T14 (round 4 reconcile, Codex Q2 DISAGREE): `<enum value="..."/>` /
+     * `<flag value="..."/>` literal 파싱. framework attrs.xml 에 32-bit unsigned hex mask 가
+     * 실재 — `<flag name="flagForceAscii" value="0x80000000"/>` (attrs.xml:1703),
+     * `value="0xffffffff"` (attrs.xml:4078, 4101). `Integer.decode` 는 이 값들을
+     * `NumberFormatException` 발화 — 파싱이 silently 누락되면 framework Bridge.sEnumValueMap
+     * 의 mask flag 가 빠져 inflate 단계에서 cascade fail.
+     *
+     * `Long.decode` 로 64-bit 파싱 후 `.toInt()` 로 narrow → 32-bit signed/unsigned 양쪽 cover
+     * (예: `0x80000000` → Long `2147483648` → Int `Int.MIN_VALUE`,
+     *      `0xffffffff` → Long `4294967295` → Int `-1`).
+     * 음수 십진 (예: `<enum value="-1"/>` ConstraintLayout 의 `layout_constraintHeight`) 도 동일
+     * 처리. 예외 시 null (broken AAR resilience — 실측 41 AAR 안 decode_bad=3 모두 framework).
+     */
+    private fun parseAttrValueLiteral(raw: String): Int? = try
+    {
+        java.lang.Long.decode(raw.trim()).toInt()
+    }
+    catch (e: NumberFormatException)
+    {
+        null
+    }
```

**변경 3 — top-level `<attr>` branch 의 child 처리**:

기존 (line 95-110):
```kotlin
TAG_ATTR ->
{
    val name = reader.getAttributeValue(null, ATTR_NAME) ?: ""
    if (name.isNotEmpty() && !name.contains(NS_NAME_SEPARATOR_CHAR) && seenAttrNames.add(name))
    {
        entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
    }
    skipElement(reader)
}
```

신규:
```diff
                         TAG_ATTR ->
                         {
                             val name = reader.getAttributeValue(null, ATTR_NAME) ?: ""
+                            // W3D4-γ T14: `<enum>/<flag>` 자식 수집 — END_ELEMENT 까지 소비.
+                            // skipElement 보다 우선 — children 정보 추출 후에는 별도 skip 불필요.
+                            val (enums, flags) = parseAttrChildren(reader)
                             if (name.isNotEmpty() && !name.contains(NS_NAME_SEPARATOR_CHAR) && seenAttrNames.add(name))
                             {
-                                entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
+                                entries += ParsedNsEntry.AttrDef(name, namespace, enums, flags, sourcePackage)
                             }
-                            skipElement(reader)
                         }
```

**변경 4 — declare-styleable nested `<attr>` branch**:

기존 (line 205-218 in handleDeclareStyleable):
```kotlin
if (event == XMLStreamConstants.START_ELEMENT && reader.localName == TAG_ATTR)
{
    val name = reader.getAttributeValue(null, ATTR_NAME) ?: ""
    if (name.isNotEmpty() && !name.contains(NS_NAME_SEPARATOR_CHAR) && seen.add(name))
    {
        entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
    }
    skipElement(reader)
}
```

신규:
```diff
             if (event == XMLStreamConstants.START_ELEMENT && reader.localName == TAG_ATTR)
             {
                 val name = reader.getAttributeValue(null, ATTR_NAME) ?: ""
+                // W3D4-γ T14: nested `<attr>` 도 top-level 과 동일한 child capture 정책.
+                // declare-styleable 안의 `<attr>` 는 보통 styleable 의 entry — enum/flag 가
+                // declare-styleable 안에 정의되는 경우는 적지만 framework 에서 발생 가능.
+                val (enums, flags) = parseAttrChildren(reader)
                 if (name.isNotEmpty() && !name.contains(NS_NAME_SEPARATOR_CHAR) && seen.add(name))
                 {
-                    entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
+                    entries += ParsedNsEntry.AttrDef(name, namespace, enums, flags, sourcePackage)
                 }
-                skipElement(reader)
             }
```

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt`

**변경 1 (round 4 reconcile, Codex Q5 DISAGREE — KILL POINT) — `getResource` 에 ATTR special-case**:

`BridgeTypedArray.resolveEnumAttribute` 의 path B 와 `BridgeXmlPullAttributes.getAttributeIntValue` 의 project supplier 가 모두 `RenderResources.getResolvedResource(...)`/`getUnresolvedResource(...)` → 우리 `LayoutlibRenderResources.kt:138-142` 가 `bundle.getResource(reference)` 호출. 그런데 `LayoutlibResourceBundle.kt:42-43` 의 현재 구현 = `byNs[ref.namespace]?.byType?.get(ref.resourceType)?.get(ref.name)` — `byType` 만 조회. AttrDef 는 `attrsMut` (별도 map) 에만 저장 (NsBucket.kt:17-21 분리). 결과: `getResource(ATTR ref)` → null → instanceof cast fail → "is not a valid integer" warning **여전히 발화**. T14 의 addValue 만으로는 acceptance gate 닫지 못함.

```diff
-    fun getResource(ref: ResourceReference): ResourceValue? =
-        byNs[ref.namespace]?.byType?.get(ref.resourceType)?.get(ref.name)
+    fun getResource(ref: ResourceReference): ResourceValue?
+    {
+        val bucket = byNs[ref.namespace] ?: return null
+        // W3D4-γ T14 (round 4 Codex Q5 fix): ATTR ref 는 byType 가 아닌 attrs map 에서 lookup.
+        // BridgeTypedArray.resolveEnumAttribute / BridgeXmlPullAttributes.getAttributeIntValue
+        // 의 (un)resolvedResource 위임 path 가 AttrResourceValueImpl 인스턴스 자체를 instanceof
+        // 로 cast — byType 만 거치면 attrs 의 enum/flag 테이블에 도달 불가.
+        if (ref.resourceType == ResourceType.ATTR)
+        {
+            return bucket.attrs[ref.name]
+        }
+        return bucket.byType[ref.resourceType]?.get(ref.name)
+    }
```

**변경 2 — buildBucket 의 AttrDef branch 에 addValue 호출**:

기존 (line 105-113):
```kotlin
is ParsedNsEntry.AttrDef ->
{
    if (!attrsMut.containsKey(e.name))
    {
        val ref = ResourceReference(ns, ResourceType.ATTR, e.name)
        attrsMut[e.name] = AttrResourceValueImpl(ref, null)
    }
}
```

신규:
```diff
                 is ParsedNsEntry.AttrDef ->
                 {
                     if (!attrsMut.containsKey(e.name))
                     {
                         val ref = ResourceReference(ns, ResourceType.ATTR, e.name)
-                        attrsMut[e.name] = AttrResourceValueImpl(ref, null)
+                        val attr = AttrResourceValueImpl(ref, null)
+                        // W3D4-γ T14: `<enum>/<flag>` 자식 값 테이블을 AttrResourceValueImpl 에 주입.
+                        // BridgeTypedArray.resolveEnumAttribute 가 RES_AUTO attr 변환 시
+                        // getAttributeValues().get("vertical") 등으로 조회. value 는 boxed Integer.
+                        for ((enumName, enumValue) in e.enumValues)
+                        {
+                            attr.addValue(enumName, enumValue, null)
+                        }
+                        for ((flagName, flagValue) in e.flagValues)
+                        {
+                            attr.addValue(flagName, flagValue, null)
+                        }
+                        attrsMut[e.name] = attr
+                    }
+                    else
+                    {
+                        // first-wins (W3D1 정책) — 두 번째 등록 시도는 기존 attr 의 enum/flag 보존.
+                        // 만약 첫 등록이 비어있고 두 번째에 enum/flag 가 있다면 명시적 merge 권장.
+                        // 현재는 silent (W3D1 first-wins 정합) — round 4 review 에서 재검토 가능.
                     }
                 }
```

**merge 정책 결정 근거 (round 4 reconcile, Codex Q3 NUANCED — empirical 정정)**: 41 fixture AAR scan 실측:
- `RES_AUTO_ATTR_DUP_ALL duplicate_names=213` (동명 attr 등장)
- `conflicting_names=56` (다른 shape 보유)
- `empty_vs_nonempty=56` (한 쪽이 empty body, 다른 쪽이 enum/flag children 보유)
- `nonempty_vs_nonempty=0` (서로 다른 enum/flag 테이블 진짜 충돌 = 0)
- `FIRST_EMPTY_LATER_NONEMPTY count=0` (empty 먼저 등록 후 nonempty 등장 = 0)

실 conflict 사례:
- `appcompat-1.6.1.aar res/values/values.xml:2558-2576` 가 `iconTintMode` enum 정의 → `material-1.12.0.aar` 가 `:7828` 에 빈 `iconTintMode` (선 등록 nonempty 가 보존)
- `constraintlayout-2.1.4.aar:154-156` 가 `layout_constraintEnd_toEndOf` enum 정의 → 후속 declare-styleable 에서 `:483, :646, :944, :1226` 에 빈 ref (역시 nonempty 가 우선)

→ 현 first-wins 가 acceptance gate 안전 — empirical 으로 nonempty 가 항상 먼저 등장 (`runtime-classpath.txt` 의 결정적 순서 + AAR 안 top-level `<attr>` 가 declare-styleable 보다 앞에 위치). future hardening (existing empty + incoming nonempty 시 merge/replace) 은 W4+ scope, **본 plan 의 T16 PASS 에 영향 없음**.

### §3.2 신규 단위 테스트 (T14.tests, ~10 cases)

#### `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AttrEnumFlagCaptureTest.kt` (신규)

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class AttrEnumFlagCaptureTest
{
    @Test
    fun `top-level attr with enum children — values map populated`()
    {
        val xml = """
            <resources>
              <attr name="orientation">
                <enum name="horizontal" value="0"/>
                <enum name="vertical" value="1"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals("orientation", attr.name)
        assertEquals(mapOf("horizontal" to 0, "vertical" to 1), attr.enumValues)
        assertEquals(emptyMap<String, Int>(), attr.flagValues)
    }

    @Test
    fun `top-level attr with flag children — hex values parsed`()
    {
        val xml = """
            <resources>
              <attr name="gravity">
                <flag name="top" value="0x30"/>
                <flag name="center_horizontal" value="0x01"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(mapOf("top" to 0x30, "center_horizontal" to 0x01), attr.flagValues)
        assertEquals(emptyMap<String, Int>(), attr.enumValues)
    }

    @Test
    fun `attr inside declare-styleable — children captured`()
    {
        val xml = """
            <resources>
              <declare-styleable name="MyView">
                <attr name="layoutDirection">
                  <enum name="ltr" value="0"/>
                  <enum name="rtl" value="1"/>
                </attr>
              </declare-styleable>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals("layoutDirection", attr.name)
        assertEquals(mapOf("ltr" to 0, "rtl" to 1), attr.enumValues)
    }

    @Test
    fun `attr without children — both maps empty`()
    {
        val xml = """<resources><attr name="customRef" format="reference"/></resources>"""
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(emptyMap<String, Int>(), attr.enumValues)
        assertEquals(emptyMap<String, Int>(), attr.flagValues)
    }

    @Test
    fun `unparseable value — silently skipped`()
    {
        val xml = """
            <resources>
              <attr name="weird">
                <enum name="ok" value="5"/>
                <enum name="broken" value="not_a_number"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(mapOf("ok" to 5), attr.enumValues)
    }

    @Test
    fun `cross-NS attr name — still skipped (T8 fix preserved)`()
    {
        val xml = """
            <resources>
              <attr name="android:visible"><enum name="x" value="1"/></attr>
              <attr name="local"><enum name="y" value="2"/></attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val names = entries.filterIsInstance<ParsedNsEntry.AttrDef>().map { it.name }
        assertEquals(listOf("local"), names)
    }

    @Test
    fun `enum value 0 — captured (ConstraintLayout parent literal)`()
    {
        val xml = """
            <resources>
              <attr name="layout_constraintEnd_toEndOf">
                <enum name="parent" value="0"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(mapOf("parent" to 0), attr.enumValues)
    }

    @Test
    fun `flag value 0x80000000 — Long-decode handles unsigned 32-bit hex`()
    {
        val xml = """
            <resources>
              <attr name="inputMethodFlags">
                <flag name="forceAscii" value="0x80000000"/>
                <flag name="all" value="0xffffffff"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        // Long.decode(0x80000000).toInt() == Int.MIN_VALUE; (0xffffffff).toInt() == -1
        assertEquals(mapOf("forceAscii" to Int.MIN_VALUE, "all" to -1), attr.flagValues)
    }

    @Test
    fun `negative decimal value — Long-decode handles negative literals`()
    {
        val xml = """
            <resources>
              <attr name="layout_constraintHeight">
                <enum name="match_constraint" value="-1"/>
              </attr>
            </resources>
        """.trimIndent()
        val entries = parseXml(xml)
        val attr = entries.filterIsInstance<ParsedNsEntry.AttrDef>().single()
        assertEquals(mapOf("match_constraint" to -1), attr.enumValues)
    }

    private fun parseXml(content: String): List<ParsedNsEntry>
    {
        val tmp = Files.createTempFile("attr-enum-flag", ".xml")
        Files.writeString(tmp, content)
        return NamespaceAwareValueParser.parse(tmp, ResourceNamespace.RES_AUTO, "test")
    }
}
```

#### `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleAttrValuesTest.kt` (신규)

5 cases (round 4 reconcile, Codex Q5 acceptance-critical 추가):
- `buildBucket - AttrDef with enumValues populates AttrResourceValueImpl.getAttributeValues`
- `buildBucket - AttrDef with flagValues populates same map (no separate flag accessor in layoutlib)`
- `buildBucket - AttrDef with empty enum/flag — getAttributeValues returns emptyMap`
- `getResource - ATTR ref returns AttrResourceValueImpl with populated values` (Q5 fix 회귀 가드 — `bundle.getResource(ResourceReference(RES_AUTO, ATTR, "layout_constraintEnd_toEndOf"))` 가 enum 테이블 보유한 AttrResourceValue 반환)
- `getResource - non-ATTR ref still uses byType bucket` (special-case 가 다른 type 영향 없음)

#### `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibRenderResourcesAttrLookupTest.kt` (신규, round 4 Codex Q5 acceptance-critical)

3 cases — `RenderResources` chain의 양쪽 entry point 가 AttrResourceValue 반환 검증 (BridgeTypedArray + BridgeXmlPullAttributes 모두 cover):
- `getResolvedResource - RES_AUTO ATTR returns AttrResourceValue with parent enum` (BridgeTypedArray.resolveEnumAttribute path)
- `getUnresolvedResource - RES_AUTO ATTR returns AttrResourceValue with parent enum` (BridgeXmlPullAttributes.getAttributeIntValue path)
- `getResolvedResource - ANDROID ATTR also exposed` (framework path 도 동일 surface — T15 의 enumValueMap 이 sibling cover 하지만 통합 회귀 검사)

#### 기존 테스트 영향 (round 4 reconcile, Codex Q6 명시)

- `NamespaceAwareValueParserTest.kt` — 모든 `filterIsInstance<ParsedNsEntry.AttrDef>` 검사는 변경 없음 (시그니처 추가 없음). 수치적 enum/flag 검증 추가는 본 신규 file 에서.
- `LayoutlibResourceBundleTest.kt:79-80` — 명시적 갱신 필요. 현재 `ParsedNsEntry.AttrDef("colorPrimary", ResourceNamespace.RES_AUTO, "first")` (3-arg) 형식 → 5-arg `ParsedNsEntry.AttrDef("colorPrimary", ResourceNamespace.RES_AUTO, emptyMap(), emptyMap(), "first")` 로 변환. 신규 enum/flag 필드는 dedup test 의 의미에 영향 없음 (empty map).

### §3.3 IT 영향 (T14 단독 적용 후)

- `MaterialFidelityIntegrationTest` 4/4 — 회귀 검사. AttrDef 의 첫 4 인자 (name/namespace/enums/flags) 가 모두 채워져 builder 동작.
- `LayoutlibRendererTier3MinimalTest` 14 PASS — RES_AUTO attr 변환 path 가 채워짐. constraint 의 `parent` 변환은 T14 단독으로 닫힘 가능 (실측 후 확정).
- `tier3-basic-primary` (현재 SKIP) — T14 단독으로는 framework path (orientation/gravity) 미해결 → 여전히 fail. T15 후 측정.

---

## §4 T15 — Framework path Bridge.init enumValueMap wiring

### §4.1 변경 파일

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt`

**변경 — framework attrs 를 Bridge.init 형식 (`Map<String, Map<String, Integer>>`) 로 export**:

```diff
     fun colorStateListCountForNamespace(ns: ResourceNamespace): Int =
         byNs[ns]?.colorStateLists?.size ?: 0
+
+    /**
+     * W3D4-γ T15: `Bridge.init()` 의 enumValueMap 인자용 export — framework (ANDROID) bucket 의
+     * 모든 AttrResourceValueImpl 에서 enum/flag 테이블을 추출하여 단일 `Map<String, Map<String, Int>>`
+     * 반환. layoutlib `BridgeTypedArray.resolveEnumAttribute` 가 ANDROID namespace 의 attr 변환 시
+     * `Bridge.sEnumValueMap.get(attrName).get("vertical")` 형태로 조회.
+     *
+     * 비어있는 attr (enum/flag 자식 없음) 은 결과 map 에서 제외 (Bridge 가 outer map miss 시 default
+     * 처리). RES_AUTO bucket 은 별도 경로 (AttrResourceValueImpl.getAttributeValues) — 본 helper
+     * 미관여.
+     */
+    fun frameworkEnumValueMap(): Map<String, Map<String, Int>>
+    {
+        val src = byNs[ResourceNamespace.ANDROID]?.attrs ?: return emptyMap()
+        val out = LinkedHashMap<String, Map<String, Int>>()
+        for ((name, attr) in src)
+        {
+            val raw = attr.attributeValues
+            if (raw.isNullOrEmpty()) continue
+            // boxed Integer → Int auto-unbox via Kotlin platform type. 명시적 toInt() 로
+            // null/IllegalState 회피 보강 (raw.values 안에 null 이 들어올 일은 없으나 방어적).
+            val converted = LinkedHashMap<String, Int>(raw.size)
+            for ((k, v) in raw)
+            {
+                if (v != null) converted[k] = v.toInt()
+            }
+            if (converted.isNotEmpty()) out[name] = converted
+        }
+        return out
+    }
```

**`raw.isNullOrEmpty()` vs `raw.isEmpty()`**: layoutlib 의 `AttrResourceValueImpl.getAttributeValues()` 는 null 반환 안 함 (bytecode 검증 — emptyMap 반환). 그러나 서명 자체가 platform type 이므로 Kotlin 의 정적 분석은 nullable 로 처리 — `isNullOrEmpty()` 가 안전.

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`

**변경 — initBridge 의 enumValueMap 채우기 (line 122 + 128-137 인접)**:

문제: 현재 `initBridge()` 은 bundle 을 모름 (line 116-122 가 platform/font/icu/keyboard 준비 단계 — bundle 은 후속 `renderViaLayoutlib` 에서 lazy 로드). bundle 을 `initBridge` 가 미리 알게 하려면:

옵션 (a) — `initBridge` 시그니처를 `initBridge(enumValueMap)` 로 변경, 호출처가 bundle 미리 로드 후 전달.
옵션 (b) — `initBridge()` 안에서 `LayoutlibResourceValueLoader.loadOrGet(args)` 를 직접 호출, bundle.frameworkEnumValueMap() export.

옵션 (b) 가 자연스러움 — bundle 은 args (constructor 의 dist/sample-app/runtime-classpath) 만 있으면 build 가능, 그리고 어차피 `renderViaLayoutlib` 에서도 cache 된 bundle 재사용. cold-start 시 한 번만 로드.

```diff
     @Synchronized
     private fun initBridge() {
         if (initialized) return
         val cl = bootstrap.createIsolatedClassLoader()
         val bridgeClass = Class.forName(BRIDGE_FQN, false, cl)
         val initMethod = bridgeClass.declaredMethods.first { it.name == BRIDGE_INIT_METHOD }

         val nativeLib = bootstrap.nativeLibDir().resolve(NATIVE_LIB_NAME)
         if (nativeLib.toFile().exists()) {
             try {
                 System.load(nativeLib.absolutePathString())
             } catch (_: Throwable) {
             }
         }

         val platformProps = bootstrap.parseBuildProperties()
         val fontDir = bootstrap.fontsDir().toFile()
         val nativeLibPath = bootstrap.nativeLibDir().absolutePathString()
         val icuPath = bootstrap.findIcuDataFile()?.absolutePathString()
             ?: error("ICU data 파일 누락 (data/icu/icudt*.dat)")
         val keyboardPaths = bootstrap.listKeyboardPaths().toTypedArray()
-        val enumValueMap = mutableMapOf<String, MutableMap<String, Int>>()
+        // W3D4-γ T15: framework attrs.xml 의 enum/flag 값 테이블을 Bridge.sEnumValueMap 에 주입.
+        // BridgeTypedArray.resolveEnumAttribute (ANDROID path) 가 정적 sEnumValueMap 만 조회.
+        // bundle 은 어차피 renderViaLayoutlib 가 동일 args 로 cache hit — 추가 비용 없음.
+        val bundle = LayoutlibResourceValueLoader.loadOrGet(rendererArgs)
+        val enumValueMap = bundle.frameworkEnumValueMap()

         val logInterface = Class.forName(ILAYOUT_LOG_FQN, false, cl)
         val logProxy = Proxy.newProxyInstance(cl, arrayOf(logInterface), NoopLogHandler())
```

`rendererArgs` — LayoutlibRenderer 의 ctor 에서 받은 args 의 reference. 현재 LayoutlibRenderer 는 `LayoutlibResourceValueLoader.Args(distDataDir, sampleAppRoot, runtimeClasspathTxt)` 를 어떻게 만드는가 — `renderViaLayoutlib` 안에서 build. 초기에는 ctor 단계에서 캡처해두고 양쪽이 사용하도록 변경.

**호출 흐름 변경 의미**:
- 이전: `initBridge()` 가 bundle 로드 안 함 → `renderViaLayoutlib` 가 lazy 로드.
- 신규: `initBridge()` 가 bundle 강제 로드 → 첫 render 호출 전 bundle 도 만들어짐 (cold-start 시간이 합쳐짐 — 성능 영향 측정).

**round 4 reconcile (Codex Q4 측정)** — 실측 cold-start (`MaterialFidelityIntegrationTest` 실행 중 캡처): `[LayoutlibResourceValueLoader] cold-start framework=39ms app=1ms aar=36ms build=13ms total=89ms` (server/layoutlib-worker test result XML, dev.axp.layoutlib.worker.resources.MaterialFidelityIntegrationTest:24). end-to-end 첫 `renderPng()` latency 는 거의 동일 — 동일 `loadOrGet(args)` 가 `initBridge` 로 이동해도 후속 `renderViaLayoutlib` 가 cache hit 으로 즉시 반환. `initBridge` 자체만 ~90ms 더 길어짐 + Synchronized block 확장. 사용자 가시 영향 없음.

### §4.2 신규 단위 테스트 (T15.tests, ~5 cases)

#### `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleFrameworkEnumExportTest.kt` (신규)

- `frameworkEnumValueMap - returns enum entries from ANDROID bucket`
- `frameworkEnumValueMap - returns flag entries merged with enum (single Map<String,Int> per attr — layoutlib API 동일 map)`
- `frameworkEnumValueMap - excludes attrs with empty maps`
- `frameworkEnumValueMap - returns emptyMap when ANDROID bucket absent`
- `frameworkEnumValueMap - RES_AUTO bucket attrs not included` (separation 검증)

#### `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererInitTest.kt` (신규 또는 기존 확장, ~3 cases)

- `initBridge - enumValueMap arg is non-empty when framework attrs.xml has enum/flag`
- `initBridge - enumValueMap key set includes orientation, gravity` (실제 framework attrs.xml 사용)
- `initBridge - enumValueMap.get('orientation').get('vertical') == 1` (end-to-end)

이 테스트는 reflection 으로 Bridge 클래스의 sEnumValueMap 정적 필드를 query 하거나 (Bridge.init 호출 후), 우리가 invoke 직전 캡처한 enumValueMap 을 직접 검증. 후자가 단순 — invoke 직전 logging 또는 snapshot 으로 검증.

### §4.3 IT 영향 (T15 적용 후)

- `MaterialFidelityIntegrationTest` 4/4 — chain walker 자체는 변경 없음. enumValueMap 추가 주입은 framework path 만 영향.
- `LayoutlibRendererTier3MinimalTest` 14 PASS — minimal renders 가 enum/flag attr 사용 적은 경우 영향 없음. 회귀 검사.
- `tier3-basic-primary` — T14 + T15 적용 후 첫 측정. orientation/gravity (framework) + layout_constraintEnd_toEndOf parent (RES_AUTO) 모두 변환 가능 → SUCCESS 가능.

---

## §5 T16 — Acceptance gate close

### §5.1 변경

#### `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`

```diff
-    @org.junit.jupiter.api.Disabled(
-        "W3D4-γ-A carry: T11/T12 적용 후 새 fail surface — `<attr>` 의 `<enum>/<flag>` 자식 미캡처 → " +
-            "layoutlib warning \"vertical\" / \"center_horizontal\" / \"parent\" is not a valid integer. " +
-            "W3D4-γ T14/T15 (AttrDef enum/flag value capture, 양쪽 path) 후 @Disabled 제거.",
-    )
     @Test
     fun `tier3 basic primary — activity_basic 가 직접 SUCCESS`()
```

cache invalidation `@BeforeEach` 는 T13 에서 이미 추가됨 — 변경 불필요.

### §5.2 PASS 조건

- `Result.Status.SUCCESS == renderer.lastSessionResult?.status`
- `bytes.size > MIN_RENDERED_PNG_BYTES`
- `isPngMagic(bytes) == true`
- IT 수: 14 → **15 PASS** + 1 SKIP (`tier3-glyph` W4 carry).
- 모듈 합산 unit 재측정.

### §5.3 실패 시 escalation 정책 (plan v3 §5.4 패턴 일관)

- T14 단독 적용 후 `tier3-basic-primary` stack trace 검사 — orientation/gravity 가 여전히 fail 이면 framework path 가 main bottleneck (T15 가 unblock).
- T15 추가 적용 후도 fail 이면 새 layer:
  - **Hypothesis γ-C** (예상): ConstraintLayout 의 `app:layout_constraintTop_toTopOf="parent"` style 의존이 R$styleable 경로 (RJarSymbolSeeder.kt:64-66 skip — Bridge.parseStyleable 위임 가정) 에서 fail. plan v3 §5.4 의 Q6.5 가 정확히 이 surface — 측정 후 plan v3.2 escalate.
  - **Hypothesis γ-D**: AAR `<style>` 의 parent="@android:style/X" 형태 cross-namespace ref chain 미해결 (cross-NS resolution 의 chain walker miss).
- 새 layer 발견 시 `t16-acceptance-gate-followup.md` (round 4 round 3 패턴 인계) 작성 → plan v3.2 또는 W3D4-δ.

---

## §6 Q1–Q6 — round 4 pair-review query (Codex+Claude, plan-revision phase)

### Q1 — 2-path 분기의 정확성
"§1.1 의 BridgeTypedArray.resolveEnumAttribute path 분기 (ANDROID → sEnumValueMap, 그 외 → AttrResourceValueImpl.getAttributeValues) 가 layoutlib 14.0.11 에서 **유일** 한 path 인가? 다른 lookup site (예: Resources_Delegate.parseValue, ResourceHelper.parseColor) 가 attr 값 변환에 enum 테이블을 별도로 조회하는 경로가 있는가?"

### Q2 — Integer.decode 의 입력 범위
"§3.1 변경 2 의 parseAttrValueLiteral 가 Integer.decode 사용. framework attrs.xml census 의 십진/십육진 외 다른 literal 형식 (negative, binary `0b`, 큰 hex `0xFFFFFFFF` overflow) 이 실 AAR 에 존재하는가? Integer.decode 의 `0xFFFFFFFF` 는 NumberFormatException — flag 의 `mask` 패턴에 고치 hex 가 있을 위험."

### Q3 — first-wins vs merge 정책 (동명 attr)
"§3.1 변경 (LayoutlibResourceBundle.buildBucket) 가 first-wins 유지 — 동명 `<attr>` 의 두 번째 등장은 silent skip. RES_AUTO 안 AAR 들이 같은 attr 을 다른 enum/flag set 으로 정의할 가능성? Material AAR 의 `<attr name="orientation">` 와 ConstraintLayout AAR 의 동명 attr 이 다른 enum set 인가? merge 가 안전한가?"

### Q4 — initBridge 의 bundle 강제 로드 비용
"§4.1 변경이 initBridge 안에서 LayoutlibResourceValueLoader.loadOrGet 호출 — cold-start 시간이 (init + bundle build) 합쳐짐. plan v2 의 측정 대비 추가 시간? renderViaLayoutlib 가 동일 args 로 cache hit 이지만 첫 render 가 늦어지는 영향?"

### Q5 — RES_AUTO path 의 `RenderResources.getResolvedResource` chain
"§1.1 의 path B 는 `RenderResources.getResolvedResource(ref)` 가 attr ref 를 받아 AttrResourceValueImpl 를 반환해야 함. LayoutlibRenderResources.kt 의 9 override 중 getResolvedResource 가 attr 의 reference 를 chain walker 로 따라가는가? attr 은 보통 ResourceType.ATTR 의 unresolved ref — chainWalker 가 ATTR 처리 분기 (`findItemInTheme` 호출) 를 거치는데, attr 자체 (style item 이 아님) 의 lookup 이 의도대로 수행되는지 검증 필요."

### Q6 — open critique 슬롯 (round 3 정책 일관)
"본 plan v3.1 의 file:line 검증 외, 양쪽 reviewer 가 독자적으로 발견한 결함이 있다면 명시. 예: getAttributeValues 의 boxed Integer ↔ Kotlin Int 변환 시 null entry 처리, ParsedNsEntry.AttrDef 의 필드 reorder 가 호출처 (특히 production 외 fixture) 에 silent breakage 유발 가능성, T14/T15 의 dependency 가 단일 commit 으로 묶여야 했는가."

---

## §7 LM (landmines) 회피 정책

| LM 코드 | 사유 | 본 plan 의 방어 |
|---|---|---|
| LM-W3D3-A / LM-α-A | empirical-verifiable claim 직접 측정 | §1.1, §1.3, §1.4, §1.5 의 javap / unzip / grep 인용 |
| LM-α-B | Codex stalled-final → single-source verdict + 명시 flagging | round 4 pair 적용 |
| LM-G | codex exec sandbox bypass | round 4 직접 CLI: `codex exec --skip-git-repo-check --sandbox danger-full-access` (MEMORY.md feedback 준수) |
| LM-W3D3-B | JUnit Jupiter Assertions only | §3.2/§4.2 신규 테스트는 `org.junit.jupiter.api.Assertions.*` 만 |
| LM-α-D | Kotlin backtick 함수명 안 마침표 금지 | §3.2/§4.2 의 backtick 테스트명 검증 |
| LM-W3D4-D | placeholder 관행 | §3.1 / §4.1 모두 explicit diff |
| LM-W3D4-E | mixed-content xliff:g | T14 는 `<attr>` 자식 enum/flag 만 — values 의 mixed content 경로 미해당 |
| LM-W3D4-F | cross-NS attr ref `:` | parser 의 기존 NS_NAME_SEPARATOR_CHAR check 그대로 — W3D4-γ 변경에 영향 없음 |
| LM-W3D4-β-D | KDoc `/*` 패턴 회피 | 본 plan 의 모든 KDoc path/예시는 인라인 quote 또는 backtick |
| LM-W3D4-β-E | assertNotNull chain 금지 | 신규 test 에 `val v = ...; assertNotNull(v); ...` 패턴 |
| LM-W3D4-β-F | IT 실행은 `-PincludeTags=integration` | T16 의 IT 측정 시 명시 |
| LM-W3D4-β-G | subagent unzip `--directory /tmp/...` | round 4 의 모든 subagent prompt 에 명시 |
| LM-W3D4-β-H | acceptance fail surface 분류 | T16 의 §5.3 escalation 정책 직접 인용 |

---

## §8 Test impact summary

| 단계 | layoutlib-worker unit | IT (PASS + SKIP) | 비고 |
|---|---|---|---|
| baseline (T13 partial 후) | 172 | 14 + 2 SKIP | tier3-basic-primary `@Disabled` |
| T14 적용 후 | 172 + 7 (AttrEnumFlagCapture) + 3 (BundleAttrValues) - 0 = 182 | 14 + 2 SKIP (변동 없음) | RES_AUTO path 단독 효과 측정 |
| T15 적용 후 | 182 + 5 (FrameworkEnumExport) + 3 (RendererInit) = 190 | 14 + 2 SKIP | framework path 추가 |
| T16 적용 후 | 190 (변동 없음) | **15 PASS + 1 SKIP** | tier3-basic-primary 닫힘 |

**모듈 합산 unit baseline 215 → T16 후 233 예상** (layoutlib-worker +18, 다른 모듈 변동 없음).

---

## §9 Commit/push 단위

CLAUDE.md task-unit completion 정책:
- T14 commit: `feat(w3d4-gamma): T14 AttrDef enum/flag value capture (parser + Bundle addValue)`
- T15 commit: `feat(w3d4-gamma): T15 framework Bridge.init enumValueMap wiring`
- T16 commit: `feat(w3d4-gamma): T16 tier3-basic-primary @Disabled 제거 + acceptance gate 닫힘`
- 각 task PASS 후 즉시 push.
- round 4 pair verdict 는 별도 work_log entry: `docs/work_log/2026-04-30_w3d4-beta-plumbing/round4-pair-review.md` (W3D4-β session-folder 안에 — 같은 session 의 후속 round).
- session 종료 시 `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` append + `handoff.md` 갱신 (필요 시).

---

## §10 Out-of-scope (본 plan 이 다루지 않는 것)

- **W3D4-γ-B** — §1.4 empirical 무력화로 제거.
- **R$styleable 경로** (γ-C 가설) — primary fail surface shift 시 plan v3.2 escalate.
- **AttrDef merge policy** — first-wins 유지, 동명 attr 의 enum/flag 차이 시 merge 는 future plan.
- **color qualifier directories** (color-night/, color-v23/) — W4+ density/locale.
- **DRAWABLE selector XML feed** — plan v3 §5.4 의 T12.5 escalation 대상 (γ surface 안 도달 시).
- **W3D4 tier3-glyph** (Font wiring) — W4 carry.

---

## §11 Round 4 reconcile placeholder

본 plan v3.1 은 round 4 pair-review (Codex+Claude, plan-revision phase) 에서 verify 후 inline 적용 완료.

**Pair-review trigger**: planning / plan-revision phase 의 escalation 정책 일치 (CLAUDE.md). Codex CLI 직접 (LM-G), Claude subagent (Plan agent). Q1-Q6 (Q6 open critique). judge round 은 Q1-Q5 의 set divergence 시점에만 실행.

### §11.1 Round 4 verdict + convergence map (2026-04-30 → 2026-05-01)

| 채널 | verdict | confidence | 핵심 finding |
|---|---|---|---|
| Codex (latest GPT, xhigh) | REVISE_REQUIRED | 0.93 | Q5 KILL POINT — `LayoutlibResourceBundle.kt:42-43` 가 `byType` 만 조회, `attrsMut` 분리 → ATTR ref lookup null. T14 의 addValue 만으로는 acceptance gate 닫지 못함. + Q2 `Integer.decode` 가 `0x80000000`/`0xffffffff` mask flag 에 NumberFormatException. |
| Claude (Plan agent) | GO_WITH_FIXES | 0.92 | Q1-Q5 모두 CORRECT 판정 — 단 Q5 의 NsBucket attrs 분리 issue 미발견 (Codex 가 catch). |

**Convergence map**:
| Q | Codex | Claude | reconcile |
|---|---|---|---|
| Q1 (2-path branch) | NUANCED (BridgeXmlPullAttributes sibling 추가) | CORRECT | Codex sibling 정보 inline 적용 (§1.1 추가 paragraph) |
| Q2 (Integer.decode) | DISAGREE — `0x80000000`/`0xffffffff` overflow 실증 | CORRECT (census 평균만) | **Codex 실증 우선** — `Long.decode` 로 교체, hex/decimal 양방향 unsigned 32-bit cover |
| Q3 (first-wins) | NUANCED — 213 dup / 56 empty-vs-nonempty / 0 nonempty conflict | NUANCED — 안전 판정 | 결론 동일, Codex 실증 inline (§3.1 merge 정책 보강) |
| Q4 (initBridge cost) | CORRECT — measured 89ms | ACCEPTABLE — ~150ms estimate | Codex 측정값 inline (§4.1 호출 흐름 변경 의미) |
| **Q5 (getResolvedResource ATTR)** | **DISAGREE — KILL POINT** | CORRECT | **Codex 실증 우선** — bundle.getResource 에 ATTR special-case 추가 (§3.1 변경 1 신규), getResolvedResource + getUnresolvedResource 둘 다 cover |
| Q6 (open critique) | LayoutlibResourceBundleTest.kt:79-80 명시 + γ-C defer OK + Kotlin boxing OK + LM 준수 | 동일 결론 | Codex 의 fixture test 명시 inline (§3.2 기존 테스트 영향) |

**가장 강한 convergence**: Q4 (cost) + Q6 (γ-C defer + LM 준수) — 양쪽 동일 결론.
**가장 강한 divergence**: Q5 — Codex 가 Claude 가 놓친 critical bug 를 file:line 으로 catch. NsBucket 의 `byType ↔ attrs` 분리 + `getResource` 의 byType-only 경로 = 검증 가능한 사실. judge round 불요 (single-channel empirical, 비검증 사실 아님 — 직접 코드 read 로 confirm).

### §11.2 Adopted plan deltas (7, 모두 inline 적용 완료)

1. **§3.1 변경 — `parseAttrValueLiteral` 가 `Long.decode(...).toInt()` 로 교체** (Q2). 32-bit unsigned hex (`0x80000000`, `0xffffffff`) + 음수 십진 cover. 추가 회귀 테스트 2개 (§3.2 의 `flag value 0x80000000` + `negative decimal value`).
2. **§3.1 신규 변경 — `LayoutlibResourceBundle.getResource(ref)` 에 ATTR special-case 추가** (Q5 KILL POINT). `byType` 외 `attrs` map 도 조회 → BridgeTypedArray + BridgeXmlPullAttributes 양쪽 enum lookup path 가 AttrResourceValue 인스턴스 도달.
3. **§3.2 신규 — `LayoutlibRenderResourcesAttrLookupTest.kt`** (Q5 acceptance-critical). `getResolvedResource` + `getUnresolvedResource` ATTR ref → AttrResourceValue 검증.
4. **§1.1 — BridgeXmlPullAttributes sibling enum consumer 명시** (Q1 NUANCED). project supplier 가 `getUnresolvedResource` 사용 → Q5 fix 가 양쪽 entry point cover 필수.
5. **§3.1 — first-wins 의 empirical justification** (Q3 NUANCED). 213 dup / 56 empty-vs-nonempty / 0 nonempty-vs-nonempty / 0 first-empty-later-nonempty 측정값 inline.
6. **§3.2 — `LayoutlibResourceBundleTest.kt:79-80` 명시** (Q6 reorder safety). 5-arg AttrDef ctor 갱신 명시.
7. **§4.1 — measured cold-start cost 89ms inline** (Q4). 추정값 → 측정값.

### §11.3 LM (landmines) 준수 검증

- LM-G: `codex exec --skip-git-repo-check --sandbox danger-full-access` 직접 CLI ✓
- LM-α-A: Codex 모든 claim file:line 인용 (40+ citation) — 양쪽 검증 가능 ✓
- LM-α-B: dual-channel verdict, single-source 회피 (Claude+Codex 둘 다 분석) ✓
- LM-W3D4-D: 모든 fix explicit diff (§3.1 변경 1, 변경 2 모두 diff 형식) ✓
- LM-W3D4-β-D~H: round 4 reviewer 가 명시 검증 (Q6 inline)

**최종 verdict**: REVISE → APPLIED (7/7 deltas inline). plan v3.1 → **GO** (post-revision). T14/T15/T16 구현 진입 승인.
