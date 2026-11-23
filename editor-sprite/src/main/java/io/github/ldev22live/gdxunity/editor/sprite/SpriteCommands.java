package io.github.ldev22live.gdxunity.editor.sprite;

import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;

import java.util.Objects;
import java.util.function.Consumer;

/** Undoable edits of a {@link SpriteDocument}. Each is one undo step. */
public final class SpriteCommands {
    private SpriteCommands() { }

    /** Changes the pixels of one cel; stores only the pixels that differ, so a stroke on a large canvas stays small. */
    public static final class SetPixels implements EditorCommand {
        private final SpriteDocument doc;
        private final int layer;
        private final int frame;
        private final int[] indices;
        private final int[] before;
        private final int[] after;
        private final String description;

        /** Compares {@code before} with {@code after} (both a whole cel) and keeps the differences. */
        public SetPixels(SpriteDocument doc, int layer, int frame, int[] before, int[] after, String description) {
            this.doc = Objects.requireNonNull(doc, "doc cannot be null");
            this.layer = layer;
            this.frame = frame;
            this.description = Objects.requireNonNull(description, "description cannot be null");
            int changed = 0;
            for (int i = 0; i < before.length; i++) {
                if (before[i] != after[i]) {
                    changed++;
                }
            }
            indices = new int[changed];
            this.before = new int[changed];
            this.after = new int[changed];
            int n = 0;
            for (int i = 0; i < before.length; i++) {
                if (before[i] != after[i]) {
                    indices[n] = i;
                    this.before[n] = before[i];
                    this.after[n] = after[i];
                    n++;
                }
            }
        }

        private void apply(int[] values) {
            int[] cel = doc.copyCel(layer, frame);
            for (int i = 0; i < indices.length; i++) {
                cel[indices[i]] = values[i];
            }
            doc.setCel(layer, frame, cel);
        }

        /** How many pixels this edit changes. */
        public int changedPixels() {
            return indices.length;
        }

        @Override
        public boolean execute() {
            if (indices.length == 0) {
                return false;
            }
            apply(after);
            return true;
        }

        @Override
        public boolean undo() {
            apply(before);
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /**
     * Any structural edit (layers, frames, tags, slices, palette, canvas size): runs {@code change} on the document and keeps a whole copy from before
     * and after, which is exact and simple; sprite documents are small.
     */
    public static final class Snapshot implements EditorCommand {
        private final SpriteDocument doc;
        private final Consumer<SpriteDocument> change;
        private final String description;
        private SpriteDocument before;
        private SpriteDocument after;

        public Snapshot(SpriteDocument doc, String description, Consumer<SpriteDocument> change) {
            this.doc = Objects.requireNonNull(doc, "doc cannot be null");
            this.description = Objects.requireNonNull(description, "description cannot be null");
            this.change = Objects.requireNonNull(change, "change cannot be null");
        }

        @Override
        public boolean execute() {
            if (after != null) {
                doc.restoreFrom(after);
                return true;
            }
            before = doc.copy();
            change.accept(doc);
            if (doc.contentEquals(before)) {
                before = null;
                return false;
            }
            after = doc.copy();
            return true;
        }

        @Override
        public boolean undo() {
            doc.restoreFrom(before);
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
}
