package io.github.ldev22live.gdxunity.engine.scene.play;

import io.github.ldev22live.gdxunity.engine.scene.Component;

/**
 * A component that runs the author's own logic on its entity: a Java or Kotlin script binding, or a graph (TD-088). A genre profile
 * does not add its movement to a player that has one, unless the player's {@link PlayerComponent#isUseProfileMovement()} is on,
 * because what the author made wins.
 */
public interface EntityLogicComponent extends Component {
    /**
     * What the Profiler calls this logic (roadmap M9.6), such as "Script PlayerController" or "Graph Hopper.graph": the time of all
     * components with the same label adds up in one section, so each script and graph shows on its own line.
     */
    String getProfilerLabel();
}
