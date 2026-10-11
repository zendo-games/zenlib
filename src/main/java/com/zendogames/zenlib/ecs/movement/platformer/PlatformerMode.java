package com.zendogames.zenlib.ecs.movement.platformer;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.shapes.CollisionRect;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.components.Mover;
import com.zendogames.zenlib.ecs.movement.MoveIntent;
import com.zendogames.zenlib.ecs.movement.MoveWorld;
import com.zendogames.zenlib.utils.Calc;

import static com.zendogames.zenlib.ecs.movement.platformer.PlatformerMode.*;

// TODO: too big, split out the mode implementations into their own files

/**
 * The behaviors a {@link PlatformerMotion} switches between. Each is a stateless singleton: the
 * per-mover state is on the motion, the kinematics on the {@link Mover}. {@link #control} runs
 * before the sweep and returns the mode to sweep with; {@link #after} runs behind it and returns
 * the mode for the next tick.
 */
public sealed interface PlatformerMode permits Ballistic, Dash, Launched, Ledge, Player {

    /** Control half of the tick: write velocity, acceleration and timers from intent. Returns the mode to sweep with. */
    PlatformerMode control(PlatformerMotion p, Mover m, MoveIntent intent, float dt);

    /** After sweep and settle: react to landed / axis hits. Returns the mode for the next tick. */
    default PlatformerMode after(PlatformerMotion p, Mover m, MoveIntent intent, PointI2 pos, CollisionShape shape, MoveWorld world) {
        return this;
    }

    PlatformerMode BALLISTIC = new Ballistic();
    PlatformerMode DASH      = new Dash();
    PlatformerMode LAUNCHED  = new Launched();
    PlatformerMode LEDGE     = new Ledge();
    PlatformerMode PLAYER    = new Player();

    /** Falls under gravity, ignores intent, keeps whatever horizontal velocity it has. Crates, drops, debris. */
    final class Ballistic implements PlatformerMode {
        @Override
        public PlatformerMode control(PlatformerMotion p, Mover m, MoveIntent intent, float dt) {
            m.yAccel = (p.grounded && m.yVel <= 0) ? 0f : p.tuning.gravity();
            return this;
        }
    }

    /** A short burst at a fixed horizontal speed with gravity off. Ends on timer or on hitting a wall. */
    final class Dash implements PlatformerMode {

        /** Sets up a dash in {@code dir} and returns the mode for the caller to hand back to the step. */
        static PlatformerMode start(PlatformerMotion p, int dir) {
            var abilities = p.tuning.abilities();
            p.modeDir      = dir;
            p.modeTime     = abilities.dashTime();
            p.dashCooldown = abilities.dashCooldown();
            p.facing       = dir;
            return DASH;
        }

        @Override
        public PlatformerMode control(PlatformerMotion p, Mover m, MoveIntent intent, float dt) {
            p.modeTime -= dt;
            m.xVel   = p.modeDir * p.tuning.abilities().dashSpeed() * p.tuning.unit();
            m.yVel   = 0;
            m.yAccel = 0;
            return this;
        }

        @Override
        public PlatformerMode after(PlatformerMotion p, Mover m, MoveIntent intent, PointI2 pos, CollisionShape shape, MoveWorld world) {
            var hitWall = (m.xHit != 0);
            var isDashComplete = hitWall || (p.modeTime <= 0);
            if (!isDashComplete) return this;

            // Leave the dash at run speed rather than dash speed; a wall hit already zeroed xVel
            if (!hitWall) {
                m.xVel = p.modeDir * p.tuning.maxSpeed(p.grounded);
            }
            return PLAYER;
        }
    }

    /**
     * A body thrown by something else; intent is ignored, plain gravity, no friction and no
     * speed clamp, so the arc is exact. Hands back to {@link #PLAYER} on landing or when the lock runs out.
     */
    final class Launched implements PlatformerMode {

        /** See {@link PlatformerMotion#launch}. */
        static void start(PlatformerMotion p, Mover m, int dir, float dist, float height, float lock) {
            var hang = p.tuning.hangTime(height);
            m.xVel = dir * dist * p.tuning.unit() / hang;
            m.yVel = p.tuning.impulseForHeight(height);
            p.jumpActive = false; // the buffer is kept: a press just before launch still jumps on landing
            p.jumpCut    = false;
            p.coyote     = 0;
            p.modeTime = hang * lock;
            p.mode     = LAUNCHED;
        }

        @Override
        public PlatformerMode control(PlatformerMotion p, Mover m, MoveIntent intent, float dt) {
            p.modeTime -= dt;

            // Keep the jump buffer alive so a press just before touchdown still jumps on landing
            if      (intent.actionPressed()) p.jumpBuffer = p.tuning.jumpBufferTime();
            else if (p.jumpBuffer > 0)       p.jumpBuffer -= dt;

            m.yAccel = (p.grounded && m.yVel <= 0) ? 0f : p.tuning.gravity();
            return this;
        }

        @Override
        public PlatformerMode after(PlatformerMotion p, Mover m, MoveIntent intent, PointI2 pos, CollisionShape shape, MoveWorld world) {
            return (p.landed || p.modeTime <= 0) ? PLAYER : this;
        }
    }

    /**
     * Normal player movement: dash, jump (coyote, buffer, variable height), ledge grab,
     * wall (grab, jump, slide), gravity, friction.
     */
    final class Player implements PlatformerMode {

        @Override
        public PlatformerMode control(PlatformerMotion p, Mover m, MoveIntent intent, float dt) {
            var tuning    = p.tuning;
            var abilities = tuning.abilities();

            // Step timers --------------------------------------------------------

            // Dash timer, transition
            if (p.dashCooldown > 0) {
                p.dashCooldown -= dt;
            }
            var canDash = intent.dashPressed() && (p.dashCooldown <= 0) && (abilities.dashTime() > 0);
            if (canDash) {
                var dir = (intent.moveX() != 0) ? intent.moveX() : p.facing;
                return Dash.start(p, dir).control(p, m, intent, dt);
            }

            // Jump coyote timer
            if (!p.grounded && p.wasGrounded && !p.jumpActive && m.yVel <= 0) {
                p.coyote = tuning.coyoteTime();
            } else if (p.coyote > 0) {
                p.coyote -= dt;
            }

            // Jump buffer timer
            if (intent.actionPressed()) {
                p.jumpBuffer = tuning.jumpBufferTime();
            } else if (p.jumpBuffer > 0) {
                p.jumpBuffer -= dt;
            }

            // Ledge regrab lock
            if (p.ledgeRegrabLock > 0) {
                p.ledgeRegrabLock -= dt;
            }

            // Jump / drop decision -----------------------------------------------

            var wantsJump = intent.actionPressed() || (p.jumpBuffer > 0);
            if (wantsJump) {
                // Drop through a one-way platform
                if (intent.downHeld() && p.onOneWay) {
                    p.dropping   = true;
                    p.jumpBuffer = 0;
                }
                // Start a new jump
                else if (!p.jumpActive && (p.grounded || p.coyote > 0)) {
                    m.yVel       = tuning.jumpImpulse();
                    p.jumped     = true;
                    p.jumpActive = true;
                    p.jumpCut    = false;
                    p.jumpBuffer = 0;
                    p.coyote     = 0;
                }
            }

            // Variable-height jump: input released mid-rise
            if (p.jumpActive && !p.jumpCut && !intent.actionHeld()) {
                p.jumpCut = true;
            }

            var isOnWall = !p.grounded && (p.wallDir != 0);

            // Wall jump: jump wanted while touching a wall in the air,
            // 'jumped' is true when the ground / coyote jump above already took the input
            if (wantsJump && isOnWall && !p.jumped) {
                m.yVel       = tuning.jumpImpulse();
                m.xVel       = -p.wallDir * abilities.wallJumpSpeedX() * tuning.unit();
                p.facing     = -p.wallDir;
                p.steerLock  = abilities.wallJumpLockTime();
                p.jumped     = true;
                p.jumpActive = true;
                p.jumpCut    = false;
                p.jumpBuffer = 0;
                p.coyote     = 0;
            }

            // Horizontal movement ------------------------------------------------

            // Steer lock: for a short time after a wall jump the 'kick' velocity is carried exactly.
            // Steering is ignored and the rest of this section is skipped, so neither friction
            // nor the max-speed clamp can eat the kick.

            // A landing ends the steer lock early
            if (p.grounded) {
                p.steerLock = 0;
            }

            var isSteerLocked = (p.steerLock > 0);
            var moveX = isSteerLocked ? 0 : intent.moveX();

            if (isSteerLocked) {
                p.steerLock -= dt;
            } else {
                if (moveX != 0) {
                    p.facing = moveX;
                }

                // Steering while jumping
                if (p.isJumping(m) && moveX != 0) {
                    // Reverse direction instantly
                    if (Math.signum(m.xVel) == -moveX) {
                        m.xVel = 0;
                    }

                    // Ensure a minimum horizontal air speed
                    if (Math.abs(m.xVel) < (2 * tuning.unit())) {
                        m.xVel = moveX * 0.8f * tuning.maxSpeed(/*grounded*/ true);
                    }
                }

                // Accelerate toward the input direction, or decelerate toward rest without input
                if (moveX != 0 && moveX == p.wallDir) {
                    // Pushing into an adjacent wall is standing still: no velocity ramp between blocked pixels
                    m.xVel = 0;
                    m.xRem = 0;
                } else if (moveX != 0) {
                    m.xVel += moveX * tuning.accel(p.grounded) * dt;
                } else {
                    m.xVel = Calc.approach(m.xVel, 0, tuning.friction(p.grounded) * dt);
                }

                var maxSpeed = tuning.maxSpeed(p.grounded);
                m.xVel = Calc.clampf(m.xVel, -maxSpeed, maxSpeed);
            }

            // Vertical movement --------------------------------------------------

            // Wall slide: falling while pushing into the wall.
            // - Falling faster than the slide speed snaps to the slide speed
            // - slower movement accelerates under fall gravity and lands on it exactly (never overshoots)
            var isPushingIntoWall = (moveX == p.wallDir);
            var hasWallSlideSpeed = (abilities.wallSlideSpeed() > 0);
            var isSliding = isOnWall && isPushingIntoWall && hasWallSlideSpeed && p.isFalling(m);
            if (isSliding) {
                var slideVel = -abilities.wallSlideSpeed() * tuning.unit();
                if (m.yVel <= slideVel) {
                    m.yVel   = slideVel;
                    m.yAccel = 0;
                } else {
                    var fallAccel = tuning.gravity() * tuning.fallGravityMultiplier();
                    m.yAccel = Math.max(fallAccel, (slideVel - m.yVel) / dt); // both negative; take the gentler
                }
            } else {
                var gravMultiplier = 1f;
                var applyJumpCut = p.jumpActive && p.jumpCut;
                if      (!p.grounded && p.isRising(m) && applyJumpCut) gravMultiplier = tuning.jumpCutGravityMultiplier();
                else if (!p.grounded && p.isFalling(m))                gravMultiplier = tuning.fallGravityMultiplier();

                // None on the ground or in a wall slide, variable in the air
                m.yAccel = (p.grounded && m.yVel <= 0) ? 0f : tuning.gravity() * gravMultiplier;
            }

            return this;
        }

        @Override
        public PlatformerMode after(PlatformerMotion p, Mover m, MoveIntent intent, PointI2 pos, CollisionShape shape, MoveWorld world) {
            return Ledge.tryGrab(p, m, intent, pos, shape, world) ? LEDGE : this;
        }
    }

    /**
     * Hanging from a ledge: no motion until the player jumps or lets go. Entered from
     * {@link Player#after} through {@link #tryGrab}; always hands back to {@link #PLAYER}.
     */
    final class Ledge implements PlatformerMode {

        private static final int NO_LEDGE = Integer.MIN_VALUE;

        /** One-pixel probe shape. Placed through {@code probe}'s x,y args, so it is never mutated. */
        private static final CollisionShape PIXEL = new CollisionRect(0, 0, 1, 1);

        /**
         * Grabs a ledge if one is in reach: snaps the body so that its top is level with the
         * ledge and stops its movement. Returns true when the grab happened, the caller then
         * returns {@link #LEDGE}.
         */
        static boolean tryGrab(PlatformerMotion p, Mover m, MoveIntent intent, PointI2 pos, CollisionShape shape, MoveWorld world) {
            var tuning    = p.tuning;
            var abilities = tuning.abilities();

            var isOnWall         = (p.wallDir != 0);
            var isFacingWall     = isOnWall && (p.facing       == p.wallDir);
            var isSteeringAtWall = isOnWall && (intent.moveX() == p.wallDir);
            var grabRuleApplies  = switch (abilities.ledgeGrab()) {
                case AUTO -> isFacingWall;
                case PUSH -> isSteeringAtWall;
            };

            var hasAbility   = (abilities.ledgeGrabReach() > 0);
            var isDescending = !p.grounded && (m.yVel <= 0);
            var isUnlocked   = (p.ledgeRegrabLock <= 0);
            if (!(hasAbility && isDescending && grabRuleApplies && isUnlocked)) {
                return false;
            }

            int reach  = Math.round(abilities.ledgeGrabReach() * tuning.unit());
            int ledgeY = findLedgeY(p, pos, shape, world, reach);
            if (ledgeY == NO_LEDGE) {
                return false;
            }

            // The hang position puts the top of the collider level with the ledge
            int snapY = ledgeY - (int) shape.top(pos);
            if (world.probe(shape, pos.x, pos.y, 0, snapY) != Block.NONE) {
                return false; // the body does not fit there
            }

            pos.y += snapY;
            m.stop();
            m.yAccel = 0;
            p.clearJump(); // a press from before the grab must not fire as a wall jump on release
            p.steerLock = 0;
            return true;
        }

        @Override
        public PlatformerMode control(PlatformerMotion p, Mover m, MoveIntent intent, float dt) {
            // Hanging: nothing moves, whatever wrote velocity since the last tick
            m.stop();
            m.yAccel = 0;

            // Let go: down, steering away from the wall, or the wall is no longer there.
            // The body falls straight down, the lock stops it re-grabbing on the way past.
            var isWallGone   = (p.wallDir == 0);
            var isSteerAway  = (intent.moveX() != 0) && (intent.moveX() == -p.wallDir);
            var wantsRelease = (intent.downHeld() || isSteerAway || isWallGone);
            if (wantsRelease) {
                p.ledgeRegrabLock = p.tuning.abilities().ledgeRegrabTime();
                return PLAYER;
            }

            // Jump: straight up along the wall, steer onto the ledge from the air
            if (intent.actionPressed()) {
                m.yVel       = p.tuning.jumpImpulse();
                p.jumped     = true;
                p.jumpActive = true;
                p.jumpCut    = false;
                return PLAYER;
            }

            return this;
        }

        /**
         * Looks for a ledge in the pixel column just outside the collider on its wall side: the
         * top surface of the wall with air directly above it, within {@code reach} pixels above
         * or below the top of the body.
         *
         * @return world y of the ledge line, or {@link #NO_LEDGE}
         */
        private static int findLedgeY(PlatformerMotion p, PointI2 pos, CollisionShape shape, MoveWorld world, int reach) {
            int columnX = (p.wallDir > 0) ? (int) shape.right(pos) : (int) shape.left(pos) - 1;
            int topY    = (int) shape.top(pos);

            // Scan down the column: the ledge is the first solid pixel directly under a clear one
            var isAboveClear = isClear(world, columnX, topY + reach);
            for (int y = topY + reach - 1; y >= topY - reach; y--) {
                var isClear = isClear(world, columnX, y);
                if (!isClear && isAboveClear) {
                    return y + 1;
                }
                isAboveClear = isClear;
            }
            return NO_LEDGE;
        }

        private static boolean isClear(MoveWorld world, int x, int y) {
            return world.probe(PIXEL, x, y, 0, 0) != Block.SOLID;
        }
    }
}
