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
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Details editor for a {@link SpriteAnimatorComponent} (TD-066, milestone D): the {@code .sprite} it plays (assigned from the Content Browser), the tag that
 * plays first, speed, looping, play on start, flipping, drawn size and tint. Every change is an undoable command.
 */
public class SpriteAnimatorComponentEditorProvider implements ComponentEditorProvider<SpriteAnimatorComponent, Table> {
    @Override
    public Class<SpriteAnimatorComponent> getComponentClass() {
        return SpriteAnimatorComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Sprite Animator";
    }

    @Override
    public void buildEditor(Table container, Entity entity, SpriteAnimatorComponent animator, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        AssetReference asset = animator.getAssetReference();
        container.add(new Label("Sprite: " + (asset != null ? asset.getPath() : "[None]"), skin)).left().pad(2).row();
        if (animator.getLoadError() != null) {
            container.add(new Label(animator.getLoadError(), skin, "muted")).left().pad(2).row();
        } else if (animator.getDocument() != null) {
            String tags = animator.getDocument().getTags().stream().map(t -> t.name()).reduce((a, b) -> a + ", " + b).orElse("none");
            container.add(new Label(animator.getDocument().getFrameCount() + " frame(s), tags: " + tags, skin, "muted")).left().pad(2).row();
        }
        TextButton assign = new TextButton("Assign Selected Sprite", skin);
        assign.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetReference selected = editorState.getSelectedAsset();
                if (selected == null || AssetType.fromPath(selected.getPath()) != AssetType.SPRITE) {
                    editorState.setStatusMessage("Select a .sprite file in the Content Browser first", true);
                    return;
                }
                editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, AssetReference>(
                    editorState, entity.getId(), animator, animator.getAssetReference(), selected, animator::setAssetReference, "Assign Sprite"));
            }
        });
        container.add(assign).height(24).pad(4).left().row();
        InspectorFieldRows.textRow(container, skin, "Tag (blank: all)", animator.getTag() == null ? "" : animator.getTag(), text ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, String>(
                editorState, entity.getId(), animator, animator.getTag(), text.trim().isEmpty() ? null : text.trim(), animator::setTag,
                "Change Sprite Tag")));
        InspectorFieldRows.textRow(container, skin, "9-slice (slice name)", animator.getSlice() == null ? "" : animator.getSlice(), text ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, String>(
                editorState, entity.getId(), animator, animator.getSlice(), text.trim().isEmpty() ? null : text.trim(), animator::setSlice,
                "Change Sprite Slice")));
        InspectorFieldRows.floatRow(container, skin, "Speed", animator.getSpeed(), speed -> {
            if (!(speed >= 0f)) {
                throw new IllegalArgumentException("Speed cannot be negative: " + speed);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, Float>(
                editorState, entity.getId(), animator, animator.getSpeed(), speed, animator::setSpeed, "Change Sprite Speed"));
        });
        InspectorFieldRows.toggleRow(container, skin, "Loop", animator.isLoop(), loop ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, Boolean>(
                editorState, entity.getId(), animator, animator.isLoop(), loop, animator::setLoop, "Change Sprite Loop")));
        InspectorFieldRows.toggleRow(container, skin, "Play On Start", animator.isPlayOnStart(), play ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, Boolean>(
                editorState, entity.getId(), animator, animator.isPlayOnStart(), play, animator::setPlayOnStart, "Change Sprite Play On Start")));
        InspectorFieldRows.toggleRow(container, skin, "Flip X", animator.isFlipX(), flip ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, Boolean>(
                editorState, entity.getId(), animator, animator.isFlipX(), flip, animator::setFlipX, "Change Sprite Flip X")));
        InspectorFieldRows.toggleRow(container, skin, "Flip Y", animator.isFlipY(), flip ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, Boolean>(
                editorState, entity.getId(), animator, animator.isFlipY(), flip, animator::setFlipY, "Change Sprite Flip Y")));
        InspectorFieldRows.floatRow(container, skin, "Width (0: sprite size)", animator.getWidth(), width -> {
            if (!(width >= 0f)) {
                throw new IllegalArgumentException("Width cannot be negative: " + width);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, Float>(
                editorState, entity.getId(), animator, animator.getWidth(), width, animator::setWidth, "Change Sprite Width"));
        });
        InspectorFieldRows.floatRow(container, skin, "Height (0: sprite size)", animator.getHeight(), height -> {
            if (!(height >= 0f)) {
                throw new IllegalArgumentException("Height cannot be negative: " + height);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, Float>(
                editorState, entity.getId(), animator, animator.getHeight(), height, animator::setHeight, "Change Sprite Height"));
        });
        InspectorFieldRows.colorRow(container, skin, "Tint", animator.getTint(), tint ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<SpriteAnimatorComponent, com.badlogic.gdx.graphics.Color>(
                editorState, entity.getId(), animator, new com.badlogic.gdx.graphics.Color(animator.getTint()), tint, animator::setTint,
                "Change Sprite Tint")));
    }
}
