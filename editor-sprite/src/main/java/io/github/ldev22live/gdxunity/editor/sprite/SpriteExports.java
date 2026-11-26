package io.github.ldev22live.gdxunity.editor.sprite;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteSheet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Source versus export (TD-104): a sprite sheet or PNG exported from a {@code .sprite} or {@code .vector} records that source, and a
 * PNG opened later points back to it. Editing an exported PNG cannot bring back the layers, frames or vector shapes it was flattened
 * from, so 2D Studio offers the source instead, or says plainly that there is none.
 */
public final class SpriteExports {
    /** The atlas written beside an exported sheet: {@code <name>.png} and {@code <name>.sheet.json}. */
    public static final String ATLAS_SUFFIX = ".sheet.json";
    /** The record beside a lone exported PNG: {@code <name>.png.source.json}. */
    public static final String SIDECAR_SUFFIX = ".source.json";

    /** What is known about where a PNG came from. */
    public enum State {
        /** No source is recorded: the PNG is all there is. */
        NONE,
        /** The recorded source exists and is unchanged since the export. */
        CURRENT,
        /** The recorded source exists but changed after the export; exporting again updates the PNG. */
        CHANGED,
        /** The recorded source file is gone. */
        MISSING
    }

    /**
     * A PNG's recorded source.
     *
     * @param state  what is known
     * @param source the recorded source path, project-relative; null for {@link State#NONE}
     */
    public record Origin(State state, String source) {
        /** One sentence for the person opening the PNG. */
        public String advice() {
            return switch (state) {
                case NONE -> "This PNG has no recorded source: its pixels can be edited, but layers, frames and vector shapes cannot be "
                    + "recovered from it.";
                case CURRENT -> "Exported from " + source + ". Open the source to edit its layers, frames and shapes; the PNG is "
                    + "regenerated when you export.";
                case CHANGED -> "Exported from " + source + ", which has changed since. Open the source, and export again to update "
                    + "this PNG.";
                case MISSING -> "Exported from " + source + ", which is no longer in the project. Only the pixels are left.";
            };
        }
    }

    private SpriteExports() { }

    /** The record to write into an export of {@code source}: its project-relative path and current SHA-256. */
    public static SpriteSheet.SourceRecord recordOf(Path projectRoot, Path source) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        Path root = projectRoot.toAbsolutePath().normalize();
        Path file = source.toAbsolutePath().normalize();
        if (!file.startsWith(root)) {
            throw new IOException("The source " + source + " is outside the project " + projectRoot);
        }
        return new SpriteSheet.SourceRecord(root.relativize(file).toString().replace('\\', '/'), sha256(Files.readAllBytes(file)));
    }

    /** Writes the sidecar record of a lone exported PNG ({@code <png>.source.json}). */
    public static void writeSidecar(Path png, SpriteSheet.SourceRecord record) throws IOException {
        Objects.requireNonNull(png, "png cannot be null");
        Objects.requireNonNull(record, "record cannot be null");
        JsonValue json = new JsonValue(JsonValue.ValueType.object);
        JsonValue source = new JsonValue(JsonValue.ValueType.object);
        source.addChild("path", new JsonValue(record.path()));
        source.addChild("sha256", new JsonValue(record.sha256()));
        json.addChild("source", source);
        Files.writeString(png.resolveSibling(png.getFileName() + SIDECAR_SUFFIX), json.toJson(OutputType.json)
            + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Where {@code png} came from: the source in its sheet atlas ({@code <name>.sheet.json}) or its sidecar
     * ({@code <name>.png.source.json}), checked against the project.
     */
    public static Origin originOf(Path projectRoot, Path png) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(png, "png cannot be null");
        String name = png.getFileName().toString();
        String base = name.toLowerCase(Locale.ROOT).endsWith(".png") ? name.substring(0, name.length() - 4) : name;
        JsonValue source = null;
        for (Path record : new Path[] {png.resolveSibling(base + ATLAS_SUFFIX), png.resolveSibling(name + SIDECAR_SUFFIX)}) {
            if (Files.isRegularFile(record)) {
                try {
                    source = new JsonReader().parse(Files.readString(record, StandardCharsets.UTF_8)).get("source");
                } catch (RuntimeException e) {
                    throw new IOException("Unreadable export record " + record.getFileName() + ": " + e.getMessage(), e);
                }
                if (source != null) {
                    break;
                }
            }
        }
        if (source == null) {
            return new Origin(State.NONE, null);
        }
        String path = source.getString("path", null);
        String recorded = source.getString("sha256", null);
        if (path == null || recorded == null) {
            throw new IOException("The export record of " + name + " has no source path or checksum");
        }
        Path file = projectRoot.resolve(path).normalize();
        if (!file.startsWith(projectRoot.normalize()) || !Files.isRegularFile(file)) {
            return new Origin(State.MISSING, path);
        }
        return new Origin(sha256(Files.readAllBytes(file)).equals(recorded) ? State.CURRENT : State.CHANGED, path);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
