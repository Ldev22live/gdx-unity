package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenre;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.GenreStatus;
import io.github.ldev22live.gdxunity.engine.scene.play.GenreSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the footer's Game Genre selector offers and says (roadmap M3b.4, TD-088), headless so it can be tested. The choices come from
 * the genre registry: "None" first, then every registered genre, each marked with its status in the scene's game mode, so a pair
 * that is only planned is never shown as playable. Choosing a genre only sets the scene's genre (saved with it, not an undo step);
 * the message says plainly when Play will refuse it, because a genre label is not gameplay.
 */
public final class GenreChoices {
    /** The label of the "no genre" choice. */
    public static final String NONE_LABEL = "None";
    /** The status line for a scene without a genre. */
    public static final String NONE_STATUS = "Scripts only";

    private GenreChoices() {
    }

    /**
     * One entry of the selector.
     *
     * @param genreId null for "None"
     * @param label   the genre's label, or "None", or the unknown id in quotes
     * @param status  the pair's status in the scene's mode; null for "None" and for an unknown id
     * @param reason  the pair's reason in plain words; empty for "None"
     */
    public record Choice(String genreId, String label, GenreStatus status, String reason) {
        public Choice {
            Objects.requireNonNull(label, "label cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }

        /** What the selector shows: the label, and for anything that is not playable, why not in a word. */
        public String display() {
            if (genreId == null) {
                return label;
            }
            if (status == null) {
                return label + " (unknown)";
            }
            return switch (status) {
                case PLAYABLE -> label;
                case PLANNED -> label + " (planned)";
                case NOT_OFFERED -> label + " (not offered)";
            };
        }

        public boolean isPlayable() {
            return status == GenreStatus.PLAYABLE;
        }

        /** The selector draws an entry with {@link #display()}. */
        @Override
        public String toString() {
            return display();
        }
    }

    /**
     * The selector's entries for a scene in {@code mode} whose genre is {@code currentId}: "None", then the registered genres in
     * order. A current id the registry does not know is kept as an extra entry, so showing the scene never loses it (TD-088).
     */
    public static List<Choice> choices(GameGenres genres, String currentId, GameMode mode) {
        Objects.requireNonNull(genres, "genres cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice(null, NONE_LABEL, null, ""));
        for (GameGenre genre : genres.genres()) {
            GenreSupport pair = genres.support(genre.id(), mode);
            choices.add(new Choice(genre.id(), genre.label(), pair.status(), pair.reason()));
        }
        if (currentId != null && genres.find(currentId).isEmpty()) {
            choices.add(new Choice(currentId, "'" + currentId + "'", null, "This version of ForgeRoom does not know this genre."));
        }
        return choices;
    }

    /** The entry for {@code currentId} among {@code choices}; "None" for null. */
    public static Choice current(List<Choice> choices, String currentId) {
        for (Choice choice : Objects.requireNonNull(choices, "choices cannot be null")) {
            if (Objects.equals(choice.genreId(), currentId)) {
                return choice;
            }
        }
        throw new IllegalArgumentException("No choice for genre '" + currentId + "'");
    }

    /** The short line after Game Mode: whether Play can use this genre in {@code mode} (the long reason is in the status bar). */
    public static String statusLine(GameGenres genres, String genreId, GameMode mode) {
        Choice choice = describe(genres, genreId, mode);
        if (choice.genreId() == null) {
            return NONE_STATUS;
        }
        if (choice.status() == null) {
            return "Unknown: Play refuses it";
        }
        return switch (choice.status()) {
            case PLAYABLE -> "Playable";
            case PLANNED -> "Planned: Play refuses it";
            case NOT_OFFERED -> "Not offered: Play refuses it";
        };
    }

    /** The modes in which {@code genreId} plays; empty for none or an unknown id. */
    public static List<GameMode> playableModes(GameGenres genres, String genreId) {
        Objects.requireNonNull(genres, "genres cannot be null");
        List<GameMode> modes = new ArrayList<>();
        if (genreId == null || genres.find(genreId).isEmpty()) {
            return modes;
        }
        for (GameMode mode : GameMode.values()) {
            if (genres.support(genreId, mode).isPlayable()) {
                modes.add(mode);
            }
        }
        return modes;
    }

    /**
     * Sets the active scene's genre from the selector and says what that means for Play in the status bar: what the profile
     * supplies, or that the pair is only planned and Play will refuse it, with the reason.
     *
     * @return false when nothing changed or the change was refused (during Play, for instance)
     */
    public static boolean choose(EditorState editorState, GameGenres genres, String genreId) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(genres, "genres cannot be null");
        if (!editorState.setGameGenre(genreId)) {
            return false;
        }
        editorState.setStatusMessage(changeMessage(genres, genreId, editorState.getGameMode()), false);
        return true;
    }

    /** The status bar message after the genre became {@code genreId} in a scene in {@code mode}. */
    public static String changeMessage(GameGenres genres, String genreId, GameMode mode) {
        Choice choice = describe(genres, genreId, mode);
        if (choice.genreId() == null) {
            return "Game genre: none. Play runs the scene's own scripts, as before.";
        }
        String start = "Game genre: " + choice.label() + ". ";
        if (choice.isPlayable()) {
            return start + "In " + mode.getLabel() + " Play adds its camera and movement to the Player. " + choice.reason();
        }
        String pair = choice.label() + " + " + mode.getLabel();
        String where = playableModes(genres, genreId).stream().map(GameMode::getLabel).reduce((a, b) -> a + ", " + b)
            .map(list -> " It plays in " + list + ".").orElse("");
        return start + pair + " has no gameplay yet, and Play will refuse it. " + choice.reason() + where;
    }

    private static Choice describe(GameGenres genres, String genreId, GameMode mode) {
        Objects.requireNonNull(genres, "genres cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        if (genreId == null) {
            return new Choice(null, NONE_LABEL, null, "");
        }
        Optional<GameGenre> genre = genres.find(genreId);
        if (genre.isEmpty()) {
            return new Choice(genreId, "'" + genreId + "'", null, "This version of ForgeRoom does not know this genre.");
        }
        GenreSupport pair = genres.support(genreId, mode);
        return new Choice(genreId, genre.get().label(), pair.status(), pair.reason());
    }
}
