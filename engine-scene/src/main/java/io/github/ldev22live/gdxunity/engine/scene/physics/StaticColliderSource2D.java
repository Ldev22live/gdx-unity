package io.github.ldev22live.gdxunity.engine.scene.physics;

import io.github.ldev22live.gdxunity.engine.scene.Component;

import java.util.List;

/**
 * A component that is a set of fixed boxes for the 2D physics (a tile map's solid tiles, TD-066). {@link PhysicsSystem2D} makes one static body for the
 * entity and one box fixture for each box returned here when the simulation starts; the boxes do not move afterwards.
 */
public interface StaticColliderSource2D extends Component {
    /**
     * A box in scene units.
     *
     * @param centerX world x of the center
     * @param centerY world y of the center
     * @param width   width, above 0
     * @param height  height, above 0
     */
    record StaticBox2D(float centerX, float centerY, float width, float height) { }

    /** The boxes in world coordinates, as they are now; empty for none. */
    List<StaticBox2D> getStaticBoxes();
}
