# Phase 6 최종 페어 리뷰 — Codex xhigh ↔ Claude 서브에이전트

> **상태**: 리뷰 완료 + 결정 반영 완료 (2026-04-22)
> **결정 결과**: §4의 L3 옵션 중 **α 채택** → 세부 반영은 [`06-revisions-and-decisions.md`](./06-revisions-and-decisions.md) 참조
> **대상**: 본 `docs/plan/` 디렉토리 전체
> **페어**: Codex xhigh (sandbox=danger-full-access, 약 2.5분 thinking) + Claude 서브에이전트 (일반 general-purpose, 약 90초)
> **포맷**: 엔지니어링 5문항(E1-E5) + DX 5문항(D1-D5) + VERDICT

---

## 1. VERDICT 비교

| | Codex xhigh | Claude 서브에이전트 |
|---|---|---|
| **Ready?** | **NO** | YES-WITH-FIXES |
| **Showstopper** | **L3가 claimed +10% 커버리지를 제공할 수 없음** (harness APK가 타겟 앱의 custom view 클래스에 접근 불가) | 없음 |
| **Top 3 Fixes** | L3 classpath/app-artifact 전략 재설계 / versioned API 계약 (RenderRequest/Response/SSE/에러 카탈로그/취소/스테일) / 구체적 온보딩 state machine (SSH/headless 포함) | cache key 확장 (fonts/JVM/aapt2/SDK/경로) / 에러 카탈로그 (Unrenderable enum) + per-layout-key mutex / `axp render --out foo.png` CLI를 v1으로 승격 |

**해석**: 두 리뷰 모두 **같은 사실**을 관찰했지만 **심각도 판단이 다름**. Codex는 "L3 아키텍처 자체가 결함"으로 판단, Claude는 "편집 가능한 3개 결함"으로 판단.

---

## 2. 양쪽이 강하게 일치한 발견

### 🔴 Critical (반드시 반영)

#### F1. L3 Escalation이 실제로는 동작하지 않을 수 있음
- **Codex E3**: `ClassNotFoundException -> L3`는 무효. L3 harness APK는 사전 빌드되어 **타겟 앱의 클래스/AAR/생성 코드에 접근 권한 없음**. custom view `com.acme.FancyChart`는 L3에서도 `ClassNotFoundException` 재발. 데이터바인딩 `@{...}`도 생성된 binding 클래스가 필요하므로 동일.
- **Claude E3**: 데이터바인딩에 L3 가는 건 "보장된 실패에 2-4초 낭비"라며 같은 이슈 지적.
- **결론**: v1 설계 최대 결함. **반드시 사용자 결정 필요** (아래 §4 참조).

#### F2. Cache key가 determinism 보장 못함
- 양쪽 모두 누락 항목 나열: `layoutlib.jar sha256`, `aapt2 version`, `Android SDK build-tools version`, `bundled font hash`, `JVM vendor+major`, `normalized relative path`, `locale/line-endings`, `night mode`, `font scale`, `API level`, `harness APK hash`, `emulator system image fingerprint`.
- Codex: "Reboots may be fine; OS/JVM/SDK/font changes are not."
- Claude: "Ubuntu vs Fedora → different glyph metrics → wrong cache hit."
- **결론**: `01-architecture.md §7`의 cache key formula 즉시 확장 필요.

#### F3. "Unrenderable: reason"이 free-form string이라 actionable하지 않음
- 양쪽 모두 **에러 카탈로그 (enum)** 필요 주장.
- Codex D3: "no stable code, category, likely cause, user action, docs link, retryability, or machine-readable failure class."
- Claude D3: `UnrenderableReason` sealed enum + localized message + remediation URL 필요.
- **결론**: `RenderResult.Unrenderable(reason: String)`을 `Unrenderable(reason: UnrenderableReason, detail: String)`으로 리팩토링 필요.

### 🟡 High (v1 전 반영 권고)

#### F4. SSH/Headless 경로가 1st-class가 아님
- 양쪽 모두 "원격 dev 머신 페르소나는 primary로 명시했는데 (`00-overview.md §3.1`), 해결책은 SSH forwarding 문서화로 떠밀어놓음"이라고 지적.
- Claude D2: `axp render --out foo.png` CLI 커맨드를 **v1.5 → v1으로 승격**.
- Codex D2: "v1에 headless-first 경로 필요. `--host/--port`, 자동 browser open 지양, PNG path 출력, port forwarding docs 포함."
- **결론**: v1 범위에 CLI mode 추가 (+1-2일 작업).

#### F5. 문서 surface 부족
- Codex D4 + Claude D4 교집합: `CONTRIBUTING.md`, `ARCHITECTURE.md` (contributor용), `CHANGELOG.md`, `SECURITY.md`, `PRIVACY.md`, 제거/uninstall 가이드, 지원 매트릭스, `THIRD_PARTY_NOTICES.md`, "Why not Paparazzi?" FAQ.
- **결론**: `03-roadmap.md` Week 4의 "문서화" 항목 확장.

#### F6. 네이밍 "axp" 충돌
- Codex D5: npm/PyPI에 이미 `axp` 패키지 존재 (Agentic Experience Platform, axp.systems). GitHub에 `jesselsookha/android-xml-previewer` 및 관련 리포 23개.
- Claude D5: PyPI의 AXP20x (embedded PMIC). 임베디드+Android 크로스 페르소나에서 충돌.
- **결론**: 퍼블릭 브랜드는 `android-xml-previewer` 유지, **CLI 바이너리명은 `axprev` 또는 `alx`로 rename** 권고.

### 🟢 Medium (완화 가능)

#### F7. Concurrency / Lifecycle 모호
- **Codex E2**: "stale-render prevention, cancellation, render queue policy, 브라우저 state model 없음. cache invalidation 자기 모순 (§3.3 dependency vs §6 conservative)."
- **Claude E2**: "two rapid saves → two parallel aapt2 link → corrupted .arsc. per-key mutex 필요."
- **결론**: `01-architecture.md §3.1`에 동기화 모델 명시 (per-layout-key single-flight mutex + cancellation semantics).

#### F8. Claude Code 크래시 시 orphaned JVM/AVD
- 양쪽 모두 "MCP 서버 lifecycle이 `hooks.json (선택)`으로만 처리되어 있음. Claude Code가 크래시하면 JVM + warm AVD 좀비."
- **결론**: PID/lockfile + health check + 재시작 시 cleanup 로직 필요.

---

## 3. 양쪽이 다르게 본 지점 (Divergence)

| 지점 | Codex | Claude |
|---|---|---|
| L3 상태 | "Architecturally broken — showstopper" | "Vague reason string, but fixable" |
| 4주 스케줄 | "Prototype 가능, release-quality NO" | "YES but L3 slip likely, escape hatch 활용" |
| "blank View heuristic" (L1→L3) | "Space/ViewStub/invisible에서 false-positive" | 지적 안 함 |
| JVM 버전 hash | 명시 | 명시 |
| `axp` 패키지 충돌 | npm/PyPI 실체 확인 완료 | PyPI 추측 |

Codex의 판단이 더 엄격. Claude 서브에이전트는 Codex가 잡은 L3 classpath 이슈를 덜 심각하게 취급.

---

## 4. 결정이 필요한 이슈 (User Decision Required)

### 🚨 L3 Classpath 문제 (showstopper per Codex)

**문제**: 플러그인이 번들하는 prebuilt harness APK는 다음에 접근할 수 없음:
- 사용자 앱의 custom view 클래스 (`com.acme.FancyChart`)
- Gradle-생성 DataBinding 클래스 (`ActivityMainBinding`)
- 사용자 앱의 dependency AAR의 리소스/코드
- Manifest에 merge된 AppCompat/Material 테마 override
- Build flavor/variant별 다른 리소스

즉, "L1 실패 → L3 성공"의 근거가 약함. L3에서도 **같은 ClassNotFoundException이 발생**. 이는 `02-alternatives-and-decision.md §3.2`의 "85% 커버리지는 L3 덕분"이라는 서사를 흔든다.

**해결 옵션**:

#### 옵션 α: L3를 classpath-aware로 재설계
- harness APK 대신 사용자 프로젝트의 **이미 빌드된 APK**를 AVD에 설치
- 그 APK에 포함된 Activity를 가로채거나, `Instrumentation` API로 XML을 dynamic inflate
- 또는 harness APK + `DexClassLoader`로 사용자 앱 DEX를 런타임 주입
- **영향**: v1 스코프 +2주 이상. Paparazzi가 왜 Gradle에 종속되는지의 이유를 일부 받아들이게 됨

#### 옵션 β: L3의 커버리지 주장을 수정
- L3는 **layoutlib이 JVM 버그로 죽는 경우**의 재시도용으로만 사용
- custom view/data-binding이 포함된 레이아웃은 **L3도 Unrenderable로 분류**
- 85% 주장은 유지하되, "custom view 없는 85%"로 정정
- **영향**: 커버리지 숫자 정직화. R2 리스크 재평가 필요.

#### 옵션 γ: 이전 페어 권고로 돌아가기 (Approach A)
- L3 전체 제거, L1 layoutlib + L2 aapt2 only
- v1 커버리지 70-75%로 정직화
- **영향**: 2주 스코프, 가장 안전. 사용자가 이미 한 번 거절한 안.

#### 옵션 δ: 하이브리드 — v1.0은 옵션 β, v1.1부터 옵션 α
- v1.0 릴리스: L3 보수적 사용 (layoutlib 버그 재시도용), custom view = Unrenderable
- v1.1: harness APK + DexClassLoader 주입으로 classpath-aware
- **영향**: v1 일정 유지, custom view 커버는 v1.1로 이동

---

## 5. 즉시 반영 가능한 수정 사항 (no user decision needed)

이것들은 양쪽 페어가 일치했고 사용자 결정 없이도 plan에 반영 가능:

- [ ] **01-architecture.md §3.1**: `Unrenderable(reason: String)` → `Unrenderable(reason: UnrenderableReason, detail: String)` 에러 카탈로그 도입
- [ ] **01-architecture.md §3.1**: per-layout-key single-flight mutex + cancellation semantics 명시
- [ ] **01-architecture.md §7**: cache key에 `layoutlib.jar sha256 + aapt2 version + SDK build-tools + JVM major + bundled font hash + normalized relative path + night mode + locale + font scale` 추가
- [ ] **03-roadmap.md Week 4**: CLI mode (`axprev render path.xml --out foo.png`)를 v1 범위로 승격 (+1-2일)
- [ ] **01-architecture.md §directory + 03-roadmap.md Week 4**: 문서 surface 확장 (CONTRIBUTING / ARCHITECTURE / CHANGELOG / SECURITY / PRIVACY / THIRD_PARTY_NOTICES / uninstall guide / "Why not Paparazzi" FAQ)
- [ ] **전체**: "axp" CLI 바이너리명을 `axprev`로 변경 (플러그인 이름은 `android-xml-previewer` 유지)
- [ ] **01-architecture.md §6 / §7**: cache invalidation 정책을 통일 (`§3.3 dependency` vs `§6 conservative` 모순 해결)
- [ ] **04-open-questions-and-risks.md**: R1에 "L3 cold/warm 추정치" 구체화 (Codex의 "최소 2-3초" 검증 데이터 필요)

---

## 6. Codex 원문 요약 (핵심 발췌)

```
### E1 Not buildable as release-quality v1
Week 3 gives one engineer five days for harness APK, AVD discovery,
snapshot boot, adb orchestration, timeout/retry/error UX, and dispatcher
validation. EmulatorRenderer.kt is most likely to slip.

### E3 Escalation rules are not coherent
ClassNotFoundException -> L3 is invalid as written. The L3 harness is a
prebuilt APK that only receives an XML path. It does not load the target
app APK, generated classes, dependency AARs, custom view bytecode, or
BindingAdapters. So the exact classpath failures L3 is supposed to fix
will still fail.

### VERDICT
Single showstopper: L3 cannot deliver the claimed +10% coverage without
loading target app code, generated binding code, dependency classes, and
merged resources.
```

## 7. Claude 서브에이전트 원문 요약 (핵심 발췌)

```
### E1 NO, not buildable in 4 weeks.
L3 EmulatorRenderer + harness APK + AVD warm-pool (Week 3).
Reality: drawToBitmap on arbitrary inflated XML dies on SurfaceView,
WebView, MapView, hardware-accelerated canvas — not mentioned anywhere.
Week 3 will bleed into Week 4 and kill D18 sampling verification.

### VERDICT
Ready? YES-WITH-FIXES. 3 concrete bugs (cache key, error catalog,
concurrency) will bite in Week 1-2 execution.
```

---

## 8. 다음 단계

1. **사용자가 §4 L3 옵션 α/β/γ/δ 중 하나 선택**
2. 선택 후 `00-overview.md`의 커버리지 문구 업데이트
3. §5 즉시 반영 사항을 plan 파일들에 반영 (사용자 승인 즉시)
4. `README.md`의 Status를 DRAFT → REVIEWED로 변경
5. 구현 Week 1 시작

---

## 9. 변경 로그
- 2026-04-22: Codex xhigh + Claude 서브에이전트 페어 최종 리뷰 완료. L3 classpath 이슈가 showstopper로 식별됨.
