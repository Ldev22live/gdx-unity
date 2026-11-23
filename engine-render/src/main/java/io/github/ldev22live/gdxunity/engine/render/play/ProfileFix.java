package io.github.ldev22live.gdxunity.engine.render.play;

import java.util.Objects;
import java.util.UUID;

/**
 * A safe fix a {@link ProfileProblem} offers (TD-088): the editor shows it as a button and runs it as one undoable command on the
 * authored scene. Play never applies a fix by itself.
 *
 * @param kind     what the fix does
 * @param entityId the entity it changes; null for {@link Kind#ADD_CAMERA}, which creates a new entity
 * @param label    the button's text, such as "Make Hero the player"
 */
public record ProfileFix(Kind kind, UUID entityId, String label) {
    /** What a fix does. */
    public enum Kind {
        /** Adds a Player component to the entity. */
        MAKE_PLAYER,
        /** Adds a Character Controller 3D with its defaults to the entity. */
        ADD_CHARACTER_CONTROLLER_3D,
        /** Creates an entity named "Main Camera" with a perspective Camera. */
        ADD_CAMERA
    }

    public ProfileFix {
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(label, "label cannot be null");
        if ((kind == Kind.ADD_CAMERA) != (entityId == null)) {
            throw new IllegalArgumentException(kind + " " + (entityId == null ? "needs" : "takes no") + " entity");
        }
    }
}
