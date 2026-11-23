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
        // Genre and Play (roadmap M3b.7, TD-088)
        registry.registerTool(new PlayProfileSetTool());
        registry.registerTool(new PlayProfileCheckTool());
        registry.registerTool(new PlayStartTool());
        registry.registerTool(new PlayStopTool());
        // What 3D Modeling can do for an object (roadmap M3c.4)
        registry.registerTool(new ModelEditCheckTool());
        // Sky, weather and water presets (roadmap M3.9, TD-090)
        registry.registerTool(new EnvironmentPresetTool());
        registry.registerTool(new NavigationCheckTool());
        return registry;
    }
}
