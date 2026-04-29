# 로드맵 — v1 / v1.5 / v2

> **⚠️ 상태**: **HISTORICAL / SUPERSEDED** by `06 §6` (6주 개정 roadmap) + `08 §1` (스케줄 conflict resolution).
> **이 문서는 참고용이며**, 실행 계획은 `06 §6` 와 `08 §1, §5` 를 따른다.
> 본 문서의 "4주" 표현은 원래 계획(옵션 α 채택 전)의 기록이다. canonical 기간은 **6주**.
> **원 스코프**: 엔지니어 1명 기준 주차별 계획. 실제 실행 시 팀 규모와 병렬성에 맞춰 조정.

---

## 1. v1 — 4주 빌드 계획

v1의 목표: **"평균적인 Android 앱 프로젝트의 res/layout/*.xml을 85% 렌더"**. Approach B 전체 구현.

### Week 1 — Foundation + L1 (layoutlib)
**월-화 (D1-D2)**: 플러그인 스캐폴딩
- `android-xml-previewer/` 리포 초기화
- `.claude-plugin/plugin.json`, `skills/preview-layout/SKILL.md`, `commands/preview-xml.md`, `.mcp.json` 작성
- Gradle 프로젝트 초기화 (`server/`), Kotlin + Netty(or Ktor) 템플릿
- MCP stdio 핸들러 skeleton (`mcp__axp-server__render_layout`가 "hello" 반환)

**수-목 (D3-D4)**: layoutlib 통합 skeleton
- `server/libs/layoutlib-android-34.jar` 다운로드/라이선스 검토
- Google `layoutlib-api/sample/Main.java` 참조하여 Kotlin 포팅
- 하드코드된 최소 XML (`<TextView text="Hello"/>`) → PNG 저장 성공

**금 (D5)**: 렌더 결과 HTTP 서빙
- `PreviewHttpServer.kt` Ktor/Netty 셋업
- `GET /preview` 엔드포인트 → 최근 PNG
- `viewer/index.html` 최소 버전 (img 태그만)
- 브라우저에서 localhost:7321 열었을 때 TextView PNG 보이면 **Week 1 exit criterion**

**Go/No-Go 체크**:
- [ ] `java -jar axp-server.jar` 실행 시 MCP 서버가 10초 내 HTTP + stdio 모두 응답
- [ ] layoutlib으로 최소 XML 1개 렌더 성공
- [ ] 브라우저에서 PNG 표시

### Week 2 — L2 (aapt2) + FileWatcher + SSE
**월-화 (D6-D7)**: aapt2 통합
- `ResourceCompiler.kt` — `aapt2 compile` + `aapt2 link` wrapper
- `.axp-cache/resources/compiled.arsc` 생성/재사용
- layoutlib에 compiled resource table 주입 → `@string/`, `@color/`, `@drawable/` 해결

**수-목 (D8-D9)**: FileWatcher + 증분 렌더
- `LayoutFileWatcher.kt` — Java WatchService, debounce 300ms
- `res/values/*` 변경 → aapt2 재컴파일 + 캐시 전체 무효화
- `res/layout/foo.xml` 변경 → 해당 레이아웃만 재렌더

**금 (D10)**: SSE hot-reload
- `SseBroadcaster.kt` — `GET /api/events`
- `viewer/app.js`에서 `EventSource` 구독 + 이미지 갱신
- 실 Android 프로젝트 하나에서 XML 수정 → 1.5초 내 브라우저 갱신 확인

**Go/No-Go 체크**:
- [ ] 실 Android 프로젝트(AOSP 스타일 샘플) 1개 checkout 후 activity_main.xml 렌더
- [ ] `@string/app_name` 값이 PNG에 올바르게 표시
- [ ] XML 편집 후 브라우저 자동 갱신 (manual F5 불필요)

### Week 3 — L3 (에뮬레이터 + harness APK)
**월-화 (D11-D12)**: harness APK 개발
- 별도 Gradle 프로젝트 `harness-apk/` — AXPHarnessActivity
- Intent으로 XML 경로 받기 → inflate → bitmap → 파일 저장 → finish
- 로컬에서 Android Studio로 빌드 및 수동 테스트 (Studio 사용 가능, 이건 빌드 도구로서의 사용)
- 결과 APK를 `server/libs/harness.apk`로 커밋

**수 (D13)**: AVD 자동화
- `EmulatorRenderer.kt` — adb, emulator 바이너리 자동 탐색
- AVD 목록 조회 + 없으면 자동 생성 가이드
- snapshot 부팅 + harness APK 설치 자동화

**목 (D14)**: 렌더 루프
- XML push → am start → 결과 pull → PNG
- 타임아웃 + 재시도 + 에러 메시지

**금 (D15)**: L1 ↔ L3 Dispatcher
- `RenderDispatcher.kt` — escalation 룰 테이블 구현
- 실제 custom view 포함 XML에서 L1 실패 → L3 성공 검증

**Go/No-Go 체크**:
- [ ] AVD 없이도 L1만으로 동작 (degrade 경로)
- [ ] warm AVD에서 L3 렌더 4초 이내
- [ ] custom view 1개 (예: `com.google.android.material.card.MaterialCardView`)가 L3에서 렌더

**⚠️ Escape Hatch 체크**: Week 3 금요일 17:00에 L3가 여전히 불안정하다면 v1.0을 A로 출시 결정. Week 4는 A 마감에 사용.

### Week 4 — UX 마감 + 테스트 + 릴리스
**월 (D16)**: 브라우저 뷰어 완성
- 디바이스 드롭다운, 테마 토글
- `Unrenderable` 카드 UI
- `Fallback mode` 배지
- 파일 이름/경로 표시

**화 (D17)**: 실패 경로 강화
- AVD 없음 안내
- Android SDK 없음 안내
- JVM 11 미만 안내
- 포트 7321 충돌 자동 탐색
- Graceful shutdown (MCP 세션 종료 시 AVD 정지)

**수 (D18)**: 커버리지 검증
- 공개 Android OSS 프로젝트 10개 샘플링 (ex: Signal, Bitwarden, Simple Mobile Tools, NewPipe, Element...)
- 각 프로젝트에서 activity_*.xml, fragment_*.xml 각 3개씩 렌더
- 성공/실패 분류 → 85% 달성 여부 측정
- 미달 시: 원인 분석 후 L1/L3 개선 또는 v1 범위 조정

**목 (D19)**: 문서화
- `README.md` (설치 / 첫 사용 / 스크린샷)
- `docs/INSTALL.md`
- `docs/LIMITATIONS.md` (v1에서 지원 안 되는 것 명시)
- `docs/TROUBLESHOOTING.md`

**금 (D20)**: 패키징 + 릴리스
- `scripts/package-release.sh` — JAR + APK + layoutlib JAR 합본 ZIP
- GitHub Release 초안 작성
- Claude Code 플러그인 마켓플레이스 제출 패키지 준비
- **v1.0.0 태그 cut**

**Week 4 exit criteria**:
- [ ] 공개 프로젝트 샘플링에서 85% 달성 (또는 달성 경로 기록)
- [ ] 처음부터 설치하는 사용자가 5분 안에 첫 렌더 성공
- [ ] 메모리 RSS < 6GB (warm AVD 포함)

---

## 2. v1.5 (v1 이후 4-6주)

v1 피드백 수집 후 가장 불만이 많은 축에 집중.

### 우선순위 후보
1. **data-binding 부분 지원** (`@{...}` literal 값) — 런타임 eval 없이 정적 추론
2. **Compose `ComposeView` 호스트** — harness APK 내에서 `setContent { AndroidView(...) }` 래핑
3. **Multi-device 병렬 렌더** — 3개 디바이스 동시 렌더 + 그리드 뷰어
4. **리소스 그래프 기반 invalidation** — `colors.xml` 변경 시 모든 레이아웃이 아닌 "의존 레이아웃만" 재렌더
5. **RTL 프리뷰 토글**
6. **렌더 결과 PNG CLI export** — CI에서 `axp render foo.xml --out foo.png`

### v1.5 성공 지표
- 커버리지 85% → 90%
- 월간 설치 500+
- "Android Studio 완전 대체" 인용 3건 이상

---

## 3. v2 (v1.5 이후)

- **warm-pool 강화**: AVD 다중 인스턴스 + 자동 snapshot 관리
- **Fragment 전환 프리뷰**: Navigation XML 파싱 → 흐름 다이어그램 + 각 목적지 프리뷰
- **Gradle variant 인식**: `debug`/`release`/`flavorA` 등 variant별 프리뷰
- **원격 렌더 모드**: `axp-server`를 별도 머신에서 돌리고 브라우저는 로컬
- **VS Code 확장**: Claude Code 생태계 밖 사용자 흡수
- **IntelliJ/Android Studio 플러그인**: 역으로 Studio 내부 사용자에게 "우리 뷰어가 더 빠르다"
- **CI 통합**: GitHub Actions `axp-render-action` — PR에 XML 변경 시 스크린샷 자동 첨부

---

## 4. 스케줄 요약표

| 기간 | 마일스톤 | 주 활동 | Exit Criterion |
|------|---------|---------|----------------|
| W1 | Foundation | plugin.json + MCP + L1 layoutlib | 최소 XML → PNG in 브라우저 |
| W2 | 리소스 + 자동갱신 | aapt2 + FileWatcher + SSE | 실 프로젝트 activity_main 렌더, XML 수정→자동갱신 |
| W3 | L3 에뮬레이터 | harness APK + AVD + Dispatcher | custom view 포함 XML L3 성공 |
| W4 | 마감 | 뷰어 UX + 문서 + 릴리스 | 샘플링 85% + v1.0 태그 |
| W5-10 | v1.5 | 피드백 기반 개선 | 커버리지 90% + 월 설치 500 |
| W11+ | v2 | 생태계 확장 | VS Code/IntelliJ + CI 통합 |

---

## 5. 의존성과 블로커

### 5.1 외부 의존 불확실성
- **layoutlib JAR 라이선스**: Android SDK 재배포 조건 재확인 필요 (v1 Week 1에 법무 체크포인트)
- **Claude Code 플러그인 마켓플레이스 가이드라인**: 2026-04 기준 최신 리뷰 기준 확인
- **Android SDK 버전 표류**: android-34 → android-35 → android-36 확산 시점에 v1.x 호환성 재검증

### 5.2 팀 규모별 병렬화
1명 → 위 일정 그대로
2명 → Week 1: 두 명 동시 스캐폴딩 + layoutlib, Week 2-3: 한 명은 L2+FileWatcher, 다른 한 명은 L3+harness APK → v1을 3주로 단축 가능
3명+ → v1과 v1.5 항목 병렬

---

## 6. 변경 로그
- 2026-04-22: 초안 작성 + escape hatch 체크포인트 추가.
