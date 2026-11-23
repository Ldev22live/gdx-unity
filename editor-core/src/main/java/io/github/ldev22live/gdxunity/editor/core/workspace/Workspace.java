package io.github.ldev22live.gdxunity.editor.core.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The places the Customize gateway leads to (roadmap Appendix E2, milestone M1b.7). Each is shown today by an existing part of the
 * editor: World by the World tab (named Viewport until owner decision 11, 2026-11-15), 3D Modeling by sculpt mode in that view, 2D Studio by the Sprites and Tiles tabs, Animation by
 * the Animations tab, Audio &amp; Music by the Music tab, Logic by the Visual Scripting tab and Assets by the Content Browser.
 */
public enum Workspace {
    WORLD("World"),
    MODELING_3D("3D Modeling"),
    STUDIO_2D("2D Studio"),
    ANIMATION("Animation"),
    AUDIO_MUSIC("Audio & Music"),
    LOGIC("Logic"),
    ASSETS("Assets");

    /** Title of the editor's centre tab that shows the World (and 3D Modeling, in sculpt mode). */
    public static final String TAB_WORLD = "World";
    /** Title of the centre tab that shows the game while it plays: part of the World during Play (Appendix E3). */
    public static final String TAB_SCENE = "Scene";
    public static final String TAB_ANIMATIONS = "Animations";
    public static final String TAB_VISUAL_SCRIPTING = "Visual Scripting";
    public static final String TAB_MUSIC = "Music";
    public static final String TAB_SPRITES = "Sprites";
    public static final String TAB_TILES = "Tiles";

    /**
     * One entry of the Customize list: a workspace and the centre tab that shows it. 2D Studio has two (Sprites and Tiles), so that
     * every centre tab is reachable once the tabs are no longer shown in the header (M1b.2).
     *
     * @param label shown in the menu
     * @param tab   the centre tab, or null for Assets
     */
    public record Destination(String label, Workspace workspace, String tab) { }

    /** Every Customize destination, in menu order: each workspace once, 2D Studio twice. */
    public static List<Destination> destinations() {
        List<Destination> all = new ArrayList<>();
        for (Workspace workspace : values()) {
            if (workspace == STUDIO_2D) {
                all.add(new Destination(workspace.label + ": Sprites", workspace, TAB_SPRITES));
                all.add(new Destination(workspace.label + ": Tiles", workspace, TAB_TILES));
            } else {
                all.add(new Destination(workspace.label, workspace, workspace.centerTab()));
            }
        }
        return all;
    }

    private final String label;

    Workspace(String label) {
        this.label = label;
    }

    /**
     * Whether the World's own tools (the view overlay, Select, Move, Rotate, Scale, Frame, Sculpt and Level) belong on screen in this
     * workspace: in World and in 3D Modeling, which is the World's view with sculpt mode on. Other workspaces have their own tools,
     * so showing these there would put unrelated controls together (roadmap M1b.4, M3b.6).
     */
    public boolean showsWorldTools() {
        return this == WORLD || this == MODELING_3D;
    }

    /** The name shown to a person, e.g. "Audio &amp; Music". */
    public String getLabel() {
        return label;
    }

    /**
     * The workspace a centre tab shows.
     *
     * @param tabTitle   one of the {@code TAB_} titles
     * @param sculptMode whether sculpt mode is on, which turns the Viewport into 3D Modeling
     * @throws IllegalArgumentException for a tab this editor does not have
     */
    public static Workspace forCenterTab(String tabTitle, boolean sculptMode) {
        Objects.requireNonNull(tabTitle, "tabTitle cannot be null");
        return switch (tabTitle) {
            case TAB_WORLD -> sculptMode ? MODELING_3D : WORLD;
            case TAB_SCENE -> WORLD;
            case TAB_ANIMATIONS -> ANIMATION;
            case TAB_VISUAL_SCRIPTING -> LOGIC;
            case TAB_MUSIC -> AUDIO_MUSIC;
            case TAB_SPRITES, TAB_TILES -> STUDIO_2D;
            default -> throw new IllegalArgumentException("not a centre tab: " + tabTitle);
        };
    }

    /**
     * The centre tab that shows this workspace, or null for {@link #ASSETS}, which is the Content Browser panel rather than a tab.
     * 3D Modeling is the World tab with sculpt mode on.
     */
    public String centerTab() {
        return switch (this) {
            case WORLD, MODELING_3D -> TAB_WORLD;
            case STUDIO_2D -> TAB_SPRITES;
            case ANIMATION -> TAB_ANIMATIONS;
            case AUDIO_MUSIC -> TAB_MUSIC;
            case LOGIC -> TAB_VISUAL_SCRIPTING;
            case ASSETS -> null;
        };
    }
}
