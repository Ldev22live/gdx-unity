package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.io.IOException;
import java.io.InputStream;

/**
 * How the Asset Library reaches a provider (TD-095). The editor uses {@link JdkHttpSource}; tests give recorded answers, so no
 * test goes online.
 */
public interface HttpSource {
    /**
     * Reads a small answer (a catalogue or a file list) whole.
     *
     * @throws IOException when the provider cannot be reached or does not answer 200
     */
    byte[] get(String url) throws IOException;

    /**
     * Opens a download; the caller reads and closes it.
     *
     * @throws IOException when the provider cannot be reached or does not answer 200
     */
    InputStream open(String url) throws IOException;
}
