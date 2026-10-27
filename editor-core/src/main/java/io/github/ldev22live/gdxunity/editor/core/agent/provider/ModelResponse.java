package io.github.ldev22live.gdxunity.editor.core.agent.provider;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Standard typed response returned by a ModelProvider.
 */
public class ModelResponse {
    private final boolean success;
    private final String content;
    private final String rawResponse;
    private final String providerId;
    private final String modelName;
    private final Duration latency;
    private final Map<String, Object> usageMetadata;
    private final String errorMessage;

    public ModelResponse(boolean success, String content, String rawResponse, String providerId, String modelName, Duration latency, Map<String, Object> usageMetadata, String errorMessage) {
        this.success = success;
        this.content = (content != null) ? content : "";
        this.rawResponse = (rawResponse != null) ? rawResponse : "";
        this.providerId = Objects.requireNonNull(providerId, "providerId cannot be null");
        this.modelName = Objects.requireNonNull(modelName, "modelName cannot be null");
        this.latency = (latency != null) ? latency : Duration.ZERO;
        this.usageMetadata = (usageMetadata != null) ? Map.copyOf(usageMetadata) : Collections.emptyMap();
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getModelName() {
        return modelName;
    }

    public Duration getLatency() {
        return latency;
    }

    public Map<String, Object> getUsageMetadata() {
        return usageMetadata;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static ModelResponse success(String content, String rawResponse, String providerId, String modelName, Duration latency) {
        return new ModelResponse(true, content, rawResponse, providerId, modelName, latency, Collections.emptyMap(), null);
    }

    public static ModelResponse failure(String errorMessage, String providerId, String modelName, Duration latency) {
        return new ModelResponse(false, "", "", providerId, modelName, latency, Collections.emptyMap(), errorMessage);
    }
}
