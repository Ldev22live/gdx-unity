package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Songs in scenes (TD-061): a Music Source plays a bundled {@code .song} through {@link GameAudio}, mixed at its volume. */
class GameAudioMusicTest {

    private static final String SONG = "music/neon-drive.song";
    private static final int BLOCK = 512;
    /** Blocks rendered while listening: about 1.5 s, long enough for the first notes of a song. */
    private static final int BLOCKS = 130;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static GameAudio audioFor(Scene scene) {
        return new GameAudio(scene, false, GameAudio.assetsOf(AssetSource.directory(new File("../assets"))));
    }

    private static MusicSourceComponent addSource(Scene scene, String song) {
        MusicSourceComponent source = new MusicSourceComponent(song);
        scene.createEntity("Band").addComponent(source);
        return source;
    }

    /** Peak absolute sample over the blocks the mixer renders. */
    private static float peak(GameAudio audio) {
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];
        float peak = 0f;
        for (int block = 0; block < BLOCKS; block++) {
            audio.render(left, right, BLOCK);
            for (int i = 0; i < BLOCK; i++) {
                peak = Math.max(peak, Math.max(Math.abs(left[i]), Math.abs(right[i])));
            }
        }
        return peak;
    }

    @Test
    void shouldStaySilentUntilTheSongStartsThenPlayItAndStopAgain() {
        Scene scene = new Scene("Music");
        MusicSourceComponent source = addSource(scene, SONG);
        GameAudio audio = audioFor(scene);

        assertEquals(List.of(), audio.bind());
        assertEquals(1, audio.getBoundSongCount());
        assertEquals(0f, peak(audio), 0f, "nothing sounds before the song starts");

        source.play();
        assertEquals(1, audio.getPlayingSongCount());
        assertTrue(peak(audio) > 0.01f, "the song makes sound");

        source.stop();
        assertEquals(0, audio.getPlayingSongCount());
        assertEquals(0f, peak(audio), 0f);
        audio.dispose();
    }

    @Test
    void shouldStartASongByItselfWhenTheSceneStarts() {
        Scene scene = new Scene("Music");
        addSource(scene, SONG);
        GameAudio audio = audioFor(scene);
        audio.bind();

        scene.update(0.016f);

        assertEquals(1, audio.getPlayingSongCount());
        audio.dispose();
    }

    @Test
    void shouldPlayQuieterAtALowerVolume() {
        Scene scene = new Scene("Music");
        MusicSourceComponent source = addSource(scene, SONG);
        GameAudio audio = audioFor(scene);
        audio.bind();
        source.play();
        float full = peak(audio);
        source.stop();
        source.setVolume(0.25f);
        source.play();

        float quiet = peak(audio);

        assertEquals(full * 0.25f, quiet, full * 0.02f);
        audio.dispose();
    }

    @Test
    void shouldRefuseToStartWhenASongCannotBeLoadedAndNameTheEntity() {
        Scene scene = new Scene("Music");
        addSource(scene, "music/nope.song");
        addSource(scene, "");
        GameAudio audio = audioFor(scene);

        List<String> problems = audio.bind();

        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("Band") && problems.get(0).contains("music/nope.song"), problems.get(0));
        assertTrue(problems.get(1).contains("no song chosen"), problems.get(1));
        assertEquals(0, audio.getBoundSongCount());
    }

    @Test
    void shouldIgnoreDisabledSourcesAndReleaseEverythingOnDispose() {
        Scene scene = new Scene("Music");
        MusicSourceComponent off = addSource(scene, "music/nope.song");
        off.setEnabled(false);
        MusicSourceComponent on = addSource(scene, SONG);
        GameAudio audio = audioFor(scene);

        assertEquals(List.of(), audio.bind());
        assertEquals(1, audio.getBoundSongCount());
        audio.dispose();

        assertFalse(on.isBound());
        Entity band = scene.getAllEntities().get(0);
        assertFalse(band.getComponent(MusicSourceComponent.class).isBound());
    }
}
