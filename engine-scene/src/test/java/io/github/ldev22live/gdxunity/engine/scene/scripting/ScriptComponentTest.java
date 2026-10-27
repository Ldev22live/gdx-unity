package io.github.ldev22live.gdxunity.engine.scene.scripting;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptComponentTest {

    static class TrackingScript extends ScriptComponent {
        final List<String> events = new ArrayList<>();
        float totalDelta = 0f;

        @Override
        public void onCreate() {
            events.add("onCreate");
        }

        @Override
        public void onStart() {
            super.onStart();
            events.add("onStart");
        }

        @Override
        public void onUpdate(float deltaTime) {
            events.add("onUpdate");
            totalDelta += deltaTime;
        }

        @Override
        public void onEnable() {
            super.onEnable();
            events.add("onEnable");
        }

        @Override
        public void onDisable() {
            super.onDisable();
            events.add("onDisable");
        }

        @Override
        public void onDestroy() {
            events.add("onDestroy");
        }
    }

    @Test
    void testScriptLifecycleInEntity() {
        Scene scene = new Scene("TestScene");
        Entity entity = scene.createEntity("Player");

        EntityScriptBindingComponent binding = new EntityScriptBindingComponent("scripts/TrackingScript.java", "TrackingScript", ScriptLanguage.JAVA);
        TrackingScript script = new TrackingScript();

        entity.addComponent(binding);
        binding.bindInstance(script);

        // onCreate and onEnable should have been called upon binding to active entity
        assertTrue(script.events.contains("onCreate"));
        assertTrue(script.events.contains("onEnable"));
        assertEquals(scene, script.getScene());
        assertEquals(entity.getTransform(), script.getTransform());

        // Update loop
        scene.update(0.016f);
        assertTrue(script.events.contains("onStart"));
        assertTrue(script.events.contains("onUpdate"));
        assertEquals(0.016f, script.totalDelta, 0.0001f);

        // Disable script
        binding.setEnabled(false);
        assertTrue(script.events.contains("onDisable"));

        // Update while disabled does not invoke onUpdate
        script.events.clear();
        scene.update(0.016f);
        assertFalse(script.events.contains("onUpdate"));

        // Re-enable script
        binding.setEnabled(true);
        assertTrue(script.events.contains("onEnable"));

        // Entity removal detaches and triggers onDestroy
        scene.removeEntity(entity);
        assertTrue(script.events.contains("onDestroy"));
    }

    @Test
    void testIndependentInstancesPerEntity() {
        Scene scene = new Scene("MultiEntityScene");
        Entity e1 = scene.createEntity("E1");
        Entity e2 = scene.createEntity("E2");

        EntityScriptBindingComponent b1 = new EntityScriptBindingComponent("scripts/TrackingScript.java", "TrackingScript", ScriptLanguage.JAVA);
        TrackingScript s1 = new TrackingScript();
        e1.addComponent(b1);
        b1.bindInstance(s1);

        EntityScriptBindingComponent b2 = new EntityScriptBindingComponent("scripts/TrackingScript.java", "TrackingScript", ScriptLanguage.JAVA);
        TrackingScript s2 = new TrackingScript();
        e2.addComponent(b2);
        b2.bindInstance(s2);

        assertNotSame(s1, s2);
        assertEquals(e1, s1.getEntity());
        assertEquals(e2, s2.getEntity());

        scene.update(0.02f);
        assertEquals(0.02f, s1.totalDelta, 0.0001f);
        assertEquals(0.02f, s2.totalDelta, 0.0001f);
    }
}
