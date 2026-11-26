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

import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
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
    /** A dynamic Rigid Body 2D that does not turn: a 2D player stays upright (TD-105). */
    static RigidBody2DComponent uprightBody() {
        RigidBody2DComponent body = new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC);
        body.setFixedRotation(true);
        return body;
    }

    /**
     * A Box Collider 2D the size the entity's sprite is drawn at: its Width and Height, or for a sprite drawn at its pixel size (0)
     * the loaded sprite's canvas. The collider's default size when there is no sprite, or it is not loaded yet.
     */
    static BoxCollider2DComponent colliderFor(Entity entity) {
        SpriteAnimatorComponent sprite = entity.getComponent(SpriteAnimatorComponent.class);
        if (sprite != null && sprite.getWidth() > 0f && sprite.getHeight() > 0f) {
            return new BoxCollider2DComponent(sprite.getWidth(), sprite.getHeight());
        }
        if (sprite != null && sprite.getDocument() != null) {
            return new BoxCollider2DComponent(sprite.getDocument().getWidth(), sprite.getDocument().getHeight());
        }
        return new BoxCollider2DComponent();
    }

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
            case ADD_RIGID_BODY_2D -> editorState.executeCommand(new AddComponentCommand(editorState, fix.entityId(), uprightBody()));
            case ADD_BOX_COLLIDER_2D -> editorState.executeCommand(new AddComponentCommand(editorState, fix.entityId(),
                colliderFor(editorState.getActiveScene().findEntityById(fix.entityId()))));
            case ADD_CAMERA -> editorState.executeCommand(new CreateEntityCommand(editorState, CAMERA_NAME, null,
                List.<Component>of(new CameraComponent(editorState.getGameMode() == GameMode.MODE_2D
                    ? CameraComponent.Projection.ORTHOGRAPHIC : CameraComponent.Projection.PERSPECTIVE))));
        };
    }
}
