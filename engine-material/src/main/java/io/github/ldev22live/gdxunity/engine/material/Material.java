package io.github.ldev22live.gdxunity.engine.material;

import com.badlogic.gdx.graphics.Color;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A surface look (TD-081): the physically based parameters a renderer shades with, stored in a {@code .material} file and shared by
 * everything that names the file. Colours are sRGB, as the colour pickers show them; the renderer converts.
 *
 * <p>{@link #getRevision()} counts changes, so a renderer that caches what it built from a material rebuilds only when the number
 * changed. Not thread safe: the editor and the game loop use it from one thread.
 */
public final class Material {
    /** How the alpha of the base colour is used. */
    public enum AlphaMode {
        /** Fully opaque; alpha is ignored. */
        OPAQUE,
        /** Blended with what is behind it. */
        BLEND,
        /** Opaque where alpha is at least the cutoff, absent elsewhere (foliage, fences). */
        MASK
    }

    /** The texture slots a material has. A slot holds a project-relative texture path, or is empty. */
    public enum Slot {
        BASE_COLOR, NORMAL, METALLIC_ROUGHNESS, OCCLUSION, EMISSIVE
    }

    /** Defaults: a matte white surface, like the primitives had before materials. */
    public static final float DEFAULT_ROUGHNESS = 0.7f;
    public static final float DEFAULT_ALPHA_CUTOFF = 0.5f;
    /** Largest emissive strength: glowing surfaces need more than 1 to bloom, but not without limit. */
    public static final float MAX_EMISSIVE_STRENGTH = 100f;
    /** Largest texture tiling per axis. */
    public static final float MAX_TILING = 1000f;

    private String name = "Material";
    private final Color baseColor = new Color(Color.WHITE);
    private float metallic;
    private float roughness = DEFAULT_ROUGHNESS;
    private final Color emissive = new Color(Color.BLACK);
    private float emissiveStrength = 1f;
    private float normalScale = 1f;
    private float occlusionStrength = 1f;
    private AlphaMode alphaMode = AlphaMode.OPAQUE;
    private float alphaCutoff = DEFAULT_ALPHA_CUTOFF;
    private boolean doubleSided;
    private float tilingU = 1f;
    private float tilingV = 1f;
    private float offsetU;
    private float offsetV;
    private final Map<Slot, String> textures = new EnumMap<>(Slot.class);
    private int revision;

    /** Display name, never blank. */
    public String getName() {
        return name;
    }

    /** @throws IllegalArgumentException if {@code name} is null or blank */
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A material needs a name");
        }
        this.name = name.trim();
        revision++;
    }

    /** Base colour (sRGB), multiplied with the base colour texture when there is one; alpha is the opacity. The returned object is a copy. */
    public Color getBaseColor() {
        return new Color(baseColor);
    }

    /** @throws IllegalArgumentException if a channel is not within 0 to 1 */
    public void setBaseColor(Color color) {
        requireColor(color, "baseColor");
        baseColor.set(color);
        revision++;
    }

    /** 0 for a dielectric (plastic, wood, stone) up to 1 for a metal. */
    public float getMetallic() {
        return metallic;
    }

    /** @throws IllegalArgumentException if {@code metallic} is not within 0 to 1 */
    public void setMetallic(float metallic) {
        this.metallic = requireUnit(metallic, "metallic");
        revision++;
    }

    /** 0 for a mirror finish up to 1 for fully matte. */
    public float getRoughness() {
        return roughness;
    }

    /** @throws IllegalArgumentException if {@code roughness} is not within 0 to 1 */
    public void setRoughness(float roughness) {
        this.roughness = requireUnit(roughness, "roughness");
        revision++;
    }

    /** Colour the surface gives off by itself (sRGB), black for none. The returned object is a copy. */
    public Color getEmissive() {
        return new Color(emissive);
    }

    /** @throws IllegalArgumentException if a channel is not within 0 to 1 */
    public void setEmissive(Color color) {
        requireColor(color, "emissive");
        emissive.set(color);
        revision++;
    }

    /** Multiplier of the emissive colour, 0 to {@value #MAX_EMISSIVE_STRENGTH}. */
    public float getEmissiveStrength() {
        return emissiveStrength;
    }

    /** @throws IllegalArgumentException if outside 0 to {@value #MAX_EMISSIVE_STRENGTH} */
    public void setEmissiveStrength(float strength) {
        this.emissiveStrength = requireRange(strength, 0f, MAX_EMISSIVE_STRENGTH, "emissiveStrength");
        revision++;
    }

    /** How strongly the normal texture bends the lighting, 0 to 10. */
    public float getNormalScale() {
        return normalScale;
    }

    /** @throws IllegalArgumentException if outside 0 to 10 */
    public void setNormalScale(float scale) {
        this.normalScale = requireRange(scale, 0f, 10f, "normalScale");
        revision++;
    }

    /** How strongly the occlusion texture darkens crevices, 0 to 1. */
    public float getOcclusionStrength() {
        return occlusionStrength;
    }

    /** @throws IllegalArgumentException if {@code strength} is not within 0 to 1 */
    public void setOcclusionStrength(float strength) {
        this.occlusionStrength = requireUnit(strength, "occlusionStrength");
        revision++;
    }

    public AlphaMode getAlphaMode() {
        return alphaMode;
    }

    public void setAlphaMode(AlphaMode mode) {
        this.alphaMode = Objects.requireNonNull(mode, "mode cannot be null");
        revision++;
    }

    /** With {@link AlphaMode#MASK}: pixels with less alpha than this are dropped. */
    public float getAlphaCutoff() {
        return alphaCutoff;
    }

    /** @throws IllegalArgumentException if {@code cutoff} is not within 0 to 1 */
    public void setAlphaCutoff(float cutoff) {
        this.alphaCutoff = requireUnit(cutoff, "alphaCutoff");
        revision++;
    }

    /** Whether the back of a surface is drawn too (leaves, cloth, planes). */
    public boolean isDoubleSided() {
        return doubleSided;
    }

    public void setDoubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
        revision++;
    }

    /** How many times the textures repeat across a surface, along U (across) and V (up). */
    public float getTilingU() {
        return tilingU;
    }

    public float getTilingV() {
        return tilingV;
    }

    /** @throws IllegalArgumentException unless both are above 0 and at most {@value #MAX_TILING} */
    public void setTiling(float u, float v) {
        if (!(u > 0f && u <= MAX_TILING && v > 0f && v <= MAX_TILING)) {
            throw new IllegalArgumentException("Tiling must be above 0 and at most " + MAX_TILING + ": " + u + ", " + v);
        }
        this.tilingU = u;
        this.tilingV = v;
        revision++;
    }

    /** Shift of the textures across the surface, in texture widths. */
    public float getOffsetU() {
        return offsetU;
    }

    public float getOffsetV() {
        return offsetV;
    }

    /** @throws IllegalArgumentException if either is not a finite number */
    public void setOffset(float u, float v) {
        if (!Float.isFinite(u) || !Float.isFinite(v)) {
            throw new IllegalArgumentException("Offset must be finite: " + u + ", " + v);
        }
        this.offsetU = u;
        this.offsetV = v;
        revision++;
    }

    /** The texture path in a slot, or an empty string when the slot is empty. */
    public String getTexture(Slot slot) {
        return textures.getOrDefault(Objects.requireNonNull(slot, "slot cannot be null"), "");
    }

    /**
     * Puts a texture in a slot.
     *
     * @param path a project-relative path, or null or blank to empty the slot
     */
    public void setTexture(Slot slot, String path) {
        Objects.requireNonNull(slot, "slot cannot be null");
        if (path == null || path.isBlank()) {
            textures.remove(slot);
        } else {
            textures.put(slot, path.trim());
        }
        revision++;
    }

    /** Changes every time something is set, so renderers know when to rebuild. */
    public int getRevision() {
        return revision;
    }

    /** A copy that shares nothing with this material. */
    public Material copy() {
        Material copy = new Material();
        copy.copyFrom(this);
        return copy;
    }

    /** Takes over every value of {@code other}, so a shared instance can be reloaded in place. */
    public void copyFrom(Material other) {
        Objects.requireNonNull(other, "other cannot be null");
        this.name = other.name;
        this.baseColor.set(other.baseColor);
        this.metallic = other.metallic;
        this.roughness = other.roughness;
        this.emissive.set(other.emissive);
        this.emissiveStrength = other.emissiveStrength;
        this.normalScale = other.normalScale;
        this.occlusionStrength = other.occlusionStrength;
        this.alphaMode = other.alphaMode;
        this.alphaCutoff = other.alphaCutoff;
        this.doubleSided = other.doubleSided;
        this.tilingU = other.tilingU;
        this.tilingV = other.tilingV;
        this.offsetU = other.offsetU;
        this.offsetV = other.offsetV;
        this.textures.clear();
        this.textures.putAll(other.textures);
        revision++;
    }

    private static void requireColor(Color color, String what) {
        Objects.requireNonNull(color, what + " cannot be null");
        if (!(color.r >= 0f && color.r <= 1f && color.g >= 0f && color.g <= 1f && color.b >= 0f && color.b <= 1f
            && color.a >= 0f && color.a <= 1f)) {
            throw new IllegalArgumentException(what + " channels must be within 0 to 1: " + color);
        }
    }

    private static float requireUnit(float value, String what) {
        return requireRange(value, 0f, 1f, what);
    }

    private static float requireRange(float value, float min, float max, String what) {
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(what + " must be within " + min + " to " + max + ": " + value);
        }
        return value;
    }
}
