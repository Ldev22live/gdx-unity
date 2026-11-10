package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameAudio;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The samples with background music (TD-061): each scene names a bundled song, and the song loads and plays. */
class SampleMusicTest {

    private static final File PROJECT = new File("../assets");
    private static final Map<String, String> SONGS = Map.of(
        "platformer-2d", "music/pixel-quest.song", "collector-3d", "music/lofi-evening.song", "racing-3d", "music/neon-drive.song",
        "ruins-runner-25d", "music/ambient-drift.song", "runner-3d", "music/funk-machine.song");

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static Scene load(String sample) throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        EditorApplication.registerComponentSerializers(registry);
        return new SceneSerializer(registry).fromJson(Files.readString(new File(PROJECT, "samples/" + sample + ".json").toPath()));
    }

    @Test
    void shouldGiveEachMusicSampleOneSongThatExists() throws IOException {
        for (Map.Entry<String, String> sample : SONGS.entrySet()) {
            List<MusicSourceComponent> sources = new ArrayList<>();
            for (Entity entity : load(sample.getKey()).getAllEntities()) {
                sources.addAll(entity.getComponents(MusicSourceComponent.class));
            }
            assertEquals(1, sources.size(), sample.getKey());
            assertEquals(sample.getValue(), sources.get(0).getSong(), sample.getKey());
            assertTrue(sources.get(0).isLoop() && sources.get(0).isPlayOnStart(), sample.getKey());
            assertTrue(new File(PROJECT, sources.get(0).getSong()).isFile(), sources.get(0).getSong());
        }
    }

    @Test
    void shouldBindAndStartEachSampleSongWithoutProblems() throws IOException {
        for (String sample : SONGS.keySet()) {
            Scene scene = load(sample);
            GameAudio audio = new GameAudio(scene, false, GameAudio.assetsOf(AssetSource.directory(PROJECT)));

            assertEquals(List.of(), audio.bind(), sample);
            scene.update(0.016f);

            assertEquals(1, audio.getPlayingSongCount(), sample);
            audio.dispose();
        }
    }
}
