# Handoff — W2D7 → W2D8 / W3D1 (Android XML Previewer)

> 다음 세션 cold-start 용 실용 안내.
> 먼저 `docs/W2D7-PAIR-REVIEW.md` (최종 페어) 와 `docs/plan/08-integration-reconciliation.md §7.7.2` (3b-arch / 3b-values 재분할) 를 읽고, 그 다음 본 문서.

---

## 0. TL;DR

- **W2D7 3b-arch GO** — `Bridge.createSession → RenderSession.render` 아키텍처가 실 layoutlib inflate 단계까지 도달. Tier3 `tier3-arch` 가 ERROR_INFLATION (config_scrollbarSize) 로 positive evidence.
- 신규 infra: `session/` 서브패키지 (LayoutPullParserAdapter, MinimalLayoutlibCallback, NoopAssetRepository, MinimalFrameworkRenderResources, SessionParamsFactory, SessionConstants).
- `implementation("com.android.tools.layoutlib:layoutlib-api:31.13.2")` 로 LayoutlibCallback/AssetRepository subclass 가능.
- 테스트: **61 unit + 5 integration + 2 SKIPPED (@Disabled W3 carry + 기존 activity_basic)**.
- 페어: Codex+Claude 플랜 페어 FULL CONVERGENCE → 4 follow-up 전량 반영.
- **다음 세션 즉시 시작점**: `W3-RESOURCE-VALUE-LOADER` — 프레임워크 리소스 VALUE loader (Paparazzi 급). 또는 `W3-CLASSLOADER-AUDIT` / `POST-W2D6-POM-RESOLVE` 중 user 선택.

---

## 1. 이 세션에서 달성한 것 (상세)

### 1.1 RenderSession 아키텍처 (§7.7.2 3b-arch — CLOSED)

**12개 신규 파일**:
- 1 fixture XML (`activity_minimal.xml`)
- 6 main Kotlin (`session/` 서브패키지)
- 4 test Kotlin (3 unit suites + 1 Tier3 integration)
- 1 docs (W2D7-PLAN-PAIR-REVIEW.md)

**5개 수정**: build.gradle.kts, LayoutlibBootstrap.kt, LayoutlibRenderer.kt, 08-integration-reconciliation.md, 플랜 markdown.

### 1.2 페어 리뷰 결과

- 플랜 단계: Codex xhigh + Claude Plan, FULL CONVERGENCE, GO_WITH_FOLLOWUPS. 4 follow-up (F1/F2/F3/F4) 전량 반영. 상세: `docs/W2D7-PLAN-PAIR-REVIEW.md`.
- 최종 단계: `docs/W2D7-PAIR-REVIEW.md` (별도 작성).

### 1.3 canonical 문서 갱신

| 파일 | 변경 |
|------|------|
| `docs/plan/08-integration-reconciliation.md` | §7.7 item 3b → 3b-arch [x] / 3b-values [ ] 분할. §7.7.2 신규 — W2D7 페어 리뷰 결과 + W3 carry 이관. |
| `docs/MILESTONES.md` | (갱신 필요 — W3 kickoff 시 반영) |
| `docs/W2D7-PLAN-PAIR-REVIEW.md` (신규) | 플랜 단계 페어 consolidation |
| `docs/W2D7-PAIR-REVIEW.md` (신규 — 최종 페어 후 작성) | 최종 페어 consolidation |
| `docs/work_log/2026-04-23_w2d7-rendersession/` (신규) | session-log.md + 본 handoff.md + next-session-prompt.md |

---

## 2. 다음 세션 즉시 시작 작업

### 2.1 환경 확인 — 2분

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
./server/gradlew -p server build                     # BUILD SUCCESSFUL
./server/gradlew -p server test                       # 61 unit PASS
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 5 PASS + 2 SKIPPED
java -jar server/mcp-server/build/libs/axp-server-0.1.0-SNAPSHOT.jar --smoke  # ok
```

### 2.2 우선순위 task 후보 — user 선택

#### A. `W3-RESOURCE-VALUE-LOADER` (§7.7.2 3b-values) — 가장 높은 canonical 가치

**목적**: Tier3 `tier3-values` 가 unblock 되어 실제 PNG pixel (TextView "Hello layoutlib" 글리프) 생성.

**작업 순서**:
1. `data/res/values/*.xml` 파일들을 파싱하는 `FrameworkResourceValueLoader` 작성. 파일별:
   - `config.xml` — `<dimen name="config_scrollbarSize">4dp</dimen>` 같은 트리비얼 값들.
   - `dimens_material.xml` — Material 테마 dimen.
   - `colors_material.xml` — Material 색상.
   - `themes_material.xml` — `Theme.Material.Light.NoActionBar` 의 `<item>` 들.
   - `attrs.xml` — (optional for minimal) 속성 정의.
2. `StyleResourceValueImpl` + `DensityBasedResourceValueImpl` / `ResourceValueImpl` 로 각 XML 값을 ResourceValue 객체화.
3. `MinimalFrameworkRenderResources` 확장: `getUnresolvedResource(ref)` / `getResolvedResource(ref)` / `getStyle(ref)` 가 loaded map 에서 lookup.
4. 구현 완료 시 `tier3-values` `@Disabled` 제거.

**Reference**:
- Paparazzi 의 `ResourceResolver.create(...)` 경로 (cashapp/paparazzi).
- Android Studio 의 `ResourceRepositoryManager` (비공개 API 이지만 알고리즘 참고).
- Predictable: `config_scrollbarSize` 가 우선 해결되어야 하고, 그 다음 TextView 의 `textAppearance` 체인 (attr→style→dimen/color) 이 해결되어야 함.

**예상 복잡도**: 500-1000 LOC. Paparazzi 는 ~1200 LOC. 본 프로젝트는 단일 fixture 대상이므로 최소화 가능.

#### B. `W3-CLASSLOADER-AUDIT` (§7.7.1 F-4)

W2D6 의 `createIsolatedClassLoader()` parent=system CL 결정을 W3 의 L3 DexClassLoader pipeline 과 호환 여부 검증. Paparazzi 의 "layoutlib-exclude-kotlin" 커스텀 로더 도입 여부 판단.

#### C. `POST-W2D6-POM-RESOLVE` (§7.7.1 F-6)

`layoutlib:14.0.11` 의 pom 을 resolve 하여 Guava/kxml2/ICU4J 의 canonical 버전으로 교체. 현재 pin 은 임시.

---

## 3. Landmines 기록 (추가)

### 3.1 ~ 3.5 — W2D6 에서 이관 (참조: 이전 handoff)

### 3.6 KDoc 내 `/*` 중첩 주석 (L1 — 재발)
W2D6 에서 이미 기록된 Kotlin 함정이 W2D7 에서 재발 (Tier3 테스트 KDoc). 향후 KDoc 내 glob 표기 완전 금지 정책. 린터 추가 후보.

### 3.7 Bridge 의 process-global static state (L2)
Bridge.sInit 등이 JVM-wide. integration test 간 격리 필요 — `forkEvery = 1L` on integration tag 로 해결. 다른 테스트가 Bridge.init 을 호출하면 Tier2 의 first-init assertion 이 실패할 수 있으므로 영구 유지.

### 3.8 RenderResources 의 `findResValue` override 금지 (L3)
null 반환 override 는 프레임워크 리소스 lookup 까지 가로채어 inflate 실패를 유발. `getDefaultTheme` 만 override.

### 3.9 `lastSessionResult` 의 createSession vs render 구분 (L4)
진단 hook 은 `lastCreateSessionResult` + `lastRenderResult` 로 분리. createSession 실패 시 원인이 그쪽.

### 3.10 Bridge.init 은 framework resource VALUE 를 로드하지 않음 (L5)
canonical 제약. §7.7.2 3b-values 로 이관.

---

## 4. Task 상태 (W2D7 종료 시점)

| ID | 상태 | 제목 |
|----|------|------|
| #1 | completed | W2D7 환경 확인 |
| #2 | completed | W2D7 플랜 + 페어 리뷰 |
| #3 | completed | SessionParams/HardwareConfig/Callback 스캐폴드 (TDD) |
| #4 | completed | LayoutlibRenderer renderViaLayoutlib 실 경로 교체 |
| #5 | completed | Tier3 integration test 강화 (`tier3-arch` PASS, `tier3-values` @Disabled) |
| #6 | **pending** | W2D7 최종 페어 리뷰 (W2D7-PAIR-REVIEW.md) ← 본 세션에서 진행 예정 |
| #7 | **in_progress** | work_log + handoff + next-session-prompt (본 문서 작성 중) |

### 신규 carry (W3 진입 시점)
- **`W3-RESOURCE-VALUE-LOADER`** (§7.7.2 3b-values) — 최우선
- 기존: `POST-W2D6-POM-RESOLVE` (F-6), `W3-CLASSLOADER-AUDIT` (F-4)

---

## 5. 긴급 회복 시나리오

### A. Gradle 빌드 깨짐
```bash
./server/gradlew -p server clean build --info 2>&1 | tail -60
```
kotlinVersion 드리프트 주의: `buildSrc/build.gradle.kts:26` ↔ `gradle/libs.versions.toml:kotlin`.

### B. Tier3 `tier3-arch` 갑자기 FAIL
- `status != ERROR_INFLATION/ERROR_UNKNOWN/ERROR_RENDER` 면 아키텍처 결함. `LayoutlibRenderer.lastCreateSessionResult` stderr 로 확인.
- `ERROR_NOT_INFLATED` → SessionParams 빌드가 실패 (`SessionParamsFactory` 재검토).
- `SUCCESS` → 3b-values 가 이미 구현됨. 테스트를 `tier3-values` 로 전환.

### C. Tier2 갑자기 FAIL (Bridge.init returns false)
- JVM 공유로 인한 "이미 초기화됨". `:layoutlib-worker/build.gradle.kts` 의 `setForkEvery(1L)` 확인.

### D. canonical 문서 정합성 의심
08 > 07 > 06 > 03. §7.7.2 (W2D7 페어) > §7.7.1 (W2D6 플랜 페어) > §7.7 (W1-END 최종 페어) 순.

---

## 6. 다음 세션 시작 프롬프트

`docs/work_log/2026-04-23_w2d7-rendersession/next-session-prompt.md` 참조.
