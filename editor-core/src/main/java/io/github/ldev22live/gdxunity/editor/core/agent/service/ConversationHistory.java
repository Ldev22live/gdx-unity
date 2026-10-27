package io.github.ldev22live.gdxunity.editor.core.agent.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory conversation history between the user and the AI assistant.
 */
public class ConversationHistory {
    private final List<Entry> entries = new ArrayList<>();
    private final int maxEntries;

    public ConversationHistory() {
        this(50);
    }

    public ConversationHistory(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public synchronized void addUserMessage(String message) {
        addEntry(new Entry(Role.USER, message, null));
    }

    public synchronized void addAssistantMessage(String message, String planSummary) {
        addEntry(new Entry(Role.ASSISTANT, message, planSummary));
    }

    public synchronized void addSystemNotice(String notice) {
        addEntry(new Entry(Role.SYSTEM, notice, null));
    }

    private void addEntry(Entry entry) {
        entries.add(entry);
        while (entries.size() > maxEntries) {
            entries.remove(0);
        }
    }

    public synchronized List<Entry> getEntries() {
        return List.copyOf(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public enum Role {
        USER, ASSISTANT, SYSTEM
    }

    public record Entry(Role role, String text, String planSummary) {}
}
