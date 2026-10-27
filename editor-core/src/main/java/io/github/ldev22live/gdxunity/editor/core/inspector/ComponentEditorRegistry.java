package io.github.ldev22live.gdxunity.editor.core.inspector;

import io.github.ldev22live.gdxunity.engine.scene.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry holding {@link ComponentEditorProvider} instances keyed by their target Component class.
 */
public class ComponentEditorRegistry<U> {
    private final Map<Class<? extends Component>, ComponentEditorProvider<?, U>> providers = new LinkedHashMap<>();

    public <C extends Component> void register(ComponentEditorProvider<C, U> provider) {
        Objects.requireNonNull(provider, "provider cannot be null");
        providers.put(provider.getComponentClass(), provider);
    }

    public <C extends Component> void unregister(Class<C> componentClass) {
        if (componentClass != null) {
            providers.remove(componentClass);
        }
    }

    @SuppressWarnings("unchecked")
    public <C extends Component> Optional<ComponentEditorProvider<C, U>> getProvider(Class<C> componentClass) {
        if (componentClass == null) return Optional.empty();
        ComponentEditorProvider<?, U> provider = providers.get(componentClass);
        return Optional.ofNullable((ComponentEditorProvider<C, U>) provider);
    }

    public boolean hasProvider(Class<? extends Component> componentClass) {
        return componentClass != null && providers.containsKey(componentClass);
    }

    public Map<Class<? extends Component>, ComponentEditorProvider<?, U>> getAllProviders() {
        return Collections.unmodifiableMap(providers);
    }

    public void clear() {
        providers.clear();
    }
}
