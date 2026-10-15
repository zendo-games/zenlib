# vendor

Third-party builds that zenlib depends on but that have no usable tagged release upstream.
`maven/` is a standard Maven repository, committed to git, so zenlib and the games that use it
resolve these like any other dependency.

## What's here and why

[jImGui](https://github.com/xpenatan/xImGui) (the Dear ImGui binding zenlib uses for debug UI) only
publishes moving `-SNAPSHOT` builds, which in turn depend on `-SNAPSHOT` builds of
[jParser](https://github.com/xpenatan/jParser). Its one tagged release (`1.92.4.0`, under the old
`xImGui` name) was built for TeaVM 0.13 and does not work with the TeaVM 0.15 that current
gdx-teavm uses, so web builds need the newer code.

To get the newer code without a dependency that can change underneath us, the snapshot builds
are frozen here under fixed versions:

| Group                         | Frozen version   | Upstream snapshot build |
|-------------------------------|------------------|-------------------------|
| `com.github.xpenatan.jImGui`  | `zendo-20260914` | `20260914.112311`       |
| `com.github.xpenatan.jParser` | `zendo-20260920` | `20260920.163639`       |

The jars are unmodified. Group and artifact names are upstream's; only the version differs, and
the `-SNAPSHOT` versions inside each POM are rewritten to the frozen ones. Gradle module metadata
(`.module`) and Javadoc jars are not mirrored. [`maven/MANIFEST.txt`](maven/MANIFEST.txt) records
the exact upstream build each artifact came from.

## Using it

zenlib's own build reads the folder directly:

```groovy
// zenlib build.gradle
maven {
    // jImGui/jParser snapshot builds frozen under fixed versions, see vendor/README.md
    url = uri('vendor/maven')
    content {
        includeGroup 'com.github.xpenatan.jImGui'
        includeGroup 'com.github.xpenatan.jParser'
    }
}
```

A game resolves zenlib's dependencies with its own repositories, so it needs an entry too. The
folder is served straight from GitHub, which Gradle reads like any other Maven repository:

```groovy
// game root build.gradle, inside subprojects { repositories { ... } }
maven {
    // jImGui/jParser snapshot builds frozen in zenlib's repository
    url = 'https://raw.githubusercontent.com/zendo-games/zenlib/main/vendor/maven'
    content {
        includeGroup 'com.github.xpenatan.jImGui'
        includeGroup 'com.github.xpenatan.jParser'
    }
}
```

A game developed against a sibling checkout of zenlib (a composite build, see the main README) can
read the folder directly instead and fall back to GitHub when the checkout is missing:

```groovy
maven {
    // jImGui/jParser builds vendored in zenlib: use the sibling checkout if present, otherwise GitHub
    def zenlibVendor = rootProject.file('../zenlib/vendor/maven')
    url = zenlibVendor.exists() ? zenlibVendor.toURI()
        : 'https://raw.githubusercontent.com/zendo-games/zenlib/main/vendor/maven'
    content {
        includeGroup 'com.github.xpenatan.jImGui'
        includeGroup 'com.github.xpenatan.jParser'
    }
}
```

zenlib brings in `imgui-core` and `gdx-gl-impl`. Each game adds the platform pieces to its launchers:

```groovy
// lwjgl3/build.gradle
implementation "com.github.xpenatan.jImGui:imgui-jni:$jImGuiVersion"
implementation "com.github.xpenatan.jImGui:imgui-jni_windows_x64:$jImGuiVersion"
implementation "com.github.xpenatan.jImGui:imgui-jni_linux_x64:$jImGuiVersion"
implementation "com.github.xpenatan.jImGui:imgui-jni_mac_x64:$jImGuiVersion"
implementation "com.github.xpenatan.jImGui:imgui-jni_mac_arm64:$jImGuiVersion"
```

```groovy
// teavm/build.gradle
implementation "com.github.xpenatan.jImGui:imgui-web:$jImGuiVersion"
implementation "com.github.xpenatan.jImGui:imgui-web_wasm:$jImGuiVersion"
```

```properties
# gradle.properties
jImGuiVersion=zendo-20260914
```

### Web payload

`imgui.js`/`imgui.wasm` and jParser's `runtime.js`/`runtime.wasm` ship in the two `_wasm` jars, and
gdx-teavm does not copy them into the web app on its own. zenlib declares them in
`src/main/resources/META-INF/gdx-teavm.properties`, so games need no extra step. If a web build
logs that imgui's native library failed to load, check that `runtime.js` and `imgui.js` are in the
web app's `scripts/` folder, and run `./gradlew teavm:clean` to clear stale copies.

## Taking a newer snapshot

Needs Python 3, no packages.

1. In [`tools/mirror.py`](../tools/mirror.py), change the dates in `FROZEN_VERSIONS`.
2. Run `python tools/mirror.py` from the repo root. It downloads the current snapshot of every
   artifact listed in `ROOTS`, follows their dependencies within the two groups, and writes the
   result here.
3. Update `jImGuiVersion` in zenlib and in each game.
4. Build and run desktop and web before committing; a newer snapshot can change API.
5. Keep the previous version's folders. Every published zenlib release names the frozen versions it
   was built with in its POM, and consumers read this folder from the `main` branch, so deleting a
   version that a release depends on breaks that release for everyone. A version may go once no tag
   references it.

Keep the version format as `zendo-<date>`: it is deliberately not a version upstream would publish.

## JitPack

zenlib is published by [JitPack](https://jitpack.io/#zendo-games/zenlib), which builds a tag from a
checkout of this repository, so zenlib's own `vendor/maven` entry works there unchanged. After the
build, JitPack scans the checkout for `*.pom` files and would treat the frozen builds as modules of
zenlib, rewriting zenlib's dependencies on them to coordinates it never serves. The `install` command
in [`jitpack.yml`](../jitpack.yml) removes `vendor/maven` after publishing so only zenlib is found.

## Returning to upstream

When upstream tags a jImGui release (and the jParser release it depends on):

1. Set `jImGuiVersion` to the release version in zenlib and each game.
2. Remove the `vendor/maven` repository entry from zenlib and each game.
3. Delete `vendor/` and `tools/mirror.py`.
4. Check whether `META-INF/gdx-teavm.properties` still needs its two `resources=` lines.

Nothing else changes, because the group and artifact names were never altered.

## License

jImGui and jParser are both licensed under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) by their authors, and bundle
[Dear ImGui](https://github.com/ocornut/imgui) (MIT). They are redistributed here unmodified.
