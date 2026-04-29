# Handoff — W1D5 → W2D6 (Android XML Previewer)

> 다음 세션(Week 2 kick-off) 이 cold-start 로도 맥락을 잃지 않고 진행하기 위한 실용 안내.
> 먼저 `docs/W1-END-PAIR-REVIEW.md` (페어 리뷰 결과) + `docs/plan/08-integration-reconciliation.md §7.7` (W1-END divergence resolution) 을 읽고, 그 다음 본 handoff 를 읽는 순서가 canonical.

---

## 0. TL;DR

- **Week 1 부분 exit**: HTTP + SSE + viewer (placeholder PNG) 검증 완료. `java -jar axp-server.jar` → `localhost:7321/` 에서 720×1280 PNG + SSE `render_complete` 정상.
- **W1-END 페어 divergence**: Codex NO_GO (canonical stdio + 실제 render 미구현), Claude GO_WITH_FOLLOWUPS (source-level 9/9 PASS). 08 §7.7 로 canonical 해소: 2 item 을 W2 D6 blocking acceptance 로 승격.
- **다음 세션 즉시 시작 지점**: `W2D6-STDIO` + `W2D6-FATJAR` 두 task (각각 08 §7.7 blocking acceptance #1, #2-#3).
- 26 unit + 4 integration (Tier1 3 PASS / Tier2 1 SKIP — Guava transitive 이슈, W2D6-FATJAR 으로 해결).

---

## 1. 어디까지 왔나 — 한 화면 요약

```
repo root: /home/bh-mark-dev-desktop/workspace/android_xml_previewer

├── docs/plan/                          canonical 9 문서 (08 > 07 > 06 > …)
│   └── 08-integration-reconciliation.md §7.1-7.7 errata 누적
├── docs/MILESTONES.md                  Week 1 체크박스 재배치 (§7.7)
├── docs/W1D1-PAIR-REVIEW.md            baseline pair review (W1D1)
├── docs/W1-END-PAIR-REVIEW.md          ★ 신규 — W1-END divergent verdict + resolution
├── docs/work_log/
│   ├── 2026-04-22_w1d1-…/              W1D1 session
│   └── 2026-04-23_w1d2-d5-http-viewer/ ★ 이 세션 (session-log.md, handoff.md)
│
├── server/                             7-module JVM server
│   ├── protocol/                       16 tests — Capabilities 에 SSE_MINIMAL 추가
│   ├── layoutlib-worker/               5 unit + 4 integration (Tier2 SKIP)
│   │   ├── main/.../LayoutlibBootstrap.kt   findLayoutlib{Jar,ApiJar} + 6 헬퍼
│   │   └── test/.../BridgeInitIntegrationTest.kt  Tier1 3 PASS / Tier2 SKIP (Guava)
│   ├── http-server/                    ★ 본 세션 신규 구현 완료 (8 Kotlin + 2 viewer)
│   │   ├── HttpServerConstants.kt / SseConstants.kt / PlaceholderPngConstants.kt
│   │   ├── PlaceholderPngRenderer.kt / RenderEvent.kt / SseBroadcaster.kt
│   │   ├── PreviewRoutes.kt / PreviewServer.kt (@Volatile engine)
│   │   ├── resources/viewer/{index.html, app.js}
│   │   └── 5 tests
│   ├── mcp-server/                     fatJar 20MB, Main.kt HTTP 기본
│   └── buildSrc/                       -PincludeTags=integration 분기
│
├── server/libs/
│   ├── fixture-app-debug.apk           5.9MB, .gitignored
│   └── layoutlib-dist/android-34/      4-artifact bundle (155MB)
│       ├── layoutlib-14.0.11.jar       (49M, Bridge JVM)
│       ├── layoutlib-api-31.13.2.jar   (120K, Bridge parent)
│       └── data/{res,fonts,icu,keyboards,linux/lib64,overlays,build.prop,…}
│
├── fixture/sample-app/                 gradle.properties + local.properties + wrapper
│
├── THIRD_PARTY_NOTICES.md §1.1.{a,b,c,d} 4 artifact sha256
└── axp-server-0.1.0-SNAPSHOT.jar       fatJar
```

---

## 2. 다음 세션에서 바로 해야 할 것 (우선순위 순)

### 2.1 환경 확인 — 2분

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer

./server/gradlew -p server build
# 기대: BUILD SUCCESSFUL, 26 unit tests PASS

./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
# 기대: Tier1 3 PASS / Tier2 1 SKIP (Guava transitive — W2D6-FATJAR 으로 해결할 것)

ls server/libs/layoutlib-dist/android-34/*.jar
# 기대: layoutlib-14.0.11.jar + layoutlib-api-31.13.2.jar
```

### 2.2 `W2D6-STDIO` — MCP stdio JSON-RPC (08 §7.7 blocking acceptance #1)

**목적**: Week 1 Go/No-Go item 1 의 "stdio 응답" 완료.

**작업**:
- `:mcp-server/Main.kt` 의 HTTP 기본 모드 유지 + **stdio JSON-RPC 루프 병행**.
- 최소 지원: `initialize` (server info + capabilities), `tools/list` (빈 배열 허용), `notifications/initialized` (필요 시 ack). Real `render_layout` tool 은 W2D6-FATJAR 이후.
- stdio 출력은 System.out (JSON-RPC 프레임) 과 System.err (로그/배너) 분리 엄수 — logback 이 기본으로 stdout 에 쓰면 MCP 오염. logback.xml 로 STDERR appender 고정 필요.
- smoke flag (`--smoke`) 호환 유지. HTTP 모드 (`--http-server` 또는 기본) 와 stdio 모드를 분기하는 인자 또는 양쪽 동시 지원.

**reference**:
- Anthropic MCP 스펙 (공식 MCP 문서의 JSON-RPC 2.0 frame format)
- `:protocol` 의 `Capabilities.RENDER_L1`, `SSE_MINIMAL` 을 advertising 에 사용
- `:protocol/src/main/kotlin/dev/axp/protocol/mcp/Envelope.kt` 의 `McpEnvelope<T>` 재사용

### 2.3 `W2D6-FATJAR` — layoutlib transitive + real render (08 §7.7 blocking acceptance #2-#3)

**목적**: Week 1 Go/No-Go item 2 "layoutlib 으로 최소 XML 1개 렌더" 완료.

**작업 순서**:
1. `:layoutlib-worker/build.gradle.kts` 에 layoutlib runtime 의 **전이 의존을 Gradle 이 resolve 하도록 configuration** 추가. 두 가지 접근:
   - (a) `com.android.tools.layoutlib:layoutlib-runtime:14.0.11:linux` 를 `runtimeOnly` 로 선언 → Gradle 이 pom 의 Guava 등을 자동 fetch.
   - (b) fatJar task 가 pom 을 읽어 bundle (현재 구조 유지).
   (a) 가 표준 — Paparazzi 도 (a) 패턴.
2. `BridgeInitIntegrationTest Tier2` 실행 시 Guava 가 classloader 에 보이는지 확인. 실패하면 `createIsolatedClassLoader()` 의 parent 를 `getPlatformClassLoader()` → `ClassLoader.getSystemClassLoader()` 로 완화 (application classpath 가 보이도록).
3. Tier2 가 PASS 로 전환되면 = Bridge.init 실제 성공. activity_basic.xml 를 layoutlib 로 렌더해 PNG 획득 — 여기서 `LayoutlibRenderer` 구현.
4. `PreviewRoutes` 의 `pngRenderer` 를 placeholder → `LayoutlibRenderer` 로 교체. Interface 분리 (`PngRenderer` 추상 → `LayoutlibRenderer`, `PlaceholderPngRenderer` 둘 다 구현).
5. curl 검증: `/preview?layout=activity_basic.xml` 응답이 placeholder (레이아웃 이름 텍스트) 가 아닌 실제 layoutlib 렌더 결과 (MaterialButton 등).

**성공 기준**:
- BridgeInitIntegrationTest Tier2 PASS
- curl /preview 의 PNG 가 시각적으로 placeholder 아닌 layoutlib 렌더
- W1-END-PAIR-REVIEW.md Codex C-2, C-3 해소 체크

### 2.4 `W2-KICKOFF-R5` — 후순위 메모화 (W1D1 handoff 상속)

08 §7.7 의 blocking acceptance 가 완료된 후:
- 07 §2.3 `LruCache(16)` 가 L3 process-per-device 가정에서도 유효한지 사이즈 측정 기준.
- 07 §4.4 publish gate 와 08 §1.2 minimal-SSE 간 state machine 축소 논리.
- docs/notes/ 에 짧게.

---

## 3. 다음 세션이 반드시 알아야 할 landmines

### 3.1 Kotlin 언어 함정 (08 §7.5 기록)
- **Block comment 중첩**: Kotlin 은 `/*` 중첩 허용 (Java 와 다름). KDoc 본문의 `*.kcm` / `/*.txt` 같은 glob 문자열이 새 comment 를 열어 EOF 까지 unclosed. 회피: 자연어 풀어쓰기.
- **함수 body `{`**: Kotlin 은 **같은 줄 필수**. Allman 스타일(별도 줄) → syntax error. CLAUDE.md 의 Allman 규칙은 C++ 전용. 클래스/init 블록은 Allman 가능.

### 3.2 logback stdout 오염 (W2D6-STDIO blocker 후보)
`java -jar axp-server.jar` 로그 일부가 stdout 에 가므로 MCP JSON-RPC 와 충돌 리스크. `src/main/resources/logback.xml` 에 `STDERR` appender 명시 필요 (W2D6-STDIO 착수 시 first fix).

### 3.3 Gradle 통합 테스트 분기
`-PincludeTags=integration` 로만 실행. 기본 `./gradlew test` 는 integration 제외. CI 에서 integration 별도 job 으로 분리 권장.

### 3.4 placeholder PNG 교체 시 테스트 영향
`PlaceholderPngRendererTest` 는 `PlaceholderPngRenderer` 를 직접 호출하므로 유지. `LayoutlibRenderer` 는 별도 통합 테스트 (Tier2 PASS 전환과 함께 추가). `PlaceholderPngRenderer` 는 개발용 fallback 으로 삭제 금지 — CI 환경이 dist 미보유일 때 재귀적으로 필요.

### 3.5 @Volatile 다른 후보
`PreviewServer.engine` 외에도 향후 shutdown hook 류가 건드리는 가변 필드가 생기면 동일 검토. 현재는 engine 만.

### 3.6 PreviewServer.start() 의 runBlocking
`broadcaster.emitRenderComplete(...)` 를 `runBlocking` 으로 호출 중. main thread 에서 1번만 실행이라 문제없지만, 향후 start 가 여러 번 호출되거나 async context 에서 호출되면 deadlock 가능. `suspend fun start()` 로 전환 고려 (W4 이후).

---

## 4. 검증되지 않은 가정 (UNVERIFIED → 업데이트)

W1D1 handoff §4 의 UNVERIFIED 는 대부분 해소:
1. Paparazzi prebuilts 경로 — **해소**. Google Maven 4-artifact 로 canonical 전환 (08 §7.5).
2. Bridge.init 시그니처 — **부분 해소**. 7-param 형식 확인 (08 §7.5 하단). 실제 호출은 W2D6-FATJAR 이후.
3. Noto CJK OFL — 여전히 UNVERIFIED. `docs/licenses/OFL-1.1.txt` 미추가. W6 문서 surface 때 같이.

신규 UNVERIFIED:
4. Guava transitive 추가 후 `createIsolatedClassLoader()` 가 제대로 작동하는지 (parent classloader 설계가 올바른지). W2D6-FATJAR 첫 업무.
5. MCP stdio 와 HTTP 동시 운영 시 logback 간섭. W2D6-STDIO 첫 업무.

---

## 5. Task 상태 (세션 간 인계)

세션 시작 시 `TaskList` 로 확인. W1D2-D5 종료 시점:

| ID | 상태 | 제목 |
|----|------|------|
| #1 | completed | W1D2-R1 fixture assembleDebug 실빌드 + APK 보관 |
| #2 | completed | W1D3-R2 layoutlib-dist 번들 + 체크섬 기록 |
| #3 | completed | W1D4-R3 Bridge.init reflection smoke test |
| #4 | completed | W1D5-R4 minimal SSE + viewer (W1 exit criterion) |
| #5 | pending   | W2-KICKOFF-R5 Week 2 사전 메모 |
| #6 | completed | W1-END-GATE Codex+Claude 페어 리뷰 |
| **#7** | **pending** | **W2D6-STDIO MCP stdio JSON-RPC 구현** ← 다음 세션 즉시 시작 |
| **#8** | **pending** | **W2D6-FATJAR layoutlib transitive closure** ← #7 과 병행 |

---

## 6. 긴급 회복 시나리오

### 시나리오 A: Gradle 빌드가 깨졌다
```bash
./server/gradlew -p server clean build --info 2>&1 | tail -60
```
drift guard 실패 → `buildSrc/build.gradle.kts:26` 의 `kotlinVersion` 과 `gradle/libs.versions.toml:kotlin` 동시 수정 필요.

### 시나리오 B: BridgeInitIntegrationTest 가 갑자기 Tier1 FAIL
dist 번들 파일이 빠진 것. `server/libs/layoutlib-dist/android-34/README.md` 의 download 스크립트로 재구성. sha256 는 THIRD_PARTY_NOTICES.md §1.1.{a,b,c,d} 와 일치해야 함.

### 시나리오 C: curl /api/events 가 즉시 500
`@Volatile engine` race 가능성 또는 runBlocking deadlock. `start()` 재호출 금지(`check(engine == null)` 이 막음), shutdown 직후 재기동 시 engine 이 null 로 보이는 지 확인.

### 시나리오 D: canonical 문서 정합성 의심
08 > 07 > 06 순위. 08 §7 이 누적 errata. W1D1-PAIR-REVIEW.md + W1-END-PAIR-REVIEW.md 가 결정 근거.

---

## 7. 다음 세션 시작 프롬프트

같은 폴더의 `next-session-prompt.md` (미작성, 아래 내용으로 생성 권장):

```
# Android XML Previewer 플러그인 — Week 2 Day 6 이어하기

본 세션은 W1-END 게이트 통과 직후입니다. docs/W1-END-PAIR-REVIEW.md 와
docs/plan/08-integration-reconciliation.md §7.7 (Codex 페어 divergence resolution)
을 먼저 읽어주세요. 그 다음 docs/work_log/2026-04-23_w1d2-d5-http-viewer/handoff.md
로 맥락 복원 후 아래 순서로 진행:

1. 환경 확인 (2분, handoff §2.1)
2. W2D6-STDIO (#7 task) — MCP stdio JSON-RPC 최소 구현
3. W2D6-FATJAR (#8 task) — layoutlib transitive + real render
4. (선택) W2-KICKOFF-R5 (#5 task) — 07 §2.3 LruCache / §4.4 publish gate 메모

종료 시 W2D6-PAIR-REVIEW 실행 (Codex xhigh + Claude 1:1 페어 리뷰).
```
