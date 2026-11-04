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
}
