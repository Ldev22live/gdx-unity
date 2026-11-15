package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainBrush;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The level editor's palette (TD-083), floating over the 3D view while the level editor is on: New Level, the base ground, the tools,
 * and under them what the tool in hand needs (the ground to paint, the objects to place, the brush size and strength). The state is
 * the {@link LevelSession}'s; this only draws it and sends clicks back.
 */
public class LevelPalette extends Table {
    private static final float BUTTON_HEIGHT = 24f;
    private static final float LABEL_WIDTH = 64f;
    /** One click on a size or strength button changes it by this factor, or this much. */
    private static final float RADIUS_STEP = 1.25f;
    private static final float STRENGTH_STEP = 0.1f;
    private static final float MIN_RADIUS = 40f;
    private static final float MAX_RADIUS = 4000f;
    private static final Surface[] GROUNDS = {Surface.GRASS, Surface.SAND, Surface.SNOW, Surface.GRAVEL};

    private final Skin skin;
    private final LevelSession level;
    private final Map<LevelSession.Tool, TextButton> toolButtons = new EnumMap<>(LevelSession.Tool.class);
    private final Map<Surface, TextButton> baseButtons = new EnumMap<>(Surface.class);
    private final Table context;
    private final Label status;
    private final List<TextButton> objectButtons = new ArrayList<>();
    private final List<String> objectPaths = new ArrayList<>();
    private final Map<Surface, TextButton> paintButtons = new EnumMap<>(Surface.class);
    private LevelSession.Tool shownTool;
    private LevelSession.Focus shownFocus;
    private final Label titleLabel;
    private final Table base;
    private final Cell<Table> baseCell;
    private final Table tools;
    /** The base surface of the ground as of {@link #cachedRevision}; the surfaces are only scanned when the terrain changes. */
    private io.github.ldev22live.gdxunity.engine.terrain.Terrain cachedTerrain;
    private int cachedRevision = -1;
    private Surface cachedBase;

    public LevelPalette(Skin skin, LevelSession level) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.level = Objects.requireNonNull(level, "level cannot be null");
        setBackground("elevated-bg");
        pad(4f);
        left();

        Table title = new Table(skin);
        titleLabel = new Label(level.getFocus().getLabel(), skin);
        title.add(titleLabel).left().padRight(8);
        title.add(button("New Level", "primary", () -> level.createLevel(Surface.GRASS))).height(BUTTON_HEIGHT);
        add(title).left().row();

        base = new Table(skin);
        base.add(new Label("Ground", skin, "muted")).width(LABEL_WIDTH).left();
        for (Surface surface : GROUNDS) {
            // With no ground yet, choosing one starts a level on it; otherwise it changes the ground's base
            TextButton b = button(surface.getLabel(), "flat", () -> {
                if (level.findGround() == null) {
                    level.createLevel(surface);
                } else {
                    level.setBaseSurface(surface);
                }
            });
            baseButtons.put(surface, b);
            base.add(b).height(BUTTON_HEIGHT).padRight(2);
        }
        baseCell = add(base).left().padTop(2);
        row();

        tools = new Table(skin);
        add(tools).left().padTop(2).row();
        buildTools();

        context = new Table(skin);
        context.left();
        add(context).left().padTop(2).row();

        status = new Label("", skin, "muted");
        add(status).left().padTop(2);
        pack();
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
        tools.clearChildren();
        toolButtons.clear();
        tools.add(new Label("Tool", skin, "muted")).width(LABEL_WIDTH).left();
        for (LevelSession.Tool tool : LevelSession.Tool.values()) {
            if (!focus.offers(tool)) {
                continue;
            }
            TextButton b = button(tool.getLabel(), "flat", () -> level.setTool(tool));
            toolButtons.put(tool, b);
            tools.add(b).height(BUTTON_HEIGHT).padRight(2);
        }
        baseCell.setActor(focus == LevelSession.Focus.OBJECTS ? null : base);
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
        paintButtons.clear();
        LevelSession.Tool tool = level.getTool();
        if (tool == LevelSession.Tool.PAINT) {
            context.add(new Label("Paint", skin, "muted")).width(LABEL_WIDTH).left();
            for (Surface surface : GROUNDS) {
                TextButton b = button(surface.getLabel(), "flat", () -> level.setPaintSurface(surface));
                paintButtons.put(surface, b);
                context.add(b).height(BUTTON_HEIGHT).padRight(2);
            }
            context.row();
        } else if (tool == LevelSession.Tool.PLACE) {
            List<String> objects = level.listObjects();
            context.add(new Label("Object", skin, "muted")).width(LABEL_WIDTH).left();
            if (objects.isEmpty()) {
                context.add(new Label("No prefabs in " + LevelSession.OBJECT_FOLDER, skin, "muted")).left();
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
            Table size = new Table(skin);
            size.add(new Label("Size", skin, "muted")).width(LABEL_WIDTH).left();
            size.add(button("-", "flat", () -> brush.setRadius(Math.max(MIN_RADIUS, brush.getRadius() / RADIUS_STEP))))
                .height(BUTTON_HEIGHT).width(28).padRight(2);
            size.add(button("+", "flat", () -> brush.setRadius(Math.min(MAX_RADIUS, brush.getRadius() * RADIUS_STEP))))
                .height(BUTTON_HEIGHT).width(28).padRight(10);
            if (tool != LevelSession.Tool.PAINT) {
                size.add(new Label("Strength", skin, "muted")).padRight(6);
                size.add(button("-", "flat", () -> brush.setStrength(Math.max(STRENGTH_STEP, brush.getStrength() - STRENGTH_STEP))))
                    .height(BUTTON_HEIGHT).width(28).padRight(2);
                size.add(button("+", "flat", () -> brush.setStrength(Math.min(1f, brush.getStrength() + STRENGTH_STEP))))
                    .height(BUTTON_HEIGHT).width(28);
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
        for (Map.Entry<Surface, TextButton> entry : paintButtons.entrySet()) {
            entry.getValue().setColor(skin.getColor(entry.getKey() == level.getPaintSurface() ? "accent" : "text-primary"));
        }
        for (int i = 0; i < objectButtons.size(); i++) {
            objectButtons.get(i).setColor(skin.getColor(objectPaths.get(i).equals(level.getObjectPath()) ? "accent" : "text-primary"));
        }
        TerrainBrush brush = level.getTerrainSession().getBrush();
        String text = level.findGround() == null
            ? "No ground yet: press New Level"
            : level.getTool().isBrush()
                ? String.format(Locale.ROOT, "%s: size %.0f, strength %.1f. Ctrl inverts, Shift smooths.", level.getTool().getLabel(),
                    brush.getRadius(), brush.getStrength())
                : level.getTool() == LevelSession.Tool.PLACE ? "Click the ground to place" : "Click an object to remove it";
        if (!text.contentEquals(status.getText())) {
            status.setText(text);
        }
        super.act(delta);
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
}
