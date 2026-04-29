# W3D1 → W3D2+ Handoff

## 상태 요약 (2026-04-24 종료 시점)

- `activity_minimal.xml` 이 실 layoutlib pipeline 을 SUCCESS 로 완주 (tier3-arch + tier3-values 모두 PASS).
- Unit 99 + Integration 8 PASS + 2 SKIPPED (activity_basic, tier3-glyph).
- **구현 pair-review 완료** (`docs/W3D1-PAIR-REVIEW.md`): Claude + Codex 양측 GO_WITH_FOLLOWUPS, No Blockers. MF1/MF2/MF3 반영. `.w3-backup` cleanup 완료.
- Codex CLI 호출 시 `--skip-git-repo-check` 필수 (landmine L6 — non-git 프로젝트의 trust-check hang).

## 1 턴에 읽어야 할 문서

1. `docs/plan/08-integration-reconciliation.md` §7.7.3 — 본 세션 결과 요약.
2. `docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md` — 상세 변경 내역 + landmine.
3. `docs/W3D1-PLAN-PAIR-REVIEW.md` — F1-F6 follow-up 이 어떻게 반영되었는지.

## 다음 세션 작업 옵션

### Option A (권장) — W3 Day 2+ 스코프 진입 (pair-review 는 본 세션에서 이미 완료)

1. `docs/W3D1-PAIR-REVIEW.md` 의 deferred carry 3 개 중 선택:
   - **CLI-DIST-PATH** (Codex F3): MCP `Main.kt` 의 `layoutlib-dist/android-34` 하드코딩 제거 + CLI 인자 지원.
   - **TEST-INFRA-SHARED-RENDERER** (Codex F4): `LayoutlibRendererIntegrationTest.kt:26` 을 tier3 의 `sharedRenderer` 패턴으로 전환.
   - **LayoutlibRenderer default 정리**: `fallback`/`fixtureRoot` default 제거 — CLAUDE.md "No default parameter values" 완전 준수.
2. 또는 `MILESTONES.md` 에서 Week 2 → Week 3 전환 체크박스 반영 후 sample-app unblock 스코프로 이동.

### Option B — tier3-glyph (W4 target) 선행 탐색

- Font wiring: `LayoutlibCallback.getParser()` + `Typeface.createFromFile(fontDir.resolve(...))` 경로 확인.
- StaticLayout / Canvas.drawText JNI 경로 — 현재 Bridge 의 native lib 이 drawText 까지 바인딩되어 있는지 smoke.
- 만일 이미 SUCCESS 로 렌더된 PNG 에서 TextView 영역에 glyph 가 나타나면 tier3-glyph 를 unblock 가능.

### Option C — W3 Day 2 스코프 브레인스토밍

- **sample-app** (`fixture/sample-app/app/src/main/res/layout/activity_basic.xml`) unblock 을 위한 로드맵.
  - 현재 activity_basic.xml 은 ConstraintLayout / MaterialButton 등 커스텀 view 를 포함 → L3 DexClassLoader carry.
- CLI 진입점 설계 (fatJar 의 `--render <path>` 인자 / MCP tool expose).

## 긴급 회복 (빌드가 깨졌을 때)

1. **Task 8 backup 파일을 부활**:
   ```bash
   mv server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalFrameworkRenderResources.kt.w3-backup \
      server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalFrameworkRenderResources.kt
   ```
2. `SessionParamsFactory.build` 의 `resources` 파라미터에 `resources: RenderResources = MinimalFrameworkRenderResources()` 형태로 default 를 복원.
3. `LayoutlibRenderer.renderViaLayoutlib` 에서 `FrameworkResourceValueLoader.loadOrGet(...)` + `FrameworkRenderResources(...)` 주입 라인 제거.
4. `./server/gradlew -p server test` 로 W2D7 state (65 unit + 5 integration PASS + 2 SKIPPED) 복귀 확인.

## 잘 있는 곳 (상태 저장된 파일들)

- 플랜: `docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md` (1768 lines) — 11 Task, F1-F6 반영 완료.
- 스펙: `docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md` — 10 section, 스코프/데이터 흐름/리스크.
- 페어 리뷰: `docs/W3D1-PLAN-PAIR-REVIEW.md` — Codex quota timeline 포함.
- 08 canonical: `docs/plan/08-integration-reconciliation.md` §7.7.3.

## 주의 landmine 재발 방지

- **L3** `findResValue` override 금지 — `FrameworkRenderResourcesTest` 의 reflection guard 가 regression 차단.
- **L4** native lib JVM-wide single-load — `LayoutlibRendererTier3MinimalTest.companion.sharedRenderer` 유지.
- **L5** Kotlin nested block comment — KDoc 안에서 `/*.xml` 같은 표현 금지.
- **L6** Codex CLI trust-check silent hang — non-git 프로젝트에서 `codex exec` 호출 시 반드시 `--skip-git-repo-check` 플래그. 이 플래그 없이 호출하면 stdin 프롬프트에 걸려 영구 대기 (quota 소진으로 오인 가능).
