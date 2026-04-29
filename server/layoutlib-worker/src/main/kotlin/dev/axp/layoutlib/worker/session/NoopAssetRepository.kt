package dev.axp.layoutlib.worker.session

import com.android.ide.common.rendering.api.AssetRepository
import java.io.IOException
import java.io.InputStream

/**
 * W2D7-RENDERSESSION — asset 접근이 없는 최소 구현.
 *
 * activity_minimal.xml 은 asset 또는 non-asset 파일을 참조하지 않음. isSupported=false 로
 * Bridge/Resources 가 asset lookup 경로를 일찍 포기하도록 유도.
 */
class NoopAssetRepository : AssetRepository() {

    override fun isSupported(): Boolean = false

    @Throws(IOException::class)
    override fun openAsset(path: String, mode: Int): InputStream {
        throw IOException("W2D7 minimal: asset 접근 비활성 — path=$path")
    }

    @Throws(IOException::class)
    override fun openNonAsset(cookie: Int, path: String, mode: Int): InputStream {
        throw IOException("W2D7 minimal: non-asset file 접근 비활성 — cookie=$cookie path=$path")
    }

    override fun isFileResource(path: String): Boolean = false
}
