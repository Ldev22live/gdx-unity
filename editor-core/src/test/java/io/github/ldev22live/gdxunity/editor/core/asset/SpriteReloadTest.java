package io.github.ldev22live.gdxunity.editor.core.asset;

import com.badlogic.gdx.files.FileHandle;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3c.8: a sprite saved again (from 2D Studio, or outside the editor) reaches the World. Its users in the scene load the file
 * again on the next draw, instead of keeping the old look until the scene is reopened.
 */
class SpriteReloadTest {
    private static final int RED = 0xFF0000FF;
    private static final int BLUE = 0x0000FFFF;

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private Path write(String name, int rgba) throws IOException {
        SpriteDocument doc = new SpriteDocument(2, 2);
        doc.setPixel(0, 0, 0, 0, rgba);
        Path file = project.resolve(name);
        Files.writeString(file, SpriteFile.write(doc));
        return file;
    }

    @Test
    void shouldReloadEverySpriteAndTileMapThatUsesTheChangedFileAndNothingElse() throws IOException {
        Path heroFile = write("hero.sprite", RED);
        Path coinFile = write("coin.sprite", RED);
        Scene scene = new Scene("Level");
        Entity hero = scene.createEntity("Hero");
        SpriteAnimatorComponent heroSprite = new SpriteAnimatorComponent(new AssetReference("sprites/hero.sprite"));
        hero.addComponent(heroSprite);
        Entity coin = scene.createEntity("Coin");
        SpriteAnimatorComponent coinSprite = new SpriteAnimatorComponent(new AssetReference("sprites/coin.sprite"));
        coin.addComponent(coinSprite);
        Entity ground = scene.createEntity("Ground");
        TilemapComponent tiles = new TilemapComponent();
        tiles.setTileSetReference(new AssetReference("sprites/hero.sprite"));
        ground.addComponent(tiles);
        heroSprite.load(new FileHandle(heroFile.toFile()));
        coinSprite.load(new FileHandle(coinFile.toFile()));
        tiles.load(new FileHandle(heroFile.toFile()));
        assertEquals(RED, heroSprite.getDocument().getPixel(0, 0, 0, 0));

        write("hero.sprite", BLUE); // saved again from 2D Studio
        assertEquals(RED, heroSprite.getDocument().getPixel(0, 0, 0, 0), "without a reload the World keeps the old look");

        assertEquals(2, SpriteReload.reloadUsers(scene, "sprites\\hero.sprite"), "the sprite and the tile map that use it");

        assertTrue(heroSprite.needsLoad(), "the next draw reads the file again");
        assertTrue(tiles.needsLoad());
        assertFalse(coinSprite.needsLoad(), "another sprite is left alone");
        heroSprite.load(new FileHandle(heroFile.toFile())); // what the renderer does on that draw
        tiles.load(new FileHandle(heroFile.toFile()));
        assertEquals(BLUE, heroSprite.getDocument().getPixel(0, 0, 0, 0), "the World shows the edit");
        assertEquals(BLUE, tiles.getTileSet().getPixel(0, 0, 0, 0));
        assertEquals(0, SpriteReload.reloadUsers(null, "sprites/hero.sprite"));
    }
}
