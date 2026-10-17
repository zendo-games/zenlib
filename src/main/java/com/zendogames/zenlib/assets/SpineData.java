package com.zendogames.zenlib.assets;

import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.SkeletonData;

/**
 * What a {@link SpineType} loads: the skeleton data every instance shares, the mix (crossfade)
 * table every instance's {@code AnimationState} reads, and how the atlas was packed, which the
 * renderer must match.
 */
public record SpineData(SkeletonData skeleton, AnimationStateData mixes, boolean premultipliedAlpha) {}
