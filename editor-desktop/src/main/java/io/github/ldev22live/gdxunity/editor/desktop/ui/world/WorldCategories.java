package io.github.ldev22live.gdxunity.editor.desktop.ui.world;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorldShell;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The category bar under the map and the palette row under it (TD-107 points 4 and 5). The bar holds Map, the World strip's
 * section tabs for the scene's mode (passed in, so they keep their behaviour), Players and Rules. The palette row shows the open
 * category's existing palette (the level palette), the Players panel (TD-108, roadmap A04) or the Rules panel (TD-109, A05).
 */
public class WorldCategories {
    private static final float BUTTON_HEIGHT = 28f;

    private final Skin skin;
    private final LevelSession level;
    private final Runnable toggleLevel;
    private final BooleanSupplier worldToolsShown;
    private final Table bar;
    private final Container<Actor> paletteRow;
    private final Actor palette;
    private final Label notBuiltLabel;
    private final TextButton mapButton;
    private final TextButton playersButton;
    private final TextButton rulesButton;
    private WorldShell.Category notBuilt;
    /** True while the Players panel has the palette row. */
    private boolean playersOpen;
    /** True while the Rules panel has the palette row. */
    private boolean rulesOpen;

    /**
     * @param sections        the World strip's section tabs (Terrain, Objects, Buildings, Characters, or the 2D ones)
     * @param palette         the open section's palette (the level palette)
     * @param players         the Players category's panel
     * @param rules           the Rules category's panel
     * @param statusLine      the status messages, at the right of the bar
     * @param toggleLevel     turns the level editor on or off (the Level action)
     * @param worldToolsShown whether the World's tools apply now (not in Play, not in another workspace)
     */
    public WorldCategories(Skin skin, EditorState editorState, LevelSession level, Runnable toggleLevel, Actor sections, Actor palette,
                           Actor players, Actor rules, Actor statusLine, BooleanSupplier worldToolsShown) {
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        Objects.requireNonNull(editorState, "editorState cannot be null");
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.toggleLevel = Objects.requireNonNull(toggleLevel, "toggleLevel cannot be null");
        this.palette = Objects.requireNonNull(palette, "palette cannot be null");
        this.worldToolsShown = Objects.requireNonNull(worldToolsShown, "worldToolsShown cannot be null");
        mapButton = button(WorldShell.MAP.label(), this::openMap);
        playersButton = button(WorldShell.PLAYERS.label(), this::togglePlayers);
        rulesButton = button(WorldShell.RULES.label(), this::toggleRules);
        bar = new Table(skin) {
            @Override
            public void act(float delta) {
                boolean show = worldToolsShown.getAsBoolean();
                mapButton.setVisible(show);
                playersButton.setVisible(show);
                rulesButton.setVisible(show);
                highlight();
                super.act(delta);
            }
        };
        bar.setBackground("header-bg");
        bar.add(mapButton).height(BUTTON_HEIGHT).padLeft(8).padRight(8);
        bar.add(sections).left();
        bar.add(playersButton).height(BUTTON_HEIGHT).padLeft(8).padRight(12);
        bar.add(rulesButton).height(BUTTON_HEIGHT).padRight(8);
        bar.add(statusLine).growX().minWidth(0).padLeft(8).padRight(8);

        notBuiltLabel = new Label("", skin, "muted");
        notBuiltLabel.setWrap(true);
        ScrollPane paletteScroll = new ScrollPane(palette, skin);
        paletteScroll.setFadeScrollBars(false);
        paletteScroll.setScrollingDisabled(false, false);
        ScrollPane playersScroll = new ScrollPane(Objects.requireNonNull(players, "players cannot be null"), skin);
        playersScroll.setFadeScrollBars(false);
        playersScroll.setScrollingDisabled(false, true);
        // Rule cards grow with their effects: the row scrolls both ways
        ScrollPane rulesScroll = new ScrollPane(Objects.requireNonNull(rules, "rules cannot be null"), skin);
        rulesScroll.setFadeScrollBars(false);
        rulesScroll.setScrollingDisabled(false, false);
        // A new scene starts placing Unowned again: the owner chosen belonged to the previous scene's players
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onActiveSceneChanged(Scene oldScene, Scene newScene) {
                level.setPlacementOwner(null);
            }
        });
        paletteRow = new Container<>() {
            private Object shown = "";

            @Override
            public void act(float delta) {
                Object want;
                if (!worldToolsShown.getAsBoolean()) {
                    want = null;
                } else if (notBuilt != null) {
                    want = notBuiltLabel;
                } else if (playersOpen) {
                    want = playersScroll;
                } else if (rulesOpen) {
                    want = rulesScroll;
                } else if (level.isActive()) {
                    want = paletteScroll;
                } else {
                    want = null;
                }
                if (want != shown) {
                    shown = want;
                    setActor((Actor) want);
                    height(want == null ? 0f : WorldShell.PALETTE_ROW_HEIGHT);
                    invalidateHierarchy();
                }
                super.act(delta);
            }
        };
        paletteRow.fill();
        paletteRow.pad(0f, 8f, 0f, 8f);
        paletteRow.height(0f);
    }

    /** The category bar. */
    public Table getBar() {
        return bar;
    }

    /** The palette row (zero height while nothing is open). */
    public Container<Actor> getPaletteRow() {
        return paletteRow;
    }

    /** The category whose "not built yet" note is open, or null. */
    public WorldShell.Category getNotBuilt() {
        return notBuilt;
    }

    private TextButton button(String label, Runnable action) {
        TextButton button = new TextButton(label, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    /** True while the Players panel has the palette row. */
    public boolean isPlayersOpen() {
        return playersOpen;
    }

    /** Opens the Players panel in the palette row, as pressing Players does when it is closed. */
    public void openPlayers() {
        if (!playersOpen) {
            togglePlayers();
        }
    }

    /** True while the Rules panel has the palette row. */
    public boolean isRulesOpen() {
        return rulesOpen;
    }

    /** Opens the Rules panel in the palette row, as pressing Rules does when it is closed. */
    public void openRules() {
        if (!rulesOpen) {
            toggleRules();
        }
    }

    /** Rules: the rules and objectives in the palette row; pressing again closes it. */
    private void toggleRules() {
        notBuilt = null;
        playersOpen = false;
        rulesOpen = !rulesOpen;
        if (rulesOpen && level.isActive()) {
            toggleLevel.run(); // the panel takes the palette row; a section chosen afterwards replaces it
        }
    }

    /** Players: the player slots, bounds and placement owner in the palette row; pressing again closes it. */
    private void togglePlayers() {
        notBuilt = null;
        rulesOpen = false;
        playersOpen = !playersOpen;
        if (playersOpen && level.isActive()) {
            toggleLevel.run(); // the panel takes the palette row; a section chosen afterwards replaces it
        }
    }

    /** Map: the level editor on its whole-level focus (new level, size, surfaces); a second press closes it. */
    private void openMap() {
        notBuilt = null;
        playersOpen = false;
        rulesOpen = false;
        if (level.isActive() && level.getFocus() == LevelSession.Focus.ALL) {
            toggleLevel.run();
            return;
        }
        if (!level.isActive()) {
            toggleLevel.run();
        }
        if (level.isActive()) {
            level.setFocus(LevelSession.Focus.ALL);
        }
    }

    /** Players or Rules: the note saying which milestone builds them; pressing again closes it. */
    private void showNotBuilt(WorldShell.Category category) {
        playersOpen = false;
        notBuilt = notBuilt == category ? null : category;
        if (notBuilt != null) {
            notBuiltLabel.setText(category.label() + ": " + category.notBuilt().orElseThrow());
            if (level.isActive()) {
                toggleLevel.run(); // the note takes the palette row; a section chosen afterwards replaces it
            }
        }
    }

    private void highlight() {
        boolean levelOpen = level.isActive();
        if (levelOpen && notBuilt != null) {
            // A World section (or Map) opened the level editor after the note: its palette replaces the note
            notBuilt = null;
        }
        if (levelOpen && (playersOpen || rulesOpen)) {
            playersOpen = false; // likewise for the Players and Rules panels
            rulesOpen = false;
        }
        mapButton.setColor(skin.getColor(levelOpen && notBuilt == null && level.getFocus() == LevelSession.Focus.ALL ? "accent" : "text-primary"));
        playersButton.setColor(skin.getColor(playersOpen ? "accent" : "text-primary"));
        rulesButton.setColor(skin.getColor(rulesOpen ? "accent" : "text-primary"));
    }
}
