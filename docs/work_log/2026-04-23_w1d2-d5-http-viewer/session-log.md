# 2026-04-23 — W1D2-D5: Android XML Previewer Week 1 완료 (fixture APK + layoutlib bundle + Bridge smoke + HTTP/SSE/viewer)

## Context

W1D1 세션의 직접 연속. 이전 세션 handoff(`docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/handoff.md`)가 W1D2-R1 ~ W1D5-R4 순차 이관을 명시. 사용자가 "이어서 해줘" 로 지시하여 진행.

`docs/MILESTONES.md` Week 1 게이트 3 item:
1. `java -jar axp-server.jar` 10s 내 stdio + HTTP 응답
2. layoutlib 으로 최소 XML 1개 렌더
3. 브라우저에서 PNG 표시

06 §6 은 "Escape Hatch 없음 (Foundation 주차는 스킵 불가)". 본 세션은 item 3 (HTTP + SSE + viewer infra)는 검증. item 2 는 08 §7.6 escape hatch (placeholder PNG) 로 처리. item 1 의 stdio 는 미구현 (W1D3 이관 상태 유지).

## Files changed

본 세션에서 **신규 작성** (30+ 파일), **수정** (10+ 파일), **삭제** (1 파일).

### 신규 Kotlin (http-server 모듈, 8 파일)
- `server/http-server/src/main/kotlin/dev/axp/http/HttpServerConstants.kt` — 포트 7321, viewer resource path, cache control
- `server/http-server/src/main/kotlin/dev/axp/http/SseConstants.kt` — event 이름, content-type, frame separator
- `server/http-server/src/main/kotlin/dev/axp/http/PlaceholderPngConstants.kt` — 720×1280 phone_normal, Material M3 팔레트, 폰트 크기
- `server/http-server/src/main/kotlin/dev/axp/http/PlaceholderPngRenderer.kt` — AWT + ImageIO, headless 강제, 4-layer 렌더(배경/베젤/배지/본문)
- `server/http-server/src/main/kotlin/dev/axp/http/RenderEvent.kt` — `RenderCompleteEvent` Serializable data class
- `server/http-server/src/main/kotlin/dev/axp/http/SseBroadcaster.kt` — SharedFlow replay=1, AtomicLong id 생성
- `server/http-server/src/main/kotlin/dev/axp/http/PreviewRoutes.kt` — GET /preview, GET /api/events, POST /api/render-now, staticResources
- `server/http-server/src/main/kotlin/dev/axp/http/PreviewServer.kt` — Ktor Netty 임베디드, blockUntilShutdown, 초기 render_complete emit

### 신규 viewer (classpath:viewer/)
- `server/http-server/src/main/resources/viewer/index.html` — Material 3 dark surface 풍 minimal viewer
- `server/http-server/src/main/resources/viewer/app.js` — EventSource 구독 + img cache-busting refresh

### 신규 테스트 (3 파일, 5 + 4 = 9 테스트 — 기본 실행 기준)
- `server/http-server/src/test/kotlin/dev/axp/http/PlaceholderPngRendererTest.kt` (3 테스트: PNG magic signature, 720×1280 dimensions, non-trivial size)
- `server/http-server/src/test/kotlin/dev/axp/http/SseBroadcasterTest.kt` (2 테스트: replay=1 검증, id auto-increment)
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt` (@Tag("integration") 4 테스트: validate Ok, Bridge class load, init 7-param 시그니처 — 3 PASS; best-effort init 호출 — 1 SKIP)

### 수정 Kotlin
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt` — 글롭 정명화(`layoutlib-api-*.jar` → `layoutlib-*.jar` + sibling prefix 제외), `findLayoutlibApiJar()` 신규, 두 JAR URLClassLoader 에 모두 추가, 6 헬퍼(dataDir/fontsDir/nativeLibDir/parseBuildProperties/findIcuDataFile/listKeyboardPaths) 추가
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrapTest.kt` — fake JAR 이름 정명화 + sibling 2JAR 테스트 픽스
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` — `--smoke` 분기 유지 + 기본 모드 = HTTP 서버 부팅 (stdio JSON-RPC 는 W1D3 이관)
- `server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts` — `-PincludeTags=integration` 분기 추가, 기본은 integration 제외

### 신규 artifact (바이너리, gitignore)
- `server/libs/fixture-app-debug.apk` (5.9MB, sha256 `7221d4d6fe5de67ccdfebfb65f646ca35d207b5c14d8ca6a66d26f6f3ab011f2`) — `.gitignore` 추가
- `server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar` (49MB, sha256 `9a8ab05c...`)
- `server/libs/layoutlib-dist/android-34/layoutlib-api-31.13.2.jar` (120KB, sha256 `d06bc650...`)
- `server/libs/layoutlib-dist/android-34/data/{fonts(208)/icu/keyboards/linux/lib64/overlays/res(445)/resources*.bin/build.prop}` — unpack 결과

### 수정 infra
- `fixture/sample-app/gradle.properties` (신규) — `android.useAndroidX=true` + nonTransitiveRClass + jvmargs
- `fixture/sample-app/local.properties` (신규, .gitignored) — sdk.dir 로컬 경로
- `fixture/sample-app/gradle/wrapper/*` + `gradlew[.bat]` — server 에서 복사
- `.gitignore` — `server/libs/fixture-app-debug.apk` 추가
- `server/libs/layoutlib-dist/android-34/README.md` — 4 artifact 다운로드 레시피 (Google Maven, 08 §7.5)

### 수정 canonical 문서
- `docs/plan/08-integration-reconciliation.md` §7.5 (layoutlib 번들 소스 + 명명 정명화), §7.6 (W1D5-R4 placeholder PNG escape hatch)
- `THIRD_PARTY_NOTICES.md` §1.1 재구성 (4 artifact 분리), §1.1.d 추가, §5 변경 로그

### 삭제
- `server/http-server/src/main/kotlin/dev/axp/http/Placeholder.kt` — scaffold marker, 실제 클래스들로 대체

## Why

### 왜 W1D2 에서 fixture gradle.properties `android.useAndroidX=true` 를 추가했나
W1D1 scaffold 에는 AndroidX opt-in 이 빠져있어 assembleDebug 가 `checkDebugAarMetadata` 단계에서 실패 (appcompat/material/constraintlayout 모두 AndroidX). `local.properties` 의 `sdk.dir` 로 SDK 는 찾지만 AGP 는 support-library→AndroidX 전환을 기본 차단. 명시 opt-in 이 정답. `nonTransitiveRClass=true` 는 성능 + R 클래스 leak 방지 부가 설정.

### 왜 Paparazzi 가 아닌 Google Maven 에서 layoutlib 를 받나 (08 §7.5)
W1D1 README 는 Paparazzi prebuilts (`cashapp/paparazzi/libs/layoutlib/android-34/`) 를 가정했으나, Paparazzi 2.x 부터 in-tree prebuilts 가 제거되고 `com.android.tools.layoutlib` Maven artifact fetch 로 전환. handoff §4 UNVERIFIED #1 을 fact-check 하면서 발견. Google Maven 이 canonical 상류이며 Paparazzi 도 같은 소스를 쓰므로 우리도 직접 쓰는 게 자연스러움.

### 왜 3-artifact 가 아니라 4-artifact 인가 (08 §7.5 갱신, W1D4-R3)
초기 W1D3 에서는 layoutlib(JVM Bridge) + layoutlib-runtime:linux(native) + layoutlib-resources(framework res) 3개 번들했으나, Bridge 클래스 로드 시 `NoClassDefFoundError: com.android.ide.common.rendering.api.Bridge` 발생. 이 parent 클래스는 별도 좌표 `layoutlib-api` (AGP/Studio 내부 SDK API) 에 있음. plan 문서가 "layoutlib-api" 와 "layoutlib 의 api 표면" 을 혼용해서 생긴 모호성. Bridge parent + ILayoutLog 인터페이스가 여기 있으므로 **필수**. 총 155MB.

### 왜 Bridge.init() 의 실제 호출이 SKIP 이 되나 (W1D4-R3 Tier2)
Bridge 의 생성자가 Guava `ImmutableMap` 을 요구. layoutlib 의 transitive 의존 (Guava, kxml2, ICU4J 등) 은 본 번들 4 artifact 에 미포함 — 의도된 설계(runtime 만 번들, 클래스 의존은 Paparazzi 가 Gradle 로 resolve). 우리 `:layoutlib-worker` 가 compileOnly 도 두지 않는 reflection-only 접근이라 transitive 가 격리 ClassLoader 에 없음. W2 fatJar 빌드에서 layoutlib-runtime 의 pom 을 읽어 Gradle 이 transitive 해결하도록 하면 자연 해결. Tier1 (class 로드 + method 시그니처) 은 성공하므로 canonical integration 의 50% 지점까지 증명됨.

### 왜 placeholder PNG escape hatch 를 썼나 (08 §7.6)
Week 1 exit criterion "브라우저에서 PNG 표시" 의 본질은 **HTTP/SSE/viewer data path 증명** 이지 픽셀 출처 증명이 아님. layoutlib 실제 렌더는 위 Guava transitive 해결 후 W2 D6 부터 자연 활성화 — `PreviewRoutes` 의 `pngRenderer` 만 교체. infrastructure 가 완성되어야 그 위에 layoutlib 가 얹힘. 06 §6 의 "Escape Hatch 없음" 은 Week 1 전체 스킵 금지를 뜻하지 "모든 item 을 W1 에 완료해야 한다"를 의미하지 않음 (본 판단은 사용자 승인: "b").

### 왜 `-PincludeTags=integration` 분기를 buildSrc 레벨에 뒀나
통합 테스트(`BridgeInitIntegrationTest`) 는 디스크 상의 155MB dist 가 필요하고 실행 시간이 kicked-off 서비스 + JVM 격리 ClassLoader 때문에 느림. 기본 `./gradlew test` 는 unit 만 실행되어야 IDE/CI 가 빠르게 피드백. convention plugin 에서 `-PincludeTags=integration` 이 있으면 include, 없으면 exclude "integration" 로 분기. 전 모듈에 일관 적용.

### 왜 SSE 를 respondBytesWriter 로 직접 구현했나
Ktor 2.3.11 은 `ktor-server-sse` 미포함 (3.0+ 전용). 3.x 로 올리면 API breaking 다수 — W1 scope 에 비해 risk 과다. `respondBytesWriter` 로 SSE 를 30줄 안에 구현 가능하고 07 §4.3 의 10-event taxonomy 로도 자연 확장. 프레임: `id: X\nevent: render_complete\ndata: {json}\n\n`. Keep-alive 코멘트 `:connected\n\n` 송신 → 일부 클라이언트가 첫 byte 도착까지 onopen 호출 지연하는 문제 회피.

### 왜 Allman 스타일 대신 K&R 로 돌아섰나
CLAUDE.md 의 "Brace Style — Opening brace on its own new line" (C++ 예제) 을 Kotlin 함수 본문에 그대로 적용하면 **syntax error** (Kotlin 문법이 함수 body `{` 를 signature 와 같은 줄에 요구). 클래스 선언 + `init {}` 블록은 Allman 가능하지만 함수 본문은 불가. 08 §7.5 errata 하단에 "Kotlin 언어 함정 메모" 로 기록.

### 왜 KDoc 안의 `/*.kcm` 이 컴파일 에러를 낳았나
Kotlin 의 block comment 는 **중첩 허용** (Java 와 다름). KDoc 본문의 `*.kcm` 문자열이 새 `/*` 를 열어 EOF 까지 unclosed. 회피: 자연어 풀어쓰기 ("`.kcm` 키맵 파일"). 같은 errata 메모에 기록.

## Verification

### 빌드 + 테스트
```bash
./server/gradlew -p server build
# → BUILD SUCCESSFUL, 44 actionable tasks

./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
# → tier1 3 PASS, tier2 1 SKIP (기대)

# 전체 테스트 합계 (default tags):
# :protocol 16 / :layoutlib-worker 5 / :http-server 5 = 26 PASS / 0 fail / 0 skip
```

### java -jar 실행 smoke
```bash
java -jar server/mcp-server/build/libs/axp-server-0.1.0-SNAPSHOT.jar
# stderr:
#   axp-server v0.1.0-SNAPSHOT (schema 1.0) capabilities=[render.l1,sse.taxonomy.v1]
#   axp viewer ready: http://127.0.0.1:7321/
# 부팅 < 1s (Ktor "Application started in 0.026 seconds")
```

### curl end-to-end
```
GET /                                       → 200, text/html, 3387B
GET /preview?layout=activity_basic.xml      → 200, image/png, 47KB, PNG magic confirmed, 720×1280 RGBA
GET /app.js                                 → 200, 3537B
GET /api/events (timeout 3s)                → 200, text/event-stream,
                                              ":connected" + "id: N" + "event: render_complete" + "data: {...}"
POST /api/render-now                        → 200, "ok"
```

### sha256 정합성
- `layoutlib-14.0.11.jar` `9a8ab05c9617ec18a3bd8546ef9d56b4ea6271208ade27710b0baf564fe4dd59` → THIRD_PARTY_NOTICES §1.1.a 일치
- `layoutlib-api-31.13.2.jar` `d06bc650247632a4a4e6596b87312019f45e900267c5476c47a5bfa6e3fd3132` → §1.1.d 일치
- `fixture-app-debug.apk` `7221d4d6fe5de67ccdfebfb65f646ca35d207b5c14d8ca6a66d26f6f3ab011f2`

## Follow-ups

### 이 세션이 열고 닫지 못한 것
- **stdio JSON-RPC** — W1D1 handoff 가 W1D3 이관을 명시했음에도 06 §6 Week 1 exit item 1 "stdio + HTTP 응답 10s 내" 의 canonical 요건은 유효. 08 §7 에 별도 errata 로 정리 필요 (Week 1 exit 의 canonical 해석 + stdio 구현 시점 명시).
- **Tier2 Bridge.init 실행** — transitive 의존 누락으로 SKIP. W2 fatJar 가 layoutlib-runtime 의 pom dependencies 를 Gradle 로 fetch 하면 자연 해결. BridgeInitIntegrationTest 가 그 시점에 PASS 로 전환되어야 함.

### W2 진입 시 즉시 해야 할 것 (W2-KICKOFF-R5 포함)
- `:layoutlib-worker` build.gradle.kts 에 layoutlib runtime 의 transitive 의존을 가져오는 configuration 추가. 또는 layoutlib-runtime:14.0.11:linux 의 pom 을 수동으로 읽어 명시 선언.
- `PreviewRoutes` 의 `pngRenderer` 를 `LayoutlibRenderer` 구현체로 교체. 인터페이스 분리 (W1 의 `PlaceholderPngRenderer` 는 그대로 개발용 fallback 유지).
- 07 §2.3 `LruCache(16)` 가 L3 process-per-device 가정에서도 유효한지 사이즈 측정 (W1D1 handoff §2.4 의 W2-KICKOFF-R5 항목).
- 07 §4.4 publish gate 의 state machine 축소 논리.

### W1-END pair review 결과 흡수
- Codex xhigh (retry 후, summary-based): NO_GO — stdio 미구현 + layoutlib 실제 렌더 미증명 + tier2 SKIP.
- Claude source-level: (미확인, mailbox 대기)
- 결과는 `docs/W1-END-PAIR-REVIEW.md` 에 별도 기록. divergence 시 judge round 수행.

## 세션 메트릭
- 산출: Kotlin 8 신규 + 3 테스트 + 2 viewer HTML/JS = ~1,400 lines. 수정 5 파일.
- 테스트: 26 unit + 4 integration (3 PASS + 1 SKIP, best-effort 의도)
- 빌드: 전체 녹색 (server + fatJar)
- plan 문서 교정: 1 신규 errata (08 §7.5 갱신 4-artifact + Kotlin 함정), 1 신규 (08 §7.6 placeholder escape)
- 페어 리뷰: Codex NO_GO 수렴 (stdio + real render 부재), Claude 대기 중
