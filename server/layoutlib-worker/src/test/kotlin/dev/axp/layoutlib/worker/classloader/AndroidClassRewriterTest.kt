package dev.axp.layoutlib.worker.classloader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class AndroidClassRewriterTest {

    private fun makeFooReferencingBuild(): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Foo", null, "java/lang/Object", null)
        cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "x", "I", null, null).visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
        mv.visitCode()
        mv.visitFieldInsn(Opcodes.GETSTATIC, "android/os/Build\$VERSION", "SDK_INT", "I")
        mv.visitFieldInsn(Opcodes.PUTSTATIC, "Foo", "x", "I")
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    @Test
    fun `Build VERSION 참조가 _Original_Build VERSION 으로 rewrite`() {
        val original = makeFooReferencingBuild()
        val rewritten = AndroidClassRewriter.rewrite(original)
        assertNotEquals(original.toList(), rewritten.toList())
        val asString = String(rewritten, Charsets.ISO_8859_1)
        assertTrue(asString.contains("_Original_Build\$VERSION"), "rewritten 에 _Original_Build\$VERSION 포함 필요")
    }

    @Test
    fun `Build 무관 클래스는 _Original_ 등장 안 함`() {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Bar", null, "java/lang/Object", null)
        cw.visitEnd()
        val original = cw.toByteArray()
        val rewritten = AndroidClassRewriter.rewrite(original)
        val asString = String(rewritten, Charsets.ISO_8859_1)
        assertTrue(!asString.contains("_Original_"))
    }

    @Test
    fun `field type signature 도 rewrite`() {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Baz", null, "java/lang/Object", null)
        cw.visitField(
            Opcodes.ACC_PUBLIC,
            "instance",
            "Landroid/os/Build\$VERSION;",
            null,
            null,
        ).visitEnd()
        cw.visitEnd()
        val original = cw.toByteArray()
        val rewritten = AndroidClassRewriter.rewrite(original)
        val asString = String(rewritten, Charsets.ISO_8859_1)
        assertTrue(asString.contains("_Original_Build\$VERSION"))
    }
}
