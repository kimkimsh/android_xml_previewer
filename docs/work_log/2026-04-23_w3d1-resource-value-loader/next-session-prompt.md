# W3D2 Next-Session Prompt

다음 세션 시작 시 아래 프롬프트 중 하나를 복사해서 사용.

---

## Option A (권장) — W3 Day 2+ 스코프 진입

```
docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md 를 먼저 읽고,
Option A 경로로 이어서 진행해줘. W3D1 의 pair-review (플랜 + 구현) 는 이미 모두 완료.

deferred carry 중 하나 선택:
- CLI-DIST-PATH (Codex F3): MCP server/mcp-server/.../Main.kt 의 layoutlib-dist/android-34
  하드코딩 제거 + CLI 인자 지원.
- TEST-INFRA-SHARED-RENDERER (Codex F4): LayoutlibRendererIntegrationTest.kt:26 을 tier3 의
  companion.sharedRenderer 패턴으로 전환하여 L4 masking 제거.
- LayoutlibRenderer default parameter 정리: fallback/fixtureRoot default 제거 +
  AxpServer/CLI 에서 명시 주입.

또는 MILESTONES.md 에서 Week 2 → Week 3 전환 체크박스 반영 후 sample-app unblock 스코프
(ConstraintLayout / MaterialButton 커스텀 view 지원) 로 이동.

Codex CLI 호출 시 반드시 --skip-git-repo-check 플래그 (L6, handoff 참조).
에이전트 팀과 스킬 적극 활용. CLAUDE.md 규약 준수.
```

## Option B — tier3-glyph 선행 탐색 (W4 target)

```
docs/work_log/2026-04-23_w3d1-resource-value-loader/handoff.md 읽고 Option B 경로.

tier3-glyph 를 unblock 할 수 있는지 탐색:
- server/layoutlib-worker 에서 layoutlib 이 이미 SUCCESS 로 PNG 를 반환 중이므로
  해당 PNG 에 글리프가 나타나는지 먼저 확인 (수동 dump 가능).
- Font wiring / StaticLayout / Canvas.drawText JNI 경로 조사.
- 가능하면 T2 gate 테스트 (@Disabled 해제 + dark pixel >= 20) unblock.

시간이 남으면 작업 결과를 docs/work_log/YYYY-MM-DD_w3dN-tier3-glyph/ 로 journaling.
```

## Option C — W3 Day 2 스코프 브레인스토밍

```
docs/plan/00-roadmap-and-initial-questions.md 와 docs/plan/07-render-worker-fat-jar.md 를
읽고, sample-app unblock (ConstraintLayout / MaterialButton 등 실 Android app XML) 을 위한
W3 Day 2+ 스코프를 CEO/Eng/DX 3-스킬 페어로 브레인스토밍. 산출물은
docs/superpowers/specs/YYYY-MM-DD-w3-sample-app-unblock-design.md 로 저장.
```

---

## 공통 시작 체크리스트

필수 환경 검증:

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
./server/gradlew -p server test                                  # 99 unit PASS 확인
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration  # 8 + 2 SKIPPED 확인
ls server/layoutlib-worker/src/main/kotlin/dev/axp/layoutlib/worker/resources/   # 7 파일
```

모두 green 이어야 시작. 실패 시 handoff.md §"긴급 회복" 참조.
