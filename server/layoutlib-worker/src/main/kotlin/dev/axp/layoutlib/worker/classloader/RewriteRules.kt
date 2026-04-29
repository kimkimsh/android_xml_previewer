package dev.axp.layoutlib.worker.classloader

import org.objectweb.asm.commons.Remapper

/**
 * layoutlib 14.0.11 의 자체 build pipeline 이 의도적으로 _Original_ prefix 만 publish 하고
 * 외부용 SHIM 을 미포함시킨 클래스들의 매핑. host-JVM 환경에서 AAR bytecode 의
 * `android/os/Build` reference 를 layoutlib 의 실재 `android/os/_Original_Build` 로 rewrite.
 *
 * Round 1 페어 리뷰 (Claude empirical) 의 critical finding: layoutlib JAR 의 25개
 * `_Original_*` prefix 클래스 중 21개 (SurfaceView/WebView/ServiceManager/WindowManagerImpl/
 * TextServicesManager) 는 *non-prefixed 버전도 함께 존재* — layoutlib 의 의도된 dual-publish.
 * 본 NAME_MAP 은 non-prefixed 가 *부재한* Build family 4 entries 만 포함.
 */
internal object RewriteRules {

    val NAME_MAP: Map<String, String> = mapOf(
        "android/os/Build" to "android/os/_Original_Build",
        "android/os/Build\$Partition" to "android/os/_Original_Build\$Partition",
        "android/os/Build\$VERSION" to "android/os/_Original_Build\$VERSION",
        "android/os/Build\$VERSION_CODES" to "android/os/_Original_Build\$VERSION_CODES",
    )

    val REMAPPER: Remapper = object : Remapper() {
        override fun map(internalName: String): String =
            NAME_MAP[internalName] ?: internalName
    }
}
