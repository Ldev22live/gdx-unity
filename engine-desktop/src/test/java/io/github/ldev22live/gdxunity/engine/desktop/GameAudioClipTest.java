package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.audio.WavIO;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioListenerComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recorded {@code .wav} clips and positional sound through {@link GameAudio} (Phase 20-C). */
class GameAudioClipTest {
    private static final int BLOCK = 256;
    private static final String CLIP = "sounds/hit.wav";

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** Writes a clip of a constant level into the project. */
    private void writeClip(String path, float level) throws IOException {
        float[] samples = new float[4000];
        Arrays.fill(samples, level);
        Path file = project.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, WavIO.write(new SampleData(samples, 22050)));
    }

    private GameAudio audioFor(Scene scene) {
        return new GameAudio(scene, false, GameAudio.assetsOf(AssetSource.directory(project.toFile())));
    }

    private static AudioSourceComponent addSource(Scene scene, String name, float x, String sound, boolean spatial) {
        Entity entity = scene.createEntity(name);
        entity.getTransform().setLocalPosition(x, 0f, 0f);
        AudioSourceComponent source = new AudioSourceComponent("hit", sound);
        source.setSpatial(spatial);
        source.setDistances(100f, 1000f);
        entity.addComponent(source);
        return source;
    }

    /** Peaks of the left and right speaker over the first block of whatever is playing. */
    private static float[] peaks(GameAudio audio) {
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];
        audio.render(left, right, BLOCK);
        float leftPeak = 0f;
        float rightPeak = 0f;
        for (int i = 0; i < BLOCK; i++) {
            leftPeak = Math.max(leftPeak, Math.abs(left[i]));
            rightPeak = Math.max(rightPeak, Math.abs(right[i]));
        }
        return new float[] {leftPeak, rightPeak};
    }

    @Test
    void shouldPlayARecordedClipOfTheProject() throws IOException {
        writeClip(CLIP, 0.5f);
        Scene scene = new Scene("Level");
        AudioSourceComponent source = addSource(scene, "Hitter", 0f, CLIP, false);
        GameAudio audio = audioFor(scene);

        assertEquals(List.of(), audio.bind());
        source.play();
        float[] peaks = peaks(audio);

        assertEquals(0.5f, peaks[0], 0.01f);
        assertEquals(0.5f, peaks[1], 0.01f);
        assertEquals(1, audio.getBoundCount());
        audio.dispose();
    }

    @Test
    void shouldRefuseToStartASceneWhoseClipIsMissingAndNameTheEntityAndFile() {
        Scene scene = new Scene("Level");
        addSource(scene, "Hitter", 0f, "sounds/nowhere.wav", false);
        GameAudio audio = audioFor(scene);

        List<String> problems = audio.bind();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Hitter") && problems.get(0).contains("sounds/nowhere.wav"), problems.get(0));
        assertEquals(0, audio.getBoundCount(), "nothing stays bound");
        audio.dispose();
    }

    @Test
    void shouldRefuseAFileThatIsNotAWavAndSayWhy() throws IOException {
        Files.createDirectories(project.resolve("sounds"));
        Files.writeString(project.resolve(CLIP), "this is not audio");
        Scene scene = new Scene("Level");
        addSource(scene, "Hitter", 0f, CLIP, false);
        GameAudio audio = audioFor(scene);

        List<String> problems = audio.bind();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("cannot be loaded"), problems.get(0));
        audio.dispose();
    }

    @Test
    void shouldSendASpatialSourceOnTheListenersRightMostlyToTheRightSpeaker() throws IOException {
        writeClip(CLIP, 1f);
        Scene scene = new Scene("Level");
        scene.createEntity("Ears").addComponent(new AudioListenerComponent());
        AudioSourceComponent source = addSource(scene, "Radio", 550f, CLIP, true);
        GameAudio audio = audioFor(scene);
        assertEquals(List.of(), audio.bind());

        source.play();
        float[] peaks = peaks(audio);

        assertEquals(0f, peaks[0], 1e-4f, "all of it is to the right");
        assertEquals(0.5f, peaks[1], 0.01f, "550 of a fade from 100 to 1000 is half volume");
        audio.dispose();
    }

    @Test
    void shouldAlsoPanTheFactoryEffectsAndLeaveNonSpatialSourcesInTheMiddle() {
        Scene scene = new Scene("Level");
        scene.createEntity("Ears").addComponent(new AudioListenerComponent());
        AudioSourceComponent left = addSource(scene, "Left", -400f, "Coin", true);
        GameAudio audio = audioFor(scene);
        assertEquals(List.of(), audio.bind());

        left.play();
        float[] peaks = peaks(audio);

        assertTrue(peaks[0] > 0.01f && peaks[1] < 1e-4f, "left " + peaks[0] + ", right " + peaks[1]);
        audio.dispose();
    }
}
