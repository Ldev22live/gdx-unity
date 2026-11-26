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
import io.github.ldev22live.gdxunity.editor.sprite.SpriteExports;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteSession;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteToolController;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteVectorSources;
import io.github.ldev22live.gdxunity.editor.sprite.VectorSession;
import io.github.ldev22live.gdxunity.engine.sprite.PixelPng;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteHitbox;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteSheet;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteSlice;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteTag;
import io.github.ldev22live.gdxunity.engine.vector.Rgba;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath;
import io.github.ldev22live.gdxunity.engine.vector.VectorShape;

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
 *
 * <p>Edit 2D Asset (TD-104, roadmap MC.3): a layer drawn from a {@code .vector} drawing shows its <b>Vector source</b> section, where
 * the drawing's shapes are recoloured, their points and position changed, and every layer drawn from it redrawn after each change.
 * Save writes changed drawings before the sprite. The bottom bar edits the current frame's <b>hitboxes</b>, and the Selection tools
 * include a free <b>transform</b> (scale and any angle).
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
    private static final String ATLAS_SUFFIX = SpriteExports.ATLAS_SUFFIX;
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
    /** The open sprite's vector-drawn layers and their drawings (TD-104). */
    private SpriteVectorSources sources;
    /** The shape the Vector source section edits; null until a linked layer is shown. */
    private String selectedShape;
    private Table vectorTable;
    private Table hitboxTable;
    /** What was typed in the hitbox name and transform fields; kept across the rebuilds of the lists. */
    private String hitboxName = "hurt";
    private String transformText = "100 100 0";
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
        sources = new SpriteVectorSources(next, projectRoot.toPath());
        selectedShape = null;
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
            // Drawings first, so the sprite never names a drawing whose changes were not written (TD-104)
            List<File> drawings = sources.saveVectors();
            session.saveAs(fileFor("." + "sprite"));
            onAssetsChanged.run();
            say("Saved " + session.getFile().getName() + (drawings.isEmpty() ? ""
                : " and " + String.join(", ", drawings.stream().map(File::getName).toList())));
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
            // A saved sprite is recorded as the sheet's source (TD-104): the PNG is an export, edited through its source
            SpriteSheet.SourceRecord source = session.getFile() == null || session.isDirty() ? null
                : SpriteExports.recordOf(projectRoot.toPath(), session.getFile().toPath());
            SpriteSheet.Export sheet = SpriteSheet.export(session.getDocument(), 0, 0, base + ".png", source);
            Files.write(new File(spritesDir(), base + ".png").toPath(), sheet.png());
            Files.writeString(new File(spritesDir(), base + ATLAS_SUFFIX).toPath(), sheet.atlasJson(), StandardCharsets.UTF_8);
            onAssetsChanged.run();
            say("Exported " + base + ".png (" + sheet.width() + " x " + sheet.height() + ") and " + base + ATLAS_SUFFIX
                + (source == null ? "; save the sprite first to record it as the source" : " from " + source.path()));
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
            say("Imported " + cut.getFrameCount() + " frame(s) from " + png.getName() + ". "
                + SpriteExports.originOf(projectRoot.toPath(), png.toPath()).advice());
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
        vectorTable = new Table(skin);
        hitboxTable = new Table(skin);
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
        column.add(button("Rotate", () -> withSelection(session::rotateSelection))).width(TOOL_BUTTON_WIDTH).height(ROW_HEIGHT).padBottom(2).row();
        // Free transform (TD-104): scale X %, scale Y % and degrees clockwise, nearest neighbour so no new colours appear
        TextField transform = new TextField(transformText, skin);
        transform.setMessageText("X% Y% deg");
        transform.setTextFieldListener((field, c) -> transformText = field.getText());
        column.add(transform).width(TOOL_BUTTON_WIDTH).height(ROW_HEIGHT).padBottom(2).row();
        column.add(button("Transform", () -> transformSelection(transform.getText()))).width(TOOL_BUTTON_WIDTH).height(ROW_HEIGHT).row();
        refreshToggles();
        return column;
    }

    /** Applies "scale X %, scale Y %, degrees" to the selection as one undo step. */
    private void transformSelection(String text) {
        transformText = text;
        String[] parts = text.trim().split("[ ,]+");
        try {
            if (parts.length != 3) {
                throw new NumberFormatException();
            }
            float scaleX = Float.parseFloat(parts[0]) / 100f;
            float scaleY = Float.parseFloat(parts[1]) / 100f;
            float degrees = Float.parseFloat(parts[2]);
            withSelection(() -> session.transformSelection(scaleX, scaleY, degrees));
        } catch (NumberFormatException e) {
            say("Transform takes three numbers: scale X %, scale Y % and degrees, such as 200 200 0 or 100 100 45");
        } catch (IllegalArgumentException e) {
            say(e.getMessage());
        }
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
        column.add(vectorTable).growX().padBottom(8).row();
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
        bar.add(hitboxTable).left().row();
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
        rebuildHitboxes(doc);
        rebuildVector();
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

    /** The current frame's hitboxes (TD-104): named rectangles for gameplay, made from the Select tool's rectangle. */
    private void rebuildHitboxes(SpriteDocument doc) {
        hitboxTable.clearChildren();
        hitboxTable.add(new Label("Hitboxes", skin, "section")).padRight(6);
        for (SpriteHitbox box : doc.getHitboxes(session.getFrame())) {
            hitboxTable.add(new Label(box.name() + " " + box.x() + "," + box.y() + " " + box.width() + "x" + box.height(), skin, "muted"))
                .padRight(2);
            hitboxTable.add(button("x", () -> session.removeHitbox(box.name()))).width(22f).height(ROW_HEIGHT).padRight(6);
        }
        TextField name = new TextField(hitboxName, skin);
        name.setMessageText("hitbox name");
        name.setTextFieldListener((field, c) -> hitboxName = field.getText());
        hitboxTable.add(name).width(90f).height(ROW_HEIGHT).padRight(2);
        hitboxTable.add(button("Hitbox from selection (frame " + (session.getFrame() + 1) + ")", () -> addHitboxFromSelection(name.getText())))
            .height(ROW_HEIGHT);
    }

    private void addHitboxFromSelection(String name) {
        hitboxName = name;
        SpriteSession.SelectionRect s = session.getSelection();
        if (s == null) {
            say("Draw the hitbox's rectangle with the Select tool first");
            return;
        }
        try {
            session.setHitbox(new SpriteHitbox(name.trim(), s.x(), s.y(), s.width(), s.height()));
            say("Hitbox " + name.trim() + " on frame " + (session.getFrame() + 1) + " (" + s.width() + " x " + s.height() + ")");
        } catch (IllegalArgumentException e) {
            say(e.getMessage());
        }
    }

    // ---- vector source (Edit 2D Asset, TD-104) ----

    /** What a vector edit does to the drawing; returns whether it changed. */
    private interface VectorEdit {
        boolean apply(VectorSession drawing);
    }

    private Label wrapped(String text) {
        Label label = new Label(text, skin, "muted");
        label.setWrap(true);
        return label;
    }

    /**
     * The current layer's vector source: its drawing, the shapes drawn into it, and for the chosen shape its fill, stroke, points and
     * position. Every change redraws all layers drawn from the drawing.
     */
    private void rebuildVector() {
        vectorTable.clearChildren();
        vectorTable.add(new Label("Vector source", skin, "section")).left().row();
        SpriteVectorSources.Link link = sources.linkOf(session.getLayer());
        if (link == null) {
            vectorTable.add(wrapped("This layer is painted here; it has no vector source.")).width(RIGHT_COLUMN_WIDTH).left().row();
            return;
        }
        String path = link.source().vector();
        VectorSession drawing;
        try {
            drawing = sources.vector(path);
        } catch (IOException e) {
            vectorTable.add(wrapped(e.getMessage())).width(RIGHT_COLUMN_WIDTH).left().row();
            return;
        }
        vectorTable.add(new Label(path + (drawing.isDirty() ? " (unsaved)" : ""), skin, "muted")).left().row();
        try {
            if (sources.isPaintedOver(link.layer())) {
                vectorTable.add(wrapped("The layer differs from its drawing (painted by hand, or not redrawn). Redraw replaces it."))
                    .width(RIGHT_COLUMN_WIDTH).left().row();
            }
        } catch (IOException | IllegalArgumentException e) {
            vectorTable.add(wrapped(e.getMessage())).width(RIGHT_COLUMN_WIDTH).left().row();
            return;
        }
        List<String> shapes = link.source().shapes().isEmpty()
            ? drawing.getDocument().shapes().stream().map(VectorShape::name).toList() : link.source().shapes();
        if (shapes.isEmpty()) {
            return;
        }
        if (selectedShape == null || !shapes.contains(selectedShape)) {
            selectedShape = shapes.get(0);
        }
        Table names = new Table(skin);
        for (String shapeName : shapes) {
            TextButton b = button(shapeName, () -> {
                selectedShape = shapeName;
                builtRevision = -1;
            });
            b.setColor(skin.getColor(shapeName.equals(selectedShape) ? "accent" : "text-primary"));
            names.add(b).height(ROW_HEIGHT).padRight(2);
        }
        vectorTable.add(names).left().padBottom(2).row();
        VectorShape shape = drawing.getDocument().shape(selectedShape);
        String chosen = selectedShape;

        Table fillRow = new Table(skin);
        TextField fill = new TextField(shape.fill() == null ? "" : shape.fill().toHex(), skin);
        fill.setMessageText("none");
        fillRow.add(new Label("Fill", skin, "muted")).width(44f);
        fillRow.add(fill).width(90f).height(ROW_HEIGHT).padRight(2);
        fillRow.add(button("Set", () -> vectorEdit(path, d -> d.setFill(chosen, colour(fill.getText()))))).height(ROW_HEIGHT);
        vectorTable.add(fillRow).left().row();

        Table strokeRow = new Table(skin);
        TextField stroke = new TextField(shape.stroke() == null ? "" : shape.stroke().toHex(), skin);
        stroke.setMessageText("none");
        TextField width = new TextField(String.valueOf(shape.strokeWidth()), skin);
        strokeRow.add(new Label("Stroke", skin, "muted")).width(44f);
        strokeRow.add(stroke).width(90f).height(ROW_HEIGHT).padRight(2);
        strokeRow.add(width).width(36f).height(ROW_HEIGHT).padRight(2);
        strokeRow.add(button("Set", () -> vectorEdit(path, d -> d.setStroke(chosen, colour(stroke.getText()),
            Float.parseFloat(width.getText().trim()))))).height(ROW_HEIGHT);
        vectorTable.add(strokeRow).left().row();

        // Points in the shape's own coordinates; Enter moves one
        List<VectorPath.PointRef> refs = shape.path().points();
        for (int i = 0; i < refs.size(); i++) {
            VectorPath.PointRef ref = refs.get(i);
            float[] at = shape.path().point(ref);
            Table pointRow = new Table(skin);
            TextField field = new TextField(number(at[0]) + " " + number(at[1]), skin);
            field.setTextFieldListener((f, c) -> {
                if (c == '\r' || c == '\n') {
                    float[] xy = twoNumbers(f.getText());
                    if (xy != null) {
                        vectorEdit(path, d -> d.movePoint(chosen, ref, xy[0], xy[1]));
                    }
                }
            });
            pointRow.add(new Label("Point " + (i + 1), skin, "muted")).width(60f);
            pointRow.add(field).width(110f).height(ROW_HEIGHT);
            vectorTable.add(pointRow).left().row();
        }

        Table moveRow = new Table(skin);
        TextField delta = new TextField("0 0", skin);
        moveRow.add(new Label("Move by", skin, "muted")).width(60f);
        moveRow.add(delta).width(70f).height(ROW_HEIGHT).padRight(2);
        moveRow.add(button("Move", () -> {
            float[] xy = twoNumbers(delta.getText());
            if (xy != null) {
                vectorEdit(path, d -> d.moveShape(chosen, xy[0], xy[1]));
            }
        })).height(ROW_HEIGHT);
        vectorTable.add(moveRow).left().row();

        Table actions = new Table(skin);
        actions.add(button("Undo drawing", () -> vectorEdit(path, VectorSession::undo))).height(ROW_HEIGHT).padRight(2);
        actions.add(button("Redo", () -> vectorEdit(path, VectorSession::redo))).height(ROW_HEIGHT).padRight(2);
        actions.add(button("Redraw", () -> redraw(path))).height(ROW_HEIGHT);
        vectorTable.add(actions).left().padTop(2).row();
    }

    /** Runs a drawing edit, then redraws every layer drawn from it (one sprite step); problems are said, never thrown. */
    private void vectorEdit(String path, VectorEdit edit) {
        try {
            if (edit.apply(sources.vector(path))) {
                sources.redrawFrom(path);
                say("Changed " + path + "; redrawn");
            } else {
                say("Nothing changed");
            }
        } catch (IOException | IllegalArgumentException e) {
            say(e.getMessage());
        }
        builtRevision = -1;
    }

    private void redraw(String path) {
        try {
            say(sources.redrawFrom(path) ? "Redrawn from " + path : "The layers already match " + path);
        } catch (IOException | IllegalArgumentException e) {
            say(e.getMessage());
        }
        builtRevision = -1;
    }

    /** A colour typed as #rrggbb or #rrggbbaa, or none for an empty field. */
    private static Rgba colour(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : Rgba.hex(trimmed.startsWith("#") ? trimmed : "#" + trimmed);
    }

    private float[] twoNumbers(String text) {
        String[] parts = text.trim().split("[ ,]+");
        try {
            if (parts.length == 2) {
                return new float[] {Float.parseFloat(parts[0]), Float.parseFloat(parts[1])};
            }
        } catch (NumberFormatException e) {
            // said below
        }
        say("Type two numbers, x and y, such as 12 30");
        return null;
    }

    private static String number(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
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
