package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.Objects;

/** Bounded synchronous control flow shared by desktop and web graphs (TD-068). */
public final class LoopNodes {
    /** Executes Body count times, then Completed; Index starts at zero. */
    public static final String REPEAT = "flow.repeat";
    /** Maximum iterations in one Repeat invocation; the event-wide execution budget also applies. */
    public static final int MAX_ITERATIONS = 1_000;

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
    }
}
