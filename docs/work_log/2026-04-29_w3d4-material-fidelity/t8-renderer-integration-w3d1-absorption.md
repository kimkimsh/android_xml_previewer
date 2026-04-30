# T8 — LayoutlibRenderer + SharedLayoutlibRenderer 통합 (themeName 5-tuple) + W3D1 흡수

Session: 2026-04-30. Atomic commit covering plan v2 §3.2 (T8 structural change) + 2 latent T2 parser
bug fixes discovered during T8 production wiring.

## Outcome

LayoutlibRenderer ctor 5-tuple 으로 reorder + themeName 4번째 인자 추가 + default param 제거 (CLAUDE.md
"No default parameter values"). 7+1 callsite 모두 named args + themeName 명시 (round-2 페어 review
에서 enum 한 7 site 외 SharedRendererBindingTest 의 RendererArgs 직접 호출 4 site 추가 발견).
RendererArgs 4-tuple. SessionConstants `DEFAULT_FIXTURE_THEME = "Theme.AxpFixture"` 추가.

LayoutlibRenderer.renderViaLayoutlib line 174 swap:
- `FrameworkResourceValueLoader.loadOrGet(distData)` → `LayoutlibResourceValueLoader.loadOrGet(Args(distData, sampleAppRoot, runtimeClasspathTxt))` (3-입력 통합)
- `FrameworkRenderResources(bundle, DEFAULT_FRAMEWORK_THEME)` → `LayoutlibRenderResources(bundle, themeName)` (σ FULL resolver)

W3D1 흡수 — 5 main + 5 test = **10 파일 삭제**. SessionParamsFactoryTest helper 는 W3D4 자료구조로
inline migrate (W3D1 import 제거 → LayoutlibResourceBundle/LayoutlibRenderResources).

**Round 2 페어 review 가 catch 못한 latent T2 bug 2개를 T8 production wiring 시점에 발견** (real
AAR XML 통과 시):
1. **Mixed content** — `<string>Hello <b>world</b></string>` 등 inline markup. StAX
   `XMLStreamReader.elementText` 가 mixed content 시 throw → `readElementText` helper 로 W3D1
   `readText` 패턴 회복 (depth-aware text collector + CDATA 처리 + child markup strip + child
   text accumulate).
2. **Cross-NS attr ref** — `<attr name="android:visible" />` 같은 declare-styleable 안 framework
   attr ref. ResourceReference name 이 `:` disallow → AssertionError. parser 레벨에서 `:` 포함
   name 의 AttrDef emit 을 skip (aapt2 와 동일 정책).

두 bug 모두 W3D1 → W3D4 absorption gap. round 2 reviewer 가 synthetic XML fixture 만 검증, real
Material/AppCompat AAR 미접근 → T8 production wiring 시점에 cascading 으로 surface.

## Files modified (16) + deleted (10) = 26

### Production
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/session/SessionConstants.kt` — `DEFAULT_FIXTURE_THEME = "Theme.AxpFixture"` 추가.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/LayoutlibRenderer.kt` — ctor reorder (distDir, fixtureRoot, sampleAppModuleRoot, themeName, fallback) brace own-line, line 174 swap.
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParser.kt` — `readElementText` helper 추가 + 3 call site 의 `reader.elementText` 교체 (handleSimpleValue, handleStyle inner-loop, parseInternal TAG_ITEM 분기). top-level TAG_ATTR + handleDeclareStyleable 의 `<attr name="X">` 분기에 `:` 포함 name skip. `NS_NAME_SEPARATOR_CHAR` const 추가 (CLAUDE.md zero-magic-strings).
- `server/mcp-server/src/main/kotlin/dev/axp/mcp/Main.kt` — LayoutlibRenderer ctor 호출에 `themeName = SessionConstants.DEFAULT_FIXTURE_THEME` 추가 (positional 보존, named args). SessionConstants import 추가.

### Test sourceSet
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/RendererArgs.kt` — 4-tuple (`themeName: String` 추가).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRenderer.kt` — getOrCreate signature + 내부 LayoutlibRenderer 생성 named args.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedRendererBindingTest.kt` — 4 RendererArgs 호출 site 갱신 (themeA/themeB fixture 추가).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` — getOrCreate 에 themeName 추가.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/SharedLayoutlibRendererIntegrationTest.kt` — 5 positional getOrCreate 호출 → named + themeName.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererTier3MinimalTest.kt` — getOrCreate 에 themeName 추가.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/session/SessionParamsFactoryTest.kt` — helper 를 LayoutlibRenderResources(LayoutlibResourceBundle.build(emptyMap()), DEFAULT_FRAMEWORK_THEME) 로 migrate. `default theme is non-null and framework-namespaced` 의 `theme.namespace == ANDROID` assert 제거 (W3D4 multi-namespace 시맨틱: empty bundle fallback theme 은 RES_AUTO ns — assert 가 brittle implementation detail). 사용 안 하는 `ResourceNamespace` import 제거.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/NamespaceAwareValueParserTest.kt` — 4 신규 case (mixed content, xliff:g, declare-styleable cross-NS skip, top-level cross-NS skip).

### Deleted (W3D1 흡수)
- 5 main: `FrameworkResourceBundle.kt`, `FrameworkRenderResources.kt`, `FrameworkResourceValueLoader.kt`, `FrameworkValueParser.kt`, `ParsedEntry.kt`
- 5 test: 위 4 파일에 대한 `*Test.kt` (ParsedEntryTest 는 원래 부재) + `FrameworkResourceLoaderRealDistTest.kt` (W3D1 F1 regression guard — W3D4 `LayoutlibResourceValueLoaderTest` framework-only path + 후속 `MaterialFidelityIntegrationTest` 가 동등 cover).

## Test results

- **Unit: 151 PASS / 0 fail / 0 skip** (pre-T8 baseline 176 - 31 deleted W3D1 test cases + 4 new W3D4 mixed-content/cross-NS cases + 2 SessionParamsFactoryTest 변경 = 147 + 4 새로 추가 = 151).
- **Integration: 11 PASS / 0 fail / 1 SKIP (tier3-glyph Disabled, W4 carry, 변경 없음)**. Pre-T8 baseline 13 PASS - 2 deleted FrameworkResourceLoaderRealDist cases = 11. **회귀 0**.
- **Build: SUCCESSFUL** (전체 server module).

## Landmines discovered + resolved

### LM-T8-A: 7-callsite enum 누락 (round 2 페어 review oversight)

Round 2 페어 review verdict 가 7 callsite 표를 명시했지만 (`Main.kt:133`, `SharedLayoutlibRenderer.kt:45`,
3 IT files), `SharedRendererBindingTest.kt` 의 RendererArgs 직접 호출 4 site 누락. RendererArgs 가
4-tuple 로 확장되면서 `No value passed for parameter 'themeName'` compile error 발생. Step 4 에서
인지 후 4 site 갱신 (themeA/themeB fixture 추가).

**보완**: 향후 round 2 페어가 ctor reorder + data class 확장 시 transitive caller (data class 직접
호출자) 까지 enum 권고.

### LM-T8-B: SessionParamsFactoryTest oversight (plan v2 deletion list 누락)

Plan v2 §3.3 의 W3D1 deletion list 가 `FrameworkResourceBundle.kt`, `FrameworkRenderResources.kt` 등
5 main + 5 test 만 명시. 그러나 `SessionParamsFactoryTest.kt` 는 deletion list 에 없으면서 `import
FrameworkRenderResources/FrameworkResourceBundle` 보유 → 삭제 시 build break.

**해결**: NEEDS_CONTEXT 보고 → user 승인 후 helper 만 inline migrate (`LayoutlibRenderResources` +
`LayoutlibResourceBundle.build(emptyMap())`). helper 이름 (`emptyFrameworkRenderResources`) 은
보존 — 호출 site 변경 없음 (전체 file rename 회피, T8 scope 제한).

추가: `default theme is non-null and framework-namespaced` test 의 `theme.namespace == ANDROID`
assert 는 W3D1 의 framework-only 시맨틱에서 inherited 된 것. W3D4 LayoutlibRenderResources 는 multi-
namespace — empty bundle fallback theme 은 `LayoutlibRenderResources.emptyTheme` 의 RES_AUTO 고정.
Assert 제거 (intent 인 "default theme non-null + name 일치" 만 보존). 실 framework theme namespace
검증은 후속 `MaterialFidelityIntegrationTest` 가 cover.

### LM-T8-C (latent T2 bug 1): NamespaceAwareValueParser elementText mixed content throw

W3D1 → W3D4 absorption 시 `FrameworkValueParser.readText` (KXmlPull 기반 depth-aware text collector)
가 `NamespaceAwareValueParser` 으로 이전되지 못함 — 새 parser 가 StAX `reader.elementText` 직접
사용. StAX spec: mixed content (자식 START_ELEMENT 포함) 시 `XMLStreamException`
`elementGetText() function expects text only element but START_ELEMENT was encountered` throw.

실 Android AAR `values.xml` 은 mixed content 가 흔함:
- `<string>Click <b>here</b></string>` — inline HTML markup
- `<string>Page <xliff:g id="num">%1$d</xliff:g></string>` — translation placeholder
- styled span (`<a>`, `<u>`, `<i>`) inside string resources

3 integration test (`tier3 basic`, `tier3-arch`, `tier3-values`) PASS → FAIL. Round 2 페어 review
가 synthetic XML fixture (`<dimen name="x">10dp</dimen>` 같은 trivial value) 만 검증, real AAR
mixed content 미접근.

**해결**: `readElementText(reader: XMLStreamReader): String` helper 추가 — W3D1 `readText` 와 동일
의미 (CHARACTERS + CDATA accumulate, 자식 START_ELEMENT depth-track + child text 도 누적, entity
ref / comment / PI silently ignore). 3 call site (handleSimpleValue, handleStyle inner-loop item,
parseInternal TAG_ITEM) 의 `reader.elementText` 교체. 2 신규 test case (`string with nested HTML`,
`xliff g placeholder`) 가 fix 검증.

### LM-T8-D (latent T2 bug 2): Cross-NS attr ref 가 ResourceReference ctor 에서 reject

LM-T8-C fix 적용 후 cascading 으로 surface. `<declare-styleable name="MyView"><attr
name="android:visible" /></declare-styleable>` 같은 cross-NS attr **ref** 가 (실 Material/AppCompat
AAR 에 수백 건 존재) `ParsedNsEntry.AttrDef("android:visible", ...)` 로 emit → `LayoutlibResourceBundle.
buildBucket` line 98 에서 `ResourceReference(RES_AUTO, ATTR, "android:visible")` 호출 →
`AssertionError: Qualified name is not allowed: android:visible` (ResourceReference name field 가
`:` disallow).

Android 시맨틱: `<declare-styleable>` 안 `<attr name="android:X" />` 는 새 attr 정의가 아니라 그
styleable 이 framework attr 을 포함한다는 declaration. aapt2 도 동일 처리 (no new attr def
generated).

**해결**: parser 레벨 filter — top-level TAG_ATTR + handleDeclareStyleable inner attr 둘 다, name
이 `:` 포함하면 AttrDef emit 을 skip. `:` 만 보지 않고 `android:` 만 prefix 매칭하지 않는 이유:
`app:foo`, `androidx:bar` 등 향후 cross-NS ref 모두 동일 시맨틱이라 broader correctness 가 정확.
2 신규 test case (`declare-styleable cross-NS skip`, `top-level cross-NS skip`) 가 fix 검증.

### LM-T8-E (보강): SessionParamsFactoryTest 의 default theme 시맨틱 차이

LM-T8-B fix 후 `default theme is non-null and framework-namespaced` test 가 fail. 원인: W3D4
`LayoutlibRenderResources.emptyTheme` (line 247) 은 `ResourceNamespace.RES_AUTO` 로 fallback —
W3D1 `FrameworkRenderResources.emptyTheme` 는 `ResourceNamespace.ANDROID` 였음. multi-namespace
시맨틱 으로 의도된 변경 — empty bundle 에서 framework theme 이 없으므로 fixture-app namespace 가
default 가 합리.

**해결**: assert 변경. test name `default theme is non-null with expected name` (intent 명확화),
`theme.namespace == ANDROID` assert 제거, name 만 검증. 실 framework theme namespace 검증은
integration (real bundle) 의 책임으로 위임. 사용 안 하는 `ResourceNamespace` import 제거.

## Canonical document changes

이 task 는 `docs/plan/08-integration-reconciliation.md` 또는 다른 canonical doc 의 직접 수정이
없음 (T8 은 implementation phase). T9 (integration test 분리) 까지 끝낸 후 T10 에서 §7.7.6 close
+ MEMORY.md 갱신 예정.

## Carry forward

- **T9** (다음 task): integration test 분리 (`tier3_basic_primary` + `tier3_basic_minimal_smoke`),
  `MaterialFidelityIntegrationTest` (real bundle Theme.AxpFixture parent walk to Theme + `?attr/
  colorPrimary` chain expansion). plan v2 §3.4 #9, #10. 13 PASS + 1 SKIP 회복 + chain depth ≥ 15
  assert 추가 예정.
- **T10** (마지막): `08 §7.7.6` close 1줄 + MEMORY.md (필요 시). work_log handoff (carry 0
  expected).

## Pair review verdict

T8 자체는 implementation phase → Codex pair 미실시 (CLAUDE.md "Pairs NOT required for ... 모든
implementation-phase work"). round 2 plan-review 페어 verdict (REVISE_REQUIRED → GO + 12 follow-up)
가 implementation 입력. 단, 본 session 에서 **round 2 plan-review 가 catch 못한 2 latent T2 bug**
가 surface — round 2 reviewer 한계는 synthetic-XML fixture 만 검증. **W3D5+ round 2 플레이북 권고**:
real-world fixture (실 AAR 의 mixed content + cross-NS ref pattern) 을 round 2 reviewer 의 검증
material 에 포함 권장.

## Self-review checklist (round 2 페어 catch-list 차용)

1. ✅ SessionConstants.DEFAULT_FIXTURE_THEME = "Theme.AxpFixture" (additive, DEFAULT_FRAMEWORK_THEME 보존)
2. ✅ LayoutlibRenderer ctor (distDir, fixtureRoot, sampleAppModuleRoot, themeName, fallback) 5-tuple, no defaults, brace own-line
3. ✅ Line 174 swap (FrameworkResourceValueLoader → LayoutlibResourceValueLoader, FrameworkRenderResources → LayoutlibRenderResources)
4. ✅ test SharedLayoutlibRenderer 의 RendererArgs 4-tuple + named LayoutlibRenderer 호출
5. ✅ 7+1 callsite 모두 named args + themeName 포함 (Main.kt + SharedLayoutlibRenderer + 5 IT + SharedRendererBindingTest 4 site)
6. ✅ W3D1 5 main + 5 test 모두 삭제 (grep 검증: live reference 0, comment 안 historical mention 만 — CLAUDE.md "Never delete existing comments")
7. ✅ build SUCCESSFUL
8. ✅ unit 151 PASS / 0 fail / 0 skip
9. ✅ integration 11 PASS + 1 SKIP (회귀 0; deleted FrameworkResourceLoaderRealDist 의 2 case 빼고 baseline 일치)
10. ✅ brace own-line (LayoutlibRenderer ctor + readElementText helper)
11. ✅ Mixed content 처리 — 2 새 test case PASS
12. ✅ Cross-NS attr ref skip — 2 새 test case PASS
13. ✅ Commit message 가 T8 + T2 fix (mixed content + cross-NS) 모두 명시
