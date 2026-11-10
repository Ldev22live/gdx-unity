package io.github.ldev22live.gdxunity.engine.visualscript;

/** Where a running graph sends its output: Print messages, errors, and (for debugging) which nodes ran. */
public interface GraphHost {

    void log(GraphInstance instance, String message);

    /** A node failed; that chain of execution stopped, the graph keeps responding to later events. */
    void error(GraphInstance instance, GraphRuntimeException error);

    /** Whether the key with this libGDX key code is held down (Key Down and Input Axis nodes); false where there is no keyboard. */
    default boolean isKeyDown(int keycode) {
        return com.badlogic.gdx.Gdx.input != null && com.badlogic.gdx.Gdx.input.isKeyPressed(keycode);
    }

    /** Called for every node that runs, so an editor can highlight it. */
    default void nodeExecuted(GraphInstance instance, String nodeId) {
    }
}
