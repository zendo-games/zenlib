package zendo.games.zenlib.physics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.*;

public class PhysicsSystem {
    private static boolean USE_STEPS = false;
    private static boolean USE_QUADTREE = true;
    public static boolean DEBUG = false;
    private static long lastPhysicsStepTime = 0;

    private float internalTimer;
    private final static float stepIncrement = .01f;

    private final Vector2 tempVec2 = new Vector2();
    private final Vector2 tempStart1 = new Vector2();
    private final Vector2 tempEnd1 = new Vector2();
    private final Vector2 tempStart2 = new Vector2();
    private final Vector2 tempEnd2 = new Vector2();
    private final Vector2 nearest1 = new Vector2();
    private final Vector2 nearest2 = new Vector2();
    private final Vector2 normal = new Vector2();
    private final Vector2 frameVel = new Vector2();
    private final Vector2 frameVel2 = new Vector2();
    private final LongArray timings = new LongArray();
    private Array<Collision> collisions = new Array<>();
    private Collision nextCollision = null;
    private final Pool<Collision> collisionPool = Pools.get(Collision.class);
    private QuadTree quadTree;
    private final Array<Collidable> neighbors = new Array<>();

    public PhysicsSystem(Rectangle area) {
        internalTimer = 0;
        quadTree = new QuadTree(0, area);
        for (int i = 0; i < 1000; i++) {
            collisionPool.free(new Collision());
        }
    }

    public long getAverageTimings() {
        if (timings.size == 0) return 0;
        long total = 0;
        for (long time : timings.items) {
            total += time;
        }
        return total / timings.size;
    }

    public void update(float dt, Array<Collidable> collidables, Array<Influencer> influencers) {
        if (USE_STEPS) {
            int stepsThisFrame = 0;
            internalTimer += dt;
            internalTimer = MathUtils.clamp(internalTimer, 0, .1f);
            while (internalTimer >= stepIncrement) {
                solve(stepIncrement, collidables, influencers);
                internalTimer -= stepIncrement;
                stepsThisFrame++;
            }
        } else {
            solve(MathUtils.clamp(dt, .001f, .1f), collidables, influencers);
        }
    }

    public void solve(float stepIncrement, Array<Collidable> collidables, Array<Influencer> influencers) {
        long startTime = TimeUtils.millis();

        boolean overlaps;
        int infiniteLoops = 0;
        float timeLeft = stepIncrement;
        while (timeLeft > 0) {
            boolean hasFlaggedOverlaps = false;
            int overlapInfiniteLoops = 0;
            do {
                if (USE_QUADTREE) {
                    quadTree.clear();
                    for (Collidable c : collidables) {
                        quadTree.insert(c);
                    }
                }
                overlaps = false;
                if (overlapInfiniteLoops++ > 100) {
                    Gdx.app.log("Physics", "Found an infinite loop while spreading objects out");
                    break;
                }
                // for each object move any overlaps
                for (Collidable object : collidables) {
                    if (object.getMass() == Collidable.IMMOVABLE) continue; // Don't move immovable objects
                    CollisionShape firstShape = object.getCollisionShape();
                    if (USE_QUADTREE) {
                        neighbors.clear();
                        quadTree.retrieve(neighbors, object);
                    } else {
                        neighbors.clear();
                        neighbors.addAll(collidables);
                    }
                    // Check against each other object
                    for (Collidable neighbor : neighbors){
                        if (neighbor == object) continue;
                        if (doShapesOverlap(object.getCollisionShape(), neighbor.getCollisionShape())) {
                            if (!(object.shouldCollideWith(neighbor) && neighbor.shouldCollideWith(object))) continue;
                            overlaps = true;
                            if (DEBUG && !hasFlaggedOverlaps) {
                                hasFlaggedOverlaps = true;
//                                Gdx.app.log("Physics", "overlap detected between "+ object.getClass().toString() +" and " +  neighbor.getClass().toString() +" moving away");
//                                if ((lastCollision1 == object || lastCollision2 == object) && (lastCollision1 == neighbor || lastCollision2 == neighbor)){
//                                    Gdx.app.log("Physics", "Same objects from last collision");
//                                }
                            }
                            // handle circle-circle first
                            if (firstShape instanceof CollisionShapeCircle && neighbor.getCollisionShape() instanceof CollisionShapeCircle) {
                                CollisionShapeCircle firstCircle = (CollisionShapeCircle) firstShape;
                                CollisionShapeCircle secondCircle = (CollisionShapeCircle) neighbor.getCollisionShape();
                                if (firstCircle.center.epsilonEquals(secondCircle.center)) {
                                    Vector2 oldCenter = object.getPosition();
                                    object.setPosition(tempVec2.set(oldCenter.x + MathUtils.random(-.1f, .1f), oldCenter.y + MathUtils.random(-.1f, .1f)));
                                }
                                float distance = firstCircle.center.dst(secondCircle.center) - .001f;
                                float overlapDistance = .5f * (distance - firstCircle.radius - secondCircle.radius);
                                tempVec2.set(firstCircle.center).sub(secondCircle.center).nor();

                                if (object.getMass() == Collidable.IMMOVABLE || neighbor.getMass() == Collidable.IMMOVABLE){
                                    Collidable mover = object;
                                    if (object.getMass() == Collidable.IMMOVABLE) {
                                        mover = neighbor;
                                        tempVec2.scl(-1f);
                                    }
                                    overlapDistance *= 2f;
                                    Vector2 oldCenter = mover.getPosition();
                                    mover.setPosition(oldCenter.x  - (overlapDistance * tempVec2.x), oldCenter.y - (overlapDistance * tempVec2.y));
                                } else {
                                    Vector2 oldCenter = object.getPosition();
                                    object.setPosition(oldCenter.x - (overlapDistance * tempVec2.x), oldCenter.y - (overlapDistance * tempVec2.y));

                                    oldCenter = neighbor.getPosition();
                                    neighbor.setPosition(oldCenter.x + (overlapDistance * tempVec2.x), oldCenter.y + (overlapDistance * tempVec2.y));
                                }
                            } else if ((object.getCollisionShape() instanceof CollisionShapeCircle && neighbor.getCollisionShape() instanceof CollisionShapeSegment) ||
                                      (object.getCollisionShape() instanceof CollisionShapeSegment && neighbor.getCollisionShape() instanceof CollisionShapeCircle)) {
                                // Circle Segment overlaps
                                Collidable circle;
                                Collidable segment;

                                if (object.getCollisionShape() instanceof CollisionShapeCircle) {
                                    circle = object;
                                    segment = neighbor;
                                } else {
                                    circle = neighbor;
                                    segment = object;
                                }
                                CollisionShapeCircle circleShape = (CollisionShapeCircle) circle.getCollisionShape();
                                CollisionShapeSegment segmentShape = (CollisionShapeSegment) segment.getCollisionShape();

                                tempVec2.set(Intersector.nearestSegmentPoint(segmentShape.start, segmentShape.end, circleShape.center, tempVec2));
                                normal.set(tempVec2).sub(circleShape.center);
                                float dist = circleShape.radius - normal.len();
                                if (dist >= 0){
                                    // TODO: Take in to account the normal of the segment and only push the correct way
                                    normal.set(circleShape.center).sub(tempVec2).nor().scl(dist+.01f);
                                    Vector2 oldCenter = circle.getPosition();
                                    circle.setPosition(oldCenter.x + normal.x, oldCenter.y + normal.y);
                                }

                            }
                        }
                    }
                }

            } while (overlaps);

            if (infiniteLoops++ > 100) {
                Gdx.app.log("Physics", "Caught in an infinite loop doing collisions");
                updateMovements(collidables, influencers, timeLeft);
                break;
            }

            nextCollision = null;

            if (USE_QUADTREE) {
                quadTree.clear();
                for (Collidable c : collidables) {
                    quadTree.insert(c);
                }
            }

            // Check for collisions
            for (int i = 0; i < collidables.size; i++) {
                Collidable c = collidables.get(i);
                if (USE_QUADTREE){
                    neighbors.clear();
                    quadTree.retrieve(neighbors, c);
                } else {
                    neighbors.clear();
                    neighbors.addAll(collidables, i, collidables.size-i);
                }
                for (int j = 0; j < neighbors.size; j++) {
                    Collidable other = neighbors.get(j);
                    if (c == other) continue;
                    IntersectionResult result = intersectCollidables(c, other, timeLeft);
                    if (result != null && result.time <= 1) {
                        if (c.shouldCollideWith(other) && other.shouldCollideWith(c)) {
                            Collision collision = collisionPool.obtain();
                            collision.init(result.time, result.position, result.normal, c, other);
                            if (nextCollision == null || collision.t < nextCollision.t){
                                if (nextCollision != null) {
                                    collisionPool.free(nextCollision);
                                }
                                nextCollision = collision;
                            }
                        }
                    }
                }
            }
            if (nextCollision != null) {
//                collisions.sort();
                Collision c = nextCollision;
                double time = c.t * timeLeft;
                updateMovements(collidables, influencers, time);
                c.handleCollision();
                timeLeft -= time;

            } else {
                // no collisions just move
                updateMovements(collidables, influencers, timeLeft);
                timeLeft = 0;
            }
        }
        if (DEBUG){
            long endTime = TimeUtils.millis();
            lastPhysicsStepTime = (endTime - startTime);
        }
    }

    public long getLastPhysicsStepTime() {
        return lastPhysicsStepTime;
    }

    private void updateMovements(Array<Collidable> collidables, Array<Influencer> influencers, double dt) {
        if (dt == 0) return;
        for (Collidable c : collidables) {
            if (c.getMass() != Collidable.IMMOVABLE) {
                tempVec2.set(c.getPosition());
                tempVec2.add((float)(c.getVelocity().x * dt), (float)(c.getVelocity().y * dt));
                c.getVelocity().scl((float) Math.pow(c.getFriction(), dt));
                c.setPosition(tempVec2);
                if (c.getVelocity().len2() < 2) c.setVelocity(Vector2.Zero);
                addInfluence(c, influencers, dt);
                // TODO: update rotations?
            }
        }
    }

    private void addInfluence(Collidable c, Array<Influencer> influencers, double dt) {
        for (Influencer i : influencers){
            if (i.shouldEffect(c)) {
                float rangeSquare = i.getRange() * i.getRange();
                tempVec2.set(i.getPosition()).sub(c.getPosition());
                float dist2 = tempVec2.len2();
                if (rangeSquare > dist2) {
                    tempVec2.nor();
                    float strength = (rangeSquare - dist2) / rangeSquare * i.getStrength();
                    c.getVelocity().add((float) (tempVec2.x * strength * dt), (float) (tempVec2.y * strength * dt));
                }
            }
        }
    }

    private IntersectionResult intersectCollidables(Collidable first, Collidable second, float timeLeft) {
        if (first.getCollisionShape() instanceof CollisionShapeCircle && second.getCollisionShape() instanceof CollisionShapeCircle) {
            return intersectCircleCircle(first, second, timeLeft);
        } else if (first.getCollisionShape() instanceof CollisionShapeCircle && second.getCollisionShape() instanceof CollisionShapeSegment) {
            return intersectCircleSegment(first, second, timeLeft);
        } else if (first.getCollisionShape() instanceof CollisionShapeSegment && second.getCollisionShape() instanceof CollisionShapeCircle) {
            return intersectCircleSegment(second, first, timeLeft);
        }

        return null;
    }

    private IntersectionResult intersectCircleCircle(Collidable first, Collidable second, float timeLeft) {
        CollisionShapeCircle firstShape = (CollisionShapeCircle)first.getCollisionShape();
        CollisionShapeCircle secondShape = (CollisionShapeCircle)second.getCollisionShape();
        tempStart1.set(first.getPosition());
        frameVel.set(first.getVelocity().x * timeLeft, first.getVelocity().y * timeLeft);
        tempEnd1.set(tempStart1).add(frameVel);

        tempStart2.set(second.getPosition());
        frameVel2.set(second.getVelocity().x * timeLeft, second.getVelocity().y * timeLeft);
        tempEnd2.set(tempStart2).add(frameVel2);
        Double time = intersectCircleCircle(tempStart1, tempStart2, frameVel, frameVel2, firstShape.radius, secondShape.radius);
        if (time != null && time <= 1f) {
            IntersectionResult result = new IntersectionResult();
            result.time = time;
            tempEnd1.set(tempStart1).add(frameVel.scl(time.floatValue() * timeLeft));
            tempEnd2.set(tempStart2).add(frameVel2.scl(time.floatValue() * timeLeft));
            normal.set(tempEnd1).sub(tempEnd2).nor();
            tempVec2.set(tempStart2).add(normal.x * secondShape.radius, normal.y * secondShape.radius);
            result.position = tempVec2;
            result.normal = normal;
            return result;
        }
        return null;
    }

    Vector2 nearestPoint = new Vector2();
    private IntersectionResult intersectCircleSegment(Collidable circle, Collidable segment, float timeLeft) {
        CollisionShapeSegment segmentShape = (CollisionShapeSegment) segment.getCollisionShape();
        CollisionShapeCircle circleShape = (CollisionShapeCircle) circle.getCollisionShape();
        nearestPoint.set(Intersector.nearestSegmentPoint(segmentShape.start, segmentShape.end, circleShape.center, nearestPoint));
        tempVec2.set(circle.getPosition()).sub(nearestPoint);
        float side = -Math.signum(segmentShape.normal.dot(tempVec2));
        if (side == 0) {
            side = 1;
        }
        float dist = tempVec2.set(circle.getPosition()).sub(nearestPoint).len() * side;
        if (Math.abs(dist) < circleShape.radius){
            // already inside?
            return null;
        }
        frameVel.set(circle.getVelocity().x * timeLeft, circle.getVelocity().y * timeLeft);
        float denom = -segmentShape.normal.dot(frameVel);
        if (denom*dist >= 0) {
            return null;
        }
        float r = circleShape.radius;
        normal.set(segmentShape.normal);
        if (dist > 0) {
            r = circleShape.radius;
        } else {
            r = -r;
            normal.scl(-1f);
        }
        double t = (r-dist)/denom;
        if (t <= 1.0) {
            IntersectionResult result = new IntersectionResult();
            result.time = t;
            result.position = nearestPoint;
            result.normal = normal;
            return result;
        }
        return null;

    }

    static Vector2 s = new Vector2();
    static Vector2 v = new Vector2();
    private Double intersectCircleCircle(Vector2 pos1, Vector2 pos2, Vector2 vel1, Vector2 vel2, float rad1, float rad2) {
        return intersectCircleCircle(pos1.x, pos1.y, pos2.x, pos2.y, vel1.x, vel1.y, vel2.x, vel2.y, rad1, rad2);
    }

    private Double intersectCircleCircle(float pos1x, float pos1y, float pos2x, float pos2y,
                                        float vel1x, float vel1y, float vel2x, float vel2y,
                                        float rad1, float rad2) {
        Double t;
        s.set(pos2x, pos2y).sub(pos1x, pos1y);
        v.set(vel2x, vel2y).sub(vel1x, vel1y);
        float r = rad1 + rad2;
        float c = s.dot(s) - r * r;
        if (c < 0){
            // Already overlap early out
            t = 0.0;
            return t;
        }
        float a = v.dot(v);
        if (a < 0f) return null; // circles not moving relative to each other
        float b = v.dot(s);
        if (b >= 0f) return null; // circles moving away from each other
        float d = b * b - a * c;
        if (d < 0) return null; // No intersections
        t = (-b - Math.sqrt(d)) / a;

        return t;
    }

    private boolean doShapesOverlap(CollisionShape first, CollisionShape second) {
        if (first instanceof CollisionShapeCircle && second instanceof CollisionShapeCircle) {
            return doCircleCircleOverlap((CollisionShapeCircle) first, (CollisionShapeCircle) second);
        }
        if (first instanceof CollisionShapeCircle && second instanceof CollisionShapeSegment) {
            return doCircleSegmentOverlap((CollisionShapeCircle) first, (CollisionShapeSegment) second);
        }
        if (first instanceof CollisionShapeSegment && second instanceof CollisionShapeCircle) {
            return doCircleSegmentOverlap((CollisionShapeCircle) second, (CollisionShapeSegment) first);
        }
        return false;
    }

    private boolean doCircleCircleOverlap(CollisionShapeCircle first, CollisionShapeCircle second) {
        return Math.abs((first.center.x - second.center.x) *
                        (first.center.x - second.center.x) +
                        (first.center.y - second.center.y) *
                        (first.center.y - second.center.y)) <
            (first.radius + second.radius) * (first.radius + second.radius);
    }

    private boolean doCircleSegmentOverlap(CollisionShapeCircle circle, CollisionShapeSegment segment) {
        tempVec2.set(Intersector.nearestSegmentPoint(segment.start, segment.end, circle.center, tempVec2));
        float distance = tempVec2.dst(circle.center);
        return distance < circle.radius;
    }

    public static Vector2 reflectVector(Vector2 incoming, Vector2 normal) {
        float initalSize = incoming.len();
        normal.nor();
        incoming.nor();
        float iDotN = incoming.dot(normal);
        incoming.set(incoming.x - 2f * normal.x * iDotN,
                incoming.y - 2f * normal.y * iDotN)
            .nor().scl(initalSize);
        return incoming;
    }

    public void debugRender(SpriteBatch batch, NinePatch ninePatch){
        quadTree.renderDebug(batch, ninePatch);
    }
}
