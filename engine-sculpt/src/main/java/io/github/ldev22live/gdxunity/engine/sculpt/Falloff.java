package io.github.ldev22live.gdxunity.engine.sculpt;

/** How a brush's effect fades from its centre to its edge. */
public enum Falloff {
    /** Smooth S-curve: full strength at the centre, no visible edge. The usual choice. */
    SMOOTH {
        @Override
        float shape(float remaining) {
            return remaining * remaining * (3f - 2f * remaining);
        }
    },
    /** Straight line from the centre to the edge. */
    LINEAR {
        @Override
        float shape(float remaining) {
            return remaining;
        }
    },
    /** Concentrated at the centre: makes a narrow, pointed effect inside the radius. */
    SHARP {
        @Override
        float shape(float remaining) {
            return remaining * remaining;
        }
    },
    /** Full strength everywhere inside the radius: a hard edge. */
    CONSTANT {
        @Override
        float shape(float remaining) {
            return 1f;
        }
    };

    /** The curve for {@code remaining = 1 - distanceFraction}, in the range 0 to 1. */
    abstract float shape(float remaining);

    /**
     * The brush weight at a distance from the centre.
     *
     * @param distanceFraction distance divided by the brush radius; 0 is the centre
     * @return 1 at the centre and 0 at or beyond the radius ({@link #CONSTANT} is 1 up to the radius), never
     *         increasing with distance
     */
    public float weight(float distanceFraction) {
        if (!(distanceFraction < 1f)) {
            return 0f;
        }
        return shape(1f - Math.max(distanceFraction, 0f));
    }
}
