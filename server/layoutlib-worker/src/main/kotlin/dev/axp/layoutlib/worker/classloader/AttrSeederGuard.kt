package dev.axp.layoutlib.worker.classloader

/**
 * W3D4 §7.2 (R-2): multiple R class 에 같은 attr name 등장 시 first-wins.
 * appcompat R$attr / material R$attr 양쪽이 'colorPrimary' 등록을 시도하면 첫 등장만
 * 통과하고 이후는 진단 로그를 남기고 skip — layoutlib 가 한 attr 당 단일 ID 를 유지.
 *
 * seen MutableSet 은 호출자 (RJarSymbolSeeder.seed) 가 outer scope (ZipFile 단위)
 * 에서 보유 — cross-class dedup.
 */
internal object AttrSeederGuard
{
    /**
     * @return 등록 성공 (first encounter) → true, skip (duplicate) → false
     */
    fun tryRegister(name: String, id: Int, sourcePackage: String, seen: MutableSet<String>): Boolean
    {
        if (seen.add(name))
        {
            return true
        }
        System.err.println(
            "[RJarSymbolSeeder] dup attr '$name' from $sourcePackage (id=0x${Integer.toHexString(id)}) — first-wins, skipped",
        )
        return false
    }
}
