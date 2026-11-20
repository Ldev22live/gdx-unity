package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.agent.DefaultAgentTools;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutor;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanStep;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The assistant's {@code model_edit_check} tool (roadmap M3c.4): the 3D Modeling capability notice as machine-readable outputs. */
class ModelEditCheckToolTest {
    private EditorState editorState;
    private AgentToolContext context;
    private ModelEditCheckTool tool;
    private Entity house;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        context = new AgentToolContext(editorState);
        tool = new ModelEditCheckTool();
        house = editorState.getActiveScene().createEntity("House");
        house.addComponent(new ModelComponent("models/house.glb"));
    }

    private static Map<String, Object> entity(Object id) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("entityId", id);
        return parameters;
    }

    @Test
    void shouldReportThatAHousesMeshCannotBeEditedYetButCanBeSculptedAsACopy() {
        AgentToolResult result = tool.execute(entity(house.getId().toString()), context);

        assertTrue(result.isSuccess(), result.getMessage());
        Map<String, Object> outputs = result.getOutputs();
        assertEquals(house.getId().toString(), outputs.get("entityId"));
        assertEquals("House", outputs.get("name"));
        assertEquals("mesh-planned", outputs.get("capability"));
        assertEquals(false, outputs.get("meshEditing"));
        assertEquals(true, outputs.get("sculptCopy"));
        assertEquals(false, outputs.get("materialSlot"));
        assertEquals("models/house.glb", outputs.get("model"));
        assertEquals(ModelingHandOff.MESH_PLANNED_TITLE, outputs.get("title"));
        assertEquals(ModelingHandOff.noticeFor(house).lines(), outputs.get("notice"), "the same sentences a person reads");
        assertEquals(ModelingHandOff.noticeFor(house).summary(), result.getMessage());
    }

    @Test
    void shouldUseTheSelectionWhenNoEntityIsNamed() {
        Entity box = editorState.getActiveScene().createEntity("Box");
        box.addComponent(new MeshPrimitiveComponent());
        editorState.setSelectedEntity(box);

        AgentToolResult result = tool.execute(new LinkedHashMap<>(), context);

        assertEquals("Box", result.getOutputs().get("name"));
        assertEquals(true, result.getOutputs().get("materialSlot"));

        editorState.setSelectedEntity(null);
        assertEquals("no-selection", tool.execute(null, context).getOutputs().get("capability"));
    }

    @Test
    void shouldRefuseAnUnknownOrMalformedEntityId() {
        assertEquals(List.of("Invalid UUID format for 'entityId': nope"), tool.validateParameters(entity("nope"), context));
        UUID missing = UUID.randomUUID();
        assertEquals(List.of("Entity with ID '" + missing + "' not found in active scene"),
            tool.validateParameters(entity(missing), context));
        assertFalse(tool.execute(entity("nope"), context).isSuccess());
    }

    @Test
    void shouldRunAsAReadOnlyPlanStepFromTheDefaultToolsWithoutAnUndoStep() {
        AgentPlan plan = new AgentPlan("Can I reshape the house?");
        plan.addStep(new AgentPlanStep("s1", ModelEditCheckTool.ID, entity(house.getId().toString())));

        AgentPlanExecutionResult result = new AgentPlanExecutor(DefaultAgentTools.createDefaultRegistry()).executePlan(plan, context);

        assertTrue(result.isSuccess(), result.getMessage());
        assertTrue(tool.isReadOnly());
        assertFalse(editorState.getCommandHistory().canUndo(), "checking changes nothing");
    }
}
