package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileFix;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;

import java.util.List;
import java.util.Objects;

/**
 * Runs a {@link ProfileFix} on the authored scene as one undoable command (TD-088). Only a person's click calls this: Play never
 * fixes a scene by itself.
 */
public final class ProfileFixes {
    /** The name of the entity {@link ProfileFix.Kind#ADD_CAMERA} creates. */
    public static final String CAMERA_NAME = "Main Camera";

    private ProfileFixes() { }

    /**
     * Applies {@code fix}.
     *
     * @return false when the editor refused the command (for example during Play, or the entity no longer exists)
     */
    public static boolean apply(EditorState editorState, ProfileFix fix) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(fix, "fix cannot be null");
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Stop the game first, then use \"" + fix.label() + "\"", true);
            return false;
        }
        if (fix.kind() != ProfileFix.Kind.ADD_CAMERA
            && (editorState.getActiveScene() == null || editorState.getActiveScene().findEntityById(fix.entityId()) == null)) {
            editorState.setStatusMessage("\"" + fix.label() + "\" cannot be done: the entity no longer exists", true);
            return false;
        }
        return switch (fix.kind()) {
            case MAKE_PLAYER -> editorState.executeCommand(new AddComponentCommand(editorState, fix.entityId(), new PlayerComponent()));
            case ADD_CHARACTER_CONTROLLER_3D -> editorState.executeCommand(
                new AddComponentCommand(editorState, fix.entityId(), new CharacterController3DComponent()));
            case ADD_CAMERA -> editorState.executeCommand(new CreateEntityCommand(editorState, CAMERA_NAME, null,
                List.<Component>of(new CameraComponent(editorState.getGameMode() == GameMode.MODE_2D
                    ? CameraComponent.Projection.ORTHOGRAPHIC : CameraComponent.Projection.PERSPECTIVE))));
        };
    }
}
