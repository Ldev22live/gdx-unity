package io.github.ldev22live.gdxunity.editor.core.workspace;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Standard implementation of WorkspaceManager.
 * Manages deterministic workspace registry, safe lifecycle transitions, and error recovery.
 */
public class DefaultWorkspaceManager implements WorkspaceManager {
    private final WorkspaceContext context;
    private final Map<String, StudioWorkspace> workspaces = new LinkedHashMap<>();
    private final List<WorkspaceListener> listeners = new CopyOnWriteArrayList<>();
    private StudioWorkspace activeWorkspace;

    public DefaultWorkspaceManager(WorkspaceContext context) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
    }

    @Override
    public synchronized void registerWorkspace(StudioWorkspace workspace) {
        Objects.requireNonNull(workspace, "workspace cannot be null");
        String id = Objects.requireNonNull(workspace.getId(), "workspace id cannot be null");
        if (workspaces.containsKey(id)) {
            throw new IllegalArgumentException("Workspace with id '" + id + "' is already registered");
        }
        try {
            workspace.initialize(context);
        } catch (Exception e) {
            context.getEditorState().setStatusMessage("Failed to initialize workspace " + id + ": " + e.getMessage(), true);
            throw new RuntimeException("Failed to initialize workspace: " + id, e);
        }
        workspaces.put(id, workspace);
        if (activeWorkspace == null) {
            setActiveWorkspace(id);
        }
    }

    @Override
    public synchronized void unregisterWorkspace(String workspaceId) {
        if (workspaceId == null) return;
        StudioWorkspace ws = workspaces.remove(workspaceId);
        if (ws != null) {
            if (activeWorkspace == ws) {
                try {
                    ws.onDeactivated();
                } catch (Exception ignored) {}
                activeWorkspace = null;
                // Switch to next available workspace
                if (!workspaces.isEmpty()) {
                    setActiveWorkspace(workspaces.keySet().iterator().next());
                }
            }
            try {
                ws.dispose();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized Collection<StudioWorkspace> getRegisteredWorkspaces() {
        return Collections.unmodifiableCollection(new ArrayList<>(workspaces.values()));
    }

    @Override
    public synchronized Optional<StudioWorkspace> getWorkspace(String workspaceId) {
        if (workspaceId == null) return Optional.empty();
        return Optional.ofNullable(workspaces.get(workspaceId));
    }

    @Override
    public synchronized StudioWorkspace getActiveWorkspace() {
        return activeWorkspace;
    }

    @Override
    public synchronized boolean setActiveWorkspace(String workspaceId) {
        if (workspaceId == null) return false;
        StudioWorkspace upcoming = workspaces.get(workspaceId);
        if (upcoming == null) {
            context.getEditorState().setStatusMessage("Cannot switch to unknown workspace: " + workspaceId, true);
            return false;
        }
        if (activeWorkspace == upcoming) {
            return true;
        }

        StudioWorkspace previous = activeWorkspace;

        for (WorkspaceListener l : listeners) {
            try {
                l.onWorkspaceChanging(previous, upcoming);
            } catch (Exception e) {
                // Prevent listener errors from aborting transition
            }
        }

        if (previous != null) {
            try {
                previous.onDeactivated();
            } catch (Exception e) {
                context.getEditorState().setStatusMessage("Error during " + previous.getId() + " deactivation: " + e.getMessage(), true);
            }
        }

        activeWorkspace = upcoming;

        try {
            activeWorkspace.onActivated();
        } catch (Exception e) {
            context.getEditorState().setStatusMessage("Error during " + activeWorkspace.getId() + " activation: " + e.getMessage(), true);
        }

        for (WorkspaceListener l : listeners) {
            try {
                l.onWorkspaceChanged(previous, activeWorkspace);
            } catch (Exception e) {
                // Prevent listener errors from propagating
            }
        }

        context.getEditorState().setStatusMessage("Workspace: " + activeWorkspace.getDisplayName(), false);
        return true;
    }

    @Override
    public void addListener(WorkspaceListener listener) {
        if (listener != null) listeners.add(listener);
    }

    @Override
    public void removeListener(WorkspaceListener listener) {
        if (listener != null) listeners.remove(listener);
    }

    @Override
    public synchronized void update(float deltaTime) {
        if (activeWorkspace != null) {
            try {
                activeWorkspace.update(deltaTime);
            } catch (Exception e) {
                context.getEditorState().setStatusMessage("Error in workspace update: " + e.getMessage(), true);
            }
        }
    }

    @Override
    public synchronized void render(float deltaTime) {
        if (activeWorkspace != null) {
            try {
                activeWorkspace.render(deltaTime);
            } catch (Exception e) {
                context.getEditorState().setStatusMessage("Error in workspace render: " + e.getMessage(), true);
            }
        }
    }

    @Override
    public synchronized void resize(int width, int height) {
        if (activeWorkspace != null) {
            try {
                activeWorkspace.resize(width, height);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized void dispose() {
        if (activeWorkspace != null) {
            try {
                activeWorkspace.onDeactivated();
            } catch (Exception ignored) {}
            activeWorkspace = null;
        }
        for (StudioWorkspace ws : workspaces.values()) {
            try {
                ws.dispose();
            } catch (Exception ignored) {}
        }
        workspaces.clear();
        listeners.clear();
    }
}
