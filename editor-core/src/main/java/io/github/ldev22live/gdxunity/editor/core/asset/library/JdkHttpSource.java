package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * {@link HttpSource} on the JDK's own HTTP client (no new dependency). Every request carries the User-Agent
 * {@code ForgeRoom/<version>} and nothing personal (TD-095), and only HTTPS addresses of the hosts the provider names are allowed.
 */
public final class JdkHttpSource implements HttpSource {
    /** How long connecting may take before the provider counts as unreachable. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    /** How long a small answer (a catalogue) may take in all. */
    private static final Duration GET_TIMEOUT = Duration.ofSeconds(60);
    /** HTTP status of a successful answer. */
    private static final int HTTP_OK = 200;

    private final String userAgent;
    private final List<String> allowedHosts;
    private final HttpClient client;

    /**
     * @param version      ForgeRoom's version, for the User-Agent
     * @param allowedHosts the hosts (and their sub-domains) requests may go to
     */
    public JdkHttpSource(String version, List<String> allowedHosts) {
        Objects.requireNonNull(version, "version cannot be null");
        this.userAgent = "ForgeRoom/" + version;
        this.allowedHosts = List.copyOf(allowedHosts);
        this.client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /** The User-Agent sent with every request. */
    public String userAgent() {
        return userAgent;
    }

    @Override
    public byte[] get(String url) throws IOException {
        HttpResponse<byte[]> response = send(request(url).timeout(GET_TIMEOUT).build(), HttpResponse.BodyHandlers.ofByteArray());
        return response.body();
    }

    @Override
    public InputStream open(String url) throws IOException {
        HttpResponse<InputStream> response = send(request(url).build(), HttpResponse.BodyHandlers.ofInputStream());
        return response.body();
    }

    private HttpRequest.Builder request(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("Not a web address: " + url, e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !isAllowed(uri.getHost())) {
            throw new IOException("Refused to contact " + url + ": only HTTPS to " + String.join(", ", allowedHosts));
        }
        return HttpRequest.newBuilder(uri).header("User-Agent", userAgent).GET();
    }

    private boolean isAllowed(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        for (String allowed : allowedHosts) {
            if (lower.equals(allowed) || lower.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        HttpResponse<T> response;
        try {
            response = client.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Cancelled: " + request.uri(), e);
        }
        if (response.statusCode() != HTTP_OK) {
            if (response.body() instanceof InputStream stream) {
                stream.close();
            }
            throw new IOException("HTTP " + response.statusCode() + " from " + request.uri());
        }
        return response;
    }
}
