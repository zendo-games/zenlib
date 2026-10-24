package com.zendogames.zenlib.ui;

import com.github.xpenatan.jparser.runtime.helper.NativeBoolArray;
import com.github.xpenatan.jparser.runtime.helper.NativeFloatArray;
import com.zendogames.zenlib.FramePool;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.*;
import com.zendogames.zenlib.ecs.movement.Motion;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerMotion;
import com.zendogames.zenlib.ecs.movement.topdown.TopDownMotion;
import com.zendogames.zenlib.ecs.systems.ContactSystem;
import com.zendogames.zenlib.input.InputState;
import com.zendogames.zenlib.input.Pad;
import com.zendogames.zenlib.input.PadAxis;
import imgui.ImGui;
import imgui.enums.ImGuiTableColumnFlags;
import imgui.enums.ImGuiTableFlags;

/**
 * {@link DebugView}s over library objects. Each factory captures what it reads and is created once
 * per screen (a {@code world.family(...)} is interned, so creating the timers view per frame would
 * be harmless, but the screen holding a field is a clearer). Everything here depends only on library
 * types, which allows zenlib to provide some generic views out of the box.
 */
public final class DebugViews {

    private DebugViews() {}

    /** Per-type active, peak and free counts of the frame pool. */
    public static DebugView framePool() {
        return new DebugView() {
            @Override public String title()          { return "FramePool"; }
            @Override public boolean openByDefault() { return false; }
            @Override public void draw() {
                if (ImGui.BeginTable("frame-pool", 4, TableFlags.TABLE)) {
                    ImGui.TableSetupColumn("Type",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Active", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Peak",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Free",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableHeadersRow();
                    for (var type : FramePool.types()) {
                        ImGui.TableNextRow();
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(type.name());
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(String.valueOf(FramePool.active(type)));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(String.valueOf(FramePool.peak(type)));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(String.valueOf(FramePool.free(type)));
                    }
                    ImGui.EndTable();
                }
            }
        };
    }

    /** Entity count, tick, interpolation alpha, and a table of systems with live enabled toggles. */
    public static DebugView ecs(World world) {
        return new DebugView() {
            @Override public String title() { return "ECS"; }
            @Override public void draw() {
                ImGui.TextUnformatted("entities: " + world.entities().size);
                ImGui.SameLine(); ImGui.TextUnformatted("  tick: " + world.step.tick);
                ImGui.SameLine(); ImGui.TextUnformatted("  alpha: %.3f".formatted(world.alpha()));
                ImGui.SameLine(); ImGui.TextUnformatted("  updating: " + world.isUpdating());

                if (ImGui.BeginTable("systems", 4, TableFlags.TABLE)) {
                    ImGui.TableSetupColumn("System",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Priority", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Runs",     ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Enabled",  ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableHeadersRow();
                    var systems = world.systems();
                    for (int i = 0; i < systems.size; i++) {
                        var system = systems.get(i);
                        ImGui.PushID(i);
                        ImGui.TableNextRow();
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(system.getClass().getSimpleName());
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(String.valueOf(system.priority));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(system.runsOnTick() ? "tick" : "render");
                        ImGui.TableNextColumn();
                        Buffers.checkbox.setValue(0, system.enabled);
                        if (ImGui.Checkbox("##enabled", Buffers.checkbox)) {
                            system.enabled = Buffers.checkbox.getValue(0);
                        }
                        ImGui.PopID();
                    }
                    ImGui.EndTable();
                }
            }
        };
    }

    /** Every active timer in the world: owner, name, time left, repeat. Rows follow {@code Timers} add order per entity. */
    public static DebugView timers(World world) {
        var family = world.family(Family.all(Timers.TYPE));
        return new DebugView() {
            @Override public String title() { return "Timers"; }
            @Override public void draw() {
                var entities = family.entities();
                int total = 0;
                for (int i = 0; i < entities.size; i++) total += Timers.get(entities.get(i)).size();
                ImGui.TextUnformatted("entities: " + entities.size + "  timers: " + total);

                if (ImGui.BeginTable("timers", 5, TableFlags.TABLE)) {
                    ImGui.TableSetupColumn("Entity",    ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Timer",     ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Remaining", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Duration",  ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Repeat",    ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableHeadersRow();
                    for (int i = 0; i < entities.size; i++) {
                        var e = entities.get(i);
                        var list = Timers.get(e).timers();
                        for (int j = 0; j < list.size; j++) {
                            var timer = list.get(j);
                            ImGui.TableNextRow();
                            ImGui.TableNextColumn(); ImGui.TextUnformatted(Name.of(e) + " " + e);
                            ImGui.TableNextColumn(); ImGui.TextUnformatted(timer.name);
                            ImGui.TableNextColumn(); ImGui.TextUnformatted("%.2fs (%d)".formatted(timer.remainingSeconds(), timer.remainingTicks()));
                            ImGui.TableNextColumn(); ImGui.TextUnformatted("%.2fs".formatted(timer.durationSeconds()));
                            ImGui.TableNextColumn(); ImGui.TextUnformatted(timer.isRepeating() ? "every" : "once");
                        }
                    }
                    ImGui.EndTable();
                }
            }
        };
    }

    /**
     * Every mover in the world: position, velocity, this tick's hits, the motion class and its
     * facts (mode, grounded, wall and facing for a platformer; facing for top-down).
     */
    public static DebugView movers(World world) {
        var family = world.family(Family.all(Position.TYPE, Mover.TYPE));
        return new DebugView() {
            @Override public String title() { return "Movers"; }
            @Override public void draw() {
                var entities = family.entities();
                ImGui.TextUnformatted("movers: " + entities.size);

                if (ImGui.BeginTable("movers", 6, TableFlags.TABLE)) {
                    ImGui.TableSetupColumn("Entity",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Motion",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Position", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Velocity", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Hit",      ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Facts",    ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableHeadersRow();
                    for (int i = 0; i < entities.size; i++) {
                        var e = entities.get(i);
                        var pos = Position.get(e);
                        var mover = Mover.get(e);
                        ImGui.TableNextRow();
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(Name.of(e) + " " + e);
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(motionName(mover));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted("%d, %d".formatted(pos.x, pos.y));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted("%.1f, %.1f".formatted(mover.xVel, mover.yVel));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted("%+d, %+d".formatted(mover.xHit, mover.yHit));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(facts(mover));
                    }
                    ImGui.EndTable();
                }
            }
        };
    }

    private static String motionName(Mover mover) {
        if (mover.motion == Motion.NONE) return "NONE";
        var name = mover.motion.getClass().getSimpleName();
        return name.isEmpty() ? "lambda" : name;
    }

    private static String facts(Mover mover) {
        if (mover.motion instanceof PlatformerMotion p) {
            return "%s%s%s face %+d".formatted(
                    p.mode.getClass().getSimpleName(),
                    p.grounded ? (p.onOneWay ? " on one-way" : " grounded") : " air",
                    (p.wallDir != 0) ? " wall %+d".formatted(p.wallDir) : "",
                    p.facing);
        }
        if (mover.motion instanceof TopDownMotion t) {
            return "face %+d, %+d".formatted(t.faceX, t.faceY);
        }
        return "";
    }

    /** This tick's contact count, the log toggles, and the ENTER / EXIT transition log, newest last. */
    public static DebugView contacts(ContactSystem system) {
        return new DebugView() {
            @Override public String title() { return "Contacts"; }
            @Override public void draw() {
                ImGui.TextUnformatted("this tick: " + system.contacts.size);

                Buffers.paused.setValue(0, system.logPaused);
                if (ImGui.Checkbox("pause log (P)", Buffers.paused)) {
                    system.logPaused = Buffers.paused.getValue(0);
                }
                ImGui.SameLine();
                Buffers.blockedExits.setValue(0, system.logBlockedExits);
                if (ImGui.Checkbox("blocked exits (X)", Buffers.blockedExits)) {
                    system.logBlockedExits = Buffers.blockedExits.getValue(0);
                }
                ImGui.SameLine();
                if (ImGui.Button("clear")) {
                    system.log.clear();
                }

                if (ImGui.BeginChild("log", imgui.ImTemp.ImVec2_1(0, 200))) {
                    for (var line : system.log) {
                        ImGui.TextUnformatted(line);
                    }
                    if (!system.logPaused) {
                        ImGui.SetScrollHereY(1f);
                    }
                }
                ImGui.EndChild();
            }
        };
    }

    /** Transitions kept per machine while its Trace box is ticked in {@link #behaviors}. */
    private static final int HISTORY_CAPACITY = 32;

    /**
     * Every {@code Behavior} in the world: current state, time in it, previous and pending state.
     * Ticking a row's Trace box makes that machine keep its last transitions and refusals, listed
     * under the table (newest last) until the box is cleared.
     */
    public static DebugView behaviors(World world) {
        var family = world.family(Family.all(Behavior.TYPE));
        return new DebugView() {
            @Override public String title() { return "Behaviors"; }
            @Override public void draw() {
                var entities = family.entities();
                ImGui.TextUnformatted("behaviors: " + entities.size);

                if (ImGui.BeginTable("behaviors", 6, TableFlags.TABLE)) {
                    ImGui.TableSetupColumn("Entity",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("State",    ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("For",      ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Previous", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Pending",  ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Trace",    ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableHeadersRow();
                    for (int i = 0; i < entities.size; i++) {
                        var e = entities.get(i);
                        var m = Behavior.get(e).machine;
                        ImGui.PushID(i);
                        ImGui.TableNextRow();
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(Name.of(e) + " " + e);
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(String.valueOf(m.current()));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(m.isStarted() ? "%.2fs (%d)".formatted(m.seconds(), m.ticks()) : "not started");
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(m.previous() == null ? "" : String.valueOf(m.previous()));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(m.pending()  == null ? "" : String.valueOf(m.pending()));
                        ImGui.TableNextColumn();
                        Buffers.checkbox.setValue(0, m.isRecording());
                        if (ImGui.Checkbox("##trace", Buffers.checkbox)) {
                            m.record(Buffers.checkbox.getValue(0) ? HISTORY_CAPACITY : 0);
                        }
                        ImGui.PopID();
                    }
                    ImGui.EndTable();
                }

                for (int i = 0; i < entities.size; i++) {
                    var e = entities.get(i);
                    var m = Behavior.get(e).machine;
                    if (!m.isRecording()) continue;
                    if (ImGui.TreeNode(Name.of(e) + " " + e)) {
                        for (var line : m.history) {
                            ImGui.TextUnformatted(line);
                        }
                        ImGui.TreePop();
                    }
                }
            }
        };
    }

    /**
     * Every {@code Sensor} in the world: whisker count, range, facing, and the nearest terrain and
     * threat readings (hit count, distance and the whisker's angle); a tree node per entity lists
     * each whisker's latest reading.
     */
    public static DebugView sensors(World world) {
        var family = world.family(Family.all(Position.TYPE, Collider.TYPE, Sensor.TYPE));
        return new DebugView() {
            @Override public String title() { return "Sensors"; }
            @Override public void draw() {
                var entities = family.entities();
                ImGui.TextUnformatted("sensors: " + entities.size);

                if (ImGui.BeginTable("sensors", 7, TableFlags.TABLE)) {
                    ImGui.TableSetupColumn("Entity",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Whiskers", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Range",    ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Facing",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Terrain",  ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Threat",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Sight",    ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableHeadersRow();
                    for (int i = 0; i < entities.size; i++) {
                        var e = entities.get(i);
                        var sensor = Sensor.get(e);
                        ImGui.TableNextRow();
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(Name.of(e) + " " + e);
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(String.valueOf(sensor.whiskers.length));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted("%.0f".formatted(sensor.range));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted("%+d".formatted(sensor.facing));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(reading(sensor, sensor.terrain));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(reading(sensor, sensor.threats));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(sight(sensor));
                    }
                    ImGui.EndTable();
                }

                for (int i = 0; i < entities.size; i++) {
                    var e = entities.get(i);
                    if (ImGui.TreeNode(Name.of(e) + " " + e)) {
                        var sensor = Sensor.get(e);
                        for (var w : sensor.whiskers) {
                            ImGui.TextUnformatted(whisker(w));
                        }
                        for (int j = 0; j < sensor.sightingCount; j++) {
                            ImGui.TextUnformatted(sighting(sensor.sightings[j]));
                        }
                        ImGui.TreePop();
                    }
                }
            }
        };
    }

    /** "2 hits, nearest 24 at -45 deg" or "clear". */
    private static String reading(Sensor sensor, int masks) {
        var nearest = sensor.nearest(masks);
        if (nearest == null) return "clear";
        var hits = sensor.count(masks);
        return "%d hit%s, nearest %.0f at %+.0f deg".formatted(hits, (hits == 1) ? "" : "s", nearest.distance, nearest.angleDeg);
    }

    private static String whisker(Sensor.Whisker w) {
        var hit = w.isHit() ? "%s %s %s".formatted(w.block, w.mask(), Name.of(w.entity)) : "clear";
        return "%+5.0f deg  %6.1f  %s".formatted(w.angleDeg, w.distance, hit);
    }

    /** "2 seen of 3, nearest 24", "none of 2 seen", "nothing in range", or "-" when the sensor has no sight rays. */
    private static String sight(Sensor sensor) {
        if (sensor.sightings == null) return "-";
        if (sensor.sightingCount == 0) return "nothing in range";
        var nearest = sensor.nearestSeen();
        if (nearest == null) return "none of %d seen".formatted(sensor.sightingCount);
        return "%d seen of %d, nearest %.0f".formatted(sensor.seenCount(), sensor.sightingCount, nearest.distance);
    }

    private static String sighting(Sensor.Sighting s) {
        var view = s.seen() ? "seen" : s.isHit() ? "hidden by " + Name.of(s.entity) : "not reached";
        return "sight %-10s %6.1f  %s".formatted(Name.of(s.target), s.distance, view);
    }

    /** Every map in the world: size and counts, then one row per tile and image layer with a live visibility toggle (collision layers start hidden). */
    public static DebugView tilemaps(World world) {
        var family = world.family(Family.all(Tilemap.TYPE));
        return new DebugView() {
            @Override public String title() { return "Tilemaps"; }
            @Override public void draw() {
                var entities = family.entities();
                if (entities.size == 0) ImGui.TextUnformatted("no tilemaps");
                for (int i = 0; i < entities.size; i++) {
                    var entity = entities.get(i);
                    var tilemap = Tilemap.get(entity);
                    ImGui.PushID(i);
                    ImGui.TextUnformatted("%s %s: %dx%d tiles of %d px, %d layers (%d collision), %d images, %d objects".formatted(
                            Name.of(entity), entity, tilemap.cols, tilemap.rows, tilemap.tileSize, tilemap.layers.size, tilemap.collisionLayers.size, tilemap.images.size, tilemap.objects.size));

                    if (ImGui.BeginTable("layers", 5, TableFlags.TABLE)) {
                        ImGui.TableSetupColumn("Layer",   ImGuiTableColumnFlags.WidthStretch);
                        ImGui.TableSetupColumn("Render",  ImGuiTableColumnFlags.WidthStretch);
                        ImGui.TableSetupColumn("Order",   ImGuiTableColumnFlags.WidthStretch);
                        ImGui.TableSetupColumn("Kind",    ImGuiTableColumnFlags.WidthStretch);
                        ImGui.TableSetupColumn("Visible", ImGuiTableColumnFlags.WidthStretch);
                        ImGui.TableHeadersRow();
                        var layers = tilemap.layerEntities;
                        for (int j = 0; j < layers.size; j++) {
                            var layerEntity = layers.get(j);
                            if (!layerEntity.isAlive()) continue;
                            var tileLayer = TileLayer.get(layerEntity);
                            ImGui.PushID(j);
                            tileLayer.visible = layerRow(tileLayer.name() + " " + layerEntity, tileLayer, tileLayer.collision ? "collision" : "tiles", tileLayer.visible);
                            ImGui.PopID();
                        }
                        var images = tilemap.imageEntities;
                        for (int j = 0; j < images.size; j++) {
                            var imageEntity = images.get(j);
                            if (!imageEntity.isAlive()) continue;
                            var imageLayer = TileImageLayer.get(imageEntity);
                            ImGui.PushID(layers.size + j);
                            imageLayer.visible = layerRow(imageLayer.name() + " " + imageEntity, imageLayer, "image", imageLayer.visible);
                            ImGui.PopID();
                        }
                        ImGui.EndTable();
                    }
                    ImGui.PopID();
                }
            }
        };
    }

    /** Every {@code SpineAnimator}: what track 0 plays, with pickers for its animations and skins and a time scale slider. */
    public static DebugView spine(World world) {
        var family = world.family(Family.all(SpineAnimator.TYPE));
        return new DebugView() {
            @Override public String title() { return "Spine"; }
            @Override public void draw() {
                var entities = family.entities();
                ImGui.TextUnformatted("skeletons: " + entities.size);

                if (ImGui.BeginTable("spine", 5, TableFlags.TABLE)) {
                    ImGui.TableSetupColumn("Entity",    ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Animation", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Time",      ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Skin",      ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Speed",     ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableHeadersRow();
                    for (int i = 0; i < entities.size; i++) {
                        var e = entities.get(i);
                        var anim = SpineAnimator.get(e);
                        var data = anim.skeleton.getData();
                        var current = anim.state.getTrack(0);
                        var playing = current == null ? "(none)" : current.getAnimation().getName();
                        var skin = anim.skeleton.getSkin();
                        ImGui.PushID(i);
                        ImGui.TableNextRow();
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(Name.of(e) + " " + e);
                        ImGui.TableNextColumn();
                        if (ImGui.BeginCombo("##animation", playing)) {
                            for (var animation : data.getAnimations()) {
                                var name = animation.getName();
                                if (ImGui.Selectable(name, name.equals(playing))) {
                                    anim.start(name, current == null || current.getLoop());
                                }
                            }
                            ImGui.EndCombo();
                        }
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(current == null ? "" : "%.2fs%s".formatted(current.getTrackTime(), current.getLoop() ? " loop" : (current.isComplete() ? " done" : "")));
                        ImGui.TableNextColumn();
                        if (ImGui.BeginCombo("##skin", skin == null ? "(none)" : skin.getName())) {
                            for (var candidate : data.getSkins()) {
                                var name = candidate.getName();
                                if (ImGui.Selectable(name, skin != null && name.equals(skin.getName()))) {
                                    anim.skin(name);
                                }
                            }
                            ImGui.EndCombo();
                        }
                        ImGui.TableNextColumn();
                        Buffers.slider.setValue(0, anim.state.getTimeScale());
                        if (ImGui.SliderFloat("##speed", Buffers.slider, 0f, 2f)) {
                            anim.state.setTimeScale(Buffers.slider.getValue(0));
                        }
                        ImGui.PopID();
                    }
                    ImGui.EndTable();
                }
            }
        };
    }

    /** The game's {@link InputState}: the clock and mouse, then one row per gamepad slot with its axes and the buttons down. */
    public static DebugView input(InputState input) {
        var pads = Pad.values();
        var axes = PadAxis.values();
        var text = new StringBuilder();
        return new DebugView() {
            @Override public String title() { return "Input"; }
            @Override public void draw() {
                var now = input.now;
                ImGui.TextUnformatted("time %.2f  mouse %d, %d  wheel %.0f, %.0f  gamepads %s".formatted(
                        input.time, now.mouseX, now.mouseY, now.wheelX, now.wheelY,
                        input.controllerSource == null ? "off" : "polled"));

                if (ImGui.BeginTable("gamepads", 4, TableFlags.TABLE)) {
                    ImGui.TableSetupColumn("Slot", ImGuiTableColumnFlags.WidthFixed);
                    ImGui.TableSetupColumn("Name", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Axes", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableSetupColumn("Down", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.TableHeadersRow();
                    for (int slot = 0; slot < now.gamepads.length; slot++) {
                        var gamepad = now.gamepads[slot];
                        ImGui.TableNextRow();
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(Integer.toString(slot));
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(gamepad.connected ? gamepad.name : "-");
                        text.setLength(0);
                        for (var axis : axes) {
                            text.append(axis.name()).append(' ').append("%+.2f".formatted(gamepad.axes[axis.ordinal()])).append("  ");
                        }
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(gamepad.connected ? text.toString() : "");
                        text.setLength(0);
                        for (var pad : pads) {
                            if (gamepad.buttons.down(pad.ordinal())) text.append(pad.name()).append(' ');
                        }
                        ImGui.TableNextColumn(); ImGui.TextUnformatted(text.toString());
                    }
                    ImGui.EndTable();
                }
            }
        };
    }

    /** One row of the tilemaps table; returns the visibility after the checkbox, changed or not. */
    private static boolean layerRow(String name, Renderable layer, String kind, boolean visible) {
        ImGui.TableNextRow();
        ImGui.TableNextColumn(); ImGui.TextUnformatted(name);
        ImGui.TableNextColumn(); ImGui.TextUnformatted(layer.layer.layerName());
        ImGui.TableNextColumn(); ImGui.TextUnformatted(String.valueOf(layer.order));
        ImGui.TableNextColumn(); ImGui.TextUnformatted(kind);
        ImGui.TableNextColumn();
        Buffers.checkbox.setValue(0, visible);
        if (ImGui.Checkbox("##visible", Buffers.checkbox)) {
            return Buffers.checkbox.getValue(0);
        }
        return visible;
    }

    /**
     * Native buffers allocated on first use by this holder class: a {@code NativeBoolArray} created
     * before {@code ImGuiGdx.load()} crashes. One checkbox buffer is reused per table row; PushID
     * keeps the repeated label unique.
     */
    private static final class Buffers {
        static final NativeBoolArray  checkbox     = new NativeBoolArray(1);
        static final NativeBoolArray  paused       = new NativeBoolArray(1);
        static final NativeBoolArray  blockedExits = new NativeBoolArray(1);
        static final NativeFloatArray slider       = new NativeFloatArray(1);
    }

    private static class TableFlags {
        private static final ImGuiTableFlags TABLE = ImGuiTableFlags.Borders
                .or(ImGuiTableFlags.RowBg).or(ImGuiTableFlags.SizingStretchSame);
    }
}
