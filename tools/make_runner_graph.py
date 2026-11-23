"""Writes assets/graphs/samples/RunnerPlayer.graph: the whole of samples/runner/RunnerPlayer.java as nodes (TD-072).

Run from the repository root:  python tools/make_runner_graph.py

The graph does what the script does: A/D or the arrows change lane once per press (three lanes 130 apart) and the player slides across at
1100 units a second; Space, W or Up jump once per press while on the ground (760 take-off, 2400 gravity) and play the "jump" sound; R
starts over. It uses Key Pressed nodes (true only on the frame a key goes down) and a Play Sound node. Tests play the Java script and
this graph side by side. The world that moves towards the player (RunnerGame) stays Java.
"""
import json
import os

from graph_layout import layout

LANE_SPACING = 130.0
LANE_CHANGE_SPEED = 1100.0
JUMP_SPEED = 760.0
GRAVITY = 2400.0

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "graphs", "samples", "RunnerPlayer.graph")


class Graph:
    def __init__(self):
        self.nodes = []
        self.links = []
        self.x = 0.0

    def node(self, type_, literals=None, props=None, column=None):
        node_id = "n%d" % (len(self.nodes) + 1)
        if column is not None:
            self.x = column * 260.0
        self.nodes.append({
            "id": node_id, "type": type_, "x": self.x, "y": 40.0 * (len(self.nodes) % 14),
            "literals": [{"pin": p, "type": "FLOAT", "value": float(v)} for p, v in (literals or {}).items()],
            "properties": [{"name": k, "type": "STRING", "value": v} for k, v in (props or {}).items()],
        })
        return node_id

    def wire(self, src, src_pin, dst, dst_pin):
        self.links.append({"from": src, "fromPin": src_pin, "to": dst, "toPin": dst_pin})

    def then(self, src, dst, pin="then"):
        self.wire(src, pin, dst, "exec")

    def literal(self, node_id, pin, value):
        next(n for n in self.nodes if n["id"] == node_id)["literals"].append({"pin": pin, "type": "FLOAT", "value": float(value)})


g = Graph()
column = 0


def var_get(name):
    return g.node("variable.get", props={"variable": name})


def var_set(name, source, pin="result", constant=None):
    node = g.node("variable.set", props={"variable": name})
    if constant is not None:
        g.literal(node, "value", constant)
    else:
        g.wire(source, pin, node, "value")
    return node


def math(kind, a, b):
    """A math node; a and b are numbers (typed in) or (node, pin) wires."""
    node = g.node("math." + kind)
    for pin, source in (("a", a), ("b", b)):
        if isinstance(source, tuple):
            g.wire(source[0], source[1], node, pin)
        else:
            g.literal(node, pin, source)
    return node


def value(name):
    return (var_get(name), "value")


def key_pressed(keys):
    return (g.node("input.keyPressed", props={"key": keys}), "pressed")


def both(a, b):
    node = g.node("logic.and")
    g.wire(a[0], a[1], node, "a")
    g.wire(b[0], b[1], node, "b")
    return (node, "result")


def negate(a):
    node = g.node("logic.not")
    g.wire(a[0], a[1], node, "value")
    return (node, "result")


def decide(previous, condition, then_chain, else_chain=None):
    """Branch on condition. then_chain(first) and else_chain(first) each take the node to hang a chain from and return its last node.
    Every way out meets again at a Sequence node whose Then 0 is returned (a join point)."""
    global column
    column += 1
    g.x = column * 260.0
    branch = g.node("flow.branch")
    g.wire(condition[0], condition[1], branch, "condition")
    g.then(previous[0], branch, previous[1])
    join = g.node("flow.sequence")
    for pin, chain in (("true", then_chain), ("false", else_chain)):
        if chain is None:
            g.then(branch, join, pin)
            continue
        first = g.node("flow.sequence")  # a plain entry point for the chain: its Then 0 starts the chain
        g.then(branch, first, pin)
        last = chain((first, "then0"))
        g.then(last[0], join, last[1])
    column += 1
    return (join, "then0")


def chain_of(*steps):
    """Runs steps one after the other from the given point: each step is a function taking (node, pin) and returning (node, pin)."""
    def run(start):
        point = start
        for step in steps:
            point = step(point)
        return point
    return run


def after(previous, node):
    g.then(previous[0], node, previous[1])
    return (node, "then")


def set_var(name, source=None, pin="result", constant=None):
    return lambda previous: after(previous, var_set(name, source, pin, constant))


# --- Begin Play: remember the ground and the distance, start in the middle lane ---
begin = g.node("event.beginPlay")
position = g.node("data.getPosition")
parts = g.node("vector.break")
g.wire(position, "position", parts, "vector")
point = (begin, "then")
point = after(point, var_set("groundY", parts, "y"))
point = after(point, var_set("z", parts, "z"))
point = after(point, var_set("x", None, constant=0.0))
point = after(point, var_set("y", parts, "y"))

# --- Tick ---
tick = g.node("event.tick", column=0)
dt = (tick, "deltaTime")
point = (tick, "then")

# R: back to the middle lane, on the ground
point = decide(point, key_pressed("R"), chain_of(
    set_var("lane", constant=1.0), set_var("x", constant=0.0),
    lambda p: after(p, var_set("y", var_get("groundY"), "value")), set_var("velocityY", constant=0.0)))

# A / Left: one lane left; D / Right: one lane right
left_test = g.node("math.greater")
g.wire(var_get("lane"), "value", left_test, "a")
g.literal(left_test, "b", 0.0)
point = decide(point, both(key_pressed("A,LEFT"), (left_test, "result")),
               set_var("lane", math("subtract", value("lane"), 1.0), "result"))
right_test = g.node("math.less")
g.wire(var_get("lane"), "value", right_test, "a")
g.literal(right_test, "b", 2.0)
point = decide(point, both(key_pressed("D,RIGHT"), (right_test, "result")),
               set_var("lane", math("add", value("lane"), 1.0), "result"))

# Jump: once per press, only on the ground
above_ground = g.node("math.greater")
g.wire(var_get("y"), "value", above_ground, "a")
g.wire(var_get("groundY"), "value", above_ground, "b")
on_ground = negate((above_ground, "result"))
sound = g.node("action.playCue", props={"cue": "jump"})
point = decide(point, both(on_ground, key_pressed("SPACE,W,UP")), chain_of(
    set_var("velocityY", constant=JUMP_SPEED), lambda p: (g.then(p[0], sound, p[1]), (sound, "then"))[1]))

# Slide towards the lane: x moves LANE_CHANGE_SPEED * dt, or lands on the lane when it is closer than that
target = math("multiply", (math("subtract", value("lane"), 1.0), "result"), LANE_SPACING)
step = math("multiply", dt, LANE_CHANGE_SPEED)
distance = math("subtract", (target, "result"), value("x"))
too_far_right = g.node("math.greater")
g.wire(distance, "result", too_far_right, "a")
g.wire(step, "result", too_far_right, "b")
negative_step = math("multiply", (step, "result"), -1.0)
too_far_left = g.node("math.less")
g.wire(distance, "result", too_far_left, "a")
g.wire(negative_step, "result", too_far_left, "b")
close = both(negate((too_far_right, "result")), negate((too_far_left, "result")))
heading_right = g.node("math.greater")
g.wire(distance, "result", heading_right, "a")
g.literal(heading_right, "b", 0.0)
point = decide(point, close,
               lambda p: after(p, var_set("x", target, "result")),
               lambda p: decide(p, (heading_right, "result"),
                                lambda q: after(q, var_set("x", math("add", value("x"), (step, "result")), "result")),
                                lambda q: after(q, var_set("x", math("subtract", value("x"), (step, "result")), "result"))))

# Fall: velocityY -= gravity * dt; y += velocityY * dt; the ground stops it
point = after(point, var_set("velocityY", math("subtract", value("velocityY"), (math("multiply", dt, GRAVITY), "result")), "result"))
point = after(point, var_set("y", math("add", value("y"), (math("multiply", value("velocityY"), dt), "result")), "result"))
below = g.node("math.less")
g.wire(var_get("y"), "value", below, "a")
g.wire(var_get("groundY"), "value", below, "b")
point = decide(point, (below, "result"), chain_of(
    lambda p: after(p, var_set("y", var_get("groundY"), "value")), set_var("velocityY", constant=0.0)))

# Put the player there
place = g.node("vector.make")
g.wire(var_get("x"), "value", place, "x")
g.wire(var_get("y"), "value", place, "y")
g.wire(var_get("z"), "value", place, "z")
write = g.node("action.setPosition")
g.wire(place, "vector", write, "position")
g.then(point[0], write, point[1])

variables = [{"name": n, "type": "FLOAT", "value": v} for n, v in
             (("lane", 1.0), ("x", 0.0), ("y", 0.0), ("z", 0.0), ("groundY", 0.0), ("velocityY", 0.0))]
document = {"schemaVersion": 1, "kind": "graph", "name": "RunnerPlayer", "variables": variables, "nodes": g.nodes, "links": g.links}
layout(document["nodes"], document["links"])
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(document, f, indent=2)
    f.write("\n")
print("wrote", os.path.normpath(OUT), len(g.nodes), "nodes", len(g.links), "links")
