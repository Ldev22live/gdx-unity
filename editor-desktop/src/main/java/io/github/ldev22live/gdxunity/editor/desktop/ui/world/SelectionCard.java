package io.github.ldev22live.gdxunity.editor.desktop.ui.world;

import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.scenario.ScenarioEditor;
import io.github.ldev22live.gdxunity.editor.core.scenario.ScenarioSetup;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway;
import io.github.ldev22live.gdxunity.editor.desktop.ui.ActionTextButton;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The selection card over the map's bottom-right corner (TD-107 point 3): the selected entity's name, Move, Rotate and Duplicate,
 * and Customize's first offer for it ("Edit 3D Model of House", "Edit 2D Asset hero.sprite"). Hidden with nothing selected and
 * during Play. Each button runs the same registered action or offer as the rest of the editor, so undo and validation apply. When the
 * scene has players (TD-108, roadmap A04) an Owner list shows who owns the selection and reassigns it, as one undo step for every
 * selected entity.
 */
public class SelectionCard extends Table {
    /** Width of the card, in UI units. */
    public static final float WIDTH = 240f;
    private static final float BUTTON_HEIGHT = 24f;

    private final EditorState editorState;
    private final Function<Entity, List<CustomizeGateway.EditOffer>> offers;
    private final Consumer<CustomizeGateway.EditOffer> runOffer;
    private final Label name;
    private final TextButton offerButton;
    private final ScenarioEditor scenario;
    private final Table ownerRow;
    private final Cell<Table> ownerCell;
    private final SelectBox<PlayersPanel.OwnerChoice> ownerBox;
    private Entity shown;
    private Entity shownForOwner;
    private CustomizeGateway.EditOffer offer;
    /** The scenario text and owner the Owner list was last filled for (identity check: the text object changes with the scenario). */
    private String ownerScenario;
    private String ownerShown;
    private boolean ownerRefreshing;

    /**
     * @param offers   Customize's edit offers for an entity ({@link CustomizeGateway#editSelected})
     * @param runOffer runs an offer as the Customize gateway does
     * @param scenario the scene's players, for the Owner list
     */
    public SelectionCard(Skin skin, EditorState editorState, EditorActionRegistry actions,
                         Function<Entity, List<CustomizeGateway.EditOffer>> offers, Consumer<CustomizeGateway.EditOffer> runOffer,
                         ScenarioEditor scenario) {
        super(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.offers = Objects.requireNonNull(offers, "offers cannot be null");
        this.runOffer = Objects.requireNonNull(runOffer, "runOffer cannot be null");
        setBackground("card-bg");
        pad(8f);
        defaults().left();
        name = new Label("", skin);
        name.setEllipsis(true);
        add(name).width(WIDTH - 16f).colspan(3).padBottom(4).row();
        this.scenario = Objects.requireNonNull(scenario, "scenario cannot be null");
        ownerBox = new SelectBox<>(skin);
        ownerBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                PlayersPanel.OwnerChoice chosen = ownerBox.getSelected();
                if (ownerRefreshing || chosen == null) {
                    return;
                }
                SelectionCard.this.scenario.assignOwner(List.copyOf(editorState.getSelectedEntityIds()), chosen.playerId());
                ownerScenario = null; // refill: a refused change shows the owner it kept
            }
        });
        ownerRow = new Table(skin);
        ownerRow.add(new Label("Owner", skin, "muted")).padRight(6);
        ownerRow.add(ownerBox).width(WIDTH - 70f).height(BUTTON_HEIGHT);
        ownerCell = add(ownerRow).colspan(3).left().padBottom(4);
        row();
        add(new ActionTextButton(skin, actions, DefaultEditorActions.TOOL_MOVE, "Move", null)).height(BUTTON_HEIGHT).padRight(4);
        add(new ActionTextButton(skin, actions, DefaultEditorActions.TOOL_ROTATE, "Rotate", null)).height(BUTTON_HEIGHT).padRight(4);
        add(new ActionTextButton(skin, actions, DefaultEditorActions.EDIT_DUPLICATE, "Duplicate", null)).height(BUTTON_HEIGHT).row();
        offerButton = new TextButton("", skin);
        offerButton.getLabel().setEllipsis(true);
        // The label's cell is held to the card's width, so a long offer ("Edit 3D Model of Cabin (opens mesh sculpt)") ends in "..."
        offerButton.getLabelCell().width(WIDTH - 32f).minWidth(0f);
        offerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (offer != null && offer.isAvailable()) {
                    SelectionCard.this.runOffer.accept(offer);
                }
            }
        });
        add(offerButton).width(WIDTH - 16f).height(BUTTON_HEIGHT).colspan(3).padTop(4);
        setVisible(false);
    }

    @Override
    public void act(float delta) {
        Entity selected = editorState.isPlayModeActive() ? null : editorState.getSelectedEntity();
        setVisible(selected != null);
        if (selected != shown) {
            shown = selected;
            if (selected != null) {
                name.setText("Selected: " + selected.getName());
                List<CustomizeGateway.EditOffer> list = offers.apply(selected);
                offer = list.isEmpty() ? null : list.get(0);
                offerButton.setText(offer == null ? "Customize" : offer.label());
                offerButton.setDisabled(offer == null || !offer.isAvailable());
            }
        } else if (selected != null && !name.getText().toString().endsWith(selected.getName())) {
            name.setText("Selected: " + selected.getName());
        }
        if (selected != null) {
            refreshOwner(selected);
        }
        super.act(delta);
    }

    /** Fills the Owner list when the scenario or the selection's owner changed; hidden when the scene has no editable players. */
    private void refreshOwner(Entity selected) {
        String raw = editorState.getActiveScene() == null ? null : editorState.getActiveScene().getScenarioJson();
        OwnerComponent component = selected.getComponent(OwnerComponent.class);
        String owner = component == null ? null : component.getPlayerId();
        if (raw == ownerScenario && Objects.equals(owner, ownerShown) && selected == shownForOwner) {
            return;
        }
        ownerScenario = raw;
        ownerShown = owner;
        shownForOwner = selected;
        ScenarioSetup setup = scenario.current().orElse(null);
        boolean editable = setup != null && !setup.isReadOnly();
        if (ownerCell.hasActor() != editable) {
            ownerCell.setActor(editable ? ownerRow : null);
            ownerCell.padBottom(editable ? 4f : 0f);
            invalidateHierarchy();
        }
        if (!editable) {
            return;
        }
        List<PlayersPanel.OwnerChoice> choices = PlayersPanel.ownerChoices(setup);
        PlayersPanel.OwnerChoice current = null;
        for (PlayersPanel.OwnerChoice choice : choices) {
            if (Objects.equals(choice.playerId(), owner)) {
                current = choice;
            }
        }
        if (current == null) {
            // An owner the scenario does not define is kept as written (ScenarioValidator reports it), and shown by id
            current = new PlayersPanel.OwnerChoice(owner, "Unknown player (" + owner.substring(0, 8) + ")");
            choices.add(current);
        }
        ownerRefreshing = true;
        try {
            ownerBox.setItems(new Array<>(choices.toArray(new PlayersPanel.OwnerChoice[0])));
            ownerBox.setSelected(current);
        } finally {
            ownerRefreshing = false;
        }
    }
}
