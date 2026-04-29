package dev.axp.layoutlib.worker

import java.nio.file.Path

/**
 * W3D2 integration cleanup (Codex F4 carry) — SharedLayoutlibRenderer 의 args 일관성
 * 검증용 pure helper. LayoutlibRenderer 생성 없이 단위 테스트 가능하도록 분리.
 *
 * W2D7 L4: native lib 는 프로세스당 1회만 로드되고 첫 dist 에 바인드됨. 동일
 * JVM 에서 다른 dist 로 singleton 을 재사용하면 진단 어려운 실패 발생 →
 * 두 번째 호출의 args 가 첫 호출과 동일해야 함.
 */
internal object SharedRendererBinding
{
    /**
     * 첫 바인드 (`bound == null`) 이면 silently 통과. 이후는 `bound == requested`
     * 여야 하며 불일치 시 [IllegalStateException].
     */
    fun verify(bound: Pair<Path, Path>?, requested: Pair<Path, Path>)
    {
        if (bound == null) return
        check(bound == requested) {
            "SharedLayoutlibRenderer args 불일치 — native lib 는 첫 바인드 args 에 고정. " +
                "bound=$bound requested=$requested"
        }
    }
}
