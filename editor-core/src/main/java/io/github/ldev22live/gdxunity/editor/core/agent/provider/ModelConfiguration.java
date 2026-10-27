package io.github.ldev22live.gdxunity.editor.core.agent.provider;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Common configuration for connecting to a model provider.
 */
public class ModelConfiguration {
    public static final String DEFAULT_MOCK_MODEL = "mock-agent-v1";
    public static final String DEFAULT_OLLAMA_ENDPOINT = "http://localhost:11434";
    public static final String DEFAULT_OLLAMA_MODEL = "llama3";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String providerId;
    private final String modelName;
    private final String endpoint;
    private final String apiKey;
    private final Duration timeout;
    private final Map<String, Object> extraOptions;

    public ModelConfiguration(String providerId, String modelName) {
        this(providerId, modelName, null, null, DEFAULT_TIMEOUT, Collections.emptyMap());
    }

    public ModelConfiguration(String providerId, String modelName, String endpoint, String apiKey, Duration timeout, Map<String, Object> extraOptions) {
        this.providerId = Objects.requireNonNull(providerId, "providerId cannot be null");
        this.modelName = Objects.requireNonNull(modelName, "modelName cannot be null");
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.timeout = (timeout != null) ? timeout : DEFAULT_TIMEOUT;
        this.extraOptions = (extraOptions != null) ? Collections.unmodifiableMap(extraOptions) : Collections.emptyMap();
    }

    public String getProviderId() {
        return providerId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Map<String, Object> getExtraOptions() {
        return extraOptions;
    }

    public static ModelConfiguration forMock() {
        return new ModelConfiguration("mock", DEFAULT_MOCK_MODEL);
    }

    public static ModelConfiguration forOllama(String modelName) {
        return new ModelConfiguration("ollama", (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_OLLAMA_MODEL, DEFAULT_OLLAMA_ENDPOINT, null, DEFAULT_TIMEOUT, Collections.emptyMap());
    }

    public static ModelConfiguration forOllama(String endpoint, String modelName, Duration timeout) {
        return new ModelConfiguration("ollama", (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_OLLAMA_MODEL, (endpoint != null && !endpoint.isBlank()) ? endpoint : DEFAULT_OLLAMA_ENDPOINT, null, timeout, Collections.emptyMap());
    }

    public static ModelConfiguration forRemote(String providerId, String modelName, String endpoint, String apiKey, Duration timeout) {
        return new ModelConfiguration(providerId, modelName, endpoint, apiKey, timeout, Collections.emptyMap());
    }
}
