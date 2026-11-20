package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M9.3 (TD-093), done-when "a small edit gives a small diff": every bundled sample scene, once saved, gives the same text
 * on every later save, and a move, a rename, a switched-off component, a new entity, a deleted entity or one painted tile changes
 * only the lines of that edit. Also records, in {@code build/m9-3/first-save.tsv}, how many lines the first save of each bundled
 * file changes (the file as committed against the layout the editor writes).
 */
class SceneTextDiffTest {
    private static final Path SAMPLES = Path.of("..", "assets", "samples");
    private static final Path OUT = Path.of("build", "m9-3");

    /** Lines of an entity with no components: braces, id, name, active, parentId, the transform's 17 lines and "components": []. */
    private static final int ENTITY_LINES = 25;

    private static SceneSerializer serializer;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
        PhysicsSystem2D.ensureNativesInitialized();
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        serializer = new SceneSerializer(registry);
    }

    /**
     * The lines a line diff (as {@code git diff} shows it) removes and adds between two texts: the shortest edit script, by Myers'
     * algorithm, so unchanged lines between two changes are not counted.
     */
    record Change(int removed, int added) {
        static Change between(String before, String after) {
            String[] a = before.split("\n", -1);
            String[] b = after.split("\n", -1);
            int n = a.length;
            int m = b.length;
            int max = n + m;
            // v[max + k] is the furthest x reached on diagonal k (x - y = k) with d edits
            int[] v = new int[2 * max + 2];
            for (int d = 0; d <= max; d++) {
                for (int k = -d; k <= d; k += 2) {
                    boolean down = k == -d || (k != d && v[max + k - 1] < v[max + k + 1]);
                    int x = down ? v[max + k + 1] : v[max + k - 1] + 1;
                    int y = x - k;
                    while (x < n && y < m && a[x].equals(b[y])) {
                        x++;
                        y++;
                    }
                    v[max + k] = x;
                    if (x >= n && y >= m) {
                        int common = (n + m - d) / 2;
                        return new Change(n - common, m - common);
                    }
                }
            }
            throw new IllegalStateException("a diff never needs more edits than there are lines");
        }
    }

    private static List<Path> samples() throws IOException {
        try (Stream<Path> files = Files.list(SAMPLES)) {
            List<Path> list = files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
            assertTrue(list.size() >= 40, "bundled samples found: " + list.size());
            return list;
        }
    }

    private static String saved(Path file) throws IOException {
        Scene scene = serializer.fromJson(Files.readString(file, StandardCharsets.UTF_8));
        try {
            return serializer.toJson(scene);
        } finally {
            scene.dispose();
        }
    }

    /** Loads the saved text, applies the edit, saves again and gives the change. */
    private static Change edit(String saved, Consumer<Scene> change) {
        Scene scene = serializer.fromJson(saved);
        try {
            change.accept(scene);
            return Change.between(saved, serializer.toJson(scene));
        } finally {
            scene.dispose();
        }
    }

    @Test
    void shouldSaveEverySampleToTheSameTextEveryTime() throws IOException {
        List<String> unstable = new ArrayList<>();
        assertEquals(new Change(1, 2), Change.between("a\nb\nc\nd", "a\nB\nc\nd\ne"), "the line diff itself");
        assertEquals(new Change(0, 0), Change.between("a\nb", "a\nb"), "the line diff itself");
        StringBuilder report = new StringBuilder("file\tlines\tfirst save: lines removed\tlines added\n");
        for (Path file : samples()) {
            String once = saved(file);
            Scene again = serializer.fromJson(once);
            String twice = serializer.toJson(again);
            again.dispose();
            if (!once.equals(twice)) {
                unstable.add(file.getFileName() + " " + Change.between(once, twice));
            }
            assertTrue(once.endsWith("}\n") && !once.contains("\r") && !once.contains("\t"), file + " is not in the layout");
            String committed = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            Change first = Change.between(committed, once);
            report.append(file.getFileName()).append('\t').append(committed.split("\n").length).append('\t')
                .append(first.removed()).append('\t').append(first.added()).append('\n');
        }
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("first-save.tsv"), report);
        assertEquals(List.of(), unstable, "samples whose second save differs from the first");
    }

    @Test
    void shouldChangeOnlyTheEditedLinesInEverySample() throws IOException {
        List<String> large = new ArrayList<>();
        for (Path file : samples()) {
            String saved = saved(file);
            String name = file.getFileName().toString();

            Change moved = edit(saved, scene -> {
                Transform transform = last(scene).getTransform();
                transform.setLocalPosition(transform.getLocalPosition().x + 10f, transform.getLocalPosition().y,
                    transform.getLocalPosition().z);
            });
            check(large, name, "move x by 10", moved, 1, 1);

            check(large, name, "rename", edit(saved, scene -> last(scene).setName("Renamed in a test")), 1, 1);

            Change disabled = edit(saved, scene -> {
                for (Entity entity : scene.getAllEntities()) {
                    List<Component> components = entity.getAllComponents();
                    if (!components.isEmpty()) {
                        components.get(0).setEnabled(!components.get(0).isEnabled());
                        return;
                    }
                }
            });
            check(large, name, "switch a component off or on", disabled, 1, 1);

            // A new entity is written last. The previous entity's "    }" gains a comma, which a line diff shows as the new block
            // inserted just before that closing line: ENTITY_LINES added, nothing removed
            Change added = edit(saved, scene -> scene.createEntity("Added in a test"));
            if (!added.equals(new Change(0, ENTITY_LINES))) {
                large.add(name + ": add an entity gave " + added + ", not " + new Change(0, ENTITY_LINES));
            }

            Entity leaf = middleLeaf(serializer.fromJson(saved));
            if (leaf != null) {
                int leafLines = leafLines(saved, leaf);
                Change removed = edit(saved, scene -> scene.removeEntity(scene.findEntityById(leaf.getId())));
                check(large, name, "delete " + leaf.getName(), removed, leafLines, 0);
            }
        }
        assertEquals(List.of(), large, "edits whose diff was larger than the edit");
    }

    @Test
    void shouldChangeOneLineWhenOneTileIsPainted() throws IOException {
        Path file = SAMPLES.resolve("tilemap-platformer-2d.json");
        String saved = saved(file);
        Change painted = edit(saved, scene -> {
            for (Entity entity : scene.getAllEntities()) {
                TilemapComponent tilemap = entity.getComponent(TilemapComponent.class);
                if (tilemap != null) {
                    TileMapData map = tilemap.getMap();
                    map.set(0, 7, 5, map.get(0, 7, 5) == 3 ? 4 : 3);
                    return;
                }
            }
            throw new AssertionError("no tile map in " + file);
        });
        assertEquals(new Change(1, 1), painted, "one painted tile");
    }

    /** Writes a saved sample and the same sample with its last entity moved, for {@code git diff --no-index} in the report. */
    @Test
    void shouldWriteAMovedEntityPairForGitDiff() throws IOException {
        String saved = saved(SAMPLES.resolve("hopper-3d.json"));
        Scene scene = serializer.fromJson(saved);
        String moved;
        try {
            Transform transform = last(scene).getTransform();
            transform.setLocalPosition(transform.getLocalPosition().x + 10f, transform.getLocalPosition().y,
                transform.getLocalPosition().z);
            last(scene).setName(last(scene).getName() + " (moved)");
            moved = serializer.toJson(scene);
        } finally {
            scene.dispose();
        }
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("hopper-saved.json"), saved);
        Files.writeString(OUT.resolve("hopper-moved.json"), moved);
        assertEquals(new Change(2, 2), Change.between(saved, moved), "a move and a rename");
    }

    private static void check(List<String> large, String file, String what, Change change, int maxRemoved, int maxAdded) {
        if (change.removed() > maxRemoved || change.added() > maxAdded || change.removed() + change.added() == 0) {
            large.add(file + ": " + what + " gave " + change + " (at most " + maxRemoved + " removed and " + maxAdded + " added)");
        }
    }

    private static Entity last(Scene scene) {
        List<Entity> all = scene.getAllEntities();
        return all.get(all.size() - 1);
    }

    /** An entity near the middle of the file with no children, or null when there is none. */
    private static Entity middleLeaf(Scene scene) {
        List<Entity> all = scene.getAllEntities();
        try {
            for (int i = all.size() / 2; i < all.size(); i++) {
                if (all.get(i).getTransform().getChildren().isEmpty() && i < all.size() - 1) {
                    return all.get(i);
                }
            }
            return null;
        } finally {
            scene.dispose();
        }
    }

    /** How many lines the entity's block takes in the saved text, from its opening brace to its closing one and comma. */
    private static int leafLines(String saved, Entity leaf) {
        String[] lines = saved.split("\n");
        String idLine = "      \"id\": \"" + leaf.getId() + "\",";
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals(idLine)) {
                int end = i;
                while (!lines[end].equals("    },")) {
                    end++;
                }
                return end - (i - 1) + 1;
            }
        }
        throw new AssertionError("entity " + leaf.getName() + " not found in the saved text");
    }
}
