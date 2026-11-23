package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutor;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanStep;
import io.github.ldev22live.gdxunity.editor.core.material.MaterialAssets;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The agent's way to put a material on a shape (roadmap M2.2, chart rule 8), run as a plan step. */
class MaterialAssignToolTest {
    @TempDir
    Path project;

    private EditorState editorState;
    private AgentToolContext context;
    private MaterialLibrary library;
    private MaterialAssignTool tool;
    private Entity box;
    private String rock;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        context = new AgentToolContext(editorState);
        library = new MaterialLibrary(AssetSource.directory(project.toFile()));
        tool = new MaterialAssignTool(library);
        rock = MaterialAssets.createNew(project.toFile(), library, "Rock");
        box = editorState.getActiveScene().createEntity("Box");
        box.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 100f, 100f, Color.WHITE));
    }

    private static Map<String, Object> parameters(Object entityId, String path) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("entityId", entityId);
        parameters.put("materialPath", path);
        return parameters;
    }

    @Test
    void shouldApplyTheMaterialAsAPlanStepThatUndoesInOneStep() {
        AgentToolRegistry registry = new AgentToolRegistry();
        registry.registerTool(tool);
        AgentPlan plan = new AgentPlan("Make the box rock");
        plan.addStep(new AgentPlanStep("s1", MaterialAssignTool.ID, parameters(box.getId().toString(), rock)));

        AgentPlanExecutionResult result = new AgentPlanExecutor(registry).executePlan(plan, context);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(rock, box.getComponent(MeshPrimitiveComponent.class).getMaterialPath());
        assertTrue(editorState.undo());
        assertEquals("", box.getComponent(MeshPrimitiveComponent.class).getMaterialPath());
        assertFalse(editorState.getCommandHistory().canUndo());
    }

    @Test
    void shouldReportOutputsWhenRunDirectly() throws Exception {
        var result = tool.execute(parameters(box.getId(), rock), context);

        assertTrue(result.isSuccess());
        assertEquals(rock, result.getOutputs().get("materialPath"));
        assertEquals(box.getId().toString(), result.getOutputs().get("entityId"));
    }

    @Test
    void shouldAcceptABlankPathToRemoveTheMaterial() {
        assertEquals(List.of(), tool.validateParameters(parameters(box.getId().toString(), ""), context));
    }

    @Test
    void shouldRefuseWithTheReasonForAnythingItCannotApply() {
        Entity robot = editorState.getActiveScene().createEntity("Robot");
        robot.addComponent(new ModelComponent("models/robot.glb"));

        assertEquals(List.of("Robot has no material slot: only shapes (Mesh Primitive) take a material so far"),
            tool.validateParameters(parameters(robot.getId().toString(), rock), context));
        assertEquals(List.of("'materialPath' must be a .material file: textures/rock.png"),
            tool.validateParameters(parameters(box.getId().toString(), "textures/rock.png"), context));
        List<String> missing = tool.validateParameters(parameters(box.getId().toString(), "materials/Gone.material"), context);
        assertEquals(List.of(library.getError("materials/Gone.material")), missing, "the library's own reason, read after the attempt");
        assertEquals(List.of("Invalid UUID format for 'entityId'"), tool.validateParameters(parameters("not-a-uuid", rock), context));
        assertEquals(2, tool.validateParameters(new LinkedHashMap<>(), context).size(), "both parameters are required");
    }
}
