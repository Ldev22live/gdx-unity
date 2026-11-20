package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Recorded answers by address; an address with no answer behaves like an unreachable provider. Counts every request. */
final class FakeHttpSource implements HttpSource {
    private final Map<String, byte[]> answers = new HashMap<>();
    final List<String> requests = new ArrayList<>();
    boolean offline;

    FakeHttpSource answer(String url, String body) {
        return answer(url, body.getBytes(StandardCharsets.UTF_8));
    }

    FakeHttpSource answer(String url, byte[] body) {
        answers.put(url, body);
        return this;
    }

    @Override
    public byte[] get(String url) throws IOException {
        requests.add(url);
        byte[] body = answers.get(url);
        if (offline || body == null) {
            throw new IOException("unreachable: " + url);
        }
        return body;
    }

    @Override
    public InputStream open(String url) throws IOException {
        return new ByteArrayInputStream(get(url));
    }
}
