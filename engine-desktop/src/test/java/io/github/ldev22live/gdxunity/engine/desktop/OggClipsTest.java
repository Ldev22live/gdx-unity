package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ogg Vorbis clips (Phase 20-C). The fixture {@code sounds/tone.ogg} is half a second of a 440 Hz sine wave at half scale, mono, 22,050
 * Hz, made once with libsndfile outside the project; nothing in the project can write Ogg Vorbis.
 */
class OggClipsTest {
    private static final String FIXTURE = "/sounds/tone.ogg";

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static byte[] fixture() throws IOException {
        try (InputStream in = OggClipsTest.class.getResourceAsStream(FIXTURE)) {
            return in.readAllBytes();
        }
    }

    @Test
    void shouldDecodeTheToneWithItsLengthRateAndLevel() throws IOException {
        SampleData clip = OggClips.read(fixture());

        assertTrue(clip.isMono());
        assertEquals(22050, clip.getSampleRate());
        assertEquals(0.5f, clip.getDurationSeconds(), 0.05f);
        assertEquals(0.5f, clip.getPeak(), 0.05f, "a sine wave at half scale");
    }

    @Test
    void shouldDecodeASineWaveOfAboutFourHundredAndFortyHertz() throws IOException {
        float[] samples = OggClips.read(fixture()).getLeft();

        int crossings = 0;
        for (int i = 2000; i < samples.length - 1; i++) {
            if (samples[i] <= 0f && samples[i + 1] > 0f) {
                crossings++;
            }
        }
        float seconds = (samples.length - 2001) / 22050f;
        assertEquals(440f, crossings / seconds, 15f, "upward zero crossings per second");
    }

    @Test
    void shouldRefuseBytesThatAreNotOggVorbis() {
        IOException refused = assertThrows(IOException.class, () -> OggClips.read("this is not an ogg file".getBytes()));

        assertTrue(refused.getMessage().startsWith("Not an Ogg Vorbis file"), refused.getMessage());
        assertThrows(IOException.class, () -> OggClips.read(new byte[0]));
    }

    @Test
    void shouldPlayAnOggClipOfTheProjectLikeAWavClip() throws IOException {
        Files.createDirectories(project.resolve("sounds"));
        Files.write(project.resolve("sounds/tone.ogg"), fixture());
        Scene scene = new Scene("Level");
        Entity entity = scene.createEntity("Tone");
        AudioSourceComponent source = new AudioSourceComponent("tone", "sounds/tone.ogg");
        entity.addComponent(source);
        GameAudio audio = new GameAudio(scene, false, GameAudio.assetsOf(AssetSource.directory(project.toFile())));

        assertEquals(List.of(), audio.bind());
        source.play();
        float[] left = new float[512];
        float[] right = new float[512];
        audio.render(left, right, 512);

        float peak = 0f;
        for (float value : left) {
            peak = Math.max(peak, Math.abs(value));
        }
        assertTrue(source.isClip());
        assertEquals(0.5f, peak, 0.06f);
        audio.dispose();
    }

    @Test
    void shouldRefuseASceneWhoseOggClipIsCorruptAndNameTheEntity() throws IOException {
        Files.createDirectories(project.resolve("sounds"));
        Files.writeString(project.resolve("sounds/bad.ogg"), "garbage");
        Scene scene = new Scene("Level");
        scene.createEntity("Tone").addComponent(new AudioSourceComponent("tone", "sounds/bad.ogg"));
        GameAudio audio = new GameAudio(scene, false, GameAudio.assetsOf(AssetSource.directory(project.toFile())));

        List<String> problems = audio.bind();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Tone") && problems.get(0).contains("bad.ogg"), problems.get(0));
        audio.dispose();
    }
}
