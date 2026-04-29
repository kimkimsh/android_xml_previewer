# W3D3 Next-Session Prompt

다음 세션 시작 시 아래 프롬프트 중 하나를 복사해서 사용.

---

## Option A (권장) — sample-app unblock

```
docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md 읽고 Option A 경로로 진행.

sample-app (activity_basic.xml) 의 ConstraintLayout / MaterialButton 등 커스텀 뷰 지원을 위한
DexClassLoader 기반 L3 class loading 을 구현한다. 계약 파일:
- MinimalLayoutlibCallback.loadView 확장.
- AAR 아티팩트 (material / constraintlayout) 파싱 → dex / jar classloader.
- LayoutlibRendererIntegrationTest @Disabled 해제 + tier3-values 패턴 assertion.

에이전트 팀과 스킬 적극 활용. CLAUDE.md 규약 준수. Codex 호출 시 --skip-git-repo-check +
--sandbox danger-full-access direct codex exec 방식 사용 (codex:codex-rescue subagent 는
bwrap 실패로 bypass 필요).
```

## Option B — tier3-glyph

```
docs/work_log/2026-04-24_w3d2-integration-cleanup/handoff.md Option B 경로.

tier3-glyph unblock: Font wiring + StaticLayout + Canvas.drawText JNI 증명.
T2 gate (dark pixel >= 20) 통과가 목표.
```

---

## 공통 시작 체크리스트

```bash
cd /home/bh-mark-dev-desktop/workspace/android_xml_previewer
./server/gradlew -p server test                                                # 118 unit PASS 확인
./server/gradlew -p server :layoutlib-worker:test -PincludeTags=integration    # 11 + 2 SKIPPED 확인
./server/gradlew -p server build                                               # BUILD SUCCESSFUL 확인
```

모두 green 이어야 시작. 실패 시 handoff.md §"긴급 회복" 참조.
