package io.github.ldev22live.gdxunity.editor.core.play;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ShapeRendererComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlayModeControllerTest {

    @BeforeAll
    static void initNatives() {
        EditorTestHelper.initNatives();
    }

    private EditorState editorState;
    private PlayModeController controller;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new ShapeRendererComponentSerializer());
        editorState.getSerializer().getRegistry().register(new io.github.ldev22live.gdxunity.engine.scene.physics.serialization.RigidBody2DComponentSerializer());
        editorState.getSerializer().getRegistry().register(new io.github.ldev22live.gdxunity.engine.scene.physics.serialization.BoxCollider2DComponentSerializer());
        editorState.getSerializer().getRegistry().register(new io.github.ldev22live.gdxunity.engine.scene.physics.serialization.CircleCollider2DComponentSerializer());
        controller = new PlayModeController(editorState);
        editorState.setPlayModeController(controller);
    }

    @Test
    void shouldInitializeInStoppedState() {
        assertEquals(PlayModeState.STOPPED, controller.getState());
        assertTrue(controller.isStopped());
        assertFalse(controller.isPlaying());
        assertFalse(controller.isPaused());
        assertNull(controller.getAuthoredScene());
        assertNull(controller.getRuntimeScene());
    }

    @Test
    void shouldStartPlayModeAndIsolateAuthoredScene() {
        Scene authored = editorState.getActiveScene();
        Entity entity = authored.createEntity("Hero");
        entity.getTransform().setLocalPosition(100f, 200f, 0f);
        entity.addComponent(new ShapeRendererComponent(50f, 50f, Color.RED));
        editorState.setSelectedEntity(entity);

        boolean started = controller.play();
        assertTrue(started);
        assertEquals(PlayModeState.PLAYING, controller.getState());
        assertTrue(controller.isPlaying());
        assertFalse(controller.isStopped());

        // Editor active scene should now be the runtime scene
        Scene runtime = editorState.getActiveScene();
        assertNotSame(authored, runtime);
        assertEquals(authored, controller.getAuthoredScene());
        assertEquals(runtime, controller.getRuntimeScene());

        // Runtime entity should exist with same UUID but be a different object
        Entity runtimeEntity = runtime.findEntityById(entity.getId());
        assertNotNull(runtimeEntity);
        assertNotSame(entity, runtimeEntity);
        assertEquals(runtimeEntity, editorState.getSelectedEntity());

        // Mutate runtime entity position and hierarchy
        runtimeEntity.getTransform().setLocalPosition(999f, 999f, 0f);
        Entity runtimeBullet = runtime.createEntity("Bullet");
        runtimeBullet.getTransform().setParent(runtimeEntity.getTransform());

        // Verify authored entity remains completely untouched
        assertEquals(100f, entity.getTransform().getLocalPosition().x);
        assertEquals(200f, entity.getTransform().getLocalPosition().y);
        assertNull(authored.findEntityByName("Bullet"));
        assertEquals(0, entity.getTransform().getChildren().size());

        // Stop simulation
        controller.stop();
        assertEquals(PlayModeState.STOPPED, controller.getState());
        assertSame(authored, editorState.getActiveScene());
        assertEquals(entity, editorState.getSelectedEntity());
        assertEquals(100f, entity.getTransform().getLocalPosition().x);
        assertEquals(200f, entity.getTransform().getLocalPosition().y);
        assertNull(editorState.getActiveScene().findEntityByName("Bullet"));
    }

    @Test
    void shouldSupportPauseAndResume() {
        Scene authored = editorState.getActiveScene();
        Entity entity = authored.createEntity("Spinner");
        entity.addComponent(new ShapeRendererComponent(40f, 40f, Color.BLUE));

        controller.play();
        assertEquals(PlayModeState.PLAYING, controller.getState());

        // Pause
        assertTrue(controller.pause());
        assertEquals(PlayModeState.PAUSED, controller.getState());
        assertTrue(controller.isPaused());

        // Paused updates do not throw and do not advance simulation
        assertDoesNotThrow(() -> controller.update(0.016f));

        // Resume
        assertTrue(controller.resume());
        assertEquals(PlayModeState.PLAYING, controller.getState());
        assertTrue(controller.isPlaying());

        controller.stop();
        assertTrue(controller.isStopped());
    }

    @Test
    void shouldHandleRepeatedStopSafely() {
        controller.play();
        assertFalse(controller.isStopped());

        controller.stop();
        assertTrue(controller.isStopped());

        // Second stop should be a no-op
        assertDoesNotThrow(() -> controller.stop());
        assertTrue(controller.isStopped());
    }

    @Test
    void shouldRejectInvalidStateTransitions() {
        // Cannot pause or resume while stopped
        assertFalse(controller.pause());
        assertFalse(controller.resume());

        controller.play();
        // Cannot play while already playing
        assertFalse(controller.play());

        controller.pause();
        // Cannot pause while already paused
        assertFalse(controller.pause());
        // Cannot play directly while paused (resume should be used)
        assertTrue(controller.resume());

        controller.stop();
    }

    @Test
    void shouldRejectPlayIfSceneHasUnsupportedComponent() {
        Scene authored = editorState.getActiveScene();
        Entity entity = authored.createEntity("CustomObj");
        // Attach unregistered component
        entity.addComponent(new BaseComponent() {});

        boolean started = controller.play();
        assertFalse(started);
        assertTrue(controller.isStopped());
        assertTrue(editorState.isStatusError());
        assertTrue(editorState.getStatusMessage().contains("unsupported components"));
    }

    @Test
    void shouldBlockEditActionsDuringPlayMode() {
        controller.play();

        // Entity creation blocked
        Entity e = editorState.createEntity("Forbidden");
        assertNull(e);

        // Entity deletion blocked
        assertFalse(editorState.deleteSelectedEntity());

        // New scene blocked
        editorState.newScene("ForbiddenScene");
        assertNotEquals("ForbiddenScene", editorState.getActiveScene().getName());

        // Save scene blocked
        assertThrows(IllegalStateException.class, () -> editorState.saveSceneToFile(new File("dummy.json")));

        // Load scene blocked
        assertThrows(IllegalStateException.class, () -> editorState.loadSceneFromFile(new File("dummy.json")));

        controller.stop();
    }

    @Test
    void shouldReconcileSelectionWhenRuntimeEntityDeletedAndRestored() {
        Scene authored = editorState.getActiveScene();
        Entity hero = authored.createEntity("Hero");
        hero.addComponent(new ShapeRendererComponent(20f, 20f, Color.YELLOW));
        editorState.setSelectedEntity(hero);

        controller.play();
        Scene runtime = editorState.getActiveScene();
        Entity runtimeHero = runtime.findEntityById(hero.getId());
        assertNotNull(runtimeHero);
        assertEquals(runtimeHero, editorState.getSelectedEntity());

        // Delete runtime entity during simulation
        runtime.removeEntity(runtimeHero, true);
        editorState.notifyHierarchyModified();
        assertNull(editorState.getSelectedEntity());

        // Stop simulation
        controller.stop();

        // Authored entity was never deleted, so selection restores back to authored hero
        assertSame(authored, editorState.getActiveScene());
        assertEquals(hero, editorState.getSelectedEntity());
        assertNotNull(authored.findEntityById(hero.getId()));
    }

    @Test
    void shouldNotifyListenersOnPlayModeStateChanged() {
        List<PlayModeState> transitions = new ArrayList<>();
        controller.addListener((oldState, newState) -> transitions.add(newState));

        controller.play();
        controller.pause();
        controller.resume();
        controller.stop();

        assertEquals(4, transitions.size());
        assertEquals(PlayModeState.PLAYING, transitions.get(0));
        assertEquals(PlayModeState.PAUSED, transitions.get(1));
        assertEquals(PlayModeState.PLAYING, transitions.get(2));
        assertEquals(PlayModeState.STOPPED, transitions.get(3));
    }

    @Test
    void shouldSanitizeAndClampDeltaTimesDuringUpdate() {
        Scene authored = editorState.getActiveScene();
        Entity entity = authored.createEntity("Mover");
        entity.addComponent(new ShapeRendererComponent(30f, 30f, Color.GREEN));

        controller.play();

        // Negative delta, NaN, and Infinity should be safely sanitized to 0
        assertDoesNotThrow(() -> controller.update(-1.0f));
        assertDoesNotThrow(() -> controller.update(Float.NaN));
        assertDoesNotThrow(() -> controller.update(Float.POSITIVE_INFINITY));

        // Huge delta should be clamped to 0.1f without exploding
        assertDoesNotThrow(() -> controller.update(100.0f));

        controller.stop();
    }

    @Test
    void shouldSimulatePhysicsDuringPlayModeAndRestoreAuthoredSceneOnStop() {
        Scene authored = editorState.getActiveScene();
        Entity ground = authored.createEntity("Ground");
        ground.getTransform().setLocalPosition(0.0f, 0.0f, 0.0f);
        ground.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent(
            io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent.BodyType.STATIC
        ));
        ground.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent(500.0f, 20.0f));

        Entity falling = authored.createEntity("Falling");
        falling.getTransform().setLocalPosition(0.0f, 200.0f, 0.0f);
        falling.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent(
            io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent.BodyType.DYNAMIC
        ));
        falling.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent(15.0f));

        // Start play mode
        assertTrue(controller.play());
        assertNotNull(controller.getPhysicsSystem());
        assertEquals(2, controller.getPhysicsSystem().getBodyCount());

        // Update simulation for 20 frames
        for (int i = 0; i < 20; i++) {
            controller.update(1.0f / 60.0f);
        }

        // Runtime entity should have moved downwards
        Entity runtimeFalling = editorState.getActiveScene().findEntityById(falling.getId());
        assertNotNull(runtimeFalling);
        assertTrue(runtimeFalling.getTransform().getLocalPosition().y < 200.0f);

        // Stop simulation
        controller.stop();
        assertNull(controller.getPhysicsSystem());

        // Authored scene must be restored exactly at 200.0f
        Entity restoredFalling = editorState.getActiveScene().findEntityById(falling.getId());
        assertNotNull(restoredFalling);
        assertEquals(200.0f, restoredFalling.getTransform().getLocalPosition().y, 0.001f);
    }

    @Test
    void shouldHandleRepeatedPlayStopCyclesWithoutMemoryOrWorldLeaks() {
        Scene authored = editorState.getActiveScene();
        Entity box = authored.createEntity("CycledBox");
        box.getTransform().setLocalPosition(0.0f, 100.0f, 0.0f);
        box.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent(
            io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent.BodyType.DYNAMIC
        ));
        box.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent(30f, 30f));

        for (int cycle = 0; cycle < 5; cycle++) {
            assertTrue(controller.play(), "Play start should succeed on cycle " + cycle);
            assertTrue(controller.isPlaying());
            assertNotNull(controller.getPhysicsSystem());

            for (int f = 0; f < 5; f++) {
                controller.update(1.0f / 60.0f);
            }

            controller.stop();
            assertTrue(controller.isStopped());
            assertNull(controller.getPhysicsSystem());
            assertEquals(100.0f, authored.findEntityById(box.getId()).getTransform().getLocalPosition().y, 0.001f);
        }
    }

    @Test
    void shouldProperlyNotifyListenersOnStateTransitions() {
        AtomicInteger transitions = new AtomicInteger(0);
        PlayModeController.Listener listener = (oldState, newState) -> transitions.incrementAndGet();
        controller.addListener(listener);

        controller.play(); // 1
        controller.pause(); // 2
        controller.resume(); // 3
        controller.stop(); // 4

        assertEquals(4, transitions.get(), "Should notify listener on each transition");

        controller.removeListener(listener);
        controller.play();
        controller.stop();
        assertEquals(4, transitions.get(), "Removed listener should receive no further notifications");
    }
}

