package io.github.ldev22live.gdxunity.editor.core.agent.provider;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Deterministic mock model provider for offline testing and development.
 * Can respond with pre-configured plans, custom scripted responses, or default entity generation plans.
 */
public class MockModelProvider implements ModelProvider {
    public static final String ID = "mock";

    private final Map<String, String> scriptedResponses = new LinkedHashMap<>();
    private String defaultPlanJson;
    private Duration simulatedDelay = Duration.ofMillis(10);
    private boolean simulateFailure = false;
    private String failureMessage = "Simulated mock provider failure";

    public MockModelProvider() {
        this.defaultPlanJson = """
        {
          "version": 1,
          "summary": "Mock Created Entity",
          "steps": [
            {
              "stepId": "mock_create",
              "tool": "entity_create",
              "parameters": {
                "name": "MockEntity"
              }
            }
          ]
        }
        """;
    }

    public void setSimulatedDelay(Duration delay) {
        this.simulatedDelay = (delay != null) ? delay : Duration.ZERO;
    }

    public void setSimulateFailure(boolean simulateFailure, String failureMessage) {
        this.simulateFailure = simulateFailure;
        this.failureMessage = failureMessage;
    }

    public void setScriptedResponse(String promptSubstring, String response) {
        scriptedResponses.put(promptSubstring, response);
    }

    public void setDefaultPlanJson(String defaultPlanJson) {
        this.defaultPlanJson = defaultPlanJson;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Mock Provider (Offline)";
    }

    @Override
    public ModelProviderCapabilities getCapabilities() {
        return ModelProviderCapabilities.mock();
    }

    @Override
    public void validateConfiguration(ModelConfiguration config) throws ModelProviderException {
        if (config == null) {
            throw new ModelProviderException(ID, "Configuration is null");
        }
    }

    @Override
    public CompletableFuture<Boolean> testConnection(ModelConfiguration config) {
        return CompletableFuture.completedFuture(!simulateFailure);
    }

    @Override
    public CompletableFuture<ModelResponse> generateAsync(ModelRequest request, ModelConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            if (simulatedDelay.toMillis() > 0) {
                try {
                    Thread.sleep(simulatedDelay.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ModelResponse.failure("Request interrupted", ID, config.getModelName(), Duration.ZERO);
                }
            }

            if (simulateFailure) {
                return ModelResponse.failure(failureMessage, ID, config.getModelName(), simulatedDelay);
            }

            String userPrompt = request.getUserPrompt();
            String matchedResponse = null;

            for (Map.Entry<String, String> entry : scriptedResponses.entrySet()) {
                if (userPrompt.toLowerCase().contains(entry.getKey().toLowerCase())) {
                    matchedResponse = entry.getValue();
                    break;
                }
            }

            if (matchedResponse == null) {
                matchedResponse = defaultPlanJson;
            }

            return ModelResponse.success(matchedResponse, matchedResponse, ID, config.getModelName(), simulatedDelay);
        });
    }
}
