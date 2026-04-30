# W3D4-β plumbing — session log (2026-04-30)

## Outcome

W3D4-β plan v3 (T11/T12/T13) 진행. **Gap A** (Material ThemeEnforcement sentinel attr namespace mismatch) **+ Gap B** (color XML state list input feed) 닫힘. 그러나 acceptance gate (`activity_basic.xml` SUCCESS) 는 plan §5.4 의 escalation 정책 정확히 적중 — fail surface 가 **제 3 layer** (`<attr>` 의 enum/flag 자식 미캡처 → "vertical/center_horizontal/parent" 변환 실패) 로 shift → **W3D4-γ-A carry**.

## Files created/modified

### main src
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/ResourceTypeFirstWinsGuard.kt` (신규) — RES_AUTO 통일 후 모든 ResourceType 의 cross-class 동명 first-wins guard. `Map<String, Int>` 로 동명+동ID silent / 동명+다른ID loud WARN.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt` — namespace `fromPackageName` → `RES_AUTO` 통일, AttrSeederGuard 위임 → ResourceTypeFirstWinsGuard 일반화.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AttrSeederGuard.kt` (삭제) — ResourceTypeFirstWinsGuard 가 인계.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AppLibraryResourceConstants.kt` — `AAR_COLOR_DIR_PREFIX`, `COLOR_XML_SUFFIX`, `COLOR_STATE_LIST_PLACEHOLDER_VALUE` 추가.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedNsEntry.kt` — `ColorStateList(name, rawXml, namespace, sourcePackage)` variant 추가.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalker.kt` — `parseValuesXml` + `collectColorStateLists` 분리, default `res/color/` 만 enumerate (qualifier 디렉토리 W4+ scope), 진단 메시지 갱신.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NsBucket.kt` — `colorStateLists: Map<String, String>` field 추가 + EMPTY 갱신.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt` — `getColorStateListXml(ref): String?` + `colorStateListCountForNamespace(ns)` + buildBucket 의 ColorStateList branch (placeholder ResourceValue 동반 등록).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SelectorXmlPullParser.kt` (신규) — KXmlParser StringReader feed + ILayoutPullParser (`getViewCookie` + `getLayoutNamespace`) — round 3 양쪽 reviewer 가 catch 한 `getLayoutNamespace` 누락 fix.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallback.kt` — 3rd 인자 `colorStateListLookup: (ResourceReference) -> String?` 추가 + `getParser` override (COLOR-only, prior null 동작 보존 for LAYOUT/MENU/DRAWABLE).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — callback wiring 에 `bundle.getColorStateListXml` lambda 주입.

### test src
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RDuplicateAttrIdTest.kt` (삭제) — ATTR-only 시대 인계.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/ResourceTypeFirstWinsGuardTest.kt` (신규, 5 테스트) — ATTR/STYLE/COLOR/DIMEN cross-class first-wins, same-id silent / different-id loud WARN.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeederTest.kt` — RES_AUTO assertion + cross-class 동ID silent + 다른ID loud 케이스 추가.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerColorTest.kt` (신규, 4 테스트) — mock AAR fixture 로 default 만 emit, qualifier dir skip, 부분 이용, code-only skip.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleColorStateListTest.kt` (신규, 6 테스트) — getColorStateListXml hit/miss/wrong-ns, byType[COLOR] placeholder 동반 등록, dup first-wins, count helper.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AarResourceWalkerTest.kt` — 기존 진단 메시지 assertion 갱신.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/MinimalLayoutlibCallbackColorParserTest.kt` (신규, 7 테스트) — getParser COLOR-only / LAYOUT null / DRAWABLE null / lookup miss / lookup hit + selector parse + getLayoutNamespace + viewCookie.
- 기존 callback 테스트 4개 (`MinimalLayoutlibCallbackInitializerTest`, `MinimalLayoutlibCallbackTest`, `MinimalLayoutlibCallbackLoadViewTest`, `SessionParamsFactoryTest`) — 14 callsite 모두 3rd 인자 `{ null }` 추가.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` — `@BeforeEach { LayoutlibResourceValueLoader.clearCache() }` (Claude Q6.3 안전망), primary `@Disabled` 갱신 (W3D4-γ-A 진단).

### docs
- `docs/superpowers/plans/2026-04-30-w3d4-beta-plumbing.md` (신규) — plan v3 (T11/T12/T13 spec, round 3 reconcile 후).
- `docs/work_log/2026-04-30_w3d4-beta-plumbing/round3-pair-review.md` (신규) — Codex+Claude pair verdict.
- `docs/work_log/2026-04-30_w3d4-beta-plumbing/t13-acceptance-gate-followup.md` (신규) — gap shift 분석 + W3D4-γ scope 제안.
- `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (본 파일).

## Test results

| 지표 | baseline (W3D4-β 시작) | T13 종료 |
|---|---|---|
| layoutlib-worker unit | 151 / 0 fail | **215 / 0 fail** (+64 net) |
| layoutlib-worker IT (PASS + SKIP) | 14 + 2 SKIP | **14 + 2 SKIP** (회귀 없음) |
| MaterialFidelityIntegrationTest | 4/4 PASS | 4/4 PASS |
| 모듈 합산 unit | 194 / 0 fail | 226 / 0 fail (+32 net) |
| `[AarResourceWalker]` 진단 | "walked 41 AARs (27 with values, 14 code-only)" | "walked 41 AARs (12 with res, 29 code-only, **191 color-state-lists**)" |

## Landmines discovered + 해결

| LM | 내용 | 해결 |
|---|---|---|
| LM-W3D4-β-D | `/* ... */` Kotlin 의 nested block comment 정책 — KDoc 안 `res/color/*.xml` 같은 path 가 `/*` 로 해석되어 doc block 닫힘 깨짐 (compile error "Unclosed comment"). | 해당 phrasing 변경 (`res slash color slash {name}.xml` 또는 backtick 인용). 향후 KDoc 작성 시 강제 회피. |
| LM-W3D4-β-E | `assertNotNull(x: T?): T?` (JUnit Jupiter) 가 platform null 으로 inference → Kotlin `!!` 또는 별도 변수 패턴 필요. | `val v = expr; assertNotNull(v); v!!.method()` 패턴으로 일관. |
| LM-W3D4-β-F | gradle property 기반 IT 분리 — `-PincludeTags=integration` 없이는 `@Tag("integration")` 제외 (default). | 빌드 스크립트 (buildSrc/axp.kotlin-common.gradle.kts:37-49) 의 unit/IT 분리 정책 확인 후 IT 실행 시 인자 명시. |
| LM-W3D4-β-G | subagent (Gap A 조사) 의 `unzip -q` 가 cwd 에 .class 부산물 남김 — git status 에 자동 노출. | 커밋 전 정리 + 향후 subagent dispatch 시 `--directory /tmp/...` 명시 권고. |
| LM-W3D4-β-H | acceptance gate fail surface shift — Gap A/B 닫힘 후 enum/flag layer 등 새 layer 발견. plan §5.4 의 escalation note 가 정확히 예측. | round 3 review 의 Q6 (open critique) 슬롯 유지가 결정적 — 미래 pair-review 도 동일 패턴 |

## Canonical document changes

- `docs/superpowers/plans/2026-04-30-w3d4-beta-plumbing.md` §1.4 / §2.2 / §3.1 / §4.1 — round 3 reconcile 인레인 적용 (5 plan delta + 2 escalation note).

## What's blocking / carried forward

### W3D4-γ (다음 세션)
1. **W3D4-γ-A — AttrDef enum/flag value capture** (primary fail surface): `<attr>` 자식 `<enum>/<flag>` 값을 ParsedNsEntry.AttrDef + AttrResourceValueImpl.addValue 로 보존. 본 세션 t13-acceptance-gate-followup.md §3.1 spec.
2. **W3D4-γ-B — ConstraintLayout parent ID** (예상): `R$id.parent == 0` callback.byId 등록 검증.

### W3D4-β 의 미해결 (defer)
- **T12.5** (Drawable selector feed) — primary fail surface 가 enum/flag 으로 직진 → drawable selector path 까지는 도달 안 함. T12 의 COLOR-only filter 는 향후 W3D4-γ-A 후 다시 측정.
- **Q6.5 (R$styleable)** — 현재 fail 이 enum/flag 변환 (attr value parse) 이지 styleable index 매핑은 아니므로 직결 안 됨. W3D4-γ-A 진행 시 자연스럽게 인접 검증.

## Pair review verdicts

- **Round 3** (planning-phase Codex+Claude pair, 2026-04-30): GO_WITH_FIXES (Codex 0.86 / Claude 0.78). 5 plan delta + 2 escalation note 적용. 자세한 내용은 `round3-pair-review.md`. 가장 강한 convergence: 양쪽 독립 catch 한 `SelectorXmlPullParser.getLayoutNamespace()` 누락 (Q6).

## Commits + push

- `2f1909b` — docs(w3d4-beta): plan v3 plumbing + round 3 pair-review (GO).
- `e83d75d` — feat(w3d4-beta): T11 RJarSymbolSeeder RES_AUTO + per-type id-aware first-wins.
- `4acb571` — feat(w3d4-beta): T12 color state list walker + parser feed via callback.
- `10ed4f3` — feat(w3d4-beta): T13 partial — cache-invalidation + W3D4-γ-A escalation.
- `0ea4f8c` — docs(w3d4-beta): session-log + W3D4-γ handoff (cold-start safe).
- `4a31771` — docs(w3d4-gamma): plan v3.1 attr enum/flag capture + round 4 pair-review.
- `3ca8bb5` — feat(w3d4-gamma): T14 AttrDef enum/flag value capture + RES_AUTO ATTR exposure.
- `1541958` — feat(w3d4-gamma): T15 framework Bridge.init enumValueMap wiring.

총 8 commits (β 4 + γ 4), 모두 push 완료. T16 partial commit (재 `@Disabled` + work_log) 은 본 라인 직후.

---

## W3D4-γ session append (2026-04-30 → 2026-05-01)

### W3D4-γ Outcome

W3D4-β plan v3 의 escalation §5.4 정책에서 도래한 enum/flag capture phase. plan v3.1 작성 → round 4 pair-review (Codex REVISE_REQUIRED 0.93 / Claude GO_WITH_FIXES 0.92, 7 deltas inline) → T14 (RES_AUTO path) + T15 (framework Bridge.init wiring) + T16 (acceptance gate).

**핵심 플랜 수정 (round 4 의 critical Q5)**: Codex 가 Claude 가 놓친 KILL POINT catch — `LayoutlibResourceBundle.getResource(ref)` 이 byType-only 조회로 ATTR ref → null 반환. AttrDef 가 `attrsMut` 별도 등록되어 NsBucket 의 byType ↔ attrs 분리 때문. 이 fix 없이 T14 의 addValue 만으로는 BridgeTypedArray.resolveEnumAttribute 의 instanceof cast 실패 — acceptance gate 닫지 못함.

**T16 측정 결과 (LM-W3D4-β-H 정확 실현)**: γ-A.1 + γ-A.2 가 의도 surface 3 warning ("vertical/center_horizontal/parent is not a valid integer") 모두 닫음 — system-err 발화 부재로 검증. 그러나 새 fail surface (W3D4-δ-A escalation) 발견:

```
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION
  msg=This component requires that you specify a valid TextAppearance attribute.
      Update your app theme to inherit from Theme.MaterialComponents (or a descendant).
```

상세는 `t16-acceptance-gate-followup.md` 참조.

### W3D4-γ files

#### main src
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/ParsedNsEntry.kt` — AttrDef 에 `enumValues: Map<String, Int>` + `flagValues: Map<String, Int>` 추가, 신규 두 필드는 default 없음 (호출처가 명시 emptyMap).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParser.kt` — `parseAttrChildren` (depth-aware enum/flag child loop) + `parseAttrValueLiteral` (`Long.decode(...).toInt()` 로 32-bit unsigned hex + 음수 cover) helper 추가, top-level + declare-styleable nested `<attr>` branch 모두 갱신, `TAG_ENUM`/`TAG_FLAG`/`ATTR_VALUE` 상수 신규.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundle.kt` — **`getResource(ref)` 에 ATTR special-case 추가 (round 4 Q5 KILL POINT fix)**, buildBucket 의 AttrDef branch 에 `AttrResourceValueImpl.addValue` 호출, `frameworkEnumValueMap()` helper 추가 (byNs[ANDROID].attrs → Bridge.init 형식 export).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — `loaderArgs()` private helper 추출, `initBridge` 의 빈 enumValueMap 을 `bundle.frameworkEnumValueMap()` 로 교체, `renderViaLayoutlib` 의 inline Args build 도 helper 사용.

#### test src
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/AttrEnumFlagCaptureTest.kt` (신규, 9 cases) — parser child capture 단위.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleAttrValuesTest.kt` (신규, 5 cases) — bundle addValue + getResource ATTR 회귀 가드.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibRenderResourcesAttrLookupTest.kt` (신규, 3 cases) — RenderResources 의 getResolvedResource + getUnresolvedResource ATTR 검증 (BridgeTypedArray + BridgeXmlPullAttributes 양쪽 path cover).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleFrameworkEnumExportTest.kt` (신규, 5 cases) — frameworkEnumValueMap export 검증.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/LayoutlibResourceBundleTest.kt` — line 79-80 의 AttrDef 3-arg 호출을 5-arg 로 갱신 (round 4 Q6 명시).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` — `tier3 basic primary` `@Disabled` 갱신 (W3D4-δ-A escalation reason).

#### docs
- `docs/superpowers/plans/2026-04-30-w3d4-gamma-attr-enum-flag.md` (신규) — plan v3.1, round 4 reconcile 의 7 deltas inline.
- `docs/work_log/2026-04-30_w3d4-beta-plumbing/round4-pair-review.md` (신규) — Codex+Claude round 4 verdict + reconcile.
- `docs/work_log/2026-04-30_w3d4-beta-plumbing/t16-acceptance-gate-followup.md` (신규) — T14/T15/T16 측정 결과 + W3D4-δ-A escalation 분석.

### W3D4-γ test results

| 지표 | T13 종료 | T14 후 | T15 후 | T16 partial |
|---|---|---|---|---|
| layoutlib-worker unit | 172 | 189 (+17) | 194 (+5) | 194 |
| layoutlib-worker IT (PASS + SKIP) | 14 + 2 SKIP | 14 + 2 SKIP | 14 + 2 SKIP | 14 + 2 SKIP |
| 모듈 합산 unit | 215 | 232 (+17) | 237 (+5) | 237 |
| `tier3-basic-primary` warning surface | enum/flag (3 warnings) | (T14 단독) framework path 미해결 | **3 warnings 모두 closed** | TextAppearance sentinel (W3D4-δ-A carry) |
| cold-start `[LayoutlibResourceValueLoader]` total | (값 동일) | — | 89ms | 85ms |

### W3D4-γ Landmines (carry over from β + 신규)

**β 의 carry over** (모두 본 session 에서 회피 검증, t16-followup §6):
| LM | 회피 검증 |
|---|---|
| LM-W3D4-β-D | 신규 KDoc 모두 backtick 인용 또는 일반 prose ✓ |
| LM-W3D4-β-E | 신규 test 의 `assertNotNull(...)` 모두 별도 변수 패턴 ✓ |
| LM-W3D4-β-F | IT 실행 시 `-PincludeTags=integration` 일관 ✓ |
| LM-W3D4-β-G | round 4 Codex CLI 직접 사용, subagent unzip 없음 ✓ |
| LM-W3D4-β-H | T16 의 새 fail surface 분류 (TextAppearance sentinel) — δ escalation ✓ |

**γ session 신규**:
| LM | 내용 | 후속 |
|---|---|---|
| LM-W3D4-γ-A | round 4 reviewer (Claude) 가 Q5 의 NsBucket byType ↔ attrs 분리 issue 미발견 — Codex 의 file:line evidence 가 catch. 향후 reviewer prompt 에 "bundle 의 lookup path 를 trace 하라" 명시 권장 | round 5+ pair prompt 갱신 |
| LM-W3D4-γ-B | `Integer.decode` 가 32-bit unsigned hex (`0x80000000`, `0xffffffff`) 에 NumberFormatException — `Long.decode(...).toInt()` 가 정합. attr literal 파싱 시 framework attrs.xml 의 mask flag 검증 필수 | parseAttrValueLiteral 본 implementation 영구 |
| LM-W3D4-γ-C | ThemeEnforcement 의 multi-sentinel check — colorPrimary 닫힘 후 TextAppearance 가 다음 layer. Material AAR 안 ThemeEnforcement 의 sentinel surface 가 stepwise 발견 — δ phase 에서 한 번에 census 권장 | W3D4-δ |
