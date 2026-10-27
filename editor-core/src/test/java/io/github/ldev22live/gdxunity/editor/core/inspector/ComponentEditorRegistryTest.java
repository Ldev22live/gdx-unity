package io.github.ldev22live.gdxunity.editor.core.inspector;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ComponentEditorRegistryTest {

    private ComponentEditorRegistry<StringBuilder> registry;

    static class DummyComponent extends BaseComponent {
        private String value = "test";
        public String getValue() { return value; }
        public void setValue(String v) { this.value = v; }
    }

    static class DummyComponentProvider implements ComponentEditorProvider<DummyComponent, StringBuilder> {
        @Override
        public Class<DummyComponent> getComponentClass() {
            return DummyComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Dummy Component";
        }

        @Override
        public void buildEditor(StringBuilder container, Entity entity, DummyComponent component, EditorState editorState, Object skin) {
            container.append("Built editor for ").append(component.getValue());
        }
    }

    @BeforeEach
    void setUp() {
        registry = new ComponentEditorRegistry<>();
    }

    @Test
    void testRegisterAndRetrieveProvider() {
        DummyComponentProvider provider = new DummyComponentProvider();
        registry.register(provider);

        assertTrue(registry.hasProvider(DummyComponent.class));
        Optional<ComponentEditorProvider<DummyComponent, StringBuilder>> opt = registry.getProvider(DummyComponent.class);
        assertTrue(opt.isPresent());
        assertEquals("Dummy Component", opt.get().getDisplayName());

        StringBuilder output = new StringBuilder();
        opt.get().buildEditor(output, null, new DummyComponent(), null, null);
        assertEquals("Built editor for test", output.toString());
    }

    @Test
    void testUnregisterProvider() {
        DummyComponentProvider provider = new DummyComponentProvider();
        registry.register(provider);
        assertTrue(registry.hasProvider(DummyComponent.class));

        registry.unregister(DummyComponent.class);
        assertFalse(registry.hasProvider(DummyComponent.class));
        assertTrue(registry.getProvider(DummyComponent.class).isEmpty());
    }

    @Test
    void testClearRegistry() {
        registry.register(new DummyComponentProvider());
        assertEquals(1, registry.getAllProviders().size());

        registry.clear();
        assertEquals(0, registry.getAllProviders().size());
    }
}
