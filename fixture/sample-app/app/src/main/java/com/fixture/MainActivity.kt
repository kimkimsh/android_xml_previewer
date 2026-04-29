package com.fixture

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 08 §5 item 6 fixture 의 단일 Activity.
 * Basic 레이아웃 렌더 + custom view 경로 에스컬레이션 양쪽을 같은 테마로 노출.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic)
    }
}
