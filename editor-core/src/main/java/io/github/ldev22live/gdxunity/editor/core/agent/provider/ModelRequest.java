package io.github.ldev22live.gdxunity.editor.core.agent.provider;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Standard typed request passed to a ModelProvider.
 */
public class ModelRequest {
    private final String systemPrompt;
    private final List<Message> history;
    private final String userPrompt;
    private final Map<String, Object> sceneContext;
    private final boolean jsonExpected;
    private final Duration timeout;

    public ModelRequest(String systemPrompt, List<Message> history, String userPrompt, Map<String, Object> sceneContext, boolean jsonExpected, Duration timeout) {
        this.systemPrompt = (systemPrompt != null) ? systemPrompt : "";
        this.history = (history != null) ? List.copyOf(history) : Collections.emptyList();
        this.userPrompt = Objects.requireNonNull(userPrompt, "userPrompt cannot be null");
        this.sceneContext = (sceneContext != null) ? Map.copyOf(sceneContext) : Collections.emptyMap();
        this.jsonExpected = jsonExpected;
        this.timeout = timeout;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<Message> getHistory() {
        return history;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public Map<String, Object> getSceneContext() {
        return sceneContext;
    }

    public boolean isJsonExpected() {
        return jsonExpected;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public record Message(Role role, String content) {
        public enum Role {
            SYSTEM, USER, ASSISTANT
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String systemPrompt = "";
        private List<Message> history = Collections.emptyList();
        private String userPrompt = "";
        private Map<String, Object> sceneContext = Collections.emptyMap();
        private boolean jsonExpected = true;
        private Duration timeout = ModelConfiguration.DEFAULT_TIMEOUT;

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder history(List<Message> history) {
            this.history = history;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public Builder sceneContext(Map<String, Object> sceneContext) {
            this.sceneContext = sceneContext;
            return this;
        }

        public Builder jsonExpected(boolean jsonExpected) {
            this.jsonExpected = jsonExpected;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public ModelRequest build() {
            return new ModelRequest(systemPrompt, history, userPrompt, sceneContext, jsonExpected, timeout);
        }
    }
}
