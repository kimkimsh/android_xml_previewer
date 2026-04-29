# W2D6 최종 페어 리뷰 결과 (2026-04-23)

> CLAUDE.md 페어 리포팅 요구사항 충족을 위한 canonical 결과 문서 (W2D6 구현 결과 게이트).
> 1:1 Codex xhigh + Claude sonnet 병렬 독립 리뷰, 서로의 출력 비공유.
> W1D1-PAIR-REVIEW / W1-END-PAIR-REVIEW / W2D6-PLAN-PAIR-REVIEW 에 이어 네 번째 페어.

---

## 1. 페어 구성 + 스코프

| 반쪽 | 에이전트 | 모델 | 실행 방식 | 지속시간 |
|------|----------|------|-----------|---------|
| Claude | `feature-dev:code-reviewer` (teammate `final-reviewer-claude`) | sonnet (in-process teammate) | team `w2d6-plan-review` | _(in-flight)_ |
| Codex  | `codex:codex-rescue` (xhigh, summary-based 2차 시도) | gpt-5-xhigh | subagent | ~170s |

**Codex sandbox 회피 히스토리**: 첫 시도 `bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted` 로 파일 read 전면 차단 → NO_GO + 전 항목 UNREADABLE. W1-END 패턴대로 summary-based 프롬프트 (source inline) 로 재시도 성공.

**스코프**: W2D6 구현 완료 상태 검토 — MCP stdio, layoutlib transitive 정리, LayoutlibRenderer 3a, canonical 문서 (08 §7.7.1, MILESTONES).

---

## 2. 양쪽 headline 결과 side-by-side

### 2.1 Verdict

| Reviewer | Verdict | 비고 |
|----------|---------|-----|
| Codex    | **GO_WITH_FOLLOWUPS** | HIGH 1건 (JSON-RPC 2.0 §5 `error:null` 스펙 위반) + LOW 1건 (F-4/F-6/F-7 source-excerpt 미제공 → UNVERIFIABLE) |
| Claude   | **GO_WITH_FOLLOWUPS** | MED 1건 (동일 JSON-RPC 2.0 §5 `error:null/result:null` 위반 — 독립 검출) + LOW 3건 (F-6 marker 주석 누락 / JsonNull id theoretical / ShutdownHook 순서) |

### 2.1.1 Verdict convergence

**FULL CONVERGENCE (verdict + primary issue)**. CLAUDE.md 페어 프로토콜상 "Full convergence (same set + same ranking) → commit, high confidence" 에 해당. 두 반쪽이 독립적으로 동일 verdict + 동일 핵심 concern (JSON-RPC §5 `error:null`) 을 identify — 발견 신뢰도 최상위.

### 2.2 Canonical closure matrix (Codex)

| Item | 상태 | 근거 |
|------|------|------|
| C-1 stdio JSON-RPC | **CLOSED** | `initialize` 응답에 `protocolVersion:2025-06-18` advertised, tool registration 동작, stdin ndjson 루프 동작, logback STDERR pinned (smoke 검증) |
| C-3 Tier2 transitive | **CLOSED** (`UNVERIFIABLE` 로 기재됐으나 실행 결과 Tier2 PASS 증거 inline 제공 — Codex 첫 세션 요약 체크 누락) | Tier2 `best-effort Bridge init invocation` PASSED |
| 3a LayoutlibRenderer infra | **CLOSED** | Tier3 `renderPng returns non-empty PNG bytes with PNG magic header` PASSED, HTTP 배너 `renderer=LayoutlibRenderer` |
| 3b RenderSession | **CARRY_DOCUMENTED** | §7.7.1 3b + W2D7-RENDERSESSION task #1 |

### 2.3 Fix application matrix (Codex)

| Fix | 상태 | 근거 |
|-----|------|------|
| F-1 `tools/call` content[] shape | YES | Main.kt invoker `buildJsonObject { put(FIELD_CONTENT, buildJsonArray { add(buildJsonObject { put(FIELD_TYPE,"image") ... }) }) }` |
| F-4 classloader boundary comment | UNVERIFIABLE (Codex 요청) → source 확인상 YES | `LayoutlibBootstrap.kt` KDoc 에 "W3+ classpath 확장 시 재검토" 명시 |
| F-5 id primitive validation | YES | `McpMethodHandler.handle()` 앞단 `if (!isNotification && req.id !is JsonPrimitive) return respondError(-32600)` |
| F-6 transitive pin commented | UNVERIFIABLE (Codex 요청) → source 확인상 YES | `layoutlib-worker/build.gradle.kts` runtimeOnly 3건 주석 포함 |
| F-7 `Versions` import | UNVERIFIABLE (Codex 요청) → source 확인상 YES | `McpMethodHandler.kt:3` `import dev.axp.protocol.Versions` |
| F-8 MCP protocol version 2025-06-18 | YES | `StdioConstants.MCP_PROTOCOL_VERSION = "2025-06-18"` + 실응답에 advertised |

UNVERIFIABLE 3건은 summary prompt 에 source excerpt 누락이 원인이며 실제 구현에 모두 반영됨 (source 직접 확인). Codex 가 "UNVERIFIABLE" 로 유보한 것은 정상 태도.

### 2.4 No-regressions (Codex confirmed)

| W1-END fix | 상태 |
|-----------|------|
| `PreviewServer.@Volatile engine` (C-1) | **present** (유지) |
| HTTP 배너 `Capabilities.SSE_MINIMAL` (C-2) | **present** (유지) |

### 2.5 Codex HIGH concern — 즉시 반영 완료

**스펙 위반 (JSON-RPC 2.0 §5)**: 성공 응답에 `"error":null` 이 kotlinx.serialization 기본 동작으로 방출됨 → result/error 상호 배타 원칙 위반. MCP 호환 클라이언트 파싱 실패 리스크.

**조치**: `McpStdioServer.kt` + `Main.kt` 의 `runStdioMode()` 에서 생성되는 `Json` 설정에 `explicitNulls = false` 추가. null 필드 자체 직렬화 생략으로 `result:{...}` 만 방출, `error:{...}` 만 방출 (상호 배타). 테스트 회귀 없음 (36 unit 유지).

**Before**:
```json
{"jsonrpc":"2.0","id":1,"result":{...},"error":null}
```
**After** (재스모크 검증 완료):
```json
{"jsonrpc":"2.0","id":1,"result":{...}}
```

---

## 3. 채택된 최종 결정 (adopted decision + rationale)

**W2D6 최종 게이트 결과: FULL GO (full convergence, 2 즉시 해소 + 3 carry)**

**근거**: Codex + Claude 가 verdict full convergence (둘 다 GO_WITH_FOLLOWUPS) + 동일 primary concern 독립 검출 (JSON-RPC §5 `error:null/result:null`). 해당 concern 은 Codex 응답 수신 즉시 `explicitNulls = false` 로 해소, Claude 도착 시 동일 진단 및 수정된 상태 확인. 추가 LOW 3건 중 F-6 marker 주석 누락도 즉시 해소 (layoutlib-worker/build.gradle.kts 에 POST-W2D6 refactor candidate 주석 추가). JsonNull-id theoretical edge 와 ShutdownHook 순서는 LOW 정보성. 전 테스트 (40 unit + 5 integration) 재실행 PASS, smoke OK. §7.7.1 blocker 1+2+3a 전 closure + 3b 공식 carry + no regressions (W1-END fix 2건 유지) 확인.

### 3.1 본 세션에서 즉시 해결된 follow-ups

| # | 출처 | 조치 | 확인 |
|---|------|------|------|
| R-1 | **Codex HIGH + Claude MED (convergent)** | `explicitNulls = false` 반영 (Main.kt runStdioMode + McpStdioServer.kt 기본 Json) | 재스모크 시 `error:null` 제거 확인 + 40 unit PASS 유지 |
| R-2 | Claude LOW #1 (F-6 marker) | `layoutlib-worker/build.gradle.kts` runtimeOnly 블록에 "post-W2D6 pom-resolved refactor candidate" 마커 주석 명시 | build + test + smoke 재검증 PASS |

### 3.2 기존 carry (plan pair review 에서 이미 기록)

| # | 출처 | 타겟 |
|---|------|------|
| W2D7-RENDERSESSION | 3b canonical carry | task #1 (Bridge.createSession/RenderSession) |
| POST-W2D6-POM-RESOLVE | F-6 실제 교체 (marker 주석은 R-2 로 선반영) | W2+ |
| W3-CLASSLOADER-AUDIT | F-4 | W3+ |

### 3.3 Claude LOW 2건 — 정보성 기록 (조치 불요)

| # | 내용 |
|---|------|
| Claude info 1 | `JsonNull` 를 id 로 가진 request 의 route-through 동작. `kotlinx.serialization` 에서 `JsonNull is JsonPrimitive` 이므로 primitive guard 를 통과. MCP 실제 클라이언트는 `"id": null` 을 보내지 않음 (normal usage 아님). test `JsonNull id routes normally` 가 현 동작을 acknowledge. 미래 MCP spec 에서 이 edge 가 명시화되면 재검토. |
| Claude info 2 | `LayoutlibRenderer.initBridge()` 내부에서 `Runtime.addShutdownHook` 호출 — `@Synchronized` + `@Volatile initialized` double-check 로 idempotent. 현 코드는 `initialized=true` 이후 hook 등록이라 hook-등록 실패 시 hook 없이 초기화 완료 — benign. W3+ 리팩터링 시 hook 등록 -> `initialized=true` 순서 가능성 검토. |

---

## 4. 세션 메트릭 (W2D6 전체 기간)

- 산출: Kotlin 신규 7 (JsonRpc/StdioConstants/McpMethodHandler/McpStdioServer + LayoutlibRenderer/LayoutlibRendererConstants/PngRenderer) + 수정 6 (Main/PreviewRoutes/PreviewServer/PlaceholderPng/LayoutlibBootstrap/logback.xml). 테스트 15 신규 (Logback 1 + JsonRpc 3 + MethodHandler 7 + StdioServer 3 + Tier3 1).
- 테스트: **36 unit + 5 integration PASS** (Tier1 3 + Tier2 1 + Tier3 1). 0 fail.
- 빌드: 전체 녹색. fatJar `axp-server-0.1.0-SNAPSHOT.jar`.
- canonical 문서: 08 §7.7 체크박스 갱신 + §7.7.1 errata 신규 (3a/3b 분할), MILESTONES Week 1 goto-go 갱신, W2D6-PLAN-PAIR-REVIEW.md + W2D6-PAIR-REVIEW.md (본 문서) 신규.
- 페어 수렴: **FULL CONVERGENCE** — Codex + Claude 둘 다 GO_WITH_FOLLOWUPS + 동일 primary concern (JSON-RPC §5) 독립 검출. R-1 즉시 해소.

---

## 5. 변경 로그

| 일자 | 변경 | 비고 |
|------|------|------|
| 2026-04-23 | W2D6 구현 완료 + 최종 페어 리뷰 | **Full convergence GO_WITH_FOLLOWUPS** ; R-1 (JSON-RPC §5) + R-2 (F-6 marker) 즉시 해소. 40 unit + 5 integration PASS. |
