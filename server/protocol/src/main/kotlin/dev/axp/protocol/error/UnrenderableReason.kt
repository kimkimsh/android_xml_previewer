package dev.axp.protocol.error

/**
 * 렌더 실패의 근본 사유를 안정적으로 분류하는 enum.
 *
 * canonical:
 *  - 06 §4           : L1 5개 + L3 7개 + SYSTEM 3개 (총 15 — 06 §4 주석 "기존 13" 은 오기)
 *  - 08 §3.5         : L3-008/009/010/011 정식 편입 → 4개 추가
 *
 * **실제 총 19개** (15 + 4). plan 문서 08 §3.5 말미의 "총 17개" 는 산술 오류이며
 * 08 §7 Post-Execution Errata 에서 교정됨.
 * 모든 코드는 docs/TROUBLESHOOTING.md 의 anchor (#…) 와 일대일 매핑.
 * snapshot test(:protocol/test) 가 enum 과 docs anchor 일치 여부를 CI 로 강제.
 */
enum class UnrenderableReason(
    /** AXP-<LAYER>-<NNN> 형식의 영구 안정 코드 (사용자-facing 에러 카드에 노출). */
    val code: String,
    val category: Category,
    /** docs/TROUBLESHOOTING.md 내부 anchor. 플러그인이 자동 링크 생성. */
    val docsAnchor: String
) {

    // ---- L1 (layoutlib) 실패 — 06 §4 그대로 ----
    L1_PARSE_ERROR("AXP-L1-001", Category.USER_CODE, "#l1-parse-error"),
    L1_CUSTOM_VIEW_CLASS_NOT_FOUND("AXP-L1-002", Category.CUSTOM_VIEW, "#l1-custom-view"),
    L1_DATA_BINDING_NOT_EVALUATED("AXP-L1-003", Category.DATA_BINDING, "#l1-data-binding"),
    L1_LAYOUTLIB_BUG("AXP-L1-004", Category.TRANSIENT, "#l1-layoutlib-bug"),
    L1_OOM("AXP-L1-005", Category.RESOURCE_LIMIT, "#oom"),

    // ---- L3 (에뮬레이터 harness) 실패 — 06 §4 + 08 §3.5 확장 ----
    L3_NO_APP_APK_BUILT("AXP-L3-001", Category.USER_ACTION, "#l3-no-apk"),
    L3_STALE_APP_APK("AXP-L3-002", Category.USER_ACTION, "#l3-stale-apk"),
    L3_DEX_LOAD_ERROR("AXP-L3-003", Category.ENVIRONMENT, "#l3-dex-load"),
    L3_ANDROIDX_CONFLICT("AXP-L3-004", Category.ENVIRONMENT, "#l3-androidx"),
    L3_ABI_MISMATCH("AXP-L3-005", Category.ENVIRONMENT, "#l3-abi"),
    L3_AVD_NOT_FOUND("AXP-L3-006", Category.ENVIRONMENT, "#l3-no-avd"),
    L3_AVD_BOOT_TIMEOUT("AXP-L3-007", Category.TRANSIENT, "#l3-avd-timeout"),
    L3_APK_OBFUSCATED("AXP-L3-008", Category.USER_ACTION, "#l3-obfuscated"),
    L3_APK_SHORT_NAMES_HEURISTIC("AXP-L3-009", Category.USER_ACTION, "#l3-heuristic"),
    L3_APK_NOT_DEBUGGABLE("AXP-L3-010", Category.USER_ACTION, "#l3-release-apk"),
    L3_MISSING_LIBRARY_CLASS("AXP-L3-011", Category.USER_ACTION, "#l3-missing-lib"),

    // ---- 시스템 수준 실패 ----
    SYSTEM_ANDROID_HOME_MISSING("AXP-SYS-001", Category.ENVIRONMENT, "#no-android-home"),
    SYSTEM_JVM_TOO_OLD("AXP-SYS-002", Category.ENVIRONMENT, "#jvm-old"),
    SYSTEM_AAPT2_FAILED("AXP-SYS-003", Category.ENVIRONMENT, "#aapt2-fail");

    /** 렌더 실패의 개념적 카테고리. 사용자 액션이 필요한지, 환경 문제인지 구분. */
    enum class Category {
        /** XML 오류 등, 사용자 코드 수정 필요 */
        USER_CODE,

        /** 빌드 실행 등 사용자 액션 필요 (debug APK 재빌드, 환경 변수 설정 등) */
        USER_ACTION,

        /** custom view 관련 (v1.0 제한) */
        CUSTOM_VIEW,

        /** data-binding 관련 (v1.5 에서 개선) */
        DATA_BINDING,

        /** 환경/설치 문제 (JVM/SDK/AVD 등) */
        ENVIRONMENT,

        /** OOM 등 리소스 한계 */
        RESOURCE_LIMIT,

        /** 재시도로 해결 가능한 일시적 실패 */
        TRANSIENT
    }

    companion object {
        /**
         * 안정적 code string 으로 reverse lookup.
         * MCP 프로토콜 역직렬화에서 사용 (07 §4.1 ErrorEnvelope.code 디코딩).
         */
        fun fromCode(code: String): UnrenderableReason? =
            entries.firstOrNull { it.code == code }
    }
}

/**
 * 07 §4.1 ErrorEnvelope 의 구조화된 결과.
 * `reason` + 구체 메시지 + stack trace(옵션) 를 함께 전달.
 */
data class UnrenderableResult(
    val reason: UnrenderableReason,
    val detail: String,
    val stackTrace: String? = null
)
