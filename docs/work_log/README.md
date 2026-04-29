# Work Log Index

> 세션별 작업 로그 모음. 포맷: `YYYY-MM-DD_<slug>/` 서브폴더 (세션당 1개),
> 각 폴더에 `session-log.md` + `handoff.md` + `next-session-prompt.md` 3개 파일.
> 개별 세션 로그 규칙은 `~/.claude/skills/session-work-log-protocol/SKILL.md` 참조.

## 세션 목록 (최신 순)

| 일자 | 세션 | 결과 | 링크 |
|------|------|------|------|
| 2026-04-22 | W1D1 — Android XML Previewer foundation + protocol scaffold | GO_WITH_FOLLOWUPS (페어 full convergence) | [2026-04-22_w1d1-android-xml-previewer-foundation/](./2026-04-22_w1d1-android-xml-previewer-foundation/) |

## 열람 순서 권장

각 세션 폴더 안에서:

1. **`handoff.md`** — 다음 세션이 cold-start 로 맥락 회복하는 데 필요한 최소 정보
2. **`session-log.md`** — 해당 세션의 5-field canonical 기록 (Context / Files / Why / Verification / Follow-ups)
3. **`next-session-prompt.md`** — 다음 Claude Code 세션에 바로 붙여넣을 프롬프트

## 세션 시작 시 실행할 grep

```bash
# 가장 최근 3개 세션의 session-log.md 확인
ls -td docs/work_log/*/ | head -3 | while read d; do
  echo "=== $d ==="
  head -40 "${d}session-log.md" 2>/dev/null
done

# 아직 핸드오프된 작업 (followup 섹션에서 'pending' 검색)
grep -l "pending\|이관" docs/work_log/*/session-log.md 2>/dev/null
```
