// android-xml-previewer — MCP 서버 Gradle multi-module 스캐폴드
// 근거: 08 §5 item 5 (Week 1 Day 1 canonical 체크리스트)
//
// 모듈 레이어링 원칙:
//   :protocol        — 의존성-free. MCP envelope, RenderRequest/Response, ErrorEnvelope,
//                       UnrenderableReason 19개 enum (08 §7 errata), WorkerRequest/Response sealed class (IPC).
//   :render-core     — RenderDispatcher, RenderCache(21-field RenderKey — 08 §7.4 errata), 단일 플라이트 mutex,
//                       AndroidProjectLocator, MergedResourceResolver (07 §2.1). :protocol 의존.
//   :layoutlib-worker— L1 per-device 워커 프로세스(07 §2.7). LayoutlibSessionPool,
//                       PlaceholderCustomView, 폰트 라우팅. 독립 실행 바이너리.
//   :emulator-harness— L3 adb + harness APK push + DexPreflight + ChildFirstDexClassLoader
//                       + AppRenderContext 빌드(07 §3).
//   :http-server     — Ktor + SSE 10-event taxonomy(07 §4.3) + flock 포트 코디네이션(07 §4.6).
//   :cli             — axprev render / serve / setup-avd 서브커맨드 (F5, F7).
//   :mcp-server      — stdio 엔트리. :protocol + :render-core + :http-server + :cli 조합.

rootProject.name = "axp-server"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

include(":protocol")
include(":render-core")
include(":layoutlib-worker")
include(":emulator-harness")
include(":http-server")
include(":cli")
include(":mcp-server")
