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

---

## W3D4-δ planning session append (2026-05-04)

### W3D4-δ Outcome (planning phase only — implementation 미진입)

W3D4-γ T16 partial 의 escalation 정책 정확히 적중 (LM-W3D4-β-H + LM-W3D4-γ-C 인계) — TextAppearance sentinel 의 multi-sentinel surface census 후 plan v3.2 작성. 3-stream parallel investigation (subagent A: ThemeEnforcement bytecode 7-attr 전체 census · subagent B: Theme.AxpFixture 15-level parent chain · subagent C: failing widget activity_basic.xml:43 MaterialButton 의 정확한 call site) 결과 dominant root cause 가설 H2 도출 — `LayoutlibResourceBundle.getResource(STYLE ref)` 의 byType-only 경로가 styles map 도달 못 함 (W3D4-γ T14 round 4 Q5 ATTR special-case 의 STYLE 변종, 자료구조 분리 패턴 동일).

### W3D4-δ files (planning artefacts)

#### docs
- `docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md` (신규) — plan v3.2, round 5 reconcile 의 12 deltas inline 적용 완료. T17 (5-question diagnostic battery) → T18 (H2 hypothesis-targeted fix, dominant; §5.2 walkParent fallback 백업) → T19 (acceptance gate close).
- `docs/work_log/2026-04-30_w3d4-beta-plumbing/round5-pair-review.md` (신규) — Codex+Claude round 5 verdict + 12 deltas + 3 escalation notes.

### W3D4-δ pair-review verdict

- **Round 5** (planning-phase Codex+Claude pair, 2026-05-04): GO_WITH_FIXES → APPLIED (Codex 0.91 / Claude 0.88). 12 plan deltas inline 적용. 가장 강한 convergence: Q4 양쪽 DISAGREE 의 동일 file:line evidence (`Args.sampleAppRoot` 정합, `PathLocator` 부재, `@Tag` 모순) — KILL POINT direct-verify (memory feedback_pair_review_codex_killpoint.md 패턴) — judge round 불요. 자세한 내용은 `round5-pair-review.md`.

### W3D4-δ 신규 LM (round 5 산출)

| LM | 내용 | 후속 |
|---|---|---|
| LM-W3D4-δ-A | spec 작성 시 fabricated API references (`PathLocator.locateW3D4Triplet()` + `Args.sampleAppModuleRoot`) 가 양쪽 reviewer Q4 DISAGREE 로 catch. compile fail 확정. 향후 plan 작성 시 모든 코드 sample 의 함수/필드 호출은 실 source verify 후 인용 의무 | round 6+ pair prompt 갱신 — "코드 sample 의 모든 식별자 grep 검증" 명시 |
| LM-W3D4-δ-B | NsBucket 의 byType ↔ styles ↔ attrs 3-way 분리에서 `getResource` 가 byType 만 보는 패턴이 type 별 sibling KILL POINT — γ T14 (ATTR) + δ-A (STYLE) 두 차례 catch. 향후 NsBucket 의 새 type-specific map 추가 시 동일 회귀 위험 — code-review checklist 화 | future bucket 추가 시 review item |
| LM-W3D4-δ-C | round 5 escalation: `LayoutlibResourceValueLoader` 가 `runtime-classpath.txt` 부재 시 silent empty AAR list (loader.kt:42-44) — false-PASS 위험. T17 의 graceful skip 패턴 (assumeTrue) 으로 본 phase 의 영향 회피, 단 W4+ hardening pass 권장 | W4+ scope |
| LM-W3D4-δ-D | T16 partial → T17/T18/T19 의 phase split 이 round 5 Q4 catch 후 `@Tag("integration")` 유지 + KDoc 정합 결정. T17 의 RED-on-main 우려는 default unit suite excludes integration tag (axp.kotlin-common.gradle.kts:43-50) 로 회피 — 본 결정 명시화 | T17 commit 시점 명시 |

### What's blocking / carried forward

#### W3D4-δ implementation (다음 세션)
1. **T17** — `W3D4DeltaThemeChainDiagnosticTest.kt` 신규 (IT-tagged, 5 question battery). spec §4.1 에 완전 코드 포함 — round 5 의 fabricated API fix 적용 후 compile 가능 상태.
2. **T18** — H2 dominant 적용 (§5.1 의 `bundle.getResource` STYLE special-case + 회귀 테스트 4 cases). T17 결과에 따라 §5.2 (walkParent fallback) 추가 commit 검토.
3. **T19** — `tier3-basic-primary` `@Disabled` 제거 + IT 측정. PASS 시 δ-A close. fail surface shift 시 W3D4-δ-B (`enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant` gate) 또는 W3D4-ε (R$styleable layer) escalate.

#### Out-of-scope (carry)
- **W3D4-δ-B** — colorPrimaryVariant gate. T19 fail 시 escalate.
- **W3D4-ε** — R$styleable seeder gap (RJarSymbolSeeder.kt:64-66). T19 fail + δ-B 적용 후도 fail 시 §5.3.1 의 callback instrumentation 으로 narrow.
- **W3D4 tier3-glyph** — Font wiring, W4 carry.

### W3D4-δ commits + push (planning only)

- (다음 commit) `docs(w3d4-delta): plan v3.2 + round 5 pair-review (GO_WITH_FIXES → APPLIED)` — 본 session-log append + plan v3.2 + round5-pair-review.md.

T17/T18/T19 의 implementation commit 은 별도 session 진입 시 수행 (T17 은 IT-tagged 로 default unit suite 제외 — RED-on-main 가능, integration runner 만 PASS/FAIL signal).

---

## W3D4-δ implementation session append (2026-05-06)

### W3D4-δ Outcome (T17/T18/T19 완료 — δ-A closed, δ-B escalation)

Plan v3.2 의 T17/T18/T19 sequence 완료. Plan §5.6 결정 매트릭스의 row 2 ("H1 PASS, H2 FAIL, H3a/H3b PASS, instanceof gate FAIL → §5.1 적용") 적중 — H2 KILL POINT (`LayoutlibResourceBundle.getResource(STYLE ref)` 의 byType-only 경로) 가 dominant root cause 로 확정. T18 의 3-line STYLE special-case 적용 후 T17 5-probe diagnostic 5/5 PASS 전환 + acceptance gate 의 TextAppearance sentinel layer (W3D4-δ-A) 닫힘. 단 acceptance gate 자체는 plan §6.3 의 정확한 예측대로 W3D4-δ-B (gate chain `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant`) 로 escalate — plan §11 out-of-scope.

### W3D4-δ files

#### server/layoutlib-worker — main
- `LayoutlibResourceBundle.kt` — `getResource(STYLE ref)` 가 `bucket.styles[ref.name]` 위임 (3-line special-case sibling to T14 의 ATTR). KDoc 영문 + structural-only 로 갱신. instanceof StyleResourceValue contract 보존.
- `LayoutlibResourceValueLoader.kt` — bootstrap 시 `axp.debug.bundleShape=true` gated one-shot summary log 추가 (RES_AUTO styles/attrs counts + byType[STYLE] intentionally-empty 진단). production hot-path 영향 없음 (default off).

#### server/layoutlib-worker — test
- `W3D4DeltaThemeChainDiagnosticTest.kt` (신규) — 5-probe diagnostic battery (H1 reachable-by-name / H2 getResource STYLE / H3a findItemInTheme materialButtonStyle / H3b findItemInTheme textAppearanceButton / bridgeTypedArray instanceof gate). `@Tag("integration")` — default unit suite 제외, `-PincludeTags=integration` opt-in.
- `LayoutlibResourceBundleStyleLookupTest.kt` (신규, 4 cases) — STYLE-ref instanceof contract pinning + byType fallback 회귀 가드.
- `LayoutlibRendererIntegrationTest.kt` — `tier3 basic primary` `@Disabled` reason 갱신 (δ-A → δ-B gate-chain structural reason, 영문 + Rule 2 OUT-OF-SCOPE 준수).

#### docs/work_log
- `t19-acceptance-gate-followup.md` (신규) — δ-A close + δ-B escalation chain 분류 (LM-W3D4-β-H 적용). stack trace + surface diff + 3-option closure ladder (isMaterialTheme short-circuit / colorPrimaryVariant chain / M3-path dispatch).

### Test posture (T19 commit 후)

| 측정 | baseline (T16 partial) | T19 commit 후 |
|---|---|---|
| 모듈 합산 unit | 237 PASS | **241 PASS** (+4 LayoutlibResourceBundleStyleLookupTest) |
| layoutlib-worker IT (`-PincludeTags=integration`) | 14 PASS + 2 SKIP | **19 PASS + 2 SKIP** (+5 W3D4DeltaThemeChainDiagnosticTest probes; δ-B carry + tier3-glyph W4 carry SKIP) |
| acceptance gate (`activity_basic` SUCCESS) | δ-A `@Disabled` carry | **δ-A closed**, δ-B `@Disabled` (escalation marker) |
| T17 diagnostic 5-probe (H2 KILL POINT 검증) | n/a | **5/5 PASS** (회귀 가드) |

### W3D4-δ pair-review verdict

본 session 은 implementation phase — Claude+Codex 1:1 pairing 비대상 (CLAUDE.md §Codex: "1:1 Claude+Codex Pairing — Planning & Plan-Review ONLY"). T17 결과의 plan §5.6 row matching 이 hypothesis 검증을 fully prescribe 했으므로 추가 pair-review 불요. T18 fix 후 T17 5/5 PASS + IT 19/2 가 합산 검증.

### W3D4-δ 신규 LM (implementation 산출)

| LM | 내용 | 후속 |
|---|---|---|
| LM-W3D4-δ-E | T19 fail surface 의 `MaterialButton.<init>` offset 53 가 T18 전후 동일하지만 throw 진입 stage 가 `checkTextAppearance` (T18 전) → `checkCompatibleTheme → checkMaterialTheme` (T18 후) 로 shift — ThemeEnforcement 의 multi-stage 검사 패턴 확인. δ-A close 검증 시 메시지 자체로 layer 식별 (stack trace 의 entry point 비교) | δ-B planning entry 시 검증 패턴 |
| LM-W3D4-δ-F (CLAUDE.md update mid-session) | Rule 2 OUT-OF-SCOPE 확대 — phase/task identifier (W3D4-δ-A, T17 등) 도 PR/conversation reference category. KDoc/`@Disabled` message 모두 structural-only 로 영문 + 식별자 회피. 본 session 의 신규 코드 모두 적용 (T17 KDoc, getResource KDoc, LayoutlibRendererIntegrationTest @Disabled message) | future commit 모두 적용 |

### Carry-forward LM (combined)

W3D4-β/γ + δ-planning + δ-implementation 의 모든 LM 통합 — 다음 세션 entry 시 모두 내재화:

| LM | 1-line 핵심 |
|---|---|
| LM-W3D4-β-D | KDoc path 표기 시 `/*` 회피 (backtick 인용) |
| LM-W3D4-β-E | `assertNotNull(...)` 반환 chain 금지 → `val v = ...; assertNotNull(v); v!!.method()` |
| LM-W3D4-β-F | IT 실행은 `-PincludeTags=integration` 명시 |
| LM-W3D4-β-G | subagent unzip `--directory /tmp/...` (cwd 회피) |
| LM-W3D4-β-H | acceptance fail 시 stack trace 우선 → fail surface 가 spec layer 인지 새 layer 인지 분류 |
| LM-W3D4-γ-A | reviewer prompt 에 "bundle 의 lookup path trace" 명시 |
| LM-W3D4-γ-B | `Long.decode(...).toInt()` 로 32-bit unsigned hex literal 처리 |
| LM-W3D4-γ-C | ThemeEnforcement multi-sentinel census — δ-A planning 이 7 attr 한 번에 census (적용 완료) |
| LM-W3D4-δ-A | spec 코드 sample 의 모든 식별자 grep/Read 검증 의무 |
| LM-W3D4-δ-B | NsBucket type-specific map 분리 = type 별 sibling KILL POINT (ATTR 의 γ T14 + STYLE 의 δ T18 검증) |
| LM-W3D4-δ-C | LayoutlibResourceValueLoader runtime-classpath.txt 부재 silent empty AAR 위험 — W4+ hardening |
| LM-W3D4-δ-D | T17 IT-tagged RED-on-main 가능 (default unit suite 제외) |
| LM-W3D4-δ-E | acceptance gate fail 의 stack-trace entry-point 비교로 layer 식별 |
| LM-W3D4-δ-F | CLAUDE.md Rule 2 OUT-OF-SCOPE 확대 (phase/task identifier 도 forbidden) — KDoc/annotation 모두 structural-only 영문 |
| LM (CLAUDE.md Three Hard Rules) | 모든 신규 코드 KDoc/inline 영문 only, function/structure only, ticket reference 최소화 |

### What's blocking / carried forward

#### W3D4-δ-B planning entry (다음 세션)
- 본 session 의 t19-acceptance-gate-followup.md §5 의 3-option closure ladder 가 plan-revision 의 입력:
  - 옵션 A — `isMaterialTheme=true` 를 fixture theme chain 에서 expose (checkCompatibleTheme first-gate short-circuit).
  - 옵션 B — `colorPrimaryVariant` chain resolution 정합 (Lvl 5:2214 chain 검사).
  - 옵션 C — M3 path dispatch (Widget.Material3.Button) — H2 fix 가 enable, materialButtonStyle chain resolve 검증 추가.
- 다음 plan-revision 는 1:1 Claude+Codex pair-review (CLAUDE.md §Codex: planning phase) — 3 옵션 중 어느 것이 minimal change & maximum coverage 인지 결정.
- T17 5-probe IT 는 본 phase regression guard — δ-B fix 후도 5/5 PASS 보장.

#### Out-of-scope (carry)
- **W3D4-ε** — R$styleable seeder gap (RJarSymbolSeeder.kt:64-66). δ-B 적용 후도 acceptance fail 시 §5.3.1 의 BridgeContext callback instrumentation (axp.debug.callback) 으로 narrow.
- **W3D4 tier3-glyph** — Font wiring, W4 carry.
- **DRAWABLE selector XML feed** — W3D4-β plan v3 §5.4 T12.5 escalation. 별도 phase.

### W3D4-δ commits + push

- `2ce640a` — `feat(w3d4-delta): T17 theme chain diagnostic battery (5-probe IT)` (신규 W3D4DeltaThemeChainDiagnosticTest.kt, RED-on-main intentional).
- `22f7077` — `feat(w3d4-delta): T18 LayoutlibResourceBundle.getResource STYLE special-case (H2 KILL POINT fix)` (3-line fix + bootstrap log + 4-case regression test). T17 5/5 PASS 전환.
- `473f55a` — `feat(w3d4-delta): T19 partial — TextAppearance sentinel layer closed via T18, gate-chain surface escalates` (`@Disabled` reason 갱신 to δ-B + t19-acceptance-gate-followup.md).

---

## W3D4-δ-B planning session append (2026-05-06)

### W3D4-δ-B Outcome (planning phase only — implementation 미진입)

W3D4-δ implementation phase (T17/T18/T19) 직후 W3D4-δ-B planning entry. 3-stream subagent burst (TE bytecode / fixture chain / BridgeContext dispatch) 로 옵션 A/B/C cost-benefit 도출 → plan v4 draft → round 6 Codex+Claude pair-review (REVISE 0.84 + GO_WITH_FIXES 0.86) → 12 deltas inline → plan v4.1 GO. T20/T21/T22 implementation 은 다음 세션 entry.

### W3D4-δ-B planning artefacts

#### docs
- `docs/superpowers/specs/2026-05-06-w3d4-delta-b-gate-chain-design.md` (신규) — plan v4.1, round 6 reconcile 의 12 deltas inline. T20 (5-probe gate-chain diagnostic IT) → T21 (fixture themes.xml 1-line `<item name="colorPrimaryVariant">?attr/colorPrimary</item>`) → T22 (acceptance gate close).
- `docs/work_log/2026-04-30_w3d4-beta-plumbing/round6-pair-review.md` (신규) — Codex+Claude round 6 verdict + 12 deltas + 3 KILLPOINTS resolution + LM-W3D4-δ-G 신규.

### W3D4-δ-B 옵션 결정 — 옵션 B (colorPrimaryVariant)

3 옵션 cost-benefit:
- **옵션 A** (`isMaterialTheme=true` 1-line) — gate skip, semantic loose, side-effect medium-low (round 6 Q4: ThemeEnforcement.class 외 colorPrimaryVariant direct reader 0).
- **옵션 B** (`colorPrimaryVariant=?attr/colorPrimary` 1-line) — gate PASS, semantic strict, future-proof. **dominant**.
- **옵션 C** (M3 path dispatch) — closed (Widget.Material3.Button 도 부모 Widget.MaterialComponents.Button:6349 의 enforceMaterialTheme=true 상속).

옵션 B 의 enable mechanism: Codex+Claude convergent BridgeContext bytecode trace — `Resources.Theme.obtainStyledAttributes(int[])` → `Resources_Theme_Delegate.internalObtainStyledAttributes` → `BridgeContext.createStyleBasedTypedArray(style=null)` → **`mRenderResources.findItemInTheme`** (LayoutlibRenderResources.kt:211-221) → `bridgeSetValue` → `BridgeTypedArray.hasValue(slot)` returns `mResourceData[slot] != null`. fixture-side 직접 정의 → chain walker 의 first-match → BridgeContext.hasValue=true.

### W3D4-δ-B pair-review verdict

- **Round 6** (planning-phase Codex+Claude pair, 2026-05-06): Codex REVISE 0.84 + Claude GO_WITH_FIXES 0.86 → APPLIED (12/12 deltas inline). 가장 강한 convergence: Q1 (BridgeContext path trace) + Q4 (colorPrimaryVariant direct reader census). 가장 강한 divergence: Q3 (BridgeContext-side P6/P7 vs P2+P3 equivalence) — equivalence note + setupResources caveat 으로 union 적용. KILLPOINTS 3건 (Codex) 직접 verify 후 inline 정정 (judge round 불필요, memory feedback_pair_review_codex_killpoint.md 패턴).

### W3D4-δ-B 신규 LM (round 6 산출)

| LM | 내용 | 후속 |
|---|---|---|
| LM-W3D4-δ-G | annotation message (e.g. `@Disabled` reason) 안 future option 의 *technical claim* 작성 시 file:line evidence 사전 verify 의무 | round 6 의 T19 `@Disabled` stale text catch ("M3 path 가 enforceMaterialTheme=false 로 SKIP" — 실제 Widget.Material3.Button 본체 override 부재, true 상속) 가 trigger. plan v4 §6.1 의 T22 가 본 stale @Disabled 제거하므로 자동 close. future annotation 작성 시 동일 LM 적용 |

### What's blocking / carried forward

#### W3D4-δ-B implementation (다음 세션)
1. **T20** — `W3D4DeltaBGateChainProbeTest.kt` 신규 (IT-tagged, 5-probe). spec §4.1 의 완전 코드 (round 6 Q1 equivalence note + LM-W3D4-δ-A self-check 적용 후 compile 가능 상태).
2. **T21** — fixture `themes.xml` 의 `Theme.AxpFixture` 에 1-line 추가. T20 P2/P3 의 PASS 전환 검증.
3. **T22** — `tier3-basic-primary` `@Disabled` 제거 + IT 측정. PASS 시 W3D4-δ phase 종결 (δ-A + δ-B 양쪽 sentinel layer closed).

#### Out-of-scope (carry)
- **W3D4-ε** — R$styleable seeder gap (RJarSymbolSeeder.kt:64-66). T22 fail 시 escalate.
- **theme overlay support** — `materialThemeOverlay` chain walker 처리. future widget surface.
- **direct-sentinel widget surface** — BadgeDrawable + BaseTransientBottomBar 직접 호출 (round 6 Q4 evidence). 현 fixture 부재.
- **W3D4 tier3-glyph** (W4 carry).

### W3D4-δ-B commits + push (planning only)

- (다음 commit) `docs(w3d4-delta-b): plan v4.1 + round 6 pair-review (REVISE+GO_WITH_FIXES → APPLIED → GO)` — 본 session-log append + plan v4.1 + round6-pair-review.md.

T20/T21/T22 의 implementation commit 은 별도 session 진입 시 수행.
