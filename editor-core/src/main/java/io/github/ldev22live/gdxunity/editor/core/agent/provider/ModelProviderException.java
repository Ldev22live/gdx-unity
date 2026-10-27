package io.github.ldev22live.gdxunity.editor.core.agent.provider;

/**
 * Exception raised during provider execution or connectivity errors.
 */
public class ModelProviderException extends Exception {
    private final String providerId;
    private final boolean retryable;

    public ModelProviderException(String providerId, String message) {
        this(providerId, message, false, null);
    }

    public ModelProviderException(String providerId, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
        this.retryable = retryable;
    }

    public String getProviderId() {
        return providerId;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
