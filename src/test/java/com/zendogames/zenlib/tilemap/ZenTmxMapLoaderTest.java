// PARKED until the tilemap slice (docs/tilemap-proposal.md) is applied: the types it tests do not exist yet.
// Unpark: remove the leading "// " from every line of the files in this directory (the proposal has the command).
// package com.zendogames.zenlib.tilemap;
//
// import com.badlogic.gdx.assets.loaders.FileHandleResolver;
// import com.badlogic.gdx.files.FileHandle;
// import com.badlogic.gdx.maps.MapObject;
// import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
// import com.badlogic.gdx.maps.tiled.TiledMap;
// import com.badlogic.gdx.maps.tiled.TmxMapLoader;
// import org.junit.BeforeClass;
// import org.junit.Test;
//
// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Path;
//
// import static org.junit.Assert.*;
//
// /**
//  * Loads a map with no tilesets (so no textures, so headless) through the stock loader and ours.
//  * The project file declares a {@code trigger} class with an {@code object} member {@code target}
//  * defaulting to 0, the shape that triggers the libGDX bug {@link ZenTmxMapLoader} works around.
//  */
// public class ZenTmxMapLoaderTest {
//
//     static final String TMX = """
//             <?xml version="1.0" encoding="UTF-8"?>
//             <map version="1.10" tiledversion="1.12.2" orientation="orthogonal" renderorder="right-down" width="4" height="4" tilewidth="16" tileheight="16" infinite="0" nextlayerid="3" nextobjectid="23">
//              <objectgroup id="2" name="objects">
//               <object id="18" name="teleport" type="trigger" x="16" y="32" width="32" height="16">
//                <properties>
//                 <property name="target" type="object" value="21"/>
//                </properties>
//               </object>
//               <object id="19" name="plain" type="trigger" x="48" y="32" width="32" height="16"/>
//               <object id="21" name="teleport-target" type="marker" x="40" y="12">
//                <point/>
//               </object>
//              </objectgroup>
//             </map>
//             """;
//
//     static final String PROJECT = """
//             {
//                 "propertyTypes": [
//                     {
//                         "id": 3, "name": "trigger", "type": "class", "useAs": ["object"],
//                         "members": [
//                             { "name": "dwell",  "type": "float",  "value": 0 },
//                             { "name": "target", "type": "object", "value": 0 }
//                         ]
//                     },
//                     {
//                         "id": 7, "name": "marker", "type": "class", "useAs": ["object"],
//                         "members": [ { "name": "unused", "type": "string", "value": "" } ]
//                     }
//                 ]
//             }
//             """;
//
//     static Path dir;
//     static FileHandleResolver resolver;
//
//     @BeforeClass
//     public static void writeFiles() throws IOException {
//         dir = Files.createTempDirectory("zenlib-tmx");
//         Files.writeString(dir.resolve("test.tmx"), TMX);
//         Files.writeString(dir.resolve("test.tiled-project"), PROJECT);
//         resolver = name -> new FileHandle(dir.resolve(name).toFile());
//     }
//
//     /** The manager's sequence: dependencies, async load, sync load, on one loader instance. */
//     private static TiledMap load(ZenTmxMapLoader loader) {
//         var params = new ZenTmxMapLoader.Parameters();
//         params.projectFilePath = "test.tiled-project";
//         var file = resolver.resolve("test.tmx");
//         assertEquals(0, loader.getDependencies("test.tmx", file, params).size);
//         loader.loadAsync(null, "test.tmx", file, params);
//         return loader.loadSync(null, "test.tmx", file, params);
//     }
//
//     private static TiledMap loadStock(TmxMapLoader loader) {
//         var params = new BaseTiledMapLoader.Parameters();
//         params.projectFilePath = "test.tiled-project";
//         var file = resolver.resolve("test.tmx");
//         loader.getDependencies("test.tmx", file, params);
//         loader.loadAsync(null, "test.tmx", file, params);
//         return loader.loadSync(null, "test.tmx", file, params);
//     }
//
//     private static MapObject object(TiledMap map, String name) {
//         return map.getLayers().get("objects").getObjects().get(name);
//     }
//
//     @Test
//     public void classObjectReferencesResolveAndUnsetOnesAreAbsent() {
//         var map = load(new ZenTmxMapLoader(resolver));
//
//         var teleport = object(map, "teleport");
//         var target   = teleport.getProperties().get("target", MapObject.class);
//         assertNotNull(target);
//         assertEquals("teleport-target", target.getName());
//         assertEquals(21, TiledProps.id(target));
//         assertEquals(0f, TiledProps.require(teleport, "dwell", Float.class), 0f); // the class default still loads
//
//         var plain = object(map, "plain");
//         assertFalse(plain.getProperties().containsKey("target"));                 // unset reference: no key, not null
//         assertEquals(0f, TiledProps.require(plain, "dwell", Float.class), 0f);
//     }
//
//     /** Pins the upstream bug. When this fails, libGDX fixed it and the override in {@link ZenTmxMapLoader} can go. */
//     @Test
//     public void stockLoaderStillNullsTheReference() {
//         var map = loadStock(new TmxMapLoader(resolver));
//         var teleport = object(map, "teleport");
//         assertTrue(teleport.getProperties().containsKey("target"));
//         assertNull(teleport.getProperties().get("target"));
//     }
//
//     @Test
//     public void unsetReferenceIsZero() {
//         assertTrue (ZenTmxMapLoader.isUnsetReference("0"));
//         assertFalse(ZenTmxMapLoader.isUnsetReference("21"));
//         assertFalse(ZenTmxMapLoader.isUnsetReference(null));
//     }
// }
