package dev.axp.layoutlib.worker

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * W3D2 integration cleanup (Codex F3 carry) — fixture root (XML 레이아웃 루트) 탐지.
 *
 * override 제공 시 명시 경로 검증 후 사용, 없으면 CANDIDATE_ROOTS × FIXTURE_SUBPATH
 * 순차 탐색. 모두 실패 시 null.
 *
 * 이전에는 LayoutlibRenderer.companion.defaultFixtureRoot() 에 있었으나, CLI 와
 * 테스트 양쪽에서 사용하도록 독립 object 로 추출.
 *
 * W3D3 (round 2 페어): locateModuleRoot 추가 — sample-app 모듈 루트 (= layout 디렉토리의 5
 * ancestors up). FIXTURE_MODULE_SUBPATH 신규 상수.
 */
object FixtureDiscovery {
    const val FIXTURE_SUBPATH = "fixture/sample-app/app/src/main/res/layout"
    const val FIXTURE_MODULE_SUBPATH = "fixture/sample-app"

    private const val CANDIDATE_ROOT_CWD = ""
    private const val CANDIDATE_ROOT_PARENT = ".."
    private const val CANDIDATE_ROOT_GRANDPARENT = "../.."

    val CANDIDATE_ROOTS: List<String> = listOf(
        CANDIDATE_ROOT_CWD,
        CANDIDATE_ROOT_PARENT,
        CANDIDATE_ROOT_GRANDPARENT,
    )

    fun locate(override: Path?): Path? = locateInternal(
        override = override,
        userDir = System.getProperty("user.dir"),
        candidateRoots = CANDIDATE_ROOTS,
    )

    fun locateModuleRoot(override: Path?): Path? = locateInternalModuleRoot(
        override = override,
        userDir = System.getProperty("user.dir"),
        candidateRoots = CANDIDATE_ROOTS,
    )

    /**
     * 기존 3-arg locateInternal — FixtureDiscoveryTest 5 tests 의 호출부 유지.
     * subpath = FIXTURE_SUBPATH.
     */
    internal fun locateInternal(
        override: Path?,
        userDir: String,
        candidateRoots: List<String>,
    ): Path? = locateInternalGeneric(override, userDir, candidateRoots, FIXTURE_SUBPATH)

    internal fun locateInternalModuleRoot(
        override: Path?,
        userDir: String,
        candidateRoots: List<String>,
    ): Path? = locateInternalGeneric(override, userDir, candidateRoots, FIXTURE_MODULE_SUBPATH)

    internal fun locateInternalGeneric(
        override: Path?,
        userDir: String,
        candidateRoots: List<String>,
        subpath: String,
    ): Path? {
        if (override != null)
        {
            require(Files.isDirectory(override))
            {
                "fixture root 경로가 디렉토리가 아님 또는 존재하지 않음: $override"
            }
            return override
        }
        val candidates: List<Path> = candidateRoots.flatMap { root ->
            if (root.isEmpty())
            {
                listOf(
                    Paths.get(subpath),
                    Paths.get(userDir, subpath),
                )
            }
            else
            {
                listOf(
                    Paths.get(root, subpath),
                    Paths.get(userDir, root, subpath),
                )
            }
        }
        return candidates.firstOrNull { Files.isDirectory(it) }
    }
}
