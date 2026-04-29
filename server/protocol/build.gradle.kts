// :protocol — 의존성-free (kotlin + kotlinx.serialization 만).
//
// 포함 타입:
//   - McpEnvelope / ErrorEnvelope (07 §4.1)
//   - RenderRequest / RenderResponse (07 §4.2)
//   - UnrenderableReason 19개 enum + Category (06 §4 + 08 §3.5 — 08 §7 errata: plan 의 "17" 은 산술 오류)
//   - WorkerRequest / WorkerResponse sealed class (08 §3.1 Worker IPC)
//   - SseEventEnvelope + 10개 이벤트 타입 (07 §4.3)

plugins {
    id("axp.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
