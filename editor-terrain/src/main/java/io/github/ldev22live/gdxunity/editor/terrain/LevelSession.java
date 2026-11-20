package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.CompoundCommand;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.command.DeleteEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabDocument;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainPathComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainBrush;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainDelta;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * The level editor's state and operations (TD-083): the tool in hand, the ground being shaped, the objects that can be placed, and
 * the edits that are not brush strokes (a new level, a new base surface, placing and erasing objects). The look and feel of the
 * original campaign editors is the model: pick a ground, shape it, paint other ground on it, drop trees and rocks.
 *
 * <p>The level's ground is the first entity with a Terrain component, so the viewport knows where strokes go even after placing an
 * object has changed the selection. Everything that changes the scene is one undo step.
 */
public final class LevelSession {
    /** The level editor's tools. */
    public enum Tool {
        RAISE("Raise"), LOWER("Lower"), SMOOTH("Smooth"), FLATTEN("Flatten"), NOISE("Rough"), PAINT("Ground"),
        /** Cuts holes in the ground; Ctrl fills them (roadmap M3.4, TD-089). */
        HOLE("Hole"),
        /** Wears steep ground into slopes (roadmap M3.5). */
        ERODE("Erode"),
        /** Drag from one point to another: an even ramp between their heights, as wide as the brush (roadmap M3.5). */
        RAMP("Ramp"),
        /**
         * Each click adds a point to the selected Terrain Path (a new one when none is selected); Apply carves the road, river or cliff
         * along it (roadmap M3.7).
         */
        PATH("Path"),
        /** Drops the chosen object where the mouse is clicked. */
        PLACE("Place"),
        /** Removes the placed object nearest the click. */
        ERASE("Erase");

        private final String label;

        Tool(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** True for the tools that are brush strokes on the terrain. */
        public boolean isBrush() {
            return this != PLACE && this != ERASE && this != PATH;
        }

        /** True for the tools that shape the ground: the brushes and the Path tool (roadmap M3.7). */
        public boolean isGround() {
            return isBrush() || this == PATH;
        }
    }

    /**
     * Which of the level editor's tools are offered (roadmap M1b.2 and M1b.4): the World strip's Terrain shows the ground tools, its
     * Objects the place and erase tools, its Characters only Place (roadmap M3c.2), and the Level button all of them. Unrelated tools
     * are then not shown together.
     */
    public enum Focus {
        ALL("Level"), TERRAIN("Terrain"), OBJECTS("Objects"), CHARACTERS("Characters"),
        /** The World strip's Buildings drawer (UI M3): place and erase prefabs from {@link #BUILDING_FOLDER}. */
        BUILDINGS("Buildings");

        private final String label;

        Focus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** Whether {@code tool} belongs to this focus. */
        public boolean offers(Tool tool) {
            return switch (this) {
                case ALL -> true;
                case TERRAIN -> tool.isGround();
                case OBJECTS, BUILDINGS -> tool == Tool.PLACE || tool == Tool.ERASE;
                case CHARACTERS -> tool == Tool.PLACE;
            };
        }

        /** The tool chosen when this focus is entered with a tool it does not offer in hand. */
        Tool firstTool() {
            return this == OBJECTS || this == CHARACTERS || this == BUILDINGS ? Tool.PLACE : Tool.RAISE;
        }
    }

    /** Folder (project relative) whose prefabs are the level editor's objects. */
    public static final String OBJECT_FOLDER = StarterPalette.LEVEL_FOLDER;
    /** Folder (project relative) whose prefabs the World strip's Characters places: the player and the spawn point (roadmap M3c.2). */
    public static final String CHARACTER_FOLDER = StarterPalette.CHARACTER_FOLDER;
    /** Folder (project relative) whose prefabs are the World strip's Buildings (UI M3). */
    public static final String BUILDING_FOLDER = StarterPalette.BUILDING_FOLDER;
    /** A placed character's capsule starts this far above the ground, so it is not placed touching it. */
    private static final float CHARACTER_CLEARANCE = 1f;
    private static final String CHARACTER_TYPE = "CharacterController3DComponent";
    private static final String PLAYER_TYPE = "PlayerComponent";
    private static final String SPAWN_TYPE = "SpawnPointComponent";
    /** Each placed object is scaled by 1 plus or minus up to this, so a forest does not look copied. */
    public static final float SCALE_JITTER = 0.2f;
    /** How far from the click, in world units, the erase tool looks for an object. */
    public static final float ERASE_RADIUS = 160f;

    private final EditorState editorState;
    private final TerrainSession terrainSession;
    private final File assetRoot;
    private final Random random = new Random(1234);
    private boolean active;
    private Tool tool = Tool.RAISE;
    private Focus focus = Focus.ALL;
    private String objectPath = "";

    public LevelSession(EditorState editorState, TerrainSession terrainSession, File assetRoot) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.terrainSession = Objects.requireNonNull(terrainSession, "terrainSession cannot be null");
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
    }

    public TerrainSession getTerrainSession() {
        return terrainSession;
    }

    /** The editor this level is edited in; the palette's Sky row runs its preset steps there (roadmap M3.9). */
    public EditorState getEditorState() {
        return editorState;
    }

    /** The project folder files are read from and written to. */
    public File getAssetRoot() {
        return assetRoot;
    }

    /** True while the level editor is on: the palette shows and the mouse works the tool in hand. */
    public boolean isActive() {
        return active;
    }

    /** Turns the level editor on or off; turning it off also returns the focus to {@link Focus#ALL}, the Level button's. */
    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            focus = Focus.ALL;
        }
        terrainSession.setLevelTarget(active ? this::findGround : null);
        applyTool();
    }

    public Focus getFocus() {
        return focus;
    }

    /**
     * Offers only the tools of {@code focus}; when the tool in hand is not one of them, the focus's first tool is taken (Raise for
     * Terrain, Place for Objects). Does not turn the level editor on.
     */
    public void setFocus(Focus focus) {
        this.focus = Objects.requireNonNull(focus, "focus cannot be null");
        if (!focus.offers(tool)) {
            tool = focus.firstTool();
        }
        if (!objectPath.isEmpty() && !listObjects().contains(objectPath)) {
            // A tree chosen under Objects is not a character, and the other way round
            objectPath = "";
        }
        applyTool();
    }

    public Tool getTool() {
        return tool;
    }

    /**
     * @throws IllegalArgumentException when the current focus does not offer {@code tool}
     */
    public void setTool(Tool tool) {
        Objects.requireNonNull(tool, "tool cannot be null");
        if (!focus.offers(tool)) {
            throw new IllegalArgumentException(tool + " is not offered in the " + focus.getLabel() + " focus");
        }
        this.tool = tool;
        applyTool();
    }

    /** The brush follows the tool, and terrain strokes are on only for the brush tools while the level editor is on. */
    private void applyTool() {
        TerrainBrush brush = terrainSession.getBrush();
        switch (tool) {
            case RAISE -> brush.setType(TerrainBrush.Type.RAISE);
            case LOWER -> brush.setType(TerrainBrush.Type.LOWER);
            case SMOOTH -> brush.setType(TerrainBrush.Type.SMOOTH);
            case FLATTEN -> brush.setType(TerrainBrush.Type.FLATTEN);
            case NOISE -> brush.setType(TerrainBrush.Type.NOISE);
            case PAINT -> brush.setType(TerrainBrush.Type.PAINT);
            case HOLE -> brush.setType(TerrainBrush.Type.HOLE);
            case ERODE -> brush.setType(TerrainBrush.Type.ERODE);
            case RAMP -> brush.setType(TerrainBrush.Type.RAMP);
            default -> { }
        }
        terrainSession.setPaintMode(active && tool.isBrush());
    }

    /** The ground the {@link Tool#PAINT} tool lays down. */
    public Surface getPaintSurface() {
        return terrainSession.getBrush().getSurface();
    }

    public void setPaintSurface(Surface surface) {
        terrainSession.getBrush().setSurface(Objects.requireNonNull(surface, "surface cannot be null"));
    }

    /** Project-relative path of the object that {@link Tool#PLACE} drops; empty when none is chosen. */
    public String getObjectPath() {
        return objectPath;
    }

    public void setObjectPath(String objectPath) {
        this.objectPath = objectPath == null ? "" : objectPath;
    }

    /**
     * The folder the palette lists for the current focus: {@link #CHARACTER_FOLDER} for Characters, {@link #BUILDING_FOLDER} for
     * Buildings, else {@link #OBJECT_FOLDER}.
     */
    public String getObjectFolder() {
        return switch (focus) {
            case CHARACTERS -> CHARACTER_FOLDER;
            case BUILDINGS -> BUILDING_FOLDER;
            default -> OBJECT_FOLDER;
        };
    }

    /** The prefabs in {@link #getObjectFolder()}, by name: what the palette offers to place. */
    public List<String> listObjects() {
        return listObjects(getObjectFolder());
    }

    /** The prefabs in a project folder, by name, as project-relative paths; empty when the folder has none. */
    public List<String> listObjects(String folderPath) {
        Objects.requireNonNull(folderPath, "folderPath cannot be null");
        File folder = new File(assetRoot, folderPath);
        String[] names = folder.list((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(PrefabService.EXTENSION));
        if (names == null) {
            return List.of();
        }
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        List<String> paths = new ArrayList<>();
        for (String name : names) {
            paths.add(folderPath + "/" + name);
        }
        return paths;
    }

    /** What to call an object in the palette: its file name without the extension. */
    public static String objectLabel(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.endsWith(PrefabService.EXTENSION) ? name.substring(0, name.length() - PrefabService.EXTENSION.length()) : name;
    }

    // ---- the ground -------------------------------------------------------------------------------------------------------

    /** The entity whose terrain is the level's ground: the first with a Terrain component that names a readable terrain; null if none. */
    public Entity findGround() {
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return null;
        }
        for (Entity entity : scene.getAllEntities()) {
            if (terrainSession.getTerrainOf(entity) != null) {
                return entity;
            }
        }
        return null;
    }

    /** The ground's terrain, or null. */
    public Terrain findTerrain() {
        return terrainSession.getTerrainOf(findGround());
    }

    /**
     * Starts a level: a flat ground of this surface, as a new terrain file and an entity named "Level Ground" that characters can walk
     * on (a Terrain Collider 3D, TD-087), in one undo step.
     *
     * @return the new entity, or null when it could not be made (the reason is in the status bar)
     */
    public Entity createLevel(Surface base) {
        Objects.requireNonNull(base, "base cannot be null");
        if (findGround() != null) {
            editorState.setStatusMessage("This scene already has a level ground; change its Ground instead", true);
            return null;
        }
        String path = TerrainAssets.createNew(assetRoot, terrainSession.getLibrary(), "Level", true, base);
        CreateEntityCommand command = new CreateEntityCommand(editorState, "Level Ground", null,
            List.<Component>of(new TerrainComponent(path), new TerrainCollider3DComponent()));
        if (!editorState.executeCommand(command)) {
            return null;
        }
        editorState.setStatusMessage("New level on " + base.getLabel().toLowerCase(Locale.ROOT) + ": " + path, false);
        return command.getCreatedEntity();
    }

    /**
     * Changes the surface of the whole ground, as one undo step; ground painted by hand changes too, as in the editors this follows,
     * where the base is the ground everything else is painted on.
     *
     * @return false when there is no ground or it already is this surface everywhere
     */
    public boolean setBaseSurface(Surface base) {
        Objects.requireNonNull(base, "base cannot be null");
        Entity ground = findGround();
        Terrain terrain = terrainSession.getTerrainOf(ground);
        if (terrain == null) {
            editorState.setStatusMessage("There is no ground yet: press New Level", true);
            return false;
        }
        byte[] current = terrain.copySurfaces();
        int[] indices = new int[current.length];
        byte[] before = new byte[current.length];
        byte[] after = new byte[current.length];
        int count = 0;
        for (int i = 0; i < current.length; i++) {
            if (current[i] != base.code()) {
                indices[count] = i;
                before[count] = current[i];
                after[count] = base.code();
                count++;
            }
        }
        if (count == 0) {
            return false;
        }
        TerrainDelta delta = new TerrainDelta(new int[0], new float[0], new float[0], Arrays.copyOf(indices, count),
            Arrays.copyOf(before, count), Arrays.copyOf(after, count));
        String path = TerrainSession.componentOf(ground).getTerrainPath();
        TerrainStrokeCommand command = new TerrainStrokeCommand(terrainSession.getLibrary(), path, terrain, delta, "Level: Ground " + base.getLabel());
        return editorState.executeCommand(command);
    }

    // ---- paths: roads, rivers and cliffs (roadmap M3.7) -------------------------------------------------------------------

    /** How far beside a path's edge its meshes stand, in world units. */
    public static final float PATH_MESH_MARGIN = 40f;

    /** The selected entity when it holds a Terrain Path, else null. */
    public Entity selectedPath() {
        Entity selected = editorState.getSelectedEntity();
        return selected != null && selected.getComponent(TerrainPathComponent.class) != null ? selected : null;
    }

    /**
     * The Path tool's click: adds a point (in the ground's own space) to the selected path, or starts a new road there when no path is
     * selected. One undo step either way.
     *
     * @return the path entity, or null when there is no ground (the status bar says so)
     */
    public Entity addPathPoint(float localX, float localZ) {
        if (findGround() == null) {
            editorState.setStatusMessage("There is no ground yet: press New Level", true);
            return null;
        }
        Entity path = selectedPath();
        if (path == null) {
            TerrainPathComponent component = new TerrainPathComponent();
            component.setPoints(List.of(new Vector2(localX, localZ)));
            CreateEntityCommand create = new CreateEntityCommand(editorState, "Road", null, List.<Component>of(component));
            if (!editorState.executeCommand(create)) {
                return null;
            }
            editorState.setSelectedEntity(create.getCreatedEntity());
            editorState.setStatusMessage("Path started: click more points along it, then Apply", false);
            return create.getCreatedEntity();
        }
        TerrainPathComponent component = path.getComponent(TerrainPathComponent.class);
        List<Vector2> before = component.getPoints();
        List<Vector2> after = component.getPoints();
        after.add(new Vector2(localX, localZ));
        if (!editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, path.getId(), component, before, after,
            component::setPoints, "Path: Add Point"))) {
            return null;
        }
        editorState.setStatusMessage(path.getName() + ": " + after.size() + " points" + (after.size() >= 2 ? "; Apply carves it" : ""), false);
        return path;
    }

    /** Lets the next Path click start a new path: the selection is cleared (nothing is changed or undone). */
    public void startNewPath() {
        editorState.clearSelection();
        editorState.setStatusMessage("Click the ground to start a new path", false);
    }

    /** Removes the selected path's last point, as one undo step; false when there is no point to remove. */
    public boolean removeLastPathPoint() {
        Entity path = selectedPath();
        if (path == null || path.getComponent(TerrainPathComponent.class).getPointCount() == 0) {
            return false;
        }
        TerrainPathComponent component = path.getComponent(TerrainPathComponent.class);
        List<Vector2> before = component.getPoints();
        List<Vector2> after = component.getPoints();
        after.remove(after.size() - 1);
        return editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, path.getId(), component, before, after,
            component::setPoints, "Path: Remove Point"));
    }

    /** Makes the selected path a road, river or cliff, as one undo step; the entity's name follows. */
    public boolean setPathKind(TerrainPath.Kind kind) {
        Objects.requireNonNull(kind, "kind cannot be null");
        Entity path = selectedPath();
        if (path == null) {
            editorState.setStatusMessage("Select a path first, or click the ground with the Path tool to start one", true);
            return false;
        }
        TerrainPathComponent component = path.getComponent(TerrainPathComponent.class);
        if (component.getKind() == kind) {
            return false;
        }
        return editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, path.getId(), component, component.getKind(),
            kind, component::setKind, "Path: " + kindLabel(kind)));
    }

    /** Sets the prefab placed beside the selected path (empty for none), as one undo step. */
    public boolean setPathPrefab(String prefabPath) {
        Entity path = selectedPath();
        if (path == null) {
            return false;
        }
        TerrainPathComponent component = path.getComponent(TerrainPathComponent.class);
        String chosen = prefabPath == null ? "" : prefabPath.trim();
        if (component.getPrefabPath().equals(chosen)) {
            return false;
        }
        return editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, path.getId(), component,
            component.getPrefabPath(), chosen, component::setPrefabPath, "Path: Beside " + (chosen.isEmpty() ? "nothing" : objectLabel(chosen))));
    }

    /**
     * Carves the selected path into the ground, paints it and places its meshes, as one undo step.
     *
     * @return false when there is no path with two points or no ground, or nothing changed (the status bar says which)
     */
    public boolean applyPath() {
        Entity path = selectedPath();
        if (path == null) {
            editorState.setStatusMessage("Select a path first, or click the ground with the Path tool to start one", true);
            return false;
        }
        EditorCommand command = applyPathCommand(path.getComponent(TerrainPathComponent.class), path.getName());
        if (command == null) {
            return false;
        }
        if (!editorState.executeCommand(command)) {
            return false;
        }
        editorState.setStatusMessage(command.getDescription(), false);
        return true;
    }

    /**
     * What Apply does for {@code path}, not yet run: the carve of the level ground and a placement of the path's prefab every
     * {@link TerrainPathComponent#getSpacing()} units, alternately on each side, set on the carved ground and turned along the path.
     * The ground shows no change until the command runs.
     *
     * @return the command, or null when the path has fewer than two points, there is no ground, or the carve changes nothing
     */
    public EditorCommand applyPathCommand(TerrainPathComponent path, String name) {
        Objects.requireNonNull(path, "path cannot be null");
        Entity ground = findGround();
        Terrain terrain = findTerrain();
        if (ground == null || terrain == null) {
            editorState.setStatusMessage("There is no ground yet: press New Level", true);
            return null;
        }
        List<Vector2> points = path.getPoints();
        if (points.size() < 2) {
            editorState.setStatusMessage(name + " needs at least two points: click the ground with the Path tool", true);
            return null;
        }
        float[] xs = new float[points.size()];
        float[] zs = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i).x;
            zs[i] = points.get(i).y;
        }
        TerrainDelta delta = TerrainPath.carve(terrain, xs, zs, path.getKind(), path.getWidth(), path.getDepth(), path.getPaint());
        if (delta.isEmpty()) {
            editorState.setStatusMessage(name + " changes nothing here", false);
            return null;
        }
        CompoundCommand command = new CompoundCommand("Path: Apply " + kindLabel(path.getKind()) + " " + name);
        // Meshes stand on the carved ground: read it before putting the terrain back for the command to redo
        List<Vector3> spots = new ArrayList<>();
        List<Float> turns = new ArrayList<>();
        if (!path.getPrefabPath().isEmpty() && editorState.getPrefabService() != null) {
            TerrainPath.Samples line = TerrainPath.sample(xs, zs, path.getSpacing());
            float offset = path.getWidth() / 2f + PATH_MESH_MARGIN;
            for (int i = 0; i < line.size(); i++) {
                int a = Math.max(0, i - 1);
                int b = Math.min(line.size() - 1, i + 1);
                float tx = line.x()[b] - line.x()[a];
                float tz = line.z()[b] - line.z()[a];
                float length = (float) Math.hypot(tx, tz);
                if (length <= 0f) {
                    continue;
                }
                float side = i % 2 == 0 ? 1f : -1f;
                float x = line.x()[i] + tz / length * offset * side;
                float z = line.z()[i] - tx / length * offset * side;
                float y = terrain.heightAt(x, z);
                if (Float.isNaN(y)) {
                    continue; // off the ground
                }
                spots.add(new Vector3(x, y, z).mul(ground.getTransform().getWorldMatrix()));
                turns.add((float) Math.toDegrees(Math.atan2(-tz, tx)));
            }
        }
        terrain.apply(delta, false);
        command.addCommand(new TerrainStrokeCommand(terrainSession.getLibrary(), TerrainSession.componentOf(ground).getTerrainPath(), terrain,
            delta, "Path: Carve " + kindLabel(path.getKind())));
        for (int i = 0; i < spots.size(); i++) {
            EditorCommand place = editorState.getPrefabService().instantiateCommand(new AssetReference(path.getPrefabPath()), spots.get(i),
                turns.get(i), 1f, new Entity[1]);
            if (place == null) {
                return null; // the prefab cannot be read; the status bar says why
            }
            command.addCommand(place);
        }
        return command;
    }

    private static String kindLabel(TerrainPath.Kind kind) {
        String name = kind.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    // ---- heightmaps (roadmap M3.5) ----------------------------------------------------------------------------------------

    /**
     * Writes the level ground's heights to {@code file} as a 16-bit PNG heightmap with their range ({@link Heightmaps#export}).
     *
     * @return false when there is no ground or the file cannot be written (the status bar says which)
     */
    public boolean exportHeightmap(File file) {
        Objects.requireNonNull(file, "file cannot be null");
        Terrain terrain = findTerrain();
        if (terrain == null) {
            editorState.setStatusMessage("There is no ground yet: press New Level", true);
            return false;
        }
        try {
            Files.write(file.toPath(), Heightmaps.export(terrain));
        } catch (IOException e) {
            editorState.setStatusMessage("Cannot write the heightmap " + file.getName() + ": " + e.getMessage(), true);
            return false;
        }
        editorState.setStatusMessage("Exported the heightmap " + file.getName() + " (" + terrain.getResolutionX() + " x "
            + terrain.getResolutionZ() + ", 16-bit)", false);
        return true;
    }

    /**
     * Puts the heights of a PNG heightmap on the level ground, resampled to its grid, as one undo step that writes the file.
     *
     * @return false when there is no ground, the image cannot be read, or nothing changed (the status bar says which)
     */
    public boolean importHeightmap(File file) {
        Objects.requireNonNull(file, "file cannot be null");
        Entity ground = findGround();
        Terrain terrain = findTerrain();
        if (ground == null || terrain == null) {
            editorState.setStatusMessage("There is no ground yet: press New Level", true);
            return false;
        }
        HeightmapPng.Heightmap map;
        try {
            map = HeightmapPng.read(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            editorState.setStatusMessage("Cannot import " + file.getName() + ": " + e.getMessage(), true);
            return false;
        }
        TerrainDelta delta = Heightmaps.importInto(terrain, map);
        if (delta.isEmpty()) {
            editorState.setStatusMessage(file.getName() + " gives the ground the heights it already has", false);
            return false;
        }
        String path = TerrainSession.componentOf(ground).getTerrainPath();
        TerrainStrokeCommand command = new TerrainStrokeCommand(terrainSession.getLibrary(), path, terrain, delta,
            "Terrain: Import Heightmap");
        if (!editorState.executeCommand(command)) {
            terrain.apply(delta, false);
            return false;
        }
        editorState.setStatusMessage("Imported the heightmap " + file.getName() + " (" + map.width() + " x " + map.height()
            + (map.hasRange() ? ", its own height range" : ", read as 0 to " + (int) Heightmaps.DEFAULT_RANGE + " units") + ")", false);
        return true;
    }

    // ---- water (roadmap M3.4, TD-089) -------------------------------------------------------------------------------------

    /** How far Up and Down move the water, in world units. */
    public static final float WATER_STEP = 20f;
    /** Where Add puts new water: this share of the way from the lowest to the highest ground, so it fills the low parts. */
    public static final float WATER_START_SHARE = 0.25f;

    /**
     * Sets the level ground's water to {@code level}, or removes it with NaN, as one undo step that writes the file.
     *
     * @return false when there is no ground or nothing would change (the status bar says which)
     */
    public boolean setWaterLevel(float level) {
        Entity ground = findGround();
        Terrain terrain = findTerrain();
        if (ground == null || terrain == null) {
            editorState.setStatusMessage("There is no ground yet: press New Level", true);
            return false;
        }
        if (Float.compare(terrain.getWaterLevel(), level) == 0) {
            return false;
        }
        String path = TerrainSession.componentOf(ground).getTerrainPath();
        TerrainWaterCommand command = new TerrainWaterCommand(terrainSession.getLibrary(), path, terrain, level);
        if (!editorState.executeCommand(command)) {
            return false;
        }
        editorState.setStatusMessage(command.getDescription(), false);
        return true;
    }

    /** Adds water over the low ground ({@link #WATER_START_SHARE} of the height range up), or does nothing when there is water. */
    public boolean addWater() {
        Terrain terrain = findTerrain();
        if (terrain != null && terrain.hasWater()) {
            return false;
        }
        if (terrain == null) {
            return setWaterLevel(0f); // reports that there is no ground
        }
        float[] range = terrain.heightRange();
        return setWaterLevel(range[0] + (range[1] - range[0]) * WATER_START_SHARE);
    }

    /** Moves the water up ({@code steps} > 0) or down by {@link #WATER_STEP} per step; false when there is no water. */
    public boolean moveWater(int steps) {
        Terrain terrain = findTerrain();
        if (terrain == null || !terrain.hasWater()) {
            editorState.setStatusMessage("There is no water: press Add first", true);
            return false;
        }
        return setWaterLevel(terrain.getWaterLevel() + steps * WATER_STEP);
    }

    // ---- downloaded models (UI M7) ----------------------------------------------------------------------------------------

    /**
     * Places a glTF model (a download from the Asset Library, UI M7) standing on the ground at world point {@code x}, {@code z}: a new
     * entity with a Model component, at the glTF's metres-to-units scale ({@link ModelComponent#DEFAULT_IMPORT_SCALE}), selected, in
     * one undo step. With no ground in the scene, or a point beyond its edge, it is placed at height {@code fallbackY} and the status
     * bar says so; it is never silently floated.
     *
     * @param modelPath the glTF or GLB, relative to the project
     * @return the new entity, or null when it could not be made (the reason is in the status bar)
     */
    public Entity placeModelOnGround(String modelPath, float x, float z, float fallbackY) {
        Objects.requireNonNull(modelPath, "modelPath cannot be null");
        String lower = modelPath.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".gltf") && !lower.endsWith(".glb")) {
            editorState.setStatusMessage("Cannot place " + modelPath + ": only glTF and GLB models are placed this way", true);
            return null;
        }
        if (editorState.getActiveScene() == null) {
            editorState.setStatusMessage("Open a scene first to place " + objectLabel(modelPath), true);
            return null;
        }
        Vector3 position = new Vector3(x, fallbackY, z);
        String where;
        Entity ground = findGround();
        Terrain terrain = terrainSession.getTerrainOf(ground);
        if (terrain == null) {
            where = "at the view centre (this scene has no ground)";
        } else {
            Vector3 local = new Vector3(x, 0f, z).mul(ground.getTransform().getWorldMatrix().cpy().inv());
            float height = terrain.heightAt(local.x, local.z);
            if (Float.isNaN(height)) {
                where = "at the view centre (beyond the edge of the ground)";
            } else {
                position.set(local.x, height, local.z).mul(ground.getTransform().getWorldMatrix());
                where = "on the ground";
            }
        }
        String name = objectLabel(modelPath);
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        CreateEntityCommand command = new CreateEntityCommand(editorState, name, position,
            List.<Component>of(new ModelComponent(modelPath)));
        if (!editorState.executeCommand(command)) {
            return null;
        }
        Entity placed = command.getCreatedEntity();
        editorState.setSelectedEntity(placed);
        editorState.setStatusMessage("Placed " + name + " " + where + ". Move or turn it with the tools.", false);
        return placed;
    }

    // ---- objects ----------------------------------------------------------------------------------------------------------

    /**
     * Places the chosen object at a point of the ground's own space, turned and scaled a little at random so repeats differ.
     *
     * @return the new instance, or null (the reason is in the status bar)
     */
    public Entity placeObject(float localX, float localY, float localZ) {
        if (objectPath.isEmpty()) {
            editorState.setStatusMessage("Choose an object to place first", true);
            return null;
        }
        PrefabService prefabs = editorState.getPrefabService();
        Entity ground = findGround();
        if (prefabs == null || ground == null) {
            editorState.setStatusMessage("There is no ground yet: press New Level", true);
            return null;
        }
        Vector3 world = new Vector3(localX, localY, localZ)
            .mul(ground.getTransform().getWorldMatrix());
        if (objectPath.startsWith(CHARACTER_FOLDER + "/")) {
            return placeCharacter(prefabs, world);
        }
        if (objectPath.startsWith(BUILDING_FOLDER + "/")) {
            // A building stands square at its own size, never turned or scaled at random, and is selected to be moved or turned next
            Entity building = prefabs.instantiate(new AssetReference(objectPath), world, 0f, 1f, true);
            if (building != null) {
                editorState.setStatusMessage("Placed " + objectLabel(objectPath) + ". Move or turn it with the tools.", false);
            }
            return building;
        }
        float yaw = random.nextFloat() * 360f;
        float scale = 1f + (random.nextFloat() * 2f - 1f) * SCALE_JITTER;
        return prefabs.instantiate(new AssetReference(objectPath), world, yaw, scale, false);
    }

    /**
     * Places a character or spawn point from {@link #CHARACTER_FOLDER} at a point on the ground (roadmap M3c.2): facing -Z at its own
     * size, never turned or scaled at random, and selected so it can be moved or turned next. A character (its root has a Character
     * Controller 3D, whose centre is the root) is lifted to stand on the ground. A scene has one player and one spawn point, so a
     * second is refused with the name of the first.
     */
    private Entity placeCharacter(PrefabService prefabs, Vector3 world) {
        JsonValue root;
        String name;
        try {
            PrefabDocument document = prefabs.getDocument(objectPath);
            root = document.getEntity(document.getRootId());
            name = document.getName();
        } catch (IOException | RuntimeException e) {
            editorState.setStatusMessage("Cannot place " + objectLabel(objectPath) + ": " + e.getMessage(), true);
            return null;
        }
        Scene scene = editorState.getActiveScene();
        if (rootComponent(root, PLAYER_TYPE) != null) {
            Entity existing = firstWith(scene, PlayerComponent.class);
            if (existing != null) {
                editorState.setStatusMessage(existing.getName() + " is already the player, and a scene has one. Select it and move it, "
                    + "or remove its Player first.", true);
                return null;
            }
        }
        if (rootComponent(root, SPAWN_TYPE) != null) {
            Entity existing = firstWith(scene, SpawnPointComponent.class);
            if (existing != null) {
                editorState.setStatusMessage(existing.getName() + " is already the spawn point, and a scene has one. Select it and move "
                    + "it instead.", true);
                return null;
            }
        }
        JsonValue character = rootComponent(root, CHARACTER_TYPE);
        if (character != null) {
            world.y += character.getFloat("height", 0f) / 2f + character.getFloat("radius", 0f) + CHARACTER_CLEARANCE;
        }
        Entity placed = prefabs.instantiate(new AssetReference(objectPath), world, 0f, 1f, true);
        if (placed != null) {
            editorState.setStatusMessage("Placed " + name + ". Move or turn it with the tools; in Play the player starts at the spawn "
                + "point.", false);
        }
        return placed;
    }

    /** The data of the root's enabled component of {@code type} in a prefab file, or null. */
    private static JsonValue rootComponent(JsonValue root, String type) {
        JsonValue components = root == null ? null : root.get("components");
        if (components == null) {
            return null;
        }
        for (JsonValue component = components.child; component != null; component = component.next) {
            if (type.equals(component.getString("type", "")) && component.getBoolean("enabled", true)) {
                JsonValue data = component.get("data");
                return data != null ? data : new JsonValue(JsonValue.ValueType.object);
            }
        }
        return null;
    }

    private static Entity firstWith(Scene scene, Class<? extends Component> type) {
        if (scene == null) {
            return null;
        }
        for (Entity entity : scene.getAllEntities()) {
            if (entity.getComponent(type) != null) {
                return entity;
            }
        }
        return null;
    }

    /**
     * The placed object or building (a prefab instance root from {@link #OBJECT_FOLDER} or {@link #BUILDING_FOLDER}) nearest a world
     * point on the ground plane, within the erase radius.
     */
    public Entity objectNear(float worldX, float worldZ) {
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return null;
        }
        Entity nearest = null;
        float best = ERASE_RADIUS * ERASE_RADIUS;
        for (Entity entity : scene.getAllEntities()) {
            PrefabInstanceComponent link = PrefabService.linkOf(entity);
            if (link == null || !link.isRoot() || !(link.getPrefabPath().startsWith(OBJECT_FOLDER + "/")
                || link.getPrefabPath().startsWith(BUILDING_FOLDER + "/"))) {
                continue;
            }
            Vector3 position = entity.getTransform().getWorldPosition();
            float dx = position.x - worldX;
            float dz = position.z - worldZ;
            float distance = dx * dx + dz * dz;
            if (distance <= best) {
                best = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    /** Deletes the object nearest a world point, if one is within reach. */
    public boolean eraseObjectNear(float worldX, float worldZ) {
        Entity target = objectNear(worldX, worldZ);
        if (target == null) {
            editorState.setStatusMessage("Nothing to erase there", false);
            return false;
        }
        return editorState.executeCommand(new DeleteEntityCommand(editorState, target.getId()));
    }
}
