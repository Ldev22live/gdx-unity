package io.github.ldev22live.gdxunity.editor.desktop.ui;

import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InspectorPanelNamingTest {

    @Test
    void shouldDeriveReadableSectionTitlesFromComponentClasses() {
        assertEquals("Shape Renderer", InspectorPanel.displayNameOf(ShapeRendererComponent.class));
        assertEquals("Rigid Body2D", InspectorPanel.displayNameOf(RigidBody2DComponent.class));
    }
}
