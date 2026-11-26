package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

/** Builds the desktop credits screen from the project manifest that travels with a game. */
final class AssetCredits {
    private AssetCredits() {
    }

    static Scene from(AssetSource source) {
        var file = source.resolve(new AssetReference(ThirdPartyManifest.PATH));
        if (!file.exists()) {
            return null;
        }
        var entries = ThirdPartyManifest.parse(file.readString("UTF-8")).entries().stream()
            .filter(entry -> "CC-BY-3.0".equals(entry.licence())).toList();
        if (entries.isEmpty()) {
            return null;
        }
        var scene = new Scene("Asset Credits");
        var credits = new StringBuilder("Asset Credits\nF1 or Escape to return\n\n");
        for (var entry : entries) {
            credits.append(entry.name()).append(" by ").append(String.join(", ", entry.authors()))
                .append("\n").append(entry.source()).append("\n")
                .append(entry.licence()).append(" | https://creativecommons.org/licenses/by/3.0/\n")
                .append("Changes: ").append(entry.conversion()).append("\n\n");
        }
        var text = new TextComponent(credits.toString());
        text.setScale(1f);
        scene.createEntity("Third-party attribution").addComponent(text);
        return scene;
    }
}
