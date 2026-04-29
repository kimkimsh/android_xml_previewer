# 미해결 질문 + 리스크 레지스터

> **상태**: DRAFT
> **용도**: Phase 6 (`/plan-eng-review`, `/plan-devex-review`)에서 검증할 항목 + v1 실행 중 답해야 할 질문들.

---

## 1. 미해결 오픈 질문 (v1 시작 전 답변 필요)

### Q1. layoutlib JAR 번들링의 라이선스 영향
- **상태**: 오픈. 필수 해결.
- **질문**: layoutlib은 Android Open Source Project의 Apache 2.0 라이선스이지만, 특정 버전의 `layoutlib.jar`을 재배포할 때 고지 의무 / 변경 표시 의무의 구체적 범위는?
- **답 얻는 방법**: Google의 공식 layoutlib-api 프로젝트 문서 확인 + (필요 시) 간단한 법무 검토.
- **v1 Week 1에 반드시 확정**.

### Q2. Claude Code 플러그인 마켓플레이스 리뷰 기준
- **상태**: 오픈.
- **질문**: 플러그인이 JVM 프로세스를 spawn하고, HTTP 서버를 띄우고, 포트를 바인딩하는 것이 허용되는가? 번들된 JAR / APK 크기 상한이 있는가?
- **답 얻는 방법**: Claude Code 공식 문서 확인 + 유사 구조 플러그인(예: `plugin_serena`처럼 LSP 띄우는 것) 레퍼런스.
- **v1 출시 1주 전 확정**.

### Q3. AVD 자동 생성 vs 사용자 제공
- **상태**: 부분 결정 (v1은 사용자 제공 AVD 감지, 자동 다운로드는 v1.5).
- **질문**: 첫 실행 시 AVD가 없으면 플러그인이 시스템 이미지를 자동 다운로드할지 vs "AVD Manager로 만드세요" 안내만 할지. 자동은 UX 우수하지만 ~1GB 다운로드와 사용자 동의 필요.
- **v1 결정**: 자동 다운로드 금지, 상세 안내 + `axp setup-avd` 커맨드 제공.

### Q4. 동일 포트(7321)에 이미 다른 프로세스가 바인딩되어 있을 때
- **상태**: 부분 결정 (7321 → 7322 → 7323 fallback).
- **질문**: 사용자가 여러 Android 프로젝트를 동시에 열어 각각 플러그인을 켜면 포트 충돌. "프로젝트별 고유 포트"로 할지, "하나의 전역 서버가 여러 프로젝트를 호스팅"할지.
- **v1 결정**: 프로젝트별 별도 프로세스 + 포트 자동 탐색. 추후 v1.5에서 전역 서버 모드 평가.

### Q5. Android 프로젝트가 아닌 곳에서 플러그인 활성화 시 동작
- **상태**: 오픈.
- **질문**: 사용자가 Claude Code를 다른 디렉토리에서 열었을 때 MCP 서버가 어떻게 동작해야 하나? Lazy start (skill 호출 시에만)? 조용히 비활성?
- **v1 결정**: Lazy start. `AndroidProjectLocator`가 프로젝트 감지 실패 시 MCP 서버는 skill 명령 대기만 하고 watcher 시작 안 함.

---

## 2. 리스크 레지스터

리스크 = (발생 확률 × 영향도). 우선순위는 **Critical → High → Medium → Low**.

### Critical

#### R1. 페어가 경고한 "L3 UX 지연 → live preview 약속 붕괴"
- **상세**: warm AVD에서도 최소 2-4초, cold 상태에서 30-60초. 첫 L3 호출에서 사용자가 "faulty"라고 판단하면 신뢰도 회복 어려움.
- **영향**: v1 사용자 retention 큰 타격, "Android Studio가 더 빠르다" 리뷰
- **완화**:
  - "⚙ emulator warming up..." 프로그레시브 UI (브라우저에 로딩 상태 표시)
  - Claude Code 세션 시작 시 AVD 백그라운드 사전 부팅 (사용자가 첫 편집하기 전에 warm)
  - L1로 커버 가능한 경우 L3 호출 시도 자체를 skip
- **트리거 측정**: L3 평균 지연 > 5초 or warm-up 실패율 > 10%

#### R2. 85% 커버리지 목표 미달
- **상세**: 공개 프로젝트 샘플링에서 70-75%만 성공하면 B 접근의 차별화 서사(Approach A 대비) 소실
- **영향**: v1.0 출시 시점에 재-구조 결정 필요
- **완화**:
  - Week 3 종료 시점에 샘플 10개 검증 (Week 4의 정식 검증 전 early warning)
  - 미달 시 v1.0을 A로 출시하고 L3는 v1.1로 미룸 (Escape Hatch)
- **트리거 측정**: Week 3 샘플링에서 `L3 with success` < 10% of failed cases

### High

#### R3. Gradle 프로젝트 모델 복잡성
- **상세**: multi-module, flavor, buildType, composite build, included builds… 실 프로젝트는 구조가 매우 다양. v1은 `app/src/main/res/`만 가정하는데, 실 프로젝트의 50%는 이 가정에서 벗어남.
- **영향**: "내 프로젝트에서는 안 된다"는 피드백 다수
- **완화**:
  - `AndroidProjectLocator.kt`에 휴리스틱 3단계: (1) settings.gradle 파싱 → (2) `*/src/main/res` glob → (3) `--project` 플래그로 수동 override
  - v1은 시작의 90% 케이스 커버 + `--help` 안내
- **트리거 측정**: "프로젝트 감지 실패" 이슈 GitHub에 5건 이상 / 첫 달

#### R4. layoutlib과 실 Android 프레임워크의 렌더 차이
- **상세**: layoutlib이 95% fidelity라 해도, 특정 View의 shadow/elevation/outline은 실 기기와 다름. 사용자가 "미리보기와 실행 결과가 다르다"는 경험을 하면 플러그인 신뢰도 훼손.
- **영향**: 사용자 "결국 에뮬레이터 돌려야 하네"
- **완화**:
  - `docs/LIMITATIONS.md`에 알려진 차이 목록 명시
  - `Fallback` 배지로 "layoutlib으로 렌더됨" / "에뮬레이터로 렌더됨" 명확히 구분
- **트리거 측정**: "렌더 차이" 이슈 10건 이상

#### R5. Claude Code 플러그인 API 변경
- **상세**: 플러그인 시스템이 안정화 초기 단계. 2026-04 이후 SKILL.md / plugin.json 포맷 breaking change 가능성 존재.
- **영향**: v1 출시 후 플러그인 사용 불가
- **완화**:
  - Claude Code 공식 changelog 구독
  - 매뉴얼 테스트: Claude Code 주기적 릴리스마다 재확인
  - CI에 "latest Claude Code" 대응 스모크 테스트
- **트리거 측정**: Claude Code minor version bump 시마다

### Medium

#### R6. macOS/Windows 지원 불완전
- **상세**: L3가 KVM에 의존. macOS는 HAXM/Hypervisor.framework, Windows는 WHPX로 대체 가능하지만 테스트 커버 필요.
- **영향**: Linux 외 사용자가 L3 사용 못함 → 85% → 75% 커버리지 저하
- **완화**:
  - v1 공식 지원: Linux. macOS는 best-effort (documented).
  - Windows는 WSL2 권장 (documented).
- **트리거 측정**: Windows 이슈 10건 이상

#### R7. aapt2 버전 호환성
- **상세**: 사용자의 Android SDK에 있는 aapt2가 여러 버전일 수 있음 (build-tools;32.0.0 vs 34.0.0). 플러그인이 특정 버전 flag에 의존하면 호환성 깨짐.
- **완화**:
  - aapt2 호출을 최소 feature subset으로 제한
  - fallback: 번들 aapt2 JAR (크기 증가 trade-off)
- **트리거 측정**: "aapt2 command failed" 이슈

### Low

#### R8. HTTP 포트 방화벽 차단
- **상세**: 일부 기업 환경에서 localhost 포트도 방화벽 차단 가능. 드문 케이스.
- **완화**: unix socket 대체 경로 v2 고려.

#### R9. 브라우저 없는 환경 (SSH only dev box)
- **상세**: 사용자가 원격 dev 서버에서 Claude Code를 돌릴 때 local 브라우저 오픈 안됨.
- **완화**:
  - SSH port forwarding 안내 (`ssh -L 7321:localhost:7321 user@host`)
  - v1.5에 `axp render --out foo.png` PNG 파일 export 커맨드

---

## 3. 테스트 전략

페어가 지적한 "golden test corpus with real projects" 누락 포인트를 해결.

### 3.1 Unit Tests (Week 1-3 동시 작성)
- `LayoutlibRenderer` — 고정된 샘플 XML 5종으로 픽셀 해시 비교 (tolerance ±1%)
- `ResourceCompiler` — 고정된 res/ 폴더 input → 고정된 .arsc output 바이트 비교
- `RenderDispatcher` — L1 실패 시나리오 모킹 → L3 escalate 동작 확인
- `LayoutFileWatcher` — debounce 타이밍

### 3.2 Integration Tests (Week 4)
- **Golden corpus**: 공개 Android OSS 10개 프로젝트 클론 + 각 프로젝트 layout_*.xml 중 3개 선별
- 주기 (nightly CI): 30개 렌더 → PNG hash / error type 집계
- 성공률 / L1 비율 / L3 비율 / Unrenderable 이유 분포 대시보드
- 회귀 감지: 각 PR에서 이 집계 재생성 + diff 표시

### 3.3 Manual Smoke Test (v1 릴리스 체크리스트)
- [ ] 빈 프로젝트에서 플러그인 설치 → 에러 없이 idle
- [ ] 실 프로젝트 (Signal? Bitwarden?)에서 설치 → activity_main.xml 렌더 ≤ 2s
- [ ] XML 편집 → 브라우저 자동 갱신 ≤ 1.5s
- [ ] Custom view 포함 레이아웃 → L3 fallback 성공
- [ ] AVD 없는 환경 → L1-only degrade 동작 + 안내 배지
- [ ] JVM 11 미만 환경 → 친절한 에러 메시지
- [ ] Android SDK 없는 환경 → 친절한 에러 메시지
- [ ] 포트 7321 점유 상태 → 7322로 자동 이동

---

## 4. 관찰성 (Observability)

### 4.1 로그
- 로그 레벨: ERROR / WARN / INFO / DEBUG
- 기본 로그 파일: `.axp-cache/logs/server.log` (rolling, 10MB x 5)
- 민감정보 금지: 파일 경로는 로깅 OK, XML content는 로깅 금지 (사용자 코드)

### 4.2 메트릭 (v1.5부터)
- 렌더 카운트 (L1 성공, L1 실패+L3 성공, L1 실패+L3 실패)
- 지연 분포 (p50, p95, p99)
- 캐시 hit rate
- `GET /api/metrics` 엔드포인트 (Prometheus format)

### 4.3 옵트인 텔레메트리 (v2+ 논의)
- 현재는 수집 안 함. 오픈소스 원칙.
- v2에서 익명 사용량 집계 고민 (명시적 opt-in)

---

## 5. 보안 고려사항

- MCP 서버는 localhost only binding (외부 포트 노출 금지)
- HTTP 서버는 인증 없음 (localhost 전제)
- harness APK가 하는 일: XML 파일 읽기, 렌더, PNG 저장 — 시스템 설정 변경 금지, 네트워크 접근 금지 (APK 매니페스트에서 권한 선언 최소화)
- 사용자 프로젝트 코드를 외부로 전송 금지 (텔레메트리 없음, 원격 로깅 없음)

---

## 6. Phase 6 리뷰에서 다룰 포인트

`/plan-eng-review` (엔지니어링 리뷰) 시 중점:
- R1-R5 (Critical + High) 완화책의 구체성
- 테스트 전략 충분성
- 캐시 key 설계가 정말 deterministic한지
- L1 ↔ L3 escalation 룰 테이블 정합성

`/plan-devex-review` (DX 리뷰) 시 중점:
- 첫 5분 사용자 경험 (설치부터 첫 렌더)
- 에러 메시지 품질
- `docs/INSTALL.md` / `docs/LIMITATIONS.md` 완성도
- 포트 충돌, AVD 없음 등 failure path UX
- Claude Code 세션 종료/재시작 시 상태 복원

---

## 7. 변경 로그
- 2026-04-22: 초안 작성. Codex+Claude 페어가 지적한 5대 누락 포인트 (cache key, error UI, resource invalidation, project model, LSP/diagnostics) 반영.
