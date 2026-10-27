package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
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
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;

import java.util.Objects;

/**
 * Custom Inspector UI for {@link RigidBody2DComponent}.
 * Supports body type selection, gravity scale, damping, flags, initial velocities, and removal.
 */
public class RigidBody2DComponentEditorProvider implements ComponentEditorProvider<RigidBody2DComponent, Table> {

    @Override
    public Class<RigidBody2DComponent> getComponentClass() {
        return RigidBody2DComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "RigidBody 2D";
    }

    @Override
    public void buildEditor(Table container, Entity entity, RigidBody2DComponent rb, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();

        // 1. Enabled toggle & remove button row
        Table topBar = new Table(skin);
        topBar.left();
        TextButton enableBtn = new TextButton(rb.isEnabled() ? "Enabled: YES" : "Enabled: NO", skin);
        Color successColor = skin.has("success", Color.class) ? skin.getColor("success") : new Color(0.3f, 0.8f, 0.4f, 1f);
        Color mutedColor = skin.has("text-muted", Color.class) ? skin.getColor("text-muted") : Color.GRAY;
        enableBtn.setColor(rb.isEnabled() ? successColor : mutedColor);
        enableBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean target = !rb.isEnabled();
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, entity.getId(), rb, rb.isEnabled(), target, rb::setEnabled, "Toggle RigidBody2D Enabled"
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
                editorState.getCommandHistory().execute(new RemoveComponentCommand(editorState, entity.getId(), rb));
            }
        });
        topBar.add(removeBtn).height(22).pad(2);
        container.add(topBar).growX().padBottom(4).row();

        // 2. Body Type dropdown
        Table typeRow = new Table(skin);
        typeRow.left();
        typeRow.add(new Label("Body Type", skin, "muted")).width(110).left();
        SelectBox<RigidBody2DComponent.BodyType> typeSelect = new SelectBox<>(skin);
        typeSelect.setItems(RigidBody2DComponent.BodyType.values());
        typeSelect.setSelected(rb.getBodyType());
        typeSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                RigidBody2DComponent.BodyType newType = typeSelect.getSelected();
                if (newType != rb.getBodyType()) {
                    editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                        editorState, entity.getId(), rb, rb.getBodyType(), newType, rb::setBodyType, "Change RigidBody2D BodyType"
                    ));
                }
            }
        });
        typeRow.add(typeSelect).growX().height(22);
        container.add(typeRow).growX().pad(2).row();

        // 3. Gravity Scale
        addFloatField(container, skin, editorState, entity, "Gravity Scale", rb.getGravityScale(),
            newVal -> editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                editorState, entity.getId(), rb, rb.getGravityScale(), newVal, rb::setGravityScale, "Change Gravity Scale"
            )));

        // 4. Linear Damping
        addFloatField(container, skin, editorState, entity, "Linear Damping", rb.getLinearDamping(),
            newVal -> {
                if (newVal >= 0f) {
                    editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                        editorState, entity.getId(), rb, rb.getLinearDamping(), newVal, rb::setLinearDamping, "Change Linear Damping"
                    ));
                }
            });

        // 5. Angular Damping
        addFloatField(container, skin, editorState, entity, "Angular Damping", rb.getAngularDamping(),
            newVal -> {
                if (newVal >= 0f) {
                    editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                        editorState, entity.getId(), rb, rb.getAngularDamping(), newVal, rb::setAngularDamping, "Change Angular Damping"
                    ));
                }
            });

        // 6. Boolean Flags: Fixed Rotation & Continuous/Bullet
        Table flagsRow = new Table(skin);
        flagsRow.left();
        TextButton fixedRotBtn = new TextButton(rb.isFixedRotation() ? "[X] Fixed Rotation" : "[ ] Fixed Rotation", skin);
        fixedRotBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean target = !rb.isFixedRotation();
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, entity.getId(), rb, rb.isFixedRotation(), target, rb::setFixedRotation, "Toggle Fixed Rotation"
                ));
            }
        });
        flagsRow.add(fixedRotBtn).height(22).padRight(4);

        TextButton bulletBtn = new TextButton(rb.isBullet() ? "[X] Bullet" : "[ ] Bullet", skin);
        bulletBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean target = !rb.isBullet();
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, entity.getId(), rb, rb.isBullet(), target, rb::setBullet, "Toggle Bullet Mode"
                ));
            }
        });
        flagsRow.add(bulletBtn).height(22);
        container.add(flagsRow).growX().pad(2).row();

        // 7. Initial Linear Velocity
        Vector2 vel = rb.getInitialLinearVelocity();
        addVector2Row(container, skin, editorState, entity, "Linear Vel", vel.x, vel.y, (x, y) -> {
            Vector2 oldV = new Vector2(rb.getInitialLinearVelocity());
            Vector2 newV = new Vector2(x, y);
            editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                editorState, entity.getId(), rb, oldV, newV, rb::setInitialLinearVelocity, "Change Linear Velocity"
            ));
        });

        // 8. Initial Angular Velocity
        addFloatField(container, skin, editorState, entity, "Angular Vel", rb.getInitialAngularVelocity(),
            newVal -> editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                editorState, entity.getId(), rb, rb.getInitialAngularVelocity(), newVal, rb::setInitialAngularVelocity, "Change Angular Velocity"
            )));
    }

    private void addFloatField(Table container, Skin skin, EditorState editorState, Entity entity, String label, float currentVal, java.util.function.Consumer<Float> consumer) {
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

    @FunctionalInterface
    interface Vector2Consumer {
        void accept(float x, float y);
    }

    private void addVector2Row(Table container, Skin skin, EditorState editorState, Entity entity, String labelText, float initX, float initY, Vector2Consumer consumer) {
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
