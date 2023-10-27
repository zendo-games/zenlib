package zendo.games.zenlib.assets;

import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import zendo.games.zenlib.utils.data.RectI;

public enum ZenPatch {
    debug                           (4, 4, 4, 4)
    , glass                           (12, 12, 12, 12)
    , glass_active                    (12, 12, 12, 12)
    , glass_blue                      (12, 12, 12, 12)
    , glass_corner_bl                 (12, 12, 12, 12)
    , glass_corner_br                 (12, 12, 12, 12)
    , glass_corner_tl                 (12, 12, 12, 12)
    , glass_corner_tr                 (12, 12, 12, 12)
    , glass_corners                   (12, 12, 12, 12)
    , glass_dark                      (12, 12, 12, 12)
    , glass_dim                       (12, 12, 22, 12)
    , glass_dim_down                  (12, 12, 12, 12)
    , glass_dim_left                  (12, 12, 12, 12)
    , glass_dim_point                 (12, 12, 12, 12)
    , glass_dim_right                 (12, 12, 12, 12)
    , glass_dim_up                    (12, 12, 12, 12)
    , glass_green                     (12, 12, 12, 12)
    , glass_red                       (12, 12, 12, 12)
    , glass_red_2                     (12, 12, 12, 12)
    , glass_red_3                     (12, 12, 12, 12)
    , glass_tab                       (12, 12, 22, 12)
    , glass_thick                     (12, 12, 22, 12)
    , glass_yellow                    (12, 12, 12, 12)
    , metal                           (12, 12, 12, 12)
    , metal_blue                      (12, 12, 12, 12)
    , metal_blue_corner               (12, 12, 12, 12)
    , metal_green                     (12, 12, 12, 12)
    , metal_green_corner              (12, 12, 12, 12)
    , metal_plate                     (12, 12, 12, 12)
    , metal_red                       (12, 12, 12, 12)
    , metal_red_corner                (12, 12, 12, 12)
    , metal_yellow                    (12, 12, 12, 12)
    , metal_yellow_corner             (12, 12, 12, 12)
    , panel                           (12, 12, 12, 12)
    , plain                           (12, 12, 12, 12)
    , plain_dim                       (12, 12, 12, 12)
    , plain_gradient                  (2,  2,  2,  2)
    , plain_gradient_highlight_green  (2,  2,  2,  2)
    , plain_gradient_highlight_red    (2,  2,  2,  2)
    , plain_gradient_highlight_yellow (2,  2,  2,  2)
    , shear                           (75, 75, 12, 12)
    ;

    public final RectI bounds;
    public NinePatch ninepatch;
    public NinePatchDrawable ninePatchDrawable;

    ZenPatch(int left, int right, int top, int bottom) {
        this.bounds = new RectI(left, bottom, right - left, top - bottom);
        this.ninepatch = null;
        this.ninePatchDrawable = null;
    }

    public static void init(TextureAtlas atlas) {
        for (var patch : values()) {
            var regionName = patch.name().replaceAll("_", "-");
            var region = atlas.findRegion(regionName);
            var bounds = patch.bounds;

            patch.ninepatch = new NinePatch(region, bounds.left(), bounds.right(), bounds.top(), bounds.bottom());
            patch.ninePatchDrawable = new NinePatchDrawable(patch.ninepatch);
        }
    }
}
