// 루트 프로젝트 빌드 — 공통 플러그인/리포지토리/테스트 설정만.
// 각 서브모듈의 구체 의존성은 subprojects { ... } 블록 대신 모듈별 build.gradle.kts 에 둔다
// (07 §7 레이어링: 모듈간 의존 명시성 유지).

plugins {
    // 루트는 Kotlin 플러그인을 직접 적용하지 않음. 서브모듈이 각자 적용.
    base
}

allprojects {
    group = "dev.axp"
    version = "0.1.0-SNAPSHOT"
}

// 저장소 선언은 settings.gradle.kts 의 dependencyResolutionManagement 에서 중앙화.
// (FAIL_ON_PROJECT_REPOS 정책으로 project 레벨에서 repositories 재선언 금지.)

subprojects {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = false
        }
    }
}
