# zenlib

Zendo Games' shared [libGDX](https://libgdx.com/) game library: the framework code, tooling hooks,
and starter assets we would otherwise copy into every new project.

> **Status:** early development (`0.1.0-SNAPSHOT`). Nothing is published yet and the API will change.

## What's in it

Planned scope, built on a small set of dependencies that zenlib exposes to games:

| Area                           | Built on                                                      |
|--------------------------------|---------------------------------------------------------------|
| Game framework / ECS           | [Ashley](https://github.com/libgdx/ashley)                    |
| Fonts and text                 | [TextraTypist](https://github.com/tommyettinger/textratypist) |
| Debug UI                       | [jImGui](https://github.com/xpenatan/xImGui) (Dear ImGui)     |
| Tweening                       | [Universal Tween Engine](https://github.com/mini2Dx/universal-tween-engine) |
| Shape drawing                  | [ShapeDrawer](https://github.com/earlygrey/shapedrawer)       |
| Bundled assets (fonts, images) | loaded from the classpath                                     |

## Platforms

| Platform | Backend                                            |
|----------|----------------------------------------------------|
| Desktop  | LWJGL3                                             |
| Web      | [gdx-teavm](https://github.com/xpenatan/gdx-teavm) |

GWT is not supported.

## Requirements

- JDK 25
- libGDX 1.14.2

Dependency versions are pinned in [`gradle.properties`](gradle.properties):

| Dependency             | Version          |
|------------------------|------------------|
| libGDX                 | 1.14.2           |
| Ashley                 | 1.7.4            |
| TextraTypist           | 2.5.0            |
| Universal Tween Engine | 6.3.3            |
| ShapeDrawer            | 2.6.0            |
| jImGui                 | `zendo-20260914` |

jImGui has no usable tagged release, so a frozen copy of its snapshot build is committed under
[`vendor/`](vendor/README.md).

## Using zenlib in a game

zenlib is not published yet. Until it is, use it through a
[composite build](#developing-against-a-game-project) from a sibling checkout.

```groovy
// core/build.gradle
dependencies {
    api "com.zendogames:zenlib:$zenlibVersion"
}
```

```properties
# gradle.properties
zenlibVersion=0.1.0-SNAPSHOT
```

The game also needs the repositories zenlib's dependencies come from:

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://jitpack.io' }
    // jImGui/jParser builds vendored in zenlib: use the sibling checkout if present, otherwise GitHub
    maven {
        def zenlibVendor = rootProject.file('../zenlib/vendor/maven')
        url = zenlibVendor.exists() ? zenlibVendor.toURI()
            : 'https://raw.githubusercontent.com/zendo-games/zenlib/main/vendor/maven'
        content {
            includeGroup 'com.github.xpenatan.jImGui'
            includeGroup 'com.github.xpenatan.jParser'
        }
    }
}
```

Each launcher module adds jImGui's platform artifacts; the exact lines are in
[`vendor/README.md`](vendor/README.md#using-it).

### Minimal game

```java
public class Main extends ZenGame {

    @Override
    public ZenConfig config() {
        return new ZenConfig("my-game-prefs", 1280, 720);
    }

    @Override
    protected ZenScreen createFirstScreen() {
        return new FirstScreen(this);
    }
}
```

`ZenGame` owns startup: it initializes the library, loads ImGui, then asks for the first screen.
Override `init()` to register game-specific flags.

## Building

```shell
./gradlew build               # compile, test, and assemble jars
./gradlew test
./gradlew javadoc             # writes docs/apidocs
./gradlew publishToMavenLocal # publishes library artifacts to local maven repo
```

Gradle output is set to `quiet` in `gradle.properties`; add `--info` when you need to see more.

## Developing against a game project

[zenlib-demo](https://github.com/zendo-games/zenlib-demo) is a
[gdx-liftoff](https://github.com/libgdx/gdx-liftoff) project that consumes zenlib the way a real
game does. It lives in its own repo and pulls zenlib in from a sibling checkout with a Gradle
composite build, so library changes are picked up on the next build with no publishing step.

```
ZendoGames/
  zenlib/         this repo
  zenlib-demo/    gdx-liftoff project: core, lwjgl3, teavm
```

```groovy
// zenlib-demo/settings.gradle

// Builds zenlib from the sibling checkout, replacing the 'com.zendogames:zenlib' dependency.
// Pass -PzenlibPublished to ignore the checkout and resolve the published artifact instead.
def zenlibCheckout = file('../zenlib')
def usePublished = providers.gradleProperty('zenlibPublished').isPresent()
if (zenlibCheckout.exists() && !usePublished) {
  includeBuild(zenlibCheckout) {
    dependencySubstitution {
      // Substitute JitPack coordinates for sibling checkout coordinates
      substitute module('com.github.zendo-games:zenlib') using project(':')
    }
  }
}
```

Gradle matches the dependency to this build by `group` (`com.zendogames`) and
`rootProject.name` (`zenlib`). The requested version is ignored while the checkout is included.

Any game project can use the same snippet to work against local zenlib sources.

### What the composite build covers

| Exercised                                                         | Not exercised                              |
|-------------------------------------------------------------------|--------------------------------------------|
| The built jar, including bundled assets and `META-INF/` resources | The published POM / Gradle module metadata |
| `api` vs `compileOnly` visibility to the game                     | JitPack's build of this repo               |
| Launcher-side dependencies (jImGui natives, gdx-teavm plugin)     | Maven Central signing and upload           |
| TeaVM compilation of zenlib's bytecode                            |                                            |

To check the right-hand column, publish locally and make the demo resolve the artifact
(it needs `mavenLocal()` in its repositories, which liftoff adds by default):

```shell
# in zenlib
./gradlew publishToMavenLocal

# in zenlib-demo
./gradlew lwjgl3:run -PzenlibPublished
```

### Things to know

- When included, zenlib reads its own `gradle.properties` for dependency versions, but
  `org.gradle.*` settings and the daemon JVM come from the including project.
- IntelliJ imports both builds into one window when the game project is opened, so navigation,
  refactoring, and debugging work across the boundary.

## Project layout

```
build.gradle                                       library build and publishing
gradle.properties                                  dependency versions and POM metadata
src/main/java/com/zendogames/zenlib/               library sources
src/main/resources/com/zendogames/zenlib/assets/   bundled assets
src/main/resources/META-INF/gdx-teavm.properties   tells gdx-teavm which web payloads to package
src/test/java/com/zendogames/zenlib/               tests (JUnit 4, LWJGL3 backend)
tools/mirror.py                                    refreshes the vendored jImGui builds
vendor/                                            frozen third-party builds, see vendor/README.md
docs/apidocs/                                      generated Javadoc
```

## Credits

Started from [tommyettinger/libgdx-library-template](https://github.com/tommyettinger/libgdx-library-template).

## License

[Apache License 2.0](LICENSE)
