package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.scripting.CompilationResult;
import io.github.ldev22live.gdxunity.editor.core.scripting.JavaScriptCompiler;
import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptClassLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphHost;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstance;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphRuntimeException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.KeyNames;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays a sample's Java script and its graph with the same keys, frame by frame (TD-071, TD-072). A scenario file lists the start position
 * and the keys of each step; the script is compiled and run, the graph is run, and the positions are sampled every {@link #RECORD_EVERY}
 * frames. The script's positions are recorded in the same file ({@code expected}); the web test replays them against the web interpreter.
 * Record again with the environment variable {@code GDXUNITY_RECORD_FIXTURES=1}.
 */
final class SampleParity {
    static final File ASSETS = new File("../assets");
    static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    static final float FRAME = 1f / 60f;
    /** Frames between the recorded positions. */
    static final int RECORD_EVERY = 10;
    /** How far the graph may be from the script, in scene units; both use floats and add up in a different order. */
    static final float TOLERANCE = 0.05f;

    private SampleParity() {
    }

    record Step(int frames, Set<Integer> keys) { }

    /** The keys both runs read: held and went-down-this-frame, like libGDX. */
    static final class Keys {
        Set<Integer> down = new HashSet<>();
        Set<Integer> previous = new HashSet<>();

        void frame(Set<Integer> now) {
            previous = down;
            down = now;
        }

        boolean justPressed(int keycode) {
            return down.contains(keycode) && !previous.contains(keycode);
        }
    }

    /** Restores {@code Gdx.app} and {@code Gdx.input} after a scripted run. */
    static final class GdxState {
        private final Application app = Gdx.app;
        private final Input input = Gdx.input;

        void restore() {
            Gdx.app = app;
            Gdx.input = input;
        }
    }

    static JsonValue read(File fixture) throws IOException {
        return new JsonReader().parse(Files.readString(fixture.toPath()));
    }

    static List<Step> steps(JsonValue root) {
        List<Step> steps = new ArrayList<>();
        for (JsonValue s = root.get("steps").child; s != null; s = s.next) {
            Set<Integer> keys = new HashSet<>();
            for (JsonValue k = s.get("keys").child; k != null; k = k.next) {
                keys.addAll(KeyNames.parse(k.asString()));
            }
            steps.add(new Step(s.getInt("frames"), keys));
        }
        return steps;
    }

    static Vector3 start(JsonValue root) {
        JsonValue s = root.get("start");
        return new Vector3(s.getFloat("x"), s.getFloat("y"), s.getFloat("z"));
    }

    /** Compiles {@code scriptPath} (under assets) and runs it on a fresh entity; returns the sampled world positions. */
    static List<Vector3> runScript(Path temp, String scriptPath, String className, JsonValue root, List<Step> steps) throws IOException {
        return runScript(temp, scriptPath, className, root, steps, entity -> { });
    }

    /**
     * Like {@link #runScript(Path, String, String, JsonValue, List)}, with {@code setup} giving the fresh entity the components the
     * sample's entity has (a Shape Renderer whose width the script reads, for example) before the script starts.
     */
    static List<Vector3> runScript(Path temp, String scriptPath, String className, JsonValue root, List<Step> steps,
                                   Consumer<Entity> setup) throws IOException {
        Keys keys = new Keys();
        Gdx.input = (Input) Proxy.newProxyInstance(SampleParity.class.getClassLoader(), new Class<?>[] {Input.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "isKeyPressed" -> keys.down.contains((Integer) args[0]);
                case "isKeyJustPressed" -> keys.justPressed((Integer) args[0]);
                default -> method.getReturnType() == boolean.class ? (Object) false : null;
            };
        });
        Entity entity = new Scene("Script").createEntity("Player");
        entity.getTransform().setLocalPosition(start(root));
        setup.accept(entity);
        File out = temp.resolve("out").toFile();
        CompilationResult result = new JavaScriptCompiler().compile(List.of(new File(ASSETS, scriptPath)), out, null);
        assertTrue(!result.hasErrors(), "the script compiles: " + result.diagnostics());
        try (ScriptClassLoader loader = new ScriptClassLoader(out, SampleParity.class.getClassLoader())) {
            ScriptComponent script = (ScriptComponent) loader.loadScriptClass(className).getDeclaredConstructor().newInstance();
            entity.addComponent(script);
            script.onStart();
            List<Vector3> positions = new ArrayList<>();
            int frame = 0;
            for (Step step : steps) {
                for (int i = 0; i < step.frames(); i++) {
                    keys.frame(step.keys());
                    script.onUpdate(FRAME);
                    if (++frame % RECORD_EVERY == 0) {
                        positions.add(new Vector3(entity.getTransform().getWorldPosition()));
                    }
                }
            }
            return positions;
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }

    /** Runs the graph at {@code graphPath} (under assets/graphs) the same way; returns the sampled world positions. */
    static List<Vector3> runGraph(String graphPath, JsonValue root, List<Step> steps) throws IOException {
        return runGraph(graphPath, root, steps, entity -> { });
    }

    /** Like {@link #runGraph(String, JsonValue, List)}, with the same entity setup as the script's run. */
    static List<Vector3> runGraph(String graphPath, JsonValue root, List<Step> steps, Consumer<Entity> setup) throws IOException {
        Graph graph = GraphSerializer.fromJson(Files.readString(new File(ASSETS, "graphs/" + graphPath).toPath()));
        Keys keys = new Keys();
        Entity entity = new Scene("Graph").createEntity("Player");
        entity.getTransform().setLocalPosition(start(root));
        setup.accept(entity);
        GraphInstance instance = new GraphInstance(graph, REGISTRY, entity, new GraphHost() {
            @Override
            public void log(GraphInstance i, String message) {
            }

            @Override
            public void error(GraphInstance i, GraphRuntimeException error) {
                throw error;
            }

            @Override
            public boolean isKeyDown(int keycode) {
                return keys.down.contains(keycode);
            }

            @Override
            public boolean isKeyJustPressed(int keycode) {
                return keys.justPressed(keycode);
            }
        });
        instance.beginPlay();
        List<Vector3> positions = new ArrayList<>();
        int frame = 0;
        for (Step step : steps) {
            for (int i = 0; i < step.frames(); i++) {
                keys.frame(step.keys());
                instance.tick(FRAME);
                if (++frame % RECORD_EVERY == 0) {
                    positions.add(new Vector3(entity.getTransform().getWorldPosition()));
                }
            }
        }
        return positions;
    }

    static void assertSamePositions(List<Vector3> expected, List<Vector3> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            String at = " at frame " + (i + 1) * RECORD_EVERY;
            assertEquals(expected.get(i).x, actual.get(i).x, TOLERANCE, "x" + at);
            assertEquals(expected.get(i).y, actual.get(i).y, TOLERANCE, "y" + at);
            assertEquals(expected.get(i).z, actual.get(i).z, TOLERANCE, "z" + at);
        }
    }

    /** With GDXUNITY_RECORD_FIXTURES=1 writes the script's positions into the scenario file and returns true. */
    static boolean recordIfAsked(File fixture, List<Vector3> script) throws IOException {
        if (!"1".equals(System.getenv("GDXUNITY_RECORD_FIXTURES"))) {
            return false;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < script.size(); i++) {
            Vector3 p = script.get(i);
            text.append(i == 0 ? "" : ",\n    ").append("[").append(p.x).append(", ").append(p.y).append(", ").append(p.z).append("]");
        }
        String original = Files.readString(fixture.toPath());
        int from = original.indexOf("\"expected\"");
        String head = from < 0 ? original.substring(0, original.lastIndexOf('}')).stripTrailing() + ",\n  " : original.substring(0, from);
        Files.writeString(fixture.toPath(), head + "\"expected\": [\n    " + text + "\n  ]\n}\n");
        return true;
    }

    /** The script's positions equal the ones recorded in the scenario file. */
    static void assertMatchesRecorded(JsonValue root, List<Vector3> script) {
        JsonValue expected = root.get("expected");
        assertTrue(expected != null && expected.size == script.size(), "the scenario file has " + script.size() + " recorded positions");
        List<Vector3> recorded = new ArrayList<>();
        for (JsonValue p = expected.child; p != null; p = p.next) {
            recorded.add(new Vector3(p.get(0).asFloat(), p.get(1).asFloat(), p.get(2).asFloat()));
        }
        assertSamePositions(recorded, script);
    }
}
