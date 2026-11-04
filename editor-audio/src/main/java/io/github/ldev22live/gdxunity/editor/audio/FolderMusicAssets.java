package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.song.MusicAssets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reads the files a song uses from the project's asset folder; paths may not leave the folder. */
public final class FolderMusicAssets implements MusicAssets {
    private final Path root;

    public FolderMusicAssets(Path root) {
        this.root = Objects.requireNonNull(root, "root cannot be null").toAbsolutePath().normalize();
    }

    @Override
    public byte[] read(String path) throws IOException {
        Objects.requireNonNull(path, "path cannot be null");
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("The path '" + path + "' is outside the project assets");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IOException("File not found: " + path);
        }
        return Files.readAllBytes(resolved);
    }
}
