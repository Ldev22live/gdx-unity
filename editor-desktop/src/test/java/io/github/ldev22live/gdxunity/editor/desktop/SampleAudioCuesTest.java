package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.desktop.GameAudio;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the sample games' sound effects honest (TD-043). A script triggers a sound by cue name and
 * {@link AudioSourceComponent#play} does nothing for a cue nobody answers, so a typo in a script or a scene would be
 * silent. This checks, for every scripted sample, that the cues its scripts play and the cues its scene answers are
 * the same set, and that every sound the scene names exists.
 */
class SampleAudioCuesTest {
    /** Tests run in the module folder; the project's assets are one level up. */
    private static final File PROJECT = new File("../assets");
    private static final List<String> SCRIPTED_SAMPLES = List.of("platformer-2d", "breakout-2d", "shooter-2d",
        "collector-3d", "runner-3d", "hopper-3d", "ruins-runner-25d", "three-heroes-25d", "grapple-gorge-25d",
        "racing-3d", "arena-fps-3d", "fighter-25d", "puzzle-3d");
    private static final Pattern PLAY_CALL = Pattern.compile("AudioSourceComponent\\.play\\([^;]*\\);");
    private static final Pattern QUOTED = Pattern.compile("\"(\\w+)\"");

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    private static Scene load(String sample) throws IOException {
        return GameSession.loadScene(new File(PROJECT, "samples/" + sample + ".json"));
    }

    /** The cues the scene's bound scripts trigger, read from the script sources. */
    private static Set<String> cuesPlayedByScripts(Scene scene) throws IOException {
        Set<String> cues = new TreeSet<>();
        for (Entity entity : scene.getAllEntities()) {
            for (EntityScriptBindingComponent binding : entity.getComponents(EntityScriptBindingComponent.class)) {
                File source = new File(PROJECT, "scripts/" + binding.getScriptClassName().replace('.', '/') + ".java");
                assertTrue(source.isFile(), "script source for " + binding.getScriptClassName());
                Matcher call = PLAY_CALL.matcher(Files.readString(source.toPath()));
                while (call.find()) {
                    Matcher cue = QUOTED.matcher(call.group());
                    while (cue.find()) {
                        cues.add(cue.group(1));
                    }
                }
            }
        }
        return cues;
    }

    private static Set<String> cuesAnsweredByScene(Scene scene) {
        Set<String> cues = new TreeSet<>();
        for (Entity entity : scene.getAllEntities()) {
            for (AudioSourceComponent source : entity.getComponents(AudioSourceComponent.class)) {
                assertTrue(cues.add(source.getCue()), "cue '" + source.getCue() + "' is answered twice");
            }
        }
        return cues;
    }

    @Test
    void shouldAnswerExactlyTheCuesEachSampleScriptPlays() throws IOException {
        for (String sample : SCRIPTED_SAMPLES) {
            Scene scene = load(sample);

            Set<String> played = cuesPlayedByScripts(scene);

            assertFalse(played.isEmpty(), sample + " has no sound effects");
            assertEquals(played, cuesAnsweredByScene(scene), sample + ": scripts play vs. scene answers");
        }
    }

    @Test
    void shouldBindEverySampleSoundEffectToARealSound() throws IOException {
        for (String sample : SCRIPTED_SAMPLES) {
            Scene scene = load(sample);
            GameAudio audio = new GameAudio(scene, false, GameAudio.assetsOf(io.github.ldev22live.gdxunity.engine.core.asset.AssetSource.directory(PROJECT)));

            List<String> problems = audio.bind();

            assertEquals(List.of(), problems, sample);
            assertEquals(cuesAnsweredByScene(scene).size(), audio.getBoundCount(), sample);
            audio.dispose();
        }
    }
}
