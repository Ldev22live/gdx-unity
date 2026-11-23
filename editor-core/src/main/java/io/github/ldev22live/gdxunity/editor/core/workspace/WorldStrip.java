package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The World strip under the scene view (roadmap Appendix E1, milestone M1b.2): a small row of what can be added to the World, which
 * opens only the palette for that choice. A 2D scene gets Tiles, Sprites, Objects and Characters; a 2.5D or 3D scene gets Terrain,
 * Objects, Buildings and Characters. A section with nothing behind it yet is still shown, and says why instead of doing nothing
 * (Appendix E1: unsupported actions labelled, never faked).
 */
public final class WorldStrip {
    /** One entry of the strip. */
    public enum Section {
        TERRAIN("Terrain"), OBJECTS("Objects"), BUILDINGS("Buildings"), CHARACTERS("Characters"), TILES("Tiles"), SPRITES("Sprites");

        private final String label;

        Section(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private WorldStrip() { }

    /** The strip's entries for a scene of {@code mode}, left to right. */
    public static List<Section> sectionsFor(GameMode mode) {
        Objects.requireNonNull(mode, "mode cannot be null");
        return mode == GameMode.MODE_2D
            ? List.of(Section.TILES, Section.SPRITES, Section.OBJECTS, Section.CHARACTERS)
            : List.of(Section.TERRAIN, Section.OBJECTS, Section.BUILDINGS, Section.CHARACTERS);
    }

    /**
     * Why {@code section} does nothing yet in a scene of {@code mode}, or empty when it works.
     *
     * @throws IllegalArgumentException when the strip does not show {@code section} for {@code mode}
     */
    public static Optional<String> unavailableReason(Section section, GameMode mode) {
        Objects.requireNonNull(section, "section cannot be null");
        if (!sectionsFor(mode).contains(section)) {
            throw new IllegalArgumentException(section + " is not in the World strip of a " + mode.getLabel() + " scene");
        }
        return switch (section) {
            // Placing buildings works (UI M3); reshaping one needs mesh editing (roadmap R7a), which Customize says
            case BUILDINGS -> Optional.empty();
            // 2.5D and 3D place the player and its spawn point (roadmap M3c.2); 2D has no character controller yet
            case CHARACTERS -> mode == GameMode.MODE_2D
                ? Optional.of("Characters for 2D scenes are planned: they need a 2D character controller. Drag a sprite from the "
                    + "Content Browser into the World for now.")
                : Optional.empty();
            case OBJECTS -> mode == GameMode.MODE_2D
                ? Optional.of("Objects for 2D scenes are planned. Drag a sprite or a prefab from the Content Browser into the World "
                    + "for now.")
                : Optional.empty();
            case TERRAIN, TILES, SPRITES -> Optional.empty();
        };
    }
}
