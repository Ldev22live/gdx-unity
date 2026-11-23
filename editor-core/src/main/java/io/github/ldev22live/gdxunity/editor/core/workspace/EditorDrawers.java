package io.github.ldev22live.gdxunity.editor.core.workspace;

import java.util.List;
import java.util.Objects;

/**
 * The editor's tool drawers (UI M3): every tool the World offers, each opened and closed on its own so the map stays the main view.
 * Nothing here draws; it names the nine drawers, where each sits and the action that opens or closes it, so the toolbar, the Window
 * menu and the tests agree on one list.
 *
 * <ul>
 *   <li>The map's tools, the first tabs of the tab row (from the World strip's sections): Terrain, Objects, Buildings and Characters.
 *       Each shows its settings in the Properties panel, and pressing it again closes it.</li>
 *   <li>Docked, beside the map: Layers (the scene hierarchy) on the left; Properties and the AI Assistant on the right, one above
 *       the other when both are open; Assets and Scripts (the Output Log and Profiler) at the bottom, side by side when both are open.
 *       Docked drawers are resized by dragging their edge, and the editor remembers which are open.</li>
 * </ul>
 */
public final class EditorDrawers {
    /** Where a drawer opens. */
    public enum Place {
        /** Over the map, opened from the World strip. */
        MAP,
        LEFT,
        RIGHT,
        BOTTOM
    }

    /**
     * One drawer.
     *
     * @param label    its name on its button
     * @param place    where it opens
     * @param actionId the editor action that opens and closes it; null for the World strip's drawers, opened by the strip itself
     * @param section  the World strip section that opens it, or null for a docked drawer
     */
    public record Drawer(String label, Place place, String actionId, WorldStrip.Section section) {
        public Drawer {
            Objects.requireNonNull(label, "label cannot be null");
            Objects.requireNonNull(place, "place cannot be null");
            if ((place == Place.MAP) != (section != null) || (place == Place.MAP) == (actionId != null)) {
                throw new IllegalArgumentException(label + ": a map drawer has a World strip section and no action, a docked one an action");
            }
        }
    }

    /** The AI Assistant's open and close action (registered by the editor, which owns the assistant). */
    public static final String TOGGLE_ASSISTANT = "window.toggle-assistant";

    /**
     * The drawers in the order of the reference layout's tab row (UI M3b, {@code docs/ForgeRoom Isometric World Editor.png}): the
     * World strip's four, then Assets, Layers, Scripts and AI Assistant. Properties is last: it is the panel at the right of the map,
     * shown or hidden from the status bar, not a tab.
     */
    public static final List<Drawer> ALL = List.of(
        new Drawer("Terrain", Place.MAP, null, WorldStrip.Section.TERRAIN),
        new Drawer("Objects", Place.MAP, null, WorldStrip.Section.OBJECTS),
        new Drawer("Buildings", Place.MAP, null, WorldStrip.Section.BUILDINGS),
        new Drawer("Characters", Place.MAP, null, WorldStrip.Section.CHARACTERS),
        new Drawer("Assets", Place.BOTTOM, PanelLayout.TOGGLE_BOTTOM_PANEL, null),
        new Drawer("Layers", Place.LEFT, PanelLayout.TOGGLE_OUTLINER, null),
        new Drawer("Scripts", Place.BOTTOM, PanelLayout.TOGGLE_CONSOLE, null),
        new Drawer("AI Assistant", Place.RIGHT, TOGGLE_ASSISTANT, null),
        new Drawer("Properties", Place.RIGHT, PanelLayout.CYCLE_DETAILS, null));

    /** The Properties panel's drawer: shown or hidden from the status bar, not a tab. */
    public static final String PROPERTIES = "Properties";

    private EditorDrawers() {
    }

    /** The docked drawers, in tab order, Properties last. */
    public static List<Drawer> docked() {
        return ALL.stream().filter(d -> d.place() != Place.MAP).toList();
    }

    /** The docked drawers with a tab in the tab row: every docked drawer but Properties. */
    public static List<Drawer> tabs() {
        return docked().stream().filter(d -> !d.label().equals(PROPERTIES)).toList();
    }
}
