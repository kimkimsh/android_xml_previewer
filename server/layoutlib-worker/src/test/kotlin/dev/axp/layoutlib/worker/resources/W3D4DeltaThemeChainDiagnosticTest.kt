package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import dev.axp.layoutlib.worker.DistDiscovery
import dev.axp.layoutlib.worker.FixtureDiscovery
import dev.axp.layoutlib.worker.session.SessionConstants
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Empirically narrows the Material TextAppearance sentinel root cause: loads the real
 * fixture bundle and probes the five lookup paths a Material widget's inflate traverses.
 * Each probe maps 1:1 to a hypothesis (reachable-by-name, getResource STYLE-ref,
 * findItemInTheme item discovery for materialButtonStyle and textAppearanceButton, and
 * the resolveResValue/StyleResourceValue instanceof gate that BridgeTypedArray and
 * BridgeContext both consume); the pass/fail pattern selects which fix surface applies.
 *
 * Integration-tagged because every probe depends on real fixture build artefacts
 * (sample-app runtime-classpath.txt, the AAR set, and the generated R.jar). The default
 * unit suite excludes the integration tag, so this class can land red without breaking
 * the default gate; opt-in via -PincludeTags=integration.
 */
@Tag("integration")
class W3D4DeltaThemeChainDiagnosticTest
{

    private lateinit var bundle: LayoutlibResourceBundle
    private lateinit var resources: LayoutlibRenderResources

    @BeforeEach
    fun loadProductionBundle()
    {
        val (dist, sampleApp) = locate() ?: return
        LayoutlibResourceValueLoader.clearCache()
        val args = LayoutlibResourceValueLoader.Args(
            distDataDir = dist.resolve(ResourceLoaderConstants.DATA_DIR),
            sampleAppRoot = sampleApp,
            runtimeClasspathTxt = sampleApp.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH),
        )
        bundle = LayoutlibResourceValueLoader.loadOrGet(args)
        resources = LayoutlibRenderResources(bundle, SessionConstants.DEFAULT_FIXTURE_THEME)
    }

    @Test
    fun `H1 — Lvl 5 Base_V14_Theme_Material3_Light is reachable by name from RES_AUTO bucket`()
    {
        val style = bundle.getStyleByName("Base.V14.Theme.Material3.Light")
        assertNotNull(style)
    }

    @Test
    fun `H2 — getResource STYLE ref returns the style instance — KILL POINT candidate`()
    {
        val ref = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "Widget.Material3.Button")
        val resolved = bundle.getResource(ref)
        assertNotNull(resolved)
    }

    @Test
    fun `H3a — findItemInTheme materialButtonStyle returns the M3 style item from Lvl 5`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "materialButtonStyle")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
        val raw = item!!.value
        assertNotNull(raw)
        assertTrue(raw!!.contains("Widget.Material3.Button"))
    }

    @Test
    fun `H3b — findItemInTheme textAppearanceButton returns Lvl 9 item`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "textAppearanceButton")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
        val raw = item!!.value
        assertNotNull(raw)
        assertTrue(raw!!.contains("TextAppearance.MaterialComponents.Button"))
    }

    @Test
    fun `H2-bridgeTypedArray gate — resolveResValue of StyleItem returns a StyleResourceValue post-fix`()
    {
        val item = StyleItemResourceValueImpl(
            ResourceNamespace.RES_AUTO,
            "android:textAppearance",
            "@style/Widget.Material3.Button",
            null,
        )
        val resolved = resources.resolveResValue(item)
        assertNotNull(resolved)
        assertTrue(
            resolved is StyleResourceValue,
            "BridgeTypedArray.getResourceId instanceof gate requires StyleResourceValue, got ${resolved!!::class.simpleName}",
        )
    }

    private fun locate(): Pair<Path, Path>?
    {
        val dist = DistDiscovery.locate(null)
        if (dist == null)
        {
            assumeTrue(false, "dist 없음")
            return null
        }
        val sampleApp = FixtureDiscovery.locateModuleRoot(null)
        if (sampleApp == null)
        {
            assumeTrue(false, "module root 없음")
            return null
        }
        val classpathTxt = sampleApp.resolve(AppLibraryResourceConstants.RUNTIME_CLASSPATH_TXT_PATH)
        if (!classpathTxt.exists())
        {
            assumeTrue(false, "runtime-classpath.txt missing — run :app:assembleDebug to populate the AAR manifest before bundle build")
            return null
        }
        return dist.toAbsolutePath().normalize() to sampleApp.toAbsolutePath().normalize()
    }
}
