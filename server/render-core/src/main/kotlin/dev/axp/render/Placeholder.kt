package dev.axp.render

/**
 * :render-core 모듈 placeholder.
 *
 * Week 1 Day 1 단계에서는 모듈 스캐폴드만 생성. 실제 타입 구현 순서:
 *   - Week 1 D5  : LayoutlibRenderer skeleton (Bridge.init 호출만)
 *   - Week 2 D6  : MergedResourceResolver (07 §2.1, AGP merged output 검출)
 *   - Week 2 D8  : LayoutFileWatcher (01 §6, debounce)
 *   - Week 3 D11 : RenderDispatcher (06 §5, single-flight mutex)
 *   - Week 3 D15 : RenderCache (17-input RenderKey 기반)
 *
 * 이 파일은 모듈 컴파일 통과를 위해 단일 top-level private 함수만 둔다.
 */
internal fun moduleMarker(): String = "render-core"
