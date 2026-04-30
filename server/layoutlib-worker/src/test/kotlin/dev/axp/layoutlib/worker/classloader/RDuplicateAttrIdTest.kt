package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * W3D4 §7.2 (R-2): multiple R class 가 동일 attr name 을 등록하려 할 때 first-wins.
 * appcompat R$attr / material R$attr 의 'colorPrimary' 충돌 시나리오.
 */
class RDuplicateAttrIdTest
{

    @Test
    fun `같은 attr name 이 두 R class 에 → first-wins 진단 출력`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val seen = HashSet<String>()
            val accepted1 = AttrSeederGuard.tryRegister("colorPrimary", 0x1, "androidx.appcompat", seen)
            val accepted2 = AttrSeederGuard.tryRegister("colorPrimary", 0x2, "com.google.android.material", seen)
            assertTrue(accepted1)
            assertFalse(accepted2, "second attempt skipped (first-wins)")
            assertTrue(errOut.toString().contains("dup attr 'colorPrimary'"))
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `다른 attr name 은 모두 등록`()
    {
        val seen = HashSet<String>()
        assertTrue(AttrSeederGuard.tryRegister("colorPrimary", 0x1, "p", seen))
        assertTrue(AttrSeederGuard.tryRegister("colorAccent", 0x2, "p", seen))
        assertEquals(2, seen.size)
    }

    @Test
    fun `같은 R class 안 동명 — first-wins 도 적용 (방어적)`()
    {
        val seen = HashSet<String>()
        AttrSeederGuard.tryRegister("x", 0x1, "p", seen)
        val second = AttrSeederGuard.tryRegister("x", 0x1, "p", seen)
        assertFalse(second, "동일 R class 안에서도 first-wins")
    }
}
