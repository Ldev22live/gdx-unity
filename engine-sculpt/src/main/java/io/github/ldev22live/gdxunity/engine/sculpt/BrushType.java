package io.github.ldev22live.gdxunity.engine.sculpt;

/** What a brush does to the vertices under it. */
public enum BrushType {
    /** Pushes the surface out along the average normal under the brush (the subtract option pushes it in). */
    DRAW,
    /** Pushes every vertex along its own normal, which swells or shrinks the area (subtract shrinks). */
    INFLATE,
    /** Moves every vertex towards the average of its neighbours, which evens out bumps. */
    SMOOTH,
    /** Flattens the area onto the average plane under the brush. */
    FLATTEN,
    /** Pulls the surface towards the brush centre along the surface, which sharpens creases (subtract spreads it). */
    PINCH,
    /** Picks up the vertices under the brush when the stroke starts and drags them with the cursor. */
    GRAB;

    /** True for brushes that apply repeated dabs along the stroke; {@link #GRAB} moves one fixed set instead. */
    public boolean usesDabs() {
        return this != GRAB;
    }
}
