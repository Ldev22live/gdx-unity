package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.animation.AnimationAssets;
import io.github.ldev22live.gdxunity.editor.animation.AnimationPlayModeExtension;
import io.github.ldev22live.gdxunity.editor.animation.TimelineDocument;
import io.github.ldev22live.gdxunity.editor.animation.TimelineSession;
import io.github.ldev22live.gdxunity.editor.audio.MusicSession;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand;
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.editor.core.file.NonInteractiveDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphAssets;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphDocument;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphExecutionTrace;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphPlayModeExtension;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperties;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.animation.Interpolation;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance project of roadmap milestone M0.4, the steps that need no display: one small 3D game built through the editor's own
 * operations (a scene, an imported model, physics, a camera, a graph, an animation clip, a song exported to audio), saved, reopened and
 * played, with what was observed at each step recorded in {@code build/acceptance-steps.txt}. The project it leaves in
 * {@code build/acceptance-project} is a real project folder, so the game runtime and the export tool can be run on it afterwards.
 *
 * <p>This is a headless run: it shows that the data and the simulation work, not how anything looks or sounds. Those, the editor's own
 * windows, and launching an exported game are recorded separately in the milestone report.
 */
class AcceptanceProjectTest {
    private static final File PROJECT = new File("build/acceptance-project");
    private static final File STEPS = new File("build/acceptance-steps.txt");
    private static final File MODEL_SOURCE = new File("../assets/models/SculptedRock.glb");
    private static final String SCENE = "scenes/acceptance.json";
    private static final float FRAME = 1f / 60f;
    private static final int THREE_SECONDS = 180;
    private static final NodeRegistry NODES = NodeRegistry.createDefault();

    private final List<String> steps = new ArrayList<>();

    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    private void step(String name, String observed) {
        steps.add(name + " | " + observed);
    }

    private static void deleteTree(File folder) throws IOException {
        if (folder.exists()) {
            try (Stream<Path> walk = Files.walk(folder.toPath())) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return new SceneSerializer(registry);
    }

    @Test
    void shouldBuildSaveReopenAndPlayASmallGameThroughTheEditorsOwnOperations() throws Exception {
        deleteTree(PROJECT);
        for (String folder : new String[]{"scenes", "models", "graphs", "animations", "music", "sounds"}) {
            Files.createDirectories(PROJECT.toPath().resolve(folder));
        }
        Files.writeString(PROJECT.toPath().resolve("gdxunity.project"),
            "format=1\nname=Acceptance Game\nstartScene=" + SCENE + "\n", StandardCharsets.UTF_8);
        try {
            run();
        } finally {
            Files.writeString(STEPS.toPath(), String.join(System.lineSeparator(), steps) + System.lineSeparator(), StandardCharsets.UTF_8);
        }
    }

    private void run() throws Exception {
        // 1. Create a 3D scene
        EditorState state = new EditorState(serializer());
        File sceneFile = new File(PROJECT, SCENE);
        SceneFileController files = new SceneFileController(state, new NonInteractiveDialogService(), PROJECT);
        assertTrue(files.newScene(GameMode.MODE_3D));
        assertEquals(GameMode.MODE_3D, state.getGameMode());
        step("1 Create a 3D scene", "a new untitled scene in game mode " + state.getGameMode().getLabel());

        // 2. Import a model: the file goes into the project, then a Model entity uses it
        assertTrue(MODEL_SOURCE.isFile(), "the sample model is missing: " + MODEL_SOURCE.getAbsolutePath());
        Files.copy(MODEL_SOURCE.toPath(), new File(PROJECT, "models/SculptedRock.glb").toPath());
        byte[] glb = Files.readAllBytes(new File(PROJECT, "models/SculptedRock.glb").toPath());
        assertEquals("glTF", new String(glb, 0, 4, StandardCharsets.US_ASCII), "a binary glTF file starts with the glTF magic");
        ComponentCatalog catalog = ComponentCatalog.createDefault();
        state.setSelectedAsset(new AssetReference("models/SculptedRock.glb"));
        Entity rock = state.createEntity("Rock");
        Entity rockMesh = state.createEntity("Rock Mesh");
        rockMesh.getTransform().setParent(rock.getTransform());
        assertTrue(catalog.addTo(state, rockMesh, entryFor(catalog, ModelComponent.class)), state.getStatusMessage());
        assertEquals("models/SculptedRock.glb", rockMesh.getComponent(ModelComponent.class).getModelPath());
        step("2 Import a model", "models/SculptedRock.glb copied into the project (" + glb.length + " bytes, glTF header present), "
            + "placed on 'Rock Mesh' as a Model component; the model was not loaded or drawn (no graphics)");

        // 3. Physics and the level: a floor and a ball that falls
        Entity floor = state.createEntity("Floor");
        floor.getTransform().setLocalPosition(0f, -50f, 0f);
        floor.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 2000f, 100f, 2000f, new Color(0.5f, 0.6f, 0.5f, 1f)));
        floor.addComponent(new BoxCollider3DComponent(new Vector3(2000f, 100f, 2000f)));
        Entity ball = state.createEntity("Ball");
        ball.getTransform().setLocalPosition(0f, 400f, 0f);
        ball.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.SPHERE, 100f, 100f, 100f, new Color(0.9f, 0.3f, 0.2f, 1f)));
        assertTrue(catalog.addTo(state, ball, entryFor(catalog, RigidBody3DComponent.class)), state.getStatusMessage());
        ball.addComponent(new SphereCollider3DComponent(50f));
        step("3 Physics", "a static floor (box collider) and a dynamic ball (rigid body 3D, sphere collider) created; the ball starts at y=400");

        // 4. A camera and a light
        Entity camera = state.createEntity("Main Camera");
        camera.getTransform().setLocalPosition(0f, 300f, 900f);
        assertTrue(catalog.addTo(state, camera, entryFor(catalog, CameraComponent.class)), state.getStatusMessage());
        Entity sun = state.createEntity("Sun");
        assertTrue(catalog.addTo(state, sun, entryFor(catalog,
            io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent.class)), state.getStatusMessage());
        CameraComponent.Projection addedAs = camera.getComponent(CameraComponent.class).getProjection();
        // The first run of this acceptance project found that Add Component > Camera came out orthographic even in a 3D scene, which
        // showed a flat slice of the level and made Play switch to the 2D view; a new camera now suits the scene's game mode
        assertEquals(CameraComponent.Projection.PERSPECTIVE, addedAs, "a camera added to a 3D scene must be perspective");
        step("4 Camera", "a Camera component was added to the 3D scene and came out " + addedAs + " with no change needed (the first run "
            + "found it came out ORTHOGRAPHIC, which hid the floor and the model; fixed in this milestone), and a directional light added");

        // 5. Graph logic, built node by node: when the ball touches something, print "touched <name>"
        GraphAssets graphs = new GraphAssets(PROJECT);
        AssetReference graphAsset = graphs.create("Toucher");
        GraphDocument document = new GraphDocument(graphAsset, graphs.load(graphAsset), NODES);
        Graph graph = document.getGraph();
        GraphNode onCollision = new GraphNode(graph.newNodeId(), BuiltInNodes.EVENT_COLLISION, 0f, 0f);
        GraphNode name = new GraphNode(graph.newNodeId(), BuiltInNodes.DATA_GET_NAME, 0f, 0f);
        GraphNode text = new GraphNode(graph.newNodeId(), BuiltInNodes.TEXT_APPEND, 0f, 0f);
        text.setLiteral("a", "touched ");
        GraphNode print = new GraphNode(graph.newNodeId(), BuiltInNodes.ACTION_PRINT, 0f, 0f);
        for (GraphNode node : List.of(onCollision, name, text, print)) {
            graph.addNode(node);
        }
        graph.addLink(new GraphLink(onCollision.getId(), PinDef.EXEC_OUT, print.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(onCollision.getId(), BuiltInNodes.PIN_OTHER, name.getId(), "target"));
        graph.addLink(new GraphLink(name.getId(), "name", text.getId(), "b"));
        graph.addLink(new GraphLink(text.getId(), "result", print.getId(), "message"));
        document.save(graphs);
        assertTrue(new File(PROJECT, graphAsset.getPath()).isFile());
        assertTrue(state.executeCommand(new AddComponentCommand(state, ball.getId(), new GraphScriptComponent(graphAsset.getPath()))));
        step("5 Graph logic", graphAsset.getPath() + " built from 4 nodes and 4 links (On Collision, Get Name, Append Text, Print) and "
            + "attached to the ball as a Graph Script");

        // 6. Animation: the rock bobs up and down
        AnimationAssets animations = new AnimationAssets(PROJECT);
        TimelineSession timeline = new TimelineSession(state, animations, AnimatedProperties.createDefault());
        AssetReference clip = animations.create("Bob");
        state.executeCommand(new AddComponentCommand(state, rock.getId(), new AnimatorComponent(clip.getPath())));
        assertTrue(timeline.open(clip, rock.getId()));
        TimelineDocument clipDocument = timeline.getDocument();
        AnimationTrack bob = clipDocument.addTrack("Rock Mesh", AnimatedProperties.POSITION_Y);
        assertNotNull(bob);
        assertTrue(clipDocument.setKey(bob, 0f, 0f));
        assertTrue(clipDocument.setKey(bob, 2f, 100f));
        clipDocument.setInterpolation(List.of(new TimelineDocument.KeyRef(bob, 0f)), Interpolation.LINEAR);
        assertTrue(timeline.saveIfDirty());
        step("6 Animation", clip.getPath() + " created with a position-Y track on 'Rock Mesh' (0 at 0 s, 100 at 2 s, linear), saved, "
            + "and bound to 'Rock' through an Animator component");

        // 7. Music: a drum pattern saved as a song, played by a Music Source; and rendered to a WAV (the Music Studio's export)
        MusicSession music = new MusicSession(PROJECT.toPath());
        assertTrue(music.createSong("music/theme.song"), String.valueOf(music.getLastError()));
        var pattern = music.getSelectedPattern();
        var channel = music.getDocument().getSong().getChannels().get(0);
        assertTrue(music.getDocument().toggleStep(pattern, channel, 0, 1f));
        assertTrue(music.getDocument().toggleStep(pattern, channel, 4, 1f));
        assertTrue(music.save(), String.valueOf(music.getLastError()));
        assertTrue(music.exportWav("sounds/theme.wav", false), String.valueOf(music.getLastError()));
        byte[] wav = Files.readAllBytes(new File(PROJECT, "sounds/theme.wav").toPath());
        int peak = 0;
        for (int i = 44; i + 1 < wav.length; i += 2) {
            peak = Math.max(peak, Math.abs((short) ((wav[i] & 0xff) | (wav[i + 1] << 8))));
        }
        assertTrue(peak > 100, "the exported audio is not silent: peak " + peak);
        // A scene plays a song through a Music Source component (TD-061), added the way the menu adds it
        Entity speaker = state.createEntity("Music");
        assertTrue(catalog.addTo(state, speaker, entryFor(catalog, MusicSourceComponent.class)), state.getStatusMessage());
        MusicSourceComponent source = speaker.getComponent(MusicSourceComponent.class);
        source.setSong("music/theme.song");
        assertEquals("music/theme.song", source.getSong());
        step("7 Music", "music/theme.song created in the Music Studio session with two drum steps and saved; a Music Source on the 'Music' "
            + "entity plays it (loops, starts with the scene). The song was also rendered to sounds/theme.wav with the Music Studio's WAV "
            + "export (" + wav.length + " bytes, peak sample " + peak + " of 32767, so not silent). Not listened to: this run has no audio device");

        // 8. Save
        state.saveSceneToFile(sceneFile);
        assertTrue(sceneFile.isFile());
        String saved = Files.readString(sceneFile.toPath());
        step("8 Save", SCENE + " written (" + saved.length() + " characters, " + state.getActiveScene().getAllEntities().size() + " entities)");

        // 9. Reopen in a fresh editor state: the same scene comes back
        EditorState reopenedState = new EditorState(serializer());
        SceneFileController reopenedFiles = new SceneFileController(reopenedState, new NonInteractiveDialogService(), PROJECT);
        assertTrue(reopenedFiles.openFile(sceneFile));
        String reopened = reopenedState.getSerializer().toJson(reopenedState.getActiveScene());
        assertEquals(state.getSerializer().toJson(state.getActiveScene()), reopened, "the reopened scene differs from the saved one");
        assertEquals(GameMode.MODE_3D, reopenedState.getGameMode());
        step("9 Reopen", "a fresh editor state opened " + SCENE + " and its saved form is identical to the original, game mode kept");

        // 10. Play the reopened scene with every runtime extension the editor adds that needs no window
        EditorLog log = new EditorLog();
        GraphAssets reopenedGraphs = new GraphAssets(PROJECT);
        TimelineSession reopenedTimeline = new TimelineSession(reopenedState, new AnimationAssets(PROJECT), AnimatedProperties.createDefault());
        PlayModeController play = new PlayModeController(reopenedState);
        AtomicLong now = new AtomicLong();
        play.addExtension(new GraphPlayModeExtension(reopenedState, reopenedGraphs, NODES, log, new GraphExecutionTrace(now::get)));
        play.addExtension(new AnimationPlayModeExtension(reopenedTimeline, log));
        play.addExtension(new io.github.ldev22live.gdxunity.editor.core.play.GameViewPlayModeExtension(reopenedState));
        play.addExtension(new GeometryBinding());
        Scene authored = reopenedState.getActiveScene();
        String before = reopenedState.getSerializer().toJson(authored);

        assertTrue(play.play(), "Play refused: " + reopenedState.getStatusMessage());
        Scene runtime = play.getRuntimeScene();
        assertNotNull(GameCameraRig.findActiveCamera(runtime), "Play found no active camera");
        Entity runtimeBall = runtime.findEntityByName("Ball");
        Entity runtimeBob = runtime.findEntityByName("Rock Mesh");
        float bobAtOneSecond = Float.NaN;
        for (int frame = 0; frame < THREE_SECONDS; frame++) {
            play.update(FRAME);
            if (frame == 59) {
                bobAtOneSecond = runtimeBob.getTransform().getLocalPosition().y;
            }
        }
        float ballY = runtimeBall.getTransform().getWorldPosition().y;
        long touches = log.getEntries(EditorLog.Severity.INFO).stream().filter(e -> e.message().contains("touched Floor")).count();
        play.stop();

        assertEquals(50f, ballY, 5f, "the ball fell and came to rest on the floor");
        assertTrue(touches >= 1, "the graph did not print when the ball touched the floor");
        assertTrue(bobAtOneSecond > 20f && bobAtOneSecond < 80f, "the clip should have moved the rock about halfway at 1 s: " + bobAtOneSecond);
        assertEquals(before, reopenedState.getSerializer().toJson(authored), "Play changed the authored scene");
        assertFalse(play.isPlaying());
        step("10 Play", "an active camera was found; after 3 simulated seconds the ball rested at y=" + ballY + " (started 400, floor top 0); "
            + "the graph printed 'touched Floor' " + touches + " time(s); the Bob clip had moved 'Rock Mesh' to y=" + bobAtOneSecond
            + " at 1 s; after Stop the authored scene's saved form was identical to before Play");
    }

    private static ComponentCatalog.Entry entryFor(ComponentCatalog catalog, Class<?> type) {
        return catalog.getEntries().stream().filter(e -> e.type() == type).findFirst().orElseThrow();
    }

    /** What the editor's renderer does for a playing scene: lets mesh and hull colliders read geometry (none are used here). */
    private static final class GeometryBinding implements PlayModeController.Extension {
        @Override
        public List<String> onPlay(Scene runtimeScene, PlayModeController controller) {
            runtimeScene.setColliderGeometry(new ColliderGeometry(null));
            return List.of();
        }

        @Override
        public void onStop(Scene runtimeScene) {
        }
    }
}
