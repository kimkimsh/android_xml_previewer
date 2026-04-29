package dev.axp.layoutlib.worker.classloader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper

/**
 * 단일 .class bytecode 를 받아 RewriteRules.REMAPPER 로 type reference 를 rewrite.
 * COMPUTE_MAXS 만 사용 (frame 재계산 불요 — type rename 만).
 */
internal object AndroidClassRewriter {
    fun rewrite(classBytes: ByteArray): ByteArray {
        val reader = ClassReader(classBytes)
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        reader.accept(ClassRemapper(writer, RewriteRules.REMAPPER), 0)
        return writer.toByteArray()
    }
}
