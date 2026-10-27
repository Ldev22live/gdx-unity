package io.github.ldev22live.gdxunity.engine.core.time;

/**
 * Source of delta time measurements in seconds.
 * Allows deterministic clock injection for unit testing.
 */
@FunctionalInterface
public interface TimeProvider {
    /**
     * Returns the raw delta time since the previous frame in seconds.
     *
     * @return delta time in seconds
     */
    float getRawDeltaTime();
}
