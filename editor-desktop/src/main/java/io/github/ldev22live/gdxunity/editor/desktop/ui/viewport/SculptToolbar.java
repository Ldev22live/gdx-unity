package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptActions;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptSession;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushType;
import io.github.ldev22live.gdxunity.engine.sculpt.SymmetrySettings;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Strip of sculpt controls under the viewport tool strip, visible only while sculpt mode is on: one button per brush,
 * the wireframe overlay and the mirror axes. Every button runs an action from the {@link EditorActionRegistry}, and the
 * brush, wireframe and mirror buttons are drawn in the accent colour while they are on.
 */
public class SculptToolbar extends Table {
    private static final float BUTTON_HEIGHT = 24f;

    private final Skin skin;
    private final SculptSession session;
    private final Map<BrushType, TextButton> brushButtons = new EnumMap<>(BrushType.class);
    private final Map<TextButton, BooleanSupplier> toggles = new LinkedHashMap<>();

    public SculptToolbar(EditorActionRegistry actions, SculptSession session, Skin skin) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(actions, "actions cannot be null");
        setBackground("elevated-bg");
        pad(2f);

        for (BrushType type : BrushType.values()) {
            TextButton button = addButton(actions, SculptActions.brushActionId(type), displayName(type));
            brushButtons.put(type, button);
        }
        add(new Label("|", skin, "muted")).padLeft(4).padRight(4);
        SymmetrySettings symmetry = session.getSymmetry();
        toggles.put(addButton(actions, SculptActions.TOGGLE_WIREFRAME, "Wire"), session::isWireframe);
        toggles.put(addButton(actions, SculptActions.MIRROR_X, "Mirror X"), symmetry::isMirrorX);
        toggles.put(addButton(actions, SculptActions.MIRROR_Y, "Y"), symmetry::isMirrorY);
        toggles.put(addButton(actions, SculptActions.MIRROR_Z, "Z"), symmetry::isMirrorZ);
        pack();
    }

    private TextButton addButton(EditorActionRegistry actions, String actionId, String label) {
        TextButton button = new TextButton(label, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                actions.execute(actionId);
            }
        });
        add(button).height(BUTTON_HEIGHT).padLeft(1).padRight(1);
        return button;
    }

    private static String displayName(BrushType type) {
        String name = type.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    public void act(float delta) {
        setVisible(session.isSculptMode());
        super.act(delta);
        for (Map.Entry<BrushType, TextButton> entry : brushButtons.entrySet()) {
            entry.getValue().setColor(skin.getColor(entry.getKey() == session.getBrushType() ? "accent" : "text-primary"));
        }
        for (Map.Entry<TextButton, BooleanSupplier> toggle : toggles.entrySet()) {
            toggle.getKey().setColor(skin.getColor(toggle.getValue().getAsBoolean() ? "accent" : "text-primary"));
        }
    }
}
