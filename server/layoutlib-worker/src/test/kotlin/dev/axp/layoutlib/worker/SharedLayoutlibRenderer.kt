package dev.axp.layoutlib.worker

import dev.axp.protocol.render.PngRenderer
import java.nio.file.Path

/**
 * W3D2 integration cleanup (Codex F4 carry) — test-only per-JVM-fork LayoutlibRenderer singleton.
 *
 * **왜 필요한가 (W2D7 L4, in-class scope)**:
 *   `System.load(libandroid_runtime.so)` 는 프로세스당 1회만 허용. 동일 test class 의
 *   여러 @Test 메서드 가 각자 별도 `LayoutlibRenderer` 를 만들면 두 번째부터
 *   `UnsatisfiedLinkError` 가 발생 → LayoutlibBootstrap 이 catch 해서 조용히 삼킴 →
 *   Bridge 의 native 바인딩이 깨진 상태로 render 시도 → 진단 어려운 null 반환.
 *   Gradle `forkEvery(1L)` 가 test class 단위 JVM 격리를 보장하므로 cross-class 는 문제
 *   아니나, in-class 는 여전히 이 singleton 으로 방어 필요.
 *
 * **해결**:
 *   - JVM fork 내에서 유일한 instance.
 *   - bound 된 dist/fixture 와 다른 args 로 호출 시 `IllegalStateException`.
 *
 * **Production 과의 관계**:
 *   Production (`Main.kt.chooseRenderer`) 은 mode 당 1회 `LayoutlibRenderer` 를 생성
 *   하므로 이 singleton 을 사용하지 않는다. 이 object 는 test sourceset 에만 존재.
 *
 * **resetForTestOnly() 미포함 (MF-F1)**: 초기 설계에 있었으나 native lib 는 `System.load`
 * 이후 JVM 종료까지 unload 불가이므로 instance 를 null 로 만들어도 실질적 isolation 효과
 * 없음 → dead API. YAGNI 로 제거. cross-class 격리는 Gradle forkEvery(1L) 이 담당.
 */
object SharedLayoutlibRenderer
{
    @Volatile private var instance: LayoutlibRenderer? = null
    @Volatile private var boundArgs: RendererArgs? = null

    @Synchronized
    fun getOrCreate(
        distDir: Path,
        fixtureRoot: Path,
        sampleAppModuleRoot: Path,
        themeName: String,
        fallback: PngRenderer?,
    ): LayoutlibRenderer
    {
        val requested = RendererArgs(distDir, fixtureRoot, sampleAppModuleRoot, themeName)
        SharedRendererBinding.verify(boundArgs, requested)
        instance?.let { return it }
        val created = LayoutlibRenderer(
            distDir = distDir,
            fixtureRoot = fixtureRoot,
            sampleAppModuleRoot = sampleAppModuleRoot,
            themeName = themeName,
            fallback = fallback,
        )
        instance = created
        boundArgs = requested
        return created
    }
}
