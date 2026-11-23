package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViewportGridRenderer3DTest {

    @Test
    void shouldPickPowerOfTenSpacingFromCameraHeight() {
        assertEquals(100f, ViewportGridRenderer3D.gridSpacing(520f), 0.001f);
        assertEquals(10f, ViewportGridRenderer3D.gridSpacing(-200f), 0.001f, "Height below the ground uses its magnitude");
        assertEquals(1000f, ViewportGridRenderer3D.gridSpacing(8000f), 0.001f);
    }

    @Test
    void shouldNeverGoBelowMinimumSpacing() {
        assertEquals(1f, ViewportGridRenderer3D.gridSpacing(0f), 0.001f);
        assertEquals(1f, ViewportGridRenderer3D.gridSpacing(2f), 0.001f);
    }
}
