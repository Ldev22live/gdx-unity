package io.github.ldev22live.gdxunity.engine.core.resource;

/**
 * Exception thrown when one or more registered resources fail during disposal.
 */
public class ResourceDisposalException extends RuntimeException {
    public ResourceDisposalException(String message) {
        super(message);
    }

    public ResourceDisposalException(String message, Throwable cause) {
        super(message, cause);
    }
}
