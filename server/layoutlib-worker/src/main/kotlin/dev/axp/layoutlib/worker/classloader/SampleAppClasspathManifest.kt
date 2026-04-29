package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.JAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.MANIFEST_RELATIVE_PATH
import java.nio.file.Files
import java.nio.file.Path

/**
 * <sampleAppModuleRoot>/app/build/axp/runtime-classpath.txt 파일을 읽어
 * resolved runtime classpath 의 AAR/JAR 절대경로 리스트를 반환.
 *
 * 형식: 라인당 하나의 절대 경로, '\n' 구분, trailing newline 없음, distinct + sorted.
 * 누락/비/공백 라인/비-절대/비-aar-jar 확장자/존재안하는 파일 → 모두 즉시 throw.
 */
object SampleAppClasspathManifest
{

    fun read(sampleAppModuleRoot: Path): List<Path>
    {
        val mf = sampleAppModuleRoot.resolve(MANIFEST_RELATIVE_PATH)
        require(Files.isRegularFile(mf))
        {
            "axp classpath manifest 누락: $mf — `(cd fixture/sample-app && ./gradlew :app:assembleDebug)` 를 먼저 실행하세요"
        }
        val raw = Files.readString(mf)
        if (raw.isBlank())
        {
            error("axp classpath manifest 가 비어있음: $mf")
        }
        return raw.split('\n').mapIndexed { idx, line ->
            require(line.isNotBlank()) { "manifest line ${idx + 1} 이 공백" }
            val p = Path.of(line)
            require(p.isAbsolute) { "manifest line ${idx + 1} 이 비-절대경로: '$line'" }
            require(line.endsWith(AAR_EXTENSION) || line.endsWith(JAR_EXTENSION))
            {
                "manifest line ${idx + 1} 의 확장자가 ${AAR_EXTENSION}/${JAR_EXTENSION} 가 아님: '$line'"
            }
            require(Files.isRegularFile(p)) { "manifest line ${idx + 1} 의 파일이 없음: $p" }
            p
        }
    }
}
