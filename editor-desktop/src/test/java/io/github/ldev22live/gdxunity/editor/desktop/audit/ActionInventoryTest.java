package io.github.ldev22live.gdxunity.editor.desktop.audit;

import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.HeaderMenus;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feature-to-workspace inventory of roadmap milestone M1b.1: every editor action, how a person reaches it today, and where it will
 * live in the World-first layout of roadmap Appendix E. The actions come from the editor's real registry (the same registrations as
 * {@code EditorApplication}, built without a window) plus the actions {@code EditorApplication} registers itself, read from its source.
 * The test fails when an action has no planned home, when the plan names an action that no longer exists, when the set of actions a
 * person cannot reach changes without this list changing, or when the table in {@code docs/reports/world-first-ux-audit.md} is stale
 * (refresh it with {@code GDXUNITY_UPDATE_AUDIT=1}). Nothing may be hidden or moved before it has a home here (Appendix E, R1b).
 */
class ActionInventoryTest {
    private static final File REPORT = new File("../docs/reports/world-first-ux-audit.md");
    private static final File APPLICATION = new File("src/main/java/io/github/ldev22live/gdxunity/editor/desktop/EditorApplication.java");
    private static final String BEGIN = "<!-- action-inventory:begin -->";
    private static final String END = "<!-- action-inventory:end -->";
    /**
     * The registry menus shown on screen as they are: the top bar's Create dropdown. The File menu is shown as the header's Project
     * dropdown and the Settings dropdown picks its entries by id ({@link HeaderMenus}); the rest of the old menu bar was removed.
     */
    private static final Set<String> VISIBLE_MENUS = Set.of(DefaultEditorActions.MENU_CREATE);
    /** Every id the header's Settings dropdown shows. */
    private static final Set<String> SETTINGS_IDS = Set.copyOf(concat(HeaderMenus.SETTINGS_GRAPHICS, HeaderMenus.SETTINGS_APPEARANCE,
        HeaderMenus.SETTINGS_ABOUT));

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        List<String> all = new ArrayList<>();
        for (List<String> list : lists) {
            all.addAll(list);
        }
        return all;
    }

    /**
     * For one action: how it is reached today besides a visible menu and a keyboard shortcut (which are read from the registry), and
     * where it will live in the World-first layout.
     */
    record Home(String otherRoutesToday, String plannedHome) { }

    /** Every action's home. The keys must be exactly the registered action ids. */
    static final Map<String, Home> HOMES = new LinkedHashMap<>();

    private static void home(String id, String other, String planned) {
        HOMES.put(id, new Home(other, planned));
    }

    static {
        // File / project
        home("file.new-scene", "", "Header > Project menu (and Ctrl+N)");
        home("file.new-scene-2d", "", "Start screen and template gallery (M1.4)");
        home("file.new-scene-2.5d", "", "Start screen and template gallery (M1.4)");
        home("file.new-scene-3d", "", "Start screen and template gallery (M1.4)");
        home("file.new-scene-screen", "", "Start screen and template gallery (M1.4)");
        home("file.new-game", "", "Header > Project menu: New Game (the template gallery, M1.4)");
        home("file.open-scene", "", "Header > Project menu (and Ctrl+O)");
        home("file.open-sample", "", "Template gallery: Play Sample (M1.4)");
        home("file.use-sample-as-template", "", "Template gallery: Use as Template (M1.4)");
        home("file.save-scene", "header Save button", "Header: Save button (Appendix E1)");
        home("file.save-scene-as", "", "Header > Project menu (and Ctrl+Shift+S)");
        home("file.import-asset", "", "Customize > Assets, and Header > Project menu");
        home("file.export-game", "", "Header > Project menu: Export");
        home("file.exit", "window close button", "Header > Project menu");
        home("file.export-sculpt-model", "", "Customize > 3D Modeling (sculpt tools)");
        home("file.save-sculpts", "", "Customize > 3D Modeling (and Ctrl+Alt+S)");
        // Edit
        home("edit.undo", "header Undo button", "Header: Undo button (Appendix E1)");
        home("edit.redo", "header Redo button", "Header: Redo button (Appendix E1)");
        home("edit.redo.ctrl-shift-z", "", "Keyboard only (Ctrl+Shift+Z), unchanged");
        home("edit.duplicate", "Outliner context menu", "Selection context menu and selection strip");
        home("edit.delete", "Outliner context menu", "Selection context menu and selection strip");
        home("edit.rename", "Outliner context menu", "Selection context menu (and F2)");
        home("prefab.create", "Outliner context menu", "Selection context menu");
        home("prefab.apply", "Outliner context menu", "Selection context menu (prefab instances)");
        home("prefab.revert", "Outliner context menu", "Selection context menu (prefab instances)");
        home("prefab.unpack", "Outliner context menu", "Selection context menu (prefab instances)");
        // Create
        home("create.empty-entity", "Outliner context menu", "World > Objects strip (advanced) and Outliner context menu");
        home("create.box", "", "World > Objects strip");
        home("create.sphere", "", "World > Objects strip");
        home("create.plane", "", "World > Objects strip");
        home("create.directional-light", "", "World > lighting presets (Appendix E2)");
        home("create.point-light", "", "World > lighting presets (Appendix E2)");
        home("create.sky-light", "", "World > lighting presets (Appendix E2)");
        home("create.tone-mapping", "", "World > lighting presets (Appendix E2)");
        home("create.sculpt", "", "Customize > 3D Modeling");
        home("create.sculpt-from-selected", "", "Customize > 3D Modeling (Edit Selected)");
        // Tools
        home("tools.select", "view tools Select button", "Viewport tool strip (and Q)");
        home("tools.move", "view tools Move button", "Viewport tool strip (and W)");
        home("tools.rotate", "view tools Rotate button", "Viewport tool strip (and E)");
        home("tools.scale", "view tools Scale button", "Viewport tool strip (and R)");
        home("tools.level-toggle", "view tools Level button; World strip Terrain and Objects", "World > Terrain and Objects strips (the level palette becomes the World strip)");
        home("tools.sculpt-toggle", "view tools Sculpt button; Customize > 3D Modeling", "Customize > 3D Modeling (and F4)");
        home("tools.sculpt-enter", "", "Customize > 3D Modeling (the Sculpt toggle covers it)");
        home("tools.sculpt-exit", "", "Customize > 3D Modeling (the Sculpt toggle covers it)");
        for (String brush : List.of("draw", "smooth", "inflate", "pinch", "flatten", "grab")) {
            home("tools.sculpt-brush." + brush, "sculpt strip in the viewport", "Customize > 3D Modeling: brush strip");
        }
        home("tools.sculpt-wireframe", "sculpt strip in the viewport", "Customize > 3D Modeling: brush strip");
        home("tools.sculpt-mirror-x", "sculpt strip in the viewport", "Customize > 3D Modeling: brush strip");
        home("tools.sculpt-mirror-y", "sculpt strip in the viewport", "Customize > 3D Modeling: brush strip");
        home("tools.sculpt-mirror-z", "sculpt strip in the viewport", "Customize > 3D Modeling: brush strip");
        home("tools.sculpt-larger", "", "Customize > 3D Modeling: brush strip (and ])");
        home("tools.sculpt-smaller", "", "Customize > 3D Modeling: brush strip (and [)");
        home("tools.sculpt-stronger", "", "Customize > 3D Modeling: brush strip (and =)");
        home("tools.sculpt-weaker", "", "Customize > 3D Modeling: brush strip (and -)");
        home("view.focus-selection", "view tools Frame button", "Viewport overlay and selection context menu (and F)");
        // View and window
        home("view.toggle-2d-3d", "viewport overlay icon", "Viewport overlay (editor view, not the game mode: Appendix E, decision 11)");
        home("window.toggle-grid", "viewport overlay icon", "Viewport overlay");
        home("window.toggle-physics-debug", "viewport overlay icon", "Viewport overlay");
        home("view.reset", "viewport overlay icon", "Viewport overlay");
        home("view.toggle-shadows", "", "Header > Settings: graphics");
        home("view.lighting-quality.low", "", "Header > Settings: graphics");
        home("view.lighting-quality.medium", "", "Header > Settings: graphics");
        home("view.lighting-quality.high", "", "Header > Settings: graphics");
        home("window.toggle-theme", "", "Header > Settings: appearance");
        home("window.toggle-assistant", "toolbar AI Assistant button (calls the same code)", "Collapsible panel until R10's Describe your game");
        home("help.about", "", "Header > Settings: about");
        // Panels (M1b.2 stage 4, TD-085): the World layout starts with them closed, so their buttons are always on screen
        home("window.toggle-outliner", "toolbar row 2 Outliner button", "Panel buttons (collapsible Outliner)");
        home("window.cycle-details", "toolbar row 2 Details button", "Panel buttons (Details with the selection, always or hidden)");
        home("window.toggle-bottom-panel", "toolbar row 2 Content Browser button", "Panel buttons (collapsible Content Browser and Log)");
    }

    /**
     * Actions a person cannot reach today: no visible menu, no shortcut and no other route. Pinned so a new one cannot appear unnoticed.
     * Since M1b.2 stage 1 only Enter and Exit Sculpt Mode remain, and the Sculpt toggle (button and F4) does both.
     */
    static final Set<String> UNREACHABLE_TODAY = Set.of("tools.sculpt-enter", "tools.sculpt-exit");

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    /** One row of the inventory. */
    private record Row(String id, String label, String menu, String shortcut, String routes, boolean reachable, String home) { }

    private List<Row> rows() throws IOException {
        Map<String, String[]> actions = new TreeMap<>();
        for (EditorAction action : ActionInventoryDump.registeredActions(project.toFile())) {
            actions.put(action.getId(), new String[]{action.getLabel(), action.getMenu() == null ? "" : action.getMenu(), action.getShortcutText()});
        }
        actions.putAll(desktopActions());
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : actions.entrySet()) {
            String id = entry.getKey();
            String[] a = entry.getValue();
            Home home = HOMES.get(id);
            List<String> routes = new ArrayList<>();
            if (VISIBLE_MENUS.contains(a[1])) {
                routes.add(a[1] + " dropdown");
            }
            if (DefaultEditorActions.MENU_FILE.equals(a[1])) {
                routes.add("header Project dropdown");
            }
            if (SETTINGS_IDS.contains(id)) {
                routes.add("header Settings dropdown");
            }
            if (!a[2].isEmpty()) {
                routes.add("shortcut " + a[2]);
            }
            if (home != null && !home.otherRoutesToday().isEmpty()) {
                routes.add(home.otherRoutesToday());
            }
            rows.add(new Row(id, a[0], a[1], a[2], String.join("; ", routes), !routes.isEmpty(), home == null ? null : home.plannedHome()));
        }
        return rows;
    }

    /** The actions {@code EditorApplication} registers itself (it needs a window, so they are read from its source). */
    private static Map<String, String[]> desktopActions() throws IOException {
        String source = Files.readString(APPLICATION.toPath());
        Map<String, String> constants = new TreeMap<>();
        Matcher constant = Pattern.compile("static final String (ACTION_\\w+) = \"([^\"]+)\"").matcher(source);
        while (constant.find()) {
            constants.put(constant.group(1), constant.group(2));
        }
        Map<String, String[]> actions = new TreeMap<>();
        Matcher builder = Pattern.compile("EditorAction\\.builder\\((ACTION_\\w+), \"([^\"]+)\"(.{0,400}?)\\.build\\(\\)", Pattern.DOTALL).matcher(source);
        while (builder.find()) {
            String body = builder.group(3);
            Matcher menu = Pattern.compile("MENU_(\\w+)").matcher(body);
            String menuName = menu.find() ? menu.group(1).charAt(0) + menu.group(1).substring(1).toLowerCase() : "";
            Matcher shortcut = Pattern.compile("\\.shortcut\\(Input\\.Keys\\.(\\w+), 0\\)").matcher(body);
            String id = constants.get(builder.group(1));
            assertTrue(id != null, "no id constant for " + builder.group(1));
            actions.put(id, new String[]{builder.group(2), menuName, shortcut.find() ? shortcut.group(1) : ""});
        }
        assertEquals(10, actions.size(), "EditorApplication registers ten actions of its own; update this test if that changes");
        return actions;
    }

    @Test
    void shouldGiveEveryRegisteredActionAPlannedHomeAndNameNoActionThatIsGone() throws IOException {
        Set<String> registered = new TreeSet<>();
        for (Row row : rows()) {
            registered.add(row.id());
        }
        Set<String> withoutHome = new TreeSet<>(registered);
        withoutHome.removeAll(HOMES.keySet());
        Set<String> stale = new TreeSet<>(HOMES.keySet());
        stale.removeAll(registered);

        assertEquals(Set.of(), withoutHome, "every action needs a planned home before the layout changes (M1b.1)");
        assertEquals(Set.of(), stale, "the plan names actions that no longer exist");
        assertEquals(73, registered.size(), "the editor registers 73 actions (69 at M1b.1, the three panel actions of M1b.2 "
            + "stage 4 and New Game of M1.4); a change here means the inventory needs a look");
    }

    @Test
    void shouldListExactlyTheActionsNobodyCanReachToday() throws IOException {
        Set<String> unreachable = new TreeSet<>();
        for (Row row : rows()) {
            if (!row.reachable()) {
                unreachable.add(row.id());
            }
        }
        assertEquals(new TreeSet<>(UNREACHABLE_TODAY), unreachable,
            "an action with no menu on screen, no shortcut and no button is hidden; give it a route or list it here knowingly");
    }

    @Test
    void shouldReadTheMenusOnScreenFromTheTopBar() throws IOException {
        String source = Files.readString(APPLICATION.toPath());
        assertTrue(source.contains("menuRow.add(EditorMenuBar.header(actionRegistry, skin))")
                && source.contains("new EditorMenuBar(actionRegistry, List.of(DefaultEditorActions.MENU_CREATE), skin)"),
            "the top bar's dropdowns changed; update VISIBLE_MENUS and the inventory");
    }

    /**
     * The footer of roadmap M1b.3 (decision 11): Game Mode, Customize and the play controls are built once, into the footer, and not
     * also kept in the toolbar. Read from the source because the shell needs a window; the screenshots in the M1b.3 report show it.
     */
    @Test
    void shouldPutGameModeCustomizeAndPlayInTheFooterOnly() throws IOException {
        String source = Files.readString(APPLICATION.toPath());
        assertTrue(source.contains("new EditorFooter(skin, new GameModeSelector(editorState, skin), createCustomizeGateway(),\n"
                + "            toolbar.getPlayControls())") || source.contains("new EditorFooter(skin, new GameModeSelector(editorState, skin), "
                + "createCustomizeGateway(),\r\n            toolbar.getPlayControls())"),
            "the footer holds the scene's game mode, the Customize gateway and the play controls");
        assertTrue(source.contains("viewTools,\n            null, false)") || source.contains("viewTools,\r\n            null, false)"),
            "the toolbar no longer holds the mode selector or the play controls");
        // The thin header (M1b.2): the centre tabs are not shown, and the routes the inventory names for the tools are built
        assertFalse(source.contains("menuRow.add(centerTabs.getTabRow())"), "the header no longer shows the centre tabs");
        for (String button : List.of("TOOL_SELECT, \"Select\"", "TOOL_MOVE, \"Move\"", "TOOL_ROTATE, \"Rotate\"", "TOOL_SCALE, \"Scale\"",
                "ACTION_FOCUS_SELECTION, \"Frame\"", "SculptActions.TOGGLE_MODE, \"Sculpt\"", "LevelActions.TOGGLE, \"Level\"")) {
            assertTrue(source.contains("(viewTools, DefaultEditorActions." + button) || source.contains("actionRegistry, " + button),
                "view tools button " + button);
        }
        assertTrue(source.contains("viewTools.add(new ActionTextButton(skin, actionRegistry, LevelActions.TOGGLE, \"Level\""),
            "Level is in the view tools");
        assertEquals(1, source.split("new GameModeSelector\\(", -1).length - 1, "one Game Mode selector");
        assertEquals(1, source.split("createCustomizeGateway\\(\\)", -1).length - 2, "one Customize gateway (one call besides its definition)");
        assertTrue(source.contains("root.add(footer)"), "the footer is in the layout");
        assertTrue(source.contains("secondRow.add(panelButtons)") && source.contains("PanelLayout.TOGGLE_OUTLINER, \"Outliner\"")
                && source.contains("PanelLayout.CYCLE_DETAILS, \"Details\"") && source.contains("PanelLayout.TOGGLE_BOTTOM_PANEL, \"Content Browser\""),
            "closed panels must keep a button on screen (TD-085), or the inventory's routes for the panel actions are wrong");
    }

    @Test
    void shouldShowOnlyRegisteredActionsInTheHeaderSettingsDropdown() throws IOException {
        Set<String> registered = new TreeSet<>();
        for (Row row : rows()) {
            registered.add(row.id());
        }
        Set<String> unknown = new TreeSet<>(SETTINGS_IDS);
        unknown.removeAll(registered);
        assertEquals(Set.of(), unknown, "the Settings dropdown names an action the editor does not register, so it would be left out");
        assertEquals(6, SETTINGS_IDS.size(), "shadows, three lighting qualities, the theme and about");
        assertTrue(registered.containsAll(Set.of(CustomizeGateway.SCULPT_ENTER_ACTION, CustomizeGateway.SCULPT_FROM_SELECTED_ACTION,
            CustomizeGateway.MOVE_TOOL_ACTION, io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceNavigator.SCULPT_EXIT_ACTION)), "Customize's Edit Selected runs these sculpt and Move actions (M1b.7, M1b.4)");
        assertEquals(DefaultEditorActions.TOOL_MOVE, CustomizeGateway.MOVE_TOOL_ACTION, "the transform entry runs the real Move tool");
    }

    @Test
    void shouldKeepTheInventoryInTheAuditReportUpToDate() throws IOException {
        StringBuilder table = new StringBuilder("| Action | Id | Registered menu | Shortcut | Reached today by | Planned home (Appendix E) |\n"
            + "|---|---|---|---|---|---|\n");
        for (Row row : rows()) {
            table.append("| ").append(row.label()).append(" | `").append(row.id()).append("` | ").append(row.menu().isEmpty() ? "none" : row.menu())
                .append(" | ").append(row.shortcut().isEmpty() ? "" : row.shortcut()).append(" | ")
                .append(row.reachable() ? row.routes() : "**nothing: hidden today**").append(" | ").append(row.home()).append(" |\n");
        }
        String report = Files.readString(REPORT.toPath());
        int begin = report.indexOf(BEGIN);
        int end = report.indexOf(END);
        assertTrue(begin >= 0 && end > begin, "the audit report needs the markers " + BEGIN + " and " + END);
        String newline = report.contains("\r\n") ? "\r\n" : "\n";
        String expected = BEGIN + newline + table.toString().replace("\n", newline) + END;
        String current = report.substring(begin, end + END.length());
        if ("1".equals(System.getenv(EditabilityAuditTest.UPDATE_VARIABLE)) && !current.equals(expected)) {
            Files.writeString(REPORT.toPath(), report.substring(0, begin) + expected + report.substring(end + END.length()));
            return;
        }
        assertEquals(expected, current, "the inventory in world-first-ux-audit.md is stale: run this test with "
            + EditabilityAuditTest.UPDATE_VARIABLE + "=1");
    }
}
