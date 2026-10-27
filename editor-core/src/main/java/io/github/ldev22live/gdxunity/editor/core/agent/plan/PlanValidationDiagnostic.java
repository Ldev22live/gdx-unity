package io.github.ldev22live.gdxunity.editor.core.agent.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Diagnostic record representing a validation finding for an AgentPlan.
 */
public class PlanValidationDiagnostic {
    public enum Severity {
        ERROR,
        WARNING
    }

    private final Severity severity;
    private final String stepId;
    private final String toolId;
    private final String code;
    private final String message;

    public PlanValidationDiagnostic(Severity severity, String stepId, String toolId, String code, String message) {
        this.severity = severity;
        this.stepId = stepId;
        this.toolId = toolId;
        this.code = code;
        this.message = message;
    }

    public static PlanValidationDiagnostic error(String stepId, String toolId, String code, String message) {
        return new PlanValidationDiagnostic(Severity.ERROR, stepId, toolId, code, message);
    }

    public static PlanValidationDiagnostic warning(String stepId, String toolId, String code, String message) {
        return new PlanValidationDiagnostic(Severity.WARNING, stepId, toolId, code, message);
    }

    public Severity getSeverity() { return severity; }
    public String getStepId() { return stepId; }
    public String getToolId() { return toolId; }
    public String getCode() { return code; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "[" + severity + "] (" + code + ") step='" + stepId + "' tool='" + toolId + "': " + message;
    }
}
