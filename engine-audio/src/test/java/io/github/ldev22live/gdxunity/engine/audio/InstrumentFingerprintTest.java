package io.github.ldev22live.gdxunity.engine.audio;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how every factory synth preset and every drum sounds (TD-074): the peak and the loudness of half a second of each. The web
 * player renders the same instruments from its own code and its test replays this file, so a change to a sound on one side that is
 * not made on the other fails a test. Record the file with {@code GDXUNITY_RECORD_FIXTURES=1}.
 */
class InstrumentFingerprintTest {
    private static final String FIXTURE = "instrument-fingerprints.json";
    private static final Path FIXTURE_FILE = Path.of("src", "test", "resources", FIXTURE);
    /** Frames rendered per instrument: half a second. */
    private static final int FRAMES = 22050;
    private static final int PRESET_NOTE = 60;
    /** Allowed difference to the recorded values: Java renders in float, so it can drift a little from a recording made on another JVM. */
    private static final double TOLERANCE = 1e-4;

    private static float[] renderPreset(String name) {
        Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, SynthPatch.preset(name));
        synth.noteOn(PRESET_NOTE, 1f);
        return AudioTestSupport.render(synth, FRAMES);
    }

    private static float[] renderFm(String name) {
        FmSynthesizer synth = new FmSynthesizer(AudioTestSupport.RATE, FmPatch.preset(name));
        synth.noteOn(PRESET_NOTE, 1f);
        return AudioTestSupport.render(synth, FRAMES);
    }

    private static float[] renderPluck(String name) {
        PluckedString string = new PluckedString(AudioTestSupport.RATE, PluckPatch.preset(name));
        string.noteOn(PRESET_NOTE, 1f);
        return AudioTestSupport.render(string, FRAMES);
    }

    private static float[] renderDrum(DrumKit.Drum drum) {
        DrumKit kit = new DrumKit(AudioTestSupport.RATE);
        kit.noteOn(drum.getNote(), 1f);
        return AudioTestSupport.render(kit, FRAMES);
    }

    private static String entry(String name, float[] data) {
        return String.format(Locale.ROOT, "\"%s\":{\"peak\":%.6f,\"rms\":%.6f}", name, AudioTestSupport.peak(data),
            AudioTestSupport.rms(data, 0, data.length));
    }

    private static String record() {
        StringBuilder json = new StringBuilder("{\"frames\":").append(FRAMES).append(",\"note\":").append(PRESET_NOTE).append(",\"presets\":{");
        boolean first = true;
        for (String name : SynthPatch.presetNames()) {
            json.append(first ? "" : ",").append(entry(name, renderPreset(name)));
            first = false;
        }
        json.append("},\"fm\":{");
        first = true;
        for (String name : FmPatch.presetNames()) {
            json.append(first ? "" : ",").append(entry(name, renderFm(name)));
            first = false;
        }
        json.append("},\"pluck\":{");
        first = true;
        for (String name : PluckPatch.presetNames()) {
            json.append(first ? "" : ",").append(entry(name, renderPluck(name)));
            first = false;
        }
        json.append("},\"drums\":{");
        first = true;
        for (DrumKit.Drum drum : DrumKit.drums()) {
            json.append(first ? "" : ",").append(entry(drum.name(), renderDrum(drum)));
            first = false;
        }
        return json.append("}}\n").toString();
    }

    private static JsonValue recorded() throws IOException {
        try (InputStream in = InstrumentFingerprintTest.class.getResourceAsStream("/" + FIXTURE)) {
            assertNotNull(in, FIXTURE + " is missing; record it with GDXUNITY_RECORD_FIXTURES=1");
            return new JsonReader().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void shouldSoundTheWayItWasRecorded() throws IOException {
        JsonValue expected;
        if ("1".equals(System.getenv("GDXUNITY_RECORD_FIXTURES"))) {
            // The file written here is not on the test classpath until the next build, so this run reads back what it wrote
            String text = record();
            Files.writeString(FIXTURE_FILE, text, StandardCharsets.UTF_8);
            expected = new JsonReader().parse(text);
        } else {
            expected = recorded();
        }
        assertEquals(SynthPatch.presetNames().size(), expected.get("presets").size, "recorded presets");
        assertEquals(DrumKit.drums().size(), expected.get("drums").size, "recorded drums");
        for (String name : SynthPatch.presetNames()) {
            check("preset " + name, expected.get("presets").get(name), renderPreset(name));
        }
        for (DrumKit.Drum drum : DrumKit.drums()) {
            check("drum " + drum, expected.get("drums").get(drum.name()), renderDrum(drum));
        }
        assertEquals(FmPatch.presetNames().size(), expected.get("fm").size, "recorded FM presets");
        for (String name : FmPatch.presetNames()) {
            check("FM preset " + name, expected.get("fm").get(name), renderFm(name));
        }
        assertEquals(PluckPatch.presetNames().size(), expected.get("pluck").size, "recorded plucked-string presets");
        for (String name : PluckPatch.presetNames()) {
            check("plucked-string preset " + name, expected.get("pluck").get(name), renderPluck(name));
        }
    }

    private static void check(String what, JsonValue expected, float[] data) {
        assertNotNull(expected, what + " is not in " + FIXTURE);
        assertEquals(expected.getDouble("peak"), AudioTestSupport.peak(data), TOLERANCE, what + " peak");
        assertEquals(expected.getDouble("rms"), AudioTestSupport.rms(data, 0, data.length), TOLERANCE, what + " rms");
        assertTrue(expected.getDouble("rms") > 0.0, what + " was recorded silent");
    }
}
