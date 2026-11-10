package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;
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
 * The two scripted sprite samples (TD-066, milestone F): {@code pixel-adventure-2d} (a 2D pixel-art platformer on a tile map) and {@code sprite-25d} (the same
 * level and hero in a 3D world seen from the side, with 3D scenery in front of and behind the 2D plane). Built here in code; run with
 * {@code GDXUNITY_WRITE_SAMPLES=1} to write the files again. The check compares the level and what the hero needs, not the entity ids.
 */
class BundledPixelScenesTest {
    private static final Path PIXEL = Path.of("..", "assets", "samples", "pixel-adventure-2d.json");
    private static final Path SIDE = Path.of("..", "assets", "samples", "sprite-25d.json");
    private static final int COLS = 40;
    private static final int ROWS = 12;
    private static final float TILE = 32f;
    private static final int BRICK = 17;
    private static final int AUTO_FIRST = 1;
    private static final float START_X = 80f;
    private static final float START_Y = 100f;
    private static final String SCRIPT = "scripts/samples/pixel/PixelHero.java";
    private static final String SCRIPT_CLASS = "samples.pixel.PixelHero";

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** Row 11 is the bottom row; the floor is rows 10 and 11 with two pits, and platforms on row 7 float above it (64 units of headroom, the hero is 56 tall). */
    static TileMapData level() {
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
            boolean pit = (c >= 14 && c <= 16) || (c >= 28 && c <= 29);
            if (!pit) {
                map.paintAuto(1, c, ROWS - 1, ground);
                map.paintAuto(1, c, ROWS - 2, ground);
            }
        }
        for (int c = 6; c <= 9; c++) {
            map.paintAuto(1, c, 7, ground);
        }
        for (int c = 19; c <= 23; c++) {
            map.paintAuto(1, c, 7, ground);
        }
        for (int c = 31; c <= 35; c++) {
            map.paintAuto(1, c, 7, ground);
        }
        for (int r = 7; r <= 9; r++) {
            map.paintAuto(1, 39, r, ground);
        }
        return map;
    }

    private static Entity entity(Scene scene, String name, float x, float y, float z) {
        Entity e = scene.createEntity(name);
        e.getTransform().setLocalPosition(x, y, z);
        return e;
    }

    /** World position of the centre of a cell. */
    private static float cellX(int col) {
        return (col + 0.5f) * TILE;
    }

    private static float cellY(int row) {
        return ROWS * TILE - (row + 0.5f) * TILE;
    }

    private static void common(Scene scene, boolean perspective) {
        float width = COLS * TILE;
        float height = ROWS * TILE;
        ShapeRendererComponent sky = new ShapeRendererComponent(perspective ? width * 3f : width, perspective ? height * 3f : height, new Color(0.1f, 0.11f, 0.15f, 1f));
        entity(scene, "Sky", width / 2f, height / 2f, perspective ? -600f : -1f).addComponent(sky);

        CameraComponent camera = new CameraComponent(perspective ? CameraComponent.Projection.PERSPECTIVE : CameraComponent.Projection.ORTHOGRAPHIC);
        if (perspective) {
            camera.setFieldOfView(50f);
        } else {
            camera.setOrthographicHeight(height);
        }
        // Start where the hero's follow clamp puts the camera, so the first frame does not slide
        float cameraX = height * 16f / 9f / 2f;
        entity(scene, "Main Camera", cameraX, height / 2f, perspective ? 560f : 0f).addComponent(camera);

        TilemapComponent tilemap = new TilemapComponent(new AssetReference("sprites/tiles.sprite"));
        tilemap.setTileSize(TILE, TILE);
        tilemap.setMap(level());
        entity(scene, "Level", 0f, 0f, 0f).addComponent(tilemap);

        Entity hero = entity(scene, "Player", START_X, START_Y, 0f);
        SpriteAnimatorComponent animator = new SpriteAnimatorComponent(new AssetReference("sprites/hero.sprite"));
        animator.setTag("idle");
        animator.setWidth(64f);
        animator.setHeight(64f);
        hero.addComponent(animator);
        hero.addComponent(new EntityScriptBindingComponent(SCRIPT, SCRIPT_CLASS, ScriptLanguage.JAVA));

        int[][] coinCells = {{7, 6}, {8, 6}, {21, 6}, {22, 6}, {33, 6}, {20, 9}};
        int n = 1;
        for (int[] cell : coinCells) {
            Entity coin = entity(scene, "Coin " + n++, cellX(cell[0]), cellY(cell[1]), 0f);
            SpriteAnimatorComponent spin = new SpriteAnimatorComponent(new AssetReference("sprites/coin.sprite"));
            spin.setTag("spin");
            spin.setWidth(32f);
            spin.setHeight(32f);
            coin.addComponent(spin);
        }

        Entity score = entity(scene, "Score Text", 0f, 0f, 0f);
        TextComponent text = new TextComponent("Coins: 0 / " + coinCells.length);
        text.setScale(1.5f);
        text.setOffset(16f, -16f);
        score.addComponent(text);
        Entity help = entity(scene, "Help Text", 0f, 0f, 0f);
        TextComponent helpText = new TextComponent("A/D or arrows run, Space jumps, R restarts");
        helpText.setAnchor(TextComponent.Anchor.BOTTOM_LEFT);
        helpText.setOffset(16f, 16f);
        help.addComponent(helpText);

        for (String[] cue : new String[][] {{"jump", "Jump"}, {"coin", "Coin"}, {"hurt", "Hurt"}}) {
            entity(scene, "Sound " + cue[0], 0f, 0f, 0f).addComponent(new AudioSourceComponent(cue[0], cue[1]));
        }
    }

    static Scene pixelAdventure() {
        Scene scene = new Scene("Pixel Adventure 2D");
        scene.setGameMode(GameMode.MODE_2D);
        common(scene, false);
        return scene;
    }

    static Scene sprite25d() {
        Scene scene = new Scene("Sprite 2.5D");
        scene.setGameMode(GameMode.MODE_2_5D);
        common(scene, true);
        entity(scene, "Sun", 0f, 0f, 0f).addComponent(new DirectionalLightComponent());
        // Scenery in 3D: hills far behind the 2D plane and pillars in front of it, so the camera shows depth as it follows the hero sideways
        float[][] hills = {{200f, 120f, -300f, 360f, 240f}, {620f, 160f, -420f, 420f, 320f}, {1050f, 110f, -330f, 380f, 220f}};
        int n = 1;
        for (float[] h : hills) {
            entity(scene, "Hill " + n++, h[0], h[1], h[2]).addComponent(
                new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, h[3], h[4], 120f, new Color(0.2f, 0.28f, 0.45f, 1f)));
        }
        float[] pillars = {640f, 1000f, 1240f};
        n = 1;
        for (float x : pillars) {
            entity(scene, "Pillar " + n++, x, 200f, 140f).addComponent(
                new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 40f, 400f, 40f, new Color(0.45f, 0.3f, 0.25f, 1f)));
        }
        return scene;
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return new SceneSerializer(registry);
    }

    private static Scene check(Path file, Scene expected, GameMode mode) throws IOException {
        SceneSerializer serializer = serializer();
        if ("1".equals(System.getenv("GDXUNITY_WRITE_SAMPLES"))) {
            Files.writeString(file, serializer.toJson(expected), StandardCharsets.UTF_8);
        }
        assertTrue(Files.isRegularFile(file), "missing " + file + "; run with GDXUNITY_WRITE_SAMPLES=1");
        Scene onDisk = serializer.fromJson(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals(mode, onDisk.getGameMode());
        assertEquals(expected.getEntityCount(), onDisk.getEntityCount(), file + " is out of date");
        TilemapComponent tilemap = onDisk.findEntityByName("Level").getComponent(TilemapComponent.class);
        assertTrue(level().contentEquals(tilemap.getMap()), "the level in " + file + " is out of date");
        EntityScriptBindingComponent binding = onDisk.findEntityByName("Player").getComponent(EntityScriptBindingComponent.class);
        assertEquals(SCRIPT_CLASS, binding.getScriptClassName());
        assertNotNull(onDisk.findEntityByName("Score Text").getComponent(TextComponent.class));
        return onDisk;
    }

    @Test
    void shouldMatchTheBundledPixelAdventureFile() throws IOException {
        Scene scene = check(PIXEL, pixelAdventure(), GameMode.MODE_2D);

        assertEquals(CameraComponent.Projection.ORTHOGRAPHIC, scene.findEntityByName("Main Camera").getComponent(CameraComponent.class).getProjection());
    }

    @Test
    void shouldMatchTheBundledSprite25dFileAndCarryScenery() throws IOException {
        Scene scene = check(SIDE, sprite25d(), GameMode.MODE_2_5D);

        assertEquals(CameraComponent.Projection.PERSPECTIVE, scene.findEntityByName("Main Camera").getComponent(CameraComponent.class).getProjection());
        assertNotNull(scene.findEntityByName("Hill 1").getComponent(MeshPrimitiveComponent.class));
        assertTrue(scene.findEntityByName("Pillar 1").getTransform().getWorldPosition().z > 0f, "a pillar stands in front of the 2D plane");
    }

    @Test
    void shouldGiveTheLevelPitsAFloorAndPlatformsToStandOn() {
        TileMapData map = level();

        assertTrue(map.isSolid(map.get(1, 0, ROWS - 1)), "floor at the start");
        assertEquals(TileMapData.EMPTY, map.get(1, 15, ROWS - 1), "a pit");
        assertTrue(map.isSolid(map.get(1, 7, 7)), "the first platform");
        assertTrue(START_Y - 28f > 2 * TILE, "the hero starts above the floor");
    }
}
