package io.github.ldev22live.gdxunity.editor.desktop;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.provider.MockModelProvider;
import io.github.ldev22live.gdxunity.editor.core.agent.provider.ModelConfiguration;
import io.github.ldev22live.gdxunity.editor.core.agent.provider.ModelProviderRegistry;
import io.github.ldev22live.gdxunity.editor.core.agent.provider.OllamaModelProvider;
import io.github.ldev22live.gdxunity.editor.core.agent.service.AssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless integration test verifying the assistant orchestration pipeline,
 * provider switching, approval requirement, and execution in editor-desktop context.
 */
class AssistantIntegrationTest {

    private EditorState editorState;
    private AssistantService assistantService;
    private MockModelProvider mockProvider;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        var toolRegistry = io.github.ldev22live.gdxunity.editor.core.agent.DefaultAgentTools.createDefaultRegistry();
        var providerRegistry = new ModelProviderRegistry();
        mockProvider = new MockModelProvider();
        providerRegistry.registerProvider(mockProvider);
        providerRegistry.registerProvider(new OllamaModelProvider());

        assistantService = new AssistantService(editorState, toolRegistry, providerRegistry);
        assistantService.setActiveConfiguration(ModelConfiguration.forMock());
    }

    @Test
    void shouldExecuteEndToEndAssistantWorkflowWithApprovalAndUndo() throws Exception {
        editorState.newScene("Desktop Assistant Test Scene");
        assertEquals(0, editorState.getActiveScene().getEntityCount());

        mockProvider.setDefaultPlanJson("""
        {
          "version": 1,
          "summary": "Create Desktop Entity",
          "steps": [
            {
              "stepId": "s1",
              "tool": "entity_create",
              "parameters": {
                "name": "DesktopEntity"
              }
            }
          ]
        }
        """);

        // 1. Submit prompt asynchronously
        CompletableFuture<Void> future = assistantService.submitPromptAsync("Spawn DesktopEntity");
        future.get();

        // 2. Validate pending state & ensure zero scene mutations occurred before approval
        assertEquals(AssistantService.State.PLAN_READY_FOR_REVIEW, assistantService.getState());
        assertEquals(0, editorState.getActiveScene().getEntityCount(), "Scene must NOT change before explicit approval!");

        // 3. User approves plan
        AgentPlanExecutionResult result = assistantService.approveAndExecute();
        assertTrue(result.isSuccess());
        assertEquals(AssistantService.State.EXECUTION_COMPLETED, assistantService.getState());
        assertEquals(1, editorState.getActiveScene().getEntityCount());
        assertNotNull(editorState.getActiveScene().findEntityByName("DesktopEntity"));

        // 4. Single Undo restores scene
        assertTrue(editorState.undo());
        assertEquals(0, editorState.getActiveScene().getEntityCount());

        // 5. Single Redo reapplies plan
        assertTrue(editorState.redo());
        assertEquals(1, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldHandleProviderSwitchingCorrectly() {
        assertEquals("mock", assistantService.getActiveConfiguration().getProviderId());

        // Switch to ollama
        assistantService.setActiveConfiguration(ModelConfiguration.forOllama("mistral"));
        assertEquals("ollama", assistantService.getActiveConfiguration().getProviderId());
        assertEquals("mistral", assistantService.getActiveConfiguration().getModelName());

        // Switch back to mock
        assistantService.setActiveConfiguration(ModelConfiguration.forMock());
        assertEquals("mock", assistantService.getActiveConfiguration().getProviderId());
    }

    @Test
    void shouldCleanUpResourcesOnDispose() {
        assistantService.dispose();
        assertEquals(AssistantService.State.IDLE, assistantService.getState());
    }
}
