# W3D4 T7 — RJarSymbolSeeder canonicalization (R-1) + duplicate attr ID guard (R-2)

Date: 2026-04-30
Scope: W3D4 plan v2 §7.1 (R-1) + §7.2 (R-2) — round 2 페어 verdict (Q2 FULL converge DISAGREE)

---

## Outcome

W3D3-α `RJarSymbolSeeder` 가 R$style 클래스 walk 시 underscore name (`Theme_AxpFixture`)
으로 emit 했으나 T6 `LayoutlibRenderResources` 가 dot name (`Theme.AxpFixture`) 으로
lookup → ID 역참조 mismatch. 또한 multiple R class (appcompat / material 등) 가 동명
attr (`colorPrimary`) 를 다른 ID 로 등록 시도 → layoutlib 가 잘못된 ID 받아 lookup 실패.

본 task 가 두 결함을 R-1 (style underscore→dot canonicalization) + R-2 (cross-class
first-wins attr guard) 로 해소.

핵심 변경 — single callback API `(ResourceReference, Int) -> Unit` 보존, `seedClass()`
호출 시 type 분기로 STYLE 만 canonicalize, ATTR 만 dedup. `seenAttrNames: HashSet`
은 `seed()` outer scope 에서 ZipFile 단위 (한 R.jar 의 모든 R$attr 클래스 공유).

## Files

### Created
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RNameCanonicalization.kt`
  — `internal object`. `styleNameToXml(rFieldName)` (underscore→dot 일괄) +
  `attrName(rFieldName)` (passthrough) + `simpleName(rFieldName)` (passthrough).
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/AttrSeederGuard.kt`
  — `internal object`. `tryRegister(name, id, sourcePackage, seen): Boolean` —
  `seen.add(name)` 으로 first-wins, dup 시 `[RJarSymbolSeeder] dup attr '$name' from $sourcePackage (id=0x...) — first-wins, skipped` stderr 1줄.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RNameCanonicalizationTest.kt`
  — 3 case: style underscore→dot, attr underscore 보존, style edge case (변환 없음).
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RDuplicateAttrIdTest.kt`
  — 3 case: dup attr 진단 + skip, 다른 attr 모두 등록, 같은 R class 안 동명 first-wins.

### Modified
- `server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeeder.kt`
  — `seed()` 본문에 `seenAttrNames` outer-scope 추가, `seedClass()` 인자에 전달.
  field emit 직전에 `if (type == STYLE)` 분기로 `RNameCanonicalization.styleNameToXml()`,
  `if (type == ATTR)` 분기로 `AttrSeederGuard.tryRegister()` (실패 시 emit skip).
  caller (`LayoutlibRenderer.seedRJarSymbols`) signature 변경 없음 — single callback 보존.
- `server/layoutlib-worker/src/test/kotlin/dev/axp/layoutlib/worker/classloader/RJarSymbolSeederTest.kt`
  — 4 → 5 case 확장. e2e 회귀 case: `Theme_AxpFixture` field 가진 R$style mock jar
  → emit name 이 `Theme.AxpFixture` 로 변환, underscore name 은 emit 안 됨 검증.

## Test results

- `RNameCanonicalizationTest`: 3 PASS
- `RDuplicateAttrIdTest`: 3 PASS
- `RJarSymbolSeederTest`: 5 PASS (4 기존 + 1 신규 e2e)
- `:layoutlib-worker:test` 전체: **176 PASS** (T1-T6 baseline 169 → 176, +7 신규)
- 전체 server suite: BUILD SUCCESSFUL, 회귀 0

## Landmines

- **single callback API mismatch** (round 2 페어 verdict): plan v1 의 두 callback 분기
  안 (`registerStyle` / `registerAttr` ...) 으로 분리하면 caller 인 `LayoutlibRenderer`
  break. v2 round 2 fix 는 single callback 보존 + `seedClass()` 안에서 type 분기로 해소.
- **seenAttrNames scope** — `seedClass()` 안에서 만들면 R$attr 가 클래스마다 새로 시작 →
  cross-class dedup 안 됨. 반드시 `seed()` outer scope (ZipFile 단위) 에 위치해야 함.
- **STYLE 만 canonicalize**: dimen/color/bool/string 의 underscore 는 의미 있는
  separator (e.g., `max_lines`, `text_size_default`) → 보존 필수.
- **ATTR 만 dedup guard**: style/dimen/color 등은 namespace 가 R class 별 다르므로
  `ResourceReference(namespace, type, name)` 자체로 동치 충돌 없음. ATTR 만 layoutlib
  가 namespace 무시하고 name 으로 lookup → cross-package dedup 필요.

## Self-review

1. 7 신규 case 모두 PASS — verified
2. 기존 4 RJarSymbolSeederTest 회귀 없음 — verified (now 5 PASS)
3. `:layoutlib-worker:test` 176 PASS green — verified
4. Single callback API `(ResourceReference, Int) -> Unit` 보존 — `LayoutlibRenderer:259` 그대로
5. `seenAttrNames` outer scope (ZipFile 단위) — `seed()` 본문 line 42
6. STYLE name canonicalization 적용 — `seedClass()` line 121-128
7. ATTR first-wins guard 적용 — `seedClass()` line 132-138
8. `RNameCanonicalization`, `AttrSeederGuard` 모두 `internal object` — verified
9. Brace own-line — preserved (T1-T6 일관)
10. Backtick test name 안 마침표 없음 (LM-α-D) — em-dash / paren / arrow 만 사용
11. ASM 의존성 — 기존 `RJarSymbolSeederTest` 가 이미 ASM 사용 → `makeRClass` helper 재활용

## Carried forward

- T8: ConfigurationFactory v2 (Q4 σ FULL converge — Material Components 권장 config).
- T9: SessionParamsFactory rewiring (T6 `LayoutlibRenderResources` injection).
- T10: 통합 회귀 — sample-app 실제 render → Theme.AxpFixture 가 dot name lookup 으로
  inflater 도달 (e2e 검증).

## Round 2 페어 verdict reference

`docs/work_log/2026-04-29_w3d4-material-fidelity/round2-pair-review.md` Q2 FULL
converge — single callback API mismatch + seenAttrNames outer-scope 명시.
