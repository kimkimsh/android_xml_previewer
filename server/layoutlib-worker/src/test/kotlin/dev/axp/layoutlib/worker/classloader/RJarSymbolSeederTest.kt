package dev.axp.layoutlib.worker.classloader

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RJarSymbolSeederTest {

    /**
     * 합성 R$<type> 클래스: package/R$type 이름, ACC_PUBLIC FINAL,
     * field map (name → int value) 의 각 entry 를 ACC_PUBLIC STATIC FINAL int field 로.
     * int[] field 도 추가 가능 (skip 검증용).
     */
    private fun makeRClass(
        packageName: String,
        rType: String,
        intFields: Map<String, Int>,
        intArrayFields: List<String> = emptyList(),
    ): Pair<String, ByteArray> {
        val internalName = "${packageName.replace('.', '/')}/R\$$rType"
        val cw = ClassWriter(0)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL,
            internalName,
            null,
            "java/lang/Object",
            null,
        )
        for ((name, value) in intFields)
        {
            cw.visitField(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
                name,
                "I",
                null,
                value,
            ).visitEnd()
        }
        for (name in intArrayFields)
        {
            cw.visitField(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
                name,
                "[I",
                null,
                null,
            ).visitEnd()
        }
        cw.visitEnd()
        return internalName to cw.toByteArray()
    }

    private fun buildRJar(root: Path, entries: List<Pair<String, ByteArray>>): Path {
        val rJar = root.resolve("R.jar")
        ZipOutputStream(Files.newOutputStream(rJar)).use { zos ->
            for ((internalName, bytes) in entries)
            {
                zos.putNextEntry(ZipEntry("$internalName${ClassLoaderConstants.CLASS_FILE_SUFFIX}"))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return rJar
    }

    @Test
    fun `R attr 의 static int 필드 register`(@TempDir root: Path) {
        val attrClass = makeRClass(
            "com.example",
            "attr",
            mapOf(
                "fooAttr" to 0x7F010001,
                "barAttr" to 0x7F010002,
            ),
        )
        val rJar = buildRJar(root, listOf(attrClass))
        val loader = URLClassLoader(arrayOf(rJar.toUri().toURL()))

        val seen = mutableMapOf<ResourceReference, Int>()
        RJarSymbolSeeder.seed(rJar, loader) { ref, id -> seen[ref] = id }

        // W3D4-β T11 (round 3 reconcile): seeded namespace 는 RES_AUTO 로 통일.
        // AarResourceWalker:71 의 namespace 와 정합 — bundle.byNs 와 callback.byId 가
        // 동일 ResourceReference 좌표계 공유.
        val ns = ResourceNamespace.RES_AUTO
        assertEquals(0x7F010001, seen[ResourceReference(ns, ResourceType.ATTR, "fooAttr")])
        assertEquals(0x7F010002, seen[ResourceReference(ns, ResourceType.ATTR, "barAttr")])
    }

    @Test
    fun `R styleable 전체 skip — A2 fix`(@TempDir root: Path) {
        val styleableClass = makeRClass(
            "com.example",
            "styleable",
            mapOf(
                "ActionBar_background" to 0,
                "ActionBar_backgroundSplit" to 1,
            ),
            intArrayFields = listOf("ActionBar"),
        )
        val rJar = buildRJar(root, listOf(styleableClass))
        val loader = URLClassLoader(arrayOf(rJar.toUri().toURL()))

        val seen = mutableMapOf<ResourceReference, Int>()
        RJarSymbolSeeder.seed(rJar, loader) { ref, id -> seen[ref] = id }

        // R$styleable type 전체 skip — 어떤 필드도 register 안 됨
        assertTrue(seen.isEmpty(), "R\$styleable 은 전체 skip 되어야: ${seen}")
    }

    @Test
    fun `int array 필드는 skip — sanity (R styleable 외에도)`(@TempDir root: Path) {
        // ASM 으로 R$attr 안에 int[] field 를 추가 — 정상적으로는 R$attr 에 array 필드가 없으나
        // sanity check: int[] 필드는 어쨌든 skip.
        val attrClass = makeRClass(
            "com.example",
            "attr",
            mapOf("foo" to 0x7F010001),
            intArrayFields = listOf("someArrayField"),
        )
        val rJar = buildRJar(root, listOf(attrClass))
        val loader = URLClassLoader(arrayOf(rJar.toUri().toURL()))

        val seen = mutableMapOf<ResourceReference, Int>()
        RJarSymbolSeeder.seed(rJar, loader) { ref, id -> seen[ref] = id }

        // W3D4-β T11: RES_AUTO 통일.
        val ns = ResourceNamespace.RES_AUTO
        assertEquals(0x7F010001, seen[ResourceReference(ns, ResourceType.ATTR, "foo")])
        assertFalse(
            seen.keys.any { it.name == "someArrayField" },
            "int[] field 는 skip — found: ${seen.keys.map { it.name }}",
        )
    }

    @Test
    fun `매핑 안 된 R type 은 skip`(@TempDir root: Path) {
        val unknownClass = makeRClass(
            "com.example",
            "weirdtype",
            mapOf("foo" to 0x7F010001),
        )
        val rJar = buildRJar(root, listOf(unknownClass))
        val loader = URLClassLoader(arrayOf(rJar.toUri().toURL()))

        val seen = mutableMapOf<ResourceReference, Int>()
        RJarSymbolSeeder.seed(rJar, loader) { ref, id -> seen[ref] = id }

        assertTrue(seen.isEmpty(), "weirdtype 은 ResourceType 매핑 없으므로 skip")
    }

    @Test
    fun `seed 가 R style underscore name 을 dot name 으로 emit`(@TempDir root: Path) {
        // v2 round 2 follow-up #11: end-to-end 회귀 — Theme_AxpFixture seeding
        // → Theme.AxpFixture lookup. T6 LayoutlibRenderResources 의 dot-name lookup 정합.
        val styleClass = makeRClass(
            "com.fixture",
            "style",
            mapOf("Theme_AxpFixture" to 0x7F0C0001),
        )
        val rJar = buildRJar(root, listOf(styleClass))
        val loader = URLClassLoader(arrayOf(rJar.toUri().toURL()), null)

        val emitted = mutableListOf<Pair<ResourceReference, Int>>()
        RJarSymbolSeeder.seed(rJar, loader) { ref, id -> emitted += ref to id }

        val styles = emitted.filter { it.first.resourceType == ResourceType.STYLE }
        assertTrue(
            styles.any { it.first.name == "Theme.AxpFixture" },
            "underscore→dot canonicalization (실측 emit name): ${styles.map { it.first.name }}",
        )
        assertTrue(
            styles.none { it.first.name == "Theme_AxpFixture" },
            "underscore name 은 emit 안 됨 (변환 후만): ${styles.map { it.first.name }}",
        )
    }

    @Test
    fun `cross-class 동명 + 동ID ATTR — silent first-wins`(@TempDir root: Path) {
        // W3D4-β T11 (round 3): AAPT non-namespaced 정책의 정상 transitive ABI.
        // appcompat / material / constraintlayout 의 colorPrimary 가 동일 ID 일 때 silent skip.
        val attrAppcompat = makeRClass(
            "androidx.appcompat", "attr",
            mapOf("colorPrimary" to 0x7F030013),
        )
        val attrMaterial = makeRClass(
            "com.google.android.material", "attr",
            mapOf("colorPrimary" to 0x7F030013),
        )
        val rJar = buildRJar(root, listOf(attrAppcompat, attrMaterial))
        val loader = URLClassLoader(arrayOf(rJar.toUri().toURL()), null)

        val errOut = java.io.ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try
        {
            val seen = mutableMapOf<ResourceReference, Int>()
            RJarSymbolSeeder.seed(rJar, loader) { ref, id -> seen[ref] = id }
            // 단일 entry — RES_AUTO 통일 + first-wins.
            assertEquals(1, seen.size, "동명+동ID 는 단일 entry: $seen")
            assertEquals(
                0x7F030013,
                seen[ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimary")],
            )
            // silent — DIFFERENT id WARN 출력 없어야 함.
            assertFalse(errOut.toString().contains("DIFFERENT id"), "동명+동ID 는 WARN 없음")
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `cross-class 동명 + 다른 ID ATTR — loud WARN`(@TempDir root: Path) {
        // W3D4-β T11 (round 3): namespaced build / R.jar 정합 오류 가정 시나리오.
        val attrAppcompat = makeRClass(
            "androidx.appcompat", "attr",
            mapOf("colorPrimary" to 0x1),
        )
        val attrMaterial = makeRClass(
            "com.google.android.material", "attr",
            mapOf("colorPrimary" to 0x2),
        )
        val rJar = buildRJar(root, listOf(attrAppcompat, attrMaterial))
        val loader = URLClassLoader(arrayOf(rJar.toUri().toURL()), null)

        val errOut = java.io.ByteArrayOutputStream()
        val origErr = System.err
        System.setErr(java.io.PrintStream(errOut))
        try
        {
            val seen = mutableMapOf<ResourceReference, Int>()
            RJarSymbolSeeder.seed(rJar, loader) { ref, id -> seen[ref] = id }
            assertEquals(1, seen.size)
            // first-wins — ZipFile entries 등록 순서 결정.
            val log = errOut.toString()
            assertTrue(log.contains("DIFFERENT id"), "다른 ID 는 loud WARN: $log")
            assertTrue(log.contains("colorPrimary"))
        }
        finally
        {
            System.setErr(origErr)
        }
    }

    @Test
    fun `parseRClassName internal — 패키지 + type 추출`() {
        val parsed = RJarSymbolSeeder.parseRClassName("androidx/constraintlayout/widget/R\$attr")
        assertEquals("androidx.constraintlayout.widget" to "attr", parsed)

        val parsedRoot = RJarSymbolSeeder.parseRClassName("R\$attr")  // bare R, package 없음
        assertNull(parsedRoot)

        val parsedNoR = RJarSymbolSeeder.parseRClassName("com/example/Foo\$inner")
        assertNull(parsedNoR)

        val parsedNoDollar = RJarSymbolSeeder.parseRClassName("com/example/Plain")
        assertNull(parsedNoDollar)
    }
}
