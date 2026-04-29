# W3D1 Plan Pair Review (partial — Codex unavailable)

**Date**: 2026-04-23
**Artifact reviewed**: `docs/superpowers/plans/2026-04-23-w3-resource-value-loader.md`
**Spec**: `docs/superpowers/specs/2026-04-23-w3-resource-value-loader-design.md`
**Pair policy**: CLAUDE.md "1:1 Claude+Codex Pairing for planning/review" — **partially satisfied**.

---

## 1. Pair status

| Reviewer | Model | Access | Verdict | Status |
|---|---|---|---|---|
| Claude teammate | sonnet (Plan subagent_type) | full file read | **NO_GO → GO after F1/F2** | delivered |
| Codex rescue | — | — | **not dispatched** | ❌ quota exhausted ("You're out of extra usage · resets 10pm Asia/Seoul") |

**Convergence verdict**: single-source, unverified per CLAUDE.md. Claude reviewer's findings are concrete and file-verified (javap + grep on real jars / XML), mitigating the cross-family gap for the specific blockers flagged. The plan is not executed until the listed F1/F2 blockers are mechanically resolved; remaining F3-F6 folded into the plan edits.

---

## 2. Claude reviewer verdict (summary)

**VERDICT**: NO_GO (two blockers). After fixes → GO_WITH_FOLLOWUPS.

### Q-by-Q headline

| Q | Finding |
|---|---|
| Q1 task granularity | OK. Task 8 slightly packed; Task 9 Step 2 split recommended (F6). |
| Q2 TDD discipline | Strong. Only `SessionParamsFactoryTest` diff is "figure it out" — minor. |
| Q3 layoutlib-api 31.13.2 | **VERIFIED** via `javap -p` on cached jar. `StyleResourceValueImpl` / `ResourceValueImpl` / `AttrResourceValueImpl` / `StyleItemResourceValueImpl` / `RenderResources.getStyle(ResourceReference)` / `StyleResourceValue.getParentStyleName()` all match plan assumptions. |
| Q4 kxml2 gotchas | OK. `FEATURE_PROCESS_NAMESPACES=true` + `getAttributeValue(null, name)` correct. `<xliff:g>` handled by depth-accumulating readText. `<eat-comment/>` / COMMENT events naturally skipped. |
| Q5 parent post-process | Logic OK for real dist. Fake test data drifts (F2). |
| Q6 tier3-arch flip | Sound. Missing ERROR_RENDER rejection (F4). |
| Q7 rollback (non-git) | Weak. Mitigate by `mv` instead of `rm` (F5). |
| Q8 integration count | **Off-by-one**: plan says 7 PASS, actual will be 6 PASS + 2 SKIPPED (F3). |
| Q9 missing tasks | **BLOCKER B2**: attrs.xml has 0 top-level `<attr>` — all inside `<declare-styleable>`. Plan's parser skips that entirely → empty attrs map → `?attr/...` lookups fail → runtime ERROR_INFLATION. (F1) |
| Q10 ordering | Serial correct; R1→R2→R3a→R3b→R4 strict dependency chain. |

---

## 3. Follow-ups (applied before execution)

### F1 (BLOCKER) — Parse `<attr>` inside `<declare-styleable>`

**Evidence** (from reviewer's own grep):
```
grep -cE "^<attr "   attrs.xml  → 0
grep -c  "^    <attr name=" attrs.xml → 32  (all nested, 4-space indent)
```

**Fix**: Rewrite `FrameworkValueParser`'s handling of `TAG_DECLARE_STYLEABLE` to recurse and collect child `<attr name="X" format="Y"/>` (or format-less — pure reference attrs) as `ParsedEntry.AttrDef`. Dedupe by name, first-wins (to preserve top-level declaration over nested restatement). Replace the test "declare-styleable 내부 attr 는 수집 안 함" with "declare-styleable 내부 attr 는 AttrDef 로 수집; 중복 first-wins". Update spec §2 line 36 wording.

Post-fix expectation: real attrs.xml produces **≥200 AttrDef entries**.

### F2 (BLOCKER) — Fake test data drift

Revise `FrameworkResourceValueLoaderTest::createFakeDataDir` so fake `themes.xml` / `themes_material.xml` reflects real parent chain:
- Real `themes_material.xml:415`: `<style name="Theme.Material.Light" parent="Theme.Light">`.
- Real `themes.xml:503`: `<style name="Theme.Light">` exists (needs to be seeded in fake `themes.xml`).

Without this, unit tests pass but developers debugging integration regressions hit a confusing "but it works in the unit test" moment.

### F3 — Integration test count

Replace all plan references to "**7 PASS + 2 SKIPPED**" (Task 9 Step 5, Task 10 §7.7.3 template) with "**6 PASS + 2 SKIPPED**":
- 5 pre-existing PASS (Bootstrap, BridgeInit, LayoutlibRendererIntegration, MinimalLayoutlibCallbackIntegration, tier3-arch)
- +1 PASS (tier3-values, flipped from SKIPPED)
- +1 SKIPPED (tier3-glyph, new @Disabled)
- activity_basic remains SKIPPED

### F4 — Reject ERROR_RENDER in flipped tier3-arch

Update Task 9 Step 2 `rejectedStatuses` from `{ERROR_NOT_INFLATED, ERROR_INFLATION}` to `{ERROR_NOT_INFLATED, ERROR_INFLATION, ERROR_RENDER, ERROR_UNKNOWN}`. Prevents draw-phase regressions from silently passing.

### F5 — Reversible delete in non-git

Task 8 Step 1: change `rm server/.../MinimalFrameworkRenderResources.kt` to `mv ...kt ...kt.w3-backup`. Task 10 Step 2 (or Task 11) adds cleanup `rm *.w3-backup` once green.

### F6 — Split Task 9 Step 2

Split into 9a (assertion sets + message rewrite) and 9b (try/catch shape flip). Reduces bisect surface if the integration behaves unexpectedly.

### F7 / F8 — Nits (defer)

- F7: `normalizeRefForm` doesn't handle `@*android:style/` (private ref). Uncommon, defer.
- F8: Add comment on `FrameworkRenderResources.getResolvedResource == getUnresolvedResource` documenting ref resolution delegated to `RenderResources` base class.

---

## 4. Codex reconciliation (deferred → re-deferred)

### 4.1 Timeline

| Attempt | Timestamp (KST) | Result |
|---|---|---|
| 1st | 2026-04-23 ~late | "You're out of extra usage · resets 10pm Asia/Seoul" |
| 2nd | 2026-04-24 ~early | 사용자 "충전됐어" 통지 → 재시도 시 "resets 3:20am Asia/Seoul" (미충전 상태) |
| 3rd | 2026-04-24 12:00 | 세션 복구 후 재시도 시 동일 "resets 3:20am" — **본 세션 내 Codex dispatch 불가 확정** |

### 4.2 Root-cause analysis

1. **정책 축 간 비대칭**. CLAUDE.md 는 Codex 호출 시 (a) 최상위 GPT 모델 + (b) `xhigh` reasoning effort 를 예외 없이 요구. 이 두 축 조합은 호출당 토큰 풋프린트가 크다 (공유 쿼터 풀).
2. **풀-컨텍스트 재주입**. Pair-review 독립성 보장 규칙상 Claude 측 verdict 을 Codex 에 넘기지 않고 플랜·spec 전체를 독립적으로 재읽히도록 dispatch 했다. 매 attempt 마다 동일 대량 컨텍스트가 입력 측에서 재소비.
3. **누적 attempt**. 같은 날 세 번의 dispatch (각각 초기화 비용 포함) 가 quota 를 끝까지 소진. 3rd attempt 단계에서 "extra usage" 버킷은 이미 비어 있었으며, 사용자의 "충전됨" 인지가 실제 리셋 시점과 엇갈렸음.

### 4.3 해결책 (본 세션 적용)

CLAUDE.md 의 폴백 규칙 ("If only one side runs, flag the result as 'single-source, unverified'") 을 엄격히 따르되, 두 축을 추가 적용:

- **(A) 본 세션 내 Codex dispatch 포기** — 3:20am KST reset 까지 ~15 시간 대기는 사용자 속도 요구와 불일치 (explicit "너무 오래 걸리는데?").
- **(B) F1-F6 즉시 반영** — Claude reviewer 의 blocker 증거는 **mechanical / falsifiable** (grep 32 vs 0, javap 시그니처 덤프) 이므로, 사람/다른 모델 없이 나중에 재검증 가능.
- **(C) Codex 를 Task 11 최종 pair 로 이관** — 3:20am KST 이후 플랜이 아닌 **구현 diff 기반** (토큰 풋프린트 ↓) 으로 독립 리뷰. 플랜 단계에서 놓친 구조적 결함이 있어도 구현 수준에서 adjudicate 가능.
- **(D) 장기 개선 — scoped review packet 도입**. 다음 pair-review 설계 시 플랜 전체가 아닌 "rubric + 쟁점 목록 + 인용 근거" 만 담은 scoped packet 을 Codex 로 보내는 템플릿을 만든다 (토큰 ≈ 1/5 예상). 본 작업 자체는 별도 task 로 분리.

### 4.4 Follow-up 적용 상태 (2026-04-24)

| ID | 종류 | 플랜 반영 | Spec 반영 |
|---|---|---|---|
| F1 | BLOCKER | ✅ parser 헬퍼 (collectAttr / parseDeclareStyleable) + Bundle first-wins + 테스트 3건 | ✅ §2 item 10 + 제외 tag 재정의 |
| F2 | BLOCKER | ✅ createFakeDataDir 실 chain 반영 + Loader 테스트 parent chain 보강 | 해당 없음 (test fixture) |
| F3 | off-by-one | ✅ Task 9 Step 5 + §7.7.3 + Task 11 Step 3 모두 "6 PASS + 2 SKIPPED" | 해당 없음 |
| F4 | regression | ✅ rejectedStatuses 에 ERROR_RENDER/ERROR_UNKNOWN 추가 + 메시지 4행 | 해당 없음 |
| F5 | reversibility | ✅ Task 8 Step 1 rm → mv .w3-backup + Task 11 Step 4 cleanup | 해당 없음 |
| F6 | split | ✅ Task 9 Step 2 → 9a/9b 분할 + bisect 설명 | 해당 없음 |
| F7/F8 | nits | 방어 — 구현 중 발견 시 inline 수정 (별도 follow-up 불요) | — |

---

## 5. Bottom line

Plan 은 구조적으로 correct 했으며, F1 (prod-impact blocker) + F2 (test-reality drift) + F3-F6 (risk reducers) 모두 문서에 반영됨. **현 verdict**: GO_WITH_FOLLOWUPS → **applied** → **GO (single-source)**.

Codex reconciliation 은 Task 11 최종 pair 로 이관. 이관 사유와 재시도 cadence 는 §4 에 기록. 다음 세션이 이 파일 §4.4 를 먼저 확인하여 "어떤 F 가 적용되었나?" 를 verify 할 수 있도록 설계.

---

## 6. Post-hoc correction (2026-04-24 13:50 KST)

§4.1 의 Codex dispatch 실패 원인을 **"quota 소진" → "trust-check 프롬프트 행"** 으로 정정.

**증상**: `codex exec "<prompt>"` 가 출력 없이 멈춤 (90+ 분). 사용자 interrupt.

**실제 원인**: Codex CLI 는 작업 디렉토리가 (a) git repo 이거나 (b) `~/.codex/config.toml` 의 `[projects."..."].trust_level = "trusted"` 목록에 등재된 경우에만 자동 진행. 본 프로젝트는 non-git + 이 유저의 config 에 해당 경로 미등재 → `Not inside a trusted directory and --skip-git-repo-check was not specified.` 프롬프트가 stdin 에 걸려 영구 대기.

**증거**: `timeout 30 codex exec "say hello"` 재현 시 동일 메시지만 출력 후 hang.

**해결**: `codex exec --skip-git-repo-check "<prompt>"` 플래그 한 줄 추가. Codex 는 즉시 실행하여 W3D1 구현 pair-review 를 7-8 분 내 완료 (GPT-5.5 + xhigh, 4161 lines 출력, 컨버전스 Codex half = GO_WITH_FOLLOWUPS).

**교훈 (landmine L6)**: Codex rescue skill 이나 직접 호출 모두 **non-git 프로젝트에서는 `--skip-git-repo-check` 필수**. 이전 "quota 소진" 과 본 "trust-check 행" 은 증상이 비슷하지만 (Codex 가 응답하지 않음) 원인이 다르며, quota 메시지는 명확히 출력되는 반면 trust-check 는 silent hang. 다음 세션의 `codex:rescue` 호출 시 주의.

**결과**: Task 11 impl pair-review 는 본 세션 내 완료. 산출물 → `docs/W3D1-PAIR-REVIEW.md`. Codex verdict: **GO_WITH_FOLLOWUPS** — Claude verdict 과 FULL 컨버전스. MF1/MF2/MF3 적용 후 regression 유지 (99 unit + 8 integration PASS). §4.4 applied 상태 변경 없음.

---
END.
