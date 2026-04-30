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

총 4 commits, 모두 push 완료.
