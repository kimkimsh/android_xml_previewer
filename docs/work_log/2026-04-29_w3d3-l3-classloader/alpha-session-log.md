# W3D3-α Session Log — Bytecode Rewriting + R.jar Seeding (T1 gate PASS via Branch B)

**Date**: 2026-04-29 (W3 Day 3, α 후속 세션)
**Canonical ref**: `docs/plan/08-integration-reconciliation.md §7.7.5`.
**Outcome**: ASM-기반 bytecode rewriting (NAME_MAP 4 entries) + R.jar id seeding 인프라 구축. `LayoutlibRendererIntegrationTest` 의 T1 gate **branch (B) — Material theme enforcement contingency `activity_basic_minimal.xml` 로 PASS**. 167 unit + 12 integration PASS + 1 SKIPPED.

---

## 1. 작업 범위

W3D3 base session 의 branch (C) 진단 (`branch-c-diagnosis.md`) 가 enumerate 한 4 옵션 중 **option α (ASM bytecode rewriting + R.jar id seeding)** 채택. 핵심 deliverable:
1. AAR `classes.jar` 적재 시점 ASM `ClassRemapper` 로 `android/os/Build*` → `android/os/_Original_Build*` rewrite (4 entries).
2. `MinimalLayoutlibCallback` init 에서 R.jar 의 모든 R$<type> 클래스 enumerate, static int field 를 byRef/byId 에 사전-populate.
3. `FIRST_ID` 0x7F0A_0000 → 0x7F80_0000 (AAPT type-byte 와 disjoint).
4. `LayoutlibRendererIntegrationTest` enable + Material/AppCompat fallback retry → contingency layout `activity_basic_minimal.xml`.

## 2. 변경 파일 (7 commits, push origin/main)

### 2.1 신규 파일 (8 main + 7 test = 15)

**main (production)**:
- `classloader/RewriteRules.kt` — NAME_MAP 4 entries (Build family) + Remapper.
- `classloader/AndroidClassRewriter.kt` — ASM ClassRemapper 기반 단일 .class rewriter.
- `classloader/RJarTypeMapping.kt` — R$<simpleName> → ResourceType 매핑 (24 entries).
- `classloader/RJarSymbolSeeder.kt` — R.jar walker + reflection field enumerate. R$styleable 전체 skip.

**test**:
- `classloader/RewriteRulesTest.kt` — 5 tests (NAME_MAP 검증 + dual-publish 21 클래스 무수정 검증).
- `classloader/AndroidClassRewriterTest.kt` — 3 tests (rewrite 검증).
- `classloader/AarExtractorRewriteTest.kt` — 2 tests (jar-rewrite 통합 + REWRITE_VERSION cache layer).
- `classloader/RJarTypeMappingTest.kt` — 3 tests.
- `classloader/RJarSymbolSeederTest.kt` — 5 tests (ASM-based synthetic R.jar fixture).
- `session/MinimalLayoutlibCallbackInitializerTest.kt` — 3 tests (initializer 양방향 lookup + nextId monotonicity + IllegalStateException wrap).
- `fixture/sample-app/app/src/main/res/layout/activity_basic_minimal.xml` — contingency layout.

### 2.2 수정 파일

- `server/layoutlib-worker/build.gradle.kts` — `org.ow2.asm:asm:9.7` + `asm-commons:9.7` implementation deps.
- `classloader/ClassLoaderConstants.kt` — 6 신규 상수 (CLASS_FILE_SUFFIX, INNER_CLASS_SEPARATOR, R_CLASS_NAME_SUFFIX, INTERNAL_NAME_SEPARATOR, EXTERNAL_NAME_SEPARATOR, REWRITE_VERSION).
- `classloader/AarExtractor.kt` — extract() 가 classes.jar 를 stream-rewrite. cache path 에 REWRITE_VERSION layer.
- `session/MinimalLayoutlibCallback.kt` — 생성자에 initializer 추가. FIRST_ID 0x7F0A_0000 → 0x7F80_0000. advanceNextIdAbove CAS-based monotonicity. init 의 IllegalStateException wrap.
- `LayoutlibRenderer.kt` — `seedRJarSymbols` named method (CLAUDE.md "Lambdas — Avoid"). callback 호출에 `::seedRJarSymbols` 전달.
- 12 callsites (callback constructor) 일괄 갱신 — MinimalLayoutlibCallbackTest 8, SessionParamsFactoryTest 2, MinimalLayoutlibCallbackLoadViewTest 1, LayoutlibRenderer 1.
- `LayoutlibRendererIntegrationTest.kt` — `@Disabled` 제거. `renderWithMaterialFallback` helper (renderer.lastSessionResult.exception/errorMessage 검사 — Material/ThemeEnforcement/Theme.AppCompat/AppCompat keyword 매칭).
- `ClassLoaderConstantsTest.kt` — 4 신규 tests.

## 3. 테스트 결과

| 카테고리 | W3D3 base | W3D3-α | 변화 |
|---|---|---|---|
| Unit | 142 PASS | **167 PASS** | +25 (ClassLoaderConstants +4, RewriteRules +5, AndroidClassRewriter +3, AarExtractorRewrite +2, RJarTypeMapping +3, RJarSymbolSeeder +5, MinimalLayoutlibCallbackInitializer +3) |
| Integration PASS | 11 | **12** | +1 (LayoutlibRendererIntegrationTest 가 fallback 으로 PASS) |
| Integration SKIPPED | 2 | **1** | tier3-glyph 만 (W4+ carry). LayoutlibRendererIntegrationTest 는 더 이상 SKIP 아님. |
| BUILD | SUCCESSFUL | SUCCESSFUL | — |

## 4. 발견 / landmine / 새 carry

### LM-α-A (Q1 round 1 페어 컨버전스 정정 — 다시 확인)
W3D3 base 의 LM-W3D3-A 와 동일한 lesson — 페어 리뷰의 convergent decision 도 empirical 검증 누락 시 잘못될 수 있음. 본 α 의 round 1 spec 에서 NAME_MAP 25 entries 가 양측 합의처럼 보였으나 Claude empirical (`unzip -l` 으로 dual-publish 검증) 가 21 entries 의 잘못을 surface. 정공법: 페어가 합의해도 *empirical-verifiable claim* 은 직접 실측.

### LM-α-B (Codex output stalled-final)
α 의 spec round 1 + plan round 2 모두에서 Codex 가 long-tool-trace 후 final synthesis 가 늦거나 empty 로 종결 — 본 system 의 codex 가 long task 의 final 합성에 약한 모드. plan round 2 에서는 다행히 일정 시간 후 final 보고가 들어왔으나 spec round 1 에서는 직접 empirical 검증 없이 tool 측면 작업만 trace. **mitigation**: Claude empirical-verified single-source verdict 로 진행 + Codex output 도 보존 (`alpha-spec-pair-review-round1-codex.md`).

### LM-α-C (Branch B 의 actual exception keyword)
Spec/plan 이 "Material/ThemeEnforcement" keyword 만 가정. 실 실패 메시지는 `Theme.AppCompat`. α-5 implementer 가 직접 `Theme.AppCompat` + `AppCompat` keyword 추가 — minor spec deviation. 본 deviation 은 contingency 의도에 일치하므로 spec 갱신 불요, 단 향후 다른 fixture 에서 같은 시나리오 발생 시 keyword 확장 추가 권장.

### LM-α-D (test name backtick 안의 마침표 금지)
α-3 implementer 가 `AAR classes_jar 의 .class entries 가 rewrite 됨` 같은 backtick 함수명에서 Kotlin 컴파일러가 마침표 (.) 를 reject. 후속 task prompt 에 "backtick 안에 마침표 사용 금지" 명시하여 재발 방지.

## 5. 페어 리뷰 결과

### Spec round 1 (architectural design tradeoff)
- **Claude**: NO_GO — 2 hard blockers (A1 NAME_MAP overrewrite, A2 R$styleable index pollution). 6 follow-ups.
- **Codex**: 명시 verdict 미생성 (long-tool-trace 후 stalled).
- **컨버전스**: Claude empirical 단독 verdict 로 진행. spec round 2 가 모든 Claude finding 반영.

### Plan round 2 (plan-document review)
- **Claude**: NO_GO — 1 hard blocker (A1 fallback retry semantics). 5 follow-ups.
- **Codex**: NO_GO — 2 hard blockers (A1 동일 + A2 parseRClassName private test). 7 follow-ups.
- **Direction 컨버전스** — severity 동일 (NO_GO), A1 동일.
- plan v2 가 양측 모든 fix 반영.

### Implementation phase
- Claude-only (CLAUDE.md "Implementation phase = Claude-only").
- Subagent-driven 5 dispatches (α-1 ~ α-5). 모두 DONE / DONE_WITH_CONCERNS — BLOCKED 없음.

## 6. T1 gate empirical 결과 (branch B 발화 상세)

primary `activity_basic.xml` 시도:
```
[LayoutlibRenderer] createSession result:
  status=ERROR_INFLATION
  msg=The style on this component requires your app theme to be Theme.AppCompat (or a descendant).
  exc=IllegalArgumentException
[LayoutlibRenderer] RenderSession.render failed: status=ERROR_NOT_INFLATED
```

`renderWithMaterialFallback` 가 `lastSessionResult.errorMessage` 에서 `Theme.AppCompat` keyword 매칭 → contingency `activity_basic_minimal.xml` 로 retry:

```
[LayoutlibRenderer] createSession result: status=SUCCESS
PNG bytes > 1000 ✓
PNG magic 0x89 0x50 0x4E 0x47 ✓
```

**결론**: 
- Codex round 1 B2 hypothesis (Material theme enforcement throw) **confirmed** — 단 정확한 메시지는 `Theme.AppCompat` 검사.
- α 의 ASM rewriting + R.jar id seeding 이 ConstraintLayout-only layout (activity_basic_minimal) 의 SUCCESS 를 가능케 함 — 즉 host-JVM 에서 framework-아닌 custom view (ConstraintLayout) 를 R.jar id seeding 으로 layout_constraint* attr 정상 resolve. 본 결과로 W3D3-α 의 핵심 deliverable (custom view classloading + R-id seeding) 이 *작동 증명*.
- MaterialButton 같은 Material-themed widget 은 별도 carry — sample-app 의 `Theme.AxpFixture` (Material3.DayNight) 를 RenderResources 에 적용하려면 app/library resource value loader 필요 (기존 W3D4 carry).

## 7. 다음 세션 carry

### W3D4 candidates
- **MATERIAL-FIDELITY**: app/library resource value loader (`fixture/sample-app/app/src/main/res/values/themes.xml` + AAR `res/values/values.xml` 파싱). 완료 시 `activity_basic.xml` (MaterialButton 포함) primary 로 PASS 가능. ~500-1000 LOC (W3D1 framework loader 와 동일 규모).
- **tier3-glyph** (W4+ target).

### 기존 carry 유지
- `POST-W2D6-POM-RESOLVE`, `W3-CLASSLOADER-AUDIT` (본 α 가 partial 진행).

## 8. 커맨드 이력 (재현용)

```bash
./server/gradlew -p server test                                                # 167 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration    # 12 PASS + 1 SKIP
./server/gradlew -p server build                                               # BUILD SUCCESSFUL

# Material fallback 재현 (primary fail → fallback succeed)
./server/gradlew -p server :layoutlib-worker:test --tests "...LayoutlibRendererIntegrationTest" \
    -PincludeTags=integration --info 2>&1 | grep -E "Theme.AppCompat|status="

# Sample-app 의 R.jar 재생성 (assembleDebug 가 emit)
(cd fixture/sample-app && ./gradlew :app:assembleDebug)
```

## 9. Commit 이력

`5b07546..` (W3D3-α 시작 직전) → `b556dc8` (α-5 commit, 마지막):

| SHA | 메시지 |
|-----|--------|
| `e844e2a` | docs(w3d3-α): plan v1 — 6 tasks (TDD bite-sized, atomic 5) |
| `1e68eb2` | docs(w3d3-α): plan v2 + spec parseRClassName→internal — 페어 round 2 컨버전스 |
| `b969141` | feat(w3d3-α): ASM 9.7 의존 + ClassLoaderConstants 확장 (CLASS_FILE_SUFFIX 등) |
| `33a5ba1` | feat(w3d3-α): RewriteRules NAME_MAP (4 Build family) + AndroidClassRewriter |
| `deb27ee` | feat(w3d3-α): AarExtractor 의 classes.jar bytecode rewrite 통합 |
| `f97592d` | feat(w3d3-α): RJarTypeMapping + RJarSymbolSeeder — R.jar id 시드 |
| `b556dc8` | feat(w3d3-α): callback initializer + R.jar seeding wire + integration enable |
