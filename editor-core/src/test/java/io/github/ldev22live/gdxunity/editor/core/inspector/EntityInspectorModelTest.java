package io.github.ldev22live.gdxunity.editor.core.inspector;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityInspectorModelTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldReadAndWriteEntityNameAndActiveState() {
        Entity entity = new Entity("Original");
        EntityInspectorModel inspector = new EntityInspectorModel(entity);

        assertEquals("Original", inspector.getName());
        assertTrue(inspector.isActive());

        inspector.setName("Renamed");
        inspector.setActive(false);

        assertEquals("Renamed", entity.getName());
        assertFalse(entity.isActiveSelf());
    }

    @Test
    void shouldUpdateLocalTransformAndAffectWorldMatrix() {
        Entity parent = new Entity("Parent");
        Entity child = new Entity("Child");
        child.getTransform().setParent(parent.getTransform());

        EntityInspectorModel parentInspector = new EntityInspectorModel(parent);
        EntityInspectorModel childInspector = new EntityInspectorModel(child);

        parentInspector.setLocalPosition(100f, 50f, 0f);
        childInspector.setLocalPosition(10f, 5f, 0f);
        childInspector.setLocalScale(2f, 2f, 1f);

        Vector3 childWorldPos = childInspector.getWorldPosition();
        assertEquals(110f, childWorldPos.x, 0.001f);
        assertEquals(55f, childWorldPos.y, 0.001f);
        assertEquals(2f, childInspector.getWorldScale().x, 0.001f);
    }

    @Test
    void shouldSafelyHandleNaNAndInfiniteInput() {
        Entity entity = new Entity("Test");
        EntityInspectorModel inspector = new EntityInspectorModel(entity);

        inspector.setLocalPosition(10f, 20f, 30f);

        // NaN and Infinite updates should be ignored
        inspector.setLocalPosition(Float.NaN, 50f, 60f);
        assertEquals(10f, inspector.getLocalPosition().x);

        inspector.setLocalScale(Float.POSITIVE_INFINITY, 1f, 1f);
        assertEquals(1f, inspector.getLocalScale().x);
    }
}
