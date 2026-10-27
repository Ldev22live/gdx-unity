package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanStep;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.PlanValidationDiagnostic;
import io.github.ldev22live.gdxunity.editor.core.agent.provider.ModelConfiguration;
import io.github.ldev22live.gdxunity.editor.core.agent.provider.ModelProvider;
import io.github.ldev22live.gdxunity.editor.core.agent.service.AssistantService;
import io.github.ldev22live.gdxunity.editor.core.agent.service.ConversationHistory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI Assistant docked panel. All provider work is asynchronous inside {@link AssistantService};
 * callbacks are marshalled to the LibGDX render thread via {@code Gdx.app.postRunnable}.
 * Scene mutation only happens when the user clicks "Approve & Execute" (render thread).
 */
public class AssistantPanel extends Table implements Disposable {
    private final AssistantService service;
    private final Skin skin;

    private final Label statusLabel;
    private final Table conversationTable;
    private final ScrollPane conversationScroll;
    private final Table planTable;
    private final TextArea inputArea;
    private final TextButton providerBtn;
    private final TextField modelField;
    private final TextField endpointField;
    private final TextButton sendBtn;
    private final TextButton stopBtn;
    private final TextButton approveBtn;
    private final TextButton discardBtn;

    private final AssistantService.StateListener listener;
    private boolean clearArmed = false;

    public AssistantPanel(AssistantService service, Skin skin) {
        super(skin);
        this.service = Objects.requireNonNull(service, "service cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        setBackground("panel-bg");
        top();

        // Header bar
        Table headerBar = new Table(skin);
        headerBar.setBackground("subheader-bg");
        headerBar.left();
        Label titleLabel = new Label("AI Assistant", skin);
        titleLabel.setColor(new Color(0.92f, 0.92f, 0.95f, 1f));
        headerBar.add(titleLabel).padLeft(8).left().expandX();
        add(headerBar).growX().height(26).colspan(2).row();

        // Provider row
        Table providerRow = new Table(skin);
        providerBtn = new TextButton("", skin);
        providerBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { cycleProvider(); }
        });
        modelField = new TextField(service.getActiveConfiguration().getModelName(), skin);
        modelField.setMessageText("model");
        endpointField = new TextField(ModelConfiguration.DEFAULT_OLLAMA_ENDPOINT, skin);
        endpointField.setMessageText("endpoint");
        TextButton applyBtn = new TextButton("Apply", skin);
        applyBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { applyConfiguration(); }
        });
        TextButton testBtn = new TextButton("Test", skin);
        testBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { testConnection(); }
        });
        providerRow.add(providerBtn).width(70).padRight(2);
        providerRow.add(modelField).growX().padRight(2);
        providerRow.add(applyBtn).padRight(2);
        providerRow.add(testBtn);
        add(providerRow).growX().pad(2).row();
        add(endpointField).growX().pad(2).row();

        statusLabel = new Label("", skin);
        statusLabel.setWrap(true);
        add(statusLabel).growX().pad(2).row();

        conversationTable = new Table(skin);
        conversationTable.top().left();
        conversationScroll = new ScrollPane(conversationTable, skin);
        conversationScroll.setFadeScrollBars(false);
        add(conversationScroll).grow().pad(2).minHeight(80).row();

        planTable = new Table(skin);
        planTable.top().left();
        ScrollPane planScroll = new ScrollPane(planTable, skin);
        planScroll.setFadeScrollBars(false);
        add(planScroll).growX().height(140).pad(2).row();

        Table approvalRow = new Table(skin);
        approveBtn = new TextButton("Approve & Execute", skin);
        approveBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { approve(); }
        });
        discardBtn = new TextButton("Discard Plan", skin);
        discardBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { service.cancelOrDiscard(); refresh(); }
        });
        approvalRow.add(approveBtn).growX().padRight(2);
        approvalRow.add(discardBtn).growX();
        add(approvalRow).growX().pad(2).row();

        inputArea = new TextArea("", skin);
        inputArea.setMessageText("Describe what to build...");
        add(inputArea).growX().height(60).pad(2).row();

        Table inputRow = new Table(skin);
        sendBtn = new TextButton("Send", skin);
        sendBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { send(); }
        });
        stopBtn = new TextButton("Stop", skin);
        stopBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { service.cancelOrDiscard(); refresh(); }
        });
        TextButton clearBtn = new TextButton("Clear", skin);
        clearBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { clearConversation(clearBtn); }
        });
        inputRow.add(sendBtn).growX().padRight(2);
        inputRow.add(stopBtn).growX().padRight(2);
        inputRow.add(clearBtn).growX();
        add(inputRow).growX().pad(2).row();

        listener = new AssistantService.StateListener() {
            @Override public void onStateChanged(AssistantService.State o, AssistantService.State n) { postRefresh(); }
            @Override public void onPlanEvaluated(AgentPlan plan, List<PlanValidationDiagnostic> d) { postRefresh(); }
            @Override public void onExecutionFinished(AgentPlanExecutionResult r) { postRefresh(); }
            @Override public void onError(String msg) { postRefresh(); }
        };
        service.addListener(listener);
        refresh();
    }

    private void postRefresh() {
        if (Gdx.app != null) {
            Gdx.app.postRunnable(this::refresh);
        }
    }

    private void cycleProvider() {
        List<ModelProvider> providers = service.getProviderRegistry().getRegisteredProviders();
        if (providers.isEmpty()) return;
        String current = service.getActiveConfiguration().getProviderId();
        int idx = 0;
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).getId().equals(current)) { idx = (i + 1) % providers.size(); break; }
        }
        String next = providers.get(idx).getId();
        if ("ollama".equals(next)) {
            modelField.setText(ModelConfiguration.DEFAULT_OLLAMA_MODEL);
        } else if ("mock".equals(next)) {
            modelField.setText(ModelConfiguration.DEFAULT_MOCK_MODEL);
        }
        providerBtn.setText(next);
        applyConfiguration();
    }

    private void applyConfiguration() {
        if (service.getState() == AssistantService.State.REQUESTING_PROVIDER
            || service.getState() == AssistantService.State.EXECUTING_PLAN) {
            return;
        }
        String providerId = providerBtn.getText().toString();
        if (providerId.isBlank()) providerId = service.getActiveConfiguration().getProviderId();
        ModelConfiguration cfg;
        if ("ollama".equals(providerId)) {
            cfg = ModelConfiguration.forOllama(endpointField.getText(), modelField.getText(), ModelConfiguration.DEFAULT_TIMEOUT);
        } else {
            cfg = new ModelConfiguration(providerId, modelField.getText().isBlank() ? ModelConfiguration.DEFAULT_MOCK_MODEL : modelField.getText());
        }
        service.setActiveConfiguration(cfg);
        refresh();
    }

    private void testConnection() {
        ModelConfiguration cfg = service.getActiveConfiguration();
        service.getProviderRegistry().getProvider(cfg.getProviderId()).ifPresent(p -> {
            statusLabel.setText("Testing connection to " + p.getDisplayName() + " at "
                + ("mock".equals(cfg.getProviderId()) ? "in-memory" : cfg.getEndpoint()) + "...");
            statusLabel.setColor(Color.LIGHT_GRAY);
            p.testConnection(cfg).whenComplete((ok, ex) -> {
                Runnable updateUi = () -> {
                    if (Boolean.TRUE.equals(ok)) {
                        statusLabel.setText("Connection OK: " + p.getDisplayName() + " (endpoint reachable, model: " + cfg.getModelName() + ")");
                        statusLabel.setColor(Color.GREEN);
                    } else {
                        String hint = "ollama".equals(cfg.getProviderId())
                            ? " [Service unavailable at " + cfg.getEndpoint() + ". Start with 'ollama serve' and pull model with 'ollama pull " + cfg.getModelName() + "']"
                            : "";
                        statusLabel.setText("Connection FAILED: " + p.getDisplayName() + hint);
                        statusLabel.setColor(Color.SALMON);
                    }
                };
                if (Gdx.app != null) {
                    Gdx.app.postRunnable(updateUi);
                } else {
                    updateUi.run();
                }
            });
        });
    }

    private void send() {
        String text = inputArea.getText();
        if (text == null || text.isBlank()) return;
        AssistantService.State s = service.getState();
        if (s == AssistantService.State.REQUESTING_PROVIDER || s == AssistantService.State.EXECUTING_PLAN) return;
        inputArea.setText("");
        service.submitPromptAsync(text.trim());
        refresh();
    }

    private void approve() {
        // Runs on the render thread (scene2d ChangeListener) -> safe to mutate the scene.
        if (service.getState() != AssistantService.State.PLAN_READY_FOR_REVIEW) return;
        service.approveAndExecute();
        refresh();
    }

    private void clearConversation(TextButton clearBtn) {
        if (!clearArmed) {
            clearArmed = true;
            clearBtn.setText("Confirm?");
            return;
        }
        clearArmed = false;
        clearBtn.setText("Clear");
        service.getConversationHistory().clear();
        refresh();
    }

    public void refresh() {
        AssistantService.State state = service.getState();
        ModelConfiguration cfg = service.getActiveConfiguration();
        providerBtn.setText(cfg.getProviderId());

        String status = switch (state) {
            case IDLE -> "Idle";
            case REQUESTING_PROVIDER -> "Waiting for model response...";
            case PLAN_READY_FOR_REVIEW -> "Plan parsed & validated - APPROVAL PENDING (no changes made yet)";
            case PLAN_INVALID -> "Plan REJECTED by validator - cannot execute";
            case EXECUTING_PLAN -> "Executing plan...";
            case EXECUTION_COMPLETED -> "Plan COMPLETED (Ctrl+Z undoes it in one step)";
            case ERROR -> "Error: " + describeError();
        };
        statusLabel.setText("[" + cfg.getProviderId() + "/" + cfg.getModelName() + "] " + status
            + ("mock".equals(cfg.getProviderId()) ? "" : "\nScene summary (names, IDs, positions) is sent to this provider."));
        Color errColor = skin.has("error", Color.class) ? skin.getColor("error") : Color.SALMON;
        Color warnColor = skin.has("warning", Color.class) ? skin.getColor("warning") : Color.GOLD;
        Color successColor = skin.has("success", Color.class) ? skin.getColor("success") : Color.GREEN;
        Color mutedColor = skin.has("text-muted", Color.class) ? skin.getColor("text-muted") : Color.LIGHT_GRAY;

        statusLabel.setColor(switch (state) {
            case ERROR, PLAN_INVALID -> errColor;
            case PLAN_READY_FOR_REVIEW -> warnColor;
            case EXECUTION_COMPLETED -> successColor;
            default -> mutedColor;
        });

        boolean busy = state == AssistantService.State.REQUESTING_PROVIDER || state == AssistantService.State.EXECUTING_PLAN;
        sendBtn.setDisabled(busy);
        stopBtn.setDisabled(state != AssistantService.State.REQUESTING_PROVIDER);
        approveBtn.setDisabled(state != AssistantService.State.PLAN_READY_FOR_REVIEW);
        approveBtn.setVisible(state == AssistantService.State.PLAN_READY_FOR_REVIEW);
        discardBtn.setDisabled(state != AssistantService.State.PLAN_READY_FOR_REVIEW && state != AssistantService.State.PLAN_INVALID);

        rebuildConversation();
        rebuildPlan(state);
    }

    private String describeError() {
        AgentPlanExecutionResult r = service.getLastExecutionResult();
        if (r != null && !r.isSuccess()) {
            if (r.getStatus() == AgentPlanExecutionResult.Status.EXECUTION_FAILED_ROLLBACK_ERROR) {
                return "execution failed AND rollback failed - scene may be partially modified: " + r.getMessage();
            }
            return "execution failed, changes rolled back: " + r.getMessage();
        }
        return service.getLastErrorMessage() != null ? service.getLastErrorMessage() : "unknown";
    }

    private void rebuildConversation() {
        conversationTable.clearChildren();
        for (ConversationHistory.Entry e : service.getConversationHistory().getEntries()) {
            Label l = new Label(prefix(e.role()) + e.text(), skin);
            l.setWrap(true);
            Color userColor = skin.has("text-primary", Color.class) ? skin.getColor("text-primary") : Color.WHITE;
            Color assistantColor = skin.has("accent", Color.class) ? skin.getColor("accent") : new Color(0.6f, 0.85f, 1f, 1f);
            Color systemColor = skin.has("text-muted", Color.class) ? skin.getColor("text-muted") : Color.LIGHT_GRAY;

            l.setColor(switch (e.role()) {
                case USER -> userColor;
                case ASSISTANT -> assistantColor;
                case SYSTEM -> systemColor;
            });
            conversationTable.add(l).growX().left().pad(1).row();
        }
        conversationScroll.layout();
        conversationScroll.setScrollPercentY(1f);
    }

    private static String prefix(ConversationHistory.Role role) {
        return switch (role) {
            case USER -> "You: ";
            case ASSISTANT -> "Assistant (proposal): ";
            case SYSTEM -> "* ";
        };
    }

    private void rebuildPlan(AssistantService.State state) {
        planTable.clearChildren();
        AgentPlan plan = service.getCurrentProposedPlan();
        if (plan == null) {
            planTable.add(new Label("No plan proposed.", skin)).left().row();
            return;
        }
        Label header = new Label("Proposed plan: " + plan.getSummary() + " (" + plan.getSteps().size() + " step(s))", skin);
        header.setWrap(true);
        planTable.add(header).growX().left().row();

        boolean destructive = false;
        int i = 1;
        for (AgentPlanStep step : plan.getSteps()) {
            boolean isDelete = "entity_delete".equals(step.getTool());
            destructive |= isDelete;
            StringBuilder sb = new StringBuilder();
            sb.append(i++).append(". ").append(step.getTool()).append(" [").append(step.getStepId()).append("] ");
            for (Map.Entry<String, Object> p : step.getParameters().entrySet()) {
                sb.append(p.getKey()).append('=').append(describeParam(p.getValue())).append(' ');
            }
            Label l = new Label(sb.toString(), skin);
            l.setWrap(true);
            if (isDelete) l.setColor(Color.SALMON);
            planTable.add(l).growX().left().row();
        }
        if (destructive) {
            Label warn = new Label("WARNING: this plan DELETES entities (highlighted).", skin);
            warn.setColor(Color.ORANGE);
            planTable.add(warn).growX().left().row();
        }
        for (PlanValidationDiagnostic d : service.getCurrentDiagnostics()) {
            Label l = new Label(d.getSeverity() + " " + d.getCode() + (d.getStepId() != null ? " @" + d.getStepId() : "") + ": " + d.getMessage(), skin);
            l.setWrap(true);
            l.setColor(d.getSeverity() == PlanValidationDiagnostic.Severity.ERROR ? Color.SALMON : Color.GOLD);
            planTable.add(l).growX().left().row();
        }
        AgentPlanExecutionResult r = service.getLastExecutionResult();
        if (r != null) {
            Label l = new Label("Execution: " + r.getStatus() + " - " + r.getMessage(), skin);
            l.setWrap(true);
            l.setColor(r.isSuccess() ? Color.GREEN : Color.SALMON);
            planTable.add(l).growX().left().row();
        }
    }

    private static String describeParam(Object v) {
        String s = String.valueOf(v);
        if (s.startsWith("$ref:")) return "<" + s.substring(5) + ">";
        if (s.matches("[0-9a-fA-F-]{36}")) return s.substring(0, 8) + "…";
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    @Override
    public void dispose() {
        service.removeListener(listener);
    }
}
