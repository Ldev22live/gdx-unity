package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptActions;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptDocument;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Details editor for a {@link DynamicMeshComponent}, the component of a sculpt (TD-038, 19-B step 6): the sculpt file,
 * how many triangles it has and whether it has unsaved strokes, its colour, and buttons that start sculpting it and
 * save it.
 */
public class DynamicMeshComponentEditorProvider implements ComponentEditorProvider<DynamicMeshComponent, Table> {
    private final SculptSession session;
    private final Consumer<AssetReference> onExported;

    /**
     * @param session    the open sculpts; the buttons act on the one this component names
     * @param onExported called with the model an export wrote, so the app can rescan its assets
     */
    public DynamicMeshComponentEditorProvider(SculptSession session, Consumer<AssetReference> onExported) {
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.onExported = Objects.requireNonNull(onExported, "onExported cannot be null");
    }

    @Override
    public Class<DynamicMeshComponent> getComponentClass() {
        return DynamicMeshComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Sculpt";
    }

    @Override
    public void buildEditor(Table container, Entity entity, DynamicMeshComponent mesh, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        container.add(new Label("File  " + mesh.getMeshPath(), skin, "muted")).left().padLeft(2).row();
        SculptDocument document = session.getDocument(mesh.getMeshPath());
        container.add(new Label(describe(document, session.getLoadError(mesh.getMeshPath())), skin, "muted")).left().padLeft(2).row();
        String warning = session.triangleWarning(document);
        if (warning != null) {
            Label label = new Label(warning, skin, "muted");
            label.setColor(skin.getColor("error"));
            label.setWrap(true);
            container.add(label).left().growX().padLeft(2).row();
        }
        InspectorFieldRows.colorRow(container, skin, "Colour", mesh.getColor(), color ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<DynamicMeshComponent, Color>(
                editorState, entity.getId(), mesh, new Color(mesh.getColor()), new Color(color), mesh::setColor, "Change Sculpt Colour")));

        Table buttons = new Table(skin);
        buttons.left();
        buttons.add(button(skin, session.isSculptMode() ? "Stop Sculpting" : "Sculpt", () -> {
            session.setSculptMode(!session.isSculptMode());
            editorState.setStatusMessage(session.isSculptMode() ? "Sculpt mode on. " + SculptActions.brushSummary(session) : "Sculpt mode off", false);
        })).height(24).padRight(4);
        buttons.add(button(skin, "Save", () -> {
            try {
                session.save(new AssetReference(mesh.getMeshPath()));
                editorState.setStatusMessage("Saved " + mesh.getMeshPath(), false);
            } catch (IOException | RuntimeException e) {
                editorState.setStatusMessage("Cannot save " + mesh.getMeshPath() + ": " + e.getMessage(), true);
            }
        })).height(24).padRight(4);
        buttons.add(button(skin, "Export Model", () -> {
            AssetReference model = SculptActions.exportModel(editorState, session, entity);
            if (model != null) {
                onExported.accept(model);
            }
        })).height(24);
        container.add(buttons).left().padTop(4).padBottom(4).row();
    }

    /** "196,608 triangles, 2 unsaved strokes" style line, or why the sculpt cannot be shown. */
    static String describe(SculptDocument document, String loadError) {
        if (document == null) {
            return loadError != null ? "Cannot load: " + loadError : "Not loaded yet; it opens when the view draws it";
        }
        int triangles = document.getSurface().getMesh().getTriangleCount();
        return String.format(Locale.ROOT, "%,d triangles%s", triangles, document.isModified() ? ", unsaved changes" : "");
    }

    private static TextButton button(Skin skin, String text, Runnable action) {
        TextButton button = new TextButton(text, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }
}
