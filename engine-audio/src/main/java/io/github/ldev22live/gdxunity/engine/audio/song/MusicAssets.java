package io.github.ldev22live.gdxunity.engine.audio.song;

import java.io.IOException;

/** Reads the files a song refers to (samples, SoundFonts). The editor reads the project folder, a game its assets. */
public interface MusicAssets {
    /** The bytes of a project-relative file. */
    byte[] read(String path) throws IOException;

    /** Assets for songs that use no files: every read fails. */
    MusicAssets NONE = path -> {
        throw new IOException("This song uses the file '" + path + "' but no asset source was provided");
    };
}
