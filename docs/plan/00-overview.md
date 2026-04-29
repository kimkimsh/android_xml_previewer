# Android XML Previewer — 기획 개요

> **상태**: DRAFT (Phase 5, 2026-04-22)
> **작성 방식**: `/office-hours` → Codex xhigh + Claude 서브에이전트 1:1 페어 → 사용자 절충 결정
> **범위**: Claude Code 플러그인 v1 기획 (v1.5 / v2 로드맵 포함)
> **산출 문서**:
> - `00-overview.md` (이 문서) — 비전 / 문제 / 사용자 / UX
> - `01-architecture.md` — 플러그인 구조, 렌더 파이프라인, 서버 설계
> - `02-alternatives-and-decision.md` — 접근법 3종 비교 + Codex 페어 증거
> - `03-roadmap.md` — 주차별 v1 빌드 계획 + v1.5 / v2
> - `04-open-questions-and-risks.md` — 미해결 질문, 리스크, 테스트 전략

---

## 1. 한 문장 비전

> **Android 개발자가 Claude Code 터미널에서 XML 레이아웃을 수정하는 순간, 별도의 IDE 전환 없이 브라우저에 실제 앱과 같은 프리뷰가 즉시 갱신되도록 한다.**

---

## 2. 문제 (Problem Statement)

### 2.1 현재 워크플로우의 고통

```
┌──────────────────┐    수정     ┌──────────────────┐
│ Claude Code에서   │ ─────────▶ │ Android Studio   │
│ XML 편집         │             │ Design 탭 열기   │
└──────────────────┘             └──────────────────┘
         ▲                                │
         │                                │ 수초~수십초
         │                                │ Gradle sync
         │      ┌──────────────────┐     │
         └──────│ 다시 CC로 alt-tab │◀────┘
                └──────────────────┘
```

- **IDE 2개 동시 운영**: RAM 8~16GB 추가 소비, alt-tab 오버헤드
- **Gradle sync 대기**: 프로젝트 스케일에 따라 10~60초
- **에이전트 드리븐 워크플로우와 단절**: Claude가 XML을 자동으로 편집할 때, 사람이 매번 Android Studio로 가서 결과 확인 필요
- **CI 환경에서 불가**: Android Studio 없이는 XML 렌더 확인 불가 → PR 리뷰어가 XML diff만 보고 판단

### 2.2 근본 원인

Android 레이아웃 XML은 **단순한 마크업이 아니다**. 실제 결과를 보려면:
- `layoutlib`이 Android 프레임워크의 View 시스템을 JVM에서 에뮬레이트
- `aapt2`가 프로젝트 리소스(`@string`, `@color`, `@drawable`, 테마)를 바이너리 테이블로 컴파일
- 테마/스타일 상속, AppCompat delegate, Material 3 토큰이 모두 resolved 되어야 함

이 모든 것을 지금까지는 Android Studio 안에 가둬놓았다. 터미널 워크플로우로 꺼내올 수 있다.

---

## 3. 타겟 사용자

### 3.1 Primary Persona

**"에이전트 드리븐 Android 개발자"**
- Claude Code를 주력 IDE로 사용
- Claude가 XML을 자동 생성/수정하는 에이전틱 워크플로우에서 시각 피드백이 필요
- 한국/영어 양쪽 커뮤니티
- Linux 데스크톱 또는 원격 dev 머신에서 작업

### 3.2 Secondary Personas

- **CI / PR 리뷰 도구 사용자**: GitHub PR에서 "이 XML 수정이 실제로 어떻게 보이는지" 자동 스크린샷 첨부
- **Android Studio 기피 개발자**: "IDE가 무겁다", "Vim/neovim으로 XML 직접 수정한다" 같은 사용자
- **디자인 시스템 관리자**: 여러 테마/다크모드/디바이스 크기를 한번에 비교 렌더

### 3.3 Non-Goals (범위 외)

- 레이아웃 편집 UI 제공 (WYSIWYG) — 사용자는 텍스트로 XML 편집, 플러그인은 오직 렌더
- Compose 프리뷰 — v1 외 (v1.5 이후 `ComposeView` 호스트 간접 지원 검토)
- 실행 중 상태 전환 / 버튼 클릭 시뮬레이션 — 정적 스크린샷만
- 내비게이션 그래프 / Fragment 플로우 — 단일 레이아웃 1장만

---

## 4. UX 시나리오 (Vignettes)

### 4.1 Vignette A — Happy Path (에이전트 드리븐)

```
[사용자]
  "이 activity_main.xml을 다크모드 대비 좀 높여줘"

[Claude Code]
  Read activity_main.xml → Edit activity_main.xml (색상 변경)

[플러그인 filesystem watcher]
  res/layout/activity_main.xml 저장 감지
  → MCP 서버에 re-render 요청

[MCP 서버]
  1. aapt2 compile (cache hit) — 50ms
  2. layoutlib 렌더 — 800ms
  3. PNG → HTTP server의 SSE broadcast

[브라우저 (localhost:7321)]
  자동 새로고침 — "changed 1s ago"

[사용자]
  브라우저에서 "아 이게 더 좋네" 확인, Claude와 대화 계속
```

**총 지연**: 저장 → 브라우저 갱신 ≤ 1.5초 (warm cache)

### 4.2 Vignette B — Custom View Graceful Degradation

```
[사용자]
  커스텀 View `com.acme.FancyChart`가 포함된 layout 편집

[플러그인]
  1. layoutlib 시도 → ClassNotFoundException
  2. 에뮬레이터 L3 fallback 시도 → 성공 (2.3초)
  3. PNG + "rendered via emulator fallback" 배지

[브라우저]
  정상 프리뷰 + 하단 배지: "⚡ fallback mode: emulator (2.3s)"
```

### 4.3 Vignette C — Unrenderable Graceful Failure

```
[사용자]
  `@{viewModel.liveData}` data-binding 표현식이 포함된 layout 편집
  (커스텀 BindingAdapter 10개 의존)

[플러그인]
  1. layoutlib → data-binding placeholder 미처리, 렌더 실패
  2. 에뮬레이터 harness APK → BindingAdapter 클래스 미포함 → 실패
  3. graceful fallback UI 호출

[브라우저]
  부분 렌더 (해결 가능한 부분) +
  상단 경고 카드:
    "❌ Unrenderable: data-binding @{viewModel.liveData} 실행 필요
     → 전체 렌더는 Android Studio Design 탭 사용 권장
     → 구조적 검증은 이 프리뷰로 가능"
```

이 "실패를 친절하게" 하는 것이 **프로젝트의 품질 지표**이다.

### 4.4 Vignette D — 다중 디바이스 비교

```
[사용자]
  브라우저에서 디바이스 드롭다운을 [phone_small, foldable, tablet]로 멀티선택

[플러그인]
  3개 디바이스 설정으로 병렬 렌더 (약 1.5초)

[브라우저]
  3열 그리드로 동시 표시 — 어느 디바이스에서 레이아웃이 깨지는지 한눈에
```

---

## 5. 성공 기준 (Success Criteria)

### 5.1 v1 정량 지표

| 지표 | 목표 | 측정 방법 |
|------|------|----------|
| 실사용 XML 렌더 성공률 | **≥ 85%** | 공개 Android OSS 프로젝트 30개 sampling (F-Droid + GitHub trending) |
| Warm render 지연 (저장→브라우저) | **≤ 1.5초** | layoutlib 경로 |
| 에뮬레이터 fallback 지연 | **≤ 4초** | L3 warm-pool |
| 첫 실행 콜드 스타트 | ≤ 15초 | aapt2 + layoutlib JVM + (optional) AVD boot |
| 메모리 풋프린트 (L3 포함) | ≤ 6GB | RSS |
| graceful failure UI 표시율 | 100% of 실패 케이스 | "blank PNG"는 절대 금지 |

### 5.2 v1 정성 지표

- **"Android Studio 없어도 된다"는 피드백** 5건 이상 (GitHub Issue, Discord, X/Twitter)
- Claude Code 플러그인 마켓플레이스 설치 100+ within 1개월
- PR 리뷰어가 "XML 시각 diff가 처음으로 편해졌다"고 인용 1건 이상

### 5.3 v1이 실패하는 시나리오 (Anti-goals)

- 평균 렌더 지연 3초 초과 → Android Studio 대비 장점 소실
- 85% 커버리지 미달 → "결국 Android Studio 열어야 하네" 경험
- `blank PNG` 반환 → 사용자 신뢰 붕괴
- Windows/macOS에서 첫 설치 실패율 30% 초과 → Linux-only 플러그인으로 인식

---

## 6. 7개 확정 전제 (Phase 3 + Codex 페어 + 사용자 절충 결정)

> 이 전제들은 **Codex xhigh + Claude 서브에이전트 1:1 페어**로 독립 검증을 거쳤다.
> 사용자는 커버리지 목표만 85%로 절충했고, 나머지는 수렴한 권고 그대로 채택.
> 상세 비교 표는 `02-alternatives-and-decision.md` 참조.

| # | 전제 | 검증 상태 |
|---|------|----------|
| 1 | v1 산출물 = Claude Code 플러그인 1종. Linux-first, macOS/Windows best-effort. | 페어 수용 |
| 2 | 렌더 파이프라인 = L1 layoutlib + L2 aapt2 + L3 에뮬레이터 harness APK fallback. Paparazzi/Roborazzi 제외 (Gradle 락인). | Codex: 우려, Claude: 제거 권고 → 사용자 유지 결정 |
| 3 | 플러그인 구조 = skill + .mcp.json + Kotlin/JVM MCP 서버 + HTTP+SSE 라이브 서버 + **filesystem watcher** (PostToolUse hook 대신). | 페어 강한 수렴 |
| 4 | "미리보기" 범위 = 단일 레이아웃 → 1장 렌더. Fragment/Navigation/state 변화 제외. | 페어 수용 |
| 5 | **커버리지 목표 = v1 85%** (사용자 절충). Material/AppCompat/ConstraintLayout/vector/drawable = L1 전부, custom view/data-binding 일부 = L3, 나머지 ~15% = graceful fail. | 원안 99% → 페어 75% 권고 → 사용자 85% 절충 |
| 6 | 환경 = 프로젝트 루트 자동 감지 + `--project` 플래그. `.axp-cache/`에 aapt2 증분 캐시. | 페어 보강 (invalidation 전략 필요 - 04 문서) |
| 7 | 배포 = GitHub OSS Apache 2.0 + Claude Code 플러그인 마켓플레이스. **layoutlib JAR은 플러그인이 번들**(android-34 pinned). JVM 11+ + Android SDK 필수 (AVD는 L3 사용 시). | Claude 제안, Codex 지지 |

---

## 7. Why now? (왜 지금인가)

- **Claude Code 플러그인 시스템 성숙**: 2026년 현재 `.claude-plugin/plugin.json`, skills, hooks, .mcp.json이 안정화. MCP 서버로 고성능 backend를 번들할 수 있음.
- **layoutlib의 standalone 사용성 검증**: Google의 공식 `layoutlib-api/sample/Main.java`가 가이드로 존재. Paparazzi는 Gradle 의존이지만 내부에서 쓰는 layoutlib 호출 방식은 레퍼런스로 읽을 수 있음.
- **에이전트 드리븐 개발 확산**: Claude가 Android 프로젝트를 직접 수정하는 워크플로우가 이미 현실. 시각 피드백 루프의 부재가 실제 병목으로 관찰됨.
- **Android Studio의 프리뷰 개선 둔화**: 2024-2026 동안 Preview rendering은 의미 있는 속도 개선이 없었음. JVM 기반 standalone 렌더가 Android Studio보다 빠를 수 있는 공간이 존재.

---

## 8. 다음 단계

1. `01-architecture.md` — 구체적 모듈 설계
2. `02-alternatives-and-decision.md` — 3가지 접근법 비교 + Codex 페어 원문
3. `03-roadmap.md` — 주차별 v1 빌드 계획
4. `04-open-questions-and-risks.md` — 미해결 이슈 + 리스크 레지스터
5. `/plan-eng-review` + `/plan-devex-review` 실행 (각각 Codex 페어와 함께) — 기획안 품질 최종 게이트
