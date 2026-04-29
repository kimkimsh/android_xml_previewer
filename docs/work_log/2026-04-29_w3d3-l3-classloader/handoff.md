# W3D3 → W3D4+ Handoff

## 2026-04-29 α 후속 종료 시점 — T1 gate PASS via Branch (B)

W3D3-α (option α — ASM bytecode rewriting + R.jar id seeding) 완료.
- **167 unit + 12 integration PASS + 1 SKIP** (tier3-glyph).
- `LayoutlibRendererIntegrationTest` enable + Material/AppCompat fallback 으로 **`activity_basic_minimal.xml` SUCCESS**.
- Branch (B) 발화: primary `activity_basic.xml` (MaterialButton 포함) 가 `Theme.AppCompat` enforcement throw → contingency `activity_basic_minimal.xml` (Button 으로 교체) retry → SUCCESS.

### 다음 carry — `MATERIAL-FIDELITY` (recommended W3D4)
sample-app 의 `Theme.AxpFixture` (Material3.DayNight) 가 inflation 시 RenderResources 에 보이도록 app/library resource value loader 추가. 완료 시 primary `activity_basic.xml` (MaterialButton 포함) 가 contingency 없이 PASS. ~500-1000 LOC, W3D1 framework loader 와 동일 패턴.

### α 후속 세션 산출물
- spec round 2: `docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md`.
- plan v2: `docs/superpowers/plans/2026-04-29-w3d3-alpha-bytecode-rewriting.md`.
- session log: `alpha-session-log.md`.
- pair reviews: `alpha-spec-pair-review-round1-{claude,codex}.md`, `alpha-plan-pair-review-round2-{claude,codex}.md`.

---

## 2026-04-29 base 종료 시점 (HISTORICAL — α 가 close)

**완료 (push to origin/main)**:
- W3D3 의 classloader 인프라 8 main + 8 test = 16 신규 파일 + 6 production 수정. 142 unit + 11 integration PASS + 2 SKIPPED.
- spec round 1 + plan round 2 페어 리뷰 (Codex+Claude 각 1:1) 모두 컨버전스 + 정정 적용.
- `axpEmitClasspath` Gradle task 가 sample-app `assembleDebug` 끝에 manifest emit (55 라인 검증).
- `LayoutlibRendererIntegrationTest` 의 SUCCESS assertion + `requireNotNull` 등 round 2 변경은 보존, `@Disabled` 만 다시 추가 (W3D4+ enable 대비).

**BLOCKED (Task 9, branch (C))**:
- `activity_basic.xml` 의 T1 gate 가 ERROR_INFLATION 으로 실패.
- 원인 두 가지 모두 W3D3 scope 밖:
  1. layoutlib 14.0.11 JAR 이 `android.os.Build` 자체를 미포함 (`_Original_Build*` prefix 만). Studio 외부 환경에서 SHIM 부재.
  2. R.jar real id (예: `2130903769 = 0x7f0f03fd`) ↔ callback generated id (`0x7F0A_xxxx`) 불일치 → style resolve 실패. Codex round 1 B3 confirmed.
- 진단: `branch-c-diagnosis.md` (옵션 α/β/γ/δ enumeration).

## 1턴에 읽어야 할 문서

1. `docs/work_log/2026-04-29_w3d3-l3-classloader/session-log.md` — 본 세션 상세.
2. `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md` — root cause #1, #2 + 4 옵션.
3. `docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md` — round 2 spec.
4. `docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md` — v2 plan (Tasks 1-8 close, Task 9 branch C).
5. `docs/plan/08-integration-reconciliation.md §7.7.4` — W3D3 close 기록.

## 다음 세션 작업 옵션 (사용자 결정 필요)

본 회기 종결 시점에서 W3D3 close 를 위해 사용자가 다음 중 1 개 선택:

### Option α — Bytecode rewriting (정공법)

ASM 의존을 worker 에 추가, `SampleAppClassLoader.build` 가 AAR `classes.jar` 적재 시점에 `android/os/Build` → `android/os/_Original_Build` 와 `android/os/Build$VERSION` → `android/os/_Original_Build$VERSION` rename. R.jar id 시드도 같은 작업 단위.

- LOC 추정: 200-400 (rewriter + tests + R.jar seed).
- 장점: layoutlib 의 모든 버전에 자동 작동, Paparazzi 가 검증한 패턴.
- 단점: ASM 의존 + classloader 가 transformer 를 끼고 동작 → 복잡성.
- 예상 일정: 1-2 일.

### Option β — Build shim JAR + R.jar id 시드 (가장 surgical)

자체 빌드 mini-jar `android-shims.jar` 작성. 내용은 `android.os.Build` (와 nested `$VERSION`/`$VERSION_CODES`) 만 포함하며 내부적으로 `android.os._Original_Build` 에 위임. SampleAppClassLoader URL 에 추가. R.jar id 시드는 별도 컴포넌트.

- LOC 추정: shim 50-100 + R.jar seed 100-200 = 150-300.
- 장점: 가장 surgical, ASM 의존 없음, 디버깅 용이.
- 단점: layoutlib 14.x → 15.x 등 버전 변경 시 shim API surface 재검토 필요.
- 예상 일정: 0.5-1 일.

### Option γ — Defer W3D3, AVD-L3 (canonical L3) 진입

`plan/06 §2` 의 emulator-based L3 (harness APK + DexClassLoader on Dalvik). v1 일정 보존.

- LOC 추정: 큼 (plan/06 §2.1-2.4 의 full L3).
- 장점: layoutlib 의 host-JVM 한계를 피함.
- 단점: AVD 의존 + 운영 복잡도 증가 + W3D3 의 classloader 인프라 폐기 (다만 carry 로 유지 가능).
- 예상 일정: 3-5 일.

### Option δ — Partial close (현 회기 산물 유지, W3D3 carry 로 정식 등록)

본 회기에서 push 한 모든 인프라는 그대로. `LayoutlibRendererIntegrationTest` 는 `@Disabled` 채로 carry. W3D4 가 sample-app unblock 외 다른 carry 로 진입 (예: `tier3-glyph` 또는 `POST-W2D6-POM-RESOLVE`).

- LOC: 0 (cleanup 만).
- 장점: 즉시 다른 진척에 집중 가능.
- 단점: 핵심 deliverable (custom view 적재) 가 미완성으로 남음.

**Orchestrator 추천 (현 회기에서 결정 안 함)**: 사용자가 옵션 β 와 옵션 γ 중 선택. β 는 W3D3 의 "가장 가까운 close" 이며, γ 는 host-JVM 우회 — long-term 안정성 차이로 결정.

## 긴급 회복 (빌드가 깨졌을 때)

본 회기의 모든 commit 은 push 됐고, 마지막 commit 이 `@Disabled` 를 복원해 main 의 build 는 PASS 상태. 만약 그 commit 이 어떻게 깨졌다면:

1. `git log --oneline -10 | head` 으로 최근 commit 확인.
2. 본 세션의 push range: `eb07fc7..<latest>` (본 회기 시작 직전 ↔ 종결 시점).
3. main 으로 hard reset 시 시작점은 `eb07fc7` (W3D3 round 2 plan + spec push commit).

## 주의 landmine 재발 방지

- **LM-W3D3-A** (round 1 페어 convergent decision empirical 정정): "tasks.named 가 lazy 라 afterEvaluate 불요" 같이 **편의성으로 합의된 결정** 도 empirical 검증 누락 시 잘못될 수 있다. AGP 의 variant API 등록 시점을 가정하는 모든 코드는 `afterEvaluate` 또는 `androidComponents.onVariants` 안에서 작성.
- **LM-W3D3-B** (subagent kotlin.test 사용): 본 워커 모듈 test classpath 는 `kotlin.test` 미포함. `org.junit.jupiter.api.Assertions.*` + `org.junit.jupiter.api.assertThrows` 만 사용 — subagent prompt 에 명시.
- **LM-W3D3-C** (StringBuilder bootstrap CL == null): `cls.classLoader` 가 null 이 가능 — 테스트에서 ClassLoader 비교는 자체 추적용 ClassLoader (e.g., `TrackingClassLoader` override loadClass) 패턴.
- **LM-W3D3-D** (BLOCKER) (`android.os.Build` 부재): layoutlib JAR 의 framework class 는 IDE 외부에서 직접 사용 불가 — `_Original_*` prefix 변형만 존재. 옵션 α (rewriter) 또는 β (shim JAR) 가 필수.
- **LM-W3D3-E** (BLOCKER) (R.jar real id 와 callback generated id 불일치): host-JVM 환경에서 layoutlib 의 RenderResources 가 sample-app R.jar 의 컴파일된 id 를 받아도 callback 의 byRef/byId 와 mismatch — sample-app inflate 시 style resolve 실패. callback id seeding (R.jar reflection enumerate) 가 정공법.

## 페어 리뷰 인프라 노트

- Codex CLI: `codex exec --skip-git-repo-check --sandbox danger-full-access` 직접 호출 (codex-rescue subagent bypass — W3D2 LM-G).
- Codex 모델/effort: `~/.codex/config.toml` 의 `model = "gpt-5.5"` + `model_reasoning_effort = "xhigh"` 가 default → 별도 인자 불필요.
- 페어 리뷰 산물은 `docs/work_log/<date>_<slug>/{spec,plan}-pair-review-round{1,2}-{codex,claude}.md` 에 보존.
