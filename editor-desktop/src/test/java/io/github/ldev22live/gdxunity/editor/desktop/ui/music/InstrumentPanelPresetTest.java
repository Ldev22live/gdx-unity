package io.github.ldev22live.gdxunity.editor.desktop.ui.music;

import io.github.ldev22live.gdxunity.engine.audio.FmPatch;
import io.github.ldev22live.gdxunity.engine.audio.PluckPatch;
import io.github.ldev22live.gdxunity.engine.audio.SynthPatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The preset menu of the instrument panel shows the preset a channel is exactly, and "Custom" otherwise (TD-076). */
class InstrumentPanelPresetTest {
    @Test
    void shouldShowTheMatchingSynthPresetFromOneBasedIndexZeroBeingCustom() {
        for (int i = 0; i < SynthPatch.presetNames().size(); i++) {
            String name = SynthPatch.presetNames().get(i);
            assertEquals(i + 1, InstrumentPanel.matchingPresetIndex(SynthPatch.presetNames(), n -> SynthPatch.preset(n).toJson(),
                SynthPatch.preset(name).toJson()), name);
        }
    }

    @Test
    void shouldShowTheMatchingFmAndPluckedStringPresets() {
        assertEquals(1, InstrumentPanel.matchingPresetIndex(FmPatch.presetNames(), n -> FmPatch.preset(n).toJson(), FmPatch.preset("Bell").toJson()));
        assertEquals(FmPatch.presetNames().size(), InstrumentPanel.matchingPresetIndex(FmPatch.presetNames(), n -> FmPatch.preset(n).toJson(),
            FmPatch.preset("FM Bass").toJson()));
        assertEquals(2, InstrumentPanel.matchingPresetIndex(PluckPatch.presetNames(), n -> PluckPatch.preset(n).toJson(),
            PluckPatch.preset("Harp").toJson()));
    }

    @Test
    void shouldShowCustomAsSoonAsAKnobHasMoved() {
        FmPatch moved = FmPatch.preset("Bell");
        moved.index += 0.5f;
        assertEquals(0, InstrumentPanel.matchingPresetIndex(FmPatch.presetNames(), n -> FmPatch.preset(n).toJson(), moved.toJson()));
        SynthPatch edited = SynthPatch.preset("Pluck");
        edited.cutoff = 1234f;
        assertEquals(0, InstrumentPanel.matchingPresetIndex(SynthPatch.presetNames(), n -> SynthPatch.preset(n).toJson(), edited.toJson()));
    }
}
