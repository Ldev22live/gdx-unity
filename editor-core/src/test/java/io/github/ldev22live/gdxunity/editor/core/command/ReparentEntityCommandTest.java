package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReparentEntityCommandTest {
    private EditorState editorState;
    private Scene scene;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        scene = new Scene("TestScene");
        editorState.setActiveScene(scene);
    }

    @Test
    void shouldReparentRootEntityUnderAnotherEntityAndUndoRedo() throws Exception {
        Entity parent = scene.createEntity("Parent");
        Entity child = scene.createEntity("Child");

        assertEquals(2, scene.getRootEntities().size());
        assertNull(child.getTransform().getParent());

        ReparentEntityCommand cmd = new ReparentEntityCommand(editorState, child.getId(), parent.getId(), false);
        boolean executed = editorState.executeCommand(cmd);

        assertTrue(executed);
        assertEquals(1, scene.getRootEntities().size());
        assertSame(parent.getTransform(), child.getTransform().getParent());
        assertEquals(1, parent.getTransform().getChildCount());
        assertSame(child.getTransform(), parent.getTransform().getChildren().get(0));

        // Undo
        boolean undone = editorState.undo();
        assertTrue(undone);
        assertEquals(2, scene.getRootEntities().size());
        assertNull(child.getTransform().getParent());
        assertEquals(0, parent.getTransform().getChildCount());

        // Redo
        boolean redone = editorState.redo();
        assertTrue(redone);
        assertEquals(1, scene.getRootEntities().size());
        assertSame(parent.getTransform(), child.getTransform().getParent());
        assertEquals(1, parent.getTransform().getChildCount());
    }

    @Test
    void shouldReparentChildToDifferentParentAndUndo() throws Exception {
        Entity parentA = scene.createEntity("ParentA");
        Entity parentB = scene.createEntity("ParentB");
        Entity child = scene.createEntity("Child");

        child.getTransform().setParent(parentA.getTransform());
        assertEquals(2, scene.getRootEntities().size());
        assertSame(parentA.getTransform(), child.getTransform().getParent());

        ReparentEntityCommand cmd = new ReparentEntityCommand(editorState, child.getId(), parentB.getId(), false);
        boolean executed = editorState.executeCommand(cmd);

        assertTrue(executed);
        assertSame(parentB.getTransform(), child.getTransform().getParent());
        assertEquals(0, parentA.getTransform().getChildCount());
        assertEquals(1, parentB.getTransform().getChildCount());

        // Undo should restore parentA
        boolean undone = editorState.undo();
        assertTrue(undone);
        assertSame(parentA.getTransform(), child.getTransform().getParent());
        assertEquals(1, parentA.getTransform().getChildCount());
        assertEquals(0, parentB.getTransform().getChildCount());
    }

    @Test
    void shouldUnparentEntityToSceneRootAndUndo() throws Exception {
        Entity parent = scene.createEntity("Parent");
        Entity child = scene.createEntity("Child");
        child.getTransform().setParent(parent.getTransform());

        assertEquals(1, scene.getRootEntities().size());

        ReparentEntityCommand unparentCmd = new ReparentEntityCommand(editorState, child.getId(), null, false);
        boolean executed = editorState.executeCommand(unparentCmd);

        assertTrue(executed);
        assertEquals(2, scene.getRootEntities().size());
        assertNull(child.getTransform().getParent());
        assertEquals(0, parent.getTransform().getChildCount());

        // Undo should restore parent
        boolean undone = editorState.undo();
        assertTrue(undone);
        assertEquals(1, scene.getRootEntities().size());
        assertSame(parent.getTransform(), child.getTransform().getParent());
        assertEquals(1, parent.getTransform().getChildCount());
    }

    @Test
    void shouldRejectSelfParentingWithoutMutatingScene() throws Exception {
        Entity entity = scene.createEntity("Solo");

        ReparentEntityCommand cmd = new ReparentEntityCommand(editorState, entity.getId(), entity.getId(), false);
        boolean executed = editorState.executeCommand(cmd);

        assertFalse(executed);
        assertNull(entity.getTransform().getParent());
        assertTrue(editorState.isStatusError());
        assertTrue(editorState.getStatusMessage().contains("itself"));
        // History should not contain failed command
        assertFalse(editorState.getCommandHistory().canUndo());
    }

    @Test
    void shouldRejectCycleDescendantParentingWithoutMutatingScene() throws Exception {
        Entity grandParent = scene.createEntity("GrandParent");
        Entity parent = scene.createEntity("Parent");
        Entity child = scene.createEntity("Child");

        parent.getTransform().setParent(grandParent.getTransform());
        child.getTransform().setParent(parent.getTransform());

        // Attempting to parent grandParent under its grandchild
        ReparentEntityCommand cmd = new ReparentEntityCommand(editorState, grandParent.getId(), child.getId(), false);
        boolean executed = editorState.executeCommand(cmd);

        assertFalse(executed);
        assertNull(grandParent.getTransform().getParent());
        assertSame(grandParent.getTransform(), parent.getTransform().getParent());
        assertTrue(editorState.isStatusError());
        assertTrue(editorState.getStatusMessage().contains("descendant"));
        assertFalse(editorState.getCommandHistory().canUndo());
    }

    @Test
    void shouldPreserveWorldTransformWhenPreserveFlagIsTrue() throws Exception {
        Entity parent = scene.createEntity("Parent");
        parent.getTransform().setLocalPosition(100f, 50f, 0f);

        Entity child = scene.createEntity("Child");
        child.getTransform().setLocalPosition(150f, 70f, 0f);

        Vector3 initialWorldPos = child.getTransform().getWorldPosition();
        assertEquals(150f, initialWorldPos.x, 0.001f);
        assertEquals(70f, initialWorldPos.y, 0.001f);

        ReparentEntityCommand cmd = new ReparentEntityCommand(editorState, child.getId(), parent.getId(), true);
        boolean executed = editorState.executeCommand(cmd);
        assertTrue(executed);

        // Child local position should now be (50, 20) relative to parent at (100, 50)
        Vector3 childLocal = child.getTransform().getLocalPosition();
        assertEquals(50f, childLocal.x, 0.001f);
        assertEquals(20f, childLocal.y, 0.001f);

        // Child world position should be preserved at (150, 70)
        Vector3 childWorld = child.getTransform().getWorldPosition();
        assertEquals(150f, childWorld.x, 0.001f);
        assertEquals(70f, childWorld.y, 0.001f);

        // Undo
        boolean undone = editorState.undo();
        assertTrue(undone);
        assertEquals(150f, child.getTransform().getLocalPosition().x, 0.001f);
        assertEquals(70f, child.getTransform().getLocalPosition().y, 0.001f);
    }
}
