package io.github.ldev22live.gdxunity.engine.scene;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EntityAndComponentTest {

    static class MockComponent extends BaseComponent {
        final AtomicInteger startCalls = new AtomicInteger(0);
        final AtomicInteger updateCalls = new AtomicInteger(0);
        final AtomicInteger enableCalls = new AtomicInteger(0);
        final AtomicInteger disableCalls = new AtomicInteger(0);
        final AtomicInteger detachCalls = new AtomicInteger(0);

        @Override
        public void onStart() {
            super.onStart();
            startCalls.incrementAndGet();
        }

        @Override
        public void onUpdate(float deltaTime) {
            super.onUpdate(deltaTime);
            updateCalls.incrementAndGet();
        }

        @Override
        public void onEnable() {
            super.onEnable();
            enableCalls.incrementAndGet();
        }

        @Override
        public void onDisable() {
            super.onDisable();
            disableCalls.incrementAndGet();
        }

        @Override
        public void onDetach() {
            super.onDetach();
            detachCalls.incrementAndGet();
        }
    }

    @Test
    void shouldHaveStableIdAndDefaultTransform() {
        UUID id = UUID.randomUUID();
        Entity entity = new Entity(id, "Player");

        assertEquals(id, entity.getId());
        assertEquals("Player", entity.getName());
        assertNotNull(entity.getTransform());
        assertEquals(entity, entity.getTransform().getEntity());
        assertTrue(entity.isActiveSelf());
        assertTrue(entity.isActiveInHierarchy());
    }

    @Test
    void shouldAttachLookupAndRemoveComponents() {
        Entity entity = new Entity("Hero");
        MockComponent comp = new MockComponent();

        entity.addComponent(comp);
        assertEquals(entity, comp.getEntity());
        assertEquals(comp, entity.getComponent(MockComponent.class));
        assertEquals(1, entity.getComponents(MockComponent.class).size());
        assertEquals(1, comp.enableCalls.get()); // Called on attach when entity is active

        assertTrue(entity.removeComponent(comp));
        assertNull(entity.getComponent(MockComponent.class));
        assertNull(comp.getEntity());
        assertEquals(1, comp.disableCalls.get());
        assertEquals(1, comp.detachCalls.get());
    }

    @Test
    void shouldRejectAttachingSameComponentToMultipleEntities() {
        Entity e1 = new Entity("E1");
        Entity e2 = new Entity("E2");
        MockComponent comp = new MockComponent();

        e1.addComponent(comp);
        assertThrows(IllegalStateException.class, () -> e2.addComponent(comp));
    }

    @Test
    void shouldPropagateActiveInHierarchyThroughParentEntities() {
        Entity parent = new Entity("Parent");
        Entity child = new Entity("Child");
        child.getTransform().setParent(parent.getTransform());

        MockComponent comp = new MockComponent();
        child.addComponent(comp);

        assertTrue(child.isActiveInHierarchy());
        assertEquals(1, comp.enableCalls.get());

        // Deactivating parent deactivates child in hierarchy
        parent.setActive(false);
        assertFalse(child.isActiveInHierarchy());
        assertEquals(1, comp.disableCalls.get());

        // Reactivating parent activates child
        parent.setActive(true);
        assertTrue(child.isActiveInHierarchy());
        assertEquals(2, comp.enableCalls.get());
    }

    @Test
    void shouldExecuteStartBeforeFirstUpdate() {
        Entity entity = new Entity("Unit");
        MockComponent comp = new MockComponent();
        entity.addComponent(comp);

        assertEquals(0, comp.startCalls.get());
        assertEquals(0, comp.updateCalls.get());

        entity.update(0.016f);
        assertEquals(1, comp.startCalls.get());
        assertEquals(1, comp.updateCalls.get());

        entity.update(0.016f);
        assertEquals(1, comp.startCalls.get());
        assertEquals(2, comp.updateCalls.get());
    }
}
