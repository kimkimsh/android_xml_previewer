package dev.axp.layoutlib.worker

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * W3D2 integration cleanup (Codex F3 carry).
 *
 * layoutlib-dist 디렉토리를 탐지. override 가 있으면 명시 경로 검증 후 사용,
 * 없으면 CANDIDATE_ROOTS × LAYOUTLIB_DIST_SUBDIR 순차 탐색. 모두 실패 시 null.
 *
 * Main.kt (CLI) 와 LayoutlibRendererTier3MinimalTest (테스트) 양쪽이 사용하는
 * 공용 entry point. CLI 관점의 에러 메시지는 Main.kt 가 flag 이름을 포함해
 * 재포장한다 — 본 object 는 CliConstants 에 의존하지 않는다 (모듈 역방향 회피).
 */
object DistDiscovery {
    const val LAYOUTLIB_DIST_SUBDIR = "layoutlib-dist/android-34"

    private const val CANDIDATE_ROOT_SERVER_LIBS = "server/libs"
    private const val CANDIDATE_ROOT_PARENT_LIBS = "../libs"

    val CANDIDATE_ROOTS: List<String> = listOf(
        CANDIDATE_ROOT_SERVER_LIBS,
        CANDIDATE_ROOT_PARENT_LIBS,
    )

    fun locate(override: Path?): Path? = locateInternal(
        override = override,
        userDir = System.getProperty("user.dir"),
        candidateRoots = CANDIDATE_ROOTS,
    )

    internal fun locateInternal(
        override: Path?,
        userDir: String,
        candidateRoots: List<String>,
    ): Path? {
        if (override != null) {
            require(Files.isDirectory(override)) {
                "dist 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
            }
            return override
        }
        val candidates: List<Path> = candidateRoots.flatMap { root ->
            listOf(
                Paths.get(root, LAYOUTLIB_DIST_SUBDIR),
                Paths.get(userDir, root, LAYOUTLIB_DIST_SUBDIR),
            )
        }
        return candidates.firstOrNull { Files.isDirectory(it) }
    }
}
