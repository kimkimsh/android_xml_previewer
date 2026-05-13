package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AarResourceWalkerTest
{

    @Test
    fun `AAR with values plus manifest parses correctly`()
    {
        val aar = makeAar(
            manifest = """<manifest package="com.test.lib"/>""",
            values = """<resources><dimen name="m">5dp</dimen></resources>""",
        )
        val result = AarResourceWalker.walkOne(aar)
        assertNotNull(result)
        assertEquals("com.test.lib", result!!.sourcePackage)
        assertEquals(ResourceNamespace.RES_AUTO, result.entries[0].namespace, "unified RES_AUTO mode")
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `AAR missing values is skipped silently with one diagnostic line`()
    {
        val aar = makeAar(manifest = """<manifest package="com.code.only"/>""", values = null)
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try
        {
            val result = AarResourceWalker.walkOne(aar)
            assertEquals(null, result, "null result when values are absent")
            val log = errOut.toString()
            assertTrue(log.contains("[AarResourceWalker]"), "diagnostic prefix")
            assertTrue(log.contains("res/values/values.xml") && log.contains("all absent"))
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `AAR missing manifest raises IllegalStateException`()
    {
        val aar = makeAarRaw(emptyMap())
        try
        {
            AarResourceWalker.walkOne(aar)
            org.junit.jupiter.api.Assertions.fail<Unit>("expected throw")
        }
        catch (e: IllegalStateException)
        {
            assertTrue(e.message?.contains("AndroidManifest") == true)
        }
    }

    @Test
    fun `manifest package extraction failure raises IllegalStateException`()
    {
        val aar = makeAar(manifest = """<manifest />""", values = """<resources/>""")
        try
        {
            AarResourceWalker.walkOne(aar)
            org.junit.jupiter.api.Assertions.fail<Unit>("expected throw")
        }
        catch (e: IllegalStateException)
        {
            assertTrue(e.message?.contains("failed to extract package") == true)
        }
    }

    @Test
    fun `walkAll filters the classpath txt to only aar entries`()
    {
        val aar1 = makeAar("""<manifest package="com.a"/>""", """<resources><dimen name="x">1dp</dimen></resources>""")
        val aar2 = makeAar("""<manifest package="com.b"/>""", """<resources><dimen name="y">2dp</dimen></resources>""")
        val classpathTxt = Files.createTempFile("cp", ".txt").apply {
            toFile().writeText(listOf(aar1.toString(), "/some.jar", aar2.toString()).joinToString("\n"))
            toFile().deleteOnExit()
        }
        val results = AarResourceWalker.walkAll(classpathTxt)
        assertEquals(2, results.size, ".jar entries are skipped")
        assertTrue(results.any { it.sourcePackage == "com.a" })
        assertTrue(results.any { it.sourcePackage == "com.b" })
    }

    @Test
    fun `walkAll prints wall-clock measurement plus counts`()
    {
        val aar = makeAar("""<manifest package="com.t"/>""", """<resources/>""")
        val cp = Files.createTempFile("cp", ".txt").apply {
            toFile().writeText(aar.toString()); toFile().deleteOnExit()
        }
        val errOut = ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try
        {
            AarResourceWalker.walkAll(cp)
            val log = errOut.toString()
            assertTrue(log.contains("[AarResourceWalker]"))
            assertTrue(log.contains("ms"), "wall-clock ms in log")
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    private fun makeAar(manifest: String, values: String?): Path
    {
        val map = mutableMapOf("AndroidManifest.xml" to manifest.toByteArray())
        if (values != null) map["res/values/values.xml"] = values.toByteArray()
        return makeAarRaw(map)
    }

    private fun makeAarRaw(entries: Map<String, ByteArray>): Path
    {
        val f = Files.createTempFile("test", ".aar")
        ZipOutputStream(f.toFile().outputStream()).use { zos ->
            entries.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        f.toFile().deleteOnExit()
        return f
    }
}
