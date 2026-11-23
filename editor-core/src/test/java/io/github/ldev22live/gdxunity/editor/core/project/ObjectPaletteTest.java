package io.github.ldev22live.gdxunity.editor.core.project;

import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The World strip's palette catalogue (roadmap M2b.3): groups, thumbnails, licences and search over the shipped prefabs; and their
 * gameplay collision (roadmap M2b.4, TD-096).
 */
class ObjectPaletteTest {
    private static final File SHIPPED = new File("../assets");
    /** Objects a character walks through on purpose (TD-096): plants, so a bush never snags the player. */
    private static final Set<String> WALK_THROUGH = Set.of("Bush", "CC0 Bush");
    /** The collider types a placed object may carry (TD-096): cheap static shapes, never a triangle mesh. */
    private static final Set<String> PLACED_COLLIDERS = Set.of("BoxCollider3DComponent", "CapsuleCollider3DComponent",
        "SphereCollider3DComponent", "ConvexHullCollider3DComponent");

    @TempDir
    Path temp;

    private static List<ObjectPalette.Item> shipped() throws IOException {
        List<String> prefabs = new ArrayList<>();
        for (String prefab : StarterPalette.prefabs(SHIPPED)) {
            if (!prefab.startsWith(StarterPalette.CHARACTER_FOLDER + "/")) {
                prefabs.add(prefab);
            }
        }
        return ObjectPalette.describe(SHIPPED, prefabs);
    }

    @Test
    void shouldGroupTheShippedPrefabsIntoTreesRocksPlantsAndBuildings() throws IOException {
        Map<String, ObjectPalette.Category> groups = new LinkedHashMap<>();
        shipped().forEach(item -> groups.put(item.label(), item.category()));

        assertEquals(ObjectPalette.Category.TREES, groups.get("Oak Tree"));
        assertEquals(ObjectPalette.Category.TREES, groups.get("Pine Tree"));
        assertEquals(ObjectPalette.Category.TREES, groups.get("CC0 Oak Tree"));
        assertEquals(ObjectPalette.Category.TREES, groups.get("CC0 Pine Tree"));
        assertEquals(ObjectPalette.Category.PLANTS, groups.get("CC0 Bush"));
        assertEquals(ObjectPalette.Category.ROCKS, groups.get("Rock"));
        assertEquals(ObjectPalette.Category.ROCKS, groups.get("Boulder"));
        assertEquals(ObjectPalette.Category.ROCKS, groups.get("CC0 Rock"));
        assertEquals(ObjectPalette.Category.PLANTS, groups.get("Bush"));
        assertEquals(ObjectPalette.Category.BUILDINGS, groups.get("CC0 House"));
        assertEquals(ObjectPalette.Category.BUILDINGS, groups.get("Cottage"));
        assertFalse(groups.containsValue(ObjectPalette.Category.OTHER), "every shipped object has a group: " + groups);
        assertEquals(List.of(ObjectPalette.Category.TREES, ObjectPalette.Category.ROCKS, ObjectPalette.Category.PLANTS,
            ObjectPalette.Category.BUILDINGS), ObjectPalette.categories(shipped()));
    }

    @Test
    void shouldGiveEveryShippedObjectAThumbnail() throws IOException {
        for (ObjectPalette.Item item : shipped()) {
            assertFalse(item.thumbnail().isEmpty(), item.label() + " has no thumbnail: run tools/make_palette_thumbnails.py");
            assertTrue(new File(SHIPPED, item.thumbnail()).isFile(), item.thumbnail());
        }
    }

    @Test
    void shouldSayWhereEachObjectsFilesComeFrom() throws IOException {
        Map<String, String> licences = new LinkedHashMap<>();
        shipped().forEach(item -> licences.put(item.label(), item.licence()));

        assertEquals("CC0 1.0 · Nature Kit by Kenney (kenney.nl)", licences.get("CC0 Oak Tree"));
        assertEquals("CC0 1.0 · Building Kit by Kenney (kenney.nl)", licences.get("CC0 House"));
        assertEquals(ObjectPalette.MADE_IN_FORGEROOM, licences.get("Oak Tree"), "built from ForgeRoom's own shapes");
    }

    @Test
    void shouldSearchByNameAndFilterByGroup() throws IOException {
        List<ObjectPalette.Item> items = shipped();

        assertEquals(List.of("CC0 Oak Tree", "Oak Tree"), labels(ObjectPalette.filter(items, null, "oak")));
        assertEquals(List.of("CC0 Oak Tree", "CC0 Pine Tree"), labels(ObjectPalette.filter(items, ObjectPalette.Category.TREES, "cc0")));
        assertEquals(List.of("CC0 Rock"), labels(ObjectPalette.filter(items, null, "CC0 rock")), "every word must match");
        assertEquals(labels(ObjectPalette.filter(items, ObjectPalette.Category.ROCKS, "")),
            labels(ObjectPalette.filter(items, null, "rocks")), "a group's name is searchable");
        assertTrue(ObjectPalette.filter(items, ObjectPalette.Category.PLANTS, "oak").isEmpty());
    }

    @Test
    void shouldShowNoThumbnailWhenThereIsNoneAndAnOtherGroupForAnUnknownName() throws IOException {
        Files.createDirectories(temp.resolve("prefabs/level"));
        Files.writeString(temp.resolve("prefabs/level/Lamp Post.prefab"), "{\"kind\": \"prefab\"}");

        ObjectPalette.Item lamp = ObjectPalette.describe(temp.toFile(), List.of("prefabs/level/Lamp Post.prefab")).get(0);

        assertEquals("", lamp.thumbnail());
        assertEquals(ObjectPalette.Category.OTHER, lamp.category());
        assertEquals(ObjectPalette.MADE_IN_FORGEROOM, lamp.licence(), "no licence manifest at all");
    }

    @Test
    void shouldGiveEveryShippedObjectThatBlocksAStaticColliderOfASimpleShape() throws IOException {
        for (ObjectPalette.Item item : shipped()) {
            List<String> types = componentTypes(item.path());
            List<String> colliders = types.stream().filter(type -> type.endsWith("Collider3DComponent")).toList();

            if (WALK_THROUGH.contains(item.label())) {
                assertEquals(List.of(), colliders, item.label() + " is walked through");
                continue;
            }
            assertFalse(colliders.isEmpty(), item.label() + " has no collider: add one in its generator (tools/make_level_prefabs.py, "
                + "tools/make_starter_pack.py)");
            assertTrue(PLACED_COLLIDERS.containsAll(colliders), item.label() + " has a collider a placed object should not carry: "
                + colliders);
            assertFalse(types.contains("RigidBody3DComponent"), item.label() + " must stay static: no rigid body");
        }
    }

    @Test
    void shouldLetTheCc0HouseDoorwayStayOpenAndCloseEveryOtherWall() throws IOException {
        PrefabDocument house = PrefabDocument.parse(Files.readString(new File(SHIPPED, "prefabs/buildings/CC0 House.prefab").toPath()));
        int doorwayColliders = 0;
        for (UUID id : house.getEntityIds()) {
            JsonValue entity = house.getEntity(id);
            String name = entity.getString("name");
            boolean collides = !colliders(entity).isEmpty();
            if (name.startsWith("Wall ") || name.startsWith("Floor") || name.startsWith("Roof ")) {
                boolean doorway = model(entity).endsWith("wall-doorway-square.glb");
                assertEquals(!doorway, collides, name + (doorway ? ": the doorway piece itself is open" : " blocks"));
            }
            if (name.startsWith("Collision ")) {
                assertTrue(model(house.getEntity(house.getParent(id))).endsWith("wall-doorway-square.glb"), name + " belongs to the doorway");
                doorwayColliders += colliders(entity).size();
            }
        }
        assertEquals(3, doorwayColliders, "two jambs and a lintel frame the opening");
    }

    /** Every component type of every entity in the prefab, children included. */
    private static List<String> componentTypes(String prefab) throws IOException {
        PrefabDocument document = PrefabDocument.parse(Files.readString(new File(SHIPPED, prefab).toPath()));
        List<String> types = new ArrayList<>();
        for (UUID id : document.getEntityIds()) {
            for (JsonValue component = document.getEntity(id).get("components").child; component != null; component = component.next) {
                types.add(component.getString("type"));
            }
        }
        return types;
    }

    private static List<String> colliders(JsonValue entity) {
        List<String> found = new ArrayList<>();
        for (JsonValue component = entity.get("components").child; component != null; component = component.next) {
            if (component.getString("type").endsWith("Collider3DComponent")) {
                found.add(component.getString("type"));
            }
        }
        return found;
    }

    private static String model(JsonValue entity) {
        for (JsonValue component = entity.get("components").child; component != null; component = component.next) {
            if ("ModelComponent".equals(component.getString("type"))) {
                return component.get("data").getString("model");
            }
        }
        return "";
    }

    private static List<String> labels(List<ObjectPalette.Item> items) {
        return items.stream().map(ObjectPalette.Item::label).toList();
    }
}
