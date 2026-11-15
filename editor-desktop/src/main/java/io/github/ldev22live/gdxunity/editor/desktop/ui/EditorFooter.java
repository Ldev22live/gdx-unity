package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import java.util.Objects;

/**
 * The persistent footer of the World-first layout (roadmap Appendix E1, milestone M1b.3): Game Genre and Game Mode on the left, then
 * Customize and the play controls on the right. The left side scrolls sideways when the window is narrow; the right side never shrinks
 * or hides, so Customize and Play stay visible at the editor's smallest window (TD-077).
 *
 * <p>Game Mode is the scene's own mode (owner decision 11, 2026-11-14): the same 2D / 2.5D / 3D setting that is saved with the scene
 * and used by Play. The editor's 2D/3D view toggle stays in the viewport overlay, a separate concern. There is no genre system yet
 * (roadmap R3b), so Game Genre says so instead of offering choices that do nothing.
 */
public class EditorFooter extends Table {
    /** What Game Genre shows until genre profiles exist (roadmap R3b, M3b.1 to M3b.7). */
    public static final String GENRE_NOT_YET = "none yet (planned)";

    private static final float GROUP_GAP = 16f;

    /**
     * @param modeSelector the scene's game mode selector
     * @param customize    the Customize gateway (indicator, Customize, Back to World)
     * @param playControls Play, Pause, Stop and the state badge
     */
    public EditorFooter(Skin skin, Actor modeSelector, Actor customize, Actor playControls) {
        super(skin);
        Objects.requireNonNull(modeSelector, "modeSelector cannot be null");
        Objects.requireNonNull(customize, "customize cannot be null");
        Objects.requireNonNull(playControls, "playControls cannot be null");
        setBackground("header-bg");

        Table settings = new Table(skin);
        // The scroll pane stretches this table to its width; keep the selectors at the left instead of centred
        settings.left();
        settings.add(new Label("Game Genre", skin)).padLeft(8).padRight(6);
        settings.add(new Label(GENRE_NOT_YET, skin, "muted")).padRight(GROUP_GAP);
        settings.add(new Label("Game Mode", skin)).padRight(6);
        settings.add(modeSelector).padRight(GROUP_GAP);

        Table actions = new Table(skin);
        actions.add(customize).padRight(8);
        actions.add(new Label("|", skin, "muted")).padRight(4);
        actions.add(playControls).padRight(8);

        add(ScrollingBar.around(settings, skin)).growX().minWidth(0).left();
        add(actions).right();
    }
}
