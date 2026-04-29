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
 */
internal object RJarSymbolSeeder {

    fun seed(
        rJarPath: Path,
        rJarLoader: ClassLoader,
        register: (ResourceReference, Int) -> Unit,
    ) {
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
                )
            }
        }
    }

    /**
     * @return Pair(packageName, typeSimpleName). 패키지 없는 bare `R$attr` 도 null.
     * Visibility: internal — round 2 A2 단위 테스트 직접 호출 가능.
     */
    internal fun parseRClassName(internalName: String): Pair<String, String>? {
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
    ) {
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
            register(ResourceReference(namespace, type, field.name), value)
        }
    }
}
