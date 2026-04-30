# T13 acceptance-gate followup — gap shift 발견 + W3D4-γ-A escalation

날짜: 2026-04-30
선행 commit: T11 (`e83d75d`), T12 (`4acb571`)
현재 head: T13 cache-invalidation only (primary IT 는 새 진단으로 re-`@Disabled`)

---

## §1 측정된 사실

### §1.1 Gap A (T11) 닫힘 검증
- `LayoutlibRendererIntegrationTest.tier3 basic minimal smoke` — PASS (이전과 동일).
- `MaterialFidelityIntegrationTest` 4/4 — PASS (회귀 없음).
- `[RJarSymbolSeeder]` warn 진단 미발생 (R.jar 의 모든 동명 ATTR 가 동ID = 정상 transitive ABI).
- → RES_AUTO 통일이 chain walker / 단순 attr lookup 회귀 없이 적용됨.

### §1.2 Gap B (T12) 닫힘 검증
- `[AarResourceWalker] walked 41 AARs (12 with res, 29 code-only, 191 color-state-lists) in 28ms`
- 이는 plan v3 §2.2 census (192 expected) 와 1 file 차이 (반올림 / qualifier 회피의 결과 중 하나) — 의도한 default `res/color/` 만 매치.
- callback.getParser 가 selector XML 을 KXmlParser → ILayoutPullParser 로 정상 feed (단위테스트 13 / 13 PASS).

### §1.3 acceptance gate 의 새 fail surface — W3D4-γ-A
T11+T12 적용 후 `tier3 basic primary` 의 fail 원인이 sentinel-attr / color-state-list 가 아닌 **enum/flag 값 변환 실패**:

```
[layoutlib.warning] resources.format | "vertical" in attribute "orientation" is not a valid integer
[layoutlib.warning] resources.format | "center_horizontal" in attribute "gravity" is not a valid integer
[layoutlib.warning] resources.format | "parent" in attribute "layout_constraintEnd_toEndOf" is not a valid integer
[layoutlib.warning] resources.format | "parent" in attribute "layout_constraintStart_toStartOf" is not a valid integer
```

→ 결국 `LayoutlibRenderer 실패 + fallback 없음: activity_basic.xml` (renderPng 의 SessionParams 단계는 성공했으나 inflate 단계에서 attr 값 해석 fail).

## §2 Root cause — W3D4-γ-A 정의

### §2.1 AttrDef 의 enum/flag 자식 값 미캡처

**현재 ParsedNsEntry.AttrDef** (line 35-39):
```kotlin
data class AttrDef(
    val name: String,
    override val namespace: ResourceNamespace,
    override val sourcePackage: String? = null,
) : ParsedNsEntry()
```

→ 단지 (name, namespace) 만 보존, format / enum / flag children 무시.

**framework `attrs.xml` 의 실제 구조**:
```xml
<attr name="orientation">
    <enum name="horizontal" value="0"/>
    <enum name="vertical" value="1"/>
</attr>
<attr name="gravity">
    <flag name="top" value="0x30"/>
    <flag name="bottom" value="0x50"/>
    <flag name="center_horizontal" value="0x01"/>
    ...
</attr>
```

**현재 NamespaceAwareValueParser** (line 95-110): top-level `<attr>` 처리 시 children 을 `skipElement` 로 무시.

**LayoutlibResourceBundle**: `attrs: Map<String, AttrResourceValueImpl>` 로 등록하지만 `AttrResourceValueImpl.addValue(name, value, description)` API 를 사용 안 함.

**결과**: layoutlib Bridge 가 framework R$attr.orientation 의 enum table 을 RenderResources 에서 lookup 했을 때 빈 attr → "vertical" 을 int 로 변환하지 못해 warning 후 fallback (보통 0 / null).

### §2.2 ConstraintLayout 의 `parent` ID

`app:layout_constraintEnd_toEndOf="parent"` — `parent` 는 ConstraintLayout 이 정의한 special ID `R.id.parent = 0` (전통적으로 ConstraintLayout 의 reserve). 우리 R.jar 에 `R$id.parent` 가 있어야 callback.byRef 로 등록되고 `getOrGenerateResourceId` 가 0 반환. 현재는 등록 누락 가능성.

→ §2.1 fix 의 사이드 케이스로 검증 필요.

## §3 W3D4-γ scope (제안)

### §3.1 W3D4-γ-A — AttrDef enum/flag value capture
- `ParsedNsEntry.AttrDef` 에 `enumValues: Map<String, Int>` + `flagValues: Map<String, Int>` 추가.
- `NamespaceAwareValueParser` 의 `<attr>` 처리에 `<enum>` / `<flag>` 자식 child loop 추가.
- `LayoutlibResourceBundle.buildBucket` 의 AttrDef 처리에서 `AttrResourceValueImpl.addValue(name, value, null)` 호출.
- `declare-styleable` 안 nested `<attr>` 도 동일.
- 단위테스트 + framework attrs.xml 통한 IT 검증.

### §3.2 W3D4-γ-B (예상) — ConstraintLayout parent ID
- 검증: `R$id.parent == 0` 가 callback.byId 에 등록되는지.
- 미등록이면 RJarSymbolSeeder 가 R$id 처리 시 0 값을 skip 하는 sanity guard 가 있는지 확인 필요.

### §3.3 round 3 plan §5.4 escalation 정책 일관
- T12.5 (drawable selector feed) 는 본 세션의 gap shift 가 enum/flag value 였으므로 별도 surface — 추후 측정.
- Q6.5 (R$styleable) — Bridge.parseStyleable 가 styleable array 로 attr index → R$attr ID 변환을 처리. 본 IT 의 enum 변환 fail 은 styleable 단계 후의 attr 값 해석. 직결 안 되지만 인접.

## §4 본 세션 결과 요약

| Task | 산출 | 상태 |
|---|---|---|
| T11 (Gap A) | RJarSymbolSeeder RES_AUTO + per-type Map<String,Int> first-wins (commit `e83d75d`) | **DONE** — 198 tests / 0 fail |
| T12 (Gap B) | AarResourceWalker color/*.xml + ParsedNsEntry.ColorStateList + Bundle.colorStateLists + MinimalLayoutlibCallback.getParser + SelectorXmlPullParser (commit `4acb571`) | **DONE** — 215 tests / 0 fail, 191 color-state-lists 통합 |
| T13 (acceptance gate) | cache-invalidation BeforeEach 만 적용, primary IT 는 W3D4-γ-A 진단으로 re-`@Disabled` | **PARTIAL** — gap shifted, W3D4-γ escalate |

acceptance gate 진단 변환 — Gap A/B 둘 다 production-pipeline path 에서 닫혔음을 증명. 새 gap (W3D4-γ-A) 은 본 plan 범위 밖.

## §5 다음 단계

1. T13 partial commit + push (cache invalidation + re-`@Disabled` with W3D4-γ-A reason + 본 work_log).
2. `docs/superpowers/specs/2026-05-XX-w3d4-gamma-attr-enum-flag-capture-design.md` (또는 plan v3.1) 신규 spec 작성.
3. round 3 정책 재이용 (Codex+Claude pair) — W3D4-γ 또한 planning phase.
4. session 종료 시 `session-log.md` + `next-session-prompt.txt` (cold-start 가이드).
