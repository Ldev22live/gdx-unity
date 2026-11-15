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
 * The Space Shooter ship's graph does what the Java script does, frame for frame (roadmap M1.2): WASD and the arrows fly the ship at 340
 * units a second inside its box, diagonals are not slowed, and opposite keys cancel. The scenario and the script's recorded positions are in
 * {@code shooter-trajectory.json}.
 */
class ShooterParityTest {
    private static final File FIXTURE = new File("src/test/resources/shooter-trajectory.json");
    private static final String SCRIPT = "scripts/samples/shooter/ShooterShip.java";
    private static final String GRAPH = "samples/ShooterShip.graph";
    /** The ship's box, as in the script. */
    private static final float MIN_X = 30f;
    private static final float MAX_X = 610f;
    private static final float MIN_Y = 40f;
    private static final float MAX_Y = 260f;

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
    void shouldFlyLikeTheScriptFrameForFrame(@TempDir Path temp) throws IOException {
        JsonValue root = SampleParity.read(FIXTURE);
        List<SampleParity.Step> steps = SampleParity.steps(root);

        List<Vector3> script = SampleParity.runScript(temp, SCRIPT, "samples.shooter.ShooterShip", root, steps);
        List<Vector3> graph = SampleParity.runGraph(GRAPH, root, steps);

        SampleParity.assertSamePositions(script, graph);
    }

    @Test
    void shouldReachEveryEdgeOfTheBoxAndNoFurther() throws IOException {
        JsonValue root = SampleParity.read(FIXTURE);

        List<Vector3> graph = SampleParity.runGraph(GRAPH, root, SampleParity.steps(root));

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Vector3 position : graph) {
            minX = Math.min(minX, position.x);
            maxX = Math.max(maxX, position.x);
            minY = Math.min(minY, position.y);
            maxY = Math.max(maxY, position.y);
        }
        assertEquals(MIN_X, minX, 0.0001f);
        assertEquals(MAX_X, maxX, 0.0001f);
        assertEquals(MIN_Y, minY, 0.0001f);
        assertEquals(MAX_Y, maxY, 0.0001f);
    }

    /** With GDXUNITY_RECORD_FIXTURES=1 the scenario file is written from the script's run instead. */
    @Test
    void shouldMatchTheRecordedPositionsOfTheScript(@TempDir Path temp) throws IOException {
        JsonValue root = SampleParity.read(FIXTURE);
        List<Vector3> script = SampleParity.runScript(temp, SCRIPT, "samples.shooter.ShooterShip", root, SampleParity.steps(root));
        if (!SampleParity.recordIfAsked(FIXTURE, script)) {
            SampleParity.assertMatchesRecorded(root, script);
        }
    }
}
