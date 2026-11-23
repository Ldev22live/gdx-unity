"""Writes assets/graphs/samples/SandboxKick.graph: the whole of samples/sandbox/SandboxLauncher.java as nodes (roadmap M1.2).

Run from the repository root:  python tools/make_sandbox_graph.py

Every frame the graph aims along its entity's view (forward is -Z turned by the world rotation), casts a 6000-unit ray and shows what it
hits on the Help Text. Aiming at a dynamic body: Space (once per press) kicks it with an impulse of 900 times its mass along the view, F
(held) pushes it with a force of 1500 times its mass, T (once per press) stops it. Aiming at anything: E (once per press) gives every
dynamic body within 260 units of the aim point an upward impulse of 700 times its mass. That is the script's behaviour, rule for rule;
a test plays the sandbox with the script and with this graph and compares every body on every frame.

It uses the Java method nodes of PhysicsSystem3D (isDynamic, getMass, addImpulse, addForce, setLinearVelocity, overlapSphere), the For
Each Entity loop over overlapSphere's list, and Vector3.mul and nor for the aim.
"""
import json
import os

from graph_layout import layout

RANGE = 6000.0
KICK_SPEED = 900.0
PUSH_ACCELERATION = 1500.0
POP_RADIUS = 260.0
POP_SPEED = 700.0
ALL_LAYERS = 65535  # PhysicsLayers3D.ALL_LAYERS: (1 << 16) - 1
HINT = "Aim at a ball or crate: Space kick, hold F push, E pop the neighbours, T stop it.  Aiming at: "

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "graphs", "samples", "SandboxKick.graph")

ENTITY = "io.github.ldev22live.gdxunity.engine.scene.Entity"
SCENE = "io.github.ldev22live.gdxunity.engine.scene.Scene"
TRANSFORM = "io.github.ldev22live.gdxunity.engine.scene.Transform"
PHYSICS = "io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D"
VECTOR = "com.badlogic.gdx.math.Vector3"
QUATERNION = "com.badlogic.gdx.math.Quaternion"
TEXT = "io.github.ldev22live.gdxunity.engine.render.components.TextComponent"


def java(owner, method, *parameters):
    return "java:%s#%s(%s)" % (owner, method, ",".join(parameters))


class Graph:
    def __init__(self):
        self.nodes = []
        self.links = []

    def node(self, type_, literals=None, props=None):
        node_id = "n%d" % (len(self.nodes) + 1)
        entries = []
        for pin, value in (literals or {}).items():
            if isinstance(value, str):
                entries.append({"pin": pin, "type": "STRING", "value": value})
            elif isinstance(value, int):
                entries.append({"pin": pin, "type": "INTEGER", "value": value})
            else:
                entries.append({"pin": pin, "type": "FLOAT", "value": float(value)})
        self.nodes.append({"id": node_id, "type": type_, "x": 0.0, "y": 0.0, "literals": entries,
                           "properties": [{"name": k, "type": "STRING", "value": v} for k, v in (props or {}).items()]})
        return node_id

    def wire(self, src, src_pin, dst, dst_pin):
        self.links.append({"from": src, "fromPin": src_pin, "to": dst, "toPin": dst_pin})

    def then(self, src, dst, pin="then"):
        self.wire(src, pin, dst, "exec")


g = Graph()


def physics():
    """Self, its scene, the scene's 3D physics: a fresh pure chain, so each use reads its own wires."""
    scene = g.node(java(ENTITY, "getScene"))
    g.wire(g.node("data.self"), "entity", scene, "target")
    system = g.node(java(SCENE, "getPhysics3D"))
    g.wire(scene, "result", system, "target")
    return system


def forward():
    return g.node("variable.get", props={"variable": "forward"})


def is_dynamic(entity_source):
    node = g.node(java(PHYSICS, "isDynamic", ENTITY))
    g.wire(physics(), "result", node, "target")
    g.wire(entity_source[0], entity_source[1], node, "p0")
    return node


def mass_times(entity_source, factor):
    mass = g.node(java(PHYSICS, "getMass", ENTITY))
    g.wire(physics(), "result", mass, "target")
    g.wire(entity_source[0], entity_source[1], mass, "p0")
    product = g.node("math.multiply", literals={"b": factor})
    g.wire(mass, "result", product, "a")
    return product


def key(kind, name):
    return g.node("input." + kind, props={"key": name})


def gate(condition_a, condition_b=None):
    """A Branch on a, or on a and b."""
    branch = g.node("flow.branch")
    if condition_b is None:
        g.wire(condition_a[0], condition_a[1], branch, "condition")
        return branch
    both = g.node("logic.and")
    g.wire(condition_a[0], condition_a[1], both, "a")
    g.wire(condition_b[0], condition_b[1], both, "b")
    g.wire(both, "result", branch, "condition")
    return branch


def set_help(text_source):
    """Help Text's TextComponent.setText(text)."""
    scene = g.node(java(ENTITY, "getScene"))
    g.wire(g.node("data.self"), "entity", scene, "target")
    help_entity = g.node(java(SCENE, "findEntityByName", "java.lang.String"), literals={"p0": "Help Text"})
    g.wire(scene, "result", help_entity, "target")
    component = g.node("component.get:TextComponent")
    g.wire(help_entity, "result", component, "target")
    set_text = g.node(java(TEXT, "setText", "java.lang.String"))
    g.wire(component, "component", set_text, "target")
    g.wire(text_source[0], text_source[1], set_text, "p0")
    return set_text


# ---- every frame: the aim ----
tick = g.node("event.tick")
down = g.node("vector.make", literals={"x": 0.0, "y": 0.0, "z": -1.0})
transform = g.node(java(ENTITY, "getTransform"))
g.wire(g.node("data.self"), "entity", transform, "target")
rotation = g.node(java(TRANSFORM, "getWorldRotation"))
g.wire(transform, "result", rotation, "target")
turn = g.node(java(VECTOR, "mul", QUATERNION))
g.wire(down, "vector", turn, "target")
g.wire(rotation, "result", turn, "p0")
g.then(tick, turn)
normalise = g.node(java(VECTOR, "nor"))
g.wire(turn, "result", normalise, "target")
g.then(turn, normalise)
store = g.node("variable.set", props={"variable": "forward"})
g.wire(normalise, "result", store, "value")
g.then(normalise, store)

ray = g.node("action.raycast3d", literals={"distance": RANGE})
g.wire(g.node("data.getPosition"), "position", ray, "origin")
g.wire(forward(), "value", ray, "direction")
g.then(store, ray)
hit = (ray, "entity")

# ---- nothing hit: say so ----
nothing = g.node("text.append", literals={"a": HINT, "b": "nothing"})
g.then(ray, set_help((nothing, "result")), "miss")

# ---- something hit: the label, then the four keys in the script's order ----
first = g.node("flow.sequence")
g.then(ray, first, "hit")
name = g.node("data.getName")
g.wire(ray, "entity", name, "target")
label = g.node("text.append", literals={"a": HINT})
g.wire(name, "name", label, "b")
g.then(first, set_help((label, "result")), "then0")

# Space: kick the aimed body along the view
kick_gate = gate((is_dynamic(hit), "result"), (key("keyPressed", "SPACE"), "pressed"))
g.then(first, kick_gate, "then1")
kick_vector = g.node("vector.scale")
g.wire(forward(), "value", kick_vector, "vector")
g.wire(mass_times(hit, KICK_SPEED), "result", kick_vector, "factor")
kick = g.node(java(PHYSICS, "addImpulse", ENTITY, VECTOR))
g.wire(physics(), "result", kick, "target")
g.wire(ray, "entity", kick, "p0")
g.wire(kick_vector, "result", kick, "p1")
g.then(kick_gate, kick, "true")

second = g.node("flow.sequence")
g.then(first, second, "then2")

# F held: push it
push_gate = gate((is_dynamic(hit), "result"), (key("keyDown", "F"), "pressed"))
g.then(second, push_gate, "then0")
push_vector = g.node("vector.scale")
g.wire(forward(), "value", push_vector, "vector")
g.wire(mass_times(hit, PUSH_ACCELERATION), "result", push_vector, "factor")
push = g.node(java(PHYSICS, "addForce", ENTITY, VECTOR))
g.wire(physics(), "result", push, "target")
g.wire(ray, "entity", push, "p0")
g.wire(push_vector, "result", push, "p1")
g.then(push_gate, push, "true")

# T: stop it
stop_gate = gate((is_dynamic(hit), "result"), (key("keyPressed", "T"), "pressed"))
g.then(second, stop_gate, "then1")
stop = g.node(java(PHYSICS, "setLinearVelocity", ENTITY, VECTOR))
g.wire(physics(), "result", stop, "target")
g.wire(ray, "entity", stop, "p0")
g.wire(g.node("vector.make", literals={"x": 0.0, "y": 0.0, "z": 0.0}), "vector", stop, "p1")
g.then(stop_gate, stop, "true")

# E: pop every dynamic body near the aim point
pop_gate = gate((key("keyPressed", "E"), "pressed"))
g.then(second, pop_gate, "then2")
near = g.node(java(PHYSICS, "overlapSphere", VECTOR, "float", "int"), literals={"p1": POP_RADIUS, "p2": ALL_LAYERS})
g.wire(physics(), "result", near, "target")
g.wire(ray, "point", near, "p0")
g.then(pop_gate, near, "true")
each = g.node("flow.forEachEntity")
g.wire(near, "result", each, "list")
g.then(near, each)
neighbour = (each, "entity")
each_gate = gate((is_dynamic(neighbour), "result"))
g.then(each, each_gate, "body")
lift = g.node("vector.make", literals={"x": 0.0, "z": 0.0})
g.wire(mass_times(neighbour, POP_SPEED), "result", lift, "y")
pop = g.node(java(PHYSICS, "addImpulse", ENTITY, VECTOR))
g.wire(physics(), "result", pop, "target")
g.wire(each, "entity", pop, "p0")
g.wire(lift, "vector", pop, "p1")
g.then(each_gate, pop, "true")

layout(g.nodes, g.links)
graph = {
    "schemaVersion": 2,
    "kind": "graph",
    "name": "SandboxKick",
    "variables": [{"name": "forward", "type": "VECTOR3", "value": {"x": 0.0, "y": 0.0, "z": -1.0}}],
    "nodes": g.nodes,
    "links": g.links,
}
with open(OUT, "w", encoding="utf-8", newline="") as out:
    out.write(json.dumps(graph, indent=2) + "\n")
print("SandboxKick.graph: %d nodes, %d wires" % (len(g.nodes), len(g.links)))
