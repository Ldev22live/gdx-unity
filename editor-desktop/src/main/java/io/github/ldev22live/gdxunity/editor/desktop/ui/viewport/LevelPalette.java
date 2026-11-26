package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import io.github.ldev22live.gdxunity.editor.core.project.ObjectPalette;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorSkinFactory;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.environment.EnvironmentPresets;
import io.github.ldev22live.gdxunity.editor.terrain.LevelActions;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.ScatterSettings;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainPathComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmospherePreset;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainBrush;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;

import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainAssets.MapSize;
import io.github.ldev22live.gdxunity.engine.render.RenderQuality3D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The level editor's palette (TD-083), floating over the 3D view while the level editor is on: New Level, the base ground, the tools,
 * and under them what the tool in hand needs (the ground to paint, the objects to place, the brush size and strength). The state is
 * the {@link LevelSession}'s; this only draws it and sends clicks back.
 *
 * <p>Objects and Buildings (roadmap M2b.3) show their prefabs as thumbnails in groups (Trees, Rocks, Plants, Buildings) with a search
 * box, and the chosen one's licence under them ({@link ObjectPalette}); the thumbnails are textures this palette owns, freed by
 * {@link #dispose()}.
 */
public class LevelPalette extends Table {
    private static final float BUTTON_HEIGHT = 24f;
    private static final float LABEL_WIDTH = 84f;
    /** One click on a size or strength button changes it by this factor, or this much. */
    /** Side of a ground swatch, and the accent border round the chosen one, in UI units (UI M3b). */
    private static final float SWATCH_SIZE = 34f;
    private static final float SWATCH_BORDER = 3f;
    private static final int SWATCHES_PER_ROW = 4;
    /** Width of the Brush Size and Strength sliders and of their value labels. */
    private static final float SLIDER_WIDTH = 150f;
    private static final float VALUE_WIDTH = 44f;
    /** The lowest brush strength the Strength slider offers. */
    private static final float STRENGTH_STEP = 0.1f;
    private static final float MIN_RADIUS = 40f;
    private static final float MAX_RADIUS = 4000f;
    /** The Scatter sliders' steps and the ends the palette offers (roadmap M3.6); the settings allow more when set from code. */
    private static final float DENSITY_STEP = 0.1f;
    private static final float SPACING_STEP = 10f;
    private static final float MAX_SPACING_SLIDER = 1000f;
    private static final float SCALE_STEP = 0.05f;
    private static final float MAX_SCALE_SLIDER = 3f;
    /** The grounds offered as the base and as paint; Rock and Dirt joined with the paint layers (roadmap M3.4, TD-089). */
    /** Width of the Sky row's menu. */
    private static final float SKY_WIDTH = 140f;
    /** Side of an object's thumbnail in the palette (M2b.3), the tiles per row, and the width of a tile's name. */
    private static final float THUMBNAIL_SIZE = 64f;
    private static final int TILES_PER_ROW = 4;
    private static final float TILE_WIDTH = 98f;
    /** Width of the palette's search box and of its licence line. */
    private static final float SEARCH_WIDTH = 220f;
    private static final float LICENCE_WIDTH = 360f;
    /** Space between the group buttons, which have no frame of their own. */
    private static final float GROUP_GAP = 12f;
    /** Thumbnail size in the World layout's palette row (TD-107), smaller so a tile fits the row's height. */
    private static final float ROW_THUMBNAIL_SIZE = 50f;
    /** Tile width in the World layout's palette row. */
    private static final float ROW_TILE_WIDTH = 84f;
    /** Gap between buttons in the palette row, so their labels do not run together. */
    private static final float TOOL_GAP = 6f;
    /** Gap between the palette row's sections. */
    private static final float ROW_SECTION_GAP = 16f;
    /** The Sky menu's entries for a scene with no Sky and Weather, and for one whose settings match no preset. */
    static final String SKY_NONE = "None";
    static final String SKY_CUSTOM = "Custom";
    private static final Surface[] GROUNDS = {Surface.GRASS, Surface.SAND, Surface.SNOW, Surface.GRAVEL, Surface.ROCK, Surface.DIRT};

    private final Skin skin;
    private final LevelSession level;
    /** True in the World layout's palette row: see the constructor. */
    private final boolean inRow;
    private final Map<LevelSession.Tool, TextButton> toolButtons = new EnumMap<>(LevelSession.Tool.class);
    private final Map<Surface, TextButton> baseButtons = new EnumMap<>(Surface.class);
    private final Table context;
    private final Label status;
    private final List<TextButton> objectButtons = new ArrayList<>();
    private final List<String> objectPaths = new ArrayList<>();
    /** The thumbnail tiles of Objects and Buildings (M2b.3), the chosen one checked, and the paths they place. */
    private final List<Button> objectTiles = new ArrayList<>();
    private final List<String> tilePaths = new ArrayList<>();
    /** Thumbnails by project path, loaded once; owned here and disposed by {@link #dispose()}. */
    private final Map<String, Texture> thumbnails = new HashMap<>();
    private List<ObjectPalette.Item> paletteItems = List.of();
    private String paletteQuery = "";
    private ObjectPalette.Category paletteCategory;
    private Table objectGrid;
    private Table categoryRow;
    private Label licenceLabel;
    private String licenceShownFor;
    /** The drawer the search and group were chosen in; another drawer starts with none. */
    private LevelSession.Focus browserFocus;
    /** The ground swatches of the Paint tool (UI M3b): one colour square per surface, the chosen one checked. */
    private final Map<Surface, Button> paintButtons = new EnumMap<>(Surface.class);
    private LevelSession.Tool shownTool;
    private LevelSession.Focus shownFocus;
    private final Label titleLabel;
    private final Table base;
    private final Table title;
    private final SelectBox<MapSize> mapSize;
    private final Table mapControls;
    private final Cell<Table> titleCell;
    private final Cell<Table> baseCell;
    private final Table tools;
    /** The base surface of the ground as of {@link #cachedRevision}; the surfaces are only scanned when the terrain changes. */
    private io.github.ldev22live.gdxunity.engine.terrain.Terrain cachedTerrain;
    private int cachedRevision = -1;
    private Surface cachedBase;
    /** The Sky row (roadmap M3.9): the preset the scene's sky holds, and whether that must be looked up again. */
    private final SelectBox<String> skySelect;
    private volatile boolean skyStale = true;

    /**
     * @param actions runs an editor action by id (the heightmap import and export open file dialogs, so they are editor actions)
     */
    /** The palette stacked for a side panel (the classic layout). */
    public LevelPalette(Skin skin, LevelSession level, Predicate<String> actions) {
        this(skin, level, actions, false);
    }

    /**
     * @param inRow true for the World layout's palette row (TD-107, roadmap A03): the sections sit side by side (title, ground,
     *              tools, the tool's settings), and the objects are one strip of tiles beside the search box and groups, so the row
     *              stays short and scrolls sideways; false stacks everything for a side panel
     */
    public LevelPalette(Skin skin, LevelSession level, Predicate<String> actions, boolean inRow) {
        super(skin);
        this.inRow = inRow;
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.level = Objects.requireNonNull(level, "level cannot be null");
        Objects.requireNonNull(actions, "actions cannot be null");
        setBackground("elevated-bg");
        pad(4f);
        left();
        if (inRow) {
            top();
            defaults().top().padRight(ROW_SECTION_GAP);
        }

        title = new Table(skin);
        titleLabel = new Label(level.getFocus().getLabel(), skin);
        title.add(titleLabel).left().padRight(8);
        title.add(button("New Level", "primary", () -> level.createLevel(Surface.GRASS))).height(BUTTON_HEIGHT);
        titleCell = add(title).left();
        row();
        mapControls = new Table(skin);
        mapSize = new SelectBox<>(skin);
        if (inRow) {
            mapSize.setItems(MapSize.values());
            mapSize.setSelected(MapSize.STANDARD);
            mapControls.add(new Label("New map size", skin)).left();
            mapControls.add(mapSize).left();
            mapControls.add(button("New Level", "primary", () -> level.createLevel(Surface.GRASS, mapSize.getSelected())));
            mapControls.row();
            mapControls.add(new Label("View lighting", skin)).left();
            Table lighting = new Table(skin);
            lighting.add(button("Toggle shadows", "flat", () -> actions.test(DefaultEditorActions.VIEW_TOGGLE_SHADOWS)));
            for (RenderQuality3D quality : RenderQuality3D.values()) {
                String name = quality.name().toLowerCase(Locale.ROOT);
                lighting.add(button(name, "flat", () -> actions.test(DefaultEditorActions.VIEW_LIGHTING_QUALITY_PREFIX + name)))
                    .padLeft(buttonGap());
            }
            mapControls.add(lighting).colspan(2).left();
        }

        base = new Table(skin);
        base.add(new Label("Ground", skin, "muted")).width(LABEL_WIDTH).left();
        for (Surface surface : GROUNDS) {
            // With no ground yet, choosing one starts a level on it; otherwise it changes the ground's base
            TextButton b = button(surface.getLabel(), "flat", () -> {
                if (level.findGround() == null) {
                    level.createLevel(surface, inRow && level.getFocus() == LevelSession.Focus.ALL ? mapSize.getSelected() : MapSize.STANDARD);
                } else {
                    level.setBaseSurface(surface);
                }
            });
            baseButtons.put(surface, b);
            base.add(b).height(BUTTON_HEIGHT).padRight(buttonGap());
        }
        // The water level of the ground (roadmap M3.4): each button is one undo step
        base.row();
        base.add(new Label("Water", skin, "muted")).width(LABEL_WIDTH).left();
        Table water = new Table(skin);
        water.add(button("Add", "flat", level::addWater)).height(BUTTON_HEIGHT).padRight(buttonGap());
        water.add(button("Up", "flat", () -> level.moveWater(1))).height(BUTTON_HEIGHT).padRight(buttonGap());
        water.add(button("Down", "flat", () -> level.moveWater(-1))).height(BUTTON_HEIGHT).padRight(buttonGap());
        water.add(button("Remove", "flat", () -> level.setWaterLevel(Float.NaN))).height(BUTTON_HEIGHT);
        base.add(water).colspan(GROUNDS.length).left().padTop(2);
        // Heightmap PNG import and export (roadmap M3.5)
        base.row();
        base.add(new Label("Heightmap", skin, "muted")).width(LABEL_WIDTH).left();
        Table heightmap = new Table(skin);
        heightmap.add(button("Import...", "flat", () -> actions.test(LevelActions.IMPORT_HEIGHTMAP))).height(BUTTON_HEIGHT).padRight(buttonGap());
        heightmap.add(button("Export...", "flat", () -> actions.test(LevelActions.EXPORT_HEIGHTMAP))).height(BUTTON_HEIGHT);
        base.add(heightmap).colspan(GROUNDS.length).left().padTop(2);
        // The scene's sky, fog and weather (roadmap M3.9, TD-090): choosing a preset is one undo step. In the palette row it sits
        // beside Heightmap, so the ground settings take three rows and fit the row's height
        if (inRow) {
            base.add(new Label("Sky", skin, "muted")).left().padLeft(ROW_SECTION_GAP).padRight(8);
        } else {
            base.row();
            base.add(new Label("Sky", skin, "muted")).width(LABEL_WIDTH).left();
        }
        skySelect = new SelectBox<>(skin);
        skySelect.setItems(skyChoices());
        skySelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applySky(skySelect.getSelected());
            }
        });
        base.add(skySelect).colspan(inRow ? 1 : GROUNDS.length).left().padTop(2).height(BUTTON_HEIGHT).width(SKY_WIDTH);
        watchSky(level.getEditorState());
        baseCell = add(base).left().padTop(2);
        row();

        tools = new Table(skin);
        add(tools).left().padTop(2).row();
        buildTools();

        context = new Table(skin) {
            @Override
            public Cell row() {
                // In the palette row the tool's settings (material, path kinds, brush size, objects) sit side by side
                return inRow ? defaults() : super.row();
            }
        };
        context.left();
        if (inRow) {
            context.defaults().padRight(ROW_SECTION_GAP).top();
        }
        add(context).left().padTop(2).row();

        status = new Label("", skin, "muted");
        add(status).left().padTop(2);
        pack();
    }

    /** The gap after a button in a row of buttons: wider in the palette row; the side panel keeps its compact spacing. */
    private float buttonGap() {
        return inRow ? TOOL_GAP : 2f;
    }

    /** In the palette row the palette's sections (title, ground, tools, settings, status) sit side by side instead of stacked. */
    @Override
    public Cell row() {
        return inRow ? defaults() : super.row();
    }

    /** "None", "Custom" and every sky preset's name. */
    static String[] skyChoices() {
        AtmospherePreset[] presets = AtmospherePreset.values();
        String[] choices = new String[presets.length + 2];
        choices[0] = SKY_NONE;
        choices[1] = SKY_CUSTOM;
        for (int i = 0; i < presets.length; i++) {
            choices[i + 2] = presets[i].getDisplayName();
        }
        return choices;
    }

    /** Applies the chosen sky preset as one undo step; None and Custom only describe the scene, so choosing them changes nothing. */
    private void applySky(String choice) {
        EditorState editorState = level.getEditorState();
        if (editorState.getActiveScene() == null || editorState.isPlayModeActive()) {
            skyStale = true;
            return;
        }
        AtmospherePreset.find(choice).ifPresentOrElse(
            preset -> editorState.executeCommand(EnvironmentPresets.applySky(editorState, preset)),
            () -> skyStale = true);
    }

    /** Looks the sky up again after any undo step or a change of scene, rather than every frame. */
    private void watchSky(EditorState editorState) {
        editorState.getCommandHistory().addListener(history -> skyStale = true);
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onActiveSceneChanged(Scene oldScene, Scene newScene) {
                skyStale = true;
            }

            @Override
            public void onHierarchyModified() {
                skyStale = true;
            }
        });
    }

    private TextButton button(String text, String style, Runnable action) {
        TextButton button = new TextButton(text, skin, style);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    /**
     * The tool row for the session's focus (roadmap M1b.4): the World strip's Terrain shows the ground tools, Objects shows Place and
     * Erase, the Level button all of them. The base ground row shows except for Objects.
     */
    private void buildTools() {
        LevelSession.Focus focus = level.getFocus();
        titleCell.setActor(inRow && focus == LevelSession.Focus.ALL ? mapControls : title);
        tools.clearChildren();
        toolButtons.clear();
        if (inRow) {
            tools.add(new Label("Tool", skin, "muted")).left().padRight(8);
        } else {
            tools.add(new Label("Tool", skin, "muted")).width(LABEL_WIDTH).left();
        }
        for (LevelSession.Tool tool : LevelSession.Tool.values()) {
            if (!focus.offers(tool)) {
                continue;
            }
            TextButton b = button(tool.getLabel(), "flat", () -> level.setTool(tool));
            toolButtons.put(tool, b);
            tools.add(b).height(BUTTON_HEIGHT).padRight(buttonGap());
        }
        baseCell.setActor(focus == LevelSession.Focus.OBJECTS || focus == LevelSession.Focus.CHARACTERS
            || focus == LevelSession.Focus.BUILDINGS ? null : base);
        titleLabel.setText(focus.getLabel());
        shownFocus = focus;
        shownTool = null;
        pack();
    }

    /** Rebuilds the row under the tools for the tool in hand. */
    private void buildContext() {
        context.clearChildren();
        objectButtons.clear();
        objectPaths.clear();
        objectTiles.clear();
        tilePaths.clear();
        licenceLabel = null;
        licenceShownFor = null;
        paintButtons.clear();
        LevelSession.Tool tool = level.getTool();
        if (tool == LevelSession.Tool.PAINT) {
            // The ground as colour swatches, as in the reference layout (UI M3b); each says its name as a tool tip
            context.add(new Label("Material", skin, "muted")).width(LABEL_WIDTH).left().top();
            Table swatches = new Table(skin);
            int column = 0;
            for (Surface surface : GROUNDS) {
                Button swatch = swatch(surface);
                paintButtons.put(surface, swatch);
                swatches.add(swatch).size(SWATCH_SIZE + 2 * SWATCH_BORDER).pad(2);
                if (++column % SWATCHES_PER_ROW == 0) {
                    swatches.row();
                }
            }
            context.add(swatches).left();
            context.row();
        } else if (tool == LevelSession.Tool.PATH) {
            // Roads, rivers and cliffs (roadmap M3.7): what the selected path is, its points, what stands beside it, Apply
            context.add(new Label("Path", skin, "muted")).width(LABEL_WIDTH).left();
            for (TerrainPath.Kind kind : TerrainPath.Kind.values()) {
                String label = kind.name().charAt(0) + kind.name().substring(1).toLowerCase(Locale.ROOT);
                context.add(button(label, "flat", () -> level.setPathKind(kind))).height(BUTTON_HEIGHT).padRight(2);
            }
            context.add(button("Undo Point", "flat", level::removeLastPathPoint)).height(BUTTON_HEIGHT).padRight(2);
            context.add(button("New Path", "flat", level::startNewPath)).height(BUTTON_HEIGHT).padRight(2);
            context.add(button("Apply", "primary", level::applyPath)).height(BUTTON_HEIGHT);
            context.row();
            Table beside = new Table(skin);
            beside.add(new Label("Beside", skin, "muted")).width(LABEL_WIDTH).left();
            beside.add(button("Nothing", "flat", () -> level.setPathPrefab(""))).height(BUTTON_HEIGHT).padRight(2);
            for (String path : level.listObjects(LevelSession.OBJECT_FOLDER)) {
                beside.add(button(LevelSession.objectLabel(path), "flat", () -> level.setPathPrefab(path))).height(BUTTON_HEIGHT).padRight(2);
            }
            context.add(beside).colspan(TerrainPath.Kind.values().length + 4).left().row();
        } else if (tool == LevelSession.Tool.PLACE && level.getFocus() != LevelSession.Focus.CHARACTERS) {
            buildObjectBrowser();
        } else if (tool == LevelSession.Tool.SCATTER) {
            buildObjectBrowser();
            buildScatterSettings();
        } else if (tool == LevelSession.Tool.PLACE) {
            List<String> objects = level.listObjects();
            boolean characters = level.getFocus() == LevelSession.Focus.CHARACTERS;
            boolean buildings = level.getFocus() == LevelSession.Focus.BUILDINGS;
            context.add(new Label(characters ? "Place" : buildings ? "Building" : "Object", skin, "muted")).width(LABEL_WIDTH).left();
            if (objects.isEmpty()) {
                context.add(new Label("No prefabs in " + level.getObjectFolder(), skin, "muted")).left();
            }
            for (String path : objects) {
                TextButton b = button(LevelSession.objectLabel(path), "flat", () -> level.setObjectPath(path));
                objectButtons.add(b);
                objectPaths.add(path);
                context.add(b).height(BUTTON_HEIGHT).padRight(2);
            }
            context.row();
            if (!objects.isEmpty() && level.getObjectPath().isEmpty()) {
                level.setObjectPath(objects.get(0));
            }
        }
        if (tool.isBrush()) {
            TerrainBrush brush = level.getTerrainSession().getBrush();
            // Brush Size and Strength as sliders with their values, as in the reference layout (UI M3b). The size slider is
            // logarithmic, so small brushes are as easy to set as large ones
            Table size = new Table(skin);
            size.add(new Label("Brush Size", skin, "muted")).width(LABEL_WIDTH).left();
            Label radiusValue = new Label(Integer.toString(Math.round(brush.getRadius())), skin);
            Slider radius = new Slider(0f, 1f, 0.001f, false, skin);
            radius.setValue(sizeToSlider(brush.getRadius()));
            radius.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    brush.setRadius(sliderToSize(radius.getValue()));
                    radiusValue.setText(Integer.toString(Math.round(brush.getRadius())));
                }
            });
            size.add(radius).width(SLIDER_WIDTH).padRight(6);
            size.add(radiusValue).width(VALUE_WIDTH).left().row();
            if (tool != LevelSession.Tool.HOLE) {
                // Paint uses the strength too since its edges are soft (TD-089)
                size.add(new Label("Strength", skin, "muted")).width(LABEL_WIDTH).left();
                Label strengthValue = new Label(percent(brush.getStrength()), skin);
                Slider strength = new Slider(STRENGTH_STEP, 1f, 0.01f, false, skin);
                strength.setValue(brush.getStrength());
                strength.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        brush.setStrength(strength.getValue());
                        strengthValue.setText(percent(brush.getStrength()));
                    }
                });
                size.add(strength).width(SLIDER_WIDTH).padRight(6);
                size.add(strengthValue).width(VALUE_WIDTH).left().row();
            }
            context.add(size).left().row();
        }
        shownTool = tool;
        pack();
    }

    @Override
    public void act(float delta) {
        setVisible(level.isActive());
        if (!level.isActive()) {
            return;
        }
        if (shownFocus != level.getFocus()) {
            buildTools();
        }
        if (shownTool != level.getTool()) {
            buildContext();
        }
        for (Map.Entry<LevelSession.Tool, TextButton> entry : toolButtons.entrySet()) {
            entry.getValue().setColor(skin.getColor(entry.getKey() == level.getTool() ? "accent" : "text-primary"));
        }
        Surface base = baseOfGround();
        for (Map.Entry<Surface, TextButton> entry : baseButtons.entrySet()) {
            entry.getValue().setColor(skin.getColor(entry.getKey() == base ? "accent" : "text-primary"));
        }
        for (Map.Entry<Surface, Button> entry : paintButtons.entrySet()) {
            entry.getValue().setProgrammaticChangeEvents(false);
            entry.getValue().setChecked(entry.getKey() == level.getPaintSurface());
        }
        for (int i = 0; i < objectButtons.size(); i++) {
            objectButtons.get(i).setColor(skin.getColor(objectPaths.get(i).equals(level.getObjectPath()) ? "accent" : "text-primary"));
        }
        for (int i = 0; i < objectTiles.size(); i++) {
            objectTiles.get(i).setProgrammaticChangeEvents(false);
            objectTiles.get(i).setChecked(tilePaths.get(i).equals(level.getObjectPath()));
            objectTiles.get(i).setProgrammaticChangeEvents(true);
        }
        if (licenceLabel != null && !level.getObjectPath().equals(licenceShownFor)) {
            licenceShownFor = level.getObjectPath();
            licenceLabel.setText(paletteItems.stream().filter(item -> item.path().equals(licenceShownFor)).findFirst()
                .map(item -> item.label() + ": " + item.licence()).orElse(""));
        }
        TerrainBrush brush = level.getTerrainSession().getBrush();
        String text = level.findGround() == null
            ? "No ground yet: press New Level"
            : level.getTool().isBrush()
                ? level.getTool() == LevelSession.Tool.RAMP
                    ? String.format(Locale.ROOT, "Ramp: drag from one point to another; %.0f wide.", brush.getRadius() * 2f)
                : level.getTool() == LevelSession.Tool.HOLE
                    ? String.format(Locale.ROOT, "Hole: size %.0f. Ctrl fills holes.", brush.getRadius())
                    : String.format(Locale.ROOT, "%s: size %.0f, strength %.1f. Ctrl inverts, Shift smooths.", level.getTool().getLabel(),
                        brush.getRadius(), brush.getStrength())
                : level.getTool() == LevelSession.Tool.PATH ? pathStatus()
                : level.getTool() == LevelSession.Tool.PLACE ? "Click the ground to place"
                : level.getTool() == LevelSession.Tool.SCATTER
                    ? String.format(Locale.ROOT, "Scatter: drag, size %.0f. Ctrl-drag removes.", brush.getRadius())
                : "Click an object to remove it";
        if (!text.contentEquals(status.getText())) {
            status.setText(text);
        }
        if (skyStale) {
            skyStale = false;
            String sky = EnvironmentPresets.describeSky(level.getEditorState().getActiveScene());
            if (!sky.equals(skySelect.getSelected())) {
                skySelect.getSelection().setProgrammaticChangeEvents(false);
                skySelect.setSelected(sky);
                skySelect.getSelection().setProgrammaticChangeEvents(true);
            }
        }
        super.act(delta);
    }

    /** The Path tool's status line: the selected path, or how to start one (roadmap M3.7). */
    private String pathStatus() {
        Entity selected = level.selectedPath();
        if (selected == null) {
            return "Path: click the ground to start a road, river or cliff";
        }
        TerrainPathComponent path = selected.getComponent(TerrainPathComponent.class);
        return String.format(Locale.ROOT, "%s: %s, %d points, %.0f wide. Click to add a point; Apply carves it.", selected.getName(),
            path.getKind().name().toLowerCase(Locale.ROOT), path.getPointCount(), path.getWidth());
    }

    /** The surface the whole ground is, to highlight its button; null when mixed or there is no ground. */
    private Surface baseOfGround() {
        var terrain = level.findTerrain();
        if (terrain == null) {
            return null;
        }
        if (terrain == cachedTerrain && terrain.getRevision() == cachedRevision) {
            return cachedBase;
        }
        byte[] codes = terrain.copySurfaces();
        byte first = codes[0];
        cachedBase = Surface.fromCode(first);
        for (byte code : codes) {
            if (code != first) {
                cachedBase = null;
                break;
            }
        }
        cachedTerrain = terrain;
        cachedRevision = terrain.getRevision();
        return cachedBase;
    }

    /**
     * Objects or Buildings with Place in hand (roadmap M2b.3): a search box, the groups present (All, Trees, Rocks, Plants,
     * Buildings), the prefabs as thumbnail tiles, and the chosen one's licence.
     */
    private void buildObjectBrowser() {
        if (browserFocus != level.getFocus()) {
            browserFocus = level.getFocus();
            paletteQuery = "";
            paletteCategory = null;
        }
        List<String> objects = level.listObjects();
        try {
            paletteItems = ObjectPalette.describe(level.getAssetRoot(), objects);
        } catch (IOException | RuntimeException e) {
            // The palette still places by name; it says why the thumbnails and licences are missing
            paletteItems = objects.stream().map(path -> new ObjectPalette.Item(path, LevelSession.objectLabel(path),
                ObjectPalette.Category.OTHER, "", "Licence unknown: " + e.getMessage())).toList();
        }
        if (paletteCategory != null && !ObjectPalette.categories(paletteItems).contains(paletteCategory)) {
            paletteCategory = null;
        }
        Table browser = new Table(skin);
        browser.top().left();
        browser.defaults().left();
        if (objects.isEmpty()) {
            browser.add(new Label("No prefabs in " + level.getObjectFolder(), skin, "muted")).row();
        } else {
            TextField search = new TextField(paletteQuery, skin);
            search.setMessageText("Search " + level.getFocus().getLabel().toLowerCase(Locale.ROOT));
            search.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    paletteQuery = search.getText();
                    fillObjectGrid();
                }
            });
            categoryRow = new Table(skin);
            objectGrid = new Table(skin);
            objectGrid.top().left();
            licenceLabel = new Label("", skin, "muted");
            if (inRow) {
                licenceLabel.setEllipsis(true); // one line in the row; the tile's tooltip has the whole licence
            } else {
                licenceLabel.setWrap(true);
            }
            if (inRow) {
                // Search, groups and licence in a column; the tiles in one strip beside it
                Table column = new Table(skin);
                column.top().left();
                column.defaults().left();
                column.add(search).width(SEARCH_WIDTH).padBottom(3).row();
                column.add(categoryRow).padBottom(3).row();
                column.add(licenceLabel).width(SEARCH_WIDTH);
                browser.add(column).top().padRight(ROW_SECTION_GAP);
                browser.add(objectGrid).top();
            } else {
                browser.add(search).width(SEARCH_WIDTH).padBottom(3).row();
                browser.add(categoryRow).padBottom(3).row();
                browser.add(objectGrid).row();
                browser.add(licenceLabel).width(LICENCE_WIDTH).padTop(3).row();
            }
            fillObjectGrid();
            if (level.getObjectPath().isEmpty() || !objects.contains(level.getObjectPath())) {
                level.setObjectPath(objects.get(0));
            }
        }
        context.add(browser).colspan(2).left().row();
    }

    /** Fills the group buttons and the tiles for the group and search text chosen now. */
    private void fillObjectGrid() {
        categoryRow.clearChildren();
        categoryRow.add(categoryButton("All", null)).height(BUTTON_HEIGHT).padRight(GROUP_GAP);
        for (ObjectPalette.Category category : ObjectPalette.categories(paletteItems)) {
            categoryRow.add(categoryButton(category.label(), category)).height(BUTTON_HEIGHT).padRight(GROUP_GAP);
        }
        objectGrid.clearChildren();
        objectTiles.clear();
        tilePaths.clear();
        List<ObjectPalette.Item> shown = ObjectPalette.filter(paletteItems, paletteCategory, paletteQuery);
        if (shown.isEmpty()) {
            objectGrid.add(new Label("Nothing matches \"" + paletteQuery.trim() + "\"", skin, "muted"));
        }
        int column = 0;
        for (ObjectPalette.Item item : shown) {
            Button tile = tile(item);
            objectTiles.add(tile);
            tilePaths.add(item.path());
            objectGrid.add(tile).width(inRow ? ROW_TILE_WIDTH : TILE_WIDTH).top().pad(1);
            if (++column % TILES_PER_ROW == 0 && !inRow) {
                objectGrid.row();
            }
        }
        licenceShownFor = null;
    }

    private TextButton categoryButton(String label, ObjectPalette.Category category) {
        TextButton button = button(label, "flat", () -> {
            paletteCategory = category;
            fillObjectGrid();
        });
        button.setColor(skin.getColor(category == paletteCategory ? "accent" : "text-primary"));
        return button;
    }

    /** One object: its thumbnail (or its group's name when it has none) over its name; the checked look marks the chosen one. */
    private Button tile(ObjectPalette.Item item) {
        Button button = new Button(skin.get("flat", TextButton.TextButtonStyle.class));
        Texture texture = thumbnail(item.thumbnail());
        if (texture != null) {
            Image image = new Image(texture);
            image.setScaling(Scaling.fit);
            button.add(image).size(inRow ? ROW_THUMBNAIL_SIZE : THUMBNAIL_SIZE).row();
        } else {
            button.add(new Label(item.category().label(), skin, "muted")).height(inRow ? ROW_THUMBNAIL_SIZE : THUMBNAIL_SIZE).row();
        }
        Label name = new Label(item.label(), skin);
        if (inRow) {
            name.setEllipsis(true); // the full name is in the tooltip
        } else {
            name.setWrap(true);
        }
        name.setAlignment(Align.center);
        button.add(name).width((inRow ? ROW_TILE_WIDTH : TILE_WIDTH) - 6f);
        button.pad(2f);
        button.addListener(new TextTooltip(item.label() + "\n" + item.licence(), skin));
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                level.setObjectPath(item.path());
            }
        });
        return button;
    }

    /** The thumbnail at a project path, loaded once; null when there is none or it cannot be read. */
    private Texture thumbnail(String path) {
        if (path.isEmpty()) {
            return null;
        }
        if (thumbnails.containsKey(path)) {
            return thumbnails.get(path);
        }
        Texture texture;
        try {
            texture = new Texture(Gdx.files.absolute(new File(level.getAssetRoot(), path).getAbsolutePath()));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        } catch (GdxRuntimeException e) {
            level.getEditorState().setStatusMessage("Could not read the thumbnail " + path + ": " + e.getMessage(), true);
            texture = null;
        }
        thumbnails.put(path, texture);
        return texture;
    }

    /** Frees the thumbnails; the palette shows names only after this. */
    public void dispose() {
        for (Texture texture : thumbnails.values()) {
            if (texture != null) {
                texture.dispose();
            }
        }
        thumbnails.clear();
    }

    /** A ground swatch: a square of the surface's colour in a button whose checked look (the accent) is its border. */
    private Button swatch(Surface surface) {
        Button button = new Button(skin.get("flat", TextButton.TextButtonStyle.class));
        Color colour = new Color(surface.channel(0), surface.channel(1), surface.channel(2), 1f);
        button.add(new Image(skin.newDrawable(EditorSkinFactory.WHITE_PIXEL, colour))).size(SWATCH_SIZE);
        button.pad(SWATCH_BORDER);
        button.addListener(new TextTooltip(surface.getLabel(), skin));
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                level.setPaintSurface(surface);
            }
        });
        return button;
    }

    /** The brush radius for a size slider position 0 to 1, from {@link #MIN_RADIUS} to {@link #MAX_RADIUS} on a log scale. */
    static float sliderToSize(float position) {
        return (float) (MIN_RADIUS * Math.pow(MAX_RADIUS / MIN_RADIUS, Math.max(0f, Math.min(1f, position))));
    }

    /** The size slider position 0 to 1 of a brush radius, the inverse of {@link #sliderToSize}. */
    static float sizeToSlider(float radius) {
        float clamped = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        return (float) (Math.log(clamped / MIN_RADIUS) / Math.log(MAX_RADIUS / MIN_RADIUS));
    }

    /**
     * The Scatter tool's settings as sliders (roadmap M3.6): the brush size it shares with the terrain brushes, then density, spacing,
     * slope limit, scale range and height range. The height range runs over the ground's own lowest to highest point; at either end
     * there is no limit.
     */
    private void buildScatterSettings() {
        ScatterSettings settings = level.getScatterSettings();
        TerrainBrush brush = level.getTerrainSession().getBrush();
        Table rows = new Table(skin);
        settingRow(rows, "Brush Size", sizeToSlider(brush.getRadius()), 0f, 1f, 0.001f,
            v -> brush.setRadius(sliderToSize(v)), () -> Integer.toString(Math.round(brush.getRadius())));
        settingRow(rows, "Density", settings.getDensity(), DENSITY_STEP, ScatterSettings.MAX_DENSITY, DENSITY_STEP,
            settings::setDensity, () -> String.format(Locale.ROOT, "%.1f", settings.getDensity()));
        settingRow(rows, "Spacing", settings.getSpacing(), 0f, MAX_SPACING_SLIDER, SPACING_STEP,
            settings::setSpacing, () -> Integer.toString(Math.round(settings.getSpacing())));
        settingRow(rows, "Max Slope", settings.getMaxSlopeDegrees(), 0f, 90f, 1f,
            settings::setMaxSlopeDegrees, () -> Math.round(settings.getMaxSlopeDegrees()) + "\u00b0");
        settingRow(rows, "Scale Min", settings.getMinScale(), SCALE_STEP, MAX_SCALE_SLIDER, SCALE_STEP,
            v -> settings.setScaleRange(v, Math.max(v, settings.getMaxScale())),
            () -> String.format(Locale.ROOT, "%.2f", settings.getMinScale()));
        settingRow(rows, "Scale Max", settings.getMaxScale(), SCALE_STEP, MAX_SCALE_SLIDER, SCALE_STEP,
            v -> settings.setScaleRange(Math.min(v, settings.getMinScale()), v),
            () -> String.format(Locale.ROOT, "%.2f", settings.getMaxScale()));
        float[] range = heightRange();
        settingRow(rows, "Lowest", fractionOf(settings.getMinHeight(), range, 0f), 0f, 1f, 0.01f,
            v -> settings.setHeightRange(v <= 0f ? Float.NEGATIVE_INFINITY : heightAt(v, range),
                Math.max(v <= 0f ? Float.NEGATIVE_INFINITY : heightAt(v, range), settings.getMaxHeight())),
            () -> heightLabel(settings.getMinHeight()));
        settingRow(rows, "Highest", fractionOf(settings.getMaxHeight(), range, 1f), 0f, 1f, 0.01f,
            v -> settings.setHeightRange(Math.min(settings.getMinHeight(), v >= 1f ? Float.POSITIVE_INFINITY : heightAt(v, range)),
                v >= 1f ? Float.POSITIVE_INFINITY : heightAt(v, range)),
            () -> heightLabel(settings.getMaxHeight()));
        context.add(rows).left().row();
    }

    /** One labelled slider whose value label follows it. */
    private void settingRow(Table rows, String name, float value, float min, float max, float step,
                            Consumer<Float> apply, Supplier<String> shown) {
        rows.add(new Label(name, skin, "muted")).width(LABEL_WIDTH).left();
        Label valueLabel = new Label(shown.get(), skin);
        Slider slider = new Slider(min, max, step, false, skin);
        slider.setValue(Math.max(min, Math.min(max, value)));
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                apply.accept(slider.getValue());
                valueLabel.setText(shown.get());
            }
        });
        rows.add(slider).width(SLIDER_WIDTH).padRight(6);
        rows.add(valueLabel).width(VALUE_WIDTH).left().row();
    }

    /** The ground's lowest and highest point, or 0 to 1 when there is no ground. */
    private float[] heightRange() {
        Terrain terrain = level.findTerrain();
        float[] range = terrain != null ? terrain.heightRange() : new float[] {0f, 1f};
        return range[1] > range[0] ? range : new float[] {range[0], range[0] + 1f};
    }

    private static float heightAt(float fraction, float[] range) {
        return range[0] + fraction * (range[1] - range[0]);
    }

    private static float fractionOf(float height, float[] range, float unlimited) {
        return Float.isInfinite(height) ? unlimited : (height - range[0]) / (range[1] - range[0]);
    }

    private static String heightLabel(float height) {
        return Float.isInfinite(height) ? "any" : Integer.toString(Math.round(height));
    }

    private static String percent(float strength) {
        return Math.round(strength * 100f) + "%";
    }
}
