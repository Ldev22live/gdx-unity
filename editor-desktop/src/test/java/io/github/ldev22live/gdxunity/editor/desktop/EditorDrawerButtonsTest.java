package io.github.ldev22live.gdxunity.editor.desktop;

import io.github.ldev22live.gdxunity.editor.core.workspace.EditorDrawers;
import io.github.ldev22live.gdxunity.editor.core.workspace.PanelLayout;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The top bar's drawer buttons (UI M3). The shell needs a window, so the wiring is read from the source, as the action inventory
 * does; the screenshots in the UI M3 report show the buttons.
 */
class EditorDrawerButtonsTest {
    private static final File APPLICATION = new File("src/main/java/io/github/ldev22live/gdxunity/editor/desktop/EditorApplication.java");

    @Test
    void shouldOpenTheAssistantDrawerWithTheActionTheEditorRegisters() {
        assertEquals(EditorDrawers.TOGGLE_ASSISTANT, EditorApplication.ACTION_TOGGLE_ASSISTANT);
    }

    @Test
    void shouldLightEveryDockedDrawersButtonFromItsOwnOpenState() throws IOException {
        String source = Files.readString(APPLICATION.toPath());

        assertTrue(source.contains("for (EditorDrawers.Drawer drawer : EditorDrawers.tabs())"), "one tab per docked drawer (UI M3b)");
        assertTrue(source.contains("for (EditorDrawers.Drawer drawer : EditorDrawers.docked())"), "and a status bar link");
        for (String id : new String[] {"PanelLayout.TOGGLE_OUTLINER -> panelLayout::isOutlinerShown",
            "PanelLayout.TOGGLE_BOTTOM_PANEL -> panelLayout::isAssetLibraryShown", "PanelLayout.TOGGLE_CONSOLE -> panelLayout::isConsoleShown",
            "EditorDrawers.TOGGLE_ASSISTANT -> () -> isAssistantVisible"}) {
            assertTrue(source.contains("case " + id), "lit from its own state: " + id);
        }
        assertTrue(source.contains("if (PanelLayout.CYCLE_DETAILS.equals(actionId))"), "Properties shows its mode");
        assertTrue(source.contains("case PanelLayout.CYCLE_DETAILS -> () -> panelLayout.getDetailsMode() != PanelLayout.DetailsMode.HIDDEN"),
            "the Properties panel shows unless hidden (the reference layout)");
        for (EditorDrawers.Drawer drawer : EditorDrawers.docked()) {
            String constant = switch (drawer.actionId()) {
                case PanelLayout.TOGGLE_OUTLINER -> "TOGGLE_OUTLINER";
                case PanelLayout.CYCLE_DETAILS -> "CYCLE_DETAILS";
                case PanelLayout.TOGGLE_BOTTOM_PANEL -> "TOGGLE_BOTTOM_PANEL";
                case PanelLayout.TOGGLE_CONSOLE -> "TOGGLE_CONSOLE";
                case EditorDrawers.TOGGLE_ASSISTANT -> "TOGGLE_ASSISTANT";
                default -> throw new AssertionError("a docked drawer the editor has no button for: " + drawer.label());
            };
            assertTrue(source.contains(constant), drawer.label());
        }
    }
}
