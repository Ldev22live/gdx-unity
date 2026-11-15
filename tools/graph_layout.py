"""Lays out the nodes of a generated .graph so it can be read in the editor (TD-072).

Nodes go into columns by how far they are from the start of their chains (the longest path along wires, execution and data alike), left to
right, and each column is stacked top to bottom and centred on y = 0. The editor shows y upwards, so the stack runs downwards from a
positive y. Spacing follows an estimate of each node's size: its wired pins, typed-in values and settings.
"""

COLUMN_WIDTH = 330.0
NODE_GAP = 40.0
BASE_HEIGHT = 70.0
ROW_HEIGHT = 24.0


def layout(nodes, links):
    by_id = {n["id"]: n for n in nodes}
    incoming = {n["id"]: [] for n in nodes}
    for link in links:
        incoming[link["to"]].append(link["from"])

    depth = {}

    def depth_of(node_id, path=()):
        if node_id in depth:
            return depth[node_id]
        if node_id in path:  # a loop of wires: cut it here
            return 0
        sources = incoming[node_id]
        depth[node_id] = 0 if not sources else 1 + max(depth_of(s, path + (node_id,)) for s in sources)
        return depth[node_id]

    for node in nodes:
        depth_of(node["id"])

    pins_used = {n["id"]: set() for n in nodes}
    for link in links:
        pins_used[link["from"]].add(("out", link["fromPin"]))
        pins_used[link["to"]].add(("in", link["toPin"]))

    def height(node):
        rows = max(sum(1 for d, _ in pins_used[node["id"]] if d == "in") + len(node["literals"]),
                   sum(1 for d, _ in pins_used[node["id"]] if d == "out"), 1)
        return BASE_HEIGHT + ROW_HEIGHT * (rows + len(node["properties"]))

    columns = {}
    for node in nodes:
        columns.setdefault(depth[node["id"]], []).append(node)
    for column, members in columns.items():
        total = sum(height(n) for n in members) + NODE_GAP * (len(members) - 1)
        y = total / 2.0
        for node in members:
            node["x"] = column * COLUMN_WIDTH
            node["y"] = y
            y -= height(node) + NODE_GAP
