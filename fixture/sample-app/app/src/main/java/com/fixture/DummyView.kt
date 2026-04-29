package com.fixture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 08 §5 item 6 — W3/W4 L3 에스컬레이션 타겟 placeholder.
 *
 * 실제 앱에서 쓰이는 임의의 custom View 를 대변. 플러그인이:
 *   - L1 에서 class-not-found → PlaceholderCustomView(07 §2.5) 로 치환
 *   - L3 에서 DexClassLoader 로 실제 DummyView 로드 (07 §3.2 AppDexFactory2)
 * 를 검증하는 데 사용.
 *
 * 의도적으로 단순 — 내부 로직은 canvas 에 이름 찍기만.
 */
class DummyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = resources.displayMetrics.density * 18f
        textAlign = Paint.Align.CENTER
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f + textPaint.textSize / 3f
        canvas.drawText("DummyView", cx, cy, textPaint)
        canvas.drawRect(
            framePaint.strokeWidth,
            framePaint.strokeWidth,
            width - framePaint.strokeWidth,
            height - framePaint.strokeWidth,
            framePaint
        )
    }
}
