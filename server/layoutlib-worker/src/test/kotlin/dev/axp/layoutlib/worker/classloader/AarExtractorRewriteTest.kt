package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class AarExtractorRewriteTest {

    /**
     * 합성 AAR (with classes.jar containing Foo.class that references android.os.Build$VERSION)
     * → extract → cached classes.jar 안의 Foo.class 가 rewrite 되어 _Original_Build$VERSION 참조하는지.
     */
    @Test
    fun `AAR classes_jar 의 class entries 가 rewrite 됨`(@TempDir root: Path) {
        // Foo.class with `<clinit>` that does GETSTATIC android/os/Build$VERSION.SDK_INT
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Foo", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
        mv.visitCode()
        mv.visitFieldInsn(Opcodes.GETSTATIC, "android/os/Build\$VERSION", "SDK_INT", "I")
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        val fooBytes = cw.toByteArray()

        // 합성 classes.jar with Foo.class
        val classesJar = root.resolve("classes-source.jar")
        ZipOutputStream(Files.newOutputStream(classesJar)).use { zos ->
            zos.putNextEntry(ZipEntry("Foo${ClassLoaderConstants.CLASS_FILE_SUFFIX}"))
            zos.write(fooBytes)
            zos.closeEntry()
        }
        val classesJarBytes = Files.readAllBytes(classesJar)

        // 합성 AAR (ZIP) — root entry 가 classes.jar
        val aar = root.resolve("test-lib${ClassLoaderConstants.AAR_EXTENSION}")
        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
            zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
            zos.write(classesJarBytes)
            zos.closeEntry()
        }

        val cacheRoot = root.resolve("cache")
        val extracted = AarExtractor.extract(aar, cacheRoot)
        assertNotNull(extracted)

        // cached classes.jar 안의 Foo.class 가 rewrite 됐는지
        ZipFile(extracted!!.toFile()).use { zip ->
            val fooEntry = zip.getEntry("Foo${ClassLoaderConstants.CLASS_FILE_SUFFIX}")
            assertNotNull(fooEntry)
            val rewrittenBytes = zip.getInputStream(fooEntry).readBytes()
            val asString = String(rewrittenBytes, Charsets.ISO_8859_1)
            assertTrue(
                asString.contains("_Original_Build\$VERSION"),
                "rewritten Foo 가 _Original_Build\$VERSION 참조해야 함",
            )
        }
    }

    @Test
    fun `cache path 에 REWRITE_VERSION 디렉토리 layer 가 포함`(@TempDir root: Path) {
        val aar = root.resolve("any${ClassLoaderConstants.AAR_EXTENSION}")
        ZipOutputStream(Files.newOutputStream(aar)).use { zos ->
            zos.putNextEntry(ZipEntry(ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY))
            zos.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            zos.closeEntry()
        }
        val cacheRoot = root.resolve("cache")
        val extracted = AarExtractor.extract(aar, cacheRoot)!!
        assertTrue(
            extracted.toString().contains("/${ClassLoaderConstants.REWRITE_VERSION}/"),
            "extracted path 에 REWRITE_VERSION 포함: $extracted",
        )
    }
}
