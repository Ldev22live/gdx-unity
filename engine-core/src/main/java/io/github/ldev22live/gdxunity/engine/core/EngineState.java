package io.github.ldev22live.gdxunity.engine.core;

/**
 * Represents the observable lifecycle states of an engine runtime.
 */
public enum EngineState {
    /** Engine instance has been created but not yet initialized. */
    NEW,
    /** Engine has been initialized and is active in its frame loop. */
    RUNNING,
    /** Engine execution is temporarily paused (e.g., window minimized or backgrounded). */
    PAUSED,
    /** Engine is in the process of shutting down and releasing owned resources. */
    DISPOSING,
    /** Engine has completed shutdown and all owned resources have been released. */
    DISPOSED,
    /** Engine encountered an unrecoverable failure during initialization or execution. */
    FAILED
}
