package io.github.ldev22live.gdxunity.engine.terrain;

/** A {@code .terrain} file that cannot be read: not JSON, a newer version, or a grid or height the terrain refuses. */
public class TerrainFormatException extends RuntimeException {
    public TerrainFormatException(String message) {
        super(message);
    }

    public TerrainFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
