package io.github.ldev22live.gdxunity.engine.visualscript;

import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Bounded synchronous control flow shared by desktop and web graphs (TD-068). */
public final class LoopNodes {
    /** Executes Body count times, then Completed; Index starts at zero. */
    public static final String REPEAT = "flow.repeat";
    /** Maximum iterations in one Repeat invocation; the event-wide execution budget also applies. */
    public static final int MAX_ITERATIONS = 1_000;
    /**
     * Executes Body once for each entity of a list a Java method node returned (such as {@code PhysicsSystem3D.overlapSphere} or
     * {@code Scene.getAllEntities}), in list order with Entity and Index set, then Completed (roadmap M1.2). The list is copied when the
     * node runs, so the body may change the scene.
     */
    public static final String FOR_EACH_ENTITY = "flow.forEachEntity";

    private LoopNodes() { }

    /** Adds Repeat to the searchable Flow category. */
    public static void registerAll(NodeRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        registry.register(NodeType.builder(REPEAT, "Repeat", "Flow", NodeType.Kind.ACTION)
            .execOutputs(PinDef.execOut("body", "Body"), PinDef.execOut("completed", "Completed"))
            .pin(PinDef.in("count", "Count", ValueType.INTEGER))
            .pin(PinDef.out("index", "Index", ValueType.INTEGER))
            .behaviour(context -> {
                int count = (Integer) context.input("count");
                if (count < 0 || count > MAX_ITERATIONS) {
                    throw context.error("Repeat count must be within 0.." + MAX_ITERATIONS + ": " + count);
                }
                context.repeat(count);
            }).build());
        registry.register(NodeType.builder(FOR_EACH_ENTITY, "For Each Entity", "Flow", NodeType.Kind.ACTION)
            .execOutputs(PinDef.execOut("body", "Body"), PinDef.execOut("completed", "Completed"))
            .pin(PinDef.inObject("list", "List", List.class))
            .pin(PinDef.out("entity", "Entity", ValueType.ENTITY))
            .pin(PinDef.out("index", "Index", ValueType.INTEGER))
            .behaviour(context -> context.forEachEntity(entities(context, context.input("list")))).build());
    }

    /** The list's entities; an unconnected list, an element that is not an entity or a list over the limit is an error, never skipped. */
    private static List<Entity> entities(NodeContext context, Object list) {
        if (!(list instanceof Collection<?> items)) {
            throw context.error("For Each Entity needs a list, got " + (list == null ? "nothing" : list.getClass().getSimpleName()));
        }
        if (items.size() > MAX_ITERATIONS) {
            throw context.error("For Each Entity runs at most " + MAX_ITERATIONS + " times: the list has " + items.size());
        }
        List<Entity> entities = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Entity entity)) {
                throw context.error("For Each Entity found " + (item == null ? "null" : item.getClass().getSimpleName())
                    + " in its list, not an entity");
            }
            entities.add(entity);
        }
        return entities;
    }
}
