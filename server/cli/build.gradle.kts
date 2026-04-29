// :cli — axprev 바이너리 (F7). 서브커맨드: render / serve / setup-avd / clean-cache.

plugins {
    id("axp.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":render-core"))
    implementation(project(":emulator-harness"))
    implementation(project(":http-server"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
}

application {
    mainClass.set("dev.axp.cli.MainKt")
    applicationName = "axprev"
}
