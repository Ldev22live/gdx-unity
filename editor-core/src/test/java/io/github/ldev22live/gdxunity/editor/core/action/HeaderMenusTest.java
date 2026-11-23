package io.github.ldev22live.gdxunity.editor.core.action;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.file.NonInteractiveDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.engine.render.RenderQuality3D;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The World-first header's Project and Settings dropdowns (roadmap M1b.2, stage 1). */
class HeaderMenusTest {
    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private EditorActionRegistry registry(EditorState state, SceneRenderer3D renderer) {
        EditorActionRegistry registry = new EditorActionRegistry();
        DefaultEditorActions.registerFileActions(registry, state,
            new SceneFileController(state, new NonInteractiveDialogService(), project.toFile()), () -> { });
        DefaultEditorActions.registerDefaults(registry, state);
        DefaultEditorActions.registerLightingActions(registry, state, renderer);
        return registry;
    }

    @Test
    void shouldShowEveryFileActionInTheProjectMenuInMenuOrder() {
        EditorActionRegistry registry = registry(new EditorState(), new SceneRenderer3D());

        List<String> file = registry.getMenuActions(DefaultEditorActions.MENU_FILE).stream().map(EditorAction::getId).toList();

        assertFalse(file.isEmpty());
        assertEquals(List.of(file), HeaderMenus.sections(registry, HeaderMenus.PROJECT));
        assertTrue(file.contains(DefaultEditorActions.FILE_SAVE_SCENE_AS));
    }

    @Test
    void shouldGiveTheHiddenGraphicsAndAboutEntriesARouteAndDropAThemeTheShellHasNotRegistered() {
        EditorState state = new EditorState();
        SceneRenderer3D renderer = new SceneRenderer3D();
        EditorActionRegistry registry = registry(state, renderer);

        List<List<String>> sections = HeaderMenus.sections(registry, HeaderMenus.SETTINGS);

        assertEquals(List.of(HeaderMenus.SETTINGS_GRAPHICS, HeaderMenus.SETTINGS_ABOUT), sections,
            "without the desktop shell's theme action, its section is left out rather than shown empty");
        for (String id : HeaderMenus.actionIds(registry, HeaderMenus.SETTINGS)) {
            assertTrue(registry.execute(id), id);
        }
        assertEquals(RenderQuality3D.HIGH, renderer.getQuality(), "the entries run: high is the last quality chosen");

        registry.register(EditorAction.builder(HeaderMenus.WINDOW_TOGGLE_THEME, "Toggle Dark/Light Theme", () -> { }).build());
        assertEquals(List.of(HeaderMenus.SETTINGS_GRAPHICS, HeaderMenus.SETTINGS_APPEARANCE, HeaderMenus.SETTINGS_ABOUT),
            HeaderMenus.sections(registry, HeaderMenus.SETTINGS));
    }

    @Test
    void shouldRefuseAMenuTheHeaderDoesNotHave() {
        EditorActionRegistry registry = new EditorActionRegistry();
        assertEquals(List.of(HeaderMenus.PROJECT, HeaderMenus.SETTINGS), HeaderMenus.names());
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> HeaderMenus.sections(registry, "Window"));
        assertTrue(failure.getMessage().contains("Window"), failure.getMessage());
        assertEquals(List.of(), HeaderMenus.sections(registry, HeaderMenus.SETTINGS), "nothing registered, nothing shown");
    }
}
