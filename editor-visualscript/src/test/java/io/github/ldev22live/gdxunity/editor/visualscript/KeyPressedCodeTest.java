package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.scripting.CompilationResult;
import io.github.ldev22live.gdxunity.editor.core.scripting.JavaScriptCompiler;
import io.github.ldev22live.gdxunity.editor.core.scripting.KotlinScriptCompiler;
import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptClassLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator.Language;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphHost;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstance;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphRuntimeException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphTypeScriptGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator;
import io.github.ldev22live.gdxunity.engine.visualscript.KeyNames;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Key Pressed and Play Sound nodes (TD-072): the interpreter and the generated Java and Kotlin react only on the frame a key goes down,
 * the sound node asks the scene to play the cue, and TypeScript generation uses the web input's press check.
 */
class KeyPressedCodeTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final int SPACE = KeyNames.parse("SPACE").get(0);
    /** Frames the scenario runs; the key goes down on the second one and is held through the third. */
    private static final int FRAMES = 4;

    private Application previousApp;
    private Input previousInput;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void restoreGdx() {
        Gdx.app = previousApp;
        Gdx.input = previousInput;
    }

    /** Tick -> Branch(Key Pressed SPACE): True -> Print "jump pressed" -> Play Sound "jump". */
    private static Graph graph() {
        Graph graph = new Graph("pressed");
        GraphNode tick = add(graph, BuiltInNodes.EVENT_TICK);
        GraphNode key = add(graph, BuiltInNodes.INPUT_KEY_PRESSED);
        key.setProperty(BuiltInNodes.PROPERTY_KEY, "SPACE");
        GraphNode branch = add(graph, BuiltInNodes.FLOW_BRANCH);
        GraphNode print = add(graph, BuiltInNodes.ACTION_PRINT);
        print.setLiteral("message", "jump pressed");
        GraphNode sound = add(graph, BuiltInNodes.ACTION_PLAY_CUE);
        sound.setProperty(BuiltInNodes.PROPERTY_CUE, "jump");
        graph.addLink(new GraphLink(key.getId(), "pressed", branch.getId(), "condition"));
        graph.addLink(new GraphLink(tick.getId(), PinDef.EXEC_OUT, branch.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(branch.getId(), "true", print.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(print.getId(), PinDef.EXEC_OUT, sound.getId(), PinDef.EXEC_IN));
        return graph;
    }

    private static GraphNode add(Graph graph, String type) {
        GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
        graph.addNode(node);
        return node;
    }

    /** True for the second and third frame (held), but only the second is a press. */
    private static boolean held(int frame) {
        return frame == 1 || frame == 2;
    }

    private static boolean justPressed(int frame) {
        return frame == 1;
    }

    @Test
    void shouldReactOnlyOnTheFrameTheKeyGoesDownInTheInterpreter() {
        List<String> lines = new ArrayList<>();
        int[] frame = {0};
        Scene scene = new Scene("Interpreted");
        GraphInstance instance = new GraphInstance(graph(), REGISTRY, scene.createEntity("Self"), new GraphHost() {
            @Override
            public void log(GraphInstance i, String message) {
                lines.add(message);
            }

            @Override
            public void error(GraphInstance i, GraphRuntimeException error) {
                throw error;
            }

            @Override
            public boolean isKeyDown(int keycode) {
                return keycode == SPACE && held(frame[0]);
            }

            @Override
            public boolean isKeyJustPressed(int keycode) {
                return keycode == SPACE && justPressed(frame[0]);
            }
        });
        for (frame[0] = 0; frame[0] < FRAMES; frame[0]++) {
            instance.tick(1f / 60f);
        }

        assertEquals(List.of("jump pressed"), lines);
        assertTrue(new GraphValidator(REGISTRY).validate(graph()).isEmpty());
    }

    @Test
    void shouldRefuseAnUnknownKey() {
        Graph graph = graph();
        graph.getNode("n2").setProperty(BuiltInNodes.PROPERTY_KEY, "NOT_A_KEY");
        List<GraphRuntimeException> errors = new ArrayList<>();
        GraphInstance instance = new GraphInstance(graph, REGISTRY, new Scene("Bad").createEntity("Self"), new GraphHost() {
            @Override
            public void log(GraphInstance i, String message) {
            }

            @Override
            public void error(GraphInstance i, GraphRuntimeException error) {
                errors.add(error);
            }
        });
        instance.tick(1f / 60f);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("NOT_A_KEY"), errors.get(0).getMessage());
    }

    private List<String> runGenerated(Language language, Path temp) throws Exception {
        String source = new GraphCodeGenerator(REGISTRY).generate(graph(), language);
        assertTrue(source.contains("keyPressed("), source);
        assertTrue(source.contains("AudioSourceComponent.play(getScene(), \"jump\")"), source);
        Path dir = Files.createDirectories(temp.resolve("src"));
        String className = GraphCodeGenerator.className(graph());
        Path file = dir.resolve(className + language.getExtension());
        Files.writeString(file, source);
        File out = temp.resolve("out").toFile();
        CompilationResult result = language == Language.JAVA
            ? new JavaScriptCompiler().compile(List.of(file.toFile()), out, null)
            : new KotlinScriptCompiler().compile(List.of(file.toFile()), out, List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        assertFalse(result.hasErrors(), language + " did not compile: " + result.diagnostics() + "\n" + source);

        List<String> lines = new ArrayList<>();
        int[] frame = {0};
        previousApp = Gdx.app;
        previousInput = Gdx.input;
        Gdx.app = (Application) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Application.class},
            (proxy, method, args) -> {
                if (method.getName().equals("log") && args != null && args.length == 2) {
                    lines.add((String) args[1]);
                }
                return null;
            });
        Gdx.input = (Input) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Input.class}, (proxy, method, args) ->
            switch (method.getName()) {
                case "isKeyPressed" -> (Integer) args[0] == SPACE && held(frame[0]);
                case "isKeyJustPressed" -> (Integer) args[0] == SPACE && justPressed(frame[0]);
                default -> method.getReturnType() == boolean.class ? (Object) false : null;
            });
        try (ScriptClassLoader loader = new ScriptClassLoader(out, getClass().getClassLoader())) {
            Entity entity = new Scene("Generated").createEntity("Self");
            ScriptComponent script = (ScriptComponent) loader.loadScriptClass(GraphCodeGenerator.PACKAGE + "." + className)
                .getDeclaredConstructor().newInstance();
            entity.addComponent(script);
            script.onStart();
            for (frame[0] = 0; frame[0] < FRAMES; frame[0]++) {
                script.onUpdate(1f / 60f);
            }
        }
        return lines;
    }

    @Test
    void shouldMakeJavaThatReactsOnlyOnThePress(@TempDir Path temp) throws Exception {
        assertEquals(List.of("jump pressed"), runGenerated(Language.JAVA, temp));
    }

    @Test
    void shouldMakeKotlinThatReactsOnlyOnThePress(@TempDir Path temp) throws Exception {
        assertEquals(List.of("jump pressed"), runGenerated(Language.KOTLIN, temp));
    }

    @Test
    void shouldMakeTypeScriptThatAsksTheWebInputForAPressAndPlaysTheCue() {
        String source = new GraphTypeScriptGenerator(REGISTRY).generate(graph());

        assertTrue(source.contains("this.input.isJustPressed(\"Space\")"), source);
        assertTrue(source.contains("this.scene.playCue(\"jump\")"), source);
    }
}
