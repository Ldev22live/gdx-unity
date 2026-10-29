package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Labelled numeric rows for Details editors. Values are committed when the user presses Enter or leaves the
 * field, so one edit becomes one undo step; text that is not a valid number restores the previous value.
 */
final class InspectorFieldRows {

    private static final float LABEL_WIDTH = 80f;
    private static final float FIELD_HEIGHT = 22f;

    private InspectorFieldRows() {}

    /** Receives a parsed value after the user commits a field. */
    interface FloatCommit {
        void commit(float value);
    }

    static void floatRow(Table container, Skin skin, String label, float value, FloatCommit onCommit) {
        Table row = new Table(skin);
        row.left();
        row.add(new Label(label, skin, "muted")).width(LABEL_WIDTH).left();
        row.add(floatField(skin, value, onCommit)).growX().height(FIELD_HEIGHT);
        container.add(row).growX().padTop(2).padBottom(2).row();
    }

    /** Three fields (X, Y, Z); a commit in any field passes all three values. */
    static void vector3Row(Table container, Skin skin, String label, Vector3 value, Consumer<Vector3> onCommit) {
        Table row = new Table(skin);
        row.left();
        row.add(new Label(label, skin, "muted")).width(LABEL_WIDTH).left();
        Vector3 current = new Vector3(value);
        row.add(floatField(skin, value.x, x -> onCommit.accept(new Vector3(x, current.y, current.z)))).growX().height(FIELD_HEIGHT).pad(1);
        row.add(floatField(skin, value.y, y -> onCommit.accept(new Vector3(current.x, y, current.z)))).growX().height(FIELD_HEIGHT).pad(1);
        row.add(floatField(skin, value.z, z -> onCommit.accept(new Vector3(current.x, current.y, z)))).growX().height(FIELD_HEIGHT).pad(1);
        container.add(row).growX().padTop(2).padBottom(2).row();
    }

    /** Red, green and blue fields in the 0..1 range; alpha is kept. */
    static void colorRow(Table container, Skin skin, String label, Color value, Consumer<Color> onCommit) {
        Table row = new Table(skin);
        row.left();
        row.add(new Label(label, skin, "muted")).width(LABEL_WIDTH).left();
        Color current = new Color(value);
        row.add(floatField(skin, value.r, r -> onCommit.accept(new Color(clamp01(r), current.g, current.b, current.a)))).growX().height(FIELD_HEIGHT).pad(1);
        row.add(floatField(skin, value.g, g -> onCommit.accept(new Color(current.r, clamp01(g), current.b, current.a)))).growX().height(FIELD_HEIGHT).pad(1);
        row.add(floatField(skin, value.b, b -> onCommit.accept(new Color(current.r, current.g, clamp01(b), current.a)))).growX().height(FIELD_HEIGHT).pad(1);
        container.add(row).growX().padTop(2).padBottom(2).row();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static TextField floatField(Skin skin, float value, FloatCommit onCommit) {
        String original = format(value);
        TextField field = new TextField(original, skin);
        Runnable commit = () -> {
            String text = field.getText().trim();
            if (text.equals(original)) {
                return;
            }
            try {
                onCommit.commit(Float.parseFloat(text));
            } catch (IllegalArgumentException e) {
                // NumberFormatException (not a number) or a value the component rejects: restore the old text
                field.setText(original);
            }
        };
        field.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                commit.run();
            }
        });
        field.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) {
                    commit.run();
                }
            }
        });
        return field;
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
