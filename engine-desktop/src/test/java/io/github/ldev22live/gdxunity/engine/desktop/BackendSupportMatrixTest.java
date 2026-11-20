package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M9.4, done-when "every feature has a recorded status per backend": {@code docs/BACKEND_SUPPORT_MATRIX.md} has one
 * row per component type the game runtime registers, each with a status for the desktop, TeaVM and Babylon backends from a
 * fixed vocabulary, and those statuses agree with the code:
 * <ul>
 *   <li>Babylon: a type is Works, Partial or Unchecked exactly when the Babylon player's {@code SUPPORTED_COMPONENTS} lists it;</li>
 *   <li>TeaVM: every 3D physics type is No, because Jolt's native library cannot load in a browser;</li>
 *   <li>Desktop: no registered type is No.</li>
 * </ul>
 * Every row of the other tables (features, release checks) also needs a valid status per column.
 */
class BackendSupportMatrixTest {
    private static final Path MATRIX = Path.of("..", "docs", "BACKEND_SUPPORT_MATRIX.md");
    private static final Path BABYLON_BUILDER = Path.of("..", "engine-babylon", "src", "runtime", "SceneBuilder.ts");
    /** The status words a cell may start with, as the matrix's legend defines them. */
    private static final Set<String> STATUSES = Set.of("Works", "Partial", "Read only", "No", "Blocked", "Unchecked", "Not applicable");
    /** Component types only the Babylon editor writes; the desktop does not register them. */
    private static final Set<String> WEB_ONLY = Set.of("SculptMeshComponent");
    /** Types that need the 3D physics backend, which is Jolt's native library on the desktop and in the TeaVM build. */
    private static final Set<String> JOLT_TYPES = Set.of("RigidBody3DComponent", "BoxCollider3DComponent", "SphereCollider3DComponent",
        "CapsuleCollider3DComponent", "ConvexHullCollider3DComponent", "MeshCollider3DComponent", "TerrainCollider3DComponent",
        "CharacterController3DComponent", "Joint3DComponent");

    private static String matrix;

    @BeforeAll
    static void read() throws IOException {
        matrix = Files.readString(MATRIX, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** Cells of every table row in the section whose heading starts with {@code heading}, header and divider rows left out. */
    private static List<List<String>> rows(String heading) {
        int start = matrix.indexOf("\n## " + heading);
        assertTrue(start >= 0, "no section '## " + heading + "' in " + MATRIX);
        int end = matrix.indexOf("\n## ", start + 1);
        String section = matrix.substring(start, end < 0 ? matrix.length() : end);
        List<List<String>> rows = new ArrayList<>();
        boolean header = true;
        for (String line : section.split("\n")) {
            if (!line.startsWith("|")) {
                header = true;
                continue;
            }
            if (header) {
                header = line.startsWith("|---") ? false : header;
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (String cell : line.substring(1, line.length() - 1).split("\\|", -1)) {
                cells.add(cell.trim());
            }
            rows.add(cells);
        }
        assertFalse(rows.isEmpty(), "section '" + heading + "' has no table rows");
        return rows;
    }

    /** The status word a cell starts with (bold markers ignored), or the cell itself when it starts with none. */
    private static String status(String cell) {
        String plain = cell.replace("*", "").trim();
        for (String word : STATUSES) {
            if (plain.equals(word) || plain.startsWith(word + ":") || plain.startsWith(word + " ") || plain.startsWith(word + ",")
                || plain.startsWith(word + ".") || plain.startsWith(word + ";")) {
                return word;
            }
        }
        return cell;
    }

    /** Component rows: type id (in backticks) to its Desktop, TeaVM and Babylon statuses. */
    private static Map<String, String[]> componentStatuses() {
        Map<String, String[]> byType = new LinkedHashMap<>();
        for (List<String> cells : rows("1. Components")) {
            assertEquals(6, cells.size(), "a component row has 6 cells: " + cells);
            String type = cells.get(0).replace("`", "");
            String[] statuses = {status(cells.get(2)), status(cells.get(3)), status(cells.get(4))};
            assertTrue(byType.put(type, statuses) == null, type + " has two rows");
        }
        return byType;
    }

    private static Set<String> babylonSupported() throws IOException {
        String source = Files.readString(BABYLON_BUILDER, StandardCharsets.UTF_8);
        int start = source.indexOf("SUPPORTED_COMPONENTS");
        assertTrue(start >= 0, "no SUPPORTED_COMPONENTS in " + BABYLON_BUILDER);
        String set = source.substring(start, source.indexOf("]);", start));
        Set<String> types = new TreeSet<>();
        Matcher matcher = Pattern.compile("^\\s*\"([A-Za-z0-9]+)\",", Pattern.MULTILINE).matcher(set);
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
        assertTrue(types.size() > 20, "Babylon's supported types were not read: " + types);
        return types;
    }

    @Test
    void shouldHaveOneRowPerRegisteredComponentType() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        Set<String> registered = new TreeSet<>(registry.getAll().keySet());
        Set<String> expected = new TreeSet<>(registered);
        expected.addAll(WEB_ONLY);
        assertEquals(expected, new TreeSet<>(componentStatuses().keySet()),
            "the matrix's component rows and the registered types (plus the web-only ones) differ");
    }

    @Test
    void shouldGiveEveryCellAStatusFromTheLegend() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String[]> row : componentStatuses().entrySet()) {
            for (String status : row.getValue()) {
                if (!STATUSES.contains(status)) {
                    bad.add(row.getKey() + ": '" + status + "'");
                }
            }
        }
        for (String section : List.of("2. Features", "3. Release checks")) {
            for (List<String> cells : rows(section)) {
                for (int column = 1; column < cells.size() - 1; column++) {
                    if (!STATUSES.contains(status(cells.get(column)))) {
                        bad.add(section + " / " + cells.get(0) + ", column " + (column + 1) + ": '" + cells.get(column) + "'");
                    }
                }
            }
        }
        assertEquals(List.of(), bad, "cells that start with no status word from the legend");
    }

    @Test
    void shouldAgreeWithTheBabylonPlayersSupportedTypes() throws IOException {
        Set<String> supported = babylonSupported();
        List<String> disagree = new ArrayList<>();
        for (Map.Entry<String, String[]> row : componentStatuses().entrySet()) {
            String babylon = row.getValue()[2];
            boolean claimed = babylon.equals("Works") || babylon.equals("Partial") || babylon.equals("Unchecked");
            if (claimed != supported.contains(row.getKey())) {
                disagree.add(row.getKey() + ": matrix says " + babylon + ", SUPPORTED_COMPONENTS "
                    + (supported.contains(row.getKey()) ? "lists it" : "does not list it"));
            }
        }
        assertEquals(List.of(), disagree, "Babylon statuses that contradict " + BABYLON_BUILDER);
    }

    @Test
    void shouldMarkThePhysicsTypesNoOnTeaVmAndNothingNoOnTheDesktop() {
        Map<String, String[]> statuses = componentStatuses();
        for (String type : JOLT_TYPES) {
            assertTrue(statuses.containsKey(type), type + " has no row");
            assertEquals("No", statuses.get(type)[1], type + " on TeaVM: Jolt's native library cannot load in a browser");
        }
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        for (String type : registry.getAll().keySet()) {
            assertTrue(statuses.containsKey(type), type + " has no row");
            assertFalse(statuses.get(type)[0].equals("No"), type + " is registered on the desktop, so it cannot be No there");
        }
    }
}
