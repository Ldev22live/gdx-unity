package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/** One pane of the multi-view 3D scene view (TD-039): its widget, its camera and what it draws. */
public interface ViewportPaneView extends Disposable {
    /** The widget that shows the pane and receives its mouse and key input. */
    SceneViewportWidget getWidget();

    /** Draws the scene through this pane's camera into the widget's frame buffer. */
    void render(Color background, float delta);

    /** Advances the pane's camera movement (flying) by a frame. */
    void update(float delta);

    /** Frames a sphere in this pane. */
    void frame(Vector3 center, float radius);

    /** Puts the pane's camera back where it starts. */
    void resetView();

    /** Whether the pane's view can be turned round in quarter turns (the isometric view, UI M2). */
    default boolean canTurn() {
        return false;
    }

    /**
     * Turns the view by quarter turns, positive to the left.
     *
     * @throws IllegalStateException when the view cannot turn
     */
    default void turn(int quarterTurns) {
        throw new IllegalStateException("This view cannot turn");
    }

    /** Fits the whole scene into the pane, for a full-map view; other views keep their camera. */
    default void frameWorld() {
    }
}
