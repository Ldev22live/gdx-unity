package io.github.ldev22live.gdxunity.editor.desktop.ui.sprite;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteSession;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteToolController;
import io.github.ldev22live.gdxunity.engine.sprite.PixelPng;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteSheet;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteSlice;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteTag;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Sprites tab (TD-066, milestone C): a pixel-art editor in the style of LibreSprite. Tools on the left, the canvas in the middle, palette, layers and
 * a preview on the right, frames and tags at the bottom. All edits go through {@link SpriteSession}, so Ctrl+Z style undo works for every step.
 * Sprites are saved in the project's {@code sprites/} folder as {@code .sprite}; "Export" also writes a PNG sheet and its atlas next to it, which the
 * Content Browser lists as a texture a Sprite Renderer can use.
 */
public final class SpriteStudioPanel extends Table implements Disposable {
    /** Folder of the project that holds sprites. */
    public static final String SPRITES_FOLDER = "sprites";
    private static final int DEFAULT_SIZE = 32;
    private static final int MAX_NEW_SIZE = 512;
    private static final float TOOL_BUTTON_WIDTH = 74f;
    private static final float ROW_HEIGHT = 24f;
    private static final float SWATCH_SIZE = 18f;
    private static final int PALETTE_COLUMNS = 8;
    private static final float RIGHT_COLUMN_WIDTH = 230f;
    private static final float FRAME_BUTTON_WIDTH = 38f;
    private static final String ATLAS_SUFFIX = ".sheet.json";
    /** The starting palette: a small, well-spread set (the 16 colors of the PICO-8 console are public domain colors as plain RGB values). */
    private static final int[] STARTER_PALETTE = {
        0x000000FF, 0x1D2B53FF, 0x7E2553FF, 0x008751FF, 0xAB5236FF, 0x5F574FFF, 0xC2C3C7FF, 0xFFF1E8FF,
        0xFF004DFF, 0xFFA300FF, 0xFFEC27FF, 0x00E436FF, 0x29ADFFFF, 0x83769CFF, 0xFF77A8FF, 0xFFCCAAFF};

    private final Skin skin;
    private final File projectRoot;
    private final Runnable onAssetsChanged;
    private final Texture white;
    private final ContextMenu menu;
    private SpriteSession session;
    private SpriteToolController tools;
    private SpriteCanvasWidget canvas;
    private SpritePreviewWidget preview;
    private TextField nameField;
    private TextField colorField;
    private TextField widthField;
    private TextField heightField;
    private Label statusLabel;
    private Label hoverLabel;
    private Table layersTable;
    private Table paletteTable;
    private Table framesTable;
    private Table tagsTable;
    private Table slicesTable;
    /** What was typed in the slice fields; kept across the rebuilds of the lists. */
    private String sliceName = "";
    private String sliceBorders = "0 0 0 0";
    private ColorSwatch currentSwatch;
    private final List<TextButton> toolButtons = new ArrayList<>();
    private final List<TextButton> optionButtons = new ArrayList<>();
    private long builtRevision = -1;
    private String lastColorText = "";

    /**
     * @param projectRoot     the project's assets folder; sprites live in its {@code sprites/} folder
     * @param onAssetsChanged called after a file was written, so the Content Browser lists it
     */
    public SpriteStudioPanel(Skin skin, File projectRoot, Runnable onAssetsChanged) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        this.onAssetsChanged = Objects.requireNonNull(onAssetsChanged, "onAssetsChanged cannot be null");
        Pixmap one = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        one.setColor(Color.WHITE);
        one.fill();
        white = new Texture(one);
        one.dispose();
        menu = new ContextMenu(skin);
        SpriteDocument starter = new SpriteDocument(DEFAULT_SIZE, DEFAULT_SIZE);
        starter.setPalette(STARTER_PALETTE);
        useSession(new SpriteSession(starter), "sprite");
    }

    /**
     * Testing aid (TD-054, environment variable GDXUNITY_EDITOR_SPRITE_DEMO): draws a small face with the real tools on a second layer, adds a second
     * frame with the eyes closed and turns onion skin on, so a screenshot shows the canvas, layers and frames at work.
     */
    public void drawDemo() {
        session.setColor(0xFFCCAAFF);
        session.drawEllipse(4, 4, 27, 27, true);
        session.setColor(0x000000FF);
        session.drawEllipse(4, 4, 27, 27, false);
        session.addLayer("Eyes");
        session.setColor(0x29ADFFFF);
        session.drawRect(10, 12, 12, 15, true);
        session.drawRect(19, 12, 21, 15, true);
        session.setColor(0xFF004DFF);
        session.drawLine(10, 21, 14, 23);
        session.drawLine(14, 23, 17, 23);
        session.drawLine(17, 23, 21, 21);
        session.duplicateFrame();
        session.setColor(0x000000FF);
        session.drawLine(10, 14, 12, 14);
        session.drawLine(19, 14, 21, 14);
        session.setOnionSkin(true);
        session.addPaletteColor(0xFFCCAAFF);
        session.addTag("blink", 0, 1, SpriteTag.Direction.PING_PONG);
    }

    /** The session being edited. */
    public SpriteSession getSession() {
        return session;
    }

    /** Whether the open sprite has changes that were not saved. */
    public boolean isDirty() {
        return session.isDirty();
    }

    private File spritesDir() {
        return new File(projectRoot, SPRITES_FOLDER);
    }

    private static String safeName(String text) {
        String cleaned = text == null ? "" : text.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
        return cleaned.isEmpty() ? "sprite" : cleaned;
    }

    // ---- sessions ----

    private void useSession(SpriteSession next, String name) {
        if (canvas != null) {
            canvas.dispose();
            preview.dispose();
        }
        session = next;
        tools = new SpriteToolController(session);
        canvas = new SpriteCanvasWidget(session, tools);
        preview = new SpritePreviewWidget(session);
        nameField = new TextField(name, skin);
        colorField = new TextField("", skin);
        build();
        canvas.requestFit();
        builtRevision = -1;
    }

    /** Opens a {@code .sprite} file of the project (a path relative to the project root, or absolute). */
    public boolean open(File file) {
        try {
            SpriteSession opened = SpriteSession.open(file);
            String name = file.getName().replaceFirst("\\.sprite$", "");
            useSession(opened, name);
            say("Opened " + file.getName());
            return true;
        } catch (IOException | RuntimeException e) {
            say("Cannot open " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void newSprite() {
        int w = parseSize(widthField.getText());
        int h = parseSize(heightField.getText());
        if (w < 1 || h < 1) {
            say("Width and height must be whole numbers from 1 to " + MAX_NEW_SIZE);
            return;
        }
        SpriteDocument doc = new SpriteDocument(w, h);
        doc.setPalette(STARTER_PALETTE);
        useSession(new SpriteSession(doc), "sprite");
        say("New " + w + " x " + h + " sprite");
    }

    private static int parseSize(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 1 && value <= MAX_NEW_SIZE ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private File fileFor(String extension) {
        return new File(spritesDir(), safeName(nameField.getText()) + extension);
    }

    /** Writes the sprite to {@code sprites/<name>.sprite}; returns whether it worked. */
    public boolean save() {
        try {
            Files.createDirectories(spritesDir().toPath());
            session.saveAs(fileFor("." + "sprite"));
            onAssetsChanged.run();
            say("Saved " + session.getFile().getName());
            return true;
        } catch (IOException e) {
            say("Cannot save: " + e.getMessage());
            return false;
        }
    }

    private void export() {
        try {
            Files.createDirectories(spritesDir().toPath());
            String base = safeName(nameField.getText());
            SpriteSheet.Export sheet = SpriteSheet.export(session.getDocument(), 0, 0, base + ".png");
            Files.write(new File(spritesDir(), base + ".png").toPath(), sheet.png());
            Files.writeString(new File(spritesDir(), base + ATLAS_SUFFIX).toPath(), sheet.atlasJson(), StandardCharsets.UTF_8);
            onAssetsChanged.run();
            say("Exported " + base + ".png (" + sheet.width() + " x " + sheet.height() + ") and " + base + ATLAS_SUFFIX);
        } catch (IOException e) {
            say("Cannot export: " + e.getMessage());
        }
    }

    private void importSheet() {
        File png = fileFor(".png");
        if (!png.isFile()) {
            say("Put a PNG named " + png.getName() + " in the sprites folder, then press Import Sheet; it is cut into frames of the current canvas size");
            return;
        }
        try {
            PixelPng.Image image = PixelPng.decode(Files.readAllBytes(png.toPath()));
            SpriteDocument cut = SpriteSheet.importSheet(image, Math.min(session.getDocument().getWidth(), image.width()),
                Math.min(session.getDocument().getHeight(), image.height()), 0);
            cut.setPalette(session.getDocument().getPalette());
            useSession(new SpriteSession(cut), nameField.getText());
            say("Imported " + cut.getFrameCount() + " frame(s) from " + png.getName());
        } catch (IOException | RuntimeException e) {
            say("Cannot import " + png.getName() + ": " + e.getMessage());
        }
    }

    private void showOpenMenu(float stageX, float stageY) {
        File[] files = spritesDir().listFiles((dir, n) -> n.endsWith(".sprite"));
        List<ContextMenu.Item> items = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                items.add(new ContextMenu.Item(f.getName(), null, true, () -> open(f)));
            }
        }
        if (items.isEmpty()) {
            items.add(new ContextMenu.Item("No sprites in " + SPRITES_FOLDER + "/ yet", null, false, () -> { }));
        }
        menu.show(getStage(), stageX, stageY, items);
    }

    private void say(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text);
        }
    }

    // ---- building the UI ----

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

    private void build() {
        clearChildren();
        toolButtons.clear();
        optionButtons.clear();
        statusLabel = new Label("", skin, "muted");
        hoverLabel = new Label("", skin, "muted");
        widthField = new TextField(String.valueOf(session.getDocument().getWidth()), skin);
        heightField = new TextField(String.valueOf(session.getDocument().getHeight()), skin);
        layersTable = new Table(skin);
        paletteTable = new Table(skin);
        framesTable = new Table(skin);
        tagsTable = new Table(skin);
        slicesTable = new Table(skin);
        currentSwatch = new ColorSwatch(white, session.getColor(), SWATCH_SIZE * 2);

        add(topBar()).growX().colspan(3).height(ROW_HEIGHT + 6).row();
        add(toolColumn()).top().left().padRight(4);
        add(canvas).grow().minSize(0, 0);
        add(rightColumn()).top().width(RIGHT_COLUMN_WIDTH).padLeft(4).row();
        add(bottomBar()).growX().colspan(3).padTop(4).row();
        Table status = new Table(skin);
        status.add(statusLabel).left().expandX();
        status.add(hoverLabel).right();
        add(status).growX().colspan(3).height(ROW_HEIGHT);
        rebuildLists();
    }

    private Table topBar() {
        Table bar = new Table(skin);
        bar.left();
        bar.add(new Label("Name", skin, "muted")).padRight(4);
        bar.add(nameField).width(130f).height(ROW_HEIGHT).padRight(6);
        bar.add(button("Save", this::save)).height(ROW_HEIGHT).padRight(2);
        TextButton openButton = button("Open...", () -> { });
        openButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showOpenMenu(event.getStageX(), event.getStageY());
            }
        });
        bar.add(openButton).height(ROW_HEIGHT).padRight(2);
        bar.add(button("Export PNG", this::export)).height(ROW_HEIGHT).padRight(2);
        bar.add(button("Import Sheet", this::importSheet)).height(ROW_HEIGHT).padRight(10);
        bar.add(new Label("New", skin, "muted")).padRight(4);
        bar.add(widthField).width(44f).height(ROW_HEIGHT);
        bar.add(new Label("x", skin, "muted")).padLeft(3).padRight(3);
        bar.add(heightField).width(44f).height(ROW_HEIGHT).padRight(4);
        bar.add(button("Create", this::newSprite)).height(ROW_HEIGHT).padRight(10);
        bar.add(button("Undo", () -> session.getHistory().undo())).height(ROW_HEIGHT).padRight(2);
        bar.add(button("Redo", () -> session.getHistory().redo())).height(ROW_HEIGHT).padRight(10);
        bar.add(button("Fit", canvas::fitToWidget)).height(ROW_HEIGHT);
        return bar;
    }

    private Table toolColumn() {
        Table column = new Table(skin);
        column.top().left();
        for (SpriteToolController.Tool tool : SpriteToolController.Tool.values()) {
            TextButton b = button(label(tool), () -> {
                tools.setTool(tool);
                refreshToggles();
            });
            b.setUserObject(tool);
            toolButtons.add(b);
            column.add(b).width(TOOL_BUTTON_WIDTH).height(ROW_HEIGHT).padBottom(2).row();
        }
        column.add(new Label("Options", skin, "section")).left().padTop(8).padBottom(2).row();
        addOption(column, "Filled", () -> tools.setFilledShapes(!tools.isFilledShapes()), tools::isFilledShapes);
        addOption(column, "Mirror X", () -> session.setMirrorX(!session.isMirrorX()), session::isMirrorX);
        addOption(column, "Mirror Y", () -> session.setMirrorY(!session.isMirrorY()), session::isMirrorY);
        addOption(column, "Tile", () -> session.setTileMode(!session.isTileMode()), session::isTileMode);
        addOption(column, "Pix.Perfect", () -> session.setPixelPerfect(!session.isPixelPerfect()), session::isPixelPerfect);
        addOption(column, "Onion skin", () -> session.setOnionSkin(!session.isOnionSkin()), session::isOnionSkin);
        column.add(new Label("Brush", skin, "muted")).left().padTop(6).row();
        Table brush = new Table(skin);
        brush.add(button("-", () -> session.setBrush(Math.max(1, session.getBrush() - 1)))).width(30f).height(ROW_HEIGHT);
        Label size = new Label("", skin) {
            @Override
            public void act(float delta) {
                super.act(delta);
                setText(String.valueOf(session.getBrush()));
            }
        };
        brush.add(size).width(14f).center();
        brush.add(button("+", () -> session.setBrush(Math.min(SpriteSession.MAX_BRUSH, session.getBrush() + 1)))).width(30f).height(ROW_HEIGHT);
        column.add(brush).left().row();
        column.add(new Label("Selection", skin, "muted")).left().padTop(6).row();
        column.add(button("Delete", this::selectionAction)).width(TOOL_BUTTON_WIDTH).height(ROW_HEIGHT).padBottom(2).row();
        column.add(button("Flip H", () -> withSelection(() -> session.flipSelection(true)))).width(TOOL_BUTTON_WIDTH).height(ROW_HEIGHT).padBottom(2).row();
        column.add(button("Flip V", () -> withSelection(() -> session.flipSelection(false)))).width(TOOL_BUTTON_WIDTH).height(ROW_HEIGHT).padBottom(2).row();
        column.add(button("Rotate", () -> withSelection(session::rotateSelection))).width(TOOL_BUTTON_WIDTH).height(ROW_HEIGHT).row();
        refreshToggles();
        return column;
    }

    private void selectionAction() {
        withSelection(session::deleteSelection);
    }

    /** Runs a selection edit, telling the user when there is no selection or it cannot be done instead of failing silently. */
    private void withSelection(Runnable edit) {
        try {
            edit.run();
        } catch (IllegalStateException e) {
            say(e.getMessage());
        }
    }

    private void addOption(Table column, String text, Runnable toggle, java.util.function.BooleanSupplier on) {
        TextButton b = button(text, () -> {
            toggle.run();
            refreshToggles();
        });
        b.setUserObject(on);
        optionButtons.add(b);
        column.add(b).width(TOOL_BUTTON_WIDTH).height(ROW_HEIGHT).padBottom(2).row();
    }

    private static String label(SpriteToolController.Tool tool) {
        String name = tool.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private void refreshToggles() {
        for (TextButton b : toolButtons) {
            b.setColor(skin.getColor(b.getUserObject() == tools.getTool() ? "accent" : "text-primary"));
        }
        for (TextButton b : optionButtons) {
            boolean on = ((java.util.function.BooleanSupplier) b.getUserObject()).getAsBoolean();
            b.setColor(skin.getColor(on ? "accent" : "text-primary"));
        }
    }

    private Table rightColumn() {
        Table column = new Table(skin);
        column.top().left();
        column.add(new Label("Color", skin, "section")).left().padBottom(2).row();
        Table color = new Table(skin);
        color.add(currentSwatch).size(SWATCH_SIZE * 2).padRight(6);
        color.add(colorField).width(90f).height(ROW_HEIGHT).padRight(4);
        color.add(button("Add", () -> session.addPaletteColor(session.getColor()))).height(ROW_HEIGHT);
        column.add(color).left().padBottom(4).row();
        column.add(paletteTable).left().padBottom(8).row();
        Table layersHeader = new Table(skin);
        layersHeader.add(new Label("Layers", skin, "section")).left().expandX();
        layersHeader.add(button("+", () -> session.addLayer("Layer " + (session.getDocument().getLayerCount() + 1)))).width(28f).height(ROW_HEIGHT);
        layersHeader.add(button("-", session::removeLayer)).width(28f).height(ROW_HEIGHT).padLeft(2);
        column.add(layersHeader).growX().row();
        column.add(layersTable).growX().padBottom(8).row();
        column.add(new Label("Preview", skin, "section")).left().row();
        Table previewRow = new Table(skin);
        previewRow.add(preview).size(96f).padRight(6);
        TextButton play = button("Play", () -> { });
        play.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                preview.setPlaying(!preview.isPlaying());
                play.setText(preview.isPlaying() ? "Stop" : "Play");
            }
        });
        previewRow.add(play).width(54f).height(ROW_HEIGHT).top();
        column.add(previewRow).left().row();
        return column;
    }

    private Table bottomBar() {
        Table bar = new Table(skin);
        bar.left();
        Table controls = new Table(skin);
        controls.left();
        controls.add(new Label("Frames", skin, "section")).padRight(6);
        controls.add(button("New", session::addFrame)).height(ROW_HEIGHT).padRight(2);
        controls.add(button("Copy", session::duplicateFrame)).height(ROW_HEIGHT).padRight(2);
        controls.add(button("Delete", session::removeFrame)).height(ROW_HEIGHT).padRight(8);
        controls.add(button("<", () -> moveCurrentFrame(-1))).width(28f).height(ROW_HEIGHT).padRight(2);
        controls.add(button(">", () -> moveCurrentFrame(1))).width(28f).height(ROW_HEIGHT).padRight(8);
        controls.add(new Label("ms", skin, "muted")).padRight(3);
        TextField duration = new TextField("", skin) {
            private int shown = -1;
            private int shownFrame = -1;

            @Override
            public void act(float delta) {
                super.act(delta);
                int frame = session.getFrame();
                int millis = session.getDocument().getFrameDuration(frame);
                if (!hasKeyboardFocus() && (shown != millis || shownFrame != frame)) {
                    setText(String.valueOf(millis));
                    shown = millis;
                    shownFrame = frame;
                }
            }
        };
        duration.setTextFieldListener((field, c) -> {
            if (c == '\r' || c == '\n') {
                try {
                    session.setFrameDuration(session.getFrame(), Integer.parseInt(field.getText().trim()));
                } catch (IllegalArgumentException e) {
                    say("A frame duration is a whole number of milliseconds, at least 1");
                }
            }
        });
        controls.add(duration).width(54f).height(ROW_HEIGHT);
        bar.add(controls).left().row();
        ScrollPane frames = new ScrollPane(framesTable, skin);
        frames.setScrollingDisabled(false, true);
        bar.add(frames).growX().height(ROW_HEIGHT + 20f).row();
        bar.add(tagsTable).left().row();
        bar.add(slicesTable).left().row();
        return bar;
    }

    private void moveCurrentFrame(int delta) {
        int to = session.getFrame() + delta;
        if (to >= 0 && to < session.getDocument().getFrameCount()) {
            session.moveFrame(session.getFrame(), to);
        }
    }

    // ---- lists that follow the document ----

    private void rebuildLists() {
        SpriteDocument doc = session.getDocument();
        layersTable.clearChildren();
        for (int l = doc.getLayerCount() - 1; l >= 0; l--) {
            int layer = l;
            Table row = new Table(skin);
            TextButton eye = button(doc.getLayer(l).visible() ? "On" : "Off", () -> session.setLayerVisible(layer, !doc.getLayer(layer).visible()));
            row.add(eye).width(34f).height(ROW_HEIGHT).padRight(2);
            TextButton name = button(doc.getLayer(l).name(), () -> session.setLayer(layer));
            name.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
            name.setColor(skin.getColor(l == session.getLayer() ? "accent" : "text-primary"));
            row.add(name).growX().height(ROW_HEIGHT).padRight(2);
            row.add(button("^", () -> moveLayer(layer, layer + 1))).width(24f).height(ROW_HEIGHT);
            row.add(button("v", () -> moveLayer(layer, layer - 1))).width(24f).height(ROW_HEIGHT);
            layersTable.add(row).growX().padBottom(1).row();
        }
        paletteTable.clearChildren();
        int[] palette = doc.getPalette();
        for (int i = 0; i < palette.length; i++) {
            int color = palette[i];
            ColorSwatch swatch = new ColorSwatch(white, color, SWATCH_SIZE);
            swatch.setMarked(color == session.getColor());
            swatch.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    session.setColor(color);
                    builtRevision = -1;
                }
            });
            paletteTable.add(swatch).size(SWATCH_SIZE).pad(1);
            if ((i + 1) % PALETTE_COLUMNS == 0) {
                paletteTable.row();
            }
        }
        framesTable.clearChildren();
        for (int f = 0; f < doc.getFrameCount(); f++) {
            int frame = f;
            TextButton b = button(String.valueOf(f + 1), () -> session.setFrame(frame));
            b.setColor(skin.getColor(f == session.getFrame() ? "accent" : "text-primary"));
            framesTable.add(b).width(FRAME_BUTTON_WIDTH).height(ROW_HEIGHT).padRight(2);
        }
        tagsTable.clearChildren();
        tagsTable.add(new Label("Tags", skin, "section")).padRight(6);
        for (SpriteTag tag : doc.getTags()) {
            TextButton b = button(tag.name() + " " + (tag.from() + 1) + "-" + (tag.to() + 1), () -> preview.setTag(tag.name()));
            tagsTable.add(b).height(ROW_HEIGHT).padRight(2);
            tagsTable.add(button("x", () -> session.removeTag(tag.name()))).width(22f).height(ROW_HEIGHT).padRight(6);
        }
        TextField tagName = new TextField("", skin);
        tagName.setMessageText("tag name");
        tagsTable.add(tagName).width(90f).height(ROW_HEIGHT).padRight(2);
        tagsTable.add(button("Tag frames " + (session.getFrame() + 1) + "-" + doc.getFrameCount(), () -> addTag(tagName.getText()))).height(ROW_HEIGHT).padRight(2);
        tagsTable.add(button("All frames", () -> preview.setTag(null))).height(ROW_HEIGHT);
        rebuildSlices(doc);
        builtRevision = session.getRevision();
    }

    /**
     * Slices: a region of the canvas with optional 9-slice borders and a pivot. "Slice from selection" takes the rectangle the Select tool made (or the whole
     * canvas); the four numbers are the left, right, top and bottom borders in pixels. A Sprite Animator draws a bordered slice stretched (its 9-slice field).
     */
    private void rebuildSlices(SpriteDocument doc) {
        slicesTable.clearChildren();
        slicesTable.add(new Label("Slices", skin, "section")).padRight(6);
        for (SpriteSlice slice : doc.getSlices()) {
            String borders = slice.hasNineSlice() ? " 9-slice " + slice.left() + "," + slice.right() + "," + slice.top() + "," + slice.bottom() : "";
            slicesTable.add(new Label(slice.name() + " " + slice.width() + "x" + slice.height() + borders, skin, "muted")).padRight(2);
            slicesTable.add(button("x", () -> session.removeSlice(slice.name()))).width(22f).height(ROW_HEIGHT).padRight(6);
        }
        TextField name = new TextField(sliceName, skin);
        name.setMessageText("slice name");
        name.setTextFieldListener((field, c) -> sliceName = field.getText());
        TextField borders = new TextField(sliceBorders, skin);
        borders.setMessageText("L R T B");
        borders.setTextFieldListener((field, c) -> sliceBorders = field.getText());
        slicesTable.add(name).width(90f).height(ROW_HEIGHT).padRight(2);
        slicesTable.add(borders).width(80f).height(ROW_HEIGHT).padRight(2);
        slicesTable.add(button("Slice from selection", () -> addSliceFromSelection(name.getText(), borders.getText()))).height(ROW_HEIGHT);
    }

    private void addSliceFromSelection(String name, String borderText) {
        sliceName = name;
        sliceBorders = borderText;
        if (name == null || name.isBlank()) {
            say("Give the slice a name first");
            return;
        }
        String[] parts = borderText.trim().isEmpty() ? new String[0] : borderText.trim().split("[ ,]+");
        int[] b = new int[4];
        try {
            if (parts.length != 0 && parts.length != 4) {
                throw new NumberFormatException("four numbers: left, right, top, bottom");
            }
            for (int i = 0; i < parts.length; i++) {
                b[i] = Integer.parseInt(parts[i]);
            }
            SpriteSession.SelectionRect s = session.getSelection();
            SpriteDocument doc = session.getDocument();
            int x = s != null ? s.x() : 0;
            int y = s != null ? s.y() : 0;
            int w = s != null ? s.width() : doc.getWidth();
            int h = s != null ? s.height() : doc.getHeight();
            session.addSlice(new SpriteSlice(name.trim(), x, y, w, h, b[0], b[1], b[2], b[3], 0, 0));
            say("Slice " + name.trim() + " added (" + w + " x " + h + ")");
        } catch (IllegalArgumentException e) {
            say("The borders are " + e.getMessage() + "; they must fit inside the slice");
        }
    }

    private void moveLayer(int from, int to) {
        if (to >= 0 && to < session.getDocument().getLayerCount()) {
            session.moveLayer(from, to);
        }
    }

    private void addTag(String name) {
        if (name == null || name.isBlank()) {
            say("Give the tag a name first");
            return;
        }
        try {
            session.addTag(name.trim(), session.getFrame(), session.getDocument().getFrameCount() - 1, SpriteTag.Direction.FORWARD);
        } catch (IllegalArgumentException e) {
            say(e.getMessage());
        }
    }

    // ---- per-frame upkeep ----

    @Override
    public void act(float delta) {
        super.act(delta);
        if (builtRevision != session.getRevision()) {
            rebuildLists();
            refreshToggles();
        }
        currentSwatch.setColorRgba(session.getColor());
        String shown = String.format(Locale.ROOT, "%08X", session.getColor());
        if (!colorField.hasKeyboardFocus() && !shown.equals(lastColorText)) {
            colorField.setText(shown);
            lastColorText = shown;
        } else if (colorField.hasKeyboardFocus() && !colorField.getText().equals(lastColorText)) {
            applyColorText(colorField.getText());
            lastColorText = colorField.getText();
        }
        int[] hover = canvas.getHover();
        hoverLabel.setText((hover != null ? hover[0] + ", " + hover[1] + "   " : "") + "zoom " + canvas.getZoom() + "x   frame "
            + (session.getFrame() + 1) + "/" + session.getDocument().getFrameCount() + "   " + (session.isDirty() ? "unsaved" : "saved"));
    }

    private void applyColorText(String text) {
        String hex = text.trim().replace("#", "");
        if (hex.length() == 6) {
            hex += "FF";
        }
        if (hex.length() != 8) {
            return;
        }
        try {
            session.setColor((int) Long.parseLong(hex, 16));
        } catch (NumberFormatException e) {
            // still typing; the field keeps what was typed
        }
    }

    @Override
    public void dispose() {
        canvas.dispose();
        preview.dispose();
        white.dispose();
    }
}
