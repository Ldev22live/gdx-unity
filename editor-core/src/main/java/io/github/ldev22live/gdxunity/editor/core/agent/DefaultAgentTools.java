package io.github.ldev22live.gdxunity.editor.core.agent;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.agent.tools.*;

/**
 * Factory helper to construct an AgentToolRegistry pre-populated with standard built-in tools.
 */
public final class DefaultAgentTools {
    private DefaultAgentTools() {}

    public static AgentToolRegistry createDefaultRegistry() {
        AgentToolRegistry registry = new AgentToolRegistry();
        registry.registerTool(new SceneInspectTool());
        registry.registerTool(new EntityCreateTool());
        registry.registerTool(new EntityRenameTool());
        registry.registerTool(new EntityDeleteTool());
        registry.registerTool(new TransformSetTool());
        registry.registerTool(new ComponentAddTool());
        return registry;
    }
}
