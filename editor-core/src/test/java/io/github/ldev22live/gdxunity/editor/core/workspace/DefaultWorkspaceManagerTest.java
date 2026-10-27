package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWorkspaceManagerTest {

    private EditorState editorState;
    private WorkspaceContext context;
    private DefaultWorkspaceManager manager;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        context = () -> editorState;
        manager = new DefaultWorkspaceManager(context);
    }

    private static class TestWorkspace implements StudioWorkspace {
        final String id;
        final String displayName;
        boolean initialized = false;
        boolean activated = false;
        boolean disposed = false;
        int updateCount = 0;
        int renderCount = 0;

        TestWorkspace(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @Override public String getId() { return id; }
        @Override public String getDisplayName() { return displayName; }
        @Override public void initialize(WorkspaceContext context) { initialized = true; }
        @Override public void onActivated() { activated = true; }
        @Override public void onDeactivated() { activated = false; }
        @Override public void update(float deltaTime) { updateCount++; }
        @Override public void render(float deltaTime) { renderCount++; }
        @Override public void dispose() { disposed = true; }
    }

    @Test
    void shouldRegisterAndAutomaticallyActivateFirstWorkspace() {
        TestWorkspace ws1 = new TestWorkspace("scene", "Scene Editor");
        manager.registerWorkspace(ws1);

        assertTrue(ws1.initialized);
        assertTrue(ws1.activated);
        assertEquals(ws1, manager.getActiveWorkspace());
        assertEquals(1, manager.getRegisteredWorkspaces().size());
    }

    @Test
    void shouldRejectDuplicateRegistration() {
        TestWorkspace ws1 = new TestWorkspace("scene", "Scene Editor");
        manager.registerWorkspace(ws1);

        TestWorkspace duplicate = new TestWorkspace("scene", "Duplicate");
        assertThrows(IllegalArgumentException.class, () -> manager.registerWorkspace(duplicate));
    }

    @Test
    void shouldSwitchWorkspaceAndTriggerLifecycleEvents() {
        TestWorkspace ws1 = new TestWorkspace("scene", "Scene Editor");
        TestWorkspace ws2 = new TestWorkspace("audio", "Audio Studio");
        manager.registerWorkspace(ws1);
        manager.registerWorkspace(ws2);

        assertEquals(ws1, manager.getActiveWorkspace());
        assertTrue(ws1.activated);
        assertFalse(ws2.activated);

        AtomicInteger changingEvents = new AtomicInteger(0);
        AtomicInteger changedEvents = new AtomicInteger(0);
        manager.addListener(new WorkspaceManager.WorkspaceListener() {
            @Override
            public void onWorkspaceChanging(StudioWorkspace current, StudioWorkspace upcoming) {
                changingEvents.incrementAndGet();
                assertEquals(ws1, current);
                assertEquals(ws2, upcoming);
            }

            @Override
            public void onWorkspaceChanged(StudioWorkspace previous, StudioWorkspace active) {
                changedEvents.incrementAndGet();
                assertEquals(ws1, previous);
                assertEquals(ws2, active);
            }
        });

        boolean switched = manager.setActiveWorkspace("audio");
        assertTrue(switched);
        assertEquals(ws2, manager.getActiveWorkspace());
        assertFalse(ws1.activated);
        assertTrue(ws2.activated);
        assertEquals(1, changingEvents.get());
        assertEquals(1, changedEvents.get());

        // Repeated activation of already active workspace should return true without re-firing
        boolean repeat = manager.setActiveWorkspace("audio");
        assertTrue(repeat);
        assertEquals(1, changedEvents.get());
    }

    @Test
    void shouldHandleInvalidWorkspaceIdGracefully() {
        TestWorkspace ws1 = new TestWorkspace("scene", "Scene Editor");
        manager.registerWorkspace(ws1);

        boolean switched = manager.setActiveWorkspace("nonexistent");
        assertFalse(switched);
        assertEquals(ws1, manager.getActiveWorkspace());
        assertTrue(editorState.isStatusError());
    }

    @Test
    void shouldUnregisterAndFallbackSafely() {
        TestWorkspace ws1 = new TestWorkspace("scene", "Scene Editor");
        TestWorkspace ws2 = new TestWorkspace("vector", "Vector Studio");
        manager.registerWorkspace(ws1);
        manager.registerWorkspace(ws2);

        manager.setActiveWorkspace("scene");
        manager.unregisterWorkspace("scene");

        assertTrue(ws1.disposed);
        assertFalse(ws1.activated);
        assertEquals(ws2, manager.getActiveWorkspace());
        assertTrue(ws2.activated);
    }

    @Test
    void shouldPropagateUpdateAndRenderToActiveWorkspaceOnly() {
        TestWorkspace ws1 = new TestWorkspace("scene", "Scene Editor");
        TestWorkspace ws2 = new TestWorkspace("audio", "Audio Studio");
        manager.registerWorkspace(ws1);
        manager.registerWorkspace(ws2);

        manager.update(0.016f);
        manager.render(0.016f);

        assertEquals(1, ws1.updateCount);
        assertEquals(1, ws1.renderCount);
        assertEquals(0, ws2.updateCount);
        assertEquals(0, ws2.renderCount);
    }

    @Test
    void shouldDisposeAllWorkspacesOnManagerDispose() {
        TestWorkspace ws1 = new TestWorkspace("scene", "Scene Editor");
        TestWorkspace ws2 = new TestWorkspace("audio", "Audio Studio");
        manager.registerWorkspace(ws1);
        manager.registerWorkspace(ws2);

        manager.dispose();

        assertTrue(ws1.disposed);
        assertTrue(ws2.disposed);
        assertNull(manager.getActiveWorkspace());
        assertTrue(manager.getRegisteredWorkspaces().isEmpty());
    }
}
