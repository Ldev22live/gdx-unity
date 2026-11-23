package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.SpriteAnimatorComponentSerializer;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteFile;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteAnimatorComponentTest {
    @TempDir
    File dir;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static SpriteDocument sprite() {
        SpriteDocument doc = new SpriteDocument(2, 2);
        for (int i = 1; i < 4; i++) {
            doc.insertFrame(i, -1);
        }
        for (int i = 0; i < 4; i++) {
            doc.setFrameDuration(i, 100);
        }
        doc.setTags(List.of(new SpriteTag("idle", 0, 1, SpriteTag.Direction.FORWARD), new SpriteTag("run", 2, 3, SpriteTag.Direction.FORWARD)));
        return doc;
    }

    @Test
    void shouldPlayATagWhenTheSceneUpdatesAndKeepPlayingItWhenAskedEveryFrame() {
        SpriteAnimatorComponent animator = new SpriteAnimatorComponent();
        animator.setDocument(sprite());

        animator.play("run");
        animator.onUpdate(0.15f);
        animator.play("run");

        assertEquals(3, animator.getFrame());
        assertEquals("run", animator.getCurrentTag());
        assertTrue(animator.isPlaying());
    }

    @Test
    void shouldStartWithTheConfiguredTagAndPausedWhenPlayOnStartIsOff() {
        SpriteAnimatorComponent animator = new SpriteAnimatorComponent();
        animator.setTag("run");
        animator.setPlayOnStart(false);
        animator.setDocument(sprite());

        animator.onUpdate(1f);

        assertEquals(2, animator.getFrame());
        assertFalse(animator.isPlaying());
        animator.resume();
        animator.onUpdate(0.1f);
        assertEquals(3, animator.getFrame());
    }

    @Test
    void shouldRememberAPlayRequestMadeBeforeTheSpriteIsLoaded() {
        SpriteAnimatorComponent animator = new SpriteAnimatorComponent();

        animator.play("run");
        animator.setDocument(sprite());

        assertEquals(2, animator.getFrame());
    }

    @Test
    void shouldFinishANonLoopingAnimationAndApplySpeed() {
        SpriteAnimatorComponent animator = new SpriteAnimatorComponent();
        animator.setLoop(false);
        animator.setSpeed(2f);
        animator.setDocument(sprite());
        animator.play("idle");

        animator.onUpdate(0.05f);
        assertFalse(animator.isFinished());
        animator.onUpdate(1f);

        assertTrue(animator.isFinished());
        assertEquals(1, animator.getFrame());
        assertThrows(IllegalArgumentException.class, () -> animator.setSpeed(-1f));
    }

    @Test
    void shouldFailLoudlyForAnUnknownTagAndWhenNothingIsLoaded() {
        SpriteAnimatorComponent animator = new SpriteAnimatorComponent();
        assertThrows(IllegalStateException.class, animator::getFrame);

        animator.setDocument(sprite());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> animator.play("fly"));
        assertTrue(e.getMessage().contains("idle, run"), e.getMessage());
    }

    @Test
    void shouldReportAConfiguredTagThatTheSpriteDoesNotHave() {
        SpriteAnimatorComponent animator = new SpriteAnimatorComponent();
        animator.setTag("fly");

        animator.setDocument(sprite());

        assertNotNull(animator.getLoadError());
        assertNull(animator.getDocument());
        assertFalse(animator.isSprite());
    }

    @Test
    void shouldLoadASpriteFileAndReportAMissingOrBrokenOne() throws IOException {
        File good = new File(dir, "hero.sprite");
        Files.writeString(good.toPath(), SpriteFile.write(sprite()));
        SpriteAnimatorComponent animator = new SpriteAnimatorComponent(new AssetReference("sprites/hero.sprite"));
        assertTrue(animator.needsLoad());

        animator.load(new FileHandle(good));

        assertFalse(animator.needsLoad());
        assertTrue(animator.isSprite());
        assertEquals(4, animator.getDocument().getFrameCount());

        SpriteAnimatorComponent missing = new SpriteAnimatorComponent(new AssetReference("sprites/none.sprite"));
        missing.load(new FileHandle(new File(dir, "none.sprite")));
        assertTrue(missing.getLoadError().contains("not found"), missing.getLoadError());
        assertFalse(missing.needsLoad(), "a failed load is not retried every frame");

        File bad = new File(dir, "bad.sprite");
        Files.writeString(bad.toPath(), "{ not a sprite");
        SpriteAnimatorComponent broken = new SpriteAnimatorComponent(new AssetReference("sprites/bad.sprite"));
        broken.load(new FileHandle(bad));
        assertTrue(broken.getLoadError().contains("bad.sprite"), broken.getLoadError());
    }

    @Test
    void shouldRoundTripTheSettingsThroughItsSerializer() {
        SpriteAnimatorComponentSerializer serializer = new SpriteAnimatorComponentSerializer();
        SpriteAnimatorComponent original = new SpriteAnimatorComponent(new AssetReference("sprites/hero.sprite"));
        original.setTag("run");
        original.setSpeed(1.5f);
        original.setLoop(false);
        original.setPlayOnStart(false);
        original.setFlipX(true);
        original.setFlipY(true);
        original.setWidth(64f);
        original.setHeight(48f);
        original.setCentered(false);
        original.setTint(new Color(1f, 0.5f, 0.25f, 0.75f));

        StringWriter text = new StringWriter();
        Json json = new Json();
        json.setWriter(new JsonWriter(text));
        json.writeObjectStart();
        serializer.serialize(original, json);
        json.writeObjectEnd();
        SpriteAnimatorComponent back = serializer.deserialize(new JsonReader().parse(text.toString()), json);

        assertEquals("sprites/hero.sprite", back.getAssetReference().getPath());
        assertEquals("run", back.getTag());
        assertEquals(1.5f, back.getSpeed());
        assertFalse(back.isLoop());
        assertFalse(back.isPlayOnStart());
        assertTrue(back.isFlipX() && back.isFlipY());
        assertEquals(64f, back.getWidth());
        assertEquals(48f, back.getHeight());
        assertFalse(back.isCentered());
        assertEquals(new Color(1f, 0.5f, 0.25f, 0.75f), back.getTint());
    }

    @Test
    void shouldReadDefaultsForAnEmptyDescription() {
        SpriteAnimatorComponent back = new SpriteAnimatorComponentSerializer().deserialize(new JsonReader().parse("{}"), new Json());

        assertNull(back.getAssetReference());
        assertNull(back.getTag());
        assertEquals(1f, back.getSpeed());
        assertTrue(back.isLoop() && back.isPlayOnStart());
        assertEquals(0f, back.getWidth());
    }
}
