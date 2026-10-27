package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    private final Queue<Collision2D> eventQueue = new ConcurrentLinkedQueue<>();
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
        createBodiesAndFixtures();
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
        }
    }
}
