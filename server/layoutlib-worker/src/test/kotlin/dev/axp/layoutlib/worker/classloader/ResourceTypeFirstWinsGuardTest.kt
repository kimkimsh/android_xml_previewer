package dev.axp.layoutlib.worker.classloader

import com.android.resources.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * W3D4-β T11 (round 3 reconcile): ResourceTypeFirstWinsGuard 의 4 시나리오 검증.
 *
 * RDuplicateAttrIdTest (W3D4 §7.2 R-2 ATTR-only) 인계 + STYLE/COLOR/DIMEN 까지 일반화 +
 * Set<String> → Map<String, Int> 강화의 동명-동ID silent / 동명-다른ID loud 정책 검증.
 */
class ResourceTypeFirstWinsGuardTest
{

    @Test
    fun `같은 ATTR name + 동일 ID 두 R class → silent skip (정상 transitive ABI)`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val seen = HashMap<String, Int>()
            val accepted1 = ResourceTypeFirstWinsGuard.tryRegister(
                ResourceType.ATTR, "colorPrimary", 0x7F030013, "androidx.appcompat", seen,
            )
            val accepted2 = ResourceTypeFirstWinsGuard.tryRegister(
                ResourceType.ATTR, "colorPrimary", 0x7F030013, "com.google.android.material", seen,
            )
            assertTrue(accepted1, "first encounter accepted")
            assertFalse(accepted2, "same-id duplicate skipped")
            assertEquals("", errOut.toString().trim(), "동명+동ID 는 silent — WARN 출력 없음")
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `같은 ATTR name + 다른 ID 두 R class → loud WARN`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val seen = HashMap<String, Int>()
            val accepted1 = ResourceTypeFirstWinsGuard.tryRegister(
                ResourceType.ATTR, "colorPrimary", 0x1, "androidx.appcompat", seen,
            )
            val accepted2 = ResourceTypeFirstWinsGuard.tryRegister(
                ResourceType.ATTR, "colorPrimary", 0x2, "com.google.android.material", seen,
            )
            assertTrue(accepted1)
            assertFalse(accepted2, "different-id duplicate skipped")
            val log = errOut.toString()
            assertTrue(log.contains("DIFFERENT id"), "WARN 명시")
            assertTrue(log.contains("colorPrimary"), "name 포함")
            assertTrue(log.contains("0x1") && log.contains("0x2"), "기존 + 신규 ID 모두 포함")
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `STYLE 동명 + 동ID 도 silent skip — ATTR 외 type 일반화`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val seen = HashMap<String, Int>()
            assertTrue(ResourceTypeFirstWinsGuard.tryRegister(
                ResourceType.STYLE, "Theme.AppCompat", 0x7F0E0001, "androidx.appcompat", seen,
            ))
            assertFalse(ResourceTypeFirstWinsGuard.tryRegister(
                ResourceType.STYLE, "Theme.AppCompat", 0x7F0E0001, "androidx.constraintlayout", seen,
            ))
            assertEquals("", errOut.toString().trim())
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `COLOR 동명 + 다른 ID → loud WARN`()
    {
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(PrintStream(errOut))
        try
        {
            val seen = HashMap<String, Int>()
            ResourceTypeFirstWinsGuard.tryRegister(
                ResourceType.COLOR, "notification_icon_bg_color", 0x1, "core", seen,
            )
            assertFalse(ResourceTypeFirstWinsGuard.tryRegister(
                ResourceType.COLOR, "notification_icon_bg_color", 0x2, "appcompat", seen,
            ))
            val log = errOut.toString()
            assertTrue(log.contains("color") && log.contains("DIFFERENT id"))
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `다른 name 은 type 별로 독립 — 모두 등록`()
    {
        val seen = HashMap<String, Int>()
        assertTrue(ResourceTypeFirstWinsGuard.tryRegister(
            ResourceType.ATTR, "colorPrimary", 0x1, "p", seen,
        ))
        assertTrue(ResourceTypeFirstWinsGuard.tryRegister(
            ResourceType.ATTR, "colorAccent", 0x2, "p", seen,
        ))
        assertEquals(2, seen.size)
    }
}
