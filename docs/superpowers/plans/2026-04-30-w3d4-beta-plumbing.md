# W3D4-β plumbing plan v3 — Gap A / Gap B / Acceptance gate close

생성: 2026-04-30
선행: `docs/superpowers/plans/2026-04-29-w3d4-material-fidelity.md` (plan v2, T1–T10), `docs/work_log/2026-04-29_w3d4-material-fidelity/t9-acceptance-gate-discovery.md`
phase: **W3D4-β plumbing** (production-pipeline gap-fix)
범위: T11 (Gap A) · T12 (Gap B) · T13 (acceptance gate 닫기) — plan v2 의 T1–T9 자료구조 / chain walker 는 변경 없음.

---

## §0 Entry context (cold-start safe)

### §0.1 작업 흐름의 위치
- W3D4 plan v2 에서 T1–T9 구현 완료, MaterialFidelityIntegrationTest 4/4 PASS.
- 그러나 T9 의 acceptance gate (`activity_basic.xml` 직접 SUCCESS — primary IT) 는 production-pipeline 에서 **2 plumbing gap** 으로 fail → primary `@Disabled` W3D4-β carry (commit `f2bc922`).
- v2 round 2 (Codex+Claude) 는 이 gap 두 개를 **chain walker 의 정확성 = OK / Bridge 와의 wiring = NG** 으로 분리, plumbing-only 로 v3 를 escalate.

### §0.2 본 plan v3 가 변경하지 않는 것 (불변식)
- `LayoutlibResourceBundle` 의 byNs 2-bucket (ANDROID + RES_AUTO) 구조.
- `LayoutlibRenderResources` 의 chain walker (`MAX_REF_HOPS=10`, `MAX_THEME_HOPS=32`).
- `AarResourceWalker` 의 RES_AUTO 통일 정책 (round 2 ξ 결정).
- `LayoutlibResourceValueLoader` 의 3-입력 통합.
- `MaterialFidelityIntegrationTest` 4/4 PASS 의 entry criterion.

### §0.3 baseline (2026-04-30 11:30 측정)
- `cd server && ./gradlew test --console=plain` → `BUILD SUCCESSFUL`
- 모듈 합산 194 tests / 0 fail / 0 error: `protocol`(2) + `http-server`(2) + `mcp-server`(7) + `layoutlib-worker`(151 unit + 14 IT + 2 SKIP, 2 SKIP = `tier3-glyph` W4 carry + `tier3-basic-primary` W3D4-β carry).
- 현재 branch: `main`, head `6a7577f`.

---

## §1 Gap A — 정확 root cause (empirical, 2026-04-30 검증)

### §1.1 증상
Material AAR 의 `com.google.android.material.internal.ThemeEnforcement.checkAppCompatTheme(ctx)` 가 `Theme.AxpFixture` (sample-app theme) 를 **AppCompat descendant 로 인식 못함** → MaterialButton inflate 단계에서 IllegalArgumentException.

### §1.2 chain walker 자체는 OK (검증됨)
- `MaterialFidelityIntegrationTest` 4/4 PASS — Theme.AxpFixture → ... → Theme.AppCompat → Theme 의 17-hop parent chain 정상.
- `LayoutlibRenderResources.computeInitialStack`, `walkParent`, `findItemInTheme` 정상 동작.

### §1.3 실제 wiring gap (file:line)

**dual namespace policy 불일치**:

| 위치 | 코드 | 효과 |
|---|---|---|
| `RJarSymbolSeeder.kt:110` | `val namespace = ResourceNamespace.fromPackageName(packageName)` | seeded ID → `ResourceReference(ns="androidx.appcompat", ATTR, "colorPrimary")` 로 callback.byId 에 등록 |
| `AarResourceWalker.kt:71` | `NamespaceAwareValueParser.parse(tmp, ResourceNamespace.RES_AUTO, pkg)` | parsed XML attr/style → `byNs[RES_AUTO]` bucket 에 적재 |

**호출 흐름**:
1. `ThemeEnforcement.checkAppCompatTheme` → `BridgeContext.obtainStyledAttributes(int[]{appcompat.R$attr.colorPrimary})`
2. Bridge → `MinimalLayoutlibCallback.resolveResourceId(seededId)` → `byId[id]` 반환 → `ResourceReference(ns="androidx.appcompat", ATTR, "colorPrimary")` (※ `MinimalLayoutlibCallback.kt:82`)
3. Bridge → `RenderResources.findItemInTheme(attr=ResourceReference(androidx.appcompat,ATTR,colorPrimary))`
4. → `LayoutlibRenderResources.findItemInTheme:213` → `findItemInStyle(theme, attr)`:200 → `cur.getItem(attr)`
5. theme 의 `StyleItemResourceValueImpl` 들은 `LayoutlibResourceBundle.buildBucket:119` 에서 `StyleItemResourceValueImpl(ns=RES_AUTO, …)` 로 생성됨.
6. `getItem(attr)` 은 attr 의 **full ResourceReference (ns 포함) 일치** 를 요구 → ns 미일치로 null.
7. ThemeEnforcement 가 `colorPrimary` 미해결 → AppCompat descendant 가 아닌 것으로 판정 → IllegalArgumentException.

### §1.4 fix shape (single source-of-truth: RES_AUTO)

**선택**: RJarSymbolSeeder 를 RES_AUTO 통일 (round 2 ξ 결정 일관). AarResourceWalker / NamespaceAwareValueParser / LayoutlibResourceBundle / LayoutlibRenderResources 는 변경 없음 (이미 RES_AUTO 가정).

**collateral — round 3 정정** (Codex + Claude 양쪽 empirical):
- 현재 `AttrSeederGuard` 는 ATTR 만 cross-class first-wins.
- RES_AUTO 통일 후 STYLE/COLOR/DIMEN 등도 동명 충돌이 **widespread**: R.txt union 측정 시 `Widget_Compat_Notification*` 15-way (constraintlayout/coordinatorlayout/core/customview/...), `notification_*` color/dimen 15-way, `fontVariationSettings` 16-way 등 수백 건. 이전 plan 의 "AAR 간 style/color/dimen disjoint" 가정은 empirical 으로 **틀림**.
- 그러나 Android `compile_and_runtime_not_namespaced_r_class_jar` (AAPT 의 non-namespaced 정책) 하에서는 동명 resource 가 **동일 ID** 를 공유 (ex: 모든 AAR 의 `R$attr.colorPrimary == 2130903315 / 0x7F030013`). 따라서 first-wins 가 ID 결정성에 영향 없음 — 동명 = 동 ID = 어느 entry 가 등록되어도 callback.byId map 결과 동일.
- 정책: per-type **Map<String, Int>** guard (HashSet 가 아닌 Map). 동일 name 의 (id 동일) 두 번째 호출 → silent skip (정상 transitive ABI). 동일 name 이지만 **id 다른** 경우 → loud WARN (실 namespaced build 에서 발생 가능 — 향후 회귀 신호).
- 신규 `ResourceTypeFirstWinsGuard` 가 이 정책 구현 (HashSet → Map 변경 반영, §3 Fix 1 참조).

---

## §2 Gap B — 정확 root cause (empirical, 2026-04-30 검증)

### §2.1 증상
`Bridge.Resources_Delegate.getColorStateList(@color/m3_highlighted_text)` (Material AAR `res/color/m3_highlighted_text.xml`) 호출 시 input feed 부재 → 렌더 중단.

### §2.2 census (실측 — round 3 reconcile 후 정정)

**중요 정정**: round 3 pair-review 의 양쪽 (Codex / Claude) 모두 census 값과 의존성 버전을 정정.
- 프로젝트 실제 사용 버전 (`fixture/sample-app/app/build.gradle.kts:43-45`): `appcompat:1.6.1` + `material:1.12.0` (NOT 1.7.1 / 1.13.0 — Gradle cache 의 다른 캐시 버전과 혼동된 것).
- `fixture/sample-app/app/build/axp/runtime-classpath.txt` → 55 AAR 항목.
- `res/color/*.xml` (default qualifier) 보유:
  - `material-1.12.0` → 171 files
  - `appcompat-1.6.1` → 21 files
  - `appcompat-resources-1.6.1` → 0 files (color XML 없음, drawable + values.xml 만)
  - 합계 **192 color XML files** (default qualifier 만, W3D4-β scope)
- qualifier directory (out of scope, W4+ carry):
  - `material-1.12.0`: `color-v31/` (33), `color-night-v8/` (4)
  - `appcompat-1.6.1`: `color-v21/` (2), `color-v23/` (10)
- `m3_highlighted_text` / `m3_dark_highlighted_text` 모두 default `res/color/` 에 존재 → base-only matching 으로 acceptance gate 도달 충분.

### §2.3 wiring gap 의 3-layer breakdown (file:line)

| layer | 위치 | 현재 동작 | 필요 동작 |
|---|---|---|---|
| **walker** | `AarResourceWalker.kt:56` | `zip.getEntry("res/values/values.xml")` 만 enumerate | `res/color/*.xml` 도 enumerate |
| **parser/entry** | `NamespaceAwareValueParser` (top-level dispatch L89–128) + `ParsedNsEntry` sealed class | `<selector>` root 처리 path 부재 + ParsedNsEntry 에 ColorStateList variant 부재 | 신규 `parseColorXml(rawXml)` helper + `ParsedNsEntry.ColorStateList` |
| **bundle** | `LayoutlibResourceBundle.kt:42` `getResource` | `byNs[ns]?.byType?.get(COLOR)?.get(name)` 만 — 단일-색 only | byType[COLOR] 에 raw selector XML 을 value 로 갖는 placeholder ResourceValue 등록 + 별도 `colorStateLists: Map<String, String>` lookup 메서드 |
| **callback** | `MinimalLayoutlibCallback.kt:107` `getParser` | `null` return | bundle 에서 raw XML fetch → `KXmlParser.setInput(StringReader(xml))` → return ILayoutPullParser |

### §2.4 Bridge contract (검증됨)
`com.android.layoutlib.bridge.impl.ResourceHelper.getColorStateList(rv, ctx, theme)` 는:
1. `callback.getParser(rv)` 호출 — non-null 시 그 parser 사용.
2. null 시 `ParserFactory.create(rv.getValue())` (값을 path 로 처리) — 우리 setup 에선 fallback 도달하면 IOException.

→ callback path 만 막으면 충분 (path 변환 불필요).

### §2.5 W3D1 historical baseline
`git show 1d5729a^:.../FrameworkValueParser.kt` (T8 absorption 직전) 도 `<selector>` 미처리 — framework 측은 단일-색 colors.xml 만 다뤘고, 색 state list 는 Bridge 에 위임. AAR 측은 W3D4 에서 처음 도입 → Gap B 는 W3D4 의 신규 surface (회귀 아님).

---

## §3 T11 — Gap A fix (RJarSymbolSeeder RES_AUTO 통일 + cross-type first-wins)

### §3.1 변경 파일

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt`

**변경 1 — header comment (round 2 → β 통일 정책 명시)**
```diff
- *  - R-2 (§7.2): multiple R class (appcompat / material / 등) 의 동명 attr 첫 등장만
- *    register — cross-class first-wins. seenAttrNames 는 ZipFile 단위 outer scope
- *    (한 R.jar 안 모든 R$attr 클래스 공유). AttrSeederGuard 위임.
+ *  - R-2 (§7.2): multiple R class 의 동명 ATTR 첫 등장만 register — cross-class first-wins.
+ *
+ * W3D4-β (T11): namespace 통일 정책 — RJarSymbolSeeder 도 ResourceNamespace.RES_AUTO
+ * 로 등록. AarResourceWalker:71 의 RES_AUTO 와 정합 (round 2 ξ 결정 일관). 이전의
+ * fromPackageName(packageName) 은 callback.byId 의 ResourceReference 와 bundle.byNs
+ * 사이에 namespace 불일치를 만들어 Material ThemeEnforcement.checkAppCompatTheme 가
+ * sentinel attr 을 못 찾는 원인이었다. cross-class first-wins 도 ATTR-only → 모든
+ * type 으로 일반화 (ResourceTypeFirstWinsGuard 신설).
```

**변경 2 — ATTR-only HashSet → per-type HashMap<String, Int> (round 3 정정 — id 추적)**
```diff
-        // v2 follow-up #1 (R-2): cross-class first-wins per attr name. appcompat R$attr
-        // 와 material R$attr 양쪽이 'colorPrimary' 를 등록 시도하면 첫 등장만 통과.
-        // outer scope = ZipFile 단위 (한 R.jar 안 모든 R$attr 클래스 공유).
-        val seenAttrNames = HashSet<String>()
+        // W3D4-β T11 (round 3 reconcile): RES_AUTO 통일 후 동명 충돌 widespread —
+        // STYLE 355 / COLOR 93 / DIMEN 130 dup (R.jar union 측정). 단 AAPT
+        // non-namespaced 정책으로 동명 = 동일 ID. per-type Map<String, Int> guard 로
+        // (1) 동명-동ID → silent skip (정상), (2) 동명-다른ID → loud WARN (회귀 신호).
+        // ZipFile 단위 outer scope.
+        val seenByType = HashMap<ResourceType, HashMap<String, Int>>()
```

**변경 3 — seedClass signature + namespace 통일**
```diff
             seedClass(
                 rJarLoader,
                 internalName.replace(INTERNAL_NAME_SEPARATOR, EXTERNAL_NAME_SEPARATOR),
                 packageName,
                 resourceType,
                 register,
-                seenAttrNames,
+                seenByType,
             )

   ...

     private fun seedClass(
         loader: ClassLoader,
         fqcn: String,
         packageName: String,
         type: ResourceType,
         register: (ResourceReference, Int) -> Unit,
-        seenAttrNames: MutableSet<String>,
+        seenByType: MutableMap<ResourceType, HashMap<String, Int>>,
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
-        val namespace = ResourceNamespace.fromPackageName(packageName)
+        // W3D4-β T11: RES_AUTO 통일 — AarResourceWalker:71 의 namespace 와 일치시켜
+        // bundle 의 byNs 와 callback 의 byId 가 같은 ResourceReference 좌표계 공유.
+        val namespace = ResourceNamespace.RES_AUTO
         for (field in cls.declaredFields)
         {
             ...
-            // v2 follow-up #1 (R-2): R$attr 만 cross-class first-wins guard 적용.
-            // 다른 type (style/dimen/color/...) 은 namespace 가 R class 별 다르므로
-            // ResourceReference 자체로 동치 충돌 없음.
-            if (type == ResourceType.ATTR)
-            {
-                if (!AttrSeederGuard.tryRegister(emitName, value, packageName, seenAttrNames))
-                {
-                    continue
-                }
-            }
+            // W3D4-β T11 (round 3): per-type Map<String, Int> guard — same-id silent,
+            // different-id loud warn. 모든 type 에 적용.
+            val seenForType = seenByType.getOrPut(type) { HashMap() }
+            if (!ResourceTypeFirstWinsGuard.tryRegister(type, emitName, value, packageName, seenForType))
+            {
+                continue
+            }
             register(ResourceReference(namespace, type, emitName), value)
         }
     }
```

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ResourceTypeFirstWinsGuard.kt` (신규)

```kotlin
package dev.axp.layoutlib.worker.classloader

import com.android.resources.ResourceType

/**
 * W3D4-β T11 (round 3 reconcile): RES_AUTO 통일 후 모든 ResourceType 의 cross-class
 * 동명 first-wins guard. 이전의 AttrSeederGuard (ATTR-only Set) 를 일반화 + Map<name,id>
 * 로 ID 일치성 검증.
 *
 * 정책:
 *  - 동명 + 동ID (현재 AAPT non-namespaced 정책의 정상 transitive ABI) → silent skip.
 *  - 동명 + 다른ID (회귀 신호 — 향후 namespaced build 또는 R.jar 빌드 오류 가능) → loud WARN.
 *
 * 실측 (round 3 Codex / Claude): R.jar union 에서 STYLE 355 / COLOR 93 / DIMEN 130
 * 동명 발견, 모두 ID 동일 (0 distinct-ID collision).
 */
internal object ResourceTypeFirstWinsGuard
{
    fun tryRegister(
        type: ResourceType,
        name: String,
        id: Int,
        sourcePackage: String,
        seen: MutableMap<String, Int>,
    ): Boolean
    {
        val existing = seen[name]
        if (existing == null)
        {
            seen[name] = id
            return true
        }
        if (existing == id)
        {
            // 정상 transitive ABI — silent.
            return false
        }
        // 회귀 신호 — loud.
        System.err.println(
            "[RJarSymbolSeeder] WARN ${type.getName()} '$name' from $sourcePackage has DIFFERENT id (existing=0x${Integer.toHexString(existing)} new=0x${Integer.toHexString(id)}) — first-wins; namespaced build 또는 빌드 정합 오류 가능"
        )
        return false
    }
}
```

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AttrSeederGuard.kt` (제거 또는 deprecate)

- 옵션 A (선호): 본 파일 삭제 + 모든 호출처 제거. 외부 참조 검증: `grep -rn "AttrSeederGuard"` (구현 + 단위테스트) → 단위테스트 동시 갱신.
- 옵션 B: deprecate + delegate to ResourceTypeFirstWinsGuard — 코드 양 증가, 동기화 부담. 채택 안 함.

→ A 채택. AttrSeederGuardTest 도 `ResourceTypeFirstWinsGuardTest` 로 rename + 다중 type 시나리오 추가.

### §3.2 신규 단위 테스트 (T11.tests)

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeederNamespaceTest.kt`

- `seed - all R$ classes register with RES_AUTO namespace` — fixture R.jar 로 부터 seed 호출, 모든 등록된 ResourceReference.namespace == RES_AUTO 단언.
- `seed - cross-class duplicate ATTR first-wins` (AttrSeederGuardTest 의 케이스 인계)
- `seed - cross-class duplicate STYLE first-wins` (신규)
- `seed - cross-class duplicate COLOR first-wins` (신규)

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ResourceTypeFirstWinsGuardTest.kt`

- 4 케이스 (ATTR/STYLE/COLOR/DIMEN) — first-wins, second-skip, dup 진단 로그 포맷 단언.

### §3.3 IT 영향
- `LayoutlibRendererTier3MinimalTest` (현재 14 PASS + 2 SKIP) — namespace 통일은 단순화 → 동작 동일 (회귀 없음). 검증 필수.
- `MaterialFidelityIntegrationTest` 4/4 — chain walker 자체는 변경 없음. 검증 필수.
- `tier3-basic-primary` (현재 SKIP) — Gap A 해결 단독으로 통과 여부 확인 (Gap B 미해결 시 다른 단계에서 fail 가능). 측정 후 T12 우선순위 결정.

---

## §4 T12 — Gap B fix (color XML walker + ParsedNsEntry + bundle + callback)

### §4.1 변경 파일

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstants.kt`

```diff
     /** AAR ZIP entry — values.xml. */
     const val AAR_VALUES_XML_PATH = "res/values/values.xml"

+    /**
+     * W3D4-β T12: AAR ZIP entry prefix — color state-list XML 디렉토리.
+     * 예: material-1.13.0 의 res/color/m3_highlighted_text.xml. 208 + 28 = 236 files / 2 AARs (실측).
+     */
+    const val AAR_COLOR_DIR_PREFIX = "res/color/"
+
+    /** color XML 파일 확장자. */
+    const val COLOR_XML_SUFFIX = ".xml"
+
+    /**
+     * W3D4-β T12: color state list ResourceValue 의 placeholder value. callback.getParser
+     * 가 가로채므로 Bridge fallback (ParserFactory.create(value)) 에 도달하지 않음 — 단지
+     * non-null marker 로 동작. 진단 시 식별 용이성을 위해 의도된 magic prefix.
+     */
+    const val COLOR_STATE_LIST_PLACEHOLDER_VALUE = "@axp:color-state-list"
```

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedNsEntry.kt`

```diff
     data class StyleDef(
         ...
     ) : ParsedNsEntry()
     {
         /** `<style>` 내부의 단일 `<item name="...">value</item>`. */
         data class StyleItem(val name: String, val value: String)
     }
+
+    /**
+     * W3D4-β T12: `res/color/<name>.xml` 의 색 state list (`<selector>` root).
+     * rawXml 는 selector body 전체 (XML declaration 포함 또는 미포함, KXmlParser.setInput 이
+     * 양쪽 모두 처리). MinimalLayoutlibCallback.getParser 가 StringReader 로 wrap 하여 feed.
+     */
+    data class ColorStateList(
+        val name: String,
+        val rawXml: String,
+        override val namespace: ResourceNamespace,
+        override val sourcePackage: String? = null,
+    ) : ParsedNsEntry()
```

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalker.kt`

```diff
     fun walkOne(aarPath: Path): Result?
     {
         require(Files.exists(aarPath)) { "AAR 부재: $aarPath" }
         ZipFile(aarPath.toFile()).use { zip ->
             val manifestEntry = ...
             val pkg = ...

             val valuesEntry = zip.getEntry(AppLibraryResourceConstants.AAR_VALUES_XML_PATH)
-            if (valuesEntry == null)
+            // W3D4-β T12: values.xml 부재 + color/*.xml 도 부재 → 진짜 code-only.
+            // 둘 중 하나라도 있으면 부분 이용. 가독성을 위해 두 path 결과를 분리 수집 후 머지.
+            val valuesEntries = if (valuesEntry == null) emptyList() else parseValuesXml(zip, valuesEntry, pkg)
+            val colorEntries = collectColorStateLists(zip, pkg)
+
+            if (valuesEntries.isEmpty() && colorEntries.isEmpty())
             {
                 System.err.println(
-                    "[AarResourceWalker] $aarPath skipped — res/values/values.xml 없음 (pkg=$pkg)"
+                    "[AarResourceWalker] $aarPath skipped — res/values/values.xml + res/color/*.xml 모두 없음 (pkg=$pkg)"
                 )
                 return null
             }
-
-            // values.xml 을 임시 파일로 풀어서 NamespaceAwareValueParser 에 넘김 (StAX 가 InputStream 보다 Path 친화적).
-            val tmp = Files.createTempFile("aarvals", ".xml")
-            tmp.toFile().deleteOnExit()
-            zip.getInputStream(valuesEntry).use { stream ->
-                Files.copy(stream, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
-            }
-            val entries = NamespaceAwareValueParser.parse(tmp, ResourceNamespace.RES_AUTO, pkg)
-            return Result(pkg, entries)
+            return Result(pkg, valuesEntries + colorEntries)
         }
     }

+    private fun parseValuesXml(zip: ZipFile, valuesEntry: ZipEntry, pkg: String): List<ParsedNsEntry>
+    {
+        val tmp = Files.createTempFile("aarvals", ".xml")
+        tmp.toFile().deleteOnExit()
+        zip.getInputStream(valuesEntry).use { stream ->
+            Files.copy(stream, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
+        }
+        return NamespaceAwareValueParser.parse(tmp, ResourceNamespace.RES_AUTO, pkg)
+    }
+
+    /**
+     * W3D4-β T12: AAR ZIP 내 res/color/*.xml 을 enumerate, 각 파일을 raw XML 문자열로 읽어
+     * ParsedNsEntry.ColorStateList 로 emit. <selector> 파싱은 layoutlib Bridge 에 위임 —
+     * 우리는 InputStream feed 만 책임 (callback.getParser).
+     */
+    private fun collectColorStateLists(zip: ZipFile, pkg: String): List<ParsedNsEntry>
+    {
+        val out = mutableListOf<ParsedNsEntry>()
+        val entries = zip.entries()
+        while (entries.hasMoreElements())
+        {
+            val e = entries.nextElement()
+            val n = e.name
+            if (e.isDirectory) continue
+            if (!n.startsWith(AppLibraryResourceConstants.AAR_COLOR_DIR_PREFIX)) continue
+            if (!n.endsWith(AppLibraryResourceConstants.COLOR_XML_SUFFIX)) continue
+            // res/color/foo.xml → name = "foo"
+            val rel = n.substring(AppLibraryResourceConstants.AAR_COLOR_DIR_PREFIX.length)
+            val baseName = rel.removeSuffix(AppLibraryResourceConstants.COLOR_XML_SUFFIX)
+            // res/color-night/, res/color-v23/ 등 qualifier 디렉토리는 prefix 가 다름 (res/color-night/) →
+            // 본 prefix 매치는 정확히 res/color/ 만. qualifier 처리는 W4+ density/night-mode 지원 시 추가.
+            if (baseName.contains('/')) continue
+            val rawXml = zip.getInputStream(e).bufferedReader().use { it.readText() }
+            out += ParsedNsEntry.ColorStateList(baseName, rawXml, ResourceNamespace.RES_AUTO, pkg)
+        }
+        return out
+    }
```

추가 import: `java.util.zip.ZipEntry`.

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt`

```diff
 internal class LayoutlibResourceBundle private constructor(
     private val byNs: LinkedHashMap<ResourceNamespace, NsBucket>,
 )
 {
     ...
+    /**
+     * W3D4-β T12: color state list (`<selector>` XML) 의 raw body lookup.
+     * MinimalLayoutlibCallback.getParser 가 호출 — Bridge ResourceHelper.getColorStateList
+     * 의 input feed 단계.
+     */
+    fun getColorStateListXml(ref: ResourceReference): String? =
+        byNs[ref.namespace]?.colorStateLists?.get(ref.name)
+
+    fun colorStateListCountForNamespace(ns: ResourceNamespace): Int =
+        byNs[ns]?.colorStateLists?.size ?: 0
```

`NsBucket` 도 변경 (`NsBucket.kt` 동일 패키지 내 별도 파일 가정 — 실제 위치 확인 후 필드 추가):

```diff
 internal data class NsBucket(
     val byType: Map<ResourceType, Map<String, ResourceValue>>,
     val styles: Map<String, StyleResourceValueImpl>,
     val attrs: Map<String, AttrResourceValueImpl>,
+    val colorStateLists: Map<String, String>,  // name → raw selector XML body
 )
```

`buildBucket` (LayoutlibResourceBundle.kt:72) 변경:

```diff
         private fun buildBucket(ns: ResourceNamespace, entries: List<ParsedNsEntry>): NsBucket
         {
             val byTypeMut = mutableMapOf<ResourceType, MutableMap<String, ResourceValue>>()
             val attrsMut = LinkedHashMap<String, AttrResourceValueImpl>()
             val stylesMut = LinkedHashMap<String, StyleResourceValueImpl>()
             val styleDefs = mutableListOf<ParsedNsEntry.StyleDef>()
+            val colorStateListsMut = LinkedHashMap<String, String>()

             for (e in entries) when (e)
             {
                 is ParsedNsEntry.SimpleValue -> ...
                 is ParsedNsEntry.AttrDef -> ...
                 is ParsedNsEntry.StyleDef -> styleDefs += e
+                is ParsedNsEntry.ColorStateList ->
+                {
+                    // W3D4-β T12: byType[COLOR] 에 placeholder ResourceValue 도 등록 →
+                    // BridgeContext 의 getResource 단계 통과 보장. value 는 magic placeholder
+                    // (callback.getParser 가 가로챔 — fallback ParserFactory.create 에 도달 안 함).
+                    val typeMap = byTypeMut.getOrPut(ResourceType.COLOR) { mutableMapOf() }
+                    if (!typeMap.containsKey(e.name))
+                    {
+                        val ref = ResourceReference(ns, ResourceType.COLOR, e.name)
+                        typeMap[e.name] = ResourceValueImpl(ref, AppLibraryResourceConstants.COLOR_STATE_LIST_PLACEHOLDER_VALUE, null)
+                    }
+                    if (colorStateListsMut.containsKey(e.name))
+                    {
+                        System.err.println(
+                            "[LayoutlibResourceBundle] dup color-state-list '${e.name}' ns=${ns.packageName ?: "RES_AUTO"} from ${e.sourcePackage} — first-wins"
+                        )
+                    }
+                    else
+                    {
+                        colorStateListsMut[e.name] = e.rawXml
+                    }
+                }
             }
             ...
             return NsBucket(
                 byType = byTypeMut.mapValues { it.value.toMap() },
                 styles = stylesMut.toMap(),
                 attrs = attrsMut.toMap(),
+                colorStateLists = colorStateListsMut.toMap(),
             )
         }
```

byType[COLOR] 의 단일색 vs colorStateLists 충돌 정책: `<color name="foo">#fff</color>` (values.xml) 가 먼저 등록되면 `colorStateLists` 에는 없는 상태 (`first-wins per map`); `res/color/foo.xml` 가 먼저 등록되면 byType[COLOR][foo] = placeholder 로 들어가고 이후 단일-색 등장 시 SimpleValue 의 later-wins 가 placeholder 를 덮어써 — 진단 로그 출력. 실제 충돌 가능성 매우 낮음 (AAR 가 같은 name 을 두 곳에 동시 정의하지 않음).

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`

생성자 + getParser:

```diff
 class MinimalLayoutlibCallback(
     private val viewClassLoaderProvider: () -> ClassLoader,
     private val initializer: ((ResourceReference, Int) -> Unit) -> Unit,
+    private val colorStateListLookup: (ResourceReference) -> String? = { null },
 ) : LayoutlibCallback() {

     ...

-    override fun getParser(layoutResource: ResourceValue?): ILayoutPullParser? = null
+    override fun getParser(layoutResource: ResourceValue?): ILayoutPullParser? {
+        // W3D4-β T12: Bridge ResourceHelper.getColorStateList 의 InputStream feed.
+        // layoutResource 가 RES_AUTO color state list 이면 raw selector XML 을 KXmlParser 에
+        // setInput(StringReader) 로 공급. layout(`@layout/...`) 등 다른 resource 는 null 유지.
+        if (layoutResource == null) return null
+        if (layoutResource.resourceType != ResourceType.COLOR) return null
+        val ref = layoutResource.asReference() ?: return null
+        val rawXml = colorStateListLookup(ref) ?: return null
+        return SelectorXmlPullParser.fromString(rawXml)
+    }
```

신규 helper:

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SelectorXmlPullParser.kt`

```kotlin
package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.ILayoutPullParser
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * W3D4-β T12 (round 3 reconcile): color state list (`<selector>` XML) 의 raw body 를
 * KXmlParser 에 StringReader 로 feed. layoutlib 의 ILayoutPullParser 계약 = XmlPullParser
 * + getViewCookie() + getLayoutNamespace() 세 가지 모두 구현 (Codex / Claude 양쪽이
 * 독립적으로 round 3 에서 catch — getLayoutNamespace 누락 시 ResourceHelper.getXmlBlockParser
 * 호출 직후 NullPointerException / UnsupportedOperationException).
 */
internal class SelectorXmlPullParser private constructor(
    private val mDelegate: KXmlParser,
) : ILayoutPullParser, XmlPullParser by mDelegate
{
    override fun getViewCookie(): Any? = null

    /**
     * Bridge ResourceHelper.getXmlBlockParser:50 가 getParser 직후 호출. selector 색상은
     * RES_AUTO ns 의 res/color/*.xml 에서 왔으므로 RES_AUTO 반환.
     */
    override fun getLayoutNamespace(): com.android.ide.common.rendering.api.ResourceNamespace =
        com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO

    companion object
    {
        fun fromString(rawXml: String): ILayoutPullParser
        {
            val parser = KXmlParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(rawXml))
            return SelectorXmlPullParser(parser)
        }
    }
}
```

#### MinimalLayoutlibCallback 호출처 (생성자 인자 추가)

`grep -rn "MinimalLayoutlibCallback(" server/layoutlib-worker/src/` 로 모든 호출처 enumerate:
- LayoutlibRenderer.kt
- SharedLayoutlibRenderer.kt
- 단위테스트 (MinimalLayoutlibCallbackTest 등)

각 호출처에서 `colorStateListLookup` 인자를 named 로 추가 (default `{ null }` 라 backwards-safe; 단위테스트는 명시적으로 제공 권장).

LayoutlibRenderer 의 wiring (가장 중요):
```kotlin
val bundle = LayoutlibResourceValueLoader.loadOrGet(args)
val callback = MinimalLayoutlibCallback(
    viewClassLoaderProvider = { ... },
    initializer = { register -> RJarSymbolSeeder.seed(rJarPath, rJarLoader, register) },
    colorStateListLookup = { ref -> bundle.getColorStateListXml(ref) },
)
```

`asReference()` helper — `ResourceValue` 의 ResourceReference 좌표 추출 (이미 존재 여부 확인; 없으면 `ResourceValue.asReference(): ResourceReference?` extension 작성):

```kotlin
private fun ResourceValue.asReference(): ResourceReference? {
    val ns = namespace ?: return null
    val type = resourceType ?: return null
    return ResourceReference(ns, type, name)
}
```

(`ResourceValue` 의 `getNamespace()/getResourceType()/getName()` API 가 layoutlib 14.0.11 에서 모두 non-null 보장이면 단순화 가능 — 검증 후 결정.)

### §4.2 신규 단위 테스트 (T12.tests)

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerColorTest.kt`

- `walkOne - res/color/*.xml entries are emitted as ColorStateList` (test fixture: 작은 mock AAR 또는 Material AAR sample 활용).
- `walkOne - color qualifier dirs (color-night/, color-v23/) are skipped`.
- `walkOne - empty res/color/ → no ColorStateList entries`.

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleColorStateListTest.kt`

- `getColorStateListXml - returns raw XML for registered name`.
- `getColorStateListXml - returns null for unknown name / wrong namespace`.
- `dup color state list logs first-wins`.

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackColorParserTest.kt`

- `getParser - returns null for layout resource`.
- `getParser - returns null when colorStateListLookup misses`.
- `getParser - returns ILayoutPullParser fed with raw XML on hit`.
- `getParser - parser returns START_TAG selector → item structure`.

### §4.3 IT 영향
- `MaterialFidelityIntegrationTest` 4/4 — color state list 를 미사용하는 path 라 회귀 가능성 낮음.
- `LayoutlibRendererTier3MinimalTest` 14 PASS — 변경 없음.
- `tier3-basic-primary` (현재 SKIP) — Gap A + B 모두 해결 후 PASS 가능. 측정.

---

## §5 T13 — Acceptance gate close

### §5.1 변경

#### `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`

```diff
-    @org.junit.jupiter.api.Disabled(
-        "W3D4-β carry: T1-T8 자료구조 + chain walker 정상 (MaterialFidelityIntegrationTest 4/4 PASS), " +
-            "단 primary 렌더 시 2 production-pipeline gap — (1) Material ThemeEnforcement.checkAppCompatTheme 가 " +
-            "Theme.AxpFixture 를 AppCompat descendant 로 인식 못함 (sentinel attr seeding 필요), " +
-            "(2) Bridge getColorStateList 가 RES_AUTO 의 color XML state list (e.g. m3_highlighted_text) input feed wiring 부재. " +
-            "Plan-revision (W3D4-β) 의 T11/T12 fix 후 @Disabled 제거.",
-    )
     @Test
     fun `tier3 basic primary — activity_basic 가 직접 SUCCESS`()
```

**round 3 reconcile — cache invalidation 안전망 추가** (Claude Q6.3):
`LayoutlibResourceValueLoader.cache` 는 JVM-wide ConcurrentHashMap. T11/T12 가 bundle 구조 (colorStateLists 추가) + namespace 통일 변경을 하므로, 동일 JVM 안 prior test 가 stale bundle 을 만들어두면 acceptance gate 검증이 무력해질 위험. `@BeforeEach` 로 cache 명시 invalidation:

```diff
+    @org.junit.jupiter.api.BeforeEach
+    fun resetBundleCache()
+    {
+        // round 3 reconcile (Claude Q6.3): JVM-wide bundle cache 가 stale 면 T11/T12
+        // 효과 측정 무효화. 각 test 시작 시 명시 clear.
+        dev.axp.layoutlib.worker.resources.LayoutlibResourceValueLoader.clearCache()
+    }
+
     @Test
     fun `tier3 basic primary — activity_basic 가 직접 SUCCESS`()
```

### §5.2 PASS 조건
- `Result.Status.SUCCESS == renderer.lastSessionResult?.status`
- `bytes.size > 1000`
- `isPngMagic(bytes) == true`
- IT 수: 14 → **15 PASS** + 1 SKIP (tier3-glyph 만 carry).
- 모듈 합산 unit + IT 변경 후 재측정.

### §5.3 실패 시 escalation 정책
- 단계별 격리 검증: T11 단독 적용 후 primary 가 어디서 실패하는가? T12 단독은? 둘 다 적용은?
- 실패 stack trace + 원인 분류 후 `t13-acceptance-gate-followup.md` 작성 → plan v3.1 또는 W3D4-γ.

### §5.4 round 3 reconcile — known follow-up surfaces

**T12.5 — Drawable selector feed (Claude Q6.4 반영)**: layoutlib `ResourceHelper.getXmlBlockParser` 는 `getDrawable` 도 호출 → `<selector>` drawable XML (예: Material `res/drawable/m3_btn_*.xml` 의 ripple state-list) 도 callback.getParser 를 통한 input feed 가 필요. T12 의 COLOR-only filter 는 DRAWABLE 을 제외하므로, T11+T12 적용 후 primary fail 위치가 "ColorStateList feed" 에서 "Drawable selector feed" 로 shift 하면 즉시 T12.5 (DRAWABLE XML walker — analogous wiring) 를 plan v3.1 로 escalate. 본 plan 은 T12.5 를 미리 spec 하지 않음 — empirical 측정 후 결정.

**Q6.5 — R$styleable (Claude open critique)**: ConstraintLayout 의 `app:layout_constraintTop_toTopOf` 는 R$styleable 배열을 통해 parse — 현재 RJarSymbolSeeder.kt:56-59 가 R$styleable 를 skip (round 2 A2 결정 = layoutlib Bridge.parseStyleable 가 처리 가정). activity_basic.xml 의 ConstraintLayout 이 이 경로에 의존하므로, T11+T12 적용 후 ConstraintLayout positioning 단계에서 별도 fail 가능성 존재 — primary fail 시 stack trace 우선 검사.

---

## §6 Q1–Q5 — round 3 pair-review query (Codex+Claude)

본 plan v3 를 round 3 plan-revision review 에 부치는 핵심 질문 (handoff §4.4 + 본 plan empirical):

### Q1 — Gap A root cause 본질
"§1.3 의 dual namespace policy 불일치 (RJarSymbolSeeder:110 = fromPackageName vs AarResourceWalker:71 = RES_AUTO) 가 ThemeEnforcement.checkAppCompatTheme 실패의 **유일한** root cause 인가? StyleResourceValueImpl.getItem(ref) 가 namespace-exact match 외 fallback path 가 layoutlib 14.0.11 내부에 있는지 (예: name-only fallback) — 검증 가능한가?"

### Q2 — RES_AUTO 통일 vs 양방향 정합 (대안)
"§3.1 의 fix 는 RJarSymbolSeeder 를 RES_AUTO 통일. 대안: AarResourceWalker 를 fromPackageName 통일 (= bundle 의 byNs 가 N-bucket 으로 확장 + chain walker 가 ns-traversal). 어느 쪽이 W4 (per-namespace styleable, configuration override) 와 호환성이 더 좋은가? RES_AUTO 통일이 미래 회귀를 만드는가?"

### Q3 — Cross-type first-wins 의 실제 충돌 빈도
"§3.1 의 ResourceTypeFirstWinsGuard 는 ATTR 외 STYLE/COLOR/DIMEN 도 first-wins. 실측 sample-app (appcompat 1.7.1 + material 1.13.0 + axp.fixture) 에서 cross-class 동명 충돌이 ATTR 외 type 에서 실제 발생하는가? 발생 시 first-wins 우선순위 (R class 이름 sort 순? package 의존?) 가 결정성 있는가?"

### Q4 — Color qualifier 디렉토리 처리 (color-night/, color-v23/)
"§4.1 collectColorStateLists 는 `res/color/` 만 매치, qualifier (color-night/, color-v23/) 는 skip. activity_basic.xml 렌더 시 default qualifier 만으로 충분한가? Material3 의 m3_highlighted_text 는 day/night 분리 가정 — `tier3-basic-primary` PASS 에 영향?"

### Q5 — MinimalLayoutlibCallback.getParser 의 layout resource fallback
"§4.1 의 getParser override 는 ResourceType.COLOR 만 처리, 그 외 (LAYOUT/MENU/DRAWABLE 등) 는 null. layoutlib Bridge 가 layout resource 를 callback.getParser 로 묻는 path 가 존재하는가? 만약 그렇다면 본 변경이 layout resource path 를 silently break 하는가?"

---

## §7 LM (landmines) 회피 정책

| LM 코드 | 사유 | 본 plan 의 방어 |
|---|---|---|
| LM-W3D3-A / LM-α-A | empirical-verifiable claim 직접 측정 | §1.3, §2.3 의 file:line citations + §2.2 census = 실측 |
| LM-α-B | Codex stalled-final → single-source verdict + 명시 flagging | round 3 pair 시 적용 |
| LM-G | codex exec sandbox bypass | round 3 직접 CLI: `codex exec --skip-git-repo-check --sandbox danger-full-access` (MEMORY.md feedback 준수) |
| LM-W3D3-B | JUnit Jupiter Assertions only | §3.2/§4.2 신규 테스트는 `org.junit.jupiter.api.Assertions.*` 만 |
| LM-α-D | Kotlin backtick 함수명 안 마침표 금지 | §3.2/§4.2 의 backtick 테스트명 검증 |
| LM-W3D4-D | placeholder 관행 | 본 plan 의 모든 fix 는 §3.1 / §4.1 의 explicit diff |
| LM-W3D4-E | mixed-content xliff:g | T12 는 selector XML 만 — values.xml mixed content 는 미해당 |
| LM-W3D4-F | cross-NS attr ref `:` | T11 namespace 통일은 attr name 본문 변경 없음 |
| LM-W3D4-G/H | 본 plan v3 의 1차 목표 자체 | T11/T12 가 직접 닫음 |

---

## §8 Test impact summary

| 단계 | unit 수 | IT 수 | 비고 |
|---|---|---|---|
| baseline (현재) | 151 | 14 PASS + 2 SKIP | tier3-basic-primary `@Disabled` |
| T11 적용 후 | 151 + 4 (RJarSymbolSeederNamespaceTest) + 4 (ResourceTypeFirstWinsGuardTest) - 4 (AttrSeederGuardTest 인계) = 155 | 14 PASS + 2 SKIP (변동 없음) | namespace 통일 단독 효과 측정 |
| T12 적용 후 | 155 + 3 (Walker) + 3 (Bundle) + 4 (Callback) = 165 | 14 PASS + 2 SKIP | color XML feed |
| T13 적용 후 | 165 (변동 없음) | **15 PASS + 1 SKIP** | tier3-basic-primary 닫힘, tier3-glyph W4 carry 만 |

---

## §9 Commit/push 단위

CLAUDE.md task-unit completion 정책:
- T11 commit: `feat(w3d4-beta): T11 RJarSymbolSeeder RES_AUTO 통일 + cross-type first-wins`
- T12 commit: `feat(w3d4-beta): T12 color state list walker + ParsedNsEntry + callback.getParser`
- T13 commit: `feat(w3d4-beta): T13 tier3-basic-primary @Disabled 제거 + acceptance gate 닫힘`
- 각 task 단위 PASS 후 즉시 push.
- round 3 pair verdict 는 별도 work_log entry: `docs/work_log/2026-04-30_w3d4-beta-plumbing/round3-pair-review.md`.
- session 종료 시 `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` + `handoff.md` (필요 시).

---

## §10 Out-of-scope (본 plan 이 다루지 않는 것)

- color qualifier (color-night/ 등) 처리 → W4+ density/locale/night-mode 지원 시.
- per-namespace bundle bucket 확장 (RES_AUTO 통일 폐기) → W4+ namespace-aware styleable 시.
- tier3-glyph (Font wiring) → W4 carry.
- ConstraintLayout 의 다른 미해결 attr (있다면) — primary fail 후 재검토.
- DRAWABLE selector XML feed (`res/drawable/<selector>.xml`) → §5.4 T12.5 escalation 대상 (empirical 측정 후 v3.1).

---

## §11 Round 3 reconcile summary (2026-04-30)

| 채널 | verdict | confidence | 주요 finding |
|---|---|---|---|
| Codex (latest GPT, xhigh) | GO_WITH_FIXES | 0.86 | Q1 strict ns match (javap StyleResourceValueImpl) / Q2 RES_AUTO right tactical / Q3 R.jar 578 dup but same-id / Q4 base-only OK / Q5 no regression / **Q6 SelectorXmlPullParser missing getLayoutNamespace** |
| Claude (Plan agent) | GO_WITH_FIXES | 0.78 | Q1 동일 결론 / Q2 AAPT non-namespaced ID 동일 증거 / Q3 widespread collision empirical / Q4 192 (1.12.0/1.6.1 정정) / Q5 동일 / **Q6 same getLayoutNamespace catch (independent)** + cache invalidation + T12.5 escalation |

**Convergence**: Q1, Q2, Q5, Q6 (SelectorXmlPullParser missing override) 모두 양쪽 독립 동일 결론 → 최강 신호. Q3, Q4 결론 동일하나 census 수치 차이 (Codex 가 1.13.0/1.7.1 캐시 검사, Claude 가 실제 빌드 1.12.0/1.6.1 검사 — Claude 수치가 정확).

**Adopted plan deltas (5)**:
1. SelectorXmlPullParser `getLayoutNamespace()` 추가 (§4.1).
2. ResourceTypeFirstWinsGuard 를 `Map<String, Int>` 로 강화 (same-id silent / different-id loud) (§3.1, §3.2).
3. §2.2 census 정정: 1.13.0/1.7.1 → 1.12.0/1.6.1, 236 → 192.
4. §1.4 collateral 설명 정정: "AAR간 disjoint" 가정 → "widespread collision but same ID per AAPT".
5. §5.1 LayoutlibRendererIntegrationTest 에 `@BeforeEach { LayoutlibResourceValueLoader.clearCache() }` 추가.

**Adopted escalation notes (2)**:
- §5.4 T12.5 (drawable selector feed) — primary fail surface shift 시 즉시 escalate.
- §5.4 Q6.5 (R$styleable for ConstraintLayout) — primary fail stack trace 우선 검사.

**최종 verdict**: **GO** (single-source verdict 아님 — dual-channel convergence). T11/T12/T13 구현 진입 승인.
