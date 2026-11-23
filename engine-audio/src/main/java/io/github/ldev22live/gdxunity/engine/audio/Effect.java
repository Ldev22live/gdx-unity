package io.github.ldev22live.gdxunity.engine.audio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A stereo effect on a mixer channel. Parameters are named floats (0..1 unless the effect says otherwise) so the
 * editor, the song file and the tests can treat every effect the same way.
 */
public interface Effect {
    /** The effect's type name, as used in song files ("delay", "reverb", "filter"). */
    String getType();

    /** Names of the parameters, in display order. */
    List<String> getParameterNames();

    /** Current value of a parameter. */
    float getParameter(String name);

    /** Changes a parameter; values are clamped to the parameter's range. */
    void setParameter(String name, float value);

    /** Processes {@code frames} frames in place. */
    void process(float[] left, float[] right, int offset, int frames);

    /** Clears the effect's memory (delay lines, filter state). */
    void reset();

    /** Snapshot of all parameters, in display order. */
    default Map<String, Float> getParameters() {
        Map<String, Float> values = new LinkedHashMap<>();
        for (String name : getParameterNames()) {
            values.put(name, getParameter(name));
        }
        return values;
    }
}
