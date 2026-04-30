# W3D4-γ Round 4 Pair Review (Codex + Claude)

날짜: 2026-04-30 (생성), 2026-05-01 (Codex 완료 — xhigh effort 13:33 elapsed)
대상: `docs/superpowers/plans/2026-04-30-w3d4-gamma-attr-enum-flag.md` (plan v3.1 draft)
phase: planning / plan-revision (Codex pairing 정책 trigger 정확히 적용 — pre-implementation, attr enum/flag capture 의 자료구조 + Bridge.init wiring)
LM-G 회피: `codex exec --skip-git-repo-check --sandbox danger-full-access` (MEMORY.md feedback 준수)

---

## §1 디스패치 채널

| 채널 | 모델 / 모드 | 산출물 |
|---|---|---|
| Codex CLI (직접) | latest GPT @ xhigh effort, sandbox bypass | `/tmp/codex-round4-review.txt` (68 lines) + `/tmp/codex-round4-stdout.txt` (~5060 lines, 다수 javap/zipgrep) |
| Claude subagent (Explore agent) | claude internal | inline 결과 (~1500 words) |

각 채널은 서로의 산출물을 보지 못한 상태에서 동일 Q1-Q5 + Q6 open critique 에 답함 — convergence 자체가 신뢰 신호.

## §2 Verdict

| 채널 | 판정 | confidence |
|---|---|---|
| Codex | **REVISE_REQUIRED** | 0.93 |
| Claude | GO_WITH_FIXES | 0.92 |

**Verdict 결정**: Codex 의 REVISE_REQUIRED 가 **set divergence** (Q5 DISAGREE — Claude CORRECT). 단 Codex 가 file:line 으로 검증 가능한 empirical 증거 (`LayoutlibResourceBundle.kt:42-43` byType-only + `attrsMut` 별도 map, NsBucket.kt:17-21 분리) 제시 → 직접 코드 read 로 verify 후 **judge round 불요**, Codex 우선.

**최종 reconciled verdict: GO** (7 plan delta + 0 escalation note 적용 후 plan v3.1 inline 갱신 완료).

## §3 Convergence map

| Q | Codex | Claude | 종합 |
|---|---|---|---|
| Q1 (2-path branch) | NUANCED — BridgeXmlPullAttributes sibling 추가 (offsets 75-85, 104-116, 515-529) | CORRECT | Codex 추가 사실 inline (§1.1) |
| Q2 (Integer.decode) | DISAGREE — `<flag value="0x80000000"/>` (attrs.xml:1703) + `value="0xffffffff"` (:4078, :4101) → NumberFormatException | CORRECT (census 평균만, edge case 미검사) | **Codex empirical 우선** — `Long.decode(...).toInt()` 로 교체 |
| Q3 (first-wins) | NUANCED — fixture scan 213 dup / 56 conflict / 0 nonempty conflict | NUANCED — 안전 판정 | 결론 동일, Codex 측정값 inline |
| Q4 (initBridge cost) | CORRECT — measured 89ms (test result XML:24) | ACCEPTABLE — ~150ms estimate | Codex 측정 우선 |
| **Q5 (getResolvedResource ATTR)** | **DISAGREE — KILL POINT** — `LayoutlibResourceBundle.kt:42-43` 가 `byType` 만 조회, `attrsMut` 별도. ATTR ref → null. T14 의 addValue 만으로는 미충분 | CORRECT — instanceof cast 통과 (byType-attrs 분리 미발견) | **Codex 우선** — `getResource` 에 ATTR special-case 추가 (§3.1 변경 1 신규) |
| Q6 (open critique) | LayoutlibResourceBundleTest.kt:79-80 명시 + γ-C defer OK + Kotlin boxing OK + LM 준수 | 동일 결론 | convergence ✓ |

## §4 Adopted plan deltas (7)

모두 plan v3.1 inline 적용 완료 (§11.2 참조).

1. **`parseAttrValueLiteral` → `Long.decode(...).toInt()`** (Q2). framework attrs.xml 의 `0x80000000` (Int.MIN_VALUE), `0xffffffff` (-1) cover. 추가 단위 테스트 2개 (§3.2 — `flag value 0x80000000`, `negative decimal value`).

2. **`LayoutlibResourceBundle.getResource` 에 ATTR special-case 추가** (Q5 KILL POINT). `bucket.attrs[ref.name]` 도 조회 → BridgeTypedArray + BridgeXmlPullAttributes 양쪽 path 가 AttrResourceValue 도달. 변경 1 신규 (§3.1 LayoutlibResourceBundle.kt 부 첫 변경).

3. **`LayoutlibRenderResourcesAttrLookupTest.kt`** 신규 (Q5 acceptance-critical, §3.2). `getResolvedResource` + `getUnresolvedResource` ATTR ref 둘 다 AttrResourceValue 반환 검증.

4. **§1.1 BridgeXmlPullAttributes sibling 명시** (Q1 NUANCED). project supplier 가 `getUnresolvedResource` 사용 — Q5 fix 가 양쪽 entry point cover 의무.

5. **§3.1 first-wins empirical 정정** (Q3 NUANCED). 213 dup / 56 empty-vs-nonempty / 0 nonempty conflict 수치 inline + appcompat/material/constraintlayout 실 사례 인용.

6. **§3.2 LayoutlibResourceBundleTest.kt:79-80 명시** (Q6 reorder safety). 5-arg ctor 갱신 명시 — empty enum/flag map 으로 dedup test 의미 보존.

7. **§4.1 measured cold-start cost 89ms inline** (Q4). 추정값 → 측정값 (`framework=39ms app=1ms aar=36ms build=13ms total=89ms`).

## §5 Escalation notes (0)

본 round 의 7 deltas 모두 plan inline 처리 — escalation 없음 (round 3 의 T12.5 / Q6.5 같은 후속 phase 전망 부재).

T16 의 `tier3-basic-primary` 가 여전히 fail 시 escalation 정책 (§5.3) 은 plan v3.1 자체에 포함:
- Hypothesis γ-C: R$styleable path (Bridge.parseStyleable 위임 가정 — RJarSymbolSeeder.kt:64-66 skip).
- Hypothesis γ-D: AAR `<style parent="@android:style/X">` cross-NS chain miss.

## §6 Divergence + reconcile rationale

가장 큰 divergence = Q5 (Codex DISAGREE, Claude CORRECT).
- Codex 의 reasoning: bytecode `BridgeTypedArray.resolveEnumAttribute` (offset 51-69) → `RenderResources.getResolvedResource` → `bundle.getResource` → byType-only → ATTR null. AttrDef 가 `attrsMut` 에만 등록되는 것은 NsBucket.kt:17-21 의 데이터 구조에서 직접 검증.
- Claude 의 reasoning: `getResolvedResource` 의 chain walker 가 unresolved 를 보존 (line 155 `current.value == null` 시 current 반환) — AttrResourceValue 가 `value=null` 이면 chain 안 돌아 그대로 반환되어 instanceof cast 통과. Claude 는 chain walker 의 short-circuit 만 보고 bundle 의 lookup path 를 검증 안 함.
- **검증 (필자 직접 read)**: `LayoutlibResourceBundle.kt:42-43` (`byNs[ref.namespace]?.byType?.get(ref.resourceType)?.get(ref.name)`) + AttrDef branch (`:105-113` 의 `attrsMut` 등록만) 모두 plan에 인용된 그대로 — Codex 검증 fact 일치. byType[ATTR] 는 비어있어 첫 단계에서 null. chain walker 의 short-circuit 도달 못 함.

→ Codex 의 진단 정확. Claude 의 confidence 0.92 가 false-positive (확신 강함, 검증 부족). Codex 의 0.93 이 evidence-based.

`AttrSeederGuard` 같은 ATTR-only 가드를 통해 Claude 가 attrsMut 의 존재를 인지했다면 Q5 catch 가능했을 것 — round 5 같은 future review 시 reviewer 에 "bundle 의 lookup path 도 trace 하라" 명시.

## §7 LM 적용

- LM-G: codex exec --skip-git-repo-check --sandbox danger-full-access 직접 CLI ✓
- LM-α-A: Codex 모든 claim file:line 인용 (40+ citation), Claude 도 file:line 사용 ✓
- LM-α-B: dual-channel verdict, single-source 회피 ✓
- LM-W3D4-D: plan 의 모든 fix 가 explicit diff (round 4 의 변경도 동일) ✓
- LM-W3D4-β-D~H: review reviewer 가 명시 검증 (Codex Q6 inline)

## §8 다음 단계

T14 → T15 → T16 순으로 subagent-driven implementation. 각 task 단위 commit + push (CLAUDE.md task-unit completion). 각 단계 완료 후 모듈 합산 test 재측정.

특히 T14 commit 은 round 4 의 critical Q5 fix (LayoutlibResourceBundle.getResource ATTR special-case) 를 첫 실 테스트 — 회귀 가드 `LayoutlibRenderResourcesAttrLookupTest` 가 green 인지 우선 검증.

## §9 산출물

- `/tmp/codex-round4-review.txt` (68 lines, structured Q1-Q6 + 7 deltas)
- `/tmp/codex-round4-stdout.txt` (~5060 lines, javap/zipgrep raw evidence — 보존 안 함, 본 review 의 §3 convergence map 으로 요약)
- 본 doc + plan v3.1 의 §11 (round 4 reconcile 요약 — plan 본문 inline)
