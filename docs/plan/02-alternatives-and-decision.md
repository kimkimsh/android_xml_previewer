# 접근법 3종 비교 + 최종 결정

> **상태**: DRAFT
> **작성 근거**: Phase 1 리서치 (Explore 에이전트 2기 병렬) + Phase 3.5 Codex xhigh ↔ Claude 서브에이전트 1:1 페어 + 사용자 절충 판단.

---

## 1. 후보 접근법 3종

office-hours Phase 4 규칙: 최소 2, 권장 3개 접근. (A) Minimal viable, (B) Ideal architecture, (C) Creative lateral.

### Approach A — Minimal Viable: layoutlib-only
> **Codex + Claude 서브에이전트가 합의하여 권고한 안.**

**요약**: L1 (layoutlib) + L2 (aapt2)만 구현. L3 에뮬레이터 전체 제거. custom view / data-binding = graceful "unrenderable" UI.

**Effort**: S (2주, 엔지니어 1명)
**Risk**: Low
**커버리지**: ~70-75%

**Pros**:
- 최소 스코프, 빠른 v1 출시
- 메모리 풋프린트 낮음 (< 1GB)
- 실패 경로가 단순 = UX 일관성 ↑
- AVD/KVM 의존 없음 → macOS/Windows 호환성 ↑
- 페어 양쪽이 동의한 "안전한 선택"

**Cons**:
- 25-30%의 실사용 XML에서 "unrenderable" 표시 → 사용자 실망 가능성
- "Android Studio 대체"라는 서사 약화
- custom view 많은 대형 앱에서는 가치 제한적

**Reuses**:
- 리서치에서 발견한 Google layoutlib-api sample `Main.java` 구조
- aapt2 CLI (Android SDK에 포함)

---

### Approach B — Ideal: layoutlib + 에뮬레이터 harness APK fallback
> **사용자가 최종 선택한 안 (85% 커버리지 절충).**

**요약**: L1 + L2 + **L3 에뮬레이터 warm-pool + harness APK**. layoutlib 실패 시 자동 fallback. 나머지 ~15%만 graceful fail.

**Effort**: M (3-4주, 엔지니어 1명)
**Risk**: Medium
**커버리지**: ~85%

**Pros**:
- 실제 Android 프레임워크 실행 → custom view / data-binding 다수 케이스 처리 가능
- Android Studio 대비 속도 우위 (warm AVD에서 2-4초 vs Android Studio Design tab 5-10초)
- v1 출시 시점부터 "내 프로젝트도 지원한다"는 커버리지 확신
- 확장 여지 존재: harness APK에 `@BindingAdapter` 스캔, Compose interop host 등 점진 추가

**Cons**:
- AVD 필수 의존 (KVM 필요, macOS는 HAXM/Hypervisor.framework)
- 메모리 ~4-6GB (warm AVD + JVM)
- L3 경로의 버그 surface ↑ (adb 플래키, snapshot 손상, network 이슈)
- 3-4주 스코프 — 페어는 "2주"를 선호

**Reuses**:
- Paparazzi/Roborazzi의 harness 패턴 (코드는 아니지만 개념)
- Espresso 스타일 `@BindingAdapter` auto-registration
- AVD snapshot API (2020년 이후 stable)

---

### Approach C — Creative Lateral: **Emulator-only + in-process XML injection**
> **Codex가 "full Gradle harness" 언급 시 부분적으로 암시한 창의적 대안.**

**요약**: layoutlib 완전 제거. 모든 렌더를 에뮬레이터에서 harness APK로 수행. 다만 **매 렌더마다 APK 재빌드 안 함** — 상주 harness APK가 stdin/intent로 XML 문자열을 받아서 실시간 inflate.

**Effort**: M (3주, 단, harness APK 복잡도 ↑)
**Risk**: Medium-High (L3 단일 실패점 + Linux KVM 의존)
**커버리지**: ~95% (layoutlib 버그에서 자유로움)

**Pros**:
- 코드 양 ↓ (layoutlib 바인딩 코드 전체 불필요)
- 실제 Android 런타임 = 단순한 신뢰 모델
- custom view, data-binding, Compose interop, 모두 자연스럽게 동작
- AVD 설치 + snapshot만 잘 하면 커버리지 압도적

**Cons**:
- 모든 렌더가 최소 2-3초 (warm) — layoutlib의 0.5-1초 대비 **3-5배 느림**
- AVD/KVM이 **필수** — 없으면 플러그인 자체가 동작 안 함
- macOS/Windows 지원 어려움 (QEMU 호환성)
- warm-pool 실패 시 전체 플러그인 멈춤 → UX 리스크 ↑
- 페어가 명시적으로 "에뮬레이터 only는 안 된다"고 경고

**Reuses**:
- Espresso/UI Automator의 intent + screenshot 패턴
- Firebase Test Lab의 harness 모델 (참고)

---

## 2. 비교 매트릭스

| 기준 | A (layoutlib-only) | B (L1+L2+L3, 선택됨) | C (Emulator-only) |
|------|--------------------|--------------------|-------------------|
| v1 기간 | **2주** | 3-4주 | 3주 |
| v1 커버리지 | 70-75% | **85%** | 95% |
| Warm render 지연 | **0.5-1s** | 1-4s (L1 우선) | 2-4s |
| Cold start | **~3s** | ~15s (AVD 포함) | ~15s |
| 메모리 풋프린트 | **<1GB** | 4-6GB | 4-6GB |
| 실패 UX 일관성 | 높음 (단일 실패 경로) | 중간 (2개 경로) | 중간 (AVD 상태 의존) |
| OS 지원 | Linux/macOS/Win | Linux 강함, mac/Win 약함 | Linux-only 사실상 |
| 버그 표면 | 좁음 | 넓음 | 넓음 |
| 차별화 서사 | "빠르고 가볍다" | **"Studio에 가깝지만 빠르다"** | "Studio와 동일한 품질" |
| 페어 추천도 | ★★★★★ | ★★★☆☆ | ★★☆☆☆ |
| 사용자 결정 | - | **✅ 선택** | - |

---

## 3. 선택된 안: Approach B (85% 커버리지 + L3 유지)

### 3.1 사용자 결정 근거
- 원안 99% 커버리지는 페어가 강하게 반대 (classic "last 10% kills project")
- 페어 권고안 (A, 75%)은 custom view 많은 실사용 프로젝트에서 "결국 Studio 열어야 하네" 경험 유발 우려
- **85%는 절충점**: L3를 유지하되 야망을 현실화. "지원 안 되는 15%는 정직하게 unrenderable 표시"라는 계약이 UX의 일관성을 지킴

### 3.2 B를 성공시키기 위해 반드시 지켜야 할 엔지니어링 원칙

페어가 제시한 리스크를 완화하기 위한 **5가지 non-negotiable**:

1. **L3는 optional-by-policy**: AVD가 없거나 부팅 실패 시 플러그인 전체는 L1-only로 degrade. 커버리지는 떨어지지만 동작은 유지. "L3 의존적 고장"이 전체 플러그인 고장이 되면 안 됨.

2. **L3 진입을 확정적으로 결정**: L1 실패 → L3 가는 경로가 heuristic에 의존하면 안 됨. 실패 타입별 룰 테이블로 관리 (01-architecture.md §3.2 참조).

3. **warm-pool UX 피드백**: 첫 L3 호출 시 브라우저에 "⚙ emulator warming up (8s)" 진행 표시. blank PNG나 무응답은 절대 금지.

4. **deterministic cache key**: `(xml hash, resource hash, device, theme, sdk, layer)` 전체 포함. 페어가 합의한 누락 포인트.

5. **graceful Unrenderable UI**: 실패 시 "왜 실패했는지"를 평문으로. 스택 트레이스 복사 버튼. Android Studio 대안 안내.

### 3.3 B에서 잘라낸 것 (v1 외)
- 사용자 정의 AVD 이미지 선택 UI — v1은 시스템 기본 AVD 또는 자동 다운로드
- Compose `ComposeView` 호스팅 — v1.5
- Fragment 전환 프리뷰 — v2
- Navigation 그래프 전체 뷰 — v2
- 테마 에디터 (실시간 색상 조정) — out of scope (Android Studio의 책임)
- RTL 시뮬레이션 드롭다운 — v1.1에 추가
- XML diff 시각 비교 (before/after) — v1.5

### 3.4 A로 되돌아갈 수 있는 "escape hatch"
v1 개발 중에 L3 구현이 3주차에도 완성되지 않으면:
- **Escape plan**: L3를 비활성화한 채로 A (layoutlib-only)로 출시 → v1.0
- L3는 v1.1으로 출시
- 이 결정의 **go/no-go 체크포인트**: v1 Week 3 종료 시점

---

## 4. Codex xhigh + Claude 서브에이전트 페어 원문 요약

### 4.1 페어 구성 (CLAUDE.md 규칙 준수)
- **Codex**: `codex exec`, `model_reasoning_effort=xhigh`, read-only, 70초 runtime
- **Claude 서브에이전트**: `general-purpose` agent, 같은 brief, 50초 runtime
- **격리**: 서로의 출력을 보지 못함 (병렬 실행)
- **Brief**: 7개 전제 + 6개 질문 (Q1~Q6)

### 4.2 합의(수렴) 지점

| 항목 | 합의 내용 |
|------|----------|
| Q1 steelman | "agent-native layout preview" — narrow but real need |
| Q2 weakest premise | 99% 커버리지 야망 + L3 설계 — 두 가지가 얽혀서 실패 |
| Q3 realistic target | v1 = 70-75% (페어 일치) |
| Q4 stack | PostToolUse hook → filesystem watcher로 변경 강권 |
| Q4 missing | cache key 전체 입력 + error UI contract + resource invalidation |
| Q5 cut | `server/emulator/*`, `server/fallback/*`, `server/databinding/*`, `server/gradleHarness/*` |

### 4.3 발산(divergence) 지점

| 질문 | Codex | Claude 서브에이전트 | 해석 |
|------|-------|-------------------|------|
| Q2 root cause | 99% 커버리지 자체가 비현실 | L3 에뮬레이터 UX 지연 | **동일 결론, 다른 경로** — triangulation 강도 ↑ |
| Q6 missing decision | standalone vs Gradle 통합 | layoutlib JAR 번들 vs 프로젝트 의존 | **보완적 관점** — 둘 다 채택 (전제 7에 반영) |

### 4.4 판정
- **"Set converges" (동일 결론, 다른 우선순위)**: B 선택 근거가 페어 권고(A)에서 벗어나는 것은 사실. 사용자의 85% 절충은 "페어가 우려한 리스크를 인지하고 완화 계획과 함께 수용한 결정".
- **CLAUDE.md 페어 규칙**: "사용자가 최종 결정자". 페어 권고는 기록되고, 선택의 근거로 사용됨.
- **신뢰도**: 중간-높음. B 실행 중 리스크 발현 시 A로 escape 가능한 구조를 유지하는 것이 조건.

### 4.5 페어 원문 아카이브
- Codex: `/tmp/axp-plan/codex-out.log` (세션 종료 시 삭제될 수 있음)
- Claude 서브에이전트: 본 문서 §4.2-4.3 및 `00-overview.md` §6에 발췌 반영

전체 원문이 필요할 경우 Phase 6 리뷰 (plan-eng-review)에서 다시 호출하여 재기록.

---

## 5. 다음 결정 타임박스

- **Week 1 종료**: L1 layoutlib 호출이 기본 레이아웃을 렌더 가능한지 go/no-go
- **Week 2 종료**: L2 aapt2 캐시가 `@string/`, `@color/` 참조를 해결하는지
- **Week 3 종료**: L3 harness APK + warm AVD가 custom view 하나라도 렌더하는지 — **escape hatch 발동 시점**
- **Week 4 종료**: 공개 Android OSS 프로젝트 10개 샘플링으로 85% 목표 검증 → 미달 시 v1.0을 A로 출시, L3는 v1.1

---

## 6. 변경 로그
- 2026-04-22: 초안. 페어 원문 반영. 사용자 85% 절충 결정 기록.
