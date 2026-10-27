package io.github.ldev22live.gdxunity.editor.core.agent.provider;

import java.util.concurrent.CompletableFuture;

/**
 * Common provider-neutral interface for language model providers (Mock, Ollama, Remote).
 */
public interface ModelProvider {
    /**
     * Unique stable identifier for the provider (e.g. "mock", "ollama", "openai", "anthropic", "gemini").
     */
    String getId();

    /**
     * Human-readable display name.
     */
    String getDisplayName();

    /**
     * Capabilities supported by this provider.
     */
    ModelProviderCapabilities getCapabilities();

    /**
     * Validates whether the given configuration contains all required parameters for this provider.
     *
     * @param config configuration to inspect
     * @throws ModelProviderException if configuration is missing required parameters or invalid
     */
    void validateConfiguration(ModelConfiguration config) throws ModelProviderException;

    /**
     * Tests connectivity to the provider endpoint.
     */
    CompletableFuture<Boolean> testConnection(ModelConfiguration config);

    /**
     * Asynchronously generates a response for the given request.
     * Must not block the calling thread.
     */
    CompletableFuture<ModelResponse> generateAsync(ModelRequest request, ModelConfiguration config);
}
