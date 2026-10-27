package io.github.ldev22live.gdxunity.engine.scene.scripting;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;

/**
 * Entity component that binds a persistent script reference (source path, class name, language)
 * to an entity, and delegates runtime lifecycle updates to the instantiated {@link ScriptComponent}.
 */
public class EntityScriptBindingComponent extends BaseComponent {
    private String scriptPath;
    private String scriptClassName;
    private ScriptLanguage language;

    // Transient runtime instance (not serialized)
    private transient ScriptComponent scriptInstance;
    private transient boolean instanceInitialized = false;

    public EntityScriptBindingComponent() {
    }

    public EntityScriptBindingComponent(String scriptPath, String scriptClassName, ScriptLanguage language) {
        this.scriptPath = scriptPath;
        this.scriptClassName = scriptClassName;
        this.language = language != null ? language : ScriptLanguage.fromPath(scriptPath);
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
        if (this.language == null && scriptPath != null) {
            this.language = ScriptLanguage.fromPath(scriptPath);
        }
    }

    public String getScriptClassName() {
        return scriptClassName;
    }

    public void setScriptClassName(String scriptClassName) {
        this.scriptClassName = scriptClassName;
    }

    public ScriptLanguage getLanguage() {
        return language;
    }

    public void setLanguage(ScriptLanguage language) {
        this.language = language;
    }

    public ScriptComponent getScriptInstance() {
        return scriptInstance;
    }

    public void bindInstance(ScriptComponent instance) {
        this.scriptInstance = instance;
        this.instanceInitialized = false;
        if (instance != null && getEntity() != null) {
            instance.onAttach(getEntity());
            instance.onCreate();
            this.instanceInitialized = true;
            if (getEntity().isActiveInHierarchy() && isEnabled()) {
                instance.onEnable();
            }
        }
    }

    public void unbindInstance() {
        if (scriptInstance != null) {
            try {
                scriptInstance.onDetach();
            } finally {
                scriptInstance = null;
                instanceInitialized = false;
            }
        }
    }

    @Override
    public void onAttach(Entity entity) {
        super.onAttach(entity);
        if (scriptInstance != null && !instanceInitialized) {
            scriptInstance.onAttach(entity);
            scriptInstance.onCreate();
            instanceInitialized = true;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (scriptInstance != null) {
            scriptInstance.onStart();
        }
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (scriptInstance != null && isEnabled()) {
            scriptInstance.onUpdate(deltaTime);
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (scriptInstance != null) {
            scriptInstance.onEnable();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (scriptInstance != null) {
            scriptInstance.onDisable();
        }
    }

    @Override
    public void onDetach() {
        try {
            unbindInstance();
        } finally {
            super.onDetach();
        }
    }
}
