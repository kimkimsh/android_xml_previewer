# W3D1 Session Log — RESOURCE-VALUE-LOADER (3b-values CLOSED)

**Date**: 2026-04-24 (세션 date; 폴더명은 W3D1 논리적 day 기준 2026-04-23 으로 유지)
**Canonical ref**: `docs/plan/08-integration-reconciliation.md` §7.7.2 → §7.7.3.
**Outcome**: `activity_minimal.xml` 이 실 layoutlib pipeline 을 SUCCESS 로 완주. `tier3-arch` 와 `tier3-values` 모두 PASS. `tier3-glyph` (Font wiring) 은 신규 W4 carry 로 @Disabled.

---

## 1. 작업 범위

W2D7 종료 시점의 canonical split (§7.7.1 item 3b):
- 3b-arch: createSession 이 inflate 단계 도달 → W2D7 에서 ERROR_* 로 PASS (arch evidence).
- **3b-values**: framework resource VALUE 를 RenderResources 에 제공 → W3 carry.

본 세션은 3b-values 를 CLOSE. 스코프는 브레인스토밍에서 결정된 Option B (Material-theme chain, 10 XML).

---

## 2. 변경 파일

### 신규 production (7 파일, `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/`)

| 파일 | 책임 |
|---|---|
| `ResourceLoaderConstants.kt` | `VALUES_DIR="res/values"`, FILE_* 10 파일 상수, REQUIRED_FILES list, REQUIRED_FILE_COUNT=10. |
| `StyleParentInference.kt` | 순함수: `infer(name, explicitParent) → String?`. `@android:style/` / `@style/` prefix 정규화 + dotted-prefix 상속. |
| `ParsedEntry.kt` | sealed class: SimpleValue / StyleDef / AttrDef + StyleItem data class. |
| `FrameworkValueParser.kt` | kxml2 기반 XML → `List<ParsedEntry>`. F1 반영: `<attr>` top-level + `<declare-styleable>` nested 양쪽 수집. |
| `FrameworkResourceBundle.kt` | immutable 집계. `byType: Map<ResourceType, Map<String, ResourceValue>>` + `styles` + `attrs`. AttrDef **first-wins** dedupe (F1), SimpleValue/StyleDef **later-wins**. Style parent post-process. |
| `FrameworkResourceValueLoader.kt` | `loadOrGet(dataDir): FrameworkResourceBundle` + JVM-wide ConcurrentHashMap 캐시. 10 XML 중 하나라도 없으면 IllegalStateException. |
| `FrameworkRenderResources.kt` | RenderResources subclass. bundle delegate. `findResValue` override 금지 (W2D7 L3 landmine). |

### 신규 test (5 파일, `.../src/test/kotlin/.../resources/`)

| 파일 | 테스트 수 |
|---|---|
| `StyleParentInferenceTest.kt` | 5 |
| `FrameworkValueParserTest.kt` | 11 (plan 10 + F1 nested attrs xml 실 케이스 1) |
| `FrameworkResourceBundleTest.kt` | 5 (plan 4 + F1 first-wins 1) |
| `FrameworkResourceValueLoaderTest.kt` | 7 (plan 6 + F1 nested attr guard 1) |
| `FrameworkRenderResourcesTest.kt` | 6 |
| `FrameworkResourceLoaderRealDistTest.kt` | 2 (integration — F1 real-dist 200+ attr + Theme.Material.Light.NoActionBar chain) |

### 수정 파일 (session 배선 + tier3 flip)

- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt` — `resources` default 제거, CLAUDE.md "No default parameter values" 준수.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — `renderViaLayoutlib` 에서 `FrameworkResourceValueLoader.loadOrGet(distDir.resolve("data"))` → `FrameworkRenderResources(bundle, DEFAULT_FRAMEWORK_THEME)` 주입.
- `server/layoutlib-worker/src/test/kotlin/.../session/SessionParamsFactoryTest.kt` — `emptyFrameworkRenderResources()` 헬퍼 + 호출부 3곳 업데이트.
- `server/layoutlib-worker/src/test/kotlin/.../LayoutlibRendererTier3MinimalTest.kt` — tier3-arch canonical flip (SUCCESS only, rejected={ERROR_NOT_INFLATED, ERROR_INFLATION, ERROR_RENDER, ERROR_UNKNOWN}), tier3-values @Disabled 제거 + T1 gate (SUCCESS + PNG>1000), tier3-glyph 신규 @Disabled (W4 carry). companion object `sharedRenderer` 로 테스트 클래스 단일 인스턴스 공유.

### 백업 (Task 11 에서 cleanup 예정)

- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalFrameworkRenderResources.kt.w3-backup` — 구 placeholder. F5 반영으로 `rm` 대신 rename.

### 문서

- `docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md` — 스펙 §2 F1 반영 + declare-styleable 재분류.
- `docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md` — F1-F6 전량 반영 (1608 → 1768 lines).
- `docs/W3D1-PLAN-PAIR-REVIEW.md` — §4 확장 (Codex quota 분석 + timeline + F1-F6 적용 상태 테이블 + §5 bottom line 갱신).
- `docs/plan/08-integration-reconciliation.md` — §7.7.3 추가 (본 세션 결과).

---

## 3. 테스트 결과

### 최종 (Task 9 완료 시점)

| 카테고리 | 전 (W2D7) | 후 (W3D1) | 변화 |
|---|---|---|---|
| Unit | 65 PASS | **99 PASS** | +34 (resources 34: 5+11+5+7+6 main tests; 가드 추가분 포함) |
| Integration PASS | 5 | **8** | +1 tier3-values flip, +2 FrameworkResourceLoaderRealDistTest (F1 guard) |
| Integration SKIPPED | 2 | **2** | activity_basic + tier3-glyph (tier3-values 가 SKIPPED 에서 빠지고 tier3-glyph 가 신규 SKIPPED 로 진입) |
| BUILD | SUCCESSFUL | SUCCESSFUL | — |

### 핵심 assertion

- `tier3-arch`: `renderer.lastSessionResult.status == Result.Status.SUCCESS` ✓.
- `tier3-values`: T1 gate (SUCCESS + PNG > 1000 bytes + dims 720×1280) ✓.
- `FrameworkResourceLoaderRealDistTest`: `bundle.attrCount() >= 100` ✓ (실 dist 에서 F1 regression guard).

---

## 4. 발견된 landmine

### L4 (신규) — Native lib JVM-wide single-load

**증상**: tier3-arch 단독 PASS, tier3-arch 후 tier3-values 실행 시 `IllegalStateException: LayoutlibRenderer 실패 + fallback 없음`. tier3-values 단독 실행 시 PASS.

**원인**: `System.load(libandroid_runtime.so)` 는 JVM 프로세스당 1회만 가능. 테스트 메서드마다 새 `LayoutlibRenderer` → 새 `URLClassLoader` → `System.load` 재호출 → `UnsatisfiedLinkError: Native Library ... already loaded in another classloader` 가 catch 되어 조용히 삼켜지고, 두 번째 Bridge 는 native 바인딩이 끊긴 상태로 render 시도 → 실패.

**해결**: `LayoutlibRendererTier3MinimalTest.companion object` 에 `@Volatile sharedRenderer` + `renderer()` synchronized factory. 테스트 클래스 생애 동안 단일 `LayoutlibRenderer` 공유. Production (`AxpServer`) 에서는 JVM 당 1개만 생성하므로 문제 없음.

**carry**: integration test infra 전반에 걸친 공용 base class 로 추후 일반화 가능 (W3+ 선택).

### L5 (신규) — Kotlin nested block comment

**증상**: `FrameworkValueParser.kt` 및 `ResourceLoaderConstants.kt` 가 "Identifier expected" + "Unclosed comment" 로 컴파일 실패.

**원인**: Kotlin 은 Java 와 달리 `/* ... */` 블록 주석의 **nested** 를 허용. KDoc `/**  values/*.xml  */` 안에 `/*` 가 새 inner 블록을 열고, 매칭되는 `*/` 가 부족하면 전체 파일 끝까지 주석 상태가 전파.

**해결**: KDoc 내 `/*.xml` 표현을 제거. 안전한 표현은 `values XML 파일들` 같은 자연어 서술.

### L3 재확인 — findResValue override 금지

`FrameworkRenderResourcesTest` 에 reflection guard 추가 (`findResValue` 메서드 미정의 검증). W2D7 L3 landmine 재발 방지.

### L6 (신규) — Codex CLI trust-check silent hang

**증상**: `codex exec "<prompt>"` 가 출력 없이 영구 대기. 사용자가 "quota 소진" 으로 오인 가능.

**원인**: 작업 디렉토리가 (a) git repo 가 아니거나 (b) `~/.codex/config.toml` 의 `[projects."..."].trust_level = "trusted"` 목록에 등재되어 있지 않으면 Codex 는 `Not inside a trusted directory and --skip-git-repo-check was not specified.` 메시지 후 stdin 프롬프트에 걸려 무한 대기. `codex:rescue` skill 이 non-interactive 로 호출하면 감지 불가.

**해결**: 모든 Codex 호출에 `--skip-git-repo-check` 플래그 추가. 단발성 재현:
```bash
codex exec --skip-git-repo-check "Reply with exactly 'pong'."
```

**carry**: `codex:rescue` skill 업그레이드 시 이 플래그 자동 추가 고려. 영향 범위는 사용자 local skill 이므로 ~/.claude/skills/ 대상은 본 프로젝트 밖.

---

## 5. 페어 리뷰 결과

### 플랜 단계 (pre-execution)

- **Claude reviewer verdict**: NO_GO → GO_WITH_FOLLOWUPS (F1 BLOCKER attrs.xml nested, F2 BLOCKER fake parent-chain, F3-F6 risk-reducers, F7-F8 nits).
- **Codex reviewer verdict**: 최초 quota 소진 메시지 (reset 3:20am KST) → 이후 재시도 시 실제 원인은 L6 (trust-check hang) 로 밝혀짐.
- F1-F6 모두 플랜·스펙·테스트에 반영. 실 실행에서 F1 가드 (Loader test + real-dist integration test 양쪽) 및 F2 가드 (createFakeDataDir parent chain) 모두 PASS.

### 구현 단계 (post-execution, 같은 세션 내 완료)

- **Claude half** (`Explore` subagent): GO_WITH_FOLLOWUPS. No blockers. F1 (rejected status 전수), F2 (AxpServer MT safety), F3 (compile-time guard), N1-N3 nits.
- **Codex half** (GPT-5.5 @ xhigh, `--skip-git-repo-check`): GO_WITH_FOLLOWUPS. No blockers. F1 (default params violation), F2 (simplify assertEquals), F3 (MCP dist path hardcode), F4 (test-infra), N1-N2 nits.
- **Convergence**: VERDICT 과 Blockers 세트 FULL 컨버전스. Q5 (rejectedStatuses 불완전) CONVERGENT. Q2 (default params) 는 Codex 만 잡아냄 (Claude miss).
- **병합된 MF1/MF2/MF3** 본 세션 내 반영 → regression 99 unit + 8 integration PASS 유지. `.w3-backup` cleanup 완료.
- 산출물: `docs/W3D1-PAIR-REVIEW.md`.

---

## 6. 다음 세션 (W3 Day 2+) 들어가는 carry

- **tier3-glyph** (본 세션에서 신규 @Disabled) — Font wiring + StaticLayout + Canvas.drawText JNI 증명. T2 gate.
- **CLI-DIST-PATH** (pair-review Codex F3) — MCP `Main.kt` 의 `layoutlib-dist/android-34` 하드코딩 제거 + CLI 인자 지원.
- **TEST-INFRA-SHARED-RENDERER** (pair-review Codex F4) — L4 의 `sharedRenderer` 패턴을 공용 base class 로 일반화 + `LayoutlibRendererIntegrationTest.kt:26` 전환.
- **LayoutlibRenderer default 파라미터 정리** — `fallback: PngRenderer? = null, fixtureRoot: Path = defaultFixtureRoot()` 도 CLAUDE.md 에 맞춰 제거 (본 세션은 MF2 로 `SessionParamsFactory` 만 처리).

**완료된 carry**:
- ~~MinimalFrameworkRenderResources.kt.w3-backup~~ (Task 11 에서 cleanup 완료).
- ~~Codex pair-review for W3D1 구현~~ (본 세션 내 완료).

기존 carry:
- `POST-W2D6-POM-RESOLVE` (F-6)
- `W3-CLASSLOADER-AUDIT` (F-4)

---

## 7. 커맨드 이력 (재현용)

```bash
# Baseline
./server/gradlew -p server test                                # 65 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration   # 5 PASS + 2 SKIPPED

# W3D1 최종
./server/gradlew -p server test                                # 99 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration   # 8 PASS + 2 SKIPPED

# 최종 빌드
./server/gradlew -p server build                               # BUILD SUCCESSFUL
```
