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

    private Color clearColor = new Color(0.12f, 0.14f, 0.18f, 1.0f);
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

        // 1. Clear frame buffer
        if (Gdx.gl != null) {
            Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }

        // 2. Update camera projection
        if (camera != null) {
            camera.update();
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

        // 5. Draw primitives
        if (!primitivesToRender.isEmpty() && shapeRenderer != null) {
            shapeRenderer.setProjectionMatrix(combinedMatrix);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            try {
                for (int i = 0; i < primitivesToRender.size(); i++) {
                    primitivesToRender.get(i).render(shapeRenderer);
                }
            } finally {
                shapeRenderer.end();
            }
        }

        // 6. Draw sprites
        if (!spritesToRender.isEmpty() && spriteBatch != null) {
            spriteBatch.setProjectionMatrix(combinedMatrix);
            spriteBatch.begin();
            try {
                for (int i = 0; i < spritesToRender.size(); i++) {
                    spritesToRender.get(i).render(spriteBatch);
                }
            } finally {
                spriteBatch.end();
            }
        }
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
