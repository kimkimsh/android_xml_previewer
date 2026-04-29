package dev.axp.protocol

/**
 * 프로토콜/서버 버전 상수.
 *
 * - `SCHEMA_VERSION` : 07 §4.1 규칙 — MAJOR 변경은 클라이언트 reject, MINOR 는 additive-only.
 * - `SERVER_VERSION` : 빌드 시 Gradle 에서 리소스로 주입하는 패턴을 v1.5 로 미룸. v1 은 상수.
 */
object Versions {
    const val SCHEMA_VERSION: String = "1.0"
    const val SERVER_VERSION: String = "0.1.0-SNAPSHOT"
    const val WORKER_VERSION: String = "0.1.0-SNAPSHOT"
}
