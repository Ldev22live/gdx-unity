# Web 3D physics spike (TD-058)

Three small scripts that drop a sphere on a floor, then drop 300 boxes, on the three candidate engines for the web player:
`jolt.mjs` (jolt-physics, Jolt's JavaScript build), `rapier.mjs` (@dimforge/rapier3d-compat) and `havok.mjs` (@babylonjs/havok).
The results are in `docs/reports/web-3d-physics-evaluation.md`. They are not part of any build.

```
mkdir <folder-outside-the-repo> && cd <folder>
npm init -y && npm install jolt-physics @dimforge/rapier3d-compat @babylonjs/havok
node <repo>/tools/web-physics-spike/jolt.mjs <folder>      # likewise rapier.mjs and havok.mjs
```

Each script takes the folder that holds `node_modules` as its only argument and prints JSON. Timings depend on the machine and
include warm-up, so compare them with each other, not with other computers. Versions measured: jolt-physics 1.1.1,
@dimforge/rapier3d-compat 0.21.0, @babylonjs/havok 1.3.14 (Node 26.12.1).
