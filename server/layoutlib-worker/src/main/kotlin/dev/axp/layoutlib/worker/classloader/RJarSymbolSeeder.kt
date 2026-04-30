package dev.axp.layoutlib.worker.classloader

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.CLASS_FILE_SUFFIX
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.INNER_CLASS_SEPARATOR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.INTERNAL_NAME_SEPARATOR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTERNAL_NAME_SEPARATOR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_CLASS_NAME_SUFFIX
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * sample-app `R.jar` 의 모든 R$<type> 클래스를 enumerate 하여, 각 정적 int 필드를
 * (ResourceReference, id) 로 callback 에 등록.
 *
 * 처리:
 *  - R$styleable 전체 skip — round 2 A2 fix. layoutlib Bridge.parseStyleable 가 styleable 처리.
 *  - 알 수 없는 type (RJarTypeMapping 미매핑) 인 R$* 는 skip.
 *  - int[] 필드는 부수적으로 skip (sanity).
 *
 * v2 round 2 follow-up #1:
 *  - R-1 (§7.1): R$style 의 underscore name (Theme_AxpFixture) → XML dot name
 *    (Theme.AxpFixture) canonicalization. T6 LayoutlibRenderResources 의 dot-name
 *    lookup 과 정합. RNameCanonicalization helper 위임.
 *  - R-2 (§7.2): multiple R class 의 동명 ATTR 첫 등장만 register — cross-class first-wins.
 *
 * W3D4-β T11 (round 3 reconcile): namespace 통일 정책 — RJarSymbolSeeder 도
 * `ResourceNamespace.RES_AUTO` 로 등록. AarResourceWalker:71 의 RES_AUTO 와 정합
 * (round 2 ξ 결정 일관). 이전의 `fromPackageName(packageName)` 은 callback.byId 의
 * ResourceReference 와 bundle.byNs 사이에 namespace 불일치를 만들어 Material
 * ThemeEnforcement.checkAppCompatTheme 가 sentinel attr 을 못 찾는 원인이었다.
 * cross-class first-wins 도 ATTR-only → 모든 type 으로 일반화 (ResourceTypeFirstWinsGuard).
 * Set<String> → Map<String, Int> 강화: 동명+동ID silent skip, 동명+다른ID loud WARN.
 */
internal object RJarSymbolSeeder
{

    fun seed(
        rJarPath: Path,
        rJarLoader: ClassLoader,
        register: (ResourceReference, Int) -> Unit,
    )
    {
        // W3D4-β T11 (round 3 reconcile): RES_AUTO 통일 후 동명 충돌 widespread —
        // STYLE 355 / COLOR 93 / DIMEN 130 dup (R.jar union 측정). 단 AAPT
        // non-namespaced 정책으로 동명 = 동일 ID. per-type Map<String, Int> guard 로
        // (1) 동명-동ID → silent skip (정상), (2) 동명-다른ID → loud WARN (회귀 신호).
        // ZipFile 단위 outer scope.
        val seenByType = HashMap<ResourceType, HashMap<String, Int>>()
        ZipFile(rJarPath.toFile()).use { zip ->
            for (entry in zip.entries())
            {
                if (!entry.name.endsWith(CLASS_FILE_SUFFIX))
                {
                    continue
                }
                val internalName = entry.name.removeSuffix(CLASS_FILE_SUFFIX)
                val parts = parseRClassName(internalName) ?: continue
                val (packageName, typeSimpleName) = parts
                val resourceType = RJarTypeMapping.fromSimpleName(typeSimpleName) ?: continue
                if (resourceType == ResourceType.STYLEABLE)
                {
                    continue
                }
                seedClass(
                    rJarLoader,
                    internalName.replace(INTERNAL_NAME_SEPARATOR, EXTERNAL_NAME_SEPARATOR),
                    packageName,
                    resourceType,
                    register,
                    seenByType,
                )
            }
        }
    }

    /**
     * @return Pair(packageName, typeSimpleName). 패키지 없는 bare `R$attr` 도 null.
     * Visibility: internal — round 2 A2 단위 테스트 직접 호출 가능.
     */
    internal fun parseRClassName(internalName: String): Pair<String, String>?
    {
        val dollarIdx = internalName.lastIndexOf(INNER_CLASS_SEPARATOR)
        if (dollarIdx < 0)
        {
            return null
        }
        val before = internalName.substring(0, dollarIdx)
        val after = internalName.substring(dollarIdx + 1)
        if (!before.endsWith(R_CLASS_NAME_SUFFIX))
        {
            return null
        }
        val packageInternal = before.removeSuffix(R_CLASS_NAME_SUFFIX)
        return packageInternal.replace(INTERNAL_NAME_SEPARATOR, EXTERNAL_NAME_SEPARATOR) to after
    }

    private fun seedClass(
        loader: ClassLoader,
        fqcn: String,
        packageName: String,
        type: ResourceType,
        register: (ResourceReference, Int) -> Unit,
        seenByType: MutableMap<ResourceType, HashMap<String, Int>>,
    )
    {
        val cls = try
        {
            loader.loadClass(fqcn)
        }
        catch (t: Throwable)
        {
            return
        }
        // W3D4-β T11: RES_AUTO 통일 — AarResourceWalker:71 의 namespace 와 일치시켜
        // bundle 의 byNs 와 callback 의 byId 가 같은 ResourceReference 좌표계 공유.
        val namespace = ResourceNamespace.RES_AUTO
        for (field in cls.declaredFields)
        {
            if (!Modifier.isStatic(field.modifiers))
            {
                continue
            }
            if (field.type != Int::class.javaPrimitiveType)
            {
                continue
            }
            field.isAccessible = true
            val value = field.getInt(null)
            // v2 follow-up #1 (R-1): R$style 의 underscore name → XML dot name canonicalization.
            // R$attr / R$dimen / R$color / R$bool / 등은 underscore 보존.
            val emitName = if (type == ResourceType.STYLE)
            {
                RNameCanonicalization.styleNameToXml(field.name)
            }
            else
            {
                field.name
            }
            // W3D4-β T11 (round 3): per-type Map<String, Int> guard — same-id silent,
            // different-id loud warn. 모든 type 에 적용.
            val seenForType = seenByType.getOrPut(type) { HashMap() }
            if (!ResourceTypeFirstWinsGuard.tryRegister(type, emitName, value, packageName, seenForType))
            {
                continue
            }
            register(ResourceReference(namespace, type, emitName), value)
        }
    }
}
