package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.render.GdxTestHelper;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.DynamicMeshComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicMeshComponentTest {
    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    private static String write(DynamicMeshComponentSerializer serializer, DynamicMeshComponent component, Json json) {
        StringWriter out = new StringWriter();
        json.setWriter(new JsonWriter(out));
        json.writeObjectStart();
        serializer.serialize(component, json);
        json.writeObjectEnd();
        return out.toString();
    }

    @Test
    void shouldRejectABlankOrMissingPath() {
        assertThrows(IllegalArgumentException.class, () -> new DynamicMeshComponent(" "));
        assertThrows(NullPointerException.class, () -> new DynamicMeshComponent(null));
        assertThrows(NullPointerException.class, () -> new DynamicMeshComponent("a.sculpt").setColor(null));
    }

    @Test
    void shouldRoundTripThroughTheSerializer() {
        DynamicMeshComponent original = new DynamicMeshComponent("sculpts/rock.sculpt");
        original.setColor(new Color(0.1f, 0.2f, 0.3f, 0.5f));
        DynamicMeshComponentSerializer serializer = new DynamicMeshComponentSerializer();
        Json json = new Json();

        DynamicMeshComponent restored = serializer.deserialize(new JsonReader().parse(write(serializer, original, json)), json);

        assertEquals("sculpts/rock.sculpt", restored.getMeshPath());
        assertEquals(0.2f, restored.getColor().g, 1e-6f);
        assertEquals(0.5f, restored.getColor().a, 1e-6f);
    }

    @Test
    void shouldUseTheDefaultColourWhenTheFileHasNone() {
        DynamicMeshComponentSerializer serializer = new DynamicMeshComponentSerializer();

        DynamicMeshComponent restored = serializer.deserialize(new JsonReader().parse("{mesh: a.sculpt}"), new Json());

        assertEquals(new DynamicMeshComponent("a.sculpt").getColor(), restored.getColor());
    }

    @Test
    void shouldReportAFileWithoutAMeshPath() {
        DynamicMeshComponentSerializer serializer = new DynamicMeshComponentSerializer();

        SceneSerializationException missing = assertThrows(SceneSerializationException.class,
            () -> serializer.deserialize(new JsonReader().parse("{colorR: 1}"), new Json()));
        assertTrue(missing.getMessage().contains("requires 'mesh'"), missing.getMessage());
        assertThrows(SceneSerializationException.class,
            () -> serializer.deserialize(new JsonReader().parse("{mesh: \"  \"}"), new Json()));
    }

    @Test
    void shouldHandOutCopiesOfItsColourAndBounds() {
        DynamicMeshComponent component = new DynamicMeshComponent("a.sculpt");
        assertNull(component.getBounds(), "no bounds before the renderer reported any");

        component.setBounds(new BoundingBox(new Vector3(-1, -2, -3), new Vector3(1, 2, 3)));
        BoundingBox copy = component.getBounds();
        copy.max.set(99, 99, 99);

        assertEquals(3f, component.getBounds().max.z);
        assertNotSame(component.getColor(), component.getColor());
        component.setBounds(null);
        assertNull(component.getBounds());
    }

    @Test
    void shouldForgetItsBoundsWhenThePathChanges() {
        DynamicMeshComponent component = new DynamicMeshComponent("a.sculpt");
        component.setBounds(new BoundingBox(new Vector3(0, 0, 0), new Vector3(1, 1, 1)));

        component.setMeshPath("a.sculpt");
        assertNotNull(component.getBounds(), "the same path keeps what is known");
        component.setMeshPath("b.sculpt");

        assertNull(component.getBounds(), "a different mesh has other bounds");
    }

    @Test
    void shouldCollectEnabledMeshesOfActiveEntitiesWithTheirWorldTransform() {
        Scene scene = new Scene("Sculpts");
        Entity rock = scene.createEntity("Rock");
        rock.getTransform().setLocalPosition(10f, 20f, 30f);
        DynamicMeshComponent shown = rock.addComponent(new DynamicMeshComponent("rock.sculpt"));
        Entity off = scene.createEntity("Off");
        off.addComponent(new DynamicMeshComponent("off.sculpt")).setEnabled(false);
        Entity hidden = scene.createEntity("Hidden");
        hidden.addComponent(new DynamicMeshComponent("hidden.sculpt"));
        hidden.setActive(false);

        List<SceneRenderer3D.DynamicMeshDraw> draws = SceneRenderer3D.collectDynamicMeshes(scene);

        assertEquals(1, draws.size());
        assertEquals(shown, draws.get(0).component());
        Matrix4 transform = draws.get(0).transform();
        assertEquals(new Vector3(10f, 20f, 30f), transform.getTranslation(new Vector3()));
        assertTrue(SceneRenderer3D.collectDynamicMeshes(null).isEmpty());
    }
}
