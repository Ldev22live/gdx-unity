package io.github.ldev22live.gdxunity.editor.core.agent.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to extract clean JSON text from raw LLM responses that may include
 * markdown formatting code blocks (```json ... ```) or conversational commentary.
 */
public final class PlanExtractor {
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private PlanExtractor() {}

    public static String extractJson(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "{}";
        }
        String trimmed = rawText.trim();

        // 1. Check for markdown code blocks
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 2. Look for JSON object bounds '{' ... '}'
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1).trim();
        }

        return trimmed;
    }
}
