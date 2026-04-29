# 개정 및 결정사항 (Phase 6 리뷰 반영)

> **상태**: APPROVED (2026-04-22) → 일부 섹션은 07-deep-improvements에서 개정됨
> **우선순위**: `00`~`04`보다 우선하지만, **`07`보다 아래**. 구현 세부 충돌 시 `07` 우선.
> **목적**: Phase 6 페어 리뷰(`05-pair-review-final.md`) 후 확정된 L3 방향(옵션 α) + 7개 비논쟁 픽스를 한 곳에 정리.
>
> **07에서 개정된 섹션 안내**:
> - §2.1 L3 렌더 플로우 → 07 §3.1-3.2 (createPackageContext 패턴)
> - §2.2 app APK 감지 룰 → 07 §3.4 (DEX preflight 우선)
> - §2.3 DexClassLoader 위험 완화 → 07 §3.3 (ChildFirstDexClassLoader)
> - §4 Unrenderable Enum → 07 §3.4에 AXP-L3-008/009/010 추가
> - §5 Concurrency 모델 → 07 §4.4 (publish gate 추가)

---

## 1. 결정 기록

### 1.1 L3 Classpath 이슈 — 옵션 α 선택
- **결정일**: 2026-04-22
- **선택**: **옵션 α (v1에 full classpath-aware L3)**
- **영향**:
  - 커버리지 목표 **85%** 유지
  - v1 일정 **4주 → 6주** 확장
  - 아키텍처에 **DexClassLoader + 사용자 앱 APK 감지** 추가
  - 완전한 standalone은 아니지만 Gradle에 hard-lock되지도 않음 (app APK 이미 빌드되어 있다고 가정)
- **이유**:
  - 85% 서사를 정직하게 지키려면 custom view 렌더 필수
  - "v1.0 대충 → v1.1 제대로"의 점진 전략은 첫 사용자 신뢰를 훼손
  - DexClassLoader 주입은 검증된 패턴 (LeakCanary, Flipper 등에서 사용)

### 1.2 7개 비논쟁 픽스 적용
페어 양쪽이 일치한 수정 사항들 (사용자 추가 결정 불필요, 즉시 canonical로 확정):

| # | 픽스 | 원 섹션 | 변경 |
|---|------|---------|------|
| F1 | 에러 카탈로그 도입 | `01 §3.1` | `Unrenderable(reason: String)` → `Unrenderable(reason: UnrenderableReason, detail: String, remediation: String)` |
| F2 | per-layout-key 동시성 mutex | `01 §3.1` | RenderDispatcher에 `Map<RenderKey, Mutex>` single-flight 보장 |
| F3 | Cache key 확장 | `01 §7` | 6개 → 14개 입력 (본 문서 §3) |
| F4 | Cache invalidation 통일 | `01 §3.3, §6` | v1은 conservative(전체 무효화). 의존 그래프는 v1.5로 명시 이관 |
| F5 | CLI mode v1 승격 | `03 §2` (v1.5 → v1) | `axprev render <file> --out foo.png` 커맨드 v1 필수 범위 |
| F6 | 문서 surface 확장 | `01 §directory, 03 §W4` | `CONTRIBUTING.md`, `ARCHITECTURE.md`, `CHANGELOG.md`, `SECURITY.md`, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`, `docs/UNINSTALL.md`, `docs/FAQ.md` (Why not Paparazzi) 추가 |
| F7 | CLI 바이너리명 변경 | 전체 | `axp` → `axprev` (퍼블릭 브랜드 `android-xml-previewer` 유지). npm/PyPI 충돌 회피 |

---

## 2. 개정된 아키텍처: L3 (classpath-aware)

### 2.1 L3 렌더 플로우 (REVISED)

```
User saves foo.xml
  │
  ▼
L1 (layoutlib) 시도
  ├─ 성공 → PNG 반환, cache put
  └─ 실패 (ClassNotFoundException, binding) → L3 시도
       │
       ▼
  ┌────────────────────────────────────────┐
  │ EmulatorRenderer (classpath-aware)      │
  │                                         │
  │ 1. AndroidProjectLocator가 app APK 탐색  │
  │    ├─ app/build/outputs/apk/debug/      │
  │    │   app-debug.apk                     │
  │    └─ 없으면 Unrenderable(NO_APP_APK,    │
  │       "build :app:assembleDebug 먼저")   │
  │                                         │
  │ 2. adb push foo.xml → AVD tmp            │
  │ 3. adb push app-debug.apk → AVD tmp      │
  │ 4. am start com.axp.harness \            │
  │      --es xml_path /tmp/foo.xml \        │
  │      --es app_apk_path /tmp/app.apk      │
  │                                         │
  │ 5. HarnessActivity:                      │
  │    DexClassLoader(app_apk_path, …)       │
  │    └→ LayoutInflater.Factory 등록        │
  │    └→ resolveNameToClass가 user app     │
  │       classloader에서도 lookup           │
  │    └→ inflate + bitmap + 저장            │
  │                                         │
  │ 6. adb pull out.png                      │
  └────────────────────────────────────────┘
```

### 2.2 app APK 감지 룰

`AndroidProjectLocator.kt`에 새 메서드:
```kotlin
fun findAppApk(projectRoot: Path): Result<Path, NoAppApkReason> {
  val candidates = listOf(
    "app/build/outputs/apk/debug/app-debug.apk",
    "build/outputs/apk/debug/app-debug.apk",
    "**/build/outputs/apk/*/debug/*.apk"  // multi-module fallback
  )
  // 가장 최근 APK 선택
  // 없으면 NoAppApkReason.NOT_BUILT 반환 + remediation hint
}
```

### 2.3 DexClassLoader 주입 위험 완화

- **AppCompat/Material 버전 불일치**: harness APK의 androidx 버전과 사용자 앱 androidx 버전이 다르면 `NoSuchMethodError`. 완화: harness APK를 **minSdk 21, compileSdk 34, androidx 최소 의존**으로 빌드. 사용자 앱이 더 최신 androidx를 쓰면 그 apk의 dex가 override.
- **Manifest merge 이슈**: harness APK의 theme이 사용자 앱 테마를 override하지 못함. 완화: harness Activity가 `setTheme(resources.getIdentifier(...))`로 런타임 테마 적용.
- **ClassLoader leak**: 매 렌더마다 새 DexClassLoader 생성하면 메모리 누수. 완화: `LruCache<AppApkHash, DexClassLoader>` 5개 유지.

### 2.4 새로운 실패 모드

| 실패 | 원인 | Unrenderable Reason | Remediation |
|------|------|---------------------|-------------|
| `NO_APP_APK` | `app/build/outputs/apk/debug/*.apk` 없음 | NO_APP_APK_BUILT | "build :app:assembleDebug 실행 후 재시도" |
| `APK_TOO_OLD` | app APK가 3일 이상 된 경우 | STALE_APP_APK | "최근 소스 변경 반영 위해 :app:assembleDebug 재실행 권장" |
| `DEX_LOAD_FAIL` | DexClassLoader 초기화 실패 | DEX_LOAD_ERROR | 스택 트레이스 + 이슈 리포트 링크 |
| `ANDROIDX_MISMATCH` | harness/app androidx 버전 충돌 | ANDROIDX_CONFLICT | "plugin 업그레이드 권장" |
| `HARNESS_APK_ABI_MISMATCH` | AVD ABI와 harness ABI 불일치 | ABI_MISMATCH | "x86_64 AVD 사용 권장" |

---

## 3. 개정된 Cache Key (F3)

### 3.1 Canonical Cache Key Inputs

```kotlin
data class RenderKey(
  // 1. Source content
  val xmlContentSha256: String,             // 정규화된 XML 내용 (LF only)
  val normalizedRelativePath: String,       // 프로젝트 루트 기준 상대 경로
  val resourceTableSha256: String,          // 컴파일된 .arsc sha256

  // 2. Device/display
  val devicePreset: String,                 // phone_small / phone_normal / ...
  val themeId: String,                      // light / dark / AppTheme.Dark
  val nightMode: Boolean,
  val fontScale: Float,                     // 0.85 / 1.0 / 1.15 / 1.30
  val locale: String,                       // en-US / ko-KR / ...

  // 3. Renderer layer + version
  val renderLayer: RenderLayer,             // LAYOUTLIB | EMULATOR
  val layoutlibJarSha256: String,           // 번들 JAR sha256
  val bundledFontPackSha256: String,        // Noto fonts 등 번들 폰트 해시
  val apiLevel: Int,                        // 34

  // 4. Tool versions
  val aapt2Version: String,                 // "34.0.0"
  val sdkBuildToolsVersion: String,
  val jvmMajor: Int,                        // 11 / 17 / 21

  // 5. L3 specific (L3 렌더일 때만)
  val appApkSha256: String?,
  val harnessApkSha256: String?,
  val avdSystemImageFingerprint: String?
)

fun RenderKey.digest(): String = sha256(
  canonicalJson(this)  // stable serialization
)
```

### 3.2 불변 입력 보장

- **XML content 정규화**: `\r\n → \n`, trailing whitespace strip, 파일 끝 개행 통일
- **상대 경로**: `projectRoot.relativize(xmlFile)` → 항상 `app/src/main/res/layout/foo.xml` 형태
- **폰트**: 플러그인이 번들하는 `fonts/` 디렉토리 전체를 tarball sha256
- **layoutlib**: 번들 JAR 파일 sha256 (첫 실행 시 계산, 이후 캐시)

---

## 4. 개정된 Unrenderable Enum (F1)

```kotlin
enum class UnrenderableReason(
  val code: String,           // 안정적 머신 읽기용
  val category: Category,
  val remediationUrl: String  // docs/TROUBLESHOOTING.md#<code>
) {
  // L1 실패
  L1_PARSE_ERROR("AXP-L1-001", Category.USER_CODE, "#l1-parse-error"),
  L1_CUSTOM_VIEW_CLASS_NOT_FOUND("AXP-L1-002", Category.CUSTOM_VIEW, "#l1-custom-view"),
  L1_DATA_BINDING_NOT_EVALUATED("AXP-L1-003", Category.DATA_BINDING, "#l1-data-binding"),
  L1_LAYOUTLIB_BUG("AXP-L1-004", Category.TRANSIENT, "#l1-layoutlib-bug"),
  L1_OOM("AXP-L1-005", Category.RESOURCE_LIMIT, "#oom"),

  // L3 실패
  L3_NO_APP_APK_BUILT("AXP-L3-001", Category.USER_ACTION, "#l3-no-apk"),
  L3_STALE_APP_APK("AXP-L3-002", Category.USER_ACTION, "#l3-stale-apk"),
  L3_DEX_LOAD_ERROR("AXP-L3-003", Category.ENVIRONMENT, "#l3-dex-load"),
  L3_ANDROIDX_CONFLICT("AXP-L3-004", Category.ENVIRONMENT, "#l3-androidx"),
  L3_ABI_MISMATCH("AXP-L3-005", Category.ENVIRONMENT, "#l3-abi"),
  L3_AVD_NOT_FOUND("AXP-L3-006", Category.ENVIRONMENT, "#l3-no-avd"),
  L3_AVD_BOOT_TIMEOUT("AXP-L3-007", Category.TRANSIENT, "#l3-avd-timeout"),

  // 시스템
  SYSTEM_ANDROID_HOME_MISSING("AXP-SYS-001", Category.ENVIRONMENT, "#no-android-home"),
  SYSTEM_JVM_TOO_OLD("AXP-SYS-002", Category.ENVIRONMENT, "#jvm-old"),
  SYSTEM_AAPT2_FAILED("AXP-SYS-003", Category.ENVIRONMENT, "#aapt2-fail");

  enum class Category {
    USER_CODE,         // XML 오류 등, 사용자 코드 수정 필요
    USER_ACTION,       // 빌드 실행 등, 사용자 액션 필요
    CUSTOM_VIEW,       // custom view 관련 (v1.0 제한)
    DATA_BINDING,      // binding 관련 (v1.5에서 개선)
    ENVIRONMENT,       // 환경/설치 문제
    RESOURCE_LIMIT,    // OOM 등
    TRANSIENT          // 재시도로 해결 가능
  }
}

data class UnrenderableResult(
  val reason: UnrenderableReason,
  val detail: String,              // 구체 에러 메시지 (JVM 예외 요약)
  val stackTrace: String? = null   // 전체 스택 (디버그 모드)
)
```

### 4.1 Viewer UI 에러 카드

```
┌──────────────────────────────────────────────────┐
│ ❌ 렌더 불가 [AXP-L3-001]                         │
├──────────────────────────────────────────────────┤
│ app APK가 빌드되지 않았습니다.                    │
│                                                   │
│ L3 에뮬레이터 렌더는 custom view 클래스를 위해    │
│ 사용자 앱의 APK가 필요합니다.                     │
│                                                   │
│ 🔧 해결:                                          │
│   ./gradlew :app:assembleDebug                    │
│                                                   │
│ 📖 자세히: docs/TROUBLESHOOTING.md#l3-no-apk     │
│ 📋 스택 복사    🐛 이슈 리포트                    │
└──────────────────────────────────────────────────┘
```

---

## 5. 개정된 Concurrency 모델 (F2)

```kotlin
class RenderDispatcher(
  private val l1: LayoutlibRenderer,
  private val l3: EmulatorRenderer,
  private val cache: RenderCache
) {
  // per-key single-flight: 같은 RenderKey의 동시 요청은 1개만 실행, 나머지는 결과 공유
  private val inFlight = ConcurrentHashMap<RenderKey, Deferred<RenderResult>>()

  suspend fun render(req: RenderRequest): RenderResult {
    val key = req.toRenderKey()

    cache.get(key)?.let { return it }

    return inFlight.computeIfAbsent(key) {
      coroutineScope.async {
        try {
          doRender(req, key).also { cache.put(key, it) }
        } finally {
          inFlight.remove(key)
        }
      }
    }.await()
  }

  // 취소: 같은 파일의 새 저장이 오면 in-flight 렌더 cancel
  fun cancelStale(layoutPath: Path) {
    inFlight.entries
      .filter { it.key.normalizedRelativePath == layoutPath.toString() }
      .forEach { it.value.cancel(CancellationException("Stale: newer edit")) }
  }
}
```

---

## 6. 개정된 6주 Roadmap (F1, F5 반영)

### Week 1 — Foundation + L1
`03-roadmap.md §Week 1`과 동일.

### Week 2 — L2 + FileWatcher + SSE
`03-roadmap.md §Week 2`와 동일.

### Week 3 — L3 Basic (harness APK + AVD)
`03-roadmap.md §Week 3`과 동일. harness APK는 이 시점까지 **classpath-agnostic** 버전.

**Go/No-Go (Week 3 종료)**:
- [ ] 에뮬레이터 부팅 + harness APK 설치 + XML 렌더 성공
- [ ] **classpath-agnostic 상태에서 커버리지 측정** (대략 75% 예상)

### Week 4 — L3 Classpath-Aware (NEW)
**월-화 (D16-D17)**: `AndroidProjectLocator.findAppApk()` + staleness check
**수-목 (D18-D19)**: harness APK의 HarnessActivity에 DexClassLoader 주입 + 테마 적용
**금 (D20)**: 5개 custom-view 샘플 레이아웃으로 renderless → render 전환 확인

**Go/No-Go (Week 4 종료)**:
- [ ] `MaterialCardView` 등 AndroidX custom view L3 렌더
- [ ] `com.acme.FancyChart` 등 사용자 정의 View 클래스 L3 렌더
- [ ] app APK 삭제 상태에서 Unrenderable(NO_APP_APK) + remediation 안내

### Week 5 — UX, Error Catalog, CLI Mode
**월 (D21)**: `UnrenderableReason` enum + 에러 카드 UI
**화 (D22)**: CLI mode (`axprev render foo.xml --device=phone_normal --out foo.png`)
**수 (D23)**: SSH/headless 안내 (`axprev serve --no-open-browser --host=0.0.0.0` + port forwarding 로그 출력)
**목 (D24)**: Viewer UI — 디바이스 드롭다운 / 테마 토글 / 에러 배지 / fallback mode 표시
**금 (D25)**: 모든 실패 경로에 대한 end-to-end 수동 테스트

### Week 6 — 검증 + 문서 + 릴리스
**월-화 (D26-D27)**: Golden corpus — 공개 OSS 10개 프로젝트 샘플링, 85% 목표 측정
**수 (D28)**: 문서 surface 완성 (README, INSTALL, LIMITATIONS, TROUBLESHOOTING, CONTRIBUTING, ARCHITECTURE, CHANGELOG, SECURITY, PRIVACY, THIRD_PARTY_NOTICES, UNINSTALL, FAQ)
**목 (D29)**: 패키징 + 플러그인 마켓플레이스 제출 준비
**금 (D30)**: **v1.0.0 릴리스 태그**

### Escape Hatches (REVISED)
- **Week 3 종료 시점**: L3 basic이 동작 안 하면 v1.0을 A로 릴리스 (하지만 이미 α 선택했으니 드문 경로)
- **Week 4 종료 시점**: classpath-aware가 동작 안 하면 β로 강등 (커버리지 주장 75%로 수정)
- **Week 6 검증에서 85% 미달**: v1.0 릴리스 대신 v1.0-rc로 퍼블릭 베타, 실 사용 데이터로 주장 수정

---

## 7. 개정된 전제 (00-overview.md §6 override)

| # | 전제 | 개정 여부 |
|---|------|----------|
| 1 | v1 산출물 = Claude Code 플러그인 1종 | 유지 |
| 2 | L1 + L2 + **L3 (classpath-aware via DexClassLoader)** | **개정**: "harness APK"에 DexClassLoader 주입 추가 |
| 3 | skill + .mcp.json + Kotlin/JVM MCP 서버 + HTTP+SSE + FileWatcher | 유지 |
| 4 | 단일 레이아웃 → 1장 렌더 | 유지 |
| 5 | **커버리지 목표 = 85% (단, app APK 빌드 상태 가정)** | **개정**: "app APK 빌드되어 있다는 가정 하에 85%" 명시 |
| 6 | 프로젝트 루트 자동 감지 + `--project` 플래그 + **app APK 감지** | **개정**: app APK 감지 추가 |
| 7 | GitHub OSS Apache 2.0 + 마켓플레이스. layoutlib JAR 번들 + **harness APK 번들** + JVM 11+ + AVD + app APK 빌드 필수 | **개정**: harness APK 번들 + app APK 빌드 요구사항 명시 |

---

## 8. 개정된 리스크 (04-open-questions-and-risks.md 보강)

### 추가 리스크

#### R10 (Critical): DexClassLoader + 런타임 클래스 로딩의 프래질
- **상세**: 사용자 앱이 ProGuard/R8 난독화된 APK이면 custom view 클래스명이 `a.b.c`로 변경. harness가 원본 XML의 `com.acme.FancyChart`를 찾지 못함.
- **완화**: v1은 debug APK (unminified)만 지원. release APK는 Unrenderable(OBFUSCATED_APK) 안내.
- **측정**: "release APK로 렌더 시도 수 / 전체 L3" > 5% 관찰되면 v1.5에 mapping 파일 지원 추가.

#### R11 (High): app APK와 소스 코드 불일치
- **상세**: 사용자가 XML 수정 후 app APK를 재빌드하지 않으면, L3는 "오래된 custom view"로 렌더. 사용자는 "최신 상태가 안 반영됨"이라 인식.
- **완화**: app APK의 mtime이 XML mtime보다 오래되면 `STALE_APP_APK` 경고 + auto-rebuild suggestion.

#### R12 (Medium): 6주 스케줄 자체의 슬립 가능성
- **상세**: α 선택으로 일정 +50%. 엔지니어 컨디션/휴일/학습 시간 고려 시 실질 8주 예상.
- **완화**: Week 4 종료 시 β로 강등하는 escape hatch를 사전 공식화 (§6).

---

## 9. 변경 로그

| 일자 | 변경 | 작성자 |
|------|------|--------|
| 2026-04-22 | 최초 작성 — α 결정 + 7개 픽스 반영 | office-hours 파이프라인 |
