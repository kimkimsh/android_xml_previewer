package dev.axp.layoutlib.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * W1D4-R3 — Bridge JVM 클래스 로드 + `init(...)` 시그니처 검증 + best-effort 실행.
 *
 * 08 §7.5 정명화: layoutlib JAR 의 정확한 Maven 좌표는 `com.android.tools.layoutlib:layoutlib`
 * (별도 좌표 `:layoutlib-api` 와 혼동 금지). 본 테스트는 Bootstrap 의 글롭이 Bridge JVM JAR 을
 * 정확히 찾는지 + reflection 으로 `init` 메서드 시그니처가 layoutlib 14.x 의 7-param 형식인지 확인.
 *
 * Tier 1 (must-pass): 디스크 번들 검증, 격리 ClassLoader, Bridge 클래스 로드, init 메서드 발견.
 * Tier 2 (best-effort): 실제 `Bridge.init(...)` 호출 시도. native lib 로딩/InvocationTargetException
 *   등 알려진 실패는 ABORT 로 분류 (테스트 실패 아님). 실제 렌더는 W2 이후.
 *
 * @Tag("integration") — `./gradlew :layoutlib-worker:test` 기본 실행에서는 제외.
 *   `./gradlew :layoutlib-worker:test -PincludeTags=integration` 로 명시 실행.
 *
 * 디스크 의존: `server/libs/layoutlib-dist/android-34/` 에 layoutlib-*.jar + data/ 가 채워져
 * 있어야 함 (W1D3-R2 결과). dist 가 비어있으면 assumeTrue 로 SKIP.
 */
@Tag("integration")
class BridgeInitIntegrationTest
{
    /** Bridge 클래스의 Fully Qualified Name (layoutlib 의 entry). */
    companion object
    {
        private const val BRIDGE_FQN = "com.android.layoutlib.bridge.Bridge"
        private const val ILAYOUT_LOG_FQN = "com.android.ide.common.rendering.api.ILayoutLog"
        private const val INIT_METHOD = "init"
        private const val EXPECTED_INIT_PARAM_COUNT = 7
        private const val DIST_REL_PATH = "libs/layoutlib-dist/android-34"
    }

    @Test
    fun `tier1 — bundle validate Ok against on-disk dist`()
    {
        val distDir = locateDistDir()
        val boot = LayoutlibBootstrap(distDir)
        val result = boot.validate()
        assertEquals(
            LayoutlibBootstrap.ValidationResult.Ok, result,
            "W1D3-R2 dist 가 완전해야 함 (07 §2.2 / 08 §7.5): $result"
        )
    }

    @Test
    fun `tier1 — Bridge class loads via isolated classloader`()
    {
        val boot = LayoutlibBootstrap(locateDistDir())
        val cl = boot.createIsolatedClassLoader()
        val bridgeClass = Class.forName(BRIDGE_FQN, false, cl)
        assertNotNull(bridgeClass, "Bridge 클래스가 격리 cl 에서 로드되어야 함")
        assertTrue(
            !bridgeClass.isInterface,
            "Bridge 는 concrete class 여야 함: ${bridgeClass.modifiers}"
        )
    }

    @Test
    fun `tier1 — Bridge init method discoverable with 7 parameters`()
    {
        val boot = LayoutlibBootstrap(locateDistDir())
        val cl = boot.createIsolatedClassLoader()
        val bridgeClass = Class.forName(BRIDGE_FQN, false, cl)

        val initMethod = bridgeClass.declaredMethods.firstOrNull { it.name == INIT_METHOD }
        assertNotNull(
            initMethod,
            "Bridge.$INIT_METHOD 메서드가 layoutlib 14.x 에서 declared 여야 함"
        )

        val params = initMethod!!.parameterTypes
        assertEquals(
            EXPECTED_INIT_PARAM_COUNT, params.size,
            "layoutlib 14.x 의 init 시그니처는 7-param: 실제 ${params.size}개 — ${params.joinToString { it.simpleName }}"
        )

        // 시그니처 정확 검증 (canonical 14.x):
        // (Map<String,String>, File, String, String, String[], Map<String,Map<String,Integer>>, ILayoutLog) -> boolean
        assertEquals(java.util.Map::class.java, params[0])
        assertEquals(File::class.java, params[1])
        assertEquals(String::class.java, params[2])
        assertEquals(String::class.java, params[3])
        assertEquals(Array<String>::class.java, params[4])
        assertEquals(java.util.Map::class.java, params[5])
        assertEquals(ILAYOUT_LOG_FQN, params[6].name)
        assertEquals(java.lang.Boolean.TYPE, initMethod.returnType)
    }

    @Test
    fun `tier2 — best-effort Bridge init invocation`()
    {
        val boot = LayoutlibBootstrap(locateDistDir())
        val cl = boot.createIsolatedClassLoader()
        val bridgeClass = Class.forName(BRIDGE_FQN, false, cl)
        val initMethod = bridgeClass.declaredMethods.first { it.name == INIT_METHOD }

        // 인자 구성 — Bootstrap 헬퍼들로 build.prop / icu / keyboards 위치 주입.
        val platformProps = boot.parseBuildProperties()
        val fontDir = boot.fontsDir().toFile()
        val nativeLibPath = boot.nativeLibDir().absolutePathString()
        val icuPath = boot.findIcuDataFile()?.absolutePathString()
        assumeTrue(icuPath != null, "icu data 파일이 있어야 init 시도 가능 (data/icu/ 의 icudt 파일)")
        val keyboardPaths = boot.listKeyboardPaths().toTypedArray()
        val enumValueMap = mutableMapOf<String, MutableMap<String, Int>>()

        // 모든 reflection 호출(생성자 + ILayoutLog 프록시 + invoke) 을 한 try 로 묶어
        // 어느 단계에서든 transitive 의존(Guava 등) / native lib / Bridge 내부 예외 가
        // 발생하면 SKIP(ABORT) 처리. W1D4 spec 의 best-effort 정의.
        try
        {
            val bridgeInstance = bridgeClass.getDeclaredConstructor().newInstance()
            val logInterface = Class.forName(ILAYOUT_LOG_FQN, false, cl)
            val logProxy: Any = Proxy.newProxyInstance(
                cl,
                arrayOf(logInterface),
                BridgeInitNoopLogHandler()
            )

            val initOk = initMethod.invoke(
                bridgeInstance,
                platformProps,
                fontDir,
                nativeLibPath,
                icuPath,
                keyboardPaths,
                enumValueMap,
                logProxy
            ) as Boolean

            assertTrue(initOk, "Bridge.init 가 true 를 반환해야 함")
            try { bridgeClass.getDeclaredMethod("dispose").invoke(bridgeInstance) } catch (_: Throwable) {}
        }
        catch (e: java.lang.reflect.InvocationTargetException)
        {
            val cause = e.cause
            assumeTrue(
                false,
                "Bridge.init 내부 호출 실패 (best-effort, W2 에 본격 처리): ${cause?.javaClass?.simpleName} — ${cause?.message?.take(160)}"
            )
        }
        catch (e: NoClassDefFoundError)
        {
            // layoutlib 의 transitive 의존(Guava, kxml2, ICU4J 등) 미포함 — W2 fatJar 로 해결.
            assumeTrue(false, "transitive 의존 누락 (W2 fatJar 빌드로 해결): ${e.message?.take(160)}")
        }
        catch (e: ClassNotFoundException)
        {
            assumeTrue(false, "transitive 클래스 누락: ${e.message?.take(160)}")
        }
        catch (e: UnsatisfiedLinkError)
        {
            assumeTrue(false, "native lib 로딩 실패 — java.library.path 미설정 (W2 forked-test 로 해결): ${e.message?.take(160)}")
        }
        catch (e: LinkageError)
        {
            assumeTrue(false, "JAR 링크 실패 — 격리 classloader 한계: ${e.message?.take(160)}")
        }
    }

    /**
     * Working directory 기준으로 `libs/layoutlib-dist/android-34` 위치 결정.
     * Gradle 은 :layoutlib-worker:test 를 모듈 디렉토리에서 실행 → `../libs/...` 로 해결.
     * IDE 직접 실행도 같은 worktree 기준이면 fallback 으로 cwd 상위 디렉토리 탐색.
     */
    private fun locateDistDir(): Path
    {
        val candidates = listOf(
            Paths.get("../libs", "layoutlib-dist", "android-34"),
            Paths.get("server/libs/layoutlib-dist/android-34"),
            Paths.get(System.getProperty("user.dir"), "../libs/layoutlib-dist/android-34")
        )
        val found = candidates.firstOrNull { it.exists() && it.isDirectory() }
        assumeTrue(
            found != null,
            "layoutlib-dist 디렉토리를 찾을 수 없음 — W1D3-R2 다운로드를 먼저 수행. cwd=${System.getProperty("user.dir")}"
        )
        return found!!.toAbsolutePath().normalize()
    }
}

/**
 * Bridge 가 호출하는 ILayoutLog 메서드들을 모두 무시(no-op).
 * Tier2 best-effort 호출에서만 사용 — 실제 W2 워커는 logback 백엔드와 연결.
 */
private class BridgeInitNoopLogHandler : InvocationHandler
{
    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any?
    {
        // void 외 반환 타입 처리 (defensive — ILayoutLog 의 모든 method 는 void 지만 forward-compat).
        return when (method.returnType.name)
        {
            "void" -> null
            "boolean" -> false
            else ->
            {
                if (Modifier.isStatic(method.modifiers)) null
                else if (method.returnType.isPrimitive) 0
                else null
            }
        }
    }
}
