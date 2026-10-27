package io.github.ldev22live.gdxunity.editor.core.agent.executor;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.*;
import io.github.ldev22live.gdxunity.editor.core.command.CompoundCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, provider-independent plan executor.
 * Validates the entire plan upfront, resolves variable references ($ref:step.output),
 * aggregates mutating commands into a single CompoundCommand in CommandHistory,
 * and rolls back cleanly on any mid-execution failure.
 */
public class AgentPlanExecutor {
    private static final Pattern REF_PATTERN = Pattern.compile("^\\$ref:([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+)$");

    private final AgentToolRegistry registry;
    private final AgentPlanValidator validator;

    public AgentPlanExecutor(AgentToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.validator = new AgentPlanValidator(registry);
    }

    public AgentPlanExecutionResult executePlan(AgentPlan plan, AgentToolContext context) {
        Objects.requireNonNull(plan, "plan cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        // 1. Upfront Plan Validation
        List<PlanValidationDiagnostic> diagnostics = validator.validate(plan, context);
        boolean hasErrors = diagnostics.stream().anyMatch(d -> d.getSeverity() == PlanValidationDiagnostic.Severity.ERROR);
        if (hasErrors) {
            return AgentPlanExecutionResult.validationFailed("Plan validation failed with " + diagnostics.size() + " diagnostic(s)", diagnostics);
        }

        // 2. Prepare execution context and compound command accumulator
        CompoundCommand compoundCommand = new CompoundCommand("AI Plan: " + plan.getSummary());
        Map<String, Map<String, Object>> stepOutputs = new LinkedHashMap<>();
        List<EditorCommand> executedCommands = new ArrayList<>();

        for (int i = 0; i < plan.getSteps().size(); i++) {
            AgentPlanStep step = plan.getSteps().get(i);
            String stepId = step.getStepId();
            String toolId = step.getTool();

            AgentTool tool = registry.getTool(toolId).orElseThrow();

            // Resolve references in parameters against prior stepOutputs
            Map<String, Object> resolvedParams;
            try {
                resolvedParams = resolveReferences(step.getParameters(), stepOutputs);
            } catch (Exception e) {
                rollback(executedCommands);
                return AgentPlanExecutionResult.rolledBack(stepId, "Failed to resolve references in step '" + stepId + "': " + e.getMessage(), e);
            }

            // Execute tool
            try {
                if (tool.isReadOnly()) {
                    AgentToolResult result = tool.execute(resolvedParams, context);
                    if (!result.isSuccess()) {
                        rollback(executedCommands);
                        return AgentPlanExecutionResult.rolledBack(stepId, "Read-only tool failed at step '" + stepId + "': " + result.getMessage(), result.getError());
                    }
                    stepOutputs.put(stepId, result.getOutputs());
                } else {
                    EditorCommand command = tool.createCommand(resolvedParams, context);
                    if (command != null) {
                        boolean executed = command.execute();
                        if (!executed) {
                            rollback(executedCommands);
                            return AgentPlanExecutionResult.rolledBack(stepId, "Command returned false at step '" + stepId + "'", null);
                        }
                        executedCommands.add(command);
                        compoundCommand.addCommand(command);

                        // If tool has output generation logic, extract outputs
                        if (command instanceof io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand) {
                            Entity created = ((io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand) command).getCreatedEntity();
                            if (created != null) {
                                Map<String, Object> out = new LinkedHashMap<>();
                                out.put("entityId", created.getId().toString());
                                out.put("name", created.getName());
                                stepOutputs.put(stepId, out);
                            }
                        } else {
                            Map<String, Object> out = new LinkedHashMap<>();
                            if (resolvedParams.containsKey("entityId")) {
                                out.put("entityId", resolvedParams.get("entityId").toString());
                            }
                            if (resolvedParams.containsKey("name")) {
                                out.put("name", resolvedParams.get("name").toString());
                            }
                            stepOutputs.put(stepId, out);
                        }
                    }
                }
            } catch (Exception e) {
                boolean rollbackSucceeded = rollback(executedCommands);
                if (rollbackSucceeded) {
                    return AgentPlanExecutionResult.rolledBack(stepId, "Exception at step '" + stepId + "': " + e.getMessage(), e);
                } else {
                    return AgentPlanExecutionResult.rollbackError(stepId, "Exception at step '" + stepId + "' and rollback also failed: " + e.getMessage(), e);
                }
            }
        }

        // 3. If any mutating commands were executed, record the CompoundCommand into CommandHistory
        if (compoundCommand.getCommandCount() > 0) {
            // Note: Since commands were already executed in the loop, we push the compoundCommand
            // onto the CommandHistory undo stack via executeCommand or manual stack integration
            context.getEditorState().getCommandHistory().execute(new PreExecutedCompoundCommand(compoundCommand));
        }

        Map<String, Object> flatOutputs = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : stepOutputs.entrySet()) {
            flatOutputs.put(entry.getKey(), entry.getValue());
        }

        return AgentPlanExecutionResult.success("Successfully executed plan with " + plan.getSteps().size() + " step(s)", flatOutputs);
    }

    private Map<String, Object> resolveReferences(Map<String, Object> rawParams, Map<String, Map<String, Object>> stepOutputs) throws Exception {
        if (rawParams == null) return Collections.emptyMap();
        Map<String, Object> resolved = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String) {
                String strVal = (String) val;
                if (strVal.startsWith("$ref:")) {
                    Matcher m = REF_PATTERN.matcher(strVal);
                    if (m.matches()) {
                        String targetStep = m.group(1);
                        String targetKey = m.group(2);

                        Map<String, Object> targetOutputs = stepOutputs.get(targetStep);
                        if (targetOutputs == null || !targetOutputs.containsKey(targetKey)) {
                            throw new IllegalArgumentException("Unresolved reference '" + strVal + "': step '" + targetStep + "' has no output '" + targetKey + "'");
                        }
                        resolved.put(entry.getKey(), targetOutputs.get(targetKey));
                        continue;
                    }
                }
            }
            resolved.put(entry.getKey(), val);
        }

        return resolved;
    }

    private boolean rollback(List<EditorCommand> executedCommands) {
        boolean allSuccess = true;
        for (int i = executedCommands.size() - 1; i >= 0; i--) {
            try {
                executedCommands.get(i).undo();
            } catch (Exception e) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    /**
     * Helper wrapper for a CompoundCommand that was already executed step-by-step during plan execution.
     */
    private static class PreExecutedCompoundCommand implements EditorCommand {
        private final CompoundCommand delegate;
        private boolean isFirstExecution = true;

        PreExecutedCompoundCommand(CompoundCommand delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean execute() throws Exception {
            if (isFirstExecution) {
                isFirstExecution = false;
                return true; // Already executed during executor run
            }
            return delegate.execute(); // Redo execution
        }

        @Override
        public boolean undo() throws Exception {
            return delegate.undo();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }
    }
}
