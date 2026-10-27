package io.github.ldev22live.gdxunity.editor.core.agent.provider;

import java.util.*;

/**
 * Registry holding available ModelProvider implementations.
 */
public class ModelProviderRegistry {
    private final Map<String, ModelProvider> providers = new LinkedHashMap<>();

    public ModelProviderRegistry() {}

    public synchronized void registerProvider(ModelProvider provider) {
        Objects.requireNonNull(provider, "provider cannot be null");
        String id = provider.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Provider ID cannot be null or blank");
        }
        if (providers.containsKey(id)) {
            throw new IllegalArgumentException("Provider with ID '" + id + "' is already registered");
        }
        providers.put(id, provider);
    }

    public synchronized Optional<ModelProvider> getProvider(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(providers.get(id));
    }

    public synchronized boolean hasProvider(String id) {
        return id != null && providers.containsKey(id);
    }

    public synchronized List<ModelProvider> getRegisteredProviders() {
        return List.copyOf(providers.values());
    }

    public synchronized int getProviderCount() {
        return providers.size();
    }
}
