plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fixture"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fixture"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = false  // W1D1 fixture 는 release 도 unminified (08 §2.5 release 미지원)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 8.x 에서 `kotlinOptions` 블록은 deprecated (향후 제거). canonical 대체:
// kotlin-android plugin 의 kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
}

// W3D3-L3-CLASSLOADER (round 2 페어): server/layoutlib-worker 가 sample-app 의 resolved
// runtime classpath 를 manifest 로 받아 자체 URLClassLoader 를 구성한다. 본 task 가 single
// source of truth — Gradle modules-2 cache 의 transforms-* hash dir 불안정성을 회피.
val axpClasspathManifest = layout.buildDirectory.file("axp/runtime-classpath.txt")
val axpEmitClasspath = tasks.register("axpEmitClasspath") {
    val cpProvider = configurations.named("debugRuntimeClasspath")
    inputs.files(cpProvider)
    outputs.file(axpClasspathManifest)
    doLast {
        val cp = cpProvider.get()
        val artifacts = cp.resolvedConfiguration.resolvedArtifacts
            .map { it.file.absolutePath }
            .distinct()
            .sorted()
        val outFile = axpClasspathManifest.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(artifacts.joinToString("\n"))
    }
}
// W3D3 round 2 정정: AGP 8.x assembleDebug 는 variant API 등록 → top-level tasks.named
// 시점에 미존재 → UnknownTaskException. afterEvaluate 필수 (empirical 검증).
afterEvaluate {
    tasks.named("assembleDebug").configure { finalizedBy(axpEmitClasspath) }
}
