# W3D4-δ-A spec — TextAppearance sentinel + ThemeEnforcement multi-sentinel surface

생성: 2026-05-04 (W3D4-γ T16 partial `83d432a` 직후)
선행 head: `83d432a` (main, T16 partial — γ-A 3 warning closed + W3D4-δ escalation)
phase: **W3D4-δ-A plumbing** (TextAppearance sentinel close + ThemeEnforcement 7-attr 전체 census)
범위: T17 (diagnostic) · T18 (fix, hypothesis-conditional) · T19 (acceptance gate). 본 plan v3.2 는 plan v3.1 위에 차곡 — 이전 자료구조 (ParsedNsEntry/NsBucket/Bundle/9-override RenderResources) 는 변경 없음, ThemeEnforcement multi-sentinel surface 만 닫음.
선행 plan: `docs/superpowers/plans/2026-04-30-w3d4-gamma-attr-enum-flag.md` (plan v3.1, T14/T15/T16)

---

## §0 Entry context (cold-start safe)

### §0.1 작업 흐름의 위치

- W3D4-β plan v3 (T11/T12/T13) 적용 — Gap A (Material ThemeEnforcement sentinel attr namespace mismatch via `colorPrimary`) + Gap B (color XML state list feed) 닫힘.
- W3D4-γ plan v3.1 (T14/T15/T16) 적용 — `<attr>` enum/flag 자식 캡처 + RES_AUTO/framework 양 path 의 attr 값 변환 wiring → 3 enum/flag warning ("vertical/center_horizontal/parent is not a valid integer") 모두 닫힘.
- W3D4-γ T16 측정 시 plan v3 §5.4 escalation 정책 다시 적중 — fail surface 가 *제 4 layer* 로 shift: `com.google.android.material.internal.ThemeEnforcement.checkTextAppearance` 가 `IllegalArgumentException("This component requires that you specify a valid TextAppearance attribute. Update your app theme to inherit from Theme.MaterialComponents (or a descendant).")` throw → `createSession` ERROR_INFLATION.
- 본 plan v3.2 가 그 새 fail surface 닫음. round 4 의 explicit-diff 정책 (LM-W3D4-D 회피) + Q6 open critique 슬롯 패턴 (LM-W3D4-β-H pair-review 인계) 일관 유지.

### §0.2 본 plan v3.2 가 변경하지 않는 것 (불변식)

- `LayoutlibResourceBundle` 의 byNs 2-bucket (ANDROID + RES_AUTO) 구조.
- `LayoutlibRenderResources` 의 9 method override 구성 + `MAX_REF_HOPS=10` / `MAX_THEME_HOPS=32`.
- `ParsedNsEntry.AttrDef` 5-arg ctor (γ T14 의 enumValues/flagValues 필드).
- `RJarSymbolSeeder` 의 RES_AUTO 통일 + R$style underscore↔dot canonicalization (β T11 + W3D4 R-1).
- `AarResourceWalker` 의 color state list 수집 + `MinimalLayoutlibCallback.getParser` wiring (β T12).
- `LayoutlibResourceValueLoader` 의 3-입력 통합 + `clearCache()` BeforeEach + `frameworkEnumValueMap()` export (γ T15).
- `LayoutlibResourceBundle.getResource(ref)` 의 ATTR special-case (γ T14 round 4 Q5).
- `MaterialFidelityIntegrationTest` 4/4 PASS, `tier3-basic-minimal-smoke` PASS, `LayoutlibRendererTier3MinimalTest` 14/14 PASS — 모두 entry criterion.

### §0.3 baseline (2026-05-04 측정, T16 partial 후)

- `cd server && ./gradlew test --console=plain` → BUILD SUCCESSFUL, **237 unit total** (16 protocol + 5 http-server + 22 mcp-server + 194 layoutlib-worker) / 0 fail.
- `cd server && ./gradlew :layoutlib-worker:test -PincludeTags=integration` → **14 IT PASS + 2 SKIP** (`tier3-basic-primary` W3D4-δ-A carry, `tier3-glyph` W4 carry).
- 현재 branch: `main`, head `83d432a`.

---

## §1 Empirical findings (round 5 의 baseline 입력)

본 plan 작성 전 3-stream parallel investigation (subagent A: ThemeEnforcement bytecode census · subagent B: Theme.AxpFixture parent chain · subagent C: failing widget identification) 결과. 모든 claim 은 file:line 인용. unzip 산출물은 `/tmp/w3d4d-{themeenforcement,themechain,widgets}/` 에 보존 (LM-W3D4-β-G 회피).

### §1.1 ThemeEnforcement 의 7-attr 전체 sentinel surface

`com.google.android.material.internal.ThemeEnforcement` (material-1.12.0.aar `classes.jar`) 가 검사하는 sentinel attr 의 exhaustive census:

| # | attr name | 검사 메서드 | 검사 방식 | throw 메시지 (해당 시) |
|---|---|---|---|---|
| 1 | `enforceMaterialTheme` (boolean, RES_AUTO) | `checkCompatibleTheme` (private, `obtainStyledAttributes` 가 호출) | `R.styleable.ThemeEnforcement` 의 slot 1 (alphabetical) `getBoolean` — `true` 면 `checkMaterialTheme` 호출 | indirect — true + isMaterialTheme 미정의 시 `checkMaterialTheme` throw |
| 2 | `enforceTextAppearance` (boolean, RES_AUTO) | `checkTextAppearance` (private) | `R.styleable.ThemeEnforcement` 의 slot 2 `getBoolean` — `true` 면 textAppearance probe 진행 | gate — false 면 throw 우회 |
| 3 | `android:textAppearance` (framework) | `checkTextAppearance` (default-slot branch) | `R.styleable.ThemeEnforcement` 의 slot 0 `getResourceId(slot, -1) != -1` | **본 phase 의 직접 trigger** — `"This component requires that you specify a valid TextAppearance attribute. Update your app theme to inherit from Theme.MaterialComponents (or a descendant)."` |
| 4 | `isMaterialTheme` (boolean, RES_AUTO) | `checkCompatibleTheme` (`Theme.resolveAttribute`) | theme 에 attr 정의 없거나 false → `checkMaterialTheme` 진행 | indirect |
| 5 | `isMaterial3Theme` (boolean, RES_AUTO) | `MaterialAttributes.resolveBoolean` (`isMaterial3Theme`) | feature gate (no throw) — `MaterialCheckBox` / `TabLayout` 의 M3-vs-M2 분기 | non-throw |
| 6 | `colorPrimary` (RES_AUTO) | `checkAppCompatTheme` / `isAppCompatTheme` | `int[]{R.attr.colorPrimary}` + `TypedArray.hasValue(0)` | **이미 W3D4-β 닫힘** — Theme.AxpFixture L7 명시 정의 + RJarSymbolSeeder RES_AUTO 통일 |
| 7 | `colorPrimaryVariant` (RES_AUTO) | `checkMaterialTheme` / `isMaterialTheme` | `int[]{R.attr.colorPrimaryVariant}` + `hasValue(0)` | `"The style on this component requires your app theme to be Theme.MaterialComponents (or a descendant)."` |

**ThemeEnforcement styleable 정의** (`/tmp/w3d4d-themeenforcement/res/values/values.xml:8882-8889`):

```xml
<declare-styleable name="ThemeEnforcement">
    <attr format="boolean" name="enforceMaterialTheme"/>
    <attr format="boolean" name="enforceTextAppearance"/>
    <attr name="android:textAppearance"/>
</declare-styleable>
```

**slot index** (alphabetical, R.jar 의 styleable 정렬 규칙):
- slot 0 = `ThemeEnforcement_android_textAppearance` (framework attr 가 alphabetical 우선)
- slot 1 = `ThemeEnforcement_enforceMaterialTheme`
- slot 2 = `ThemeEnforcement_enforceTextAppearance`

**caller surface** (subagent A `javap -p` 전체 census): 43 Material 위젯 클래스가 `ThemeEnforcement.obtainStyledAttributes` / `obtainTintedStyledAttributes` 경유 — 모든 호출에서 `checkCompatibleTheme` (가능 시 `checkMaterialTheme`) + `checkTextAppearance` 자동 dispatch. `BaseTransientBottomBar` (Snackbar) 는 `checkAppCompatTheme` 직접 호출, `BadgeDrawable` 는 `checkMaterialTheme` 직접 호출.

`MaterialTextView` 는 ThemeEnforcement 미경유 (MaterialThemeOverlay.wrap 사용) — `<TextView>` element 도 framework `android.widget.TextView` 로 inflate → 본 sentinel 의 호출 path 부재. **본 phase 의 trigger 는 `<com.google.android.material.button.MaterialButton>` 단일 widget**.

### §1.2 Theme.AxpFixture 의 15-level parent chain

`fixture/sample-app/app/src/main/res/values/themes.xml:5` 의 `Theme.AxpFixture` 가 `Theme.Material3.DayNight.NoActionBar` 상속. subagent B 의 full chain walk (Material AAR `/tmp/w3d4d-themechain/material/res/values/values.xml`, AppCompat AAR `/tmp/w3d4d-themechain/appcompat/res/values/values.xml`):

| Lvl | style 이름 | 명시/암시 parent | source AAR | file:line | 본 phase 의 sentinel 관련 item |
|---|---|---|---|---|---|
| 0 | `Theme.AxpFixture` | 명시 `Theme.Material3.DayNight.NoActionBar` | sample-app | `themes.xml:5` | colorPrimary/On/Container/Surface/SurfaceVariant 5 items |
| 1 | `Theme.Material3.DayNight.NoActionBar` | 명시 `Theme.Material3.Light.NoActionBar` | material | `material/values.xml:4294` | alias-only |
| 2 | `Theme.Material3.Light.NoActionBar` | **암시 dot-trim** `Theme.Material3.Light` | material | `material/values.xml:4320` | windowActionBar/windowNoTitle |
| 3 | `Theme.Material3.Light` | 명시 `Base.Theme.Material3.Light` | material | `material/values.xml:4308` | alias-only |
| 4 | `Base.Theme.Material3.Light` | 명시 `Base.V14.Theme.Material3.Light` | material | `material/values.xml:1522` | alias-only |
| **5** | **`Base.V14.Theme.Material3.Light`** | 명시 `Theme.MaterialComponents.Light` | material | `material/values.xml:2141` | **`materialButtonStyle → @style/Widget.Material3.Button` (L2273)**, `colorPrimaryVariant → ?attr/colorPrimary` (L2214), `isMaterial3Theme → true` (L2142) |
| 6 | `Theme.MaterialComponents.Light` | 명시 `Base.Theme.MaterialComponents.Light` | material | `material/values.xml:4370` | alias-only |
| 7 | `Base.Theme.MaterialComponents.Light` | 명시 `Base.V14.Theme.MaterialComponents.Light` | material | `material/values.xml:1557` | alias-only |
| 8 | `Base.V14.Theme.MaterialComponents.Light` | 명시 `Base.V14.Theme.MaterialComponents.Light.Bridge` | material | `material/values.xml:2958` | textAppearanceLargePopupMenu/SmallPopupMenu |
| **9** | **`Base.V14.Theme.MaterialComponents.Light.Bridge`** | 명시 `Platform.MaterialComponents.Light` | material | `material/values.xml:3020` | **`textAppearanceButton → @style/TextAppearance.MaterialComponents.Button` (L3068)**, `colorPrimaryVariant → @color/design_default_color_primary_variant` (L3023), `isMaterialTheme → true` (L3021) |
| 10 | `Platform.MaterialComponents.Light` | 명시 `Theme.AppCompat.Light` | material | `material/values.xml:3830` | none |
| 11 | `Theme.AppCompat.Light` | 명시 `Base.Theme.AppCompat.Light` | appcompat | `appcompat/values.xml:1589` | alias-only |
| 12 | `Base.Theme.AppCompat.Light` | 명시 `Base.V7.Theme.AppCompat.Light` | appcompat | `appcompat/values.xml:438` | alias-only |
| 13 | `Base.V7.Theme.AppCompat.Light` | 명시 `Platform.AppCompat.Light` | appcompat | `appcompat/values.xml:735` | `android:textAppearanceButton → @style/TextAppearance.AppCompat.Widget.Button` (L873) |
| 14 | `Platform.AppCompat.Light` | 명시 `android:Theme.Holo.Light` | appcompat | `appcompat/values.xml:1366` | framework `android:textAppearance{,Inverse,Large,…,ListItemSmall}` 11 items (L1401-L1417) |
| 15 | `android:Theme.Holo.Light` → `…` → `android:Theme` | (framework) | layoutlib | framework `data/res/values/styles.xml` | framework defaults |

**핵심 pivot 두 개**:
- **Lvl 5** `Base.V14.Theme.Material3.Light:2273` 가 `materialButtonStyle → @style/Widget.Material3.Button` 정의 — chain 도달 시 MaterialButton 의 defStyleAttr (`R.attr.materialButtonStyle`) 이 M3 스타일로 dispatch → `Widget.Material3.Button:5239` 가 `enforceTextAppearance=false` 명시 → `checkTextAppearance` 우회.
- **Lvl 9** `Base.V14.Theme.MaterialComponents.Light.Bridge:3068` 가 `textAppearanceButton → @style/TextAppearance.MaterialComponents.Button` 정의 — chain 도달 시 M2 fallback path 의 `?attr/textAppearanceButton` 가 concrete style 로 resolve → `getResourceId(slot, -1)` 가 -1 아님 → 우회.

**한 path 만 작동해도 throw 회피**. 두 path 모두 closed 상태 = δ-A 가 닫힘.

### §1.3 Failing widget call chain (subagent C 의 bytecode-cited)

`fixture/sample-app/app/src/main/res/layout/activity_basic.xml:43-48` 의 `<com.google.android.material.button.MaterialButton id="@+id/primary_button">`. 본 widget 만 ThemeEnforcement 호출 (다른 elements: `<ConstraintLayout>`, `<LinearLayout>`, `<TextView>` 2개 — 모두 framework / 직접 호출 없음).

**`MaterialButton.<init>(Context, AttributeSet, int)` bytecode** (`/tmp/w3d4d-widgets/classes_extracted/com/google/android/material/button/MaterialButton.class`, javap offset):

```
41: aload_1                         // Context
42: aload_2                         // AttributeSet
43: getstatic R$styleable.MaterialButton:[I
46: iload_3                         // defStyleAttr
47: getstatic DEF_STYLE_RES:I       // R.style.Widget_MaterialComponents_Button (M2)
50: iconst_0
51: newarray int                    // empty int[] for textAppearanceIndices
53: invokestatic ThemeEnforcement.obtainStyledAttributes(...)
```

**`ThemeEnforcement.checkTextAppearance` bytecode** (`/tmp/w3d4d-themeenforcement/te_disasm.txt:158-239`):

```
0-8:    a = ctx.obtainStyledAttributes(set, R.styleable.ThemeEnforcement, defStyleAttr, defStyleRes)
15-22:  enforceTextAppearance = a.getBoolean(slot=2, false); if !enforce → recycle + return
35-46:  if textAppearanceIndices == null || length == 0  // TRUE for MaterialButton
46-56:    validId = a.getResourceId(slot=0 /* android:textAppearance */, -1) != -1
92:     if !valid → throw IllegalArgumentException("This component requires that you specify a valid TextAppearance attribute…")
```

**`Widget.MaterialComponents.Button` style** (`material/values.xml:6348-6353` — M2 default 의 source of truth):

```xml
<style name="Widget.MaterialComponents.Button" parent="Widget.AppCompat.Button">
    <item name="enforceMaterialTheme">true</item>
    <item name="android:background">@empty</item>
    <item name="enforceTextAppearance">true</item>
    <item name="android:textAppearance">?attr/textAppearanceButton</item>
    …
</style>
```

**`Widget.Material3.Button` style** (`material/values.xml:5238-5239` — M3 우회 path):

```xml
<style name="Widget.Material3.Button" parent="Widget.MaterialComponents.Button">
    <item name="enforceTextAppearance">false</item>
    …
</style>
```

**bug 의 정확한 trigger** (round 5 Q1 reconcile — fail mode 정확화): MaterialButton 가 `defStyleAttr=R.attr.materialButtonStyle` 으로 inflate → BridgeContext 의 styled-attrs lookup (BridgeContext-javap.txt:1879/1926/1959 trace) 이 `RenderResources.findItemInTheme(materialButtonStyle attr ref)` 호출 → 우리 chain walker 가 Lvl 5:2273 의 `<item name="materialButtonStyle">@style/Widget.Material3.Button</item>` 발견 (item discovery 단계 정상). 후속 `resolveResValue("@style/Widget.Material3.Button")` 가 chain walker 의 STYLE ref hop (LayoutlibRenderResources.kt:171-178) 에서 `getUnresolvedResource(STYLE ref) → bundle.getResource(ref)` 호출 → `LayoutlibResourceBundle.kt:50-58` 의 `byType[STYLE]?.get(name)` 경로가 null 반환 (styles 는 `bucket.styles` 분리 저장). chain walker 의 `current` 가 unresolved StyleItem 그대로 — BridgeContext offset 312-325 의 `instanceof StyleResourceValue` cast fail → defStyleRes (M2 `Widget.MaterialComponents.Button`) fallback 진입 → `enforceTextAppearance=true` + `android:textAppearance=?attr/textAppearanceButton`. M2 path 도 sibling fail: `BridgeTypedArray.getResourceId(slot=0, -1)` (BridgeTypedArray-javap.txt:1052/1080) 가 chain walker 결과의 instanceof StyleResourceValue check 동일 fail → -1 반환 → **throw**.

### §1.4 contrasting `tier3-basic-minimal-smoke` PASS 의 의미

`activity_basic_minimal.xml:21` 가 `<Button>` (framework `android.widget.Button`) 사용 — Material 위젯 부재 → ThemeEnforcement 호출 path 부재 → 본 sentinel 미발화. 본 minimal smoke 의 PASS 는 layoutlib core path (R.jar seeding, BridgeContext bootstrap, theme resolution 일부) 정상 작동 증명. δ-A 의 surface 는 **Material widget gateway** 로 좁혀진다.

### §1.5 Sentinel attr 정의 location 검증

| sentinel attr | namespace | declaration | item 정의 (theme chain 위) |
|---|---|---|---|
| `enforceMaterialTheme` | RES_AUTO | material `attrs.xml` (declare-styleable ThemeEnforcement) | various style 들이 정의 (e.g. `Widget.MaterialComponents.Button:6349`) |
| `enforceTextAppearance` | RES_AUTO | material `attrs.xml` (declare-styleable ThemeEnforcement) | `Widget.MaterialComponents.Button:6351` (true), `Widget.Material3.Button:5239` (false) |
| `android:textAppearance` | ANDROID | framework `attrs.xml:111` | various Material/AppCompat themes (Lvl 14 framework default 보장) |
| `isMaterialTheme` | RES_AUTO | `material/values.xml:106` | Lvl 9 `Base.V14.Theme.MaterialComponents.Light.Bridge:3021` (true) |
| `isMaterial3Theme` | RES_AUTO | `material/values.xml:105` | Lvl 5 `Base.V14.Theme.Material3.Light:2142` (true) |
| `colorPrimary` | RES_AUTO | appcompat `attrs.xml` | Theme.AxpFixture L7 (W3D4-β 닫힘) |
| `colorPrimaryVariant` | RES_AUTO | `material/values.xml:53` | Lvl 5 `:2214` (`?attr/colorPrimary` chain), Lvl 9 `:3023` (concrete color) |

→ 모든 sentinel attr 가 명시적으로 정의됨 (Material AAR + framework). 누락된 정의 없음. **bug 는 정의 부재가 아닌 chain walker 의 resolution 실패** 임이 확정.

---

## §2 Root cause 후보 정리 + 가설 ranking

본 phase 의 직접 측정 (T16 system-err) 과 §1 census 만으로는 chain 의 어느 layer 에서 끊기는지 단정 불가. 4개 가설 ranking — T17 diagnostic 으로 empirically narrow.

### §2.1 H1 — chain walker 의 dot-parent inference 가 Lvl 5 에 도달 못 함

`StyleParentInference.kt:15-20` 가 dot-trim 을 이미 구현 — explicit parent null 시 `lastDot > 0` 조건에서 dotted-prefix 반환. `LayoutlibResourceBundle.buildBucket:200-211` 이 inferred parent 를 `parentStyleName` 에 set. `LayoutlibRenderResources.walkParent:84-86` 이 `style.parentStyleName` fallback 사용. **§1.2 의 chain 안에서 dot-trim 의존하는 단일 edge** 는 Lvl 2 `Theme.Material3.Light.NoActionBar → Theme.Material3.Light` (Lvl 3 explicit `parent=` 부재 → infer). Lvl 3 이후는 모두 explicit `parent=` (material/values.xml:4308 / 1522 / 2141 / 2958 / 3020 / 3830 등 verified).

**확률**: 낮음. round 5 Codex Q2 verify — `MaterialFidelityIntegrationTest.kt:56-59` 의 chain depth ≥ 15 assertion 이 baseline 에서 green (실측 17 hop) → Lvl 5 IS reachable today. 단 `StyleParentInference` 가 nonexistent parent 도 blind 으로 infer 하는 것이 unrelated alias-only style 에 부작용 가능 (W4+ scope).

### §2.2 H2 — `bundle.getResource(STYLE ref)` 가 byType-only — styles map 누락 (KILL POINT 후보)

`LayoutlibResourceBundle.kt:50-58` 의 `getResource`:

```kotlin
fun getResource(ref: ResourceReference): ResourceValue? {
    val bucket = byNs[ref.namespace] ?: return null
    if (ref.resourceType == ResourceType.ATTR) {
        return bucket.attrs[ref.name]
    }
    return bucket.byType[ref.resourceType]?.get(ref.name)
}
```

`buildBucket:202-222` 의 StyleDef 처리는 `stylesMut[def.name] = sv` 만 호출 — `byTypeMut[STYLE]` 는 채워지지 않음. → **`bundle.getResource(STYLE ref)` 가 항상 null 반환** (style 이 bundle 에 존재하더라도).

체인 walker 의 `resolveResValue:171-178` 가 STYLE ref 만나면 `getUnresolvedResource(ref) → bundle.getResource(ref) → null → return current` (unresolved StyleItem 그대로). Bridge 가 unresolved StyleItem 받으면 reference resolution 실패 → defStyle 못 찾음 → defStyleRes 사용 → M2 path → throw.

`?attr/textAppearanceButton` chain 에서도 동일 — `findItemInTheme(textAppearanceButton)` 가 StyleItem 반환 후, 그 value `@style/TextAppearance.MaterialComponents.Button` 를 resolve 하려는 chain walker 가 동일 nullify.

**확률**: **높음** (H2 가 dominant 가설). γ T14 의 ATTR special-case 와 정확히 sibling case — 자료구조의 byType ↔ styles 분리에서 lookup path 만 byType 만 보는 동일 패턴. round 4 Q5 KILL POINT 의 STYLE 변종.

### §2.3 H3 — R$style 의 symbol seed 가 chain-relevant style 누락

`RJarSymbolSeeder.kt` 가 R.jar 의 모든 R$style 정적 필드를 RES_AUTO 로 register. canonicalization helper `RNameCanonicalization.styleNameToXml` (`Theme_Material3_Light` → `Theme.Material3.Light`) 적용. 그러나:
- 41 AAR 중 일부의 R$style 가 R.jar 안에 없을 가능성 (W3D3-α single R.jar 가정).
- canonicalization regex 가 Material3 의 path-style 이름 (e.g. `Widget_Material3_Button_TextButton_Dialog_Flush`) 처리하는지 검증 필요 — dot 인지 underscore 인지의 ambiguity (예: `Theme.AppCompat.Light.NoActionBar` 의 4-part path).

**확률**: 중간. R.jar 의 R$style symbol 이 callback.byRef 를 통해 layoutlib resource ID resolution 을 지원 — Bridge 가 "@style/Widget.Material3.Button" 을 ID 로 변환 시 callback 의존. 만약 canonicalization 에 갭이 있으면 ID lookup 실패.

### §2.4 H4 — `@macro/...` reference 가 chain walker 미지원

`Widget.Material3.Button:5247` 등 일부 M3 style 이 `@macro/m3_comp_filled_button_label_text_type` 형식 reference 사용. 우리 `parseReference (LayoutlibRenderResources.kt:259-272)` 는 `ResourceUrl.parse` 위임 — `ResourceUrl` 이 macro 를 인식하면 type 이 NEW (not enum 의 STYLE/COLOR/...) → chain 에서 unrecognized type 으로 단절.

**확률**: 낮음. `enforceTextAppearance=false` 가 macro 보다 먼저 (offset 13-22, before macro consumption) 읽힘 → bypass 가 우선. macro 가 throw 의 직접 cause 일 가능성은 적음.

### §2.5 Diagnostic 전략 — T17 가 empirically narrow

T17 의 단위 테스트 (§4) 가 production input (real fixture R.jar + sample-app res + AAR walker) 으로 bundle 을 build → 다음 4 questions 를 직접 query:

1. `bundle.getStyleByName("Base.V14.Theme.Material3.Light")` returns non-null? (H1 검증)
2. `bundle.getResource(ResourceReference(RES_AUTO, STYLE, "Widget.Material3.Button"))` returns non-null? (H2 검증 — 만약 null 이면 byType[STYLE] 누락 확정)
3. `LayoutlibRenderResources(bundle, "Theme.AxpFixture").findItemInTheme(materialButtonStyle attr ref)` returns non-null with value containing "Widget.Material3.Button"?
4. `findItemInTheme(textAppearanceButton attr ref)` returns non-null with value `@style/TextAppearance.MaterialComponents.Button`?

각 question 의 PASS/FAIL 에 따라 H1-H4 가운데 어느 가설이 정확한지 즉시 판정. T18 fix 가 그 결과에 mapped (§5).

---

## §3 Scope & task split

| Task | scope | 의존 |
|---|---|---|
| **T17** | diagnostic — production-input unit test 신규 (theme chain + STYLE ref + ATTR ref resolution 4-question battery). T18 가 어느 가설을 fix 해야 하는지 empirically 판정. | (independent) |
| **T18** | hypothesis-targeted fix — T17 결과에 따라 H1/H2/H3/H4 중 1개 이상 fix 적용. **dominant 예상: H2** — `bundle.getResource(STYLE ref)` 에 styles map fallback. 추가 hardening (§5.5). | T17 측정 결과 |
| **T19** | acceptance gate — `tier3-basic-primary` `@Disabled` 제거 + 1회 IT 실측. PASS 시 δ-A close. fail surface 가 다른 layer 로 shift 시 W3D4-ε escalate (LM-W3D4-β-H 패턴). | T18 |

**T17 의 commit 단위**: T17 의 diagnostic 테스트만 단독 commit (붉은 상태로 land — 측정 도구로서의 가치). T18 가 fix 적용 후 동일 테스트가 green 으로 전환.

---

## §4 T17 — Diagnostic instrumentation

### §4.1 변경 파일

#### `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/W3D4DeltaThemeChainDiagnosticTest.kt` (신규)

production input (real fixture) 으로 5 question 측정 (round 5 reconcile — Q3 Claude delta `resolveResValue StyleItem → is StyleResourceValue` 5번째 probe 추가). `MaterialFidelityIntegrationTest` (위 동일 패키지) 의 `locate(): Pair<Path, Path>?` helper 패턴 직접 mirror — graceful skip + 실 dist/sample-app 입력. Args 필드명 `sampleAppRoot` (NOT `sampleAppModuleRoot` — round 5 Q4 fix).

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import dev.axp.layoutlib.worker.DistDiscovery
import dev.axp.layoutlib.worker.FixtureDiscovery
import dev.axp.layoutlib.worker.session.SessionConstants
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Empirically narrows the W3D4-δ-A root cause. Loads the real fixture bundle and
 * probes the 5 lookup paths that a Material widget's inflate traverses. Each probe
 * maps 1:1 to a hypothesis (H1, H2, H3a, H3b, BridgeTypedArray instanceof gate)
 * defined in plan v3.2 §2 — pass/fail patterns determine which fix in §5 applies.
 *
 * Integration-tagged because all probes depend on real fixture build artefacts
 * (sample-app 의 runtime-classpath.txt + 41 AAR + R.jar). Default unit suite
 * excludes integration tag (axp.kotlin-common.gradle.kts:43-50) — opt-in via
 * -PincludeTags=integration. T17 commit can land RED on main without breaking
 * the default unit gate.
 */
@Tag("integration")
class W3D4DeltaThemeChainDiagnosticTest
{
    private lateinit var bundle: LayoutlibResourceBundle
    private lateinit var resources: LayoutlibRenderResources

    @BeforeEach
    fun loadProductionBundle()
    {
        val (dist, sampleApp) = locate() ?: return
        LayoutlibResourceValueLoader.clearCache()
        val args = LayoutlibResourceValueLoader.Args(
            distDataDir = dist.resolve(ResourceLoaderConstants.DATA_DIR),
            sampleAppRoot = sampleApp,
            runtimeClasspathTxt = sampleApp.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
        )
        bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        resources = LayoutlibRenderResources(bundle, SessionConstants.DEFAULT_FIXTURE_THEME)
    }

    @Test
    fun `H1 — Lvl 5 Base_V14_Theme_Material3_Light is reachable by name from RES_AUTO bucket`()
    {
        val style = bundle.getStyleByName("Base.V14.Theme.Material3.Light")
        assertNotNull(style)
    }

    @Test
    fun `H2 — getResource STYLE ref returns the style instance — KILL POINT candidate`()
    {
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Widget.Material3.Button")
        val resolved = bundle.getResource(ref)
        assertNotNull(resolved)
    }

    @Test
    fun `H3a — findItemInTheme materialButtonStyle returns the M3 style item from Lvl 5`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "materialButtonStyle")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
        val raw = item!!.value
        assertNotNull(raw)
        assertTrue(raw!!.contains("Widget.Material3.Button"))
    }

    @Test
    fun `H3b — findItemInTheme textAppearanceButton returns Lvl 9 item`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "textAppearanceButton")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
        val raw = item!!.value
        assertNotNull(raw)
        assertTrue(raw!!.contains("TextAppearance.MaterialComponents.Button"))
    }

    @Test
    fun `H2-bridgeTypedArray gate — resolveResValue of StyleItem returns a StyleResourceValue post-fix`()
    {
        val styleRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Widget.Material3.Button")
        val item = StyleItemResourceValueImpl(
            ResourceNamespace.RES_AUTO,
            "android:textAppearance",
            "@style/Widget.Material3.Button",
            null,
        )
        val resolved = resources.resolveResValue(item)
        assertNotNull(resolved)
        assertTrue(
            resolved is StyleResourceValue,
            "BridgeTypedArray.getResourceId offset 28 instanceof gate requires StyleResourceValue, got ${resolved!!::class.simpleName}",
        )
    }

    private fun locate(): Pair<Path, Path>?
    {
        val dist = DistDiscovery.locate(null)
        if (dist == null)
        {
            assumeTrue(false, "dist 없음")
            return null
        }
        val sampleApp = FixtureDiscovery.locateModuleRoot(null)
        if (sampleApp == null)
        {
            assumeTrue(false, "module root 없음")
            return null
        }
        return dist.toAbsolutePath().normalize() to sampleApp.toAbsolutePath().normalize()
    }
}
```

`locate()` 의 graceful skip 패턴 (`assumeTrue(false, ...)`) 은 `MaterialFidelityIntegrationTest:123-144` 와 동일 — fixture build artefact 부재 시 false-fail 회피, IT runner 가 skip 상태로 가시. round 5 Codex Q4 escalation: `runtimeClasspathTxt.exists()` 의 hard-require 는 본 패턴의 `assumeTrue` 가 dist + module root 단계에서 처리 (manifest 부재 시 loadOrGet 안 silent empty AAR 가 false PASS 위험은 W4+ hardening 으로 carry — escalation #2).

### §4.2 무엇을 측정하는가

| 단위 테스트 | PASS 시 의미 | FAIL 시 의미 (가설) |
|---|---|---|
| `H1 — Lvl 5 reachable by name` | RES_AUTO bucket 에 Base.V14.Theme.Material3.Light style 등록됨 | bundle build 단계에서 누락 — AAR walker / parser 회귀. 매우 적음. |
| `H2 — getResource STYLE ref` | byType[STYLE] 도 채워져 있음 (의외) | **H2 확정** — bundle.getResource(STYLE) 의 byType-only 경로 갭. T18 의 dominant fix. |
| `H3a — findItemInTheme materialButtonStyle` | chain walker 가 Lvl 5 까지 탐색 → item 발견 | walkParent 또는 dot-trim inference 가 Lvl 2/3 에서 단절 (H1 의 변종) |
| `H3b — findItemInTheme textAppearanceButton` | Lvl 9 까지 walk + item 발견 | Lvl 5-9 사이 어디선가 단절 — Lvl 5 의 explicit parent (`Theme.MaterialComponents.Light`) lookup 실패 또는 후속 chain 미연결 |
| `H2-bridgeTypedArray gate — resolveResValue StyleItem returns StyleResourceValue` (round 5 Q3 추가) | post-fix instanceof gate 통과 — BridgeTypedArray.getResourceId offset 28 + BridgeContext offset 312-325 양쪽의 instanceof StyleResourceValue cast 가 chain walker 결과로 PASS | 본 단계가 H2 fix 의 acceptance contract — H2 만 fix 후 본 probe 가 여전히 fail 시 H3 (R$style symbol seed) 또는 H4 (macro) escalate |

5개 모두 PASS = chain + STYLE-ref hop 정상 → H4 (macro) 또는 미식별 layer (R$styleable seeder gap, RJarSymbolSeeder.kt:64-66 의 skip 정책 영향) 이 후속 surface.
H2 만 FAIL = T18 가 §5.1 적용 (dominant). 4번째 5번째 probe 는 §5.1 적용 후 PASS 로 전환.
H2 + H3 FAIL = §5.1 + §5.2 양쪽 적용 (uncorrelated layer — chain 단절 + resolve nullify 둘 다 필요).

---

## §5 T18 — Hypothesis-targeted fix surfaces

T17 결과에 따라 T18 의 변경 범위 결정. 각 hypothesis 별 spec — 해당 가설이 confirmed 시 적용.

### §5.1 H2 fix (dominant 예상) — `bundle.getResource(STYLE ref)` 에 styles map fallback

#### `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt`

```diff
     fun getResource(ref: ResourceReference): ResourceValue?
     {
         val bucket = byNs[ref.namespace] ?: return null
         if (ref.resourceType == ResourceType.ATTR)
         {
             return bucket.attrs[ref.name]
         }
+        if (ref.resourceType == ResourceType.STYLE)
+        {
+            return bucket.styles[ref.name]
+        }
         return bucket.byType[ref.resourceType]?.get(ref.name)
     }
```

근거: NsBucket 의 byType ↔ styles ↔ attrs 가 분리 저장 — γ T14 가 ATTR 에 special-case 추가, 본 fix 가 STYLE 에도 sibling 추가. byType[STYLE] 은 비어 있으므로 fallback 이 아닌 single-path 변경. layoutlib RenderResources base 의 `getResolvedResource(STYLE ref) → resolveResValue(getStyle?)` 가 base 에서 안 동작 — 우리 σ FULL override 의 chain walker 가 직접 bundle.getResource 위임. 따라서 styles map 도달 가능성이 enabling fix.

**Round 5 Q1/Q5 reconcile — BridgeContext path split + bytecode-offset annotation**:

본 3-line 변경이 enable 하는 두 instance check gate:

- **M3 path (defStyleAttr resolution)**: BridgeContext (BridgeContext-javap.txt:1879/1926/1959) 의 styled-attrs lookup 이 `findItemInTheme(materialButtonStyle attr ref)` → `resolveResValue(StyleItem("@style/Widget.Material3.Button"))` 호출. 우리 chain walker (LayoutlibRenderResources.kt:171-178) 가 STYLE ref hop 단계에서 `getUnresolvedResource → bundle.getResource`. **본 fix 적용 시** `bucket.styles["Widget.Material3.Button"]` 반환 → BridgeContext offset 312-325 의 `instanceof StyleResourceValue` cast 통과 → defStyle = Widget.Material3.Button → `enforceTextAppearance=false` (material/values.xml:5239) → `checkTextAppearance` 게이트 우회.

- **M2 path (defStyleRes 의 `?attr/textAppearanceButton` resolution)**: defStyleRes 자체는 직접 `getStyle(ref)` 사용 (BridgeContext offset 396+) — 우리 `bundle.getStyleExact` 으로 이미 작동. 단 `Widget.MaterialComponents.Button:6352` 의 `<item name="android:textAppearance">?attr/textAppearanceButton</item>` 가 chain 안 Lvl 9:3068 의 `<item name="textAppearanceButton">@style/TextAppearance.MaterialComponents.Button</item>` 도달 → 그 STYLE ref hop 의 resolution. BridgeTypedArray.getResourceId (BridgeTypedArray-javap.txt:1052/1080) 가 chain walker 결과의 `instanceof StyleResourceValue` cast 후 `getDynamicIdByStyle` 호출. **본 fix 적용 시** chain walker 가 Lvl 9 의 item 발견 → resolveResValue 의 STYLE hop → `bucket.styles["TextAppearance.MaterialComponents.Button"]` 반환 → instanceof gate 통과 → dynamic ID 반환 → `getResourceId(slot=0, -1) != -1` → throw 우회.

**중요 — round 5 Q5 conditional framing**: H2 fix 의 coverage 는 *item discovery 후의 STYLE-ref hop resolution 단계* 만. item discovery 자체 (findItemInTheme 단계) 가 fail 시 본 fix 무관 — H1/H3 가 §5.2 의 walkParent fallback 으로 cover. 5.6 의 결정 매트릭스 (H1+H3 partial-FAIL row) 가 ordering 처리.

#### Test (`LayoutlibResourceBundleStyleLookupTest.kt` 신규, 4 cases)

- `getResource - STYLE ref returns style instance from RES_AUTO`
- `getResource - STYLE ref returns null when name absent`
- `getResource - STYLE ref returns null when namespace absent`
- `getResource - non-STYLE/ATTR types still use byType bucket` (회귀 가드)

### §5.2 H1 fix — chain walker / dot-trim inference 보강

만약 T17 의 H1 또는 H3a FAIL 시:

#### `LayoutlibRenderResources.walkParent` 의 명시 후 fallback 추가

```diff
     private fun walkParent(style: StyleResourceValue): StyleResourceValue?
     {
         val parentRef = style.parentStyle
         if (parentRef != null)
         {
             val exact = bundle.getStyleExact(parentRef)
             if (exact != null) { return exact }
             return bundle.getStyleByName(parentRef.name)
         }
         val rawName = style.parentStyleName ?: return null
-        return resolveStyleNameWithNamespace(rawName)
+        val byNs = resolveStyleNameWithNamespace(rawName)
+        if (byNs != null) return byNs
+        // dot-trim inference 가 build-time 에 적용되지 않은 edge case (e.g. 양쪽 모두 null 이지만
+        // 자기 이름에 dot 이 있는 style — 가능성은 적지만 방어적 fallback).
+        val styleName = style.resourceUrl?.name ?: return null
+        val lastDot = styleName.lastIndexOf('.')
+        if (lastDot <= 0) return null
+        return bundle.getStyleByName(styleName.substring(0, lastDot))
     }
```

(T17 가 H1 PASS 하면 본 변경 미적용 — speculative 추가 금지.)

### §5.3 H3 fix — R$style symbol seed gap

만약 T17 후 layoutlib 단계 ID resolution 실패 패턴이 식별 시 (T17 단독 unit test 로는 캡처 못 하므로 T19 IT 측정 후 escalate). RJarSymbolSeeder + `RNameCanonicalization` 의 dot-pattern 회귀 가드 추가. 본 plan 의 정의된 변경 부재 — measure-first.

#### §5.3.1 — post-T17-all-green BridgeContext callback instrumentation (round 5 Codex Q3 delta)

T17 의 5 probe 모두 PASS + T19 IT 여전히 fail 시 적용. resource-layer (bundle / chain walker) 가 정상이지만 layoutlib 의 BridgeContext callback layer (`MinimalLayoutlibCallback.getOrGenerateResourceId` / `resolveResourceId`) 가 R$styleable 또는 generated style ID resolution 단계에서 단절 가능 — RJarSymbolSeeder.kt:64-66 의 R$styleable skip 정책 (W3D4 round 2 A2 결정) 의 후속 layer.

instrumentation 변경: `MinimalLayoutlibCallback` 에 system-property gated logger 추가 (default off, opt-in via `-Daxp.debug.callback=true`). 4개 ref (`materialButtonStyle`, `Widget.Material3.Button`, `textAppearanceButton`, `TextAppearance.MaterialComponents.Button`) 의 callback layer 도달 측정 — 어느 ID 가 unresolved 또는 generated 인지 식별. T19 fail 후의 진단 도구 — 본 plan 의 변경 surface 가 아닌 escalation tooling.

```kotlin
// MinimalLayoutlibCallback.kt — instrumentation hook (T19 fail 후 추가, 단독 commit)
override fun resolveResourceId(id: Int): ResourceReference?
{
    val ref = inner.resolveResourceId(id)
    if (DEBUG_CALLBACK && ref != null && ref.name in CALLBACK_DEBUG_NAMES)
    {
        System.err.println("[MinimalLayoutlibCallback] resolveResourceId(0x${"%08x".format(id)}) → $ref")
    }
    return ref
}

private companion object
{
    val DEBUG_CALLBACK: Boolean = System.getProperty("axp.debug.callback") == "true"
    val CALLBACK_DEBUG_NAMES: Set<String> = setOf(
        "materialButtonStyle",
        "Widget.Material3.Button",
        "textAppearanceButton",
        "TextAppearance.MaterialComponents.Button",
    )
}
```

본 instrumentation 은 T19 fail 진단용 — T19 PASS 시 적용 안 함. R$styleable layer escalation (W3D4-ε) 의 trigger condition.

### §5.4 H4 fix — macro reference 처리

`LayoutlibRenderResources.parseReference` 에서 `ResourceUrl.parse` 결과의 type 이 macro (또는 unrecognized) 이면 chain 단절 대신 raw 반환. T19 측정 후 escalate. 본 plan 의 정의된 변경 부재 — speculative.

### §5.5 일반 hardening — bundle build 시점 one-shot 진단

(round 5 Q6 delta — DEBUG_STYLE_MISS per-call hot-path 로그 폐기. 대신 LayoutlibResourceValueLoader bootstrap 시 1회 summary 로 진단 가시성 확보.)

#### `LayoutlibResourceValueLoader.kt` bootstrap log 의 byType[STYLE] 카운트 추가

기존 cold-start 진단 로그 (`[LayoutlibResourceValueLoader] cold-start framework=…ms app=…ms aar=…ms build=…ms total=…ms`) 옆에 styles/attrs/byType 카운트 한 줄 추가:

```diff
     System.err.println(
         "[LayoutlibResourceValueLoader] cold-start framework=${tFw}ms app=${tApp}ms aar=${tAar}ms build=${tBuild}ms total=${tFw+tApp+tAar+tBuild}ms"
     )
+    if (DEBUG_BUNDLE_SHAPE)
+    {
+        val resAuto = bundle.namespacesInOrder().lastOrNull() ?: ResourceNamespace.RES_AUTO
+        val styles = bundle.styleCountForNamespace(resAuto)
+        val attrs = bundle.attrCountForNamespace(resAuto)
+        System.err.println(
+            "[LayoutlibResourceValueLoader] bundle shape RES_AUTO styles=$styles attrs=$attrs (byType[STYLE] is intentionally empty — getResource STYLE 은 styles map 위임, plan v3.2 §5.1)"
+        )
+    }
```

`DEBUG_BUNDLE_SHAPE` = `System.getProperty("axp.debug.bundleShape") == "true"` (default off — production fast-path 보존). 본 라인은 진단 시점에만 enable, per-call hot-path 영향 없음.

### §5.6 결정 기준

T17 5-test 의 결과 매핑 (round 5 Q6 delta — H1+H3 partial-FAIL row 추가, gate-condition 명시):

| 결과 패턴 | 적용 fix | 우선순위 |
|---|---|---|
| H1 PASS, H2 FAIL, H3a PASS, H3b PASS | §5.1 (H2 KILL POINT) 단독 — dominant 예상 | 1 commit |
| H1 PASS, H2 FAIL, H3a/H3b PASS, instanceof gate FAIL (5번째 probe FAIL) | §5.1 적용 — 5번째 probe 가 §5.1 의 acceptance contract | 1 commit |
| H1 PASS, H2 PASS, H3a FAIL OR H3b FAIL | §5.2 (walkParent fallback) 단독 — chain 의 deep 단절 | 1 commit |
| H2 FAIL + H3a/H3b 중 1+ FAIL | §5.1 적용 후 재측정. §5.1 적용 후도 H3 fail 시 §5.2 추가 (별도 commit). | 2 commits — H2 먼저 |
| H1 FAIL | bundle build / parser 회귀 — fix 정의 부재. measure & escalate (W3D4-ε). | hold |
| 5개 모두 PASS | T19 IT 측정 — 다른 layer (H3 ID resolution / H4 macro / R$styleable) escalate. §5.3.1 instrumentation 적용. | T19 escalation |

**Ordering rationale**: §5.1 (H2 fix) 가 dominant 예상이므로 먼저 적용 + T17 재측정 → §5.2 도 필요 시 별도 commit (CLAUDE.md task-unit completion). H2 fix 가 chain walker 결과의 instanceof gate 만 enable — item discovery 자체가 단절된 chain 에는 영향 없음 (Q5 conditional framing). H1+H3 의 chain walker 갭과 H2 의 STYLE-ref hop 갭은 *uncorrelated layer* — 별도 commit 이 디버깅 가능성 보장.

**T18 commit 단위**: §5.1 단독 (H2 confirmed) 시 1 commit. §5.2 추가 시 별도 commit (서로 다른 hypothesis). §5.5 의 bootstrap log 는 §5.1 commit 에 포함 가능 (single-line 진단 추가, per-task 단독 commit 의무 없음).

---

## §6 T19 — Acceptance gate

### §6.1 변경

#### `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`

```diff
-    @org.junit.jupiter.api.Disabled(
-        "W3D4-δ-A carry: γ-A 닫힘 후 Material ThemeEnforcement 의 multi-sentinel check — " +
-            "checkTextAppearance 가 IllegalArgumentException throw (\"This component requires that you specify " +
-            "a valid TextAppearance attribute. Update your app theme to inherit from Theme.MaterialComponents " +
-            "(or a descendant).\"). colorPrimary 외 다른 sentinel attr (TextAppearance 계열) 도 검사. " +
-            "W3D4-δ-A spec 작성 후 fix 적용 → @Disabled 제거."
-    )
     @Test
     fun `tier3 basic primary — activity_basic 가 직접 SUCCESS`()
```

cache invalidation `@BeforeEach` 는 T13 부터 보존 — 변경 없음.

### §6.2 PASS 조건

- `Result.Status.SUCCESS == renderer.lastSessionResult?.status`
- `bytes.size > MIN_RENDERED_PNG_BYTES`
- `isPngMagic(bytes) == true`
- IT 수: 14 PASS + 2 SKIP → **15 PASS + 1 SKIP** (`tier3-glyph` W4 carry).
- system-err 에 `[layoutlib.warning]` 또는 `IllegalArgumentException` 없음 (M3 path 안전 dispatch 또는 M2 path 의 ?attr/textAppearanceButton 정상 chain).
- 모듈 합산 unit 재측정.

### §6.3 실패 시 escalation 정책 (LM-W3D4-β-H 인계)

- 새 fail surface 식별 시 stack trace + system-err 분류 → `t19-acceptance-gate-followup.md` 작성.
- Hypothesis 후보 (round 5 reconcile — `enforceMaterialTheme` gate chain 명시 + direct sentinel surface 추가):
  - **W3D4-δ-B (gate chain — 가장 가까운 escalation)**: `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant` 의 3-step gate. `Widget.MaterialComponents.Button:6349` 가 `enforceMaterialTheme=true` 명시 → M2 path 진입 시 `checkCompatibleTheme` (te_disasm.txt:90-156) 가 `Theme.resolveAttribute(R.attr.isMaterialTheme)` 호출 후 false 면 `checkMaterialTheme(ctx)` (te_disasm.txt:315-327) 호출 → `int[]{R.attr.colorPrimaryVariant} hasValue` 검사 → false 면 throw `"… requires your app theme to be Theme.MaterialComponents (or a descendant)."`. 본 plan §5.1 의 H2 fix 가 M3 path 로 dispatch 시키면 본 gate 미진입 (M3 의 `Widget.Material3.Button` 은 enforceMaterialTheme 미상속). 그러나 §5.1 fix 후도 M2 fallback 시 본 gate 가 다음 sentinel surface — Lvl 5:2214 `colorPrimaryVariant → ?attr/colorPrimary` chain (또는 Lvl 9:3023 의 concrete color) 가 chain 안에서 valid 하게 resolve 되는지 measure.
  - **direct-sentinel surface (현 fixture 외)**: `BadgeDrawable` 가 `checkMaterialTheme` 직접 호출 (`obtainStyledAttributes` 미경유), `BaseTransientBottomBar` (Snackbar) 가 `checkAppCompatTheme` 직접 호출 — `activity_basic.xml` 에 부재이므로 본 phase 무관, 단 future fixture 진입 시 동일 sentinel 의 다른 entry point 로 escalate.
  - **W3D4-ε (R$styleable layer — 가장 먼)**: R$styleable 의 layoutlib Bridge.parseStyleable 위임 — 현재 `RJarSymbolSeeder.kt:64-66` 가 R$styleable skip. styled-attrs lookup path 의 후속 layer. T17 모두 PASS + T19 fail 시 §5.3.1 의 BridgeContext callback instrumentation 으로 narrow 후 trigger.

---

## §7 Q1–Q6 — round 5 pair-review query (Codex+Claude, plan-revision phase)

### Q1 — H2 (KILL POINT 후보) 의 정확성
"§2.2 의 `bundle.getResource(STYLE ref)` 가 byType-only 라는 분석은 correct 인가? layoutlib 14.0.11 에서 `BridgeContext.obtainStyledAttributes` 가 defStyleAttr → defStyle 변환 시 어느 RenderResources API path 를 통과하는가? `getStyle(ref)` 직접 호출 (우리 9 override 가 styles map 위임 — 정상) vs `getResolvedResource(STYLE ref)` 또는 `resolveResValue` chain (우리 chain walker 가 bundle.getResource(STYLE) 위임 — 본 plan 의 H2 fix 가 enabling). javap 또는 BridgeContext bytecode 검증 권장."

### Q2 — Lvl 5 Base.V14.Theme.Material3.Light 의 chain reachability
"§1.2 의 chain 이 16 levels 지만 Lvl 2 → Lvl 3 가 dot-trim inference 에 의존. `StyleParentInference.kt:19-20` 의 `lastDot` 처리가 Material AAR 의 *모든* alias-only style 에 대해 정합한가? Specifically: `Theme.Material3.Light.NoActionBar` (Lvl 2, no `parent=`) → `Theme.Material3.Light` 가 `bundle.getStyleByName` 으로 발견되는가? 만약 발견 안 되면 chain 이 Lvl 2 에서 단절."

### Q3 — T17 의 4-question battery 가 H1-H4 narrowing 에 충분한가
"§4.2 의 4 question 이 H1/H2/H3a/H3b 를 1:1 mapping 하지만, layoutlib 단계의 layoutlib-internal callbacks (Bridge.resolveValueRef → callback.byRef → R$style ID lookup) 는 unit test 가 직접 측정 못 함. T17 모두 PASS 인데 IT 여전히 fail 시 H3 (R$style symbol seed gap) 또는 H4 (macro) 가설로 escalate — 그 trigger 후 추가 instrumentation 의 strategy?"

### Q4 — T17 의 production-input 의존성
"§4.1 의 W3D4DeltaThemeChainDiagnosticTest 가 `PathLocator.locateW3D4Triplet()` 위임으로 real fixture 를 사용. 본 fixture 에 의존 = unit test 가 fixture build 결과 (`fixture/sample-app/app/build/...`) 에 link. CI 에서 본 fixture 의 build artifact 가 항상 보장되는가? IT 측정 시 `MaterialFidelityIntegrationTest` 와 동일 entry criterion 인가?"

### Q5 — `?attr/textAppearanceButton` 이 chain 의 어느 단계에서 끊기는가
"§1.3 의 hypothesis: 우리 chain walker 가 Lvl 9 의 `<item name="textAppearanceButton">` 발견 못 함. 그러나 §2.2 의 H2 fix 적용 시 chain 의 `@style/TextAppearance.MaterialComponents.Button` resolution 이 enable 됨. 두 path (M3 우회 via materialButtonStyle vs M2 정합 via textAppearanceButton) 가운데 H2 fix 가 어느 path 를 enable 하는가? 양쪽 모두인가? subagent B 의 §5 의 6 옵션 중 H2 fix 가 cover 하는 것은 옵션 (1) + (2) 양쪽인가?"

### Q6 — open critique 슬롯 (round 4 정책 일관)
"본 plan v3.2 의 file:line 검증 외, 양쪽 reviewer 가 독립적으로 발견한 결함이 있다면 명시. 예:
- T17 의 `@Tag("integration")` 사용 — diagnostic 도 IT runner 에서만 실행되어 unit suite gate 를 missed (LM-W3D4-β-F 의 `-PincludeTags=integration` 의존성 noise).
- §5.5 의 `DEBUG_STYLE_MISS` system property gate 가 production noise 발생 위험 (default off 이지만 명시적 toggle 부재).
- §5.6 의 결정 매트릭스가 H2+H3 동시 fail 의 ordering 미명시 — H2 먼저 fix 후 H3 측정 vs 동시 적용.
- §6.3 의 W3D4-δ-B 가설이 colorPrimaryVariant 만 — `enforceMaterialTheme` 게이트 (Widget.MaterialComponents.Button:6349 가 true) 도 sibling surface."

---

## §8 LM (landmines) 회피 정책

| LM 코드 | 사유 | 본 plan 의 방어 |
|---|---|---|
| LM-W3D3-A / LM-α-A | empirical-verifiable claim 직접 측정 | §1.1, §1.2, §1.3, §1.5 모두 file:line 인용 (subagent A/B/C 의 javap + values.xml grep) |
| LM-α-B | Codex stalled-final → single-source verdict + 명시 flagging | round 5 pair 적용 |
| LM-G | codex exec sandbox bypass | round 5 직접 CLI: `codex exec --skip-git-repo-check --sandbox danger-full-access` (MEMORY.md feedback 준수, codex-rescue subagent 미사용) |
| LM-W3D3-B | JUnit Jupiter Assertions only | §4.1 / §5.1 신규 테스트 모두 `org.junit.jupiter.api.Assertions.*` |
| LM-α-D | Kotlin backtick 함수명 안 마침표 금지 | §4.1 의 모든 backtick 테스트명 검증 |
| LM-W3D4-D | placeholder 관행 | §4.1 / §5.1 / §5.2 / §5.5 / §6.1 모두 explicit diff |
| LM-W3D4-E | mixed-content xliff:g | T17/T18 변경은 attr lookup / style chain 만 — values 의 mixed content 경로 미해당 |
| LM-W3D4-F | cross-NS attr ref `:` | T17 측정 시 sentinel attr 는 모두 ns-internal — 영향 없음 |
| LM-W3D4-β-D | KDoc `/*` 패턴 회피 | 본 plan / 신규 코드 KDoc 모두 backtick 인용 또는 일반 prose |
| LM-W3D4-β-E | assertNotNull chain 금지 | §4.1 의 `val v = ...; assertNotNull(v); v!!.method()` 패턴 일관 |
| LM-W3D4-β-F | IT 실행은 `-PincludeTags=integration` | T17/T19 의 IT 측정 시 명시 |
| LM-W3D4-β-G | subagent unzip `--directory /tmp/...` | round 5 의 모든 subagent prompt 에 명시; 본 plan 작성 시 사용한 subagent A/B/C 도 `/tmp/w3d4d-*` 사용 (cwd 회피 검증) |
| LM-W3D4-β-H | acceptance fail surface 분류 | §6.3 의 escalation 정책 직접 인용 (M3 우회 vs colorPrimaryVariant vs R$styleable layer) |
| LM-W3D4-γ-A | reviewer 의 bundle lookup path trace | round 5 reviewer prompt 에 "STYLE/ATTR ref 의 byType ↔ styles ↔ attrs 분리 검증" 명시 |
| LM-W3D4-γ-B | 32-bit unsigned hex 의 Long.decode 경로 | 본 plan 변경 surface 무관 (parser 변경 부재) |
| LM-W3D4-γ-C | ThemeEnforcement multi-sentinel census | **본 plan §1.1 가 정확히 이 census** — 7 attr 한 번에 enumerate, future phase 에서 stepwise 발견 회피 |
| **LM (CLAUDE.md 신규)** Three Hard Rules for Comments | English ONLY, Function/Structure ONLY, Zero Filler | §4.1 / §5.1-§5.5 의 KDoc/inline 모두 신 룰 준수 — 영문 only, 책임/contract 만, ticket reference 최소화 |

---

## §9 Test impact summary

| 단계 | layoutlib-worker unit | IT (PASS + SKIP) | 비고 |
|---|---|---|---|
| baseline (T16 partial 후) | 194 | 14 + 2 SKIP | tier3-basic-primary `@Disabled` (W3D4-δ-A) |
| T17 적용 후 | 194 + 4 (W3D4DeltaThemeChainDiagnosticTest) = 198 (tag=integration → unit gate 영향 없음) | **14 + 2 SKIP unit suite + 4 추가 IT** = 18 + 2 SKIP if `-PincludeTags=integration` | 4 개 테스트 의 PASS/FAIL 패턴 으로 H1-H4 narrowing |
| T18 적용 후 (H2 dominant 가정) | 198 + 4 (LayoutlibResourceBundleStyleLookupTest) = 202 (unit-tag); 의존하는 case 별로 fluctuate | T17 의 H2 test 가 PASS 로 전환 | dominant fix path 만 |
| T19 적용 후 | 202 (변동 없음) | **15 PASS + 1 SKIP** | tier3-basic-primary 닫힘 |

**모듈 합산 unit baseline 237 → T19 후 245 예상** (layoutlib-worker +8, 다른 모듈 변동 없음). 단, T17 의 IT-tag 결정에 따라 unit count 가 ±4 변동.

---

## §10 Commit/push 단위

CLAUDE.md task-unit completion 정책:
- T17 commit: `feat(w3d4-delta): T17 W3D4-δ-A theme chain diagnostic test (4-question battery)`
- T18 commit: `feat(w3d4-delta): T18 LayoutlibResourceBundle.getResource STYLE special-case (H2 KILL POINT fix)` (또는 다른 가설 결과에 따라 변동)
- T19 commit: `feat(w3d4-delta): T19 tier3-basic-primary @Disabled 제거 + W3D4-δ-A acceptance gate 닫힘`
- 각 task PASS 후 즉시 push.
- round 5 pair verdict 는 별도 work_log entry: `docs/work_log/2026-04-30_w3d4-beta-plumbing/round5-pair-review.md` (β session-folder 안에 — 같은 series 의 후속 round, β/γ/δ 가 동일 cluster 의 progressive escalation).
- session 종료 시 `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` append + (필요 시) `handoff.md` 갱신.

---

## §11 Out-of-scope (본 plan 이 다루지 않는 것)

- **W3D4-δ-B** (`colorPrimaryVariant` chain) — §6.3 의 escalation 후보. T19 fail 시 escalate.
- **W3D4-ε** (R$styleable layer) — `RJarSymbolSeeder.kt:64-66` skip 의 후속 layer. styled-attrs ID resolution 의 layoutlib 측 path. T19 fail + δ-B 적용 후도 fail 시 escalate.
- **macro reference (`@macro/...`) chain support** — §2.4 의 H4. 우선순위 낮음.
- **Material widget 외 sentinel** (Snackbar/BadgeDrawable 직접 `checkAppCompatTheme/checkMaterialTheme` 호출) — 현 fixture 의 activity_basic.xml 에 없음 — 본 phase scope 외.
- **W3D4 tier3-glyph** (Font wiring) — W4 carry.
- **DRAWABLE selector XML feed** — plan v3 §5.4 의 T12.5 escalation 대상 (본 phase 의 surface 가 닫혀도 별도).
- **POST-W2D6-POM-RESOLVE** — W4+ scope.

---

## §12 Round 5 reconcile (완료 — 2026-05-04)

본 plan v3.2 는 round 5 pair-review (Codex+Claude, plan-revision phase) 에서 verify 후 inline 적용 완료. 자세한 review 는 `docs/work_log/2026-04-30_w3d4-beta-plumbing/round5-pair-review.md`.

### §12.1 Round 5 verdict + convergence map

| 채널 | verdict | confidence | 핵심 finding |
|---|---|---|---|
| Codex (latest GPT, xhigh) | GO_WITH_FIXES | 0.91 | Q1 H2 KILL POINT 실증 (BridgeContext defStyleAttr 가 findItemInTheme + resolveResValue 사용, NOT direct getStyle). Q4 fabricated API 양쪽 catch (Args.sampleAppRoot, PathLocator 부재). |
| Claude (Plan agent) | GO_WITH_FIXES | 0.88 | Q1-Q3 NUANCED — Codex 와 동일 결론 + §1.3 fail mode disjunction 정확화 + Q3 의 5번째 probe (resolveResValue StyleItem instanceof) delta. |

**Convergence map**:

| Q | Codex | Claude | reconcile |
|---|---|---|---|
| Q1 (H2 KILL POINT) | CORRECT | NUANCED | Codex empirical 우선 (BridgeContext bytecode trace) + Claude §1.3 disambiguation delta inline (§1.3 갱신) |
| Q2 (Lvl 5 reachability) | NUANCED (over-claim 경고) | CORRECT (chain depth IT green 검증) | Codex narrowing inline + Claude IT-validation 결합 (§1.2/§2.1 갱신) |
| Q3 (T17 4-question 충분성) | NUANCED (callback layer 미커버) | NUANCED (5th probe 추가) | 양쪽 권고 결합 — Codex 의 §5.3.1 신규 + Claude 의 §4.2 5번째 probe inline |
| **Q4 (production-input 의존성)** | **DISAGREE** (sampleAppModuleRoot 오기, fixture manifest softness) | **DISAGREE** (PathLocator + sampleAppModuleRoot 둘 다 fabricated, @Tag self-contradiction) | **양쪽 동일 DISAGREE — KILL POINT 직접 verify (memory feedback_pair_review_codex_killpoint.md 패턴), judge round 불요. §4.1/§4.3 전면 재작성** |
| Q5 (H2 양 path coverage) | NUANCED (item discovery 후 STYLE hop conditional) | CORRECT (양 path 동시 enable) | Codex 의 conditional framing 이 더 정확 (§5.1 갱신) |
| Q6 (open critique) | NUANCED (5 defects) | NUANCED (7 defects) | union 적용 (12 deltas inline) |

**가장 강한 convergence**: Q4 (양쪽 DISAGREE 의 동일 file:line evidence — `Args.sampleAppRoot` + `PathLocator` 부재 + `@Tag` 모순). KILL POINT direct-verify 패턴 정확 실현 — 직접 코드 read 로 confirm 후 §4.1/§4.3 전면 재작성. judge round 불요.

**가장 강한 divergence**: 부재 — Q1/Q2/Q3/Q5/Q6 모두 결론 동일, framing 만 차이.

### §12.2 Adopted plan deltas (12 — 모두 inline 적용 완료)

1. **§1.3 fail mode 정확화** (Q1 reconcile) — "발견 못 하거나 nullify" disjunction → BridgeContext offset 312-325 instanceof gate fail 의 정확한 trace.
2. **§1.2 / §2.1 dot-parent claim narrow** (Q2 Codex) — "all alias-only" 일반화 → Lvl 2 의 single edge 검증 + chain depth IT 검증 인용.
3. **§4.1 fabricated API 제거** (Q4 양쪽 KILL POINT) — `PathLocator.locateW3D4Triplet()` 폐기, `MaterialFidelityIntegrationTest.locate()` 패턴 mirror, `Args.sampleAppRoot` 정정, `dist.resolve(ResourceLoaderConstants.DATA_DIR)` 적용.
4. **§4.1 KDoc + @Tag 정합** (Q4/Q6) — "NOT integration-tagged" 제거, `@Tag("integration")` 유지 + KDoc 갱신 (real fixture 필요 명시).
5. **§4.2 5th probe** (Q3 Claude) — `resolveResValue(StyleItem) is StyleResourceValue` BridgeTypedArray instanceof gate 사전 검증.
6. **§4.3 PathLocator 추출 폐기** (Q4 양쪽) — 본 절 전체 삭제. T17 가 자체 private `locate()` 보유.
7. **§5.1 BridgeContext split + bytecode-offset annotation** (Q1 Codex + Q5 Claude) — H2 fix 의 KDoc 에 BridgeContext offset 1879-1959/312-325 + BridgeTypedArray offset 28-46 명시.
8. **§5.1/§5.6 conditional framing** (Q5 Codex) — H2 의 coverage 가 item discovery 후의 STYLE hop 만 임을 명시.
9. **§5.3.1 신규** (Q3 Codex) — post-T17-all-green BridgeContext callback instrumentation. 4개 ref 의 callback layer 도달 측정.
10. **§5.5 DEBUG_STYLE_MISS 폐기** (Q6 양쪽) → bootstrap one-shot summary log (`axp.debug.bundleShape`).
11. **§5.6 H1+H3 partial-FAIL row** (Q6 Claude) + ordering rationale (H2 먼저, §5.2 별도 commit).
12. **§6.3 enforceMaterialTheme gate chain** (Q6 양쪽) + `BadgeDrawable`/`BaseTransientBottomBar` direct-sentinel surface 추가.

### §12.3 LM 준수 검증

- LM-G: `codex exec --skip-git-repo-check --sandbox danger-full-access` 직접 CLI ✓
- LM-α-A: Codex 모든 claim file:line 인용 (40+ citations 본 round 5 stdout 의 javap dump), Claude 도 file:line 사용 (35+ citations) ✓
- LM-α-B: dual-channel verdict, single-source 회피 ✓
- LM-W3D4-D: 모든 fix explicit diff (round 5 의 12 deltas 도 동일 — fabricated `TODO("...")` 1건 round 5 catch 후 §4.3 전면 폐기로 해결) ✓
- LM-W3D4-β-D~H: round 5 reviewer 가 명시 검증 (Q6 inline)
- LM-W3D4-γ-A~C: round 5 reviewer prompt 가 "bundle 의 byType ↔ styles ↔ attrs 분리 trace" 명시 — Claude Plan 의 Q1 분석에서 Codex 와 동일 detection 도달 ✓
- LM (CLAUDE.md 신규 Three Hard Rules for Comments): 신규 코드 sample 의 KDoc / inline 모두 영문 only, function/structure 만, ticket reference 최소화 (T17 KDoc 가 phase 명시는 보존 — historical context 가 함께 카탈로그된 round 4 와 동일 자유) ✓
- **LM-W3D4-δ-A (round 5 신규)**: spec 작성 시 fabricated API references — 미존재 helper / 미존재 field 호출. 양쪽 reviewer 가 Q4 DISAGREE 로 catch. 향후 plan 작성 시 코드 sample 모든 호출은 실 source 에서 verify 후 인용 의무 — round 5 의 §4.1 KDoc 안 명시. round 6+ pair prompt 갱신.

**최종 verdict**: REVISE → APPLIED (12/12 deltas inline). plan v3.2 → **GO** (post-revision). T17/T18/T19 구현 진입 승인.
