package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.util.List;

/**
 * The Kenney packs the owner approved for the samples (docs/asset-integration-report.md, 2026-11-19), offered by the Asset Library
 * as a curated list (UI M4). Every Kenney asset page states "Creative Commons CC0", and kenney.nl/support says all assets on the
 * asset pages are CC0. Kenney has no documented API and its robots.txt disallows automated agents, so the editor only opens a pack's
 * page; a person downloads the archive and imports it ({@link PackImporter}).
 */
public final class KenneyPacks {
    /** Kenney's statement that its game assets are CC0. */
    public static final String LICENCE_EVIDENCE = "https://kenney.nl/support";
    private static final String AUTHOR = "Kenney";

    /** The approved packs, as listed in the asset integration report's shortlist. */
    public static final List<CuratedPack> ALL = List.of(
        pack("building-kit", "Building Kit", "buildings"),
        pack("nature-kit", "Nature Kit", "trees, rocks, plants"),
        pack("blocky-characters", "Blocky Characters", "animated characters"),
        pack("pixel-platformer", "Pixel Platformer", "2D tiles and characters"),
        pack("ui-pack", "UI Pack", "interface sprites"),
        pack("game-icons", "Game Icons", "icons"),
        pack("rpg-audio", "RPG Audio", "footsteps and foley"),
        pack("impact-sounds", "Impact Sounds", "hits and impacts"),
        pack("interface-sounds", "Interface Sounds", "interface sounds"));

    private KenneyPacks() {
    }

    private static CuratedPack pack(String id, String name, String category) {
        return new CuratedPack("kenney", id, name, category, "https://kenney.nl/assets/" + id, AUTHOR, LICENCE_EVIDENCE);
    }
}
