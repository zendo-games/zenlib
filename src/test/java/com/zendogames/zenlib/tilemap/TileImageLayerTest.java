package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.*;
import com.zendogames.zenlib.render.RenderLayer;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static com.zendogames.zenlib.tilemap.TestMaps.*;
import static org.junit.Assert.*;

public class TileImageLayerTest {

    @Test
    public void classifiesImageLayersByTheTileLayerRules() {
        var map = map(4, 4);
        tiles(map, "ground");                                             // walk index 0
        var group = group(map.getLayers(), "backdrop");                   // 1, its child 1
        var sky = image(map, group.getLayers(), "sky", 32, 16, 0, 0);
        sky.getProperties().put(Tilemap.LAYER_PROPERTY, "background");
        var moon = image(map, map.getLayers(), "foreground", 8, 8, 0, 0); // 3 by walk, 7 by property
        moon.getProperties().put(Tilemap.ORDER_PROPERTY, 7);
        var hidden = image(map, map.getLayers(), "hidden", 8, 8, 0, 0);   // 4
        hidden.setVisible(false);

        var tilemap = new Tilemap(map);

        assertEquals(1, tilemap.layers.size);
        assertEquals(3, tilemap.images.size);
        var first = tilemap.images.get(0);
        assertSame(sky, first.image());
        assertEquals("sky", first.name());
        assertEquals(RenderLayer.BACKGROUND, first.renderLayer());
        assertEquals(1, first.order());
        assertTrue(first.visible());

        var second = tilemap.images.get(1);
        assertEquals(RenderLayer.FOREGROUND, second.renderLayer());          // parsed from the name
        assertEquals(7, second.order());

        assertFalse(tilemap.images.get(2).visible());
        assertSame(moon, tilemap.image("foreground").image());
        assertNull(tilemap.image("nope"));
    }

    @Test
    public void aHiddenGroupHidesItsImageLayers() {
        var map = map(4, 4);
        var group = group(map.getLayers(), "off");
        group.setVisible(false);
        image(map, group.getLayers(), "sky", 8, 8, 0, 0);

        var tilemap = new Tilemap(map);
        assertFalse(tilemap.images.first().visible());
    }

    @Test
    public void spawnCreatesOneEntityPerImageLayerBesideTheTileLayers() {
        var map = map(4, 4);
        tiles(map, "ground");
        var sky = image(map, map.getLayers(), "sky", 32, 16, 0, 0);
        sky.getProperties().put(Tilemap.LAYER_PROPERTY, "background");
        sky.setVisible(false);
        var tilemap = new Tilemap(map);

        var world = new World();
        tilemap.spawn(world, Mask.SOLID, null, null);

        assertEquals(1, tilemap.layerEntities.size);
        assertTrue(TileLayer.has(tilemap.layerEntities.first()));
        assertEquals(1, tilemap.imageEntities.size);
        var entity = tilemap.imageEntities.first();
        assertTrue(Name.get(entity).is("layer:sky"));
        assertTrue(Position.get(entity).is(0, 0));
        assertFalse(TileLayer.has(entity));

        var layer = TileImageLayer.get(entity);
        assertSame(sky, layer.image);
        assertEquals("sky", layer.name());
        assertEquals(RenderLayer.BACKGROUND, layer.layer);
        assertEquals(1, layer.order);
        assertFalse(layer.visible);
        assertEquals(32f, layer.size.x, 0f);
        assertEquals(16f, layer.size.y, 0f);
    }

    @Test
    public void placesTheImageWhereTheLoaderPutItPlusTheEntityPosition() {
        // The loader stores x = offsetx and y = mapHeight - offsety - imageHeight; a 4x4 map of 16 px is 64 high
        var map = map(4, 4);
        var sky = image(map, map.getLayers(), "sky", 32, 16, 3, 5);
        var tilemap = new Tilemap(map);
        var layer = new TileImageLayer(null, null, tilemap.images.first());

        assertEquals(3f, layer.imageX(0), 0f);
        assertEquals(64 - 5 - 16, layer.imageY(0), 0f);
        assertEquals(13f, layer.imageX(10), 0f);
        assertEquals(64 - 5 - 16 + 20, layer.imageY(20), 0f);
        assertEquals(3f, sky.getX(), 0f);                                   // untouched until render
    }

    @Test
    public void aParentGroupOffsetIsAddedOnceYDown() {
        var map = map(4, 4);
        var group = group(map.getLayers(), "backdrop");
        group.setOffsetX(10);
        group.setOffsetY(20);
        var sky = image(map, group.getLayers(), "sky", 32, 16, 3, 5);
        sky.setParent(group);                                               // what the loader does for group children
        var tilemap = new Tilemap(map);
        var layer = new TileImageLayer(null, null, tilemap.images.first());

        assertEquals(3 + 10, layer.imageX(0), 0f);
        assertEquals(64 - 5 - 16 - 20, layer.imageY(0), 0f);
    }

    @Test
    public void renderRefusesAnotherBatchThanTheRenderersAndSkipsWhenHidden() {
        var map = map(4, 4);
        image(map, map.getLayers(), "sky", 8, 8, 0, 0);
        var tilemap = new Tilemap(map);
        var world = new World();
        tilemap.spawn(world, Mask.SOLID, null, null);
        var layer = TileImageLayer.get(tilemap.imageEntities.first());

        layer.visible = false;
        layer.render(null, 0, 0);                                           // returns before touching the batch

        layer.visible = true;
        var other = (Batch) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Batch.class }, (proxy, method, args) -> null);
        var e = assertThrows(GdxRuntimeException.class, () -> layer.render(other, 0, 0));
        assertTrue(e.getMessage(), e.getMessage().contains("'sky'"));
    }
}
