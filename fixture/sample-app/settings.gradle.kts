// W1D1 fixture — Android Gradle Project 로 독립 빌드.
// 본 프로젝트는 :app 단일 모듈. 서버 Gradle 빌드와는 별개로 유지.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "axp-fixture"
include(":app")
