package io.github.ldev22live.gdxunity.editor.desktop.ui.world;

import io.github.ldev22live.gdxunity.editor.core.scenario.ScenarioSetup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The owner lists of the Players panel and the selection card (TD-108): Unowned first, players by display name, no fake semantics. */
class PlayersPanelTest {
    private static final String A = "11111111-1111-4111-8111-111111111111";
    private static final String B = "22222222-2222-4222-8222-222222222222";
    private static final String N = "33333333-3333-4333-8333-333333333333";

    @Test
    void shouldListUnownedFirstThenPlayersWithNeutralMarkedAndDuplicatesById() {
        ScenarioSetup setup = ScenarioSetup.parse("{\"version\":1,\"players\":["
            + "{\"id\":\"" + A + "\",\"name\":\"Blue\",\"color\":\"#0000ff\"},"
            + "{\"id\":\"" + B + "\",\"name\":\"BLUE\",\"color\":\"#0000aa\"},"
            + "{\"id\":\"" + N + "\",\"name\":\"Nature\",\"color\":\"#888888\",\"neutral\":true}],\"objectives\":[],\"rules\":[]}");

        List<PlayersPanel.OwnerChoice> choices = PlayersPanel.ownerChoices(setup);

        assertEquals(List.of("Unowned", "Blue (11111111)", "BLUE (22222222)", "Nature (neutral)"),
            choices.stream().map(PlayersPanel.OwnerChoice::toString).toList());
        assertNull(choices.get(0).playerId());
        assertEquals(N, choices.get(3).playerId());
    }

    @Test
    void shouldOfferOnlyUnownedForAReadOnlyOrMissingScenario() {
        assertEquals(List.of("Unowned"), PlayersPanel.ownerChoices(null).stream().map(PlayersPanel.OwnerChoice::label).toList());
        ScenarioSetup newer = ScenarioSetup.parse("{\"version\":9,\"players\":[]}");
        assertTrue(newer.isReadOnly());
        assertEquals(1, PlayersPanel.ownerChoices(newer).size());
    }

    @Test
    void shouldSayPlainlyThatPlayDoesNotUseOwnershipYet() {
        assertTrue(PlayersPanel.SEMANTICS_NOTE.contains("Play does not use it yet"));
        assertTrue(PlayersPanel.SEMANTICS_NOTE.contains("no teams, diplomacy, economy or unit AI"));
    }
}
