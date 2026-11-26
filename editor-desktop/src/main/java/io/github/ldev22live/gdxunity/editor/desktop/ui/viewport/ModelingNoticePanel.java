package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshWorkspace;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;

import io.github.ldev22live.gdxunity.editor.sculpt.MeshSaveSession;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The 3D Modeling source panel. Prefab assemblies offer their individual model parts while the World stays visible as reference.
 */
public class ModelingNoticePanel extends Table {
    /** Width the notice's sentences wrap at, in UI units; narrow enough for the editor's 1024-wide minimum window. */
    private static final float WRAP_WIDTH = 440f;

    private final Skin skin;
    private final EditorState editorState;
    private final BooleanSupplier modelingOnScreen;
    private final Path assetRoot;
    private final Consumer<String> modelSaved;
    private Entity shownFor;
    private String shownName;
    private boolean built;
    private boolean shown;

    private record PartChoice(Entity entity) {
        @Override
        public String toString() {
            return entity.getName();
        }
    }

    /**
     * @param modelingOnScreen whether the 3D Modeling workspace is on screen (sculpt mode in the World's view)
     */
    public ModelingNoticePanel(Skin skin, EditorState editorState, BooleanSupplier modelingOnScreen, Path assetRoot) {
        this(skin, editorState, modelingOnScreen, assetRoot, path -> {});
    }

    /** Notifies the World asset cache after a successful save on the graphics thread. */
    public ModelingNoticePanel(Skin skin, EditorState editorState, BooleanSupplier modelingOnScreen, Path assetRoot,
                               Consumer<String> modelSaved) {
        super(skin);
        this.modelSaved = Objects.requireNonNull(modelSaved, "modelSaved cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.modelingOnScreen = Objects.requireNonNull(modelingOnScreen, "modelingOnScreen cannot be null");
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        setBackground("elevated-bg");
        pad(6f);
        setVisible(false);
    }

    @Override
    public void act(float delta) {
        Entity selected = editorState.getSelectedEntity();
        String name = selected == null ? null : selected.getName();
        boolean active = modelingOnScreen.getAsBoolean() && !editorState.isPlayModeActive();
        if (!active && built) {
            clearChildren();
            shownFor = null;
            shownName = null;
            built = false;
        }
        if (active && (!built || selected != shownFor || !Objects.equals(name, shownName))) {
            rebuild(selected);
            shownFor = selected;
            shownName = name;
            built = true;
        }
        boolean visible = shown && active;
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
        if (shown && selected != null) {
            MeshWorkspace workspace = new MeshWorkspace(assetRoot, selected);
            if (!workspace.parts().isEmpty()) {
                buildSourcePanel(selected, workspace);
                invalidateHierarchy();
                return;
            }
        }
        add(new Label(notice.title(), skin)).left().row();
        for (String line : notice.lines()) {
            addLine(line);
        }
        invalidateHierarchy();
    }

    private void buildSourcePanel(Entity selected, MeshWorkspace workspace) {
        add(new Label("Mesh source · " + selected.getName(), skin)).width(WRAP_WIDTH).left().row();
        if (workspace.prefabPath() != null) {
            addLine("Prefab instance · " + workspace.prefabPath());
        }
        Table tools = new Table();
        Map<Entity, Table> drafts = new HashMap<>();
        Label source;
        if (workspace.parts().size() > 1) {
            SelectBox<PartChoice> parts = new SelectBox<>(skin);
            parts.setItems(workspace.parts().stream().map(PartChoice::new).toArray(PartChoice[]::new));
            add(parts).width(WRAP_WIDTH).left().row();
            source = addLine("");
            parts.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    openPart(workspace, parts.getSelected().entity(), source, tools, drafts);
                }
            });
        } else {
            source = addLine("");
        }
        openPart(workspace, workspace.parts().getFirst(), source, tools, drafts);
        addLine("Edit Instance · Move, Rotate and Scale in World and overrides in Details; never changes a mesh.");
        if (workspace.prefabPath() != null) {
            addLine("Edit Shared Prefab · saves a new model into the prefab, changing every instance after a warning.");
        }
        addLine("Create Unique Mesh · saves a new model for this part of this instance only. Save as new model only creates "
            + "a reusable asset.");
        add(tools).left().row();
    }

    private Label addLine(String text) {
        Label label = new Label(text, skin, "muted");
        label.setWrap(true);
        add(label).width(WRAP_WIDTH).left().padTop(2f).row();
        return label;
    }

    private void openPart(MeshWorkspace workspace, Entity part, Label source, Table tools, Map<Entity, Table> drafts) {
        tools.clearChildren();
        try {
            Table panel = drafts.get(part);
            if (panel == null) {
                MeshWorkspace.OpenPart opened = workspace.open(part);
                MeshSaveSession session = new MeshSaveSession(assetRoot, opened);
                panel = new Table();
                panel.add(new MeshEditPanel(skin, session.drafts())).row();
                addSaveControls(panel, session, part, source);
                drafts.put(part, panel);
            }
            tools.add(panel);
            source.setText("Source ready: " + part.getComponent(ModelComponent.class).getModelPath());
        } catch (IOException e) {
            source.setText(e.getMessage());
        }
        invalidateHierarchy();
    }
    private void addSaveControls(Table panel, MeshSaveSession session, Entity part, Label status) {
        Table row = new Table();
        SelectBox<MeshSaveSession.Mode> mode = new SelectBox<>(skin);
        mode.setItems(MeshSaveSession.Mode.values());
        TextField name = new TextField(part.getName().replaceAll("[^A-Za-z0-9_-]", "_") + "-edited", skin);
        TextButton save = new TextButton("Save", skin);
        row.add(mode).padRight(4f);
        row.add(name).width(120f);
        row.add(save);
        panel.add(row);
        mode.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) {
                name.setDisabled(mode.getSelected() == MeshSaveSession.Mode.UPDATE);
            }
        });
        save.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) {
                MeshSaveSession.Mode selected = mode.getSelected();
                boolean shared = selected == MeshSaveSession.Mode.PREFAB;
                if (selected != MeshSaveSession.Mode.UPDATE && !shared) {
                    saveModel(session, selected, name.getText(), status, false);
                    return;
                }
                try {
                    // Both change more than this part: warn first, and save only on the person's confirmation
                    String warning = shared ? session.prefabWarning(editorState) : session.updateWarning(editorState);
                    Dialog confirm = new Dialog(shared ? "Edit shared prefab" : "Update shared model", skin) {
                        protected void result(Object choice) {
                            if (Boolean.TRUE.equals(choice)) saveModel(session, selected, name.getText(), status, true);
                        }
                    };
                    Label message = new Label(warning, skin);
                    message.setWrap(true);
                    confirm.getContentTable().add(message).width(WRAP_WIDTH);
                    confirm.button("Cancel", false).button(shared ? "Apply to all" : "Update", true).show(getStage());
                } catch (IOException e) {
                    status.setText(e.getMessage());
                }
            }
        });
    }

    private void saveModel(MeshSaveSession session, MeshSaveSession.Mode mode, String name, Label status, boolean confirmed) {
        try {
            String path = session.save(mode, name, editorState, confirmed);
            modelSaved.accept(path);
            status.setText("Saved " + path);
        } catch (IOException | IllegalArgumentException e) {
            status.setText(e.getMessage());
        }
    }

}
