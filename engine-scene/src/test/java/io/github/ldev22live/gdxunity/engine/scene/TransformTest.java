package io.github.ldev22live.gdxunity.engine.scene;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformTest {

    @Test
    void shouldInitializeWithDefaultValues() {
        Entity entity = new Entity("TestEntity");
        Transform t = entity.getTransform();

        assertEquals(new Vector3(0, 0, 0), t.getLocalPosition());
        assertEquals(new Quaternion(), t.getLocalRotation());
        assertEquals(new Vector3(1, 1, 1), t.getLocalScale());

        assertEquals(new Vector3(0, 0, 0), t.getWorldPosition());
        assertEquals(new Quaternion(), t.getWorldRotation());
        assertEquals(new Vector3(1, 1, 1), t.getWorldScale());
        assertNull(t.getParent());
        assertEquals(0, t.getChildCount());
    }

    @Test
    void shouldCalculateWorldPositionWhenParentIsTransformed() {
        Entity parentEntity = new Entity("Parent");
        Transform parent = parentEntity.getTransform();
        parent.setLocalPosition(10f, 20f, 0f);

        Entity childEntity = new Entity("Child");
        Transform child = childEntity.getTransform();
        child.setLocalPosition(5f, -2f, 0f);
        child.setParent(parent);

        assertEquals(parent, child.getParent());
        assertEquals(1, parent.getChildCount());
        assertEquals(child, parent.getChild(0));

        // World position should be parent pos + child pos = (15, 18, 0)
        Vector3 childWorldPos = child.getWorldPosition();
        assertEquals(15f, childWorldPos.x, 0.001f);
        assertEquals(18f, childWorldPos.y, 0.001f);
        assertEquals(0f, childWorldPos.z, 0.001f);
    }

    @Test
    void shouldComposeScaleThroughHierarchy() {
        Entity parentEntity = new Entity("Parent");
        Transform parent = parentEntity.getTransform();
        parent.setLocalScale(2f, 3f, 1f);

        Entity childEntity = new Entity("Child");
        Transform child = childEntity.getTransform();
        child.setLocalScale(4f, 2f, 1f);
        child.setParent(parent);

        Vector3 childWorldScale = child.getWorldScale();
        assertEquals(8f, childWorldScale.x, 0.001f);
        assertEquals(6f, childWorldScale.y, 0.001f);
        assertEquals(1f, childWorldScale.z, 0.001f);
    }

    @Test
    void shouldPropagateDirtyFlagToDescendantsWhenParentChanges() {
        Entity root = new Entity("Root");
        Entity child = new Entity("Child");
        Entity grandChild = new Entity("GrandChild");

        child.getTransform().setParent(root.getTransform());
        grandChild.getTransform().setParent(child.getTransform());

        // Force resolution
        grandChild.getTransform().getWorldPosition();
        assertFalse(root.getTransform().isDirty());
        assertFalse(child.getTransform().isDirty());
        assertFalse(grandChild.getTransform().isDirty());

        // Mutating root must dirty all descendants
        root.getTransform().setLocalPosition(100f, 0f, 0f);
        assertTrue(root.getTransform().isDirty());
        assertTrue(child.getTransform().isDirty());
        assertTrue(grandChild.getTransform().isDirty());

        // Re-evaluating grandChild resolves all ancestors
        Vector3 grandChildPos = grandChild.getTransform().getWorldPosition();
        assertEquals(100f, grandChildPos.x, 0.001f);
        assertFalse(root.getTransform().isDirty());
        assertFalse(child.getTransform().isDirty());
        assertFalse(grandChild.getTransform().isDirty());
    }

    @Test
    void shouldPreventSelfParenting() {
        Entity entity = new Entity("Loop");
        Transform t = entity.getTransform();

        assertThrows(IllegalArgumentException.class, () -> t.setParent(t));
    }

    @Test
    void shouldPreventCyclicAncestorParenting() {
        Entity a = new Entity("A");
        Entity b = new Entity("B");
        Entity c = new Entity("C");

        b.getTransform().setParent(a.getTransform());
        c.getTransform().setParent(b.getTransform());

        // Attempting to make A child of C creates a cycle: A -> B -> C -> A
        assertThrows(IllegalArgumentException.class, () -> a.getTransform().setParent(c.getTransform()));
    }

    @Test
    void shouldPreserveWorldTransformWhenReparentingWithPreserveFlag() {
        Entity parent = new Entity("Parent");
        parent.getTransform().setLocalPosition(100f, 50f, 0f);

        Entity child = new Entity("Child");
        child.getTransform().setLocalPosition(10f, 10f, 0f); // World pos (10, 10, 0)
        assertEquals(10f, child.getTransform().getWorldPosition().x);

        // Reparent child to parent, preserving world position
        child.getTransform().setParent(parent.getTransform(), true);

        // World position must still be (10, 10, 0)
        Vector3 childWPos = child.getTransform().getWorldPosition();
        assertEquals(10f, childWPos.x, 0.001f);
        assertEquals(10f, childWPos.y, 0.001f);

        // Local position should be updated to (-90, -40, 0)
        Vector3 childLPos = child.getTransform().getLocalPosition();
        assertEquals(-90f, childLPos.x, 0.001f);
        assertEquals(-40f, childLPos.y, 0.001f);
    }

    @Test
    void shouldUpdateBothSidesWhenChildIsDetached() {
        Entity parent = new Entity("Parent");
        Entity child = new Entity("Child");
        child.getTransform().setParent(parent.getTransform());

        assertEquals(1, parent.getTransform().getChildCount());
        assertEquals(parent.getTransform(), child.getTransform().getParent());

        child.getTransform().setParent(null);
        assertEquals(0, parent.getTransform().getChildCount());
        assertNull(child.getTransform().getParent());
    }
}
