package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelComponentTest {

    private static ModelComponent walking(boolean loop) {
        ModelComponent component = new ModelComponent("models/hero.glb");
        component.setAnimation("Walk");
        component.setLoop(loop);
        component.setModelInfo(new ModelComponent.ModelInfo(Map.of("Walk", 2f), null, 12, null));
        return component;
    }

    @Test
    void shouldWrapTimeWhenLooping() {
        ModelComponent component = walking(true);
        component.onStart();

        component.onUpdate(1.5f);
        component.onUpdate(1.0f);

        assertEquals(0.5f, component.getTime(), 1e-5f);
        assertTrue(component.isPlaying());
    }

    @Test
    void shouldStopAtEndWhenNotLooping() {
        ModelComponent component = walking(false);
        component.onStart();

        component.onUpdate(3f);

        assertEquals(2f, component.getTime(), 1e-5f);
        assertFalse(component.isPlaying());
    }

    @Test
    void shouldPlayBackwardsWithNegativeSpeed() {
        ModelComponent component = walking(true);
        component.setSpeed(-1f);
        component.onStart();

        component.onUpdate(0.5f);

        assertEquals(1.5f, component.getTime(), 1e-5f);
    }

    @Test
    void shouldNotPlayWhenPlayOnStartIsOff() {
        ModelComponent component = walking(true);
        component.setPlayOnStart(false);
        component.onStart();

        component.onUpdate(1f);

        assertEquals(0f, component.getTime());
    }

    @Test
    void shouldKeepTimeUntilClipLengthIsKnown() {
        ModelComponent component = new ModelComponent("models/hero.glb");
        component.setAnimation("Walk");
        component.setTime(5f);

        assertEquals(5f, component.getTime());

        component.setModelInfo(new ModelComponent.ModelInfo(Map.of("Walk", 2f), null, 0, null));

        assertEquals(1f, component.getTime(), 1e-5f);
    }

    @Test
    void shouldResetLoadedInfoWhenPathChanges() {
        ModelComponent component = walking(true);

        component.setModelPath("models/other.glb");

        assertEquals(ModelComponent.ModelInfo.NONE, component.getModelInfo());
    }

    @Test
    void shouldRejectInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new ModelComponent(" "));
        ModelComponent component = new ModelComponent("a.glb");
        assertThrows(IllegalArgumentException.class, () -> component.setImportScale(0f));
        assertThrows(IllegalArgumentException.class, () -> component.setSpeed(Float.NaN));
    }

    @Test
    void shouldRoundTripThroughSerializer() {
        ModelComponent original = new ModelComponent("models/hero.glb");
        original.setImportScale(2.5f);
        original.setAnimation("Run");
        original.setLoop(false);
        original.setPlayOnStart(false);
        original.setSpeed(-0.5f);
        ModelComponentSerializer serializer = new ModelComponentSerializer();

        ModelComponent copy = serializer.deserialize(new JsonReader().parse(write(serializer, original)), new Json());

        assertEquals("models/hero.glb", copy.getModelPath());
        assertEquals(2.5f, copy.getImportScale());
        assertEquals("Run", copy.getAnimation());
        assertFalse(copy.isLoop());
        assertFalse(copy.isPlayOnStart());
        assertEquals(-0.5f, copy.getSpeed());
    }

    @Test
    void shouldOmitAnimationWhenNoneIsSelected() {
        ModelComponentSerializer serializer = new ModelComponentSerializer();

        ModelComponent copy = serializer.deserialize(
            new JsonReader().parse(write(serializer, new ModelComponent("a.glb"))), new Json());

        assertNull(copy.getAnimation());
    }

    @Test
    void shouldRejectMissingPath() {
        assertThrows(SceneSerializationException.class,
            () -> new ModelComponentSerializer().deserialize(new JsonReader().parse("{}"), new Json()));
    }

    private static String write(ModelComponentSerializer serializer, ModelComponent component) {
        StringWriter out = new StringWriter();
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setWriter(out);
        json.writeObjectStart();
        serializer.serialize(component, json);
        json.writeObjectEnd();
        return out.toString();
    }
}
