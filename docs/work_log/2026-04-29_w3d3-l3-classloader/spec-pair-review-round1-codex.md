Reading additional input from stdin...
OpenAI Codex v0.125.0 (research preview)
--------
workdir: /home/bh-mark-dev-desktop/workspace/android_xml_previewer
model: gpt-5.5
provider: openai
approval: never
sandbox: danger-full-access
reasoning effort: xhigh
reasoning summaries: none
session id: 019dd7a1-82a3-7d10-8c9b-4a6ab149165c
--------
user
# Codex Pair Review — W3D3 L3 ClassLoader Design Spec

You are reviewing a Kotlin/JVM design spec for adding "in-JVM custom view loading" to an Android-XML-renderer that runs Google's `layoutlib` on the host JVM (not on Android Runtime). The reviewer's job is independent verification of architectural soundness.

**Configuration**: Use the highest available GPT model (currently GPT-5.5 / GPT-5.4 — whichever is the latest) with reasoning effort set to `xhigh`. No time limit. Maximum reasoning depth.

## Project Background (read these first, in order)

Working directory: `/home/bh-mark-dev-desktop/workspace/android_xml_previewer`

1. `docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md` — THE SPEC UNDER REVIEW.
2. `docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md` — what just shipped.
3. `docs/plan/06-revisions-and-decisions.md` §2.1-§2.3 — the canonical (different!) AVD-based L3 design. The spec under review explicitly distinguishes itself from this canonical L3.
4. `docs/plan/08-integration-reconciliation.md` §7.7.3 — W3D1 result + carry list.
5. `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt` — existing isolated-CL setup that the new CL hangs off of (key: parent = `getSystemClassLoader()`, only loads layoutlib JAR, layoutlib-api stays on system CL).
6. `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — render pipeline.
7. `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` — current callback that throws on `loadView` (the file the spec extends).
8. `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt` — how callback gets wired in.
9. `fixture/sample-app/app/build.gradle.kts` — Gradle file the spec wants to extend.
10. `fixture/sample-app/app/src/main/res/layout/activity_basic.xml` — the target layout (ConstraintLayout > LinearLayout > TextView × 2 + MaterialButton).

## What you are reviewing

The spec proposes adding ~290 LOC of production + ~330 LOC of tests + ~25 lines of Gradle to:
1. Emit a manifest of `debugRuntimeClasspath` resolved AAR/JAR absolute paths from `fixture/sample-app/app/build.gradle.kts` (`axpEmitClasspath` task, finalizedBy assembleDebug).
2. At test time, read the manifest, extract `classes.jar` from each AAR to a stable cache, and build a `URLClassLoader` whose parent is the existing isolated layoutlib CL. Add `app/build/intermediates/.../R.jar` to the URLs (without it, R$styleable lookups crash).
3. Extend `MinimalLayoutlibCallback` constructor with a `viewClassLoader` and have `loadView(name, sig, args)` use reflection.
4. Wire the new CL lazily into `LayoutlibRenderer.renderViaLayoutlib`.
5. Enable `LayoutlibRendererIntegrationTest` (currently `@Disabled`) with the existing tier3-values pattern (PNG > 1000 + magic header).

## Critical context the spec asserts (you must verify these)

- **DexClassLoader is NOT used** — the layoutlib runs on host JVM, AAR `classes.jar` is JVM bytecode, so `URLClassLoader` is correct. The user's prompt mentioning "DexClassLoader" is a misnomer. Spec §0.2.
- **No ChildFirst delegation** — class namespaces between layers (android.* / androidx.* / kotlin.*) are disjoint. Spec §0.3.
- **R.jar must be on the AAR-tier classloader URLs** — without `androidx.constraintlayout.widget.R$styleable`, ConstraintLayout's `<init>` will crash. Spec §4.5.
- **Resource VALUE loading (e.g. ConstraintLayout's `app:layout_constraintTop_toTopOf` attr → resource id) is OUT OF SCOPE for W3D3** — accepted limit: render achieves `Result.Status == SUCCESS` with broken positioning. Spec §1.2 R3.
- **Lazy CL build** — activity_minimal (W3D1) does not need any custom view, so loadView is never called → manifest-missing must not fail the existing W3D1 tests. Spec §4.7 Q4.

## What I need from you

A NO_GO / GO_WITH_FOLLOWUPS / GO verdict with:

### A. Blockers (NO_GO)
Issues that, if not fixed, would prevent the implementation from ever working or that violate documented project policy. For each: file/section, exact failure, recommended fix. Cite line numbers.

### B. Strongly recommended (GO_WITH_FOLLOWUPS)
Issues that the implementation can proceed with, but should be addressed before merging. For each: file/section, why, recommended fix.

### C. Pair-review questions in spec §7 (Q1-Q5)
The spec lists five explicit questions for the pair review. Answer each with your stance (agree / disagree / alternative) and one-paragraph reasoning. The questions are about:
- Q1: Gradle task wiring (afterEvaluate vs applicationVariants.all).
- Q2: AAR cache invalidation (mtime vs hash).
- Q3: loadView exception unwrapping (InvocationTargetException).
- Q4: Lazy CL build (option A vs B).
- Q5: T1 gate strictness (SUCCESS status assertion).

### D. Independent issues
Anything not covered above that would surface during implementation. Especially:
- **Verify the R.jar path** by `ls fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar` — does it exist? Spec asserts AGP 8.x emits it there.
- **Verify the `classes.jar` entry name in AARs** by `unzip -l <one of the AARs in ~/.gradle/caches/modules-2/files-2.1/...>` — confirm it's `classes.jar` not something else. Spec relies on this entry name.
- **Verify `debugRuntimeClasspath` resolves AAR + transitive JAR** — sanity check by running `cd fixture/sample-app && ./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>/dev/null | head -50`.
- **layoutlib's `LayoutlibCallback`** abstract methods — does it have any other method (besides `loadView`) that needs custom-view-aware behavior? Look in `~/.gradle/caches/modules-2/files-2.1/com/android/tools/layoutlib/layoutlib-api/31.13.2/` for the JAR and inspect.
- **Memory / fork lifecycle**: integration tests run with `forkEvery(1L)` — each test class fork creates one URLClassLoader. Closing? Leakage? Acceptable?

### E. Verdict
NO_GO / GO_WITH_FOLLOWUPS / GO + one-paragraph rationale.

## Output format

Markdown with sections A, B, C (per Q), D, E. Be concrete — file paths, line numbers, exact commands. No hand-waving.

## What you have access to
You can run any shell command. The codex sandbox is `danger-full-access`. The `~/.gradle/caches/modules-2/files-2.1/` AAR cache is populated. The sample-app `build/` tree is populated (it's been built recently). Read source files freely.
exec
/bin/bash -lc "sed -n '1,220p' /home/bh-mark-dev-desktop/.codex/plugins/cache/openai-curated/superpowers/6807e4de/skills/using-superpowers/SKILL.md" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
---
name: using-superpowers
description: Use when starting any conversation - establishes how to find and use skills, requiring Skill tool invocation before ANY response including clarifying questions
---

<SUBAGENT-STOP>
If you were dispatched as a subagent to execute a specific task, skip this skill.
</SUBAGENT-STOP>

<EXTREMELY-IMPORTANT>
If you think there is even a 1% chance a skill might apply to what you are doing, you ABSOLUTELY MUST invoke the skill.

IF A SKILL APPLIES TO YOUR TASK, YOU DO NOT HAVE A CHOICE. YOU MUST USE IT.

This is not negotiable. This is not optional. You cannot rationalize your way out of this.
</EXTREMELY-IMPORTANT>

## Instruction Priority

Superpowers skills override default system prompt behavior, but **user instructions always take precedence**:

1. **User's explicit instructions** (CLAUDE.md, GEMINI.md, AGENTS.md, direct requests) — highest priority
2. **Superpowers skills** — override default system behavior where they conflict
3. **Default system prompt** — lowest priority

If CLAUDE.md, GEMINI.md, or AGENTS.md says "don't use TDD" and a skill says "always use TDD," follow the user's instructions. The user is in control.

## How to Access Skills

**In Claude Code:** Use the `Skill` tool. When you invoke a skill, its content is loaded and presented to you—follow it directly. Never use the Read tool on skill files.

**In Copilot CLI:** Use the `skill` tool. Skills are auto-discovered from installed plugins. The `skill` tool works the same as Claude Code's `Skill` tool.

**In Gemini CLI:** Skills activate via the `activate_skill` tool. Gemini loads skill metadata at session start and activates the full content on demand.

**In other environments:** Check your platform's documentation for how skills are loaded.

## Platform Adaptation

Skills use Claude Code tool names. Non-CC platforms: see `references/copilot-tools.md` (Copilot CLI), `references/codex-tools.md` (Codex) for tool equivalents. Gemini CLI users get the tool mapping loaded automatically via GEMINI.md.

# Using Skills

## The Rule

**Invoke relevant or requested skills BEFORE any response or action.** Even a 1% chance a skill might apply means that you should invoke the skill to check. If an invoked skill turns out to be wrong for the situation, you don't need to use it.

```dot
digraph skill_flow {
    "User message received" [shape=doublecircle];
    "About to EnterPlanMode?" [shape=doublecircle];
    "Already brainstormed?" [shape=diamond];
    "Invoke brainstorming skill" [shape=box];
    "Might any skill apply?" [shape=diamond];
    "Invoke Skill tool" [shape=box];
    "Announce: 'Using [skill] to [purpose]'" [shape=box];
    "Has checklist?" [shape=diamond];
    "Create TodoWrite todo per item" [shape=box];
    "Follow skill exactly" [shape=box];
    "Respond (including clarifications)" [shape=doublecircle];

    "About to EnterPlanMode?" -> "Already brainstormed?";
    "Already brainstormed?" -> "Invoke brainstorming skill" [label="no"];
    "Already brainstormed?" -> "Might any skill apply?" [label="yes"];
    "Invoke brainstorming skill" -> "Might any skill apply?";

    "User message received" -> "Might any skill apply?";
    "Might any skill apply?" -> "Invoke Skill tool" [label="yes, even 1%"];
    "Might any skill apply?" -> "Respond (including clarifications)" [label="definitely not"];
    "Invoke Skill tool" -> "Announce: 'Using [skill] to [purpose]'";
    "Announce: 'Using [skill] to [purpose]'" -> "Has checklist?";
    "Has checklist?" -> "Create TodoWrite todo per item" [label="yes"];
    "Has checklist?" -> "Follow skill exactly" [label="no"];
    "Create TodoWrite todo per item" -> "Follow skill exactly";
}
```

## Red Flags

These thoughts mean STOP—you're rationalizing:

| Thought | Reality |
|---------|---------|
| "This is just a simple question" | Questions are tasks. Check for skills. |
| "I need more context first" | Skill check comes BEFORE clarifying questions. |
| "Let me explore the codebase first" | Skills tell you HOW to explore. Check first. |
| "I can check git/files quickly" | Files lack conversation context. Check for skills. |
| "Let me gather information first" | Skills tell you HOW to gather information. |
| "This doesn't need a formal skill" | If a skill exists, use it. |
| "I remember this skill" | Skills evolve. Read current version. |
| "This doesn't count as a task" | Action = task. Check for skills. |
| "The skill is overkill" | Simple things become complex. Use it. |
| "I'll just do this one thing first" | Check BEFORE doing anything. |
| "This feels productive" | Undisciplined action wastes time. Skills prevent this. |
| "I know what that means" | Knowing the concept ≠ using the skill. Invoke it. |

## Skill Priority

When multiple skills could apply, use this order:

1. **Process skills first** (brainstorming, debugging) - these determine HOW to approach the task
2. **Implementation skills second** (frontend-design, mcp-builder) - these guide execution

"Let's build X" → brainstorming first, then implementation skills.
"Fix this bug" → debugging first, then domain-specific skills.

## Skill Types

**Rigid** (TDD, debugging): Follow exactly. Don't adapt away discipline.

**Flexible** (patterns): Adapt principles to context.

The skill itself tells you which.

## User Instructions

Instructions say WHAT, not HOW. "Add X" or "Fix Y" doesn't mean skip workflows.

codex
Using `superpowers:using-superpowers` to honor the session workflow, then I’ll do this as an independent architecture review with direct source and build-cache verification.
I’ll keep this as a review workflow: gather the cited context first, then run the concrete verification commands the prompt calls out, then report findings by severity.
  → Read spec and canonical context with line numbers
  • Inspect current layoutlib worker source and Gradle fixture
  • Verify build artifacts, AAR contents, dependencies, and layoutlib-api surface
  • Assess blockers, follow-ups, explicit Q1-Q5, independent issues, verdict
codex
I’m starting with the spec and the shipped/canonical context so the review can separate intentional divergence from accidental drift. After that I’ll validate the local artifacts rather than trusting the spec’s assumptions.
exec
/bin/bash -lc "nl -ba docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	# W3D3 — L3 ClassLoader (in-JVM custom view) 설계 스펙
     2	
     3	> **Date**: 2026-04-29
     4	> **Scope**: Option A — sample-app `activity_basic.xml` (ConstraintLayout / MaterialButton) 의 layoutlib 렌더 unblock.
     5	> **Canonical refs**:
     6	> - `docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md` §"Option A".
     7	> - `docs/plan/06-revisions-and-decisions.md §1.1, §2.3` (canonical L3 = AVD/harness-APK + DexClassLoader; **본 W3D3 는 그 L3 가 아님** — §0.3 참조).
     8	> - `docs/plan/08-integration-reconciliation.md §7.7.3` (W3D1 carry: tier3-glyph + W3-CLASSLOADER-AUDIT F-4).
     9	
    10	---
    11	
    12	## 0. 용어 정리 (CRITICAL — 페어 리뷰 전 명확화)
    13	
    14	### 0.1 "L3" 의 두 가지 의미
    15	
    16	이 프로젝트에서 "L3" 는 두 다른 메커니즘을 지칭한다:
    17	
    18	| 명명 | 메커니즘 | 위치 | 상태 |
    19	|------|---------|------|------|
    20	| **canonical L3** (`plan/06 §2.1`) | AVD 에뮬레이터 + harness APK + Dalvik DexClassLoader | 별도 Android 런타임 프로세스 | v1.0 W3 후반/W4 carry |
    21	| **본 W3D3 L3** (=in-JVM custom view) | layoutlib host JVM 안에서 AAR `classes.jar` 를 URLClassLoader 로 적재 | layoutlib-worker JVM | 본 스펙 타겟 |
    22	
    23	핸드오프 `Option A` 와 사용자 prompt 의 "DexClassLoader 기반 L3 class loading" 표현은 **후자** 를 의미한다 (사용자 prompt 의 dex/jar 양쪽 허용 표현이 근거). 본 스펙 안에서는 혼동 회피를 위해:
    24	- **canonical L3** = AVD-L3.
    25	- **본 스펙 타겟** = "in-JVM custom view loading" 또는 "L3-classloader" (개행 시 줄임 — 동일 의미).
    26	
    27	### 0.2 DexClassLoader 가 본 스펙에서 "쓰이지 않는" 이유
    28	
    29	`dalvik.system.DexClassLoader` 는 Android 런타임(ART/Dalvik) 전용 API 이며, host JVM 에는 존재하지 않는다. layoutlib 은 host JVM 위에서 `libandroid_runtime.so` 만 native 로 로드하여 Android 프레임워크 클래스를 JVM 바이트코드 형태로 포함한 layoutlib JAR 을 사용한다. AAR 의 `classes.jar` 도 D8 변환 전의 **JVM 바이트코드** (= classfile, magic `0xCAFEBABE`) — DEX(매직 `dex.035`) 가 아니다.
    30	
    31	따라서 본 스펙은 `java.net.URLClassLoader` 를 사용한다. 사용자 prompt 의 "dex/jar classloader" 표현은 jar branch 를 채택.
    32	
    33	### 0.3 ChildFirst 정책 검토
    34	
    35	`plan/06 §2.3` 의 ChildFirstDexClassLoader 정책은 AVD-L3 한정 — harness APK 의 androidx 가 사용자 앱 androidx 보다 우선되지 않도록 child-first delegation 을 강제하기 위함. 본 스펙은 다음 구조이므로 ChildFirst 가 **불필요**:
    36	
    37	```
    38	system CL          ← Kotlin stdlib, layoutlib-api, kxml2
    39	   ↑ parent
    40	isolated CL        ← layoutlib JAR (android.view.*, android.widget.*, android.content.*)
    41	   ↑ parent
    42	AAR URL CL         ← constraintlayout/material/appcompat/... classes.jar + sample-app R.jar
    43	```
    44	
    45	각 계층의 클래스가 disjoint:
    46	- `android.*` → isolated CL only.
    47	- `androidx.*` / `com.google.android.material.*` / `com.fixture.*` → AAR CL only.
    48	- `kotlin.*` / `kxml2.*` / `org.xmlpull.*` → system CL.
    49	
    50	shadowing 가능 클래스 없음 → parent-first 안전. ChildFirst 도입은 YAGNI.
    51	
    52	---
    53	
    54	## 1. 요구사항 (W3D3 deliverable)
    55	
    56	### 1.1 In-scope (T1 gate 통과 목표)
    57	
    58	1. AAR 의 `classes.jar` 를 host JVM 클래스로더로 적재.
    59	2. `MinimalLayoutlibCallback.loadView(name, sig, args)` 가 위 클래스로더로부터 custom view 를 reflection-instantiate.
    60	3. sample-app 의 `R.jar` (이미 빌드되어 존재) 를 클래스로더 URL 에 포함 — `androidx.constraintlayout.widget.R$styleable` 등이 정상 resolve 되어야 함.
    61	4. `LayoutlibRendererIntegrationTest` 의 `@Disabled` 제거 + tier3-values 패턴 (T1 gate: `Result.Status == SUCCESS && PNG > 1000 bytes`) 통과.
    62	
    63	### 1.2 Out-of-scope (W3D4+ carry)
    64	
    65	다음은 본 W3D3 에서 **다루지 않는다**. 페어 리뷰가 in-scope 이동을 요구하면 별도 합의:
    66	
    67	- **App/library resource value loading**: AAR 의 `res/values/values.xml` 파싱 + RenderResources 통합. 영향: ConstraintLayout 의 `app:layout_constraintTop_toTopOf` 등 custom attr 가 default(0/null) 로 fallback. layout 위치가 잘못되어도 `Result.Status == SUCCESS` + PNG > 1000 bytes 는 통과 가능.
    68	- **Visual fidelity assertion** (pixel level). T1 gate 는 status + 크기만 검사.
    69	- **AVD-L3** (canonical L3 — `plan/06 §2`).
    70	- **tier3-glyph** (W4+ carry).
    71	
    72	### 1.3 Acceptance Gate (T1)
    73	
    74	```kotlin
    75	// LayoutlibRendererIntegrationTest 가 expect:
    76	val bytes = renderer.renderPng("activity_basic.xml")
    77	assertEquals(SUCCESS, renderer.lastSessionResult.status)   // 페어 리뷰가 강하게 권장하면 추가
    78	assertTrue(bytes.size > 1000)                              // 기존 패턴
    79	assertTrue(bytes[0..3] == PNG_MAGIC)                       // 기존 패턴
    80	```
    81	
    82	---
    83	
    84	## 2. 컴포넌트 분해
    85	
    86	| # | 파일 | 책임 | LOC 추정 |
    87	|---|------|------|----------|
    88	| C1 | `fixture/sample-app/app/build.gradle.kts` (수정) | `axpEmitClasspath` Gradle task — 해석된 debugRuntimeClasspath 의 AAR + JAR 절대 경로를 manifest 로 emit. `assembleDebug` 에 finalizedBy. | +25 |
    89	| C2 | `server/layoutlib-worker/.../classloader/SampleAppClasspathManifest.kt` | manifest 파일 (텍스트, 한 줄에 한 절대 경로) 파싱. 누락/오래됨/형식 오류 → 명시 예외. | 60 |
    90	| C3 | `server/layoutlib-worker/.../classloader/AarExtractor.kt` | `.aar` ZIP 에서 `classes.jar` 를 stable cache (`<sample-app>/build/axp/aar-classes/<sha1>/<artifact>.jar`) 로 추출. mtime 기반 idempotent. | 70 |
    91	| C4 | `server/layoutlib-worker/.../classloader/SampleAppClassLoader.kt` | 위 manifest+extractor 결과 + R.jar 위치 → `URLClassLoader(parent = isolatedCL)`. 빌드된 CL 인스턴스 + 진단용 entry list. | 60 |
    92	| C5 | `server/layoutlib-worker/.../classloader/ClassLoaderConstants.kt` | 패키지 상수 (manifest 파일명, R.jar 상대 경로, AAR cache 디렉토리명). | 30 |
    93	| C6 | `MinimalLayoutlibCallback.kt` (수정) | 생성자에 `viewClassLoader: ClassLoader` 추가. `loadView` 가 reflection 으로 instantiate. UnsupportedOperationException 분기 제거. | +25 |
    94	| C7 | `LayoutlibRenderer.kt` (수정) | `renderViaLayoutlib` 가 SampleAppClassLoader 를 빌드 후 callback 에 주입. lazy + per-renderer 캐시. | +20 |
    95	| C8 | `LayoutlibRendererIntegrationTest.kt` (수정) | `@Disabled` 제거. T1 gate assertion 추가. SharedLayoutlibRenderer 호출은 유지. | +10 |
    96	| **Tests** | | | |
    97	| TC1 | `SampleAppClasspathManifestTest` (단위 5) | manifest 파일 누락 / 빈 / 잘못된 라인 / 정상 / 절대경로 강제. | 80 |
    98	| TC2 | `AarExtractorTest` (단위 4) | 정상 추출 / 캐시 hit / 손상 AAR / classes.jar 없는 AAR. | 100 |
    99	| TC3 | `SampleAppClassLoaderTest` (단위 3) | 클래스로더 합성 / parent chain / R.jar 포함 검증. tmp dir + minimal jar fixture. | 80 |
   100	| TC4 | `MinimalLayoutlibCallbackLoadViewTest` (단위 3) | 새 생성자 / reflection instantiate 성공 / 클래스 없음 → ClassNotFoundException. | 70 |
   101	
   102	추정 합계: production ~290 LOC, test ~330 LOC, +25 Gradle.
   103	
   104	---
   105	
   106	## 3. 데이터 흐름
   107	
   108	```
   109	[Build time, fixture/sample-app]
   110	  ./gradlew :app:assembleDebug
   111	    └─ tasks: ... → axpEmitClasspath (finalizedBy assembleDebug)
   112	        └─ writes: app/build/axp/runtime-classpath.txt
   113	                   각 라인: <abs path to .aar or .jar>
   114	
   115	[Test time, server/layoutlib-worker]
   116	  LayoutlibRenderer.renderPng("activity_basic.xml")
   117	    └─ initBridge() (기존)
   118	    └─ buildSampleAppClassLoader(fixtureRoot) [lazy, per-renderer]
   119	        ├─ SampleAppClasspathManifest.read(fixtureRoot.../runtime-classpath.txt)
   120	        │     → List<Path> entries
   121	        ├─ entries.map { path ->
   122	        │     when {
   123	        │       path ends ".aar" → AarExtractor.extract(path) // → classes.jar path
   124	        │       path ends ".jar" → path
   125	        │       else → IllegalStateException
   126	        │     }
   127	        │   }
   128	        ├─ + sample-app R.jar (fixtureRoot.../intermediates/.../R.jar)
   129	        └─ URLClassLoader(urls, parent = isolatedClassLoader)
   130	    └─ renderViaLayoutlib(layoutName, viewClassLoader)
   131	        ├─ MinimalLayoutlibCallback(viewClassLoader)
   132	        ├─ SessionParamsFactory.build(...)
   133	        └─ Bridge.createSession(params).render()
   134	            ├─ inflater 가 LayoutlibCallback.loadView 호출
   135	            └─ callback.loadView("androidx.constraintlayout.widget.ConstraintLayout", [Context, AttributeSet], [...])
   136	                └─ viewClassLoader.loadClass(name).getConstructor(...).newInstance(...)
   137	```
   138	
   139	---
   140	
   141	## 4. 컴포넌트 스펙
   142	
   143	### 4.1 C1 — Gradle manifest task
   144	
   145	`fixture/sample-app/app/build.gradle.kts` 의 끝에 추가:
   146	
   147	```kotlin
   148	// 본 task 는 W3D3 in-JVM custom view 로딩 지원 — server/layoutlib-worker 가
   149	// debugRuntimeClasspath (resolved AAR + transitive JAR) 의 절대경로 manifest 를 읽어
   150	// 자체 URLClassLoader 를 구성한다. Maven cache 의 transforms-* hash dir 의 불안정성을
   151	// 우회하기 위해 본 manifest 가 single source of truth 가 된다.
   152	val axpClasspathManifest = layout.buildDirectory.file("axp/runtime-classpath.txt")
   153	val axpEmitClasspath = tasks.register("axpEmitClasspath") {
   154	    val cpProvider = configurations.named("debugRuntimeClasspath")
   155	    inputs.files(cpProvider)
   156	    outputs.file(axpClasspathManifest)
   157	    doLast {
   158	        val cp = cpProvider.get()
   159	        val artifacts = cp.resolvedConfiguration.resolvedArtifacts
   160	            .map { it.file.absolutePath }
   161	            .sorted()
   162	        val outFile = axpClasspathManifest.get().asFile
   163	        outFile.parentFile.mkdirs()
   164	        outFile.writeText(artifacts.joinToString("\n"))
   165	    }
   166	}
   167	afterEvaluate {
   168	    tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
   169	}
   170	```
   171	
   172	**계약**:
   173	- 출력 경로: `fixture/sample-app/app/build/axp/runtime-classpath.txt`.
   174	- 형식: 라인 separator `\n`, 각 라인 = AAR 또는 JAR 의 절대경로. trailing newline 없음 (joinToString 결과).
   175	- 정렬: lexicographic — reproducibility 를 위함.
   176	- 경로는 `.aar` 또는 `.jar` 로 끝나야 함. 그 외 형식 발견 시 worker 가 거부 (TC1 가 검증).
   177	
   178	**왜 emit 시점 = `finalizedBy(assembleDebug)`?**: 사용자 흐름 `./gradlew :app:assembleDebug` 가 보통 W3 dev 사이클의 entry point. 별도 task 명령 강제 시 forget 위험.
   179	
   180	**페어 리뷰 Q1**: `applicationVariants.all` 또는 androidComponents API 를 써야 할까? — `debugRuntimeClasspath` 은 표준 configuration 이름 (AGP 8.x 안정), 직접 참조 OK. 그러나 향후 flavor 추가 시 break — 본 fixture 는 flavor 없음, defer.
   181	
   182	### 4.2 C2 — SampleAppClasspathManifest
   183	
   184	```kotlin
   185	package dev.axp.layoutlib.worker.classloader
   186	
   187	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
   188	import java.nio.file.Files
   189	import java.nio.file.Path
   190	
   191	/**
   192	 * fixture/sample-app/app/build/axp/runtime-classpath.txt 파일을 읽어
   193	 * resolved runtime classpath 의 AAR/JAR 절대경로 리스트를 제공한다.
   194	 *
   195	 * 파일 형식 (axpEmitClasspath Gradle task 가 emit):
   196	 *  - 라인 separator '\n', trailing newline 없음.
   197	 *  - 각 라인 = AAR 또는 JAR 의 절대 경로.
   198	 *  - 정렬: lexicographic.
   199	 *
   200	 * 누락 / 빈 라인 (whitespace 만) / 비-절대경로 / 비-aar/jar 확장자는 모두 throw.
   201	 * "fail loud at fixture-prep boundary" — 부분 적재로 silent 한 ClassNotFoundException 보다
   202	 * 명시적 에러가 디버깅 비용 낮음.
   203	 */
   204	object SampleAppClasspathManifest {
   205	
   206	    fun read(fixtureRoot: Path): List<Path> {
   207	        val mf = fixtureRoot.resolve(MANIFEST_RELATIVE_PATH)
   208	        require(Files.isRegularFile(mf)) {
   209	            "axp classpath manifest 누락: $mf — `./gradlew :app:assembleDebug` 를 먼저 실행하세요"
   210	        }
   211	        val raw = Files.readString(mf)
   212	        if (raw.isBlank()) {
   213	            error("axp classpath manifest 가 비어있음: $mf")
   214	        }
   215	        return raw.split('\n').mapIndexed { idx, line ->
   216	            require(line.isNotBlank()) { "manifest line ${idx + 1} 이 공백" }
   217	            val p = Path.of(line)
   218	            require(p.isAbsolute) { "manifest line ${idx + 1} 이 비-절대경로: '$line'" }
   219	            require(line.endsWith(".aar") || line.endsWith(".jar")) {
   220	                "manifest line ${idx + 1} 의 확장자가 .aar/.jar 가 아님: '$line'"
   221	            }
   222	            require(Files.isRegularFile(p)) { "manifest line ${idx + 1} 의 파일이 없음: $p" }
   223	            p
   224	        }
   225	    }
   226	}
   227	```
   228	
   229	### 4.3 C3 — AarExtractor
   230	
   231	```kotlin
   232	package dev.axp.layoutlib.worker.classloader
   233	
   234	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
   235	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
   236	import java.io.IOException
   237	import java.nio.file.Files
   238	import java.nio.file.Path
   239	import java.security.MessageDigest
   240	import java.util.zip.ZipFile
   241	import kotlin.io.path.isRegularFile
   242	import kotlin.io.path.outputStream
   243	
   244	/**
   245	 * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출.
   246	 *
   247	 * Cache 위치: `<fixtureRoot>/app/build/axp/aar-classes/<sha1(absPath)>/<artifactName>.jar`.
   248	 * Idempotent: 캐시된 파일의 mtime >= AAR 의 mtime 이면 재사용.
   249	 *
   250	 * AAR 안에 classes.jar 가 없는 경우 (e.g. 순수 resource-only AAR) → 경고 + null 반환 (호출자가 skip).
   251	 * 손상된 ZIP → IOException pass-through.
   252	 */
   253	object AarExtractor {
   254	
   255	    /**
   256	     * @return 추출된 classes.jar 의 Path, classes.jar 가 AAR 에 없으면 null.
   257	     */
   258	    fun extract(aarPath: Path, cacheRoot: Path): Path? {
   259	        require(aarPath.isRegularFile()) { "AAR 누락: $aarPath" }
   260	        val key = sha1(aarPath.toAbsolutePath().toString())

exec
/bin/bash -lc "nl -ba docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	# W3D2 → W3D3+ Handoff
     2	
     3	## 상태 요약 (2026-04-24 종료 시점)
     4	
     5	- W3D1 pair-review deferred carry 3 개 (C1 default 제거, C2 CLI-DIST-PATH, C3 TEST-INFRA-SHARED-RENDERER) 모두 CLOSED.
     6	- Unit 118 + Integration 11 PASS + 2 SKIPPED (tier3-glyph, LayoutlibRendererIntegrationTest@Disabled).
     7	- LayoutlibRenderer 생성자 default parameter 0 — CLAUDE.md "No default parameter values" 완전 준수.
     8	- Main.kt 가 `--dist-dir=<path>` / `--fixture-root=<path>` CLI override 지원 (optional, auto-detect fallback).
     9	- SharedLayoutlibRenderer 싱글톤이 per-JVM-fork 에서 in-class L4 (native lib) 차단.
    10	- Codex pair-review 는 `codex exec --sandbox danger-full-access --skip-git-repo-check` direct CLI 로 사용 (codex:codex-rescue subagent 는 bwrap 실패).
    11	
    12	## 1턴에 읽어야 할 문서
    13	
    14	1. `docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md` — 본 세션 상세.
    15	2. `docs/superpowers/specs/2026-04-24-w3d2-integration-cleanup-design.md` — W3D2 설계 (MF-B1~B4 반영 완료).
    16	3. `docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md` — 본 플랜 (모든 11 Task 체크박스 닫힘 확인).
    17	4. `docs/plan/08-integration-reconciliation.md` — 전체 로드맵 (W3D2 완료 상태 업데이트 필요 시).
    18	
    19	## 다음 세션 작업 옵션
    20	
    21	### Option A (권장) — sample-app unblock (W3 본론)
    22	
    23	`fixture/sample-app/app/src/main/res/layout/activity_basic.xml` 의 ConstraintLayout / MaterialButton 등 커스텀 뷰 지원.
    24	- 핵심 구현: DexClassLoader 로 Android support/material artifact (AAR) 로부터 클래스 로드.
    25	- `MinimalLayoutlibCallback.loadView` 확장해 위임.
    26	- `LayoutlibRendererIntegrationTest` 의 `@Disabled` 해제 후 tier3-values 패턴 assertion.
    27	
    28	### Option B — tier3-glyph (W4 target)
    29	
    30	- Font wiring + StaticLayout + Canvas.drawText JNI 경로 증명.
    31	- T2 gate (activity_minimal TextView 영역 dark pixel >= 20) unblock.
    32	
    33	### Option C — POST-W2D6-POM-RESOLVE / W3-CLASSLOADER-AUDIT (기존 carry)
    34	
    35	## 긴급 회복 (빌드가 깨졌을 때)
    36	
    37	1. `git` 관리가 없으므로 수동 rollback 필요. 본 세션의 신규 파일을 rm + 수정 파일을 W3D1 상태 (`W3D1 session-log §2`) 로 복구.
    38	2. `./server/gradlew -p server test` 로 W3D1 state (99 unit + 8 integration PASS + 2 SKIPPED) 복귀 확인.
    39	
    40	## 주의 landmine 재발 방지
    41	
    42	- **L6** Codex CLI trust-check hang — non-git 프로젝트에서 항상 `--skip-git-repo-check` 플래그.
    43	- **LM-G** codex-companion sandbox 실패 — bwrap user namespace 제약이 있는 환경에서는 `codex:codex-rescue` subagent 대신 `codex exec --sandbox danger-full-access --skip-git-repo-check` direct CLI 사용.
    44	- **LM-F1** `SharedLayoutlibRenderer` 에 `resetForTestOnly()` 를 추가하지 말 것. 이전 설계에서 고려됐으나 native lib unload 불가 로 의미 없는 dead API — MF-F1 로 제외됨.
    45	- **LM-B3** singleton 은 per-JVM-fork scope. Gradle `forkEvery(1L)` 가 integration test class 단위 JVM 격리 보장. "JVM-wide" 가 아닌 "per-test-class-fork" 로 이해할 것.

exec
/bin/bash -lc "nl -ba docs/plan/06-revisions-and-decisions.md | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	# 개정 및 결정사항 (Phase 6 리뷰 반영)
     2	
     3	> **상태**: APPROVED (2026-04-22) → 일부 섹션은 07-deep-improvements에서 개정됨
     4	> **우선순위**: `00`~`04`보다 우선하지만, **`07`보다 아래**. 구현 세부 충돌 시 `07` 우선.
     5	> **목적**: Phase 6 페어 리뷰(`05-pair-review-final.md`) 후 확정된 L3 방향(옵션 α) + 7개 비논쟁 픽스를 한 곳에 정리.
     6	>
     7	> **07에서 개정된 섹션 안내**:
     8	> - §2.1 L3 렌더 플로우 → 07 §3.1-3.2 (createPackageContext 패턴)
     9	> - §2.2 app APK 감지 룰 → 07 §3.4 (DEX preflight 우선)
    10	> - §2.3 DexClassLoader 위험 완화 → 07 §3.3 (ChildFirstDexClassLoader)
    11	> - §4 Unrenderable Enum → 07 §3.4에 AXP-L3-008/009/010 추가
    12	> - §5 Concurrency 모델 → 07 §4.4 (publish gate 추가)
    13	
    14	---
    15	
    16	## 1. 결정 기록
    17	
    18	### 1.1 L3 Classpath 이슈 — 옵션 α 선택
    19	- **결정일**: 2026-04-22
    20	- **선택**: **옵션 α (v1에 full classpath-aware L3)**
    21	- **영향**:
    22	  - 커버리지 목표 **85%** 유지
    23	  - v1 일정 **4주 → 6주** 확장
    24	  - 아키텍처에 **DexClassLoader + 사용자 앱 APK 감지** 추가
    25	  - 완전한 standalone은 아니지만 Gradle에 hard-lock되지도 않음 (app APK 이미 빌드되어 있다고 가정)
    26	- **이유**:
    27	  - 85% 서사를 정직하게 지키려면 custom view 렌더 필수
    28	  - "v1.0 대충 → v1.1 제대로"의 점진 전략은 첫 사용자 신뢰를 훼손
    29	  - DexClassLoader 주입은 검증된 패턴 (LeakCanary, Flipper 등에서 사용)
    30	
    31	### 1.2 7개 비논쟁 픽스 적용
    32	페어 양쪽이 일치한 수정 사항들 (사용자 추가 결정 불필요, 즉시 canonical로 확정):
    33	
    34	| # | 픽스 | 원 섹션 | 변경 |
    35	|---|------|---------|------|
    36	| F1 | 에러 카탈로그 도입 | `01 §3.1` | `Unrenderable(reason: String)` → `Unrenderable(reason: UnrenderableReason, detail: String, remediation: String)` |
    37	| F2 | per-layout-key 동시성 mutex | `01 §3.1` | RenderDispatcher에 `Map<RenderKey, Mutex>` single-flight 보장 |
    38	| F3 | Cache key 확장 | `01 §7` | 6개 → 14개 입력 (본 문서 §3) |
    39	| F4 | Cache invalidation 통일 | `01 §3.3, §6` | v1은 conservative(전체 무효화). 의존 그래프는 v1.5로 명시 이관 |
    40	| F5 | CLI mode v1 승격 | `03 §2` (v1.5 → v1) | `axprev render <file> --out foo.png` 커맨드 v1 필수 범위 |
    41	| F6 | 문서 surface 확장 | `01 §directory, 03 §W4` | `CONTRIBUTING.md`, `ARCHITECTURE.md`, `CHANGELOG.md`, `SECURITY.md`, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`, `docs/UNINSTALL.md`, `docs/FAQ.md` (Why not Paparazzi) 추가 |
    42	| F7 | CLI 바이너리명 변경 | 전체 | `axp` → `axprev` (퍼블릭 브랜드 `android-xml-previewer` 유지). npm/PyPI 충돌 회피 |
    43	
    44	---
    45	
    46	## 2. 개정된 아키텍처: L3 (classpath-aware)
    47	
    48	### 2.1 L3 렌더 플로우 (REVISED)
    49	
    50	```
    51	User saves foo.xml
    52	  │
    53	  ▼
    54	L1 (layoutlib) 시도
    55	  ├─ 성공 → PNG 반환, cache put
    56	  └─ 실패 (ClassNotFoundException, binding) → L3 시도
    57	       │
    58	       ▼
    59	  ┌────────────────────────────────────────┐
    60	  │ EmulatorRenderer (classpath-aware)      │
    61	  │                                         │
    62	  │ 1. AndroidProjectLocator가 app APK 탐색  │
    63	  │    ├─ app/build/outputs/apk/debug/      │
    64	  │    │   app-debug.apk                     │
    65	  │    └─ 없으면 Unrenderable(NO_APP_APK,    │
    66	  │       "build :app:assembleDebug 먼저")   │
    67	  │                                         │
    68	  │ 2. adb push foo.xml → AVD tmp            │
    69	  │ 3. adb push app-debug.apk → AVD tmp      │
    70	  │ 4. am start com.axp.harness \            │
    71	  │      --es xml_path /tmp/foo.xml \        │
    72	  │      --es app_apk_path /tmp/app.apk      │
    73	  │                                         │
    74	  │ 5. HarnessActivity:                      │
    75	  │    DexClassLoader(app_apk_path, …)       │
    76	  │    └→ LayoutInflater.Factory 등록        │
    77	  │    └→ resolveNameToClass가 user app     │
    78	  │       classloader에서도 lookup           │
    79	  │    └→ inflate + bitmap + 저장            │
    80	  │                                         │
    81	  │ 6. adb pull out.png                      │
    82	  └────────────────────────────────────────┘
    83	```
    84	
    85	### 2.2 app APK 감지 룰
    86	
    87	`AndroidProjectLocator.kt`에 새 메서드:
    88	```kotlin
    89	fun findAppApk(projectRoot: Path): Result<Path, NoAppApkReason> {
    90	  val candidates = listOf(
    91	    "app/build/outputs/apk/debug/app-debug.apk",
    92	    "build/outputs/apk/debug/app-debug.apk",
    93	    "**/build/outputs/apk/*/debug/*.apk"  // multi-module fallback
    94	  )
    95	  // 가장 최근 APK 선택
    96	  // 없으면 NoAppApkReason.NOT_BUILT 반환 + remediation hint
    97	}
    98	```
    99	
   100	### 2.3 DexClassLoader 주입 위험 완화
   101	
   102	- **AppCompat/Material 버전 불일치**: harness APK의 androidx 버전과 사용자 앱 androidx 버전이 다르면 `NoSuchMethodError`. 완화: harness APK를 **minSdk 21, compileSdk 34, androidx 최소 의존**으로 빌드. 사용자 앱이 더 최신 androidx를 쓰면 그 apk의 dex가 override.
   103	- **Manifest merge 이슈**: harness APK의 theme이 사용자 앱 테마를 override하지 못함. 완화: harness Activity가 `setTheme(resources.getIdentifier(...))`로 런타임 테마 적용.
   104	- **ClassLoader leak**: 매 렌더마다 새 DexClassLoader 생성하면 메모리 누수. 완화: `LruCache<AppApkHash, DexClassLoader>` 5개 유지.
   105	
   106	### 2.4 새로운 실패 모드
   107	
   108	| 실패 | 원인 | Unrenderable Reason | Remediation |
   109	|------|------|---------------------|-------------|
   110	| `NO_APP_APK` | `app/build/outputs/apk/debug/*.apk` 없음 | NO_APP_APK_BUILT | "build :app:assembleDebug 실행 후 재시도" |
   111	| `APK_TOO_OLD` | app APK가 3일 이상 된 경우 | STALE_APP_APK | "최근 소스 변경 반영 위해 :app:assembleDebug 재실행 권장" |
   112	| `DEX_LOAD_FAIL` | DexClassLoader 초기화 실패 | DEX_LOAD_ERROR | 스택 트레이스 + 이슈 리포트 링크 |
   113	| `ANDROIDX_MISMATCH` | harness/app androidx 버전 충돌 | ANDROIDX_CONFLICT | "plugin 업그레이드 권장" |
   114	| `HARNESS_APK_ABI_MISMATCH` | AVD ABI와 harness ABI 불일치 | ABI_MISMATCH | "x86_64 AVD 사용 권장" |
   115	
   116	---
   117	
   118	## 3. 개정된 Cache Key (F3)
   119	
   120	### 3.1 Canonical Cache Key Inputs
   121	
   122	```kotlin
   123	data class RenderKey(
   124	  // 1. Source content
   125	  val xmlContentSha256: String,             // 정규화된 XML 내용 (LF only)
   126	  val normalizedRelativePath: String,       // 프로젝트 루트 기준 상대 경로
   127	  val resourceTableSha256: String,          // 컴파일된 .arsc sha256
   128	
   129	  // 2. Device/display
   130	  val devicePreset: String,                 // phone_small / phone_normal / ...
   131	  val themeId: String,                      // light / dark / AppTheme.Dark
   132	  val nightMode: Boolean,
   133	  val fontScale: Float,                     // 0.85 / 1.0 / 1.15 / 1.30
   134	  val locale: String,                       // en-US / ko-KR / ...
   135	
   136	  // 3. Renderer layer + version
   137	  val renderLayer: RenderLayer,             // LAYOUTLIB | EMULATOR
   138	  val layoutlibJarSha256: String,           // 번들 JAR sha256
   139	  val bundledFontPackSha256: String,        // Noto fonts 등 번들 폰트 해시
   140	  val apiLevel: Int,                        // 34
   141	
   142	  // 4. Tool versions
   143	  val aapt2Version: String,                 // "34.0.0"
   144	  val sdkBuildToolsVersion: String,
   145	  val jvmMajor: Int,                        // 11 / 17 / 21
   146	
   147	  // 5. L3 specific (L3 렌더일 때만)
   148	  val appApkSha256: String?,
   149	  val harnessApkSha256: String?,
   150	  val avdSystemImageFingerprint: String?
   151	)
   152	
   153	fun RenderKey.digest(): String = sha256(
   154	  canonicalJson(this)  // stable serialization
   155	)
   156	```
   157	
   158	### 3.2 불변 입력 보장
   159	
   160	- **XML content 정규화**: `\r\n → \n`, trailing whitespace strip, 파일 끝 개행 통일
   161	- **상대 경로**: `projectRoot.relativize(xmlFile)` → 항상 `app/src/main/res/layout/foo.xml` 형태
   162	- **폰트**: 플러그인이 번들하는 `fonts/` 디렉토리 전체를 tarball sha256
   163	- **layoutlib**: 번들 JAR 파일 sha256 (첫 실행 시 계산, 이후 캐시)
   164	
   165	---
   166	
   167	## 4. 개정된 Unrenderable Enum (F1)
   168	
   169	```kotlin
   170	enum class UnrenderableReason(
   171	  val code: String,           // 안정적 머신 읽기용
   172	  val category: Category,
   173	  val remediationUrl: String  // docs/TROUBLESHOOTING.md#<code>
   174	) {
   175	  // L1 실패
   176	  L1_PARSE_ERROR("AXP-L1-001", Category.USER_CODE, "#l1-parse-error"),
   177	  L1_CUSTOM_VIEW_CLASS_NOT_FOUND("AXP-L1-002", Category.CUSTOM_VIEW, "#l1-custom-view"),
   178	  L1_DATA_BINDING_NOT_EVALUATED("AXP-L1-003", Category.DATA_BINDING, "#l1-data-binding"),
   179	  L1_LAYOUTLIB_BUG("AXP-L1-004", Category.TRANSIENT, "#l1-layoutlib-bug"),
   180	  L1_OOM("AXP-L1-005", Category.RESOURCE_LIMIT, "#oom"),

exec
/bin/bash -lc "nl -ba docs/plan/08-integration-reconciliation.md | sed -n '300,460p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   300	- 라이선스: 플러그인 `THIRD_PARTY_NOTICES.md`에 layoutlib-api Apache 2.0 고지
   301	- 번들 크기: framework data ~40MB + layoutlib JAR ~8MB + native libs ~15MB = 총 ~60MB
   302	
   303	**Week 1 Day 1 액션 아이템**: 라이선스 검토 체크포인트.
   304	
   305	### 3.4 Theme 선택 로직
   306	
   307	**오류**: `07 §3.1`의 `appResources.getIdentifier("AppTheme", ...)`는 **하드코드된 `AppTheme` 가정**. 실제 프로젝트는:
   308	- Manifest `<application android:theme="@style/MyFancyTheme">`
   309	- 각 Activity의 `android:theme` override
   310	- Flavor별 다른 테마
   311	- Runtime theme overlay (dark mode)
   312	
   313	**교정**: Manifest 파싱으로 테마 결정.
   314	
   315	```kotlin
   316	fun resolveTheme(appApk: Path, layoutPath: Path): Int {
   317	  val manifest = ManifestReader.read(appApk)
   318	
   319	  // 1. layoutPath가 특정 Activity와 매핑되면 Activity theme
   320	  val activityTheme = manifest.activities
   321	    .firstOrNull { it.layoutFile == layoutPath.name }?.themeRes
   322	  if (activityTheme != null) return activityTheme
   323	
   324	  // 2. Application theme
   325	  if (manifest.applicationTheme != 0) return manifest.applicationTheme
   326	
   327	  // 3. Fallback: Theme.DeviceDefault
   328	  return android.R.style.Theme_DeviceDefault
   329	}
   330	```
   331	
   332	사용자가 명시적으로 override 하고 싶으면 viewer UI의 테마 드롭다운에서 선택.
   333	
   334	### 3.5 Unrenderable enum 통합
   335	
   336	**결정**: `06 §4`의 `UnrenderableReason` enum에 **AXP-L3-008, 009, 010, 011** 정식 추가 (ad hoc `AxpError` string → enum 승격).
   337	
   338	```kotlin
   339	enum class UnrenderableReason(
   340	  val code: String, val category: Category, val remediationUrl: String
   341	) {
   342	  // ... (06 §4의 기존 13개) ...
   343	  L3_APK_OBFUSCATED("AXP-L3-008", Category.USER_ACTION, "#l3-obfuscated"),
   344	  L3_APK_SHORT_NAMES_HEURISTIC("AXP-L3-009", Category.USER_ACTION, "#l3-heuristic"),
   345	  L3_APK_NOT_DEBUGGABLE("AXP-L3-010", Category.USER_ACTION, "#l3-release-apk"),
   346	  L3_MISSING_LIBRARY_CLASS("AXP-L3-011", Category.USER_ACTION, "#l3-missing-lib")
   347	}
   348	```
   349	
   350	총 17개 UnrenderableReason.
   351	
   352	### 3.6 Corpus pin 선정 타임라인
   353	
   354	**결정**: Week 5 Day 23까지 `corpus/pins.yaml`의 각 프로젝트에 대해:
   355	- Pinned commit SHA
   356	- 해당 SHA에서 `./gradlew :app:assembleDebug` 성공 확인
   357	- 각 프로젝트당 선정된 ≤8 layouts 파일 경로 명시
   358	
   359	Week 6 Day 26-27 acceptance run이 시작되기 **최소 2일 전에 완성**.
   360	
   361	---
   362	
   363	## 4. README 수정 사항 (F5)
   364	
   365	Codex가 지적한 README의 잔여 모순:
   366	- "4주" 문구 (원래 03에서 왔고 06에서 6주로 개정됐는데 README 요약에 잔재)
   367	- 03과 01의 일부를 "superseded/historical"로 명시 안 함
   368	- canonical implementation map이 없음
   369	
   370	**액션**: 본 문서 작성 후 README 추가 수정 (별도 커밋).
   371	
   372	---
   373	
   374	## 5. Week 1 Day 1 Checklist (F6 반영)
   375	
   376	Codex가 제안한 체크리스트에 본 문서 결정을 결합:
   377	
   378	```
   379	[ ] 0. 라이선스 체크: layoutlib-api 배포 조건 확인 (§3.3)
   380	[ ] 1. README의 잔여 "4주" → "6주" 수정 (§4)
   381	[ ] 2. `08-integration-reconciliation.md`(본 문서) 내 모든 canonical 결정 읽기
   382	[ ] 3. Worker IPC: Unix socket + JSON line-framed (§3.1)
   383	[ ] 4. 버전 pin:
   384	     JVM=OpenJDK 17.0.11, Kotlin=1.9.x, Gradle=8.x,
   385	     Android SDK build-tools=34.0.0, layoutlib=android-34 Paparazzi bundle,
   386	     aapt2=34.0.0
   387	[ ] 5. `server/` Gradle multi-module 스캐폴드:
   388	     :protocol (MCP envelope, RenderRequest/Response, ErrorEnvelope, UnrenderableReason 17)
   389	     :render-core (RenderDispatcher + cache + RenderKey 17-input)
   390	     :layoutlib-worker (L1 per-device worker process)
   391	     :emulator-harness (L3 adb + harness APK + ChildFirstDexClassLoader)
   392	     :http-server (Ktor + SSE minimal)
   393	     :cli (axprev render subcommand)
   394	     :mcp-server (stdio main entry)
   395	[ ] 6. Tiny fixture Android app 생성:
   396	     - activity_basic.xml (LinearLayout + TextView + Button, Material theme)
   397	     - activity_custom_view.xml (com.fixture.DummyView)
   398	     - DummyView.kt (placeholder class)
   399	     - ./gradlew :app:assembleDebug로 APK 생성 확인
   400	[ ] 7. L1 spike:
   401	     - layoutlib-dist 번들 구조 스캐폴딩 (framework data 40MB 포함)
   402	     - Bridge.init + 단일 activity_basic.xml 렌더 → PNG 저장
   403	[ ] 8. 첫 contract test:
   404	     - MCP envelope 직렬화/역직렬화
   405	     - UnrenderableReason enum → docs anchor 매핑 (§3.5)
   406	[ ] 9. Week 1-6 체크포인트 게이트 캘린더 GitHub Projects에 등록
   407	```
   408	
   409	---
   410	
   411	## 6. 변경 로그
   412	
   413	| 일자 | 변경 | 작성자 |
   414	|------|------|--------|
   415	| 2026-04-22 | 초안 — Codex final integration F1-F6 전건 대응 | Codex xhigh final + 반영 |
   416	| 2026-04-22 | Post-Execution Errata 섹션 §7 추가 | W1D1 구현 세션 |
   417	
   418	---
   419	
   420	## 7. Post-Execution Errata (W1D1 구현 중 발견)
   421	
   422	실제 구현 단계에서 발견된 plan 문서 내부 카운트 모순. canonical 결정을 덮는 것이 아니라
   423	산술 수정(arithmetic correction)만 기록한다.
   424	
   425	### 7.1 UnrenderableReason 총 개수
   426	
   427	- `06 section 4` 주석 "기존 13개" — 실제 정의된 값 **15** (L1:5 + L3:7 + SYS:3)
   428	- `08 section 3.5` 말미 "총 17개" — 실제 15 + 4(L3-008~011) = **19**
   429	- **canonical 카운트**: **19**. 후속 편집에서 `06 §4` 와 `08 §3.5` 말미 숫자는 19 로 교정.
   430	
   431	### 7.2 영향
   432	- `server/protocol/src/main/kotlin/dev/axp/protocol/error/UnrenderableReason.kt` — 19 개 enum 정의 (이 문서 기준 정확)
   433	- `server/protocol/src/test/kotlin/dev/axp/protocol/UnrenderableReasonSnapshotTest.kt` — 19 를 floor 로 고정
   434	
   435	### 7.3 후속 대응
   436	추가 L3 실패 모드가 발견되면 카운트 증가. snapshot test 가 실패하면 본 §7 에 새 카운트와
   437	이유 기록 후 테스트 수정.
   438	
   439	### 7.4 RenderKey input count (Claude 페어 리뷰에서 발견)
   440	
   441	- `06 §3.1` 실제 enumeration: 15 non-nullable + 3 nullable(L3) = **18 필드** (plan 의 "14" 는 카운트 축약)
   442	- `08 §1.4` 추가: `frameworkDataBundleSha256`, `mergedResourcesSha256`, `callbackClasspathSha256` = **3**
   443	- **실제 총 21 필드 declared** (18 base + 3 추가). 이 중 mandatory 는 18 (15 base + 3 §1.4), nullable 3 (L3 전용).
   444	- plan 문서 08 §1.4 말미 "총 17 inputs" 는 산술 오류이며 본 §7.4 가 canonical 교정.
   445	
   446	**영향**: cache key digest 함수(W2 에 구현 예정)는 18 mandatory + 3 nullable 조건부 포함 방식으로
   447	설계되어야 함. "17 입력" 가정으로 작성된 후속 테스트/문서는 본 §7.4 기준으로 재정렬.
   448	
   449	### 7.5 layoutlib 번들 소스 + JAR 명명 정명화 (W1D3-R2 구현 중 발견)
   450	
   451	W1D1 의 다음 가정 두 가지가 동시에 무효화됨:
   452	
   453	1. **Paparazzi prebuilts 경로 가정 무효** — `cashapp/paparazzi` 저장소는 2.x 부터 in-tree
   454	   prebuilts (`libs/layoutlib/android-34/`) 를 제거하고 Maven artifact (`com.android.tools.layoutlib:layoutlib-runtime` + `:layoutlib-resources`) fetch 방식으로 전환됨.
   455	   handoff §4 UNVERIFIED #1 이 fact-check 됨. canonical 새 소스는 Google's Maven `dl.google.com/android/maven2/com/android/tools/layoutlib/`.
   456	
   457	2. **JAR 좌표 명명 오류** — `06`/`07` 의 "layoutlib-api JAR" 표현은 별도 좌표
   458	   `com.android.tools.layoutlib:layoutlib-api` 와 충돌. 후자는 AGP/Studio 의 SDK-internal API
   459	   모듈이며 Bridge JVM JAR 이 아님. 실제 Bridge JVM JAR 의 정확한 Maven 좌표는
   460	   `com.android.tools.layoutlib:layoutlib`. 본 §7.5 가 canonical.

exec
/bin/bash -lc "nl -ba docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md | sed -n '260,620p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   260	        val key = sha1(aarPath.toAbsolutePath().toString())
   261	        val artifactName = aarPath.fileName.toString().removeSuffix(".aar")
   262	        val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR).resolve(key)
   263	        Files.createDirectories(outDir)
   264	        val outJar = outDir.resolve("$artifactName.jar")
   265	
   266	        val aarMtime = Files.getLastModifiedTime(aarPath).toMillis()
   267	        if (outJar.isRegularFile() &&
   268	            Files.getLastModifiedTime(outJar).toMillis() >= aarMtime) {
   269	            return outJar
   270	        }
   271	
   272	        ZipFile(aarPath.toFile()).use { zip ->
   273	            val entry = zip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
   274	            zip.getInputStream(entry).use { input ->
   275	                outJar.outputStream().use { output -> input.copyTo(output) }
   276	            }
   277	        }
   278	        return outJar
   279	    }
   280	
   281	    private fun sha1(s: String): String {
   282	        val md = MessageDigest.getInstance("SHA-1")
   283	        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
   284	    }
   285	}
   286	```
   287	
   288	**페어 리뷰 Q2**: 캐시 invalidation 을 mtime 기반으로 하면 AAR 이 동일 경로로 재배포되면서 mtime 만 갱신되는 시나리오가 깨지나? — Gradle cache 의 AAR 은 hash 디렉토리에 위치하므로 동일 path 의 contents 변경은 사실상 발생하지 않음 (Maven 좌표+버전이 같으면 contents 동일). hash mismatch 시 path 자체가 달라짐 → cacheRoot 안의 sha1 키도 다름. 안전.
   289	
   290	### 4.4 C4 — SampleAppClassLoader
   291	
   292	```kotlin
   293	package dev.axp.layoutlib.worker.classloader
   294	
   295	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
   296	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
   297	import java.net.URL
   298	import java.net.URLClassLoader
   299	import java.nio.file.Files
   300	import java.nio.file.Path
   301	
   302	/**
   303	 * sample-app 의 runtime classpath (resolved AAR + transitive JAR + R.jar) 로부터
   304	 * URLClassLoader 를 구성. parent = layoutlib isolatedClassLoader (android.view.* 보유).
   305	 *
   306	 * AAR 은 AarExtractor 로 classes.jar 를 추출 후 그 경로를 URL 로 사용.
   307	 * Resource-only AAR (classes.jar 없음) 은 silently skip.
   308	 */
   309	class SampleAppClassLoader private constructor(
   310	    val classLoader: ClassLoader,
   311	    val urls: List<URL>,
   312	) {
   313	    companion object {
   314	
   315	        /**
   316	         * @param fixtureRoot fixture/sample-app 절대경로.
   317	         * @param parent 본 CL 의 parent — 보통 LayoutlibBootstrap.createIsolatedClassLoader() 결과.
   318	         */
   319	        fun build(fixtureRoot: Path, parent: ClassLoader): SampleAppClassLoader {
   320	            val manifest = SampleAppClasspathManifest.read(fixtureRoot)
   321	            val cacheRoot = fixtureRoot.resolve("app").resolve("build")
   322	            val urls = mutableListOf<URL>()
   323	
   324	            for (entry in manifest) {
   325	                val asString = entry.toString()
   326	                val jarPath = if (asString.endsWith(".aar")) {
   327	                    AarExtractor.extract(entry, cacheRoot) ?: continue // resource-only AAR skip
   328	                } else {
   329	                    entry
   330	                }
   331	                urls += jarPath.toUri().toURL()
   332	            }
   333	
   334	            // sample-app 의 R.jar — 모든 R.<package>.* 클래스 포함. 누락 시 NoClassDefFoundError
   335	            // for androidx.constraintlayout.widget.R$styleable etc.
   336	            val rJar = fixtureRoot.resolve(R_JAR_RELATIVE_PATH)
   337	            require(Files.isRegularFile(rJar)) {
   338	                "sample-app R.jar 누락: $rJar — `./gradlew :app:assembleDebug` 가 필요"
   339	            }
   340	            urls += rJar.toUri().toURL()
   341	
   342	            val cl = URLClassLoader(urls.toTypedArray(), parent)
   343	            return SampleAppClassLoader(cl, urls.toList())
   344	        }
   345	    }
   346	}
   347	```
   348	
   349	### 4.5 C5 — ClassLoaderConstants
   350	
   351	```kotlin
   352	package dev.axp.layoutlib.worker.classloader
   353	
   354	internal object ClassLoaderConstants {
   355	    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 fixture-relative 경로. */
   356	    const val MANIFEST_RELATIVE_PATH = "app/build/axp/runtime-classpath.txt"
   357	
   358	    /** AarExtractor 의 stable 캐시 디렉토리 (cacheRoot 기준 상대). */
   359	    const val AAR_CACHE_RELATIVE_DIR = "axp/aar-classes"
   360	
   361	    /** AAR ZIP 안에서 JVM 바이트코드 JAR 의 표준 entry 이름. */
   362	    const val AAR_CLASSES_JAR_ENTRY = "classes.jar"
   363	
   364	    /**
   365	     * AGP 8.x 가 emit 하는 통합 R.jar 경로 (compile_and_runtime_not_namespaced_r_class_jar variant).
   366	     * 본 경로는 AGP minor 버전 변경 시 깨질 수 있으나 8.x 안정 — 변경 시 본 상수만 갱신.
   367	     */
   368	    const val R_JAR_RELATIVE_PATH =
   369	        "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
   370	}
   371	```
   372	
   373	### 4.6 C6 — MinimalLayoutlibCallback 변경
   374	
   375	```kotlin
   376	class MinimalLayoutlibCallback(
   377	    private val viewClassLoader: ClassLoader,
   378	) : LayoutlibCallback() {
   379	
   380	    // ... 기존 nextId / byRef / byId / 동기화된 getOrGenerateResourceId / resolveResourceId 유지 ...
   381	
   382	    override fun loadView(
   383	        name: String,
   384	        constructorSignature: Array<out Class<*>>?,
   385	        constructorArgs: Array<out Any>?,
   386	    ): Any {
   387	        val cls = viewClassLoader.loadClass(name)
   388	        val sig = constructorSignature ?: emptyArray()
   389	        val args = constructorArgs ?: emptyArray()
   390	        val ctor = cls.getDeclaredConstructor(*sig)
   391	        ctor.isAccessible = true
   392	        return ctor.newInstance(*args)
   393	    }
   394	
   395	    // ... 나머지 unchanged ...
   396	}
   397	```
   398	
   399	**페어 리뷰 Q3**: `loadView` 가 throw 하는 정확한 타입은 무엇이어야 하나? — LayoutlibCallback 인터페이스는 unchecked. 우리는 ClassNotFoundException / NoSuchMethodException / InvocationTargetException 를 그대로 propagate (try-catch 없음 — 실제 layoutlib 의 inflater 가 catch). 명시적 wrap 불요 — 페어 리뷰가 별도 처리를 권하면 별도 PR.
   400	
   401	### 4.7 C7 — LayoutlibRenderer 변경
   402	
   403	`renderViaLayoutlib` 가 callback 인스턴스 생성 직전에 SampleAppClassLoader 를 빌드:
   404	
   405	```kotlin
   406	@Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null
   407	
   408	private fun resolveViewClassLoader(): ClassLoader {
   409	    sampleAppClassLoader?.let { return it.classLoader }
   410	    val isolated = classLoader ?: error("Bridge 가 init 안 됨")
   411	    val built = SampleAppClassLoader.build(fixtureRoot, isolated)
   412	    sampleAppClassLoader = built
   413	    return built.classLoader
   414	}
   415	
   416	private fun renderViaLayoutlib(layoutName: String): ByteArray? {
   417	    // ... 기존 코드 ...
   418	    val viewCL = resolveViewClassLoader()
   419	    val params: SessionParams = SessionParamsFactory.build(
   420	        layoutParser = parser,
   421	        callback = MinimalLayoutlibCallback(viewCL),  // ← 변경
   422	        resources = resources,
   423	    )
   424	    // ... 기존 코드 ...
   425	}
   426	```
   427	
   428	**페어 리뷰 Q4**: `fixtureRoot` 이 sample-app 이 아닌 (예: 다른 fixture) 이면? — 현재 fixture-discovery 가 sample-app 만 지원. activity_minimal 이 framework-only 이므로 SampleAppClassLoader.build 호출이 manifest 누락 → throw 하면 W3D1 시나리오가 깨진다. 따라서:
   429	- 옵션 A: lazy build — `loadView` 가 호출될 때만 build 시도. activity_minimal 은 framework only 라 호출 0 → side-effect 없음.
   430	- 옵션 B: build 가 manifest 없으면 empty CL 반환.
   431	
   432	옵션 A 채택 (스펙 §4.6 의 viewClassLoader 가 lazy proxy 가 됨). 페어 리뷰가 단순화 권하면 옵션 B 검토.
   433	
   434	**구현 변경**: `MinimalLayoutlibCallback` 에 `viewClassLoader: () -> ClassLoader` (lambda) 또는 `Lazy<ClassLoader>` 주입. activity_minimal 은 절대 호출 안 됨 → manifest 누락이 무해.
   435	
   436	### 4.8 C8 — LayoutlibRendererIntegrationTest 변경
   437	
   438	```kotlin
   439	@Tag("integration")
   440	class LayoutlibRendererIntegrationTest {
   441	
   442	    @Test
   443	    fun `tier3-values — activity_basic renders SUCCESS with non-empty PNG`() {
   444	        val dist = locateDistDir()
   445	        val fixture = locateFixtureRoot()
   446	        val renderer = SharedLayoutlibRenderer.getOrCreate(
   447	            distDir = dist,
   448	            fixtureRoot = fixture,
   449	            fallback = null,
   450	        )
   451	        val bytes = renderer.renderPng("activity_basic.xml")
   452	
   453	        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG bytes 가 placeholder 보다 큼: ${bytes.size}")
   454	        assertTrue(
   455	            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
   456	                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
   457	            "PNG magic 헤더가 아님",
   458	        )
   459	        // 페어 리뷰가 SUCCESS assertion 추가 권하면 본 줄 활성:
   460	        // assertEquals(Result.Status.SUCCESS, renderer.lastSessionResult?.status)
   461	    }
   462	
   463	    // ... locateDistDir / locateFixtureRoot 유지 ...
   464	
   465	    private companion object {
   466	        const val MIN_RENDERED_PNG_BYTES = 1000
   467	    }
   468	}
   469	```
   470	
   471	---
   472	
   473	## 5. 테스트 전략 (TDD)
   474	
   475	각 컴포넌트별 단위 테스트 → integration 테스트 마지막.
   476	
   477	### 5.1 단위
   478	
   479	| Test | 케이스 | 검증 |
   480	|------|-------|------|
   481	| TC1-1 | manifest 파일 누락 | IllegalArgumentException with hint |
   482	| TC1-2 | manifest 빈 파일 | IllegalStateException |
   483	| TC1-3 | 라인이 공백 | IllegalArgumentException with line index |
   484	| TC1-4 | 비-절대경로 | IllegalArgumentException |
   485	| TC1-5 | 정상 (.aar + .jar 혼합) | List<Path> 반환, 순서 유지 |
   486	| TC2-1 | 정상 추출 | classes.jar 가 cacheRoot 에 생성, content 일치 |
   487	| TC2-2 | 캐시 hit | 두 번째 호출이 동일 Path, mtime 변경 없음 |
   488	| TC2-3 | classes.jar 없는 AAR | null 반환 |
   489	| TC2-4 | 손상 ZIP | IOException |
   490	| TC3-1 | URLClassLoader 합성 + parent chain | parent === isolatedCL |
   491	| TC3-2 | manifest 의 .jar 가 그대로 URL 화 | URL list 에 포함 |
   492	| TC3-3 | R.jar 누락 시 require fail | 명확한 메시지 |
   493	| TC4-1 | viewClassLoader 가 known class 를 load | Class 인스턴스 반환 |
   494	| TC4-2 | unknown class 요청 | ClassNotFoundException pass-through |
   495	| TC4-3 | constructor signature mismatch | NoSuchMethodException pass-through |
   496	
   497	### 5.2 Integration
   498	
   499	| Test | 입력 | 검증 |
   500	|------|-----|------|
   501	| `tier3-basic` (renamed from `tier3 - renderPng...`) | activity_basic.xml | T1 gate (PNG > 1000 + magic). 페어 리뷰가 권하면 SUCCESS status 추가. |
   502	
   503	기존 `LayoutlibRendererTier3MinimalTest` (activity_minimal) 영향 없음 — 본 변경 후에도 PASS 보장. lazy CL 덕분에 manifest 누락 영향 없음.
   504	
   505	### 5.3 회귀 가능성
   506	
   507	- Bridge.init / RenderSession 경로 — 변경 없음.
   508	- FrameworkResourceValueLoader / FrameworkRenderResources — 변경 없음.
   509	- SharedLayoutlibRenderer — 변경 없음.
   510	- 99 → 118 unit tests + 11 integration → 본 변경 후: 118 + ~15 unit (TC1-4) + 11 → 12 integration (LayoutlibRendererIntegrationTest enable) PASS. tier3-glyph 만 SKIPPED 유지.
   511	
   512	---
   513	
   514	## 6. 위험 / 알려진 한계
   515	
   516	### 6.1 위험
   517	
   518	| ID | 위험 | 완화 |
   519	|----|------|------|
   520	| R1 | sample-app 미빌드 시 manifest/R.jar 누락 → 모든 integration test 가 FAIL | C2/C4 의 require 메시지가 정확한 명령(`./gradlew :app:assembleDebug`) 안내. 페어 리뷰가 더 강한 요구 시 worker 가 자동으로 invoke 하는 방안 검토. |
   521	| R2 | layoutlib 의 LayoutlibCallback 이 loadView 외 다른 callback (e.g. `getProject` / `findClass`) 도 사용? | 페어 리뷰에서 layoutlib 14.x 의 LayoutlibCallback 추상메서드 audit. 본 스펙은 loadView 만 변경. |
   522	| R3 | ConstraintLayout 의 `app:layout_constraintXxx` custom attr 가 default 로 fallback → render 결과가 broken positioning. T1 gate 통과는 하지만 visual fidelity 미달. | W3D4 carry: app/library resource value loader. 본 스펙 out-of-scope §1.2 명시. |
   523	| R4 | AGP 8.x 의 `compile_and_runtime_not_namespaced_r_class_jar` 경로가 AGP 9.x 에서 변경 가능 | ClassLoaderConstants 에 상수 1개 — 발견 시 단일 변경. |
   524	| R5 | sample-app 의 `debugRuntimeClasspath` 에 layoutlib JAR 이 포함되면 isolatedCL 과 충돌? | sample-app 은 layoutlib 에 의존하지 않음. 가능성 없음. |
   525	| R6 | `forkEvery(1L)` 에서 두 integration test 가 같은 sample-app cache dir 을 동시에 쓰지 않나 | Gradle test fork 는 process 격리, 같은 디스크지만 mtime 기반 idempotent → race condition 시 둘이 동일 컨텐츠로 덮어씀. 안전. |
   526	
   527	### 6.2 알려진 한계
   528	
   529	- **Visual fidelity**: §1.2 R3 — out-of-scope, W3D4+ carry.
   530	- **Multi-fixture**: 본 스펙은 sample-app 만 가정. 다중 fixture 지원은 W3D4+.
   531	- **Hot reload**: 빌드 manifest 가 매 build 마다 갱신 → 워커 재시작 필요. 현재 worker 는 매 test class 별 fork 라 실용 영향 없음.
   532	
   533	---
   534	
   535	## 7. 페어 리뷰용 질문 (Q1-Q5)
   536	
   537	페어 리뷰 (Codex+Claude) 에서 명시적으로 검증 요청:
   538	
   539	- **Q1** (Gradle): `applicationVariants.all` API 가 `axpEmitClasspath` 를 더 안정적으로 wire 할 수 있나? AGP 8.x 의 best practice 는?
   540	- **Q2** (Cache): `AarExtractor` 의 mtime 기반 invalidation 이 충분한가? hash 기반 (sha256) 으로 변경해야 하는 시나리오가 있나?
   541	- **Q3** (loadView): InvocationTargetException 의 cause 를 unwrap 해서 re-throw 해야 하나? layoutlib 14.x 의 inflater 가 unwrap 을 기대하나?
   542	- **Q4** (lazy build): SampleAppClassLoader 빌드를 lazy 로 미루면 (옵션 A) activity_minimal 호환성 유지. 더 단순한 옵션 (B: empty CL) 이 권장되는가?
   543	- **Q5** (assertion): T1 gate 에 `Result.Status == SUCCESS` 를 추가해야 하나? 현재 W2D7 패턴은 PNG > 1000 만 검사 — strictness 의 trade-off?
   544	
   545	페어 리뷰 verdict 형식: GO / GO_WITH_FOLLOWUPS / NO_GO + 각 Q 별 stance.
   546	
   547	---
   548	
   549	## 8. 변경 로그
   550	
   551	- 2026-04-29: 초안 작성. W3D2 carry 의 sample-app unblock 옵션 A 의 in-JVM 변형 (canonical AVD-L3 와 구분).

exec
/bin/bash -lc "nl -ba docs/plan/08-integration-reconciliation.md | sed -n '449,620p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   449	### 7.5 layoutlib 번들 소스 + JAR 명명 정명화 (W1D3-R2 구현 중 발견)
   450	
   451	W1D1 의 다음 가정 두 가지가 동시에 무효화됨:
   452	
   453	1. **Paparazzi prebuilts 경로 가정 무효** — `cashapp/paparazzi` 저장소는 2.x 부터 in-tree
   454	   prebuilts (`libs/layoutlib/android-34/`) 를 제거하고 Maven artifact (`com.android.tools.layoutlib:layoutlib-runtime` + `:layoutlib-resources`) fetch 방식으로 전환됨.
   455	   handoff §4 UNVERIFIED #1 이 fact-check 됨. canonical 새 소스는 Google's Maven `dl.google.com/android/maven2/com/android/tools/layoutlib/`.
   456	
   457	2. **JAR 좌표 명명 오류** — `06`/`07` 의 "layoutlib-api JAR" 표현은 별도 좌표
   458	   `com.android.tools.layoutlib:layoutlib-api` 와 충돌. 후자는 AGP/Studio 의 SDK-internal API
   459	   모듈이며 Bridge JVM JAR 이 아님. 실제 Bridge JVM JAR 의 정확한 Maven 좌표는
   460	   `com.android.tools.layoutlib:layoutlib`. 본 §7.5 가 canonical.
   461	
   462	**canonical 번들 구성** (Android 34 / API 34, U): 4 artifact (총 ~155MB) — sha256 은 `THIRD_PARTY_NOTICES.md §1.1.{a,b,c,d}`:
   463	- `layoutlib:14.0.11` (JVM Bridge classes, 49M) → distDir 루트
   464	- `layoutlib-api:31.13.2` (Bridge parent + ILayoutLog, 120K) → distDir 루트 — **W1D4-R3 추가 (필수)**
   465	- `layoutlib-runtime:14.0.11:linux` (native + 폰트/icu/keyboards, 74M) → distDir 언팩 + build.prop 을 data/ 로 이동
   466	- `layoutlib-resources:14.0.11` (framework res + overlays, 32M) → distDir/data/ 언팩
   467	
   468	**구현 영향**:
   469	- `LayoutlibBootstrap.findLayoutlibJar()` 글롭: `layoutlib-api-*.jar` → `layoutlib-*.jar` (단, sibling 좌표 `-api-`/`-runtime-`/`-resources-` prefix 는 제외).
   470	- `LayoutlibBootstrap.findLayoutlibApiJar()` 신규 — sibling 글롭 `layoutlib-api-*.jar` 전용 (W1D4-R3).
   471	- `LayoutlibBootstrap.createIsolatedClassLoader()` — 두 JAR 모두 URLClassLoader 에 추가 (Bridge parent 누락 시 NoClassDefFoundError).
   472	- `LayoutlibBootstrap` 헬퍼들 추가 (W1D4-R3): `dataDir/fontsDir/nativeLibDir/parseBuildProperties/findIcuDataFile/listKeyboardPaths` — Bridge.init 7-param 인자 구성용.
   473	- `LayoutlibBootstrapTest` 의 fake JAR 이름도 동일하게 정명화 (5 테스트 모두 통과 유지).
   474	- `BridgeInitIntegrationTest` (W1D4-R3) — `@Tag("integration")`. Tier1 3 PASS (validate Ok / Bridge class load / init 7-param 시그니처). Tier2 best-effort init 호출 — transitive Guava 누락 시 SKIP (W2 fatJar 빌드로 해결).
   475	- `axp.kotlin-common.gradle.kts` — `-PincludeTags=integration` 으로 통합 테스트만 실행, 기본은 제외.
   476	- W1D1 scaffold README (`server/libs/layoutlib-dist/android-34/README.md`) 다운로드 레시피 갱신 (4 artifact).
   477	- 06 §4 / 07 §2.2 / 06 §3.3 의 "layoutlib-api" 언급은 향후 편집 사이클에서 일괄 정리 (08 §7.5 가 우선 적용).
   478	
   479	**Cache-key 영향**: `RenderKey.frameworkDataBundleSha256` (08 §1.4) 의 input 은 본 4 artifact 의 결합 sha256.
   480	W2 cache key digest 구현 시 §1.1.{a,b,c,d} 의 sha256 을 canonical 시드로.
   481	
   482	**Kotlin 언어 함정 메모 (W1D4 발견)**:
   483	- Kotlin 의 block comment 는 **중첩 허용** (Java 와 다름). KDoc 본문에 `/*` 로 시작하는 문자열(예: `/*.kcm` 같은 glob)이 있으면 nested comment 로 해석되어 EOF 까지 unclosed 발생. 글롭 문자열은 backtick 안에서도 회피 필요. fix: 자연어 풀어 쓰기.
   484	- Kotlin 함수 본문 `{` 는 시그니처와 같은 줄에 있어야 컴파일됨 (Allman 스타일은 함수 declaration 에서 syntax error). CLAUDE.md 의 "Opening brace on its own new line" 은 C++ 컨벤션이며 Kotlin 함수 본문에는 적용 불가.
   485	
   486	### 7.6 W1D5-R4 placeholder PNG escape hatch (W1 exit criterion)
   487	
   488	**원안 (handoff §2.5)**: `:mcp-server` 부팅 → activity_basic.xml L1 layoutlib 렌더 → 브라우저에 실제 PNG 표시.
   489	
   490	**실측 제약**: layoutlib `Bridge.init(...)` 내부가 Guava `com.google.common.collect.ImmutableMap` 등 transitive 의존을 요구 (W1D4-R3 BridgeInitIntegrationTest tier2 SKIP 으로 확인). v1 minimal 구성에서 :layoutlib-worker 는 reflection-only 접근이므로 compileOnly 도 두지 않음 → 격리 ClassLoader 가 transitive 의존 미해결.
   491	
   492	**채택 결정 (시나리오 B)**: HTTP + SSE + viewer infrastructure 만 W1D5 에 완성. `/preview` 응답은 `PlaceholderPngRenderer` 가 생성하는 720x1280 PNG (레이아웃 명 + 상태 메시지 표시). 실제 layoutlib 렌더는 W2 D6 의 fatJar 작업(transitive Guava/kxml2/ICU4J 포함) 후 자연 활성화 — `PreviewRoutes` 의 `pngRenderer` 만 교체하면 됨.
   493	
   494	**근거**:
   495	- 06 §6 / 08 §1.5 canonical: "delay 는 선택지가 아님". 따라서 infra path 만이라도 검증해 W1 exit 시그널을 명확히.
   496	- Week 1 exit "브라우저 PNG 표시" 의 본질은 **HTTP/SSE/viewer 데이터 경로 증명** — 실제 픽셀 출처는 W2 부터 layoutlib 으로 전환되어도 viewer 코드는 무수정.
   497	- 06 §6 minimal SSE 정신과 일치: minimum viable hot-reload path 가 W1 산출.
   498	
   499	**구현 위치**:
   500	- `:http-server` 모듈 신규: `HttpServerConstants` / `SseConstants` / `PlaceholderPngConstants` / `PlaceholderPngRenderer` / `RenderEvent` / `SseBroadcaster` / `PreviewRoutes` / `PreviewServer` (8 파일).
   501	- `viewer/index.html` + `viewer/app.js` (classpath:`viewer/`).
   502	- `:mcp-server/Main.kt` 기본 모드: `PreviewServer().start(); blockUntilShutdown()`. `--smoke` 는 W1D1 동작 유지.
   503	- 5 신규 테스트 (3 placeholder PNG + 2 SseBroadcaster) 모두 PASS. 통합 verify 는 java -jar + curl 로 진행 — index 200 / preview 200 (PNG signature + 720x1280 검증) / app.js 200 / SSE 200 (`event: render_complete` + JSON payload).
   504	
   505	**W2 진입 시 변경 범위 (예상)**:
   506	- `:layoutlib-worker` 에 layoutlib 의 transitive 의존(Guava 등) 추가 → fatJar 에 포함되도록.
   507	- `LayoutlibBootstrap.createIsolatedClassLoader()` 의 parent classloader 정책 재검토 (현재 platform — Guava 가 보이지 않음. application classloader 로 변경 또는 별도 lib 추가).
   508	- `PreviewServer` 의 `pngRenderer` 를 placeholder → `LayoutlibRenderer` 로 교체. `PreviewRoutes` 인터페이스는 유지.
   509	- `BridgeInitIntegrationTest tier2` 는 자동 SKIP → PASS 로 전환 (transitive 해결 시).
   510	
   511	**Cache-key 영향**: 본 escape-hatch 는 RenderKey 에 영향 없음. placeholder 결과는 캐시 대상 아님 (`renderId="init-0"|"manual-..."` 로 trivial). W2 부터 RenderKey-based 캐싱 활성화.
   512	
   513	### 7.7 W1-END 페어 리뷰 divergence 해소 — stdio + real render W2 최우선 승격
   514	
   515	본 §7.7 은 `docs/W1-END-PAIR-REVIEW.md` 의 Codex xhigh ↔ Claude sonnet 페어 divergence 를 canonical 로 해소한다.
   516	
   517	**Codex FAIL 의 구조적 근거** (MILESTONES.md 를 literal 읽음):
   518	- Week 1 Go/No-Go item 1: "`java -jar axp-server.jar` 10s 내 **stdio + HTTP** 응답" → stdio 미구현
   519	- Week 1 Go/No-Go item 2: "**layoutlib 으로 최소 XML 1개 렌더**" → placeholder PNG 는 layoutlib 출처 아님
   520	- "Escape Hatch 없음 (Foundation 주차는 스킵 불가)"
   521	
   522	**Claude GO_WITH_FOLLOWUPS 의 구조적 근거**:
   523	- 08 §7.6 의 placeholder PNG escape 는 user 승인.
   524	- Source invariants 9/9 PASS. tech debt concerns 2건 (@Volatile, capability 배너) 는 즉시 해소.
   525	
   526	**canonical 해소** (본 §7.7):
   527	1. 08 §7.6 의 escape hatch 범위를 **stdio + real layoutlib render 둘 다** 로 확장. 두 gap 이 원래 canonical Week 1 item 이었음을 인정하고, W2 D6 의 blocking acceptance criterion 으로 승격.
   528	2. MILESTONES.md Week 1 Go/No-Go 체크박스는 "item 3 브라우저 PNG 표시" 만 W1 완료로 표기. item 1-2 는 W2 D6 acceptance 로 재배치.
   529	3. 이로써 Codex 의 FAIL 은 "W1 수용 불가" 가 아닌 "W2 초입 blocker" 로 정합 — canonical 궤적 유지 + release gate (08 §1.5) 영향 없음.
   530	
   531	**W2 D6 blocking acceptance** (W1-END 승계 task — §7.7.1 의 3a/3b 분할 이후 resolution 상태):
   532	- [x] `:mcp-server/Main.kt` 에 MCP stdio JSON-RPC 루프 구현. `initialize` + `tools/list` + `tools/call{render_layout}` + `shutdown` + `notifications/initialized` 처리. `--smoke` 호환 유지. (W2D6 완료)
   533	- [x] `:layoutlib-worker/build.gradle.kts` 에 layoutlib 14.0.11 runtime transitive (Guava / kxml2 / ICU4J) `runtimeOnly` 선언. `BridgeInitIntegrationTest Tier2` PASS 전환. (W2D6 완료 — §7.7.1 F-6: layoutlib pom-based resolve 는 post-W2D6 refactor 후보)
   534	- [x] **3a** `PreviewRoutes.pngRenderer` 의 interface 분리 → `PngRenderer` 를 `:protocol` 에 위치, `LayoutlibRenderer` 구현체 연결, Bridge.init 실제 성공 경로 증명 (Tier2 + Tier3 PASS). (W2D6 완료)
   535	- [x] **3b-arch** (W2D7 완료 — §7.7.1 확장): `Bridge.createSession(SessionParams)` 경로가 실 layoutlib 의 inflate 단계까지 도달. SessionParams / HardwareConfig / ILayoutPullParser / LayoutlibCallback / AssetRepository / RenderResources baseline 인프라 전 세트 구축. Tier3 `tier3-arch` 가 `ERROR_INFLATION (config_scrollbarSize)` 를 architecture-positive evidence 로 assert.
   536	- [ ] **3b-values** (W3 carry — §7.7.1 확장): 프레임워크 리소스 VALUE loader (data/res/values 의 모든 dimen/integer/bool/color/style XML 파싱 → `RenderResources` 제공). Paparazzi 급 infra ~1000 LOC. 구현 완료 시 Tier3 `tier3-values` 가 unblock되어 실제 PNG pixel assertion 통과 예상.
   537	- [x] 3 item (1+2+3a) 완료 시 MILESTONES.md Week 1 체크박스 soft-close + 본 §7.7 "resolution" 상태 전환. (W2D6 완료, 3b 는 별개 carry.)
   538	
   539	### 7.7.1 W2D6 플랜 페어 리뷰 divergence 해소 — item 3 의 3a / 3b 분할 (2026-04-23)
   540	
   541	본 §7.7.1 은 `docs/W2D6-PLAN-PAIR-REVIEW.md` 의 verdict divergence (Codex NO_GO ↔ Claude GO_WITH_FOLLOWUPS) 를 canonical 로 해소한다. 상세 내용은 해당 문서 §3 참조.
   542	
   543	**Codex NO_GO 의 핵심 근거 (C-2)**:
   544	- §7.7 item 3 "activity_basic.xml 한 개 이상 실제 layoutlib 렌더" 를 문자 그대로 해석 → `LayoutlibRenderer` 가 `Bridge.init` 성공 후 `BufferedImage` 만 그리는 것은 canonical 미충족.
   545	
   546	**Claude GO_WITH_FOLLOWUPS 의 핵심 근거**:
   547	- Bridge.init 실제 성공 (Tier2 PASS) + `PngRenderer` interface 분리 + graceful fallback 이 W1-END 의 user-approved 점진 궤적에 정합.
   548	
   549	**canonical 해소**:
   550	1. §7.7 item 3 를 두 단계로 세분화:
   551	   - **3a (W2D6 closure)**: `LayoutlibRenderer` 가 `Bridge.init` 실제 성공 경로를 증명. `PngRenderer` 를 `:protocol` 로 분리, `PreviewRoutes` 가 interface 로 교체, dist 부재 시 graceful fallback. 이 단계는 PNG pixel 이 아직 layoutlib `RenderSession` 이 아닌 placeholder-after-init BufferedImage 에서 유래함을 canonical 로 인정.
   552	   - **3b (W2D7 carry)**: `Bridge.createSession(SessionParams)` → `RenderSession.render(timeout)` → `session.image` 를 PNG 로 인코딩. `ResourceResolver` + `LayoutlibCallback` + `HardwareConfig` 최소 infra.
   553	2. user 가 W1-END 에서 scenario B (placeholder 우선, L1 fatJar 해결 후 교체) 를 승인한 점을 상속. `RenderSession` 은 `SessionParams`+`ResourceResolver`+`LayoutlibCallback` 의 비자명한 infra 를 동반해 W2D6 단일 blocker 내 수용 시 risk 증가.
   554	
   555	**추가 pre-execution fix (플랜 실행 전 반영)**:
   556	- **F-1** (Codex+Claude convergent): MCP `tools/call` 응답을 `result.content = [{type:"image", data, mimeType}]` 로. W2D6-STDIO 구현 시 반영.
   557	- **F-4** (convergent CONCERN): `createIsolatedClassLoader()` parent=`getSystemClassLoader()` 는 W2D6 blocker 해소 목적 한정. W3+ 재검토 task 필수.
   558	- **F-5** (Codex 고유 valid): `JsonRpcRequest.id` 는 primitive 만 허용, 그 외 `-32600`.
   559	- **F-6** (Codex 고유 valid): Guava/kxml2/ICU4J 버전은 W2D6 범위 내 pin 허용. post-W2D6 pom-resolved refactor task 기록.
   560	- **F-7** (Claude 고유 valid): STDIO snippet 의 `import dev.axp.protocol.Versions` 확보.
   561	- **F-8** (Codex citation): MCP protocol version `2025-06-18` 유지 — revision 실존 확인.
   562	
   563	**영향 범위**:
   564	- MILESTONES.md Week 1 Go/No-Go item 2 의 문구 "3a partial / 3b carry" 로 갱신.
   565	- 신규 task: `W2D7-RENDERSESSION` (3b), `POST-W2D6-POM-RESOLVE` (F-6), `W3-CLASSLOADER-AUDIT` (F-4).
   566	
   567	**Axis diverge 회고**:
   568	- 본 플랜 페어 divergence 에 judge round 를 별도 실행하지 않은 사유: user 가 W1-END 에서 scenario B 를 승인했으며 handoff §2.3 step 3 에서 "Tier2 PASS = Bridge.init 성공 → 여기서 LayoutlibRenderer 구현" 점진 순서가 canonical 에 이미 명시. 3a/3b 분할은 이 점진 순서를 문자 그대로 반영.
   569	- Codex+Claude convergent issue 4건 은 pre-execution fix 로 전량 반영. 본 resolution 은 divergent 1건 (C-2) 한정.
   570	
   571	**Axis diverge 회고** (W1-END §7.7 의 원문 회고 위치 존중):
   572	- 본 페어가 divergent 한 데도 judge round 를 별도 실행하지 않은 사유: user 가 scenario B (placeholder PNG 먼저, L1 fatJar 해결 후 교체) 를 명시적으로 선택하여 구조적 divergence 의 원인을 이미 해소함. judge round 를 실행해도 결과는 "user 결정 유효" 로 수렴.
   573	- 향후 게이트에서 user 개입 없는 divergence 가 발생하면 CLAUDE.md 규정대로 judge round 실행 의무.
   574	
   575	**영향 범위** (원문):
   576	- MILESTONES.md 업데이트 필요 (Week 1 체크박스 + Week 2 entry note).
   577	- `handoff.md` 를 W1 → W2 간 인계용으로 별도 작성 (`docs/work_log/2026-04-23_w1d2-d5-http-viewer/handoff.md`).
   578	- W2-KICKOFF-R5 task 는 본 §7.7 의 3 blocking acceptance 보다 **후순위** (노트 → 구현 순서 유지).
   579	
   580	### 7.7.2 W2D7 실행 페어 리뷰 — 3b 를 3b-arch / 3b-values 로 재분할 (2026-04-23)
   581	
   582	본 §7.7.2 는 W2D7 실행 중 발견된 canonical 한계 (프레임워크 리소스 VALUE loader 가 별개의 Paparazzi 급 인프라) 를 문서화. §7.7.1 의 3b 를 추가 분할한다.
   583	
   584	**실행 중 발견**:
   585	- `:layoutlib-worker` 의 소스에 layoutlib-api 31.13.2 를 `implementation` 으로 추가 → `LayoutlibCallback` / `AssetRepository` / `ILayoutPullParser` subclass 가능.
   586	- `MinimalLayoutlibCallback`, `LayoutPullParserAdapter`, `SessionParamsFactory`, `MinimalFrameworkRenderResources`, `NoopAssetRepository` 를 구현.
   587	- `LayoutlibRenderer.renderViaLayoutlib` 를 실 `Bridge.createSession(SessionParams)` → `RenderSession.render(timeout)` 경로로 교체. BufferedImage stub 제거.
   588	- Tier3 test 가 `createSession` → inflate 단계까지 실제 도달 확인. 실패 지점은 `android.content.res.Resources_Delegate.getDimensionPixelSize` 가 프레임워크 dimen `config_scrollbarSize` (0x10500DD) 의 VALUE 를 찾지 못하는 것.
   589	
   590	**canonical 한계**:
   591	- `Bridge.init` 은 **프레임워크 resource ID ↔ name 매핑 만** 로드 (`sRMap` / `sRevRMap` via R.class reflection). **프레임워크 resource VALUE 는 외부 RenderResources 를 통해 제공되어야 함**. layoutlib-api 에는 `ResourceResolver` 공용 클래스가 없음.
   592	- 풀 framework resource loader 는 Paparazzi 의 `ResourceResolver.create(...)` 경로와 동일한 복잡도 (`data/res/values` 의 모든 XML 파싱 + FolderConfiguration 기반 매칭 + 스타일 체인 해석) 로, W2D7 단일 세션 내 수용 시 risk 증가.
   593	
   594	**canonical 해소**:
   595	1. §7.7.1 item 3b 를 두 단계로 재분할 (3a/3b 전례와 동일 패턴):
   596	   - **3b-arch (W2D7 closure)**: SessionParams / HardwareConfig / ILayoutPullParser / LayoutlibCallback / AssetRepository / RenderResources baseline 인프라 구현 + createSession 이 실 inflate 단계 도달 증명. Tier3 `tier3-arch` 가 `ERROR_INFLATION (config_scrollbarSize)` 를 positive evidence 로 assert. PNG pixel 은 fallback (placeholder) 으로 대체됨을 canonical 로 인정.
   597	   - **3b-values (W3 carry)**: 프레임워크 resource VALUE loader 구현. `data/res/values` 의 XML 을 파싱하여 `MinimalFrameworkRenderResources` 의 `getUnresolvedResource` / `getResolvedResource` / `getStyle` 이 실제 값을 반환하도록 확장. 구현 완료 시 `tier3-values` `@Disabled` 해제.
   598	
   599	2. W2D7 에 **신규 편의**로 도입된 사항 (영향 범위):
   600	   - `:layoutlib-worker` 의 `build.gradle.kts` 에 `layoutlib-api:31.13.2` `implementation` 추가.
   601	   - F1 (페어 리뷰): `LayoutlibBootstrap.createIsolatedClassLoader()` 가 `layoutlib-api.jar` URL 을 더 이상 추가하지 않음. system CL 이 단일 출처.
   602	   - integration test 의 `forkEvery = 1` 설정 (JVM-wide Bridge static state 오염 방지).
   603	   - Tier3 test 를 class 분리: 기존 `LayoutlibRendererIntegrationTest` 은 유지 (activity_basic — custom view, 기대 SKIP), 신규 `LayoutlibRendererTier3MinimalTest` 가 canonical target.
   604	
   605	3. **W3 carry list** (신규):
   606	   - `W3-RESOURCE-VALUE-LOADER` (§7.7.2 3b-values)
   607	   - 기존: `POST-W2D6-POM-RESOLVE` (F-6), `W3-CLASSLOADER-AUDIT` (F-4)
   608	
   609	**페어 리뷰 (2026-04-23)**:
   610	- `docs/W2D7-PLAN-PAIR-REVIEW.md` — FULL CONVERGENCE GO_WITH_FOLLOWUPS. F1/F2/F3/F4 전량 반영.
   611	- `docs/W2D7-PAIR-REVIEW.md` — 구현 완료 후 페어 리뷰 결과.
   612	
   613	**Axis diverge 회고**:
   614	- 실행 중 canonical 한계 (framework resource VALUE loader) 를 발견한 것은 W2D7-PLAN-PAIR-REVIEW 에서 Codex/Claude 모두 지적한 Q3 에 부합 — 플랜 페어가 이 risk 를 조기에 surface 했고, 플랜은 Task 6 fallback tree 로 미리 branch 해두었음. 실제 실행에서는 fallback 시도보다 canonical split 이 더 honest 한 engineering 판단이라 3b-values 를 W3 carry 로 격상.
   615	
   616	
   617	---
   618	
   619	### 7.7.3 W3D1 실행 결과 — 3b-values CLOSED, tier3-glyph 신규 carry (2026-04-24)
   620	

exec
/bin/bash -lc "nl -ba docs/plan/08-integration-reconciliation.md | sed -n '619,720p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   619	### 7.7.3 W3D1 실행 결과 — 3b-values CLOSED, tier3-glyph 신규 carry (2026-04-24)
   620	
   621	**완료**:
   622	- `dev.axp.layoutlib.worker.resources` 서브패키지 신설 (7 main file + 5 test file = 12 파일, ~950 LoC).
   623	- `FrameworkResourceValueLoader.loadOrGet(dataDir)` 가 `data/res/values/` 의 10 XML 을 kxml2 로 파싱 → 집계.
   624	  - **F1 규칙** (pair-review): 실 `attrs.xml` 은 top-level `<attr>` 이 0 개이고 모두 `<declare-styleable>` 내부에 nested — parser 가 양쪽 수집, Loader 는 first-wins dedupe. 실 dist 에서 200+ AttrDef 확인.
   625	- `FrameworkRenderResources` 가 bundle delegate 로 layoutlib 에 값 제공. `findResValue` override 금지 (W2D7 L3 landmine, reflection guard 테스트 존재).
   626	- Tier3 `tier3-arch` 의 canonical 플립: expected={SUCCESS}, rejected={ERROR_NOT_INFLATED, ERROR_INFLATION, ERROR_RENDER, ERROR_UNKNOWN} (F4).
   627	- `tier3-values` @Disabled 제거, T1 gate (SUCCESS + PNG > 1000 bytes) 통과 — activity_minimal.xml 이 layoutlib pipeline 을 SUCCESS 로 완주.
   628	- Native lib single-load 이슈 발견 → `LayoutlibRendererTier3MinimalTest` companion object sharedRenderer 단일 인스턴스로 해결.
   629	- 테스트: **99 unit + 8 integration PASS + 2 SKIPPED (activity_basic + tier3-glyph)**.
   630	  - 8 PASS 내역: 4 BridgeInit + 1 tier3-arch + 1 tier3-values + 2 FrameworkResourceLoaderRealDistTest (F1 guard + chain resolve).
   631	  - 플랜 F3 는 6 PASS 예상했으나 F1 real-dist guard 2건을 세션 중에 추가로 붙여 8 PASS 로 확장.
   632	
   633	**canonical split**:
   634	- §7.7.1 item 3b (W2 carry) → **CLOSED** (arch + values 모두 SUCCESS).
   635	- §7.7.2 3b-values (W3 carry) → **CLOSED**.
   636	- 신규 carry: `tier3-glyph` — Font wiring + StaticLayout + Canvas.drawText JNI 증명. T2 gate (PNG ≥ 10KB + dark pixel ≥ 20). W4+ 타겟.
   637	
   638	**결정**:
   639	- 스코프 Option B (Material-theme chain, 10 XML) 채택. spec: `docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md`.
   640	- Style parent 는 pre-populate (StyleParentInference 순함수) + Loader/Bundle post-process (bundle-contains check).
   641	- AttrDef dedupe 정책: **first-wins** (F1); SimpleValue/StyleDef 는 **later-wins** (themes_material 오버라이드 지원).
   642	- `VALUES_DIR = "res/values"` — 실 dist 구조는 `data/res/values/` (플랜 초기 `"values"` → W3D1 수정).
   643	
   644	**페어 리뷰**:
   645	- **플랜 단계** (`docs/W3D1-PLAN-PAIR-REVIEW.md`): Codex 호출이 "quota 소진" 으로 오인되었으나 실제 원인은 non-git 프로젝트의 trust-check hang (landmine L6). `--skip-git-repo-check` 플래그 누락. Claude 단독 리뷰로 F1-F6 반영.
   646	- **구현 단계** (`docs/W3D1-PAIR-REVIEW.md`): Claude (Explore subagent) + Codex (GPT-5.5 xhigh) 양측 모두 **GO_WITH_FOLLOWUPS** + **No Blockers** — FULL 컨버전스. 병합된 follow-up:
   647	  - **MF1**: `tier3-arch` 의 rejected-set 을 `assertEquals(SUCCESS, status)` 로 단순화 (Codex Q5 의 8+ 추가 Result.Status enum 열거 불요).
   648	  - **MF2**: `SessionParamsFactory.build` 의 `callback` default 제거 (CLAUDE.md "No default parameter values").
   649	  - **MF3**: `LayoutlibRenderer` 의 `"data"` 문자열을 `ResourceLoaderConstants.DATA_DIR` 상수화.
   650	  - Deferred: Codex F3 (CLI/MCP dist path 하드코딩 제거 — W3 Day 2+), Codex F4 (`LayoutlibRendererIntegrationTest` shared-renderer 전환 — test-infra 일반화 carry).
   651	
   652	**새 carry list** (W3 Day 2+):
   653	- `tier3-glyph` (본 §7.7.3, W4+ target).
   654	- `CLI-DIST-PATH` (pair-review Codex F3): MCP `Main.kt` 의 `layoutlib-dist/android-34` 하드코딩 제거 + CLI 인자 지원.
   655	- `TEST-INFRA-SHARED-RENDERER` (pair-review Codex F4): integration test 에 tier3 의 `sharedRenderer` 패턴 일반화.
   656	- 기존 carry 유지: `POST-W2D6-POM-RESOLVE` (F-6), `W3-CLASSLOADER-AUDIT` (F-4).

The spec explicitly diverges from canonical AVD-L3 and defines a narrower in-JVM custom-view path; I’m now checking the actual worker code that this would attach to.
  ✓ Read spec and canonical context with line numbers
  → Inspect current layoutlib worker source and Gradle fixture
  • Verify build artifacts, AAR contents, dependencies, and layoutlib-api surface
  • Assess blockers, follow-ups, explicit Q1-Q5, independent issues, verdict
codex
The canonical docs confirm this spec is intentionally not the AVD/DexClassLoader path, but it also inherits current W3D1 constraints: `activity_basic` is skipped today because custom view class loading is missing, while `activity_minimal` already succeeds. I’m checking whether the proposed hook points match the implementation surface.
exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import java.net.URL
     4	import java.net.URLClassLoader
     5	import java.nio.file.Files
     6	import java.nio.file.Path
     7	import kotlin.io.path.exists
     8	import kotlin.io.path.isDirectory
     9	import kotlin.io.path.isRegularFile
    10	import kotlin.io.path.name
    11	
    12	/**
    13	 * W1D3-D4 L1 spike 의 entry-point.
    14	 *
    15	 * 07 §2.2 canonical 번들 구조 (08 §7.5 errata 적용 — JAR 이름 정명화):
    16	 *   server/libs/layoutlib-dist/android-34/
    17	 *   ├── layoutlib-*.jar               (Maven 좌표: com.android.tools.layoutlib:layoutlib)
    18	 *   └── data/
    19	 *       ├── res/
    20	 *       ├── fonts/
    21	 *       ├── keyboards/
    22	 *       ├── icu/
    23	 *       └── linux/lib64/
    24	 *
    25	 * 이 클래스는 번들 디렉토리에서 JAR + native lib path + framework data 를 찾아
    26	 * 격리된 URLClassLoader 를 만들고, 그 안에서 reflection 으로 `Bridge.init(...)` 호출.
    27	 *
    28	 * 왜 reflection? layoutlib 은 Android 프레임워크의 내부 API 에 직접 의존하므로, compile-time
    29	 * 의존을 두면 :layoutlib-worker 가 android.jar / layoutlib.jar 를 classpath 에 가지고 있어야
    30	 * 컴파일되지만, 그것은 배포 관점에서 번거롭고 버전 고정 문제 발생. 런타임 reflection 이 canonical.
    31	 *
    32	 * Week 1 Day 1 는 번들 존재 여부 + 위치 탐지만 구현. 실제 `Bridge.init` 호출은 W1D4.
    33	 */
    34	class LayoutlibBootstrap(val distDir: Path) {
    35	
    36	    init {
    37	        require(distDir.exists() && distDir.isDirectory()) {
    38	            "layoutlib-dist 디렉토리가 없음: $distDir — 07 §2.2 번들 구조를 확인하세요"
    39	        }
    40	    }
    41	
    42	    /** `data/` 절대 경로. */
    43	    fun dataDir(): Path = distDir.resolve(LayoutlibPaths.DATA_DIR)
    44	
    45	    /** `data/fonts/` 절대 경로 (Bridge.init 인자). */
    46	    fun fontsDir(): Path = distDir.resolve(LayoutlibPaths.FONTS_DIR)
    47	
    48	    /** `data/linux/lib64/` 절대 경로 (libandroid_runtime.so 위치). */
    49	    fun nativeLibDir(): Path = distDir.resolve(LayoutlibPaths.LINUX_LIB64_DIR)
    50	
    51	    /**
    52	     * `data/build.prop` 을 key=value 라인 단위로 파싱.
    53	     * `Bridge.init` 의 첫 번째 인자(platform properties Map) 로 사용.
    54	     */
    55	    fun parseBuildProperties(): Map<String, String> {
    56	        val buildProp = distDir.resolve(LayoutlibPaths.BUILD_PROP)
    57	        require(buildProp.isRegularFile()) { "data/build.prop 누락: $buildProp" }
    58	        val props = mutableMapOf<String, String>()
    59	        for (raw in Files.readAllLines(buildProp)) {
    60	            val line = raw.trim()
    61	            if (line.isEmpty() || line.startsWith("#")) continue
    62	            val eq = line.indexOf('=')
    63	            if (eq <= 0) continue
    64	            props[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
    65	        }
    66	        return props
    67	    }
    68	
    69	    /**
    70	     * `data/icu/` 의 `icudt`로 시작하고 `.dat` 으로 끝나는 파일 절대 경로 (Bridge.init 의 icuDataPath 인자).
    71	     * 여러 개면 lex 최신.
    72	     */
    73	    fun findIcuDataFile(): Path? {
    74	        val icuDir = distDir.resolve(LayoutlibPaths.ICU_DIR).toFile()
    75	        if (!icuDir.isDirectory) return null
    76	        val matches = icuDir.listFiles { f ->
    77	            f.isFile && f.name.startsWith("icudt") && f.name.endsWith(".dat")
    78	        }?.sortedByDescending { it.name } ?: emptyList()
    79	        return matches.firstOrNull()?.toPath()
    80	    }
    81	
    82	    /**
    83	     * `data/keyboards/` 내 `.kcm` 키맵 파일 절대 경로 목록 (Bridge.init 의 String[] keyboardPaths 인자).
    84	     * Kotlin 의 nested block-comment 규칙 회피를 위해 glob 표기(`/` 다음 별표) 사용 금지.
    85	     */
    86	    fun listKeyboardPaths(): List<String> {
    87	        val keyboardsDir = distDir.resolve(LayoutlibPaths.KEYBOARDS_DIR).toFile()
    88	        if (!keyboardsDir.isDirectory) return emptyList()
    89	        return keyboardsDir.listFiles { f -> f.isFile && f.name.endsWith(".kcm") }
    90	            ?.sortedBy { it.name }
    91	            ?.map { it.absolutePath }
    92	            ?: emptyList()
    93	    }
    94	
    95	    /**
    96	     * 번들 디렉토리의 필수 구성요소 존재 여부 검사.
    97	     * 하나라도 누락되면 UnrenderableReason 으로 매핑되는 Result 반환.
    98	     *
    99	     * 08 §7.5 정명화: layoutlib + layoutlib-api 둘 다 필수 (W1D4-R3 정정).
   100	     * layoutlib-api JAR 의 parent 클래스(`com.android.ide.common.rendering.api.Bridge` 등)가
   101	     * 없으면 Bridge 클래스 로드가 NoClassDefFoundError 로 실패.
   102	     */
   103	    fun validate(): ValidationResult {
   104	        val missing = mutableListOf<String>()
   105	        val dataDir = distDir.resolve("data")
   106	
   107	        if (!findLayoutlibJar().exists) missing += "layoutlib-*.jar (root of dist dir, Maven com.android.tools.layoutlib:layoutlib)"
   108	        if (!findLayoutlibApiJar().exists) missing += "layoutlib-api-*.jar (root of dist dir, Maven com.android.tools.layoutlib:layoutlib-api)"
   109	        if (!dataDir.resolve("res").isDirectory()) missing += "data/res/"
   110	        if (!dataDir.resolve("fonts").isDirectory()) missing += "data/fonts/"
   111	        if (!dataDir.resolve("icu").isDirectory()) missing += "data/icu/"
   112	
   113	        return if (missing.isEmpty()) ValidationResult.Ok
   114	        else ValidationResult.MissingComponents(missing.toList())
   115	    }
   116	
   117	    /**
   118	     * distDir 바로 아래에서 main Bridge JVM JAR 을 찾음 (Maven 좌표 com.android.tools.layoutlib:layoutlib).
   119	     * 여러 개 있으면 lexicographic 최신(큰 이름) 선택 — 버전 pin 은 번들러가 책임.
   120	     *
   121	     * sibling 좌표 -api/-runtime/-resources 는 별도 메서드로 제공 — 글롭 충돌 회피 (08 §7.5).
   122	     */
   123	    fun findLayoutlibJar(): JarLookup {
   124	        val jars = distDir.toFile().listFiles { f ->
   125	            f.isFile && f.name.startsWith("layoutlib-") && f.name.endsWith(".jar")
   126	                && !f.name.startsWith("layoutlib-api-")
   127	                && !f.name.startsWith("layoutlib-runtime-")
   128	                && !f.name.startsWith("layoutlib-resources-")
   129	        }?.sortedByDescending { it.name } ?: emptyList()
   130	        return JarLookup(path = jars.firstOrNull()?.toPath(), exists = jars.isNotEmpty())
   131	    }
   132	
   133	    /**
   134	     * distDir 바로 아래에서 API surface JAR 을 찾음 (Maven 좌표 com.android.tools.layoutlib:layoutlib-api).
   135	     * Bridge 의 parent 클래스 + ILayoutLog 등 public API 인터페이스 포함 — Bridge 로드에 필수.
   136	     */
   137	    fun findLayoutlibApiJar(): JarLookup {
   138	        val jars = distDir.toFile().listFiles { f ->
   139	            f.isFile && f.name.startsWith("layoutlib-api-") && f.name.endsWith(".jar")
   140	        }?.sortedByDescending { it.name } ?: emptyList()
   141	        return JarLookup(path = jars.firstOrNull()?.toPath(), exists = jars.isNotEmpty())
   142	    }
   143	
   144	    /**
   145	     * 격리된 URLClassLoader 구성.
   146	     *
   147	     * W2D6-FATJAR (08 §7.7): parent = system classloader 로 변경.
   148	     * 이전에는 platform classloader 를 parent 로 써서 worker runtime classpath 의
   149	     * Guava/kxml2/ICU4J 가 Bridge 내부 reflection 에서 보이지 않아 NoClassDefFoundError 발생.
   150	     * system classloader 는 worker runtimeOnly deps 를 포함하므로 Bridge.init 이 정상 진행된다.
   151	     *
   152	     * 경계: Kotlin stdlib 가 system classloader 에도 보이므로 layoutlib 내부 클래스가
   153	     * worker 의 stdlib 를 사용하게 됨 — Kotlin 1.9.x 단일 버전 환경(W2D6)에서는 안전하나
   154	     * W3+ classpath 확장 시 재검토 필요.
   155	     *
   156	     * W2D7-F1 (페어 리뷰 FULL CONVERGENCE): layoutlib-api JAR URL 은 **포함하지 않음**.
   157	     * :layoutlib-worker 의 implementation dep 으로 system CL 에 이미 존재하므로, parent-first
   158	     * delegation 으로 단일 Class 객체가 보장된다. 자식 URL 에도 추가하면 (이전 패턴) parent-first
   159	     * 로 shadow 되지만, loader 정책이 바뀌면 ClassCastException 트랩. single source of truth.
   160	     * layoutlib 본체 (Bridge 구현) 는 dist JAR 에서만 로드 — API 는 system CL.
   161	     */
   162	    fun createIsolatedClassLoader(): ClassLoader {
   163	        val mainJar = findLayoutlibJar().path
   164	            ?: error("layoutlib JAR 이 없음: ${validate()}")
   165	        // findLayoutlibApiJar() 는 validate() 경로를 위해 유지하되, 여기서는 URL 에 넣지 않는다.
   166	        // native lib 는 System.load 로 따로 로드 (Bridge.init 내부) — 여기서는 JAR 만 추가.
   167	        val urls = arrayOf<URL>(mainJar.toUri().toURL())
   168	        return URLClassLoader(urls, ClassLoader.getSystemClassLoader())
   169	    }
   170	
   171	    sealed class ValidationResult {
   172	        data object Ok : ValidationResult()
   173	        data class MissingComponents(val components: List<String>) : ValidationResult()
   174	    }
   175	
   176	    data class JarLookup(val path: Path?, val exists: Boolean)
   177	}
   178	
   179	/** 번들 디렉토리 내 `data/` 하위 경로들을 표준화된 상수로 제공. */
   180	object LayoutlibPaths {
   181	    const val DATA_DIR = "data"
   182	    const val RES_DIR = "data/res"
   183	    const val FONTS_DIR = "data/fonts"
   184	    const val KEYBOARDS_DIR = "data/keyboards"
   185	    const val ICU_DIR = "data/icu"
   186	    const val LINUX_LIB64_DIR = "data/linux/lib64"
   187	    const val BUILD_PROP = "data/build.prop"
   188	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt | sed -n '1,320p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import com.android.ide.common.rendering.api.RenderSession
     4	import com.android.ide.common.rendering.api.Result
     5	import com.android.ide.common.rendering.api.SessionParams
     6	import dev.axp.layoutlib.worker.resources.FrameworkRenderResources
     7	import dev.axp.layoutlib.worker.resources.FrameworkResourceValueLoader
     8	import dev.axp.layoutlib.worker.resources.ResourceLoaderConstants
     9	import dev.axp.layoutlib.worker.session.LayoutPullParserAdapter
    10	import dev.axp.layoutlib.worker.session.MinimalLayoutlibCallback
    11	import dev.axp.layoutlib.worker.session.SessionConstants
    12	import dev.axp.layoutlib.worker.session.SessionParamsFactory
    13	import dev.axp.protocol.render.PngRenderer
    14	import dev.axp.layoutlib.worker.LayoutlibRendererConstants.BRIDGE_DISPOSE_METHOD
    15	import dev.axp.layoutlib.worker.LayoutlibRendererConstants.BRIDGE_FQN
    16	import dev.axp.layoutlib.worker.LayoutlibRendererConstants.BRIDGE_INIT_METHOD
    17	import dev.axp.layoutlib.worker.LayoutlibRendererConstants.HEADLESS_PROPERTY_KEY
    18	import dev.axp.layoutlib.worker.LayoutlibRendererConstants.HEADLESS_PROPERTY_VALUE
    19	import dev.axp.layoutlib.worker.LayoutlibRendererConstants.ILAYOUT_LOG_FQN
    20	import dev.axp.layoutlib.worker.LayoutlibRendererConstants.IMAGE_FORMAT_PNG
    21	import dev.axp.layoutlib.worker.LayoutlibRendererConstants.NATIVE_LIB_NAME
    22	import java.awt.image.BufferedImage
    23	import java.io.ByteArrayOutputStream
    24	import java.lang.reflect.InvocationHandler
    25	import java.lang.reflect.Method
    26	import java.lang.reflect.Modifier
    27	import java.lang.reflect.Proxy
    28	import java.nio.file.Path
    29	import javax.imageio.ImageIO
    30	import kotlin.io.path.absolutePathString
    31	
    32	/**
    33	 * W2D6-FATJAR (08 §7.7 blocker #3) → W2D7-RENDERSESSION (08 §7.7.1 item 3b).
    34	 *
    35	 * W2D7 에서 `renderViaLayoutlib` 는 실제 `Bridge.createSession(SessionParams)` →
    36	 * `RenderSession.render(timeout)` → `session.image: BufferedImage` 경로로 교체되었다.
    37	 * Bridge.init 은 한 번만 수행 (JVM 생애). RenderSession 은 per-render 생성-dispose.
    38	 *
    39	 * 경계:
    40	 *  - 커스텀 뷰 (ConstraintLayout, MaterialButton 등) 는 `MinimalLayoutlibCallback.loadView`
    41	 *    가 UnsupportedOperationException 으로 거부 — activity_basic.xml 은 W3+ L3 타겟.
    42	 *  - fixture XML 을 찾지 못하면 즉시 throw (버그). RenderSession 내부 실패는 fallback 으로 위임.
    43	 */
    44	class LayoutlibRenderer(
    45	    private val distDir: Path,
    46	    private val fallback: PngRenderer?,
    47	    private val fixtureRoot: Path,
    48	) : PngRenderer {
    49	
    50	    private val bootstrap = LayoutlibBootstrap(distDir)
    51	
    52	    @Volatile private var initialized = false
    53	    @Volatile private var classLoader: ClassLoader? = null
    54	    @Volatile private var bridgeInstance: Any? = null
    55	
    56	    /**
    57	     * W2D7 3b-arch 진단 hook — 마지막 `createSession` 호출의 Result. createSession 이 inflate 를
    58	     * 시도한 뒤의 즉시 상태를 담는다 (render() 가 이후 ERROR_NOT_INFLATED 를 추가로 반환해도
    59	     * 덮어쓰지 않음 — 원인은 createSession 에 있음).
    60	     *
    61	     * 현재 3b-values (framework resource VALUE 파싱) 이 W3 carry 로 split 되어 있어 실 inflate 는
    62	     * `ERROR_INFLATION (config_scrollbarSize not found)` 에서 중단된다.
    63	     */
    64	    @Volatile var lastCreateSessionResult: Result? = null
    65	        private set
    66	
    67	    /** `render()` 호출의 Result (createSession 이 성공했을 때만 의미 있음). */
    68	    @Volatile var lastRenderResult: Result? = null
    69	        private set
    70	
    71	    /**
    72	     * 진단용 편의: createSession 이 실패했다면 그 result, 아니면 render 의 result.
    73	     * 3b-arch tests 는 이 값을 assert.
    74	     */
    75	    val lastSessionResult: Result?
    76	        get() = lastCreateSessionResult?.takeIf { !it.isSuccess } ?: lastRenderResult
    77	
    78	    init {
    79	        System.setProperty(HEADLESS_PROPERTY_KEY, HEADLESS_PROPERTY_VALUE)
    80	    }
    81	
    82	    override fun renderPng(layoutName: String): ByteArray {
    83	        if (!initialized) {
    84	            initBridge()
    85	        }
    86	        return renderViaLayoutlib(layoutName)
    87	            ?: (fallback?.renderPng(layoutName)
    88	                ?: error("LayoutlibRenderer 실패 + fallback 없음: $layoutName"))
    89	    }
    90	
    91	    @Synchronized
    92	    private fun initBridge() {
    93	        if (initialized) return
    94	        val cl = bootstrap.createIsolatedClassLoader()
    95	        val bridgeClass = Class.forName(BRIDGE_FQN, false, cl)
    96	        val initMethod = bridgeClass.declaredMethods.first { it.name == BRIDGE_INIT_METHOD }
    97	
    98	        val nativeLib = bootstrap.nativeLibDir().resolve(NATIVE_LIB_NAME)
    99	        if (nativeLib.toFile().exists()) {
   100	            try {
   101	                System.load(nativeLib.absolutePathString())
   102	            } catch (_: Throwable) {
   103	                // native lib 로딩 실패는 렌더 실패로 처리하지 않음 — init 시도는 계속.
   104	            }
   105	        }
   106	
   107	        val platformProps = bootstrap.parseBuildProperties()
   108	        val fontDir = bootstrap.fontsDir().toFile()
   109	        val nativeLibPath = bootstrap.nativeLibDir().absolutePathString()
   110	        val icuPath = bootstrap.findIcuDataFile()?.absolutePathString()
   111	            ?: error("ICU data 파일 누락 (data/icu/icudt*.dat)")
   112	        val keyboardPaths = bootstrap.listKeyboardPaths().toTypedArray()
   113	        val enumValueMap = mutableMapOf<String, MutableMap<String, Int>>()
   114	
   115	        val logInterface = Class.forName(ILAYOUT_LOG_FQN, false, cl)
   116	        val logProxy = Proxy.newProxyInstance(cl, arrayOf(logInterface), NoopLogHandler())
   117	
   118	        val instance = bridgeClass.getDeclaredConstructor().newInstance()
   119	        initMethod.invoke(
   120	            instance,
   121	            platformProps,
   122	            fontDir,
   123	            nativeLibPath,
   124	            icuPath,
   125	            keyboardPaths,
   126	            enumValueMap,
   127	            logProxy
   128	        )
   129	
   130	        classLoader = cl
   131	        bridgeInstance = instance
   132	        initialized = true
   133	
   134	        Runtime.getRuntime().addShutdownHook(Thread {
   135	            try {
   136	                bridgeClass.getDeclaredMethod(BRIDGE_DISPOSE_METHOD).invoke(instance)
   137	            } catch (_: Throwable) {}
   138	        })
   139	    }
   140	
   141	    /**
   142	     * W2D7: 실 Bridge.createSession → RenderSession.render → BufferedImage → PNG 경로.
   143	     *
   144	     * 흐름:
   145	     *  1. fixture 경로에서 XML 로드 → LayoutPullParserAdapter.
   146	     *  2. SessionParamsFactory.build(parser) → SessionParams.
   147	     *  3. bridge.createSession(params) 를 reflection 으로 호출 (bridge 는 isolated CL 에서 로드된
   148	     *     인스턴스; SessionParams 는 system CL 의 동일 클래스 타입 — parent-first delegation 이
   149	     *     동일 Class identity 를 보장).
   150	     *  4. session.render(timeout) → result.isSuccess 체크.
   151	     *  5. session.image → ImageIO.write PNG → bytes.
   152	     *  6. 무조건 session.dispose().
   153	     *
   154	     * 실패 시 (result non-success, exception, fixture 없음 이외) → null 반환 → fallback.
   155	     */
   156	    private fun renderViaLayoutlib(layoutName: String): ByteArray? {
   157	        val layoutPath = fixtureRoot.resolve(layoutName)
   158	        require(layoutPath.toFile().isFile) {
   159	            "fixture 레이아웃을 찾을 수 없음: $layoutPath"
   160	        }
   161	
   162	        val parser = LayoutPullParserAdapter.fromFile(layoutPath)
   163	        // W3D1 3b-values: framework resource VALUE loader 가 10 XML 을 파싱하여 RenderResources 에 주입.
   164	        // JVM-wide cache 라 첫 호출만 파싱 비용 발생.
   165	        val bundle = FrameworkResourceValueLoader.loadOrGet(
   166	            distDir.resolve(ResourceLoaderConstants.DATA_DIR)
   167	        )
   168	        val resources = FrameworkRenderResources(bundle, SessionConstants.DEFAULT_FRAMEWORK_THEME)
   169	        val params: SessionParams = SessionParamsFactory.build(
   170	            layoutParser = parser,
   171	            callback = MinimalLayoutlibCallback(),
   172	            resources = resources,
   173	        )
   174	
   175	        val bridge = bridgeInstance ?: return null
   176	
   177	        // createSession 은 bridge 클래스에서 선언. parent-first 덕분에 인자/반환 타입이 system CL 의
   178	        // SessionParams / RenderSession 과 동일.
   179	        // 페어 리뷰 (Codex + Claude convergent): parameterCount==1 만 매칭하면 향후 layoutlib 이
   180	        // 다른 1-arg createSession 오버로드를 추가할 때 잘못된 메서드를 선택할 수 있으므로, 인자
   181	        // 타입을 SessionParams 로 명시 검증.
   182	        val createSession = bridge.javaClass.methods.firstOrNull {
   183	            it.name == BRIDGE_CREATE_SESSION &&
   184	                it.parameterCount == BRIDGE_CREATE_SESSION_PARAM_COUNT &&
   185	                it.parameterTypes.singleOrNull()?.name == SessionParams::class.java.name
   186	        } ?: return null
   187	
   188	        val session = try {
   189	            createSession.invoke(bridge, params) as? RenderSession ?: return null
   190	        } catch (t: Throwable) {
   191	            // Bridge 내부 예외 — fallback 으로 위임.
   192	            t.printStackTrace(System.err)
   193	            return null
   194	        }
   195	
   196	        // createSession 이 inflate 까지 시도. 실패 시 session.result 에 상태가 담긴다.
   197	        // 3b-arch hook: tests 가 architecture-positive evidence 로 사용.
   198	        val initialResult = session.result
   199	        lastCreateSessionResult = initialResult
   200	        initialResult?.let {
   201	            System.err.println(
   202	                "[LayoutlibRenderer] createSession result: status=${it.status} " +
   203	                    "msg=${it.errorMessage} exc=${it.exception?.javaClass?.simpleName}"
   204	            )
   205	        }
   206	
   207	        try {
   208	            val result = session.render(SessionConstants.RENDER_TIMEOUT_MS)
   209	            lastRenderResult = result
   210	            if (!result.isSuccess) {
   211	                System.err.println(
   212	                    "[LayoutlibRenderer] RenderSession.render failed: status=${result.status} " +
   213	                        "msg=${result.errorMessage} exc=${result.exception?.javaClass?.simpleName}"
   214	                )
   215	                return null
   216	            }
   217	            val image: BufferedImage = session.image ?: return null
   218	            val baos = ByteArrayOutputStream()
   219	            ImageIO.write(image, IMAGE_FORMAT_PNG, baos)
   220	            return baos.toByteArray()
   221	        } catch (t: Throwable) {
   222	            t.printStackTrace(System.err)
   223	            return null
   224	        } finally {
   225	            try {
   226	                session.dispose()
   227	            } catch (_: Throwable) {}
   228	        }
   229	    }
   230	
   231	    private class NoopLogHandler : InvocationHandler {
   232	        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
   233	            return when (method.returnType.name) {
   234	                "void" -> null
   235	                "boolean" -> false
   236	                else -> if (Modifier.isStatic(method.modifiers)) null
   237	                        else if (method.returnType.isPrimitive) 0
   238	                        else null
   239	            }
   240	        }
   241	    }
   242	
   243	    companion object {
   244	        private const val BRIDGE_CREATE_SESSION = "createSession"
   245	
   246	        /** `createSession(SessionParams)` 의 parameter count — strict 리플렉션 매칭용. */
   247	        private const val BRIDGE_CREATE_SESSION_PARAM_COUNT = 1
   248	    }
   249	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt | sed -n '1,320p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.session
     2	
     3	import com.android.ide.common.rendering.api.ActionBarCallback
     4	import com.android.ide.common.rendering.api.AdapterBinding
     5	import com.android.ide.common.rendering.api.ILayoutPullParser
     6	import com.android.ide.common.rendering.api.LayoutlibCallback
     7	import com.android.ide.common.rendering.api.ResourceReference
     8	import com.android.ide.common.rendering.api.ResourceValue
     9	import org.kxml2.io.KXmlParser
    10	import org.xmlpull.v1.XmlPullParser
    11	import java.util.concurrent.atomic.AtomicInteger
    12	
    13	/**
    14	 * W2D7-RENDERSESSION — LayoutlibCallback 의 최소 구현체.
    15	 *
    16	 * 구현 범위 (activity_minimal.xml 기준):
    17	 *  - resource id 양방향 map (getOrGenerateResourceId ↔ resolveResourceId). 스레드-세이프.
    18	 *  - loadView: 커스텀 뷰 요구 시 즉시 UnsupportedOperationException — 프레임워크 위젯은
    19	 *    Bridge 내부에서 처리되므로 호출되지 않음이 불변식 (custom view = L3 W3+).
    20	 *  - getAdapterBinding: ListView/Spinner 데이터 바인딩 없음 → null.
    21	 *  - getActionBarCallback: 기본 ActionBarCallback() — setForceNoDecor 로 어차피 action bar 미표시.
    22	 *  - XmlParserFactory 메서드: 본 W2D7 fixture 에서 호출될 일 없으나 interface 계약상 KXmlParser 반환.
    23	 */
    24	class MinimalLayoutlibCallback : LayoutlibCallback() {
    25	
    26	    private val nextId = AtomicInteger(FIRST_ID)
    27	    private val byRef = mutableMapOf<ResourceReference, Int>()
    28	    private val byId = mutableMapOf<Int, ResourceReference>()
    29	
    30	    @Synchronized
    31	    override fun getOrGenerateResourceId(ref: ResourceReference): Int {
    32	        byRef[ref]?.let { return it }
    33	        val id = nextId.getAndIncrement()
    34	        byRef[ref] = id
    35	        byId[id] = ref
    36	        return id
    37	    }
    38	
    39	    @Synchronized
    40	    override fun resolveResourceId(id: Int): ResourceReference? = byId[id]
    41	
    42	    override fun loadView(name: String, constructorSignature: Array<out Class<*>>?, constructorArgs: Array<out Any>?): Any {
    43	        throw UnsupportedOperationException(
    44	            "W2D7 minimal: custom view '$name' 은 L3 (W3+) 타겟. activity_minimal.xml 은 framework 위젯만 허용."
    45	        )
    46	    }
    47	
    48	    override fun getParser(layoutResource: ResourceValue?): ILayoutPullParser? = null
    49	
    50	    override fun getAdapterBinding(cookie: Any?, attributes: Map<String, String>): AdapterBinding? = null
    51	
    52	    override fun getActionBarCallback(): ActionBarCallback = ActionBarCallback()
    53	
    54	    override fun createXmlParser(): XmlPullParser = buildKxml()
    55	
    56	    override fun createXmlParserForFile(fileName: String): XmlPullParser = buildKxml()
    57	
    58	    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser = buildKxml()
    59	
    60	    override fun getApplicationId(): String = APPLICATION_ID
    61	
    62	    private fun buildKxml(): XmlPullParser = KXmlParser().also {
    63	        it.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    64	    }
    65	
    66	    companion object {
    67	        /** 생성 id 기저. 0x7F 패밀리 (android studio 관례) 의 하위. */
    68	        private const val FIRST_ID = 0x7F0A_0000
    69	
    70	        /** Bridge.mProjectKey lookup 등 내부 진단에 쓰일 수 있는 안정적 app id. */
    71	        private const val APPLICATION_ID = "axp.render"
    72	    }
    73	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.session
     2	
     3	import com.android.ide.common.rendering.api.HardwareConfig
     4	import com.android.ide.common.rendering.api.ILayoutLog
     5	import com.android.ide.common.rendering.api.ILayoutPullParser
     6	import com.android.ide.common.rendering.api.LayoutlibCallback
     7	import com.android.ide.common.rendering.api.RenderResources
     8	import com.android.ide.common.rendering.api.SessionParams
     9	import com.android.resources.Density
    10	import com.android.resources.ScreenOrientation
    11	import com.android.resources.ScreenRound
    12	import com.android.resources.ScreenSize
    13	import java.lang.reflect.InvocationHandler
    14	import java.lang.reflect.Method
    15	import java.lang.reflect.Modifier
    16	import java.lang.reflect.Proxy
    17	
    18	/**
    19	 * W2D7-RENDERSESSION (08 §7.7.1 item 3b) — `Bridge.createSession(params)` 에 전달할 SessionParams
    20	 * 를 일관된 설정으로 생성하는 팩토리.
    21	 *
    22	 * W3D1 3b-values: resources 파라미터 default 제거 — 호출자가 FrameworkRenderResources 를 주입해야 함
    23	 * (CLAUDE.md "No default parameter values" 정책 + framework VALUE loader 배선).
    24	 */
    25	object SessionParamsFactory {
    26	
    27	    /**
    28	     * @param layoutParser fixture XML 을 감싼 ILayoutPullParser.
    29	     * @param callback LayoutlibCallback — 호출자 주입 필수 (production 은 `MinimalLayoutlibCallback()`).
    30	     * @param resources RenderResources — 호출자 주입 필수. production 경로는 FrameworkRenderResources.
    31	     *
    32	     * CLAUDE.md "No default parameter values" 준수 (W3D1 impl-pair-review MF2):
    33	     * Codex reviewer 가 Q2 에서 `callback` default 가 아직 존재함을 지적 → 제거.
    34	     */
    35	    fun build(
    36	        layoutParser: ILayoutPullParser,
    37	        callback: LayoutlibCallback,
    38	        resources: RenderResources,
    39	    ): SessionParams {
    40	        val hardware = HardwareConfig(
    41	            SessionConstants.RENDER_WIDTH_PX,
    42	            SessionConstants.RENDER_HEIGHT_PX,
    43	            Density.XHIGH,
    44	            SessionConstants.DPI_XHIGH,
    45	            SessionConstants.DPI_XHIGH,
    46	            ScreenSize.NORMAL,
    47	            ScreenOrientation.PORTRAIT,
    48	            ScreenRound.NOTROUND,
    49	            /* softwareButtons */ false
    50	        )
    51	
    52	        val logProxy = Proxy.newProxyInstance(
    53	            ILayoutLog::class.java.classLoader,
    54	            arrayOf(ILayoutLog::class.java),
    55	            NoopLogHandler
    56	        ) as ILayoutLog
    57	
    58	        val params = SessionParams(
    59	            layoutParser,
    60	            SessionParams.RenderingMode.NORMAL,
    61	            SessionConstants.PROJECT_KEY,
    62	            hardware,
    63	            resources,
    64	            callback,
    65	            SessionConstants.MIN_SDK,
    66	            SessionConstants.TARGET_SDK,
    67	            logProxy
    68	        )
    69	
    70	        // F2: AssetRepository 미지원 + decor 제거 → status bar/action bar 없는 content-only.
    71	        params.setAssetRepository(NoopAssetRepository())
    72	        params.setForceNoDecor()
    73	        params.setRtlSupport(true)
    74	        params.setLocale(SessionConstants.RENDER_LOCALE)
    75	        params.setFontScale(1.0f)
    76	        // W2D7 시도 2: FolderConfiguration 매칭이 "current configuration" 에서 base 리소스
    77	        // (values/config.xml) 를 못 찾는 문제가 uiMode/night mode 미지정 때문일 수 있어 명시적으로 설정.
    78	        params.setUiMode(SessionConstants.UI_MODE_NORMAL_DAY)
    79	        params.timeout = SessionConstants.RENDER_TIMEOUT_MS
    80	
    81	        return params
    82	    }
    83	
    84	    /**
    85	     * ILayoutLog 프록시 — 진단을 위해 error/warning/fidelityWarning 호출을 stderr 로 dump.
    86	     * 정상 경로에서는 무작업. TDD / layoutlib 내부 예외 추적 용도.
    87	     * Bridge.createSession 에 전달되어 layoutlib 이 inflate/render 중 발견한 문제를 통보.
    88	     */
    89	    private object NoopLogHandler : InvocationHandler {
    90	        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
    91	            val name = method.name
    92	            if (name in LOG_METHODS_TO_DUMP) {
    93	                System.err.println(
    94	                    "[layoutlib.${name}] " + (args?.joinToString(" | ") { safeStr(it) } ?: "")
    95	                )
    96	                val throwable = args?.firstOrNull { it is Throwable } as? Throwable
    97	                throwable?.printStackTrace(System.err)
    98	            }
    99	            return when (method.returnType.name) {
   100	                "void" -> null
   101	                "boolean" -> false
   102	                else -> if (Modifier.isStatic(method.modifiers)) null
   103	                        else if (method.returnType.isPrimitive) 0
   104	                        else null
   105	            }
   106	        }
   107	
   108	        private fun safeStr(v: Any?): String = when (v) {
   109	            null -> "null"
   110	            is Throwable -> "${v.javaClass.simpleName}: ${v.message}"
   111	            is Array<*> -> v.joinToString(",") { safeStr(it) }
   112	            else -> v.toString().take(300)
   113	        }
   114	
   115	        private val LOG_METHODS_TO_DUMP = setOf(
   116	            "error", "warning", "fidelityWarning", "logAndroidFramework"
   117	        )
   118	    }
   119	}

exec
/bin/bash -lc "nl -ba fixture/sample-app/app/build.gradle.kts | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	plugins {
     2	    id("com.android.application")
     3	    id("org.jetbrains.kotlin.android")
     4	}
     5	
     6	android {
     7	    namespace = "com.fixture"
     8	    compileSdk = 34
     9	
    10	    defaultConfig {
    11	        applicationId = "com.fixture"
    12	        minSdk = 21
    13	        targetSdk = 34
    14	        versionCode = 1
    15	        versionName = "0.1.0"
    16	    }
    17	
    18	    buildTypes {
    19	        getByName("debug") {
    20	            isMinifyEnabled = false
    21	            isDebuggable = true
    22	        }
    23	        getByName("release") {
    24	            isMinifyEnabled = false  // W1D1 fixture 는 release 도 unminified (08 §2.5 release 미지원)
    25	        }
    26	    }
    27	
    28	    compileOptions {
    29	        sourceCompatibility = JavaVersion.VERSION_17
    30	        targetCompatibility = JavaVersion.VERSION_17
    31	    }
    32	}
    33	
    34	// AGP 8.x 에서 `kotlinOptions` 블록은 deprecated (향후 제거). canonical 대체:
    35	// kotlin-android plugin 의 kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }
    36	kotlin {
    37	    compilerOptions {
    38	        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    39	    }
    40	}
    41	
    42	dependencies {
    43	    implementation("androidx.appcompat:appcompat:1.6.1")
    44	    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    45	    implementation("com.google.android.material:material:1.12.0")
    46	}

exec
/bin/bash -lc "nl -ba fixture/sample-app/app/src/main/res/layout/activity_basic.xml | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	<?xml version="1.0" encoding="utf-8"?>
     2	<!--
     3	  08 §5 item 6 — W1D4 L1 spike 타겟.
     4	  Material theme + LinearLayout + TextView + Button.
     5	  layoutlib 이 이 레이아웃을 렌더하는 데 성공하면 Foundation 주차의 exit criterion 만족.
     6	-->
     7	<androidx.constraintlayout.widget.ConstraintLayout
     8	    xmlns:android="http://schemas.android.com/apk/res/android"
     9	    xmlns:app="http://schemas.android.com/apk/res-auto"
    10	    xmlns:tools="http://schemas.android.com/tools"
    11	    android:layout_width="match_parent"
    12	    android:layout_height="match_parent"
    13	    android:padding="16dp"
    14	    tools:context=".MainActivity">
    15	
    16	    <LinearLayout
    17	        android:layout_width="match_parent"
    18	        android:layout_height="wrap_content"
    19	        android:orientation="vertical"
    20	        android:gravity="center_horizontal"
    21	        app:layout_constraintTop_toTopOf="parent"
    22	        app:layout_constraintStart_toStartOf="parent"
    23	        app:layout_constraintEnd_toEndOf="parent">
    24	
    25	        <TextView
    26	            android:id="@+id/title"
    27	            android:layout_width="wrap_content"
    28	            android:layout_height="wrap_content"
    29	            android:text="@string/fixture_basic_title"
    30	            android:textAppearance="?attr/textAppearanceHeadlineMedium"
    31	            android:textColor="?attr/colorOnSurface"
    32	            android:layout_marginBottom="24dp" />
    33	
    34	        <TextView
    35	            android:id="@+id/body"
    36	            android:layout_width="wrap_content"
    37	            android:layout_height="wrap_content"
    38	            android:text="@string/fixture_basic_body"
    39	            android:textAppearance="?attr/textAppearanceBodyMedium"
    40	            android:textColor="?attr/colorOnSurfaceVariant"
    41	            android:layout_marginBottom="32dp" />
    42	
    43	        <com.google.android.material.button.MaterialButton
    44	            android:id="@+id/primary_button"
    45	            android:layout_width="wrap_content"
    46	            android:layout_height="wrap_content"
    47	            android:text="@string/fixture_basic_button"
    48	            app:icon="@drawable/ic_sparkle" />
    49	    </LinearLayout>
    50	</androidx.constraintlayout.widget.ConstraintLayout>

exec
/bin/bash -lc "rg -n \"LayoutlibRendererIntegrationTest|LayoutlibRendererTier3MinimalTest|SharedLayoutlibRenderer|forkEvery|includeTags|MinimalLayoutlibCallback\" server/layoutlib-worker/src server/layoutlib-worker/build.gradle.kts server/build.gradle.kts server/gradle -g '*.kt' -g '*.kts'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/layoutlib-worker/build.gradle.kts:59:// unit test (no integration tag) 는 forkEvery 비활성 — 빠른 실행 유지.
server/layoutlib-worker/build.gradle.kts:61:    val includeTagsProp = providers.gradleProperty("includeTags").orNull
server/layoutlib-worker/build.gradle.kts:62:    if (!includeTagsProp.isNullOrBlank() && includeTagsProp.contains("integration")) {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/DistDiscovery.kt:13: * Main.kt (CLI) 와 LayoutlibRendererTier3MinimalTest (테스트) 양쪽이 사용하는
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:10:import dev.axp.layoutlib.worker.session.MinimalLayoutlibCallback
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:40: *  - 커스텀 뷰 (ConstraintLayout, MaterialButton 등) 는 `MinimalLayoutlibCallback.loadView`
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:171:            callback = MinimalLayoutlibCallback(),
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:29:     * @param callback LayoutlibCallback — 호출자 주입 필수 (production 은 `MinimalLayoutlibCallback()`).
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:24:class MinimalLayoutlibCallback : LayoutlibCallback() {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt:6: * W3D2 integration cleanup (Codex F4 carry) — SharedLayoutlibRenderer 의 args 일관성
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt:23:            "SharedLayoutlibRenderer args 불일치 — native lib 는 첫 바인드 args 에 고정. " +
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:32: *   `./gradlew :layoutlib-worker:test -PincludeTags=integration` 로 명시 실행.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:14: *   Gradle `forkEvery(1L)` 가 test class 단위 JVM 격리를 보장하므로 cross-class 는 문제
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:27: * 없음 → dead API. YAGNI 로 제거. cross-class 격리는 Gradle forkEvery(1L) 이 담당.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:29:object SharedLayoutlibRenderer
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:30:class LayoutlibRendererTier3MinimalTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:34:         * W3D2 cleanup: native lib JVM-wide single-load 이슈 (L4) 는 SharedLayoutlibRenderer
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:48:            return SharedLayoutlibRenderer.getOrCreate(
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:42:            callback = MinimalLayoutlibCallback(),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:65:            callback = MinimalLayoutlibCallback(),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:13: * W2D7-RENDERSESSION — MinimalLayoutlibCallback 의 resource id 양방향 맵 + 기본값 계약 검증.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:18:class MinimalLayoutlibCallbackTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:22:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:31:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:39:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:47:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:53:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:59:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:65:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:71:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:15: * 으로 처리했었다. W3D2 cleanup 에서 L4 는 SharedLayoutlibRenderer 로 해결됐으나 L3
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:16: * (MinimalLayoutlibCallback.loadView 가 ConstraintLayout/MaterialButton 을 reject) 는
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:20: * 호출부는 **SharedLayoutlibRenderer** 를 사용하도록 유지해, @Disabled 가 풀렸을 때
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:25:class LayoutlibRendererIntegrationTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:31:        val renderer = SharedLayoutlibRenderer.getOrCreate(
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:13: * W3D2 integration cleanup — SharedLayoutlibRenderer 의 per-JVM-fork 싱글톤 동작 검증.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:16: * Gradle `forkEvery(1L)` (`server/layoutlib-worker/build.gradle.kts:60-65`) 덕에 이 test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:22:class SharedLayoutlibRendererIntegrationTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:29:        val r = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:38:        val r1 = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:40:        val r2 = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:50:        SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:55:            SharedLayoutlibRenderer.getOrCreate(dist, differentFixture, fallback = null)

exec
/bin/bash -lc "rg -n \"layoutlib-api|layoutlib\" server/layoutlib-worker/build.gradle.kts server/build.gradle.kts server/gradle -g '*.kts'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/layoutlib-worker/build.gradle.kts:1:// :layoutlib-worker — L1 per-device 워커 프로세스 (07 §2.7 프로세스 격리).
server/layoutlib-worker/build.gradle.kts:2:// 런타임 classpath 에 layoutlib-dist/android-34 의 layoutlib JAR 을 추가해야 함.
server/layoutlib-worker/build.gradle.kts:19:    // ILayoutPullParser subclass 를 :layoutlib-worker 소스에 직접 작성하기 위함.
server/layoutlib-worker/build.gradle.kts:20:    // layoutlib 본체 (Bridge 등) 는 dist JAR 에서 reflection 으로 로드하지만,
server/layoutlib-worker/build.gradle.kts:24:    // W2D7-F1 (페어 리뷰): LayoutlibBootstrap.createIsolatedClassLoader() 에서 layoutlib-api
server/layoutlib-worker/build.gradle.kts:26:    implementation("com.android.tools.layoutlib:layoutlib-api:31.13.2")
server/layoutlib-worker/build.gradle.kts:28:    // layoutlib-dist 의 JAR 은 런타임에 -cp 로 주입. compileOnly 로도 두지 않음
server/layoutlib-worker/build.gradle.kts:31:    // W2D6-FATJAR (08 §7.7): layoutlib 14.x 의 런타임 transitive 의존을 worker 의 runtime
server/layoutlib-worker/build.gradle.kts:33:    // layoutlib 본체 JAR 은 disk 번들에서 reflection 으로 로드 — 여기 선언된 deps 는 Bridge
server/layoutlib-worker/build.gradle.kts:37:    // 아래 3개 좌표/버전은 layoutlib 14.0.11 의 transitive 실 해석을 대체하기 위해 W2D6 범위
server/layoutlib-worker/build.gradle.kts:38:    // 내에서 임시 pin. 올바른 canonical 은 `com.android.tools.layoutlib:layoutlib:14.0.11` 의
server/layoutlib-worker/build.gradle.kts:41:    // 때문이며, layoutlib 업그레이드 시 실패 가능.
server/layoutlib-worker/build.gradle.kts:43:    // W2D7-RENDERSESSION: kxml2 는 layoutlib transitive 로 runtime 필요이면서, 동시에
server/layoutlib-worker/build.gradle.kts:51:    mainClass.set("dev.axp.layoutlib.worker.MainKt")
server/layoutlib-worker/build.gradle.kts:52:    applicationName = "axp-layoutlib-worker"

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/build.gradle.kts | sed -n '1,130p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	// :layoutlib-worker — L1 per-device 워커 프로세스 (07 §2.7 프로세스 격리).
     2	// 런타임 classpath 에 layoutlib-dist/android-34 의 layoutlib JAR 을 추가해야 함.
     3	// 빌드 타임에 JAR 이 없어도 컴파일 가능하도록 compileOnly + classpath 는 런처가 주입.
     4	
     5	plugins {
     6	    id("axp.kotlin-common")
     7	    id("org.jetbrains.kotlin.plugin.serialization")
     8	    application
     9	}
    10	
    11	dependencies {
    12	    implementation(project(":protocol"))
    13	    implementation(project(":render-core"))
    14	    implementation(libs.kotlinx.coroutines.core)
    15	    implementation(libs.kotlinx.serialization.json)
    16	    implementation(libs.logback.classic)
    17	
    18	    // W2D7-RENDERSESSION (08 §7.7.1 item 3b): LayoutlibCallback / AssetRepository /
    19	    // ILayoutPullParser subclass 를 :layoutlib-worker 소스에 직접 작성하기 위함.
    20	    // layoutlib 본체 (Bridge 등) 는 dist JAR 에서 reflection 으로 로드하지만,
    21	    // public API surface (`com.android.ide.common.rendering.api.*`) 는 system CL
    22	    // 에 존재해야 isolated URLClassLoader 의 parent-first delegation 으로 하나의 Class
    23	    // 객체로 결합된다 — Bridge 가 기대하는 subclass 타입 identity 일치.
    24	    // W2D7-F1 (페어 리뷰): LayoutlibBootstrap.createIsolatedClassLoader() 에서 layoutlib-api
    25	    // URL 은 제거하여 single source of truth 유지.
    26	    implementation("com.android.tools.layoutlib:layoutlib-api:31.13.2")
    27	
    28	    // layoutlib-dist 의 JAR 은 런타임에 -cp 로 주입. compileOnly 로도 두지 않음
    29	    // (reflection-only 접근 — 07 §2.3).
    30	
    31	    // W2D6-FATJAR (08 §7.7): layoutlib 14.x 의 런타임 transitive 의존을 worker 의 runtime
    32	    // classpath 에 두어 BridgeInitIntegrationTest Tier2 (실제 Bridge.init 호출) 가 통과하게 함.
    33	    // layoutlib 본체 JAR 은 disk 번들에서 reflection 으로 로드 — 여기 선언된 deps 는 Bridge
    34	    // 내부가 import 하는 클래스들이다.
    35	    //
    36	    // 08 §7.7.1 F-6 marker — post-W2D6 pom-resolved refactor candidate:
    37	    // 아래 3개 좌표/버전은 layoutlib 14.0.11 의 transitive 실 해석을 대체하기 위해 W2D6 범위
    38	    // 내에서 임시 pin. 올바른 canonical 은 `com.android.tools.layoutlib:layoutlib:14.0.11` 의
    39	    // pom 을 resolve 하여 그 runtime 좌표/버전을 그대로 채택하는 것. task POST-W2D6-POM-RESOLVE
    40	    // 에서 교체 예정. 본 pin 이 tests 를 통과시키는 것은 Guava API 호환범위에 우연히 들어왔기
    41	    // 때문이며, layoutlib 업그레이드 시 실패 가능.
    42	    runtimeOnly("com.google.guava:guava:32.1.3-jre")
    43	    // W2D7-RENDERSESSION: kxml2 는 layoutlib transitive 로 runtime 필요이면서, 동시에
    44	    // `org.xmlpull.v1.XmlPullParser` (ILayoutPullParser 의 parent) 를 우리 session 어댑터가
    45	    // 컴파일 타임에 참조하므로 implementation 으로 승격.
    46	    implementation("net.sf.kxml:kxml2:2.3.0")
    47	    runtimeOnly("com.ibm.icu:icu4j:73.2")
    48	}
    49	
    50	application {
    51	    mainClass.set("dev.axp.layoutlib.worker.MainKt")
    52	    applicationName = "axp-layoutlib-worker"
    53	}
    54	
    55	// W2D7-RENDERSESSION: Bridge 가 process-global static state (sInit, sRMap, sNativeCrash) 를 들고
    56	// 있어 여러 integration test 가 같은 JVM 을 공유하면 "이미 초기화됨" 이 테스트 간에 전파된다.
    57	// Tier2 (best-effort Bridge.init) 가 "초기 init 호출" 을 전제로 true 를 기대하므로, 테스트 클래스
    58	// 단위로 JVM 을 fork 해서 각 integration test 가 독립적으로 Bridge 상태를 구성하도록 강제.
    59	// unit test (no integration tag) 는 forkEvery 비활성 — 빠른 실행 유지.
    60	tasks.named<Test>("test") {
    61	    val includeTagsProp = providers.gradleProperty("includeTags").orNull
    62	    if (!includeTagsProp.isNullOrBlank() && includeTagsProp.contains("integration")) {
    63	        setForkEvery(1L)
    64	    }
    65	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import org.junit.jupiter.api.Assertions.assertTrue
     4	import org.junit.jupiter.api.Assumptions.assumeTrue
     5	import org.junit.jupiter.api.Disabled
     6	import org.junit.jupiter.api.Tag
     7	import org.junit.jupiter.api.Test
     8	import java.nio.file.Path
     9	
    10	/**
    11	 * Tier3 pre-canonical integration test — activity_basic.xml (ConstraintLayout / MaterialButton 포함)
    12	 * 의 full render 를 기대.
    13	 *
    14	 * W2D6 시점에는 `assumeTrue(false)` 로 L4 masking + L3 (custom view 미지원) 을 모두 skip
    15	 * 으로 처리했었다. W3D2 cleanup 에서 L4 는 SharedLayoutlibRenderer 로 해결됐으나 L3
    16	 * (MinimalLayoutlibCallback.loadView 가 ConstraintLayout/MaterialButton 을 reject) 는
    17	 * 여전히 남아있어 render 실패가 예측된다. 따라서 `@Disabled` annotation 으로 명시적
    18	 * skip — W3 sample-app unblock (DexClassLoader) 이후 enable 예정.
    19	 *
    20	 * 호출부는 **SharedLayoutlibRenderer** 를 사용하도록 유지해, @Disabled 가 풀렸을 때
    21	 * L4 regression 재발하지 않도록 설계.
    22	 */
    23	@Tag("integration")
    24	@Disabled("W3 sample-app unblock 필요 — L3 DexClassLoader carry (ConstraintLayout / MaterialButton)")
    25	class LayoutlibRendererIntegrationTest {
    26	
    27	    @Test
    28	    fun `tier3 — renderPng returns non-empty PNG bytes with PNG magic header`() {
    29	        val dist = locateDistDir()
    30	        val fixture = locateFixtureRoot()
    31	        val renderer = SharedLayoutlibRenderer.getOrCreate(
    32	            distDir = dist,
    33	            fixtureRoot = fixture,
    34	            fallback = null,
    35	        )
    36	        val bytes = renderer.renderPng("activity_basic.xml")
    37	
    38	        assertTrue(bytes.size > 1000, "PNG bytes 가 placeholder 보다 큰 실 이미지여야 함: ${bytes.size}")
    39	        // PNG magic: 0x89 0x50 0x4E 0x47
    40	        assertTrue(
    41	            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
    42	                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
    43	            "PNG magic 헤더가 아님",
    44	        )
    45	    }
    46	
    47	    private fun locateDistDir(): Path {
    48	        val found = DistDiscovery.locate(null)
    49	        assumeTrue(found != null, "dist 없음 — W1D3-R2 다운로드를 먼저 수행")
    50	        return found!!.toAbsolutePath().normalize()
    51	    }
    52	
    53	    private fun locateFixtureRoot(): Path {
    54	        val found = FixtureDiscovery.locate(null)
    55	        assumeTrue(found != null, "fixture 없음 — fixture/sample-app 확인")
    56	        return found!!.toAbsolutePath().normalize()
    57	    }
    58	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import com.android.ide.common.rendering.api.Result
     4	import dev.axp.layoutlib.worker.session.SessionConstants
     5	import org.junit.jupiter.api.Assertions.assertEquals
     6	import org.junit.jupiter.api.Assertions.assertNotNull
     7	import org.junit.jupiter.api.Assertions.assertTrue
     8	import org.junit.jupiter.api.Disabled
     9	import org.junit.jupiter.api.Tag
    10	import org.junit.jupiter.api.Test
    11	import java.io.ByteArrayInputStream
    12	import javax.imageio.ImageIO
    13	
    14	/**
    15	 * W2D7-RENDERSESSION — Tier3 "아키텍처 positive evidence" + W3 carry placeholder.
    16	 *
    17	 * **§7.7.1 item 3b 의 canonical split**:
    18	 *   - **3b-arch (W2D7 closure)**: `Bridge.createSession(SessionParams)` 가 실제로 실행되어
    19	 *     layoutlib 의 inflate 단계 (`Layout.<init>` → `FrameLayout.<init>` → `ViewConfiguration.<init>`)
    20	 *     까지 도달한다. 이 경로가 연결되었다는 사실은 SessionParams / HardwareConfig / LayoutPullParser
    21	 *     / LayoutlibCallback 인프라가 올바르게 조립되었다는 positive evidence.
    22	 *   - **3b-values (W3 carry)**: 프레임워크 리소스 VALUE (`config_scrollbarSize` 등 data/res/values
    23	 *     내 XML 의 실제 값) 를 `RenderResources` 에 제공하는 풀 resource parsing (Paparazzi 급 infra ~1000 LOC).
    24	 *     이 작업이 완료되어야 실 pixel 렌더가 가능.
    25	 *
    26	 * 따라서 본 세션의 Tier3 는 **architecture evidence 만** assert. 실 pixel 테스트는 `@Disabled` 로
    27	 * 남겨두고 W3 에서 unblock.
    28	 */
    29	@Tag("integration")
    30	class LayoutlibRendererTier3MinimalTest {
    31	
    32	    companion object {
    33	        /**
    34	         * W3D2 cleanup: native lib JVM-wide single-load 이슈 (L4) 는 SharedLayoutlibRenderer
    35	         * 로 해결. 기존 `sharedRenderer` 필드 + synchronized factory 는 그 object 로 이동.
    36	         */
    37	        private fun renderer(): LayoutlibRenderer {
    38	            val dist = DistDiscovery.locate(null)
    39	                ?: run {
    40	                    // CI 환경 변수에 dist 없을 수 있음 — assumeTrue 로 skip.
    41	                    org.junit.jupiter.api.Assumptions.assumeTrue(
    42	                        false, "dist 없음 — W1D3-R2 다운로드를 먼저 수행",
    43	                    )
    44	                    error("unreachable")
    45	                }
    46	            val fixture = FixtureDiscovery.locate(null)
    47	                ?: error("fixture 없음 — fixture/sample-app 확인")
    48	            return SharedLayoutlibRenderer.getOrCreate(
    49	                distDir = dist.toAbsolutePath().normalize(),
    50	                fixtureRoot = fixture.toAbsolutePath().normalize(),
    51	                fallback = null,
    52	            )
    53	        }
    54	    }
    55	
    56	    @Test
    57	    fun `tier3-arch — createSession reaches inflate phase on activity_minimal`() {
    58	        val renderer = renderer()
    59	
    60	        // 3b-values 완료: renderPng 은 성공적으로 PNG 반환. 예외는 곧 regression.
    61	        val bytes = renderer.renderPng("activity_minimal.xml")
    62	        assertTrue(bytes.isNotEmpty(), "3b-values: PNG bytes 는 non-empty")
    63	
    64	        // ===== architecture positive evidence =====
    65	        val result = renderer.lastSessionResult
    66	        assertNotNull(
    67	            result,
    68	            "createSession 이 호출되어 result 가 채워져야 함 — null 이면 SessionParams 빌드 전 실패."
    69	        )
    70	
    71	        // W3D1 impl-pair-review MF1 (Codex F2 + Claude F1 converged):
    72	        // rejectedStatuses 는 SUCCESS-only 와 중복이라 제거. Result.Status 의 8+ 개 non-success enum
    73	        // (NOT_IMPLEMENTED / ERROR_TIMEOUT / ERROR_LOCK_INTERRUPTED / ERROR_VIEWGROUP_NO_CHILDREN /
    74	        //  ERROR_ANIM_NOT_FOUND / ERROR_NOT_A_DRAWABLE / ERROR_REFLECTION / ERROR_RENDER_TASK 등)
    75	        // 을 일일이 나열하기보다 "SUCCESS 만 허용" 한 assertion 으로 모두 거부.
    76	        val status = result!!.status
    77	        val msg = result.errorMessage.orEmpty()
    78	        val exc = result.exception?.javaClass?.simpleName
    79	        assertEquals(
    80	            Result.Status.SUCCESS, status,
    81	            "3b-values 완료 canonical: SUCCESS 만 허용.\n" +
    82	                "  actual=$status msg=$msg exc=$exc\n" +
    83	                "  ERROR_INFLATION    → 3b-values regression (framework VALUE loader 확인).\n" +
    84	                "  ERROR_RENDER       → draw-phase regression (W4 carry 범위 침범).\n" +
    85	                "  ERROR_UNKNOWN      → native/JNI 단계 실패 (native lib wiring 확인).\n" +
    86	                "  ERROR_NOT_INFLATED → SessionParams 빌드 결함 (W2D7 regression).\n"
    87	        )
    88	    }
    89	
    90	    /**
    91	     * W3D1 3b-values — T1 gate: SUCCESS + PNG > 1000 bytes. glyph 렌더는 tier3-glyph 로 분리.
    92	     */
    93	    @Test
    94	    fun `tier3-values — activity_minimal 이 SUCCESS + valid PNG 반환`() {
    95	        val renderer = renderer()
    96	        val bytes = renderer.renderPng("activity_minimal.xml")
    97	
    98	        assertTrue(bytes.size > 1000, "PNG size > 1000 bytes: actual=${bytes.size}")
    99	        val img = ImageIO.read(ByteArrayInputStream(bytes))
   100	        assertNotNull(img)
   101	        assertEquals(SessionConstants.RENDER_WIDTH_PX, img!!.width)
   102	        assertEquals(SessionConstants.RENDER_HEIGHT_PX, img.height)
   103	
   104	        val result = renderer.lastSessionResult
   105	        assertNotNull(result)
   106	        assertEquals(
   107	            Result.Status.SUCCESS, result!!.status,
   108	            "tier3-values T1 gate: SUCCESS 필요. actual=${result.status} msg=${result.errorMessage}"
   109	        )
   110	    }
   111	
   112	    /**
   113	     * tier3-glyph (W4+ carry) — 실 글리프 렌더 증명.
   114	     * Font wiring + StaticLayout + Canvas.drawText JNI 전 영역 검증.
   115	     * T1 gate 와 분리 (3b-values 완료는 이 테스트 unblock 의 전제).
   116	     */
   117	    @Test
   118	    @Disabled("tier3-glyph W4 carry — Font wiring + glyph 렌더링 검증 (T2 gate)")
   119	    fun `tier3-glyph — activity_minimal 의 TextView 영역에 실 dark pixel`() {
   120	        val renderer = renderer()
   121	        val bytes = renderer.renderPng("activity_minimal.xml")
   122	
   123	        assertTrue(bytes.size >= 10_000, "PNG size >= 10_000: actual=${bytes.size}")
   124	        val img = ImageIO.read(ByteArrayInputStream(bytes))!!
   125	        assertEquals(SessionConstants.RENDER_WIDTH_PX, img.width)
   126	        assertEquals(SessionConstants.RENDER_HEIGHT_PX, img.height)
   127	
   128	        val textRectX = 64..600
   129	        val textRectY = 64..200
   130	        var dark = 0
   131	        for (y in textRectY step 2) for (x in textRectX step 2) {
   132	            if (x >= img.width || y >= img.height) continue
   133	            val rgb = img.getRGB(x, y)
   134	            val r = (rgb shr 16) and 0xFF
   135	            val g = (rgb shr 8) and 0xFF
   136	            val b = rgb and 0xFF
   137	            if (r + g + b < 384) dark++
   138	        }
   139	        assertTrue(dark >= 20, "TextView 영역 dark pixels >= 20: actual=$dark")
   140	    }
   141	
   142	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt | sed -n '1,140p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import dev.axp.protocol.render.PngRenderer
     4	import java.nio.file.Path
     5	
     6	/**
     7	 * W3D2 integration cleanup (Codex F4 carry) — test-only per-JVM-fork LayoutlibRenderer singleton.
     8	 *
     9	 * **왜 필요한가 (W2D7 L4, in-class scope)**:
    10	 *   `System.load(libandroid_runtime.so)` 는 프로세스당 1회만 허용. 동일 test class 의
    11	 *   여러 @Test 메서드 가 각자 별도 `LayoutlibRenderer` 를 만들면 두 번째부터
    12	 *   `UnsatisfiedLinkError` 가 발생 → LayoutlibBootstrap 이 catch 해서 조용히 삼킴 →
    13	 *   Bridge 의 native 바인딩이 깨진 상태로 render 시도 → 진단 어려운 null 반환.
    14	 *   Gradle `forkEvery(1L)` 가 test class 단위 JVM 격리를 보장하므로 cross-class 는 문제
    15	 *   아니나, in-class 는 여전히 이 singleton 으로 방어 필요.
    16	 *
    17	 * **해결**:
    18	 *   - JVM fork 내에서 유일한 instance.
    19	 *   - bound 된 dist/fixture 와 다른 args 로 호출 시 `IllegalStateException`.
    20	 *
    21	 * **Production 과의 관계**:
    22	 *   Production (`Main.kt.chooseRenderer`) 은 mode 당 1회 `LayoutlibRenderer` 를 생성
    23	 *   하므로 이 singleton 을 사용하지 않는다. 이 object 는 test sourceset 에만 존재.
    24	 *
    25	 * **resetForTestOnly() 미포함 (MF-F1)**: 초기 설계에 있었으나 native lib 는 `System.load`
    26	 * 이후 JVM 종료까지 unload 불가이므로 instance 를 null 로 만들어도 실질적 isolation 효과
    27	 * 없음 → dead API. YAGNI 로 제거. cross-class 격리는 Gradle forkEvery(1L) 이 담당.
    28	 */
    29	object SharedLayoutlibRenderer
    30	{
    31	    @Volatile private var instance: LayoutlibRenderer? = null
    32	    @Volatile private var boundArgs: Pair<Path, Path>? = null
    33	
    34	    @Synchronized
    35	    fun getOrCreate(
    36	        distDir: Path,
    37	        fixtureRoot: Path,
    38	        fallback: PngRenderer?,
    39	    ): LayoutlibRenderer
    40	    {
    41	        val requested = distDir to fixtureRoot
    42	        SharedRendererBinding.verify(boundArgs, requested)
    43	        instance?.let { return it }
    44	        val created = LayoutlibRenderer(distDir, fallback, fixtureRoot)
    45	        instance = created
    46	        boundArgs = requested
    47	        return created
    48	    }
    49	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import java.nio.file.Files
     4	import java.nio.file.Path
     5	import java.nio.file.Paths
     6	
     7	/**
     8	 * W3D2 integration cleanup (Codex F3 carry) — fixture root (XML 레이아웃 루트) 탐지.
     9	 *
    10	 * override 제공 시 명시 경로 검증 후 사용, 없으면 CANDIDATE_ROOTS × FIXTURE_SUBPATH
    11	 * 순차 탐색. 모두 실패 시 null.
    12	 *
    13	 * 이전에는 LayoutlibRenderer.companion.defaultFixtureRoot() 에 있었으나, CLI 와
    14	 * 테스트 양쪽에서 사용하도록 독립 object 로 추출.
    15	 */
    16	object FixtureDiscovery {
    17	    const val FIXTURE_SUBPATH = "fixture/sample-app/app/src/main/res/layout"
    18	
    19	    private const val CANDIDATE_ROOT_CWD = ""
    20	    private const val CANDIDATE_ROOT_PARENT = ".."
    21	    private const val CANDIDATE_ROOT_GRANDPARENT = "../.."
    22	
    23	    val CANDIDATE_ROOTS: List<String> = listOf(
    24	        CANDIDATE_ROOT_CWD,
    25	        CANDIDATE_ROOT_PARENT,
    26	        CANDIDATE_ROOT_GRANDPARENT,
    27	    )
    28	
    29	    fun locate(override: Path?): Path? = locateInternal(
    30	        override = override,
    31	        userDir = System.getProperty("user.dir"),
    32	        candidateRoots = CANDIDATE_ROOTS,
    33	    )
    34	
    35	    internal fun locateInternal(
    36	        override: Path?,
    37	        userDir: String,
    38	        candidateRoots: List<String>,
    39	    ): Path? {
    40	        if (override != null) {
    41	            require(Files.isDirectory(override)) {
    42	                "fixture root 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
    43	            }
    44	            return override
    45	        }
    46	        val candidates: List<Path> = candidateRoots.flatMap { root ->
    47	            if (root.isEmpty())
    48	            {
    49	                listOf(
    50	                    Paths.get(FIXTURE_SUBPATH),
    51	                    Paths.get(userDir, FIXTURE_SUBPATH),
    52	                )
    53	            }
    54	            else
    55	            {
    56	                listOf(
    57	                    Paths.get(root, FIXTURE_SUBPATH),
    58	                    Paths.get(userDir, root, FIXTURE_SUBPATH),
    59	                )
    60	            }
    61	        }
    62	        return candidates.firstOrNull { Files.isDirectory(it) }
    63	    }
    64	}

exec
/bin/bash -lc "rg -n \"fixtureRoot|locateFixtureRoot|FixtureDiscovery|renderPng\\(\" server -g '*.kt'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/protocol/src/main/kotlin/dev/axp/protocol/render/PngRenderer.kt:11:    fun renderPng(layoutName: String): ByteArray
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt:16:object FixtureDiscovery {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:47:    private val fixtureRoot: Path,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:82:    override fun renderPng(layoutName: String): ByteArray {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:87:            ?: (fallback?.renderPng(layoutName)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:157:        val layoutPath = fixtureRoot.resolve(layoutName)
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:7:import dev.axp.layoutlib.worker.FixtureDiscovery
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:32: *   --fixture-root=<path> : XML fixture 루트. 없으면 FixtureDiscovery.locate(null).
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:100:    val bytes = renderer.renderPng(layoutName)
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:120:    val fixture = FixtureDiscovery.locate(fixtureOverride)
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:133:            fixtureRoot = fixture.toAbsolutePath().normalize(),
server/http-server/src/test/kotlin/dev/axp/http/PlaceholderPngRendererTest.kt:16:        val bytes = renderer.renderPng("activity_basic.xml")
server/http-server/src/test/kotlin/dev/axp/http/PlaceholderPngRendererTest.kt:27:        val bytes = renderer.renderPng("activity_custom_view.xml")
server/http-server/src/test/kotlin/dev/axp/http/PlaceholderPngRendererTest.kt:35:        val bytes = renderer.renderPng("activity_basic.xml")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:46:            val fixture = FixtureDiscovery.locate(null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:50:                fixtureRoot = fixture.toAbsolutePath().normalize(),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:61:        val bytes = renderer.renderPng("activity_minimal.xml")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:96:        val bytes = renderer.renderPng("activity_minimal.xml")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:121:        val bytes = renderer.renderPng("activity_minimal.xml")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:37:        fixtureRoot: Path,
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:41:        val requested = distDir to fixtureRoot
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:44:        val created = LayoutlibRenderer(distDir, fallback, fixtureRoot)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:12:class FixtureDiscoveryTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:18:        val found = FixtureDiscovery.locate(customFixture)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:28:            FixtureDiscovery.locate(nonexistent)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:39:        val target = tempDir.resolve(tSyntheticRoot).resolve(FixtureDiscovery.FIXTURE_SUBPATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:42:        val found = FixtureDiscovery.locateInternal(
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:55:        val found = FixtureDiscovery.locateInternal(
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:66:        val target = tempDir.resolve(FixtureDiscovery.FIXTURE_SUBPATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:69:        val found = FixtureDiscovery.locateInternal(
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:27:        val fixture = locateFixtureRoot()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:37:        val fixture = locateFixtureRoot()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:48:        val fixture = locateFixtureRoot()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:69:    private fun locateFixtureRoot(): Path {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:70:        val found = FixtureDiscovery.locate(null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:30:        val fixture = locateFixtureRoot()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:33:            fixtureRoot = fixture,
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:36:        val bytes = renderer.renderPng("activity_basic.xml")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:53:    private fun locateFixtureRoot(): Path {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:54:        val found = FixtureDiscovery.locate(null)
server/http-server/src/main/kotlin/dev/axp/http/PreviewRoutes.kt:67:            val pngBytes = pngRenderer.renderPng(layout)
server/http-server/src/main/kotlin/dev/axp/http/PreviewRoutes.kt:115:            pngRenderer.renderPng(layout)
server/http-server/src/main/kotlin/dev/axp/http/PlaceholderPng.kt:54:    override fun renderPng(layoutName: String): ByteArray {

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/Main.kt | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import dev.axp.protocol.Versions
     4	import dev.axp.protocol.worker.WorkerFrame
     5	
     6	/**
     7	 * L1 워커 프로세스 엔트리 (07 §2.7 프로세스 격리).
     8	 *
     9	 * 기동 인자:
    10	 *   --device=<id>         디바이스 ID (phone_normal 등). 워커 인스턴스 식별.
    11	 *   --socket=<path>       Unix socket 파일 경로 (부모 프로세스가 생성 후 전달).
    12	 *   --layoutlib-cp=<jar>  layoutlib-dist 디렉토리. 런타임에 URLClassLoader 로 로드.
    13	 *
    14	 * Week 1 Day 1 은 idle 상태에서 Pong 만 응답하는 최소 스켈레톤. 실제 render 루프는 Week 2-3.
    15	 */
    16	fun main(args: Array<String>) {
    17	    val device = args.firstOrNull { it.startsWith("--device=") }?.removePrefix("--device=")
    18	    val socket = args.firstOrNull { it.startsWith("--socket=") }?.removePrefix("--socket=")
    19	
    20	    System.err.println(
    21	        "axp-layoutlib-worker v${Versions.WORKER_VERSION}" +
    22	            " device=${device ?: "(unset)"}" +
    23	            " socket=${socket ?: "(unset)"}" +
    24	            " frameHeaderBytes=${WorkerFrame.HEADER_BYTES}" +
    25	            " maxFrame=${WorkerFrame.MAX_FRAME_SIZE}"
    26	    )
    27	
    28	    // TODO(W2D7): Unix socket accept 루프 + frame decode + WorkerRequest 처리
    29	    //             (Ping → Pong, Render → LayoutlibSessionPool.render, …)
    30	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import org.junit.jupiter.api.Assertions.assertEquals
     4	import org.junit.jupiter.api.Assertions.assertNull
     5	import org.junit.jupiter.api.Assertions.assertTrue
     6	import org.junit.jupiter.api.Test
     7	import org.junit.jupiter.api.assertThrows
     8	import org.junit.jupiter.api.io.TempDir
     9	import java.nio.file.Files
    10	import java.nio.file.Path
    11	
    12	class FixtureDiscoveryTest {
    13	
    14	    @Test
    15	    fun `override 가 존재 디렉토리이면 해당 경로 반환`(@TempDir tempDir: Path) {
    16	        val customFixture = tempDir.resolve("custom-fixture").also { Files.createDirectory(it) }
    17	
    18	        val found = FixtureDiscovery.locate(customFixture)
    19	
    20	        assertEquals(customFixture, found)
    21	    }
    22	
    23	    @Test
    24	    fun `override 경로가 비존재면 IllegalArgumentException`(@TempDir tempDir: Path) {
    25	        val nonexistent = tempDir.resolve("missing-fixture")
    26	
    27	        val ex = assertThrows<IllegalArgumentException> {
    28	            FixtureDiscovery.locate(nonexistent)
    29	        }
    30	        assertTrue(
    31	            ex.message!!.contains(nonexistent.toString()),
    32	            "에러 메시지에 경로 포함 필요: ${ex.message}",
    33	        )
    34	    }
    35	
    36	    @Test
    37	    fun `override null + candidate match 시 해당 경로 반환`(@TempDir tempDir: Path) {
    38	        val tSyntheticRoot = "myroot"
    39	        val target = tempDir.resolve(tSyntheticRoot).resolve(FixtureDiscovery.FIXTURE_SUBPATH)
    40	        Files.createDirectories(target)
    41	
    42	        val found = FixtureDiscovery.locateInternal(
    43	            override = null,
    44	            userDir = tempDir.toString(),
    45	            candidateRoots = listOf(tSyntheticRoot),
    46	        )
    47	
    48	        assertEquals(target, found)
    49	    }
    50	
    51	    @Test
    52	    fun `override null + 모든 candidate 실패 시 null 반환`(@TempDir tempDir: Path) {
    53	        val tMissingRoot1 = "no-such"
    54	        val tMissingRoot2 = "not-here"
    55	        val found = FixtureDiscovery.locateInternal(
    56	            override = null,
    57	            userDir = tempDir.toString(),
    58	            candidateRoots = listOf(tMissingRoot1, tMissingRoot2),
    59	        )
    60	
    61	        assertNull(found)
    62	    }
    63	
    64	    @Test
    65	    fun `empty root candidate (CWD) 가 userDir 기준으로 FIXTURE_SUBPATH 해석`(@TempDir tempDir: Path) {
    66	        val target = tempDir.resolve(FixtureDiscovery.FIXTURE_SUBPATH)
    67	        Files.createDirectories(target)
    68	
    69	        val found = FixtureDiscovery.locateInternal(
    70	            override = null,
    71	            userDir = tempDir.toString(),
    72	            candidateRoots = listOf(""),
    73	        )
    74	
    75	        assertEquals(target, found)
    76	    }
    77	}

codex
One concrete mismatch already surfaced: the existing `fixtureRoot` is the XML layout directory, not the sample-app project root that the proposed classloader code expects. I’m keeping that as a candidate blocker while I verify the artifact assumptions and callback API.
Source inspection found a path-contract mismatch around `fixtureRoot`; now I’m running the concrete local checks requested in section D.
  ✓ Read spec and canonical context with line numbers
  ✓ Inspect current layoutlib worker source and Gradle fixture
  → Verify build artifacts, AAR contents, dependencies, and layoutlib-api surface
  • Assess blockers, follow-ups, explicit Q1-Q5, independent issues, verdict
exec
/bin/bash -lc 'ls -l fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
-rw-rw-r-- 1 bh-mark-dev-desktop bh-mark-dev-desktop 839012 Apr 23 13:24 fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar

exec
/bin/bash -lc "find ~/.gradle/caches/modules-2/files-2.1 -path '*constraintlayout*2.1.4*.aar' -o -path '*material*1.12.0*.aar' -o -path '*appcompat*1.6.1*.aar' | head -20" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.google.android.material/material/1.12.0/e493b7ac7fc4b8ca48527e0199f9961652711ac3/material-1.12.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.constraintlayout/constraintlayout/2.1.4/b7ad5eca28aa9e7c4ea1a7e256c7ee4401a031f/constraintlayout-2.1.4.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.appcompat/appcompat/1.6.1/6c7577004b7ebbee5ed87d512b578dd20e3c8c31/appcompat-1.6.1.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.appcompat/appcompat-resources/1.6.1/3fe43025e50b556f319b1ff82a730d5a376a31e/appcompat-resources-1.6.1.aar

exec
/bin/bash -lc "find ~/.gradle/caches/modules-2/files-2.1/com/android/tools/layoutlib/layoutlib-api/31.13.2 -type f -name '*.jar' -print" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 exited 1 in 0ms:
find: ‘/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com/android/tools/layoutlib/layoutlib-api/31.13.2’: No such file or directory

exec
/bin/bash -lc "find fixture/sample-app/app/build/intermediates -name 'R.jar' -print | sort" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar

exec
/bin/bash -lc "unzip -l /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.constraintlayout/constraintlayout/2.1.4/b7ad5eca28aa9e7c4ea1a7e256c7ee4401a031f/constraintlayout-2.1.4.aar | sed -n '1,80p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Archive:  /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.constraintlayout/constraintlayout/2.1.4/b7ad5eca28aa9e7c4ea1a7e256c7ee4401a031f/constraintlayout-2.1.4.aar
  Length      Date    Time    Name
---------  ---------- -----   ----
   136944  1980-02-01 00:00   R.txt
      281  1980-02-01 00:00   AndroidManifest.xml
   473442  1980-02-01 00:00   classes.jar
        0  1980-02-01 00:00   res/
        0  1980-02-01 00:00   res/values/
    69479  1980-02-01 00:00   res/values/values.xml
       44  1980-02-01 00:00   META-INF/com/android/build/gradle/aar-metadata.properties
        0  1980-02-01 00:00   values/
---------                     -------
   680190                     8 files

exec
/bin/bash -lc "find ~/.gradle/caches/modules-2/files-2.1 -path '*layoutlib-api*31.13.2*.jar' -print" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar

exec
/bin/bash -lc "unzip -l /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.google.android.material/material/1.12.0/e493b7ac7fc4b8ca48527e0199f9961652711ac3/material-1.12.0.aar | sed -n '1,80p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Archive:  /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.google.android.material/material/1.12.0/e493b7ac7fc4b8ca48527e0199f9961652711ac3/material-1.12.0.aar
  Length      Date    Time    Name
---------  ---------- -----   ----
   320598  1980-02-01 00:00   R.txt
      862  1980-02-01 00:00   AndroidManifest.xml
    41320  1980-02-01 00:00   public.txt
    17283  1980-02-01 00:00   annotations.zip
  1872366  1980-02-01 00:00   classes.jar
     2019  1980-02-01 00:00   proguard.txt
        0  1980-02-01 00:00   res/
        0  1980-02-01 00:00   res/anim/
     1028  1980-02-01 00:00   res/anim/design_bottom_sheet_slide_in.xml
     1017  1980-02-01 00:00   res/anim/design_bottom_sheet_slide_out.xml
      802  1980-02-01 00:00   res/anim/design_snackbar_in.xml
      802  1980-02-01 00:00   res/anim/design_snackbar_out.xml
      940  1980-02-01 00:00   res/anim/linear_indeterminate_line1_head_interpolator.xml
      951  1980-02-01 00:00   res/anim/linear_indeterminate_line1_tail_interpolator.xml
      941  1980-02-01 00:00   res/anim/linear_indeterminate_line2_head_interpolator.xml
      941  1980-02-01 00:00   res/anim/linear_indeterminate_line2_tail_interpolator.xml
     1028  1980-02-01 00:00   res/anim/m3_bottom_sheet_slide_in.xml
     1156  1980-02-01 00:00   res/anim/m3_bottom_sheet_slide_out.xml
     1238  1980-02-01 00:00   res/anim/m3_motion_fade_enter.xml
     1055  1980-02-01 00:00   res/anim/m3_motion_fade_exit.xml
      922  1980-02-01 00:00   res/anim/m3_side_sheet_enter_from_left.xml
      921  1980-02-01 00:00   res/anim/m3_side_sheet_enter_from_right.xml
      911  1980-02-01 00:00   res/anim/m3_side_sheet_exit_to_left.xml
      909  1980-02-01 00:00   res/anim/m3_side_sheet_exit_to_right.xml
      995  1980-02-01 00:00   res/anim/mtrl_bottom_sheet_slide_in.xml
      984  1980-02-01 00:00   res/anim/mtrl_bottom_sheet_slide_out.xml
      855  1980-02-01 00:00   res/anim/mtrl_card_lowers_interpolator.xml
        0  1980-02-01 00:00   res/anim-v21/
     1020  1980-02-01 00:00   res/anim-v21/design_bottom_sheet_slide_in.xml
     1018  1980-02-01 00:00   res/anim-v21/design_bottom_sheet_slide_out.xml
     1028  1980-02-01 00:00   res/anim-v21/m3_bottom_sheet_slide_in.xml
     1172  1980-02-01 00:00   res/anim-v21/m3_bottom_sheet_slide_out.xml
      918  1980-02-01 00:00   res/anim-v21/m3_side_sheet_enter_from_left.xml
      917  1980-02-01 00:00   res/anim-v21/m3_side_sheet_enter_from_right.xml
      914  1980-02-01 00:00   res/anim-v21/m3_side_sheet_exit_to_left.xml
      917  1980-02-01 00:00   res/anim-v21/m3_side_sheet_exit_to_right.xml
      987  1980-02-01 00:00   res/anim-v21/mtrl_bottom_sheet_slide_in.xml
      987  1980-02-01 00:00   res/anim-v21/mtrl_bottom_sheet_slide_out.xml
        0  1980-02-01 00:00   res/animator/
     1280  1980-02-01 00:00   res/animator/design_fab_hide_motion_spec.xml
     1278  1980-02-01 00:00   res/animator/design_fab_show_motion_spec.xml
     2545  1980-02-01 00:00   res/animator/m3_btn_elevated_btn_state_list_anim.xml
     2519  1980-02-01 00:00   res/animator/m3_btn_state_list_anim.xml
     2326  1980-02-01 00:00   res/animator/m3_card_elevated_state_list_anim.xml
     2287  1980-02-01 00:00   res/animator/m3_card_state_list_anim.xml
     2173  1980-02-01 00:00   res/animator/m3_chip_state_list_anim.xml
     2019  1980-02-01 00:00   res/animator/m3_elevated_chip_state_list_anim.xml
     1793  1980-02-01 00:00   res/animator/m3_extended_fab_change_size_collapse_motion_spec.xml
     1793  1980-02-01 00:00   res/animator/m3_extended_fab_change_size_expand_motion_spec.xml
     1344  1980-02-01 00:00   res/animator/m3_extended_fab_hide_motion_spec.xml
     1338  1980-02-01 00:00   res/animator/m3_extended_fab_show_motion_spec.xml
     3994  1980-02-01 00:00   res/animator/m3_extended_fab_state_list_animator.xml
     3619  1980-02-01 00:00   res/animator/mtrl_btn_state_list_anim.xml
      763  1980-02-01 00:00   res/animator/mtrl_btn_unelevated_state_list_anim.xml
     1684  1980-02-01 00:00   res/animator/mtrl_card_state_list_anim.xml
     1544  1980-02-01 00:00   res/animator/mtrl_chip_state_list_anim.xml
     1624  1980-02-01 00:00   res/animator/mtrl_extended_fab_change_size_collapse_motion_spec.xml
     1625  1980-02-01 00:00   res/animator/mtrl_extended_fab_change_size_expand_motion_spec.xml
      997  1980-02-01 00:00   res/animator/mtrl_extended_fab_hide_motion_spec.xml
     1283  1980-02-01 00:00   res/animator/mtrl_extended_fab_show_motion_spec.xml
     3879  1980-02-01 00:00   res/animator/mtrl_extended_fab_state_list_animator.xml
     1254  1980-02-01 00:00   res/animator/mtrl_fab_hide_motion_spec.xml
     1254  1980-02-01 00:00   res/animator/mtrl_fab_show_motion_spec.xml
     2223  1980-02-01 00:00   res/animator/mtrl_fab_transformation_sheet_collapse_spec.xml
     2220  1980-02-01 00:00   res/animator/mtrl_fab_transformation_sheet_expand_spec.xml
        0  1980-02-01 00:00   res/animator-v21/
     1579  1980-02-01 00:00   res/animator-v21/design_appbar_state_list_animator.xml
     1711  1980-02-01 00:00   res/animator-v21/m3_appbar_state_list_animator.xml
        0  1980-02-01 00:00   res/color/
     1127  1980-02-01 00:00   res/color/design_box_stroke_color.xml
      888  1980-02-01 00:00   res/color/design_error.xml
      834  1980-02-01 00:00   res/color/design_icon_tint.xml
      843  1980-02-01 00:00   res/color/m3_appbar_overlay_color.xml
      950  1980-02-01 00:00   res/color/m3_assist_chip_icon_tint_color.xml
     1236  1980-02-01 00:00   res/color/m3_assist_chip_stroke_color.xml
      823  1980-02-01 00:00   res/color/m3_bottom_sheet_drag_handle_color.xml
      940  1980-02-01 00:00   res/color/m3_button_background_color_selector.xml

exec
/bin/bash -lc "unzip -l fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar | sed -n '1,80p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Archive:  fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar
  Length      Date    Time    Name
---------  ---------- -----   ----
     2647  1981-01-01 01:01   com/google/android/material/R$anim.class
     2101  1981-01-01 01:01   com/google/android/material/R$animator.class
    50192  1981-01-01 01:01   com/google/android/material/R$attr.class
      403  1981-01-01 01:01   com/google/android/material/R$bool.class
    43867  1981-01-01 01:01   com/google/android/material/R$color.class
    48135  1981-01-01 01:01   com/google/android/material/R$dimen.class
    10012  1981-01-01 01:01   com/google/android/material/R$drawable.class
    16419  1981-01-01 01:01   com/google/android/material/R$id.class
     3893  1981-01-01 01:01   com/google/android/material/R$integer.class
     1330  1981-01-01 01:01   com/google/android/material/R$interpolator.class
     5586  1981-01-01 01:01   com/google/android/material/R$layout.class
      302  1981-01-01 01:01   com/google/android/material/R$plurals.class
     9457  1981-01-01 01:01   com/google/android/material/R$string.class
    74704  1981-01-01 01:01   com/google/android/material/R$style.class
   116243  1981-01-01 01:01   com/google/android/material/R$styleable.class
     1056  1981-01-01 01:01   com/google/android/material/R.class
     1594  1981-01-01 01:01   androidx/constraintlayout/widget/R$anim.class
    23324  1981-01-01 01:01   androidx/constraintlayout/widget/R$attr.class
      357  1981-01-01 01:01   androidx/constraintlayout/widget/R$bool.class
     4836  1981-01-01 01:01   androidx/constraintlayout/widget/R$color.class
     6867  1981-01-01 01:01   androidx/constraintlayout/widget/R$dimen.class
     5357  1981-01-01 01:01   androidx/constraintlayout/widget/R$drawable.class
     9853  1981-01-01 01:01   androidx/constraintlayout/widget/R$id.class
      516  1981-01-01 01:01   androidx/constraintlayout/widget/R$integer.class
      747  1981-01-01 01:01   androidx/constraintlayout/widget/R$interpolator.class
     2248  1981-01-01 01:01   androidx/constraintlayout/widget/R$layout.class
     1800  1981-01-01 01:01   androidx/constraintlayout/widget/R$string.class
    21818  1981-01-01 01:01   androidx/constraintlayout/widget/R$style.class
    83039  1981-01-01 01:01   androidx/constraintlayout/widget/R$styleable.class
     1002  1981-01-01 01:01   androidx/constraintlayout/widget/R.class
      291  1981-01-01 01:01   androidx/appcompat/resources/R$drawable.class
     1978  1981-01-01 01:01   androidx/appcompat/resources/R$styleable.class
      298  1981-01-01 01:01   androidx/appcompat/resources/R.class
     1566  1981-01-01 01:01   androidx/appcompat/R$anim.class
    11560  1981-01-01 01:01   androidx/appcompat/R$attr.class
      329  1981-01-01 01:01   androidx/appcompat/R$bool.class
     4568  1981-01-01 01:01   androidx/appcompat/R$color.class
     5691  1981-01-01 01:01   androidx/appcompat/R$dimen.class
     4995  1981-01-01 01:01   androidx/appcompat/R$drawable.class
     3061  1981-01-01 01:01   androidx/appcompat/R$id.class
      429  1981-01-01 01:01   androidx/appcompat/R$integer.class
      719  1981-01-01 01:01   androidx/appcompat/R$interpolator.class
     1858  1981-01-01 01:01   androidx/appcompat/R$layout.class
     1711  1981-01-01 01:01   androidx/appcompat/R$string.class
    21353  1981-01-01 01:01   androidx/appcompat/R$style.class
    26325  1981-01-01 01:01   androidx/appcompat/R$styleable.class
      806  1981-01-01 01:01   androidx/appcompat/R.class
     1186  1981-01-01 01:01   androidx/viewpager2/R$attr.class
      440  1981-01-01 01:01   androidx/viewpager2/R$color.class
     1827  1981-01-01 01:01   androidx/viewpager2/R$dimen.class
      834  1981-01-01 01:01   androidx/viewpager2/R$drawable.class
     3354  1981-01-01 01:01   androidx/viewpager2/R$id.class
      291  1981-01-01 01:01   androidx/viewpager2/R$integer.class
      592  1981-01-01 01:01   androidx/viewpager2/R$layout.class
      291  1981-01-01 01:01   androidx/viewpager2/R$string.class
      665  1981-01-01 01:01   androidx/viewpager2/R$style.class
     3584  1981-01-01 01:01   androidx/viewpager2/R$styleable.class
      663  1981-01-01 01:01   androidx/viewpager2/R.class
      277  1981-01-01 01:01   androidx/fragment/R$anim.class
      487  1981-01-01 01:01   androidx/fragment/R$animator.class
      733  1981-01-01 01:01   androidx/fragment/R$attr.class
      436  1981-01-01 01:01   androidx/fragment/R$color.class
     1485  1981-01-01 01:01   androidx/fragment/R$dimen.class
      830  1981-01-01 01:01   androidx/fragment/R$drawable.class
     3623  1981-01-01 01:01   androidx/fragment/R$id.class
      287  1981-01-01 01:01   androidx/fragment/R$integer.class
      588  1981-01-01 01:01   androidx/fragment/R$layout.class
      287  1981-01-01 01:01   androidx/fragment/R$string.class
      661  1981-01-01 01:01   androidx/fragment/R$style.class
     3022  1981-01-01 01:01   androidx/fragment/R$styleable.class
      739  1981-01-01 01:01   androidx/fragment/R.class
      320  1981-01-01 01:01   androidx/activity/R$id.class
      198  1981-01-01 01:01   androidx/activity/R.class
      149  1981-01-01 01:01   androidx/emoji2/viewsintegration/R.class
      132  1981-01-01 01:01   androidx/emoji2/R.class
      815  1981-01-01 01:01   androidx/drawerlayout/R$attr.class
      472  1981-01-01 01:01   androidx/drawerlayout/R$color.class

exec
/bin/bash -lc './gradlew :app:dependencies --configuration debugRuntimeClasspath 2>/dev/null | head -50' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app
 succeeded in 3993ms:
Starting a Gradle Daemon (subsequent builds will be faster)

> Task :app:dependencies

------------------------------------------------------------
Project ':app'
------------------------------------------------------------

debugRuntimeClasspath - Runtime classpath of /debug.
+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23
|    +--- org.jetbrains:annotations:13.0
|    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0 -> 1.8.22 (c)
|    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0 -> 1.8.22 (c)
|    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (c)
+--- androidx.appcompat:appcompat:1.6.1
|    +--- androidx.activity:activity:1.6.0 -> 1.8.0
|    |    +--- androidx.annotation:annotation:1.1.0 -> 1.6.0
|    |    |    \--- androidx.annotation:annotation-jvm:1.6.0
|    |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.0 -> 1.9.23 (*)
|    |    +--- androidx.collection:collection:1.0.0 -> 1.1.0
|    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
|    |    +--- androidx.core:core:1.8.0 -> 1.13.0
|    |    |    +--- androidx.annotation:annotation:1.6.0 (*)
|    |    |    +--- androidx.annotation:annotation-experimental:1.4.0
|    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 1.9.23 (*)
|    |    |    +--- androidx.collection:collection:1.0.0 -> 1.1.0 (*)
|    |    |    +--- androidx.concurrent:concurrent-futures:1.0.0 -> 1.1.0
|    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
|    |    |    |    \--- com.google.guava:listenablefuture:1.0
|    |    |    +--- androidx.interpolator:interpolator:1.0.0
|    |    |    |    \--- androidx.annotation:annotation:1.0.0 -> 1.6.0 (*)
|    |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.2
|    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
|    |    |    |    +--- androidx.arch.core:core-common:2.2.0
|    |    |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
|    |    |    |    +--- androidx.arch.core:core-runtime:2.2.0
|    |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
|    |    |    |    |    \--- androidx.arch.core:core-common:2.2.0 (*)
|    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.2
|    |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
|    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.9.23 (*)
|    |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4
|    |    |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
|    |    |    |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
|    |    |    |    |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
|    |    |    |    |    |    |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 (c)
|    |    |    |    |    |    |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (c)
|    |    |    |    |    |    |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4 (c)
|    |    |    |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 -> 1.8.22
|    |    |    |    |    |    |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 1.9.23 (*)

exec
/bin/bash -lc 'javap -classpath /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar -public -s com.android.ide.common.rendering.api.LayoutlibCallback' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Compiled from "LayoutlibCallback.java"
public abstract class com.android.ide.common.rendering.api.LayoutlibCallback implements com.android.ide.common.rendering.api.XmlParserFactory {
  public com.android.ide.common.rendering.api.LayoutlibCallback();
    descriptor: ()V

  public abstract java.lang.Object loadView(java.lang.String, java.lang.Class[], java.lang.Object[]) throws java.lang.Exception;
    descriptor: (Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;

  public abstract com.android.ide.common.rendering.api.ResourceReference resolveResourceId(int);
    descriptor: (I)Lcom/android/ide/common/rendering/api/ResourceReference;

  public abstract int getOrGenerateResourceId(com.android.ide.common.rendering.api.ResourceReference);
    descriptor: (Lcom/android/ide/common/rendering/api/ResourceReference;)I

  public abstract com.android.ide.common.rendering.api.ILayoutPullParser getParser(com.android.ide.common.rendering.api.ResourceValue);
    descriptor: (Lcom/android/ide/common/rendering/api/ResourceValue;)Lcom/android/ide/common/rendering/api/ILayoutPullParser;

  public java.lang.Object getAdapterItemValue(com.android.ide.common.rendering.api.ResourceReference, java.lang.Object, com.android.ide.common.rendering.api.ResourceReference, int, int, int, int, com.android.ide.common.rendering.api.ResourceReference, com.android.ide.common.rendering.api.LayoutlibCallback$ViewAttribute, java.lang.Object);
    descriptor: (Lcom/android/ide/common/rendering/api/ResourceReference;Ljava/lang/Object;Lcom/android/ide/common/rendering/api/ResourceReference;IIIILcom/android/ide/common/rendering/api/ResourceReference;Lcom/android/ide/common/rendering/api/LayoutlibCallback$ViewAttribute;Ljava/lang/Object;)Ljava/lang/Object;

  public abstract com.android.ide.common.rendering.api.AdapterBinding getAdapterBinding(java.lang.Object, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/Object;Ljava/util/Map;)Lcom/android/ide/common/rendering/api/AdapterBinding;

  public abstract com.android.ide.common.rendering.api.ActionBarCallback getActionBarCallback();
    descriptor: ()Lcom/android/ide/common/rendering/api/ActionBarCallback;

  public java.lang.Object loadClass(java.lang.String, java.lang.Class[], java.lang.Object[]) throws java.lang.ClassNotFoundException;
    descriptor: (Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;

  public java.lang.String getApplicationId();
    descriptor: ()Ljava/lang/String;

  public java.lang.String getResourcePackage();
    descriptor: ()Ljava/lang/String;

  public java.lang.Class<?> findClass(java.lang.String) throws java.lang.ClassNotFoundException;
    descriptor: (Ljava/lang/String;)Ljava/lang/Class;

  public boolean isClassLoaded(java.lang.String);
    descriptor: (Ljava/lang/String;)Z

  public com.android.ide.common.rendering.api.ResourceNamespace$Resolver getImplicitNamespaces();
    descriptor: ()Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;

  public boolean hasLegacyAppCompat();
    descriptor: ()Z

  public boolean hasAndroidXAppCompat();
    descriptor: ()Z

  public boolean isResourceNamespacingRequired();
    descriptor: ()Z

  public boolean shouldUseCustomInflater();
    descriptor: ()Z

  public void error(java.lang.String, java.lang.String...);
    descriptor: (Ljava/lang/String;[Ljava/lang/String;)V

  public void error(java.lang.String, java.lang.Throwable);
    descriptor: (Ljava/lang/String;Ljava/lang/Throwable;)V

  public void error(java.lang.Throwable);
    descriptor: (Ljava/lang/Throwable;)V

  public void warn(java.lang.String, java.lang.Throwable);
    descriptor: (Ljava/lang/String;Ljava/lang/Throwable;)V

  public void warn(java.lang.Throwable);
    descriptor: (Ljava/lang/Throwable;)V
}

exec
/bin/bash -lc "javap -classpath /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar -c -p com.android.ide.common.rendering.api.LayoutlibCallback | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Compiled from "LayoutlibCallback.java"
public abstract class com.android.ide.common.rendering.api.LayoutlibCallback implements com.android.ide.common.rendering.api.XmlParserFactory {
  public com.android.ide.common.rendering.api.LayoutlibCallback();
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return

  public abstract java.lang.Object loadView(java.lang.String, java.lang.Class[], java.lang.Object[]) throws java.lang.Exception;

  public abstract com.android.ide.common.rendering.api.ResourceReference resolveResourceId(int);

  public abstract int getOrGenerateResourceId(com.android.ide.common.rendering.api.ResourceReference);

  public abstract com.android.ide.common.rendering.api.ILayoutPullParser getParser(com.android.ide.common.rendering.api.ResourceValue);

  public java.lang.Object getAdapterItemValue(com.android.ide.common.rendering.api.ResourceReference, java.lang.Object, com.android.ide.common.rendering.api.ResourceReference, int, int, int, int, com.android.ide.common.rendering.api.ResourceReference, com.android.ide.common.rendering.api.LayoutlibCallback$ViewAttribute, java.lang.Object);
    Code:
       0: aconst_null
       1: areturn

  public abstract com.android.ide.common.rendering.api.AdapterBinding getAdapterBinding(java.lang.Object, java.util.Map<java.lang.String, java.lang.String>);

  public abstract com.android.ide.common.rendering.api.ActionBarCallback getActionBarCallback();

  public java.lang.Object loadClass(java.lang.String, java.lang.Class[], java.lang.Object[]) throws java.lang.ClassNotFoundException;
    Code:
       0: aload_0
       1: aload_1
       2: aload_2
       3: aload_3
       4: invokevirtual #7                  // Method loadView:(Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;
       7: areturn
       8: astore        4
      10: aload         4
      12: athrow
      13: astore        4
      15: new           #13                 // class java/lang/ClassNotFoundException
      18: dup
      19: aload_1
      20: invokedynamic #17,  0             // InvokeDynamic #0:makeConcatWithConstants:(Ljava/lang/String;)Ljava/lang/String;
      25: aload         4
      27: invokespecial #21                 // Method java/lang/ClassNotFoundException."<init>":(Ljava/lang/String;Ljava/lang/Throwable;)V
      30: athrow
    Exception table:
       from    to  target type
           0     7     8   Class java/lang/ClassNotFoundException
           0     7    13   Class java/lang/Exception

  public java.lang.String getApplicationId();
    Code:
       0: aconst_null
       1: areturn

  public java.lang.String getResourcePackage();
    Code:
       0: aconst_null
       1: areturn

  public java.lang.Class<?> findClass(java.lang.String) throws java.lang.ClassNotFoundException;
    Code:
       0: new           #13                 // class java/lang/ClassNotFoundException
       3: dup
       4: aload_1
       5: invokedynamic #17,  0             // InvokeDynamic #0:makeConcatWithConstants:(Ljava/lang/String;)Ljava/lang/String;
      10: invokespecial #24                 // Method java/lang/ClassNotFoundException."<init>":(Ljava/lang/String;)V
      13: athrow

  public boolean isClassLoaded(java.lang.String);
    Code:
       0: iconst_0
       1: ireturn

  public com.android.ide.common.rendering.api.ResourceNamespace$Resolver getImplicitNamespaces();
    Code:
       0: getstatic     #27                 // Field com/android/ide/common/rendering/api/ResourceNamespace$Resolver.EMPTY_RESOLVER:Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;
       3: areturn

  public boolean hasLegacyAppCompat();
    Code:
       0: iconst_0
       1: ireturn

  public boolean hasAndroidXAppCompat();
    Code:
       0: iconst_0
       1: ireturn

  public boolean isResourceNamespacingRequired();
    Code:
       0: iconst_0
       1: ireturn

  public boolean shouldUseCustomInflater();
    Code:
       0: iconst_1
       1: ireturn

  public void error(java.lang.String, java.lang.String...);
    Code:
       0: return

  public void error(java.lang.String, java.lang.Throwable);
    Code:
       0: return

  public void error(java.lang.Throwable);
    Code:
       0: return

  public void warn(java.lang.String, java.lang.Throwable);
    Code:
       0: return

  public void warn(java.lang.Throwable);
    Code:
       0: return
}

exec
/bin/bash -lc "find server/libs/layoutlib-dist/android-34 -maxdepth 1 -type f -name 'layoutlib-*.jar' "'! -name '"'layoutlib-api-*' "'! -name '"'layoutlib-runtime-*' "'! -name '"'layoutlib-resources-*' -print" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar

exec
/bin/bash -lc "rg -n \"axpEmitClasspath|runtime-classpath|compile_and_runtime_not_namespaced|R_JAR|SampleAppClassLoader|AarExtractor\" docs server fixture -g '*.md' -g '*.kt' -g '*.kts'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:88:| C1 | `fixture/sample-app/app/build.gradle.kts` (수정) | `axpEmitClasspath` Gradle task — 해석된 debugRuntimeClasspath 의 AAR + JAR 절대 경로를 manifest 로 emit. `assembleDebug` 에 finalizedBy. | +25 |
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:90:| C3 | `server/layoutlib-worker/.../classloader/AarExtractor.kt` | `.aar` ZIP 에서 `classes.jar` 를 stable cache (`<sample-app>/build/axp/aar-classes/<sha1>/<artifact>.jar`) 로 추출. mtime 기반 idempotent. | 70 |
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:91:| C4 | `server/layoutlib-worker/.../classloader/SampleAppClassLoader.kt` | 위 manifest+extractor 결과 + R.jar 위치 → `URLClassLoader(parent = isolatedCL)`. 빌드된 CL 인스턴스 + 진단용 entry list. | 60 |
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:94:| C7 | `LayoutlibRenderer.kt` (수정) | `renderViaLayoutlib` 가 SampleAppClassLoader 를 빌드 후 callback 에 주입. lazy + per-renderer 캐시. | +20 |
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:98:| TC2 | `AarExtractorTest` (단위 4) | 정상 추출 / 캐시 hit / 손상 AAR / classes.jar 없는 AAR. | 100 |
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:99:| TC3 | `SampleAppClassLoaderTest` (단위 3) | 클래스로더 합성 / parent chain / R.jar 포함 검증. tmp dir + minimal jar fixture. | 80 |
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:111:    └─ tasks: ... → axpEmitClasspath (finalizedBy assembleDebug)
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:112:        └─ writes: app/build/axp/runtime-classpath.txt
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:118:    └─ buildSampleAppClassLoader(fixtureRoot) [lazy, per-renderer]
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:119:        ├─ SampleAppClasspathManifest.read(fixtureRoot.../runtime-classpath.txt)
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:123:        │       path ends ".aar" → AarExtractor.extract(path) // → classes.jar path
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:152:val axpClasspathManifest = layout.buildDirectory.file("axp/runtime-classpath.txt")
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:153:val axpEmitClasspath = tasks.register("axpEmitClasspath") {
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:168:    tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:173:- 출력 경로: `fixture/sample-app/app/build/axp/runtime-classpath.txt`.
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:192: * fixture/sample-app/app/build/axp/runtime-classpath.txt 파일을 읽어
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:195: * 파일 형식 (axpEmitClasspath Gradle task 가 emit):
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:229:### 4.3 C3 — AarExtractor
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:253:object AarExtractor {
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:290:### 4.4 C4 — SampleAppClassLoader
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:295:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:306: * AAR 은 AarExtractor 로 classes.jar 를 추출 후 그 경로를 URL 로 사용.
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:309:class SampleAppClassLoader private constructor(
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:319:        fun build(fixtureRoot: Path, parent: ClassLoader): SampleAppClassLoader {
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:327:                    AarExtractor.extract(entry, cacheRoot) ?: continue // resource-only AAR skip
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:336:            val rJar = fixtureRoot.resolve(R_JAR_RELATIVE_PATH)
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:343:            return SampleAppClassLoader(cl, urls.toList())
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:355:    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 fixture-relative 경로. */
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:356:    const val MANIFEST_RELATIVE_PATH = "app/build/axp/runtime-classpath.txt"
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:358:    /** AarExtractor 의 stable 캐시 디렉토리 (cacheRoot 기준 상대). */
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:365:     * AGP 8.x 가 emit 하는 통합 R.jar 경로 (compile_and_runtime_not_namespaced_r_class_jar variant).
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:368:    const val R_JAR_RELATIVE_PATH =
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:369:        "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:403:`renderViaLayoutlib` 가 callback 인스턴스 생성 직전에 SampleAppClassLoader 를 빌드:
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:406:@Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:411:    val built = SampleAppClassLoader.build(fixtureRoot, isolated)
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:428:**페어 리뷰 Q4**: `fixtureRoot` 이 sample-app 이 아닌 (예: 다른 fixture) 이면? — 현재 fixture-discovery 가 sample-app 만 지원. activity_minimal 이 framework-only 이므로 SampleAppClassLoader.build 호출이 manifest 누락 → throw 하면 W3D1 시나리오가 깨진다. 따라서:
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:523:| R4 | AGP 8.x 의 `compile_and_runtime_not_namespaced_r_class_jar` 경로가 AGP 9.x 에서 변경 가능 | ClassLoaderConstants 에 상수 1개 — 발견 시 단일 변경. |
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:539:- **Q1** (Gradle): `applicationVariants.all` API 가 `axpEmitClasspath` 를 더 안정적으로 wire 할 수 있나? AGP 8.x 의 best practice 는?
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:540:- **Q2** (Cache): `AarExtractor` 의 mtime 기반 invalidation 이 충분한가? hash 기반 (sha256) 으로 변경해야 하는 시나리오가 있나?
docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:542:- **Q4** (lazy build): SampleAppClassLoader 빌드를 lazy 로 미루면 (옵션 A) activity_minimal 호환성 유지. 더 단순한 옵션 (B: empty CL) 이 권장되는가?

exec
/bin/bash -lc "jar tf /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar | rg 'LayoutlibCallback|XmlParserFactory'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
com/android/ide/common/rendering/api/LayoutlibCallback$ViewAttribute.class
com/android/ide/common/rendering/api/LayoutlibCallback.class
com/android/ide/common/rendering/api/XmlParserFactory.class

exec
/bin/bash -lc "jar tf server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | rg 'BridgeInflater|LayoutInflater|BridgeContext|LayoutlibCallback' | head -80" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
android/view/BridgeInflater$1.class
android/view/BridgeInflater.class
android/view/LayoutInflater$BlinkLayout$1.class
android/view/LayoutInflater$BlinkLayout.class
android/view/LayoutInflater$Factory.class
android/view/LayoutInflater$Factory2.class
android/view/LayoutInflater$FactoryMerger.class
android/view/LayoutInflater$Filter.class
android/view/LayoutInflater.class
android/view/LayoutInflater_Delegate.class
com/android/internal/policy/PhoneLayoutInflater.class
com/android/layoutlib/bridge/android/BridgeContext$1.class
com/android/layoutlib/bridge/android/BridgeContext$2.class
com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.class
com/android/layoutlib/bridge/android/BridgeContext$Key.class
com/android/layoutlib/bridge/android/BridgeContext$TypedArrayCache.class
com/android/layoutlib/bridge/android/BridgeContext.class

exec
/bin/bash -lc "javap -classpath server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar:/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar -c -p android.view.BridgeInflater | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
exec
/bin/bash -lc "javap -classpath server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar:/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar -c -p android.view.LayoutInflater_Delegate | sed -n '1,240p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
exec
/bin/bash -lc 'strings server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | rg -n "findClass|isClassLoaded|loadClass|loadView|getApplicationId|getResourcePackage" | head -80' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 140ms:
 astore_1
      73: aload_1
      74: ifnull        83
      77: aload_1
      78: ldc           #166                // class java/lang/Object
      80: if_acmpne     2
      83: new           #141                // class java/lang/NoSuchMethodException
      86: dup
      87: invokespecial #169                // Method java/lang/NoSuchMethodException."<init>":()V
      90: athrow
    Exception table:
       from    to  target type
           2    66    67   Class java/lang/NoSuchMethodException

  private static java.lang.Class<?> findCustomInflater(com.android.layoutlib.bridge.android.BridgeContext, com.android.ide.common.rendering.api.LayoutlibCallback);
    Code:
       0: aload_1
       1: invokevirtual #181                // Method com/android/ide/common/rendering/api/LayoutlibCallback.isResourceNamespacingRequired:()Z
       4: ifeq          37
       7: aload_1
       8: invokevirtual #184                // Method com/android/ide/common/rendering/api/LayoutlibCallback.hasLegacyAppCompat:()Z
      11: ifeq          21
      14: getstatic     #186                // Field LEGACY_APPCOMPAT_INFLATER_CLASS_ATTR:Lcom/android/ide/common/rendering/api/ResourceReference;
      17: astore_2
      18: goto          41
      21: aload_1
      22: invokevirtual #189                // Method com/android/ide/common/rendering/api/LayoutlibCallback.hasAndroidXAppCompat:()Z
      25: ifeq          35
      28: getstatic     #191                // Field ANDROIDX_APPCOMPAT_INFLATER_CLASS_ATTR:Lcom/android/ide/common/rendering/api/ResourceReference;
      31: astore_2
      32: goto          41
      35: aconst_null
      36: areturn
      37: getstatic     #193                // Field RES_AUTO_INFLATER_CLASS_ATTR:Lcom/android/ide/common/rendering/api/ResourceReference;
      40: astore_2
      41: aload_0
      42: invokevirtual #199                // Method com/android/layoutlib/bridge/android/BridgeContext.getRenderResources:()Lcom/android/ide/common/rendering/api/RenderResources;
      45: aload_2
      46: invokevirtual #205                // Method com/android/ide/common/rendering/api/RenderResources.findItemInTheme:(Lcom/android/ide/common/rendering/api/ResourceReference;)Lcom/android/ide/common/rendering/api/ResourceValue;
      49: astore_3
      50: aload_3
      51: ifnull        63
      54: aload_3
      55: invokeinterface #211,  1          // InterfaceMethod com/android/ide/common/rendering/api/ResourceValue.getValue:()Ljava/lang/String;
      60: goto          64
      63: aconst_null
      64: astore        4
      66: aload         4
      68: ifnull        83
      71: aload_1
      72: aload         4
      74: invokevirtual #215                // Method com/android/ide/common/rendering/api/LayoutlibCallback.findClass:(Ljava/lang/String;)Ljava/lang/Class;
      77: areturn
      78: astore        5
      80: goto          123
      83: aload_0
      84: invokevirtual #218                // Method com/android/layoutlib/bridge/android/BridgeContext.isAppCompatTheme:()Z
      87: ifeq          123
      90: aload_1

 succeeded in 136ms:
Compiled from "LayoutInflater_Delegate.java"
public class android.view.LayoutInflater_Delegate {
  private static final java.lang.String TAG_MERGE;

  private static final java.lang.String ATTR_LAYOUT;

  private static final int[] ATTRS_THEME;

  public static boolean sIsInInclude;

  public android.view.LayoutInflater_Delegate();
    Code:
       0: aload_0
       1: invokespecial #38                 // Method java/lang/Object."<init>":()V
       4: return

  static void rInflate(android.view.LayoutInflater, org.xmlpull.v1.XmlPullParser, android.view.View, android.content.Context, android.util.AttributeSet, boolean) throws org.xmlpull.v1.XmlPullParserException, java.io.IOException;
    Code:
       0: iload         5
       2: ifne          20
       5: aload_0
       6: instanceof    #49                 // class android/view/BridgeInflater
       9: ifeq          20
      12: aload_0
      13: checkcast     #49                 // class android/view/BridgeInflater
      16: iconst_1
      17: invokevirtual #53                 // Method android/view/BridgeInflater.setIsInMerge:(Z)V
      20: aload_0
      21: aload_1
      22: aload_2
      23: aload_3
      24: aload         4
      26: iload         5
      28: invokevirtual #59                 // Method android/view/LayoutInflater.rInflate_Original:(Lorg/xmlpull/v1/XmlPullParser;Landroid/view/View;Landroid/content/Context;Landroid/util/AttributeSet;Z)V
      31: iload         5
      33: ifne          51
      36: aload_0
      37: instanceof    #49                 // class android/view/BridgeInflater
      40: ifeq          51
      43: aload_0
      44: checkcast     #49                 // class android/view/BridgeInflater
      47: iconst_0
      48: invokevirtual #53                 // Method android/view/BridgeInflater.setIsInMerge:(Z)V
      51: return

  public static void parseInclude(android.view.LayoutInflater, org.xmlpull.v1.XmlPullParser, android.content.Context, android.view.View, android.util.AttributeSet) throws org.xmlpull.v1.XmlPullParserException, java.io.IOException;
    Code:
       0: aload_3
       1: instanceof    #19                 // class android/view/ViewGroup
       4: ifeq          613
       7: aload_2
       8: aload         4
      10: getstatic     #76                 // Field ATTRS_THEME:[I
      13: invokevirtual #82                 // Method android/content/Context.obtainStyledAttributes:(Landroid/util/AttributeSet;[I)Landroid/content/res/TypedArray;
      16: astore        6
      18: aload         6
      20: iconst_0
      21: iconst_0
      22: invokevirtual #88                 // Method android/content/res/TypedArray.getResourceId:(II)I
      25: istore        7
      27: iload         7
      29: ifeq          36
      32: iconst_1
      33: goto          37
      36: iconst_0
      37: istore        8
      39: iload         8
      41: ifeq          55
      44: new           #90                 // class android/view/ContextThemeWrapper
      47: dup
      48: aload_2
      49: iload         7
      51: invokespecial #93                 // Method android/view/ContextThemeWrapper."<init>":(Landroid/content/Context;I)V
      54: astore_2
      55: aload         6
      57: invokevirtual #96                 // Method android/content/res/TypedArray.recycle:()V
      60: aload         4
      62: aconst_null
      63: ldc           #30                 // String layout
      65: iconst_0
      66: invokeinterface #102,  4          // InterfaceMethod android/util/AttributeSet.getAttributeResourceValue:(Ljava/lang/String;Ljava/lang/String;I)I
      71: istore        9
      73: iload         9
      75: ifne          139
      78: aload         4
      80: aconst_null
      81: ldc           #30                 // String layout
      83: invokeinterface #106,  3          // InterfaceMethod android/util/AttributeSet.getAttributeValue:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
      88: astore        10
      90: aload         10
      92: ifnull        103
      95: aload         10
      97: invokevirtual #112                // Method java/lang/String.length:()I
     100: ifgt          122
     103: invokestatic  #118                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
     106: ldc           #120                // String broken
     108: ldc           #122                // String You must specify a layout in the include tag: <include layout=\"@layout/layoutID\" />
     110: aconst_null
     111: aconst_null
     112: invokeinterface #128,  5          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V
     117: aload_1
     118: invokestatic  #132                // Method android/view/LayoutInflater.consumeChildElements:(Lorg/xmlpull/v1/XmlPullParser;)V
     121: return
     122: aload_2
     123: invokevirtual #136                // Method android/content/Context.getResources:()Landroid/content/res/Resources;
     126: aload         10
     128: iconst_1
     129: invokevirtual #140                // Method java/lang/String.substring:(I)Ljava/lang/String;
     132: aconst_null
     133: aconst_null
     134: invokevirtual #144                // Method android/content/res/Resources.getIdentifier:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
     137: istore        9
     139: iload         9
     141: ifeq          175
     144: new           #146                // class android/util/TypedValue
     147: dup
     148: invokespecial #147                // Method android/util/TypedValue."<init>":()V
     151: astore        10
     153: aload_2
     154: invokevirtual #151                // Method android/content/Context.getTheme:()Landroid/content/res/Resources$Theme;
     157: iload         9
     159: aload         10
     161: iconst_1
     162: invokevirtual #155                // Method android/content/res/Resources$Theme.resolveAttribute:(ILandroid/util/TypedValue;Z)Z
     165: ifeq          175
     168: aload         10
     170: getfield      #159                // Field android/util/TypedValue.resourceId:I
     173: istore        9
     175: iload         9
     177: ifne          254
     180: aload         4
     182: aconst_null
     183: ldc           #30                 // String layout
     185: invokeinterface #106,  3          // InterfaceMethod android/util/AttributeSet.getAttributeValue:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
     190: astore        10
     192: aload         10
     194: ifnonnull     214
     197: invokestatic  #118                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
     200: ldc           #120                // String broken
     202: ldc           #122                // String You must specify a layout in the include tag: <include layout=\"@layout/layoutID\" />
     204: aconst_null
     205: aconst_null
     206: invokeinterface #128,  5          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V
     211: goto          251
     214: invokestatic  #118                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
     217: ldc           #120                // String broken
     219: new           #161                // class java/lang/StringBuilder
     222: dup
     223: invokespecial #162                // Method java/lang/StringBuilder."<init>":()V
     226: ldc           #164                // String You must specify a valid layout reference. The layout ID
     228: invokevirtual #168                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
     231: aload         10
     233: invokevirtual #168                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
     236: ldc           #170                // String  is not valid.
     238: invokevirtual #168                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
     241: invokevirtual #174                // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
     244: aconst_null
     245: aconst_null
     246: invokeinterface #128,  5          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V
     251: goto          610
     254: aload_0
     255: invokevirtual #178                // Method android/view/LayoutInflater.getContext:()Landroid/content/Context;
     258: invokevirtual #136                // Method android/content/Context.getResources:()Landroid/content/res/Resources;
     261: iload         9
     263: invokevirtual #182                // Method android/content/res/Resources.getLayout:(I)Landroid/content/res/XmlResourceParser;
     266: astore        10
     268: aload         10
     270: invokestatic  #188                // Method android/util/Xml.asAttributeSet:(Lorg/xmlpull/v1/XmlPullParser;)Landroid/util/AttributeSet;
     273: astore        11
     275: aload         10
     277: invokeinterface #193,  1          // InterfaceMethod android/content/res/XmlResourceParser.next:()I
     282: dup
     283: istore        5
     285: iconst_2
     286: if_icmpeq     298
     289: iload         5
     291: iconst_1
     292: if_icmpeq     298
     295: goto          275
     298: iload         5
     300: iconst_2
     301: if_icmpeq     353
     304: invokestatic  #118                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
     307: ldc           #120                // String broken
     309: new           #161                // class java/lang/StringBuilder
     312: dup
     313: invokespecial #162                // Method java/lang/StringBuilder."<init>":()V
     316: aload         10
     318: invokeinterface #200,  1          // InterfaceMethod android/content/res/XmlResourceParser.getPositionDescription:()Ljava/lang/String;
     323: invokevirtual #168                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
     326: ldc           #202                // String : No start tag found!
     328: invokevirtual #168                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
     331: invokevirtual #174                // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
     334: aconst_null
     335: aconst_null
     336: invokeinterface #128,  5          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V
     341: aload_1
     342: invokestatic  #132                // Method android/view/LayoutInflater.consumeChildElements:(Lorg/xmlpull/v1/XmlPullParser;)V
     345: aload         10
     347: invokeinterface #205,  1          // InterfaceMethod android/content/res/XmlResourceParser.close:()V
     352: return
     353: aload         10
     355: invokeinterface #208,  1          // InterfaceMethod android/content/res/XmlResourceParser.getName:()Ljava/lang/String;
     360: astore        12
     362: ldc           #27                 // String merge
     364: aload         12
     366: invokevirtual #212                // Method java/lang/String.equals:(Ljava/lang/Object;)Z
     369: ifeq          386
     372: aload_0
     373: aload         10
     375: aload_3
     376: aload_2
     377: aload         11
     379: iconst_0
     380: invokevirtual #214                // Method android/view/LayoutInflater.rInflate:(Lorg/xmlpull/v1/XmlPullParser;Landroid/view/View;Landroid/content/Context;Landroid/util/AttributeSet;Z)V
     383: goto          588
     386: aload_0
     387: aload_3
     388: aload         12
     390: aload_2
     391: aload         11
     393: iload         8
     395: invokevirtual #218                // Method android/view/LayoutInflater.createViewFromTag:(Landroid/view/View;Ljava/lang/String;Landroid/content/Context;Landroid/util/AttributeSet;Z)Landroid/view/View;
     398: astore        13
     400: aload_3
     401: checkcast     #19                 // class android/view/ViewGroup
     404: astore        14
     406: aload_2
     407: aload         4
     409: getstatic     #221                // Field com/android/internal/R$styleable.Include:[I
     412: invokevirtual #82                 // Method android/content/Context.obtainStyledAttributes:(Landroid/util/AttributeSet;[I)Landroid/content/res/TypedArray;
     415: astore        15
     417: aload         15
     419: iconst_0
     420: iconst_m1
     421: invokevirtual #88                 // Method android/content/res/TypedArray.getResourceId:(II)I
     424: istore        16
     426: aload         15
     428: iconst_1
     429: iconst_m1

 succeeded in 187ms:
exec
/bin/bash -lc "javap -classpath server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar:/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar -c -p android.view.BridgeInflater | sed -n '260,620p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 151ms:
      90: aload_1
      91: invokevirtual #184                // Method com/android/ide/common/rendering/api/LayoutlibCallback.hasLegacyAppCompat:()Z
      94: ifeq          104
      97: aload_1
      98: ldc           #33                 // String android.support.v7.app.AppCompatViewInflater
     100: invokevirtual #215                // Method com/android/ide/common/rendering/api/LayoutlibCallback.findClass:(Ljava/lang/String;)Ljava/lang/Class;
     103: areturn
     104: aload_1
     105: invokevirtual #189                // Method com/android/ide/common/rendering/api/LayoutlibCallback.hasAndroidXAppCompat:()Z
     108: ifeq          118
     111: aload_1
     112: ldc           #36                 // String androidx.appcompat.app.AppCompatViewInflater
     114: invokevirtual #215                // Method com/android/ide/common/rendering/api/LayoutlibCallback.findClass:(Ljava/lang/String;)Ljava/lang/Class;
     117: areturn
     118: goto          123
     121: astore        5
     123: aconst_null
     124: areturn
    Exception table:
       from    to  target type
          71    77    78   Class java/lang/ClassNotFoundException
          90   103   121   Class java/lang/ClassNotFoundException
         104   117   121   Class java/lang/ClassNotFoundException

  private android.view.View createViewFromCustomInflater(java.lang.String, android.util.AttributeSet);
    Code:
       0: aload_0
       1: getfield      #77                 // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
       4: invokevirtual #232                // Method com/android/ide/common/rendering/api/LayoutlibCallback.shouldUseCustomInflater:()Z
       7: ifne          12
      10: aconst_null
      11: areturn
      12: aload_0
      13: getfield      #234                // Field mCustomInflater:Ljava/util/function/BiFunction;
      16: ifnonnull     128
      19: aload_0
      20: invokevirtual #238                // Method getContext:()Landroid/content/Context;
      23: astore_3
      24: aload_3
      25: invokestatic  #67                 // Method com/android/layoutlib/bridge/android/BridgeContext.getBaseContext:(Landroid/content/Context;)Landroid/content/Context;
      28: astore_3
      29: aload_3
      30: instanceof    #63                 // class com/android/layoutlib/bridge/android/BridgeContext
      33: ifeq          112
      36: aload_3
      37: checkcast     #63                 // class com/android/layoutlib/bridge/android/BridgeContext
      40: astore        4
      42: aload         4
      44: aload_0
      45: getfield      #77                 // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
      48: invokestatic  #240                // Method findCustomInflater:(Lcom/android/layoutlib/bridge/android/BridgeContext;Lcom/android/ide/common/rendering/api/LayoutlibCallback;)Ljava/lang/Class;
      51: astore        5
      53: aload         5
      55: ifnull        112
      58: aload         5
      60: iconst_0
      61: anewarray     #143                // class java/lang/Class
      64: invokevirtual #244                // Method java/lang/Class.getDeclaredConstructor:([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;
      67: astore        6
      69: aload         6
      71: iconst_1
      72: invokevirtual #247                // Method java/lang/reflect/Constructor.setAccessible:(Z)V
      75: aload         6
      77: iconst_0
      78: anewarray     #166                // class java/lang/Object
      81: invokevirtual #251                // Method java/lang/reflect/Constructor.newInstance:([Ljava/lang/Object;)Ljava/lang/Object;
      84: astore        7
      86: aload         5
      88: invokestatic  #253                // Method getCreateViewMethod:(Ljava/lang/Class;)Ljava/lang/reflect/Method;
      91: astore        8
      93: aload_0
      94: aload_0
      95: aload         8
      97: aload         7
      99: invokedynamic #272,  0            // InvokeDynamic #0:apply:(Landroid/view/BridgeInflater;Ljava/lang/reflect/Method;Ljava/lang/Object;)Ljava/util/function/BiFunction;
     104: putfield      #234                // Field mCustomInflater:Ljava/util/function/BiFunction;
     107: goto          112
     110: astore        6
     112: aload_0
     113: getfield      #234                // Field mCustomInflater:Ljava/util/function/BiFunction;
     116: ifnonnull     128
     119: aload_0
     120: invokedynamic #281,  0            // InvokeDynamic #1:apply:()Ljava/util/function/BiFunction;
     125: putfield      #234                // Field mCustomInflater:Ljava/util/function/BiFunction;
     128: aload_0
     129: getfield      #234                // Field mCustomInflater:Ljava/util/function/BiFunction;
     132: aload_1
     133: aload_2
     134: invokeinterface #285,  3          // InterfaceMethod java/util/function/BiFunction.apply:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
     139: checkcast     #11                 // class android/view/View
     142: areturn
    Exception table:
       from    to  target type
          58   107   110   Class java/lang/reflect/InvocationTargetException
          58   107   110   Class java/lang/IllegalAccessException
          58   107   110   Class java/lang/NoSuchMethodException
          58   107   110   Class java/lang/InstantiationException

  public android.view.View createViewFromTag(android.view.View, java.lang.String, android.content.Context, android.util.AttributeSet, boolean);
    Code:
       0: aconst_null
       1: astore        6
       3: aload_2
       4: ldc_w         #294                // String view
       7: invokevirtual #298                // Method java/lang/String.equals:(Ljava/lang/Object;)Z
      10: ifeq          68
      13: aload         4
      15: aconst_null
      16: ldc_w         #300                // String class
      19: invokeinterface #304,  3          // InterfaceMethod android/util/AttributeSet.getAttributeValue:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
      24: astore_2
      25: aload_2
      26: ifnonnull     68
      29: invokestatic  #310                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
      32: ldc_w         #312                // String broken
      35: ldc_w         #314                // String Unable to inflate view tag without class attribute
      38: aconst_null
      39: aconst_null
      40: invokeinterface #320,  5          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V
      45: new           #322                // class com/android/layoutlib/bridge/MockView
      48: dup
      49: aload_3
      50: aload         4
      52: invokespecial #325                // Method com/android/layoutlib/bridge/MockView."<init>":(Landroid/content/Context;Landroid/util/AttributeSet;)V
      55: astore        6
      57: aload         6
      59: checkcast     #322                // class com/android/layoutlib/bridge/MockView
      62: ldc_w         #294                // String view
      65: invokevirtual #329                // Method com/android/layoutlib/bridge/MockView.setText:(Ljava/lang/CharSequence;)V
      68: aload         6
      70: ifnonnull     86
      73: aload_0
      74: aload_1
      75: aload_2
      76: aload_3
      77: aload         4
      79: iload         5
      81: invokespecial #331                // Method android/view/LayoutInflater.createViewFromTag:(Landroid/view/View;Ljava/lang/String;Landroid/content/Context;Landroid/util/AttributeSet;Z)Landroid/view/View;
      84: astore        6
      86: goto          286
      89: astore        7
      91: iload         5
      93: ifne          137
      96: aload_3
      97: aload         4
      99: getstatic     #333                // Field ATTRS_THEME:[I
     102: invokevirtual #337                // Method android/content/Context.obtainStyledAttributes:(Landroid/util/AttributeSet;[I)Landroid/content/res/TypedArray;
     105: astore        8
     107: aload         8
     109: iconst_0
     110: iconst_0
     111: invokevirtual #343                // Method android/content/res/TypedArray.getResourceId:(II)I
     114: istore        9
     116: iload         9
     118: ifeq          132
     121: new           #345                // class android/view/ContextThemeWrapper
     124: dup
     125: aload_3
     126: iload         9
     128: invokespecial #348                // Method android/view/ContextThemeWrapper."<init>":(Landroid/content/Context;I)V
     131: astore_3
     132: aload         8
     134: invokevirtual #351                // Method android/content/res/TypedArray.recycle:()V
     137: aload         7
     139: invokevirtual #355                // Method android/view/InflateException.getCause:()Ljava/lang/Throwable;
     142: instanceof    #98                 // class java/lang/ClassNotFoundException
     145: ifne          192
     148: new           #322                // class com/android/layoutlib/bridge/MockView
     151: dup
     152: aload_3
     153: aload         4
     155: invokespecial #325                // Method com/android/layoutlib/bridge/MockView."<init>":(Landroid/content/Context;Landroid/util/AttributeSet;)V
     158: astore        6
     160: aload         6
     162: checkcast     #322                // class com/android/layoutlib/bridge/MockView
     165: aload_2
     166: invokevirtual #329                // Method com/android/layoutlib/bridge/MockView.setText:(Ljava/lang/CharSequence;)V
     169: invokestatic  #310                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
     172: ldc_w         #312                // String broken
     175: aload         7
     177: invokevirtual #358                // Method android/view/InflateException.getMessage:()Ljava/lang/String;
     180: aload         7
     182: aconst_null
     183: aconst_null
     184: invokeinterface #361,  6          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;Ljava/lang/Object;Ljava/lang/Object;)V
     189: goto          286
     192: aload_0
     193: getfield      #91                 // Field mConstructorArgs:[Ljava/lang/Object;
     196: iconst_0
     197: aaload
     198: astore        8
     200: aload_0
     201: getfield      #91                 // Field mConstructorArgs:[Ljava/lang/Object;
     204: iconst_0
     205: aload_3
     206: aastore
     207: aload_0
     208: aload_2
     209: aload         4
     211: invokevirtual #119                // Method loadCustomView:(Ljava/lang/String;Landroid/util/AttributeSet;)Landroid/view/View;
     214: astore        6
     216: aload_0
     217: getfield      #91                 // Field mConstructorArgs:[Ljava/lang/Object;
     220: iconst_0
     221: aload         8
     223: aastore
     224: goto          286
     227: astore        9
     229: new           #100                // class android/view/InflateException
     232: dup
     233: invokespecial #362                // Method android/view/InflateException."<init>":()V
     236: astore        10
     238: aload         9
     240: invokevirtual #365                // Method java/lang/Object.getClass:()Ljava/lang/Class;
     243: ldc           #98                 // class java/lang/ClassNotFoundException
     245: invokevirtual #366                // Method java/lang/Object.equals:(Ljava/lang/Object;)Z
     248: ifne          262
     251: aload         10
     253: aload         9
     255: invokevirtual #370                // Method android/view/InflateException.initCause:(Ljava/lang/Throwable;)Ljava/lang/Throwable;
     258: pop
     259: goto          270
     262: aload         10
     264: aload         7
     266: invokevirtual #370                // Method android/view/InflateException.initCause:(Ljava/lang/Throwable;)Ljava/lang/Throwable;
     269: pop
     270: aload         10
     272: athrow
     273: astore        11
     275: aload_0
     276: getfield      #91                 // Field mConstructorArgs:[Ljava/lang/Object;
     279: iconst_0
     280: aload         8
     282: aastore
     283: aload         11
     285: athrow
     286: aload_0
     287: aload         6
     289: aload         4
     291: invokevirtual #127                // Method setupViewInContext:(Landroid/view/View;Landroid/util/AttributeSet;)V
     294: aload         6
     296: areturn
    Exception table:
       from    to  target type
          68    86    89   Class android/view/InflateException
         207   216   227   Class java/lang/Exception
         207   216   273   any
         227   275   273   any

  public android.view.View inflate(int, android.view.ViewGroup);
    Code:
       0: aload_0
       1: invokevirtual #238                // Method getContext:()Landroid/content/Context;
       4: astore_3
       5: aload_3
       6: invokestatic  #67                 // Method com/android/layoutlib/bridge/android/BridgeContext.getBaseContext:(Landroid/content/Context;)Landroid/content/Context;
       9: astore_3
      10: aload_3
      11: instanceof    #63                 // class com/android/layoutlib/bridge/android/BridgeContext
      14: ifeq          161
      17: aload_3
      18: checkcast     #63                 // class com/android/layoutlib/bridge/android/BridgeContext
      21: astore        4
      23: aconst_null
      24: astore        5
      26: iload_1
      27: invokestatic  #387                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
      30: astore        6
      32: aload         6
      34: ifnonnull     47
      37: aload_0
      38: getfield      #77                 // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
      41: iload_1
      42: invokevirtual #388                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
      45: astore        6
      47: aload         6
      49: ifnull        64
      52: aload         4
      54: invokevirtual #199                // Method com/android/layoutlib/bridge/android/BridgeContext.getRenderResources:()Lcom/android/ide/common/rendering/api/RenderResources;
      57: aload         6
      59: invokevirtual #393                // Method com/android/ide/common/rendering/api/RenderResources.getResolvedResource:(Lcom/android/ide/common/rendering/api/ResourceReference;)Lcom/android/ide/common/rendering/api/ResourceValue;
      62: astore        5
      64: aload         5
      66: ifnull        161
      69: aload         5
      71: invokeinterface #211,  1          // InterfaceMethod com/android/ide/common/rendering/api/ResourceValue.getValue:()Ljava/lang/String;
      76: astore        7
      78: aload         7
      80: iconst_1
      81: invokestatic  #399                // Method com/android/layoutlib/bridge/impl/ParserFactory.create:(Ljava/lang/String;Z)Lorg/xmlpull/v1/XmlPullParser;
      84: astore        8
      86: aload         8
      88: ifnonnull     93
      91: aconst_null
      92: areturn
      93: new           #403                // class com/android/layoutlib/bridge/android/BridgeXmlBlockParser
      96: dup
      97: aload         8
      99: aload         4
     101: aload         5
     103: invokeinterface #407,  1          // InterfaceMethod com/android/ide/common/rendering/api/ResourceValue.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
     108: invokespecial #410                // Method com/android/layoutlib/bridge/android/BridgeXmlBlockParser."<init>":(Lorg/xmlpull/v1/XmlPullParser;Lcom/android/layoutlib/bridge/android/BridgeContext;Lcom/android/ide/common/rendering/api/ResourceNamespace;)V
     111: astore        9
     113: aload_0
     114: aload         9
     116: aload_2
     117: invokevirtual #413                // Method inflate:(Lorg/xmlpull/v1/XmlPullParser;Landroid/view/ViewGroup;)Landroid/view/View;
     120: areturn
     121: astore        8
     123: invokestatic  #310                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
     126: ldc_w         #415                // String resources.read
     129: new           #417                // class java/lang/StringBuilder
     132: dup
     133: invokespecial #418                // Method java/lang/StringBuilder."<init>":()V
     136: ldc_w         #420                // String Failed to parse file
     139: invokevirtual #424                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
     142: aload         7
     144: invokevirtual #424                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
     147: invokevirtual #427                // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
     150: aload         8
     152: aconst_null
     153: aconst_null
     154: invokeinterface #361,  6          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;Ljava/lang/Object;Ljava/lang/Object;)V
     159: aconst_null
     160: areturn
     161: aconst_null
     162: areturn
    Exception table:
       from    to  target type
          78    92   121   Class java/lang/Exception
          93   120   121   Class java/lang/Exception

  private android.view.View loadCustomView(java.lang.String, android.util.AttributeSet, boolean) throws java.lang.Exception;
    Code:
       0: aload_0
       1: getfield      #77                 // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
       4: ifnull        94
       7: aload_1
       8: ldc_w         #294                // String view
      11: invokevirtual #298                // Method java/lang/String.equals:(Ljava/lang/Object;)Z
      14: ifeq          34
      17: aload_2
      18: aconst_null
      19: ldc_w         #300                // String class
      22: invokeinterface #304,  3          // InterfaceMethod android/util/AttributeSet.getAttributeValue:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
      27: astore_1
      28: aload_1
      29: ifnonnull     34
      32: aconst_null
      33: areturn
      34: aload_0
      35: getfield      #91                 // Field mConstructorArgs:[Ljava/lang/Object;
      38: iconst_1
      39: aload_2
      40: aastore
      41: iload_3
      42: ifeq          63
      45: aload_0
      46: getfield      #77                 // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
      49: aload_1
      50: getstatic     #442                // Field mConstructorSignature:[Ljava/lang/Class;

exec
/bin/bash -lc "javap -classpath server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar:/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar -c -p android.view.BridgeInflater | sed -n '620,760p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 139ms:
      50: getstatic     #442                // Field mConstructorSignature:[Ljava/lang/Class;
      53: aload_0
      54: getfield      #91                 // Field mConstructorArgs:[Ljava/lang/Object;
      57: invokevirtual #446                // Method com/android/ide/common/rendering/api/LayoutlibCallback.loadClass:(Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;
      60: goto          78
      63: aload_0
      64: getfield      #77                 // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
      67: aload_1
      68: getstatic     #442                // Field mConstructorSignature:[Ljava/lang/Class;
      71: aload_0
      72: getfield      #91                 // Field mConstructorArgs:[Ljava/lang/Object;
      75: invokevirtual #449                // Method com/android/ide/common/rendering/api/LayoutlibCallback.loadView:(Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;
      78: astore        4
      80: aload         4
      82: instanceof    #11                 // class android/view/View
      85: ifeq          94
      88: aload         4
      90: checkcast     #11                 // class android/view/View
      93: areturn
      94: aconst_null
      95: areturn

  private android.view.View loadCustomView(java.lang.String, android.util.AttributeSet) throws java.lang.Exception;
    Code:
       0: aload_0
       1: aload_1
       2: aload_2
       3: iconst_0
       4: invokevirtual #453                // Method loadCustomView:(Ljava/lang/String;Landroid/util/AttributeSet;Z)Landroid/view/View;
       7: areturn

  private void setupViewInContext(android.view.View, android.util.AttributeSet);
    Code:
       0: aload_0
       1: invokevirtual #238                // Method getContext:()Landroid/content/Context;
       4: astore_3
       5: aload_3
       6: invokestatic  #67                 // Method com/android/layoutlib/bridge/android/BridgeContext.getBaseContext:(Landroid/content/Context;)Landroid/content/Context;
       9: astore_3
      10: aload_3
      11: instanceof    #63                 // class com/android/layoutlib/bridge/android/BridgeContext
      14: ifne          18
      17: return
      18: aload_3
      19: checkcast     #63                 // class com/android/layoutlib/bridge/android/BridgeContext
      22: astore        4
      24: aload_2
      25: aload         4
      27: aload_0
      28: getfield      #455                // Field mResourceReference:Lcom/android/ide/common/rendering/api/ResourceReference;
      31: aload_0
      32: getfield      #61                 // Field mIsInMerge:Z
      35: invokestatic  #459                // Method getViewKeyFromParser:(Landroid/util/AttributeSet;Lcom/android/layoutlib/bridge/android/BridgeContext;Lcom/android/ide/common/rendering/api/ResourceReference;Z)Ljava/lang/Object;
      38: astore        5
      40: aload         5
      42: ifnull        53
      45: aload         4
      47: aload_1
      48: aload         5
      50: invokevirtual #463                // Method com/android/layoutlib/bridge/android/BridgeContext.addViewKey:(Landroid/view/View;Ljava/lang/Object;)V
      53: aload_2
      54: ldc_w         #465                // String http://schemas.android.com/apk/res/android
      57: ldc_w         #467                // String scrollX
      60: invokeinterface #304,  3          // InterfaceMethod android/util/AttributeSet.getAttributeValue:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
      65: astore        6
      67: aload         6
      69: ifnull        109
      72: aload         6
      74: ldc_w         #469                // String px
      77: invokevirtual #473                // Method java/lang/String.endsWith:(Ljava/lang/String;)Z
      80: ifeq          109
      83: aload         6
      85: iconst_0
      86: aload         6
      88: invokevirtual #477                // Method java/lang/String.length:()I
      91: iconst_2
      92: isub
      93: invokevirtual #481                // Method java/lang/String.substring:(II)Ljava/lang/String;
      96: invokestatic  #487                // Method java/lang/Integer.parseInt:(Ljava/lang/String;)I
      99: istore        7
     101: aload         4
     103: aload_1
     104: iload         7
     106: invokevirtual #491                // Method com/android/layoutlib/bridge/android/BridgeContext.setScrollXPos:(Landroid/view/View;I)V
     109: aload_2
     110: ldc_w         #465                // String http://schemas.android.com/apk/res/android
     113: ldc_w         #493                // String scrollY
     116: invokeinterface #304,  3          // InterfaceMethod android/util/AttributeSet.getAttributeValue:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
     121: astore        7
     123: aload         7
     125: ifnull        165
     128: aload         7
     130: ldc_w         #469                // String px
     133: invokevirtual #473                // Method java/lang/String.endsWith:(Ljava/lang/String;)Z
     136: ifeq          165
     139: aload         7
     141: iconst_0
     142: aload         7
     144: invokevirtual #477                // Method java/lang/String.length:()I
     147: iconst_2
     148: isub
     149: invokevirtual #481                // Method java/lang/String.substring:(II)Ljava/lang/String;
     152: invokestatic  #487                // Method java/lang/Integer.parseInt:(Ljava/lang/String;)I
     155: istore        8
     157: aload         4
     159: aload_1
     160: iload         8
     162: invokevirtual #496                // Method com/android/layoutlib/bridge/android/BridgeContext.setScrollYPos:(Landroid/view/View;I)V
     165: aload_1
     166: getstatic     #501                // Field com/android/layoutlib/bridge/android/support/RecyclerViewUtil.CN_RECYCLER_VIEW:[Ljava/lang/String;
     169: invokestatic  #507                // Method com/android/layoutlib/common/util/ReflectionUtils.isInstanceOf:(Ljava/lang/Object;[Ljava/lang/String;)Z
     172: ifeq          254
     175: iconst_0
     176: istore        8
     178: aload_2
     179: ldc_w         #509                // String http://schemas.android.com/tools
     182: ldc_w         #511                // String itemCount
     185: iconst_m1
     186: invokeinterface #515,  4          // InterfaceMethod android/util/AttributeSet.getAttributeIntValue:(Ljava/lang/String;Ljava/lang/String;I)I
     191: istore        9
     193: aload_2
     194: instanceof    #517                // class android/util/ResolvingAttributeSet
     197: ifeq          237
     200: aload_2
     201: checkcast     #517                // class android/util/ResolvingAttributeSet
     204: ldc_w         #509                // String http://schemas.android.com/tools
     207: ldc_w         #519                // String listitem
     210: invokeinterface #523,  3          // InterfaceMethod android/util/ResolvingAttributeSet.getResolvedAttributeValue:(Ljava/lang/String;Ljava/lang/String;)Lcom/android/ide/common/rendering/api/ResourceValue;
     215: astore        10
     217: aload         10
     219: ifnull        237
     222: aload         4
     224: aload         10
     226: invokeinterface #527,  1          // InterfaceMethod com/android/ide/common/rendering/api/ResourceValue.asReference:()Lcom/android/ide/common/rendering/api/ResourceReference;
     231: iconst_0
     232: invokevirtual #530                // Method com/android/layoutlib/bridge/android/BridgeContext.getResourceId:(Lcom/android/ide/common/rendering/api/ResourceReference;I)I
     235: istore        8
     237: aload_1
     238: aload         4
     240: aload_0
     241: getfield      #77                 // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.session
     2	
     3	/**
     4	 * W2D7-RENDERSESSION (08 §7.7.1 item 3b): SessionParams / HardwareConfig / render timeout
     5	 * 등 layoutlib render 경로 전역 상수.
     6	 *
     7	 * CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" 정책 준수 — 모든 리터럴은 본 object
     8	 * 를 참조하여 single source of truth.
     9	 */
    10	object SessionConstants {
    11	    /** phone_normal 폭 (px). 360dp × xhdpi. HardwareConfig.screenWidth 와 일치. */
    12	    const val RENDER_WIDTH_PX = 720
    13	
    14	    /** phone_normal 높이 (px). 640dp × xhdpi. */
    15	    const val RENDER_HEIGHT_PX = 1280
    16	
    17	    /** xhdpi 의 논리 dpi. HardwareConfig.xdpi / ydpi 양쪽에 사용. */
    18	    const val DPI_XHIGH = 320f
    19	
    20	    /**
    21	     * RenderSession.render(timeout) 밀리초. 6_000ms 는 Paparazzi 기본값과 정합.
    22	     * RenderParams.DEFAULT_TIMEOUT 이 10_000ms 이지만, fixture 가 trivial 하므로 더 짧게 설정.
    23	     */
    24	    const val RENDER_TIMEOUT_MS = 6_000L
    25	
    26	    /** minSdk — fixture 는 android-34 dist 에서 렌더. */
    27	    const val MIN_SDK = 34
    28	
    29	    /** targetSdk — android-34 번들과 맞춤. */
    30	    const val TARGET_SDK = 34
    31	
    32	    /** SessionParams.projectKey — render cache / callback context 식별용 상수 토큰. */
    33	    const val PROJECT_KEY = "axp-w2d7-rendersession"
    34	
    35	    /** RenderSession.setLocale(locale). 기본 en. */
    36	    const val RENDER_LOCALE = "en"
    37	
    38	    /** layoutlib framework package name (resource namespace "android"). */
    39	    const val FRAMEWORK_PACKAGE = "android"
    40	
    41	    /** F2 default theme — Theme.Material.Light.NoActionBar (decor 제거됨 + decent TextAppearance 체인). */
    42	    const val DEFAULT_FRAMEWORK_THEME = "Theme.Material.Light.NoActionBar"
    43	
    44	    /**
    45	     * UI_MODE_TYPE_NORMAL (0x01) | UI_MODE_NIGHT_NO (0x10) = 0x11 (17).
    46	     * android.content.res.Configuration 의 uiMode 필드 값.
    47	     * FolderConfiguration 매칭 시 base `values/` (qualifier 없음) 리소스 선택 보장.
    48	     */
    49	    const val UI_MODE_NORMAL_DAY = 0x11
    50	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt | sed -n '1,300p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.resources
     2	
     3	import com.android.ide.common.rendering.api.RenderResources
     4	import com.android.ide.common.rendering.api.ResourceNamespace
     5	import com.android.ide.common.rendering.api.ResourceReference
     6	import com.android.ide.common.rendering.api.ResourceValue
     7	import com.android.ide.common.rendering.api.StyleResourceValue
     8	import com.android.ide.common.rendering.api.StyleResourceValueImpl
     9	import com.android.resources.ResourceType
    10	
    11	/**
    12	 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2 3b-values): RenderResources subclass.
    13	 * Bundle 에 있는 값을 framework (android) namespace 로 매핑하여 layoutlib 에 제공.
    14	 *
    15	 * 설계 결정:
    16	 *  - `findResValue` 는 override 금지 (W2D7 L3 landmine — 기본 RenderResources 의 ref 해석을 가로챔).
    17	 *  - default theme 은 생성자 주입. bundle 내 style 이 없어도 parent=null 빈 StyleResourceValue 반환
    18	 *    (layoutlib 이 self-chain 만 수행하고 실패하지 않도록 minimal fallback).
    19	 *  - project namespace (RES_AUTO) 요청은 전부 null — framework 전용 resolver.
    20	 */
    21	class FrameworkRenderResources(
    22	    private val bundle: FrameworkResourceBundle,
    23	    private val defaultThemeName: String,
    24	) : RenderResources() {
    25	
    26	    private val mDefaultTheme: StyleResourceValue = bundle.getStyle(defaultThemeName)
    27	        ?: StyleResourceValueImpl(
    28	            ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, defaultThemeName),
    29	            /* parentStyle */ null,
    30	            /* libraryName */ null,
    31	        )
    32	
    33	    override fun getDefaultTheme(): StyleResourceValue = mDefaultTheme
    34	
    35	    override fun getStyle(ref: ResourceReference): StyleResourceValue? {
    36	        if (ref.namespace != ResourceNamespace.ANDROID) return null
    37	        return bundle.getStyle(ref.name)
    38	    }
    39	
    40	    override fun getUnresolvedResource(ref: ResourceReference): ResourceValue? {
    41	        if (ref.namespace != ResourceNamespace.ANDROID) return null
    42	        return bundle.getResource(ref.resourceType, ref.name)
    43	    }
    44	
    45	    override fun getResolvedResource(ref: ResourceReference): ResourceValue? {
    46	        // framework scope 내에서는 unresolved==resolved (string literal, ref 해석 X — RenderResources base delegate).
    47	        return getUnresolvedResource(ref)
    48	    }
    49	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapter.kt | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.session
     2	
     3	import com.android.ide.common.rendering.api.ILayoutPullParser
     4	import com.android.ide.common.rendering.api.ResourceNamespace
     5	import org.kxml2.io.KXmlParser
     6	import org.xmlpull.v1.XmlPullParser
     7	import org.xmlpull.v1.XmlPullParserException
     8	import java.io.Reader
     9	import java.nio.charset.StandardCharsets
    10	import java.nio.file.Path
    11	
    12	/**
    13	 * W2D7-RENDERSESSION — kxml2 `KXmlParser` 위에 `ILayoutPullParser` 인터페이스를 얇게 얹는 adapter.
    14	 *
    15	 * ILayoutPullParser 는 XmlPullParser 에 두 개의 추가 메서드만 얹는다:
    16	 *  - getViewCookie(): inflate 된 View ↔ XML source 간의 mapping 훅 (IDE 용). 본 경로에서 null.
    17	 *  - getLayoutNamespace(): 레이아웃이 속한 리소스 네임스페이스. 우리 fixture 는 app-local 이므로 RES_AUTO.
    18	 *
    19	 * layoutlib 은 `SessionParams.layoutDescription` 으로 본 어댑터를 소비하여 `next()` 를 반복 호출,
    20	 * `START_TAG` 에서 element name + attribute 들을 읽어 View 를 inflate 한다.
    21	 */
    22	class LayoutPullParserAdapter private constructor(
    23	    private val delegate: KXmlParser
    24	) : ILayoutPullParser, XmlPullParser by delegate {
    25	
    26	    override fun getViewCookie(): Any? = null
    27	
    28	    override fun getLayoutNamespace(): ResourceNamespace = ResourceNamespace.RES_AUTO
    29	
    30	    companion object {
    31	
    32	        /**
    33	         * Reader 로부터 파서 생성. 내부적으로 FEATURE_PROCESS_NAMESPACES=true 설정.
    34	         * 파서는 input 직후 START_DOCUMENT 위치에 있으므로 layoutlib 이 next() 로 진행.
    35	         */
    36	        @Throws(XmlPullParserException::class)
    37	        fun fromReader(reader: Reader): LayoutPullParserAdapter {
    38	            val parser = KXmlParser()
    39	            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    40	            parser.setInput(reader)
    41	            return LayoutPullParserAdapter(parser)
    42	        }
    43	
    44	        /** 파일 경로로부터 파서 생성 — UTF-8 로 읽어 fromReader 에 위임. */
    45	        @Throws(XmlPullParserException::class)
    46	        fun fromFile(path: Path): LayoutPullParserAdapter {
    47	            val reader = path.toFile().inputStream().reader(StandardCharsets.UTF_8).buffered()
    48	            return fromReader(reader)
    49	        }
    50	    }
    51	}

exec
/bin/bash -lc 'find fixture/sample-app/app/src/main/res -maxdepth 3 -type f | sort' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
fixture/sample-app/app/src/main/res/drawable/ic_sparkle.xml
fixture/sample-app/app/src/main/res/layout/activity_basic.xml
fixture/sample-app/app/src/main/res/layout/activity_custom_view.xml
fixture/sample-app/app/src/main/res/layout/activity_minimal.xml
fixture/sample-app/app/src/main/res/values/strings.xml
fixture/sample-app/app/src/main/res/values/themes.xml

exec
/bin/bash -lc "nl -ba fixture/sample-app/app/src/main/res/values/themes.xml | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	<?xml version="1.0" encoding="utf-8"?>
     2	<resources>
     3	    <!-- W1D1 fixture 는 Material 3 DayNight 사용. L1 의 merged-resources 경로(07 §2.1)가
     4	         androidx.appcompat + material 의 transitive AAR 리소스를 정확히 참조하는지 확인용. -->
     5	    <style name="Theme.AxpFixture" parent="Theme.Material3.DayNight.NoActionBar">
     6	        <item name="colorPrimary">#6750A4</item>
     7	        <item name="colorOnPrimary">#FFFFFF</item>
     8	        <item name="colorPrimaryContainer">#EADDFF</item>
     9	        <item name="colorOnSurface">#1C1B1F</item>
    10	        <item name="colorOnSurfaceVariant">#49454F</item>
    11	    </style>
    12	</resources>

exec
/bin/bash -lc "nl -ba fixture/sample-app/app/src/main/res/values/strings.xml | sed -n '1,160p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	<?xml version="1.0" encoding="utf-8"?>
     2	<resources>
     3	    <string name="app_name">AXP Fixture</string>
     4	    <string name="fixture_basic_title">Android XML Previewer</string>
     5	    <string name="fixture_basic_body">Fixture layout for plugin self-test. Renders through L1 layoutlib.</string>
     6	    <string name="fixture_basic_button">Render me</string>
     7	    <string name="fixture_custom_caption">Custom view via DexClassLoader (L3 escalation target)</string>
     8	</resources>

exec
/bin/bash -lc "nl -ba fixture/sample-app/app/src/main/res/drawable/ic_sparkle.xml | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	<?xml version="1.0" encoding="utf-8"?>
     2	<!-- 단순 VectorDrawable — L1 렌더 성공률 측정(07 §2.8)을 위한 기본 경로. -->
     3	<vector xmlns:android="http://schemas.android.com/apk/res/android"
     4	    android:width="24dp"
     5	    android:height="24dp"
     6	    android:viewportWidth="24"
     7	    android:viewportHeight="24"
     8	    android:tint="?attr/colorOnPrimary">
     9	    <path
    10	        android:fillColor="@android:color/white"
    11	        android:pathData="M12,2L14.09,8.26L20.5,9L15.5,13.26L16.82,19.02L12,15.77L7.18,19.02L8.5,13.26L3.5,9L9.91,8.26L12,2Z"/>
    12	</vector>

exec
/bin/bash -lc "nl -ba fixture/sample-app/app/src/main/AndroidManifest.xml | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	<?xml version="1.0" encoding="utf-8"?>
     2	<manifest xmlns:android="http://schemas.android.com/apk/res/android">
     3	
     4	    <application
     5	        android:allowBackup="true"
     6	        android:debuggable="true"
     7	        android:label="@string/app_name"
     8	        android:supportsRtl="true"
     9	        android:theme="@style/Theme.AxpFixture">
    10	
    11	        <activity
    12	            android:name=".MainActivity"
    13	            android:exported="true">
    14	            <intent-filter>
    15	                <action android:name="android.intent.action.MAIN" />
    16	                <category android:name="android.intent.category.LAUNCHER" />
    17	            </intent-filter>
    18	        </activity>
    19	
    20	    </application>
    21	</manifest>

exec
/bin/bash -lc 'unzip -p /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.google.android.material/material/1.12.0/e493b7ac7fc4b8ca48527e0199f9961652711ac3/material-1.12.0.aar classes.jar > /tmp/material-1.12.0-classes.jar' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
exec
/bin/bash -lc "javap -classpath /tmp/material-1.12.0-classes.jar -c -p com.google.android.material.internal.ThemeEnforcement | sed -n '1,280p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Compiled from "ThemeEnforcement.java"
public final class com.google.android.material.internal.ThemeEnforcement {
  private static final int[] APPCOMPAT_CHECK_ATTRS;

  private static final java.lang.String APPCOMPAT_THEME_NAME;

  private static final int[] MATERIAL_CHECK_ATTRS;

  private static final java.lang.String MATERIAL_THEME_NAME;

  private com.google.android.material.internal.ThemeEnforcement();
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return

  public static android.content.res.TypedArray obtainStyledAttributes(android.content.Context, android.util.AttributeSet, int[], int, int, int...);
    Code:
       0: aload_0
       1: aload_1
       2: iload_3
       3: iload         4
       5: invokestatic  #2                  // Method checkCompatibleTheme:(Landroid/content/Context;Landroid/util/AttributeSet;II)V
       8: aload_0
       9: aload_1
      10: aload_2
      11: iload_3
      12: iload         4
      14: aload         5
      16: invokestatic  #3                  // Method checkTextAppearance:(Landroid/content/Context;Landroid/util/AttributeSet;[III[I)V
      19: aload_0
      20: aload_1
      21: aload_2
      22: iload_3
      23: iload         4
      25: invokevirtual #4                  // Method android/content/Context.obtainStyledAttributes:(Landroid/util/AttributeSet;[III)Landroid/content/res/TypedArray;
      28: areturn

  public static androidx.appcompat.widget.TintTypedArray obtainTintedStyledAttributes(android.content.Context, android.util.AttributeSet, int[], int, int, int...);
    Code:
       0: aload_0
       1: aload_1
       2: iload_3
       3: iload         4
       5: invokestatic  #2                  // Method checkCompatibleTheme:(Landroid/content/Context;Landroid/util/AttributeSet;II)V
       8: aload_0
       9: aload_1
      10: aload_2
      11: iload_3
      12: iload         4
      14: aload         5
      16: invokestatic  #3                  // Method checkTextAppearance:(Landroid/content/Context;Landroid/util/AttributeSet;[III[I)V
      19: aload_0
      20: aload_1
      21: aload_2
      22: iload_3
      23: iload         4
      25: invokestatic  #5                  // Method androidx/appcompat/widget/TintTypedArray.obtainStyledAttributes:(Landroid/content/Context;Landroid/util/AttributeSet;[III)Landroidx/appcompat/widget/TintTypedArray;
      28: areturn

  private static void checkCompatibleTheme(android.content.Context, android.util.AttributeSet, int, int);
    Code:
       0: aload_0
       1: aload_1
       2: getstatic     #6                  // Field com/google/android/material/R$styleable.ThemeEnforcement:[I
       5: iload_2
       6: iload_3
       7: invokevirtual #4                  // Method android/content/Context.obtainStyledAttributes:(Landroid/util/AttributeSet;[III)Landroid/content/res/TypedArray;
      10: astore        4
      12: aload         4
      14: getstatic     #7                  // Field com/google/android/material/R$styleable.ThemeEnforcement_enforceMaterialTheme:I
      17: iconst_0
      18: invokevirtual #8                  // Method android/content/res/TypedArray.getBoolean:(IZ)Z
      21: istore        5
      23: aload         4
      25: invokevirtual #9                  // Method android/content/res/TypedArray.recycle:()V
      28: iload         5
      30: ifeq          84
      33: new           #10                 // class android/util/TypedValue
      36: dup
      37: invokespecial #11                 // Method android/util/TypedValue."<init>":()V
      40: astore        6
      42: aload_0
      43: invokevirtual #12                 // Method android/content/Context.getTheme:()Landroid/content/res/Resources$Theme;
      46: getstatic     #13                 // Field com/google/android/material/R$attr.isMaterialTheme:I
      49: aload         6
      51: iconst_1
      52: invokevirtual #14                 // Method android/content/res/Resources$Theme.resolveAttribute:(ILandroid/util/TypedValue;Z)Z
      55: istore        7
      57: iload         7
      59: ifeq          80
      62: aload         6
      64: getfield      #15                 // Field android/util/TypedValue.type:I
      67: bipush        18
      69: if_icmpne     84
      72: aload         6
      74: getfield      #16                 // Field android/util/TypedValue.data:I
      77: ifne          84
      80: aload_0
      81: invokestatic  #17                 // Method checkMaterialTheme:(Landroid/content/Context;)V
      84: aload_0
      85: invokestatic  #18                 // Method checkAppCompatTheme:(Landroid/content/Context;)V
      88: return

  private static void checkTextAppearance(android.content.Context, android.util.AttributeSet, int[], int, int, int...);
    Code:
       0: aload_0
       1: aload_1
       2: getstatic     #6                  // Field com/google/android/material/R$styleable.ThemeEnforcement:[I
       5: iload_3
       6: iload         4
       8: invokevirtual #4                  // Method android/content/Context.obtainStyledAttributes:(Landroid/util/AttributeSet;[III)Landroid/content/res/TypedArray;
      11: astore        6
      13: aload         6
      15: getstatic     #19                 // Field com/google/android/material/R$styleable.ThemeEnforcement_enforceTextAppearance:I
      18: iconst_0
      19: invokevirtual #8                  // Method android/content/res/TypedArray.getBoolean:(IZ)Z
      22: istore        7
      24: iload         7
      26: ifne          35
      29: aload         6
      31: invokevirtual #9                  // Method android/content/res/TypedArray.recycle:()V
      34: return
      35: aload         5
      37: ifnull        46
      40: aload         5
      42: arraylength
      43: ifne          69
      46: aload         6
      48: getstatic     #20                 // Field com/google/android/material/R$styleable.ThemeEnforcement_android_textAppearance:I
      51: iconst_m1
      52: invokevirtual #21                 // Method android/content/res/TypedArray.getResourceId:(II)I
      55: iconst_m1
      56: if_icmpeq     63
      59: iconst_1
      60: goto          64
      63: iconst_0
      64: istore        8
      66: goto          82
      69: aload_0
      70: aload_1
      71: aload_2
      72: iload_3
      73: iload         4
      75: aload         5
      77: invokestatic  #22                 // Method isCustomTextAppearanceValid:(Landroid/content/Context;Landroid/util/AttributeSet;[III[I)Z
      80: istore        8
      82: aload         6
      84: invokevirtual #9                  // Method android/content/res/TypedArray.recycle:()V
      87: iload         8
      89: ifne          102
      92: new           #23                 // class java/lang/IllegalArgumentException
      95: dup
      96: ldc           #24                 // String This component requires that you specify a valid TextAppearance attribute. Update your app theme to inherit from Theme.MaterialComponents (or a descendant).
      98: invokespecial #25                 // Method java/lang/IllegalArgumentException."<init>":(Ljava/lang/String;)V
     101: athrow
     102: return

  private static boolean isCustomTextAppearanceValid(android.content.Context, android.util.AttributeSet, int[], int, int, int...);
    Code:
       0: aload_0
       1: aload_1
       2: aload_2
       3: iload_3
       4: iload         4
       6: invokevirtual #4                  // Method android/content/Context.obtainStyledAttributes:(Landroid/util/AttributeSet;[III)Landroid/content/res/TypedArray;
       9: astore        6
      11: aload         5
      13: astore        7
      15: aload         7
      17: arraylength
      18: istore        8
      20: iconst_0
      21: istore        9
      23: iload         9
      25: iload         8
      27: if_icmpge     62
      30: aload         7
      32: iload         9
      34: iaload
      35: istore        10
      37: aload         6
      39: iload         10
      41: iconst_m1
      42: invokevirtual #21                 // Method android/content/res/TypedArray.getResourceId:(II)I
      45: iconst_m1
      46: if_icmpne     56
      49: aload         6
      51: invokevirtual #9                  // Method android/content/res/TypedArray.recycle:()V
      54: iconst_0
      55: ireturn
      56: iinc          9, 1
      59: goto          23
      62: aload         6
      64: invokevirtual #9                  // Method android/content/res/TypedArray.recycle:()V
      67: iconst_1
      68: ireturn

  public static void checkAppCompatTheme(android.content.Context);
    Code:
       0: aload_0
       1: getstatic     #26                 // Field APPCOMPAT_CHECK_ATTRS:[I
       4: ldc           #28                 // String Theme.AppCompat
       6: invokestatic  #29                 // Method checkTheme:(Landroid/content/Context;[ILjava/lang/String;)V
       9: return

  public static void checkMaterialTheme(android.content.Context);
    Code:
       0: aload_0
       1: getstatic     #30                 // Field MATERIAL_CHECK_ATTRS:[I
       4: ldc           #31                 // String Theme.MaterialComponents
       6: invokestatic  #29                 // Method checkTheme:(Landroid/content/Context;[ILjava/lang/String;)V
       9: return

  public static boolean isAppCompatTheme(android.content.Context);
    Code:
       0: aload_0
       1: getstatic     #26                 // Field APPCOMPAT_CHECK_ATTRS:[I
       4: invokestatic  #32                 // Method isTheme:(Landroid/content/Context;[I)Z
       7: ireturn

  public static boolean isMaterialTheme(android.content.Context);
    Code:
       0: aload_0
       1: getstatic     #30                 // Field MATERIAL_CHECK_ATTRS:[I
       4: invokestatic  #32                 // Method isTheme:(Landroid/content/Context;[I)Z
       7: ireturn

  public static boolean isMaterial3Theme(android.content.Context);
    Code:
       0: aload_0
       1: getstatic     #33                 // Field com/google/android/material/R$attr.isMaterial3Theme:I
       4: iconst_0
       5: invokestatic  #34                 // Method com/google/android/material/resources/MaterialAttributes.resolveBoolean:(Landroid/content/Context;IZ)Z
       8: ireturn

  private static boolean isTheme(android.content.Context, int[]);
    Code:
       0: aload_0
       1: aload_1
       2: invokevirtual #35                 // Method android/content/Context.obtainStyledAttributes:([I)Landroid/content/res/TypedArray;
       5: astore_2
       6: iconst_0
       7: istore_3
       8: iload_3
       9: aload_1
      10: arraylength
      11: if_icmpge     34
      14: aload_2
      15: iload_3
      16: invokevirtual #36                 // Method android/content/res/TypedArray.hasValue:(I)Z
      19: ifne          28
      22: aload_2
      23: invokevirtual #9                  // Method android/content/res/TypedArray.recycle:()V
      26: iconst_0
      27: ireturn
      28: iinc          3, 1
      31: goto          8
      34: aload_2
      35: invokevirtual #9                  // Method android/content/res/TypedArray.recycle:()V
      38: iconst_1
      39: ireturn

  private static void checkTheme(android.content.Context, int[], java.lang.String);
    Code:
       0: aload_0
       1: aload_1
       2: invokestatic  #32                 // Method isTheme:(Landroid/content/Context;[I)Z
       5: ifne          40
       8: new           #23                 // class java/lang/IllegalArgumentException
      11: dup
      12: new           #37                 // class java/lang/StringBuilder
      15: dup
      16: invokespecial #38                 // Method java/lang/StringBuilder."<init>":()V
      19: ldc           #39                 // String The style on this component requires your app theme to be
      21: invokevirtual #40                 // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
      24: aload_2
      25: invokevirtual #40                 // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
      28: ldc           #41                 // String  (or a descendant).
      30: invokevirtual #40                 // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;

exec
/bin/bash -lc "javap -classpath /tmp/material-1.12.0-classes.jar -c -p com.google.android.material.button.MaterialButton | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Compiled from "MaterialButton.java"
public class com.google.android.material.button.MaterialButton extends androidx.appcompat.widget.AppCompatButton implements android.widget.Checkable,com.google.android.material.shape.Shapeable {
  private static final int[] CHECKABLE_STATE_SET;

  private static final int[] CHECKED_STATE_SET;

  public static final int ICON_GRAVITY_START;

  public static final int ICON_GRAVITY_TEXT_START;

  public static final int ICON_GRAVITY_END;

  public static final int ICON_GRAVITY_TEXT_END;

  public static final int ICON_GRAVITY_TOP;

  public static final int ICON_GRAVITY_TEXT_TOP;

  private static final java.lang.String LOG_TAG;

  private static final int DEF_STYLE_RES;

  private final com.google.android.material.button.MaterialButtonHelper materialButtonHelper;

  private final java.util.LinkedHashSet<com.google.android.material.button.MaterialButton$OnCheckedChangeListener> onCheckedChangeListeners;

  private com.google.android.material.button.MaterialButton$OnPressedChangeListener onPressedChangeListenerInternal;

  private android.graphics.PorterDuff$Mode iconTintMode;

  private android.content.res.ColorStateList iconTint;

  private android.graphics.drawable.Drawable icon;

  private java.lang.String accessibilityClassName;

  private int iconSize;

  private int iconLeft;

  private int iconTop;

  private int iconPadding;

  private boolean checked;

  private boolean broadcasting;

  private int iconGravity;

  public com.google.android.material.button.MaterialButton(android.content.Context);
    Code:
       0: aload_0
       1: aload_1
       2: aconst_null
       3: invokespecial #1                  // Method "<init>":(Landroid/content/Context;Landroid/util/AttributeSet;)V
       6: return

  public com.google.android.material.button.MaterialButton(android.content.Context, android.util.AttributeSet);
    Code:
       0: aload_0
       1: aload_1
       2: aload_2
       3: getstatic     #2                  // Field com/google/android/material/R$attr.materialButtonStyle:I
       6: invokespecial #3                  // Method "<init>":(Landroid/content/Context;Landroid/util/AttributeSet;I)V
       9: return

  public com.google.android.material.button.MaterialButton(android.content.Context, android.util.AttributeSet, int);
    Code:
       0: aload_0
       1: aload_1
       2: aload_2
       3: iload_3
       4: getstatic     #4                  // Field DEF_STYLE_RES:I
       7: invokestatic  #5                  // Method com/google/android/material/theme/overlay/MaterialThemeOverlay.wrap:(Landroid/content/Context;Landroid/util/AttributeSet;II)Landroid/content/Context;
      10: aload_2
      11: iload_3
      12: invokespecial #6                  // Method androidx/appcompat/widget/AppCompatButton."<init>":(Landroid/content/Context;Landroid/util/AttributeSet;I)V
      15: aload_0
      16: new           #7                  // class java/util/LinkedHashSet
      19: dup
      20: invokespecial #8                  // Method java/util/LinkedHashSet."<init>":()V
      23: putfield      #9                  // Field onCheckedChangeListeners:Ljava/util/LinkedHashSet;
      26: aload_0
      27: iconst_0
      28: putfield      #10                 // Field checked:Z
      31: aload_0
      32: iconst_0
      33: putfield      #11                 // Field broadcasting:Z
      36: aload_0
      37: invokevirtual #12                 // Method getContext:()Landroid/content/Context;
      40: astore_1
      41: aload_1
      42: aload_2
      43: getstatic     #13                 // Field com/google/android/material/R$styleable.MaterialButton:[I
      46: iload_3
      47: getstatic     #4                  // Field DEF_STYLE_RES:I
      50: iconst_0
      51: newarray       int
      53: invokestatic  #14                 // Method com/google/android/material/internal/ThemeEnforcement.obtainStyledAttributes:(Landroid/content/Context;Landroid/util/AttributeSet;[III[I)Landroid/content/res/TypedArray;
      56: astore        4
      58: aload_0
      59: aload         4
      61: getstatic     #15                 // Field com/google/android/material/R$styleable.MaterialButton_iconPadding:I
      64: iconst_0
      65: invokevirtual #16                 // Method android/content/res/TypedArray.getDimensionPixelSize:(II)I
      68: putfield      #17                 // Field iconPadding:I
      71: aload_0
      72: aload         4
      74: getstatic     #18                 // Field com/google/android/material/R$styleable.MaterialButton_iconTintMode:I
      77: iconst_m1
      78: invokevirtual #19                 // Method android/content/res/TypedArray.getInt:(II)I
      81: getstatic     #20                 // Field android/graphics/PorterDuff$Mode.SRC_IN:Landroid/graphics/PorterDuff$Mode;
      84: invokestatic  #21                 // Method com/google/android/material/internal/ViewUtils.parseTintMode:(ILandroid/graphics/PorterDuff$Mode;)Landroid/graphics/PorterDuff$Mode;
      87: putfield      #22                 // Field iconTintMode:Landroid/graphics/PorterDuff$Mode;
      90: aload_0
      91: aload_0
      92: invokevirtual #12                 // Method getContext:()Landroid/content/Context;
      95: aload         4
      97: getstatic     #23                 // Field com/google/android/material/R$styleable.MaterialButton_iconTint:I
     100: invokestatic  #24                 // Method com/google/android/material/resources/MaterialResources.getColorStateList:(Landroid/content/Context;Landroid/content/res/TypedArray;I)Landroid/content/res/ColorStateList;
     103: putfield      #25                 // Field iconTint:Landroid/content/res/ColorStateList;
     106: aload_0
     107: aload_0
     108: invokevirtual #12                 // Method getContext:()Landroid/content/Context;
     111: aload         4
     113: getstatic     #26                 // Field com/google/android/material/R$styleable.MaterialButton_icon:I
     116: invokestatic  #27                 // Method com/google/android/material/resources/MaterialResources.getDrawable:(Landroid/content/Context;Landroid/content/res/TypedArray;I)Landroid/graphics/drawable/Drawable;
     119: putfield      #28                 // Field icon:Landroid/graphics/drawable/Drawable;
     122: aload_0
     123: aload         4
     125: getstatic     #29                 // Field com/google/android/material/R$styleable.MaterialButton_iconGravity:I
     128: iconst_1
     129: invokevirtual #31                 // Method android/content/res/TypedArray.getInteger:(II)I
     132: putfield      #32                 // Field iconGravity:I
     135: aload_0
     136: aload         4
     138: getstatic     #33                 // Field com/google/android/material/R$styleable.MaterialButton_iconSize:I
     141: iconst_0
     142: invokevirtual #16                 // Method android/content/res/TypedArray.getDimensionPixelSize:(II)I
     145: putfield      #34                 // Field iconSize:I
     148: aload_1
     149: aload_2
     150: iload_3
     151: getstatic     #4                  // Field DEF_STYLE_RES:I
     154: invokestatic  #35                 // Method com/google/android/material/shape/ShapeAppearanceModel.builder:(Landroid/content/Context;Landroid/util/AttributeSet;II)Lcom/google/android/material/shape/ShapeAppearanceModel$Builder;
     157: invokevirtual #36                 // Method com/google/android/material/shape/ShapeAppearanceModel$Builder.build:()Lcom/google/android/material/shape/ShapeAppearanceModel;
     160: astore        5
     162: aload_0
     163: new           #37                 // class com/google/android/material/button/MaterialButtonHelper
     166: dup
     167: aload_0
     168: aload         5
     170: invokespecial #38                 // Method com/google/android/material/button/MaterialButtonHelper."<init>":(Lcom/google/android/material/button/MaterialButton;Lcom/google/android/material/shape/ShapeAppearanceModel;)V
     173: putfield      #39                 // Field materialButtonHelper:Lcom/google/android/material/button/MaterialButtonHelper;
     176: aload_0
     177: getfield      #39                 // Field materialButtonHelper:Lcom/google/android/material/button/MaterialButtonHelper;
     180: aload         4
     182: invokevirtual #40                 // Method com/google/android/material/button/MaterialButtonHelper.loadFromAttributes:(Landroid/content/res/TypedArray;)V
     185: aload         4
     187: invokevirtual #41                 // Method android/content/res/TypedArray.recycle:()V
     190: aload_0
     191: aload_0
     192: getfield      #17                 // Field iconPadding:I
     195: invokevirtual #42                 // Method setCompoundDrawablePadding:(I)V
     198: aload_0
     199: aload_0
     200: getfield      #28                 // Field icon:Landroid/graphics/drawable/Drawable;
     203: ifnull        210
     206: iconst_1
     207: goto          211
     210: iconst_0
     211: invokespecial #43                 // Method updateIcon:(Z)V
     214: return

  java.lang.String getA11yClassName();
    Code:
       0: aload_0
       1: getfield      #44                 // Field accessibilityClassName:Ljava/lang/String;
       4: invokestatic  #45                 // Method android/text/TextUtils.isEmpty:(Ljava/lang/CharSequence;)Z
       7: ifne          15
      10: aload_0
      11: getfield      #44                 // Field accessibilityClassName:Ljava/lang/String;
      14: areturn
      15: aload_0
      16: invokevirtual #46                 // Method isCheckable:()Z
      19: ifeq          27
      22: ldc           #47                 // class android/widget/CompoundButton
      24: goto          29
      27: ldc           #48                 // class android/widget/Button
      29: invokevirtual #49                 // Method java/lang/Class.getName:()Ljava/lang/String;
      32: areturn

  void setA11yClassName(java.lang.String);
    Code:
       0: aload_0
       1: aload_1
       2: putfield      #44                 // Field accessibilityClassName:Ljava/lang/String;
       5: return

  public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo);
    Code:
       0: aload_0
       1: aload_1
       2: invokespecial #50                 // Method androidx/appcompat/widget/AppCompatButton.onInitializeAccessibilityNodeInfo:(Landroid/view/accessibility/AccessibilityNodeInfo;)V
       5: aload_1
       6: aload_0
       7: invokevirtual #51                 // Method getA11yClassName:()Ljava/lang/String;
      10: invokevirtual #52                 // Method android/view/accessibility/AccessibilityNodeInfo.setClassName:(Ljava/lang/CharSequence;)V
      13: aload_1
      14: aload_0
      15: invokevirtual #46                 // Method isCheckable:()Z
      18: invokevirtual #53                 // Method android/view/accessibility/AccessibilityNodeInfo.setCheckable:(Z)V
      21: aload_1
      22: aload_0
      23: invokevirtual #54                 // Method isChecked:()Z
      26: invokevirtual #55                 // Method android/view/accessibility/AccessibilityNodeInfo.setChecked:(Z)V
      29: aload_1
      30: aload_0
      31: invokevirtual #56                 // Method isClickable:()Z

exec
/bin/bash -lc "javap -classpath /tmp/material-1.12.0-classes.jar -c -p com.google.android.material.theme.overlay.MaterialThemeOverlay | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Compiled from "MaterialThemeOverlay.java"
public class com.google.android.material.theme.overlay.MaterialThemeOverlay {
  private static final int[] ANDROID_THEME_OVERLAY_ATTRS;

  private static final int[] MATERIAL_THEME_OVERLAY_ATTR;

  private com.google.android.material.theme.overlay.MaterialThemeOverlay();
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return

  public static android.content.Context wrap(android.content.Context, android.util.AttributeSet, int, int);
    Code:
       0: aload_0
       1: aload_1
       2: iload_2
       3: iload_3
       4: invokestatic  #2                  // Method obtainMaterialThemeOverlayId:(Landroid/content/Context;Landroid/util/AttributeSet;II)I
       7: istore        4
       9: aload_0
      10: instanceof    #3                  // class androidx/appcompat/view/ContextThemeWrapper
      13: ifeq          32
      16: aload_0
      17: checkcast     #3                  // class androidx/appcompat/view/ContextThemeWrapper
      20: invokevirtual #4                  // Method androidx/appcompat/view/ContextThemeWrapper.getThemeResId:()I
      23: iload         4
      25: if_icmpne     32
      28: iconst_1
      29: goto          33
      32: iconst_0
      33: istore        5
      35: iload         4
      37: ifeq          45
      40: iload         5
      42: ifeq          47
      45: aload_0
      46: areturn
      47: new           #3                  // class androidx/appcompat/view/ContextThemeWrapper
      50: dup
      51: aload_0
      52: iload         4
      54: invokespecial #5                  // Method androidx/appcompat/view/ContextThemeWrapper."<init>":(Landroid/content/Context;I)V
      57: astore        6
      59: aload_0
      60: aload_1
      61: invokestatic  #6                  // Method obtainAndroidThemeOverlayId:(Landroid/content/Context;Landroid/util/AttributeSet;)I
      64: istore        7
      66: iload         7
      68: ifeq          82
      71: aload         6
      73: invokevirtual #7                  // Method android/content/Context.getTheme:()Landroid/content/res/Resources$Theme;
      76: iload         7
      78: iconst_1
      79: invokevirtual #8                  // Method android/content/res/Resources$Theme.applyStyle:(IZ)V
      82: aload         6
      84: areturn

  private static int obtainAndroidThemeOverlayId(android.content.Context, android.util.AttributeSet);
    Code:
       0: aload_0
       1: aload_1
       2: getstatic     #9                  // Field ANDROID_THEME_OVERLAY_ATTRS:[I
       5: invokevirtual #10                 // Method android/content/Context.obtainStyledAttributes:(Landroid/util/AttributeSet;[I)Landroid/content/res/TypedArray;
       8: astore_2
       9: aload_2
      10: iconst_0
      11: iconst_0
      12: invokevirtual #11                 // Method android/content/res/TypedArray.getResourceId:(II)I
      15: istore_3
      16: aload_2
      17: iconst_1
      18: iconst_0
      19: invokevirtual #11                 // Method android/content/res/TypedArray.getResourceId:(II)I
      22: istore        4
      24: aload_2
      25: invokevirtual #12                 // Method android/content/res/TypedArray.recycle:()V
      28: iload_3
      29: ifeq          36
      32: iload_3
      33: goto          38
      36: iload         4
      38: ireturn

  private static int obtainMaterialThemeOverlayId(android.content.Context, android.util.AttributeSet, int, int);
    Code:
       0: aload_0
       1: aload_1
       2: getstatic     #13                 // Field MATERIAL_THEME_OVERLAY_ATTR:[I
       5: iload_2
       6: iload_3
       7: invokevirtual #14                 // Method android/content/Context.obtainStyledAttributes:(Landroid/util/AttributeSet;[III)Landroid/content/res/TypedArray;
      10: astore        4
      12: aload         4
      14: iconst_0
      15: iconst_0
      16: invokevirtual #11                 // Method android/content/res/TypedArray.getResourceId:(II)I
      19: istore        5
      21: aload         4
      23: invokevirtual #12                 // Method android/content/res/TypedArray.recycle:()V
      26: iload         5
      28: ireturn

  static {};
    Code:
       0: iconst_2
       1: newarray       int
       3: dup
       4: iconst_0
       5: ldc           #16                 // int 16842752
       7: iastore
       8: dup
       9: iconst_1
      10: getstatic     #17                 // Field com/google/android/material/R$attr.theme:I
      13: iastore
      14: putstatic     #9                  // Field ANDROID_THEME_OVERLAY_ATTRS:[I
      17: iconst_1
      18: newarray       int
      20: dup
      21: iconst_0
      22: getstatic     #18                 // Field com/google/android/material/R$attr.materialThemeOverlay:I
      25: iastore
      26: putstatic     #13                 // Field MATERIAL_THEME_OVERLAY_ATTR:[I
      29: return
}

exec
/bin/bash -lc "javap -classpath /tmp/material-1.12.0-classes.jar -c -p com.google.android.material.internal.ThemeEnforcement | sed -n '280,420p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
      30: invokevirtual #40                 // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
      33: invokevirtual #42                 // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
      36: invokespecial #25                 // Method java/lang/IllegalArgumentException."<init>":(Ljava/lang/String;)V
      39: athrow
      40: return

  static {};
    Code:
       0: iconst_1
       1: newarray       int
       3: dup
       4: iconst_0
       5: getstatic     #43                 // Field com/google/android/material/R$attr.colorPrimary:I
       8: iastore
       9: putstatic     #26                 // Field APPCOMPAT_CHECK_ATTRS:[I
      12: iconst_1
      13: newarray       int
      15: dup
      16: iconst_0
      17: getstatic     #44                 // Field com/google/android/material/R$attr.colorPrimaryVariant:I
      20: iastore
      21: putstatic     #30                 // Field MATERIAL_CHECK_ATTRS:[I
      24: return
}

exec
/bin/bash -lc "unzip -l /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.google.android.material/material/1.12.0/e493b7ac7fc4b8ca48527e0199f9961652711ac3/material-1.12.0.aar | rg '("'^|/)libs/|'"\\.jar"'$'"' | head -50" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
  1872366  1980-02-01 00:00   classes.jar

exec
/bin/bash -lc "unzip -l /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.constraintlayout/constraintlayout/2.1.4/b7ad5eca28aa9e7c4ea1a7e256c7ee4401a031f/constraintlayout-2.1.4.aar | rg '("'^|/)libs/|'"\\.jar"'$'"' | head -50" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   473442  1980-02-01 00:00   classes.jar

exec
/bin/bash -lc "unzip -l /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.appcompat/appcompat/1.6.1/6c7577004b7ebbee5ed87d512b578dd20e3c8c31/appcompat-1.6.1.aar | rg '("'^|/)libs/|'"\\.jar"'$'"' | head -50" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   764869  1980-00-00 04:08   classes.jar
    42314  1980-00-00 04:08   lint.jar

exec
/bin/bash -lc "unzip -l /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.appcompat/appcompat-resources/1.6.1/3fe43025e50b556f319b1ff82a730d5a376a31e/appcompat-resources-1.6.1.aar | rg '("'^|/)libs/|'"\\.jar"'$'"' | head -50" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
    69360  1980-00-00 04:08   classes.jar

exec
/bin/bash -lc "unzip -l fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar | rg 'com/fixture/R' | head -30" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
      252  1981-01-01 01:01   com/fixture/R$drawable.class
      361  1981-01-01 01:01   com/fixture/R$id.class
      296  1981-01-01 01:01   com/fixture/R$layout.class
      421  1981-01-01 01:01   com/fixture/R$string.class
      252  1981-01-01 01:01   com/fixture/R$style.class
      360  1981-01-01 01:01   com/fixture/R.class

exec
/bin/bash -lc "javap -classpath fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar -p -constants 'androidx.constraintlayout.widget.R"'$styleable'"' | rg 'ConstraintLayout_Layout_layout_constraintTop_toTopOf|ConstraintLayout_Layout_layout_constraintStart_toStartOf|ConstraintLayout_Layout_layout_constraintEnd_toEndOf'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
  public static int ConstraintLayout_Layout_layout_constraintEnd_toEndOf = 70;
  public static int ConstraintLayout_Layout_layout_constraintStart_toStartOf = 90;
  public static int ConstraintLayout_Layout_layout_constraintTop_toTopOf = 94;

exec
/bin/bash -lc "javap -classpath fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar -p -constants 'androidx.constraintlayout.widget.R"'$attr'"' | rg 'layout_constraintTop_toTopOf|layout_constraintStart_toStartOf|layout_constraintEnd_toEndOf'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
  public static int layout_constraintEnd_toEndOf = 2130903681;
  public static int layout_constraintStart_toStartOf = 2130903701;
  public static int layout_constraintTop_toTopOf = 2130903705;

exec
/bin/bash -lc "javap -classpath fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar -p -constants 'com.fixture.R"'$string'"'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
public final class com.fixture.R$string {
  public static int app_name = 2131623964;
  public static int fixture_basic_body = 2131623988;
  public static int fixture_basic_button = 2131623989;
  public static int fixture_basic_title = 2131623990;
  public static int fixture_custom_caption = 2131623991;
  private com.fixture.R$string();
}

exec
/bin/bash -lc "javap -classpath fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar -p -constants 'com.google.android.material.R"'$attr'"' | rg 'colorPrimary|isMaterialTheme|materialButtonStyle'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
  public static int colorPrimary = 2130903315;
  public static int colorPrimaryContainer = 2130903316;
  public static int colorPrimaryDark = 2130903317;
  public static int colorPrimaryFixed = 2130903318;
  public static int colorPrimaryFixedDim = 2130903319;
  public static int colorPrimaryInverse = 2130903320;
  public static int colorPrimarySurface = 2130903321;
  public static int colorPrimaryVariant = 2130903322;
  public static int isMaterialTheme = 2130903618;
  public static int materialButtonStyle = 2130903769;

exec
/bin/bash -lc 'javap -classpath server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar:/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar -c -p com.android.layoutlib.bridge.android.BridgeContext | rg -n "getApplicationId|getResourcePackage|resolveResourceId|getOrGenerateResourceId|ResourceNamespace" | head -80' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 150ms:
85:  private final com.android.ide.common.rendering.api.ResourceNamespace mAppCompatNamespace;
274:     365: getstatic     #335                // Field com/android/ide/common/rendering/api/ResourceNamespace.APPCOMPAT:Lcom/android/ide/common/rendering/api/ResourceNamespace;
275:     368: putfield      #337                // Field mAppCompatNamespace:Lcom/android/ide/common/rendering/api/ResourceNamespace;
278:     375: getstatic     #340                // Field com/android/ide/common/rendering/api/ResourceNamespace.APPCOMPAT_LEGACY:Lcom/android/ide/common/rendering/api/ResourceNamespace;
279:     378: putfield      #337                // Field mAppCompatNamespace:Lcom/android/ide/common/rendering/api/ResourceNamespace;
282:     385: getstatic     #343                // Field com/android/ide/common/rendering/api/ResourceNamespace.RES_AUTO:Lcom/android/ide/common/rendering/api/ResourceNamespace;
283:     388: putfield      #337                // Field mAppCompatNamespace:Lcom/android/ide/common/rendering/api/ResourceNamespace;
468:       1: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
475:      16: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
628:       1: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
640:      23: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
652:       1: invokevirtual #584                // Method com/android/ide/common/rendering/api/ResourceReference.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
653:       4: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
654:       7: invokevirtual #588                // Method com/android/ide/common/rendering/api/ResourceNamespace.equals:(Ljava/lang/Object;)Z
681:      64: invokevirtual #584                // Method com/android/ide/common/rendering/api/ResourceReference.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
682:      67: invokespecial #602                // Method com/android/layoutlib/bridge/android/BridgeXmlBlockParser."<init>":(Lorg/xmlpull/v1/XmlPullParser;Lcom/android/layoutlib/bridge/android/BridgeContext;Lcom/android/ide/common/rendering/api/ResourceNamespace;)V
730:     166: invokevirtual #584                // Method com/android/ide/common/rendering/api/ResourceReference.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
731:     169: invokespecial #602                // Method com/android/layoutlib/bridge/android/BridgeXmlBlockParser."<init>":(Lorg/xmlpull/v1/XmlPullParser;Lcom/android/layoutlib/bridge/android/BridgeContext;Lcom/android/ide/common/rendering/api/ResourceNamespace;)V
1382:      54: invokevirtual #972                // Method com/android/layoutlib/bridge/android/BridgeXmlBlockParser.getFileResourceNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1389:      69: invokevirtual #978                // Method com/android/ide/common/rendering/api/LayoutlibCallback.getImplicitNamespaces:()Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;
1390:      72: invokespecial #981                // Method com/android/layoutlib/bridge/android/XmlPullParserResolver."<init>":(Lorg/xmlpull/v1/XmlPullParser;Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;)V
1396:      87: getstatic     #343                // Field com/android/ide/common/rendering/api/ResourceNamespace.RES_AUTO:Lcom/android/ide/common/rendering/api/ResourceNamespace;
1398:      92: getstatic     #987                // Field com/android/ide/common/rendering/api/ResourceNamespace$Resolver.EMPTY_RESOLVER:Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;
1411:     122: getstatic     #343                // Field com/android/ide/common/rendering/api/ResourceNamespace.RES_AUTO:Lcom/android/ide/common/rendering/api/ResourceNamespace;
1413:     127: getstatic     #987                // Field com/android/ide/common/rendering/api/ResourceNamespace$Resolver.EMPTY_RESOLVER:Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;
1443:     188: invokespecial #1007               // Method com/android/layoutlib/bridge/android/UnresolvedResourceValue."<init>":(Ljava/lang/String;Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;)V
1516:     359: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1523:     375: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1630:     614: invokevirtual #1034               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1631:     617: invokevirtual #1037               // Method com/android/ide/common/rendering/api/ResourceNamespace.getXmlNamespaceUri:()Ljava/lang/String;
1636:     631: invokevirtual #1034               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1637:     634: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
1697:     775: invokevirtual #1034               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1698:     778: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
1764:     949: invokevirtual #1034               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1768:     959: invokevirtual #1076               // Method android/content/res/BridgeTypedArray.bridgeSetValue:(ILjava/lang/String;Lcom/android/ide/common/rendering/api/ResourceNamespace;ILcom/android/ide/common/rendering/api/ResourceValue;)V
1774:     973: invokevirtual #1034               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1784:     995: invokespecial #1007               // Method com/android/layoutlib/bridge/android/UnresolvedResourceValue."<init>":(Ljava/lang/String;Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;)V
1786:    1001: invokevirtual #1076               // Method android/content/res/BridgeTypedArray.bridgeSetValue:(ILjava/lang/String;Lcom/android/ide/common/rendering/api/ResourceNamespace;ILcom/android/ide/common/rendering/api/ResourceValue;)V
1809:      18: invokevirtual #1118               // Method com/android/ide/common/rendering/api/LayoutlibCallback.getApplicationId:()Ljava/lang/String;
1902:     132: invokevirtual #1034               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1906:     142: invokevirtual #1076               // Method android/content/res/BridgeTypedArray.bridgeSetValue:(ILjava/lang/String;Lcom/android/ide/common/rendering/api/ResourceNamespace;ILcom/android/ide/common/rendering/api/ResourceValue;)V
1939:      34: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1946:      50: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1972:       1: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1979:      14: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1999:      35: invokevirtual #1161               // Method com/android/ide/common/rendering/api/LayoutlibCallback.getOrGenerateResourceId:(Lcom/android/ide/common/rendering/api/ResourceReference;)I
2005:       1: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
2012:      14: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
2032:      12: invokevirtual #584                // Method com/android/ide/common/rendering/api/ResourceReference.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
2033:      15: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
2034:      18: invokevirtual #588                // Method com/android/ide/common/rendering/api/ResourceNamespace.equals:(Ljava/lang/Object;)Z
2048:      48: invokevirtual #1161               // Method com/android/ide/common/rendering/api/LayoutlibCallback.getOrGenerateResourceId:(Lcom/android/ide/common/rendering/api/ResourceReference;)I
2077:       4: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
2080:       9: invokespecial #1185               // Method com/android/ide/common/rendering/api/ResourceReference."<init>":(Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/resources/ResourceType;Ljava/lang/String;)V
2096:       5: getfield      #337                // Field mAppCompatNamespace:Lcom/android/ide/common/rendering/api/ResourceNamespace;
2099:      10: invokespecial #1185               // Method com/android/ide/common/rendering/api/ResourceReference."<init>":(Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/resources/ResourceType;Ljava/lang/String;)V
2998:      63: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
3002:      75: invokespecial #1547               // Method com/android/ide/common/rendering/api/ResourceValueImpl."<init>":(Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/resources/ResourceType;Ljava/lang/String;Ljava/lang/String;)V
3009:      94: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
3013:     106: invokespecial #1547               // Method com/android/ide/common/rendering/api/ResourceValueImpl."<init>":(Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/resources/ResourceType;Ljava/lang/String;Ljava/lang/String;)V
3020:     125: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
3024:     137: invokespecial #1547               // Method com/android/ide/common/rendering/api/ResourceValueImpl."<init>":(Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/resources/ResourceType;Ljava/lang/String;Ljava/lang/String;)V
3031:     156: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
3035:     168: invokespecial #1547               // Method com/android/ide/common/rendering/api/ResourceValueImpl."<init>":(Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/resources/ResourceType;Ljava/lang/String;Ljava/lang/String;)V
3042:     187: getstatic     #587                // Field com/android/ide/common/rendering/api/ResourceNamespace.ANDROID:Lcom/android/ide/common/rendering/api/ResourceNamespace;
3046:     199: invokespecial #1547               // Method com/android/ide/common/rendering/api/ResourceValueImpl."<init>":(Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/resources/ResourceType;Ljava/lang/String;Ljava/lang/String;)V

exec
/bin/bash -lc "nl -ba fixture/sample-app/settings.gradle.kts | sed -n '1,160p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	// W1D1 fixture — Android Gradle Project 로 독립 빌드.
     2	// 본 프로젝트는 :app 단일 모듈. 서버 Gradle 빌드와는 별개로 유지.
     3	
     4	pluginManagement {
     5	    repositories {
     6	        google()
     7	        mavenCentral()
     8	        gradlePluginPortal()
     9	    }
    10	}
    11	
    12	dependencyResolutionManagement {
    13	    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    14	    repositories {
    15	        google()
    16	        mavenCentral()
    17	    }
    18	}
    19	
    20	rootProject.name = "axp-fixture"
    21	include(":app")

exec
/bin/bash -lc "nl -ba fixture/sample-app/build.gradle.kts | sed -n '1,200p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	// W1D1 fixture 루트.
     2	// Android Gradle Plugin 8.x + Kotlin 1.9.23 — server 모듈과 Kotlin 버전 일치.
     3	
     4	plugins {
     5	    id("com.android.application") version "8.3.2" apply false
     6	    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
     7	}

exec
/bin/bash -lc "rg -n \"com.android.application|agp|androidGradle|kotlin\" fixture/sample-app gradle server -g '*.toml' -g '*.kts' -g '*.properties'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 exited 2 in 0ms:
rg: gradle: No such file or directory (os error 2)
fixture/sample-app/build.gradle.kts:5:    id("com.android.application") version "8.3.2" apply false
fixture/sample-app/build.gradle.kts:6:    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
fixture/sample-app/gradle/libs.versions.toml:10:kotlin            = "1.9.23"            # Kotlin 1.9.x — 2.0은 K2 컴파일러 이슈로 v1에서 회피
fixture/sample-app/gradle/libs.versions.toml:14:kotlinx-coroutines = "1.8.0"
fixture/sample-app/gradle/libs.versions.toml:15:kotlinx-serialization = "1.6.3"
fixture/sample-app/gradle/libs.versions.toml:28:kotlinx-coroutines-test = "1.8.0"
fixture/sample-app/gradle/libs.versions.toml:45:kotlin-stdlib          = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
fixture/sample-app/gradle/libs.versions.toml:46:kotlin-reflect         = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }
fixture/sample-app/gradle/libs.versions.toml:47:kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
fixture/sample-app/gradle/libs.versions.toml:48:kotlinx-coroutines-jdk8 = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8", version.ref = "kotlinx-coroutines" }
fixture/sample-app/gradle/libs.versions.toml:49:kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
fixture/sample-app/gradle/libs.versions.toml:59:ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
fixture/sample-app/gradle/libs.versions.toml:71:kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines-test" }
fixture/sample-app/gradle/libs.versions.toml:80:kotlin-jvm             = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
fixture/sample-app/gradle/libs.versions.toml:81:kotlin-serialization   = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
fixture/sample-app/gradle/libs.versions.toml:86:                 "ktor-server-content-negotiation", "ktor-serialization-kotlinx-json"]
fixture/sample-app/gradle/libs.versions.toml:88:                 "kotest-assertions", "kotlinx-coroutines-test"]
server/protocol/build.gradle.kts:1:// :protocol — 의존성-free (kotlin + kotlinx.serialization 만).
server/protocol/build.gradle.kts:11:    id("axp.kotlin-common")
server/protocol/build.gradle.kts:12:    id("org.jetbrains.kotlin.plugin.serialization")
server/protocol/build.gradle.kts:16:    api(libs.kotlinx.serialization.json)
fixture/sample-app/gradle.properties:13:kotlin.code.style=official
fixture/sample-app/app/build.gradle.kts:2:    id("com.android.application")
fixture/sample-app/app/build.gradle.kts:3:    id("org.jetbrains.kotlin.android")
fixture/sample-app/app/build.gradle.kts:34:// AGP 8.x 에서 `kotlinOptions` 블록은 deprecated (향후 제거). canonical 대체:
fixture/sample-app/app/build.gradle.kts:35:// kotlin-android plugin 의 kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }
fixture/sample-app/app/build.gradle.kts:36:kotlin {
fixture/sample-app/app/build.gradle.kts:38:        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
server/cli/build.gradle.kts:4:    id("axp.kotlin-common")
server/cli/build.gradle.kts:5:    id("org.jetbrains.kotlin.plugin.serialization")
server/cli/build.gradle.kts:15:    implementation(libs.kotlinx.coroutines.core)
server/cli/build.gradle.kts:16:    implementation(libs.kotlinx.serialization.json)
server/gradle/libs.versions.toml:10:kotlin            = "1.9.23"            # Kotlin 1.9.x — 2.0은 K2 컴파일러 이슈로 v1에서 회피
server/gradle/libs.versions.toml:14:kotlinx-coroutines = "1.8.0"
server/gradle/libs.versions.toml:15:kotlinx-serialization = "1.6.3"
server/gradle/libs.versions.toml:28:kotlinx-coroutines-test = "1.8.0"
server/gradle/libs.versions.toml:45:kotlin-stdlib          = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
server/gradle/libs.versions.toml:46:kotlin-reflect         = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }
server/gradle/libs.versions.toml:47:kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
server/gradle/libs.versions.toml:48:kotlinx-coroutines-jdk8 = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8", version.ref = "kotlinx-coroutines" }
server/gradle/libs.versions.toml:49:kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
server/gradle/libs.versions.toml:59:ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
server/gradle/libs.versions.toml:71:kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines-test" }
server/gradle/libs.versions.toml:80:kotlin-jvm             = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
server/gradle/libs.versions.toml:81:kotlin-serialization   = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
server/gradle/libs.versions.toml:86:                 "ktor-server-content-negotiation", "ktor-serialization-kotlinx-json"]
server/gradle/libs.versions.toml:88:                 "kotest-assertions", "kotlinx-coroutines-test"]
server/render-core/build.gradle.kts:5:    id("axp.kotlin-common")
server/render-core/build.gradle.kts:6:    id("org.jetbrains.kotlin.plugin.serialization")
server/render-core/build.gradle.kts:11:    implementation(libs.kotlinx.coroutines.core)
server/render-core/build.gradle.kts:12:    implementation(libs.kotlinx.coroutines.jdk8)
server/render-core/build.gradle.kts:13:    implementation(libs.kotlinx.serialization.json)
server/emulator-harness/build.gradle.kts:6:    id("axp.kotlin-common")
server/emulator-harness/build.gradle.kts:7:    id("org.jetbrains.kotlin.plugin.serialization")
server/emulator-harness/build.gradle.kts:13:    implementation(libs.kotlinx.coroutines.core)
server/emulator-harness/build.gradle.kts:14:    implementation(libs.kotlinx.serialization.json)
server/http-server/build.gradle.kts:4:    id("axp.kotlin-common")
server/http-server/build.gradle.kts:5:    id("org.jetbrains.kotlin.plugin.serialization")
server/http-server/build.gradle.kts:13:    implementation(libs.kotlinx.coroutines.core)
server/http-server/build.gradle.kts:14:    implementation(libs.kotlinx.serialization.json)
server/buildSrc/build.gradle.kts:6://       kotlin-gradle-plugin 을 명시 버전으로 고정. 버전 동기화는 한 줄 바꿈으로 유지 가능.
server/buildSrc/build.gradle.kts:8:// libs.versions.toml 의 `kotlin = "1.9.23"` 과 반드시 동일한 값을 여기에 유지.
server/buildSrc/build.gradle.kts:11:    `kotlin-dsl`
server/buildSrc/build.gradle.kts:19:val kotlinVersion = "1.9.23"  // gradle/libs.versions.toml 의 kotlin 버전과 반드시 일치
server/buildSrc/build.gradle.kts:28:        .firstOrNull { it.trimStart().startsWith("kotlin ") && it.contains("=") && !it.contains("-") }
server/buildSrc/build.gradle.kts:29:        ?: error("libs.versions.toml 에서 kotlin 버전 라인을 찾을 수 없음")
server/buildSrc/build.gradle.kts:35:check(catalogKotlinVersion == kotlinVersion) {
server/buildSrc/build.gradle.kts:36:    "buildSrc 의 kotlinVersion='$kotlinVersion' 이 libs.versions.toml 의 kotlin='$catalogKotlinVersion' 과 불일치.\n" +
server/buildSrc/build.gradle.kts:41:    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
server/buildSrc/build.gradle.kts:42:    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
server/mcp-server/build.gradle.kts:5:    id("axp.kotlin-common")
server/mcp-server/build.gradle.kts:6:    id("org.jetbrains.kotlin.plugin.serialization")
server/mcp-server/build.gradle.kts:18:    implementation(libs.kotlinx.coroutines.core)
server/mcp-server/build.gradle.kts:19:    implementation(libs.kotlinx.serialization.json)
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:1:// axp.kotlin-common — 모든 Kotlin JVM 모듈의 공통 컨벤션
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:3:// 근거: 08 §5 item 5. 모든 서브모듈이 동일한 JVM target + kotlin stdlib + 테스트 러너.
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:4:// 각 모듈에서 `plugins { id("axp.kotlin-common") }` 로 적용.
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:7:    id("org.jetbrains.kotlin.jvm")
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:15:kotlin {
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:20:    add("implementation", libs.findLibrary("kotlin-stdlib").get())
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:28:tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:32:            "-opt-in=kotlin.RequiresOptIn"
server/layoutlib-worker/build.gradle.kts:6:    id("axp.kotlin-common")
server/layoutlib-worker/build.gradle.kts:7:    id("org.jetbrains.kotlin.plugin.serialization")
server/layoutlib-worker/build.gradle.kts:14:    implementation(libs.kotlinx.coroutines.core)
server/layoutlib-worker/build.gradle.kts:15:    implementation(libs.kotlinx.serialization.json)

exec
/bin/bash -lc 'ls -l fixture/sample-app/app/build/axp/runtime-classpath.txt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 exited 2 in 0ms:
ls: cannot access 'fixture/sample-app/app/build/axp/runtime-classpath.txt': No such file or directory

exec
/bin/bash -lc 'find fixture/sample-app/app/src/main -type f | sort' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
fixture/sample-app/app/src/main/AndroidManifest.xml
fixture/sample-app/app/src/main/java/com/fixture/DummyView.kt
fixture/sample-app/app/src/main/java/com/fixture/MainActivity.kt
fixture/sample-app/app/src/main/res/drawable/ic_sparkle.xml
fixture/sample-app/app/src/main/res/layout/activity_basic.xml
fixture/sample-app/app/src/main/res/layout/activity_custom_view.xml
fixture/sample-app/app/src/main/res/layout/activity_minimal.xml
fixture/sample-app/app/src/main/res/values/strings.xml
fixture/sample-app/app/src/main/res/values/themes.xml

exec
/bin/bash -lc "nl -ba fixture/sample-app/app/src/main/java/com/fixture/DummyView.kt | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package com.fixture
     2	
     3	import android.content.Context
     4	import android.graphics.Canvas
     5	import android.graphics.Color
     6	import android.graphics.Paint
     7	import android.util.AttributeSet
     8	import android.view.View
     9	
    10	/**
    11	 * 08 §5 item 6 — W3/W4 L3 에스컬레이션 타겟 placeholder.
    12	 *
    13	 * 실제 앱에서 쓰이는 임의의 custom View 를 대변. 플러그인이:
    14	 *   - L1 에서 class-not-found → PlaceholderCustomView(07 §2.5) 로 치환
    15	 *   - L3 에서 DexClassLoader 로 실제 DummyView 로드 (07 §3.2 AppDexFactory2)
    16	 * 를 검증하는 데 사용.
    17	 *
    18	 * 의도적으로 단순 — 내부 로직은 canvas 에 이름 찍기만.
    19	 */
    20	class DummyView @JvmOverloads constructor(
    21	    context: Context,
    22	    attrs: AttributeSet? = null,
    23	    defStyleAttr: Int = 0
    24	) : View(context, attrs, defStyleAttr) {
    25	
    26	    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    27	        color = Color.WHITE
    28	        textSize = resources.displayMetrics.density * 18f
    29	        textAlign = Paint.Align.CENTER
    30	    }
    31	
    32	    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    33	        style = Paint.Style.STROKE
    34	        strokeWidth = resources.displayMetrics.density * 2f
    35	        color = Color.WHITE
    36	    }
    37	
    38	    override fun onDraw(canvas: Canvas) {
    39	        super.onDraw(canvas)
    40	        val cx = width / 2f
    41	        val cy = height / 2f + textPaint.textSize / 3f
    42	        canvas.drawText("DummyView", cx, cy, textPaint)
    43	        canvas.drawRect(
    44	            framePaint.strokeWidth,
    45	            framePaint.strokeWidth,
    46	            width - framePaint.strokeWidth,
    47	            height - framePaint.strokeWidth,
    48	            framePaint
    49	        )
    50	    }
    51	}

exec
/bin/bash -lc "nl -ba fixture/sample-app/app/src/main/res/layout/activity_custom_view.xml | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	<?xml version="1.0" encoding="utf-8"?>
     2	<!--
     3	  08 §5 item 6 — W3/W4 L3 에스컬레이션 타겟.
     4	  <com.fixture.DummyView> 는 AndroidX/framework 에 없는 사용자 정의 View.
     5	  layoutlib(L1) 에서 ClassNotFoundException → L3 에서 DexClassLoader 로 앱 APK 의 DummyView 로드.
     6	-->
     7	<androidx.constraintlayout.widget.ConstraintLayout
     8	    xmlns:android="http://schemas.android.com/apk/res/android"
     9	    xmlns:app="http://schemas.android.com/apk/res-auto"
    10	    xmlns:tools="http://schemas.android.com/tools"
    11	    android:layout_width="match_parent"
    12	    android:layout_height="match_parent"
    13	    android:padding="16dp">
    14	
    15	    <com.fixture.DummyView
    16	        android:id="@+id/dummy"
    17	        android:layout_width="200dp"
    18	        android:layout_height="120dp"
    19	        android:background="?attr/colorPrimaryContainer"
    20	        app:layout_constraintTop_toTopOf="parent"
    21	        app:layout_constraintStart_toStartOf="parent"
    22	        app:layout_constraintEnd_toEndOf="parent"
    23	        tools:text="DummyView" />
    24	
    25	    <TextView
    26	        android:id="@+id/caption"
    27	        android:layout_width="wrap_content"
    28	        android:layout_height="wrap_content"
    29	        android:text="@string/fixture_custom_caption"
    30	        android:textAppearance="?attr/textAppearanceBodySmall"
    31	        android:layout_marginTop="16dp"
    32	        app:layout_constraintTop_toBottomOf="@id/dummy"
    33	        app:layout_constraintStart_toStartOf="parent"
    34	        app:layout_constraintEnd_toEndOf="parent" />
    35	</androidx.constraintlayout.widget.ConstraintLayout>

exec
/bin/bash -lc "find fixture/sample-app/app/build/intermediates -type f \\( -name '*classes*.jar' -o -name '*.class' \\) | rg 'com/fixture|runtime|javac|kotlin|classes.jar' | head -80" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
exec
/bin/bash -lc "find fixture/sample-app/app/build/tmp -type f -name '*.class' | head -80" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
fixture/sample-app/app/build/tmp/kotlin-classes/debug/com/fixture/MainActivity.class
fixture/sample-app/app/build/tmp/kotlin-classes/debug/com/fixture/DummyView.class

exec
/bin/bash -lc './gradlew :app:androidDependencies 2>/dev/null | head -80' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app
 succeeded in 1072ms:

> Task :app:androidDependencies
debug
debugCompileClasspath - Dependencies for compilation
+--- com.google.android.material:material:1.12.0@aar
+--- androidx.appcompat:appcompat:1.6.1@aar
+--- androidx.viewpager2:viewpager2:1.0.0@aar
+--- androidx.fragment:fragment:1.3.6@aar
+--- androidx.fragment:fragment:1.3.6@aar
+--- androidx.activity:activity:1.8.0@aar
+--- androidx.appcompat:appcompat-resources:1.6.1@aar
+--- androidx.drawerlayout:drawerlayout:1.1.1@aar
+--- androidx.coordinatorlayout:coordinatorlayout:1.1.0@aar
+--- androidx.dynamicanimation:dynamicanimation:1.0.0@aar
+--- androidx.recyclerview:recyclerview:1.1.0@aar
+--- androidx.transition:transition:1.5.0@aar
+--- androidx.vectordrawable:vectordrawable-animated:1.1.0@aar
+--- androidx.vectordrawable:vectordrawable:1.1.0@aar
+--- androidx.viewpager:viewpager:1.0.0@aar
+--- androidx.customview:customview:1.1.0@aar
+--- androidx.legacy:legacy-support-core-utils:1.0.0@aar
+--- androidx.loader:loader:1.0.0@aar
+--- androidx.lifecycle:lifecycle-livedata:2.6.2@aar
+--- androidx.lifecycle:lifecycle-common:2.6.2@jar
+--- androidx.lifecycle:lifecycle-livedata-core:2.6.2@aar
+--- androidx.lifecycle:lifecycle-viewmodel:2.6.2@aar
+--- androidx.lifecycle:lifecycle-viewmodel:2.6.2@aar
+--- androidx.lifecycle:lifecycle-runtime:2.6.2@aar
+--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.2@aar
+--- androidx.core:core-ktx:1.13.0@aar
+--- androidx.core:core:1.13.0@aar
+--- androidx.core:core:1.13.0@aar
+--- androidx.savedstate:savedstate:1.2.1@aar
+--- androidx.annotation:annotation-experimental:1.4.0@aar
+--- androidx.cursoradapter:cursoradapter:1.0.0@aar
+--- androidx.cardview:cardview:1.0.0@aar
+--- androidx.versionedparcelable:versionedparcelable:1.1.1@aar
+--- androidx.collection:collection:1.1.0@jar
+--- androidx.arch.core:core-runtime:2.2.0@aar
+--- androidx.arch.core:core-common:2.2.0@jar
+--- androidx.interpolator:interpolator:1.0.0@aar
+--- androidx.documentfile:documentfile:1.0.0@aar
+--- androidx.localbroadcastmanager:localbroadcastmanager:1.0.0@aar
+--- androidx.print:print:1.0.0@aar
+--- androidx.annotation:annotation-jvm:1.6.0@jar
+--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4@jar
+--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4@jar
+--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22@jar
+--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22@jar
+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23@jar
+--- androidx.constraintlayout:constraintlayout:2.1.4@aar
+--- org.jetbrains:annotations:13.0@jar
\--- androidx.resourceinspection:resourceinspection-annotation:1.0.1@jar

debugRuntimeClasspath - Dependencies for runtime/packaging
+--- com.google.android.material:material:1.12.0@aar
+--- androidx.constraintlayout:constraintlayout:2.1.4@aar
+--- androidx.appcompat:appcompat-resources:1.6.1@aar
+--- androidx.appcompat:appcompat:1.6.1@aar
+--- androidx.viewpager2:viewpager2:1.0.0@aar
+--- androidx.fragment:fragment:1.3.6@aar
+--- androidx.fragment:fragment:1.3.6@aar
+--- androidx.activity:activity:1.8.0@aar
+--- androidx.emoji2:emoji2-views-helper:1.2.0@aar
+--- androidx.emoji2:emoji2:1.2.0@aar
+--- androidx.drawerlayout:drawerlayout:1.1.1@aar
+--- androidx.coordinatorlayout:coordinatorlayout:1.1.0@aar
+--- androidx.transition:transition:1.5.0@aar
+--- androidx.dynamicanimation:dynamicanimation:1.0.0@aar
+--- androidx.vectordrawable:vectordrawable-animated:1.1.0@aar
+--- androidx.vectordrawable:vectordrawable:1.1.0@aar
+--- androidx.viewpager:viewpager:1.0.0@aar
+--- androidx.legacy:legacy-support-core-utils:1.0.0@aar
+--- androidx.loader:loader:1.0.0@aar
+--- androidx.recyclerview:recyclerview:1.1.0@aar
+--- androidx.customview:customview:1.1.0@aar
+--- androidx.core:core-ktx:1.13.0@aar
+--- androidx.core:core:1.13.0@aar
+--- androidx.core:core:1.13.0@aar
+--- androidx.lifecycle:lifecycle-viewmodel:2.6.2@aar

exec
/bin/bash -lc "./gradlew :app:androidDependencies 2>/dev/null | sed -n '/debugRuntimeClasspath/,+70p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app
 succeeded in 445ms:
debugRuntimeClasspath - Dependencies for runtime/packaging
+--- com.google.android.material:material:1.12.0@aar
+--- androidx.constraintlayout:constraintlayout:2.1.4@aar
+--- androidx.appcompat:appcompat-resources:1.6.1@aar
+--- androidx.appcompat:appcompat:1.6.1@aar
+--- androidx.viewpager2:viewpager2:1.0.0@aar
+--- androidx.fragment:fragment:1.3.6@aar
+--- androidx.fragment:fragment:1.3.6@aar
+--- androidx.activity:activity:1.8.0@aar
+--- androidx.emoji2:emoji2-views-helper:1.2.0@aar
+--- androidx.emoji2:emoji2:1.2.0@aar
+--- androidx.drawerlayout:drawerlayout:1.1.1@aar
+--- androidx.coordinatorlayout:coordinatorlayout:1.1.0@aar
+--- androidx.transition:transition:1.5.0@aar
+--- androidx.dynamicanimation:dynamicanimation:1.0.0@aar
+--- androidx.vectordrawable:vectordrawable-animated:1.1.0@aar
+--- androidx.vectordrawable:vectordrawable:1.1.0@aar
+--- androidx.viewpager:viewpager:1.0.0@aar
+--- androidx.legacy:legacy-support-core-utils:1.0.0@aar
+--- androidx.loader:loader:1.0.0@aar
+--- androidx.recyclerview:recyclerview:1.1.0@aar
+--- androidx.customview:customview:1.1.0@aar
+--- androidx.core:core-ktx:1.13.0@aar
+--- androidx.core:core:1.13.0@aar
+--- androidx.core:core:1.13.0@aar
+--- androidx.lifecycle:lifecycle-viewmodel:2.6.2@aar
+--- androidx.lifecycle:lifecycle-viewmodel:2.6.2@aar
+--- androidx.lifecycle:lifecycle-process:2.6.2@aar
+--- androidx.lifecycle:lifecycle-livedata-core:2.6.2@aar
+--- androidx.lifecycle:lifecycle-livedata:2.6.2@aar
+--- androidx.lifecycle:lifecycle-common:2.6.2@jar
+--- androidx.lifecycle:lifecycle-runtime:2.6.2@aar
+--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.2@aar
+--- androidx.savedstate:savedstate:1.2.1@aar
+--- androidx.annotation:annotation-experimental:1.4.0@aar
+--- androidx.cursoradapter:cursoradapter:1.0.0@aar
+--- androidx.resourceinspection:resourceinspection-annotation:1.0.1@jar
+--- androidx.cardview:cardview:1.0.0@aar
+--- androidx.versionedparcelable:versionedparcelable:1.1.1@aar
+--- androidx.collection:collection:1.1.0@jar
+--- androidx.profileinstaller:profileinstaller:1.3.0@aar
+--- androidx.startup:startup-runtime:1.1.1@aar
+--- androidx.tracing:tracing:1.0.0@aar
+--- androidx.interpolator:interpolator:1.0.0@aar
+--- androidx.concurrent:concurrent-futures:1.1.0@jar
+--- androidx.documentfile:documentfile:1.0.0@aar
+--- androidx.localbroadcastmanager:localbroadcastmanager:1.0.0@aar
+--- androidx.print:print:1.0.0@aar
+--- androidx.arch.core:core-runtime:2.2.0@aar
+--- androidx.arch.core:core-common:2.2.0@jar
+--- androidx.annotation:annotation-jvm:1.6.0@jar
+--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4@jar
+--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4@jar
+--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22@jar
+--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22@jar
+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23@jar
+--- org.jetbrains:annotations:13.0@jar
+--- androidx.constraintlayout:constraintlayout-core:1.0.4@jar
+--- com.google.errorprone:error_prone_annotations:2.15.0@jar
\--- com.google.guava:listenablefuture:1.0@jar

release
releaseCompileClasspath - Dependencies for compilation
+--- com.google.android.material:material:1.12.0@aar
+--- androidx.appcompat:appcompat:1.6.1@aar
+--- androidx.viewpager2:viewpager2:1.0.0@aar
+--- androidx.fragment:fragment:1.3.6@aar
+--- androidx.fragment:fragment:1.3.6@aar
+--- androidx.activity:activity:1.8.0@aar
+--- androidx.appcompat:appcompat-resources:1.6.1@aar
+--- androidx.drawerlayout:drawerlayout:1.1.1@aar

Artifact assumptions mostly check out, but the spec has path-contract and resource/R-id gaps that block the stated SUCCESS gate.
  ✓ Read spec and canonical context with line numbers
  ✓ Inspect current layoutlib worker source and Gradle fixture
  ✓ Verify build artifacts, AAR contents, dependencies, and layoutlib-api surface
  → Assess blockers, follow-ups, explicit Q1-Q5, independent issues, verdict
Review is complete. The final verdict is NO_GO because the spec as written cannot reach the stated activity_basic SUCCESS gate.
  ✓ Read spec and canonical context with line numbers
  ✓ Inspect current layoutlib worker source and Gradle fixture
  ✓ Verify build artifacts, AAR contents, dependencies, and layoutlib-api surface
  ✓ Assess blockers, follow-ups, explicit Q1-Q5, independent issues, verdict
codex
**A. Blockers (NO_GO)**

1. **`fixtureRoot` contract is wrong for the proposed classloader.**  
   [FixtureDiscovery.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt:17) returns the layout directory, and [LayoutlibRenderer.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:157) does `fixtureRoot.resolve(layoutName)`. The spec’s `SampleAppClassLoader.build(fixtureRoot)` expects `fixtureRoot == fixture/sample-app` ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:315), [line 320](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:320), [line 336](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:336)).  
   **Failure:** it will look for `app/build/...` under `app/src/main/res/layout`.  
   **Fix:** introduce a separate `sampleAppRoot`/`projectRoot`, or derive it explicitly from the layout root and keep the renderer’s layout root unchanged.

2. **App/library resource values cannot remain out of scope for `activity_basic.xml`.**  
   The target layout contains `MaterialButton` ([activity_basic.xml](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app/app/src/main/res/layout/activity_basic.xml:43)) and the fixture theme is Material3 ([themes.xml](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app/app/src/main/res/values/themes.xml:5)). Current rendering uses only a framework theme ([SessionConstants.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt:42)) and explicitly returns null for non-android resources/styles ([FrameworkRenderResources.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:35), [line 40](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:40)). The spec says app/library resource value loading is out of scope ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:67)) while requiring SUCCESS ([line 61](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:61)).  
   **Failure:** `MaterialButton` runs Material theme enforcement; without Material/AppCompat theme attrs, constructor inflation is expected to throw before render SUCCESS.  
   **Fix:** either move a minimal merged app/library resource/theme loader into W3D3, or change the T1 layout to avoid Material/AppCompat components.

3. **Putting `R.jar` on the classloader is necessary but not sufficient.**  
   The spec keeps the existing generated ID callback behavior ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:380)). Current callback generates IDs from `0x7F0A_0000` ([MinimalLayoutlibCallback.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:30), [line 68](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:68)). Actual `R.attr` IDs from the built `R.jar` are different, e.g. `layout_constraintTop_toTopOf = 2130903705`.  
   **Failure:** constructors use actual `R.styleable`/`R.attr` ints, but `resolveResourceId()` cannot map those ints back to resources, so `TypedArray` lookups for ConstraintLayout/Material attrs miss.  
   **Fix:** seed `getOrGenerateResourceId`/`resolveResourceId` from merged `R.jar` or symbol data, using actual IDs.

4. **Literal constants in the spec snippets violate the repo policy if copied directly.**  
   Examples: `"SHA-1"` and `".aar"`/`".jar"` in `AarExtractor` ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:260), [line 264](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:264)), plus `"app"`/`"build"` in `SampleAppClassLoader` ([line 321](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:321)).  
   **Fix:** move suffixes, hash algorithm, cache file naming, and path segments into `ClassLoaderConstants`.

**B. Strongly Recommended**

- Override `findClass()` and `isClassLoaded()` to use the same lazy view classloader. `LayoutlibCallback` has no additional abstract custom-view method, but `javap` shows `findClass` defaults to throwing and `BridgeInflater` uses it for AppCompat/custom inflater discovery.

- Avoid `afterEvaluate` for the Gradle task. `tasks.named("assembleDebug").configure { finalizedBy(...) }` can be registered directly; use `androidComponents` only if this fixture grows flavors/variants.

- Make AAR extraction atomic: write to a temp file and `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. The current spec writes directly to the cache path ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:272)), which is fragile under parallel forks.

- If this is meant to cover real app custom views, add app compiled classes too. `debugRuntimeClasspath` does not include `fixture/sample-app/app/build/tmp/kotlin-classes/debug`, where `DummyView.class` is built.

**C. Q1-Q5**

- **Q1:** Alternative. Direct `debugRuntimeClasspath` is acceptable for this single-variant fixture, but `afterEvaluate` is unnecessary. Use direct lazy task wiring now; move to `androidComponents` when variants/flavors matter.

- **Q2:** Agree for Gradle module-cache AARs; mtime is enough because paths are content-hash cache paths. For flatDir/local artifacts or generalized use, use sha256 or at least size+mtime.

- **Q3:** Disagree with raw `InvocationTargetException` pass-through. Unwrap and rethrow the cause so logs show `IllegalArgumentException`/theme/resource failures directly. Layoutlib does not require wrapping here.

- **Q4:** Agree with option A, lazy build. Empty CL masks setup errors and degrades into confusing `ClassNotFoundException`. Use a synchronized `Lazy<ClassLoader>` after fixing the sample-app-root mismatch.

- **Q5:** Agree, add `Result.Status.SUCCESS`. PNG size/magic alone can pass fallback or partial paths. Existing tier3-values already asserts SUCCESS for minimal render ([LayoutlibRendererTier3MinimalTest.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:104)).

**D. Independent Verification**

- `R.jar` exists at the asserted AGP 8.3.2 path:  
  `ls -l fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar` → exists, 839012 bytes.

- AAR entry name is `classes.jar`: confirmed with `unzip -l` for `constraintlayout-2.1.4.aar`, `material-1.12.0.aar`, and `appcompat-1.6.1.aar`.

- `debugRuntimeClasspath` resolves AARs and transitive JARs. `./gradlew :app:androidDependencies` shows Material/ConstraintLayout/AppCompat AARs plus JARs including Kotlin stdlib and `constraintlayout-core`.

- The “disjoint namespaces” assertion is not literally true: runtime classpath includes `kotlin.*` JARs, while Kotlin also exists on the worker/system CL. Parent-first still looks right for this fixture, but the spec should not justify it as fully disjoint.

- `layoutlib-api` actual cache path is `~/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/...`, not `com/android/tools/layoutlib/...`. `javap` shows the abstract methods are already implemented by `MinimalLayoutlibCallback`; custom-view awareness should additionally cover non-abstract `findClass/isClassLoaded`.

- `forkEvery(1L)` is present for integration tests ([build.gradle.kts](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/build.gradle.kts:60)). One URLClassLoader per fork is acceptable; closing is not a merge blocker for tests.

**E. Verdict**

**NO_GO.** The host-JVM `URLClassLoader` direction is correct, and the local artifact assumptions mostly verify. But the spec as written cannot reach the stated `activity_basic.xml` SUCCESS gate because it uses the wrong root path, lacks actual R-ID/resource mapping, and excludes the Material/AppCompat resource/theme data required by the target layout.
2026-04-29T05:14:27.588204Z ERROR codex_core::session: failed to record rollout items: thread 019dd7a1-82a3-7d10-8c9b-4a6ab149165c not found
tokens used
158,901
**A. Blockers (NO_GO)**

1. **`fixtureRoot` contract is wrong for the proposed classloader.**  
   [FixtureDiscovery.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt:17) returns the layout directory, and [LayoutlibRenderer.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:157) does `fixtureRoot.resolve(layoutName)`. The spec’s `SampleAppClassLoader.build(fixtureRoot)` expects `fixtureRoot == fixture/sample-app` ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:315), [line 320](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:320), [line 336](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:336)).  
   **Failure:** it will look for `app/build/...` under `app/src/main/res/layout`.  
   **Fix:** introduce a separate `sampleAppRoot`/`projectRoot`, or derive it explicitly from the layout root and keep the renderer’s layout root unchanged.

2. **App/library resource values cannot remain out of scope for `activity_basic.xml`.**  
   The target layout contains `MaterialButton` ([activity_basic.xml](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app/app/src/main/res/layout/activity_basic.xml:43)) and the fixture theme is Material3 ([themes.xml](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app/app/src/main/res/values/themes.xml:5)). Current rendering uses only a framework theme ([SessionConstants.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt:42)) and explicitly returns null for non-android resources/styles ([FrameworkRenderResources.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:35), [line 40](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:40)). The spec says app/library resource value loading is out of scope ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:67)) while requiring SUCCESS ([line 61](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:61)).  
   **Failure:** `MaterialButton` runs Material theme enforcement; without Material/AppCompat theme attrs, constructor inflation is expected to throw before render SUCCESS.  
   **Fix:** either move a minimal merged app/library resource/theme loader into W3D3, or change the T1 layout to avoid Material/AppCompat components.

3. **Putting `R.jar` on the classloader is necessary but not sufficient.**  
   The spec keeps the existing generated ID callback behavior ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:380)). Current callback generates IDs from `0x7F0A_0000` ([MinimalLayoutlibCallback.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:30), [line 68](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:68)). Actual `R.attr` IDs from the built `R.jar` are different, e.g. `layout_constraintTop_toTopOf = 2130903705`.  
   **Failure:** constructors use actual `R.styleable`/`R.attr` ints, but `resolveResourceId()` cannot map those ints back to resources, so `TypedArray` lookups for ConstraintLayout/Material attrs miss.  
   **Fix:** seed `getOrGenerateResourceId`/`resolveResourceId` from merged `R.jar` or symbol data, using actual IDs.

4. **Literal constants in the spec snippets violate the repo policy if copied directly.**  
   Examples: `"SHA-1"` and `".aar"`/`".jar"` in `AarExtractor` ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:260), [line 264](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:264)), plus `"app"`/`"build"` in `SampleAppClassLoader` ([line 321](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:321)).  
   **Fix:** move suffixes, hash algorithm, cache file naming, and path segments into `ClassLoaderConstants`.

**B. Strongly Recommended**

- Override `findClass()` and `isClassLoaded()` to use the same lazy view classloader. `LayoutlibCallback` has no additional abstract custom-view method, but `javap` shows `findClass` defaults to throwing and `BridgeInflater` uses it for AppCompat/custom inflater discovery.

- Avoid `afterEvaluate` for the Gradle task. `tasks.named("assembleDebug").configure { finalizedBy(...) }` can be registered directly; use `androidComponents` only if this fixture grows flavors/variants.

- Make AAR extraction atomic: write to a temp file and `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. The current spec writes directly to the cache path ([spec](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:272)), which is fragile under parallel forks.

- If this is meant to cover real app custom views, add app compiled classes too. `debugRuntimeClasspath` does not include `fixture/sample-app/app/build/tmp/kotlin-classes/debug`, where `DummyView.class` is built.

**C. Q1-Q5**

- **Q1:** Alternative. Direct `debugRuntimeClasspath` is acceptable for this single-variant fixture, but `afterEvaluate` is unnecessary. Use direct lazy task wiring now; move to `androidComponents` when variants/flavors matter.

- **Q2:** Agree for Gradle module-cache AARs; mtime is enough because paths are content-hash cache paths. For flatDir/local artifacts or generalized use, use sha256 or at least size+mtime.

- **Q3:** Disagree with raw `InvocationTargetException` pass-through. Unwrap and rethrow the cause so logs show `IllegalArgumentException`/theme/resource failures directly. Layoutlib does not require wrapping here.

- **Q4:** Agree with option A, lazy build. Empty CL masks setup errors and degrades into confusing `ClassNotFoundException`. Use a synchronized `Lazy<ClassLoader>` after fixing the sample-app-root mismatch.

- **Q5:** Agree, add `Result.Status.SUCCESS`. PNG size/magic alone can pass fallback or partial paths. Existing tier3-values already asserts SUCCESS for minimal render ([LayoutlibRendererTier3MinimalTest.kt](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:104)).

**D. Independent Verification**

- `R.jar` exists at the asserted AGP 8.3.2 path:  
  `ls -l fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar` → exists, 839012 bytes.

- AAR entry name is `classes.jar`: confirmed with `unzip -l` for `constraintlayout-2.1.4.aar`, `material-1.12.0.aar`, and `appcompat-1.6.1.aar`.

- `debugRuntimeClasspath` resolves AARs and transitive JARs. `./gradlew :app:androidDependencies` shows Material/ConstraintLayout/AppCompat AARs plus JARs including Kotlin stdlib and `constraintlayout-core`.

- The “disjoint namespaces” assertion is not literally true: runtime classpath includes `kotlin.*` JARs, while Kotlin also exists on the worker/system CL. Parent-first still looks right for this fixture, but the spec should not justify it as fully disjoint.

- `layoutlib-api` actual cache path is `~/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/...`, not `com/android/tools/layoutlib/...`. `javap` shows the abstract methods are already implemented by `MinimalLayoutlibCallback`; custom-view awareness should additionally cover non-abstract `findClass/isClassLoaded`.

- `forkEvery(1L)` is present for integration tests ([build.gradle.kts](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/build.gradle.kts:60)). One URLClassLoader per fork is acceptable; closing is not a merge blocker for tests.

**E. Verdict**

**NO_GO.** The host-JVM `URLClassLoader` direction is correct, and the local artifact assumptions mostly verify. But the spec as written cannot reach the stated `activity_basic.xml` SUCCESS gate because it uses the wrong root path, lacks actual R-ID/resource mapping, and excludes the Material/AppCompat resource/theme data required by the target layout.
