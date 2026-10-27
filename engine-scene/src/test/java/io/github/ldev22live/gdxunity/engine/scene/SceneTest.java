package io.github.ldev22live.gdxunity.engine.scene;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SceneTest {

    @Test
    void shouldRegisterAndLookupEntities() {
        Scene scene = new Scene("Level1");
        Entity e1 = scene.createEntity("Player");
        Entity e2 = scene.createEntity("Enemy");

        assertEquals(2, scene.getEntityCount());
        assertEquals(e1, scene.findEntityById(e1.getId()));
        assertEquals(e2, scene.findEntityByName("Enemy"));
        assertEquals(scene, e1.getScene());
    }

    @Test
    void shouldRejectDuplicateEntityIds() {
        Scene scene = new Scene("TestScene");
        UUID id = UUID.randomUUID();
        Entity e1 = new Entity(id, "First");
        Entity e2 = new Entity(id, "Second");

        scene.addEntity(e1);
        assertThrows(IllegalArgumentException.class, () -> scene.addEntity(e2));
    }

    @Test
    void shouldMaintainRootEntitiesCorrectly() {
        Scene scene = new Scene("HierarchyScene");
        Entity parent = scene.createEntity("Parent");
        Entity child = scene.createEntity("Child");

        assertEquals(2, scene.getRootEntities().size());

        // Make child child of parent
        child.getTransform().setParent(parent.getTransform());
        // Parent should have child
        assertEquals(1, parent.getTransform().getChildCount());
    }

    @Test
    void shouldHandleConcurrentEntityAdditionAndRemovalDuringUpdate() {
        Scene scene = new Scene("DynamicScene");
        Entity spawner = scene.createEntity("Spawner");

        AtomicInteger updates = new AtomicInteger(0);

        spawner.addComponent(new BaseComponent() {
            @Override
            public void onUpdate(float deltaTime) {
                updates.incrementAndGet();
                if (updates.get() == 1) {
                    // Create new entity during update loop
                    Entity spawned = scene.createEntity("SpawnedItem");
                    // Remove current entity during update loop
                    scene.removeEntity(spawner);
                }
            }
        });

        // First update: should not throw ConcurrentModificationException
        assertDoesNotThrow(() -> scene.update(0.016f));
        assertEquals(1, updates.get());

        // Pending addition & removal flushed
        assertNull(scene.findEntityById(spawner.getId()));
        assertNotNull(scene.findEntityByName("SpawnedItem"));
    }

    @Test
    void shouldSupportSceneManagerSwitchingAndDisposal() {
        SceneManager manager = new SceneManager();
        Scene s1 = new Scene("Scene1");
        Scene s2 = new Scene("Scene2");

        manager.setScene(s1);
        assertEquals(s1, manager.getActiveScene());
        assertTrue(s1.isActive());

        // Switch to s2; s1 disposed
        manager.setScene(s2, true);
        assertEquals(s2, manager.getActiveScene());
        assertTrue(s2.isActive());
        assertTrue(s1.isDisposed());

        // Dispose manager
        manager.dispose();
        assertNull(manager.getActiveScene());
        assertTrue(s2.isDisposed());
    }
}
