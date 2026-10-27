package io.github.ldev22live.gdxunity.engine.core.resource;

import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manages explicit ownership of {@link Disposable} resources.
 * Ensures:
 * - Resources are disposed in reverse registration order (LIFO).
 * - Resources are registered and disposed at most once.
 * - Double-disposal protection (idempotent dispose).
 * - Exceptions thrown by individual disposables do not abort cleanup of remaining resources.
 * - Registrations after disposal are safely rejected or disposed immediately.
 */
public final class ResourceManager implements Disposable {
    private final Deque<Disposable> resources = new ArrayDeque<>();
    private final Set<Disposable> registered = new HashSet<>();
    private boolean disposed = false;

    /**
     * Registers a resource to be owned and disposed by this manager.
     * Duplicate registrations are ignored to avoid duplicate disposal.
     *
     * @param resource the resource to register, cannot be null
     * @param <T>      the disposable type
     * @return the registered resource for fluent assignment
     * @throws IllegalStateException if the manager is already disposed
     * @throws NullPointerException  if resource is null
     */
    public synchronized <T extends Disposable> T register(T resource) {
        Objects.requireNonNull(resource, "resource cannot be null");
        if (disposed) {
            throw new IllegalStateException("Cannot register resource: ResourceManager has already been disposed");
        }
        if (registered.add(resource)) {
            resources.push(resource);
        }
        return resource;
    }

    /**
     * Unregisters a resource without disposing it, releasing ownership.
     *
     * @param resource resource to unregister
     * @return true if resource was found and unregistered, false otherwise
     */
    public synchronized boolean unregister(Disposable resource) {
        if (resource == null || disposed) {
            return false;
        }
        if (registered.remove(resource)) {
            return resources.remove(resource);
        }
        return false;
    }

    /**
     * Disposes all registered resources in reverse registration order (LIFO).
     * Any exceptions during individual resource disposal are collected and re-thrown
     * as a {@link ResourceDisposalException} containing all suppressed failures.
     * This method is idempotent.
     */
    @Override
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        List<Throwable> failures = new ArrayList<>();

        while (!resources.isEmpty()) {
            Disposable resource = resources.pop();
            try {
                resource.dispose();
            } catch (Throwable t) {
                failures.add(t);
            }
        }
        registered.clear();

        if (!failures.isEmpty()) {
            ResourceDisposalException rootEx = new ResourceDisposalException(
                "Encountered " + failures.size() + " failure(s) during resource cleanup"
            );
            for (Throwable t : failures) {
                rootEx.addSuppressed(t);
            }
            throw rootEx;
        }
    }

    /**
     * Returns true if this manager has been disposed.
     */
    public synchronized boolean isDisposed() {
        return disposed;
    }

    /**
     * Returns the count of currently registered resources.
     */
    public synchronized int getRegisteredCount() {
        return resources.size();
    }
}
