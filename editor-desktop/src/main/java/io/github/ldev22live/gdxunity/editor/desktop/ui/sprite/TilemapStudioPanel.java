package io.github.ldev22live.gdxunity.editor.desktop.ui.sprite;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.sprite.TilePaintInteraction;
import io.github.ldev22live.gdxunity.editor.sprite.TilemapSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetService;
import io.github.ldev22live.gdxunity.engine.render.components.FrameAtlas;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.sprite.AutoTileRule;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Tiles tab (TD-066, milestone E): paints the tile map of the selected entity. A palette of the tile set's frames on the left, the map in the
 * middle, layers, tools, auto-tile rules and collision on the right. Edits are undoable inside the tab (its own history, like the Sprites tab).
 */
public final class TilemapStudioPanel extends Table implements Disposable {
    private static final float TOOL_WIDTH = 74f;
    private static final float ROW_HEIGHT = 24f;
    private static final float RIGHT_WIDTH = 220f;
    private static final float PALETTE_WIDTH = 150f;
    private static final float PALETTE_CELL = 32f;
    private static final int PALETTE_COLUMNS = 4;
    private static final int RULE_TILES = AutoTileRule.VARIANTS;

    private final EditorState editorState;
    private final Skin skin;
    private final Table content;
    private final Label message;
    private final TilemapCanvasWidget canvas;
    private final Table paletteTable = new Table();
    private final Table layersTable = new Table();
    private final Table rulesTable = new Table();
    private final Label statusLabel;
    private final Label hoverLabel;
    private final List<TextButton> toolButtons = new ArrayList<>();
    private TextButton solidButton;
    private TextButton paintInViewButton;
    private boolean paintInView;
    private TextButton collidesButton;
    private TextField colsField;
    private TextField rowsField;
    private TilemapComponent component;
    private TilemapSession session;
    private FrameAtlas atlas;
    private SpriteDocument atlasSource;
    private long builtRevision = -1;
    private int builtTile = -1;

    public TilemapStudioPanel(Skin skin, EditorState editorState) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        message = new Label("", skin, "muted");
        message.setWrap(true);
        statusLabel = new Label("", skin, "muted");
        hoverLabel = new Label("", skin, "muted");
        canvas = new TilemapCanvasWidget(() -> session, () -> atlas);
        content = new Table(skin);
        content.add(leftColumn()).top().width(PALETTE_WIDTH).padRight(4);
        content.add(canvas).grow().minSize(0, 0);
        content.add(rightColumn()).top().width(RIGHT_WIDTH).padLeft(4).row();
        Table status = new Table(skin);
        status.add(statusLabel).left().expandX();
        status.add(hoverLabel).right();
        content.add(status).growX().colspan(3).height(ROW_HEIGHT);
        add(message).growX().pad(12).top().row();
        add(content).grow();
    }

    private TextButton button(String text, Runnable action) {
        TextButton b = new TextButton(text, skin);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return b;
    }

    private Table leftColumn() {
        Table column = new Table(skin);
        column.top().left();
        column.add(new Label("Tiles", skin, "section")).left().padBottom(2).row();
        ScrollPane scroll = new ScrollPane(paletteTable, skin);
        scroll.setScrollingDisabled(true, false);
        column.add(scroll).grow().row();
        return column;
    }

    private Table rightColumn() {
        Table column = new Table(skin);
        column.top().left();
        column.add(new Label("Tools", skin, "section")).left().row();
        Table tools = new Table(skin);
        int n = 0;
        for (TilemapSession.Tool tool : TilemapSession.Tool.values()) {
            TextButton b = button(label(tool), () -> {
                if (session != null) {
                    session.setTool(tool);
                }
                refreshToggles();
            });
            b.setUserObject(tool);
            toolButtons.add(b);
            tools.add(b).width(TOOL_WIDTH / 2f + 18f).height(ROW_HEIGHT).padRight(2).padBottom(2);
            if (++n % 2 == 0) {
                tools.row();
            }
        }
        column.add(tools).left().padBottom(6).row();
        solidButton = button("Solid", this::toggleSolid);
        collidesButton = button("Collides", this::toggleCollides);
        Table flags = new Table(skin);
        flags.add(solidButton).width(TOOL_WIDTH).height(ROW_HEIGHT).padRight(2);
        flags.add(collidesButton).width(TOOL_WIDTH + 10f).height(ROW_HEIGHT);
        column.add(flags).left().padBottom(4).row();
        paintInViewButton = button("Paint in view", this::togglePaintInView);
        column.add(paintInViewButton).left().width(TOOL_WIDTH * 2 + 12f).height(ROW_HEIGHT).padBottom(8).row();

        Table layersHeader = new Table(skin);
        layersHeader.add(new Label("Layers", skin, "section")).left().expandX();
        layersHeader.add(button("+", () -> withSession(s -> s.addLayer("Layer " + (s.getMap().getLayerCount() + 1))))).width(28f).height(ROW_HEIGHT);
        layersHeader.add(button("-", () -> withSession(TilemapSession::removeLayer))).width(28f).height(ROW_HEIGHT).padLeft(2);
        column.add(layersHeader).growX().row();
        column.add(layersTable).growX().padBottom(2).row();
        column.add(button("Clear Layer", () -> withSession(TilemapSession::clearLayer))).left().height(ROW_HEIGHT).padBottom(8).row();

        column.add(new Label("Auto-tile", skin, "section")).left().row();
        column.add(rulesTable).growX().row();
        column.add(button("Rule from 16 tiles", this::makeRule)).left().height(ROW_HEIGHT).padBottom(2).row();
        Label hint = new Label("Makes a rule from the selected tile and the 15 after it, in the order of neighbour bits: none, N, E, N+E, S ...",
            skin, "muted");
        hint.setWrap(true);
        column.add(hint).growX().padBottom(8).row();

        column.add(new Label("Map size", skin, "section")).left().row();
        colsField = new TextField("", skin);
        rowsField = new TextField("", skin);
        Table size = new Table(skin);
        size.add(colsField).width(44f).height(ROW_HEIGHT);
        size.add(new Label("x", skin, "muted")).padLeft(3).padRight(3);
        size.add(rowsField).width(44f).height(ROW_HEIGHT).padRight(4);
        size.add(button("Resize", this::resize)).height(ROW_HEIGHT);
        column.add(size).left().padBottom(4).row();
        column.add(button("Undo", () -> withSession(s -> s.getHistory().undo()))).left().width(TOOL_WIDTH).height(ROW_HEIGHT).padBottom(2).row();
        column.add(button("Redo", () -> withSession(s -> s.getHistory().redo()))).left().width(TOOL_WIDTH).height(ROW_HEIGHT).padBottom(2).row();
        column.add(button("Fit", canvas::fit)).left().width(TOOL_WIDTH).height(ROW_HEIGHT).row();
        return column;
    }

    private static String label(TilemapSession.Tool tool) {
        String name = tool.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private void withSession(java.util.function.Consumer<TilemapSession> action) {
        if (session != null) {
            action.accept(session);
        }
    }

    private void say(String text) {
        statusLabel.setText(text);
    }

    // ---- actions ----

    private void toggleSolid() {
        if (session == null) {
            return;
        }
        int tile = session.getTile();
        session.setSolid(tile, !session.getMap().isSolid(tile));
    }

    private void togglePaintInView() {
        paintInView = !paintInView;
        say(paintInView ? "Painting in the 2D scene view: left mouse paints, right drag pans, the wheel zooms" : "Painting in the scene view is off");
        refreshToggles();
    }

    /**
     * What the 2D scene view paints while "Paint in view" is on: the selected entity's tile map and this tab's session (tool, tile, layer, rule);
     * null when it is off, nothing is selected or the game is playing.
     */
    public TilePaintInteraction.Target getPaintTarget() {
        if (!paintInView || component == null || session == null || editorState.isPlayModeActive()) {
            return null;
        }
        return new TilePaintInteraction.Target(component, session);
    }

    private void toggleCollides() {
        if (component != null) {
            component.setCollides(!component.isCollides());
            refreshToggles();
        }
    }

    private void resize() {
        if (session == null) {
            return;
        }
        try {
            session.resize(Integer.parseInt(colsField.getText().trim()), Integer.parseInt(rowsField.getText().trim()));
            canvas.requestFit();
        } catch (IllegalArgumentException e) {
            say("The size is two whole numbers from 1 to " + TileMapData.MAX_CELLS);
        }
    }

    private void makeRule() {
        if (session == null) {
            return;
        }
        SpriteDocument tileSet = component.getTileSet();
        int first = session.getTile();
        if (tileSet == null || first + RULE_TILES - 1 > tileSet.getFrameCount()) {
            say("A rule needs 16 tiles from the selected one, but the tile set ends sooner");
            return;
        }
        int[] tiles = new int[RULE_TILES];
        for (int i = 0; i < RULE_TILES; i++) {
            tiles[i] = first + i;
        }
        session.putRule(new AutoTileRule("auto" + first, tiles));
        session.setAutoRule("auto" + first);
        say("Rule auto" + first + " made; painting uses it now");
    }

    // ---- following the selection ----

    private TilemapComponent selectedTilemap() {
        Entity selected = editorState.getSelectedEntity();
        return selected == null ? null : selected.getComponent(TilemapComponent.class);
    }

    private void bind(TilemapComponent next) {
        component = next;
        session = next == null ? null : new TilemapSession(next.getMap());
        builtRevision = -1;
        canvas.requestFit();
        if (next == null) {
            message.setText("Select an entity with a Tilemap component (Add Component > Tilemap) to paint its tiles here. "
                + "The tile set is a .sprite file whose frames are the tiles; make one in the Sprites tab.");
        }
    }

    private void refreshAtlas() {
        SpriteDocument tileSet = component == null ? null : component.getTileSet();
        if (tileSet != atlasSource) {
            if (atlas != null) {
                atlas.dispose();
                atlas = null;
            }
            atlasSource = tileSet;
            if (tileSet != null) {
                atlas = new FrameAtlas(tileSet);
            }
            builtRevision = -1;
        }
    }

    private void refreshToggles() {
        for (TextButton b : toolButtons) {
            b.setColor(skin.getColor(session != null && b.getUserObject() == session.getTool() ? "accent" : "text-primary"));
        }
        if (paintInViewButton != null) {
            paintInViewButton.setColor(skin.getColor(paintInView ? "accent" : "text-primary"));
        }
        if (collidesButton != null && component != null) {
            collidesButton.setColor(skin.getColor(component.isCollides() ? "accent" : "text-primary"));
        }
        if (solidButton != null && session != null) {
            solidButton.setColor(skin.getColor(session.getMap().isSolid(session.getTile()) ? "accent" : "text-primary"));
        }
    }

    private void rebuildLists() {
        paletteTable.clearChildren();
        layersTable.clearChildren();
        rulesTable.clearChildren();
        if (session == null) {
            return;
        }
        TileMapData map = session.getMap();
        if (atlas != null) {
            paletteTable.top().left();
            for (int f = 0; f < atlas.frames(); f++) {
                int tile = f + 1;
                Image picture = new Image(new TextureRegion(atlas.texture(), atlas.srcX(f), atlas.srcY(f), atlas.frameWidth(), atlas.frameHeight()));
                picture.setColor(session.getTile() == tile ? com.badlogic.gdx.graphics.Color.WHITE : new com.badlogic.gdx.graphics.Color(0.65f, 0.65f, 0.65f, 1f));
                picture.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        session.setTile(tile);
                    }
                });
                Table cell = new Table(skin);
                cell.add(picture).size(PALETTE_CELL);
                if (session.getTile() == tile) {
                    cell.setBackground("subheader-bg");
                }
                if (map.isSolid(tile)) {
                    cell.add(new Label("#", skin)).top().right();
                }
                paletteTable.add(cell).pad(1);
                if ((f + 1) % PALETTE_COLUMNS == 0) {
                    paletteTable.row();
                }
            }
        } else {
            String reason = component.getLoadError() != null ? component.getLoadError() : "No tile set yet: assign a .sprite in Details";
            Label why = new Label(reason, skin, "muted");
            why.setWrap(true);
            paletteTable.add(why).width(PALETTE_WIDTH - 8f);
        }
        for (int l = map.getLayerCount() - 1; l >= 0; l--) {
            int layer = l;
            Table row = new Table(skin);
            row.add(button(map.getLayer(l).isVisible() ? "On" : "Off", () -> session.setLayerVisible(layer, !map.getLayer(layer).isVisible())))
                .width(34f).height(ROW_HEIGHT).padRight(2);
            TextButton name = button(map.getLayer(l).getName(), () -> session.setLayer(layer));
            name.setColor(skin.getColor(l == session.getLayer() ? "accent" : "text-primary"));
            row.add(name).growX().height(ROW_HEIGHT);
            layersTable.add(row).growX().padBottom(1).row();
        }
        TextButton none = button("None", () -> session.setAutoRule(null));
        none.setColor(skin.getColor(session.getAutoRule() == null ? "accent" : "text-primary"));
        rulesTable.add(none).left().height(ROW_HEIGHT).padRight(2);
        for (AutoTileRule rule : map.getRules()) {
            TextButton b = button(rule.name(), () -> session.setAutoRule(rule.name()));
            b.setColor(skin.getColor(rule.name().equals(session.getAutoRule()) ? "accent" : "text-primary"));
            rulesTable.add(b).left().height(ROW_HEIGHT).padRight(2);
        }
        colsField.setText(String.valueOf(map.getCols()));
        rowsField.setText(String.valueOf(map.getRows()));
        builtRevision = session.getRevision();
        builtTile = session.getTile();
        refreshToggles();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        TilemapComponent selected = selectedTilemap();
        if (selected != component || (component != null && session != null && component.getMap() != session.getMap())) {
            bind(selected);
        }
        boolean visible = component != null;
        content.setVisible(visible);
        message.setVisible(!visible);
        if (!visible) {
            return;
        }
        AssetService assets = editorState.getAssetService();
        if (component.needsLoad() && assets != null) {
            component.load(assets.resolve(component.getTileSetReference()));
        }
        refreshAtlas();
        if (builtRevision != session.getRevision() || builtTile != session.getTile()) {
            rebuildLists();
        }
        int[] hover = canvas.getHover();
        hoverLabel.setText((hover != null ? hover[0] + ", " + hover[1] + "   " : "") + "layer " + (session.getLayer() + 1) + "   tile " + session.getTile()
            + (session.getMap().isSolid(session.getTile()) ? " (solid)" : ""));
    }

    @Override
    public void dispose() {
        canvas.dispose();
        if (atlas != null) {
            atlas.dispose();
            atlas = null;
        }
    }
}
