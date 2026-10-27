package io.github.ldev22live.gdxunity.editor.core.agent;

import java.util.*;

/**
 * Registry managing available AgentTools.
 * Ensures unique IDs, explicit registration, and deterministic iteration order.
 */
public class AgentToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public synchronized void registerTool(AgentTool tool) {
        Objects.requireNonNull(tool, "tool cannot be null");
        String id = Objects.requireNonNull(tool.getId(), "tool id cannot be null");
        if (tools.containsKey(id)) {
            throw new IllegalArgumentException("Tool with id '" + id + "' is already registered");
        }
        tools.put(id, tool);
    }

    public synchronized void unregisterTool(String id) {
        if (id != null) {
            tools.remove(id);
        }
    }

    public synchronized Optional<AgentTool> getTool(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(tools.get(id));
    }

    public synchronized boolean hasTool(String id) {
        return id != null && tools.containsKey(id);
    }

    public synchronized Collection<AgentTool> getAllTools() {
        return Collections.unmodifiableCollection(new ArrayList<>(tools.values()));
    }

    public synchronized List<AgentTool> getReadOnlyTools() {
        List<AgentTool> list = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            if (tool.isReadOnly()) {
                list.add(tool);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public synchronized List<AgentTool> getMutatingTools() {
        List<AgentTool> list = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            if (!tool.isReadOnly()) {
                list.add(tool);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public synchronized int getToolCount() {
        return tools.size();
    }
}
