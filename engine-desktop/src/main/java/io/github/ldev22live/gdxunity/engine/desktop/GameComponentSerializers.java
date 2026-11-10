package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.CameraComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.CameraRigComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.DynamicMeshComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ShapeRendererComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.SpriteRendererComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TextComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioListenerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.DirectionalLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.PointLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.SkyLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.ToneMappingComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics.serialization.BoxCollider2DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics.serialization.CircleCollider2DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics.serialization.RigidBody2DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CapsuleCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.ConvexHullCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.MeshCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CharacterController3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.Joint3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.RigidBody3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.SphereCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponentSerializer;

import java.util.Objects;

/**
 * Every component type a scene file can contain (TD-035). The editor and the game runtime register the same list,
 * so a scene that opens in the editor also loads in the runtime.
 */
public final class GameComponentSerializers {

    private GameComponentSerializers() {
    }

    /** Registers the serializer of every engine component type in {@code registry}. */
    public static void registerAll(ComponentSerializerRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        registry.register(new GraphScriptComponentSerializer());
        registry.register(new ModelComponentSerializer());
        registry.register(new DynamicMeshComponentSerializer());
        registry.register(new CameraComponentSerializer());
        registry.register(new CameraRigComponentSerializer());
        registry.register(new TextComponentSerializer());
        registry.register(new AnimatorComponentSerializer());
        registry.register(new ShapeRendererComponentSerializer());
        registry.register(new SpriteRendererComponentSerializer());
        registry.register(new io.github.ldev22live.gdxunity.engine.render.components.serialization.SpriteAnimatorComponentSerializer());
        registry.register(new io.github.ldev22live.gdxunity.engine.render.components.serialization.TilemapComponentSerializer());
        registry.register(new EntityScriptBindingComponentSerializer());
        registry.register(new RigidBody2DComponentSerializer());
        registry.register(new BoxCollider2DComponentSerializer());
        registry.register(new CircleCollider2DComponentSerializer());
        registry.register(new MeshPrimitiveComponentSerializer());
        registry.register(new DirectionalLightComponentSerializer());
        registry.register(new PointLightComponentSerializer());
        registry.register(new SkyLightComponentSerializer());
        registry.register(new ToneMappingComponentSerializer());
        registry.register(new RigidBody3DComponentSerializer());
        registry.register(new BoxCollider3DComponentSerializer());
        registry.register(new SphereCollider3DComponentSerializer());
        registry.register(new CapsuleCollider3DComponentSerializer());
        registry.register(new ConvexHullCollider3DComponentSerializer());
        registry.register(new MeshCollider3DComponentSerializer());
        registry.register(new CharacterController3DComponentSerializer());
        registry.register(new Joint3DComponentSerializer());
        registry.register(new PrefabInstanceComponentSerializer());
        registry.register(new AudioSourceComponentSerializer());
        registry.register(new MusicSourceComponentSerializer());
        registry.register(new AudioListenerComponentSerializer());
    }
}
