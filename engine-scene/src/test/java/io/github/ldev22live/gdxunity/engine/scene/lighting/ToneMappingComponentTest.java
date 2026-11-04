package io.github.ldev22live.gdxunity.engine.scene.lighting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToneMappingComponentTest {

    @Test
    void shouldDefaultToUnitExposureAndFilmicCurve() {
        ToneMappingComponent toneMapping = new ToneMappingComponent();

        assertEquals(1f, toneMapping.getExposure());
        assertEquals(ToneMappingComponent.Operator.ACES_FILMIC, toneMapping.getOperator());
    }

    @Test
    void shouldRejectExposureThatIsNotAPositiveFiniteValue() {
        ToneMappingComponent toneMapping = new ToneMappingComponent();

        assertThrows(IllegalArgumentException.class, () -> toneMapping.setExposure(0f));
        assertThrows(IllegalArgumentException.class, () -> toneMapping.setExposure(-1f));
        assertThrows(IllegalArgumentException.class, () -> toneMapping.setExposure(Float.NaN));
        assertThrows(NullPointerException.class, () -> toneMapping.setOperator(null));
    }
}
