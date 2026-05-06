# W3D4-δ-B Gate Chain — Material `colorPrimaryVariant` 고정 design (plan v4 draft)

날짜: 2026-05-06
선행 head: `db17bfe` (main, W3D4-δ T17/T18/T19 partial + handoff-delta-b)
다음 phase: W3D4-δ-B implementation (T20 → T21 → T22)
plan-revision: round 6 pair-review (Codex + Claude planning, planning ONLY)

---

## §0 Entry context (cold-start safe)

### §0.1 작업 흐름의 위치

- W3D4-δ-A (TextAppearance sentinel) **closed** — T18 의 `LayoutlibResourceBundle.getResource` STYLE special-case 가 BridgeContext + BridgeTypedArray 양쪽 instanceof gate 동시 enable. T17 5-probe diagnostic 5/5 PASS 가 regression guard.
- W3D4-δ-B (gate chain `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant`) **open** — `MaterialButton.<init>:239` 가 `ThemeEnforcement.checkCompatibleTheme:144 → checkMaterialTheme:216 → checkTheme:247` chain 으로 throw `"The style on this component requires your app theme to be Theme.MaterialComponents (or a descendant)."`.
- 본 plan v4 가 W3D4-δ-B close 의 design 결정. T20 (diagnostic) → T21 (fix) → T22 (acceptance close) 의 task split.

### §0.2 본 plan v4 가 변경하지 않는 것 (불변식)

- `LayoutlibResourceBundle.getResource(STYLE ref)` 의 styles map 위임 (T18 fix) — 보존.
- T17 의 5-probe diagnostic (`W3D4DeltaThemeChainDiagnosticTest`) — regression guard 로 보존, 본 phase fix 후도 5/5 PASS 필수.
- NsBucket 의 byType ↔ styles ↔ attrs 3-way 분리 (LM-W3D4-δ-B carry).
- chain walker 의 STYLE ref hop 정합 (T17 H2 + bridgeTypedArray gate 의 PASS).
- Material AAR + AppCompat AAR 의 자체 정의 (read-only).

### §0.3 baseline (2026-05-06 측정, T19 partial 후)

```
$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 241 unit total / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain | tail -3
BUILD SUCCESSFUL — 19 IT PASS + 2 SKIP (tier3-basic-primary δ-B carry, tier3-glyph W4 carry).
```

---

## §1 Empirical findings (round 6 pair-review 의 baseline 입력)

### §1.1 `ThemeEnforcement.checkCompatibleTheme` bytecode 의 SKIP 조건 census

`/tmp/w3d4d-themeenforcement/te_disasm.txt:90-156` 의 `checkCompatibleTheme(Context, AttributeSet, int defStyleAttr, int defStyleRes)`:

```
 92:  0: aload_0
 94:  2: getstatic R$styleable.ThemeEnforcement              // attrs[0]
 97:  7: invokevirtual Context.obtainStyledAttributes(...)
100: 14: getstatic R$styleable.ThemeEnforcement_enforceMaterialTheme
102: 18: invokevirtual TypedArray.getBoolean(I,Z)            // value default false
106: 28: iload 5                                              // enforceMaterialTheme bool
107: 30: ifeq          84                                     // (1) FALSE → goto 84 SKIP
113: 43: invokevirtual Context.getTheme()
114: 46: getstatic R$attr.isMaterialTheme
117: 52: invokevirtual Resources$Theme.resolveAttribute(I,TypedValue,Z)
118: 55: istore 7                                             // resolved? bool
119: 57: iload 7
120: 59: ifeq          80                                     // (2) NOT resolved → fall to 80 → checkMaterialTheme
123: 67: bipush 18                                            // TypedValue.TYPE_INT_BOOLEAN
124: 69: if_icmpne     84                                     // (3) type ≠ BOOLEAN → goto 84 SKIP
126: 74: getfield TypedValue.data
127: 77: ifne          84                                     // (4) data ≠ 0 (TRUE) → goto 84 SKIP
128: 80: aload_0
129: 81: invokestatic checkMaterialTheme(Context)V            // gate trigger
130: 84: aload_0
131: 85: invokestatic checkAppCompatTheme(Context)V
132: 88: return
```

**정확한 SKIP 조건** (어느 한 조건 만으로 `checkMaterialTheme` 미진입):
- (1) **enforceMaterialTheme=false** — line 107 `ifeq 84`. styled-attrs 안 default false.
- (3) `isMaterialTheme` resolved + `value.type ≠ TYPE_INT_BOOLEAN(0x12=18)` — line 124 `if_icmpne 84`.
- (4) `isMaterialTheme` resolved + BOOLEAN + `value.data ≠ 0` (즉 **isMaterialTheme=true**) — line 127 `ifne 84`.

**TRIGGER 조건** (checkMaterialTheme 호출):
- enforceMaterialTheme=true AND (isMaterialTheme unresolved OR isMaterialTheme=false BOOLEAN).

### §1.2 `ThemeEnforcement.checkMaterialTheme` + `checkTheme` bytecode

`te_disasm.txt:315-340` (checkMaterialTheme):
```
315:  public static void checkMaterialTheme(Context);
316:    Code:
       0: aload_0
       1: getstatic    MATERIAL_CHECK_ATTRS                   // int[]{R.attr.colorPrimaryVariant}
       4: ldc          "Theme.MaterialComponents"
320:   6: invokestatic checkTheme:(Landroid/content/Context;[ILjava/lang/String;)V
       9: return
```

`te_disasm.txt:410-…` (checkTheme):
```
410: private static void checkTheme(Context, int[], String);
       0: aload_0
       1: aload_1
       2: invokestatic isTheme(Context, int[]):Z              // line 414
       5: ifne         exit                                   // PASS if true
       8-22: new IllegalArgumentException + StringBuilder concat
                "The style on this component requires your app theme to be"
                + label                                       // "Theme.MaterialComponents"
                + " (or a descendant)."
       25: athrow
       26: return
```

`isTheme(Context, int[])` (te_disasm.txt:368-…): `Theme.obtainStyledAttributes(int[])` → loop 으로 각 slot 의 `TypedArray.hasValue(slot)` 검사 → 한 개라도 false 면 isTheme=false.

따라서 **checkMaterialTheme 의 PASS 조건**: `Theme.obtainStyledAttributes(int[]{colorPrimaryVariant}).hasValue(0) == true`.

`hasValue` 가 true 인 조건: theme 의 attribute resolution 단계에서 `colorPrimaryVariant` 가 raw value 또는 resolved attr-ref chain 으로 도달 가능.

### §1.3 Theme.AxpFixture chain 의 colorPrimaryVariant + isMaterialTheme 분포

`fixture/sample-app/app/src/main/res/values/themes.xml:5-11`:
```xml
<style name="Theme.AxpFixture" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="colorPrimary">#6750A4</item>
    <item name="colorOnPrimary">#FFFFFF</item>
    <item name="colorPrimaryContainer">#EADDFF</item>
    <item name="colorOnSurface">#1C1B1F</item>
    <item name="colorOnSurfaceVariant">#49454F</item>
</style>
```
- **colorPrimaryVariant 미정의**, **isMaterialTheme 미정의**.

Material AAR (`/tmp/w3d4d-themechain/material/res/values/values.xml`) 의 attr/value 분포:

| 정의 line | style | content |
|---|---|---|
| 53 | (top-level attr) | `<attr format="color" name="colorPrimaryVariant"/>` |
| 105-106 | (top-level attrs) | `<attr format="boolean" name="isMaterial3Theme"/>` + `name="isMaterialTheme"` |
| 1604 | M3 chain | `<item name="isMaterial3Theme">true</item>` |
| 1676 | M3 chain | `<item name="colorPrimaryVariant">?attr/colorPrimary</item>` |
| 2141-2142 | **Lvl 5 Base.V14.Theme.Material3.Light** | `<item name="isMaterial3Theme">true</item>` (M3-only marker, ≠ isMaterialTheme) |
| 2214 | Lvl 5 동일 style | `<item name="colorPrimaryVariant">?attr/colorPrimary</item>` |
| 3020-3021 | **Lvl 9 Base.V14.Theme.MaterialComponents.Light.Bridge** | `<item name="isMaterialTheme">true</item>` (M2 Bridge fallback — chain walker reachability of this level for `isMaterialTheme` attr is empirically uncertain; T17 measured H1 Lvl 5 reachability for `materialButtonStyle` only, NOT `isMaterialTheme` traversal) |
| 3023 | Lvl 9 동일 style | `<item name="colorPrimaryVariant">@color/design_default_color_primary_variant</item>` (concrete) |
| 5238-5257 | `Widget.Material3.Button` parent="Widget.MaterialComponents.Button" | `enforceMaterialTheme` override **없음** — 상속 |
| 6348-6371 | `Widget.MaterialComponents.Button` parent="Widget.AppCompat.Button" | `<item name="enforceMaterialTheme">true</item>` (line 6349) |

`enforceMaterialTheme=false` 4건 (4968 / 4979 / 5041 / 5046) 모두 `Widget.Design.*` (BottomNav, BottomSheet, TextInput) — Widget.Material3.* override 아님.

### §1.4 Theme.AxpFixture parent chain 의 dot-trim resolution (plan v3.2 §1.2 carry)

```
Lvl 1: Theme.AxpFixture                        ← fixture/themes.xml:5
Lvl 2: Theme.Material3.DayNight.NoActionBar    ← parent= 명시
Lvl 3: Theme.Material3.DayNight                ← dot-trim inference
Lvl 4: Theme.Material3.Light                   ← Material AAR alias
Lvl 5: Base.V14.Theme.Material3.Light          ← parent= 명시 (M3 path 의 핵심)
Lvl 6: Base.V14.Theme.Material3                ← dot-trim
...
Lvl 9: Base.V14.Theme.MaterialComponents.Light.Bridge ← M2 fallback (isMaterialTheme=true 정의)
...
Lvl 15: android:Theme
```

T17 의 H1 (Lvl 5 reachable by name) + H3a (findItemInTheme materialButtonStyle returns Widget.Material3.Button) PASS = chain walker 가 M3 path (Lvl 5) 에 정확히 도달.

### §1.5 BridgeContext defStyleAttr/defStyleRes dispatch (subagent C 검증, T18 fix 후)

`/tmp/w3d4d-BridgeContext-javap.txt:1782` (`internalObtainStyledAttributes`):
- offset 1904-1959: defStyleAttr resolution path (`searchAttr → findItemInTheme → resolveResValue → instanceof StyleResourceValue cast → store var 12`).
- offset 1927-1941: priority logic — `aload var 12; ifnonnull 555` 즉 **defStyleAttr 결과가 non-null 이면 defStyleRes 무시**.
- offset 332-405: defStyleRes fallback (var 12 == null 일 때만) — `Bridge.resolveResourceId(int) → ResourceRef`, `getStyle(ResourceRef) → StyleResourceValue`.

`/tmp/w3d4d-materialbutton-javap.txt:71` (3-arg constructor): `defStyleAttr = R.attr.materialButtonStyle` (non-zero).
같은 file:2444-2445 (class init): `defStyleRes = R.style.Widget_MaterialComponents_Button` (M2).

**T18 fix 후 dispatch 결정** — chain walker 가 `?attr/materialButtonStyle → @style/Widget.Material3.Button` (M3) 발견 → BridgeContext var 12 = StyleResourceValue(Widget.Material3.Button) → line 329 `ifnonnull 555` → **M3 dispatch active**, defStyleRes (M2) 무시.

그러나 `Widget.Material3.Button:5238` parent= `Widget.MaterialComponents.Button:6348` 이고 후자가 `enforceMaterialTheme=true:6349` 정의 — Widget.Material3.Button 자신은 override 안 함. 즉 M3 dispatch 후도 `obtainStyledAttributes(R.styleable.ThemeEnforcement)` 의 enforceMaterialTheme 가 **true 상속**.

→ **옵션 C 는 closed** — M3 dispatch 가 이미 active 이지만 enforceMaterialTheme 우회 효과 없음. δ-B fix 는 chain walker / dispatch layer 가 아닌 **theme-side fix**.

---

## §2 Root cause + 옵션 A/B/C cost-benefit

### §2.1 Root cause (T18 후 sequence)

1. `MaterialButton.<init>(Context, AttributeSet, R.attr.materialButtonStyle)` (line 53 of MaterialButton bytecode).
2. super call passes `defStyleRes = R.style.Widget_MaterialComponents_Button` (M2).
3. `MaterialButton.<init>:239` 부근 `ThemeEnforcement.obtainStyledAttributes(...)` 호출 (te_disasm.txt:53-89 / 76 영역의 wrapper).
4. wrapper 가 `checkCompatibleTheme(ctx, set, defStyleAttr, defStyleRes)` 호출 → `obtainStyledAttributes(set, ThemeEnforcement_styleable, defStyleAttr=materialButtonStyle, defStyleRes=Widget.MaterialComponents.Button)` 수행.
5. M3 path (Widget.Material3.Button) 가 dispatch 됨 (T18 + chain walker + BridgeContext line 329 priority).
6. Widget.Material3.Button 부모 chain → enforceMaterialTheme=true 상속.
7. te_disasm.txt:107 enforceMaterialTheme=true → fall through (skip 안함).
8. te_disasm.txt:117 `Theme.resolveAttribute(R.attr.isMaterialTheme)` — Theme.AxpFixture 자체 정의 부재 (themes.xml:5-11). chain inheritance 측 의 Lvl 9 Bridge:3021 의 `isMaterialTheme=true` 가 chain walker 단계에서 reachable 인지는 empirically 미측정 (T17 의 H1 은 Lvl 5 의 `materialButtonStyle` 정의 reachability 만 검증). 실 fail 의 BridgeContext layer 단계에서 `resolveAttribute` 가 false 반환 — 이 단계의 정확한 mechanism 은 P2/P6-equivalent (§4.2 footnote) 측정 대상.
9. te_disasm.txt:120 ifeq 80 → checkMaterialTheme 진입.
10. checkMaterialTheme:320 → checkTheme(ctx, int[]{colorPrimaryVariant}, "Theme.MaterialComponents"):247.
11. checkTheme:414 isTheme(ctx, int[]) → `Theme.obtainStyledAttributes(int[]{colorPrimaryVariant}).hasValue(0)`.
12. **hasValue=false** (Theme.AxpFixture 자체에 colorPrimaryVariant 미정의 + chain 의 ?attr/colorPrimary chain 이 BridgeContext.obtainStyledAttributes(int[]) 의 attribute resolution path 에서 어느 단계에서 단절 — empirical T20 측정 대상).
13. throw IllegalArgumentException.

### §2.2 옵션 A — Theme.AxpFixture 에 `<item name="isMaterialTheme">true</item>` 추가

**Enable mechanism**: te_disasm.txt:124 의 `value.type=TYPE_INT_BOOLEAN(18)` AND `value.data≠0` (true) → goto 84 → checkMaterialTheme **SKIP**. Theme stack 의 walk (`Theme.resolveAttribute`) 가 fixture 의 1-line 정의 즉시 발견.

**Cost**: 1-line.

**Side effect (medium-low — round 6 Q4 grep 검증)**:
- `checkMaterialTheme` 자체 우회 → `colorPrimaryVariant` hasValue 검사 SKIP.
- Round 6 Codex+Claude convergent grep 결과: Material AAR 안 `colorPrimaryVariant` 의 직접 reader = `ThemeEnforcement.class` **유일** (`/tmp/w3d4db-codex-material-class-strings.txt:2982` + `MATERIAL_CHECK_ATTRS` init at `/tmp/w3d4db-codex-ThemeEnforcement-javap-v.txt:895-905`). 즉 옵션 A 의 'Snackbar/BadgeDrawable silent fail' 위험은 plan 작성 시 과장. 단 **다른 위험**: `BadgeDrawable` 가 `checkMaterialTheme` 직접 호출 (`/tmp/w3d4db-codex-BadgeDrawable-javap-v.txt:1418-1423`) — 옵션 A 가 *fixture chain 의 isMaterialTheme=true 단서* 만 제공하므로 본 widget 의 path 도 동일 effect (BadgeDrawable 의 checkMaterialTheme 가 isTheme PASS — fixture isMaterialTheme=true 가 직접 도움). 하지만 `BaseTransientBottomBar (Snackbar)` 는 `checkAppCompatTheme` 호출 (`/tmp/w3d4db-codex-BaseTransientBottomBar-javap-v.txt:1298-1302`) — 옵션 A 와 무관 (별도 path).
- **주된 단점은 semantic strictness**: 옵션 A 는 *gate skip* (compliance via short-circuit) 이고 옵션 B 는 *gate PASS* (compliance via actual attr definition). 후자가 fixture 의 의도 (Material3 colored theme) 와 정합.

**Coverage**: 본 phase 의 acceptance gate. Future Material widget 가 ThemeEnforcement *외* path 로 colorPrimaryVariant 사용 site 는 grep 결과 0 — 단 이는 현 Material AAR 1.12.0 기준 census, future minor version 변동 시 재검증 필요.

### §2.3 옵션 B — Theme.AxpFixture 에 `<item name="colorPrimaryVariant">?attr/colorPrimary</item>` 추가 (DOMINANT)

**Enable mechanism**: te_disasm.txt:411 isTheme path 의 `Theme.obtainStyledAttributes(int[]{colorPrimaryVariant}).hasValue(0)` 가 fixture 의 직접 정의 발견 → `?attr/colorPrimary` 가 raw 값 위치를 가지는 ref entry 로 resolve → **hasValue=true**. 또한 `?attr/colorPrimary` chain 의 hop target (fixture themes.xml:6 의 `colorPrimary=#6750A4`) 이 valid concrete color → resolved value 도 정합.

**Cost**: 1-line.

**Side effect (낮음)**:
- `colorPrimaryVariant` 가 fixture 안에서 standard Material attr 로 정의됨 — Material AAR 의 다른 widget 가 이 attr 사용 시 정합 resolve.
- `?attr/colorPrimary` chain 은 layoutlib chain walker 가 이미 처리 (LayoutlibRenderResources.resolveResValue:153-186, MAX_REF_HOPS=10).
- fixture 의 의도 (Material3 colored theme) 와 정합.

**Coverage**: 옵션 A 보다 strict — checkMaterialTheme PASS 가 actual semantic 요구 충족 (colorPrimaryVariant 정의됨).

### §2.4 옵션 C — M3 path dispatch (closed)

§1.5 검증 — T18 후 BridgeContext 가 이미 M3 dispatch active. 단 `Widget.Material3.Button` (values.xml:5238-5257) 본체 안 `enforceMaterialTheme` override **부재** → 부모 `Widget.MaterialComponents.Button` (values.xml:6348-6371) 의 `<item name="enforceMaterialTheme">true</item>` (line 6349) 상속. M3 dispatch 가 enforceMaterialTheme 우회 효과 없음.

**옵션 C 만으로 close 불가**. `Widget.Material3.Button` 의 enforceMaterialTheme override 가 Material AAR 자체에 없어서 → 우리가 fixture 안에서 ThemeOverlay 또는 다른 mechanism 으로 강제 override 가능하나 그 경우 fixture XML 변경량 ≥ 옵션 B → ROI 부족.

### §2.5 Ranking

| 옵션 | cost | coverage | side-effect | future-proof |
|---|---|---|---|---|
| A (isMaterialTheme=true) | 1-line | gate skip (semantic loose) | medium-low (round 6 grep: ThemeEnforcement 외 colorPrimaryVariant reader 0; BadgeDrawable 직접 checkMaterialTheme 호출은 옵션 A 도 동일 우회) | low (skip 기반) |
| **B (colorPrimaryVariant=?attr/colorPrimary)** | **1-line** | **gate PASS (actual semantic)** | **low** | **high (attr 정의 기반)** |
| C (M3 path) | n/a | closed (Widget.Material3.Button 상속) | n/a | n/a |

**Dominant**: **옵션 B**. T20 의 5-probe diagnostic 이 옵션 B 의 enable mechanism (chain walker 의 colorPrimaryVariant resolve + ?attr/colorPrimary chain hop + Theme stack 의 hasValue equivalent) 을 empirically 검증.

---

## §3 Scope & task split

| Task | 변경 surface | commit |
|---|---|---|
| T20 | Diagnostic 5-probe IT 신규 (`W3D4DeltaBGateChainProbeTest.kt`) | 1 |
| T21 | `fixture/sample-app/app/src/main/res/values/themes.xml` 1-line + T20 결과 PASS 전환 검증 | 1 |
| T22 | `LayoutlibRendererIntegrationTest.tier3 basic primary` 의 `@Disabled` 제거 + IT 측정 | 1 |

각 commit 즉시 push (CLAUDE.md task-unit completion). T20 IT-tagged 이므로 default unit suite 무영향 (T17 와 동일 패턴, LM-W3D4-δ-D carry).

---

## §4 T20 — Diagnostic 5-probe battery

### §4.1 변경 파일

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/W3D4DeltaBGateChainProbeTest.kt` (신규).

T17 (`W3D4DeltaThemeChainDiagnosticTest`) 와 동일 entry pattern (`locate()` + `LayoutlibResourceValueLoader.loadOrGet` + `LayoutlibRenderResources` instantiation). 5 probe:

```kotlin
package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
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
 * Empirically narrows whether the Material gate chain (enforceMaterialTheme →
 * checkMaterialTheme → colorPrimaryVariant hasValue) can be closed by adding
 * colorPrimaryVariant to Theme.AxpFixture. Each probe maps 1:1 to one segment
 * of the resolution flow (attr definition, theme stack walk, attr-ref chain
 * hop, T18 regression guard, and a Material STYLE-side guard).
 *
 * Integration-tagged because every probe depends on real fixture build
 * artefacts. The default unit suite excludes the integration tag, so this
 * class can land red without breaking the default gate; opt-in via
 * -PincludeTags=integration.
 */
@Tag("integration")
class W3D4DeltaBGateChainProbeTest
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
    fun `P1 — colorPrimaryVariant attr is defined in the bundle as RES_AUTO`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimaryVariant")
        val resolved = bundle.getResource(attrRef)
        assertNotNull(resolved)
    }

    @Test
    fun `P2 — findItemInTheme colorPrimaryVariant returns the chain item`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimaryVariant")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
    }

    @Test
    fun `P3 — resolveResValue of the chain item terminates on a concrete color value`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimaryVariant")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
        val resolved = resources.resolveResValue(item)
        assertNotNull(resolved)
        val raw = resolved!!.value
        assertNotNull(raw)
        assertTrue(
            raw!!.startsWith("#") || raw.startsWith("@color/"),
            "post-fix expects concrete color value (e.g. #6750A4) or final @color/ ref, got: $raw",
        )
    }

    @Test
    fun `P4 — T18 regression guard — getResource STYLE Widget_Material3_Button still returns the style`()
    {
        val styleRef = ResourceReference(
            ResourceNamespace.RES_AUTO,
            ResourceType.STYLE,
            "Widget.Material3.Button",
        )
        val resolved = bundle.getResource(styleRef)
        assertNotNull(resolved)
        assertTrue(resolved is StyleResourceValue)
    }

    @Test
    fun `P5 — colorPrimary chain anchor is concrete in fixture (anchor of P3 chain)`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimary")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
        val resolved = resources.resolveResValue(item)
        assertNotNull(resolved)
        val raw = resolved!!.value
        assertNotNull(raw)
        assertTrue(
            raw == "#6750A4" || raw!!.startsWith("@color/"),
            "fixture defines colorPrimary directly, got: $raw",
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

### §4.2 무엇을 측정하는가

| probe | T21 전 (current) 예상 | T21 후 (post-fix) 예상 | 의미 |
|---|---|---|---|
| P1 attr 정의 | PASS | PASS | Material AAR 의 `<attr name="colorPrimaryVariant"/>:53` 는 RES_AUTO bucket 에 등록됨 (T14 + buildBucket) |
| P2 findItemInTheme | **FAIL or PASS** | **PASS** (fixture 직접 정의 도달) | Theme stack walk 가 fixture 의 `colorPrimaryVariant` 정의 발견하는지 |
| P3 resolveResValue | FAIL | **PASS** (#6750A4 또는 @color/...) | ?attr/colorPrimary chain hop 이 fixture colorPrimary 까지 정합 |
| P4 STYLE getResource | PASS (T18 carry) | PASS (regression guard) | T18 의 STYLE special-case 가 본 phase 적용 후도 정합 |
| P5 colorPrimary chain | PASS | PASS | fixture 자체의 colorPrimary anchor 검증 (P3 chain 의 종착점) |

**P2 의 current state 가 본 phase 의 결정적 측정** — 이미 PASS 라면 chain walker 만으로 inheritance chain 의 Lvl 5:2214 `?attr/colorPrimary` 도달 가능 (Theme.AxpFixture parent chain 안). 이 경우 옵션 B 의 fix 가 *redundant* 일 가능성 — chain walker 와 BridgeContext.hasValue path 가 동일 mechanism 위 (round 6 Q1 trace 참조 — 아래 footnote).

**Round 6 Q1+Q2 BridgeContext-equivalence note (Codex+Claude convergent)**: `Resources.Theme.obtainStyledAttributes(int[])` → `Resources_Theme_Delegate.internalObtainStyledAttributes` (`/tmp/w3d4db-codex-Resources_Theme_Delegate-javap.txt:23-31`) → `BridgeContext.internalObtainStyledAttributes(0, attrs)` → `BridgeContext.createStyleBasedTypedArray(style=null, attrs)` (BridgeContext-javap.txt:1782 / offset 2455+76-88 / 2494-2522) → **`mRenderResources.findItemInTheme(attrRef)`** (LayoutlibRenderResources.kt:211-221) → `resolveResValue` → `bridgeSetValue` → `BridgeTypedArray.hasValue(slot)` (`/tmp/w3d4db-codex-BridgeTypedArray-javap.txt:1567-1584`) returns `mResourceData[slot] != null`. **즉 P2 + P3 PASS = BridgeContext.hasValue=true 동치** (chain walker 가 BridgeContext 의 mRenderResources 와 동일 instance). residual risk = `Resources_Theme_Delegate.setupResources` (offset 165-199) 가 active RenderResources stack 을 mutate 하는 단계 — IT 직접 검증은 T22 acceptance gate 가 cover.

**P2 FAIL 시**: 옵션 B fix (themes.xml 1-line) 이 P2/P3 둘 다 PASS 로 전환. T22 acceptance gate close.
**P2 PASS 시 (예상)**: 옵션 B fix 가 chain shortening — Lvl 1 (Theme.AxpFixture 자체) 에서 즉시 발견. shadowing safe (round 6 Q5: M2 concrete fallback 만 bypass, 의미적 동치). T22 가 BridgeContext-side acceptance probe — PASS 로 transition. fail 시 §6.3 escalation.

---

## §5 T21 — fixture themes.xml 1-line add (옵션 B)

### §5.1 변경 파일

`fixture/sample-app/app/src/main/res/values/themes.xml`:

```diff
 <style name="Theme.AxpFixture" parent="Theme.Material3.DayNight.NoActionBar">
     <item name="colorPrimary">#6750A4</item>
     <item name="colorOnPrimary">#FFFFFF</item>
     <item name="colorPrimaryContainer">#EADDFF</item>
+    <item name="colorPrimaryVariant">?attr/colorPrimary</item>
     <item name="colorOnSurface">#1C1B1F</item>
     <item name="colorOnSurfaceVariant">#49454F</item>
 </style>
```

근거: `?attr/colorPrimary` chain 이 fixture 의 1-line 정의로 hop 가능 (LayoutlibRenderResources.resolveResValue:153-186, MAX_REF_HOPS=10). Material Bridge AAR 의 concrete color (`@color/design_default_color_primary_variant:3023`) 를 사용하는 대신 fixture 의 colorPrimary anchor 를 재사용 — fixture 의 #6750A4 와 정합 (Material3 colored theme pattern).

### §5.2 T20 재측정

T21 commit 후 즉시:
```bash
./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.resources.W3D4DeltaBGateChainProbeTest"
```

**예상**: 5/5 PASS (P1~P5). P2 의 transition 이 fix 의 actual mechanism 검증.

### §5.3 회귀 가드

T17 5-probe 도 동시 재측정:
```bash
./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.resources.W3D4DeltaThemeChainDiagnosticTest"
```

**예상**: 5/5 PASS (LM-W3D4-δ-B carry — STYLE special-case 회귀 없음).

---

## §6 T22 — Acceptance gate

### §6.1 변경

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`:

```diff
-    @org.junit.jupiter.api.Disabled(
-        "MaterialButton.<init> throws \"The style on this component requires your app theme " +
-            "to be Theme.MaterialComponents (or a descendant).\" via ThemeEnforcement." +
-            "checkCompatibleTheme → checkMaterialTheme. Closing this gate requires either " +
-            "exposing isMaterialTheme=true on the fixture theme chain to short-circuit " +
-            "checkCompatibleTheme, or wiring colorPrimaryVariant chain resolution so " +
-            "checkMaterialTheme's hasValue probe succeeds, or routing MaterialButton " +
-            "through the M3 path (Widget.Material3.Button) so enforceMaterialTheme=false " +
-            "skips the gate entirely.",
-    )
     @Test
     fun `tier3 basic primary — activity_basic 가 직접 SUCCESS`()
```

### §6.2 PASS 조건

- `Result.Status.SUCCESS == renderer.lastSessionResult?.status`
- `bytes.size > MIN_RENDERED_PNG_BYTES` + `isPngMagic(bytes) == true`
- IT 수: 19 PASS + 2 SKIP → **20 PASS + 1 SKIP** (`tier3-glyph` W4 carry only).
- system-err 에 `[layoutlib.warning]` 또는 `IllegalArgumentException` 없음.

### §6.3 실패 시 escalation 정책 (LM-W3D4-β-H + LM-W3D4-δ-E carry)

새 fail surface 식별 시 stack-trace entry-point 비교 → `t22-acceptance-gate-followup.md`. Hypothesis 후보:
- **W3D4-ε (R$styleable layer)**: `RJarSymbolSeeder.kt:64-66` skip 의 후속 layer. callback 의 `getOrGenerateResourceId` / `resolveResourceId` instrumentation (`axp.debug.callback`, plan v3.2 §5.3.1 carry) 으로 narrow.
- **direct-sentinel widget surface**: BadgeDrawable 의 `checkMaterialTheme` 직접 호출 (`/tmp/w3d4db-codex-BadgeDrawable-javap-v.txt:1418-1423`) + BaseTransientBottomBar (Snackbar) 의 `checkAppCompatTheme` 직접 호출 (`/tmp/w3d4db-codex-BaseTransientBottomBar-javap-v.txt:1298-1302`) — 현 fixture 부재 이므로 본 phase 무관. 옵션 B 가 fixture 의 colorPrimaryVariant 정의를 추가하므로 future fixture 가 BadgeDrawable 추가 시 동일 fix mechanism 으로 close 가능 (BadgeDrawable.checkMaterialTheme 가 isTheme(int[]{colorPrimaryVariant}) 사용).
- **Resources_Theme_Delegate.setupResources mutation** (Codex Q1 caveat, `/tmp/w3d4db-codex-Resources_Theme_Delegate-javap.txt:165-199`): active RenderResources stack 을 setupResources 가 mutate 가능. T22 fail + T20 P2 PASS 시나리오에서 본 path 의 instrumentation 권고 (callback 의 setStyle/applyStyle 호출 trace).

---

## §7 Q1–Q6 — round 6 pair-review query (Codex+Claude, plan-revision phase)

### Q1 — H2 (체인 walker → BridgeContext layer 의 hasValue 정합) 의 정확성
"§2.1 step 11 의 Theme.obtainStyledAttributes(int[]).hasValue(0) 는 layoutlib BridgeContext 의 어느 method 가 호출 처리? `getInternalThemeStyledAttribute`? `BridgeContext.obtainStyledAttributes(int[])` 를 BridgeContext-javap.txt 안에서 trace. T20 P2 가 PASS (chain walker 발견) but T22 FAIL (BridgeContext hasValue=false) 시나리오의 BridgeContext path 가 chain walker 의 mThemeStack 사용하는지 별도 layer 인지 확인."

### Q2 — 옵션 B fix 가 P2 PASS 와 BridgeContext.hasValue=true 를 동시에 enable 하는가?
"§5.1 의 fixture themes.xml 변경이 chain walker 에서 보면 fixture style 의 `<item name="colorPrimaryVariant">?attr/colorPrimary</item>` 자체가 raw value 위치 (StyleResourceValueImpl.items map). Theme.obtainStyledAttributes(int[]{colorPrimaryVariant}) 의 BridgeContext 처리가 본 item 을 직접 인식하는가, 또는 chain walker 의 mThemeStack 통해 indirection 추가? 양 path 가 동일 결과 보장?"

### Q3 — T20 5-probe 가 옵션 B 의 enable mechanism 모든 layer 를 cover 하는가?
"§4.1 의 P1~P5 가 (a) bundle attr 등록 (b) Theme stack 의 chain walker 발견 (c) attr-ref chain hop (d) STYLE special-case 회귀 (e) chain anchor 까지 cover. BridgeContext 측 hasValue 검증은 IT 가능? T20 모두 PASS but T22 FAIL 시 추가 instrumentation 권고?"

**Round 6 reconcile**: Codex 가 BridgeContext-side standalone probe (P6/P7) 추가 권고, Claude 는 P2+P3 ≡ BridgeContext.hasValue path equivalence 주장. 양쪽의 BridgeContext bytecode trace (createStyleBasedTypedArray:2494-2499 → mRenderResources.findItemInTheme) 가 일치. **resolution**: P6/P7 standalone IT 미추가 (BridgeContext 부트스트랩 비용 > benefit), §4.2 footnote 에 equivalence note 추가 + §6.3 에 setupResources caveat 추가. T22 가 end-to-end probe 역할.

### Q4 — 옵션 A 의 부작용 quantification
"§2.2 의 옵션 A 부작용 — Snackbar/BadgeDrawable 의 colorPrimaryVariant 직접 사용 site enumerate (Material AAR 안 grep). 실제로 fixture 가 future Snackbar 추가 시 surface 전환 가능성 평가."

### Q5 — Theme.AxpFixture parent chain 의 chain walker reachability 가 옵션 B 에서 변하는가?
"§5.1 의 fixture 1-line 추가 후 mThemeStack walking 결과 — fixture 자체 의 colorPrimaryVariant 가 Lvl 1 (Theme.AxpFixture) 정의로 등록 → chain walker 가 즉시 발견 (Lvl 5 도달 안 함). 즉 chain shortening 효과. 부작용?"

### Q6 — open critique 슬롯 (round 4/5 정책 일관)
"본 plan v4 의 file:line 검증 외, 양쪽 reviewer 가 독립적으로 발견한 결함이 있다면 명시. 예:
- §4.1 의 P5 가 `colorPrimary` 의 raw 값 종착 — 그러나 fixture 의 `#6750A4` 는 raw color 가 아닌 raw string (StringResourceValue 반환?) 일 가능성.
- §5.1 의 1-line 추가가 Material AAR 의 다른 attr 사용 widget (e.g. AppBarLayout, BottomNavigationView) 의 inflate behavior 영향.
- §6.3 의 escalation 후보 중 'theme overlay layer' 가 plan v3.2 §11 out-of-scope 와 중복.
- T20 의 P3 expectation 이 chain anchor 다양성 (concrete `#hex` vs `@color/`) 에 robust 한가?"

---

## §8 LM (landmines) 회피 정책

| LM 코드 | 사유 | 본 plan 의 방어 |
|---|---|---|
| LM-W3D3-A / LM-α-A | empirical-verifiable claim 직접 측정 | §1 모두 file:line 인용 (te_disasm + values.xml + javap) |
| LM-α-B | dual-channel verdict, single-source 회피 | round 6 pair-review |
| LM-G | codex sandbox bypass | round 6 직접 CLI: `codex exec --skip-git-repo-check --sandbox danger-full-access` |
| LM-W3D3-B | JUnit Jupiter Assertions only | §4.1 신규 코드 `org.junit.jupiter.api.Assertions.*` |
| LM-α-D | Kotlin backtick 함수명 안 마침표 금지 | §4.1 의 모든 backtick 테스트명 검증 (P1~P5 prefix) |
| LM-W3D4-D | placeholder 관행 | §4.1 / §5.1 / §6.1 모두 explicit diff |
| LM-W3D4-β-D | KDoc `/*` 회피 | 신규 코드 KDoc 모두 backtick 또는 prose |
| LM-W3D4-β-E | assertNotNull chain 금지 | §4.1 의 `val v = ...; assertNotNull(v); v!!.method()` 패턴 일관 |
| LM-W3D4-β-F | IT 실행은 `-PincludeTags=integration` | §5.2 / §6.1 명시 |
| LM-W3D4-β-G | subagent unzip `--directory /tmp/...` | round 6 의 모든 subagent prompt 에 명시 |
| LM-W3D4-β-H | acceptance fail 시 stack-trace entry 비교 | §6.3 직접 인용 + LM-W3D4-δ-E carry |
| LM-W3D4-γ-A | reviewer prompt 에 "lookup path trace" 명시 | round 6 prompt 에 "BridgeContext.obtainStyledAttributes(int[]) 의 hasValue path trace" 명시 |
| LM-W3D4-δ-A | spec 코드 sample 의 모든 식별자 grep/Read 검증 | §4.1 식별자 verify ✓ — `LayoutlibResourceValueLoader.Args` (loader.kt:17-21), `clearCache` (loader.kt:30), `loadOrGet` (loader.kt:25-28), `bundle.getResource` (bundle.kt:50-63 post-T18 STYLE special-case), `findItemInTheme` (renderResources.kt:211-222), `resolveResValue` (renderResources.kt:144-187), `DistDiscovery.locate` (DistDiscovery.kt:28-32), `FixtureDiscovery.locateModuleRoot` (FixtureDiscovery.kt:39-43), `ResourceLoaderConstants.DATA_DIR` (loaderConstants.kt:13), `AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH` (appConstants.kt:12), `SessionConstants.DEFAULT_FIXTURE_THEME` (sessionConstants.kt:50). 양쪽 round 6 reviewer self-check 동일 결론 (no unverified identifier). |
| LM-W3D4-δ-B | NsBucket type-specific map 분리 sibling KILL POINT | T20 P4 가 T18 의 STYLE special-case 회귀 가드 |
| LM-W3D4-δ-C | runtime-classpath.txt 부재 silent empty AAR | T20 의 graceful skip (assumeTrue) 패턴 보존 |
| LM-W3D4-δ-D | T17 / T20 IT-tagged RED-on-main 가능 | §3 명시 (default unit suite 무영향) |
| LM-W3D4-δ-E | acceptance fail surface 의 stack-trace entry-point 비교 | §6.3 의 "stack-trace entry 비교" carry |
| LM-W3D4-δ-F | CLAUDE.md Rule 2 OUT-OF-SCOPE 확대 — phase/task identifier 회피 | §4.1 / §6.1 의 KDoc/annotation 영문 + structural-only |
| LM (CLAUDE.md Three Hard Rules) | 모든 KDoc/inline 영문 only, function/structure only | 본 plan 의 신규 코드 sample KDoc 검증 ✓ |
| **LM-W3D4-δ-G (round 6 신규)** | annotation message (e.g. `@Disabled` reason) 안 future option 의 *technical claim* 작성 시 file:line evidence 사전 verify 의무 | round 6 의 stale text catch (T19 의 `@Disabled` message 안 "M3 path 가 enforceMaterialTheme=false 로 SKIP" 잘못 진술 — 실제 Widget.Material3.Button 본체 override 부재, true 상속) 가 trigger. plan v4 의 T22 가 본 stale @Disabled 제거하므로 자동 close. future annotation 작성 시 동일 LM 적용. |

---

## §9 Test impact summary

| 단계 | layoutlib-worker unit | IT (PASS + SKIP) | 비고 |
|---|---|---|---|
| baseline (T19 partial 후) | 198 | 19 + 2 SKIP | tier3-basic-primary δ-B `@Disabled`, tier3-glyph W4 carry |
| T20 적용 후 | 198 (+0, IT-tagged → unit gate 영향 없음) | **24 + 2 SKIP** if `-PincludeTags=integration` (+5 W3D4DeltaBGateChainProbeTest probes; current state 측정) | P2 + P3 의 PASS/FAIL 으로 옵션 B mechanism 검증 |
| T21 적용 후 (옵션 B) | 198 (+0, fixture XML 변경 — kotlin compile 무영향) | T20 의 P2 + P3 PASS 로 전환 | 5-probe 모두 PASS |
| T22 적용 후 | 198 (변동 없음) | **25 PASS + 1 SKIP** | tier3-basic-primary 닫힘, tier3-glyph 만 SKIP |

**모듈 합산 unit baseline 241 → T22 후 241 예상** (T20/T21 모두 unit gate 무영향). IT 19+2 → 25+1 transition.

---

## §10 Commit/push 단위

CLAUDE.md task-unit completion 정책:
- T20 commit: `feat(w3d4-delta-b): T20 gate-chain probe IT (5-probe colorPrimaryVariant chain diagnostic)`
- T21 commit: `feat(w3d4-delta-b): T21 fixture colorPrimaryVariant 1-line — checkMaterialTheme.hasValue gate close`
- T22 commit: `feat(w3d4-delta-b): T22 tier3-basic-primary 닫힘 — δ-A + δ-B 양쪽 sentinel layer closed`
- 각 task PASS 후 즉시 push.
- round 6 pair verdict 는 별도 work_log entry: `docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md`.

---

## §11 Out-of-scope (본 plan 이 다루지 않는 것)

- **W3D4-ε** (R$styleable layer) — `RJarSymbolSeeder.kt:64-66` skip. T22 fail 시 §6.3 의 callback instrumentation 으로 narrow 후 trigger.
- **theme overlay support** — `materialThemeOverlay` (Widget.Material3.Button:5249, ThemeOverlay.Material3.Button) 의 chain walker 처리. 본 phase 의 colorPrimaryVariant 정의가 fixture 자체 정의이므로 ThemeOverlay 미경유 — 단 future Material widget 가 ThemeOverlay 의존 시 surface.
- **direct-sentinel widget surface**: BadgeDrawable 직접 `checkMaterialTheme` (`/tmp/w3d4db-codex-BadgeDrawable-javap-v.txt:1418-1423`) + BaseTransientBottomBar 직접 `checkAppCompatTheme` (`/tmp/w3d4db-codex-BaseTransientBottomBar-javap-v.txt:1298-1302`) — 현 fixture 부재. 옵션 B 의 colorPrimaryVariant 가 future BadgeDrawable 추가 시 동일 fix mechanism close.
- **W3D4 tier3-glyph** (Font wiring) — W4 carry.
- **DRAWABLE selector XML feed** — plan v3 §5.4 T12.5 escalation. 별도 phase.
- **POST-W2D6-POM-RESOLVE** — W4+ scope.

---

## §12 Round 6 reconcile (완료 — 2026-05-06)

본 plan v4 는 round 6 pair-review (Codex codex-cli 0.128.0 latest GPT xhigh + Claude planning subagent) 에서 verify 후 inline 적용 완료. 자세한 review 는 `docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md`.

### §12.1 Round 6 verdict + convergence map

| 채널 | verdict | confidence | 핵심 finding |
|---|---|---|---|
| Codex (latest GPT, xhigh) | REVISE | 0.84 | 3 KILLPOINTS (plan §1.3/§2.1/§6.1 의 contradictory file:line 진술) + Q3 의 BridgeContext-side P6/P7 권고 + Q4 의 colorPrimaryVariant direct reader = ThemeEnforcement only. |
| Claude (Plan agent) | GO_WITH_FIXES | 0.86 | §4.1 P5 null-safety + §6.3 theme overlay redundancy + §2.2 옵션 A side-effect 과장 + §1.5 M3 dispatch active 검증. |

**Convergence**: 양쪽 옵션 B dominant 수렴. Q1 (BridgeContext path) / Q2 (option B 의 hasValue=true enable) / Q4 (colorPrimaryVariant direct reader census) / Q5 (shadowing safe) 모두 file:line 인용 일관.

**Divergence**: Q3 의 T20 coverage scope (Codex strict P6/P7 vs Claude minimal P2+P3 equivalence). resolution: equivalence note + setupResources caveat 으로 union 적용, P6/P7 standalone IT 미추가 (BridgeContext 부트스트랩 비용 > benefit).

**Q6 KILLPOINTS** (Codex 3 항목): 직접 verify 후 모두 inline 정정. memory feedback_pair_review_codex_killpoint.md 패턴 적용 (judge round 불필요).

### §12.2 Adopted plan deltas (12 — 모두 inline 적용 완료)

1. **§1.3 census 정정** (Q6 Codex) — Lvl 5:2141-2142 의 `isMaterial3Theme=true` (≠ isMaterialTheme), Lvl 9:3020-3021 의 `isMaterialTheme=true` chain walker reachability 의 empirical uncertainty 명시.
2. **§2.1 step 8 정정** (Q6 Codex) — fixture-direct miss vs inheritance reachability 구분. T17 의 isMaterialTheme attr 미측정 명시.
3. **§2.2 옵션 A side-effect** (Q4 양쪽 + Q6 Codex) — "high" → "medium-low" + Codex Q4 grep evidence (`/tmp/w3d4db-codex-material-class-strings.txt:2982`) inline. BadgeDrawable 직접 checkMaterialTheme path + BaseTransientBottomBar direct checkAppCompatTheme path 명시.
4. **§2.5 ranking row** — 옵션 A side-effect "high" → "medium-low". 옵션 B dominance rationale = 'gate PASS vs SKIP semantic' 로 재정렬.
5. **§2.4 옵션 C** (Q6 Codex KILLPOINT) — Widget.Material3.Button 의 enforceMaterialTheme inheritance evidence (values.xml:5238-5257 + 6348-6371 + 6349) 명시.
6. **§4.1 P5 null-safety** (Q6 Claude) — `assertNotNull(raw)` 한 줄 추가.
7. **§4.2 P2/P3 footnote** (Q3 reconcile + Q1 양쪽) — Codex+Claude convergent BridgeContext path trace inline (Resources_Theme_Delegate:23-31 + BridgeContext-javap:2455-2522 + BridgeTypedArray-javap:1567-1584). P2+P3 ≡ BridgeContext.hasValue=true equivalence note. residual risk = setupResources mutation.
8. **§6.3 theme overlay 항목** (Q6 Claude) — §11 out-of-scope 와 중복 → 본 항목 제거 + setupResources caveat (Codex Q1) 추가.
9. **§7 Q3 reconcile note** — round 6 의 P6/P7 vs equivalence divergence + resolution inline.
10. **§8 LM-W3D4-δ-A self-check evidence** (Codex 권고) — 모든 식별자 file:line 인용 추가.
11. **§8 LM-W3D4-δ-G 신규** — annotation message technical claim verify 의무 (T19 `@Disabled` stale text catch 가 trigger).
12. **§11 out-of-scope** — direct-sentinel widget surface 에 Codex Q4 grep evidence (BadgeDrawable + BaseTransientBottomBar 의 정확 file:line) 명시.

### §12.3 LM 준수 검증

- LM-G: codex 직접 CLI `codex exec --skip-git-repo-check --sandbox danger-full-access` ✓
- LM-α-A: 양쪽 reviewer file:line 인용 (50+ Codex citations + 40+ Claude citations) ✓
- LM-α-B: dual-channel verdict, single-source 회피 ✓
- LM-W3D4-D: 모든 fix explicit diff (12 deltas inline). fabricated 0건 ✓
- LM-W3D4-β-G: `unzip -d /tmp/w3d4db-codex-...` 사용 — codex 가 BadgeDrawable/BaseTransientBottomBar/ThemeEnforcement/Resources_Theme_Delegate javap 를 본 패턴 사용 ✓
- LM-W3D4-γ-A: reviewer prompt "BridgeContext.obtainStyledAttributes(int[]) 의 hasValue path trace" — 양쪽이 createStyleBasedTypedArray:2494-2499 동일 path 도달 ✓
- LM-W3D4-δ-A: §4.1 모든 식별자 verify (Codex + Claude self-check 동일 결론) ✓
- LM-W3D4-δ-B: NsBucket type-specific 분리 — T20 P4 가 STYLE special-case 회귀 가드 ✓
- LM-W3D4-δ-E: stack-trace entry-point 비교 — Codex Q1 의 BridgeContext bytecode trace 가 본 LM 패턴 ✓
- LM-W3D4-δ-F: Rule 2 OUT-OF-SCOPE — 신규 코드 KDoc/annotation structural-only 영문 ✓
- **LM-W3D4-δ-G** (round 6 신규): annotation message technical claim verify 의무 — round 6 의 T19 `@Disabled` stale text catch 가 trigger.

**최종 verdict**: REVISE (Codex) + GO_WITH_FIXES (Claude) → APPLIED (12/12 deltas inline). plan v4 → **plan v4.1** → **GO** (post-revision). T20/T21/T22 구현 진입 승인.
