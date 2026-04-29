package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_BASE_RELATIVE_PATH
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.R_JAR_RELATIVE_PATH
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * sample-app 의 runtime classpath (resolved AAR + transitive JAR + R.jar) 로부터
 * URLClassLoader 를 구성. parent = layoutlib isolatedClassLoader (android.view.* 보유).
 *
 * AAR 은 AarExtractor 로 classes.jar 를 추출 후 그 경로를 URL 로 사용.
 * Resource-only AAR (classes.jar 없음) 은 silently skip.
 */
class SampleAppClassLoader private constructor(
    val classLoader: ClassLoader,
    val urls: List<URL>,
) {
    companion object {

        fun build(sampleAppModuleRoot: Path, parent: ClassLoader): SampleAppClassLoader {
            val manifest = SampleAppClasspathManifest.read(sampleAppModuleRoot)
            val cacheRoot = sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)
            val urls = mutableListOf<URL>()

            for (entry in manifest)
            {
                val asString = entry.toString()
                val jarPath = if (asString.endsWith(AAR_EXTENSION))
                {
                    AarExtractor.extract(entry, cacheRoot) ?: continue
                }
                else
                {
                    entry
                }
                urls += jarPath.toUri().toURL()
            }

            val rJar = sampleAppModuleRoot.resolve(R_JAR_RELATIVE_PATH)
            require(Files.isRegularFile(rJar))
            {
                "sample-app R.jar 누락: $rJar — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 필요"
            }
            urls += rJar.toUri().toURL()

            val cl = URLClassLoader(urls.toTypedArray(), parent)
            return SampleAppClassLoader(cl, urls.toList())
        }
    }
}
