package io.github.ldev22live.gdxunity.editor.core.workspace;

import java.util.Collection;
import java.util.Optional;

/**
 * Coordinates workspace registration, navigation, lifecycle transitions, and active workspace state.
 */
public interface WorkspaceManager {
    void registerWorkspace(StudioWorkspace workspace);
    void unregisterWorkspace(String workspaceId);

    Collection<StudioWorkspace> getRegisteredWorkspaces();
    Optional<StudioWorkspace> getWorkspace(String workspaceId);

    StudioWorkspace getActiveWorkspace();
    boolean setActiveWorkspace(String workspaceId);

    void addListener(WorkspaceListener listener);
    void removeListener(WorkspaceListener listener);

    void update(float deltaTime);
    void render(float deltaTime);
    void resize(int width, int height);
    void dispose();

    interface WorkspaceListener {
        default void onWorkspaceChanging(StudioWorkspace current, StudioWorkspace upcoming) {}
        default void onWorkspaceChanged(StudioWorkspace previous, StudioWorkspace active) {}
    }
}
