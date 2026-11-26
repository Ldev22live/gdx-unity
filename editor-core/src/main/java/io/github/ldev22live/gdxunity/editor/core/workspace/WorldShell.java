package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The World screen's shell after TD-107 (approved 2026-11-25; urGameForge Alpha A02), modelled on the owner's scenario-editor
 * reference ({@code docs/Screenshot 2026-11-25 113907.png}): a thin header; a dominant map; below it one category bar (Map, the
 * World strip's sections for the scene's mode, Players, Rules) and one palette row; a footer with Game Genre, Game Mode, Customize
 * and Play. Every other panel is hidden by default and reachable from Settings → Panels. Headless: the desktop shell draws it.
 */
public final class WorldShell {
    /** Which composition the World screen uses. */
    public enum Style {
        /** TD-107: the scenario-editor layout (the default). */
        WORLD,
        /** The UI M3b layout (tabs on top, tool rail, Properties panel), kept selectable until the owner signs off A02. */
        CLASSIC;

        /** The style named by {@code value} ({@code world} or {@code classic}, any case), or {@link #WORLD} for null or blank. */
        public static Style parse(String value) {
            if (value == null || value.isBlank()) {
                return WORLD;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "world" -> WORLD;
                case "classic" -> CLASSIC;
                default -> throw new IllegalArgumentException("Unknown layout style '" + value + "' (expected world or classic)");
            };
        }
    }

    /** A button of the category bar. */
    public record Category(String id, String label, WorldStrip.Section section, String notBuiltReason) {
        public Category {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(label, "label cannot be null");
        }

        /** The World strip section this category opens, if it is one. */
        public Optional<WorldStrip.Section> worldSection() {
            return Optional.ofNullable(section);
        }

        /** Why this category has nothing yet (shown instead of controls), or empty when it works. */
        public Optional<String> notBuilt() {
            return Optional.ofNullable(notBuiltReason);
        }
    }

    /** The Map category: the level itself (new level, size, lighting), the existing level actions. */
    public static final Category MAP = new Category("map", "Map", null, null);
    /** Player slots and ownership: their editor is roadmap A04 (the data exists since A12, TD-106). */
    public static final Category PLAYERS = new Category("players", "Players", null,
        "Player slots and object ownership come with milestone A04. Owned objects in this scene are kept as they are.");
    /** Triggers and objectives: their editor is roadmap A05. */
    public static final Category RULES = new Category("rules", "Rules", null,
        "Triggers and objectives come with milestone A05. Visual scripts (Customize, Logic) work today.");

    /** Header height, in UI units (the reference's thin header). */
    public static final float HEADER_HEIGHT = 36f;
    /** Category bar height. */
    public static final float CATEGORY_BAR_HEIGHT = 34f;
    /** The palette row's height while a category with a palette is open. */
    public static final float PALETTE_ROW_HEIGHT = 96f;
    /** Footer height (Game Genre, Game Mode, Customize, Play). */
    public static final float FOOTER_HEIGHT = 40f;
    /** The share of the window the map must keep with every drawer closed (TD-107, A02 test). */
    public static final float MIN_VIEWPORT_SHARE = 0.70f;
    /** The panel-state file of the World style, separate from the classic one so a stale classic layout does not apply. */
    public static final String LAYOUT_FILE_NAME = "workspace_layout_world.json";
    /** Selects the style for one run: {@code world} or {@code classic} (testing aid, as the other GDXUNITY_EDITOR_* variables). */
    public static final String STYLE_VARIABLE = "GDXUNITY_EDITOR_LAYOUT";

    private WorldShell() {
    }

    /** The category bar for a scene in {@code mode}: Map, the World strip's sections for the mode, Players, Rules. */
    public static List<Category> categories(GameMode mode) {
        Objects.requireNonNull(mode, "mode cannot be null");
        List<Category> categories = new ArrayList<>();
        categories.add(MAP);
        for (WorldStrip.Section section : WorldStrip.sectionsFor(mode)) {
            categories.add(new Category(section.name().toLowerCase(Locale.ROOT), section.getLabel(), section, null));
        }
        categories.add(PLAYERS);
        categories.add(RULES);
        return categories;
    }

    /**
     * The share of a {@code width} × {@code height} window left to the map when the World style shows its header, category bar and
     * footer, plus the palette row when {@code paletteOpen}, with no side panel and no bottom drawer.
     */
    public static float viewportShare(float width, float height, boolean paletteOpen) {
        if (!(width > 0f) || !(height > 0f)) {
            throw new IllegalArgumentException("window size must be positive: " + width + " x " + height);
        }
        float bars = HEADER_HEIGHT + CATEGORY_BAR_HEIGHT + FOOTER_HEIGHT + (paletteOpen ? PALETTE_ROW_HEIGHT : 0f);
        return Math.max(0f, height - bars) / height;
    }

    /**
     * The World style's first-run panel state (TD-107 point 7): outliner, Details, console and the bottom drawer closed. Applied
     * when its own layout file does not exist yet; a classic layout file is never read for it.
     */
    public static WorkspaceLayoutConfig firstRunLayout() {
        WorkspaceLayoutConfig config = new WorkspaceLayoutConfig();
        config.setOutlinerShown(false);
        config.setDetailsMode(PanelLayout.DetailsMode.HIDDEN.name());
        config.setConsoleShown(false);
        config.setBottomPanelCollapsed(true);
        return config;
    }
}
