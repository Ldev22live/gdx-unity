package io.github.ldev22live.gdxunity.editor.core.environment;

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
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessPreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessVolumeComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.serialization.PostProcessVolumeComponentSerializer;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Post Process Volumes in the editor (roadmap M6.2, TD-091): the catalog entry, presets as one undo step, save and reopen, and the
 * {@code environment_preset} agent tool's {@code post} target.
 */
class PostProcessPresetsTest {
    private EditorState editorState;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new PostProcessVolumeComponentSerializer());
        editorState = new EditorState(new SceneSerializer(registry));
        editorState.setActiveScene(new Scene("Street"));
    }

    private Scene scene() {
        return editorState.getActiveScene();
    }

    @Test
    void shouldOfferThePostProcessVolumeUnderRendering3D() {
        ComponentCatalog catalog = ComponentCatalog.createDefault();
        ComponentCatalog.Entry entry = catalog.search("post process").stream()
            .filter(candidate -> candidate.displayName().equals("Post Process Volume")).findFirst().orElseThrow();
        assertEquals("Rendering 3D", entry.category());
        Entity entity = scene().createEntity("Volume");
        assertTrue(catalog.addTo(editorState, entity, entry));
        PostProcessVolumeComponent volume = entity.getComponent(PostProcessVolumeComponent.class);
        assertNotNull(volume);
        assertFalse(volume.hasEffects(), "a new volume changes nothing until an effect is switched on");
        assertFalse(catalog.addTo(editorState, entity, entry), "one per entity");
    }

    @Test
    void shouldMakeAGlobalVolumeForTheFirstScenePresetAndRemoveItOnUndo() {
        assertEquals("None", EnvironmentPresets.describePostProcess(scene()));

        assertTrue(editorState.executeCommand(EnvironmentPresets.applyScenePostProcess(editorState, PostProcessPreset.CINEMATIC)));

        Entity entity = EnvironmentPresets.findGlobalPostProcessEntity(scene());
        assertEquals(EnvironmentPresets.POST_PROCESS_ENTITY_NAME, entity.getName());
        assertTrue(entity.getComponent(PostProcessVolumeComponent.class).isGlobal());
        assertEquals("Cinematic", EnvironmentPresets.describePostProcess(scene()));
        assertTrue(editorState.undo());
        assertNull(EnvironmentPresets.findGlobalPostProcessEntity(scene()));
    }

    @Test
    void shouldChangeAVolumesEffectsInOneUndoStepButKeepItsPlaceAndReach() {
        PostProcessVolumeComponent volume = new PostProcessVolumeComponent();
        volume.setGlobal(false);
        volume.setSize(400f, 300f, 200f);
        volume.setPriority(4);
        PostProcessPreset.WARM.applyTo(volume);
        volume.setSaturation(1.6f); // a custom tweak the undo must bring back
        PostProcessVolumeComponent before = volume.copy();
        Entity room = scene().createEntity("Cellar");
        room.addComponent(volume);

        assertTrue(editorState.executeCommand(EnvironmentPresets.applyPostProcess(editorState, room, PostProcessPreset.NOIR)));

        assertEquals(PostProcessPreset.NOIR, PostProcessPreset.matching(volume).orElseThrow());
        assertFalse(volume.isGlobal());
        assertEquals(300f, volume.getSizeY());
        assertEquals(4, volume.getPriority());
        assertEquals("None", EnvironmentPresets.describePostProcess(scene()), "a box volume is not the scene's global one");
        assertTrue(editorState.undo());
        assertTrue(volume.sameEffects(before, 0f), "every effect is back, the tweak included");
    }

    @Test
    void shouldKeepAnAppliedLookThroughSaveAndReopen() {
        editorState.executeCommand(EnvironmentPresets.applyScenePostProcess(editorState, PostProcessPreset.DREAMY));

        String saved = editorState.getSerializer().toJson(scene());
        Scene reopened = editorState.getSerializer().fromJson(saved);

        assertEquals("Dreamy", EnvironmentPresets.describePostProcess(reopened));
        assertEquals(saved, editorState.getSerializer().toJson(reopened), "saving the reopened scene writes the same file");
    }

    @Test
    void shouldApplyAPostProcessingPresetFromAnAgentPlanAsOneUndoStep() {
        AgentPlan plan = new AgentPlan("Make it look like an old film");
        plan.addStep(new AgentPlanStep("s1", EnvironmentPresetTool.ID, parameters("post", "noir", null)));

        AgentPlanExecutionResult result = new AgentPlanExecutor(DefaultAgentTools.createDefaultRegistry())
            .executePlan(plan, new AgentToolContext(editorState));

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("Noir", EnvironmentPresets.describePostProcess(scene()));
        assertTrue(editorState.undo());
        assertEquals("None", EnvironmentPresets.describePostProcess(scene()));
    }

    @Test
    void shouldChangeANamedVolumeThroughTheToolAndRefuseOneWithoutAVolume() throws Exception {
        EnvironmentPresetTool tool = new EnvironmentPresetTool();
        AgentToolContext context = new AgentToolContext(editorState);
        Entity room = scene().createEntity("Cellar");
        PostProcessVolumeComponent volume = new PostProcessVolumeComponent();
        volume.setGlobal(false);
        room.addComponent(volume);
        Entity rock = scene().createEntity("Rock");

        AgentToolResult result = tool.execute(parameters("post", "Cold", room.getId().toString()), context);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(PostProcessPreset.COLD, PostProcessPreset.matching(volume).orElseThrow());
        assertEquals("None", result.getOutputs().get("post"));
        assertEquals(List.of("Rock has no Post Process Volume"),
            tool.validateParameters(parameters("post", "Cold", rock.getId().toString()), context));
        assertTrue(tool.validateParameters(parameters("post", "Sepia", null), context).get(0)
            .startsWith("Unknown post-processing preset 'Sepia'"));
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
