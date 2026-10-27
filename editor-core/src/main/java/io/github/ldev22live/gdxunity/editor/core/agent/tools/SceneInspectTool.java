package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.*;

/**
 * Read-only tool that inspects the active scene hierarchy, entities, transforms, and components.
 */
public class SceneInspectTool implements AgentTool {
    public static final String ID = "scene_inspect";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Inspects the active scene hierarchy, entities, transforms, and components.";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        // No required parameters; optional includeComponents flag
        return Collections.emptyList();
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        return null; // Read-only
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) {
        Scene scene = context.getActiveScene();
        if (scene == null) {
            return AgentToolResult.failure("No active scene available to inspect");
        }

        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("sceneName", scene.getName());
        outputs.put("entityCount", scene.getEntityCount());

        List<Map<String, Object>> entitySummaries = new ArrayList<>();
        for (Entity root : scene.getRootEntities()) {
            buildEntityTree(root, entitySummaries);
        }

        outputs.put("entities", entitySummaries);
        return AgentToolResult.success("Scene inspected successfully (" + scene.getEntityCount() + " entities)", outputs);
    }

    private void buildEntityTree(Entity entity, List<Map<String, Object>> list) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId().toString());
        map.put("name", entity.getName());
        map.put("active", entity.isActiveSelf());

        Transform t = entity.getTransform();
        map.put("posX", t.getLocalPosition().x);
        map.put("posY", t.getLocalPosition().y);
        map.put("posZ", t.getLocalPosition().z);
        map.put("scaleX", t.getLocalScale().x);
        map.put("scaleY", t.getLocalScale().y);

        List<String> componentTypes = new ArrayList<>();
        for (Component c : entity.getAllComponents()) {
            componentTypes.add(c.getClass().getSimpleName());
        }
        map.put("components", componentTypes);

        list.add(map);

        for (Transform child : t.getChildren()) {
            buildEntityTree(child.getEntity(), list);
        }
    }
}
