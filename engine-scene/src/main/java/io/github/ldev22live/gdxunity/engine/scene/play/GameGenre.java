package io.github.ldev22live.gdxunity.engine.scene.play;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A kind of game a scene can be played as (TD-088): Platformer, Action RPG and the others in {@link GameGenres}. A genre is only a
 * name and a sentence; what Play supplies for it is a {@link GameTestProfile}, and only for the {@link GenreSupport} pairs marked
 * playable.
 *
 * @param id          the value saved in the scene file's {@code gameGenre} field: lower case words joined by hyphens
 * @param label       the name shown in the Game Genre selector
 * @param description one sentence for the selector's tooltip
 */
public record GameGenre(String id, String label, String description) {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public GameGenre {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(label, "label cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("A genre id is lower case words joined by hyphens, not '" + id + "'");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("Genre '" + id + "' needs a label");
        }
    }
}
