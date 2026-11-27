package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.sculpt.StaticMeshImport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap A06: leaving 3D Modeling never discards or saves a mesh edit. The drafts of a part survive Back to World and a change of
 * selection, come back with their undo history, and stop being "unsaved" only on Save or an explicit Discard.
 */
class MeshDraftKeeperTest {
    @TempDir
    Path root;

    private final String source = "thirdparty/wall.gltf";
    private final MeshDraftKeeper keeper = new MeshDraftKeeper();
    private EditorState state;
    private Entity wall;
    private Entity other;
    private int opened;

    @BeforeAll
    static void initNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void openWall() throws IOException {
        String original = StaticMeshImport.read(Path.of("..", "assets"),
            Path.of("thirdparty/kenney/building-kit/Models/GLB format/wall.glb")).modelJson();
        Files.createDirectory(root.resolve("thirdparty"));
        Files.writeString(root.resolve(source), original);
        state = new EditorState();
        Scene scene = new Scene("World");
        wall = scene.createEntity("Wall");
        other = scene.createEntity("Another wall");
        wall.addComponent(new ModelComponent(source));
        other.addComponent(new ModelComponent(source));
        state.setActiveScene(scene);
    }

    private MeshSaveSession session(Entity part) throws IOException {
        return keeper.session(part, () -> {
            opened++;
            return new MeshSaveSession(root, new MeshWorkspace(root, part).open(part));
        });
    }

    private static boolean extrude(MeshSaveSession session) {
        return session.drafts().getFirst().apply(MeshPartEditor.Tool.Extrude, MeshPartEditor.Kind.Face, 0, .2f, 0, 0);
    }

    @Test
    void shouldKeepTheSameDraftsAcrossLeavingAndComingBack() throws IOException {
        MeshSaveSession first = session(wall);
        assertTrue(extrude(first));

        // Back to World, select the other wall, then come back to the first one
        session(other);
        MeshSaveSession again = session(wall);

        assertSame(first, again, "the same drafts, not a fresh copy of the file");
        assertEquals(2, opened, "each part read from its file once");
        assertTrue(again.drafts().getFirst().isModified());
        assertTrue(again.drafts().getFirst().undo(), "the undo history came back too");
        assertFalse(keeper.isUnsaved(again));
    }

    @Test
    void shouldReportUnsavedEditsOnBackToWorldUntilSaved() throws IOException {
        MeshSaveSession edited = session(wall);
        assertTrue(keeper.backToWorldNotice().isEmpty(), "opening without editing leaves nothing unsaved");
        assertTrue(extrude(edited));

        assertEquals(List.of("Wall"), keeper.unsavedParts());
        assertTrue(keeper.backToWorldNotice().orElseThrow().startsWith("Unsaved mesh edits of Wall are kept, not saved"));
        assertEquals(source, wall.getComponent(ModelComponent.class).getModelPath(), "nothing was saved behind the person's back");

        edited.save(MeshSaveSession.Mode.NEW, "Wall variant", state, false);
        keeper.markSaved(edited);
        assertTrue(keeper.unsavedParts().isEmpty());
        assertTrue(extrude(edited));
        assertEquals(List.of("Wall"), keeper.unsavedParts(), "a change after Save is unsaved again");
    }

    @Test
    void shouldStartFromTheFileAgainOnlyAfterAnExplicitDiscard() throws IOException {
        MeshSaveSession edited = session(wall);
        assertTrue(extrude(edited));

        assertTrue(keeper.discard(wall));

        MeshSaveSession fresh = session(wall);
        assertNotSame(edited, fresh);
        assertFalse(fresh.drafts().getFirst().isModified());
        assertTrue(keeper.unsavedParts().isEmpty());
    }

    @Test
    void shouldOpenAgainWhenThePartUsesAnotherModel() throws IOException {
        MeshSaveSession before = session(wall);
        String unique = before.save(MeshSaveSession.Mode.UNIQUE, "Wall unique", state, false);
        assertEquals(unique, wall.getComponent(ModelComponent.class).getModelPath());

        MeshSaveSession after = session(wall);

        assertNotSame(before, after, "the part now uses its unique model, read from that file");
        assertEquals(unique, after.target());
    }
}
