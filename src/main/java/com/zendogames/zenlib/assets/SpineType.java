package com.zendogames.zenlib.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.utils.SkeletonDataLoader;

import static com.esotericsoftware.spine.utils.SkeletonDataLoader.*;

/**
 * A Spine export ({@code .skel} or {@code .json} plus the atlas packed with it) loaded by
 * {@code Assets} through spine's {@code SkeletonDataLoader}; the atlas loads as a dependency.
 * The skeleton data is immutable and shared: each {@code SpineAnimator} builds its own
 * {@code Skeleton} and {@code AnimationState} over it.
 * <pre>{@code
 * public enum GameSpine implements SpineType {
 *     HERO("spine/hero/hero.skel", 1 / 8f);
 *     ...
 *     @Override public FileHandle file() { return Gdx.files.internal(path); }
 *     @Override public float scale()     { return scale; }
 *     @Override public void mixes(AnimationStateData mixes) { mixes.setMix("run", "jump", 0.2f); }
 * }
 * }</pre>
 */
public interface SpineType extends AssetType<SpineData> {

    /** The skeleton file, binary ({@code .skel}) or JSON; resolved lazily (not in the enum constructor) so {@code Gdx.files} is available. */
    FileHandle file();

    /** The atlas Spine exported with the skeleton; by default {@code <skeleton>.atlas} next to it. */
    default FileHandle atlasFile() {
        return file().sibling(file().nameWithoutExtension() + ".atlas");
    }

    /** World units per Spine unit, baked into the data at load: {@code 1 / 8f} draws a 128 px tall export 16 px tall. */
    default float scale() { return 1f; }

    /** Crossfade seconds between any two animations {@link #mixes} does not list. */
    default float defaultMix() { return 0f; }

    /** Per-pair crossfades: {@code mixes.setMix("run", "jump", 0.2f)}. Runs once, at load. */
    default void mixes(AnimationStateData mixes) {}

    @Override
    default void load(Assets assets) {
        var parameter = new SkeletonDataParameter(atlasFile().path(), scale());
        assets.mgr.load(file().path(), SkeletonData.class, parameter);
    }

    @Override
    default SpineData init(Assets assets) {
        var skeleton = assets.mgr.get(file().path(), SkeletonData.class);
        var mixes = new AnimationStateData(skeleton);
        mixes.setDefaultMix(defaultMix());
        mixes(mixes);
        return new SpineData(skeleton, mixes, premultipliedAlpha(atlasFile()));
    }

    /**
     * Whether the atlas was packed with premultiplied alpha (Spine's default export), from the
     * {@code pma} field of its first page. Re-parses the small text file; libGDX's {@code TextureAtlas}
     * does not keep the flag.
     */
    static boolean premultipliedAlpha(FileHandle atlasFile) {
        var data = new TextureAtlas.TextureAtlasData(atlasFile, atlasFile.parent(), false);
        var pages = data.getPages();
        return pages.notEmpty() && pages.first().pma;
    }
}
