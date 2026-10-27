package io.github.ldev22live.gdxunity.editor.core.agent.executor;

import io.github.ldev22live.gdxunity.editor.core.agent.plan.PlanValidationDiagnostic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured outcome returned by AgentPlanExecutor.
 */
public class AgentPlanExecutionResult {
    public enum Status {
        SUCCESS,
        VALIDATION_FAILED,
        EXECUTION_FAILED_ROLLED_BACK,
        EXECUTION_FAILED_ROLLBACK_ERROR
    }

    private final Status status;
    private final String message;
    private final List<PlanValidationDiagnostic> diagnostics;
    private final Map<String, Object> stepOutputs;
    private final String failedStepId;
    private final Throwable error;

    private AgentPlanExecutionResult(Status status, String message, List<PlanValidationDiagnostic> diagnostics,
                                     Map<String, Object> stepOutputs, String failedStepId, Throwable error) {
        this.status = status;
        this.message = (message != null) ? message : "";
        this.diagnostics = (diagnostics != null) ? Collections.unmodifiableList(diagnostics) : Collections.emptyList();
        this.stepOutputs = (stepOutputs != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(stepOutputs)) : Collections.emptyMap();
        this.failedStepId = failedStepId;
        this.error = error;
    }

    public static AgentPlanExecutionResult success(String message, Map<String, Object> outputs) {
        return new AgentPlanExecutionResult(Status.SUCCESS, message, Collections.emptyList(), outputs, null, null);
    }

    public static AgentPlanExecutionResult validationFailed(String message, List<PlanValidationDiagnostic> diagnostics) {
        return new AgentPlanExecutionResult(Status.VALIDATION_FAILED, message, diagnostics, Collections.emptyMap(), null, null);
    }

    public static AgentPlanExecutionResult rolledBack(String failedStepId, String message, Throwable error) {
        return new AgentPlanExecutionResult(Status.EXECUTION_FAILED_ROLLED_BACK, message, Collections.emptyList(), Collections.emptyMap(), failedStepId, error);
    }

    public static AgentPlanExecutionResult rollbackError(String failedStepId, String message, Throwable error) {
        return new AgentPlanExecutionResult(Status.EXECUTION_FAILED_ROLLBACK_ERROR, message, Collections.emptyList(), Collections.emptyMap(), failedStepId, error);
    }

    public Status getStatus() { return status; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public String getMessage() { return message; }
    public List<PlanValidationDiagnostic> getDiagnostics() { return diagnostics; }
    public Map<String, Object> getStepOutputs() { return stepOutputs; }
    public String getFailedStepId() { return failedStepId; }
    public Throwable getError() { return error; }
}
