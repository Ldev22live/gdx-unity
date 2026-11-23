package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The capability notice of the 3D Modeling workspace (roadmap M3c.4): while 3D Modeling is on screen, says what can be done there for
 * the selection, from {@link ModelingHandOff#noticeFor}. For a model or shape it says that mesh editing is planned (R7a), what a sculpt
 * copy does and that moving it never edits its mesh. Hidden for a sculpt, whose brush strip says what it can do, and during Play. The
 * text is rebuilt only when the selection or its name changes, not every frame, and a hidden notice takes no room.
 */
public class ModelingNoticePanel extends Table {
    /** Width the notice's sentences wrap at, in UI units; narrow enough for the editor's 1024-wide minimum window. */
    private static final float WRAP_WIDTH = 440f;

    private final Skin skin;
    private final EditorState editorState;
    private final BooleanSupplier modelingOnScreen;
    private Entity shownFor;
    private String shownName;
    private boolean built;
    private boolean shown;

    /**
     * @param modelingOnScreen whether the 3D Modeling workspace is on screen (sculpt mode in the World's view)
     */
    public ModelingNoticePanel(Skin skin, EditorState editorState, BooleanSupplier modelingOnScreen) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.modelingOnScreen = Objects.requireNonNull(modelingOnScreen, "modelingOnScreen cannot be null");
        setBackground("elevated-bg");
        pad(6f);
        setVisible(false);
    }

    @Override
    public void act(float delta) {
        Entity selected = editorState.getSelectedEntity();
        String name = selected == null ? null : selected.getName();
        if (!built || selected != shownFor || !Objects.equals(name, shownName)) {
            rebuild(selected);
            shownFor = selected;
            shownName = name;
            built = true;
        }
        boolean visible = shown && modelingOnScreen.getAsBoolean() && !editorState.isPlayModeActive();
        if (visible != isVisible()) {
            setVisible(visible);
            invalidateHierarchy(); // a hidden notice takes no room, so the level palette below it does not move in World
        }
        super.act(delta);
    }

    @Override
    public float getPrefWidth() {
        return isVisible() ? super.getPrefWidth() : 0f;
    }

    @Override
    public float getPrefHeight() {
        return isVisible() ? super.getPrefHeight() : 0f;
    }

    private void rebuild(Entity selected) {
        clearChildren();
        ModelingHandOff.Notice notice = ModelingHandOff.noticeFor(selected);
        shown = notice.isShown();
        add(new Label(notice.title(), skin)).left().row();
        for (String line : notice.lines()) {
            Label label = new Label(line, skin, "muted");
            label.setWrap(true);
            add(label).width(WRAP_WIDTH).left().padTop(2f).row();
        }
        invalidateHierarchy();
    }
}
