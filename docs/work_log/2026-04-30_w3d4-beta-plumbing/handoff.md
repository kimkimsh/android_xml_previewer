# W3D4-γ phase entry — handoff

날짜: 2026-04-30 (생성), 다음 세션 entry: W3D4-γ
선행 head: `10ed4f3` (main)

---

## §1 어디까지 끝났나

W3D4-β plumbing (T11 + T12) 적용 완료, **두 production-pipeline gap 닫힘**:
- **Gap A — Material ThemeEnforcement sentinel attr**: RJarSymbolSeeder 가 `fromPackageName` → `RES_AUTO` 로 통일, AarResourceWalker 와 namespace 좌표계 정합. cross-class 동명 first-wins 를 ATTR-only → 모든 type 으로 일반화 + `Map<String, Int>` 강화 (동명+동ID silent / 동명+다른ID loud WARN).
- **Gap B — color XML state list feed**: AarResourceWalker 가 `res/color/<name>.xml` (default qualifier) 도 enumerate, `ParsedNsEntry.ColorStateList` → `LayoutlibResourceBundle.colorStateLists` → `MinimalLayoutlibCallback.getParser` → `SelectorXmlPullParser` (KXmlParser StringReader feed + `getLayoutNamespace`). 41 AAR 중 12 가 res 보유 / 191 color-state-lists 통합 확인.

acceptance gate (`activity_basic.xml` SUCCESS) 는 **여전히 fail** — 그러나 fail 위치가 Gap A/B 가 아닌 **새 layer** 로 shift. plan v3 §5.4 의 escalation 정책 정확히 적중.

## §2 새 fail surface — W3D4-γ-A

### §2.1 증상
```
[layoutlib.warning] resources.format | "vertical" in attribute "orientation" is not a valid integer
[layoutlib.warning] resources.format | "center_horizontal" in attribute "gravity" is not a valid integer
[layoutlib.warning] resources.format | "parent" in attribute "layout_constraintEnd_toEndOf" is not a valid integer
```

### §2.2 원인 (empirical, t13-acceptance-gate-followup.md §2.1)
- `ParsedNsEntry.AttrDef` 가 (name, namespace, sourcePackage) 만 보존 — `<attr>` 의 `<enum>/<flag>` 자식 무시.
- `NamespaceAwareValueParser` line 95-110 이 `<attr>` 자식을 `skipElement` 로 무시.
- `LayoutlibResourceBundle.buildBucket` 의 AttrDef 처리에서 `AttrResourceValueImpl.addValue(name, value, description)` API 미사용.
- 결과: layoutlib Bridge 가 framework R$attr.orientation 의 enum table 을 RenderResources 에서 lookup 시 빈 attr → "vertical" 변환 fail.

### §2.3 fix shape (W3D4-γ-A)
1. `ParsedNsEntry.AttrDef` 에 `enumValues: Map<String, Int>` + `flagValues: Map<String, Int>` 추가.
2. `NamespaceAwareValueParser` 의 `<attr>` 처리에 `<enum>/<flag>` 자식 child loop 추가.
3. `LayoutlibResourceBundle.buildBucket` 의 AttrDef 처리에서 `AttrResourceValueImpl.addValue(...)` 호출.
4. `<declare-styleable>` 안 nested `<attr>` 도 동일.
5. 단위테스트 + framework attrs.xml IT 검증.

### §2.4 fix shape (W3D4-γ-B, 예상)
ConstraintLayout 의 `parent` ID = `R$id.parent == 0`. RJarSymbolSeeder 가 R$id 처리 시 0 값 skip 하지 않는지 검증 (현재 모든 static int field 등록 — 0 도 등록되어야 정상). 만약 누락이면 fix.

## §3 다음 세션 진입 단계

### §3.1 cold-read 권장 docs (이 순서)
1. `docs/work_log/2026-04-30_w3d4-beta-plumbing/handoff.md` (본 파일)
2. `docs/work_log/2026-04-30_w3d4-beta-plumbing/t13-acceptance-gate-followup.md` (gap shift 분석 — W3D4-γ root cause)
3. `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (T11/T12 산출물 + landmines)
4. `docs/work_log/2026-04-30_w3d4-beta-plumbing/round3-pair-review.md` (round 3 verdict, Q6 open critique 정책)
5. `docs/superpowers/plans/2026-04-30-w3d4-beta-plumbing.md` (plan v3 — 본 phase 산출 plan)
6. `docs/superpowers/specs/2026-04-29-w3d4-material-fidelity-design.md` (spec round 2 — 자료구조 background)

### §3.2 본 세션 첫 작업 = W3D4-γ-A spec / plan 작성
1. baseline verify: `cd server && ./gradlew :layoutlib-worker:test --console=plain` → 215 unit / 0 fail. `./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain` → 14 IT PASS + 2 SKIP.
2. `<attr>` 자식 처리 empirical investigation (subagent dispatch):
   - framework `attrs.xml` 의 `<attr>` + `<enum>` + `<flag>` 패턴 census (몇 개 attr / 몇 개 enum / 몇 개 flag)
   - layoutlib Bridge 가 enum/flag table 을 어떻게 query 하는지 (javap on `BridgeContext.obtainStyledAttributes` / `ResourceHelper.parseValue` / `AttrResourceValueImpl.getAttributeValues()`)
   - `app:layout_constraintEnd_toEndOf="parent"` 의 `parent` 값 ID 확인 (R$id.parent 가 0x0 인지)
3. W3D4-γ design spec 또는 plan v3.1 작성.
4. round 4 Codex+Claude pair-review (planning-phase, MEMORY.md feedback_codex_sandbox_bypass 준수).
5. 구현 → commit + push → tier3-basic-primary @Disabled 제거 시도.

## §4 회피해야 할 LM (carry from W3D4-β)

| LM | 회피 방법 |
|---|---|
| LM-W3D4-β-D | KDoc 안 path 표기 시 `/*` 패턴 회피 (e.g. `res/color/*.xml` 대신 `res/color/<name>.xml` 또는 backtick 인용) |
| LM-W3D4-β-E | `assertNotNull(...)` 의 반환값 chain 사용 금지 → `val v = ...; assertNotNull(v); v!!.method()` 패턴 |
| LM-W3D4-β-F | IT 실행 시 반드시 `-PincludeTags=integration` 명시 |
| LM-W3D4-β-G | subagent dispatch 시 `unzip --directory /tmp/...` 명시 — cwd 오염 회피 |
| LM-W3D4-β-H | acceptance gate fail 시 stack trace 우선 검사 → fail surface 가 spec 한 layer 인지 새 layer 인지 분류 → 새 layer 면 escalate (plan §5.4 패턴) |

## §5 출발 지점 환경 sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
10ed4f3 feat(w3d4-beta): T13 partial — cache-invalidation + W3D4-γ-A escalation
4acb571 feat(w3d4-beta): T12 color state list walker + parser feed via callback
e83d75d feat(w3d4-beta): T11 RJarSymbolSeeder RES_AUTO + per-type id-aware first-wins
2f1909b docs(w3d4-beta): plan v3 plumbing + round 3 pair-review (GO)
6a7577f docs(w3d4): next-session-prompt — W3D4-β plan-revision phase entry

$ cd server && ./gradlew :layoutlib-worker:test --console=plain | tail -3
BUILD SUCCESSFUL — 215 unit / 0 fail.

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain | tail -3
BUILD SUCCESSFUL — 14 IT PASS + 2 SKIP (tier3-glyph W4 carry, tier3-basic-primary W3D4-γ carry).
```
