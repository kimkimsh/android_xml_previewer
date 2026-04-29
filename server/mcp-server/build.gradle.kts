// :mcp-server — Claude Code MCP stdio 엔트리.
// 이 모듈이 전체 애플리케이션 합본 (fat jar) 을 생성.

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
    implementation(project(":layoutlib-worker"))  // W2D6-FATJAR: LayoutlibRenderer wired into Main
    implementation(project(":cli"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
}

application {
    mainClass.set("dev.axp.mcp.MainKt")
    applicationName = "axp-server"
}

// 단일 실행 JAR 을 server/build/libs/axp-server.jar 로 출력 (plugin.json 의 mcpServers 경로와 일치)
tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Assembles a single executable JAR for plugin distribution"
    archiveBaseName.set("axp-server")
    archiveClassifier.set("")
    manifest { attributes["Main-Class"] = "dev.axp.mcp.MainKt" }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
}
