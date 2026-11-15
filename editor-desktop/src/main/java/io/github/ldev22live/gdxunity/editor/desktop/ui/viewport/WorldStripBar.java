package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorldStrip;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The World strip at the bottom of the scene view (roadmap Appendix E1, M1b.2, TD-085): Terrain, Objects, Buildings and Characters
 * for a 2.5D or 3D scene, Tiles, Sprites, Objects and Characters for a 2D one. Terrain and Objects open the level editor with only that
 * part of its palette ({@link LevelSession.Focus}); pressing the lit entry again closes it. Tiles and Sprites open their editors. An
 * entry with nothing behind it yet says why in the status bar ({@link WorldStrip#unavailableReason}).
 */
public class WorldStripBar extends Table {
    private static final float BUTTON_HEIGHT = 26f;

    private final EditorState editorState;
    private final LevelSession level;
    private final Skin skin;
    private final Runnable toggleLevel;
    private final Map<WorldStrip.Section, Runnable> openers;
    private final Map<WorldStrip.Section, TextButton> buttons = new EnumMap<>(WorldStrip.Section.class);
    private GameMode shownMode;

    /**
     * @param toggleLevel turns the level editor on or off (the Level action, so its view switching and status text apply)
     * @param openers     what Tiles and Sprites open
     */
    public WorldStripBar(Skin skin, EditorState editorState, LevelSession level, Runnable toggleLevel,
                         Map<WorldStrip.Section, Runnable> openers) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.toggleLevel = Objects.requireNonNull(toggleLevel, "toggleLevel cannot be null");
        this.openers = Map.copyOf(Objects.requireNonNull(openers, "openers cannot be null"));
        setBackground("elevated-bg");
        pad(3f);
    }

    private void build(GameMode mode) {
        clearChildren();
        buttons.clear();
        for (WorldStrip.Section section : WorldStrip.sectionsFor(mode)) {
            Optional<String> reason = WorldStrip.unavailableReason(section, mode);
            TextButton button = new TextButton(section.getLabel(), skin, "flat");
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    choose(section, reason);
                }
            });
            buttons.put(section, button);
            add(button).height(BUTTON_HEIGHT).padLeft(4).padRight(4);
        }
        shownMode = mode;
        pack();
    }

    private void choose(WorldStrip.Section section, Optional<String> reason) {
        if (reason.isPresent()) {
            editorState.setStatusMessage(section.getLabel() + ": " + reason.get(), false);
            return;
        }
        switch (section) {
            case TERRAIN -> focus(LevelSession.Focus.TERRAIN);
            case OBJECTS -> focus(LevelSession.Focus.OBJECTS);
            default -> {
                Runnable opener = openers.get(section);
                if (opener != null) {
                    opener.run();
                }
            }
        }
    }

    /** Opens the level editor on {@code focus}, or closes it when that focus is already open. */
    private void focus(LevelSession.Focus focus) {
        if (level.isActive() && level.getFocus() == focus) {
            toggleLevel.run();
            return;
        }
        if (!level.isActive()) {
            toggleLevel.run();
        }
        if (level.isActive()) {
            level.setFocus(focus);
        }
    }

    @Override
    public void act(float delta) {
        GameMode mode = editorState.getGameMode();
        if (mode != shownMode) {
            build(mode);
        }
        for (Map.Entry<WorldStrip.Section, TextButton> entry : buttons.entrySet()) {
            boolean lit = level.isActive() && (entry.getKey() == WorldStrip.Section.TERRAIN && level.getFocus() == LevelSession.Focus.TERRAIN
                || entry.getKey() == WorldStrip.Section.OBJECTS && level.getFocus() == LevelSession.Focus.OBJECTS);
            entry.getValue().setColor(skin.getColor(lit ? "accent" : "text-primary"));
        }
        super.act(delta);
    }
}
