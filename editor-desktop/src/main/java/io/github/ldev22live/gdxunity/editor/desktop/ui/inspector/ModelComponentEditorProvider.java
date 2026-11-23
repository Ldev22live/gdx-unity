package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.render.ModelAssets;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;

/**
 * Details editor for {@link ModelComponent} (TD-030): the model file, its own clip to play (from the loaded file),
 * playback settings and import scale. Every change is undoable.
 */
public class ModelComponentEditorProvider implements ComponentEditorProvider<ModelComponent, Table> {
    /** Shown in the clip list for "no clip". */
    private static final String REST_POSE = "(rest pose)";

    private final ModelAssets modelAssets;

    public ModelComponentEditorProvider(ModelAssets modelAssets) {
        this.modelAssets = Objects.requireNonNull(modelAssets, "modelAssets cannot be null");
    }

    @Override
    public Class<ModelComponent> getComponentClass() {
        return ModelComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Model";
    }

    @Override
    public void buildEditor(Table container, Entity entity, ModelComponent model, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        // Load now (this runs on the render thread) so a model added this frame already lists its clips
        ModelAssets.LoadedModel loaded = modelAssets.get(model.getModelPath());
        model.setModelInfo(loaded != null ? loaded.info() : modelAssets.info(model.getModelPath()));
        ModelComponent.ModelInfo info = model.getModelInfo();

        Table pathRow = new Table(skin);
        pathRow.left();
        pathRow.add(new Label("File", skin, "muted")).width(80).left();
        Label path = new Label(model.getModelPath(), skin);
        path.setEllipsis(true);
        pathRow.add(path).left().growX().minWidth(0);
        container.add(pathRow).growX().padTop(2).padBottom(2).row();
        if (info.error() != null) {
            Label error = new Label(info.error(), skin, "error");
            error.setWrap(true);
            container.add(error).growX().padBottom(4).row();
        } else {
            container.add(new Label(info.boneCount() + " bones, " + info.animationDurations().size() + " clip(s)", skin, "muted"))
                .left().padBottom(2).row();
        }

        Table buttons = new Table(skin);
        TextButton useSelected = new TextButton("Use Selected Model", skin, "flat");
        useSelected.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetReference selected = editorState.getSelectedAsset();
                if (selected == null || AssetType.fromPath(selected.getPath()) != AssetType.MODEL) {
                    editorState.setStatusMessage("Select a .gltf or .glb in the Content Browser first", true);
                    return;
                }
                editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), model,
                    model.getModelPath(), selected.getPath(), model::setModelPath, "Change Model"));
            }
        });
        buttons.add(useSelected).height(24).padRight(6);
        TextButton reload = new TextButton("Reload", skin, "flat");
        reload.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                modelAssets.reload(model.getModelPath());
                ModelAssets.LoadedModel reloaded = modelAssets.get(model.getModelPath());
                model.setModelInfo(reloaded != null ? reloaded.info() : modelAssets.info(model.getModelPath()));
                editorState.setStatusMessage(model.getModelInfo().error() == null
                    ? "Reloaded " + model.getModelPath() : model.getModelInfo().error(), model.getModelInfo().error() != null);
            }
        });
        buttons.add(reload).height(24);
        container.add(buttons).left().padTop(2).padBottom(4).row();

        Table clipRow = new Table(skin);
        clipRow.left();
        clipRow.add(new Label("Clip", skin, "muted")).width(80).left();
        SelectBox<String> clipSelect = new SelectBox<>(skin);
        Array<String> names = new Array<>();
        names.add(REST_POSE);
        info.animationNames().forEach(names::add);
        if (model.getAnimation() != null && !names.contains(model.getAnimation(), false)) {
            // Keep showing a saved clip name the file no longer has
            names.add(model.getAnimation());
        }
        clipSelect.setItems(names);
        clipSelect.setSelected(model.getAnimation() == null ? REST_POSE : model.getAnimation());
        clipSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String chosen = REST_POSE.equals(clipSelect.getSelected()) ? null : clipSelect.getSelected();
                if (!Objects.equals(chosen, model.getAnimation())) {
                    editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), model,
                        model.getAnimation(), chosen, model::setAnimation, "Change Model Clip"));
                }
            }
        });
        clipRow.add(clipSelect).growX().height(22);
        container.add(clipRow).growX().padTop(2).padBottom(2).row();

        InspectorFieldRows.toggleRow(container, skin, "Play On Start", model.isPlayOnStart(), value ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), model,
                model.isPlayOnStart(), value, model::setPlayOnStart, "Change Play On Start")));
        InspectorFieldRows.toggleRow(container, skin, "Loop", model.isLoop(), value ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), model,
                model.isLoop(), value, model::setLoop, "Change Loop")));
        InspectorFieldRows.floatRow(container, skin, "Speed", model.getSpeed(), value -> {
            if (!Float.isFinite(value)) {
                editorState.setStatusMessage("Speed must be a number", true);
                return;
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), model,
                model.getSpeed(), value, model::setSpeed, "Change Clip Speed"));
        });
        InspectorFieldRows.floatRow(container, skin, "Import Scale", model.getImportScale(), value -> {
            if (!(value > 0f) || Float.isInfinite(value)) {
                editorState.setStatusMessage("Import scale must be positive", true);
                return;
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), model,
                model.getImportScale(), value, model::setImportScale, "Change Import Scale"));
        });
    }
}
