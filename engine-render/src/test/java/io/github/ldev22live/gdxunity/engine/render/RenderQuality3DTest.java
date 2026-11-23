package io.github.ldev22live.gdxunity.engine.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderQuality3DTest {

    @Test
    void shouldNotLowerAnyResolutionFromOnePresetToTheNext() {
        RenderQuality3D[] presets = RenderQuality3D.values();
        for (int i = 1; i < presets.length; i++) {
            assertTrue(presets[i].getShadowMapSize() > presets[i - 1].getShadowMapSize(), presets[i].name());
            assertTrue(presets[i].getRadianceMapSize() > presets[i - 1].getRadianceMapSize(), presets[i].name());
            assertTrue(presets[i].getIrradianceMapSize() >= presets[i - 1].getIrradianceMapSize(), presets[i].name());
        }
    }

    @Test
    void shouldUsePowerOfTwoTextureSizes() {
        for (RenderQuality3D preset : RenderQuality3D.values()) {
            for (int size : new int[] {preset.getShadowMapSize(), preset.getIrradianceMapSize(), preset.getRadianceMapSize()}) {
                assertEquals(0, size & (size - 1), preset + ": " + size);
            }
        }
    }

    @Test
    void shouldGiveRadianceMipLevelsWhoseLargestFaceIsTheRadianceMapSize() {
        for (RenderQuality3D preset : RenderQuality3D.values()) {
            int levels = preset.getRadianceMipLevels();
            // IBLBuilder.buildRadianceMap(levels) renders its first face at 1 << (levels - 1)
            assertEquals(preset.getRadianceMapSize(), 1 << (levels - 1), preset.name());
        }
        assertEquals(7, RenderQuality3D.LOW.getRadianceMipLevels());
        assertEquals(8, RenderQuality3D.MEDIUM.getRadianceMipLevels());
        assertEquals(9, RenderQuality3D.HIGH.getRadianceMipLevels());
    }

    @Test
    void shouldCountMipLevelsDownToOneTexel() {
        assertEquals(1, RenderQuality3D.mipLevelsForFaceSize(1));
        assertEquals(2, RenderQuality3D.mipLevelsForFaceSize(2));
        assertEquals(11, RenderQuality3D.mipLevelsForFaceSize(1024));
    }

    @Test
    void shouldRejectFaceSizesThatAreNotPositivePowersOfTwo() {
        for (int size : new int[] {0, -64, 3, 96}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RenderQuality3D.mipLevelsForFaceSize(size));
            assertTrue(error.getMessage().contains(String.valueOf(size)), error.getMessage());
        }
    }
}
