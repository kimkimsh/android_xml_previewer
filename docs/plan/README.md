# Android XML Previewer — 기획안 (docs/plan/)

> **프로젝트**: Claude Code에서 Android XML 레이아웃을 즉시 렌더해 브라우저로 미리보기
> **작성일**: 2026-04-22
> **상태**: **DEEP-APPROVED** — L3 옵션 α 채택 (v1에 classpath-aware L3, **6주** 스케줄). 최종 canonical: `08-integration-reconciliation.md`.
> **문서 우선순위**: `08` > `07` > `06` > `04` > `02` > `05` > `03` > `01` > `00`. 충돌 시 상위 문서 승. 03/01은 historical 참고용.

---

## 읽는 순서

1. **[00-overview.md](./00-overview.md)** — 비전 / 문제 / 타겟 사용자 / UX 시나리오 / 성공 기준 / 확정 전제 7개
2. **[01-architecture.md](./01-architecture.md)** — 플러그인 구조 / 3-Layer 렌더 파이프라인 / 디렉터리 레이아웃 / MCP 인터페이스. _구현 세부는 07/08에 의해 개정됨._
3. **[02-alternatives-and-decision.md](./02-alternatives-and-decision.md)** — 접근법 3종(A/B/C) 비교 매트릭스 + Codex 페어 원문 + 85% 절충 결정 근거
4. **[03-roadmap.md](./03-roadmap.md)** — _HISTORICAL (superseded by 06 §6 + 08 §1)_. 초기 4주 roadmap 기록.
5. **[04-open-questions-and-risks.md](./04-open-questions-and-risks.md)** — 오픈 질문 / 리스크 레지스터 / 테스트 전략 / 관찰성 / 보안
6. **[05-pair-review-final.md](./05-pair-review-final.md)** — Phase 6 최종 Codex+Claude 페어 리뷰. L3 classpath showstopper 발견.
7. **[06-revisions-and-decisions.md](./06-revisions-and-decisions.md)** — **canonical what**. α 채택 + 7개 비논쟁 픽스 + 6주 roadmap + DexClassLoader 기본 설계 + Unrenderable enum + 확장 cache key.
8. **[07-deep-improvements.md](./07-deep-improvements.md)** — **canonical how (초안)**. 4기 병렬 심층 분석(Codex xhigh × 2 + Claude subagent × 2) 통합. L1 리소스 파이프라인 재설계, createPackageContext 패턴, ChildFirstDexClassLoader, SSE 10-event taxonomy, 13개 golden corpus, CI pipeline YAML.
9. **[08-integration-reconciliation.md](./08-integration-reconciliation.md)** ⭐ — **최상위 canonical**. Codex final integration이 06↔07 사이 5개 silent conflict + 6개 기술 오류 지적한 것을 전건 해소. 스케줄 정렬 (L3 2-phase Week 3-4, SSE 2-phase Week 2+4), ResourcesLoader 교체, split APK 대응, manual AAR merge 금지, 17-input cache key, 17 UnrenderableReason, Unix socket IPC, Week 1 Day 1 체크리스트. 모든 충돌 시 이 문서가 최종 권위.

---

## 한 문단 요약

Claude Code에서 Android 개발할 때 XML 레이아웃 미리보기를 보려면 Android Studio로 alt-tab해야 하는 불편을 없앤다. 플러그인이 `.claude-plugin/plugin.json` + skill + MCP 서버(Kotlin/JVM) + HTTP+SSE 라이브 서버로 구성되어, filesystem watcher가 `res/layout/*.xml` 변경을 감지하면 **L1 layoutlib** → 실패 시 **L3 에뮬레이터 harness APK + ChildFirstDexClassLoader**로 사용자 앱 classpath를 런타임 주입해 렌더하고, 사용자 브라우저(localhost:7321)에 hot-reload로 즉시 갱신한다. v1 목표는 실사용 XML의 **85% 렌더**(페어 권고 75%에서 옵션 α로 절충)를 **6주** 내 달성하는 것.

---

## 핵심 결정 3가지

| 결정 | 선택 | 근거 |
|------|------|------|
| 렌더 엔진 | **layoutlib + aapt2 + 에뮬레이터 fallback** | Paparazzi/Roborazzi는 Gradle 락인; 자체 파이프라인 필요 |
| 트리거 방식 | **Filesystem watcher** (PostToolUse hook 대신) | Codex+Claude 페어 공통 권고. 에디터/git 변경까지 커버 |
| v1 커버리지 목표 | **85%** (절충안) | 원안 99% → 페어 75% 권고 → 사용자 85% 절충 |

---

## Codex xhigh + Claude 서브에이전트 페어 리뷰 요약

CLAUDE.md 페어링 규칙에 따라 **1:1 병렬 독립 리뷰** 수행.

**강한 수렴 지점**:
- Q1 (steelman): agent-native workflow에 real but narrow need
- Q3 (커버리지): 99%는 비현실적, 75% 권고
- Q4 (stack): PostToolUse hook → filesystem watcher로 교체
- Q5 (2주 빌드): 거의 동일한 모듈 리스트 + 동일한 "cut list"

**보완적 관점** (둘 다 채택):
- Codex: standalone vs Gradle 통합 여부가 근본 결정
- Claude: layoutlib JAR 번들링 + SDK 버전 핀 정책

**사용자 최종 결정**: 85% 커버리지 + 에뮬레이터 유지 (페어 권고에서 벗어남, 리스크는 R1/R2로 명시적 관리).

상세는 [02-alternatives-and-decision.md §4](./02-alternatives-and-decision.md) 참조.

---

## 다음 단계 — 구현 Week 1 시작 준비 완료

- [x] Phase 6 Codex+Claude 페어 리뷰 완료 ([05-pair-review-final.md](./05-pair-review-final.md))
- [x] L3 이슈 결정: **옵션 α 채택** (v1 classpath-aware L3)
- [x] 7개 비논쟁 픽스 canonical 확정 ([06 §1.2](./06-revisions-and-decisions.md))
- [x] Phase 7 4-Agent 심층 분석 완료 ([07-deep-improvements.md](./07-deep-improvements.md))
- [x] Phase 8 Codex final integration — 5 silent conflict + 6 tech claim 오류 전건 해소 ([08-integration-reconciliation.md](./08-integration-reconciliation.md))
- [x] Status: DRAFT → REVIEWED → APPROVED → DEEP-APPROVED → **INTEGRATION-APPROVED**
- [ ] 구현 Week 1 Day 1: [08 §5 체크리스트](./08-integration-reconciliation.md) 실행

## 가장 중요한 4가지 교정 (07 §0)

07 심층 분석에서 **암묵적 가정 3가지가 틀렸음**이 확인됨:
1. L1 리소스 = `res/`만 컴파일 → **틀림**. transitive AAR resources + framework data from matching layoutlib dist 필요
2. L3 리소스 = `createConfigurationContext` → **틀림**. `createPackageContext` + `ContextWrapper` override + `aapt2 link -I app.apk`가 정답
3. RenderSession 풀링 = XML 단위 → **틀림**. XML 변경 시 반드시 새 세션
4. 병렬성 = thread 격리 → **틀림**. `Bridge` + native는 process-global, 프로세스 per device 필요

---

## 변경 로그

| 일자 | 변경 | 작성자 |
|------|------|--------|
| 2026-04-22 | 초안 5문서 작성 (office-hours Phase 5 완료) | Claude + Codex xhigh 페어 |
| 2026-04-22 | Phase 6 페어 리뷰 → α 선택 → 06 canonical what 작성 | Codex xhigh + Claude pair |
| 2026-04-22 | Phase 7 4-Agent 병렬 심층 분석 → 07 canonical how | Codex xhigh×2 + Claude sub×2 |
| 2026-04-22 | Phase 8 Codex final integration → 08 reconciliation | Codex xhigh final |
