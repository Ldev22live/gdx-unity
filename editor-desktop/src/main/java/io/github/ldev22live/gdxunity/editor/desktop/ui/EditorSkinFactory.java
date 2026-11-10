package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
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
    /** Height of a slider's track, in pixels. */
    private static final int SLIDER_TRACK_HEIGHT = 4;
    /** Size of a slider's knob, in pixels. */
    private static final int SLIDER_KNOB_WIDTH = 8;
    private static final int SLIDER_KNOB_HEIGHT = 16;
    private final java.util.List<Disposable> ownedResources = new ArrayList<>();

    private static final String REGULAR_FONT = "fonts/Inter-Regular.ttf";
    private static final String SEMIBOLD_FONT = "fonts/Inter-SemiBold.ttf";
    /** UI text size in logical pixels. */
    private static final int BASE_FONT_SIZE = 14;
    /** Space between a drop-down's edge and its text, in pixels. */
    private static final float SELECT_BOX_TEXT_INSET = 6f;
    /** Icon edge length in logical pixels. */
    private static final int ICON_SIZE = 12;
    /** Size in UI units of the icons on the viewport tool strip. */
    private static final int TOOL_ICON_SIZE = 16;
    /** Prefix of the tool icon {@link ImageButton.ImageButtonStyle} names; the suffix is the lowercase {@link EditorIcon} name. */
    public static final String TOOL_STYLE_PREFIX = "tool-";

    /** Border, hover and selected fills of the top-bar buttons: the web editor's #3A3F4E, #343949 and #3B5B8F. */
    private static final Color BAR_BUTTON_BORDER = new Color(0.227f, 0.247f, 0.306f, 1f);
    private static final Color BAR_BUTTON_HOVER = new Color(0.204f, 0.224f, 0.286f, 1f);
    private static final Color BAR_BUTTON_SELECTED = new Color(0.231f, 0.357f, 0.561f, 1f);
    /** Space between a top-bar button's border and its text. */
    private static final float BAR_BUTTON_PAD_X = 10f;
    private static final float BAR_BUTTON_PAD_Y = 3f;
    /** Typographic characters used in editor text beyond FreeType's default Latin-1 set. */
    private static final String EXTRA_CHARACTERS = "—–…•·←→↑↓›";

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

        // Top-bar buttons, like the web editor's: a filled, bordered box; the selected one is the active blue
        TextButton.TextButtonStyle barBtnStyle = new TextButton.TextButtonStyle();
        barBtnStyle.font = font;
        barBtnStyle.fontColor = palette.getTextPrimary();
        barBtnStyle.overFontColor = palette.getTextPrimary();
        barBtnStyle.downFontColor = palette.getTextOnAccent();
        barBtnStyle.checkedFontColor = palette.getTextOnAccent();
        barBtnStyle.disabledFontColor = palette.getDisabled();
        barBtnStyle.up = createBorderedDrawable(palette.getSurfaceElevated(), BAR_BUTTON_BORDER);
        barBtnStyle.over = createBorderedDrawable(BAR_BUTTON_HOVER, BAR_BUTTON_BORDER);
        barBtnStyle.down = createBorderedDrawable(BAR_BUTTON_SELECTED, palette.getPrimary());
        barBtnStyle.checked = createBorderedDrawable(BAR_BUTTON_SELECTED, palette.getPrimary());
        skin.add("bar", barBtnStyle);

        // Play-control buttons: default button look with a drawn icon before the text
        Color iconColor = palette.getTextPrimary();
        skin.add("play", iconButtonStyle(btnStyle, createIconDrawable(EditorIcon.PLAY, iconColor)));
        skin.add("pause", iconButtonStyle(btnStyle, createIconDrawable(EditorIcon.PAUSE, iconColor)));
        skin.add("stop", iconButtonStyle(btnStyle, createIconDrawable(EditorIcon.STOP, iconColor)));

        // Tool-strip icon buttons: the icon is drawn white and tinted by the button colour (accent when active)
        for (EditorIcon icon : EditorIcon.values()) {
            if (icon.tool) {
                ImageButton.ImageButtonStyle toolStyle = new ImageButton.ImageButtonStyle();
                toolStyle.over = btnOver;
                toolStyle.down = btnDown;
                toolStyle.checked = btnChecked;
                toolStyle.imageUp = createIconDrawable(icon, Color.WHITE, TOOL_ICON_SIZE);
                skin.add(TOOL_STYLE_PREFIX + icon.name().toLowerCase(java.util.Locale.ROOT), toolStyle);
            }
        }

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

        // 8b. SelectBoxStyle (drop-downs in Details, the Graph tab and the Timeline)
        Drawable selectBg = createColorDrawable(palette.getBackground());
        selectBg.setLeftWidth(SELECT_BOX_TEXT_INSET);
        selectBg.setRightWidth(SELECT_BOX_TEXT_INSET);
        Drawable selectOverBg = createColorDrawable(palette.getSurfaceElevated());
        selectOverBg.setLeftWidth(SELECT_BOX_TEXT_INSET);
        selectOverBg.setRightWidth(SELECT_BOX_TEXT_INSET);
        ScrollPane.ScrollPaneStyle selectScrollStyle = new ScrollPane.ScrollPaneStyle();
        selectScrollStyle.background = elevatedBg;
        SelectBox.SelectBoxStyle selectStyle = new SelectBox.SelectBoxStyle(font, palette.getTextPrimary(), selectBg,
            selectScrollStyle, listStyle);
        selectStyle.backgroundOver = selectOverBg;
        selectStyle.disabledFontColor = palette.getDisabled();
        skin.add("default", selectStyle);

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

        // 11. SliderStyle (character creator, TD-056): a thin track, the part up to the knob in the accent color, a block knob
        Drawable sliderTrack = createColorDrawable(palette.getBorder(), 1, SLIDER_TRACK_HEIGHT);
        Drawable sliderFilled = createColorDrawable(palette.getPrimary(), 1, SLIDER_TRACK_HEIGHT);
        Drawable sliderKnob = createColorDrawable(palette.getTextPrimary(), SLIDER_KNOB_WIDTH, SLIDER_KNOB_HEIGHT);
        Drawable sliderKnobOver = createColorDrawable(palette.getPrimary(), SLIDER_KNOB_WIDTH, SLIDER_KNOB_HEIGHT);
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle(sliderTrack, sliderKnob);
        sliderStyle.knobBefore = sliderFilled;
        sliderStyle.knobOver = sliderKnobOver;
        sliderStyle.knobDown = sliderKnobOver;
        skin.add("default-horizontal", sliderStyle);

        return skin;
    }

    /** Icons drawn procedurally at the display density, so no image assets are needed. */
    enum EditorIcon {
        PLAY(false), PAUSE(false), STOP(false),
        SELECT(true), MOVE(true), ROTATE(true), SCALE(true), VIEW_MODE(true), FOCUS(true), GRID(true),
        PHYSICS(true), RESET_VIEW(true), SCULPT(true);

        /** True for the icons of the viewport tool strip. */
        final boolean tool;

        EditorIcon(boolean tool) {
            this.tool = tool;
        }
    }

    private static ImageTextButton.ImageTextButtonStyle iconButtonStyle(TextButton.TextButtonStyle base, Drawable icon) {
        ImageTextButton.ImageTextButtonStyle style = new ImageTextButton.ImageTextButtonStyle(base);
        style.imageUp = icon;
        return style;
    }

    /** Draws a tool-strip icon in {@code color}; shapes are laid out in fractions of the icon size. */
    private static void drawToolIcon(Pixmap pixmap, EditorIcon icon, int size, Color color) {
        int max = size - 1;
        int w = Math.max(1, Math.round(size * 0.12f));
        switch (icon) {
            case SELECT -> {
                triangle(pixmap, max, 0.22f, 0.08f, 0.22f, 0.85f, 0.78f, 0.58f);
                thickLine(pixmap, max, 0.5f, 0.6f, 0.8f, 0.92f, w);
            }
            case MOVE -> {
                thickLine(pixmap, max, 0.5f, 0.15f, 0.5f, 0.85f, w);
                thickLine(pixmap, max, 0.15f, 0.5f, 0.85f, 0.5f, w);
                triangle(pixmap, max, 0.5f, 0.0f, 0.32f, 0.22f, 0.68f, 0.22f);
                triangle(pixmap, max, 0.5f, 1.0f, 0.32f, 0.78f, 0.68f, 0.78f);
                triangle(pixmap, max, 0.0f, 0.5f, 0.22f, 0.32f, 0.22f, 0.68f);
                triangle(pixmap, max, 1.0f, 0.5f, 0.78f, 0.32f, 0.78f, 0.68f);
            }
            case ROTATE -> {
                ring(pixmap, size, 0.42f, w, color);
                triangle(pixmap, max, 0.95f, 0.18f, 0.62f, 0.12f, 0.88f, 0.48f);
            }
            case SCALE -> {
                box(pixmap, max, 0.08f, 0.4f, 0.6f, 0.92f, w);
                thickLine(pixmap, max, 0.4f, 0.6f, 0.82f, 0.18f, w);
                triangle(pixmap, max, 0.95f, 0.05f, 0.55f, 0.12f, 0.88f, 0.45f);
            }
            case VIEW_MODE -> {
                // A cube seen from a corner
                thickLine(pixmap, max, 0.5f, 0.05f, 0.9f, 0.27f, w);
                thickLine(pixmap, max, 0.9f, 0.27f, 0.9f, 0.73f, w);
                thickLine(pixmap, max, 0.9f, 0.73f, 0.5f, 0.95f, w);
                thickLine(pixmap, max, 0.5f, 0.95f, 0.1f, 0.73f, w);
                thickLine(pixmap, max, 0.1f, 0.73f, 0.1f, 0.27f, w);
                thickLine(pixmap, max, 0.1f, 0.27f, 0.5f, 0.05f, w);
                thickLine(pixmap, max, 0.1f, 0.27f, 0.5f, 0.5f, w);
                thickLine(pixmap, max, 0.9f, 0.27f, 0.5f, 0.5f, w);
                thickLine(pixmap, max, 0.5f, 0.5f, 0.5f, 0.95f, w);
            }
            case FOCUS -> {
                ring(pixmap, size, 0.3f, w, color);
                thickLine(pixmap, max, 0.5f, 0.0f, 0.5f, 0.2f, w);
                thickLine(pixmap, max, 0.5f, 0.8f, 0.5f, 1.0f, w);
                thickLine(pixmap, max, 0.0f, 0.5f, 0.2f, 0.5f, w);
                thickLine(pixmap, max, 0.8f, 0.5f, 1.0f, 0.5f, w);
                pixmap.fillCircle(max / 2, max / 2, Math.max(1, Math.round(size * 0.08f)));
            }
            case GRID -> {
                box(pixmap, max, 0.08f, 0.08f, 0.92f, 0.92f, w);
                thickLine(pixmap, max, 0.36f, 0.08f, 0.36f, 0.92f, w);
                thickLine(pixmap, max, 0.64f, 0.08f, 0.64f, 0.92f, w);
                thickLine(pixmap, max, 0.08f, 0.36f, 0.92f, 0.36f, w);
                thickLine(pixmap, max, 0.08f, 0.64f, 0.92f, 0.64f, w);
            }
            case PHYSICS -> {
                // A collider: a circle inside its bounding box
                ring(pixmap, size, 0.32f, w, color);
                box(pixmap, max, 0.05f, 0.05f, 0.95f, 0.95f, Math.max(1, w / 2));
            }
            case RESET_VIEW -> {
                // A house: roof and body
                triangle(pixmap, max, 0.5f, 0.05f, 0.05f, 0.5f, 0.95f, 0.5f);
                box(pixmap, max, 0.2f, 0.5f, 0.8f, 0.95f, w);
            }
            case SCULPT -> {
                // A blob of clay on a tool handle
                pixmap.fillCircle(Math.round(max * 0.34f), Math.round(max * 0.66f), Math.max(2, Math.round(size * 0.26f)));
                thickLine(pixmap, max, 0.45f, 0.55f, 0.92f, 0.08f, w + 1);
            }
            default -> throw new IllegalArgumentException("Not a tool icon: " + icon);
        }
    }

    private static void triangle(Pixmap pixmap, int max, float x1, float y1, float x2, float y2, float x3, float y3) {
        pixmap.fillTriangle(Math.round(x1 * max), Math.round(y1 * max), Math.round(x2 * max), Math.round(y2 * max),
            Math.round(x3 * max), Math.round(y3 * max));
    }

    /** Draws a line {@code width} pixels thick by stacking offset 1-pixel lines. */
    private static void thickLine(Pixmap pixmap, int max, float x1, float y1, float x2, float y2, int width) {
        int ax = Math.round(x1 * max);
        int ay = Math.round(y1 * max);
        int bx = Math.round(x2 * max);
        int by = Math.round(y2 * max);
        boolean mostlyVertical = Math.abs(by - ay) >= Math.abs(bx - ax);
        for (int i = 0; i < width; i++) {
            int offset = i - width / 2;
            if (mostlyVertical) {
                pixmap.drawLine(ax + offset, ay, bx + offset, by);
            } else {
                pixmap.drawLine(ax, ay + offset, bx, by + offset);
            }
        }
    }

    private static void box(Pixmap pixmap, int max, float x1, float y1, float x2, float y2, int width) {
        thickLine(pixmap, max, x1, y1, x2, y1, width);
        thickLine(pixmap, max, x1, y2, x2, y2, width);
        thickLine(pixmap, max, x1, y1, x1, y2, width);
        thickLine(pixmap, max, x2, y1, x2, y2, width);
    }

    /** Draws a circle outline by filling a disc and clearing its inside; leaves the pixmap colour as {@code color}. */
    private static void ring(Pixmap pixmap, int size, float radiusFraction, int width, Color color) {
        int center = (size - 1) / 2;
        int radius = Math.round(size * radiusFraction);
        pixmap.fillCircle(center, center, radius);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fillCircle(center, center, Math.max(0, radius - width));
        pixmap.setColor(color);
    }

    private Drawable createIconDrawable(EditorIcon icon, Color color) {
        return createIconDrawable(icon, color, ICON_SIZE);
    }

    private Drawable createIconDrawable(EditorIcon icon, Color color, int iconSize) {
        float density = uiDensity();
        int size = Math.max(8, Math.round(iconSize * density));
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
            default -> drawToolIcon(pixmap, icon, size, color);
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        ownedResources.add(texture);
        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(texture));
        drawable.setMinSize(iconSize, iconSize);
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

    /** A box of {@code fill} with a one pixel {@code border}, stretched to any size, with room around the text. */
    private Drawable createBorderedDrawable(Color fill, Color border) {
        Pixmap pixmap = new Pixmap(3, 3, Pixmap.Format.RGBA8888);
        pixmap.setColor(border);
        pixmap.fill();
        pixmap.setColor(fill);
        pixmap.drawPixel(1, 1);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        ownedResources.add(texture);
        NinePatch patch = new NinePatch(new TextureRegion(texture), 1, 1, 1, 1);
        patch.setPadding(BAR_BUTTON_PAD_X, BAR_BUTTON_PAD_X, BAR_BUTTON_PAD_Y, BAR_BUTTON_PAD_Y);
        return new NinePatchDrawable(patch);
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
