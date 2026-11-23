package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.render.GdxTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.CameraComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TextComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Camera and Text components (TD-034): validation, text layout and serialization. */
class GameComponentsTest {

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @Test
    void shouldRejectInvalidCameraSettings() {
        CameraComponent camera = new CameraComponent();
        assertThrows(IllegalArgumentException.class, () -> camera.setOrthographicHeight(0f));
        assertThrows(IllegalArgumentException.class, () -> camera.setFieldOfView(180f));
        assertThrows(IllegalArgumentException.class, () -> camera.setClipPlanes(10f, 5f));
        assertThrows(IllegalArgumentException.class, () -> camera.setProjection(null));
    }

    @Test
    void shouldRejectInvalidTextSettings() {
        TextComponent text = new TextComponent();
        assertThrows(IllegalArgumentException.class, () -> text.setText(null));
        assertThrows(IllegalArgumentException.class, () -> text.setScale(-1f));
        assertThrows(IllegalArgumentException.class, () -> text.setOffset(Float.NaN, 0f));
    }

    @Test
    void shouldPlaceTextAtEachAnchorOfTheView() {
        // A 100 x 20 text in an 800 x 600 view; left and top edges before the offset
        assertEquals(0f, TextComponent.Anchor.TOP_LEFT.textLeft(800f, 100f));
        assertEquals(600f, TextComponent.Anchor.TOP_LEFT.textTop(600f, 20f));
        assertEquals(350f, TextComponent.Anchor.CENTER.textLeft(800f, 100f));
        assertEquals(310f, TextComponent.Anchor.CENTER.textTop(600f, 20f));
        assertEquals(700f, TextComponent.Anchor.BOTTOM_RIGHT.textLeft(800f, 100f));
        assertEquals(20f, TextComponent.Anchor.BOTTOM_RIGHT.textTop(600f, 20f));
    }

    @Test
    void shouldRoundTripCameraAndText() {
        CameraComponent camera = new CameraComponent(CameraComponent.Projection.PERSPECTIVE);
        camera.setOrthographicHeight(720f);
        camera.setFieldOfView(75f);
        camera.setClipPlanes(5f, 8000f);
        CameraComponent cameraCopy = roundTrip(new CameraComponentSerializer(), camera);
        assertEquals(CameraComponent.Projection.PERSPECTIVE, cameraCopy.getProjection());
        assertEquals(720f, cameraCopy.getOrthographicHeight());
        assertEquals(75f, cameraCopy.getFieldOfView());
        assertEquals(5f, cameraCopy.getNearPlane());
        assertEquals(8000f, cameraCopy.getFarPlane());

        TextComponent text = new TextComponent("Score: 12");
        text.setColor(new Color(1f, 0.5f, 0.25f, 0.75f));
        text.setScale(2.5f);
        text.setAnchor(TextComponent.Anchor.BOTTOM);
        text.setOffset(-4f, 30f);
        TextComponent textCopy = roundTrip(new TextComponentSerializer(), text);
        assertEquals("Score: 12", textCopy.getText());
        assertEquals(new Color(1f, 0.5f, 0.25f, 0.75f), textCopy.getColor());
        assertEquals(2.5f, textCopy.getScale());
        assertEquals(TextComponent.Anchor.BOTTOM, textCopy.getAnchor());
        assertEquals(-4f, textCopy.getOffsetX());
        assertEquals(30f, textCopy.getOffsetY());
    }

    @Test
    void shouldReportUnknownEnumValuesAsSceneErrors() {
        JsonValue badCamera = new JsonReader().parse("{\"projection\":\"FISHEYE\"}");
        assertThrows(SceneSerializationException.class, () -> new CameraComponentSerializer().deserialize(badCamera, new Json()));
        JsonValue badText = new JsonReader().parse("{\"text\":\"x\",\"anchor\":\"MIDDLE\"}");
        assertThrows(SceneSerializationException.class, () -> new TextComponentSerializer().deserialize(badText, new Json()));
    }

    private static <T extends io.github.ldev22live.gdxunity.engine.scene.Component> T roundTrip(
            ComponentSerializer<T> serializer, T component) {
        StringWriter out = new StringWriter();
        Json json = new Json(JsonWriter.OutputType.json);
        json.setWriter(out);
        json.writeObjectStart();
        serializer.serialize(component, json);
        json.writeObjectEnd();
        return serializer.deserialize(new JsonReader().parse(out.toString()), new Json());
    }
}
