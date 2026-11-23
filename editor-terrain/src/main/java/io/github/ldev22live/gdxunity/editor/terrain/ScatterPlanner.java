package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Where one dab of the Scatter tool puts objects (roadmap M3.6, TD-097), worked out without touching the scene. Everything is in the
 * terrain's own space. A dab fills the brush's circle up to the density: copies of the same object already inside count, so going over
 * the same ground again tops it up rather than piling objects up. Each candidate point is random; it is refused off the terrain, in a
 * hole, under water, outside the height range, on ground steeper than the slope limit, or nearer another object than the spacing (or a
 * keep-out circle, such as a building's footprint).
 */
public final class ScatterPlanner {
    /** Random points tried per object wanted, so a dab with many refusals still fills what it can. */
    static final int ATTEMPTS_PER_OBJECT = 8;

    /** Why a candidate point got no object. */
    public enum Refusal { OFF_TERRAIN, HOLE, WATER, HEIGHT, SLOPE, CROWDED }

    /**
     * Something already on the ground, in the terrain's own space.
     *
     * @param keepOut how far around it nothing may be scattered, on top of the spacing: 0 for an object, a building's footprint for one
     * @param same    whether it is a copy of the object being scattered, so it counts towards the density
     */
    public record Obstacle(float x, float z, float keepOut, boolean same) { }

    /** One object to place, in the terrain's own space, standing on the ground. */
    public record Placement(float x, float y, float z, float yawDegrees, float scale) { }

    /** A dab's placements, and how many candidate points were refused for each reason. */
    public record Plan(List<Placement> placements, Map<Refusal, Integer> refused) { }

    private ScatterPlanner() { }

    /**
     * @param centreX   the brush centre on the terrain
     * @param radius    the brush radius, in world units; must be positive
     * @param obstacles what already stands on the ground
     * @param random    where the points, turns and scales come from; seeded by the caller, so a dab can be repeated
     */
    public static Plan plan(Terrain terrain, float centreX, float centreZ, float radius, ScatterSettings settings,
                            List<Obstacle> obstacles, Random random) {
        Objects.requireNonNull(terrain, "terrain cannot be null");
        Objects.requireNonNull(settings, "settings cannot be null");
        Objects.requireNonNull(obstacles, "obstacles cannot be null");
        Objects.requireNonNull(random, "random cannot be null");
        if (!(radius > 0f)) {
            throw new IllegalArgumentException("Scatter radius must be positive: " + radius);
        }
        Map<Refusal, Integer> refused = new EnumMap<>(Refusal.class);
        int wanted = Math.round(settings.getDensity() * MathUtils.PI * radius * radius / ScatterSettings.DENSITY_AREA)
            - countSame(obstacles, centreX, centreZ, radius);
        if (wanted <= 0) {
            return new Plan(List.of(), refused);
        }
        List<Placement> placements = new ArrayList<>();
        float slopeLimitY = MathUtils.cosDeg(settings.getMaxSlopeDegrees());
        Vector3 normal = new Vector3();
        for (int attempt = 0; attempt < wanted * ATTEMPTS_PER_OBJECT && placements.size() < wanted; attempt++) {
            // Uniform over the circle: the square root keeps the centre from crowding
            float distance = radius * (float) Math.sqrt(random.nextFloat());
            float angle = random.nextFloat() * MathUtils.PI2;
            float x = centreX + distance * MathUtils.cos(angle);
            float z = centreZ + distance * MathUtils.sin(angle);
            float yaw = random.nextFloat() * 360f;
            float scale = settings.getMinScale() + random.nextFloat() * (settings.getMaxScale() - settings.getMinScale());
            Refusal refusal = refusal(terrain, x, z, settings, slopeLimitY, normal, obstacles, placements);
            if (refusal != null) {
                refused.merge(refusal, 1, Integer::sum);
                continue;
            }
            placements.add(new Placement(x, terrain.heightAt(x, z), z, yaw, scale));
        }
        return new Plan(Collections.unmodifiableList(placements), refused);
    }

    private static int countSame(List<Obstacle> obstacles, float centreX, float centreZ, float radius) {
        int count = 0;
        for (Obstacle obstacle : obstacles) {
            float dx = obstacle.x() - centreX;
            float dz = obstacle.z() - centreZ;
            if (obstacle.same() && dx * dx + dz * dz <= radius * radius) {
                count++;
            }
        }
        return count;
    }

    private static Refusal refusal(Terrain terrain, float x, float z, ScatterSettings settings, float slopeLimitY, Vector3 normal,
                                   List<Obstacle> obstacles, List<Placement> placed) {
        float height = terrain.heightAt(x, z);
        if (Float.isNaN(height)) {
            return Refusal.OFF_TERRAIN;
        }
        if (terrain.isHoleAt(x, z)) {
            return Refusal.HOLE;
        }
        if (terrain.hasWater() && height < terrain.getWaterLevel()) {
            return Refusal.WATER;
        }
        if (height < settings.getMinHeight() || height > settings.getMaxHeight()) {
            return Refusal.HEIGHT;
        }
        int ix = Math.round((x + terrain.getSizeX() / 2f) / terrain.getCellSize());
        int iz = Math.round((z + terrain.getSizeZ() / 2f) / terrain.getCellSize());
        // A small tolerance, so a limit of exactly a slope's angle keeps that slope
        if (terrain.normalAt(ix, iz, normal).y < slopeLimitY - 1e-4f) {
            return Refusal.SLOPE;
        }
        float spacing = settings.getSpacing();
        for (Obstacle obstacle : obstacles) {
            if (within(x, z, obstacle.x(), obstacle.z(), obstacle.keepOut() + spacing)) {
                return Refusal.CROWDED;
            }
        }
        for (Placement placement : placed) {
            if (within(x, z, placement.x(), placement.z(), spacing)) {
                return Refusal.CROWDED;
            }
        }
        return null;
    }

    private static boolean within(float x, float z, float otherX, float otherZ, float distance) {
        float dx = x - otherX;
        float dz = z - otherZ;
        return dx * dx + dz * dz < distance * distance;
    }
}
