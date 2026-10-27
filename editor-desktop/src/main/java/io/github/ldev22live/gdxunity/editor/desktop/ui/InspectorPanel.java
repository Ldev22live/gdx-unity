package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorRegistry;
import io.github.ldev22live.gdxunity.editor.core.inspector.EntityInspectorModel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.BoxCollider2DComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.CircleCollider2DComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.RigidBody2DComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.ScriptBindingComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.SpriteRendererComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Inspector panel displaying and editing properties of the currently selected entity.
 * Supports extensible {@link ComponentEditorRegistry}, multi-selection summary,
 * and clean Unity dark styling.
 */
public class InspectorPanel extends Table {
    private final EditorState editorState;
    private final Skin skin;
    private final ComponentEditorRegistry<Table> componentRegistry;

    private final Table contentTable;
    private final ScrollPane scrollPane;

    public InspectorPanel(EditorState editorState, Skin skin) {
        this(editorState, skin, createDefaultRegistry());
    }

    public InspectorPanel(EditorState editorState, Skin skin, ComponentEditorRegistry<Table> componentRegistry) {
        super(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.componentRegistry = Objects.requireNonNull(componentRegistry, "componentRegistry cannot be null");

        setBackground("panel-bg");
        top().left();

        // Header bar
        Table headerBar = new Table(skin);
        headerBar.setBackground("subheader-bg");
        headerBar.left();
        Label titleLabel = new Label("Inspector", skin);
        if (skin.has("text-primary", Color.class)) {
            titleLabel.setColor(skin.getColor("text-primary"));
        } else {
            titleLabel.setColor(new Color(0.92f, 0.92f, 0.95f, 1f));
        }
        headerBar.add(titleLabel).padLeft(8).left().expandX();
        add(headerBar).growX().height(26).row();

        this.contentTable = new Table(skin);
        this.contentTable.top().left();
        this.scrollPane = new ScrollPane(contentTable, skin);
        this.scrollPane.setFadeScrollBars(false);
        add(scrollPane).grow().pad(4).row();

        // Listen for editor state events
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onActiveSceneChanged(Scene oldScene, Scene newScene) {
                refresh();
            }

            @Override
            public void onSelectionChanged(UUID previousSelectedId, UUID newSelectedId) {
                refresh();
            }

            @Override
            public void onHierarchyModified() {
                refresh();
            }

            @Override
            public void onEntityTransformModified(Entity entity) {
                refresh();
            }
        });

        refresh();
    }

    public ComponentEditorRegistry<Table> getComponentRegistry() {
        return componentRegistry;
    }

    private static ComponentEditorRegistry<Table> createDefaultRegistry() {
        ComponentEditorRegistry<Table> reg = new ComponentEditorRegistry<>();
        reg.register(new SpriteRendererComponentEditorProvider());
        reg.register(new ScriptBindingComponentEditorProvider());
        reg.register(new RigidBody2DComponentEditorProvider());
        reg.register(new BoxCollider2DComponentEditorProvider());
        reg.register(new CircleCollider2DComponentEditorProvider());
        return reg;
    }

    public void refresh() {
        contentTable.clear();

        Set<UUID> selection = editorState.getSelectedEntityIds();
        if (selection.isEmpty()) {
            Label noSelection = new Label("No GameObject selected", skin, "muted");
            contentTable.add(noSelection).pad(16).left().row();
            return;
        }

        if (selection.size() > 1) {
            buildMultiSelectionView(selection);
            return;
        }

        Entity selected = editorState.getSelectedEntity();
        if (selected == null) {
            Label noSelection = new Label("No GameObject selected", skin, "muted");
            contentTable.add(noSelection).pad(16).left().row();
            return;
        }

        EntityInspectorModel inspector = new EntityInspectorModel(selected);

        // 1. Entity Identity Section
        Table identityTable = new Table(skin);
        identityTable.left();

        TextButton activeToggle = new TextButton(inspector.isActive() ? "[X]" : "[ ]", skin);
        activeToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean newActive = !selected.isActiveSelf();
                editorState.executeCommand(new io.github.ldev22live.gdxunity.editor.core.command.ChangeEntityActiveCommand(
                    editorState, selected.getId(), selected.isActiveSelf(), newActive
                ));
                refresh();
            }
        });
        identityTable.add(activeToggle).width(34).height(24).padRight(4);

        TextField nameField = new TextField(inspector.getName(), skin);
        nameField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String newName = nameField.getText();
                if (!Objects.equals(selected.getName(), newName)) {
                    editorState.executeCommand(new io.github.ldev22live.gdxunity.editor.core.command.RenameEntityCommand(
                        editorState, selected.getId(), selected.getName(), newName
                    ));
                }
            }
        });
        identityTable.add(nameField).growX().height(24);
        contentTable.add(identityTable).growX().pad(4).row();

        // UUID display
        String uuidStr = selected.getId().toString();
        Label idLabel = new Label("ID: " + uuidStr.substring(0, Math.min(uuidStr.length(), 18)) + "...", skin, "muted");
        contentTable.add(idLabel).left().padLeft(6).padBottom(6).row();

        // 2. Transform Section
        addSectionHeader("Transform");

        Vector3 pos = inspector.getLocalPosition();
        addVector3Row("Position", pos.x, pos.y, pos.z, (x, y, z) -> {
            io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot before =
                io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot.capture(selected.getTransform());
            inspector.setLocalPosition(x, y, z);
            io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot after =
                io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot.capture(selected.getTransform());
            editorState.getCommandHistory().execute(new io.github.ldev22live.gdxunity.editor.core.command.ChangeTransformCommand(
                editorState, selected.getId(), before, after, "Move Entity via Inspector"
            ));
        });

        Vector3 rot = inspector.getLocalEulerAngles();
        addVector3Row("Rotation", rot.x, rot.y, rot.z, (yaw, pitch, roll) -> {
            io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot before =
                io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot.capture(selected.getTransform());
            inspector.setLocalEulerAngles(yaw, pitch, roll);
            io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot after =
                io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot.capture(selected.getTransform());
            editorState.getCommandHistory().execute(new io.github.ldev22live.gdxunity.editor.core.command.ChangeTransformCommand(
                editorState, selected.getId(), before, after, "Rotate Entity via Inspector"
            ));
        });

        Vector3 scale = inspector.getLocalScale();
        addVector3Row("Scale", scale.x, scale.y, scale.z, (x, y, z) -> {
            io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot before =
                io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot.capture(selected.getTransform());
            inspector.setLocalScale(x, y, z);
            io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot after =
                io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot.capture(selected.getTransform());
            editorState.getCommandHistory().execute(new io.github.ldev22live.gdxunity.editor.core.command.ChangeTransformCommand(
                editorState, selected.getId(), before, after, "Scale Entity via Inspector"
            ));
        });

        // World Coordinates readout
        Vector3 wPos = inspector.getWorldPosition();
        Label wPosLabel = new Label(String.format("World: (%.1f, %.1f, %.1f)", wPos.x, wPos.y, wPos.z), skin, "muted");
        contentTable.add(wPosLabel).left().padLeft(6).padTop(2).padBottom(6).row();

        // 3. Extensible Component Sections from registry
        List<Component> components = selected.getAllComponents();
        for (Component comp : components) {
            renderRegisteredComponent(selected, comp);
        }

        // 4. Add Component button rows
        addSectionHeader("Add Component");
        Table addCompBar1 = new Table(skin);
        addCompBar1.left();
        TextButton addSpriteBtn = new TextButton("+ Sprite Renderer", skin);
        addSpriteBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                io.github.ldev22live.gdxunity.engine.core.asset.AssetReference selectedAsset = editorState.getSelectedAsset();
                SpriteRendererComponent newSprite = (selectedAsset != null)
                    ? new SpriteRendererComponent(selectedAsset, 100f, 100f)
                    : new SpriteRendererComponent(100f, 100f);
                editorState.executeCommand(new io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand(
                    editorState, selected.getId(), newSprite
                ));
                refresh();
            }
        });

        TextButton addScriptBtn = new TextButton("+ Script...", skin);
        addScriptBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager sm = editorState.getScriptManager();
                String defaultClass = "SamplePlayerScript";
                if (sm != null && !sm.getDiscoveredScriptClasses().isEmpty()) {
                    defaultClass = sm.getDiscoveredScriptClasses().keySet().iterator().next();
                }
                EntityScriptBindingComponent binding =
                    new EntityScriptBindingComponent(
                        "scripts/" + defaultClass + ".java",
                        defaultClass,
                        io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage.JAVA
                    );
                editorState.executeCommand(new io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand(
                    editorState, selected.getId(), binding
                ));
                refresh();
            }
        });

        addCompBar1.add(addSpriteBtn).height(24).pad(2);
        addCompBar1.add(addScriptBtn).height(24).pad(2);
        contentTable.add(addCompBar1).growX().padTop(4).row();

        Table addCompBar2 = new Table(skin);
        addCompBar2.left();
        TextButton addRbBtn = new TextButton("+ RigidBody 2D", skin);
        addRbBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (selected.getComponent(RigidBody2DComponent.class) == null) {
                    editorState.executeCommand(new io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand(
                        editorState, selected.getId(), new io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent()
                    ));
                    refresh();
                } else {
                    editorState.setStatusMessage("Entity already has a RigidBody2DComponent", true);
                }
            }
        });

        TextButton addBoxColBtn = new TextButton("+ Box Collider", skin);
        addBoxColBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.executeCommand(new io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand(
                    editorState, selected.getId(), new io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent(100f, 100f)
                ));
                refresh();
            }
        });

        TextButton addCircleColBtn = new TextButton("+ Circle Collider", skin);
        addCircleColBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.executeCommand(new io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand(
                    editorState, selected.getId(), new io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent(50f)
                ));
                refresh();
            }
        });

        addCompBar2.add(addRbBtn).height(24).pad(2);
        addCompBar2.add(addBoxColBtn).height(24).pad(2);
        addCompBar2.add(addCircleColBtn).height(24).pad(2);
        contentTable.add(addCompBar2).growX().padTop(2).padBottom(8).row();
    }

    @SuppressWarnings("unchecked")
    private <C extends Component> void renderRegisteredComponent(Entity entity, C comp) {
        Class<C> compClass = (Class<C>) comp.getClass();
        Optional<ComponentEditorProvider<C, Table>> providerOpt = componentRegistry.getProvider(compClass);
        if (providerOpt.isPresent()) {
            ComponentEditorProvider<C, Table> provider = providerOpt.get();
            addSectionHeader(provider.getDisplayName());
            Table compCard = new Table(skin);
            compCard.left();
            provider.buildEditor(compCard, entity, comp, editorState, skin);
            contentTable.add(compCard).growX().pad(4).row();
        }
    }

    private void buildMultiSelectionView(Set<UUID> selection) {
        Table multiTable = new Table(skin);
        multiTable.left();

        Label header = new Label("Multiple Objects Selected", skin);
        if (skin.has("text-primary", Color.class)) {
            header.setColor(skin.getColor("text-primary"));
        } else {
            header.setColor(new Color(0.92f, 0.92f, 0.95f, 1f));
        }
        multiTable.add(header).left().padBottom(6).row();

        Label countLabel = new Label(selection.size() + " GameObjects selected", skin, "muted");
        multiTable.add(countLabel).left().padBottom(12).row();

        TextButton deselectBtn = new TextButton("Clear Selection", skin);
        deselectBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.clearSelection();
            }
        });
        multiTable.add(deselectBtn).height(24).left().row();

        contentTable.add(multiTable).pad(16).left().row();
    }

    private void addSectionHeader(String title) {
        Table bar = new Table(skin);
        bar.setBackground("subheader-bg");
        bar.left();
        Label lbl = new Label(title, skin, "section");
        bar.add(lbl).padLeft(6).left();
        contentTable.add(bar).growX().height(22).padTop(8).padBottom(4).row();
    }

    @FunctionalInterface
    interface Vector3Consumer {
        void accept(float x, float y, float z);
    }

    private void addVector3Row(String labelText, float initX, float initY, float initZ, Vector3Consumer consumer) {
        Table row = new Table(skin);
        row.left();

        Label label = new Label(labelText, skin, "muted");
        row.add(label).width(55).left();

        TextField fX = new TextField(String.format("%.1f", initX), skin);
        TextField fY = new TextField(String.format("%.1f", initY), skin);
        TextField fZ = new TextField(String.format("%.1f", initZ), skin);

        ChangeListener changeListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    float x = Float.parseFloat(fX.getText().trim());
                    float y = Float.parseFloat(fY.getText().trim());
                    float z = Float.parseFloat(fZ.getText().trim());
                    consumer.accept(x, y, z);
                } catch (NumberFormatException ignored) {
                }
            }
        };

        fX.addListener(changeListener);
        fY.addListener(changeListener);
        fZ.addListener(changeListener);

        Label lblX = new Label("X", skin, "muted");
        Label lblY = new Label("Y", skin, "muted");
        Label lblZ = new Label("Z", skin, "muted");

        row.add(lblX).padLeft(2).padRight(2);
        row.add(fX).growX().height(22).pad(1);
        row.add(lblY).padLeft(4).padRight(2);
        row.add(fY).growX().height(22).pad(1);
        row.add(lblZ).padLeft(4).padRight(2);
        row.add(fZ).growX().height(22).pad(1);

        contentTable.add(row).growX().padTop(2).padBottom(2).row();
    }
}
