package io.github.ldev22live.gdxunity.editor.core.navigation;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.agent.DefaultAgentTools;
import io.github.ldev22live.gdxunity.editor.core.agent.tools.NavigationCheckTool;
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavAgent3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavMeshBoundsComponent;
import io.github.ldev22live.gdxunity.engine.scene.navigation.serialization.NavigationSerializers;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Navigation in the editor (roadmap M6.4, TD-092): the catalog entries and the read-only {@code navigation_check} agent tool. */
class NavigationEditingTest {
    private EditorState editorState;
    private final ComponentCatalog catalog = ComponentCatalog.createDefault();

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        NavigationSerializers.registerAll(registry);
        editorState = new EditorState(new SceneSerializer(registry));
        Scene scene = new Scene("Courtyard");
        Entity ground = scene.createEntity("Ground");
        ground.getTransform().setLocalPosition(0f, -10f, 0f);
        ground.addComponent(new BoxCollider3DComponent(new Vector3(2000f, 20f, 2000f)));
        Entity wall = scene.createEntity("Wall");
        wall.getTransform().setLocalPosition(0f, 100f, 0f);
        wall.addComponent(new BoxCollider3DComponent(new Vector3(100f, 200f, 1200f)));
        editorState.setActiveScene(scene);
    }

    private ComponentCatalog.Entry entry(String name) {
        return catalog.search(name).stream().filter(e -> e.displayName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void shouldOfferBothComponentsUnderNavigationIn3DButNotIn2D() {
        assertEquals("Navigation", entry("Nav Mesh Bounds").category());
        assertEquals("Navigation", entry("Nav Agent").category());
        assertTrue(catalog.search("nav", GameMode.MODE_2D).isEmpty(), "2D games have no 3D navigation");
        assertEquals(2, catalog.search("nav", GameMode.MODE_3D).size());

        Entity guard = editorState.getActiveScene().createEntity("Guard");
        assertTrue(catalog.addTo(editorState, guard, entry("Nav Agent")));
        assertNotNull(guard.getComponent(NavAgent3DComponent.class));
        assertFalse(catalog.addTo(editorState, guard, entry("Nav Agent")), "one per entity");
        editorState.undo();
        assertNull(guard.getComponent(NavAgent3DComponent.class), "one undo step");
    }

    @Test
    void shouldSayWhenTheSceneHasNoBoundsToBake() throws Exception {
        AgentToolResult result = new NavigationCheckTool().execute(Map.of(), new AgentToolContext(editorState));
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(0, result.getOutputs().get("regions"));
        assertTrue(result.getMessage().contains("add one (Add Component > Navigation)"), result.getMessage());
    }

    @Test
    void shouldFindTheWayRoundTheWallWithoutKeepingTheBake() throws Exception {
        editorState.getActiveScene().createEntity("Bounds").addComponent(new NavMeshBoundsComponent());
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("from", List.of(-600, 0, 0));
        parameters.put("to", List.of(600.0, 0.0, 0.0));

        AgentToolResult result = DefaultAgentTools.createDefaultRegistry().getTool(NavigationCheckTool.ID).orElseThrow()
            .execute(parameters, new AgentToolContext(editorState));

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(1, result.getOutputs().get("regions"));
        assertTrue((Integer) result.getOutputs().get("walkableCells") > 5000);
        assertEquals(true, result.getOutputs().get("reachable"));
        assertTrue((Float) result.getOutputs().get("length") > 1700f, "round the wall, not through it: " + result.getOutputs());
        assertEquals(List.of(), result.getOutputs().get("problems"));
        assertNull(editorState.getActiveScene().getNavigation(), "a check while editing does not keep a bake that Play would reuse");
        assertTrue(new NavigationCheckTool().isReadOnly());
    }

    @Test
    void shouldRefuseHalfAQueryOrAPointThatIsNotThreeNumbers() {
        NavigationCheckTool tool = new NavigationCheckTool();
        AgentToolContext context = new AgentToolContext(editorState);
        assertEquals(List.of("Give both 'from' and 'to' to find a path, or neither"),
            tool.validateParameters(Map.of("from", List.of(0, 0, 0)), context));
        assertTrue(tool.validateParameters(Map.of("from", List.of(0, 0), "to", List.of(1, 2, 3)), context).get(0)
            .startsWith("'from' must be a list of three numbers"));
    }
}
