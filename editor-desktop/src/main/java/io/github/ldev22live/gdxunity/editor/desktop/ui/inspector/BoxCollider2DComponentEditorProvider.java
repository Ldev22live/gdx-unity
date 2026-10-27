package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.command.RemoveComponentCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;

/**
 * Custom Inspector UI for {@link BoxCollider2DComponent}.
 * Supports width, height, offset, density, friction, restitution, sensor, category/mask bits, and removal.
 */
public class BoxCollider2DComponentEditorProvider implements ComponentEditorProvider<BoxCollider2DComponent, Table> {

    @Override
    public Class<BoxCollider2DComponent> getComponentClass() {
        return BoxCollider2DComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Box Collider 2D";
    }

    @Override
    public void buildEditor(Table container, Entity entity, BoxCollider2DComponent collider, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();

        // 1. Top bar: Enabled toggle & Remove button
        Table topBar = new Table(skin);
        topBar.left();
        TextButton enableBtn = new TextButton(collider.isEnabled() ? "Enabled: YES" : "Enabled: NO", skin);
        Color successColor = skin.has("success", Color.class) ? skin.getColor("success") : new Color(0.3f, 0.8f, 0.4f, 1f);
        Color mutedColor = skin.has("text-muted", Color.class) ? skin.getColor("text-muted") : Color.GRAY;
        enableBtn.setColor(collider.isEnabled() ? successColor : mutedColor);
        enableBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean target = !collider.isEnabled();
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, entity.getId(), collider, collider.isEnabled(), target, collider::setEnabled, "Toggle BoxCollider2D Enabled"
                ));
            }
        });
        topBar.add(enableBtn).height(22).pad(2);

        TextButton removeBtn = new TextButton("Remove", skin);
        Color errColor = skin.has("error", Color.class) ? skin.getColor("error") : Color.SALMON;
        removeBtn.setColor(errColor);
        removeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.getCommandHistory().execute(new RemoveComponentCommand(editorState, entity.getId(), collider));
            }
        });
        topBar.add(removeBtn).height(22).pad(2);
        container.add(topBar).growX().padBottom(4).row();

        // 2. Width and Height
        addDimensionsRow(container, skin, editorState, entity, collider);

        // 3. Local Offset
        Vector2 offset = collider.getOffset();
        addVector2Row(container, skin, "Offset", offset.x, offset.y, (x, y) -> {
            Vector2 oldOff = new Vector2(collider.getOffset());
            Vector2 newOff = new Vector2(x, y);
            editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                editorState, entity.getId(), collider, oldOff, newOff, collider::setOffset, "Change BoxCollider2D Offset"
            ));
        });

        // 4. Material: Density, Friction, Restitution
        addFloatField(container, skin, "Density", collider.getDensity(), newVal -> {
            if (newVal >= 0f) {
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, entity.getId(), collider, collider.getDensity(), newVal, collider::setDensity, "Change Collider Density"
                ));
            }
        });

        addFloatField(container, skin, "Friction", collider.getFriction(), newVal -> {
            if (newVal >= 0f && newVal <= 1f) {
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, entity.getId(), collider, collider.getFriction(), newVal, collider::setFriction, "Change Collider Friction"
                ));
            }
        });

        addFloatField(container, skin, "Restitution", collider.getRestitution(), newVal -> {
            if (newVal >= 0f && newVal <= 1f) {
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, entity.getId(), collider, collider.getRestitution(), newVal, collider::setRestitution, "Change Collider Restitution"
                ));
            }
        });

        // 5. Sensor Toggle
        Table sensorRow = new Table(skin);
        sensorRow.left();
        TextButton sensorBtn = new TextButton(collider.isSensor() ? "[X] Is Trigger / Sensor" : "[ ] Is Trigger / Sensor", skin);
        sensorBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean target = !collider.isSensor();
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, entity.getId(), collider, collider.isSensor(), target, collider::setSensor, "Toggle Collider Sensor"
                ));
            }
        });
        sensorRow.add(sensorBtn).height(22);
        container.add(sensorRow).growX().pad(2).row();

        // 6. Collision Filtering: Category Bits & Mask Bits
        addShortField(container, skin, "Category Bits", collider.getCategoryBits(), newBits -> {
            editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                editorState, entity.getId(), collider, collider.getCategoryBits(), newBits, collider::setCategoryBits, "Change Category Bits"
            ));
        });

        addShortField(container, skin, "Mask Bits", collider.getMaskBits(), newBits -> {
            editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                editorState, entity.getId(), collider, collider.getMaskBits(), newBits, collider::setMaskBits, "Change Mask Bits"
            ));
        });
    }

    private void addDimensionsRow(Table container, Skin skin, EditorState editorState, Entity entity, BoxCollider2DComponent collider) {
        Table row = new Table(skin);
        row.left();
        row.add(new Label("Size", skin, "muted")).width(110).left();

        TextField fW = new TextField(String.format("%.1f", collider.getWidth()), skin);
        TextField fH = new TextField(String.format("%.1f", collider.getHeight()), skin);

        fW.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    float w = Float.parseFloat(fW.getText().trim());
                    if (Float.isFinite(w) && w > 0f && w != collider.getWidth()) {
                        editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                            editorState, entity.getId(), collider, collider.getWidth(), w, collider::setWidth, "Change BoxCollider Width"
                        ));
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        fH.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    float h = Float.parseFloat(fH.getText().trim());
                    if (Float.isFinite(h) && h > 0f && h != collider.getHeight()) {
                        editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                            editorState, entity.getId(), collider, collider.getHeight(), h, collider::setHeight, "Change BoxCollider Height"
                        ));
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        row.add(new Label("W", skin, "muted")).padLeft(2).padRight(2);
        row.add(fW).growX().height(22).pad(1);
        row.add(new Label("H", skin, "muted")).padLeft(4).padRight(2);
        row.add(fH).growX().height(22).pad(1);

        container.add(row).growX().pad(2).row();
    }

    private void addFloatField(Table container, Skin skin, String label, float currentVal, java.util.function.Consumer<Float> consumer) {
        Table row = new Table(skin);
        row.left();
        row.add(new Label(label, skin, "muted")).width(110).left();
        TextField tf = new TextField(String.format("%.2f", currentVal), skin);
        tf.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    float val = Float.parseFloat(tf.getText().trim());
                    if (Float.isFinite(val)) {
                        consumer.accept(val);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });
        row.add(tf).growX().height(22);
        container.add(row).growX().pad(2).row();
    }

    private void addShortField(Table container, Skin skin, String label, short currentVal, java.util.function.Consumer<Short> consumer) {
        Table row = new Table(skin);
        row.left();
        row.add(new Label(label, skin, "muted")).width(110).left();
        TextField tf = new TextField(String.valueOf(currentVal), skin);
        tf.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    short val = Short.parseShort(tf.getText().trim());
                    consumer.accept(val);
                } catch (NumberFormatException ignored) {}
            }
        });
        row.add(tf).growX().height(22);
        container.add(row).growX().pad(2).row();
    }

    @FunctionalInterface
    interface Vector2Consumer {
        void accept(float x, float y);
    }

    private void addVector2Row(Table container, Skin skin, String labelText, float initX, float initY, Vector2Consumer consumer) {
        Table row = new Table(skin);
        row.left();
        row.add(new Label(labelText, skin, "muted")).width(110).left();

        TextField fX = new TextField(String.format("%.2f", initX), skin);
        TextField fY = new TextField(String.format("%.2f", initY), skin);

        ChangeListener changeListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    float x = Float.parseFloat(fX.getText().trim());
                    float y = Float.parseFloat(fY.getText().trim());
                    if (Float.isFinite(x) && Float.isFinite(y)) {
                        consumer.accept(x, y);
                    }
                } catch (NumberFormatException ignored) {}
            }
        };

        fX.addListener(changeListener);
        fY.addListener(changeListener);

        row.add(new Label("X", skin, "muted")).padLeft(2).padRight(2);
        row.add(fX).growX().height(22).pad(1);
        row.add(new Label("Y", skin, "muted")).padLeft(4).padRight(2);
        row.add(fY).growX().height(22).pad(1);

        container.add(row).growX().pad(2).row();
    }
}
