package io.github.ldev22live.gdxunity.editor.sprite;

import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteLayerSource;
import io.github.ldev22live.gdxunity.engine.vector.VectorDocument;
import io.github.ldev22live.gdxunity.engine.vector.VectorRasterizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Edit 2D Asset's vector side (TD-104, roadmap MC.3): the layers of an open sprite that are drawn from a {@code .vector} drawing, the
 * drawings themselves (opened once each, from the project), and redrawing a layer after its drawing changed. The vector is the source
 * and the layer its picture: a redraw replaces the layer's pixels in <b>every frame</b> with the drawing, as one undo step of the
 * sprite, so pixels painted over a linked layer by hand are lost on the next redraw. {@link #isPaintedOver} says so beforehand.
 *
 * <p>The drawing and the sprite keep their own undo histories: undoing a vector edit and redrawing is a new sprite step.
 */
public final class SpriteVectorSources {
    /**
     * One layer drawn from a vector.
     *
     * @param layer     the layer's index in the sprite
     * @param layerName its name
     * @param source    the drawing and the shapes drawn into it
     */
    public record Link(int layer, String layerName, SpriteLayerSource source) { }

    private final SpriteSession sprite;
    private final Path projectRoot;
    private final Map<String, VectorSession> vectors = new LinkedHashMap<>();

    /**
     * @param sprite      the open sprite
     * @param projectRoot the project folder the layer sources are relative to
     */
    public SpriteVectorSources(SpriteSession sprite, Path projectRoot) {
        this.sprite = Objects.requireNonNull(sprite, "sprite cannot be null");
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
    }

    public SpriteSession getSprite() {
        return sprite;
    }

    /** Every linked layer, bottom to top. */
    public List<Link> links() {
        SpriteDocument doc = sprite.getDocument();
        List<Link> links = new ArrayList<>();
        for (int layer = 0; layer < doc.getLayerCount(); layer++) {
            SpriteLayerSource source = doc.getLayerSource(layer);
            if (source != null) {
                links.add(new Link(layer, doc.getLayer(layer).name(), source));
            }
        }
        return links;
    }

    /** The link of {@code layer}, or null when the layer was painted in the sprite itself. */
    public Link linkOf(int layer) {
        SpriteLayerSource source = sprite.getDocument().getLayerSource(layer);
        return source == null ? null : new Link(layer, sprite.getDocument().getLayer(layer).name(), source);
    }

    /**
     * The drawing at {@code vectorPath}, opened from the project the first time it is asked for and kept, so its edits and undo
     * history last while the sprite is open.
     *
     * @throws IOException naming the file when it is missing or not a vector file
     */
    public VectorSession vector(String vectorPath) throws IOException {
        Objects.requireNonNull(vectorPath, "vectorPath cannot be null");
        VectorSession open = vectors.get(vectorPath);
        if (open == null) {
            File file = projectRoot.resolve(vectorPath).toFile();
            if (!file.isFile()) {
                throw new IOException("The layer's vector source " + vectorPath + " is not in the project; the layer's pixels are all "
                    + "that is left of it");
            }
            open = VectorSession.open(file);
            vectors.put(vectorPath, open);
        }
        return open;
    }

    /**
     * Draws {@code shapes} of the drawing at {@code vectorPath} into {@code layer} in every frame and links the layer to it, as one
     * undo step.
     *
     * @param shapes the shapes, by name; empty for the whole drawing
     */
    public boolean link(int layer, String vectorPath, List<String> shapes) throws IOException {
        SpriteLayerSource source = new SpriteLayerSource(vectorPath, shapes);
        int[] pixels = render(source);
        return sprite.edit("Draw " + vectorPath + " into layer " + sprite.getDocument().getLayer(layer).name(), d -> {
            fillFrames(d, layer, pixels);
            d.setLayerSource(layer, source);
        });
    }

    /** Draws the linked layer again from its drawing as it is now, in every frame, as one undo step; false when nothing changed. */
    public boolean redraw(int layer) throws IOException {
        Link link = requireLink(layer);
        int[] pixels = render(link.source());
        return sprite.edit("Redraw " + link.layerName() + " from " + link.source().vector(), d -> fillFrames(d, layer, pixels));
    }

    /** Redraws every layer linked to {@code vectorPath}, as one undo step; false when nothing changed. */
    public boolean redrawFrom(String vectorPath) throws IOException {
        Map<Integer, int[]> drawn = new LinkedHashMap<>();
        for (Link link : links()) {
            if (link.source().vector().equals(vectorPath)) {
                drawn.put(link.layer(), render(link.source()));
            }
        }
        if (drawn.isEmpty()) {
            return false;
        }
        return sprite.edit("Redraw from " + vectorPath, d -> drawn.forEach((layer, pixels) -> fillFrames(d, layer, pixels)));
    }

    /**
     * Whether some frame of the linked layer differs from what its drawing gives now: painted over by hand, or the drawing changed and
     * the layer was not redrawn. A redraw would replace those pixels.
     */
    public boolean isPaintedOver(int layer) throws IOException {
        Link link = requireLink(layer);
        int[] pixels = render(link.source());
        SpriteDocument doc = sprite.getDocument();
        for (int frame = 0; frame < doc.getFrameCount(); frame++) {
            if (!Arrays.equals(pixels, doc.copyCel(layer, frame))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Saves every changed drawing to its file, then the sprite to its file (the sprite last, so it never names a drawing that was not
     * written).
     *
     * @return the files written, drawings first
     * @throws IllegalStateException when the sprite has never been saved, so it has no file
     */
    public List<File> save() throws IOException {
        if (sprite.getFile() == null) {
            throw new IllegalStateException("Save the sprite once with a name first");
        }
        List<File> written = saveVectors();
        sprite.saveAs(sprite.getFile());
        written.add(sprite.getFile());
        return written;
    }

    /** Saves every changed drawing to its file; for a sprite saved under a new name, before it is written. Returns the files written. */
    public List<File> saveVectors() throws IOException {
        List<File> written = new ArrayList<>();
        for (VectorSession vector : vectors.values()) {
            if (vector.isDirty()) {
                vector.saveAs(vector.getFile());
                written.add(vector.getFile());
            }
        }
        return written;
    }

    /** Whether a drawing opened through this object has unsaved changes. */
    public boolean hasUnsavedVectors() {
        return vectors.values().stream().anyMatch(VectorSession::isDirty);
    }

    private Link requireLink(int layer) {
        Link link = linkOf(layer);
        if (link == null) {
            throw new IllegalArgumentException("Layer " + sprite.getDocument().getLayer(layer).name() + " is not drawn from a vector");
        }
        return link;
    }

    private int[] render(SpriteLayerSource source) throws IOException {
        VectorDocument drawing = vector(source.vector()).getDocument();
        for (String name : source.shapes()) {
            drawing.shape(name); // fails for a shape the drawing no longer has, before anything changes
        }
        Set<String> wanted = Set.copyOf(source.shapes());
        VectorDocument subset = wanted.isEmpty() ? drawing : new VectorDocument(drawing.width(), drawing.height(),
            drawing.shapes().stream().filter(s -> wanted.contains(s.name())).toList());
        SpriteDocument doc = sprite.getDocument();
        return VectorRasterizer.render(subset, doc.getWidth(), doc.getHeight());
    }

    private static void fillFrames(SpriteDocument doc, int layer, int[] pixels) {
        for (int frame = 0; frame < doc.getFrameCount(); frame++) {
            doc.setCel(layer, frame, pixels); // setCel keeps a copy
        }
    }

    /** Whether {@code path} exists in the project; for a panel that lists links before opening them. */
    public boolean exists(String vectorPath) {
        return Files.isRegularFile(projectRoot.resolve(vectorPath));
    }
}
