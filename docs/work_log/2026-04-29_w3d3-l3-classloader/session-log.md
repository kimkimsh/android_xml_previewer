# W3D3 Session Log — L3 ClassLoader Infrastructure (Tasks 1-8 close, Task 9 BLOCKED)

**Date**: 2026-04-29 (W3 Day 3)
**Canonical ref**: `docs/plan/08-integration-reconciliation.md §7.7.4`.
**Outcome**: classloader 인프라 (4 main + 4 test 신규 + 6 modify) push 완료. 142 unit + 11 integration PASS + 2 SKIP. activity_basic.xml 의 T1 gate 가 branch (C) — layoutlib 의 `android.os.Build` 부재 + R.jar id 불일치 (Codex B3 confirmed) — HARD STOP, escalation 으로 종결.

---

## 1. 작업 범위

W3D2 핸드오프의 **Option A** — sample-app `activity_basic.xml` (ConstraintLayout / MaterialButton 등 custom view) 의 layoutlib SUCCESS 렌더 unblock. AAR `classes.jar` URLClassLoader 적재 + `MinimalLayoutlibCallback.loadView` 확장.

## 2. 변경 파일 (push 완료, ac06d8e..94c5b2a)

### 2.1 신규 (4 main + 4 test = 8 파일)

| 파일 | 책임 | LOC |
|------|------|-----|
| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt` | 모든 magic strings (paths/extensions/digest) 상수 | 30 |
| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifest.kt` | `<module>/app/build/axp/runtime-classpath.txt` 파서 | 35 |
| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt` | AAR ZIP 의 classes.jar 를 stable cache 로 atomic 추출 | 60 |
| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt` | manifest + AarExtractor + R.jar → URLClassLoader(parent=isolatedCL) | 50 |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/{4 test files}` | 단위 17 테스트 (3+5+5+3) | ~370 |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/RendererArgs.kt` | `SharedRendererBinding` 의 cache key data class | 15 |
| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackLoadViewTest.kt` | TrackingClassLoader 패턴, 5 단위 테스트 | 90 |

### 2.2 수정 (production)

- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt` — `locateModuleRoot` + `FIXTURE_MODULE_SUBPATH` + `locateInternalGeneric` 추가, 기존 3-arg `locateInternal` 은 1-line bridge.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` — 생성자에 `viewClassLoaderProvider: () -> ClassLoader`. `loadView` 가 reflection + ITE unwrap. `findClass` + `hasAndroidXAppCompat=true` override 추가. W2D7 KDoc 보존.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — 생성자에 `sampleAppModuleRoot: Path` 4번째 인자. `ensureSampleAppClassLoader()` lazy provider. callback 주입 wire.
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt` — `SAMPLE_APP_ROOT_FLAG`, `VALUE_BEARING_FLAGS`, `USAGE_LINE` 갱신.
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` — `chooseRenderer` 가 `FixtureDiscovery.locateModuleRoot` 로 module root 결정.
- `fixture/sample-app/app/build.gradle.kts` — `axpEmitClasspath` Gradle task + `afterEvaluate { tasks.named("assembleDebug").configure { finalizedBy(...) } }`.

### 2.3 수정 (test infra)

- `SharedLayoutlibRenderer.kt` / `SharedRendererBinding.kt` / `SharedRendererBindingTest.kt` (3 tests) — `Pair<Path,Path>` → `RendererArgs(distDir, fixtureRoot, sampleAppModuleRoot)`.
- `SharedLayoutlibRendererIntegrationTest.kt` (4 sites) — `getOrCreate` signature 갱신.
- `LayoutlibRendererTier3MinimalTest.kt` — companion sharedRenderer 에 sampleAppModuleRoot.
- `MinimalLayoutlibCallbackTest.kt` (8 sites) — no-arg 호출을 `MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }` 로.
- `SessionParamsFactoryTest.kt` (2 sites) — 동일.
- `CliArgsTest.kt` — `--sample-app-root=value` 파싱 단위 테스트 +1.
- `LayoutlibRendererIntegrationTest.kt` — `@Disabled` 갱신, SUCCESS assertion + `requireNotNull` 보존 (미래 enable 대비).

## 3. 테스트 결과

| 카테고리 | 전 (W3D2) | 후 (W3D3) | 변화 |
|---|---|---|---|
| Unit | 118 PASS | **142 PASS** | +24 (Task 1-6 의 신규 + CliArgs `--sample-app-root` + FixtureDiscovery locateModuleRoot 2) |
| Integration PASS | 11 | **11** | 변화 없음 (W3D2 baseline 그대로) |
| Integration SKIPPED | 2 | **2** | `tier3-glyph` (W4+) + `LayoutlibRendererIntegrationTest` (W3D3 branch C, carry) |
| BUILD | SUCCESSFUL | SUCCESSFUL | — |

## 4. 발견된 landmine / 새 carry

### LM-W3D3-A (Q1 round 1 페어 컨버전스 empirical 정정)

Spec round 1 페어 리뷰 (Codex+Claude convergent) 가 "Q1: `tasks.named("assembleDebug")` 가 lazy 라 `afterEvaluate` 불요" 라고 결론. **Round 2 plan-review (Claude empirical)** 에서 실측: AGP 8.x 의 `assembleDebug` 는 variant API 등록 시점 이후에야 존재 → top-level `tasks.named` 시점에 `UnknownTaskException`. **`afterEvaluate` 필수**. round 1 페어 reasoning 의 함정 — "lazy" 의 의미가 task 등록 시점이 아닌 task body 평가 시점에 한함.

**교훈**: 페어 리뷰의 convergent 결정도 empirical 검증이 누락되면 잘못될 수 있다. Round 2 가 plan code 를 직접 시도해본 점이 잡아냄.

### LM-W3D3-B (subagent kotlin.test 의존 부재)

Task 1 implementer 가 처음 작성한 `ClassLoaderConstantsTest` 가 `import kotlin.test.assertEquals` 를 사용 → `:layoutlib-worker` test classpath 에 `kotlin.test` 없음 → 컴파일 FAIL. 기존 convention (`org.junit.jupiter.api.Assertions.*`) 로 교체. 후속 Task 2-6 prompts 에 명시적 instruction 추가하여 재발 방지.

### LM-W3D3-C (StringBuilder bootstrap CL → null)

Round 2 plan-review Codex valid finding: plan v1 의 Task 6 `findClass` 테스트가 `assertSame(parent, cls.classLoader)` — StringBuilder 같은 bootstrap-loaded 클래스의 `classLoader == null` 이라 fail. Round 2 v2 에서 `TrackingClassLoader` 패턴 (override `loadClass` + 호출 기록) 로 교체.

### LM-W3D3-D (BLOCKER) — `android.os.Build` 부재

**가장 큰 발견**: layoutlib 14.0.11 JAR 의 `android/os/` 하위에 `android.os.Build` 가 없다. 단 `_Original_Build*` prefix 변형만 존재. layoutlib 의 빌드 파이프라인이 의도적으로 rename — Android Studio 가 자체 SHIM 으로 `android.os.Build` 를 주입한다고 가정. 우리 환경 (Studio 외부 host JVM) 에는 SHIM 부재.

ConstraintLayout 등 AAR 의 bytecode 가 `Build.VERSION.SDK_INT` 를 그대로 참조 → 우리 SampleAppClassLoader chain 으로 resolve 실패 → ClassNotFoundException → ERROR_INFLATION. **W3D3 가 본 회기에서 close 불가** — option α (ASM-based bytecode rewriting) 또는 option β (Build shim JAR + R.jar id 시드) 가 W3D4+ carry.

진단 문서: `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md`.

### LM-W3D3-E (R.jar real id ↔ callback generated id 불일치)

Codex round 1 B3 가 spec 단계에서 surface 했고 spec §1.2 R3 가 "fidelity 손상, but SUCCESS 가능" 으로 축소 추정. 실측 결과 ERROR_INFLATION 직전에 발화 — SUCCESS 자체를 막는 confirmed blocker.

해결: `MinimalLayoutlibCallback` 의 `getOrGenerateResourceId`/`resolveResourceId` 가 sample-app R.jar 의 R$* 클래스의 모든 static int 필드를 reflection enumerate 하여 byRef/byId 를 사전-populate (call this **L3-RJAR-ID-SEED** carry).

## 5. 페어 리뷰 결과

### Spec round 1 (architectural design tradeoff analysis)
- Codex GPT-5.5 xhigh: NO_GO (4 blockers + 4 follow-ups). `spec-pair-review-round1-codex.md`.
- Claude Opus 4.7: GO_WITH_FOLLOWUPS (1 blocker + 6 follow-ups). `spec-pair-review-round1-claude.md`.
- 컨버전스: B1 (fixtureRoot 분리), F1 (findClass + hasAndroidXAppCompat), B4 (magic strings), Q1-Q5 (afterEvaluate 제거 — round 2 정정 대상).
- Spec round 2 가 모두 반영.

### Plan round 2 (plan-document review)
- Codex GPT-5.5 xhigh: NO_GO (6 blockers + 4 follow-ups).
- Claude Opus 4.7: GO_WITH_FOLLOWUPS (3 blockers + 7 follow-ups).
- 컨버전스 11건 (Task 7+8 atomic merge, StringBuilder bootstrap CL, R.jar test 정정, RendererArgs data class, afterEvaluate 정정 — Q1 empirical, SessionParamsFactoryTest caller, T1 gate requireNotNull, branch C hard-stop, Task 3 corrupted AAR, KDoc 보존, CLI USAGE_LINE).
- Plan v2 가 모두 반영. divergence: severity (NO_GO vs GO_WITH_FOLLOWUPS) 만 — direction 컨버전.

### Implementation phase
- Claude-only (CLAUDE.md "Implementation phase = Claude-only" 정책).
- Subagent-driven 9 dispatches:
  - Task 1: ClassLoaderConstants — DONE_WITH_CONCERNS (LM-W3D3-B).
  - Task 2-6, 8: DONE.
  - Task 7: DONE (atomic 11 files, 142 unit PASS).
  - Task 9: BLOCKED branch (C) — orchestrator-level diagnosis 후 amend + escalation.

## 6. 다음 세션 carry

### Primary carry (sample-app unblock 지속)
- **W3D3-FINISH**: option α (bytecode rewriting) 또는 option β (Build shim JAR + R.jar id 시드) 중 사용자 결정 후 진입. 진단 §5 enumeration 참조.
- **L3-RJAR-ID-SEED**: callback id seeding (Codex B3 정공법) — option β 의 일부.

### 기존 carry 유지
- `tier3-glyph` (W4+ target).
- `CLI-DIST-PATH` (W3D2 에서 close, 본 W3D3 가 `--sample-app-root` 추가 — `CliConstants.USAGE_LINE` 도 함께 갱신).
- `TEST-INFRA-SHARED-RENDERER` (W3D2 에서 close).
- `POST-W2D6-POM-RESOLVE` (F-6).
- `W3-CLASSLOADER-AUDIT` (F-4) — 본 W3D3 가 부분적으로 진행했으나 Build SHIM 이슈로 full close 미달.

## 7. 커맨드 이력 (재현용)

```bash
# Final gates (W3D3 종결 시점)
./server/gradlew -p server test                                                # 142 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration    # 11 PASS + 2 SKIPPED
./server/gradlew -p server build                                               # BUILD SUCCESSFUL

# Smoke
./server/mcp-server/build/install/mcp-server/bin/mcp-server --smoke            # "ok"

# Sample-app manifest 생성 (Task 8 산물)
(cd fixture/sample-app && ./gradlew :app:assembleDebug)                        # → app/build/axp/runtime-classpath.txt (55 라인)

# Branch (C) 재현 (옵션 α/β 진입 전 baseline 확인용)
# 1. LayoutlibRendererIntegrationTest 의 @Disabled 제거.
# 2. ./server/gradlew -p server :layoutlib-worker:test --tests "...LayoutlibRendererIntegrationTest" -PincludeTags=integration
# 3. Expected: ERROR_INFLATION + android.os.Build$VERSION CNFE + style 2130903769 lookup fail.
```
