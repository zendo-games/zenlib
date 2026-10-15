# zenlib

Zendo Games' shared [libGDX](https://libgdx.com/) game library: the framework code, debug tooling
and starter assets we would otherwise copy into every new project. Games extend `ZenGame` and
`ZenScreen` and get startup and asset loading, flags, logging, preferences, time and a fixed-step
simulation, screen transitions, a small ECS with collision, movement and behavior systems, tilemaps,
and an ImGui debug window.

> **Status:** `0.1.0`, early development. Published through [JitPack](https://jitpack.io/#zendo-games/zenlib).
> The API will change between minor versions.

## What's in it

| Area | Package | Notes |
|---|---|---|
| Startup, flags, logging, prefs, time | `com.zendogames.zenlib` | `ZenGame`, `ZenConfig`, `Flag`/`Flags`, `Log`, `Prefs`, `Time`, `FramePool` |
| Assets | `assets` | `Assets` manager with typed enum keys (`AtlasType`, `AnimType`, `FontType`, `SkinType`, `IconType`, `MapType`); bundled atlas and JetBrains Mono font |
| ECS | `ecs`, `ecs/components`, `ecs/systems` | custom `World`/`Entity`/`Component`/`Family`/`EntitySystem`, no reflection |
| Movement | `ecs/movement` (+ `platformer`, `topdown`) | pixel-walk `Mover` with pluggable `Motion` strategies |
| Collision | `collision`, `collision/shapes` | rect, circle, capsule, one-way segment and tile grid shapes; probes, overlaps, raycasts, contacts, sensors |
| State machines | `state` | `State<C>` enums driving a per-owner `StateMachine<C>`; `Behavior` component |
| Tilemaps | `tilemap` | Tiled `.tmx` as an asset, one entity per layer, grid colliders, object spawning |
| Screens and input | `screens`, `screens/transition`, `input` | `ZenScreen`, shader screen transitions, event-driven `ScreenInputProcessor` |
| Rendering | `render` | `WorldView` viewport (optional framebuffer path), `CameraFollow`, render layers |
| Debug UI | `imgui`, `ui` | Dear ImGui context (`ImGuiGdx`), the Debug window, `DebugView`s over library objects, `DebugActions`, `ScreenSwitcher` |
| Simulation, utilities | `sim`, `utils`, `accessors` | `SimulationStep`, `Scheduler`, math and drawing helpers, tween accessors |

## Platforms

| Platform | Backend | Status |
|---|---|---|
| Desktop | LWJGL3 | supported |
| Web | [gdx-teavm](https://github.com/xpenatan/gdx-teavm) 1.6.2 (TeaVM 0.15) | supported, wasm release target verified |
| GWT | | not supported |
| Android, iOS | | untested |

zenlib contains no reflection and no platform code; games keep the backends in their launcher modules.

## Requirements

- **JDK 25.** Sources, bytecode (`--release 25`) and the Gradle daemon toolchain all target Java 25,
  and the published Gradle module metadata says so, so a game must build with Java 25 too.
- libGDX 1.14.2 and gdx-freetype 1.14.2.
- Gradle 9 (the wrapper is included; the daemon JDK is provisioned through
  `gradle/gradle-daemon-jvm.properties`).

## Dependencies

Everything below is an `api` dependency: games see these on their classpath and do not declare them
again. Versions are pinned in [`gradle.properties`](gradle.properties).

| Dependency | Version | Used for |
|---|---|---|
| [libGDX](https://libgdx.com/) `gdx`, `gdx-freetype` | 1.14.2 | everything; FreeType for the bundled font |
| [gdcrux](https://github.com/tommyettinger/gdcrux) | 0.1.2 | `PointF2`/`PointI2` value types behind `FramePool` |
| [TextraTypist](https://github.com/tommyettinger/textratypist) | 2.5.0 | `Font`/`Layout` text rendering |
| [Universal Tween Engine](https://github.com/mini2Dx/universal-tween-engine) | 6.3.3 | tweening, with accessors in `accessors/` |
| [ShapeDrawer](https://github.com/earlygrey/shapedrawer) | 2.6.0 | debug outlines and shapes |
| [jImGui](https://github.com/xpenatan/xImGui) `imgui-core`, `gdx-gl-impl` | `zendo-20260914` | Dear ImGui binding for the debug UI (see below) |

## How jImGui is managed

jImGui only publishes moving `-SNAPSHOT` builds, and its one tagged release targets an older TeaVM
than gdx-teavm 1.6.2 uses. So a snapshot of jImGui and of the [jParser](https://github.com/xpenatan/jParser)
runtime it depends on is frozen under fixed versions (`zendo-20260914`, `zendo-20260920`) in
[`vendor/maven`](vendor/README.md), a plain Maven repository committed to this repo. Group and
artifact names are upstream's, only the version string differs, so switching back to an upstream
release later is a version bump.

What that means in practice:

- zenlib's own build reads `vendor/maven` as a local repository.
- A game adds the same repository through its raw GitHub URL (next section); the jars come straight
  from this repo, nothing else is hosted.
- The desktop and web platform pieces of jImGui are **not** zenlib dependencies. Each launcher module
  declares them, the same way it declares libGDX's native jars.
- The web payloads (`imgui.wasm`, jParser's `runtime.wasm`) are selected for gdx-teavm by zenlib's
  `META-INF/gdx-teavm.properties`, so a web build needs no extra step beyond the two `imgui-web`
  dependencies. Run `teavm:clean` after changing the jImGui version; the web `scripts/` folder is
  never cleaned otherwise.
- A frozen version folder stays in `vendor/maven` for as long as any published zenlib release
  depends on it.

## Using zenlib in a game

The instructions assume a [gdx-liftoff](https://github.com/libgdx/gdx-liftoff) project with `core`,
`lwjgl3` and `teavm` modules. zenlib is resolved from JitPack under the coordinates
`com.github.zendo-games:zenlib`.

```properties
# gradle.properties
zenlibVersion=0.1.0
jImGuiVersion=zendo-20260914
```

`zenlibVersion` can also be a commit hash (`3c161b7`) or `main-SNAPSHOT` for the latest build of
`main`. Gradle caches snapshots for 24 hours; use `--refresh-dependencies` when iterating against one.

```groovy
// root build.gradle, inside subprojects { repositories { ... } }
mavenCentral()
maven { url = 'https://jitpack.io' }
maven {
    // jImGui/jParser snapshot builds frozen in zenlib's repository, see its vendor/README.md
    url = 'https://raw.githubusercontent.com/zendo-games/zenlib/main/vendor/maven'
    content {
        includeGroup 'com.github.xpenatan.jImGui'
        includeGroup 'com.github.xpenatan.jParser'
    }
}
```

```groovy
// core/build.gradle
dependencies {
    api "com.github.zendo-games:zenlib:$zenlibVersion"
}
```

```groovy
// lwjgl3/build.gradle
dependencies {
    implementation "com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion"
    implementation "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop"
    implementation "com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop"
    implementation "com.github.xpenatan.jImGui:imgui-jni:$jImGuiVersion"
    implementation "com.github.xpenatan.jImGui:imgui-jni_windows_x64:$jImGuiVersion"
    implementation "com.github.xpenatan.jImGui:imgui-jni_linux_x64:$jImGuiVersion"
    implementation "com.github.xpenatan.jImGui:imgui-jni_mac_x64:$jImGuiVersion"
    implementation "com.github.xpenatan.jImGui:imgui-jni_mac_arm64:$jImGuiVersion"
}
```

```groovy
// teavm/build.gradle
dependencies {
    implementation "com.github.xpenatan.gdx-teavm:backend-web:$gdxTeaVMVersion"
    implementation "com.github.xpenatan.gdx-teavm:gdx-freetype-web:$gdxTeaVMVersion"
    implementation "com.github.xpenatan.jImGui:imgui-web:$jImGuiVersion"
    implementation "com.github.xpenatan.jImGui:imgui-web_wasm:$jImGuiVersion"
}
```

Launcher notes: the web launcher should set `config.useGL30 = true` (jImGui's `ImGuiGdxGLImpl` is used
on both platforms); the desktop launcher works with liftoff's defaults, and `setOpenGLEmulation(GL32, 3, 2)`
is what the demo uses.

### Minimal game

```java
public class Main extends ZenGame {

    @Override
    public ZenConfig config() {
        return new ZenConfig("my-game-prefs", 1280, 720);
    }

    @Override
    protected void init() {
        // register the game's flags and asset enums
        Flags.register(GameFlag.values());
        assets.register(GameAtlas.values());
    }

    @Override
    protected ZenScreen createFirstScreen() {
        return new FirstScreen(this);
    }
}
```

`ZenGame` owns startup: it initializes the library, creates the shared renderers, loads ImGui, calls
`init()`, loads every registered asset while drawing a progress view, then asks for the first screen.
Screens are created and disposed by `ZenGame.setScreen` alone. Games override `init()` and
`shutdown()`, never `create()`/`dispose()`.

## Developing against a game project

[zenlib-demo](https://github.com/bploeckelman/zenlib-demo) is a gdx-liftoff project that exercises
zenlib the way a real game does, one screen per library slice, on desktop and web. It lives in its own
repo and pulls zenlib in from a sibling checkout with a Gradle composite build, so library changes are
picked up on the next build with no publishing step.

```
ZendoGames/
  zenlib/         this repo
  zenlib-demo/    gdx-liftoff project: core, lwjgl3, teavm
```

```groovy
// zenlib-demo/settings.gradle

// Builds zenlib from the sibling checkout, replacing the 'com.github.zendo-games:zenlib' dependency.
// Pass -PzenlibPublished to ignore the checkout and resolve the published artifact instead.
def zenlibCheckout = file('../zenlib')
def usePublished = providers.gradleProperty('zenlibPublished').isPresent()
if (zenlibCheckout.exists() && !usePublished) {
  includeBuild(zenlibCheckout) {
    dependencySubstitution {
      substitute module('com.github.zendo-games:zenlib') using project(':')
    }
  }
}
```

The game's `core` module keeps the JitPack coordinates (`com.github.zendo-games:zenlib`); the
substitution maps them onto the checkout, and `-PzenlibPublished` makes the same build resolve the
JitPack artifact instead. Any game can use the snippet to work against local zenlib sources.

| The composite build exercises | It does not exercise |
|---|---|
| the built jar, bundled assets and `META-INF/` resources | the published POM and module metadata |
| `api` visibility to the game | JitPack's build of this repo |
| launcher-side dependencies (jImGui natives, gdx-teavm) | the raw GitHub vendor URL (the sibling folder is used when present) |
| TeaVM compilation of zenlib's bytecode | |

Things to know: when included, zenlib reads its own `gradle.properties` for dependency versions, but
`org.gradle.*` settings and the daemon JVM come from the including project; IntelliJ imports both builds
into one window when the game project is opened.

## Building

```shell
./gradlew build               # compile, test (JUnit 4, headless), assemble jars
./gradlew test
./gradlew javadoc             # writes docs/apidocs
./gradlew publishToMavenLocal # com.zendogames:zenlib:<VERSION_NAME> into ~/.m2
```

Gradle output is set to `quiet` in `gradle.properties`; add `--info` when you need more. The
`texturePacker` task packs `sprites/` into the bundled atlas before `processResources`; its output is
committed, so a build without sprite changes does not touch it.

## Releasing

Releases are built by JitPack from git tags; nothing is uploaded by hand. JitPack runs the
`install` command in [`jitpack.yml`](jitpack.yml): the standard Gradle publish to its local Maven
repository, followed by removing `vendor/maven` so JitPack's artifact scan does not mistake the
vendored jImGui POMs for modules of zenlib.

1. Set `VERSION_NAME` in `gradle.properties` to the release version (`0.1.0`) and update the version
   in this README. Run `./gradlew build`.
2. Commit, tag with the plain version number and push both:
   ```shell
   git tag -a 0.1.0 -m "zenlib 0.1.0"
   git push origin main 0.1.0
   ```
3. Trigger and check the JitPack build: open https://jitpack.io/#zendo-games/zenlib and click
   "Get it" on the tag, or request the POM directly (blocks until the build ends, about 90 seconds):
   ```shell
   curl -sSL -o /dev/null -w "%{http_code}\n" https://jitpack.io/com/github/zendo-games/zenlib/0.1.0/zenlib-0.1.0.pom
   ```
   In the build log, the only "Found artifact" lines should be zenlib's, and the POM's jImGui
   dependencies should keep the `com.github.xpenatan.jImGui` group. A tag's artifacts become
   immutable after seven days; before that a bad build can be deleted and rebuilt from the JitPack page.
4. Bump `VERSION_NAME` to the next `-SNAPSHOT` and commit.

Before tagging, make sure every frozen version under `vendor/maven` that the release depends on is
committed; consumers read it from the `main` branch.

## Project layout

```
build.gradle                                       library build and publishing
gradle.properties                                  dependency versions, VERSION_NAME and POM metadata
jitpack.yml                                        JitPack build (JDK 25, vendor folder removed after publish)
src/main/java/com/zendogames/zenlib/               library sources
src/main/resources/com/zendogames/zenlib/assets/   bundled assets: sprites/zen.{atlas,png}, fonts/, shaders/transitions/
src/main/resources/META-INF/gdx-teavm.properties   tells gdx-teavm which web payload jars to package
src/test/java/com/zendogames/zenlib/               tests (JUnit 4, headless)
sprites/                                           texture packer input for the bundled atlas
tools/mirror.py                                    refreshes the vendored jImGui builds
vendor/maven/                                      frozen jImGui/jParser builds, see vendor/README.md
docs/apidocs/                                      generated Javadoc
```

## Credits

Started from [tommyettinger/libgdx-library-template](https://github.com/tommyettinger/libgdx-library-template).
The debug UI is [Dear ImGui](https://github.com/ocornut/imgui) through xpenatan's jImGui and jParser.

## License

[Apache License 2.0](LICENSE)

Bundled fonts are under the SIL Open Font License 1.1, see
`src/main/resources/com/zendogames/zenlib/assets/fonts/LICENSE-OFL.txt`. The vendored jImGui and
jParser builds are Apache 2.0 and bundle Dear ImGui (MIT); see [`vendor/README.md`](vendor/README.md).
