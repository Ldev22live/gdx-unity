package io.github.ldev22live.gdxunity.engine.audio;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates effects by type name, for song files and the editor's effect menu. */
public final class Effects {
    private static final List<String> TYPES = List.of("delay", "reverb", "filter");

    private Effects() { }

    /** The effect types that {@link #create} knows. */
    public static List<String> types() {
        return TYPES;
    }

    /** A new effect with default settings. */
    public static Effect create(String type, float sampleRate) {
        Objects.requireNonNull(type, "type cannot be null");
        return switch (type) {
            case "delay" -> new DelayEffect(sampleRate);
            case "reverb" -> new ReverbEffect(sampleRate);
            case "filter" -> new FilterEffect(sampleRate);
            default -> throw new IllegalArgumentException("Unknown effect type: " + type + " (known: " + TYPES + ")");
        };
    }

    /** A new effect with the given parameter values; unknown parameter names are rejected. */
    public static Effect create(String type, Map<String, Float> parameters, float sampleRate) {
        Effect effect = create(type, sampleRate);
        for (Map.Entry<String, Float> entry : parameters.entrySet()) {
            effect.setParameter(entry.getKey(), entry.getValue());
        }
        return effect;
    }
}
