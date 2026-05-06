package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
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

/**
 * Empirically narrows whether the Material gate chain (enforceMaterialTheme →
 * checkMaterialTheme → colorPrimaryVariant hasValue) can be closed by adding
 * colorPrimaryVariant to Theme.AxpFixture. Each probe maps 1:1 to one segment
 * of the resolution flow (attr definition, theme stack walk, attr-ref chain
 * hop, T18 regression guard, and a Material STYLE-side guard).
 *
 * Integration-tagged because every probe depends on real fixture build
 * artefacts. The default unit suite excludes the integration tag, so this
 * class can land red without breaking the default gate; opt-in via
 * -PincludeTags=integration.
 */
@Tag("integration")
class W3D4DeltaBGateChainProbeTest
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
    fun `P1 — colorPrimaryVariant attr is defined in the bundle as RES_AUTO`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimaryVariant")
        val resolved = bundle.getResource(attrRef)
        assertNotNull(resolved)
    }

    @Test
    fun `P2 — findItemInTheme colorPrimaryVariant returns the chain item`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimaryVariant")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
    }

    @Test
    fun `P3 — resolveResValue of the chain item terminates on a concrete color value`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimaryVariant")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
        val resolved = resources.resolveResValue(item)
        assertNotNull(resolved)
        val raw = resolved!!.value
        assertNotNull(raw)
        assertTrue(
            raw!!.startsWith("#") || raw.startsWith("@color/"),
            "post-fix expects concrete color value (e.g. #6750A4) or final @color/ ref, got: $raw",
        )
    }

    @Test
    fun `P4 — T18 regression guard — getResource STYLE Widget_Material3_Button still returns the style`()
    {
        val styleRef = ResourceReference(
            ResourceNamespace.RES_AUTO,
            ResourceType.STYLE,
            "Widget.Material3.Button",
        )
        val resolved = bundle.getResource(styleRef)
        assertNotNull(resolved)
        assertTrue(resolved is StyleResourceValue)
    }

    @Test
    fun `P5 — colorPrimary chain anchor is concrete in fixture (anchor of P3 chain)`()
    {
        val attrRef = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "colorPrimary")
        val item = resources.findItemInTheme(attrRef)
        assertNotNull(item)
        val resolved = resources.resolveResValue(item)
        assertNotNull(resolved)
        val raw = resolved!!.value
        assertNotNull(raw)
        assertTrue(
            raw == "#6750A4" || raw!!.startsWith("@color/"),
            "fixture defines colorPrimary directly, got: $raw",
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
        return dist.toAbsolutePath().normalize() to sampleApp.toAbsolutePath().normalize()
    }
}
