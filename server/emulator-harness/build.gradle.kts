// :emulator-harness — L3 구현 (호스트 사이드).
// adb/aapt2 호출, DexPreflight, preview 리소스 APK 빌드, harness APK 관리.
// harness APK 자체의 빌드는 별도 harness-apk/ 디렉토리(Android Gradle Project).

plugins {
    id("axp.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(project(":protocol"))
    implementation(project(":render-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.dexlib2)
}
