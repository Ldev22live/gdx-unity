package io.github.ldev22live.gdxunity.engine.visualscript;

import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.List;
import java.util.Objects;

/** Curated component lookup and public API nodes (TD-068), without a dependency on optional engine modules. */
public final class ComponentNodes {
    /** Prefix of typed component lookup nodes; method nodes retain their stable Java method ids. */
    public static final String GET_PREFIX = "component.get:";
    private static final String RENDER = "io.github.ldev22live.gdxunity.engine.render.components.";
    private static final String AUDIO = "io.github.ldev22live.gdxunity.engine.scene.audio.";
    /** Exact method signatures exposed in the Components menu; no constructors, filesystem or editor APIs. */
    public static final List<String> API = List.of(
        RENDER + "ShapeRendererComponent#getWidth()", RENDER + "ShapeRendererComponent#getHeight()",
        RENDER + "TextComponent#getText()", RENDER + "TextComponent#setText(java.lang.String)",
        RENDER + "CameraComponent#getFieldOfView()", RENDER + "CameraComponent#setFieldOfView(float)",
        RENDER + "SpriteAnimatorComponent#play(java.lang.String)",
        RENDER + "TilemapComponent#isSolidAt(float,float)",
        AUDIO + "AudioSourceComponent#getVolume()", AUDIO + "AudioSourceComponent#setVolume(float)",
        AUDIO + "AudioSourceComponent#play()");

    private ComponentNodes() { }

    /** Registers available component APIs; unavailable optional modules contribute no nodes. Repeated calls are safe. */
    public static void registerAll(NodeRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        for (String signature : API) {
            String name = signature.substring(0, signature.indexOf('#'));
            Class<?> found;
            try {
                found = Class.forName(name, false, ComponentNodes.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                continue; // Optional render module is not present in a scene-only runtime.
            }
            Class<? extends Component> component = found.asSubclass(Component.class);
            registerLookup(registry, component);
            for (NodeType method : JavaMethodNodes.typesFor(component)) {
                if (!method.getId().equals(JavaMethodNodes.ID_PREFIX + signature) || registry.get(method.getId()) != null) {
                    continue;
                }
                NodeType.Builder builder = NodeType.builder(method.getId(), method.getTitle(),
                    "Components: " + component.getSimpleName(), method.getKind()).source(method.getSource());
                Graph empty = new Graph("Component API");
                GraphNode node = new GraphNode("api", method.getId(), 0f, 0f);
                for (PinDef pin : method.pinsFor(node, empty)) {
                    if (!pin.isExec()) builder.pin(pin);
                }
                registry.register(builder.behaviour(method::run).build());
            }
        }
    }

    private static void registerLookup(NodeRegistry registry, Class<? extends Component> component) {
        String id = GET_PREFIX + component.getSimpleName();
        if (registry.get(id) != null) return;
        registry.register(NodeType.builder(id, "Get " + component.getSimpleName(), "Components", NodeType.Kind.PURE)
            .pin(PinDef.in("target", "Entity", ValueType.ENTITY))
            .pin(PinDef.outObject("component", "Component", component))
            .source(component)
            .behaviour(context -> {
                Entity entity = context.inputEntity("target");
                if (entity == null) throw context.error("Target entity is missing");
                Component value = entity.getComponent(component);
                if (value == null) throw context.error("Entity " + entity.getName() + " has no " + component.getSimpleName());
                context.output("component", value);
            }).build());
    }
}
