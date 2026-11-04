package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameViewPlayModeExtensionTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private static Scene sceneWithCamera(CameraComponent.Projection projection) {
        Scene scene = new Scene("game");
        scene.createEntity("Camera").addComponent(new CameraComponent(projection));
        return scene;
    }

    @Test
    void shouldShowPerspectiveGamesIn3DAndRestoreTheViewOnStop() {
        EditorState state = new EditorState();
        GameViewPlayModeExtension extension = new GameViewPlayModeExtension(state);
        Scene scene = sceneWithCamera(CameraComponent.Projection.PERSPECTIVE);

        assertEquals(List.of(), extension.onPlay(scene, null));
        assertEquals(ViewportMode.VIEW_3D, state.getViewportMode());
        extension.onStop(scene);
        assertEquals(ViewportMode.VIEW_2D, state.getViewportMode());
    }

    @Test
    void shouldShowOrthographicGamesIn2D() {
        EditorState state = new EditorState();
        state.setViewportMode(ViewportMode.VIEW_3D);
        GameViewPlayModeExtension extension = new GameViewPlayModeExtension(state);
        Scene scene = sceneWithCamera(CameraComponent.Projection.ORTHOGRAPHIC);

        extension.onPlay(scene, null);
        assertEquals(ViewportMode.VIEW_2D, state.getViewportMode());
        extension.onStop(scene);
        assertEquals(ViewportMode.VIEW_3D, state.getViewportMode());
    }

    @Test
    void shouldLeaveTheViewAloneForScenesWithoutCamera() {
        EditorState state = new EditorState();
        state.setViewportMode(ViewportMode.VIEW_3D);
        GameViewPlayModeExtension extension = new GameViewPlayModeExtension(state);
        Scene scene = new Scene("no camera");

        extension.onPlay(scene, null);
        state.setViewportMode(ViewportMode.VIEW_2D);
        extension.onStop(scene);
        assertEquals(ViewportMode.VIEW_2D, state.getViewportMode(), "the user's choice during play is kept");
    }
}
