# W3D4-β Round 3 Pair Review (Codex + Claude)

날짜: 2026-04-30
대상: `docs/superpowers/plans/2026-04-30-w3d4-beta-plumbing.md` (plan v3 draft)
phase: planning / plan-revision (Codex pairing 정책 trigger 정확히 적용 — pre-implementation)
LM-G 회피: `codex exec --skip-git-repo-check --sandbox danger-full-access` (MEMORY.md feedback 준수)

---

## §1 디스패치 채널

| 채널 | 모델 / 모드 | 산출물 |
|---|---|---|
| Codex CLI (직접) | 최신 GPT @ xhigh effort, sandbox bypass | `/tmp/codex-round3-review.txt` |
| Claude subagent (Plan agent) | claude internal | bg agent `aa4ce26e1efb7f80d` 결과 |

각 채널은 서로의 산출물을 보지 못한 상태에서 동일 Q1-Q5 + Q6 open critique 에 답함 — convergence 자체가 신뢰 신호.

## §2 Verdict

| 채널 | 판정 | confidence |
|---|---|---|
| Codex | GO_WITH_FIXES | 0.86 |
| Claude | GO_WITH_FIXES | 0.78 |

**최종 reconciled verdict: GO** (5 line-level plan delta + 2 escalation note 적용 후).

## §3 Convergence map

| Q | 결론 | 양쪽 동의? | 비고 |
|---|---|---|---|
| Q1 root cause | RJarSymbolSeeder vs AarResourceWalker namespace mismatch 가 unique cause (StyleResourceValueImpl.getItem 의 strict ns+name match 확인) | ✓ 양쪽 javap 독립 검증 | 가장 확실 |
| Q2 RES_AUTO 통일 | 옳음 (AAPT non-namespaced ID 동일 정책 — Claude 가 0x7F030013 ID 동일성 직접 측정) | ✓ | N-bucket 은 W4+ scope |
| Q3 cross-type dedupe | "disjoint" 가정 틀림 (R.jar 측정: 578 dup name 발견), 그러나 동일 ID → first-wins 안전. Codex: per-type Map<name,id> 권장 | ✓ 양쪽 empirical | plan 수정 |
| Q4 color qualifier | base-only 충분 (m3_highlighted_text 는 default `res/color/` 존재) | ✓ | Codex 1.13.0/1.7.1 / Claude 1.12.0/1.6.1 — Claude 가 실제 build.gradle 버전 |
| Q5 getParser COLOR-only | layout/menu/drawable 에 회귀 없음 (prior null 동작 보존) | ✓ | |
| **Q6 SelectorXmlPullParser** | `ILayoutPullParser` 가 `getLayoutNamespace()` 도 require — plan 의 SelectorXmlPullParser 는 컴파일 안 됨 | ✓✓ **양쪽 독립 catch** | 최강 convergence |

## §4 Adopted plan deltas (5)

1. **§4.1 SelectorXmlPullParser**: `override fun getLayoutNamespace(): ResourceNamespace = RES_AUTO` 추가.
2. **§3.1 RJarSymbolSeeder + ResourceTypeFirstWinsGuard**: `Set<String>` → `Map<String, Int>` 로 강화 — 동명+동ID silent skip (정상 transitive ABI), 동명+다른ID loud WARN (회귀 신호).
3. **§2.2 census**: 1.13.0/1.7.1 → 실제 1.12.0/1.6.1, 236 → **192** color XML files.
4. **§1.4 collateral 설명**: "AAR간 disjoint" 가정 제거 → "widespread collision but same ID per AAPT non-namespaced" 정정.
5. **§5.1 IT BeforeEach**: `@BeforeEach { LayoutlibResourceValueLoader.clearCache() }` — JVM-wide cache stale 방지.

## §5 Escalation notes (2)

- **§5.4 T12.5 (drawable selector feed)**: T11+T12 적용 후 primary fail 위치가 "ColorStateList feed" 에서 "Drawable selector feed" 로 shift 시, DRAWABLE XML walker (analogous wiring) 를 plan v3.1 로 escalate. 본 plan 은 미리 spec 하지 않음 — empirical 측정 후 결정.
- **§5.4 Q6.5 (R$styleable)**: ConstraintLayout 의 `app:layout_constraintTop_toTopOf` 는 R$styleable 의존. RJarSymbolSeeder.kt:56-59 가 R$styleable skip — Bridge.parseStyleable 에 위임 가정. primary fail 시 stack trace 우선 검사.

## §6 Divergence (사소)

- 의존성 버전: Codex 가 Gradle cache 의 1.13.0/1.7.1 검사 vs Claude 가 build.gradle.kts 의 실제 1.12.0/1.6.1 검사 → Claude 수치 채택.
- Confidence: Codex 0.86 vs Claude 0.78 — Claude 가 T12.5 (drawable selector) 의 unknown unknown 을 더 강하게 weighted.
- judge round 불요 — convergence 가 압도적이고 divergence 가 censusing 수준의 사실 차이로 Claude 채택이 명확.

## §7 LM 적용

- LM-G: codex exec --skip-git-repo-check --sandbox danger-full-access 직접 CLI 사용 ✓
- LM-α-A: Codex / Claude 양쪽 모두 javap / unzip / grep 직접 측정 — claim 모두 file:line 인용 ✓
- LM-α-B: dual-channel verdict, single-source 회피 ✓
- LM-W3D4-D: plan 의 모든 fix 가 explicit diff (placeholder 없음) — round 3 review 가 명시적으로 검증 ✓

## §8 다음 단계

T11 → T12 → T13 순으로 subagent-driven implementation. 각 task 단위 commit + push (CLAUDE.md task-unit completion). 각 단계 완료 후 모듈 합산 test 재측정.
