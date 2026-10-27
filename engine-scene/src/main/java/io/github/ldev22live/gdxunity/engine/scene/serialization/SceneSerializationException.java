package io.github.ldev22live.gdxunity.engine.scene.serialization;

/**
 * Thrown when scene serialization or deserialization encounters invalid syntax,
 * unsupported schema versions, or broken entity hierarchies.
 */
public class SceneSerializationException extends RuntimeException {
    public SceneSerializationException(String message) {
        super(message);
    }

    public SceneSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
