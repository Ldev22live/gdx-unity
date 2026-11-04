package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperties;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClipSerializer;
import io.github.ldev22live.gdxunity.engine.animation.AnimationCurve;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.animation.Interpolation;
import io.github.ldev22live.gdxunity.engine.animation.Keyframe;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionTest {
    private static final float FRAME = 1f / 60f;

    /** Collects what the session logs instead of printing it. */
    private static final class RecordingLog implements GameSession.Log {
        final List<String> info = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message) {
            info.add(message);
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }
    }

    @TempDir
    Path project;

    private Scene scene;
    private Entity player;
    private RecordingLog log;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Level");
        scene.createEntity("Camera").addComponent(new CameraComponent());
        player = scene.createEntity("Player");
        log = new RecordingLog();
    }

    private GameSession session() {
        // The test classes are on the parent class loader; the folder only has to exist
        return new GameSession(project.toFile(), scene, new GameScripts(project.toFile(), getClass().getClassLoader()), log);
    }

    private void bindScript(String className) {
        player.addComponent(new EntityScriptBindingComponent("scripts/Step.java", className, ScriptLanguage.JAVA));
    }

    private void write(String path, String text) throws IOException {
        Path file = project.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void shouldRefuseToStartWithoutACamera() {
        scene.findEntityByName("Camera").setActive(false);
        GameSession session = session();

        List<String> problems = session.start();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Camera"), problems.get(0));
        assertFalse(session.isStarted());
        session.dispose();
    }

    @Test
    void shouldBindPrecompiledScriptsAndRunThemEachFrame() {
        bindScript(StepCountingScript.class.getName());
        GameSession session = session();

        assertEquals(List.of(), session.start());
        session.update(FRAME);
        session.update(FRAME);

        StepCountingScript script = (StepCountingScript) player.getComponent(EntityScriptBindingComponent.class)
            .getScriptInstance();
        assertEquals(2, script.updates);
        assertEquals(2 * StepCountingScript.STEP, player.getTransform().getLocalPosition().x, 1e-4f);
        session.dispose();
    }

    @Test
    void shouldReportScriptsThatCannotBeCreated() {
        bindScript("samples.DoesNotExist");
        GameSession session = session();

        List<String> problems = session.start();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("samples.DoesNotExist"), problems.get(0));
        assertNull(session.getPhysicsSystem(), "nothing stays running after a failed start");
        session.dispose();
    }

    @Test
    void shouldExplainWhereCompiledScriptsAreExpectedWhenTheFolderIsMissing() {
        bindScript(StepCountingScript.class.getName());
        File missing = project.resolve("build/scripts").toFile();
        GameSession session = new GameSession(project.toFile(), scene, new GameScripts(missing, getClass().getClassLoader()), log);

        List<String> problems = session.start();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains(missing.getPath()) && problems.get(0).contains("--scripts"), problems.get(0));
        session.dispose();
    }

    @Test
    void shouldSimulate2DPhysicsBeforeTheSceneUpdate() {
        player.getTransform().setLocalPosition(0f, 500f, 0f);
        player.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        player.addComponent(new BoxCollider2DComponent(50f, 50f));
        GameSession session = session();
        assertEquals(List.of(), session.start());

        for (int i = 0; i < 30; i++) {
            session.update(FRAME);
        }

        assertNotNull(session.getPhysicsSystem());
        assertTrue(player.getTransform().getLocalPosition().y < 500f, "the body fell under gravity");
        session.dispose();
        assertNull(session.getPhysicsSystem());
    }

    @Test
    void shouldRunGraphsFromTheProjectAndRecordTheirErrors() throws IOException {
        Graph graph = new Graph("Greeter");
        GraphNode begin = new GraphNode(graph.newNodeId(), BuiltInNodes.EVENT_BEGIN_PLAY, 0f, 0f);
        GraphNode print = new GraphNode(graph.newNodeId(), BuiltInNodes.ACTION_PRINT, 0f, 0f);
        print.setLiteral("message", "hello from the runtime");
        GraphNode tick = new GraphNode(graph.newNodeId(), BuiltInNodes.EVENT_TICK, 0f, 0f);
        GraphNode divide = new GraphNode(graph.newNodeId(), BuiltInNodes.MATH_DIVIDE, 0f, 0f);
        GraphNode text = new GraphNode(graph.newNodeId(), BuiltInNodes.TEXT_FROM_FLOAT, 0f, 0f);
        GraphNode printResult = new GraphNode(graph.newNodeId(), BuiltInNodes.ACTION_PRINT, 0f, 0f);
        for (GraphNode node : List.of(begin, print, tick, divide, text, printResult)) {
            graph.addNode(node);
        }
        graph.addLink(new GraphLink(begin.getId(), PinDef.EXEC_OUT, print.getId(), PinDef.EXEC_IN));
        // Divides by zero every frame
        graph.addLink(new GraphLink(tick.getId(), PinDef.EXEC_OUT, printResult.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(divide.getId(), "result", text.getId(), "value"));
        graph.addLink(new GraphLink(text.getId(), "text", printResult.getId(), "message"));
        write("graphs/Greeter.graph", GraphSerializer.toJson(graph));
        GraphScriptComponent graphScript = player.addComponent(new GraphScriptComponent("graphs/Greeter.graph"));
        GameSession session = session();
        assertEquals(List.of(), session.start());

        for (int i = 0; i < GameSession.MAX_RECORDED_ERRORS + 5; i++) {
            session.update(FRAME);
        }

        assertTrue(log.info.stream().anyMatch(line -> line.contains("hello from the runtime")), () -> "log: " + log.info);
        assertEquals(GameSession.MAX_RECORDED_ERRORS, session.getRuntimeErrors().size(), "recorded errors are capped");
        assertTrue(log.errors.size() > GameSession.MAX_RECORDED_ERRORS, "every error is still logged");
        assertTrue(session.getRuntimeErrors().get(0).contains("Greeter on Player"), session.getRuntimeErrors().get(0));
        session.dispose();
        assertNull(graphScript.getInstance(), "graphs are unbound on dispose");
    }

    @Test
    void shouldReportMissingGraphAndAnimationFiles() {
        player.addComponent(new GraphScriptComponent("graphs/Missing.graph"));
        GameSession graphSession = session();
        List<String> graphProblems = graphSession.start();
        assertEquals(1, graphProblems.size());
        assertTrue(graphProblems.get(0).contains("graphs/Missing.graph"), graphProblems.get(0));
        graphSession.dispose();

        setUp();
        player.addComponent(new AnimatorComponent("animations/Missing.anim"));
        GameSession animationSession = session();
        List<String> animationProblems = animationSession.start();
        assertEquals(1, animationProblems.size());
        assertTrue(animationProblems.get(0).contains("animations/Missing.anim"), animationProblems.get(0));
        animationSession.dispose();
    }

    @Test
    void shouldPlayAnimatorClipsFromTheProject() throws IOException {
        AnimationClip clip = new AnimationClip("Rise", 2f, 30);
        AnimationCurve curve = new AnimationCurve();
        curve.setKey(new Keyframe(0f, 0f, Interpolation.LINEAR));
        curve.setKey(new Keyframe(2f, 200f, Interpolation.LINEAR));
        clip.addTrack(new AnimationTrack("", AnimatedProperties.POSITION_Y, curve));
        write("animations/Rise.anim", AnimationClipSerializer.toJson(clip));
        player.addComponent(new AnimatorComponent("animations/Rise.anim"));
        GameSession session = session();
        assertEquals(List.of(), session.start());

        for (int i = 0; i < 30; i++) {
            session.update(FRAME);
        }

        assertTrue(player.getTransform().getLocalPosition().y > 10f, "the clip moved the player up");
        session.dispose();
    }

    @Test
    void shouldClampLongFramesAndIgnoreInvalidDeltas() {
        bindScript(StepCountingScript.class.getName());
        player.getTransform().setLocalPosition(0f, 500f, 0f);
        player.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        player.addComponent(new BoxCollider2DComponent(50f, 50f));
        GameSession session = session();
        assertEquals(List.of(), session.start());

        session.update(Float.NaN);
        float afterInvalid = player.getTransform().getLocalPosition().y;
        session.update(5f);
        float afterStall = player.getTransform().getLocalPosition().y;

        assertEquals(500f, afterInvalid, 1e-3f, "an invalid delta advances nothing");
        assertTrue(500f - afterStall < 50f, "a 5 s stall is simulated as " + GameSession.MAX_FRAME_DELTA + " s, fell " + (500f - afterStall));
        session.dispose();
    }

    @Test
    void shouldUnbindScriptsOnDisposeAndRejectRestarts() {
        bindScript(StepCountingScript.class.getName());
        EntityScriptBindingComponent binding = player.getComponent(EntityScriptBindingComponent.class);
        GameSession session = session();
        assertEquals(List.of(), session.start());
        assertNotNull(binding.getScriptInstance());
        assertThrows(IllegalStateException.class, session::start);

        session.dispose();
        session.dispose();

        assertNull(binding.getScriptInstance());
        assertThrows(IllegalStateException.class, session::start);
    }

    @Test
    void shouldLoadSceneFilesWithEveryEngineComponentType() throws IOException {
        player.addComponent(new GraphScriptComponent("graphs/A.graph"));
        player.addComponent(new AnimatorComponent("animations/A.anim"));
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        write("level.json", new SceneSerializer(registry).toJson(scene));

        Scene loaded = GameSession.loadScene(project.resolve("level.json").toFile());

        Entity loadedPlayer = loaded.findEntityByName("Player");
        assertNotNull(loadedPlayer.getComponent(GraphScriptComponent.class));
        assertNotNull(loadedPlayer.getComponent(AnimatorComponent.class));
        assertNotNull(loaded.findEntityByName("Camera").getComponent(CameraComponent.class));
        assertThrows(IOException.class, () -> GameSession.loadScene(project.resolve("missing.json").toFile()));
    }

    @Test
    void shouldPlayRegisteredScriptsWithoutAClassLoaderAsTheWebRuntimeDoes() {
        bindScript(StepCountingScript.class.getName());
        ScriptRegistry registry = new ScriptRegistry().register(StepCountingScript.class.getName(), StepCountingScript::new);
        GameSession session = new GameSession(AssetSource.directory(project.toFile()), scene, registry, log);

        assertEquals(List.of(), session.start());
        session.update(FRAME);

        assertEquals(StepCountingScript.STEP, player.getTransform().getLocalPosition().x, 1e-4f);
        session.dispose();
    }

    @Test
    void shouldNameAScriptThatIsMissingFromTheRegistry() {
        bindScript("game.NotBuiltIn");
        GameSession session = new GameSession(AssetSource.directory(project.toFile()), scene, new ScriptRegistry(), log);

        List<String> problems = session.start();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("game.NotBuiltIn"), problems.get(0));
        assertTrue(problems.get(0).contains("script registry"), problems.get(0));
        assertFalse(session.isStarted());
        session.dispose();
    }

    @Test
    void shouldReadGraphsThroughTheAssetSourceNotTheFileSystem() throws IOException {
        Graph graph = new Graph("Hello");
        GraphNode begin = new GraphNode(graph.newNodeId(), BuiltInNodes.EVENT_BEGIN_PLAY, 0f, 0f);
        GraphNode print = new GraphNode(graph.newNodeId(), BuiltInNodes.ACTION_PRINT, 0f, 0f);
        print.setLiteral("message", "read through the source");
        graph.addNode(begin);
        graph.addNode(print);
        graph.addLink(new GraphLink(begin.getId(), PinDef.EXEC_OUT, print.getId(), PinDef.EXEC_IN));
        // Stored under another folder than the scene names: only the source knows where it is
        write("bundle/graphs/Hello.graph", GraphSerializer.toJson(graph));
        List<String> requested = new ArrayList<>();
        AssetSource bundled = reference -> {
            requested.add(reference.getPath());
            return new FileHandle(project.resolve("bundle").resolve(reference.getPath()).toFile());
        };
        player.addComponent(new GraphScriptComponent("graphs/Hello.graph"));
        GameSession session = new GameSession(bundled, scene, new ScriptRegistry(), log);

        assertEquals(List.of(), session.start());
        session.update(FRAME);

        assertEquals(List.of("graphs/Hello.graph"), requested);
        assertTrue(log.info.stream().anyMatch(line -> line.contains("read through the source")), log.info.toString());
        session.dispose();
    }

    @Test
    void shouldLoadSceneThroughAnAssetSourceAndNameAMissingOne() throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        write("levels/level.json", new SceneSerializer(registry).toJson(scene));
        AssetSource source = AssetSource.directory(project.toFile());

        Scene loaded = GameSession.loadScene(source, "levels/level.json");

        assertNotNull(loaded.findEntityByName("Player"));
        IOException missing = assertThrows(IOException.class, () -> GameSession.loadScene(source, "levels/none.json"));
        assertTrue(missing.getMessage().contains("Scene not found: levels/none.json"), missing.getMessage());
    }
}
