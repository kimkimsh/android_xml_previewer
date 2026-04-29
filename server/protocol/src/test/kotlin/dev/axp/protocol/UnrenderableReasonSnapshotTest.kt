package dev.axp.protocol

import dev.axp.protocol.error.UnrenderableReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 07 §5.7 + 08 §3.5 canonical — UnrenderableReason 17 enum ↔ docs/TROUBLESHOOTING.md anchor 매핑.
 *
 * 본 테스트가 실패한다는 것은 다음 중 하나가 발생했음을 뜻함:
 *   - enum 값이 추가/제거/이름변경됨 (프로토콜 contract 변경)
 *   - docsAnchor 가 다른 형식으로 바뀜
 *   - 총 개수가 17이 아님
 *
 * 모든 실패는 의도적 contract 변경이므로 해당 PR 에서 이 snapshot 을 같이 수정해야 한다.
 */
class UnrenderableReasonSnapshotTest {

    /**
     * 07 section 5.7 카운트 플로어 — UnrenderableReason catalog snapshot test.
     *
     * 구현 시 발견된 plan 문서 내부 카운트 모순:
     *   - 06 section 4 실제 정의: L1(5) + L3(7) + SYS(3) = **15**
     *     (06 section 4 주석 "기존 13개"는 오기)
     *   - 08 section 3.5: L3-008/009/010/011 +4 추가, 말미 "총 17" 명시
     *     → 15 + 4 = **19** (plan 의 "17" 은 산술 오류)
     *
     * 해결: 실제 canonical 카운트 19 를 floor 로 테스트. plan 문서 교정 노트는
     *        docs/plan/08-integration-reconciliation.md 하단 "Post-Execution Errata" 에 기록.
     */
    @Test
    fun `enum should contain exactly 19 reasons canonical 06-4 plus 08-3_5`() {
        assertEquals(19, UnrenderableReason.entries.size,
            "UnrenderableReason 개수가 19 이 아님. " +
            "06 section 4 정의 15 + 08 section 3.5 추가 4 = 19 canonical " +
            "(plan 문서의 '총 17' 표기는 산술 오류).")
    }

    /** 모든 code 가 `AXP-<LAYER>-<NNN>` 포맷인지 검증. 오타/리네임 방지. */
    @Test
    fun `all codes match AXP-layer-number pattern`() {
        val pattern = Regex("^AXP-(L1|L3|SYS)-\\d{3}$")
        for (reason in UnrenderableReason.entries) {
            assertTrue(
                pattern.matches(reason.code),
                "'${reason.code}' 는 AXP-<LAYER>-<NNN> 패턴이 아님"
            )
        }
    }

    /** code 는 enum 전체에서 유일해야 함 (reverse lookup 안전). */
    @Test
    fun `codes are unique across all reasons`() {
        val codes = UnrenderableReason.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "중복된 code 가 있음: $codes")
    }

    /** docsAnchor 는 '#' 로 시작, 공백/대문자 없음 — URL 안전. */
    @Test
    fun `all docsAnchors start with hash and are kebab-safe`() {
        val anchorPattern = Regex("^#[a-z0-9-]+$")
        for (reason in UnrenderableReason.entries) {
            assertTrue(
                anchorPattern.matches(reason.docsAnchor),
                "${reason.name} 의 docsAnchor '${reason.docsAnchor}' 는 #kebab-case 형식이 아님"
            )
        }
    }

    /** fromCode reverse lookup round-trip. */
    @Test
    fun `fromCode round-trip returns same enum value`() {
        for (reason in UnrenderableReason.entries) {
            assertEquals(reason, UnrenderableReason.fromCode(reason.code),
                "fromCode('${reason.code}') 가 ${reason.name} 과 일치하지 않음")
        }
    }

    /** 알 수 없는 code 에 대해 fromCode 는 null 반환. */
    @Test
    fun `fromCode returns null for unknown code`() {
        assertEquals(null, UnrenderableReason.fromCode("AXP-XX-999"))
        assertEquals(null, UnrenderableReason.fromCode(""))
    }

    /**
     * 08 section 3.5 가 요구한 4 개 AXP-L3-008/009/010/011 이 모두 enum 에 존재하는지 확인.
     * 이 네 개가 빠지면 coverage target 85% 서사가 깨짐 (07 section 3.4 의 preflight 결과 → UX 변환 경로 부재).
     */
    @Test
    fun `08 section 3_5 canonical L3 codes all present`() {
        val required = setOf("AXP-L3-008", "AXP-L3-009", "AXP-L3-010", "AXP-L3-011")
        val actual = UnrenderableReason.entries.map { it.code }.toSet()
        assertTrue(
            actual.containsAll(required),
            "08 section 3.5 가 요구한 L3 코드 누락: ${required - actual}"
        )
    }
}
