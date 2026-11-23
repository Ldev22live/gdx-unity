package io.github.ldev22live.gdxunity.editor.core;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.editor.core.input.ViewportCameraController3D;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Game modes (TD-067): inference, the scene view per mode, hidden component categories, the state API and the locked side view. */
class GameModeTest {
    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private static boolean offers(ComponentCatalog catalog, GameMode mode, String name) {
        return catalog.search(name, mode).stream().anyMatch(e -> e.displayName().equals(name));
    }

    @Test
    void shouldInferTwoDForAnEmptySceneAndAFlatOneAndThreeDWhenThereIsA3DComponent() {
        Scene flat = new Scene("Flat");
        assertEquals(GameMode.MODE_2D, GameModeProfile.resolve(flat));
        flat.createEntity("Quad").addComponent(new ShapeRendererComponent(10f, 10f, Color.WHITE));
        assertEquals(GameMode.MODE_2D, GameModeProfile.resolve(flat));

        flat.createEntity("Box").addComponent(new MeshPrimitiveComponent());

        assertEquals(GameMode.MODE_3D, GameModeProfile.resolve(flat));
    }

    @Test
    void shouldPreferTheSavedModeOverInference() {
        Scene scene = new Scene("Side");
        scene.createEntity("Box").addComponent(new MeshPrimitiveComponent());
        scene.setGameMode(GameMode.MODE_2_5D);

        assertEquals(GameMode.MODE_2_5D, GameModeProfile.resolve(scene));
    }

    @Test
    void shouldGiveEachModeItsSceneViewAndOnlyTheSideModeIsLocked() {
        assertEquals(ViewportMode.VIEW_2D, GameModeProfile.of(GameMode.MODE_2D).getViewport());
        assertEquals(ViewportMode.VIEW_3D, GameModeProfile.of(GameMode.MODE_2_5D).getViewport());
        assertEquals(ViewportMode.VIEW_3D, GameModeProfile.of(GameMode.MODE_3D).getViewport());
        assertFalse(GameModeProfile.of(GameMode.MODE_2D).isSideViewLocked());
        assertTrue(GameModeProfile.of(GameMode.MODE_2_5D).isSideViewLocked());
        assertFalse(GameModeProfile.of(GameMode.MODE_3D).isSideViewLocked());
    }

    @Test
    void shouldHideComponentsThatDoNotBelongToTheMode() {
        ComponentCatalog catalog = ComponentCatalog.createDefault();

        assertFalse(offers(catalog, GameMode.MODE_2D, "Rigid Body 3D"));
        assertFalse(offers(catalog, GameMode.MODE_2D, "Mesh Primitive"));
        assertTrue(offers(catalog, GameMode.MODE_2D, "Rigid Body 2D"));
        assertTrue(offers(catalog, GameMode.MODE_2D, "Sprite Renderer"));

        assertTrue(offers(catalog, GameMode.MODE_2_5D, "Rigid Body 3D"));
        assertTrue(offers(catalog, GameMode.MODE_2_5D, "Sprite Renderer"));
        assertFalse(offers(catalog, GameMode.MODE_2_5D, "Rigid Body 2D"));

        assertTrue(offers(catalog, GameMode.MODE_3D, "Directional Light"));
        assertFalse(offers(catalog, GameMode.MODE_3D, "Box Collider 2D"));
        assertTrue(catalog.search("", GameMode.MODE_3D).size() < catalog.search("").size());
    }

    @Test
    void shouldStillOfferScriptsAndAudioInEveryMode() {
        ComponentCatalog catalog = ComponentCatalog.createDefault();

        for (GameMode mode : GameMode.values()) {
            assertTrue(offers(catalog, mode, "Script"), mode.name());
            assertTrue(offers(catalog, mode, "Audio Source"), mode.name());
        }
    }

    @Test
    void shouldSwitchTheSceneViewAndNotifyWhenTheModeChanges() {
        EditorState state = new EditorState();
        List<String> events = new ArrayList<>();
        state.addListener(new EditorState.Listener() {
            @Override
            public void onGameModeChanged(GameMode oldMode, GameMode newMode) {
                events.add(oldMode + ">" + newMode);
            }
        });

        state.setGameMode(GameMode.MODE_3D);
        state.setGameMode(GameMode.MODE_3D);
        state.setGameMode(GameMode.MODE_2D);

        assertEquals(List.of("MODE_2D>MODE_3D", "MODE_3D>MODE_2D"), events, "no event when the mode did not change");
        assertEquals(GameMode.MODE_2D, state.getActiveScene().getGameMode());
        assertEquals(ViewportMode.VIEW_2D, state.getViewportMode());
        state.setGameMode(GameMode.MODE_2_5D);
        assertEquals(ViewportMode.VIEW_3D, state.getViewportMode());
    }

    @Test
    void shouldStartANewSceneInTheChosenModeAndNotifyAboutIt() {
        EditorState state = new EditorState();
        List<GameMode> seen = new ArrayList<>();
        state.addListener(new EditorState.Listener() {
            @Override
            public void onGameModeChanged(GameMode oldMode, GameMode newMode) {
                seen.add(newMode);
            }
        });

        state.newScene("Side Scroller", GameMode.MODE_2_5D);

        assertEquals(GameMode.MODE_2_5D, state.getGameMode());
        assertEquals("Side Scroller", state.getActiveScene().getName());
        assertEquals(List.of(GameMode.MODE_2_5D), seen);
        assertEquals(ViewportMode.VIEW_3D, state.getViewportMode());
    }

    @Test
    void shouldOpenASceneInTheViewOfItsMode() {
        EditorState state = new EditorState();
        state.setViewportMode(ViewportMode.VIEW_3D);
        Scene flat = new Scene("Flat");
        flat.setGameMode(GameMode.MODE_2D);

        state.setActiveScene(flat);

        assertEquals(ViewportMode.VIEW_2D, state.getViewportMode());
    }

    @Test
    void shouldRefuseToChangeTheModeWhilePlaying() {
        EditorState state = new EditorState();
        state.setGameMode(GameMode.MODE_3D);
        io.github.ldev22live.gdxunity.editor.core.play.PlayModeController play = new io.github.ldev22live.gdxunity.editor.core.play.PlayModeController(state);
        state.setPlayModeController(play);
        assertTrue(play.play());

        state.setGameMode(GameMode.MODE_2D);

        assertEquals(GameMode.MODE_3D, state.getGameMode());
        assertTrue(state.isStatusError());
    }

    @Test
    void shouldLockTheCameraToASideViewThatPansAndDoesNotOrbitOrFly() {
        PerspectiveCamera3D camera = new PerspectiveCamera3D(67f, 800f, 600f, 0.1f, 200f);
        camera.setPosition(10f, 8f, 6f);
        camera.lookAt(0f, 0f, 0f);
        camera.update();
        ViewportCameraController3D controller = new ViewportCameraController3D(camera);
        controller.setTarget(0f, 0f, 0f);

        controller.setSideViewLocked(true);

        assertEquals(0f, camera.getPosition().x, 1e-4f);
        assertEquals(0f, camera.getPosition().y, 1e-4f);
        assertTrue(camera.getPosition().z >= ViewportCameraController3D.DEFAULT_SIDE_VIEW_DISTANCE - 1e-3f);
        assertEquals(-1f, camera.getDirection().z, 1e-4f);

        controller.startNavigation(ViewportCameraController3D.NavigationMode.ORBIT, 100f, 100f);
        assertEquals(ViewportCameraController3D.NavigationMode.PAN, controller.getCurrentMode(), "an orbit drag pans");
        controller.processDrag(140f, 100f);
        controller.stopNavigation();
        assertEquals(-1f, camera.getDirection().z, 1e-4f, "still looking along -Z");
        assertTrue(Math.abs(camera.getPosition().x) > 0.01f, "the camera panned sideways");

        controller.orbit(50f, 50f);
        controller.flyLook(50f, 50f);
        assertEquals(-1f, camera.getDirection().z, 1e-4f, "orbit and fly do nothing while locked");

        controller.setSideViewLocked(false);
        controller.startNavigation(ViewportCameraController3D.NavigationMode.ORBIT, 0f, 0f);
        assertEquals(ViewportCameraController3D.NavigationMode.ORBIT, controller.getCurrentMode());
    }
}
