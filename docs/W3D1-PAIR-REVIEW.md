# W3D1 Implementation Pair Review (Claude + Codex)

**Date**: 2026-04-24
**Scope**: W3D1 구현 pair-review — `resources/` 서브패키지 7 production 파일 + tier3 canonical flip + session 배선.
**Pair policy**: CLAUDE.md "1:1 Claude+Codex Pairing for planning/review" — **FULL SET CONVERGENCE** (verdict / blockers). Follow-up set divergence minor.

---

## 1. Pair status

| Reviewer | Model | Access | Verdict | Status |
|---|---|---|---|---|
| Claude teammate | Explore subagent | full file read | GO_WITH_FOLLOWUPS | ✅ delivered |
| Codex rescue | **GPT-5.5 @ xhigh** | full file read + repo shell | GO_WITH_FOLLOWUPS | ✅ delivered |

**Convergence verdict**: 두 리뷰어 모두 **GO_WITH_FOLLOWUPS**, **No Blockers** — **full verdict convergence**. Codex 행 원인은 `--skip-git-repo-check` 플래그 누락 (non-git project + trusted-projects 목록 미등록) — 이전 "quota 소진" 과는 별개 문제. `~/.codex/config.toml` 수정 없이 플래그만으로 해결.

---

## 2. Q-by-Q convergence matrix

| Q | Claude | Codex | Convergence |
|---|---|---|---|
| Q1 subpackage 경계 | OK (단일 배선점 LayoutlibRenderer.kt:161-168) | OK (LayoutlibRenderer.kt:160-168, resources→session import 없음) | ✅ |
| Q2 CLAUDE.md 스타일 | OK | **ISSUE** — SessionParamsFactory.kt:34 `callback = MinimalLayoutlibCallback()` default + LayoutlibRenderer.kt:44-45 `fallback=null, fixtureRoot=...` default. CLAUDE.md "No default parameter values" 위반. | **DIVERGE — Codex 우세** (Claude missed concrete line numbers) |
| Q3 F1 first-wins dedupe | OK (라인 64-67 + KXmlParser sequential) | OK (FrameworkResourceBundle.kt:62-67, Parser 라인 49-55 / 113-126, Loader REQUIRED_FILES 순서 37-44) | ✅ |
| Q4 F2 fake parent-chain | OK (line 147 `Theme.Material.Light parent="Theme.Light"`) | OK + **NIT**: real `Theme.Material.Light.NoActionBar` 는 parent 명시 없이 dotted inference 에 의존하나 fake 는 명시 → 완전 mirror 는 아님 | ✅ w/ nit |
| Q5 rejectedStatuses 완전성 | **ISSUE (F1)**: Result.Status enum 전수 검사 권장 | **ISSUE (F2)**: `javap` 결과 8개 추가 상태 — `NOT_IMPLEMENTED, ERROR_TIMEOUT, ERROR_LOCK_INTERRUPTED, ERROR_VIEWGROUP_NO_CHILDREN, ERROR_ANIM_NOT_FOUND, ERROR_NOT_A_DRAWABLE, ERROR_REFLECTION, ERROR_RENDER_TASK`. 권장: `assertEquals(SUCCESS, status)` 로 단순화 | ✅ **CONVERGE** (Codex 가 구체적 enum 명시) |
| Q6 L4 production 영향 | OK (stateless PngRenderer) | OK + **new info**: MCP `chooseRenderer()` 가 mode 당 1번 생성 (mcp-server/.../Main.kt:56, 101, 111-124), **LayoutlibRendererIntegrationTest.kt:26 은 여전히 별도 renderer** → L4 masking 가능 | ✅ w/ Codex F4 |
| Q7 findResValue guard | OK + **F3 suggestion**: compile-time enforcement (final/sealed) | OK, **반대**: Kotlin class 이미 `final`, 금지 대상은 클래스 내부 edit 이므로 reflection guard 가 최소 + 충분 | **DIVERGE — Codex 우세** (technical correctness) |
| Q8 ConcurrentHashMap cache | OK (computeIfAbsent atomic) | OK (build() throws → 부분 캐시 없음) | ✅ |
| Q9 L5 coverage | OK (grep: 다른 KDoc 에 `/*` nested 없음) | OK (no remaining nested `/*`) | ✅ |
| Q10 CLI-friendly | OK (distDir 인자) | OK + **F3**: MCP discovery 가 `layoutlib-dist/android-34` 하드코딩 (Main.kt:111-116). `distDir.resolve("data")` 의 `"data"` 도 상수화 필요 | ✅ w/ Codex F3 |

**Strongest divergence**: Q2 — Claude 는 스타일 compliance OK 로 판정했으나 Codex 가 default parameter 2 곳을 구체적으로 지목. CLAUDE.md 정책 우선 → Codex 판정 채택, 수정 필요.

**Q7 divergence**: Claude 의 compile-time enforcement 제안이 잘못된 것은 아니지만, Codex 가 더 정확 (현 class 는 `final`, guard 는 override 방지 용도). Claude 제안 무효로 간주.

---

## 3. 병합된 Follow-up 결정

### 즉시 반영

- **MF1 (Codex F2 + Claude F1 converged)**: `LayoutlibRendererTier3MinimalTest.kt` 의 `tier3-arch` 함수에서 `expectedArchStatuses={SUCCESS}` 와 `rejectedStatuses` dual-check 를 `assertEquals(SUCCESS, status, ...)` 로 단순화. 동시에 `tier3-values` 의 `assertEquals(SUCCESS, result.status)` 는 이미 동일하므로 양쪽 일관성 확보. rejected 리스트 유지할 필요 없음 (SUCCESS 외 모든 상태 자동 거부).

- **MF2 (Codex Q2 = style violation)**: `SessionParamsFactory.kt:34` `callback = MinimalLayoutlibCallback()` default 제거 + 테스트 호출부 보정. `LayoutlibRenderer.kt:44-45` `fallback`/`fixtureRoot` default 제거는 **별도 carry** (기존 production 사용 중 — CLI/AxpServer 리팩토링 시 동반). 본 세션은 본 세션이 추가/수정한 default 만 제거.

- **MF3 (Codex N2)**: `LayoutlibRenderer.kt:163` 의 `"data"` 를 `ResourceLoaderConstants.DATA_DIR` 로 추출.

### Deferred (carry 로 이관)

- **Codex F3**: MCP `Main.kt` 의 `layoutlib-dist/android-34` 하드코딩 제거 + CLI 인자 지원. W3 Day 2+ 스코프.
- **Codex F4**: `LayoutlibRendererIntegrationTest.kt` 를 tier3 shared-renderer 패턴으로 전환. test-infra 일반화 carry.
- **Codex N1 + Claude nits**: 완전 optional cosmetic.

---

## 4. 최종 결정

**Verdict**: 두 리뷰어 모두 **GO_WITH_FOLLOWUPS** → MF1/MF2/MF3 반영 → **GO**.

**실행 순서**:
1. MF1 적용 → `./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration`
2. MF2 적용 → regression
3. MF3 적용 → regression
4. `.w3-backup` cleanup
5. work_log + 08 §7.7.3 업데이트 (pair 결과 반영)

---
END.
