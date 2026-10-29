package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.theme.EditorThemePalette;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Creates clean, Material Design 3-inspired scene2d.ui Skins.
 * Builds both Dark and Light skins parametrically using semantic {@link EditorThemePalette} tokens.
 */
public final class EditorSkinFactory implements Disposable {
    private final java.util.List<Disposable> ownedResources = new ArrayList<>();

    private static final String REGULAR_FONT = "fonts/Inter-Regular.ttf";
    private static final String SEMIBOLD_FONT = "fonts/Inter-SemiBold.ttf";
    /** UI text size in logical pixels. */
    private static final int BASE_FONT_SIZE = 14;
    /** Icon edge length in logical pixels. */
    private static final int ICON_SIZE = 12;
    /** Typographic characters used in editor text beyond FreeType's default Latin-1 set. */
    private static final String EXTRA_CHARACTERS = "—–…•·←→↑↓";

    public Skin createSkin() {
        return createSkin(EditorThemePalette.createDarkPalette());
    }

    public Skin createSkin(EditorThemePalette palette) {
        Objects.requireNonNull(palette, "palette cannot be null");
        Skin skin = new Skin();

        // 1. Typography: Inter generated at the display's pixel density (TD-021)
        BitmapFont font = generateFont(REGULAR_FONT, BASE_FONT_SIZE);
        BitmapFont headingFont = generateFont(SEMIBOLD_FONT, BASE_FONT_SIZE);
        skin.add("default", font);
        skin.add("heading", headingFont);

        // 2. Semantic Color Registration
        skin.add("background", palette.getBackground());
        skin.add("surface", palette.getSurface());
        skin.add("surface-variant", palette.getSurfaceVariant());
        skin.add("surface-elevated", palette.getSurfaceElevated());
        skin.add("header", palette.getHeader());
        skin.add("subheader", palette.getSubheader());

        skin.add("text-primary", palette.getTextPrimary());
        skin.add("text-secondary", palette.getTextSecondary());
        skin.add("text-muted", palette.getTextMuted());
        skin.add("text-on-accent", palette.getTextOnAccent());

        skin.add("border", palette.getBorder());
        skin.add("border-muted", palette.getBorderMuted());
        skin.add("accent", palette.getPrimary());
        skin.add("accent-active", palette.getPrimaryActive());
        skin.add("play-tint", palette.getPlayTint());
        skin.add("success", palette.getSuccess());
        skin.add("warning", palette.getWarning());
        skin.add("error", palette.getError());
        skin.add("disabled", palette.getDisabled());

        // Backward compatibility color names
        skin.add("white", Color.WHITE);
        skin.add("black", Color.BLACK);
        skin.add("gray", palette.getTextMuted());
        skin.add("dark", palette.getBackground());
        skin.add("panel", palette.getSurface());

        // 3. Drawables
        Drawable bgDrawable = createColorDrawable(palette.getBackground());
        Drawable panelBg = createColorDrawable(palette.getSurface());
        Drawable headerBg = createColorDrawable(palette.getHeader());
        Drawable subheaderBg = createColorDrawable(palette.getSubheader());
        Drawable elevatedBg = createColorDrawable(palette.getSurfaceElevated());

        Drawable btnUp = createColorDrawable(palette.getSurfaceVariant());
        Drawable btnDown = createColorDrawable(palette.getPrimaryActive());
        Drawable btnOver = createColorDrawable(palette.getSurfaceElevated());
        Drawable btnChecked = createColorDrawable(palette.getPrimary());

        Drawable inputBg = createColorDrawable(palette.getBackground());
        Drawable cursor = createColorDrawable(palette.getCursor());
        Drawable selection = createColorDrawable(palette.getSelection());
        Drawable splitHandle = createColorDrawable(palette.getSplitHandle());

        Drawable tabActiveBg = createColorDrawable(palette.getSurface());
        Drawable tabInactiveBg = createColorDrawable(palette.getHeader());

        // Primary Accent Button Drawables
        Drawable btnPrimaryUp = createColorDrawable(palette.getPrimary());
        Drawable btnPrimaryOver = createColorDrawable(palette.getPrimaryActive());
        Drawable btnPrimaryDown = createColorDrawable(new Color(
            Math.max(0f, palette.getPrimary().r - 0.1f),
            Math.max(0f, palette.getPrimary().g - 0.1f),
            Math.max(0f, palette.getPrimary().b - 0.1f),
            1f
        ));

        skin.add("bg", bgDrawable, Drawable.class);
        skin.add("panel-bg", panelBg, Drawable.class);
        skin.add("header-bg", headerBg, Drawable.class);
        skin.add("subheader-bg", subheaderBg, Drawable.class);
        skin.add("elevated-bg", elevatedBg, Drawable.class);
        skin.add("split-handle", splitHandle, Drawable.class);
        skin.add("tab-active", tabActiveBg, Drawable.class);
        skin.add("tab-inactive", tabInactiveBg, Drawable.class);

        // 4. LabelStyles
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, palette.getTextPrimary());
        skin.add("default", labelStyle);

        Label.LabelStyle secondaryStyle = new Label.LabelStyle(font, palette.getTextSecondary());
        skin.add("secondary", secondaryStyle);

        Label.LabelStyle mutedStyle = new Label.LabelStyle(font, palette.getTextMuted());
        skin.add("muted", mutedStyle);

        Label.LabelStyle titleStyle = new Label.LabelStyle(headingFont, palette.getTextPrimary());
        titleStyle.background = headerBg;
        skin.add("title", titleStyle);

        Label.LabelStyle sectionStyle = new Label.LabelStyle(headingFont, palette.getPrimary());
        skin.add("section", sectionStyle);

        Label.LabelStyle errorStyle = new Label.LabelStyle(font, palette.getError());
        skin.add("error", errorStyle);

        Label.LabelStyle successStyle = new Label.LabelStyle(font, palette.getSuccess());
        skin.add("success", successStyle);

        Label.LabelStyle warningStyle = new Label.LabelStyle(font, palette.getWarning());
        skin.add("warning", warningStyle);

        // 5. TextButtonStyles
        // Default (Tonal / Surface-Variant)
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = palette.getTextPrimary();
        btnStyle.downFontColor = palette.getTextOnAccent();
        btnStyle.overFontColor = palette.getTextPrimary();
        btnStyle.disabledFontColor = palette.getDisabled();
        btnStyle.up = btnUp;
        btnStyle.down = btnDown;
        btnStyle.over = btnOver;
        btnStyle.checked = btnChecked;
        skin.add("default", btnStyle);

        // Primary Accent Button (Filled)
        TextButton.TextButtonStyle primaryBtnStyle = new TextButton.TextButtonStyle();
        primaryBtnStyle.font = font;
        primaryBtnStyle.fontColor = palette.getTextOnAccent();
        primaryBtnStyle.downFontColor = palette.getTextOnAccent();
        primaryBtnStyle.overFontColor = palette.getTextOnAccent();
        primaryBtnStyle.disabledFontColor = palette.getDisabled();
        primaryBtnStyle.up = btnPrimaryUp;
        primaryBtnStyle.over = btnPrimaryOver;
        primaryBtnStyle.down = btnPrimaryDown;
        primaryBtnStyle.checked = btnPrimaryDown;
        skin.add("primary", primaryBtnStyle);

        // Active / Toggle button style
        TextButton.TextButtonStyle activeBtnStyle = new TextButton.TextButtonStyle();
        activeBtnStyle.font = font;
        activeBtnStyle.fontColor = palette.getTextOnAccent();
        activeBtnStyle.up = btnChecked;
        activeBtnStyle.down = btnDown;
        activeBtnStyle.over = btnOver;
        skin.add("active", activeBtnStyle);

        // Flat button style for clean toolbars and list items
        TextButton.TextButtonStyle flatBtnStyle = new TextButton.TextButtonStyle();
        flatBtnStyle.font = font;
        flatBtnStyle.fontColor = palette.getTextPrimary();
        flatBtnStyle.overFontColor = palette.getPrimary();
        flatBtnStyle.downFontColor = palette.getPrimaryActive();
        flatBtnStyle.disabledFontColor = palette.getDisabled();
        flatBtnStyle.up = null;
        flatBtnStyle.over = btnOver;
        flatBtnStyle.down = btnDown;
        flatBtnStyle.checked = btnChecked;
        skin.add("flat", flatBtnStyle);

        // Tab button style for Scene / Game / Workspace tabs
        TextButton.TextButtonStyle tabBtnStyle = new TextButton.TextButtonStyle();
        tabBtnStyle.font = font;
        tabBtnStyle.fontColor = palette.getTextSecondary();
        tabBtnStyle.overFontColor = palette.getTextPrimary();
        tabBtnStyle.downFontColor = palette.getTextOnAccent();
        tabBtnStyle.up = tabInactiveBg;
        tabBtnStyle.over = btnOver;
        tabBtnStyle.down = tabActiveBg;
        tabBtnStyle.checked = tabActiveBg;
        skin.add("tab", tabBtnStyle);

        // Play-control buttons: default button look with a drawn icon before the text
        Color iconColor = palette.getTextPrimary();
        skin.add("play", iconButtonStyle(btnStyle, createIconDrawable(EditorIcon.PLAY, iconColor)));
        skin.add("pause", iconButtonStyle(btnStyle, createIconDrawable(EditorIcon.PAUSE, iconColor)));
        skin.add("stop", iconButtonStyle(btnStyle, createIconDrawable(EditorIcon.STOP, iconColor)));

        // 6. TextFieldStyle
        TextField.TextFieldStyle txtStyle = new TextField.TextFieldStyle();
        txtStyle.font = font;
        txtStyle.fontColor = palette.getTextPrimary();
        txtStyle.messageFontColor = palette.getTextMuted();
        txtStyle.background = inputBg;
        txtStyle.cursor = cursor;
        txtStyle.selection = selection;
        skin.add("default", txtStyle);

        // 7. ScrollPaneStyle
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = panelBg;
        skin.add("default", scrollStyle);

        // 8. ListStyle
        List.ListStyle listStyle = new List.ListStyle();
        listStyle.font = font;
        listStyle.fontColorSelected = palette.getTextOnAccent();
        listStyle.fontColorUnselected = palette.getTextPrimary();
        listStyle.selection = selection;
        skin.add("default", listStyle);

        // 9. WindowStyle / DialogStyle
        Window.WindowStyle winStyle = new Window.WindowStyle();
        winStyle.titleFont = font;
        winStyle.titleFontColor = palette.getTextPrimary();
        winStyle.background = elevatedBg;
        skin.add("default", winStyle);

        // 10. SplitPaneStyle
        Drawable splitHandleH = createSplitHandleDrawable(palette.getSplitHandle(), 4, 1);
        Drawable splitHandleV = createSplitHandleDrawable(palette.getSplitHandle(), 1, 4);
        SplitPane.SplitPaneStyle splitStyleH = new SplitPane.SplitPaneStyle(splitHandleH);
        SplitPane.SplitPaneStyle splitStyleV = new SplitPane.SplitPaneStyle(splitHandleV);
        skin.add("default-horizontal", splitStyleH);
        skin.add("default-vertical", splitStyleV);
        skin.add("default", splitStyleH);

        return skin;
    }

    /** Icons drawn procedurally at the display density, so no image assets are needed. */
    enum EditorIcon {
        PLAY,
        PAUSE,
        STOP
    }

    private static ImageTextButton.ImageTextButtonStyle iconButtonStyle(TextButton.TextButtonStyle base, Drawable icon) {
        ImageTextButton.ImageTextButtonStyle style = new ImageTextButton.ImageTextButtonStyle(base);
        style.imageUp = icon;
        return style;
    }

    private Drawable createIconDrawable(EditorIcon icon, Color color) {
        float density = uiDensity();
        int size = Math.max(8, Math.round(ICON_SIZE * density));
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(color);
        int inset = Math.round(size * 0.15f);
        int far = size - 1 - inset;
        switch (icon) {
            case PLAY -> pixmap.fillTriangle(inset, inset, inset, far, far, size / 2);
            case PAUSE -> {
                int barWidth = Math.max(2, Math.round(size * 0.22f));
                pixmap.fillRectangle(inset, inset, barWidth, far - inset + 1);
                pixmap.fillRectangle(far - barWidth + 1, inset, barWidth, far - inset + 1);
            }
            case STOP -> pixmap.fillRectangle(inset, inset, far - inset + 1, far - inset + 1);
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        ownedResources.add(texture);
        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(texture));
        drawable.setMinSize(ICON_SIZE, ICON_SIZE);
        return drawable;
    }

    /**
     * Generates a font from a bundled TrueType file. The glyphs are rendered at the back-buffer resolution and
     * scaled back to UI units, so text stays sharp on HiDPI displays.
     *
     * @throws com.badlogic.gdx.utils.GdxRuntimeException if the font resource is missing
     */
    private BitmapFont generateFont(String classpathFile, int logicalSize) {
        float density = uiDensity();
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.classpath(classpathFile));
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = Math.round(logicalSize * density);
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + EXTRA_CHARACTERS;
            BitmapFont font = generator.generateFont(parameter);
            font.getData().setScale(1f / density);
            font.setUseIntegerPositions(false);
            ownedResources.add(font);
            return font;
        } finally {
            generator.dispose();
        }
    }

    /** Back-buffer pixels per UI unit (1 on standard displays, e.g. 1.5 or 2 on HiDPI). */
    static float uiDensity() {
        if (Gdx.graphics == null || Gdx.graphics.getWidth() <= 0) {
            return 1f;
        }
        return Math.max(1f, Gdx.graphics.getBackBufferWidth() / (float) Gdx.graphics.getWidth());
    }

    private Drawable createColorDrawable(Color color) {
        return createColorDrawable(color, 1, 1);
    }

    private Drawable createColorDrawable(Color color, int width, int height) {
        Pixmap pixmap = new Pixmap(Math.max(1, width), Math.max(1, height), Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        ownedResources.add(texture);
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    private Drawable createSplitHandleDrawable(Color color, int width, int height) {
        return createColorDrawable(color, width, height);
    }

    @Override
    public void dispose() {
        for (Disposable d : ownedResources) {
            try {
                d.dispose();
            } catch (Throwable ignored) {}
        }
        ownedResources.clear();
    }
}
