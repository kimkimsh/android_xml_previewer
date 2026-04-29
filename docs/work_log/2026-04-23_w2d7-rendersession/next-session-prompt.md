# Next Session Start Prompt (권장)

```
# Android XML Previewer — W2 Day 8 / W3 Day 1 이어하기

본 세션은 W2D7 3b-arch 게이트 FULL CONVERGENCE GO 직후입니다.

읽는 순서:
  1. docs/W2D7-PAIR-REVIEW.md (최종 페어)
  2. docs/plan/08-integration-reconciliation.md §7.7.2 (3b-arch / 3b-values 재분할)
  3. docs/work_log/2026-04-23_w2d7-rendersession/handoff.md (본 handoff)

환경 확인 (2분):
  ./server/gradlew -p server build
  ./server/gradlew -p server test
  ./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration
  java -jar server/mcp-server/build/libs/axp-server-0.1.0-SNAPSHOT.jar --smoke

작업 선택 (user 결정):
  A. W3-RESOURCE-VALUE-LOADER (§7.7.2 3b-values) — framework resource VALUE loader
     구현. data/res/values XML 파싱 → MinimalFrameworkRenderResources 확장.
     Tier3 `tier3-values` unblock 목표. 예상 500-1000 LOC.
  B. W3-CLASSLOADER-AUDIT (§7.7.1 F-4) — parent=system CL + L3 DexClassLoader 호환성.
  C. POST-W2D6-POM-RESOLVE (§7.7.1 F-6) — transitive pin → pom-resolved.

종료 시 페어 리뷰 실행 (Codex xhigh + Claude 1:1).
```
