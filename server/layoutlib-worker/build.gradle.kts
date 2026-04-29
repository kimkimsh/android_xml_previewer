// :layoutlib-worker — L1 per-device 워커 프로세스 (07 §2.7 프로세스 격리).
// 런타임 classpath 에 layoutlib-dist/android-34 의 layoutlib JAR 을 추가해야 함.
// 빌드 타임에 JAR 이 없어도 컴파일 가능하도록 compileOnly + classpath 는 런처가 주입.

plugins {
    id("axp.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":render-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)

    // W2D7-RENDERSESSION (08 §7.7.1 item 3b): LayoutlibCallback / AssetRepository /
    // ILayoutPullParser subclass 를 :layoutlib-worker 소스에 직접 작성하기 위함.
    // layoutlib 본체 (Bridge 등) 는 dist JAR 에서 reflection 으로 로드하지만,
    // public API surface (`com.android.ide.common.rendering.api.*`) 는 system CL
    // 에 존재해야 isolated URLClassLoader 의 parent-first delegation 으로 하나의 Class
    // 객체로 결합된다 — Bridge 가 기대하는 subclass 타입 identity 일치.
    // W2D7-F1 (페어 리뷰): LayoutlibBootstrap.createIsolatedClassLoader() 에서 layoutlib-api
    // URL 은 제거하여 single source of truth 유지.
    implementation("com.android.tools.layoutlib:layoutlib-api:31.13.2")

    // layoutlib-dist 의 JAR 은 런타임에 -cp 로 주입. compileOnly 로도 두지 않음
    // (reflection-only 접근 — 07 §2.3).

    // W2D6-FATJAR (08 §7.7): layoutlib 14.x 의 런타임 transitive 의존을 worker 의 runtime
    // classpath 에 두어 BridgeInitIntegrationTest Tier2 (실제 Bridge.init 호출) 가 통과하게 함.
    // layoutlib 본체 JAR 은 disk 번들에서 reflection 으로 로드 — 여기 선언된 deps 는 Bridge
    // 내부가 import 하는 클래스들이다.
    //
    // 08 §7.7.1 F-6 marker — post-W2D6 pom-resolved refactor candidate:
    // 아래 3개 좌표/버전은 layoutlib 14.0.11 의 transitive 실 해석을 대체하기 위해 W2D6 범위
    // 내에서 임시 pin. 올바른 canonical 은 `com.android.tools.layoutlib:layoutlib:14.0.11` 의
    // pom 을 resolve 하여 그 runtime 좌표/버전을 그대로 채택하는 것. task POST-W2D6-POM-RESOLVE
    // 에서 교체 예정. 본 pin 이 tests 를 통과시키는 것은 Guava API 호환범위에 우연히 들어왔기
    // 때문이며, layoutlib 업그레이드 시 실패 가능.
    runtimeOnly("com.google.guava:guava:32.1.3-jre")
    // W2D7-RENDERSESSION: kxml2 는 layoutlib transitive 로 runtime 필요이면서, 동시에
    // `org.xmlpull.v1.XmlPullParser` (ILayoutPullParser 의 parent) 를 우리 session 어댑터가
    // 컴파일 타임에 참조하므로 implementation 으로 승격.
    implementation("net.sf.kxml:kxml2:2.3.0")
    runtimeOnly("com.ibm.icu:icu4j:73.2")

    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
}

application {
    mainClass.set("dev.axp.layoutlib.worker.MainKt")
    applicationName = "axp-layoutlib-worker"
}

// W2D7-RENDERSESSION: Bridge 가 process-global static state (sInit, sRMap, sNativeCrash) 를 들고
// 있어 여러 integration test 가 같은 JVM 을 공유하면 "이미 초기화됨" 이 테스트 간에 전파된다.
// Tier2 (best-effort Bridge.init) 가 "초기 init 호출" 을 전제로 true 를 기대하므로, 테스트 클래스
// 단위로 JVM 을 fork 해서 각 integration test 가 독립적으로 Bridge 상태를 구성하도록 강제.
// unit test (no integration tag) 는 forkEvery 비활성 — 빠른 실행 유지.
tasks.named<Test>("test") {
    val includeTagsProp = providers.gradleProperty("includeTags").orNull
    if (!includeTagsProp.isNullOrBlank() && includeTagsProp.contains("integration")) {
        setForkEvery(1L)
    }
}
