// :http-server — Ktor + SSE 10-event taxonomy + 정적 viewer 서빙.

plugins {
    id("axp.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":render-core"))

    implementation(libs.bundles.ktor.server)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
}
