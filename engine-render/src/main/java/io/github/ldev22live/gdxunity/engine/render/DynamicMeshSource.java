package io.github.ldev22live.gdxunity.engine.render;

/**
 * Finds the {@link DynamicMesh} a {@link io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent}
 * refers to. The editor supplies the sculpts it has open (live, while they change); a game runtime would load them
 * from files.
 */
@FunctionalInterface
public interface DynamicMeshSource {
    /**
     * @param path the component's project-relative mesh path
     * @return the mesh, or null if there is none (the component then draws nothing)
     */
    DynamicMesh find(String path);

    /** True to draw the edges of every dynamic mesh over its surface (a wireframe overlay); false by default. */
    default boolean isWireframe() {
        return false;
    }
}
