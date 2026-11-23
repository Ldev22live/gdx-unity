// Spike: Jolt Physics JavaScript build (jolt-physics npm). Drops a sphere on a static box, lists the API the desktop backend relies on.
import { pathToFileURL } from 'node:url';

const root = process.argv[2];
const mod = await import(pathToFileURL(`${root}/node_modules/jolt-physics/dist/jolt-physics.wasm-compat.js`).href);
const t0 = performance.now();
const Jolt = await (mod.default ?? mod)();
const loadMs = performance.now() - t0;

const wanted = ['HingeConstraintSettings', 'SliderConstraintSettings', 'DistanceConstraintSettings', 'FixedConstraintSettings',
  'PointConstraintSettings', 'MeshShapeSettings', 'ConvexHullShapeSettings', 'CharacterVirtualSettings', 'CharacterVirtual',
  'ContactListenerJS', 'BodyCreationSettings', 'CapsuleShapeSettings', 'SphereShapeSettings', 'BoxShapeSettings',
  'MotorSettings', 'RayCast', 'BroadPhaseLayerInterfaceTable', 'ObjectLayerPairFilterTable'];
const present = Object.fromEntries(wanted.map((n) => [n, typeof Jolt[n] !== 'undefined']));

const settings = new Jolt.JoltSettings();
const NON_MOVING = 0, MOVING = 1;
const filter = new Jolt.ObjectLayerPairFilterTable(2);
filter.EnableCollision(NON_MOVING, MOVING);
filter.EnableCollision(MOVING, MOVING);
const bp = new Jolt.BroadPhaseLayerInterfaceTable(2, 2);
bp.MapObjectToBroadPhaseLayer(NON_MOVING, new Jolt.BroadPhaseLayer(0));
bp.MapObjectToBroadPhaseLayer(MOVING, new Jolt.BroadPhaseLayer(1));
settings.mObjectLayerPairFilter = filter;
settings.mBroadPhaseLayerInterface = bp;
settings.mObjectVsBroadPhaseLayerFilter = new Jolt.ObjectVsBroadPhaseLayerFilterTable(settings.mBroadPhaseLayerInterface, 2, settings.mObjectLayerPairFilter, 2);
const jolt = new Jolt.JoltInterface(settings);
const system = jolt.GetPhysicsSystem();
const bi = system.GetBodyInterface();

const floorShape = new Jolt.BoxShape(new Jolt.Vec3(50, 0.5, 50), 0.05, null);
const floor = bi.CreateBody(new Jolt.BodyCreationSettings(floorShape, new Jolt.RVec3(0, -0.5, 0), Jolt.Quat.prototype.sIdentity(), Jolt.EMotionType_Static, NON_MOVING));
bi.AddBody(floor.GetID(), Jolt.EActivation_DontActivate);
const ball = bi.CreateBody(new Jolt.BodyCreationSettings(new Jolt.SphereShape(0.5), new Jolt.RVec3(0, 5, 0), Jolt.Quat.prototype.sIdentity(), Jolt.EMotionType_Dynamic, MOVING));
bi.AddBody(ball.GetID(), Jolt.EActivation_Activate);

const steps = 240;
const t1 = performance.now();
for (let i = 0; i < steps; i++) jolt.Step(1 / 60, 1);
const stepMs = performance.now() - t1;
const y = ball.GetPosition().GetY();

console.log(JSON.stringify({ engine: 'jolt-physics (wasm-compat)', loadMs: Math.round(loadMs), stepsMsTotal: +stepMs.toFixed(2), steps, finalBallY: +y.toFixed(3), expectedRestY: 0.5, api: present }, null, 1));

// Pile benchmark: 300 boxes dropped in a 10 x 6 x 5 grid, 180 steps
{
  const boxShape = new Jolt.BoxShape(new Jolt.Vec3(0.25, 0.25, 0.25), 0.05, null);
  const ids = [];
  for (let i = 0; i < 300; i++) {
    const b = bi.CreateBody(new Jolt.BodyCreationSettings(boxShape, new Jolt.RVec3((i % 10) * 0.6 - 3, 1 + Math.floor(i / 10 % 6) * 0.6, Math.floor(i / 60) * 0.6 - 1.5), Jolt.Quat.prototype.sIdentity(), Jolt.EMotionType_Dynamic, MOVING));
    bi.AddBody(b.GetID(), Jolt.EActivation_Activate);
    ids.push(b);
  }
  const t = performance.now();
  for (let i = 0; i < 180; i++) jolt.Step(1 / 60, 1);
  const ms = performance.now() - t;
  const maxY = Math.max(...ids.map((b) => b.GetPosition().GetY()));
  console.log(JSON.stringify({ pile300: { totalMs: +ms.toFixed(1), msPerStep: +(ms / 180).toFixed(2), highestBoxY: +maxY.toFixed(2) } }));
}
