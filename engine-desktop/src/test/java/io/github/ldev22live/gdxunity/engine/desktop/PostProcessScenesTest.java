package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessPreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessSettings;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessVolumeComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessVolumes;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M6.2: the material showroom under post-processing looks, written to build/m6-2-post for the game runtime's screenshots
 * (the done-when "a volume changes the frame"). Each scene is read back by the game runtime's own loader, which must find the same
 * look, and plays 30 frames without errors. "Outside" has a Noir box volume away from the camera, so it must look like "Plain".
 */
class PostProcessScenesTest {
    private static final Path OUT = Path.of("build", "m6-2-post");
    private static final File SAMPLE = new File("../assets/samples/materials-3d.json");
    private static final Path ASSETS = Path.of("..", "assets");
    /** Where the box volume of "Outside" stands: 50 m to the side of the showroom, its 10 m box far from the camera. */
    private static final float OUTSIDE_X = 5000f;

    private record Look(String name, PostProcessPreset preset, boolean global) {}

    private static final List<Look> LOOKS = List.of(
        new Look("Plain", null, true),
        new Look("Cinematic", PostProcessPreset.CINEMATIC, true),
        new Look("Dreamy", PostProcessPreset.DREAMY, true),
        new Look("Noir", PostProcessPreset.NOIR, true),
        new Look("Vivid", PostProcessPreset.VIVID, true),
        new Look("Outside", PostProcessPreset.NOIR, false));

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldWriteAndPlayTheShowroomUnderEachLook() throws IOException {
        Files.createDirectories(OUT.resolve("scenes"));
        copyFolder("materials");
        copyFolder("textures");
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        SceneSerializer serializer = new SceneSerializer(registry);
        AssetSource project = AssetSource.directory(OUT.toFile());

        for (Look look : LOOKS) {
            Scene scene = GameSession.loadScene(SAMPLE);
            scene.setName("Showroom " + look.name());
            if (look.preset() != null) {
                PostProcessVolumeComponent volume = new PostProcessVolumeComponent();
                look.preset().applyTo(volume);
                volume.setGlobal(look.global());
                Entity entity = scene.createEntity("Post Process Volume");
                entity.getTransform().setLocalPosition(look.global() ? 0f : OUTSIDE_X, 0f, 0f);
                entity.addComponent(volume);
            }
            String path = "scenes/" + look.name() + ".json";
            Files.writeString(OUT.resolve(path), serializer.toJson(scene), StandardCharsets.UTF_8);

            Scene loaded = GameSession.loadScene(project, path);
            Vector3 camera = loaded.findEntityByName("Main Camera").getTransform().getWorldPosition();
            PostProcessSettings seen = new PostProcessVolumes().resolve(loaded, camera, new PostProcessSettings());
            if (look.preset() != null) {
                PostProcessVolumeComponent loadedVolume = loaded.findEntityByName("Post Process Volume")
                    .getComponent(PostProcessVolumeComponent.class);
                assertEquals(look.preset(), PostProcessPreset.matching(loadedVolume).orElseThrow(), look.name());
            }
            assertEquals(look.preset() != null && look.global(), seen.isActive(), look.name() + ": what the camera sees");

            GameSession session = new GameSession(project, loaded, new ScriptRegistry(), GameSession.CONSOLE);
            try {
                assertEquals(List.of(), session.start(), look.name());
                for (int i = 0; i < 30; i++) {
                    session.update(1f / 60f);
                }
                assertEquals(List.of(), session.getRuntimeErrors(), look.name());
            } finally {
                session.dispose();
            }
        }
        Files.writeString(OUT.resolve("gdxunity.project"), "format=1\nname=Post\nstartScene=scenes/Plain.json\n", StandardCharsets.UTF_8);
        assertTrue(Files.exists(OUT.resolve("materials/Lava.material")));
    }

    private static void copyFolder(String name) throws IOException {
        Path from = ASSETS.resolve(name);
        Path to = OUT.resolve(name);
        Files.createDirectories(to);
        try (Stream<Path> files = Files.list(from)) {
            for (Path file : files.toList()) {
                if (Files.isRegularFile(file)) {
                    Files.copy(file, to.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
