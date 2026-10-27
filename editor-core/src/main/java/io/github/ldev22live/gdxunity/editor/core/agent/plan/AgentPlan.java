package io.github.ldev22live.gdxunity.editor.core.agent.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Versioned structured plan model defining a sequence of agent tool operations.
 */
public class AgentPlan {
    public static final int CURRENT_VERSION = 1;
    public static final int MAX_STEPS = 100;

    private int version = CURRENT_VERSION;
    private String summary = "";
    private List<AgentPlanStep> steps = new ArrayList<>();

    public AgentPlan() {}

    public AgentPlan(String summary) {
        this.summary = summary;
    }

    public AgentPlan(int version, String summary) {
        this.version = version;
        this.summary = summary;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<AgentPlanStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentPlanStep> steps) {
        this.steps = (steps != null) ? new ArrayList<>(steps) : new ArrayList<>();
    }

    public void addStep(AgentPlanStep step) {
        if (step != null) {
            steps.add(step);
        }
    }
}
