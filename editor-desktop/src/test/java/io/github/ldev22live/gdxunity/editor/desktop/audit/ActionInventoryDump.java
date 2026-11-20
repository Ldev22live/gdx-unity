package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.file.NonInteractiveDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.workspace.PanelLayout;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceLayoutConfig;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptActions;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptAssets;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptSession;
import io.github.ldev22live.gdxunity.editor.terrain.LevelActions;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the editor's action registry the way {@code EditorApplication} does, from every static registration, without a window. The
 * nine actions {@code EditorApplication} registers itself are read from its source by {@link ActionInventoryTest}.
 */
final class ActionInventoryDump {
    private ActionInventoryDump() { }

    static List<EditorAction> registeredActions(File project) {
        EditorState state = new EditorState();
        EditorActionRegistry registry = new EditorActionRegistry();
        SceneFileController files = new SceneFileController(state, new NonInteractiveDialogService(), project);
        File samples = new File(project, "samples");
        DefaultEditorActions.registerFileActions(registry, state, files, () -> { });
        DefaultEditorActions.registerSampleActions(registry, state, files, samples);
        DefaultEditorActions.registerTemplateAction(registry, state, files, project, samples, new File(project, "projects"));
        DefaultEditorActions.registerImportAction(registry, state, new NonInteractiveDialogService(), project, path -> { });
        DefaultEditorActions.registerDefaults(registry, state);
        DefaultEditorActions.registerCreate3DActions(registry, state, Vector3::new);
        SculptSession sculpts = new SculptSession(new SculptAssets(project));
        SculptActions.register(registry, state, sculpts, Vector3::new);
        SculptActions.registerExport(registry, state, sculpts, model -> { });
        SculptActions.registerFromSelected(registry, state, sculpts, new ColliderGeometry(null));
        DefaultEditorActions.registerLightingActions(registry, state, org.mockito.Mockito.mock(SceneRenderer3D.class));
        TerrainSession terrain = new TerrainSession(new TerrainLibrary(AssetSource.directory(project)));
        LevelSession level = new LevelSession(state, terrain, project);
        LevelActions.register(registry, state, level, on -> { });
        LevelActions.registerHeightmaps(registry, state, level, new NonInteractiveDialogService(), project);
        PanelLayout.registerActions(registry, state, new PanelLayout(new WorkspaceLayoutConfig()));
        List<EditorAction> actions = new ArrayList<>(registry.getAll());
        actions.sort(Comparator.comparing(EditorAction::getId));
        return actions;
    }
}
