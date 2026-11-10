package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * What a node sees while it runs: its inputs (evaluated on demand through incoming wires, else the typed literal,
 * else the type default), its outputs, and the ways to continue execution.
 */
public interface NodeContext {

    Object input(String pinId);

    default float inputFloat(String pinId) {
        return (Float) input(pinId);
    }

    default boolean inputBoolean(String pinId) {
        return (Boolean) input(pinId);
    }

    default String inputString(String pinId) {
        return (String) input(pinId);
    }

    /** A copy, safe to modify. */
    default Vector3 inputVector(String pinId) {
        return new Vector3((Vector3) input(pinId));
    }

    /** The connected entity, or the graph's own entity when the pin is not connected. */
    Entity inputEntity(String pinId);

    void output(String pinId, Object value);

    /** Node setting (see {@link NodeType#getProperties()}), or null. */
    Object property(String name);

    /** Continues execution from this node's execution output {@code pinId}, after this node returns. */
    void continueWith(String pinId);

    /** Continues from {@code pinId} after {@code seconds} of play time. */
    void continueAfter(float seconds, String pinId);

    /** Schedules Repeat's synchronous Body chains in index order, then Completed; delayed work is not awaited. */
    void repeat(int count);

    /** Whether the key with this libGDX key code is held down. */
    default boolean isKeyDown(int keycode) {
        return instance().isKeyDown(keycode);
    }

    /** The entity running this graph. */
    Entity self();

    GraphInstance instance();

    /** Writes a line to the host's log (the Output Log in the editor). */
    void log(String message);

    /** An error tied to this node; throw it to stop the current chain. */
    GraphRuntimeException error(String message);
}
