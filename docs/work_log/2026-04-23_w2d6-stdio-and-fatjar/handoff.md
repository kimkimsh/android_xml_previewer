# Handoff — W2D6 → W2D7 (Android XML Previewer)

> 다음 세션 (W2 Day 7) 이 cold-start 로 진행해도 맥락을 잃지 않기 위한 실용 안내.
> 먼저 `docs/W2D6-PAIR-REVIEW.md` (최종 페어 결과 FULL CONVERGENCE) 와
> `docs/plan/08-integration-reconciliation.md §7.7 + §7.7.1` (3a/3b 분할) 를 읽고, 그 다음 본 문서.
> 실행 플랜은 `docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md` 참고 — 플랜 페어 리뷰 결과는 `docs/W2D6-PLAN-PAIR-REVIEW.md`.

---

## 0. TL;DR

- **W2D6 FULL GO** — §7.7 item 1/2/3a 전부 CLOSED, 3b 공식 carry.
- MCP stdio 모드 가동: `java -jar axp-server.jar --stdio` 로 `initialize`/`notifications/initialized`/`tools/list`/`tools/call{render_layout}`/`shutdown` 처리. 응답은 MCP spec 준수 (`content[]` of typed blocks + JSON-RPC 2.0 §5 mutual-exclusion).
- LayoutlibRenderer 3a: Bridge.init 실 성공 + PNG 반환 (BufferedImage 기반 stub, 실 RenderSession 은 W2D7).
- 테스트: **40 unit + 5 integration (Tier1/2/3) PASS, 0 fail, 0 skip**.
- 페어: Codex xhigh + Claude sonnet 이 독립적으로 FULL CONVERGENCE → GO_WITH_FOLLOWUPS + 동일 JSON-RPC §5 `error:null` 위반 concern 검출 → 즉시 해소.
- **다음 세션 즉시 시작점**: `W2D7-RENDERSESSION` task — 실제 `Bridge.createSession` + `RenderSession.render` pipeline.

---

## 1. 이 세션에서 달성한 것 (상세)

### 1.1 MCP stdio JSON-RPC (§7.7 item 1 — CLOSED)

**7개 신규 파일** (모두 `server/mcp-server/src/main/kotlin/dev/axp/mcp/`):
- `StdioConstants.kt` — 프로토콜 상수 (메서드 이름, 에러 코드, MCP version `2025-06-18`, MIME types, tool names)
- `JsonRpc.kt` — JsonRpcRequest/Response/Error + InitializeResult + ServerInfo + ToolsListResult + ToolDescriptor (kotlinx.serialization)
- `McpMethodHandler.kt` — method 라우팅. id primitive 검증 (F-5). `notifications/initialized` 는 null return
- `McpStdioServer.kt` — stdin ndjson 루프 → handler → stdout ndjson. `explicitNulls=false` 로 §5 준수
- `Main.kt` (수정) — `--stdio` / `--http-server` / `--smoke` 분기. tool registration `render_layout` with MCP spec `content[]` response
- `resources/logback.xml` — STDERR appender pinned (stdout 은 JSON-RPC frame 전용)

**테스트 14 신규** (mcp-server):
- LogbackStderrConfigTest (1) · JsonRpcSerializationTest (3) · McpMethodHandlerTest (7, id 검증 케이스 3 포함) · McpStdioServerTest (3, content[] assertion 포함)

### 1.2 layoutlib transitive closure (§7.7 item 2 — CLOSED)

- `server/layoutlib-worker/build.gradle.kts`: `runtimeOnly` 3건 (guava 32.1.3-jre / kxml2 2.3.0 / icu4j 73.2) + POST-W2D6 pom-resolved refactor candidate 마커 주석
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt`: `createIsolatedClassLoader()` parent 를 `getPlatformClassLoader()` → `getSystemClassLoader()` 로 변경 (W3+ 재검토 경계 주석 명시)
- `BridgeInitIntegrationTest Tier2` SKIP → **PASS** 전환.

### 1.3 LayoutlibRenderer 3a (§7.7.1 — CLOSED)

- `server/protocol/src/main/kotlin/dev/axp/protocol/render/PngRenderer.kt` (신규) — interface. 순환 의존 회피 위해 `:protocol` 에 위치.
- `server/http-server/src/main/kotlin/dev/axp/http/PlaceholderPng.kt` — `implements PngRenderer` 로 수정
- `server/http-server/src/main/kotlin/dev/axp/http/PreviewRoutes.kt` + `PreviewServer.kt` — `PngRenderer` 인터페이스 의존으로 수정
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` (신규) — reflection 기반 Bridge.init + dispose hook, @Volatile + @Synchronized double-check
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererConstants.kt` (신규) — magic number 전량 상수화
- Tier3 (`LayoutlibRendererIntegrationTest`) PASS.

### 1.4 Canonical 문서 갱신

| 파일 | 변경 |
|------|------|
| `docs/plan/08-integration-reconciliation.md` | §7.7 체크박스 4개 `[x]` 전환 + §7.7.1 신규 (3a/3b 분할 + F-1..F-8 ledger) |
| `docs/MILESTONES.md` | Week 1 Go/No-Go item 2 → "3a partial + 3b carry to W2D7" |
| `docs/W2D6-PLAN-PAIR-REVIEW.md` (신규) | 플랜 단계 페어 리뷰 consolidation |
| `docs/W2D6-PAIR-REVIEW.md` (신규) | 최종 페어 리뷰 결과 (FULL CONVERGENCE GO_WITH_FOLLOWUPS) |
| `docs/work_log/2026-04-23_w2d6-stdio-and-fatjar/handoff.md` (본 문서) | W2D6 → W2D7 인계 |

---

## 2. 다음 세션 (W2D7) 즉시 시작 작업

### 2.1 환경 확인 — 2분

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
./server/gradlew -p server build                    # BUILD SUCCESSFUL
./server/gradlew -p server test                      # 40 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 5 PASS (Tier1+2+3)
java -jar server/mcp-server/build/libs/axp-server-0.1.0-SNAPSHOT.jar --smoke # ok
```

### 2.2 `W2D7-RENDERSESSION` — 실 RenderSession 경로 (§7.7.1 3b)

**목적**: §7.7 item 3 의 문자 그대로 (활동 실제 layoutlib 렌더) 충족.

**작업 순서**:
1. `com.android.ide.common.rendering.api.SessionParams` 최소 구성 — `LayoutPullParser` (XmlPullParser 로 `fixture/sample-app/app/src/main/res/layout/activity_basic.xml` 로드), `RenderingMode.NORMAL`, `HardwareConfig` (720×1280 / xhdpi / ORIENTATION_PORTRAIT / NIGHT_NOTNIGHT / KEYBOARDHIDDEN / ...).
2. `com.android.ide.common.rendering.api.ResourceResolver` / `LayoutlibCallback` 최소 구현 — 현재 fixture 레이아웃이 literal string 만 쓰므로 대부분 null return 으로 OK. 프레임워크 style `@android:style/Theme.Material.Light` 는 data/framework_res.xml 에서 해석되어야 함 (layoutlib-dist data/res 에 포함).
3. `Bridge.createSession(params)` → `RenderSession.render(timeout)` → `session.result.isSuccess` 체크 → `session.image: BufferedImage` 추출 → `ImageIO.write("PNG")`.
4. `LayoutlibRenderer.renderViaLayoutlib` 을 위 경로로 교체. BufferedImage stub 제거.
5. Tier3 기대값 강화: PNG size > 10KB, 또는 image pixels 가 특정 위치에 non-white (렌더된 TextView 의 text 가 있는 증거).

**주의**:
- `LayoutlibCallback` 의 `loadClassName` / `loadView` 로 custom View 가 요구되면 W3+ L3 영역. 기본 View 만으로 activity_basic.xml 통과해야 함.
- 실패 시 `UnrenderableReason` 로 mapping 하여 `:protocol` 계층에서 surface — spec 준수.
- `RenderSession.dispose()` 를 `ShutdownHook` 에 추가.

**Reference**:
- Paparazzi source (`com.app.cash.paparazzi:paparazzi` 의 `Paparazzi.kt` `renderView()` 구조)
- layoutlib 14.x javadoc (api surface 는 `layoutlib-api-31.13.2.jar` 안)

### 2.3 `POST-W2D6-POM-RESOLVE` — transitive pin 대체

`com.android.tools.layoutlib:layoutlib:14.0.11` 의 실 pom 을 resolve 하여 guava/kxml2/icu4j 의 canonical 버전으로 교체. 현재 pin 은 임시.

### 2.4 `W3-CLASSLOADER-AUDIT` — classloader 재검토

`createIsolatedClassLoader()` parent=systemClassLoader 가 W3 의 L3 DexClassLoader pipeline 과 호환되는지 검증. Paparazzi 방식 (layoutlib-exclude-kotlin 커스텀 로더) 도입 여부 판단.

---

## 3. landmines 기록

### 3.1 kotlinx.serialization `explicitNulls`
`encodeDefaults = true` 만 쓰면 `result:null`/`error:null` 이 방출되어 JSON-RPC 2.0 §5 위반. 필히 `explicitNulls = false` 병행. 본 세션에서 Main.kt + McpStdioServer.kt 둘 다 반영. 향후 `:protocol` 의 McpEnvelope 직렬화도 같은 정책으로 유지 필요.

### 3.2 `JsonNull` is `JsonPrimitive`
`req.id !is JsonPrimitive` 는 `JsonNull` 을 걸러내지 않음 (kotlinx 상속 구조). 현재 test `JsonNull id routes normally` 로 동작 acknowledge. MCP 클라이언트가 `"id": null` 을 보내는 것은 non-standard 이므로 실무에서 문제 없음. 향후 spec 이 이 edge 를 명시화하면 추가 guard.

### 3.3 LayoutlibRenderer ShutdownHook 순서
현재 `initialized=true` 이후 hook 등록. hook 등록 실패 시 benign (init 는 완료, dispose 만 누락). W3+ 리팩터링 시 hook-first 패턴 검토.

### 3.4 `PreviewRoutes` HEAD
`curl -I /preview` 가 405 반환 (Ktor 는 HEAD 자동 매핑 안 함). 실제 viewer 는 GET 만 사용하므로 영향 없음. W4 SSE full taxonomy 도입 시 OPTIONS/HEAD 도 함께 검토.

### 3.5 Kotlin 언어 함정 (W1D5 에서 이관)
- Block comment 중첩 (`/* */` 안 `/*` 허용) — KDoc 내 glob 표기 사용 금지.
- 함수 body `{` 동일 라인 필수 (Allman 은 클래스/init 한정).

---

## 4. 검증되지 않은 가정 — 본 세션 해소

| # | W1D5 handoff 의 UNVERIFIED | 해소 여부 |
|---|----|----------|
| 4 | Guava transitive 추가 후 isolated classloader 동작 | **해소** (Tier2 PASS + worker 컴파일 정상) |
| 5 | MCP stdio 와 HTTP 동시 운영 시 logback 간섭 | **해소** (logback.xml 로 STDERR 고정, 두 모드 모두 정상) |

---

## 5. Task 상태 (W2D6 종료 시점)

| ID | 상태 | 제목 |
|----|------|------|
| #1 | **pending** | W2D7-RENDERSESSION: 실 Bridge.createSession/RenderSession (§7.7.1 3b) ← 다음 세션 즉시 시작 |
| #2 | completed | W2D6-STDIO ✅ |
| #3 | completed | W2D6-DOCS ✅ |
| #4 | completed | W2D6-FINAL-PAIR ✅ |

신규 carry:
- POST-W2D6-POM-RESOLVE (§7.7.1 F-6 실 교체)
- W3-CLASSLOADER-AUDIT (§7.7.1 F-4)

---

## 6. 긴급 회복 시나리오

### A. Gradle 빌드 깨짐
```bash
./server/gradlew -p server clean build --info 2>&1 | tail -60
```
drift guard: `buildSrc/build.gradle.kts:26` kotlinVersion ↔ `gradle/libs.versions.toml:kotlin` 동기화.

### B. Tier2/Tier3 갑자기 SKIP/FAIL
dist 번들 파일 누락. `server/libs/layoutlib-dist/android-34/README.md` 의 다운로드 스크립트 재실행. sha256 는 THIRD_PARTY_NOTICES.md §1.1.{a,b,c,d} 와 일치해야 함.

### C. `--stdio` 모드에서 파싱 실패 / 무반응
- logback 이 stdout 으로 샜을 가능성 — `server/mcp-server/src/main/resources/logback.xml` 의 `<target>System.err</target>` 확인.
- kotlinx 가 `result:null` 등을 내고 있는지 stdout 첫 frame 확인 — `explicitNulls = false` 유지 여부 확인.

### D. canonical 문서 정합성 의심
08 > 07 > 06 > 03. §7.7.1 이 최신 canonical (W2D6). W1-END-PAIR-REVIEW + W2D6-PLAN-PAIR-REVIEW + W2D6-PAIR-REVIEW 가 결정 근거 chain.

---

## 7. 다음 세션 시작 프롬프트 (권장)

```
# Android XML Previewer — W2 Day 7 이어하기

본 세션은 W2D6 게이트 FULL CONVERGENCE GO_WITH_FOLLOWUPS 통과 직후입니다.
읽는 순서:
  1. docs/W2D6-PAIR-REVIEW.md (최종 페어)
  2. docs/plan/08-integration-reconciliation.md §7.7 + §7.7.1 (3a/3b 분할)
  3. docs/work_log/2026-04-23_w2d6-stdio-and-fatjar/handoff.md (본 handoff)

작업:
  1. 환경 확인 (2분)
  2. W2D7-RENDERSESSION (#1 task) — SessionParams + ResourceResolver + LayoutlibCallback + HardwareConfig + Bridge.createSession → RenderSession.render → PNG
  3. (선택) POST-W2D6-POM-RESOLVE

종료 시 W2D7-PAIR-REVIEW 실행 (Codex xhigh + Claude 1:1 페어).
```
