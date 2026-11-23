package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import io.github.ldev22live.gdxunity.engine.material.Material;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;

import java.util.Objects;

/**
 * Maps a {@link Material} (TD-081) to the shading attributes of a libGDX material, which gdx-gltf's PBR shader reads. Pure
 * attribute work, no GL calls, so it is unit-tested headless. What it applies: base colour and opacity, metallic, roughness, emissive
 * colour times strength, alpha mode, double-sided, and (with a {@link TextureSource}) the texture slots with tiling, offset, normal
 * scale and occlusion strength.
 */
final class MaterialAttributes {
    private MaterialAttributes() { }

    /**
     * Sets {@code target}'s attributes from {@code source}. Attributes the source does not use are removed, so a material that
     * stops being emissive or blended stops looking so.
     *
     * @param gamma     the display gamma the renderer converts sRGB colours with
     * @param twoSided  whether the shape itself is drawn from both sides (a plane), whatever the material says
     */
    static void apply(Material source, com.badlogic.gdx.graphics.g3d.Material target, float gamma, boolean twoSided) {
        apply(source, target, gamma, twoSided, null);
    }

    /**
     * As {@link #apply(Material, com.badlogic.gdx.graphics.g3d.Material, float, boolean)}, and the texture slots too: each filled slot
     * whose texture {@code textures} finds becomes a texture attribute carrying the material's tiling and offset; the others are
     * removed. With no source, slots are not drawn.
     */
    static void apply(Material source, com.badlogic.gdx.graphics.g3d.Material target, float gamma, boolean twoSided,
                      TextureSource textures) {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Color base = linear(source.getBaseColor(), 1f, gamma);
        float opacity = source.getAlphaMode() == Material.AlphaMode.OPAQUE ? 1f : source.getBaseColor().a;
        base.a = opacity;
        target.set(PBRColorAttribute.createBaseColorFactor(base));
        target.set(PBRFloatAttribute.createMetallic(source.getMetallic()));
        target.set(PBRFloatAttribute.createRoughness(source.getRoughness()));

        // An emissive texture with a black emissive colour would glow nowhere; the texture alone then glows at the strength
        applyTextures(source, target, textures);
        boolean emissiveTexture = target.has(PBRTextureAttribute.EmissiveTexture);
        Color emissiveColor = emissiveTexture && isBlack(source.getEmissive()) ? Color.WHITE : source.getEmissive();
        Color emissive = linear(emissiveColor, source.getEmissiveStrength(), gamma);
        if (emissive.r > 0f || emissive.g > 0f || emissive.b > 0f) {
            target.set(ColorAttribute.createEmissive(emissive));
        } else {
            target.remove(ColorAttribute.Emissive);
        }

        switch (source.getAlphaMode()) {
            case BLEND -> {
                target.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, opacity));
                target.remove(FloatAttribute.AlphaTest);
            }
            case MASK -> {
                target.set(FloatAttribute.createAlphaTest(source.getAlphaCutoff()));
                target.remove(BlendingAttribute.Type);
            }
            case OPAQUE -> {
                target.remove(BlendingAttribute.Type);
                target.remove(FloatAttribute.AlphaTest);
            }
        }
        target.set(IntAttribute.createCullFace(twoSided || source.isDoubleSided() ? GL20.GL_NONE : GL20.GL_BACK));
    }

    private static void applyTextures(Material source, com.badlogic.gdx.graphics.g3d.Material target, TextureSource textures) {
        for (Material.Slot slot : Material.Slot.values()) {
            long type = textureType(slot);
            Texture texture = null;
            String path = source.getTexture(slot);
            if (textures != null && !path.isEmpty()) {
                texture = textures.get(path);
            }
            if (texture == null) {
                target.remove(type);
                continue;
            }
            PBRTextureAttribute attribute = new PBRTextureAttribute(type, texture);
            attribute.scaleU = source.getTilingU();
            attribute.scaleV = source.getTilingV();
            attribute.offsetU = source.getOffsetU();
            attribute.offsetV = source.getOffsetV();
            attribute.textureDescription.uWrap = Texture.TextureWrap.Repeat;
            attribute.textureDescription.vWrap = Texture.TextureWrap.Repeat;
            attribute.textureDescription.minFilter = Texture.TextureFilter.MipMapLinearLinear;
            attribute.textureDescription.magFilter = Texture.TextureFilter.Linear;
            target.set(attribute);
        }
        if (target.has(PBRTextureAttribute.NormalTexture)) {
            target.set(PBRFloatAttribute.createNormalScale(source.getNormalScale()));
        } else {
            target.remove(PBRFloatAttribute.NormalScale);
        }
        if (target.has(PBRTextureAttribute.OcclusionTexture)) {
            target.set(PBRFloatAttribute.createOcclusionStrength(source.getOcclusionStrength()));
        } else {
            target.remove(PBRFloatAttribute.OcclusionStrength);
        }
    }

    private static boolean isBlack(Color color) {
        return color.r == 0f && color.g == 0f && color.b == 0f;
    }

    private static long textureType(Material.Slot slot) {
        return switch (slot) {
            case BASE_COLOR -> PBRTextureAttribute.BaseColorTexture;
            case NORMAL -> PBRTextureAttribute.NormalTexture;
            case METALLIC_ROUGHNESS -> PBRTextureAttribute.MetallicRoughnessTexture;
            case OCCLUSION -> PBRTextureAttribute.OcclusionTexture;
            case EMISSIVE -> PBRTextureAttribute.EmissiveTexture;
        };
    }

    /**
     * Puts a primitive's material back to the plain look it has without a material: white, the given roughness, nothing emitted,
     * opaque. The renderer then sets the colour from the component each frame.
     */
    static void reset(com.badlogic.gdx.graphics.g3d.Material target, float roughness, boolean twoSided) {
        Objects.requireNonNull(target, "target cannot be null");
        target.set(PBRColorAttribute.createBaseColorFactor(Color.WHITE));
        target.set(PBRFloatAttribute.createMetallic(0f));
        target.set(PBRFloatAttribute.createRoughness(roughness));
        target.remove(ColorAttribute.Emissive);
        target.remove(BlendingAttribute.Type);
        target.remove(FloatAttribute.AlphaTest);
        target.set(IntAttribute.createCullFace(twoSided ? GL20.GL_NONE : GL20.GL_BACK));
        applyTextures(new Material(), target, null);
    }

    /**
     * An sRGB colour as linear light, times {@code scale}; alpha is 1. Set field by field: {@code new Color(r, g, b, a)} clamps to 1,
     * which would cap a glow stronger than 1.
     */
    private static Color linear(Color srgb, float scale, float gamma) {
        Color out = new Color();
        out.r = (float) Math.pow(srgb.r, gamma) * scale;
        out.g = (float) Math.pow(srgb.g, gamma) * scale;
        out.b = (float) Math.pow(srgb.b, gamma) * scale;
        out.a = 1f;
        return out;
    }
}
