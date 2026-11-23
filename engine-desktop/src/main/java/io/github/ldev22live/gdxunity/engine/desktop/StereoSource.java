package io.github.ldev22live.gdxunity.engine.desktop;

/** Something that fills blocks of stereo samples for the sound output: the effects mixer, or effects plus songs. */
@FunctionalInterface
interface StereoSource {
    /** Overwrites the first {@code frames} entries of both arrays. */
    void render(float[] left, float[] right, int frames);
}
