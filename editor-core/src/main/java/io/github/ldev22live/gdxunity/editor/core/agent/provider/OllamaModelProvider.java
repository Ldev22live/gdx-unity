package io.github.ldev22live.gdxunity.editor.core.agent.provider;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Local LLM provider adapter connecting to Ollama's HTTP REST API (e.g. http://localhost:11434).
 * Uses standard java.net.http.HttpClient without third-party SDK dependencies.
 */
public class OllamaModelProvider implements ModelProvider {
    public static final String ID = "ollama";

    private final HttpClient httpClient;

    public OllamaModelProvider() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public OllamaModelProvider(HttpClient httpClient) {
        this.httpClient = (httpClient != null) ? httpClient : HttpClient.newHttpClient();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Ollama (Local LLM)";
    }

    @Override
    public ModelProviderCapabilities getCapabilities() {
        return ModelProviderCapabilities.ollama();
    }

    @Override
    public void validateConfiguration(ModelConfiguration config) throws ModelProviderException {
        if (config == null) {
            throw new ModelProviderException(ID, "Configuration cannot be null");
        }
        if (config.getEndpoint() == null || config.getEndpoint().isBlank()) {
            throw new ModelProviderException(ID, "Ollama endpoint URL is required (e.g. http://localhost:11434)");
        }
        if (config.getModelName() == null || config.getModelName().isBlank()) {
            throw new ModelProviderException(ID, "Ollama model name is required (e.g. llama3, mistral, qwen)");
        }
    }

    @Override
    public CompletableFuture<Boolean> testConnection(ModelConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String baseUrl = normalizeEndpoint(config.getEndpoint());
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<ModelResponse> generateAsync(ModelRequest request, ModelConfiguration config) {
        long startTime = System.currentTimeMillis();
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateConfiguration(config);
                String baseUrl = normalizeEndpoint(config.getEndpoint());

                // Build Ollama /api/generate JSON payload
                String payload = buildGeneratePayload(request, config);

                Duration timeout = (request.getTimeout() != null) ? request.getTimeout() : config.getTimeout();
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                long latencyMs = System.currentTimeMillis() - startTime;

                if (httpResponse.statusCode() == 404) {
                    return ModelResponse.failure("Ollama model '" + config.getModelName() + "' not found. Pull it manually with 'ollama pull " + config.getModelName() + "'.", ID, config.getModelName(), Duration.ofMillis(latencyMs));
                }
                if (httpResponse.statusCode() != 200) {
                    return ModelResponse.failure("Ollama error (" + httpResponse.statusCode() + "): " + httpResponse.body(), ID, config.getModelName(), Duration.ofMillis(latencyMs));
                }

                // Parse Ollama JSON response: {"response": "..."}
                JsonValue root = new JsonReader().parse(httpResponse.body());
                String responseText = "";
                if (root != null && root.has("response")) {
                    responseText = root.getString("response");
                }

                return ModelResponse.success(responseText, httpResponse.body(), ID, config.getModelName(), Duration.ofMillis(latencyMs));
            } catch (java.net.ConnectException e) {
                long latencyMs = System.currentTimeMillis() - startTime;
                return ModelResponse.failure("Ollama service unavailable at " + config.getEndpoint() + ". Is 'ollama serve' running?", ID, config.getModelName(), Duration.ofMillis(latencyMs));
            } catch (java.net.http.HttpTimeoutException e) {
                long latencyMs = System.currentTimeMillis() - startTime;
                return ModelResponse.failure("Ollama request timed out", ID, config.getModelName(), Duration.ofMillis(latencyMs));
            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - startTime;
                return ModelResponse.failure("Ollama request failed: " + e.getMessage(), ID, config.getModelName(), Duration.ofMillis(latencyMs));
            }
        });
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return ModelConfiguration.DEFAULT_OLLAMA_ENDPOINT;
        }
        String trimmed = endpoint.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String buildGeneratePayload(ModelRequest request, ModelConfiguration config) {
        StringWriter sw = new StringWriter();
        JsonWriter writer = new JsonWriter(sw);
        writer.setOutputType(JsonWriter.OutputType.json);

        try {
            writer.object();
            writer.set("model", config.getModelName());
            writer.set("stream", false);

            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                writer.set("system", request.getSystemPrompt());
            }

            StringBuilder promptBuilder = new StringBuilder();
            if (!request.getHistory().isEmpty()) {
                for (ModelRequest.Message msg : request.getHistory()) {
                    promptBuilder.append(msg.role().name()).append(": ").append(msg.content()).append("\n");
                }
            }
            if (!request.getSceneContext().isEmpty()) {
                promptBuilder.append("SCENE CONTEXT: ").append(request.getSceneContext()).append("\n");
            }
            promptBuilder.append(request.getUserPrompt());
            writer.set("prompt", promptBuilder.toString());

            if (request.isJsonExpected()) {
                writer.set("format", "json");
            }

            writer.pop();
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct Ollama JSON request", e);
        }
    }
}
