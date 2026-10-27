package io.github.ldev22live.gdxunity.editor.core.agent.service;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.*;

/**
 * Builds standard system instructions and bounded scene context summaries for language models.
 */
public class AgentPromptContextBuilder {

    public static String buildSystemInstruction(AgentToolRegistry registry) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI assistant integrated into the GdxUnity game engine editor.\n");
        sb.append("Your role is to help users construct, organize, modify, and inspect scenes by generating a structured AgentPlan JSON.\n\n");
        sb.append("CRITICAL RULES:\n");
        sb.append("1. Always output ONLY a valid AgentPlan JSON object conforming to version 1.\n");
        sb.append("2. Format:\n");
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"summary\": \"Brief human-readable summary\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"stepId\": \"unique_step_id\",\n");
        sb.append("      \"tool\": \"tool_id\",\n");
        sb.append("      \"parameters\": { ... }\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("3. You may reference outputs from earlier steps using syntax: \"$ref:<priorStepId>.<outputKey>\". Never use forward references or cycles.\n");
        sb.append("4. Do not invent tools or parameters. Use only registered tools listed below.\n");
        sb.append("5. Do not include markdown code fence formatting or surrounding explanations when JSON format is requested.\n\n");
        sb.append("AVAILABLE TOOLS:\n");

        if (registry != null) {
            for (AgentTool tool : registry.getAllTools()) {
                sb.append("- ").append(tool.getId()).append(": ").append(tool.getDescription())
                  .append(" (readOnly: ").append(tool.isReadOnly()).append(")\n");
            }
        }

        sb.append("\nTOOL PARAMETERS (all others are rejected):\n");
        sb.append("- scene_inspect: {} ; outputs: sceneName, entityCount, entities\n");
        sb.append("- entity_create: { name: string<=256 } ; outputs: entityId, name\n");
        sb.append("- entity_rename: { entityId: uuid, name: string<=256 }\n");
        sb.append("- entity_delete: { entityId: uuid } (DESTRUCTIVE)\n");
        sb.append("- transform_set: { entityId: uuid, posX/posY/posZ: number in [-100000,100000], rotZ: degrees, scaleX/scaleY/scaleZ: number in [0.0001,10000] }\n");
        sb.append("- component_add: { entityId: uuid, componentType: \"ShapeRendererComponent\", properties: { width: number, height: number, color: \"#RRGGBB\" } }\n");
        sb.append("\nYour output is untrusted: it will be parsed and validated by the editor and shown to the user for approval.\n");
        sb.append("Never claim that a scene change has happened; you only propose a plan.\n");

        return sb.toString();
    }

    public static Map<String, Object> buildSceneContext(Scene scene, int maxEntities) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (scene == null) {
            context.put("activeScene", "None");
            context.put("entityCount", 0);
            return context;
        }

        context.put("sceneName", scene.getName());
        context.put("entityCount", scene.getEntityCount());

        List<Map<String, Object>> entitiesList = new ArrayList<>();
        int count = 0;
        for (Entity e : scene.getAllEntities()) {
            if (count++ >= maxEntities) break;
            Map<String, Object> entInfo = new LinkedHashMap<>();
            entInfo.put("id", e.getId().toString());
            entInfo.put("name", e.getName());
            entInfo.put("active", e.isActiveSelf());
            entInfo.put("posX", e.getTransform().getLocalPosition().x);
            entInfo.put("posY", e.getTransform().getLocalPosition().y);
            entitiesList.add(entInfo);
        }
        context.put("entities", entitiesList);
        return context;
    }
}
