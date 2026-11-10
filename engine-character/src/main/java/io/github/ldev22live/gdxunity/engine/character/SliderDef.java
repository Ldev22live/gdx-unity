package io.github.ldev22live.gdxunity.engine.character;

/**
 * One slider of the character creator (TD-053). A slider with two targets goes from -1 (the {@code lowTarget}) through 0 (the
 * base shape) to 1 (the {@code highTarget}); one with a single target goes from 0 to 1.
 *
 * @param id         stable name, {@code group/name}, used in saved characters
 * @param group      body part the slider belongs to (head, nose, torso ...), for grouping in the editor
 * @param label      name shown to the user
 * @param lowTarget  morph target for negative values, or null for a one-sided slider
 * @param highTarget morph target for positive values
 */
public record SliderDef(String id, String group, String label, String lowTarget, String highTarget) {

    /** Whether the slider goes both ways (-1 to 1). */
    public boolean isSigned() {
        return lowTarget != null;
    }

    public float min() {
        return isSigned() ? -1f : 0f;
    }

    public float max() {
        return 1f;
    }
}
