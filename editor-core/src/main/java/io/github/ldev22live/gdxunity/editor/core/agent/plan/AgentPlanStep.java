package io.github.ldev22live.gdxunity.editor.core.agent.plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An individual step inside an AgentPlan.
 */
public class AgentPlanStep {
    private String stepId;
    private String tool;
    private java.util.LinkedHashMap<String, Object> parameters = new java.util.LinkedHashMap<>();
    private java.util.List<String> dependencies = new java.util.ArrayList<>();

    public AgentPlanStep() {}

    public AgentPlanStep(String stepId, String tool, Map<String, Object> parameters) {
        this(stepId, tool, parameters, Collections.emptyList());
    }

    public AgentPlanStep(String stepId, String tool, Map<String, Object> parameters, List<String> dependencies) {
        this.stepId = Objects.requireNonNull(stepId, "stepId cannot be null");
        this.tool = Objects.requireNonNull(tool, "tool cannot be null");
        if (parameters != null) {
            this.parameters.putAll(parameters);
        }
        if (dependencies != null) {
            this.dependencies.addAll(dependencies);
        }
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = (dependencies != null) ? new java.util.ArrayList<>(dependencies) : new java.util.ArrayList<>();
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = (parameters != null) ? new LinkedHashMap<>(parameters) : new LinkedHashMap<>();
    }
}
