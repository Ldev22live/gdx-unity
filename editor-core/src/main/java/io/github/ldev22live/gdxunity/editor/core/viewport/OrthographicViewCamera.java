package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;

import java.util.Collection;
import java.util.Objects;

/**
 * The camera of an axis view (TD-039): orthographic, looking straight along the X, Y or Z axis at a focus point.
 * One scene unit is one pixel at zoom 1; a larger zoom shows more of the scene. It wraps the editor's screen-filling
 * orthographic camera, whose depth range of +-20,000 units lets it see everything along its line of sight.
 */
public final class OrthographicViewCamera {
    /** Smallest zoom: a pixel covers a thousandth of a unit. */
    public static final float MIN_ZOOM = 0.001f;
    /** Largest zoom: a pixel covers a thousand units. */
    public static final float MAX_ZOOM = 1000f;
    /** How much larger than a framed box the view is: a tenth of room around the whole map. */
    public static final float BOX_MARGIN = 1.1f;

    private final OrthographicCamera2D camera = OrthographicCamera2D.createScreenFilling(0f, 0f);
    private final Vector3 focus = new Vector3();
    private ViewKind kind;
    /** Quarter turns about the vertical axis, 0 to 3, for a view that {@link ViewKind#canTurn() can turn}. */
    private int quarterTurns;
    private int width = 1;
    private int height = 1;

    /**
     * @param kind one of the axis views
     * @throws IllegalArgumentException for {@link ViewKind#PERSPECTIVE}
     */
    public OrthographicViewCamera(ViewKind kind) {
        setKind(kind);
    }

    /** The view this camera shows. */
    public ViewKind getKind() {
        return kind;
    }

    /**
     * Changes the axis this camera looks along.
     *
     * @throws IllegalArgumentException for {@link ViewKind#PERSPECTIVE}
     */
    public void setKind(ViewKind kind) {
        Objects.requireNonNull(kind, "kind cannot be null");
        if (!kind.isOrthographic()) {
            throw new IllegalArgumentException("An orthographic view camera needs an axis view, not " + kind);
        }
        this.kind = kind;
        quarterTurns = 0;
        update();
    }

    /** How many quarter turns to the left the view is turned, 0 to 3. */
    public int getQuarterTurns() {
        return quarterTurns;
    }

    /**
     * Turns the view round the focus about the vertical axis by {@code steps} quarter turns (positive turns the view to the left,
     * negative to the right), as isometric map editors do. The focus stays in the middle of the view.
     *
     * @throws IllegalStateException for a view that cannot turn (the axis views look along fixed axes)
     */
    public void turn(int steps) {
        if (!kind.canTurn()) {
            throw new IllegalStateException(kind.getDisplayName() + " looks along a fixed axis and cannot turn");
        }
        quarterTurns = Math.floorMod(quarterTurns + steps, 4);
        update();
    }

    /** The direction the view looks in, turned by its quarter turns, as a new vector. */
    public Vector3 getDirection() {
        return turned(kind.getDirection());
    }

    /** The screen's up direction, turned by its quarter turns, as a new vector. */
    public Vector3 getUp() {
        return turned(kind.getUp());
    }

    private Vector3 turned(Vector3 vector) {
        return quarterTurns == 0 ? vector : vector.rotate(Vector3.Y, 90f * quarterTurns);
    }

    /** The camera for renderers; call {@link #update()} first. */
    public OrthographicCamera2D getCamera() {
        return camera;
    }

    /** A copy of the point the view is centred on. */
    public Vector3 getFocus() {
        return new Vector3(focus);
    }

    /** Centres the view on a point. */
    public void setFocus(Vector3 point) {
        focus.set(Objects.requireNonNull(point, "point cannot be null"));
        update();
    }

    /** Scene units per pixel. */
    public float getZoom() {
        return camera.getZoom();
    }

    /** Sets the scene units per pixel, limited to {@link #MIN_ZOOM} .. {@link #MAX_ZOOM}. */
    public void setZoom(float zoom) {
        if (Float.isNaN(zoom)) {
            throw new IllegalArgumentException("zoom is not a number");
        }
        camera.setZoom(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom)));
        update();
    }

    /** Tells the camera the size of its pane in pixels. */
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            this.width = width;
            this.height = height;
            camera.resize(width, height);
            update();
        }
    }

    /** Applies the focus, zoom and axis to the underlying camera. */
    public void update() {
        Camera gdx = camera.getGdxCamera();
        gdx.position.set(focus);
        gdx.direction.set(getDirection());
        gdx.up.set(getUp());
        camera.update();
    }

    /** The height of the visible area in scene units; sizes gizmo handles. */
    public float getVisibleHeight() {
        return height * getZoom();
    }

    /** The unit vector pointing right on the screen. */
    public Vector3 getRight() {
        return getDirection().crs(getUp()).nor();
    }

    /**
     * Moves the view so the scene follows a drag of the pointer.
     *
     * @param dx pixels the pointer moved right
     * @param dy pixels the pointer moved up
     */
    public void pan(float dx, float dy) {
        Vector3 move = getRight().scl(-dx * getZoom()).mulAdd(getUp(), -dy * getZoom());
        focus.add(move);
        update();
    }

    /**
     * Zooms by a factor (above 1 shows less) keeping the scene point under the pointer where it is.
     *
     * @param pointerX pointer position in pixels from the left of the pane
     * @param pointerY pointer position in pixels from the bottom of the pane
     */
    public void zoomAt(float factor, float pointerX, float pointerY) {
        if (!(factor > 0f) || Float.isInfinite(factor)) {
            throw new IllegalArgumentException("factor must be a positive number: " + factor);
        }
        float oldZoom = getZoom();
        float newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, oldZoom / factor));
        // The pointer is (pointer - centre) pixels from the focus; keep that distance's scene length unchanged
        float offsetX = pointerX - width / 2f;
        float offsetY = pointerY - height / 2f;
        Vector3 shift = getRight().scl(offsetX * (oldZoom - newZoom)).mulAdd(getUp(), offsetY * (oldZoom - newZoom));
        focus.add(shift);
        camera.setZoom(newZoom);
        update();
    }

    /**
     * Centres on a sphere and zooms to fit it, with some room around it.
     *
     * @param center the centre of the thing to show
     * @param radius its radius in scene units, at least a small positive value is used
     */
    public void frame(Vector3 center, float radius) {
        focus.set(Objects.requireNonNull(center, "center cannot be null"));
        float visible = Math.max(radius, 1f) * 2.4f;
        camera.setZoom(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, visible / Math.max(1, Math.min(width, height)))));
        update();
    }

    /** The point on the plane through the focus, square to the view, that is under a pointer position. */
    public Vector3 worldPointAt(float pointerX, float pointerY) {
        float offsetX = (pointerX - width / 2f) * getZoom();
        float offsetY = (pointerY - height / 2f) * getZoom();
        return getRight().scl(offsetX).mulAdd(getUp(), offsetY).add(focus);
    }

    /**
     * Centres on boxes and zooms so all of them fit the pane as they are seen from this view, with {@link #BOX_MARGIN} room around
     * them: every box's eight corners are measured along the screen's right and up directions, and the view is centred on the
     * middle of what was measured.
     *
     * @throws IllegalArgumentException when {@code boxes} is empty
     */
    public void frame(Collection<BoundingBox> boxes) {
        Objects.requireNonNull(boxes, "boxes cannot be null");
        if (boxes.isEmpty()) {
            throw new IllegalArgumentException("Nothing to frame: no boxes");
        }
        Vector3 right = getRight();
        Vector3 up = getUp();
        float minRight = Float.MAX_VALUE;
        float maxRight = -Float.MAX_VALUE;
        float minUp = Float.MAX_VALUE;
        float maxUp = -Float.MAX_VALUE;
        BoundingBox union = null;
        Vector3 corner = new Vector3();
        for (BoundingBox box : boxes) {
            union = union == null ? new BoundingBox(box) : union.ext(box);
            for (int i = 0; i < 8; i++) {
                corner.set((i & 1) == 0 ? box.min.x : box.max.x, (i & 2) == 0 ? box.min.y : box.max.y,
                    (i & 4) == 0 ? box.min.z : box.max.z);
                float alongRight = corner.dot(right);
                float alongUp = corner.dot(up);
                minRight = Math.min(minRight, alongRight);
                maxRight = Math.max(maxRight, alongRight);
                minUp = Math.min(minUp, alongUp);
                maxUp = Math.max(maxUp, alongUp);
            }
        }
        // The middle of the boxes in depth, moved across the screen to the middle of what was measured
        Vector3 center = union.getCenter(new Vector3());
        focus.set(center).mulAdd(right, (minRight + maxRight) / 2f - center.dot(right)).mulAdd(up, (minUp + maxUp) / 2f - center.dot(up));
        float zoom = Math.max((maxRight - minRight) / width, (maxUp - minUp) / height) * BOX_MARGIN;
        camera.setZoom(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom)));
        update();
    }
}
