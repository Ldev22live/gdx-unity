package io.github.ldev22live.gdxunity.editor.core.theme;

import com.badlogic.gdx.graphics.Color;

import java.util.Objects;

/**
 * Material Design 3 semantic color tokens for the editor.
 * Provides consistent tokens for surfaces, text, borders, accents, selections, and status states.
 */
public final class EditorThemePalette {
    private final Color background;
    private final Color surface;
    private final Color surfaceVariant;
    private final Color surfaceElevated;
    private final Color header;
    private final Color subheader;

    private final Color textPrimary;
    private final Color textSecondary;
    private final Color textMuted;
    private final Color textOnAccent;

    private final Color border;
    private final Color borderMuted;
    private final Color splitHandle;

    private final Color primary;
    private final Color primaryActive;
    private final Color selection;
    private final Color cursor;
    private final Color focus;

    private final Color success;
    private final Color warning;
    private final Color error;
    private final Color disabled;
    private final Color playTint;

    public EditorThemePalette(Builder builder) {
        this.background = Objects.requireNonNull(builder.background, "background cannot be null");
        this.surface = Objects.requireNonNull(builder.surface, "surface cannot be null");
        this.surfaceVariant = Objects.requireNonNull(builder.surfaceVariant, "surfaceVariant cannot be null");
        this.surfaceElevated = Objects.requireNonNull(builder.surfaceElevated, "surfaceElevated cannot be null");
        this.header = Objects.requireNonNull(builder.header, "header cannot be null");
        this.subheader = Objects.requireNonNull(builder.subheader, "subheader cannot be null");

        this.textPrimary = Objects.requireNonNull(builder.textPrimary, "textPrimary cannot be null");
        this.textSecondary = Objects.requireNonNull(builder.textSecondary, "textSecondary cannot be null");
        this.textMuted = Objects.requireNonNull(builder.textMuted, "textMuted cannot be null");
        this.textOnAccent = Objects.requireNonNull(builder.textOnAccent, "textOnAccent cannot be null");

        this.border = Objects.requireNonNull(builder.border, "border cannot be null");
        this.borderMuted = Objects.requireNonNull(builder.borderMuted, "borderMuted cannot be null");
        this.splitHandle = Objects.requireNonNull(builder.splitHandle, "splitHandle cannot be null");

        this.primary = Objects.requireNonNull(builder.primary, "primary cannot be null");
        this.primaryActive = Objects.requireNonNull(builder.primaryActive, "primaryActive cannot be null");
        this.selection = Objects.requireNonNull(builder.selection, "selection cannot be null");
        this.cursor = Objects.requireNonNull(builder.cursor, "cursor cannot be null");
        this.focus = Objects.requireNonNull(builder.focus, "focus cannot be null");

        this.success = Objects.requireNonNull(builder.success, "success cannot be null");
        this.warning = Objects.requireNonNull(builder.warning, "warning cannot be null");
        this.error = Objects.requireNonNull(builder.error, "error cannot be null");
        this.disabled = Objects.requireNonNull(builder.disabled, "disabled cannot be null");
        this.playTint = Objects.requireNonNull(builder.playTint, "playTint cannot be null");
    }

    public static EditorThemePalette createDarkPalette() {
        return new Builder()
            // MD3 Dark Neutrals & Surfaces
            .background(new Color(0.11f, 0.11f, 0.12f, 1f))       // #1C1B1F
            .surface(new Color(0.15f, 0.15f, 0.17f, 1f))          // #26262B
            .surfaceVariant(new Color(0.18f, 0.18f, 0.20f, 1f))   // #2E2E33
            .surfaceElevated(new Color(0.22f, 0.22f, 0.25f, 1f))  // #383840
            .header(new Color(0.13f, 0.13f, 0.14f, 1f))           // #212124
            .subheader(new Color(0.14f, 0.14f, 0.16f, 1f))        // #242429

            // Typography
            .textPrimary(new Color(0.92f, 0.92f, 0.95f, 1f))      // #EBEBF2
            .textSecondary(new Color(0.72f, 0.72f, 0.76f, 1f))    // #B8B8C2
            .textMuted(new Color(0.50f, 0.50f, 0.54f, 1f))        // #80808A
            .textOnAccent(Color.WHITE)

            // Outlines / Dividers
            .border(new Color(0.26f, 0.26f, 0.30f, 1f))
            .borderMuted(new Color(0.18f, 0.18f, 0.20f, 1f))
            .splitHandle(new Color(0.09f, 0.09f, 0.10f, 1f))

            // Primary Accent & Interactive States (MD3 Blue / Indigo)
            .primary(new Color(0.30f, 0.55f, 0.96f, 1f))          // #4D8CF5
            .primaryActive(new Color(0.42f, 0.65f, 1.0f, 1f))
            .selection(new Color(0.20f, 0.42f, 0.75f, 0.85f))
            .cursor(new Color(0.95f, 0.95f, 1.0f, 1f))
            .focus(new Color(0.40f, 0.65f, 1.0f, 0.9f))

            // Semantics & Status
            .success(new Color(0.30f, 0.78f, 0.45f, 1f))
            .warning(new Color(0.96f, 0.72f, 0.20f, 1f))
            .error(new Color(0.95f, 0.36f, 0.36f, 1f))
            .disabled(new Color(0.38f, 0.38f, 0.42f, 1f))
            .playTint(new Color(0.12f, 0.22f, 0.14f, 1f))
            .build();
    }

    public static EditorThemePalette createLightPalette() {
        return new Builder()
            // MD3 Light Neutrals & Surfaces
            .background(new Color(0.94f, 0.94f, 0.96f, 1f))       // #F0F0F5
            .surface(new Color(0.98f, 0.98f, 0.99f, 1f))          // #FAFAFC
            .surfaceVariant(new Color(0.90f, 0.90f, 0.93f, 1f))   // #E6E6ED
            .surfaceElevated(new Color(1.0f, 1.0f, 1.0f, 1f))     // Pure White
            .header(new Color(0.88f, 0.88f, 0.91f, 1f))           // #E0E0E8
            .subheader(new Color(0.92f, 0.92f, 0.94f, 1f))        // #EBEBF0

            // Typography
            .textPrimary(new Color(0.12f, 0.12f, 0.14f, 1f))      // High Contrast Dark
            .textSecondary(new Color(0.32f, 0.32f, 0.36f, 1f))    // Medium Contrast
            .textMuted(new Color(0.52f, 0.52f, 0.56f, 1f))        // Muted
            .textOnAccent(Color.WHITE)

            // Outlines / Dividers
            .border(new Color(0.78f, 0.78f, 0.82f, 1f))
            .borderMuted(new Color(0.86f, 0.86f, 0.89f, 1f))
            .splitHandle(new Color(0.80f, 0.80f, 0.84f, 1f))

            // Primary Accent & Interactive States (MD3 Blue / Indigo)
            .primary(new Color(0.16f, 0.42f, 0.82f, 1f))          // #296BD1
            .primaryActive(new Color(0.12f, 0.34f, 0.72f, 1f))
            .selection(new Color(0.24f, 0.48f, 0.85f, 0.30f))
            .cursor(new Color(0.12f, 0.12f, 0.14f, 1f))
            .focus(new Color(0.20f, 0.45f, 0.85f, 0.9f))

            // Semantics & Status
            .success(new Color(0.15f, 0.60f, 0.28f, 1f))
            .warning(new Color(0.85f, 0.55f, 0.05f, 1f))
            .error(new Color(0.85f, 0.20f, 0.20f, 1f))
            .disabled(new Color(0.65f, 0.65f, 0.70f, 1f))
            .playTint(new Color(0.86f, 0.95f, 0.88f, 1f))
            .build();
    }

    public Color getBackground() { return background; }
    public Color getSurface() { return surface; }
    public Color getSurfaceVariant() { return surfaceVariant; }
    public Color getSurfaceElevated() { return surfaceElevated; }
    public Color getHeader() { return header; }
    public Color getSubheader() { return subheader; }
    public Color getTextPrimary() { return textPrimary; }
    public Color getTextSecondary() { return textSecondary; }
    public Color getTextMuted() { return textMuted; }
    public Color getTextOnAccent() { return textOnAccent; }
    public Color getBorder() { return border; }
    public Color getBorderMuted() { return borderMuted; }
    public Color getSplitHandle() { return splitHandle; }
    public Color getPrimary() { return primary; }
    public Color getPrimaryActive() { return primaryActive; }
    public Color getSelection() { return selection; }
    public Color getCursor() { return cursor; }
    public Color getFocus() { return focus; }
    public Color getSuccess() { return success; }
    public Color getWarning() { return warning; }
    public Color getError() { return error; }
    public Color getDisabled() { return disabled; }
    public Color getPlayTint() { return playTint; }

    public static class Builder {
        private Color background;
        private Color surface;
        private Color surfaceVariant;
        private Color surfaceElevated;
        private Color header;
        private Color subheader;
        private Color textPrimary;
        private Color textSecondary;
        private Color textMuted;
        private Color textOnAccent;
        private Color border;
        private Color borderMuted;
        private Color splitHandle;
        private Color primary;
        private Color primaryActive;
        private Color selection;
        private Color cursor;
        private Color focus;
        private Color success;
        private Color warning;
        private Color error;
        private Color disabled;
        private Color playTint;

        public Builder background(Color color) { this.background = color; return this; }
        public Builder surface(Color color) { this.surface = color; return this; }
        public Builder surfaceVariant(Color color) { this.surfaceVariant = color; return this; }
        public Builder surfaceElevated(Color color) { this.surfaceElevated = color; return this; }
        public Builder header(Color color) { this.header = color; return this; }
        public Builder subheader(Color color) { this.subheader = color; return this; }
        public Builder textPrimary(Color color) { this.textPrimary = color; return this; }
        public Builder textSecondary(Color color) { this.textSecondary = color; return this; }
        public Builder textMuted(Color color) { this.textMuted = color; return this; }
        public Builder textOnAccent(Color color) { this.textOnAccent = color; return this; }
        public Builder border(Color color) { this.border = color; return this; }
        public Builder borderMuted(Color color) { this.borderMuted = color; return this; }
        public Builder splitHandle(Color color) { this.splitHandle = color; return this; }
        public Builder primary(Color color) { this.primary = color; return this; }
        public Builder primaryActive(Color color) { this.primaryActive = color; return this; }
        public Builder selection(Color color) { this.selection = color; return this; }
        public Builder cursor(Color color) { this.cursor = color; return this; }
        public Builder focus(Color color) { this.focus = color; return this; }
        public Builder success(Color color) { this.success = color; return this; }
        public Builder warning(Color color) { this.warning = color; return this; }
        public Builder error(Color color) { this.error = color; return this; }
        public Builder disabled(Color color) { this.disabled = color; return this; }
        public Builder playTint(Color color) { this.playTint = color; return this; }

        public EditorThemePalette build() {
            return new EditorThemePalette(this);
        }
    }
}
