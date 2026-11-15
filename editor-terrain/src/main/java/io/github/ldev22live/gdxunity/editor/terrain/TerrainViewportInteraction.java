package io.github.ldev22live.gdxunity.editor.terrain;

import java.util.Optional;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportCoordinates3D;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainBrush;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainDelta;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainStroke;

import java.util.Locale;
import java.util.Objects;

/**
 * Turns mouse input in the 3D view into terrain brush strokes while paint mode is on (TD-082). It wraps the normal 3D interaction: a
 * left-button drag on the selected terrain paints, and everything else (camera fly, pan and orbit, scrolling, keys, clicks that miss
 * the terrain) goes to the wrapped interaction unchanged.
 *
 * <ul>
 *   <li>Shift while pressing the button: use the smooth brush for this stroke.</li>
 *   <li>Ctrl while pressing the button: invert the brush (raise lowers, lower raises) for this stroke.</li>
 *   <li>Alt with the left button is camera orbit and is never a stroke.</li>
 * </ul>
 *
 * <p>The ray is moved into the terrain's own space with the inverse of the entity's world matrix, so the entity may be moved, rotated
 * and scaled; brush sizes are in the terrain's own units. A finished stroke is one {@link TerrainStrokeCommand} on the undo stack.
 */
public final class TerrainViewportInteraction implements ViewportInteraction {
    private final EditorState editorState;
    private final TerrainSession session;
    private final Camera camera;
    private final ViewportInteraction delegate;
    private final Terrain.Hit hit = new Terrain.Hit();

    private TerrainStroke stroke;
    private Terrain activeTerrain;
    private String activePath;
    private Matrix4 activeInverse;
    private TerrainBrush.Type activeType;

    /** @param delegate the interaction that handles everything that is not painting */
    public TerrainViewportInteraction(EditorState editorState, TerrainSession session, Camera camera, ViewportInteraction delegate) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    /** True while a stroke is in progress (between the button going down and up). */
    public boolean isStroking() {
        return stroke != null && stroke.isActive();
    }

    @Override
    public boolean touchDown(float localX, float localY, float panelWidth, float panelHeight, int button, int modifiers) {
        boolean wantsStroke = session.isPaintMode() && button == Input.Buttons.LEFT
            && (modifiers & EditorKeyboardDispatcher.MOD_ALT) == 0 && !isStroking();
        if (wantsStroke && beginStroke(localX, localY, panelWidth, panelHeight, modifiers)) {
            return true;
        }
        return delegate.touchDown(localX, localY, panelWidth, panelHeight, button, modifiers);
    }

    private boolean beginStroke(float localX, float localY, float panelWidth, float panelHeight, int modifiers) {
        Entity entity = session.strokeTarget(editorState.getSelectedEntity());
        Terrain terrain = session.getTerrainOf(entity);
        if (terrain == null) {
            return false;
        }
        Matrix4 inverse = new Matrix4(entity.getTransform().getWorldMatrix()).inv();
        if (!raycastLocal(terrain, inverse, localX, localY, panelWidth, panelHeight)) {
            return false;
        }
        TerrainBrush brush = session.getBrush().copy();
        if ((modifiers & EditorKeyboardDispatcher.MOD_CTRL) != 0) {
            brush.setInverted(!brush.isInverted());
        }
        if ((modifiers & EditorKeyboardDispatcher.MOD_SHIFT) != 0) {
            brush.setType(TerrainBrush.Type.SMOOTH);
        }
        if (stroke == null || terrain != activeTerrain) {
            stroke = new TerrainStroke(terrain);
        }
        activeTerrain = terrain;
        activePath = TerrainSession.componentOf(entity).getTerrainPath();
        activeInverse = inverse;
        activeType = brush.getType();
        stroke.begin(brush, hit.x, hit.z);
        return true;
    }

    /** Casts the mouse ray at the terrain in its own space; the result is in {@link #hit}. */
    private boolean raycastLocal(Terrain terrain, Matrix4 inverse, float localX, float localY, float panelWidth, float panelHeight) {
        camera.update();
        Ray ray = ViewportCoordinates3D.panelToRay(localX, localY, panelWidth, panelHeight, camera.getGdxCamera());
        ray.mul(inverse);
        return terrain.raycast(ray, Float.MAX_VALUE, hit);
    }

    @Override
    public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
        if (!isStroking()) {
            return delegate.touchDragged(localX, localY, panelWidth, panelHeight);
        }
        // When the cursor leaves the terrain the stroke simply waits for it to come back
        if (raycastLocal(activeTerrain, activeInverse, localX, localY, panelWidth, panelHeight)) {
            stroke.move(hit.x, hit.z);
        }
        return true;
    }

    @Override
    public boolean touchUp(int button) {
        if (!isStroking() || button != Input.Buttons.LEFT) {
            return delegate.touchUp(button);
        }
        finishStroke(stroke.end());
        return true;
    }

    private void finishStroke(TerrainDelta delta) {
        String name = "Terrain: " + activeType.name().charAt(0) + activeType.name().substring(1).toLowerCase(Locale.ROOT);
        if (delta.isEmpty()) {
            editorState.setStatusMessage(name + ": nothing changed", false);
            return;
        }
        TerrainStrokeCommand command = new TerrainStrokeCommand(session.getLibrary(), activePath, activeTerrain, delta, name);
        if (editorState.executeCommand(command)) {
            editorState.setStatusMessage(name + " (" + Math.max(delta.size(), delta.surfaceIndices().length) + " points)", false);
        } else {
            // The editor refused the change (for example during Play): put the ground back as it was
            activeTerrain.apply(delta, false);
            editorState.setStatusMessage("The terrain cannot be changed right now; the stroke was discarded", true);
        }
    }

    @Override
    public boolean scrolled(float amountY) {
        return delegate.scrolled(amountY);
    }

    @Override
    public boolean keyDown(int keycode) {
        return delegate.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        return delegate.keyUp(keycode);
    }

    @Override
    public void update(float deltaTime) {
        delegate.update(deltaTime);
    }

    @Override
    public Vector3 worldPointAt(float localX, float localY, float panelWidth, float panelHeight) {
        return delegate.worldPointAt(localX, localY, panelWidth, panelHeight);
    }

    @Override
    public Optional<Entity> entityAt(float localX, float localY, float panelWidth, float panelHeight) {
        return delegate.entityAt(localX, localY, panelWidth, panelHeight);
    }
}
