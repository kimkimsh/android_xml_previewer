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
session id: 019dd80a-3ba8-7ca3-a0ed-be666b7b7653
--------
user
# Codex Pair Review — W3D3-α Bytecode Rewriting + R.jar Seeding Spec

You are reviewing a Kotlin/JVM design spec that adds ASM-based bytecode rewriting and R.jar id seeding to unblock the W3D3 milestone. The W3D3 base infrastructure (Tasks 1-8) was completed and pushed; this α delta is focused on resolving two specific blockers found in branch (C) of the prior session.

**Configuration**: Use the highest available GPT model (currently GPT-5.5/5.4) with reasoning effort `xhigh`. No time limit.

## What you have

Working directory: `/home/bh-mark-dev-desktop/workspace/android_xml_previewer`

1. **THE SPEC UNDER REVIEW**: `docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md`
2. **Branch (C) diagnosis** that motivated α: `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md`
3. **Predecessor spec** (W3D3 round 2, complete): `docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md`
4. **Predecessor session log**: `docs/work_log/2026-04-29_w3d3-l3-classloader/session-log.md`
5. **Existing infrastructure** (Tasks 1-8 already merged):
   - `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/{ClassLoaderConstants,SampleAppClasspathManifest,AarExtractor,SampleAppClassLoader}.kt`
   - `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/{LayoutlibRenderer,FixtureDiscovery}.kt`
   - `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`
   - `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/{RendererArgs,SharedLayoutlibRenderer,SharedRendererBinding}.kt`
   - `fixture/sample-app/app/build.gradle.kts` (axpEmitClasspath task)
6. CLAUDE.md (project + global).

## What's already settled (don't re-litigate)

- URLClassLoader (not DexClassLoader) is correct.
- Lazy CL build pattern.
- Pair-review process for design tradeoff analysis.
- All round 2 fixes from the predecessor spec.

## What you are reviewing

The α spec proposes ~270 production LOC + ~480 test LOC to:
1. Add ASM 9.7 dependency to `:layoutlib-worker`.
2. Introduce `RewriteRules` with empirically-derived NAME_MAP of 25 entries (all `_Original_*` classes in layoutlib 14.0.11).
3. Introduce `AndroidClassRewriter.rewrite(bytes): bytes` using ASM `ClassRemapper` + `ClassWriter`.
4. Modify `AarExtractor` to rewrite each `.class` entry in classes.jar using AndroidClassRewriter, producing rewritten classes.jar in the cache.
5. Introduce `RJarTypeMapping` (R$<simpleName> → ResourceType enum).
6. Introduce `RJarSymbolSeeder` that walks all R$* class entries in the sample-app R.jar, reflection-enumerates static int fields, registers as ResourceReference + id with the callback.
7. Modify `MinimalLayoutlibCallback` to accept an `initializer` lambda that runs at construction time, with a `registerSymbol` callback for the seeder.
8. Modify `LayoutlibRenderer.renderViaLayoutlib` to wire RJarSymbolSeeder into the callback initializer.
9. Re-enable `LayoutlibRendererIntegrationTest` (remove @Disabled).

## Specific concerns to address

Answer each with concrete evidence (file:line citations + shell command output where relevant).

### A. Plan-level blockers (NO_GO conditions)

For each: spec section, exact failure mode, fix.

### B. Strongly recommended (GO_WITH_FOLLOWUPS)

### C. Spec questions (Q1-Q3)

The spec lists three explicit questions. Answer each with stance + reasoning:

**Q1 (NAME_MAP completeness)**: Is the empirically-derived 25-entry NAME_MAP sufficient? Could there be *indirect* references (e.g., type signatures in field descriptors) that need additional rewriting beyond what `_Original_*` names show? Investigate by:
- Comparing with Paparazzi's `JvmCallableTransformer` if you can find it in any cache (`find ~/.gradle/caches -name "paparazzi*.jar" 2>/dev/null` — likely not present, OK to note absence).
- Checking if the layoutlib JAR has any class with `_Original_` references in its bytecode (`unzip -p layoutlib-14.0.11.jar android/os/_Original_Build.class | strings | grep -c Original`) to confirm self-consistency.

**Q2 (R.jar id collision with callback nextId)**: Will R.jar ids collide with `nextId` starting at `0x7F0A_0000`? Investigate:
- Use `unzip -p` + `javap -p` on `androidx/constraintlayout/widget/R$attr.class` to dump int field values. Confirm whether they fall within or outside `0x7F0A_xxxx` range. The spec's `advanceNextIdAbove` mitigation should handle, but verify.
- Look at the `register` callback's behavior in the spec (§4.6) — if a duplicate ResourceReference is registered, what happens?

**Q3 (`R$styleable` int[] skip)**: Does layoutlib's `BridgeContext.obtainStyledAttributes(set, attrs)` actually look up styleable arrays by attr-id (array contents) or by the array reference itself? Verify by examining BridgeContext if available, or by reasoning from the JVM call semantics — the array is a parameter, so layoutlib iterates over its int contents.

### D. Independent issues

Especially:
- **Performance**: AAR rewrite is per-AAR-on-first-extract (mtime cached). For ~50 AARs in the sample-app classpath, what's the expected wall-clock cost of ASM transformation? Run the existing AarExtractor on one AAR and benchmark.
- **CLAUDE.md compliance**: spec snippets — any magic strings in logic that should be in `ClassLoaderConstants`? `CLASS_FILE_SUFFIX = ".class"` is mentioned but not yet added — verify.
- **Race conditions**: The MinimalLayoutlibCallback's `init` block runs the seeder. If layoutlib calls `getOrGenerateResourceId` from another thread *before* init completes, is that possible? Check the LayoutlibCallback contract.
- **com.android.resources.ResourceType availability**: This enum is referenced by spec §4.4. Is it actually on the worker's classpath? Check by `grep -rn "ResourceType" server/layoutlib-worker/`.
- **com.android.ide.common.rendering.api.ResourceNamespace.fromPackageName**: Is this method available in layoutlib-api 31.13.2? Verify via javap on the api JAR.

### E. Verdict
NO_GO / GO_WITH_FOLLOWUPS / GO + paragraph rationale.

## Output format

Markdown with sections A, B, C (per Q), D, E. File:line citations. Run shell commands freely.

Save your final report to `/tmp/w3d3-alpha-codex-spec-review.md`.
codex
I’ll use the Superpowers workflow guidance first, then review the spec against the actual branch files and run the requested local probes. I’ll keep the report evidence-heavy and write it to `/tmp/w3d3-alpha-codex-spec-review.md`.
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
Using `superpowers:using-superpowers` for workflow discipline. I’m also checking the review-oriented guidance so the final report leads with actionable blockers and evidence.
exec
/bin/bash -lc "sed -n '1,260p' /home/bh-mark-dev-desktop/.codex/plugins/cache/openai-curated/superpowers/6807e4de/skills/requesting-code-review/SKILL.md" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
---
name: requesting-code-review
description: Use when completing tasks, implementing major features, or before merging to verify work meets requirements
---

# Requesting Code Review

Dispatch superpowers:code-reviewer subagent to catch issues before they cascade. The reviewer gets precisely crafted context for evaluation — never your session's history. This keeps the reviewer focused on the work product, not your thought process, and preserves your own context for continued work.

**Core principle:** Review early, review often.

## When to Request Review

**Mandatory:**
- After each task in subagent-driven development
- After completing major feature
- Before merge to main

**Optional but valuable:**
- When stuck (fresh perspective)
- Before refactoring (baseline check)
- After fixing complex bug

## How to Request

**1. Get git SHAs:**
```bash
BASE_SHA=$(git rev-parse HEAD~1)  # or origin/main
HEAD_SHA=$(git rev-parse HEAD)
```

**2. Dispatch code-reviewer subagent:**

Use Task tool with superpowers:code-reviewer type, fill template at `code-reviewer.md`

**Placeholders:**
- `{WHAT_WAS_IMPLEMENTED}` - What you just built
- `{PLAN_OR_REQUIREMENTS}` - What it should do
- `{BASE_SHA}` - Starting commit
- `{HEAD_SHA}` - Ending commit
- `{DESCRIPTION}` - Brief summary

**3. Act on feedback:**
- Fix Critical issues immediately
- Fix Important issues before proceeding
- Note Minor issues for later
- Push back if reviewer is wrong (with reasoning)

## Example

```
[Just completed Task 2: Add verification function]

You: Let me request code review before proceeding.

BASE_SHA=$(git log --oneline | grep "Task 1" | head -1 | awk '{print $1}')
HEAD_SHA=$(git rev-parse HEAD)

[Dispatch superpowers:code-reviewer subagent]
  WHAT_WAS_IMPLEMENTED: Verification and repair functions for conversation index
  PLAN_OR_REQUIREMENTS: Task 2 from docs/superpowers/plans/deployment-plan.md
  BASE_SHA: a7981ec
  HEAD_SHA: 3df7661
  DESCRIPTION: Added verifyIndex() and repairIndex() with 4 issue types

[Subagent returns]:
  Strengths: Clean architecture, real tests
  Issues:
    Important: Missing progress indicators
    Minor: Magic number (100) for reporting interval
  Assessment: Ready to proceed

You: [Fix progress indicators]
[Continue to Task 3]
```

## Integration with Workflows

**Subagent-Driven Development:**
- Review after EACH task
- Catch issues before they compound
- Fix before moving to next task

**Executing Plans:**
- Review after each batch (3 tasks)
- Get feedback, apply, continue

**Ad-Hoc Development:**
- Review before merge
- Review when stuck

## Red Flags

**Never:**
- Skip review because "it's simple"
- Ignore Critical issues
- Proceed with unfixed Important issues
- Argue with valid technical feedback

**If reviewer wrong:**
- Push back with technical reasoning
- Show code/tests that prove it works
- Request clarification

See template at: requesting-code-review/code-reviewer.md

  → Read project instructions and target specs/logs
  • Inspect current implementation and dependencies
  • Run requested bytecode/classpath probes
  • Benchmark one AAR extraction/rewrite path
  • Draft and save review report
  • Verify report file and summarize verdict
codex
I’m starting with the local instructions and the three design documents, then I’ll cross-check the specific proposed APIs against the current Gradle dependencies and bytecode artifacts.
exec
/bin/bash -lc "pwd && rg --files -g 'CLAUDE.md' -g 'AGENTS.md' -g 'build.gradle.kts' -g 'settings.gradle.kts'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
/home/bh-mark-dev-desktop/workspace/android_xml_previewer
fixture/sample-app/build.gradle.kts
fixture/sample-app/app/build.gradle.kts
CLAUDE.md
server/settings.gradle.kts
fixture/sample-app/settings.gradle.kts
server/protocol/build.gradle.kts
server/http-server/build.gradle.kts
server/layoutlib-worker/build.gradle.kts
server/render-core/build.gradle.kts
server/cli/build.gradle.kts
server/build.gradle.kts
server/mcp-server/build.gradle.kts
server/emulator-harness/build.gradle.kts
server/buildSrc/build.gradle.kts
server/buildSrc/settings.gradle.kts

exec
/bin/bash -lc "nl -ba docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	# W3D3-α — Bytecode Rewriting + R.jar id Seeding 설계 스펙
     2	
     3	> **Date**: 2026-04-29
     4	> **Scope**: W3D3 의 branch (C) blocker 두 건을 ASM bytecode rewriting + R.jar id seeding 으로 해결.
     5	> **Carried-from**: `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md` (옵션 α 선택).
     6	> **Predecessor specs**: `2026-04-29-w3d3-l3-classloader-design.md` (round 2 close 상태).
     7	
     8	---
     9	
    10	## 1. 요구사항
    11	
    12	`activity_basic.xml` 의 `LayoutlibRendererIntegrationTest` (현재 `@Disabled`) 가 T1 gate 를 통과해야 함:
    13	- `Result.Status.SUCCESS`
    14	- PNG bytes > 1000
    15	- PNG magic 헤더
    16	
    17	본 W3D3-α 에서 다음 두 blocker 를 해결:
    18	- **Blocker #1**: AAR 의 `android.os.Build.VERSION.SDK_INT` 참조가 `_Original_Build` 부재로 ClassNotFoundException → ASM rewriting 으로 해결.
    19	- **Blocker #2**: R.jar 의 real id ↔ callback generated id 불일치 → R.jar id seeding 으로 해결.
    20	
    21	In-scope: 새 컴포넌트 2개 (`AndroidClassRewriter` + `RJarSymbolSeeder`) + 통합 + 통합 테스트 enable.
    22	Out-of-scope: AVD-L3, tier3-glyph, 다중 fixture, fidelity (visual pixel) 검증.
    23	
    24	---
    25	
    26	## 2. 아키텍처
    27	
    28	### 2.1 데이터 흐름 변경
    29	
    30	```
    31	[기존 W3D3 round 2 흐름 — Tasks 1-8]
    32	SampleAppClassLoader.build(sampleAppModuleRoot, parent=isolatedCL)
    33	  ├─ manifest 파싱
    34	  ├─ AarExtractor.extract(aar)  ← classes.jar 그대로 추출
    35	  └─ URLClassLoader (rewritten 없음 + R.jar URL 만 추가)
    36	
    37	[α 추가 흐름]
    38	AarExtractor.extract(aar)
    39	  └─ ZipFile 의 classes.jar entry 를 stream-read
    40	     └─ stream 안의 각 .class 를 AndroidClassRewriter.rewrite(bytes)  ← 신규
    41	     └─ rewritten classes.jar 를 cache 에 atomic 저장
    42	
    43	MinimalLayoutlibCallback.<init>
    44	  └─ rJarLoader.let { RJarSymbolSeeder.seed(it, this::registerSymbol) }  ← 신규
    45	     └─ 본 callback 의 byRef/byId 가 R.jar 의 실 id 와 일치되도록 사전-populate
    46	```
    47	
    48	### 2.2 클래스로더 계층은 W3D3 round 2 그대로
    49	
    50	```
    51	system CL          ← Kotlin stdlib, layoutlib-api, kxml2
    52	   ↑ parent
    53	isolated CL        ← layoutlib JAR (android.view.* / _Original_Build*)
    54	   ↑ parent
    55	SampleAppClassLoader  ← rewritten AAR classes.jar + R.jar
    56	```
    57	
    58	α 의 변화는 **classes.jar 내용물의 transform** 만이며, 계층 구조는 무변경.
    59	
    60	### 2.3 NAME_MAP — empirical 도출 (25 entries)
    61	
    62	`unzip -l layoutlib-14.0.11.jar | grep "_Original_"` 결과 그대로 매핑:
    63	
    64	| 원래 이름 | rewrite 대상 |
    65	|-----------|--------------|
    66	| `android/os/Build` | `android/os/_Original_Build` |
    67	| `android/os/Build$Partition` | `android/os/_Original_Build$Partition` |
    68	| `android/os/Build$VERSION` | `android/os/_Original_Build$VERSION` |
    69	| `android/os/Build$VERSION_CODES` | `android/os/_Original_Build$VERSION_CODES` |
    70	| `android/os/ServiceManager` | `android/os/_Original_ServiceManager` |
    71	| `android/os/ServiceManager$ServiceNotFoundException` | `android/os/_Original_ServiceManager$ServiceNotFoundException` |
    72	| `android/os/ServiceManager$Stats` | `android/os/_Original_ServiceManager$Stats` |
    73	| `android/view/SurfaceView` | `android/view/_Original_SurfaceView` |
    74	| `android/view/SurfaceView$1` | `android/view/_Original_SurfaceView$1` |
    75	| `android/view/SurfaceView$SurfaceLifecycleStrategy` | `android/view/_Original_SurfaceView$SurfaceLifecycleStrategy` |
    76	| `android/view/SurfaceView$SurfaceViewPositionUpdateListener` | `android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener` |
    77	| `android/view/SurfaceView$SyncBufferTransactionCallback` | `android/view/_Original_SurfaceView$SyncBufferTransactionCallback` |
    78	| `android/view/WindowManagerImpl` | `android/view/_Original_WindowManagerImpl` |
    79	| `android/view/WindowManagerImpl$1` | `android/view/_Original_WindowManagerImpl$1` |
    80	| `android/view/WindowManagerImpl$OnFpsCallbackListenerProxy` | `android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy` |
    81	| `android/view/textservice/TextServicesManager` | `android/view/textservice/_Original_TextServicesManager` |
    82	| `android/webkit/WebView` | `android/webkit/_Original_WebView` |
    83	| `android/webkit/WebView$FindListener` | `android/webkit/_Original_WebView$FindListener` |
    84	| `android/webkit/WebView$FindListenerDistributor` | `android/webkit/_Original_WebView$FindListenerDistributor` |
    85	| `android/webkit/WebView$HitTestResult` | `android/webkit/_Original_WebView$HitTestResult` |
    86	| `android/webkit/WebView$PictureListener` | `android/webkit/_Original_WebView$PictureListener` |
    87	| `android/webkit/WebView$PrivateAccess` | `android/webkit/_Original_WebView$PrivateAccess` |
    88	| `android/webkit/WebView$RendererPriority` | `android/webkit/_Original_WebView$RendererPriority` |
    89	| `android/webkit/WebView$VisualStateCallback` | `android/webkit/_Original_WebView$VisualStateCallback` |
    90	| `android/webkit/WebView$WebViewTransport` | `android/webkit/_Original_WebView$WebViewTransport` |
    91	
    92	이 25 매핑이 layoutlib 14.0.11 의 `_Original_*` 전체이며, *그 이상의 매핑이 필요할 일은 없다* (layoutlib 자체가 더 매핑하지 않으므로). 향후 layoutlib 버전 변경 시 본 매핑 재추출.
    93	
    94	---
    95	
    96	## 3. 컴포넌트 분해
    97	
    98	| # | 파일 | 책임 | LOC |
    99	|---|------|------|-----|
   100	| α1 | `server/layoutlib-worker/build.gradle.kts` (수정) | `org.ow2.asm:asm:9.7` + `asm-commons:9.7` 의존 추가 | +2 |
   101	| α2 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RewriteRules.kt` (신규) | `NAME_MAP` 25 entries + `Remapper` 인스턴스 | 60 |
   102	| α3 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AndroidClassRewriter.kt` (신규) | `rewrite(classBytes: ByteArray): ByteArray` — `ClassReader`/`ClassRemapper`/`ClassWriter` | 30 |
   103	| α4 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt` (수정) | classes.jar entries 를 stream 으로 읽고 각 .class 를 `AndroidClassRewriter.rewrite` 후 새 jar 에 write | +50 |
   104	| α5 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt` (신규) | `seed(rJarPath, registerSymbol: (ResourceReference, Int) -> Unit)` — R.jar 의 모든 R$* 클래스를 walk + 각 정적 int 필드를 enumerate | 90 |
   105	| α6 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarTypeMapping.kt` (신규) | R$type 의 simple name (e.g., "attr", "style", "styleable") → `com.android.resources.ResourceType` enum | 50 |
   106	| α7 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` (수정) | 생성자에 `rJarSeeder: (MinimalLayoutlibCallback) -> Unit` 추가, init 에서 invoke. byRef/byId 에 사전 populate 가능하도록 `registerSymbol` private method 노출 (internal) | +25 |
   107	| α8 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` (수정) | `ensureSampleAppClassLoader` 가 빌드한 후 그 classloader 의 R.jar URL 로부터 seeder 함수 합성 → callback 에 주입 | +15 |
   108	| α9 | `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` (수정) | `@Disabled` 제거 (round 2 의 SUCCESS assertion 등은 그대로) | -5 |
   109	| **Tests** | | | |
   110	| TC-α1 | `RewriteRulesTest` | NAME_MAP 25 entries 무결성 + remapper 가 unknown 이름 그대로 반환 | 60 |
   111	| TC-α2 | `AndroidClassRewriterTest` | (a) Build 참조하는 합성 .class → _Original_Build 로 rewritten / (b) Build 무관 .class → no change / (c) field type signature 도 rewritten | 130 |
   112	| TC-α3 | `AarExtractorRewriteTest` | extract 결과 classes.jar 안의 각 .class 가 NAME_MAP 적용됐는지 Round-trip ZipFile 로 확인 | 100 |
   113	| TC-α4 | `RJarSymbolSeederTest` | (a) 합성 R.jar 의 모든 R$* 클래스 walk / (b) static int 필드 register 됨 / (c) int[] 필드 (R$styleable) skip / (d) namespace + type 정확 추출 | 130 |
   114	| TC-α5 | `RJarTypeMappingTest` | "attr"/"style"/"layout"/... 모두 매핑 + 알 수 없는 type → null | 60 |
   115	
   116	production ~270 LOC + tests ~480 LOC.
   117	
   118	---
   119	
   120	## 4. 상세 스펙
   121	
   122	### 4.1 RewriteRules.kt
   123	
   124	```kotlin
   125	package dev.axp.layoutlib.worker.classloader
   126	
   127	import org.objectweb.asm.commons.Remapper
   128	
   129	/**
   130	 * layoutlib 14.0.11 의 자체 build pipeline 이 _Original_ prefix 를 붙인 25 클래스들의
   131	 * 매핑. host-JVM 환경에서 AAR bytecode 의 `android/os/Build` 등 reference 를 layoutlib 의
   132	 * 실재 `android/os/_Original_Build` 로 rewrite 하기 위함.
   133	 *
   134	 * Empirical 도출: `unzip -l layoutlib-14.0.11.jar | grep _Original_`. layoutlib 버전 변경
   135	 * 시 본 NAME_MAP 재추출 (자동화 task `dumpLayoutlibOriginals` 향후 carry).
   136	 */
   137	internal object RewriteRules {
   138	
   139	    val NAME_MAP: Map<String, String> = mapOf(
   140	        "android/os/Build" to "android/os/_Original_Build",
   141	        "android/os/Build\$Partition" to "android/os/_Original_Build\$Partition",
   142	        "android/os/Build\$VERSION" to "android/os/_Original_Build\$VERSION",
   143	        "android/os/Build\$VERSION_CODES" to "android/os/_Original_Build\$VERSION_CODES",
   144	        "android/os/ServiceManager" to "android/os/_Original_ServiceManager",
   145	        "android/os/ServiceManager\$ServiceNotFoundException" to "android/os/_Original_ServiceManager\$ServiceNotFoundException",
   146	        "android/os/ServiceManager\$Stats" to "android/os/_Original_ServiceManager\$Stats",
   147	        "android/view/SurfaceView" to "android/view/_Original_SurfaceView",
   148	        "android/view/SurfaceView\$1" to "android/view/_Original_SurfaceView\$1",
   149	        "android/view/SurfaceView\$SurfaceLifecycleStrategy" to "android/view/_Original_SurfaceView\$SurfaceLifecycleStrategy",
   150	        "android/view/SurfaceView\$SurfaceViewPositionUpdateListener" to "android/view/_Original_SurfaceView\$SurfaceViewPositionUpdateListener",
   151	        "android/view/SurfaceView\$SyncBufferTransactionCallback" to "android/view/_Original_SurfaceView\$SyncBufferTransactionCallback",
   152	        "android/view/WindowManagerImpl" to "android/view/_Original_WindowManagerImpl",
   153	        "android/view/WindowManagerImpl\$1" to "android/view/_Original_WindowManagerImpl\$1",
   154	        "android/view/WindowManagerImpl\$OnFpsCallbackListenerProxy" to "android/view/_Original_WindowManagerImpl\$OnFpsCallbackListenerProxy",
   155	        "android/view/textservice/TextServicesManager" to "android/view/textservice/_Original_TextServicesManager",
   156	        "android/webkit/WebView" to "android/webkit/_Original_WebView",
   157	        "android/webkit/WebView\$FindListener" to "android/webkit/_Original_WebView\$FindListener",
   158	        "android/webkit/WebView\$FindListenerDistributor" to "android/webkit/_Original_WebView\$FindListenerDistributor",
   159	        "android/webkit/WebView\$HitTestResult" to "android/webkit/_Original_WebView\$HitTestResult",
   160	        "android/webkit/WebView\$PictureListener" to "android/webkit/_Original_WebView\$PictureListener",
   161	        "android/webkit/WebView\$PrivateAccess" to "android/webkit/_Original_WebView\$PrivateAccess",
   162	        "android/webkit/WebView\$RendererPriority" to "android/webkit/_Original_WebView\$RendererPriority",
   163	        "android/webkit/WebView\$VisualStateCallback" to "android/webkit/_Original_WebView\$VisualStateCallback",
   164	        "android/webkit/WebView\$WebViewTransport" to "android/webkit/_Original_WebView\$WebViewTransport",
   165	    )
   166	
   167	    val REMAPPER: Remapper = object : Remapper() {
   168	        override fun map(internalName: String): String =
   169	            NAME_MAP[internalName] ?: internalName
   170	    }
   171	}
   172	```
   173	
   174	### 4.2 AndroidClassRewriter.kt
   175	
   176	```kotlin
   177	package dev.axp.layoutlib.worker.classloader
   178	
   179	import org.objectweb.asm.ClassReader
   180	import org.objectweb.asm.ClassWriter
   181	import org.objectweb.asm.commons.ClassRemapper
   182	
   183	/**
   184	 * 단일 .class bytecode 를 받아 RewriteRules.REMAPPER 로 type reference 를 rewrite.
   185	 * ClassWriter 의 COMPUTE_MAXS 만 사용 (frame 재계산 불요 — type rename 만 수행).
   186	 */
   187	internal object AndroidClassRewriter {
   188	    fun rewrite(classBytes: ByteArray): ByteArray {
   189	        val reader = ClassReader(classBytes)
   190	        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
   191	        reader.accept(ClassRemapper(writer, RewriteRules.REMAPPER), 0)
   192	        return writer.toByteArray()
   193	    }
   194	}
   195	```
   196	
   197	### 4.3 AarExtractor 수정
   198	
   199	기존 `extract(aarPath, cacheRoot)` 가 AAR 안의 `classes.jar` 를 그대로 추출. α 에서는 추출 *직후* 본 jar 를 rewrite:
   200	
   201	```kotlin
   202	fun extract(aarPath: Path, cacheRoot: Path): Path? {
   203	    // ... 기존 require / sha1 / outDir / mtime check ...
   204	
   205	    val tmpJar = outDir.resolve(artifactName + TEMP_JAR_SUFFIX)
   206	    ZipFile(aarPath.toFile()).use { aarZip ->
   207	        val entry = aarZip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
   208	        aarZip.getInputStream(entry).use { input ->
   209	            // α: classes.jar 의 entries 각각을 rewrite
   210	            rewriteClassesJar(input, tmpJar)
   211	        }
   212	    }
   213	    Files.move(tmpJar, outJar, ATOMIC_MOVE, REPLACE_EXISTING)
   214	    return outJar
   215	}
   216	
   217	private fun rewriteClassesJar(input: InputStream, outPath: Path) {
   218	    ZipInputStream(input).use { zin ->
   219	        ZipOutputStream(Files.newOutputStream(outPath)).use { zout ->
   220	            var entry = zin.nextEntry
   221	            while (entry != null)
   222	            {
   223	                val bytes = zin.readBytes()
   224	                val rewritten = if (entry.name.endsWith(CLASS_FILE_SUFFIX))
   225	                {
   226	                    AndroidClassRewriter.rewrite(bytes)
   227	                }
   228	                else
   229	                {
   230	                    bytes
   231	                }
   232	                zout.putNextEntry(java.util.zip.ZipEntry(entry.name))
   233	                zout.write(rewritten)
   234	                zout.closeEntry()
   235	                entry = zin.nextEntry
   236	            }
   237	        }
   238	    }
   239	}
   240	```
   241	
   242	`CLASS_FILE_SUFFIX = ".class"` 추가 — `ClassLoaderConstants` 에.
   243	
   244	### 4.4 RJarTypeMapping.kt
   245	
   246	```kotlin
   247	package dev.axp.layoutlib.worker.classloader
   248	
   249	import com.android.resources.ResourceType
   250	
   251	/**
   252	 * R$<simpleName> 의 simpleName → ResourceType 매핑.
   253	 * R.jar 안의 모든 inner class 이름을 ResourceType enum 으로 변환.
   254	 *
   255	 * 매핑 누락 시 null 반환 → 호출자가 그 R$* 클래스 전체 skip.
   256	 */
   257	internal object RJarTypeMapping {
   258	
   259	    private val MAPPING: Map<String, ResourceType> = mapOf(
   260	        "anim" to ResourceType.ANIM,

exec
/bin/bash -lc "nl -ba docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	# W3D3 — Branch (C) Root-Cause 진단 (orchestrator-level)
     2	
     3	**Status**: HARD STOP — user escalation required.
     4	**Date**: 2026-04-29.
     5	**Subagent (Task 9 implementer)**: 정확히 branch (C) 프로토콜 준수 — evidence 캡처, 푸시 없음, BLOCKED 보고.
     6	**Orchestrator (이 문서)**: subagent 가 보고한 stack trace 두 줄을 root-cause level 로 분석.
     7	
     8	---
     9	
    10	## 1. Subagent 가 발견한 두 줄
    11	
    12	```
    13	[layoutlib.error] resources.resolve | Failed to find the style corresponding to the id 2130903769 | null | null
    14	[LayoutlibRenderer] createSession result: status=ERROR_INFLATION msg=android.os.Build$VERSION exc=ClassNotFoundException
    15	```
    16	
    17	## 2. Root cause #1 — `android.os.Build$VERSION` ClassNotFoundException
    18	
    19	### 2.1 무엇이 발생했나
    20	ConstraintLayout (또는 그 transitive 의존) 의 bytecode 가 `android.os.Build.VERSION.SDK_INT` 를 참조. 우리 SampleAppClassLoader → isolated layoutlib CL 의 parent chain 에서 `android.os.Build$VERSION` 클래스 lookup 이 ClassNotFoundException 으로 실패 → ConstraintLayout `<init>` 이 throw → layoutlib BridgeInflater 가 `Result.Status.ERROR_INFLATION` 으로 wrap.
    21	
    22	### 2.2 왜 이게 의외인가 — layoutlib 가 자기 자신은 정상 init 함
    23	W3D1 의 `tier3-values` (`activity_minimal.xml`) 가 PASS — 즉 Bridge.init / RenderSession 자체는 정상 동작. activity_minimal 은 layoutlib 의 INTERNAL inflater 가 처리하는 framework widget 만 사용하므로 외부 classloader 경로를 trigger 하지 않음.
    24	
    25	### 2.3 layoutlib JAR 실측 (orchestrator)
    26	
    27	```
    28	$ unzip -l server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | grep "android/os/Build"
    29	(empty)
    30	
    31	$ unzip -l server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar | grep -E "android/os/.*Build"
    32	   android/os/_Original_Build$VERSION.class
    33	   android/os/_Original_Build$VERSION_CODES.class
    34	   android/os/_Original_Build.class
    35	```
    36	
    37	**결정적 finding**: layoutlib 14.0.11 JAR 은 `android/os/Build*.class` 를 포함하지 않는다. 단지 `_Original_Build*` prefix 변형만 존재.
    38	
    39	### 2.4 왜 layoutlib 가 `_Original_Build` 로 prefix 한 채 배포되나
    40	layoutlib 의 빌드 파이프라인이 `android.os.Build` 를 의도적으로 rename 한 후 재배포. 의도:
    41	- layoutlib 자신의 코드는 `_Original_Build` 만 참조 (참조 사이트가 모두 빌드 시점에 rename 됨).
    42	- 실 `android.os.Build` 는 **호스트 환경 (Android Studio 의 IDE classpath)** 가 SHIM 으로 제공한다고 가정.
    43	- Studio 의 ModuleClassLoader / DesignSurface 가 자체 `android.os.Build` 를 합성해 layoutlib 에 주입.
    44	
    45	**우리 환경에는 그 SHIM 이 없다**. dist/ 에 별도의 jar 가 없으며, worker 의 transitive 의존 (`com.android.tools.layoutlib:layoutlib-api:31.13.2` + Guava + kxml2 + ICU4J) 어디에도 `android.os.Build` 클래스가 없다 (검색 0 hit).
    46	
    47	### 2.5 layoutlib 자신은 어떻게 SDK_INT 를 평가하는가
    48	layoutlib 자신의 코드는 `_Original_Build.VERSION.SDK_INT` 라는 변형된 reference 를 사용. layoutlib 가 SessionParams 의 `targetSdk` 로부터 자기 SDK 정보를 받기 때문에 외부 `android.os.Build` 가 정상 존재할 필요 없음.
    49	
    50	### 2.6 그러나 AAR 의 외부 코드는 `android.os.Build` 그대로 참조
    51	constraintlayout-2.1.4 / material-1.12.0 / appcompat-1.6.1 등의 AAR `classes.jar` 는 `javac` 로 빌드된 보통 JVM 바이트코드 — `android.os.Build.VERSION` 을 그대로 참조한다 (rename 되어 있지 않음). 우리 URLClassLoader chain 으로 이 클래스를 resolve 시도 → 부재 → CNFE.
    52	
    53	### 2.7 가능한 fix 전략 (orchestrator 평가)
    54	
    55	| 전략 | 장점 | 단점 | LOC 추정 |
    56	|------|------|------|----------|
    57	| **A. Bytecode rewriting (ASM)** — AAR classes.jar 적재 시점에 `android/os/Build` → `android/os/_Original_Build` rename | 정공법; Paparazzi 등 이미 검증 | ASM 의존 추가, classloader 에 ClassFileTransformer 또는 사전-rewrite 단계 도입 | 200-400 |
    58	| **B. 별도 shim JAR** — `android/os/Build.class` (와 nested $VERSION) 를 자체 빌드해 SampleAppClassLoader URL 에 추가. 내부적으로 `_Original_Build` 에 위임 | LOC 적음, 명확한 surface | 호환 surface 가 layoutlib 버전마다 다를 수 있음 — Build API 변경 시 shim 재컴파일 필요 | 50-150 |
    59	| **C. Paparazzi 의 layoutlib 배포로 교체** | 이미 같은 문제를 해결한 distribution | dist 교체 = 큰 변경; w1d3-r2 의 canonical bundle 검증 다시 | 환경 변경 |
    60	| **D. 딴 길로** — AVD-L3 (canonical L3, plan/06 §2) 로 전환 | 호스트 JVM 의존 회피 | v1 일정 6주 → 8 주 가능; plan/06 §6 W4 carry 의 본격 작업 | 본 W3D3 폐기 |
    61	
    62	**Orchestrator 추천**: 본 회기에서 결정 보류 — 사용자 escalation 후 결정. B 가 가장 surgical, A 는 가장 정공.
    63	
    64	## 3. Root cause #2 — Style id 2130903769 resolve 실패 (Codex round 1 B3 confirmed)
    65	
    66	### 3.1 무엇이 발생했나
    67	```
    68	[layoutlib.error] resources.resolve | Failed to find the style corresponding to the id 2130903769 | null | null
    69	[layoutlib.error] null | Failed to find style with id 0x7f0f03fd in current theme
    70	```
    71	
    72	`2130903769 == 0x7f0f03fd`. 이 id 는 sample-app `R.jar` 의 `com.fixture.R$style.*` 또는 transitive AAR 의 R$style 중 하나.
    73	
    74	### 3.2 Codex round 1 spec-pair-review B3 와의 일치
    75	> "Putting `R.jar` on the classloader is necessary but not sufficient. Current callback generates IDs from `0x7F0A_0000`. Actual `R.attr` IDs from the built `R.jar` are different, e.g. `layout_constraintTop_toTopOf = 2130903705`. Constructors use actual `R.styleable`/`R.attr` ints, but `resolveResourceId()` cannot map those ints back to resources, so `TypedArray` lookups for ConstraintLayout/Material attrs miss."
    76	
    77	**확정**: 현 SampleAppClassLoader + R.jar 가 클래스 적재는 가능하게 하지만, layoutlib 의 RenderResources 가 R.jar 의 컴파일된 id 와 callback 의 generated id 를 일치시키지 못한다. spec round 1 Q3 의 R3 가 "broken positioning, but SUCCESS 가능" 이라고 추정했지만, 실제로는 style resolve 실패가 inflate 자체를 막을 수 있다 (FrameworkRenderResources 의 setTheme 이 실패하면 ERROR_INFLATION).
    78	
    79	### 3.3 가능한 fix 전략
    80	
    81	| 전략 | 장점 | 단점 |
    82	|------|------|------|
    83	| **R.jar id 시드** — startup 에 R.jar 를 reflection 으로 모든 R$* 클래스의 모든 static int 필드 enumerate, ResourceReference 로 mapping 하여 callback 의 byRef/byId 를 사전-populate | 정공법, 한 번만 | R$* 클래스의 namespace 추론 (com.fixture.R$style.X 가 sample-app vs material vs constraintlayout 인지) 필요 |
    84	| **App resource value loader** (W3D4 carry) | 완전한 fidelity | 큰 작업 (~500-1000 LOC, framework loader 와 같은 규모) |
    85	
    86	전략 1 이 짧음. 단, root cause #1 이 먼저 해결 안 되면 무의미.
    87	
    88	---
    89	
    90	## 4. Subagent 가 만든 unpushed local commit 정리
    91	
    92	`df772bc` (local only, not pushed): `LayoutlibRendererIntegrationTest` 의 `@Disabled` 가 제거된 채로 남아있다. 본 commit 을 main 에 push 하면 CI 가 실패하므로 해당 변경을 다시 정정해야 한다 — orchestrator 가 다음 동작에서 amend.
    93	
    94	---
    95	
    96	## 5. 사용자 결정 필요 — 다음 단계 옵션
    97	
    98	**Option α — bytecode rewriting (전략 A)**: ASM 의존 추가, AAR classes.jar 의 `android/os/Build*` 참조를 `android/os/_Original_Build*` 로 rewrite 하는 ClassFileTransformer 또는 사전-rewrite. 200-400 LOC. id 시드 (Root cause #2) 도 함께 fix. **W3D3 deliverable 이 본 안에서 close 가능 (예상 1-2 일).**
    99	
   100	**Option β — Build shim JAR (전략 B)**: `android.os.Build` 를 직접 작성해 SampleAppClassLoader URL 에 추가. delegate to `_Original_Build`. id 시드도 동시 작업. 50-150 LOC + R.jar id 시드 100-200 LOC. **W3D3 close 가능 (예상 0.5-1 일).** — 가장 surgical.
   101	
   102	**Option γ — defer W3D3, AVD-L3 진입 (전략 D)**: plan/06 의 canonical L3. v1 일정 보존. W3D3 의 classloader 인프라 (Tasks 1-8) 는 별도 carry 로 보존하고 재활용 가능 — host-JVM 외부 fixture 코드 동적 적재 시 사용 (예: hot reload of user-edited code).
   103	
   104	**Option δ — partial close (인프라만)**: 본 회기에서 Tasks 1-8 의 classloader 인프라는 commit 됨 (이미 push). Task 9 의 통합 테스트는 `@Disabled` 로 되돌리고 명확한 disable 사유로 본 진단 문서 cite. W3D4 가 옵션 α 또는 β 를 선택해 enable. **현 회기 cleanup 비용 가장 낮음.**
   105	
   106	**Orchestrator 추천**: Option δ + 다음 회기에 Option β (가장 surgical) 또는 Option α (가장 정공) 결정. 본 회기는 이미 3+ 시간 진행 — 새 architectural 작업을 즉시 시작하는 것보다 깔끔한 stop point 가 합리적.
   107	
   108	---
   109	
   110	## 6. 부수 commit cleanup 계획 (Option δ 선택 시)
   111	
   112	1. `df772bc` 을 reset --soft 로 풀고 local 변경 유지.
   113	2. `LayoutlibRendererIntegrationTest.kt` 에 `@Disabled("W3D3 branch (C) — see docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md")` 다시 추가. SUCCESS assertion 과 `requireNotNull` 같은 다른 변경은 미래의 enable 를 위해 보존.
   114	3. 신규 commit + push.
   115	4. branch-c-diagnosis.md (본 문서) 는 별도 commit.
   116	5. Task 10 (work_log + handoff) 가 본 진단을 cite.
   117	
   118	본 회기 종결 시점의 net 상태:
   119	- Tasks 1-8 인프라 push 완료 (활용 가능).
   120	- Task 9 BLOCKED, Task 10 (work_log) 완료.
   121	- Test suite: 142 unit PASS, 11 integration PASS + 2 SKIP (W3D2 baseline 그대로 — `LayoutlibRendererIntegrationTest` 다시 SKIP).
   122	- W3D3 carry: option α 또는 β 결정 후 진입.

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
    12	## 0. 페어 리뷰 결과 (round 1, 2026-04-29)
    13	
    14	본 스펙 round 1 페어 리뷰 (Codex GPT-5.5 xhigh + Claude Opus 4.7) 결과:
    15	- **Codex**: NO_GO (4 blockers + 4 follow-ups). `/tmp/w3d3-codex-spec-review.md`.
    16	- **Claude**: GO_WITH_FOLLOWUPS (1 blocker + 6 follow-ups). `/tmp/w3d3-claude-spec-review.md`.
    17	
    18	**컨버전스 항목 (양측 일치, round 2 본문에 반영)**:
    19	- **B1**: `fixtureRoot` 의미 불일치 — 별도 `sampleAppModuleRoot` 인자 도입 (§4.4, §4.7).
    20	- **F1**: `MinimalLayoutlibCallback.findClass` + `hasAndroidXAppCompat` override 추가 (§4.6 D3).
    21	- **B4 (Codex-only, valid)**: magic strings 를 `ClassLoaderConstants` 로 (§4.5).
    22	- **Manifest dedup** (Claude-F4): `.distinct()` 추가 (§4.1).
    23	- **Atomic AAR write** (Codex-B2-followup): temp + ATOMIC_MOVE (§4.3).
    24	- **Q1 (round 2 정정)**: ~~`afterEvaluate` 제거~~ — round 2 페어 리뷰에서 empirical 검증 결과 AGP 8.x 의 `assembleDebug` 가 variant API 등록이라 top-level `tasks.named` 시점에 미존재 → `UnknownTaskException`. **`afterEvaluate { ... }` 필수**. Round 1 convergence 가 잘못된 가정에 기반했음 (둘 다 empirical 검증 안 함). §4.1 본문은 `afterEvaluate` 포함 패턴.
    25	- **Q2**: mtime 유지 (Gradle modules-2 cache 의 content-hash dir 가정).
    26	- **Q3**: Codex 입장 채택 — `InvocationTargetException` cause unwrap (§4.6).
    27	- **Q4**: lazy build (option A) 유지 (§4.7).
    28	- **Q5**: T1 gate 에 `Result.Status == SUCCESS` assertion 활성 (§4.8).
    29	
    30	**디버전트 항목 (round 2 에서 처리)**:
    31	- **B2** (Codex): activity_basic.xml 의 `MaterialButton` 이 Material theme enforcement 로 SUCCESS 불가 주장 — Claude 는 약하게 동의 (D7 fidelity 손상). **empirical 검증 필요** — 구현 후 실측. Contingency: T1 gate 가 fail 시 § 9.1 의 fallback layout (activity_basic_minimal.xml — MaterialButton → 표준 Button) 으로 자동 다운그레이드.
    32	- **B3** (Codex): R.jar real id (e.g., `2130903705`) ↔ callback generated id (`0x7F0A_xxxx`) 불일치. **이미 §1.2 R3 가 인정** — broken positioning. SUCCESS 자체는 막지 않음 (`obtainStyledAttributes` 가 unknown id 에 대해 default 반환, throw 없음). round 2 에서 §1.2 R3 표현 강화.
    33	
    34	---
    35	
    36	## 0. 용어 정리 (CRITICAL — 페어 리뷰 전 명확화)
    37	
    38	### 0.1 "L3" 의 두 가지 의미
    39	
    40	이 프로젝트에서 "L3" 는 두 다른 메커니즘을 지칭한다:
    41	
    42	| 명명 | 메커니즘 | 위치 | 상태 |
    43	|------|---------|------|------|
    44	| **canonical L3** (`plan/06 §2.1`) | AVD 에뮬레이터 + harness APK + Dalvik DexClassLoader | 별도 Android 런타임 프로세스 | v1.0 W3 후반/W4 carry |
    45	| **본 W3D3 L3** (=in-JVM custom view) | layoutlib host JVM 안에서 AAR `classes.jar` 를 URLClassLoader 로 적재 | layoutlib-worker JVM | 본 스펙 타겟 |
    46	
    47	핸드오프 `Option A` 와 사용자 prompt 의 "DexClassLoader 기반 L3 class loading" 표현은 **후자** 를 의미한다 (사용자 prompt 의 dex/jar 양쪽 허용 표현이 근거). 본 스펙 안에서는 혼동 회피를 위해:
    48	- **canonical L3** = AVD-L3.
    49	- **본 스펙 타겟** = "in-JVM custom view loading" 또는 "L3-classloader" (개행 시 줄임 — 동일 의미).
    50	
    51	### 0.2 DexClassLoader 가 본 스펙에서 "쓰이지 않는" 이유
    52	
    53	`dalvik.system.DexClassLoader` 는 Android 런타임(ART/Dalvik) 전용 API 이며, host JVM 에는 존재하지 않는다. layoutlib 은 host JVM 위에서 `libandroid_runtime.so` 만 native 로 로드하여 Android 프레임워크 클래스를 JVM 바이트코드 형태로 포함한 layoutlib JAR 을 사용한다. AAR 의 `classes.jar` 도 D8 변환 전의 **JVM 바이트코드** (= classfile, magic `0xCAFEBABE`) — DEX(매직 `dex.035`) 가 아니다.
    54	
    55	따라서 본 스펙은 `java.net.URLClassLoader` 를 사용한다. 사용자 prompt 의 "dex/jar classloader" 표현은 jar branch 를 채택.
    56	
    57	### 0.3 ChildFirst 정책 검토
    58	
    59	`plan/06 §2.3` 의 ChildFirstDexClassLoader 정책은 AVD-L3 한정 — harness APK 의 androidx 가 사용자 앱 androidx 보다 우선되지 않도록 child-first delegation 을 강제하기 위함. 본 스펙은 다음 구조이므로 ChildFirst 가 **불필요**:
    60	
    61	```
    62	system CL          ← Kotlin stdlib, layoutlib-api, kxml2
    63	   ↑ parent
    64	isolated CL        ← layoutlib JAR (android.view.*, android.widget.*, android.content.*)
    65	   ↑ parent
    66	AAR URL CL         ← constraintlayout/material/appcompat/... classes.jar + sample-app R.jar
    67	```
    68	
    69	각 계층의 클래스가 disjoint:
    70	- `android.*` → isolated CL only.
    71	- `androidx.*` / `com.google.android.material.*` / `com.fixture.*` → AAR CL only.
    72	- `kotlin.*` / `kxml2.*` / `org.xmlpull.*` → system CL.
    73	
    74	shadowing 가능 클래스 없음 → parent-first 안전. ChildFirst 도입은 YAGNI.
    75	
    76	---
    77	
    78	## 1. 요구사항 (W3D3 deliverable)
    79	
    80	### 1.1 In-scope (T1 gate 통과 목표)
    81	
    82	1. AAR 의 `classes.jar` 를 host JVM 클래스로더로 적재.
    83	2. `MinimalLayoutlibCallback.loadView(name, sig, args)` 가 위 클래스로더로부터 custom view 를 reflection-instantiate.
    84	3. sample-app 의 `R.jar` (이미 빌드되어 존재) 를 클래스로더 URL 에 포함 — `androidx.constraintlayout.widget.R$styleable` 등이 정상 resolve 되어야 함.
    85	4. `LayoutlibRendererIntegrationTest` 의 `@Disabled` 제거 + tier3-values 패턴 (T1 gate: `Result.Status == SUCCESS && PNG > 1000 bytes`) 통과.
    86	
    87	### 1.2 Out-of-scope (W3D4+ carry)
    88	
    89	다음은 본 W3D3 에서 **다루지 않는다**. 페어 리뷰가 in-scope 이동을 요구하면 별도 합의:
    90	
    91	- **App/library resource value loading**: AAR 의 `res/values/values.xml` 파싱 + RenderResources 통합. 영향:
    92	  - ConstraintLayout 의 `app:layout_constraintTop_toTopOf` 등 custom attr 가 default(0/null) 로 fallback → 모든 child 가 (0,0) 에 적층. layout 위치가 잘못되어도 `Result.Status == SUCCESS` + PNG > 1000 bytes 는 통과 가능.
    93	  - sample-app 의 `Theme.AxpFixture` (Material3.DayNight 파생) 가 적용 안 됨 — framework `Theme.Material.Light.NoActionBar` 로 fallback. AppCompat/Material 의 `?attr/textAppearanceHeadlineMedium`, `?attr/colorOnSurface` 등 unresolved → 모든 TextView default text appearance.
    94	  - **R.jar real id (e.g., `2130903705`) ↔ callback generated id (`0x7F0A_xxxx`) 불일치**: ConstraintLayout/MaterialButton 등이 `obtainStyledAttributes(attrs, R.styleable.X)` 를 호출하면 `R.styleable.X` 의 int 들은 layoutlib 의 resource id space 와 disjoint → `TypedArray` lookup miss, default 반환 (throw 없음). 이는 SUCCESS 자체는 막지 않음.
    95	  - **Material theme enforcement 위험**: `MaterialThemeOverlay.wrap` / `ThemeEnforcement.checkCompatibleTheme` 가 framework theme 에 specific Material attr 부재 시 throw 가능 — empirical 검증 필요 (구현 후 §9.1 contingency 발동 여부 판단).
    96	- **Visual fidelity assertion** (pixel level). T1 gate 는 status + 크기만 검사.
    97	- **AVD-L3** (canonical L3 — `plan/06 §2`).
    98	- **tier3-glyph** (W4+ carry).
    99	
   100	### 1.3 Acceptance Gate (T1)
   101	
   102	```kotlin
   103	// LayoutlibRendererIntegrationTest 가 expect:
   104	val bytes = renderer.renderPng("activity_basic.xml")
   105	assertEquals(SUCCESS, renderer.lastSessionResult.status)   // 페어 리뷰가 강하게 권장하면 추가
   106	assertTrue(bytes.size > 1000)                              // 기존 패턴
   107	assertTrue(bytes[0..3] == PNG_MAGIC)                       // 기존 패턴
   108	```
   109	
   110	---
   111	
   112	## 2. 컴포넌트 분해
   113	
   114	| # | 파일 | 책임 | LOC 추정 |
   115	|---|------|------|----------|
   116	| C1 | `fixture/sample-app/app/build.gradle.kts` (수정) | `axpEmitClasspath` Gradle task — 해석된 debugRuntimeClasspath 의 AAR + JAR 절대 경로를 manifest 로 emit. `assembleDebug` 에 finalizedBy. | +25 |
   117	| C0 | `server/layoutlib-worker/.../FixtureDiscovery.kt` (수정) | `locateModuleRoot(override): Path?` 메서드 추가 — `fixture/sample-app` (= layout 디렉토리의 5 ancestors up). FIXTURE_MODULE_SUBPATH 상수. | +30 |
   118	| C2 | `server/layoutlib-worker/.../classloader/SampleAppClasspathManifest.kt` | manifest 파일 (텍스트, 한 줄에 한 절대 경로) 파싱. 누락/오래됨/형식 오류 → 명시 예외. **input 은 sampleAppModuleRoot** (B1 수정). | 60 |
   119	| C3 | `server/layoutlib-worker/.../classloader/AarExtractor.kt` | `.aar` ZIP 에서 `classes.jar` 를 stable cache (`<sample-app>/app/build/axp/aar-classes/<sha1>/<artifact>.jar`) 로 추출. mtime 기반 idempotent. **atomic write** (temp + ATOMIC_MOVE). | 80 |
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
   225	// Q1 round 2 정정: AGP 8.x `assembleDebug` 는 variant API 등록 → top-level `tasks.named`
   226	// 시점에 미존재 → UnknownTaskException. `afterEvaluate` 필수 (empirical 검증 완료).
   227	afterEvaluate {
   228	    tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
   229	}
   230	```
   231	
   232	**계약**:
   233	- 출력 경로: `fixture/sample-app/app/build/axp/runtime-classpath.txt`.
   234	- 형식: 라인 separator `\n`, 각 라인 = AAR 또는 JAR 의 절대경로. trailing newline 없음 (joinToString 결과).
   235	- 정렬: lexicographic — reproducibility 를 위함.
   236	- 경로는 `.aar` 또는 `.jar` 로 끝나야 함. 그 외 형식 발견 시 worker 가 거부 (TC1 가 검증).
   237	
   238	**왜 emit 시점 = `finalizedBy(assembleDebug)`?**: 사용자 흐름 `./gradlew :app:assembleDebug` 가 보통 W3 dev 사이클의 entry point. 별도 task 명령 강제 시 forget 위험.
   239	
   240	**페어 리뷰 Q1**: `applicationVariants.all` 또는 androidComponents API 를 써야 할까? — `debugRuntimeClasspath` 은 표준 configuration 이름 (AGP 8.x 안정), 직접 참조 OK. 그러나 향후 flavor 추가 시 break — 본 fixture 는 flavor 없음, defer.
   241	
   242	### 4.2 C2 — SampleAppClasspathManifest
   243	
   244	`sampleAppModuleRoot` 가 `fixture/sample-app` 모듈 루트 (B1 fix). MANIFEST_RELATIVE_PATH 도 module-root 기준으로 재정의 (§4.5).
   245	
   246	```kotlin
   247	package dev.axp.layoutlib.worker.classloader
   248	
   249	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
   250	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.JAR_EXTENSION
   251	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
   252	import java.nio.file.Files
   253	import java.nio.file.Path
   254	
   255	/**
   256	 * <sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt 파일을 읽어
   257	 * resolved runtime classpath 의 AAR/JAR 절대경로 리스트를 제공한다.
   258	 *
   259	 * 파일 형식 (axpEmitClasspath Gradle task 가 emit):
   260	 *  - 라인 separator '\n', trailing newline 없음.

exec
/bin/bash -lc "nl -ba docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md | sed -n '244,620p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   244	### 4.4 RJarTypeMapping.kt
   245	
   246	```kotlin
   247	package dev.axp.layoutlib.worker.classloader
   248	
   249	import com.android.resources.ResourceType
   250	
   251	/**
   252	 * R$<simpleName> 의 simpleName → ResourceType 매핑.
   253	 * R.jar 안의 모든 inner class 이름을 ResourceType enum 으로 변환.
   254	 *
   255	 * 매핑 누락 시 null 반환 → 호출자가 그 R$* 클래스 전체 skip.
   256	 */
   257	internal object RJarTypeMapping {
   258	
   259	    private val MAPPING: Map<String, ResourceType> = mapOf(
   260	        "anim" to ResourceType.ANIM,
   261	        "animator" to ResourceType.ANIMATOR,
   262	        "array" to ResourceType.ARRAY,
   263	        "attr" to ResourceType.ATTR,
   264	        "bool" to ResourceType.BOOL,
   265	        "color" to ResourceType.COLOR,
   266	        "dimen" to ResourceType.DIMEN,
   267	        "drawable" to ResourceType.DRAWABLE,
   268	        "font" to ResourceType.FONT,
   269	        "fraction" to ResourceType.FRACTION,
   270	        "id" to ResourceType.ID,
   271	        "integer" to ResourceType.INTEGER,
   272	        "interpolator" to ResourceType.INTERPOLATOR,
   273	        "layout" to ResourceType.LAYOUT,
   274	        "menu" to ResourceType.MENU,
   275	        "mipmap" to ResourceType.MIPMAP,
   276	        "navigation" to ResourceType.NAVIGATION,
   277	        "plurals" to ResourceType.PLURALS,
   278	        "raw" to ResourceType.RAW,
   279	        "string" to ResourceType.STRING,
   280	        "style" to ResourceType.STYLE,
   281	        "styleable" to ResourceType.STYLEABLE,
   282	        "transition" to ResourceType.TRANSITION,
   283	        "xml" to ResourceType.XML,
   284	    )
   285	
   286	    fun fromSimpleName(simpleName: String): ResourceType? = MAPPING[simpleName]
   287	}
   288	```
   289	
   290	### 4.5 RJarSymbolSeeder.kt
   291	
   292	```kotlin
   293	package dev.axp.layoutlib.worker.classloader
   294	
   295	import com.android.ide.common.rendering.api.ResourceNamespace
   296	import com.android.ide.common.rendering.api.ResourceReference
   297	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.CLASS_FILE_SUFFIX
   298	import java.lang.reflect.Modifier
   299	import java.nio.file.Path
   300	import java.util.zip.ZipFile
   301	
   302	/**
   303	 * sample-app `R.jar` 의 모든 R$<type> 클래스를 enumerate 하여, 각 정적 int 필드를
   304	 * (ResourceReference, id) 로 callback 에 등록. layoutlib 의 RenderResources 가 styleable
   305	 * lookup 시 본 등록된 id 를 통해 자원으로 resolve 한다.
   306	 *
   307	 * 처리:
   308	 *  - R$styleable 의 int[] 필드는 skip — 본 array 는 styleable 정의이며 layoutlib 이 자체로 처리.
   309	 *  - 알 수 없는 type (RJarTypeMapping 미매핑) 인 R$* 는 skip.
   310	 *
   311	 * Codex round 1 B3 의 정공법 — host-JVM 환경에서 AAR R 클래스 컴파일된 id 를 callback 의
   312	 * resolveResourceId 로 reverse-lookup 가능하게 함.
   313	 */
   314	internal object RJarSymbolSeeder {
   315	
   316	    fun seed(
   317	        rJarPath: Path,
   318	        rJarLoader: ClassLoader,
   319	        register: (ResourceReference, Int) -> Unit,
   320	    ) {
   321	        ZipFile(rJarPath.toFile()).use { zip ->
   322	            for (entry in zip.entries())
   323	            {
   324	                if (!entry.name.endsWith(CLASS_FILE_SUFFIX))
   325	                {
   326	                    continue
   327	                }
   328	                val internalName = entry.name.removeSuffix(CLASS_FILE_SUFFIX)
   329	                val parts = parseRClassName(internalName) ?: continue
   330	                val (packageName, typeSimpleName) = parts
   331	                val resourceType = RJarTypeMapping.fromSimpleName(typeSimpleName) ?: continue
   332	                seedClass(rJarLoader, internalName.replace('/', '.'), packageName, resourceType, register)
   333	            }
   334	        }
   335	    }
   336	
   337	    /**
   338	     * @return Pair(packageName, typeSimpleName) — 예: ("androidx.constraintlayout.widget", "attr").
   339	     * R 클래스 이름 패턴 안 맞으면 null.
   340	     */
   341	    private fun parseRClassName(internalName: String): Pair<String, String>? {
   342	        // 예: "androidx/constraintlayout/widget/R$attr"
   343	        val dollarIdx = internalName.lastIndexOf('$')
   344	        if (dollarIdx < 0)
   345	        {
   346	            return null
   347	        }
   348	        val before = internalName.substring(0, dollarIdx)
   349	        val after = internalName.substring(dollarIdx + 1)
   350	        // before 가 ".../R" 형태여야 함
   351	        if (!before.endsWith("/R"))
   352	        {
   353	            return null
   354	        }
   355	        val packageInternal = before.removeSuffix("/R")
   356	        return packageInternal.replace('/', '.') to after
   357	    }
   358	
   359	    private fun seedClass(
   360	        loader: ClassLoader,
   361	        fqcn: String,
   362	        packageName: String,
   363	        type: com.android.resources.ResourceType,
   364	        register: (ResourceReference, Int) -> Unit,
   365	    ) {
   366	        val cls = try
   367	        {
   368	            loader.loadClass(fqcn)
   369	        }
   370	        catch (t: Throwable)
   371	        {
   372	            return
   373	        }
   374	        val namespace = ResourceNamespace.fromPackageName(packageName)
   375	        for (field in cls.declaredFields)
   376	        {
   377	            if (!Modifier.isStatic(field.modifiers))
   378	            {
   379	                continue
   380	            }
   381	            // styleable 의 int[] 같은 array 필드는 skip
   382	            if (field.type != Int::class.javaPrimitiveType)
   383	            {
   384	                continue
   385	            }
   386	            field.isAccessible = true
   387	            val value = field.getInt(null)
   388	            register(ResourceReference(namespace, type, field.name), value)
   389	        }
   390	    }
   391	}
   392	```
   393	
   394	### 4.6 MinimalLayoutlibCallback 수정
   395	
   396	```kotlin
   397	class MinimalLayoutlibCallback(
   398	    private val viewClassLoaderProvider: () -> ClassLoader,
   399	    /**
   400	     * α: callback 생성 직후 호출되는 seeder. R.jar id seeding 등 사전-populate 작업.
   401	     * lambda 기본값 없음 (CLAUDE.md "no default parameter values").
   402	     */
   403	    private val initializer: (registerSymbol: (ResourceReference, Int) -> Unit) -> Unit,
   404	) : LayoutlibCallback() {
   405	
   406	    private val nextId = AtomicInteger(FIRST_ID)
   407	    private val byRef = mutableMapOf<ResourceReference, Int>()
   408	    private val byId = mutableMapOf<Int, ResourceReference>()
   409	
   410	    init {
   411	        initializer { ref, id ->
   412	            // 사전-populate 시 race 없음 — init 이후 callback 사용 시작.
   413	            byRef[ref] = id
   414	            byId[id] = ref
   415	            // nextId 도 안전하게 advance — 등록된 id 보다 큰 next 가 보장되어야 collision 회피
   416	            advanceNextIdAbove(id)
   417	        }
   418	    }
   419	
   420	    private fun advanceNextIdAbove(seeded: Int) {
   421	        // CAS 로 nextId >= seeded + 1 를 보장. 단순히 set 으로 덮어쓸 수도 있지만,
   422	        // R.jar 의 id 가 0x7F0A_0000 보다 항상 작은 0x7F0A_0000 미만일 수 있어 (특히 com.fixture.R)
   423	        // monotonicity 만 유지.
   424	        while (true)
   425	        {
   426	            val current = nextId.get()
   427	            if (current > seeded)
   428	            {
   429	                return
   430	            }
   431	            if (nextId.compareAndSet(current, seeded + 1))
   432	            {
   433	                return
   434	            }
   435	        }
   436	    }
   437	
   438	    @Synchronized
   439	    override fun getOrGenerateResourceId(ref: ResourceReference): Int {
   440	        byRef[ref]?.let { return it }
   441	        val id = nextId.getAndIncrement()
   442	        byRef[ref] = id
   443	        byId[id] = ref
   444	        return id
   445	    }
   446	
   447	    // ... resolveResourceId / loadView / findClass / hasAndroidXAppCompat 등 그대로 ...
   448	}
   449	```
   450	
   451	### 4.7 LayoutlibRenderer 수정
   452	
   453	`ensureSampleAppClassLoader()` 가 `SampleAppClassLoader` 를 빌드한 후 그 안에서 R.jar URL 을 추출, `RJarSymbolSeeder.seed` 를 호출하는 lambda 를 생성하여 callback 생성자에 주입:
   454	
   455	```kotlin
   456	private fun renderViaLayoutlib(layoutName: String): ByteArray? {
   457	    // ... 기존 코드 ...
   458	
   459	    val seeder: (((ResourceReference, Int) -> Unit) -> Unit) = { register ->
   460	        val sampleAppCL = ensureSampleAppClassLoader()
   461	        val rJarPath = sampleAppModuleRoot.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH)
   462	        RJarSymbolSeeder.seed(rJarPath, sampleAppCL, register)
   463	    }
   464	
   465	    val params: SessionParams = SessionParamsFactory.build(
   466	        layoutParser = parser,
   467	        callback = MinimalLayoutlibCallback(
   468	            viewClassLoaderProvider = { ensureSampleAppClassLoader() },
   469	            initializer = seeder,
   470	        ),
   471	        resources = resources,
   472	    )
   473	    // ... 기존 코드 ...
   474	}
   475	```
   476	
   477	호출 부 (`MinimalLayoutlibCallbackTest` / `SessionParamsFactoryTest` 등 기존 8+2 sites): `MinimalLayoutlibCallback({ cl }, { /* no-op */ })` — initializer 가 register 를 호출하지 않아 기존 byRef/byId 가 빈 상태로 시작 (W3D3 round 2 의 동작과 동일).
   478	
   479	### 4.8 LayoutlibRendererIntegrationTest 활성화
   480	
   481	W3D3 round 2 에서 이미 SUCCESS assertion + `requireNotNull` 등이 작성됨. `@Disabled` annotation 만 제거.
   482	
   483	---
   484	
   485	## 5. 위험 / 알려진 한계
   486	
   487	### 5.1 위험
   488	
   489	| ID | 위험 | 완화 |
   490	|----|------|------|
   491	| Rα-1 | NAME_MAP 가 필요 매핑을 누락 — 본 25 외에 layoutlib 가 다른 framework class 도 rewrite 했을 수 있음 | empirical 검증: 모든 `_Original_*` 가 NAME_MAP 의 reverse 방향에 정확 매핑됨을 확인. 추가 발견 시 한 줄 추가. |
   492	| Rα-2 | classes.jar rewrite 가 stack frame 재계산을 트리거 (ClassWriter.COMPUTE_FRAMES 미사용) → bytecode 무결성 issue | type rename 만 수행하고 stack 변경은 없으므로 COMPUTE_MAXS 로 충분. ASM 표준 패턴. |
   493	| Rα-3 | R.jar 의 일부 R$* 클래스가 reflection 시 NoClassDefFoundError | seeder 가 try-catch 로 skip — robust |
   494	| Rα-4 | id seeding 후 0x7F0A_0000 이상의 id 가 R.jar 에 존재해 nextId 와 collision | `advanceNextIdAbove` 가 seed 후 nextId 를 monotonically 증가. CAS 로 thread-safe. |
   495	| Rα-5 | sample-app 의 com.fixture.R 같은 application R 의 id 가 0x7F0A_xxxx 와 겹침 | 동일 ResourceReference (namespace + type + name) 라면 register 가 byRef 에 같은 entry 한 번. 다른 ref 라면 두 id 가 같은 byRef 매핑이 안 됨 — 후순위 등록이 byRef 에서 conflict 가능. **mitigation**: register 가 `if byRef.contains(ref) ignore` 정책. (단, sample-app 환경에서 발생 불가능 — 0x7F0A_0000 아래 R.jar id 만 존재.) |
   496	| Rα-6 | Material/AppCompat 의 ThemeEnforcement 가 별도 throw (round 1 Codex B2 의 가설) | branch (C) 진단에서 stack trace 가 ThemeEnforcement 가 *아닌* Build CNFE 였음 — α 가 Build 를 해결한 후 ThemeEnforcement 가 재발화하면 §5.2 발동. |
   497	
   498	### 5.2 contingency — α 적용 후 추가 blocker 발견 시
   499	
   500	α 적용 후 통합 테스트가 다시 FAIL 할 가능성:
   501	- (A) Build 와 ServiceManager 가 해결되었으나 layoutlib 의 다른 internal API 가 누락 → empirical NAME_MAP 확장.
   502	- (B) R.jar id seeding 만으로 부족하고 추가로 *resource value* 도 RenderResources 에 주입해야 함 → app/library resource value loader (W3D4 carry, 별도 work).
   503	- (C) ThemeEnforcement throw — `SessionConstants.DEFAULT_FRAMEWORK_THEME` (`Theme.Material.Light.NoActionBar`) 는 frame work theme. AppCompat 자동 치환이 활성화되었으므로 inflation 은 `AppCompatTextView` 사용 → AppCompat 이 framework theme 와 호환 점검 가능 (?attr/colorAccent 등). throw 면 `setTheme` 를 sample-app 의 `Theme.AxpFixture` 로 교체 필요 — 이는 W3D4 의 resource value loader 와 함께만 가능.
   504	
   505	α 의 contingency: 발화 시 hard-stop 후 진단 (W3D3-α 자체의 branch (C)).
   506	
   507	---
   508	
   509	## 6. 페어 리뷰 질문 (Q1-Q3)
   510	
   511	### Q1 — NAME_MAP 25 entries 가 layoutlib 14.0.11 의 전수인가?
   512	empirical 검증: `unzip -l layoutlib-14.0.11.jar | grep "_Original_" | wc -l` = 25. 매핑 25 entries 는 그 전수 1:1. 단, layoutlib 이 *간접적으로* rewrite 한 다른 reference (예: import statement 의 type signature) 가 있을 가능성 — Paparazzi 의 transformer 와 비교 필요.
   513	
   514	### Q2 — R.jar id 와 callback generated id collision 처리
   515	seeder 가 등록한 id 가 0x7F0A_0000 ~ 0x7F0A_FFFF 범위와 겹치는지? sample-app 의 R.jar id 분포: AGP 가 0x7F010001 (attr), 0x7F060001 (color), 0x7F0F0001 (style) 등 type 별 sub-range 사용. 0x7F0A 는 `R.id` (id 0x0A) 이지만 sample-app 에서는 미사용 (com.fixture 가 layout id 를 layout XML 에 정의 안 함). 그러나 transitive AAR (material 의 R.id 등) 는 0x7F0A 범위 사용. **Codex/Claude 어느 입장이 맞는지 검증 필요.**
   516	
   517	### Q3 — `R$styleable` int[] 필드 skip 정책
   518	seeder 가 `R$styleable.MaterialButton` (= `int[]`) 을 skip. layoutlib 의 RenderResources 가 styleable 을 어떻게 lookup 하나? styleable 은 attr id 의 array 이므로 attr 자체가 등록되어 있으면 충분 — array 자체를 register 하지 않아도 layoutlib 의 `obtainStyledAttributes(set, attrs[])` 가 array 의 각 attr id 로 lookup. 검증: layoutlib 14.x 의 `BridgeContext.obtainStyledAttributes` 가 attr id array 를 caller 가 준 것으로 사용하므로 array 자체 등록 불요. **Codex 검증 권장.**
   519	
   520	---
   521	
   522	## 7. Acceptance Gate (T1)
   523	
   524	본 W3D3-α 의 close 조건:
   525	
   526	```kotlin
   527	// LayoutlibRendererIntegrationTest 가 expect:
   528	val bytes = renderer.renderPng("activity_basic.xml")
   529	assertEquals(SUCCESS, renderer.lastSessionResult?.status)
   530	assertTrue(bytes.size > 1000)
   531	assertTrue(bytes[0..3] == PNG_MAGIC)
   532	```
   533	
   534	전체 게이트:
   535	- 142 unit + 23 신규 unit = ~165 unit PASS
   536	- 11 → 12 integration PASS, 1 SKIP (tier3-glyph)
   537	- BUILD SUCCESSFUL
   538	
   539	---
   540	
   541	## 8. 변경 로그
   542	
   543	- 2026-04-29: 초안 작성. W3D3 branch (C) 의 옵션 α 진입.

exec
/bin/bash -lc "nl -ba CLAUDE.md | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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

exec
/bin/bash -lc "nl -ba docs/work_log/2026-04-29_w3d3-l3-classloader/session-log.md | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	# W3D3 Session Log — L3 ClassLoader Infrastructure (Tasks 1-8 close, Task 9 BLOCKED)
     2	
     3	**Date**: 2026-04-29 (W3 Day 3)
     4	**Canonical ref**: `docs/plan/08-integration-reconciliation.md §7.7.4`.
     5	**Outcome**: classloader 인프라 (4 main + 4 test 신규 + 6 modify) push 완료. 142 unit + 11 integration PASS + 2 SKIP. activity_basic.xml 의 T1 gate 가 branch (C) — layoutlib 의 `android.os.Build` 부재 + R.jar id 불일치 (Codex B3 confirmed) — HARD STOP, escalation 으로 종결.
     6	
     7	---
     8	
     9	## 1. 작업 범위
    10	
    11	W3D2 핸드오프의 **Option A** — sample-app `activity_basic.xml` (ConstraintLayout / MaterialButton 등 custom view) 의 layoutlib SUCCESS 렌더 unblock. AAR `classes.jar` URLClassLoader 적재 + `MinimalLayoutlibCallback.loadView` 확장.
    12	
    13	## 2. 변경 파일 (push 완료, ac06d8e..94c5b2a)
    14	
    15	### 2.1 신규 (4 main + 4 test = 8 파일)
    16	
    17	| 파일 | 책임 | LOC |
    18	|------|------|-----|
    19	| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt` | 모든 magic strings (paths/extensions/digest) 상수 | 30 |
    20	| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifest.kt` | `<module>/app/build/axp/runtime-classpath.txt` 파서 | 35 |
    21	| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt` | AAR ZIP 의 classes.jar 를 stable cache 로 atomic 추출 | 60 |
    22	| `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt` | manifest + AarExtractor + R.jar → URLClassLoader(parent=isolatedCL) | 50 |
    23	| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/{4 test files}` | 단위 17 테스트 (3+5+5+3) | ~370 |
    24	| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/RendererArgs.kt` | `SharedRendererBinding` 의 cache key data class | 15 |
    25	| `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackLoadViewTest.kt` | TrackingClassLoader 패턴, 5 단위 테스트 | 90 |
    26	
    27	### 2.2 수정 (production)
    28	
    29	- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/FixtureDiscovery.kt` — `locateModuleRoot` + `FIXTURE_MODULE_SUBPATH` + `locateInternalGeneric` 추가, 기존 3-arg `locateInternal` 은 1-line bridge.
    30	- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` — 생성자에 `viewClassLoaderProvider: () -> ClassLoader`. `loadView` 가 reflection + ITE unwrap. `findClass` + `hasAndroidXAppCompat=true` override 추가. W2D7 KDoc 보존.
    31	- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — 생성자에 `sampleAppModuleRoot: Path` 4번째 인자. `ensureSampleAppClassLoader()` lazy provider. callback 주입 wire.
    32	- `server/mcp-server/src/main/kotlin/dev/axp/mcp/CliConstants.kt` — `SAMPLE_APP_ROOT_FLAG`, `VALUE_BEARING_FLAGS`, `USAGE_LINE` 갱신.
    33	- `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` — `chooseRenderer` 가 `FixtureDiscovery.locateModuleRoot` 로 module root 결정.
    34	- `fixture/sample-app/app/build.gradle.kts` — `axpEmitClasspath` Gradle task + `afterEvaluate { tasks.named("assembleDebug").configure { finalizedBy(...) } }`.
    35	
    36	### 2.3 수정 (test infra)
    37	
    38	- `SharedLayoutlibRenderer.kt` / `SharedRendererBinding.kt` / `SharedRendererBindingTest.kt` (3 tests) — `Pair<Path,Path>` → `RendererArgs(distDir, fixtureRoot, sampleAppModuleRoot)`.
    39	- `SharedLayoutlibRendererIntegrationTest.kt` (4 sites) — `getOrCreate` signature 갱신.
    40	- `LayoutlibRendererTier3MinimalTest.kt` — companion sharedRenderer 에 sampleAppModuleRoot.
    41	- `MinimalLayoutlibCallbackTest.kt` (8 sites) — no-arg 호출을 `MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }` 로.
    42	- `SessionParamsFactoryTest.kt` (2 sites) — 동일.
    43	- `CliArgsTest.kt` — `--sample-app-root=value` 파싱 단위 테스트 +1.
    44	- `LayoutlibRendererIntegrationTest.kt` — `@Disabled` 갱신, SUCCESS assertion + `requireNotNull` 보존 (미래 enable 대비).
    45	
    46	## 3. 테스트 결과
    47	
    48	| 카테고리 | 전 (W3D2) | 후 (W3D3) | 변화 |
    49	|---|---|---|---|
    50	| Unit | 118 PASS | **142 PASS** | +24 (Task 1-6 의 신규 + CliArgs `--sample-app-root` + FixtureDiscovery locateModuleRoot 2) |
    51	| Integration PASS | 11 | **11** | 변화 없음 (W3D2 baseline 그대로) |
    52	| Integration SKIPPED | 2 | **2** | `tier3-glyph` (W4+) + `LayoutlibRendererIntegrationTest` (W3D3 branch C, carry) |
    53	| BUILD | SUCCESSFUL | SUCCESSFUL | — |
    54	
    55	## 4. 발견된 landmine / 새 carry
    56	
    57	### LM-W3D3-A (Q1 round 1 페어 컨버전스 empirical 정정)
    58	
    59	Spec round 1 페어 리뷰 (Codex+Claude convergent) 가 "Q1: `tasks.named("assembleDebug")` 가 lazy 라 `afterEvaluate` 불요" 라고 결론. **Round 2 plan-review (Claude empirical)** 에서 실측: AGP 8.x 의 `assembleDebug` 는 variant API 등록 시점 이후에야 존재 → top-level `tasks.named` 시점에 `UnknownTaskException`. **`afterEvaluate` 필수**. round 1 페어 reasoning 의 함정 — "lazy" 의 의미가 task 등록 시점이 아닌 task body 평가 시점에 한함.
    60	
    61	**교훈**: 페어 리뷰의 convergent 결정도 empirical 검증이 누락되면 잘못될 수 있다. Round 2 가 plan code 를 직접 시도해본 점이 잡아냄.
    62	
    63	### LM-W3D3-B (subagent kotlin.test 의존 부재)
    64	
    65	Task 1 implementer 가 처음 작성한 `ClassLoaderConstantsTest` 가 `import kotlin.test.assertEquals` 를 사용 → `:layoutlib-worker` test classpath 에 `kotlin.test` 없음 → 컴파일 FAIL. 기존 convention (`org.junit.jupiter.api.Assertions.*`) 로 교체. 후속 Task 2-6 prompts 에 명시적 instruction 추가하여 재발 방지.
    66	
    67	### LM-W3D3-C (StringBuilder bootstrap CL → null)
    68	
    69	Round 2 plan-review Codex valid finding: plan v1 의 Task 6 `findClass` 테스트가 `assertSame(parent, cls.classLoader)` — StringBuilder 같은 bootstrap-loaded 클래스의 `classLoader == null` 이라 fail. Round 2 v2 에서 `TrackingClassLoader` 패턴 (override `loadClass` + 호출 기록) 로 교체.
    70	
    71	### LM-W3D3-D (BLOCKER) — `android.os.Build` 부재
    72	
    73	**가장 큰 발견**: layoutlib 14.0.11 JAR 의 `android/os/` 하위에 `android.os.Build` 가 없다. 단 `_Original_Build*` prefix 변형만 존재. layoutlib 의 빌드 파이프라인이 의도적으로 rename — Android Studio 가 자체 SHIM 으로 `android.os.Build` 를 주입한다고 가정. 우리 환경 (Studio 외부 host JVM) 에는 SHIM 부재.
    74	
    75	ConstraintLayout 등 AAR 의 bytecode 가 `Build.VERSION.SDK_INT` 를 그대로 참조 → 우리 SampleAppClassLoader chain 으로 resolve 실패 → ClassNotFoundException → ERROR_INFLATION. **W3D3 가 본 회기에서 close 불가** — option α (ASM-based bytecode rewriting) 또는 option β (Build shim JAR + R.jar id 시드) 가 W3D4+ carry.
    76	
    77	진단 문서: `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md`.
    78	
    79	### LM-W3D3-E (R.jar real id ↔ callback generated id 불일치)
    80	
    81	Codex round 1 B3 가 spec 단계에서 surface 했고 spec §1.2 R3 가 "fidelity 손상, but SUCCESS 가능" 으로 축소 추정. 실측 결과 ERROR_INFLATION 직전에 발화 — SUCCESS 자체를 막는 confirmed blocker.
    82	
    83	해결: `MinimalLayoutlibCallback` 의 `getOrGenerateResourceId`/`resolveResourceId` 가 sample-app R.jar 의 R$* 클래스의 모든 static int 필드를 reflection enumerate 하여 byRef/byId 를 사전-populate (call this **L3-RJAR-ID-SEED** carry).
    84	
    85	## 5. 페어 리뷰 결과
    86	
    87	### Spec round 1 (architectural design tradeoff analysis)
    88	- Codex GPT-5.5 xhigh: NO_GO (4 blockers + 4 follow-ups). `spec-pair-review-round1-codex.md`.
    89	- Claude Opus 4.7: GO_WITH_FOLLOWUPS (1 blocker + 6 follow-ups). `spec-pair-review-round1-claude.md`.
    90	- 컨버전스: B1 (fixtureRoot 분리), F1 (findClass + hasAndroidXAppCompat), B4 (magic strings), Q1-Q5 (afterEvaluate 제거 — round 2 정정 대상).
    91	- Spec round 2 가 모두 반영.
    92	
    93	### Plan round 2 (plan-document review)
    94	- Codex GPT-5.5 xhigh: NO_GO (6 blockers + 4 follow-ups).
    95	- Claude Opus 4.7: GO_WITH_FOLLOWUPS (3 blockers + 7 follow-ups).
    96	- 컨버전스 11건 (Task 7+8 atomic merge, StringBuilder bootstrap CL, R.jar test 정정, RendererArgs data class, afterEvaluate 정정 — Q1 empirical, SessionParamsFactoryTest caller, T1 gate requireNotNull, branch C hard-stop, Task 3 corrupted AAR, KDoc 보존, CLI USAGE_LINE).
    97	- Plan v2 가 모두 반영. divergence: severity (NO_GO vs GO_WITH_FOLLOWUPS) 만 — direction 컨버전.
    98	
    99	### Implementation phase
   100	- Claude-only (CLAUDE.md "Implementation phase = Claude-only" 정책).
   101	- Subagent-driven 9 dispatches:
   102	  - Task 1: ClassLoaderConstants — DONE_WITH_CONCERNS (LM-W3D3-B).
   103	  - Task 2-6, 8: DONE.
   104	  - Task 7: DONE (atomic 11 files, 142 unit PASS).
   105	  - Task 9: BLOCKED branch (C) — orchestrator-level diagnosis 후 amend + escalation.
   106	
   107	## 6. 다음 세션 carry
   108	
   109	### Primary carry (sample-app unblock 지속)
   110	- **W3D3-FINISH**: option α (bytecode rewriting) 또는 option β (Build shim JAR + R.jar id 시드) 중 사용자 결정 후 진입. 진단 §5 enumeration 참조.
   111	- **L3-RJAR-ID-SEED**: callback id seeding (Codex B3 정공법) — option β 의 일부.
   112	
   113	### 기존 carry 유지
   114	- `tier3-glyph` (W4+ target).
   115	- `CLI-DIST-PATH` (W3D2 에서 close, 본 W3D3 가 `--sample-app-root` 추가 — `CliConstants.USAGE_LINE` 도 함께 갱신).
   116	- `TEST-INFRA-SHARED-RENDERER` (W3D2 에서 close).
   117	- `POST-W2D6-POM-RESOLVE` (F-6).
   118	- `W3-CLASSLOADER-AUDIT` (F-4) — 본 W3D3 가 부분적으로 진행했으나 Build SHIM 이슈로 full close 미달.
   119	
   120	## 7. 커맨드 이력 (재현용)
   121	
   122	```bash
   123	# Final gates (W3D3 종결 시점)
   124	./server/gradlew -p server test                                                # 142 unit PASS
   125	./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration    # 11 PASS + 2 SKIPPED
   126	./server/gradlew -p server build                                               # BUILD SUCCESSFUL
   127	
   128	# Smoke
   129	./server/mcp-server/build/install/mcp-server/bin/mcp-server --smoke            # "ok"
   130	
   131	# Sample-app manifest 생성 (Task 8 산물)
   132	(cd fixture/sample-app && ./gradlew :app:assembleDebug)                        # → app/build/axp/runtime-classpath.txt (55 라인)
   133	
   134	# Branch (C) 재현 (옵션 α/β 진입 전 baseline 확인용)
   135	# 1. LayoutlibRendererIntegrationTest 의 @Disabled 제거.
   136	# 2. ./server/gradlew -p server :layoutlib-worker:test --tests "...LayoutlibRendererIntegrationTest" -PincludeTags=integration
   137	# 3. Expected: ERROR_INFLATION + android.os.Build$VERSION CNFE + style 2130903769 lookup fail.
   138	```

exec
/bin/bash -lc 'git status --short && git rev-parse --show-toplevel && rg -n "ResourceType|ResourceNamespace|fromPackageName|MinimalLayoutlibCallback|register|getOrGenerateResourceId|AarExtractor|ClassLoaderConstants|LayoutlibRendererIntegrationTest" server/layoutlib-worker/src server/layoutlib-worker/build.gradle.kts fixture/sample-app/app/build.gradle.kts docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
?? docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md
/home/bh-mark-dev-desktop/workspace/android_xml_previewer
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:12:`activity_basic.xml` 의 `LayoutlibRendererIntegrationTest` (현재 `@Disabled`) 가 T1 gate 를 통과해야 함:
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:34:  ├─ AarExtractor.extract(aar)  ← classes.jar 그대로 추출
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:38:AarExtractor.extract(aar)
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:43:MinimalLayoutlibCallback.<init>
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:44:  └─ rJarLoader.let { RJarSymbolSeeder.seed(it, this::registerSymbol) }  ← 신규
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:103:| α4 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt` (수정) | classes.jar entries 를 stream 으로 읽고 각 .class 를 `AndroidClassRewriter.rewrite` 후 새 jar 에 write | +50 |
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:104:| α5 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt` (신규) | `seed(rJarPath, registerSymbol: (ResourceReference, Int) -> Unit)` — R.jar 의 모든 R$* 클래스를 walk + 각 정적 int 필드를 enumerate | 90 |
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:105:| α6 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarTypeMapping.kt` (신규) | R$type 의 simple name (e.g., "attr", "style", "styleable") → `com.android.resources.ResourceType` enum | 50 |
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:106:| α7 | `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` (수정) | 생성자에 `rJarSeeder: (MinimalLayoutlibCallback) -> Unit` 추가, init 에서 invoke. byRef/byId 에 사전 populate 가능하도록 `registerSymbol` private method 노출 (internal) | +25 |
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:108:| α9 | `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` (수정) | `@Disabled` 제거 (round 2 의 SUCCESS assertion 등은 그대로) | -5 |
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:112:| TC-α3 | `AarExtractorRewriteTest` | extract 결과 classes.jar 안의 각 .class 가 NAME_MAP 적용됐는지 Round-trip ZipFile 로 확인 | 100 |
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:113:| TC-α4 | `RJarSymbolSeederTest` | (a) 합성 R.jar 의 모든 R$* 클래스 walk / (b) static int 필드 register 됨 / (c) int[] 필드 (R$styleable) skip / (d) namespace + type 정확 추출 | 130 |
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:197:### 4.3 AarExtractor 수정
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:242:`CLASS_FILE_SUFFIX = ".class"` 추가 — `ClassLoaderConstants` 에.
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:249:import com.android.resources.ResourceType
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:252: * R$<simpleName> 의 simpleName → ResourceType 매핑.
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:253: * R.jar 안의 모든 inner class 이름을 ResourceType enum 으로 변환.
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:259:    private val MAPPING: Map<String, ResourceType> = mapOf(
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:260:        "anim" to ResourceType.ANIM,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:261:        "animator" to ResourceType.ANIMATOR,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:262:        "array" to ResourceType.ARRAY,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:263:        "attr" to ResourceType.ATTR,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:264:        "bool" to ResourceType.BOOL,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:265:        "color" to ResourceType.COLOR,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:266:        "dimen" to ResourceType.DIMEN,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:267:        "drawable" to ResourceType.DRAWABLE,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:268:        "font" to ResourceType.FONT,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:269:        "fraction" to ResourceType.FRACTION,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:270:        "id" to ResourceType.ID,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:271:        "integer" to ResourceType.INTEGER,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:272:        "interpolator" to ResourceType.INTERPOLATOR,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:273:        "layout" to ResourceType.LAYOUT,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:274:        "menu" to ResourceType.MENU,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:275:        "mipmap" to ResourceType.MIPMAP,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:276:        "navigation" to ResourceType.NAVIGATION,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:277:        "plurals" to ResourceType.PLURALS,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:278:        "raw" to ResourceType.RAW,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:279:        "string" to ResourceType.STRING,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:280:        "style" to ResourceType.STYLE,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:281:        "styleable" to ResourceType.STYLEABLE,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:282:        "transition" to ResourceType.TRANSITION,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:283:        "xml" to ResourceType.XML,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:286:    fun fromSimpleName(simpleName: String): ResourceType? = MAPPING[simpleName]
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:295:import com.android.ide.common.rendering.api.ResourceNamespace
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:297:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.CLASS_FILE_SUFFIX
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:319:        register: (ResourceReference, Int) -> Unit,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:332:                seedClass(rJarLoader, internalName.replace('/', '.'), packageName, resourceType, register)
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:363:        type: com.android.resources.ResourceType,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:364:        register: (ResourceReference, Int) -> Unit,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:374:        val namespace = ResourceNamespace.fromPackageName(packageName)
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:388:            register(ResourceReference(namespace, type, field.name), value)
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:394:### 4.6 MinimalLayoutlibCallback 수정
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:397:class MinimalLayoutlibCallback(
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:403:    private val initializer: (registerSymbol: (ResourceReference, Int) -> Unit) -> Unit,
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:439:    override fun getOrGenerateResourceId(ref: ResourceReference): Int {
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:459:    val seeder: (((ResourceReference, Int) -> Unit) -> Unit) = { register ->
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:461:        val rJarPath = sampleAppModuleRoot.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH)
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:462:        RJarSymbolSeeder.seed(rJarPath, sampleAppCL, register)
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:467:        callback = MinimalLayoutlibCallback(
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:477:호출 부 (`MinimalLayoutlibCallbackTest` / `SessionParamsFactoryTest` 등 기존 8+2 sites): `MinimalLayoutlibCallback({ cl }, { /* no-op */ })` — initializer 가 register 를 호출하지 않아 기존 byRef/byId 가 빈 상태로 시작 (W3D3 round 2 의 동작과 동일).
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:479:### 4.8 LayoutlibRendererIntegrationTest 활성화
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:495:| Rα-5 | sample-app 의 com.fixture.R 같은 application R 의 id 가 0x7F0A_xxxx 와 겹침 | 동일 ResourceReference (namespace + type + name) 라면 register 가 byRef 에 같은 entry 한 번. 다른 ref 라면 두 id 가 같은 byRef 매핑이 안 됨 — 후순위 등록이 byRef 에서 conflict 가능. **mitigation**: register 가 `if byRef.contains(ref) ignore` 정책. (단, sample-app 환경에서 발생 불가능 — 0x7F0A_0000 아래 R.jar id 만 존재.) |
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:518:seeder 가 `R$styleable.MaterialButton` (= `int[]`) 을 skip. layoutlib 의 RenderResources 가 styleable 을 어떻게 lookup 하나? styleable 은 attr id 의 array 이므로 attr 자체가 등록되어 있으면 충분 — array 자체를 register 하지 않아도 layoutlib 의 `obtainStyledAttributes(set, attrs[])` 가 array 의 각 attr id 로 lookup. 검증: layoutlib 14.x 의 `BridgeContext.obtainStyledAttributes` 가 attr id array 를 caller 가 준 것으로 사용하므로 array 자체 등록 불요. **Codex 검증 권장.**
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:527:// LayoutlibRendererIntegrationTest 가 expect:
fixture/sample-app/app/build.gradle.kts:52:val axpEmitClasspath = tasks.register("axpEmitClasspath") {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifest.kt:3:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifest.kt:4:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.JAR_EXTENSION
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifest.kt:5:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt:3:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt:4:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt:5:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt:6:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTRACTED_JAR_SUFFIX
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt:7:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.SHA1_DIGEST_NAME
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt:8:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.TEMP_JAR_SUFFIX
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt:25:object AarExtractor {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt:3:internal object ClassLoaderConstants {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt:7:    /** AarExtractor cacheRoot (= sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)) 의 base. */
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt:10:    /** AarExtractor 의 stable 캐시 서브디렉토리 (cacheRoot 기준 상대). */
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt:22:    /** AarExtractor 의 추출 결과 파일 suffix (artifactName 뒤에 붙음). */
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt:25:    /** AarExtractor 의 atomic write 용 임시 파일 suffix. */
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt:28:    /** AarExtractor 의 캐시 키용 path digest 알고리즘. */
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt:3:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_BASE_RELATIVE_PATH
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt:4:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt:5:import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt:15: * AAR 은 AarExtractor 로 classes.jar 를 추출 후 그 경로를 URL 로 사용.
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt:34:                    AarExtractor.extract(entry, cacheRoot) ?: continue
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:4:import com.android.ide.common.rendering.api.ResourceNamespace
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:9:import com.android.resources.ResourceType
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:28:            ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, defaultThemeName),
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:36:        if (ref.namespace != ResourceNamespace.ANDROID) return null
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:41:        if (ref.namespace != ResourceNamespace.ANDROID) return null
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:69:            TAG_DIMEN -> out += simpleValue(p, ResourceType.DIMEN)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:70:            TAG_COLOR -> out += simpleValue(p, ResourceType.COLOR)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:71:            TAG_INTEGER -> out += simpleValue(p, ResourceType.INTEGER)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:72:            TAG_BOOL -> out += simpleValue(p, ResourceType.BOOL)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:73:            TAG_STRING -> out += simpleValue(p, ResourceType.STRING)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:76:                val resType = typeAttr?.let { ResourceType.fromXmlValue(it) }
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:129:    private fun simpleValue(p: KXmlParser, type: ResourceType): ParsedEntry.SimpleValue {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedEntry.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedEntry.kt:20:        val type: ResourceType,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:5:import com.android.ide.common.rendering.api.ResourceNamespace
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:12:import com.android.resources.ResourceType
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:18: * scope: framework (ResourceNamespace.ANDROID) 만. library/app 은 별도.
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:21:    private val byType: Map<ResourceType, Map<String, ResourceValue>>,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:26:    fun getResource(type: ResourceType, name: String): ResourceValue? =
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:34:    fun typeCount(type: ResourceType): Int = byType[type]?.size ?: 0
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:50:            val byTypeMut = mutableMapOf<ResourceType, MutableMap<String, ResourceValue>>()
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:58:                    val ref = ResourceReference(ResourceNamespace.ANDROID, e.type, e.name)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:65:                        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, e.name)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:77:                val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, def.name)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:81:                        StyleItemResourceValueImpl(ResourceNamespace.ANDROID, it2.name, it2.value, null)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:11:import dev.axp.layoutlib.worker.session.MinimalLayoutlibCallback
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:41: *  - 커스텀 뷰 (ConstraintLayout, MaterialButton 등) 는 `MinimalLayoutlibCallback.loadView`
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:174:            callback = MinimalLayoutlibCallback { ensureSampleAppClassLoader() },
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:236:     * Bridge.init 이 끝난 후 (= isolated CL 가 준비된 후) MinimalLayoutlibCallback 의
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:29:     * @param callback LayoutlibCallback — 호출자 주입 필수 (production 은 `MinimalLayoutlibCallback()`).
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:18: *  - resource id 양방향 map (getOrGenerateResourceId ↔ resolveResourceId). 스레드-세이프.
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:32:class MinimalLayoutlibCallback(
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt:41:    override fun getOrGenerateResourceId(ref: ResourceReference): Int {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapter.kt:4:import com.android.ide.common.rendering.api.ResourceNamespace
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapter.kt:28:    override fun getLayoutNamespace(): ResourceNamespace = ResourceNamespace.RES_AUTO
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt:22:class SharedLayoutlibRendererIntegrationTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:17:        val aar = parent.resolve("$name${ClassLoaderConstants.AAR_EXTENSION}")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:19:            zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:27:        val jar = parent.resolve("$name${ClassLoaderConstants.JAR_EXTENSION}")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:42:        val mfDir = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH).parent
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:44:        val rJarDir = root.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH).parent
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:46:        val rJar = root.resolve(ClassLoaderConstants.R_JAR_RELATIVE_PATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:56:        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:67:        val mfDir = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH).parent
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:71:            root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:83:        Files.writeString(root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH), jar.toAbsolutePath().toString())
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoaderTest.kt:85:        assertTrue(cl.urls.any { it.toString().endsWith("passthru${ClassLoaderConstants.JAR_EXTENSION}") })
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifestTest.kt:26:        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifestTest.kt:35:        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifestTest.kt:46:        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClasspathManifestTest.kt:56:        val mf = root.resolve(ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:18: *  - MinimalLayoutlibCallback.loadView 가 reflection-instantiate.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt:34:class LayoutlibRendererIntegrationTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt:7:class ClassLoaderConstantsTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt:11:        assertEquals("app/build/axp/runtime-classpath.txt", ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt:12:        assertTrue(!ClassLoaderConstants.MANIFEST_RELATIVE_PATH.startsWith("/"))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt:17:        assertTrue(ClassLoaderConstants.AAR_EXTENSION.startsWith("."))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt:18:        assertTrue(ClassLoaderConstants.JAR_EXTENSION.startsWith("."))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt:19:        assertTrue(ClassLoaderConstants.TEMP_JAR_SUFFIX.startsWith("."))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt:25:        assertEquals(expected, ClassLoaderConstants.R_JAR_RELATIVE_PATH)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:15:class AarExtractorTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:18:        val aar = parent.resolve("$name${ClassLoaderConstants.AAR_EXTENSION}")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:25:                zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:37:        val extracted = AarExtractor.extract(aar, cacheRoot)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:40:        assertTrue(extracted!!.fileName.toString().endsWith(ClassLoaderConstants.EXTRACTED_JAR_SUFFIX))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:41:        val tmpName = "lib${ClassLoaderConstants.TEMP_JAR_SUFFIX}"
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:51:        val first = AarExtractor.extract(aar, cacheRoot)!!
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:54:        val second = AarExtractor.extract(aar, cacheRoot)!!
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:63:        assertNull(AarExtractor.extract(aar, cacheRoot))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:70:        assertThrows<IllegalArgumentException> { AarExtractor.extract(missing, cacheRoot) }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:76:        val corrupted = root.resolve("broken${ClassLoaderConstants.AAR_EXTENSION}")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractorTest.kt:78:        assertThrows<java.util.zip.ZipException> { AarExtractor.extract(corrupted, cacheRoot) }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:3:import com.android.ide.common.rendering.api.ResourceNamespace
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:42:            callback = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() },
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:65:            callback = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() },
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:80:        assertEquals(ResourceNamespace.ANDROID, theme.namespace)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapterTest.kt:3:import com.android.ide.common.rendering.api.ResourceNamespace
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/LayoutPullParserAdapterTest.kt:52:        assertEquals(ResourceNamespace.RES_AUTO, p.layoutNamespace)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:3:import com.android.ide.common.rendering.api.ResourceNamespace
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:5:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:15:        ParsedEntry.SimpleValue(ResourceType.DIMEN, "config_scrollbarSize", "4dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:43:        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:52:        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:59:        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.DIMEN, "config_scrollbarSize")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:3:import com.android.ide.common.rendering.api.ResourceNamespace
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:5:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:13: * W2D7-RENDERSESSION — MinimalLayoutlibCallback 의 resource id 양방향 맵 + 기본값 계약 검증.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:15: * LayoutlibCallback 은 abstract class. Bridge 내부가 callback.getOrGenerateResourceId 로 int
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:18:class MinimalLayoutlibCallbackTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:21:    fun `getOrGenerateResourceId returns stable id across calls`() {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:22:        val cb = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:23:        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:24:        val first = cb.getOrGenerateResourceId(ref)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:25:        val second = cb.getOrGenerateResourceId(ref)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:31:        val cb = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:32:        val title = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:33:        val body = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "body")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:34:        assertNotEquals(cb.getOrGenerateResourceId(title), cb.getOrGenerateResourceId(body))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:38:    fun `resolveResourceId returns registered reference`() {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:39:        val cb = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:40:        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:41:        val id = cb.getOrGenerateResourceId(ref)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:47:        val cb = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:53:        val cb = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:59:        val cb = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:65:        val cb = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:71:        val cb = MinimalLayoutlibCallback { ClassLoader.getSystemClassLoader() }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:25:        assertEquals(ResourceType.DIMEN, d.type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:38:        assertEquals(ResourceType.COLOR, c.type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:53:        assertEquals(ResourceType.INTEGER, list[0].type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:54:        assertEquals(ResourceType.BOOL, list[1].type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:55:        assertEquals(ResourceType.STRING, list[2].type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:67:        assertEquals(ResourceType.DIMEN, e.type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:14:            ParsedEntry.SimpleValue(ResourceType.DIMEN, "config_scrollbarSize", "4dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:15:            ParsedEntry.SimpleValue(ResourceType.COLOR, "material_blue_grey_800", "#ff37474f"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:16:            ParsedEntry.SimpleValue(ResourceType.DIMEN, "action_bar_size", "56dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:20:        val d = bundle.getResource(ResourceType.DIMEN, "config_scrollbarSize")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:24:        val c = bundle.getResource(ResourceType.COLOR, "material_blue_grey_800")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:27:        assertNull(bundle.getResource(ResourceType.DIMEN, "does_not_exist"))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:67:            ParsedEntry.SimpleValue(ResourceType.DIMEN, "x", "1dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:68:            ParsedEntry.SimpleValue(ResourceType.DIMEN, "x", "2dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:71:        assertEquals("2dp", bundle.getResource(ResourceType.DIMEN, "x")!!.value)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackLoadViewTest.kt:10:class MinimalLayoutlibCallbackLoadViewTest {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackLoadViewTest.kt:24:    private fun newCallback(cl: ClassLoader): MinimalLayoutlibCallback =
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackLoadViewTest.kt:25:        MinimalLayoutlibCallback { cl }
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:34:        val d = bundle.getResource(ResourceType.DIMEN, "config_scrollbarSize")

codex
The current tree already has `ResourceType` usages in worker sources, so that part is probably classpath-safe; the more interesting checks are the exact callback map behavior and whether the proposed seeder can actually load all R classes.
exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt | sed -n '1,240p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
    11	import java.lang.reflect.InvocationTargetException
    12	import java.util.concurrent.atomic.AtomicInteger
    13	
    14	/**
    15	 * W2D7-RENDERSESSION — LayoutlibCallback 의 최소 구현체.
    16	 *
    17	 * 구현 범위 (activity_minimal.xml 기준):
    18	 *  - resource id 양방향 map (getOrGenerateResourceId ↔ resolveResourceId). 스레드-세이프.
    19	 *  - loadView: 커스텀 뷰 요구 시 즉시 UnsupportedOperationException — 프레임워크 위젯은
    20	 *    Bridge 내부에서 처리되므로 호출되지 않음이 불변식 (custom view = L3 W3+).
    21	 *  - getAdapterBinding: ListView/Spinner 데이터 바인딩 없음 → null.
    22	 *  - getActionBarCallback: 기본 ActionBarCallback() — setForceNoDecor 로 어차피 action bar 미표시.
    23	 *  - XmlParserFactory 메서드: 본 W2D7 fixture 에서 호출될 일 없으나 interface 계약상 KXmlParser 반환.
    24	 *
    25	 * W3D3-L3-CLASSLOADER (round 2 페어 리뷰 반영) — 변경 사항:
    26	 *  - 생성자에 viewClassLoaderProvider lazy lambda 추가 (Q4 lazy build).
    27	 *  - loadView: viewClassLoaderProvider 로부터 lazy 로 CL 받아 reflection-instantiate.
    28	 *    InvocationTargetException 의 cause 를 unwrap (Q3, Codex 입장).
    29	 *  - findClass: BridgeInflater.findCustomInflater 의 AppCompat 자동 치환 활성화 (F1).
    30	 *  - hasAndroidXAppCompat: true (F1) — sample-app 의존 그래프 보유.
    31	 */
    32	class MinimalLayoutlibCallback(
    33	    private val viewClassLoaderProvider: () -> ClassLoader,
    34	) : LayoutlibCallback() {
    35	
    36	    private val nextId = AtomicInteger(FIRST_ID)
    37	    private val byRef = mutableMapOf<ResourceReference, Int>()
    38	    private val byId = mutableMapOf<Int, ResourceReference>()
    39	
    40	    @Synchronized
    41	    override fun getOrGenerateResourceId(ref: ResourceReference): Int {
    42	        byRef[ref]?.let { return it }
    43	        val id = nextId.getAndIncrement()
    44	        byRef[ref] = id
    45	        byId[id] = ref
    46	        return id
    47	    }
    48	
    49	    @Synchronized
    50	    override fun resolveResourceId(id: Int): ResourceReference? = byId[id]
    51	
    52	    override fun loadView(name: String, constructorSignature: Array<out Class<*>>?, constructorArgs: Array<out Any>?): Any {
    53	        val cls = viewClassLoaderProvider().loadClass(name)
    54	        val sig = constructorSignature ?: emptyArray()
    55	        val args = constructorArgs ?: emptyArray()
    56	        val ctor = cls.getDeclaredConstructor(*sig)
    57	        ctor.isAccessible = true
    58	        try
    59	        {
    60	            return ctor.newInstance(*args)
    61	        }
    62	        catch (ite: InvocationTargetException)
    63	        {
    64	            // Q3: cause unwrap — layoutlib 의 BridgeInflater 가 InflateException 으로 wrap 함.
    65	            throw ite.cause ?: ite
    66	        }
    67	    }
    68	
    69	    override fun findClass(name: String): Class<*> {
    70	        return viewClassLoaderProvider().loadClass(name)
    71	    }
    72	
    73	    override fun hasAndroidXAppCompat(): Boolean = true
    74	
    75	    override fun getParser(layoutResource: ResourceValue?): ILayoutPullParser? = null
    76	
    77	    override fun getAdapterBinding(cookie: Any?, attributes: Map<String, String>): AdapterBinding? = null
    78	
    79	    override fun getActionBarCallback(): ActionBarCallback = ActionBarCallback()
    80	
    81	    override fun createXmlParser(): XmlPullParser = buildKxml()
    82	
    83	    override fun createXmlParserForFile(fileName: String): XmlPullParser = buildKxml()
    84	
    85	    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser = buildKxml()
    86	
    87	    override fun getApplicationId(): String = APPLICATION_ID
    88	
    89	    private fun buildKxml(): XmlPullParser = KXmlParser().also {
    90	        it.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    91	    }
    92	
    93	    companion object {
    94	        /** 생성 id 기저. 0x7F 패밀리 (android studio 관례) 의 하위. */
    95	        private const val FIRST_ID = 0x7F0A_0000
    96	
    97	        /** Bridge.mProjectKey lookup 등 내부 진단에 쓰일 수 있는 안정적 app id. */
    98	        private const val APPLICATION_ID = "axp.render"
    99	    }
   100	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AarExtractor.kt | sed -n '1,240p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.classloader
     2	
     3	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
     4	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
     5	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
     6	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTRACTED_JAR_SUFFIX
     7	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.SHA1_DIGEST_NAME
     8	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.TEMP_JAR_SUFFIX
     9	import java.nio.file.Files
    10	import java.nio.file.Path
    11	import java.nio.file.StandardCopyOption.ATOMIC_MOVE
    12	import java.nio.file.StandardCopyOption.REPLACE_EXISTING
    13	import java.security.MessageDigest
    14	import java.util.zip.ZipFile
    15	import kotlin.io.path.isRegularFile
    16	import kotlin.io.path.outputStream
    17	
    18	/**
    19	 * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출. mtime 기반 idempotent + atomic write.
    20	 *
    21	 * Cache 위치: `<cacheRoot>/aar-classes/<sha1(absPath)>/<artifactName>.jar`.
    22	 * AAR 안에 classes.jar 없는 경우 (resource-only) → null.
    23	 * 손상된 ZIP → ZipException pass-through.
    24	 */
    25	object AarExtractor {
    26	
    27	    fun extract(aarPath: Path, cacheRoot: Path): Path? {
    28	        require(aarPath.isRegularFile()) { "AAR 누락: $aarPath" }
    29	        val key = sha1(aarPath.toAbsolutePath().toString())
    30	        val artifactName = aarPath.fileName.toString().removeSuffix(AAR_EXTENSION)
    31	        val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR).resolve(key)
    32	        Files.createDirectories(outDir)
    33	        val outJar = outDir.resolve(artifactName + EXTRACTED_JAR_SUFFIX)
    34	
    35	        val aarMtime = Files.getLastModifiedTime(aarPath).toMillis()
    36	        if (outJar.isRegularFile() &&
    37	            Files.getLastModifiedTime(outJar).toMillis() >= aarMtime)
    38	        {
    39	            return outJar
    40	        }
    41	
    42	        val tmpJar = outDir.resolve(artifactName + TEMP_JAR_SUFFIX)
    43	        ZipFile(aarPath.toFile()).use { zip ->
    44	            val entry = zip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
    45	            zip.getInputStream(entry).use { input ->
    46	                tmpJar.outputStream().use { output -> input.copyTo(output) }
    47	            }
    48	        }
    49	        Files.move(tmpJar, outJar, ATOMIC_MOVE, REPLACE_EXISTING)
    50	        return outJar
    51	    }
    52	
    53	    private fun sha1(s: String): String {
    54	        val md = MessageDigest.getInstance(SHA1_DIGEST_NAME)
    55	        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    56	    }
    57	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstants.kt | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.classloader
     2	
     3	internal object ClassLoaderConstants {
     4	    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 sampleAppModuleRoot-relative 경로. */
     5	    const val MANIFEST_RELATIVE_PATH = "app/build/axp/runtime-classpath.txt"
     6	
     7	    /** AarExtractor cacheRoot (= sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)) 의 base. */
     8	    const val AAR_CACHE_BASE_RELATIVE_PATH = "app/build/axp"
     9	
    10	    /** AarExtractor 의 stable 캐시 서브디렉토리 (cacheRoot 기준 상대). */
    11	    const val AAR_CACHE_RELATIVE_DIR = "aar-classes"
    12	
    13	    /** AAR ZIP 안에서 JVM 바이트코드 JAR 의 표준 entry 이름. */
    14	    const val AAR_CLASSES_JAR_ENTRY = "classes.jar"
    15	
    16	    /** AAR file 확장자 (manifest 검증 + 추출 분기). */
    17	    const val AAR_EXTENSION = ".aar"
    18	
    19	    /** JAR file 확장자 (manifest 검증). */
    20	    const val JAR_EXTENSION = ".jar"
    21	
    22	    /** AarExtractor 의 추출 결과 파일 suffix (artifactName 뒤에 붙음). */
    23	    const val EXTRACTED_JAR_SUFFIX = ".jar"
    24	
    25	    /** AarExtractor 의 atomic write 용 임시 파일 suffix. */
    26	    const val TEMP_JAR_SUFFIX = ".jar.tmp"
    27	
    28	    /** AarExtractor 의 캐시 키용 path digest 알고리즘. */
    29	    const val SHA1_DIGEST_NAME = "SHA-1"
    30	
    31	    /**
    32	     * AGP 8.x 가 emit 하는 통합 R.jar 경로 (compile_and_runtime_not_namespaced_r_class_jar variant).
    33	     * 본 경로는 AGP minor 버전 변경 시 깨질 수 있으나 8.x 안정 — 변경 시 본 상수만 갱신.
    34	     */
    35	    const val R_JAR_RELATIVE_PATH =
    36	        "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
    37	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt | sed -n '140,270p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
   140	            } catch (_: Throwable) {}
   141	        })
   142	    }
   143	
   144	    /**
   145	     * W2D7: 실 Bridge.createSession → RenderSession.render → BufferedImage → PNG 경로.
   146	     *
   147	     * 흐름:
   148	     *  1. fixture 경로에서 XML 로드 → LayoutPullParserAdapter.
   149	     *  2. SessionParamsFactory.build(parser) → SessionParams.
   150	     *  3. bridge.createSession(params) 를 reflection 으로 호출 (bridge 는 isolated CL 에서 로드된
   151	     *     인스턴스; SessionParams 는 system CL 의 동일 클래스 타입 — parent-first delegation 이
   152	     *     동일 Class identity 를 보장).
   153	     *  4. session.render(timeout) → result.isSuccess 체크.
   154	     *  5. session.image → ImageIO.write PNG → bytes.
   155	     *  6. 무조건 session.dispose().
   156	     *
   157	     * 실패 시 (result non-success, exception, fixture 없음 이외) → null 반환 → fallback.
   158	     */
   159	    private fun renderViaLayoutlib(layoutName: String): ByteArray? {
   160	        val layoutPath = fixtureRoot.resolve(layoutName)
   161	        require(layoutPath.toFile().isFile) {
   162	            "fixture 레이아웃을 찾을 수 없음: $layoutPath"
   163	        }
   164	
   165	        val parser = LayoutPullParserAdapter.fromFile(layoutPath)
   166	        // W3D1 3b-values: framework resource VALUE loader 가 10 XML 을 파싱하여 RenderResources 에 주입.
   167	        // JVM-wide cache 라 첫 호출만 파싱 비용 발생.
   168	        val bundle = FrameworkResourceValueLoader.loadOrGet(
   169	            distDir.resolve(ResourceLoaderConstants.DATA_DIR)
   170	        )
   171	        val resources = FrameworkRenderResources(bundle, SessionConstants.DEFAULT_FRAMEWORK_THEME)
   172	        val params: SessionParams = SessionParamsFactory.build(
   173	            layoutParser = parser,
   174	            callback = MinimalLayoutlibCallback { ensureSampleAppClassLoader() },
   175	            resources = resources,
   176	        )
   177	
   178	        val bridge = bridgeInstance ?: return null
   179	
   180	        // createSession 은 bridge 클래스에서 선언. parent-first 덕분에 인자/반환 타입이 system CL 의
   181	        // SessionParams / RenderSession 과 동일.
   182	        // 페어 리뷰 (Codex + Claude convergent): parameterCount==1 만 매칭하면 향후 layoutlib 이
   183	        // 다른 1-arg createSession 오버로드를 추가할 때 잘못된 메서드를 선택할 수 있으므로, 인자
   184	        // 타입을 SessionParams 로 명시 검증.
   185	        val createSession = bridge.javaClass.methods.firstOrNull {
   186	            it.name == BRIDGE_CREATE_SESSION &&
   187	                it.parameterCount == BRIDGE_CREATE_SESSION_PARAM_COUNT &&
   188	                it.parameterTypes.singleOrNull()?.name == SessionParams::class.java.name
   189	        } ?: return null
   190	
   191	        val session = try {
   192	            createSession.invoke(bridge, params) as? RenderSession ?: return null
   193	        } catch (t: Throwable) {
   194	            // Bridge 내부 예외 — fallback 으로 위임.
   195	            t.printStackTrace(System.err)
   196	            return null
   197	        }
   198	
   199	        // createSession 이 inflate 까지 시도. 실패 시 session.result 에 상태가 담긴다.
   200	        // 3b-arch hook: tests 가 architecture-positive evidence 로 사용.
   201	        val initialResult = session.result
   202	        lastCreateSessionResult = initialResult
   203	        initialResult?.let {
   204	            System.err.println(
   205	                "[LayoutlibRenderer] createSession result: status=${it.status} " +
   206	                    "msg=${it.errorMessage} exc=${it.exception?.javaClass?.simpleName}"
   207	            )
   208	        }
   209	
   210	        try {
   211	            val result = session.render(SessionConstants.RENDER_TIMEOUT_MS)
   212	            lastRenderResult = result
   213	            if (!result.isSuccess) {
   214	                System.err.println(
   215	                    "[LayoutlibRenderer] RenderSession.render failed: status=${result.status} " +
   216	                        "msg=${result.errorMessage} exc=${result.exception?.javaClass?.simpleName}"
   217	                )
   218	                return null
   219	            }
   220	            val image: BufferedImage = session.image ?: return null
   221	            val baos = ByteArrayOutputStream()
   222	            ImageIO.write(image, IMAGE_FORMAT_PNG, baos)
   223	            return baos.toByteArray()
   224	        } catch (t: Throwable) {
   225	            t.printStackTrace(System.err)
   226	            return null
   227	        } finally {
   228	            try {
   229	                session.dispose()
   230	            } catch (_: Throwable) {}
   231	        }
   232	    }
   233	
   234	    /**
   235	     * W3D3 (round-2 페어 B1): sample-app 의 dex/aar 클래스로더는 lazy 하게 build.
   236	     * Bridge.init 이 끝난 후 (= isolated CL 가 준비된 후) MinimalLayoutlibCallback 의
   237	     * loadView 가 처음 호출되는 시점에 한 번 빌드된다.
   238	     */
   239	    @Synchronized
   240	    private fun ensureSampleAppClassLoader(): ClassLoader
   241	    {
   242	        sampleAppClassLoader?.let { return it.classLoader }
   243	        val isolated = classLoader ?: error("Bridge 가 init 안 됨 (initBridge 가 먼저 실행되어야 함)")
   244	        val built = SampleAppClassLoader.build(sampleAppModuleRoot, isolated)
   245	        sampleAppClassLoader = built
   246	        return built.classLoader
   247	    }
   248	
   249	    private class NoopLogHandler : InvocationHandler {
   250	        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
   251	            return when (method.returnType.name) {
   252	                "void" -> null
   253	                "boolean" -> false
   254	                else -> if (Modifier.isStatic(method.modifiers)) null
   255	                        else if (method.returnType.isPrimitive) 0
   256	                        else null
   257	            }
   258	        }
   259	    }
   260	
   261	    companion object {
   262	        private const val BRIDGE_CREATE_SESSION = "createSession"
   263	
   264	        /** `createSession(SessionParams)` 의 parameter count — strict 리플렉션 매칭용. */
   265	        private const val BRIDGE_CREATE_SESSION_PARAM_COUNT = 1
   266	    }
   267	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/build.gradle.kts | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/SampleAppClassLoader.kt | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.classloader
     2	
     3	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_BASE_RELATIVE_PATH
     4	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
     5	import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
     6	import java.net.URL
     7	import java.net.URLClassLoader
     8	import java.nio.file.Files
     9	import java.nio.file.Path
    10	
    11	/**
    12	 * sample-app 의 runtime classpath (resolved AAR + transitive JAR + R.jar) 로부터
    13	 * URLClassLoader 를 구성. parent = layoutlib isolatedClassLoader (android.view.* 보유).
    14	 *
    15	 * AAR 은 AarExtractor 로 classes.jar 를 추출 후 그 경로를 URL 로 사용.
    16	 * Resource-only AAR (classes.jar 없음) 은 silently skip.
    17	 */
    18	class SampleAppClassLoader private constructor(
    19	    val classLoader: ClassLoader,
    20	    val urls: List<URL>,
    21	) {
    22	    companion object {
    23	
    24	        fun build(sampleAppModuleRoot: Path, parent: ClassLoader): SampleAppClassLoader {
    25	            val manifest = SampleAppClasspathManifest.read(sampleAppModuleRoot)
    26	            val cacheRoot = sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)
    27	            val urls = mutableListOf<URL>()
    28	
    29	            for (entry in manifest)
    30	            {
    31	                val asString = entry.toString()
    32	                val jarPath = if (asString.endsWith(AAR_EXTENSION))
    33	                {
    34	                    AarExtractor.extract(entry, cacheRoot) ?: continue
    35	                }
    36	                else
    37	                {
    38	                    entry
    39	                }
    40	                urls += jarPath.toUri().toURL()
    41	            }
    42	
    43	            val rJar = sampleAppModuleRoot.resolve(R_JAR_RELATIVE_PATH)
    44	            require(Files.isRegularFile(rJar))
    45	            {
    46	                "sample-app R.jar 누락: $rJar — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 필요"
    47	            }
    48	            urls += rJar.toUri().toURL()
    49	
    50	            val cl = URLClassLoader(urls.toTypedArray(), parent)
    51	            return SampleAppClassLoader(cl, urls.toList())
    52	        }
    53	    }
    54	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker
     2	
     3	import com.android.ide.common.rendering.api.Result
     4	import org.junit.jupiter.api.Assertions.assertEquals
     5	import org.junit.jupiter.api.Assertions.assertTrue
     6	import org.junit.jupiter.api.Assumptions.assumeTrue
     7	import org.junit.jupiter.api.Disabled
     8	import org.junit.jupiter.api.Tag
     9	import org.junit.jupiter.api.Test
    10	import java.nio.file.Path
    11	
    12	/**
    13	 * Tier3 pre-canonical integration test — activity_basic.xml (ConstraintLayout / MaterialButton 포함)
    14	 * 의 full render 를 기대.
    15	 *
    16	 * W3D3-L3-CLASSLOADER (round 2 페어 리뷰 반영):
    17	 *  - SampleAppClassLoader 가 sample-app 의 AAR + R.jar 를 host JVM 에 적재.
    18	 *  - MinimalLayoutlibCallback.loadView 가 reflection-instantiate.
    19	 *  - T1 gate (SUCCESS + PNG > 1000) 통과 시 W3D3 deliverable close.
    20	 *
    21	 * **W3D3 status: BLOCKED (branch (C) — `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md`)**
    22	 *  - layoutlib 14.0.11 JAR 이 `android.os.Build` 자체를 포함하지 않음 (`_Original_Build*` prefix 만 존재).
    23	 *    Studio 외부 환경에서 SHIM 부재 → AAR 의 Build.VERSION.SDK_INT 참조가 ClassNotFoundException.
    24	 *  - 추가로 R.jar 의 real id (e.g., 2130903769) ↔ callback generated id (0x7F0A_xxxx) 불일치
    25	 *    → style resolve 실패 (Codex round 1 B3 confirmed).
    26	 *  - 본 테스트의 acceptance gate, requireNotNull, SUCCESS assertion 은 향후 옵션 α (bytecode rewriting)
    27	 *    또는 옵션 β (Build shim JAR + R.jar id 시드) 가 land 된 후 enable 될 수 있도록 보존.
    28	 */
    29	@Tag("integration")
    30	@Disabled(
    31	    "W3D3 branch (C) — layoutlib 의 android.os.Build 부재 + R.jar id 불일치. " +
    32	        "carry: docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md 참조.",
    33	)
    34	class LayoutlibRendererIntegrationTest {
    35	
    36	    @Test
    37	    fun `tier3-basic — activity_basic renders SUCCESS with non-empty PNG`() {
    38	        val dist = locateDistDir()
    39	        val layoutRoot = locateFixtureRoot()
    40	        val moduleRoot = locateSampleAppModuleRoot()
    41	        val renderer = SharedLayoutlibRenderer.getOrCreate(
    42	            distDir = dist,
    43	            fixtureRoot = layoutRoot,
    44	            sampleAppModuleRoot = moduleRoot,
    45	            fallback = null,
    46	        )
    47	        val bytes = renderer.renderPng("activity_basic.xml")
    48	
    49	        assertEquals(
    50	            Result.Status.SUCCESS,
    51	            renderer.lastSessionResult?.status,
    52	            "render status SUCCESS 여야 함",
    53	        )
    54	        assertTrue(bytes.size > MIN_RENDERED_PNG_BYTES, "PNG bytes 가 placeholder 보다 큼: ${bytes.size}")
    55	        assertTrue(
    56	            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
    57	                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
    58	            "PNG magic 헤더가 아님",
    59	        )
    60	    }
    61	
    62	    private fun locateDistDir(): Path {
    63	        val found = DistDiscovery.locate(null)
    64	        assumeTrue(found != null, "dist 없음 — W1D3-R2 다운로드를 먼저 수행")
    65	        return found!!.toAbsolutePath().normalize()
    66	    }
    67	
    68	    private fun locateFixtureRoot(): Path {
    69	        val found = FixtureDiscovery.locate(null)
    70	        assumeTrue(found != null, "fixture 없음 — fixture/sample-app 확인")
    71	        return found!!.toAbsolutePath().normalize()
    72	    }
    73	
    74	    private fun locateSampleAppModuleRoot(): Path {
    75	        val found = FixtureDiscovery.locateModuleRoot(null)
    76	        return requireNotNull(found)
    77	        {
    78	            "sample-app module root 없음 — fixture/sample-app 확인 + (cd fixture/sample-app && ./gradlew :app:assembleDebug) 실행"
    79	        }.toAbsolutePath().normalize()
    80	    }
    81	
    82	    private companion object {
    83	        const val MIN_RENDERED_PNG_BYTES = 1000
    84	    }
    85	}

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ClassLoaderConstantsTest.kt | sed -n '1,120p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.classloader
     2	
     3	import org.junit.jupiter.api.Assertions.assertEquals
     4	import org.junit.jupiter.api.Assertions.assertTrue
     5	import org.junit.jupiter.api.Test
     6	
     7	class ClassLoaderConstantsTest {
     8	
     9	    @Test
    10	    fun `manifest path 는 module-relative 이고 trailing 없음`() {
    11	        assertEquals("app/build/axp/runtime-classpath.txt", ClassLoaderConstants.MANIFEST_RELATIVE_PATH)
    12	        assertTrue(!ClassLoaderConstants.MANIFEST_RELATIVE_PATH.startsWith("/"))
    13	    }
    14	
    15	    @Test
    16	    fun `aar 와 jar 확장자가 dot 으로 시작`() {
    17	        assertTrue(ClassLoaderConstants.AAR_EXTENSION.startsWith("."))
    18	        assertTrue(ClassLoaderConstants.JAR_EXTENSION.startsWith("."))
    19	        assertTrue(ClassLoaderConstants.TEMP_JAR_SUFFIX.startsWith("."))
    20	    }
    21	
    22	    @Test
    23	    fun `R_JAR 경로가 module-relative + 정확한 AGP 8 layout`() {
    24	        val expected = "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
    25	        assertEquals(expected, ClassLoaderConstants.R_JAR_RELATIVE_PATH)
    26	    }
    27	}

exec
/bin/bash -lc "find ~/.gradle/caches -name 'paparazzi*.jar' 2>/dev/null | sed -n '1,40p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
codex
Paparazzi jars are not present in `~/.gradle/caches` from the requested `find` probe, so I’ll rely on local layoutlib bytecode and ASM semantics for Q1 rather than a cached transformer comparison.
exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; ls -l "$RJAR"; unzip -Z1 "$RJAR" | sed -n '"'1,80p'; unzip -Z1 \""'$RJAR" | wc -l' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
-rw-rw-r-- 1 bh-mark-dev-desktop bh-mark-dev-desktop 839052 Apr 29 15:01 fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar
com/google/android/material/R$anim.class
com/google/android/material/R$animator.class
com/google/android/material/R$attr.class
com/google/android/material/R$bool.class
com/google/android/material/R$color.class
com/google/android/material/R$dimen.class
com/google/android/material/R$drawable.class
com/google/android/material/R$id.class
com/google/android/material/R$integer.class
com/google/android/material/R$interpolator.class
com/google/android/material/R$layout.class
com/google/android/material/R$plurals.class
com/google/android/material/R$string.class
com/google/android/material/R$style.class
com/google/android/material/R$styleable.class
com/google/android/material/R.class
androidx/constraintlayout/widget/R$anim.class
androidx/constraintlayout/widget/R$attr.class
androidx/constraintlayout/widget/R$bool.class
androidx/constraintlayout/widget/R$color.class
androidx/constraintlayout/widget/R$dimen.class
androidx/constraintlayout/widget/R$drawable.class
androidx/constraintlayout/widget/R$id.class
androidx/constraintlayout/widget/R$integer.class
androidx/constraintlayout/widget/R$interpolator.class
androidx/constraintlayout/widget/R$layout.class
androidx/constraintlayout/widget/R$string.class
androidx/constraintlayout/widget/R$style.class
androidx/constraintlayout/widget/R$styleable.class
androidx/constraintlayout/widget/R.class
androidx/appcompat/resources/R$drawable.class
androidx/appcompat/resources/R$styleable.class
androidx/appcompat/resources/R.class
androidx/appcompat/R$anim.class
androidx/appcompat/R$attr.class
androidx/appcompat/R$bool.class
androidx/appcompat/R$color.class
androidx/appcompat/R$dimen.class
androidx/appcompat/R$drawable.class
androidx/appcompat/R$id.class
androidx/appcompat/R$integer.class
androidx/appcompat/R$interpolator.class
androidx/appcompat/R$layout.class
androidx/appcompat/R$string.class
androidx/appcompat/R$style.class
androidx/appcompat/R$styleable.class
androidx/appcompat/R.class
androidx/viewpager2/R$attr.class
androidx/viewpager2/R$color.class
androidx/viewpager2/R$dimen.class
androidx/viewpager2/R$drawable.class
androidx/viewpager2/R$id.class
androidx/viewpager2/R$integer.class
androidx/viewpager2/R$layout.class
androidx/viewpager2/R$string.class
androidx/viewpager2/R$style.class
androidx/viewpager2/R$styleable.class
androidx/viewpager2/R.class
androidx/fragment/R$anim.class
androidx/fragment/R$animator.class
androidx/fragment/R$attr.class
androidx/fragment/R$color.class
androidx/fragment/R$dimen.class
androidx/fragment/R$drawable.class
androidx/fragment/R$id.class
androidx/fragment/R$integer.class
androidx/fragment/R$layout.class
androidx/fragment/R$string.class
androidx/fragment/R$style.class
androidx/fragment/R$styleable.class
androidx/fragment/R.class
androidx/activity/R$id.class
androidx/activity/R.class
androidx/emoji2/viewsintegration/R.class
androidx/emoji2/R.class
androidx/drawerlayout/R$attr.class
androidx/drawerlayout/R$color.class
androidx/drawerlayout/R$dimen.class
androidx/drawerlayout/R$drawable.class
androidx/drawerlayout/R$id.class
233

exec
/bin/bash -lc 'LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; ls -l "$LAYOUTLIB"; unzip -Z1 "$LAYOUTLIB" | grep '"'_Original_' | sort; echo COUNT="'$(unzip -Z1 "$LAYOUTLIB" | grep -c '"'_Original_'); unzip -p \""'$LAYOUTLIB" '"'android/os/_Original_Build.class' | strings | grep -c Original" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
-rw-rw-r-- 1 bh-mark-dev-desktop bh-mark-dev-desktop 50566171 Apr 23 13:32 server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar
android/os/_Original_Build$Partition.class
android/os/_Original_Build$VERSION.class
android/os/_Original_Build$VERSION_CODES.class
android/os/_Original_Build.class
android/os/_Original_ServiceManager$ServiceNotFoundException.class
android/os/_Original_ServiceManager$Stats.class
android/os/_Original_ServiceManager.class
android/view/_Original_SurfaceView$1.class
android/view/_Original_SurfaceView$SurfaceLifecycleStrategy.class
android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener.class
android/view/_Original_SurfaceView$SyncBufferTransactionCallback.class
android/view/_Original_SurfaceView.class
android/view/_Original_WindowManagerImpl$1.class
android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy.class
android/view/_Original_WindowManagerImpl.class
android/view/textservice/_Original_TextServicesManager.class
android/webkit/_Original_WebView$FindListener.class
android/webkit/_Original_WebView$FindListenerDistributor.class
android/webkit/_Original_WebView$HitTestResult.class
android/webkit/_Original_WebView$PictureListener.class
android/webkit/_Original_WebView$PrivateAccess.class
android/webkit/_Original_WebView$RendererPriority.class
android/webkit/_Original_WebView$VisualStateCallback.class
android/webkit/_Original_WebView$WebViewTransport.class
android/webkit/_Original_WebView.class
COUNT=25
7

exec
/bin/bash -lc "find ~/.gradle/caches -name 'paparazzi*.jar' 2>/dev/null | wc -l" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
0

exec
/bin/bash -lc 'LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; unzip -Z1 "$LAYOUTLIB" | grep '"'\\.class"'$'"' | while read -r c; do n="'$(unzip -p "$LAYOUTLIB" "$c" | strings | grep -c '"'_Original_'); if [ \""'$n" -gt 0 ]; then printf '"'%s %s\\n' \""'$n" "$c"; fi; done | sort -nr | sed -n '"'1,80p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
2026-04-29T07:02:31.952036Z ERROR codex_core::tools::router: error=write_stdin failed: stdin is closed for this session; rerun exec_command with tty=true to keep stdin open
exec
/bin/bash -lc "ps -eo pid,ppid,stat,comm,args | rg 'unzip -p|layoutlib-14.0.11|grep -c _Original_|while read'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
1613339 1613336 Sl   node            node /home/bh-mark-dev-desktop/.nvm/versions/node/v22.22.2/bin/codex exec --skip-git-repo-check --sandbox danger-full-access -C /home/bh-mark-dev-desktop/workspace/android_xml_previewer # Codex Pair Review — W3D3-α Bytecode Rewriting + R.jar Seeding Spec  You are reviewing a Kotlin/JVM design spec that adds ASM-based bytecode rewriting and R.jar id seeding to unblock the W3D3 milestone. The W3D3 base infrastructure (Tasks 1-8) was completed and pushed; this α delta is focused on resolving two specific blockers found in branch (C) of the prior session.  **Configuration**: Use the highest available GPT model (currently GPT-5.5/5.4) with reasoning effort `xhigh`. No time limit.  ## What you have  Working directory: `/home/bh-mark-dev-desktop/workspace/android_xml_previewer`  1. **THE SPEC UNDER REVIEW**: `docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md` 2. **Branch (C) diagnosis** that motivated α: `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md` 3. **Predecessor spec** (W3D3 round 2, complete): `docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md` 4. **Predecessor session log**: `docs/work_log/2026-04-29_w3d3-l3-classloader/session-log.md` 5. **Existing infrastructure** (Tasks 1-8 already merged):    - `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/{ClassLoaderConstants,SampleAppClasspathManifest,AarExtractor,SampleAppClassLoader}.kt`    - `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/{LayoutlibRenderer,FixtureDiscovery}.kt`    - `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`    - `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/{RendererArgs,SharedLayoutlibRenderer,SharedRendererBinding}.kt`    - `fixture/sample-app/app/build.gradle.kts` (axpEmitClasspath task) 6. CLAUDE.md (project + global).  ## What's already settled (don't re-litigate)  - URLClassLoader (not DexClassLoader) is correct. - Lazy CL build pattern. - Pair-review process for design tradeoff analysis. - All round 2 fixes from the predecessor spec.  ## What you are reviewing  The α spec proposes ~270 production LOC + ~480 test LOC to: 1. Add ASM 9.7 dependency to `:layoutlib-worker`. 2. Introduce `RewriteRules` with empirically-derived NAME_MAP of 25 entries (all `_Original_*` classes in layoutlib 14.0.11). 3. Introduce `AndroidClassRewriter.rewrite(bytes): bytes` using ASM `ClassRemapper` + `ClassWriter`. 4. Modify `AarExtractor` to rewrite each `.class` entry in classes.jar using AndroidClassRewriter, producing rewritten classes.jar in the cache. 5. Introduce `RJarTypeMapping` (R$<simpleName> → ResourceType enum). 6. Introduce `RJarSymbolSeeder` that walks all R$* class entries in the sample-app R.jar, reflection-enumerates static int fields, registers as ResourceReference + id with the callback. 7. Modify `MinimalLayoutlibCallback` to accept an `initializer` lambda that runs at construction time, with a `registerSymbol` callback for the seeder. 8. Modify `LayoutlibRenderer.renderViaLayoutlib` to wire RJarSymbolSeeder into the callback initializer. 9. Re-enable `LayoutlibRendererIntegrationTest` (remove @Disabled).  ## Specific concerns to address  Answer each with concrete evidence (file:line citations + shell command output where relevant).  ### A. Plan-level blockers (NO_GO conditions)  For each: spec section, exact failure mode, fix.  ### B. Strongly recommended (GO_WITH_FOLLOWUPS)  ### C. Spec questions (Q1-Q3)  The spec lists three explicit questions. Answer each with stance + reasoning:  **Q1 (NAME_MAP completeness)**: Is the empirically-derived 25-entry NAME_MAP sufficient? Could there be *indirect* references (e.g., type signatures in field descriptors) that need additional rewriting beyond what `_Original_*` names show? Investigate by: - Comparing with Paparazzi's `JvmCallableTransformer` if you can find it in any cache (`find ~/.gradle/caches -name "paparazzi*.jar" 2>/dev/null` — likely not present, OK to note absence). - Checking if the layoutlib JAR has any class with `_Original_` references in its bytecode (`unzip -p layoutlib-14.0.11.jar android/os/_Original_Build.class | strings | grep -c Original`) to confirm self-consistency.  **Q2 (R.jar id collision with callback nextId)**: Will R.jar ids collide with `nextId` starting at `0x7F0A_0000`? Investigate: - Use `unzip -p` + `javap -p` on `androidx/constraintlayout/widget/R$attr.class` to dump int field values. Confirm whether they fall within or outside `0x7F0A_xxxx` range. The spec's `advanceNextIdAbove` mitigation should handle, but verify. - Look at the `register` callback's behavior in the spec (§4.6) — if a duplicate ResourceReference is registered, what happens?  **Q3 (`R$styleable` int[] skip)**: Does layoutlib's `BridgeContext.obtainStyledAttributes(set, attrs)` actually look up styleable arrays by attr-id (array contents) or by the array reference itself? Verify by examining BridgeContext if available, or by reasoning from the JVM call semantics — the array is a parameter, so layoutlib iterates over its int contents.  ### D. Independent issues  Especially: - **Performance**: AAR rewrite is per-AAR-on-first-extract (mtime cached). For ~50 AARs in the sample-app classpath, what's the expected wall-clock cost of ASM transformation? Run the existing AarExtractor on one AAR and benchmark. - **CLAUDE.md compliance**: spec snippets — any magic strings in logic that should be in `ClassLoaderConstants`? `CLASS_FILE_SUFFIX = ".class"` is mentioned but not yet added — verify. - **Race conditions**: The MinimalLayoutlibCallback's `init` block runs the seeder. If layoutlib calls `getOrGenerateResourceId` from another thread *before* init completes, is that possible? Check the LayoutlibCallback contract. - **com.android.resources.ResourceType availability**: This enum is referenced by spec §4.4. Is it actually on the worker's classpath? Check by `grep -rn "ResourceType" server/layoutlib-worker/`. - **com.android.ide.common.rendering.api.ResourceNamespace.fromPackageName**: Is this method available in layoutlib-api 31.13.2? Verify via javap on the api JAR.  ### E. Verdict NO_GO / GO_WITH_FOLLOWUPS / GO + paragraph rationale.  ## Output format  Markdown with sections A, B, C (per Q), D, E. File:line citations. Run shell commands freely.  Save your final report to `/tmp/w3d3-alpha-codex-spec-review.md`.
1613371 1613339 Sl   codex           /home/bh-mark-dev-desktop/.nvm/versions/node/v22.22.2/lib/node_modules/@openai/codex/node_modules/@openai/codex-linux-x64/vendor/x86_64-unknown-linux-musl/codex/codex exec --skip-git-repo-check --sandbox danger-full-access -C /home/bh-mark-dev-desktop/workspace/android_xml_previewer # Codex Pair Review — W3D3-α Bytecode Rewriting + R.jar Seeding Spec  You are reviewing a Kotlin/JVM design spec that adds ASM-based bytecode rewriting and R.jar id seeding to unblock the W3D3 milestone. The W3D3 base infrastructure (Tasks 1-8) was completed and pushed; this α delta is focused on resolving two specific blockers found in branch (C) of the prior session.  **Configuration**: Use the highest available GPT model (currently GPT-5.5/5.4) with reasoning effort `xhigh`. No time limit.  ## What you have  Working directory: `/home/bh-mark-dev-desktop/workspace/android_xml_previewer`  1. **THE SPEC UNDER REVIEW**: `docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md` 2. **Branch (C) diagnosis** that motivated α: `docs/work_log/2026-04-29_w3d3-l3-classloader/branch-c-diagnosis.md` 3. **Predecessor spec** (W3D3 round 2, complete): `docs/superpowers/specs/2026-04-29-w3d3-l3-classloader-design.md` 4. **Predecessor session log**: `docs/work_log/2026-04-29_w3d3-l3-classloader/session-log.md` 5. **Existing infrastructure** (Tasks 1-8 already merged):    - `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/{ClassLoaderConstants,SampleAppClasspathManifest,AarExtractor,SampleAppClassLoader}.kt`    - `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/{LayoutlibRenderer,FixtureDiscovery}.kt`    - `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt`    - `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/{RendererArgs,SharedLayoutlibRenderer,SharedRendererBinding}.kt`    - `fixture/sample-app/app/build.gradle.kts` (axpEmitClasspath task) 6. CLAUDE.md (project + global).  ## What's already settled (don't re-litigate)  - URLClassLoader (not DexClassLoader) is correct. - Lazy CL build pattern. - Pair-review process for design tradeoff analysis. - All round 2 fixes from the predecessor spec.  ## What you are reviewing  The α spec proposes ~270 production LOC + ~480 test LOC to: 1. Add ASM 9.7 dependency to `:layoutlib-worker`. 2. Introduce `RewriteRules` with empirically-derived NAME_MAP of 25 entries (all `_Original_*` classes in layoutlib 14.0.11). 3. Introduce `AndroidClassRewriter.rewrite(bytes): bytes` using ASM `ClassRemapper` + `ClassWriter`. 4. Modify `AarExtractor` to rewrite each `.class` entry in classes.jar using AndroidClassRewriter, producing rewritten classes.jar in the cache. 5. Introduce `RJarTypeMapping` (R$<simpleName> → ResourceType enum). 6. Introduce `RJarSymbolSeeder` that walks all R$* class entries in the sample-app R.jar, reflection-enumerates static int fields, registers as ResourceReference + id with the callback. 7. Modify `MinimalLayoutlibCallback` to accept an `initializer` lambda that runs at construction time, with a `registerSymbol` callback for the seeder. 8. Modify `LayoutlibRenderer.renderViaLayoutlib` to wire RJarSymbolSeeder into the callback initializer. 9. Re-enable `LayoutlibRendererIntegrationTest` (remove @Disabled).  ## Specific concerns to address  Answer each with concrete evidence (file:line citations + shell command output where relevant).  ### A. Plan-level blockers (NO_GO conditions)  For each: spec section, exact failure mode, fix.  ### B. Strongly recommended (GO_WITH_FOLLOWUPS)  ### C. Spec questions (Q1-Q3)  The spec lists three explicit questions. Answer each with stance + reasoning:  **Q1 (NAME_MAP completeness)**: Is the empirically-derived 25-entry NAME_MAP sufficient? Could there be *indirect* references (e.g., type signatures in field descriptors) that need additional rewriting beyond what `_Original_*` names show? Investigate by: - Comparing with Paparazzi's `JvmCallableTransformer` if you can find it in any cache (`find ~/.gradle/caches -name "paparazzi*.jar" 2>/dev/null` — likely not present, OK to note absence). - Checking if the layoutlib JAR has any class with `_Original_` references in its bytecode (`unzip -p layoutlib-14.0.11.jar android/os/_Original_Build.class | strings | grep -c Original`) to confirm self-consistency.  **Q2 (R.jar id collision with callback nextId)**: Will R.jar ids collide with `nextId` starting at `0x7F0A_0000`? Investigate: - Use `unzip -p` + `javap -p` on `androidx/constraintlayout/widget/R$attr.class` to dump int field values. Confirm whether they fall within or outside `0x7F0A_xxxx` range. The spec's `advanceNextIdAbove` mitigation should handle, but verify. - Look at the `register` callback's behavior in the spec (§4.6) — if a duplicate ResourceReference is registered, what happens?  **Q3 (`R$styleable` int[] skip)**: Does layoutlib's `BridgeContext.obtainStyledAttributes(set, attrs)` actually look up styleable arrays by attr-id (array contents) or by the array reference itself? Verify by examining BridgeContext if available, or by reasoning from the JVM call semantics — the array is a parameter, so layoutlib iterates over its int contents.  ### D. Independent issues  Especially: - **Performance**: AAR rewrite is per-AAR-on-first-extract (mtime cached). For ~50 AARs in the sample-app classpath, what's the expected wall-clock cost of ASM transformation? Run the existing AarExtractor on one AAR and benchmark. - **CLAUDE.md compliance**: spec snippets — any magic strings in logic that should be in `ClassLoaderConstants`? `CLASS_FILE_SUFFIX = ".class"` is mentioned but not yet added — verify. - **Race conditions**: The MinimalLayoutlibCallback's `init` block runs the seeder. If layoutlib calls `getOrGenerateResourceId` from another thread *before* init completes, is that possible? Check the LayoutlibCallback contract. - **com.android.resources.ResourceType availability**: This enum is referenced by spec §4.4. Is it actually on the worker's classpath? Check by `grep -rn "ResourceType" server/layoutlib-worker/`. - **com.android.ide.common.rendering.api.ResourceNamespace.fromPackageName**: Is this method available in layoutlib-api 31.13.2? Verify via javap on the api JAR.  ### E. Verdict NO_GO / GO_WITH_FOLLOWUPS / GO + paragraph rationale.  ## Output format  Markdown with sections A, B, C (per Q), D, E. File:line citations. Run shell commands freely.  Save your final report to `/tmp/w3d3-alpha-codex-spec-review.md`.
1615238 1613371 Ss   bash            /bin/bash -c LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; unzip -Z1 "$LAYOUTLIB" | grep '\.class$' | while read -r c; do n=$(unzip -p "$LAYOUTLIB" "$c" | strings | grep -c '_Original_'); if [ "$n" -gt 0 ]; then printf '%s %s\n' "$n" "$c"; fi; done | sort -nr | sed -n '1,80p'
1615240 1615238 S    unzip           unzip -Z1 server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar
1615242 1615238 S    bash            /bin/bash -c LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; unzip -Z1 "$LAYOUTLIB" | grep '\.class$' | while read -r c; do n=$(unzip -p "$LAYOUTLIB" "$c" | strings | grep -c '_Original_'); if [ "$n" -gt 0 ]; then printf '%s %s\n' "$n" "$c"; fi; done | sort -nr | sed -n '1,80p'
1626975 1613371 Ss   bash            /bin/bash -c ps -eo pid,ppid,stat,comm,args | rg 'unzip -p|layoutlib-14.0.11|grep -c _Original_|while read'
1626977 1626975 S    rg              rg unzip -p|layoutlib-14.0.11|grep -c _Original_|while read

exec
/bin/bash -lc 'kill 1615238 1615242 2>/dev/null || true' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
 exited -1 in 34692ms:
3 android/app/appsearch/ParcelableUtil.class
3 android/adservices/topics/TopicsManager.class
3 android/adservices/measurement/WebTriggerRegistrationRequest.class
3 android/adservices/measurement/WebSourceRegistrationRequest.class
3 android/adservices/measurement/MeasurementManager.class
3 android/adservices/customaudience/CustomAudienceManager.class
3 android/adservices/common/SandboxedSdkContextUtils.class
3 android/adservices/common/AdServicesCommonManager.class
3 android/adservices/appsetid/AppSetIdManager.class
3 android/adservices/adselection/AdSelectionManager.class
3 android/adservices/adselection/AdSelectionFromOutcomesConfig.class
3 android/adservices/adid/AdIdManager.class
2 android/bluetooth/BluetoothProfileConnector.class
2 android/app/servertransaction/PendingTransactionActions$StopInfo.class
2 android/app/sdksandbox/SdkLevel.class
2 android/app/admin/DevicePolicyManager.class
2 android/app/WallpaperManager.class
2 android/app/WallpaperManager$Globals.class
2 android/app/SystemServiceRegistry$72.class
2 android/app/SystemServiceRegistry$71.class
2 android/app/SystemServiceRegistry$58.class
2 android/app/Service.class
2 android/app/PendingIntent.class
2 android/app/NotificationManager.class
2 android/app/Notification.class
2 android/app/Notification$MessagingStyle.class
2 android/app/Notification$Builder.class
2 android/app/NativeActivity.class
2 android/app/LoadedApk.class
2 android/app/Instrumentation.class
2 android/app/GameManager.class
2 android/app/FragmentTransition.class
2 android/app/FragmentManagerImpl.class
2 android/app/Fragment.class
2 android/app/ExitTransitionCoordinator.class
2 android/app/DownloadManager.class
2 android/app/DownloadManager$Request.class
2 android/app/ContextImpl.class
2 android/app/BroadcastOptions.class
2 android/app/BackStackRecord.class
2 android/app/ApplicationPackageManager.class
2 android/app/ApplicationLoaders.class
2 android/app/AlarmManager.class
2 android/app/ActivityThread.class
2 android/app/ActivityThread$ActivityClientRecord.class
2 android/app/ActivityManager$RunningAppProcessInfo.class
2 android/app/Activity.class
2 android/app/Activity$HostCallbacks.class
2 android/animation/AnimatorSet.class
2 android/accounts/AccountManager.class
2 android/accessibilityservice/AccessibilityServiceInfo.class
1 android/app/admin/SystemUpdateInfo.class
1 android/app/UiAutomation$IAccessibilityServiceClientImpl$1.class
1 android/app/AppOpsManager.class
1 android/app/ActivityThread$ApplicationThread.class
1 android/app/ActivityTaskManager.class
1 android/app/ActivityManager.class

exec
/bin/bash -lc 'LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; unzip -p "$LAYOUTLIB" '"'*.class' | strings | grep '_Original_' | sort -u | sed -n '1,80p'; echo DISTINCT="'$(unzip -p "$LAYOUTLIB" '"'*.class' | strings | grep '_Original_' | sort -u | wc -l)" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 865ms:
 android.webkit._Original_WebView
 android/webkit/_Original_WebView
"Landroid/webkit/_Original_WebView;
"android.view._Original_SurfaceView
"android/os/_Original_Build$VERSION
"android/view/_Original_SurfaceView
#android.os._Original_ServiceManager
#android/os/_Original_ServiceManager
$()Landroid/webkit/_Original_WebView;
$Landroid/os/_Original_Build$VERSION;
$Landroid/view/_Original_SurfaceView;
$android/os/_Original_Build$Partition
$android/view/_Original_SurfaceView$1
%(Landroid/webkit/_Original_WebView;)F
%(Landroid/webkit/_Original_WebView;)I
%(Landroid/webkit/_Original_WebView;)V
%(Landroid/webkit/_Original_WebView;)Z
%Landroid/os/_Original_ServiceManager;
&(Landroid/webkit/_Original_WebView;I)I
&(Landroid/webkit/_Original_WebView;I)Z
&Landroid/os/_Original_Build$Partition;
&Landroid/view/_Original_SurfaceView$1;
'(Landroid/view/_Original_SurfaceView;)V
'(Landroid/webkit/_Original_WebView;II)V
'(Landroid/webkit/_Original_WebView;IZ)Z
(Landroid/view/textservice/SpellCheckerInfo;Landroid/view/textservice/_Original_TextServicesManager;Landroid/view/textservice/SpellCheckerSession$SpellCheckerSessionListener;Ljava/util/concurrent/Executor;)V
(android.view._Original_WindowManagerImpl
(android/os/_Original_Build$VERSION_CODES
(android/view/_Original_WindowManagerImpl
)(Landroid/view/_Original_SurfaceView;II)V
)(Landroid/webkit/_Original_WebView;IIII)Z
)android/os/_Original_ServiceManager$Stats
*Landroid/os/_Original_Build$VERSION_CODES;
*Landroid/view/_Original_WindowManagerImpl;
*android/view/_Original_WindowManagerImpl$1
,Landroid/view/_Original_WindowManagerImpl$1;
-android/webkit/_Original_WebView$FindListener
.(Landroid/webkit/_Original_WebView;IIIIIIIIZ)Z
.android/webkit/_Original_WebView$HitTestResult
.android/webkit/_Original_WebView$PrivateAccess
/Landroid/webkit/_Original_WebView$FindListener;
0Landroid/webkit/_Original_WebView$HitTestResult;
0Landroid/webkit/_Original_WebView$PrivateAccess;
0android/webkit/_Original_WebView$PictureListener
1android/webkit/_Original_WebView$RendererPriority
1android/webkit/_Original_WebView$WebViewTransport
2()Landroid/webkit/_Original_WebView$HitTestResult;
2(Landroid/webkit/_Original_WebView$FindListener;)V
2Landroid/webkit/_Original_WebView$PictureListener;
3Landroid/webkit/_Original_WebView$WebViewTransport;
4android/webkit/_Original_WebView$VisualStateCallback
5(Landroid/webkit/_Original_WebView$PictureListener;)V
6Landroid/webkit/_Original_WebView$VisualStateCallback;
6android.view.textservice._Original_TextServicesManager
6android/view/textservice/_Original_TextServicesManager
8Landroid/view/textservice/_Original_TextServicesManager;
8android/webkit/_Original_WebView$FindListenerDistributor
9(Landroid/webkit/_Original_WebView;ILandroid/os/Bundle;)Z
:()Landroid/view/textservice/_Original_TextServicesManager;
:()Ljava/util/List<Landroid/os/_Original_Build$Partition;>;
:(JLandroid/webkit/_Original_WebView$VisualStateCallback;)V
:Landroid/webkit/_Original_WebView$FindListenerDistributor;
;android/view/_Original_SurfaceView$SurfaceLifecycleStrategy
<(Landroid/webkit/_Original_WebView;Landroid/view/KeyEvent;)Z
<android/os/_Original_ServiceManager$ServiceNotFoundException
=(Landroid/webkit/_Original_WebView;ILandroid/graphics/Rect;)Z
=Ljava/util/ArrayList<Landroid/os/_Original_Build$Partition;>;
>(Landroid/webkit/_Original_WebView;Landroid/content/Intent;I)V
>Landroid/os/_Original_ServiceManager$ServiceNotFoundException;
?(Landroid/webkit/_Original_WebView;Landroid/graphics/Picture;)V
?(Landroid/webkit/_Original_WebView;Landroid/view/MotionEvent;)Z
@android/view/_Original_SurfaceView$SyncBufferTransactionCallback
A(Landroid/view/Window;)Landroid/view/_Original_WindowManagerImpl;
BLandroid/view/_Original_SurfaceView$SyncBufferTransactionCallback;
Candroid/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy
Dandroid/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener
E(Landroid/content/Context;)Landroid/view/_Original_WindowManagerImpl;
ELandroid/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy;
FLandroid/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener;
J(Landroid/webkit/_Original_WebView;Landroid/view/ViewGroup$LayoutParams;)V
DISTINCT=92

codex
The layoutlib jar contains the 25 `_Original_*` classes, and strings from other framework classes show descriptors and generic signatures pointing at those renamed classes. That makes descriptor/signature remapping a must, which ASM `ClassRemapper` covers; I’m now checking the R.jar id ranges and the actual callback/API availability.
exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -c '"'androidx.constraintlayout.widget.R"'$id'"' | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
public final class androidx.constraintlayout.widget.R$id {
  public static int NO_DEBUG;

  public static int SHOW_ALL;

  public static int SHOW_PATH;

  public static int SHOW_PROGRESS;

  public static int accelerate;

  public static int accessibility_action_clickable_span;

  public static int accessibility_custom_action_0;

  public static int accessibility_custom_action_1;

  public static int accessibility_custom_action_10;

  public static int accessibility_custom_action_11;

  public static int accessibility_custom_action_12;

  public static int accessibility_custom_action_13;

  public static int accessibility_custom_action_14;

  public static int accessibility_custom_action_15;

  public static int accessibility_custom_action_16;

  public static int accessibility_custom_action_17;

  public static int accessibility_custom_action_18;

  public static int accessibility_custom_action_19;

  public static int accessibility_custom_action_2;

  public static int accessibility_custom_action_20;

  public static int accessibility_custom_action_21;

  public static int accessibility_custom_action_22;

  public static int accessibility_custom_action_23;

  public static int accessibility_custom_action_24;

  public static int accessibility_custom_action_25;

  public static int accessibility_custom_action_26;

  public static int accessibility_custom_action_27;

  public static int accessibility_custom_action_28;

  public static int accessibility_custom_action_29;

  public static int accessibility_custom_action_3;

  public static int accessibility_custom_action_30;

  public static int accessibility_custom_action_31;

  public static int accessibility_custom_action_4;

  public static int accessibility_custom_action_5;

  public static int accessibility_custom_action_6;

  public static int accessibility_custom_action_7;

  public static int accessibility_custom_action_8;

  public static int accessibility_custom_action_9;

  public static int actionDown;

  public static int actionDownUp;

  public static int actionUp;

  public static int action_bar;

  public static int action_bar_activity_content;

  public static int action_bar_container;

  public static int action_bar_root;

  public static int action_bar_spinner;

  public static int action_bar_subtitle;

  public static int action_bar_title;

  public static int action_container;

  public static int action_context_bar;

  public static int action_divider;

  public static int action_image;

  public static int action_menu_divider;

  public static int action_menu_presenter;

  public static int action_mode_bar;

  public static int action_mode_bar_stub;

  public static int action_mode_close_button;

  public static int action_text;

  public static int actions;

  public static int activity_chooser_view_content;

  public static int add;

  public static int alertTitle;

  public static int aligned;

  public static int allStates;

  public static int animateToEnd;

  public static int animateToStart;

  public static int antiClockwise;

  public static int anticipate;

  public static int asConfigured;

  public static int async;

  public static int auto;

  public static int autoComplete;

  public static int autoCompleteToEnd;

  public static int autoCompleteToStart;

  public static int baseline;

  public static int bestChoice;

  public static int blocking;

  public static int bottom;

  public static int bounce;

  public static int buttonPanel;

  public static int callMeasure;

  public static int carryVelocity;

  public static int center;

  public static int chain;

  public static int chain2;

  public static int checkbox;

  public static int checked;

  public static int chronometer;

  public static int clockwise;

  public static int closest;

exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -c '"'androidx.constraintlayout.widget.R"'$attr'"' | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
public final class androidx.constraintlayout.widget.R$attr {
  public static int SharedValue;

  public static int SharedValueId;

  public static int actionBarDivider;

  public static int actionBarItemBackground;

  public static int actionBarPopupTheme;

  public static int actionBarSize;

  public static int actionBarSplitStyle;

  public static int actionBarStyle;

  public static int actionBarTabBarStyle;

  public static int actionBarTabStyle;

  public static int actionBarTabTextStyle;

  public static int actionBarTheme;

  public static int actionBarWidgetTheme;

  public static int actionButtonStyle;

  public static int actionDropDownStyle;

  public static int actionLayout;

  public static int actionMenuTextAppearance;

  public static int actionMenuTextColor;

  public static int actionModeBackground;

  public static int actionModeCloseButtonStyle;

  public static int actionModeCloseDrawable;

  public static int actionModeCopyDrawable;

  public static int actionModeCutDrawable;

  public static int actionModeFindDrawable;

  public static int actionModePasteDrawable;

  public static int actionModePopupWindowStyle;

  public static int actionModeSelectAllDrawable;

  public static int actionModeShareDrawable;

  public static int actionModeSplitBackground;

  public static int actionModeStyle;

  public static int actionModeWebSearchDrawable;

  public static int actionOverflowButtonStyle;

  public static int actionOverflowMenuStyle;

  public static int actionProviderClass;

  public static int actionViewClass;

  public static int activityChooserViewStyle;

  public static int alertDialogButtonGroupStyle;

  public static int alertDialogCenterButtons;

  public static int alertDialogStyle;

  public static int alertDialogTheme;

  public static int allowStacking;

  public static int alpha;

  public static int alphabeticModifiers;

  public static int altSrc;

  public static int animateCircleAngleTo;

  public static int animateRelativeTo;

  public static int applyMotionScene;

  public static int arcMode;

  public static int arrowHeadLength;

  public static int arrowShaftLength;

  public static int attributeName;

  public static int autoCompleteMode;

  public static int autoCompleteTextViewStyle;

  public static int autoSizeMaxTextSize;

  public static int autoSizeMinTextSize;

  public static int autoSizePresetSizes;

  public static int autoSizeStepGranularity;

  public static int autoSizeTextType;

  public static int autoTransition;

  public static int background;

  public static int backgroundSplit;

  public static int backgroundStacked;

  public static int backgroundTint;

  public static int backgroundTintMode;

  public static int barLength;

  public static int barrierAllowsGoneWidgets;

  public static int barrierDirection;

  public static int barrierMargin;

  public static int blendSrc;

  public static int borderRound;

  public static int borderRoundPercent;

  public static int borderlessButtonStyle;

  public static int brightness;

  public static int buttonBarButtonStyle;

  public static int buttonBarNegativeButtonStyle;

  public static int buttonBarNeutralButtonStyle;

  public static int buttonBarPositiveButtonStyle;

  public static int buttonBarStyle;

  public static int buttonCompat;

  public static int buttonGravity;

  public static int buttonIconDimen;

  public static int buttonPanelSideLayout;

  public static int buttonStyle;

  public static int buttonStyleSmall;

  public static int buttonTint;

  public static int buttonTintMode;

  public static int carousel_backwardTransition;

  public static int carousel_emptyViewsBehavior;

  public static int carousel_firstView;

  public static int carousel_forwardTransition;

  public static int carousel_infinite;

  public static int carousel_nextState;

  public static int carousel_previousState;

  public static int carousel_touchUpMode;

  public static int carousel_touchUp_dampeningFactor;

  public static int carousel_touchUp_velocityThreshold;

  public static int chainUseRtl;

  public static int checkboxStyle;

  public static int checkedTextViewStyle;

  public static int circleRadius;

  public static int circularflow_angles;

  public static int circularflow_defaultAngle;

  public static int circularflow_defaultRadius;

  public static int circularflow_radiusInDP;

  public static int circularflow_viewCenter;

  public static int clearsTag;

  public static int clickAction;

  public static int closeIcon;

  public static int closeItemLayout;

  public static int collapseContentDescription;

exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -c '"'com.fixture.R"'$id'"' 'com.fixture.R"'$style'"' 'com.fixture.R"'$attr'"' 2>&1 | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
public final class com.fixture.R$id {
  public static int body;

  public static int caption;

  public static int dummy;

  public static int primary_button;

  public static int title;

  private com.fixture.R$id();
    Code:
       0: aload_0
       1: invokespecial #22                 // Method java/lang/Object."<init>":()V
       4: return
}
public final class com.fixture.R$style {
  public static int Theme_AxpFixture;

  private com.fixture.R$style();
    Code:
       0: aload_0
       1: invokespecial #14                 // Method java/lang/Object."<init>":()V
       4: return
}
Error: class not found: com.fixture.R$attr

exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -c '"'com.google.android.material.R"'$id'"' | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
public final class com.google.android.material.R$id {
  public static int BOTTOM_END;

  public static int BOTTOM_START;

  public static int NO_DEBUG;

  public static int SHOW_ALL;

  public static int SHOW_PATH;

  public static int SHOW_PROGRESS;

  public static int TOP_END;

  public static int TOP_START;

  public static int accelerate;

  public static int accessibility_action_clickable_span;

  public static int accessibility_custom_action_0;

  public static int accessibility_custom_action_1;

  public static int accessibility_custom_action_10;

  public static int accessibility_custom_action_11;

  public static int accessibility_custom_action_12;

  public static int accessibility_custom_action_13;

  public static int accessibility_custom_action_14;

  public static int accessibility_custom_action_15;

  public static int accessibility_custom_action_16;

  public static int accessibility_custom_action_17;

  public static int accessibility_custom_action_18;

  public static int accessibility_custom_action_19;

  public static int accessibility_custom_action_2;

  public static int accessibility_custom_action_20;

  public static int accessibility_custom_action_21;

  public static int accessibility_custom_action_22;

  public static int accessibility_custom_action_23;

  public static int accessibility_custom_action_24;

  public static int accessibility_custom_action_25;

  public static int accessibility_custom_action_26;

  public static int accessibility_custom_action_27;

  public static int accessibility_custom_action_28;

  public static int accessibility_custom_action_29;

  public static int accessibility_custom_action_3;

  public static int accessibility_custom_action_30;

  public static int accessibility_custom_action_31;

  public static int accessibility_custom_action_4;

  public static int accessibility_custom_action_5;

  public static int accessibility_custom_action_6;

  public static int accessibility_custom_action_7;

  public static int accessibility_custom_action_8;

  public static int accessibility_custom_action_9;

  public static int action_bar;

  public static int action_bar_activity_content;

  public static int action_bar_container;

  public static int action_bar_root;

  public static int action_bar_spinner;

  public static int action_bar_subtitle;

  public static int action_bar_title;

  public static int action_container;

  public static int action_context_bar;

  public static int action_divider;

  public static int action_image;

  public static int action_menu_divider;

  public static int action_menu_presenter;

  public static int action_mode_bar;

  public static int action_mode_bar_stub;

  public static int action_mode_close_button;

  public static int action_text;

  public static int actions;

  public static int activity_chooser_view_content;

  public static int add;

  public static int alertTitle;

  public static int aligned;

  public static int animateToEnd;

  public static int animateToStart;

  public static int arc;

  public static int asConfigured;

  public static int async;

  public static int auto;

  public static int autoComplete;

  public static int autoCompleteToEnd;

  public static int autoCompleteToStart;

  public static int baseline;

  public static int blocking;

  public static int bottom;

  public static int bounce;

  public static int buttonPanel;

  public static int cancel_button;

  public static int center;

  public static int centerCrop;

  public static int centerInside;

  public static int chain;

  public static int checkbox;

  public static int checked;

  public static int chronometer;

  public static int circle_center;

  public static int clear_text;

  public static int clockwise;

  public static int compress;

exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -verbose '"'androidx.constraintlayout.widget.R"'$attr'"' | rg -n \"layout_constraintTop_toTopOf|barrierAllowsGoneWidgets|SharedValue|ConstantValue|descriptor: I|flags: \\(0x0009\\)\" -A3 -B2 | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -verbose '"'com.fixture.R"'$id'"' 'com.fixture.R"'$style'"' | rg -n \"body|title|primary_button|Theme_AxpFixture|ConstantValue|descriptor: I\" -A3 -B2 | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
17-   #6 = Class              #5             // com/fixture/R
18-   #7 = Utf8               id
19:   #8 = Utf8               body
20-   #9 = Utf8               I
21-  #10 = Integer            2131230813
22-  #11 = Utf8               caption
--
24-  #13 = Utf8               dummy
25-  #14 = Integer            2131230888
26:  #15 = Utf8               primary_button
27-  #16 = Integer            2131231077
28:  #17 = Utf8               title
29-  #18 = Integer            2131231183
30-  #19 = Utf8               <init>
31-  #20 = Utf8               ()V
32-  #21 = NameAndType        #19:#20        // "<init>":()V
33-  #22 = Methodref          #4.#21         // java/lang/Object."<init>":()V
34:  #23 = Utf8               ConstantValue
35-  #24 = Utf8               Code
36-  #25 = Utf8               InnerClasses
37-{
38:  public static int body;
39:    descriptor: I
40-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
41:    ConstantValue: int 2131230813
42-
43-  public static int caption;
44:    descriptor: I
45-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
46:    ConstantValue: int 2131230823
47-
48-  public static int dummy;
49:    descriptor: I
50-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
51:    ConstantValue: int 2131230888
52-
53:  public static int primary_button;
54:    descriptor: I
55-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
56:    ConstantValue: int 2131231077
57-
58:  public static int title;
59:    descriptor: I
60-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
61:    ConstantValue: int 2131231183
62-
63-  private com.fixture.R$id();
64-    descriptor: ()V
--
90-   #6 = Class              #5             // com/fixture/R
91-   #7 = Utf8               style
92:   #8 = Utf8               Theme_AxpFixture
93-   #9 = Utf8               I
94-  #10 = Integer            2131690017
95-  #11 = Utf8               <init>
--
97-  #13 = NameAndType        #11:#12        // "<init>":()V
98-  #14 = Methodref          #4.#13         // java/lang/Object."<init>":()V
99:  #15 = Utf8               ConstantValue
100-  #16 = Utf8               Code
101-  #17 = Utf8               InnerClasses
102-{
103:  public static int Theme_AxpFixture;
104:    descriptor: I
105-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
106:    ConstantValue: int 2131690017
107-
108-  private com.fixture.R$style();
109-    descriptor: ()V

exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -verbose '"'com.google.android.material.R"'$id'"' | rg -n \"design_menu_item_action_area|mtrl|snackbar|ConstantValue|descriptor: I\" -A3 -B2 | sed -n '1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -verbose '"'androidx.constraintlayout.widget.R"'$id'"' | rg -n \"NO_DEBUG|parent|ConstantValue|descriptor: I\" -A3 -B2 | sed -n '1,180p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 118ms:
9:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1220:    ConstantValue: int 2130903057
1221-
1222-  public static int actionModeBackground;
1223:    descriptor: I
1224:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1225:    ConstantValue: int 2130903058
1226-
1227-  public static int actionModeCloseButtonStyle;
1228:    descriptor: I
1229:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1230:    ConstantValue: int 2130903059
1231-
1232-  public static int actionModeCloseDrawable;
1233:    descriptor: I
1234:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1235:    ConstantValue: int 2130903061
1236-
1237-  public static int actionModeCopyDrawable;
1238:    descriptor: I
1239:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1240:    ConstantValue: int 2130903062
1241-
1242-  public static int actionModeCutDrawable;
1243:    descriptor: I
1244:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1245:    ConstantValue: int 2130903063
1246-
1247-  public static int actionModeFindDrawable;
1248:    descriptor: I
1249:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1250:    ConstantValue: int 2130903064
1251-
1252-  public static int actionModePasteDrawable;
1253:    descriptor: I
1254:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1255:    ConstantValue: int 2130903065
1256-
1257-  public static int actionModePopupWindowStyle;
1258:    descriptor: I
1259:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1260:    ConstantValue: int 2130903066
1261-
1262-  public static int actionModeSelectAllDrawable;
1263:    descriptor: I
1264:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1265:    ConstantValue: int 2130903067
1266-
1267-  public static int actionModeShareDrawable;
1268:    descriptor: I
1269:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1270:    ConstantValue: int 2130903068
1271-
1272-  public static int actionModeSplitBackground;
1273:    descriptor: I
1274:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1275:    ConstantValue: int 2130903069
1276-
1277-  public static int actionModeStyle;
1278:    descriptor: I
1279:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1280:    ConstantValue: int 2130903070
1281-
1282-  public static int actionModeWebSearchDrawable;
1283:    descriptor: I
1284:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1285:    ConstantValue: int 2130903072
1286-
1287-  public static int actionOverflowButtonStyle;
1288:    descriptor: I
1289:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1290:    ConstantValue: int 2130903073
1291-
1292-  public static int actionOverflowMenuStyle;
1293:    descriptor: I
1294:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1295:    ConstantValue: int 2130903074
1296-
1297-  public static int actionProviderClass;
1298:    descriptor: I
1299:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1300:    ConstantValue: int 2130903075
1301-
1302-  public static int actionViewClass;
1303:    descriptor: I
1304:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1305:    ConstantValue: int 2130903077
1306-
1307-  public static int activityChooserViewStyle;
1308:    descriptor: I
1309:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1310:    ConstantValue: int 2130903079
1311-
1312-  public static int alertDialogButtonGroupStyle;
1313:    descriptor: I
1314:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1315:    ConstantValue: int 2130903081
1316-
1317-  public static int alertDialogCenterButtons;
1318:    descriptor: I
1319:    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
1320:    ConstantValue: int 2130903082
1321-

 succeeded in 102ms:
9) ACC_PUBLIC, ACC_STATIC
971:    ConstantValue: int 2131230754
972-

 succeeded in 105ms:
tValue: int 2131230745
617-
618-  public static int accessibility_custom_action_18;
619:    descriptor: I
620-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
621:    ConstantValue: int 2131230746
622-
623-  public static int accessibility_custom_action_19;
624:    descriptor: I
625-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
626:    ConstantValue: int 2131230747
627-
628-  public static int accessibility_custom_action_2;
629:    descriptor: I
630-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
631:    ConstantValue: int 2131230748
632-
633-  public static int accessibility_custom_action_20;
634:    descriptor: I
635-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
636:    ConstantValue: int 2131230749
637-
638-  public static int accessibility_custom_action_21;
639:    descriptor: I
640-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
641:    ConstantValue: int 2131230750
642-
643-  public static int accessibility_custom_action_22;
644:    descriptor: I
645-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
646:    ConstantValue: int 2131230751
647-
648-  public static int accessibility_custom_action_23;
649:    descriptor: I
650-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
651:    ConstantValue: int 2131230752
652-
653-  public static int accessibility_custom_action_24;
654:    descriptor: I
655-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
656:    ConstantValue: int 2131230753
657-
658-  public static int accessibility_custom_action_25;
659:    descriptor: I
660-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
661:    ConstantValue: int 2131230754
662-
663-  public static int accessibility_custom_action_26;
664:    descriptor: I
665-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
666:    ConstantValue: int 2131230755
667-
668-  public static int accessibility_custom_action_27;
669:    descriptor: I
670-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
671:    ConstantValue: int 2131230756
672-
673-  public static int accessibility_custom_action_28;
674:    descriptor: I
675-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
676:    ConstantValue: int 2131230757
677-
678-  public static int accessibility_custom_action_29;
679:    descriptor: I

exec
/bin/bash -lc "printf '2130903040=0x%08x\\n2130903705=0x%08x\\n2131230726=0x%08x\\n2131230813=0x%08x\\n2131231077=0x%08x\\n2131690017=0x%08x\\n2130903769=0x%08x\\n' 2130903040 2130903705 2131230726 2131230813 2131231077 2131690017 2130903769" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
2130903040=0x7f030000
2130903705=0x7f030299
2131230726=0x7f080006
2131230813=0x7f08005d
2131231077=0x7f080165
2131690017=0x7f0f0221
2130903769=0x7f0302d9

codex
One issue is surfacing in the seeder design: `R$styleable` does not only contain `int[]`; it also contains static integer index constants. I’m verifying that because seeding those as resource ids would pollute the callback map with values like `0`, `1`, etc.
exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -verbose '"'androidx.constraintlayout.widget.R"'$styleable'"' | rg -n \"ConstraintLayout_Layout|ConstraintSet|ConstantValue|descriptor: I|descriptor: \\[I\" -A4 -B2 | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 183ms:
out_constraintHorizontal_weight
854:   #843 = Utf8               ConstraintSet_layout_constraintLeft_creator
855:   #844 = Utf8               ConstraintSet_layout_constraintLeft_toLeftOf
856:   #845 = Utf8               ConstraintSet_layout_constraintLeft_toRightOf
857:   #846 = Utf8               ConstraintSet_layout_constraintRight_creator
858:   #847 = Utf8               ConstraintSet_layout_constraintRight_toLeftOf
859:   #848 = Utf8               ConstraintSet_layout_constraintRight_toRightOf
860:   #849 = Utf8               ConstraintSet_layout_constraintStart_toEndOf
861:   #850 = Utf8               ConstraintSet_layout_constraintStart_toStartOf
862:   #851 = Utf8               ConstraintSet_layout_constraintTag
863:   #852 = Utf8               ConstraintSet_layout_constraintTop_creator
864:   #853 = Utf8               ConstraintSet_layout_constraintTop_toBottomOf
865:   #854 = Utf8               ConstraintSet_layout_constraintTop_toTopOf
866:   #855 = Utf8               ConstraintSet_layout_constraintVertical_bias
867:   #856 = Utf8               ConstraintSet_layout_constraintVertical_chainStyle
868:   #857 = Utf8               ConstraintSet_layout_constraintVertical_weight
869:   #858 = Utf8               ConstraintSet_layout_constraintWidth_default
870:   #859 = Utf8               ConstraintSet_layout_constraintWidth_max
871:   #860 = Utf8               ConstraintSet_layout_constraintWidth_min
872:   #861 = Utf8               ConstraintSet_layout_constraintWidth_percent
873:   #862 = Utf8               ConstraintSet_layout_editor_absoluteX
874:   #863 = Utf8               ConstraintSet_layout_editor_absoluteY
875:   #864 = Utf8               ConstraintSet_layout_goneMarginBaseline
876:   #865 = Utf8               ConstraintSet_layout_goneMarginBottom
877:   #866 = Utf8               ConstraintSet_layout_goneMarginEnd
878:   #867 = Utf8               ConstraintSet_layout_goneMarginLeft
879:   #868 = Utf8               ConstraintSet_layout_goneMarginRight
880:   #869 = Utf8               ConstraintSet_layout_goneMarginStart
881:   #870 = Utf8               ConstraintSet_layout_goneMarginTop
882:   #871 = Utf8               ConstraintSet_layout_marginBaseline
883:   #872 = Utf8               ConstraintSet_layout_wrapBehaviorInParent
884:   #873 = Utf8               ConstraintSet_motionProgress
885:   #874 = Utf8               ConstraintSet_motionStagger
886:   #875 = Utf8               ConstraintSet_pathMotionArc
887:   #876 = Utf8               ConstraintSet_pivotAnchor
888:   #877 = Utf8               ConstraintSet_polarRelativeTo
889:   #878 = Utf8               ConstraintSet_quantizeMotionSteps
890:   #879 = Utf8               ConstraintSet_transitionEasing
891:   #880 = Utf8               ConstraintSet_transitionPathRotate
892-   #881 = Utf8               CustomAttribute
893-   #882 = Utf8               CustomAttribute_attributeName
894-   #883 = Utf8               CustomAttribute_customBoolean
895-   #884 = Utf8               CustomAttribute_customColorDrawableValue
--
1910-  #1899 = Integer            2130903660
1911-  #1900 = Integer            2130903727
1912:  #1901 = NameAndType        #525:#9      // ConstraintLayout_Layout:[I

exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -verbose '"'androidx.constraintlayout.widget.R"'$styleable'"' | sed -n '2100,2320p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 183ms:
4071
  #2250 = Integer            2130904072
  #2251 = Integer            2130904074
  #2252 = Integer            2130904185
  #2253 = Integer            2130904186
  #2254 = Integer            2130904187
  #2255 = Integer            2130904230
  #2256 = Integer            2130904242
  #2257 = Integer            2130904243
  #2258 = NameAndType        #1340:#9     // SwitchCompat:[I
  #2259 = Fieldref           #2.#2258     // androidx/constraintlayout/widget/R$styleable.SwitchCompat:[I
  #2260 = Integer            16842906
  #2261 = Integer            16842907
  #2262 = Integer            16843105
  #2263 = Integer            16843106
  #2264 = Integer            16843107
  #2265 = Integer            16844165
  #2266 = NameAndType        #1355:#9     // TextAppearance:[I
  #2267 = Fieldref           #2.#2266     // androidx/constraintlayout/widget/R$styleable.TextAppearance:[I
  #2268 = Integer            2130904154
  #2269 = NameAndType        #1372:#9     // TextEffects:[I
  #2270 = Fieldref           #2.#2269     // androidx/constraintlayout/widget/R$styleable.TextEffects:[I
  #2271 = Integer            2130903184
  #2272 = Integer            2130903271
  #2273 = Integer            2130903272
  #2274 = Integer            2130903756
  #2275 = Integer            2130903811
  #2276 = Integer            2130903820
  #2277 = Integer            2130903882
  #2278 = Integer            2130903883
  #2279 = Integer            2130904064
  #2280 = Integer            2130904065
  #2281 = Integer            2130904206
  #2282 = Integer            2130904207
  #2283 = Integer            2130904208
  #2284 = Integer            2130904209
  #2285 = Integer            2130904210
  #2286 = Integer            2130904211
  #2287 = Integer            2130904213
  #2288 = Integer            2130904214
  #2289 = NameAndType        #1387:#9     // Toolbar:[I
  #2290 = Fieldref           #2.#2289     // androidx/constraintlayout/widget/R$styleable.Toolbar:[I
  #2291 = NameAndType        #1418:#9     // Transform:[I
  #2292 = Fieldref           #2.#2291     // androidx/constraintlayout/widget/R$styleable.Transform:[I
  #2293 = Integer            2130903109
  #2294 = Integer            2130903347
  #2295 = Integer            2130903348
  #2296 = Integer            2130903447
  #2297 = Integer            2130903872
  #2298 = Integer            2130904034
  #2299 = Integer            2130904245
  #2300 = Integer            2130904247
  #2301 = NameAndType        #1431:#9     // Transition:[I
  #2302 = Fieldref           #2.#2301     // androidx/constraintlayout/widget/R$styleable.Transition:[I
  #2303 = Integer            2130903960
  #2304 = Integer            2130903961
  #2305 = Integer            2130903962
  #2306 = Integer            2130903963
  #2307 = NameAndType        #1443:#9     // Variant:[I
  #2308 = Fieldref           #2.#2307     // androidx/constraintlayout/widget/R$styleable.Variant:[I
  #2309 = Integer            16842752

exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -verbose '"'androidx.constraintlayout.widget.R"'$styleable'"' | rg -n \"public static int ConstraintLayout_Layout_android_orientation|public static int\\[\\] ConstraintLayout_Layout|ConstantValue: int\" -A3 -B2 | sed -n '1,120p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 178ms:
2355-    descriptor: I
2356-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2357:    ConstantValue: int 0
2358-
2359-  public static int ActionBar_backgroundSplit;
2360-    descriptor: I
2361-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2362:    ConstantValue: int 1
2363-
2364-  public static int ActionBar_backgroundStacked;
2365-    descriptor: I
2366-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2367:    ConstantValue: int 2
2368-
2369-  public static int ActionBar_contentInsetEnd;
2370-    descriptor: I
2371-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2372:    ConstantValue: int 3
2373-
2374-  public static int ActionBar_contentInsetEndWithActions;
2375-    descriptor: I
2376-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2377:    ConstantValue: int 4
2378-
2379-  public static int ActionBar_contentInsetLeft;
2380-    descriptor: I
2381-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2382:    ConstantValue: int 5
2383-
2384-  public static int ActionBar_contentInsetRight;
2385-    descriptor: I
2386-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2387:    ConstantValue: int 6
2388-
2389-  public static int ActionBar_contentInsetStart;
2390-    descriptor: I
2391-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2392:    ConstantValue: int 7
2393-
2394-  public static int ActionBar_contentInsetStartWithNavigation;
2395-    descriptor: I
2396-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2397:    ConstantValue: int 8
2398-
2399-  public static int ActionBar_customNavigationLayout;
2400-    descriptor: I
2401-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2402:    ConstantValue: int 9
2403-
2404-  public static int ActionBar_displayOptions;
2405-    descriptor: I
2406-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2407:    ConstantValue: int 10
2408-
2409-  public static int ActionBar_divider;
2410-    descriptor: I
2411-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2412:    ConstantValue: int 11
2413-
2414-  public static int ActionBar_elevation;
2415-    descriptor: I
2416-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2417:    ConstantValue: int 12
2418-
2419-  public static int ActionBar_height;
2420-    descriptor: I
2421-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2422:    ConstantValue: int 13
2423-
2424-  public static int ActionBar_hideOnContentScroll;
2425-    descriptor: I
2426-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2427:    ConstantValue: int 14
2428-
2429-  public static int ActionBar_homeAsUpIndicator;
2430-    descriptor: I
2431-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2432:    ConstantValue: int 15
2433-
2434-  public static int ActionBar_homeLayout;
2435-    descriptor: I
2436-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2437:    ConstantValue: int 16
2438-
2439-  public static int ActionBar_icon;
2440-    descriptor: I
2441-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2442:    ConstantValue: int 17
2443-
2444-  public static int ActionBar_indeterminateProgressStyle;
2445-    descriptor: I
2446-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2447:    ConstantValue: int 18
2448-
2449-  public static int ActionBar_itemPadding;
2450-    descriptor: I
2451-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2452:    ConstantValue: int 19
2453-
2454-  public static int ActionBar_logo;
2455-    descriptor: I
2456-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2457:    ConstantValue: int 20
2458-
2459-  public static int ActionBar_navigationMode;
2460-    descriptor: I
2461-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2462:    ConstantValue: int 21
2463-
2464-  public static int ActionBar_popupTheme;
2465-    descriptor: I
2466-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2467:    ConstantValue: int 22
2468-
2469-  public static int ActionBar_progressBarPadding;
2470-    descriptor: I
2471-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2472:    ConstantValue: int 23
2473-
2474-  public static int ActionBar_progressBarStyle;

exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; javap -classpath "$RJAR" -p -verbose '"'androidx.constraintlayout.widget.R"'$styleable'"' | rg -n \"public static int\\[\\] ActionBar|public static int ActionBar_background|descriptor: \\[I|descriptor: I\" -A2 -B1 | sed -n '1,100p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 179ms:
2349-{
2350:  public static int[] ActionBar;
2351:    descriptor: [I
2352-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2353-
2354:  public static int ActionBar_background;
2355:    descriptor: I
2356-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2357-    ConstantValue: int 0
2358-
2359:  public static int ActionBar_backgroundSplit;
2360:    descriptor: I
2361-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2362-    ConstantValue: int 1
2363-
2364:  public static int ActionBar_backgroundStacked;
2365:    descriptor: I
2366-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2367-    ConstantValue: int 2
--
2369-  public static int ActionBar_contentInsetEnd;
2370:    descriptor: I
2371-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2372-    ConstantValue: int 3
--
2374-  public static int ActionBar_contentInsetEndWithActions;
2375:    descriptor: I
2376-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2377-    ConstantValue: int 4
--
2379-  public static int ActionBar_contentInsetLeft;
2380:    descriptor: I
2381-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2382-    ConstantValue: int 5
--
2384-  public static int ActionBar_contentInsetRight;
2385:    descriptor: I
2386-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2387-    ConstantValue: int 6
--
2389-  public static int ActionBar_contentInsetStart;
2390:    descriptor: I
2391-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2392-    ConstantValue: int 7
--
2394-  public static int ActionBar_contentInsetStartWithNavigation;
2395:    descriptor: I
2396-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2397-    ConstantValue: int 8
--
2399-  public static int ActionBar_customNavigationLayout;
2400:    descriptor: I
2401-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2402-    ConstantValue: int 9
--
2404-  public static int ActionBar_displayOptions;
2405:    descriptor: I
2406-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2407-    ConstantValue: int 10
--
2409-  public static int ActionBar_divider;
2410:    descriptor: I
2411-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2412-    ConstantValue: int 11
--
2414-  public static int ActionBar_elevation;
2415:    descriptor: I
2416-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2417-    ConstantValue: int 12
--
2419-  public static int ActionBar_height;
2420:    descriptor: I
2421-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2422-    ConstantValue: int 13
--
2424-  public static int ActionBar_hideOnContentScroll;
2425:    descriptor: I
2426-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2427-    ConstantValue: int 14
--
2429-  public static int ActionBar_homeAsUpIndicator;
2430:    descriptor: I
2431-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2432-    ConstantValue: int 15
--
2434-  public static int ActionBar_homeLayout;
2435:    descriptor: I
2436-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2437-    ConstantValue: int 16
--
2439-  public static int ActionBar_icon;
2440:    descriptor: I
2441-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2442-    ConstantValue: int 17
--
2444-  public static int ActionBar_indeterminateProgressStyle;
2445:    descriptor: I
2446-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2447-    ConstantValue: int 18
--

exec
/bin/bash -lc 'LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; unzip -Z1 "$LAYOUTLIB" | rg '"'BridgeContext.class|obtainStyledAttributes' | sed -n '1,80p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
com/android/layoutlib/bridge/android/BridgeContext.class

exec
/bin/bash -lc 'LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; javap -classpath "$LAYOUTLIB" -p '"'com.android.layoutlib.bridge.android.BridgeContext' | rg -n \"obtainStyledAttributes|getOrGenerate|resolveResourceId\" -A2 -B2 | sed -n '1,160p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 125ms:
exec
/bin/bash -lc 'LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; javap -classpath "$LAYOUTLIB" -p -c '"'com.android.layoutlib.bridge.android.BridgeContext' | rg -n \"obtainStyledAttributes\\(|searchAttrs|int\\[\\]|\\[I|BridgeTypedArray\" -A60 -B8 | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 166ms:
1234-     810: areturn
1235-
1236-  public java.lang.String getSystemServiceName(java.lang.Class<?>);
1237-    Code:
1238-       0: aload_1
1239-       1: invokestatic  #868                // Method android/app/SystemServiceRegistry.getSystemServiceName:(Ljava/lang/Class;)Ljava/lang/String;
1240-       4: areturn
1241-
1242:  public final android.content.res.BridgeTypedArray internalObtainStyledAttributes(int, int[]) throws android.content.res.Resources$NotFoundException;
1243-    Code:
1244-       0: aconst_null
1245-       1: astore_3
1246-       2: iload_1
1247-       3: ifeq          75
1248-       6: aload_0
1249-       7: iload_1
1250-       8: invokevirtual #877                // Method getStyleByDynamicId:(I)Lcom/android/ide/common/rendering/api/StyleResourceValue;
1251-      11: astore_3
1252-      12: aload_3
1253-      13: ifnonnull     38
1254-      16: aload_0
1255-      17: iload_1
1256-      18: invokevirtual #879                // Method resolveId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1257-      21: astore        4
1258-      23: aload         4
1259-      25: ifnull        38
1260-      28: aload_0
1261-      29: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1262-      32: aload         4
1263-      34: invokevirtual #883                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.getStyle:(Lcom/android/ide/common/rendering/api/ResourceReference;)Lcom/android/ide/common/rendering/api/StyleResourceValue;
1264-      37: astore_3
1265-      38: aload_3
1266-      39: ifnonnull     75
1267-      42: invokestatic  #645                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
1268-      45: ldc_w         #885                // String info
1269-      48: new           #661                // class java/lang/StringBuilder
1270-      51: dup
1271-      52: invokespecial #662                // Method java/lang/StringBuilder."<init>":()V
1272-      55: ldc_w         #887                // String Failed to find style with
1273-      58: invokevirtual #668                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
1274-      61: iload_1
1275-      62: invokevirtual #890                // Method java/lang/StringBuilder.append:(I)Ljava/lang/StringBuilder;
1276-      65: invokevirtual #671                // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
1277-      68: aconst_null
1278-      69: aconst_null
1279-      70: invokeinterface #862,  5          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.warning:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V
1280-      75: aload_0
1281-      76: getfield      #892                // Field mTypedArrayCache:Lcom/android/layoutlib/bridge/android/BridgeContext$TypedArrayCache;
1282-      79: ifnonnull     93
1283-      82: aload_0
1284-      83: new           #7                  // class com/android/layoutlib/bridge/android/BridgeContext$TypedArrayCache
1285-      86: dup
1286-      87: invokespecial #893                // Method com/android/layoutlib/bridge/android/BridgeContext$TypedArrayCache."<init>":()V
1287-      90: putfield      #892                // Field mTypedArrayCache:Lcom/android/layoutlib/bridge/android/BridgeContext$TypedArrayCache;
1288-      93: aload_0
1289-      94: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1290-      97: invokevirtual #897                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.getAllThemes:()Ljava/util/List;
1291-     100: astore        4
1292-     102: aload_0
1293-     103: getfield      #892                // Field mTypedArrayCache:Lcom/android/layoutlib/bridge/android/BridgeContext$TypedArrayCache;
1294-     106: aload_2
1295-     107: aload         4
1296-     109: iload_1
1297:     110: invokevirtual #900                // Method com/android/layoutlib/bridge/android/BridgeContext$TypedArrayCache.get:([ILjava/util/List;I)Landroid/util/Pair;
1298-     113: astore        5
1299-     115: aload         5
1300-     117: ifnonnull     141
1301-     120: aload_0
1302-     121: aload_3
1303-     122: aload_2
1304:     123: invokevirtual #904                // Method createStyleBasedTypedArray:(Lcom/android/ide/common/rendering/api/StyleResourceValue;[I)Landroid/util/Pair;
1305-     126: astore        5
1306-     128: aload_0
1307-     129: getfield      #892                // Field mTypedArrayCache:Lcom/android/layoutlib/bridge/android/BridgeContext$TypedArrayCache;
1308-     132: aload_2
1309-     133: aload         4
1310-     135: iload_1
1311-     136: aload         5
1312:     138: invokevirtual #907                // Method com/android/layoutlib/bridge/android/BridgeContext$TypedArrayCache.put:([ILjava/util/List;ILandroid/util/Pair;)V
1313-     141: aload         5
1314-     143: getfield      #912                // Field android/util/Pair.second:Ljava/lang/Object;
1315-     146: ifnull        239
1316-     149: aload_0
1317-     150: invokevirtual #914                // Method getCurrentParser:()Lcom/android/layoutlib/bridge/android/BridgeXmlBlockParser;
1318-     153: astore        6
1319-     155: aload         6
1320-     157: ifnull        168
1321-     160: aload         6
1322-     162: invokevirtual #917                // Method com/android/layoutlib/bridge/android/BridgeXmlBlockParser.getViewCookie:()Ljava/lang/Object;
1323-     165: goto          169
1324-     168: aconst_null
1325-     169: astore        7
1326-     171: aload         7
1327-     173: ifnull        239
1328-     176: aload_0
1329-     177: getfield      #181                // Field mDefaultPropMaps:Ljava/util/Map;
1330-     180: aload         7
1331-     182: invokeinterface #920,  2          // InterfaceMethod java/util/Map.get:(Ljava/lang/Object;)Ljava/lang/Object;
1332-     187: checkcast     #919                // class java/util/Map
1333-     190: astore        8
1334-     192: aload         8
1335-     194: ifnonnull     224
1336-     197: aload         5
1337-     199: getfield      #912                // Field android/util/Pair.second:Ljava/lang/Object;
1338-     202: checkcast     #919                // class java/util/Map
1339-     205: astore        8
1340-     207: aload_0
1341-     208: getfield      #181                // Field mDefaultPropMaps:Ljava/util/Map;
1342-     211: aload         7
1343-     213: aload         8
1344-     215: invokeinterface #921,  3          // InterfaceMethod java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
1345-     220: pop
1346-     221: goto          239
1347-     224: aload         8
1348-     226: aload         5
1349-     228: getfield      #912                // Field android/util/Pair.second:Ljava/lang/Object;
1350-     231: checkcast     #919                // class java/util/Map
1351-     234: invokeinterface #925,  2          // InterfaceMethod java/util/Map.putAll:(Ljava/util/Map;)V
1352-     239: aload         5
1353-     241: getfield      #928                // Field android/util/Pair.first:Ljava/lang/Object;
1354:     244: checkcast     #930                // class android/content/res/BridgeTypedArray
1355-     247: areturn
1356-
1357:  public android.content.res.BridgeTypedArray internalObtainStyledAttributes(android.util.AttributeSet, int[], int, int);
1358-    Code:
1359-       0: aconst_null
1360-       1: astore        5
1361-       3: aconst_null
1362-       4: astore        6
1363-       6: aload_1
1364-       7: instanceof    #451                // class com/android/layoutlib/bridge/android/BridgeXmlBlockParser
1365-      10: ifeq          80
1366-      13: aload_1
1367-      14: checkcast     #451                // class com/android/layoutlib/bridge/android/BridgeXmlBlockParser
1368-      17: astore        9
1369-      19: aload         9
1370-      21: invokevirtual #917                // Method com/android/layoutlib/bridge/android/BridgeXmlBlockParser.getViewCookie:()Ljava/lang/Object;
1371-      24: astore        6
1372-      26: aload         6
1373-      28: ifnull        52
1374-      31: aload_0
1375-      32: getfield      #181                // Field mDefaultPropMaps:Ljava/util/Map;
1376-      35: aload         6
1377-      37: invokedynamic #962,  0            // InvokeDynamic #0:apply:()Ljava/util/function/Function;
1378-      42: invokeinterface #966,  3          // InterfaceMethod java/util/Map.computeIfAbsent:(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;
1379-      47: checkcast     #919                // class java/util/Map
1380-      50: astore        5
1381-      52: aload         9
1382-      54: invokevirtual #972                // Method com/android/layoutlib/bridge/android/BridgeXmlBlockParser.getFileResourceNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1383-      57: astore        7
1384-      59: new           #974                // class com/android/layoutlib/bridge/android/XmlPullParserResolver
1385-      62: dup
1386-      63: aload         9
1387-      65: aload_0
1388-      66: getfield      #201                // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
1389-      69: invokevirtual #978                // Method com/android/ide/common/rendering/api/LayoutlibCallback.getImplicitNamespaces:()Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;
1390-      72: invokespecial #981                // Method com/android/layoutlib/bridge/android/XmlPullParserResolver."<init>":(Lorg/xmlpull/v1/XmlPullParser;Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;)V
1391-      75: astore        8
1392-      77: goto          132
1393-      80: aload_1
1394-      81: instanceof    #983                // class com/android/layoutlib/bridge/android/BridgeLayoutParamsMapAttributes
1395-      84: ifeq          100
1396-      87: getstatic     #343                // Field com/android/ide/common/rendering/api/ResourceNamespace.RES_AUTO:Lcom/android/ide/common/rendering/api/ResourceNamespace;
1397-      90: astore        7
1398-      92: getstatic     #987                // Field com/android/ide/common/rendering/api/ResourceNamespace$Resolver.EMPTY_RESOLVER:Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;
1399-      95: astore        8
1400-      97: goto          132
1401-     100: aload_1
1402-     101: ifnull        122
1403-     104: invokestatic  #645                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
1404-     107: ldc_w         #647                // String broken
1405-     110: ldc_w         #989                // String Parser is not a BridgeXmlBlockParser!
1406-     113: aconst_null
1407-     114: aconst_null
1408-     115: invokeinterface #659,  5          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V
1409-     120: aconst_null
1410-     121: areturn
1411-     122: getstatic     #343                // Field com/android/ide/common/rendering/api/ResourceNamespace.RES_AUTO:Lcom/android/ide/common/rendering/api/ResourceNamespace;
1412-     125: astore        7
1413-     127: getstatic     #987                // Field com/android/ide/common/rendering/api/ResourceNamespace$Resolver.EMPTY_RESOLVER:Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;
1414-     130: astore        8
1415-     132: aload_0
1416-     133: aload_2
1417:     134: invokevirtual #993                // Method searchAttrs:([I)Ljava/util/List;
1418-     137: astore        9
1419-     139: aload_0
1420-     140: getfield      #370                // Field mSystemResources:Landroid/content/res/Resources;
1421-     143: aload_2
1422-     144: arraylength
1423:     145: invokestatic  #997                // Method android/content/res/Resources_Delegate.newTypeArray:(Landroid/content/res/Resources;I)Landroid/content/res/BridgeTypedArray;
1424-     148: astore        10
1425-     150: aconst_null
1426-     151: astore        11
1427-     153: aload_1
1428-     154: ifnull        211
1429-     157: aload_1
1430-     158: aconst_null
1431-     159: ldc_w         #998                // String style
1432-     162: invokeinterface #1002,  3         // InterfaceMethod android/util/AttributeSet.getAttributeValue:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
1433-     167: astore        12
1434-     169: aload         12
1435-     171: ifnull        211
1436-     174: aload_0
1437-     175: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1438-     178: new           #1004               // class com/android/layoutlib/bridge/android/UnresolvedResourceValue
1439-     181: dup
1440-     182: aload         12
1441-     184: aload         7
1442-     186: aload         8
1443-     188: invokespecial #1007               // Method com/android/layoutlib/bridge/android/UnresolvedResourceValue."<init>":(Ljava/lang/String;Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;)V
1444-     191: invokevirtual #491                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.resolveResValue:(Lcom/android/ide/common/rendering/api/ResourceValue;)Lcom/android/ide/common/rendering/api/ResourceValue;
1445-     194: astore        13
1446-     196: aload         13
1447-     198: instanceof    #495                // class com/android/ide/common/rendering/api/StyleResourceValue
1448-     201: ifeq          211
1449-     204: aload         13
1450-     206: checkcast     #495                // class com/android/ide/common/rendering/api/StyleResourceValue
1451-     209: astore        11
1452-     211: aconst_null
1453-     212: astore        12
1454-     214: iload_3
1455-     215: ifeq          327
1456-     218: aload_0
1457-     219: iload_3
1458-     220: invokevirtual #1010               // Method searchAttr:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1459-     223: astore        13
1460-     225: aload         13
1461-     227: ifnonnull     266
1462-     230: invokestatic  #645                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
1463-     233: ldc_w         #1012               // String resources.resolve
1464-     236: new           #661                // class java/lang/StringBuilder
1465-     239: dup
1466-     240: invokespecial #662                // Method java/lang/StringBuilder."<init>":()V
1467-     243: ldc_w         #1014               // String Failed to find the style corresponding to the id
1468-     246: invokevirtual #668                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
1469-     249: iload_3
1470-     250: invokevirtual #890                // Method java/lang/StringBuilder.append:(I)Ljava/lang/StringBuilder;
1471-     253: invokevirtual #671                // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
1472-     256: aconst_null
1473-     257: aconst_null
1474-     258: invokeinterface #659,  5          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V
1475-     263: goto          327
1476-     266: aload_0
1477-     267: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1478-     270: aload         13
1479-     272: invokevirtual #487                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.findItemInTheme:(Lcom/android/ide/common/rendering/api/ResourceReference;)Lcom/android/ide/common/rendering/api/ResourceValue;
1480-     275: astore        14
1481-     277: aload         14
1482-     279: ifnull        327
1483-     282: aload         6
--
1760-     941: aload         10
1761-     943: iload         13
1762-     945: aload         15
1763-     947: aload         14
1764-     949: invokevirtual #1034               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1765-     952: aload         14
1766-     954: invokevirtual #1072               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getResourceId:()I
1767-     957: aload         17
1768:     959: invokevirtual #1076               // Method android/content/res/BridgeTypedArray.bridgeSetValue:(ILjava/lang/String;Lcom/android/ide/common/rendering/api/ResourceNamespace;ILcom/android/ide/common/rendering/api/ResourceValue;)V

exec
/bin/bash -lc 'LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; javap -classpath "$LAYOUTLIB" -p -c '"'com.android.layoutlib.bridge.android.BridgeContext' | rg -n \"searchAttrs|searchAttr\\(\" -A120 -B8 | sed -n '1,260p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 149ms:
1409-     120: aconst_null
1410-     121: areturn
1411-     122: getstatic     #343                // Field com/android/ide/common/rendering/api/ResourceNamespace.RES_AUTO:Lcom/android/ide/common/rendering/api/ResourceNamespace;
1412-     125: astore        7
1413-     127: getstatic     #987                // Field com/android/ide/common/rendering/api/ResourceNamespace$Resolver.EMPTY_RESOLVER:Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;
1414-     130: astore        8
1415-     132: aload_0
1416-     133: aload_2
1417:     134: invokevirtual #993                // Method searchAttrs:([I)Ljava/util/List;
1418-     137: astore        9
1419-     139: aload_0
1420-     140: getfield      #370                // Field mSystemResources:Landroid/content/res/Resources;
1421-     143: aload_2
1422-     144: arraylength
1423-     145: invokestatic  #997                // Method android/content/res/Resources_Delegate.newTypeArray:(Landroid/content/res/Resources;I)Landroid/content/res/BridgeTypedArray;
1424-     148: astore        10
1425-     150: aconst_null
1426-     151: astore        11
1427-     153: aload_1
1428-     154: ifnull        211
1429-     157: aload_1
1430-     158: aconst_null
1431-     159: ldc_w         #998                // String style
1432-     162: invokeinterface #1002,  3         // InterfaceMethod android/util/AttributeSet.getAttributeValue:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
1433-     167: astore        12
1434-     169: aload         12
1435-     171: ifnull        211
1436-     174: aload_0
1437-     175: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1438-     178: new           #1004               // class com/android/layoutlib/bridge/android/UnresolvedResourceValue
1439-     181: dup
1440-     182: aload         12
1441-     184: aload         7
1442-     186: aload         8
1443-     188: invokespecial #1007               // Method com/android/layoutlib/bridge/android/UnresolvedResourceValue."<init>":(Ljava/lang/String;Lcom/android/ide/common/rendering/api/ResourceNamespace;Lcom/android/ide/common/rendering/api/ResourceNamespace$Resolver;)V
1444-     191: invokevirtual #491                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.resolveResValue:(Lcom/android/ide/common/rendering/api/ResourceValue;)Lcom/android/ide/common/rendering/api/ResourceValue;
1445-     194: astore        13
1446-     196: aload         13
1447-     198: instanceof    #495                // class com/android/ide/common/rendering/api/StyleResourceValue
1448-     201: ifeq          211
1449-     204: aload         13
1450-     206: checkcast     #495                // class com/android/ide/common/rendering/api/StyleResourceValue
1451-     209: astore        11
1452-     211: aconst_null
1453-     212: astore        12
1454-     214: iload_3
1455-     215: ifeq          327
1456-     218: aload_0
1457-     219: iload_3
1458-     220: invokevirtual #1010               // Method searchAttr:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1459-     223: astore        13
1460-     225: aload         13
1461-     227: ifnonnull     266
1462-     230: invokestatic  #645                // Method com/android/layoutlib/bridge/Bridge.getLog:()Lcom/android/ide/common/rendering/api/ILayoutLog;
1463-     233: ldc_w         #1012               // String resources.resolve
1464-     236: new           #661                // class java/lang/StringBuilder
1465-     239: dup
1466-     240: invokespecial #662                // Method java/lang/StringBuilder."<init>":()V
1467-     243: ldc_w         #1014               // String Failed to find the style corresponding to the id
1468-     246: invokevirtual #668                // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
1469-     249: iload_3
1470-     250: invokevirtual #890                // Method java/lang/StringBuilder.append:(I)Ljava/lang/StringBuilder;
1471-     253: invokevirtual #671                // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
1472-     256: aconst_null
1473-     257: aconst_null
1474-     258: invokeinterface #659,  5          // InterfaceMethod com/android/ide/common/rendering/api/ILayoutLog.error:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V
1475-     263: goto          327
1476-     266: aload_0
1477-     267: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1478-     270: aload         13
1479-     272: invokevirtual #487                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.findItemInTheme:(Lcom/android/ide/common/rendering/api/ResourceReference;)Lcom/android/ide/common/rendering/api/ResourceValue;
1480-     275: astore        14
1481-     277: aload         14
1482-     279: ifnull        327
1483-     282: aload         6
1484-     284: ifnull        301
1485-     287: aload_0
1486-     288: getfield      #183                // Field mDefaultStyleMap:Ljava/util/Map;
1487-     291: aload         6
1488-     293: aload         13
1489-     295: invokeinterface #921,  3          // InterfaceMethod java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
1490-     300: pop
1491-     301: aload_0
1492-     302: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1493-     305: aload         14
1494-     307: invokevirtual #491                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.resolveResValue:(Lcom/android/ide/common/rendering/api/ResourceValue;)Lcom/android/ide/common/rendering/api/ResourceValue;
1495-     310: astore        14
1496-     312: aload         14
1497-     314: instanceof    #495                // class com/android/ide/common/rendering/api/StyleResourceValue
1498-     317: ifeq          327
1499-     320: aload         14
1500-     322: checkcast     #495                // class com/android/ide/common/rendering/api/StyleResourceValue
1501-     325: astore        12
1502-     327: aload         12
1503-     329: ifnonnull     555
1504-     332: iload         4
1505-     334: ifeq          555
1506-     337: aload_0
1507-     338: iload         4
1508-     340: invokevirtual #877                // Method getStyleByDynamicId:(I)Lcom/android/ide/common/rendering/api/StyleResourceValue;
1509-     343: astore        13
1510-     345: aload         13
1511-     347: ifnull        357
1512-     350: aload         13
1513-     352: astore        12
1514-     354: goto          555
1515-     357: iload         4
1516-     359: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1517-     362: astore        14
1518-     364: aload         14
1519-     366: ifnonnull     380
1520-     369: aload_0
1521-     370: getfield      #201                // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
1522-     373: iload         4
1523-     375: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1524-     378: astore        14
1525-     380: aload         14
1526-     382: ifnull        526
1527-     385: aload         14
1528-     387: invokevirtual #477                // Method com/android/ide/common/rendering/api/ResourceReference.getResourceType:()Lcom/android/resources/ResourceType;
1529-     390: getstatic     #1017               // Field com/android/resources/ResourceType.STYLE:Lcom/android/resources/ResourceType;
1530-     393: if_acmpne     483
1531-     396: aload_0
1532-     397: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1533-     400: aload         14
1534-     402: invokevirtual #883                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.getStyle:(Lcom/android/ide/common/rendering/api/ResourceReference;)Lcom/android/ide/common/rendering/api/StyleResourceValue;
1535-     405: astore        13
1536-     407: aload         13
1537-     409: ifnull        443
--
1835-  public void unregisterComponentCallbacks(android.content.ComponentCallbacks);
1836-    Code:
1837-       0: return
1838-
1839-  private android.util.Pair<android.content.res.BridgeTypedArray, java.util.Map<com.android.ide.common.rendering.api.ResourceReference, com.android.ide.common.rendering.api.ResourceValue>> createStyleBasedTypedArray(com.android.ide.common.rendering.api.StyleResourceValue, int[]) throws android.content.res.Resources$NotFoundException;
1840-    Code:
1841-       0: aload_0
1842-       1: aload_2
1843:       2: invokevirtual #993                // Method searchAttrs:([I)Ljava/util/List;
1844-       5: astore_3
1845-       6: aload_0
1846-       7: getfield      #370                // Field mSystemResources:Landroid/content/res/Resources;
1847-      10: aload_2
1848-      11: arraylength
1849-      12: invokestatic  #997                // Method android/content/res/Resources_Delegate.newTypeArray:(Landroid/content/res/Resources;I)Landroid/content/res/BridgeTypedArray;
1850-      15: astore        4
1851-      17: new           #167                // class java/util/HashMap
1852-      20: dup
1853-      21: invokespecial #168                // Method java/util/HashMap."<init>":()V
1854-      24: astore        5
1855-      26: iconst_0
1856-      27: istore        6
1857-      29: iload         6
1858-      31: aload_2
1859-      32: arraylength
1860-      33: if_icmpge     151
1861-      36: aload_3
1862-      37: iload         6
1863-      39: invokeinterface #1032,  2         // InterfaceMethod java/util/List.get:(I)Ljava/lang/Object;
1864-      44: checkcast     #9                  // class com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder
1865-      47: astore        7
1866-      49: aload         7
1867-      51: ifnull        145
1868-      54: aload_1
1869-      55: ifnull        76
1870-      58: aload_0
1871-      59: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1872-      62: aload_1
1873-      63: aload         7
1874-      65: invokevirtual #1040               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.asReference:()Lcom/android/ide/common/rendering/api/ResourceReference;
1875-      68: invokevirtual #1044               // Method com/android/layoutlib/bridge/android/DynamicRenderResources.findItemInStyle:(Lcom/android/ide/common/rendering/api/StyleResourceValue;Lcom/android/ide/common/rendering/api/ResourceReference;)Lcom/android/ide/common/rendering/api/ResourceValue;
1876-      71: astore        8
1877-      73: goto          90
1878-      76: aload_0
1879-      77: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1880-      80: aload         7
1881-      82: invokevirtual #1040               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.asReference:()Lcom/android/ide/common/rendering/api/ResourceReference;
1882-      85: invokevirtual #487                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.findItemInTheme:(Lcom/android/ide/common/rendering/api/ResourceReference;)Lcom/android/ide/common/rendering/api/ResourceValue;
1883-      88: astore        8
1884-      90: aload         8
1885-      92: ifnull        145
1886-      95: aload         5
1887-      97: aload         7
1888-      99: invokevirtual #1040               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.asReference:()Lcom/android/ide/common/rendering/api/ResourceReference;
1889-     102: aload         8
1890-     104: invokeinterface #921,  3          // InterfaceMethod java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
1891-     109: pop
1892-     110: aload_0
1893-     111: getfield      #208                // Field mRenderResources:Lcom/android/layoutlib/bridge/android/DynamicRenderResources;
1894-     114: aload         8
1895-     116: invokevirtual #491                // Method com/android/layoutlib/bridge/android/DynamicRenderResources.resolveResValue:(Lcom/android/ide/common/rendering/api/ResourceValue;)Lcom/android/ide/common/rendering/api/ResourceValue;
1896-     119: astore        8
1897-     121: aload         4
1898-     123: iload         6
1899-     125: aload         7
1900-     127: invokevirtual #1033               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getName:()Ljava/lang/String;
1901-     130: aload         7
1902-     132: invokevirtual #1034               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getNamespace:()Lcom/android/ide/common/rendering/api/ResourceNamespace;
1903-     135: aload         7
1904-     137: invokevirtual #1072               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder.getResourceId:()I
1905-     140: aload         8
1906-     142: invokevirtual #1076               // Method android/content/res/BridgeTypedArray.bridgeSetValue:(ILjava/lang/String;Lcom/android/ide/common/rendering/api/ResourceNamespace;ILcom/android/ide/common/rendering/api/ResourceValue;)V
1907-     145: iinc          6, 1
1908-     148: goto          29
1909-     151: aload         4
1910-     153: invokevirtual #1079               // Method android/content/res/BridgeTypedArray.sealArray:()V
1911-     156: aload         4
1912-     158: aload         5
1913-     160: invokestatic  #621                // Method android/util/Pair.create:(Ljava/lang/Object;Ljava/lang/Object;)Landroid/util/Pair;
1914-     163: areturn
1915-
1916:  private java.util.List<com.android.layoutlib.bridge.android.BridgeContext$AttributeHolder> searchAttrs(int[]);
1917-    Code:
1918-       0: new           #1136               // class java/util/ArrayList
1919-       3: dup
1920-       4: aload_1
1921-       5: arraylength
1922-       6: invokespecial #1139               // Method java/util/ArrayList."<init>":(I)V
1923-       9: astore_2
1924-      10: aload_1
1925-      11: astore_3
1926-      12: aload_3
1927-      13: arraylength
1928-      14: istore        4
1929-      16: iconst_0
1930-      17: istore        5
1931-      19: iload         5
1932-      21: iload         4
1933-      23: if_icmpge     96
1934-      26: aload_3
1935-      27: iload         5
1936-      29: iaload
1937-      30: istore        6
1938-      32: iload         6
1939-      34: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1940-      37: astore        7
1941-      39: aload         7
1942-      41: ifnonnull     55
1943-      44: aload_0
1944-      45: getfield      #201                // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
1945-      48: iload         6
1946-      50: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
1947-      53: astore        7
1948-      55: aload         7
1949-      57: ifnull        82
1950-      60: aload_2
1951-      61: new           #9                  // class com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder
1952-      64: dup
1953-      65: aload_0
1954-      66: iload         6
1955-      68: aload         7
1956-      70: invokespecial #1142               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder."<init>":(Lcom/android/layoutlib/bridge/android/BridgeContext;ILcom/android/ide/common/rendering/api/ResourceReference;)V
1957-      73: invokeinterface #1145,  2         // InterfaceMethod java/util/List.add:(Ljava/lang/Object;)Z
1958-      78: pop
1959-      79: goto          90
1960-      82: aload_2
1961-      83: aconst_null
1962-      84: invokeinterface #1145,  2         // InterfaceMethod java/util/List.add:(Ljava/lang/Object;)Z
1963-      89: pop
1964-      90: iinc          5, 1

exec
/bin/bash -lc 'LAYOUTLIB=server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar; javap -classpath "$LAYOUTLIB" -p -c '"'com.android.layoutlib.bridge.android.BridgeContext' | sed -n '1916,1995p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 159ms:
  private java.util.List<com.android.layoutlib.bridge.android.BridgeContext$AttributeHolder> searchAttrs(int[]);
    Code:
       0: new           #1136               // class java/util/ArrayList
       3: dup
       4: aload_1
       5: arraylength
       6: invokespecial #1139               // Method java/util/ArrayList."<init>":(I)V
       9: astore_2
      10: aload_1
      11: astore_3
      12: aload_3
      13: arraylength
      14: istore        4
      16: iconst_0
      17: istore        5
      19: iload         5
      21: iload         4
      23: if_icmpge     96
      26: aload_3
      27: iload         5
      29: iaload
      30: istore        6
      32: iload         6
      34: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
      37: astore        7
      39: aload         7
      41: ifnonnull     55
      44: aload_0
      45: getfield      #201                // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
      48: iload         6
      50: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
      53: astore        7
      55: aload         7
      57: ifnull        82
      60: aload_2
      61: new           #9                  // class com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder
      64: dup
      65: aload_0
      66: iload         6
      68: aload         7
      70: invokespecial #1142               // Method com/android/layoutlib/bridge/android/BridgeContext$AttributeHolder."<init>":(Lcom/android/layoutlib/bridge/android/BridgeContext;ILcom/android/ide/common/rendering/api/ResourceReference;)V
      73: invokeinterface #1145,  2         // InterfaceMethod java/util/List.add:(Ljava/lang/Object;)Z
      78: pop
      79: goto          90
      82: aload_2
      83: aconst_null
      84: invokeinterface #1145,  2         // InterfaceMethod java/util/List.add:(Ljava/lang/Object;)Z
      89: pop
      90: iinc          5, 1
      93: goto          19
      96: aload_2
      97: areturn

  private com.android.ide.common.rendering.api.ResourceReference searchAttr(int);
    Code:
       0: iload_1
       1: invokestatic  #470                // Method com/android/layoutlib/bridge/Bridge.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
       4: astore_2
       5: aload_2
       6: ifnonnull     18
       9: aload_0
      10: getfield      #201                // Field mLayoutlibCallback:Lcom/android/ide/common/rendering/api/LayoutlibCallback;
      13: iload_1
      14: invokevirtual #471                // Method com/android/ide/common/rendering/api/LayoutlibCallback.resolveResourceId:(I)Lcom/android/ide/common/rendering/api/ResourceReference;
      17: astore_2
      18: aload_2
      19: areturn

  public int getDynamicIdByStyle(com.android.ide.common.rendering.api.StyleResourceValue);
    Code:
       0: aload_1
       1: invokeinterface #1153,  1         // InterfaceMethod com/android/ide/common/rendering/api/StyleResourceValue.isFramework:()Z
       6: ifeq          25
       9: aload_1
      10: invokeinterface #1154,  1         // InterfaceMethod com/android/ide/common/rendering/api/StyleResourceValue.getResourceType:()Lcom/android/resources/ResourceType;
      15: aload_1
      16: invokeinterface #709,  1          // InterfaceMethod com/android/ide/common/rendering/api/StyleResourceValue.getName:()Ljava/lang/String;
      21: invokestatic  #1157               // Method com/android/layoutlib/bridge/Bridge.getResourceId:(Lcom/android/resources/ResourceType;Ljava/lang/String;)I
      24: ireturn
      25: aload_0

exec
/bin/bash -lc "find ~/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2 -name '*.jar' -print" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2/3b7f1fe9c54d6fd684a3fd3b8feffeedfe546fd4/layoutlib-api-31.13.2.jar

exec
/bin/bash -lc "rg -n \"ResourceType\" server/layoutlib-worker/ | sed -n '1,120p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:9:import com.android.resources.ResourceType
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt:28:            ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, defaultThemeName),
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:69:            TAG_DIMEN -> out += simpleValue(p, ResourceType.DIMEN)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:70:            TAG_COLOR -> out += simpleValue(p, ResourceType.COLOR)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:71:            TAG_INTEGER -> out += simpleValue(p, ResourceType.INTEGER)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:72:            TAG_BOOL -> out += simpleValue(p, ResourceType.BOOL)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:73:            TAG_STRING -> out += simpleValue(p, ResourceType.STRING)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:76:                val resType = typeAttr?.let { ResourceType.fromXmlValue(it) }
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParser.kt:129:    private fun simpleValue(p: KXmlParser, type: ResourceType): ParsedEntry.SimpleValue {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedEntry.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedEntry.kt:20:        val type: ResourceType,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:12:import com.android.resources.ResourceType
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:21:    private val byType: Map<ResourceType, Map<String, ResourceValue>>,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:26:    fun getResource(type: ResourceType, name: String): ResourceValue? =
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:34:    fun typeCount(type: ResourceType): Int = byType[type]?.size ?: 0
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:50:            val byTypeMut = mutableMapOf<ResourceType, MutableMap<String, ResourceValue>>()
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:65:                        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, e.name)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt:77:                val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, def.name)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:14:            ParsedEntry.SimpleValue(ResourceType.DIMEN, "config_scrollbarSize", "4dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:15:            ParsedEntry.SimpleValue(ResourceType.COLOR, "material_blue_grey_800", "#ff37474f"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:16:            ParsedEntry.SimpleValue(ResourceType.DIMEN, "action_bar_size", "56dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:20:        val d = bundle.getResource(ResourceType.DIMEN, "config_scrollbarSize")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:24:        val c = bundle.getResource(ResourceType.COLOR, "material_blue_grey_800")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:27:        assertNull(bundle.getResource(ResourceType.DIMEN, "does_not_exist"))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:67:            ParsedEntry.SimpleValue(ResourceType.DIMEN, "x", "1dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:68:            ParsedEntry.SimpleValue(ResourceType.DIMEN, "x", "2dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:71:        assertEquals("2dp", bundle.getResource(ResourceType.DIMEN, "x")!!.value)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:34:        val d = bundle.getResource(ResourceType.DIMEN, "config_scrollbarSize")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:5:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:15:        ParsedEntry.SimpleValue(ResourceType.DIMEN, "config_scrollbarSize", "4dp"),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:43:        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:52:        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Theme")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:59:        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.DIMEN, "config_scrollbarSize")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:5:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:23:        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:32:        val title = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:33:        val body = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "body")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackTest.kt:40:        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "title")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:3:import com.android.resources.ResourceType
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:25:        assertEquals(ResourceType.DIMEN, d.type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:38:        assertEquals(ResourceType.COLOR, c.type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:53:        assertEquals(ResourceType.INTEGER, list[0].type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:54:        assertEquals(ResourceType.BOOL, list[1].type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:55:        assertEquals(ResourceType.STRING, list[2].type)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:67:        assertEquals(ResourceType.DIMEN, e.type)

exec
/bin/bash -lc "./server/gradlew -p server :layoutlib-worker:dependencies --configuration compileClasspath | rg -n \"layoutlib-api|sdk-common|common|resources\" -C 2 | sed -n '1,160p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
exec
/bin/bash -lc 'APIJAR=$(find ~/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2 -name '"'*.jar' | head -1); echo APIJAR="'$APIJAR; javap -classpath "$APIJAR" -p com.android.ide.common.rendering.api.ResourceNamespace | rg -n "fromPackageName|class ResourceNamespace|RES_AUTO|ANDROID" -A3 -B2; javap -classpath "$APIJAR" -p com.android.ide.common.rendering.api.LayoutlibCallback | sed -n '"'1,220p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 176ms:
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

 succeeded in 571ms:
23-+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23
24-|    +--- org.jetbrains:annotations:13.0 -> 23.0.0
25:|    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (c)
26-+--- org.slf4j:slf4j-api:2.0.13
27-+--- project :protocol
--
34-|              |    +--- org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3 (c)
35-|              |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3 (c)
36:|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.22 -> 1.9.23
37-|              |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 (*)
38-|              \--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3
--
40-|                        +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.22 -> 1.9.23 (*)
41-|                        +--- org.jetbrains.kotlinx:kotlinx-serialization-bom:1.6.3 (*)
42:|                        \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.22 -> 1.9.23 (*)
43-+--- project :render-core
44-|    \--- project :protocol (*)
--
54-|    +--- ch.qos.logback:logback-core:1.5.6
55-|    \--- org.slf4j:slf4j-api:2.0.13
56:+--- com.android.tools.layoutlib:layoutlib-api:31.13.2
57-\--- net.sf.kxml:kxml2:2.3.0
58-

exec
/bin/bash -lc 'APIJAR=$(find ~/.gradle/caches/modules-2/files-2.1/com.android.tools.layoutlib/layoutlib-api/31.13.2 -name '"'*.jar' | head -1); javap -classpath \""'$APIJAR" -p com.android.resources.ResourceType | sed -n '"'1,120p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Compiled from "ResourceType.java"
public final class com.android.resources.ResourceType extends java.lang.Enum<com.android.resources.ResourceType> {
  public static final com.android.resources.ResourceType ANIM;
  public static final com.android.resources.ResourceType ANIMATOR;
  public static final com.android.resources.ResourceType ARRAY;
  public static final com.android.resources.ResourceType ATTR;
  public static final com.android.resources.ResourceType BOOL;
  public static final com.android.resources.ResourceType COLOR;
  public static final com.android.resources.ResourceType DIMEN;
  public static final com.android.resources.ResourceType DRAWABLE;
  public static final com.android.resources.ResourceType FONT;
  public static final com.android.resources.ResourceType FRACTION;
  public static final com.android.resources.ResourceType ID;
  public static final com.android.resources.ResourceType INTEGER;
  public static final com.android.resources.ResourceType INTERPOLATOR;
  public static final com.android.resources.ResourceType LAYOUT;
  public static final com.android.resources.ResourceType MENU;
  public static final com.android.resources.ResourceType MIPMAP;
  public static final com.android.resources.ResourceType NAVIGATION;
  public static final com.android.resources.ResourceType PLURALS;
  public static final com.android.resources.ResourceType RAW;
  public static final com.android.resources.ResourceType STRING;
  public static final com.android.resources.ResourceType STYLE;
  public static final com.android.resources.ResourceType STYLEABLE;
  public static final com.android.resources.ResourceType TRANSITION;
  public static final com.android.resources.ResourceType XML;
  public static final com.android.resources.ResourceType PUBLIC;
  public static final com.android.resources.ResourceType AAPT;
  public static final com.android.resources.ResourceType OVERLAYABLE;
  public static final com.android.resources.ResourceType STYLE_ITEM;
  public static final com.android.resources.ResourceType SAMPLE_DATA;
  public static final com.android.resources.ResourceType MACRO;
  private final java.lang.String mName;
  private final com.android.resources.ResourceType$Kind mKind;
  private final java.lang.String mDisplayName;
  private final java.lang.String[] mAlternateXmlNames;
  public static final com.google.common.collect.ImmutableSet<com.android.resources.ResourceType> REFERENCEABLE_TYPES;
  private static final com.google.common.collect.ImmutableMap<java.lang.String, com.android.resources.ResourceType> TAG_NAMES;
  private static final com.google.common.collect.ImmutableMap<java.lang.String, com.android.resources.ResourceType> CLASS_NAMES;
  private static final com.android.resources.ResourceType[] $VALUES;
  public static com.android.resources.ResourceType[] values();
  public static com.android.resources.ResourceType valueOf(java.lang.String);
  private com.android.resources.ResourceType(java.lang.String, java.lang.String, java.lang.String...);
  private com.android.resources.ResourceType(java.lang.String, java.lang.String, com.android.resources.ResourceType$Kind);
  public java.lang.String getName();
  public java.lang.String getDisplayName();
  public static com.android.resources.ResourceType fromClassName(java.lang.String);
  public static com.android.resources.ResourceType fromFolderName(java.lang.String);
  public static com.android.resources.ResourceType fromXmlTagName(java.lang.String);
  public static com.android.resources.ResourceType fromXmlValue(java.lang.String);
  public static <T> com.android.resources.ResourceType fromXmlTag(T, java.util.function.Function<T, java.lang.String>, java.util.function.BiFunction<? super T, ? super java.lang.String, java.lang.String>);
  public static com.android.resources.ResourceType fromXmlTag(org.w3c.dom.Node);
  public static java.util.Collection<java.lang.String> getClassNames();
  public boolean getHasInnerClass();
  public boolean getCanBeReferenced();
  public boolean isSynthetic();
  public java.lang.String toString();
  private static java.lang.String lambda$fromXmlTag$0(org.w3c.dom.Element);
  private static com.android.resources.ResourceType[] $values();
  static {};
}

exec
/bin/bash -lc "which kotlinc || true; which kotlin || true; ls server/layoutlib-worker/build/classes/kotlin/main/dev/axp/layoutlib/worker/classloader 2>/dev/null || true; sed -n '1,20p' fixture/sample-app/app/build/axp/runtime-classpath.txt; wc -l fixture/sample-app/app/build/axp/runtime-classpath.txt" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
AarExtractor$sha1$1.class
AarExtractor.class
ClassLoaderConstants.class
SampleAppClassLoader$Companion.class
SampleAppClassLoader.class
SampleAppClasspathManifest.class
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.activity/activity/1.8.0/4266e2118d565daa20212d1726e11f41e1a4d0ca/activity-1.8.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation-experimental/1.4.0/c7a2038d831d0cfbfb9a440890b287c2e839ecf/annotation-experimental-1.4.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation-jvm/1.6.0/a7257339a052df0f91433cf9651231bbb802b502/annotation-jvm-1.6.0.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.appcompat/appcompat-resources/1.6.1/3fe43025e50b556f319b1ff82a730d5a376a31e/appcompat-resources-1.6.1.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.appcompat/appcompat/1.6.1/6c7577004b7ebbee5ed87d512b578dd20e3c8c31/appcompat-1.6.1.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.arch.core/core-common/2.2.0/5e1b8b81dfd5f52c56a8d53b18ca759c19a301f3/core-common-2.2.0.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.arch.core/core-runtime/2.2.0/39705982680d78556b679ef9d5400d4f17509b0b/core-runtime-2.2.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.cardview/cardview/1.0.0/158dbc2e2bc502815821191b04446b8f663c1874/cardview-1.0.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.collection/collection/1.1.0/1f27220b47669781457de0d600849a5de0e89909/collection-1.1.0.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.concurrent/concurrent-futures/1.1.0/50b7fb98350d5f42a4e49704b03278542293ba48/concurrent-futures-1.1.0.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.constraintlayout/constraintlayout-core/1.0.4/29cdbe03ded6b0980f63fa5da2579a430e911c40/constraintlayout-core-1.0.4.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.constraintlayout/constraintlayout/2.1.4/b7ad5eca28aa9e7c4ea1a7e256c7ee4401a031f/constraintlayout-2.1.4.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.coordinatorlayout/coordinatorlayout/1.1.0/8eeb7baf75b9595d017642a460df5af3bb9fa4e1/coordinatorlayout-1.1.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.core/core-ktx/1.13.0/c2402581b5301db71ee55547caec73c78b51ab98/core-ktx-1.13.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.core/core/1.13.0/ec9d4e1da0dc84ff6a644624447a99ad136de21d/core-1.13.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.cursoradapter/cursoradapter/1.0.0/74014983a86b83cbce534dec4e7aa9312f5f5d82/cursoradapter-1.0.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.customview/customview/1.1.0/a030aaf64c5419a89f7d91a33755c4629a43471/customview-1.1.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.documentfile/documentfile/1.0.0/66104345c90cd8c2fd5ad2d3aad692b280e10c32/documentfile-1.0.0.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.drawerlayout/drawerlayout/1.1.1/1b2b049e1e88589510ddd4d93f2699b46ecd73dd/drawerlayout-1.1.1.aar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.dynamicanimation/dynamicanimation/1.0.0/e980497a58fb319d471f3bb78d066b926a08ba8e/dynamicanimation-1.0.0.aar
55 fixture/sample-app/app/build/axp/runtime-classpath.txt

exec
/bin/bash -lc "javap -classpath server/layoutlib-worker/build/classes/kotlin/main -p dev.axp.layoutlib.worker.classloader.AarExtractor | sed -n '1,120p'; find ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.23 -name '*.jar' -print" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
Compiled from "AarExtractor.kt"
public final class dev.axp.layoutlib.worker.classloader.AarExtractor {
  public static final dev.axp.layoutlib.worker.classloader.AarExtractor INSTANCE;
  private dev.axp.layoutlib.worker.classloader.AarExtractor();
  public final java.nio.file.Path extract(java.nio.file.Path, java.nio.file.Path);
  private final java.lang.String sha1(java.lang.String);
  static {};
}
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.23/dbaadea1f5e68f790d242a91a38355a83ec38747/kotlin-stdlib-1.9.23.jar

exec
/bin/bash -lc "find ~/.gradle/caches/modules-2/files-2.1/org.ow2.asm -name '*.jar' | sed -n '1,40p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-analysis/9.6/2a0c33a19c1b594fee4eb539e3cd51eb3dd80903/asm-analysis-9.6-sources.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-analysis/9.6/9ce6c7b174bd997fc2552dff47964546bd7a5ec3/asm-analysis-9.6.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-util/9.6/f77caf84eb93786a749b2baa40865b9613e3eaee/asm-util-9.6.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-util/9.6/7113c3476982c7b982f16b45d24dd86e2ffca46a/asm-util-9.6-sources.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-tree/9.6/c0cdda9d211e965d2a4448aa3fd86110f2f8c2de/asm-tree-9.6.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-tree/9.6/ea2ff719db7976655f5c95469a98eac7671a119/asm-tree-9.6-sources.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm/9.6/aa205cf0a06dbd8e04ece91c0b37c3f5d567546a/asm-9.6.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm/9.6/d55087ece3c6a82ef0d6acb9d853886d7af9445d/asm-9.6-sources.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-commons/9.6/f1a9e5508eff490744144565c47326c8648be309/asm-commons-9.6.jar
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-commons/9.6/446c8ca5fffad2e50b5a7ab4c25fc901c7f38ab9/asm-commons-9.6-sources.jar

exec
/bin/bash -lc "awk 'END{print \"entries=\" NR} /\\.aar"'$/{a++} /'"\\.jar"'$/{j++} END{print "aars=" a+0 " jars=" j+0}'"' fixture/sample-app/app/build/axp/runtime-classpath.txt" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
entries=56
aars=41 jars=15

exec
/bin/bash -lc 'AAR=$(rg '"'constraintlayout/2\\.1\\.4/.+constraintlayout-2\\.1\\.4\\.aar' fixture/sample-app/app/build/axp/runtime-classpath.txt); echo \""'$AAR"; unzip -l "$AAR" classes.jar; unzip -p "$AAR" classes.jar > /tmp/constraintlayout-classes.jar; unzip -Z1 /tmp/constraintlayout-classes.jar | rg '"'\\.class"'$'"' | wc -l; ls -lh /tmp/constraintlayout-classes.jar" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
exec
/bin/bash -lc 'AAR=$(rg '"'material/1\\.12\\.0/.+material-1\\.12\\.0\\.aar' fixture/sample-app/app/build/axp/runtime-classpath.txt); echo \""'$AAR"; unzip -l "$AAR" classes.jar; unzip -p "$AAR" classes.jar > /tmp/material-classes.jar; unzip -Z1 /tmp/material-classes.jar | rg '"'\\.class"'$'"' | wc -l; ls -lh /tmp/material-classes.jar" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.constraintlayout/constraintlayout/2.1.4/b7ad5eca28aa9e7c4ea1a7e256c7ee4401a031f/constraintlayout-2.1.4.aar
Archive:  /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/androidx.constraintlayout/constraintlayout/2.1.4/b7ad5eca28aa9e7c4ea1a7e256c7ee4401a031f/constraintlayout-2.1.4.aar
  Length      Date    Time    Name
---------  ---------- -----   ----
   473442  1980-02-01 00:00   classes.jar
---------                     -------
   473442                     1 file
163
-rw-rw-r-- 1 bh-mark-dev-desktop bh-mark-dev-desktop 463K Apr 29 16:05 /tmp/constraintlayout-classes.jar

 succeeded in 0ms:
/home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.google.android.material/material/1.12.0/e493b7ac7fc4b8ca48527e0199f9961652711ac3/material-1.12.0.aar
Archive:  /home/bh-mark-dev-desktop/.gradle/caches/modules-2/files-2.1/com.google.android.material/material/1.12.0/e493b7ac7fc4b8ca48527e0199f9961652711ac3/material-1.12.0.aar
  Length      Date    Time    Name
---------  ---------- -----   ----
  1872366  1980-02-01 00:00   classes.jar
---------                     -------
  1872366                     1 file
1022
-rw-rw-r-- 1 bh-mark-dev-desktop bh-mark-dev-desktop 1.8M Apr 29 16:05 /tmp/material-classes.jar

exec
/bin/bash -lc 'KOTLIN_STDLIB=$(find ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.23 -name '"'kotlin-stdlib-1.9.23.jar' | head -1); AAR="'$(rg '"'material/1\\.12\\.0/.+material-1\\.12\\.0\\.aar' fixture/sample-app/app/build/axp/runtime-classpath.txt); jshell --class-path \"server/layoutlib-worker/build/classes/kotlin/main:"'$KOTLIN_STDLIB" <<EOF
import java.nio.file.*;
import java.util.*;
import dev.axp.layoutlib.worker.classloader.AarExtractor;
Path aar = Path.of("$AAR");
Path cache = Path.of("/tmp/aar-extractor-bench");
void clear(Path p) throws Exception { if (Files.exists(p)) Files.walk(p).sorted(Comparator.reverseOrder()).forEach(x -> { try { Files.delete(x); } catch (Exception e) { throw new RuntimeException(e); } }); }
for (int i = 1; i <= 5; i++) { clear(cache); long s = System.nanoTime(); Path out = AarExtractor.INSTANCE.extract(aar, cache); long e = System.nanoTime(); System.out.printf("cold[%d]=%.3f ms out=%s size=%d%n", i, (e-s)/1_000_000.0, out, Files.size(out)); }
long s = System.nanoTime(); Path out = AarExtractor.INSTANCE.extract(aar, cache); long e = System.nanoTime(); System.out.printf("warm=%.3f ms out=%s size=%d%n", (e-s)/1_000_000.0, out, Files.size(out));
/exit
EOF' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 1861ms:
|  Welcome to JShell -- Version 17.0.18
|  For an introduction type: /help intro

jshell>  
jshell>  
jshell>  
jshell>  aar ==> /home/bh-mark-dev-desktop/.gradle/caches/modules- ... 711ac3/material-1.12.0.aar

jshell>  cache ==> /tmp/aar-extractor-bench

jshell>  |  created method clear(Path)

jshell>  cold[1]=40.651 ms out=/tmp/aar-extractor-bench/aar-classes/20c5faafc752a39677f1460feccd02da9512cc09/material-1.12.0.jar size=1872366
cold[2]=6.713 ms out=/tmp/aar-extractor-bench/aar-classes/20c5faafc752a39677f1460feccd02da9512cc09/material-1.12.0.jar size=1872366
cold[3]=6.297 ms out=/tmp/aar-extractor-bench/aar-classes/20c5faafc752a39677f1460feccd02da9512cc09/material-1.12.0.jar size=1872366
cold[4]=6.140 ms out=/tmp/aar-extractor-bench/aar-classes/20c5faafc752a39677f1460feccd02da9512cc09/material-1.12.0.jar size=1872366
cold[5]=6.124 ms out=/tmp/aar-extractor-bench/aar-classes/20c5faafc752a39677f1460feccd02da9512cc09/material-1.12.0.jar size=1872366

jshell>  s ==> 707310118399063
out ==> /tmp/aar-extractor-bench/aar-classes/20c5faafc752 ... 12cc09/material-1.12.0.jar
e ==> 707310493432277
warm=375.033 ms out=/tmp/aar-extractor-bench/aar-classes/20c5faafc752a39677f1460feccd02da9512cc09/material-1.12.0.jar size=1872366
$11 ==> java.io.PrintStream@9e89d68

jshell>  |  Goodbye

apply patch
patch: completed
/tmp/BenchAarExtractor.java
diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}

exec
/bin/bash -lc 'KOTLIN_STDLIB=$(find ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.23 -name '"'kotlin-stdlib-1.9.23.jar' | head -1); AAR="'$(rg '"'material/1\\.12\\.0/.+material-1\\.12\\.0\\.aar' fixture/sample-app/app/build/axp/runtime-classpath.txt); javac -cp \"server/layoutlib-worker/build/classes/kotlin/main:"'$KOTLIN_STDLIB" /tmp/BenchAarExtractor.java; java -cp "/tmp:server/layoutlib-worker/build/classes/kotlin/main:$KOTLIN_STDLIB" BenchAarExtractor "$AAR" /tmp/aar-extractor-bench-java' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}

 succeeded in 298ms:
cold[1]=44.344 ms size=1872366
cold[2]=6.398 ms size=1872366
cold[3]=6.420 ms size=1872366
cold[4]=6.065 ms size=1872366
cold[5]=5.768 ms size=1872366
warm=0.459 ms size=1872366

codex
Existing extraction is very cheap after filesystem warmup: material’s 1.8 MB `classes.jar` extracts in roughly 6 ms cold after the first run and 0.5 ms on cache hit. I’m adding a scratch ASM benchmark with the same remapper shape to separate the proposed transformation cost from ZIP extraction.
apply patch
patch: completed
/tmp/BenchAsmRewrite.java
diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

exec
/bin/bash -lc 'ASM=$(find ~/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm/9.6 -name '"'asm-9.6.jar' | head -1); ASM_COMMONS="'$(find ~/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-commons/9.6 -name '"'asm-commons-9.6.jar' | head -1); javac -cp \""'$ASM:$ASM_COMMONS" /tmp/BenchAsmRewrite.java; java -cp "/tmp:$ASM:$ASM_COMMONS" BenchAsmRewrite /tmp/material-classes.jar /tmp/material-classes-rewritten.jar; java -cp "/tmp:$ASM:$ASM_COMMONS" BenchAsmRewrite /tmp/constraintlayout-classes.jar /tmp/constraintlayout-classes-rewritten.jar' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

 succeeded in 1665ms:
rewrite[1]=266.964 ms classes=1022 outSize=1899610
rewrite[2]=182.406 ms classes=1022 outSize=1899610
rewrite[3]=160.686 ms classes=1022 outSize=1899610
rewrite[4]=174.095 ms classes=1022 outSize=1899610
rewrite[5]=177.332 ms classes=1022 outSize=1899610
rewrite[1]=99.907 ms classes=163 outSize=479674
rewrite[2]=57.379 ms classes=163 outSize=479674
rewrite[3]=52.704 ms classes=163 outSize=479674
rewrite[4]=49.907 ms classes=163 outSize=479674
rewrite[5]=49.588 ms classes=163 outSize=479674

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

exec
/bin/bash -lc 'while read -r p; do if [[ "$p" == *.aar ]]; then n=$(unzip -p "$p" classes.jar 2>/dev/null | unzip -Z1 - 2>/dev/null | rg -c '"'\\.class"'$'"' || true); s="'$(unzip -l "$p" classes.jar 2>/dev/null | awk '"'/classes.jar/{print "'$1}'"' | head -1); printf '%s %s %s\\n' \""'${n:-0}" "${s:-0}" "$(basename "$p")"; fi; done < fixture/sample-app/app/build/axp/runtime-classpath.txt | awk '"'{classes+="'$1; bytes+=$2; count++} {print} END{printf "TOTAL_AARS=%d TOTAL_CLASSES=%d TOTAL_CLASSES_JAR_BYTES=%d'"\\n\", count, classes, bytes}' | sed -n '1,80p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
0 173303 activity-1.8.0.aar
0 6304 annotation-experimental-1.4.0.aar
0 69360 appcompat-resources-1.6.1.aar
0 764869 appcompat-1.6.1.aar
0 6270 core-runtime-2.2.0.aar
0 21655 cardview-1.0.0.aar
0 473442 constraintlayout-2.1.4.aar
0 41837 coordinatorlayout-1.1.0.aar
0 188652 core-ktx-1.13.0.aar
0 1261357 core-1.13.0.aar
0 11504 cursoradapter-1.0.0.aar
0 33345 customview-1.1.0.aar
0 11278 documentfile-1.0.0.aar
0 32498 drawerlayout-1.1.1.aar
0 38168 dynamicanimation-1.0.0.aar
0 22964 emoji2-views-helper-1.2.0.aar
0 77192 emoji2-1.2.0.aar
0 274187 fragment-1.3.6.aar
0 7661 interpolator-1.0.0.aar
0 2042 legacy-support-core-utils-1.0.0.aar
0 9612 lifecycle-livedata-core-2.6.2.aar
0 18379 lifecycle-livedata-2.6.2.aar
0 14592 lifecycle-process-2.6.2.aar
0 20559 lifecycle-runtime-2.6.2.aar
0 40372 lifecycle-viewmodel-savedstate-2.6.2.aar
0 41698 lifecycle-viewmodel-2.6.2.aar
0 35177 loader-1.0.0.aar
0 6875 localbroadcastmanager-1.0.0.aar
0 15476 print-1.0.0.aar
0 47432 profileinstaller-1.3.0.aar
0 376794 recyclerview-1.1.0.aar
0 19555 savedstate-1.2.1.aar
0 6788 startup-runtime-1.1.1.aar
0 3696 tracing-1.0.0.aar
0 193874 transition-1.5.0.aar
0 34311 vectordrawable-animated-1.1.0.aar
0 33350 vectordrawable-1.1.0.aar
0 31906 versionedparcelable-1.1.1.aar
0 55298 viewpager-1.0.0.aar
0 64553 viewpager2-1.0.0.aar
0 1872366 material-1.12.0.aar
TOTAL_AARS=41 TOTAL_CLASSES=0 TOTAL_CLASSES_JAR_BYTES=6460551

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

exec
/bin/bash -lc 'totalClasses=0; totalBytes=0; totalAars=0; while read -r p; do if [[ "$p" == *.aar ]]; then tmp=$(mktemp /tmp/classes.XXXXXX.jar); if unzip -p "$p" classes.jar > "$tmp" 2>/dev/null && [[ -s "$tmp" ]]; then n=$(unzip -Z1 "$tmp" 2>/dev/null | rg -c '"'\\.class"'$'"' || true); s="'$(stat -c %s "$tmp"); else n=0; s=0; fi; rm -f "$tmp"; totalClasses=$((totalClasses+n)); totalBytes=$((totalBytes+s)); totalAars=$((totalAars+1)); printf '"'%4d %8d %s\\n' \""'$n" "$s" "$(basename "$p")"; fi; done < fixture/sample-app/app/build/axp/runtime-classpath.txt; printf '"'TOTAL_AARS=%d TOTAL_CLASSES=%d TOTAL_CLASSES_JAR_BYTES=%d\\n' \""'$totalAars" "$totalClasses" "$totalBytes"' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

 succeeded in 186ms:
  31    41698 lifecycle-viewmodel-2.6.2.aar
  23    35177 loader-1.0.0.aar
   4     6875 localbroadcastmanager-1.0.0.aar
   7    15476 print-1.0.0.aar
  27    47432 profileinstaller-1.3.0.aar
 175   376794 recyclerview-1.1.0.aar
  13    19555 savedstate-1.2.1.aar
   5     6788 startup-runtime-1.1.1.aar
   3     3696 tracing-1.0.0.aar
 136   193874 transition-1.5.0.aar
  13    34311 vectordrawable-animated-1.1.0.aar
  12    33350 vectordrawable-1.1.0.aar
  15    31906 versionedparcelable-1.1.1.aar
  24    55298 viewpager-1.0.0.aar
  45    64553 viewpager2-1.0.0.aar
1022  1872366 material-1.12.0.aar
TOTAL_AARS=41 TOTAL_CLASSES=3666 TOTAL_CLASSES_JAR_BYTES=6460551

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
/bin/bash -lc "rg -n \"Theme_AxpFixture|Theme.AxpFixture|style name=|Material3|parent=\" fixture/sample-app/app/src/main/res -g '*.xml'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
fixture/sample-app/app/src/main/res/values/themes.xml:5:    <style name="Theme.AxpFixture" parent="Theme.Material3.DayNight.NoActionBar">

exec
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResources.kt | sed -n '1,160p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
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
/bin/bash -lc "nl -ba server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundle.kt | sed -n '1,130p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	package dev.axp.layoutlib.worker.resources
     2	
     3	import com.android.ide.common.rendering.api.AttrResourceValue
     4	import com.android.ide.common.rendering.api.AttrResourceValueImpl
     5	import com.android.ide.common.rendering.api.ResourceNamespace
     6	import com.android.ide.common.rendering.api.ResourceReference
     7	import com.android.ide.common.rendering.api.ResourceValue
     8	import com.android.ide.common.rendering.api.ResourceValueImpl
     9	import com.android.ide.common.rendering.api.StyleItemResourceValueImpl
    10	import com.android.ide.common.rendering.api.StyleResourceValue
    11	import com.android.ide.common.rendering.api.StyleResourceValueImpl
    12	import com.android.resources.ResourceType
    13	
    14	/**
    15	 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): ParsedEntry 집합을 layoutlib-api 의 ResourceValue 로
    16	 * 변환하여 집계한 immutable 번들.
    17	 *
    18	 * scope: framework (ResourceNamespace.ANDROID) 만. library/app 은 별도.
    19	 */
    20	class FrameworkResourceBundle private constructor(
    21	    private val byType: Map<ResourceType, Map<String, ResourceValue>>,
    22	    private val styles: Map<String, StyleResourceValue>,
    23	    private val attrs: Map<String, AttrResourceValue>,
    24	) {
    25	
    26	    fun getResource(type: ResourceType, name: String): ResourceValue? =
    27	        byType[type]?.get(name)
    28	
    29	    fun getStyle(name: String): StyleResourceValue? = styles[name]
    30	
    31	    fun getAttr(name: String): AttrResourceValue? = attrs[name]
    32	
    33	    /** 진단용 — 테스트에서만 사용 권장. */
    34	    fun typeCount(type: ResourceType): Int = byType[type]?.size ?: 0
    35	    fun styleCount(): Int = styles.size
    36	    fun attrCount(): Int = attrs.size
    37	
    38	    companion object {
    39	        /**
    40	         * @param entries 모든 파일의 ParsedEntry 를 평탄화하여 전달.
    41	         *
    42	         * Dedupe 정책:
    43	         *   - SimpleValue / StyleDef: later-wins (themes_material 이 themes 를 오버라이드하는 실 Android 동작).
    44	         *   - AttrDef: first-wins (W3D1 pair-review F1) — top-level attr 가 declare-styleable nested 보다 우선.
    45	         *
    46	         * Style parent post-process: StyleDef 의 explicit parent 또는 inference 결과가
    47	         * 집합 내에 존재하지 않으면 parentStyleName 을 null 로 세팅 (chain 끊김).
    48	         */
    49	        fun build(entries: List<ParsedEntry>): FrameworkResourceBundle {
    50	            val byTypeMut = mutableMapOf<ResourceType, MutableMap<String, ResourceValue>>()
    51	            val stylesMut = mutableMapOf<String, StyleResourceValue>()
    52	            val attrsMut = mutableMapOf<String, AttrResourceValue>()
    53	
    54	            val styleDefs = mutableListOf<ParsedEntry.StyleDef>()
    55	
    56	            for (e in entries) when (e) {
    57	                is ParsedEntry.SimpleValue -> {
    58	                    val ref = ResourceReference(ResourceNamespace.ANDROID, e.type, e.name)
    59	                    val rv = ResourceValueImpl(ref, e.value, null)
    60	                    byTypeMut.getOrPut(e.type) { mutableMapOf() }[e.name] = rv
    61	                }
    62	                is ParsedEntry.AttrDef -> {
    63	                    // first-wins: 이미 등록된 name 은 덮어쓰지 않는다.
    64	                    if (!attrsMut.containsKey(e.name)) {
    65	                        val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, e.name)
    66	                        attrsMut[e.name] = AttrResourceValueImpl(ref, null)
    67	                    }
    68	                }
    69	                is ParsedEntry.StyleDef -> styleDefs += e
    70	            }
    71	
    72	            // Style pass: inference 로 parent 이름 결정 후 존재 여부 판단.
    73	            val allStyleNames: Set<String> = styleDefs.mapTo(HashSet()) { it.name }
    74	            for (def in styleDefs) {
    75	                val candidate = StyleParentInference.infer(def.name, def.parent)
    76	                val parentName = if (candidate != null && candidate in allStyleNames) candidate else null
    77	                val ref = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, def.name)
    78	                val sv = StyleResourceValueImpl(ref, parentName, null)
    79	                for (it2 in def.items) {
    80	                    sv.addItem(
    81	                        StyleItemResourceValueImpl(ResourceNamespace.ANDROID, it2.name, it2.value, null)
    82	                    )
    83	                }
    84	                stylesMut[def.name] = sv
    85	            }
    86	
    87	            return FrameworkResourceBundle(byTypeMut, stylesMut, attrsMut)
    88	        }
    89	    }
    90	}

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

exec
/bin/bash -lc 'rg -n "DEFAULT_FRAMEWORK_THEME|Theme.Material|SessionConstants" server/layoutlib-worker/src/main/kotlin server/layoutlib-worker/src/test/kotlin docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
docs/superpowers/specs/2026-04-29-w3d3-alpha-bytecode-rewriting-design.md:503:- (C) ThemeEnforcement throw — `SessionConstants.DEFAULT_FRAMEWORK_THEME` (`Theme.Material.Light.NoActionBar`) 는 frame work theme. AppCompat 자동 치환이 활성화되었으므로 inflation 은 `AppCompatTextView` 사용 → AppCompat 이 framework theme 와 호환 점검 가능 (?attr/colorAccent 등). throw 면 `setTheme` 를 sample-app 의 `Theme.AxpFixture` 로 교체 필요 — 이는 W3D4 의 resource value loader 와 함께만 가능.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:4:import dev.axp.layoutlib.worker.session.SessionConstants
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:104:        assertEquals(SessionConstants.RENDER_WIDTH_PX, img!!.width)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:105:        assertEquals(SessionConstants.RENDER_HEIGHT_PX, img.height)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:128:        assertEquals(SessionConstants.RENDER_WIDTH_PX, img.width)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt:129:        assertEquals(SessionConstants.RENDER_HEIGHT_PX, img.height)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:34:            ParsedEntry.StyleDef("Theme.Material.Light.NoActionBar", parent = "Theme.Material.Light",
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:36:            // 점 포함하지만 explicit parent 없음 → inference 로 Theme.Material 추정되나 존재 X
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:37:            ParsedEntry.StyleDef("Theme.Material.Light", parent = null, items = emptyList()),
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:44:        val noActionBar = bundle.getStyle("Theme.Material.Light.NoActionBar")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:45:        assertEquals("Theme.Material.Light", noActionBar!!.parentStyleName)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:47:        val light = bundle.getStyle("Theme.Material.Light")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceBundleTest.kt:48:        // inference Theme.Material bundle null post-process.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:12:            "Theme.Material.Light",
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:13:            StyleParentInference.infer("Theme.Material.Light.NoActionBar", explicitParent = "Theme.Material.Light")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:20:            "Theme.Material",
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:21:            StyleParentInference.infer("Theme.Material.Light", explicitParent = "@android:style/Theme.Material")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:35:        assertEquals("Theme.Material.Light", StyleParentInference.infer("Theme.Material.Light.NoActionBar", null))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInferenceTest.kt:36:        assertEquals("Theme.Material", StyleParentInference.infer("Theme.Material.Light", ""))
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceLoaderRealDistTest.kt:45:    fun `real dist - Theme Material Light NoActionBar chain 이 resolve`() {
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceLoaderRealDistTest.kt:49:        val noActionBar = bundle.getStyle("Theme.Material.Light.NoActionBar")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceLoaderRealDistTest.kt:50:        assertNotNull(noActionBar, "Theme.Material.Light.NoActionBar 존재")
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInference.kt:24:     * `@android:style/Theme.Material`  → `Theme.Material`
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/StyleParentInference.kt:26:     * `Theme.Material`                  → `Theme.Material`  (이미 이름 형태)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:76:        val noActionBar = bundle.getStyle("Theme.Material.Light.NoActionBar")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:78:        assertEquals("Theme.Material.Light", noActionBar!!.parentStyleName)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:80:        val light = bundle.getStyle("Theme.Material.Light")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:83:            "F2 regression guard: 실 frameworks 는 Theme.Material.Light parent=Theme.Light")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:147:              <style name="Theme.Material.Light" parent="Theme.Light"/>
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkResourceValueLoaderTest.kt:148:              <style name="Theme.Material.Light.NoActionBar" parent="Theme.Material.Light"/>
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:18:            "Theme.Material.Light.NoActionBar",
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:26:        val rr = FrameworkRenderResources(bundle(), defaultThemeName = "Theme.Material.Light.NoActionBar")
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkRenderResourcesTest.kt:29:        assertEquals("Theme.Material.Light.NoActionBar", theme.name)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:12:import dev.axp.layoutlib.worker.session.SessionConstants
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:171:        val resources = FrameworkRenderResources(bundle, SessionConstants.DEFAULT_FRAMEWORK_THEME)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt:211:            val result = session.render(SessionConstants.RENDER_TIMEOUT_MS)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:76:              <style name="Theme.Material.Light.NoActionBar" parent="Theme.Material.Light">
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:83:        assertEquals("Theme.Material.Light.NoActionBar", s.name)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/FrameworkValueParserTest.kt:84:        assertEquals("Theme.Material.Light", s.parent)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:41:            SessionConstants.RENDER_WIDTH_PX,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:42:            SessionConstants.RENDER_HEIGHT_PX,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:44:            SessionConstants.DPI_XHIGH,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:45:            SessionConstants.DPI_XHIGH,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:61:            SessionConstants.PROJECT_KEY,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:65:            SessionConstants.MIN_SDK,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:66:            SessionConstants.TARGET_SDK,
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:74:        params.setLocale(SessionConstants.RENDER_LOCALE)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:78:        params.setUiMode(SessionConstants.UI_MODE_NORMAL_DAY)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactory.kt:79:        params.timeout = SessionConstants.RENDER_TIMEOUT_MS
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:30:     * W3D1 3b-values: resources 파라미터 default 제거 → 테스트용 empty bundle + DEFAULT_FRAMEWORK_THEME.
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:36:            SessionConstants.DEFAULT_FRAMEWORK_THEME,
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:49:        assertEquals(SessionConstants.RENDER_WIDTH_PX, hw.screenWidth)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:50:        assertEquals(SessionConstants.RENDER_HEIGHT_PX, hw.screenHeight)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:73:        assertEquals(SessionConstants.RENDER_TIMEOUT_MS, buildParams().timeout)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:81:        assertEquals(SessionConstants.DEFAULT_FRAMEWORK_THEME, theme.name)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:95:        assertEquals(SessionConstants.PROJECT_KEY, params.projectKey)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:96:        assertEquals(SessionConstants.MIN_SDK, params.minSdkVersion)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:97:        assertEquals(SessionConstants.TARGET_SDK, params.targetSdkVersion)
server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt:102:        assertEquals(SessionConstants.RENDER_LOCALE, buildParams().locale)
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt:10:object SessionConstants {
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt:41:    /** F2 default theme — Theme.Material.Light.NoActionBar (decor 제거됨 + decent TextAppearance 체인). */
server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt:42:    const val DEFAULT_FRAMEWORK_THEME = "Theme.Material.Light.NoActionBar"

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

exec
/bin/bash -lc 'CACHE=fixture/sample-app/app/build/axp/aar-classes; if [ -d "$CACHE" ]; then find "$CACHE" -name '"'*.jar' | wc -l; find \""'$CACHE" -name '"'constraintlayout-2.1.4.jar' -o -name 'material-1.12.0.jar' | while read -r f; do echo \""'$f"; stat -c '"'cache_mtime=%y size=%s' \""'$f"; done; fi; AAR=$(rg '"'constraintlayout/2\\.1\\.4/.+constraintlayout-2\\.1\\.4\\.aar' fixture/sample-app/app/build/axp/runtime-classpath.txt); stat -c 'aar_mtime=%y size=%s' \""'$AAR"' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
41
fixture/sample-app/app/build/axp/aar-classes/20c5faafc752a39677f1460feccd02da9512cc09/material-1.12.0.jar
cache_mtime=2026-04-29 15:03:13.940167040 +0900 size=1872366
fixture/sample-app/app/build/axp/aar-classes/379655a9d4fd4278c156369b8f90c95c74283a85/constraintlayout-2.1.4.jar
cache_mtime=2026-04-29 15:03:13.918166082 +0900 size=473442
aar_mtime=2026-03-31 14:17:41.402714857 +0900 size=476596

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

exec
/bin/bash -lc "javap -classpath /tmp/material-classes.jar -p -c com.google.android.material.button.MaterialButton | rg -n \"<init>|materialButtonStyle|Widget_MaterialComponents_Button|obtainStyledAttributes|ThemeEnforcement\" -A45 -B8 | sed -n '1,220p'; javap -classpath \"fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar\" -p -verbose 'com.google.android.material.R"'$style'"' 'com.google.android.material.R"'$attr'"' | rg -n \"Widget_MaterialComponents_Button|materialButtonStyle|ConstantValue\" -A3 -B2 | sed -n '1,140p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

 succeeded in 293ms:
2058-  #2047 = Utf8               Widget_MaterialComponents_BottomSheet_Modal
2059-  #2048 = Integer            2131690492
2060:  #2049 = Utf8               Widget_MaterialComponents_Button
2061-  #2050 = Integer            2131690493
2062:  #2051 = Utf8               Widget_MaterialComponents_Button_Icon
2063-  #2052 = Integer            2131690494
2064:  #2053 = Utf8               Widget_MaterialComponents_Button_OutlinedButton
2065-  #2054 = Integer            2131690495
2066:  #2055 = Utf8               Widget_MaterialComponents_Button_OutlinedButton_Icon
2067-  #2056 = Integer            2131690496
2068:  #2057 = Utf8               Widget_MaterialComponents_Button_TextButton
2069-  #2058 = Integer            2131690497
2070:  #2059 = Utf8               Widget_MaterialComponents_Button_TextButton_Dialog
2071-  #2060 = Integer            2131690498
2072:  #2061 = Utf8               Widget_MaterialComponents_Button_TextButton_Dialog_Flush
2073-  #2062 = Integer            2131690499
2074:  #2063 = Utf8               Widget_MaterialComponents_Button_TextButton_Dialog_Icon
2075-  #2064 = Integer            2131690500
2076:  #2065 = Utf8               Widget_MaterialComponents_Button_TextButton_Icon
2077-  #2066 = Integer            2131690501
2078:  #2067 = Utf8               Widget_MaterialComponents_Button_TextButton_Snackbar
2079-  #2068 = Integer            2131690502
2080:  #2069 = Utf8               Widget_MaterialComponents_Button_UnelevatedButton
2081-  #2070 = Integer            2131690503
2082:  #2071 = Utf8               Widget_MaterialComponents_Button_UnelevatedButton_Icon
2083-  #2072 = Integer            2131690504
2084-  #2073 = Utf8               Widget_MaterialComponents_CardView
2085-  #2074 = Integer            2131690505
--
2274-  #2263 = NameAndType        #2261:#2262  // "<init>":()V
2275-  #2264 = Methodref          #4.#2263     // java/lang/Object."<init>":()V
2276:  #2265 = Utf8               ConstantValue
2277-  #2266 = Utf8               Code
2278-  #2267 = Utf8               InnerClasses
2279-{
--
2281-    descriptor: I
2282-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2283:    ConstantValue: int 2131689472
2284-
2285-  public static int AlertDialog_AppCompat_Light;
2286-    descriptor: I
2287-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2288:    ConstantValue: int 2131689473
2289-
2290-  public static int Animation_AppCompat_Dialog;
2291-    descriptor: I
2292-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2293:    ConstantValue: int 2131689474
2294-
2295-  public static int Animation_AppCompat_DropDownUp;
2296-    descriptor: I
2297-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2298:    ConstantValue: int 2131689475
2299-
2300-  public static int Animation_AppCompat_Tooltip;
2301-    descriptor: I
2302-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2303:    ConstantValue: int 2131689476
2304-
2305-  public static int Animation_Design_BottomSheetDialog;
2306-    descriptor: I
2307-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2308:    ConstantValue: int 2131689477
2309-
2310-  public static int Animation_Material3_BottomSheetDialog;
2311-    descriptor: I
2312-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2313:    ConstantValue: int 2131689478
2314-
2315-  public static int Animation_Material3_SideSheetDialog;
2316-    descriptor: I
2317-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2318:    ConstantValue: int 2131689479
2319-
2320-  public static int Animation_Material3_SideSheetDialog_Left;
2321-    descriptor: I
2322-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2323:    ConstantValue: int 2131689480
2324-
2325-  public static int Animation_Material3_SideSheetDialog_Right;
2326-    descriptor: I
2327-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2328:    ConstantValue: int 2131689481
2329-
2330-  public static int Animation_MaterialComponents_BottomSheetDialog;
2331-    descriptor: I
2332-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2333:    ConstantValue: int 2131689482
2334-
2335-  public static int Base_AlertDialog_AppCompat;
2336-    descriptor: I
2337-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2338:    ConstantValue: int 2131689483
2339-
2340-  public static int Base_AlertDialog_AppCompat_Light;
2341-    descriptor: I
2342-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2343:    ConstantValue: int 2131689484
2344-
2345-  public static int Base_Animation_AppCompat_Dialog;
2346-    descriptor: I
2347-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2348:    ConstantValue: int 2131689485
2349-
2350-  public static int Base_Animation_AppCompat_DropDownUp;
2351-    descriptor: I
2352-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2353:    ConstantValue: int 2131689486
2354-
2355-  public static int Base_Animation_AppCompat_Tooltip;
2356-    descriptor: I
2357-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2358:    ConstantValue: int 2131689487
2359-
2360-  public static int Base_CardView;
2361-    descriptor: I
2362-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2363:    ConstantValue: int 2131689488
2364-
2365-  public static int Base_DialogWindowTitleBackground_AppCompat;
2366-    descriptor: I
2367-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2368:    ConstantValue: int 2131689490
2369-
2370-  public static int Base_DialogWindowTitle_AppCompat;
2371-    descriptor: I
2372-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2373:    ConstantValue: int 2131689489
2374-
2375-  public static int Base_MaterialAlertDialog_MaterialComponents_Title_Icon;
2376-    descriptor: I
2377-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2378:    ConstantValue: int 2131689491
2379-
2380-  public static int Base_MaterialAlertDialog_MaterialComponents_Title_Panel;
2381-    descriptor: I
2382-    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
2383:    ConstantValue: int 2131689492
2384-

exec
/bin/bash -lc "javap -classpath /tmp/material-classes.jar -p -c com.google.android.material.button.MaterialButton | rg -n \"static \\{|DEF_STYLE_RES|Widget_MaterialComponents_Button\" -A40 -B4 | sed -n '1,160p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

 succeeded in 101ms:
PorterDuff$Mode;
114-      84: invokestatic  #21                 // Method com/google/android/material/internal/ViewUtils.parseTintMode:(ILandroid/graphics/PorterDuff$Mode;)Landroid/graphics/PorterDuff$Mode;
115-      87: putfield      #22                 // Field iconTintMode:Landroid/graphics/PorterDuff$Mode;
116-      90: aload_0
117-      91: aload_0
118-      92: invokevirtual #12                 // Method getContext:()Landroid/content/Context;
119-      95: aload         4
120-      97: getstatic     #23                 // Field com/google/android/material/R$styleable.MaterialButton_iconTint:I
121-     100: invokestatic  #24                 // Method com/google/android/material/resources/MaterialResources.getColorStateList:(Landroid/content/Context;Landroid/content/res/TypedArray;I)Landroid/content/res/ColorStateList;
122-     103: putfield      #25                 // Field iconTint:Landroid/content/res/ColorStateList;
123-     106: aload_0
124-     107: aload_0
125-     108: invokevirtual #12                 // Method getContext:()Landroid/content/Context;
126-     111: aload         4
127-     113: getstatic     #26                 // Field com/google/android/material/R$styleable.MaterialButton_icon:I
128-     116: invokestatic  #27                 // Method com/google/android/material/resources/MaterialResources.getDrawable:(Landroid/content/Context;Landroid/content/res/TypedArray;I)Landroid/graphics/drawable/Drawable;
129-     119: putfield      #28                 // Field icon:Landroid/graphics/drawable/Drawable;
130-     122: aload_0
131-     123: aload         4
132-     125: getstatic     #29                 // Field com/google/android/material/R$styleable.MaterialButton_iconGravity:I
133-     128: iconst_1
134-     129: invokevirtual #31                 // Method android/content/res/TypedArray.getInteger:(II)I
135-     132: putfield      #32                 // Field iconGravity:I
136-     135: aload_0
137-     136: aload         4
--
141-     145: putfield      #34                 // Field iconSize:I
142-     148: aload_1
143-     149: aload_2
144-     150: iload_3
145:     151: getstatic     #4                  // Field DEF_STYLE_RES:I
146-     154: invokestatic  #35                 // Method com/google/android/material/shape/ShapeAppearanceModel.builder:(Landroid/content/Context;Landroid/util/AttributeSet;II)Lcom/google/android/material/shape/ShapeAppearanceModel$Builder;
147-     157: invokevirtual #36                 // Method com/google/android/material/shape/ShapeAppearanceModel$Builder.build:()Lcom/google/android/material/shape/ShapeAppearanceModel;
148-     160: astore        5
149-     162: aload_0
150-     163: new           #37                 // class com/google/android/material/button/MaterialButtonHelper
151-     166: dup
152-     167: aload_0
153-     168: aload         5
154-     170: invokespecial #38                 // Method com/google/android/material/button/MaterialButtonHelper."<init>":(Lcom/google/android/material/button/MaterialButton;Lcom/google/android/material/shape/ShapeAppearanceModel;)V
155-     173: putfield      #39                 // Field materialButtonHelper:Lcom/google/android/material/button/MaterialButtonHelper;
156-     176: aload_0
157-     177: getfield      #39                 // Field materialButtonHelper:Lcom/google/android/material/button/MaterialButtonHelper;
158-     180: aload         4
159-     182: invokevirtual #40                 // Method com/google/android/material/button/MaterialButtonHelper.loadFromAttributes:(Landroid/content/res/TypedArray;)V
160-     185: aload         4
161-     187: invokevirtual #41                 // Method android/content/res/TypedArray.recycle:()V
162-     190: aload_0
163-     191: aload_0
164-     192: getfield      #17                 // Field iconPadding:I
165-     195: invokevirtual #42                 // Method setCompoundDrawablePadding:(I)V
166-     198: aload_0
167-     199: aload_0
168-     200: getfield      #28                 // Field icon:Landroid/graphics/drawable/Drawable;
169-     203: ifnull        210
170-     206: iconst_1
171-     207: goto          211
172-     210: iconst_0
173-     211: invokespecial #43                 // Method updateIcon:(Z)V
174-     214: return
175-
176-  java.lang.String getA11yClassName();
177-    Code:
178-       0: aload_0
179-       1: getfield      #44                 // Field accessibilityClassName:Ljava/lang/String;
180-       4: invokestatic  #45                 // Method android/text/TextUtils.isEmpty:(Ljava/lang/CharSequence;)Z
181-       7: ifne          15
182-      10: aload_0
183-      11: getfield      #44                 // Field accessibilityClassName:Ljava/lang/String;
184-      14: areturn
185-      15: aload_0

exec
/bin/bash -lc "javap -classpath /tmp/material-classes.jar -p -c com.google.android.material.button.MaterialButton | sed -n '1110,1165p'" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

 succeeded in 105ms:
exec
/bin/bash -lc 'javap -classpath /tmp/material-classes.jar -p -c com.google.android.material.button.MaterialButton | tail -n 80' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
       0: aload_0
       1: invokespecial #68                 // Method isUsingOriginalBackground:()Z
       4: ifeq          15
       7: aload_0
       8: getfield      #39                 // Field materialButtonHelper:Lcom/google/android/material/button/MaterialButtonHelper;
      11: invokevirtual #213                // Method com/google/android/material/button/MaterialButtonHelper.getShapeAppearanceModel:()Lcom/google/android/material/shape/ShapeAppearanceModel;
      14: areturn
      15: new           #210                // class java/lang/IllegalStateException
      18: dup
      19: ldc           #214                // String Attempted to get ShapeAppearanceModel from a MaterialButton which has an overwritten background.
      21: invokespecial #212                // Method java/lang/IllegalStateException."<init>":(Ljava/lang/String;)V
      24: athrow

  void setOnPressedChangeListenerInternal(com.google.android.material.button.MaterialButton$OnPressedChangeListener);
    Code:
       0: aload_0
       1: aload_1
       2: putfield      #215                // Field onPressedChangeListenerInternal:Lcom/google/android/material/button/MaterialButton$OnPressedChangeListener;
       5: return

  public void setPressed(boolean);
    Code:
       0: aload_0
       1: getfield      #215                // Field onPressedChangeListenerInternal:Lcom/google/android/material/button/MaterialButton$OnPressedChangeListener;
       4: ifnull        18
       7: aload_0
       8: getfield      #215                // Field onPressedChangeListenerInternal:Lcom/google/android/material/button/MaterialButton$OnPressedChangeListener;
      11: aload_0
      12: iload_1
      13: invokeinterface #216,  3          // InterfaceMethod com/google/android/material/button/MaterialButton$OnPressedChangeListener.onPressedChanged:(Lcom/google/android/material/button/MaterialButton;Z)V
      18: aload_0
      19: iload_1
      20: invokespecial #217                // Method androidx/appcompat/widget/AppCompatButton.setPressed:(Z)V
      23: return

  private boolean isUsingOriginalBackground();
    Code:
       0: aload_0
       1: getfield      #39                 // Field materialButtonHelper:Lcom/google/android/material/button/MaterialButtonHelper;
       4: ifnull        21
       7: aload_0
       8: getfield      #39                 // Field materialButtonHelper:Lcom/google/android/material/button/MaterialButtonHelper;
      11: invokevirtual #218                // Method com/google/android/material/button/MaterialButtonHelper.isBackgroundOverwritten:()Z
      14: ifne          21
      17: iconst_1
      18: goto          22
      21: iconst_0
      22: ireturn

  void setShouldDrawSurfaceColorStroke(boolean);
    Code:
       0: aload_0
       1: invokespecial #68                 // Method isUsingOriginalBackground:()Z
       4: ifeq          15
       7: aload_0
       8: getfield      #39                 // Field materialButtonHelper:Lcom/google/android/material/button/MaterialButtonHelper;
      11: iload_1
      12: invokevirtual #219                // Method com/google/android/material/button/MaterialButtonHelper.setShouldDrawSurfaceColorStroke:(Z)V
      15: return

  static {};
    Code:
       0: iconst_1
       1: newarray       int
       3: dup
       4: iconst_0
       5: ldc           #221                // int 16842911
       7: iastore
       8: putstatic     #187                // Field CHECKABLE_STATE_SET:[I
      11: iconst_1
      12: newarray       int
      14: dup
      15: iconst_0
      16: ldc           #222                // int 16842912
      18: iastore
      19: putstatic     #189                // Field CHECKED_STATE_SET:[I
      22: getstatic     #223                // Field com/google/android/material/R$style.Widget_MaterialComponents_Button:I
      25: putstatic     #4                  // Field DEF_STYLE_RES:I
      28: return
}

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

exec
/bin/bash -lc 'RJAR=fixture/sample-app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar; tmp=$(mktemp -d /tmp/rjar.XXXXXX); (cd "$tmp" && jar xf "$OLDPWD/$RJAR") 2>/dev/null || true; echo tmp=$tmp; find "$tmp" -name '"'R"'$*.class'"' | wc -l; rm -rf \""'$tmp"' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
tmp=/tmp/rjar.h5eJHh
191

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

exec
/bin/bash -lc "if [ -f ~/.claude/CLAUDE.md ]; then nl -ba ~/.claude/CLAUDE.md | sed -n '1,260p'; else echo 'no ~/.claude/CLAUDE.md'; fi" in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
     1	# Code Style Guidelines
     2	
     3	## Naming (Java-style)
     4	- Local/temp: `tValue` · Members: `mValue` · Functions: `camelCase` · Classes: `PascalCase`
     5	- **Function parameters**: plain Java-style names — **never** prefix with `m` or `t`
     6	  - OK: `userName`, `retryCount`, `sensorValue`  ·  NG: `tUserName`, `mRetryCount`
     7	
     8	## File Structure
     9	- **One class per file**
    10	- **C++**: header-only (no separate `.cpp` per class)
    11	- Class layout order: member variables → constructor(s) → public methods → private methods
    12	
    13	## Brace Style
    14	- Always use `{}` — even for single-line `if`/`for`/`while`
    15	- Opening brace on its **own new line**
    16	
    17	```cpp
    18	if (condition)
    19	{
    20	    // ...
    21	}
    22	```
    23	
    24	## Constructor
    25	- Assign members inside `{}` body, **not** via initializer list
    26	- Extract helpers if the constructor grows complex
    27	
    28	## Function Parameters
    29	- **No default parameter values**
    30	- Do not use `m`/`t` prefixes on parameters
    31	- When 3+ parameters all come from the same struct/class → pass the object (`shared_ptr`) directly and extract fields inside the function. Use `/simplify-params` to find and apply this pattern.
    32	
    33	## Comments
    34	- **Never delete existing comments**
    35	- Add professional comments for non-trivial logic
    36	- **No emojis in any code**
    37	
    38	## Constants — Zero Tolerance for Magic Numbers/Strings
    39	Every literal used in logic (thresholds, sizes, timeouts, indices, retries, config, even `0`/`1`/`"\n"` if it carries domain meaning) MUST be a named constant.
    40	
    41	- **Organize by domain, one responsibility per header**. Create a new header for each new domain; never dump into an unrelated file:
    42	  - `NetworkConstants.h` · `SerialConstants.h` · `UiConstants.h` · `ProtocolConstants.h` · `TimingConstants.h` · `PinConstants.h` · `SensorConstants.h`
    43	- Prefer `static constexpr` inside a class; use `#define` only when compile-time string concat or preprocessor usage is required
    44	- Names describe purpose, not value → `MAX_RETRY_COUNT`, not `THREE`
    45	- Same value used in 2+ places → extract **immediately** (no "refactor later")
    46	
    47	## C++ Memory — `shared_ptr` is the Default
    48	
    49	Default ownership for any class/struct member holding a **user-defined type** = `std::shared_ptr<T>`, even if the type looks trivially copyable. Goal: eliminate dangling references, stack-heap lifetime mismatches, accidental aliasing, and silent slicing at the root.
    50	
    51	**Rules of thumb (apply before writing any class member declaration)**:
    52	1. Member is a user-defined `class`/`struct` → `std::shared_ptr<T>`
    53	2. Primitive (`int`, `bool`, `float`, `enum class`) → plain value
    54	3. Container of primitives → plain value unless mutated/reassigned across call boundaries (then wrap)
    55	4. Container of user-defined types → `std::shared_ptr<std::vector<std::shared_ptr<T>>>` — outer AND inner wrapped
    56	
    57	**Allocation**: always `std::make_shared<T>(args...)`. **Never bare `new`**.
    58	
    59	**Argument types must match**: a function consuming a shared type takes `std::shared_ptr<T>` (or `const std::shared_ptr<T>&` on hot paths). Never strip ownership via a raw reference in a class member.
    60	
    61	**Anti-patterns — rejected**:
    62	- "It's just two `int`s — a plain value is lighter" — rejected; safety > allocation
    63	- "POD has no lifetime risk" — rejected for class members; refactors will grow around them
    64	- "We never mutate it, a raw reference is fine" — rejected; raw reference members are dangling waiting to happen
    65	- "`unique_ptr` is cheaper" — rejected; `shared_ptr` survives refactorings that transfer/duplicate ownership (common in multi-threaded robot/sensor code). Use `unique_ptr` only with an explicit architectural comment
    66	
    67	**Narrow exceptions (document why in a comment)**:
    68	- Program-wide constants (`static const std::map<Enum,Config>`) initialized at startup and never mutated
    69	- Stack-local aggregates passed between private methods in the same stack frame — `// valid only for scope of compute(); do not copy or store`
    70	- Primitive fields inside a value struct
    71	- One-shot command values taken by `const&` and never stored
    72	
    73	**Clone for deep copy**: any class with internal state/resources that must be duplicated needs a `clone()` that performs a **deep** copy, not shallow.
    74	
    75	**During refactoring**: treat any bare user-defined member (`MotionSequenceConfig mConfig;`) as a conversion candidate unless a comment already documents why it must remain a value.
    76	
    77	## C++14 Compatibility (default — `CMAKE_CXX_STANDARD 14`)
    78	
    79	Code must compile **AND link** under `-std=gnu++14`. A clean C++17 build does NOT prove C++14 safety — most failures surface at link time. Only protobuf/gRPC targets upgrade to C++17 (abseil requirement); every other target is C++14.
    80	
    81	### ODR-use trap — the #1 C++14 link failure
    82	
    83	In C++14, a `static constexpr` class member is declaration-only for ODR-use. Binding it to a `const T&` parameter takes its address → linker expects an out-of-class definition that doesn't exist in a header-only constants class → `undefined reference to Constants::FOO`. In C++17 members are implicit `inline`, so the same source links silently. **The same code compiles under both standards but only fails to link under C++14.**
    84	
    85	**ODR-use triggers** (anything that binds to `const T&`):
    86	- `std::max`, `std::min`, `std::clamp`, `std::swap`
    87	- `std::chrono::duration` constructors (`milliseconds`, `microseconds`, `seconds`, …)
    88	- Container inserters: `push_back(const T&)`, `emplace*`, `std::pair` ctors
    89	- `const auto& x = Constants::FOO;`, capture-by-reference in lambdas, `std::ref`/`std::cref`, range-based for
    90	
    91	**Non-ODR (safe)**: arithmetic/built-in operators (`C::F * x`), casts (`static_cast<int>(C::F)`), pass-by-value call, `int x = C::DEFAULT;`.
    92	
    93	### Fix — local-copy pattern
    94	
    95	Introduce a local copy at the call site. The local has its own address, the reference binds to it, the original member is never ODR-used. Works identically in C++14 and C++17, zero runtime cost (compiler elides it), and preserves the header-only policy.
    96	
    97	```cpp
    98	// WRONG — C++14 link error: undefined reference to Constants::FLOOR
    99	tResult = std::max(HorizontalMovingConstant::MIN_ARRIVAL_TIME_FLOOR, tValue);
   100	this_thread::sleep_for(chrono::milliseconds(HorizontalMovingConstant::POLLING_INTERVAL_MS));
   101	
   102	// CORRECT — local copy breaks ODR-use
   103	const float tFloor = HorizontalMovingConstant::MIN_ARRIVAL_TIME_FLOOR;
   104	tResult = std::max(tFloor, tValue);
   105	const int tPollingIntervalMs = HorizontalMovingConstant::POLLING_INTERVAL_MS;
   106	this_thread::sleep_for(chrono::milliseconds(tPollingIntervalMs));
   107	```
   108	
   109	- Hoist the local to enclosing function/loop scope if reused — declare once
   110	- Comment the reason: `// C++14 ODR-use 회피: const T& 바인딩용 지역 복사본`
   111	- **Do NOT** add a `.cpp` just to provide the out-of-class definition — it breaks header-only policy
   112	
   113	### C++17-only features FORBIDDEN in C++14 targets
   114	- `inline` variables (namespace-scope or class data members)
   115	- `std::optional`, `std::variant`, `std::any`, `std::string_view`, `std::filesystem`, `std::byte`
   116	- `if constexpr`, fold expressions, class template argument deduction (CTAD)
   117	- Structured bindings (`auto [a, b] = ...`)
   118	- Nested namespace syntax `namespace a::b::c {}` — use classic nested form
   119	- Attributes: `[[nodiscard]]`, `[[maybe_unused]]`, `[[fallthrough]]`
   120	- `std::apply`, `std::invoke`, `std::make_from_tuple`, `std::shared_mutex`, `std::scoped_lock`
   121	
   122	### Verification discipline
   123	- Before claiming "C++14-safe": mentally verify every call site — *does any callee bind this value to a reference?*
   124	- When in doubt on a new `static constexpr` usage → apply local-copy **preemptively** (free at runtime, costs one line)
   125	- If a pre-existing ODR-use site turns up during refactor/review → **fix it in the same change**, do not defer
   126	- A target showing `-std=gnu++17` despite CMake pinning C++14 is usually Qt5/abseil leakage and does NOT license new C++17 usage — other build machines will expose the failure
   127	
   128	## Lambdas — Avoid
   129	- Prefer named functions, static methods, or functor classes
   130	- **Allowed only** when stdlib API strictly requires a callable (e.g., `std::sort` comparator) AND a named alternative would be significantly more verbose
   131	- Custom `shared_ptr` deleters → use a named no-op deleter struct, not a lambda
   132	- Never lambdas for multi-line complex logic — extract a named method
   133	
   134	## Architecture
   135	- **One class = one responsibility**. Do not mix UI / business logic / I/O / protocol / state in one class
   136	- Controllers orchestrate — they do **not** embed detailed business logic
   137	- Favor composition over complex inheritance
   138	- Separate policy, mechanism, and data representation
   139	- Before coding, check for a better structure. Split responsibilities early instead of accumulating debt
   140	
   141	## Change Policy
   142	- Modifying code NOT explicitly requested → **explain why** in the turn
   143	- Do not silently refactor unrelated areas
   144	- Preserve existing intent, comments, and behavior unless the change is necessary and explained
   145	
   146	## Git & `.gitignore` Policy
   147	- `.gitignore` entries are excluded intentionally — never track ignored files
   148	- **`git add -f` / `--force` is blocked by a PreToolUse hook** (`$HOME/.claude/hooks/block-git-add-force.sh`). If truly required, ask the user to update `.gitignore` first
   149	- Do not modify `.gitignore` to remove entries unless the user explicitly asks
   150	- Do not bypass `.gitignore` with force-add, force-push, or any other override
   151	
   152	## Skill Usage — gstack Restriction
   153	**Never proactively invoke or suggest any gstack skill.** Only invoke when the user explicitly requests it by name or specific functionality.
   154	
   155	gstack skills (do NOT auto-trigger): `browse`, `gstack`, `connect-chrome`, `setup-browser-cookies`, `qa`, `qa-only`, `design-review`, `design-shotgun`, `design-html`, `design-consultation`, `canary`, `benchmark`, `devex-review`, `ship`, `land-and-deploy`, `setup-deploy`, `document-release`, `checkpoint`, `freeze`, `unfreeze`, `guard`, `careful`, `health`, `retro`, `learn`, `loop`, `schedule`, `office-hours`, `plan-ceo-review`, `plan-eng-review`, `plan-design-review`, `plan-devex-review`, `autoplan`, `review`, `codex`, `cso`, `gstack-upgrade`
   156	
   157	When the user says "use gstack" / "gstack mode" / "gstack help" → invoke `/gstack-auto` (single entry point — handles intent classification, pipeline sequencing, dependencies).
   158	
   159	## User-Authored Skill Improvement Policy
   160	- **Scope**: ONLY skills the user authored locally (`$HOME/.claude/skills/` or a project-local `.claude/skills/` the user maintains). **Plugin-namespaced skills** (`superpowers:*`, `figma:*`, `codex:*`, `data:*`, `coderabbit:*`, `sentry:*`, `supabase:*`, etc.) and anything under `$HOME/.claude/plugins/` are **out of scope — never edit as part of this policy**. Enforced by a PreToolUse hook (`$HOME/.claude/hooks/block-plugin-edits.sh`) that blocks Edit/Write/NotebookEdit under `$HOME/.claude/plugins/`.
   161	- **Trigger**: after a task in which a user-authored skill was actually invoked, evaluate whether it has concrete improvement opportunities. If — and only if — yes, update the skill file in place as task wrap-up.
   162	- **Qualifying issues** (non-exhaustive): trigger gap (should have fired for this scenario / missing phrase in description); false-trigger risk (fired out of scope → narrow description); missing step or edge case; ambiguous/contradictory instructions that caused rework; outdated references (paths, APIs, flags, examples); missing anti-pattern the task just exposed.
   163	- **Do NOT edit** when: skill worked cleanly end-to-end; change is cosmetic reword with no trigger/behavior change; change is speculative ("might be useful someday"); change would broaden scope beyond the skill's single responsibility — prefer a new skill in that case.
   164	- **Discipline**: surgical edits only — do not rewrite unrelated sections. Preserve existing structure, ordering, voice, and all still-correct content (extends the "never delete existing comments" rule). If the change alters scope or trigger surface, confirm with the user before editing.
   165	- **Reporting**: when a skill is edited, include in the final summary — (a) which file, (b) concrete gap that motivated the edit, (c) one-line change description. When no edit is warranted, no reporting — do not fabricate improvements.
   166	
   167	## Agent Team Dispatch — Choose the More Efficient Mode Per Task
   168	
   169	When the user requests an agent team, parallel agents, multi-agent review, or any workflow that spawns 2+ concurrent agents, **evaluate which mode is actually more efficient for THIS task and pick that one**. There is no default reflex in either direction. The two modes are:
   170	
   171	- **Subagent mode** — in-process `Agent(subagent_type, ...)` calls dispatched in parallel within a single turn.
   172	- **Teammate mode** — `TeamCreate` + `Agent({team_name, name, ...})` spawning real OS processes with their own tmux panes via Claude Code's native Teams feature.
   173	
   174	Pick by the efficiency axes below. When the comparison is genuinely ambiguous, **prefer the cheaper mode (subagents)** — but that is a tie-breaker on cost, not a default position.
   175	
   176	### Efficiency Axes — evaluate before dispatching
   177	
   178	1. **Token cost.** Each teammate is a separate OS process with its own context window, its own system prompt re-load, and its own MCP server reconnections (context7, figma, serena, chrome-devtools, playwright, sentry, supabase, notion, etc.). 3 teammates ≠ 3× a subagent — it is closer to 5–10× once you count reconnection prelude, idle cache misses, and inter-teammate `SendMessage` round-trips. For short or moderate tasks this single axis usually decides in favor of subagents.
   179	
   180	2. **Wall-clock cost.** Teammate spawn is **seconds** (process start + MCP init + tmux pane registration); subagent spawn is **milliseconds** (in-process). For short tasks the spawn cost dominates the work cost, and subagents win on this axis. For genuinely long-running work (> ~5 min per agent) the spawn cost amortizes and stops mattering.
   181	
   182	3. **Capability fit.** Subagents return a single consolidated result block at the end and have no cross-turn persistence; teammates render live tmux panes, retain accumulating context across turns, and can coordinate via `SendMessage`. When the task *needs* live visibility, cross-turn state, or message-based gating, this axis flips the decision toward teammates regardless of cost — capability is part of efficiency too (paying less but failing the task is not efficient).
   183	
   184	### Signals Favoring Teammate Mode — efficiency wins for teammates when ANY clearly applies
   185	
   186	1. **Multi-turn supervised collaboration**: the work spans several user turns and a teammate must retain its own accumulating context, hold partial state, and resume on the next turn without re-priming. (Example: a review teammate watching successive PR pushes.)
   187	2. **Live progress visibility is genuinely needed**: the user explicitly asks to see panes / `ctrl-o` the teammate manager / monitor a long-running pipeline as it streams, AND is making decisions while the work runs. Aesthetic preference alone does not qualify.
   188	3. **Long-running pipeline (> ~5 min per agent)** where waiting for a single consolidated subagent block would block the user too long, AND mid-flight intervention is plausible.
   189	4. **Claude+Codex 1:1 pair work for high-stakes planning/review** (see Codex Pairing section below). Pairing inherently needs persistent two-way coordination and side-by-side reconciliation across turns.
   190	5. **Independent role-based pipelines that must coordinate via `SendMessage`** (handoffs, gating, review rounds) rather than just running in parallel and merging results.
   191	
   192	### Signals Favoring Subagent Mode — efficiency wins for subagents when ANY clearly applies
   193	
   194	- Single-agent research, exploration, or lookup — one `Agent` call.
   195	- N-way parallel single-shot work that fits in one turn ("explore these 4 files in parallel", "rate these 5 candidates").
   196	- Mechanical one-shot refactor, lookup, status check, or format-only change.
   197	- Multi-role review/planning that fits one turn — dispatch role-tagged subagents in parallel and consolidate in the next turn.
   198	- User explicitly says "lightweight" / "in-session" / "no team needed" / "just subagents".
   199	- **None of the teammate signals above clearly applies** — the cost axes default the answer to subagents.
   200	
   201	"It feels team-ish", "the user said the word *team* / *agents* / *parallel*", and "there are multiple roles" are NOT teammate signals on their own — multiple roles can run as a single-turn parallel subagent burst with role-tagged prompts. Verify a real signal from the list above before paying teammate cost.
   202	
   203	### Hybrid 2-Tier Pattern — when teammates win the comparison
   204	
   205	Subagents and teammates are complementary, not competing:
   206	
   207	- **Subagent (leaf tier)** = lightweight, one-shot, high-parallelism. In-process spawn (~ms), no live visibility, returns a single consolidated result block, no state across turns, **shares parent's MCP connections**. Best for single-shot research, exploration, lookups, mechanical refactors, and N-way parallel single-shot work dispatched in one turn.
   208	- **Teammate (supervisory tier)** = heavyweight, persistent, observable. Separate OS process (~seconds to spawn), renders its own tmux pane with live streaming, survives across turns in idle state, **establishes its own MCP connections per process**, coordinates via `SendMessage` and shared task list. Justified by the signals above.
   209	
   210	When teammates win, prefer a **2-tier hybrid**: a small number of teammates (often just 1–2) at the supervisory tier, each internally dispatching subagent bursts for its own sub-tasks. This minimizes teammate count while still getting burst parallelism at the leaf — efficient on both axes.
   211	
   212	### Sizing & Cost Discipline
   213	
   214	- **Prefer the smallest teammate count that satisfies the signals.** 1–2 teammates is often plenty; **3 is the practical ceiling** for routine work; **5 is a hard cap** absent extraordinary justification. Each additional teammate pays full MCP reconnection + cache cost.
   215	- **Prompt cache TTL is 5 minutes.** Long-idle teammates miss cache on wake-up, once per teammate. Shut down teammates the moment their phase is clearly done — do not leave them idle "in case" they are useful later.
   216	- **Each teammate maintains its own accumulating context.** Long-lived teammates grow their context window over time. If a teammate's context bloats or pollutes, prefer `SendMessage({type: "shutdown_request"})` + respawn over trying to reset in-place.
   217	- **Teammate spawn is NOT free.** If a task fits in one subagent burst in a single turn, **never** spawn teammates just for visual parallelism — the spawn cost (process + MCP init + tmux pane) routinely exceeds the work itself.
   218	
   219	### Required Dispatch Pattern (when teammate mode wins)
   220	1. **Create the team first**: `TeamCreate({team_name: "<slug>", description: "<purpose>"})`. Use short, role-based slugs (e.g., `review-crew`, `refactor-squad`, `debug-crew`).
   221	2. **Spawn each teammate with a role name**: `Agent({team_name, name: "<role>", subagent_type, prompt})`. Names must be human-readable roles (`fact-checker`, `tone-reviewer`, `backend-impl`) — never UUIDs or numeric suffixes.
   222	3. **Parallel spawn in ONE message**: issue multiple `Agent` calls in a single assistant turn so the harness splits panes simultaneously. Sequential spawns across turns lose the visual parallelism.
   223	4. **Match `subagent_type` to the task's tool needs**: read-only types (`Explore`, `Plan`) for research; full-capability types (`general-purpose`) for implementation. Never assign implementation work to a read-only teammate.
   224	5. **Coordinate via `SendMessage`**: address teammates by `name`. Plain text output is NOT visible to teammates — always use `SendMessage` to talk to them. Teammate messages are delivered automatically; never poll an inbox.
   225	6. **Graceful shutdown**: send `SendMessage({to: "<name>", message: {type: "shutdown_request"}})` to each teammate when the work is done, then call `TeamDelete()` after they exit to clean up `~/.claude/teams/` and `~/.claude/tasks/`.
   226	
   227	### Reporting When You Choose Teammate Mode
   228	When teammate mode wins the efficiency comparison, **state the deciding signal in one short line at the top of the dispatch turn** — which signal applied, and why a subagent burst would have been less efficient for THIS task. This keeps the cost/benefit decision conscious rather than reflexive, and makes it easy for the user to push back ("no, just use subagents").
   229	
   230	### Do NOT
   231	- Spawn teammates just because the user said "team", "agents", or "parallel" — verify a real teammate signal first.
   232	- Spawn teammates as a reflex for review/audit/planning workflows that fit one turn — those are subagent territory unless they need cross-turn persistence or Codex pairing.
   233	- Skip `TeamCreate` and expect `team_name` on `Agent` alone to work — the team must exist first.
   234	- Spawn teammates sequentially across multiple turns — splits panes only partially and loses the live parallel visual.
   235	- Leave orphaned teams after a session — always `TeamDelete` once all teammates have shut down.
   236	
   237	### Interaction with Codex Pairing (see below)
   238	The Codex 1:1 pairing policy is the main standing case where teammate mode wins on the capability-fit axis (Signal #4). When pairing applies, use teammate mode for the Claude side of each pair (one Claude teammate per pair in its own pane). Codex is dispatched through its own runtime and is not a tmux teammate — keep its output separate and reconcile side-by-side in the consolidation step. **Pairing itself remains gated by the "Pairs REQUIRED for / NOT required for" lists below — routine pair-eligible tasks that fit one turn can still run as a parallel subagent burst with a single Codex call alongside.**
   239	
   240	## Codex Agent Invocation Policy
   241	
   242	### Model & Effort — Always Top Model at xhigh Effort
   243	Codex invocations have **two independent axes** — pick both correctly:
   244	
   245	- **Model** = always the **latest / highest-capability GPT model available in Codex CLI** at invocation time (as of 2026-04 this is **GPT-5.4**; when 5.5 / 6.0 ship, roll forward automatically). Do NOT hardcode a version — phrase it as "the highest available GPT model".
   246	- **Effort / reasoning tier** = always **`xhigh`** (the highest reasoning effort level Codex CLI exposes, e.g. `--effort xhigh` or the equivalent config key). Lower tiers (`low` / `medium` / `high`) are **forbidden** regardless of task size.
   247	
   248	**Do NOT** concatenate the two into a fake single identifier like `gpt-5-xhigh`, `gpt-5.4-xhigh`, or `gpt-xhigh` — no such model exists, the call will fail with "model not found". They are two separate flags / config fields.
   249	
   250	Include an explicit line in every Codex prompt: *"Use the highest available GPT model (currently GPT-5.4 or newer) with reasoning effort set to `xhigh`. No time limit. Maximum reasoning depth."*
   251	
   252	**No exceptions**, regardless of task size — the entire point of Codex is a second opinion from a different model family at maximum reasoning strength. Stuck Codex agent → tighten scope or add explicit stop conditions; **never** downgrade the model or effort to work around it.
   253	
   254	### 1:1 Claude+Codex Pairing — Planning & Plan-Review ONLY
   255	
   256	**Scope restriction (hard rule)**: Claude+Codex 1:1 pairing is used **exclusively during the planning and plan-review phases** (i.e. before any implementation code is written, or while reviewing a written-but-not-yet-executed plan document). Once a plan is approved and implementation begins, **all subsequent work — including code audits, PR reviews, post-phase reviews, post-commit audits, risk audits of shipped code, mid-implementation sanity checks, and retrospective reviews of implementation — is Claude-only**. Do not dispatch Codex during implementation, and do not gate a commit/phase on a Codex audit once implementation has started.
   257	
   258	**Why narrowed**: The cross-family triangulation Codex provides is most valuable when the artifact is *design intent* (still malleable, high-leverage if wrong, no ground truth to test against). Once implementation is under way, the artifact is *code* — cheaper to verify by building, running, and comparing to the approved plan than by re-running a second-opinion pair against every change.
   259	
   260	**When pairs ARE dispatched (planning / plan-review only)**: dispatch as **1:1 Claude+Codex pairs**, one pair per unit of work (per field, per topic, per contested decision), running in parallel. Each half must NOT see the other's output while working — convergence/divergence IS the confidence signal.

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

Evidence gathering found three NO_GO issues: stale AAR cache invalidation, R$styleable index seeding, and missing app/library resource values for Material style resolution.
  ✓ Read project instructions and target specs/logs
  ✓ Inspect current implementation and dependencies
  ✓ Run requested bytecode/classpath probes
  ✓ Benchmark one AAR extraction/rewrite path
  → Draft and save review report
  • Verify report file and summarize verdict
diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

exec
/bin/bash -lc 'test -e /tmp/w3d3-alpha-codex-spec-review.md && echo exists || echo missing' in /home/bh-mark-dev-desktop/workspace/android_xml_previewer
 succeeded in 0ms:
exists

diff --git a//tmp/BenchAarExtractor.java b//tmp/BenchAarExtractor.java
new file mode 100644
index 0000000000000000000000000000000000000000..896ab94f019b9125858adae87046eed6549897a6
--- /dev/null
+++ b//tmp/BenchAarExtractor.java
@@ -0,0 +1,34 @@
+import dev.axp.layoutlib.worker.classloader.AarExtractor;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Comparator;
+
+public class BenchAarExtractor {
+    private static void clear(Path path) throws Exception {
+        if (!Files.exists(path)) {
+            return;
+        }
+        try (var stream = Files.walk(path)) {
+            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
+                Files.delete(entry);
+            }
+        }
+    }
+
+    public static void main(String[] args) throws Exception {
+        Path aar = Path.of(args[0]);
+        Path cache = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            clear(cache);
+            long start = System.nanoTime();
+            Path out = AarExtractor.INSTANCE.extract(aar, cache);
+            long end = System.nanoTime();
+            System.out.printf("cold[%d]=%.3f ms size=%d%n", i, (end - start) / 1_000_000.0, Files.size(out));
+        }
+
+        long start = System.nanoTime();
+        Path out = AarExtractor.INSTANCE.extract(aar, cache);
+        long end = System.nanoTime();
+        System.out.printf("warm=%.3f ms size=%d%n", (end - start) / 1_000_000.0, Files.size(out));
+    }
+}
diff --git a//tmp/BenchAsmRewrite.java b//tmp/BenchAsmRewrite.java
new file mode 100644
index 0000000000000000000000000000000000000000..90889fff9fd43a5778a102bfb2481de4a4057d20
--- /dev/null
+++ b//tmp/BenchAsmRewrite.java
@@ -0,0 +1,84 @@
+import java.io.ByteArrayInputStream;
+import java.io.ByteArrayOutputStream;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.Map;
+import java.util.zip.ZipEntry;
+import java.util.zip.ZipInputStream;
+import java.util.zip.ZipOutputStream;
+import org.objectweb.asm.ClassReader;
+import org.objectweb.asm.ClassWriter;
+import org.objectweb.asm.commons.ClassRemapper;
+import org.objectweb.asm.commons.Remapper;
+
+public class BenchAsmRewrite {
+    private static final Map<String, String> NAME_MAP = Map.ofEntries(
+        Map.entry("android/os/Build", "android/os/_Original_Build"),
+        Map.entry("android/os/Build$Partition", "android/os/_Original_Build$Partition"),
+        Map.entry("android/os/Build$VERSION", "android/os/_Original_Build$VERSION"),
+        Map.entry("android/os/Build$VERSION_CODES", "android/os/_Original_Build$VERSION_CODES"),
+        Map.entry("android/os/ServiceManager", "android/os/_Original_ServiceManager"),
+        Map.entry("android/os/ServiceManager$ServiceNotFoundException", "android/os/_Original_ServiceManager$ServiceNotFoundException"),
+        Map.entry("android/os/ServiceManager$Stats", "android/os/_Original_ServiceManager$Stats"),
+        Map.entry("android/view/SurfaceView", "android/view/_Original_SurfaceView"),
+        Map.entry("android/view/SurfaceView$1", "android/view/_Original_SurfaceView$1"),
+        Map.entry("android/view/SurfaceView$SurfaceLifecycleStrategy", "android/view/_Original_SurfaceView$SurfaceLifecycleStrategy"),
+        Map.entry("android/view/SurfaceView$SurfaceViewPositionUpdateListener", "android/view/_Original_SurfaceView$SurfaceViewPositionUpdateListener"),
+        Map.entry("android/view/SurfaceView$SyncBufferTransactionCallback", "android/view/_Original_SurfaceView$SyncBufferTransactionCallback"),
+        Map.entry("android/view/WindowManagerImpl", "android/view/_Original_WindowManagerImpl"),
+        Map.entry("android/view/WindowManagerImpl$1", "android/view/_Original_WindowManagerImpl$1"),
+        Map.entry("android/view/WindowManagerImpl$OnFpsCallbackListenerProxy", "android/view/_Original_WindowManagerImpl$OnFpsCallbackListenerProxy"),
+        Map.entry("android/view/textservice/TextServicesManager", "android/view/textservice/_Original_TextServicesManager"),
+        Map.entry("android/webkit/WebView", "android/webkit/_Original_WebView"),
+        Map.entry("android/webkit/WebView$FindListener", "android/webkit/_Original_WebView$FindListener"),
+        Map.entry("android/webkit/WebView$FindListenerDistributor", "android/webkit/_Original_WebView$FindListenerDistributor"),
+        Map.entry("android/webkit/WebView$HitTestResult", "android/webkit/_Original_WebView$HitTestResult"),
+        Map.entry("android/webkit/WebView$PictureListener", "android/webkit/_Original_WebView$PictureListener"),
+        Map.entry("android/webkit/WebView$PrivateAccess", "android/webkit/_Original_WebView$PrivateAccess"),
+        Map.entry("android/webkit/WebView$RendererPriority", "android/webkit/_Original_WebView$RendererPriority"),
+        Map.entry("android/webkit/WebView$VisualStateCallback", "android/webkit/_Original_WebView$VisualStateCallback"),
+        Map.entry("android/webkit/WebView$WebViewTransport", "android/webkit/_Original_WebView$WebViewTransport")
+    );
+
+    private static final Remapper REMAPPER = new Remapper() {
+        @Override
+        public String map(String internalName) {
+            return NAME_MAP.getOrDefault(internalName, internalName);
+        }
+    };
+
+    private static byte[] rewrite(byte[] bytes) {
+        ClassReader reader = new ClassReader(bytes);
+        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
+        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
+        return writer.toByteArray();
+    }
+
+    public static void main(String[] args) throws Exception {
+        byte[] jarBytes = Files.readAllBytes(Path.of(args[0]));
+        Path outPath = Path.of(args[1]);
+        for (int i = 1; i <= 5; i++) {
+            int classes = 0;
+            long start = System.nanoTime();
+            try (
+                ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
+                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outPath))
+            ) {
+                ZipEntry entry = zin.getNextEntry();
+                while (entry != null) {
+                    byte[] bytes = zin.readAllBytes();
+                    if (entry.getName().endsWith(".class")) {
+                        bytes = rewrite(bytes);
+                        classes++;
+                    }
+                    zout.putNextEntry(new ZipEntry(entry.getName()));
+                    zout.write(bytes);
+                    zout.closeEntry();
+                    entry = zin.getNextEntry();
+                }
+            }
+            long end = System.nanoTime();
+            System.out.printf("rewrite[%d]=%.3f ms classes=%d outSize=%d%n", i, (end - start) / 1_000_000.0, classes, Files.size(outPath));
+        }
+    }
+}

