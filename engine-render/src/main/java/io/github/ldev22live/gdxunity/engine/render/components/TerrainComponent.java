package io.github.ldev22live.gdxunity.engine.render.components;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

/**
 * Ground drawn from a {@code .terrain} heightmap (TD-082). The terrain is centred on the entity: its x and z run symmetric around the
 * entity's position, its heights add to the entity's y, and the entity's rotation and scale apply. Several entities may name the same
 * file; they then share one {@code Terrain}, so a brush stroke shows on all of them.
 *
 * <p>The component holds only the path. The renderer reads the file through the project's terrain library; a file that is missing
 * or unreadable draws nothing.
 */
public class TerrainComponent extends BaseComponent {
    private String terrainPath = "";

    public TerrainComponent() { }

    public TerrainComponent(String terrainPath) {
        setTerrainPath(terrainPath);
    }

    /** The project-relative {@code .terrain} file, or an empty string for none. */
    public String getTerrainPath() {
        return terrainPath;
    }

    /** @param path a project-relative {@code .terrain} path; null or blank for none */
    public void setTerrainPath(String path) {
        this.terrainPath = path == null ? "" : path.trim();
    }
}
