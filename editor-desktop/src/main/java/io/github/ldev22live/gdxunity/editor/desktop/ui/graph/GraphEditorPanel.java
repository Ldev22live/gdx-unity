package io.github.ldev22live.gdxunity.editor.desktop.ui.graph;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphAssets;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphCommands;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphDocument;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphExamples;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphExecutionTrace;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSourceEmbed;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphVariable;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.ValueType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Graph tab (TD-029): a toolbar (New, Save, Undo, Redo, Add Variable, problem count) above the
 * {@link GraphCanvas}. Opening another graph saves the current one first, so edits are never lost.
 */
public class GraphEditorPanel extends Table implements Disposable {
    private final Skin skin;
    private final EditorState editorState;
    private final GraphAssets assets;
    private final NodeRegistry registry;
    private final EditorDialogService dialogs;
    private final File importDirectory;
    private final Runnable onAssetsChanged;
    private final Supplier<String> refreshNodes;
    private final GraphCanvas canvas;
    private final Label titleLabel;
    private final Label problemsLabel;
    private final ContextMenu variableMenu;
    private final GraphDocument.Listener documentListener = d -> refresh();

    /**
     * @param dialogs          used to choose the script file to import
     * @param importDirectory  folder the import dialog opens in
     * @param onAssetsChanged called after a graph file is created, so the Content Browser can rescan
     * @param refreshNodes    reads the project's classes again for the method nodes (TD-060); returns a line for the status bar
     */
    public GraphEditorPanel(Skin skin, EditorState editorState, GraphAssets assets, NodeRegistry registry,
                            GraphExecutionTrace trace, EditorDialogService dialogs, File importDirectory, Runnable onAssetsChanged,
                            Supplier<String> refreshNodes) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.assets = Objects.requireNonNull(assets, "assets cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs cannot be null");
        this.importDirectory = Objects.requireNonNull(importDirectory, "importDirectory cannot be null");
        this.onAssetsChanged = Objects.requireNonNull(onAssetsChanged, "onAssetsChanged cannot be null");
        this.refreshNodes = Objects.requireNonNull(refreshNodes, "refreshNodes cannot be null");
        this.canvas = new GraphCanvas(skin, editorState, trace);
        this.variableMenu = new ContextMenu(skin);
        setBackground("panel-bg");
        top().left();

        Table bar = new Table(skin);
        bar.setBackground("subheader-bg");
        bar.left();
        titleLabel = new Label("No graph open. Create one, or double-click a .graph in the Content Browser.", skin, "muted");
        bar.add(button("New Graph", this::newGraph)).height(24).padLeft(4);
        bar.add(button("Save", this::save)).height(24);
        bar.add(button("Undo", () -> withDocument(GraphDocument::undo))).height(24);
        bar.add(button("Redo", () -> withDocument(GraphDocument::redo))).height(24);
        TextButton addVariable = new TextButton("Add Variable", skin, "flat");
        addVariable.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showAddVariable(addVariable);
            }
        });
        bar.add(addVariable).height(24);
        bar.add(button("Generate Java", () -> generate(GraphCodeGenerator.Language.JAVA))).height(24);
        bar.add(button("Generate Kotlin", () -> generate(GraphCodeGenerator.Language.KOTLIN))).height(24);
        bar.add(button("Generate TypeScript", this::generateTypeScript)).height(24);
        bar.add(button("Import Script", this::importScript)).height(24);
        TextButton examples = new TextButton("Examples", skin, "flat");
        examples.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showExamples(examples);
            }
        });
        bar.add(examples).height(24);
        bar.add(button("Pure/Action", this::switchSelectedKind)).height(24);
        bar.add(button("Refresh Nodes", () -> editorState.setStatusMessage("Method nodes: " + this.refreshNodes.get(), false))).height(24);
        bar.add(titleLabel).padLeft(12).left().expandX();
        problemsLabel = new Label("", skin, "muted");
        bar.add(problemsLabel).padRight(8);
        add(bar).growX().height(30).row();
        add(canvas).grow();
    }

    public GraphDocument getDocument() {
        return canvas.getDocument();
    }

    /** Opens the graph at {@code asset}, saving the graph currently open first. */
    public boolean open(AssetReference asset) {
        Objects.requireNonNull(asset, "asset cannot be null");
        if (!saveIfDirty()) {
            return false;
        }
        try {
            GraphDocument document = new GraphDocument(asset, assets.load(asset), registry);
            GraphDocument previous = canvas.getDocument();
            if (previous != null) {
                previous.removeListener(documentListener);
            }
            document.addListener(documentListener);
            canvas.setDocument(document);
            refresh();
            editorState.setStatusMessage("Opened graph " + asset.getPath(), false);
            return true;
        } catch (IOException | SceneSerializationException e) {
            editorState.setStatusMessage("Cannot open graph " + asset.getPath() + ": " + e.getMessage(), true);
            return false;
        }
    }

    /** Creates a new graph asset named after the selected entity (or "Graph") and opens it. */
    public AssetReference newGraph() {
        String name = editorState.getSelectedEntity() != null ? editorState.getSelectedEntity().getName() : "Graph";
        try {
            AssetReference asset = assets.create(name);
            onAssetsChanged.run();
            open(asset);
            return asset;
        } catch (IOException e) {
            editorState.setStatusMessage("Cannot create graph: " + e.getMessage(), true);
            return null;
        }
    }

    public void save() {
        withDocument(document -> {
            try {
                document.save(assets);
                List<GraphValidator.Problem> problems = document.validate();
                editorState.setStatusMessage("Saved graph " + document.getAsset().getPath()
                    + (problems.isEmpty() ? "" : " (" + problems.size() + " problem(s): " + problems.get(0).message() + ")"),
                    !problems.isEmpty());
            } catch (IOException e) {
                editorState.setStatusMessage("Cannot save graph: " + e.getMessage(), true);
            }
        });
    }

    /**
     * Writes a Java or Kotlin script with the same behaviour as the open graph to {@code scripts/generated}, for
     * programmers who want to carry on in code (TD-044). The graph is saved first.
     */
    public void generate(GraphCodeGenerator.Language language) {
        withDocument(document -> {
            try {
                document.save(assets);
                AssetReference script = assets.exportScript(document.getGraph(), registry, language);
                onAssetsChanged.run();
                editorState.setStatusMessage("Generated " + script.getPath() + ": bind " + GraphCodeGenerator.PACKAGE + "."
                    + GraphCodeGenerator.className(document.getGraph()) + " with an Entity Script Binding", false);
            } catch (IllegalArgumentException e) {
                editorState.setStatusMessage("Cannot generate code: " + e.getMessage(), true);
            } catch (IOException e) {
                editorState.setStatusMessage("Cannot write the script: " + e.getMessage(), true);
            }
        });
    }

    /**
     * Writes a TypeScript script with the same behaviour as the open graph to {@code scripts/web}, to copy into the web
     * project (TD-045). The graph is saved first.
     */
    public void generateTypeScript() {
        withDocument(document -> {
            try {
                document.save(assets);
                AssetReference script = assets.exportTypeScript(document.getGraph(), registry);
                onAssetsChanged.run();
                editorState.setStatusMessage("Generated " + script.getPath() + ": copy it into the web project's src/scripts", false);
            } catch (IllegalArgumentException e) {
                editorState.setStatusMessage("Cannot generate code: " + e.getMessage(), true);
            } catch (IOException e) {
                editorState.setStatusMessage("Cannot write the script: " + e.getMessage(), true);
            }
        });
    }

    /**
     * Asks for a Java, Kotlin or TypeScript script that Forgeroom generated (here or in the web editor) and opens the
     * graph embedded in it as a new graph asset (TD-045). Code edits made after generating are not read back.
     */
    public void importScript() {
        Optional<File> chosen = dialogs.chooseOpenFile("Import script", importDirectory,
            "Scripts generated by Forgeroom (.java, .kt, .ts)", "*");
        chosen.ifPresent(this::importScript);
    }

    /** Imports the graph embedded in {@code script} and opens it; the status line says why it failed. */
    public boolean importScript(File script) {
        if (!saveIfDirty()) {
            return false;
        }
        try {
            GraphAssets.ImportResult imported = assets.importScriptChecked(script);
            AssetReference asset = imported.asset();
            onAssetsChanged.run();
            if (!open(asset)) {
                return false;
            }
            GraphSourceEmbed.Edit edit = imported.edit();
            editorState.setStatusMessage("Imported " + script.getName() + " as " + asset.getPath() + (edit == null ? ""
                : ". The code was edited after it was generated (first change at line " + edit.line() + ": " + edit.text()
                + "); edits to code are not read back, the graph is the one embedded"), edit != null);
            return true;
        } catch (IllegalArgumentException | SceneSerializationException e) {
            editorState.setStatusMessage("Cannot import " + script.getName() + ": " + e.getMessage(), true);
        } catch (IOException e) {
            editorState.setStatusMessage("Cannot read " + script.getName() + ": " + e.getMessage(), true);
        }
        return false;
    }

    /** Saves the open graph if it has changes; false when that failed. */
    public boolean saveIfDirty() {
        GraphDocument document = canvas.getDocument();
        if (document == null || !document.isDirty()) {
            return true;
        }
        try {
            document.save(assets);
            return true;
        } catch (IOException e) {
            editorState.setStatusMessage("Cannot save graph: " + e.getMessage(), true);
            return false;
        }
    }

    private void refresh() {
        GraphDocument document = canvas.getDocument();
        canvas.rebuild();
        if (document == null) {
            return;
        }
        titleLabel.setText(document.getGraph().getName() + (document.isDirty() ? " *" : "") + "   " + document.getAsset().getPath());
        List<GraphValidator.Problem> problems = document.validate();
        problemsLabel.setText(problems.isEmpty() ? "No problems" : problems.size() + " problem(s): " + problems.get(0).message());
        problemsLabel.setColor(problems.isEmpty() ? skin.getColor("text-muted") : skin.getColor("error"));
    }

    private void showAddVariable(Actor anchor) {
        GraphDocument document = canvas.getDocument();
        if (document == null || getStage() == null) {
            editorState.setStatusMessage("Open a graph first", true);
            return;
        }
        Table popup = new Table(skin);
        TextField name = new TextField("", skin);
        name.setMessageText("Variable name");
        SelectBox<ValueType> type = new SelectBox<>(skin);
        type.setItems(ValueType.FLOAT, ValueType.INTEGER, ValueType.BOOLEAN, ValueType.STRING, ValueType.VECTOR3);
        TextButton add = new TextButton("Add", skin, "primary");
        add.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String text = name.getText().trim();
                if (text.isEmpty()) {
                    editorState.setStatusMessage("A variable needs a name", true);
                    return;
                }
                variableMenu.close();
                if (!document.execute(new GraphCommands.AddVariable(document.getGraph(), new GraphVariable(text, type.getSelected(), null)))) {
                    editorState.setStatusMessage("Variable " + text + " already exists", true);
                }
            }
        });
        popup.add(name).width(160).pad(4);
        popup.add(type).width(100).pad(4);
        popup.add(add).height(24).pad(4);
        Vector2 at = anchor.localToStageCoordinates(new Vector2(0f, 0f));
        variableMenu.showContent(getStage(), at.x, at.y, popup);
        getStage().setKeyboardFocus(name);
    }

    /** Lists the project's example graphs (TD-060): what each mirrors, covers and leaves out, and opens the one chosen. */
    private void showExamples(Actor anchor) {
        if (getStage() == null) {
            return;
        }
        List<GraphExamples.Example> examples;
        try {
            examples = GraphExamples.load(importDirectory);
        } catch (IOException | IllegalArgumentException e) {
            editorState.setStatusMessage("Cannot read the example list: " + e.getMessage(), true);
            return;
        }
        if (examples.isEmpty()) {
            editorState.setStatusMessage("This project has no example graphs (" + GraphExamples.FILE + ")", false);
            return;
        }
        Table list = new Table(skin);
        list.top().left();
        for (GraphExamples.Example example : examples) {
            TextButton open = new TextButton(example.title(), skin, "flat");
            open.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    variableMenu.close();
                    open(example.asset());
                }
            });
            Label description = new Label("Mirrors " + example.mirrors() + "\nCovers: " + example.covers()
                + "\nLeft out: " + example.notCovered(), skin, "muted");
            description.setWrap(true);
            list.add(open).left().width(190).height(24).padTop(6).row();
            list.add(description).left().growX().width(560).padBottom(2).row();
        }
        ScrollPane scroll = new ScrollPane(list, skin);
        scroll.setFadeScrollBars(false);
        Table popup = new Table(skin);
        popup.add(scroll).width(600).height(420);
        Vector2 at = anchor.localToStageCoordinates(new Vector2(0f, 0f));
        variableMenu.showContent(getStage(), at.x, at.y, popup);
    }

    /** Switches the selected method nodes between Pure (read when a pin needs it) and Action (run once in the chain); one undo step each. */
    private void switchSelectedKind() {
        GraphDocument document = canvas.getDocument();
        if (document == null) {
            editorState.setStatusMessage("No graph open", true);
            return;
        }
        int switched = 0;
        for (String id : canvas.getSelectedNodeIds()) {
            if (GraphCommands.SwitchMethodKind.canSwitch(document.getGraph(), registry, id)
                && document.execute(new GraphCommands.SwitchMethodKind(document.getGraph(), registry, id))) {
                switched++;
            }
        }
        editorState.setStatusMessage(switched > 0 ? "Switched " + switched + " node(s) between Pure and Action"
            : "Select a Java method node that can be Pure or Action (a getter, say) first", switched == 0);
    }

    private void withDocument(Consumer<GraphDocument> action) {
        GraphDocument document = canvas.getDocument();
        if (document == null) {
            editorState.setStatusMessage("No graph open", true);
            return;
        }
        action.accept(document);
    }

    private TextButton button(String text, Runnable action) {
        TextButton button = new TextButton(text, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    @Override
    public void dispose() {
        canvas.dispose();
    }
}
