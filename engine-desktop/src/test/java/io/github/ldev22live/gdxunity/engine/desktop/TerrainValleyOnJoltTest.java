package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3.3's done-when on the shipped sample (TD-087): in {@code terrain-3d.json}, Hero stands on the valley, and after the valley
 * is reshaped walks across it and jumps, staying on the ground the whole way. The game runtime's renderer gives the scene its
 * height fields; here, without a GL context, the test does what it does ({@code TerrainHeightFields} on a library of the project).
 * Sliding down slopes steeper than the limit is tested on Jolt in {@code engine-physics}.
 */
class TerrainValleyOnJoltTest {
    private static final float FRAME = 1f / 60f;
    private static final String SCENE = "samples/terrain-3d.json";
    /** The class name Hero's stand-in script is bound by. */
    private static final String HERO_SCRIPT = "test.TerrainHero";
    /** Where a default character's centre is above the ground it stands on: half of 80 plus the 30 radius. */
    private static final float STANDING_HEIGHT = 70f;
    /** "Within a step of the ground" (TD-087's test plan): a default character's step height. */
    private static final float WITHIN_A_STEP = 30f;
    /** Height of the bump raised in front of Hero, and its radius: about 13 degrees at its steepest, under the 45 degree limit. */
    private static final float BUMP_HEIGHT = 60f;
    private static final float BUMP_RADIUS = 400f;

    private final AssetSource project = AssetSource.directory(new File("../assets"));
    private final List<String> errors = new ArrayList<>();
    private TerrainLibrary library;
    private GameSession session;
    private Scene scene;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        library = new TerrainLibrary(project);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.dispose();
        }
    }

    /** Hero's walker graph reads the keyboard, so a test script takes its place; everything else is the sample as shipped. */
    private void start(Supplier<ScriptComponent> heroScript) throws IOException {
        scene = GameSession.loadScene(project, SCENE);
        Entity hero = scene.findEntityByName("Hero");
        assertNotNull(hero.getComponent(GraphScriptComponent.class), "the sample walks Hero with a graph");
        hero.removeComponent(hero.getComponent(GraphScriptComponent.class));
        hero.addComponent(new EntityScriptBindingComponent("", HERO_SCRIPT, ScriptLanguage.JAVA));
        scene.setHeightFields(new TerrainHeightFields(library));
        session = new GameSession(project, scene, new ScriptRegistry().register(HERO_SCRIPT, heroScript), new GameSession.Log() {
            @Override
            public void info(String message) {
            }

            @Override
            public void error(String message) {
                errors.add(message);
            }
        });
        assertEquals(List.of(), session.start(), "the sample starts without problems");
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            session.update(FRAME);
        }
    }

    private Terrain valley() {
        Entity valley = scene.findEntityByName("Valley");
        assertNotNull(valley.getComponent(TerrainCollider3DComponent.class), "the sample's valley is walkable");
        return library.get(valley.getComponent(TerrainComponent.class).getTerrainPath());
    }

    private float groundUnder(Entity entity) {
        Vector3 position = entity.getTransform().getWorldPosition();
        return valley().heightAt(position.x, position.z);
    }

    @Test
    void shouldStandHeroOnTheValley() throws IOException {
        start(() -> new ScriptComponent() { });
        Entity hero = scene.findEntityByName("Hero");

        run(240);

        assertTrue(scene.getPhysics3D().isCharacterGrounded(hero), "on the ground after 4 seconds");
        assertEquals(groundUnder(hero) + STANDING_HEIGHT, hero.getTransform().getWorldPosition().y, WITHIN_A_STEP,
            "standing on the terrain, not fallen through it");
        assertEquals(List.of(), session.getRuntimeErrors());
        assertEquals(List.of(), errors);
    }

    @Test
    void shouldWalkAndJumpAcrossAReshapedValley() throws IOException {
        // Reshape before Play, as a person would with the brush: a smooth bump on Hero's path along +x
        Terrain terrain = library.get("terrains/Valley.terrain");
        float bumpX = 300f;
        float bumpZ = 1500f;
        for (int iz = 0; iz < terrain.getResolutionZ(); iz++) {
            for (int ix = 0; ix < terrain.getResolutionX(); ix++) {
                double distance = Math.hypot(terrain.xAt(ix) - bumpX, terrain.zAt(iz) - bumpZ);
                if (distance < BUMP_RADIUS) {
                    float lift = (float) (BUMP_HEIGHT * 0.5 * (1.0 + Math.cos(Math.PI * distance / BUMP_RADIUS)));
                    terrain.setHeight(ix, iz, terrain.getHeight(ix, iz) + lift);
                }
            }
        }
        WalkingScript[] walker = new WalkingScript[1];
        start(() -> walker[0] = new WalkingScript());
        Entity hero = scene.findEntityByName("Hero");
        float startX = hero.getTransform().getWorldPosition().x;
        float worstAboveGround = 0f;

        for (int frame = 0; frame < 4 * 60; frame++) {
            run(1);
            Vector3 position = hero.getTransform().getWorldPosition();
            float above = position.y - STANDING_HEIGHT - groundUnder(hero);
            if (frame > 60 && scene.getPhysics3D().isCharacterGrounded(hero)) {
                worstAboveGround = Math.max(worstAboveGround, Math.abs(above));
            }
            assertTrue(above > -WITHIN_A_STEP, "never sinks into the reshaped ground (frame " + frame + ", " + above + ")");
        }

        assertTrue(hero.getTransform().getWorldPosition().x > startX + 600f, "walked more than 6 m: " + hero.getTransform().getWorldPosition());
        assertEquals(1, walker[0].jumps, "jumped once");
        assertTrue(worstAboveGround < WITHIN_A_STEP, "while on the ground it follows the terrain, within " + worstAboveGround);
        assertTrue(scene.getPhysics3D().isCharacterGrounded(hero), "and stands on it at the end");
        assertEquals(List.of(), session.getRuntimeErrors());
    }
}
