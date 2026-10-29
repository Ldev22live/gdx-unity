package io.github.ldev22live.gdxunity.editor.desktop.file;

import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.util.Optional;

/**
 * Native open/save/message dialogs through LWJGL tinyfd (TD-022). Calls block the render thread until the
 * user answers, like any modal desktop dialog.
 */
public class TinyFileDialogService implements EditorDialogService {

    /** Native tinyfd_messageBox return values for a "yesnocancel" dialog (0 = cancel, 1 = yes, 2 = no). */
    private static final int ANSWER_CANCEL = 0;
    private static final int ANSWER_YES = 1;
    /** Default (focused) button: Yes/OK. */
    private static final int DEFAULT_BUTTON_YES = 1;

    @Override
    public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
        String defaultPath = initialDirectory != null ? initialDirectory.getAbsolutePath() + File.separator : null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = filterPatterns(stack, extension);
            String result = TinyFileDialogs.tinyfd_openFileDialog(sanitize(title), defaultPath, filters,
                sanitize(filterDescription), false);
            return Optional.ofNullable(result).map(File::new);
        }
    }

    @Override
    public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
        String defaultPath = suggestedFile != null ? suggestedFile.getAbsolutePath() : null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = filterPatterns(stack, extension);
            String result = TinyFileDialogs.tinyfd_saveFileDialog(sanitize(title), defaultPath, filters,
                sanitize(filterDescription));
            return Optional.ofNullable(result).map(File::new);
        }
    }

    @Override
    public UnsavedChangesChoice askUnsavedChanges(String documentName) {
        int answer;
        // The convenience overload returns boolean and cannot tell "No" from "Cancel"; the raw call returns the int
        try (MemoryStack stack = MemoryStack.stackPush()) {
            answer = TinyFileDialogs.ntinyfd_messageBox(
                MemoryUtil.memAddress(stack.UTF8("Unsaved Changes")),
                MemoryUtil.memAddress(stack.UTF8(sanitize("Save changes to " + documentName + " before continuing?"))),
                MemoryUtil.memAddress(stack.UTF8("yesnocancel")),
                MemoryUtil.memAddress(stack.UTF8("question")),
                DEFAULT_BUTTON_YES);
        }
        return switch (answer) {
            case ANSWER_YES -> UnsavedChangesChoice.SAVE;
            case ANSWER_CANCEL -> UnsavedChangesChoice.CANCEL;
            default -> UnsavedChangesChoice.DISCARD;
        };
    }

    @Override
    public void showError(String title, String message) {
        TinyFileDialogs.tinyfd_messageBox(sanitize(title), sanitize(message), "ok", "error", true);
    }

    private static PointerBuffer filterPatterns(MemoryStack stack, String extension) {
        PointerBuffer filters = stack.mallocPointer(1);
        filters.put(stack.UTF8("*." + extension));
        filters.flip();
        return filters;
    }

    /** tinyfd rejects quote characters in dialog text on some platforms. */
    static String sanitize(String text) {
        return text == null ? "" : text.replace('"', '`').replace('\'', '`');
    }
}
