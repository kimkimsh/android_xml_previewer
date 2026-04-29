package dev.axp.layoutlib.worker.classloader

internal object ClassLoaderConstants {
    /** axpEmitClasspath Gradle task 가 emit 하는 manifest 의 sampleAppModuleRoot-relative 경로. */
    const val MANIFEST_RELATIVE_PATH = "app/build/axp/runtime-classpath.txt"

    /** AarExtractor cacheRoot (= sampleAppModuleRoot.resolve(AAR_CACHE_BASE_RELATIVE_PATH)) 의 base. */
    const val AAR_CACHE_BASE_RELATIVE_PATH = "app/build/axp"

    /** AarExtractor 의 stable 캐시 서브디렉토리 (cacheRoot 기준 상대). */
    const val AAR_CACHE_RELATIVE_DIR = "aar-classes"

    /** AAR ZIP 안에서 JVM 바이트코드 JAR 의 표준 entry 이름. */
    const val AAR_CLASSES_JAR_ENTRY = "classes.jar"

    /** AAR file 확장자 (manifest 검증 + 추출 분기). */
    const val AAR_EXTENSION = ".aar"

    /** JAR file 확장자 (manifest 검증). */
    const val JAR_EXTENSION = ".jar"

    /** AarExtractor 의 추출 결과 파일 suffix (artifactName 뒤에 붙음). */
    const val EXTRACTED_JAR_SUFFIX = ".jar"

    /** AarExtractor 의 atomic write 용 임시 파일 suffix. */
    const val TEMP_JAR_SUFFIX = ".jar.tmp"

    /** AarExtractor 의 캐시 키용 path digest 알고리즘. */
    const val SHA1_DIGEST_NAME = "SHA-1"

    /**
     * AGP 8.x 가 emit 하는 통합 R.jar 경로 (compile_and_runtime_not_namespaced_r_class_jar variant).
     * 본 경로는 AGP minor 버전 변경 시 깨질 수 있으나 8.x 안정 — 변경 시 본 상수만 갱신.
     */
    const val R_JAR_RELATIVE_PATH =
        "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
}
