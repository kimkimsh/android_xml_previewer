# W3D3 — Branch (C) Root-Cause 진단 (orchestrator-level)

**Status**: HARD STOP — user escalation required.
**Date**: 2026-04-29.
**Subagent (Task 9 implementer)**: 정확히 branch (C) 프로토콜 준수 — evidence 캡처, 푸시 없음, BLOCKED 보고.
**Orchestrator (이 문서)**: subagent 가 보고한 stack trace 두 줄을 root-cause level 로 분석.

---

## 1. Subagent 가 발견한 두 줄

```
[layoutlib.error] resources.resolve | Failed to find the style corresponding to the id 2130903769 | null | null
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION msg=android.os.Build$VERSION exc=ClassNotFoundException
```

## 2. Root cause #1 — `android.os.Build$VERSION` ClassNotFoundException

### 2.1 무엇이 발생했나
ConstraintLayout (또는 그 transitive 의존) 의 bytecode 가 `android.os.Build.VERSION.SDK_INT` 를 참조. 우리 SampleAppClassLoader → isolated layoutlib CL 의 parent chain 에서 `android.os.Build$VERSION` 클래스 lookup 이 ClassNotFoundException 으로 실패 → ConstraintLayout `<init>` 이 throw → layoutlib BridgeInflater 가 `Result.Status.ERROR_INFLATION` 으로 wrap.

### 2.2 왜 이게 의외인가 — layoutlib 가 자기 자신은 정상 init 함
W3D1 의 `tier3-values` (`activity_minimal.xml`) 가 PASS — 즉 Bridge.init / RenderSession 자체는 정상 동작. activity_minimal 은 layoutlib 의 INTERNAL inflater 가 처리하는 framework widget 만 사용하므로 외부 classloader 경로를 trigger 하지 않음.

### 2.3 layoutlib JAR 실측 (orchestrator)

```
$ unzip -l server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | grep "android/os/Build"
(empty)

$ unzip -l server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | grep -E "android/os/.*Build"
   android/os/_Original_Build$VERSION.class
   android/os/_Original_Build$VERSION_CODES.class
   android/os/_Original_Build.class
```

**결정적 finding**: layoutlib 14.0.11 JAR 은 `android/os/Build*.class` 를 포함하지 않는다. 단지 `_Original_Build*` prefix 변형만 존재.

### 2.4 왜 layoutlib 가 `_Original_Build` 로 prefix 한 채 배포되나
layoutlib 의 빌드 파이프라인이 `android.os.Build` 를 의도적으로 rename 한 후 재배포. 의도:
- layoutlib 자신의 코드는 `_Original_Build` 만 참조 (참조 사이트가 모두 빌드 시점에 rename 됨).
- 실 `android.os.Build` 는 **호스트 환경 (Android Studio 의 IDE classpath)** 가 SHIM 으로 제공한다고 가정.
- Studio 의 ModuleClassLoader / DesignSurface 가 자체 `android.os.Build` 를 합성해 layoutlib 에 주입.

**우리 환경에는 그 SHIM 이 없다**. dist/ 에 별도의 jar 가 없으며, worker 의 transitive 의존 (`com.android.tools.layoutlib:layoutlib-api:31.13.2` + Guava + kxml2 + ICU4J) 어디에도 `android.os.Build` 클래스가 없다 (검색 0 hit).

### 2.5 layoutlib 자신은 어떻게 SDK_INT 를 평가하는가
layoutlib 자신의 코드는 `_Original_Build.VERSION.SDK_INT` 라는 변형된 reference 를 사용. layoutlib 가 SessionParams 의 `targetSdk` 로부터 자기 SDK 정보를 받기 때문에 외부 `android.os.Build` 가 정상 존재할 필요 없음.

### 2.6 그러나 AAR 의 외부 코드는 `android.os.Build` 그대로 참조
constraintlayout-2.1.4 / material-1.12.0 / appcompat-1.6.1 등의 AAR `classes.jar` 는 `javac` 로 빌드된 보통 JVM 바이트코드 — `android.os.Build.VERSION` 을 그대로 참조한다 (rename 되어 있지 않음). 우리 URLClassLoader chain 으로 이 클래스를 resolve 시도 → 부재 → CNFE.

### 2.7 가능한 fix 전략 (orchestrator 평가)

| 전략 | 장점 | 단점 | LOC 추정 |
|------|------|------|----------|
| **A. Bytecode rewriting (ASM)** — AAR classes.jar 적재 시점에 `android/os/Build` → `android/os/_Original_Build` rename | 정공법; Paparazzi 등 이미 검증 | ASM 의존 추가, classloader 에 ClassFileTransformer 또는 사전-rewrite 단계 도입 | 200-400 |
| **B. 별도 shim JAR** — `android/os/Build.class` (와 nested $VERSION) 를 자체 빌드해 SampleAppClassLoader URL 에 추가. 내부적으로 `_Original_Build` 에 위임 | LOC 적음, 명확한 surface | 호환 surface 가 layoutlib 버전마다 다를 수 있음 — Build API 변경 시 shim 재컴파일 필요 | 50-150 |
| **C. Paparazzi 의 layoutlib 배포로 교체** | 이미 같은 문제를 해결한 distribution | dist 교체 = 큰 변경; w1d3-r2 의 canonical bundle 검증 다시 | 환경 변경 |
| **D. 딴 길로** — AVD-L3 (canonical L3, plan/06 §2) 로 전환 | 호스트 JVM 의존 회피 | v1 일정 6주 → 8 주 가능; plan/06 §6 W4 carry 의 본격 작업 | 본 W3D3 폐기 |

**Orchestrator 추천**: 본 회기에서 결정 보류 — 사용자 escalation 후 결정. B 가 가장 surgical, A 는 가장 정공.

## 3. Root cause #2 — Style id 2130903769 resolve 실패 (Codex round 1 B3 confirmed)

### 3.1 무엇이 발생했나
```
[layoutlib.error] resources.resolve | Failed to find the style corresponding to the id 2130903769 | null | null
[layoutlib.error] null | Failed to find style with id 0x7f0f03fd in current theme
```

`2130903769 == 0x7f0f03fd`. 이 id 는 sample-app `R.jar` 의 `com.fixture.R$style.*` 또는 transitive AAR 의 R$style 중 하나.

### 3.2 Codex round 1 spec-pair-review B3 와의 일치
> "Putting `R.jar` on the classloader is necessary but not sufficient. Current callback generates IDs from `0x7F0A_0000`. Actual `R.attr` IDs from the built `R.jar` are different, e.g. `layout_constraintTop_toTopOf = 2130903705`. Constructors use actual `R.styleable`/`R.attr` ints, but `resolveResourceId()` cannot map those ints back to resources, so `TypedArray` lookups for ConstraintLayout/Material attrs miss."

**확정**: 현 SampleAppClassLoader + R.jar 가 클래스 적재는 가능하게 하지만, layoutlib 의 RenderResources 가 R.jar 의 컴파일된 id 와 callback 의 generated id 를 일치시키지 못한다. spec round 1 Q3 의 R3 가 "broken positioning, but SUCCESS 가능" 이라고 추정했지만, 실제로는 style resolve 실패가 inflate 자체를 막을 수 있다 (FrameworkRenderResources 의 setTheme 이 실패하면 ERROR_INFLATION).

### 3.3 가능한 fix 전략

| 전략 | 장점 | 단점 |
|------|------|------|
| **R.jar id 시드** — startup 에 R.jar 를 reflection 으로 모든 R$* 클래스의 모든 static int 필드 enumerate, ResourceReference 로 mapping 하여 callback 의 byRef/byId 를 사전-populate | 정공법, 한 번만 | R$* 클래스의 namespace 추론 (com.fixture.R$style.X 가 sample-app vs material vs constraintlayout 인지) 필요 |
| **App resource value loader** (W3D4 carry) | 완전한 fidelity | 큰 작업 (~500-1000 LOC, framework loader 와 같은 규모) |

전략 1 이 짧음. 단, root cause #1 이 먼저 해결 안 되면 무의미.

---

## 4. Subagent 가 만든 unpushed local commit 정리

`df772bc` (local only, not pushed): `LayoutlibRendererIntegrationTest` 의 `@Disabled` 가 제거된 채로 남아있다. 본 commit 을 main 에 push 하면 CI 가 실패하므로 해당 변경을 다시 정정해야 한다 — orchestrator 가 다음 동작에서 amend.

---

## 5. 사용자 결정 필요 — 다음 단계 옵션

**Option α — bytecode rewriting (전략 A)**: ASM 의존 추가, AAR classes.jar 의 `android/os/Build*` 참조를 `android/os/_Original_Build*` 로 rewrite 하는 ClassFileTransformer 또는 사전-rewrite. 200-400 LOC. id 시드 (Root cause #2) 도 함께 fix. **W3D3 deliverable 이 본 안에서 close 가능 (예상 1-2 일).**

**Option β — Build shim JAR (전략 B)**: `android.os.Build` 를 직접 작성해 SampleAppClassLoader URL 에 추가. delegate to `_Original_Build`. id 시드도 동시 작업. 50-150 LOC + R.jar id 시드 100-200 LOC. **W3D3 close 가능 (예상 0.5-1 일).** — 가장 surgical.

**Option γ — defer W3D3, AVD-L3 진입 (전략 D)**: plan/06 의 canonical L3. v1 일정 보존. W3D3 의 classloader 인프라 (Tasks 1-8) 는 별도 carry 로 보존하고 재활용 가능 — host-JVM 외부 fixture 코드 동적 적재 시 사용 (예: hot reload of user-edited code).

**Option δ — partial close (인프라만)**: 본 회기에서 Tasks 1-8 의 classloader 인프라는 commit 됨 (이미 push). Task 9 의 통합 테스트는 `@Disabled` 로 되돌리고 명확한 disable 사유로 본 진단 문서 cite. W3D4 가 옵션 α 또는 β 를 선택해 enable. **현 회기 cleanup 비용 가장 낮음.**

**Orchestrator 추천**: Option δ + 다음 회기에 Option β (가장 surgical) 또는 Option α (가장 정공) 결정. 본 회기는 이미 3+ 시간 진행 — 새 architectural 작업을 즉시 시작하는 것보다 깔끔한 stop point 가 합리적.

---

## 6. 부수 commit cleanup 계획 (Option δ 선택 시)

1. `df772bc` 을 reset --soft 로 풀고 local 변경 유지.
2. `LayoutlibRendererIntegrationTest.kt` 에 `@Disabled("W3D3 branch (C) — see docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md")` 다시 추가. SUCCESS assertion 과 `requireNotNull` 같은 다른 변경은 미래의 enable 를 위해 보존.
3. 신규 commit + push.
4. branch-c-diagnosis.md (본 문서) 는 별도 commit.
5. Task 10 (work_log + handoff) 가 본 진단을 cite.

본 회기 종결 시점의 net 상태:
- Tasks 1-8 인프라 push 완료 (활용 가능).
- Task 9 BLOCKED, Task 10 (work_log) 완료.
- Test suite: 142 unit PASS, 11 integration PASS + 2 SKIP (W3D2 baseline 그대로 — `LayoutlibRendererIntegrationTest` 다시 SKIP).
- W3D3 carry: option α 또는 β 결정 후 진입.
