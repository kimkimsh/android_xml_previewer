package dev.axp.layoutlib.worker

import java.nio.file.Path

/**
 * SharedLayoutlibRenderer / SharedRendererBinding 의 cache 키 + 인자 묶음.
 * W3D3-B4 (round 2 페어 리뷰): 기존 Pair<Path,Path> 가 3-path 로 확장 안 되어 도입.
 * W3D4 T8: themeName 추가 (4-tuple). LayoutlibRenderer ctor 의 5-tuple 중 fallback 을
 * 제외한 4 식별 인자 (dist + fixture + sampleAppRoot + theme) 가 args 동일성 검증 키.
 */
internal data class RendererArgs(
    val distDir: Path,
    val fixtureRoot: Path,
    val sampleAppModuleRoot: Path,
    val themeName: String,
)
