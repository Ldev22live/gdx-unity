package io.github.ldev22live.gdxunity.editor.core.project.contract;

import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shared project contract on the desktop (TD-106, roadmap A12.3, A12.4, A12.5, A12.8, A12.11). */
class ProjectContractTest {
    private static final Path FIXTURE = Path.of("..", "fixtures", "contract-v1");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T13:00:00Z"), ZoneOffset.UTC);
    private static final String SCENE = "scenes/valley.json";

    @TempDir
    Path temp;

    private Path copyFixture(String name) throws IOException {
        Path target = temp.resolve(name);
        Path source = FIXTURE.resolve("valley-project");
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
        return target;
    }

    private static LocalProjectStore store(Path root, CapabilityPolicy policy) {
        return new LocalProjectStore(root, policy, "0.1.0-test", CLOCK, Map.of());
    }

    private static LocalProjectStore store(Path root) {
        return store(root, CapabilityPolicy.desktop());
    }

    private static String scene(Path root) throws IOException {
        return Files.readString(root.resolve(SCENE), StandardCharsets.UTF_8);
    }

    /** A legacy project: the fixture without its manifest and with a format-1 marker. */
    private Path legacyProject() throws IOException {
        Path root = copyFixture("legacy");
        Files.delete(root.resolve(ProjectManifest.FILE_NAME));
        Files.writeString(root.resolve(EditorProject.MARKER_FILE), "format=1\nname=Legacy Valley\nstartScene=" + SCENE + "\n");
        return root;
    }

    @Test
    void shouldOpenALegacyProjectWithoutWritingAnything() throws IOException {
        Path root = legacyProject();
        String marker = Files.readString(root.resolve(EditorProject.MARKER_FILE));

        ProjectStore.OpenResult opened = store(root).open();

        assertNull(opened.manifest());
        assertEquals(CapabilityPolicy.Access.FULL, opened.evaluation().access());
        assertFalse(Files.exists(root.resolve(ProjectManifest.FILE_NAME)), "opening never adds a manifest");
        assertEquals(marker, Files.readString(root.resolve(EditorProject.MARKER_FILE)), "nor rewrites the marker");
        assertInstanceOf(ProjectStore.SaveResult.NeedsManifest.class, store(root).save(null, Map.of(SCENE, scene(root)), Set.of(), false),
            "a save without the person's agreement writes nothing");
        assertFalse(Files.exists(root.resolve(ProjectManifest.FILE_NAME)));
    }

    @Test
    void shouldCreateTheManifestOnAnAgreedSaveAndKeepItsIdentityAfterwards() throws IOException {
        Path root = legacyProject();

        ProjectStore.SaveResult first = store(root).save(null, Map.of(SCENE, scene(root)), Set.of("ownership/1"), true);
        ProjectManifest manifest = assertInstanceOf(ProjectStore.SaveResult.Saved.class, first).manifest();

        assertNull(manifest.revision().base(), "the first revision is based on none");
        assertEquals("Legacy Valley", manifest.name());
        assertEquals(List.of("scene/1", "preserve-unknown/1", "ownership/1"), manifest.required());
        assertEquals(List.of("materials/Meadow.material", SCENE), manifest.assets().stream().map(ProjectManifest.AssetEntry::path).toList(),
            "the scene and the material it reaches");
        ProjectMarker marker = ProjectMarker.read(root);
        assertEquals(List.of(), marker.inconsistencies(manifest), "the marker mirrors the manifest");
        assertEquals(2, marker.format());
        assertEquals(List.of(), AssetInventory.verify(root, manifest), "every hash matches the files on disk");

        ProjectStore.SaveResult second = store(root).save(manifest.revision().id(), Map.of(SCENE, scene(root) + " "), Set.of(), false);
        ProjectManifest next = assertInstanceOf(ProjectStore.SaveResult.Saved.class, second).manifest();
        assertEquals(manifest.projectId(), next.projectId(), "the project id is stable across saves");
        assertEquals(manifest.revision().id(), next.revision().base());
        assertNotEquals(manifest.revision().id(), next.revision().id());
        assertEquals(manifest.scenes(), next.scenes(), "scene ids are stable");
    }

    @Test
    void shouldReadTheFixtureManifestAndRefuseInvalidOnes() throws IOException {
        Path root = copyFixture("valid");
        ProjectStore.OpenResult opened = store(root).open();
        assertEquals("Contract Valley", opened.manifest().name());
        assertEquals(CapabilityPolicy.Access.FULL, opened.evaluation().access(), opened.notes().toString());
        assertTrue(opened.notes().isEmpty(), opened.notes().toString());

        String future = Files.readString(FIXTURE.resolve("invalid/future-version.manifest.json"));
        assertTrue(assertThrows(ProjectManifest.ManifestException.class, () -> ProjectManifest.parse(future)).getMessage()
            .contains("contract version 2 needs a newer editor"));
        String traversal = Files.readString(FIXTURE.resolve("invalid/path-traversal.manifest.json"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(traversal)).getMessage()
            .contains("leaves the project folder"));
        String unknownMember = Files.readString(root.resolve(ProjectManifest.FILE_NAME)).replace("\"name\"", "\"owner\": 1, \"name\"");
        assertThrows(ProjectManifest.ManifestException.class, () -> ProjectManifest.parse(unknownMember));
        String badHash = Files.readString(root.resolve(ProjectManifest.FILE_NAME)).replaceFirst("\"sha256\": \"[0-9a-f]", "\"sha256\": \"Z");
        assertThrows(ProjectManifest.ManifestException.class, () -> ProjectManifest.parse(badHash));
    }

    @Test
    void shouldRefuseUnsafePathsByTextAndOnTheFileSystem() throws IOException {
        for (String bad : List.of("../x", "a/../../b", "/etc/passwd", "C:/Windows", "c:x", "a\\b", "a//b", "./a", "a/%2e%2e/b",
            "a%2Fb", "%5c..")) {
            assertThrows(ProjectPaths.UnsafePathException.class, () -> ProjectPaths.checkText(bad), bad);
        }
        for (String good : List.of("materials/Meadow.material", "Models/GLB format/wall.glb", "a/b..c/d%20e.png")) {
            ProjectPaths.checkText(good);
        }
        Path root = copyFixture("paths");
        Path outside = Files.createDirectories(temp.resolve("outside"));
        try {
            Files.createSymbolicLink(root.resolve("escape"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            return; // creating symbolic links needs a privilege on Windows; the text checks above still ran
        }
        assertTrue(assertThrows(ProjectPaths.UnsafePathException.class, () -> ProjectPaths.resolve(root, "escape/file.txt"))
            .getMessage().contains("symbolic link"));
    }

    @Test
    void shouldReportMissingAndModifiedAssetsWithoutSubstitutingAnything() throws IOException {
        Path root = copyFixture("assets");
        Files.writeString(root.resolve("materials/Meadow.material"), "{\"version\": 1, \"name\": \"Changed\"}");
        Files.delete(root.resolve(SCENE));

        ProjectManifest manifest = ProjectManifest.parse(Files.readString(root.resolve(ProjectManifest.FILE_NAME)));
        List<AssetInventory.Finding> findings = AssetInventory.verify(root, manifest);

        assertEquals(List.of(AssetInventory.Issue.MODIFIED, AssetInventory.Issue.MISSING),
            findings.stream().map(AssetInventory.Finding::issue).toList());
        assertFalse(Files.exists(root.resolve(SCENE)), "nothing was put in the missing file's place");
    }

    @Test
    void shouldOpenReadOnlyAndRefuseToSaveWithoutARequiredCapability() throws IOException {
        Path root = copyFixture("required");
        String manifestBefore = Files.readString(root.resolve(ProjectManifest.FILE_NAME));
        CapabilityPolicy webToday = new CapabilityPolicy("This web editor", Set.of("scene/1", "preserve-unknown/1", "material/1"));

        ProjectStore.OpenResult opened = store(root, webToday).open();

        assertEquals(CapabilityPolicy.Access.READ_ONLY, opened.evaluation().access());
        assertEquals(List.of("scenario/1", "ownership/1"), opened.evaluation().missingRequired());
        assertTrue(opened.notes().stream().anyMatch(n -> n.contains("read-only") && n.contains("scenario/1")), opened.notes().toString());
        ProjectStore.SaveResult refused = store(root, webToday).save(opened.revision(), Map.of(SCENE, "{}"), Set.of(), false);
        assertInstanceOf(ProjectStore.SaveResult.ReadOnly.class, refused);
        assertEquals(manifestBefore, Files.readString(root.resolve(ProjectManifest.FILE_NAME)), "nothing was written");
    }

    @Test
    void shouldEditWithLimitsAndKeepOptionalCapabilitiesItLacks() throws IOException {
        Path root = copyFixture("optional");
        CapabilityPolicy noProfiles = new CapabilityPolicy("A client without profiles",
            Set.of("scene/1", "preserve-unknown/1", "material/1", "scenario/1", "ownership/1", "physics3d/1"));

        ProjectStore.OpenResult opened = store(root, noProfiles).open();
        assertEquals(CapabilityPolicy.Access.LIMITED, opened.evaluation().access());
        assertEquals(List.of("profile:action-rpg/3d"), opened.evaluation().missingOptional());
        ProjectManifest saved = assertInstanceOf(ProjectStore.SaveResult.Saved.class,
            store(root, noProfiles).save(opened.revision(), Map.of(SCENE, scene(root)), Set.of(), false)).manifest();

        assertEquals(List.of("profile:action-rpg/3d", "physics3d/1"), saved.optional(), "its capabilities are kept, not removed");
        assertEquals(opened.manifest().required(), saved.required());
        assertEquals(opened.manifest().extensions(), saved.extensions(), "extensions are kept unchanged");
    }

    @Test
    void shouldGiveAConflictToTheSecondClientAndOfferCopyOrReload() throws IOException {
        Path root = copyFixture("shared");
        String base = store(root).open().revision();
        String original = scene(root);

        ProjectStore.SaveResult a = store(root).save(base, Map.of(SCENE, original.replace("Contract Valley", "Valley A")), Set.of(), false);
        ProjectStore.SaveResult b = store(root).save(base, Map.of(SCENE, original.replace("Contract Valley", "Valley B")), Set.of(), false);

        String revisionA = assertInstanceOf(ProjectStore.SaveResult.Saved.class, a).manifest().revision().id();
        ProjectStore.SaveResult.Conflict conflict = assertInstanceOf(ProjectStore.SaveResult.Conflict.class, b);
        assertEquals(base, conflict.yourBase());
        assertEquals(revisionA, conflict.current());
        assertTrue(scene(root).contains("Valley A"), "A's save was not overwritten: no last-write-wins");

        // "Reload theirs": B opens the current revision
        assertEquals(revisionA, store(root).open().revision());
        // "Save mine as a copy": B's text becomes a new project with a new identity
        ProjectStore.OpenResult copy = store(root).saveAsCopy("Valley B copy",
            Map.of(SCENE, original.replace("Contract Valley", "Valley B")));
        assertNotEquals(store(root).open().manifest().projectId(), copy.manifest().projectId(), "a copy is a new project");
        assertNull(copy.manifest().revision().base());
        assertEquals("Valley B copy", copy.manifest().name());
        assertTrue(Files.readString(temp.resolve("Valley B copy").resolve(SCENE)).contains("Valley B"));
        assertTrue(scene(root).contains("Valley A"), "the original is untouched");
    }

    @Test
    void shouldFinishACommittedSaveAndUndoAnUncommittedOne() throws IOException {
        Path root = copyFixture("crash");
        LocalProjectStore store = store(root);
        String base = store.open().revision();
        String before = scene(root);

        // An uncommitted save: pending files written, no journal yet
        Files.writeString(root.resolve(SCENE + LocalProjectStore.PENDING + "x"), "half-written");
        Files.writeString(root.resolve(ProjectManifest.FILE_NAME + LocalProjectStore.PENDING + "x"), "{");
        ProjectStore.OpenResult rolledBack = store.open();
        assertEquals(base, rolledBack.revision());
        assertEquals(before, scene(root));
        assertFalse(Files.exists(root.resolve(SCENE + LocalProjectStore.PENDING + "x")), "unfinished files removed");
        assertTrue(rolledBack.notes().stream().anyMatch(n -> n.contains("was undone")), rolledBack.notes().toString());

        // A committed save interrupted before its moves: complete pending files and a journal
        ProjectManifest saved = assertInstanceOf(ProjectStore.SaveResult.Saved.class,
            store.save(base, Map.of(SCENE, before.replace("Contract Valley", "Saved Valley")), Set.of(), false)).manifest();
        String committed = Files.readString(root.resolve(ProjectManifest.FILE_NAME));
        String sceneText = scene(root);
        Files.writeString(root.resolve(ProjectManifest.FILE_NAME), "corrupted by the crash");
        Files.writeString(root.resolve(SCENE + LocalProjectStore.PENDING + "r2"), sceneText);
        Files.writeString(root.resolve(ProjectManifest.FILE_NAME + LocalProjectStore.PENDING + "r2"), committed);
        Files.writeString(root.resolve(LocalProjectStore.JOURNAL), "{\"revision\": \"r2\", \"moves\": ["
            + "{\"from\": \"" + SCENE + LocalProjectStore.PENDING + "r2\", \"to\": \"" + SCENE + "\"}, "
            + "{\"from\": \"" + ProjectManifest.FILE_NAME + LocalProjectStore.PENDING + "r2\", \"to\": \"" + ProjectManifest.FILE_NAME + "\"}]}");

        ProjectStore.OpenResult rolledForward = store.open();

        assertEquals(saved.revision().id(), rolledForward.revision(), "the interrupted save was completed");
        assertTrue(scene(root).contains("Saved Valley"));
        assertFalse(Files.exists(root.resolve(LocalProjectStore.JOURNAL)));
        assertEquals(List.of(), AssetInventory.verify(root, rolledForward.manifest()), "scene and manifest belong to one revision");
    }
}
