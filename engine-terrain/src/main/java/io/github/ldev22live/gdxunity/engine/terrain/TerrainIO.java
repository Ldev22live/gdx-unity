package io.github.ldev22live.gdxunity.engine.terrain;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Reads and writes the {@code .terrain} file: one {@link Terrain} as JSON, version {@value #VERSION} (TD-082). A file may also carry "surfaces" (TD-083): one byte per grid point, the code of its
 * {@link Surface}; a file without it is all {@link Surface#AUTO}. The heights are every
 * grid point's height as a 32-bit float, little endian, row by row along x, in one Base64 string, so a map is a few hundred kilobytes
 * and round-trips exactly. Reading is strict: a newer version, a wrong type, a grid out of range or heights of the wrong length is an
 * error that names the problem.
 */
public final class TerrainIO {
    /** Current file format version. */
    public static final int VERSION = 1;

    private TerrainIO() { }

    /** The terrain as JSON text; the same terrain always gives the same text. */
    public static String toJson(Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain cannot be null");
        float[] heights = terrain.copyHeights();
        ByteBuffer bytes = ByteBuffer.allocate(heights.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asFloatBuffer().put(heights);
        String surfaces = terrain.hasSurfaces()
            ? ",\n  \"surfaces\": \"" + Base64.getEncoder().encodeToString(terrain.copySurfaces()) + "\"" : "";
        return "{\n"
            + "  \"version\": " + VERSION + ",\n"
            + "  \"name\": \"" + escape(terrain.getName()) + "\",\n"
            + "  \"resolutionX\": " + terrain.getResolutionX() + ",\n"
            + "  \"resolutionZ\": " + terrain.getResolutionZ() + ",\n"
            + "  \"cellSize\": " + String.format(Locale.ROOT, "%s", terrain.getCellSize()) + ",\n"
            + "  \"heights\": \"" + Base64.getEncoder().encodeToString(bytes.array()) + "\"" + surfaces + "\n"
            + "}\n";
    }

    /**
     * Reads a terrain.
     *
     * @throws TerrainFormatException if the text is not a version {@value #VERSION} terrain file
     */
    public static Terrain fromJson(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (SerializationException e) {
            throw new TerrainFormatException("Not a terrain file: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new TerrainFormatException("Not a terrain file: expected a JSON object");
        }
        int version = root.getInt("version", -1);
        if (version != VERSION) {
            throw new TerrainFormatException(version < 0 ? "Not a terrain file: it has no version"
                : "Terrain file version " + version + " is not supported (this editor reads version " + VERSION + ")");
        }
        try {
            int resolutionX = integer(root, "resolutionX");
            int resolutionZ = integer(root, "resolutionZ");
            Terrain terrain = new Terrain(resolutionX, resolutionZ, number(root, "cellSize"));
            if (root.has("name")) {
                JsonValue name = root.get("name");
                if (!name.isString()) {
                    throw new IllegalArgumentException("name must be text");
                }
                terrain.setName(name.asString());
            }
            JsonValue encoded = root.get("heights");
            if (encoded == null || !encoded.isString()) {
                throw new IllegalArgumentException("heights must be a Base64 string");
            }
            byte[] bytes = Base64.getDecoder().decode(encoded.asString());
            if (bytes.length != resolutionX * resolutionZ * Float.BYTES) {
                throw new IllegalArgumentException("heights hold " + bytes.length + " bytes but a " + resolutionX + " x " + resolutionZ
                    + " grid needs " + resolutionX * resolutionZ * Float.BYTES);
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int iz = 0; iz < resolutionZ; iz++) {
                for (int ix = 0; ix < resolutionX; ix++) {
                    terrain.setHeight(ix, iz, buffer.getFloat());
                }
            }
            JsonValue surfaceData = root.get("surfaces");
            if (surfaceData != null) {
                if (!surfaceData.isString()) {
                    throw new IllegalArgumentException("surfaces must be a Base64 string");
                }
                byte[] codes = Base64.getDecoder().decode(surfaceData.asString());
                if (codes.length != resolutionX * resolutionZ) {
                    throw new IllegalArgumentException("surfaces hold " + codes.length + " bytes but a " + resolutionX + " x " + resolutionZ
                        + " grid needs " + resolutionX * resolutionZ);
                }
                for (int iz = 0; iz < resolutionZ; iz++) {
                    for (int ix = 0; ix < resolutionX; ix++) {
                        terrain.setSurface(ix, iz, Surface.fromCode(codes[iz * resolutionX + ix]));
                    }
                }
            }
            return terrain;
        } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException e) {
            throw new TerrainFormatException("Bad terrain: " + e.getMessage(), e);
        }
    }

    private static int integer(JsonValue root, String field) {
        JsonValue value = root.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return value.asInt();
    }

    private static float number(JsonValue root, String field) {
        JsonValue value = root.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return value.asFloat();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
