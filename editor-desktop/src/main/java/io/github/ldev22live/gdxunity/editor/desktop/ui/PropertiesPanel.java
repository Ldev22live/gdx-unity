package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The Properties panel at the right of the map (UI M3b, the design reference {@code docs/ForgeRoom Isometric World Editor.png}). It
 * shows what is being worked on:
 * <ul>
 *   <li>while a map drawer (Terrain, Objects, Buildings, Characters) is open, that tool's settings: the level palette, with brush
 *       sliders and ground swatches;</li>
 *   <li>otherwise the selection's components (Details).</li>
 * </ul>
 * Below them, a collapsible World Settings section holds the scene's game genre and mode, Play's fixes and Customize, which the old
 * footer held.
 */
public class PropertiesPanel extends Table {
    private final Actor toolSettings;
    private final Actor details;
    private final BooleanSupplier toolOpen;
    private final Cell<Actor> content;
    private final Cell<Actor> worldSettingsCell;
    private final Actor worldSettings;
    private final TextButton worldSettingsHeader;
    private boolean worldSettingsOpen = true;
    private Boolean showingTool;

    /**
     * @param toolSettings  the open map tool's settings (the level palette), shown in a scroll pane
     * @param details       the selection's components
     * @param toolOpen      whether a map drawer is open
     * @param worldSettings the World Settings section's contents
     */
    public PropertiesPanel(Skin skin, Actor toolSettings, Actor details, BooleanSupplier toolOpen, Actor worldSettings) {
        super(skin);
        Objects.requireNonNull(skin, "skin cannot be null");
        // Held at the top left, so a short palette starts under the title rather than in the middle of the panel
        Table holder = new Table(skin);
        holder.top().left();
        holder.add(Objects.requireNonNull(toolSettings, "toolSettings cannot be null")).top().left();
        ScrollPane scroll = new ScrollPane(holder, skin);
        scroll.setFadeScrollBars(false);
        this.toolSettings = scroll;
        this.details = Objects.requireNonNull(details, "details cannot be null");
        this.toolOpen = Objects.requireNonNull(toolOpen, "toolOpen cannot be null");
        this.worldSettings = Objects.requireNonNull(worldSettings, "worldSettings cannot be null");
        setBackground("card-bg");
        top();
        Label title = new Label("Properties", skin, "default");
        title.setStyle(new Label.LabelStyle(skin.getFont("heading"), skin.getColor("text-primary")));
        add(title).left().pad(4f, 6f, 6f, 6f).row();
        content = add((Actor) null).grow().minHeight(0f);
        row();
        worldSettingsHeader = new TextButton("", skin, "flat");
        worldSettingsHeader.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
        worldSettingsHeader.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setWorldSettingsOpen(!worldSettingsOpen);
            }
        });
        add(worldSettingsHeader).growX().padTop(6f).row();
        worldSettingsCell = add((Actor) null).growX().left();
        setWorldSettingsOpen(true);
    }

    /** Opens or folds the World Settings section. */
    public void setWorldSettingsOpen(boolean open) {
        worldSettingsOpen = open;
        worldSettingsHeader.setText((open ? "v " : "> ") + "World Settings");
        worldSettingsCell.setActor(open ? worldSettings : null);
        invalidateHierarchy();
    }

    /** Whether the World Settings section is open. */
    public boolean isWorldSettingsOpen() {
        return worldSettingsOpen;
    }

    /** Whether the panel shows the open map tool's settings (true) or the selection's components (false). */
    public boolean isShowingToolSettings() {
        return toolOpen.getAsBoolean();
    }

    @Override
    public void act(float delta) {
        boolean tool = toolOpen.getAsBoolean();
        if (showingTool == null || showingTool != tool) {
            content.setActor(tool ? toolSettings : details);
            showingTool = tool;
            invalidateHierarchy();
        }
        super.act(delta);
    }
}
