package io.github.ldev22live.gdxunity.editor.core.scenario;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The editable part of a scene's {@code scenario} block (TD-108, roadmap A04): its player slots and map bounds. Everything else in
 * the block (objectives, rules, members a newer client added) is kept as the exact JSON text it was read as and written back in its
 * original place, so editing players never changes data this version does not edit. Immutable: each edit returns a new setup.
 * <p>
 * A block this version cannot edit safely (a newer {@code version}, or players it cannot read) is {@linkplain #isReadOnly() read
 * only}: it keeps its raw text and every edit is refused with {@link #readOnlyReason()}.
 */
public final class ScenarioSetup {
    /** Most player slots a scenario may have (schema {@code players.maxItems}). */
    public static final int MAX_PLAYERS = 16;
    /** Longest player name (schema {@code players[].name.maxLength}). */
    public static final int MAX_NAME_LENGTH = 60;
    /** A player colour: {@code #rrggbb}. */
    public static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    /** The colours new slots take, in order (an original palette, chosen to tell apart on grass, sand and snow). */
    public static final List<String> SLOT_COLORS = List.of("#2f6fdf", "#d9432f", "#3fa34d", "#e0b62d", "#8e4fc9", "#2fb5c9",
        "#e07a2d", "#d94f9a");
    /** The neutral slot's colour. */
    public static final String NEUTRAL_COLOR = "#8a8a7a";
    /** The neutral slot's name when it is added. */
    public static final String NEUTRAL_NAME = "Neutral";

    private static final String PLAYERS = "players";
    private static final String BOUNDS = "bounds";
    private static final Set<String> PLAYER_MEMBERS = Set.of("id", "name", "color", "neutral", "spawn");

    /** One player slot. {@code spawn} is the entity id of a Spawn Point, or null; {@code preserved} keeps members of newer clients. */
    public record PlayerSlot(String id, String name, String color, boolean neutral, String spawn, PreservedFields preserved) {
        public PlayerSlot {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(color, "color cannot be null");
            Objects.requireNonNull(preserved, "preserved cannot be null");
        }

        PlayerSlot withName(String value) {
            return new PlayerSlot(id, value, color, neutral, spawn, preserved);
        }

        PlayerSlot withColor(String value) {
            return new PlayerSlot(id, name, value, neutral, spawn, preserved);
        }

        PlayerSlot withSpawn(String value) {
            return new PlayerSlot(id, name, color, neutral, value, preserved);
        }
    }

    /** The playable area in scene units on X and Z (Y unbounded); always finite with each minimum below its maximum. */
    public record Bounds(double minX, double minZ, double maxX, double maxZ) {
        /** @throws IllegalArgumentException when a value is not finite or a minimum is not below its maximum */
        public Bounds {
            if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) {
                throw new IllegalArgumentException("Bounds must be finite numbers: " + minX + ", " + minZ + ", " + maxX + ", " + maxZ);
            }
            if (!(minX < maxX) || !(minZ < maxZ)) {
                throw new IllegalArgumentException("Each minimum must be below its maximum: X " + minX + " to " + maxX + ", Z "
                    + minZ + " to " + maxZ);
            }
        }
    }

    /** Every member of the block as compact JSON text, in file order; {@code players} and {@code bounds} are rewritten from fields. */
    private final Map<String, String> members;
    private final List<PlayerSlot> players;
    private final Bounds bounds;
    private final String readOnlyReason;

    private ScenarioSetup(Map<String, String> members, List<PlayerSlot> players, Bounds bounds, String readOnlyReason) {
        this.members = members;
        this.players = players;
        this.bounds = bounds;
        this.readOnlyReason = readOnlyReason;
    }

    /** The scenario of {@code scene}, or empty when it has no scenario block. */
    public static Optional<ScenarioSetup> read(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        String raw = scene.getScenarioJson();
        return raw == null ? Optional.empty() : Optional.of(parse(raw));
    }

    /** A new scenario with two named, coloured player slots and no objectives or rules (TD-108). */
    public static ScenarioSetup starter() {
        Map<String, String> members = new LinkedHashMap<>();
        members.put("version", String.valueOf(ScenarioValidator.SUPPORTED_VERSION));
        members.put(PLAYERS, "[]");
        members.put("objectives", "[]");
        members.put("rules", "[]");
        List<PlayerSlot> players = List.of(
            new PlayerSlot(newId(), "Player 1", SLOT_COLORS.get(0), false, null, PreservedFields.EMPTY),
            new PlayerSlot(newId(), "Player 2", SLOT_COLORS.get(1), false, null, PreservedFields.EMPTY));
        return new ScenarioSetup(members, players, null, null);
    }

    /**
     * Reads a scenario block's JSON text. Never throws for content: a block it cannot edit safely comes back read only, with why.
     *
     * @throws IllegalArgumentException when {@code json} is not JSON at all
     */
    public static ScenarioSetup parse(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The scenario block is not JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            return readOnly(Map.of(), "The scenario block is not a JSON object");
        }
        Map<String, String> members = new LinkedHashMap<>();
        for (JsonValue member = root.child; member != null; member = member.next) {
            members.put(member.name, PreservedFields.toJson(member));
        }
        int version = root.getInt("version", -1);
        if (version > ScenarioValidator.SUPPORTED_VERSION) {
            return readOnly(members, "This scenario is version " + version + " and needs a newer editor (this one edits version "
                + ScenarioValidator.SUPPORTED_VERSION + "); it is kept as it is");
        }
        if (version != ScenarioValidator.SUPPORTED_VERSION) {
            return readOnly(members, "The scenario has no valid version (" + root.get("version") + "); it is kept as it is");
        }
        List<PlayerSlot> players = new ArrayList<>();
        JsonValue list = root.get(PLAYERS);
        if (list != null && !list.isArray()) {
            return readOnly(members, "The scenario's players are not a list; they are kept as they are");
        }
        for (JsonValue player = list == null ? null : list.child; player != null; player = player.next) {
            String problem = slotProblem(player);
            if (problem != null) {
                return readOnly(members, "Player " + (players.size() + 1) + " cannot be edited (" + problem + "); the scenario is kept "
                    + "as it is");
            }
            players.add(new PlayerSlot(player.getString("id"), player.getString("name"), player.getString("color"),
                player.getBoolean("neutral", false), player.getString("spawn", null),
                PreservedFields.unknownMembers(player, PLAYER_MEMBERS)));
        }
        Bounds bounds = null;
        JsonValue boundsValue = root.get(BOUNDS);
        if (boundsValue != null) {
            try {
                bounds = new Bounds(boundsValue.getDouble("minX"), boundsValue.getDouble("minZ"), boundsValue.getDouble("maxX"),
                    boundsValue.getDouble("maxZ"));
            } catch (RuntimeException e) {
                return readOnly(members, "The map bounds cannot be read (" + e.getMessage() + "); the scenario is kept as it is");
            }
        }
        return new ScenarioSetup(members, List.copyOf(players), bounds, null);
    }

    private static String slotProblem(JsonValue player) {
        if (!player.isObject()) {
            return "not an object";
        }
        String id = player.getString("id", null);
        try {
            UUID.fromString(String.valueOf(id));
        } catch (IllegalArgumentException e) {
            return "its id is not a UUID: " + id;
        }
        JsonValue name = player.get("name");
        if (name == null || !name.isString()) {
            return "it has no name";
        }
        JsonValue color = player.get("color");
        if (color == null || !color.isString() || !COLOR.matcher(color.asString()).matches()) {
            return "its colour is not #rrggbb";
        }
        JsonValue neutral = player.get("neutral");
        if (neutral != null && !neutral.isBoolean()) {
            return "'neutral' is not true or false";
        }
        JsonValue spawn = player.get("spawn");
        if (spawn != null && !spawn.isString()) {
            return "its spawn is not an entity id";
        }
        return null;
    }

    private static ScenarioSetup readOnly(Map<String, String> members, String reason) {
        return new ScenarioSetup(Collections.unmodifiableMap(new LinkedHashMap<>(members)), List.of(), null, reason);
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    /** True when this version must not edit the block (see {@link #readOnlyReason()}). */
    public boolean isReadOnly() {
        return readOnlyReason != null;
    }

    /** Why the block cannot be edited, or null when it can. */
    public String readOnlyReason() {
        return readOnlyReason;
    }

    /** The player slots, in order (empty for a read-only block). */
    public List<PlayerSlot> players() {
        return players;
    }

    /** The slot with {@code id}, if there is one. */
    public Optional<PlayerSlot> player(String id) {
        return players.stream().filter(slot -> slot.id().equals(id)).findFirst();
    }

    /** The map bounds, if set. */
    public Optional<Bounds> bounds() {
        return Optional.ofNullable(bounds);
    }

    /** The compact JSON text of a member this version keeps without editing ({@code objectives}, {@code rules}), or null. */
    public String rawMember(String name) {
        return members.get(name);
    }

    /**
     * The name to show for {@code slot}: its name, followed by the start of its id when another slot has the same name ignoring
     * case (a file from elsewhere may have duplicates; they are kept and told apart by id).
     */
    public String displayName(PlayerSlot slot) {
        Objects.requireNonNull(slot, "slot cannot be null");
        String key = slot.name().toLowerCase(Locale.ROOT);
        long same = players.stream().filter(other -> other.name().toLowerCase(Locale.ROOT).equals(key)).count();
        return same > 1 ? slot.name() + " (" + slot.id().substring(0, 8) + ")" : slot.name();
    }

    // ---- edits; each returns a new setup -----------------------------------------------------------------------------------

    ScenarioSetup withPlayers(List<PlayerSlot> value) {
        requireEditable();
        return new ScenarioSetup(members, List.copyOf(value), bounds, null);
    }

    ScenarioSetup withBounds(Bounds value) {
        requireEditable();
        return new ScenarioSetup(members, players, value, null);
    }

    ScenarioSetup replacePlayer(PlayerSlot slot) {
        List<PlayerSlot> list = new ArrayList<>(players);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(slot.id())) {
                list.set(i, slot);
                return withPlayers(list);
            }
        }
        throw new IllegalArgumentException("No player " + slot.id());
    }

    private void requireEditable() {
        if (readOnlyReason != null) {
            throw new IllegalStateException(readOnlyReason);
        }
    }

    /** The block as compact JSON, every member in its original order; a read-only block comes back exactly as read. */
    public String toJson() {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        boolean boundsWritten = false;
        for (Map.Entry<String, String> member : members.entrySet()) {
            String name = member.getKey();
            String raw = member.getValue();
            if (readOnlyReason == null) {
                if (name.equals(BOUNDS)) {
                    if (bounds == null) {
                        continue;
                    }
                    raw = boundsJson(bounds);
                    boundsWritten = true;
                } else if (name.equals(PLAYERS)) {
                    raw = playersJson();
                }
            }
            first = append(out, first, name, raw);
            if (readOnlyReason == null && name.equals(PLAYERS) && bounds != null && !members.containsKey(BOUNDS)) {
                first = append(out, first, BOUNDS, boundsJson(bounds));
                boundsWritten = true;
            }
        }
        if (readOnlyReason == null && bounds != null && !boundsWritten) {
            append(out, first, BOUNDS, boundsJson(bounds));
        }
        return out.append('}').toString();
    }

    private static boolean append(StringBuilder out, boolean first, String name, String raw) {
        if (!first) {
            out.append(',');
        }
        out.append(PreservedFields.quote(name)).append(':').append(raw);
        return false;
    }

    private String playersJson() {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < players.size(); i++) {
            PlayerSlot slot = players.get(i);
            out.append(i == 0 ? "" : ",").append("{\"id\":").append(PreservedFields.quote(slot.id()))
                .append(",\"name\":").append(PreservedFields.quote(slot.name()))
                .append(",\"color\":").append(PreservedFields.quote(slot.color()));
            if (slot.neutral()) {
                out.append(",\"neutral\":true");
            }
            if (slot.spawn() != null) {
                out.append(",\"spawn\":").append(PreservedFields.quote(slot.spawn()));
            }
            for (Map.Entry<String, String> extra : slot.preserved().asMap().entrySet()) {
                out.append(',').append(PreservedFields.quote(extra.getKey())).append(':').append(extra.getValue());
            }
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String boundsJson(Bounds bounds) {
        return "{\"minX\":" + number(bounds.minX()) + ",\"minZ\":" + number(bounds.minZ()) + ",\"maxX\":" + number(bounds.maxX())
            + ",\"maxZ\":" + number(bounds.maxZ()) + "}";
    }

    private static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** {@code scene}'s top-level preserved members with the scenario block set to {@code json} (null removes it), order kept. */
    static PreservedFields withScenario(PreservedFields fields, String json) {
        Map<String, String> copy = new LinkedHashMap<>(fields.asMap());
        if (json == null) {
            copy.remove(Scene.SCENARIO_MEMBER);
        } else {
            copy.put(Scene.SCENARIO_MEMBER, json);
        }
        return PreservedFields.of(copy);
    }
}
