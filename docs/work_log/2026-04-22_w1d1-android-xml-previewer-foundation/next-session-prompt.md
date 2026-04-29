# Next Session Prompt — W1D2 이어하기

> 이 파일 **아래의 "--- 여기부터 복사 ---" 와 "--- 여기까지 복사 ---" 사이** 를 통째로 복사해 다음 세션 Claude Code 에 붙여넣으세요.
> 프롬프트는 한국어로 작성돼 있으며 canonical 문서 + 페어 규칙 + 게이트 기준을 모두 적재합니다.

---

## 사용 팁

1. **새 Claude Code 세션** 을 `/home/bh-mark-dev-desktop/workspace/android_xml_previewer` 에서 시작.
2. 아래 블록을 첫 입력으로 붙여넣기 (수정 없이 그대로).
3. Claude 가 handoff 파일을 읽고 환경 확인 후 W1D2-R1 부터 진행.
4. 중간에 Android SDK 미설치로 막히면 Claude 가 스킵 경로(W1D5-R4 먼저) 를 자동 제안.

---

--- 여기부터 복사 ---

# Android XML Previewer 플러그인 — Week 1 Day 2 이어하기

## 세션 컨텍스트 (반드시 먼저 읽어주세요)

본 세션은 **이전 W1D1 세션의 연속**입니다. 프로젝트 상태·의사결정·다음 할 일이
`docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/` 폴더 아래 3개 파일에 전부
기록되어 있습니다. 먼저 아래 3개를 **반드시 순서대로** 읽고 진행해주세요:

1. `docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/handoff.md` ← 최우선
2. `docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/session-log.md` 의 "Why" 섹션
3. `docs/MILESTONES.md` (Week 1-6 게이트) + `docs/W1D1-PAIR-REVIEW.md`

## canonical 문서 우선순위 (충돌 시)

`docs/plan/08-integration-reconciliation.md` > `07` > `06` > `04` > `02` > `05` > `03`(HISTORICAL) > `01`(일부 supersed) > `00`

구현 중 plan 의 산술/논리 오류 발견 시 → `08 §7 Post-Execution Errata` 에 sub-item 추가.

## 오늘 할 일 — 우선순위 순

1. **환경 확인**: `./server/gradlew -p server build` 녹색 / `echo $ANDROID_HOME` / `which adb`
2. **W1D2-R1**: `fixture/sample-app` 의 `assembleDebug` 실빌드. 결과 APK 를
   `server/libs/fixture-app-debug.apk` 로 보관. Android SDK 필수.
3. **W1D3-R2**: Paparazzi prebuilts 에서 layoutlib-dist 번들 다운로드 +
   `server/libs/layoutlib-dist/android-34/` 채우기 + 각 파일 sha256 을
   `THIRD_PARTY_NOTICES.md §1.1` 부록에 기록.
4. **W1D4-R3**: `LayoutlibBootstrap` 을 사용한 `Bridge.init` reflection smoke test
   (`@Tag("integration")` 로 분리).
5. **W1D5-R4** (**Week 1 exit criterion**): `:http-server` Ktor Netty + `GET /preview` +
   `respondBytesWriter` 기반 minimal SSE(`render_complete` 단일). `viewer/index.html` +
   `viewer/app.js`. activity_basic.xml PNG 를 브라우저 `localhost:7321` 에 표시.

## 필수 원칙 (CLAUDE.md 발췌 요약)

- **페어 규칙**: 마일스톤 게이트 검증은 Codex xhigh + Claude 서브에이전트 1:1 병렬 독립 리뷰.
  서로의 출력을 보지 못한 상태에서 각자 판단 → convergence/divergence 분석.
  Codex 가 sandbox 오류로 막히면 Claude 로 대체 금지 (페어 의미 상실). sandbox 회피 프롬프트로 재시도.
- **Codex tier**: 항상 `gpt-5-xhigh` (또는 현재 최고 reasoning tier) 사용, no time limit, maximum depth.
- **작업 추적**: `TaskList` 로 현재 task 상태 먼저 확인 (W1D2-R1 = task #14, 이후 #15-#18 순).
- **커밋 정책**: 사용자가 명시적으로 요청하지 않으면 git commit 금지. force-push·amend·destructive 금지.
- **`.gitignore` 정책**: `git add -f` 차단됨 (PreToolUse hook). 무시된 파일은 추적하지 말 것.
- **스킬 사용**: `session-work-log-protocol` 로 이번 세션도 work_log 기록. gstack 류는 **사용자 명시 요청 시만**.

## 이미 해결된 landmines (재발 방지)

- `UnrenderableReason` 은 **19개** (plan 의 "17" 은 08 §7.1 에서 교정됨).
- `RenderKey` 는 **21 필드** (plan 의 "17-input" 은 08 §7.4 에서 교정됨).
- `buildSrc/build.gradle.kts` 의 `val kotlinVersion` 과 `gradle/libs.versions.toml:kotlin` 은
  **동시 수정** 필요 (drift guard 가 불일치 시 configuration phase 에서 실패).
- Kotlin 백틱 함수명에 `§` 사용 불가 (`section` 으로 치환).
- Ktor 2.3.11 엔 `ktor-server-sse` 없음 → `respondBytesWriter` 로 직접 구현.
- AGP 8.5+ 는 `kotlinOptions {}` 제거. 대체: `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`.

## 시작

1. `TaskList` 로 현재 task 상태 확인.
2. handoff.md 읽기.
3. Android SDK 확인 후 W1D2-R1 부터 진행.
4. Week 1 전체가 끝나면(W1D5 완료 후) W1-END-GATE Codex+Claude 페어 리뷰 실행.
5. 이 세션의 work_log 도 `docs/work_log/YYYY-MM-DD_<slug>/` 에 기록.

오늘 세션이 Week 1 을 exit 하지 못하면 `06 §6 Escape Hatch` 에 따라 상태 기록 후 Week 2 로 이관.
어떤 경우에도 "delay" 는 선택지가 아님 (08 §1.5 canonical — 6주 fixed, 85% 미달이면 v1.0-rc).

--- 여기까지 복사 ---

---

## 프롬프트를 짧게 쓰고 싶을 때 (minimal 버전)

복붙 용도로 아래 minimal 버전도 가능 — 단 Claude 가 알아서 handoff 파일을 찾도록 신뢰 기반:

--- minimal 시작 ---

`docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/handoff.md` 를 먼저 읽고,
거기 적힌 대로 W1D2-R1 (fixture assembleDebug 실빌드) 부터 이어서 진행해주세요.

canonical 문서 우선순위는 `docs/plan/08` > 07 > 06 이며,
구현 중 plan 오류 발견 시 `08 §7 Post-Execution Errata` 에 추가합니다.
마일스톤 검증은 Codex xhigh + Claude 페어 리뷰 필수 (CLAUDE.md 규칙).

--- minimal 끝 ---

---

## 체크리스트 (다음 세션 시작 시 Claude 에게)

다음 세션의 Claude 가 **본 프롬프트를 받은 직후** 실행해야 하는 것:

- [ ] `TaskList` 호출 → 현재 18개 task 중 #14-#18 이 pending 인지 확인
- [ ] `docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/handoff.md` 전체 읽기
- [ ] `./server/gradlew -p server build` 실행해 환경 회복 확인 (BUILD SUCCESSFUL + 21 tests PASSED)
- [ ] `echo $ANDROID_HOME` / `which adb` 로 Android SDK 확인
- [ ] SDK 있음 → W1D2-R1 시작 / SDK 없음 → 사용자에게 설치 요청 또는 W1D5-R4 로 스킵 제안
