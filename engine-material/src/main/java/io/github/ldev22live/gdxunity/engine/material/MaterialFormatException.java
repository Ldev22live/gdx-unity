package io.github.ldev22live.gdxunity.engine.material;

/** A {@code .material} file that cannot be read: not JSON, a newer version, or a value the material refuses. */
public class MaterialFormatException extends RuntimeException {
    public MaterialFormatException(String message) {
        super(message);
    }

    public MaterialFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
