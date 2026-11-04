package io.github.ldev22live.gdxunity.editor.core.file;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonInteractiveDialogServiceTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldReportAFailedOpenInTheStatusInsteadOfWaitingForAClick() {
        EditorState editorState = new EditorState();
        StringBuilder status = new StringBuilder();
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onStatusMessage(String message, boolean isError) {
                if (isError) {
                    status.append(message);
                }
            }
        });
        SceneFileController files = new SceneFileController(editorState, new NonInteractiveDialogService(), tempDir.toFile());

        assertFalse(files.openFile(tempDir.resolve("missing.json").toFile()));
        assertTrue(status.toString().contains("missing.json"), status.toString());
        assertFalse(files.open(), "file choices are cancelled");
    }
}
