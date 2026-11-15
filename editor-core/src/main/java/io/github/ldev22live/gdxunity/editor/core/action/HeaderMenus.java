package io.github.ldev22live.gdxunity.editor.core.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The two dropdowns of the World-first header (roadmap Appendix E1, milestone M1b.2, stage 1 of the plan in
 * {@code docs/reports/world-first-ux-audit.md}): <b>Project</b>, which holds every File action (the File dropdown of M1.5 under the
 * name the layout gives it), and <b>Settings</b>, which holds the editor's graphics, appearance and about entries, the actions that had
 * no route on screen once the old Window and Help menus were removed. Each menu is a list of sections; a section is a list of action
 * ids, shown with a separator between sections. Ids that are not registered are left out when a menu is listed.
 */
public final class HeaderMenus {
    /** The header's project dropdown: every action registered in {@link DefaultEditorActions#MENU_FILE}. */
    public static final String PROJECT = "Project";
    /** The header's settings dropdown. */
    public static final String SETTINGS = "Settings";
    /** The desktop editor's theme switch, registered by the desktop shell itself. */
    public static final String WINDOW_TOGGLE_THEME = "window.toggle-theme";

    /** Settings > graphics: the 3D view's shadows and lighting quality. */
    public static final List<String> SETTINGS_GRAPHICS = List.of(DefaultEditorActions.VIEW_TOGGLE_SHADOWS,
        DefaultEditorActions.VIEW_LIGHTING_QUALITY_PREFIX + "low", DefaultEditorActions.VIEW_LIGHTING_QUALITY_PREFIX + "medium",
        DefaultEditorActions.VIEW_LIGHTING_QUALITY_PREFIX + "high");
    /** Settings > appearance. */
    public static final List<String> SETTINGS_APPEARANCE = List.of(WINDOW_TOGGLE_THEME);
    /** Settings > about. */
    public static final List<String> SETTINGS_ABOUT = List.of(DefaultEditorActions.HELP_ABOUT);

    private HeaderMenus() { }

    /** The header's menu names, left to right. */
    public static List<String> names() {
        return List.of(PROJECT, SETTINGS);
    }

    /**
     * The sections of the named header menu, with only the ids {@code registry} knows; empty sections are dropped.
     *
     * @throws IllegalArgumentException for a name that is not one of {@link #names()}
     */
    public static List<List<String>> sections(EditorActionRegistry registry, String menu) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(menu, "menu cannot be null");
        List<List<String>> wanted = switch (menu) {
            case PROJECT -> List.of(registry.getMenuActions(DefaultEditorActions.MENU_FILE).stream().map(EditorAction::getId).toList());
            case SETTINGS -> List.of(SETTINGS_GRAPHICS, SETTINGS_APPEARANCE, SETTINGS_ABOUT);
            default -> throw new IllegalArgumentException("not a header menu: " + menu + " (expected one of " + names() + ")");
        };
        List<List<String>> sections = new ArrayList<>();
        for (List<String> section : wanted) {
            List<String> known = section.stream().filter(id -> registry.find(id).isPresent()).toList();
            if (!known.isEmpty()) {
                sections.add(known);
            }
        }
        return sections;
    }

    /** Every action id the named header menu shows, in order, for checks that only need membership. */
    public static List<String> actionIds(EditorActionRegistry registry, String menu) {
        return sections(registry, menu).stream().flatMap(List::stream).toList();
    }
}
