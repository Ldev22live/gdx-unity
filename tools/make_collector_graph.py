"""Writes assets/graphs/samples/CollectorPlayer.graph: the whole of samples/collector/CollectorPlayer.java as nodes (TD-071).

Run from the repository root:  python tools/make_collector_graph.py

The graph does what the script does, frame for frame: A/D/W/S or the arrows accelerate the ball, rolling friction keeps 0.15 of the
speed per second (a Power node), the speed is limited to 460 (Vector3.limit), the ball bounces off the arena edge at 740 keeping 0.6 of
its speed, and R puts it back at the start (once per press). Tests play the Java script and this graph side by side.
"""
import json
import os

from graph_layout import layout

ACCELERATION = 1500.0
MAX_SPEED = 460.0
DRAG = 0.15
ARENA_HALF_SIZE = 740.0
WALL_BOUNCE = 0.6
LIMIT = "java:com.badlogic.gdx.math.Vector3#limit(float)"

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "graphs", "samples", "CollectorPlayer.graph")
EXAMPLES = os.path.join(os.path.dirname(OUT), "examples.json")


class Graph:
    def __init__(self):
        self.nodes = []
        self.links = []
        self.x = 0.0
        self.pending = []

    def node(self, type_, literals=None, props=None, column=None):
        node_id = "n%d" % (len(self.nodes) + 1)
        if column is not None:
            self.x = column * 260.0
        entry = {
            "id": node_id, "type": type_, "x": self.x, "y": 40.0 * (len(self.nodes) % 14),
            "literals": [{"pin": p, "type": "FLOAT", "value": v} for p, v in (literals or {}).items()],
            "properties": [{"name": k, "type": "STRING", "value": v} for k, v in (props or {}).items()],
        }
        self.nodes.append(entry)
        return node_id

    def wire(self, src, src_pin, dst, dst_pin):
        self.links.append({"from": src, "fromPin": src_pin, "to": dst, "toPin": dst_pin})

    def then(self, src, dst, pin="then"):
        self.wire(src, pin, dst, "exec")


g = Graph()


def var_get(name, column=None):
    return g.node("variable.get", props={"variable": name}, column=column)


def var_set(name, value_node, value_pin, column=None):
    node = g.node("variable.set", props={"variable": name}, column=column)
    g.wire(value_node, value_pin, node, "value")
    return node


def make(components, column=None):
    """A Make Vector node; each component is a number (typed in) or a (node, pin) wire."""
    node = g.node("vector.make", column=column)
    entry = g.nodes[-1]
    for pin, source in zip(("x", "y", "z"), components):
        if isinstance(source, tuple):
            g.wire(source[0], source[1], node, pin)
        else:
            entry["literals"].append({"pin": pin, "type": "FLOAT", "value": float(source)})
    return node


def split(vector_node, vector_pin):
    node = g.node("vector.break")
    g.wire(vector_node, vector_pin, node, "vector")
    return node


def math(kind, a, b):
    """A math node; a and b are numbers (typed in) or (node, pin) wires."""
    node = g.node("math." + kind)
    entry = g.nodes[-1]
    for pin, source in (("a", a), ("b", b)):
        if isinstance(source, tuple):
            g.wire(source[0], source[1], node, pin)
        else:
            entry["literals"].append({"pin": pin, "type": "FLOAT", "value": float(source)})
    return node


def result(node):
    return (node, "result")


# --- Begin Play: remember where the ball starts ---
begin = g.node("event.beginPlay", column=0)
start_position = g.node("data.getPosition", column=1)
remember = var_set("start", start_position, "position", column=2)
g.then(begin, remember)

# --- Tick ---
tick = g.node("event.tick", column=0)
dt = (tick, "deltaTime")

# R puts the ball back, once per press
r_down = g.node("input.keyDown", props={"key": "R"}, column=1)
r_held = var_get("rHeld")
not_held = g.node("logic.not")
g.wire(r_held, "value", not_held, "value")
pressed_now = g.node("logic.and")
g.wire(r_down, "pressed", pressed_now, "a")
g.wire(not_held, "result", pressed_now, "b")
restart = g.node("flow.branch", column=2)
g.wire(pressed_now, "result", restart, "condition")
g.then(tick, restart)
start_get = var_get("start", column=3)
put_back = g.node("action.setPosition", column=3)
g.wire(start_get, "value", put_back, "position")
stop_ball = var_set("velocity", make([0, 0, 0]), "vector", column=4)
g.then(restart, put_back, "true")
g.then(put_back, stop_ball)
r_down_again = g.node("input.keyDown", props={"key": "R"}, column=5)
remember_r = var_set("rHeld", r_down_again, "pressed", column=6)
g.then(stop_ball, remember_r)
g.then(restart, remember_r, "false")

# Accelerate, slow down and limit: velocity = limit((velocity + keys * 1500 * dt) * 0.15 ^ dt, 460)
axis_x = g.node("input.axis", props={"negative": "A,LEFT", "positive": "D,RIGHT"}, column=7)
axis_z = g.node("input.axis", props={"negative": "W,UP", "positive": "S,DOWN"})
push_x = math("multiply", result(math("multiply", (axis_x, "value"), ACCELERATION)), dt)
push_z = math("multiply", result(math("multiply", (axis_z, "value"), ACCELERATION)), dt)
push = make([result(push_x), 0, result(push_z)])
speeded = g.node("vector.add", column=8)
g.wire(var_get("velocity"), "value", speeded, "a")
g.wire(push, "vector", speeded, "b")
drag = g.node("math.power", literals={"base": DRAG}, column=8)
g.wire(*dt, drag, "exponent")
slowed = g.node("vector.scale", column=9)
g.wire(speeded, "result", slowed, "vector")
g.wire(drag, "result", slowed, "factor")
limit = g.node(LIMIT, literals={"p0": MAX_SPEED}, column=10)
g.wire(slowed, "result", limit, "target")
g.then(remember_r, limit)
keep_velocity = var_set("velocity", limit, "result", column=11)
g.then(limit, keep_velocity)

# Move: position = position + velocity * dt
step = g.node("vector.scale", column=12)
g.wire(var_get("velocity"), "value", step, "vector")
g.wire(*dt, step, "factor")
moved = g.node("vector.add", column=12)
g.wire(g.node("data.getPosition"), "position", moved, "a")
g.wire(step, "result", moved, "b")
keep_position = var_set("position", moved, "result", column=13)
g.then(keep_velocity, keep_position)

# Bounce off the arena edge, one axis at a time: past +-740 the ball is put on the edge and the speed turns around, keeping 0.6
previous = keep_position
previous_pin = "then"
column = 14
for axis in ("x", "z"):
    index = 0 if axis == "x" else 2
    for edge in (ARENA_HALF_SIZE, -ARENA_HALF_SIZE):
        position_parts = split(var_get("position", column=column), "value")
        test = g.node("math.greater" if edge > 0 else "math.less", literals={"b": edge}, column=column)
        g.wire(position_parts, axis, test, "a")
        branch = g.node("flow.branch", column=column)
        g.wire(test, "result", branch, "condition")
        g.wire(previous, previous_pin, branch, "exec")
        # on the edge
        position_now = split(var_get("position"), "value")
        placed_parts = [(position_now, "x"), (position_now, "y"), (position_now, "z")]
        placed_parts[index] = edge
        place = var_set("position", make(placed_parts), "vector", column=column + 1)
        velocity_now = split(var_get("velocity"), "value")
        turned = math("multiply", result(math("multiply", (velocity_now, axis), -1.0)), WALL_BOUNCE)
        turned_parts = [(velocity_now, "x"), (velocity_now, "y"), (velocity_now, "z")]
        turned_parts[index] = result(turned)
        bounce = var_set("velocity", make(turned_parts), "vector", column=column + 1)
        g.then(branch, place, "true")
        g.then(place, bounce)
        # both ways out lead to the next test
        previous = branch
        previous_pin = "false"
        g.pending.append((bounce, "then"))
        column += 2
    # after the +edge test and the -edge test, every way out meets here
    meeting = g.node("flow.sequence", column=column)  # a join point: Sequence runs Then 0 only
    for source, pin in g.pending + [(previous, previous_pin)]:
        g.wire(source, pin, meeting, "exec")
    g.pending = []
    previous = meeting
    previous_pin = "then0"
    column += 1

final_position = var_get("position", column=column)
write = g.node("action.setPosition", column=column)
g.wire(final_position, "value", write, "position")
g.wire(previous, previous_pin, write, "exec")

document = {
    "schemaVersion": 1, "kind": "graph", "name": "CollectorPlayer",
    "variables": [
        {"name": "velocity", "type": "VECTOR3", "value": {"x": 0, "y": 0, "z": 0}},
        {"name": "start", "type": "VECTOR3", "value": {"x": 0, "y": 0, "z": 0}},
        {"name": "position", "type": "VECTOR3", "value": {"x": 0, "y": 0, "z": 0}},
        {"name": "rHeld", "type": "BOOLEAN", "value": False},
    ],
    "nodes": g.nodes, "links": g.links,
}
layout(document["nodes"], document["links"])
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(document, f, indent=2)
    f.write("\n")
print("wrote", os.path.normpath(OUT), len(g.nodes), "nodes", len(g.links), "links")
