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
 *  - R-2 (§7.2): multiple R class (appcompat / material / 등) 의 동명 attr 첫 등장만
 *    register — cross-class first-wins. seenAttrNames 는 ZipFile 단위 outer scope
 *    (한 R.jar 안 모든 R$attr 클래스 공유). AttrSeederGuard 위임.
 */
internal object RJarSymbolSeeder
{

    fun seed(
        rJarPath: Path,
        rJarLoader: ClassLoader,
        register: (ResourceReference, Int) -> Unit,
    )
    {
        // v2 follow-up #1 (R-2): cross-class first-wins per attr name. appcompat R$attr
        // 와 material R$attr 양쪽이 'colorPrimary' 를 등록 시도하면 첫 등장만 통과.
        // outer scope = ZipFile 단위 (한 R.jar 안 모든 R$attr 클래스 공유).
        val seenAttrNames = HashSet<String>()
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
                    seenAttrNames,
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
        seenAttrNames: MutableSet<String>,
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
        val namespace = ResourceNamespace.fromPackageName(packageName)
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
            // v2 follow-up #1 (R-2): R$attr 만 cross-class first-wins guard 적용.
            // 다른 type (style/dimen/color/...) 은 namespace 가 R class 별 다르므로
            // ResourceReference 자체로 동치 충돌 없음.
            if (type == ResourceType.ATTR)
            {
                if (!AttrSeederGuard.tryRegister(emitName, value, packageName, seenAttrNames))
                {
                    continue
                }
            }
            register(ResourceReference(namespace, type, emitName), value)
        }
    }
}
