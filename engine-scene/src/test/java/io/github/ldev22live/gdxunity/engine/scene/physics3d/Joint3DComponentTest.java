package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent.MotorMode;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent.Type;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.Joint3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.RigidBody3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Joint 3D (TD-051) without a physics engine: what its settings accept, how it is saved, and what scripts get without joints. */
class Joint3DComponentTest {

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldRefuseBadSettingsAndKeepTheOldValue() {
        Joint3DComponent joint = new Joint3DComponent();

        assertThrows(IllegalArgumentException.class, () -> joint.setAxis(new Vector3()));
        assertThrows(IllegalArgumentException.class, () -> joint.setAnchor(new Vector3(Float.NaN, 0f, 0f)));
        assertThrows(IllegalArgumentException.class, () -> joint.setLimits(10f, -10f));
        assertThrows(IllegalArgumentException.class, () -> joint.setLimits(Float.NEGATIVE_INFINITY, 1f));
        assertThrows(IllegalArgumentException.class, () -> joint.setMotorMaxForce(0f));
        assertThrows(IllegalArgumentException.class, () -> joint.setMotorTarget(Float.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> joint.setConnectedName(null));
        assertEquals(new Vector3(Vector3.Y), joint.getAxis(), "a refused axis leaves the default");
        assertEquals(0f, joint.getUpperLimit());
        assertEquals("", joint.getConnectedName(), "joined to the world by default");
        assertFalse(joint.isCollideConnected());
        assertEquals(Type.HINGE, joint.getType());
    }

    @Test
    void shouldRoundTripEverySettingThroughTheSceneFile() {
        Scene scene = new Scene("Joint");
        Entity door = scene.createEntity("Door");
        Joint3DComponent joint = new Joint3DComponent(Type.SLIDER);
        joint.setConnectedName("Frame");
        joint.setAnchor(new Vector3(1f, 2f, 3f));
        joint.setConnectedAnchor(new Vector3(4f, 5f, 6f));
        joint.setAxis(new Vector3(0f, 0f, 1f));
        joint.setCollideConnected(true);
        joint.setLimitsEnabled(true);
        joint.setLimits(-20f, 140f);
        joint.setMotorMode(MotorMode.POSITION);
        joint.setMotorTarget(75f);
        joint.setMotorMaxForce(250f);
        door.addComponent(joint);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new Joint3DComponentSerializer());
        SceneSerializer serializer = new SceneSerializer(registry);

        Joint3DComponent copy = serializer.fromJson(serializer.toJson(scene)).findEntityById(door.getId())
            .getComponent(Joint3DComponent.class);

        assertEquals(Type.SLIDER, copy.getType());
        assertEquals("Frame", copy.getConnectedName());
        assertEquals(new Vector3(1f, 2f, 3f), copy.getAnchor());
        assertEquals(new Vector3(4f, 5f, 6f), copy.getConnectedAnchor());
        assertEquals(new Vector3(0f, 0f, 1f), copy.getAxis());
        assertTrue(copy.isCollideConnected());
        assertTrue(copy.isLimitsEnabled());
        assertEquals(-20f, copy.getLowerLimit());
        assertEquals(140f, copy.getUpperLimit());
        assertEquals(MotorMode.POSITION, copy.getMotorMode());
        assertEquals(75f, copy.getMotorTarget());
        assertEquals(250f, copy.getMotorMaxForce());
    }

    @Test
    void shouldFailToLoadAnUnknownTypeOrMotor() {
        Joint3DComponentSerializer serializer = new Joint3DComponentSerializer();

        assertThrows(SceneSerializationException.class,
            () -> serializer.deserialize(new JsonReader().parse("{\"type\":\"SPRING\"}"), new Json()));
        assertThrows(SceneSerializationException.class,
            () -> serializer.deserialize(new JsonReader().parse("{\"motorMode\":\"TURBO\"}"), new Json()));
        assertThrows(SceneSerializationException.class,
            () -> serializer.deserialize(new JsonReader().parse("{\"lowerLimit\":5,\"upperLimit\":-5}"), new Json()));
    }

    @Test
    void shouldWriteATypeThatCanBeReadWithoutTheOtherPhysicsComponents() {
        Joint3DComponentSerializer serializer = new Joint3DComponentSerializer();
        StringWriter out = new StringWriter();
        Json json = new Json(JsonWriter.OutputType.json);
        json.setWriter(out);
        json.writeObjectStart();
        serializer.serialize(new Joint3DComponent(Type.FIXED), json);
        json.writeObjectEnd();

        assertTrue(out.toString().contains("\"type\":\"FIXED\""), out.toString());
        assertEquals(Type.FIXED, serializer.deserialize(new JsonReader().parse(out.toString()), new Json()).getType());
    }

    @Test
    void shouldTellAScriptWhenTheSceneHasNoPhysicsOrNoJoint() {
        Scene scene = new Scene("Script");
        Entity entity = scene.createEntity("Door");
        ScriptComponent script = new ScriptComponent() { };
        entity.addComponent(script);

        IllegalStateException noPhysics = assertThrows(IllegalStateException.class,
            () -> script.setJointMotor3D(MotorMode.VELOCITY, 10f));
        assertTrue(noPhysics.getMessage().contains("3D physics"), noPhysics.getMessage());
        assertThrows(IllegalStateException.class, script::getJointPosition3D);

        PhysicsSystem3D physics = new PhysicsSystem3D(scene, new PhysicsSystem3DTest.FakeBackend());
        try {
            IllegalStateException noJoint = assertThrows(IllegalStateException.class, () -> script.setJointMotor3D(MotorMode.OFF, 0f));
            assertTrue(noJoint.getMessage().contains("Door") && noJoint.getMessage().contains("no joint"), noJoint.getMessage());
        } finally {
            physics.dispose();
        }
    }

    @Test
    void shouldKeepPhysicsComponentsOfAJointedEntityLoadableTogether() {
        // The joint is saved next to the body and collider it needs
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new Joint3DComponentSerializer());
        registry.register(new RigidBody3DComponentSerializer());
        registry.register(new BoxCollider3DComponentSerializer());
        Scene scene = new Scene("Door");
        Entity door = scene.createEntity("Door");
        door.addComponent(new BoxCollider3DComponent(new Vector3(10f, 20f, 30f)));
        door.addComponent(new RigidBody3DComponent());
        door.addComponent(new Joint3DComponent(Type.BALL));
        SceneSerializer serializer = new SceneSerializer(registry);

        Entity read = serializer.fromJson(serializer.toJson(scene)).findEntityById(door.getId());

        assertEquals(Type.BALL, read.getComponent(Joint3DComponent.class).getType());
        assertEquals(new Vector3(10f, 20f, 30f), read.getComponent(BoxCollider3DComponent.class).getSize());
    }
}
