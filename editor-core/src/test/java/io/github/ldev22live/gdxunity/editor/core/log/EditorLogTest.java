package io.github.ldev22live.gdxunity.editor.core.log;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog.Severity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorLogTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldKeepOnlyNewestEntriesUpToCapacity() {
        EditorLog log = new EditorLog(3);
        for (int i = 1; i <= 5; i++) {
            log.info("Test", "message " + i);
        }
        assertEquals(3, log.size());
        assertEquals("message 3", log.getEntries(Severity.INFO).get(0).message());
        assertEquals("message 5", log.getEntries(Severity.INFO).get(2).message());
    }

    @Test
    void shouldFilterByMinimumSeverity() {
        EditorLog log = new EditorLog();
        log.info("A", "info");
        log.warning("B", "warn");
        log.error("C", "err");

        assertEquals(3, log.getEntries(Severity.INFO).size());
        assertEquals(2, log.getEntries(Severity.WARNING).size());
        assertEquals("err", log.getEntries(Severity.ERROR).get(0).message());
    }

    @Test
    void shouldCaptureEditorStatusMessagesWithSeverity() {
        EditorState editorState = new EditorState();
        EditorLog log = new EditorLog();
        log.captureStatusMessages(editorState);

        editorState.setStatusMessage("All good", false);
        editorState.setStatusMessage("Broken", true);

        assertEquals(1, log.getEntries(Severity.ERROR).size());
        assertEquals("Broken", log.getEntries(Severity.ERROR).get(0).message());
        assertEquals("Editor", log.getEntries(Severity.INFO).get(0).source());
    }

    @Test
    void shouldNotifyListenersOnAddAndClear() {
        EditorLog log = new EditorLog();
        AtomicInteger notifications = new AtomicInteger();
        log.addListener(changed -> notifications.incrementAndGet());

        log.info("A", "one");
        log.clear();
        assertEquals(2, notifications.get());
        assertEquals(0, log.size());
        assertThrows(IllegalArgumentException.class, () -> new EditorLog(0));
    }
}
