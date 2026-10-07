package com.zendogames.zenlib.assets;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Animation.PlayMode;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public enum ZenAnimHero implements AnimType {
      ATTACK ("characters/hero/hero-attack/hero-attack")
    , DEATH  ("characters/hero/hero-death/hero-death")
    , FALL   ("characters/hero/hero-fall/hero-fall")
    , HURT   ("characters/hero/hero-hurt/hero-hurt")
    , IDLE   ("characters/hero/hero-idle/hero-idle")
    , JUMP   ("characters/hero/hero-jump/hero-jump")
    , RUN    ("characters/hero/hero-run/hero-run")
    , EFFECT_ATTACK ("characters/hero/effects/hero-attack-effect/hero-attack-effect")
    , EFFECT_LAND   ("characters/hero/effects/hero-land-effect/hero-land-effect")
    ;

    private final Asset<Animation<TextureRegion>> asset;
    private final String regionName;
    private final float frameDuration;
    private final PlayMode playMode;

    ZenAnimHero(String regionName) {
        this(regionName, DEFAULT_FRAME_DURATION, DEFAULT_PLAY_MODE);
    }

    ZenAnimHero(String regionName, float frameDuration, PlayMode playMode) {
        this.asset = new Asset<>(this);
        this.regionName = regionName;
        this.frameDuration = frameDuration;
        this.playMode = playMode;
    }

    @Override public AtlasType atlas()     { return ZenAtlas.SPRITES; }
    @Override public String regionName()   { return regionName; }
    @Override public float frameDuration() { return frameDuration; }
    @Override public PlayMode playMode()   { return playMode; }
    @Override public Asset<Animation<TextureRegion>> asset() { return asset; }
}
