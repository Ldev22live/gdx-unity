package io.github.ldev22live.gdxunity.engine.render;

/**
 * Quality presets of {@link SceneRenderer3D}'s lighting. Higher presets give sharper shadows and smoother sky
 * reflections at the cost of GPU memory and time when the sky light is rebuilt.
 *
 * <table>
 *   <caption>Preset values</caption>
 *   <tr><th>Preset</th><th>Shadow map</th><th>Sky irradiance map</th><th>Sky reflection map</th></tr>
 *   <tr><td>LOW</td><td>1024²</td><td>16²</td><td>64²</td></tr>
 *   <tr><td>MEDIUM</td><td>2048²</td><td>32²</td><td>128²</td></tr>
 *   <tr><td>HIGH</td><td>4096²</td><td>32²</td><td>256²</td></tr>
 * </table>
 *
 * Cubemap sizes are per face. Shadow distance is a separate renderer setting
 * ({@link SceneRenderer3D#setShadowDistance(float)}) because it depends on the scene's scale, not on the GPU.
 */
public enum RenderQuality3D {
    LOW(1024, 16, 64),
    MEDIUM(2048, 32, 128),
    HIGH(4096, 32, 256);

    private final int shadowMapSize;
    private final int irradianceMapSize;
    private final int radianceMapSize;

    RenderQuality3D(int shadowMapSize, int irradianceMapSize, int radianceMapSize) {
        this.shadowMapSize = shadowMapSize;
        this.irradianceMapSize = irradianceMapSize;
        this.radianceMapSize = radianceMapSize;
    }

    /** Width and height of the square shadow map in texels. */
    public int getShadowMapSize() {
        return shadowMapSize;
    }

    /** Face size of the diffuse sky light cubemap (very blurry, so small). */
    public int getIrradianceMapSize() {
        return irradianceMapSize;
    }

    /** Face size of the sky reflection cubemap at its sharpest mip level. */
    public int getRadianceMapSize() {
        return radianceMapSize;
    }

    /**
     * Number of mip levels of the sky reflection cubemap, from {@link #getRadianceMapSize()} down to 1x1. This is
     * the argument gdx-gltf's {@code IBLBuilder.buildRadianceMap(int mipMapLevels)} expects; passing the face size
     * there instead makes it build a framebuffer of {@code 1 << (size - 1)} texels, which overflows.
     */
    public int getRadianceMipLevels() {
        return mipLevelsForFaceSize(radianceMapSize);
    }

    /**
     * Mip levels of a square power-of-two texture of the given face size, down to and including 1x1.
     *
     * @throws IllegalArgumentException if {@code faceSize} is not a positive power of two
     */
    static int mipLevelsForFaceSize(int faceSize) {
        if (faceSize <= 0 || (faceSize & (faceSize - 1)) != 0) {
            throw new IllegalArgumentException("faceSize must be a positive power of two: " + faceSize);
        }
        return Integer.numberOfTrailingZeros(faceSize) + 1;
    }
}
