package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavAgent3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavMeshBoundsComponent;

import java.util.function.Consumer;

/**
 * Details editors for Nav Mesh Bounds and Nav Agent (roadmap M6.4, TD-092). Every field is its own undo step; a value the component
 * refuses leaves it unchanged and the field shows the old value again.
 */
public final class NavigationComponentEditorProviders {
    private NavigationComponentEditorProviders() {}

    /** Size, cell size and the agent's shape and reach of a {@link NavMeshBoundsComponent}. */
    public static class Bounds implements ComponentEditorProvider<NavMeshBoundsComponent, Table> {
        @Override
        public Class<NavMeshBoundsComponent> getComponentClass() {
            return NavMeshBoundsComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Nav Mesh Bounds";
        }

        @Override
        public void buildEditor(Table container, Entity entity, NavMeshBoundsComponent bounds, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.vector3Row(container, skin, "Size", bounds.getSize(),
                size -> changeBounds(editorState, entity, bounds, b -> b.setSize(size), "Change Nav Bounds Size"));
            InspectorFieldRows.floatRow(container, skin, "Cell", bounds.getCellSize(),
                value -> changeBounds(editorState, entity, bounds, b -> b.setCellSize(value), "Change Nav Cell Size"));
            InspectorFieldRows.floatRow(container, skin, "Radius", bounds.getAgentRadius(),
                value -> changeBounds(editorState, entity, bounds, b -> b.setAgentRadius(value), "Change Agent Radius"));
            InspectorFieldRows.floatRow(container, skin, "Height", bounds.getAgentHeight(),
                value -> changeBounds(editorState, entity, bounds, b -> b.setAgentHeight(value), "Change Agent Height"));
            InspectorFieldRows.floatRow(container, skin, "Max slope", bounds.getMaxSlope(),
                value -> changeBounds(editorState, entity, bounds, b -> b.setMaxSlope(value), "Change Max Slope"));
            InspectorFieldRows.floatRow(container, skin, "Step", bounds.getStepHeight(),
                value -> changeBounds(editorState, entity, bounds, b -> b.setStepHeight(value), "Change Step Height"));
        }
    }

    /** Speed, stopping distance and facing of a {@link NavAgent3DComponent}. */
    public static class Agent implements ComponentEditorProvider<NavAgent3DComponent, Table> {
        @Override
        public Class<NavAgent3DComponent> getComponentClass() {
            return NavAgent3DComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Nav Agent";
        }

        @Override
        public void buildEditor(Table container, Entity entity, NavAgent3DComponent agent, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.floatRow(container, skin, "Speed", agent.getSpeed(),
                value -> changeAgent(editorState, entity, agent, a -> a.setSpeed(value), "Change Agent Speed"));
            InspectorFieldRows.floatRow(container, skin, "Stop at", agent.getStoppingDistance(),
                value -> changeAgent(editorState, entity, agent, a -> a.setStoppingDistance(value), "Change Stopping Distance"));
            InspectorFieldRows.toggleRow(container, skin, "Face path", agent.isFaceMovement(),
                on -> changeAgent(editorState, entity, agent, a -> a.setFaceMovement(on), on ? "Face Where It Walks" : "Keep Its Facing"));
        }
    }

    /** One undo step; {@code edit} runs on a copy first, so a refused value throws before anything changes. */
    private static void changeBounds(EditorState editorState, Entity entity, NavMeshBoundsComponent bounds,
                                     Consumer<NavMeshBoundsComponent> edit, String description) {
        NavMeshBoundsComponent after = bounds.copy();
        edit.accept(after);
        editorState.executeCommand(new ModifyComponentPropertyCommand<NavMeshBoundsComponent, NavMeshBoundsComponent>(editorState,
            entity.getId(), bounds, bounds.copy(), after, bounds::set, description));
    }

    private static void changeAgent(EditorState editorState, Entity entity, NavAgent3DComponent agent, Consumer<NavAgent3DComponent> edit,
                                    String description) {
        NavAgent3DComponent after = agent.copy();
        edit.accept(after);
        editorState.executeCommand(new ModifyComponentPropertyCommand<NavAgent3DComponent, NavAgent3DComponent>(editorState,
            entity.getId(), agent, agent.copy(), after, agent::set, description));
    }
}
