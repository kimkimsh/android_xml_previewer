# 2026-04-22 — W1D1: Android XML Previewer plugin foundation + canonical protocol scaffold

## Context

사용자 요청: `docs/plan/` 폴더의 9개 canonical 문서(00-08 + README) 를 철저히 분석하고 기획안대로 구현 진행.
마일스톤마다 Codex+Claude 페어 리뷰로 검증하며 전진할 것.

프로젝트는 **Claude Code 용 Android XML 레이아웃 프리뷰 플러그인**. Phase 5-8 의 다중 페어 리뷰를 거쳐
DEEP-APPROVED → INTEGRATION-APPROVED 상태였으며 canonical 문서 우선순위는 `08 > 07 > 06 > …`.
6주 로드맵 / 85% 실사용 커버리지 / Linux-first / Kotlin+JVM MCP 서버 / L1 layoutlib + L3 DexClassLoader-aware 에뮬레이터.

실제 실행은 본 세션이 처음이므로 `08 §5 W1D1 canonical checklist` 9개 아이템이 출발점.

## Files changed

구현 시 작성/수정된 파일 74개 중 핵심 (전체는 `find . -type f -not -path "*/build/*"` 참고):

### Project-level governance
- `LICENSE` (신규) — Apache 2.0 전문 (루트 배포 의무)
- `THIRD_PARTY_NOTICES.md` (신규) — layoutlib-api / Noto / dexlib2 등 재배포·의존 고지
- `docs/licenses/LICENSE-APACHE-2.0.txt`, `docs/licenses/README.md` (신규)
- `.gitignore` (신규) — Gradle / IDE / AVD / layoutlib JAR / .axp-cache
- `.claude-plugin/plugin.json`, `.mcp.json` (신규) — Claude Code 플러그인 매니페스트
- `skills/preview-layout/SKILL.md`, `commands/preview-xml.md` (신규)
- `docs/MILESTONES.md` (신규) — W1-W6 게이트 체크포인트 (canonical)
- `docs/W1D1-PAIR-REVIEW.md` (신규) — 페어 리뷰 결과 공식 기록
- `docs/plan/03-roadmap.md` (수정) — "HISTORICAL / SUPERSEDED" 배너 추가
- `docs/plan/08-integration-reconciliation.md` (수정) — §7 Post-Execution Errata 신규 섹션 (7.1 UnrenderableReason 17→19 / 7.4 RenderKey 17→21)

### server/ (Gradle multi-module scaffold, 7 modules)
- `server/settings.gradle.kts`, `server/build.gradle.kts` (신규)
- `server/gradle/libs.versions.toml` (신규) — JVM 17, Kotlin 1.9.23, Gradle 8.7, Ktor 2.3.11, dexlib2 3.0.3 pin
- `server/gradle/wrapper/gradle-wrapper.{jar,properties}`, `server/gradlew(.bat)` (신규 — 다른 로컬 프로젝트에서 복사)
- `server/buildSrc/{settings.gradle.kts,build.gradle.kts}` (신규) — version catalog 의 kotlin 값과 buildSrc 하드코드 일치 검증 drift guard 포함
- `server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts` (신규) — Kotlin 공통 convention plugin

### :protocol (의존성-free, 547 lines Kotlin)
- `server/protocol/build.gradle.kts`
- `server/protocol/src/main/kotlin/dev/axp/protocol/Versions.kt`
- `.../error/UnrenderableReason.kt` — **19개 enum** (L1:5 + L3:11 + SYS:3) + Category + `fromCode`
- `.../mcp/Envelope.kt` — `McpEnvelope<T>`, `ErrorEnvelope`, `Capabilities`
- `.../render/RenderRequest.kt` — schema `axp/render-request/1.0`, `DevicePreset` 9개
- `.../render/RenderKey.kt` — **21 필드** (18 mandatory + 3 nullable L3), 08 §1.4 추가 3 포함
- `.../worker/WorkerIpc.kt` — Unix socket + u32 LE length-prefixed JSON. Sealed `WorkerRequest/Response` with `@SerialName` discriminators
- `.../sse/SseEvents.kt` — 10-event taxonomy sealed class (07 §4.3)
- Tests (16): `UnrenderableReasonSnapshotTest`, `McpEnvelopeSerializationTest`, `WorkerIpcContractTest`

### :render-core / :emulator-harness / :http-server
- 각 모듈 `build.gradle.kts` + `Placeholder.kt` (W1D2-D5 에 실제 구현 이관)

### :layoutlib-worker
- `build.gradle.kts` (application plugin)
- `.../Main.kt` — 워커 엔트리 스캐폴드
- `.../LayoutlibBootstrap.kt` — URLClassLoader 격리, `validate()` / `findLayoutlibJar()` / `createIsolatedClassLoader()`
- Tests (5): `LayoutlibBootstrapTest`

### :cli / :mcp-server
- `.../cli/Main.kt` — `axprev --help|--version` (F7 브랜드명)
- `.../mcp/Main.kt` — 버전 출력 스모크 (MCP stdio 루프는 W1D3)
- `mcp-server/build.gradle.kts` — `fatJar` task 정의

### fixture/sample-app (Android Gradle project — SDK 없어 assembleDebug 미검증)
- `fixture/sample-app/settings.gradle.kts`, `fixture/sample-app/build.gradle.kts`, `.../app/build.gradle.kts`
- `.../res/layout/activity_basic.xml` (ConstraintLayout + MaterialButton — L1 spike 타겟)
- `.../res/layout/activity_custom_view.xml` (`<com.fixture.DummyView/>` — L3 에스컬레이션 타겟)
- `.../res/values/{strings,themes}.xml`, `.../res/drawable/ic_sparkle.xml`
- `.../java/com/fixture/{MainActivity,DummyView}.kt`
- `.../AndroidManifest.xml`

### server/libs/layoutlib-dist/android-34/
- `README.md` + `data/{res,fonts,keyboards,icu,linux/lib64}/.gitkeep` (실물 JAR/폰트는 W1D3)

## Why

### 왜 08 §5 를 우선 수행했나
canonical 문서 priority 가 명시됨(08 > 07 > 06). 08 §5 는 "Week 1 Day 1 체크리스트" 를 아홉 항목으로
정확히 서술 — plan 의 의도를 벗어나지 않으면서 실행을 즉시 시작할 수 있는 유일한 entry point.

### 왜 `:protocol` 을 의존성-free 로 유지하나
07 §7 레이어링: `:protocol` 변경은 API contract 변경이므로 다른 모듈 재컴파일 트리거. 여기에
`kotlinx.serialization` 외 다른 라이브러리가 들어오면 wire format 결정이 library upgrade 와 결합.
:protocol 을 의존성 최소로 유지해야 Rust/Go 워커가 붙을 v2 때 wire 호환성 보장.

### 왜 `kotlinOptions` 대신 `kotlin.compilerOptions` DSL
AGP 8.5+ 에서 `kotlinOptions` 블록이 제거 예정. 지금(AGP 8.3.2) 은 warning 만이지만 다음 업그레이드
사이클에서 build break. 교체 비용이 0 이고 영향이 미래 큰 경우엔 지금 처리.

### 왜 `buildSrc` kotlinVersion 을 catalog 파싱으로 가드했나
Codex 페어 리뷰가 지적한 drift risk: `buildSrc/build.gradle.kts` 는 `libs.versions.toml` 의
`findPlugin()` API 와 호환 안 됨(Gradle 8.7) → 하드코드 `"1.9.23"` 필요. 하드코드는 drift 원인.
catalog 파일을 텍스트로 파싱해서 configuration phase 에 `check()` 하면 CI 가 drift 즉시 검출.
17줄 Kotlin 으로 영구 보장 — 비용 대비 수익 압도적.

### 왜 UnrenderableReason 이 17 이 아니라 19 인가 (plan 문서 모순 발견)
`06 §4` 주석 "기존 13개" 는 실제 정의 15개와 불일치 (plan 자체 오기).
`08 §3.5` 는 4개(L3-008~011) 추가 후 "총 17개" 로 결론 — 15+4=19 이므로 산술 오류.
구현 시 실제 enum 을 작성하며 발견. `08 §7.1 Post-Execution Errata` 로 공식 교정.
snapshot test 는 floor=19 로 고정.

### 왜 RenderKey 가 17 이 아니라 21 인가 (두 번째 모순)
`06 §3.1` canonical 의 실제 정의: 15 non-nullable + 3 nullable L3 = 18 필드 (plan 의 "14" 는 축약).
`08 §1.4` 추가 3 → 실제 21 declared. Claude 페어 리뷰가 catch. `08 §7.4` errata 로 공식화.
W2 cache key digest 구현 시 이 21 (또는 L3 없으면 18) 을 기준으로 canonical JSON 직렬화.

### 왜 Ktor 2.3.11 유지하고 SSE 를 직접 구현할 계획인가
Ktor 3.0+ 만 `ktor-server-sse` 제공. 3.x 는 API breaking changes 다수 — W1D1 에 상위 버전 pin 은 리스크
과다. v1 목표에 비추어 `respondBytesWriter` 로 SSE 구현이 수십 줄이면 충분하고 02-alternatives
문서의 "Gradle 락인 회피 = 자체 파이프라인" 전제와 같은 논리(자체 구현이 락인 안전).

## Verification

### 빌드 + 테스트
```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer/server

./gradlew clean build
# → BUILD SUCCESSFUL in 2s / 52 actionable tasks

./gradlew :protocol:test :layoutlib-worker:test --rerun-tasks
# → 21/21 PASSED (protocol 16 + layoutlib-worker 5)
```

### drift guard 동적 검증
```bash
# 고의 불일치
sed -i 's/val kotlinVersion = "1.9.23"/val kotlinVersion = "1.9.99"/' server/buildSrc/build.gradle.kts
./gradlew :protocol:compileKotlin
# → BUILD FAILED: buildSrc 의 kotlinVersion='1.9.99' 이 libs.versions.toml 의 kotlin='1.9.23' 과 불일치.

# 복원
sed -i 's/val kotlinVersion = "1.9.99"/val kotlinVersion = "1.9.23"/' server/buildSrc/build.gradle.kts
./gradlew :protocol:compileKotlin
# → BUILD SUCCESSFUL
```

### 실행 스모크
```bash
./gradlew :mcp-server:run --args="--smoke"
# → axp-server v0.1.0-SNAPSHOT (schema 1.0) capabilities=[render.l1,sse.taxonomy.v1]
# → ok

./gradlew :cli:run --args="--version"
# → axprev 0.1.0-SNAPSHOT
```

### 페어 리뷰 수렴
- Claude `feature-dev:code-reviewer` (223s) : axis 4 모두 CONCERN 또는 PASS / verdict GO_WITH_FOLLOWUPS
- Codex `codex:codex-rescue` xhigh (재시도 126s) : axis 4 동일 / verdict 동일
- **full convergence** (4/4 axis + verdict). judge round 불필요.
- Concerns 의 발견 평면이 상보적: Claude = 소스 레벨 / Codex = 실행 레벨 → 한쪽만 돌렸으면 절반 놓침
- 상세: `docs/W1D1-PAIR-REVIEW.md`

### 08 §5 canonical checklist
- [x] 0. layoutlib-api 라이선스
- [x] 1. README "4주" 교정 (03 에 superseded 배너)
- [x] 2. 08 decisions 읽기
- [x] 3. Worker IPC Unix socket + JSON line-framed 명세
- [x] 4. 버전 pin
- [x] 5. Gradle multi-module 스캐폴드 (전체 빌드 ✅)
- [x] 6. fixture app 구조 (assembleDebug 실제 실행은 W1D2 이관 — Android SDK 필요)
- [x] 7. L1 spike 스캐폴딩 (Bridge.init 실제 호출은 W1D4 이관)
- [x] 8. Contract tests
- [x] 9. Week 1-6 게이트 문서

## Follow-ups

### 이 세션에서 해결됐지만 다음 세션도 인지해야 함
- `08 §7 Post-Execution Errata` 은 본 구현이 신설한 섹션. plan 의 다른 카운트 주장(cache key input 수,
  SSE event 수, fallback layer 수 등) 도 향후 구현 시 재검증하며 §7 에 추가되는 게 자연스러움.
- Gradle wrapper JAR 은 다른 로컬 프로젝트(`bh_secondary_app`)에서 복사. 신뢰할 수 있는 source 에서
  재확보 + SHA-256 기록 권장 (다음 세션 optional).

### W1D2-D5 이관 항목 (페어 후속)
- **W1D2-R1** (Codex concern 1): fixture `assembleDebug` 실빌드. Android SDK 설치 필요. 성공 시 APK 를
  `server/libs/fixture-app-debug.apk` 로 보관 — W3/W4 L3 타겟.
- **W1D3-R2** (Codex followup 2): Paparazzi prebuilts 에서 `layoutlib-api-*.jar` + framework data + Noto
  CJK 폰트 다운로드. 각 파일 sha256 을 `THIRD_PARTY_NOTICES.md §1.1` 부록으로 기록.
- **W1D4-R3** (Codex followup 2): `LayoutlibBootstrap.createIsolatedClassLoader()` 로 `Bridge` 로드 →
  reflection 으로 `Bridge.init(...)` 호출 성공까지의 smoke. 렌더 금지.
- **W1D5-R4** (06 §6 Week 1 exit + Codex concern 3): `:http-server` Ktor Netty + `GET /preview` +
  `respondBytesWriter` 기반 minimal SSE. viewer/index.html + app.js. activity_basic.xml PNG 를 브라우저
  localhost:7321 에 표시 — Week 1 게이트 exit criterion.

### Week 2 kick-off 전 메모화 필요 (W2-KICKOFF-R5)
- 07 §2.3 `LruCache(16)` 가 L3 프로세스-per-device 가정에서도 유효한지 사이즈 측정 기준 정의.
- 07 §4.4 publish gate 구현 상세: cancelled render 의 PNG 바이트를 renders/ 디렉토리 write 직전 drop.
- 08 §1.2 minimal SSE(render_complete 단일) 가 07 §4.5 브라우저 state machine 을 어떻게 단순화하는지
  (HANDSHAKING 상태 skip 가능 여부) 기술 노트.

### Promote to CLAUDE.md 후보 (skill §6 권고)
- **"plan 문서의 산술적 카운트 주장은 구현 시 enum/field 을 수작업으로 세어 검증한다."** — 이번 세션이
  plan 이 Codex final pass 까지 거쳤음에도 두 건의 카운트 오류를 포함함을 드러냄. plan-as-code discipline
  의 일부로 CLAUDE.md 또는 프로젝트 로컬 `.claude/knowledge/plan-arithmetic-audit.md` 로 promote 고려.

None of the above should block W1D2 kickoff — 다음 세션은 즉시 W1D2-R1 (fixture assembleDebug) 에서
시작 가능.
