package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Collector's player graph (TD-071) does what the Java script does: the script is compiled and run, the graph is run, both are fed
 * the same keys frame by frame, and the positions must agree (see {@link SampleParity}). The scenario and the script's recorded
 * positions are in {@code collector-trajectory.json}, which the web test replays.
 */
class CollectorParityTest {
    private static final File FIXTURE = new File("src/test/resources/collector-trajectory.json");
    private static final String SCRIPT = "scripts/samples/collector/CollectorPlayer.java";
    private static final String GRAPH = "samples/CollectorPlayer.graph";

    private final SampleParity.GdxState gdx = new SampleParity.GdxState();

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void restoreGdx() {
        gdx.restore();
    }

    @Test
    void shouldHaveNoProblems() throws IOException {
        Graph graph = GraphSerializer.fromJson(Files.readString(new File(SampleParity.ASSETS, "graphs/" + GRAPH).toPath()));

        assertTrue(new GraphValidator(SampleParity.REGISTRY).validate(graph).isEmpty(),
            new GraphValidator(SampleParity.REGISTRY).validate(graph).toString());
    }

    @Test
    void shouldMoveLikeTheScriptFrameForFrame(@TempDir Path temp) throws IOException {
        JsonValue root = SampleParity.read(FIXTURE);
        List<SampleParity.Step> steps = SampleParity.steps(root);

        List<Vector3> script = SampleParity.runScript(temp, SCRIPT, "samples.collector.CollectorPlayer", root, steps);
        List<Vector3> graph = SampleParity.runGraph(GRAPH, root, steps);

        SampleParity.assertSamePositions(script, graph);
    }

    @Test
    void shouldReachTheWallsAndKeepTheHeight() throws IOException {
        JsonValue root = SampleParity.read(FIXTURE);

        List<Vector3> graph = SampleParity.runGraph(GRAPH, root, SampleParity.steps(root));

        float farthest = 0f;
        for (Vector3 position : graph) {
            farthest = Math.max(farthest, Math.max(Math.abs(position.x), Math.abs(position.z)));
        }
        assertEquals(740f, farthest, 0.01f, "the ball reaches the arena edge and is held there");
        assertEquals(SampleParity.start(root).y, graph.get(graph.size() - 1).y, 0.0001f, "height never changes");
    }

    /** With GDXUNITY_RECORD_FIXTURES=1 the scenario file is written from the script's run instead. */
    @Test
    void shouldMatchTheRecordedPositionsOfTheScript(@TempDir Path temp) throws IOException {
        JsonValue root = SampleParity.read(FIXTURE);
        List<Vector3> script = SampleParity.runScript(temp, SCRIPT, "samples.collector.CollectorPlayer", root, SampleParity.steps(root));
        if (!SampleParity.recordIfAsked(FIXTURE, script)) {
            SampleParity.assertMatchesRecorded(root, script);
        }
    }
}
