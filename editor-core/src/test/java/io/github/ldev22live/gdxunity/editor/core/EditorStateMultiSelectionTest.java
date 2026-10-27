package io.github.ldev22live.gdxunity.editor.core;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorStateMultiSelectionTest {

    @Test
    void shouldMaintainDeterministicMultiSelectionOrder() {
        EditorState state = new EditorState();
        Entity e1 = state.createEntity("E1");
        Entity e2 = state.createEntity("E2");
        Entity e3 = state.createEntity("E3");

        state.clearSelection();
        assertTrue(state.getSelectedEntityIds().isEmpty());
        assertNull(state.getSelectedEntityId());
        assertNull(state.getSelectedEntity());

        // Add sequentially
        assertTrue(state.addSelectedEntityId(e1.getId()));
        assertTrue(state.addSelectedEntityId(e2.getId()));
        assertTrue(state.addSelectedEntityId(e3.getId()));

        // Check primary selection (defaults to first added)
        assertEquals(e1.getId(), state.getSelectedEntityId());
        assertEquals(e1, state.getSelectedEntity());

        // Verify order in set
        List<UUID> list = new ArrayList<>(state.getSelectedEntityIds());
        assertEquals(3, list.size());
        assertEquals(e1.getId(), list.get(0));
        assertEquals(e2.getId(), list.get(1));
        assertEquals(e3.getId(), list.get(2));

        // Adding duplicate should return false and not reorder
        assertFalse(state.addSelectedEntityId(e2.getId()));
        list = new ArrayList<>(state.getSelectedEntityIds());
        assertEquals(e2.getId(), list.get(1));
    }

    @Test
    void shouldHandleRemoveFromMultiSelectionAndFallbackPrimary() {
        EditorState state = new EditorState();
        Entity e1 = state.createEntity("E1");
        Entity e2 = state.createEntity("E2");
        Entity e3 = state.createEntity("E3");

        state.setSelectedEntityIds(Arrays.asList(e1.getId(), e2.getId(), e3.getId()), e1.getId());
        assertEquals(e1.getId(), state.getSelectedEntityId());

        // Remove primary e1 -> primary should fall back to next in set (e2)
        assertTrue(state.removeSelectedEntityId(e1.getId()));
        assertEquals(2, state.getSelectedEntityIds().size());
        assertEquals(e2.getId(), state.getSelectedEntityId());
        assertFalse(state.isSelected(e1.getId()));
        assertTrue(state.isSelected(e2.getId()));

        // Removing non-existent should return false
        assertFalse(state.removeSelectedEntityId(e1.getId()));
    }

    @Test
    void shouldNotifyMultiSelectionListeners() {
        EditorState state = new EditorState();
        Entity e1 = state.createEntity("E1");
        Entity e2 = state.createEntity("E2");

        AtomicInteger legacyEvents = new AtomicInteger(0);
        AtomicInteger multiEvents = new AtomicInteger(0);

        state.addListener(new EditorState.Listener() {
            @Override
            public void onSelectionChanged(UUID previousSelectedId, UUID newSelectedId) {
                legacyEvents.incrementAndGet();
            }

            @Override
            public void onMultiSelectionChanged(Set<UUID> previousSelection, Set<UUID> newSelection, UUID primarySelectedId) {
                multiEvents.incrementAndGet();
            }
        });

        state.setSelectedEntity(e1);
        assertEquals(1, legacyEvents.get());
        assertEquals(1, multiEvents.get());

        // Adding e2 without changing primary: legacy event does not fire (primary unchanged), multi event fires!
        state.addSelectedEntityId(e2.getId());
        assertEquals(1, legacyEvents.get());
        assertEquals(2, multiEvents.get());

        // Clear selection: both events fire
        state.clearSelection();
        assertEquals(2, legacyEvents.get());
        assertEquals(3, multiEvents.get());
    }

    @Test
    void shouldIgnoreNullOrForeignEntityIds() {
        EditorState state = new EditorState();
        Scene foreignScene = new Scene("Foreign");
        Entity foreign = foreignScene.createEntity("ForeignEntity");

        assertFalse(state.addSelectedEntityId(null));
        assertFalse(state.addSelectedEntityId(foreign.getId()));
        assertFalse(state.isSelected(null));
        assertFalse(state.isSelected(foreign.getId()));

        state.setSelectedEntityIds(Collections.singleton(foreign.getId()), foreign.getId());
        assertTrue(state.getSelectedEntityIds().isEmpty());
        assertNull(state.getSelectedEntityId());
    }

    @Test
    void shouldReconcileSelectionWhenEntityIsDeletedFromScene() {
        EditorState state = new EditorState();
        Entity e1 = state.createEntity("E1");
        Entity e2 = state.createEntity("E2");

        state.setSelectedEntityIds(Arrays.asList(e1.getId(), e2.getId()), e1.getId());
        assertEquals(2, state.getSelectedEntityIds().size());

        // Remove e1 in scene directly, then notifyHierarchyModified
        state.getActiveScene().removeEntity(e1);
        state.notifyHierarchyModified();

        assertEquals(1, state.getSelectedEntityIds().size());
        assertTrue(state.isSelected(e2.getId()));
        assertEquals(e2.getId(), state.getSelectedEntityId());
    }

    @Test
    void shouldReturnImmutableCopyFromGetSelectedEntityIds() {
        EditorState state = new EditorState();
        Entity e1 = state.createEntity("E1");
        state.setSelectedEntity(e1);

        Set<UUID> selection = state.getSelectedEntityIds();
        assertThrows(UnsupportedOperationException.class, () -> selection.add(UUID.randomUUID()));
    }
}
