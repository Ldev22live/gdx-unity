package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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

    public Skin createSkin() {
        return createSkin(EditorThemePalette.createDarkPalette());
    }

    public Skin createSkin(EditorThemePalette palette) {
        Objects.requireNonNull(palette, "palette cannot be null");
        Skin skin = new Skin();

        // 1. Typography (Base font)
        BitmapFont font = new BitmapFont();
        skin.add("default", font);
        ownedResources.add(font);

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

        Label.LabelStyle titleStyle = new Label.LabelStyle(font, palette.getTextPrimary());
        titleStyle.background = headerBg;
        skin.add("title", titleStyle);

        Label.LabelStyle sectionStyle = new Label.LabelStyle(font, palette.getPrimary());
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
        SplitPane.SplitPaneStyle splitStyle = new SplitPane.SplitPaneStyle(splitHandle);
        skin.add("default-horizontal", splitStyle);
        skin.add("default-vertical", splitStyle);
        skin.add("default", splitStyle);

        return skin;
    }

    private Drawable createColorDrawable(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        ownedResources.add(texture);
        return new TextureRegionDrawable(new TextureRegion(texture));
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
