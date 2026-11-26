package io.github.ldev22live.gdxunity.editor.desktop.ui.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.workspace.MinimapMath;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The minimap over the map's bottom-left corner (TD-107 point 3): a schematic top view of the level, one dot per entity (the
 * selection lit), the view's centre marked; a click centres the view on that point. The mapping is {@link MinimapMath}; this widget
 * only draws it and reports clicks. Hidden during Play and when the scene is empty.
 */
public class MinimapView extends Widget implements Disposable {
    /** Side of the minimap, in UI units. */
    public static final float SIZE = 132f;
    private static final float DOT = 4f;
    private static final float SELECTED_DOT = 7f;
    private static final float VIEW_MARK = 9f;
    private static final Color BACKGROUND = new Color(0.08f, 0.10f, 0.12f, 0.78f);
    private static final Color FRAME = new Color(0.55f, 0.62f, 0.70f, 0.9f);
    private static final Color ENTITY = new Color(0.80f, 0.86f, 0.92f, 0.85f);
    private static final Color SELECTED = new Color(1f, 0.78f, 0.25f, 1f);
    private static final Color VIEW = new Color(0.35f, 0.75f, 1f, 1f);

    private final EditorState editorState;
    private final Supplier<Vector3> viewCentre;
    private final Texture pixel;
    private final Vector2 point = new Vector2();
    private final Color previous = new Color();
    private MinimapMath map;
    private int mappedEntities = -1;
    private Scene mappedScene;

    /**
     * @param viewCentre the point the editor's view is centred on (the 3D orbit target, or the 2D camera position)
     * @param centreView centres the editor's view on a world point (a click on the minimap)
     */
    public MinimapView(EditorState editorState, Supplier<Vector3> viewCentre, Consumer<Vector3> centreView) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.viewCentre = Objects.requireNonNull(viewCentre, "viewCentre cannot be null");
        Objects.requireNonNull(centreView, "centreView cannot be null");
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (map != null) {
                    Vector3 level = viewCentre.get();
                    centreView.accept(map.toWorld(x, y, getWidth(), map.isGroundPlane() ? level.y : level.z, new Vector3()));
                }
            }
        });
    }

    @Override
    public float getPrefWidth() {
        return SIZE;
    }

    @Override
    public float getPrefHeight() {
        return SIZE;
    }

    @Override
    public void act(float delta) {
        Scene scene = editorState.getActiveScene();
        boolean show = scene != null && !editorState.isPlayModeActive() && !scene.getAllEntities().isEmpty();
        setVisible(show);
        if (show && (scene != mappedScene || scene.getAllEntities().size() != mappedEntities)) {
            // Re-fitted when entities are added or removed, not every frame: moving one entity does not rescale the map
            map = MinimapMath.of(scene);
            mappedScene = scene;
            mappedEntities = scene.getAllEntities().size();
        }
        super.act(delta);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (map == null) {
            return;
        }
        float x = getX();
        float y = getY();
        float size = getWidth();
        previous.set(batch.getColor());
        fill(batch, FRAME, parentAlpha, x - 1f, y - 1f, size + 2f, size + 2f);
        fill(batch, BACKGROUND, parentAlpha, x, y, size, size);
        Entity selected = editorState.getSelectedEntity();
        for (Entity entity : editorState.getActiveScene().getAllEntities()) {
            if (entity == selected) {
                continue;
            }
            dot(batch, ENTITY, parentAlpha, entity.getTransform().getWorldPosition(), DOT);
        }
        if (selected != null) {
            dot(batch, SELECTED, parentAlpha, selected.getTransform().getWorldPosition(), SELECTED_DOT);
        }
        map.toMinimap(viewCentre.get(), size, point);
        if (point.x >= 0f && point.x <= size && point.y >= 0f && point.y <= size) {
            float half = VIEW_MARK / 2f;
            fill(batch, VIEW, parentAlpha, x + point.x - half, y + point.y - 1f, VIEW_MARK, 2f);
            fill(batch, VIEW, parentAlpha, x + point.x - 1f, y + point.y - half, 2f, VIEW_MARK);
        }
        batch.setColor(previous);
    }

    private void dot(Batch batch, Color color, float alpha, Vector3 world, float side) {
        map.toMinimap(world, getWidth(), point);
        if (point.x < 0f || point.x > getWidth() || point.y < 0f || point.y > getHeight()) {
            return;
        }
        fill(batch, color, alpha, getX() + point.x - side / 2f, getY() + point.y - side / 2f, side, side);
    }

    private void fill(Batch batch, Color color, float alpha, float x, float y, float width, float height) {
        batch.setColor(color.r, color.g, color.b, color.a * alpha);
        batch.draw(pixel, x, y, width, height);
    }

    @Override
    public void dispose() {
        pixel.dispose();
    }
}
