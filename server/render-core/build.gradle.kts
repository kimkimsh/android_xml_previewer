// :render-core — Dispatcher, cache, project locator, merged resource resolver.
// L1/L3 구현은 각각 :layoutlib-worker / :emulator-harness 로 분리.

plugins {
    id("axp.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(project(":protocol"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.twelvemonkeys.imageio.core)
}
