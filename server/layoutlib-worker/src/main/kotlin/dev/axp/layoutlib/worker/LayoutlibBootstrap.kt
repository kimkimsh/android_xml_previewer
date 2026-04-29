package dev.axp.layoutlib.worker

import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * W1D3-D4 L1 spike 의 entry-point.
 *
 * 07 §2.2 canonical 번들 구조 (08 §7.5 errata 적용 — JAR 이름 정명화):
 *   server/libs/layoutlib-dist/android-34/
 *   ├── layoutlib-*.jar               (Maven 좌표: com.android.tools.layoutlib:layoutlib)
 *   └── data/
 *       ├── res/
 *       ├── fonts/
 *       ├── keyboards/
 *       ├── icu/
 *       └── linux/lib64/
 *
 * 이 클래스는 번들 디렉토리에서 JAR + native lib path + framework data 를 찾아
 * 격리된 URLClassLoader 를 만들고, 그 안에서 reflection 으로 `Bridge.init(...)` 호출.
 *
 * 왜 reflection? layoutlib 은 Android 프레임워크의 내부 API 에 직접 의존하므로, compile-time
 * 의존을 두면 :layoutlib-worker 가 android.jar / layoutlib.jar 를 classpath 에 가지고 있어야
 * 컴파일되지만, 그것은 배포 관점에서 번거롭고 버전 고정 문제 발생. 런타임 reflection 이 canonical.
 *
 * Week 1 Day 1 는 번들 존재 여부 + 위치 탐지만 구현. 실제 `Bridge.init` 호출은 W1D4.
 */
class LayoutlibBootstrap(val distDir: Path) {

    init {
        require(distDir.exists() && distDir.isDirectory()) {
            "layoutlib-dist 디렉토리가 없음: $distDir — 07 §2.2 번들 구조를 확인하세요"
        }
    }

    /** `data/` 절대 경로. */
    fun dataDir(): Path = distDir.resolve(LayoutlibPaths.DATA_DIR)

    /** `data/fonts/` 절대 경로 (Bridge.init 인자). */
    fun fontsDir(): Path = distDir.resolve(LayoutlibPaths.FONTS_DIR)

    /** `data/linux/lib64/` 절대 경로 (libandroid_runtime.so 위치). */
    fun nativeLibDir(): Path = distDir.resolve(LayoutlibPaths.LINUX_LIB64_DIR)

    /**
     * `data/build.prop` 을 key=value 라인 단위로 파싱.
     * `Bridge.init` 의 첫 번째 인자(platform properties Map) 로 사용.
     */
    fun parseBuildProperties(): Map<String, String> {
        val buildProp = distDir.resolve(LayoutlibPaths.BUILD_PROP)
        require(buildProp.isRegularFile()) { "data/build.prop 누락: $buildProp" }
        val props = mutableMapOf<String, String>()
        for (raw in Files.readAllLines(buildProp)) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            props[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
        }
        return props
    }

    /**
     * `data/icu/` 의 `icudt`로 시작하고 `.dat` 으로 끝나는 파일 절대 경로 (Bridge.init 의 icuDataPath 인자).
     * 여러 개면 lex 최신.
     */
    fun findIcuDataFile(): Path? {
        val icuDir = distDir.resolve(LayoutlibPaths.ICU_DIR).toFile()
        if (!icuDir.isDirectory) return null
        val matches = icuDir.listFiles { f ->
            f.isFile && f.name.startsWith("icudt") && f.name.endsWith(".dat")
        }?.sortedByDescending { it.name } ?: emptyList()
        return matches.firstOrNull()?.toPath()
    }

    /**
     * `data/keyboards/` 내 `.kcm` 키맵 파일 절대 경로 목록 (Bridge.init 의 String[] keyboardPaths 인자).
     * Kotlin 의 nested block-comment 규칙 회피를 위해 glob 표기(`/` 다음 별표) 사용 금지.
     */
    fun listKeyboardPaths(): List<String> {
        val keyboardsDir = distDir.resolve(LayoutlibPaths.KEYBOARDS_DIR).toFile()
        if (!keyboardsDir.isDirectory) return emptyList()
        return keyboardsDir.listFiles { f -> f.isFile && f.name.endsWith(".kcm") }
            ?.sortedBy { it.name }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    /**
     * 번들 디렉토리의 필수 구성요소 존재 여부 검사.
     * 하나라도 누락되면 UnrenderableReason 으로 매핑되는 Result 반환.
     *
     * 08 §7.5 정명화: layoutlib + layoutlib-api 둘 다 필수 (W1D4-R3 정정).
     * layoutlib-api JAR 의 parent 클래스(`com.android.ide.common.rendering.api.Bridge` 등)가
     * 없으면 Bridge 클래스 로드가 NoClassDefFoundError 로 실패.
     */
    fun validate(): ValidationResult {
        val missing = mutableListOf<String>()
        val dataDir = distDir.resolve("data")

        if (!findLayoutlibJar().exists) missing += "layoutlib-*.jar (root of dist dir, Maven com.android.tools.layoutlib:layoutlib)"
        if (!findLayoutlibApiJar().exists) missing += "layoutlib-api-*.jar (root of dist dir, Maven com.android.tools.layoutlib:layoutlib-api)"
        if (!dataDir.resolve("res").isDirectory()) missing += "data/res/"
        if (!dataDir.resolve("fonts").isDirectory()) missing += "data/fonts/"
        if (!dataDir.resolve("icu").isDirectory()) missing += "data/icu/"

        return if (missing.isEmpty()) ValidationResult.Ok
        else ValidationResult.MissingComponents(missing.toList())
    }

    /**
     * distDir 바로 아래에서 main Bridge JVM JAR 을 찾음 (Maven 좌표 com.android.tools.layoutlib:layoutlib).
     * 여러 개 있으면 lexicographic 최신(큰 이름) 선택 — 버전 pin 은 번들러가 책임.
     *
     * sibling 좌표 -api/-runtime/-resources 는 별도 메서드로 제공 — 글롭 충돌 회피 (08 §7.5).
     */
    fun findLayoutlibJar(): JarLookup {
        val jars = distDir.toFile().listFiles { f ->
            f.isFile && f.name.startsWith("layoutlib-") && f.name.endsWith(".jar")
                && !f.name.startsWith("layoutlib-api-")
                && !f.name.startsWith("layoutlib-runtime-")
                && !f.name.startsWith("layoutlib-resources-")
        }?.sortedByDescending { it.name } ?: emptyList()
        return JarLookup(path = jars.firstOrNull()?.toPath(), exists = jars.isNotEmpty())
    }

    /**
     * distDir 바로 아래에서 API surface JAR 을 찾음 (Maven 좌표 com.android.tools.layoutlib:layoutlib-api).
     * Bridge 의 parent 클래스 + ILayoutLog 등 public API 인터페이스 포함 — Bridge 로드에 필수.
     */
    fun findLayoutlibApiJar(): JarLookup {
        val jars = distDir.toFile().listFiles { f ->
            f.isFile && f.name.startsWith("layoutlib-api-") && f.name.endsWith(".jar")
        }?.sortedByDescending { it.name } ?: emptyList()
        return JarLookup(path = jars.firstOrNull()?.toPath(), exists = jars.isNotEmpty())
    }

    /**
     * 격리된 URLClassLoader 구성.
     *
     * W2D6-FATJAR (08 §7.7): parent = system classloader 로 변경.
     * 이전에는 platform classloader 를 parent 로 써서 worker runtime classpath 의
     * Guava/kxml2/ICU4J 가 Bridge 내부 reflection 에서 보이지 않아 NoClassDefFoundError 발생.
     * system classloader 는 worker runtimeOnly deps 를 포함하므로 Bridge.init 이 정상 진행된다.
     *
     * 경계: Kotlin stdlib 가 system classloader 에도 보이므로 layoutlib 내부 클래스가
     * worker 의 stdlib 를 사용하게 됨 — Kotlin 1.9.x 단일 버전 환경(W2D6)에서는 안전하나
     * W3+ classpath 확장 시 재검토 필요.
     *
     * W2D7-F1 (페어 리뷰 FULL CONVERGENCE): layoutlib-api JAR URL 은 **포함하지 않음**.
     * :layoutlib-worker 의 implementation dep 으로 system CL 에 이미 존재하므로, parent-first
     * delegation 으로 단일 Class 객체가 보장된다. 자식 URL 에도 추가하면 (이전 패턴) parent-first
     * 로 shadow 되지만, loader 정책이 바뀌면 ClassCastException 트랩. single source of truth.
     * layoutlib 본체 (Bridge 구현) 는 dist JAR 에서만 로드 — API 는 system CL.
     */
    fun createIsolatedClassLoader(): ClassLoader {
        val mainJar = findLayoutlibJar().path
            ?: error("layoutlib JAR 이 없음: ${validate()}")
        // findLayoutlibApiJar() 는 validate() 경로를 위해 유지하되, 여기서는 URL 에 넣지 않는다.
        // native lib 는 System.load 로 따로 로드 (Bridge.init 내부) — 여기서는 JAR 만 추가.
        val urls = arrayOf<URL>(mainJar.toUri().toURL())
        return URLClassLoader(urls, ClassLoader.getSystemClassLoader())
    }

    sealed class ValidationResult {
        data object Ok : ValidationResult()
        data class MissingComponents(val components: List<String>) : ValidationResult()
    }

    data class JarLookup(val path: Path?, val exists: Boolean)
}

/** 번들 디렉토리 내 `data/` 하위 경로들을 표준화된 상수로 제공. */
object LayoutlibPaths {
    const val DATA_DIR = "data"
    const val RES_DIR = "data/res"
    const val FONTS_DIR = "data/fonts"
    const val KEYBOARDS_DIR = "data/keyboards"
    const val ICU_DIR = "data/icu"
    const val LINUX_LIB64_DIR = "data/linux/lib64"
    const val BUILD_PROP = "data/build.prop"
}
