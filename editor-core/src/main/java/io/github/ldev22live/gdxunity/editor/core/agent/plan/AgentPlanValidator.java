package io.github.ldev22live.gdxunity.editor.core.agent.plan;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the syntax, tool availability, parameter contracts, step uniqueness,
 * and reference integrity of an AgentPlan prior to any execution.
 */
public class AgentPlanValidator {
    private static final Pattern REF_PATTERN = Pattern.compile("^\\$ref:([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+)$");

    private final AgentToolRegistry registry;

    public AgentPlanValidator(AgentToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    public List<PlanValidationDiagnostic> validate(AgentPlan plan, AgentToolContext context) {
        List<PlanValidationDiagnostic> diagnostics = new ArrayList<>();
        if (plan == null) {
            diagnostics.add(PlanValidationDiagnostic.error(null, null, "NULL_PLAN", "Plan is null"));
            return diagnostics;
        }

        if (plan.getVersion() != AgentPlan.CURRENT_VERSION) {
            diagnostics.add(PlanValidationDiagnostic.error(null, null, "UNSUPPORTED_VERSION",
                "Unsupported plan version: " + plan.getVersion()));
        }

        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            diagnostics.add(PlanValidationDiagnostic.warning(null, null, "EMPTY_PLAN", "Plan contains zero steps"));
            return diagnostics;
        }

        if (plan.getSteps().size() > AgentPlan.MAX_STEPS) {
            diagnostics.add(PlanValidationDiagnostic.error(null, null, "STEP_LIMIT_EXCEEDED",
                "Plan step count " + plan.getSteps().size() + " exceeds limit of " + AgentPlan.MAX_STEPS));
        }

        Set<String> declaredStepIds = new HashSet<>();

        for (int i = 0; i < plan.getSteps().size(); i++) {
            AgentPlanStep step = plan.getSteps().get(i);
            String stepId = step.getStepId();

            if (stepId == null || stepId.trim().isEmpty()) {
                diagnostics.add(PlanValidationDiagnostic.error(String.valueOf(i), step.getTool(), "MISSING_STEP_ID",
                    "Step at index " + i + " is missing a unique stepId"));
            } else if (declaredStepIds.contains(stepId)) {
                diagnostics.add(PlanValidationDiagnostic.error(stepId, step.getTool(), "DUPLICATE_STEP_ID",
                    "Duplicate stepId '" + stepId + "' found in plan"));
            } else {
                declaredStepIds.add(stepId);
            }

            String toolId = step.getTool();
            if (toolId == null || toolId.trim().isEmpty()) {
                diagnostics.add(PlanValidationDiagnostic.error(stepId, null, "MISSING_TOOL_ID",
                    "Step '" + stepId + "' does not specify a tool"));
                continue;
            }

            Optional<AgentTool> toolOpt = registry.getTool(toolId);
            if (toolOpt.isEmpty()) {
                diagnostics.add(PlanValidationDiagnostic.error(stepId, toolId, "UNKNOWN_TOOL",
                    "Unknown or unregistered tool '" + toolId + "'"));
                continue;
            }

            AgentTool tool = toolOpt.get();
            Map<String, Object> params = step.getParameters();

            // Tool confirmation safety check
            if (tool.requiresConfirmation()) {
                if (context != null && !context.isConfirmationApproved()) {
                    diagnostics.add(PlanValidationDiagnostic.error(stepId, toolId, "CONFIRMATION_REQUIRED",
                        "Step '" + stepId + "' uses destructive tool '" + toolId + "' which requires explicit confirmation"));
                }
            }

            // Check declared explicit dependencies
            if (step.getDependencies() != null) {
                for (String depId : step.getDependencies()) {
                    if (depId.equals(stepId)) {
                        diagnostics.add(PlanValidationDiagnostic.error(stepId, toolId, "CYCLIC_DEPENDENCY",
                            "Step '" + stepId + "' cannot depend on itself"));
                    } else if (!declaredStepIds.contains(depId)) {
                        diagnostics.add(PlanValidationDiagnostic.error(stepId, toolId, "UNRESOLVED_DEPENDENCY",
                            "Step '" + stepId + "' has unresolved or forward dependency on '" + depId + "'"));
                    }
                }
            }

            // Validate references: any $ref must refer to a prior declared stepId
            if (params != null) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        String strVal = (String) entry.getValue();
                        if (strVal.startsWith("$ref:")) {
                            Matcher m = REF_PATTERN.matcher(strVal);
                            if (!m.matches()) {
                                diagnostics.add(PlanValidationDiagnostic.error(stepId, toolId, "MALFORMED_REFERENCE",
                                    "Malformed reference '" + strVal + "' in parameter '" + entry.getKey() + "'"));
                            } else {
                                String targetStep = m.group(1);
                                if (!declaredStepIds.contains(targetStep) || targetStep.equals(stepId)) {
                                    diagnostics.add(PlanValidationDiagnostic.error(stepId, toolId, "UNRESOLVED_REFERENCE",
                                        "Forward or unresolved reference to step '" + targetStep + "' in parameter '" + entry.getKey() + "'"));
                                }
                            }
                        }
                    }
                }
            }

            // Run tool-specific validation (ignoring unresolved $ref values during pre-validation)
            if (context != null) {
                Set<String> refKeys = new HashSet<>();
                Map<String, Object> sanitizedParams = filterOutReferences(params, refKeys);
                List<String> toolErrors = tool.validateParameters(sanitizedParams, context);
                if (toolErrors != null) {
                    for (String err : toolErrors) {
                        // If error is about entity not found for a parameter that was a $ref, ignore during pre-validation
                        boolean isRefEntityError = refKeys.contains("entityId") && err.contains("not found in active scene");
                        if (!isRefEntityError) {
                            diagnostics.add(PlanValidationDiagnostic.error(stepId, toolId, "PARAMETER_INVALID", err));
                        }
                    }
                }
            }
        }

        return diagnostics;
    }

    private Map<String, Object> filterOutReferences(Map<String, Object> raw, Set<String> refKeysOut) {
        if (raw == null) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof String && ((String) entry.getValue()).startsWith("$ref:")) {
                if (refKeysOut != null) refKeysOut.add(entry.getKey());
                result.put(entry.getKey(), UUID.randomUUID().toString());
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
