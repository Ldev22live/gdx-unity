package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController;
import io.github.ldev22live.gdxunity.editor.core.input.ViewportCameraController3D;
import io.github.ldev22live.gdxunity.editor.core.viewport.OrthographicViewCamera;
import io.github.ldev22live.gdxunity.editor.core.viewport.OrthographicViewInteraction;
import io.github.ldev22live.gdxunity.editor.core.viewport.SceneViewportInteraction3D;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewKind;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportFocus;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.editor.desktop.ui.Collider3DRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.SelectionOutlineRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.SkeletonOverlayRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.TransformGizmoRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.workspaces.SceneEditorWorkspace;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptSession;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptViewportInteraction;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builds the views of the panes of the multi-view layout (TD-039). The first perspective pane is the editor's main 3D
 * camera, so its position and controls carry over from the single-view editor; further perspective panes get a camera
 * of their own, and each axis pane an orthographic one. Every pane has its own grid, selection outline, collider,
 * skeleton and gizmo drawing, tied to its camera, and sculpts through the shared {@link SculptSession}.
 */
public final class ScenePaneFactory implements MultiViewportArea.PaneFactory {
    /** Where a new perspective camera starts relative to the scene centre. */
    private static final Vector3 NEW_CAMERA_OFFSET = new Vector3(0f, 280f, 900f);
    /** Zoom a new axis view starts with, in scene units per pixel. */
    private static final float AXIS_START_ZOOM = 1.2f;

    /** The main 3D camera, its controller and its already-built interaction. */
    public record MainPerspective(PerspectiveCamera3D camera, ViewportCameraController3D controller,
                                  ViewportInteraction interaction, Runnable reset) {
    }

    private final EditorState editorState;
    private final GizmoInteractionController gizmoController;
    private final SculptSession sculptSession;
    private final SceneRenderer3D sceneRenderer3D;
    private final SceneEditorWorkspace workspace;
    private final BooleanSupplier showAllColliders;
    private final MainPerspective main;
    private final Vector3 sceneCenter;
    private boolean mainInUse;

    /**
     * @param sceneCenter the point new views look at, and axis views start centred on
     */
    public ScenePaneFactory(EditorState editorState, GizmoInteractionController gizmoController,
                            SculptSession sculptSession, SceneRenderer3D sceneRenderer3D,
                            SceneEditorWorkspace workspace, BooleanSupplier showAllColliders,
                            MainPerspective main, Vector3 sceneCenter) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.gizmoController = Objects.requireNonNull(gizmoController, "gizmoController cannot be null");
        this.sculptSession = Objects.requireNonNull(sculptSession, "sculptSession cannot be null");
        this.sceneRenderer3D = Objects.requireNonNull(sceneRenderer3D, "sceneRenderer3D cannot be null");
        this.workspace = Objects.requireNonNull(workspace, "workspace cannot be null");
        this.showAllColliders = Objects.requireNonNull(showAllColliders, "showAllColliders cannot be null");
        this.main = Objects.requireNonNull(main, "main cannot be null");
        this.sceneCenter = new Vector3(Objects.requireNonNull(sceneCenter, "sceneCenter cannot be null"));
    }

    @Override
    public ViewportPaneView create(ViewKind kind, int paneId) {
        if (kind.isOrthographic()) {
            return new AxisPane(kind);
        }
        if (!mainInUse) {
            mainInUse = true;
            return new PerspectivePane(main.camera(), main.controller(), main.interaction(), main.reset(), true);
        }
        PerspectiveCamera3D camera = new PerspectiveCamera3D(main.camera().getFieldOfView(), 800f, 600f,
            main.camera().getNearPlane(), main.camera().getFarPlane());
        ViewportCameraController3D controller = new ViewportCameraController3D(camera);
        controller.setPanSensitivity(main.controller().getPanSensitivity());
        controller.setFlyMoveSpeed(main.controller().getFlyMoveSpeed());
        controller.setZoomSensitivity(main.controller().getZoomSensitivity());
        ViewportInteraction interaction = new SculptViewportInteraction(editorState, sculptSession, camera,
            new SceneViewportInteraction3D(editorState, camera, controller, gizmoController));
        PerspectivePane pane = new PerspectivePane(camera, controller, interaction, null, false);
        pane.resetView();
        return pane;
    }

    /** Draws a pane's grid and overlays, in the order the single view does. */
    private final class Overlays implements SceneEditorWorkspace.PaneOverlays {
        private final Consumer<ShapeRenderer> grid;
        private final TransformGizmoRenderer gizmo;
        private final SelectionOutlineRenderer outline;
        private final Collider3DRenderer colliders;
        private final SkeletonOverlayRenderer skeleton;
        private final Supplier<Float> gizmoScale;

        Overlays(Consumer<ShapeRenderer> grid, TransformGizmoRenderer gizmo,
                 SelectionOutlineRenderer outline, Collider3DRenderer colliders, SkeletonOverlayRenderer skeleton,
                 Supplier<Float> gizmoScale) {
            this.grid = grid;
            this.gizmo = gizmo;
            this.outline = outline;
            this.colliders = colliders;
            this.skeleton = skeleton;
            this.gizmoScale = gizmoScale;
        }

        @Override
        public void renderGrid(ShapeRenderer shapeRenderer) {
            grid.accept(shapeRenderer);
        }

        @Override
        public void renderOverlays(ShapeRenderer shapeRenderer) {
            colliders.render(shapeRenderer);
            outline.render(shapeRenderer);
            skeleton.render(shapeRenderer);
            gizmo.render(shapeRenderer, gizmoScale.get());
        }
    }

    private final class PerspectivePane implements ViewportPaneView {
        private final PerspectiveCamera3D camera;
        private final ViewportCameraController3D controller;
        private final ViewportInteraction interaction;
        private final Runnable reset;
        private final boolean isMain;
        private final SceneViewportWidget widget;
        private final ViewportGridRenderer3D grid;
        private final SceneEditorWorkspace.PaneOverlays overlays;

        PerspectivePane(PerspectiveCamera3D camera, ViewportCameraController3D controller,
                        ViewportInteraction interaction, Runnable reset, boolean isMain) {
            this.camera = camera;
            this.controller = controller;
            this.interaction = interaction;
            this.reset = reset;
            this.isMain = isMain;
            this.widget = new SceneViewportWidget(interaction, camera::resize);
            this.grid = new ViewportGridRenderer3D(camera);
            this.overlays = new Overlays(grid::render,
                new TransformGizmoRenderer(editorState, gizmoController, camera),
                new SelectionOutlineRenderer(editorState, camera),
                new Collider3DRenderer(editorState, camera, showAllColliders),
                new SkeletonOverlayRenderer(editorState, camera, sceneRenderer3D), () -> 1f);
        }

        @Override
        public SceneViewportWidget getWidget() {
            return widget;
        }

        @Override
        public void render(Color background, float delta) {
            int width = Math.round(widget.getWidth());
            int height = Math.round(widget.getHeight());
            if (width > 0 && height > 0) {
                // The main camera is shared with the single view, which may have sized it differently
                camera.resize(width, height);
            }
            widget.renderScene(background, () -> workspace.renderPane3D(camera, overlays, delta));
        }

        @Override
        public void update(float delta) {
            // The editor updates the main interaction itself every frame; doing it here too would double the flying
            if (!isMain) {
                interaction.update(delta);
            }
        }

        @Override
        public void frame(Vector3 center, float radius) {
            float distance = ViewportFocus.perspectiveDistance(radius, camera.getFieldOfView());
            Vector3 direction = camera.getDirection().nor();
            camera.setPosition(new Vector3(center).mulAdd(direction, -distance));
            camera.update();
            controller.setTarget(center.x, center.y, center.z);
        }

        @Override
        public void resetView() {
            if (reset != null) {
                reset.run();
                return;
            }
            camera.setPosition(new Vector3(sceneCenter).add(NEW_CAMERA_OFFSET));
            camera.setUp(Vector3.Y);
            camera.lookAt(sceneCenter.x, sceneCenter.y, sceneCenter.z);
            camera.update();
            controller.setTarget(sceneCenter.x, sceneCenter.y, sceneCenter.z);
        }

        @Override
        public void dispose() {
            widget.dispose();
            if (isMain) {
                mainInUse = false;
            }
        }
    }

    private final class AxisPane implements ViewportPaneView {
        private final OrthographicViewCamera view;
        private final OrthographicViewInteraction axisInteraction;
        private final ViewportInteraction interaction;
        private final SceneViewportWidget widget;
        private final SceneEditorWorkspace.PaneOverlays overlays;
        private final AxisGridRenderer grid;

        AxisPane(ViewKind kind) {
            this.view = new OrthographicViewCamera(kind);
            this.axisInteraction = new OrthographicViewInteraction(editorState, view, gizmoController);
            this.interaction = new SculptViewportInteraction(editorState, sculptSession, view.getCamera(), axisInteraction);
            this.widget = new SceneViewportWidget(interaction, view::resize);
            this.grid = new AxisGridRenderer(view);
            this.overlays = new Overlays(grid::render,
                new TransformGizmoRenderer(editorState, gizmoController, view.getCamera(), true),
                new SelectionOutlineRenderer(editorState, view.getCamera()),
                new Collider3DRenderer(editorState, view.getCamera(), showAllColliders),
                new SkeletonOverlayRenderer(editorState, view.getCamera(), sceneRenderer3D), axisInteraction::gizmoScale);
            resetView();
        }

        @Override
        public SceneViewportWidget getWidget() {
            return widget;
        }

        @Override
        public void render(Color background, float delta) {
            widget.renderScene(background, () -> workspace.renderPane3D(view.getCamera(), overlays, delta));
        }

        @Override
        public void update(float delta) {
            interaction.update(delta);
        }

        @Override
        public void frame(Vector3 center, float radius) {
            view.frame(center, radius);
        }

        @Override
        public void resetView() {
            view.setFocus(sceneCenter);
            view.setZoom(AXIS_START_ZOOM);
        }

        @Override
        public void dispose() {
            widget.dispose();
        }
    }
}
