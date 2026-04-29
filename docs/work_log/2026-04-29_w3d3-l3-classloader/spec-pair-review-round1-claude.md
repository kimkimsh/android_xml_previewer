# W3D3 — L3 ClassLoader 설계 스펙 페어 리뷰 (Claude 半)

**Reviewer**: Claude (Opus 4.7, independent half of 1:1 pair)
**Date**: 2026-04-29
**Spec**: `docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md`
**Verdict**: **GO_WITH_FOLLOWUPS** (1 blocker + 5 follow-ups)

---

## A. Blockers (NO_GO conditions)

### B1 — `fixtureRoot` 의미 불일치 (CRITICAL)

**Spec citation**: §4.4 `SampleAppClassLoader.build(fixtureRoot, parent)` —
- `val cacheRoot = fixtureRoot.resolve("app").resolve("build")`
- `val rJar = fixtureRoot.resolve(R_JAR_RELATIVE_PATH)` 에서 `R_JAR_RELATIVE_PATH = "app/build/intermediates/..."` (§4.5).
- `SampleAppClasspathManifest.read(fixtureRoot)` 도 `fixtureRoot.resolve("app/build/axp/runtime-classpath.txt")` (§4.5 MANIFEST_RELATIVE_PATH).

**File evidence**:
- `/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt:17` — `FIXTURE_SUBPATH = "fixture/sample-app/app/src/main/res/layout"`.
- 이 값이 `LayoutlibRenderer.fixtureRoot` (`LayoutlibRenderer.kt:47, 157`) 로 전달되어 `fixtureRoot.resolve(layoutName)` (`activity_basic.xml`) 으로 사용된다.
- 즉 실제 `fixtureRoot = .../fixture/sample-app/app/src/main/res/layout`.

**Failure mode**: spec 가 가정하는 `fixtureRoot = .../fixture/sample-app` 와 실제 코드의 `fixtureRoot = .../fixture/sample-app/app/src/main/res/layout` 이 **4 단계 차이**. spec 의 `fixtureRoot.resolve(R_JAR_RELATIVE_PATH)` 는 `.../res/layout/app/build/intermediates/...` 라는 존재하지 않는 경로로 향한다 → `IllegalArgumentException("sample-app R.jar 누락")` 이 항상 throw → SampleAppClassLoader.build() 100% fail → T1 gate 실패.

**Fix (택 1)**:
- (권장) `LayoutlibRenderer` 가 manifest/R.jar 경로 산출용 별개 인자 `sampleAppModuleRoot: Path` (= `.../fixture/sample-app`) 를 받게 추가, `fixtureRoot` 와 분리. CLAUDE.md "No default parameter values" 정책에 따라 default 금지 — `Main.kt` / `SharedLayoutlibRenderer.getOrCreate` / 테스트의 모든 호출자가 명시 주입.
- 또는 spec 의 `R_JAR_RELATIVE_PATH` 와 `MANIFEST_RELATIVE_PATH` 를 `"../../../../app/build/..."` 로 하드코딩 — fragile, 권장하지 않음.
- 또는 `FixtureDiscovery` 가 모듈 루트도 같이 반환하도록 확장 (best long-term).

이 분리 없이는 spec §4.4 / §4.5 가 컴파일은 되어도 모든 integration test 에서 fail.

---

## B. Recommended follow-ups (GO_WITH_FOLLOWUPS)

### F1 — `LayoutlibCallback.findClass(String)` override 필요 (강 권장)

**Spec citation**: §4.6 — `loadView` 만 override.

**File evidence**: `/tmp/bridgeinflater.txt` (디컴파일 결과) 의 `findCustomInflater` 메서드:
- `BridgeInflater` 가 `BridgeContext.isAppCompatTheme() == true` 일 때 `mLayoutlibCallback.findClass("androidx.appcompat.app.AppCompatViewInflater")` 호출 (offset 100, 114).
- 기본 `LayoutlibCallback.findClass` 는 즉시 `ClassNotFoundException` throw — `findCustomInflater` 가 catch 후 `null` 반환 (Exception table line 1290-1294 확인).
- 결과적으로 AppCompat custom inflater 가 활성화되지 않음 → `AppCompatTextView` 가 아닌 raw `TextView` 로 inflate 됨. `<TextView ?attr/textAppearanceHeadlineMedium>` 등 AppCompat-specific attribute styling 이 누락 → fidelity 손실.

**Failure mode**: T1 gate (SUCCESS + PNG > 1000) 는 통과 가능하나 visual fidelity 가 spec §1.2 R3 가 주장하는 것보다 훨씬 더 낮음. spec 은 "custom attrs 가 default 로 fallback" 만 언급했지만, AppCompat ViewInflater 자체가 비활성화되는 별개 문제.

**Fix**: `MinimalLayoutlibCallback` 에 다음 추가:
```kotlin
override fun findClass(name: String): Class<*> = viewClassLoader.loadClass(name)
override fun hasAndroidXAppCompat(): Boolean = true  // sample-app 의존 그래프에 androidx.appcompat 존재
```

이 두 줄로 AppCompat custom inflater 경로가 활성화되어 `AppCompatTextView`/`AppCompatButton` 자동 치환이 동작.

### F2 — `loadClass` (3-arg) override 필요

**Spec citation**: §4.6 — `loadClass(name, sig, args)` override 미언급.

**File evidence**: `/tmp/bridgeinflater.txt:1878` — `BridgeInflater.loadCustomView` 가 `mLayoutlibCallback.loadClass(name, sig, args)` (silent=true 분기) 와 `mLayoutlibCallback.loadView(name, sig, args)` (silent=false 분기) 둘 다 사용.

`LayoutlibCallback.loadClass` 의 default impl 은 `loadView` 로 위임 (LayoutlibCallback bytecode 분석 결과 — `/tmp/layoutlibapi/com/android/ide/common/rendering/api/LayoutlibCallback.class`, public java.lang.Object loadClass:..., bytecode `4: invokevirtual ... loadView`).

따라서 spec 의 `loadView` override 만으로 silent 분기도 작동. 그러나 default 구현이 `loadView` exception 발생 시 `ClassNotFoundException` 으로 wrapping 한다 (Exception table 분석). 이는 spec §4.6 의 "ClassNotFoundException pass-through" claim 과 정확히 일치. **이 항목은 OK — F2 는 follow-up 이 아니라 verification note 로 격하**.

### F3 — `axpEmitClasspath` 가 transformed AAR 가 아닌 raw AAR 을 emit 함 (구조적 우려)

**Spec citation**: §4.1, §4.4 — `cp.resolvedConfiguration.resolvedArtifacts.map { it.file.absolutePath }` 후 worker 가 `AarExtractor` 로 직접 `classes.jar` 를 추출.

**Verification**:
```
$ ./gradlew :app:printDebugCp
.../androidx.constraintlayout/constraintlayout/2.1.4/.../constraintlayout-2.1.4.aar  (raw AAR)
.../com.google.android.material/material/1.12.0/.../material-1.12.0.aar  (raw AAR)
```

Raw AAR 이 emit 됨. AGP 자체는 빌드 시 `transforms-3/<hash>/transformed/jetified-<artifact>/jars/classes.jar` 로 transform 한 결과를 사용. spec 은 그 transform pipeline 을 우회하므로:
- (장점) transforms-3 의 hash 디렉토리 불안정성을 피함 — spec §4.3 이 sha1(absPath) 로 자체 cache 키 생성. 합리적.
- (위험) jetifier 가 적용되지 않은 raw classes.jar 사용. sample-app 의 모든 의존이 이미 androidx 이므로 jetifier 변환은 no-op → 영향 없음. 그러나 향후 fixture 에 legacy support library 가 추가되면 break.

**Failure mode**: 본 fixture 에서는 영향 없음. 다른 fixture 추가 시 (W3D4+) 깨질 수 있음 — spec 의 "Multi-fixture: W3D4+ carry" 가 이미 기록됨.

**Recommendation**: §6.2 알려진 한계 에 "no jetifier transform" 한 줄 추가.

### F4 — Manifest 의 중복 라인 처리 미정의

**Spec citation**: §4.1 — `joinToString("\n")` 후 `.sorted()` (`it.file.absolutePath`).

**Verification**:
```
.../androidx.core/core/1.13.0/.../core-1.13.0.aar
.../androidx.core/core/1.13.0/.../core-1.13.0.aar  (DUPLICATE)
.../androidx.fragment/fragment/1.3.6/.../fragment-1.3.6.aar
.../androidx.fragment/fragment/1.3.6/.../fragment-1.3.6.aar  (DUPLICATE)
```

동일 절대 경로가 2회 등장 (Gradle resolution graph 상 직접 + transitive 양쪽 경로).

**Failure mode**: TC1 (manifest parser) 는 이를 valid 로 통과시키되, `URLClassLoader` 에 같은 URL 이 2회 추가됨. URLClassLoader 는 dup URL 을 무시하므로 기능 영향은 없음. 그러나 spec §4.2 의 "라인이 공백" reject 와 일관성 부족.

**Fix**: §4.1 에 `.distinct()` 추가:
```kotlin
.map { it.file.absolutePath }.distinct().sorted()
```

또는 §4.4 의 `urls += ...` 시점에 중복 제거.

### F5 — `Locale` / `versionedparcelable` 등 transitive AAR 누락된 클래스 의존성

**File evidence**: resolvedArtifacts 의 `androidx.versionedparcelable:1.1.1` 과 같은 transitive 가 manifest 에 모두 포함됨. 따라서 `MaterialButton` extends `AppCompatButton` extends `View` 의 체인이 모두 SampleAppClassLoader 안에서 resolve 가능. 추가로 framework 레벨 의존(android.view.View 등)은 isolated CL 에서 해결.

**Verification**: `unzip -l layoutlib-14.0.11.jar | grep "android/view/View.class"` 확인됨 — `View`, `ViewGroup` 모두 layoutlib 내에 존재.

**Result**: 의존성 끊김 없음 — **OK**, 본 항목은 follow-up 이 아니라 verification note. 단, F1/F2 의 `findClass` override 가 추가될 때 worker 가 임의 클래스 로드를 허용하면 layoutlib 내부 클래스(e.g., `android.support.v7.app.AppCompatViewInflater`) 요청도 들어올 수 있으므로 `viewClassLoader.loadClass` 로 그대로 위임하면 isolated CL 에 없는 클래스는 ClassNotFoundException → catch → null 반환 (BridgeInflater 측 catch) 로 안전.

### F6 — `fixtureRoot` lazy build 의 단일 fixture 가정 (spec §4.7 Q4)

**Spec citation**: §4.7 — "`fixtureRoot` 이 sample-app 이 아닌 (예: 다른 fixture) 이면? — 현재 fixture-discovery 가 sample-app 만 지원."

**Verification**: `ls fixture/` → `sample-app` 하나만 존재. 단일 fixture 가정은 W3D3 에서 안전.

**Result**: spec 의 §4.7 옵션 A (lazy) 채택은 **올바름** (Q4 답변 참조). 단, B1 blocker 가 해결되면 manifest 누락 시점이 lazy 호출로 미뤄지므로 activity_minimal 은 manifest 없어도 PASS — 이 점은 spec 이 §5.3 회귀 가능성에서 이미 명시.

---

## C. 페어 리뷰 질문 Q1-Q5 (spec §7)

### Q1 (Gradle): `applicationVariants.all` vs `afterEvaluate { tasks.named("assembleDebug") }`

**Stance**: **disagree with spec — `androidComponents` API 권장**.

AGP 8.x 에서 `applicationVariants.all` 은 deprecated (variant API v1) — DSL 결정 시점에 호출되므로 `configurations.named("debugRuntimeClasspath")` 직접 참조와 결합하면 동일 효과. spec 의 `afterEvaluate { tasks.named("assembleDebug") }` 는 plugin order 에 민감하지만 본 fixture 처럼 minimal build 에서는 동작. 그러나 canonical 패턴은:

```kotlin
androidComponents.onVariants(selector().withName("debug")) { variant ->
    val cp = variant.runtimeConfiguration  // typed Provider, build cacheable
    ...
}
```

이 패턴은 (a) variant API v2 사용, (b) flavor 추가 시 자동으로 모든 debug variant 에 적용, (c) configuration cache 친화적. 그러나 본 W3D3 의 single-flavor + single-buildtype 환경에서는 spec 의 `afterEvaluate` 패턴도 정상 작동 — 본 follow-up 은 W3D4+ multi-fixture 시 적용으로 defer 해도 OK.

### Q2 (Cache): mtime 기반 vs sha256

**Stance**: **agree with spec (mtime 기반 충분)**.

Gradle modules-2 cache 는 좌표(group/artifact/version) + sha1 디렉토리 키 — 동일 좌표/버전의 contents 는 변경되지 않음 (sha1 mismatch 시 다른 디렉토리). spec §4.3 의 `sha1(aarPath.absolutePath)` 가 이미 implicit content-address. mtime invalidation 은 단지 caching 안전망. sha256 base 는:
- (장점) AAR 이 동일 경로에 다른 contents 로 overwrite 되는 시나리오 (~Maven-local SNAPSHOT) 방어.
- (단점) 매 호출 SHA256 계산 비용 (수 MB AAR × N 개 = 수십~수백 ms).

본 fixture 의 의존은 모두 release 좌표 (1.6.1, 1.12.0, 2.1.4) — SNAPSHOT 없음. mtime 충분.

### Q3 (loadView): `InvocationTargetException` unwrap

**Stance**: **agree with spec — unwrap 불요**.

`/tmp/bridgeinflater.txt` 분석 결과 `BridgeInflater.loadCustomView` 가 `loadClass`/`loadView` 결과를 `Object` 로 받아 `instanceof View` 확인 후 cast — 어떤 exception 도 catch 하지 않고 `throws Exception` 로 propagate. 그 위 `createViewFromTag` 가 `InflateException` 으로 wrap 후 `MockView` placeholder 처리. 즉 layoutlib 본체는 raw `InvocationTargetException` 도 정상 처리. unwrap 시 오히려 stack 정보 손실 우려.

단, `MinimalLayoutlibCallback.loadView` 는 `throws java.lang.Exception` (LayoutlibCallback 인터페이스 시그니처) — Kotlin 에서는 checked exception 강제 안 되지만 spec 의 "ctor.newInstance" 는 InvocationTargetException 을 던질 수 있음을 호출자가 인지해야 함. **OK**.

### Q4 (lazy build): 옵션 A vs B

**Stance**: **agree with spec — 옵션 A (lazy) 채택**.

옵션 B (empty CL fallback) 는 manifest 누락 시 silent 로 진행 → activity_basic 에서 `loadView("ConstraintLayout")` 가 isolated CL parent-first 로 framework 위젯만 검색 → ClassNotFoundException → render fail. 결과는 옵션 A 와 같지만 **에러 메시지가 manifest 누락의 인과를 알리지 못함**. 옵션 A 의 "manifest 가 필요한 시점에만 require + 정확한 fix command 안내" 가 디버깅 비용 압도적 우위.

### Q5 (assertion): `Result.Status == SUCCESS` 추가?

**Stance**: **strongly agree — SUCCESS assertion 반드시 추가**.

**근거**:
1. W3D1 의 `tier3-values` 가 이미 `assertEquals(SUCCESS, status)` 패턴을 채택 (`LayoutlibRendererTier3MinimalTest.kt:106-109`). 그 패턴이 W3D1 impl-pair-review MF1 에서 합의된 canonical (rejected-set 열거 대신 SUCCESS-only).
2. PNG > 1000 bytes 만으로는 `MockView` placeholder 가 가득한 broken render 도 통과시킬 수 있음. layoutlib `BridgeInflater.createViewFromTag` 가 ClassNotFoundException 을 catch 후 `MockView` 로 대체하면 (line 148, /tmp/bridgeinflater.txt) result.status == SUCCESS 인 채로 broken layout 이 렌더됨 — 이 경로를 status 만으로 거를 수는 없지만, SUCCESS assertion 이 status 가 ERROR_INFLATION 으로 떨어지는 진짜 fail 는 명확히 구분.
3. spec §1.3 의 코드 주석 `// 페어 리뷰가 강하게 권장하면 추가` 도 적극 활성화 의도.

**Recommendation**: §4.8 의 주석 처리된 `assertEquals(SUCCESS, ...)` 라인을 활성. tier3-values 의 일관성 유지.

---

## D. Independent issues (직접 검증)

### D1 — R.jar 경로 + 내용 검증 ✓

```
$ ls fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar
-rw-rw-r-- ... 839012 Apr 23 R.jar
$ unzip -l ... | grep "constraintlayout/widget/R\$styleable.class"
   83039  ...  androidx/constraintlayout/widget/R$styleable.class
```
spec §4.5 의 하드코딩 경로 + R$styleable 포함 모두 confirmed.

### D2 — AAR 의 `classes.jar` entry 표준성 ✓

3 개 AAR (constraintlayout-2.1.4, material-1.5.0, appcompat-1.7.1) 모두 root 의 `classes.jar` entry 보유. AGP/Maven 빌드된 모든 AAR 의 표준이며 spec §4.3 의 `AAR_CLASSES_JAR_ENTRY = "classes.jar"` 안전. 참고: 일부 resource-only AAR (e.g., fragment-1.3.6, lifecycle-process-2.6.2) 도 실제로는 `classes.jar` 가 거의 항상 존재 — spec §4.3 의 null 분기는 거의 hit 되지 않으나 방어 정책으로는 합리적.

### D3 — `LayoutlibCallback` 추상 메서드 audit (CRITICAL FINDING)

`/tmp/layoutlibapi/com/android/ide/common/rendering/api/LayoutlibCallback.class` decompile 결과:

| 메서드 | abstract? | default 동작 | spec 변경? |
|--------|-----------|-------------|-----------|
| `loadView(name, sig, args)` | abstract | — | ✓ override |
| `findClass(String)` | concrete | throw CNFE | **❌ spec 누락 — F1 follow-up** |
| `loadClass(name, sig, args)` | concrete | delegate to `loadView` | ✓ default OK (위임됨) |
| `isClassLoaded(String)` | concrete | return false | ✓ default OK |
| `hasAndroidXAppCompat()` | concrete | return false | **❌ spec 누락 — F1 follow-up** |
| `hasLegacyAppCompat()` | concrete | return false | ✓ default OK |
| `shouldUseCustomInflater()` | concrete | return true | ✓ default OK |

**`findClass` 는 spec R2 에 명시된 audit task 의 답이며, hasAndroidXAppCompat 과 함께 F1 follow-up** (위 참조).

### D4 — ConstraintLayout 의 `<init>` 가 R$styleable 에 의존하는가? ✓

`/tmp/cltest/androidx/constraintlayout/widget/ConstraintLayout.class` 디컴파일:
- `<clinit>` 은 trivial (sSharedValues = null).
- 4개 ctor 모두 `init(AttributeSet, int, int)` private method 호출.
- `init(...)` 의 bytecode offset 45, 83, 109, 135, 161 등에서 `getstatic Field androidx/constraintlayout/widget/R$styleable.ConstraintLayout_Layout:[I` 등 다수.

`getstatic` 은 R$styleable 클래스의 `<clinit>` 을 trigger → `R$styleable` 가 classloader 에 없으면 `NoClassDefFoundError` (NOT just zero defaults). 따라서 spec §4.5 R.jar 필수 claim **정확함**. 단순 "zero return" 은 발생하지 않음.

### D5 — `forkEvery(1L)` × URLClassLoader 자원 누수 ✓

`/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/build.gradle.kts:60-65`:
- `setForkEvery(1L)` 이 `integration` 태그 시에만 적용.
- test class 단위 JVM 격리 → 매 fork 종료 시 OS 가 file handle 회수.
- spec §4.7 의 `@Volatile sampleAppClassLoader: SampleAppClassLoader?` 는 per-renderer (=per-JVM-fork via SharedLayoutlibRenderer) cache. 단일 fork 동안 1개 URLClassLoader 만 생성 → JVM 종료 시 GC. 누수 없음.

`maxParallelForks` 는 설정 없음 (defaults to 1) — 동일 sample-app cache dir 동시 쓰기 race 없음. spec §6.1 R6 정확.

### D6 — Transitive class chain (MaterialButton → AppCompatButton → View) ✓

| 클래스 | 위치 |
|-------|------|
| `com.google.android.material.button.MaterialButton` | material-1.12.0.aar/classes.jar |
| `androidx.appcompat.widget.AppCompatButton` | appcompat-1.6.1.aar/classes.jar |
| `androidx.appcompat.widget.AppCompatTextView` | appcompat-1.6.1.aar/classes.jar |
| `android.widget.Button` | layoutlib-14.0.11.jar (isolated CL) |
| `android.view.View` / `ViewGroup` | layoutlib-14.0.11.jar (isolated CL) |
| `androidx.constraintlayout.widget.R$styleable` | sample-app R.jar |
| `kotlin.jvm.internal.*` | system CL (worker dep) + sample-app kotlin-stdlib-1.9.23.jar (URLClassLoader) — duplicate but parent-first wins |

체인 끊김 없음. 단, sample-app 의 `kotlin-stdlib-1.9.23.jar` 와 worker 의 `kotlin-stdlib` 가 system CL 에 양쪽 존재 — parent-first 로 worker 버전이 선택됨. spec §0.3 의 "namespace disjoint" 는 약간 부정확 (kotlin/kotlinx 는 dup) 하나 결과적으로 충돌 없음.

### D7 — App/library resource value loading 의 실 영향 (R3 의 정확도)

spec §1.2 R3 는 "custom attr 가 default 로 fallback → broken positioning, but SUCCESS" 라 주장.

**Independent finding**: 그보다 더 큰 영향이 있다.
- `FrameworkRenderResources.kt:36-41` 가 `RES_AUTO` namespace 에 대해 항상 null 반환 → AppCompat/Material 의 `?attr/textAppearanceHeadlineMedium`, `?attr/colorOnSurface` 등 모두 unresolved.
- `setTheme` 은 `Theme.Material.Light.NoActionBar` (framework) — sample-app 의 `Theme.AxpFixture` (Material3.DayNight 파생) 가 적용되지 않음.
- 결과: TextView 들이 default text appearance, default color 로 렌더. ConstraintLayout positioning 도 default → 모든 child 가 (0,0) 에 적층.

**Trajectory**: T1 gate (SUCCESS + PNG > 1000) 는 통과 가능. Visual fidelity 는 spec 이 인정한 R3 보다 더 심각한 손상. 단, 이는 W3D4+ "App resource value loading" carry 범위와 일치하므로 **별도 blocker 아님** — spec §1.2 R3 의 표현을 "default fallback 으로 위치/스타일 모두 broken — pixel-level 검증은 W3D4+" 로 강화 권장 (minor).

### D8 — `hasAndroidXAppCompat()` 와 isAppCompatTheme 의 상호작용

`/tmp/bridgeinflater.txt:1271` `BridgeContext.isAppCompatTheme()` 가 true 일 때만 `findClass("androidx.appcompat.app.AppCompatViewInflater")` 시도. `BridgeContext.isAppCompatTheme` 의 정의는 `themeName.contains("AppCompat") || themeName.contains("Material")` (보편적 — layoutlib bridge 내부). default theme `Theme.Material.Light.NoActionBar` 가 "Material" 포함 → true.

따라서 **F1 의 `findClass` override + `hasAndroidXAppCompat = true` 조합이 실제로 hit 되는 경로**임을 확인. 추가로, 만약 `findClass` 만 override 하고 `hasAndroidXAppCompat` 는 false 로 두면 `findCustomInflater` 의 `isResourceNamespacingRequired` 분기에서 `null` 반환 (line 1245) → AppCompat 인플레이터 비활성. **양쪽 모두 필요**.

---

## E. Verdict

**GO_WITH_FOLLOWUPS** — 단, **B1 (fixtureRoot 의미 불일치) 는 implementation 시작 전에 해결 필수**.

**Rationale**: spec 의 핵심 아키텍처(URLClassLoader + AAR extract + R.jar 포함 + parent=isolated)는 정확. ChildFirst 불요 분석 옳음 (Kotlin/coroutines duplication 이 있지만 parent-first 로 해결됨). DexClassLoader 분기 거부 옳음. mtime 캐시 타당. lazy build 옳음. T1 gate SUCCESS assertion 권장 활성. 다만 B1 의 path 정의 불일치는 spec 그대로 구현 시 100% test failure 를 야기하는 cause-effect 명확한 결함이므로 spec PR 단계에서 수정 필요. F1 (`findClass` + `hasAndroidXAppCompat` override) 는 fidelity 향상의 primary lever — T1 gate 자체는 통과해도 다음 carry (`App resource value loading`) 진입 전에 잡지 않으면 두 carry 가 교차 오염될 위험. 합치면 ~3-5 LOC + 1 인자 추가의 surgical fix 로 해결 가능.

---

## 참고: 변경 후 권장 spec diff (요약)

```
§4.4 SampleAppClassLoader.build(...)
  - parameter: fixtureRoot → sampleAppModuleRoot (= .../fixture/sample-app)
  - 또는 LayoutlibRenderer 가 별도 인자로 전달

§4.5 ClassLoaderConstants
  - MANIFEST_RELATIVE_PATH / R_JAR_RELATIVE_PATH 의 base 가 module root 임을 주석으로 명시

§4.6 MinimalLayoutlibCallback
  + override fun findClass(name: String): Class<*> = viewClassLoader.loadClass(name)
  + override fun hasAndroidXAppCompat(): Boolean = true

§4.1
  - .map { it.file.absolutePath } → .map { ... }.distinct()

§4.8
  - // assertEquals(SUCCESS, ...) → 활성화 (Q5 stance)
```
