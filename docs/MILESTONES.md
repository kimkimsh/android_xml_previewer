# Week Gate 체크포인트

> **근거**: 06 §6 (개정 6주 roadmap) + 08 §1 (스케줄 conflict resolution) + 08 §5 (W1D1 체크리스트)
> **용도**: 각 주차의 Go/No-Go 게이트와 escape hatch 기준을 한 곳에서 관리. GitHub Projects 대체.
> **업데이트 주기**: 매 주차 금요일 17:00 (사용자 타임존 KST)
>
> **canonical 우선순위**: `08` > `07` > `06` > `03`(historical). 본 문서는 이 우선순위를 존중.

---

## 전체 일정 요약

| 주차 | 기간 | 마일스톤 | 핵심 산출물 | Go/No-Go |
|------|------|---------|-------------|----------|
| W1   | D1-D5   | Foundation + L1 | MCP 스캐폴드 + layoutlib 첫 렌더 | 최소 XML → PNG 브라우저 표시 |
| W2   | D6-D10  | L2 + FileWatcher + minimal SSE | aapt2 + merged res + auto-refresh | XML 수정 → 1.5s 내 자동 갱신 |
| W3   | D11-D15 | L3 Basic (classpath-agnostic) | harness APK + AVD warm-pool | 기본 Material 레이아웃 L3 렌더 |
| W4   | D16-D20 | L3 Classpath-aware + full SSE | DexClassLoader + 10 SSE events | 커스텀 View L3 + 85% 예상 |
| W5   | D21-D25 | UX + Error Catalog + CLI | Viewer UI + axprev render 커맨드 | 모든 실패 경로 수동 테스트 통과 |
| W6   | D26-D30 | Golden Corpus 검증 + 문서 + 릴리스 | 13개 OSS 프로젝트 acceptance | v1.0.0 태그 (또는 v1.0-rc) |

---

## Week 1 — Foundation + L1 (D1-D5)

### W1D1 체크리스트 (08 §5)
- [x] 0. 라이선스 체크 (THIRD_PARTY_NOTICES.md + docs/licenses/)
- [x] 1. README "4주" → 6주 superseded 배너
- [x] 2. 08 canonical 결정 내용 읽기
- [x] 3. Worker IPC: Unix socket + JSON line-framed 명세 (:protocol/worker)
- [x] 4. 버전 pin (gradle/libs.versions.toml: JVM 17, Kotlin 1.9.23, Gradle 8.7, Ktor 2.3.11)
- [x] 5. Gradle multi-module 스캐폴드 (7 모듈) + 전체 build 성공 + 테스트 통과
- [ ] 6. Tiny fixture Android app (activity_basic.xml + activity_custom_view.xml + DummyView.kt) ← **W1D2 로 이관**
- [ ] 7. L1 spike: Bridge.init + 첫 렌더 ← **W1D2-D4**
- [x] 8. Contract tests (MCP envelope + UnrenderableReason snapshot + Worker IPC)
- [x] 9. Week 1-6 게이트 문서 (본 파일)

### W1D2-D5 남은 작업
- D2: fixture 앱 Gradle 구조 (sample-app) + plugin manifests 검증
- D3-D4: `:layoutlib-worker` 의 LayoutlibRenderer 실제 구현 (Bridge.init)
- D5: `:http-server` `GET /preview` 엔드포인트 + viewer/index.html 최소 버전 → 브라우저에 PNG 표시

### W1 Go/No-Go (06 §6 Week 1 exit + 08 §7.7 W1-END 페어 divergence resolution → W2D6 §7.7.1 3a/3b)
- [x] `java -jar axp-server.jar` 10s 내 HTTP 응답 + viewer 배너 + MCP stdio JSON-RPC (W2 D6 완료, 08 §7.7 item 1)
- [x] layoutlib 으로 최소 XML 1개 렌더 — **3a partial** : Bridge.init 실제 성공 경로 증명 (Tier2 PASS) + LayoutlibRenderer 연결. **3b carry to W2 D7**: 실제 RenderSession.render() pixel (08 §7.7.1)
- [x] 브라우저에서 PNG 표시 (W1D5-R4 curl end-to-end 통과 + W2D6 LayoutlibRenderer 경유)

### Escape Hatch (08 §7.6 + §7.7 + §7.7.1)
원래 "없음" 이었으나 W1-END 페어 리뷰에서 divergence 발생 → 08 §7.7 이 canonical 해소:
- stdio JSON-RPC + real layoutlib render 두 item 을 W2 D6 blocking acceptance 로 재배치.
- W2D6 플랜 페어 리뷰 (`docs/W2D6-PLAN-PAIR-REVIEW.md`) 의 divergence 해소를 위해 §7.7 item 3 를 3a/3b 분할 (§7.7.1): 3a (Bridge.init + infra) = W2D6 closure, 3b (RenderSession) = W2D7 carry.
- Release gate (08 §1.5) 영향 없음.

---

## Week 2 — L2 + FileWatcher + minimal SSE (D6-D10)

**핵심 개정 (08 §1.2)**: Week 2 D10 은 **minimal SSE** (`render_complete` 단일 이벤트)만.
full 10-event taxonomy 는 Week 4 D16-D17.

### 주요 작업 (07 §2.1, 01 §6, 06 §6)
- D6-D7: `MergedResourceResolver` (AGP `mergeResources` 출력 우선 + manual AAR 병합 금지 - 08 §2.4)
- D8-D9: `LayoutFileWatcher` (Java WatchService, debounce 300ms, values→전체 invalidate, layout→단일)
- D10: minimal SSE (`GET /api/events` → `render_complete`만), viewer `EventSource` 구독

### Go/No-Go
- [ ] 실 Android 프로젝트(fixture sample-app) `@string/app_name` 정확 표시
- [ ] XML 편집 → 1.5s 내 브라우저 자동 갱신

### Escape Hatch
없음 (W2 실패는 v1 불가).

---

## Week 3 — L3 Basic / classpath-agnostic (D11-D15)

**08 §1.1 canonical**: Week 3 은 harness APK + AVD warm-pool 만. **DexClassLoader 는 Week 4**.
이 분할이 리스크 분산 + escape hatch 발동 기회의 근거.

### 주요 작업 (06 §6 Week 3)
- D11-D12: harness APK 개발 (별도 `harness-apk/` AGP 프로젝트) — **plain Activity** (07 §3.3 권고, AppCompatActivity 아님)
- D13: `AvdController`, adb/emulator 자동 탐색, snapshot boot
- D14: XML push → am start → PNG pull 루프
- D15: `RenderDispatcher` L1↔L3 escalation 룰 (06 §5 publish gate 포함)

### Go/No-Go
- [ ] AVD 없이 L1 only degrade 동작 (~75% 예상)
- [ ] warm AVD 에서 L3 4s 이내
- [ ] `MaterialCardView` 등 androidx custom view L3 렌더

### Escape Hatch (08 §1.5)
- L3 basic 이 동작 안 하면 Week 4 에서 β 강등 (커버리지 주장 75% 로 수정) — "delay" 없음

---

## Week 4 — L3 Classpath-aware + full SSE (D16-D20)

### 주요 작업 (07 §3 + 08 §1.1, §1.2)
- D16-D17: full SSE 10-event taxonomy (07 §4.3) + browser state machine (07 §4.5)
- D16: `AndroidProjectLocator.findAppApk()` + staleness check + split APK 대응 (08 §2.2)
- D17: `createPackageContext` + `AppRenderContext` (07 §3.1 + 08 §2.3)
- D18: `AppDexFactory2` (07 §3.2) + `ChildFirstDexClassLoader` (07 §3.3)
- D18: `DexPreflight` (07 §3.4) — appPkg 필터 제거, library class 도 검사 (08 §2.5)
- D19: `aapt2 link -I app-debug.apk` preview resource APK 빌드 (08 §2.1: ResourcesLoader 우선)
- D19: flock 포트 코디네이션 (07 §4.6) + CSRF + AXP_TOKEN (07 §4.7)
- D20: LruCache 메모리 적응(07 §3.6), theme 선택 로직 (08 §3.4)

### Go/No-Go (06 §6 Week 4 exit)
- [ ] `MaterialCardView` 등 androidx custom view L3 렌더
- [ ] `com.acme.FancyChart` 등 사용자 정의 View L3 렌더
- [ ] app APK 삭제 상태에서 `L3_NO_APP_APK_BUILT` + remediation 안내

### Escape Hatch
- classpath-aware 동작 안 하면 β 강등 (커버리지 주장 75%)
- release APK 사용자는 `L3_APK_NOT_DEBUGGABLE` 안내 (v1.1 에서 mapping.txt 지원)

---

## Week 5 — UX + Error Catalog + CLI (D21-D25)

### 주요 작업 (06 §6 + 07 §5)
- D21: `UnrenderableReason` 17(실제 19) enum 카드 UI, `docs/TROUBLESHOOTING.md` 완성
- D22: `axprev render <file> --device=phone_normal --out foo.png` (F5 — v1 필수)
- D23: SSH/headless 안내 (`axprev serve --no-open-browser --host=0.0.0.0`) + port forwarding 힌트
- D24: Viewer UI — 디바이스 드롭다운 / 테마 토글 / 에러 배지 / fallback mode 표시
- D25: 5-tier coverage rubric 자동 평가기(07 §5.2), odiff + shadow mask(07 §5.3)

### Go/No-Go
- [ ] 모든 실패 경로 수동 테스트 통과 (설치/권한/포트/AVD/SDK 각 케이스)
- [ ] odiff 기반 DSSIM ≤ 0.02 (SSIM ≥ 0.985) 도달 샘플 확보

---

## Week 6 — Acceptance + 문서 + 릴리스 (D26-D30)

### 주요 작업 (07 §5.8 + 06 §6)
- D23(선행): `corpus/pins.yaml` 13개 프로젝트 commit SHA pin + 선정된 ≤8 layouts 파일 경로 (08 §3.6)
- D26-D27: Golden corpus acceptance run (`./gradlew corpusRender --all --repeats=3`)
- D28: 문서 surface 완성 (README/INSTALL/LIMITATIONS/TROUBLESHOOTING/CONTRIBUTING/ARCHITECTURE/CHANGELOG/SECURITY/PRIVACY/THIRD_PARTY_NOTICES/UNINSTALL/FAQ) — F6
- D29: 패키징 + 플러그인 마켓플레이스 제출 준비
- D30: v1.0.0 태그 cut

### 08 §1.5 Release Gate (delay 없음)
| 조건 | 결과 |
|------|------|
| T1+T2+T3 ≥ 85% AND T6 = 0% AND 모든 카테고리 ≥ 70% | **v1.0.0 정식 릴리스** |
| 80-84% OR 카테고리 60-70% | **v1.0-rc** + 2주 public beta, 주장을 측정값으로 수정 |
| 70-79% | **v1.0-rc** + 4주 public beta + 근본 원인 보고서 병행 |
| <70% OR T6 > 0% | **v1.0-preview** 태그 (기능 완성될 때까지 unreleased) |

### Ongoing telemetry (07 §5.8)
- `report.json` → `goldens/history.jsonl` → nightly `docs/CORPUS_DASHBOARD.md`
- 1pp 회귀 → GitHub issue 자동 오픈

---

## 변경 로그

| 일자 | 변경 | 비고 |
|------|------|------|
| 2026-04-22 | 초안 — W1D1 Task #9 | 08 §1 + §5 + 06 §6 통합 |
