package io.github.ldev22live.gdxunity.editor.desktop.ui.graph;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Array;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphCommands;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphDocument;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphVariable;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeType;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One node on the {@link GraphCanvas}: a title bar to drag, settings (variable or event name), and rows of pins.
 * Unconnected data inputs show a field for typing a value. Every change is a command on the graph's history.
 */
class GraphNodeView extends Table {
    private static final float NUMBER_FIELD_WIDTH = 46f;
    private static final float TEXT_FIELD_WIDTH = 110f;
    private static final float FIELD_HEIGHT = 20f;
    private static final Color EVENT_TITLE = new Color(0.95f, 0.45f, 0.4f, 1f);
    private static final Color ACTION_TITLE = new Color(0.5f, 0.7f, 1f, 1f);
    private static final Color PURE_TITLE = new Color(0.6f, 0.9f, 0.55f, 1f);
    private static final String NO_VARIABLE = "(choose)";

    /** A pin's handle: drag from it to wire, right-click to break its wires. */
    static final class PinView extends Label {
        final String nodeId;
        final PinDef def;

        PinView(GraphCanvas canvas, Skin skin, String nodeId, PinDef def, boolean connected) {
            super(def.isExec() ? (connected ? ">>" : ">") : (connected ? "(*)" : "( )"), skin);
            this.nodeId = nodeId;
            this.def = def;
            setColor(GraphCanvas.colorOf(def));
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    if (button == Input.Buttons.RIGHT) {
                        canvas.breakWires(PinView.this);
                        return true;
                    }
                    if (button == Input.Buttons.LEFT) {
                        canvas.beginWire(PinView.this, event.getStageX(), event.getStageY());
                        return true;
                    }
                    return false;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    canvas.dragWire(event.getStageX(), event.getStageY());
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    if (button == Input.Buttons.LEFT) {
                        canvas.endWire(event.getStageX(), event.getStageY());
                    }
                }
            });
        }

        Vector2 stageCenter() {
            return localToStageCoordinates(new Vector2(getWidth() / 2f, getHeight() / 2f));
        }
    }

    private final GraphCanvas canvas;
    private final Skin skin;
    private final GraphDocument document;
    private final GraphNode node;
    private final Map<String, PinView> pins = new HashMap<>();

    GraphNodeView(GraphCanvas canvas, Skin skin, GraphDocument document, GraphNode node, NodeType type) {
        super(skin);
        this.canvas = canvas;
        this.skin = skin;
        this.document = document;
        this.node = node;
        setBackground("elevated-bg");
        top().left();
        buildTitle(type);
        if (type == null) {
            add(new Label("Unknown node " + node.getTypeId(), skin, "muted")).pad(6).row();
            return;
        }
        buildProperties(type);
        buildPins(type);
    }

    PinView pin(String pinId) {
        return pins.get(pinId);
    }

    private void buildTitle(NodeType type) {
        Table bar = new Table(skin);
        bar.setBackground("subheader-bg");
        String text = type == null ? node.getTypeId() : type.getTitle();
        Object variable = node.getProperty(BuiltInNodes.PROPERTY_VARIABLE);
        Object eventName = node.getProperty(BuiltInNodes.PROPERTY_NAME);
        if (variable != null) {
            text += "  " + variable;
        } else if (eventName != null) {
            text += "  " + eventName;
        }
        Label title = new Label(text, skin, "section");
        if (type != null) {
            title.setColor(switch (type.getKind()) {
                case EVENT -> EVENT_TITLE;
                case ACTION -> ACTION_TITLE;
                case PURE -> PURE_TITLE;
            });
        }
        bar.add(title).left().expandX().pad(2, 6, 2, 6);
        bar.addListener(new DragListener() {
            private float startX;
            private float startY;

            {
                setTapSquareSize(2f);
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == Input.Buttons.LEFT) {
                    boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                    canvas.select(node.getId(), shift);
                }
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void dragStart(InputEvent event, float x, float y, int pointer) {
                startX = GraphNodeView.this.getX();
                startY = GraphNodeView.this.getY();
            }

            @Override
            public void drag(InputEvent event, float x, float y, int pointer) {
                GraphNodeView.this.moveBy(getDeltaX(), getDeltaY());
            }

            @Override
            public void dragStop(InputEvent event, float x, float y, int pointer) {
                document.execute(new GraphCommands.MoveNode(node, startX, startY,
                    GraphNodeView.this.getX(), GraphNodeView.this.getY()));
            }
        });
        add(bar).growX().row();
    }

    private void buildProperties(NodeType type) {
        if (type.getProperties().containsKey(BuiltInNodes.PROPERTY_VARIABLE)) {
            Array<String> names = new Array<>();
            names.add(NO_VARIABLE);
            for (GraphVariable variable : document.getGraph().getVariables()) {
                names.add(variable.name());
            }
            SelectBox<String> select = new SelectBox<>(skin);
            select.setItems(names);
            Object current = node.getProperty(BuiltInNodes.PROPERTY_VARIABLE);
            select.setSelected(current instanceof String s && names.contains(s, false) ? s : NO_VARIABLE);
            select.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    String chosen = NO_VARIABLE.equals(select.getSelected()) ? null : select.getSelected();
                    document.execute(new GraphCommands.SetProperty(document.getGraph(), document.getRegistry(), node,
                        BuiltInNodes.PROPERTY_VARIABLE, chosen));
                }
            });
            add(select).growX().height(FIELD_HEIGHT).pad(2, 6, 2, 6).row();
        }
        if (type.getProperties().containsKey(BuiltInNodes.PROPERTY_NAME)) {
            Object current = node.getProperty(BuiltInNodes.PROPERTY_NAME);
            TextField field = committingField(current instanceof String s ? s : "", text ->
                document.execute(new GraphCommands.SetProperty(document.getGraph(), document.getRegistry(), node,
                    BuiltInNodes.PROPERTY_NAME, text.isBlank() ? null : text.trim())));
            field.setMessageText("Event name");
            add(field).growX().height(FIELD_HEIGHT).pad(2, 6, 2, 6).row();
        }
    }

    private void buildPins(NodeType type) {
        List<PinDef> all = type.pinsFor(node, document.getGraph());
        Table inputs = new Table(skin);
        inputs.top().left();
        Table outputs = new Table(skin);
        outputs.top().right();
        for (PinDef pin : all) {
            boolean connected = canvas.isConnected(node.getId(), pin);
            PinView handle = new PinView(canvas, skin, node.getId(), pin, connected);
            pins.put(pin.id(), handle);
            Table row = new Table(skin);
            if (pin.isInput()) {
                row.add(handle).padRight(4);
                if (!pin.label().isEmpty()) {
                    row.add(new Label(pin.label(), skin, "muted")).padRight(4);
                }
                if (!pin.isExec() && !connected && pin.type().hasLiteral()) {
                    row.add(literalEditor(pin));
                }
                inputs.add(row).left().pad(1, 4, 1, 4).row();
            } else {
                if (!pin.label().isEmpty()) {
                    row.add(new Label(pin.label(), skin, "muted")).padRight(4);
                }
                row.add(handle);
                outputs.add(row).right().pad(1, 4, 1, 4).row();
            }
        }
        Table body = new Table(skin);
        body.add(inputs).top().left().expandX();
        body.add(outputs).top().right().padLeft(12);
        add(body).growX().pad(2, 0, 4, 0).row();
    }

    private Actor literalEditor(PinDef pin) {
        Object literal = node.getLiteral(pin.id());
        Object value = literal != null ? literal : pin.type().defaultValue();
        switch (pin.type()) {
            case FLOAT -> {
                TextField field = committingField(format((Float) value), text -> setLiteral(pin, Float.parseFloat(text)));
                return sized(field, NUMBER_FIELD_WIDTH);
            }
            case INTEGER -> {
                TextField field = committingField(String.valueOf(value), text -> setLiteral(pin, Integer.parseInt(text)));
                return sized(field, NUMBER_FIELD_WIDTH);
            }
            case STRING -> {
                return sized(committingField((String) value, text -> setLiteral(pin, text)), TEXT_FIELD_WIDTH);
            }
            case BOOLEAN -> {
                boolean on = (Boolean) value;
                TextButton toggle = new TextButton(on ? "[x]" : "[ ]", skin, "flat");
                toggle.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        setLiteral(pin, !on);
                    }
                });
                return toggle;
            }
            case VECTOR3 -> {
                Vector3 v = (Vector3) value;
                Table fields = new Table(skin);
                fields.add(sized(committingField(format(v.x), t -> setLiteral(pin, new Vector3(Float.parseFloat(t), v.y, v.z))), NUMBER_FIELD_WIDTH)).padRight(1);
                fields.add(sized(committingField(format(v.y), t -> setLiteral(pin, new Vector3(v.x, Float.parseFloat(t), v.z))), NUMBER_FIELD_WIDTH)).padRight(1);
                fields.add(sized(committingField(format(v.z), t -> setLiteral(pin, new Vector3(v.x, v.y, Float.parseFloat(t)))), NUMBER_FIELD_WIDTH));
                return fields;
            }
            default -> {
                return new Label("", skin);
            }
        }
    }

    private void setLiteral(PinDef pin, Object value) {
        document.execute(new GraphCommands.SetLiteral(node, pin.id(), value));
    }

    private Table sized(Actor field, float width) {
        Table cell = new Table(skin);
        cell.add(field).width(width).height(FIELD_HEIGHT);
        return cell;
    }

    /**
     * Text field that commits on Enter or when it loses focus; text the commit rejects (not a number) restores
     * the previous value.
     */
    private TextField committingField(String initial, Consumer<String> onCommit) {
        TextField field = new TextField(initial, skin);
        Runnable commit = () -> {
            String text = field.getText().trim();
            if (text.equals(initial.trim())) {
                return;
            }
            try {
                onCommit.accept(text);
            } catch (IllegalArgumentException e) {
                // NumberFormatException: not a number
                field.setText(initial);
            }
        };
        field.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                commit.run();
            }
        });
        field.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) {
                    commit.run();
                }
            }
        });
        return field;
    }

    private static String format(float value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.3f", value);
    }
}
