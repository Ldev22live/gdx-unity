package io.github.ldev22live.gdxunity.editor.core.project.contract;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The desktop's {@link ProjectStore}: a project folder (TD-106, roadmap A12.3, A12.4 and A12.11).
 *
 * <p><b>Legacy projects</b> (a format-1 marker, no manifest) open unchanged; nothing is written on opening. A manifest is created only
 * by a save the person agreed to ({@code createManifest}); without that, a save answers {@link SaveResult.NeedsManifest}.
 *
 * <p><b>Recoverable saves.</b> Every file of a save (scenes, then the manifest and the marker) is first written beside its target as
 * {@code <name>.pending-<revision>}. Then a journal {@value #JOURNAL} listing the moves is written: that is the commit point. Then
 * each pending file is moved over its target and the journal is deleted. {@link #open()} finishes an interrupted save whose journal
 * exists (every pending file is complete) and deletes pending files that have no journal (the save never committed), so the marker,
 * manifest and scenes always belong to one revision.
 *
 * <p><b>Revisions.</b> A save's base is compared with the stored revision while holding a lock on {@value #LOCK}, so two editors
 * on one machine cannot both save from the same base.
 */
public final class LocalProjectStore implements ProjectStore {
    /** The save journal at the project root. */
    public static final String JOURNAL = ".forgeroom-save-journal.json";
    /** The lock file at the project root. */
    public static final String LOCK = ".forgeroom-save.lock";
    /** The infix of a file written but not yet moved into place. */
    public static final String PENDING = ".pending-";

    private final Path root;
    private final CapabilityPolicy policy;
    private final String clientVersion;
    private final Clock clock;
    private final Map<String, String> licences;
    private final Supplier<String> ids;

    /**
     * A store for the project folder {@code root}.
     *
     * @param licences licence record ids by project path (TD-094), copied into asset entries
     */
    public LocalProjectStore(Path root, CapabilityPolicy policy, String clientVersion, Clock clock, Map<String, String> licences) {
        this(root, policy, clientVersion, clock, licences, () -> UUID.randomUUID().toString());
    }

    /**
     * A store whose new ids (projects, scenes, revisions) come from {@code ids}: random UUIDs in the editor; a fixed sequence in tests
     * that must write the same bytes every run.
     */
    public LocalProjectStore(Path root, CapabilityPolicy policy, String clientVersion, Clock clock, Map<String, String> licences,
                             Supplier<String> ids) {
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
        this.root = realPath(Objects.requireNonNull(root, "root cannot be null"));
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.clientVersion = Objects.requireNonNull(clientVersion, "clientVersion cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.licences = Map.copyOf(Objects.requireNonNull(licences, "licences cannot be null"));
    }

    /**
     * {@code path} as one spelling: its real path when it exists (Windows short names and links resolved), else absolute and
     * normalised. Comparing two paths needs one spelling, or a file inside the project could look outside it.
     */
    public static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /** The project folder. */
    public Path root() {
        return root;
    }

    @Override
    public OpenResult open() throws IOException {
        List<String> notes = new ArrayList<>(recover());
        if (!Files.isRegularFile(root.resolve(EditorProject.MARKER_FILE))) {
            throw new IOException("Not a project (no " + EditorProject.MARKER_FILE + "): " + root);
        }
        ProjectMarker marker = ProjectMarker.read(root);
        Path manifestFile = root.resolve(ProjectManifest.FILE_NAME);
        if (!Files.isRegularFile(manifestFile)) {
            if (marker.hasManifest()) {
                throw new IOException("The marker names " + marker.manifest() + ", which is missing; the project is not opened rather "
                    + "than guessed. Restore the manifest from a backup.");
            }
            notes.add("A project without a manifest (format " + marker.format() + "): opened as it is. A manifest is added only when "
                + "you agree on saving.");
            return new OpenResult(null, new CapabilityPolicy.Evaluation(CapabilityPolicy.Access.FULL, List.of(), List.of(), List.of()),
                notes);
        }
        ProjectManifest manifest = ProjectManifest.parse(Files.readString(manifestFile, StandardCharsets.UTF_8));
        for (String difference : marker.inconsistencies(manifest)) {
            notes.add("The marker disagrees with the manifest (" + difference + "); the manifest is used, and the marker is rewritten "
                + "on the next save.");
        }
        for (AssetInventory.Finding finding : AssetInventory.verify(root, manifest)) {
            notes.add("Asset " + finding.path() + ": " + finding.issue() + " (" + finding.detail() + "). Nothing was substituted.");
        }
        CapabilityPolicy.Evaluation evaluation = policy.evaluate(manifest);
        notes.addAll(evaluation.explanations());
        return new OpenResult(manifest, evaluation, notes);
    }

    @Override
    public SaveResult save(String baseRevision, Map<String, String> sceneTexts, Set<String> addedRequired, boolean createManifest)
        throws IOException {
        Objects.requireNonNull(sceneTexts, "sceneTexts cannot be null");
        Objects.requireNonNull(addedRequired, "addedRequired cannot be null");
        try (FileChannel channel = FileChannel.open(root.resolve(LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            OpenResult current = open();
            if (current.evaluation().readOnly()) {
                return new SaveResult.ReadOnly(current.evaluation().explanations());
            }
            ProjectManifest manifest = current.manifest();
            if (manifest == null && !createManifest) {
                return new SaveResult.NeedsManifest("This project has no manifest yet. Saving adds project-manifest.json and updates "
                    + EditorProject.MARKER_FILE + " to format 2; older editors can still open it.");
            }
            if (manifest != null && !Objects.equals(baseRevision, manifest.revision().id())) {
                return new SaveResult.Conflict(baseRevision, manifest.revision().id());
            }
            ProjectManifest next = nextManifest(manifest, sceneTexts, addedRequired);
            commit(sceneTexts, next);
            return new SaveResult.Saved(next);
        }
    }

    @Override
    public OpenResult saveAsCopy(String copyName, Map<String, String> sceneTexts) throws IOException {
        Objects.requireNonNull(copyName, "copyName cannot be null");
        Path target = root.resolveSibling(copyName);
        if (Files.exists(target)) {
            throw new IOException("Cannot save a copy over an existing folder: " + target);
        }
        copyProject(root, target);
        Files.deleteIfExists(target.resolve(ProjectManifest.FILE_NAME));
        LocalProjectStore copy = new LocalProjectStore(target, policy, clientVersion, clock, licences, ids);
        ProjectMarker old = ProjectMarker.read(target);
        // A new project: a new id and a first revision based on none; the name is the copy's
        Files.writeString(target.resolve(EditorProject.MARKER_FILE),
            new ProjectMarker(1, copyName, old.startScene(), null, null).toText(), StandardCharsets.UTF_8);
        SaveResult saved = copy.save(null, sceneTexts, Set.of(), true);
        if (!(saved instanceof SaveResult.Saved)) {
            throw new IOException("The copy could not be saved: " + saved);
        }
        return copy.open();
    }

    private ProjectManifest nextManifest(ProjectManifest manifest, Map<String, String> sceneTexts, Set<String> addedRequired)
        throws IOException {
        Path staging = Files.createTempDirectory("forgeroom-save");
        try {
            // Hash the scene texts as they will be written, and every other reached file as it is on disk
            List<String> scenePaths = new ArrayList<>();
            List<ProjectManifest.SceneEntry> scenes = new ArrayList<>();
            ProjectMarker marker = ProjectMarker.read(root);
            if (manifest != null) {
                scenes.addAll(manifest.scenes());
            } else {
                String start = Objects.requireNonNull(marker.startScene(), "a legacy project needs startScene in its marker");
                scenes.add(new ProjectManifest.SceneEntry(ids.get(), start));
            }
            for (String path : sceneTexts.keySet()) {
                ProjectPaths.checkText(path);
                if (scenes.stream().noneMatch(s -> s.path().equals(path))) {
                    scenes.add(new ProjectManifest.SceneEntry(ids.get(), path));
                }
            }
            scenes.forEach(s -> scenePaths.add(s.path()));
            List<ProjectManifest.AssetEntry> assets = new ArrayList<>();
            for (ProjectManifest.AssetEntry entry : AssetInventory.inventory(root, scenePaths, licences)) {
                String text = sceneTexts.get(entry.path());
                if (text == null) {
                    assets.add(entry);
                } else {
                    Path file = staging.resolve("scene.json");
                    Files.writeString(file, text, StandardCharsets.UTF_8);
                    assets.add(new ProjectManifest.AssetEntry(entry.path(), "scene", Files.size(file), AssetInventory.sha256(file),
                        entry.licence()));
                }
            }
            for (String path : sceneTexts.keySet()) {
                if (assets.stream().noneMatch(a -> a.path().equals(path))) {
                    Path file = staging.resolve("scene.json");
                    Files.writeString(file, sceneTexts.get(path), StandardCharsets.UTF_8);
                    assets.add(new ProjectManifest.AssetEntry(path, "scene", Files.size(file), AssetInventory.sha256(file), null));
                }
            }
            assets.sort((a, b) -> a.path().compareTo(b.path()));
            ProjectManifest.Revision revision = new ProjectManifest.Revision(ids.get(),
                manifest == null ? null : manifest.revision().id(), "desktop", clientVersion,
                Instant.now(clock).truncatedTo(ChronoUnit.MILLIS).toString());
            if (manifest == null) {
                return new ProjectManifest(ids.get(), marker.name() == null ? root.getFileName().toString()
                    : marker.name(), marker.startScene(), scenes, assets, CapabilityPolicy.requiredAfterSave(List.of("scene/1",
                    "preserve-unknown/1"), addedRequired), List.of(), revision, Map.of());
            }
            return new ProjectManifest(manifest.projectId(), manifest.name(), manifest.startScene(), scenes, assets,
                CapabilityPolicy.requiredAfterSave(manifest.required(), addedRequired), manifest.optional(), revision,
                manifest.extensions());
        } finally {
            try (Stream<Path> files = Files.list(staging)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(staging);
        }
    }

    /** Writes every file pending, writes the journal (the commit point), moves them in place, deletes the journal. */
    private void commit(Map<String, String> sceneTexts, ProjectManifest next) throws IOException {
        String revision = next.revision().id();
        Map<String, String> files = new LinkedHashMap<>(sceneTexts);
        files.put(ProjectManifest.FILE_NAME, next.toJson());
        files.put(EditorProject.MARKER_FILE, ProjectMarker.of(next).toText());
        Map<Path, Path> moves = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = ProjectPaths.resolve(root, file.getKey());
            Path pending = target.resolveSibling(target.getFileName() + PENDING + revision);
            Files.createDirectories(target.getParent());
            Files.writeString(pending, file.getValue(), StandardCharsets.UTF_8);
            moves.put(pending, target);
        }
        writeJournal(revision, moves);
        finishMoves(moves);
        Files.deleteIfExists(root.resolve(JOURNAL));
    }

    private void writeJournal(String revision, Map<Path, Path> moves) throws IOException {
        StringBuilder json = new StringBuilder("{\"revision\": ").append(PreservedFields.quote(revision)).append(", \"moves\": [");
        boolean first = true;
        for (Map.Entry<Path, Path> move : moves.entrySet()) {
            json.append(first ? "" : ", ").append("{\"from\": ").append(PreservedFields.quote(relative(move.getKey())))
                .append(", \"to\": ").append(PreservedFields.quote(relative(move.getValue()))).append('}');
            first = false;
        }
        Path pending = root.resolve(JOURNAL + PENDING + revision);
        Files.writeString(pending, json.append("]}\n").toString(), StandardCharsets.UTF_8);
        move(pending, root.resolve(JOURNAL));
    }

    private void finishMoves(Map<Path, Path> moves) throws IOException {
        for (Map.Entry<Path, Path> move : moves.entrySet()) {
            if (Files.exists(move.getKey())) {
                move(move.getKey(), move.getValue());
            }
        }
    }

    /**
     * Finishes a committed save (a journal exists) and removes pending files of a save that never committed. Returns notes for the
     * person.
     */
    private List<String> recover() throws IOException {
        List<String> notes = new ArrayList<>();
        Path journal = root.resolve(JOURNAL);
        if (Files.isRegularFile(journal)) {
            JsonValue moves = new JsonReader().parse(Files.readString(journal, StandardCharsets.UTF_8));
            Map<Path, Path> pending = new LinkedHashMap<>();
            for (JsonValue m = moves.get("moves").child; m != null; m = m.next) {
                pending.put(ProjectPaths.resolve(root, m.getString("from")), ProjectPaths.resolve(root, m.getString("to")));
            }
            finishMoves(pending);
            Files.delete(journal);
            notes.add("An interrupted save (revision " + moves.getString("revision") + ") was completed.");
        }
        List<Path> stray = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                return dir.equals(root) || !(name.equals("build") || name.startsWith(".")) ? FileVisitResult.CONTINUE
                    : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().contains(PENDING)) {
                    stray.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        for (Path file : stray) {
            Files.delete(file);
        }
        if (!stray.isEmpty()) {
            notes.add("An interrupted save that had not finished was undone (" + stray.size() + " unfinished files removed); the "
                + "project is as it was last saved.");
        }
        return notes;
    }

    private String relative(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyProject(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (!dir.equals(from) && (name.equals("build") || name.startsWith("."))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(to.resolve(from.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (!name.equals(LOCK) && !name.equals(JOURNAL) && !name.contains(PENDING)) {
                    Files.copy(file, to.resolve(from.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
