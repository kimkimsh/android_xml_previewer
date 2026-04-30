package dev.axp.layoutlib.worker.classloader

import com.android.resources.ResourceType

/**
 * W3D4-β T11 (round 3 reconcile): RES_AUTO 통일 후 모든 ResourceType 의 cross-class
 * 동명 first-wins guard. 이전의 AttrSeederGuard (ATTR-only Set) 를 일반화 + Map<name, id>
 * 로 ID 일치성 검증.
 *
 * 정책:
 *  - 동명 + 동ID (현재 AAPT non-namespaced 정책의 정상 transitive ABI) → silent skip.
 *  - 동명 + 다른ID (회귀 신호 — 향후 namespaced build 또는 R.jar 빌드 정합 오류) → loud WARN.
 *
 * 실측 (round 3 Codex / Claude): R.jar union 에서 STYLE 355 / COLOR 93 / DIMEN 130
 * 동명 발견, 모두 ID 동일 (0 distinct-ID collision).
 */
internal object ResourceTypeFirstWinsGuard
{
    /**
     * @return 등록 성공 (first encounter) → true, skip (duplicate name) → false.
     */
    fun tryRegister(
        type: ResourceType,
        name: String,
        id: Int,
        sourcePackage: String,
        seen: MutableMap<String, Int>,
    ): Boolean
    {
        val existing = seen[name]
        if (existing == null)
        {
            seen[name] = id
            return true
        }
        if (existing == id)
        {
            // 정상 transitive ABI (AAPT non-namespaced 정책) — silent.
            return false
        }
        // 회귀 신호 — loud.
        System.err.println(
            "[RJarSymbolSeeder] WARN ${type.getName()} '$name' from $sourcePackage has DIFFERENT id (existing=0x${Integer.toHexString(existing)} new=0x${Integer.toHexString(id)}) — first-wins; namespaced build 또는 빌드 정합 오류 가능",
        )
        return false
    }
}
