package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CapsuleCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.RigidBody3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.SphereCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Physics3DSerializationTest {

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new RigidBody3DComponentSerializer());
        registry.register(new BoxCollider3DComponentSerializer());
        registry.register(new SphereCollider3DComponentSerializer());
        registry.register(new CapsuleCollider3DComponentSerializer());
        return new SceneSerializer(registry);
    }

    @Test
    void shouldRoundTripRigidBodyAndAllColliders() {
        Scene scene = new Scene("Physics 3D");
        Entity entity = scene.createEntity("Player");
        RigidBody3DComponent body = new RigidBody3DComponent(RigidBody3DComponent.MotionType.KINEMATIC);
        body.setMass(80f);
        body.setLinearDamping(0.2f);
        body.setAngularDamping(0.3f);
        body.setGravityFactor(0.5f);
        body.setFriction(0.9f);
        body.setRestitution(0.25f);
        body.setLayer(4);
        body.setCollisionMask(0b1011);
        entity.addComponent(body);
        BoxCollider3DComponent box = new BoxCollider3DComponent(new Vector3(10f, 20f, 30f));
        box.setOffset(new Vector3(1f, 2f, 3f));
        entity.addComponent(box);
        SphereCollider3DComponent sphere = new SphereCollider3DComponent(7f);
        sphere.setTrigger(true);
        sphere.setEnabled(false);
        entity.addComponent(sphere);
        entity.addComponent(new CapsuleCollider3DComponent(15f, 60f));

        SceneSerializer serializer = serializer();
        Entity read = serializer.fromJson(serializer.toJson(scene)).findEntityById(entity.getId());

        RigidBody3DComponent readBody = read.getComponent(RigidBody3DComponent.class);
        assertEquals(RigidBody3DComponent.MotionType.KINEMATIC, readBody.getMotionType());
        assertEquals(80f, readBody.getMass());
        assertEquals(0.2f, readBody.getLinearDamping());
        assertEquals(0.3f, readBody.getAngularDamping());
        assertEquals(0.5f, readBody.getGravityFactor());
        assertEquals(0.9f, readBody.getFriction());
        assertEquals(0.25f, readBody.getRestitution());
        assertEquals(4, readBody.getLayer());
        assertEquals(0b1011, readBody.getCollisionMask());

        BoxCollider3DComponent readBox = read.getComponent(BoxCollider3DComponent.class);
        assertEquals(new Vector3(10f, 20f, 30f), readBox.getSize());
        assertEquals(new Vector3(1f, 2f, 3f), readBox.getOffset());
        assertFalse(readBox.isTrigger());
        SphereCollider3DComponent readSphere = read.getComponent(SphereCollider3DComponent.class);
        assertEquals(7f, readSphere.getRadius());
        assertTrue(readSphere.isTrigger());
        assertFalse(readSphere.isEnabled());
        CapsuleCollider3DComponent readCapsule = read.getComponent(CapsuleCollider3DComponent.class);
        assertEquals(15f, readCapsule.getRadius());
        assertEquals(60f, readCapsule.getHeight());
        assertEquals(3, read.getComponents(Collider3DComponent.class).size());
    }

    @Test
    void shouldRejectInvalidValues() {
        RigidBody3DComponent body = new RigidBody3DComponent();
        assertThrows(IllegalArgumentException.class, () -> body.setMass(0f));
        assertThrows(IllegalArgumentException.class, () -> body.setRestitution(1.5f));
        assertThrows(IllegalArgumentException.class, () -> body.setLayer(PhysicsLayers3D.LAYER_COUNT));
        assertThrows(IllegalArgumentException.class, () -> new SphereCollider3DComponent(-1f));
        assertThrows(IllegalArgumentException.class, () -> new BoxCollider3DComponent(new Vector3(1f, 0f, 1f)));
        body.setCollisionMask(-1);
        assertEquals(PhysicsLayers3D.ALL_LAYERS, body.getCollisionMask(), "Bits above the layer count are dropped");

        String json = "{\"schemaVersion\":1,\"name\":\"S\",\"entities\":[{\"id\":\"00000000-0000-0000-0000-000000000001\","
            + "\"name\":\"E\",\"components\":[{\"type\":\"RigidBody3DComponent\",\"data\":{\"motionType\":\"FLYING\"}}]}]}";
        assertThrows(SceneSerializationException.class, () -> serializer().fromJson(json));
    }
}
