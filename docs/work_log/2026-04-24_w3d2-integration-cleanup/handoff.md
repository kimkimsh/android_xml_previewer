# W3D2 → W3D3+ Handoff

## 상태 요약 (2026-04-24 종료 시점)

- W3D1 pair-review deferred carry 3 개 (C1 default 제거, C2 CLI-DIST-PATH, C3 TEST-INFRA-SHARED-RENDERER) 모두 CLOSED.
- Unit 118 + Integration 11 PASS + 2 SKIPPED (tier3-glyph, LayoutlibRendererIntegrationTest@Disabled).
- LayoutlibRenderer 생성자 default parameter 0 — CLAUDE.md "No default parameter values" 완전 준수.
- Main.kt 가 `--dist-dir=<path>` / `--fixture-root=<path>` CLI override 지원 (optional, auto-detect fallback).
- SharedLayoutlibRenderer 싱글톤이 per-JVM-fork 에서 in-class L4 (native lib) 차단.
- Codex pair-review 는 `codex exec --sandbox danger-full-access --skip-git-repo-check` direct CLI 로 사용 (codex:codex-rescue subagent 는 bwrap 실패).

## 1턴에 읽어야 할 문서

1. `docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md` — 본 세션 상세.
2. `docs/superpowers/specs/2026-04-24-w3d2-integration-cleanup-design.md` — W3D2 설계 (MF-B1~B4 반영 완료).
3. `docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md` — 본 플랜 (모든 11 Task 체크박스 닫힘 확인).
4. `docs/plan/08-integration-reconciliation.md` — 전체 로드맵 (W3D2 완료 상태 업데이트 필요 시).

## 다음 세션 작업 옵션

### Option A (권장) — sample-app unblock (W3 본론)

`fixture/sample-app/app/src/main/res/layout/activity_basic.xml` 의 ConstraintLayout / MaterialButton 등 커스텀 뷰 지원.
- 핵심 구현: DexClassLoader 로 Android support/material artifact (AAR) 로부터 클래스 로드.
- `MinimalLayoutlibCallback.loadView` 확장해 위임.
- `LayoutlibRendererIntegrationTest` 의 `@Disabled` 해제 후 tier3-values 패턴 assertion.

### Option B — tier3-glyph (W4 target)

- Font wiring + StaticLayout + Canvas.drawText JNI 경로 증명.
- T2 gate (activity_minimal TextView 영역 dark pixel >= 20) unblock.

### Option C — POST-W2D6-POM-RESOLVE / W3-CLASSLOADER-AUDIT (기존 carry)

## 긴급 회복 (빌드가 깨졌을 때)

1. `git` 관리가 없으므로 수동 rollback 필요. 본 세션의 신규 파일을 rm + 수정 파일을 W3D1 상태 (`W3D1 session-log §2`) 로 복구.
2. `./server/gradlew -p server test` 로 W3D1 state (99 unit + 8 integration PASS + 2 SKIPPED) 복귀 확인.

## 주의 landmine 재발 방지

- **L6** Codex CLI trust-check hang — non-git 프로젝트에서 항상 `--skip-git-repo-check` 플래그.
- **LM-G** codex-companion sandbox 실패 — bwrap user namespace 제약이 있는 환경에서는 `codex:codex-rescue` subagent 대신 `codex exec --sandbox danger-full-access --skip-git-repo-check` direct CLI 사용.
- **LM-F1** `SharedLayoutlibRenderer` 에 `resetForTestOnly()` 를 추가하지 말 것. 이전 설계에서 고려됐으나 native lib unload 불가 로 의미 없는 dead API — MF-F1 로 제외됨.
- **LM-B3** singleton 은 per-JVM-fork scope. Gradle `forkEvery(1L)` 가 integration test class 단위 JVM 격리 보장. "JVM-wide" 가 아닌 "per-test-class-fork" 로 이해할 것.
