package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ObjectMap;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Draws the bones of selected imported models in the 3D view, in their current (animated or previewed) pose: a
 * line from each bone to its nearest parent bone and a small cross at each joint.
 */
public class SkeletonOverlayRenderer {
    private static final Color BONE_COLOR = new Color(1f, 0.85f, 0.25f, 1f);
    /** Half-size of the joint cross, in scene units. */
    private static final float JOINT_SIZE = 2f;

    private final EditorState editorState;
    private final Camera camera;
    private final SceneRenderer3D sceneRenderer3D;
    private final Matrix4 identity = new Matrix4();
    private final Vector3 from = new Vector3();
    private final Vector3 to = new Vector3();

    public SkeletonOverlayRenderer(EditorState editorState, Camera camera, SceneRenderer3D sceneRenderer3D) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
        this.sceneRenderer3D = Objects.requireNonNull(sceneRenderer3D, "sceneRenderer3D cannot be null");
    }

    public void render(ShapeRenderer shapeRenderer) {
        Scene scene = editorState.getActiveScene();
        if (shapeRenderer == null || scene == null || editorState.getSelectedEntityIds().isEmpty()) {
            return;
        }
        Matrix4 previousTransform = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(identity);
        shapeRenderer.setProjectionMatrix(camera.getCombinedMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(BONE_COLOR);
        for (Entity entity : scene.getAllEntities()) {
            if (!editorState.isSelected(entity.getId())) {
                continue;
            }
            for (ModelComponent model : entity.getComponents(ModelComponent.class)) {
                ModelInstance instance = sceneRenderer3D.getModelInstance(model);
                if (instance != null && model.isEnabled()) {
                    drawBones(shapeRenderer, instance);
                }
            }
        }
        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(previousTransform);
    }

    private void drawBones(ShapeRenderer shapes, ModelInstance instance) {
        Set<Node> bones = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node : instance.nodes) {
            collectBones(node, bones);
        }
        for (Node bone : bones) {
            bone.globalTransform.getTranslation(to).mul(instance.transform);
            shapes.line(to.x - JOINT_SIZE, to.y, to.z, to.x + JOINT_SIZE, to.y, to.z);
            shapes.line(to.x, to.y - JOINT_SIZE, to.z, to.x, to.y + JOINT_SIZE, to.z);
            shapes.line(to.x, to.y, to.z - JOINT_SIZE, to.x, to.y, to.z + JOINT_SIZE);
            Node parent = bone.getParent();
            while (parent != null && !bones.contains(parent)) {
                parent = parent.getParent();
            }
            if (parent != null) {
                parent.globalTransform.getTranslation(from).mul(instance.transform);
                shapes.line(from, to);
            }
        }
    }

    private static void collectBones(Node node, Set<Node> bones) {
        for (NodePart part : node.parts) {
            if (part.invBoneBindTransforms != null) {
                for (ObjectMap.Entry<Node, ?> entry : part.invBoneBindTransforms) {
                    bones.add(entry.key);
                }
            }
        }
        for (Node child : node.getChildren()) {
            collectBones(child, bones);
        }
    }
}
