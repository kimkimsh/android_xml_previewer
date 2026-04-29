# W1-END 페어 리뷰 결과 (2026-04-23)

> **CLAUDE.md 페어 리포팅 요구사항** 충족을 위한 canonical 결과 문서.
> 1:1 Codex xhigh + Claude 서브에이전트 병렬 독립 리뷰. 서로의 출력 비공유.
> W1D1 페어 리뷰(`docs/W1D1-PAIR-REVIEW.md`, full convergence) 의 후속.

---

## 1. 페어 구성 + 스코프

| 반쪽 | 에이전트 | 모델 | 실행 방식 | 지속시간 |
|------|----------|------|-----------|---------|
| Claude | `feature-dev:code-reviewer` | sonnet (teammate) | tmux pane `%15` teammate mode | ~9분 |
| Codex  | `codex:codex-rescue` (xhigh 재시도) | gpt-5-xhigh | summary-based 프롬프트 (sandbox 회피) | ~132초 |

**스코프**: Week 1 전체 게이트 (D1-D5). 본 세션이 새로 추가/수정한 범위 정밀 검토.
**리뷰 기준 축**: canonical coverage / module boundaries / tech debt / plan deltas.
**Verdict 축**: GO / GO_WITH_FOLLOWUPS / NO_GO.

**Codex sandbox 회피 히스토리**: W1D1 과 동일 패턴 — 첫 시도가 `bwrap: loopback: Failed RTM_NEWADDR` 로 블록. CLAUDE.md 규칙(*"Codex unavailable 시 Claude 로 대체 금지"*)에 따라 summary-based 프롬프트로 재시도 성공.

---

## 2. 양쪽 headline 결과 side-by-side

### 2.1 Axis scores

| Axis | Claude (source-level) | Codex (execution-level) | 수렴? |
|------|-----------------------|-------------------------|-------|
| 1. Canonical coverage | **PASS** (9/9 mandated 검증 통과) | **FAIL** (MILESTONES stdio + 실제 layoutlib render 미충족) | ❌ diverge |
| 2. Module boundaries  | **PASS** | **PASS** (`:http-server` deps = protocol/render-core/ktor only) | ✅ full |
| 3. Tech debt          | **CONCERN** (@Volatile 누락) | **CONCERN** (tier2 SKIP, placeholder handoff) | ✅ 동의 (다른 plane) |
| 4. Plan deltas        | **PASS** (implicit — 08 §7 새 delta 미발견) | **CONCERN** (W2 acceptance gates 명시 필요) | △ partial |

### 2.2 Verdict

| Reviewer | Verdict |
|----------|---------|
| Claude   | **GO_WITH_FOLLOWUPS** |
| Codex    | **NO_GO** |

**수렴 판정**: verdict **DIVERGENT**. Axis 1 (canonical coverage) 에서 FAIL vs PASS 극단적 분기. Axis 2-3 은 완전 수렴, Axis 4 는 partial.

### 2.3 분기의 구조적 원인 분석

CLAUDE.md 페어 리뷰는 "convergence/divergence 가 confidence signal". 본 divergence 는 **프롬프트 프레이밍 + 사용자 scope 결정** 으로 구조적 설명됨:

- **Claude 는 프롬프트에서 명시적으로 들었음**: *"W1 exit 의 escape hatch 자체를 trial 하지 말 것 (사용자 승인 있음)"*. 따라서 placeholder PNG (08 §7.6) 를 canonical 로 수용하고 나머지 source invariants 만 판정.
- **Codex 는 프롬프트에서 그 지시가 없었음**: MILESTONES.md 를 literal 읽음. "Escape Hatch 없음 (Foundation 주차는 스킵 불가)" 와 "stdio + HTTP 응답 10s 내" + "layoutlib 으로 최소 XML 1개 렌더" 를 강제 요건으로 해석 → stdio 미구현 + placeholder PNG 로 인한 2건 blocker.

양쪽 모두 자기 frame 내에서 정합적. 실질적으로는:
- Codex 는 canonical MILESTONES 의 문자 그대로 읽은 결과를 제시
- Claude 는 user-accepted scope 아래 source invariants 를 검사
- **두 결과 사이 합리적 타협점**: MILESTONES 의 3 item 을 literal 로 강요하면 stdio 도 이번 세션에 포함됐어야 함. 이를 08 §7.7 errata 로 정식 기록하여 W2 최우선 작업으로 승격 — Codex 의 FAIL 을 W2 필수 캐리로 전환.

### 2.4 Concerns — 발견 평면의 상보성 (W1D1 패턴 재현)

Claude 와 Codex 는 W1D1 때와 마찬가지로 **서로 다른 추상 수준** 에서 발견:

| Claude (소스 레벨) | Codex (실행 레벨) |
|--------------------|---------------------|
| `PreviewServer.engine` @Volatile 누락 → shutdown hook ↔ main thread JMM data race, latent live-lock | MILESTONES Week 1 item 1 "stdio + HTTP 응답" 중 stdio 미구현 (canonical requirement) |
| `Main.kt:44` `SSE_FULL_TAXONOMY` capability 기동 배너에 선언 (실제는 minimal SSE, W4 전까지 오탐) | Week 1 item 2 "layoutlib 으로 최소 XML 1개 렌더" 미증명 (placeholder 는 픽셀 출처가 layoutlib 아님) |
| (informational, reported 임계 미달) `findLayoutlibJar()` exclusion list 신규 좌표 fail-safe 미흡 (신뢰도 75%) | Tier2 SKIP (transitive Guava) — W2 fatJar closure blocking 필요 |
| (informational) `BridgeInitIntegrationTest.locateDistDir()` 의 candidate-3 ≈ candidate-1 중복 (기능적 문제 없음) | 종합 W2 acceptance gates 추가 제안 |

→ 두 축이 합해져 W1 상태를 완전 커버. 단일 리뷰어였다면 한 축을 놓쳤을 것 (W1D1 페어 패턴 재현 확인).

---

## 3. 채택된 최종 결정 (adopted decision + rationale)

**W1-END 게이트 결과: CONDITIONAL GO (divergence explicit, escape hatch expanded)**

**근거**: Claude 의 GO_WITH_FOLLOWUPS 를 base 로 채택하되, Codex 가 지적한 2건의 canonical gap (stdio, 실제 render) 을 08 §7.7 errata 로 정식 수용하여 W2 최우선 작업으로 승격. 이로써 Codex 의 FAIL 은 "W1 수용 불가" 가 아닌 "W2 초입 blocker" 로 정합. user 가 시나리오 B (placeholder PNG) 를 명시적으로 승인했다는 사실이 divergence 해소의 기반.

### 3.1 이 세션에서 즉시 해결된 follow-ups (Claude 결과 수신 직후)

| # | 출처 | 조치 | 확인 |
|---|------|------|------|
| F-1 | Claude C-1 | `PreviewServer.engine` 에 `@Volatile` 추가 + 주석 기록 | 전체 빌드 녹색 유지 |
| F-2 | Claude C-2 | `Capabilities.SSE_MINIMAL = "sse.minimal"` 신규 + Main.kt 기동 배너를 `SSE_FULL_TAXONOMY` → `SSE_MINIMAL` 로 교체 | 26 unit 테스트 유지 |

### 3.2 Codex 지적 → 08 §7.7 errata 승격 (W2 최우선)

| # | Codex 출처 | 조치 | 타겟 |
|---|------------|------|------|
| Codex C-1 | MILESTONES Week 1 item 1 stdio | 08 §7.7 에 stdio 를 W1 exit escape 로 canonical 수록 + W2 D6 최우선 착수 task 지정 | W2 D6 kickoff |
| Codex C-2 | MILESTONES Week 1 item 2 layoutlib render | 08 §7.7 에 placeholder → real render 교체 승격 + W2 D7 타겟 | W2 D7 |
| Codex C-3 | Tier2 Guava transitive | 08 §7.7 에 fatJar transitive closure acceptance 기준 명시 + BridgeInitIntegrationTest 의 Tier2 를 PASS 로 전환 요구 | W2 D6 |

### 3.3 Reported 임계 미달 — 기록만 유지 (조치 불요)

| # | 출처 | 내용 |
|---|------|------|
| Claude info 1 | `findLayoutlibJar()` exclusion list 신규 좌표 fail-safe 미흡 — Claude 자체 판정 신뢰도 75% (임계 미달). 당분간 기록만 유지, 새 좌표 출현 시 재검토. |
| Claude info 2 | `BridgeInitIntegrationTest.locateDistDir()` candidate-3 ≈ candidate-1 중복. 기능적 문제 없음. 향후 정리 기회에 통합. |

---

## 4. 세션 메트릭

- 산출: Kotlin 신규 11 + 수정 4 파일. 테스트 9 신규 (3+2+4, Tier2 SKIP 1). viewer 2 HTML/JS.
- 테스트: **26 unit + 4 integration PASS (Tier1 3 + Tier2 1 SKIP, best-effort 의도)**. 0 fail.
- 빌드: 전체 녹색. fatJar 20MB (`axp-server-0.1.0-SNAPSHOT.jar`).
- canonical 문서 교정: **3 신규 errata** (08 §7.5 4-artifact / 08 §7.6 placeholder PNG / 08 §7.7 stdio + real render W2 승격), THIRD_PARTY_NOTICES §1.1 4-artifact 재구성.
- 페어 수렴: axis 2 full / axis 3 concern 수렴 / axis 4 partial / axis 1 **divergent** — but divergence 를 structural 원인 (프롬프트 프레이밍 + user scope) 으로 설명 + 08 §7.7 로 resolution.

---

## 5. 변경 로그

| 일자 | 변경 | 비고 |
|------|------|------|
| 2026-04-23 | W1-END 페어 리뷰 결과 문서화 | Codex NO_GO ↔ Claude GO_WITH_FOLLOWUPS divergent, 08 §7.7 로 resolution |
