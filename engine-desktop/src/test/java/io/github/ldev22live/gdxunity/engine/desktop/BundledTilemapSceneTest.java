package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.sprite.AutoTileRule;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample scene {@code samples/tilemap-platformer-2d.json}: a tile map level (auto-tiled ground and platforms on a brick background) with two sprite
 * heroes that fall onto it. The scene is built here in code; run with {@code GDXUNITY_WRITE_SAMPLES=1} to write the file again (entity ids are new each time, so the
 * check compares the level, not the ids).
 */
class BundledTilemapSceneTest {
    private static final Path SCENE = Path.of("..", "assets", "samples", "tilemap-platformer-2d.json");
    private static final int COLS = 20;
    private static final int ROWS = 12;
    private static final float TILE = 32f;
    private static final int BRICK = 17;
    private static final int AUTO_FIRST = 1;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
        PhysicsSystem2D.ensureNativesInitialized();
    }

    private static TileMapData level() {
        TileMapData map = new TileMapData(COLS, ROWS);
        map.loadLayers(List.of("Background", "Ground"), List.of(true, true), List.of(new int[COLS * ROWS], new int[COLS * ROWS]));
        int[] tiles = new int[AutoTileRule.VARIANTS];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = AUTO_FIRST + i;
            map.setSolid(AUTO_FIRST + i, true);
        }
        AutoTileRule ground = new AutoTileRule("ground", tiles);
        map.putRule(ground);
        for (int r = 0; r < ROWS - 2; r++) {
            for (int c = 0; c < COLS; c++) {
                map.set(0, c, r, BRICK);
            }
        }
        for (int c = 0; c < COLS; c++) {
            map.paintAuto(1, c, ROWS - 1, ground);
            map.paintAuto(1, c, ROWS - 2, ground);
        }
        for (int c = 4; c <= 8; c++) {
            map.paintAuto(1, c, 7, ground);
        }
        for (int c = 12; c <= 16; c++) {
            map.paintAuto(1, c, 5, ground);
        }
        map.paintAuto(1, 16, 6, ground);
        map.paintAuto(1, 16, 7, ground);
        map.paintAuto(1, 16, 8, ground);
        return map;
    }

    private static Entity entity(Scene scene, String name, float x, float y) {
        Entity e = scene.createEntity(name);
        e.getTransform().setLocalPosition(x, y, 0f);
        return e;
    }

    private static Scene scene() {
        Scene scene = new Scene("Tilemap Platformer 2D");
        scene.setGameMode(GameMode.MODE_2D);
        ShapeRendererComponent sky = new ShapeRendererComponent(COLS * TILE, ROWS * TILE, new Color(0.1f, 0.11f, 0.15f, 1f));
        entity(scene, "Sky", COLS * TILE / 2f, ROWS * TILE / 2f).addComponent(sky);
        CameraComponent camera = new CameraComponent(CameraComponent.Projection.ORTHOGRAPHIC);
        camera.setOrthographicHeight(ROWS * TILE);
        entity(scene, "Main Camera", COLS * TILE / 2f, ROWS * TILE / 2f).addComponent(camera);

        TilemapComponent tilemap = new TilemapComponent(new AssetReference("sprites/tiles.sprite"));
        tilemap.setTileSize(TILE, TILE);
        tilemap.setMap(level());
        entity(scene, "Level", 0f, 0f).addComponent(tilemap);

        for (float[] spec : new float[][] {{200f, 300f, 0f}, {470f, 330f, 1f}}) {
            Entity hero = entity(scene, spec[2] == 0f ? "Idle Hero" : "Walking Hero", spec[0], spec[1]);
            SpriteAnimatorComponent animator = new SpriteAnimatorComponent(new AssetReference("sprites/hero.sprite"));
            animator.setTag(spec[2] == 0f ? "idle" : "walk");
            animator.setWidth(64f);
            animator.setHeight(64f);
            hero.addComponent(animator);
            RigidBody2DComponent body = new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC);
            body.setFixedRotation(true);
            hero.addComponent(body);
            hero.addComponent(new BoxCollider2DComponent(36f, 60f));
        }
        return scene;
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return new SceneSerializer(registry);
    }

    @Test
    void shouldMatchTheBundledSceneFile() throws IOException {
        SceneSerializer serializer = serializer();
        if ("1".equals(System.getenv("GDXUNITY_WRITE_SAMPLES"))) {
            Files.writeString(SCENE, serializer.toJson(scene()), StandardCharsets.UTF_8);
        }

        assertTrue(Files.isRegularFile(SCENE), "missing " + SCENE + "; run with GDXUNITY_WRITE_SAMPLES=1");
        Scene onDisk = serializer.fromJson(Files.readString(SCENE, StandardCharsets.UTF_8));
        assertEquals(GameMode.MODE_2D, onDisk.getGameMode());
        TilemapComponent tilemap = onDisk.findEntityByName("Level").getComponent(TilemapComponent.class);
        assertNotNull(tilemap);
        assertTrue(level().contentEquals(tilemap.getMap()), "the level in tilemap-platformer-2d.json is out of date");
        assertEquals("sprites/tiles.sprite", tilemap.getTileSetReference().getPath());
        assertEquals(5, onDisk.getEntityCount());
    }

    @Test
    void shouldLetBothHeroesLandOnTheLevelInThePhysicsWorld() {
        Scene scene = scene();
        PhysicsSystem2D physics = new PhysicsSystem2D(scene);
        try {
            for (int i = 0; i < 300; i++) {
                physics.update(1f / 60f);
            }
        } finally {
            physics.dispose();
        }

        // The first hero falls onto the low platform (row 7: top at y = 160), the second onto the high one (row 5: top at y = 224); half the 60 unit collider is 30
        float idleY = scene.findEntityByName("Idle Hero").getTransform().getWorldPosition().y;
        assertEquals(160f + 30f, idleY, 3f, "the first hero stands on the low platform");
        float walkingY = scene.findEntityByName("Walking Hero").getTransform().getWorldPosition().y;
        assertEquals(224f + 30f, walkingY, 3f, "the second hero stands on the high platform");
    }
}
