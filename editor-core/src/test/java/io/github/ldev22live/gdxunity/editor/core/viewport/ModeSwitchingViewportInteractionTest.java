package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Roadmap A03: mouse hover reaches the interaction of the current view mode, and the other one is told the hover ended. */
class ModeSwitchingViewportInteractionTest {

    /** Records the hover calls it receives. */
    private static final class Recorder implements ViewportInteraction {
        final List<String> calls = new ArrayList<>();

        @Override
        public boolean touchDown(float localX, float localY, float panelWidth, float panelHeight, int button, int modifiers) {
            return false;
        }

        @Override
        public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
            return false;
        }

        @Override
        public boolean touchUp(int button) {
            return false;
        }

        @Override
        public boolean scrolled(float amountY) {
            return false;
        }

        @Override
        public Vector3 worldPointAt(float localX, float localY, float panelWidth, float panelHeight) {
            return new Vector3();
        }

        @Override
        public void hovered(float localX, float localY, float panelWidth, float panelHeight) {
            calls.add("hovered " + localX + "," + localY);
        }

        @Override
        public void hoverEnded() {
            calls.add("ended");
        }
    }

    @Test
    void shouldSendHoverToTheActiveModeAndEndItInTheOther() {
        EditorState editorState = new EditorState();
        editorState.newScene("Hover");
        Recorder view2D = new Recorder();
        Recorder view3D = new Recorder();
        ModeSwitchingViewportInteraction interaction = new ModeSwitchingViewportInteraction(editorState, view2D, view3D);

        editorState.setViewportMode(ViewportMode.VIEW_3D);
        interaction.hovered(5f, 6f, 100f, 100f);
        assertEquals(List.of("hovered 5.0,6.0"), view3D.calls);
        assertEquals(List.of("ended"), view2D.calls);

        interaction.hoverEnded();
        assertEquals(List.of("hovered 5.0,6.0", "ended"), view3D.calls, "leaving the view ends the hover");
    }
}
