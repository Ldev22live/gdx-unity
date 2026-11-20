package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway.EditOffer;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway.Kind;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.CustomizeMenu;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Customize gateway's "Edit Selected" (roadmap M1b.7) over every entity of every shipped sample, with the editor's full serializer
 * list: every graph script and every animator is offered in its workspace, and every file an offer opens exists.
 */
class CustomizeGatewaySamplesTest {
    private static final Path ASSETS = Path.of("..", "assets");

    @BeforeAll
    static void initNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldOfferEveryGraphAndClipOfEverySampleEntityAndOnlyFilesThatExist() throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        EditorApplication.registerComponentSerializers(registry);
        SceneSerializer serializer = new SceneSerializer(registry);
        List<Path> scenes;
        try (Stream<Path> files = Files.list(ASSETS.resolve("samples"))) {
            scenes = files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        assertFalse(scenes.isEmpty());

        List<String> problems = new ArrayList<>();
        Map<Workspace, Integer> opened = new EnumMap<>(Workspace.class);
        int handedTo3DModeling = 0;
        for (Path file : scenes) {
            Scene scene = serializer.fromJson(Files.readString(file));
            for (Entity entity : scene.getAllEntities()) {
                String where = file.getFileName() + " / " + entity.getName();
                List<EditOffer> offers = CustomizeGateway.editSelected(entity, registry);
                for (EditOffer offer : offers) {
                    if (offer.kind() == Kind.OPEN_ASSET) {
                        opened.merge(offer.workspace(), 1, Integer::sum);
                        if (!Files.isRegularFile(ASSETS.resolve(offer.assetPath()))) {
                            problems.add(where + ": offers " + offer.assetPath() + ", which does not exist");
                        }
                    }
                }
                // Roadmap M3c.4: every model or shape is handed to 3D Modeling first, and its notice never claims mesh editing
                if (ModelingHandOff.offersEdit3DModel(entity)) {
                    handedTo3DModeling++;
                    if (offers.isEmpty() || offers.get(0).kind() != Kind.OPEN_MODELING) {
                        problems.add(where + ": Edit 3D Model is not the first entry");
                    }
                    if (ModelingHandOff.noticeFor(entity).meshEditing()) {
                        problems.add(where + ": its notice claims mesh editing, which is R7a");
                    }
                } else if (offers.stream().anyMatch(o -> o.kind() == Kind.OPEN_MODELING)) {
                    problems.add(where + ": offers Edit 3D Model with no model or shape");
                }
                GraphScriptComponent graph = entity.getComponent(GraphScriptComponent.class);
                if (graph != null && !graph.getGraphPath().isBlank() && offers.stream().noneMatch(o -> o.workspace() == Workspace.LOGIC
                    && graph.getGraphPath().equals(o.assetPath()))) {
                    problems.add(where + ": its graph " + graph.getGraphPath() + " is not offered in Logic");
                }
                AnimatorComponent animator = entity.getComponent(AnimatorComponent.class);
                if (animator != null && !animator.getClipPath().isBlank() && offers.stream().noneMatch(o -> o.workspace() == Workspace.ANIMATION
                    && animator.getClipPath().equals(o.assetPath()))) {
                    problems.add(where + ": its clip " + animator.getClipPath() + " is not offered in Animation");
                }
            }
        }
        assertEquals(List.of(), problems);
        assertTrue(opened.getOrDefault(Workspace.LOGIC, 0) > 0 && opened.getOrDefault(Workspace.ANIMATION, 0) > 0
            && opened.getOrDefault(Workspace.AUDIO_MUSIC, 0) > 0, "the samples exercise graphs, clips and songs: " + opened);
        assertTrue(handedTo3DModeling > 0, "the samples have models and shapes to hand to 3D Modeling");
    }

    @Test
    void shouldListEditSelectedFirstThenEveryWorkspaceWithTheCurrentOneMarked() {
        List<EditOffer> chosen = new ArrayList<>();
        List<Workspace.Destination> visited = new ArrayList<>();
        EditOffer song = new EditOffer("Edit Music theme.song", Workspace.AUDIO_MUSIC, Kind.OPEN_ASSET, "music/theme.song", null, null);
        EditOffer sound = new EditOffer("Edit Sound door.wav", Workspace.AUDIO_MUSIC, Kind.UNAVAILABLE, "sounds/door.wav", null, "why");

        List<ContextMenu.Item> items = CustomizeMenu.items("Speaker", List.of(song, sound), Workspace.LOGIC, Workspace.TAB_VISUAL_SCRIPTING,
            chosen::add, visited::add);

        List<String> labels = items.stream().map(i -> i.label() == null ? "---" : i.label()).toList();
        assertEquals(List.of("Edit Music theme.song", "Edit Sound door.wav" + CustomizeMenu.NOT_YET_SUFFIX, "---", "World", "3D Modeling",
            "2D Studio: Sprites", "2D Studio: Tiles", "Animation", "Audio & Music", "Logic" + CustomizeMenu.CURRENT_SUFFIX, "Assets"), labels);
        items.get(1).action().run();
        items.get(3).action().run();
        items.get(6).action().run();
        assertEquals(List.of(sound), chosen, "an entry with no editor still explains itself when chosen");
        assertEquals(List.of(Workspace.WORLD, Workspace.STUDIO_2D), visited.stream().map(Workspace.Destination::workspace).toList());
        assertEquals(Workspace.TAB_TILES, visited.get(1).tab(), "the Tiles tab has its own entry now the tabs are not in the header");

        List<ContextMenu.Item> nothing = CustomizeMenu.items(null, List.of(), Workspace.WORLD, Workspace.TAB_WORLD, chosen::add, visited::add);
        assertFalse(nothing.get(0).enabled(), "with no selection the first row says how to get an Edit Selected entry");
        assertEquals("World" + CustomizeMenu.CURRENT_SUFFIX, nothing.get(2).label());
        List<ContextMenu.Item> tiles = CustomizeMenu.items(null, List.of(), Workspace.STUDIO_2D, Workspace.TAB_TILES, chosen::add, visited::add);
        assertEquals(List.of("2D Studio: Sprites", "2D Studio: Tiles" + CustomizeMenu.CURRENT_SUFFIX),
            tiles.subList(4, 6).stream().map(ContextMenu.Item::label).toList(), "only the 2D Studio tab on screen is marked");
    }
}
