package io.github.ldev22live.gdxunity.editor.core.environment;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.agent.DefaultAgentTools;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutor;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanStep;
import io.github.ldev22live.gdxunity.editor.core.agent.tools.EnvironmentPresetTool;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmosphereComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmospherePreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterBodyComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterPreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.serialization.AtmosphereComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.environment.serialization.WaterBodyComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3.9's done-when, "a preset applies and persists": sky and water presets as undo steps, through a save and reopen,
 * and through the {@code environment_preset} agent tool (TD-090).
 */
class EnvironmentPresetsTest {
    private EditorState editorState;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new AtmosphereComponentSerializer());
        registry.register(new WaterBodyComponentSerializer());
        editorState = new EditorState(new SceneSerializer(registry));
        editorState.setActiveScene(new Scene("Valley"));
    }

    private Scene scene() {
        return editorState.getActiveScene();
    }

    @Test
    void shouldMakeASkyEntityForTheFirstPresetAndRemoveItOnUndo() {
        assertEquals("None", EnvironmentPresets.describeSky(scene()));

        assertTrue(editorState.executeCommand(EnvironmentPresets.applySky(editorState, AtmospherePreset.SUNSET)));

        Entity sky = EnvironmentPresets.findSkyEntity(scene());
        assertNotNull(sky);
        assertEquals(EnvironmentPresets.SKY_ENTITY_NAME, sky.getName());
        assertEquals("Sunset", EnvironmentPresets.describeSky(scene()));
        assertTrue(editorState.undo());
        assertNull(EnvironmentPresets.findSkyEntity(scene()), "one undo step removes it again");
        assertTrue(editorState.redo());
        assertEquals("Sunset", EnvironmentPresets.describeSky(scene()));
    }

    @Test
    void shouldChangeTheExistingSkyInOneUndoStepThatRestoresEverySetting() {
        editorState.executeCommand(EnvironmentPresets.applySky(editorState, AtmospherePreset.CLEAR_DAY));
        AtmosphereComponent sky = EnvironmentPresets.findSkyEntity(scene()).getComponent(AtmosphereComponent.class);
        sky.setSunlight(1.7f); // a custom tweak the undo must bring back
        AtmosphereComponent before = sky.copy();

        editorState.executeCommand(EnvironmentPresets.applySky(editorState, AtmospherePreset.RAIN));

        assertEquals(1, scene().getAllEntities().size(), "the same entity takes the preset");
        assertSame(sky, EnvironmentPresets.findSkyEntity(scene()).getComponent(AtmosphereComponent.class));
        assertEquals(AtmosphereComponent.Weather.RAIN, sky.getWeather());
        assertTrue(editorState.undo());
        assertTrue(sky.sameSettings(before, 0f), "every setting is back, the tweak included");
        assertEquals("Custom", EnvironmentPresets.describeSky(scene()));
    }

    @Test
    void shouldEnableADisabledSkyWhenAPresetIsApplied() {
        AtmosphereComponent sky = new AtmosphereComponent();
        sky.setEnabled(false);
        scene().createEntity("Old Sky").addComponent(sky);

        editorState.executeCommand(EnvironmentPresets.applySky(editorState, AtmospherePreset.NIGHT));

        assertTrue(sky.isEnabled());
        assertEquals("Night", EnvironmentPresets.describeSky(scene()));
        editorState.undo();
        assertFalse(sky.isEnabled());
    }

    @Test
    void shouldAddWaterAndChangeItsLookButNotItsSize() {
        editorState.executeCommand(EnvironmentPresets.addWater(editorState, new Vector3(0f, 120f, 0f), WaterPreset.LAKE));
        Entity entity = scene().findEntityByName(EnvironmentPresets.WATER_ENTITY_NAME);
        assertNotNull(entity);
        assertEquals(120f, entity.getTransform().getWorldPosition().y, 1e-4f);
        WaterBodyComponent water = entity.getComponent(WaterBodyComponent.class);
        water.setSize(9000f, 3000f);

        editorState.executeCommand(EnvironmentPresets.applyWater(editorState, entity, WaterPreset.OCEAN));

        assertEquals(WaterPreset.OCEAN, WaterPreset.matching(water).orElseThrow());
        assertEquals(9000f, water.getWidth(), 1e-4f);
        editorState.undo();
        assertEquals(WaterPreset.LAKE, WaterPreset.matching(water).orElseThrow());
    }

    @Test
    void shouldKeepAppliedPresetsThroughSaveAndReopen() {
        editorState.executeCommand(EnvironmentPresets.applySky(editorState, AtmospherePreset.SNOW));
        editorState.executeCommand(EnvironmentPresets.addWater(editorState, new Vector3(), WaterPreset.ICY));

        String saved = editorState.getSerializer().toJson(scene());
        Scene reopened = editorState.getSerializer().fromJson(saved);

        assertEquals("Snow", EnvironmentPresets.describeSky(reopened));
        WaterBodyComponent water = reopened.findEntityByName(EnvironmentPresets.WATER_ENTITY_NAME).getComponent(WaterBodyComponent.class);
        assertEquals(WaterPreset.ICY, WaterPreset.matching(water).orElseThrow());
        assertEquals(saved, editorState.getSerializer().toJson(reopened), "saving the reopened scene writes the same file");
    }

    @Test
    void shouldApplyASkyPresetFromAnAgentPlanAsOneUndoStep() {
        AgentPlan plan = new AgentPlan("Make it rain");
        plan.addStep(new AgentPlanStep("s1", EnvironmentPresetTool.ID, parameters("sky", "rain", null)));

        AgentPlanExecutionResult result = new AgentPlanExecutor(DefaultAgentTools.createDefaultRegistry())
            .executePlan(plan, new AgentToolContext(editorState));

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("Rain", EnvironmentPresets.describeSky(scene()));
        assertTrue(editorState.undo());
        assertEquals("None", EnvironmentPresets.describeSky(scene()));
    }

    @Test
    void shouldChangeOrAddWaterThroughTheAgentTool() throws Exception {
        EnvironmentPresetTool tool = new EnvironmentPresetTool();
        AgentToolContext context = new AgentToolContext(editorState);
        AgentToolResult added = tool.execute(parameters("water", "Tropical", null), context);
        assertTrue(added.isSuccess(), added.getMessage());
        Entity entity = scene().findEntityByName(EnvironmentPresets.WATER_ENTITY_NAME);

        AgentToolResult changed = tool.execute(parameters("water", "swamp", entity.getId().toString()), context);

        assertTrue(changed.isSuccess(), changed.getMessage());
        assertEquals(WaterPreset.SWAMP, WaterPreset.matching(entity.getComponent(WaterBodyComponent.class)).orElseThrow());
        assertEquals(1, scene().getAllEntities().size());
    }

    @Test
    void shouldRefuseUnknownTargetsPresetsAndEntitiesWithoutWater() {
        EnvironmentPresetTool tool = new EnvironmentPresetTool();
        AgentToolContext context = new AgentToolContext(editorState);
        Entity rock = scene().createEntity("Rock");

        assertEquals(List.of("'target' must be 'sky', 'water' or 'post': 'lava'"), tool.validateParameters(parameters("lava", "Rain", null), context));
        assertTrue(tool.validateParameters(parameters("sky", "Hurricane", null), context).get(0).startsWith("Unknown sky preset 'Hurricane'"));
        assertTrue(tool.validateParameters(parameters("water", "Lava", null), context).get(0).startsWith("Unknown water preset 'Lava'"));
        assertEquals(List.of("Rock has no Water Body"), tool.validateParameters(parameters("water", "Lake", rock.getId().toString()), context));
        assertTrue(tool.validateParameters(parameters("sky", "Clear Day", null), context).isEmpty());
    }

    private static Map<String, Object> parameters(String target, String preset, String entityId) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("target", target);
        parameters.put("preset", preset);
        if (entityId != null) {
            parameters.put("entityId", entityId);
        }
        return parameters;
    }
}
