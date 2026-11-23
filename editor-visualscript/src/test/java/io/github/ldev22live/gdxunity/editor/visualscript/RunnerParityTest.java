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
 * The Runner player's graph (TD-072) does what the Java script does, frame for frame: lane changes once per press, the slide, jumps once
 * per press and only on the ground, gravity and landing, and R starting over. The scenario and the script's recorded positions are in
 * {@code runner-trajectory.json}, which the web test replays; there it also counts the "jump" sounds.
 */
class RunnerParityTest {
    private static final File FIXTURE = new File("src/test/resources/runner-trajectory.json");
    private static final String SCRIPT = "scripts/samples/runner/RunnerPlayer.java";
    private static final String GRAPH = "samples/RunnerPlayer.graph";
    private static final float GROUND = 25f;

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

        List<Vector3> script = SampleParity.runScript(temp, SCRIPT, "samples.runner.RunnerPlayer", root, steps);
        List<Vector3> graph = SampleParity.runGraph(GRAPH, root, steps);

        SampleParity.assertSamePositions(script, graph);
    }

    @Test
    void shouldUseTheThreeLanesAndJumpAndLand() throws IOException {
        JsonValue root = SampleParity.read(FIXTURE);

        List<Vector3> graph = SampleParity.runGraph(GRAPH, root, SampleParity.steps(root));

        float highest = 0f;
        java.util.Set<Integer> lanes = new java.util.HashSet<>();
        for (Vector3 position : graph) {
            highest = Math.max(highest, position.y);
            if (position.x == -130f || position.x == 0f || position.x == 130f) {
                lanes.add(Math.round(position.x));
            }
        }
        assertTrue(highest > GROUND + 100f, "a jump leaves the ground: " + highest);
        assertEquals(3, lanes.size(), "the player stands in all three lanes: " + lanes);
        assertEquals(GROUND, graph.get(graph.size() - 1).y, 0.0001f, "and ends on the ground");
    }

    /** With GDXUNITY_RECORD_FIXTURES=1 the scenario file is written from the script's run instead. */
    @Test
    void shouldMatchTheRecordedPositionsOfTheScript(@TempDir Path temp) throws IOException {
        JsonValue root = SampleParity.read(FIXTURE);
        List<Vector3> script = SampleParity.runScript(temp, SCRIPT, "samples.runner.RunnerPlayer", root, SampleParity.steps(root));
        if (!SampleParity.recordIfAsked(FIXTURE, script)) {
            SampleParity.assertMatchesRecorded(root, script);
        }
    }
}
