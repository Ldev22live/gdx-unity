package io.github.ldev22live.gdxunity.engine.sprite;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The {@code .sprite} file (TD-066): JSON with the layers, frames, tags, slices and palette, and one PNG (base64) per non-empty cel, so the file
 * can be read by a browser too. {@code schemaVersion} is 1.
 */
public final class SpriteFile {
    /** File extension without the dot. */
    public static final String EXTENSION = "sprite";
    /** Version written, and the only version read. */
    public static final int SCHEMA_VERSION = 1;
    private static final String KIND = "sprite";
    private static final int PALETTE_HEX_DIGITS = 8;

    private SpriteFile() { }

    /** Writes a document as {@code .sprite} text. */
    public static String write(SpriteDocument doc) throws IOException {
        Objects.requireNonNull(doc, "doc cannot be null");
        StringWriter text = new StringWriter();
        JsonWriter w = new JsonWriter(text);
        w.setOutputType(JsonWriter.OutputType.json);
        w.object();
        w.set("schemaVersion", SCHEMA_VERSION);
        w.set("kind", KIND);
        w.set("width", doc.getWidth());
        w.set("height", doc.getHeight());
        w.array("layers");
        for (int l = 0; l < doc.getLayerCount(); l++) {
            SpriteLayer layer = doc.getLayer(l);
            w.object().set("name", layer.name()).set("visible", layer.visible()).set("opacity", layer.opacity()).pop();
        }
        w.pop();
        w.array("frames");
        for (int f = 0; f < doc.getFrameCount(); f++) {
            w.object().set("duration", doc.getFrameDuration(f)).pop();
        }
        w.pop();
        w.array("tags");
        for (SpriteTag t : doc.getTags()) {
            w.object().set("name", t.name()).set("from", t.from()).set("to", t.to()).set("direction", t.direction().name()).pop();
        }
        w.pop();
        w.array("slices");
        for (SpriteSlice s : doc.getSlices()) {
            w.object().set("name", s.name()).set("x", s.x()).set("y", s.y()).set("width", s.width()).set("height", s.height())
                .set("left", s.left()).set("right", s.right()).set("top", s.top()).set("bottom", s.bottom())
                .set("pivotX", s.pivotX()).set("pivotY", s.pivotY()).pop();
        }
        w.pop();
        w.array("palette");
        for (int color : doc.getPalette()) {
            w.value(String.format(Locale.ROOT, "%0" + PALETTE_HEX_DIGITS + "x", color));
        }
        w.pop();
        w.array("cels");
        for (int l = 0; l < doc.getLayerCount(); l++) {
            for (int f = 0; f < doc.getFrameCount(); f++) {
                if (!doc.isCelEmpty(l, f)) {
                    byte[] png = PixelPng.encode(doc.getWidth(), doc.getHeight(), doc.copyCel(l, f));
                    w.object().set("layer", l).set("frame", f).set("png", Base64.getEncoder().encodeToString(png)).pop();
                }
            }
        }
        w.pop();
        w.pop();
        w.close();
        return text.toString();
    }

    /** Reads {@code .sprite} text. Throws {@link IOException} with the reason for a file that is not a valid version-1 sprite. */
    public static SpriteDocument read(String json) throws IOException {
        Objects.requireNonNull(json, "json cannot be null");
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException e) {
            throw new IOException("not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || !KIND.equals(root.getString("kind", null))) {
            throw new IOException("not a sprite file (kind is not \"" + KIND + "\")");
        }
        int version = root.getInt("schemaVersion", -1);
        if (version != SCHEMA_VERSION) {
            throw new IOException("unsupported sprite schemaVersion " + version + " (this build reads " + SCHEMA_VERSION + ")");
        }
        try {
            return build(root);
        } catch (RuntimeException e) {
            throw new IOException("invalid sprite file: " + e.getMessage(), e);
        }
    }

    private static SpriteDocument build(JsonValue root) throws IOException {
        SpriteDocument doc = new SpriteDocument(root.getInt("width"), root.getInt("height"));
        JsonValue layers = root.get("layers");
        JsonValue frames = root.get("frames");
        if (layers == null || layers.size < 1 || frames == null || frames.size < 1) {
            throw new IllegalArgumentException("a sprite needs at least one layer and one frame");
        }
        for (int i = 1; i < layers.size; i++) {
            doc.addLayer("tmp");
        }
        for (int i = 1; i < frames.size; i++) {
            doc.insertFrame(doc.getFrameCount(), -1);
        }
        int index = 0;
        for (JsonValue l : layers) {
            doc.setLayer(index++, new SpriteLayer(l.getString("name"), l.getBoolean("visible", true), l.getInt("opacity", SpriteLayer.OPAQUE)));
        }
        index = 0;
        for (JsonValue f : frames) {
            doc.setFrameDuration(index++, f.getInt("duration", SpriteDocument.DEFAULT_FRAME_MS));
        }
        List<SpriteTag> tags = new ArrayList<>();
        for (JsonValue t : array(root, "tags")) {
            tags.add(new SpriteTag(t.getString("name"), t.getInt("from"), t.getInt("to"), SpriteTag.Direction.valueOf(t.getString("direction", "FORWARD"))));
        }
        doc.setTags(tags);
        List<SpriteSlice> slices = new ArrayList<>();
        for (JsonValue s : array(root, "slices")) {
            slices.add(new SpriteSlice(s.getString("name"), s.getInt("x"), s.getInt("y"), s.getInt("width"), s.getInt("height"),
                s.getInt("left", 0), s.getInt("right", 0), s.getInt("top", 0), s.getInt("bottom", 0), s.getInt("pivotX", 0), s.getInt("pivotY", 0)));
        }
        doc.setSlices(slices);
        List<Integer> palette = new ArrayList<>();
        for (JsonValue c : array(root, "palette")) {
            palette.add((int) Long.parseLong(c.asString(), 16));
        }
        doc.setPalette(palette.stream().mapToInt(Integer::intValue).toArray());
        for (JsonValue c : array(root, "cels")) {
            PixelPng.Image image = PixelPng.decode(Base64.getDecoder().decode(c.getString("png")));
            if (image.width() != doc.getWidth() || image.height() != doc.getHeight()) {
                throw new IllegalArgumentException("a cel is " + image.width() + "x" + image.height() + " but the canvas is " + doc.getWidth() + "x" + doc.getHeight());
            }
            doc.setCel(c.getInt("layer"), c.getInt("frame"), image.pixels());
        }
        return doc;
    }

    private static Iterable<JsonValue> array(JsonValue root, String name) {
        JsonValue v = root.get(name);
        return v == null ? List.<JsonValue>of() : v;
    }
}
