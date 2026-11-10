package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.ldev22live.gdxunity.engine.core.EngineRenderer;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Camera-driven 2D scene renderer.
 * Traverses active entities in the current {@link Scene}, extracts {@link Renderable} components,
 * and renders geometry transformed by entity world matrices and projected through the {@link Camera}.
 */
public class SceneRenderer2D implements EngineRenderer {
    private Camera camera;
    private Scene activeScene;
    private io.github.ldev22live.gdxunity.engine.core.asset.AssetService assetService;

    private ShapeRenderer shapeRenderer;
    private SpriteBatch spriteBatch;
    private boolean depthComposite = true;
    /** Moves a renderable to its entity's Z while drawing with depth (scratch matrix, no allocation per frame). */
    private final Matrix4 zShift = new Matrix4();

    private Color clearColor = new Color(0.12f, 0.14f, 0.18f, 1.0f);
    private boolean clearEnabled = true;
    private boolean initialized = false;
    private boolean disposed = false;

    public SceneRenderer2D() {
        this(new OrthographicCamera2D(), null);
    }

    public SceneRenderer2D(Camera camera) {
        this(camera, null);
    }

    public SceneRenderer2D(Camera camera, io.github.ldev22live.gdxunity.engine.core.asset.AssetService assetService) {
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
        this.assetService = assetService;
    }

    public io.github.ldev22live.gdxunity.engine.core.asset.AssetService getAssetService() {
        return assetService;
    }

    public void setAssetService(io.github.ldev22live.gdxunity.engine.core.asset.AssetService assetService) {
        this.assetService = assetService;
    }

    @Override
    public void initialize() {
        if (initialized || disposed) {
            return;
        }

        this.shapeRenderer = createShapeRenderer();
        this.spriteBatch = createSpriteBatch();
        this.initialized = true;
    }

    protected ShapeRenderer createShapeRenderer() {
        return new ShapeRenderer();
    }

    protected SpriteBatch createSpriteBatch() {
        return new SpriteBatch();
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null) {
            camera.resize(width, height);
        }
    }

    @Override
    public void render(float deltaTime) {
        if (!initialized || disposed) {
            return;
        }

        // 1. Clear frame buffer (disabled when the host clears and draws a background such as a grid first)
        if (clearEnabled && Gdx.gl != null) {
            Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }

        // 2. Update camera projection and restore this camera's GL viewport. Other passes in the same
        // frame (e.g. the editor UI stage) apply their own viewports, so it must be re-applied every frame
        // or the scene is drawn with the wrong viewport and no longer matches picking coordinates.
        if (camera != null) {
            camera.update();
            if (Gdx.gl != null && camera.getViewport() != null) {
                camera.getViewport().apply();
            }
        }

        // 3. Skip scene rendering if no scene or scene is inactive
        if (activeScene == null || !activeScene.isActive()) {
            return;
        }

        // 4. Collect renderables from active entities
        List<Renderable> primitivesToRender = new ArrayList<>();
        List<Renderable> spritesToRender = new ArrayList<>();

        collectRenderables(activeScene.getAllEntities(), primitivesToRender, spritesToRender, assetService);

        Matrix4 combinedMatrix = (camera != null) ? camera.getCombinedMatrix() : new Matrix4();

        // 5. Draw primitives and 6. sprites. Seen through a perspective camera (the 3D view, a 3D game) they are tested against the
        // depth the 3D pass left, at the entity's own world Z, so a wall in front of a sprite hides it (Phase 13-D).
        boolean depth = isDepthCompositeActive() && !(primitivesToRender.isEmpty() && spritesToRender.isEmpty());
        if (depth) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
            // Tested but not written: sprites keep their painter's order among themselves, and transparent edges leave no halo
            Gdx.gl.glDepthMask(false);
        }
        try {
            drawPrimitives(primitivesToRender, combinedMatrix, depth);
            drawSprites(spritesToRender, combinedMatrix, depth);
        } finally {
            if (depth) {
                Gdx.gl.glDepthMask(true);
                Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            }
        }
    }

    private void drawPrimitives(List<Renderable> primitives, Matrix4 combinedMatrix, boolean depth) {
        if (primitives.isEmpty() || shapeRenderer == null) {
            return;
        }
        shapeRenderer.setProjectionMatrix(combinedMatrix);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        try {
            float z = 0f;
            for (int i = 0; i < primitives.size(); i++) {
                Renderable primitive = primitives.get(i);
                if (depth) {
                    float entityZ = worldZ(primitive);
                    if (entityZ != z) {
                        z = entityZ;
                        shapeRenderer.setTransformMatrix(zShift.setToTranslation(0f, 0f, z));
                    }
                }
                primitive.render(shapeRenderer);
            }
        } finally {
            shapeRenderer.end();
            // Hosts draw overlays with this renderer next: leave it as it was found
            shapeRenderer.setTransformMatrix(zShift.idt());
        }
    }

    private void drawSprites(List<Renderable> sprites, Matrix4 combinedMatrix, boolean depth) {
        if (sprites.isEmpty() || spriteBatch == null) {
            return;
        }
        spriteBatch.setProjectionMatrix(combinedMatrix);
        spriteBatch.begin();
        try {
            float z = 0f;
            for (int i = 0; i < sprites.size(); i++) {
                Renderable sprite = sprites.get(i);
                if (depth) {
                    float entityZ = worldZ(sprite);
                    if (entityZ != z) {
                        z = entityZ;
                        spriteBatch.setTransformMatrix(zShift.setToTranslation(0f, 0f, z));
                    }
                }
                sprite.render(spriteBatch);
            }
        } finally {
            spriteBatch.end();
            spriteBatch.setTransformMatrix(zShift.idt());
        }
    }

    /** The world Z of the entity a renderable belongs to; 0 for one that is not a component. */
    private static float worldZ(Renderable renderable) {
        if (renderable instanceof Component component && component.getEntity() != null) {
            return component.getEntity().getTransform().getWorldPosition().z;
        }
        return 0f;
    }

    /**
     * Whether 2D renderables are depth tested against the 3D pass: on for a perspective camera, where depth means something. An
     * orthographic camera draws everything on one plane in painter's order, whatever the Z of the entities.
     */
    public boolean isDepthCompositeActive() {
        return depthComposite && Gdx.gl != null && camera instanceof PerspectiveCamera3D;
    }

    /** Turns the depth test against the 3D pass off or on (on by default, and only used with a perspective camera). */
    public void setDepthComposite(boolean depthComposite) {
        this.depthComposite = depthComposite;
    }

    /**
     * Traverses the entity list and gathers renderables from entities that are active in hierarchy.
     */
    public static void collectRenderables(List<Entity> entities, List<Renderable> primitives, List<Renderable> sprites) {
        collectRenderables(entities, primitives, sprites, null);
    }

    public static void collectRenderables(
        List<Entity> entities,
        List<Renderable> primitives,
        List<Renderable> sprites,
        io.github.ldev22live.gdxunity.engine.core.asset.AssetService assetService
    ) {
        if (entities == null) return;
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (!entity.isActiveInHierarchy()) {
                continue;
            }
            List<Component> components = entity.getAllComponents();
            for (int j = 0; j < components.size(); j++) {
                Component c = components.get(j);
                if (c.isEnabled() && c instanceof Renderable renderable) {
                    if (renderable.isPrimitive()) {
                        primitives.add(renderable);
                    } else if (c instanceof io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent sprite) {
                        if (sprite.getTexture() == null && sprite.getAssetReference() != null && assetService != null) {
                            com.badlogic.gdx.graphics.Texture tex = assetService.loadTexture(sprite.getAssetReference());
                            if (tex != null) {
                                sprite.setTexture(tex);
                            }
                        }
                        if (sprite.isSprite()) {
                            sprites.add(sprite);
                        }
                    } else if (c instanceof io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent tilemap) {
                        if (tilemap.needsLoad() && assetService != null) {
                            tilemap.load(assetService.resolve(tilemap.getTileSetReference()));
                        }
                        if (tilemap.isSprite()) {
                            sprites.add(tilemap);
                        }
                    } else if (c instanceof io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent animator) {
                        if (animator.needsLoad() && assetService != null) {
                            animator.load(assetService.resolve(animator.getAssetReference()));
                        }
                        if (animator.isSprite()) {
                            sprites.add(animator);
                        }
                    } else if (renderable.isSprite()) {
                        sprites.add(renderable);
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        initialized = false;

        if (shapeRenderer != null) {
            try {
                shapeRenderer.dispose();
            } catch (Throwable t) {
                // Ignore disposal errors on shutdown
            } finally {
                shapeRenderer = null;
            }
        }

        if (spriteBatch != null) {
            try {
                spriteBatch.dispose();
            } catch (Throwable t) {
                // Ignore disposal errors on shutdown
            } finally {
                spriteBatch = null;
            }
        }
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
    }

    public Scene getActiveScene() {
        return activeScene;
    }

    public void setActiveScene(Scene activeScene) {
        this.activeScene = activeScene;
    }

    public Color getClearColor() {
        return clearColor;
    }

    public boolean isClearEnabled() {
        return clearEnabled;
    }

    /** Controls whether {@link #render(float)} clears the color buffer before drawing. Default true. */
    public void setClearEnabled(boolean clearEnabled) {
        this.clearEnabled = clearEnabled;
    }

    public void setClearColor(Color clearColor) {
        this.clearColor = Objects.requireNonNull(clearColor, "clearColor cannot be null");
    }

    public ShapeRenderer getShapeRenderer() {
        return shapeRenderer;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isDisposed() {
        return disposed;
    }
}
