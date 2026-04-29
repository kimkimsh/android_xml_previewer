package dev.axp.layoutlib.worker.resources

import com.android.resources.ResourceType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class FrameworkResourceValueLoaderTest {

    @BeforeEach
    fun setUp() {
        FrameworkResourceValueLoader.clearCache()
    }

    @AfterEach
    fun tearDown() {
        FrameworkResourceValueLoader.clearCache()
    }

    @Test
    fun `loadOrGet - 10 파일 모두 존재하면 bundle 생성`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val bundle = FrameworkResourceValueLoader.loadOrGet(data)

        val d = bundle.getResource(ResourceType.DIMEN, "config_scrollbarSize")
        assertNotNull(d)
        assertEquals("4dp", d!!.value)
    }

    @Test
    fun `loadOrGet - 파일 하나라도 누락 시 IllegalStateException`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        Files.delete(data.resolve(ResourceLoaderConstants.VALUES_DIR).resolve(ResourceLoaderConstants.FILE_ATTRS))
        val ex = assertThrows(IllegalStateException::class.java) {
            FrameworkResourceValueLoader.loadOrGet(data)
        }
        assertTrue(ex.message?.contains(ResourceLoaderConstants.FILE_ATTRS) == true)
    }

    @Test
    fun `loadOrGet - 동일 dataDir 재호출 시 cache 반환 동일 인스턴스`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val a = FrameworkResourceValueLoader.loadOrGet(data)
        val b = FrameworkResourceValueLoader.loadOrGet(data)
        assertSame(a, b)
    }

    @Test
    fun `clearCache 후 재로드는 새 instance`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val a = FrameworkResourceValueLoader.loadOrGet(data)
        FrameworkResourceValueLoader.clearCache()
        val b = FrameworkResourceValueLoader.loadOrGet(data)
        assertTrue(a !== b, "clearCache 후에는 재로드 인스턴스가 새 객체")
    }

    @Test
    fun `loadOrGet - REQUIRED_FILES 개수 = 10`() {
        assertEquals(ResourceLoaderConstants.REQUIRED_FILE_COUNT, ResourceLoaderConstants.REQUIRED_FILES.size)
    }

    @Test
    fun `loadOrGet - 스타일 parent chain 이 bundle 에 존재`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val bundle = FrameworkResourceValueLoader.loadOrGet(data)

        val noActionBar = bundle.getStyle("Theme.Material.Light.NoActionBar")
        assertNotNull(noActionBar)
        assertEquals("Theme.Material.Light", noActionBar!!.parentStyleName)

        val light = bundle.getStyle("Theme.Material.Light")
        assertNotNull(light)
        assertEquals("Theme.Light", light!!.parentStyleName,
            "F2 regression guard: 실 frameworks 는 Theme.Material.Light parent=Theme.Light")

        val themeLight = bundle.getStyle("Theme.Light")
        assertNotNull(themeLight)
        assertEquals("Theme", themeLight!!.parentStyleName)
    }

    @Test
    fun `loadOrGet - attrs xml declare-styleable 내부 attr 이 bundle 에 수집됨 (F1)`(@TempDir tmp: Path) {
        val data = createFakeDataDir(tmp, allFiles = true)
        val bundle = FrameworkResourceValueLoader.loadOrGet(data)

        assertNotNull(bundle.getAttr("colorPrimary"),
            "F1 regression guard: declare-styleable 내부 attr 이 bundle 에 수집되어야 함")
        assertNotNull(bundle.getAttr("isLightTheme"))
        assertNotNull(bundle.getAttr("id"))
    }

    // ----- helpers -----

    /**
     * 실 data/res/values 대체로 minimal 한 10 파일을 tmp 에 생성.
     * F1: attrs.xml top-level attr 0 개 + nested 만. F2: parent chain 실 구조 반영.
     */
    private fun createFakeDataDir(tmp: Path, allFiles: Boolean): Path {
        val valuesDir = tmp.resolve("data").resolve(ResourceLoaderConstants.VALUES_DIR)
        Files.createDirectories(valuesDir)

        valuesDir.resolve(ResourceLoaderConstants.FILE_CONFIG).writeText("""
            <resources>
              <dimen name="config_scrollbarSize">4dp</dimen>
            </resources>
        """.trimIndent())

        valuesDir.resolve(ResourceLoaderConstants.FILE_COLORS).writeText("<resources/>")
        valuesDir.resolve(ResourceLoaderConstants.FILE_DIMENS).writeText("<resources/>")

        valuesDir.resolve(ResourceLoaderConstants.FILE_THEMES).writeText("""
            <resources>
              <style name="Theme"><item name="colorPrimary">#fff</item></style>
              <style name="Theme.Light"><item name="colorPrimary">#eee</item></style>
            </resources>
        """.trimIndent())

        valuesDir.resolve(ResourceLoaderConstants.FILE_STYLES).writeText("<resources/>")

        valuesDir.resolve(ResourceLoaderConstants.FILE_ATTRS).writeText("""
            <resources>
              <declare-styleable name="Theme">
                <attr name="colorPrimary" format="color" />
                <attr name="isLightTheme" format="boolean" />
              </declare-styleable>
              <declare-styleable name="View">
                <attr name="id" format="reference" />
                <attr name="colorPrimary" />
              </declare-styleable>
            </resources>
        """.trimIndent())

        valuesDir.resolve(ResourceLoaderConstants.FILE_COLORS_MATERIAL).writeText("<resources/>")
        valuesDir.resolve(ResourceLoaderConstants.FILE_DIMENS_MATERIAL).writeText("<resources/>")

        valuesDir.resolve(ResourceLoaderConstants.FILE_THEMES_MATERIAL).writeText("""
            <resources>
              <style name="Theme.Material.Light" parent="Theme.Light"/>
              <style name="Theme.Material.Light.NoActionBar" parent="Theme.Material.Light"/>
            </resources>
        """.trimIndent())

        valuesDir.resolve(ResourceLoaderConstants.FILE_STYLES_MATERIAL).writeText("<resources/>")
        return tmp.resolve("data")
    }
}
