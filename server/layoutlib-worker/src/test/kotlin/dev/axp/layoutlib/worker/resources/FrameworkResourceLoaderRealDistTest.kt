package dev.axp.layoutlib.worker.resources

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * W3D1 F1 regression guard — 실 layoutlib-dist 의 attrs.xml 이 대량의 AttrDef 를 내놓는지 확인.
 *
 * 실 attrs.xml 은 top-level `<attr>` 이 0 개이고 declare-styleable 내부에만 존재 (~수백 개). 파서가
 * nested attr 을 수집하지 못하면 이 테스트는 attrCount ≈ 0 으로 fail → 런타임 ERROR_INFLATION 전
 * 조기 신호.
 *
 * dist 미존재 환경에서는 assumeTrue 로 skip — CI 환경 friendly.
 */
@Tag("integration")
class FrameworkResourceLoaderRealDistTest {

    @BeforeEach
    fun setUp() { FrameworkResourceValueLoader.clearCache() }

    @AfterEach
    fun tearDown() { FrameworkResourceValueLoader.clearCache() }

    @Test
    fun `real dist - attrs bundle 에 200+ 수집 F1 guard`() {
        val dataDir = locateDataDir()
        val bundle = FrameworkResourceValueLoader.loadOrGet(dataDir)

        // F1: 실 attrs.xml 만으로도 수백 개가 나와야 함. 100 을 하한으로 보수적 설정.
        val attrCount = bundle.attrCount()
        assertTrue(attrCount >= 100,
            "F1 regression guard: 실 attrs.xml declare-styleable 내부 attr 수집. actual=$attrCount < 100")
    }

    @Test
    fun `real dist - Theme Material Light NoActionBar chain 이 resolve`() {
        val dataDir = locateDataDir()
        val bundle = FrameworkResourceValueLoader.loadOrGet(dataDir)

        val noActionBar = bundle.getStyle("Theme.Material.Light.NoActionBar")
        assertNotNull(noActionBar, "Theme.Material.Light.NoActionBar 존재")
        assertTrue(noActionBar!!.parentStyleName?.isNotEmpty() == true,
            "parent chain 은 non-null (themes_material 의 parent explicit)")
    }

    private fun locateDataDir(): Path {
        val candidates = listOf(
            Paths.get("../libs", "layoutlib-dist", "android-34", "data"),
            Paths.get("server/libs/layoutlib-dist/android-34/data"),
            Paths.get(System.getProperty("user.dir"), "../libs/layoutlib-dist/android-34/data")
        )
        val found = candidates.firstOrNull { it.exists() && it.isDirectory() }
        assumeTrue(found != null, "layoutlib-dist data 디렉토리 없음 — W1D3-R2 다운로드 필요")
        return found!!.toAbsolutePath().normalize()
    }
}
