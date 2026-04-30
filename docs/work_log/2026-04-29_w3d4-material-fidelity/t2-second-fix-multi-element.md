# W3D4 T2 Second Fix — Multi-Element Latent Bug

Date: 2026-04-30
Scope: `NamespaceAwareValueParser.parseInternal` outer-loop depth bug fix (post-`e171ef0` follow-up)

---

## Outcome

T2 의 `parseInternal` 이 outer `depth` counter 로 top-level element 를 식별했었으나,
`handleSimpleValue` / `handleStyle` / `handleDeclareStyleable` 가 자체 END_ELEMENT 까지 consume
하면서 outer loop 의 `depth--` 가 fire 되지 않아 **두 번째 sibling top-level element 부터
silently dropped** 되는 latent bug 가 존재. T3/T4 가 real Material3 AAR `values.xml` (수백
sibling) 을 만나면 첫 element 만 파싱되어 회귀.

해결: W3D1 `FrameworkValueParser` 의 state-machine 패턴 차용 — root `<resources>` scan + root
END_TAG match 로 종료. depth counter 제거. top-level `<attr>` 도 자식 (enum/flag) 에 대비해
`skipElement` 호출 추가.

## Hypothesis 검증 (TDD step)

`Case 8 — multi-element top-level` test 를 impl 수정 전 추가 후 실행 → fail 확인:

```
expected: <[d1, d2, c1, d3]> but was: <[d1]>
NamespaceAwareValueParserTest.kt:141
```

→ 8 sibling top-level element 중 첫 1 개만 entry 로 잡힘. depth-counter 가설 정확히 일치.

## Files modified

- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParser.kt`
  — `parseInternal` 본문 W3D1 state-machine 패턴으로 simplify (depth 제거 + root END_TAG match
    종료); top-level `TAG_ATTR` 분기에 `skipElement(reader)` 추가 (enum/flag 자식 대비);
    `TAG_RESOURCES` 상수 추가 (zero-magic-strings); class-level KDoc 에 second-fix 사유 명시.

- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParserTest.kt`
  — Case 8 `multi-element top-level (real Material3 패턴) 모두 파싱` test 추가 (4 simple + 2
    style + 2 attr = 8 entries 검증).

## Test results

- `NamespaceAwareValueParserTest`: **8 PASS** (Case 1–7 기존 + Case 8 신규 모두 통과).
- `FrameworkValueParserTest`: **11 PASS** (회귀 없음).
- 전체 `server` test suite: **180 tests, 0 failures, 0 errors** (BUILD SUCCESSFUL).

## Landmines discovered

1. **depth counter ↔ XMLStreamReader cursor 진행 mismatch**: `reader.elementText` 같은 helper
   는 START_ELEMENT 위치에서 호출되어 매칭 END_ELEMENT 까지 cursor 를 진행 + 그 END_ELEMENT 도
   consume 한다. outer loop 가 다음 `reader.next()` 시 sibling START_ELEMENT 를 받으므로 자기
   END_ELEMENT 는 NEVER 본 것 → `depth--` skip → 다음 START_ELEMENT 시 depth==3 → top-level
   dispatch fail. **depth-tracking 은 single-loop StAX 에서 fragile**, state-machine 패턴이 안전.

2. **Test 가설 검증 (TDD)**: impl 수정 전 신규 test 만 먼저 추가하여 fail-shape 확인 → 가설
   (depth bug → 첫 element 만 잡힘) 정확히 검증됨. Case 1–7 이 모두 single top-level element
   였기에 기존 테스트가 bug 를 못 잡았던 것을 명시적으로 노출.

3. **top-level `<attr>` 자식 처리**: 기존 코드는 `seenAttrNames.add` 만 하고 자식 skip 없음 →
   `<attr name="X"><enum.../></attr>` 같은 top-level (드물지만 가능) 를 만나면 outer 가 자식
   START 를 다시 처리하려고 시도. `skipElement(reader)` 추가로 방어.

## Carried forward to next session

없음 — T2 second-fix 자체는 완료. plan v1 (T1–T10) 의 T3 (Material3 AAR ValuesLoader 통합)
부터 진행 가능. 본 fix 가 T3/T4 의 real Material3 AAR 회귀 가능성을 사전 차단.

## Pair review verdict

이번 fix 는 implementation-phase work — Codex pair 미적용 (CLAUDE.md 의 "1:1 Claude+Codex
Pairing — Planning & Plan-Review ONLY" 정책에 따라 implementation 단계 코드 fix 는 Claude-only).
직전 round 2 pair review (`round2-pair-review.md`) 의 GO 결정 + REVISE_REQUIRED 후속 항목으로
Claude 단독 진행.

## Commit

`fix(w3d4): T2 multi-element latent bug — depth-tracking 제거 + W3D1 state-machine 패턴`
