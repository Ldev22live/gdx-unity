package io.github.ldev22live.gdxunity.editor.sprite;

import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteLayerSource;
import io.github.ldev22live.gdxunity.engine.vector.Affine;
import io.github.ldev22live.gdxunity.engine.vector.Rgba;
import io.github.ldev22live.gdxunity.engine.vector.SvgImport;
import io.github.ldev22live.gdxunity.engine.vector.VectorDocument;
import io.github.ldev22live.gdxunity.engine.vector.VectorFile;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath;
import io.github.ldev22live.gdxunity.engine.vector.VectorRasterizer;
import io.github.ldev22live.gdxunity.engine.vector.VectorShape;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Vector editing in 2D Studio (TD-104, roadmap MC.3): one open {@code .vector} drawing, edited by shape name, every edit one undo
 * step of this session's own history. Documents are immutable, so a step keeps the drawing before and after. Rasterizing into a
 * sprite layer is an edit of the sprite session instead (one step there), so the vector stays the source and the layer can be
 * redrawn from it at any time.
 */
public final class VectorSession {
    private final CommandHistory history = new CommandHistory();
    private VectorDocument document;
    private File file;
    private boolean dirty;
    private List<String> lastImportReport = List.of();

    public VectorSession(VectorDocument document) {
        this.document = Objects.requireNonNull(document, "document cannot be null");
    }

    /** Opens a {@code .vector} file. */
    public static VectorSession open(File file) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        try {
            VectorSession session = new VectorSession(VectorFile.read(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            session.file = file;
            return session;
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot open " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Starts a session from SVG text in the TD-104 subset. What the subset does not cover is listed in {@link #getLastImportReport()};
     * nothing is guessed for it.
     */
    public static VectorSession importSvg(String svg) {
        SvgImport.Result result = SvgImport.read(svg);
        VectorSession session = new VectorSession(result.document());
        session.lastImportReport = result.unsupported();
        session.dirty = true;
        return session;
    }

    /** Writes the drawing to {@code target} as a {@code .vector} file and remembers it as the session's file. */
    public void saveAs(File target) throws IOException {
        Objects.requireNonNull(target, "target cannot be null");
        Files.writeString(target.toPath(), VectorFile.write(document), StandardCharsets.UTF_8);
        file = target;
        dirty = false;
    }

    public VectorDocument getDocument() {
        return document;
    }

    public CommandHistory getHistory() {
        return history;
    }

    public File getFile() {
        return file;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** What the last SVG import could not read, one line each; empty when everything was read or the session was not imported. */
    public List<String> getLastImportReport() {
        return lastImportReport;
    }

    public boolean undo() {
        return history.undo();
    }

    public boolean redo() {
        return history.redo();
    }

    /** Sets a shape's fill colour, or removes its fill with null. */
    public boolean setFill(String shape, Rgba fill) {
        return change("Fill " + shape, shape, s -> s.withFill(fill));
    }

    /** Sets a shape's stroke colour and width, or removes the stroke with a null colour. */
    public boolean setStroke(String shape, Rgba stroke, float width) {
        return change("Stroke " + shape, shape, s -> s.withStroke(stroke, stroke == null ? 0f : width));
    }

    /** Moves one point of a shape's path (an end point or a control point) to (x, y) in the shape's coordinates. */
    public boolean movePoint(String shape, VectorPath.PointRef point, float x, float y) {
        return change("Move point of " + shape, shape, s -> s.withPath(s.path().withPoint(point, x, y)));
    }

    /** Moves several points of a shape by the same offset, in the shape's coordinates, as one step (a dragged selection of nodes). */
    public boolean movePoints(String shape, Collection<VectorPath.PointRef> points, float dx, float dy) {
        Set<VectorPath.PointRef> unique = Set.copyOf(points);
        return change("Move points of " + shape, shape, s -> {
            VectorPath path = s.path();
            for (VectorPath.PointRef point : unique) {
                float[] at = path.point(point);
                path = path.withPoint(point, at[0] + dx, at[1] + dy);
            }
            return s.withPath(path);
        });
    }

    /** Inserts a straight node after segment {@code after} of a shape's path (the segment must not be its last close). */
    public boolean insertNode(String shape, int after, float x, float y) {
        return change("Add node to " + shape, shape, s -> {
            List<VectorPath.Segment> segments = new ArrayList<>(s.path().segments());
            if (after < 0 || after >= segments.size() || segments.get(after) instanceof VectorPath.Close) {
                throw new IllegalArgumentException("No node can follow segment " + after + " of " + shape);
            }
            segments.add(after + 1, new VectorPath.LineTo(x, y));
            return s.withPath(new VectorPath(segments));
        });
    }

    /** Deletes the node at the end of segment {@code index}, which must be a line or curve (not a subpath's move or a close). */
    public boolean deleteNode(String shape, int index) {
        return change("Delete node of " + shape, shape, s -> {
            List<VectorPath.Segment> segments = new ArrayList<>(s.path().segments());
            if (index <= 0 || index >= segments.size() || segments.get(index) instanceof VectorPath.MoveTo
                || segments.get(index) instanceof VectorPath.Close) {
                throw new IllegalArgumentException("Segment " + index + " of " + shape + " has no deletable node");
            }
            segments.remove(index);
            return s.withPath(new VectorPath(segments));
        });
    }

    /** Moves a whole shape by (dx, dy) in drawing coordinates. */
    public boolean moveShape(String shape, float dx, float dy) {
        return change("Move " + shape, shape, s -> s.withTransform(Affine.translate(dx, dy).times(s.transform())));
    }

    /** Puts a shape at draw order {@code index}, 0 being the back. */
    public boolean reorder(String shape, int index) {
        return replace("Reorder " + shape, document.moving(shape, index));
    }

    public boolean addShape(VectorShape shape) {
        return replace("Add " + shape.name(), document.adding(shape));
    }

    public boolean removeShape(String shape) {
        return replace("Delete " + shape, document.removing(shape));
    }

    /**
     * Draws shapes of this drawing into one cel of a sprite, at the sprite's canvas size, replacing that cel; one undo step of the
     * sprite session. The cel is cleared first, so redrawing after a vector edit gives exactly the new drawing.
     *
     * @param shapes the shapes to draw, by name, in their drawing order; empty for all
     * @return whether the cel changed
     */
    public boolean rasterizeInto(SpriteSession sprite, int layer, int frame, Collection<String> shapes) {
        return rasterizeInto(sprite, layer, frame, shapes, null);
    }

    /**
     * As {@link #rasterizeInto(SpriteSession, int, int, Collection)}, also linking the layer to this drawing in the same undo step, so
     * the sprite records where the layer comes from and Edit 2D Asset can open it (TD-104).
     *
     * @param vectorPath this drawing's project-relative path, or null to leave the layer's link as it is
     */
    public boolean rasterizeInto(SpriteSession sprite, int layer, int frame, Collection<String> shapes, String vectorPath) {
        Objects.requireNonNull(sprite, "sprite cannot be null");
        Objects.requireNonNull(shapes, "shapes cannot be null");
        for (String name : shapes) {
            document.shape(name); // fails for an unknown name before anything changes
        }
        Set<String> wanted = Set.copyOf(shapes);
        VectorDocument subset = wanted.isEmpty() ? document : new VectorDocument(document.width(), document.height(),
            document.shapes().stream().filter(s -> wanted.contains(s.name())).toList());
        var target = sprite.getDocument();
        int[] pixels = VectorRasterizer.render(subset, target.getWidth(), target.getHeight());
        return sprite.edit("Draw " + (wanted.isEmpty() ? "drawing" : String.join(", ", shapes)) + " into layer",
            d -> {
                d.setCel(layer, frame, pixels);
                if (vectorPath != null) {
                    d.setLayerSource(layer, new SpriteLayerSource(vectorPath, List.copyOf(shapes)));
                }
            });
    }

    private boolean change(String description, String shape, UnaryOperator<VectorShape> edit) {
        VectorShape before = document.shape(shape);
        return replace(description, document.with(edit.apply(before)));
    }

    private boolean replace(String description, VectorDocument after) {
        VectorDocument before = document;
        if (after.equals(before)) {
            return false;
        }
        boolean done = history.execute(new EditorCommand() {
            @Override
            public boolean execute() {
                document = after;
                return true;
            }

            @Override
            public boolean undo() {
                document = before;
                return true;
            }

            @Override
            public String getDescription() {
                return description;
            }
        });
        if (done) {
            dirty = true;
        }
        return done;
    }
}
