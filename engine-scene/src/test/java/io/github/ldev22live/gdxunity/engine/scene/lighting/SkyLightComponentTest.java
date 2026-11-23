package io.github.ldev22live.gdxunity.engine.scene.lighting;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyLightComponentTest {

    @Test
    void shouldReturnCopiesOfColors() {
        SkyLightComponent sky = new SkyLightComponent();
        sky.getSkyColor().set(Color.RED);
        sky.getGroundColor().set(Color.RED);

        assertEquals(SkyLightComponent.DEFAULT_SKY_COLOR, sky.getSkyColor());
        assertEquals(SkyLightComponent.DEFAULT_GROUND_COLOR, sky.getGroundColor());
    }

    @Test
    void shouldRejectNegativeOrNonFiniteIntensity() {
        SkyLightComponent sky = new SkyLightComponent();

        assertThrows(IllegalArgumentException.class, () -> sky.setIntensity(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> sky.setIntensity(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> sky.setIntensity(Float.POSITIVE_INFINITY));
        sky.setIntensity(0f);
        assertEquals(0f, sky.getIntensity());
    }
}
