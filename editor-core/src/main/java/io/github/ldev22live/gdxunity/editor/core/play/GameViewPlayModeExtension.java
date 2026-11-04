package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.List;
import java.util.Objects;

/**
 * Shows a game through its own camera (TD-034): on Play, a scene with a {@link CameraComponent} switches the scene
 * view to 2D for an orthographic camera or 3D for a perspective one; on Stop the previous view mode comes back.
 * Scenes without a camera keep the editor view, as before.
 */
public final class GameViewPlayModeExtension implements PlayModeController.Extension {
    private final EditorState editorState;
    /** View mode before Play switched it; null when Play did not switch. */
    private ViewportMode modeBeforePlay;

    public GameViewPlayModeExtension(EditorState editorState) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
    }

    @Override
    public List<String> onPlay(Scene runtimeScene, PlayModeController controller) {
        CameraComponent camera = GameCameraRig.findActiveCamera(runtimeScene);
        if (camera != null) {
            modeBeforePlay = editorState.getViewportMode();
            editorState.setViewportMode(camera.getProjection() == CameraComponent.Projection.PERSPECTIVE
                ? ViewportMode.VIEW_3D : ViewportMode.VIEW_2D);
        }
        return List.of();
    }

    @Override
    public void onStop(Scene runtimeScene) {
        if (modeBeforePlay != null) {
            editorState.setViewportMode(modeBeforePlay);
            modeBeforePlay = null;
        }
    }
}
