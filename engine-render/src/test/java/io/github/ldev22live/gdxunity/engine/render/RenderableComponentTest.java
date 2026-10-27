package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderableComponentTest {

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @Test
    void shouldDiscoverOnlyActiveRenderableComponents() {
        Scene scene = new Scene("TestScene");

        // 1. Active entity with active primitive
        Entity e1 = scene.createEntity("ActiveEntity");
        ShapeRendererComponent shape1 = new ShapeRendererComponent(100, 100, Color.RED);
        e1.addComponent(shape1);

        // 2. Disabled component on active entity
        ShapeRendererComponent shapeDisabled = new ShapeRendererComponent(50, 50, Color.BLUE);
        shapeDisabled.setEnabled(false);
        e1.addComponent(shapeDisabled);

        // 3. Active component on inactive entity
        Entity e2 = scene.createEntity("InactiveEntity");
        ShapeRendererComponent shape2 = new ShapeRendererComponent(80, 80, Color.GREEN);
        e2.addComponent(shape2);
        e2.setActive(false);

        // 4. Entity without renderable components
        Entity e3 = scene.createEntity("NonRenderableEntity");
        e3.addComponent(new BaseComponent() {});

        List<Renderable> primitives = new ArrayList<>();
        List<Renderable> sprites = new ArrayList<>();

        SceneRenderer2D.collectRenderables(scene.getAllEntities(), primitives, sprites);

        // Only shape1 should be collected
        assertEquals(1, primitives.size());
        assertEquals(shape1, primitives.get(0));
        assertEquals(0, sprites.size());
    }

    @Test
    void shouldSkipChildrenWhenParentEntityIsInactiveInHierarchy() {
        Scene scene = new Scene("HierarchyScene");
        Entity parent = scene.createEntity("Parent");
        Entity child = scene.createEntity("Child");
        child.getTransform().setParent(parent.getTransform());

        ShapeRendererComponent childShape = new ShapeRendererComponent(20, 20, Color.YELLOW);
        child.addComponent(childShape);

        List<Renderable> primitives = new ArrayList<>();
        List<Renderable> sprites = new ArrayList<>();

        // When parent is active, child is collected
        SceneRenderer2D.collectRenderables(scene.getAllEntities(), primitives, sprites);
        assertEquals(1, primitives.size());

        // Deactivate parent
        parent.setActive(false);
        primitives.clear();
        SceneRenderer2D.collectRenderables(scene.getAllEntities(), primitives, sprites);
        assertEquals(0, primitives.size(), "Child must be skipped when ancestor is inactive");
    }

    @Test
    void shouldPreserveEntityTransformsWithoutSideEffectsDuringRendering() {
        Entity entity = new Entity("Box");
        entity.getTransform().setLocalPosition(150f, 250f, 0f);
        ShapeRendererComponent shape = new ShapeRendererComponent(60f, 60f, Color.WHITE);
        entity.addComponent(shape);

        // World matrix before
        var matrixBefore = entity.getTransform().getWorldMatrix();

        // Check values
        assertEquals(150f, entity.getTransform().getWorldPosition().x);
        assertEquals(250f, entity.getTransform().getWorldPosition().y);

        // World matrix after remains unchanged
        var matrixAfter = entity.getTransform().getWorldMatrix();
        assertArrayEquals(matrixBefore.val, matrixAfter.val);
    }
}
