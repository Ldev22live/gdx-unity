// Spike: Havok (@babylonjs/havok), low-level API. Drops a sphere on a static box and lists the exported functions for shapes and constraints.
import { pathToFileURL } from 'node:url';
import { readFileSync } from 'node:fs';

const root = process.argv[2];
const dir = `${root}/node_modules/@babylonjs/havok/lib/esm`;
const mod = await import(pathToFileURL(`${dir}/HavokPhysics_es.js`).href);
const t0 = performance.now();
const HK = await (mod.default ?? mod)({ wasmBinary: readFileSync(`${dir}/HavokPhysics.wasm`) });
const loadMs = performance.now() - t0;

const names = Object.keys(HK).filter((n) => n.startsWith('HP_'));
const has = (prefix) => names.filter((n) => n.startsWith(prefix)).length;
const shapes = names.filter((n) => /^HP_Shape_Create/.test(n)).map((n) => n.replace('HP_Shape_Create', ''));
const constraints = names.filter((n) => /^HP_Constraint_Set/.test(n)).length;

const [, world] = HK.HP_World_Create();
HK.HP_World_SetGravity(world, [0, -9.81, 0]);
const [, floorShape] = HK.HP_Shape_CreateBox([0, 0, 0], [0, 0, 0, 1], [100, 1, 100]);
const [, floor] = HK.HP_Body_Create();
HK.HP_Body_SetShape(floor, floorShape);
HK.HP_Body_SetMotionType(floor, HK.MotionType.STATIC);
HK.HP_Body_SetQTransform(floor, [[0, -0.5, 0], [0, 0, 0, 1]]);
HK.HP_World_AddBody(world, floor, false);
const [, ballShape] = HK.HP_Shape_CreateSphere([0, 0, 0], 0.5);
HK.HP_Shape_SetDensity(ballShape, 1);
const [, ball] = HK.HP_Body_Create();
HK.HP_Body_SetShape(ball, ballShape);
HK.HP_Body_SetMotionType(ball, HK.MotionType.DYNAMIC);
HK.HP_Body_SetMassProperties(ball, HK.HP_Shape_BuildMassProperties(ballShape)[1]);
HK.HP_Body_SetQTransform(ball, [[0, 5, 0], [0, 0, 0, 1]]);
HK.HP_World_AddBody(world, ball, false);
const steps = 240;
const t1 = performance.now();
for (let i = 0; i < steps; i++) HK.HP_World_Step(world, 1 / 60);
const stepMs = performance.now() - t1;
const y = HK.HP_Body_GetQTransform(ball)[1][0][1];

console.log(JSON.stringify({ engine: 'havok (@babylonjs/havok low-level)', loadMs: Math.round(loadMs), stepsMsTotal: +stepMs.toFixed(2), steps,
  finalBallY: +y.toFixed(3), expectedRestY: 0.5, shapeCreators: shapes, constraintSetters: constraints,
  hasConstraintApi: has('HP_Constraint_'), hasQuery: names.filter((n) => /Raycast|Cast|Query/i.test(n)).slice(0, 6) }, null, 1));

{
  const [, shape] = HK.HP_Shape_CreateBox([0, 0, 0], [0, 0, 0, 1], [0.5, 0.5, 0.5]);
  HK.HP_Shape_SetDensity(shape, 1);
  const mass = HK.HP_Shape_BuildMassProperties(shape)[1];
  const bodies = [];
  for (let i = 0; i < 300; i++) {
    const [, b] = HK.HP_Body_Create();
    HK.HP_Body_SetShape(b, shape);
    HK.HP_Body_SetMotionType(b, HK.MotionType.DYNAMIC);
    HK.HP_Body_SetMassProperties(b, mass);
    HK.HP_Body_SetQTransform(b, [[(i % 10) * 0.6 - 3, 1 + Math.floor(i / 10 % 6) * 0.6, Math.floor(i / 60) * 0.6 - 1.5], [0, 0, 0, 1]]);
    HK.HP_World_AddBody(world, b, false);
    bodies.push(b);
  }
  const t = performance.now();
  for (let i = 0; i < 180; i++) HK.HP_World_Step(world, 1 / 60);
  const ms = performance.now() - t;
  console.log(JSON.stringify({ pile300: { totalMs: +ms.toFixed(1), msPerStep: +(ms / 180).toFixed(2), highestBoxY: +Math.max(...bodies.map((b) => HK.HP_Body_GetQTransform(b)[1][0][1])).toFixed(2) } }));
}
