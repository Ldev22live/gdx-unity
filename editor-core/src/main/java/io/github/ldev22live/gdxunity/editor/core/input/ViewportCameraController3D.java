package io.github.ldev22live.gdxunity.editor.core.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;

import java.util.Objects;

/**
 * 3D Viewport Navigation Controller implementing:
 * - Orbit Camera (Alt + Left Click Drag)
 * - Pan Camera (Alt + Middle Click Drag or Middle Click Drag)
 * - Fly Camera (Right Click + WASD / QE)
 * - Dolly / Zoom (Mouse Wheel)
 *
 * Isolated from raw desktop windows for unit-testability and headless verification.
 */
public class ViewportCameraController3D {

    /** Minimum angle in degrees between the orbit offset and the world up/down axis, preventing the camera from flipping over the poles. */
    public static final float MIN_ORBIT_POLAR_ANGLE = 1f;
    /** Closest distance the dolly zoom may bring the camera to the orbit target. */
    public static final float MIN_DOLLY_DISTANCE = 0.2f;
    /** Camera-to-target distance at which pan speed equals {@code panSensitivity}; pan speed scales linearly with distance. */
    private static final float PAN_REFERENCE_DISTANCE = 10f;

    private final PerspectiveCamera3D camera;

    // Orbit target point in world coordinates
    private final Vector3 target = new Vector3(0f, 0f, 0f);

    // Sensitivities & speeds
    private float orbitSensitivity = 0.4f;
    private float panSensitivity = 0.05f;
    private float flyMoveSpeed = 10f; // units per second
    private float flyTurnSensitivity = 0.3f;
    private float zoomSensitivity = 1.5f;

    // Active drag state
    public enum NavigationMode {
        NONE,
        ORBIT,
        PAN,
        FLY
    }

    private NavigationMode currentMode = NavigationMode.NONE;
    private float lastScreenX;
    private float lastScreenY;

    // Key states for Fly camera
    private boolean moveForward;
    private boolean moveBackward;
    private boolean moveLeft;
    private boolean moveRight;
    private boolean moveUp;
    private boolean moveDown;

    public ViewportCameraController3D(PerspectiveCamera3D camera) {
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
    }

    public PerspectiveCamera3D getCamera() {
        return camera;
    }

    public Vector3 getTarget() {
        return new Vector3(target);
    }

    public void setTarget(Vector3 target) {
        Objects.requireNonNull(target, "target cannot be null");
        this.target.set(target);
    }

    public void setTarget(float x, float y, float z) {
        this.target.set(x, y, z);
    }

    public NavigationMode getCurrentMode() {
        return currentMode;
    }

    public void startNavigation(NavigationMode mode, float screenX, float screenY) {
        this.currentMode = (mode != null) ? mode : NavigationMode.NONE;
        this.lastScreenX = screenX;
        this.lastScreenY = screenY;
    }

    public void stopNavigation() {
        this.currentMode = NavigationMode.NONE;
        this.moveForward = false;
        this.moveBackward = false;
        this.moveLeft = false;
        this.moveRight = false;
        this.moveUp = false;
        this.moveDown = false;
    }

    /**
     * Handles mouse dragging for active navigation mode.
     */
    public boolean processDrag(float screenX, float screenY) {
        if (currentMode == NavigationMode.NONE) {
            return false;
        }

        float deltaX = screenX - lastScreenX;
        float deltaY = screenY - lastScreenY;
        lastScreenX = screenX;
        lastScreenY = screenY;

        switch (currentMode) {
            case ORBIT -> orbit(deltaX, deltaY);
            case PAN -> pan(deltaX, deltaY);
            case FLY -> flyLook(deltaX, deltaY);
            default -> {
                return false;
            }
        }

        camera.update();
        return true;
    }

    /**
     * Orbit camera around target position.
     * Vertical orbit is clamped so the camera never passes over the world up/down poles.
     */
    public void orbit(float deltaX, float deltaY) {
        // Horizontal rotation around world Y
        camera.rotateAround(target, Vector3.Y, -deltaX * orbitSensitivity);

        // Vertical rotation around camera right vector. A positive angle around `right`
        // moves the camera down, increasing its polar angle measured from world +Y.
        Vector3 offset = camera.getPosition().sub(target);
        if (!offset.isZero()) {
            float polarAngle = MathUtils.acos(MathUtils.clamp(offset.nor().dot(Vector3.Y), -1f, 1f)) * MathUtils.radDeg;
            float pitch = MathUtils.clamp(-deltaY * orbitSensitivity,
                MIN_ORBIT_POLAR_ANGLE - polarAngle,
                (180f - MIN_ORBIT_POLAR_ANGLE) - polarAngle);
            Vector3 right = new Vector3(camera.getDirection()).crs(camera.getUp()).nor();
            camera.rotateAround(target, right, pitch);
        }

        camera.lookAt(target);
    }

    /**
     * Pan camera parallel to the view plane.
     */
    public void pan(float deltaX, float deltaY) {
        Vector3 right = new Vector3(camera.getDirection()).crs(camera.getUp()).nor();
        Vector3 up = camera.getUp();

        float effectivePanSpeed = panSensitivity * (camera.getPosition().dst(target) / PAN_REFERENCE_DISTANCE);
        if (effectivePanSpeed <= 0f) effectivePanSpeed = panSensitivity;

        Vector3 translation = new Vector3();
        translation.mulAdd(right, -deltaX * effectivePanSpeed);
        translation.mulAdd(up, deltaY * effectivePanSpeed);

        camera.translate(translation.x, translation.y, translation.z);
        target.add(translation);
    }

    /**
     * Fly mode camera orientation (mouselook).
     */
    public void flyLook(float deltaX, float deltaY) {
        // Yaw around world Y
        camera.rotate(Vector3.Y, -deltaX * flyTurnSensitivity);

        // Pitch around camera right vector
        Vector3 right = new Vector3(camera.getDirection()).crs(camera.getUp()).nor();
        camera.rotate(right, -deltaY * flyTurnSensitivity);
    }

    /**
     * Updates fly camera translation based on elapsed time and keyboard inputs.
     */
    public void update(float deltaTime) {
        if (currentMode != NavigationMode.FLY || deltaTime <= 0f) {
            return;
        }

        Vector3 moveDir = new Vector3();
        Vector3 forward = new Vector3(camera.getDirection()).nor();
        Vector3 right = new Vector3(camera.getDirection()).crs(camera.getUp()).nor();
        Vector3 up = new Vector3(camera.getUp()).nor();

        if (moveForward) moveDir.add(forward);
        if (moveBackward) moveDir.sub(forward);
        if (moveRight) moveDir.add(right);
        if (moveLeft) moveDir.sub(right);
        if (moveUp) moveDir.add(up);
        if (moveDown) moveDir.sub(up);

        if (!moveDir.isZero()) {
            moveDir.nor().scl(flyMoveSpeed * deltaTime);
            camera.translate(moveDir.x, moveDir.y, moveDir.z);
            target.add(moveDir);
            camera.update();
        }
    }

    /**
     * Handles mouse wheel zoom / dolly towards target.
     * Scrolling forward (amountY < 0) dollies towards target; scrolling backward (amountY > 0) dollies away.
     */
    public boolean processScroll(float amountY) {
        Vector3 direction = new Vector3(camera.getDirection()).nor();
        float dist = camera.getPosition().dst(target);

        float dollyAmount = -amountY * zoomSensitivity;
        if (dollyAmount > 0f) {
            // Moving towards target - clamp so we don't pass target
            if (dist - dollyAmount < MIN_DOLLY_DISTANCE) {
                dollyAmount = Math.max(0f, dist - MIN_DOLLY_DISTANCE);
            }
        }

        camera.translate(direction.x * dollyAmount, direction.y * dollyAmount, direction.z * dollyAmount);
        camera.update();
        return true;
    }

    /**
     * Key event handling for Fly navigation.
     */
    public boolean processKeyDown(int keycode) {
        if (currentMode != NavigationMode.FLY) {
            return false;
        }

        switch (keycode) {
            case Input.Keys.W -> moveForward = true;
            case Input.Keys.S -> moveBackward = true;
            case Input.Keys.A -> moveLeft = true;
            case Input.Keys.D -> moveRight = true;
            case Input.Keys.E -> moveUp = true;
            case Input.Keys.Q -> moveDown = true;
            default -> {
                return false;
            }
        }
        return true;
    }

    public boolean processKeyUp(int keycode) {
        if (currentMode != NavigationMode.FLY) {
            return false;
        }

        switch (keycode) {
            case Input.Keys.W -> moveForward = false;
            case Input.Keys.S -> moveBackward = false;
            case Input.Keys.A -> moveLeft = false;
            case Input.Keys.D -> moveRight = false;
            case Input.Keys.E -> moveUp = false;
            case Input.Keys.Q -> moveDown = false;
            default -> {
                return false;
            }
        }
        return true;
    }

    // Setters for speed/sensitivity
    public void setOrbitSensitivity(float orbitSensitivity) {
        this.orbitSensitivity = orbitSensitivity;
    }

    public void setPanSensitivity(float panSensitivity) {
        this.panSensitivity = panSensitivity;
    }

    public float getFlyMoveSpeed() {
        return flyMoveSpeed;
    }

    public void setFlyMoveSpeed(float flyMoveSpeed) {
        this.flyMoveSpeed = flyMoveSpeed;
    }

    public void setFlyTurnSensitivity(float flyTurnSensitivity) {
        this.flyTurnSensitivity = flyTurnSensitivity;
    }

    public void setZoomSensitivity(float zoomSensitivity) {
        this.zoomSensitivity = zoomSensitivity;
    }
}
