package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.animation.WrapMode;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Details editor for {@link AnimatorComponent} (TD-031): the clip asset, a button that opens it in the Timeline to
 * animate this entity, and playback settings. Every change is undoable.
 */
public class AnimatorComponentEditorProvider implements ComponentEditorProvider<AnimatorComponent, Table> {
    private final BiConsumer<AssetReference, Entity> openInTimeline;

    /** @param openInTimeline opens a clip in the Timeline, animating the given entity */
    public AnimatorComponentEditorProvider(BiConsumer<AssetReference, Entity> openInTimeline) {
        this.openInTimeline = Objects.requireNonNull(openInTimeline, "openInTimeline cannot be null");
    }

    @Override
    public Class<AnimatorComponent> getComponentClass() {
        return AnimatorComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Animator";
    }

    @Override
    public void buildEditor(Table container, Entity entity, AnimatorComponent animator, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        Table row = new Table(skin);
        row.left();
        row.add(new Label("Clip", skin, "muted")).width(80).left();
        Label path = new Label(animator.getClipPath(), skin);
        path.setEllipsis(true);
        row.add(path).left().growX().minWidth(0);
        container.add(row).growX().padTop(2).padBottom(2).row();

        Table buttons = new Table(skin);
        TextButton open = new TextButton("Open in Timeline", skin, "primary");
        open.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openInTimeline.accept(new AssetReference(animator.getClipPath()), entity);
            }
        });
        buttons.add(open).height(24).padRight(6);
        TextButton useSelected = new TextButton("Use Selected Clip", skin, "flat");
        useSelected.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetReference selected = editorState.getSelectedAsset();
                if (selected == null || AssetType.fromPath(selected.getPath()) != AssetType.ANIMATION) {
                    editorState.setStatusMessage("Select a .anim in the Content Browser first", true);
                    return;
                }
                editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), animator,
                    animator.getClipPath(), selected.getPath(), animator::setClipPath, "Change Animation Clip"));
            }
        });
        buttons.add(useSelected).height(24);
        container.add(buttons).left().padTop(4).padBottom(4).row();

        InspectorFieldRows.toggleRow(container, skin, "Play On Start", animator.isPlayOnStart(), value ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), animator,
                animator.isPlayOnStart(), value, animator::setPlayOnStart, "Change Play On Start")));
        InspectorFieldRows.enumRow(container, skin, "Wrap", animator.getWrapMode(), WrapMode.values(), value ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), animator,
                animator.getWrapMode(), value, animator::setWrapMode, "Change Wrap Mode")));
        InspectorFieldRows.floatRow(container, skin, "Speed", animator.getSpeed(), value -> {
            if (!Float.isFinite(value)) {
                editorState.setStatusMessage("Speed must be a number", true);
                return;
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), animator,
                animator.getSpeed(), value, animator::setSpeed, "Change Animation Speed"));
        });
    }
}
