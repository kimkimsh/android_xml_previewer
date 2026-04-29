// buildSrc — 컨벤션 플러그인을 Gradle plugin DSL 로 빌드
//
// version catalog 의 버전은 settings.gradle.kts 에서 읽어 Map 으로 전달 받는 방식을 쓰려 했으나,
// buildSrc 에서 findPlugin() 호출이 PluginContainer.findPlugin 과 시그니처 충돌 (8.7 기준).
// 대안: gradle/libs.versions.toml 를 직접 파싱하지 않고, 컨벤션 플러그인 DEPENDENCY 로서
//       kotlin-gradle-plugin 을 명시 버전으로 고정. 버전 동기화는 한 줄 바꿈으로 유지 가능.
//
// libs.versions.toml 의 `kotlin = "1.9.23"` 과 반드시 동일한 값을 여기에 유지.

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

val kotlinVersion = "1.9.23"  // gradle/libs.versions.toml 의 kotlin 버전과 반드시 일치

// Codex 페어 리뷰가 지적한 drift guard: build 타임에 catalog 값과 buildSrc 값을 비교.
// 불일치 시 configuration phase 에서 실패 → CI 가 즉시 검출.
// TOML 의 inline comment `#` 제거 + 양끝 따옴표/공백 정리.
val catalogKotlinVersion: String = run {
    val line = file("../gradle/libs.versions.toml")
        .readText()
        .lineSequence()
        .firstOrNull { it.trimStart().startsWith("kotlin ") && it.contains("=") && !it.contains("-") }
        ?: error("libs.versions.toml 에서 kotlin 버전 라인을 찾을 수 없음")
    val afterEq = line.substringAfter("=")
    val noComment = if ("#" in afterEq) afterEq.substringBefore("#") else afterEq
    noComment.trim().trim('"')
}

check(catalogKotlinVersion == kotlinVersion) {
    "buildSrc 의 kotlinVersion='$kotlinVersion' 이 libs.versions.toml 의 kotlin='$catalogKotlinVersion' 과 불일치.\n" +
    "두 값을 동일하게 유지하세요. (Codex W1D1 페어 리뷰 follow-up)"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
}
