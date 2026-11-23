package io.github.ldev22live.gdxunity.editor.terrain;

import java.util.Optional;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportCoordinates3D;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;

import java.util.Objects;

/**
 * Places and erases level objects with the mouse while the level editor's Place or Erase tool is in hand (TD-083). A left click on the
 * ground places (or erases) one object at that point; everything else, including the brush tools, goes to the wrapped interaction
 * unchanged. Alt with the left button is camera orbit and never places. With Scatter (roadmap M3.6) a left drag scatters the chosen
 * object along the way, Ctrl-drag removes it, and releasing the button ends the stroke as one undo step.
 */
public final class LevelViewportInteraction implements ViewportInteraction {
    private final LevelSession level;
    private final Camera camera;
    private final ViewportInteraction delegate;
    private final Terrain.Hit hit = new Terrain.Hit();
    /** True between a click this interaction took and the button going up, so the drag and release do not reach the delegate. */
    private boolean consuming;

    /** @param delegate the interaction that handles everything that is not placing or erasing */
    public LevelViewportInteraction(LevelSession level, Camera camera, ViewportInteraction delegate) {
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public boolean touchDown(float localX, float localY, float panelWidth, float panelHeight, int button, int modifiers) {
        boolean wanted = level.isActive() && !level.getTool().isBrush() && button == Input.Buttons.LEFT
            && (modifiers & EditorKeyboardDispatcher.MOD_ALT) == 0;
        if (wanted && level.getTool() == LevelSession.Tool.SCATTER) {
            Terrain.Hit at = groundHit(localX, localY, panelWidth, panelHeight);
            if (at != null && level.beginScatter((modifiers & EditorKeyboardDispatcher.MOD_CTRL) != 0)) {
                level.scatterAt(at.x, at.z);
                consuming = true;
                return true;
            }
        }
        if (wanted && level.getTool() != LevelSession.Tool.SCATTER && act(localX, localY, panelWidth, panelHeight)) {
            consuming = true;
            return true;
        }
        return delegate.touchDown(localX, localY, panelWidth, panelHeight, button, modifiers);
    }

    /** Where the mouse points on the ground, in the ground's own space; null when it misses the ground or there is none. */
    private Terrain.Hit groundHit(float localX, float localY, float panelWidth, float panelHeight) {
        Entity ground = level.findGround();
        Terrain terrain = level.findTerrain();
        if (ground == null || terrain == null) {
            return null;
        }
        Matrix4 inverse = new Matrix4(ground.getTransform().getWorldMatrix()).inv();
        camera.update();
        Ray ray = ViewportCoordinates3D.panelToRay(localX, localY, panelWidth, panelHeight, camera.getGdxCamera());
        ray.mul(inverse);
        return terrain.raycast(ray, Float.MAX_VALUE, hit) ? hit : null;
    }

    /** @return true when the click landed on the ground and was used */
    private boolean act(float localX, float localY, float panelWidth, float panelHeight) {
        Entity ground = level.findGround();
        if (groundHit(localX, localY, panelWidth, panelHeight) == null) {
            return false;
        }
        if (level.getTool() == LevelSession.Tool.PLACE) {
            level.placeObject(hit.x, hit.y, hit.z);
        } else if (level.getTool() == LevelSession.Tool.PATH) {
            level.addPathPoint(hit.x, hit.z); // the ground's own space, where a path's points live (roadmap M3.7)
        } else {
            Vector3 world = new Vector3(hit.x, hit.y, hit.z).mul(ground.getTransform().getWorldMatrix());
            level.eraseObjectNear(world.x, world.z);
        }
        return true;
    }

    @Override
    public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
        if (consuming && level.isScattering()) {
            Terrain.Hit at = groundHit(localX, localY, panelWidth, panelHeight);
            if (at != null) {
                level.scatterAt(at.x, at.z);
            }
            return true;
        }
        return consuming || delegate.touchDragged(localX, localY, panelWidth, panelHeight);
    }

    @Override
    public boolean touchUp(int button) {
        if (consuming && button == Input.Buttons.LEFT) {
            consuming = false;
            if (level.isScattering()) {
                level.endScatter();
            }
            return true;
        }
        return delegate.touchUp(button);
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
