package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavGrid3D;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavigationSystem3D;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bakes the active scene's navigation and, when asked, finds a path (roadmap M6.4, TD-092), so an agent can check that a guard will
 * find its way before it presses Play. Read-only: the bake is not kept on the scene.
 *
 * <p>Parameters (all optional): {@code from} and {@code to}, each a list of three numbers x, y, z. Outputs: {@code regions} (baked
 * Nav Mesh Bounds), {@code walkableCells}, {@code problems} (what was left out, as sentences), and with both ends {@code reachable},
 * {@code waypoints} (lists of x, y, z) and {@code length} (world units along the path).
 */
public class NavigationCheckTool implements AgentTool {
    public static final String ID = "navigation_check";
    private static final String FROM = "from";
    private static final String TO = "to";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Bakes the scene's Nav Mesh Bounds into walkable grids and reports regions, walkableCells and problems; with from and to "
            + "(each [x, y, z]) also finds the path a Nav Agent would walk: reachable, waypoints and length. Read-only.";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        if (context.getActiveScene() == null) {
            errors.add("No scene is open");
            return errors;
        }
        Object from = parameters == null ? null : parameters.get(FROM);
        Object to = parameters == null ? null : parameters.get(TO);
        if ((from == null) != (to == null)) {
            errors.add("Give both '" + FROM + "' and '" + TO + "' to find a path, or neither");
        }
        for (String key : new String[] {FROM, TO}) {
            Object value = parameters == null ? null : parameters.get(key);
            if (value != null && point(value) == null) {
                errors.add("'" + key + "' must be a list of three numbers x, y, z: " + value);
            }
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        return null; // Read-only
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = validateParameters(parameters, context);
        if (!errors.isEmpty()) {
            return AgentToolResult.failure(errors.get(0));
        }
        NavigationSystem3D navigation = NavigationSystem3D.bake(context.getActiveScene());
        int walkable = 0;
        for (NavigationSystem3D.Region region : navigation.getRegions()) {
            walkable += region.grid().walkableCount();
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("regions", navigation.getRegions().size());
        outputs.put("walkableCells", walkable);
        outputs.put("problems", List.copyOf(navigation.getProblems()));
        String summary = navigation.getRegions().isEmpty()
            ? "The scene has no Nav Mesh Bounds that could be baked; add one (Add Component > Navigation) over the ground"
            : navigation.getRegions().size() + " navigation region(s), " + walkable + " walkable cells";
        if (parameters != null && parameters.get(FROM) != null) {
            Vector3 from = point(parameters.get(FROM));
            Vector3 to = point(parameters.get(TO));
            List<Vector3> path = navigation.findPath(from, to);
            List<List<Float>> waypoints = new ArrayList<>();
            float length = 0f;
            Vector3 previous = from;
            for (Vector3 waypoint : path) {
                waypoints.add(List.of(waypoint.x, waypoint.y, waypoint.z));
                length += Vector3.dst(previous.x, 0f, previous.z, waypoint.x, 0f, waypoint.z);
                previous = waypoint;
            }
            outputs.put("reachable", !path.isEmpty());
            outputs.put("waypoints", waypoints);
            outputs.put("length", length);
            NavGrid3D grid = navigation.getRegions().isEmpty() ? null : navigation.gridAt(from.x, from.z);
            summary += path.isEmpty()
                ? (grid == null ? "; the start is outside every region" : "; no way from the start to the end")
                : "; a path of " + waypoints.size() + " waypoint(s), " + Math.round(length) + " units long";
        }
        return AgentToolResult.success(summary, outputs);
    }

    /** A point from a list of three numbers, or null. */
    private static Vector3 point(Object value) {
        if (!(value instanceof List<?> list) || list.size() != 3) {
            return null;
        }
        float[] xyz = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!(list.get(i) instanceof Number number) || !Float.isFinite(number.floatValue())) {
                return null;
            }
            xyz[i] = number.floatValue();
        }
        return new Vector3(xyz[0], xyz[1], xyz[2]);
    }
}
