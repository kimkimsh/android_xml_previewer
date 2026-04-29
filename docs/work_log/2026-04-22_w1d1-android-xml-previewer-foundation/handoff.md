# Handoff — W1D1 → W1D2 (Android XML Previewer)

> 다음 세션이 **cold-start 로도 맥락을 잃지 않고** W1D2 를 이어가기 위한 실용 안내.
> 먼저 이 파일을 끝까지 읽고, 그 다음 `session-log.md` 의 "Why" 섹션을 읽으면 됩니다.

---

## 0. TL;DR

- W1D1 **게이트 통과**. canonical 체크리스트 9개 중 9개 done (fixture APK 실빌드 + Bridge.init 은
  구조만 완료하고 W1D2-D4 로 이관 — Android SDK 필요해서).
- 21 테스트 모두 통과, clean build 성공, 페어 리뷰 full convergence.
- 다음 세션 **즉시 시작 지점**: `W1D2-R1` (fixture `assembleDebug` 실빌드).

---

## 1. 어디까지 왔나 — 한 화면 요약

```
repo root: /home/bh-mark-dev-desktop/workspace/android_xml_previewer

├── docs/plan/                         ← canonical 9 문서 (08 > 07 > 06 > …)
│   ├── 08-integration-reconciliation.md  ← §7 Post-Execution Errata 이 세션에 추가
│   └── ...
├── docs/MILESTONES.md                 ← W1-W6 게이트 체크포인트 (canonical)
├── docs/W1D1-PAIR-REVIEW.md           ← 페어 리뷰 결과 (Codex + Claude full convergence)
├── docs/work_log/                     ← 이 파일이 있는 곳
│
├── .claude-plugin/plugin.json         ← Claude Code 플러그인 매니페스트
├── .mcp.json
├── skills/preview-layout/SKILL.md
├── commands/preview-xml.md
├── LICENSE, THIRD_PARTY_NOTICES.md
│
├── server/                            ← Gradle 7-module JVM 서버 (전체 빌드 녹색)
│   ├── settings.gradle.kts            (:protocol, :render-core, :layoutlib-worker,
│   │                                    :emulator-harness, :http-server, :cli, :mcp-server)
│   ├── gradle/libs.versions.toml      ← JVM 17 / Kotlin 1.9.23 / Gradle 8.7 / Ktor 2.3.11
│   ├── buildSrc/                      ← kotlinVersion drift guard 포함
│   ├── protocol/                      ← 의존성-free canonical 타입 (547 L, 16 tests)
│   ├── layoutlib-worker/              ← LayoutlibBootstrap + 5 tests
│   └── [나머지 4 모듈은 Placeholder.kt 만]
│
├── fixture/sample-app/                ← mini Android app (assembleDebug 미검증)
│   └── app/src/main/{res,java,AndroidManifest.xml}
│
└── server/libs/layoutlib-dist/android-34/    ← 번들 디렉토리 스캐폴드 (JAR/폰트 미확보)
```

**파일 74개 / Kotlin·KTS 1,639줄 / 테스트 21/21 통과**

---

## 2. 다음 세션에서 바로 해야 할 것 (우선순위 순)

### 2.1 먼저 환경 확인 — 5분

다음 세션 시작 시 아래를 먼저 돌려 환경을 체크:

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer

# 1. 서버 빌드가 깨지지 않았는지
./server/gradlew -p server build
# 기대: BUILD SUCCESSFUL, 21 tests passed

# 2. Android SDK 확인
echo "ANDROID_HOME=$ANDROID_HOME"
which adb
ls $ANDROID_HOME/platforms/ 2>/dev/null | head -5
# 기대: ANDROID_HOME 설정됨, android-34 존재
# 만약 없으면 → 사용자에게 Android SDK 설치 요청 (SDK Manager 또는 sdkmanager CLI)
```

### 2.2 W1D2-R1: fixture assembleDebug 실빌드

**선행 조건**: Android SDK 34 설치 + `ANDROID_HOME` 설정.

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app
cp ../../server/gradlew ./gradlew 2>/dev/null || true
cp -r ../../server/gradle ./gradle 2>/dev/null || true
chmod +x gradlew
./gradlew :app:assembleDebug 2>&1 | tee /tmp/w1d2-assemble.log
```

성공 시 APK 경로: `fixture/sample-app/app/build/outputs/apk/debug/app-debug.apk`
→ `server/libs/fixture-app-debug.apk` 로 복사 (W3/W4 L3 타겟용).

실패 시: 로그에서 누락된 dependency/SDK component 추출 → SDK Manager 로 설치 → 재시도.

### 2.3 W1D3-R2: layoutlib-dist 번들 확보

Paparazzi prebuilts 에서 복사:

```bash
cd /tmp
git clone --depth=1 https://github.com/cashapp/paparazzi.git paparazzi-src
ls paparazzi-src/libs/layoutlib/ 2>/dev/null || echo "경로 변경됐을 수 있음 — Paparazzi README 재확인"

# 예상 소스 (실제 확인 필요):
#   paparazzi-src/libs/layoutlib/data/*
#   paparazzi-src/libs/layoutlib/runtime/layoutlib-native-linux-x86_64*.jar
# 이를 android_xml_previewer/server/libs/layoutlib-dist/android-34/ 로 복사
```

각 파일 sha256 계산해서 `THIRD_PARTY_NOTICES.md §1.1` 부록에 추가:
```bash
find server/libs/layoutlib-dist/android-34 -type f -not -name "*.gitkeep" | while read f; do
  echo "$(sha256sum "$f" | awk '{print $1}')  $(basename "$f")"
done
```

### 2.4 W1D4-R3: Bridge.init reflection smoke

`server/layoutlib-worker/src/test/kotlin/.../BridgeInitIntegrationTest.kt` 신규.
`@Tag("integration")` 로 unit 테스트와 분리. `LayoutlibBootstrap.createIsolatedClassLoader()` 로 JAR
로드 후 reflection `Class.forName("com.android.layoutlib.bridge.Bridge", ...).getMethod("init", ...)`.

테스트 실행:
```bash
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
```

### 2.5 W1D5-R4: minimal SSE + viewer 첫 PNG

**Week 1 exit criterion**. 다음 3개 산출:

1. `:http-server` 에 Ktor Netty + `GET /preview?layout=<path>` (PNG binary 반환) +
   `GET /api/events` (SSE, `render_complete` 단일 이벤트만)
2. `viewer/index.html` + `viewer/app.js` — `<img>` + `EventSource`, 이벤트 수신 시 `img.src = png_url + "&v=" + renderId`
3. `:mcp-server` Main.kt 를 실제 HTTP server 부팅 + activity_basic.xml 1회 L1 렌더하도록 확장

성공 기준: `java -jar ...axp-server.jar` 후 브라우저 `localhost:7321` 에서 activity_basic.xml PNG 표시.

---

## 3. 다음 세션이 반드시 알아야 할 landmines

### 3.1 plan 문서 산술 오류 2건 (canonical 교정됨)
- UnrenderableReason: plan "총 17" → 실제 **19**. `08 §7.1` 참조.
- RenderKey: plan "17-input" → 실제 **21 declared / 18 mandatory**. `08 §7.4` 참조.
- 다음 세션에서 canonical 문서의 다른 카운트 주장(SSE 이벤트 10개, cache key input 등)도 실제 구현 시
  수작업 검증 습관화. 발견 시 `08 §7` 에 sub-item 추가.

### 3.2 Ktor 2.3.11 SSE 전략
- Ktor 2.x 엔 `ktor-server-sse` 없음. W1D5-R4 의 SSE 는 `respondBytesWriter` 로 직접 구현.
- `Content-Type: text/event-stream` + `Cache-Control: no-cache` + `Connection: keep-alive` 헤더.
- 포맷: `"id: $eventId\ndata: ${json}\n\n"` 를 byte channel 로 flush.
- 07 §4.3 의 10-event 는 Week 4 D16-17 에 확장. Week 2 D10 까지는 `render_complete` 만.

### 3.3 buildSrc kotlin 버전은 두 곳 (의도적, 가드 있음)
- `server/gradle/libs.versions.toml` 의 `kotlin = "1.9.23"` 과 `server/buildSrc/build.gradle.kts` 의
  `val kotlinVersion = "1.9.23"` 가 중복. configuration phase `check()` 가 drift 시 실패.
- Kotlin 업그레이드 시 **두 곳을 동시 변경**. 한 곳만 바꾸면 CI 가 즉시 실패.

### 3.4 Gradle 빌드 스캐폴드 함정 (재발 방지)
- `subprojects { repositories { } }` 블록은 `FAIL_ON_PROJECT_REPOS` 와 충돌 — 저장소는 settings 에만.
- 서브모듈의 `plugins` 에서 `alias(libs.plugins.kotlin.serialization)` 는 buildSrc 에 이미 classpath
  에 있으므로 충돌. `id("org.jetbrains.kotlin.plugin.serialization")` (버전 없이) 로.
- Kotlin 함수명에 `§` 문자 사용 불가 (JVM 메서드명 제약). `section` 으로.

### 3.5 Android SDK 없는 환경 주의
- `fixture/sample-app/app/build.gradle.kts` 의 `compileSdk = 34` 는 Android SDK 설치 필수.
- 다음 세션이 SDK 없는 환경이면 W1D2-R1/W1D4-R3 은 건너뛰고 W1D5-R4 부터 layoutlib 렌더 없이
  HTTP+SSE 구조만 구현 가능. 단, Week 1 exit criterion(브라우저 PNG) 은 layoutlib JAR 필수이므로
  SDK 없이는 Week 1 을 "미완" 으로 기록하고 W2 로 넘어가는 escape hatch 발동.

---

## 4. 검증되지 않은 가정 (UNVERIFIED)

Codex 페어 리뷰가 명시적으로 UNVERIFIED 로 표기한 항목. 다음 세션에서 실제 확인 필요.

1. **Paparazzi prebuilts 경로** — `libs/layoutlib/android-34/` 가 현재도 유효한 경로인가, 구조가 변했나.
   Paparazzi 릴리스 노트 확인.
2. **layoutlib-api JAR 의 내부 API 안정성** — `Bridge.init(...)` 시그니처가 버전별로 변했을 가능성.
   Paparazzi 소스의 `Renderer.kt` 나 `RenderSession` 호출부를 참고.
3. **Noto CJK 번들 경로 라이선스** — OFL 1.1 전문을 `docs/licenses/OFL-1.1.txt` 로 추가 필요
   (W1D1 엔 pending 상태).

---

## 5. Task 상태 (세션 간 인계)

다음 세션 시작 시 `TaskList` 로 현재 task 상태 확인 가능. W1D1 세션 종료 시:

| ID | 상태 | 제목 |
|----|------|------|
| #1 | completed | W1D1-00 라이선스 문서 |
| #2 | completed | W1D1-01 README 교정 |
| #3 | completed | W1D1-03 Worker IPC 명세 |
| #4 | completed | W1D1-04 버전 pin |
| #5 | completed | W1D1-05 Gradle 스캐폴드 |
| #6 | completed | W1D1-06 fixture Android app |
| #7 | completed | W1D1-07 L1 spike 스캐폴드 |
| #8 | completed | W1D1-08 Contract 테스트 |
| #9 | completed | W1D1-09 Milestones 문서 |
| #10 | completed | W1-END W1D1 게이트 + 페어 리뷰 |
| #11 | completed | W1D2-PLUGIN 매니페스트 |
| #12 | completed | W1-REVIEW-FIX Claude findings 교정 |
| #13 | completed | W1-PAIR-REPORT 페어 결과 정리 + drift guard |
| **#14** | **pending** | **W1D2-R1 fixture assembleDebug 실빌드** ← 다음 세션 즉시 시작 |
| #15 | pending | W1D3-R2 layoutlib-dist 번들 다운로드 + 체크섬 |
| #16 | pending | W1D4-R3 Bridge.init reflection smoke |
| #17 | pending | W1D5-R4 minimal SSE + viewer (W1 exit) |
| #18 | pending | W2-KICKOFF-R5 LruCache/publish gate/SSE 단순화 메모 |

---

## 6. 긴급 회복 시나리오

### 시나리오 A: Gradle 빌드가 깨졌다
```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer/server
./gradlew clean
./gradlew build --info 2>&1 | tail -60
```
에러가 Kotlin plugin 관련이면 `buildSrc/build.gradle.kts:26` 의 drift guard 실패 — `kotlinVersion` 과
`gradle/libs.versions.toml:kotlin` 이 일치하는지.

### 시나리오 B: 테스트가 실패한다
`UnrenderableReasonSnapshotTest` 가 19 아닌 카운트로 실패하면 enum 에 항목 추가된 것.
`08 §7` 에 errata sub-item 추가 후 테스트의 assertion 19 → 새 값으로 변경. 절대 enum 항목을
삭제하지 말 것 (wire format 안정성).

### 시나리오 C: canonical 문서 정합성 의심
`docs/plan/08-integration-reconciliation.md` 가 최상위. 구 문서(03/01) 와 충돌 시 08 승.
08 §7 이 구현-발견 교정을 누적.

---

## 7. 다음 세션 시작 프롬프트

같은 폴더의 `next-session-prompt.md` 에 바로 붙여넣을 수 있는 프롬프트가 준비되어 있음.
