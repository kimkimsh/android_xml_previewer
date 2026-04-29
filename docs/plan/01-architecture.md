# 아키텍처 설계

> **상태**: DRAFT (Phase 5)
> **전제**: `00-overview.md`의 확정 전제 7개를 모두 전제로 한다.

---

## 1. 10-second High-Level View

```
┌──────────────────────────────────────────────────────────────┐
│ Claude Code CLI                                              │
│                                                              │
│  [skill: preview-layout]                                     │
│         │                                                    │
│         └──► 사용자 /preview-xml <path>   (수동 트리거)     │
│                                                              │
└────────────┬─────────────────────────────────────────────────┘
             │  (슬래시커맨드 + .mcp.json 연결)
             ▼
┌──────────────────────────────────────────────────────────────┐
│ MCP Server (Kotlin/JVM, long-running)                        │
│                                                              │
│ ┌──────────────────┐  ┌──────────────────┐  ┌─────────────┐  │
│ │ FileWatcher       │  │ RenderDispatcher │  │ HttpServer  │  │
│ │ (res/layout,      │─▶│  L1: layoutlib    │◀─│ HTTP+SSE    │  │
│ │  res/values, …)   │  │  L2: aapt2 cache  │  │ localhost:  │  │
│ └──────────────────┘  │  L3: AVD harness  │  │   7321      │  │
│                        └──────────────────┘  └─────────────┘  │
│                                                       ▲      │
└───────────────────────────────────────────────────────┼──────┘
                                                        │
                                          ┌─────────────┴───────────┐
                                          │ 사용자 브라우저           │
                                          │ (HTML viewer, SSE live)  │
                                          └─────────────────────────┘
```

**핵심 원칙**:
- **MCP 서버는 long-running** — Claude Code 세션당 1개 프로세스, XML 수정 이벤트 큐 처리
- **FileWatcher가 PostToolUse hook보다 상위** — Claude가 수정하든, vim이 수정하든, git checkout이 파일을 바꾸든 동일 트리거
- **HTTP+SSE는 MCP 서버에 임베드** — 별도 daemon 없음. 포트 충돌 시 7321→7322→7323 탐색 후 선택
- **브라우저는 유일한 "뷰어"** — 플러그인이 어떤 터미널 UI도 띄우지 않음

---

## 2. 디렉터리 레이아웃

```
android-xml-previewer/                    ← 플러그인 루트 (GitHub 리포)
├── .claude-plugin/
│   └── plugin.json                      ← Claude Code 매니페스트
├── skills/
│   └── preview-layout/
│       └── SKILL.md                     ← 주 트리거
├── commands/
│   └── preview-xml.md                   ← 수동 슬래시커맨드
├── .mcp.json                            ← MCP 서버 선언
├── server/                              ← Kotlin/JVM MCP 서버
│   ├── build.gradle.kts
│   ├── src/main/kotlin/axp/
│   │   ├── Main.kt                      ← 엔트리
│   │   ├── mcp/
│   │   │   └── McpHandler.kt           ← MCP stdio 프로토콜
│   │   ├── render/
│   │   │   ├── RenderDispatcher.kt      ← L1→L3 escalation
│   │   │   ├── LayoutlibRenderer.kt     ← L1: layoutlib 호출
│   │   │   ├── ResourceCompiler.kt      ← L2: aapt2 wrapper
│   │   │   ├── EmulatorRenderer.kt      ← L3: AVD + harness APK
│   │   │   └── RenderResult.kt          ← sealed: Success | Fallback | Unrenderable
│   │   ├── project/
│   │   │   └── AndroidProjectLocator.kt ← res/ 자동 감지, 모듈 탐색
│   │   ├── watch/
│   │   │   └── LayoutFileWatcher.kt     ← Java WatchService
│   │   ├── http/
│   │   │   ├── PreviewHttpServer.kt     ← Netty / Ktor
│   │   │   └── SseBroadcaster.kt        ← 갱신 이벤트 push
│   │   └── cache/
│   │       └── RenderCache.kt           ← (xml hash, res hash, device, theme, sdk) → PNG
│   └── libs/
│       ├── layoutlib-android-34.jar     ← 번들 (Premise 7)
│       └── harness.apk                  ← 사전 빌드된 L3 harness (작은 크기)
├── viewer/                              ← HTML/JS 프론트엔드 (MCP 서버가 정적 서빙)
│   ├── index.html
│   ├── app.js                           ← SSE 수신, 디바이스 스위치
│   └── styles.css
├── hooks/
│   └── hooks.json                       ← (선택) Session 종료 시 서버 cleanup
├── scripts/
│   ├── install-check.sh                 ← Android SDK / JVM 존재 확인
│   └── package-release.sh
├── docs/
│   ├── INSTALL.md
│   ├── LIMITATIONS.md
│   └── TROUBLESHOOTING.md
├── LICENSE                              ← Apache 2.0
└── README.md
```

**왜 Kotlin/JVM MCP 서버인가**:
- layoutlib과 aapt2는 JVM native (layoutlib은 Java, aapt2는 CLI 바이너리)
- Kotlin은 null-safety + coroutines + Java interop 모두 강점
- Android 생태계와 언어 통일 → 향후 contributor 유입 ↑

**Python/Node를 쓰지 않는 이유**: layoutlib을 호출하려면 JVM 프로세스 ↔ MCP 프로세스 간 IPC가 필요 → 레이턴시 + 복잡도 상승. 한 프로세스에 통합이 최적.

---

## 3. 렌더 파이프라인 상세

### 3.1 3-Layer Escalation

```kotlin
sealed class RenderResult {
  data class Success(val png: ByteArray, val layer: RenderLayer, val elapsed: Duration) : RenderResult()
  data class Fallback(val png: ByteArray, val layer: RenderLayer, val reason: String, val elapsed: Duration) : RenderResult()
  data class Unrenderable(val reason: String, val partialPng: ByteArray?, val elapsed: Duration) : RenderResult()
}

enum class RenderLayer { LAYOUTLIB, EMULATOR }

class RenderDispatcher(
  private val layoutlib: LayoutlibRenderer,
  private val emulator: EmulatorRenderer,
  private val cache: RenderCache
) {
  suspend fun render(req: RenderRequest): RenderResult {
    cache.get(req)?.let { return it }

    // L1: try layoutlib first
    val l1 = layoutlib.tryRender(req)
    if (l1.isSuccess()) {
      cache.put(req, l1)
      return l1
    }

    // L3: escalate to emulator if failure signal suggests it can help
    if (l1.escalatable()) {
      val l3 = emulator.tryRender(req)
      if (l3.isSuccess()) {
        cache.put(req, l3)
        return RenderResult.Fallback(l3.png, EMULATOR, reason = l1.reason, elapsed = l3.elapsed)
      }
      return l3  // Unrenderable with reason
    }

    return RenderResult.Unrenderable(l1.reason, partialPng = l1.partialPng, elapsed = l1.elapsed)
  }
}
```

### 3.2 L1: layoutlib

**번들링**: 플러그인이 `libs/layoutlib-android-34.jar`를 포함. 프로젝트의 `compileSdk`와 무관하게 android-34로 렌더 (reproducibility > marginal fidelity, Claude 서브에이전트 Q6 권고).

**호출 경로**:
```
1. parse XML → LayoutInflater (layoutlib 내부)
2. BridgeContext 생성 (테마, 리소스 주입)
3. 디바이스 설정 (px, density, orientation, isRtl)
4. render() → Bitmap
5. Bitmap → PNG 인코딩
```

**실패 감지 (escalatable):**
- `ClassNotFoundException`: 커스텀 View → L3 재시도
- `@{...}` 토큰이 파싱 실패: data-binding → L3 재시도
- `Resources$NotFoundException`: → 리소스 재빌드 시도 후 재실행; 재실패 시 L3
- 렌더는 성공했으나 특정 View 영역이 비어있다고 감지: heuristic, L3 재시도

**실패 감지 (non-escalatable):**
- XML parse error: 개발자 코드 에러 → L3도 실패할 것이므로 Unrenderable
- `StackOverflowError`: 잘못된 레이아웃 구조 → Unrenderable

### 3.3 L2: aapt2 리소스 컴파일

**트리거**: `res/values/*.xml`, `res/drawable/*.xml`, `res/color/*.xml` 등 리소스 파일 변경 감지.

**증분 전략**:
```
res/values/colors.xml 변경
  → aapt2 compile res/values/colors.xml (단일 파일, ~50ms)
  → aapt2 link all compiled.flat → resources.arsc (~200ms)
  → RenderCache 무효화 (colors.xml 의존 레이아웃만)
```

**캐시 키 설계** (페어 양쪽 모두 강조한 누락 포인트):
```
cacheKey = sha256(
  xmlFile.content +
  resourceTable.hash +   // 전체 res/의 해시
  device.config +        // phone_small, tablet, ...
  theme.id +             // light/dark + AppTheme
  layoutlib.version      // android-34
)
```

### 3.4 L3: 에뮬레이터 + harness APK (v1에서 구현)

**harness APK 구조**:
```kotlin
// AXPHarnessActivity.kt (플러그인 repo에 사전 빌드)
class AXPHarnessActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val xmlPath = intent.getStringExtra("xml_path")!!
    val inflated = LayoutInflater.from(this).inflate(
      File(xmlPath).toXmlPullParser(),  // 커스텀 InflaterFactory
      null, false
    )
    setContentView(inflated)
    // onLayout 완료 후 시그널
    window.decorView.post {
      val bmp = drawToBitmap()
      File(cacheDir, "out.png").writeBytes(bmp.toPng())
      finish()
    }
  }
}
```

**렌더 흐름**:
```
1. harness APK를 AVD에 설치 (최초 1회, 캐시됨)
2. 대상 XML을 /data/local/tmp/preview/ 에 push (adb push)
3. am start -n com.axp.harness/.HarnessActivity --es xml_path /data/local/tmp/preview/foo.xml
4. 컴포넌트 finish 감지 (pm broadcast listener 또는 logcat tag)
5. adb pull /data/data/com.axp.harness/cache/out.png
6. 정리
```

**warm-pool 전략**:
- 플러그인 시작 시 background에서 AVD 부팅 (snapshot 사용으로 ~8초)
- MCP 서버가 살아있는 동안 AVD 유지
- AVD 종료는 Claude Code 세션 종료 hook에서 cleanup

**Claude 서브에이전트의 Q2 우려에 대한 완화**:
- "30-60초 cold start"는 snapshot 저장으로 8-10초로 단축
- 첫 L3 렌더까지는 사용자가 "⚙ emulator 시작 중..." 상태를 브라우저에서 보게 됨 (spinner + progress)
- warm 상태에서 렌더는 ~2-4초 목표

**실패 시나리오**:
- AVD 이미지 없음 → setup guide 링크
- KVM 미사용 환경 → L3 비활성화, L1만 사용 (커버리지 ~75%)
- harness APK 설치 실패 → 상세 로그 + "install from apks/" 수동 설치 가이드

---

## 4. 플러그인 매니페스트 (구체 예시)

### 4.1 `.claude-plugin/plugin.json`
```json
{
  "name": "android-xml-previewer",
  "version": "1.0.0",
  "description": "Render Android XML layouts to a live browser preview directly from Claude Code",
  "author": { "name": "TBD", "email": "TBD@example.com" },
  "keywords": ["android", "xml", "layout", "preview", "layoutlib"],
  "mcpServers": {
    "axp-server": {
      "command": "java",
      "args": ["-jar", "${CLAUDE_PLUGIN_ROOT}/server/build/libs/axp-server.jar"],
      "env": {
        "AXP_LIBS": "${CLAUDE_PLUGIN_ROOT}/server/libs",
        "ANDROID_HOME": "${ANDROID_HOME}"
      }
    }
  }
}
```

### 4.2 `skills/preview-layout/SKILL.md`
```markdown
---
name: preview-android-layout
description: |
  Use this skill when the user edits an Android XML layout file
  (res/layout/*.xml) and wants a live visual preview in the browser.
  Also use when the user asks to "render layout", "preview XML",
  "see the layout", or mentions an Android XML file path.

  <example>
  user: "activity_main.xml 어떻게 보일지 보고 싶어"
  assistant: "[invoke preview-layout skill]"
  <commentary>User explicitly asks for layout preview</commentary>
  </example>

  <example>
  user: "이 Button의 margin을 늘려줘"
  assistant: "[edit XML, then auto-preview via filesystem watcher — no skill call needed]"
  <commentary>Filesystem watcher handles auto-render; skill is for manual
  'open the browser viewer' flow</commentary>
  </example>
version: 1.0.0
allowed-tools: Read, Bash(axp:*, xdg-open:*)
---

# Preview Android Layout

When invoked, call the `axp-server` MCP tool `open_viewer` which:
1. Ensures the preview HTTP server is running on localhost:7321 (starts if needed)
2. Opens the user's default browser to that URL if not already open
3. Returns the viewer URL to the user

Do NOT call `render_layout` directly — the filesystem watcher handles re-renders
automatically. Only `open_viewer` is needed in most cases.

If the user asks for a specific XML file ("preview activity_main.xml"):
1. Resolve the full path (look for `res/layout/activity_main.xml` in project)
2. Call `axp-server.render_layout(path)` to pre-render it
3. Then `open_viewer` to focus on that layout
```

### 4.3 `commands/preview-xml.md`
```markdown
---
description: Open the Android XML layout preview viewer in the browser
allowed-tools: Bash(xdg-open:*), mcp__axp-server__open_viewer
argument-hint: [optional: specific layout file path]
---

Ensure the AXP preview server is running, then open the viewer at http://localhost:7321.

If $ARGUMENTS contains a specific layout file, pre-render it before opening.
```

---

## 5. MCP 서버 인터페이스

### 5.1 노출하는 MCP tools
```kotlin
@McpTool("render_layout")
suspend fun renderLayout(
  xmlPath: String,
  device: String = "phone_normal",
  theme: String = "light"
): RenderResponse
```

```kotlin
@McpTool("list_devices")
fun listDevices(): List<DevicePreset>
// [phone_small (360x640), phone_normal (412x892), tablet (800x1280), foldable (673x841), ...]
```

```kotlin
@McpTool("open_viewer")
suspend fun openViewer(): ViewerUrl
// "http://localhost:7321"
```

```kotlin
@McpTool("get_render_status")
fun getRenderStatus(): ServerStatus
// { renders_total, cache_hits, l1_success_rate, l3_success_rate, avd_state }
```

### 5.2 내부 API (브라우저 ↔ MCP 서버)

**HTTP endpoints**:
```
GET  /                      → viewer/index.html
GET  /static/*              → viewer/ 정적 파일
GET  /preview?layout=foo.xml&device=phone_normal&theme=light
                            → 현재 PNG (binary)
GET  /api/layouts           → JSON: 현재 프로젝트의 res/layout/*.xml 목록
GET  /api/events (SSE)      → 렌더 완료 이벤트 실시간 push
POST /api/select            → 뷰어에서 사용자가 레이아웃/디바이스 변경
```

**SSE 이벤트 스키마**:
```json
event: render_complete
data: {
  "layout": "activity_main.xml",
  "device": "phone_normal",
  "theme": "light",
  "layer": "layoutlib",
  "elapsed_ms": 830,
  "png_url": "/preview?layout=activity_main.xml&device=phone_normal&theme=light&v=af42"
}
```

---

## 6. FileWatcher 설계 (페어가 강하게 권고한 부분)

```kotlin
class LayoutFileWatcher(
  private val projectRoot: Path,
  private val onEvent: (WatchEvent) -> Unit
) {
  private val watchService = FileSystems.getDefault().newWatchService()
  private val debouncer = Debouncer(300.milliseconds)

  fun start() {
    watchRecursively(projectRoot / "res" / "layout", LAYOUT)
    watchRecursively(projectRoot / "res" / "values", VALUES)
    watchRecursively(projectRoot / "res" / "drawable", DRAWABLE)
    watchRecursively(projectRoot / "res" / "color", COLOR)
    // app/src/main/AndroidManifest.xml 도 감시 (테마 참조 변경)
    watch(projectRoot / "app" / "src" / "main" / "AndroidManifest.xml", MANIFEST)
  }

  private fun onRawEvent(path: Path, kind: Kind) {
    debouncer.submit(path) {
      val type = classify(path)
      onEvent(WatchEvent(path, type))
    }
  }
}
```

**왜 PostToolUse hook이 아닌가** (Codex + Claude 양쪽 일치):
- Hook은 Claude Code 내부 Write/Edit만 발화
- 사용자가 vim, neovim, Android Studio, git checkout 등으로 XML을 바꾸면 hook은 발화하지 않음
- FileWatcher는 **파일 시스템 변경이라는 ground truth**에 반응 → 더 견고하고 도구 중립적

**동기화 전략**:
- Debounce 300ms (저장 키 연타 무시)
- `values/` 변경 시 → 전체 캐시 무효화
- `layout/` 변경 시 → 해당 파일만 재렌더
- `drawable/` / `color/` 변경 시 → 참조 그래프 기반 invalidation (v1.5에서 고도화, v1은 conservative 전체 무효화)

---

## 7. 캐시 설계

```
.axp-cache/                           ← 프로젝트 루트에 생성
├── resources/
│   └── compiled.arsc                 ← aapt2 결과
├── renders/
│   ├── <sha256>.png                  ← 렌더 결과
│   └── <sha256>.meta.json            ← {layout, device, theme, layer, created_at}
├── index.jsonl                        ← 캐시 엔트리 메타 로그
└── avd/
    └── snapshot.img                   ← L3 warm-pool AVD 스냅샷
```

**.gitignore 추가 권고**: `.axp-cache/`

**max size**: 기본 2GB, LRU 퇴출 (v1.5). v1은 무제한 + 수동 clean 커맨드.

---

## 8. 핵심 의존성 매트릭스

| 의존성 | 버전 | 필수 여부 | Fallback |
|--------|------|----------|---------|
| JVM (OpenJDK / Adoptium) | 11+ | **필수** | 없음 — 플러그인 설치 실패 메시지 |
| Android SDK (`$ANDROID_HOME`) | 34 이상 권장 | **필수** | `install-check.sh`가 안내 |
| aapt2 (Android build-tools) | 34.0.0+ | **필수** | Android SDK에 포함, 자동 탐색 |
| layoutlib JAR | android-34 | **번들** | 플러그인이 자체 포함, 재다운로드 없음 |
| AVD (emulator) | API 34 이상 | **선택** | 없으면 L3 비활성화 (커버리지 85%→75%로 감소) |
| Netty / Ktor | 최신 | **필수** | HTTP 서버 |
| Kotlin coroutines | 1.8+ | **필수** | 렌더 스케줄링 |

---

## 9. 변경 로그

- 2026-04-22: 초안 작성 (`/office-hours` Phase 5)
