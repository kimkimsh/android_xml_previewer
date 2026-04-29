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
session id: 019dd7b5-4bdd-77c1-ad93-74385e322bc1
--------
user
# Codex Pair Review — W3D3 L3 ClassLoader Implementation Plan (round 2)

You are reviewing the implementation plan that decomposes the (already-pair-reviewed) design spec into bite-sized TDD tasks. The spec round 1 review has already converged on most architectural decisions. This round 2 review is about **plan execution feasibility**: are the tasks bite-sized? Do they have exact code/commands? Do they preserve invariants between tasks? Will the build stay green between commits?

**Configuration**: Use the highest available GPT model (currently GPT-5.5 / GPT-5.4) with reasoning effort `xhigh`. No time limit.

## What you have

Working directory: `/home/bh-mark-dev-desktop/workspace/android_xml_previewer`

1. **THE PLAN UNDER REVIEW** — `docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md` (11 tasks, ~970 lines).
2. **The spec the plan implements** — `docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md` (round 2, all spec convergent fixes already merged).
3. **Round 1 pair-review reports** (for context on what's already settled):
   - `docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md`
   - `docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-claude.md`
4. **Existing source the plan modifies** — `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/{LayoutlibRenderer,FixtureDiscovery,session/MinimalLayoutlibCallback}.kt` plus `server/layoutlib-worker/build.gradle.kts`, `server/mcp-server/.../{Main,CliArgs,CliConstants}.kt`, `fixture/sample-app/app/build.gradle.kts`.
5. **CLAUDE.md** rules: zero-tolerance for magic strings; no default parameter values; one class per file; brace style (single statement → still `{}`); commit-and-push at task-unit completion.

## Round 1 already settled (don't re-litigate)

- DexClassLoader → URLClassLoader (correct).
- ChildFirst delegation → not needed.
- B1 fixtureRoot vs sampleAppModuleRoot → spec/plan use a separate `sampleAppModuleRoot` parameter.
- F1 `findClass` + `hasAndroidXAppCompat` overrides → planned in Task 6.
- B4 magic strings → all moved to `ClassLoaderConstants` in Task 1.
- Q1 (afterEvaluate removed), Q2 (mtime cache), Q3 (ITE unwrap), Q4 (lazy provider), Q5 (SUCCESS assertion) — all reflected.
- B2 (Material theme enforcement risk) → §9.1 contingency layout. Task 10 Step 3 has explicit branch for empirical result.

## What I need you to verify (round 2 scope)

### A. Plan-level blockers (NO_GO conditions)

Things that would make the plan unimplementable or that would leave the codebase broken between tasks. Examples:
- Task ordering breaks compilation between commits (Task N's commit cannot succeed because Task M hasn't run).
- A task references a file/symbol that hasn't been created yet.
- A task's exact code violates CLAUDE.md (magic strings, default params, etc.).
- A test fixture won't actually exercise the production behavior the task claims.

For each: cite the task number + step, exact failure mode, recommended fix.

### B. Strongly recommended

Plan-level improvements that aren't blockers but improve quality:
- Better test coverage / edge cases.
- Better commit boundaries.
- Clearer expected outputs.

### C. Specific concerns to address

1. **Task 7 + Task 8 atomicity** — Task 7 says "do not commit" because compilation breaks; Task 8 fixes all callers and commits the unit. Is this an acceptable atomic-task pattern? Or should both be a single Task to avoid mid-execution-state risk if a subagent dies? Look at W3D2's session-log "LM-B1 Task 4 compile atomicity" landmine for prior art.
2. **Task 5 `locateInternalGeneric` refactor** — does the plan preserve existing `locateInternal` behavior? Existing tests in `FixtureDiscoveryTest` (5 unit tests) must still pass. Verify by reading the existing tests.
3. **Task 6's `loadView` test for StringBuilder** — the test uses `java.lang.StringBuilder(CharSequence)` constructor. Is that a public constructor accessible via `getDeclaredConstructor`? Verify.
4. **Task 9's Gradle task wiring** — `tasks.named("assembleDebug").configure { finalizedBy(...) }` needs the Android plugin to have created `assembleDebug` first. With Kotlin Gradle DSL `tasks.named` is lazy but the task must exist by configuration phase. Will this work without `afterEvaluate` in an AGP 8.x project? Verify by checking whether assembleDebug exists at script evaluation time, OR by attempting `./gradlew :app:tasks --all 2>&1 | grep assembleDebug` to see if it's reachable.
5. **Task 10 Step 3 branch (B) — contingency layout** — the plan says "if MaterialThemeOverlay/ThemeEnforcement related stack trace, fall back to activity_basic_minimal.xml". But what happens if the failure is a DIFFERENT kind of failure that the plan doesn't anticipate? Branch (C) handles "other" but doesn't say what concrete fallbacks exist. Does the plan have enough scaffolding to recover, or could a subagent get stuck?
6. **Test cardinality math** — plan claims "118 → 130+ unit (15 신규)". Count carefully: Task 1 (3) + Task 2 (5) + Task 3 (4) + Task 4 (3) + Task 5 (2) + Task 6 (4) = 21 new unit tests. Plus Task 6 modifies existing `MinimalLayoutlibCallbackTest` — does that file have tests that count differently? Verify by reading the existing test file.
7. **Constructor parameter ordering** — `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` — current signature is `(distDir, fallback, fixtureRoot)` per W3D2. Adding `sampleAppModuleRoot` at the end means all four positional callers must update — does Task 8 grep cover all of them? Look at `grep -rn "LayoutlibRenderer(" server/ --include="*.kt"` to enumerate.

### D. Independent issues

Anything else that would surface during implementation. Especially:
- Are the magic-string violations in spec/plan code blocks ALREADY in `ClassLoaderConstants`? The plan duplicates code blocks across multiple tasks — verify no drift.
- Does the empty manifest test in Task 2 (`assertThrows<IllegalStateException>`) match the actual production behavior (`error("...")` throws `IllegalStateException`)?
- The `assumeTrue(found != null, ...)` calls in `LayoutlibRendererIntegrationTest.locateSampleAppModuleRoot` — do these correctly skip if sample-app isn't built? Or should they fail loudly?

### E. Verdict
NO_GO / GO_WITH_FOLLOWUPS / GO + one-paragraph rationale on whether the plan is ready to enter implementation phase.

## Output format

Markdown with sections A, B, C (per concern 1-7), D, E. File:line citations. No hand-waving.

## What you have access to
You can run any shell command. Read files freely.
codex
I’ll use the Superpowers startup guidance first, then inspect the plan against the actual Kotlin/Gradle code and tests so the review is grounded in file and line evidence.
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
Using `superpowers:using-superpowers` to satisfy the session workflow. I’m now going straight into evidence gathering: plan task boundaries, existing tests, constructor callers, and the Gradle task wiring claim.
exec
/bin/bash -lc 'wc -l docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
  978 docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md
  727 docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md
 1705 total

exec
/bin/bash -lc 'rg -n "''^## Task|''^### Task|Task [0-9]|do not commit|LayoutlibRenderer'"\\(|locateInternalGeneric|StringBuilder|assembleDebug|finalizedBy|118|130|15 신규|SUCCESS|MaterialThemeOverlay|ThemeEnforcement|activity_basic_minimal|assertThrows|assumeTrue\" docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
5:**Goal:** sample-app 의 `activity_basic.xml` (ConstraintLayout / MaterialButton 등 custom view) 가 layoutlib host JVM 안에서 SUCCESS 로 렌더되도록, AAR `classes.jar` 를 `URLClassLoader` 로 적재해 `MinimalLayoutlibCallback.loadView` 를 통해 reflection-instantiate 한다.
8:- AAR/JAR resolution = sample-app `assembleDebug` 가 emit 한 manifest 파일 (`<sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt`).
20:## Task 1: `ClassLoaderConstants` (B4 magic strings 제거 기반 작업)
105:Expected: BUILD SUCCESSFUL, 3 tests PASS.
118:## Task 2: `SampleAppClasspathManifest`
130:import org.junit.jupiter.api.assertThrows
140:        val ex = assertThrows<IllegalArgumentException> {
144:        assertEquals(true, ex.message!!.contains("./gradlew :app:assembleDebug"))
152:        assertThrows<IllegalStateException> { SampleAppClasspathManifest.read(root) }
161:        val ex = assertThrows<IllegalArgumentException> { SampleAppClasspathManifest.read(root) }
170:        val ex = assertThrows<IllegalArgumentException> { SampleAppClasspathManifest.read(root) }
215:            "axp classpath manifest 누락: $mf — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 를 먼저 실행하세요"
245:## Task 3: `AarExtractor`
257:import org.junit.jupiter.api.assertThrows
322:        assertThrows<IllegalArgumentException> { AarExtractor.extract(missing, cacheRoot) }
398:## Task 4: `SampleAppClassLoader`
410:import org.junit.jupiter.api.assertThrows
473:        val ex = assertThrows<IllegalStateException> { SampleAppClassLoader.build(root, parent) }
526:                "sample-app R.jar 누락: $rJar — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 필요"
543:## Task 5: `FixtureDiscovery.locateModuleRoot` 추가
587:): Path? = locateInternalGeneric(override, userDir, candidateRoots, FIXTURE_MODULE_SUBPATH)
589:internal fun locateInternalGeneric(
609:기존 `locateInternal` 은 `locateInternalGeneric(... , FIXTURE_SUBPATH)` 로 위임 (동작 동일).
620:## Task 6: `MinimalLayoutlibCallback` 변경 (loadView + findClass + hasAndroidXAppCompat)
633:import org.junit.jupiter.api.assertThrows
646:        val v = cb.loadView("java.lang.StringBuilder", arrayOf(CharSequence::class.java), arrayOf<Any>("hi"))
653:        assertThrows<ClassNotFoundException> {
662:        val cls = cb.findClass("java.lang.StringBuilder")
778:## Task 7: `LayoutlibRenderer` — sampleAppModuleRoot 인자 + lazy provider 배선
786:class LayoutlibRenderer(
815:(여기서 commit 하지 않고 Task 8 까지 묶어서 처리 — 컴파일이 깨진 상태는 task unit 미완으로 간주.)
819:## Task 8: 모든 호출부 업데이트 — Main.kt CLI + SharedLayoutlibRenderer + 모든 테스트
827:- Modify: 모든 production `LayoutlibRenderer(...)` 호출하는 테스트 (W3D1: `LayoutlibRendererTier3MinimalTest`, integration tests).
828:- Modify: `LayoutlibRendererIntegrationTest.kt` — 곧 Task 10 에서 enable.
841:W3D2 의 시그니처 그대로 확장. binding 은 `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 호출.
846:grep -rn "LayoutlibRenderer(" server/ --include="*.kt"
854:- [ ] **Step 6: 전체 build + test 실행 — 컴파일 PASS + 기존 테스트 (118 unit + 11 integration) 회귀 없음 확인**
857:Expected: BUILD SUCCESSFUL. activity_basic.xml 의 `LayoutlibRendererIntegrationTest` 는 아직 `@Disabled` 상태 — 회귀 없음.
859:- [ ] **Step 7: Commit (Task 7 + Task 8 묶음 — atomic compilation unit)**
863:## Task 9: Gradle `axpEmitClasspath` task + 첫 manifest 생성
875:cd fixture/sample-app && ./gradlew :app:assembleDebug 2>&1 | tail -10
889:## Task 10: `LayoutlibRendererIntegrationTest` enable + 첫 실측 + B2 contingency 결정
894:- [ ] **Step 1: `@Disabled` 제거 + spec §4.8 의 코드 적용 (SUCCESS assertion 활성, sampleAppModuleRoot 주입)**
906:  **(B) FAIL with `MaterialThemeOverlay`/`ThemeEnforcement`/`Material*` 관련 stack trace**:
908:  - `fixture/sample-app/app/src/main/res/layout/activity_basic_minimal.xml` 작성 (spec §9.1 의 마크업).
909:  - `LayoutlibRendererIntegrationTest` 의 layoutName 을 `"activity_basic_minimal.xml"` 로 변경.
920:./server/gradlew -p server build                                         # BUILD SUCCESSFUL
924:Expected: 모든 게이트 통과. Unit 118+ → 130+ (15 신규), integration 11 → 12 PASS, tier3-glyph 만 SKIPPED.
926:- [ ] **Step 5: Commit (Task 10 unit)**
930:## Task 11: work_log + handoff 업데이트
950:- §0 페어 리뷰 결과 → Task 1-10 구현으로 모두 반영.
951:- §1.1 deliverables (manifest, AAR loader, callback override, integration enable) → Task 1-4, 6, 9, 10.
953:- §1.3 acceptance gate (T1) → Task 10 Step 2.
954:- §2 컴포넌트 분해 → Task 별 1:1 매핑 (C0/C1/C2/C3/C4/C5/C6/C7/C8 → Task 5/9/2/3/4/1/6/7/10).
955:- §3 데이터 흐름 → Task 7+8 의 LayoutlibRenderer 수정으로 wire.
956:- §4 컴포넌트 스펙 → Task 1-7 의 코드 블록.
957:- §5 테스트 전략 → Task 1-4, 6 의 단위 + Task 10 의 통합.
958:- §6 위험 → Task 10 Step 3 의 분기 (R1/R3/R4).
960:- §8 변경 로그 → Task 11 step 3 가 plan/08 에 추가.
961:- §9 contingency → Task 10 Step 3 (B).
965:**Type consistency**: `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 시그니처가 모든 task 에서 일관. `MinimalLayoutlibCallback { provider }` lambda 도.

exec
/bin/bash -lc "rg -n \"class LayoutlibRenderer|fun locateInternal|LayoutlibRenderer\\(|sampleAppModuleRoot|DexClassLoader|URLClassLoader|findClass|loadView|hasAndroidXAppCompat\" server/layoutlib-worker/src/main/kotlin server/layoutlib-worker/src/test/kotlin server/mcp-server/src/main/kotlin fixture/sample-app/app/build.gradle.kts server/layoutlib-worker/build.gradle.kts" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/layoutlib-worker/build.gradle.kts:22:    // 에 존재해야 isolated URLClassLoader 의 parent-first delegation 으로 하나의 Class
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:130:        LayoutlibRenderer(
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/DistDiscovery.kt:34:    internal fun locateInternal(
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt:35:    internal fun locateInternal(
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt:4:import java.net.URLClassLoader
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt:26: * 격리된 URLClassLoader 를 만들고, 그 안에서 reflection 으로 `Bridge.init(...)` 호출.
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt:145:     * 격리된 URLClassLoader 구성.
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrap.kt:168:        return URLClassLoader(urls, ClassLoader.getSystemClassLoader())
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:44:        val created = LayoutlibRenderer(distDir, fallback, fixtureRoot)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:30:class LayoutlibRendererTier3MinimalTest {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:40: *  - 커스텀 뷰 (ConstraintLayout, MaterialButton 등) 는 `MinimalLayoutlibCallback.loadView`
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:44:class LayoutlibRenderer(
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/Main.kt:12: *   --layoutlib-cp=<jar>  layoutlib-dist 디렉토리. 런타임에 URLClassLoader 로 로드.
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:18: *  - loadView: 커스텀 뷰 요구 시 즉시 UnsupportedOperationException — 프레임워크 위젯은
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:42:    override fun loadView(name: String, constructorSignature: Array<out Class<*>>?, constructorArgs: Array<out Any>?): Any {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:16: * (MinimalLayoutlibCallback.loadView 가 ConstraintLayout/MaterialButton 을 reject) 는
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:18: * skip — W3 sample-app unblock (DexClassLoader) 이후 enable 예정.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:24:@Disabled("W3 sample-app unblock 필요 — L3 DexClassLoader carry (ConstraintLayout / MaterialButton)")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:25:class LayoutlibRendererIntegrationTest {

exec
/bin/bash -lc "nl -ba docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md | sed -n '1,120p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	# W3D3 L3 ClassLoader Implementation Plan
     2	
     3	> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
     4	
     5	**Goal:** sample-app 의 `activity_basic.xml` (ConstraintLayout / MaterialButton 등 custom view) 가 layoutlib host JVM 안에서 SUCCESS 로 렌더되도록, AAR `classes.jar` 를 `URLClassLoader` 로 적재해 `MinimalLayoutlibCallback.loadView` 를 통해 reflection-instantiate 한다.
     6	
     7	**Architecture:**
     8	- AAR/JAR resolution = sample-app `assembleDebug` 가 emit 한 manifest 파일 (`<sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt`).
     9	- Worker 가 manifest 의 `.aar` 를 풀어 (`<sampleAppModuleRoot>/app/build/axp/aar-classes/<sha1>/<artifact>.jar`) JAR + sample-app `R.jar` 와 합쳐 `URLClassLoader(parent = layoutlib isolatedCL)` 구성.
    10	- `MinimalLayoutlibCallback` 의 `loadView` / `findClass` 가 lazy provider 로 위 CL 사용. `hasAndroidXAppCompat = true` 로 BridgeInflater 의 AppCompat 자동 치환 활성. `InvocationTargetException` 은 cause 로 unwrap.
    11	
    12	**Tech Stack:** Kotlin 1.9 / JDK 17 / Gradle 8 / JUnit Jupiter 5 / `java.net.URLClassLoader` / `java.util.zip.ZipFile` / `java.security.MessageDigest`. layoutlib 14.0.11 (이미 wired). AGP 8.x in `fixture/sample-app`.
    13	
    14	**Spec**: [`docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md`](../specs/2026-04-29-w3d3-l3-classloader-design.md). 본 플랜 모든 결정은 spec 의 round 2 본문 + §9 contingency 에 일치.
    15	
    16	**Round 1 페어-리뷰**: spec 단계에서 완료 (`docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-{codex,claude}.md`). 본 플랜에 별도 round 2 페어-리뷰 진행 후 구현 phase 진입.
    17	
    18	---
    19	
    20	## Task 1: `ClassLoaderConstants` (B4 magic strings 제거 기반 작업)
    21	
    22	**Files:**
    23	- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt`
    24	- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt`
    25	
    26	- [ ] **Step 1: 새 디렉토리 + Constants 파일 작성**
    27	
    28	```kotlin
    29	// ClassLoaderConstants.kt
    30	package dev.axp.layoutlib.worker.classloader
    31	
    32	internal object ClassLoaderConstants {
    33	    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 sampleAppModuleRoot-relative 경로. */
    34	    const val MANIFEST_RELATIVE_PATH = "app/build/axp/runtime-classpath.txt"
    35	
    36	    /** AarExtractor cacheRoot (= sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)) 의 base. */
    37	    const val AAR_CACHE_BASE_RELATIVE_PATH = "app/build/axp"
    38	
    39	    /** AarExtractor 의 stable 캐시 서브디렉토리 (cacheRoot 기준 상대). */
    40	    const val AAR_CACHE_RELATIVE_DIR = "aar-classes"
    41	
    42	    /** AAR ZIP 안에서 JVM 바이트코드 JAR 의 표준 entry 이름. */
    43	    const val AAR_CLASSES_JAR_ENTRY = "classes.jar"
    44	
    45	    /** AAR file 확장자 (manifest 검증 + 추출 분기). */
    46	    const val AAR_EXTENSION = ".aar"
    47	
    48	    /** JAR file 확장자 (manifest 검증). */
    49	    const val JAR_EXTENSION = ".jar"
    50	
    51	    /** AarExtractor 의 추출 결과 파일 suffix (artifactName 뒤에 붙음). */
    52	    const val EXTRACTED_JAR_SUFFIX = ".jar"
    53	
    54	    /** AarExtractor 의 atomic write 용 임시 파일 suffix. */
    55	    const val TEMP_JAR_SUFFIX = ".jar.tmp"
    56	
    57	    /** AarExtractor 의 캐시 키용 path digest 알고리즘. */
    58	    const val SHA1_DIGEST_NAME = "SHA-1"
    59	
    60	    /**
    61	     * AGP 8.x 가 emit 하는 통합 R.jar 경로 (compile_and_runtime_not_namespaced_r_class_jar variant).
    62	     * 본 경로는 AGP minor 버전 변경 시 깨질 수 있으나 8.x 안정 — 변경 시 본 상수만 갱신.
    63	     */
    64	    const val R_JAR_RELATIVE_PATH =
    65	        "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
    66	}
    67	```
    68	
    69	- [ ] **Step 2: 단일 sanity test 작성 — 상수 무결성**
    70	
    71	```kotlin
    72	// ClassLoaderConstantsTest.kt
    73	package dev.axp.layoutlib.worker.classloader
    74	
    75	import org.junit.jupiter.api.Test
    76	import kotlin.test.assertEquals
    77	import kotlin.test.assertTrue
    78	
    79	class ClassLoaderConstantsTest {
    80	
    81	    @Test
    82	    fun `manifest path 는 module-relative 이고 trailing 없음`() {
    83	        assertEquals("app/build/axp/runtime-classpath.txt", ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
    84	        assertTrue(!ClassLoaderConstants.MANIFEST_RELATIVE_PATH.startsWith("/"))
    85	    }
    86	
    87	    @Test
    88	    fun `aar 와 jar 확장자가 dot 으로 시작`() {
    89	        assertTrue(ClassLoaderConstants.AAR_EXTENSION.startsWith("."))
    90	        assertTrue(ClassLoaderConstants.JAR_EXTENSION.startsWith("."))
    91	        assertTrue(ClassLoaderConstants.TEMP_JAR_SUFFIX.startsWith("."))
    92	    }
    93	
    94	    @Test
    95	    fun `R_JAR 경로가 module-relative + 정확한 AGP 8 layout`() {
    96	        val expected = "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
    97	        assertEquals(expected, ClassLoaderConstants.R_JAR_RELATIVE_PATH)
    98	    }
    99	}
   100	```
   101	
   102	- [ ] **Step 3: 컴파일 + 테스트 실행**
   103	
   104	Run: `./server/gradlew -p server :layoutlib-worker:compileKotlin :layoutlib-worker:compileTestKotlin :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.ClassLoaderConstantsTest"`
   105	Expected: BUILD SUCCESSFUL, 3 tests PASS.
   106	
   107	- [ ] **Step 4: Commit (per CLAUDE.md commit-and-push standing rule)**
   108	
   109	```bash
   110	git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt \
   111	        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt
   112	git commit -m "feat(w3d3): ClassLoaderConstants — manifest/AAR cache/R.jar 경로 + 확장자 상수화 (...heredoc with Co-Authored-By...)"
   113	git push origin main
   114	```
   115	
   116	---
   117	
   118	## Task 2: `SampleAppClasspathManifest`
   119	
   120	**Files:**

exec
/bin/bash -lc "nl -ba docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md | sed -n '118,244p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   118	## Task 2: `SampleAppClasspathManifest`
   119	
   120	**Files:**
   121	- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifest.kt`
   122	- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifestTest.kt`
   123	
   124	- [ ] **Step 1: 5개 단위 테스트 작성 (TDD red)**
   125	
   126	```kotlin
   127	package dev.axp.layoutlib.worker.classloader
   128	
   129	import org.junit.jupiter.api.Test
   130	import org.junit.jupiter.api.assertThrows
   131	import org.junit.jupiter.api.io.TempDir
   132	import java.nio.file.Files
   133	import java.nio.file.Path
   134	import kotlin.test.assertEquals
   135	
   136	class SampleAppClasspathManifestTest {
   137	
   138	    @Test
   139	    fun `누락 파일 — IllegalArgumentException 명확 메시지`(@TempDir root: Path) {
   140	        val ex = assertThrows<IllegalArgumentException> {
   141	            SampleAppClasspathManifest.read(root)
   142	        }
   143	        assertEquals(true, ex.message!!.contains("manifest 누락"))
   144	        assertEquals(true, ex.message!!.contains("./gradlew :app:assembleDebug"))
   145	    }
   146	
   147	    @Test
   148	    fun `빈 파일 — IllegalStateException`(@TempDir root: Path) {
   149	        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
   150	        Files.createDirectories(mf.parent)
   151	        Files.writeString(mf, "")
   152	        assertThrows<IllegalStateException> { SampleAppClasspathManifest.read(root) }
   153	    }
   154	
   155	    @Test
   156	    fun `공백 라인 포함 — IllegalArgumentException with line index`(@TempDir root: Path) {
   157	        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
   158	        Files.createDirectories(mf.parent)
   159	        val realJar = Files.createFile(root.resolve("real.jar"))
   160	        Files.writeString(mf, "${realJar.toAbsolutePath()}\n   \n")
   161	        val ex = assertThrows<IllegalArgumentException> { SampleAppClasspathManifest.read(root) }
   162	        assertEquals(true, ex.message!!.contains("line 2"))
   163	    }
   164	
   165	    @Test
   166	    fun `비-절대경로 — IllegalArgumentException`(@TempDir root: Path) {
   167	        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
   168	        Files.createDirectories(mf.parent)
   169	        Files.writeString(mf, "relative/path.aar")
   170	        val ex = assertThrows<IllegalArgumentException> { SampleAppClasspathManifest.read(root) }
   171	        assertEquals(true, ex.message!!.contains("비-절대경로"))
   172	    }
   173	
   174	    @Test
   175	    fun `정상 — aar 와 jar 혼합, 순서 유지`(@TempDir root: Path) {
   176	        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
   177	        Files.createDirectories(mf.parent)
   178	        val a = Files.createFile(root.resolve("a.aar"))
   179	        val b = Files.createFile(root.resolve("b.jar"))
   180	        Files.writeString(mf, "${a.toAbsolutePath()}\n${b.toAbsolutePath()}")
   181	        val result = SampleAppClasspathManifest.read(root)
   182	        assertEquals(listOf(a.toAbsolutePath(), b.toAbsolutePath()), result.map { it.toAbsolutePath() })
   183	    }
   184	}
   185	```
   186	
   187	- [ ] **Step 2: 테스트 실행 → FAIL (object 미존재)**
   188	
   189	Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.SampleAppClasspathManifestTest"`
   190	Expected: 5 FAIL — `SampleAppClasspathManifest` unresolved.
   191	
   192	- [ ] **Step 3: 구현 작성**
   193	
   194	```kotlin
   195	package dev.axp.layoutlib.worker.classloader
   196	
   197	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
   198	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.JAR_EXTENSION
   199	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
   200	import java.nio.file.Files
   201	import java.nio.file.Path
   202	
   203	/**
   204	 * <sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt 파일을 읽어
   205	 * resolved runtime classpath 의 AAR/JAR 절대경로 리스트를 반환.
   206	 *
   207	 * 형식: 라인당 하나의 절대 경로, '\n' 구분, trailing newline 없음, distinct + sorted.
   208	 * 누락/비/공백 라인/비-절대/비-aar-jar 확장자/존재안하는 파일 → 모두 즉시 throw.
   209	 */
   210	object SampleAppClasspathManifest {
   211	
   212	    fun read(sampleAppModuleRoot: Path): List<Path> {
   213	        val mf = sampleAppModuleRoot.resolve(MANIFEST_RELATIVE_PATH)
   214	        require(Files.isRegularFile(mf)) {
   215	            "axp classpath manifest 누락: $mf — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 를 먼저 실행하세요"
   216	        }
   217	        val raw = Files.readString(mf)
   218	        if (raw.isBlank()) {
   219	            error("axp classpath manifest 가 비어있음: $mf")
   220	        }
   221	        return raw.split('\n').mapIndexed { idx, line ->
   222	            require(line.isNotBlank()) { "manifest line ${idx + 1} 이 공백" }
   223	            val p = Path.of(line)
   224	            require(p.isAbsolute) { "manifest line ${idx + 1} 이 비-절대경로: '$line'" }
   225	            require(line.endsWith(AAR_EXTENSION) || line.endsWith(JAR_EXTENSION)) {
   226	                "manifest line ${idx + 1} 의 확장자가 ${AAR_EXTENSION}/${JAR_EXTENSION} 가 아님: '$line'"
   227	            }
   228	            require(Files.isRegularFile(p)) { "manifest line ${idx + 1} 의 파일이 없음: $p" }
   229	            p
   230	        }
   231	    }
   232	}
   233	```
   234	
   235	- [ ] **Step 4: 테스트 PASS 확인**
   236	
   237	Run: 동일 명령. Expected: 5 PASS.
   238	
   239	- [ ] **Step 5: Commit**
   240	
   241	`git add` Manifest 두 파일 + commit.
   242	
   243	---
   244	

exec
/bin/bash -lc "nl -ba docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md | sed -n '245,397p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   245	## Task 3: `AarExtractor`
   246	
   247	**Files:**
   248	- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt`
   249	- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt`
   250	
   251	- [ ] **Step 1: 단위 테스트 4개 작성 (TDD red)**
   252	
   253	```kotlin
   254	package dev.axp.layoutlib.worker.classloader
   255	
   256	import org.junit.jupiter.api.Test
   257	import org.junit.jupiter.api.assertThrows
   258	import org.junit.jupiter.api.io.TempDir
   259	import java.nio.file.Files
   260	import java.nio.file.Path
   261	import java.util.zip.ZipEntry
   262	import java.util.zip.ZipOutputStream
   263	import kotlin.test.assertEquals
   264	import kotlin.test.assertNotNull
   265	import kotlin.test.assertNull
   266	import kotlin.test.assertTrue
   267	
   268	class AarExtractorTest {
   269	
   270	    private fun makeAar(parent: Path, name: String, withClassesJar: Boolean): Path {
   271	        val aar = parent.resolve("$name${ClassLoaderConstants.AAR_EXTENSION}")
   272	        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
   273	            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
   274	            zos.write("<?xml version='1.0'?><manifest/>".toByteArray())
   275	            zos.closeEntry()
   276	            if (withClassesJar) {
   277	                zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
   278	                // minimal valid empty zip bytes (PK\05\06 EOCD only)
   279	                zos.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
   280	                zos.closeEntry()
   281	            }
   282	        }
   283	        return aar
   284	    }
   285	
   286	    @Test
   287	    fun `정상 추출 — classes_jar 가 cache 에 atomic 으로 생성`(@TempDir root: Path) {
   288	        val cacheRoot = root.resolve("cache")
   289	        val aar = makeAar(root, "lib", withClassesJar = true)
   290	        val extracted = AarExtractor.extract(aar, cacheRoot)
   291	        assertNotNull(extracted)
   292	        assertTrue(Files.isRegularFile(extracted))
   293	        assertTrue(extracted.fileName.toString().endsWith(ClassLoaderConstants.EXTRACTED_JAR_SUFFIX))
   294	        // tmp 파일 청소 확인
   295	        val tmpName = "lib${ClassLoaderConstants.TEMP_JAR_SUFFIX}"
   296	        assertTrue(Files.list(extracted.parent).noneMatch { it.fileName.toString() == tmpName })
   297	    }
   298	
   299	    @Test
   300	    fun `cache hit — 두 번째 호출이 동일 path, 재추출 없음`(@TempDir root: Path) {
   301	        val cacheRoot = root.resolve("cache")
   302	        val aar = makeAar(root, "lib", withClassesJar = true)
   303	        val first = AarExtractor.extract(aar, cacheRoot)!!
   304	        val firstMtime = Files.getLastModifiedTime(first).toMillis()
   305	        Thread.sleep(15) // ensure mtime tick
   306	        val second = AarExtractor.extract(aar, cacheRoot)!!
   307	        assertEquals(first, second)
   308	        assertEquals(firstMtime, Files.getLastModifiedTime(second).toMillis())
   309	    }
   310	
   311	    @Test
   312	    fun `classes_jar 없는 AAR — null 반환`(@TempDir root: Path) {
   313	        val cacheRoot = root.resolve("cache")
   314	        val aar = makeAar(root, "resonly", withClassesJar = false)
   315	        assertNull(AarExtractor.extract(aar, cacheRoot))
   316	    }
   317	
   318	    @Test
   319	    fun `존재하지 않는 AAR — IllegalArgumentException`(@TempDir root: Path) {
   320	        val cacheRoot = root.resolve("cache")
   321	        val missing = root.resolve("missing.aar")
   322	        assertThrows<IllegalArgumentException> { AarExtractor.extract(missing, cacheRoot) }
   323	    }
   324	}
   325	```
   326	
   327	- [ ] **Step 2: 테스트 실행 → FAIL**
   328	
   329	- [ ] **Step 3: 구현**
   330	
   331	```kotlin
   332	package dev.axp.layoutlib.worker.classloader
   333	
   334	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
   335	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
   336	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
   337	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTRACTED_JAR_SUFFIX
   338	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.SHA1_DIGEST_NAME
   339	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.TEMP_JAR_SUFFIX
   340	import java.nio.file.Files
   341	import java.nio.file.Path
   342	import java.nio.file.StandardCopyOption.ATOMIC_MOVE
   343	import java.nio.file.StandardCopyOption.REPLACE_EXISTING
   344	import java.security.MessageDigest
   345	import java.util.zip.ZipFile
   346	import kotlin.io.path.isRegularFile
   347	import kotlin.io.path.outputStream
   348	
   349	/**
   350	 * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출. mtime 기반 idempotent + atomic write.
   351	 *
   352	 * Cache 위치: `<cacheRoot>/aar-classes/<sha1(absPath)>/<artifactName>.jar`.
   353	 * AAR 안에 classes.jar 없는 경우 (resource-only) → null.
   354	 */
   355	object AarExtractor {
   356	
   357	    fun extract(aarPath: Path, cacheRoot: Path): Path? {
   358	        require(aarPath.isRegularFile()) { "AAR 누락: $aarPath" }
   359	        val key = sha1(aarPath.toAbsolutePath().toString())
   360	        val artifactName = aarPath.fileName.toString().removeSuffix(AAR_EXTENSION)
   361	        val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR).resolve(key)
   362	        Files.createDirectories(outDir)
   363	        val outJar = outDir.resolve(artifactName + EXTRACTED_JAR_SUFFIX)
   364	
   365	        val aarMtime = Files.getLastModifiedTime(aarPath).toMillis()
   366	        if (outJar.isRegularFile() &&
   367	            Files.getLastModifiedTime(outJar).toMillis() >= aarMtime) {
   368	            return outJar
   369	        }
   370	
   371	        val tmpJar = outDir.resolve(artifactName + TEMP_JAR_SUFFIX)
   372	        ZipFile(aarPath.toFile()).use { zip ->
   373	            val entry = zip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
   374	            zip.getInputStream(entry).use { input ->
   375	                tmpJar.outputStream().use { output -> input.copyTo(output) }
   376	            }
   377	        }
   378	        Files.move(tmpJar, outJar, ATOMIC_MOVE, REPLACE_EXISTING)
   379	        return outJar
   380	    }
   381	
   382	    private fun sha1(s: String): String {
   383	        val md = MessageDigest.getInstance(SHA1_DIGEST_NAME)
   384	        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
   385	    }
   386	}
   387	```
   388	
   389	- [ ] **Step 4: 테스트 PASS**
   390	
   391	Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.AarExtractorTest"`
   392	Expected: 4 PASS.
   393	
   394	- [ ] **Step 5: Commit**
   395	
   396	---
   397	

exec
/bin/bash -lc "nl -ba docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md | sed -n '398,619p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   398	## Task 4: `SampleAppClassLoader`
   399	
   400	**Files:**
   401	- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt`
   402	- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt`
   403	
   404	- [ ] **Step 1: 단위 테스트 3개 (TempDir + minimal AAR/jar/R.jar fixture)**
   405	
   406	```kotlin
   407	package dev.axp.layoutlib.worker.classloader
   408	
   409	import org.junit.jupiter.api.Test
   410	import org.junit.jupiter.api.assertThrows
   411	import org.junit.jupiter.api.io.TempDir
   412	import java.nio.file.Files
   413	import java.nio.file.Path
   414	import java.util.zip.ZipEntry
   415	import java.util.zip.ZipOutputStream
   416	import kotlin.test.assertEquals
   417	import kotlin.test.assertSame
   418	import kotlin.test.assertTrue
   419	
   420	class SampleAppClassLoaderTest {
   421	
   422	    private fun makeTinyAar(parent: Path, name: String): Path {
   423	        val aar = parent.resolve("$name${ClassLoaderConstants.AAR_EXTENSION}")
   424	        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
   425	            zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
   426	            zos.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
   427	            zos.closeEntry()
   428	        }
   429	        return aar
   430	    }
   431	
   432	    private fun makeTinyJar(parent: Path, name: String): Path {
   433	        val jar = parent.resolve("$name${ClassLoaderConstants.JAR_EXTENSION}")
   434	        ZipOutputStream(Files.newOutputStream(jar)).use { zos ->
   435	            zos.putNextEntry(ZipEntry("dummy.txt"))
   436	            zos.write("ok".toByteArray())
   437	            zos.closeEntry()
   438	        }
   439	        return jar
   440	    }
   441	
   442	    private fun setupModuleRoot(@TempDir root: Path): Path {
   443	        val mfDir = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH).parent
   444	        Files.createDirectories(mfDir)
   445	        val rJarDir = root.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH).parent
   446	        Files.createDirectories(rJarDir)
   447	        val rJar = root.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH)
   448	        Files.copy(makeTinyJar(root, "rjar-source"), rJar)
   449	        return root
   450	    }
   451	
   452	    @Test
   453	    fun `정상 빌드 — parent chain + AAR classes jar + R_jar 모두 URL 에 포함`(@TempDir root: Path) {
   454	        setupModuleRoot(root)
   455	        val aar = makeTinyAar(root, "lib")
   456	        val jar = makeTinyJar(root, "extra")
   457	        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
   458	        Files.writeString(mf, "${aar.toAbsolutePath()}\n${jar.toAbsolutePath()}")
   459	
   460	        val parent = ClassLoader.getSystemClassLoader()
   461	        val cl = SampleAppClassLoader.build(root, parent)
   462	        assertSame(parent, cl.classLoader.parent)
   463	        assertEquals(3, cl.urls.size)  // aar-extracted + jar + R.jar
   464	    }
   465	
   466	    @Test
   467	    fun `R_jar 누락 — 명확한 메시지로 require fail`(@TempDir root: Path) {
   468	        val mfDir = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH).parent
   469	        Files.createDirectories(mfDir)
   470	        Files.writeString(root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH), "")
   471	        // intentionally do NOT create R.jar
   472	        val parent = ClassLoader.getSystemClassLoader()
   473	        val ex = assertThrows<IllegalStateException> { SampleAppClassLoader.build(root, parent) }
   474	        // 빈 manifest 가 먼저 발화되거나 R.jar 누락이 발화 — 둘 다 IllegalStateException 또는
   475	        // IllegalArgumentException. ex 가 둘 중 하나면 된다.
   476	    }
   477	
   478	    @Test
   479	    fun `manifest 의 jar 항목은 그대로 URL 화 됨`(@TempDir root: Path) {
   480	        setupModuleRoot(root)
   481	        val jar = makeTinyJar(root, "passthru")
   482	        Files.writeString(root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH), jar.toAbsolutePath().toString())
   483	        val cl = SampleAppClassLoader.build(root, ClassLoader.getSystemClassLoader())
   484	        assertTrue(cl.urls.any { it.toString().endsWith("passthru${ClassLoaderConstants.JAR_EXTENSION}") })
   485	    }
   486	}
   487	```
   488	
   489	- [ ] **Step 2: 테스트 실행 → FAIL**
   490	
   491	- [ ] **Step 3: 구현 — spec §4.4 그대로**
   492	
   493	```kotlin
   494	package dev.axp.layoutlib.worker.classloader
   495	
   496	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_BASE_RELATIVE_PATH
   497	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
   498	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
   499	import java.net.URL
   500	import java.net.URLClassLoader
   501	import java.nio.file.Files
   502	import java.nio.file.Path
   503	
   504	class SampleAppClassLoader private constructor(
   505	    val classLoader: ClassLoader,
   506	    val urls: List<URL>,
   507	) {
   508	    companion object {
   509	        fun build(sampleAppModuleRoot: Path, parent: ClassLoader): SampleAppClassLoader {
   510	            val manifest = SampleAppClasspathManifest.read(sampleAppModuleRoot)
   511	            val cacheRoot = sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)
   512	            val urls = mutableListOf<URL>()
   513	
   514	            for (entry in manifest) {
   515	                val asString = entry.toString()
   516	                val jarPath = if (asString.endsWith(AAR_EXTENSION)) {
   517	                    AarExtractor.extract(entry, cacheRoot) ?: continue
   518	                } else {
   519	                    entry
   520	                }
   521	                urls += jarPath.toUri().toURL()
   522	            }
   523	
   524	            val rJar = sampleAppModuleRoot.resolve(R_JAR_RELATIVE_PATH)
   525	            require(Files.isRegularFile(rJar)) {
   526	                "sample-app R.jar 누락: $rJar — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 필요"
   527	            }
   528	            urls += rJar.toUri().toURL()
   529	
   530	            val cl = URLClassLoader(urls.toTypedArray(), parent)
   531	            return SampleAppClassLoader(cl, urls.toList())
   532	        }
   533	    }
   534	}
   535	```
   536	
   537	- [ ] **Step 4: 테스트 PASS**
   538	
   539	- [ ] **Step 5: Commit**
   540	
   541	---
   542	
   543	## Task 5: `FixtureDiscovery.locateModuleRoot` 추가
   544	
   545	**Files:**
   546	- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt`
   547	- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt` (기존 + 확장)
   548	
   549	- [ ] **Step 1: 새 케이스 2개 추가 (locateModuleRoot)**
   550	
   551	```kotlin
   552	@Test
   553	fun `locateModuleRoot — override 디렉토리 그대로 반환`(@TempDir root: Path) {
   554	    val moduleDir = Files.createDirectory(root.resolve("custom-sample-app"))
   555	    val result = FixtureDiscovery.locateModuleRoot(moduleDir)
   556	    assertEquals(moduleDir, result)
   557	}
   558	
   559	@Test
   560	fun `locateModuleRoot — candidate root 에서 fixture sample-app 디렉토리 발견`(@TempDir root: Path) {
   561	    val moduleDir = root.resolve("fixture").resolve("sample-app")
   562	    Files.createDirectories(moduleDir)
   563	    val result = FixtureDiscovery.locateInternalModuleRoot(
   564	        override = null,
   565	        userDir = root.toString(),
   566	        candidateRoots = FixtureDiscovery.CANDIDATE_ROOTS,
   567	    )
   568	    assertEquals(moduleDir, result)
   569	}
   570	```
   571	
   572	- [ ] **Step 2: FixtureDiscovery 수정 — `locateModuleRoot` + `locateInternalModuleRoot` + `FIXTURE_MODULE_SUBPATH` 상수 추가**
   573	
   574	```kotlin
   575	const val FIXTURE_MODULE_SUBPATH = "fixture/sample-app"
   576	
   577	fun locateModuleRoot(override: Path?): Path? = locateInternalModuleRoot(
   578	    override = override,
   579	    userDir = System.getProperty("user.dir"),
   580	    candidateRoots = CANDIDATE_ROOTS,
   581	)
   582	
   583	internal fun locateInternalModuleRoot(
   584	    override: Path?,
   585	    userDir: String,
   586	    candidateRoots: List<String>,
   587	): Path? = locateInternalGeneric(override, userDir, candidateRoots, FIXTURE_MODULE_SUBPATH)
   588	
   589	internal fun locateInternalGeneric(
   590	    override: Path?,
   591	    userDir: String,
   592	    candidateRoots: List<String>,
   593	    subpath: String,
   594	): Path? {
   595	    if (override != null) {
   596	        require(Files.isDirectory(override)) {
   597	            "fixture root 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
   598	        }
   599	        return override
   600	    }
   601	    val candidates: List<Path> = candidateRoots.flatMap { root ->
   602	        if (root.isEmpty()) listOf(Paths.get(subpath), Paths.get(userDir, subpath))
   603	        else listOf(Paths.get(root, subpath), Paths.get(userDir, root, subpath))
   604	    }
   605	    return candidates.firstOrNull { Files.isDirectory(it) }
   606	}
   607	```
   608	
   609	기존 `locateInternal` 은 `locateInternalGeneric(... , FIXTURE_SUBPATH)` 로 위임 (동작 동일).
   610	
   611	- [ ] **Step 3: 전체 테스트 실행 — 기존 5 + 신규 2 모두 PASS**
   612	
   613	Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.FixtureDiscoveryTest"`
   614	Expected: 7 PASS.
   615	
   616	- [ ] **Step 4: Commit**
   617	
   618	---
   619	

exec
/bin/bash -lc "nl -ba docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md | sed -n '620,777p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   620	## Task 6: `MinimalLayoutlibCallback` 변경 (loadView + findClass + hasAndroidXAppCompat)
   621	
   622	**Files:**
   623	- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`
   624	- Modify (small): `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt` — 기존 테스트 새 시그니처에 맞게 업데이트.
   625	- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackLoadViewTest.kt` (3 tests)
   626	
   627	- [ ] **Step 1: 신규 테스트 3개 작성**
   628	
   629	```kotlin
   630	package dev.axp.layoutlib.worker.session
   631	
   632	import org.junit.jupiter.api.Test
   633	import org.junit.jupiter.api.assertThrows
   634	import kotlin.test.assertNotNull
   635	import kotlin.test.assertSame
   636	
   637	class MinimalLayoutlibCallbackLoadViewTest {
   638	
   639	    private fun newCallback(provider: () -> ClassLoader): MinimalLayoutlibCallback =
   640	        MinimalLayoutlibCallback(provider)
   641	
   642	    @Test
   643	    fun `loadView — 기본 String 클래스 로드 성공 (자기 CL 활용)`() {
   644	        val cb = newCallback { ClassLoader.getSystemClassLoader() }
   645	        // String 의 (CharSequence) 생성자
   646	        val v = cb.loadView("java.lang.StringBuilder", arrayOf(CharSequence::class.java), arrayOf<Any>("hi"))
   647	        assertNotNull(v)
   648	    }
   649	
   650	    @Test
   651	    fun `loadView — 미지 클래스 ClassNotFoundException`() {
   652	        val cb = newCallback { ClassLoader.getSystemClassLoader() }
   653	        assertThrows<ClassNotFoundException> {
   654	            cb.loadView("does.not.Exist", arrayOf(), arrayOf())
   655	        }
   656	    }
   657	
   658	    @Test
   659	    fun `findClass — provider CL 위임`() {
   660	        val parent = ClassLoader.getSystemClassLoader()
   661	        val cb = newCallback { parent }
   662	        val cls = cb.findClass("java.lang.StringBuilder")
   663	        assertSame(parent, cls.classLoader)
   664	    }
   665	
   666	    @Test
   667	    fun `hasAndroidXAppCompat — true`() {
   668	        val cb = newCallback { ClassLoader.getSystemClassLoader() }
   669	        kotlin.test.assertTrue(cb.hasAndroidXAppCompat())
   670	    }
   671	}
   672	```
   673	
   674	- [ ] **Step 2: 기존 MinimalLayoutlibCallbackTest.kt — `MinimalLayoutlibCallback()` no-arg 호출 부분을 `MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }` 로 업데이트**
   675	
   676	(파일 안의 모든 호출부에 lambda 인자 명시 — sed/grep 으로 일괄 변환 가능)
   677	
   678	- [ ] **Step 3: MinimalLayoutlibCallback 수정**
   679	
   680	```kotlin
   681	package dev.axp.layoutlib.worker.session
   682	
   683	import com.android.ide.common.rendering.api.ActionBarCallback
   684	import com.android.ide.common.rendering.api.AdapterBinding
   685	import com.android.ide.common.rendering.api.ILayoutPullParser
   686	import com.android.ide.common.rendering.api.LayoutlibCallback
   687	import com.android.ide.common.rendering.api.ResourceReference
   688	import com.android.ide.common.rendering.api.ResourceValue
   689	import org.kxml2.io.KXmlParser
   690	import org.xmlpull.v1.XmlPullParser
   691	import java.lang.reflect.InvocationTargetException
   692	import java.util.concurrent.atomic.AtomicInteger
   693	
   694	/**
   695	 * W3D3-L3-CLASSLOADER (round 2 페어 리뷰 반영).
   696	 *
   697	 * 변경:
   698	 *  - loadView: viewClassLoaderProvider 로부터 lazy 로 CL 받아 reflection-instantiate.
   699	 *    InvocationTargetException 의 cause 를 unwrap (Q3, Codex 입장).
   700	 *  - findClass: BridgeInflater.findCustomInflater 의 AppCompat 자동 치환 활성화 (F1).
   701	 *  - hasAndroidXAppCompat: true (F1) — sample-app 의존 그래프 보유.
   702	 *
   703	 * lazy provider (Q4): activity_minimal 처럼 custom view 없는 fixture 는 호출 0 회 →
   704	 * sample-app manifest 누락이 silent (W3D1 의 `tier3-values` 회귀 없음).
   705	 */
   706	class MinimalLayoutlibCallback(
   707	    private val viewClassLoaderProvider: () -> ClassLoader,
   708	) : LayoutlibCallback() {
   709	
   710	    private val nextId = AtomicInteger(FIRST_ID)
   711	    private val byRef = mutableMapOf<ResourceReference, Int>()
   712	    private val byId = mutableMapOf<Int, ResourceReference>()
   713	
   714	    @Synchronized
   715	    override fun getOrGenerateResourceId(ref: ResourceReference): Int {
   716	        byRef[ref]?.let { return it }
   717	        val id = nextId.getAndIncrement()
   718	        byRef[ref] = id
   719	        byId[id] = ref
   720	        return id
   721	    }
   722	
   723	    @Synchronized
   724	    override fun resolveResourceId(id: Int): ResourceReference? = byId[id]
   725	
   726	    override fun loadView(
   727	        name: String,
   728	        constructorSignature: Array<out Class<*>>?,
   729	        constructorArgs: Array<out Any>?,
   730	    ): Any {
   731	        val cls = viewClassLoaderProvider().loadClass(name)
   732	        val sig = constructorSignature ?: emptyArray()
   733	        val args = constructorArgs ?: emptyArray()
   734	        val ctor = cls.getDeclaredConstructor(*sig)
   735	        ctor.isAccessible = true
   736	        try {
   737	            return ctor.newInstance(*args)
   738	        } catch (ite: InvocationTargetException) {
   739	            // Q3: cause unwrap — layoutlib 의 BridgeInflater 가 InflateException 으로 wrap 함.
   740	            throw ite.cause ?: ite
   741	        }
   742	    }
   743	
   744	    override fun findClass(name: String): Class<*> {
   745	        return viewClassLoaderProvider().loadClass(name)
   746	    }
   747	
   748	    override fun hasAndroidXAppCompat(): Boolean = true
   749	
   750	    override fun getParser(layoutResource: ResourceValue?): ILayoutPullParser? = null
   751	    override fun getAdapterBinding(cookie: Any?, attributes: Map<String, String>): AdapterBinding? = null
   752	    override fun getActionBarCallback(): ActionBarCallback = ActionBarCallback()
   753	    override fun createXmlParser(): XmlPullParser = buildKxml()
   754	    override fun createXmlParserForFile(fileName: String): XmlPullParser = buildKxml()
   755	    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser = buildKxml()
   756	    override fun getApplicationId(): String = APPLICATION_ID
   757	
   758	    private fun buildKxml(): XmlPullParser = KXmlParser().also {
   759	        it.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
   760	    }
   761	
   762	    companion object {
   763	        private const val FIRST_ID = 0x7F0A_0000
   764	        private const val APPLICATION_ID = "axp.render"
   765	    }
   766	}
   767	```
   768	
   769	- [ ] **Step 4: 전체 callback 단위 테스트 PASS 확인**
   770	
   771	Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.session.MinimalLayoutlibCallback*"`
   772	Expected: 기존 + 신규 모두 PASS.
   773	
   774	- [ ] **Step 5: Commit**
   775	
   776	---
   777	

exec
/bin/bash -lc "nl -ba docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md | sed -n '778,862p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   778	## Task 7: `LayoutlibRenderer` — sampleAppModuleRoot 인자 + lazy provider 배선
   779	
   780	**Files:**
   781	- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`
   782	
   783	- [ ] **Step 1: 생성자에 `sampleAppModuleRoot: Path` 추가 + `ensureSampleAppClassLoader` private 메서드**
   784	
   785	```kotlin
   786	class LayoutlibRenderer(
   787	    private val distDir: Path,
   788	    private val fallback: PngRenderer?,
   789	    private val fixtureRoot: Path,
   790	    private val sampleAppModuleRoot: Path,
   791	) : PngRenderer {
   792	    // ... 기존 필드 그대로 ...
   793	
   794	    @Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null
   795	
   796	    @Synchronized
   797	    private fun ensureSampleAppClassLoader(): ClassLoader {
   798	        sampleAppClassLoader?.let { return it.classLoader }
   799	        val isolated = classLoader ?: error("Bridge 가 init 안 됨 (initBridge 가 먼저 실행되어야 함)")
   800	        val built = SampleAppClassLoader.build(sampleAppModuleRoot, isolated)
   801	        sampleAppClassLoader = built
   802	        return built.classLoader
   803	    }
   804	```
   805	
   806	- [ ] **Step 2: `renderViaLayoutlib` 안의 `MinimalLayoutlibCallback()` 호출을 `MinimalLayoutlibCallback { ensureSampleAppClassLoader() }` 로 교체**
   807	
   808	해당 라인은 `LayoutlibRenderer.kt:170` 부근 (W2D7 시점 기준; 현재 정확한 라인은 grep `MinimalLayoutlibCallback()`).
   809	
   810	- [ ] **Step 3: 컴파일만 — 컴파일 FAIL 예상 (호출부 모두 sampleAppModuleRoot 인자 누락)**
   811	
   812	Run: `./server/gradlew -p server :layoutlib-worker:compileKotlin :mcp-server:compileKotlin`
   813	Expected: FAIL — 호출부가 새 인자를 안 줌.
   814	
   815	(여기서 commit 하지 않고 Task 8 까지 묶어서 처리 — 컴파일이 깨진 상태는 task unit 미완으로 간주.)
   816	
   817	---
   818	
   819	## Task 8: 모든 호출부 업데이트 — Main.kt CLI + SharedLayoutlibRenderer + 모든 테스트
   820	
   821	**Files:**
   822	- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt`
   823	- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt`
   824	- Modify: `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt`
   825	- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt`
   826	- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt`
   827	- Modify: 모든 production `LayoutlibRenderer(...)` 호출하는 테스트 (W3D1: `LayoutlibRendererTier3MinimalTest`, integration tests).
   828	- Modify: `LayoutlibRendererIntegrationTest.kt` — 곧 Task 10 에서 enable.
   829	
   830	- [ ] **Step 1: CliConstants/CliArgs — `--sample-app-root=<path>` 플래그 추가**
   831	
   832	CliConstants.VALUE_BEARING_FLAGS 에 `"--sample-app-root"` 추가.
   833	CliArgs 데이터클래스에 `val sampleAppRoot: String?` 추가, parse 가 채움.
   834	
   835	- [ ] **Step 2: Main.kt `chooseRenderer` — `FixtureDiscovery.locateModuleRoot(cliArgs.sampleAppRoot?.let { Path.of(it) })` 결과를 LayoutlibRenderer 에 주입**
   836	
   837	기존 `--fixture-root` 처리 옆에 동일 패턴.
   838	
   839	- [ ] **Step 3: SharedLayoutlibRenderer.getOrCreate(distDir, fixtureRoot, sampleAppModuleRoot, fallback)**
   840	
   841	W3D2 의 시그니처 그대로 확장. binding 은 `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 호출.
   842	
   843	- [ ] **Step 4: 모든 production renderer 호출 grep + 업데이트**
   844	
   845	```bash
   846	grep -rn "LayoutlibRenderer(" server/ --include="*.kt"
   847	grep -rn "SharedLayoutlibRenderer.getOrCreate" server/ --include="*.kt"
   848	```
   849	
   850	각 호출부에서 `sampleAppModuleRoot` 인자 명시. 테스트는 `FixtureDiscovery.locateModuleRoot(null)!!` 패턴 사용.
   851	
   852	- [ ] **Step 5: `LayoutlibRendererTier3MinimalTest.kt` — companion sharedRenderer 생성 시 sampleAppModuleRoot 추가**
   853	
   854	- [ ] **Step 6: 전체 build + test 실행 — 컴파일 PASS + 기존 테스트 (118 unit + 11 integration) 회귀 없음 확인**
   855	
   856	Run: `./server/gradlew -p server build`
   857	Expected: BUILD SUCCESSFUL. activity_basic.xml 의 `LayoutlibRendererIntegrationTest` 는 아직 `@Disabled` 상태 — 회귀 없음.
   858	
   859	- [ ] **Step 7: Commit (Task 7 + Task 8 묶음 — atomic compilation unit)**
   860	
   861	---
   862	

exec
/bin/bash -lc "nl -ba docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md | sed -n '863,929p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   863	## Task 9: Gradle `axpEmitClasspath` task + 첫 manifest 생성
   864	
   865	**Files:**
   866	- Modify: `fixture/sample-app/app/build.gradle.kts`
   867	
   868	- [ ] **Step 1: Gradle 신규 task 추가**
   869	
   870	`build.gradle.kts` 의 끝에 spec §4.1 의 코드 그대로 추가.
   871	
   872	- [ ] **Step 2: 한 번 수동 실행 — manifest 가 expected 위치에 생성되는지 확인**
   873	
   874	```bash
   875	cd fixture/sample-app && ./gradlew :app:assembleDebug 2>&1 | tail -10
   876	ls -la app/build/axp/runtime-classpath.txt
   877	head -5 app/build/axp/runtime-classpath.txt
   878	wc -l app/build/axp/runtime-classpath.txt
   879	```
   880	
   881	Expected: 파일 존재, 첫 5 라인이 절대경로, 라인수 ≥ 30 (transitive deps).
   882	
   883	- [ ] **Step 3: Commit**
   884	
   885	(`app/build/` 는 .gitignore — 코드만 commit.)
   886	
   887	---
   888	
   889	## Task 10: `LayoutlibRendererIntegrationTest` enable + 첫 실측 + B2 contingency 결정
   890	
   891	**Files:**
   892	- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`
   893	
   894	- [ ] **Step 1: `@Disabled` 제거 + spec §4.8 의 코드 적용 (SUCCESS assertion 활성, sampleAppModuleRoot 주입)**
   895	
   896	- [ ] **Step 2: 통합 테스트 실행**
   897	
   898	Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest" -PincludeTags=integration`
   899	Expected: 1 test PASS or FAIL — 결과에 따라 분기.
   900	
   901	- [ ] **Step 3: 결과 분기**
   902	
   903	  **(A) PASS — B2 가설 무효, T1 gate 통과**:
   904	  - 다음 Task 로 진행 (전체 게이트 + work_log).
   905	
   906	  **(B) FAIL with `MaterialThemeOverlay`/`ThemeEnforcement`/`Material*` 관련 stack trace**:
   907	  - B2 가설 confirmed → §9.1 contingency 발동.
   908	  - `fixture/sample-app/app/src/main/res/layout/activity_basic_minimal.xml` 작성 (spec §9.1 의 마크업).
   909	  - `LayoutlibRendererIntegrationTest` 의 layoutName 을 `"activity_basic_minimal.xml"` 로 변경.
   910	  - 다시 실행 → PASS 기대.
   911	
   912	  **(C) FAIL with 다른 원인** (e.g., R class missing, ClassNotFoundException 의 다른 cause):
   913	  - stack trace 분석 → 본 플랜의 누락된 컴포넌트 식별 → 그 컴포넌트의 task 재진입 / 신규 task 추가.
   914	
   915	- [ ] **Step 4: 전체 게이트 실행**
   916	
   917	```bash
   918	./server/gradlew -p server test                                          # 전체 unit
   919	./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 전체 integration
   920	./server/gradlew -p server build                                         # BUILD SUCCESSFUL
   921	./server/mcp-server/build/install/mcp-server/bin/mcp-server --smoke      # "ok"
   922	```
   923	
   924	Expected: 모든 게이트 통과. Unit 118+ → 130+ (15 신규), integration 11 → 12 PASS, tier3-glyph 만 SKIPPED.
   925	
   926	- [ ] **Step 5: Commit (Task 10 unit)**
   927	
   928	---
   929	

exec
/bin/bash -lc "nl -ba docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md | sed -n '930,978p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   930	## Task 11: work_log + handoff 업데이트
   931	
   932	**Files:**
   933	- Create: `docs/work_log/2026-04-29_w3d3-l3-classloader/session-log.md`
   934	- Create: `docs/work_log/2026-04-29_w3d3-l3-classloader/handoff.md`
   935	- Modify: `docs/plan/08-integration-reconciliation.md` (W3D3 close section 추가)
   936	
   937	- [ ] **Step 1: session-log 작성** — 변경 파일 목록, 테스트 카운트 변화, 발견된 landmine, B2 contingency 발동 여부, pair-review verdicts.
   938	
   939	- [ ] **Step 2: handoff 작성** — 다음 세션의 W3D4 후보 (app/library resource value loading, 또는 tier3-glyph).
   940	
   941	- [ ] **Step 3: `docs/plan/08-integration-reconciliation.md` §7.7.4 추가 (W3D3 close 기록)**.
   942	
   943	- [ ] **Step 4: Commit**
   944	
   945	---
   946	
   947	## Self-Review (writing-plans 종결 체크리스트)
   948	
   949	**Spec coverage**:
   950	- §0 페어 리뷰 결과 → Task 1-10 구현으로 모두 반영.
   951	- §1.1 deliverables (manifest, AAR loader, callback override, integration enable) → Task 1-4, 6, 9, 10.
   952	- §1.2 out-of-scope → 본 플랜 미터치 (W3D4 carry).
   953	- §1.3 acceptance gate (T1) → Task 10 Step 2.
   954	- §2 컴포넌트 분해 → Task 별 1:1 매핑 (C0/C1/C2/C3/C4/C5/C6/C7/C8 → Task 5/9/2/3/4/1/6/7/10).
   955	- §3 데이터 흐름 → Task 7+8 의 LayoutlibRenderer 수정으로 wire.
   956	- §4 컴포넌트 스펙 → Task 1-7 의 코드 블록.
   957	- §5 테스트 전략 → Task 1-4, 6 의 단위 + Task 10 의 통합.
   958	- §6 위험 → Task 10 Step 3 의 분기 (R1/R3/R4).
   959	- §7 페어 리뷰 Q1-Q5 → spec round 2 에서 모두 처리, 코드 반영.
   960	- §8 변경 로그 → Task 11 step 3 가 plan/08 에 추가.
   961	- §9 contingency → Task 10 Step 3 (B).
   962	
   963	**No placeholders**: 모든 Task 가 exact code + exact commands + exact expected output. "TODO" 없음.
   964	
   965	**Type consistency**: `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 시그니처가 모든 task 에서 일관. `MinimalLayoutlibCallback { provider }` lambda 도.
   966	
   967	---
   968	
   969	## Execution Handoff
   970	
   971	**Plan complete and saved to `docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md`. Two execution options:**
   972	
   973	1. **Subagent-Driven (recommended)** — fresh subagent per task, two-stage review.
   974	2. **Inline Execution** — batch with checkpoints.
   975	
   976	**Pre-execution gating**: 본 플랜 자체에 대한 round 2 페어 리뷰 (Codex+Claude 1:1, planning phase 마지막 게이트) 가 먼저 수행됨 — verdict GO 또는 GO_WITH_FOLLOWUPS 일 때만 구현 phase 진입.
   977	
   978	**Execution choice**: Subagent-Driven (CLAUDE.md "Implementation phase = Claude-only, no Codex" 정책 + W3D2 에서 검증된 패턴).

codex
The plan is written with exact snippets, so I’m checking those snippets as if they were patches. A few likely risks are in the snippet itself rather than in high-level architecture.
exec
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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

exec
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.session
     2	
     3	import com.android.ide.common.rendering.api.ResourceNamespace
     4	import com.android.ide.common.rendering.api.ResourceReference
     5	import com.android.resources.ResourceType
     6	import org.junit.jupiter.api.Assertions.assertEquals
     7	import org.junit.jupiter.api.Assertions.assertNotEquals
     8	import org.junit.jupiter.api.Assertions.assertNotNull
     9	import org.junit.jupiter.api.Assertions.assertNull
    10	import org.junit.jupiter.api.Test
    11	
    12	/**
    13	 * W2D7-RENDERSESSION — MinimalLayoutlibCallback 의 resource id 양방향 맵 + 기본값 계약 검증.
    14	 *
    15	 * LayoutlibCallback 은 abstract class. Bridge 내부가 callback.getOrGenerateResourceId 로 int
    16	 * 를 받고 나중에 callback.resolveResourceId 로 역참조. 양방향 stable mapping 이 필수.
    17	 */
    18	class MinimalLayoutlibCallbackTest {
    19	
    20	    @Test
    21	    fun `getOrGenerateResourceId returns stable id across calls`() {
    22	        val cb = MinimalLayoutlibCallback()
    23	        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
    24	        val first = cb.getOrGenerateResourceId(ref)
    25	        val second = cb.getOrGenerateResourceId(ref)
    26	        assertEquals(first, second)
    27	    }
    28	
    29	    @Test
    30	    fun `different references get different ids`() {
    31	        val cb = MinimalLayoutlibCallback()
    32	        val title = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
    33	        val body = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "body")
    34	        assertNotEquals(cb.getOrGenerateResourceId(title), cb.getOrGenerateResourceId(body))
    35	    }
    36	
    37	    @Test
    38	    fun `resolveResourceId returns registered reference`() {
    39	        val cb = MinimalLayoutlibCallback()
    40	        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
    41	        val id = cb.getOrGenerateResourceId(ref)
    42	        assertEquals(ref, cb.resolveResourceId(id))
    43	    }
    44	
    45	    @Test
    46	    fun `resolveResourceId returns null for unknown id`() {
    47	        val cb = MinimalLayoutlibCallback()
    48	        assertNull(cb.resolveResourceId(0x7F999999))
    49	    }
    50	
    51	    @Test
    52	    fun `getAdapterBinding is null`() {
    53	        val cb = MinimalLayoutlibCallback()
    54	        assertNull(cb.getAdapterBinding(Any(), emptyMap()))
    55	    }
    56	
    57	    @Test
    58	    fun `getActionBarCallback is non-null`() {
    59	        val cb = MinimalLayoutlibCallback()
    60	        assertNotNull(cb.getActionBarCallback())
    61	    }
    62	
    63	    @Test
    64	    fun `getParser returns null for any resource value`() {
    65	        val cb = MinimalLayoutlibCallback()
    66	        assertNull(cb.getParser(null))
    67	    }
    68	
    69	    @Test
    70	    fun `applicationId is stable axp token`() {
    71	        val cb = MinimalLayoutlibCallback()
    72	        assertEquals("axp.render", cb.applicationId)
    73	    }
    74	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import java.nio.file.Path
     4	
     5	/**
     6	 * W3D2 integration cleanup (Codex F4 carry) — SharedLayoutlibRenderer 의 args 일관성
     7	 * 검증용 pure helper. LayoutlibRenderer 생성 없이 단위 테스트 가능하도록 분리.
     8	 *
     9	 * W2D7 L4: native lib 는 프로세스당 1회만 로드되고 첫 dist 에 바인드됨. 동일
    10	 * JVM 에서 다른 dist 로 singleton 을 재사용하면 진단 어려운 실패 발생 →
    11	 * 두 번째 호출의 args 가 첫 호출과 동일해야 함.
    12	 */
    13	internal object SharedRendererBinding
    14	{
    15	    /**
    16	     * 첫 바인드 (`bound == null`) 이면 silently 통과. 이후는 `bound == requested`
    17	     * 여야 하며 불일치 시 [IllegalStateException].
    18	     */
    19	    fun verify(bound: Pair<Path, Path>?, requested: Pair<Path, Path>)
    20	    {
    21	        if (bound == null) return
    22	        check(bound == requested) {
    23	            "SharedLayoutlibRenderer args 불일치 — native lib 는 첫 바인드 args 에 고정. " +
    24	                "bound=$bound requested=$requested"
    25	        }
    26	    }
    27	}

exec
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc 'nl -ba server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
exec
/bin/bash -lc "nl -ba server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.mcp
     2	
     3	/**
     4	 * W3D2 integration cleanup (Codex F3 carry) — 간단한 CLI 인자 파서.
     5	 *
     6	 * GNU-style `--key=value` 와 `--flag` (value 없음) 모두 지원. 비-플래그 위치 인자와
     7	 * 정체 불명 플래그는 stderr 경고 후 무시 (하위호환).
     8	 */
     9	internal data class CliArgs(
    10	    val flags: Set<String>,
    11	    val values: Map<String, String>,
    12	) {
    13	    fun hasFlag(name: String): Boolean = flags.contains(name)
    14	    fun valueOf(name: String): String? = values[name]
    15	
    16	    companion object {
    17	        fun parse(args: Array<String>, valueBearingFlags: Set<String>): CliArgs {
    18	            val flags = mutableSetOf<String>()
    19	            val values = mutableMapOf<String, String>()
    20	            for (raw in args) {
    21	                if (!raw.startsWith("--")) {
    22	                    System.err.println("axp: 알 수 없는 인자 무시: $raw")
    23	                    continue
    24	                }
    25	                val eqIdx = raw.indexOf(CliConstants.ARG_SEPARATOR)
    26	                if (eqIdx < 0) {
    27	                    // MF-B2: value-bearing flag 가 `=` 없이 단독 입력되면 throw.
    28	                    require(raw !in valueBearingFlags) {
    29	                        "axp: $raw 는 값을 받는 옵션입니다 — `$raw=<value>` 형식 필요 (${CliConstants.USAGE_LINE})"
    30	                    }
    31	                    flags.add(raw)
    32	                } else {
    33	                    // MF-F2: `--=value` 는 key="--" + value="value" 로 파싱됨 (의도된 동작 —
    34	                    // valueOf("--") lookup 은 호출부 어디서도 하지 않으므로 harmless).
    35	                    // `---triple-dash` 등 malformed flag 는 eqIdx<0 branch 로 flags 에 저장되며
    36	                    // 역시 조회되지 않아 무시됨. 엄격 validation 은 향후 필요 시 별도 carry.
    37	                    val key = raw.substring(0, eqIdx)
    38	                    val value = raw.substring(eqIdx + 1)
    39	                    require(value.isNotEmpty()) {
    40	                        "axp: $key 값 누락 (${CliConstants.USAGE_LINE})"
    41	                    }
    42	                    values[key] = value
    43	                }
    44	            }
    45	            return CliArgs(flags, values)
    46	        }
    47	    }
    48	}

 succeeded in 0ms:
     1	package dev.axp.mcp
     2	
     3	import dev.axp.http.HttpServerConstants
     4	import dev.axp.http.PlaceholderPngRenderer
     5	import dev.axp.http.PreviewServer
     6	import dev.axp.layoutlib.worker.DistDiscovery
     7	import dev.axp.layoutlib.worker.FixtureDiscovery
     8	import dev.axp.layoutlib.worker.LayoutlibRenderer
     9	import dev.axp.protocol.Versions
    10	import dev.axp.protocol.mcp.Capabilities
    11	import dev.axp.protocol.render.PngRenderer
    12	import kotlinx.serialization.json.Json
    13	import kotlinx.serialization.json.JsonPrimitive
    14	import kotlinx.serialization.json.buildJsonArray
    15	import kotlinx.serialization.json.buildJsonObject
    16	import kotlinx.serialization.json.put
    17	import java.nio.file.Path
    18	import java.nio.file.Paths
    19	import java.util.Base64
    20	
    21	/**
    22	 * Claude Code MCP 서버 엔트리.
    23	 *
    24	 * CLI 모드:
    25	 *   --smoke           : stderr 에 버전 라인 + stdout 에 "ok" 1줄 후 종료 (CI 스모크).
    26	 *   --stdio           : MCP JSON-RPC 2.0 루프 (stdin/stdout, EOF 까지 유지).
    27	 *   --http-server     : localhost:7321 에 HTTP/SSE 서버 + viewer.
    28	 *   (인자 없음)         : 기본 = --http-server (W1 demo 호환 유지).
    29	 *
    30	 * Path override (optional):
    31	 *   --dist-dir=<path>     : layoutlib dist. 없으면 DistDiscovery.locate(null) 로 auto-detect.
    32	 *   --fixture-root=<path> : XML fixture 루트. 없으면 FixtureDiscovery.locate(null).
    33	 *   override 제공했으나 경로 비존재 → IllegalArgumentException (fail-fast).
    34	 *   auto-detect 모두 실패 → PlaceholderPngRenderer fallback (기존 behavior 유지).
    35	 */
    36	fun main(args: Array<String>) {
    37	    val parsed = CliArgs.parse(args, CliConstants.VALUE_BEARING_FLAGS)
    38	    val versionLine = buildVersionLine()
    39	    System.err.println(versionLine)
    40	
    41	    if (parsed.hasFlag(StdioConstants.SMOKE_FLAG)) {
    42	        System.out.println(StdioConstants.SMOKE_OK_LINE)
    43	        return
    44	    }
    45	
    46	    if (parsed.hasFlag(StdioConstants.STDIO_FLAG)) {
    47	        runStdioMode(parsed)
    48	        return
    49	    }
    50	
    51	    runHttpMode(parsed)
    52	}
    53	
    54	private fun runStdioMode(parsed: CliArgs) {
    55	    // W2D6 final pair review (Codex HIGH): JSON-RPC 2.0 §5 는 result 와 error 상호 배타.
    56	    // kotlinx.serialization 은 encodeDefaults=true + null 필드를 기본 방출하므로 explicitNulls=false
    57	    // 로 null 필드를 생략해 spec 준수.
    58	    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false }
    59	    val renderer = chooseRenderer(parsed)
    60	    val handler = McpMethodHandler(json)
    61	    handler.registerTool(
    62	        descriptor = ToolDescriptor(
    63	            name = StdioConstants.TOOL_RENDER_LAYOUT,
    64	            description = "Render a single Android layout XML to a PNG (base64-encoded) using layoutlib.",
    65	            inputSchema = buildJsonObject {
    66	                put("type", JsonPrimitive("object"))
    67	                put("properties", buildJsonObject {
    68	                    put(StdioConstants.TOOL_PARAM_LAYOUT, buildJsonObject {
    69	                        put("type", JsonPrimitive("string"))
    70	                        put("description", JsonPrimitive("Layout file name, e.g. activity_basic.xml"))
    71	                    })
    72	                })
    73	                put("required", buildJsonArray {
    74	                    add(JsonPrimitive(StdioConstants.TOOL_PARAM_LAYOUT))
    75	                })
    76	            }
    77	        ),
    78	        invoker = { args -> invokeRenderLayoutTool(renderer, args) }
    79	    )
    80	    val server = McpStdioServer(handler, System.`in`, System.out, json)
    81	    server.run()
    82	}
    83	
    84	private fun runHttpMode(parsed: CliArgs) {
    85	    val renderer = chooseRenderer(parsed)
    86	    val server = PreviewServer(pngRenderer = renderer)
    87	    server.start()
    88	    System.err.println(
    89	        "axp viewer ready: http://${HttpServerConstants.DEFAULT_HOST}:${HttpServerConstants.DEFAULT_PORT}/" +
    90	            " (renderer=${renderer.javaClass.simpleName})"
    91	    )
    92	    server.blockUntilShutdown()
    93	}
    94	
    95	private fun invokeRenderLayoutTool(renderer: PngRenderer, args: Any?): kotlinx.serialization.json.JsonObject {
    96	    val layoutName = (args as? kotlinx.serialization.json.JsonObject)
    97	        ?.get(StdioConstants.TOOL_PARAM_LAYOUT)
    98	        ?.let { (it as? JsonPrimitive)?.content }
    99	        ?: error("layout 인자 누락")
   100	    val bytes = renderer.renderPng(layoutName)
   101	    val b64 = Base64.getEncoder().encodeToString(bytes)
   102	    // F-1 (W2D6 pair review): MCP spec requires result.content[] typed content blocks.
   103	    return buildJsonObject {
   104	        put(StdioConstants.FIELD_CONTENT, buildJsonArray {
   105	            add(buildJsonObject {
   106	                put(StdioConstants.FIELD_TYPE, JsonPrimitive(StdioConstants.CONTENT_TYPE_IMAGE))
   107	                put(StdioConstants.FIELD_DATA, JsonPrimitive(b64))
   108	                put(StdioConstants.FIELD_MIME_TYPE, JsonPrimitive(StdioConstants.MIME_TYPE_PNG))
   109	            })
   110	        })
   111	        put(StdioConstants.FIELD_IS_ERROR, JsonPrimitive(false))
   112	    }
   113	}
   114	
   115	private fun chooseRenderer(parsed: CliArgs): PngRenderer {
   116	    val distOverride: Path? = parsed.valueOf(CliConstants.DIST_DIR_FLAG)?.let { Paths.get(it) }
   117	    val fixtureOverride: Path? = parsed.valueOf(CliConstants.FIXTURE_ROOT_FLAG)?.let { Paths.get(it) }
   118	
   119	    val dist = DistDiscovery.locate(distOverride)
   120	    val fixture = FixtureDiscovery.locate(fixtureOverride)
   121	
   122	    if (dist == null || fixture == null) {
   123	        System.err.println(
   124	            "axp: layoutlib dist 또는 fixture 탐지 실패 (dist=$dist, fixture=$fixture) " +
   125	                "→ placeholder PNG 로 fallback"
   126	        )
   127	        return PlaceholderPngRenderer()
   128	    }
   129	    return try {
   130	        LayoutlibRenderer(
   131	            distDir = dist.toAbsolutePath().normalize(),
   132	            fallback = PlaceholderPngRenderer(),
   133	            fixtureRoot = fixture.toAbsolutePath().normalize(),
   134	        )
   135	    } catch (e: Throwable) {
   136	        System.err.println(
   137	            "axp: LayoutlibRenderer 초기화 실패 (${e.javaClass.simpleName}) → placeholder fallback"
   138	        )
   139	        PlaceholderPngRenderer()
   140	    }
   141	}
   142	
   143	private fun buildVersionLine(): String {
   144	    return buildString {
   145	        append("axp-server v").append(Versions.SERVER_VERSION)
   146	        append(" (schema ").append(Versions.SCHEMA_VERSION).append(")")
   147	        append(" capabilities=[")
   148	        // W1-END 페어 리뷰 C-2: 실제 보유 capability 만 advertise.
   149	        append(listOf(Capabilities.RENDER_L1, Capabilities.SSE_MINIMAL).joinToString(","))
   150	        append("]")
   151	    }
   152	}

exec
/bin/bash -lc 'nl -ba server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.mcp
     2	
     3	/**
     4	 * W3D2 integration cleanup (Codex F3 carry) — CLI flag 이름 / 구분자 / value-bearing flag 집합.
     5	 *
     6	 * StdioConstants (mode flags: --smoke, --stdio, --http-server) 와 분리되어 있다.
     7	 * 이 object 는 "값을 받는 옵션" (--dist-dir=/x, --fixture-root=/y) 전용.
     8	 */
     9	internal object CliConstants {
    10	    const val DIST_DIR_FLAG = "--dist-dir"
    11	    const val FIXTURE_ROOT_FLAG = "--fixture-root"
    12	    const val ARG_SEPARATOR = "="
    13	    const val USAGE_LINE =
    14	        "Usage: axp-server [--dist-dir=<path>] [--fixture-root=<path>] [--smoke|--stdio|--http-server]"
    15	
    16	    /**
    17	     * MF-B2 — 값을 반드시 `=<value>` 로 받아야 하는 flag 집합. `CliArgs.parse` 가
    18	     * 이 집합의 flag 가 `=` 없이 단독 입력되면 `IllegalArgumentException` 을 던진다.
    19	     */
    20	    val VALUE_BEARING_FLAGS: Set<String> = setOf(DIST_DIR_FLAG, FIXTURE_ROOT_FLAG)
    21	}

exec
/bin/bash -lc 'nl -ba fixture/sample-app/app/build.gradle.kts' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc 'rg -n "axpEmitClasspath|runtimeClasspath|assembleDebug|finalizedBy|activity_basic_minimal|MaterialThemeOverlay|ThemeEnforcement|sampleAppModuleRoot|LayoutlibRendererIntegrationTest|SUCCESS|assumeTrue|SampleAppClasspathManifest|AarExtractor|SampleAppClassLoader|MinimalLayoutlibCallback" docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
19:- **B1**: `fixtureRoot` 의미 불일치 — 별도 `sampleAppModuleRoot` 인자 도입 (§4.4, §4.7).
20:- **F1**: `MinimalLayoutlibCallback.findClass` + `hasAndroidXAppCompat` override 추가 (§4.6 D3).
24:- **Q1**: `afterEvaluate` 제거, 직접 `tasks.named("assembleDebug").configure { finalizedBy(...) }` (§4.1).
28:- **Q5**: T1 gate 에 `Result.Status == SUCCESS` assertion 활성 (§4.8).
31:- **B2** (Codex): activity_basic.xml 의 `MaterialButton` 이 Material theme enforcement 로 SUCCESS 불가 주장 — Claude 는 약하게 동의 (D7 fidelity 손상). **empirical 검증 필요** — 구현 후 실측. Contingency: T1 gate 가 fail 시 § 9.1 의 fallback layout (activity_basic_minimal.xml — MaterialButton → 표준 Button) 으로 자동 다운그레이드.
32:- **B3** (Codex): R.jar real id (e.g., `2130903705`) ↔ callback generated id (`0x7F0A_xxxx`) 불일치. **이미 §1.2 R3 가 인정** — broken positioning. SUCCESS 자체는 막지 않음 (`obtainStyledAttributes` 가 unknown id 에 대해 default 반환, throw 없음). round 2 에서 §1.2 R3 표현 강화.
83:2. `MinimalLayoutlibCallback.loadView(name, sig, args)` 가 위 클래스로더로부터 custom view 를 reflection-instantiate.
85:4. `LayoutlibRendererIntegrationTest` 의 `@Disabled` 제거 + tier3-values 패턴 (T1 gate: `Result.Status == SUCCESS && PNG > 1000 bytes`) 통과.
92:  - ConstraintLayout 의 `app:layout_constraintTop_toTopOf` 등 custom attr 가 default(0/null) 로 fallback → 모든 child 가 (0,0) 에 적층. layout 위치가 잘못되어도 `Result.Status == SUCCESS` + PNG > 1000 bytes 는 통과 가능.
94:  - **R.jar real id (e.g., `2130903705`) ↔ callback generated id (`0x7F0A_xxxx`) 불일치**: ConstraintLayout/MaterialButton 등이 `obtainStyledAttributes(attrs, R.styleable.X)` 를 호출하면 `R.styleable.X` 의 int 들은 layoutlib 의 resource id space 와 disjoint → `TypedArray` lookup miss, default 반환 (throw 없음). 이는 SUCCESS 자체는 막지 않음.
95:  - **Material theme enforcement 위험**: `MaterialThemeOverlay.wrap` / `ThemeEnforcement.checkCompatibleTheme` 가 framework theme 에 specific Material attr 부재 시 throw 가능 — empirical 검증 필요 (구현 후 §9.1 contingency 발동 여부 판단).
103:// LayoutlibRendererIntegrationTest 가 expect:
105:assertEquals(SUCCESS, renderer.lastSessionResult.status)   // 페어 리뷰가 강하게 권장하면 추가
116:| C1 | `fixture/sample-app/app/build.gradle.kts` (수정) | `axpEmitClasspath` Gradle task — 해석된 debugRuntimeClasspath 의 AAR + JAR 절대 경로를 manifest 로 emit. `assembleDebug` 에 finalizedBy. | +25 |
118:| C2 | `server/layoutlib-worker/.../classloader/SampleAppClasspathManifest.kt` | manifest 파일 (텍스트, 한 줄에 한 절대 경로) 파싱. 누락/오래됨/형식 오류 → 명시 예외. **input 은 sampleAppModuleRoot** (B1 수정). | 60 |
119:| C3 | `server/layoutlib-worker/.../classloader/AarExtractor.kt` | `.aar` ZIP 에서 `classes.jar` 를 stable cache (`<sample-app>/app/build/axp/aar-classes/<sha1>/<artifact>.jar`) 로 추출. mtime 기반 idempotent. **atomic write** (temp + ATOMIC_MOVE). | 80 |
120:| C4 | `server/layoutlib-worker/.../classloader/SampleAppClassLoader.kt` | 위 manifest+extractor 결과 + R.jar 위치 → `URLClassLoader(parent = isolatedCL)`. 빌드된 CL 인스턴스 + 진단용 entry list. **input 은 sampleAppModuleRoot** (B1 수정). | 70 |
122:| C6 | `MinimalLayoutlibCallback.kt` (수정) | 생성자에 `viewClassLoader: () -> ClassLoader` 추가 (lazy provider, F1/Q4). `loadView`/`findClass` reflection. `hasAndroidXAppCompat` true. UnsupportedOperationException 분기 제거. **InvocationTargetException unwrap** (Q3). | +40 |
123:| C7 | `LayoutlibRenderer.kt` (수정) | 생성자에 `sampleAppModuleRoot: Path` 인자 추가 (B1, no default). `renderViaLayoutlib` 가 SampleAppClassLoader 를 lazy 빌드 후 callback 에 주입. | +25 |
124:| C7b | `Main.kt` / `SharedLayoutlibRenderer.kt` / `LayoutlibRendererTier3MinimalTest.kt` 등 모든 호출부 (수정) | 새 인자 `sampleAppModuleRoot` 명시 주입 — CLAUDE.md "no default parameter values" 정책. CLI override `--sample-app-root`. | +15 |
125:| C8 | `LayoutlibRendererIntegrationTest.kt` (수정) | `@Disabled` 제거. T1 gate assertion 추가 — `assertEquals(Result.Status.SUCCESS, ...)` (Q5). SharedLayoutlibRenderer 호출은 유지. | +15 |
127:| TC1 | `SampleAppClasspathManifestTest` (단위 5) | manifest 파일 누락 / 빈 / 잘못된 라인 / 정상 / 절대경로 강제. | 80 |
128:| TC2 | `AarExtractorTest` (단위 4) | 정상 추출 / 캐시 hit / 손상 AAR / classes.jar 없는 AAR. | 100 |
129:| TC3 | `SampleAppClassLoaderTest` (단위 3) | 클래스로더 합성 / parent chain / R.jar 포함 검증. tmp dir + minimal jar fixture. | 80 |
130:| TC4 | `MinimalLayoutlibCallbackLoadViewTest` (단위 3) | 새 생성자 / reflection instantiate 성공 / 클래스 없음 → ClassNotFoundException. | 70 |
140:  ./gradlew :app:assembleDebug
141:    └─ tasks: ... → axpEmitClasspath (finalizedBy assembleDebug)
148:    └─ buildSampleAppClassLoader(fixtureRoot) [lazy, per-renderer]
149:        ├─ SampleAppClasspathManifest.read(fixtureRoot.../runtime-classpath.txt)
153:        │       path ends ".aar" → AarExtractor.extract(path) // → classes.jar path
161:        ├─ MinimalLayoutlibCallback(viewClassLoader)
210:val axpEmitClasspath = tasks.register("axpEmitClasspath") {
226:tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
235:**왜 emit 시점 = `finalizedBy(assembleDebug)`?**: 사용자 흐름 `./gradlew :app:assembleDebug` 가 보통 W3 dev 사이클의 entry point. 별도 task 명령 강제 시 forget 위험.
239:### 4.2 C2 — SampleAppClasspathManifest
241:`sampleAppModuleRoot` 가 `fixture/sample-app` 모듈 루트 (B1 fix). MANIFEST_RELATIVE_PATH 도 module-root 기준으로 재정의 (§4.5).
253: * <sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt 파일을 읽어
256: * 파일 형식 (axpEmitClasspath Gradle task 가 emit):
261:object SampleAppClasspathManifest {
263:    fun read(sampleAppModuleRoot: Path): List<Path> {
264:        val mf = sampleAppModuleRoot.resolve(MANIFEST_RELATIVE_PATH)
266:            "axp classpath manifest 누락: $mf — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 를 먼저 실행하세요"
286:### 4.3 C3 — AarExtractor
318:object AarExtractor {
354:### 4.4 C4 — SampleAppClassLoader
356:`sampleAppModuleRoot` (= `fixture/sample-app`) 가 모든 path 의 base. layoutlib 의 layout-dir `fixtureRoot` 와 분리 (B1 fix).
373: * AAR 은 AarExtractor 로 classes.jar 를 추출 후 그 경로를 URL 로 사용.
376:class SampleAppClassLoader private constructor(
383:         * @param sampleAppModuleRoot `fixture/sample-app` 모듈 루트 (= FixtureDiscovery.locateModuleRoot 결과).
386:        fun build(sampleAppModuleRoot: Path, parent: ClassLoader): SampleAppClassLoader {
387:            val manifest = SampleAppClasspathManifest.read(sampleAppModuleRoot)
388:            val cacheRoot = sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)
394:                    AarExtractor.extract(entry, cacheRoot) ?: continue // resource-only AAR skip
403:            val rJar = sampleAppModuleRoot.resolve(R_JAR_RELATIVE_PATH)
405:                "sample-app R.jar 누락: $rJar — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 필요"
410:            return SampleAppClassLoader(cl, urls.toList())
424:    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 sampleAppModuleRoot-relative 경로. */
427:    /** AarExtractor cacheRoot (= sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)) 의 base. */
430:    /** AarExtractor 의 stable 캐시 서브디렉토리 (cacheRoot 기준 상대). */
442:    /** AarExtractor 의 추출 결과 파일 suffix (artifactName 뒤에 붙음). */
445:    /** AarExtractor 의 atomic write 용 임시 파일 suffix. */
448:    /** AarExtractor 의 캐시 키용 path digest 알고리즘. */
460:### 4.6 C6 — MinimalLayoutlibCallback 변경 (F1 + Q3 적용)
470:class MinimalLayoutlibCallback(
516:생성자에 `sampleAppModuleRoot: Path` 인자 추가 (B1, no default — CLAUDE.md 정책). lazy provider 로 callback 에 주입.
523:    private val sampleAppModuleRoot: Path,  // ← 신규 (B1)
528:    @Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null
531:    private fun ensureSampleAppClassLoader(): ClassLoader {
534:        val built = SampleAppClassLoader.build(sampleAppModuleRoot, isolated)
543:            callback = MinimalLayoutlibCallback { ensureSampleAppClassLoader() },  // ← lazy provider
551:**Q4 (lazy)**: `MinimalLayoutlibCallback` 의 `viewClassLoaderProvider: () -> ClassLoader` lambda 가 activity_minimal 처럼 custom view 가 없는 경우 호출 0 → manifest 누락 영향 없음 → W3D1 의 `tier3-values` 회귀 없음.
553:**모든 호출부 (C7b)**: `Main.kt`, `SharedLayoutlibRenderer.getOrCreate`, 그리고 production `LayoutlibRenderer(...)` 호출하는 모든 테스트에서 `sampleAppModuleRoot` 인자 명시. CLI override `--sample-app-root=<path>` 추가 (CliConstants/CliArgs).
555:### 4.8 C8 — LayoutlibRendererIntegrationTest 변경 (Q5 적용)
557:Q5 컨버전스: `Result.Status == SUCCESS` assertion 활성. `LayoutlibRendererTier3MinimalTest` 의 W3D1 패턴과 일관성 유지.
561:class LayoutlibRendererIntegrationTest {
564:    fun `tier3-basic — activity_basic renders SUCCESS with non-empty PNG`() {
571:            sampleAppModuleRoot = moduleRoot,
576:        assertEquals(Result.Status.SUCCESS, renderer.lastSessionResult?.status, "render status SUCCESS 여야 함")
587:        assumeTrue(found != null, "sample-app module root 없음 — fixture/sample-app 확인")
629:| `tier3-basic` (renamed from `tier3 - renderPng...`) | activity_basic.xml | T1 gate (PNG > 1000 + magic). 페어 리뷰가 권하면 SUCCESS status 추가. |
638:- 99 → 118 unit tests + 11 integration → 본 변경 후: 118 + ~15 unit (TC1-4) + 11 → 12 integration (LayoutlibRendererIntegrationTest enable) PASS. tier3-glyph 만 SKIPPED 유지.
648:| R1 | sample-app 미빌드 시 manifest/R.jar 누락 → 모든 integration test 가 FAIL | C2/C4 의 require 메시지가 정확한 명령(`./gradlew :app:assembleDebug`) 안내. 페어 리뷰가 더 강한 요구 시 worker 가 자동으로 invoke 하는 방안 검토. |
665:**B2 가설** (Codex round 1): `MaterialButton` 의 Material theme enforcement (`MaterialThemeOverlay.wrap`, `ThemeEnforcement.checkCompatibleTheme`) 가 framework theme + missing app/library resource values 환경에서 inflate 시 throw → `Result.Status != SUCCESS`.
668:1. T1 gate (SUCCESS + PNG > 1000) PASS → B2 가설 무효, 본 스펙 그대로 close.
669:2. T1 gate FAIL with `ThemeEnforcement` 또는 `MaterialThemeOverlay` 관련 exception → §9.1 발동.
671:### 9.1 Contingency layout — `activity_basic_minimal.xml`
673:만약 §1 의 acceptance gate 가 MaterialButton 때문에 깨지면, 동일 fixture 디렉토리에 추가 layout 파일을 작성하고 `LayoutlibRendererIntegrationTest` 의 타겟을 그것으로 변경:
676:<!-- fixture/sample-app/app/src/main/res/layout/activity_basic_minimal.xml -->
706:**spec close 전 결정**: 구현 phase 에서 empirical 결과에 따라 §1 의 layout 이름을 "activity_basic.xml" 또는 "activity_basic_minimal.xml" 중 어느 것으로 fix 할지 work_log 에 기록.
714:- **Q1** (Gradle): `applicationVariants.all` API 가 `axpEmitClasspath` 를 더 안정적으로 wire 할 수 있나? AGP 8.x 의 best practice 는?
715:- **Q2** (Cache): `AarExtractor` 의 mtime 기반 invalidation 이 충분한가? hash 기반 (sha256) 으로 변경해야 하는 시나리오가 있나?
717:- **Q4** (lazy build): SampleAppClassLoader 빌드를 lazy 로 미루면 (옵션 A) activity_minimal 호환성 유지. 더 단순한 옵션 (B: empty CL) 이 권장되는가?
718:- **Q5** (assertion): T1 gate 에 `Result.Status == SUCCESS` 를 추가해야 하나? 현재 W2D7 패턴은 PNG > 1000 만 검사 — strictness 의 trade-off?
727:- 2026-04-29 (round 1 페어 리뷰 반영): B1 (fixtureRoot 분리), F1 (findClass + hasAndroidXAppCompat), B4 (magic strings → ClassLoaderConstants), Q1 (afterEvaluate 제거), Q3 (ITE unwrap), Q5 (SUCCESS assertion 활성), manifest distinct, atomic AAR write. §9 contingency 신설 (B2 empirical risk).

exec
/bin/bash -lc "nl -ba docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md | sed -n '120,230p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   120	| C4 | `server/layoutlib-worker/.../classloader/SampleAppClassLoader.kt` | 위 manifest+extractor 결과 + R.jar 위치 → `URLClassLoader(parent = isolatedCL)`. 빌드된 CL 인스턴스 + 진단용 entry list. **input 은 sampleAppModuleRoot** (B1 수정). | 70 |
   121	| C5 | `server/layoutlib-worker/.../classloader/ClassLoaderConstants.kt` | 모든 magic strings/번호 상수화 (B4): manifest 파일명, R.jar 상대 경로, AAR cache 디렉토리명, `"classes.jar"` entry, `"SHA-1"` digest, `".aar"`/`".jar"` 확장자, `"app"`/`"build"` path 세그먼트. | 50 |
   122	| C6 | `MinimalLayoutlibCallback.kt` (수정) | 생성자에 `viewClassLoader: () -> ClassLoader` 추가 (lazy provider, F1/Q4). `loadView`/`findClass` reflection. `hasAndroidXAppCompat` true. UnsupportedOperationException 분기 제거. **InvocationTargetException unwrap** (Q3). | +40 |
   123	| C7 | `LayoutlibRenderer.kt` (수정) | 생성자에 `sampleAppModuleRoot: Path` 인자 추가 (B1, no default). `renderViaLayoutlib` 가 SampleAppClassLoader 를 lazy 빌드 후 callback 에 주입. | +25 |
   124	| C7b | `Main.kt` / `SharedLayoutlibRenderer.kt` / `LayoutlibRendererTier3MinimalTest.kt` 등 모든 호출부 (수정) | 새 인자 `sampleAppModuleRoot` 명시 주입 — CLAUDE.md "no default parameter values" 정책. CLI override `--sample-app-root`. | +15 |
   125	| C8 | `LayoutlibRendererIntegrationTest.kt` (수정) | `@Disabled` 제거. T1 gate assertion 추가 — `assertEquals(Result.Status.SUCCESS, ...)` (Q5). SharedLayoutlibRenderer 호출은 유지. | +15 |
   126	| **Tests** | | | |
   127	| TC1 | `SampleAppClasspathManifestTest` (단위 5) | manifest 파일 누락 / 빈 / 잘못된 라인 / 정상 / 절대경로 강제. | 80 |
   128	| TC2 | `AarExtractorTest` (단위 4) | 정상 추출 / 캐시 hit / 손상 AAR / classes.jar 없는 AAR. | 100 |
   129	| TC3 | `SampleAppClassLoaderTest` (단위 3) | 클래스로더 합성 / parent chain / R.jar 포함 검증. tmp dir + minimal jar fixture. | 80 |
   130	| TC4 | `MinimalLayoutlibCallbackLoadViewTest` (단위 3) | 새 생성자 / reflection instantiate 성공 / 클래스 없음 → ClassNotFoundException. | 70 |
   131	
   132	추정 합계: production ~290 LOC, test ~330 LOC, +25 Gradle.
   133	
   134	---
   135	
   136	## 3. 데이터 흐름
   137	
   138	```
   139	[Build time, fixture/sample-app]
   140	  ./gradlew :app:assembleDebug
   141	    └─ tasks: ... → axpEmitClasspath (finalizedBy assembleDebug)
   142	        └─ writes: app/build/axp/runtime-classpath.txt
   143	                   각 라인: <abs path to .aar or .jar>
   144	
   145	[Test time, server/layoutlib-worker]
   146	  LayoutlibRenderer.renderPng("activity_basic.xml")
   147	    └─ initBridge() (기존)
   148	    └─ buildSampleAppClassLoader(fixtureRoot) [lazy, per-renderer]
   149	        ├─ SampleAppClasspathManifest.read(fixtureRoot.../runtime-classpath.txt)
   150	        │     → List<Path> entries
   151	        ├─ entries.map { path ->
   152	        │     when {
   153	        │       path ends ".aar" → AarExtractor.extract(path) // → classes.jar path
   154	        │       path ends ".jar" → path
   155	        │       else → IllegalStateException
   156	        │     }
   157	        │   }
   158	        ├─ + sample-app R.jar (fixtureRoot.../intermediates/.../R.jar)
   159	        └─ URLClassLoader(urls, parent = isolatedClassLoader)
   160	    └─ renderViaLayoutlib(layoutName, viewClassLoader)
   161	        ├─ MinimalLayoutlibCallback(viewClassLoader)
   162	        ├─ SessionParamsFactory.build(...)
   163	        └─ Bridge.createSession(params).render()
   164	            ├─ inflater 가 LayoutlibCallback.loadView 호출
   165	            └─ callback.loadView("androidx.constraintlayout.widget.ConstraintLayout", [Context, AttributeSet], [...])
   166	                └─ viewClassLoader.loadClass(name).getConstructor(...).newInstance(...)
   167	```
   168	
   169	---
   170	
   171	## 4. 컴포넌트 스펙
   172	
   173	### 4.0 C0 — FixtureDiscovery 확장 (B1 fix)
   174	
   175	`server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt`:
   176	
   177	```kotlin
   178	object FixtureDiscovery {
   179	    const val FIXTURE_SUBPATH = "fixture/sample-app/app/src/main/res/layout"
   180	    const val FIXTURE_MODULE_SUBPATH = "fixture/sample-app"   // ← 신규
   181	
   182	    // ... 기존 CANDIDATE_ROOTS / locate(...) 그대로 ...
   183	
   184	    /** sample-app 모듈 루트 (= app/src/main/res/layout 의 5 ancestors up) 를 탐지. */
   185	    fun locateModuleRoot(override: Path?): Path? = locateInternal(
   186	        override = override,
   187	        userDir = System.getProperty("user.dir"),
   188	        candidateRoots = CANDIDATE_ROOTS,
   189	        subpath = FIXTURE_MODULE_SUBPATH,
   190	    )
   191	
   192	    // 기존 locateInternal 시그니처에 subpath 파라미터 추가 (default 없음 — overload 두 개로 분리하거나
   193	    // CLAUDE.md 정책에 따라 호출자가 양쪽 명시 인자 주입). 권장: 기존 locateInternal 은 그대로 유지하고
   194	    // 신규 internal helper 함수 `locateInternalWithSubpath` 분리.
   195	}
   196	```
   197	
   198	CLI override (`Main.kt`) 도 확장: 기존 `--fixture-root=<layout dir>` 외에 `--sample-app-root=<module dir>` 추가. CliConstants/CliArgs 에 신규 flag.
   199	
   200	### 4.1 C1 — Gradle manifest task
   201	
   202	`fixture/sample-app/app/build.gradle.kts` 의 끝에 추가:
   203	
   204	```kotlin
   205	// 본 task 는 W3D3 in-JVM custom view 로딩 지원 — server/layoutlib-worker 가
   206	// debugRuntimeClasspath (resolved AAR + transitive JAR) 의 절대경로 manifest 를 읽어
   207	// 자체 URLClassLoader 를 구성한다. Maven cache 의 transforms-* hash dir 의 불안정성을
   208	// 우회하기 위해 본 manifest 가 single source of truth 가 된다.
   209	val axpClasspathManifest = layout.buildDirectory.file("axp/runtime-classpath.txt")
   210	val axpEmitClasspath = tasks.register("axpEmitClasspath") {
   211	    val cpProvider = configurations.named("debugRuntimeClasspath")
   212	    inputs.files(cpProvider)
   213	    outputs.file(axpClasspathManifest)
   214	    doLast {
   215	        val cp = cpProvider.get()
   216	        val artifacts = cp.resolvedConfiguration.resolvedArtifacts
   217	            .map { it.file.absolutePath }
   218	            .distinct()                // ← Claude-F4: dup 라인 제거 (Gradle resolve 가 직접+transitive 양쪽 path 노출)
   219	            .sorted()
   220	        val outFile = axpClasspathManifest.get().asFile
   221	        outFile.parentFile.mkdirs()
   222	        outFile.writeText(artifacts.joinToString("\n"))
   223	    }
   224	}
   225	// Q1 (Codex+Claude convergent): afterEvaluate 제거. tasks.named 가 이미 lazy.
   226	tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
   227	```
   228	
   229	**계약**:
   230	- 출력 경로: `fixture/sample-app/app/build/axp/runtime-classpath.txt`.

exec
/bin/bash -lc "nl -ba docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md | sed -n '230,430p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   230	- 출력 경로: `fixture/sample-app/app/build/axp/runtime-classpath.txt`.
   231	- 형식: 라인 separator `\n`, 각 라인 = AAR 또는 JAR 의 절대경로. trailing newline 없음 (joinToString 결과).
   232	- 정렬: lexicographic — reproducibility 를 위함.
   233	- 경로는 `.aar` 또는 `.jar` 로 끝나야 함. 그 외 형식 발견 시 worker 가 거부 (TC1 가 검증).
   234	
   235	**왜 emit 시점 = `finalizedBy(assembleDebug)`?**: 사용자 흐름 `./gradlew :app:assembleDebug` 가 보통 W3 dev 사이클의 entry point. 별도 task 명령 강제 시 forget 위험.
   236	
   237	**페어 리뷰 Q1**: `applicationVariants.all` 또는 androidComponents API 를 써야 할까? — `debugRuntimeClasspath` 은 표준 configuration 이름 (AGP 8.x 안정), 직접 참조 OK. 그러나 향후 flavor 추가 시 break — 본 fixture 는 flavor 없음, defer.
   238	
   239	### 4.2 C2 — SampleAppClasspathManifest
   240	
   241	`sampleAppModuleRoot` 가 `fixture/sample-app` 모듈 루트 (B1 fix). MANIFEST_RELATIVE_PATH 도 module-root 기준으로 재정의 (§4.5).
   242	
   243	```kotlin
   244	package dev.axp.layoutlib.worker.classloader
   245	
   246	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
   247	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.JAR_EXTENSION
   248	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
   249	import java.nio.file.Files
   250	import java.nio.file.Path
   251	
   252	/**
   253	 * <sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt 파일을 읽어
   254	 * resolved runtime classpath 의 AAR/JAR 절대경로 리스트를 제공한다.
   255	 *
   256	 * 파일 형식 (axpEmitClasspath Gradle task 가 emit):
   257	 *  - 라인 separator '\n', trailing newline 없음.
   258	 *  - 각 라인 = AAR 또는 JAR 의 절대 경로.
   259	 *  - 정렬: lexicographic, distinct.
   260	 */
   261	object SampleAppClasspathManifest {
   262	
   263	    fun read(sampleAppModuleRoot: Path): List<Path> {
   264	        val mf = sampleAppModuleRoot.resolve(MANIFEST_RELATIVE_PATH)
   265	        require(Files.isRegularFile(mf)) {
   266	            "axp classpath manifest 누락: $mf — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 를 먼저 실행하세요"
   267	        }
   268	        val raw = Files.readString(mf)
   269	        if (raw.isBlank()) {
   270	            error("axp classpath manifest 가 비어있음: $mf")
   271	        }
   272	        return raw.split('\n').mapIndexed { idx, line ->
   273	            require(line.isNotBlank()) { "manifest line ${idx + 1} 이 공백" }
   274	            val p = Path.of(line)
   275	            require(p.isAbsolute) { "manifest line ${idx + 1} 이 비-절대경로: '$line'" }
   276	            require(line.endsWith(AAR_EXTENSION) || line.endsWith(JAR_EXTENSION)) {
   277	                "manifest line ${idx + 1} 의 확장자가 ${AAR_EXTENSION}/${JAR_EXTENSION} 가 아님: '$line'"
   278	            }
   279	            require(Files.isRegularFile(p)) { "manifest line ${idx + 1} 의 파일이 없음: $p" }
   280	            p
   281	        }
   282	    }
   283	}
   284	```
   285	
   286	### 4.3 C3 — AarExtractor
   287	
   288	Atomic write 적용 — temp file + ATOMIC_MOVE (Codex follow-up).
   289	
   290	```kotlin
   291	package dev.axp.layoutlib.worker.classloader
   292	
   293	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
   294	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
   295	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
   296	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTRACTED_JAR_SUFFIX
   297	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.SHA1_DIGEST_NAME
   298	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.TEMP_JAR_SUFFIX
   299	import java.nio.file.Files
   300	import java.nio.file.Path
   301	import java.nio.file.StandardCopyOption.ATOMIC_MOVE
   302	import java.nio.file.StandardCopyOption.REPLACE_EXISTING
   303	import java.security.MessageDigest
   304	import java.util.zip.ZipFile
   305	import kotlin.io.path.isRegularFile
   306	import kotlin.io.path.outputStream
   307	
   308	/**
   309	 * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출.
   310	 *
   311	 * Cache 위치: `<cacheRoot>/aar-classes/<sha1(absPath)>/<artifactName>.jar`.
   312	 * Idempotent: 캐시된 파일의 mtime >= AAR 의 mtime 이면 재사용.
   313	 * Atomic: temp file 에 먼저 쓴 뒤 ATOMIC_MOVE — 동시 forks 에서도 race-free.
   314	 *
   315	 * AAR 안에 classes.jar 가 없는 경우 (e.g. 순수 resource-only AAR) → null 반환.
   316	 * 손상된 ZIP → IOException pass-through.
   317	 */
   318	object AarExtractor {
   319	
   320	    fun extract(aarPath: Path, cacheRoot: Path): Path? {
   321	        require(aarPath.isRegularFile()) { "AAR 누락: $aarPath" }
   322	        val key = sha1(aarPath.toAbsolutePath().toString())
   323	        val artifactName = aarPath.fileName.toString().removeSuffix(AAR_EXTENSION)
   324	        val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR).resolve(key)
   325	        Files.createDirectories(outDir)
   326	        val outJar = outDir.resolve(artifactName + EXTRACTED_JAR_SUFFIX)
   327	
   328	        val aarMtime = Files.getLastModifiedTime(aarPath).toMillis()
   329	        if (outJar.isRegularFile() &&
   330	            Files.getLastModifiedTime(outJar).toMillis() >= aarMtime) {
   331	            return outJar
   332	        }
   333	
   334	        val tmpJar = outDir.resolve(artifactName + TEMP_JAR_SUFFIX)
   335	        ZipFile(aarPath.toFile()).use { zip ->
   336	            val entry = zip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
   337	            zip.getInputStream(entry).use { input ->
   338	                tmpJar.outputStream().use { output -> input.copyTo(output) }
   339	            }
   340	        }
   341	        Files.move(tmpJar, outJar, ATOMIC_MOVE, REPLACE_EXISTING)
   342	        return outJar
   343	    }
   344	
   345	    private fun sha1(s: String): String {
   346	        val md = MessageDigest.getInstance(SHA1_DIGEST_NAME)
   347	        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
   348	    }
   349	}
   350	```
   351	
   352	**페어 리뷰 Q2**: 캐시 invalidation 을 mtime 기반으로 하면 AAR 이 동일 경로로 재배포되면서 mtime 만 갱신되는 시나리오가 깨지나? — Gradle cache 의 AAR 은 hash 디렉토리에 위치하므로 동일 path 의 contents 변경은 사실상 발생하지 않음 (Maven 좌표+버전이 같으면 contents 동일). hash mismatch 시 path 자체가 달라짐 → cacheRoot 안의 sha1 키도 다름. 안전.
   353	
   354	### 4.4 C4 — SampleAppClassLoader
   355	
   356	`sampleAppModuleRoot` (= `fixture/sample-app`) 가 모든 path 의 base. layoutlib 의 layout-dir `fixtureRoot` 와 분리 (B1 fix).
   357	
   358	```kotlin
   359	package dev.axp.layoutlib.worker.classloader
   360	
   361	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_BASE_RELATIVE_PATH
   362	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
   363	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
   364	import java.net.URL
   365	import java.net.URLClassLoader
   366	import java.nio.file.Files
   367	import java.nio.file.Path
   368	
   369	/**
   370	 * sample-app 의 runtime classpath (resolved AAR + transitive JAR + R.jar) 로부터
   371	 * URLClassLoader 를 구성. parent = layoutlib isolatedClassLoader (android.view.* 보유).
   372	 *
   373	 * AAR 은 AarExtractor 로 classes.jar 를 추출 후 그 경로를 URL 로 사용.
   374	 * Resource-only AAR (classes.jar 없음) 은 silently skip.
   375	 */
   376	class SampleAppClassLoader private constructor(
   377	    val classLoader: ClassLoader,
   378	    val urls: List<URL>,
   379	) {
   380	    companion object {
   381	
   382	        /**
   383	         * @param sampleAppModuleRoot `fixture/sample-app` 모듈 루트 (= FixtureDiscovery.locateModuleRoot 결과).
   384	         * @param parent 본 CL 의 parent — 보통 LayoutlibBootstrap.createIsolatedClassLoader() 결과.
   385	         */
   386	        fun build(sampleAppModuleRoot: Path, parent: ClassLoader): SampleAppClassLoader {
   387	            val manifest = SampleAppClasspathManifest.read(sampleAppModuleRoot)
   388	            val cacheRoot = sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)
   389	            val urls = mutableListOf<URL>()
   390	
   391	            for (entry in manifest) {
   392	                val asString = entry.toString()
   393	                val jarPath = if (asString.endsWith(AAR_EXTENSION)) {
   394	                    AarExtractor.extract(entry, cacheRoot) ?: continue // resource-only AAR skip
   395	                } else {
   396	                    entry
   397	                }
   398	                urls += jarPath.toUri().toURL()
   399	            }
   400	
   401	            // sample-app 의 R.jar — 모든 R.<package>.* 클래스 포함. 누락 시 NoClassDefFoundError
   402	            // for androidx.constraintlayout.widget.R$styleable etc.
   403	            val rJar = sampleAppModuleRoot.resolve(R_JAR_RELATIVE_PATH)
   404	            require(Files.isRegularFile(rJar)) {
   405	                "sample-app R.jar 누락: $rJar — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 필요"
   406	            }
   407	            urls += rJar.toUri().toURL()
   408	
   409	            val cl = URLClassLoader(urls.toTypedArray(), parent)
   410	            return SampleAppClassLoader(cl, urls.toList())
   411	        }
   412	    }
   413	}
   414	```
   415	
   416	### 4.5 C5 — ClassLoaderConstants
   417	
   418	CLAUDE.md "Zero Tolerance for Magic Numbers/Strings" 정책에 따라 **모든** literal 을 상수화 (B4).
   419	
   420	```kotlin
   421	package dev.axp.layoutlib.worker.classloader
   422	
   423	internal object ClassLoaderConstants {
   424	    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 sampleAppModuleRoot-relative 경로. */
   425	    const val MANIFEST_RELATIVE_PATH = "app/build/axp/runtime-classpath.txt"
   426	
   427	    /** AarExtractor cacheRoot (= sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)) 의 base. */
   428	    const val AAR_CACHE_BASE_RELATIVE_PATH = "app/build/axp"
   429	
   430	    /** AarExtractor 의 stable 캐시 서브디렉토리 (cacheRoot 기준 상대). */

exec
/bin/bash -lc "nl -ba docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md | sed -n '430,650p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   430	    /** AarExtractor 의 stable 캐시 서브디렉토리 (cacheRoot 기준 상대). */
   431	    const val AAR_CACHE_RELATIVE_DIR = "aar-classes"
   432	
   433	    /** AAR ZIP 안에서 JVM 바이트코드 JAR 의 표준 entry 이름. */
   434	    const val AAR_CLASSES_JAR_ENTRY = "classes.jar"
   435	
   436	    /** AAR file 확장자 (manifest 검증 + 추출 분기). */
   437	    const val AAR_EXTENSION = ".aar"
   438	
   439	    /** JAR file 확장자 (manifest 검증). */
   440	    const val JAR_EXTENSION = ".jar"
   441	
   442	    /** AarExtractor 의 추출 결과 파일 suffix (artifactName 뒤에 붙음). */
   443	    const val EXTRACTED_JAR_SUFFIX = ".jar"
   444	
   445	    /** AarExtractor 의 atomic write 용 임시 파일 suffix. */
   446	    const val TEMP_JAR_SUFFIX = ".jar.tmp"
   447	
   448	    /** AarExtractor 의 캐시 키용 path digest 알고리즘. */
   449	    const val SHA1_DIGEST_NAME = "SHA-1"
   450	
   451	    /**
   452	     * AGP 8.x 가 emit 하는 통합 R.jar 경로 (compile_and_runtime_not_namespaced_r_class_jar variant).
   453	     * 본 경로는 AGP minor 버전 변경 시 깨질 수 있으나 8.x 안정 — 변경 시 본 상수만 갱신.
   454	     */
   455	    const val R_JAR_RELATIVE_PATH =
   456	        "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
   457	}
   458	```
   459	
   460	### 4.6 C6 — MinimalLayoutlibCallback 변경 (F1 + Q3 적용)
   461	
   462	페어 리뷰 컨버전스:
   463	- **F1**: `findClass` + `hasAndroidXAppCompat` override 추가 — `BridgeInflater` 의 `findCustomInflater` 가 AppCompat 자동 치환을 활성화하기 위해.
   464	- **Q3 (Codex 입장 채택)**: `InvocationTargetException` 의 cause 를 unwrap 해 layoutlib 로 던짐 — log/diagnostic 에서 raw cause 가 보이도록.
   465	- **Q4 (lazy)**: `viewClassLoader: () -> ClassLoader` lambda — activity_minimal 처럼 custom view 가 없으면 호출되지 않아 manifest 누락이 silent.
   466	
   467	```kotlin
   468	import java.lang.reflect.InvocationTargetException
   469	
   470	class MinimalLayoutlibCallback(
   471	    private val viewClassLoaderProvider: () -> ClassLoader,
   472	) : LayoutlibCallback() {
   473	
   474	    // ... 기존 nextId / byRef / byId / 동기화된 getOrGenerateResourceId / resolveResourceId 유지 ...
   475	
   476	    override fun loadView(
   477	        name: String,
   478	        constructorSignature: Array<out Class<*>>?,
   479	        constructorArgs: Array<out Any>?,
   480	    ): Any {
   481	        val cls = viewClassLoaderProvider().loadClass(name)
   482	        val sig = constructorSignature ?: emptyArray()
   483	        val args = constructorArgs ?: emptyArray()
   484	        val ctor = cls.getDeclaredConstructor(*sig)
   485	        ctor.isAccessible = true
   486	        try {
   487	            return ctor.newInstance(*args)
   488	        } catch (ite: InvocationTargetException) {
   489	            // Q3: cause 를 그대로 throw — layoutlib BridgeInflater.createViewFromTag 가 InflateException 으로 wrap.
   490	            throw ite.cause ?: ite
   491	        }
   492	    }
   493	
   494	    /**
   495	     * F1: BridgeInflater.findCustomInflater 가 isAppCompatTheme=true 일 때
   496	     * `findClass("androidx.appcompat.app.AppCompatViewInflater")` 를 호출.
   497	     * default impl 은 즉시 ClassNotFoundException → null 분기 → AppCompatViewInflater 비활성.
   498	     * 우리 viewClassLoader 가 androidx.appcompat 을 보유하므로 정상 resolve 가능.
   499	     */
   500	    override fun findClass(name: String): Class<*> {
   501	        return viewClassLoaderProvider().loadClass(name)
   502	    }
   503	
   504	    /**
   505	     * F1: sample-app 의존 그래프에 androidx.appcompat 존재 → true. default false.
   506	     * BridgeInflater.findCustomInflater 의 분기 활성화에 필수.
   507	     */
   508	    override fun hasAndroidXAppCompat(): Boolean = true
   509	
   510	    // ... 나머지 (getParser / getAdapterBinding / getActionBarCallback / createXmlParser*) unchanged ...
   511	}
   512	```
   513	
   514	### 4.7 C7 — LayoutlibRenderer 변경 (B1 + Q4 적용)
   515	
   516	생성자에 `sampleAppModuleRoot: Path` 인자 추가 (B1, no default — CLAUDE.md 정책). lazy provider 로 callback 에 주입.
   517	
   518	```kotlin
   519	class LayoutlibRenderer(
   520	    private val distDir: Path,
   521	    private val fallback: PngRenderer?,
   522	    private val fixtureRoot: Path,
   523	    private val sampleAppModuleRoot: Path,  // ← 신규 (B1)
   524	) : PngRenderer {
   525	
   526	    // ... 기존 bootstrap / initialized / classLoader / bridgeInstance 그대로 ...
   527	
   528	    @Volatile private var sampleAppClassLoader: SampleAppClassLoader? = null
   529	
   530	    @Synchronized
   531	    private fun ensureSampleAppClassLoader(): ClassLoader {
   532	        sampleAppClassLoader?.let { return it.classLoader }
   533	        val isolated = classLoader ?: error("Bridge 가 init 안 됨 (initBridge 가 먼저 실행되어야 함)")
   534	        val built = SampleAppClassLoader.build(sampleAppModuleRoot, isolated)
   535	        sampleAppClassLoader = built
   536	        return built.classLoader
   537	    }
   538	
   539	    private fun renderViaLayoutlib(layoutName: String): ByteArray? {
   540	        // ... 기존 코드 (parser / bundle / resources) ...
   541	        val params: SessionParams = SessionParamsFactory.build(
   542	            layoutParser = parser,
   543	            callback = MinimalLayoutlibCallback { ensureSampleAppClassLoader() },  // ← lazy provider
   544	            resources = resources,
   545	        )
   546	        // ... 기존 코드 (bridge.createSession / render / image / dispose) ...
   547	    }
   548	}
   549	```
   550	
   551	**Q4 (lazy)**: `MinimalLayoutlibCallback` 의 `viewClassLoaderProvider: () -> ClassLoader` lambda 가 activity_minimal 처럼 custom view 가 없는 경우 호출 0 → manifest 누락 영향 없음 → W3D1 의 `tier3-values` 회귀 없음.
   552	
   553	**모든 호출부 (C7b)**: `Main.kt`, `SharedLayoutlibRenderer.getOrCreate`, 그리고 production `LayoutlibRenderer(...)` 호출하는 모든 테스트에서 `sampleAppModuleRoot` 인자 명시. CLI override `--sample-app-root=<path>` 추가 (CliConstants/CliArgs).
   554	
   555	### 4.8 C8 — LayoutlibRendererIntegrationTest 변경 (Q5 적용)
   556	
   557	Q5 컨버전스: `Result.Status == SUCCESS` assertion 활성. `LayoutlibRendererTier3MinimalTest` 의 W3D1 패턴과 일관성 유지.
   558	
   559	```kotlin
   560	@Tag("integration")
   561	class LayoutlibRendererIntegrationTest {
   562	
   563	    @Test
   564	    fun `tier3-basic — activity_basic renders SUCCESS with non-empty PNG`() {
   565	        val dist = locateDistDir()
   566	        val layoutRoot = locateFixtureRoot()
   567	        val moduleRoot = locateSampleAppModuleRoot()
   568	        val renderer = SharedLayoutlibRenderer.getOrCreate(
   569	            distDir = dist,
   570	            fixtureRoot = layoutRoot,
   571	            sampleAppModuleRoot = moduleRoot,
   572	            fallback = null,
   573	        )
   574	        val bytes = renderer.renderPng("activity_basic.xml")
   575	
   576	        assertEquals(Result.Status.SUCCESS, renderer.lastSessionResult?.status, "render status SUCCESS 여야 함")
   577	        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG bytes 가 placeholder 보다 큼: ${bytes.size}")
   578	        assertTrue(
   579	            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
   580	                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
   581	            "PNG magic 헤더가 아님",
   582	        )
   583	    }
   584	
   585	    private fun locateSampleAppModuleRoot(): Path {
   586	        val found = FixtureDiscovery.locateModuleRoot(null)
   587	        assumeTrue(found != null, "sample-app module root 없음 — fixture/sample-app 확인")
   588	        return found!!.toAbsolutePath().normalize()
   589	    }
   590	
   591	    // ... locateDistDir / locateFixtureRoot 유지 ...
   592	
   593	    private companion object {
   594	        const val MIN_RENDERED_PNG_BYTES = 1000
   595	    }
   596	}
   597	```
   598	
   599	---
   600	
   601	## 5. 테스트 전략 (TDD)
   602	
   603	각 컴포넌트별 단위 테스트 → integration 테스트 마지막.
   604	
   605	### 5.1 단위
   606	
   607	| Test | 케이스 | 검증 |
   608	|------|-------|------|
   609	| TC1-1 | manifest 파일 누락 | IllegalArgumentException with hint |
   610	| TC1-2 | manifest 빈 파일 | IllegalStateException |
   611	| TC1-3 | 라인이 공백 | IllegalArgumentException with line index |
   612	| TC1-4 | 비-절대경로 | IllegalArgumentException |
   613	| TC1-5 | 정상 (.aar + .jar 혼합) | List<Path> 반환, 순서 유지 |
   614	| TC2-1 | 정상 추출 | classes.jar 가 cacheRoot 에 생성, content 일치 |
   615	| TC2-2 | 캐시 hit | 두 번째 호출이 동일 Path, mtime 변경 없음 |
   616	| TC2-3 | classes.jar 없는 AAR | null 반환 |
   617	| TC2-4 | 손상 ZIP | IOException |
   618	| TC3-1 | URLClassLoader 합성 + parent chain | parent === isolatedCL |
   619	| TC3-2 | manifest 의 .jar 가 그대로 URL 화 | URL list 에 포함 |
   620	| TC3-3 | R.jar 누락 시 require fail | 명확한 메시지 |
   621	| TC4-1 | viewClassLoader 가 known class 를 load | Class 인스턴스 반환 |
   622	| TC4-2 | unknown class 요청 | ClassNotFoundException pass-through |
   623	| TC4-3 | constructor signature mismatch | NoSuchMethodException pass-through |
   624	
   625	### 5.2 Integration
   626	
   627	| Test | 입력 | 검증 |
   628	|------|-----|------|
   629	| `tier3-basic` (renamed from `tier3 - renderPng...`) | activity_basic.xml | T1 gate (PNG > 1000 + magic). 페어 리뷰가 권하면 SUCCESS status 추가. |
   630	
   631	기존 `LayoutlibRendererTier3MinimalTest` (activity_minimal) 영향 없음 — 본 변경 후에도 PASS 보장. lazy CL 덕분에 manifest 누락 영향 없음.
   632	
   633	### 5.3 회귀 가능성
   634	
   635	- Bridge.init / RenderSession 경로 — 변경 없음.
   636	- FrameworkResourceValueLoader / FrameworkRenderResources — 변경 없음.
   637	- SharedLayoutlibRenderer — 변경 없음.
   638	- 99 → 118 unit tests + 11 integration → 본 변경 후: 118 + ~15 unit (TC1-4) + 11 → 12 integration (LayoutlibRendererIntegrationTest enable) PASS. tier3-glyph 만 SKIPPED 유지.
   639	
   640	---
   641	
   642	## 6. 위험 / 알려진 한계
   643	
   644	### 6.1 위험
   645	
   646	| ID | 위험 | 완화 |
   647	|----|------|------|
   648	| R1 | sample-app 미빌드 시 manifest/R.jar 누락 → 모든 integration test 가 FAIL | C2/C4 의 require 메시지가 정확한 명령(`./gradlew :app:assembleDebug`) 안내. 페어 리뷰가 더 강한 요구 시 worker 가 자동으로 invoke 하는 방안 검토. |
   649	| R2 | layoutlib 의 LayoutlibCallback 이 loadView 외 다른 callback (e.g. `getProject` / `findClass`) 도 사용? | 페어 리뷰에서 layoutlib 14.x 의 LayoutlibCallback 추상메서드 audit. 본 스펙은 loadView 만 변경. |
   650	| R3 | ConstraintLayout 의 `app:layout_constraintXxx` custom attr 가 default 로 fallback → render 결과가 broken positioning. T1 gate 통과는 하지만 visual fidelity 미달. | W3D4 carry: app/library resource value loader. 본 스펙 out-of-scope §1.2 명시. |

exec
/bin/bash -lc "nl -ba docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md | sed -n '650,727p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   650	| R3 | ConstraintLayout 의 `app:layout_constraintXxx` custom attr 가 default 로 fallback → render 결과가 broken positioning. T1 gate 통과는 하지만 visual fidelity 미달. | W3D4 carry: app/library resource value loader. 본 스펙 out-of-scope §1.2 명시. |
   651	| R4 | AGP 8.x 의 `compile_and_runtime_not_namespaced_r_class_jar` 경로가 AGP 9.x 에서 변경 가능 | ClassLoaderConstants 에 상수 1개 — 발견 시 단일 변경. |
   652	| R5 | sample-app 의 `debugRuntimeClasspath` 에 layoutlib JAR 이 포함되면 isolatedCL 과 충돌? | sample-app 은 layoutlib 에 의존하지 않음. 가능성 없음. |
   653	| R6 | `forkEvery(1L)` 에서 두 integration test 가 같은 sample-app cache dir 을 동시에 쓰지 않나 | Gradle test fork 는 process 격리, 같은 디스크지만 mtime 기반 idempotent → race condition 시 둘이 동일 컨텐츠로 덮어씀. 안전. |
   654	
   655	### 6.2 알려진 한계
   656	
   657	- **Visual fidelity**: §1.2 R3 — out-of-scope, W3D4+ carry.
   658	- **Multi-fixture**: 본 스펙은 sample-app 만 가정. 다중 fixture 지원은 W3D4+.
   659	- **Hot reload**: 빌드 manifest 가 매 build 마다 갱신 → 워커 재시작 필요. 현재 worker 는 매 test class 별 fork 라 실용 영향 없음.
   660	
   661	---
   662	
   663	## 9. Contingency — B2 empirical fallback
   664	
   665	**B2 가설** (Codex round 1): `MaterialButton` 의 Material theme enforcement (`MaterialThemeOverlay.wrap`, `ThemeEnforcement.checkCompatibleTheme`) 가 framework theme + missing app/library resource values 환경에서 inflate 시 throw → `Result.Status != SUCCESS`.
   666	
   667	**검증 단계** (구현 phase 의 첫 통합 테스트 실행 직후):
   668	1. T1 gate (SUCCESS + PNG > 1000) PASS → B2 가설 무효, 본 스펙 그대로 close.
   669	2. T1 gate FAIL with `ThemeEnforcement` 또는 `MaterialThemeOverlay` 관련 exception → §9.1 발동.
   670	
   671	### 9.1 Contingency layout — `activity_basic_minimal.xml`
   672	
   673	만약 §1 의 acceptance gate 가 MaterialButton 때문에 깨지면, 동일 fixture 디렉토리에 추가 layout 파일을 작성하고 `LayoutlibRendererIntegrationTest` 의 타겟을 그것으로 변경:
   674	
   675	```xml
   676	<!-- fixture/sample-app/app/src/main/res/layout/activity_basic_minimal.xml -->
   677	<androidx.constraintlayout.widget.ConstraintLayout
   678	    xmlns:android="http://schemas.android.com/apk/res/android"
   679	    xmlns:app="http://schemas.android.com/apk/res-auto"
   680	    android:layout_width="match_parent"
   681	    android:layout_height="match_parent">
   682	
   683	    <TextView
   684	        android:id="@+id/title"
   685	        android:layout_width="wrap_content"
   686	        android:layout_height="wrap_content"
   687	        android:text="@string/fixture_basic_title"
   688	        app:layout_constraintTop_toTopOf="parent"
   689	        app:layout_constraintStart_toStartOf="parent" />
   690	
   691	    <Button
   692	        android:id="@+id/primary_button"
   693	        android:layout_width="wrap_content"
   694	        android:layout_height="wrap_content"
   695	        android:text="@string/fixture_basic_button"
   696	        app:layout_constraintTop_toBottomOf="@id/title"
   697	        app:layout_constraintStart_toStartOf="parent" />
   698	</androidx.constraintlayout.widget.ConstraintLayout>
   699	```
   700	
   701	**왜 contingency 가 W3D3 의 의도를 지키나**:
   702	- 핵심 deliverable = "in-JVM custom view classloading 증명". `androidx.constraintlayout.widget.ConstraintLayout` 도 layoutlib framework 외부 클래스이므로 classloader 경로의 모든 코드 (loadView / findClass / AAR 추출 / R.jar / parent chain) 가 동일하게 exercised.
   703	- MaterialButton 만 표준 `android.widget.Button` (framework, isolated CL) 으로 교체 — 새 코드 경로 영향 없음.
   704	- 원래 `activity_basic.xml` 은 보존 — W3D4 (app/library resource value loader) 완료 후 다시 타겟으로 환원.
   705	
   706	**spec close 전 결정**: 구현 phase 에서 empirical 결과에 따라 §1 의 layout 이름을 "activity_basic.xml" 또는 "activity_basic_minimal.xml" 중 어느 것으로 fix 할지 work_log 에 기록.
   707	
   708	---
   709	
   710	## 7. 페어 리뷰용 질문 (Q1-Q5)
   711	
   712	페어 리뷰 (Codex+Claude) 에서 명시적으로 검증 요청:
   713	
   714	- **Q1** (Gradle): `applicationVariants.all` API 가 `axpEmitClasspath` 를 더 안정적으로 wire 할 수 있나? AGP 8.x 의 best practice 는?
   715	- **Q2** (Cache): `AarExtractor` 의 mtime 기반 invalidation 이 충분한가? hash 기반 (sha256) 으로 변경해야 하는 시나리오가 있나?
   716	- **Q3** (loadView): InvocationTargetException 의 cause 를 unwrap 해서 re-throw 해야 하나? layoutlib 14.x 의 inflater 가 unwrap 을 기대하나?
   717	- **Q4** (lazy build): SampleAppClassLoader 빌드를 lazy 로 미루면 (옵션 A) activity_minimal 호환성 유지. 더 단순한 옵션 (B: empty CL) 이 권장되는가?
   718	- **Q5** (assertion): T1 gate 에 `Result.Status == SUCCESS` 를 추가해야 하나? 현재 W2D7 패턴은 PNG > 1000 만 검사 — strictness 의 trade-off?
   719	
   720	페어 리뷰 verdict 형식: GO / GO_WITH_FOLLOWUPS / NO_GO + 각 Q 별 stance.
   721	
   722	---
   723	
   724	## 8. 변경 로그
   725	
   726	- 2026-04-29: 초안 작성. W3D2 carry 의 sample-app unblock 옵션 A 의 in-JVM 변형 (canonical AVD-L3 와 구분).
   727	- 2026-04-29 (round 1 페어 리뷰 반영): B1 (fixtureRoot 분리), F1 (findClass + hasAndroidXAppCompat), B4 (magic strings → ClassLoaderConstants), Q1 (afterEvaluate 제거), Q3 (ITE unwrap), Q5 (SUCCESS assertion 활성), manifest distinct, atomic AAR write. §9 contingency 신설 (B2 empirical risk).

exec
/bin/bash -lc 'rg -n "LM-B1 Task 4|compile atomicity|landmine|Task 4" docs/work_log docs | head -80' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/handoff.md:138:## 3. 다음 세션이 반드시 알아야 할 landmines
docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/next-session-prompt.md:62:## 이미 해결된 landmines (재발 방지)
docs/superpowers/specs/2026-04-24-w3d2-integration-cleanup-design.md:16:3. **C3 — TEST-INFRA-SHARED-RENDERER (Codex F4)**: W2D7 L4 landmine (native lib 프로세스 당 1회 로드) 은 *같은 test class 내 여러 @Test 메서드* 가 동일 JVM 에서 실행될 때 문제가 된다. `LayoutlibRendererTier3MinimalTest.companion object` 에 `sharedRenderer` 패턴으로 이를 막아뒀다. 다른 integration test class (`LayoutlibRendererIntegrationTest.kt:26`) 는 Gradle `forkEvery(1L)` (`server/layoutlib-worker/build.gradle.kts:60-65`) 덕에 **독립 JVM fork** 에서 실행되므로 cross-class 의 native lib 충돌은 없다. 그러나 동일 클래스 내 `LayoutlibRenderer(dist)` 를 직접 호출하는 일관성 부재 + `assumeTrue(false)` 의 throwable catch 가 실패를 모두 skip 로 전환해 커버리지 손실을 가리는 두 문제는 남아 있다. 이 carry 는 **각 JVM fork 내부** 에서 일관된 singleton 패턴 + 명시적 `@Disabled` 로 두 문제를 정돈한다.
docs/superpowers/specs/2026-04-24-w3d2-integration-cleanup-design.md:539:## 10. 예상 landmine + 회피
docs/superpowers/specs/2026-04-24-w3d2-integration-cleanup-design.md:541:| ID | 예상 landmine | 회피 |
docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md:91:| `FrameworkRenderResources` | `RenderResources()` subclass. 4 override: `getStyle`, `getUnresolvedResource`, `getResolvedResource`, `getDefaultTheme`. `findResValue`는 W2D7 L3 landmine 에 따라 override 금지. | bundle, `SessionConstants.DEFAULT_FRAMEWORK_THEME` |
docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md:135:- **파일 누락** → `IllegalStateException("필수 프레임워크 리소스 XML 누락: <path>")`. fail-fast. fallback 금지 (W2D7 L3 landmine).
docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:8:- **Phase 1 (C1+C2)** — `LayoutlibRenderer.fallback` / `fixtureRoot` default 제거, `DistDiscovery` / `FixtureDiscovery` / `CliConstants` / `CliArgs` 신규 object 로 path / CLI 상수화, `Main.kt` 가 `CliArgs.parse(args, CliConstants.VALUE_BEARING_FLAGS)` → `chooseRenderer(parsed)` 로 재배선. Task 4 는 **default 제거와 동시에 두 test 파일** (Tier3MinimalTest, LayoutlibRendererIntegrationTest) 의 constructor 호출을 3-arg 로 동반 수정 (compile 유지).
docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:13:**Non-git project note:** 본 저장소는 git init 되어 있지 않다 (`fatal: not a git repository` 확인, W3D1 L6 landmine). 각 Task 의 commit step 은 생략하고, Phase 경계에서 전체 gradle gate + 마지막에 session work_log 에 journaling. Codex 호출 시 `--skip-git-repo-check` 필수.
docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:575:## Task 4: LayoutlibRenderer default 제거 + Tier3 factory 명시 주입 (임시)
docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:671:- [ ] **Step 4.6: 빌드 확인 (mcp-server 컴파일 에러 예상 — Task 4+5 atomicity scope)**
docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:678:**Atomicity note (MF-B1 extension)**: Task 4+5 는 atomic unit. 중간 단계에서 **full-project gate (`./server/gradlew -p server test` 또는 `./server/gradlew -p server build`) 를 절대 돌리지 말 것** — mcp-server 컴파일 FAIL 이 의도된 intermediate state. Full-project gate 는 Task 6 에서만 실행.
docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1457:## 4. 발견된 landmine
docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1517:## 주의 landmine 재발 방지
docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:302:## Task 4: FrameworkValueParser (TDD R2)
docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:1287:        // 리플렉션으로 override 선언 여부 검증. W2D7 L3 landmine: findResValue override 는 프레임워크
docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:1291:        assertTrue(findResValue == null, "findResValue 는 override 하지 말 것 — W2D7 landmine L3")
docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:1318: *  - `findResValue` 는 override 금지 (W2D7 L3 landmine).
docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:1688:landmine, pair review 결과.
docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:1696:상세 내용은 실제 구현 후 본 세션 종료 시점에 작성 (구현 과정에서 새로 발견된 landmine 은
docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:1756:     확인. Task 4 step 3 의 parser 가 compile 시점에 인식 가능해야.
docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:398:## Task 4: `SampleAppClassLoader`
docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:937:- [ ] **Step 1: session-log 작성** — 변경 파일 목록, 테스트 카운트 변화, 발견된 landmine, B2 contingency 발동 여부, pair-review verdicts.
docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:41:- `server/layoutlib-worker/build.gradle.kts` — declare `runtimeOnly` layoutlib transitives (Task 4).
docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:866:## Task 4 — Declare layoutlib transitives + widen isolated classloader parent
docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:1013: * dist + Tier2 PASS 가 선결 (Task 4).
docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:1195:Expected: PASS — Bridge.init succeeds via bootstrap + transitives (Task 4), renderer emits a PNG with the PNG magic header. If init still fails, the test SKIPs with a specific message (record in handoff).
docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:1400:- **Spec coverage**: §7.7 items 1 (stdio) → Task 2, item 2 (fatJar transitive) → Task 4, item 3 (real render) → Task 5, item 4 (MILESTONES close) → Task 6.
docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:1401:- **Placeholders**: every code step has full code. Task 4 Step 3 carries a conditional add-more-deps loop but with concrete fallback coords; not a placeholder.
docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:523:    40	## 주의 landmine 재발 방지
docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:1360:   625	- `FrameworkRenderResources` 가 bundle delegate 로 layoutlib 에 값 제공. `findResValue` override 금지 (W2D7 L3 landmine, reflection guard 테스트 존재).
docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:1380:   645	- **플랜 단계** (`docs/W3D1-PLAN-PAIR-REVIEW.md`): Codex 호출이 "quota 소진" 으로 오인되었으나 실제 원인은 non-git 프로젝트의 trust-check hang (landmine L6). `--skip-git-repo-check` 플래그 누락. Claude 단독 리뷰로 F1-F6 반영.
docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:4216:    16	 *  - `findResValue` 는 override 금지 (W2D7 L3 landmine — 기본 RenderResources 의 ref 해석을 가로챔).
docs/superpowers/plans/2026-04-23-w2d7-rendersession.md:317:## Task 4 — NoopAssetRepository + SessionParamsFactory (TDD)
docs/superpowers/plans/2026-04-23-w2d7-rendersession.md:599:- [ ] session-log: 수정 파일/신규 파일 목록 + 각 파일당 1줄 이유. landmine 기록.
docs/superpowers/plans/2026-04-23-w2d7-rendersession.md:639:## Known landmines / fail-fast exits
docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md:8:- Codex CLI 호출 시 `--skip-git-repo-check` 필수 (landmine L6 — non-git 프로젝트의 trust-check hang).
docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md:13:2. `docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md` — 상세 변경 내역 + landmine.
docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md:56:## 주의 landmine 재발 방지
docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md:31:| `FrameworkRenderResources.kt` | RenderResources subclass. bundle delegate. `findResValue` override 금지 (W2D7 L3 landmine). |
docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md:83:## 4. 발견된 landmine
docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md:105:`FrameworkRenderResourcesTest` 에 reflection guard 추가 (`findResValue` 메서드 미정의 검증). W2D7 L3 landmine 재발 방지.
docs/W2D7-PLAN-PAIR-REVIEW.md:32:### F2 — Theme application to Task 4 baseline (not Task 6 fallback)
docs/W2D7-PLAN-PAIR-REVIEW.md:38:**Action**: In Task 4 SessionParamsFactory, **MUST** set up RenderResources with `Theme.Material.Light.NoActionBar` applied as default theme and `setForceNoDecor()` on SessionParams. Empty RenderResources allowed only in explicit negative-test path (document this clearly).
docs/W2D7-PLAN-PAIR-REVIEW.md:40:**Open execution question**: The plan does not specify the concrete layoutlib API for "load framework theme from `data/res/` into RenderResources". Codex explicitly flagged this as a plan gap. Resolution: Task 4 Step 2 gets an initial research sub-step that either (a) uses `ResourceResolver.create(projectResources, frameworkResources, themeName, isProjectTheme)` if signature is compatible, OR (b) implements a minimal `RenderResources` subclass overriding `getDefaultTheme()` / `findItemInTheme()` to return hardcoded defaults for the handful of attrs TextView/LinearLayout actually query during inflation of the minimal fixture. Outcome recorded in session-log.
docs/work_log/2026-04-23_w1d2-d5-http-viewer/handoff.md:120:## 3. 다음 세션이 반드시 알아야 할 landmines
docs/W3D1-PLAN-PAIR-REVIEW.md:150:**교훈 (landmine L6)**: Codex rescue skill 이나 직접 호출 모두 **non-git 프로젝트에서는 `--skip-git-repo-check` 필수**. 이전 "quota 소진" 과 본 "trust-check 행" 은 증상이 비슷하지만 (Codex 가 응답하지 않음) 원인이 다르며, quota 메시지는 명확히 출력되는 반면 trust-check 는 silent hang. 다음 세션의 `codex:rescue` 호출 시 주의.
docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md:40:## 주의 landmine 재발 방지
docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:46:## 4. 발견된 landmine / 새 carry
docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:48:### LM-B1 (Task 4 compile atomicity — Codex round 2)
docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:49:`LayoutlibRendererIntegrationTest.kt:26` 이 1-arg `LayoutlibRenderer(dist)` 를 쓰고 있어, Task 4 에서 default 제거 시 `:layoutlib-worker:test` 컴파일 FAIL. 해결: Task 4 에 `LayoutlibRendererIntegrationTest` 임시 update step 추가 (Task 10 에서 정식 `@Disabled` + SharedLayoutlibRenderer 로 최종 전환).
docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:67:- **Round 2 (Codex direct `codex exec`)**: 파일 실증 검증. NO_GO — 3 blockers (B1/B2/B3 위 landmine 참조).
docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/handoff.md:138:## 3. 다음 세션이 반드시 알아야 할 landmines
docs/work_log/2026-04-23_w2d6-stdio-and-fatjar/handoff.md:105:## 3. landmines 기록
docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/next-session-prompt.md:62:## 이미 해결된 landmines (재발 방지)
docs/work_log/2026-04-23_w2d6-stdio-and-fatjar/handoff.md:105:## 3. landmines 기록
docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:523:    40	## 주의 landmine 재발 방지
docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:1360:   625	- `FrameworkRenderResources` 가 bundle delegate 로 layoutlib 에 값 제공. `findResValue` override 금지 (W2D7 L3 landmine, reflection guard 테스트 존재).
docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:1380:   645	- **플랜 단계** (`docs/W3D1-PLAN-PAIR-REVIEW.md`): Codex 호출이 "quota 소진" 으로 오인되었으나 실제 원인은 non-git 프로젝트의 trust-check hang (landmine L6). `--skip-git-repo-check` 플래그 누락. Claude 단독 리뷰로 F1-F6 반영.
docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:4216:    16	 *  - `findResValue` 는 override 금지 (W2D7 L3 landmine — 기본 RenderResources 의 ref 해석을 가로챔).
docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md:40:## 주의 landmine 재발 방지
docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md:8:- Codex CLI 호출 시 `--skip-git-repo-check` 필수 (landmine L6 — non-git 프로젝트의 trust-check hang).
docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md:13:2. `docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md` — 상세 변경 내역 + landmine.
docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md:56:## 주의 landmine 재발 방지
docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md:31:| `FrameworkRenderResources.kt` | RenderResources subclass. bundle delegate. `findResValue` override 금지 (W2D7 L3 landmine). |
docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md:83:## 4. 발견된 landmine
docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md:105:`FrameworkRenderResourcesTest` 에 reflection guard 추가 (`findResValue` 메서드 미정의 검증). W2D7 L3 landmine 재발 방지.
docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:46:## 4. 발견된 landmine / 새 carry
docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:48:### LM-B1 (Task 4 compile atomicity — Codex round 2)
docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:49:`LayoutlibRendererIntegrationTest.kt:26` 이 1-arg `LayoutlibRenderer(dist)` 를 쓰고 있어, Task 4 에서 default 제거 시 `:layoutlib-worker:test` 컴파일 FAIL. 해결: Task 4 에 `LayoutlibRendererIntegrationTest` 임시 update step 추가 (Task 10 에서 정식 `@Disabled` + SharedLayoutlibRenderer 로 최종 전환).
docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:67:- **Round 2 (Codex direct `codex exec`)**: 파일 실증 검증. NO_GO — 3 blockers (B1/B2/B3 위 landmine 참조).
docs/work_log/2026-04-23_w1d2-d5-http-viewer/handoff.md:120:## 3. 다음 세션이 반드시 알아야 할 landmines
docs/plan/08-integration-reconciliation.md:625:- `FrameworkRenderResources` 가 bundle delegate 로 layoutlib 에 값 제공. `findResValue` override 금지 (W2D7 L3 landmine, reflection guard 테스트 존재).
docs/plan/08-integration-reconciliation.md:645:- **플랜 단계** (`docs/W3D1-PLAN-PAIR-REVIEW.md`): Codex 호출이 "quota 소진" 으로 오인되었으나 실제 원인은 non-git 프로젝트의 trust-check hang (landmine L6). `--skip-git-repo-check` 플래그 누락. Claude 단독 리뷰로 F1-F6 반영.

exec
/bin/bash -lc "rg -n \"LayoutlibRenderer\\(\" server --glob '*.kt'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:44:        val created = LayoutlibRenderer(distDir, fallback, fixtureRoot)
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:130:        LayoutlibRenderer(
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:44:class LayoutlibRenderer(

exec
/bin/bash -lc "rg -n \"MinimalLayoutlibCallback\\(\" server --glob '*.kt'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:171:            callback = MinimalLayoutlibCallback(),
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:29:     * @param callback LayoutlibCallback — 호출자 주입 필수 (production 은 `MinimalLayoutlibCallback()`).
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:42:            callback = MinimalLayoutlibCallback(),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:65:            callback = MinimalLayoutlibCallback(),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:22:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:31:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:39:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:47:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:53:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:59:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:65:        val cb = MinimalLayoutlibCallback()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:71:        val cb = MinimalLayoutlibCallback()

exec
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.session
     2	
     3	import com.android.ide.common.rendering.api.ResourceNamespace
     4	import com.android.ide.common.rendering.api.SessionParams
     5	import com.android.resources.Density
     6	import com.android.resources.ScreenOrientation
     7	import dev.axp.layoutlib.worker.resources.FrameworkRenderResources
     8	import dev.axp.layoutlib.worker.resources.FrameworkResourceBundle
     9	import org.junit.jupiter.api.Assertions.assertEquals
    10	import org.junit.jupiter.api.Assertions.assertNotNull
    11	import org.junit.jupiter.api.Assertions.assertSame
    12	import org.junit.jupiter.api.Assertions.assertTrue
    13	import org.junit.jupiter.api.Test
    14	import java.io.StringReader
    15	
    16	/**
    17	 * W2D7-RENDERSESSION — SessionParamsFactory 가 layoutlib 이 기대하는 SessionParams 를
    18	 * 올바른 필드값으로 생성하는지 검증.
    19	 *
    20	 * CLAUDE.md F2 (페어 리뷰): empty RenderResources 금지 → default theme 필수.
    21	 */
    22	class SessionParamsFactoryTest {
    23	
    24	    private val sampleXml = """
    25	        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    26	            android:layout_width="match_parent" android:layout_height="match_parent"/>
    27	    """.trimIndent()
    28	
    29	    /**
    30	     * W3D1 3b-values: resources 파라미터 default 제거 → 테스트용 empty bundle + DEFAULT_FRAMEWORK_THEME.
    31	     * 실 값 로딩은 integration (tier3-values) 에서 검증.
    32	     */
    33	    private fun emptyFrameworkRenderResources() =
    34	        FrameworkRenderResources(
    35	            FrameworkResourceBundle.build(emptyList()),
    36	            SessionConstants.DEFAULT_FRAMEWORK_THEME,
    37	        )
    38	
    39	    private fun buildParams(): SessionParams =
    40	        SessionParamsFactory.build(
    41	            layoutParser = LayoutPullParserAdapter.fromReader(StringReader(sampleXml)),
    42	            callback = MinimalLayoutlibCallback(),
    43	            resources = emptyFrameworkRenderResources(),
    44	        )
    45	
    46	    @Test
    47	    fun `HardwareConfig is phone xhigh portrait`() {
    48	        val hw = buildParams().hardwareConfig
    49	        assertEquals(SessionConstants.RENDER_WIDTH_PX, hw.screenWidth)
    50	        assertEquals(SessionConstants.RENDER_HEIGHT_PX, hw.screenHeight)
    51	        assertEquals(Density.XHIGH, hw.density)
    52	        assertEquals(ScreenOrientation.PORTRAIT, hw.orientation)
    53	    }
    54	
    55	    @Test
    56	    fun `rendering mode is NORMAL`() {
    57	        assertEquals(SessionParams.RenderingMode.NORMAL, buildParams().renderingMode)
    58	    }
    59	
    60	    @Test
    61	    fun `layout parser is forwarded to SessionParams`() {
    62	        val parser = LayoutPullParserAdapter.fromReader(StringReader(sampleXml))
    63	        val params = SessionParamsFactory.build(
    64	            layoutParser = parser,
    65	            callback = MinimalLayoutlibCallback(),
    66	            resources = emptyFrameworkRenderResources(),
    67	        )
    68	        assertSame(parser, params.layoutDescription)
    69	    }
    70	
    71	    @Test
    72	    fun `timeout is configured`() {
    73	        assertEquals(SessionConstants.RENDER_TIMEOUT_MS, buildParams().timeout)
    74	    }
    75	
    76	    @Test
    77	    fun `default theme is non-null and framework-namespaced`() {
    78	        val theme = buildParams().resources.defaultTheme
    79	        assertNotNull(theme, "F2: empty RenderResources 금지 — default theme 필수")
    80	        assertEquals(ResourceNamespace.ANDROID, theme.namespace)
    81	        assertEquals(SessionConstants.DEFAULT_FRAMEWORK_THEME, theme.name)
    82	    }
    83	
    84	    @Test
    85	    fun `AssetRepository is attached and non-supported`() {
    86	        val params = buildParams()
    87	        val assets = params.assets
    88	        assertNotNull(assets)
    89	        assertTrue(!assets.isSupported, "NoopAssetRepository 는 비지원 모드로 Bridge 에 신호")
    90	    }
    91	
    92	    @Test
    93	    fun `project key and sdk levels are set`() {
    94	        val params = buildParams()
    95	        assertEquals(SessionConstants.PROJECT_KEY, params.projectKey)
    96	        assertEquals(SessionConstants.MIN_SDK, params.minSdkVersion)
    97	        assertEquals(SessionConstants.TARGET_SDK, params.targetSdkVersion)
    98	    }
    99	
   100	    @Test
   101	    fun `locale is configured`() {
   102	        assertEquals(SessionConstants.RENDER_LOCALE, buildParams().locale)
   103	    }
   104	
   105	    @Test
   106	    fun `forceNoDecor flag applied`() {
   107	        // 페어 리뷰 (Claude): setForceNoDecor 가 적용되지 않으면 status bar/action bar 가
   108	        // 포함되어 targeted-rect pixel 체크가 false-positive 될 수 있음.
   109	        val field = java.lang.Class.forName("com.android.ide.common.rendering.api.RenderParams")
   110	            .getDeclaredField("mForceNoDecor").apply { isAccessible = true }
   111	        assertTrue(field.getBoolean(buildParams()), "setForceNoDecor() 가 적용되어야 함")
   112	    }
   113	
   114	    @Test
   115	    fun `rtl support enabled`() {
   116	        val field = java.lang.Class.forName("com.android.ide.common.rendering.api.RenderParams")
   117	            .getDeclaredField("mSupportsRtl").apply { isAccessible = true }
   118	        assertTrue(field.getBoolean(buildParams()), "setRtlSupport(true) 가 적용되어야 함")
   119	    }
   120	
   121	    @Test
   122	    fun `font scale is unity`() {
   123	        assertEquals(1.0f, buildParams().fontScale, 0.001f)
   124	    }
   125	
   126	    @Test
   127	    fun `uiMode set to normal day`() {
   128	        // UI_MODE_TYPE_NORMAL (0x01) | UI_MODE_NIGHT_NO (0x10) = 0x11 (17).
   129	        assertEquals(0x11, buildParams().uiMode)
   130	    }
   131	}

exec
/bin/bash -lc 'nl -ba server/layoutlib-worker/build.gradle.kts' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc 'rg -n "@Test|@Disabled|@Tag|includeTags|forkEvery|useJUnitPlatform|filter" server/layoutlib-worker/src/test/kotlin server/layoutlib-worker/build.gradle.kts' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/layoutlib-worker/build.gradle.kts:59:// unit test (no integration tag) 는 forkEvery 비활성 — 빠른 실행 유지.
server/layoutlib-worker/build.gradle.kts:61:    val includeTagsProp = providers.gradleProperty("includeTags").orNull
server/layoutlib-worker/build.gradle.kts:62:    if (!includeTagsProp.isNullOrBlank() && includeTagsProp.contains("integration")) {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:31: * @Tag("integration") — `./gradlew :layoutlib-worker:test` 기본 실행에서는 제외.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:32: *   `./gradlew :layoutlib-worker:test -PincludeTags=integration` 로 명시 실행.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:37:@Tag("integration")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:50:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:62:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:75:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:106:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt:16:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt:23:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt:32:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:11: *   여러 @Test 메서드 가 각자 별도 `LayoutlibRenderer` 를 만들면 두 번째부터
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:14: *   Gradle `forkEvery(1L)` 가 test class 단위 JVM 격리를 보장하므로 cross-class 는 문제
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:27: * 없음 → dead API. YAGNI 로 제거. cross-class 격리는 Gradle forkEvery(1L) 이 담당.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:14:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:23:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:36:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:51:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:64:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/DistDiscoveryTest.kt:14:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/DistDiscoveryTest.kt:23:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/DistDiscoveryTest.kt:36:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/DistDiscoveryTest.kt:51:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:26: * 따라서 본 세션의 Tier3 는 **architecture evidence 만** assert. 실 pixel 테스트는 `@Disabled` 로
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:29:@Tag("integration")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:56:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:93:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:117:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:118:    @Disabled("tier3-glyph W4 carry — Font wiring + glyph 렌더링 검증 (T2 gate)")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:11:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:30:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:52:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:64:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:74:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:9:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:17:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:25:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:33:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:39:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceLoaderRealDistTest.kt:24:@Tag("integration")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceLoaderRealDistTest.kt:33:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceLoaderRealDistTest.kt:44:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:29:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:39:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:49:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:57:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:66:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:71:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:90:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:24:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:32:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:40:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:49:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:56:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:67:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:16:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:30:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:42:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:51:        val list = parse(xml).filterIsInstance<ParsedEntry.SimpleValue>()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:59:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:72:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:91:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:104:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:112:        val attrs = parse(xml).filterIsInstance<ParsedEntry.AttrDef>()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:118:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:131:        val attrs = parse(xml).filterIsInstance<ParsedEntry.AttrDef>()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:143:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:157:        val attrs = parse(xml).filterIsInstance<ParsedEntry.AttrDef>()
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:163:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:176:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:46:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:55:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:60:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:71:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:76:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:84:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:92:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:100:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:105:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:114:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:121:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:126:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapterTest.kt:29:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapterTest.kt:36:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapterTest.kt:43:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapterTest.kt:49:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapterTest.kt:55:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:20:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:29:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:37:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:45:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:51:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:57:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:63:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:69:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrapTest.kt:21:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrapTest.kt:28:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrapTest.kt:41:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrapTest.kt:55:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibBootstrapTest.kt:73:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:15: * 본 test 는 native lib 를 실제 로드한다 (LayoutlibRenderer 생성). 따라서 @Tag("integration").
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:16: * Gradle `forkEvery(1L)` (`server/layoutlib-worker/build.gradle.kts:60-65`) 덕에 이 test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:17: * class 는 독립 JVM fork 에서 실행되고, 그 fork 안의 3 @Test 는 순차 실행되며 singleton
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:21:@Tag("integration")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:24:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:34:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:45:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:17: * 여전히 남아있어 render 실패가 예측된다. 따라서 `@Disabled` annotation 으로 명시적
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:20: * 호출부는 **SharedLayoutlibRenderer** 를 사용하도록 유지해, @Disabled 가 풀렸을 때
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:23:@Tag("integration")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:24:@Disabled("W3 sample-app unblock 필요 — L3 DexClassLoader carry (ConstraintLayout / MaterialButton)")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:27:    @Test

exec
/bin/bash -lc "rg --files -g 'CLAUDE.md' -g 'AGENTS.md' -g 'GEMINI.md'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
CLAUDE.md

exec
/bin/bash -lc 'git status --short' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
exec
/bin/bash -lc 'find . -name CLAUDE.md -o -name AGENTS.md | sort' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
./CLAUDE.md

exec
/bin/bash -lc "nl -ba CLAUDE.md | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	# CLAUDE.md — Android XML Previewer
     2	
     3	Project-local guidance for Claude Code. Global user instructions in `~/.claude/CLAUDE.md` still apply.
     4	
     5	---
     6	
     7	## Session work log discipline
     8	
     9	After completing a unit of work (session, feature, fix, milestone, pair review, or anything future sessions will need to reconstruct), summarize the progress and persist it under `docs/work_log/`. Pick ONE of the three patterns below — whichever best fits the work. Don't bury substantial work in someone else's session; don't spin up a new folder for a one-line follow-up.
    10	
    11	### Pattern 1 — Update an existing file in an existing folder
    12	
    13	Use when the work continues a session already recorded (same-day follow-up, minor amendment, correction of a prior entry) and fits naturally into an existing `session-log.md` / `handoff.md` / sibling file. Edit in place; never overwrite historical entries — append or add a dated subsection.
    14	
    15	### Pattern 2 — Create a new file in an existing folder
    16	
    17	Use when the work is a distinct artifact within an existing session's scope (e.g., adding a `pair-review.md`, `postmortem.md`, or `rerun-notes.md` alongside the session's `session-log.md`). Match the existing naming style of that folder.
    18	
    19	### Pattern 3 — Create a new folder + new file
    20	
    21	Use when the work is a new session or milestone worth isolating. Follow the existing convention:
    22	
    23	```
    24	docs/work_log/YYYY-MM-DD_<slug>/
    25	  session-log.md        # what changed, files, tests, landmines
    26	  handoff.md            # (if next session needs a cold-start onramp)
    27	  next-session-prompt.md # (if handoff is meaningful)
    28	```
    29	
    30	Use absolute dates (`2026-04-23`), never relative ("today", "yesterday"). Slug should be short and descriptive (`w2d7-rendersession`, not `work`).
    31	
    32	---
    33	
    34	## What to include
    35	
    36	Every entry should let a cold-start future session continue without reconstructing context:
    37	
    38	- **Outcome** — what changed and why (1-2 sentences, not a diff dump).
    39	- **Files created/modified** — one line per file explaining its reason-to-exist.
    40	- **Test results** after the change (counts, pass/fail/skip, key names).
    41	- **Landmines discovered** and how they were resolved — these compound over sessions.
    42	- **Canonical document changes** (e.g., `docs/plan/...` edits) with section numbers.
    43	- **What's blocking / carried forward** to the next session, if anything.
    44	- **Pair review verdicts** (if applicable), following the pairing policy in `~/.claude/CLAUDE.md`.
    45	
    46	## When NOT to create a work_log entry
    47	
    48	Skip work_log updates for trivial reads, exploratory questions, one-line fixes, or status checks — unless the user explicitly asks. Work_log is for work that future sessions need to understand in order to continue without context loss. Noise pollutes signal.
    49	
    50	## Picking the lightest pattern
    51	
    52	Prefer Pattern 1 over Pattern 2, and Pattern 2 over Pattern 3, when the work genuinely fits. Creating a new folder signals "this is a distinct session/milestone worth indexing later"; use it only when that framing is accurate.
    53	
    54	---
    55	
    56	## Commit & push at task-unit completion
    57	
    58	**Standing authorization** (overrides the global default of "never commit unless explicitly asked", within the scope defined below): whenever a task unit is complete, commit it and push to `origin` immediately. Do not batch multiple task units into one commit; do not leave finished work uncommitted at session end.
    59	
    60	### What counts as a "task unit"
    61	
    62	- A discrete item from a `TaskCreate` / `TaskUpdate` list, marked `completed`.
    63	- A self-contained feature, fix, or refactor — one focused intent, not a grab-bag.
    64	- A milestone or pair-review round whose outcome is a coherent state.
    65	- A `docs/work_log/` entry produced under the work-log discipline above.
    66	
    67	A unit is "complete" only when its tests pass (or the absence of tests is intentional and noted), the build is green, and the work would make sense to a cold-start future session.
    68	
    69	### How to commit & push
    70	
    71	1. Stage only the files that belong to this unit (`git add <paths>` — never `-A` blindly when unrelated edits are pending).
    72	2. Write the commit message via HEREDOC, following the global commit policy in `~/.claude/CLAUDE.md` (concise subject, "why" over "what", `Co-Authored-By` trailer).
    73	3. If the unit produced a `work_log` entry, include it in the same commit — code and work_log ship together.
    74	4. `git push` immediately after the commit succeeds. Do not accumulate local-only commits across task units.
    75	5. If a hook fails, fix the underlying issue and create a NEW commit — never bypass with `--no-verify` or amend an already-pushed commit.
    76	
    77	### Still out of scope — explicit user authorization required each time
    78	
    79	This standing rule covers only linear, additive commits and non-force pushes to the current branch. The following remain blocked unless the user requests them in the moment:
    80	
    81	- Force-push (`--force`, `--force-with-lease`) or history rewrites (`rebase -i`, `reset --hard` on shared branches, `commit --amend` after push).
    82	- Creating, switching, merging, or deleting branches the user did not ask for.
    83	- Skipping hooks (`--no-verify`, `--no-gpg-sign`).
    84	- Committing `.gitignore`-excluded files, large binaries, secrets, or anything credential-shaped.
    85	
    86	### When NOT to commit per task unit
    87	
    88	- Trivial reads, exploratory questions, or status checks — same exclusions as the work-log discipline.
    89	- Work-in-progress that does not yet build or pass tests — finish or split the unit first.
    90	- When the user has said "don't commit yet" in the current session — that overrides this standing rule.

codex
I found one concrete compile gap already: Task 6 updates `MinimalLayoutlibCallbackTest`, but current no-arg callback callers also exist in `SessionParamsFactoryTest`. I’m continuing through Gradle and test-cardinality checks before assigning the final verdict.
exec
/bin/bash -lc './gradlew :app:tasks --all' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app
 succeeded in 677ms:

> Task :app:tasks

------------------------------------------------------------
Tasks runnable from project ':app'
------------------------------------------------------------

Android tasks
-------------
androidDependencies - Displays the Android dependencies of the project.
signingReport - Displays the signing info for the base and test modules
sourceSets - Prints out all the source sets defined in this project.

Build tasks
-----------
assemble - Assemble main outputs for all the variants.
assembleAndroidTest - Assembles all the Test applications.
assembleUnitTest - Assembles all the unit test applications.
build - Assembles and tests this project.
buildDependents - Assembles and tests this project and all projects that depend on it.
buildKotlinToolingMetadata - Build metadata json file containing information about the used Kotlin tooling
buildNeeded - Assembles and tests this project and all projects it depends on.
bundle - Assemble bundles for all the variants.
clean - Deletes the build directory.
compileDebugAndroidTestSources
compileDebugSources
compileDebugUnitTestSources
compileReleaseSources
compileReleaseUnitTestSources

Help tasks
----------
buildEnvironment - Displays all buildscript dependencies declared in project ':app'.
dependencies - Displays all dependencies declared in project ':app'.
dependencyInsight - Displays the insight into a specific dependency in project ':app'.
help - Displays a help message.
javaToolchains - Displays the detected java toolchains.
kotlinDslAccessorsReport - Prints the Kotlin code for accessing the currently available project extensions and conventions.
outgoingVariants - Displays the outgoing variants of project ':app'.
projects - Displays the sub-projects of project ':app'.
properties - Displays the properties of project ':app'.
resolvableConfigurations - Displays the configurations that can be resolved in project ':app'.
tasks - Displays the tasks runnable from project ':app'.

Install tasks
-------------
installDebug - Installs the Debug build.
installDebugAndroidTest - Installs the android (on device) tests for the Debug build.
uninstallAll - Uninstall all applications.
uninstallDebug - Uninstalls the Debug build.
uninstallDebugAndroidTest - Uninstalls the android (on device) tests for the Debug build.
uninstallRelease - Uninstalls the Release build.

Verification tasks
------------------
check - Runs all checks.
checkJetifier - Checks whether Jetifier is needed for the current project
checkKotlinGradlePluginConfigurationErrors - Checks that Kotlin Gradle Plugin hasn't reported project configuration errors, failing otherwise. This task always runs before compileKotlin* or similar tasks.
connectedAndroidTest - Installs and runs instrumentation tests for all flavors on connected devices.
connectedCheck - Runs all device checks on currently connected devices.
connectedDebugAndroidTest - Installs and runs the tests for debug on connected devices.
deviceAndroidTest - Installs and runs instrumentation tests using all Device Providers.
deviceCheck - Runs all device checks using Device Providers and Test Servers.
lint - Runs lint on the default variant.
lintDebug - Print text output from the corresponding lint report task
lintFix - Runs lint on the default variant and applies any safe suggestions to the source code.
lintRelease - Print text output from the corresponding lint report task
lintVitalRelease - Print text output from the corresponding lint report task
test - Run unit tests for all variants.
testDebugUnitTest - Run unit tests for the debug build.
testReleaseUnitTest - Run unit tests for the release build.
updateLintBaseline - Updates the lint baseline using the default variant.

Other tasks
-----------
analyzeDebugAndroidTestDependencies
analyzeDebugDependencies
analyzeDebugUnitTestDependencies
analyzeReleaseDependencies
analyzeReleaseUnitTestDependencies
asarToCompatSplitsForDebug
asarToCompatSplitsForRelease
assembleDebug - Assembles main output for variant debug
assembleDebugAndroidTest - Assembles main output for variant debugAndroidTest
assembleDebugUnitTest - Assembles main output for variant debugUnitTest
assembleRelease - Assembles main output for variant release
assembleReleaseUnitTest - Assembles main output for variant releaseUnitTest
buildDebugPreBundle
buildReleasePreBundle
bundleDebug - Assembles bundle for variant debug
bundleDebugClassesToCompileJar
bundleDebugClassesToRuntimeJar
bundleDebugResources
bundleRelease - Assembles bundle for variant release
bundleReleaseClassesToCompileJar
bundleReleaseClassesToRuntimeJar
bundleReleaseResources
checkDebugAarMetadata
checkDebugAndroidTestAarMetadata
checkDebugAndroidTestDuplicateClasses
checkDebugDuplicateClasses
checkDebugManifest
checkReleaseAarMetadata
checkReleaseDuplicateClasses
checkReleaseManifest
collectReleaseDependencies
compileDebugAndroidTestJavaWithJavac
compileDebugAndroidTestKotlin - Compiles the debugAndroidTest kotlin.
compileDebugAndroidTestShaders
compileDebugArtProfile
compileDebugJavaWithJavac
compileDebugKotlin - Compiles the debug kotlin.
compileDebugShaders
compileDebugUnitTestJavaWithJavac
compileDebugUnitTestKotlin - Compiles the debugUnitTest kotlin.
compileLint
compileLintChecks
compileReleaseArtProfile
compileReleaseJavaWithJavac
compileReleaseKotlin - Compiles the release kotlin.
compileReleaseShaders
compileReleaseUnitTestJavaWithJavac
compileReleaseUnitTestKotlin - Compiles the releaseUnitTest kotlin.
components - Displays the components produced by project ':app'. [deprecated]
compressDebugAndroidTestAssets
compressDebugAssets
compressReleaseAssets
configureReleaseDependencies
consumeConfigAttr
createDebugAndroidTestApkListingFileRedirect
createDebugApkListingFileRedirect
createDebugApksFromBundleListingFileRedirect
createDebugBundleListingFileRedirect
createDebugCompatibleScreenManifests
createMockableJar
createReleaseApkListingFileRedirect
createReleaseApksFromBundleListingFileRedirect
createReleaseBundleListingFileRedirect
createReleaseCompatibleScreenManifests
dependentComponents - Displays the dependent components of components in project ':app'. [deprecated]
desugarDebugAndroidTestFileDependencies
desugarDebugFileDependencies
desugarReleaseFileDependencies
dexBuilderDebug
dexBuilderDebugAndroidTest
dexBuilderRelease
extractApksForDebug
extractApksForRelease
extractApksFromBundleForDebug
extractApksFromBundleForRelease
extractDebugNativeDebugMetadata
extractDebugNativeSymbolTables
extractDeepLinksDebug
extractDeepLinksRelease
extractProguardFiles
extractReleaseNativeDebugMetadata
extractReleaseNativeSymbolTables
extractReleaseVersionControlInfo
generateDebugAndroidTestAssets
generateDebugAndroidTestLintModel
generateDebugAndroidTestResources
generateDebugAndroidTestResValues
generateDebugAndroidTestSources
generateDebugAssets
generateDebugFeatureMetadata
generateDebugFeatureTransitiveDeps
generateDebugLintModel
generateDebugLintReportModel
generateDebugManifestClass
generateDebugResources
generateDebugResValues
generateDebugSources
generateDebugUnitTestAssets
generateDebugUnitTestLintModel
generateDebugUnitTestResources
generateDebugUnitTestSources
generateReleaseAssets
generateReleaseFeatureMetadata
generateReleaseFeatureTransitiveDeps
generateReleaseLintModel
generateReleaseLintReportModel
generateReleaseLintVitalReportModel
generateReleaseManifestClass
generateReleaseResources
generateReleaseResValues
generateReleaseSources
generateReleaseUnitTestAssets
generateReleaseUnitTestLintModel
generateReleaseUnitTestResources
generateReleaseUnitTestSources
javaPreCompileDebug
javaPreCompileDebugAndroidTest
javaPreCompileDebugUnitTest
javaPreCompileRelease
javaPreCompileReleaseUnitTest
lintAnalyzeDebug - Run lint analysis on the debug component
lintAnalyzeDebugAndroidTest - Run lint analysis on the debugAndroidTest component
lintAnalyzeDebugUnitTest - Run lint analysis on the debugUnitTest component
lintAnalyzeRelease - Run lint analysis on the release component
lintAnalyzeReleaseUnitTest - Run lint analysis on the releaseUnitTest component
lintFixDebug - Fix lint on the debug variant
lintFixRelease - Fix lint on the release variant
lintReportDebug - Run lint on the debug variant
lintReportRelease - Run lint on the release variant
lintVitalAnalyzeRelease - Run lint analysis with only the fatal issues enabled 
lintVitalReportRelease - Run lint with only the fatal issues enabled on the release variant
makeApkFromBundleForDebug
makeApkFromBundleForRelease
mapDebugAndroidTestSourceSetPaths
mapDebugSourceSetPaths
mapReleaseSourceSetPaths
mergeDebugAndroidTestAssets
mergeDebugAndroidTestGeneratedProguardFiles
mergeDebugAndroidTestGlobalSynthetics
mergeDebugAndroidTestJavaResource
mergeDebugAndroidTestJniLibFolders
mergeDebugAndroidTestNativeLibs
mergeDebugAndroidTestResources
mergeDebugAndroidTestShaders
mergeDebugArtProfile
mergeDebugAssets
mergeDebugGeneratedProguardFiles
mergeDebugGlobalSynthetics
mergeDebugJavaResource
mergeDebugJniLibFolders
mergeDebugNativeDebugMetadata
mergeDebugNativeLibs
mergeDebugResources
mergeDebugShaders
mergeDexRelease
mergeExtDexDebug
mergeExtDexDebugAndroidTest
mergeExtDexRelease
mergeLibDexDebug
mergeLibDexDebugAndroidTest
mergeProjectDexDebug
mergeProjectDexDebugAndroidTest
mergeReleaseArtProfile
mergeReleaseAssets
mergeReleaseGeneratedProguardFiles
mergeReleaseGlobalSynthetics
mergeReleaseJavaResource
mergeReleaseJniLibFolders
mergeReleaseNativeDebugMetadata
mergeReleaseNativeLibs
mergeReleaseResources
mergeReleaseShaders
model - Displays the configuration model of project ':app'. [deprecated]
optimizeReleaseResources
packageDebug
packageDebugAndroidTest
packageDebugBundle
packageDebugResources
packageDebugUniversalApk
packageRelease
packageReleaseBundle
packageReleaseResources
packageReleaseUniversalApk
parseDebugIntegrityConfig
parseDebugLocalResources
parseReleaseIntegrityConfig
parseReleaseLocalResources
preBuild
preDebugAndroidTestBuild
preDebugBuild
preDebugUnitTestBuild
prepareLintJarForPublish
preReleaseBuild
preReleaseUnitTestBuild
processApplicationManifestDebugForBundle
processApplicationManifestReleaseForBundle
processDebugAndroidTestJavaRes
processDebugAndroidTestManifest
processDebugAndroidTestResources
processDebugJavaRes
processDebugMainManifest
processDebugManifest
processDebugManifestForInstantApp
processDebugManifestForPackage
processDebugResources
processDebugUnitTestJavaRes
processManifestDebugForFeature
processManifestReleaseForFeature
processReleaseJavaRes
processReleaseMainManifest
processReleaseManifest
processReleaseManifestForInstantApp
processReleaseManifestForPackage
processReleaseResources
processReleaseUnitTestJavaRes
produceDebugBundleIdeListingFile
produceReleaseBundleIdeListingFile
resolveConfigAttr
sdkReleaseDependencyData
signDebugBundle
signingConfigWriterDebug
signingConfigWriterDebugAndroidTest
signingConfigWriterRelease
signReleaseBundle
stripDebugAndroidTestDebugSymbols
stripDebugDebugSymbols
stripReleaseDebugSymbols
updateLintBaselineDebug - Update the lint baseline using the debug variant
updateLintBaselineRelease - Update the lint baseline using the release variant
validateSigningDebug
validateSigningDebugAndroidTest
writeDebugAndroidTestSigningConfigVersions
writeDebugApplicationId
writeDebugAppMetadata
writeDebugModuleMetadata
writeDebugSigningConfigVersions
writeReleaseApplicationId
writeReleaseAppMetadata
writeReleaseModuleMetadata
writeReleaseSigningConfigVersions
zipApksForDebug
zipApksForRelease

Rules
-----
Pattern: clean<TaskName>: Cleans the output files of a task.
Pattern: build<ConfigurationName>: Assembles the artifacts of a configuration.

BUILD SUCCESSFUL in 656ms
1 actionable task: 1 executed

exec
/bin/bash -lc 'rg -n "@Test" server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:14:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:23:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:36:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:51:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:64:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:20:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:29:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:37:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:45:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:51:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:57:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:63:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:69:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:46:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:55:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:60:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:71:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:76:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:84:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:92:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:100:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:105:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:114:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:121:    @Test
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:126:    @Test

exec
/bin/bash -lc 'rg -n "fun `" docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 exited 2 in 0ms:
/bin/bash: -c: line 1: unexpected EOF while looking for matching ``'

exec
/bin/bash -lc "rg -n \"const val|\\\"--sample-app-root\\\"|debugRuntimeClasspath|assembleDebug|runtime-classpath|axpEmitClasspath|dummy.txt|ok|cache|lib|real.jar|missing.aar|does.not.Exist|java.lang.StringBuilder|axp.render|line 2|manifest 누락|비-절대경로\" docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
5:**Goal:** sample-app 의 `activity_basic.xml` (ConstraintLayout / MaterialButton 등 custom view) 가 layoutlib host JVM 안에서 SUCCESS 로 렌더되도록, AAR `classes.jar` 를 `URLClassLoader` 로 적재해 `MinimalLayoutlibCallback.loadView` 를 통해 reflection-instantiate 한다.
8:- AAR/JAR resolution = sample-app `assembleDebug` 가 emit 한 manifest 파일 (`<sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt`).
9:- Worker 가 manifest 의 `.aar` 를 풀어 (`<sampleAppModuleRoot>/app/build/axp/aar-classes/<sha1>/<artifact>.jar`) JAR + sample-app `R.jar` 와 합쳐 `URLClassLoader(parent = layoutlib isolatedCL)` 구성.
10:- `MinimalLayoutlibCallback` 의 `loadView` / `findClass` 가 lazy provider 로 위 CL 사용. `hasAndroidXAppCompat = true` 로 BridgeInflater 의 AppCompat 자동 치환 활성. `InvocationTargetException` 은 cause 로 unwrap.
12:**Tech Stack:** Kotlin 1.9 / JDK 17 / Gradle 8 / JUnit Jupiter 5 / `java.net.URLClassLoader` / `java.util.zip.ZipFile` / `java.security.MessageDigest`. layoutlib 14.0.11 (이미 wired). AGP 8.x in `fixture/sample-app`.
23:- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt`
24:- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt`
30:package dev.axp.layoutlib.worker.classloader
33:    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 sampleAppModuleRoot-relative 경로. */
34:    const val MANIFEST_RELATIVE_PATH = "app/build/axp/runtime-classpath.txt"
36:    /** AarExtractor cacheRoot (= sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)) 의 base. */
37:    const val AAR_CACHE_BASE_RELATIVE_PATH = "app/build/axp"
39:    /** AarExtractor 의 stable 캐시 서브디렉토리 (cacheRoot 기준 상대). */
40:    const val AAR_CACHE_RELATIVE_DIR = "aar-classes"
43:    const val AAR_CLASSES_JAR_ENTRY = "classes.jar"
46:    const val AAR_EXTENSION = ".aar"
49:    const val JAR_EXTENSION = ".jar"
52:    const val EXTRACTED_JAR_SUFFIX = ".jar"
55:    const val TEMP_JAR_SUFFIX = ".jar.tmp"
58:    const val SHA1_DIGEST_NAME = "SHA-1"
64:    const val R_JAR_RELATIVE_PATH =
73:package dev.axp.layoutlib.worker.classloader
83:        assertEquals("app/build/axp/runtime-classpath.txt", ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
104:Run: `./server/gradlew -p server :layoutlib-worker:compileKotlin :layoutlib-worker:compileTestKotlin :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.ClassLoaderConstantsTest"`
110:git add server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt \
111:        server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt
112:git commit -m "feat(w3d3): ClassLoaderConstants — manifest/AAR cache/R.jar 경로 + 확장자 상수화 (...heredoc with Co-Authored-By...)"
121:- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifest.kt`
122:- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifestTest.kt`
127:package dev.axp.layoutlib.worker.classloader
143:        assertEquals(true, ex.message!!.contains("manifest 누락"))
144:        assertEquals(true, ex.message!!.contains("./gradlew :app:assembleDebug"))
159:        val realJar = Files.createFile(root.resolve("real.jar"))
162:        assertEquals(true, ex.message!!.contains("line 2"))
166:    fun `비-절대경로 — IllegalArgumentException`(@TempDir root: Path) {
171:        assertEquals(true, ex.message!!.contains("비-절대경로"))
189:Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.SampleAppClasspathManifestTest"`
195:package dev.axp.layoutlib.worker.classloader
197:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
198:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.JAR_EXTENSION
199:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
204: * <sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt 파일을 읽어
215:            "axp classpath manifest 누락: $mf — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 를 먼저 실행하세요"
224:            require(p.isAbsolute) { "manifest line ${idx + 1} 이 비-절대경로: '$line'" }
248:- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt`
249:- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt`
254:package dev.axp.layoutlib.worker.classloader
287:    fun `정상 추출 — classes_jar 가 cache 에 atomic 으로 생성`(@TempDir root: Path) {
288:        val cacheRoot = root.resolve("cache")
289:        val aar = makeAar(root, "lib", withClassesJar = true)
290:        val extracted = AarExtractor.extract(aar, cacheRoot)
295:        val tmpName = "lib${ClassLoaderConstants.TEMP_JAR_SUFFIX}"
300:    fun `cache hit — 두 번째 호출이 동일 path, 재추출 없음`(@TempDir root: Path) {
301:        val cacheRoot = root.resolve("cache")
302:        val aar = makeAar(root, "lib", withClassesJar = true)
303:        val first = AarExtractor.extract(aar, cacheRoot)!!
306:        val second = AarExtractor.extract(aar, cacheRoot)!!
313:        val cacheRoot = root.resolve("cache")
315:        assertNull(AarExtractor.extract(aar, cacheRoot))
320:        val cacheRoot = root.resolve("cache")
321:        val missing = root.resolve("missing.aar")
322:        assertThrows<IllegalArgumentException> { AarExtractor.extract(missing, cacheRoot) }
332:package dev.axp.layoutlib.worker.classloader
334:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
335:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
336:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
337:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTRACTED_JAR_SUFFIX
338:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.SHA1_DIGEST_NAME
339:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.TEMP_JAR_SUFFIX
350: * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출. mtime 기반 idempotent + atomic write.
352: * Cache 위치: `<cacheRoot>/aar-classes/<sha1(absPath)>/<artifactName>.jar`.
357:    fun extract(aarPath: Path, cacheRoot: Path): Path? {
361:        val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR).resolve(key)
391:Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.classloader.AarExtractorTest"`
401:- Create: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt`
402:- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt`
407:package dev.axp.layoutlib.worker.classloader
435:            zos.putNextEntry(ZipEntry("dummy.txt"))
436:            zos.write("ok".toByteArray())
455:        val aar = makeTinyAar(root, "lib")
494:package dev.axp.layoutlib.worker.classloader
496:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_BASE_RELATIVE_PATH
497:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
498:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
511:            val cacheRoot = sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)
517:                    AarExtractor.extract(entry, cacheRoot) ?: continue
526:                "sample-app R.jar 누락: $rJar — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 필요"
546:- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt`
547:- Test: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt` (기존 + 확장)
575:const val FIXTURE_MODULE_SUBPATH = "fixture/sample-app"
613:Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.FixtureDiscoveryTest"`
620:## Task 6: `MinimalLayoutlibCallback` 변경 (loadView + findClass + hasAndroidXAppCompat)
623:- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`
624:- Modify (small): `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt` — 기존 테스트 새 시그니처에 맞게 업데이트.
625:- Create: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackLoadViewTest.kt` (3 tests)
630:package dev.axp.layoutlib.worker.session
637:class MinimalLayoutlibCallbackLoadViewTest {
639:    private fun newCallback(provider: () -> ClassLoader): MinimalLayoutlibCallback =
640:        MinimalLayoutlibCallback(provider)
646:        val v = cb.loadView("java.lang.StringBuilder", arrayOf(CharSequence::class.java), arrayOf<Any>("hi"))
654:            cb.loadView("does.not.Exist", arrayOf(), arrayOf())
662:        val cls = cb.findClass("java.lang.StringBuilder")
674:- [ ] **Step 2: 기존 MinimalLayoutlibCallbackTest.kt — `MinimalLayoutlibCallback()` no-arg 호출 부분을 `MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }` 로 업데이트**
678:- [ ] **Step 3: MinimalLayoutlibCallback 수정**
681:package dev.axp.layoutlib.worker.session
686:import com.android.ide.common.rendering.api.LayoutlibCallback
704: * sample-app manifest 누락이 silent (W3D1 의 `tier3-values` 회귀 없음).
706:class MinimalLayoutlibCallback(
708:) : LayoutlibCallback() {
739:            // Q3: cause unwrap — layoutlib 의 BridgeInflater 가 InflateException 으로 wrap 함.
751:    override fun getAdapterBinding(cookie: Any?, attributes: Map<String, String>): AdapterBinding? = null
763:        private const val FIRST_ID = 0x7F0A_0000
764:        private const val APPLICATION_ID = "axp.render"
771:Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.session.MinimalLayoutlibCallback*"`
778:## Task 7: `LayoutlibRenderer` — sampleAppModuleRoot 인자 + lazy provider 배선
781:- Modify: `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt`
786:class LayoutlibRenderer(
806:- [ ] **Step 2: `renderViaLayoutlib` 안의 `MinimalLayoutlibCallback()` 호출을 `MinimalLayoutlibCallback { ensureSampleAppClassLoader() }` 로 교체**
808:해당 라인은 `LayoutlibRenderer.kt:170` 부근 (W2D7 시점 기준; 현재 정확한 라인은 grep `MinimalLayoutlibCallback()`).
812:Run: `./server/gradlew -p server :layoutlib-worker:compileKotlin :mcp-server:compileKotlin`
819:## Task 8: 모든 호출부 업데이트 — Main.kt CLI + SharedLayoutlibRenderer + 모든 테스트
825:- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt`
826:- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt`
827:- Modify: 모든 production `LayoutlibRenderer(...)` 호출하는 테스트 (W3D1: `LayoutlibRendererTier3MinimalTest`, integration tests).
828:- Modify: `LayoutlibRendererIntegrationTest.kt` — 곧 Task 10 에서 enable.
832:CliConstants.VALUE_BEARING_FLAGS 에 `"--sample-app-root"` 추가.
835:- [ ] **Step 2: Main.kt `chooseRenderer` — `FixtureDiscovery.locateModuleRoot(cliArgs.sampleAppRoot?.let { Path.of(it) })` 결과를 LayoutlibRenderer 에 주입**
839:- [ ] **Step 3: SharedLayoutlibRenderer.getOrCreate(distDir, fixtureRoot, sampleAppModuleRoot, fallback)**
841:W3D2 의 시그니처 그대로 확장. binding 은 `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 호출.
846:grep -rn "LayoutlibRenderer(" server/ --include="*.kt"
847:grep -rn "SharedLayoutlibRenderer.getOrCreate" server/ --include="*.kt"
852:- [ ] **Step 5: `LayoutlibRendererTier3MinimalTest.kt` — companion sharedRenderer 생성 시 sampleAppModuleRoot 추가**
857:Expected: BUILD SUCCESSFUL. activity_basic.xml 의 `LayoutlibRendererIntegrationTest` 는 아직 `@Disabled` 상태 — 회귀 없음.
863:## Task 9: Gradle `axpEmitClasspath` task + 첫 manifest 생성
875:cd fixture/sample-app && ./gradlew :app:assembleDebug 2>&1 | tail -10
876:ls -la app/build/axp/runtime-classpath.txt
877:head -5 app/build/axp/runtime-classpath.txt
878:wc -l app/build/axp/runtime-classpath.txt
889:## Task 10: `LayoutlibRendererIntegrationTest` enable + 첫 실측 + B2 contingency 결정
892:- Modify: `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt`
898:Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest" -PincludeTags=integration`
909:  - `LayoutlibRendererIntegrationTest` 의 layoutName 을 `"activity_basic_minimal.xml"` 로 변경.
919:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 전체 integration
921:./server/mcp-server/build/install/mcp-server/bin/mcp-server --smoke      # "ok"
939:- [ ] **Step 2: handoff 작성** — 다음 세션의 W3D4 후보 (app/library resource value loading, 또는 tier3-glyph).
955:- §3 데이터 흐름 → Task 7+8 의 LayoutlibRenderer 수정으로 wire.
965:**Type consistency**: `LayoutlibRenderer(distDir, fallback, fixtureRoot, sampleAppModuleRoot)` 시그니처가 모든 task 에서 일관. `MinimalLayoutlibCallback { provider }` lambda 도.

exec
/bin/bash -lc 'javap -classpath $(dirname $(readlink -f $(which javac)))/../lib/modules java.lang.StringBuilder | sed -n '"'1,80p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Compiled from "StringBuilder.java"
public final class java.lang.StringBuilder extends java.lang.AbstractStringBuilder implements java.io.Serializable, java.lang.Comparable<java.lang.StringBuilder>, java.lang.CharSequence {
  static final long serialVersionUID;
  public java.lang.StringBuilder();
  public java.lang.StringBuilder(int);
  public java.lang.StringBuilder(java.lang.String);
  public java.lang.StringBuilder(java.lang.CharSequence);
  public int compareTo(java.lang.StringBuilder);
  public java.lang.StringBuilder append(java.lang.Object);
  public java.lang.StringBuilder append(java.lang.String);
  public java.lang.StringBuilder append(java.lang.StringBuffer);
  public java.lang.StringBuilder append(java.lang.CharSequence);
  public java.lang.StringBuilder append(java.lang.CharSequence, int, int);
  public java.lang.StringBuilder append(char[]);
  public java.lang.StringBuilder append(char[], int, int);
  public java.lang.StringBuilder append(boolean);
  public java.lang.StringBuilder append(char);
  public java.lang.StringBuilder append(int);
  public java.lang.StringBuilder append(long);
  public java.lang.StringBuilder append(float);
  public java.lang.StringBuilder append(double);
  public java.lang.StringBuilder appendCodePoint(int);
  public java.lang.StringBuilder delete(int, int);
  public java.lang.StringBuilder deleteCharAt(int);
  public java.lang.StringBuilder replace(int, int, java.lang.String);
  public java.lang.StringBuilder insert(int, char[], int, int);
  public java.lang.StringBuilder insert(int, java.lang.Object);
  public java.lang.StringBuilder insert(int, java.lang.String);
  public java.lang.StringBuilder insert(int, char[]);
  public java.lang.StringBuilder insert(int, java.lang.CharSequence);
  public java.lang.StringBuilder insert(int, java.lang.CharSequence, int, int);
  public java.lang.StringBuilder insert(int, boolean);
  public java.lang.StringBuilder insert(int, char);
  public java.lang.StringBuilder insert(int, int);
  public java.lang.StringBuilder insert(int, long);
  public java.lang.StringBuilder insert(int, float);
  public java.lang.StringBuilder insert(int, double);
  public int indexOf(java.lang.String);
  public int indexOf(java.lang.String, int);
  public int lastIndexOf(java.lang.String);
  public int lastIndexOf(java.lang.String, int);
  public java.lang.StringBuilder reverse();
  public java.lang.String toString();
  public java.util.stream.IntStream codePoints();
  public java.util.stream.IntStream chars();
  public java.lang.AbstractStringBuilder reverse();
  public java.lang.AbstractStringBuilder insert(int, double);
  public java.lang.AbstractStringBuilder insert(int, float);
  public java.lang.AbstractStringBuilder insert(int, long);
  public java.lang.AbstractStringBuilder insert(int, int);
  public java.lang.AbstractStringBuilder insert(int, char);
  public java.lang.AbstractStringBuilder insert(int, boolean);
  public java.lang.AbstractStringBuilder insert(int, java.lang.CharSequence, int, int);
  public java.lang.AbstractStringBuilder insert(int, java.lang.CharSequence);
  public java.lang.AbstractStringBuilder insert(int, char[]);
  public java.lang.AbstractStringBuilder insert(int, java.lang.String);
  public java.lang.AbstractStringBuilder insert(int, java.lang.Object);
  public java.lang.AbstractStringBuilder insert(int, char[], int, int);
  public java.lang.String substring(int, int);
  public java.lang.CharSequence subSequence(int, int);
  public java.lang.String substring(int);
  public java.lang.AbstractStringBuilder replace(int, int, java.lang.String);
  public java.lang.AbstractStringBuilder deleteCharAt(int);
  public java.lang.AbstractStringBuilder appendCodePoint(int);
  public java.lang.AbstractStringBuilder delete(int, int);
  public java.lang.AbstractStringBuilder append(double);
  public java.lang.AbstractStringBuilder append(float);
  public java.lang.AbstractStringBuilder append(long);
  public java.lang.AbstractStringBuilder append(int);
  public java.lang.AbstractStringBuilder append(char);
  public java.lang.AbstractStringBuilder append(boolean);
  public java.lang.AbstractStringBuilder append(char[], int, int);
  public java.lang.AbstractStringBuilder append(char[]);
  public java.lang.AbstractStringBuilder append(java.lang.CharSequence, int, int);
  public java.lang.AbstractStringBuilder append(java.lang.CharSequence);
  public java.lang.AbstractStringBuilder append(java.lang.StringBuffer);
  public java.lang.AbstractStringBuilder append(java.lang.String);
  public java.lang.AbstractStringBuilder append(java.lang.Object);
  public void setCharAt(int, char);
  public void getChars(int, int, char[], int);

exec
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import org.junit.jupiter.api.Assertions.assertDoesNotThrow
     4	import org.junit.jupiter.api.Assertions.assertTrue
     5	import org.junit.jupiter.api.Test
     6	import org.junit.jupiter.api.assertThrows
     7	import java.nio.file.Paths
     8	
     9	class SharedRendererBindingTest {
    10	
    11	    private val distA = Paths.get("/dist/a")
    12	    private val distB = Paths.get("/dist/b")
    13	    private val fixA = Paths.get("/fix/a")
    14	    private val fixB = Paths.get("/fix/b")
    15	
    16	    @Test
    17	    fun `bound 가 null 이면 항상 통과 (첫 바인드)`() {
    18	        assertDoesNotThrow {
    19	            SharedRendererBinding.verify(bound = null, requested = distA to fixA)
    20	        }
    21	    }
    22	
    23	    @Test
    24	    fun `bound 와 requested 가 동일하면 통과`() {
    25	        val same = distA to fixA
    26	
    27	        assertDoesNotThrow {
    28	            SharedRendererBinding.verify(bound = same, requested = same)
    29	        }
    30	    }
    31	
    32	    @Test
    33	    fun `bound 와 requested 가 다르면 IllegalStateException`() {
    34	        val bound = distA to fixA
    35	        val requested = distB to fixB
    36	
    37	        val ex = assertThrows<IllegalStateException> {
    38	            SharedRendererBinding.verify(bound = bound, requested = requested)
    39	        }
    40	        assertTrue(
    41	            ex.message!!.contains("불일치") && ex.message!!.contains("bound"),
    42	            "메시지: ${ex.message}",
    43	        )
    44	    }
    45	}

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
/bin/bash -lc 'rg -n "fun [''^(]+'"\\(["'^)]*=|class [''^(]+'"\\(["'^)]*=" docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 exited 1 in 0ms:
exec
/bin/bash -lc './server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.FixtureDiscoveryTest"' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 4056ms:
Starting a Gradle Daemon, 3 incompatible Daemons could not be reused, use --status for details
> Task :buildSrc:checkKotlinGradlePluginConfigurationErrors
> Task :buildSrc:generateExternalPluginSpecBuilders UP-TO-DATE
> Task :buildSrc:extractPrecompiledScriptPluginPlugins UP-TO-DATE
> Task :buildSrc:compilePluginsBlocks UP-TO-DATE
> Task :buildSrc:generatePrecompiledScriptPluginAccessors UP-TO-DATE
> Task :buildSrc:generateScriptPluginAdapters UP-TO-DATE
> Task :buildSrc:compileKotlin UP-TO-DATE
> Task :buildSrc:compileJava NO-SOURCE
> Task :buildSrc:compileGroovy NO-SOURCE
> Task :buildSrc:pluginDescriptors UP-TO-DATE
> Task :buildSrc:processResources UP-TO-DATE
> Task :buildSrc:classes UP-TO-DATE
> Task :buildSrc:jar UP-TO-DATE
Path for java installation '/usr/lib/jvm/openjdk-17' (Common Linux Locations) does not contain a java executable
> Task :layoutlib-worker:checkKotlinGradlePluginConfigurationErrors
> Task :protocol:checkKotlinGradlePluginConfigurationErrors
> Task :protocol:compileKotlin UP-TO-DATE
> Task :protocol:compileJava NO-SOURCE
> Task :protocol:processResources NO-SOURCE
> Task :protocol:classes UP-TO-DATE
> Task :protocol:jar UP-TO-DATE
> Task :render-core:checkKotlinGradlePluginConfigurationErrors
> Task :render-core:compileKotlin UP-TO-DATE
> Task :render-core:compileJava NO-SOURCE
> Task :render-core:processResources NO-SOURCE
> Task :render-core:classes UP-TO-DATE
> Task :render-core:jar UP-TO-DATE
> Task :layoutlib-worker:compileKotlin UP-TO-DATE
> Task :layoutlib-worker:compileJava NO-SOURCE
> Task :layoutlib-worker:processResources NO-SOURCE
> Task :layoutlib-worker:classes UP-TO-DATE
> Task :layoutlib-worker:compileTestKotlin UP-TO-DATE
> Task :layoutlib-worker:compileTestJava NO-SOURCE
> Task :layoutlib-worker:processTestResources NO-SOURCE
> Task :layoutlib-worker:testClasses UP-TO-DATE

> Task :layoutlib-worker:test

FixtureDiscoveryTest > override 경로가 비존재면 IllegalArgumentException(Path) PASSED

FixtureDiscoveryTest > empty root candidate (CWD) 가 userDir 기준으로 FIXTURE_SUBPATH 해석(Path) PASSED

FixtureDiscoveryTest > override 가 존재 디렉토리이면 해당 경로 반환(Path) PASSED

FixtureDiscoveryTest > override null + 모든 candidate 실패 시 null 반환(Path) PASSED

FixtureDiscoveryTest > override null + candidate match 시 해당 경로 반환(Path) PASSED

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.7/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 3s
20 actionable tasks: 5 executed, 15 up-to-date

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
/bin/bash -lc 'nl -ba fixture/sample-app/app/src/main/res/layout/activity_basic.xml' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc 'nl -ba fixture/sample-app/app/src/main/res/layout/activity_minimal.xml' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	<?xml version="1.0" encoding="utf-8"?>
     2	<!--
     3	  W2D7-RENDERSESSION (08 §7.7.1 item 3b) canonical render target.
     4	  프레임워크 위젯만 (Material / AndroidX / ConstraintLayout 미사용) — LayoutlibCallback.loadView
     5	  가 android.widget.* 기본 경로만 타도록 최소화. activity_basic.xml 은 L3 (W3+) 타겟.
     6	-->
     7	<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
     8	    android:orientation="vertical"
     9	    android:layout_width="match_parent"
    10	    android:layout_height="match_parent"
    11	    android:background="#FFFFFFFF"
    12	    android:padding="32dp">
    13	
    14	    <TextView
    15	        android:id="@+id/title"
    16	        android:layout_width="wrap_content"
    17	        android:layout_height="wrap_content"
    18	        android:text="Hello layoutlib"
    19	        android:textColor="#FF000000"
    20	        android:textSize="28sp" />
    21	
    22	    <View
    23	        android:layout_width="match_parent"
    24	        android:layout_height="1dp"
    25	        android:layout_marginTop="16dp"
    26	        android:background="#FF888888" />
    27	</LinearLayout>

exec
/bin/bash -lc 'nl -ba fixture/sample-app/app/src/main/res/values/strings.xml' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc "rg -n \"SharedLayoutlibRenderer\\.getOrCreate\" server --glob '*.kt'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:48:            return SharedLayoutlibRenderer.getOrCreate(
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:31:        val renderer = SharedLayoutlibRenderer.getOrCreate(
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:29:        val r = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:38:        val r1 = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:40:        val r2 = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:50:        SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:55:            SharedLayoutlibRenderer.getOrCreate(dist, differentFixture, fallback = null)

exec
/bin/bash -lc 'nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import org.junit.jupiter.api.Assertions.assertNotNull
     4	import org.junit.jupiter.api.Assertions.assertSame
     5	import org.junit.jupiter.api.Assertions.assertTrue
     6	import org.junit.jupiter.api.Assumptions.assumeTrue
     7	import org.junit.jupiter.api.Tag
     8	import org.junit.jupiter.api.Test
     9	import org.junit.jupiter.api.assertThrows
    10	import java.nio.file.Path
    11	
    12	/**
    13	 * W3D2 integration cleanup — SharedLayoutlibRenderer 의 per-JVM-fork 싱글톤 동작 검증.
    14	 *
    15	 * 본 test 는 native lib 를 실제 로드한다 (LayoutlibRenderer 생성). 따라서 @Tag("integration").
    16	 * Gradle `forkEvery(1L)` (`server/layoutlib-worker/build.gradle.kts:60-65`) 덕에 이 test
    17	 * class 는 독립 JVM fork 에서 실행되고, 그 fork 안의 3 @Test 는 순차 실행되며 singleton
    18	 * 상태를 공유한다. 다른 integration test class (Tier3MinimalTest 등) 는 별도 JVM fork
    19	 * 에서 실행되므로 cross-class state leakage 없음.
    20	 */
    21	@Tag("integration")
    22	class SharedLayoutlibRendererIntegrationTest {
    23	
    24	    @Test
    25	    fun `첫 getOrCreate 시 LayoutlibRenderer 반환`() {
    26	        val dist = locateDistDir()
    27	        val fixture = locateFixtureRoot()
    28	
    29	        val r = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
    30	
    31	        assertNotNull(r)
    32	    }
    33	
    34	    @Test
    35	    fun `같은 args 로 재호출 시 동일 인스턴스 반환`() {
    36	        val dist = locateDistDir()
    37	        val fixture = locateFixtureRoot()
    38	        val r1 = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
    39	
    40	        val r2 = SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
    41	
    42	        assertSame(r1, r2, "같은 args 는 동일 인스턴스여야 함 (referential equality)")
    43	    }
    44	
    45	    @Test
    46	    fun `다른 args 로 호출 시 IllegalStateException`() {
    47	        val dist = locateDistDir()
    48	        val fixture = locateFixtureRoot()
    49	        // bound 상태 확보 — 첫 getOrCreate.
    50	        SharedLayoutlibRenderer.getOrCreate(dist, fixture, fallback = null)
    51	
    52	        val differentFixture = fixture.resolveSibling("different")
    53	
    54	        val ex = assertThrows<IllegalStateException> {
    55	            SharedLayoutlibRenderer.getOrCreate(dist, differentFixture, fallback = null)
    56	        }
    57	        assertTrue(
    58	            ex.message!!.contains("불일치"),
    59	            "메시지: ${ex.message}",
    60	        )
    61	    }
    62	
    63	    private fun locateDistDir(): Path {
    64	        val found = DistDiscovery.locate(null)
    65	        assumeTrue(found != null, "dist 없음 — W1D3-R2 다운로드를 먼저 수행")
    66	        return found!!.toAbsolutePath().normalize()
    67	    }
    68	
    69	    private fun locateFixtureRoot(): Path {
    70	        val found = FixtureDiscovery.locate(null)
    71	        assumeTrue(found != null, "fixture 없음 — fixture/sample-app 확인")
    72	        return found!!.toAbsolutePath().normalize()
    73	    }
    74	}

exec
/bin/bash -lc "rg -n \"includeTags|excludeTags|useJUnitPlatform|tasks\\.withType<Test>|named<Test>\\(\\\"test\\\"\\)\" -S server buildSrc ." in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 exited 2 in 0ms:
rg: buildSrc: No such file or directory (os error 2)
server/build.gradle.kts:20:        useJUnitPlatform()
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:37:// JUnit Platform tag filter — `-PincludeTags=integration` 로 통합 테스트만 실행.
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:40:tasks.withType<Test>().configureEach {
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:41:    val includeTagsProp = providers.gradleProperty("includeTags").orNull
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:42:    useJUnitPlatform {
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:43:        if (includeTagsProp.isNullOrBlank())
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:45:            excludeTags("integration")
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:49:            includeTags(*includeTagsProp.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
server/layoutlib-worker/build.gradle.kts:60:tasks.named<Test>("test") {
server/layoutlib-worker/build.gradle.kts:61:    val includeTagsProp = providers.gradleProperty("includeTags").orNull
server/layoutlib-worker/build.gradle.kts:62:    if (!includeTagsProp.isNullOrBlank() && includeTagsProp.contains("integration")) {
./docs/superpowers/specs/2026-04-24-w3d2-integration-cleanup-design.md:510:6. Full gate: `./server/gradlew -p server test`, `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`, `./server/gradlew -p server build`.
./docs/superpowers/specs/2026-04-24-w3d2-integration-cleanup-design.md:546:| LM-D | `LayoutlibRendererIntegrationTest` 를 `@Disabled` 로 바꾸면 `@Tag("integration")` 태그 gating 이 영향받지 않는지 | JUnit 5 는 `@Disabled` 가 tag 와 독립 — SKIPPED 로 기록되나 `-PincludeTags=integration` 결과 카운트에 반영됨. 기존 tier3-glyph 와 동일 패턴이라 안전. |
./docs/superpowers/specs/2026-04-24-w3d2-integration-cleanup-design.md:554:2. `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` 이후: **11 integration PASS + 2 SKIPPED** (tier3-glyph + LayoutlibRendererIntegrationTest@Disabled).
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:23:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:663:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:894:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1116:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "*SharedLayoutlibRendererIntegrationTest"
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1177:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "*SharedLayoutlibRendererIntegrationTest"
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1184:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1237:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "*LayoutlibRendererTier3MinimalTest"
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1244:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1330:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "*LayoutlibRendererIntegrationTest"
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1353:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1565:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration   # 11 + 2 SKIPPED 확인
./docs/superpowers/plans/2026-04-24-w3d2-integration-cleanup.md:1576:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration && \
./docs/plan/08-integration-reconciliation.md:475:- `axp.kotlin-common.gradle.kts` — `-PincludeTags=integration` 으로 통합 테스트만 실행, 기본은 제외.
./docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:21:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:1625:Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`
./docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md:1727:Run: `./server/gradlew -p server build && ./server/gradlew -p server test && ./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`
./docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:898:Run: `./server/gradlew -p server :layoutlib-worker:test --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest" -PincludeTags=integration`
./docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:919:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 전체 integration
./docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:889:Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --info 2>&1 | grep -E "TIER2 SKIP CAUSE"`
./docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:943:Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --info 2>&1 | tail -40`
./docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:1074:Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest"`
./docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:1194:Run: `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration --tests "dev.axp.layoutlib.worker.LayoutlibRendererIntegrationTest"`
./docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:1316:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md:1387:- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` → 5 PASS, 0 SKIP.
./docs/superpowers/plans/2026-04-23-w2d7-rendersession.md:86:- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` Tier1/Tier2 통과 확인 — F1 의 URL 제거가 Bridge 로딩을 깨지 않음을 확인.
./docs/superpowers/plans/2026-04-23-w2d7-rendersession.md:424:- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` → 기존 Tier3 (PNG magic only) 통과.
./docs/superpowers/plans/2026-04-23-w2d7-rendersession.md:442:- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` → **여전히 PASS** (같은 fixture 로 real render 산출 PNG 여도 magic header 는 유지).
./docs/superpowers/plans/2026-04-23-w2d7-rendersession.md:558:- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`
./docs/superpowers/plans/2026-04-23-w2d7-rendersession.md:635:- [ ] `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration` 6 PASS (Tier1 3 + Tier2 1 + 기존 Tier3 1 + 신규 Tier3 1).
./docs/W2D7-PAIR-REVIEW.md:113:### N4 — `forkEvery=1L` only on `-PincludeTags=integration`, not IDE runs
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:32: *   `./gradlew :layoutlib-worker:test -PincludeTags=integration` 로 명시 실행.
./docs/W3D1-PAIR-REVIEW.md:64:1. MF1 적용 → `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`
./docs/work_log/2026-04-22_w1d1-android-xml-previewer-foundation/handoff.md:122:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./server/layoutlib-worker/build.gradle.kts:60:tasks.named<Test>("test") {
./server/layoutlib-worker/build.gradle.kts:61:    val includeTagsProp = providers.gradleProperty("includeTags").orNull
./server/layoutlib-worker/build.gradle.kts:62:    if (!includeTagsProp.isNullOrBlank() && includeTagsProp.contains("integration")) {
./server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:37:// JUnit Platform tag filter — `-PincludeTags=integration` 로 통합 테스트만 실행.
./server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:40:tasks.withType<Test>().configureEach {
./server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:41:    val includeTagsProp = providers.gradleProperty("includeTags").orNull
./server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:42:    useJUnitPlatform {
./server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:43:        if (includeTagsProp.isNullOrBlank())
./server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:45:            excludeTags("integration")
./server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts:49:            includeTags(*includeTagsProp.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
./docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:1204:   475	- `axp.kotlin-common.gradle.kts` — `-PincludeTags=integration` 으로 통합 테스트만 실행, 기본은 제외.
./docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:2150:/bin/bash -lc "rg -n \"LayoutlibRendererIntegrationTest|LayoutlibRendererTier3MinimalTest|SharedLayoutlibRenderer|forkEvery|includeTags|MinimalLayoutlibCallback\" server/layoutlib-worker/src server/layoutlib-worker/build.gradle.kts server/build.gradle.kts server/gradle -g '*.kt' -g '*.kts'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
./docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:2153:server/layoutlib-worker/build.gradle.kts:61:    val includeTagsProp = providers.gradleProperty("includeTags").orNull
./docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:2154:server/layoutlib-worker/build.gradle.kts:62:    if (!includeTagsProp.isNullOrBlank() && includeTagsProp.contains("integration")) {
./docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:2163:server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:32: *   `./gradlew :layoutlib-worker:test -PincludeTags=integration` 로 명시 실행.
./docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:2277:    60	tasks.named<Test>("test") {
./docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:2278:    61	    val includeTagsProp = providers.gradleProperty("includeTags").orNull
./docs/work_log/2026-04-29_w3d3-l3-classloader/spec-pair-review-round1-codex.md:2279:    62	    if (!includeTagsProp.isNullOrBlank() && includeTagsProp.contains("integration")) {
./server/build.gradle.kts:20:        useJUnitPlatform()
./docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md:162:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration   # 5 PASS + 2 SKIPPED
./docs/work_log/2026-04-23_w3d1-resource-value-loader/session-log.md:166:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration   # 8 PASS + 2 SKIPPED
./docs/work_log/2026-04-23_w3d1-resource-value-loader/next-session-prompt.md:60:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 8 + 2 SKIPPED 확인
./docs/work_log/2026-04-24_w3d2-constructor-defaults-cli-path/session-log.md:30:| 4.5 integration | `:layoutlib-worker:test -PincludeTags=integration` | 8 PASS + 2 SKIPPED (tier3-glyph + LayoutlibRendererIntegrationTest) |
./docs/work_log/2026-04-24_w3d2-constructor-defaults-cli-path/session-log.md:35:| 5.5 integration | `:layoutlib-worker:test -PincludeTags=integration` | 8 PASS + 2 SKIPPED |
./docs/work_log/2026-04-23_w2d6-stdio-and-fatjar/handoff.md:71:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 5 PASS (Tier1+2+3)
./docs/work_log/2026-04-23_w1d2-d5-http-viewer/handoff.md:43:│   └── buildSrc/                       -PincludeTags=integration 분기
./docs/work_log/2026-04-23_w1d2-d5-http-viewer/handoff.md:70:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/work_log/2026-04-23_w1d2-d5-http-viewer/handoff.md:130:`-PincludeTags=integration` 로만 실행. 기본 `./gradlew test` 는 integration 제외. CI 에서 integration 별도 job 으로 분리 권장.
./docs/work_log/2026-04-23_w1d2-d5-http-viewer/session-log.md:41:- `server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts` — `-PincludeTags=integration` 분기 추가, 기본은 integration 제외
./docs/work_log/2026-04-23_w1d2-d5-http-viewer/session-log.md:80:### 왜 `-PincludeTags=integration` 분기를 buildSrc 레벨에 뒀나
./docs/work_log/2026-04-23_w1d2-d5-http-viewer/session-log.md:81:통합 테스트(`BridgeInitIntegrationTest`) 는 디스크 상의 155MB dist 가 필요하고 실행 시간이 kicked-off 서비스 + JVM 격리 ClassLoader 때문에 느림. 기본 `./gradlew test` 는 unit 만 실행되어야 IDE/CI 가 빠르게 피드백. convention plugin 에서 `-PincludeTags=integration` 이 있으면 include, 없으면 exclude "integration" 로 분기. 전 모듈에 일관 적용.
./docs/work_log/2026-04-23_w1d2-d5-http-viewer/session-log.md:99:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/work_log/2026-04-23_w2d7-rendersession/handoff.md:56:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 5 PASS + 2 SKIPPED
./docs/work_log/2026-04-23_w2d7-rendersession/next-session-prompt.md:16:  ./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
./docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:89:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration    # 11 + 2 SKIPPED
./docs/work_log/2026-04-24_w3d2-integration-cleanup/next-session-prompt.md:39:./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration    # 11 + 2 SKIPPED 확인
./server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/BridgeInitIntegrationTest.kt:32: *   `./gradlew :layoutlib-worker:test -PincludeTags=integration` 로 명시 실행.

exec
/bin/bash -lc "rg --files buildSrc server/buildSrc . | rg 'kotlin-common|\\.gradle\\.kts"'$'"'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
rg: buildSrc: No such file or directory (os error 2)
server/buildSrc/build.gradle.kts
server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts
server/buildSrc/settings.gradle.kts
./server/protocol/build.gradle.kts
./fixture/sample-app/settings.gradle.kts
./fixture/sample-app/build.gradle.kts
./server/layoutlib-worker/build.gradle.kts
./server/cli/build.gradle.kts
./fixture/sample-app/app/build.gradle.kts
./server/buildSrc/build.gradle.kts
./server/http-server/build.gradle.kts
./server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts
./server/buildSrc/settings.gradle.kts
./server/mcp-server/build.gradle.kts
./server/build.gradle.kts
./server/render-core/build.gradle.kts
./server/emulator-harness/build.gradle.kts
./server/settings.gradle.kts

exec
/bin/bash -lc "nl -ba server/buildSrc/src/main/kotlin/axp.kotlin-common.gradle.kts | sed -n '1,90p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	// axp.kotlin-common — 모든 Kotlin JVM 모듈의 공통 컨벤션
     2	//
     3	// 근거: 08 §5 item 5. 모든 서브모듈이 동일한 JVM target + kotlin stdlib + 테스트 러너.
     4	// 각 모듈에서 `plugins { id("axp.kotlin-common") }` 로 적용.
     5	
     6	plugins {
     7	    id("org.jetbrains.kotlin.jvm")
     8	}
     9	
    10	// 저장소는 settings.gradle.kts (dependencyResolutionManagement) 에서 중앙화.
    11	
    12	// version catalog 참조
    13	val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
    14	
    15	kotlin {
    16	    jvmToolchain(libs.findVersion("jvm").get().requiredVersion.toInt())
    17	}
    18	
    19	dependencies {
    20	    add("implementation", libs.findLibrary("kotlin-stdlib").get())
    21	    add("implementation", libs.findLibrary("slf4j-api").get())
    22	
    23	    // 테스트는 모든 모듈 공통
    24	    add("testImplementation", libs.findBundle("test-core").get())
    25	    add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:${libs.findVersion("junit-jupiter").get().requiredVersion}")
    26	}
    27	
    28	tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    29	    compilerOptions {
    30	        freeCompilerArgs.addAll(
    31	            "-Xjsr305=strict",
    32	            "-opt-in=kotlin.RequiresOptIn"
    33	        )
    34	    }
    35	}
    36	
    37	// JUnit Platform tag filter — `-PincludeTags=integration` 로 통합 테스트만 실행.
    38	// 기본 동작: @Tag("integration") 제외 → unit suite 가 native lib / 디스크 의존 없이 빠르게 실행.
    39	// W1D4 BridgeInitIntegrationTest 가 이 분기를 사용 (08 §7.5 / W1D4-R3).
    40	tasks.withType<Test>().configureEach {
    41	    val includeTagsProp = providers.gradleProperty("includeTags").orNull
    42	    useJUnitPlatform {
    43	        if (includeTagsProp.isNullOrBlank())
    44	        {
    45	            excludeTags("integration")
    46	        }
    47	        else
    48	        {
    49	            includeTags(*includeTagsProp.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
    50	        }
    51	    }
    52	}

exec
/bin/bash -lc "jar tf ~/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/*/layoutlib-api-31.13.2.jar | rg 'LayoutlibCallback.class'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
com/android/ide/common/rendering/api/LayoutlibCallback.class

exec
/bin/bash -lc "javap -classpath ~/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/*/layoutlib-api-31.13.2.jar com.android.ide.common.rendering.api.LayoutlibCallback | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Compiled from "LayoutlibCallback.java"
public abstract class com.android.ide.common.rendering.api.LayoutlibCallback implements com.android.ide.common.rendering.api.XmlParserFactory {
  public com.android.ide.common.rendering.api.LayoutlibCallback();
  public abstract java.lang.Object loadView(java.lang.String, java.lang.Class[], java.lang.Object[]) throws java.lang.Exception;
  public abstract com.android.ide.common.rendering.api.ResourceReference resolveResourceId(int);
  public abstract int getOrGenerateResourceId(com.android.ide.common.rendering.api.ResourceReference);
  public abstract com.android.ide.common.rendering.api.ILayoutPullParser getParser(com.android.ide.common.rendering.api.ResourceValue);
  public java.lang.Object getAdapterItemValue(com.android.ide.common.rendering.api.ResourceReference, java.lang.Object, com.android.ide.common.rendering.api.ResourceReference, int, int, int, int, com.android.ide.common.rendering.api.ResourceReference, com.android.ide.common.rendering.api.LayoutlibCallback$ViewAttribute, java.lang.Object);
  public abstract com.android.ide.common.rendering.api.AdapterBinding getAdapterBinding(java.lang.Object, java.util.Map<java.lang.String, java.lang.String>);
  public abstract com.android.ide.common.rendering.api.ActionBarCallback getActionBarCallback();
  public java.lang.Object loadClass(java.lang.String, java.lang.Class[], java.lang.Object[]) throws java.lang.ClassNotFoundException;
  public java.lang.String getApplicationId();
  public java.lang.String getResourcePackage();
  public java.lang.Class<?> findClass(java.lang.String) throws java.lang.ClassNotFoundException;
  public boolean isClassLoaded(java.lang.String);
  public com.android.ide.common.rendering.api.ResourceNamespace$Resolver getImplicitNamespaces();
  public boolean hasLegacyAppCompat();
  public boolean hasAndroidXAppCompat();
  public boolean isResourceNamespacingRequired();
  public boolean shouldUseCustomInflater();
  public void error(java.lang.String, java.lang.String...);
  public void error(java.lang.String, java.lang.Throwable);
  public void error(java.lang.Throwable);
  public void warn(java.lang.String, java.lang.Throwable);
  public void warn(java.lang.Throwable);
}

exec
/bin/bash -lc "rg -n \"CliArgs|CliConstants|DIST_DIR_FLAG|FIXTURE_ROOT_FLAG|VALUE_BEARING_FLAGS\" server/mcp-server/src/test/kotlin server/mcp-server/src/main/kotlin --glob '*.kt'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt:10:class CliArgsTest {
server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt:12:    private val valueBearing = CliConstants.VALUE_BEARING_FLAGS
server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt:16:        val parsed = CliArgs.parse(arrayOf("--dist-dir=/opt/x"), valueBearing)
server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt:24:        val parsed = CliArgs.parse(arrayOf("--stdio"), valueBearing)
server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt:33:            CliArgs.parse(arrayOf("--dist-dir="), valueBearing)
server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt:44:            CliArgs.parse(arrayOf("--dist-dir"), valueBearing)
server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt:54:        val parsed = CliArgs.parse(arrayOf("garbage", "--stdio"), valueBearing)
server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt:63:        val parsed = CliArgs.parse(arrayOf("--dist-dir=/a", "--dist-dir=/b"), valueBearing)
server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt:70:        val parsed = CliArgs.parse(arrayOf("---triple-dash", "--=value"), valueBearing)
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt:9:internal object CliConstants {
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt:10:    const val DIST_DIR_FLAG = "--dist-dir"
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt:11:    const val FIXTURE_ROOT_FLAG = "--fixture-root"
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt:17:     * MF-B2 — 값을 반드시 `=<value>` 로 받아야 하는 flag 집합. `CliArgs.parse` 가
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt:20:    val VALUE_BEARING_FLAGS: Set<String> = setOf(DIST_DIR_FLAG, FIXTURE_ROOT_FLAG)
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:37:    val parsed = CliArgs.parse(args, CliConstants.VALUE_BEARING_FLAGS)
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:54:private fun runStdioMode(parsed: CliArgs) {
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:84:private fun runHttpMode(parsed: CliArgs) {
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:115:private fun chooseRenderer(parsed: CliArgs): PngRenderer {
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:116:    val distOverride: Path? = parsed.valueOf(CliConstants.DIST_DIR_FLAG)?.let { Paths.get(it) }
server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:117:    val fixtureOverride: Path? = parsed.valueOf(CliConstants.FIXTURE_ROOT_FLAG)?.let { Paths.get(it) }
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt:9:internal data class CliArgs(
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt:17:        fun parse(args: Array<String>, valueBearingFlags: Set<String>): CliArgs {
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt:25:                val eqIdx = raw.indexOf(CliConstants.ARG_SEPARATOR)
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt:29:                        "axp: $raw 는 값을 받는 옵션입니다 — `$raw=<value>` 형식 필요 (${CliConstants.USAGE_LINE})"
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt:40:                        "axp: $key 값 누락 (${CliConstants.USAGE_LINE})"
server/mcp-server/src/main/kotlin/dev/axp/mcp/CliArgs.kt:45:            return CliArgs(flags, values)

exec
/bin/bash -lc 'nl -ba server/mcp-server/src/test/kotlin/dev/axp/mcp/CliArgsTest.kt' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.mcp
     2	
     3	import org.junit.jupiter.api.Assertions.assertEquals
     4	import org.junit.jupiter.api.Assertions.assertFalse
     5	import org.junit.jupiter.api.Assertions.assertNull
     6	import org.junit.jupiter.api.Assertions.assertTrue
     7	import org.junit.jupiter.api.Test
     8	import org.junit.jupiter.api.assertThrows
     9	
    10	class CliArgsTest {
    11	
    12	    private val valueBearing = CliConstants.VALUE_BEARING_FLAGS
    13	
    14	    @Test
    15	    fun `--key=value 형식 파싱`() {
    16	        val parsed = CliArgs.parse(arrayOf("--dist-dir=/opt/x"), valueBearing)
    17	
    18	        assertEquals("/opt/x", parsed.valueOf("--dist-dir"))
    19	        assertFalse(parsed.hasFlag("--dist-dir"))
    20	    }
    21	
    22	    @Test
    23	    fun `비 value-bearing --flag 단독 은 flags set 에 등록`() {
    24	        val parsed = CliArgs.parse(arrayOf("--stdio"), valueBearing)
    25	
    26	        assertTrue(parsed.hasFlag("--stdio"))
    27	        assertNull(parsed.valueOf("--stdio"))
    28	    }
    29	
    30	    @Test
    31	    fun `--key= (빈 value) 는 IllegalArgumentException`() {
    32	        val ex = assertThrows<IllegalArgumentException> {
    33	            CliArgs.parse(arrayOf("--dist-dir="), valueBearing)
    34	        }
    35	        assertTrue(
    36	            ex.message!!.contains("--dist-dir") && ex.message!!.contains("값 누락"),
    37	            "에러 메시지: ${ex.message}",
    38	        )
    39	    }
    40	
    41	    @Test
    42	    fun `MF-B2 value-bearing --dist-dir bare (= 없이) 는 IllegalArgumentException`() {
    43	        val ex = assertThrows<IllegalArgumentException> {
    44	            CliArgs.parse(arrayOf("--dist-dir"), valueBearing)
    45	        }
    46	        assertTrue(
    47	            ex.message!!.contains("--dist-dir") && ex.message!!.contains("값을 받는 옵션"),
    48	            "에러 메시지: ${ex.message}",
    49	        )
    50	    }
    51	
    52	    @Test
    53	    fun `비-플래그 위치 arg (-- prefix 없음) 는 warn-only 로 무시`() {
    54	        val parsed = CliArgs.parse(arrayOf("garbage", "--stdio"), valueBearing)
    55	
    56	        assertTrue(parsed.hasFlag("--stdio"))
    57	        assertFalse(parsed.hasFlag("garbage"))
    58	        assertNull(parsed.valueOf("garbage"))
    59	    }
    60	
    61	    @Test
    62	    fun `중복 key 는 last-wins`() {
    63	        val parsed = CliArgs.parse(arrayOf("--dist-dir=/a", "--dist-dir=/b"), valueBearing)
    64	
    65	        assertEquals("/b", parsed.valueOf("--dist-dir"))
    66	    }
    67	
    68	    @Test
    69	    fun `MF-F2 edge cases — ---triple-dash 는 flags 에 그대로 저장, --=value 는 빈 key + value 저장`() {
    70	        val parsed = CliArgs.parse(arrayOf("---triple-dash", "--=value"), valueBearing)
    71	
    72	        assertTrue(parsed.hasFlag("---triple-dash"))
    73	        assertEquals("value", parsed.valueOf("--"))
    74	    }
    75	}

exec
/bin/bash -lc "rg -n \"chooseRenderer|--fixture-root|--sample-app-root|PlaceholderPngRenderer|mcp-server --smoke|Main\" server/mcp-server/src/test/kotlin server/http-server/src/test/kotlin server/cli/src/test/kotlin --glob '*.kt'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/http-server/src/test/kotlin/dev/axp/http/PlaceholderPngRendererTest.kt:10:class PlaceholderPngRendererTest {
server/http-server/src/test/kotlin/dev/axp/http/PlaceholderPngRendererTest.kt:12:    private val renderer = PlaceholderPngRenderer()

exec
/bin/bash -lc 'nl -ba server/mcp-server/build.gradle.kts' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	// :mcp-server — Claude Code MCP stdio 엔트리.
     2	// 이 모듈이 전체 애플리케이션 합본 (fat jar) 을 생성.
     3	
     4	plugins {
     5	    id("axp.kotlin-common")
     6	    id("org.jetbrains.kotlin.plugin.serialization")
     7	    application
     8	}
     9	
    10	dependencies {
    11	    implementation(project(":protocol"))
    12	    implementation(project(":render-core"))
    13	    implementation(project(":emulator-harness"))
    14	    implementation(project(":http-server"))
    15	    implementation(project(":layoutlib-worker"))  // W2D6-FATJAR: LayoutlibRenderer wired into Main
    16	    implementation(project(":cli"))
    17	
    18	    implementation(libs.kotlinx.coroutines.core)
    19	    implementation(libs.kotlinx.serialization.json)
    20	    implementation(libs.logback.classic)
    21	}
    22	
    23	application {
    24	    mainClass.set("dev.axp.mcp.MainKt")
    25	    applicationName = "axp-server"
    26	}
    27	
    28	// 단일 실행 JAR 을 server/build/libs/axp-server.jar 로 출력 (plugin.json 의 mcpServers 경로와 일치)
    29	tasks.register<Jar>("fatJar") {
    30	    group = "distribution"
    31	    description = "Assembles a single executable JAR for plugin distribution"
    32	    archiveBaseName.set("axp-server")
    33	    archiveClassifier.set("")
    34	    manifest { attributes["Main-Class"] = "dev.axp.mcp.MainKt" }
    35	    from(sourceSets.main.get().output)
    36	    dependsOn(configurations.runtimeClasspath)
    37	    from({
    38	        configurations.runtimeClasspath.get()
    39	            .filter { it.name.endsWith("jar") }
    40	            .map { zipTree(it) }
    41	    })
    42	    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    43	    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    44	}

codex
I’m at the synthesis point. Before finalizing, I’m doing one more pass over the exact task references and the local verification commands I ran so the verdict separates hard blockers from follow-up polish.
exec
/bin/bash -lc "sed -n '1,220p' /home/bh-mark-dev-desktop/.codex/plugins/cache/openai-curated/superpowers/6807e4de/skills/verification-before-completion/SKILL.md" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
---
name: verification-before-completion
description: Use when about to claim work is complete, fixed, or passing, before committing or creating PRs - requires running verification commands and confirming output before making any success claims; evidence before assertions always
---

# Verification Before Completion

## Overview

Claiming work is complete without verification is dishonesty, not efficiency.

**Core principle:** Evidence before claims, always.

**Violating the letter of this rule is violating the spirit of this rule.**

## The Iron Law

```
NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE
```

If you haven't run the verification command in this message, you cannot claim it passes.

## The Gate Function

```
BEFORE claiming any status or expressing satisfaction:

1. IDENTIFY: What command proves this claim?
2. RUN: Execute the FULL command (fresh, complete)
3. READ: Full output, check exit code, count failures
4. VERIFY: Does output confirm the claim?
   - If NO: State actual status with evidence
   - If YES: State claim WITH evidence
5. ONLY THEN: Make the claim

Skip any step = lying, not verifying
```

## Common Failures

| Claim | Requires | Not Sufficient |
|-------|----------|----------------|
| Tests pass | Test command output: 0 failures | Previous run, "should pass" |
| Linter clean | Linter output: 0 errors | Partial check, extrapolation |
| Build succeeds | Build command: exit 0 | Linter passing, logs look good |
| Bug fixed | Test original symptom: passes | Code changed, assumed fixed |
| Regression test works | Red-green cycle verified | Test passes once |
| Agent completed | VCS diff shows changes | Agent reports "success" |
| Requirements met | Line-by-line checklist | Tests passing |

## Red Flags - STOP

- Using "should", "probably", "seems to"
- Expressing satisfaction before verification ("Great!", "Perfect!", "Done!", etc.)
- About to commit/push/PR without verification
- Trusting agent success reports
- Relying on partial verification
- Thinking "just this once"
- Tired and wanting work over
- **ANY wording implying success without having run verification**

## Rationalization Prevention

| Excuse | Reality |
|--------|---------|
| "Should work now" | RUN the verification |
| "I'm confident" | Confidence ≠ evidence |
| "Just this once" | No exceptions |
| "Linter passed" | Linter ≠ compiler |
| "Agent said success" | Verify independently |
| "I'm tired" | Exhaustion ≠ excuse |
| "Partial check is enough" | Partial proves nothing |
| "Different words so rule doesn't apply" | Spirit over letter |

## Key Patterns

**Tests:**
```
✅ [Run test command] [See: 34/34 pass] "All tests pass"
❌ "Should pass now" / "Looks correct"
```

**Regression tests (TDD Red-Green):**
```
✅ Write → Run (pass) → Revert fix → Run (MUST FAIL) → Restore → Run (pass)
❌ "I've written a regression test" (without red-green verification)
```

**Build:**
```
✅ [Run build] [See: exit 0] "Build passes"
❌ "Linter passed" (linter doesn't check compilation)
```

**Requirements:**
```
✅ Re-read plan → Create checklist → Verify each → Report gaps or completion
❌ "Tests pass, phase complete"
```

**Agent delegation:**
```
✅ Agent reports success → Check VCS diff → Verify changes → Report actual state
❌ Trust agent report
```

## Why This Matters

From 24 failure memories:
- your human partner said "I don't believe you" - trust broken
- Undefined functions shipped - would crash
- Missing requirements shipped - incomplete features
- Time wasted on false completion → redirect → rework
- Violates: "Honesty is a core value. If you lie, you'll be replaced."

## When To Apply

**ALWAYS before:**
- ANY variation of success/completion claims
- ANY expression of satisfaction
- ANY positive statement about work state
- Committing, PR creation, task completion
- Moving to next task
- Delegating to agents

**Rule applies to:**
- Exact phrases
- Paraphrases and synonyms
- Implications of success
- ANY communication suggesting completion/correctness

## The Bottom Line

**No shortcuts for verification.**

Run the command. Read the output. THEN claim the result.

This is non-negotiable.

codex
**A. Plan-Level Blockers**

1. **Task 7 + Task 8 are not safe as separate top-level tasks.** The plan requires subagent-driven task-by-task execution ([plan:3](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:3), [plan:973](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:973)), but Task 7 intentionally leaves compilation broken ([plan:810](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:810), [plan:815](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:815)). This conflicts with the repo rule that complete units must be green and WIP must not be committed/left as a completed unit ([CLAUDE:67](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/CLAUDE.md:67), [CLAUDE:86](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/CLAUDE.md:86)).  
   **Fix:** merge Tasks 7 and 8 into one task: “LayoutlibRenderer constructor + lazy CL + all callers,” with one commit after full build.

2. **Task 6 misses current no-arg `MinimalLayoutlibCallback()` callers.** Task 6 only instructs updating `MinimalLayoutlibCallbackTest.kt` ([plan:624](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:624), [plan:674](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:674)), but `SessionParamsFactoryTest` also calls the no-arg constructor at [SessionParamsFactoryTest:42](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:42) and [SessionParamsFactoryTest:65](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:65). After the constructor changes to require a provider ([plan:706](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:706)), `compileTestKotlin` will fail.  
   **Fix:** Task 6 must grep/update all `MinimalLayoutlibCallback(` call sites, including `SessionParamsFactoryTest`.

3. **Task 6 `findClass` test is wrong for `StringBuilder`.** `java.lang.StringBuilder(CharSequence)` is public, so the `loadView` constructor test is valid. But `StringBuilder` is bootstrap-loaded, so `cls.classLoader` is `null`, not `ClassLoader.getSystemClassLoader()`. The assertion at [plan:662](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:662)-[plan:663](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:663) will fail.  
   **Fix:** assert returned class equality, or use a custom test `ClassLoader` that records `loadClass` delegation.

4. **Task 4’s R.jar-missing test does not exercise R.jar-missing behavior.** The test writes an empty manifest ([plan:468](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:468)-[plan:473](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:473)), so `SampleAppClasspathManifest.read()` throws on blank input before R.jar is checked ([plan:510](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:510), [plan:524](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:524)). Also the planned R.jar check uses `require`, so the real exception is `IllegalArgumentException`, not `IllegalStateException` ([plan:525](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:525)).  
   **Fix:** write a valid non-empty jar manifest, omit only R.jar, assert `IllegalArgumentException` and message contains `R.jar`.

5. **Shared renderer binding invariant is under-specified.** Current binding keys only `(distDir, fixtureRoot)` ([SharedLayoutlibRenderer:32](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:32), [SharedRendererBinding:19](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt:19)). Task 8 adds `sampleAppModuleRoot` to renderer creation ([plan:839](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:839)-[plan:841](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:841)) but gives no exact code for extending the binding key/tests.  
   **Fix:** add a one-class-per-file binding args value class/data class including all three paths, update `SharedRendererBindingTest`.

6. **Some exact snippets violate the supplied style rules if pasted unchanged.** Example: Task 5 uses one-line `if/else` without braces ([plan:601](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:601)-[plan:604](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:604)); Task 9’s Gradle snippet hardcodes task/config/path strings in logic ([spec:209](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:209)-[spec:226](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:226)).  
   **Fix:** either rewrite snippets to conform, or explicitly state snippets are semantic and implementation must apply AGENTS/CLAUDE style.

**B. Strongly Recommended**

- Add Task 8 tests for `--sample-app-root`. Existing CLI parsing is generic `valueOf(...)` ([Main:116](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:116)-[Main:117](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:117)); the plan’s `cliArgs.sampleAppRoot` is inconsistent with the current API ([plan:833](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:833)-[plan:835](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:835)).
- Add tests for `InvocationTargetException` cause unwrap and constructor-signature mismatch. The spec calls out constructor mismatch ([spec:621](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:621)-[spec:623](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:623)), but the plan does not include it.
- Make Task 10 branch C a stop-and-report gate with concrete triage commands. Current text says analyze and add a task ([plan:912](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:912)-[plan:914](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:914)), which is too open-ended for autonomous execution.

**C. Specific Concerns**

1. **Task 7 + 8 atomicity:** not acceptable as separate top-level tasks under “fresh subagent per task.” Merge them. The prior W3D2 landmine was specifically about constructor-change atomicity ([session-log:48](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:48)-[session-log:49](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:49)).

2. **Task 5 `locateInternalGeneric`:** behavior is preserved if existing `locateInternal` delegates with `FIXTURE_SUBPATH`. Current behavior is override first, then candidate order, then first existing directory ([FixtureDiscovery:35](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt:35)-[FixtureDiscovery:62](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt:62)); existing five tests cover those paths ([FixtureDiscoveryTest:14](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:14)-[FixtureDiscoveryTest:75](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:75)). I ran the existing `FixtureDiscoveryTest`; it passed.

3. **Task 6 `StringBuilder`:** public constructor exists; `loadView` case is fine. The `findClass` classloader assertion is not.

4. **Task 9 Gradle wiring:** likely OK in this project. I ran `./gradlew :app:tasks --all` in `fixture/sample-app`; `assembleDebug` is registered and reachable. The project applies Android/Kotlin plugins before the snippet location ([sample build:1](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app/app/build.gradle.kts:1)-[sample build:4](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app/app/build.gradle.kts:4)).

5. **Task 10 branch C:** not enough recovery scaffolding. Branch B has concrete fallback layout steps ([plan:906](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:906)-[plan:910](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:910)); branch C does not.

6. **Test cardinality:** plan math is wrong. New unit tests are 3 + 5 + 4 + 3 + 2 + 4 = **21**, not 15. Task 6 says “3 tests” ([plan:625](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:625)) but includes four test methods ([plan:642](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:642), [plan:650](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:650), [plan:658](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:658), [plan:666](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:666)). Existing `MinimalLayoutlibCallbackTest` has 8 tests and only changes constructor calls ([MinimalLayoutlibCallbackTest:20](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:20)-[MinimalLayoutlibCallbackTest:72](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:72)).

7. **Constructor callers:** direct `LayoutlibRenderer(` grep finds the class declaration plus two construction sites: `Main.kt` and `SharedLayoutlibRenderer.kt` ([Main:130](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:130), [SharedLayoutlibRenderer:44](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:44)). `SharedLayoutlibRenderer.getOrCreate` has additional callers, including `SharedLayoutlibRendererIntegrationTest` ([SharedIntegration:29](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:29)-[SharedIntegration:55](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:55)). Task 8’s grep command would catch them, but the explicit file list should name this test.

**D. Independent Issues**

- Empty manifest test matches planned production behavior: `error(...)` throws `IllegalStateException` ([plan:217](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:217)-[plan:220](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:220)).
- `locateSampleAppModuleRoot()` should probably fail loudly, not skip. The fixture project is checked into this repo; `assumeTrue(found != null)` would hide a repo/layout regression ([spec:585](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:585)-[spec:588](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:588)).
- Task 3 spec says corrupted AAR/IOException coverage ([spec:616](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:616)-[spec:617](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:617)), but plan tests missing-file `IllegalArgumentException` instead ([plan:318](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:318)-[plan:322](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:322)).

**E. Verdict**

**NO_GO.** The architecture remains coherent, but the execution plan is not ready: Task 7/8 atomicity conflicts with the stated subagent model, Task 6 has a real compile gap and a failing `StringBuilder` assertion, and Task 4’s R.jar test does not test the behavior it claims. Merge 7+8, fix the Task 4/6 tests, extend shared binding to include `sampleAppModuleRoot`, and correct the test-count/branch-C scaffolding before implementation.
2026-04-29T05:35:26.362046Z ERROR codex_core::session: failed to record rollout items: thread 019dd7b5-4bdd-77c1-ad93-74385e322bc1 not found
tokens used
140,049
**A. Plan-Level Blockers**

1. **Task 7 + Task 8 are not safe as separate top-level tasks.** The plan requires subagent-driven task-by-task execution ([plan:3](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:3), [plan:973](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:973)), but Task 7 intentionally leaves compilation broken ([plan:810](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:810), [plan:815](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:815)). This conflicts with the repo rule that complete units must be green and WIP must not be committed/left as a completed unit ([CLAUDE:67](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/CLAUDE.md:67), [CLAUDE:86](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/CLAUDE.md:86)).  
   **Fix:** merge Tasks 7 and 8 into one task: “LayoutlibRenderer constructor + lazy CL + all callers,” with one commit after full build.

2. **Task 6 misses current no-arg `MinimalLayoutlibCallback()` callers.** Task 6 only instructs updating `MinimalLayoutlibCallbackTest.kt` ([plan:624](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:624), [plan:674](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:674)), but `SessionParamsFactoryTest` also calls the no-arg constructor at [SessionParamsFactoryTest:42](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:42) and [SessionParamsFactoryTest:65](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:65). After the constructor changes to require a provider ([plan:706](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:706)), `compileTestKotlin` will fail.  
   **Fix:** Task 6 must grep/update all `MinimalLayoutlibCallback(` call sites, including `SessionParamsFactoryTest`.

3. **Task 6 `findClass` test is wrong for `StringBuilder`.** `java.lang.StringBuilder(CharSequence)` is public, so the `loadView` constructor test is valid. But `StringBuilder` is bootstrap-loaded, so `cls.classLoader` is `null`, not `ClassLoader.getSystemClassLoader()`. The assertion at [plan:662](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:662)-[plan:663](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:663) will fail.  
   **Fix:** assert returned class equality, or use a custom test `ClassLoader` that records `loadClass` delegation.

4. **Task 4’s R.jar-missing test does not exercise R.jar-missing behavior.** The test writes an empty manifest ([plan:468](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:468)-[plan:473](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:473)), so `SampleAppClasspathManifest.read()` throws on blank input before R.jar is checked ([plan:510](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:510), [plan:524](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:524)). Also the planned R.jar check uses `require`, so the real exception is `IllegalArgumentException`, not `IllegalStateException` ([plan:525](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:525)).  
   **Fix:** write a valid non-empty jar manifest, omit only R.jar, assert `IllegalArgumentException` and message contains `R.jar`.

5. **Shared renderer binding invariant is under-specified.** Current binding keys only `(distDir, fixtureRoot)` ([SharedLayoutlibRenderer:32](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:32), [SharedRendererBinding:19](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBinding.kt:19)). Task 8 adds `sampleAppModuleRoot` to renderer creation ([plan:839](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:839)-[plan:841](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:841)) but gives no exact code for extending the binding key/tests.  
   **Fix:** add a one-class-per-file binding args value class/data class including all three paths, update `SharedRendererBindingTest`.

6. **Some exact snippets violate the supplied style rules if pasted unchanged.** Example: Task 5 uses one-line `if/else` without braces ([plan:601](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:601)-[plan:604](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:604)); Task 9’s Gradle snippet hardcodes task/config/path strings in logic ([spec:209](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:209)-[spec:226](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:226)).  
   **Fix:** either rewrite snippets to conform, or explicitly state snippets are semantic and implementation must apply AGENTS/CLAUDE style.

**B. Strongly Recommended**

- Add Task 8 tests for `--sample-app-root`. Existing CLI parsing is generic `valueOf(...)` ([Main:116](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:116)-[Main:117](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:117)); the plan’s `cliArgs.sampleAppRoot` is inconsistent with the current API ([plan:833](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:833)-[plan:835](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:835)).
- Add tests for `InvocationTargetException` cause unwrap and constructor-signature mismatch. The spec calls out constructor mismatch ([spec:621](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:621)-[spec:623](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:623)), but the plan does not include it.
- Make Task 10 branch C a stop-and-report gate with concrete triage commands. Current text says analyze and add a task ([plan:912](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:912)-[plan:914](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:914)), which is too open-ended for autonomous execution.

**C. Specific Concerns**

1. **Task 7 + 8 atomicity:** not acceptable as separate top-level tasks under “fresh subagent per task.” Merge them. The prior W3D2 landmine was specifically about constructor-change atomicity ([session-log:48](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:48)-[session-log:49](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/work_log/2026-04-24_w3d2-integration-cleanup/session-log.md:49)).

2. **Task 5 `locateInternalGeneric`:** behavior is preserved if existing `locateInternal` delegates with `FIXTURE_SUBPATH`. Current behavior is override first, then candidate order, then first existing directory ([FixtureDiscovery:35](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt:35)-[FixtureDiscovery:62](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt:62)); existing five tests cover those paths ([FixtureDiscoveryTest:14](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:14)-[FixtureDiscoveryTest:75](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/FixtureDiscoveryTest.kt:75)). I ran the existing `FixtureDiscoveryTest`; it passed.

3. **Task 6 `StringBuilder`:** public constructor exists; `loadView` case is fine. The `findClass` classloader assertion is not.

4. **Task 9 Gradle wiring:** likely OK in this project. I ran `./gradlew :app:tasks --all` in `fixture/sample-app`; `assembleDebug` is registered and reachable. The project applies Android/Kotlin plugins before the snippet location ([sample build:1](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app/app/build.gradle.kts:1)-[sample build:4](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/fixture/sample-app/app/build.gradle.kts:4)).

5. **Task 10 branch C:** not enough recovery scaffolding. Branch B has concrete fallback layout steps ([plan:906](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:906)-[plan:910](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:910)); branch C does not.

6. **Test cardinality:** plan math is wrong. New unit tests are 3 + 5 + 4 + 3 + 2 + 4 = **21**, not 15. Task 6 says “3 tests” ([plan:625](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:625)) but includes four test methods ([plan:642](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:642), [plan:650](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:650), [plan:658](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:658), [plan:666](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:666)). Existing `MinimalLayoutlibCallbackTest` has 8 tests and only changes constructor calls ([MinimalLayoutlibCallbackTest:20](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:20)-[MinimalLayoutlibCallbackTest:72](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:72)).

7. **Constructor callers:** direct `LayoutlibRenderer(` grep finds the class declaration plus two construction sites: `Main.kt` and `SharedLayoutlibRenderer.kt` ([Main:130](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt:130), [SharedLayoutlibRenderer:44](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt:44)). `SharedLayoutlibRenderer.getOrCreate` has additional callers, including `SharedLayoutlibRendererIntegrationTest` ([SharedIntegration:29](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:29)-[SharedIntegration:55](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:55)). Task 8’s grep command would catch them, but the explicit file list should name this test.

**D. Independent Issues**

- Empty manifest test matches planned production behavior: `error(...)` throws `IllegalStateException` ([plan:217](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:217)-[plan:220](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:220)).
- `locateSampleAppModuleRoot()` should probably fail loudly, not skip. The fixture project is checked into this repo; `assumeTrue(found != null)` would hide a repo/layout regression ([spec:585](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:585)-[spec:588](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:588)).
- Task 3 spec says corrupted AAR/IOException coverage ([spec:616](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:616)-[spec:617](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md:617)), but plan tests missing-file `IllegalArgumentException` instead ([plan:318](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:318)-[plan:322](/home/bh-mark-dev-desktop/workspace/android_xml_previewer/docs/superpowers/plans/2026-04-29-w3d3-l3-classloader.md:322)).

**E. Verdict**

**NO_GO.** The architecture remains coherent, but the execution plan is not ready: Task 7/8 atomicity conflicts with the stated subagent model, Task 6 has a real compile gap and a failing `StringBuilder` assertion, and Task 4’s R.jar test does not test the behavior it claims. Merge 7+8, fix the Task 4/6 tests, extend shared binding to include `sampleAppModuleRoot`, and correct the test-count/branch-C scaffolding before implementation.
