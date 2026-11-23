package io.github.ldev22live.gdxunity.editor.desktop.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TinyFileDialogServiceTest {

    @Test
    void shouldReplaceQuotesThatNativeDialogsReject() {
        assertEquals("Save `level` before it`s lost?", TinyFileDialogService.sanitize("Save \"level\" before it's lost?"));
        assertEquals("", TinyFileDialogService.sanitize(null));
    }
}
