package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap MP.3: the opt-in copy of the Platformer + 2.5D sample whose box ground is a flat terrain plays like the original, on real Jolt
 * through the game runtime's {@link GameSession}. Both get the same keys; the runner's path may differ only by the few units Jolt's
 * height field and box contacts differ by, never by a missed platform or a fall.
 */
class MigratedPlatformerProfileTest {
    private static final float FRAME = 1f / 60f;
    private static final String ORIGINAL = "samples/platformer-profile-25d.json";
    private static final String MIGRATED = "samples/platformer-profile-terrain-25d.json";
    /** Jolt lets a resting character sink up to about 2 cm into what it stands on; a box and a height field differ by that much. */
    private static final float SLOP = 5f;
    /** A default character's centre above what it stands on: half of 80 plus the 30 radius. */
    private static final float STANDING = 70f;

    private final AssetSource project = AssetSource.directory(new File("../assets"));
    private final List<GameSession> sessions = new ArrayList<>();

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(GameSession::dispose);
    }

    /** A started session whose keys are {@code held}. */
    private Scene start(String path, Set<Integer> held) throws IOException {
        Scene scene = GameSession.loadScene(project, path);
        scene.setHeightFields(new TerrainHeightFields(new TerrainLibrary(project)));
        GameSession session = new GameSession(project, scene, new ScriptRegistry(), GameSession.CONSOLE);
        session.setInput(held::contains);
        assertEquals(List.of(), session.start(), path);
        sessions.add(session);
        return scene;
    }

    @Test
    void shouldReplaceOnlyTheGroundAndKeepEveryOtherEntityAndId() throws IOException {
        Scene original = start(ORIGINAL, Set.of());
        Scene migrated = start(MIGRATED, Set.of());

        assertEquals(original.getEntityCount(), migrated.getEntityCount());
        for (Entity entity : original.getAllEntities()) {
            assertNotNull(migrated.findEntityById(entity.getId()), entity.getName() + " keeps its id");
        }
        Entity ground = migrated.findEntityByName("Ground");
        assertEquals(original.findEntityByName("Ground").getId(), ground.getId(), "the ground keeps its id too");
        assertTrue(ground.getAllComponents().stream().anyMatch(c -> c.getClass().getSimpleName().equals("TerrainComponent")),
            "the ground is a terrain");
    }

    @Test
    void shouldRunJumpAndStopLikeTheOriginal() throws IOException {
        Set<Integer> held = new HashSet<>();
        Scene original = start(ORIGINAL, held);
        Scene migrated = start(MIGRATED, held);
        Entity runnerA = original.findEntityByName("Runner");
        Entity runnerB = migrated.findEntityByName("Runner");
        GameSession a = sessions.get(0);
        GameSession b = sessions.get(1);

        float worst = 0f;
        float highest = 0f;
        for (int frame = 0; frame < 600; frame++) {
            held.clear();
            if (frame >= 30 && frame < 420) {
                held.add(Keys.D);
            }
            if (frame >= 90 && frame < 100 || frame >= 250 && frame < 260) {
                held.add(Keys.SPACE);
            }
            if (frame >= 480) {
                held.add(Keys.A);
            }
            a.update(FRAME);
            b.update(FRAME);
            Vector3 pa = runnerA.getTransform().getWorldPosition();
            Vector3 pb = runnerB.getTransform().getWorldPosition();
            worst = Math.max(worst, Math.max(Math.abs(pa.x - pb.x), Math.abs(pa.y - pb.y)));
            highest = Math.max(highest, pa.y);
            assertEquals(pa.x, pb.x, SLOP, "frame " + frame + ": " + pa + " against " + pb);
            assertEquals(pa.y, pb.y, SLOP, "frame " + frame + ": " + pa + " against " + pb);
            assertEquals(0f, pb.z, 0.01f, "frame " + frame + ": on the play plane");
        }
        assertTrue(highest > 120f + STANDING - SLOP, "the run reached a platform: highest " + highest);
        assertTrue(migrated.getPhysics3D().isCharacterGrounded(runnerB), "standing at the end");
        assertEquals(List.of(), a.getRuntimeErrors());
        assertEquals(List.of(), b.getRuntimeErrors());
        System.out.println("MP.3 2.5D: largest difference between the runs " + worst + " units");
    }

    @Test
    void shouldStandOnTheTerrainWhereTheBoxTopWas() throws IOException {
        Scene migrated = start(MIGRATED, Set.of());
        for (int frame = 0; frame < 60; frame++) {
            sessions.get(0).update(FRAME);
        }
        Entity runner = migrated.findEntityByName("Runner");
        assertTrue(migrated.getPhysics3D().isCharacterGrounded(runner));
        assertEquals(STANDING, runner.getTransform().getWorldPosition().y, SLOP, "the terrain's top is at 0, as the box's was");
    }
}
