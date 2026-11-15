package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.core.material.MaterialAssets;
import io.github.ldev22live.gdxunity.editor.core.material.MaterialAssignment;
import io.github.ldev22live.gdxunity.editor.core.material.ModifyMaterialPropertyCommand;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.material.Material;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.io.File;

/**
 * Details editor for {@link MeshPrimitiveComponent}: shape, size and color, each change undoable. With a material library it also
 * offers the material (TD-081): the {@code .material} the surface uses, buttons to use the one selected in the Content Browser or
 * to make a new one, and the material's own parameters, edited in place. A material is shared, so editing it changes every
 * surface that uses it, and the file is saved with each change.
 */
public class MeshPrimitiveComponentEditorProvider implements ComponentEditorProvider<MeshPrimitiveComponent, Table> {
    private final MaterialLibrary materials;
    private final File assetRoot;

    /** An editor without materials: shape, size and color only. */
    public MeshPrimitiveComponentEditorProvider() {
        this(null, null);
    }

    /**
     * @param materials the project's materials, or null for none
     * @param assetRoot the project's asset folder, where new materials are created
     */
    public MeshPrimitiveComponentEditorProvider(MaterialLibrary materials, File assetRoot) {
        this.materials = materials;
        this.assetRoot = assetRoot;
    }

    @Override
    public Class<MeshPrimitiveComponent> getComponentClass() {
        return MeshPrimitiveComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Mesh Primitive";
    }

    @Override
    public void buildEditor(Table container, Entity entity, MeshPrimitiveComponent primitive, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();

        Table shapeRow = new Table(skin);
        shapeRow.left();
        shapeRow.add(new Label("Shape", skin, "muted")).width(80).left();
        SelectBox<MeshPrimitiveComponent.Shape> shapeSelect = new SelectBox<>(skin);
        shapeSelect.setItems(MeshPrimitiveComponent.Shape.values());
        shapeSelect.setSelected(primitive.getShape());
        shapeSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                MeshPrimitiveComponent.Shape selected = shapeSelect.getSelected();
                if (selected != primitive.getShape()) {
                    editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), primitive,
                        primitive.getShape(), selected, primitive::setShape, "Change Mesh Shape"));
                }
            }
        });
        shapeRow.add(shapeSelect).growX().height(22);
        container.add(shapeRow).growX().padTop(2).padBottom(2).row();

        InspectorFieldRows.vector3Row(container, skin, "Size", primitive.getSize(), size -> {
            if (size.x <= 0f || size.y <= 0f || size.z <= 0f) {
                editorState.setStatusMessage("Mesh size must be positive", true);
                return;
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<MeshPrimitiveComponent, Vector3>(
                editorState, entity.getId(), primitive, primitive.getSize(), size, primitive::setSize, "Resize Mesh"));
        });
        InspectorFieldRows.colorRow(container, skin, "Color (RGB)", primitive.getColor(), color ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<MeshPrimitiveComponent, Color>(
                editorState, entity.getId(), primitive, primitive.getColor(), color, primitive::setColor, "Change Mesh Color")));
        if (materials != null) {
            buildMaterialSection(container, entity, primitive, editorState, skin);
        }
    }

    private void buildMaterialSection(Table container, Entity entity, MeshPrimitiveComponent primitive, EditorState editorState, Skin skin) {
        container.add(new Label("Material", skin)).left().padTop(8).row();
        InspectorFieldRows.textRow(container, skin, "File", primitive.getMaterialPath(), path -> {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                if (!MaterialAssets.isMaterialPath(trimmed)) {
                    throw new IllegalArgumentException("A material is a " + MaterialAssets.EXTENSION + " file: " + trimmed);
                }
                if (materials.get(trimmed) == null) {
                    throw new IllegalArgumentException(materials.getError(trimmed));
                }
            }
            assign(entity, primitive, editorState, trimmed);
        });
        Table buttons = new Table(skin);
        TextButton useSelected = new TextButton("Use Selected Material", skin, "flat");
        useSelected.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetReference selected = editorState.getSelectedAsset();
                if (selected == null || AssetType.fromPath(selected.getPath()) != AssetType.MATERIAL) {
                    editorState.setStatusMessage("Select a .material in the Content Browser first", true);
                } else if (materials.get(selected.getPath()) == null) {
                    editorState.setStatusMessage(materials.getError(selected.getPath()), true);
                } else {
                    assign(entity, primitive, editorState, selected.getPath());
                }
            }
        });
        buttons.add(useSelected).height(24).padRight(6);
        TextButton create = new TextButton("New Material", skin, "primary");
        create.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                assign(entity, primitive, editorState, MaterialAssets.createNew(assetRoot, materials, "Material"));
            }
        });
        buttons.add(create).height(24).padRight(6);
        TextButton clear = new TextButton("No Material", skin, "flat");
        clear.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                assign(entity, primitive, editorState, "");
            }
        });
        buttons.add(clear).height(24);
        container.add(buttons).left().padTop(4).row();

        String path = primitive.getMaterialPath();
        if (path.isEmpty()) {
            container.add(new Label("No material: the color above is used.", skin, "muted")).left().padLeft(2).row();
            return;
        }
        Material material = materials.get(path);
        if (material == null) {
            container.add(new Label(materials.getError(path), skin, "muted")).left().padLeft(2).row();
            return;
        }
        container.add(new Label("Shared by every surface that uses " + path + ". Its base color replaces the color above.",
            skin, "muted")).left().padLeft(2).row();
        buildMaterialRows(container, editorState, skin, path, material);
    }

    /** The same step as a material dropped from the Content Browser (roadmap M2.2). */
    private void assign(Entity entity, MeshPrimitiveComponent primitive, EditorState editorState, String path) {
        if (!path.equals(primitive.getMaterialPath())) {
            MaterialAssignment.assign(editorState, materials, entity, path);
        }
    }

    /** One undoable, saved change of a material property. */
    private <V> void change(EditorState editorState, String path, Material material, V oldValue, V newValue,
                            java.util.function.Consumer<V> setter, String description) {
        editorState.executeCommand(new ModifyMaterialPropertyCommand<V>(editorState, materials, path, material, oldValue, newValue,
            setter, description));
    }

    private void buildMaterialRows(Table container, EditorState editorState, Skin skin, String path, Material m) {
        InspectorFieldRows.colorRow(container, skin, "Base Color", m.getBaseColor(), color ->
            change(editorState, path, m, m.getBaseColor(), color, m::setBaseColor, "Change Material Color"));
        InspectorFieldRows.floatRow(container, skin, "Opacity", m.getBaseColor().a, opacity -> {
            Color old = m.getBaseColor();
            Color next = new Color(old.r, old.g, old.b, opacity);
            change(editorState, path, m, old, next, m::setBaseColor, "Change Material Opacity");
        });
        InspectorFieldRows.floatRow(container, skin, "Metallic", m.getMetallic(), value ->
            change(editorState, path, m, m.getMetallic(), value, m::setMetallic, "Change Material Metallic"));
        InspectorFieldRows.floatRow(container, skin, "Roughness", m.getRoughness(), value ->
            change(editorState, path, m, m.getRoughness(), value, m::setRoughness, "Change Material Roughness"));
        InspectorFieldRows.colorRow(container, skin, "Emissive", m.getEmissive(), color ->
            change(editorState, path, m, m.getEmissive(), color, m::setEmissive, "Change Material Emissive"));
        InspectorFieldRows.floatRow(container, skin, "Glow", m.getEmissiveStrength(), value ->
            change(editorState, path, m, m.getEmissiveStrength(), value, m::setEmissiveStrength, "Change Material Emissive Strength"));
        InspectorFieldRows.enumRow(container, skin, "Alpha", m.getAlphaMode(), Material.AlphaMode.values(), mode ->
            change(editorState, path, m, m.getAlphaMode(), mode, m::setAlphaMode, "Change Material Alpha Mode"));
        InspectorFieldRows.floatRow(container, skin, "Cutoff", m.getAlphaCutoff(), value ->
            change(editorState, path, m, m.getAlphaCutoff(), value, m::setAlphaCutoff, "Change Material Alpha Cutoff"));
        InspectorFieldRows.toggleRow(container, skin, "2 Sided", m.isDoubleSided(), value ->
            change(editorState, path, m, m.isDoubleSided(), value, m::setDoubleSided, "Change Material Double Sided"));
        buildTextureRows(container, editorState, skin, path, m);
    }

    /** Short row labels of the texture slots; the Details column is narrow. */
    private static String slotLabel(Material.Slot slot) {
        return switch (slot) {
            case BASE_COLOR -> "Color Tex";
            case NORMAL -> "Normal Tex";
            case METALLIC_ROUGHNESS -> "Metal Tex";
            case OCCLUSION -> "AO Tex";
            case EMISSIVE -> "Glow Tex";
        };
    }

    /**
     * Texture slots as image paths (a png or jpg of the project, empty for none), how the textures repeat, and how strongly the normal
     * and occlusion textures act. Textures are drawn on every shape; a box shows one copy per face, a sphere wraps once.
     */
    private void buildTextureRows(Table container, EditorState editorState, Skin skin, String path, Material m) {
        container.add(new Label("Textures (a png or jpg of the project; Color Tex is multiplied with the base color)", skin, "muted"))
            .left().padLeft(2).padTop(4).row();
        for (Material.Slot slot : Material.Slot.values()) {
            InspectorFieldRows.textRow(container, skin, slotLabel(slot), m.getTexture(slot), value -> {
                String trimmed = value.trim();
                if (!trimmed.isEmpty() && AssetType.fromPath(trimmed) != AssetType.TEXTURE) {
                    throw new IllegalArgumentException("A texture is a png or jpg file: " + trimmed);
                }
                change(editorState, path, m, m.getTexture(slot), trimmed, text -> m.setTexture(slot, text),
                    "Change Material " + slotLabel(slot));
            });
        }
        TextButton useSelected = new TextButton("Use Selected Image as Color Tex", skin, "flat");
        useSelected.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetReference selected = editorState.getSelectedAsset();
                if (selected == null || AssetType.fromPath(selected.getPath()) != AssetType.TEXTURE) {
                    editorState.setStatusMessage("Select a png or jpg in the Content Browser first", true);
                } else {
                    change(editorState, path, m, m.getTexture(Material.Slot.BASE_COLOR), selected.getPath(),
                        text -> m.setTexture(Material.Slot.BASE_COLOR, text), "Change Material Color Tex");
                }
            }
        });
        container.add(useSelected).left().height(24).padTop(2).row();
        InspectorFieldRows.floatRow(container, skin, "Tile U", m.getTilingU(), value -> change(editorState, path, m, m.getTilingU(), value,
            u -> m.setTiling(u, m.getTilingV()), "Change Material Tiling"));
        InspectorFieldRows.floatRow(container, skin, "Tile V", m.getTilingV(), value -> change(editorState, path, m, m.getTilingV(), value,
            v -> m.setTiling(m.getTilingU(), v), "Change Material Tiling"));
        InspectorFieldRows.floatRow(container, skin, "Offset U", m.getOffsetU(), value -> change(editorState, path, m, m.getOffsetU(),
            value, u -> m.setOffset(u, m.getOffsetV()), "Change Material Offset"));
        InspectorFieldRows.floatRow(container, skin, "Offset V", m.getOffsetV(), value -> change(editorState, path, m, m.getOffsetV(),
            value, v -> m.setOffset(m.getOffsetU(), v), "Change Material Offset"));
        InspectorFieldRows.floatRow(container, skin, "Normal", m.getNormalScale(), value ->
            change(editorState, path, m, m.getNormalScale(), value, m::setNormalScale, "Change Material Normal Scale"));
        InspectorFieldRows.floatRow(container, skin, "AO", m.getOcclusionStrength(), value ->
            change(editorState, path, m, m.getOcclusionStrength(), value, m::setOcclusionStrength, "Change Material Occlusion"));
    }
}
