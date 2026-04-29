package dev.axp.protocol.render

import kotlinx.serialization.Serializable

/**
 * 08 §1.4 확정 — **17-input** cache key.
 *
 * 구성: 06 §3.1 의 14개 canonical 필드 + 08 §1.4 에서 추가된 3개 (framework/merged-res/callback-classpath).
 *
 * 불변 입력 보장 (06 §3.2):
 *  - XML content 정규화: CRLF → LF, trailing whitespace strip, 끝 개행 통일
 *  - 상대 경로: projectRoot.relativize(xmlFile)
 *  - 폰트: server/libs/fonts 디렉토리 tarball sha256
 *  - layoutlib: 번들 JAR 파일 sha256 (첫 실행 시 계산 후 캐시)
 *
 * digest 는 canonical JSON 직렬화 후 sha256.
 */
@Serializable
data class RenderKey(
    // 1. Source content ------------------------------------------------------
    val xmlContentSha256: String,
    val normalizedRelativePath: String,
    val resourceTableSha256: String,

    // 2. Device / display ----------------------------------------------------
    val devicePreset: String,
    val themeId: String,
    val nightMode: Boolean,
    val fontScale: Float,
    val locale: String,

    // 3. Renderer layer + version -------------------------------------------
    val renderLayer: RenderLayer,
    val layoutlibJarSha256: String,
    val bundledFontPackSha256: String,
    val apiLevel: Int,

    // 4. Tool versions -------------------------------------------------------
    val aapt2Version: String,
    val sdkBuildToolsVersion: String,
    val jvmMajor: Int,

    // 5. L3 specific (L3 일 때만 채움) --------------------------------------
    val appApkSha256: String? = null,
    val harnessApkSha256: String? = null,
    val avdSystemImageFingerprint: String? = null,

    // 6. 08 §1.4 추가 필드 ---------------------------------------------------
    /** layoutlib-dist/android-34 디렉토리 전체 번들 해시 (framework res/fonts/icu/…). */
    val frameworkDataBundleSha256: String,
    /** res/ + 모든 transitive AAR 병합 결과 디렉토리 해시 (07 §2.1). */
    val mergedResourcesSha256: String,
    /** L3 에서 ChildFirstDexClassLoader 가 본 APK set 해시. L1 에서는 빈 문자열. */
    val callbackClasspathSha256: String
)

/**
 * 렌더가 수행된 레이어. Fallback 구분에 사용.
 */
@Serializable
enum class RenderLayer { LAYOUTLIB, EMULATOR }
