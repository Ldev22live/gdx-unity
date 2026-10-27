package io.github.ldev22live.gdxunity.editor.core.agent;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutor;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanParser;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanStep;
import io.github.ldev22live.gdxunity.editor.core.agent.tools.*;
import io.github.ldev22live.gdxunity.editor.core.command.CompoundCommand;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AgentPlanEngineTest {

    private EditorState editorState;
    private AgentToolContext context;
    private AgentToolRegistry registry;
    private AgentPlanExecutor executor;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        context = new AgentToolContext(editorState);
        registry = new AgentToolRegistry();

        // Register default tools
        registry.registerTool(new SceneInspectTool());
        registry.registerTool(new EntityCreateTool());
        registry.registerTool(new EntityRenameTool());
        registry.registerTool(new EntityDeleteTool());
        registry.registerTool(new TransformSetTool());
        registry.registerTool(new ComponentAddTool());

        executor = new AgentPlanExecutor(registry);
    }

    @Test
    void shouldRegisterAndResolveDefaultTools() {
        assertEquals(6, registry.getToolCount());
        assertTrue(registry.hasTool(SceneInspectTool.ID));
        assertTrue(registry.hasTool(EntityCreateTool.ID));
        assertTrue(registry.hasTool(EntityRenameTool.ID));
        assertTrue(registry.hasTool(EntityDeleteTool.ID));
        assertTrue(registry.hasTool(TransformSetTool.ID));
        assertTrue(registry.hasTool(ComponentAddTool.ID));

        // Duplicate registration should throw
        assertThrows(IllegalArgumentException.class, () -> registry.registerTool(new SceneInspectTool()));
    }

    @Test
    void shouldExecuteSceneInspectToolWithoutMutation() {
        Entity e1 = editorState.createEntity("CoreEntity");
        editorState.clearSelection();

        AgentPlan plan = new AgentPlan("Inspect Scene");
        plan.addStep(new AgentPlanStep("step1", SceneInspectTool.ID, Collections.emptyMap()));

        AgentPlanExecutionResult result = executor.executePlan(plan, context);
        assertTrue(result.isSuccess());
        assertNotNull(result.getStepOutputs().get("step1"));

        // Verify entity count unchanged
        assertEquals(1, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldExecuteMultiStepPlanWithReferencesAndSingleUndo() {
        // Initial empty scene
        editorState.newScene("Plan Test Scene");
        assertEquals(0, editorState.getActiveScene().getEntityCount());

        AgentPlan plan = new AgentPlan("Build Player and Ground");

        // Step 1: Create Player
        Map<String, Object> p1 = new LinkedHashMap<>();
        p1.put("name", "PlayerRaw");
        plan.addStep(new AgentPlanStep("s1_create_player", EntityCreateTool.ID, p1));

        // Step 2: Rename Player using $ref
        Map<String, Object> p2 = new LinkedHashMap<>();
        p2.put("entityId", "$ref:s1_create_player.entityId");
        p2.put("name", "HeroPlayer");
        plan.addStep(new AgentPlanStep("s2_rename_player", EntityRenameTool.ID, p2));

        // Step 3: Transform Player using $ref
        Map<String, Object> p3 = new LinkedHashMap<>();
        p3.put("entityId", "$ref:s1_create_player.entityId");
        p3.put("posX", 150.0);
        p3.put("posY", 200.0);
        plan.addStep(new AgentPlanStep("s3_transform_player", TransformSetTool.ID, p3));

        // Step 4: Add ShapeRendererComponent using $ref
        Map<String, Object> p4 = new LinkedHashMap<>();
        p4.put("entityId", "$ref:s1_create_player.entityId");
        p4.put("componentType", "ShapeRendererComponent");
        Map<String, Object> compProps = new LinkedHashMap<>();
        compProps.put("width", 50.0);
        compProps.put("height", 80.0);
        compProps.put("color", "#FF0000");
        p4.put("properties", compProps);
        plan.addStep(new AgentPlanStep("s4_add_renderer", ComponentAddTool.ID, p4));

        AgentPlanExecutionResult result = executor.executePlan(plan, context);
        assertTrue(result.isSuccess(), "Plan execution should succeed: " + result.getMessage());

        // Verify scene state after execution
        assertEquals(1, editorState.getActiveScene().getEntityCount());
        Entity hero = editorState.getActiveScene().findEntityByName("HeroPlayer");
        assertNotNull(hero);
        assertEquals(150.0f, hero.getTransform().getLocalPosition().x, 0.001f);
        assertEquals(200.0f, hero.getTransform().getLocalPosition().y, 0.001f);
        assertNotNull(hero.getComponent(ShapeRendererComponent.class));

        // Test Single Undo: Entire plan should be reversed in 1 undo!
        assertTrue(editorState.undo());
        assertEquals(0, editorState.getActiveScene().getEntityCount());
        assertNull(editorState.getActiveScene().findEntityByName("HeroPlayer"));

        // Test Redo: Entire plan should be restored in 1 redo!
        assertTrue(editorState.redo());
        assertEquals(1, editorState.getActiveScene().getEntityCount());
        Entity restoredHero = editorState.getActiveScene().findEntityByName("HeroPlayer");
        assertNotNull(restoredHero);
        assertEquals(150.0f, restoredHero.getTransform().getLocalPosition().x, 0.001f);
    }

    @Test
    void shouldRejectPlanWithValidationErrorsBeforeExecutingAnyMutations() {
        editorState.newScene("Validation Test");
        assertEquals(0, editorState.getActiveScene().getEntityCount());

        AgentPlan plan = new AgentPlan("Invalid Plan");

        // Step 1: Valid entity create
        Map<String, Object> p1 = new LinkedHashMap<>();
        p1.put("name", "ShouldNotBeCreated");
        plan.addStep(new AgentPlanStep("step1", EntityCreateTool.ID, p1));

        // Step 2: Tool that does not exist
        plan.addStep(new AgentPlanStep("step2", "nonexistent_fake_tool", Collections.emptyMap()));

        AgentPlanExecutionResult result = executor.executePlan(plan, context);
        assertFalse(result.isSuccess());
        assertEquals(AgentPlanExecutionResult.Status.VALIDATION_FAILED, result.getStatus());

        // Crucial safety check: Step 1 must NOT have executed! Scene must remain completely empty!
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldRollbackPriorOperationsIfMidExecutionFailureOccurs() {
        editorState.newScene("Rollback Test");
        assertEquals(0, editorState.getActiveScene().getEntityCount());

        AgentPlan plan = new AgentPlan("Failing Mid-Execution Plan");

        // Step 1: Create Entity
        Map<String, Object> p1 = new LinkedHashMap<>();
        p1.put("name", "TemporaryEntity");
        plan.addStep(new AgentPlanStep("s1", EntityCreateTool.ID, p1));

        // Step 2: Transform with reference to non-existent output key (will cause reference resolution failure)
        Map<String, Object> p2 = new LinkedHashMap<>();
        p2.put("entityId", "$ref:s1.nonexistentKey");
        plan.addStep(new AgentPlanStep("s2", TransformSetTool.ID, p2));

        AgentPlanExecutionResult result = executor.executePlan(plan, context);
        assertFalse(result.isSuccess());
        assertEquals(AgentPlanExecutionResult.Status.EXECUTION_FAILED_ROLLED_BACK, result.getStatus());

        // Verify that Step 1 was cleanly rolled back!
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldFilterReadOnlyAndMutatingTools() {
        List<AgentTool> readOnly = registry.getReadOnlyTools();
        List<AgentTool> mutating = registry.getMutatingTools();

        assertEquals(1, readOnly.size());
        assertEquals(SceneInspectTool.ID, readOnly.get(0).getId());

        assertEquals(5, mutating.size());
        assertTrue(mutating.stream().anyMatch(t -> t.getId().equals(EntityCreateTool.ID)));
        assertTrue(mutating.stream().anyMatch(t -> t.getId().equals(EntityDeleteTool.ID)));
    }

    @Test
    void shouldEnforceConfirmationForDestructiveOperations() {
        Entity entity = editorState.createEntity("VictimEntity");
        UUID entityId = entity.getId();

        AgentPlan plan = new AgentPlan("Delete Victim");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("entityId", entityId.toString());
        plan.addStep(new AgentPlanStep("delete_step", EntityDeleteTool.ID, params));

        // When confirmation is NOT approved in context, execution must be rejected before mutation
        context.setConfirmationApproved(false);
        AgentPlanExecutionResult result = executor.executePlan(plan, context);

        assertFalse(result.isSuccess());
        assertEquals(AgentPlanExecutionResult.Status.VALIDATION_FAILED, result.getStatus());
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "CONFIRMATION_REQUIRED".equals(d.getCode())));
        assertNotNull(editorState.getActiveScene().findEntityById(entityId), "Entity must not be deleted without confirmation");

        // When confirmation is approved, plan execution should succeed
        context.setConfirmationApproved(true);
        AgentPlanExecutionResult approvedResult = executor.executePlan(plan, context);

        assertTrue(approvedResult.isSuccess());
        assertNull(editorState.getActiveScene().findEntityById(entityId), "Entity should be deleted once confirmed");

        // Undo should restore the entity
        assertTrue(editorState.undo());
        assertNotNull(editorState.getActiveScene().findEntityById(entityId));
    }

    @Test
    void shouldRejectPlanWithUnresolvedOrCyclicDependencies() {
        AgentPlan plan = new AgentPlan("Cyclic Plan");

        // Step depends on itself
        AgentPlanStep selfDep = new AgentPlanStep("step1", SceneInspectTool.ID, Collections.emptyMap(), List.of("step1"));
        plan.addStep(selfDep);

        AgentPlanExecutionResult result = executor.executePlan(plan, context);
        assertFalse(result.isSuccess());
        assertEquals(AgentPlanExecutionResult.Status.VALIDATION_FAILED, result.getStatus());
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "CYCLIC_DEPENDENCY".equals(d.getCode())));
    }

    @Test
    void shouldRejectPlanWithForwardDependencies() {
        AgentPlan plan = new AgentPlan("Forward Dep Plan");

        // Step depends on future step
        AgentPlanStep step1 = new AgentPlanStep("step1", SceneInspectTool.ID, Collections.emptyMap(), List.of("step2"));
        AgentPlanStep step2 = new AgentPlanStep("step2", SceneInspectTool.ID, Collections.emptyMap());
        plan.addStep(step1);
        plan.addStep(step2);

        AgentPlanExecutionResult result = executor.executePlan(plan, context);
        assertFalse(result.isSuccess());
        assertEquals(AgentPlanExecutionResult.Status.VALIDATION_FAILED, result.getStatus());
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "UNRESOLVED_DEPENDENCY".equals(d.getCode())));
    }

    @Test
    void shouldRejectPlanExceedingMaxSteps() {
        AgentPlan plan = new AgentPlan("Too Many Steps");
        for (int i = 0; i <= AgentPlan.MAX_STEPS; i++) {
            plan.addStep(new AgentPlanStep("step_" + i, SceneInspectTool.ID, Collections.emptyMap()));
        }

        AgentPlanExecutionResult result = executor.executePlan(plan, context);
        assertFalse(result.isSuccess());
        assertEquals(AgentPlanExecutionResult.Status.VALIDATION_FAILED, result.getStatus());
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "STEP_LIMIT_EXCEEDED".equals(d.getCode())));
    }

    @Test
    void shouldParsePlanFromJsonSuccessfully() throws Exception {
        AgentPlanParser parser = new AgentPlanParser();
        String json = """
        {
            "version": 1,
            "summary": "Sample JSON Plan",
            "steps": [
                {
                    "stepId": "create_box",
                    "tool": "entity_create",
                    "parameters": {
                        "name": "Box"
                    },
                    "dependencies": []
                }
            ]
        }
        """;

        AgentPlan parsed = parser.parse(json);
        assertNotNull(parsed);
        assertEquals(1, parsed.getVersion());
        assertEquals("Sample JSON Plan", parsed.getSummary());
        assertEquals(1, parsed.getSteps().size());
        assertEquals("create_box", parsed.getSteps().get(0).getStepId());
        assertEquals(EntityCreateTool.ID, parsed.getSteps().get(0).getTool());
    }

    @Test
    void shouldRejectMalformedOrUnsupportedJsonPlans() {
        AgentPlanParser parser = new AgentPlanParser();

        assertThrows(AgentPlanParser.AgentPlanParseException.class, () -> parser.parse(""));
        assertThrows(AgentPlanParser.AgentPlanParseException.class, () -> parser.parse("{ broken json "));
        assertThrows(AgentPlanParser.AgentPlanParseException.class, () -> parser.parse("{\"version\": 999}"));
    }
}
