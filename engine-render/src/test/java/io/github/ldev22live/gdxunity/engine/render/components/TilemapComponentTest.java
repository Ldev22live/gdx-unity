package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TilemapComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.StaticColliderSource2D;
import io.github.ldev22live.gdxunity.engine.sprite.AutoTileRule;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TilemapComponentTest {
    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
        PhysicsSystem2D.ensureNativesInitialized();
    }

    private static TilemapComponent floorMap(Entity entity) {
        TilemapComponent tilemap = new TilemapComponent();
        tilemap.setTileSize(32f, 32f);
        TileMapData map = new TileMapData(10, 4);
        map.setSolid(1, true);
        for (int c = 0; c < 10; c++) {
            map.set(0, c, 3, 1);
        }
        tilemap.setMap(map);
        entity.addComponent(tilemap);
        return tilemap;
    }

    @Test
    void shouldGiveSolidBoxesInWorldCoordinatesFromTheBottomLeftCorner() {
        Scene scene = new Scene("S");
        Entity entity = scene.createEntity("Level");
        entity.getTransform().setLocalPosition(100f, 50f, 0f);
        TilemapComponent tilemap = floorMap(entity);

        List<StaticColliderSource2D.StaticBox2D> boxes = tilemap.getStaticBoxes();

        assertEquals(1, boxes.size());
        StaticColliderSource2D.StaticBox2D floor = boxes.get(0);
        assertEquals(100f + 160f, floor.centerX(), 1e-3f, "ten cells of 32 from x = 100");
        assertEquals(50f + 16f, floor.centerY(), 1e-3f, "the bottom row of the map sits on y = 50");
        assertEquals(320f, floor.width(), 1e-3f);
        assertEquals(32f, floor.height(), 1e-3f);
    }

    @Test
    void shouldGiveNoBoxesWhenCollisionIsOffOrNothingIsSolid() {
        Scene scene = new Scene("S");
        TilemapComponent tilemap = floorMap(scene.createEntity("Level"));

        tilemap.setCollides(false);
        assertTrue(tilemap.getStaticBoxes().isEmpty());
        tilemap.setCollides(true);
        tilemap.getMap().setSolid(1, false);
        assertTrue(tilemap.getStaticBoxes().isEmpty());
    }

    @Test
    void shouldLetADynamicBodyLandOnTheSolidTilesInTheBox2DWorld() {
        Scene scene = new Scene("S");
        Entity level = scene.createEntity("Level");
        level.getTransform().setLocalPosition(0f, 0f, 0f);
        floorMap(level);
        Entity ball = scene.createEntity("Ball");
        ball.getTransform().setLocalPosition(160f, 300f, 0f);
        ball.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        ball.addComponent(new BoxCollider2DComponent(20f, 20f));

        PhysicsSystem2D physics = new PhysicsSystem2D(scene);
        try {
            for (int i = 0; i < 240; i++) {
                physics.update(1f / 60f);
            }
        } finally {
            physics.dispose();
        }

        float y = ball.getTransform().getWorldPosition().y;
        assertTrue(y > 32f && y < 60f, "rests on top of the 32 unit floor, but y = " + y);
        assertEquals(2, scene.getEntityCount());
    }

    @Test
    void shouldLetBodiesFallThroughWhenTheTilemapDoesNotCollide() {
        Scene scene = new Scene("S");
        floorMap(scene.createEntity("Level")).setCollides(false);
        Entity ball = scene.createEntity("Ball");
        ball.getTransform().setLocalPosition(160f, 300f, 0f);
        ball.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        ball.addComponent(new BoxCollider2DComponent(20f, 20f));

        PhysicsSystem2D physics = new PhysicsSystem2D(scene);
        try {
            for (int i = 0; i < 240; i++) {
                physics.update(1f / 60f);
            }
        } finally {
            physics.dispose();
        }

        assertTrue(ball.getTransform().getWorldPosition().y < 0f);
    }

    @Test
    void shouldRoundTripTheMapThroughItsSerializer() {
        TilemapComponentSerializer serializer = new TilemapComponentSerializer();
        TilemapComponent original = new TilemapComponent(new AssetReference("sprites/tiles.sprite"));
        original.setTileSize(16f, 24f);
        original.setCollides(false);
        original.setTint(new Color(0.5f, 0.5f, 1f, 1f));
        TileMapData map = new TileMapData(3, 2);
        map.set(0, 0, 0, 4);
        map.set(0, 2, 1, 9);
        map.addLayer("Decor");
        map.set(1, 1, 1, 6);
        map.setLayerVisible(1, false);
        map.setSolid(4, true);
        map.setSolid(9, true);
        int[] tiles = new int[16];
        java.util.Arrays.fill(tiles, 5);
        map.putRule(new AutoTileRule("grass", tiles));
        original.setMap(map);

        StringWriter text = new StringWriter();
        Json json = new Json();
        json.setWriter(new JsonWriter(text));
        json.writeObjectStart();
        serializer.serialize(original, json);
        json.writeObjectEnd();
        TilemapComponent back = serializer.deserialize(new JsonReader().parse(text.toString()), json);

        assertEquals("sprites/tiles.sprite", back.getTileSetReference().getPath());
        assertEquals(16f, back.getTileWidth());
        assertEquals(24f, back.getTileHeight());
        assertFalse(back.isCollides());
        assertTrue(map.contentEquals(back.getMap()));
        assertEquals(new Color(0.5f, 0.5f, 1f, 1f), back.getTint());
    }

    @Test
    void shouldReadDefaultsForAnEmptyDescriptionAndRefuseBadTileSizes() {
        TilemapComponent back = new TilemapComponentSerializer().deserialize(new JsonReader().parse("{}"), new Json());

        assertNull(back.getTileSetReference());
        assertEquals(TilemapComponent.DEFAULT_COLS, back.getMap().getCols());
        assertEquals(1, back.getMap().getLayerCount());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> back.setTileSize(0f, 8f));
    }
}
