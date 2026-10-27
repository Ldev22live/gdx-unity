package io.github.ldev22live.gdxunity.editor.core.agent.provider;

/**
 * Declares operational capabilities supported by a model provider.
 */
public record ModelProviderCapabilities(
    boolean supportsSystemPrompt,
    boolean supportsStreaming,
    boolean supportsJsonMode,
    boolean supportsLocalOffline,
    boolean requiresApiKey
) {
    public static ModelProviderCapabilities mock() {
        return new ModelProviderCapabilities(true, false, true, true, false);
    }

    public static ModelProviderCapabilities ollama() {
        return new ModelProviderCapabilities(true, true, true, true, false);
    }

    public static ModelProviderCapabilities remoteStandard() {
        return new ModelProviderCapabilities(true, true, true, false, true);
    }
}
