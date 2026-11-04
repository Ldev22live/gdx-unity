package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.command.RemoveComponentCommand;
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorRegistry;
import io.github.ldev22live.gdxunity.editor.core.inspector.EntityInspectorModel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.BoxCollider2DComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.CircleCollider2DComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.Physics3DComponentEditorProviders;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.RigidBody2DComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.ScriptBindingComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.SpriteRendererComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabState;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.HashSet;
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
    private final ComponentCatalog componentCatalog;
    private final ContextMenu addComponentMenu;
    /** Component class names whose sections are collapsed; kept across selection changes. */
    private final Set<String> collapsedSections = new HashSet<>();

    private final Table contentTable;
    private final ScrollPane scrollPane;

    public InspectorPanel(EditorState editorState, Skin skin) {
        this(editorState, skin, createDefaultRegistry());
    }

    public InspectorPanel(EditorState editorState, Skin skin, ComponentEditorRegistry<Table> componentRegistry) {
        this(editorState, skin, componentRegistry, ComponentCatalog.createDefault());
    }

    public InspectorPanel(EditorState editorState, Skin skin, ComponentEditorRegistry<Table> componentRegistry,
                          ComponentCatalog componentCatalog) {
        super(skin);
        this.componentCatalog = Objects.requireNonNull(componentCatalog, "componentCatalog cannot be null");
        this.addComponentMenu = new ContextMenu(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.componentRegistry = Objects.requireNonNull(componentRegistry, "componentRegistry cannot be null");

        setBackground("panel-bg");
        top().left();

        // Header bar
        Table headerBar = new Table(skin);
        headerBar.setBackground("subheader-bg");
        headerBar.left();
        Label titleLabel = new Label("Details", skin);
        if (skin.has("text-primary", Color.class)) {
            titleLabel.setColor(skin.getColor("text-primary"));
        } else {
            titleLabel.setColor(new Color(0.92f, 0.92f, 0.95f, 1f));
        }
        headerBar.add(titleLabel).padLeft(8).left().expandX();
        add(headerBar).growX().height(30).row();

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

    /** Details editors for the built-in components; the desktop shell adds editors for other modules. */
    public static ComponentEditorRegistry<Table> createDefaultRegistry() {
        ComponentEditorRegistry<Table> reg = new ComponentEditorRegistry<>();
        reg.register(new SpriteRendererComponentEditorProvider());
        reg.register(new ScriptBindingComponentEditorProvider());
        reg.register(new RigidBody2DComponentEditorProvider());
        reg.register(new BoxCollider2DComponentEditorProvider());
        reg.register(new CircleCollider2DComponentEditorProvider());
        reg.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.MeshPrimitiveComponentEditorProvider());
        reg.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.LightComponentEditorProviders.Directional());
        reg.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.LightComponentEditorProviders.Point());
        reg.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.LightComponentEditorProviders.Sky());
        reg.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.LightComponentEditorProviders.ToneMapping());
        reg.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.GameComponentEditorProviders.Camera());
        reg.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.GameComponentEditorProviders.Text());
        reg.register(new Physics3DComponentEditorProviders.RigidBody());
        reg.register(new Physics3DComponentEditorProviders.Box());
        reg.register(new Physics3DComponentEditorProviders.Sphere());
        reg.register(new Physics3DComponentEditorProviders.Capsule());
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

        PrefabService prefabs = editorState.getPrefabService();
        Set<String> overrides = prefabs != null ? prefabs.getOverrides(selected) : Set.of();
        if (prefabs != null && PrefabService.instanceRootOf(selected) != null) {
            addPrefabBar(prefabs, selected, overrides);
        }

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

        // 3. Component sections (collapsible, with enable toggle and Remove)
        List<Component> components = selected.getAllComponents();
        for (Component comp : components) {
            if (comp instanceof PrefabInstanceComponent) {
                continue; // shown as the prefab bar
            }
            renderComponentSection(selected, comp, overrides);
        }

        // 4. Searchable Add Component
        TextButton addComponentBtn = new TextButton("Add Component", skin, "primary");
        addComponentBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showAddComponentMenu(selected, addComponentBtn);
            }
        });
        contentTable.add(addComponentBtn).growX().height(28).pad(8).row();
    }

    private void showAddComponentMenu(Entity entity, Actor anchor) {
        Stage stage = getStage();
        if (stage == null) {
            return;
        }
        Table popup = new Table(skin);
        TextField search = new TextField("", skin);
        search.setMessageText("Search components...");
        Table results = new Table(skin);
        results.top().left();
        Runnable fillResults = () -> {
            results.clear();
            List<ComponentCatalog.Entry> matches = componentCatalog.search(search.getText());
            if (matches.isEmpty()) {
                results.add(new Label("No matching components", skin, "muted")).left().pad(4).row();
            }
            for (ComponentCatalog.Entry entry : matches) {
                TextButton item = new TextButton(entry.displayName() + "   (" + entry.category() + ")", skin, "flat");
                item.getLabel().setAlignment(Align.left);
                boolean allowed = componentCatalog.canAdd(entity, entry);
                item.setDisabled(!allowed);
                if (!allowed) {
                    item.getLabel().setColor(skin.getColor("text-muted"));
                }
                item.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        addComponentMenu.close();
                        componentCatalog.addTo(editorState, entity, entry);
                        refresh();
                    }
                });
                results.add(item).growX().height(24).row();
            }
        };
        search.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fillResults.run();
            }
        });
        fillResults.run();

        popup.add(search).growX().height(24).padBottom(4).row();
        popup.add(results).growX().row();
        Vector2 anchorPos = anchor.localToStageCoordinates(new Vector2(0f, 0f));
        addComponentMenu.showContent(stage, anchorPos.x, anchorPos.y, popup);
        stage.setKeyboardFocus(search);
    }

    @SuppressWarnings("unchecked")
    /** Prefab asset, override count and Apply / Revert / Unpack for an entity of a prefab instance (TD-027). */
    private void addPrefabBar(PrefabService prefabs, Entity selected, Set<String> overrides) {
        Table bar = new Table(skin);
        bar.setBackground("subheader-bg");
        bar.left();
        PrefabInstanceComponent link = PrefabService.linkOf(selected);
        Label title = new Label("Prefab  " + link.getPrefabPath(), skin, "section");
        title.setColor(HierarchyPanel.PREFAB_TEXT_COLOR);
        bar.add(title).padLeft(6).left().expandX();
        bar.add(prefabButton("Apply", () -> prefabs.applyAll(selected))).height(22);
        bar.add(prefabButton("Revert", () -> prefabs.revertAll(selected))).height(22);
        bar.add(prefabButton("Unpack", () -> prefabs.unpack(selected))).height(22).padRight(4);
        contentTable.add(bar).growX().height(24).padBottom(2).row();

        int instanceOverrides = prefabs.countInstanceOverrides(selected);
        String summary = instanceOverrides == 0 ? "Matches the prefab"
            : instanceOverrides + " overridden " + (instanceOverrides == 1 ? "property" : "properties") + " in this instance"
                + (overrides.isEmpty() ? "" : " (" + overrides.size() + " on this entity)");
        contentTable.add(new Label(summary, skin, "muted")).left().padLeft(6).padBottom(6).row();
    }

    private TextButton prefabButton(String text, Runnable action) {
        TextButton button = new TextButton(text, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
                refresh();
            }
        });
        return button;
    }

    private <C extends Component> void renderComponentSection(Entity entity, C comp, Set<String> overrides) {
        Class<C> compClass = (Class<C>) comp.getClass();
        Optional<ComponentEditorProvider<C, Table>> providerOpt = componentRegistry.getProvider(compClass);
        String title = providerOpt.map(ComponentEditorProvider::getDisplayName).orElse(displayNameOf(compClass));
        String sectionKey = compClass.getName();
        boolean collapsed = collapsedSections.contains(sectionKey);

        Table bar = new Table(skin);
        bar.setBackground("subheader-bg");
        bar.left();

        TextButton collapseToggle = new TextButton(collapsed ? "+" : "-", skin, "flat");
        collapseToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!collapsedSections.remove(sectionKey)) {
                    collapsedSections.add(sectionKey);
                }
                refresh();
            }
        });
        bar.add(collapseToggle).width(22).height(22);

        TextButton enabledToggle = new TextButton(comp.isEnabled() ? "[x]" : "[ ]", skin, "flat");
        enabledToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean oldValue = comp.isEnabled();
                editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), comp,
                    oldValue, !oldValue, comp::setEnabled, (oldValue ? "Disable " : "Enable ") + title));
                refresh();
            }
        });
        bar.add(enabledToggle).width(30).height(22);
        String componentKey = overrides.isEmpty() ? null
            : PrefabState.componentKey(editorState.getSerializer(), entity, comp);
        boolean overridden = componentKey != null
            && overrides.stream().anyMatch(p -> componentKey.equals(PrefabState.componentKeyOf(p)));
        Label titleLabel = new Label(overridden ? title + "  (overridden)" : title, skin, "section");
        if (overridden) {
            titleLabel.setColor(HierarchyPanel.PREFAB_TEXT_COLOR);
        }
        bar.add(titleLabel).padLeft(4).left().expandX();
        if (overridden) {
            bar.add(prefabButton("Revert", () -> editorState.getPrefabService().revertComponent(entity, comp))).height(22);
        }

        TextButton removeButton = new TextButton("Remove", skin, "flat");
        removeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.executeCommand(new RemoveComponentCommand(editorState, entity.getId(), comp));
                refresh();
            }
        });
        bar.add(removeButton).height(22).padRight(4);
        contentTable.add(bar).growX().height(24).padTop(8).padBottom(4).row();

        if (collapsed) {
            return;
        }
        Table compCard = new Table(skin);
        compCard.left();
        if (providerOpt.isPresent()) {
            providerOpt.get().buildEditor(compCard, entity, comp, editorState, skin);
        } else {
            compCard.add(new Label("No editable properties", skin, "muted")).left().padLeft(6);
        }
        contentTable.add(compCard).growX().pad(4).row();
    }

    /** "ShapeRendererComponent" -> "Shape Renderer". */
    static String displayNameOf(Class<?> componentClass) {
        String name = componentClass.getSimpleName();
        if (name.endsWith("Component") && name.length() > "Component".length()) {
            name = name.substring(0, name.length() - "Component".length());
        }
        return name.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
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
