package io.github.ldev22live.gdxunity.engine.physics;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.github.stephengold.joltjni.AllHitCastRayCollector;
import com.github.stephengold.joltjni.AllHitCollideShapeCollector;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyFilter;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BodyLockMultiWrite;
import com.github.stephengold.joltjni.BodyLockRead;
import com.github.stephengold.joltjni.BodyLockWrite;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.CapsuleShapeSettings;
import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.Float3;
import com.github.stephengold.joltjni.IndexedTriangle;
import com.github.stephengold.joltjni.IndexedTriangleList;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.VertexList;
import com.github.stephengold.joltjni.CharacterVirtual;
import com.github.stephengold.joltjni.CharacterVirtualSettings;
import com.github.stephengold.joltjni.CollideShapeResult;
import com.github.stephengold.joltjni.CollideShapeSettings;
import com.github.stephengold.joltjni.CustomContactListener;
import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.DefaultBroadPhaseLayerFilter;
import com.github.stephengold.joltjni.DefaultObjectLayerFilter;
import com.github.stephengold.joltjni.ExtendedUpdateSettings;
import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.HingeConstraintSettings;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Plane;
import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RRayCast;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.RayCastResult;
import com.github.stephengold.joltjni.RayCastSettings;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.ShapeFilter;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.SliderConstraintSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.SubShapeIdPair;
import com.github.stephengold.joltjni.TempAllocatorMalloc;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EGroundState;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.ValidateResult;
import com.github.stephengold.joltjni.readonly.ConstCharacterContact;
import com.github.stephengold.joltjni.readonly.ConstShape;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BodyDescriptor3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterDescriptor3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.JointDescriptor3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsBackend3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ShapeDescriptor3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link PhysicsBackend3D} on Jolt Physics through jolt-jni (TD-028). This is the only class that sees Jolt types.
 * <p>
 * How the engine's model maps onto Jolt:
 * <ul>
 *   <li><b>Layers.</b> Jolt filters by object layer pairs, but the engine's masks belong to each body. Each body gets
 *       object layer {@code 2 * layer + (moving ? 1 : 0)}, which lets Jolt skip static-vs-static pairs in the broad phase,
 *       and the exact rule {@link PhysicsLayers3D#collide} is applied in the contact validation callback.</li>
 *   <li><b>Contacts.</b> Jolt reports sub-shape pairs; the engine wants body pairs. A pair counts as touching from its
 *       first sub-shape contact until its last one ends. Sensors report the same way. Bodies never sleep ({@link #ALLOW_SLEEPING}).</li>
 *   <li><b>Threads.</b> The simulation runs on the calling thread ({@value #WORKER_THREADS} helper threads), so contact
 *       callbacks, which Jolt makes from the stepping threads, arrive on the caller's thread. Raise it only after
 *       profiling shows a need and the callbacks have been checked for thread safety.</li>
 *   <li><b>Handles.</b> A body's handle is its Jolt body id.</li>
 *   <li><b>Characters (TD-049).</b> Each is a Jolt {@code CharacterVirtual} with an inner body, so other bodies collide with
 *       it and rays find it. Its handle is a negative number, never a body id. The inner body's contacts are reported under
 *       the character's handle, and so are the bodies the character itself reports touching, which Jolt does not mirror into
 *       body contacts when the other body is static.</li>
 *   <li><b>Joints (TD-051).</b> Each is a Jolt two-body constraint made in world space, so the pose both bodies have when the
 *       joint is added is its rest pose. Handles count up from 1. Joined bodies that must not collide are listed in
 *       {@code noCollidePairs} and refused in the contact validation callback. A body with a joint cannot be removed before
 *       the joint: that is an error here instead of a native crash.</li>
 * </ul>
 * Not thread safe, like the interface it implements. Jolt's native library is loaded by {@link JoltNatives}.
 */
public final class JoltPhysicsBackend3D implements PhysicsBackend3D {
    /** Helper threads of the job system; 0 runs everything on the thread that calls {@link #step}. */
    static final int WORKER_THREADS = 0;
    /** Object layers: two per engine layer, see the class comment. */
    static final int OBJECT_LAYERS = PhysicsLayers3D.LAYER_COUNT * 2;
    /** Broad phase layer of static bodies. */
    private static final int BROAD_PHASE_STATIC = 0;
    /** Broad phase layer of kinematic and dynamic bodies. */
    private static final int BROAD_PHASE_MOVING = 1;
    private static final int BROAD_PHASE_LAYERS = 2;
    /** Most bodies the system holds at once. */
    private static final int MAX_BODIES = 10_240;
    /** Mutexes protecting bodies; 0 lets Jolt choose. */
    private static final int BODY_MUTEXES = 0;
    /** Most body pairs and contact constraints in flight at once. */
    private static final int MAX_BODY_PAIRS = 65_536;
    private static final int MAX_CONTACT_CONSTRAINTS = 20_480;
    /** Bits of a body's user data that hold its collision layer; the mask follows. */
    private static final int LAYER_BITS = 8;
    private static final long LAYER_FIELD = (1L << LAYER_BITS) - 1;
    /** Bit above the layer and mask fields that marks a sensor. */
    private static final int SENSOR_BIT = LAYER_BITS + PhysicsLayers3D.LAYER_COUNT;
    /**
     * Whether resting bodies may fall asleep. Jolt reports a sleeping body's contacts as ended, which scripts would see as
     * leaving the ground or a trigger while standing still, so bodies stay awake. The cost is simulating resting bodies;
     * revisit with profiling if scenes with many of them need it.
     */
    private static final boolean ALLOW_SLEEPING = false;
    /** Handle of the first character; the next ones count down, so they never equal a body id. */
    private static final int FIRST_CHARACTER_HANDLE = -2;
    /** A character reports touching a body when the gap between them is at most this many meters. */
    private static final float TOUCH_DISTANCE = 0.05f;
    /** Collision steps per {@link #step}: the engine already steps at a fixed 1/60 s. */
    private static final int COLLISION_STEPS = 1;

    static {
        // Before any field below is created: they are native objects
        JoltNatives.load();
    }

    private final PhysicsSystem system;
    private final BodyInterface bodies;
    private final TempAllocatorMalloc tempAllocator = new TempAllocatorMalloc();
    private final JobSystemThreadPool jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, WORKER_THREADS);
    private final BroadPhaseLayerInterfaceTable broadPhaseLayers = new BroadPhaseLayerInterfaceTable(OBJECT_LAYERS, BROAD_PHASE_LAYERS);
    private final ObjectLayerPairFilterTable layerPairs = new ObjectLayerPairFilterTable(OBJECT_LAYERS);
    private final ObjectVsBroadPhaseLayerFilterTable layersVsBroadPhase;
    private final Contacts contacts = new Contacts();
    private final Object eventLock = new Object();
    private final List<ContactEvent> events = new ArrayList<>();
    /** Sub-shape contacts alive per body pair (key: both body ids), see {@link Contacts}. */
    private final Map<Long, Integer> pairContacts = new HashMap<>();
    /** Handles of the bodies that exist: Jolt does not check an id, and a stale one can crash the process. */
    private final Set<Integer> liveBodies = new HashSet<>();
    private final Map<Integer, JoltCharacter> characters = new HashMap<>();
    private final Map<Integer, JoltJoint> joints = new HashMap<>();
    /** Body pairs (see {@link #pairKey}) that are joined and must not collide with each other. */
    private final Set<Long> noCollidePairs = new HashSet<>();
    /** Character handle by the Jolt id of its inner body. */
    private final Map<Integer, Integer> characterByInnerBody = new HashMap<>();
    private final Vec3 gravity = new Vec3(0f, -9.81f, 0f);
    private int nextCharacterHandle = FIRST_CHARACTER_HANDLE;
    private int nextJointHandle = 1;
    private boolean disposed;

    /** A character and the native helpers it needs for every update; closed with it. */
    private final class JoltCharacter implements AutoCloseable {
        final int handle;
        final CharacterVirtual virtual;
        final ExtendedUpdateSettings update = new ExtendedUpdateSettings();
        final BodyFilter bodyFilter = new BodyFilter();
        final ShapeFilter shapeFilter = new ShapeFilter();
        final DefaultBroadPhaseLayerFilter broadPhaseFilter;
        final DefaultObjectLayerFilter objectFilter;
        final int innerBody;
        final Set<Integer> touching = new HashSet<>();

        JoltCharacter(int handle, CharacterVirtual virtual, int movingObjectLayer) {
            this.handle = handle;
            this.virtual = virtual;
            this.broadPhaseFilter = system.getDefaultBroadPhaseLayerFilter(movingObjectLayer);
            this.objectFilter = system.getDefaultLayerFilter(movingObjectLayer);
            this.innerBody = virtual.getInnerBodyId();
        }

        @Override
        public void close() {
            objectFilter.close();
            broadPhaseFilter.close();
            shapeFilter.close();
            bodyFilter.close();
            update.close();
            virtual.close();
        }
    }

    /** A joint: the Jolt constraint and what is needed to undo it. {@code noCollideKey} is 0 when the bodies may collide. */
    private record JoltJoint(TwoBodyConstraint constraint, Joint3DComponent.Type type, int bodyA, int bodyB, long noCollideKey) {}

    public JoltPhysicsBackend3D() {
        for (int layer = 0; layer < PhysicsLayers3D.LAYER_COUNT; layer++) {
            broadPhaseLayers.mapObjectToBroadPhaseLayer(objectLayer(layer, false), BROAD_PHASE_STATIC);
            broadPhaseLayers.mapObjectToBroadPhaseLayer(objectLayer(layer, true), BROAD_PHASE_MOVING);
        }
        // Everything may meet everything except two static bodies; the masks are checked in Contacts.onContactValidate
        for (int a = 0; a < OBJECT_LAYERS; a++) {
            for (int b = a; b < OBJECT_LAYERS; b++) {
                if (isMovingLayer(a) || isMovingLayer(b)) {
                    layerPairs.enableCollision(a, b);
                } else {
                    layerPairs.disableCollision(a, b);
                }
            }
        }
        // jolt-jni takes the two counts in the order object layers, broad phase layers, unlike Jolt's C++ constructor
        // (checked: with the C++ order only the first two object layers filter correctly, JoltPhysicsBackend3DTest)
        layersVsBroadPhase = new ObjectVsBroadPhaseLayerFilterTable(broadPhaseLayers, OBJECT_LAYERS, layerPairs, BROAD_PHASE_LAYERS);
        system = new PhysicsSystem();
        system.init(MAX_BODIES, BODY_MUTEXES, MAX_BODY_PAIRS, MAX_CONTACT_CONSTRAINTS, broadPhaseLayers, layersVsBroadPhase, layerPairs);
        system.setContactListener(contacts);
        bodies = system.getBodyInterface();
    }

    @Override
    public void setGravity(Vector3 gravity) {
        requireOpen();
        Objects.requireNonNull(gravity, "gravity cannot be null");
        system.setGravity(gravity.x, gravity.y, gravity.z);
        this.gravity.set(gravity.x, gravity.y, gravity.z);
    }

    @Override
    public int addBody(BodyDescriptor3D body) {
        requireOpen();
        Objects.requireNonNull(body, "body cannot be null");
        if (body.layer() < 0 || body.layer() >= PhysicsLayers3D.LAYER_COUNT) {
            throw new IllegalArgumentException("layer must be between 0 and " + (PhysicsLayers3D.LAYER_COUNT - 1) + ": " + body.layer());
        }
        boolean moving = body.motionType() != RigidBody3DComponent.MotionType.STATIC;
        try (ShapeSettings shape = shapeOf(body.shapes());
             BodyCreationSettings settings = new BodyCreationSettings()) {
            ShapeResult result = shape.create();
            if (result.hasError()) {
                throw new IllegalArgumentException("Jolt rejected the collision shape: " + result.getError());
            }
            settings.setShape(result.get());
            settings.setPosition(new RVec3(body.position().x, body.position().y, body.position().z));
            settings.setRotation(new Quat(body.rotation().x, body.rotation().y, body.rotation().z, body.rotation().w));
            settings.setMotionType(motionTypeOf(body.motionType()));
            settings.setObjectLayer(objectLayer(body.layer(), moving));
            settings.setIsSensor(body.sensor());
            settings.setAllowSleeping(ALLOW_SLEEPING);
            settings.setFriction(body.friction());
            settings.setRestitution(body.restitution());
            settings.setLinearDamping(body.linearDamping());
            settings.setAngularDamping(body.angularDamping());
            settings.setGravityFactor(body.gravityFactor());
            if (body.motionType() == RigidBody3DComponent.MotionType.DYNAMIC) {
                // Keep the shape's inertia but give it the component's mass
                MassProperties mass = new MassProperties();
                mass.setMass(body.mass());
                settings.setMassPropertiesOverride(mass);
                settings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
            }
            settings.setUserData(pack(body.layer(), body.collisionMask(), body.sensor()));
            int id = bodies.createAndAddBody(settings, moving ? EActivation.Activate : EActivation.DontActivate);
            if (id == Jolt.cInvalidBodyId) {
                throw new IllegalStateException("Jolt could not add the body (more than " + MAX_BODIES + " bodies?)");
            }
            liveBodies.add(id);
            return id;
        }
    }

    @Override
    public void removeBody(int handle) {
        requireBody(handle);
        for (JoltJoint joint : joints.values()) {
            if (joint.bodyA() == handle || joint.bodyB() == handle) {
                throw new IllegalStateException("Body " + handle + " still has a joint; remove the joint first");
            }
        }
        liveBodies.remove(handle);
        bodies.removeBody(handle);
        bodies.destroyBody(handle);
        endContactsOf(handle);
    }

    /** Jolt does not report the contacts of a removed body ending: do it here so listeners can let go. */
    private void endContactsOf(int handle) {
        synchronized (eventLock) {
            Iterator<Map.Entry<Long, Integer>> pairs = pairContacts.entrySet().iterator();
            while (pairs.hasNext()) {
                long key = pairs.next().getKey();
                int first = (int) (key >>> 32);
                int second = (int) key;
                if (first == handle || second == handle) {
                    events.add(new ContactEvent(first, second, false));
                    pairs.remove();
                }
            }
        }
    }

    @Override
    public void moveKinematic(int handle, Vector3 position, Quaternion rotation, float deltaSeconds) {
        requireBody(handle);
        bodies.moveKinematic(handle, new RVec3(position.x, position.y, position.z),
            new Quat(rotation.x, rotation.y, rotation.z, rotation.w), deltaSeconds);
    }

    @Override
    public void getBodyTransform(int handle, Vector3 outPosition, Quaternion outRotation) {
        requireBody(handle);
        RVec3 position = new RVec3();
        Quat rotation = new Quat();
        bodies.getPositionAndRotation(handle, position, rotation);
        outPosition.set((float) position.xx(), (float) position.yy(), (float) position.zz());
        outRotation.set(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW());
    }

    @Override
    public void step(float deltaSeconds) {
        requireOpen();
        int error = system.update(deltaSeconds, COLLISION_STEPS, tempAllocator, jobSystem);
        if (error != 0) {
            throw new IllegalStateException("Jolt reported a physics error (code " + error + "): the scene has too many bodies or contacts");
        }
    }

    @Override
    public void drainContactEvents(List<ContactEvent> out) {
        synchronized (eventLock) {
            out.addAll(events);
            events.clear();
        }
    }

    @Override
    public RayHit raycast(Vector3 origin, Vector3 direction, float maxDistance, int layerMask) {
        requireOpen();
        Vector3 along = new Vector3(direction).nor();
        RRayCast ray = new RRayCast(new RVec3(origin.x, origin.y, origin.z), new Vec3(along.x * maxDistance, along.y * maxDistance, along.z * maxDistance));
        try (RayCastSettings settings = new RayCastSettings(); AllHitCastRayCollector hits = new AllHitCastRayCollector()) {
            system.getNarrowPhaseQuery().castRay(ray, settings, hits);
            RayCastResult closest = null;
            for (RayCastResult hit : hits.getHits()) {
                long data = bodies.getUserData(hit.getBodyId());
                boolean allowed = !isSensor(data) && (layerMask & (1 << layerOf(data))) != 0;
                if (allowed && (closest == null || hit.getFraction() < closest.getFraction())) {
                    closest = hit;
                }
            }
            if (closest == null) {
                return null;
            }
            float distance = closest.getFraction() * maxDistance;
            Vector3 point = new Vector3(origin).mulAdd(along, distance);
            return new RayHit(handleOf(closest.getBodyId()), point, normalAt(closest.getBodyId(), closest.getSubShapeId2(), point), distance);
        }
    }

    @Override
    public void addForce(int handle, Vector3 force) {
        requireBody(handle);
        bodies.addForce(handle, new Vec3(force.x, force.y, force.z));
    }

    @Override
    public void addTorque(int handle, Vector3 torque) {
        requireBody(handle);
        bodies.addTorque(handle, new Vec3(torque.x, torque.y, torque.z));
    }

    @Override
    public void addImpulse(int handle, Vector3 impulse) {
        requireBody(handle);
        bodies.addImpulse(handle, new Vec3(impulse.x, impulse.y, impulse.z));
    }

    @Override
    public void setLinearVelocity(int handle, Vector3 velocity) {
        requireBody(handle);
        bodies.setLinearVelocity(handle, new Vec3(velocity.x, velocity.y, velocity.z));
    }

    @Override
    public void getLinearVelocity(int handle, Vector3 outVelocity) {
        requireBody(handle);
        Vec3 velocity = bodies.getLinearVelocity(handle);
        outVelocity.set(velocity.getX(), velocity.getY(), velocity.getZ());
    }

    @Override
    public void setAngularVelocity(int handle, Vector3 velocity) {
        requireBody(handle);
        bodies.setAngularVelocity(handle, new Vec3(velocity.x, velocity.y, velocity.z));
    }

    @Override
    public void getAngularVelocity(int handle, Vector3 outVelocity) {
        requireBody(handle);
        Vec3 velocity = bodies.getAngularVelocity(handle);
        outVelocity.set(velocity.getX(), velocity.getY(), velocity.getZ());
    }

    @Override
    public void overlapSphere(Vector3 center, float radius, int layerMask, List<Integer> outBodies) {
        requireOpen();
        try (ShapeSettings settings = new SphereShapeSettings(radius);
             CollideShapeSettings collide = new CollideShapeSettings();
             AllHitCollideShapeCollector hits = new AllHitCollideShapeCollector()) {
            ShapeResult result = settings.create();
            if (result.hasError()) {
                throw new IllegalArgumentException("Jolt rejected the query sphere: " + result.getError());
            }
            ConstShape sphere = result.get().getPtr();
            // The sphere's own origin is its center: the transform places it, so no base offset is needed
            system.getNarrowPhaseQuery().collideShape(sphere, new Vec3(1f, 1f, 1f),
                RMat44.sTranslation(new RVec3(center.x, center.y, center.z)), collide, new RVec3(), hits);
            Set<Integer> seen = new HashSet<>();
            for (CollideShapeResult hit : hits.getHits()) {
                int id = hit.getBodyId2();
                long data = bodies.getUserData(id);
                int handle = handleOf(id);
                if (!isSensor(data) && (layerMask & (1 << layerOf(data))) != 0 && seen.add(handle)) {
                    outBodies.add(handle);
                }
            }
        }
    }

    // --- Characters (TD-049) ---

    @Override
    public int addCharacter(CharacterDescriptor3D description) {
        requireOpen();
        Objects.requireNonNull(description, "description cannot be null");
        if (description.layer() < 0 || description.layer() >= PhysicsLayers3D.LAYER_COUNT) {
            throw new IllegalArgumentException("layer must be between 0 and " + (PhysicsLayers3D.LAYER_COUNT - 1) + ": " + description.layer());
        }
        int movingLayer = objectLayer(description.layer(), true);
        try (ShapeSettings shapeSettings = new CapsuleShapeSettings(description.halfHeight(), description.radius());
             CharacterVirtualSettings settings = new CharacterVirtualSettings()) {
            ShapeResult result = shapeSettings.create();
            if (result.hasError()) {
                throw new IllegalArgumentException("Jolt rejected the character shape: " + result.getError());
            }
            ConstShape capsule = result.get().getPtr();
            settings.setShape(capsule);
            settings.setInnerBodyShape(capsule);
            settings.setInnerBodyLayer(movingLayer);
            settings.setUp(new Vec3(0f, 1f, 0f));
            settings.setMaxSlopeAngle(description.slopeRadians());
            settings.setMass(description.mass());
            // Only the capsule's lower sphere can support the character; the plane is relative to the capsule's center
            settings.setSupportingVolume(new Plane(new Vec3(0f, 1f, 0f), description.halfHeight() - description.radius()));
            Vector3 at = description.position();
            CharacterVirtual virtual = new CharacterVirtual(settings, new RVec3(at.x, at.y, at.z), new Quat(0f, 0f, 0f, 1f), 0L, system);
            int handle = nextCharacterHandle--;
            JoltCharacter character = new JoltCharacter(handle, virtual, movingLayer);
            character.update.setStickToFloorStepDown(new Vec3(0f, -description.stepHeight(), 0f));
            character.update.setWalkStairsStepUp(new Vec3(0f, description.stepHeight(), 0f));
            // Without this the inner body has no layer or mask and the contact filter would treat it as layer 0
            bodies.setUserData(character.innerBody, pack(description.layer(), description.collisionMask(), false));
            characters.put(handle, character);
            characterByInnerBody.put(character.innerBody, handle);
            return handle;
        }
    }

    @Override
    public void removeCharacter(int handle) {
        JoltCharacter character = requireCharacter(handle);
        characters.remove(handle);
        characterByInnerBody.remove(character.innerBody);
        character.close();
        endContactsOf(handle);
    }

    @Override
    public void moveCharacter(int handle, Vector3 velocity, float deltaSeconds) {
        JoltCharacter character = requireCharacter(handle);
        character.virtual.setLinearVelocity(velocity.x, velocity.y, velocity.z);
        character.virtual.extendedUpdate(deltaSeconds, gravity, character.update, character.broadPhaseFilter, character.objectFilter,
            character.bodyFilter, character.shapeFilter, tempAllocator);
        reportCharacterContacts(character);
    }

    @Override
    public void setCharacterPosition(int handle, Vector3 position) {
        requireCharacter(handle).virtual.setPosition(new RVec3(position.x, position.y, position.z));
    }

    @Override
    public void getCharacterPosition(int handle, Vector3 outPosition) {
        RVec3 position = requireCharacter(handle).virtual.getPosition();
        outPosition.set((float) position.xx(), (float) position.yy(), (float) position.zz());
    }

    @Override
    public boolean isCharacterGrounded(int handle) {
        return requireCharacter(handle).virtual.getGroundState() == EGroundState.OnGround;
    }

    // --- Joints (TD-051) ---

    @Override
    public int addJoint(JointDescriptor3D joint) {
        requireOpen();
        Objects.requireNonNull(joint, "joint cannot be null");
        requireBody(joint.bodyA());
        boolean toWorld = joint.bodyB() == JointDescriptor3D.WORLD;
        if (!toWorld) {
            requireBody(joint.bodyB());
        }
        try (TwoBodyConstraintSettings settings = jointSettings(joint)) {
            // Jolt measures a hinge angle or slider position of body 2 relative to body 1, so the connected body (or the world) is
            // body 1: a positive position then means this entity moved along the axis
            TwoBodyConstraint constraint;
            if (toWorld) {
                try (BodyLockWrite lock = new BodyLockWrite(system.getBodyLockInterface(), joint.bodyA())) {
                    constraint = settings.create(Body.sFixedToWorld(), lock.getBody());
                }
            } else {
                try (BodyLockMultiWrite lock = new BodyLockMultiWrite(system.getBodyLockInterface(), joint.bodyB(), joint.bodyA())) {
                    constraint = settings.create(lock.getBody(0), lock.getBody(1));
                }
            }
            if (constraint == null) {
                throw new IllegalArgumentException("Jolt could not make the " + joint.type() + " joint");
            }
            system.addConstraint(constraint);
            long noCollideKey = 0L;
            if (!joint.collideConnected() && !toWorld) {
                noCollideKey = pairKey(joint.bodyA(), joint.bodyB());
                noCollidePairs.add(noCollideKey);
            }
            int handle = nextJointHandle++;
            JoltJoint made = new JoltJoint(constraint, joint.type(), joint.bodyA(), joint.bodyB(), noCollideKey);
            joints.put(handle, made);
            applyMotor(made, joint.motorMode(), joint.motorTarget(), joint.motorMaxForce());
            return handle;
        }
    }

    @Override
    public void removeJoint(int handle) {
        requireOpen();
        JoltJoint joint = joints.remove(handle);
        if (joint == null) {
            throw new IllegalArgumentException("No such joint (removed already?): " + handle);
        }
        if (joint.noCollideKey() != 0L) {
            noCollidePairs.remove(joint.noCollideKey());
        }
        system.removeConstraint(joint.constraint());
        joint.constraint().close();
    }

    @Override
    public void setJointMotor(int handle, Joint3DComponent.MotorMode mode, float target, float maxForce) {
        applyMotor(requireJoint(handle), mode, target, maxForce);
    }

    @Override
    public float getJointPosition(int handle) {
        JoltJoint joint = requireJoint(handle);
        if (joint.constraint() instanceof HingeConstraint hinge) {
            return hinge.getCurrentAngle();
        }
        if (joint.constraint() instanceof SliderConstraint slider) {
            return slider.getCurrentPosition();
        }
        throw new IllegalStateException("A " + joint.type() + " joint has no position; only a HINGE or SLIDER has");
    }

    private JoltJoint requireJoint(int handle) {
        requireOpen();
        JoltJoint joint = joints.get(handle);
        if (joint == null) {
            throw new IllegalArgumentException("No such joint (removed already?): " + handle);
        }
        return joint;
    }

    private static void applyMotor(JoltJoint joint, Joint3DComponent.MotorMode mode, float target, float maxForce) {
        Objects.requireNonNull(mode, "mode cannot be null");
        if (joint.constraint() instanceof HingeConstraint hinge) {
            hinge.getMotorSettings().setTorqueLimit(maxForce);
            switch (mode) {
                case OFF -> hinge.setMotorState(EMotorState.Off);
                case VELOCITY -> {
                    hinge.setTargetAngularVelocity(target);
                    hinge.setMotorState(EMotorState.Velocity);
                }
                case POSITION -> {
                    hinge.setTargetAngle(target);
                    hinge.setMotorState(EMotorState.Position);
                }
            }
        } else if (joint.constraint() instanceof SliderConstraint slider) {
            slider.getMotorSettings().setForceLimit(maxForce);
            switch (mode) {
                case OFF -> slider.setMotorState(EMotorState.Off);
                case VELOCITY -> {
                    slider.setTargetVelocity(target);
                    slider.setMotorState(EMotorState.Velocity);
                }
                case POSITION -> {
                    slider.setTargetPosition(target);
                    slider.setMotorState(EMotorState.Position);
                }
            }
        } else if (mode != Joint3DComponent.MotorMode.OFF) {
            throw new IllegalStateException("A " + joint.type() + " joint has no motor; only a HINGE or SLIDER has");
        }
    }

    /** Settings in world space: Jolt turns the points and axes into each body's local frame, which makes the current pose the rest pose. */
    private static TwoBodyConstraintSettings jointSettings(JointDescriptor3D joint) {
        Vector3 anchor = joint.anchor();
        Vector3 axis = new Vector3(joint.axis()).nor();
        switch (joint.type()) {
            case FIXED -> {
                FixedConstraintSettings settings = new FixedConstraintSettings();
                settings.setSpace(EConstraintSpace.WorldSpace);
                settings.setPoint1(anchor.x, anchor.y, anchor.z);
                settings.setPoint2(anchor.x, anchor.y, anchor.z);
                settings.setAxisX1(1f, 0f, 0f);
                settings.setAxisX2(1f, 0f, 0f);
                settings.setAxisY1(0f, 1f, 0f);
                settings.setAxisY2(0f, 1f, 0f);
                return settings;
            }
            case HINGE -> {
                Vector3 normal = perpendicularTo(axis);
                HingeConstraintSettings settings = new HingeConstraintSettings();
                settings.setSpace(EConstraintSpace.WorldSpace);
                settings.setPoint1(anchor.x, anchor.y, anchor.z);
                settings.setPoint2(anchor.x, anchor.y, anchor.z);
                settings.setHingeAxis1(axis.x, axis.y, axis.z);
                settings.setHingeAxis2(axis.x, axis.y, axis.z);
                settings.setNormalAxis1(normal.x, normal.y, normal.z);
                settings.setNormalAxis2(normal.x, normal.y, normal.z);
                settings.setLimitsMin(joint.limited() ? joint.lower() : (float) -Math.PI);
                settings.setLimitsMax(joint.limited() ? joint.upper() : (float) Math.PI);
                return settings;
            }
            case SLIDER -> {
                SliderConstraintSettings settings = new SliderConstraintSettings();
                settings.setSpace(EConstraintSpace.WorldSpace);
                settings.setPoint1(anchor.x, anchor.y, anchor.z);
                settings.setPoint2(anchor.x, anchor.y, anchor.z);
                settings.setSliderAxis(axis.x, axis.y, axis.z);
                if (joint.limited()) {
                    settings.setLimitsMin(joint.lower());
                    settings.setLimitsMax(joint.upper());
                }
                return settings;
            }
            case DISTANCE -> {
                Vector3 other = joint.anchorB();
                DistanceConstraintSettings settings = new DistanceConstraintSettings();
                settings.setSpace(EConstraintSpace.WorldSpace);
                // Point 1 belongs to body 1, the connected body
                settings.setPoint1(other.x, other.y, other.z);
                settings.setPoint2(anchor.x, anchor.y, anchor.z);
                float now = anchor.dst(other);
                settings.setMinDistance(joint.limited() ? joint.lower() : now);
                settings.setMaxDistance(joint.limited() ? joint.upper() : now);
                return settings;
            }
            case BALL -> {
                PointConstraintSettings settings = new PointConstraintSettings();
                settings.setSpace(EConstraintSpace.WorldSpace);
                settings.setPoint1(anchor.x, anchor.y, anchor.z);
                settings.setPoint2(anchor.x, anchor.y, anchor.z);
                return settings;
            }
        }
        throw new IllegalArgumentException("Unsupported joint type: " + joint.type());
    }

    /** Any unit vector at a right angle to {@code axis}; the hinge measures its angle from it. */
    private static Vector3 perpendicularTo(Vector3 axis) {
        Vector3 helper = Math.abs(axis.x) < 0.9f ? new Vector3(1f, 0f, 0f) : new Vector3(0f, 1f, 0f);
        return new Vector3(axis).crs(helper).nor();
    }

    /** Turns the bodies the character touches now, compared with the last update, into contact events under its handle. */
    private void reportCharacterContacts(JoltCharacter character) {
        Set<Integer> now = new HashSet<>();
        for (ConstCharacterContact contact : character.virtual.getActiveContacts()) {
            int other = contact.getBodyB();
            if (other != Jolt.cInvalidBodyId && contact.getDistance() <= TOUCH_DISTANCE) {
                now.add(handleOf(other));
            }
        }
        now.remove(character.handle);
        for (int other : now) {
            if (character.touching.add(other)) {
                contactAdded(character.handle, other);
            }
        }
        Iterator<Integer> before = character.touching.iterator();
        while (before.hasNext()) {
            int other = before.next();
            if (!now.contains(other)) {
                before.remove();
                contactRemoved(character.handle, other);
            }
        }
    }

    private JoltCharacter requireCharacter(int handle) {
        requireOpen();
        JoltCharacter character = characters.get(handle);
        if (character == null) {
            throw new IllegalArgumentException("No such character (removed already?): " + handle);
        }
        return character;
    }

    /** The character that owns {@code bodyId} as its inner body, or the id itself for an ordinary body. */
    private int handleOf(int bodyId) {
        return characterByInnerBody.getOrDefault(bodyId, bodyId);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        // Joints and characters first: they point into the system
        for (JoltJoint joint : joints.values()) {
            system.removeConstraint(joint.constraint());
            joint.constraint().close();
        }
        joints.clear();
        noCollidePairs.clear();
        for (JoltCharacter character : characters.values()) {
            character.close();
        }
        characters.clear();
        characterByInnerBody.clear();
        // The system first: it holds the bodies and calls the listener. The rest follow in reverse order of creation.
        system.close();
        contacts.close();
        layersVsBroadPhase.close();
        layerPairs.close();
        broadPhaseLayers.close();
        jobSystem.close();
        tempAllocator.close();
        synchronized (eventLock) {
            events.clear();
            pairContacts.clear();
        }
        liveBodies.clear();
    }

    // --- Bodies and shapes ---

    private Vector3 normalAt(int bodyId, int subShapeId, Vector3 point) {
        try (BodyLockRead lock = new BodyLockRead(system.getBodyLockInterface(), bodyId)) {
            if (!lock.succeeded()) {
                return new Vector3(Vector3.Y);
            }
            Vec3 normal = lock.getBody().getWorldSpaceSurfaceNormal(subShapeId, new RVec3(point.x, point.y, point.z));
            return new Vector3(normal.getX(), normal.getY(), normal.getZ());
        }
    }

    /** One shape, or a compound when there are several or one is moved off the body's origin. */
    private static ShapeSettings shapeOf(List<ShapeDescriptor3D> shapes) {
        if (shapes.size() == 1 && shapes.get(0).offset().isZero()) {
            return single(shapes.get(0));
        }
        StaticCompoundShapeSettings compound = new StaticCompoundShapeSettings();
        for (ShapeDescriptor3D shape : shapes) {
            compound.addShape(new Vec3(shape.offset().x, shape.offset().y, shape.offset().z), new Quat(0f, 0f, 0f, 1f), single(shape));
        }
        return compound;
    }

    private static ShapeSettings single(ShapeDescriptor3D shape) {
        return switch (shape.kind()) {
            case BOX -> new BoxShapeSettings(shape.halfExtents().x, shape.halfExtents().y, shape.halfExtents().z);
            case SPHERE -> new SphereShapeSettings(shape.radius());
            // Jolt's capsule takes the half height of the cylinder part first, then the radius
            case CAPSULE -> new CapsuleShapeSettings(shape.halfHeight(), shape.radius());
            case HULL -> hullOf(shape.points());
            case MESH -> meshOf(shape.points(), shape.indices());
        };
    }

    /** Convex radius of a hull collider in meters: small, so the hull stays close to the mesh it was built from. */
    private static final float HULL_CONVEX_RADIUS = 0.005f;

    private static ShapeSettings hullOf(float[] points) {
        Vec3[] vertices = new Vec3[points.length / 3];
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = new Vec3(points[i * 3], points[i * 3 + 1], points[i * 3 + 2]);
        }
        return new ConvexHullShapeSettings(vertices, HULL_CONVEX_RADIUS);
    }

    private static ShapeSettings meshOf(float[] points, int[] indices) {
        VertexList vertices = new VertexList();
        for (int i = 0; i < points.length; i += 3) {
            vertices.pushBack(new Float3(points[i], points[i + 1], points[i + 2]));
        }
        IndexedTriangleList triangles = new IndexedTriangleList();
        triangles.resize(indices.length / 3);
        for (int i = 0; i < indices.length / 3; i++) {
            triangles.set(i, new IndexedTriangle(indices[i * 3], indices[i * 3 + 1], indices[i * 3 + 2]));
        }
        return new MeshShapeSettings(vertices, triangles);
    }

    private static EMotionType motionTypeOf(RigidBody3DComponent.MotionType type) {
        return switch (type) {
            case STATIC -> EMotionType.Static;
            case KINEMATIC -> EMotionType.Kinematic;
            case DYNAMIC -> EMotionType.Dynamic;
        };
    }

    static int objectLayer(int layer, boolean moving) {
        return layer * 2 + (moving ? 1 : 0);
    }

    private static boolean isMovingLayer(int objectLayer) {
        return (objectLayer & 1) != 0;
    }

    /** Layer, mask and the sensor flag in one number, kept in the body's Jolt user data. */
    static long pack(int layer, int mask, boolean sensor) {
        return layer | ((long) mask << LAYER_BITS) | (sensor ? 1L << SENSOR_BIT : 0L);
    }

    static int layerOf(long data) {
        return (int) (data & LAYER_FIELD);
    }

    static int maskOf(long data) {
        return (int) ((data >>> LAYER_BITS) & PhysicsLayers3D.ALL_LAYERS);
    }

    static boolean isSensor(long data) {
        return (data & (1L << SENSOR_BIT)) != 0;
    }

    private void requireBody(int handle) {
        requireOpen();
        if (!liveBodies.contains(handle)) {
            throw new IllegalArgumentException("No such body (removed already?): " + handle);
        }
    }

    private void requireOpen() {
        if (disposed) {
            throw new IllegalStateException("The physics backend was disposed");
        }
    }

    // --- Contacts ---

    private static long pairKey(int a, int b) {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return ((long) low << 32) | (high & 0xFFFFFFFFL);
    }

    private void contactAdded(int a, int b) {
        synchronized (eventLock) {
            if (pairContacts.merge(pairKey(a, b), 1, Integer::sum) == 1) {
                events.add(new ContactEvent(Math.min(a, b), Math.max(a, b), true));
            }
        }
    }

    private void contactRemoved(int a, int b) {
        synchronized (eventLock) {
            long key = pairKey(a, b);
            Integer count = pairContacts.get(key);
            if (count == null) {
                // Already reported ended when the body was removed
                return;
            }
            if (count == 1) {
                pairContacts.remove(key);
                events.add(new ContactEvent(Math.min(a, b), Math.max(a, b), false));
            } else {
                pairContacts.put(key, count - 1);
            }
        }
    }

    /** Jolt's callbacks: applies the layer masks and turns sub-shape contacts into body pair events. */
    private final class Contacts extends CustomContactListener {
        @Override
        public int onContactValidate(long body1Va, long body2Va, double offsetX, double offsetY, double offsetZ, long collisionResultVa) {
            long first = new Body(body1Va).getUserData();
            long second = new Body(body2Va).getUserData();
            if (!noCollidePairs.isEmpty() && noCollidePairs.contains(pairKey(new Body(body1Va).getId(), new Body(body2Va).getId()))) {
                return ValidateResult.RejectAllContactsForThisBodyPair.ordinal();
            }
            boolean collide = PhysicsLayers3D.collide(layerOf(first), maskOf(first), layerOf(second), maskOf(second));
            return (collide ? ValidateResult.AcceptAllContactsForThisBodyPair : ValidateResult.RejectAllContactsForThisBodyPair).ordinal();
        }

        @Override
        public void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
            contactAdded(handleOf(new Body(body1Va).getId()), handleOf(new Body(body2Va).getId()));
        }

        @Override
        public void onContactRemoved(long pairVa) {
            SubShapeIdPair pair = new SubShapeIdPair(pairVa);
            contactRemoved(handleOf(pair.getBody1Id()), handleOf(pair.getBody2Id()));
        }
    }
}
