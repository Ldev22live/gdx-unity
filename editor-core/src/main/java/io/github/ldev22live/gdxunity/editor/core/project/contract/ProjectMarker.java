package io.github.ldev22live.gdxunity.editor.core.project.contract;

import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * The {@code gdxunity.project} marker (TD-106 point 1). Format 1 has {@code name} and {@code startScene}; format 2 adds
 * {@code projectId} and {@code manifest}. The manifest is the source of truth: the marker's {@code name} and {@code startScene} are
 * written from it on every save so that format-1 readers (the exporter, older editors) keep working, and a disagreement is
 * reported by {@link #inconsistencies(ProjectManifest)}, the manifest winning.
 */
public record ProjectMarker(int format, String name, String startScene, String projectId, String manifest) {
    /** The marker format written with a manifest. */
    public static final int FORMAT_WITH_MANIFEST = 2;

    /** Reads the marker of {@code projectRoot}. A missing or non-numeric {@code format} reads as 1, as format-1 files were written. */
    public static ProjectMarker read(Path projectRoot) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(projectRoot.resolve(EditorProject.MARKER_FILE), StandardCharsets.UTF_8)));
        int format;
        try {
            format = Integer.parseInt(properties.getProperty("format", "1").trim());
        } catch (NumberFormatException e) {
            format = 1;
        }
        return new ProjectMarker(format, properties.getProperty("name"), properties.getProperty("startScene"),
            properties.getProperty("projectId"), properties.getProperty("manifest"));
    }

    /** The format-2 marker that mirrors {@code manifest}. */
    public static ProjectMarker of(ProjectManifest manifest) {
        Objects.requireNonNull(manifest, "manifest cannot be null");
        return new ProjectMarker(FORMAT_WITH_MANIFEST, manifest.name(), manifest.startScene(), manifest.projectId(),
            ProjectManifest.FILE_NAME);
    }

    /** True when the marker points to a manifest (format 2 or later with a {@code manifest} key). */
    public boolean hasManifest() {
        return format >= FORMAT_WITH_MANIFEST && manifest != null;
    }

    /** What this marker says differently from {@code manifest}; empty when they agree. */
    public List<String> inconsistencies(ProjectManifest manifest) {
        List<String> out = new ArrayList<>();
        if (!manifest.projectId().equals(projectId)) {
            out.add("projectId: marker " + projectId + ", manifest " + manifest.projectId());
        }
        if (!manifest.name().equals(name)) {
            out.add("name: marker " + name + ", manifest " + manifest.name());
        }
        if (!manifest.startScene().equals(startScene)) {
            out.add("startScene: marker " + startScene + ", manifest " + manifest.startScene());
        }
        return out;
    }

    /** The marker file's text, keys in a fixed order (format, name, startScene, projectId, manifest), {@code \n} line ends. */
    public String toText() {
        StringBuilder out = new StringBuilder("format=").append(format).append('\n');
        out.append("name=").append(escape(name)).append('\n');
        if (startScene != null) {
            out.append("startScene=").append(escape(startScene)).append('\n');
        }
        if (projectId != null) {
            out.append("projectId=").append(projectId).append('\n');
        }
        if (manifest != null) {
            out.append("manifest=").append(escape(manifest)).append('\n');
        }
        return out.toString();
    }

    /** Escapes a value as {@link Properties#load} reads it back (backslash, leading space, line breaks). */
    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(i == 0 && c == ' ' ? "\\ " : String.valueOf(c));
            }
        }
        return out.toString();
    }
}
