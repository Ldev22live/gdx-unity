package io.github.ldev22live.gdxunity.editor.desktop.ui.graph;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphDocument;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphSignatures;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphEventDef;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphFunction;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphParam;
import io.github.ldev22live.gdxunity.engine.visualscript.ValueType;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Functions and Events pop-ups of the Graph tab (TD-070): lists of the graph's functions or declared custom events, with a name
 * and type field to add them and their inputs, outputs and parameters. Every change is one undoable command; clicking a parameter
 * removes it. The content rebuilds itself after each change.
 */
final class SignaturePopup {
    private static final float POPUP_WIDTH = 520f;
    private static final float POPUP_HEIGHT = 380f;
    private static final float ROW_HEIGHT = 24f;
    private static final float TYPE_WIDTH = 110f;

    private final Skin skin;
    private final Supplier<GraphDocument> document;
    private final Consumer<String> error;
    private final Runnable closeAndRefresh;
    private final boolean functions;
    private final Table content;
    private final TextField name;
    private final SelectBox<ValueType> type;

    private SignaturePopup(Skin skin, Supplier<GraphDocument> document, Consumer<String> error, Runnable closeAndRefresh, boolean functions) {
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.document = Objects.requireNonNull(document, "document cannot be null");
        this.error = Objects.requireNonNull(error, "error cannot be null");
        this.closeAndRefresh = Objects.requireNonNull(closeAndRefresh, "closeAndRefresh cannot be null");
        this.functions = functions;
        this.content = new Table(skin);
        this.name = new TextField("", skin);
        this.name.setMessageText(functions ? "Function name, or parameter name" : "Event name, or parameter name");
        this.type = new SelectBox<>(skin);
        this.type.setItems(ValueType.FLOAT, ValueType.INTEGER, ValueType.BOOLEAN, ValueType.STRING, ValueType.VECTOR3, ValueType.ENTITY);
    }

    static SignaturePopup forFunctions(Skin skin, Supplier<GraphDocument> document, Consumer<String> error, Runnable closeAndRefresh) {
        return new SignaturePopup(skin, document, error, closeAndRefresh, true);
    }

    static SignaturePopup forEvents(Skin skin, Supplier<GraphDocument> document, Consumer<String> error, Runnable closeAndRefresh) {
        return new SignaturePopup(skin, document, error, closeAndRefresh, false);
    }

    TextField nameField() {
        return name;
    }

    /** The pop-up's table, filled; it refills after every change. */
    Table build() {
        Table popup = new Table(skin);
        Table top = new Table(skin);
        top.add(name).growX().pad(4);
        top.add(type).width(TYPE_WIDTH).pad(4);
        top.add(button(functions ? "New Function" : "New Event", this::addNamed)).height(ROW_HEIGHT).pad(4);
        popup.add(top).growX().row();
        popup.add(new Label(functions
            ? "Type a name and a type, then New Function, or + Input / + Output on a function. Click a parameter to remove it."
            : "Type a name and a type, then New Event, or + Parameter on an event. Click a parameter to remove it.", skin, "muted"))
            .left().padLeft(6).row();
        ScrollPane scroll = new ScrollPane(content, skin);
        scroll.setFadeScrollBars(false);
        popup.add(scroll).width(POPUP_WIDTH).height(POPUP_HEIGHT).row();
        fill();
        return popup;
    }

    private void fill() {
        content.clear();
        content.top().left();
        GraphDocument doc = document.get();
        if (doc == null) {
            content.add(new Label("Open a graph first", skin, "muted")).left().pad(4);
            return;
        }
        if (functions) {
            if (doc.getGraph().getFunctions().isEmpty()) {
                content.add(new Label("No functions yet", skin, "muted")).left().pad(4).row();
            }
            for (GraphFunction function : doc.getGraph().getFunctions()) {
                functionRow(doc, function);
            }
        } else {
            if (doc.getGraph().getEvents().isEmpty()) {
                content.add(new Label("No declared events yet", skin, "muted")).left().pad(4).row();
            }
            for (GraphEventDef event : doc.getGraph().getEvents()) {
                eventRow(doc, event);
            }
        }
    }

    private void functionRow(GraphDocument doc, GraphFunction function) {
        Table header = new Table(skin);
        header.add(new Label(function.getName(), skin, "section")).left().padRight(8);
        header.add(button("Edit", () -> {
            doc.openFunction(function.getName());
            closeAndRefresh.run();
        })).height(ROW_HEIGHT).padRight(4);
        header.add(button("+ Input", () -> run(GraphSignatures.addInput(doc.getGraph(), function.getName(), param())))).height(ROW_HEIGHT).padRight(4);
        header.add(button("+ Output", () -> run(GraphSignatures.addOutput(doc.getGraph(), function.getName(), param())))).height(ROW_HEIGHT).padRight(4);
        header.add(button("Delete", () -> run(GraphSignatures.removeFunction(doc.getGraph(), function.getName())))).height(ROW_HEIGHT);
        content.add(header).left().padTop(6).row();
        Table params = new Table(skin);
        for (GraphParam input : function.getInputs()) {
            params.add(button("in " + input.name() + ": " + input.type(),
                () -> run(GraphSignatures.removeInput(doc.getGraph(), function.getName(), input.name())))).height(ROW_HEIGHT).padRight(4);
        }
        for (GraphParam output : function.getOutputs()) {
            params.add(button("out " + output.name() + ": " + output.type(),
                () -> run(GraphSignatures.removeOutput(doc.getGraph(), function.getName(), output.name())))).height(ROW_HEIGHT).padRight(4);
        }
        content.add(params).left().padLeft(12).row();
    }

    private void eventRow(GraphDocument doc, GraphEventDef event) {
        Table header = new Table(skin);
        header.add(new Label(event.getName(), skin, "section")).left().padRight(8);
        header.add(button("+ Parameter", () -> run(GraphSignatures.addEventParam(doc.getGraph(), event.getName(), param())))).height(ROW_HEIGHT).padRight(4);
        header.add(button("Delete", () -> run(GraphSignatures.removeEvent(doc.getGraph(), event.getName())))).height(ROW_HEIGHT);
        content.add(header).left().padTop(6).row();
        Table params = new Table(skin);
        for (GraphParam p : event.getParams()) {
            params.add(button(p.name() + ": " + p.type(),
                () -> run(GraphSignatures.removeEventParam(doc.getGraph(), event.getName(), p.name())))).height(ROW_HEIGHT).padRight(4);
        }
        content.add(params).left().padLeft(12).row();
    }

    private void addNamed() {
        GraphDocument doc = document.get();
        if (doc == null) {
            error.accept("Open a graph first");
            return;
        }
        String text = name.getText().trim();
        run(functions ? GraphSignatures.addFunction(doc.getGraph(), text) : GraphSignatures.addEvent(doc.getGraph(), text));
    }

    private GraphParam param() {
        return new GraphParam(name.getText().trim(), type.getSelected());
    }

    /** Runs a command made by the factories; a bad name is reported on the status line instead of thrown. */
    private void run(EditorCommand command) {
        document.get().execute(command);
        name.setText("");
        fill();
    }

    private TextButton button(String text, Runnable action) {
        TextButton button = new TextButton(text, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    action.run();
                } catch (IllegalArgumentException | IllegalStateException e) {
                    error.accept(e.getMessage());
                }
            }
        });
        return button;
    }
}
