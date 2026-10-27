package io.github.ldev22live.gdxunity.editor.core.agent;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.PlanValidationDiagnostic;
import io.github.ldev22live.gdxunity.editor.core.agent.provider.*;
import io.github.ldev22live.gdxunity.editor.core.agent.service.AssistantService;
import io.github.ldev22live.gdxunity.editor.core.agent.service.PlanExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AssistantServiceTest {

    private EditorState editorState;
    private AgentToolRegistry toolRegistry;
    private ModelProviderRegistry providerRegistry;
    private MockModelProvider mockProvider;
    private AssistantService service;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        toolRegistry = DefaultAgentTools.createDefaultRegistry();
        providerRegistry = new ModelProviderRegistry();

        mockProvider = new MockModelProvider();
        providerRegistry.registerProvider(mockProvider);
        providerRegistry.registerProvider(new OllamaModelProvider());

        service = new AssistantService(editorState, toolRegistry, providerRegistry);
        service.setActiveConfiguration(ModelConfiguration.forMock());
    }

    @Test
    void shouldRegisterAndResolveProviders() {
        assertEquals(2, providerRegistry.getProviderCount());
        assertTrue(providerRegistry.hasProvider("mock"));
        assertTrue(providerRegistry.hasProvider("ollama"));

        assertThrows(IllegalArgumentException.class, () -> providerRegistry.registerProvider(new MockModelProvider()));
    }

    @Test
    void shouldValidateProviderConfigurations() throws Exception {
        mockProvider.validateConfiguration(ModelConfiguration.forMock());

        OllamaModelProvider ollama = new OllamaModelProvider();
        ollama.validateConfiguration(ModelConfiguration.forOllama("llama3"));

        assertThrows(ModelProviderException.class, () -> ollama.validateConfiguration(new ModelConfiguration("ollama", "")));
    }

    @Test
    void shouldExtractJsonFromMarkdownCodeBlocksOrComments() {
        String wrappedJson = """
        Here is the plan for your scene:
        ```json
        {
          "version": 1,
          "summary": "Create Box",
          "steps": []
        }
        ```
        Hope this helps!
        """;

        String extracted = PlanExtractor.extractJson(wrappedJson);
        assertTrue(extracted.startsWith("{"));
        assertTrue(extracted.endsWith("}"));
        assertTrue(extracted.contains("\"summary\": \"Create Box\""));
    }

    @Test
    void shouldSubmitPromptAndRequireExplicitApprovalBeforeMutation() throws Exception {
        editorState.newScene("Assistant Test Scene");
        assertEquals(0, editorState.getActiveScene().getEntityCount());

        mockProvider.setDefaultPlanJson("""
        {
          "version": 1,
          "summary": "Spawn Player Hero",
          "steps": [
            {
              "stepId": "s1",
              "tool": "entity_create",
              "parameters": {
                "name": "HeroKnight"
              }
            }
          ]
        }
        """);

        CompletableFuture<Void> future = service.submitPromptAsync("Please create a player knight");
        future.get(); // wait for completion

        // State must now be PLAN_READY_FOR_REVIEW
        assertEquals(AssistantService.State.PLAN_READY_FOR_REVIEW, service.getState());
        assertNotNull(service.getCurrentProposedPlan());
        assertEquals(0, service.getCurrentDiagnostics().size());

        // CRUCIAL SAFETY: No mutation must have occurred before explicit approval!
        assertEquals(0, editorState.getActiveScene().getEntityCount(), "Scene must NOT be mutated before user approval!");

        // Now user explicitly approves plan
        AgentPlanExecutionResult result = service.approveAndExecute();
        assertTrue(result.isSuccess());
        assertEquals(AssistantService.State.EXECUTION_COMPLETED, service.getState());

        // Verify entity now exists
        assertEquals(1, editorState.getActiveScene().getEntityCount());
        assertNotNull(editorState.getActiveScene().findEntityByName("HeroKnight"));

        // Verify single Undo reverses the change
        assertTrue(editorState.undo());
        assertEquals(0, editorState.getActiveScene().getEntityCount());

        // Verify Redo re-applies the change
        assertTrue(editorState.redo());
        assertEquals(1, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldDiscardPlanAndLeaveSceneUntouched() throws Exception {
        editorState.newScene("Discard Test Scene");
        assertEquals(0, editorState.getActiveScene().getEntityCount());

        CompletableFuture<Void> future = service.submitPromptAsync("Make something");
        future.get();

        assertEquals(AssistantService.State.PLAN_READY_FOR_REVIEW, service.getState());

        // User discards plan
        service.cancelOrDiscard();
        assertEquals(AssistantService.State.IDLE, service.getState());
        assertNull(service.getCurrentProposedPlan());

        // Scene remains untouched
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldRejectInvalidPlanFromModelWithoutExecutingMutations() throws Exception {
        editorState.newScene("Invalid Model Response");

        mockProvider.setDefaultPlanJson("""
        {
          "version": 1,
          "summary": "Bad Plan",
          "steps": [
            {
              "stepId": "step1",
              "tool": "nonexistent_dangerous_tool",
              "parameters": {}
            }
          ]
        }
        """);

        CompletableFuture<Void> future = service.submitPromptAsync("Run forbidden tool");
        future.get();

        assertEquals(AssistantService.State.PLAN_INVALID, service.getState());
        assertTrue(service.getCurrentDiagnostics().size() > 0);

        // Disallow execution
        assertThrows(IllegalStateException.class, () -> service.approveAndExecute());

        // Scene remains untouched
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldHandleProviderFailureGracefully() throws Exception {
        mockProvider.setSimulateFailure(true, "Ollama connection refused");

        CompletableFuture<Void> future = service.submitPromptAsync("Test failure");
        future.get();

        assertEquals(AssistantService.State.ERROR, service.getState());
        assertNotNull(service.getLastErrorMessage());
        assertTrue(service.getLastErrorMessage().contains("Ollama connection refused"));
    }

    @Test
    void shouldRejectMalformedProviderOutput() throws Exception {
        editorState.newScene("Malformed");
        mockProvider.setDefaultPlanJson("Sorry, I cannot do that { not json");
        service.submitPromptAsync("anything").get();
        assertEquals(AssistantService.State.ERROR, service.getState());
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldRejectUnsupportedPlanVersion() throws Exception {
        mockProvider.setDefaultPlanJson("{\"version\": 2, \"summary\": \"x\", \"steps\": []}");
        service.submitPromptAsync("anything").get();
        assertEquals(AssistantService.State.ERROR, service.getState());
    }

    @Test
    void shouldRejectInvalidEntityReferenceAndParameters() throws Exception {
        editorState.newScene("Bad refs");
        mockProvider.setDefaultPlanJson("""
        {"version":1,"summary":"bad","steps":[
          {"stepId":"a","tool":"entity_rename","parameters":{"entityId":"00000000-0000-0000-0000-000000000001","name":"X"}},
          {"stepId":"b","tool":"transform_set","parameters":{"entityId":"$ref:zzz.entityId","posX":1e9}}
        ]}
        """);
        service.submitPromptAsync("bad").get();
        assertEquals(AssistantService.State.PLAN_INVALID, service.getState());
        assertTrue(service.getCurrentDiagnostics().size() >= 2);
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldTimeOutSlowProviderWithoutMutation() throws Exception {
        editorState.newScene("Timeout");
        mockProvider.setSimulatedDelay(Duration.ofSeconds(5));
        service.setActiveConfiguration(new ModelConfiguration("mock", "m", null, null, Duration.ofMillis(100), null));
        service.submitPromptAsync("slow").get();
        assertEquals(AssistantService.State.ERROR, service.getState());
        assertTrue(service.getLastErrorMessage().contains("timed out"));
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldRemainResponsiveAndCancelPendingRequest() throws Exception {
        editorState.newScene("Cancel");
        mockProvider.setSimulatedDelay(Duration.ofSeconds(5));
        long start = System.nanoTime();
        CompletableFuture<Void> f = service.submitPromptAsync("slow");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 1000, "submit must not block the caller (render) thread");
        assertEquals(AssistantService.State.REQUESTING_PROVIDER, service.getState());

        service.cancelOrDiscard();
        assertEquals(AssistantService.State.IDLE, service.getState());
        assertNull(service.getCurrentProposedPlan());
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldReportRolledBackExecutionFailure() throws Exception {
        editorState.newScene("Rollback");
        mockProvider.setDefaultPlanJson("""
        {"version":1,"summary":"fails late","steps":[
          {"stepId":"s1","tool":"entity_create","parameters":{"name":"Temp"}},
          {"stepId":"s2","tool":"transform_set","parameters":{"entityId":"$ref:s1.missingKey"}}
        ]}
        """);
        service.submitPromptAsync("x").get();
        assertEquals(AssistantService.State.PLAN_READY_FOR_REVIEW, service.getState());
        AgentPlanExecutionResult r = service.approveAndExecute();
        assertFalse(r.isSuccess());
        assertEquals(AgentPlanExecutionResult.Status.EXECUTION_FAILED_ROLLED_BACK, r.getStatus());
        assertEquals(AssistantService.State.ERROR, service.getState());
        assertEquals(0, editorState.getActiveScene().getEntityCount());
        assertFalse(editorState.undo(), "failed plan must not be in command history");
    }

    @Test
    void shouldCleanUpOnDispose() throws Exception {
        mockProvider.setSimulatedDelay(Duration.ofSeconds(5));
        CompletableFuture<Void> f = service.submitPromptAsync("slow");
        service.dispose();
        assertEquals(AssistantService.State.IDLE, service.getState());
        assertNull(service.getCurrentProposedPlan());
    }

    @Test
    void ollamaAdapterShouldParseResponseFromLocalHttpStub() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        String body = "{\"model\":\"llama3\",\"response\":\"{\\\"version\\\":1,\\\"summary\\\":\\\"s\\\",\\\"steps\\\":[]}\",\"done\":true}";
        server.createContext("/api/generate", ex -> {
            byte[] b = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/api/tags", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            OllamaModelProvider ollama = new OllamaModelProvider();
            ModelConfiguration cfg = ModelConfiguration.forOllama(url, "llama3", Duration.ofSeconds(5));
            assertTrue(ollama.testConnection(cfg).get());
            ModelResponse resp = ollama.generateAsync(ModelRequest.builder().userPrompt("hi").build(), cfg).get();
            assertTrue(resp.isSuccess(), resp.getErrorMessage());
            assertTrue(resp.getContent().contains("\"version\":1"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ollamaAdapterShouldReportModelNotFoundAndUnavailable() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", ex -> { ex.sendResponseHeaders(404, -1); ex.close(); });
        server.start();
        int port = server.getAddress().getPort();
        try {
            OllamaModelProvider ollama = new OllamaModelProvider();
            ModelResponse resp = ollama.generateAsync(ModelRequest.builder().userPrompt("hi").build(),
                ModelConfiguration.forOllama("http://127.0.0.1:" + port, "nope", Duration.ofSeconds(5))).get();
            assertFalse(resp.isSuccess());
            assertTrue(resp.getErrorMessage().contains("not found"));
        } finally {
            server.stop(0);
        }
        ModelResponse down = new OllamaModelProvider().generateAsync(ModelRequest.builder().userPrompt("hi").build(),
            ModelConfiguration.forOllama("http://127.0.0.1:" + port, "x", Duration.ofSeconds(5))).get();
        assertFalse(down.isSuccess());
    }

    @Test
    void shouldIgnoreStaleResponseFromEarlierPromptWhenNewPromptSubmitted() throws Exception {
        editorState.newScene("Stale Test Scene");
        mockProvider.setSimulatedDelay(Duration.ofMillis(300));
        mockProvider.setDefaultPlanJson("{\"version\":1,\"summary\":\"Old Plan\",\"steps\":[]}");

        // Prompt 1 launched (slow)
        CompletableFuture<Void> f1 = service.submitPromptAsync("First prompt");

        // User immediately cancels and launches Prompt 2 with different plan
        service.cancelOrDiscard();
        mockProvider.setSimulatedDelay(Duration.ofMillis(10));
        mockProvider.setDefaultPlanJson("{\"version\":1,\"summary\":\"New Plan\",\"steps\":[]}");
        CompletableFuture<Void> f2 = service.submitPromptAsync("Second prompt");

        f2.get(); // wait for second prompt
        assertEquals(AssistantService.State.PLAN_READY_FOR_REVIEW, service.getState());
        assertEquals("New Plan", service.getCurrentProposedPlan().getSummary());

        // Wait for first prompt future to complete in background; verify it did NOT overwrite "New Plan"
        try {
            f1.get();
        } catch (Exception ignored) {}

        assertEquals("New Plan", service.getCurrentProposedPlan().getSummary(), "Stale response must NOT overwrite active plan!");
    }

    @Test
    void shouldRejectExecutionIfSceneChangedBetweenValidationAndApproval() throws Exception {
        editorState.newScene("Scene Alpha");
        mockProvider.setDefaultPlanJson("""
        {"version":1,"summary":"Create Alpha Entity","steps":[
          {"stepId":"s1","tool":"entity_create","parameters":{"name":"AlphaEnt"}}
        ]}
        """);

        service.submitPromptAsync("create on alpha").get();
        assertEquals(AssistantService.State.PLAN_READY_FOR_REVIEW, service.getState());

        // External event: user switches active scene to "Scene Beta"
        editorState.newScene("Scene Beta");

        // Attempting to execute the plan validated against Alpha on Beta must be safely rejected
        AgentPlanExecutionResult result = service.approveAndExecute();
        assertFalse(result.isSuccess());
        assertEquals(AgentPlanExecutionResult.Status.VALIDATION_FAILED, result.getStatus());
        assertEquals(AssistantService.State.ERROR, service.getState());

        // Crucial safety check: Beta scene must have 0 entities!
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }

    @Test
    void shouldRejectExecutionIfReferencedEntityDeletedBetweenValidationAndApproval() throws Exception {
        editorState.newScene("Precondition Scene");
        var entity = editorState.createEntity("TargetToDelete");
        assertEquals(1, editorState.getActiveScene().getEntityCount());

        mockProvider.setDefaultPlanJson("""
        {"version":1,"summary":"Rename Target","steps":[
          {"stepId":"s1","tool":"entity_rename","parameters":{"entityId":"%s","name":"NewName"}}
        ]}
        """.formatted(entity.getId().toString()));

        service.submitPromptAsync("rename target").get();
        assertEquals(AssistantService.State.PLAN_READY_FOR_REVIEW, service.getState());

        // External event: user removes TargetToDelete in the scene before approving the plan
        editorState.getActiveScene().removeEntity(entity);
        assertEquals(0, editorState.getActiveScene().getEntityCount());

        // Pre-execution revalidation must detect that TargetToDelete is gone and safely reject execution
        AgentPlanExecutionResult result = service.approveAndExecute();
        assertFalse(result.isSuccess());
        assertEquals(AgentPlanExecutionResult.Status.VALIDATION_FAILED, result.getStatus());
        assertEquals(AssistantService.State.ERROR, service.getState());
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }
}
