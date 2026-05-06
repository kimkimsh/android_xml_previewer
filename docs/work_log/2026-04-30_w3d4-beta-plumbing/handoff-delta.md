# W3D4-δ implementation phase entry — handoff

날짜: 2026-05-06 (생성), 다음 세션 entry: W3D4-δ implementation (T17/T18/T19)
선행 head: `2478d78` (main, plan v3.2 + round 5 pair-review APPLIED)

---

## §1 어디까지 끝났나

W3D4-γ plumbing (T14/T15/T16) 완료 + W3D4-δ planning (plan v3.2 + round 5) 완료. **3 enum/flag warning 모두 닫힘**:

- T14 (γ-A.2 RES_AUTO path): ParsedNsEntry.AttrDef 의 enumValues/flagValues 필드 + NamespaceAwareValueParser child loop + LayoutlibResourceBundle.buildBucket addValue + getResource ATTR special-case (round 4 Q5 KILL POINT). commit `3ca8bb5`.
- T15 (γ-A.1 framework Bridge.init wiring): bundle.frameworkEnumValueMap() 신규 + LayoutlibRenderer.initBridge 의 enumValueMap inject. commit `1541958`.
- T16 partial (acceptance gate 측정): tier3-basic-primary 1회 IT 실행 → 의도 surface 3 warning ("vertical/center_horizontal/parent is not a valid integer") 모두 system-err 부재로 검증 ✓. 새 fail surface 발견 → re-`@Disabled` (W3D4-δ-A reason). commit `83d432a`.
- W3D4-δ planning: spec v3.2 작성 → 3-stream parallel investigation (subagent A/B/C, ThemeEnforcement bytecode + theme chain + widget call site) → round 5 pair-review (Codex 0.91 / Claude 0.88, GO_WITH_FIXES) → 12 deltas inline. commit `2478d78`.

acceptance gate (`activity_basic.xml` SUCCESS) 는 **여전히 fail** — 단 fail surface 가 enum/flag 가 아닌 **TextAppearance sentinel** 로 shift (LM-W3D4-β-H + LM-W3D4-γ-C 인계). plan v3.2 가 본 surface 닫는 design 확정 — 다음 세션 = implementation phase.

## §2 새 fail surface — W3D4-δ-A

### §2.1 증상 (T16 partial 측정)

```
[LayoutlibRenderer] createSession result: status=ERROR_INFLATION
  msg=This component requires that you specify a valid TextAppearance attribute.
      Update your app theme to inherit from Theme.MaterialComponents (or a descendant).
  exc=IllegalArgumentException
[LayoutlibRenderer] RenderSession.render failed: status=ERROR_NOT_INFLATED msg=null exc=null
→ IllegalStateException: LayoutlibRenderer 실패 + fallback 없음: activity_basic.xml
```

### §2.2 Trigger (subagent A/B/C 의 empirical 검증, plan v3.2 §1.1-§1.5)

`activity_basic.xml:43-48` 의 `<com.google.android.material.button.MaterialButton id="@+id/primary_button">` (다른 elements 는 framework / 직접 호출 없음). `MaterialButton.<init>(Context, AttributeSet, int)` 의 bytecode offset 53 가 `ThemeEnforcement.obtainStyledAttributes(...)` 호출 → `checkTextAppearance` (te_disasm.txt:158-239) 가 `R.styleable.ThemeEnforcement_android_textAppearance` (slot 0 alphabetical) `getResourceId(slot, -1) != -1` 검사 → -1 반환 → throw.

전체 sentinel surface (subagent A census, 7 attrs):
| # | attr | namespace | role |
|---|---|---|---|
| 1 | `enforceMaterialTheme` | RES_AUTO | gate for `checkMaterialTheme` |
| 2 | `enforceTextAppearance` | RES_AUTO | gate — false 시 throw 우회 |
| 3 | **`android:textAppearance`** | ANDROID | **본 phase 의 직접 trigger** |
| 4 | `isMaterialTheme` | RES_AUTO | indirect (checkMaterialTheme 진입) |
| 5 | `isMaterial3Theme` | RES_AUTO | feature gate (no throw) |
| 6 | `colorPrimary` | RES_AUTO | W3D4-β 닫힘 |
| 7 | `colorPrimaryVariant` | RES_AUTO | next escalation (δ-B) |

### §2.3 Root cause hypothesis (plan v3.2 §2)

Dominant 가설 H2 — `LayoutlibResourceBundle.getResource(STYLE ref)` 의 byType-only 경로가 styles map 도달 못 함 (W3D4-γ T14 round 4 Q5 ATTR special-case 의 STYLE 변종). chain walker 가 `?attr/textAppearanceButton` chain 또는 `materialButtonStyle` chain 의 STYLE-ref hop 단계에서 unresolved StyleItem 그대로 반환 → BridgeContext offset 312-325 의 instanceof StyleResourceValue cast fail → defStyleRes M2 fallback → throw.

기타 가설: H1 (chain walker dot-trim, 확률 낮음 — MaterialFidelityIntegrationTest:56-59 chain depth ≥ 15 baseline green), H3 (R$style symbol seed gap, T19 fail 후 §5.3.1 instrumentation), H4 (macro reference, 확률 낮음).

### §2.4 Fix shape (plan v3.2 §5.1, dominant)

```diff
 fun getResource(ref: ResourceReference): ResourceValue?
 {
     val bucket = byNs[ref.namespace] ?: return null
     if (ref.resourceType == ResourceType.ATTR)
     {
         return bucket.attrs[ref.name]
     }
+    if (ref.resourceType == ResourceType.STYLE)
+    {
+        return bucket.styles[ref.name]
+    }
     return bucket.byType[ref.resourceType]?.get(ref.name)
 }
```

3-line 변경이 BridgeContext (M3 path defStyleAttr resolution) + BridgeTypedArray (M2 path `?attr/textAppearanceButton` resolution) 양쪽의 instanceof StyleResourceValue gate 동시 enable. 단 H2 의 coverage 는 *item discovery 후의 STYLE-ref hop* 만 — item discovery (findItemInTheme) 자체 fail 시 §5.2 walkParent fallback 별도 commit (round 5 reconcile).

## §3 다음 세션 진입 단계

### §3.1 cold-read 권장 docs (이 순서)

1. **`docs/work_log/2026-04-30_w3d4-beta-plumbing/handoff-delta.md`** (본 파일)
2. `docs/superpowers/specs/2026-05-04-w3d4-delta-textappearance-sentinel-design.md` (plan v3.2 — T17/T18/T19 의 explicit code/diff 포함, round 5 reconcile 12 deltas inline)
3. `docs/work_log/2026-04-30_w3d4-beta-plumbing/round5-pair-review.md` (round 5 verdict + convergence map)
4. `docs/work_log/2026-04-30_w3d4-beta-plumbing/t16-acceptance-gate-followup.md` (W3D4-δ-A surface 발견 시점)
5. `docs/work_log/2026-04-30_w3d4-beta-plumbing/session-log.md` (β/γ/δ planning 산출 + W3D4-δ append + LM δ-A~D)
6. `docs/superpowers/plans/2026-04-30-w3d4-gamma-attr-enum-flag.md` (γ plan v3.1 — 자료구조/RES_AUTO ATTR 패턴 background, δ 가 STYLE 변종)

### §3.2 본 세션 첫 작업 = T17 → T18 → T19 implementation

#### T17 — Diagnostic 5-question battery (1 commit, IT-tagged)

신규 파일 `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/resources/W3D4DeltaThemeChainDiagnosticTest.kt` — plan v3.2 §4.1 의 코드를 그대로 사용 (round 5 Q4 fix 적용 후 compile 가능). 5 question:
- H1 (Lvl 5 reachable by name) · H2 (getResource STYLE ref) · H3a (findItemInTheme materialButtonStyle) · H3b (findItemInTheme textAppearanceButton) · H2-bridgeTypedArray-gate (resolveResValue StyleItem → is StyleResourceValue)

`@Tag("integration")` — default unit suite 제외 (axp.kotlin-common.gradle.kts:43-50). RED-on-main 가능 (LM-W3D4-δ-D 인계).

```bash
cd server && ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain --tests "dev.axp.layoutlib.worker.resources.W3D4DeltaThemeChainDiagnosticTest"
```

5 question 의 PASS/FAIL 패턴이 plan v3.2 §5.6 의 결정 매트릭스에 mapped — T18 의 fix 범위 즉시 결정.

#### T18 — H2 fix (plan v3.2 §5.1) 적용 (1 commit, dominant 예상)

3-line 변경 `LayoutlibResourceBundle.getResource` + 회귀 테스트 `LayoutlibResourceBundleStyleLookupTest.kt` 신규 4 cases (plan §5.1). 추가로 §5.5 의 bootstrap one-shot summary log (DEBUG_BUNDLE_SHAPE gated) — 본 commit 에 포함 가능.

T17 재측정 시 H2 + H2-bridgeTypedArray-gate 둘 다 PASS 로 전환. H3a/H3b 도 PASS 인지 확인 — fail 시 §5.2 walkParent fallback 별도 commit.

#### T19 — Acceptance gate close (1 commit, IT 측정)

`server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/LayoutlibRendererIntegrationTest.kt` 의 `@org.junit.jupiter.api.Disabled(...)` 제거 → `tier3 basic primary` IT 실행. PASS 시 W3D4-δ-A close, fail 시 §6.3 escalation:

- W3D4-δ-B: `enforceMaterialTheme → checkMaterialTheme → colorPrimaryVariant` gate chain
- direct-sentinel surface: BadgeDrawable / BaseTransientBottomBar (현 fixture 외)
- W3D4-ε: R$styleable layer (RJarSymbolSeeder.kt:64-66 skip) — §5.3.1 instrumentation 으로 narrow

각 task 완료 시 즉시 commit + push (CLAUDE.md task-unit completion). T17 → T18 → T19 순.

## §4 회피해야 할 LM (carry from W3D4-β/γ + δ planning 신규)

| LM | 회피 방법 |
|---|---|
| LM-W3D4-β-D | KDoc 안 path 표기 시 `/*` 패턴 회피 (e.g. `res/color/<name>.xml` 또는 backtick 인용) |
| LM-W3D4-β-E | `assertNotNull(...)` 의 반환값 chain 사용 금지 → `val v = ...; assertNotNull(v); v!!.method()` 패턴 |
| LM-W3D4-β-F | IT 실행 시 반드시 `-PincludeTags=integration` 명시 |
| LM-W3D4-β-G | subagent dispatch 시 `unzip --directory /tmp/...` 명시 — cwd 오염 회피 |
| LM-W3D4-β-H | acceptance gate fail 시 stack trace 우선 검사 → fail surface 가 spec 한 layer 인지 새 layer 인지 분류 |
| LM-W3D4-γ-A | reviewer prompt 에 "bundle 의 lookup path trace" 명시 (round 5 자체가 본 LM 적용 → δ-A 의 H2 KILL POINT catch) |
| LM-W3D4-γ-B | `Long.decode(...).toInt()` 로 32-bit unsigned hex literal 처리 (parseAttrValueLiteral 영구) |
| LM-W3D4-γ-C | ThemeEnforcement multi-sentinel census — δ-A planning 이 본 LM 적용 (7 attr 한 번에 census) ✓ |
| **LM-W3D4-δ-A (신규)** | spec 코드 sample 의 모든 함수/필드/타입 호출은 grep/Read 로 사전 verify — fabricated reference 금지. round 5 의 PathLocator/sampleAppModuleRoot fabrication 양쪽 catch 가 trigger |
| **LM-W3D4-δ-B (신규)** | NsBucket 의 byType ↔ styles ↔ attrs 3-way 분리에서 `getResource` 가 byType 만 보는 패턴 = type 별 sibling KILL POINT. 향후 NsBucket 새 type-specific map 추가 시 동일 회귀 위험 — code review checklist |
| **LM-W3D4-δ-C (신규)** | LayoutlibResourceValueLoader 가 runtime-classpath.txt 부재 시 silent empty AAR list — false-PASS 위험. T17 의 graceful skip (assumeTrue) 이 본 phase 회피, W4+ hardening pass 권장 |
| **LM-W3D4-δ-D (신규)** | T17 IT-tagged 결정 — RED-on-main 가능 (default unit suite 제외 via axp.kotlin-common.gradle.kts:43-50). T17 commit 시 본 결정 명시 |
| **LM (CLAUDE.md Three Hard Rules for Comments)** | 신규 코드 의 KDoc/inline 모두 영문 only, function/structure 만, ticket reference 최소화 |

## §5 출발 지점 환경 sanity

```bash
$ cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
$ git log --oneline -5
2478d78 docs(w3d4-delta): plan v3.2 + round 5 pair-review (GO_WITH_FIXES → APPLIED)
83d432a feat(w3d4-gamma): T16 partial — γ-A target warnings closed + W3D4-δ escalation
1541958 feat(w3d4-gamma): T15 framework Bridge.init enumValueMap wiring
3ca8bb5 feat(w3d4-gamma): T14 AttrDef enum/flag value capture + RES_AUTO ATTR exposure
4a31771 docs(w3d4-gamma): plan v3.1 attr enum/flag capture + round 4 pair-review

$ cd server && ./gradlew test --console=plain | tail -3
BUILD SUCCESSFUL — 237 unit total / 0 fail (16 protocol + 5 http-server + 22 mcp-server + 194 layoutlib-worker).

$ ./gradlew :layoutlib-worker:test -PincludeTags=integration --console=plain | tail -3
BUILD SUCCESSFUL — 14 IT PASS + 2 SKIP (tier3-basic-primary W3D4-δ-A carry, tier3-glyph W4 carry).
```

T17 unzip 산출물 (LM-W3D4-β-G 회피용 — round 5 investigation 잔존):
- `/tmp/w3d4d-themeenforcement/` (Material AAR + ThemeEnforcement.class disasm)
- `/tmp/w3d4d-themechain/{material,appcompat}/` (양 AAR values.xml)
- `/tmp/w3d4d-widgets/` (MaterialButton.class disasm)

다음 세션에서 동일 verify 필요 시 재unzip 가능 — 본 폴더 보존 안 됨 (cleanup 정책 무관).
