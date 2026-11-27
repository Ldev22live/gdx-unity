package io.github.ldev22live.gdxunity.editor.sculpt;

import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the 3D Modeling mesh drafts of each model part for as long as the editor runs (roadmap A06, TD-084): leaving 3D Modeling, Back
 * to World or selecting something else never discards an unsaved geometry edit, and never saves one either. Coming back to the same
 * part continues with the same drafts and their undo history. Only Save ({@link #markSaved}) or an explicit {@link #discard} ends
 * the "unsaved" state.
 */
public final class MeshDraftKeeper {
    /** Opens a new save session (and its drafts) for a part the first time it is edited. */
    @FunctionalInterface
    public interface Opener {
        MeshSaveSession open() throws IOException;
    }

    private record Key(UUID entityId, String modelPath) {
    }

    private static final class Kept {
        final Entity part;
        final MeshSaveSession session;
        /** The draft geometries at the last save; null before the first save (then "unsaved" means "modified"). */
        List<MeshPartEditor.Geometry> saved;

        Kept(Entity part, MeshSaveSession session) {
            this.part = part;
            this.session = session;
        }

        boolean unsaved() {
            List<MeshPartEditor> drafts = session.drafts();
            for (int i = 0; i < drafts.size(); i++) {
                MeshPartEditor draft = drafts.get(i);
                if (saved == null ? draft.isModified() : draft.geometry() != saved.get(i)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Map<Key, Kept> kept = new LinkedHashMap<>();

    /**
     * The kept session of {@code part} (the same drafts as last time), or a new one from {@code opener} when the part has none or its
     * model file changed since.
     */
    public MeshSaveSession session(Entity part, Opener opener) throws IOException {
        Objects.requireNonNull(part, "part cannot be null");
        Objects.requireNonNull(opener, "opener cannot be null");
        Key key = key(part);
        Kept existing = kept.get(key);
        if (existing != null && existing.part == part) {
            return existing.session;
        }
        Kept fresh = new Kept(part, Objects.requireNonNull(opener.open(), "opener returned no session"));
        kept.put(key, fresh);
        return fresh.session;
    }

    /** Records that {@code session} was just saved: its drafts are no longer unsaved until they change again. */
    public void markSaved(MeshSaveSession session) {
        for (Kept entry : kept.values()) {
            if (entry.session == session) {
                List<MeshPartEditor.Geometry> geometries = new ArrayList<>();
                for (MeshPartEditor draft : session.drafts()) {
                    geometries.add(draft.geometry());
                }
                entry.saved = geometries;
            }
        }
    }

    /** True when {@code session}'s drafts changed since they were opened or last saved. */
    public boolean isUnsaved(MeshSaveSession session) {
        return kept.values().stream().anyMatch(entry -> entry.session == session && entry.unsaved());
    }

    /** Drops the drafts of {@code part} (an explicit Discard); the next edit starts again from the model file. */
    public boolean discard(Entity part) {
        return kept.remove(key(part)) != null;
    }

    /** The names of the parts with unsaved mesh edits, in the order they were first edited. */
    public List<String> unsavedParts() {
        List<String> names = new ArrayList<>();
        for (Kept entry : kept.values()) {
            if (entry.unsaved()) {
                names.add(entry.part.getName());
            }
        }
        return names;
    }

    /** What Back to World says when mesh edits are unsaved (they are kept, neither saved nor dropped), or empty when there are none. */
    public Optional<String> backToWorldNotice() {
        List<String> names = unsavedParts();
        if (names.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Unsaved mesh edits of " + String.join(", ", names) + " are kept, not saved: open Customize > Edit 3D "
            + "Model to Save or Discard them. The World shows the saved model until then.");
    }

    private static Key key(Entity part) {
        ModelComponent model = part.getComponent(ModelComponent.class);
        return new Key(part.getId(), model == null ? "" : model.getModelPath());
    }
}
