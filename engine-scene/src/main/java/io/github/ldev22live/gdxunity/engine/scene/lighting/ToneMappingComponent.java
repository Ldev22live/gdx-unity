package io.github.ldev22live.gdxunity.engine.scene.lighting;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Scene-wide exposure and tone mapping for the 3D renderer. Exposure scales all scene light (directional, point,
 * sky and ambient light; not emissive surfaces) before tone mapping compresses bright values so they approach
 * white instead of clipping. The entity's transform is ignored; only the first enabled one in a scene is used.
 * Without one the renderer uses exposure 1 and {@link Operator#NONE}.
 */
public class ToneMappingComponent extends BaseComponent {

    /** How lit colors brighter than 1 are mapped to the screen. */
    public enum Operator {
        /** No mapping: values above 1 clip, as before Phase 13-B. */
        NONE,
        /** Reinhard, {@code c / (1 + c)}: soft, keeps hues, slightly flat. */
        REINHARD,
        /** Filmic curve (Narkowicz' fit of the ACES reference curve): more contrast, highlights roll off to white. */
        ACES_FILMIC
    }

    private float exposure = 1f;
    private Operator operator = Operator.ACES_FILMIC;

    /** Linear multiplier on scene light. Default 1. */
    public float getExposure() {
        return exposure;
    }

    /**
     * Sets the linear exposure multiplier.
     *
     * @throws IllegalArgumentException if {@code exposure} is not a finite value above 0
     */
    public void setExposure(float exposure) {
        if (!(exposure > 0f) || Float.isInfinite(exposure)) {
            throw new IllegalArgumentException("exposure must be a finite value > 0: " + exposure);
        }
        this.exposure = exposure;
    }

    /** Tone mapping operator. Default {@link Operator#ACES_FILMIC}. */
    public Operator getOperator() {
        return operator;
    }

    /** Sets the tone mapping operator. */
    public void setOperator(Operator operator) {
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
    }
}
