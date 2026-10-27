package io.github.ldev22live.gdxunity.editor.core.agent.plan;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses and serializes versioned AgentPlan JSON models using LibGDX Json infrastructure.
 */
public class AgentPlanParser {
    public static final int MAX_JSON_LENGTH = 1_000_000; // 1MB safety limit
    private final Json json;

    public AgentPlanParser() {
        this.json = new Json();
        this.json.setOutputType(JsonWriter.OutputType.json);
        this.json.setIgnoreUnknownFields(true);
    }

    public AgentPlan parse(String jsonString) throws AgentPlanParseException {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new AgentPlanParseException("Plan JSON is null or empty");
        }
        if (jsonString.length() > MAX_JSON_LENGTH) {
            throw new AgentPlanParseException("Plan JSON exceeds maximum permitted length of " + MAX_JSON_LENGTH + " characters");
        }
        try {
            com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(jsonString);
            if (root == null || !root.isObject()) {
                throw new AgentPlanParseException("Root JSON element must be an object");
            }

            int version = root.getInt("version", -1);
            if (version != AgentPlan.CURRENT_VERSION) {
                throw new AgentPlanParseException("Unsupported plan version: " + version + ". Current supported version is: " + AgentPlan.CURRENT_VERSION);
            }

            String summary = root.getString("summary", "");
            AgentPlan plan = new AgentPlan(version, summary);

            com.badlogic.gdx.utils.JsonValue stepsVal = root.get("steps");
            if (stepsVal != null && stepsVal.isArray()) {
                for (com.badlogic.gdx.utils.JsonValue stepVal = stepsVal.child; stepVal != null; stepVal = stepVal.next) {
                    String stepId = stepVal.getString("stepId", null);
                    String tool = stepVal.getString("tool", null);
                    java.util.LinkedHashMap<String, Object> params = new java.util.LinkedHashMap<>();

                    com.badlogic.gdx.utils.JsonValue paramsVal = stepVal.get("parameters");
                    if (paramsVal != null && paramsVal.isObject()) {
                        for (com.badlogic.gdx.utils.JsonValue p = paramsVal.child; p != null; p = p.next) {
                            params.put(p.name, jsonValueToObject(p));
                        }
                    }
                    java.util.List<String> deps = new java.util.ArrayList<>();
                    com.badlogic.gdx.utils.JsonValue depsVal = stepVal.get("dependencies");
                    if (depsVal != null && depsVal.isArray()) {
                        for (com.badlogic.gdx.utils.JsonValue d = depsVal.child; d != null; d = d.next) {
                            if (d.asString() != null) {
                                deps.add(d.asString());
                            }
                        }
                    }
                    plan.addStep(new AgentPlanStep(stepId, tool, params, deps));
                }
            }

            if (plan.getSteps().size() > AgentPlan.MAX_STEPS) {
                throw new AgentPlanParseException("Plan contains " + plan.getSteps().size() + " steps, exceeding maximum limit of " + AgentPlan.MAX_STEPS);
            }

            return plan;
        } catch (AgentPlanParseException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentPlanParseException("Malformed plan JSON: " + e.getMessage(), e);
        }
    }

    private static Object jsonValueToObject(com.badlogic.gdx.utils.JsonValue val) {
        if (val == null || val.isNull()) return null;
        if (val.isBoolean()) return val.asBoolean();
        if (val.isDouble()) return val.asDouble();
        if (val.isLong()) return val.asLong();
        if (val.isString()) return val.asString();
        if (val.isObject()) {
            java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
            for (com.badlogic.gdx.utils.JsonValue child = val.child; child != null; child = child.next) {
                map.put(child.name, jsonValueToObject(child));
            }
            return map;
        }
        if (val.isArray()) {
            java.util.ArrayList<Object> list = new java.util.ArrayList<>();
            for (com.badlogic.gdx.utils.JsonValue child = val.child; child != null; child = child.next) {
                list.add(jsonValueToObject(child));
            }
            return list;
        }
        return val.asString();
    }

    public String serialize(AgentPlan plan) {
        if (plan == null) return "{}";
        return json.prettyPrint(plan);
    }

    public static class AgentPlanParseException extends Exception {
        public AgentPlanParseException(String message) {
            super(message);
        }
        public AgentPlanParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
