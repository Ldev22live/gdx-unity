package io.github.ldev22live.gdxunity.editor.core.prefab;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.SceneSnapshotCommand;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Prefabs: reusable entity trees saved as {@code .prefab} assets, placed as linked instances (TD-027).
 * <p>
 * Every scene change made here is one {@link SceneSnapshotCommand}, so it is a single undo step. Applying
 * overrides also rewrites the prefab file; undo restores the previous file contents.
 * <p>
 * Override model: an instance property is overridden when it differs from the prefab. Overrides are computed
 * by comparison while editing and stored on the instance's {@link PrefabInstanceComponent}s when the scene is
 * saved, so they survive the prefab changing while the scene is closed.
 */
public class PrefabService {
    /** Folder under the asset root where new prefabs are created. */
    public static final String PREFAB_FOLDER = "prefabs";
    public static final String EXTENSION = ".prefab";

    private final EditorState editorState;
    private final File assetRoot;
    private final Map<String, PrefabDocument> cache = new HashMap<>();

    public PrefabService(EditorState editorState, File assetRoot) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        if (serializer().getRegistry().getByTypeId(PrefabInstanceComponentSerializer.TYPE_ID) == null) {
            serializer().getRegistry().register(new PrefabInstanceComponentSerializer());
        }
    }

    // --- Queries ---

    /** The entity's prefab link, or null when it is not part of an instance. */
    public static PrefabInstanceComponent linkOf(Entity entity) {
        return entity != null ? entity.getComponent(PrefabInstanceComponent.class) : null;
    }

    /** The root of the instance {@code entity} belongs to, or null. */
    public static Entity instanceRootOf(Entity entity) {
        PrefabInstanceComponent link = linkOf(entity);
        if (link == null) {
            return null;
        }
        Entity current = entity;
        while (current != null) {
            PrefabInstanceComponent currentLink = linkOf(current);
            if (currentLink != null && currentLink.isRoot() && currentLink.getPrefabPath().equalsIgnoreCase(link.getPrefabPath())) {
                return current;
            }
            Transform parent = current.getTransform().getParent();
            current = parent != null ? parent.getEntity() : null;
        }
        return null;
    }

    /** The prefab document at {@code path}, read from disk on first use. */
    public PrefabDocument getDocument(String path) throws IOException {
        String key = cacheKey(path);
        PrefabDocument document = cache.get(key);
        if (document == null) {
            File file = fileOf(path);
            if (!file.isFile()) {
                throw new IOException("Prefab not found: " + path);
            }
            document = PrefabDocument.parse(Files.readString(file.toPath()));
            cache.put(key, document);
        }
        return document;
    }

    /**
     * Overridden property paths of an instance entity (see {@link PrefabState}), excluding the instance root's
     * own name and placement. Empty when the entity is not linked or its prefab cannot be read.
     */
    public Set<String> getOverrides(Entity entity) {
        PrefabInstanceComponent link = linkOf(entity);
        if (link == null) {
            return Set.of();
        }
        try {
            return computeOverrides(entity, getDocument(link.getPrefabPath()));
        } catch (IOException | SceneSerializationException e) {
            return Set.of();
        }
    }

    /** Number of overridden properties over every entity of the instance {@code entity} belongs to. */
    public int countInstanceOverrides(Entity entity) {
        Entity root = instanceRootOf(entity);
        if (root == null) {
            return 0;
        }
        int count = 0;
        for (Entity member : linkedMembers(root, linkOf(root).getPrefabPath()).values()) {
            count += getOverrides(member).size();
        }
        return count;
    }

    // --- Operations (each one undo step) ---

    /**
     * Saves {@code root} and its children as a new prefab under {@link #PREFAB_FOLDER} and turns them into an
     * instance of it. Prefab links already inside the tree are replaced (nested prefabs are not supported).
     *
     * @return the new prefab asset, or null when nothing was created (reason in the status bar)
     */
    public AssetReference createPrefab(Entity root) {
        Objects.requireNonNull(root, "root cannot be null");
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Cannot create a prefab during Play/Pause mode", true);
            return null;
        }
        List<Entity> members = subtree(root);
        Map<Entity, UUID> sourceIds = new LinkedHashMap<>();
        for (Entity member : members) {
            sourceIds.put(member, UUID.randomUUID());
        }
        PrefabDocument document = buildDocument(root.getName(), root, members, sourceIds, null);
        AssetReference asset = new AssetReference(uniquePrefabPath(root.getName()));
        try {
            writeDocument(asset.getPath(), document.toJson());
        } catch (IOException e) {
            editorState.setStatusMessage("Could not save prefab: " + e.getMessage(), true);
            return null;
        }
        cache.put(cacheKey(asset.getPath()), document);
        rescanAssets();

        boolean linked = editorState.executeCommand(new SceneSnapshotCommand(editorState,
            "Create Prefab '" + document.getName() + "'", () -> {
                for (Entity member : members) {
                    PrefabInstanceComponent old = linkOf(member);
                    if (old != null) {
                        member.removeComponent(old);
                    }
                    PrefabInstanceComponent link = new PrefabInstanceComponent(asset.getPath(), sourceIds.get(member), member == root);
                    if (member == root) {
                        link.setKnownSourceIds(new LinkedHashSet<>(document.getEntityIds()));
                    }
                    member.addComponent(link);
                }
                return true;
            }));
        if (!linked) {
            return null;
        }
        editorState.setStatusMessage("Created prefab " + asset.getPath(), false);
        return asset;
    }

    /**
     * Places a new instance of the prefab with its root at {@code position} and selects it.
     *
     * @return the instance root, or null when the prefab could not be read or placed
     */
    public Entity instantiate(AssetReference prefab, Vector3 position) {
        Objects.requireNonNull(prefab, "prefab cannot be null");
        Objects.requireNonNull(position, "position cannot be null");
        PrefabDocument document;
        try {
            document = getDocument(prefab.getPath());
        } catch (IOException | SceneSerializationException e) {
            editorState.setStatusMessage("Cannot place " + prefab.getFileName() + ": " + e.getMessage(), true);
            return null;
        }
        Entity[] created = new Entity[1];
        boolean placed = editorState.executeCommand(new SceneSnapshotCommand(editorState,
            "Place Prefab '" + document.getName() + "'", () -> {
                Scene scene = editorState.getActiveScene();
                Map<UUID, Entity> bySource = new HashMap<>();
                for (UUID sourceId : document.getEntityIds()) {
                    Entity parent = bySource.get(document.getParent(sourceId));
                    bySource.put(sourceId, createMember(scene, document, prefab.getPath(), sourceId, parent));
                }
                Entity root = bySource.get(document.getRootId());
                root.getTransform().setLocalPosition(position);
                linkOf(root).setKnownSourceIds(new LinkedHashSet<>(document.getEntityIds()));
                created[0] = root;
                return true;
            }));
        if (!placed) {
            return null;
        }
        editorState.setSelectedEntity(created[0]);
        editorState.setStatusMessage("Placed prefab " + prefab.getFileName(), false);
        return created[0];
    }

    /**
     * Writes every override of the instance {@code entity} belongs to into its prefab, then updates the other
     * instances in the scene (keeping their own overrides). Entities added to this instance become part of the
     * prefab; prefab entities deleted from it are removed from the prefab.
     */
    public boolean applyAll(Entity entity) {
        Entity root = instanceRootOf(entity);
        if (root == null) {
            editorState.setStatusMessage("Not a prefab instance", true);
            return false;
        }
        String path = linkOf(root).getPrefabPath();
        PrefabDocument oldDocument;
        String oldText;
        try {
            oldDocument = getDocument(path);
            oldText = Files.readString(fileOf(path).toPath());
        } catch (IOException | SceneSerializationException e) {
            editorState.setStatusMessage("Cannot apply to " + path + ": " + e.getMessage(), true);
            return false;
        }
        String[] newText = new String[1];
        SceneSnapshotCommand command = new SceneSnapshotCommand(editorState, "Apply Prefab '" + oldDocument.getName() + "'", () -> {
            Scene scene = editorState.getActiveScene();
            List<Entity> others = otherInstanceRoots(scene, path, root);
            Map<Entity, Set<String>> otherOverrides = new HashMap<>();
            for (Entity other : others) {
                for (Entity member : linkedMembers(other, path).values()) {
                    otherOverrides.put(member, computeOverrides(member, oldDocument));
                }
            }

            List<Entity> members = subtree(root);
            Map<Entity, UUID> sourceIds = new LinkedHashMap<>();
            for (Entity member : members) {
                PrefabInstanceComponent link = linkOf(member);
                boolean ours = link != null && link.getPrefabPath().equalsIgnoreCase(path);
                sourceIds.put(member, ours ? link.getSourceId() : UUID.randomUUID());
            }
            PrefabDocument newDocument = buildDocument(oldDocument.getName(), root, members, sourceIds,
                oldDocument.getEntity(oldDocument.getRootId()));
            newText[0] = newDocument.toJson();
            writeDocument(path, newText[0]);
            cache.put(cacheKey(path), newDocument);

            for (Entity member : members) {
                PrefabInstanceComponent link = linkOf(member);
                if (link == null || !link.getPrefabPath().equalsIgnoreCase(path)) {
                    if (link != null) {
                        member.removeComponent(link);
                    }
                    member.addComponent(new PrefabInstanceComponent(path, sourceIds.get(member), false));
                }
            }
            linkOf(root).setKnownSourceIds(new LinkedHashSet<>(newDocument.getEntityIds()));
            for (Entity other : others) {
                syncInstance(other, newDocument, member -> otherOverrides.getOrDefault(member, linkOf(member).getOverrides()));
            }
            storeOverrides(scene);
            return true;
        }) {
            @Override
            protected void afterRestore(boolean redo) {
                restoreFile(path, redo ? newText[0] : oldText);
            }
        };
        if (!editorState.executeCommand(command)) {
            return false;
        }
        editorState.setStatusMessage("Applied overrides to " + path, false);
        return true;
    }

    /**
     * Discards every override of the instance {@code entity} belongs to: properties return to the prefab's,
     * deleted prefab entities come back and entities added to the instance are removed.
     */
    public boolean revertAll(Entity entity) {
        Entity root = instanceRootOf(entity);
        if (root == null) {
            editorState.setStatusMessage("Not a prefab instance", true);
            return false;
        }
        String path = linkOf(root).getPrefabPath();
        PrefabDocument document;
        try {
            document = getDocument(path);
        } catch (IOException | SceneSerializationException e) {
            editorState.setStatusMessage("Cannot revert " + path + ": " + e.getMessage(), true);
            return false;
        }
        boolean reverted = editorState.executeCommand(new SceneSnapshotCommand(editorState, "Revert Prefab Instance", () -> {
            Scene scene = editorState.getActiveScene();
            Map<UUID, Entity> linked = linkedMembers(root, path);
            for (Entity member : subtree(root)) {
                if (!linked.containsValue(member) && member.getScene() == scene) {
                    reparentChildren(member);
                    scene.removeEntity(member, false);
                }
            }
            linkOf(root).setKnownSourceIds(Set.of());
            syncInstance(root, document, member -> Set.of());
            return true;
        }));
        if (reverted) {
            editorState.setStatusMessage("Reverted " + root.getName() + " to " + path, false);
        }
        return reverted;
    }

    /** Discards the overrides of one component (or of an added component) on an instance entity. */
    public boolean revertComponent(Entity entity, Component component) {
        PrefabInstanceComponent link = linkOf(entity);
        String key = PrefabState.componentKey(serializer(), entity, component);
        Entity root = instanceRootOf(entity);
        if (link == null || key == null || root == null) {
            return false;
        }
        PrefabDocument document;
        try {
            document = getDocument(link.getPrefabPath());
        } catch (IOException | SceneSerializationException e) {
            editorState.setStatusMessage("Cannot revert: " + e.getMessage(), true);
            return false;
        }
        Set<String> remaining = new LinkedHashSet<>(computeOverrides(entity, document));
        if (!remaining.removeIf(p -> key.equals(PrefabState.componentKeyOf(p)))) {
            return false;
        }
        return editorState.executeCommand(new SceneSnapshotCommand(editorState, "Revert Component Overrides", () -> {
            Map<Entity, Set<String>> live = new HashMap<>();
            for (Entity member : linkedMembers(root, link.getPrefabPath()).values()) {
                live.put(member, member == entity ? remaining : computeOverrides(member, document));
            }
            linkOf(root).setKnownSourceIds(new LinkedHashSet<>(document.getEntityIds()));
            syncInstance(root, document, live::get);
            return true;
        }));
    }

    /** Turns the instance {@code entity} belongs to into plain entities that no longer follow the prefab. */
    public boolean unpack(Entity entity) {
        Entity root = instanceRootOf(entity);
        if (root == null) {
            editorState.setStatusMessage("Not a prefab instance", true);
            return false;
        }
        String path = linkOf(root).getPrefabPath();
        boolean unpacked = editorState.executeCommand(new SceneSnapshotCommand(editorState, "Unpack Prefab", () -> {
            for (Entity member : linkedMembers(root, path).values()) {
                member.removeComponent(linkOf(member));
            }
            return true;
        }));
        if (unpacked) {
            editorState.setStatusMessage("Unpacked " + root.getName(), false);
        }
        return unpacked;
    }

    // --- Scene file hooks ---

    /**
     * Brings every instance in a freshly loaded scene up to date with its prefab, using the overrides stored
     * at save time. Instances whose prefab is missing or unreadable are left as saved.
     *
     * @return one message per instance that could not be updated
     */
    public List<String> syncScene(Scene scene) {
        cache.clear();
        List<String> problems = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            PrefabInstanceComponent link = linkOf(entity);
            if (link == null || !link.isRoot() || entity.getScene() != scene) {
                continue;
            }
            try {
                PrefabDocument document = getDocument(link.getPrefabPath());
                syncInstance(entity, document, member -> linkOf(member).getOverrides());
            } catch (IOException | SceneSerializationException e) {
                problems.add(entity.getName() + ": " + e.getMessage());
            }
        }
        return problems;
    }

    /** Records each instance entity's current overrides on its link, ready to be saved with the scene. */
    public void storeOverrides(Scene scene) {
        for (Entity entity : scene.getAllEntities()) {
            PrefabInstanceComponent link = linkOf(entity);
            if (link == null) {
                continue;
            }
            try {
                link.setOverrides(computeOverrides(entity, getDocument(link.getPrefabPath())));
            } catch (IOException | SceneSerializationException e) {
                // Prefab unreadable: keep the overrides stored when the scene was loaded
                editorState.setStatusMessage("Prefab " + link.getPrefabPath() + " unreadable; kept stored overrides", true);
            }
        }
    }

    // --- Internals ---

    private SceneSerializer serializer() {
        return editorState.getSerializer();
    }

    private Set<String> computeOverrides(Entity entity, PrefabDocument document) {
        PrefabInstanceComponent link = linkOf(entity);
        if (link == null || !document.contains(link.getSourceId())) {
            return Set.of();
        }
        Set<String> paths = PrefabState.diff(
            PrefabState.flatten(document.getEntity(link.getSourceId())),
            PrefabState.flatten(serializer().entityToJsonValue(entity)));
        if (link.isRoot()) {
            paths.removeAll(PrefabState.ROOT_PATHS);
        }
        return paths;
    }

    /**
     * Updates one instance to {@code document}: linked entities take the prefab's values except their
     * overrides, entities new to the prefab are created, entities removed from the prefab are deleted.
     * Prefab entities the user deleted from this instance (known at the last sync) stay deleted.
     */
    private void syncInstance(Entity root, PrefabDocument document, Function<Entity, Set<String>> overridesOf) {
        Scene scene = root.getScene();
        PrefabInstanceComponent rootLink = linkOf(root);
        String path = rootLink.getPrefabPath();
        Map<UUID, Entity> linked = linkedMembers(root, path);
        Set<UUID> known = new LinkedHashSet<>(rootLink.getKnownSourceIds());

        for (UUID sourceId : document.getEntityIds()) {
            Entity member = linked.get(sourceId);
            Entity prefabParent = linked.get(document.getParent(sourceId));
            if (member == null) {
                boolean newInPrefab = !known.contains(sourceId);
                if (newInPrefab && prefabParent != null) {
                    linked.put(sourceId, createMember(scene, document, path, sourceId, prefabParent));
                }
                continue;
            }
            Set<String> overrides = new LinkedHashSet<>(overridesOf.apply(member));
            if (member == root) {
                overrides.addAll(PrefabState.ROOT_PATHS);
            } else if (prefabParent != null && member.getTransform().getParent() != prefabParent.getTransform()) {
                member.getTransform().setParent(prefabParent.getTransform(), false);
            }
            JsonValue merged = PrefabState.merge(document.getEntity(sourceId), serializer().entityToJsonValue(member), overrides);
            PrefabState.applyTo(member, merged, serializer());
        }

        for (Map.Entry<UUID, Entity> entry : linked.entrySet()) {
            Entity member = entry.getValue();
            if (member != root && !document.contains(entry.getKey()) && member.getScene() == scene) {
                reparentChildren(member);
                scene.removeEntity(member, false);
            }
        }
        rootLink.setKnownSourceIds(new LinkedHashSet<>(document.getEntityIds()));
        for (Entity member : linkedMembers(root, path).values()) {
            linkOf(member).setOverrides(computeOverrides(member, document));
        }
    }

    private Entity createMember(Scene scene, PrefabDocument document, String path, UUID sourceId, Entity parent) {
        JsonValue state = document.getEntity(sourceId);
        Entity entity = new Entity(state.getString(PrefabState.NAME, "Entity"));
        scene.addEntity(entity);
        if (parent != null) {
            entity.getTransform().setParent(parent.getTransform(), false);
        }
        PrefabState.applyTo(entity, state, serializer());
        entity.addComponent(new PrefabInstanceComponent(path, sourceId, parent == null));
        return entity;
    }

    /**
     * Builds a prefab from a tree of scene entities.
     *
     * @param keepRootPlacement prefab root state whose name and placement are kept (Apply), or null to take
     *                          them from {@code root} with the position reset to the origin (Create)
     */
    private PrefabDocument buildDocument(String name, Entity root, List<Entity> members, Map<Entity, UUID> sourceIds,
                                         JsonValue keepRootPlacement) {
        Map<UUID, JsonValue> entities = new LinkedHashMap<>();
        Map<UUID, UUID> parents = new LinkedHashMap<>();
        for (Entity member : members) {
            JsonValue state = serializer().entityToJsonValue(member);
            if (member == root) {
                Map<String, JsonValue> flat = PrefabState.flatten(state);
                if (keepRootPlacement != null) {
                    Map<String, JsonValue> kept = PrefabState.flatten(keepRootPlacement);
                    for (String rootPath : PrefabState.ROOT_PATHS) {
                        if (kept.containsKey(rootPath)) {
                            flat.put(rootPath, kept.get(rootPath));
                        }
                    }
                } else {
                    flat.put("transform/position/x", new JsonValue(0.0));
                    flat.put("transform/position/y", new JsonValue(0.0));
                    flat.put("transform/position/z", new JsonValue(0.0));
                }
                state = PrefabState.unflatten(flat);
            } else {
                state = PrefabState.unflatten(PrefabState.flatten(state));
                Transform parent = member.getTransform().getParent();
                parents.put(sourceIds.get(member), sourceIds.get(parent.getEntity()));
            }
            entities.put(sourceIds.get(member), state);
        }
        return new PrefabDocument(name, sourceIds.get(root), entities, parents);
    }

    /** Entities of the instance under {@code root} linked to {@code path}, by prefab entity id. */
    private static Map<UUID, Entity> linkedMembers(Entity root, String path) {
        Map<UUID, Entity> result = new LinkedHashMap<>();
        for (Entity member : subtree(root)) {
            PrefabInstanceComponent link = linkOf(member);
            if (link != null && link.getPrefabPath().equalsIgnoreCase(path)) {
                result.putIfAbsent(link.getSourceId(), member);
            }
        }
        return result;
    }

    private static List<Entity> otherInstanceRoots(Scene scene, String path, Entity except) {
        List<Entity> roots = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            PrefabInstanceComponent link = linkOf(entity);
            if (entity != except && link != null && link.isRoot() && link.getPrefabPath().equalsIgnoreCase(path)) {
                roots.add(entity);
            }
        }
        return roots;
    }

    /** {@code root} and all descendants, parents first. */
    private static List<Entity> subtree(Entity root) {
        List<Entity> result = new ArrayList<>();
        List<Entity> stack = new ArrayList<>(List.of(root));
        while (!stack.isEmpty()) {
            Entity entity = stack.remove(stack.size() - 1);
            result.add(entity);
            List<Transform> children = entity.getTransform().getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i).getEntity());
            }
        }
        return result;
    }

    private static void reparentChildren(Entity entity) {
        Transform parent = entity.getTransform().getParent();
        for (Transform child : new ArrayList<>(entity.getTransform().getChildren())) {
            child.setParent(parent, true);
        }
    }

    private String uniquePrefabPath(String entityName) {
        String base = entityName.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (base.isEmpty()) {
            base = "Prefab";
        }
        String candidate = PREFAB_FOLDER + "/" + base + EXTENSION;
        for (int n = 1; fileOf(candidate).exists(); n++) {
            candidate = PREFAB_FOLDER + "/" + base + " " + n + EXTENSION;
        }
        return candidate;
    }

    private File fileOf(String path) {
        return new File(assetRoot, new AssetReference(path).getPath());
    }

    private void writeDocument(String path, String text) throws IOException {
        File file = fileOf(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        Files.writeString(file.toPath(), text);
    }

    private void restoreFile(String path, String text) {
        try {
            writeDocument(path, text);
            cache.put(cacheKey(path), PrefabDocument.parse(text));
        } catch (IOException e) {
            editorState.setStatusMessage("Could not restore prefab file " + path + ": " + e.getMessage(), true);
        }
    }

    private void rescanAssets() {
        if (editorState.getAssetService() != null) {
            editorState.getAssetService().getCatalog().scan();
        }
    }

    private static String cacheKey(String path) {
        return new AssetReference(path).getPath().toLowerCase(Locale.ROOT);
    }
}
