package dev.axp.layoutlib.worker.resources

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): `data/` 디렉토리의 10 XML 을 로드하여
 * FrameworkResourceBundle 로 집계.
 *
 * JVM-wide lazy cache — layoutlib dist 는 process 생애 불변.
 * `clearCache()` 는 테스트 격리용.
 */
object FrameworkResourceValueLoader {

    private val cache = ConcurrentHashMap<Path, FrameworkResourceBundle>()

    /**
     * @param dataDir layoutlib dist 의 `data` 디렉토리 (e.g. `server/libs/layoutlib-dist/android-34/data`).
     * @throws IllegalStateException 10 XML 중 하나라도 없거나 parsing 실패.
     */
    fun loadOrGet(dataDir: Path): FrameworkResourceBundle {
        val key = dataDir.toAbsolutePath().normalize()
        cache[key]?.let { return it }
        return cache.computeIfAbsent(key) { build(it) }
    }

    /** 테스트 격리용 — production 경로에서는 호출 금지. */
    fun clearCache() {
        cache.clear()
    }

    private fun build(dataDir: Path): FrameworkResourceBundle {
        val valuesDir = dataDir.resolve(ResourceLoaderConstants.VALUES_DIR)
        val entries = mutableListOf<ParsedEntry>()
        for (filename in ResourceLoaderConstants.REQUIRED_FILES) {
            val path = valuesDir.resolve(filename)
            if (!path.exists() || !path.isRegularFile()) {
                throw IllegalStateException(
                    "필수 프레임워크 리소스 XML 누락: $path (파일명: $filename)"
                )
            }
            entries += FrameworkValueParser.parse(path)
        }
        return FrameworkResourceBundle.build(entries)
    }
}
