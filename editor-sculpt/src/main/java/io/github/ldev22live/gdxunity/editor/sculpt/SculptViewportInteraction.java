package io.github.ldev22live.gdxunity.editor.sculpt;

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
import io.github.ldev22live.gdxunity.engine.sculpt.BrushSettings;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushType;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptStroke;
import io.github.ldev22live.gdxunity.engine.sculpt.StrokeDelta;
import io.github.ldev22live.gdxunity.engine.sculpt.TriangleBvh;

import java.util.Objects;

/**
 * Turns mouse input in the 3D view into brush strokes while sculpt mode is on (TD-038). It wraps the normal 3D
 * interaction: a left-button drag on the selected sculpt paints, and everything else (camera fly, pan and orbit,
 * scrolling, keys, clicks that miss the sculpt) goes to the wrapped interaction unchanged.
 *
 * <ul>
 *   <li>Shift while pressing the button: use the smooth brush for this stroke.</li>
 *   <li>Ctrl while pressing the button: invert the brush (subtract) for this stroke.</li>
 *   <li>Alt with the left button is camera orbit and is never a stroke.</li>
 * </ul>
 *
 * <p>The ray is moved into the sculpt's own space with the inverse of the entity's world matrix, so the entity may
 * be moved, rotated and scaled; brush sizes are in the sculpt's own units. A finished stroke is pushed on the
 * editor's undo stack as one {@link SculptStrokeCommand}.
 */
public final class SculptViewportInteraction implements ViewportInteraction {
    private final EditorState editorState;
    private final SculptSession session;
    private final Camera camera;
    private final ViewportInteraction delegate;
    private final TriangleBvh.Hit hit = new TriangleBvh.Hit();

    /** The stroke engine, reused for every stroke on {@link #activeDocument}. */
    private SculptStroke stroke;
    private SculptDocument activeDocument;
    private Entity activeEntity;
    private Matrix4 activeInverse;
    private BrushType activeType;

    /**
     * @param delegate the interaction that handles everything that is not sculpting
     */
    public SculptViewportInteraction(EditorState editorState, SculptSession session, Camera camera,
                                     ViewportInteraction delegate) {
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
        boolean wantsStroke = session.isSculptMode() && button == Input.Buttons.LEFT
            && (modifiers & EditorKeyboardDispatcher.MOD_ALT) == 0 && !isStroking();
        if (wantsStroke && beginStroke(localX, localY, panelWidth, panelHeight, modifiers)) {
            return true;
        }
        return delegate.touchDown(localX, localY, panelWidth, panelHeight, button, modifiers);
    }

    private boolean beginStroke(float localX, float localY, float panelWidth, float panelHeight, int modifiers) {
        Entity entity = editorState.getSelectedEntity();
        SculptDocument document = session.getDocumentOf(entity);
        if (document == null) {
            return false;
        }
        Matrix4 inverse = new Matrix4(entity.getTransform().getWorldMatrix()).inv();
        if (!raycastLocal(document, inverse, localX, localY, panelWidth, panelHeight)) {
            return false;
        }
        BrushSettings settings = session.getBrush().copy();
        if ((modifiers & EditorKeyboardDispatcher.MOD_CTRL) != 0) {
            settings.setSubtract(!settings.isSubtract());
        }
        BrushType type = (modifiers & EditorKeyboardDispatcher.MOD_SHIFT) != 0 ? BrushType.SMOOTH : session.getBrushType();
        if (stroke == null || document != activeDocument) {
            stroke = new SculptStroke(document.getSurface());
        }
        activeDocument = document;
        activeEntity = entity;
        activeInverse = inverse;
        activeType = type;
        stroke.begin(type, settings, session.getSymmetry(), hit.x, hit.y, hit.z, hit.nx, hit.ny, hit.nz);
        return true;
    }

    /** Casts the mouse ray at the sculpt in its own space; the result is in {@link #hit}. */
    private boolean raycastLocal(SculptDocument document, Matrix4 inverse, float localX, float localY,
                                 float panelWidth, float panelHeight) {
        camera.update();
        Ray ray = ViewportCoordinates3D.panelToRay(localX, localY, panelWidth, panelHeight, camera.getGdxCamera());
        ray.mul(inverse);
        return document.getSurface().raycast(ray, Float.MAX_VALUE, hit);
    }

    @Override
    public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
        if (!isStroking()) {
            return delegate.touchDragged(localX, localY, panelWidth, panelHeight);
        }
        // When the cursor leaves the surface the stroke simply waits for it to come back
        if (raycastLocal(activeDocument, activeInverse, localX, localY, panelWidth, panelHeight)) {
            stroke.move(hit.x, hit.y, hit.z, hit.nx, hit.ny, hit.nz);
        }
        return true;
    }

    @Override
    public boolean touchUp(int button) {
        if (!isStroking() || button != Input.Buttons.LEFT) {
            return delegate.touchUp(button);
        }
        StrokeDelta delta = stroke.end();
        finishStroke(delta);
        return true;
    }

    private void finishStroke(StrokeDelta delta) {
        String name = "Sculpt: " + activeType.name().charAt(0) + activeType.name().substring(1).toLowerCase();
        if (delta.isEmpty()) {
            editorState.setStatusMessage(name + ": nothing under the brush", false);
            return;
        }
        SculptStrokeCommand command = new SculptStrokeCommand(activeDocument.getSurface(), delta, name);
        if (editorState.executeCommand(command)) {
            editorState.setStatusMessage(name + " (" + delta.size() + " vertices)", false);
        } else {
            // The editor refused the change (for example during Play): put the surface back as it was
            activeDocument.getSurface().apply(delta, false);
            editorState.setStatusMessage("The sculpt cannot be changed right now; the stroke was discarded", true);
        }
    }

    /**
     * Gives the world-space centre and normal of the last brush position of the current stroke, for drawing the brush
     * outline.
     *
     * @return false when no stroke is in progress
     */
    public boolean getBrushIndicator(Vector3 centerWorld, Vector3 normalWorld) {
        if (!isStroking() || activeEntity == null) {
            return false;
        }
        Matrix4 world = activeEntity.getTransform().getWorldMatrix();
        centerWorld.set(hit.x, hit.y, hit.z).mul(world);
        normalWorld.set(hit.nx, hit.ny, hit.nz).mul(new Matrix4(world).inv().tra()).nor();
        return true;
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
}
