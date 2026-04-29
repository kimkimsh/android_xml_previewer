package dev.axp.layoutlib.worker

/**
 * LayoutlibRenderer 렌더링 상수.
 *
 * W2D6-FATJAR: phone_normal preset (720x1280 px, xhdpi).
 * PlaceholderPngConstants 와 동일 값 — :layoutlib-worker 는 :http-server 에 의존하지 않으므로
 * 별도 선언. 08 §7.7 blocker #3.
 */
object LayoutlibRendererConstants {
    /** phone_normal 폭 (px). 360dp × 2 (xhdpi). */
    const val RENDER_WIDTH_PX = 720

    /** phone_normal 높이 (px). 640dp × 2 (xhdpi). */
    const val RENDER_HEIGHT_PX = 1280

    /** PNG 출력 포맷 (ImageIO writer). */
    const val IMAGE_FORMAT_PNG = "PNG"

    /** AWT headless 시스템 프로퍼티 키 — 서버/CI 환경에서 디스플레이 없이 AWT 사용. */
    const val HEADLESS_PROPERTY_KEY = "java.awt.headless"

    /** AWT headless 활성화 값. */
    const val HEADLESS_PROPERTY_VALUE = "true"

    /** layoutlib Bridge FQCN. */
    const val BRIDGE_FQN = "com.android.layoutlib.bridge.Bridge"

    /** layoutlib ILayoutLog 인터페이스 FQCN (Bridge.init 의 마지막 파라미터). */
    const val ILAYOUT_LOG_FQN = "com.android.ide.common.rendering.api.ILayoutLog"

    /** Bridge native library 파일명 (linux). */
    const val NATIVE_LIB_NAME = "libandroid_runtime.so"

    /** Bridge.init 메서드 이름. */
    const val BRIDGE_INIT_METHOD = "init"

    /** Bridge.dispose 메서드 이름. */
    const val BRIDGE_DISPOSE_METHOD = "dispose"

    /** 렌더 결과 레이블 텍스트 x 오프셋 (px). */
    const val LABEL_X_PX = 20

    /** 렌더 결과 레이블 텍스트 y 오프셋 (px). */
    const val LABEL_Y_PX = 40
}
