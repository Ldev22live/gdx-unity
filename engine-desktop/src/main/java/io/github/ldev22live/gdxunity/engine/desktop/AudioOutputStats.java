package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.audio.SfxPlayer;

/**
 * The sound output's latency and render cost over a run (roadmap M9.5), as {@link SfxAudioOutput#getStats()} reports them.
 *
 * @param latencyFrames    buffered latency from rendering a sample to the device's output, in frames at
 *                         {@link SfxPlayer#SAMPLE_RATE}; -1 when no device was open
 * @param blockMillis      one block's length: the budget for rendering it
 * @param blocks           blocks rendered
 * @param meanRenderMillis mean time spent rendering a block
 * @param maxRenderMillis  the longest a block took to render
 */
public record AudioOutputStats(int latencyFrames, double blockMillis, long blocks, double meanRenderMillis, double maxRenderMillis) {
    /** The latency in milliseconds, or NaN when no device was open. */
    public double latencyMillis() {
        return latencyFrames < 0 ? Double.NaN : latencyFrames * 1000.0 / SfxPlayer.SAMPLE_RATE;
    }
}
