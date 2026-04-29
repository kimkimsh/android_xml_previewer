// axp.kotlin-common — 모든 Kotlin JVM 모듈의 공통 컨벤션
//
// 근거: 08 §5 item 5. 모든 서브모듈이 동일한 JVM target + kotlin stdlib + 테스트 러너.
// 각 모듈에서 `plugins { id("axp.kotlin-common") }` 로 적용.

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// 저장소는 settings.gradle.kts (dependencyResolutionManagement) 에서 중앙화.

// version catalog 참조
val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(libs.findVersion("jvm").get().requiredVersion.toInt())
}

dependencies {
    add("implementation", libs.findLibrary("kotlin-stdlib").get())
    add("implementation", libs.findLibrary("slf4j-api").get())

    // 테스트는 모든 모듈 공통
    add("testImplementation", libs.findBundle("test-core").get())
    add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:${libs.findVersion("junit-jupiter").get().requiredVersion}")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}

// JUnit Platform tag filter — `-PincludeTags=integration` 로 통합 테스트만 실행.
// 기본 동작: @Tag("integration") 제외 → unit suite 가 native lib / 디스크 의존 없이 빠르게 실행.
// W1D4 BridgeInitIntegrationTest 가 이 분기를 사용 (08 §7.5 / W1D4-R3).
tasks.withType<Test>().configureEach {
    val includeTagsProp = providers.gradleProperty("includeTags").orNull
    useJUnitPlatform {
        if (includeTagsProp.isNullOrBlank())
        {
            excludeTags("integration")
        }
        else
        {
            includeTags(*includeTagsProp.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
        }
    }
}
