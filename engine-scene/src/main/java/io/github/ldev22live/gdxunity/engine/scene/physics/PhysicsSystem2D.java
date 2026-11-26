package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptCollisionForwarder2D;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.*;

/**
 * Runtime system managing the 2D physics simulation, Box2D World lifecycle,
 * fixed-timestep stepping, transform synchronization, and deferred collision event dispatch.
 */
public class PhysicsSystem2D implements Disposable {

    public static final float FIXED_TIMESTEP = 1.0f / 60.0f;
    public static final int VELOCITY_ITERATIONS = 6;
    public static final int POSITION_ITERATIONS = 2;
    public static final float MAX_FRAME_DELTA = 0.1f;
    public static final int MAX_SUB_STEPS = 5;

    public interface CollisionListener {
        void onCollision(Collision2D event);
    }

    private static boolean nativesInitialized = false;

    private final Scene runtimeScene;
    private final World world;
    private final Map<UUID, Body> entityBodies = new LinkedHashMap<>();
    private final Map<Body, Entity> bodyEntities = new HashMap<>();
    private final Map<Fixture, Collider2DComponent> fixtureColliders = new HashMap<>();

    // Filled by Box2D's contact callbacks, which run synchronously inside world.step() on the calling thread,
    // and drained right after the step; single-threaded, so no concurrent queue (also unavailable on TeaVM web)
    private final Queue<Collision2D> eventQueue = new ArrayDeque<>();
    private final List<CollisionListener> listeners = new ArrayList<>();

    private float accumulator = 0.0f;
    private boolean disposed = false;

    /**
     * Initializes LibGDX core and Box2D native shared libraries once safely.
     */
    public static synchronized void ensureNativesInitialized() {
        if (!nativesInitialized) {
            com.badlogic.gdx.utils.GdxNativesLoader.load();
            Box2D.init();
            nativesInitialized = true;
        }
    }

    public PhysicsSystem2D(Scene runtimeScene) {
        this(runtimeScene, new Vector2(0f, -9.81f));
    }

    public PhysicsSystem2D(Scene runtimeScene, Vector2 gravity) {
        this.runtimeScene = Objects.requireNonNull(runtimeScene, "runtimeScene cannot be null");
        ensureNativesInitialized();

        Vector2 grav = (gravity != null) ? gravity : new Vector2(0f, -9.81f);
        this.world = new World(grav, true);

        setupContactListener();
        // Scripts get the 2D collision callbacks from every system, whoever made it (editor Play, the game runtime, a test)
        listeners.add(new ScriptCollisionForwarder2D());
        createBodiesAndFixtures();
        runtimeScene.attachPhysics2D(this);
    }

    // ---- moving a body in scene units (TD-105) ----

    /**
     * The velocity of {@code entity}'s body in scene units (pixels) per second, into {@code out}.
     *
     * @throws IllegalArgumentException when the entity has no body here
     */
    public Vector2 getLinearVelocity(Entity entity, Vector2 out) {
        Vector2 v = bodyOf(entity, "getLinearVelocity").getLinearVelocity();
        return out.set(PhysicsUnits2D.toSceneLength(v.x), PhysicsUnits2D.toSceneLength(v.y));
    }

    /**
     * Sets the velocity of {@code entity}'s body, in scene units (pixels) per second, and wakes it.
     *
     * @throws IllegalArgumentException when the entity has no body here, or a value is not finite
     */
    public void setLinearVelocity(Entity entity, float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("velocity must be finite: " + x + ", " + y);
        }
        Body body = bodyOf(entity, "setLinearVelocity");
        body.setLinearVelocity(PhysicsUnits2D.toPhysicsLength(x), PhysicsUnits2D.toPhysicsLength(y));
        body.setAwake(true);
    }

    /**
     * Whether something solid lies under {@code entity}'s body: three short rays straight down (at its left, middle and right) from
     * the bottom of its shapes reach a fixture of another body that is not a sensor within {@link #GROUND_PROBE} scene units.
     *
     * @throws IllegalArgumentException when the entity has no body here, or its body has no shapes
     */
    public boolean isGrounded(Entity entity) {
        Body body = bodyOf(entity, "isGrounded");
        if (!shapeBounds(body)) {
            throw new IllegalArgumentException("'" + entity.getName() + "' has no collider, so it cannot stand on anything");
        }
        float inset = (groundBox[2] - groundBox[0]) * GROUND_PROBE_INSET;
        float bottom = groundBox[1];
        float start = bottom + PhysicsUnits2D.toPhysicsLength(GROUND_PROBE_START);
        float end = bottom - PhysicsUnits2D.toPhysicsLength(GROUND_PROBE);
        groundBody = body;
        groundHit = false;
        for (int ray = 0; ray < 3 && !groundHit; ray++) {
            float x = ray == 0 ? groundBox[0] + inset : ray == 1 ? (groundBox[0] + groundBox[2]) / 2f : groundBox[2] - inset;
            world.rayCast(groundRay, x, start, x, end);
        }
        groundBody = null;
        return groundHit;
    }

    /** How far below a body's shapes {@link #isGrounded} looks, scene units. */
    public static final float GROUND_PROBE = 4f;
    /** Where the downward rays start, scene units above the bottom of the shapes, so a touching floor is found. */
    private static final float GROUND_PROBE_START = 1f;
    /** How far in from the body's sides the outer rays are, as a share of its width. */
    private static final float GROUND_PROBE_INSET = 0.1f;
    private boolean groundHit;
    private Body groundBody;
    private final float[] groundBox = new float[4];
    private final Vector2 tmpLocal = new Vector2();
    /** The downward ray of {@link #isGrounded}: ignores the body itself and sensors, stops at the first other fixture. */
    private final RayCastCallback groundRay = (fixture, point, normal, fraction) -> {
        if (fixture.getBody() == groundBody || fixture.isSensor()) {
            return -1f;
        }
        groundHit = true;
        return 0f;
    };

    /** The world bounds of a body's fixtures (minX, minY, maxX, maxY) in metres, into {@code groundBox}; false when it has none. */
    private boolean shapeBounds(Body body) {
        boolean any = false;
        for (Fixture fixture : body.getFixtureList()) {
            Shape shape = fixture.getShape();
            if (shape instanceof PolygonShape polygon) {
                for (int i = 0; i < polygon.getVertexCount(); i++) {
                    polygon.getVertex(i, tmpLocal);
                    Vector2 p = body.getWorldPoint(tmpLocal);
                    any = grow(any, p.x, p.y, 0f);
                }
            } else if (shape instanceof CircleShape circle) {
                Vector2 centre = body.getWorldPoint(circle.getPosition());
                any = grow(any, centre.x, centre.y, circle.getRadius());
            }
        }
        return any;
    }

    private boolean grow(boolean any, float x, float y, float radius) {
        if (!any) {
            groundBox[0] = x - radius;
            groundBox[1] = y - radius;
            groundBox[2] = x + radius;
            groundBox[3] = y + radius;
            return true;
        }
        groundBox[0] = Math.min(groundBox[0], x - radius);
        groundBox[1] = Math.min(groundBox[1], y - radius);
        groundBox[2] = Math.max(groundBox[2], x + radius);
        groundBox[3] = Math.max(groundBox[3], y + radius);
        return true;
    }

    private Body bodyOf(Entity entity, String operation) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Body body = getBody(entity);
        if (body == null) {
            throw new IllegalArgumentException(operation + ": '" + entity.getName() + "' has no 2D body; give it a Rigid Body 2D and a "
                + "2D collider");
        }
        return body;
    }

    public World getWorld() {
        return world;
    }

    public int getBodyCount() {
        return entityBodies.size();
    }

    public Body getBody(Entity entity) {
        if (entity == null) return null;
        return entityBodies.get(entity.getId());
    }

    public void addCollisionListener(CollisionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeCollisionListener(CollisionListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void createBodiesAndFixtures() {
        for (Entity entity : runtimeScene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) {
                continue;
            }

            RigidBody2DComponent rb = entity.getComponent(RigidBody2DComponent.class);
            List<BoxCollider2DComponent> boxColliders = entity.getComponents(BoxCollider2DComponent.class);
            List<CircleCollider2DComponent> circleColliders = entity.getComponents(CircleCollider2DComponent.class);

            boolean hasColliders = !boxColliders.isEmpty() || !circleColliders.isEmpty();
            List<StaticColliderSource2D> staticSources = new ArrayList<>();
            for (io.github.ldev22live.gdxunity.engine.scene.Component component : entity.getAllComponents()) {
                if (component.isEnabled() && component instanceof StaticColliderSource2D source && !source.getStaticBoxes().isEmpty()) {
                    staticSources.add(source);
                }
            }
            hasColliders |= !staticSources.isEmpty();

            // If entity has no RigidBody and no colliders, skip
            if (rb == null && !hasColliders) {
                continue;
            }

            // Create body definition
            BodyDef bodyDef = new BodyDef();

            // Transform world position to physics position
            Vector3 worldPos = entity.getTransform().getWorldPosition();
            Vector2 physPos = PhysicsUnits2D.toPhysicsPosition(worldPos.x, worldPos.y);
            bodyDef.position.set(physPos);

            float worldAngleDeg = entity.getTransform().getWorldRotation().getAngleAround(0f, 0f, 1f);
            if (!Float.isFinite(worldAngleDeg)) {
                worldAngleDeg = 0f;
            }
            bodyDef.angle = PhysicsUnits2D.toPhysicsAngle(worldAngleDeg);

            if (rb != null) {
                switch (rb.getBodyType()) {
                    case STATIC -> bodyDef.type = BodyDef.BodyType.StaticBody;
                    case KINEMATIC -> bodyDef.type = BodyDef.BodyType.KinematicBody;
                    case DYNAMIC -> bodyDef.type = BodyDef.BodyType.DynamicBody;
                }
                bodyDef.gravityScale = rb.getGravityScale();
                bodyDef.linearDamping = rb.getLinearDamping();
                bodyDef.angularDamping = rb.getAngularDamping();
                bodyDef.fixedRotation = rb.isFixedRotation();
                bodyDef.bullet = rb.isBullet();
                bodyDef.linearVelocity.set(rb.getInitialLinearVelocity());
                bodyDef.angularVelocity = rb.getInitialAngularVelocity();
            } else {
                // Colliders present without RigidBody -> Implicit StaticBody
                bodyDef.type = BodyDef.BodyType.StaticBody;
            }

            Body body = world.createBody(bodyDef);
            body.setUserData(entity);
            entityBodies.put(entity.getId(), body);
            bodyEntities.put(body, entity);

            // Attach box colliders
            for (BoxCollider2DComponent box : boxColliders) {
                if (box.isEnabled()) {
                    createBoxFixture(body, entity, box);
                }
            }

            // Attach circle colliders
            for (CircleCollider2DComponent circle : circleColliders) {
                if (circle.isEnabled()) {
                    createCircleFixture(body, entity, circle);
                }
            }

            for (StaticColliderSource2D source : staticSources) {
                createStaticBoxFixtures(body, entity, source);
            }
        }
    }

    /** Tile friction of the fixed boxes (the default of a collider). */
    private static final float STATIC_BOX_FRICTION = 0.2f;

    private void createStaticBoxFixtures(Body body, Entity entity, StaticColliderSource2D source) {
        Vector3 origin = entity.getTransform().getWorldPosition();
        for (StaticColliderSource2D.StaticBox2D box : source.getStaticBoxes()) {
            PolygonShape shape = new PolygonShape();
            Vector2 center = PhysicsUnits2D.toPhysicsPosition(box.centerX() - origin.x, box.centerY() - origin.y);
            shape.setAsBox(PhysicsUnits2D.toPhysicsLength(box.width() / 2f), PhysicsUnits2D.toPhysicsLength(box.height() / 2f), center, 0f);
            FixtureDef fixtureDef = new FixtureDef();
            fixtureDef.shape = shape;
            fixtureDef.friction = STATIC_BOX_FRICTION;
            Fixture fixture = body.createFixture(fixtureDef);
            fixture.setUserData(source);
            shape.dispose();
        }
    }

    private void createBoxFixture(Body body, Entity entity, BoxCollider2DComponent box) {
        PolygonShape shape = new PolygonShape();
        Vector3 worldScale = entity.getTransform().getWorldScale();
        float absScaleX = Math.abs(worldScale.x);
        float absScaleY = Math.abs(worldScale.y);
        if (!Float.isFinite(absScaleX) || absScaleX < 0.0001f) absScaleX = 1.0f;
        if (!Float.isFinite(absScaleY) || absScaleY < 0.0001f) absScaleY = 1.0f;

        float halfW = PhysicsUnits2D.toPhysicsLength((box.getWidth() * absScaleX) / 2f);
        float halfH = PhysicsUnits2D.toPhysicsLength((box.getHeight() * absScaleY) / 2f);
        Vector2 physOffset = PhysicsUnits2D.toPhysicsPosition(box.getOffset().x * worldScale.x, box.getOffset().y * worldScale.y);

        shape.setAsBox(halfW, halfH, physOffset, 0f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = box.getDensity();
        fixtureDef.friction = box.getFriction();
        fixtureDef.restitution = box.getRestitution();
        fixtureDef.isSensor = box.isSensor();
        fixtureDef.filter.categoryBits = box.getCategoryBits();
        fixtureDef.filter.maskBits = box.getMaskBits();

        Fixture fixture = body.createFixture(fixtureDef);
        fixture.setUserData(box);
        fixtureColliders.put(fixture, box);
        shape.dispose();
    }

    private void createCircleFixture(Body body, Entity entity, CircleCollider2DComponent circle) {
        CircleShape shape = new CircleShape();
        Vector3 worldScale = entity.getTransform().getWorldScale();
        float absScaleX = Math.abs(worldScale.x);
        float absScaleY = Math.abs(worldScale.y);
        if (!Float.isFinite(absScaleX) || absScaleX < 0.0001f) absScaleX = 1.0f;
        if (!Float.isFinite(absScaleY) || absScaleY < 0.0001f) absScaleY = 1.0f;
        float scaleFactor = Math.max(absScaleX, absScaleY);

        float physRadius = PhysicsUnits2D.toPhysicsLength(circle.getRadius() * scaleFactor);
        Vector2 physOffset = PhysicsUnits2D.toPhysicsPosition(circle.getOffset().x * worldScale.x, circle.getOffset().y * worldScale.y);

        shape.setRadius(physRadius);
        shape.setPosition(physOffset);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = circle.getDensity();
        fixtureDef.friction = circle.getFriction();
        fixtureDef.restitution = circle.getRestitution();
        fixtureDef.isSensor = circle.isSensor();
        fixtureDef.filter.categoryBits = circle.getCategoryBits();
        fixtureDef.filter.maskBits = circle.getMaskBits();

        Fixture fixture = body.createFixture(fixtureDef);
        fixture.setUserData(circle);
        fixtureColliders.put(fixture, circle);
        shape.dispose();
    }

    private void setupContactListener() {
        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {
                enqueueContactEvent(contact, true);
            }

            @Override
            public void endContact(Contact contact) {
                enqueueContactEvent(contact, false);
            }

            @Override
            public void preSolve(Contact contact, Manifold oldManifold) {
            }

            @Override
            public void postSolve(Contact contact, ContactImpulse impulse) {
            }
        });
    }

    private void enqueueContactEvent(Contact contact, boolean isBegin) {
        Fixture fixA = contact.getFixtureA();
        Fixture fixB = contact.getFixtureB();
        if (fixA == null || fixB == null) return;

        Body bodyA = fixA.getBody();
        Body bodyB = fixB.getBody();
        Entity entityA = bodyEntities.get(bodyA);
        Entity entityB = bodyEntities.get(bodyB);
        if (entityA == null || entityB == null) return;

        Collider2DComponent compA = fixtureColliders.get(fixA);
        Collider2DComponent compB = fixtureColliders.get(fixB);

        boolean isSensor = fixA.isSensor() || fixB.isSensor();
        Collision2D.Type type;
        if (isSensor) {
            type = isBegin ? Collision2D.Type.SENSOR_ENTER : Collision2D.Type.SENSOR_EXIT;
        } else {
            type = isBegin ? Collision2D.Type.COLLISION_ENTER : Collision2D.Type.COLLISION_EXIT;
        }

        Vector2 contactPoint = new Vector2();
        Vector2 normal = new Vector2();
        WorldManifold manifold = contact.getWorldManifold();
        if (manifold != null && manifold.getNumberOfContactPoints() > 0) {
            contactPoint.set(manifold.getPoints()[0]);
            normal.set(manifold.getNormal());
        }

        Collision2D eventA = new Collision2D(type, entityA, entityB, compA, compB, contactPoint, normal, isSensor);
        eventQueue.offer(eventA);
    }

    /**
     * Steps the simulation with fixed timestep and synchronizes body positions back to Scene Transforms.
     * Dispatches queued collision callbacks safely outside the native Box2D step lock.
     */
    public void update(float deltaTime) {
        if (disposed) return;

        float clampedDelta = Math.min(Math.max(0f, deltaTime), MAX_FRAME_DELTA);
        accumulator += clampedDelta;

        int substeps = 0;
        while (accumulator >= FIXED_TIMESTEP && substeps < MAX_SUB_STEPS) {
            world.step(FIXED_TIMESTEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
            accumulator -= FIXED_TIMESTEP;
            substeps++;
        }

        // Clamp remaining accumulator if we hit max substeps to prevent backlog explosion
        if (accumulator > FIXED_TIMESTEP) {
            accumulator = 0f;
        }

        // Synchronize simulated Box2D bodies back to Entity transforms
        syncSimulatedTransforms();

        // Flush deferred collision events
        flushCollisionEvents();
    }

    private void syncSimulatedTransforms() {
        for (Map.Entry<UUID, Body> entry : entityBodies.entrySet()) {
            UUID entityId = entry.getKey();
            Body body = entry.getValue();

            // Static bodies don't move during simulation
            if (body.getType() == BodyDef.BodyType.StaticBody) {
                continue;
            }

            Entity entity = runtimeScene.findEntityById(entityId);
            if (entity == null || !entity.isActiveInHierarchy()) {
                continue;
            }

            Vector2 physPos = body.getPosition();
            Vector2 scenePos = PhysicsUnits2D.toScenePosition(physPos);
            float physAngle = body.getAngle();
            float sceneAngleDeg = PhysicsUnits2D.toSceneAngle(physAngle);

            Transform transform = entity.getTransform();
            Transform parent = transform.getParent();

            if (parent == null) {
                // Root entity: local position is world position
                transform.setLocalPosition(scenePos.x, scenePos.y, transform.getLocalPosition().z);
                transform.setLocalRotation(new com.badlogic.gdx.math.Quaternion(new Vector3(0f, 0f, 1f), sceneAngleDeg));
            } else {
                // Parented entity: convert simulated world position to local coordinates
                Vector3 curWorldPos = new Vector3(scenePos.x, scenePos.y, transform.getWorldPosition().z);
                Vector3 parentWPos = parent.getWorldPosition();
                com.badlogic.gdx.math.Quaternion parentWRot = parent.getWorldRotation();
                Vector3 parentWScale = parent.getWorldScale();

                Vector3 diff = new Vector3(curWorldPos).sub(parentWPos);
                com.badlogic.gdx.math.Quaternion invParentRot = new com.badlogic.gdx.math.Quaternion(parentWRot).conjugate();
                invParentRot.transform(diff);
                float parentScaleX = parentWScale.x;
                float parentScaleY = parentWScale.y;
                if (!Float.isFinite(parentScaleX) || Math.abs(parentScaleX) < 0.0001f) parentScaleX = 1.0f;
                if (!Float.isFinite(parentScaleY) || Math.abs(parentScaleY) < 0.0001f) parentScaleY = 1.0f;
                diff.x /= parentScaleX;
                diff.y /= parentScaleY;

                transform.setLocalPosition(diff.x, diff.y, transform.getLocalPosition().z);

                float parentAngleDeg = parentWRot.getAngleAround(0f, 0f, 1f);
                if (!Float.isFinite(parentAngleDeg)) {
                    parentAngleDeg = 0f;
                }
                float relativeAngle = sceneAngleDeg - parentAngleDeg;
                if (!Float.isFinite(relativeAngle)) {
                    relativeAngle = 0f;
                }
                transform.setLocalRotation(new com.badlogic.gdx.math.Quaternion(new Vector3(0f, 0f, 1f), relativeAngle));
            }
        }
    }

    private void flushCollisionEvents() {
        Collision2D event;
        while ((event = eventQueue.poll()) != null) {
            if (event.entity() != null && event.entity().isActiveInHierarchy()) {
                for (CollisionListener listener : listeners) {
                    try {
                        listener.onCollision(event);
                    } catch (Throwable t) {
                        System.err.println("Error in CollisionListener: " + t.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            eventQueue.clear();
            listeners.clear();
            fixtureColliders.clear();
            bodyEntities.clear();
            entityBodies.clear();
            world.dispose();
            runtimeScene.detachPhysics2D(this);
        }
    }
}
