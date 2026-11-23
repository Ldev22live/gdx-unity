package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceManager;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;

import java.util.Objects;

/**
 * Sky light maps rendered on the GPU from a two-color sky with gdx-gltf's {@link IBLBuilder} (TD-032): a blurred
 * irradiance cubemap for diffuse light, a mip-mapped radiance cubemap for reflections, and gdx-gltf's BRDF lookup
 * texture. Needs a GL context.
 *
 * <p>Rough reflections read lower mip levels with {@code textureLod}, which the PBR shader only gets on GL 3 or
 * with {@code EXT_shader_texture_lod}. Without either, {@link #bake} skips the radiance map: gdx-gltf then
 * reflects the irradiance map, so every surface reflects the sky as if fully rough. {@link #hasReflectionMap()}
 * reports which case applies.
 */
public final class BakedSkyLightMaps implements SkyLightMaps {
    /** gdx-gltf's precomputed BRDF lookup table, shipped in its jar. */
    static final String BRDF_LUT_PATH = "net/mgsx/gltf/shaders/brdfLUT.png";

    private final ResourceManager resources = new ResourceManager();
    private final Attribute diffuse;
    private final Attribute specular;
    private final Attribute brdf;

    private BakedSkyLightMaps(Cubemap irradiance, Cubemap radiance, Texture brdfLut) {
        resources.register(irradiance);
        if (radiance != null) {
            resources.register(radiance);
        }
        resources.register(brdfLut);
        this.diffuse = PBRCubemapAttribute.createDiffuseEnv(irradiance);
        this.specular = radiance == null ? null : PBRCubemapAttribute.createSpecularEnv(radiance);
        this.brdf = PBRTextureAttribute.createBRDFLookupTexture(brdfLut);
    }

    /**
     * Renders the maps for a sky that is {@code skyColor} above the horizon and {@code groundColor} below.
     *
     * @param sunDirection only orients the builder; the sun itself is not drawn into the maps because the scene's
     *                     directional light already lights the scene directly
     */
    public static BakedSkyLightMaps bake(Color skyColor, Color groundColor, Vector3 sunDirection, RenderQuality3D quality) {
        Objects.requireNonNull(skyColor, "skyColor cannot be null");
        Objects.requireNonNull(groundColor, "groundColor cannot be null");
        Objects.requireNonNull(sunDirection, "sunDirection cannot be null");
        Objects.requireNonNull(quality, "quality cannot be null");

        DirectionalLight sun = new DirectionalLight();
        sun.direction.set(sunDirection).nor();
        IBLBuilder builder = IBLBuilder.createCustom(sun);
        Cubemap irradiance = null;
        Cubemap radiance = null;
        Texture brdfLut = null;
        try {
            builder.renderSun = false;
            builder.renderGradient = true;
            builder.farSkyColor.set(skyColor);
            builder.nearSkyColor.set(skyColor);
            builder.nearGroundColor.set(groundColor);
            builder.farGroundColor.set(groundColor);
            irradiance = builder.buildIrradianceMap(quality.getIrradianceMapSize());
            if (supportsReflectionMap()) {
                // buildRadianceMap takes a mip level count, not a face size: its largest face is 2^(levels - 1)
                radiance = builder.buildRadianceMap(quality.getRadianceMipLevels());
            }
            brdfLut = new Texture(Gdx.files.classpath(BRDF_LUT_PATH));
            return new BakedSkyLightMaps(irradiance, radiance, brdfLut);
        } catch (RuntimeException e) {
            for (Disposable built : new Disposable[] {irradiance, radiance, brdfLut}) {
                if (built != null) {
                    try {
                        built.dispose();
                    } catch (RuntimeException disposalFailure) {
                        e.addSuppressed(disposalFailure);
                    }
                }
            }
            throw e;
        } finally {
            builder.dispose();
        }
    }

    /** Whether the PBR shader can sample cubemap mip levels (GL 3, or GL 2 with {@code EXT_shader_texture_lod}). */
    public static boolean supportsReflectionMap() {
        return Gdx.graphics.isGL30Available() || Gdx.graphics.supportsExtension("EXT_shader_texture_lod");
    }

    /** True when sharp-to-rough reflections are available; false when surfaces reflect the blurred sky only. */
    public boolean hasReflectionMap() {
        return specular != null;
    }

    @Override
    public void applyTo(Environment environment) {
        environment.set(diffuse);
        if (specular != null) {
            environment.set(specular);
        }
        environment.set(brdf);
    }

    /** Disposes the maps; failures are collected per TD-008. Idempotent. */
    @Override
    public void dispose() {
        if (!resources.isDisposed()) {
            resources.dispose();
        }
    }
}
