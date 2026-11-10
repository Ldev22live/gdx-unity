package io.github.ldev22live.gdxunity.editor.animation;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponentSerializer;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Animations tab's clip library: what it lists, which entity a clip plays on, and assigning a clip with undo (TD-044). */
class ClipLibraryTest {

    private static final File PROJECT_ASSETS = new File("../assets");

    @TempDir
    Path assetRoot;

    private EditorState editorState;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new AnimatorComponentSerializer());
    }

    private Entity humanoid(String name) {
        Entity root = editorState.getActiveScene().createEntity(name);
        Entity hips = editorState.getActiveScene().createEntity("Hips");
        hips.getTransform().setParent(root.getTransform());
        return root;
    }

    @Test
    void shouldListTheHumanoidClipsOfTheProjectFirst() {
        List<ClipLibrary.Entry> entries = ClipLibrary.list(PROJECT_ASSETS);

        List<String> humanoid = entries.stream().filter(ClipLibrary.Entry::humanoid).map(ClipLibrary.Entry::name).toList();
        assertEquals(List.of("Crouch", "Dance", "Fall", "Idle", "Jump", "LookAround", "Punch", "Run", "Walk", "Wave"), humanoid);
        assertTrue(entries.subList(0, humanoid.size()).stream().allMatch(ClipLibrary.Entry::humanoid), "humanoid group comes first");
        assertEquals("animations/humanoid/Run.anim", entries.stream().filter(e -> e.name().equals("Run")).findFirst().orElseThrow().asset().getPath());
        assertTrue(entries.stream().anyMatch(e -> !e.humanoid() && e.name().equals("BallBounce")), "other clips are listed after");
    }

    @Test
    void shouldListNothingWhenThereIsNoAnimationsFolder() {
        assertEquals(List.of(), ClipLibrary.list(assetRoot.toFile()));
    }

    @Test
    void shouldFindTheSelectedHumanoidElseTheFirstOneInTheScene() {
        Entity plain = editorState.getActiveScene().createEntity("Plain");
        Entity first = humanoid("First");
        Entity second = humanoid("Second");

        assertSame(second, ClipLibrary.findHumanoid(editorState.getActiveScene(), second));
        assertSame(first, ClipLibrary.findHumanoid(editorState.getActiveScene(), plain));
        assertSame(first, ClipLibrary.findHumanoid(editorState.getActiveScene(), null));
        assertNull(ClipLibrary.findHumanoid(new io.github.ldev22live.gdxunity.engine.scene.Scene("Empty"), plain));
    }

    @Test
    void shouldAddAnAnimatorThenChangeItsClipEachWithOneUndo() throws Exception {
        Entity hero = humanoid("Hero");
        AssetReference walk = new AssetReference("animations/humanoid/Walk.anim");
        AssetReference run = new AssetReference("animations/humanoid/Run.anim");

        assertTrue(ClipLibrary.assign(editorState, hero, walk));
        AnimatorComponent animator = hero.getComponent(AnimatorComponent.class);
        assertNotNull(animator);
        assertEquals(walk.getPath(), animator.getClipPath());

        assertTrue(ClipLibrary.assign(editorState, hero, run));
        assertSame(animator, hero.getComponent(AnimatorComponent.class), "the same Animator, only its clip changes");
        assertEquals(run.getPath(), animator.getClipPath());

        assertTrue(editorState.undo());
        assertEquals(walk.getPath(), hero.getComponent(AnimatorComponent.class).getClipPath());
        assertTrue(editorState.undo());
        assertNull(hero.getComponent(AnimatorComponent.class));
    }
}
