# Deep Improvements — 4-Agent 병렬 심층 분석 통합

> **상태**: APPROVED (2026-04-22)
> **우선순위**: `00`~`04`보다 상위. `06`과 공존(이 문서는 `06`을 보강하되 일부 덮어씀).
> **근거**: Codex xhigh × 2 (R1 L3, R2 L1) + Claude 서브에이전트 × 2 (R3 API, R4 Tests) 병렬 구동, 서로의 결과 미공유. 총 4,500+ 단어 분량 원문 분석.
> **원칙**: 사용자 확정 "커버리지 축소 금지 / 스코프 축소 금지" 준수. 모든 개선은 **품질/깊이 증가 방향**.

---

## 0. 요약 — 가장 중요한 4가지 교정

1. **L1 리소스 파이프라인이 틀렸다**: `res/`만 aapt2 컴파일하면 `Theme.MaterialComponents.*`가 해결 안 됨. **transitive AAR resources + framework data가 matching layoutlib distribution에서 와야 함**. (§2.1)

2. **L3 리소스 접근 방식이 틀렸다**: `createConfigurationContext`는 설정만 바꿀 뿐 리소스 테이블 교체 불가. **`createPackageContext` + `ContextWrapper` override + `pm path` + `aapt2 link -I app.apk`**로 재설계. (§3.1)

3. **RenderSession을 XML 단위로 풀링 불가**: `RenderSessionImpl`이 XML 파서/인플레이터/root를 소유해서 XML 변경 시 **반드시 새 세션**. 풀링은 worker 단위로만. (§2.3)

4. **병렬성은 thread가 아닌 process**: `Bridge`/native/static singleton이 process-global이라 thread 격리로 안 됨. **프로세스 per device**. (§2.7)

이 4가지 중 하나라도 Week 2 이후에 발견했다면 일정이 2주 이상 밀렸을 것이다. 사전 발견이 이 리뷰의 핵심 가치.

---

## 1. 방법론

### 1.1 4기 구성

| # | 담당 | 모델 | 포커스 |
|---|------|------|--------|
| R1 | Codex xhigh (danger-full-access, 80초) | gpt-5-xhigh | L3 classpath + DexClassLoader 8문항 |
| R2 | Codex xhigh (120초) | gpt-5-xhigh | L1 layoutlib internals 8문항 |
| R3 | Claude 서브에이전트 (general-purpose, 107초) | sonnet-4.6 | MCP/HTTP/SSE 프로토콜 8문항 |
| R4 | Claude 서브에이전트 (120초) | sonnet-4.6 | Golden corpus + 테스트 전략 8문항 |

총 32문항에 대한 독립 답변. CLAUDE.md "2개 이상 Codex xhigh + 1:1 Claude 페어" 규칙 충족.

### 1.2 수렴/발산 analysis
양쪽 엔지니어링 페어(R1×R2 Codex)와 양쪽 품질 페어(R3×R4 Claude)를 교차 대조. 5건 수렴, 1건 발산(데이터바인딩 처리 전략), 0건 상호 모순. Codex는 AOSP 소스 레벨(R1·R2 공통 `AppCompatActivity` 사용 지양 권고)에서 깊이 파고들었고 Claude는 구체적 아티팩트(JSON 스키마, YAML, 프로젝트 목록)를 생산.

---

## 2. L1 layoutlib 개선 (R2 Codex → 01-architecture.md §3.2 대체)

### 2.1 리소스 파이프라인 — 핵심 재설계

**원래 계획 (잘못됨)**: `aapt2 compile res/` → `.arsc` → layoutlib 렌더.
**실제 필요**: 사용자 프로젝트의 **merged resources (res/ + 모든 transitive AAR의 res/)**가 필요.

AppTheme이 `parent="Theme.MaterialComponents.DayNight.NoActionBar"`이면, 이 parent style의 정의는 `com.google.android.material:material:*.aar` 안에 있다. framework `android.jar`에도 layoutlib JAR에도 없다.

**해결**: AGP의 `mergeResources` 출력(`app/build/intermediates/incremental/packageDebugResources/merged.dir/`)을 우선 소비. 없으면 Gradle dependency tree + local Maven cache에서 AAR 순회하여 병합.

```kotlin
class MergedResourceResolver {
  fun resolve(projectRoot: Path): Path = when {
    agpMergedResExists(projectRoot) -> agpMergedResDir(projectRoot)  // 가장 빠름
    else -> manualMergeFromAars(projectRoot)  // fallback: ~3-5s 첫 실행
  }

  private fun manualMergeFromAars(root: Path): Path {
    val aars = GradleDependencyTreeReader.read(root)
      .flatMap { it.aarArtifacts() }
    val outDir = root.resolve(".axp-cache/merged-res")
    for (aar in aars) ZipTools.extractPrefix(aar, "res/", outDir)
    ZipTools.copy(root.resolve("app/src/main/res"), outDir, overwrite = true)
    return outDir
  }
}
```

### 2.2 Framework data 소스 (서브틀한 교정)

**원래 계획**: `$ANDROID_HOME/platforms/android-34/data/`에서 framework res/fonts를 참조.
**실제 필요**: layoutlib JAR과 **matching 배포 번들**의 data 필요. android-34 platform data와 layoutlib-android-34 JAR의 enum/attr 테이블이 미묘하게 불일치할 수 있음.

**해결**: 플러그인에 다음을 함께 번들 (`server/libs/layoutlib-dist/android-34/`):
```
data/
├── res/           (framework res, framework-res.apk 압축 해제)
├── fonts/         (§2.4 참조)
├── keyboards/     (*.kcm)
├── icu/           (ICU 데이터)
├── linux/lib64/   (native .so, Linux)
└── build.prop
```
총 배포 크기: ~40-60MB. 용량이 아깝지만 determinism이 cache key(§6)에 전제되어 있어서 필수.

`$ANDROID_HOME/platforms/android-34/data/`는 **fallback 전용**이며 사용 시 그 디렉토리 해시를 cache key에 추가해서 기기 간 동일성 보장.

### 2.3 RenderSession 풀링 재설계

**잘못된 전제**: device/theme 키로 RenderSession 풀 → XML만 바꿔서 재사용.
**진실**: `RenderSessionImpl`은 `BridgeXmlBlockParser`, `BridgeInflater`, `BridgeContext`, `mViewRoot`, `mContentRoot`를 소유. XML이 바뀌면 전부 재생성.

**개선안**:
```kotlin
data class RenderWorkerKey(
  val deviceId: String, val themeId: String, val densityDpi: Int,
  val locale: String, val nightMode: Boolean,
  val fontPackHash: String, val frameworkHash: String
)

data class SessionCacheKey(
  val workerKey: RenderWorkerKey,
  val layoutXmlHash: String,
  val mergedResourcesHash: String,
  val callbackClasspathHash: String  // DexClassLoader set (§3) changes invalidate
)

class LayoutlibSessionPool(private val bridge: Bridge) {
  // XML 변경 빈도가 높으므로 layout-level 캐시는 LruCache로 관리
  private val sessions = LruCache<SessionCacheKey, RenderSession>(16)

  suspend fun render(req: RenderRequest): BufferedImage {
    val key = req.toSessionCacheKey()
    val session = sessions[key] ?: createSession(req).also { sessions.put(key, it) }
    val result = session.render(req.frameTimeNanos)
    check(result.isSuccess) { "render failed: ${result.errorMessage}" }
    return session.image
  }

  fun invalidateAll(projectKey: Any) {
    sessions.evictAll()
    bridge.clearResourceCaches(projectKey)
  }
}
```

핵심: **같은 XML의 재렌더(animation frame, 디바이스 간 회전) 때만 세션 재사용** 가능. 코드 수정에 의한 XML 변경은 새 세션. 따라서 sessions LRU 사이즈는 작게(16).

### 2.4 폰트 팩 — 한국어 필수 (R2 Codex + R4 Claude 양쪽 강조)

**잘못된 전제**: Roboto만 번들하고 나머지는 런타임 로드.
**진실**: `Bridge.init`이 `Typeface.loadPreinstalledSystemFontMap()`으로 폰트 맵을 고정. init 이후 폰트 디렉토리 변경 불안정. 사용자의 primary persona가 **한국어 사용자**이므로 Korean font coverage는 non-negotiable.

**v1 번들 구성**:
| 폰트 | 용도 | 크기 |
|------|------|------|
| Roboto (Regular/Bold/Italic) | Latin 기본 | ~1MB |
| DroidSansMono | 코드/모노스페이스 | ~200KB |
| NotoSansCJK-Regular.ttc | 한/중/일 | ~20MB |
| NotoSansCJK-Bold.ttc | 한/중/일 굵은 | ~22MB |
| NotoColorEmoji | 이모지 | ~10MB |
| NotoSansSymbols/Symbols2 | 특수문자 | ~3MB |

총 ~55MB. 플러그인 다운로드 한 번의 비용. cache key에 `bundledFontPackSha256` 포함.

**worker 라우팅** (R2 Codex 제안):
- 레이아웃의 문자열 유니코드 범위 사전 스캔
- Latin-only → `latin` 워커 (Roboto만 로드, 빠름)
- CJK/Hangul/Kana/Emoji 존재 → `intl` 워커 (전체 로드, 느림)

### 2.5 Custom View Placeholder (L1)

layoutlib이 `<com.acme.FancyChart>`를 만나면 `LayoutlibCallback.loadClassInstance(className, ...)`를 호출. placeholder 반환하면 L1이 중단되지 않음. L3 fallback 필요 여부는 dispatcher가 결정.

```kotlin
class PlaceholderCustomView(
  context: Context, attrs: AttributeSet?, private val className: String
) : View(context, attrs) {
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE; strokeWidth = dp(1f)
    pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f)
  }
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = dp(12f); textAlign = Paint.Align.CENTER
  }

  override fun onMeasure(wSpec: Int, hSpec: Int) {
    setMeasuredDimension(
      resolveSize(dp(160f).toInt(), wSpec),
      resolveSize(dp(64f).toInt(), hSpec)
    )
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawRect(0f, 0f, width - 1f, height - 1f, borderPaint)
    canvas.drawText(className.substringAfterLast('.'), width / 2f, height / 2f, textPaint)
  }

  private fun dp(v: Float) = v * resources.displayMetrics.density
}
```

**`wrap_content`의 기본 사이즈**: 160×64dp. `match_parent`는 parent가 결정. exact dimension은 XML에서 존중.

### 2.6 Data-binding 처리 (L1 preprocessing)

`<TextView android:text="@{viewModel.title}"/>`. L1에서 실제 evaluate 불가.

**R2 Codex 권고 (low complexity)**: XML 사본에 **rewrite pass**:
- `android:text="@{viewModel.title}"` → `android:text="{viewModel.title}"` (literal로 교체)
- `<data>` 섹션의 `default="Foo"` attribute가 있으면 `Foo`로 치환
- non-text typed attribute는 제거 또는 safe default
- 렌더 결과에 "data binding placeholder used" 배지를 **이미지 외부**에 붙임

**R1 Codex 발산 의견**: L3로 에스컬레이트해도 data-binding은 generated code 없이 evaluate 불가. L3 시도는 낭비. 따라서 **L1 placeholder가 data-binding의 최종 경로** (L3 에스컬레이션 트리거에서 제외).

**결정**: Codex 두 의견을 결합. XML rewrite는 L1에서 수행, L3 에스컬레이션 룰에서 data-binding은 제거.

### 2.7 스레드 → 프로세스 격리

`Bridge.init` + `RenderSession` = process-global static state + native libs. 한 JVM에 여러 Bridge 넣으려 하면 native lib 재진입으로 crash. classloader 격리도 native에서 깨짐.

**해결**: **디바이스당 1개 JVM 워커 프로세스**. IPC via JSON over stdin/stdout (또는 Unix socket).

```kotlin
class LayoutlibRenderFarm(deviceIds: List<String>) {
  private val workers = deviceIds.associateWith { LayoutlibWorkerProcess.start(it) }

  suspend fun renderAll(req: RenderRequest): List<RenderResult> = coroutineScope {
    workers.map { (device, worker) ->
      async { worker.render(req.forDevice(device)) }
    }.awaitAll()
  }

  fun shutdown() = workers.values.forEach { it.close() }
}
```

워커 프로세스 당 ~250MB RSS. 3개 디바이스 병렬 = ~750MB. warm 상태에서 render IPC RTT는 ~5ms.

### 2.8 Vector drawable 실패율 완화

R2 Codex 실사용 추정: ~1-2% (단순 VectorDrawable). 실패 집중: `animated-vector`, path-morph animators, gradient with color stops, nested bitmap drawables.

**preprocessing**:
- `<animated-vector>` → 참조된 static vector의 첫 프레임 렌더
- path morph → 첫 `pathData` 값만 사용
- gradient 실패 → 첫 stop color로 flat fill + 경고
- bitmap child 누락 → placeholder + 경고

---

## 3. L3 DexClassLoader 개선 (R1 Codex → 06-revisions §2 대체)

### 3.1 Resource 접근 — createPackageContext 패턴

**잘못된 전제**: `createConfigurationContext`로 사용자 앱 resources 접근.
**진실**: `createConfigurationContext`는 configuration(밤/낮, locale, density)만 override. 다른 APK의 resource table을 바꾸지 않음.

**올바른 시퀀스**:
```
1. adb install -r -d app-debug.apk
2. adb shell pm path com.acme                → /data/app/.../base.apk
3. Intent에 app_package + app_apk_path 전달
4. HarnessActivity에서:
   val appContext = createPackageContext(appPackageName, 0)
   val appResources = appContext.resources
5. AppRenderContext(ContextWrapper) 생성, getResources/getTheme/getClassLoader/getPackageName override
6. themeResId = appResources.getIdentifier("AppTheme", "style", appPackageName)
7. inflater = layoutInflater.cloneInContext(renderContext)
8. inflater.factory2 = AppDexFactory2(appLoader, harnessLoader)
9. inflater.inflate(previewLayoutId, FrameLayout(renderContext), false)
```

```kotlin
class AppRenderContext(
  base: Context,
  private val appPackageName: String,
  private val appResources: Resources,
  private val appLoader: ClassLoader,
  themeResId: Int
) : ContextWrapper(base) {
  private val appTheme = appResources.newTheme().apply { applyStyle(themeResId, true) }
  override fun getResources() = appResources
  override fun getAssets() = appResources.assets
  override fun getTheme() = appTheme
  override fun getClassLoader() = appLoader
  override fun getPackageName() = appPackageName
}
```

### 3.2 Factory2 훅 — 모든 요소에 적용됨

**교정**: `LayoutInflater.Factory2.onCreateView`는 **every element**에 대해 호출됨 (root만이 아님). `<view class="...">`는 `class` 속성 값으로 정규화. `<include>`는 별도 경로(`parseInclude()`).

```kotlin
class AppDexFactory2(
  private val appLoader: ClassLoader,
  private val harnessLoader: ClassLoader
) : LayoutInflater.Factory2 {
  private val ctorCache = HashMap<String, Constructor<out View>>()

  override fun onCreateView(parent: View?, name: String, ctx: Context, attrs: AttributeSet) =
    createView(name, ctx, attrs)
  override fun onCreateView(name: String, ctx: Context, attrs: AttributeSet) =
    createView(name, ctx, attrs)

  private fun createView(raw: String, ctx: Context, attrs: AttributeSet): View? {
    val name = if (raw == "view") attrs.getAttributeValue(null, "class") ?: return null else raw

    // FQCN → 그대로. Short → 프레임워크/AppCompat 후보
    val candidates = if (name.contains('.')) listOf(name)
      else listOf(
        "androidx.appcompat.widget.AppCompat$name",
        "android.widget.$name", "android.view.$name", "android.webkit.$name"
      )

    for (className in candidates) {
      // android.* 는 harness, 그 외는 app 먼저
      val loaders = if (className.startsWith("android.")) listOf(harnessLoader)
                    else listOf(appLoader, harnessLoader)
      for (loader in loaders) {
        try {
          val ctor = ctorCache.getOrPut("${loader.hashCode()}:$className") {
            loader.loadClass(className).asSubclass(View::class.java)
              .getConstructor(Context::class.java, AttributeSet::class.java)
          }
          return ctor.newInstance(ctx, attrs)
        } catch (_: ClassNotFoundException) { /* try next */ }
      }
    }
    return null
  }
}
```

### 3.3 Child-first ClassLoader (androidx 충돌 해결)

**문제**: harness APK의 androidx.appcompat 1.7.0 + 사용자 앱의 1.6.1. parent-first delegation이면 harness 버전 로드 → 앱이 기대한 API 없어서 `NoSuchMethodError`.

**해결**: app/사용자 패키지는 **child-first**, framework/harness는 parent-first.

```kotlin
class ChildFirstDexClassLoader(
  dexPath: String, optDir: String, libPath: String?, parent: ClassLoader,
  private val harnessPrefix: String  // "com.axp.harness."
) : DexClassLoader(dexPath, optDir, libPath, parent) {
  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    synchronized(getClassLoadingLock(name)) {
      findLoadedClass(name)?.let { return it }

      val parentFirst = name.startsWith("java.") ||
        name.startsWith("javax.") || name.startsWith("android.") ||
        name.startsWith("dalvik.") || name.startsWith(harnessPrefix)

      val clazz = if (parentFirst) parent.loadClass(name)
                  else try { findClass(name) } catch (_: ClassNotFoundException) { parent.loadClass(name) }
      if (resolve) resolveClass(clazz)
      return clazz
    }
  }
}
```

**부가 권고**: harness shell을 `AppCompatActivity` 대신 **plain `Activity`**로. AppCompatActivity를 상속하면 instance의 ClassLoader가 harness의 AppCompat으로 고정되어 사용자 앱 커스텀 View를 캐스트할 때 충돌.

### 3.4 DEX Preflight — obfuscation/staleness 사전 감지

**R1 Codex 접근**: APK 파일의 `classes*.dex`를 파싱해 XML에 나온 custom class descriptor가 모두 존재하는지 확인. 없는 게 있으면 obfuscation 또는 stale APK.

```kotlin
object DexPreflight {
  fun check(apk: Path, xml: Path, appPkg: String): Result<Unit, AxpError> {
    val xmlClasses = collectViewClassNames(xml).filter { it.startsWith(appPkg) }
    val dexClasses = DexClassIndex.fromApk(apk)  // dex file header + string table 스캔

    val missing = xmlClasses.filterNot { it in dexClasses }
    if (missing.isNotEmpty()) return Err(AxpError("AXP-L3-008",
      "APK obfuscated or stale. Missing classes: ${missing.take(3).joinToString()}",
      remediation = "./gradlew :app:assembleDebug"))

    // 보조 heuristic: 단일 문자 클래스 비율
    val appClasses = dexClasses.filter { it.startsWith(appPkg) }
    val shortNames = appClasses.count { it.substringAfterLast('.').length <= 2 }
    if (appClasses.size > 50 && shortNames.toDouble() / appClasses.size > 0.35) {
      return Err(AxpError("AXP-L3-009", "APK looks obfuscated (short name ratio > 35%)",
        remediation = "Disable R8/ProGuard for debug builds: buildTypes { debug { isMinifyEnabled = false } }"))
    }

    // ApplicationInfo.FLAG_DEBUGGABLE 확인
    val manifest = ManifestReader.read(apk)
    if (!manifest.debuggable) return Err(AxpError("AXP-L3-010",
      "Release APK not supported in v1. Use debug variant.", retriable = false))

    return Ok(Unit)
  }
}
```

DEX 클래스 인덱스는 dexlib2 라이브러리로 ~50ms. 첫 L3 호출에 부담 없음.

### 3.5 XML → 바이너리 컴파일 (host-side)

**잘못된 전제**: harness APK에 XML 텍스트를 push하고 `LayoutInflater`로 바로 inflate.
**진실**: stock Android `LayoutInflater`는 **compiled XML** (binary format)만 받음. source XML은 `XmlPullParser`로 직접 파싱해야 하지만 그러면 resource ID 매핑이 깨짐.

**올바른 경로** (host-side compile):
```bash
# 1. 대상 XML을 preview 프로젝트 res/로 배치 (symlink OK)
mkdir -p /tmp/axp-preview/res/layout
cp target.xml /tmp/axp-preview/res/layout/target.xml

# 2. aapt2 compile
aapt2 compile --dir /tmp/axp-preview/res -o /tmp/axp-preview/compiled.zip

# 3. aapt2 link, 앱 APK를 -I로 추가 (중요: 앱 resource ID 공간 공유)
aapt2 link \
  -I "$ANDROID_HOME/platforms/android-34/android.jar" \
  -I app-debug.apk \
  --manifest /tmp/axp-preview/AndroidManifest.xml \
  --package-id 0x80 \
  --auto-add-overlay \
  -o /tmp/axp-preview/preview-res.apk \
  /tmp/axp-preview/compiled.zip

# 4. 기기로 push
adb push /tmp/axp-preview/preview-res.apk /data/local/tmp/

# 5. 기기에서 런타임에 resource APK를 추가 Resources로 로드
```

Runtime 측:
```kotlin
val previewAssets = AssetManager::class.java.newInstance().apply {
  val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
  addAssetPath.invoke(this, appApk.toString())
  addAssetPath.invoke(this, "/data/local/tmp/preview-res.apk")
}
val previewResources = Resources(previewAssets, appResources.displayMetrics, appResources.configuration)
val layoutId = previewResources.getIdentifier("target", "layout", "preview")
val parser = previewResources.getLayout(layoutId)
val view = inflater.inflate(parser, FrameLayout(renderContext), false)
```

**package-id 0x80**: 앱(0x7f), framework(0x01)와 충돌 방지. linker가 preview 리소스에 별도 남은 space 할당.

### 3.6 메모리 관리 — MemoryInfo 기반 adaptive cache

**잘못된 전제**: 5슬롯 LruCache 고정.
**진실**: AVD RAM은 가변(보통 2048MB). `ActivityManager.getMemoryInfo`로 실시간 판단.

```kotlin
fun computeCacheSize(am: ActivityManager): Int {
  val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
  return when {
    am.isLowRamDevice || info.lowMemory -> 0
    info.availMem < 384L * 1024 * 1024 -> 1
    am.memoryClass < 256 -> 1
    am.memoryClass < 384 -> 3
    else -> 5
  }
}

// ComponentCallbacks2에서 onTrimMemory 훅
override fun onTrimMemory(level: Int) {
  when {
    level >= TRIM_MEMORY_RUNNING_CRITICAL -> dexCache.evictAll()
    level >= TRIM_MEMORY_RUNNING_LOW -> dexCache.trimToSize(1)
    level >= TRIM_MEMORY_RUNNING_MODERATE -> dexCache.trimToSize(3)
  }
}
```

**"캐시 안 하는 게 나은" 신호**: low-RAM device, 최근 OOM 발생, native lib 로드됨, 단발 render.

### 3.7 v1.1 — 난독화 APK 지원 (mapping.txt)

`app/build/outputs/mapping/release/mapping.txt` 파일 존재 시:
- XML의 `<com.acme.FancyChart>` → obfuscated `<a.b.c>`
- XML을 **rewrite한 복사본**으로 aapt2 compile
- 렌더 실패 시 obfuscated stack을 **reverse-resolve**해서 사용자에게 원래 이름으로 에러 표시

```kotlin
fun rewriteClassNames(xml: Document, m: Mapping) {
  xml.walkElements { el ->
    m.originalToObfuscated[el.tagName]?.let { el.tagName = it }
    if (el.tagName == "view") m.originalToObfuscated[el.getAttribute("class")]
      ?.let { el.setAttribute("class", it) }
    m.originalToObfuscated[el.getAttribute("android:name")]
      ?.let { el.setAttribute("android:name", it) }
  }
}
```

v1.1 Week 1-2 스코프(v1 출시 후 4주차 이내).

---

## 4. API / Protocol 개선 (R3 Claude → 01-architecture.md §5 대체)

### 4.1 MCP Envelope 표준

모든 MCP 응답을 envelope으로 감쌈:

```kotlin
data class McpEnvelope<T>(
  val schemaVersion: String,       // "1.0" — protocol contract
  val serverVersion: String,       // "1.0.0" — axp-server build
  val capabilities: Set<String>,   // ["render.l1","render.l3","render.batch"]
  val data: T?,
  val error: ErrorEnvelope? = null
)

data class ErrorEnvelope(
  val code: String,           // "AXP-L3-001"
  val category: String,
  val message: String,
  val detail: String?,
  val remediation: String?,
  val remediationUrl: String?,
  val retriable: Boolean
)
```

**규칙**:
- `schemaVersion` MAJOR 불일치 시 클라이언트는 reject. MINOR는 additive-only, 호환.
- tool 이름은 안정적(`render_layout`). 버전 suffix 금지.
- 새 기능은 새 tool. `capabilities` set으로 기능 감지.
- deprecation은 2-minor window, `AXP-DEPRECATED` 에러 반환하면서 remediation 제공.

### 4.2 RenderRequest / RenderResponse 스키마

```json
// request
{
  "$schema": "axp/render-request/1.0",
  "xml_path": "app/src/main/res/layout/activity_main.xml",
  "device": "phone_normal",
  "theme": "light",
  "night_mode": false,
  "font_scale": 1.0,
  "locale": "en-US",
  "force_layer": null,           // "L1" | "L3" | null (auto)
  "force_rerender": false,
  "request_id": "req_01HXYZ...",
  "client_epoch": 42,
  "metadata": { "_reserved": "opaque bag" }
}

// response (success)
{
  "schema_version": "1.0",
  "server_version": "1.0.0",
  "capabilities": ["render.l1","render.l3","aapt2.cache.warm"],
  "data": {
    "status": "success",
    "render_id": "af42b8c1",       // monotonic nano-derived base36
    "layer": "layoutlib",
    "fallback_reason": null,
    "elapsed_ms": 830,
    "png_url": "/preview?layout=...&v=af42b8c1",
    "cache_hit": false,
    "aapt2_warm": true,
    "avd_state": "ready",
    "request_id": "req_01HXYZ...",
    "server_epoch": 42
  },
  "error": null
}
```

**디바이스 enum**: `phone_small | phone_normal | phone_large | tablet_7 | tablet_10 | foldable_inner | foldable_outer | tv_1080p | wearable_round`

### 4.3 SSE Event Taxonomy (10개)

공통 헤더: `id: <monotonic u64>`, `retry: 2000`.

| # | event | 시점 | 페이로드 |
|---|-------|------|---------|
| 1 | `server_ready` | 서버 부팅 | `{aapt2_warm, avd_state, bundled_api_level, projects:[]}` |
| 2 | `render_queued` | debounce 통과 | `{render_id, layout, device, theme, debounce_ms_remaining}` |
| 3 | `render_started` | L1 시도 시작 | `{render_id, layer_attempt:"L1"}` |
| 4 | `render_progress` | L1→L3 escalation or AVD boot % | `{render_id, stage, pct?}` |
| 5 | `render_complete` | 성공 | `{render_id, layer, elapsed_ms, png_url, cache_hit, fallback_reason?}` |
| 6 | `render_failed` | Unrenderable | `{render_id, error: ErrorEnvelope}` |
| 7 | `render_cancelled` | 상위 렌더가 이 렌더를 밀어냄 | `{render_id, superseded_by?, reason}` |
| 8 | `project_changed` | 프로젝트 루트 변경 | `{old_root?, new_root, layouts_count}` |
| 9 | `resource_invalidated` | values/drawable 변경 | `{scope, affected_layouts?, render_ids_cancelled}` |
| 10 | `server_shutdown` | 정상 종료 | `{reason, grace_ms}` |

**순서 보장**: `render_id`별로 queued → started → progress* → (complete | failed | cancelled). 터미널 이벤트는 정확히 1회.

**재연결**: 256-event ring buffer, `Last-Event-ID` 헤더로 replay. `server_epoch` 불일치 → 리싱크.

### 4.4 취소 + 스테일 방지

```kotlin
// HTTP: POST /api/cancel { "render_id": "af42b8c1" } or { "layout": "foo.xml" }

class RenderDispatcher {
  private val inFlight = ConcurrentHashMap<RenderKey, Deferred<RenderResult>>()

  fun cancel(key: RenderKey, reason: String): Boolean {
    val d = inFlight[key] ?: return false
    d.cancel(CancellationException(reason))
    sseBroadcaster.emit(RenderCancelled(key.renderId, reason))
    return true
  }

  suspend fun render(req: RenderRequest): RenderResult {
    val key = req.toRenderKey()
    cache.get(key)?.let { return it }

    // single-flight
    return inFlight.computeIfAbsent(key) {
      coroutineScope.async {
        try { doRender(req, key).also { if (isActive) cache.put(key, it) } }
        finally { inFlight.remove(key) }
      }
    }.await()
  }
}
```

**스테일 방지 3층**:
1. monotonic `render_id` (nanoTime base36)
2. URL versioning: `/preview?...&v=<render_id>` — server returns 410 Gone if superseded
3. browser compare: `evt.render_id !== expected` → drop

**publish gate**: cancelled render의 PNG 바이트는 `renders/` 디렉토리에 쓰이기 전에 drop. 파일시스템에 stale PNG가 존재할 수 없음.

### 4.5 Browser ↔ Server 상태머신

```
Browser states:
  DISCONNECTED ──SSE open──▶ HANDSHAKING
  HANDSHAKING  ──server_ready──▶ LIVE
  LIVE         ──socket drop──▶ RECONNECTING (1s, 2s, 4s, 8s, ..., 30s exp-backoff)
  RECONNECTING ──server_ready(same epoch)──▶ LIVE (replay from Last-Event-ID)
  RECONNECTING ──server_ready(new epoch)──▶ RESYNCING (purge local, fetch /api/layouts)
  RESYNCING    ──done──▶ LIVE
  *            ──server_shutdown──▶ STALE
```

User actions (device 드롭다운, 테마 토글) while DISCONNECTED → `pendingUserActions[]` 큐, re-LIVE 시 last-write-wins per (layout, field)로 재생.

Heartbeat: 15s마다 `event: ping`, 5s마다 `:comment` keepalive (NAT/미들웨어 idle-timeout 방지).

### 4.6 Port 조정 + 크래시 복구

per-project flock + JSON advertisement:

```
<projectRoot>/.axp-cache/
├── server.lock      ← flock(2)-held while alive, contains PID
└── server.json      ← {port, pid, epoch, started_at, server_version}
```

**startup algorithm**:
1. `flock LOCK_EX|LOCK_NB` on `server.lock`
2. 획득 → 포트 할당 (이전 값 우선, 없으면 7321-7399 스캔), `server.json` 갱신
3. 실패 → 기존 `server.json` 읽고 `/api/ping` 확인
   - 살아있음 → 재사용 (exit 0, "reusing existing axp-server at :PORT")
   - 죽음 → flock 재시도, 두 번째 실패 시 hard error

**zombie detection on startup** (포트 bind 직전):
- `server.json.pid` → `kill -0 pid` 실패 → zombie cleanup
- alive지만 `/api/ping` 3회 2s timeout → SIGTERM(3s) → SIGKILL
- `adb emu kill` for `axp-*` AVD (parent PID 체인 검증)
- `SO_REUSEADDR`로 TIME_WAIT 우회
- `server.lock` 삭제, `server.json` truncate
- 새 서버 시작 + `epoch` 증가

**브라우저 대응**: SSE auto-reconnect → 새 `server_epoch` 수신 → RESYNCING으로 투명 전환.

### 4.7 Security

- **CSRF 토큰**: `/api/cancel`, `/api/select` 변조 방지. `server_ready`에서 double-submit 토큰 발급. localhost-only라도 악성 사이트 JS가 localhost 찌를 수 있음.
- **AXP_TOKEN**: MCP init에서 echo, `/api/*`에 필수 헤더. 다른 프로세스가 서버 찌르는 것 방지.
- **CORS**: `Origin: http://localhost:*` + `null`(file://)만 허용.
- **Origin header**: SSE 연결 시 `X-AXP-Project-Root: <hash>` 검증 → 다른 프로젝트 서버에 잘못 붙은 탭은 409 반환 → "project changed — reopen viewer" 배너.

### 4.8 META — 추가 개선

- **WebP 지원**: `Accept: image/webp` → 40% payload 감소
- **ETag**: `ETag: "<render_id>"` + conditional GET → 304
- **HTTP/2 keep-alive**: SSE + `/preview` 다수 요청 동거
- **Idempotency-Key** header: `/api/cancel`, `/api/select`의 reconnect 후 재전송 안전
- **/api/logs** (SSE): 개발자 모드 전용, 토큰 게이트, 이벤트 스트림과 분리
- **Protocol negotiation in MCP init**: 클라이언트가 `supported_schema_versions: ["1.0","1.1"]` 전송, 서버가 최고 공통 선택 → 라이브 탭 깨지지 않고 미드플라이트 업그레이드

---

## 5. 테스트 전략 개선 (R4 Claude → 04-open-questions §3 대체)

### 5.1 Golden Corpus — 13개 프로젝트

| 프로젝트 | 카테고리 | Exercises |
|----------|----------|-----------|
| Signal-Android | M3, ConstraintLayout-heavy | 테마 오버레이, MaterialToolbar, custom conversation views |
| Bitwarden/android | M3, Compose+XML 하이브리드 | ComposeView XML host, M3 tokens |
| NewPipe | M2 legacy, LinearLayout | Fragment list/detail, legacy styling, ExoPlayer SurfaceView |
| AntennaPod | M3 마이그레이션 중, 혼합 | 혼재 코드베이스, MediaSessionCompat views |
| Element-Android | M2, heavy data-binding | 복잡한 binding 표현식, timeline RecyclerView items |
| Simple-Mobile-Tools | M2, RelativeLayout legacy | styles.xml 상속, `?attr/` refs |
| Tachiyomi/Mihon | M3, ConstraintLayout | MotionLayout, CoordinatorLayout+AppBar |
| DuckDuckGo/Android | M3, multi-module(50+) | 멀티모듈 리소스 해결, R-class class loading |
| WireGuard-Android | minimal M2 | 최소 표면, SwitchPreferenceCompat 엣지 |
| Kiwix-Android | M2, multi-locale 포함 ko-KR | Locale/font 메트릭 (CJK), WebView placeholder |
| Toss `slash` demos / 한국 OSS | 한국어 앱, M3 | Noto Sans KR, 한국어 텍스트 메트릭 |
| osmdroid sample | heavy custom View | DexClassLoader 타겟, Canvas-draw View |
| MPAndroidChart sample | charts custom Views | L1 실패→L3 경로, attrs.xml styleables |

**층화 비율**: 7 M3 / 6 M2 (54/46). ConstraintLayout-heavy 6, legacy Linear/Relative 5, hybrid 2. Data-binding 5. Multi-module 6. Custom-view-heavy 3. Korean 2.

### 5.2 커버리지 Rubric (5 tier)

| Tier | 정의 | 자동 판정 |
|------|------|----------|
| **T1 PIXEL_MATCH** | DSSIM ≤ 0.003 vs golden | 자동 |
| **T2 STRUCTURAL_MATCH** | DSSIM ≤ 0.02, ViewInfo tree IoU ≥ 0.95 | 자동 |
| **T3 RECOGNIZABLE** | DSSIM ≤ 0.08, 인간 평가 "faithful" | 2 grader κ≥0.7 |
| **T4 DEGRADED** | PNG 생성되나 element missing / fallback 배지 | 자동 (Unrenderable partial) |
| **T5 UNRENDERABLE** | Typed `UnrenderableReason` | 자동 |
| **T6 ERROR** | crash/timeout/malformed | 자동 (릴리스 0% 필수) |

**85% 정의**: `T1+T2+T3 ≥ 85%` AND `T6 = 0%` AND `T4 ≤ 10%`.

### 5.3 픽셀 비교

- **Tool**: `odiff` (Rust/OCaml, 10× pixelmatch). fallback: `pixelmatch`. Paparazzi의 differ는 자체 포맷 결합이라 제외.
- **Per-pixel tolerance**: YIQ `--threshold=0.08`, `--antialiasing=true`
- **Aggregate**: DSSIM ≤ 0.02 (SSIM ≥ 0.985)
- **Diff 픽셀 비율**: p95 ≤ 0.5%, p99 ≤ 1.5%
- **Shadow mask**: `ViewInfo.elevation>0` 영역 4px dilate, 그 안에서 tolerance 3× (0.20) — shadow drift는 shadow가 있을 때만 허용
- **Font pin**: Noto Sans + Noto Sans KR + Roboto 번들 해시가 cache key에 포함 → 폰트 AA drift 원천 차단

### 5.4 CI Pipeline

```yaml
name: corpus
on:
  pull_request:
  schedule: [{cron: "0 9 * * *"}]
  workflow_dispatch:
jobs:
  l1-corpus:                                # PR마다 L1+L2만 (~90s)
    runs-on: ubuntu-22.04
    strategy:
      fail-fast: false
      matrix: {sdk: [34, 35, 36]}
    container: ghcr.io/axp/ci-base:sdk${{matrix.sdk}}-20260420
    steps:
      - uses: actions/checkout@v4
      - name: Restore goldens
        uses: actions/cache@v4
        with: {path: .axp-cache/goldens, key: goldens-${{hashFiles('corpus/**/*.xml')}}}
      - run: ./gradlew :server:corpusRender --sdk=${{matrix.sdk}} --layer=L1,L2
      - run: odiff-runner --dir=out/ --golden=goldens/ --threshold=0.08 --aa --dssim=0.02
      - if: failure()
        uses: actions/upload-artifact@v4
        with: {name: diff-sdk${{matrix.sdk}}, path: out/diffs/}
      - if: failure()
        run: ./scripts/post-diff-comment.sh

  l3-corpus:                                # nightly + 레이블 시만 L3 풀런
    if: github.event_name != 'pull_request' || contains(github.event.pull_request.labels.*.name, 'run-full-corpus')
    runs-on: ubuntu-22.04-large            # KVM 필요
    needs: l1-corpus
    strategy: {matrix: {sdk: [34, 35, 36]}}
    steps:
      - uses: actions/checkout@v4
      - uses: reactivecircus/android-emulator-runner@v2
        with: {api-level: "${{matrix.sdk}}", arch: x86_64, profile: pixel_6,
               emulator-boot-timeout: 600, force-avd-creation: false,
               disable-animations: true,
               script: "./gradlew :server:corpusRender --layer=L3"}
      - run: odiff-runner --dir=out/ --golden=goldens/ --shadow-mask --threshold=0.08
```

**Docker 이미지 사전 구성**: `ghcr.io/axp/ci-base:<sdk>-<date>`에 layoutlib JAR + aapt2 + AVD 이미지 + harness APK 포함. ~5GB, `actions/cache` of `/var/lib/docker`로 주 1회 pull.

### 5.5 Flake 관리

**Determinism 컨트롤 (입력)**:
1. Clock freeze: `System.setProperty("axp.clock", "2026-01-01T12:00:00Z")` → layoutlib BridgeContext에 clock shim 주입
2. RNG freeze: reflection으로 layoutlib `Random` seed=42
3. Animation off: `SessionParams.setFlag(FLAG_KEY_DISABLE_BITMAP_CACHING, true)`
4. Locale lock: en-US 기본, ko-KR canary 병행
5. Font pin (§5.3)
6. data-binding: `StaticBindingResolver`로 `tools:*` attrs를 deterministic placeholder로 치환

**Flake detection**: 각 레이아웃 3× 렌더. variance < 0.001 = deterministic, > 0.005 = 자동 flake-quarantine 라벨 → 별도 메트릭. **no-retry policy** — flake는 quarantine 또는 fix. retry로 regression 가림 금지.

**Auto-triage**: 실패 렌더의 DSSIM을 `goldens/history.jsonl`의 30일 히스토리 대비 3σ 벗어날 때만 flag.

### 5.6 Dev workflow

```bash
./gradlew corpusRender --only=signal/activity_main.xml  # 단일
./gradlew corpusRender --project=newpipe               # 한 프로젝트
./gradlew corpusRender --changed                       # git diff 기반 ≤10개 샘플, ~45s warm
./gradlew corpusGolden --accept=signal/activity_main.xml --reason="M3 update"  # golden 승격
./gradlew corpusBisect --layout=X --since=v1.0.0       # git-bisect automation
```

**corpus/ownership.yaml** (신규): Kotlin 모듈 → 대표 레이아웃 매핑. `LayoutlibRenderer` 변경 → 5 L1 sample, `AaptCompiler` 변경 → 3 resource-heavy sample. `--changed` heuristic이 이 매핑으로 ≤10 레이아웃 선택.

### 5.7 Unit test 구조

| 모듈 | 스타일 | 카운트 | Cov floor |
|------|--------|--------|-----------|
| XmlParser → Model | pure JUnit5 + fixtures | 40 | 95% line / 90% branch |
| AaptCompiler | JUnit5 + @TempDir + real aapt2 | 15 | 85% line |
| ResourceTableLoader | JUnit5 + pre-built .arsc bytes | 12 | 90% |
| LayoutlibRenderer | JUnit5 + layoutlib JAR + 5 fixture | 10 | 80% (JVM init 제외) |
| EmulatorRenderer | @Tag("integration") only | 6 | n/a |
| RenderDispatcher | Mockk'd L1/L3, scenario matrix | 25 | 95% |
| RenderCache | @TempDir + eviction | 15 | 95% |
| LayoutFileWatcher | @TempDir + fake clock | 8 | 85% |
| HttpServer (Ktor) | `testApplication {}` | 12 | 85% |
| SseBroadcaster | coroutine channel 3-client sim | 8 | 90% |
| McpServer | stdio pipe harness + JSON-RPC | 10 | 90% |
| UnrenderableReason catalog | snapshot test (enum → docs) | 1 | 100% |
| Viewer app.js | Vitest + jsdom + EventSource polyfill | 12 | 80% |

**Aggregate floor**: 85% line / 75% branch (non-integration). Kover + JaCoCo.

### 5.8 Week 6 Acceptance Protocol

**Pre-conditions (locked)**: ubuntu-22.04-large, Pixel_6_API_34 snapshot `avd-snapshot-v1`, SDK build-tools `34.0.0`, layoutlib `layoutlib-api-34.0.0-2026-Q1`, aapt2 `34.0.0`, JVM 17.0.11, fonts-pack-v3.

**Input**: 13 프로젝트를 `corpus/pins.yaml`의 commit SHA로 pin. 각 ≤8 layouts lexical sort → ~90-100 layouts. phone_normal / light / en-US + 2 ko-KR canary.

**Steps (D26-27)**:
1. `./scripts/clone-corpus.sh` — pinned SHA로 checkout + `:app:assembleDebug`
2. `./gradlew corpusRender --layer=auto --all --repeats=3`
3. Auto-grader → `report.json`
4. 인간 평가 pass (T3/T4, 2 grader κ ≥ 0.7)
5. `./scripts/aggregate.py` → `acceptance.md`

**Gates**:
- `T1+T2+T3 ≥ 85%` AND `T6 = 0%` AND `flaky ≤ 2%` AND 모든 카테고리 ≥ 70% → **v1.0 release**
- `80% ≤ T1+T2+T3 < 85%` OR 카테고리 60-70% → **v1.0-rc**, 2주 public beta, 측정치로 주장 수정
- `< 80%` OR `T6 > 0%` → **delay** + Week 4 escape-hatch 재발동

**Ongoing telemetry**: `report.json` → `goldens/history.jsonl` → `docs/CORPUS_DASHBOARD.md`(nightly regen). 1pp 이상 회귀 → GitHub issue 자동 오픈.

---

## 6. 교차 결과로 등장한 신규 리스크

**R13 (High)** — Golden drift from font-pack or layoutlib upgrade misattributed to code regression
- 완화: PR이 `LayoutlibRenderer` / 번들 font pack 수정 시 wholesale re-baseline 요구 + `REBASELINE_REASON` commit trailer + reviewer가 ≥5 sample diff 검수

**R14 (High)** — AGP merged resources 부재 시 fallback 실패
- 완화: Week 2 Day 8에 `MergedResourceResolver`의 manual AAR 병합 경로 먼저 검증 (§2.1)

**R15 (Medium)** — harness APK의 androidx와 사용자 앱 androidx 메이저 버전 gap
- 완화: ChildFirstDexClassLoader가 커버하지만, 테스트 코퍼스의 13개 앱 중 androidx 버전 분포 측정 + compat matrix 문서화

**R16 (Medium)** — Korean 사용자에게 font-coverage 회귀
- 완화: ko-KR canary 2개가 nightly 필수. 3σ 벗어나면 즉시 alert (§5.5)

---

## 7. 06-revisions-and-decisions.md와의 덧셈 관계

본 문서는 `06`을 **교체하지 않고 확장**한다. 06이 "무엇을 할지(what)" 결정을 기록하고 07이 "어떻게 할지(how)" 깊이를 채운다. 두 문서 모두 canonical이되, 구현 세부에서 충돌 발생 시 **07이 우선**.

### 7.1 06의 어느 부분을 07이 덮어쓰는가

| 06 섹션 | 07의 개정 |
|---------|-----------|
| §2.1 "L3 렌더 플로우" | §3.1-3.2 (createPackageContext 패턴 + Factory2 전 요소 적용) |
| §2.2 "app APK 감지 룰" | §3.4 (DEX preflight가 먼저) |
| §2.3 "DexClassLoader 위험 완화" | §3.3 (child-first ClassLoader) |
| §3.1 "Canonical Cache Key" | **유지** — 07은 추가 정보 없음 |
| §4 "Unrenderable Enum" | 코드 추가: AXP-L3-008/009/010 (§3.4) |
| §5 "Concurrency 모델" | §4.4 (publish gate 추가) |

### 7.2 새로 추가되는 문서

- `docs/CORPUS_FORMAT.md` — golden PNG 포맷 스펙 + re-baseline 정책
- `docs/CORPUS_DASHBOARD.md` — nightly trend (자동 생성)
- `corpus/pins.yaml` — 13 프로젝트 commit SHA pin
- `corpus/ownership.yaml` — Kotlin 모듈 ↔ 대표 레이아웃 매핑

---

## 8. Week별 적용 체크포인트 (03-roadmap / 06 §6 보강)

### Week 1
- layoutlib-dist 번들 구조 결정 + 프로젝트 스캐폴딩 (§2.2)
- MCP envelope + ErrorEnvelope 기본 셋업 (§4.1)

### Week 2
- `MergedResourceResolver` AGP 경로 + manual AAR merge (§2.1) — **가장 큰 교정**
- Font pack v3 번들 (§2.4)
- aapt2 cache + host-side compile pipeline

### Week 3
- L3 worker process per device (§2.7)
- `createPackageContext` + `AppRenderContext` + `AppDexFactory2` (§3.1-3.2)
- `ChildFirstDexClassLoader` (§3.3)
- `DexPreflight` (§3.4)
- `aapt2 link -I app-debug.apk` preview resource APK 빌드 (§3.5)

### Week 4
- `LruCache` 메모리 적응 (§3.6)
- SSE 10-event taxonomy + state machine (§4.3, §4.5)
- flock 포트 코디네이션 (§4.6)
- CSRF + AXP_TOKEN (§4.7)

### Week 5
- `UnrenderableReason` 완전 카탈로그 + 에러 카드 UI
- 5-tier rubric 자동 평가기 (§5.2)
- odiff + shadow mask (§5.3)

### Week 6
- CI pipeline YAML 활성화 + `ghcr.io/axp/ci-base` 이미지 빌드 (§5.4)
- 13 프로젝트 acceptance run (§5.8)
- `docs/CORPUS_DASHBOARD.md` 첫 생성

---

## 9. 남은 미해결 이슈 (Phase 8 후속)

1. **v1.1 mapping.txt 지원의 정확한 스펙** — §3.7 sketch만 있음, 구현 상세는 v1 출시 후 2주차에 작성
2. **worker process IPC 프로토콜** — JSON over stdin/stdout vs Unix socket vs gRPC. 성능 벤치 필요
3. **AVD system image 자동 다운로드 vs 안내** — §04-open-questions Q3 미확정
4. **Windows 지원 roadmap** — v2에서 WHPX + ADB 경로 검증 필요

---

## 10. 변경 로그

| 일자 | 변경 | 에이전트 |
|------|------|---------|
| 2026-04-22 | 초안 — 4기 병렬 심층 분석 통합 | Codex xhigh × 2 + Claude subagent × 2 |
