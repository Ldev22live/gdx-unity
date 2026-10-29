package io.github.ldev22live.gdxunity.editor.desktop.ui.graph;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphCommands;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphDocument;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphExecutionTrace;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeType;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import io.github.ldev22live.gdxunity.engine.visualscript.ValueType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The graph editing surface (TD-029). Nodes are {@link GraphNodeView}s inside a pannable, zoomable group; wires
 * are drawn underneath as curves coloured by value type (white for execution).
 * <ul>
 *   <li>Right-click empty space: searchable node menu. Right or middle drag: pan. Wheel: zoom.</li>
 *   <li>Drag a node's title: move. Click: select (Shift adds). Delete: remove the selection.</li>
 *   <li>Drag from a pin to another pin: wire (incompatible pins are refused with a reason). Right-click a pin:
 *       break its wires.</li>
 *   <li>Ctrl+Z / Ctrl+Y: this graph's own undo history.</li>
 * </ul>
 * Nodes that ran during the last moments of play are outlined in green.
 */
public class GraphCanvas extends WidgetGroup implements Disposable {
    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 2f;
    private static final float ZOOM_STEP = 1.1f;
    /** Mouse travel below which a right-button press counts as a click (opens the node menu). */
    private static final float CLICK_SLOP = 4f;
    /** Horizontal reach of a wire's curve handles, in canvas units. */
    private static final float WIRE_TANGENT = 80f;
    private static final int WIRE_SEGMENTS = 24;
    /** How long a node stays outlined after it ran. */
    private static final float EXECUTION_GLOW_SECONDS = 0.3f;
    private static final float OUTLINE_GAP = 3f;

    static final Color EXEC_COLOR = new Color(0.95f, 0.95f, 0.95f, 1f);
    private static final Color SELECTED_COLOR = new Color(1f, 0.62f, 0.12f, 1f);
    private static final Color EXECUTED_COLOR = new Color(0.35f, 0.9f, 0.4f, 1f);
    private static final Color PENDING_WIRE_COLOR = new Color(1f, 1f, 1f, 0.6f);

    private final Skin skin;
    private final EditorState editorState;
    private final GraphExecutionTrace trace;
    private final Group content = new Group();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final ContextMenu nodeMenu;
    private final Map<String, GraphNodeView> views = new LinkedHashMap<>();
    private final Set<String> selection = new LinkedHashSet<>();
    private GraphDocument document;
    private float zoom = 1f;
    /** A newly opened graph is centred once the canvas has a size. */
    private boolean centerPending;

    private GraphNodeView.PinView wireStart;
    private final Vector2 wireEnd = new Vector2();

    public GraphCanvas(Skin skin, EditorState editorState, GraphExecutionTrace trace) {
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.trace = Objects.requireNonNull(trace, "trace cannot be null");
        this.nodeMenu = new ContextMenu(skin);
        content.setTransform(true);
        addActor(content);
        addListener(new CanvasInput());
    }

    public GraphDocument getDocument() {
        return document;
    }

    public void setDocument(GraphDocument document) {
        this.document = document;
        selection.clear();
        zoom = 1f;
        content.setScale(1f);
        centerPending = true;
        invalidate();
        rebuild();
    }

    /** Recreates the node views from the graph; called after every edit. */
    public void rebuild() {
        content.clearChildren();
        views.clear();
        if (document == null) {
            return;
        }
        selection.removeIf(id -> document.getGraph().getNode(id) == null);
        for (GraphNode node : document.getGraph().getNodes()) {
            NodeType type = document.getRegistry().get(node.getTypeId());
            GraphNodeView view = new GraphNodeView(this, skin, document, node, type);
            view.pack();
            view.setPosition(node.getX(), node.getY());
            content.addActor(view);
            views.put(node.getId(), view);
        }
    }

    // --- Called by node views ---

    void select(String nodeId, boolean add) {
        if (!add) {
            selection.clear();
        }
        selection.add(nodeId);
        getStage().setKeyboardFocus(this);
    }

    void beginWire(GraphNodeView.PinView pin, float stageX, float stageY) {
        wireStart = pin;
        wireEnd.set(stageX, stageY);
    }

    void dragWire(float stageX, float stageY) {
        wireEnd.set(stageX, stageY);
    }

    void endWire(float stageX, float stageY) {
        GraphNodeView.PinView start = wireStart;
        wireStart = null;
        if (start == null || getStage() == null) {
            return;
        }
        GraphNodeView.PinView target = pinAt(getStage().hit(stageX, stageY, true));
        if (target == null || target == start) {
            return;
        }
        GraphNodeView.PinView from = start.def.isInput() ? target : start;
        GraphNodeView.PinView to = start.def.isInput() ? start : target;
        GraphCommands.Connect connect = new GraphCommands.Connect(document.getGraph(), document.getRegistry(),
            new GraphLink(from.nodeId, from.def.id(), to.nodeId, to.def.id()));
        if (!document.execute(connect) && connect.getRefusal() != null) {
            editorState.setStatusMessage("Cannot connect: " + connect.getRefusal(), true);
        }
    }

    void breakWires(GraphNodeView.PinView pin) {
        List<GraphLink> links = pin.def.isInput()
            ? document.getGraph().linksInto(pin.nodeId, pin.def.id())
            : document.getGraph().linksFrom(pin.nodeId, pin.def.id());
        if (!links.isEmpty()) {
            document.execute(new GraphCommands.Disconnect(document.getGraph(), links));
        }
    }

    boolean isConnected(String nodeId, PinDef pin) {
        return pin.isInput() ? !document.getGraph().linksInto(nodeId, pin.id()).isEmpty()
            : !document.getGraph().linksFrom(nodeId, pin.id()).isEmpty();
    }

    void deleteSelection() {
        if (document != null && !selection.isEmpty()) {
            document.execute(new GraphCommands.RemoveNodes(document.getGraph(), new ArrayList<>(selection)));
            selection.clear();
        }
    }

    private static GraphNodeView.PinView pinAt(Actor actor) {
        for (Actor a = actor; a != null; a = a.getParent()) {
            if (a instanceof GraphNodeView.PinView pin) {
                return pin;
            }
        }
        return null;
    }

    // --- Drawing ---

    @Override
    public void layout() {
        // Node views size themselves; the content group is positioned by panning
        if (centerPending && getWidth() > 0f) {
            content.setPosition(getWidth() / 4f, getHeight() / 2f);
            centerPending = false;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        if (!clipBegin()) {
            return;
        }
        batch.end();
        shapes.setProjectionMatrix(getStage().getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (document != null) {
            drawOutlines();
            for (GraphLink link : document.getGraph().getLinks()) {
                GraphNodeView from = views.get(link.fromNode());
                GraphNodeView to = views.get(link.toNode());
                GraphNodeView.PinView fromPin = from != null ? from.pin(link.fromPin()) : null;
                GraphNodeView.PinView toPin = to != null ? to.pin(link.toPin()) : null;
                if (fromPin != null && toPin != null) {
                    shapes.setColor(colorOf(fromPin.def));
                    drawWire(fromPin.stageCenter(), toPin.stageCenter());
                }
            }
            if (wireStart != null) {
                shapes.setColor(PENDING_WIRE_COLOR);
                Vector2 start = wireStart.stageCenter();
                if (wireStart.def.isInput()) {
                    drawWire(new Vector2(wireEnd), start);
                } else {
                    drawWire(start, new Vector2(wireEnd));
                }
            }
        }
        shapes.end();
        batch.begin();
        super.draw(batch, parentAlpha);
        batch.flush();
        clipEnd();
    }

    private void drawOutlines() {
        String path = document.getAsset().getPath();
        for (Map.Entry<String, GraphNodeView> entry : views.entrySet()) {
            boolean selected = selection.contains(entry.getKey());
            boolean ran = trace.ranWithin(path, entry.getKey(), EXECUTION_GLOW_SECONDS);
            if (!selected && !ran) {
                continue;
            }
            GraphNodeView view = entry.getValue();
            Vector2 min = view.localToStageCoordinates(new Vector2(-OUTLINE_GAP, -OUTLINE_GAP));
            Vector2 max = view.localToStageCoordinates(new Vector2(view.getWidth() + OUTLINE_GAP, view.getHeight() + OUTLINE_GAP));
            shapes.setColor(ran ? EXECUTED_COLOR : SELECTED_COLOR);
            shapes.rect(min.x, min.y, max.x - min.x, max.y - min.y);
        }
    }

    private void drawWire(Vector2 from, Vector2 to) {
        float tangent = WIRE_TANGENT * zoom;
        shapes.curve(from.x, from.y, from.x + tangent, from.y, to.x - tangent, to.y, to.x, to.y, WIRE_SEGMENTS);
    }

    static Color colorOf(PinDef pin) {
        if (pin.isExec()) {
            return EXEC_COLOR;
        }
        return colorOf(pin.type());
    }

    static Color colorOf(ValueType type) {
        return switch (type) {
            case FLOAT -> new Color(0.55f, 0.9f, 0.45f, 1f);
            case BOOLEAN -> new Color(0.9f, 0.3f, 0.3f, 1f);
            case STRING -> new Color(0.95f, 0.45f, 0.85f, 1f);
            case VECTOR3 -> new Color(0.98f, 0.82f, 0.25f, 1f);
            case ENTITY -> new Color(0.35f, 0.65f, 1f, 1f);
        };
    }

    // --- Node menu ---

    private void showNodeMenu(float stageX, float stageY) {
        if (document == null || getStage() == null) {
            return;
        }
        Vector2 at = content.stageToLocalCoordinates(new Vector2(stageX, stageY));
        Table popup = new Table(skin);
        TextField search = new TextField("", skin);
        search.setMessageText("Search nodes...");
        Table results = new Table(skin);
        results.top().left();
        Runnable fill = () -> {
            results.clear();
            List<NodeType> matches = document.getRegistry().search(search.getText());
            if (matches.isEmpty()) {
                results.add(new Label("No matching nodes", skin, "muted")).left().pad(4).row();
            }
            for (NodeType type : matches) {
                TextButton item = new TextButton(type.getTitle() + "   (" + type.getCategory() + ")", skin, "flat");
                item.getLabel().setAlignment(Align.left);
                item.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        nodeMenu.close();
                        GraphNode node = document.addNode(type, at.x, at.y);
                        if (node != null) {
                            selection.clear();
                            selection.add(node.getId());
                        }
                    }
                });
                results.add(item).growX().height(24).row();
            }
        };
        search.setTextFieldListener((field, c) -> fill.run());
        fill.run();
        popup.add(search).growX().pad(4).row();
        ScrollPane scroll = new ScrollPane(results, skin);
        scroll.setFadeScrollBars(false);
        popup.add(scroll).width(280).height(320).row();
        nodeMenu.showContent(getStage(), stageX, stageY, popup);
        getStage().setKeyboardFocus(search);
    }

    // --- Input ---

    private final class CanvasInput extends InputListener {
        private final Vector2 pressedAt = new Vector2();
        private final Vector2 lastDrag = new Vector2();
        private int panButton = -1;
        private boolean moved;

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (pinAt(event.getTarget()) != null) {
                return false;
            }
            if (getStage() != null) {
                getStage().setKeyboardFocus(GraphCanvas.this);
                getStage().setScrollFocus(GraphCanvas.this);
            }
            if (button == Input.Buttons.RIGHT || button == Input.Buttons.MIDDLE) {
                panButton = button;
                moved = false;
                pressedAt.set(event.getStageX(), event.getStageY());
                lastDrag.set(pressedAt);
                return true;
            }
            if (button == Input.Buttons.LEFT && (event.getTarget() == GraphCanvas.this || event.getTarget() == content)) {
                selection.clear();
            }
            return false;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (panButton < 0) {
                return;
            }
            if (pressedAt.dst(event.getStageX(), event.getStageY()) > CLICK_SLOP) {
                moved = true;
            }
            content.moveBy(event.getStageX() - lastDrag.x, event.getStageY() - lastDrag.y);
            lastDrag.set(event.getStageX(), event.getStageY());
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (button == panButton && button == Input.Buttons.RIGHT && !moved) {
                showNodeMenu(event.getStageX(), event.getStageY());
            }
            panButton = -1;
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
            float newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (amountY > 0 ? 1f / ZOOM_STEP : ZOOM_STEP)));
            // Keep the point under the mouse in place
            Vector2 before = content.stageToLocalCoordinates(new Vector2(event.getStageX(), event.getStageY()));
            zoom = newZoom;
            content.setScale(zoom);
            Vector2 after = content.localToStageCoordinates(new Vector2(before));
            content.moveBy(event.getStageX() - after.x, event.getStageY() - after.y);
            return true;
        }

        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            if (document == null || event.getTarget() != GraphCanvas.this) {
                return false;
            }
            boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (keycode == Input.Keys.FORWARD_DEL || keycode == Input.Keys.DEL) {
                deleteSelection();
                return true;
            }
            if (ctrl && keycode == Input.Keys.Z) {
                document.undo();
                return true;
            }
            if (ctrl && keycode == Input.Keys.Y) {
                document.redo();
                return true;
            }
            return false;
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
