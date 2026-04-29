# W2D6 플랜 페어 리뷰 결과 (2026-04-23)

> CLAUDE.md 페어 리포팅 요구사항 충족을 위한 canonical 결과 문서 (플랜 리뷰 단계).
> 1:1 Codex xhigh + Claude sonnet 병렬 독립 리뷰, 서로의 출력 비공유.
> W1D1-PAIR-REVIEW / W1-END-PAIR-REVIEW 에 이어 세 번째 페어.

---

## 1. 페어 구성 + 스코프

| 반쪽 | 에이전트 | 모델 | 실행 방식 | 지속시간 |
|------|----------|------|-----------|---------|
| Claude | `feature-dev:code-reviewer` (teammate `plan-reviewer-claude`) | sonnet (in-process teammate) | team `w2d6-plan-review` | ~2분 |
| Codex  | `codex:codex-rescue` (xhigh) | gpt-5-xhigh | subagent (bwrap sandbox 통과) | ~10분 |

**스코프**: `docs/superpowers/plans/2026-04-23-w2d6-stdio-and-fatjar.md` (7 task, 약 1100 line 의 구현 플랜). §7.7 의 3 blocking acceptance item 에 대한 플랜 정합성 판정.

**리뷰 축**: (1) canonical coverage, (2) correctness, (3) MCP protocol, (4) classloader strategy, (5) gradle transitive declaration, (6) task granularity / repo rules, (7) scope drift / risks.

---

## 2. 양쪽 headline 결과 side-by-side

### 2.1 Verdict

| Reviewer | Verdict |
|----------|---------|
| Claude   | **GO_WITH_FOLLOWUPS** |
| Codex    | **NO_GO** |

**수렴 판정**: verdict **DIVERGENT** (2-tier). 단, issue set 은 상당 부분 converge.

### 2.2 Issue set overlap

| Issue | Codex | Claude | 합의 |
|-------|:-----:|:------:|:---:|
| MCP `tools/call` 응답 shape 비준수 (`{mimeType, dataBase64}` → `content[]` of typed blocks) | ✅ | ✅ | **convergent FAIL** |
| `createIsolatedClassLoader()` parent 를 `getSystemClassLoader()` 로 넓히는 전략의 격리 경계 침식 위험 | ✅ | ✅ | **convergent CONCERN** |
| `PngRenderer` interface 배치: `:http-server` 가 아닌 `:protocol` 로 이동 (plan 의 option (b)) | ✅ | ✅ | **convergent FIX** |
| Magic number / 매직 스트링 추출 (720, 1280, "image/png") | ✅ | ✅ | **convergent CONCERN** |
| Task 5 `renderViaLayoutlib` 가 Bridge.init 성공 후 BufferedImage 만 그려 canonical "실제 layoutlib 렌더" 미충족 | ✅ | ❌ | **Codex 고유** |
| `MCP_PROTOCOL_VERSION = "2025-06-18"` 값의 존재성 / 협상 호환성 | ❌ (Codex citation 이 URL 확인) | ✅ (knowledge cutoff 기반 "존재 안 함" 주장) | **Claude out-of-date** |
| Guava/kxml2/ICU4J 버전을 임의로 pin — layoutlib 14.0.11 pom 기반 resolve 미수행 | ✅ | ❌ | **Codex 고유** |
| JsonRpc id 검증 (null/object/array 거부) | ✅ | ❌ | **Codex 고유** |
| `Versions` import 누락 (Task 2 Step 7 snippet) | ❌ | ✅ | **Claude 고유** |

Convergent 4건 + Codex 고유 3건 + Claude 고유 1건 + Claude out-of-date 1건.

### 2.3 분기의 구조적 원인 분석

- **Verdict divergence**: Codex 는 strict canonical reading (§7.7 item 3 "실제 layoutlib 렌더") 을 FAIL trigger 로 해석. Claude 는 W1-END pattern (user 의 scenario B 승인) 을 점진 수용 frame 으로 해석하여 GO_WITH_FOLLOWUPS 로 완화. 두 프레임 모두 내부적으로 정합.
- **MCP version string**: Claude 의 knowledge cutoff 가 `2025-06-18` 이전이라 "존재 안 함" 결론. Codex xhigh 는 spec URL (`https://modelcontextprotocol.io/specification/2025-06-18/...`) 을 citation 으로 제시 — 실제 revision 존재. **최신 knowledge 채택: Codex verdict 유효**, `2025-06-18` 유지.
- **renderViaLayoutlib 해석**: canonical 문자를 엄격히 읽으면 Codex 옳음. 단 user 가 W1-END 에서 "placeholder PNG 먼저, L1 fatJar 해결 후 교체" 점진 경로를 승인한 점이 판단 기반. 본 분기는 아래 §3 에서 3a/3b 분할로 해소.

---

## 3. 채택된 최종 결정 (adopted decision + rationale)

**W2D6 플랜 게이트 결과: GO_WITH_FOLLOWUPS_WITH_3A_3B_SPLIT (8 mandatory pre-execution fixes)**

**근거**: Claude 의 GO_WITH_FOLLOWUPS 를 base 로 채택하되 Codex 의 FAIL 근거 중 convergent 항목은 플랜 실행 전 전량 반영, divergent 항목 (C-2 renderViaLayoutlib) 은 user scope 궤적 (§7.6 placeholder escape) 을 상속해 3a (infra+init, W2D6) / 3b (RenderSession, W2D7) 로 분할. 이로써 Codex NO_GO 는 "플랜 수용 불가" 가 아닌 "플랜 + 3b carry" 로 정합.

### 3.1 Pre-execution 필수 fix (플랜 실행 전 반영)

| # | 근거 | 조치 |
|---|------|------|
| F-1 | Codex+Claude convergent | MCP `tools/call` 응답을 `result.content = [{type:"image", data:"<b64>", mimeType:"image/png"}]` 로. plan Task 5 Step 8. |
| F-2 | Codex+Claude convergent | `PngRenderer` 를 `:protocol` 에 배치 (plan option (b)). `:layoutlib-worker` 는 `:http-server` 에 의존하지 않음 — 이미 teammate 가 Task #2 구현 중 반영. |
| F-3 | Codex+Claude convergent | 720/1280/"image/png"/method 이름 등 magic 상수 → 전용 Constants object. 이미 teammate 가 `LayoutlibRendererConstants` 로 반영. |
| F-4 | Codex+Claude convergent | Classloader 확대는 W2D6 blocker 해소 목적 한정. 주석에 W3+ 재검토 경계 명시 필수. 이미 teammate 가 comment 반영. |
| F-5 | Codex 고유, valid | `JsonRpcRequest.id` 는 primitive (number | string) 만 허용; object/array/null(with method ≠ notification) 거부 + JSON-RPC `-32600 Invalid Request` 반환. |
| F-6 | Codex 고유, valid | Guava/kxml2/ICU4J runtimeOnly 버전은 layoutlib 14.0.11 pom 기반 resolve 가 canonical 이지만 W2D6 blocker 해소 범위로는 pin 허용. `build.gradle.kts` 주석에 "post-W2D6 pom-resolved refactor candidate" 를 명시. |
| F-7 | Claude 고유, valid | Task 2 Step 7 snippet 의 `import dev.axp.protocol.Versions` 누락 — 실제 구현 시 확보. |
| F-8 | Codex citation | MCP protocol version `2025-06-18` 유지 — revision 실존 확인 (Codex URL). Claude 의 "존재 안 함" 주장은 knowledge cutoff 한계. |

### 3.2 §7.7 item 3 의 3a / 3b 분할 (Codex C-2 수용 방식)

**§7.7.1 erratum** (`docs/plan/08-integration-reconciliation.md` 에 추가 필요):

> §7.7 item 3 "activity_basic.xml 한 개 이상 실제 layoutlib 렌더 + 브라우저 PNG 로 전달" 을 두 단계로 세분화:
> - **3a (W2D6 closure)**: `LayoutlibRenderer` 가 `Bridge.init` 실제 성공 경로를 증명 (Tier2+Tier3 PASS). `PngRenderer` interface 를 `:protocol` 로 분리, `PreviewRoutes` 가 interface 로 교체, dist 부재 시 graceful fallback. 이 단계는 PNG pixel 이 아직 layoutlib 의 `RenderSession` 이 아닌 `BufferedImage` 에서 유래함을 canonical 로 인정.
> - **3b (W2D7 carry)**: `Bridge.createSession(SessionParams)` → `RenderSession.render(timeout)` → `session.image` 를 PNG 로 인코딩. `ResourceResolver` + `LayoutlibCallback` + `HardwareConfig` 최소 infra. 이 단계 완료 시 `PreviewRoutes` 의 PNG 가 실제 layoutlib 렌더 pixel.

**근거**: user 가 W1-END 에서 `scenario B` (placeholder 우선, 교체 점진) 를 승인한 점을 상속. `RenderSession` 은 `SessionParams`+`ResourceResolver`+`LayoutlibCallback` 의 비자명한 infra 를 동반해 W2D6 단일 blocker 내 수용 시 오히려 risk 증가. 3b 를 W2D7 로 분리하면 W2D6 은 깨끗이 닫히고, 3b 는 독립 TDD cycle 가능.

---

## 4. 후속 task (이 플랜 실행과 병행/후속)

| ID | 상태 | 제목 | 근거 |
|----|------|------|------|
| #1 (W2D6-STDIO) | pending | MCP stdio JSON-RPC (convergent F-1/F-5/F-7/F-8 반영) | §7.7 item 1 |
| #2 (W2D6-FATJAR) | **completed** | layoutlib transitive + 3a 인프라 (teammate 자율 완료) | §7.7 item 2 + 3a |
| #2b (W2D7-RENDERSESSION) | **신규, pending** | 3b 실 RenderSession 호출 경로 | §7.7.1 3b |
| #3 (W2D6-PAIR-REVIEW) | blocked by #1 | 실행 결과에 대한 최종 Codex+Claude 페어 | CLAUDE.md 페어 프로토콜 |
| #4 (W3-CLASSLOADER-AUDIT) | **신규, pending** | parent=systemClassLoader widening 의 W3+ 재검토 | F-4 |
| #5 (POST-W2D6-POM-RESOLVE) | **신규, pending** | layoutlib-14.0.11 pom-based transitive resolve | F-6 |

---

## 5. 세션 메트릭

- 플랜 LOC: ~1100 line, 7 task.
- 리뷰 duration: Claude ~2분 / Codex ~10분 (xhigh).
- Issue 발견: 4 convergent + 3 Codex-unique + 1 Claude-unique + 1 Claude-out-of-date = 9건.
- 최종 결정: convergent 4 fix 반영, divergent 1건 (C-2) 은 3a/3b 분할, Codex-unique 3건 반영 (F-5/F-6/F-8), Claude-unique 1건 반영 (F-7).

---

## 6. 변경 로그

| 일자 | 변경 | 비고 |
|------|------|------|
| 2026-04-23 | W2D6 플랜 페어 리뷰 결과 문서화 | verdict divergent (NO_GO ↔ GO_WITH_FOLLOWUPS), 8 pre-execution fix + 3a/3b 분할로 해소 |
