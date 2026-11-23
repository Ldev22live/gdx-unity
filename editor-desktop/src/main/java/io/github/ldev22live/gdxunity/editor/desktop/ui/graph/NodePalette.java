package io.github.ldev22live.gdxunity.editor.desktop.ui.graph;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.Align;
import io.github.ldev22live.gdxunity.editor.visualscript.NodePaletteModel;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Side list of every node the open graph can use (TD-069): a search box above the nodes grouped by category. Drag a node onto the
 * {@link GraphCanvas} to add it where it is dropped; clicking a node adds it at the middle of the canvas, for a keyboard or a
 * trackpad. Without a search the categories start closed (the Java method nodes alone number in the thousands); a search lists the
 * matches of every category, at most {@value NodePaletteModel#MAX_SEARCH_RESULTS}.
 */
public class NodePalette extends Table {
    private static final float ROW_HEIGHT = 22f;

    /** What the palette hands the canvas while a node is dragged. */
    record Dragged(NodeType type) { }

    private final Skin skin;
    private final Supplier<NodeRegistry> registry;
    private final GraphCanvas canvas;
    private final DragAndDrop dragAndDrop = new DragAndDrop();
    private final TextField search;
    private final Table rows = new Table();
    private final Set<String> openCategories = new HashSet<>();

    /**
     * @param registry gives the registry to list on every refill, so nodes added by Refresh Nodes appear
     * @param canvas   the surface nodes are dropped on
     */
    public NodePalette(Skin skin, Supplier<NodeRegistry> registry, GraphCanvas canvas) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.canvas = Objects.requireNonNull(canvas, "canvas cannot be null");
        setBackground("panel-bg");
        top().left();
        search = new TextField("", skin);
        search.setMessageText("Search nodes...");
        search.setTextFieldListener((field, c) -> refill());
        add(search).growX().pad(4).row();
        add(new Label("Drag a node onto the graph", skin, "muted")).left().padLeft(6).row();
        rows.top().left();
        ScrollPane scroll = new ScrollPane(rows, skin);
        scroll.setFadeScrollBars(false);
        // Rows fill the panel's width and cut a long name with an ellipsis; sideways scrolling made them as wide as the longest name (TD-077)
        scroll.setScrollingDisabled(true, false);
        add(scroll).grow().pad(2).row();

        dragAndDrop.setDragActorPosition(0f, 0f);
        refill();
    }

    /** Sets the search text and lists the matches. */
    public void setSearch(String text) {
        search.setText(text);
        refill();
    }

    /** Lists the registry again for the current search; call after the registry changed. */
    public void refill() {
        rows.clear();
        dragAndDrop.clear();
        dragAndDrop.addTarget(dragTarget());
        String text = search.getText();
        boolean searching = !text.isBlank();
        NodePaletteModel.Listing listing = NodePaletteModel.list(registry.get(), text);
        Map<String, List<NodeType>> byCategory = listing.categories();
        if (byCategory.isEmpty()) {
            rows.add(new Label("No matching nodes", skin, "muted")).left().pad(4).row();
        }
        for (Map.Entry<String, List<NodeType>> category : byCategory.entrySet()) {
            boolean open = searching || openCategories.contains(category.getKey());
            TextButton header = new TextButton((open ? "- " : "+ ") + category.getKey() + " (" + category.getValue().size() + ")",
                skin, "flat");
            header.getLabel().setAlignment(Align.left);
            header.getLabel().setEllipsis(true);
            // The label takes the room the row has, whatever its text needs, so a long name is cut with an ellipsis
            header.getLabelCell().width(0f).growX();
            header.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (!openCategories.remove(category.getKey())) {
                        openCategories.add(category.getKey());
                    }
                    refill();
                }
            });
            rows.add(header).growX().height(ROW_HEIGHT).row();
            if (open) {
                for (NodeType type : category.getValue()) {
                    rows.add(nodeRow(type)).growX().height(ROW_HEIGHT).padLeft(10f).row();
                }
            }
        }
        if (listing.truncated()) {
            rows.add(new Label("Showing the first " + NodePaletteModel.MAX_SEARCH_RESULTS + " matches; search for more", skin, "muted")).left().pad(4).row();
        }
    }

    private DragAndDrop.Target dragTarget() {
        return new DragAndDrop.Target(canvas) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                return payload.getObject() instanceof Dragged && canvas.hasDocument();
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                Vector2 at = getActor().localToStageCoordinates(new Vector2(x, y));
                canvas.addNodeAt(((Dragged) payload.getObject()).type(), at.x, at.y);
            }
        };
    }

    private Actor nodeRow(NodeType type) {
        TextButton item = new TextButton(type.getTitle(), skin, "flat");
        item.getLabel().setAlignment(Align.left);
        item.getLabel().setEllipsis(true);
        item.getLabelCell().width(0f).growX();
        item.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                canvas.addNodeAtCenter(type);
            }
        });
        dragAndDrop.addSource(new DragAndDrop.Source(item) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                DragAndDrop.Payload payload = new DragAndDrop.Payload();
                payload.setObject(new Dragged(type));
                Label ghost = new Label(" " + type.getTitle() + " ", skin, "default");
                payload.setDragActor(ghost);
                payload.setValidDragActor(ghost);
                payload.setInvalidDragActor(new Label(" Open a graph first ", skin, "muted"));
                return payload;
            }
        });
        return item;
    }
}
