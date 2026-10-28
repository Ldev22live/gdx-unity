package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Hierarchy panel displaying scene entities in a structured hierarchy tree.
 * Styled with clear parent-child indentation, selection highlights, and accessible controls.
 */
public class HierarchyPanel extends Table {
    private final EditorState editorState;
    private final Skin skin;

    private final Table listTable;
    private final ScrollPane scrollPane;
    private final Label countLabel;
    private String searchFilter = "";

    private final com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop dragAndDrop;

    public HierarchyPanel(EditorState editorState, Skin skin) {
        super(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.dragAndDrop = new com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop();
        this.dragAndDrop.setDragActorPosition(0, 0);

        setBackground("panel-bg");
        top().left();

        // 1. Header bar
        Table headerBar = new Table(skin);
        headerBar.setBackground("subheader-bg");
        headerBar.left();

        Label titleLabel = new Label("Hierarchy", skin);
        if (skin.has("text-primary", Color.class)) {
            titleLabel.setColor(skin.getColor("text-primary"));
        } else {
            titleLabel.setColor(new Color(0.92f, 0.92f, 0.95f, 1f));
        }
        headerBar.add(titleLabel).padLeft(8).left();

        this.countLabel = new Label("", skin, "muted");
        headerBar.add(countLabel).left().padLeft(4);

        // Search/Filter box
        com.badlogic.gdx.scenes.scene2d.ui.TextField searchField = new com.badlogic.gdx.scenes.scene2d.ui.TextField("", skin);
        searchField.setMessageText("Filter entities...");
        searchField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                searchFilter = searchField.getText().trim().toLowerCase();
                refresh();
            }
        });
        headerBar.add(searchField).width(110).minWidth(80).height(22).padLeft(6).padRight(4);

        headerBar.add(new Table()).expandX();

        TextButton addBtn = new TextButton("+ Create", skin);
        addBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.createEntity("New Entity");
            }
        });
        headerBar.add(addBtn).height(22).padRight(3);

        TextButton dupBtn = new TextButton("Duplicate", skin);
        dupBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.duplicateSelectedEntity();
            }
        });
        headerBar.add(dupBtn).height(22).padRight(3);

        TextButton delBtn = new TextButton("- Delete", skin);
        delBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.deleteSelectedEntity();
            }
        });
        headerBar.add(delBtn).height(22).padRight(6);

        add(headerBar).growX().height(30).row();

        // 2. Scrollable list table
        this.listTable = new Table(skin);
        this.listTable.top().left();
        this.scrollPane = new ScrollPane(listTable, skin);
        this.scrollPane.setFadeScrollBars(false);
        add(scrollPane).grow().pad(4).row();

        // Root drop target on list background / scrollPane to allow unparenting to scene root
        this.dragAndDrop.addTarget(new com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target(listTable) {
            @Override
            public boolean drag(com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source source,
                                com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload,
                                float x, float y, int pointer) {
                if (payload == null || payload.getObject() == null) return false;
                UUID draggedId = (UUID) payload.getObject();
                Scene scene = editorState.getActiveScene();
                if (scene == null) return false;
                Entity dragged = scene.findEntityById(draggedId);
                // Valid if entity is currently parented (can be unparented to root)
                return dragged != null && dragged.getTransform().getParent() != null;
            }

            @Override
            public void reset(com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source source,
                              com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload) {
            }

            @Override
            public void drop(com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source source,
                             com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload,
                             float x, float y, int pointer) {
                if (payload != null && payload.getObject() instanceof UUID draggedId) {
                    editorState.reparentEntity(draggedId, null, false);
                }
            }
        });

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
        });

        refresh();
    }

    public void refresh() {
        dragAndDrop.clear();

        // Re-register listTable as root target after clear
        dragAndDrop.addTarget(new com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target(listTable) {
            @Override
            public boolean drag(com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source source,
                                com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload,
                                float x, float y, int pointer) {
                if (payload == null || payload.getObject() == null) return false;
                UUID draggedId = (UUID) payload.getObject();
                Scene scene = editorState.getActiveScene();
                if (scene == null) return false;
                Entity dragged = scene.findEntityById(draggedId);
                return dragged != null && dragged.getTransform().getParent() != null;
            }

            @Override
            public void reset(com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source source,
                              com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload) {
            }

            @Override
            public void drop(com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source source,
                             com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload,
                             float x, float y, int pointer) {
                if (payload != null && payload.getObject() instanceof UUID draggedId) {
                    editorState.reparentEntity(draggedId, null, false);
                }
            }
        });

        listTable.clear();

        Scene scene = editorState.getActiveScene();
        if (scene == null || scene.getEntityCount() == 0) {
            countLabel.setText("(0)");
            Label empty = new Label("(Scene is empty)", skin, "muted");
            listTable.add(empty).pad(12).left().row();
            return;
        }

        countLabel.setText("(" + scene.getEntityCount() + ")");
        List<Entity> roots = scene.getRootEntities();
        for (Entity root : roots) {
            buildHierarchyRow(root, 0);
        }
    }

    private void buildHierarchyRow(Entity entity, int depth) {
        boolean matchesFilter = searchFilter.isEmpty() || entity.getName().toLowerCase().contains(searchFilter);
        if (!matchesFilter && depth == 0 && !hasMatchingDescendant(entity)) {
            return;
        }

        Table row = new Table(skin);
        row.left();

        // Clean ASCII indentation prefix
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            prefix.append("   ");
        }
        if (depth > 0) {
            prefix.append("|- ");
        }

        boolean isSelected = editorState.isSelected(entity.getId());
        boolean isPrimary = Objects.equals(editorState.getSelectedEntityId(), entity.getId());
        String activeBadge = entity.isActiveSelf() ? "[*] " : "[ ] ";
        String labelText = prefix + activeBadge + entity.getName();

        TextButton itemBtn = new TextButton(labelText, skin, "flat");
        itemBtn.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);

        Color normalColor;
        Color accentColor = skin.has("accent", Color.class) ? skin.getColor("accent") : new Color(0.24f, 0.49f, 0.90f, 1f);
        Color accentActiveColor = skin.has("accent-active", Color.class) ? skin.getColor("accent-active") : new Color(0.35f, 0.60f, 0.85f, 1f);
        Color textMutedColor = skin.has("text-muted", Color.class) ? skin.getColor("text-muted") : new Color(0.55f, 0.55f, 0.58f, 1f);
        Color textPrimaryColor = skin.has("text-primary", Color.class) ? skin.getColor("text-primary") : Color.WHITE;

        if (isPrimary) {
            normalColor = accentColor;
        } else if (isSelected) {
            normalColor = accentActiveColor;
        } else if (!entity.isActiveSelf()) {
            normalColor = textMutedColor;
        } else {
            normalColor = textPrimaryColor;
        }
        itemBtn.setColor(normalColor);

        // Click selection listener
        itemBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean shiftOrCtrl = com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                    || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT)
                    || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)
                    || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);

                if (shiftOrCtrl) {
                    if (editorState.isSelected(entity.getId())) {
                        editorState.removeSelectedEntityId(entity.getId());
                    } else {
                        editorState.addSelectedEntityId(entity.getId());
                    }
                } else {
                    editorState.setSelectedEntity(entity);
                }
            }
        });

        // Register drag source on entity button
        dragAndDrop.addSource(new com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source(itemBtn) {
            @Override
            public com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload dragStart(
                com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer) {
                com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload =
                    new com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload();
                payload.setObject(entity.getId());

                // Visual drag feedback actor
                Label dragLabel = new Label(" Move: " + entity.getName(), skin, "default");
                dragLabel.setColor(new Color(0.4f, 0.8f, 1.0f, 0.9f));
                payload.setDragActor(dragLabel);

                Label validLabel = new Label(" Drop into: (Target)", skin, "default");
                validLabel.setColor(new Color(0.3f, 0.9f, 0.4f, 0.9f));
                payload.setValidDragActor(validLabel);

                Label invalidLabel = new Label(" Invalid Drop Target", skin, "default");
                invalidLabel.setColor(new Color(0.9f, 0.3f, 0.3f, 0.9f));
                payload.setInvalidDragActor(invalidLabel);

                return payload;
            }
        });

        // Register drop target on entity button
        dragAndDrop.addTarget(new com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target(itemBtn) {
            @Override
            public boolean drag(com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source source,
                                com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload,
                                float x, float y, int pointer) {
                if (payload == null || payload.getObject() == null) return false;
                UUID draggedId = (UUID) payload.getObject();
                // Cannot parent to self
                if (entity.getId().equals(draggedId)) {
                    itemBtn.setColor(0.8f, 0.3f, 0.3f, 1f);
                    return false;
                }

                Scene scene = editorState.getActiveScene();
                if (scene == null) return false;
                Entity draggedEntity = scene.findEntityById(draggedId);
                if (draggedEntity == null) return false;

                // Cannot parent to a descendant (cycle prevention)
                if (entity.getTransform().isDescendantOf(draggedEntity.getTransform())) {
                    itemBtn.setColor(0.8f, 0.3f, 0.3f, 1f);
                    return false;
                }

                // Cannot parent to current parent (no-op)
                Transform currentParent = draggedEntity.getTransform().getParent();
                if (currentParent != null && entity.getId().equals(currentParent.getEntity().getId())) {
                    return false;
                }

                // Valid target highlight
                itemBtn.setColor(0.2f, 0.8f, 0.4f, 1f);
                return true;
            }

            @Override
            public void reset(com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source source,
                              com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload) {
                itemBtn.setColor(normalColor);
            }

            @Override
            public void drop(com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source source,
                             com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload payload,
                             float x, float y, int pointer) {
                if (payload != null && payload.getObject() instanceof UUID draggedId) {
                    editorState.reparentEntity(draggedId, entity.getId(), false);
                }
            }
        });

        row.add(itemBtn).growX().height(24).left().pad(1, 2, 1, 2);
        listTable.add(row).growX().padBottom(1).row();

        // Recursive traversal for children
        List<Transform> children = entity.getTransform().getChildren();
        for (Transform childTransform : children) {
            buildHierarchyRow(childTransform.getEntity(), depth + 1);
        }
    }

    private boolean hasMatchingDescendant(Entity entity) {
        if (searchFilter.isEmpty()) return true;
        for (Transform childTransform : entity.getTransform().getChildren()) {
            Entity child = childTransform.getEntity();
            if (child.getName().toLowerCase().contains(searchFilter) || hasMatchingDescendant(child)) {
                return true;
            }
        }
        return false;
    }
}
