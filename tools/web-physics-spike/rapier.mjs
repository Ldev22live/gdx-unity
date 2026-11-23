// Spike: Rapier (@dimforge/rapier3d-compat). Drops a sphere on a static box, lists the API the desktop backend relies on.
import { pathToFileURL } from 'node:url';

const root = process.argv[2];
const mod = await import(pathToFileURL(`${root}/node_modules/@dimforge/rapier3d-compat/dist/rapier.mjs`).href);
const RAPIER = mod.default ?? mod;
const t0 = performance.now();
await RAPIER.init();
const loadMs = performance.now() - t0;

const world = new RAPIER.World({ x: 0, y: -9.81, z: 0 });
world.timestep = 1 / 60;
world.createCollider(RAPIER.ColliderDesc.cuboid(50, 0.5, 50).setTranslation(0, -0.5, 0));
const ball = world.createRigidBody(RAPIER.RigidBodyDesc.dynamic().setTranslation(0, 5, 0));
world.createCollider(RAPIER.ColliderDesc.ball(0.5), ball);
const steps = 240;
const t1 = performance.now();
for (let i = 0; i < steps; i++) world.step();
const stepMs = performance.now() - t1;

const api = {
  revoluteJoint: typeof RAPIER.JointData?.revolute === 'function',
  prismaticJoint: typeof RAPIER.JointData?.prismatic === 'function',
  fixedJoint: typeof RAPIER.JointData?.fixed === 'function',
  sphericalJoint: typeof RAPIER.JointData?.spherical === 'function',
  ropeJointAsDistance: typeof RAPIER.JointData?.rope === 'function',
  trimesh: typeof RAPIER.ColliderDesc?.trimesh === 'function',
  convexHull: typeof RAPIER.ColliderDesc?.convexHull === 'function',
  capsule: typeof RAPIER.ColliderDesc?.capsule === 'function',
  characterController: typeof world.createCharacterController === 'function',
  castRay: typeof world.castRay === 'function',
  intersectionsWithShape: typeof world.intersectionsWithShape === 'function',
  eventQueue: typeof RAPIER.EventQueue === 'function',
  jointMotors: typeof world.createImpulseJoint === 'function',
};
console.log(JSON.stringify({ engine: 'rapier3d-compat ' + (RAPIER.version?.() ?? ''), loadMs: Math.round(loadMs), stepsMsTotal: +stepMs.toFixed(2), steps,
  finalBallY: +ball.translation().y.toFixed(3), expectedRestY: 0.5, api }, null, 1));

{
  const bodies = [];
  for (let i = 0; i < 300; i++) {
    const b = world.createRigidBody(RAPIER.RigidBodyDesc.dynamic().setTranslation((i % 10) * 0.6 - 3, 1 + Math.floor(i / 10 % 6) * 0.6, Math.floor(i / 60) * 0.6 - 1.5));
    world.createCollider(RAPIER.ColliderDesc.cuboid(0.25, 0.25, 0.25), b);
    bodies.push(b);
  }
  const t = performance.now();
  for (let i = 0; i < 180; i++) world.step();
  const ms = performance.now() - t;
  console.log(JSON.stringify({ pile300: { totalMs: +ms.toFixed(1), msPerStep: +(ms / 180).toFixed(2), highestBoxY: +Math.max(...bodies.map((b) => b.translation().y)).toFixed(2) } }));
}
