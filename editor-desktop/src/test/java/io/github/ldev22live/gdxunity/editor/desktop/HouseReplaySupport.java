package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.project.GameExporter;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceNavigator;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameScripts;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What the house-edit tests (M3c.6, MC.2) share: an editor, placement, a stand-in export image, and playing an export. */
final class HouseReplaySupport {
    /** One frame of the game runtime. */
    static final float FRAME = 1f / 60f;
    /** Two seconds of the exported game. */
    static final int PLAY_FRAMES = 120;

    private HouseReplaySupport() { }

    /** An editor state with every component the desktop editor serializes. */
    static EditorState newEditor() {
        EditorState editor = new EditorState();
        EditorApplication.registerComponentSerializers(editor.getSerializer().getRegistry());
        return editor;
    }

    /** Places {@code prefab} from the level palette on the ground at {@code at}, as a click in the World does. */
    static Entity place(LevelSession level, Terrain terrain, String prefab, Vector3 at) {
        level.setObjectPath(prefab);
        return level.placeObject(at.x, terrain.heightAt(at.x, at.z), at.z);
    }

    /** A packaged editor's layout with stand-in files, as in {@code TemplateGalleryProjectsTest}. */
    static Path fakeRuntimeImage(Path root) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(GameExporter.EDITOR_LAUNCHER_NAME + ".exe", "editor exe");
        files.put(GameExporter.LAUNCHER_NAME + ".exe", "game exe");
        files.put("runtime/bin/java.dll", "jvm");
        files.put("app/engine-desktop-1.0.0.jar", "engine");
        files.put("app/" + GameExporter.LAUNCHER_NAME + ".cfg", "game cfg");
        files.put("app/" + GameExporter.EDITOR_LAUNCHER_NAME + ".cfg", "editor cfg");
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
        return root;
    }

    /**
     * Plays an exported scene for two seconds in the game runtime's session, as the exported game does.
     *
     * @return every error the session reported
     */
    static List<String> playExported(File gameRoot, Scene loaded) throws IOException {
        List<String> errors = new ArrayList<>();
        GameSession.Log log = new GameSession.Log() {
            @Override
            public void info(String message) { }

            @Override
            public void error(String message) {
                errors.add(message);
            }
        };
        loaded.setColliderGeometry(new ColliderGeometry(null));
        loaded.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(gameRoot))));
        try (GameScripts scripts = new GameScripts(new File(gameRoot, "no-scripts"), HouseReplaySupport.class.getClassLoader())) {
            GameSession session = new GameSession(gameRoot, loaded, scripts, log);
            try {
                errors.addAll(session.start());
                for (int i = 0; i < PLAY_FRAMES; i++) {
                    session.update(FRAME);
                }
                errors.addAll(session.getRuntimeErrors());
            } finally {
                session.dispose();
            }
        }
        return errors;
    }

    /** The screen as far as navigation needs it: the World tab, sculpt mode for 3D Modeling. */
    static final class RecordingView implements WorkspaceNavigator.View {
        private String tab = Workspace.TAB_WORLD;
        private boolean sculpt;

        @Override
        public String centerTab() {
            return tab;
        }

        @Override
        public void showCenterTab(String title) {
            tab = title;
        }

        @Override
        public boolean isSculptMode() {
            return sculpt;
        }

        @Override
        public boolean runAction(String actionId) {
            if (actionId.equals(WorkspaceNavigator.SCULPT_ENTER_ACTION)) {
                sculpt = true;
            } else if (actionId.equals(WorkspaceNavigator.SCULPT_EXIT_ACTION)) {
                sculpt = false;
            }
            return true;
        }

        @Override
        public void showContentBrowser() { }

        @Override
        public void frameSelection() { }
    }

    /** Dialogs never shown: these tests save to the file they opened and ask nothing. */
    static final class NoDialogs implements EditorDialogService {
        @Override
        public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
            throw new AssertionError("no open dialog expected: " + title);
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
            throw new AssertionError("no save dialog expected: " + title);
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            throw new AssertionError("no unsaved-changes question expected: " + documentName);
        }

        @Override
        public void showError(String title, String message) {
            throw new AssertionError(title + ": " + message);
        }
    }
}
