package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainAssets;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainCollision;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainBrush;

import java.io.File;
import java.util.Locale;

/**
 * Details editor for {@link TerrainComponent} (TD-082): the {@code .terrain} file the entity draws, buttons to make a new one or use
 * the one selected in the Content Browser, a Paint toggle, and the brush (tool, radius, strength). With paint on, a left-button drag
 * on the terrain in the 3D view paints; Ctrl inverts and Shift smooths for one stroke. Every stroke is one undo step and the file is
 * saved with it.
 */
public class TerrainComponentEditorProvider implements ComponentEditorProvider<TerrainComponent, Table> {
    private final TerrainSession session;
    private final File assetRoot;

    /**
     * @param session   the terrains and the brush
     * @param assetRoot the project's asset folder, where new terrains are created
     */
    public TerrainComponentEditorProvider(TerrainSession session, File assetRoot) {
        this.session = java.util.Objects.requireNonNull(session, "session cannot be null");
        this.assetRoot = java.util.Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
    }

    @Override
    public Class<TerrainComponent> getComponentClass() {
        return TerrainComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Terrain";
    }

    @Override
    public void buildEditor(Table container, Entity entity, TerrainComponent component, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        InspectorFieldRows.textRow(container, skin, "File", component.getTerrainPath(), path -> {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                if (!TerrainAssets.isTerrainPath(trimmed)) {
                    throw new IllegalArgumentException("A terrain is a " + TerrainAssets.EXTENSION + " file: " + trimmed);
                }
                if (session.getLibrary().get(trimmed) == null) {
                    throw new IllegalArgumentException(session.getLibrary().getError(trimmed));
                }
            }
            assign(entity, component, editorState, trimmed);
        });

        Table buttons = new Table(skin);
        buttons.add(button(skin, "New Hills", "primary", () -> assign(entity, component, editorState,
            TerrainAssets.createNew(assetRoot, session.getLibrary(), "Terrain", false)))).height(24).padRight(6);
        buttons.add(button(skin, "New Flat", "flat", () -> assign(entity, component, editorState,
            TerrainAssets.createNew(assetRoot, session.getLibrary(), "Terrain", true)))).height(24).padRight(6);
        buttons.add(button(skin, "Use Selected", "flat", () -> {
            AssetReference selected = editorState.getSelectedAsset();
            if (selected == null || AssetType.fromPath(selected.getPath()) != AssetType.TERRAIN) {
                editorState.setStatusMessage("Select a .terrain in the Content Browser first", true);
            } else if (session.getLibrary().get(selected.getPath()) == null) {
                editorState.setStatusMessage(session.getLibrary().getError(selected.getPath()), true);
            } else {
                assign(entity, component, editorState, selected.getPath());
            }
        })).height(24);
        container.add(buttons).left().padTop(4).row();

        String path = component.getTerrainPath();
        if (path.isEmpty()) {
            container.add(new Label("No terrain: nothing is drawn. Make one with New Hills or New Flat.", skin, "muted")).left().padLeft(2).row();
            return;
        }
        Terrain terrain = session.getLibrary().get(path);
        if (terrain == null) {
            container.add(new Label(session.getLibrary().getError(path), skin, "muted")).left().padLeft(2).row();
            return;
        }
        float[] range = terrain.heightRange();
        container.add(new Label(String.format(Locale.ROOT, "%d x %d points, %.0f x %.0f units, heights %.0f to %.0f",
            terrain.getResolutionX(), terrain.getResolutionZ(), terrain.getSizeX(), terrain.getSizeZ(), range[0], range[1]),
            skin, "muted")).left().padLeft(2).row();
        buildBrushSection(container, editorState, skin);
    }

    private void buildBrushSection(Table container, EditorState editorState, Skin skin) {
        TerrainBrush brush = session.getBrush();
        container.add(new Label("Paint", skin)).left().padTop(8).row();
        TextButton paint = new TextButton(session.isPaintMode() ? "Paint: On (click to stop)" : "Paint: Off (click to start)", skin,
            session.isPaintMode() ? "primary" : "flat");
        paint.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                session.setPaintMode(!session.isPaintMode());
                paint.setText(session.isPaintMode() ? "Paint: On (click to stop)" : "Paint: Off (click to start)");
                editorState.setStatusMessage(session.isPaintMode()
                    ? "Terrain paint on: drag on the terrain. Ctrl inverts, Shift smooths, Alt-drag orbits."
                    : "Terrain paint off", false);
            }
        });
        container.add(paint).left().height(24).padTop(2).row();

        Table toolRow = new Table(skin);
        toolRow.left();
        toolRow.add(new Label("Tool", skin, "muted")).width(80).left();
        SelectBox<TerrainBrush.Type> tool = new SelectBox<>(skin);
        tool.setItems(TerrainBrush.Type.values());
        tool.setSelected(brush.getType());
        tool.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                brush.setType(tool.getSelected());
            }
        });
        toolRow.add(tool).growX().height(22);
        container.add(toolRow).growX().padTop(2).padBottom(2).row();
        InspectorFieldRows.floatRow(container, skin, "Radius", brush.getRadius(), brush::setRadius);
        InspectorFieldRows.floatRow(container, skin, "Strength", brush.getStrength(), brush::setStrength);
        container.add(new Label("Radius is in world units. Strokes are undoable and saved with the file.", skin, "muted")).left().padLeft(2).row();
    }

    private static TextButton button(Skin skin, String text, String style, Runnable action) {
        TextButton button = new TextButton(text, skin, style);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    /** A new terrain is walkable at once: the Terrain Collider 3D comes in the same undo step (TD-087). */
    private void assign(Entity entity, TerrainComponent component, EditorState editorState, String path) {
        TerrainCollision.assignTerrain(editorState, entity, component, path);
    }
}
