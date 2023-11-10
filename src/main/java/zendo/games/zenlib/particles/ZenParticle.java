package zendo.games.zenlib.particles;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Pool;
import zendo.games.zenlib.utils.data.SimplePath;

public class ZenParticle implements Pool.Poolable {

    // TODO: add additional interpolators so that some properties can be interpolated independent of others (alpha vs
    // anim for exaample)
    // TODO: add a 'drop shadow' flag to particle and initializer to improve readability for things like text particles
    // TODO: add optional [x|y]Jitter so several can be spawned at once with the same params but have a little variation
    // in position
    // TODO: add optional arrays of sizes / rotations so that multiple values can be interpolated across throughout the
    // lifetime of the particle
    // eg. sizes[{10,10}, {20, 20}, {10, 10}] would interpolate to twice the size by halfway through its lifetime, then
    // back down to initial size by the end, scale this across an arbitrary number of values

    static Initializer initializer(ZenParticle particle) {
        return new Initializer(particle);
    }

    static Initializer initializer(ZenParticleSystem system) {
        return new Initializer(system.obtainParticle());
    }

    private SimplePath path;
    private Interpolation interpolation;

    private TextureRegion keyframe;

    private Animation<TextureRegion> animation;
    private float animTime;
    private boolean animUnlocked;

    private float xStart;
    private float yStart;
    private Vector2 position;

    private boolean targeted;
    private float xTarget;
    private float yTarget;

    private Vector2 velocity;
    private float bounceScale;

    private Vector2 accel;
    private float accDamp;

    private float widthStart;
    private float widthEnd;
    private float width;

    private float heightStart;
    private float heightEnd;
    private float height;

    private float rotationStart;
    private float rotationEnd;
    private float rotation;

    private float rStart, gStart, bStart, aStart;
    private float rEnd, gEnd, bEnd, aEnd;
    private float r, g, b, a;

    private boolean timed;
    public float ttlMax;
    public float ttl;

    private boolean dead;
    private boolean persistent;
    private Circle collisionBounds;
    private Rectangle collisionRect;

    public ZenParticle() {
        velocity = new Vector2();
        position = new Vector2();
        accel = new Vector2();
        collisionBounds = new Circle();
        collisionRect = new Rectangle();
        reset();
    }

    public void update(float dt) {
        float lifetime, progress;
        if (timed) {
            ttl -= dt;
            if (ttl <= 0f && !persistent) {
                dead = true;
            }
            lifetime = MathUtils.clamp(ttl / ttlMax, 0f, 1f);
        } else {
            ttl += dt;
            lifetime = MathUtils.clamp(ttl, 0f, 1f);
        }
        progress = interpolation.apply(0f, 1f, MathUtils.clamp(1f - lifetime, 0f, 1f));

        if (animation != null) {
            if (!persistent && !animUnlocked && timed) {
                animTime = progress * animation.getAnimationDuration();
            } else {
                animTime += dt;
            }
            keyframe = animation.getKeyFrame(animTime);
        }

        if (path != null) {
            // https://github.com/libgdx/libgdx/wiki/Path-interface-and-Splines#make-the-sprite-traverse-at-constant-speed
            var pathPos = path.derivativeAt(progress);
            var arcLengthProgress = progress + (dt * ttl / path.spanCount()) / pathPos.len();
            pathPos.set(path.valueAt(arcLengthProgress));
            position.x = pathPos.x;
            position.y = pathPos.y;
        } else if (targeted) {
            position.x = MathUtils.lerp(xStart, xTarget, progress);
            position.y = MathUtils.lerp(yStart, yTarget, progress);
        } else {
            accel.x *= accDamp;
            accel.y *= accDamp;
            if (MathUtils.isEqual(accel.x, 0f, 0.01f)) accel.x = 0f;
            if (MathUtils.isEqual(accel.y, 0f, 0.01f)) accel.y = 0f;

            velocity.x += accel.x * dt;
            velocity.y += accel.y * dt;

            position.x += velocity.x * dt;
            position.y += velocity.y * dt;
        }

        width = MathUtils.lerp(widthStart, widthEnd, progress);
        height = MathUtils.lerp(heightStart, heightEnd, progress);

        rotation = MathUtils.lerp(rotationStart, rotationEnd, progress);

        r = MathUtils.lerp(rStart, rEnd, progress);
        g = MathUtils.lerp(gStart, gEnd, progress);
        b = MathUtils.lerp(bStart, bEnd, progress);
        a = MathUtils.lerp(aStart, aEnd, progress);
        collisionRect.set(position.x - width / 2, position.y - height / 2, width, height);
    }

    public void render(SpriteBatch batch) {
        if (keyframe == null) return;
        batch.setColor(r, g, b, a);
        batch.draw(
                keyframe,
                position.x - width / 2f,
                position.y - height / 2f,
                width / 2f,
                height / 2f,
                width,
                height,
                1f,
                1f,
                rotation);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    boolean isDead() {
        return dead;
    }

    @Override
    public void reset() {
        this.path = null;
        this.interpolation = Interpolation.linear;

        this.keyframe = null;

        this.animation = null;
        this.animUnlocked = false;
        this.animTime = 0f;

        this.xStart = 0f;
        this.yStart = 0f;
        this.position.set(0, 0);

        this.targeted = false;
        this.xTarget = 0f;
        this.yTarget = 0f;

        this.velocity.set(0, 0);
        this.bounceScale = .8f;

        this.accel.set(0, 0);
        this.accDamp = 0f;

        this.widthStart = 0f;
        this.widthEnd = 0f;
        this.width = 0f;

        this.heightStart = 0f;
        this.heightEnd = 0f;
        this.height = 0f;

        this.rotationStart = 0f;
        this.rotationEnd = 0f;
        this.rotation = 0f;

        this.rStart = 1f;
        this.gStart = 1f;
        this.bStart = 1f;
        this.aStart = 1f;
        this.rEnd = 1f;
        this.gEnd = 1f;
        this.bEnd = 1f;
        this.aEnd = 1f;
        this.r = rStart;
        this.g = gStart;
        this.b = bStart;
        this.a = aStart;

        this.timed = false;
        this.ttlMax = 0f;
        this.ttl = 0f;

        this.dead = true;
        this.persistent = false;
        this.collisionBounds.set(0, 0, 0);
    }

    // ------------------------------------------------------------------------

    public static class Initializer {

        private final ZenParticle particle;

        private SimplePath path = null;
        private TextureRegion keyframe = null;
        private Animation<TextureRegion> animation = null;
        private Interpolation interpolation = Interpolation.linear;
        private boolean animUnlocked = false;

        private float xStart = 0f;
        private float yStart = 0f;

        private boolean targeted = false;
        private float xTarget = 0f;
        private float yTarget = 0f;

        private float xVel = 0f;
        private float yVel = 0f;

        private float bounceScale = .8f;

        private float xAcc = 0f;
        private float yAcc = 0f;
        private float accDamp = 0f;

        private float widthStart = 0f;
        private float widthEnd = 0f;
        private boolean setWidthEnd = false;

        private float heightStart = 0f;
        private float heightEnd = 0f;
        private boolean setHeightEnd = false;

        private float rotationStart = 0f;
        private float rotationEnd = 0f;
        private boolean setRotationEnd = false;

        private float rStart = 1f;
        private float gStart = 1f;
        private float bStart = 1f;
        private float aStart = 1f;
        private float rEnd = 1f;
        private float gEnd = 1f;
        private float bEnd = 1f;
        private float aEnd = 1f;
        private boolean setColorEnd = false;
        private boolean setAlphaEnd = false;

        private boolean persistent = false;
        private boolean timed = false;
        private float ttlMax = 0f;

        Initializer(ZenParticle particle) {
            this.particle = particle;
            this.particle.reset();
        }

        Initializer interpolation(Interpolation interpolation) {
            this.interpolation = interpolation;
            return this;
        }

        Initializer path(SimplePath path) {
            this.path = path;
            return this;
        }

        Initializer keyframe(TextureRegion keyframe) {
            this.keyframe = keyframe;
            return this;
        }

        Initializer animation(Animation<TextureRegion> animation) {
            this.animation = animation;
            return this;
        }

        Initializer animUnlocked(boolean animUnlocked) {
            this.animUnlocked = animUnlocked;
            return this;
        }

        Initializer startPos(float x, float y) {
            this.xStart = x;
            this.yStart = y;
            return this;
        }

        Initializer targetPos(float x, float y) {
            this.xTarget = x;
            this.yTarget = y;
            this.targeted = true;
            return this;
        }

        Initializer velocity(float x, float y) {
            this.xVel = x;
            this.yVel = y;
            return this;
        }

        Initializer velocityDirection(float angle, float magnitude) {
            this.xVel = MathUtils.cosDeg(angle) * magnitude;
            this.yVel = MathUtils.sinDeg(angle) * magnitude;
            return this;
        }

        Initializer acceleration(float x, float y) {
            this.xAcc = x;
            this.yAcc = y;
            return this;
        }

        Initializer accelerationDamping(float damp) {
            this.accDamp = damp;
            return this;
        }

        Initializer startSize(float width, float height) {
            this.widthStart = width;
            this.heightStart = height;
            return this;
        }

        Initializer startSize(float size) {
            this.widthStart = size;
            this.heightStart = size;
            return this;
        }

        Initializer endSize(float width, float height) {
            this.widthEnd = width;
            this.heightEnd = height;
            this.setWidthEnd = true;
            this.setHeightEnd = true;
            return this;
        }

        Initializer endSize(float size) {
            this.widthEnd = size;
            this.heightEnd = size;
            this.setWidthEnd = true;
            this.setHeightEnd = true;
            return this;
        }

        Initializer startRotation(float rotation) {
            this.rotationStart = rotation;
            return this;
        }

        Initializer endRotation(float rotation) {
            this.rotationEnd = rotation;
            this.setRotationEnd = true;
            return this;
        }

        Initializer startColor(float r, float g, float b, float a) {
            this.rStart = r;
            this.gStart = g;
            this.bStart = b;
            this.aStart = a;
            return this;
        }

        Initializer startColor(Color color) {
            this.rStart = color.r;
            this.gStart = color.g;
            this.bStart = color.b;
            this.aStart = color.a;
            return this;
        }

        Initializer endColor(float r, float g, float b, float a) {
            this.rEnd = r;
            this.gEnd = g;
            this.bEnd = b;
            this.aEnd = a;
            this.setColorEnd = true;
            return this;
        }

        Initializer endColor(Color color) {
            this.rEnd = color.r;
            this.gEnd = color.g;
            this.bEnd = color.b;
            this.aEnd = color.a;
            this.setColorEnd = true;
            return this;
        }

        Initializer startAlpha(float a) {
            this.aStart = a;
            return this;
        }

        Initializer endAlpha(float a) {
            this.aEnd = a;
            this.setAlphaEnd = true;
            return this;
        }

        Initializer timeToLive(float ttl) {
            this.ttlMax = ttl;
            this.timed = true;
            return this;
        }

        Initializer persist() {
            this.persistent = true;
            return this;
        }

        ZenParticle init() {
            if (keyframe != null) {
                particle.keyframe = keyframe;
            }
            if (animation != null) {
                particle.animation = animation;
                particle.animTime = 0f;
                particle.animUnlocked = animUnlocked;
            }
            if (path != null) {
                if (!timed) {
                    throw new GdxRuntimeException(
                            "Particles with a path must also have a time to live, is your Particle.Initializer missing a call to timeToLive()?");
                }
                particle.path = path;
            }
            if (interpolation != null) {
                particle.interpolation = interpolation;
            }

            particle.xStart = xStart;
            particle.yStart = yStart;
            particle.position.set(xStart, yStart);

            particle.targeted = targeted;
            particle.xTarget = xTarget;
            particle.yTarget = yTarget;
            if (targeted && !timed) {
                throw new GdxRuntimeException(
                        "Particles with a target must also have a time to live, is your Particle.Initializer missing a call to timeToLive()?");
            }

            particle.velocity.set(xVel, yVel);
            particle.bounceScale = bounceScale;

            particle.accel.set(xAcc, yAcc);
            particle.accDamp = accDamp;

            particle.widthStart = widthStart;
            particle.widthEnd = (setWidthEnd) ? widthEnd : widthStart;
            particle.width = widthStart;

            particle.heightStart = heightStart;
            particle.heightEnd = (setHeightEnd) ? heightEnd : heightStart;
            particle.height = heightStart;

            if ((particle.widthStart == 0f && particle.widthEnd == 0f)
                    || (particle.heightStart == 0f && particle.heightEnd == 0f)) {
                Gdx.app.log(
                        "WARN",
                        "A particle has been created with degenerate size (starting and ending width or height both equal zero), you probably didn't mean to do this as this means the particle won't be visible");
            }

            particle.rotationStart = rotationStart;
            particle.rotationEnd = (setRotationEnd) ? rotationEnd : rotationStart;
            particle.rotation = rotationStart;

            particle.rStart = rStart;
            particle.gStart = gStart;
            particle.bStart = bStart;
            particle.aStart = aStart;
            particle.rEnd = (setColorEnd) ? rEnd : rStart;
            particle.gEnd = (setColorEnd) ? gEnd : gStart;
            particle.bEnd = (setColorEnd) ? bEnd : bStart;
            particle.aEnd = (setColorEnd || setAlphaEnd) ? aEnd : aStart;
            particle.r = rStart;
            particle.g = gStart;
            particle.b = bStart;
            particle.a = aStart;

            if (particle.aStart == 0f && particle.aEnd == 0f) {
                Gdx.app.log(
                        "WARN",
                        "A particle has been created with degenerate alpha (starting and ending alpha both equal zero), you probably didn't mean to do this as this means the particle won't be visible");
            }

            particle.timed = timed;
            particle.ttlMax = ttlMax;
            particle.ttl = ttlMax;

            particle.persistent = persistent;
            particle.dead = false;

            return particle;
        }
    }
}
