package io.github.ldev22live.gdxunity.editor.core.asset.library;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteSourcesTest {
    @Test
    void shouldOfferForImportOnlyTheKenney2DPacksTheOwnerApproved() {
        Set<String> offered = KenneyPacks.ALL.stream().map(CuratedPack::id).collect(Collectors.toSet());

        for (SpriteSources.Source source : SpriteSources.ALL) {
            if (!source.provider().equals("kenney")) {
                continue;
            }
            assertEquals(source.importable(), offered.contains(source.id()),
                source.name() + ": offered for import exactly when the owner approved it (the Legal gate)");
            if (source.importable()) {
                assertEquals(source.page(), KenneyPacks.ALL.stream().filter(p -> p.id().equals(source.id())).findFirst().orElseThrow().page());
            }
        }
        assertTrue(SpriteSources.find("kenney", "pixel-platformer").orElseThrow().importable(), "approved on 2026-11-19");
        assertFalse(SpriteSources.find("kenney", "new-platformer-pack").orElseThrow().importable(), "named by B1, not yet approved");
    }

    @Test
    void shouldCheckEveryOpenGameArtListingOnItsOwnAndAwaitTheOwner() {
        long listings = SpriteSources.ALL.stream().filter(s -> s.provider().equals("opengameart")).peek(source -> {
            assertEquals(SpriteSources.LicenceCheck.PER_LISTING, source.check(), source.name());
            assertEquals(SpriteSources.Approval.AWAITING_OWNER, source.approval(), source.name());
            assertFalse(source.importable(), source.name());
            assertTrue(source.page().startsWith("https://opengameart.org/content/"), source.page());
        }).count();
        assertEquals(4, listings, "Appendix B1 names four OpenGameArt listings");
    }

    @Test
    void shouldNeverTreatACatalogAsASourceEvenIfApproved() {
        SpriteSources.Source itch = SpriteSources.find("itch", "free-cc0-2d-catalog").orElseThrow();
        SpriteSources.Source approvedCatalog = new SpriteSources.Source(itch.provider(), itch.id(), itch.name(), itch.use(), itch.page(),
            itch.check(), SpriteSources.Approval.APPROVED);

        assertEquals(SpriteSources.LicenceCheck.DISCOVERY_ONLY, itch.check());
        assertFalse(itch.importable());
        assertFalse(approvedCatalog.importable(), "a catalog's finds are checked as their own sources");
    }

    @Test
    void shouldListEveryAppendixB1SourceOnce() {
        assertEquals(SpriteSources.ALL.size(), SpriteSources.ALL.stream().map(s -> s.provider() + "/" + s.id()).distinct().count());
        assertEquals(Set.of("kenney/new-platformer-pack", "kenney/pixel-platformer", "kenney/platformer-pack-remastered",
                "kenney/brick-pack", "opengameart/2d-sunny-grassland-sprites-platformer-sidescroller",
                "opengameart/dark-knight-2d-character-sprites", "opengameart/dungeon-pack", "opengameart/2d-world-sprites",
                "itch/free-cc0-2d-catalog"),
            SpriteSources.ALL.stream().filter(s -> !Set.of("ui-pack", "game-icons").contains(s.id()))
                .map(s -> s.provider() + "/" + s.id()).collect(Collectors.toSet()));
    }
}
