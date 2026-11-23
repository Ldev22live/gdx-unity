package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The 2D sources of roadmap Appendix B1, each with the licence check it needs before anything of it may ship (roadmap M2b.5,
 * TD-099). TD-094 accepts CC0 only, and the chart puts every new source behind the owner's Legal gate: a source is
 * {@link Approval#APPROVED} only when the owner approved it (the CC0 shortlist of 2026-11-19), and only approved sources may be
 * offered for import ({@link KenneyPacks}). Kenney and OpenGameArt disallow automated agents in their {@code robots.txt}, so a person
 * downloads from them; ForgeRoom only opens the page.
 */
public final class SpriteSources {
    /** How a source's licence is checked. */
    public enum LicenceCheck {
        /**
         * The provider states every asset on its asset pages is CC0 ({@link KenneyPacks#LICENCE_EVIDENCE}), and the downloaded pack
         * must carry a licence file that says CC0 ({@link PackImporter} refuses it otherwise).
         */
        PROVIDER_STATEMENT_AND_LICENCE_FILE,
        /**
         * Each listing states its own licence, which must be CC0 alone; a collection is a list of links, not a licence, so every item
         * in it is checked as its own listing, and the creator and the listing's address are recorded.
         */
        PER_LISTING,
        /**
         * A catalog for finding art, never a source: whatever is found there is checked as its own source (the creator's pack and its
         * stated licence) and approved on its own.
         */
        DISCOVERY_ONLY
    }

    /** Whether the owner approved the source (the Legal gate). */
    public enum Approval {
        /** In the owner's approved shortlist (docs/asset-integration-report.md, 2026-11-19). */
        APPROVED,
        /** Named by Appendix B1 but not yet approved: nothing of it may be imported or shipped. */
        AWAITING_OWNER
    }

    /**
     * One 2D source.
     *
     * @param provider the provider's id, a lowercase word ("kenney", "opengameart", "itch")
     * @param id       the source's id at the provider, lowercase words joined by hyphens
     * @param name     its name as its page gives it
     * @param use      what it would be used for (Appendix B1)
     * @param page     its page
     * @param check    how its licence is checked
     * @param approval whether the owner approved it
     */
    public record Source(String provider, String id, String name, String use, String page, LicenceCheck check, Approval approval) {
        public Source {
            Objects.requireNonNull(provider, "provider cannot be null");
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(use, "use cannot be null");
            Objects.requireNonNull(page, "page cannot be null");
            Objects.requireNonNull(check, "check cannot be null");
            Objects.requireNonNull(approval, "approval cannot be null");
            if (!page.startsWith("https://")) {
                throw new IllegalArgumentException("A source's page must be https: " + page);
            }
        }

        /** True when a person may download it and the Asset Library may import it: approved, and a source rather than a catalog. */
        public boolean importable() {
            return approval == Approval.APPROVED && check != LicenceCheck.DISCOVERY_ONLY;
        }
    }

    /** Appendix B1's 2D sources, plus the 2D packs of the owner's approved shortlist, in that order. */
    public static final List<Source> ALL = List.of(
        kenney("new-platformer-pack", "New Platformer Pack", "2D platformer tiles, characters, interface", Approval.AWAITING_OWNER),
        kenney("pixel-platformer", "Pixel Platformer", "pixel-art platformer scene and tile painting", Approval.APPROVED),
        kenney("platformer-pack-remastered", "Platformer Pack Remastered", "alternative 2D tiles and characters",
            Approval.AWAITING_OWNER),
        kenney("brick-pack", "Brick Pack", "editable 2D building and tile assembly", Approval.AWAITING_OWNER),
        kenney("ui-pack", "UI Pack", "interface sprites", Approval.APPROVED),
        kenney("game-icons", "Game Icons", "icons", Approval.APPROVED),
        listing("2d-sunny-grassland-sprites-platformer-sidescroller", "Sunny Grassland Sprites",
            "trees, stones, platforms and scenery for a side-scroller"),
        listing("dark-knight-2d-character-sprites", "Dark Knight 2D Character Sprites", "an animated character: walk, jump, attack"),
        listing("dungeon-pack", "Dungeon Pack", "a top-down RPG: characters, enemies, tiles, props"),
        listing("2d-world-sprites", "2D World Sprites", "simple terrain and vegetation sprites for regression scenes"),
        new Source("itch", "free-cc0-2d-catalog", "itch.io free CC0 2D asset catalog", "finding more art styles",
            "https://itch.io/game-assets/free/tag-2d/tag-cc0", LicenceCheck.DISCOVERY_ONLY, Approval.AWAITING_OWNER));

    private SpriteSources() {
    }

    /** The source with this provider and id, if Appendix B1 or the shortlist names it. */
    public static Optional<Source> find(String provider, String id) {
        return ALL.stream().filter(source -> source.provider().equals(provider) && source.id().equals(id)).findFirst();
    }

    private static Source kenney(String id, String name, String use, Approval approval) {
        return new Source("kenney", id, name, use, "https://kenney.nl/assets/" + id, LicenceCheck.PROVIDER_STATEMENT_AND_LICENCE_FILE,
            approval);
    }

    private static Source listing(String id, String name, String use) {
        return new Source("opengameart", id, name, use, "https://opengameart.org/content/" + id, LicenceCheck.PER_LISTING,
            Approval.AWAITING_OWNER);
    }
}
