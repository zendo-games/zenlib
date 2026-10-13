// PARKED until the tilemap slice (docs/tilemap-proposal.md) is applied: the types it tests do not exist yet.
// Unpark: remove the leading "// " from every line of the files in this directory (the proposal has the command).
// package com.zendogames.zenlib.tilemap;
//
// import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
// import com.badlogic.gdx.utils.GdxRuntimeException;
// import com.zendogames.zenlib.collision.shapes.CollisionGrid;
// import com.zendogames.zenlib.ecs.Family;
// import com.zendogames.zenlib.ecs.World;
// import com.zendogames.zenlib.ecs.components.*;
// import com.zendogames.zenlib.render.RenderLayer;
// import org.junit.Test;
//
// import static com.zendogames.zenlib.collision.shapes.CollisionGrid.CellShape.*;
// import static com.zendogames.zenlib.tilemap.TestMaps.*;
// import static org.junit.Assert.*;
//
// public class TilemapTest {
//
//     @Test
//     public void readsTheSizeAndRejectsNonSquareTiles() {
//         var tilemap = new Tilemap(map(40, 23));
//         assertEquals(40, tilemap.cols);
//         assertEquals(23, tilemap.rows);
//         assertEquals(16, tilemap.tileSize);
//         assertEquals(640, tilemap.width());
//         assertEquals(368, tilemap.height());
//         var bounds = tilemap.bounds(10, 20);
//         assertEquals(10f, bounds.x, 0f);
//         assertEquals(20f, bounds.y, 0f);
//         assertEquals(640f, bounds.width, 0f);
//         assertEquals(368f, bounds.height, 0f);
//
//         var e = assertThrows(GdxRuntimeException.class, () -> new Tilemap(map(4, 4, 16, 8)));
//         assertTrue(e.getMessage(), e.getMessage().contains("16x8"));
//     }
//
//     @Test
//     public void classifiesLayersInFileOrderThroughGroups() {
//         var map = map(4, 4);
//         var background = tiles(map, "background");                       // walk index 0
//         background.getProperties().put(Tilemap.LAYER_PROPERTY, "background");
//         var group = group(map.getLayers(), "room");                       // 1, its children 1 and 2
//         var world = tiles(map, group.getLayers(), "world");
//         var objects = objectLayer(group.getLayers(), "objects");
//         object(objects, 1, "player-1", "player", 0, 0, 16, 16);
//         object(objects, 2, "spot", "marker", 8, 8, 0, 0);
//         var collision = tiles(map, "collision");                         // 4 (the group counted once more after its children)
//         var fx = tiles(map, "fx");                                        // 5 by walk, 42 by property
//         fx.getProperties().put(Tilemap.COLLISION_PROPERTY, true);
//         fx.getProperties().put(Tilemap.LAYER_PROPERTY, "foreground");
//         fx.getProperties().put(Tilemap.ORDER_PROPERTY, 42);
//         var hidden = tiles(map, "hidden");                                // 6
//         hidden.setVisible(false);
//
//         var tilemap = new Tilemap(map);
//
//         assertEquals(5, tilemap.layers.size);
//         assertEquals("background", tilemap.layers.get(0).name());
//         assertEquals("world",      tilemap.layers.get(1).name());
//         assertEquals("collision",  tilemap.layers.get(2).name());
//         assertEquals("fx",         tilemap.layers.get(3).name());
//         assertEquals("hidden",     tilemap.layers.get(4).name());
//
//         assertEquals(RenderLayer.BACKGROUND, tilemap.layer("background").renderLayer());
//         assertEquals(RenderLayer.WORLD,      tilemap.layer("world").renderLayer());
//         assertEquals(RenderLayer.FOREGROUND, tilemap.layer("fx").renderLayer());
//         assertEquals(0,  tilemap.layer("background").order());
//         assertEquals(1,  tilemap.layer("world").order());
//         assertEquals(4,  tilemap.layer("collision").order());
//         assertEquals(42, tilemap.layer("fx").order());
//         assertEquals(6,  tilemap.layer("hidden").order());
//
//         assertTrue (tilemap.layer("collision").collision());   // by name
//         assertTrue (tilemap.layer("fx").collision());          // by property
//         assertFalse(tilemap.layer("world").collision());
//         assertEquals(2, tilemap.collisionLayers.size);
//         assertTrue(tilemap.hasCollision());
//
//         assertTrue (tilemap.layer("world").visible());
//         assertFalse(tilemap.layer("hidden").visible());
//         assertNull(tilemap.layer("nope"));
//
//         assertEquals(2, tilemap.objects.size);
//         assertEquals("player-1", tilemap.objects.get(0).getName());
//         assertEquals("spot",     tilemap.objects.get(1).getName());
//     }
//
//     @Test
//     public void aHiddenGroupHidesItsChildren() {
//         var map = map(2, 2);
//         var group = group(map.getLayers(), "off");
//         group.setVisible(false);
//         tiles(map, group.getLayers(), "child");
//         assertFalse(new Tilemap(map).layer("child").visible());
//     }
//
//     @Test
//     public void gridColliderMergesCollisionLayersWithShapesAndFlips() {
//         var map = map(3, 2);
//         var a = tiles(map, "collision");
//         a.setCell(0, 0, cell(tile(1, null)));                                  // FULL by default
//         a.setCell(1, 0, cell(tile(2, "ramp_ne")));
//         a.setCell(2, 1, cell(tile(2, "ramp_ne"), true, false, Cell.ROTATE_0)); // flipped: climbs left
//         var b = tiles(map, "more");
//         b.getProperties().put(Tilemap.COLLISION_PROPERTY, true);
//         b.setCell(1, 0, cell(tile(3, "empty")));                               // later layer wins: punches a hole
//         b.setCell(0, 1, cell(tile(4, "ramp_sw")));
//
//         var collider = new Tilemap(map).gridCollider(Mask.SOLID);
//         assertSame(Mask.SOLID, collider.mask());
//         var grid = collider.shape(CollisionGrid.class);
//         assertEquals(16, grid.cellSize());
//         assertEquals(3, grid.cols());
//         assertEquals(2, grid.rows());
//         assertEquals(FULL,    grid.get(0, 0));
//         assertEquals(EMPTY,   grid.get(1, 0));
//         assertEquals(EMPTY,   grid.get(2, 0));
//         assertEquals(RAMP_SW, grid.get(0, 1));
//         assertEquals(EMPTY,   grid.get(1, 1));
//         assertEquals(RAMP_NW, grid.get(2, 1));
//     }
//
//     @Test
//     public void gridColliderFromOneNamedLayer() {
//         var map = map(2, 1);
//         tiles(map, "collision").setCell(0, 0, cell(tile(1, null)));
//         var hazard = tiles(map, "spikes");                                       // not a collision layer
//         hazard.setCell(1, 0, cell(tile(5, null)));
//         var tilemap = new Tilemap(map);
//
//         var spikes = tilemap.gridCollider(Mask.HAZARD, "spikes").shape(CollisionGrid.class);
//         assertEquals(EMPTY, spikes.get(0, 0));
//         assertEquals(FULL,  spikes.get(1, 0));
//         var solid = tilemap.gridCollider(Mask.SOLID).shape(CollisionGrid.class);
//         assertEquals(FULL,  solid.get(0, 0));
//         assertEquals(EMPTY, solid.get(1, 0));
//
//         var e = assertThrows(GdxRuntimeException.class, () -> tilemap.gridCollider(Mask.SOLID, "lava"));
//         assertTrue(e.getMessage(), e.getMessage().contains("'lava'"));
//     }
//
//     @Test
//     public void aMapWithoutCollisionLayersIsLegalButHasNoGrid() {
//         var map = map(2, 2);
//         tiles(map, "backdrop");
//         var tilemap = new Tilemap(map);
//         assertFalse(tilemap.hasCollision());
//         assertThrows(GdxRuntimeException.class, () -> tilemap.gridCollider(Mask.SOLID));
//
//         var world = new World();
//         var entity = tilemap.spawn(world, Mask.SOLID, null, null);
//         assertFalse(Collider.has(entity));
//     }
//
//     @Test
//     public void spawnCreatesTheMapEntityAndOnePerLayer() {
//         var map = map(4, 4);
//         var backdrop = tiles(map, "background");
//         backdrop.getProperties().put(Tilemap.LAYER_PROPERTY, "background");
//         backdrop.setOffsetX(3);
//         var collision = tiles(map, "collision");
//         collision.setCell(0, 0, cell(tile(1, null)));
//         var fg = tiles(map, "foreground");
//         fg.getProperties().put(Tilemap.LAYER_PROPERTY, "foreground");
//         fg.setVisible(false);
//         var tilemap = new Tilemap(map);
//
//         var world = new World();
//         var entity = tilemap.spawn(world, Mask.SOLID, null, null);
//
//         assertTrue(Name.get(entity).is("map"));
//         assertTrue(Position.get(entity).is(0, 0));
//         assertSame(tilemap, Tilemap.get(entity));
//         assertSame(Mask.SOLID, Collider.get(entity).mask());
//         assertEquals(FULL, Collider.get(entity).shape(CollisionGrid.class).get(0, 0));
//
//         assertEquals(3, tilemap.layerEntities.size);
//         assertEquals(4, world.entities().size);
//         var drawn = world.family(Family.all(Position.TYPE, TileLayer.TYPE));
//         assertEquals(3, drawn.size());
//
//         var bg = TileLayer.get(tilemap.layerEntities.get(0));
//         assertTrue(Name.get(tilemap.layerEntities.get(0)).is("layer:background"));
//         assertEquals("background", bg.name());
//         assertEquals(RenderLayer.BACKGROUND, bg.layer);
//         assertEquals(0, bg.order);
//         assertTrue(bg.visible);
//         assertFalse(bg.collision);
//         assertEquals(64f, bg.size.x, 0f);
//         assertEquals(64f, bg.size.y, 0f);
//         assertSame(bg.renderer, TileLayer.get(tilemap.layerEntities.get(1)).renderer); // one renderer per map
//
//         var col = TileLayer.get(tilemap.layerEntities.get(1));
//         assertTrue(col.collision);
//         assertFalse(col.visible);                                                  // starts hidden
//         assertEquals(RenderLayer.WORLD, col.layer);
//
//         var fore = TileLayer.get(tilemap.layerEntities.get(2));
//         assertFalse(fore.visible);                                                 // hidden in Tiled stays hidden
//         assertFalse(fore.collision);
//         assertEquals(RenderLayer.FOREGROUND, fore.layer);
//         assertEquals(2, fore.order);
//     }
// }
