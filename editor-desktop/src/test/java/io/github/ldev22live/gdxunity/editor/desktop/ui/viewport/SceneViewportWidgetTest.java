package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneViewportWidgetTest {

    @Test
    void shouldScaleLogicalSizeToBackBufferPixels() {
        // 100% display scaling: logical and back-buffer sizes match
        assertEquals(800, SceneViewportWidget.toBackBufferPixels(800, 1920, 1920));
        // 150% display scaling: 1280 logical px map to 1920 back-buffer px
        assertEquals(1200, SceneViewportWidget.toBackBufferPixels(800, 1920, 1280));
    }

    @Test
    void shouldNeverReturnAnEmptyFrameBufferSize() {
        assertEquals(1, SceneViewportWidget.toBackBufferPixels(0, 1920, 1920));
        assertEquals(1, SceneViewportWidget.toBackBufferPixels(0, 0, 0));
    }
}
