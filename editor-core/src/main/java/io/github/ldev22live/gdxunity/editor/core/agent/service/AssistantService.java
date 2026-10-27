package io.github.ldev22live.gdxunity.editor.core.agent.service;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutor;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanParser;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanValidator;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.PlanValidationDiagnostic;
import io.github.ldev22live.gdxunity.editor.core.agent.provider.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central orchestrator connecting user input, ModelProviders, plan validation,
 * approval states, and the deterministic AgentPlanExecutor.
 */
public class AssistantService {
    public enum State {
        IDLE,
        REQUESTING_PROVIDER,
        PLAN_READY_FOR_REVIEW,
        PLAN_INVALID,
        EXECUTING_PLAN,
        EXECUTION_COMPLETED,
        ERROR
    }

    public interface StateListener {
        void onStateChanged(State oldState, State newState);
        void onPlanEvaluated(AgentPlan plan, List<PlanValidationDiagnostic> diagnostics);
        void onExecutionFinished(AgentPlanExecutionResult result);
        void onError(String errorMessage);
    }

    private final EditorState editorState;
    private final AgentToolRegistry toolRegistry;
    private final ModelProviderRegistry providerRegistry;
    private final AgentPlanParser planParser;
    private final AgentPlanValidator planValidator;
    private final AgentPlanExecutor planExecutor;
    private final ConversationHistory conversationHistory = new ConversationHistory();

    private final List<StateListener> listeners = new CopyOnWriteArrayList<>();

    private volatile State state = State.IDLE;
    private ModelConfiguration activeConfiguration = ModelConfiguration.forMock();
    private CompletableFuture<?> pendingRequest;
    private long activeRequestId = 0L;
    private AgentPlan currentProposedPlan;
    private List<PlanValidationDiagnostic> currentDiagnostics = Collections.emptyList();
    private io.github.ldev22live.gdxunity.engine.scene.Scene planValidatedScene;
    private AgentPlanExecutionResult lastExecutionResult;
    private String lastErrorMessage;

    public AssistantService(EditorState editorState, AgentToolRegistry toolRegistry, ModelProviderRegistry providerRegistry) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry cannot be null");
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry cannot be null");
        this.planParser = new AgentPlanParser();
        this.planValidator = new AgentPlanValidator(toolRegistry);
        this.planExecutor = new AgentPlanExecutor(toolRegistry);
    }

    public void addListener(StateListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(StateListener listener) {
        listeners.remove(listener);
    }

    public State getState() {
        return state;
    }

    public ModelConfiguration getActiveConfiguration() {
        return activeConfiguration;
    }

    public void setActiveConfiguration(ModelConfiguration configuration) {
        this.activeConfiguration = Objects.requireNonNull(configuration, "configuration cannot be null");
    }

    public ConversationHistory getConversationHistory() {
        return conversationHistory;
    }

    public AgentPlan getCurrentProposedPlan() {
        return currentProposedPlan;
    }

    public List<PlanValidationDiagnostic> getCurrentDiagnostics() {
        return currentDiagnostics;
    }

    public AgentPlanExecutionResult getLastExecutionResult() {
        return lastExecutionResult;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public ModelProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    public AgentToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * Submits a user natural language request asynchronously to the active model provider.
     */
    public synchronized CompletableFuture<Void> submitPromptAsync(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        if (state == State.REQUESTING_PROVIDER || state == State.EXECUTING_PLAN) {
            throw new IllegalStateException("Assistant is currently busy in state: " + state);
        }

        conversationHistory.addUserMessage(prompt);
        currentProposedPlan = null;
        currentDiagnostics = Collections.emptyList();
        planValidatedScene = null;
        lastExecutionResult = null;
        lastErrorMessage = null;

        setState(State.REQUESTING_PROVIDER);
        final long requestId = ++activeRequestId;

        ModelProvider provider = providerRegistry.getProvider(activeConfiguration.getProviderId())
            .orElse(null);

        if (provider == null) {
            String err = "Model provider '" + activeConfiguration.getProviderId() + "' is not registered";
            failWithError(err);
            return CompletableFuture.completedFuture(null);
        }

        // Build context and prompt
        String systemInstruction = AgentPromptContextBuilder.buildSystemInstruction(toolRegistry);
        Map<String, Object> sceneContext = AgentPromptContextBuilder.buildSceneContext(editorState.getActiveScene(), 50);

        List<ModelRequest.Message> historyMessages = new ArrayList<>();
        for (ConversationHistory.Entry entry : conversationHistory.getEntries()) {
            ModelRequest.Message.Role role = switch (entry.role()) {
                case USER -> ModelRequest.Message.Role.USER;
                case ASSISTANT -> ModelRequest.Message.Role.ASSISTANT;
                case SYSTEM -> ModelRequest.Message.Role.SYSTEM;
            };
            historyMessages.add(new ModelRequest.Message(role, entry.text()));
        }

        ModelRequest request = ModelRequest.builder()
            .systemPrompt(systemInstruction)
            .history(historyMessages)
            .userPrompt(prompt)
            .sceneContext(sceneContext)
            .jsonExpected(true)
            .timeout(activeConfiguration.getTimeout())
            .build();

        CompletableFuture<ModelResponse> future = provider.generateAsync(request, activeConfiguration)
            .orTimeout(activeConfiguration.getTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        this.pendingRequest = future;

        return future.thenAccept(response -> {
            synchronized (AssistantService.this) {
                // Ignore stale or superseded responses
                if (requestId != activeRequestId || state != State.REQUESTING_PROVIDER) return;

                if (!response.isSuccess()) {
                    failWithError("Provider error: " + response.getErrorMessage());
                    return;
                }

                String cleanJson = PlanExtractor.extractJson(response.getContent());
                AgentPlan plan;
                try {
                    plan = planParser.parse(cleanJson);
                } catch (Exception e) {
                    failWithError("Failed to parse plan JSON: " + e.getMessage());
                    conversationHistory.addAssistantMessage("Error: Could not parse response as structured plan.", null);
                    return;
                }

                // Upfront plan validation against current active scene
                AgentToolContext context = new AgentToolContext(editorState);
                List<PlanValidationDiagnostic> diagnostics = planValidator.validate(plan, context);
                boolean hasErrors = diagnostics.stream().anyMatch(d -> d.getSeverity() == PlanValidationDiagnostic.Severity.ERROR);

                currentProposedPlan = plan;
                currentDiagnostics = diagnostics;
                planValidatedScene = editorState.getActiveScene();

                conversationHistory.addAssistantMessage(plan.getSummary(), plan.getSummary());

                if (hasErrors) {
                    setState(State.PLAN_INVALID);
                } else {
                    setState(State.PLAN_READY_FOR_REVIEW);
                }

                for (StateListener l : listeners) {
                    l.onPlanEvaluated(plan, diagnostics);
                }
            }
        }).exceptionally(ex -> {
            synchronized (AssistantService.this) {
                if (requestId == activeRequestId && state == State.REQUESTING_PROVIDER) {
                    Throwable cause = (ex instanceof java.util.concurrent.CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
                    if (cause instanceof java.util.concurrent.TimeoutException) {
                        failWithError("Provider request timed out after " + activeConfiguration.getTimeout().toSeconds() + "s");
                    } else {
                        failWithError("Request failed: " + cause.getMessage());
                    }
                }
            }
            return null;
        });
    }

    /**
     * Cancels outstanding requests and detaches listeners. Called when the editor closes.
     */
    public synchronized void dispose() {
        if (pendingRequest != null && !pendingRequest.isDone()) {
            pendingRequest.cancel(true);
        }
        pendingRequest = null;
        currentProposedPlan = null;
        listeners.clear();
        state = State.IDLE;
    }

    /**
     * Cancels any active provider generation or discards an unapproved plan.
     */
    public synchronized void cancelOrDiscard() {
        if (pendingRequest != null && !pendingRequest.isDone()) {
            pendingRequest.cancel(true);
        }
        pendingRequest = null;
        currentProposedPlan = null;
        currentDiagnostics = Collections.emptyList();
        conversationHistory.addSystemNotice("Operation cancelled / plan discarded.");
        setState(State.IDLE);
    }

    /**
     * Executes the currently proposed plan through AgentPlanExecutor.
     * Must be called on the main editor thread (or caller ensures thread safety).
     */
    public synchronized AgentPlanExecutionResult approveAndExecute() {
        if (state != State.PLAN_READY_FOR_REVIEW || currentProposedPlan == null) {
            throw new IllegalStateException("No valid plan is currently pending approval");
        }

        // 1. Scene Compatibility Check: Ensure scene has not switched since validation
        if (planValidatedScene != null && planValidatedScene != editorState.getActiveScene()) {
            failWithError("Active scene changed since plan was proposed. Please discard and generate a new plan.");
            return AgentPlanExecutionResult.validationFailed("Scene mismatch: Active scene changed from '"
                + planValidatedScene.getName() + "' to '"
                + (editorState.getActiveScene() != null ? editorState.getActiveScene().getName() : "None")
                + "' before approval.", Collections.emptyList());
        }

        // 2. Pre-execution Revalidation Check: Ensure entities or preconditions haven't been deleted/invalidated
        AgentToolContext context = new AgentToolContext(editorState);
        List<PlanValidationDiagnostic> revalidationDiagnostics = planValidator.validate(currentProposedPlan, context);
        boolean hasRevalidationErrors = revalidationDiagnostics.stream().anyMatch(d -> d.getSeverity() == PlanValidationDiagnostic.Severity.ERROR);
        if (hasRevalidationErrors) {
            currentDiagnostics = revalidationDiagnostics;
            setState(State.PLAN_INVALID);
            for (StateListener l : listeners) {
                l.onPlanEvaluated(currentProposedPlan, revalidationDiagnostics);
            }
            failWithError("Plan is no longer valid against the current scene state: " + revalidationDiagnostics.get(0).getMessage());
            return AgentPlanExecutionResult.validationFailed("Pre-execution revalidation failed", revalidationDiagnostics);
        }

        setState(State.EXECUTING_PLAN);

        AgentPlanExecutionResult result = planExecutor.executePlan(currentProposedPlan, context);
        lastExecutionResult = result;

        if (result.isSuccess()) {
            conversationHistory.addSystemNotice("Plan executed successfully: " + result.getMessage());
            setState(State.EXECUTION_COMPLETED);
        } else {
            conversationHistory.addSystemNotice("Plan execution failed [" + result.getStatus() + "]: " + result.getMessage()
                + (result.getStatus() == AgentPlanExecutionResult.Status.EXECUTION_FAILED_ROLLBACK_ERROR
                    ? " WARNING: rollback failed; the scene may be partially modified."
                    : " Scene was rolled back to its pre-plan state."));
            setState(State.ERROR);
        }

        for (StateListener l : listeners) {
            l.onExecutionFinished(result);
        }

        return result;
    }

    private void failWithError(String message) {
        lastErrorMessage = message;
        conversationHistory.addSystemNotice(message);
        setState(State.ERROR);
        for (StateListener l : listeners) {
            l.onError(message);
        }
    }

    private void setState(State newState) {
        State oldState = this.state;
        this.state = newState;
        for (StateListener l : listeners) {
            l.onStateChanged(oldState, newState);
        }
    }
}
