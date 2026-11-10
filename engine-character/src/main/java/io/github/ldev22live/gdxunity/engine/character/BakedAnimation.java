package io.github.ldev22live.gdxunity.engine.character;

import java.util.List;

/**
 * An animation template (a humanoid {@code .anim} clip) sampled onto the character's bones, ready to be written into the model
 * file (TD-053).
 *
 * @param name     clip name, also the animation name in the model file
 * @param times    sample times in seconds, ascending, starting at 0
 * @param channels the bones that move
 * @param problems tracks of the clip that could not be applied, each with the reason
 */
public record BakedAnimation(String name, float[] times, List<Channel> channels, List<String> problems) {

    /**
     * Motion of one bone.
     *
     * @param bone         index into the character's bones
     * @param translations x, y, z per sample (parent space, meters), or null when the clip does not move the bone
     * @param rotations    x, y, z, w per sample (parent space), or null when the clip does not turn the bone
     */
    public record Channel(int bone, float[] translations, float[] rotations) {}
}
